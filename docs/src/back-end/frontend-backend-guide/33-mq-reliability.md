# 消息队列可靠性

> 这一章把消息队列从"会发会收"升级到"丢不了、不重复、能排查"。MQ 是后端最容易出隐性事故的地方：测试环境一切正常，上线后某天用户被多扣一次配额、生图任务卡在"处理中"、半夜消息积压几十万条——这些都是可靠性没做到位的表现。我们以本项目 svc-canvas 发任务、svc-ai 消费的链路为主线，把生产、Broker、消费三个阶段逐段拆开讲透。

---

## 33.1 先回顾：MQ 到底解决什么问题

**一句话**：消息队列是一个"消息中转站"。生产者把消息扔进去，消费者从里面取出来处理，两边不用同时在线、不用互相等待。

**前端类比**：你在前端用过的 `EventBus`（或 Vue 的 `mitt`、Node 的 `EventEmitter`）就是最朴素的 MQ。

```javascript
// 前端 EventBus：发布订阅
eventBus.emit('image:generate', { taskId: '123', prompt: '一只猫' }); // 生产者
eventBus.on('image:generate', (data) => { /* 处理 */ });             // 消费者
```

区别在于 EventBus 活在单个浏览器标签页的内存里，刷新就没了；而 RocketMQ 把消息**持久化到磁盘**、支持**消费失败重试**、支持**多消费者负载均衡**。它解决三件事：

| 作用 | 含义 | 本项目场景 |
| --- | --- | --- |
| 异步（async） | 生产者发完即走，不等消费者处理完 | 用户点"生成"立即返回 taskId，生图在后台慢慢跑 |
| 解耦（decouple） | 生产者不需要知道谁来消费、有几个消费者 | svc-canvas 不关心 svc-ai 部署了几个实例 |
| 削峰（peak shaving） | 高峰期消息堆在队列里，消费者匀速消费 | 活动期一秒涌入上千个生图请求，AI 算力有限，靠队列缓冲 |

### 本项目里的主链路

生图是个耗时操作（一次几秒到几十秒），绝不能让 HTTP 请求一直挂着等。所以 svc-canvas 把"提交任务"和"实际生图"用 MQ 解耦开（这条链路在 [一个请求的完整链路](/back-end/frontend-backend-guide/03-request-lifecycle) 里出现过，这里聚焦它的可靠性）。

```text
   svc-canvas                    RocketMQ Broker                 svc-ai
   (生产者)                      Topic: ai-image-gen            (消费者)
      │                                │                            │
      │  1. 用户提交生图任务            │                            │
      │  2. 落库 task(status=PENDING)   │                            │
      │  3. 发送任务消息 ─────────────▶ │  消息持久化到 CommitLog     │
      │  4. 立即返回 taskId 给前端       │                            │
      │                                │  5. 推送/拉取消息 ─────────▶ │
      │                                │                            │  6. 调 AI API 生图
      │                                │                            │  7. 图片传 svc-oss
      │                                │  ◀──── 8. 消费成功 ack ──── │  8. 更新 task=DONE
      │  前端轮询 task 状态 ◀───────────────────────────────────────┤
```

前端拿到 taskId 后，靠**轮询**（或 WebSocket）查任务状态从 `PENDING` → `PROCESSING` → `DONE`。整条链路的可靠性目标只有一句话：**用户提交过的任务，绝不能凭空消失，也绝不能因为重复消费被多扣配额或生成两张图。**

---

## 33.2 消息为什么会丢：三个阶段全景

一条消息从生产到消费要走三段路，每一段都可能丢。先建立全景，再逐段防护。

```text
  ① 生产端                    ② Broker 端                ③ 消费端
  svc-canvas  ──发送──▶  RocketMQ 接收+落盘  ──投递──▶  svc-ai 处理
     │                       │                          │
  丢点：网络抖动           丢点：消息还在内存            丢点：刚收到消息就
  发出去没人收，            没落盘，Broker 宕机          ack，结果处理时崩了，
  程序却以为成功            消息随内存消失               消息已被删除
```

> **前端类比**：这就像 `axios.post()` 发请求。生产端丢 = 请求根本没发出去（断网）；Broker 丢 = 服务端收到了但还没写库就崩了；消费端丢 = 你在 `.then()` 里 `return` 太早，回调里真正的业务还没跑完就当成功了。

下面三节分别对应这三个阶段。

---

## 33.3 生产端：怎么保证"发出去了"

### 三种发送方式，可靠性递增

```java
// 方式 1：单向发送 oneway —— 发完不管，最快但最不可靠
producer.sendOneway(msg);
// 适用：日志埋点这种丢了也无所谓的场景

// 方式 2：异步发送 async —— 用回调拿结果，吞吐高
producer.send(msg, new SendCallback() {
    @Override public void onSuccess(SendResult result) { /* 记录成功 */ }
    @Override public void onException(Throwable e) { /* 失败重发或落库补偿 */ }
});

// 方式 3：同步发送 sync —— 阻塞等 Broker 确认，最可靠（生图任务用这个）
SendResult result = producer.send(msg);
if (result.getSendStatus() != SendStatus.SEND_OK) {
    throw new BizException(ErrCode.MQ_SEND_FAIL, "任务消息发送失败");
}
```

**前端类比**：`oneway` 像 `navigator.sendBeacon()`（发了就不管）；`async` 像 `axios.post().then().catch()`；`sync` 像 `await axios.post()` 并检查响应状态码。生图这种"丢了用户就白等"的任务，必须用同步发送并校验返回状态。

### 发送确认：SendStatus 必须检查

很多人写 `producer.send(msg)` 后不看返回值，这是大坑。RocketMQ 的 `SendResult` 有四种状态，只有 `SEND_OK` 才算稳：

```text
SEND_OK                   —— 成功，消息已可靠落盘
FLUSH_DISK_TIMEOUT        —— 刷盘超时（开了同步刷盘时），消息可能丢
FLUSH_SLAVE_TIMEOUT       —— 同步到从节点超时，主挂了可能丢
SLAVE_NOT_AVAILABLE       —— 没有可用从节点，主挂了会丢
```

后三种都要当作"可能失败"处理——要么重发，要么落到本地"待发送表"由定时任务补偿。

### 失败重发与生产端封装

本项目把发送逻辑收敛在 cpt-rocketmq 里，统一做重试和兜底：

```java
@Component
public class ReliableProducer {

    @Autowired private RocketMQTemplate rocketMQTemplate;

    /**
     * 可靠发送：同步 + 校验状态 + 有限重试，失败抛异常让上层回滚
     */
    public void sendReliable(String topic, String bizKey, Object payload) {
        Message<String> msg = MessageBuilder
                .withPayload(JsonUtil.toJson(payload))
                .setHeader(RocketMQHeaders.KEYS, bizKey) // 业务键，后面去重/查轨迹都靠它
                .build();

        int maxRetry = 3;
        for (int i = 1; i <= maxRetry; i++) {
            SendResult r = rocketMQTemplate.syncSend(topic, msg, 3000); // 3s 超时
            if (r.getSendStatus() == SendStatus.SEND_OK) {
                return; // 确认落盘，成功返回
            }
            log.warn("MQ 发送非 OK 状态 topic={} key={} status={} 第{}次重试",
                    topic, bizKey, r.getSendStatus(), i);
        }
        throw new BizException(ErrCode.MQ_SEND_FAIL, "消息发送失败 key=" + bizKey);
    }
}
```

> 注意：生产端重发会让同一条业务消息**可能被发送两次**（第一次其实成功了但响应超时，第二次又发了一遍）。这正是后面"重复消费"问题的根源之一——所以重发可以放心做，但消费端必须幂等来兜底。

---

## 33.4 Broker 端：消息进了队列还会丢吗

会。消息到了 Broker 不等于安全，还要看它**有没有真正写进磁盘**、**有没有副本**。

### 刷盘策略：同步刷盘 vs 异步刷盘

RocketMQ 收到消息先写进 PageCache（操作系统的内存缓冲），再决定什么时候落到磁盘文件 CommitLog：

```text
异步刷盘 ASYNC_FLUSH（默认）
  消息写入 PageCache 就返回 SEND_OK，后台线程攒一批再刷盘
  → 性能高，但 Broker 突然断电，PageCache 里没刷的消息丢失

同步刷盘 SYNC_FLUSH
  消息必须真正写入磁盘后才返回 SEND_OK
  → 不丢，但每条消息都等磁盘 IO，吞吐下降
```

```text
# broker.conf 关键配置
flushDiskType = SYNC_FLUSH        # 核心业务（扣费/订单）建议同步刷盘
```

**前端类比**：异步刷盘像 `localStorage.setItem` 后浏览器异步落盘——大多数时候没事，但断电可能丢；同步刷盘像调用 `fsync` 强制写穿到磁盘。

### 主从复制：单台磁盘也不够

一个 Broker 的磁盘也可能坏。生产环境用**主从架构**，主节点的消息复制到从节点：

```text
SYNC_MASTER（同步复制）：消息同步到从节点后才返回 OK —— 主挂了从节点还有，不丢
ASYNC_MASTER（异步复制）：主写完就返回，异步同步到从 —— 主磁盘坏了可能丢一点
```

```text
# broker.conf
brokerRole = SYNC_MASTER          # 核心 Topic 用同步复制
```

### Broker 端可靠性配方

把"持久化 + 刷盘 + 主从"组合起来，结论很简单：

| 业务重要性 | 刷盘 | 复制 | 代价 |
| --- | --- | --- | --- |
| 核心（扣配额、支付） | SYNC_FLUSH | SYNC_MASTER | 最慢但最稳，不丢 |
| 一般（生图任务） | ASYNC_FLUSH | SYNC_MASTER | 折中，主从兜底 |
| 不重要（埋点日志） | ASYNC_FLUSH | ASYNC_MASTER | 最快，允许偶尔丢 |

> 这部分配置一般由运维/SRE 定，前端转后端的你不一定要改，但**必须知道这三个开关决定了"消息会不会丢"**，排查丢消息时第一时间去看它们。

---

## 33.5 消费端：处理成功才能 ack

消费端是最容易出问题、也是你以后写得最多的地方。核心铁律一句话：**只有业务真正处理成功，才能返回 ack（告诉 Broker 这条消息可以删了）。**

### 反例：自动 ack 导致丢消息

```java
// ❌ 错误：收到消息就异步丢给线程池，立刻 return CONSUME_SUCCESS
@RocketMQMessageListener(topic = "ai-image-gen", consumerGroup = "svc-ai-gen")
public class WrongListener implements RocketMQListener<String> {
    @Override
    public void onMessage(String body) {
        threadPool.submit(() -> handleGenerate(body)); // 提交完就返回
        // 框架认为消费成功，把消息删了。如果线程池里 handleGenerate 崩了，消息永久丢失
    }
}
```

**前端类比**：这就像在 `Promise` 链里没 `await` 就提前 `resolve`——异步任务还没跑完，调用方却以为完成了。

### 正例：同步处理，异常不吞

```java
@RocketMQMessageListener(
        topic = "ai-image-gen",
        consumerGroup = "svc-ai-gen",
        consumeMode = ConsumeMode.CONCURRENTLY,   // 并发消费
        maxReconsumeTimes = 5)                     // 失败最多重试 5 次后进 DLQ
public class ImageGenListener implements RocketMQListener<String> {

    @Autowired private ImageGenService imageGenService;

    @Override
    public void onMessage(String body) {
        TaskMsg msg = JsonUtil.parse(body, TaskMsg.class);
        // 同步处理：方法正常返回 = 框架自动 ack；抛异常 = 不 ack，触发重试
        imageGenService.handle(msg);
    }
}
```

只要 `onMessage` **正常返回**就视为消费成功（ack）；只要**抛出异常**，RocketMQ 就不删消息、按退避策略重投。所以消费端的异常**绝对不能 catch 后吞掉**，否则等于骗框架"我成功了"——和 [事务与一致性](/back-end/frontend-backend-guide/11-transactions-consistency) 里 `try-catch` 吞异常导致事务误提交是同一类坑。

---

## 33.6 重复消费与幂等（本章重点）

### 为什么必然会重复

MQ 的投递语义只有三种，而工程上能落地的只有"至少一次"：

```text
至多一次 at-most-once   —— 不重试，可能丢消息（不可接受）
至少一次 at-least-once  —— 失败重试，消息不丢但可能重复（RocketMQ 默认，业界主流）
恰好一次 exactly-once   —— 理论理想，端到端实现代价极高，工程上基本靠"至少一次 + 幂等"模拟
```

只要选了"至少一次"，重复就是**必然**而非偶然。重复来自三处：

1. 生产端重发（33.3 提到的，第一次其实成功了但响应超时又发了一次）；
2. 消费端处理成功了，但返回 ack 前网络抖动 / 消费者重启，Broker 没收到 ack，重投；
3. 消费组 rebalance（扩缩容时队列重新分配），同一条消息被换了个消费者再投一次。

**结论：消费端必须幂等。** 幂等 = 同一条消息处理一次和处理十次，最终结果完全一样。这不是可选项，是分布式系统的命根子。

### 四种幂等实现手段

```text
① 数据库唯一索引     —— 最简单，靠 DB 报唯一键冲突天然去重
② 去重表 + 唯一业务键 —— 先插一条"我处理过了"，插不进去就说明重复
③ Redis SETNX        —— 用 setIfAbsent 抢一个标记，适合高并发、可容忍极端边界
④ 状态机             —— 只允许 PENDING→DONE，重复消息看到已是 DONE 直接跳过
```

### 实战：扣配额的幂等消费

场景：svc-ai 生图前要从 svc-user 扣 1 次配额。消息重投绝不能把配额多扣一次。下面用**去重表 + 数据库唯一索引**，整段放进同一个本地事务：

```java
@Service
public class QuotaConsumeService {

    @Autowired private QuotaLogMapper quotaLogMapper;
    @Autowired private UserQuotaMapper userQuotaMapper;

    /**
     * 幂等扣配额：同一个 taskId 无论消费几次，只扣一次
     */
    @Transactional(rollbackFor = Exception.class)
    public RtData<Void> deductQuota(TaskMsg msg) {
        // 第一步：幂等卫兵——往去重表插一条，靠 task_id 唯一索引拦重复
        int inserted = quotaLogMapper.insertIfAbsent(
                msg.getTaskId(),    // 唯一业务键
                msg.getUserId(),
                msg.getCost());
        if (inserted == 0) {
            // 插不进去 = 这个 taskId 已经处理过，直接当成功返回，不再扣
            log.warn("重复消息，已扣过配额，跳过 taskId={}", msg.getTaskId());
            return RtData.ok();
        }

        // 第二步：真正扣减（带余额判断，防扣成负数）
        int rows = userQuotaMapper.decreaseIfEnough(msg.getUserId(), msg.getCost());
        if (rows == 0) {
            // 配额不足，抛异常让事务回滚（去重表那条也一起回滚，下次重试还能进来）
            throw new BizException(ErrCode.QUOTA_NOT_ENOUGH, "配额不足");
        }
        return RtData.ok();
    }
}
```

对应的去重表和唯一索引：

```sql
CREATE TABLE quota_log (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    task_id    VARCHAR(64)  NOT NULL COMMENT '业务唯一键，来自消息',
    user_id    BIGINT       NOT NULL,
    cost       INT          NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_id (task_id)   -- ★ 幂等的核心：同一 task_id 只能插一行
) ENGINE=InnoDB COMMENT='扣配额去重流水';
```

`insertIfAbsent` 用 `INSERT ... ON DUPLICATE KEY` 或捕获唯一键冲突实现，返回 0 即重复。唯一索引相关细节见 [SQL 与索引](/back-end/frontend-backend-guide/10-sql-and-indexes)。

> **关键点**：去重表的插入和实际扣减必须在**同一个本地事务**里，否则会出现"插了去重记录但扣减失败"的中间态。事务边界为什么这么定，见 [事务与一致性](/back-end/frontend-backend-guide/11-transactions-consistency)。

### Redis SETNX 版（轻量但有边界）

如果不想动 DB，也可以用 Redis 抢标记。它快，但要注意"标记成功后业务崩了"的边界：

```java
public boolean tryConsume(String taskId) {
    // setIfAbsent = SETNX，第一个抢到的返回 true，过期时间防止标记永久占用
    Boolean ok = redisTemplate.opsForValue()
            .setIfAbsent("mq:dedup:" + taskId, "1", Duration.ofHours(2));
    return Boolean.TRUE.equals(ok);
}
```

权衡：Redis 方案没有事务兜底——若"抢到标记后、业务执行前"进程崩了，这条消息重投时会因标记已存在被错误跳过。所以**对一致性要求高的扣费类操作，优先用数据库唯一索引；Redis SETNX 更适合"重复一次也不致命"的去重**。Redis 用法见 [Redis 实战](/back-end/frontend-backend-guide/12-redis-in-practice)。

---

## 33.7 消息顺序：什么时候才真的需要

大多数业务**不需要**全局顺序，强行要求顺序会牺牲并发性能。先分清两种"顺序"：

```text
全局顺序 —— 整个 Topic 所有消息严格按发送顺序消费
            代价：只能单队列单线程消费，吞吐极低，几乎不用

分区顺序 —— 同一业务 key 的消息按顺序，不同 key 之间无所谓
            做法：相同 key（如 taskId）路由到同一个队列，队列内顺序消费
            这才是实际工程里说的"顺序消息"
```

### RocketMQ 顺序消息怎么做

两步：发送时用 `MessageQueueSelector` 把同一个 key 的消息投到**同一个队列**；消费时用 `ConsumeMode.ORDERLY` 保证队列内**单线程顺序**消费。

```java
// 生产端：同一个 taskId 的消息进同一个队列
rocketMQTemplate.syncSendOrderly(
        "ai-task-status",
        MessageBuilder.withPayload(JsonUtil.toJson(msg)).build(),
        msg.getTaskId());   // ★ hashKey：决定进哪个队列

// 消费端：顺序消费
@RocketMQMessageListener(
        topic = "ai-task-status",
        consumerGroup = "svc-canvas-status",
        consumeMode = ConsumeMode.ORDERLY)   // ★ 队列内单线程，保证顺序
public class TaskStatusListener implements RocketMQListener<String> { /* ... */ }
```

### 本项目什么时候需要顺序

任务状态流转 `PENDING → PROCESSING → DONE` 必须按顺序到达，否则可能出现"先收到 DONE，又收到 PROCESSING"把状态改回去的诡异 bug。让同一个 `taskId` 的状态消息走顺序消息即可。但要注意：**顺序消费是单线程的，一条消息处理慢会阻塞整个队列**，所以只对真正需要顺序的少量消息用，生图本体这种耗时操作仍用并发消费。

---

## 33.8 消费失败、重试与死信队列（DLQ）

消费抛异常后 RocketMQ 不会无限重试，而是按**退避策略**逐步拉长间隔，超过最大次数就进**死信队列**等人工处理。

```text
第 1 次失败 → 等 10s 重投
第 2 次失败 → 等 30s
第 3 次失败 → 等 1min
...逐级退避...
第 N 次失败（达到 maxReconsumeTimes）
        ↓
进入死信队列 DLQ：Topic 名为 %DLQ%<consumerGroup>
        ↓
告警 + 人工介入（修数据 / 修代码 / 手动重发）
```

**前端类比**：和你给 `axios` 加的请求重试拦截器一个思路——失败重试几次、每次间隔拉长（退避），还不行就放弃并上报错误。

### 区分"可重试"与"不可重试"异常

不是所有失败都该重试。参数错误重试一万次也还是错，只会浪费资源；只有"暂时性故障"才值得重试：

```java
@Override
public void onMessage(String body) {
    TaskMsg msg = JsonUtil.parse(body, TaskMsg.class);
    try {
        imageGenService.handle(msg);
    } catch (AiApiTimeoutException e) {
        // 可重试：下游 AI 服务超时，抛出去让 MQ 自动退避重试
        log.warn("AI 超时，等待重试 taskId={}", msg.getTaskId());
        throw e;
    } catch (IllegalPromptException e) {
        // 不可重试：prompt 违规，重试一万次也没用
        // 直接落库标记失败 + 正常返回（不让它进 DLQ 浪费资源）
        log.error("prompt 非法，标记任务失败 taskId={}", msg.getTaskId(), e);
        taskService.markFailed(msg.getTaskId(), "prompt 违规");
        // 正常返回 = ack，消息消费完成
    }
}
```

### 怎么处理 DLQ 里的消息

死信队列不会自己消化，必须有人管。生产实践：给 `%DLQ%<consumerGroup>` 配一个监控消费者，把死信落到一张 `mq_dead_letter` 表并触发告警，运维在控制台或工单里决定是修数据后重发、还是直接丢弃。**死信队列长期为空是健康信号，一旦有消息堆积说明有一类业务在系统性失败**，要立刻查。

---

## 33.9 事务消息：本地操作与发消息的原子性

### 问题：先扣配额还是先发消息

考虑 svc-user 的一个场景：扣减用户配额（本地 DB 操作）后，要发一条"配额变更"消息通知 svc-canvas。这两步不在一个事务里，先做哪个都有坑：

```text
先扣配额，再发消息  → 扣成功了，发消息时进程崩了 → 配额扣了但下游不知道（消息丢）
先发消息，再扣配额  → 消息发了，扣配额失败了      → 下游以为扣了，实际没扣（数据不一致）
```

这是经典的"本地事务 + 发消息"原子性难题，本质和 [事务与一致性](/back-end/frontend-backend-guide/11-transactions-consistency) 讲的跨资源一致性是同一类问题。

### RocketMQ 事务消息（half message 机制）

RocketMQ 用"半消息（half message）+ 事务回查"解决：

```text
① 发送半消息 half message
   Broker 收下，但标记为"暂不可投递"，消费者看不到
        ↓
② 执行本地事务（扣配额 DB 操作）
        ↓
③ 根据本地事务结果，向 Broker 发 commit / rollback
   commit   → 半消息转正，消费者可见
   rollback → 半消息丢弃，谁也收不到
        ↓
④ 万一第 ③ 步的确认丢了，Broker 定时"回查"本地事务状态
   生产者实现 checkLocalTransaction，告诉 Broker 到底成没成
```

```java
@RocketMQTransactionListener
public class QuotaTxListener implements RocketMQLocalTransactionListener {

    @Autowired private QuotaService quotaService;

    // 执行本地事务：扣配额
    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        try {
            quotaService.deduct((TaskMsg) arg);  // 本地 DB 事务
            return RocketMQLocalTransactionState.COMMIT;    // 成功 → 半消息转正
        } catch (Exception e) {
            return RocketMQLocalTransactionState.ROLLBACK;  // 失败 → 半消息丢弃
        }
    }

    // 回查：Broker 没收到确认时来问"那笔本地事务到底成没成"
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        String taskId = (String) msg.getHeaders().get(RocketMQHeaders.KEYS);
        boolean done = quotaService.isDeducted(taskId); // 查 DB 是否真扣了
        return done ? RocketMQLocalTransactionState.COMMIT
                    : RocketMQLocalTransactionState.ROLLBACK;
    }
}
```

事务消息保证的是**生产端"本地事务"与"消息发出"的原子性**（要么都成、要么都不成），下游消费仍是异步的最终一致——所以消费端**依然要幂等**。这是 [事务与一致性](/back-end/frontend-backend-guide/11-transactions-consistency) 里"最终一致性"的工程落地之一。

---

## 33.10 消息积压排查（呼应排查方法论）

**症状**：生图任务越来越慢，前端轮询半天还是 `PENDING`，控制台看到队列堆积量（消息总数 - 已消费位点）持续上涨。

**目标**：定位"消费跟不上生产"的根因，恢复消费速度。

### 第一步：看积压量和消费 TPS

```bash
# 查某个消费组在某 Topic 上的积压情况
sh mqadmin consumerProgress -g svc-ai-gen -n 127.0.0.1:9876
```

预期输出（样例）：

```text
#Topic            #Broker     #QID    #BrokerOffset  #ConsumerOffset  #Diff
ai-image-gen      broker-a    0       1502340        1340120          162220
ai-image-gen      broker-a    1       1498765        1335900          162865
...
Consume TPS: 12          Diff Total: 1289000
```

**怎么读这段输出**：
- `BrokerOffset` 是消息已写入的最大位点，`ConsumerOffset` 是已消费到的位点；
- `Diff` = 两者之差 = **该队列积压条数**，这里每个队列积压 16 万，合计 `Diff Total: 1289000` 近 130 万条；
- `Consume TPS: 12` 表示当前每秒只消费 12 条——生产远快于消费，必然越堆越多。

### 第二步：判断是"消费慢"还是"消费者太少"

```bash
# 看消费者实例数量与订阅关系
sh mqadmin consumerConnection -g svc-ai-gen -n 127.0.0.1:9876
```

```text
ConnectionId: 10.0.0.21@svc-ai-1   Version: V5_1_0
ConnectionId: 10.0.0.22@svc-ai-2   Version: V5_1_0
ConsumeType: CONSUME_PASSIVELY     MessageModel: CLUSTERING
```

**怎么读**：只有 2 个消费者实例，而 Topic 有 8 个队列——并行度严重不足。

### 第三步：对症下药

| 根因 | 现象 | 处置 |
| --- | --- | --- |
| 消费者实例太少 | 队列数 > 消费者数，部分队列无人消费 | 扩 svc-ai 副本（k8s 扩 Pod），但**消费者数不能超过队列数**，否则多出来的空转 |
| 单条消费太慢 | TPS 低，但 CPU/线程没打满 | 排查下游（AI API 慢？DB 慢查询？锁等待？），看线程栈 |
| 消费线程池太小 | CPU 没满但 TPS 上不去 | 调大 `consumeThreadMax` |
| 消费在重试打转 | 同一批消息反复重投 | 看是不是某条毒消息一直失败，挪进 DLQ 别堵着 |

> 积压排查只是排查方法论的一个应用，完整的"症状→假设→验证→定位"流程在 [排查方法论](/back-end/frontend-backend-guide/27-troubleshooting-methodology) 与 [排查实战手册](/back-end/frontend-backend-guide/28-troubleshooting-playbook) 里。一句话口诀：**积压 = 生产 > 消费，先看 TPS 定位是慢还是少，再扩消费者或治消费慢。**

---

## 小结

- **MQ 的价值是异步、解耦、削峰**（前端的 EventBus 升级版）；本项目用它把 svc-canvas 的"提交任务"和 svc-ai 的"实际生图"解耦，用户提交即返回 taskId。
- **消息防丢分三段**：生产端用同步发送 + 校验 SendStatus + 失败重发；Broker 端靠持久化 + 同步刷盘 + 主从复制；消费端必须**处理成功才 ack、异常绝不吞**。
- **重复消费是"至少一次"投递的必然结果**，消费端**必须幂等**：首选数据库唯一索引 / 去重表（带本地事务），Redis SETNX 适合非关键去重，状态机适合状态流转。
- **顺序消息靠同一 key 进同一队列 + ORDERLY 单线程消费**，代价是并发下降，只对真正需要顺序的少量消息（如任务状态流转）使用。
- **消费失败按退避重试，超限进死信队列 DLQ** 等人工介入；要区分可重试（超时）与不可重试（参数错）异常，避免无效重试。
- **事务消息用 half message + 回查**解决"本地事务 + 发消息"的原子性，是最终一致性的工程落地，下游消费仍需幂等。

### 自测

1. svc-canvas 用 `producer.send(msg)` 发任务后没检查返回值，某天 Broker 主从切换期间丢了一批任务消息。指出这里至少两个可靠性疏漏，并说明各自怎么补。
2. 扣配额的消费逻辑为什么"必须"幂等？请描述一个会导致同一个 taskId 被消费两次的具体时序，并说明你用哪种手段保证只扣一次。
3. 线上生图任务积压了 100 万条，`Consume TPS` 只有个位数，但 svc-ai 的 CPU 也没打满。你会按什么顺序排查、可能的根因有哪些？

### 下一章

可靠的消息链路搭好后，对外暴露的接口同样需要规范——下一章进入 [API 设计](/back-end/frontend-backend-guide/34-api-design)，看 RESTful 风格、统一响应 RtData、版本管理和错误码在本项目里怎么落地。

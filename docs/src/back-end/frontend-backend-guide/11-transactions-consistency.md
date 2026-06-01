# 事务与一致性

> 这一章解决一个核心焦虑：**怎么保证一组数据库操作"要么全成功、要么全失败"，永远不会停在中间状态。**
>
> 前端类比：你做乐观更新（optimistic update），先把 UI 改了，等接口回来发现失败，再 `rollback` 回原来的状态。事务就是数据库层面的这套"失败就回滚"，只是它比你的前端代码可靠得多，而且是数据库帮你兜底。

---

## 11.1 为什么需要事务

先看一个会出人命的 bug。在 `svc-user` 里给用户扣配额（quota）后，要往 `quota_log` 写一条流水：

```java
// 危险写法：两步操作没有事务包裹
public RtData<Void> deductQuota(Long userId, int cost) {
    userMapper.decreaseQuota(userId, cost);     // 第 1 步：扣余额
    quotaLogMapper.insertLog(userId, cost);     // 第 2 步：写流水
    return RtData.ok();
}
```

如果第 1 步执行成功，但第 2 步抛异常（比如数据库连接突然断了），结果就是：**用户的配额扣掉了，但没有任何流水记录**。对账时永远对不平，用户投诉"我的额度凭空少了"，你查不到原因。

这就是经典的转账问题：A 给 B 转 100 元，必须满足 `A -100` 和 `B +100` 同时成功；如果只成功一半，钱就凭空消失或凭空多出。

> 前端类比：相当于你 `await` 了两个接口，第一个成功了，第二个 reject 了，但你没写 `try/catch` 也没回滚——页面状态就此错乱。事务就是把这两步绑成一个原子操作。

**事务（Transaction）的定义**：一组操作被当作一个不可分割的整体，要么全部生效，要么全部撤销，绝不会停在中间。

---

## 11.2 ACID 四性

事务的可靠性由四个特性保证，缩写 ACID。用"扣配额 + 写流水"来逐个理解：

```text
┌──────────────────────────────────────────────────────────────┐
│  A  Atomicity   原子性                                        │
│     扣余额 + 写流水 是一个整体，中途失败就全部回滚            │
│     💡 前端类比：Promise.all 语义——但失败时还能"撤销已成功的" │
├──────────────────────────────────────────────────────────────┤
│  C  Consistency 一致性                                        │
│     事务前后数据满足业务规则（如 余额≥0、总额守恒）          │
│     💡 前端类比：zod schema 校验——状态切换前后都得合法        │
├──────────────────────────────────────────────────────────────┤
│  I  Isolation   隔离性                                        │
│     并发的事务互不干扰，像各自独占数据库一样                  │
│     💡 前端类比：每个请求有独立的闭包作用域，不串数据         │
├──────────────────────────────────────────────────────────────┤
│  D  Durability  持久性                                        │
│     事务一旦提交（commit），就算立刻断电也不丢                │
│     💡 前端类比：写进了 localStorage 而不是只在内存里的 state │
└──────────────────────────────────────────────────────────────┘
```

- **原子性（Atomicity）**：靠数据库的 `undo log`（回滚日志）实现。出错时按日志把已做的改动倒回去。
- **一致性（Consistency）**：是目的，不是手段——它由另外三性 + 业务约束（外键、`CHECK`、唯一索引）共同保证。
- **隔离性（Isolation）**：最复杂的一性，下一节专门讲。隔离做得越严，并发性能越差，这是个权衡。
- **持久性（Durability）**：靠 `redo log`（重做日志）+ 刷盘机制。提交时先把日志落盘，数据页可以稍后再写。

四性里你日常踩坑最多的是**原子性**（忘了加事务）和**隔离性**（并发读到脏数据）。

---

## 11.3 隔离级别：三种并发问题

隔离性如果做到 100%（完全串行），并发性能会惨不忍睹。所以数据库提供了几档"隔离级别"，让你在**正确性**和**性能**之间选。要选对档位，先得认识不做隔离时会出的三种问题。

### 脏读（Dirty Read）

事务 A 读到了事务 B **还没提交**的数据，结果 B 回滚了，A 读到的是一个"根本不存在过"的值。

```text
事务 A（查配额）          事务 B（扣配额，最后回滚）
                          UPDATE quota = 0 WHERE id=1   (未提交)
SELECT quota → 读到 0  ←── 读到了 B 的未提交修改！
                          ROLLBACK  (B 回滚，quota 其实还是 100)
基于"0"做了判断 → 错误
```

### 不可重复读（Non-Repeatable Read）

事务 A 内**两次读同一行**，结果不一样——因为中间有别的事务改了这行并提交了。问题出在"同一行被 UPDATE"。

```text
事务 A                        事务 B
SELECT quota → 100
                              UPDATE quota=50 WHERE id=1; COMMIT
SELECT quota → 50   ←── 同一事务内两次读结果不同
```

### 幻读（Phantom Read）

事务 A 内**两次查同一范围**，第二次多出/少了几行——因为别的事务 `INSERT`/`DELETE` 了符合条件的行。问题出在"行数变了"，不是某一行的值变了。

```text
事务 A                                事务 B
SELECT count(*) FROM task
  WHERE user_id=1 → 3 条
                                      INSERT INTO task ... user_id=1; COMMIT
SELECT count(*) ... → 4 条  ←── 凭空多出一行，像见了鬼
```

> 不可重复读关注**单行被修改**，幻读关注**结果集行数变化**。这俩很像，记住"改值 vs 增删行"的区别就不会混。

---

## 11.4 四个隔离级别与对照表

SQL 标准定义了四个隔离级别，越往下越严格、越安全，但并发吞吐越低：

```text
读未提交  READ UNCOMMITTED   几乎不加锁，啥都能读到，最快也最危险
读已提交  READ COMMITTED     只能读到已提交数据（Oracle/PostgreSQL 默认）
可重复读  REPEATABLE READ    事务内多次读同一数据结果一致（MySQL 默认）
串行化    SERIALIZABLE       事务排队串行执行，最安全也最慢
```

**问题 × 级别对照表**（✅ 表示该级别会出现这个问题，❌ 表示已解决）：

| 隔离级别 | 脏读 | 不可重复读 | 幻读 |
| --- | :---: | :---: | :---: |
| 读未提交 READ UNCOMMITTED | ✅ 会 | ✅ 会 | ✅ 会 |
| 读已提交 READ COMMITTED | ❌ 不会 | ✅ 会 | ✅ 会 |
| 可重复读 REPEATABLE READ | ❌ 不会 | ❌ 不会 | ✅ 会* |
| 串行化 SERIALIZABLE | ❌ 不会 | ❌ 不会 | ❌ 不会 |

> \* 按 SQL 标准，可重复读理论上仍允许幻读。但 **MySQL 的 InnoDB 用 MVCC + Next-Key Lock（间隙锁）在可重复读级别下基本也避免了幻读**，所以 MySQL 默认级别已经相当安全。这也是 MySQL 默认选可重复读、而 PostgreSQL/Oracle 选读已提交的差异来源。

**实操：查看和验证 MySQL 当前隔离级别**

症状/目标：确认你连的 MySQL 实例用的是哪个隔离级别。

```bash
mysql -h 127.0.0.1 -u root -p svc_user_db
```

```sql
-- MySQL 8.0 查全局和当前会话的隔离级别
SELECT @@global.transaction_isolation, @@session.transaction_isolation;
```

预期输出：

```text
+--------------------------------+---------------------------------+
| @@global.transaction_isolation | @@session.transaction_isolation |
+--------------------------------+---------------------------------+
| REPEATABLE-READ                | REPEATABLE-READ                 |
+--------------------------------+---------------------------------+
```

怎么读：两列都是 `REPEATABLE-READ` 说明用的是 MySQL 默认级别。`global` 影响新连接，`session` 只影响当前连接——如果你临时执行过 `SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;`，两列就会不一致。

结论：除非有明确理由（极少数高并发统计场景），**保持默认的可重复读即可，不要随手改隔离级别**——改错了会引入难以复现的并发 bug。MySQL 隔离级别和 MVCC 的更多细节见 [SQL 进阶](/back-end/database/mysql/advanced)。

---

## 11.5 Spring 事务：@Transactional

在 Spring Boot 里你几乎不会手写 `begin/commit/rollback`，而是在 Service 方法上打一个注解 `@Transactional`，Spring 帮你在方法开始时开启事务、正常返回时提交、抛出异常时回滚。

```java
@Service
public class QuotaService {

    private final UserMapper userMapper;
    private final QuotaLogMapper quotaLogMapper;

    @Transactional(rollbackFor = Exception.class)
    public RtData<Void> deductQuota(Long userId, int cost) {
        userMapper.decreaseQuota(userId, cost);   // 扣余额
        quotaLogMapper.insertLog(userId, cost);   // 写流水
        // 方法正常返回 → Spring 自动 commit
        // 中途抛异常   → Spring 自动 rollback，两步一起撤销
        return RtData.ok();
    }
}
```

> 前端类比：`@Transactional` 像一个高阶函数 `withTransaction(fn)`——它在 `fn` 外面包了一层 `try { begin(); fn(); commit(); } catch { rollback(); }`，你只写中间的业务逻辑，提交/回滚交给框架。

它怎么"包一层"的？靠 **AOP 动态代理**：Spring 不会让外部直接调用你的原始对象，而是生成一个代理对象，在代理里塞进 begin/commit/rollback 逻辑，再调你的真方法。**记住这个"代理"机制，下一节的两个坑全因它而起。** 注解本质上就是前端的装饰器，Spring 注解原理可回顾 [关键注解速查](/back-end/frontend-backend-guide/08-annotations-cheatsheet)。

```text
你以为：      caller ──→ QuotaService.deductQuota()
实际上：      caller ──→ [代理对象] 开事务 → 调真方法 → 提交/回滚
```

---

## 11.6 两个经典坑（90% 的新人都踩过）

### 坑一：同类内部方法自调用，事务失效

```java
@Service
public class CanvasTaskService {

    // 外层方法没加事务
    public RtData<Void> submit(TaskDTO dto) {
        validate(dto);
        this.doSubmit(dto);   // ⚠️ this. 调用：直接调的是原始对象，不走代理！
        return RtData.ok();
    }

    @Transactional(rollbackFor = Exception.class)
    public void doSubmit(TaskDTO dto) {
        taskMapper.insert(dto);
        quotaMapper.deduct(dto.getUserId(), dto.getCost());  // 这里抛异常不会回滚！
    }
}
```

**为什么失效**：`this.doSubmit()` 调用的是**原始对象自己**，没经过那个加了事务逻辑的代理对象。`@Transactional` 形同虚设，`doSubmit` 抛异常也不会回滚——`task` 插进去了，`quota` 没扣成功，数据又对不平了。

> 前端类比：你给一个函数包了 `withLogging(fn)` 得到 `wrapped`，但在另一个函数内部直接调了原始的 `fn` 而不是 `wrapped`——日志当然不会打。代理和被代理是两个不同的对象。

**正确做法**（三选一）：

```java
// 方案 A（推荐）：把事务方法抽到另一个 Bean，通过依赖注入调用，自然走代理
@Service
public class CanvasTaskService {
    private final TaskTxService txService;   // 注入另一个 Bean
    public RtData<Void> submit(TaskDTO dto) {
        validate(dto);
        txService.doSubmit(dto);             // 跨 Bean 调用，走代理，事务生效
        return RtData.ok();
    }
}

// 方案 B：注入自己的代理（注意循环依赖，需 @Lazy）
@Autowired @Lazy
private CanvasTaskService self;
// 调用：self.doSubmit(dto);

// 方案 C：直接在外层方法 submit 上加 @Transactional，不做内部自调用
```

依赖注入（DI）为什么能拿到代理对象、Bean 是怎么被 Spring 容器管理的，可回顾 [Spring IoC 与依赖注入](/back-end/frontend-backend-guide/06-spring-boot-ioc-di)。

### 坑二：try-catch 吞掉异常，事务不回滚

```java
@Transactional(rollbackFor = Exception.class)
public RtData<Void> deductQuota(Long userId, int cost) {
    try {
        userMapper.decreaseQuota(userId, cost);
        quotaLogMapper.insertLog(userId, cost);   // 假设这里抛了异常
    } catch (Exception e) {
        log.error("扣配额失败", e);    // ⚠️ 异常被你"吃"掉了，没有再抛出去
        return RtData.fail("扣配额失败");
    }
    return RtData.ok();
}
```

**为什么不回滚**：Spring 判断要不要回滚，看的是"方法**有没有把异常抛到代理那一层**"。你在方法内部 `catch` 住了，方法**正常返回**了，代理以为一切顺利 → 执行 `commit`。结果第一步的扣款被提交了，第二步失败，数据再次对不平。

> 前端类比：你 `catch` 了 Promise 的 reject 还 `return` 了一个正常值，外层 `.then` 完全不知道出过错——错误被你"消化"掉了。

**正确做法**：要么不要 catch（让异常自然冒泡到代理），要么 catch 后**手动标记回滚**或**重新抛出**：

```java
// 方案 A：catch 后重新抛出（保留原始异常栈）
catch (Exception e) {
    log.error("扣配额失败", e);
    throw e;   // 或 throw new BizException(ErrCode.QUOTA_DEDUCT_FAIL, e);
}

// 方案 B：不抛异常，但手动标记当前事务必须回滚
catch (Exception e) {
    log.error("扣配额失败", e);
    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    return RtData.fail("扣配额失败");
}
```

### 顺带：rollbackFor 为什么要写

Spring 的默认规则有个反直觉的点：**默认只对 `RuntimeException` 和 `Error` 回滚，对受检异常（checked exception，如 `IOException`）默认不回滚！**

```java
@Transactional                                // ⚠️ 抛 IOException 不会回滚
@Transactional(rollbackFor = Exception.class) // ✅ 任何异常都回滚（推荐写法）
```

结论：**统一写 `@Transactional(rollbackFor = Exception.class)`**，别依赖默认行为。本项目所有 Service 事务方法都这么写。Java 受检异常 vs 运行时异常的区别见 [Java 异常处理](/back-end/java/05-exception)。

---

## 11.7 分布式一致性：跨服务怎么办

本地事务（单库 `@Transactional`）只能管住**同一个数据库**。但本项目是微服务，"提交生图任务"这件事横跨多个服务和多个库：

```text
用户点"生成" → svc-canvas 创建任务（写 MongoDB）
            → 调 svc-user 扣配额（写 MySQL）
            → 通知 svc-ai 开始生图（异步）

这三步分散在三个进程、两种数据库，
@Transactional 完全管不到跨进程的回滚 —— 本地事务在这里无能为力。
```

> 前端类比：单库事务像一个组件内部的 state 更新（同步、可控）；分布式一致性像你要同时更新三个互不相识的微前端子应用的状态——没有一个"全局回滚"按钮能一键撤销所有人。

### CAP 一句话

分布式系统里，**一致性（Consistency）、可用性（Availability）、分区容错性（Partition tolerance）三者不可兼得，而网络分区（P）在分布式里必然会发生，所以你实际只能在 C 和 A 之间二选一。**

绝大多数互联网业务（包括本项目）选 **AP——优先保证可用，牺牲"实时强一致"，改为追求"最终一致"**。

### 强一致 vs 最终一致

- **强一致（Strong Consistency）**：任何时刻所有节点看到的数据都一样。实现手段如分布式事务 2PC、Seata，代价是要锁住多个资源、性能差、还可能因为协调者宕机而卡死。**只在金融核心账务这类场景才值得。**
- **最终一致（Eventual Consistency）**：允许短时间内各节点数据不一致，但保证"最终"会一致。代价小、可用性高，**是绝大多数业务的正确选择。**

> 前端类比：强一致像 `await` 同步等所有子任务返回才渲染（慢但准）；最终一致像先 `setState` 乐观更新让用户看到结果，后台慢慢把真实状态对齐（快，且允许短暂不一致）。

---

## 11.8 本项目实战：用 MQ 实现最终一致

回到"扣配额 + 创建生图任务"。我们不追求强一致，用 **RocketMQ + 本地事务 + 失败重试/补偿** 实现最终一致。核心思路：**把跨服务的"扣配额"从同步调用改成异步消息**，每一步只保证自己的本地事务，靠消息的可靠投递把两边"最终"对齐。

```text
┌────────────────────────────────────────────────────────────────┐
│ svc-canvas：一个本地事务里完成两件事                            │
│   1. INSERT 生图任务，状态 = PENDING                            │
│   2. 发送 RocketMQ 消息 "TaskCreated"（事务消息，与 1 同成败）   │
│      —— 本地事务保证：任务和消息要么都成功，要么都没有          │
└───────────────────────────────┬────────────────────────────────┘
                                │  消息可靠投递（失败自动重试）
                                ▼
┌────────────────────────────────────────────────────────────────┐
│ svc-user：消费消息，本地事务里扣配额 + 写流水                    │
│   成功 → ACK，消息消费完成                                       │
│   失败 → 不 ACK，RocketMQ 按退避策略自动重投（最多 N 次）        │
│   仍失败 → 进入死信队列（DLQ），告警 + 人工/补偿任务介入        │
└────────────────────────────────────────────────────────────────┘
```

为什么这样就"最终一致"了：任务先以 `PENDING` 落库（用户立刻看到"排队中"，可用性高）；扣配额通过消息异步完成，哪怕 `svc-user` 临时挂了，消息也不会丢，恢复后重投，配额最终会被扣掉，两边数据最终对齐。

> 前端类比：这就是**乐观更新 + 失败重试队列**。先让 UI 显示成功（任务 PENDING），后台静默地把请求重试到成功为止；万一彻底失败，才弹错误提示（死信告警）。

**关键前提：消费端必须幂等**

异步重试带来一个新问题——**同一条消息可能被投递多次**（网络抖动、ACK 丢失都会触发重投）。如果 `svc-user` 每收到一次就扣一次配额，重投 3 次就扣了 3 倍，用户直接被扣穿。

所以**消费逻辑必须幂等**：同一条消息处理一次和处理十次，结果完全一样。常见做法是用消息的全局唯一 ID 做去重：

```java
@Transactional(rollbackFor = Exception.class)
public void onTaskCreated(TaskCreatedMsg msg) {
    // 幂等卫兵：用唯一键拦截重复消息
    int inserted = quotaLogMapper.insertIfAbsent(msg.getMsgId(), msg.getUserId(), msg.getCost());
    if (inserted == 0) {
        log.warn("重复消息，已处理过，直接跳过 msgId={}", msg.getMsgId());
        return;   // 已处理过，直接返回，保证幂等
    }
    userMapper.decreaseQuota(msg.getUserId(), msg.getCost());
}
```

`insertIfAbsent` 靠 `quota_log` 表上 `msg_id` 的**唯一索引**实现：重复消息插不进去，返回 0，直接跳过扣款。唯一索引与索引设计见 [SQL 与索引](/back-end/frontend-backend-guide/10-sql-and-indexes)。

> 幂等是分布式系统的命根子，从重试、超时、补偿到接口设计都绕不开。它的完整设计模式（唯一 ID、状态机、去重表、RocketMQ 消息可靠性投递、死信队列处理）放在 [消息队列与可靠性](/back-end/frontend-backend-guide/33-mq-reliability) 专门讲。

---

## 11.9 决策速查：什么时候用什么

| 场景 | 用什么 | 理由 |
| --- | --- | --- |
| 同一个库的多步写（扣余额+写流水） | 本地事务 `@Transactional` | 简单可靠，首选 |
| 跨服务/跨库，允许短暂不一致 | MQ + 本地事务 + 幂等消费 | 最终一致，高可用，本项目主力方案 |
| 跨服务，但要求强一致（核心账务） | 分布式事务（Seata/2PC） | 代价大，非必要不用 |
| 只读，不改数据 | 不需要事务 | 别给查询方法乱加 `@Transactional` |

---

## 小结

- **事务保证"要么全成功要么全回滚"**，靠 ACID 四性兜底；日常踩坑集中在原子性（忘加事务）和隔离性（并发脏数据）。
- **隔离级别是正确性与性能的权衡**：MySQL 默认可重复读，且用 MVCC + 间隙锁基本消除了幻读，无特殊理由不要改默认级别。
- **Spring 的 `@Transactional` 靠 AOP 代理实现**，两个致命坑都源于代理：同类内部 `this.` 自调用会绕过代理使事务失效；`try-catch` 吞掉异常会让代理误以为成功而提交。统一写 `rollbackFor = Exception.class`。
- **微服务跨库无法用本地事务**：CAP 下多数业务选 AP + 最终一致，用 MQ 把同步调用改成可靠异步消息，每步只管自己的本地事务。
- **最终一致的前提是幂等**：消息会重投，消费端必须保证处理一次和多次结果一致，常用唯一索引/去重表实现。

### 自测

1. `deductQuota` 方法上加了 `@Transactional(rollbackFor = Exception.class)`，但写流水那步抛异常后扣款依然被提交了。给出两个最可能的原因。
2. MySQL 默认隔离级别是哪个？它默认能不能避免幻读？为什么 PostgreSQL 选的是另一个级别？
3. 用 MQ 实现"扣配额+创建任务"的最终一致时，为什么消费端的幂等是必须的？不做幂等会发生什么？

### 下一章

数据库的并发与一致性聊完了，缓存层同样绕不开一致性问题——下一章进入 [Redis 实战](/back-end/frontend-backend-guide/12-redis-in-practice)，看缓存、限流和分布式锁在本项目里怎么落地。

# 性能与高并发

> 前面几章把后端的"零件"拆开讲过了：缓存（[Redis 实战](/back-end/frontend-backend-guide/12-redis-in-practice)）、连接池（[连接池](/back-end/frontend-backend-guide/13-connection-pools)）、线程池（[线程池与 Executor](/back-end/frontend-backend-guide/15-thread-pools-executor)）、异步（[异步编程](/back-end/frontend-backend-guide/17-async-programming)）、GC（[垃圾回收 GC](/back-end/frontend-backend-guide/19-garbage-collection)）。这一章把它们串成一条线：**当流量从每秒几十请求涨到每秒几万请求，系统会先在哪里崩、怎么扛住、扛不住时怎么优雅地"少崩一点"**。本章按"缓存 → 限流 → 熔断降级 → 异步削峰 → 压测找瓶颈 → 优化心法"展开，重点是限流和压测两块实操。

**前端类比**：前端做性能优化也是一整套——懒加载、虚拟列表、CDN、debounce、请求合并、骨架屏兜底。后端高并发的手段几乎能一一对应：缓存≈CDN/SWR、限流≈debounce、降级≈骨架屏/兜底 UI、异步削峰≈把任务丢进队列慢慢消费。只是后端面对的是"机器扛不住"而不是"浏览器卡顿"，代价是真金白银的服务器和真实的用户下单失败。

---

## 31.1 多级缓存：本地 + 分布式

[Redis 实战](/back-end/frontend-backend-guide/12-redis-in-practice) 讲过 Cache-Aside，那是"应用 ↔ Redis"的单级缓存。高并发下还要再加一级：**进程内的本地缓存**（Caffeine）。

```text
                        命中率自上而下递减，速度自上而下递减
  ┌──────────────────────────────────────────────────────────┐
  │  请求                                                       │
  │   │                                                         │
  │   ▼  L1 本地缓存 Caffeine（JVM 堆内，纳秒级，无网络）          │
  │  ┌───────────┐  命中 ──▶ 返回                               │
  │  │ svc-user  │                                              │
  │  └─────┬─────┘  miss                                        │
  │        ▼  L2 分布式缓存 Redis（跨进程共享，微秒级，走网络）     │
  │  ┌───────────┐  命中 ──▶ 回写 L1，返回                       │
  │  │  Redis    │                                              │
  │  └─────┬─────┘  miss                                        │
  │        ▼  回源 数据库 MongoDB（毫秒级，最慢）                  │
  │  ┌───────────┐  ──▶ 回写 L2、L1，返回                        │
  │  │ MongoDB   │                                              │
  │  └───────────┘                                              │
  └──────────────────────────────────────────────────────────┘
```

**前端类比**：这就是浏览器的缓存层级——内存缓存（memory cache，最快）→ 磁盘缓存（disk cache）→ CDN → 源站。Caffeine 是"内存缓存"，Redis 是"CDN"，数据库是"源站"。越靠近请求越快，但容量越小、越容易过期。

Caffeine 的最小用法（svc-user 缓存配额）：

```java
@Configuration
public class CacheConfig {
    @Bean
    public Cache<Long, UserQuota> quotaLocalCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)                       // 最多缓存 1 万个用户，超了按 LRU 淘汰
                .expireAfterWrite(30, TimeUnit.SECONDS)    // 写入 30 秒后过期（本地缓存 TTL 要短）
                .build();
    }
}
```

```java
public UserQuota getQuota(Long uid) {
    // L1：本地缓存，命中直接返回，连 Redis 都不碰
    UserQuota local = quotaLocalCache.getIfPresent(uid);
    if (local != null) return local;

    // L2：Redis
    String cached = redisTemplate.opsForValue().get("user:quota:" + uid);
    if (cached != null) {
        UserQuota q = JSON.parseObject(cached, UserQuota.class);
        quotaLocalCache.put(uid, q);                       // 回写 L1
        return q;
    }
    // 回源数据库
    UserQuota q = quotaRepository.findByUid(uid);
    redisTemplate.opsForValue().set("user:quota:" + uid, JSON.toJSONString(q), 5, TimeUnit.MINUTES);
    quotaLocalCache.put(uid, q);
    return q;
}
```

### 缓存什么、不缓存什么

| 适合缓存 | 不适合缓存 |
| --- | --- |
| 读多写少（配置、字典、用户基本信息） | 写多读少（每次都变，缓存命中率低） |
| 允许短暂不一致（热门榜、商品详情） | 强一致要求（账户余额、配额扣减的"权威值"） |
| 计算/查询昂贵（聚合统计、多表 JOIN） | 本身就很快的查询（按主键查一行） |
| 热点数据（少量 key 占大量流量） | 长尾数据（每个 key 只被访问一两次，白占内存） |

> 配额这个例子要小心：**展示用的配额可以缓存**（用户看的余额允许差几秒），但**扣减时的判断必须以数据库/Redis 原子操作为准**，不能信任本地缓存的旧值——否则就是 [Redis 实战](/back-end/frontend-backend-guide/12-redis-in-practice) 里讲的超卖。这就是"缓存用于读，权威用于写"。

### 多级缓存的一致性代价

加一级缓存就多一层不一致。Redis 删了，但各个 pod 的 Caffeine 还各存着旧值，要等它们各自 30 秒 TTL 过期才会刷新。所以本地缓存的 TTL 一定要短（秒级），且只放"能容忍短暂脏读"的数据。要更强的一致，可以在写操作时发一条 Redis Pub/Sub 或 MQ 广播，通知所有 pod 主动失效本地缓存——但这就更复杂了。一致性取舍的总原则见 [事务与一致性](/back-end/frontend-backend-guide/11-transactions-consistency)：**没有银弹，只有按业务选择"能容忍多脏"。**

---

## 31.2 限流：四种算法对比（重点）

限流是高并发系统的"保险丝"：**宁可拒绝一部分请求，也不能让所有请求一起把系统拖垮**。这里把四种经典算法的图和适用场景讲清楚。

**前端类比**：限流就是服务端版的 throttle/debounce，但它是"强制"的——前端节流只能管住自家页面发的请求，攻击者抓包绕过前端直接打接口时毫无作用；服务端限流是最后一道闸门，对谁都生效。

### 固定窗口计数器

把时间切成固定窗口（比如每秒一个），窗口内计数，超过阈值就拒绝，到下一个窗口清零。

```text
 阈值=10/秒
 ┌────────第0秒────────┐┌────────第1秒────────┐
 │ ▮▮▮▮▮▮▮▮▮▮ 计满拒绝  ││ ▮▮▮▮▮▮▮▮▮▮ 计满拒绝   │
 └────────────────────┘└─────────────────────┘
 边界问题：0.9秒来10个 + 1.1秒来10个 → 0.2秒内放过20个（瞬时2倍）
```

- **优点**：实现最简单（[Redis 实战](/back-end/frontend-backend-guide/12-redis-in-practice) 里的网关限流就是它，`INCR` + `EXPIRE`）。
- **缺点**：窗口边界会有 2 倍突刺。
- **适用**：对精度要求不高的普通接口。

### 滑动窗口

把窗口切成更细的小格子（比如把 1 秒分成 10 个 100ms 的格子），统计"最近 1 秒"内所有格子的总和，窗口随时间平滑滑动。

```text
 滑动窗口 = 最近1秒（10个100ms小格的滑动求和）
   过去 ───────────────────▶ 现在
   [格][格][格][格][格][格][格][格][格][格]
        └──────── 统计这一段总和 ────────┘  随时间整体右移
 → 没有固定窗口的边界突刺，更平滑
```

- **优点**：消除了固定窗口的边界突刺，精度更高。
- **缺点**：要存每个小格/每次请求的记录，内存和计算成本更高。
- **适用**：对限流精度有要求的场景。Redis 可以用 ZSet 实现：score 存请求时间戳，每次请求 `ZADD` 一条、`ZREMRANGEBYSCORE` 删掉 1 秒之前的、再 `ZCARD` 看窗口内有多少条。

### 漏桶（匀速流出）

请求先进一个固定容量的桶，桶以**恒定速率**漏出（被处理），桶满了就拒绝。无论进水多猛，出水永远匀速。

```text
   请求(忽快忽慢) ─▶ ┌─────────┐
                     │  ▮▮▮▮▮  │ 桶（满了溢出=拒绝）
                     │  ▮▮▮▮▮  │
                     └────┬────┘
                          │ 恒定速率漏出（比如 100/秒）
                          ▼ 下游始终匀速收到请求
```

- **优点**：输出绝对平滑，能保护下游（比如下游是个只能扛 100 QPS 的老系统）。
- **缺点**：**不允许突发**——即使桶是空的、下游很闲，也只能匀速放行，浪费瞬时余量。
- **适用**：需要严格保护下游、对外部第三方接口匀速调用。

### 令牌桶（允许突发）

桶里以恒定速率**放入令牌**，每个请求要先拿到一个令牌才能通过，桶满了令牌就不再增加。

```text
   令牌生成(恒定速率 100/秒) ─▶ ┌─────────┐
                                │ ●●●●●●● │ 令牌桶（攒着，最多攒满）
                                └────┬────┘
   请求 ─▶ 拿到令牌? ─是─▶ 通过      │
                └─否─▶ 拒绝/排队     ▼
   关键：桶里平时能攒下令牌，突发来一批请求时可一次性消耗 → 允许短时突发
```

- **优点**：平时攒令牌，**允许短时突发**（积累的令牌可以一次性消耗），又能限制长期平均速率。这最贴近真实流量"平时平稳、偶尔尖峰"的特点。
- **缺点**：实现稍复杂。
- **适用**：最常用的通用限流算法，单机限流首选。

### 一张表选算法

| 算法 | 是否允许突发 | 输出平滑度 | 实现难度 | 典型用途 |
| --- | --- | --- | --- | --- |
| 固定窗口 | 边界处会突刺 | 一般 | 最简单 | 普通接口粗粒度限流 |
| 滑动窗口 | 否（平滑限速） | 高 | 中 | 需要精确限流 |
| 漏桶 | **否**（强制匀速） | 最高 | 中 | 保护脆弱下游、匀速调外部 API |
| 令牌桶 | **是**（攒令牌突发） | 高 | 中 | 通用首选、单机限流 |

### 网关用 Redis 做分布式限流

svc-gateway 是多实例的，限流必须是"全集群共享一个计数"，所以要放在 Redis 而非每个实例本地（呼应 [项目整体架构](/back-end/frontend-backend-guide/02-architecture-overview) 里网关的职责）。Spring Cloud Gateway 内置的 `RequestRateLimiter` 用的就是 Redis + 令牌桶（基于 Redis 官方的 Lua 脚本，保证"取令牌"的原子性）：

```yaml
# svc-gateway application.yml
spring:
  cloud:
    gateway:
      routes:
        - id: svc-ai-route
          uri: lb://svc-ai
          predicates:
            - Path=/api/ai/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 100    # 每秒补充 100 个令牌（长期平均速率）
                redis-rate-limiter.burstCapacity: 200     # 桶容量 200（允许瞬时突发到 200）
                key-resolver: "#{@userKeyResolver}"        # 按用户维度限流
```

```java
// 限流维度：按登录用户 uid 限流（从请求头取，网关已在鉴权后塞进来）
@Bean
public KeyResolver userKeyResolver() {
    return exchange -> Mono.just(
        exchange.getRequest().getHeaders().getFirst("X-User-Id"));
}
```

被限流时网关返回 `429 Too Many Requests`。前端拿到 429 应该提示"操作太频繁，请稍后再试"并退避重试，而不是当成普通错误。

### 单机限流：Guava RateLimiter / Sentinel

如果只想保护**单个进程内**某段昂贵逻辑（比如 svc-ai 调用本地一个吃 CPU 的模型），用进程内限流即可，不必走 Redis：

```java
// Guava 的令牌桶，单机、零依赖外部组件
private final RateLimiter limiter = RateLimiter.create(50.0);  // 每秒 50 个令牌

public RtData<GenResult> generate(GenRequest req) {
    if (!limiter.tryAcquire()) {                                // 拿不到令牌立即返回，不阻塞
        return RtData.fail("当前生图繁忙，请稍后重试");
    }
    return RtData.ok(doGenerate(req));
}
```

更复杂的场景（要可视化配置、按 QPS/线程数/异常比例多维限流、规则动态下发）用 **Sentinel**——它把限流、降级、熔断做成了一套，能在控制台动态改规则不用重启，是国内 Spring Cloud 项目的常见选择。

---

## 31.3 熔断与降级

限流是"我自己快撑不住了，少收点请求"；熔断降级是"**我依赖的下游撑不住了，我别再拖死自己**"。

**前端类比**：你调一个慢接口，会设 `timeout` + `try/catch`，超时或报错就展示兜底 UI（缓存数据、骨架屏、"加载失败请重试"）而不是让整个页面白屏卡死。熔断降级就是后端版的这套兜底，只是它还能"自动统计失败率，连续失败就干脆一段时间内不再调，直接走兜底"。

### 雪崩效应：为什么需要熔断

svc-canvas 调 svc-ai 生图。如果 svc-ai 挂了或变慢（每个请求卡 30 秒），svc-canvas 的每个线程都阻塞在等 svc-ai 上，线程池很快被占满，于是 svc-canvas 也无法响应别的请求……再上游的 svc-gateway 调 svc-canvas 也跟着卡——**一个服务慢拖垮整条调用链**，这叫雪崩效应。熔断就是给这条链路装的"保险丝"。

### 熔断器三态

```text
            连续失败率超阈值
   ┌────────────────────────────────▶┌──────────┐
   │                                  │  OPEN    │ 打开：直接快速失败走兜底，
┌──┴───┐                              │ (打开)   │ 完全不调下游，给它喘息时间
│CLOSED│ 关闭：正常放行，统计成功/失败  └────┬─────┘
│(关闭)│◀─────────────────────┐           │ 冷却时间到（如 10 秒）
└──────┘   半开试探成功         │           ▼
   ▲                          │      ┌──────────┐
   │     半开试探仍失败 ───────┘      │ HALF_OPEN│ 半开：放几个试探请求过去，
   └─────────────────────────────────│ (半开)   │ 成功就恢复 CLOSED，失败就回 OPEN
                                      └──────────┘
```

- **CLOSED（关闭）**：正常状态，请求全放行，同时统计失败率。失败率超阈值（比如最近 50 次里失败 > 50%）→ 跳到 OPEN。
- **OPEN（打开）**：熔断生效。所有请求**不再调下游**，直接快速失败走兜底（fail fast）。持续一段冷却时间（比如 10 秒）后 → 跳到 HALF_OPEN。
- **HALF_OPEN（半开）**：放几个"试探"请求过去探探下游恢复没。试探成功 → 回 CLOSED（恢复正常）；试探还失败 → 回 OPEN（继续熔断）。

### Resilience4j 实现（与 Feign fallback 配合）

[请求的完整链路](/back-end/frontend-backend-guide/03-request-lifecycle) 里讲过 Feign 的 fallback。熔断器和 fallback 是绝配：熔断打开时，直接走 fallback 返回兜底数据。

```java
@Service
public class CanvasAiService {
    @Autowired
    private AiClient aiClient;     // cpt-api 里的 Feign 客户端

    // name 对应配置文件里的熔断规则；fallbackMethod 是兜底方法
    @CircuitBreaker(name = "svc-ai", fallbackMethod = "generateFallback")
    public RtData<GenResult> generate(GenRequest req) {
        return aiClient.generate(req);    // 正常调下游
    }

    // 兜底：熔断打开或调用异常时走这里。签名要和原方法一致，末尾多一个 Throwable
    private RtData<GenResult> generateFallback(GenRequest req, Throwable ex) {
        log.warn("svc-ai 熔断兜底, prompt={}, cause={}", req.getPrompt(), ex.toString());
        // 兜底策略：把任务转入 MQ 异步重试，先给用户一个"排队中"的回执
        mqProducer.send("ai-gen-retry", req);
        return RtData.fail("生图服务繁忙，已为你排队，稍后在任务列表查看结果");
    }
}
```

```yaml
# application.yml
resilience4j:
  circuitbreaker:
    instances:
      svc-ai:
        sliding-window-size: 50              # 统计最近 50 次调用
        failure-rate-threshold: 50           # 失败率 > 50% 就打开熔断
        wait-duration-in-open-state: 10s     # 打开后冷却 10 秒再进入半开
        permitted-number-of-calls-in-half-open-state: 5   # 半开放 5 个试探请求
```

### 限流、降级、熔断三者的区别

这三个词经常被混着说，但它们的触发原因和动作完全不同，必须分清：

| 名词 | 触发原因 | 动作 | 一句话 |
| --- | --- | --- | --- |
| **限流** | 请求量太大（保护自己） | 拒绝超出阈值的请求（返回 429） | "我收不了这么多" |
| **熔断** | 下游故障率高（保护调用链） | 暂时不调下游，自动恢复探测 | "下游坏了，先别调它" |
| **降级** | 系统压力大或下游不可用 | 砍掉非核心功能、返回兜底/旧数据 | "保核心，弃枝叶" |

它们经常一起用：限流挡掉超量请求，熔断隔离故障下游，降级在两种情况下都提供兜底响应。降级的典型例子：大促时把 svc-canvas 的"实时缩略图预览"功能临时关掉（返回默认占位图），把算力让给核心的"提交生图"——这就是"保核心、弃枝叶"。

---

## 31.4 异步化与削峰

同步处理的天花板是"最慢那一步"。生图要 5~30 秒，如果接口同步等结果返回，一个线程就被占住 30 秒，并发能力惨不忍睹。解法是**异步化**。

**前端类比**：你不会让用户点"生成"后页面一直转圈 30 秒，而是立刻返回"任务已提交"，再轮询或用 WebSocket 等结果。后端的异步化是同一个思路——只是把"提交"和"执行"在服务端就拆开了。

### 耗时操作走 MQ 异步（削峰填谷）

```text
  瞬时洪峰 1万 QPS                       MQ 当缓冲区（蓄水池）
  ┌──────────┐  提交即返回   ┌────────┐         ┌──────────┐
  │ svc-canvas│ ──────────▶ │RocketMQ │ ──────▶ │  svc-ai  │
  │ (生产者)  │  写入任务     │ (削峰)  │ 匀速消费 │ (消费者) │
  └──────────┘             └────────┘ 比如500/秒└──────────┘
   用户立刻拿到"已提交"        堆积的任务            按自己能力慢慢处理
```

- **削峰**：洪峰来时请求先进 MQ 排队，下游按自己的能力匀速消费，不会被瞬时洪峰打垮（和漏桶限流异曲同工，只是这里"溢出"变成了"在队列里排队等"）。
- **填谷**：低谷期消费者继续消化积压，把峰值摊平到一段时间里。

提交生图任务的实际流程（呼应 [请求的完整链路](/back-end/frontend-backend-guide/03-request-lifecycle) 的链路图）：

```java
public RtData<String> submitTask(GenRequest req) {
    // 1. 同步部分：校验 + 扣配额（必须立即确认，快）
    if (!quotaService.tryDeduct(req.getUid(), 1)) {
        return RtData.fail("配额不足");
    }
    // 2. 落库一条 PENDING 任务，拿到 taskId
    String taskId = taskRepository.create(req, TaskStatus.PENDING);
    // 3. 把耗时的生图丢给 MQ，立即返回（不等结果）
    mqProducer.send("ai-gen-task", new GenTaskMsg(taskId, req));
    // 4. 立即返回 taskId，前端拿它去轮询任务状态
    return RtData.ok(taskId);
}
```

消费者（svc-ai 侧）慢慢消费、更新任务状态，前端轮询 `GET /api/canvas/task/{taskId}` 拿进度。MQ 的可靠投递、重复消费、消息堆积怎么处理，是 [消息队列可靠性](/back-end/frontend-backend-guide/33-mq-reliability) 的内容。

### 批处理合并请求

频繁的小操作合并成一次大操作，能大幅降低开销。比如统计每个 prompt 被使用的次数，不必每次生图都 `UPDATE` 一次 MySQL，而是先在内存/Redis 累加，每隔几秒批量刷一次库：

```java
// 内存累加，定时批量落库（攒一批，一次写）
private final Map<String, LongAdder> buffer = new ConcurrentHashMap<>();

public void recordUse(String promptId) {
    buffer.computeIfAbsent(promptId, k -> new LongAdder()).increment();
}

@Scheduled(fixedRate = 5000)   // 每 5 秒刷一次
public void flush() {
    if (buffer.isEmpty()) return;
    Map<String, Long> snapshot = drainBuffer();      // 取出并清空
    statRepository.batchIncrement(snapshot);          // 一条 SQL 批量更新
}
```

**前端类比**：这就是把多个 setState 合并成一次重渲染、或把多个请求用 DataLoader 攒成一个批量查询——核心都是"减少往返次数"。

### 池化：复用昂贵资源

线程、数据库连接都是创建昂贵、应该复用的资源。高并发下**绝不能**每来一个请求就 `new Thread()` 或新建一个数据库连接。这部分前面已经讲透：线程池见 [线程池与 Executor](/back-end/frontend-backend-guide/15-thread-pools-executor)，连接池见 [连接池](/back-end/frontend-backend-guide/13-connection-pools)。这里只强调一句：**高并发系统里几乎所有昂贵资源都该池化**，池子大小是关键调参，太小排队、太大耗尽资源。

---

## 31.5 压测实操：找出真正的瓶颈

> 优化前必须先压测。**没有数据就优化，等于闭眼开车。**

### 目标

压测 svc-user 的"查配额"接口 `GET /api/user/quota`，搞清楚它的极限 QPS、延迟分布和瓶颈在哪。

### 命令：用 wrk 压测

`wrk` 是个轻量高性能的 HTTP 压测工具，一条命令就能跑。

```bash
# -t12  开 12 个线程
# -c400 保持 400 个并发连接
# -d30s 持续压 30 秒
# --latency 输出延迟分布（P50/P99 等）
wrk -t12 -c400 -d30s --latency http://10.0.0.5:8081/api/user/quota
```

### 预期输出样例

```text
Running 30s test @ http://10.0.0.5:8081/api/user/quota
  12 threads and 400 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    18.42ms   31.07ms   1.02s    95.18%
    Req/Sec     1.85k     0.31k    3.20k    71.25%
  Latency Distribution
     50%    9.21ms
     75%   15.63ms
     90%   28.74ms
     99%  210.55ms
  662431 requests in 30.02s, 121.43MB read
  Socket errors: connect 0, read 0, write 0, timeout 38
  Requests/sec:  22066.31
  Non-2xx or 3xx responses: 0
```

### 怎么读这段输出

- **Requests/sec: 22066** —— 吞吐量 QPS，这个接口大约扛 2.2 万 QPS。这是最重要的容量数字。
- **Latency Avg 18.42ms** —— 平均延迟。但平均值会被掩盖，**别只看平均**。
- **Latency Distribution 99% 210.55ms** —— P99 延迟，即 99% 的请求在 210ms 内完成，剩下 1% 更慢。线上 SLA 通常盯 P99/P999，因为"平均 18ms 但 P99 有 210ms"意味着有一批用户体验很差（可能是 GC 停顿、慢查询、连接池排队）。**P99 和平均值差距越大，说明系统抖动越严重。**
- **timeout 38** —— 38 个请求超时了。有错误数说明已经压到接近极限或某处偶发卡顿，要查日志定位。
- **Non-2xx or 3xx responses: 0** —— 没有业务报错（如果这里有大量数字，可能是被限流返回 429 或后端 500 了，QPS 数字就是"假高"）。

### 通过压测找瓶颈：到底卡在哪

压测压到 QPS 上不去、延迟飙升时，瓶颈无非这几处，用 [排查工具箱](/back-end/frontend-backend-guide/29-diagnostic-toolbox) 里的工具逐个排查：

```bash
# 1) CPU 打满了吗？（top 看进程 CPU%）—— 计算密集 / 序列化 / 加密导致
top -Hp <pid>        # 看是哪个线程吃 CPU，再用 jstack 看它在干嘛

# 2) 是 DB 慢吗？开慢查询日志，或看连接池监控
#    HikariCP 日志里 "Connection is not available, request timed out" = 连接池被打满

# 3) 连接池 / 线程池排队了吗？
#    actuator 的 hikaricp.connections.pending 指标 > 0 = 有请求在等连接

# 4) 是 GC 停顿吗？（频繁 Full GC 会让 P99 飙高，呼应 GC 那章）
jstat -gcutil <pid> 1000     # 每秒打一行，看 FGC 次数和 GC 耗时
```

各瓶颈的典型信号与对策：

| 瓶颈 | 信号 | 对策 |
| --- | --- | --- |
| CPU | `top` 里 CPU 接近 100%，QPS 上不去 | 优化算法/序列化、加机器水平扩展 |
| 数据库 | DB 的 CPU/IO 高，慢查询日志多 | 加索引（[SQL 与索引](/back-end/frontend-backend-guide/10-sql-and-indexes)）、加缓存、读写分离 |
| 连接池 | 应用 CPU 不高但延迟高、有等待连接的指标 | 调大连接池、查是否连接泄漏（[连接池](/back-end/frontend-backend-guide/13-connection-pools)） |
| 线程池 | 任务在队列堆积、被拒绝 | 调线程池参数、把慢操作异步化（[线程池与 Executor](/back-end/frontend-backend-guide/15-thread-pools-executor)） |
| GC | P99 周期性飙高，`jstat` 见频繁 FGC | 调堆/GC 参数、查内存泄漏（[垃圾回收 GC](/back-end/frontend-backend-guide/19-garbage-collection)） |

> 关于工具选择：`wrk` 适合命令行快速压单接口；要压复杂业务流程（登录 → 提交任务 → 轮询）、需要图形报告和断言，用 **JMeter**（图形化、能录制脚本、出 HTML 报告）。两者目的一样，按场景选。

### 容量规划一句话

知道单实例极限 QPS 后，容量规划就是道除法：**所需实例数 ≈ 预估峰值 QPS ÷ 单实例安全 QPS（一般取压测极限的 60~70%，留余量给 GC 抖动和突发）**。比如峰值预估 6 万 QPS、单实例安全值取 1.3 万，那大约需要 5 个 pod 起步，再按监控（[可观测三件套](/back-end/frontend-backend-guide/30-observability)）动态扩缩容。

---

## 31.6 性能优化心法

技术手段之外，几条思维原则比具体工具更重要：

1. **先测量，再优化**。没有 profiling 数据就动手改，大概率改错地方还引入 bug。永远先用压测和监控定位，再优化。
2. **找瓶颈，别凭感觉**。瓶颈往往在你想不到的地方——你以为是算法慢，结果是连接池配小了；你以为要加缓存，结果是少了个索引。用数据说话。
3. **二八定律（80/20）**。80% 的耗时通常集中在 20% 的代码上。找到那 20% 重点优化，比把全部代码都"顺手优化一遍"高效得多。一个慢 SQL 的收益，往往胜过你手工抠十处微优化。
4. **优化是有代价的**。多级缓存换来一致性复杂度，异步化换来调试和顺序保证的难度，连接池调大换来内存占用。优化不是越多越好，是"在能接受的复杂度内换到够用的性能"。
5. **能水平扩展就别死磕单机**。很多时候多加两台机器（[Kubernetes 实战](/back-end/frontend-backend-guide/24-kubernetes-in-practice) 的水平扩缩容）比熬夜抠单机性能更划算——前提是你的服务是无状态的、能被加机器解决。

**前端类比**：这套心法和前端性能优化完全一致——先用 Performance / Lighthouse 面板找到真正的长任务和大资源（测量），再针对性优化那几个关键瓶颈（二八），而不是一上来就把所有组件都包一层 `memo`（凭感觉、徒增复杂度）。

---

## 小结

- **缓存**分层：本地 Caffeine（快、易脏、TTL 要短）+ 分布式 Redis（共享）+ 数据库回源；只缓存"读多写少、能容忍短暂不一致、查询昂贵的热点数据"，扣减等强一致操作以数据库为准。
- **限流**四算法各有适用：固定窗口（简单有突刺）、滑动窗口（精确）、漏桶（强制匀速、护下游）、令牌桶（允许突发、通用首选）；网关用 Redis 做分布式限流，单机用 Guava/Sentinel。
- **熔断降级**保护调用链：熔断器三态（CLOSED→OPEN→HALF_OPEN）自动隔离故障下游并配 fallback 兜底；限流（收不了这么多）、熔断（下游坏了别调）、降级（保核心弃枝叶）三者动作不同、常一起用。
- **异步削峰**提升吞吐：耗时操作走 MQ 削峰填谷、批处理合并请求减少往返、昂贵资源一律池化。
- **压测**是优化前提：用 wrk/JMeter 测 QPS 和 P99，盯 P99 而非平均；瓶颈按 CPU/DB/连接池/线程池/GC 逐个排查；容量规划 = 峰值 QPS ÷ 单实例安全值。
- **心法**：先测量再优化、找瓶颈别凭感觉、抓二八、认清优化的代价、能水平扩展就别死磕单机。

### 自测

1. 漏桶和令牌桶都能限流，最关键的区别是什么？svc-ai 调用一个"只能扛 100 QPS 的外部模型 API"时，该选哪个？为什么？
2. 熔断器的三个状态分别是什么？描述一次"下游故障 → 熔断打开 → 自动恢复"的完整状态流转。再用一句话区分限流、熔断、降级三者。
3. 一次 wrk 压测结果是"平均延迟 18ms，但 P99 是 210ms"，这说明系统可能存在什么问题？你会用哪些命令去定位是 CPU、DB、连接池还是 GC 的瓶颈？

### 下一章

性能扛住了，还得守得住——下一章 [安全](/back-end/frontend-backend-guide/32-security) 讲后端必须面对的认证授权、越权、注入、敏感数据保护等安全问题。

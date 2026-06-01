# Redis 实战

> 上一章我们聊了事务和一致性，知道了数据库为了保证正确性付出了多少代价。这一章换个角色：当数据库扛不住高并发读、或者你需要一个跨进程共享的"全局变量"时，Redis 就该上场了。这是后端工程师最常用、面试最爱问、线上事故最高发的一个组件——本章按"是什么 → 数据结构 → 缓存模式 → 三大问题 → 分布式锁 → 限流"的顺序，把实战要点讲透。

延伸阅读：本课程聚焦"怎么用、怎么排查"，如果你想补充 Redis 的基础概念和安装，可看 [Redis 基础与应用](/back-end/database/redis/intro)；想看 Spring Boot 里 RedisTemplate 的完整 API，可看 [Java 操作 Redis](/back-end/database/redis/java-redis)。

---

## 12.1 Redis 是什么

**一句话**：Redis 是一个把数据存在内存里的、单线程处理命令的、超快的键值数据库，读写速度是 MySQL 的 100 倍以上（单机轻松 10 万 QPS）。

为什么这么快？三个原因：

1. **数据在内存**——MySQL 默认要落盘，机械/SSD 的随机 IO 是微秒到毫秒级；内存访问是纳秒级，差好几个数量级。
2. **命令处理单线程**——没有锁竞争、没有线程切换开销。一条命令执行完才执行下一条，所以单条命令天然原子（这个特性后面做计数器、分布式锁时非常关键）。
3. **数据结构是为操作优化的**——它不是简单的 `Map<String, String>`，而是内置了 Hash、跳表、压缩列表等结构，很多操作是 O(1) 或 O(log n)。

**前端类比**：你可以把 Redis 想成"一个所有后端实例共享的、超大号的、带过期时间的 `Map`"。前端的 `localStorage` 是浏览器单机本地的，Redis 则是部署在服务器上、所有 `svc-*` 服务都能连过去读写的同一份内存。

| Redis 用途 | 前端类比 | 本项目场景 |
| --- | --- | --- |
| 缓存热点数据 | `localStorage.getItem('user')` | 缓存用户配额、缓存生图任务状态 |
| 限流计数 | 局部变量记录点击次数 | svc-gateway 对单用户每秒请求数限流 |
| 分布式锁 | 不让两个标签页同时提交（但跨机器） | svc-user 防并发扣配额超卖 |
| Session/Token | `sessionStorage` | svc-auth 存登录 token 与过期 |
| 排行榜/延时队列 | 前端排序数组 | 热门 prompt 榜、延时关闭超时任务 |

> ⚠️ 内存是有限且昂贵的资源。Redis 不是"另一个数据库"，而是"放得下、丢得起、读得勤"的那部分数据的加速层。真正的数据归宿仍然是 MongoDB / MySQL。

---

## 12.2 五种核心数据结构与用途

Redis 的精髓是数据结构。前端用 `axios` 时你不会在乎传输层细节，但用 Redis 时选错数据结构会直接决定性能和能不能做某件事。下面五种是必须烂熟于心的。

下面所有命令都可以在 `redis-cli` 里直接敲。先连进去：

```bash
# 进入项目的 cpt-redis 实例（本地或容器内）
redis-cli -h 127.0.0.1 -p 6379
# 如果有密码：redis-cli -h 127.0.0.1 -p 6379 -a yourpassword
```

### String —— 缓存 / 计数 / 分布式锁

最基础的类型，value 可以是字符串、数字、甚至序列化后的 JSON。

```bash
# 缓存一个 JSON 字符串，60 秒后过期
127.0.0.1:6379> SET user:10001 '{"uid":10001,"quota":50}' EX 60
OK
127.0.0.1:6379> GET user:10001
"{\"uid\":10001,\"quota\":50}"
127.0.0.1:6379> TTL user:10001          # 还剩多少秒过期
(integer) 57

# 当作计数器（原子自增，单线程保证不会丢）
127.0.0.1:6379> INCR ai:gen:count:10001
(integer) 1
127.0.0.1:6379> INCRBY ai:gen:count:10001 5
(integer) 6
```

**前端类比**：`SET key value` 就是 `localStorage.setItem`，`EX 60` 是它没有的"自动过期"能力。`INCR` 相当于 `count++`，但它是跨所有服务实例原子的。

### Hash —— 存对象

一个 key 下面有多个 field-value，适合存对象，可以只更新某个字段而不用读出整个对象再写回。

```bash
127.0.0.1:6379> HSET user:profile:10001 nickname "小高" quota 50 vip 1
(integer) 3
127.0.0.1:6379> HGET user:profile:10001 quota
"50"
127.0.0.1:6379> HINCRBY user:profile:10001 quota -1   # 只扣配额这一个字段
(integer) 49
127.0.0.1:6379> HGETALL user:profile:10001
1) "nickname"
2) "小高"
3) "quota"
4) "49"
5) "vip"
6) "1"
```

**前端类比**：Hash 就是一个对象 `{ nickname, quota, vip }`。用 String 存 JSON 时改一个字段要"读 → 反序列化 → 改 → 序列化 → 写"，用 Hash 直接 `HINCRBY` 改一个 field，省事又原子。

### List —— 队列 / 栈

有序、可重复，从两端进出，常用作简单消息队列。

```bash
127.0.0.1:6379> LPUSH task:queue "task-1001" "task-1002"   # 左侧入队
(integer) 2
127.0.0.1:6379> RPOP task:queue                            # 右侧出队（先进先出）
"task-1001"
127.0.0.1:6379> LLEN task:queue
(integer) 1
```

**前端类比**：`LPUSH` + `RPOP` 就是 `arr.unshift()` + `arr.pop()` 组成的队列。本项目正经的异步任务走 RocketMQ（见 [消息队列可靠性](/back-end/frontend-backend-guide/33-mq-reliability)），List 只适合做轻量、可丢失的临时队列。

### Set —— 去重 / 标签

无序、不重复，支持交并差集运算。

```bash
127.0.0.1:6379> SADD post:123:likes 10001 10002 10003   # 谁点了赞
(integer) 3
127.0.0.1:6379> SADD post:123:likes 10001               # 重复加无效
(integer) 0
127.0.0.1:6379> SISMEMBER post:123:likes 10002          # 用户 10002 点过赞吗
(integer) 1
127.0.0.1:6379> SCARD post:123:likes                    # 点赞总数
(integer) 3
```

**前端类比**：就是 JS 的 `Set`，`SADD` 即 `set.add()`，天然去重。后端常用它做"用户是否已点赞""今日活跃用户 UV 去重"。

### ZSet —— 排行榜 / 延时队列

每个成员带一个 score，按 score 自动排序。这是 Redis 最强大的结构。

```bash
# 热门 prompt 榜：score 是被使用次数
127.0.0.1:6379> ZADD hot:prompt 120 "赛博朋克猫" 88 "水墨山水" 200 "宇航员"
(integer) 3
127.0.0.1:6379> ZREVRANGE hot:prompt 0 2 WITHSCORES     # Top3，从高到低
1) "宇航员"
2) "200"
3) "赛博朋克猫"
4) "120"
5) "水墨山水"
6) "88"

# 延时队列：score 用"到期时间戳"，到点的任务先取出来
127.0.0.1:6379> ZADD delay:close:task 1735689600 "task-1001"
(integer) 1
127.0.0.1:6379> ZRANGEBYSCORE delay:close:task 0 1735689600   # 取出所有已到期任务
1) "task-1001"
```

**前端类比**：ZSet 像一个永远保持有序的数组，每个元素附带排序权重。本项目 svc-canvas 用 ZSet 实现"提交后 30 分钟没完成就标记超时"的延时关闭——score 存到期时间戳，后台定时 `ZRANGEBYSCORE` 捞已到期的任务来处理。

| 结构 | 典型操作复杂度 | 项目用途 |
| --- | --- | --- |
| String | `GET/SET/INCR` O(1) | 缓存 JSON、计数、限流、分布式锁 |
| Hash | `HGET/HSET/HINCRBY` O(1) | 存用户配额对象、只改单字段 |
| List | `LPUSH/RPOP` O(1) | 轻量队列 |
| Set | `SADD/SISMEMBER` O(1) | 点赞去重、标签、UV 统计 |
| ZSet | `ZADD` O(log n)、`ZREVRANGE` | 排行榜、延时队列 |

---

## 12.3 缓存模式：Cache-Aside（旁路缓存）

这是 90% 场景用的缓存模式，也是面试必考。核心思想：**应用代码自己负责维护缓存，缓存只是"旁路"，不挡在数据库前面**。

### 读流程：先查缓存，miss 再查库，然后回写

```text
        ┌─────────┐   1.查缓存    ┌─────────┐
请求 ──▶│  应用    │─────────────▶│  Redis  │
        │ svc-user │◀────────────│         │
        └────┬────┘  命中?返回    └─────────┘
             │ miss(没命中)
             │ 2.查数据库
             ▼
        ┌─────────┐
        │ MongoDB │
        └────┬────┘
             │ 3.回写缓存(带过期时间)
             ▼
        ┌─────────┐
        │  Redis  │
        └─────────┘
```

伪代码（以 svc-user 查配额为例）：

```java
public UserQuota getQuota(Long uid) {
    String key = "user:quota:" + uid;
    // 1. 先查缓存
    String cached = redisTemplate.opsForValue().get(key);
    if (cached != null) {
        return JSON.parseObject(cached, UserQuota.class);   // 命中，直接返回
    }
    // 2. 缓存 miss，查数据库（慢）
    UserQuota quota = quotaRepository.findByUid(uid);
    // 3. 回写缓存，设置过期时间（关键：一定要带 TTL）
    redisTemplate.opsForValue().set(key, JSON.toJSONString(quota), 5, TimeUnit.MINUTES);
    return quota;
}
```

**前端类比**：这就是 React Query / SWR 的心智模型——先看 cache，没有或过期了才发请求，请求回来再写进 cache。区别是 Redis 这份 cache 是所有后端实例共享的，不是某个浏览器独有的。

### 写流程：更新数据库，然后删缓存

```java
public void deductQuota(Long uid, int n) {
    quotaRepository.deduct(uid, n);                 // 1. 改数据库
    redisTemplate.delete("user:quota:" + uid);      // 2. 删缓存（不是更新！）
}
```

### 为什么是"删缓存"而不是"更新缓存"？

这是新手最容易写错的地方。三条理由：

1. **避免并发写覆盖出脏数据**。如果用"更新缓存"，两个请求 A、B 几乎同时改库，A 先改库后慢半拍才更缓存，B 后改库却先更了缓存，最终缓存里留的是 A 的旧值，库里是 B 的新值——缓存和库永久不一致。删缓存则不存在"谁后写"的问题，下次读自然回源到最新库数据。
2. **省计算**。更新缓存往往要重新拼一份完整对象（可能还要 JOIN 多张表），但这份数据不一定马上有人读。删掉，等真有人读时再算（lazy），更划算。
3. **简单**。删除是幂等的，删两次也没事；更新缓存的逻辑要和读逻辑保持一致，容易写歪。

> 进阶：即使"先更库再删缓存"，在极端时序下仍可能短暂不一致（读请求在删之前命中了旧缓存）。生产上常用"延迟双删"或订阅数据库 binlog 异步删缓存兜底。对本项目配额这类场景，给缓存设较短 TTL（如 5 分钟）+ 删缓存，已经足够。一致性话题详见 [事务与一致性](/back-end/frontend-backend-guide/11-transactions-consistency)。

---

## 12.4 缓存三大问题（重点）

这三个词几乎是后端面试 Redis 部分的必考题，线上事故也多由它们引起。记住它们的区别：**穿透**是查根本不存在的数据，**击穿**是单个热点 key 过期瞬间，**雪崩**是大批 key 同时过期。

### 缓存穿透：查一个不存在的 key

**现象**：恶意请求一直查 `uid=-1` 这种数据库里也没有的数据。缓存永远 miss（因为库里也没有，回写不了），每次请求都打到 MongoDB，缓存形同虚设，数据库被打垮。

```text
请求(uid=-1) ─▶ Redis(miss) ─▶ MongoDB(也没有) ─▶ 返回 null，缓存里没东西
   ↑                                                          │
   └──────────── 下次还是 miss，继续打库 ◀────────────────────┘
```

**对策一：缓存空值**。查不到也往缓存写一个空标记，给个短 TTL。

```java
UserQuota quota = quotaRepository.findByUid(uid);
if (quota == null) {
    // 缓存一个空标记，2 分钟，防止反复打库
    redisTemplate.opsForValue().set(key, "NULL", 2, TimeUnit.MINUTES);
    return null;
}
```

**对策二：布隆过滤器（Bloom Filter）**。在 Redis 前放一个布隆过滤器，把"所有存在的 uid"预先放进去。请求先问布隆过滤器："这个 uid 可能存在吗？"——它说不存在就一定不存在，直接拦掉。它有极小概率误判"存在"（实际不存在），但绝不会漏掉真存在的，所以拦截是安全的。

**前端类比**：布隆过滤器就像表单提交前的 `zod` 前置校验——在请求真正打到后端之前先用一个轻量规则筛掉一批明显非法的输入，省得每个都走完整流程。

### 缓存击穿：单个热点 key 过期的瞬间

**现象**：某个超热的 key（比如首页热门榜）TTL 到期的那一刹那，成千上万个请求同时 miss，全部涌向数据库去重建这一个缓存，瞬间把库压垮。注意它和穿透不同——数据是存在的，只是过期的瞬间撞上了高并发。

**对策一：互斥锁重建**。只让一个请求去查库重建缓存，其他请求短暂等待或返回旧值。

```java
public HotRank getHotRank() {
    String cached = redisTemplate.opsForValue().get("hot:rank");
    if (cached != null) return parse(cached);

    String lockKey = "lock:hot:rank";
    // 只有抢到锁的那个线程去重建
    Boolean locked = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
    if (Boolean.TRUE.equals(locked)) {
        try {
            HotRank rank = rankRepository.compute();  // 重建（慢）
            redisTemplate.opsForValue().set("hot:rank", toJson(rank), 5, TimeUnit.MINUTES);
            return rank;
        } finally {
            redisTemplate.delete(lockKey);
        }
    } else {
        Thread.sleep(50);          // 没抢到锁，稍等后重试读缓存
        return getHotRank();
    }
}
```

**对策二：逻辑过期**。缓存永不真正过期，但在 value 里塞一个"逻辑过期时间"字段；读到发现逻辑过期了，就开一个后台线程异步重建，当前请求先返回旧值。用户永远不会等，代价是短时间内可能读到旧数据。

### 缓存雪崩：大量 key 在同一时刻集体过期

**现象**：系统启动时一次性把大量缓存都设成了同样的 TTL（比如都设 30 分钟），结果 30 分钟后它们同时过期，海量请求同一秒全部回源，数据库瞬间被打挂。和击穿的区别是：击穿是"一个"热点 key，雪崩是"一大批" key 同时失效。

**对策：过期时间加随机抖动**，把集中失效打散。

```java
// 基础 TTL 30 分钟，再加 0~300 秒随机，避免同时过期
int baseTtl = 1800;
int jitter = ThreadLocalRandom.current().nextInt(300);
redisTemplate.opsForValue().set(key, value, baseTtl + jitter, TimeUnit.SECONDS);
```

其他兜底手段：Redis 高可用部署（主从 + 哨兵 / 集群，防止 Redis 整个宕机引发的"硬雪崩"）、对数据库做限流降级、热点数据永不过期由后台主动刷新。

| 问题 | 触发点 | 一句话对策 |
| --- | --- | --- |
| 穿透 | 查不存在的数据 | 缓存空值 + 布隆过滤器 |
| 击穿 | 单个热点 key 过期瞬间 | 互斥锁重建 / 逻辑过期 |
| 雪崩 | 大批 key 同时过期 | TTL 加随机 + Redis 高可用 |

---

## 12.5 分布式锁：防止并发扣配额超卖

### 为什么需要分布式锁

本项目 svc-user 是多实例部署的（Docker/K8s 跑了好几个 pod）。当同一个用户的两次"提交生图"请求被负载均衡分到了不同实例上，两个实例同时执行"检查配额 → 扣减配额"，就可能都读到 `quota=1`，都判断"够用"，都扣减，最终扣成了 `-1`——这就是**超卖**。

**前端类比**：你给按钮加 `disabled` 防重复点击、用防抖节流——那只能管住单个浏览器单个标签页。分布式锁要管的是"跨机器、跨进程的互斥"，相当于给整个集群一个全局唯一的 `lock` 标志。JVM 内置的 `synchronized`（见 [线程安全与锁](/back-end/frontend-backend-guide/16-thread-safety-locks)）只能锁住单个进程内的线程，跨不了机器，所以才需要 Redis 这种公共存储来当锁。

### 原理：SETNX + 过期时间

最朴素的实现：用 `SET key value NX EX seconds`。`NX` 表示"key 不存在时才设置成功"，谁设置成功谁就拿到锁；`EX` 给锁一个过期时间，防止持锁的实例崩了之后锁永远释放不了（死锁）。

```bash
127.0.0.1:6379> SET lock:quota:10001 "uuid-abc" NX EX 10
OK                                  # 抢到锁
127.0.0.1:6379> SET lock:quota:10001 "uuid-xyz" NX EX 10
(nil)                               # 另一个请求没抢到（已存在）
```

### 两个经典的坑

**坑 1：锁误删**。线程 A 拿到锁但执行太久，锁到 10 秒自动过期了；这时线程 B 抢到了新锁；A 终于执行完，调 `DEL` 把锁删了——结果删的是 B 的锁！解决：value 存一个唯一标识（如 UUID），释放时先校验"这把锁是不是我的"再删。而"校验 + 删除"两步必须原子，否则校验完锁又过期被别人抢了，所以要用 Lua 脚本一次执行：

```text
-- 释放锁的 Lua 脚本：先比对 value，相等才删
if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('del', KEYS[1])
else
    return 0
end
```

**坑 2：过期续期**。锁设了 10 秒，但业务（比如调用 AI 接口）跑了 15 秒还没完，锁提前过期，别人就能进来并发执行了。解决：开一个后台"看门狗"线程，在持锁期间定时给锁续命（延长 TTL），直到业务执行完才停。手写这套逻辑非常容易出 bug。

### 生产推荐：Redisson

不要自己手撸上面这些细节。生产环境直接用 **Redisson**——它把"唯一 value、Lua 原子释放、看门狗自动续期、可重入"全帮你封装好了。

```java
@Service
public class QuotaService {
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private QuotaRepository quotaRepository;
    @Autowired
    private StringRedisTemplate redisTemplate;

    public RtData<Void> deductQuota(Long uid, int n) {
        RLock lock = redissonClient.getLock("lock:quota:" + uid);
        try {
            // 最多等 3 秒拿锁；拿到后默认看门狗自动续期（不传 leaseTime）
            if (!lock.tryLock(3, TimeUnit.SECONDS)) {
                return RtData.fail("系统繁忙，请稍后重试");
            }
            // —— 临界区：同一 uid 同一时刻只有一个线程在这里 ——
            UserQuota quota = quotaRepository.findByUid(uid);
            if (quota.getRemaining() < n) {
                return RtData.fail("配额不足");
            }
            quotaRepository.deduct(uid, n);
            redisTemplate.delete("user:quota:" + uid);   // 删缓存
            return RtData.ok(null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return RtData.fail("获取锁被中断");
        } finally {
            // 只有当前线程持有锁时才解锁，避免误删
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

**前端类比**：`lock.tryLock(3, ...)` 像是带 timeout 的 `await acquire()`——拿不到就放弃，避免请求无限挂起，这和你写接口请求加超时是一个思路。

> ⚠️ 锁的范围要尽量小：临界区只放"检查 + 扣减"，**不要**把调用 AI 这种几秒的慢操作放进锁里，否则锁占用时间太长，并发直接退化成串行。正确做法是先扣配额（短锁），再异步提交 AI 任务。

---

## 12.6 限流计数器：网关限流

svc-gateway 要防止某个用户/IP 短时间内疯狂刷接口。最简单实用的方案是**固定窗口计数器**：用 `INCR` + `EXPIRE`，每个时间窗口一个计数 key。

### 目标与实现

目标：限制单个用户每秒最多 10 次请求，超了返回 429。

```java
public boolean allow(Long uid) {
    // key 带上当前秒，天然形成每秒一个窗口
    long second = System.currentTimeMillis() / 1000;
    String key = "rate:" + uid + ":" + second;

    Long count = redisTemplate.opsForValue().increment(key);   // INCR，第一次返回 1
    if (count != null && count == 1L) {
        // 这个窗口的第一个请求，给 key 设 2 秒过期（窗口过了自动清理）
        redisTemplate.expire(key, 2, TimeUnit.SECONDS);
    }
    return count != null && count <= 10;   // 超过 10 就拒绝
}
```

对应的 redis-cli 视角：

```bash
127.0.0.1:6379> INCR rate:10001:1735689600
(integer) 1                       # 第一次，接着 EXPIRE
127.0.0.1:6379> INCR rate:10001:1735689600
(integer) 2
# ... 直到第 11 次返回 11 > 10，网关返回 429 Too Many Requests
```

**怎么读这个结果**：返回值就是该用户在当前这一秒内的累计请求数。只要 `<= 10` 就放行，`> 10` 就拦截。EXPIRE 保证这些计数 key 不会永远堆在内存里。

**前端类比**：这就是把前端的 debounce/throttle 搬到了服务端、并且按用户维度全局生效——前端节流只能"劝阻"自己页面的请求，服务端限流是"强制"的最后一道闸门，对抓包绕过前端的攻击也有效（接口安全相关可参考 [Web 安全](/front-end/the-basics/network-basics/webSafety)）。

> 固定窗口有个边界问题：第 0.9 秒来 10 个、第 1.1 秒又来 10 个，跨窗口看 0.2 秒内放过了 20 个。要更平滑可用"滑动窗口"（ZSet 存每次请求时间戳）或"令牌桶"。网关层一般用现成组件（Sentinel、Spring Cloud Gateway 的 RequestRateLimiter）。本项目对一般接口用固定窗口足够，对支付等敏感接口才上更严的方案。完整的限流降级讨论见 [性能与并发](/back-end/frontend-backend-guide/31-performance-concurrency)。

---

## 12.7 几个实操排查技巧

**目标：看某个 key 到底存了什么、还剩多久过期**

```bash
127.0.0.1:6379> TYPE user:quota:10001     # 先看类型，决定用哪个命令读
string
127.0.0.1:6379> GET user:quota:10001
"{\"uid\":10001,\"remaining\":49}"
127.0.0.1:6379> TTL user:quota:10001
(integer) 287                              # 还剩 287 秒；返回 -1 表示永不过期，-2 表示 key 不存在
```

**目标：定位线上是不是某条命令慢/某个大 key 拖垮了 Redis**

```bash
# 抓最近的慢命令（执行超过阈值的）
127.0.0.1:6379> SLOWLOG GET 5
1) 1) (integer) 14                # 日志 id
   2) (integer) 1735689600       # 发生时间戳
   3) (integer) 12000            # 耗时 12000 微秒 = 12ms（对 Redis 算很慢了）
   4) 1) "KEYS"                  # 罪魁祸首：KEYS 命令
      2) "user:*"
```

**怎么读这段输出**：第 4 项是被记录的命令——这里是 `KEYS user:*`。`KEYS` 会全量扫描所有 key、O(n) 且阻塞单线程，在生产上是禁用级别的命令；线上要遍历 key 必须用 `SCAN`（游标分批，不阻塞）。

**结论**：看到 SLOWLOG 里出现 `KEYS`、超大 value 的读写（大 key）、或 O(n) 的集合操作，就是优化方向。Redis 单线程，一条慢命令会拖累后面所有请求——这点和 Node 单线程事件循环里"一个同步重计算卡死整个进程"是一模一样的直觉。读日志和系统化排查的完整方法，见 [看懂日志](/back-end/frontend-backend-guide/26-reading-logs) 和 [排查方法论](/back-end/frontend-backend-guide/27-troubleshooting-methodology)。

---

## 小结

- Redis 是内存级 KV 数据库，快在"内存 + 单线程 + 专用数据结构"；它是数据库的加速层和分布式协调工具，不是数据的最终归宿。
- 五种核心结构各有所长：String（缓存/计数/锁）、Hash（对象）、List（队列）、Set（去重）、ZSet（排行榜/延时）；选对结构是用好 Redis 的前提。
- Cache-Aside 是主流缓存模式：读时回源回写，写时更新库后**删缓存**而非更新缓存，避免并发脏数据。
- 三大问题要分清：穿透（查不存在→缓存空值/布隆）、击穿（热点 key 过期→互斥锁/逻辑过期）、雪崩（大批 key 同时过期→TTL 加随机 + 高可用）。
- 分布式锁手写坑多（误删、续期），生产用 Redisson；限流用 `INCR + EXPIRE` 的计数器，是服务端强制版的 throttle。

### 自测

1. 缓存更新为什么推荐"删缓存"而不是"更新缓存"？请说出至少两条理由，并描述一个用"更新缓存"会产生脏数据的并发时序。
2. 缓存穿透、击穿、雪崩三者的触发条件分别是什么？它们各自最典型的对策是什么？
3. 用 `SET key value NX EX 10` 实现的分布式锁有哪两个经典坑？Redisson 分别是怎么解决的？

### 下一章

数据库连接不是用完就扔的——下一章 [连接池](/back-end/frontend-backend-guide/13-connection-pools) 讲清楚为什么连接要"池化"，以及连接池配错了会怎样拖垮整个服务。

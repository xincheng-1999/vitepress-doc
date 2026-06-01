# 后端思维：从一个浏览器到上万并发请求

> 学后端，最难的不是 Java 语法，也不是 Spring 注解——这些查文档、抄示例都能搞定。
> 真正难的是**换一套世界观**：你以前写的代码，运行在「一个用户、一个浏览器、一个 Tab、刷新即重来」的世界里；
> 现在你写的代码，要运行在「一个进程、同时服务上万人、连续跑几个月不重启、出错了没有 console 可看」的世界里。

这一章不教任何具体技术，它是全课程的**认知总纲**。我们先把世界观切换过来，后面 30 多章的细节才有地方安放。如果你只读一章，就读这章。

---

## 1.1 先承认：你已经会的，比你以为的多

作为有 1 年以上经验的前端，你其实早就接触过「后端式」的问题，只是没意识到：

- 你用过 `Promise.all` 同时发好几个请求——这就是**并发**。
- 你把登录态放进 `localStorage` 而不是组件 state——这就是**状态外置**。
- 你给 `axios` 配过 `timeout`，给请求加过失败重试——这就是**超时与重试**。
- 你用过 `zod` 校验接口返回的数据——后端在入口处校验请求参数，思路一模一样。
- 你调过线上 bug，发现只能靠 `console.log` 和 Sentry 上报，而不是断点——这就是**看不见的现场**。

所以后端思维不是凭空长出来的新器官，而是把你已有的这些零散经验，**放大、常态化、并发化**。下面五个核心转变，每一个都从「你现在怎么做」讲起。

---

## 1.2 转变一：并发——同一段代码，被上万请求同时执行

**前端现状**：一个用户配一个浏览器，一个浏览器（主线程）一次只跑一段 JS。你写 `let count = 0; count++` 永远不会出错，因为不存在「另一个人」也在同时改这个 `count`。你脑子里的执行模型是「一条单行道」。

**后端为何不同**：你的 `svc-ai` 服务只是**一个进程**，但它要同时服务成千上万个用户。Spring Boot 内置的 Tomcat 默认开 200 个工作线程，这 200 个线程会**同时**执行你写的同一个 `Controller` 方法。如果你像写前端那样把状态塞进一个共享变量里，就会出现「A 用户的请求把 B 用户的数据改了」这种灵异 bug。

看一个真实的坑——`svc-user` 里统计今日生图次数：

```java
@Service
public class QuotaService {
    // ❌ 危险：这个字段被所有请求线程共享
    private int todayCount = 0;

    public RtData<Integer> incr() {
        todayCount++;            // 不是原子操作：读 → 加 1 → 写回
        return RtData.ok(todayCount);
    }
}
```

`todayCount++` 看起来是一行，实际是三步（读出来、加一、写回去）。当 200 个线程同时执行时，可能 A 和 B 都读到 `100`，各自加成 `101`，最后写回去还是 `101`——少算了一次。这类 bug 在你本地单人点测时**永远不会复现**，一上线高并发就出。

> **前端类比**：想象 `Promise.all([reqA, reqB, ...])` 同时发了 200 个请求，而这 200 个请求的回调里都在 `count++` 改同一个模块级变量。在前端这是单线程排队执行所以没事；在后端这 200 个是**真正同时**跑在不同线程上，就会互相踩踏。

**正确做法的方向**（细节后面讲）：要么别用共享可变状态（把计数放 Redis，用原子操作），要么加锁。

> 深入：单线程到多线程的世界观切换看 [并发入门：从单线程到多线程](/back-end/frontend-backend-guide/14-single-thread-to-multithread)；如何用线程池承载并发看 [线程池与 Executor](/back-end/frontend-backend-guide/15-thread-pools-executor)；怎样写出线程安全的代码、什么时候加锁看 [线程安全与锁](/back-end/frontend-backend-guide/16-thread-safety-locks)。

---

## 1.3 转变二：无状态——服务随时会被杀掉、重启、复制

**前端现状**：你的应用状态（Redux store、组件 state、内存里的对象）活在用户的浏览器里。只要不刷新，状态就一直在。你可以放心地把「当前登录用户」「购物车」存在内存里。

**后端为何不同**：在 Kubernetes 里，你的 `svc-canvas` 不是「一个」进程，而是「同时跑着 3 个副本（Pod）」，而且这些副本随时可能：

- 被 K8s 因为节点资源紧张而**杀掉重新调度**；
- 因为发版**滚动重启**；
- 因为流量上涨**自动扩容**，从 3 个变 10 个。

这意味着：**任何放在单个进程内存里的状态，随时会丢，而且别的副本根本看不到它。**

举个会出事的例子——把 AI 生图任务的状态存在进程内存的 `Map` 里：

```java
@Service
public class TaskService {
    // ❌ 任务状态只存在当前这个进程的内存里
    private final Map<String, String> taskStatus = new ConcurrentHashMap<>();

    public RtData<Void> submit(String taskId) {
        taskStatus.put(taskId, "PROCESSING");   // 提交请求落在副本 A
        return RtData.ok();
    }

    public RtData<String> query(String taskId) {
        // 轮询请求经过负载均衡，可能落到副本 B —— 它的 Map 里压根没这条
        return RtData.ok(taskStatus.getOrDefault(taskId, "NOT_FOUND"));
    }
}
```

前端轮询任务状态时，提交请求落在副本 A，下一次轮询被负载均衡分到了副本 B，B 的内存里没有这个任务，于是返回 `NOT_FOUND`——用户的图明明在生成，前端却显示「任务不存在」。

**正确做法的方向**：任务状态写进 Redis 或 MongoDB，所有副本读同一份外部存储：

```java
// ✅ 状态外置到 Redis，三个副本看到的是同一份
redisTemplate.opsForValue().set("task:" + taskId, "PROCESSING", Duration.ofHours(1));
```

> **前端类比**：这就像你做了 SSR / 多端同步——不能再把用户状态只放进某一个浏览器 Tab 的内存，而要存到服务端（数据库）这个「单一数据源」，所有客户端读同一份。后端的「无状态服务 + 外置存储」是同一个道理：进程是可丢弃的，状态必须活在进程之外。

> 深入：Redis 怎么承载会话、缓存、计数这类外置状态看 [Redis 实战](/back-end/frontend-backend-guide/12-redis-in-practice)；服务为什么会被随意杀掉与复制、Pod 是什么看 [Kubernetes 实战](/back-end/frontend-backend-guide/24-kubernetes-in-practice)。

---

## 1.4 转变三：长生命周期——服务 7×24 不停跑，问题会随时间累积

**前端现状**：页面平均存活几分钟，用户一刷新，内存、定时器、监听器全部清空，从头再来。哪怕你忘了 `clearInterval`、忘了 `removeEventListener`，刷新一下就「治好了」。内存泄漏在前端往往不致命。

**后端为何不同**：`svc-gateway` 启动后可能连续跑几个月不重启。在这种「长生命周期」下，任何「每次请求都增加一点、却从不释放」的资源，都会像滴水一样积累，最终撑爆服务：

- 内存里某个 static 的 `List` 只 `add` 不 `remove` → 几天后 **OOM（内存溢出）**，服务被杀。
- 每次请求都 `new` 一个数据库连接却不归还 → 连接池耗尽，后续请求全部卡死等连接。
- 创建了线程却不关 → 线程数飙到几千，CPU 被上下文切换吃光。

而且后端的内存有「代际」概念，会被 **GC（垃圾回收）** 周期性清理；GC 本身也会让服务出现短暂停顿（STW，Stop The World），表现为「接口偶发性变慢几百毫秒」。这些都是前端从来不需要操心的。

一个典型的连接没归还的写法：

```java
public RtData<User> getUser(Long id) {
    Connection conn = dataSource.getConnection();  // 从连接池借一条连接
    // ... 执行查询 ...
    return RtData.ok(user);
    // ❌ 没有 close()，连接没还回池子。请求量一大，连接池被借空
}
```

连接池默认就那么几十条连接，借出去不还，几分钟内就会出现大量请求卡在「等待获取连接」上——监控里表现为接口超时激增，但 CPU 和内存看着都正常，非常迷惑。

> **前端类比**：这就是放大了无数倍的「忘了 `clearInterval`」。前端里这个 bug 一刷新就没了，所以你不痛不痒；后端里它会潜伏几天，然后在某个流量高峰把整个服务拖垮。**在后端，"清理资源" 不是好习惯，是生死线。**

> 深入：JVM 内存怎么分代、栈和堆是什么看 [JVM 内存模型](/back-end/frontend-backend-guide/18-jvm-memory-model)；GC 怎么工作、停顿从哪来看 [垃圾回收](/back-end/frontend-backend-guide/19-garbage-collection)；如何定位 OOM 和内存泄漏看 [OOM 与内存泄漏排查](/back-end/frontend-backend-guide/20-oom-memory-leak)；连接池为什么会被借空看 [连接池](/back-end/frontend-backend-guide/13-connection-pools)。

---

## 1.5 转变四：看不见的现场——没有 console，没有断点

**前端现状**：出 bug 了，你打开 DevTools，看 Console 报错、看 Network 面板、在 Sources 里打断点单步调试，甚至直接在控制台改变量重试。现场是「看得见、摸得着、可交互」的。

**后端为何不同**：线上的 `svc-canvas` 跑在某台你登不进去的服务器（或容器）里，没有界面、没有控制台、不能打断点（打了断点整个服务就卡住了，几千个用户一起卡）。当一个生图任务失败时，你能依靠的只有四样东西：

| 排查手段 | 它是什么 | 前端类比 |
| --- | --- | --- |
| **日志（Log）** | 代码主动打印的文本流，事后翻看 | 满天飞的 `console.log` + 上报到 Sentry |
| **监控（Metrics）** | CPU / 内存 / QPS / 错误率等数值曲线 | Web Vitals 面板、性能监控大盘 |
| **链路追踪（Trace）** | 一个请求穿过多个服务的完整路径 | Network 面板里一个请求的 Waterfall |
| **dump（线程栈 / 堆快照）** | 把当前进程「拍照」存成文件离线分析 | Memory 面板的 Heap Snapshot、调用栈截图 |

这带来一个根本性的工作方式转变：**你必须提前把「现场」种进代码里，否则出事时无现场可看。** 前端可以等 bug 出现了再去开 DevTools；后端必须在写代码时就想好「如果这里出错，我事后靠什么知道发生了什么」——这就是为什么后端代码里到处是日志。

```java
public RtData<String> submitTask(GenImageReq req) {
    // 进来先打一条，带上能串起整条链路的标识
    log.info("提交生图任务, userId={}, prompt={}, traceId={}",
            req.getUserId(), req.getPrompt(), MDC.get("traceId"));
    try {
        String taskId = canvasService.submit(req);
        log.info("生图任务提交成功, taskId={}", taskId);
        return RtData.ok(taskId);
    } catch (QuotaExceededException e) {
        // 出错时把上下文打全，事后才看得懂
        log.warn("配额不足, userId={}, msg={}", req.getUserId(), e.getMessage());
        return RtData.fail("配额不足，请充值");
    }
}
```

那个 `traceId` 很关键：一个前端请求经过网关 → 认证 → 用户 → AI → 画布五个服务，每个服务各打各的日志。靠 `traceId` 才能把分散在五个服务里的日志串成「这一次请求」的完整故事——这正是前端 Network Waterfall 在分布式后端的对应物。

> **前端类比**：把它想成「线上没有 DevTools，只有 Sentry」的极端版。你不能现场调试，只能依赖事前埋好的日志和上报。区别是后端的"Sentry"由你自己搭建和喂养——打不打日志、打得好不好，直接决定半夜出故障时你能不能在 10 分钟内定位，还是抓瞎两小时。

> 深入：怎么打日志、怎么读日志看 [读懂日志](/back-end/frontend-backend-guide/26-reading-logs)；一套可复用的排障套路看 [排障实战手册](/back-end/frontend-backend-guide/28-troubleshooting-playbook)；线程 dump、堆 dump 等诊断工具看 [诊断工具箱](/back-end/frontend-backend-guide/29-diagnostic-toolbox)；监控与链路追踪体系看 [可观测性](/back-end/frontend-backend-guide/30-observability)。

---

## 1.6 转变五：依赖与故障——你依赖的一切，都会慢、都会挂

**前端现状**：你只依赖一个后端（或几个 API）。它挂了，你顶多展示个「网络错误，请重试」，影响范围就是当前这个用户的当前这次操作。失败是局部的、可见的、用户自己会重试。

**后端为何不同**：`svc-canvas` 这个最复杂的服务，一次生图任务要依赖一长串外部组件，**每一个都可能变慢或挂掉**：

```text
svc-canvas 提交一次生图任务，依赖链：
┌──────────────────────────────────────────────────────┐
│  svc-canvas                                            │
│     │                                                  │
│     ├─(Feign HTTP)→ svc-user    校验配额    ← 可能超时  │
│     ├─(Feign HTTP)→ svc-ai      调模型生图  ← 可能很慢  │
│     ├─(Redis)─────→ 抢分布式锁、缓存任务    ← 可能连不上 │
│     ├─(MongoDB)───→ 落库任务记录            ← 可能慢查询 │
│     ├─(RocketMQ)──→ 发异步消息通知 svc-oss  ← 可能堆积   │
│     └─(OSS)───────→ 存生成的图片            ← 可能限流   │
└──────────────────────────────────────────────────────┘
   任意一环出问题，都可能拖垮 svc-canvas 的所有线程
```

最危险的不是「下游挂了」，而是「下游变慢」。如果 `svc-ai` 从平时 2 秒变成 30 秒不返回，而你调用它时**没设超时**，那么每个等待 `svc-ai` 的请求都会死死占住 `svc-canvas` 的一个工作线程。200 个线程很快被占满，于是 `svc-canvas` 对**所有用户**都没响应了——一个下游的慢，引发了整个服务的雪崩。这就是「级联故障」。

所以后端写远程调用，**默认就要带四件套**：

| 机制 | 解决什么 | 前端类比 |
| --- | --- | --- |
| **超时（Timeout）** | 下游慢就别傻等，到点就放弃 | `axios` 的 `timeout` 配置 |
| **重试（Retry）** | 偶发抖动，自动再试一两次 | 失败请求自动 retry（注意幂等） |
| **降级（Fallback）** | 下游挂了，返回一个兜底结果而不是报错 | 接口失败时展示缓存数据 / 默认值 |
| **熔断（Circuit Breaker）** | 下游持续失败时，直接快速失败一段时间，不再打它 | 短时间内连续失败就停止请求、稍后再恢复 |

```java
// 用 Feign + 降级：svc-user 不可用时，走 fallback 返回兜底结果，而不是把异常往上抛
@FeignClient(name = "svc-user", fallback = UserClientFallback.class)
public interface UserClient {
    @GetMapping("/quota/{userId}")
    RtData<Integer> getQuota(@PathVariable Long userId);
}

@Component
class UserClientFallback implements UserClient {
    @Override
    public RtData<Integer> getQuota(Long userId) {
        // svc-user 挂了/超时/熔断打开时，统一走这里——保护 svc-canvas 不被拖垮
        return RtData.fail("用户服务暂时不可用，请稍后重试");
    }
}
```

> **前端类比**：你给 `axios` 设 `timeout` 是为了不让一个慢接口卡住页面；后端设超时是为了不让一个慢下游卡死整个服务、进而拖垮成千上万个用户。同一个动作，在后端的「破坏半径」大了好几个数量级。**「下游一定会出问题」要成为你写每一次远程调用时的默认假设。**

> 深入：超时、限流、雪崩防护与并发性能看 [性能与并发](/back-end/frontend-backend-guide/31-performance-concurrency)；消息队列怎么保证不丢消息、不重复消费看 [MQ 可靠性](/back-end/frontend-backend-guide/33-mq-reliability)。

---

## 1.7 全景对照：前端关注点 vs 后端关注点

把上面五点压缩成一张表，贴在你工位上：

| 维度 | 前端关注什么 | 后端关注什么 |
| --- | --- | --- |
| **服务对象** | 单个用户的体验 | 高并发下的整体吞吐与公平 |
| **核心指标** | 首屏速度、包体积、FPS | 接口 P99 延迟、QPS、错误率 |
| **资源约束** | 浏览器内存、网络带宽、JS 包大小 | 进程内存 / GC、CPU、连接数、线程数 |
| **状态归属** | 浏览器内存（刷新即清空） | 进程之外（DB / Redis），进程可随时丢弃 |
| **生命周期** | 几分钟，一刷新重来 | 7×24 长跑，问题随时间累积 |
| **出错现场** | DevTools：Console / 断点 / Network | 日志 / 监控 / 链路追踪 / dump |
| **失败影响** | 当前用户的当前操作 | 可能波及全部在线用户（级联雪崩） |
| **兼容性目标** | 各种浏览器 / 设备能正常显示 | 服务可用性 SLA（如 99.9% 不宕机） |
| **校验位置** | 在客户端（`zod`）防手滑、提升体验 | 在服务端入口强制校验，因为客户端不可信 |

注意最后一行：前端用 `zod` 校验主要是为了体验（提前提示用户），可你心里清楚「校验可以被绕过」。在后端，**所有进入系统的数据都默认不可信**——任何人都能用 `curl` 绕过你的前端直接打接口，所以服务端的参数校验、鉴权、限流是**强制的安全边界**，不是体验优化。

> **前端类比**：前端是「为这一个用户尽力做到最好」，后端是「在所有人同时来、还有人想搞破坏的前提下，保证系统整体不崩、对每个人都还过得去」。前者像精雕一件作品，后者像调度一座永不打烊的车站。

---

## 1.8 这套世界观，决定了这门课怎么学

理解了上面五点，你就明白这门课的章节为什么这么排了——它们都是在解决「后端世界观」带来的具体问题：

```text
认知与基础          并发与内存            线上与故障
─────────────       ─────────────        ─────────────
本章 世界观切换  →   14 多线程       →    21 Linux 服务器
02  整体架构         15 线程池            22 网络基础
03  请求链路         16 线程安全/锁        23 Docker
04  三层结构         17 异步编程          24 Kubernetes
05  Java 速成        18 JVM 内存          26 读日志
06  IoC/DI           19 GC                28 排障手册
07  写第一个 CRUD     20 OOM 排查          29 诊断工具箱
                                          30 可观测性
                                          31 性能与并发
```

**给前端的建议路线**（不要从头到尾死读）：

1. **先把世界观立住**：本章 → [项目整体架构](/back-end/frontend-backend-guide/02-architecture-overview) → [一个请求的完整链路](/back-end/frontend-backend-guide/03-request-lifecycle)。
2. **补 Java 与 Spring 的最小子集**：[Java 速成](/back-end/frontend-backend-guide/05-java-crash-course) → [Spring IoC/DI](/back-end/frontend-backend-guide/06-spring-boot-ioc-di) → [动手写一个 CRUD 接口](/back-end/frontend-backend-guide/07-build-a-crud-api)。能跑起来一个接口，比看十篇文章都管用。
3. **遇到具体问题再回来查对应章**：高并发踩坑查 14/15/16，内存爆了查 18/19/20，线上排障查 26/28/29。
4. **全程用运行示例练手**：把每个概念都对应到 `svc-*` 的真实场景（登录、扣配额、提交生图、轮询状态），学一个就去代码里找一个对应实现。

完整的学习地图、阶段目标和时间安排，见本课程的 [学习路线图](/back-end/frontend-backend-guide/93-learning-path)。如果你想先快速建立 Java 全栈的整体印象，也可以扫一眼 [Spring 全栈路线](/back-end/spring-fullstack-roadmap)。

---

## 小结

- 学后端的本质是**换世界观**，不是换语言：你的代码从「一人一浏览器」搬到了「一进程服务所有人、长期运行、看不见现场、依赖一堆会挂的下游」。
- 五个核心转变：**并发**（同段代码被上万请求同时执行）、**无状态**（状态必须外置到 DB/Redis）、**长生命周期**（资源不清理会累积成灾）、**看不见的现场**（靠日志/监控/dump 而非断点）、**依赖与故障**（远程调用默认带超时/重试/降级/熔断）。
- 前端与后端的关注点系统性不同：单用户体验 vs 高并发吞吐、包体积 vs 内存/GC、首屏速度 vs 接口 P99、浏览器兼容 vs 可用性 SLA。
- 后端「破坏半径」远大于前端：一个慢下游、一个没关的连接、一个共享变量，就可能波及全部在线用户——所以「清理资源」「设超时」「校验入参」在后端是生死线而非好习惯。
- 别死读，按建议路线走，全程拿运行示例项目 `svc-*` 练手。

### 自测

1. `svc-user` 里有人写了 `private int todayCount = 0;` 并在接口里 `todayCount++` 来统计调用次数。在高并发下这段代码会出什么问题？为什么本地单人测试发现不了？应该往哪个方向改？
2. 一个 AI 生图任务，提交时显示成功，但前端轮询状态却时好时坏地返回「任务不存在」。结合「无状态」和「多副本」，推测可能的根因，以及正确的状态存放方式。
3. `svc-ai` 平时 2 秒返回，今天突然变成 30 秒不返回，结果 `svc-canvas` 对所有用户都没响应了。请解释这条「级联故障」的发生过程，以及超时/熔断/降级分别在其中起什么作用。

### 下一章

世界观立住了，接下来去看这套世界观在真实项目里长什么样——进入 [项目整体架构](/back-end/frontend-backend-guide/02-architecture-overview)，看微服务是怎么拆的、它们之间怎么通信。

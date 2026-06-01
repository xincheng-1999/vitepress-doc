# 异步编程

> 你早就会异步了。`fetch().then()`、`async/await`、`Promise.all`——你每天都在用。
> 本章要做的，不是教你「异步是什么」，而是把你脑子里那套 Promise 模型，**一对一翻译**成 Java 的 `CompletableFuture` 和 Spring 的 `@Async`。
> 概念几乎完全对得上，对不上的地方（线程池、阻塞取值、同类自调用失效）才是后端真正的坑——这章重点讲那些。

读这章前最好先有两个底子：[线程池与 Executor](/back-end/frontend-backend-guide/15-thread-pools-executor) 讲清楚了「线程从哪来」，[线程安全与锁](/back-end/frontend-backend-guide/16-thread-safety-locks) 讲清楚了多线程并发改数据的坑。本章的异步，本质就是「把任务丢给线程池跑，再用回调/编排把结果接回来」。

---

## 17.1 先对齐概念：同步/异步、阻塞/非阻塞

这四个词在面试和文档里经常混着用，先用前端经验把它们钉死。

- **同步（sync）**：调用方一直等到结果出来才往下走。`const a = compute()`，下一行拿得到 `a`。
- **异步（async）**：调用方不等结果，先往下走，结果晚点通过回调/Promise 给你。`fetch(url).then(...)`，`fetch` 这行立刻返回。
- **阻塞（blocking）**：当前线程被挂起，啥也干不了，干等。
- **非阻塞（non-blocking）**：当前线程发起操作后立刻能去干别的。

> **前端类比**：浏览器主线程是**单线程 + 非阻塞**的典范。你 `fetch` 一个接口，主线程不会卡在那里等响应（不阻塞），而是挂个 `.then` 回调继续跑别的代码，等网络好了事件循环再回调你。后端的异步追求的是同一件事：**别让宝贵的线程干等 IO。**

Java 这边有个关键区别要先说清楚：

| 概念 | 前端世界 | Java 后端世界 |
| --- | --- | --- |
| 谁来跑异步任务 | 事件循环 + 一个主线程 | **多个真线程**（线程池里的线程） |
| `await` / `.get()` 会怎样 | `await` 只让出当前协程，主线程去干别的 | `.get()` / `.join()` **真的阻塞**当前这条线程，让它干等 |
| 默认并行度 | 主线程就一条，并发靠事件循环调度 | 真并行，几条线程就真的同时在几个 CPU 核上跑 |

记住这条最重要的差异：**JS 的 `await` 是「协作式让出」，不占线程；Java 的 `future.get()` 是「真阻塞」，会把一条线程钉死在那干等。** 这条差异是后面所有「该不该传线程池」「为什么不能在 Web 线程里随便 `.get()`」的根源。

---

## 17.2 CompletableFuture：Java 版的 Promise

`CompletableFuture<T>`（下文简称 CF）就是 Java 的 `Promise`。一个 `CompletableFuture<String>` 约等于 TS 里的 `Promise<string>`：一个「将来会有值」的容器。下面是核心 API 的逐个对照，每个都给 JS ↔ Java 并排。

### 17.2.1 创建：`supplyAsync` ↔ `new Promise` / 异步函数

```javascript
// JS：异步执行一段有返回值的逻辑
const p = (async () => {
  return await callAiService();   // 返回 Promise<string>
})();
```

```java
// Java：supplyAsync 把这段逻辑丢到线程池里异步跑，返回 CompletableFuture<String>
CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> {
    return aiClient.gen(req);     // 这段 lambda 在另一条线程上执行
});
```

- `supplyAsync(Supplier)`：有返回值的异步任务（对应 `async () => 返回值`）。
- `runAsync(Runnable)`：没返回值的异步任务，类型是 `CompletableFuture<Void>`（对应 `async () => { 干活但不 return }`）。
- 已经有现成的值，想包成 CF：`CompletableFuture.completedFuture(data)`，对应 `Promise.resolve(data)`。

> Lambda 写法不熟可以回头看 [Java 速成](/back-end/frontend-backend-guide/05-java-crash-course) 和 [Lambda 与 Stream](/back-end/java/04a-lambda-stream)。`() -> {...}` 就是 JS 的 `() => {...}`。

### 17.2.2 转换：`thenApply` ↔ `.then(map)`、`thenCompose` ↔ `.then(flatMap)`

这是最容易混的一对，但用 `map` / `flatMap` 的直觉一秒就能分清：

```javascript
// JS：.then 里 return 一个普通值 → map
fetchUser(id).then(user => user.name);              // Promise<string>

// JS：.then 里 return 一个 Promise → 自动拍平（flatMap），不会变成 Promise<Promise<...>>
fetchUser(id).then(user => fetchAvatar(user.id));   // Promise<Avatar>，不是 Promise<Promise<Avatar>>
```

```java
// Java：thenApply —— 回调返回普通值，相当于 map
CompletableFuture<String> name =
    userClientCf(id).thenApply(user -> user.getName());          // CompletableFuture<String>

// Java：thenCompose —— 回调返回的是另一个 CompletableFuture，相当于 flatMap，会自动拍平
CompletableFuture<Avatar> avatar =
    userClientCf(id).thenCompose(user -> avatarClientCf(user.getId())); // CompletableFuture<Avatar>，不是嵌套
```

一句话记忆：

- 回调返回**普通值** → 用 `thenApply`（= `map`）。
- 回调返回**另一个 CompletableFuture** → 用 `thenCompose`（= `flatMap`），否则你会得到 `CompletableFuture<CompletableFuture<T>>` 这种俄罗斯套娃。

如果回调既不返回值也不需要上一步的结果，只想做个副作用（打日志、发通知），用 `thenAccept`（拿到值但无返回，对应 `.then(v => { log(v) })`）或 `thenRun`（连值都不要，对应 `.then(() => {...})`）。

### 17.2.3 合并多个：`thenCombine` ↔ 两个 Promise 一起等

```javascript
// JS：同时拿配额和用户信息，都到了再合并
const [quota, profile] = await Promise.all([getQuota(uid), getProfile(uid)]);
const view = buildView(quota, profile);
```

```java
// Java：thenCombine —— 两个独立的 CF 都完成后，把两个结果合并成一个
CompletableFuture<Integer> quotaCf   = quotaClientCf(uid);
CompletableFuture<Profile> profileCf = profileClientCf(uid);

CompletableFuture<UserView> viewCf = quotaCf.thenCombine(profileCf,
        (quota, profile) -> buildView(quota, profile));
```

`thenCombine` 只能合两个；要合一批，用下面的 `allOf`。

### 17.2.4 一批任务：`allOf` ↔ `Promise.all`、`anyOf` ↔ `Promise.race`

```javascript
// JS：等全部完成
const all = await Promise.all([a, b, c]);   // 全部成功才 resolve，任一失败立刻 reject
// JS：谁先完成用谁
const first = await Promise.race([a, b, c]);
```

```java
// Java：allOf —— 等全部完成。注意它返回 CompletableFuture<Void>，本身不收集结果！
CompletableFuture<Void> allDone = CompletableFuture.allOf(aCf, bCf, cCf);

// 想拿到结果，要在 allOf 之后自己从各个 CF 取（此时它们都已完成，join 不会真的阻塞多久）
List<String> results = allDone.thenApply(v ->
        Stream.of(aCf, bCf, cCf).map(CompletableFuture::join).collect(Collectors.toList())
).join();

// Java：anyOf —— 谁先完成返回谁，返回 CompletableFuture<Object>（类型被擦掉，要自己转）
CompletableFuture<Object> first = CompletableFuture.anyOf(aCf, bCf, cCf);
```

两个坑前端要特别注意：

1. `allOf` 返回的是 `CompletableFuture<Void>`，**不像 `Promise.all` 那样直接给你一个结果数组**。它只负责「告诉你全到齐了」，结果得你自己从原来的 CF 里 `join()` 出来。
2. `anyOf` 返回 `CompletableFuture<Object>`，泛型信息丢了，拿到要强转，不如 `Promise.race` 顺手。

> **本项目场景**：`svc-canvas` 渲染任务详情页，要并行向 `svc-user`（配额）、`svc-ai`（模型状态）、`svc-oss`（缩略图）三个服务要数据，全到齐再拼成一个响应——这正是 `allOf` 的典型用法。下面 17.5 会给完整代码。

### 17.2.5 错误处理：`exceptionally` ↔ `.catch`，`handle` ↔ `.then(onOk, onErr)`

```javascript
// JS
fetchUser(id)
  .then(u => u.name)
  .catch(err => "默认昵称");          // catch 兜底
```

```java
// Java：exceptionally —— 出异常时给个兜底值，相当于 .catch
CompletableFuture<String> name = userClientCf(id)
        .thenApply(User::getName)
        .exceptionally(ex -> {
            log.warn("取用户失败，走兜底, err={}", ex.getMessage());
            return "默认昵称";
        });

// Java：handle —— 不管成功失败都进来，(结果, 异常) 二选一非 null，相当于 .then(onOk, onErr) 合体
CompletableFuture<String> name2 = userClientCf(id)
        .handle((user, ex) -> ex != null ? "默认昵称" : user.getName());
```

有个**异常包装的坑**：CF 内部抛出的异常，会被包成 `CompletionException`，真正的原因在 `ex.getCause()` 里。所以排查时别只看最外层，要 `ex.getCause()` 才是你抛的那个业务异常。异常类型怎么读看 [异常处理](/back-end/java/05-exception)。

### 17.2.6 取值：`get` / `join` ↔ `await`（但它是真阻塞）

```javascript
// JS：await 让出当前协程，主线程去干别的，不占线程
const data = await cf;
```

```java
// Java：join() / get() —— 真的阻塞当前线程，让它干等到结果出来
String data = cf.join();                 // 异常被包成未检查异常 CompletionException，不用 try-catch
String data2 = cf.get();                 // 抛受检异常，必须 try-catch（InterruptedException/ExecutionException）
String data3 = cf.get(2, TimeUnit.SECONDS); // 带超时，2 秒不来就抛 TimeoutException —— 强烈推荐
```

`get()` 和 `join()` 几乎一样，区别只在异常签名：`join()` 抛运行时异常（不强制 try-catch），`get()` 抛受检异常（编译器逼你处理）。**实践中优先用 `join()`，或者带超时的 `get(timeout)`。**

> **前端类比 + 关键差异**：`join()` 看着像 `await`，但本质天差地别。`await` 在 JS 里**不占线程**（让出去了）；`join()` 在 Java 里会把当前这条线程**钉在原地干等**。所以在 Tomcat 的 Web 线程里随便 `join()` 一个慢任务，等于白白占着一个工作线程不干活——200 个线程很快被占满，服务对所有人都没响应了（这就是 [后端思维](/back-end/frontend-backend-guide/01-backend-mindset) 里说的级联故障的一种）。结论：**`join()` 之前一定要么确定任务很快，要么带超时。**

---

## 17.3 指定线程池：默认的 commonPool 是个坑

这是前端最容易忽略、后端最容易踩的点。

`supplyAsync(supplier)` 不传第二个参数时，任务跑在 **`ForkJoinPool.commonPool()`** 上。这个公共池的大小默认是 `CPU 核数 - 1`，比如 4 核机器上只有 3 条线程。它是给**CPU 密集型**短任务设计的。

问题来了：我们的异步任务大多是 **IO 密集型**（调 Feign、查 MongoDB、请求 OSS），这些任务会长时间阻塞等待网络。如果都丢进只有 3 条线程的 commonPool，几个慢请求就能把整个池占满，连累全应用所有用 commonPool 的地方一起卡住。

```java
// ❌ 不传线程池：IO 任务全挤在 commonPool（默认才几条线程），互相拖累
CompletableFuture.supplyAsync(() -> aiClient.gen(req));

// ✅ 传一个为 IO 量身定做的线程池
CompletableFuture.supplyAsync(() -> aiClient.gen(req), aiExecutor);
```

线程池怎么配（IO 密集型为什么要多开线程、队列和拒绝策略怎么选）在 [线程池与 Executor](/back-end/frontend-backend-guide/15-thread-pools-executor) 讲得很细，这里只给一个 IO 场景的范例：

```java
// svc-canvas 里给「并行调下游」专用的 IO 线程池
@Bean("aiExecutor")
public ThreadPoolTaskExecutor aiExecutor() {
    ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
    ex.setCorePoolSize(16);                 // IO 密集，线程数可远大于核数
    ex.setMaxPoolSize(32);
    ex.setQueueCapacity(200);
    ex.setThreadNamePrefix("ai-async-");    // ★ 给线程起名，排查时一眼认出（看日志/线程 dump 时极有用）
    ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    ex.initialize();
    return ex;
}
```

> **前端类比**：commonPool 就像浏览器里**所有**异步任务共享的那一个事件循环——但 JS 的事件循环不会因为某个回调慢就「占满」（它不阻塞）。Java 线程池不一样：一个 IO 任务阻塞，就真的钉住一条线程。所以「该用哪个池」在后端是必须主动决策的，不能像前端那样无脑全丢给默认调度。规则：**凡是会阻塞等 IO 的异步任务，一律传自定义 Executor，别用默认 commonPool。**

注意带 `Async` 后缀的转换方法（`thenApplyAsync`、`thenComposeAsync`）也能传线程池，控制「后续回调在哪个池里跑」；不带 `Async` 的 `thenApply` 则在「完成前一步的那条线程」上接着跑。一般 IO 链路用带 `Async` 的并显式传池更可控。

---

## 17.4 Spring @Async：把一个方法变成异步

`CompletableFuture` 是手动编排，啰嗦。Spring 提供了 `@Async`：给方法打个注解，调用它时 Spring 自动把方法体丢到线程池里跑，调用方立刻拿到一个 future。

> **前端类比**：有点像你把一个普通函数标成 `async function`，调用它就立刻得到一个 `Promise`，函数体在「后台」执行。区别是 Spring 这个「后台」是真线程池，而且是靠 AOP 代理实现的（这点引出了下面的大坑）。

### 17.4.1 用法三步走

第一步，启动类或配置类上开总开关（**忘了它 `@Async` 完全不生效，还不报错**）：

```java
@SpringBootApplication
@EnableAsync                 // ★ 没有这行，下面所有 @Async 都会变成普通同步调用
public class CanvasApplication { }
```

第二步，给方法打 `@Async`，指定用哪个线程池，返回 `CompletableFuture`：

```java
@Service
public class NotifyService {

    // 异步发任务完成通知，调用方不用等
    @Async("aiExecutor")                              // 指定线程池，不写就用 Spring 默认的
    public CompletableFuture<RtData<Void>> pushDone(String taskId) {
        log.info("发送完成通知, taskId={}, thread={}", taskId, Thread.currentThread().getName());
        // ... 调 push 网关，可能耗时几百毫秒 ...
        return CompletableFuture.completedFuture(RtData.ok());
    }
}
```

第三步，正常调用，拿到的就是 future：

```java
CompletableFuture<RtData<Void>> f = notifyService.pushDone(taskId);
// 这行立刻返回，pushDone 的方法体在 aiExecutor 的线程上跑
```

`@Async` 方法的合法返回类型：`void`（彻底不管结果，发完即忘）、`CompletableFuture<T>`、`Future<T>`。**返回 `void` 时异常会被吞掉**（只进未捕获异常处理器），需要拿结果或处理异常就返回 `CompletableFuture`。

### 17.4.2 三个会让你 debug 半天的坑

**坑一：同类自调用，`@Async` 直接失效。** 这是最经典的。

```java
@Service
public class TaskService {

    public RtData<String> submit(GenImageReq req) {
        String taskId = save(req);
        this.pushNotify(taskId);      // ❌ 同类内部 this 调用，@Async 不生效，变成同步阻塞！
        return RtData.ok(taskId);
    }

    @Async("aiExecutor")
    public void pushNotify(String taskId) { /* ... */ }
}
```

原因：`@Async`（以及 `@Transactional`）靠 Spring 的 **AOP 代理**实现——外部调用先经过代理对象，代理才把活儿丢进线程池。但 `this.pushNotify(...)` 是对象内部直接调自己，**绕过了代理**，于是注解形同虚设。

> **前端类比**：想象你用一个 Proxy 包了某个对象，所有「从外面打进来」的方法调用都会被 Proxy 拦截加工。但对象内部 `this.foo()` 调自己时，走的是原始对象、不经过 Proxy，所以拦截逻辑（这里是「丢到线程池」）就没了。

修法：把异步方法挪到**另一个 Bean** 里，通过依赖注入调用（让调用穿过代理）。这和 [事务与一致性](/back-end/frontend-backend-guide/11-transactions-consistency) 里 `@Transactional` 自调用失效是同一个根因。

```java
@Service
public class TaskService {
    private final NotifyService notifyService;     // 注入另一个 Bean
    public TaskService(NotifyService notifyService) { this.notifyService = notifyService; }

    public RtData<String> submit(GenImageReq req) {
        String taskId = save(req);
        notifyService.pushNotify(taskId);          // ✅ 跨 Bean 调用，经过代理，@Async 生效
        return RtData.ok(taskId);
    }
}
```

依赖注入不熟看 [Spring IoC/DI](/back-end/frontend-backend-guide/06-spring-boot-ioc-di) 或 [Spring IoC 与 DI](/back-end/java/07a-spring-ioc-di)。

**坑二：忘了 `@EnableAsync`。** 没开总开关，`@Async` 静默退化成同步调用，不报任何错，最难发现。验证办法：在异步方法里打印 `Thread.currentThread().getName()`，如果线程名还是 `http-nio-...`（Web 线程）而不是你的 `ai-async-...`，说明根本没异步。

**坑三：异步丢了 traceId / 上下文。** Web 线程里的 `MDC`（存 traceId）、`SecurityContext`（存登录用户）默认**不会**自动传到异步线程，导致异步任务里的日志没了 traceId、取不到当前登录用户。解法是配 `TaskDecorator` 把上下文复制过去（细节属于 [可观测性](/back-end/frontend-backend-guide/30-observability) 范畴，这里先知道有这个坑）。

---

## 17.5 实战：并行聚合下游调用

把上面的东西串起来。**目标**：`svc-canvas` 要返回任务详情页所需数据，得调三个下游服务。

先看**串行**写法——也是前端转后端最容易顺手写出的、却最慢的写法：

```java
// ❌ 串行：三次远程调用排队执行，总耗时 = 三者之和
public RtData<TaskDetailVO> detailSerial(String taskId, Long uid) {
    Integer quota   = userClient.getQuota(uid).getData();      // 等 ~80ms
    AiStatus status = aiClient.status(taskId).getData();       // 等 ~120ms
    String thumb    = ossClient.thumbnail(taskId).getData();   // 等 ~60ms
    return RtData.ok(new TaskDetailVO(quota, status, thumb));  // 总计 ~260ms
}
```

三个调用互不依赖，却一个等完才发下一个，白白把延迟相加。改成**并行**：

```java
// ✅ 并行：三个调用同时发出，总耗时 ≈ 最慢的那个
public RtData<TaskDetailVO> detail(String taskId, Long uid) {
    CompletableFuture<Integer>  quotaCf =
        CompletableFuture.supplyAsync(() -> userClient.getQuota(uid).getData(), aiExecutor);
    CompletableFuture<AiStatus> statusCf =
        CompletableFuture.supplyAsync(() -> aiClient.status(taskId).getData(), aiExecutor);
    CompletableFuture<String>   thumbCf =
        CompletableFuture.supplyAsync(() -> ossClient.thumbnail(taskId).getData(), aiExecutor);

    try {
        // 等三个都到齐（带超时，绝不无限等下游）
        CompletableFuture.allOf(quotaCf, statusCf, thumbCf).get(2, TimeUnit.SECONDS);
        return RtData.ok(new TaskDetailVO(quotaCf.join(), statusCf.join(), thumbCf.join()));
    } catch (TimeoutException e) {
        log.warn("聚合下游超时, taskId={}", taskId);
        return RtData.fail("加载超时，请重试");
    } catch (Exception e) {
        log.error("聚合下游失败, taskId={}", taskId, e.getCause());
        return RtData.fail("加载失败");
    }
}
```

> **前端类比**：这就是把 `await a; await b; await c;`（瀑布式串行，新手常见性能 bug）改成 `await Promise.all([a, b, c])`。后端这里多了两件前端常省略的事：**显式指定线程池**（`aiExecutor`）和**带超时**（`get(2, TimeUnit.SECONDS)`），因为这两点在高并发下是保命的。

时序对比一目了然：

```text
串行（detailSerial，总 ~260ms）
  quota  ├──80──┤
  status         ├───120───┤
  thumb                     ├─60─┤
  时间   └────────────────────────→  累加 = 260ms

并行（detail，总 ~120ms ≈ 最慢的一个）
  quota  ├──80──┤
  status ├───120───┤          ← 三个同时发出
  thumb  ├─60─┤
  时间   └──────────→          取最长 = 120ms
```

**什么时候该上异步并行？** 给个判断清单：

- ✅ 要**并行调多个互不依赖的下游**（上面这个聚合场景）——收益最直接。
- ✅ 有**耗时的副作用任务**，调用方不需要等它（发通知、写审计日志、刷缓存）——用 `@Async` 发完即忘。
- ❌ 几个调用**互相依赖**（B 要用 A 的结果），那是 `thenCompose` 链式，不是并行，省不了时间。
- ❌ 任务本身很快（纯内存计算几微秒），开异步的线程切换开销比任务本身还大，**纯属添乱**。
- ❌ 只是想「让接口看起来快点」就把唯一的核心逻辑丢异步然后立刻 `join`——白绕一圈，还多占线程。

---

## 17.6 关于响应式（WebFlux）：知道它存在就好

你可能听过 Spring **WebFlux** / **Reactor**（`Mono` / `Flux`）这套响应式编程。它走得更激进：用极少的线程通过事件循环承载海量并发连接，理念上更接近 Node.js 的非阻塞模型。

但它**学习曲线陡、调试和排错都更难**（栈追踪难读、心智负担大），而且我们这套 Spring Cloud 项目用的是传统的阻塞式 Web 栈（Tomcat + 线程池）。所以本课程**不展开 WebFlux**——你先用 `CompletableFuture` + `@Async` 把异步用熟，足够覆盖绝大多数后端场景。等哪天遇到「单机要扛十万长连接」这种极端场景，再去专门学它不迟。

---

## 小结

- `CompletableFuture` 就是 Java 的 `Promise`，API 几乎一一对应：`supplyAsync`≈异步函数、`thenApply`≈`.then(map)`、`thenCompose`≈`.then(flatMap)`、`thenCombine`≈合并两个、`allOf`≈`Promise.all`、`anyOf`≈`Promise.race`、`exceptionally`≈`.catch`、`join()/get()`≈`await`。
- 最大差异：JS 的 `await` 不占线程，Java 的 `join()/get()` **真阻塞**一条线程。所以取值一定要带超时，别在 Web 线程里傻等。
- 默认的 `ForkJoinPool.commonPool()` 线程很少、只适合 CPU 短任务；**IO 密集任务（调 Feign/查库/请求 OSS）一律传自定义 `Executor`**，并给线程起名便于排查。
- Spring `@Async` 让方法异步化，但有三个坑：必须加 `@EnableAsync`、**同类自调用因绕过 AOP 代理而失效**（挪到另一个 Bean）、异步线程默认丢失 traceId/登录上下文。
- 该异步的典型场景：并行调多个互不依赖的下游做聚合、发完即忘的副作用任务；不该异步：调用互相依赖、任务本身极快。

### 自测

1. 在 Web 接口里写 `String name = userClientCf(id).get();`（不带超时），高并发下 `svc-user` 突然变慢，会引发什么后果？为什么这跟「JS 里 `await` 一个慢请求」的后果不一样？应该怎么改？
2. 同事在 `TaskService.submit()` 里写了 `this.sendNotify(taskId)`，`sendNotify` 上明明有 `@Async`，但压测发现 `submit` 的耗时把 `sendNotify` 的时间也算进去了，说明没异步成功。请说出根因，并写出正确改法。
3. `svc-canvas` 要并行调 `svc-user`、`svc-ai`、`svc-oss` 三个下游再聚合返回。请说明：为什么不能用默认 commonPool？`allOf` 返回的是什么类型、为什么还要再 `join()` 一次才能拿到结果？这里的超时应该加在哪一步？

### 下一章

异步把「线程怎么用」讲完了，接下来往下钻一层——看这些线程和对象到底活在内存的哪里、栈和堆怎么分。进入 [JVM 内存模型](/back-end/frontend-backend-guide/18-jvm-memory-model)。

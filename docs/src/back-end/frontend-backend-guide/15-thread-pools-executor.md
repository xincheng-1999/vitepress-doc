# 线程池与 Executor

> 上一章我们从「JS 单线程」过渡到「Java 多线程」，知道了后端是用**很多线程**同时处理请求的。这一章解决一个更现实的问题：这些线程从哪来、谁来管、能开多少个、开太多会怎样。答案就是**线程池**——它是后端性能与稳定性的核心，几乎所有线上事故（请求堆积、响应飙升、OOM）都和线程池脱不开关系。

---

## 为什么不能 `new Thread()` 了事

前端写异步，你从不关心「线程」这种东西：`fetch()`、`setTimeout()`、`Promise.all()`，浏览器/Node 在底下帮你调度，你只管 `await`。到了后端，线程是一种**真实、昂贵、有限**的系统资源。

来看两个硬成本：

**1. 创建和销毁线程很贵。** 创建一个线程要向操作系统申请资源、分配栈内存、做内核态调度登记，销毁时又要回收。一次请求来就 `new Thread()`、用完就扔，相当于前端每发一个 fetch 就重新建立一次 TCP 连接还不复用——浪费在「建立/拆除」上的开销可能比干活本身还多。

**2. 线程数量不能无限。** 每个 Java 线程默认占用约 **1MB 栈内存**（`-Xss` 可调）。开 1000 个线程，光栈就吃掉约 1GB，还没算堆。线程一多，CPU 大量时间花在**上下文切换**上而不是干活，吞吐不升反降。

> 前端类比：你不会对一个有 10000 张图的列表同时发 10000 个请求——浏览器自己有并发上限（同域名 6 个左右），多出来的排队等。线程池就是把这套「**并发上限 + 任务排队**」的机制显式地交到你手里：你来定最多几个线程在跑、跑不过来的任务怎么排队、队列也满了又怎么办。

所以后端的铁律是：**任务交给线程池，而不是自己 new 线程。** 复用一批已经创建好的线程，循环利用，省掉反复创建销毁的开销，同时把并发数牢牢控制在一个安全范围内。

### 其实你早就在用线程池了

Spring Boot 内嵌的 Tomcat 处理 HTTP 请求，用的就是一个线程池。你在 `application.yml` 里见过的这段，调的就是它：

```yaml
server:
  tomcat:
    threads:
      max: 200          # 最多 200 个工作线程同时处理请求（默认 200）
      min-spare: 10     # 至少保留 10 个空闲线程待命
    accept-count: 100   # 线程都忙时，最多再排队 100 个连接，超了直接拒绝
```

这意味着：`svc-gateway` 同一瞬间最多有 200 个请求在被真正处理，第 201 个开始排队，排队也满了（accept-count）就被拒。**Controller 里那些「不用立刻给前端返回结果」的活**（比如提交生图任务后发个 MQ 消息、写一条统计日志），如果你 `new Thread()` 去做，就绕开了所有管控；正确做法是再准备一个**业务线程池**专门跑它们。下面就讲怎么造这个池。

---

## ThreadPoolExecutor 的七大参数

Java 所有线程池底层都是 `ThreadPoolExecutor`（`Executors.newXxx()` 只是帮你填好了参数的快捷工厂，后面会讲为什么不要用它）。看懂这七个参数，你就看懂了线程池的全部行为：

```java
public ThreadPoolExecutor(
    int corePoolSize,                 // 1. 核心线程数
    int maximumPoolSize,              // 2. 最大线程数
    long keepAliveTime,               // 3. 非核心线程空闲存活时间
    TimeUnit unit,                    //    上面时间的单位
    BlockingQueue<Runnable> workQueue,// 4. 任务队列
    ThreadFactory threadFactory,      // 5. 线程工厂
    RejectedExecutionHandler handler  // 6. 拒绝策略
)
```

逐个拆开讲。

**1. `corePoolSize` 核心线程数。** 池子的「常备军」。即使空闲也默认不会被回收，随时待命。这是池子的稳态规模。

**2. `maximumPoolSize` 最大线程数。** 池子的「最大编制」。当核心线程全忙、队列也排满了，池子会临时扩招到这个上限。`maximumPoolSize - corePoolSize` 就是允许临时加开的「非核心线程」数量。

**3. `keepAliveTime` + `unit` 空闲存活时间。** 那些临时加开的非核心线程，闲置超过这段时间就会被回收，让池子缩回核心规模，省内存。前端类比：高峰临时叫的外包，闲下来就遣散，常备员工（核心线程）留着。

**4. `workQueue` 任务队列。** 核心线程都在忙时，新任务先进这个队列排队。**这是最关键、最容易踩坑的参数**，单独展开讲：

- `ArrayBlockingQueue(capacity)`：**有界**队列，必须指定容量。生产环境首选——队列满了才会触发扩容/拒绝，问题能被暴露出来。
- `LinkedBlockingQueue()`：不传容量时是**无界**队列（容量 `Integer.MAX_VALUE`）。这意味着队列**永远不会满**，于是 `maximumPoolSize` 形同虚设（永远扩不到），任务无限堆积直到把堆内存撑爆 **OOM**。这就是后面要讲的「不要用 `newFixedThreadPool`」的根因。
- `SynchronousQueue()`：不存任务，每个任务必须直接交给一个线程，否则就扩容/拒绝。`newCachedThreadPool` 用的是它，配合无上限的 `maximumPoolSize`——任务一多就疯狂开线程，可能开爆。

> 一句话记住：**生产环境一律用有界队列。** 无界队列把「线程池满」这个本该被你看见的信号藏起来了，代价是某天毫无征兆地 OOM。

**5. `threadFactory` 线程工厂。** 控制新线程怎么创建——最实用的作用是**给线程命名**。默认线程叫 `pool-1-thread-3` 这种，出问题抓 dump 时你根本认不出是哪个池。起个有意义的名字（如 `svc-ai-sd-1`），排查时一眼定位。

**6. `handler` 拒绝策略。** 当**线程数已达 `maximumPoolSize` 且队列也满了**，再来的任务无处安放，由它决定怎么办。JDK 自带 4 种：

| 拒绝策略 | 行为 | 适用场景 |
| --- | --- | --- |
| `AbortPolicy`（默认） | 直接抛 `RejectedExecutionException` | 不能丢任务、要让上游感知失败 |
| `CallerRunsPolicy` | 让**提交任务的线程**自己跑这个任务 | 想自带「反压」：提交方被拖慢，自然降低提交速度 |
| `DiscardPolicy` | **静默丢弃**新任务，不报错 | 可丢的任务（如某些埋点），但隐蔽，慎用 |
| `DiscardOldestPolicy` | 丢掉队列里**最老**的任务，再尝试提交新的 | 只关心最新数据（如实时行情快照） |

> 前端类比：拒绝策略 ≈ 你给 `axios` 配的「请求队列满了之后怎么办」。`AbortPolicy` 像直接 reject 这个 Promise，让调用方 catch；`CallerRunsPolicy` 像让发起方自己同步执行、自然就慢下来不再猛发；`DiscardPolicy` 像 debounce 把多余的请求悄悄吞掉。

---

## 线程池的工作流程

七个参数怎么协同？记住这条**任务进来时的判断链**，比死背参数有用得多：

```text
                       ┌──────────────────────────┐
   提交任务 execute() ─▶│ 当前运行线程数 < 核心数?  │
                       └───────────┬──────────────┘
                          是 │           │ 否
                  ┌──────────┘           └──────────┐
                  ▼                                 ▼
         ┌─────────────────┐              ┌──────────────────┐
         │ 新建核心线程     │              │ 队列 workQueue    │
         │ 立即执行该任务   │              │ 还没满?           │
         └─────────────────┘              └────────┬─────────┘
                                            是 │        │ 否
                                    ┌──────────┘        └──────────┐
                                    ▼                              ▼
                            ┌───────────────┐          ┌────────────────────┐
                            │ 任务入队排队   │          │ 运行线程数 < 最大数? │
                            │ 等空闲线程来取 │          └─────────┬──────────┘
                            └───────────────┘             是 │       │ 否
                                                  ┌──────────┘       └────────┐
                                                  ▼                          ▼
                                        ┌──────────────────┐     ┌────────────────────┐
                                        │ 新建非核心线程    │     │ 触发拒绝策略 handler│
                                        │ 立即执行该任务    │     │ (抛异常/丢弃/降级)  │
                                        └──────────────────┘     └────────────────────┘
```

读这张图的关键，是注意**「先排队，后扩容」**这个反直觉的顺序：核心线程满了之后，并**不会**立刻去开新线程，而是**先把任务塞进队列**；只有**队列也满了**，才扩容到 `maximumPoolSize`；扩到顶了队列还满，才走拒绝策略。

这正是无界队列致命的原因：队列**永远填不满** → 永远走不到「扩容」和「拒绝」这两步 → 任务无限堆积。这条流程图把这个坑解释得很清楚，值得你记牢。

---

## 写一个真实可用的线程池

把上面的知识落到代码。下面是 `svc-ai` 里一个消费生图任务的线程池，参数怎么定都写在注释里：

```java
import java.util.concurrent.*;

// 用 Guava 的 ThreadFactoryBuilder 给线程命名；也可手写 ThreadFactory
import com.google.common.util.concurrent.ThreadFactoryBuilder;

ThreadFactory factory = new ThreadFactoryBuilder()
        .setNameFormat("svc-ai-render-%d")   // 线程名 svc-ai-render-0/1/2...，dump 里一眼认出
        .build();

ThreadPoolExecutor renderPool = new ThreadPoolExecutor(
        8,                                   // corePoolSize：常备 8 个线程
        16,                                  // maximumPoolSize：高峰最多扩到 16
        60L, TimeUnit.SECONDS,               // keepAliveTime：非核心线程空闲 60s 回收
        new ArrayBlockingQueue<>(200),       // 有界队列，最多排 200 个待处理任务
        factory,
        new ThreadPoolExecutor.CallerRunsPolicy() // 满了让提交线程自己跑，形成反压
);
```

**参数到底怎么定？** 不要抄网上的公式硬套，先判断任务是哪一类：

- **CPU 密集型**（纯计算，比如图片本地缩放、加解密）：线程多了只会抢 CPU 互相拖累。`corePoolSize` ≈ **CPU 核数 + 1**。
- **IO 密集型**（大部分时间在等网络/磁盘/下游，比如调 `svc-oss` 上传、等 AI 推理返回）：线程在等待时不占 CPU，可以多开。经验起点 ≈ **CPU 核数 × 2**，再用压测调。

`svc-ai` 提交任务后要**等远端 GPU 推理返回**，是典型 IO 密集型，所以核心数可以高于核数。**队列容量**则按「能容忍多少积压」定：积压 200 个生图任务意味着排在最后的用户要等很久，与其无限排队让用户干等，不如用 `CallerRunsPolicy` 反压、或直接拒绝并提示「当前排队人数过多，请稍后再试」——这是产品决策，不是技术细节。

> 关键认知：线程池参数**没有标准答案**，它由「任务性质 + 你能接受的延迟/拒绝率」共同决定，最终要靠**压测和监控**来校准，而不是拍脑袋。

---

## Spring 里的线程池：`@Async` + 自定义 Executor

业务代码里你很少手写 `new ThreadPoolExecutor`，而是用 Spring 的 `@Async`——给方法加个注解，它就在线程池里异步执行，调用方不阻塞。**前端类比：就像把一个函数变成「不 await 的 Promise」，丢出去让它后台跑，主流程继续往下走。**

但 `@Async` **默认用的线程池是 `SimpleAsyncTaskExecutor`——它根本不复用线程，每次都 new 一个新线程**，等于回到了我们一开始批判的做法。所以**用 `@Async` 必须自己配 Executor**。

第一步，定义线程池 Bean：

```java
@Configuration
@EnableAsync   // 开启 @Async 支持
public class AsyncConfig {

    @Bean("renderExecutor")
    public Executor renderExecutor() {
        // Spring 的 ThreadPoolTaskExecutor 是对 ThreadPoolExecutor 的封装
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);            // 有界！绝不留默认无界
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("svc-ai-render-");
        // 队列满 + 线程满 时，让调用线程自己执行，形成反压
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅停机：关闭时等在跑的任务做完，最多等 30s
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
```

第二步，在方法上指定用哪个池（**`@Async` 一定要带名字**，否则又退回默认池）：

```java
@Service
public class RenderService {

    // 指定用上面那个 renderExecutor
    @Async("renderExecutor")
    public void submitRenderTask(Long taskId) {
        // 这里调 svc-ai 推理、写 MongoDB、回写 Redis 状态……
        // 整段在 renderExecutor 的线程里跑，提交它的 Controller 线程不会被阻塞
    }
}
```

> `@Async` 两个常见坑（前端不会遇到、后端反复踩）：① **同类内部调用不生效**——`@Async` 靠 Spring AOP 代理实现，类内部 `this.submitRenderTask()` 绕过了代理，注解失效，必须从外部 Bean 调用；这和 `@Transactional` 的失效原因一模一样（见 [第十一章 事务与一致性](/back-end/frontend-backend-guide/11-transactions-consistency)）。② **返回 `void` 的 `@Async` 方法里抛的异常会被「吞掉」**，调用方完全无感——需要返回 `CompletableFuture` 或配 `AsyncUncaughtExceptionHandler` 才能捕获，异步编程详见 [第十七章 异步编程](/back-end/frontend-backend-guide/17-async-programming)。

---

## 为什么不要用 `Executors.newFixedThreadPool`

`Executors` 这个工厂类，《阿里巴巴 Java 开发手册》明文**禁止**在生产使用，原因全在「队列」和「线程数」上。看一眼它的源码就懂了：

```java
// JDK 源码：newFixedThreadPool
public static ExecutorService newFixedThreadPool(int nThreads) {
    return new ThreadPoolExecutor(nThreads, nThreads,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>());   // ← 无界队列！容量 Integer.MAX_VALUE
}

// newCachedThreadPool
public static ExecutorService newCachedThreadPool() {
    return new ThreadPoolExecutor(0, Integer.MAX_VALUE,  // ← 最大线程数无上限！
            60L, TimeUnit.SECONDS,
            new SynchronousQueue<Runnable>());
}
```

两个隐藏炸弹：

- **`newFixedThreadPool` / `newSingleThreadExecutor` 用无界 `LinkedBlockingQueue`。** 队列永远填不满（参见前面的流程图），上游一旦比下游快，任务无限堆积，**直到堆内存被撑爆 OOM**。`svc-canvas` 如果用它接生图任务，赶上一波流量高峰，没有任何拒绝、没有任何报警，进程就在某个瞬间因 `OutOfMemoryError` 倒下——这正是 [第二十章 OOM 与内存泄漏](/back-end/frontend-backend-guide/20-oom-memory-leak) 要排查的经典事故。
- **`newCachedThreadPool` 的 `maximumPoolSize` 是 `Integer.MAX_VALUE`。** 任务一多就疯狂创建线程，可能瞬间开出成千上万个，**直接耗尽内存或打满 CPU**。

> 结论：**永远手动 `new ThreadPoolExecutor(...)`，自己指定有界队列和合理的最大线程数**（Spring 项目用 `ThreadPoolTaskExecutor`）。`Executors.newXxx()` 把最危险的参数藏在了「看起来很方便」的工厂方法背后，省下的几行代码不值得拿线上稳定性换。

---

## 本项目实战：`svc-ai` 按模型类型分池

`svc-ai` 要支持多种 AI 模型：SD（Stable Diffusion，慢、吃 GPU）、Flux（更慢、显存更大）、轻量风格迁移（快）。如果**所有任务挤在一个线程池**里，会发生「**慢任务拖死快任务**」——8 个线程全被慢吞吞的 Flux 占满，本该秒回的风格迁移任务在队列里干等。

> 这叫**线程池隔离**（舱壁模式 Bulkhead）。前端类比：你不会把「上传大文件」和「实时搜索建议」共用同一个请求并发额度，否则一个大上传就把搜索框卡住。后端用「不同业务用不同线程池」来做同样的隔离。

做法是给每种模型一个独立线程池，慢模型的积压不会波及快模型：

```java
@Configuration
@EnableAsync
public class RenderExecutorConfig {

    // SD 模型：中等速度，并发可适当高
    @Bean("sdExecutor")
    public Executor sdExecutor() {
        return build("svc-ai-sd-", 8, 16, 200);
    }

    // Flux 模型：很慢且吃显存，线程数和队列都收紧，避免拖垮 GPU
    @Bean("fluxExecutor")
    public Executor fluxExecutor() {
        return build("svc-ai-flux-", 2, 4, 50);
    }

    // 轻量风格迁移：快，给独立池保证它不被慢任务饿死
    @Bean("styleExecutor")
    public Executor styleExecutor() {
        return build("svc-ai-style-", 16, 32, 500);
    }

    private Executor build(String prefix, int core, int max, int queue) {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(core);
        e.setMaxPoolSize(max);
        e.setQueueCapacity(queue);
        e.setThreadNamePrefix(prefix);
        e.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        e.initialize();
        return e;
    }
}
```

分发任务时，按模型类型选池。这里用拒绝异常给前端一个明确的「排队中」反馈，而不是让它无限等：

```java
@Service
public class AiRenderDispatcher {

    @Resource(name = "sdExecutor")
    private Executor sdExecutor;
    @Resource(name = "fluxExecutor")
    private Executor fluxExecutor;
    @Resource(name = "styleExecutor")
    private Executor styleExecutor;

    public RtData<Void> dispatch(RenderTask task) {
        Executor pool = switch (task.getModelType()) {
            case "SD"    -> sdExecutor;
            case "FLUX"  -> fluxExecutor;
            case "STYLE" -> styleExecutor;
            default      -> throw new IllegalArgumentException("未知模型类型");
        };
        try {
            pool.execute(() -> doRender(task));   // 提交到对应模型的池
            return RtData.ok();
        } catch (RejectedExecutionException ex) {
            // 触发了 AbortPolicy：池满 + 队列满，明确告诉前端别干等
            return RtData.fail("当前模型排队人数过多，请稍后再试");
        }
    }

    private void doRender(RenderTask task) { /* 调推理、写状态、发 MQ */ }
}
```

> 真实项目里，这些线程池的参数通常不会写死在代码里，而是放到配置中心（`application.yml` / Nacos），不同环境、不同流量配不同值，改了不用发版。配置外置见 [第二十五章 配置与环境管理](/back-end/frontend-backend-guide/25-config-and-env)。

---

## 线程池满了：症状与排查

线程池是**最常见的线上故障源**之一。它满了的时候，表现往往不直接——你看到的是「接口慢」「报错」，要顺藤摸瓜才能定位到是某个池满了。

**症状（怎么察觉）：**

- 接口 **RT（响应时间）突然飙升**，但下游（数据库、AI 服务）监控却显示自己并不慢——任务卡在**队列里排队**，还没轮到执行。
- 日志里大量 `RejectedExecutionException`（用了 `AbortPolicy`），或接口直接返回你写的「排队人数过多」。
- 用了 `CallerRunsPolicy` 时更隐蔽：**Tomcat 的请求线程**被拉去跑业务任务，导致接收新请求的能力下降，整个服务一起变慢。

**排查目标：确认是哪个池、它的活跃线程数和队列长度。** `ThreadPoolExecutor` 自带几个一抓就准的指标：

```java
// 在监控/健康检查接口里读出来，或打到日志
log.info("pool={} active={} poolSize={} queueSize={} completed={}",
        "svc-ai-flux",
        renderPool.getActiveCount(),      // 正在执行任务的线程数
        renderPool.getPoolSize(),         // 当前总线程数
        renderPool.getQueue().size(),     // 队列里积压的任务数 ← 重点看它
        renderPool.getCompletedTaskCount()// 已完成任务总数
);
```

跑起来后，一段健康的输出长这样：

```text
pool=svc-ai-flux active=2 poolSize=2 queueSize=0  completed=18452
```

出问题时会变成这样：

```text
pool=svc-ai-flux active=4 poolSize=4 queueSize=50 completed=18460
```

**怎么读这段输出：**

- `active=4 poolSize=4`：4 个线程全在干活，已经达到 `maxPoolSize`（Flux 池配的就是 max=4），**没有空闲线程了**。
- `queueSize=50`：队列塞满（Flux 池配的 queue=50），**再来的任务就要被拒绝了**。
- `completed` 在两次采样间几乎不涨（18460 比 18452 只多 8）：说明任务**执行得极慢甚至卡住**——结合 `active` 一直顶满，基本可以判定是**下游 GPU 推理变慢或卡死**，任务出不去、队列只进不出。

**结论与处置：** 这不是「线程池太小」的问题，**根因在下游**——Flux 推理变慢导致任务积压。扩大线程池只会让更多任务卡在那里、加速 OOM。正确动作是先去查 `svc-ai` 调的 GPU 推理为什么慢（超时配置？显存打满？），同时给前端降级（提示排队、或临时关闭 Flux 入口）。

> 生产环境不会靠手动打日志看这些指标，而是把它们接入监控系统（Micrometer 暴露 `executor.active` / `executor.queued` 等指标到 Prometheus，配 Grafana 看板和告警）。系统化的监控与告警见 [第三十章 可观测性](/back-end/frontend-backend-guide/30-observability)。

更完整的「线程池满 / 请求堆积」实战排查步骤（抓线程 dump、定位卡住的线程在等什么），收录在 [第二十八章 排查实战手册](/back-end/frontend-backend-guide/28-troubleshooting-playbook)；用到的工具（`jstack`、Arthas 等）见 [第二十九章 诊断工具箱](/back-end/frontend-backend-guide/29-diagnostic-toolbox)。

---

## 小结

- 后端线程是**昂贵且有限**的资源（创建销毁有开销、每个占约 1MB 栈），所以**任务交给线程池复用，而不是 `new Thread()`**；Tomcat 处理请求本身就是个线程池，业务异步任务要另配自己的池。
- `ThreadPoolExecutor` 七大参数里，**有界 `workQueue`** 最关键。任务进来的顺序是「**先建核心线程 → 满了入队 → 队列满了才扩容到 max → 再满才走拒绝策略**」，记住这条链就理解了全部行为。
- 拒绝策略 4 种：`AbortPolicy`（抛异常，默认）、`CallerRunsPolicy`（调用方自己跑，反压）、`DiscardPolicy`（静默丢）、`DiscardOldestPolicy`（丢最老的）。生产常用前两种。
- **绝不用 `Executors.newFixedThreadPool` / `newCachedThreadPool`**——前者无界队列会 OOM，后者无上限线程会开爆。一律手动 `new ThreadPoolExecutor` 或 Spring 的 `ThreadPoolTaskExecutor`，指定有界队列和合理上限。
- Spring 用 `@Async("executorName")` 异步执行，**必须自配带名字的 Executor**，否则退回不复用线程的默认池；注意类内部调用失效、`void` 方法吞异常两个坑。
- 线程池满的典型信号是 **RT 飙升 + 队列积压（`queueSize` 高）+ 活跃线程顶满**，但根因常在下游变慢，**扩池往往是错的解法**，要先查下游。

### 自测

1. 一个线程池配置为 `corePoolSize=4, maximumPoolSize=10, workQueue=ArrayBlockingQueue(20)`，此刻同时来了 30 个任务。会有几个线程在跑、队列里有几个、有没有任务被拒绝？（提示：按流程图一步步走。）
2. 为什么说 `Executors.newFixedThreadPool` 会导致 OOM？把它换成什么写法能避免，关键改了哪个参数？
3. `svc-ai-flux` 池的监控显示 `active=4 poolSize=4 queueSize=50`，且 `completedTaskCount` 几乎不增长。你会判断根因是「线程池太小，应该扩容」吗？为什么？真正该先排查什么？

### 下一章

线程池让多个线程并发跑了起来，可这些线程一旦**同时读写同一份数据**（比如并发扣减用户配额），就会出现前端几乎遇不到的「竞态」问题——下一章 [第十六章 线程安全与锁](/back-end/frontend-backend-guide/16-thread-safety-locks) 就来解决「多个线程抢同一块数据怎么办」。

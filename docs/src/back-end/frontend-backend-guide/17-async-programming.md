# 异步编程：几次独立查询，怎样一起等结果

::: info 按需阅读
这一篇用 `CompletableFuture` 组织异步结果，需要时再看。示例里的客户端、`RtData` 和业务对象都是局部示意，不是可运行 CRUD 已有的功能。线程数量如何控制见 [线程池](/back-end/frontend-backend-guide/15-thread-pools-executor)，共享数据如何保护见 [线程安全](/back-end/frontend-backend-guide/16-thread-safety-locks)；它们不是 [五阶段主线](./index) 的额外必修前置。
:::

一个任务详情页需要三份数据：用户剩余额度、处理状态和缩略图。假设三次调用分别耗时 80ms、120ms、60ms，而且互不依赖。

```java
Integer quota = userClient.getQuota(uid).getData();
AiStatus status = aiClient.status(taskId).getData();
String thumb = ossClient.thumbnail(taskId).getData();
return RtData.ok(new TaskDetailVO(quota, status, thumb));
```

这会等第一份数据回来才去请求第二份，大致花费三者之和。能不能先发起三次调用，等它们都完成再组合结果？这就是本篇要解决的问题。

## 1. 异步返回的不是结果，而是结果的约定

前端常这样写：

```javascript
const [quota, status, thumb] = await Promise.all([
  getQuota(uid),
  getStatus(taskId),
  getThumbnail(taskId),
])
```

Java 的 `CompletableFuture<T>` 也能表示“将来得到一个 `T`，或者以异常结束”。例如：

```java
CompletableFuture<Integer> quotaCf = CompletableFuture.supplyAsync(
        () -> userClient.getQuota(uid).getData(), aiExecutor);
```

这次提交会返回一个 Future，查询逻辑交给 `aiExecutor` 执行。提交本身也可能因池满而失败，不保证永远立即成功返回。

这里要分开两件事：调用方可以先继续，是**异步**；执行查询的工作线程仍可能在等网络响应，这是**阻塞 I/O**。换一条线程等待，并没有让底层客户端变成非阻塞客户端。

同样，JS 的 Promise 也不自动把函数交给后台线程：`new Promise(...)` 的执行器会同步调用，`async` 函数在第一个实际让出点之前也会同步执行。两种语言都能表示异步结果，但调度方式不能一一照搬。

## 2. 先给这些查询准备一个有边界的池

阻塞式远程调用不宜随意挤进公共池。下面是 Spring 配置片段，容量仅作演示，需按下游并发能力和等待预算验证：

```java
@Bean("aiExecutor")
public ThreadPoolTaskExecutor aiExecutor() {
    ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
    ex.setCorePoolSize(8);
    ex.setMaxPoolSize(16);
    ex.setQueueCapacity(200);
    ex.setThreadNamePrefix("task-detail-");
    ex.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    ex.setWaitForTasksToCompleteOnShutdown(true);
    ex.setAwaitTerminationSeconds(30);
    return ex;
}
```

把它放在配置类中并注入调用方，容器负责生命周期。省略执行器的 `supplyAsync(...)` 通常使用 `ForkJoinPool.commonPool()`；它是共享资源，具体并行度受 JDK、系统与配置影响，某些低并行度情况下还会使用其他线程创建行为。不要把它理解成“应用独占的固定几条 I/O 线程”。

明确的业务池能限制这类查询的积压和影响范围，但一条请求拆成三个任务，也增加了同时访问下游的数量，并不免费。

## 3. 发起三次调用，再有期限地等待

下面保留同步返回响应的写法，便于看清楚哪里在等待。`aiExecutor`、客户端和 `log` 都由所在业务类提供：

```java
public RtData<TaskDetailVO> detail(String taskId, Long uid) {
    try {
        CompletableFuture<Integer> quotaCf = CompletableFuture.supplyAsync(
                () -> userClient.getQuota(uid).getData(), aiExecutor);
        CompletableFuture<AiStatus> statusCf = CompletableFuture.supplyAsync(
                () -> aiClient.status(taskId).getData(), aiExecutor);
        CompletableFuture<String> thumbCf = CompletableFuture.supplyAsync(
                () -> ossClient.thumbnail(taskId).getData(), aiExecutor);

        CompletableFuture.allOf(quotaCf, statusCf, thumbCf)
                .get(2, TimeUnit.SECONDS);

        return RtData.ok(new TaskDetailVO(
                quotaCf.join(), statusCf.join(), thumbCf.join()));
    } catch (RejectedExecutionException e) {
        return RtData.fail("当前繁忙，请稍后再试");
    } catch (TimeoutException e) {
        log.warn("聚合下游超时, taskId={}", taskId);
        return RtData.fail("加载超时，请重试");
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return RtData.fail("请求已中断");
    } catch (ExecutionException e) {
        log.error("聚合下游失败, taskId={}", taskId, e.getCause());
        return RtData.fail("加载失败");
    }
}
```

三次调用先提交，`allOf` 再建立一个“全部完成”的 Future。它返回 `CompletableFuture<Void>`，**不收集结果，也不会像 `Promise.all` 那样因第一个失败就立即结束等待**。所有任务完成后，若有异常，它会异常完成。示例先用带超时的 `get` 等待，只有全部成功才继续读取各自结果；这时三个 `join()` 不再需要等未完成的任务。

如果队列和下游都有余量，总时间可能接近最慢的 120ms，再加提交、排队和组装开销；不能保证永远如此。串行调用也不是天然错误：后一项依赖前一项结果，或者下游承受不了更高并发时，就不能简单并发发起。

### 两秒超时，限制了什么

这里的 `get(2, TimeUnit.SECONDS)` 限制的是**当前线程等待聚合 Future 的时间**，不是整个请求必在两秒内结束的保证，也不是下游网络超时。超时后，已提交的调用通常仍在运行；第二次或第三次提交被拒绝时，前面已接收的任务也可能继续。

因此还要给 HTTP、数据库等客户端设置连接和读取超时，必要时配合能够真正中断底层操作的取消机制。`CompletableFuture.cancel(...)` 也不能被当成一定终止远程调用。否则只是用户不等了，后台线程仍然长期被占用。

这个示例中的 `get` **会阻塞当前请求线程**。它通过重叠三次独立等待缩短总延迟，却没有释放正在等待的 Web 线程。JS 的 `await` 则暂停当前异步函数，让对应 agent 能执行其他任务，两者不是相同的等待机制。

如果只是把唯一一次调用交给工作线程后立刻 `join()`，通常既没缩短等待，又多占用一条线程，不值得为了“用了异步”这样改。单次等待还会在请求增多时累积成资源耗尽，影响其他用户；这种服务侧的资源边界也见 [后端思维](/back-end/frontend-backend-guide/01-backend-mindset)。

## 4. 结果还需要后续处理时，怎么连接

不用每一步都取出来再提交。可以给 Future 接后续操作：

```java
// 回调返回普通值：转换结果
CompletableFuture<String> nameCf = userClientCf(id)
        .thenApply(user -> user.getName());

// 回调返回另一个 Future：接上下一次异步操作
CompletableFuture<Avatar> avatarCf = userClientCf(id)
        .thenCompose(user -> avatarClientCf(user.getId()));

// 两个互不依赖的结果：都完成后再组合
CompletableFuture<UserView> viewCf = quotaCf.thenCombine(profileCf,
        (quota, profile) -> buildView(quota, profile));
```

`thenApply` 像结果转换；`thenCompose` 会接平下一层 Future，否则容易得到 `CompletableFuture<CompletableFuture<T>>`。JS 的 `.then` 会自动接平返回的 Promise，Java 这里需要选对方法。`() -> ...` 的语法可回看 [Java 速成](/back-end/frontend-backend-guide/05-java-crash-course) 和 [Lambda 与 Stream](/back-end/java/04a-lambda-stream)。

不带 `Async` 的回调不保证都在某一条固定线程执行：可能由完成前一步的线程执行，也可能在注册时由当前线程执行。轻量结果转换通常足够；要在后续做新的阻塞操作，可以显式使用带执行器的 `thenApplyAsync(..., executor)` 等方法。

### 失败不是都要变成默认值

```java
CompletableFuture<String> nameCf = userClientCf(id)
        .thenApply(user -> user.getName())
        .exceptionally(ex -> "暂未获取昵称");
```

`exceptionally` 在失败时给出替代结果；`handle` 可以统一处理成功或失败；`whenComplete` 常用于记录结果或异常，而不刻意把失败变成成功。

昵称失败或许能降级，但权限校验、扣费失败就不能随便返回一个“成功”的默认值。先决定业务上允许什么，再选择错误处理方法。

通过 `join()` 取值时，异常通常表现为未检查的 `CompletionException`；`get()` 则可能抛出 `ExecutionException`，还需要处理中断。读异常链时看真实原因，但也不要假定每个异常都有 `cause`。相关基础见 [Java 异常](/back-end/java/05-exception)。

::: details 按需查：其他几个 Future 方法
`runAsync(task, executor)` 用于没有结果的任务，返回 `CompletableFuture<Void>`；`completedFuture(value)` 把现成值包装成已完成 Future，**不会启动后台任务**。

`thenAccept` 消费上一步结果但不返回新值，`thenRun` 不使用上一步结果。`anyOf` 返回率先完成者的结果或异常，结果类型是 `Object`；它不负责取消其他任务，也不等于“第一个成功结果”。

`join()` 没有超时参数，也不会像 `get()` 那样直接抛出 `InterruptedException`。`get(timeout)` 限制本次等待；`orTimeout(...)` 则会使对应 Future 超时异常完成，但同样不保证停止底层工作。不要以为加上任一超时方法，就已做好端到端超时控制。
:::

## 5. 只是想把一个方法交给后台时，用 @Async

例如任务完成后尝试发一条通知，不需要像详情页那样组合三个结果，可以让 Spring 代理代为提交：

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    // aiExecutor 的 Bean 定义使用前面的配置
}
```

```java
@Service
public class NotifyService {
    @Async("aiExecutor")
    public CompletableFuture<Void> pushDone(String taskId) {
        sendNotification(taskId);
        return CompletableFuture.completedFuture(null);
    }
}
```

另一个 Bean 注入 `NotifyService` 后调用：

```java
CompletableFuture<Void> future = notifyService.pushDone(taskId);
future.whenComplete((ignored, ex) -> {
    if (ex != null) {
        log.warn("通知失败, taskId={}", taskId, ex);
    }
});
```

这里的后台执行来自 `@EnableAsync` 和经过代理的调用，不是因为返回类型叫 Future。同一个类里用 `this.pushDone(...)`，在默认代理模式下绕过代理，不会变成异步。修法通常是把通知方法放到另一个 Bean，通过注入调用，原因与 [事务注解](/back-end/frontend-backend-guide/11-transactions-consistency) 相同。注入方式见 [Spring IoC/DI](/back-end/frontend-backend-guide/06-spring-boot-ioc-di) 和 [IoC 与 DI 详解](/back-end/java/07a-spring-ioc-di)。

返回 `void` 时，异步异常不能直接返回原调用方，会交给 `AsyncUncaughtExceptionHandler`，默认通常会记录日志；不是“抛了就完全无声”。返回 Future 时，也要真正观察失败，不能拿到对象后一直不处理。

还有两个边界不能靠注解省掉：请求线程的事务不会自动跨到工作线程；日志 traceId、登录上下文等也不要假定会自动传播。需要传播时应按框架机制显式复制，并在工作线程复用前清理，见 [可观测性](/back-end/frontend-backend-guide/30-observability)。

如果通知或审计记录要求可靠完成，内存里的异步任务可能随进程崩溃丢失，需要持久记录、重试和幂等处理，不能把重要副作用一概“发完即忘”。

::: details WebFlux 是另一条路线，不是本篇的隐藏前置
Spring WebFlux 常用事件循环和非阻塞调用组织请求。是否有收益，要看整条调用链、并发规模和团队能力；把阻塞式数据库或 HTTP 调用放进去，并不会自动变成非阻塞。本篇先限定在普通线程池与 Future，不为了讲异步再引入另一套 Web 栈。
:::

回到任务详情：只有调用互相独立、下游有能力承受并发，而且等待和失败都有边界时，重叠执行才真正有价值。需要继续理解这些线程与对象占用哪部分内存，再看 [JVM 内存模型](/back-end/frontend-backend-guide/18-jvm-memory-model)。

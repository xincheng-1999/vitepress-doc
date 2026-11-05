# 线程池与 Executor：任务太多时怎么办

::: info 按需阅读
这一篇讨论后台任务的数量限制，不是要求给每个接口加异步。代码用于说明配置和行为，不是一套完整工程；第一次学习可继续走 [五阶段主线](./index)。
:::

任务系统允许用户导出报表。一次导出要查数据、写文件，可能耗时几秒。如果每个请求都 `new Thread()`，平时看起来没问题，流量突然增加时却会不断创建线程，争用内存和下游连接。

改用线程池，不只是为了少创建几次线程。更重要的是回答：**最多多少任务正在执行，多少任务可以排队，超过这个范围怎么办？**

## 1. 用一个池子，把限制写出来

先看一个配置片段：

```java
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

AtomicInteger sequence = new AtomicInteger();
ThreadFactory factory = task ->
        new Thread(task, "task-export-" + sequence.incrementAndGet());

ThreadPoolExecutor exportPool = new ThreadPoolExecutor(
        8, 16,
        60L, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(200),
        factory,
        new ThreadPoolExecutor.AbortPolicy()
);
```

这个池子只承接任务导出，不是所有业务共用的池。`8`、`16` 和 `200` 只是演示值，不是推荐容量。

它最多使用 16 条工作线程，队列最多保存 200 个待执行任务。线程名带业务含义，后续看日志或线程栈时容易辨认；`AtomicInteger` 只是安全地生成名称序号。池的基本工作顺序是：

```text
提交任务
  ├─ 工作线程数不足 8：创建线程执行
  └─ 已有至少 8 条线程：尝试入队
       ├─ 队列有空间：等待工作线程取走
       └─ 队列已满：尝试增加线程，最多到 16
            └─ 仍接不下：执行拒绝策略
```

关键是**先入队，再扩到最大线程数**，不是忙到 8 条就马上增加到 16 条。默认情况下，核心线程按需创建，并非构造池时一次建齐；额外线程空闲 60 秒后可以回收。核心线程默认不因空闲超时退出，也可以另行配置。

假设所有任务都还没完成：前 8 个开始执行，再来 200 个入队，之后 8 个可以由新增线程执行。再提交一个，便可能被拒绝。实际运行中任务会不断完成，不能只按提交总数推算瞬时状态。

`Executor` 是“可以接收并执行任务”的接口，`ThreadPoolExecutor` 是它的一种实现，并非所有执行器都由这个类实现。

## 2. 满了以后，让调用方知道发生了什么

上面使用 `AbortPolicy`：无法接收任务时抛出 `RejectedExecutionException`，关闭后的池也会拒绝新任务。因此，提交处要处理失败，不能已经告诉用户“提交成功”，却没把工作交进去：

```java
try {
    exportPool.execute(() -> export(taskId));
    // 这里只能说明池已接收，不代表导出完成
} catch (RejectedExecutionException ex) {
    // 告诉调用方当前繁忙；若先建了任务记录，也要处理它的状态
}
```

常见策略的差别在这里：

| 策略 | 做什么 | 要承担的后果 |
| --- | --- | --- |
| `AbortPolicy` | 抛拒绝异常 | 上游需要明确处理失败或重试 |
| `CallerRunsPolicy` | 池未关闭时，让提交线程执行任务 | 可以拖慢提交速度，但也可能占住 HTTP 请求线程 |
| `DiscardPolicy` | 静默丢掉新任务 | 不适合不能丢失的业务任务 |
| `DiscardOldestPolicy` | 丢掉队列头部任务，再尝试接收新任务 | 旧任务可能无声消失，不能理解为可靠的“只保留最新”方案 |

`CallerRunsPolicy` 不是免费的反压：池满时它会使提交调用变慢，甚至不再异步；池关闭后它会丢弃任务。静默丢弃与返回 Future 的提交方式组合，还可能留下永不完成的 Future。选择策略时，要一起考虑“谁提交”和“调用方怎么知道结果”，而不是只看名字。

另一个边界是**可靠性**。内存队列中的任务在进程崩溃后会丢失。重要导出、审计或通知若要求可靠执行，需要持久任务记录、重试和幂等等设计，不能把线程池当消息队列。

## 3. 队列大小，比“最多几条线程”更容易被忽略

`newFixedThreadPool(8)` 看起来也限制了线程数，但它默认用容量接近 `Integer.MAX_VALUE` 的队列。如果提交长期快于完成，任务和它们引用的数据会不断占堆，最终可能发生 [OOM](/back-end/frontend-backend-guide/20-oom-memory-leak)。大队列还会把过载隐藏成很长的等待时间。

几种队列的行为：

- `ArrayBlockingQueue<>(200)` 明确限制排队数量。
- `LinkedBlockingQueue<>(200)` 同样有界；不传容量时，上限是 `Integer.MAX_VALUE`，通常视作无界。
- `SynchronousQueue` 不存储任务，必须直接交给工作线程，否则需要增线程或拒绝。

`newCachedThreadPool()` 使用最后一种队列，但允许极大的最大线程数，过载时可能创建过多线程。不是 `Executors` 的所有工厂方法都不能用，而是**使用前必须知道它隐藏了什么限制**。对承接外部流量的业务池，显式配置并发上限、有界队列和拒绝行为通常更容易控制。

容量怎么定？先看下游能承受多少并发、单任务占多少内存，以及用户最多愿意等多久。纯计算任务增加线程不一定增加吞吐；等待网络较多的任务可以使用更多线程，但也会同时占用更多连接、内存和下游配额。CPU 核数公式只能作为试验起点，不能代替压测。

## 4. Spring 中，把这个池交给容器管理

在 Spring 中可以使用 `ThreadPoolTaskExecutor`，避免业务代码自己管理每次线程创建。下面是一个配置片段，需要相应的 Spring 类和并发类 import：

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean("exportExecutor")
    public ThreadPoolTaskExecutor exportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("task-export-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
```

容器负责初始化和销毁这个 Bean。停机设置表示关闭时尝试完成任务，并等待一定时间；**30 秒不是自动把任务做完的保证，也不是任务运行超时**，外部容器仍可能先终止进程。应用要为中断、失败和重试留下合适的处理。

另一个 Bean 可以这样使用它：

```java
@Service
public class ExportService {
    @Async("exportExecutor")
    public void submitExportTask(Long taskId) {
        // 执行导出，记录成功或失败状态
    }
}
```

`@EnableAsync` 开启支持，`@Async("exportExecutor")` 指定执行器。默认从其他 Bean 经过 Spring 代理调用时，框架才会把方法交给这个池；同类中的 `this.submitExportTask(...)` 不经过代理，通常仍同步执行。这与 [事务注解的自调用问题](/back-end/frontend-backend-guide/11-transactions-consistency) 类似。

不要假定不写名字就一定使用某一种默认执行器。Spring、Spring Boot 版本和已有 Bean 都可能影响选择；明确指定名称更容易读懂和验证。返回 `void` 的异步方法异常不能直接抛回原调用方，会交给异步异常处理机制；需要结果时可返回 `CompletableFuture`，详见 [异步编程](/back-end/frontend-backend-guide/17-async-programming)。

普通 JDK 程序自己创建的 `ExecutorService`，则要在所属组件结束使用时调用 `shutdown()`；它停止接收新任务，但不等于已经等到所有任务结束。不要在每个请求结束时关闭本该长期复用的池。

## 5. 有了业务池，HTTP 线程池还在吗

还在。同步 Spring MVC 里，Tomcat 工作线程负责处理 HTTP 请求；上面的业务池是另一组线程。把导出交出去，不等于请求处理本身不再使用线程。

::: details 按需看：Tomcat 的几个配置不是同一个队列
```yaml
server:
  tomcat:
    threads:
      max: 200
      min-spare: 10
    accept-count: 100
```

这些是常见配置示例，实际行为需结合 Spring Boot 和 Tomcat 版本验证。`threads.max` 限制工作线程，`min-spare` 是空闲线程的相关设置；不能把 `accept-count` 直接理解为“第 201 个 HTTP 请求开始排的那条任务队列”。它涉及达到连接接收限制后的连接等待，还要结合 `max-connections`、操作系统 backlog 等设置。更不能推断成“第 301 个请求必定拒绝”。

网关还可能采用不同的 Web 栈，并非所有服务都使用 Tomcat 的这组参数。
:::

## 6. 接口变慢时，先看任务堵在哪里

假设监控读到：

```java
log.info("active={} poolSize={} queueSize={} completed={}",
        exportPool.getActiveCount(),
        exportPool.getPoolSize(),
        exportPool.getQueue().size(),
        exportPool.getCompletedTaskCount());
```

```text
active=16 poolSize=16 queueSize=200 completed=18460
```

结合前面的配置，这一刻工作线程都很忙，队列也满了。但这份快照**不能证明根因是池太小，也不能证明一定是下游故障**。

继续比较一段时间里的完成速度、排队时长和拒绝次数。如果队列持续满、完成数增长很慢，再结合任务耗时、数据库连接等待、远程调用超时和 CPU 使用率，才能判断线程在算、在等，还是遇到锁竞争。单纯扩池可能增加吞吐，也可能只是让更多线程一起等待，把下游压得更慢。

如果慢导出挤占了需要及时响应的通知，可以给两类任务分池，这叫资源隔离。但分池后总线程数和下游连接仍然共享预算，不能每种任务都随意开一大池。参数外置见 [配置与环境管理](/back-end/frontend-backend-guide/25-config-and-env)；外置并不意味着运行中会自动生效，还需要相应的动态更新机制。

日常将这些指标接入 [可观测性](/back-end/frontend-backend-guide/30-observability)；确实需要查看线程在等什么，再按 [排查实战手册](/back-end/frontend-backend-guide/28-troubleshooting-playbook) 和 [诊断工具箱](/back-end/frontend-backend-guide/29-diagnostic-toolbox) 的范围操作。

线程池管住了任务数量，却没有保护任务之间共享的字段。遇到多个任务更新同一份数据，再看 [线程安全与锁](/back-end/frontend-backend-guide/16-thread-safety-locks)。

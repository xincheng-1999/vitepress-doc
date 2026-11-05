# 从 JS 单线程到 Java 多线程

::: info 按需阅读
这一篇解释：两个请求各自把计数加一，为什么可能只多了一次。示例是独立的小程序，不需要另找微服务工程。第一次学习仍可沿 [五阶段主线](./index) 继续。
:::

任务系统想在内存里统计完成次数。现在计数是 `5`，两个请求各增加一次，应该得到 `7`。但下面这种写法，在多个线程调用时可能只得到 `6`：

```java
public void addOne() {
    int current = count;
    count = current + 1;
}
```

原因不在加法，而在**两个调用可以交错执行，却读写同一个字段**。先把这个过程看清楚，再看线程是什么。

## 1. 同一段代码，可能正在处理不同请求

在常见的同步 Spring MVC 应用中，Tomcat 使用工作线程处理请求。请求 A 进入一个 Service 方法时，请求 B 也可能由另一个线程进入这个方法。若 Service 是默认的单例 Bean，两个调用访问的是同一个实例。

**线程**可以先理解为进程里的一条执行路径。同一个 Java 进程中有多条线程，各自执行方法，但可能拿到同一个对象的引用。它们访问对象的同一个可变字段时，就需要协调。

计数丢失可以这样发生：

```text
时间    线程 A                  线程 B
 1      读到 count = 5
 2                              读到 count = 5
 3      算出 6，写回 count
 4                              算出 6，写回 count

两个调用结束，count 只有 6，而不是 7。
```

“读出、加一、写回”合起来不是一个不可分割的操作。即使缩成 `count++`，也不会自动获得这样的保证。后写回的值覆盖了前一次更新，这叫**丢失更新**。

这类结果依赖执行顺序的问题叫**竞态条件**。它不要求两个线程在物理上同一时刻执行：即使只有一个 CPU 核，线程切换造成的交错也可能让程序出错。多核上同时执行多段代码叫**并行**；多个任务在一段时间内交错推进叫**并发**。Java 多线程可能并行，但不能把两者画等号。

## 2. 跑一个最小例子

下面保留了两步读写，方便对应刚才的过程。把完整代码保存为 `RaceDemo.java`，在自己的本地临时目录用 JDK 17 运行即可，无需 Spring：

```java
public class RaceDemo {
    static class Counter {
        private int count = 0;

        public void addOne() {
            int current = count;
            count = current + 1;
        }

        public int getCount() {
            return count;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();
        Runnable task = () -> {
            for (int i = 0; i < 100_000; i++) {
                counter.addOne();
            }
        };

        Thread t1 = new Thread(task, "worker-1");
        Thread t2 = new Thread(task, "worker-2");
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println("期望值: 200000, 实际值: " + counter.getCount());
    }
}
```

```bash
java RaceDemo.java
```

`Runnable` 只描述要做的工作；`Thread` 负责运行它。这里两个线程共用 `counter`，各调用十万次 `addOne()`。

`start()` 会启动新线程；如果误写成 `run()`，只是当前线程普通地执行方法。`join()` 则让 `main` 线程等待对应工作线程结束，再读最终值；它也保证已结束线程中的修改对等待方可见，但**不会把两个工作线程之间的自增变成安全操作**。

一次输出可能是：

```text
期望值: 200000, 实际值: 173852
```

也可能正好是 `200000`，甚至连续几次都正确，结果取决于运行时的调度和优化。只要一次小于期望值，就观察到了丢失更新；一直正确也不能证明代码安全。增加循环次数可以增加观察机会，但没有必要为了复现而开成千上万个线程。

## 3. 和 JS 的区别，具体在哪里

在不涉及 Worker 共享内存的单一 JS agent 中，下面两个同步调用会依次执行：

```javascript
let count = 0

function addOne() {
  const current = count
  count = current + 1
}

addOne()
addOne()
console.log(count) // 2
```

第一个 `addOne()` 没有让出执行机会，普通回调不会在读与写之间插进来。但这不等于 JS 没有竞态。把一次更新拆到 `await` 两边，就可能出现相似的覆盖：

```javascript
let count = 0

async function addOne() {
  const current = count
  await Promise.resolve()
  count = current + 1
}

await Promise.all([addOne(), addOne()])
console.log(count) // 1：两次调用都在让出前读到了 0
```

这里适合放在支持顶层 `await` 的模块环境中理解。两个调用不需要同时执行，也会因为交错而出错。多个客户端修改同一条数据库记录，同样可能产生竞态。

Web Worker 和 Node 的 `worker_threads` 还能执行独立任务。通常通过消息传递数据，也可以使用 `SharedArrayBuffer` 共享内存；共享时就要使用 `Atomics` 等适当的同步方式。因此，不要记成“前端天然安全，后端才需要协调”，而要看**是否共享状态、一次操作中能在哪里交错**。

## 4. 进程、线程和共享对象的边界

启动一个 Java 应用，通常得到一个 JVM **进程**。不同进程通常有独立的地址空间，不能直接拿另一个进程的 Java 对象引用来使用，需要通过网络、文件等方式交换数据。

同一进程内的线程则共享堆里的对象，各自有自己的调用栈。两个线程的方法局部变量可以分别保存引用，却指向同一个 `Counter`。所以，“局部变量各有一份”并不等于“它们指向的对象各有一份”。

多个服务实例又是另一层问题：实例 A 和实例 B 各自有一个内存计数器，加起来不会自动成为全局统计。真实任务数通常还要考虑持久化、重启和多实例，不能把这个内存演示当成完整统计方案。

## 5. 接下来先解决哪件事

这个例子有两个不同的问题：线程由谁创建和限制；共享计数怎样安全更新。前一个交给线程池，后一个需要减少共享，或使用锁、原子类等机制。**线程池不会自动让业务字段变得安全。**

这里手写 `Thread` 是为了看清执行过程。实际业务中的平台线程需要栈内存和系统资源，数量不能无限增加；栈大小也因平台和配置而异，不是固定每条 1MB。批量任务通常交给线程池复用，思路与 [连接池](/back-end/frontend-backend-guide/13-connection-pools) 中的资源管理相似，但管理的是不同资源。

需要控制任务数量时，看 [线程池与 Executor](/back-end/frontend-backend-guide/15-thread-pools-executor)；想修好刚才的计数器，看 [线程安全与锁](/back-end/frontend-backend-guide/16-thread-safety-locks)。

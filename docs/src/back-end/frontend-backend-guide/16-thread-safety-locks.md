# 线程安全与锁：共享数据怎样改才不会串

::: info 按需阅读
这一篇回答多个线程读写同一份数据时，怎样保持结果正确。代码是局部示例，`RtData` 等业务类型只用于说明，不对应另一套完整工程。并发从哪里来见 [从单线程到多线程](/back-end/frontend-backend-guide/14-single-thread-to-multithread)，任务数量如何限制见 [线程池](/back-end/frontend-backend-guide/15-thread-pools-executor)；第一次学习仍可继续 [五阶段主线](./index)。
:::

用户 A 创建任务，最后却把任务记到了用户 B 名下。先不要急着选锁，看看请求数据是不是放错了位置：

```java
@Service
public class TaskService {
    private String currentUserId;

    public void submit(String userId) {
        this.currentUserId = userId;
        checkQuota();
        createTask(this.currentUserId);
    }
}
```

Spring 的 Service 默认按单例管理：通常是一个容器中这个 Bean 只有一个实例，不是每次请求新建一个。A 写入 `currentUserId` 后，如果 B 写入自己的 ID，A 后面读到的就可能是 B 的值。即使两个线程没有物理并行，交错也足以产生问题。

## 1. 先问：这份数据需要共享吗

当前用户只是这次调用的数据，没有理由放进所有请求共用的字段。改成参数或局部变量即可：

```java
@Service
public class TaskService {
    public void submit(String userId) {
        String currentUserId = userId;
        checkQuota(currentUserId);
        createTask(currentUserId);
    }
}
```

每次方法调用有自己的局部变量，不会被另一次调用的赋值覆盖。这比给整个提交过程加锁更直接，也不会让所有用户排队。

不过，**局部引用独立，不代表引用的对象独立**。两个调用若都拿到同一个可变 `Task`，仍然会修改同一个对象。`final` 字段也只是不允许重新赋值，不会把对象内部变成不可变。注入的 Repository、客户端等依赖是否能安全共享，要看它们自身的实现。

Bean 的作用域和注入方式可回看 [Spring IoC 与 DI](/back-end/frontend-backend-guide/06-spring-boot-ioc-di) 或 [IoC/DI 详解](/back-end/java/07a-spring-ioc-di)。这里先记住：不要让单例 Bean 的成员变量保存“这一次请求是谁、入参是什么”。JS 服务中的全局可变请求状态也有同样风险，不是 Java 独有的问题。

## 2. 确实要共享一个计数，使用原子操作

假设系统需要一个当前进程内的完成次数：

```java
private long totalCount = 0;

public long incr() {
    totalCount++;
    return totalCount;
}
```

`++` 不是不可分割的一步。两个线程都读到 `100`，各自写回 `101`，两次完成只计了一次。这类丢失更新在 [多线程示例](/back-end/frontend-backend-guide/14-single-thread-to-multithread) 中已经演示，不需要再开更多线程来复现。

对于单个计数，可以用 `AtomicLong`：

```java
private final AtomicLong totalCount = new AtomicLong(0);

public long incr() {
    long current = totalCount.incrementAndGet();
    return current;
}
```

`incrementAndGet()` 把加一和取得新值合成原子操作。两个调用都执行成功，就会分别完成一次增加。直接返回这次操作得到的 `current`，也避免了自增后再读一次、读到别人的更新。

它只保护这个原子变量。若规则是“检查剩余名额，再扣减，再加入处理队列”，换成三个原子方法，并不能让整套步骤一起原子执行。这个计数也只存在于当前 JVM，不能直接充当跨实例、可持久保存的任务总数。

::: details 按需看：CAS 和 LongAdder
**CAS（比较并交换）**可以理解为：仅当当前值还等于先前读到的值时才更新，否则重新尝试。原子类可利用底层原子操作完成更新，具体实现不必都想象成同一个 Java 循环。

它不等于“永远比锁快”。竞争激烈时重试也有成本；多个字段共同维持的规则，更不能仅凭每个字段分别原子就保证正确。

`LongAdder` 适合高竞争的统计累加，它把更新分散，`sum()` 在并发更新期间不提供一个原子快照，因此不适合拿它判断精确库存或发放唯一序号。

```java
private final LongAdder hotCounter = new LongAdder();
// 完成一次任务时
hotCounter.increment();
// 读取统计值时
long total = hotCounter.sum();
```
:::

## 3. 一组操作必须一起完成时，用同一把锁

如果两个字段必须满足“完成数 + 剩余数 = 总数”，只保证单个字段的更新还不够。可以让相关读写都经过同一把锁：

```java
public class TaskProgress {
    private final Object lock = new Object();
    private int remaining = 100;
    private int completed = 0;

    public boolean completeOne() {
        synchronized (lock) {
            if (remaining == 0) {
                return false;
            }
            remaining--;
            completed++;
            return true;
        }
    }

    public int[] snapshot() {
        synchronized (lock) {
            return new int[] {remaining, completed};
        }
    }
}
```

`synchronized (lock)` 表示：一个线程持有这把锁期间，其他需要**同一把锁**的线程必须等待。它还建立必要的可见性保证，使后续取得该锁的线程能看到之前释放锁前的修改。

注意 `snapshot()` 也取得了这把锁，否则读取方仍可能看到一次修改的中间状态。锁不会使所有代码都“不能打断”，也不会自动保护绕过锁的访问。相关数据的访问方式必须统一。

锁的范围应覆盖完整业务规则，但不要无故把网络调用等漫长工作放进去。锁太小，规则可能被拆开；锁太大，不相关的任务也得等待。

方法上的 `synchronized` 锁当前实例，`static synchronized` 锁对应的类对象，两者不是同一把锁。不要使用字符串常量或装箱数字等可能被别处复用的对象当锁。同步块无论正常返回还是抛异常，都会自动释放锁；异常基础见 [Java 异常](/back-end/java/05-exception)。

### 如果不想一直等锁

需要尝试加锁、限时等待或响应中断时，可以使用 `ReentrantLock`：

```java
private final ReentrantLock lock = new ReentrantLock();

public boolean updateTask(String taskId) throws InterruptedException {
    if (!lock.tryLock(2, TimeUnit.SECONDS)) {
        return false; // 两秒内没拿到锁，由调用方决定如何反馈
    }
    try {
        updateSharedState(taskId);
        return true;
    } finally {
        lock.unlock();
    }
}
```

只在成功拿锁后进入 `try`，并在 `finally` 释放。`tryLock()` 不带时间时立即返回结果；`lockInterruptibly()` 允许等待过程中响应中断。公平模式能减少插队，但不等于绝对调度顺序，通常也有吞吐代价。没有这些需要时，用 `synchronized` 更容易避免漏解锁。

## 4. 一个开关需要的是可见性，不是自增

再看一个不同需求：工作线程循环处理任务，另一个线程要求它停止：

```java
private volatile boolean running = true;

public void workLoop() {
    while (running) {
        doOneShortTask();
    }
}

public void stop() {
    running = false;
}
```

普通共享字段如果没有合适的同步关系，工作线程不保证能及时观察到修改。`volatile` 为这个字段的读写建立可见性和一定的顺序保证。不要把它理解为“每次强制从物理内存取值”或“禁止所有重排序”。

这个例子适合用它，是因为停止操作只需直接写一个布尔值。若当前任务阻塞在网络读取中，修改开关也不会立刻打断那次读取，仍需要超时或取消机制。

把计数改成 `volatile long count` 则救不了 `count++`：读和写之间，其他线程仍可完成自己的读写。**看见修改与把一组动作合成原子操作，是两件事。**

## 5. 共享 Map，也要看你调用的是一个方法还是一组方法

缓存任务状态时，不能让多个线程随意读写普通 `HashMap`。可以使用 `ConcurrentHashMap`，但它不替你合并任意步骤：

```java
private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

// 有问题：两个线程都可能判断为不存在，再各计算一次
if (!cache.containsKey(key)) {
    cache.put(key, compute(key));
}

// 用容器提供的原子复合操作
String value = cache.computeIfAbsent(key, k -> compute(k));
```

`computeIfAbsent` 把“缺失时计算并放入”作为原子操作处理，但不是“这个 key 在整个进程寿命中永远只计算一次”。值被删除、计算返回 `null` 或抛异常后，后续仍可能重算。计算函数应简短，别在里面长时间调用远端；这个 Map 也不会让存进去的可变对象自动线程安全。

列表如果读很多、修改很少，可以考虑 `CopyOnWriteArrayList`；它通过写时复制让读遍历获得快照，但写频繁或列表很大时复制成本高。只属于一次调用的局部集合，通常继续用 `HashMap`、`ArrayList` 即可。相关基础见 [Java 集合](/back-end/java/04-collections) 和 [Lambda 与 Stream](/back-end/java/04a-lambda-stream)。

## 6. 锁也可能让任务互相等住

线程 A 先锁住任务 1，再等任务 2；线程 B 先锁住任务 2，再等任务 1，就可能形成**死锁**：双方都在等对方释放锁，谁也无法继续。

如果确实要同时锁两个对象，所有路径都应遵守固定顺序，例如始终先锁 ID 小的对象。这个约定要求 ID 稳定且能区分锁对象，同一份数据也必须对应同一把锁。也可用限时加锁，在未取得下一把锁时释放已持有的锁并报告失败；不要拿着第一把锁无限重试。

线上出现接口卡住、CPU 不高时，死锁只是可能原因之一。先看已有指标和日志；确实需要线程快照时，在**有授权的目标环境**用 `jps -l` 找到进程。核对后，在同一终端用 `PID=实际数字` 设置目标进程号，再执行：

```bash
jstack "${PID:?请先设置已核对的目标 JVM 进程号}"
```

这会读取目标 JVM 的线程栈，通常影响较低，但并非零影响；需要匹配的权限和工具环境，不要高频重复抓取。输出可能含业务路径等信息，分享前脱敏。若工具识别到一类 Java 锁死锁，可能出现：

```text
Found one Java-level deadlock:
"worker-1": waiting for a lock held by "worker-2"
"worker-2": waiting for a lock held by "worker-1"
```

这里是缩略示意，关键是找出相互等待的闭环。没有这段提示也不能排除所有业务层等待问题。进一步方法见 [排障实战手册](/back-end/frontend-backend-guide/28-troubleshooting-playbook) 和 [诊断工具箱](/back-end/frontend-backend-guide/29-diagnostic-toolbox)。

## 7. 最后确认：数据是在一个进程内，还是在多个实例之间

`synchronized`、`ReentrantLock` 和原子类只协调当前 JVM 中的访问。任务系统部署三个副本后，每个副本有自己的锁，它们不会互相等待。

但解决跨实例问题也不是直接“一律上 Redis 锁”。如果权威数据在数据库里，唯一约束、条件更新、版本号和正确事务边界，往往能更直接表达“不能重复创建”“仅未完成时允许完成”等规则。需要分布式互斥时，再研究锁的过期、续期、持有者身份和故障边界，见 [Redis 实战](/back-end/frontend-backend-guide/12-redis-in-practice) 与 [Redis 的 Java 实战](/back-end/database/redis/java-redis)。锁本身不保证任务恰好处理一次。

回到开头的问题：先确认状态是否该共享；必须共享时，再看要保证的是单次原子更新、复合规则，还是读写可见性。选对要保护的范围，比背一张“锁性能排行”更重要。若接下来要把工作交给后台并收集结果，可继续看 [异步编程](/back-end/frontend-backend-guide/17-async-programming)，但异步不会免除这些共享数据问题。

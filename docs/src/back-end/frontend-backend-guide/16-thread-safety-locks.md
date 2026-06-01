# 线程安全与锁

> 上一章你学会了用线程池把请求并发地跑起来。但「能并发跑」和「并发跑还不出错」是两码事。
> 这一章专门讲：当多个线程**同时**碰同一份数据时会发生什么乱象，以及五类把它掰回来的手段——分别在什么场景用、用错会怎样。

前端几乎遇不到这些问题，因为浏览器主线程是单线程的：你写 `count++` 永远不会被别人打断。可在后端，Tomcat 默认开 200 个工作线程，它们会**同时**执行你写的同一段代码。这一章的所有坑，根源都是这一句话。

> **前端类比**：你写 React 时从不担心两个事件回调「同时」修改同一个 `useRef`——因为 JS 单线程，回调是排队跑的。后端没有这个保护伞，200 个线程是**物理上同时**在跑。本章可以理解为「失去单线程保护之后，要自己手动把并发修改排回队」。

承接上文：并发是怎么来的看 [从单线程到多线程](/back-end/frontend-backend-guide/14-single-thread-to-multithread)，线程从哪来、池子怎么配看 [线程池与 Executor](/back-end/frontend-backend-guide/15-thread-pools-executor)。

---

## 16.1 竞态条件：`i++` 不是一步完成的

先把问题摆到台面上。`svc-user` 里要统计「今天总共生成了多少张图」，有人很自然地写成这样：

```java
@Service
public class CounterService {
    // ❌ 这个字段被所有请求线程共享
    private long totalCount = 0;

    public RtData<Long> incr() {
        totalCount++;          // 看起来一行，实际三步
        return RtData.ok(totalCount);
    }
}
```

`totalCount++` 在 CPU 眼里不是一个动作，而是三个：

```text
读  →  load totalCount 到寄存器     (假设读到 100)
改  →  寄存器里 +1                  (算出 101)
写  →  把 101 写回 totalCount
```

当线程 A 和线程 B 几乎同时执行时，可能这样交错：

```text
时刻   线程 A                线程 B
 t1   读 totalCount = 100
 t2                          读 totalCount = 100   ← 也读到 100！
 t3   算出 101
 t4                          算出 101
 t5   写回 101
 t6                          写回 101              ← 又写了一遍 101
结果：两次 incr，totalCount 只增加了 1，丢了一次更新
```

这就是**竞态条件（Race Condition）**：结果取决于线程执行的先后顺序，而这个顺序不可控。

### 用一段能跑的代码复现给自己看

口说无凭。下面这段 `main` 方法开 1000 个线程，每个线程对计数器 `+1` 一万次，理论结果应该是 `10,000,000`：

```java
public class RaceDemo {
    private static long totalCount = 0;   // 共享变量

    public static void main(String[] args) throws InterruptedException {
        int threads = 1000, perThread = 10_000;
        CountDownLatch latch = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(50);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                for (int j = 0; j < perThread; j++) {
                    totalCount++;          // ❌ 非原子操作
                }
                latch.countDown();
            });
        }
        latch.await();                     // 等所有线程跑完
        pool.shutdown();
        System.out.println("期望 = " + (threads * perThread));
        System.out.println("实际 = " + totalCount);
    }
}
```

**预期输出**（每次跑结果都不一样，但几乎不可能等于一千万）：

```text
期望 = 10000000
实际 = 9876421
```

**怎么读这段输出**：实际值小于期望值，少掉的那几十万次就是「读-改-写」交错时被覆盖掉的更新。注意三点：① 结果每次都不同——这正是竞态的标志；② 你**本地单线程点测永远复现不出来**，因为只有一个线程在改；③ 把线程数改成 1 就一定正确——并发量越高，丢得越多。

**结论**：只要有「多个线程读写同一份可变数据」，就有竞态。下面五类手段，都是为了消除它。

> **前端类比**：你在前端做「按钮防重复提交」时，用一个 `loading` 标志位拦住第二次点击——这是在单线程里手动串行化。后端的锁本质上是同一件事，但要对抗的是**真正并行**的线程，所以需要语言/JVM 层面的机制，光一个普通布尔变量是拦不住的（后面 `volatile` 一节会讲为什么）。

---

## 16.2 手段一：`synchronized`——最基础的互斥锁

`synchronized` 的语义：同一时刻，只有一个线程能进入被它保护的代码，其他线程在门口排队等。

```java
@Service
public class CounterService {
    private long totalCount = 0;

    // 修正方式 A：加在方法上，锁的是当前对象（this）
    public synchronized RtData<Long> incr() {
        totalCount++;          // 现在「读-改-写」三步不会被打断
        return RtData.ok(totalCount);
    }
}
```

把上一节 demo 里的 `totalCount++` 换成走这个 `synchronized` 方法，结果就**稳定等于一千万**了。

### 锁方法 vs 锁代码块

锁整个方法简单，但**锁的粒度太大**——方法里那些跟共享数据无关的逻辑也被串行化了，白白降低并发。更推荐只锁住真正需要保护的那几行：

```java
@Service
public class CounterService {
    private long totalCount = 0;
    // 专门用来当锁的对象，private final 避免被别人拿去乱锁
    private final Object lock = new Object();

    public RtData<Long> incr() {
        // 这些是无关的耗时操作，不该被锁住
        doSomeHeavyValidation();

        long current;
        synchronized (lock) {        // 修正方式 B：只锁关键区
            totalCount++;
            current = totalCount;
        }
        return RtData.ok(current);
    }
}
```

**何时用 `synchronized`**：逻辑简单、锁的代码块短、不需要「超时/可中断/尝试加锁」这些高级控制时，首选它。它是 JVM 内置的，写法最省事，JDK 优化得也好。

> **前端类比**：`synchronized` 像 `async` 函数里用一个共享的 `Promise` 串行化——`await lastTask; lastTask = doThing()`，强制后来者排在前一个后面。区别是 JS 那是协作式排队（你主动 await），`synchronized` 是 JVM 强制的，谁都绕不过去。

> 注意几个坑：① **别锁字符串常量或 `Integer` 等可能被复用的对象**，容易和不相关的代码撞同一把锁；② `synchronized` 是**可重入**的——同一个线程已经拿到锁后，再次进入同一把锁的代码不会自己锁死自己；③ 方法上的 `static synchronized` 锁的是「类对象」，和实例方法的 `synchronized(this)` 不是同一把锁，别混。异常处理基础可看 [Java 异常](/back-end/java/05-exception)。

---

## 16.3 手段二：`ReentrantLock`——能控制更多细节的锁

`synchronized` 的短板是「太死板」：拿不到锁就只能死等，不能设超时、不能响应中断、不能公平排队。`ReentrantLock` 把这些都补上了，代价是要手动 `lock()` / `unlock()`。

```java
@Service
public class TaskLockService {
    private final ReentrantLock lock = new ReentrantLock();

    public RtData<Void> updateTask(String taskId) {
        lock.lock();                 // 拿锁
        try {
            // ... 操作共享数据 ...
            return RtData.ok();
        } finally {
            lock.unlock();           // ⚠️ 必须放 finally，否则异常时锁永远不释放 → 死锁
        }
    }
}
```

它最有用的三个能力：

```java
// 1. tryLock：拿不到锁就立刻返回 false，不傻等——适合「抢不到就放弃/走兜底」
if (lock.tryLock()) {
    try { doWork(); } finally { lock.unlock(); }
} else {
    return RtData.fail("任务正在处理中，请勿重复提交");
}

// 2. tryLock 带超时：最多等 2 秒，等不到就放弃，避免线程被无限期挂住
if (lock.tryLock(2, TimeUnit.SECONDS)) {
    try { doWork(); } finally { lock.unlock(); }
} else {
    return RtData.fail("系统繁忙，请稍后重试");
}

// 3. lockInterruptibly：等锁的过程中可以被中断（响应 Thread.interrupt()）
lock.lockInterruptibly();
```

构造时传 `true` 还能开启**公平锁**（先到先得，避免某些线程一直抢不到），但公平锁吞吐更低，非必要不用：

```java
private final ReentrantLock fairLock = new ReentrantLock(true);  // 公平模式
```

**何时用 `ReentrantLock`**：需要 `tryLock`（拿不到就走兜底，比如上面的「请勿重复提交」）、需要带超时等待、需要可中断、或需要公平排队时。其余情况优先 `synchronized`，因为后者不会忘记 `unlock`。

> **前端类比**：`tryLock()` 就是你那个 `if (loading) return;` 的防重复提交——抢不到就直接拒绝，不排队。`tryLock(2s)` 则像给一个互斥操作加 `Promise.race([task, timeout(2000)])`，到点不等了。

> **单机锁 ≠ 分布式锁**：`synchronized` 和 `ReentrantLock` 只在**当前这一个 JVM 进程内**有效。可你的 `svc-canvas` 在 K8s 里有 3 个副本（3 个进程），进程 A 的锁拦不住进程 B。要跨副本互斥（比如「同一个任务全局只能被处理一次」），得用 **Redis 分布式锁**——见 [Redis 实战](/back-end/frontend-backend-guide/12-redis-in-practice) 和 [Redis 的 Java 实战](/back-end/database/redis/java-redis)。

---

## 16.4 手段三：原子类——CAS 无锁，专治计数

回到「统计总数」这个场景。加锁能解决，但锁有开销（线程要排队、要切换）。对于「就是个计数器」这种简单场景，有更轻量的武器：**原子类**，比如 `AtomicLong` / `AtomicInteger`。

```java
@Service
public class CounterService {
    // ✅ 用原子类，天生线程安全，不用加锁
    private final AtomicLong totalCount = new AtomicLong(0);

    public RtData<Long> incr() {
        long current = totalCount.incrementAndGet();   // 原子地 +1 并返回新值
        return RtData.ok(current);
    }
}
```

它凭什么不加锁就线程安全？靠 **CAS（Compare-And-Swap，比较并交换）**。`incrementAndGet` 内部大致是这样一个循环：

```text
1. 读出当前值 cur（比如 100）
2. 算出期望写入的新值 next = cur + 1（101）
3. CAS：「如果内存里现在还是 100，就把它换成 101」
   - 成功 → 返回 101，结束
   - 失败（说明这期间被别人改了）→ 回到第 1 步，拿新值重试
```

这是一条 CPU 级别的原子指令，整个「比较+交换」不可被打断。它**没有加锁、没有阻塞**——抢输的线程不是去排队睡觉，而是立刻拿新值再试一次（叫「自旋」）。

**何时用原子类**：单个数值/引用的并发更新——计数器、累加器、状态标志位、自增 ID。比锁轻得多。

**什么时候它不够用**：当你要原子地更新**多个**变量、或更新逻辑很复杂（不只是 +1）时，CAS 就力不从心了，得回去用锁。另外在**极高竞争**下自旋会浪费 CPU，这时可以换 `LongAdder`（分段累加，高并发计数更快，代价是读取瞬时值不如 `AtomicLong` 精确）。

```java
private final LongAdder hotCounter = new LongAdder();  // 超高频计数场景更优
hotCounter.increment();
long total = hotCounter.sum();
```

> **前端类比**：CAS 的思想就是你做乐观更新时用的**乐观锁**——提交时带上「我读到的版本号」，服务端发现版本变了（别人先改了）就让你重做。`while (CAS 失败) retry` 和「版本冲突 → 重新拉取 → 重试提交」是同一个套路：不上锁，赌冲突少，冲突了就重来。

---

## 16.5 手段四：`volatile`——保证可见性，但不保证原子性

这是最容易被误用的关键字，务必把边界搞清楚。

先理解一个反直觉的现象：**一个线程改了共享变量，另一个线程可能一直看不到**。因为每个线程可能把变量缓存在自己的工作内存（CPU 缓存）里，不一定及时同步回主内存。

```java
public class ShutdownDemo {
    private boolean running = true;     // ❌ 没加 volatile

    public void start() {
        new Thread(() -> {
            while (running) {           // 工作线程可能一直读自己缓存里的 true
                doWork();
            }
        }).start();
    }

    public void stop() {
        running = false;                // 主线程改成 false，但工作线程可能永远看不到
    }
}
```

上面 `stop()` 把 `running` 设为 `false` 后，那个 `while` 循环**有可能永远停不下来**——因为工作线程读的是自己缓存里的旧值。加上 `volatile` 就能解决：

```java
private volatile boolean running = true;   // ✅ 保证每次都从主内存读最新值
```

`volatile` 干两件事：① **可见性**——一个线程的写，对其他线程立即可见；② **禁止指令重排序**。

但请记牢这条红线——**`volatile` 不保证原子性**：

```java
private volatile long count = 0;
// ❌ 错误！volatile 救不了 count++
public void incr() {
    count++;     // 仍然是「读-改-写」三步，volatile 只保证每步读到的是新值，
                 // 不保证三步合起来不被打断。多线程下照样丢更新
}
```

很多人以为「加了 volatile 就线程安全了」，这是头号误解。**`volatile` 只解决「看见」，不解决「打断」**。`count++` 这种复合操作，该用 `AtomicLong` 还得用 `AtomicLong`。

**何时用 `volatile`**：变量被一个线程写、其他线程只读（或读多写少且写操作本身是原子的——比如直接赋一个布尔/引用值），典型就是上面这种「停机标志位」「配置热更新开关」。

> **前端类比**：可见性问题在前端没有直接对应物（单线程不存在「别的线程看不到我的修改」）。硬要类比，有点像 Web Worker 之间不共享内存、必须 `postMessage` 才能同步——`volatile` 就是强制「每次都走主内存这条公共通道」，让所有线程看到同一份值。

> 一句话记忆：**`volatile` 管「可见」，`synchronized`/锁/原子类管「互斥/原子」。`count++` 要的是后者。**

---

## 16.6 手段五：并发容器——别用普通集合扛并发

普通的 `HashMap` / `ArrayList` 在并发读写下不仅会丢数据，还可能直接抛 `ConcurrentModificationException`，甚至（老版本 JDK）`HashMap` 扩容时形成环形链表导致 CPU 100%。所以多线程共享的集合，**换成并发容器**。

```java
// ❌ 多线程共享一个普通 HashMap
private final Map<String, String> cache = new HashMap<>();

// ✅ 换成并发安全的 ConcurrentHashMap
private final Map<String, String> cache = new ConcurrentHashMap<>();
```

`ConcurrentHashMap` 内部用分段/CAS 的方式做到「多个线程操作不同 key 时基本不互相阻塞」，比「用 `synchronized` 包一个普通 Map」并发高得多。

但要注意：**单个方法原子 ≠ 组合操作原子**。下面这种「先查再放」的复合操作仍然有竞态：

```java
// ❌ get 和 put 各自原子，但「中间这段」会被别的线程插进来
if (!cache.containsKey(key)) {     // 线程 A、B 可能同时判断为 true
    cache.put(key, compute(key));  // 于是 compute 被执行了两次
}

// ✅ 用 ConcurrentHashMap 自带的原子复合方法
cache.computeIfAbsent(key, k -> compute(key));   // 整体原子，compute 只执行一次
```

另一个常用的是 `CopyOnWriteArrayList`：写的时候复制一份新数组，读不加锁。适合**读极多、写极少**的场景——比如服务里一份「监听器列表」「白名单」，启动时写好，运行期几乎只读。

```java
// 读多写极少：监听器/回调注册表
private final List<TaskListener> listeners = new CopyOnWriteArrayList<>();
```

但如果写很频繁，`CopyOnWriteArrayList` 每次写都复制整个数组，开销巨大，**千万别拿它当普通可变列表用**。

**何时用哪个**：

| 需求 | 用什么 |
| --- | --- |
| 多线程读写一个 Map | `ConcurrentHashMap` |
| 「不存在才放入」「存在才更新」等原子复合 | `ConcurrentHashMap` 的 `computeIfAbsent` / `merge` 等 |
| 读极多、写极少的列表（监听器、配置快照） | `CopyOnWriteArrayList` |
| 普通的、不跨线程共享的局部集合 | 照常用 `HashMap`/`ArrayList`，别为「安全」白白付出并发容器的开销 |

> **前端类比**：JS 里 `Map`/数组从不会有这问题（单线程）。`computeIfAbsent` 类似你做「缓存请求」时的去重：`if (!promiseCache[key]) promiseCache[key] = fetch(...)`——保证同一个 key 只发一次请求。区别是后端得靠容器自带的原子方法来保证这个「只一次」，普通 `if` 判断在并发下是会漏的。

集合相关基础可回顾 [Java 集合](/back-end/java/04-collections) 与 [Lambda 与 Stream](/back-end/java/04a-lambda-stream)。

---

## 16.7 头号实战陷阱：Spring 单例 Bean 里的可变成员变量

这是前端转后端**最高频、最隐蔽**的踩坑点，单独拎出来讲。

关键事实：**Spring 的 `@Service` / `@Component` / `@RestController` 默认都是单例（singleton）**——整个应用只有这一个实例。而 Tomcat 的 200 个线程**共享这同一个实例**。所以你在里面定义的任何可变成员变量，都是被所有请求线程共享的。

下面这段 `svc-canvas` 的代码，用前端思维写出来毫无破绽，上线却会串数据：

```java
@Service
public class TaskService {
    // ❌ 致命错误：把「请求级状态」存成了成员变量
    private String currentUserId;
    private GenImageReq currentReq;

    public RtData<String> submit(GenImageReq req) {
        this.currentUserId = req.getUserId();   // 线程 A 刚写进去
        this.currentReq = req;

        // ... 中间有耗时操作（查配额、抢锁）...
        // 此刻线程 B 进来，把 currentUserId 改成了 B 的用户！

        deductQuota(this.currentUserId);         // 线程 A 可能扣到了 B 的配额
        return RtData.ok(genTask(this.currentReq));
    }
}
```

**症状**：偶发地「用户 A 提交任务，配额却扣在了用户 B 头上」「返回的 taskId 是别人的」。这种 bug **本地点测一定复现不出来**（只有一个人一个线程），一上量就零星出现，极难定位。根因就是：成员变量被多个请求线程并发覆盖。

**修正：请求级状态只用局部变量 / 方法入参传递，不要放成员变量。**

```java
@Service
public class TaskService {
    // ✅ 成员变量只放「无状态」的依赖（这些是线程安全的，且本就该共享）
    private final UserClient userClient;
    private final TaskRepository taskRepository;

    public TaskService(UserClient userClient, TaskRepository taskRepository) {
        this.userClient = userClient;
        this.taskRepository = taskRepository;
    }

    public RtData<String> submit(GenImageReq req) {
        // ✅ 请求级数据全程用局部变量，每个线程在自己的栈上，互不干扰
        String userId = req.getUserId();
        deductQuota(userId);
        String taskId = genTask(req);
        return RtData.ok(taskId);
    }
}
```

记住这条规矩：**Service/Controller 里的成员变量，只允许放"无状态的依赖"（注入进来的其他 Bean、配置常量），绝不能放"这一次请求的数据"。** 局部变量天生线程隔离（每个线程有自己的调用栈），是最简单、最该优先的「线程安全」方案——能用局部变量就别上锁。

> **前端类比**：单例 Bean 就像一个**所有用户共享的全局模块**。你绝不会在一个被全 App 共享的 JS 模块顶部写 `let currentUser`、然后在处理函数里改它——因为下一个调用就把它覆盖了。后端的 200 个线程共享单例，是同样的道理，只不过覆盖是**并发**发生的，更难发现。

> 想深入理解 Bean 为什么是单例、依赖注入怎么回事，看 [Spring IoC 与 DI](/back-end/frontend-backend-guide/06-spring-boot-ioc-di) 和 [Spring IoC/DI 详解](/back-end/java/07a-spring-ioc-di)。

---

## 16.8 死锁：两个线程互相等对方手里的锁

加锁能解决竞态，但锁用不好会带来新问题——**死锁**：两个线程各拿着一把锁，又都在等对方手里的那把，谁也不让，结果**永远卡住**。

经典成因是「两个线程以**相反的顺序**获取两把锁」。设想 `svc-user` 里一个转账配额的操作，要同时锁住「转出账户」和「转入账户」：

```java
public void transfer(Account from, Account to, int amount) {
    synchronized (from) {              // 先锁 from
        synchronized (to) {            // 再锁 to
            from.deduct(amount);
            to.add(amount);
        }
    }
}
```

当线程 A 执行 `transfer(账户1, 账户2)`、线程 B 同时执行 `transfer(账户2, 账户1)`：

```text
时刻   线程 A                     线程 B
 t1   锁住 账户1 ✔
 t2                              锁住 账户2 ✔
 t3   想锁 账户2 → 被 B 占着，等待
 t4                              想锁 账户1 → 被 A 占着，等待
      ──── 两个线程互相等，永久阻塞 ────
```

**症状**：相关接口全部卡死、没有任何报错、CPU 也不高（线程在等待，不消耗 CPU），日志里安静得可怕。这是最折磨人的故障之一，因为它「不报错只卡住」。

### 三种避免死锁的做法

**① 固定加锁顺序**（最常用、最有效）。让所有线程**永远按同一个顺序**拿锁，比如按账户 ID 从小到大：

```java
public void transfer(Account from, Account to, int amount) {
    // 不管转账方向如何，都按 id 小的先锁，破坏「相反顺序」这个死锁条件
    Account first  = from.getId() < to.getId() ? from : to;
    Account second = from.getId() < to.getId() ? to : from;
    synchronized (first) {
        synchronized (second) {
            from.deduct(amount);
            to.add(amount);
        }
    }
}
```

**② 用 `tryLock` 带超时**：拿不到第二把锁就放弃并释放第一把，过会儿重试，绝不无限期等：

```java
if (lockA.tryLock(1, TimeUnit.SECONDS)) {
    try {
        if (lockB.tryLock(1, TimeUnit.SECONDS)) {
            try { doTransfer(); } finally { lockB.unlock(); }
        } else {
            // 拿不到 B，放弃，稍后重试，不会和别人卡死
        }
    } finally { lockA.unlock(); }
}
```

**③ 尽量缩小锁范围、减少嵌套锁**：能不嵌套两把锁就别嵌套，这是从源头消除死锁。

### 线上怀疑死锁，怎么确认？

死锁不报错，得主动「拍线程快照」看。用 `jstack`（或 `jcmd`）把进程所有线程的栈打出来，它会**直接帮你标出死锁**：

```bash
# 1. 找到 Java 进程的 PID
jps -l
# 2. 打印该进程的线程栈
jstack <PID>
```

**预期输出**（节选关键部分）：

```text
Found one Java-level deadlock:
=============================
"http-nio-8080-exec-7":
  waiting to lock monitor 0x00007f... (object 0x000000076ab..., a com.demo.Account),
  which is held by "http-nio-8080-exec-3"
"http-nio-8080-exec-3":
  waiting to lock monitor 0x00007f... (object 0x000000076ac..., a com.demo.Account),
  which is held by "http-nio-8080-exec-7"

Found 1 deadlock.
```

**怎么读这段输出**：`jstack` 已经直接告诉你 `Found one Java-level deadlock`，并列出了「线程 7 等线程 3 持有的锁，线程 3 又等线程 7 持有的锁」——这就是相互等待的闭环。顺着它给的线程名和锁对象，回代码里就能定位到那两段 `synchronized`。

**结论**：死锁靠「肉眼看代码」很难发现，靠 `jstack` 一抓一个准。完整的排查步骤和 `jstack` 用法，见 [排障实战手册](/back-end/frontend-backend-guide/28-troubleshooting-playbook) 和 [诊断工具箱](/back-end/frontend-backend-guide/29-diagnostic-toolbox)。

> **前端类比**：前端单线程几乎不会真正死锁，最接近的是「两个 `await` 互相等对方的 Promise resolve」——A 等 B 完成、B 又等 A 完成，结果两个都卡住。死锁就是这个现象在多线程世界里的常态化版本，而且更隐蔽（不报错）。

---

## 16.9 决策速查：这场景该用哪个？

| 场景 | 首选方案 | 为什么 |
| --- | --- | --- |
| 请求级数据（当前用户、当前入参） | **局部变量 / 入参传递** | 天生线程隔离，零成本，能用就别上锁 |
| 单个数值计数、累加、状态位 | **`AtomicLong` / `AtomicInteger`** | CAS 无锁，比加锁轻 |
| 超高频计数 | **`LongAdder`** | 分段累加，极高并发下比 `AtomicLong` 更快 |
| 「停机/热更新」开关：一写多读 | **`volatile`** | 只需可见性，开销最小 |
| 多线程读写 Map | **`ConcurrentHashMap`** | 并发安全且高吞吐 |
| 读极多写极少的列表 | **`CopyOnWriteArrayList`** | 读不加锁 |
| 一小段复合逻辑要互斥，逻辑简单 | **`synchronized` 代码块** | 写法最省事，不会忘记解锁 |
| 需要 tryLock/超时/可中断/公平 | **`ReentrantLock`** | 控制更细，记得 `finally` 里 `unlock` |
| 跨进程/跨副本互斥 | **Redis 分布式锁** | 单机锁拦不住多个 Pod |

一句心法：**优先「不共享」（局部变量）> 其次「无锁」（原子类/并发容器）> 最后才「加锁」。锁是有成本的，能不上就不上。**

---

## 小结

- **竞态条件**的根源是「读-改-写」这类复合操作不是原子的，多线程交错执行会丢更新。本地单线程永远复现不出来，一上并发就出——这是前端转后端的第一个认知坎。
- 五类手段各有边界：`synchronized`（简单互斥）、`ReentrantLock`（tryLock/超时/可中断/公平）、原子类（CAS 无锁，专治计数）、`volatile`（**只管可见性，不管原子性**，`count++` 救不了它）、并发容器（`ConcurrentHashMap` / `CopyOnWriteArrayList`，注意复合操作要用自带原子方法）。
- 头号陷阱：**Spring 单例 Bean 被所有请求线程共享**，绝不能在 Service/Controller 里用可变成员变量存请求级状态——请求数据一律走局部变量或入参。
- **死锁**多由「相反顺序拿两把锁」引起，表现为卡死但不报错、CPU 不高；用「固定加锁顺序」预防，用 `jstack` 一抓一个准。
- 优先级永远是：**不共享 > 无锁 > 加锁**。单机锁不跨进程，跨副本互斥要上 Redis 分布式锁。

### 自测

1. 有人说「我给计数器加了 `volatile`，现在 `count++` 就线程安全了」。这个说法对吗？请说明 `volatile` 到底保证什么、不保证什么，以及这个计数器正确的写法是什么。
2. `svc-canvas` 的 `TaskService`（`@Service` 单例）里有一个成员变量 `private String currentUserId`，在 `submit` 方法开头赋值、后面用它扣配额。线上偶发「A 用户的任务把配额扣到了 B 头上」。请解释根因，并给出修正方案；再说明为什么这个 bug 本地点测发现不了。
3. 两个线程分别调用 `transfer(账户1, 账户2)` 和 `transfer(账户2, 账户1)`，相关接口全部卡死、不报错、CPU 也不高。这是什么问题？你会用什么命令确认它、用什么办法从代码层面根治它？

### 下一章

锁和原子类解决的是「共享数据」的安全；但很多场景我们其实想要的是「别让线程死等下游」——把耗时操作丢到后台、用回调或 Future 拿结果。下一章进入 [异步编程](/back-end/frontend-backend-guide/17-async-programming)，看 `CompletableFuture` 如何对应你熟悉的 `Promise`。

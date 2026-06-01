# OOM 与内存泄漏

> 上一章讲了 GC 怎么自动回收内存，于是你可能会想：「既然有 GC，那 Java 是不是就不会内存泄漏、不会 OOM 了？」
> 恰恰相反。GC 只回收**没人引用**的对象；只要还有一根引用链拽着它，GC 就动不了它。内存泄漏在 Java 里换了个形式继续存在，而且因为「看不见」，比手动管理内存的语言更难查。
> 这一章我们把线上最常见的 5 类 OOM 逐个拆开（报错关键字 + 典型原因），再讲清「Java 有 GC 为什么还会泄漏」，最后给一套能在生产环境真正用起来的定位流程：自动抓 heap dump → 用 MAT 找占用大户 → 用 `jmap` 看实时分布。

承接上一章 [垃圾回收 GC](/back-end/frontend-backend-guide/19-garbage-collection)；本章的内存区域名词（堆 Heap、Metaspace、栈、Direct Memory）若不熟，回看 [JVM 内存模型](/back-end/frontend-backend-guide/18-jvm-memory-model)。

> 💡 **前端类比**：你在浏览器里也遇过内存泄漏——组件卸载了但 `addEventListener` 没 `removeEventListener`，或者 `setInterval` 没 `clearInterval`，闭包一直拽着 DOM 节点不放，Chrome DevTools 的 Memory 面板里堆越涨越高。Java 的泄漏本质一模一样：**该断的引用没断**。区别只是后端进程要连续跑几个月不重启，泄漏会日积月累，最终把整个服务拖垮。

---

## 20.1 OOM 不是一种错误，是一类错误

很多人一看到 `OutOfMemoryError` 就慌，其实它后面那半句话才是关键——它告诉你**是哪块内存不够了**，而不同的区域对应完全不同的原因和解法。

```text
java.lang.OutOfMemoryError: Java heap space
                           └────────┬───────┘
                            这半句才是诊断的起点
```

先记住一张总表，后面逐个展开：

| 报错关键字 | 哪块内存满了 | 最常见的真实原因 |
| --- | --- | --- |
| `Java heap space` | 堆 Heap | 堆配小了 / 一次加载太多数据 / 内存泄漏 |
| `Metaspace` | 元空间（类元数据） | 类加载过多 / 运行时动态生成类（CGLIB、热部署） |
| `GC overhead limit exceeded` | 堆（间接） | GC 一直在跑但几乎回收不动，接近泄漏的前兆 |
| `unable to create new native thread` | 线程栈 / 系统资源 | 线程创建失控，没用线程池 |
| `Direct buffer memory` | 堆外直接内存 | NIO/Netty 的 DirectByteBuffer 没释放 |

> ⚠️ 注意 `OutOfMemoryError` 是 `Error` 不是 `Exception`，**不要去 `catch` 它然后假装没事**。它代表 JVM 已经处于不健康状态，继续运行只会产出更多脏数据。正确做法是让进程崩溃、被 K8s 重启，同时把现场（heap dump）保留下来供事后分析。

---

## 20.2 五类 OOM 逐个拆

### 1. `Java heap space`——最常见的一种

```text
java.lang.OutOfMemoryError: Java heap space
	at java.util.Arrays.copyOf(Arrays.java:3332)
	at com.example.svcai.service.ImageService.loadAll(ImageService.java:88)
```

**三种典型成因**：

1. **堆本来就配小了**：默认 `-Xmx` 不够你的业务量。这不是泄漏，是配置问题。
2. **一次性加载太多数据**：典型如「查全表」「一次读一个超大文件进内存」。
3. **内存泄漏**：对象只增不减，迟早撑爆。

`svc-canvas` 里一个真实踩坑——导出用户全部历史画作时，一把捞进内存：

```java
// ❌ 危险：用户有 5 万张图，每张元数据 + 缩略图字节，一次性全进堆
public RtData<byte[]> exportAll(String uid) {
    List<Canvas> all = canvasRepository.findByUid(uid); // 5 万条全查出来
    ByteArrayOutputStream zip = new ByteArrayOutputStream();
    for (Canvas c : all) {
        zip.write(ossClient.download(c.getThumbUrl())); // 每张缩略图字节全堆在内存
    }
    return RtData.ok(zip.toByteArray()); // toByteArray 又拷贝一份，瞬间翻倍
}
```

**修正方向**：分页/流式处理，别让单次请求的内存占用随数据量线性增长。

```java
// ✅ 流式：边读边写出去，内存占用恒定，不随数据量增长
public void exportAll(String uid, OutputStream out) {
    try (Stream<Canvas> stream = canvasRepository.streamByUid(uid)) { // 游标，不一次性加载
        stream.forEach(c -> {
            try (InputStream in = ossClient.openStream(c.getThumbUrl())) {
                in.transferTo(out); // 8KB 缓冲区滚动拷贝
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
```

> 💡 **前端类比**：等价于你不再 `const all = await fetchAll()` 一次性把所有数据塞进内存，而是改用流式 / 分页 / `ReadableStream`，边到边处理边丢弃。前端一般没这么严重是因为单用户数据量小、Tab 关了就清空；后端是 200 个线程同时这么干，且进程不重启。

---

### 2. `Metaspace`——类元数据撑爆

```text
java.lang.OutOfMemoryError: Metaspace
```

Metaspace 存的是**类的元数据**（类结构、方法、常量池），不是业务对象。它满了通常意味着「加载/生成的类太多」。

**典型原因**：

- **运行时动态生成类**：CGLIB 动态代理、字节码增强、大量 lambda、热部署框架。每生成一个新类就占一格 Metaspace，只增不减。
- **类加载器泄漏**：自定义 `ClassLoader` 没被回收，它加载的类也跟着无法卸载。Web 容器反复热部署、某些插件机制是重灾区。
- **没设上限**：Metaspace 默认可以一直涨到吃光机器物理内存。

```java
// ❌ 反面教材：每次请求都用 ByteBuddy/CGLIB 动态造一个新类
public Object handle(String type) {
    Class<?> dynamic = new ByteBuddy()
        .subclass(Object.class)
        .name("Generated$" + System.nanoTime()) // 名字每次都不同 = 每次都是新类
        .make()
        .load(getClass().getClassLoader())
        .getLoaded();
    // ... 这些类永远不会被卸载，Metaspace 一路涨到 OOM
    return dynamic;
}
```

**排查信号**：生产建议显式设上限 `-XX:MaxMetaspaceSize=256m`，这样它撑爆时会**明确报 Metaspace OOM**，而不是悄悄吃光物理内存、导致整机变慢、更难定位。

---

### 3. `GC overhead limit exceeded`——GC 在空转

```text
java.lang.OutOfMemoryError: GC overhead limit exceeded
```

这条很有迷惑性：堆其实还没 100% 满，但 JVM 主动抛了 OOM。它的判定规则是：**超过 98% 的 CPU 时间花在 GC 上，却只回收回来不到 2% 的堆**。

翻译成人话：垃圾回收器累死累活地跑，但几乎啥也回收不动——因为活着的对象太多了（接近泄漏）。这通常是 `Java heap space` OOM 的前兆，往往伴随服务**响应变得极慢**（CPU 全被 GC 占了）。

> 💡 **前端类比**：像浏览器主线程被一段死循环占满，页面卡到点不动。这里是 JVM 把 CPU 全用来做无效 GC，业务线程几乎拿不到时间片。看到这个报错，**不要简单地调大 `-Xmx` 蒙混过去**，多半是有泄漏在背后。

---

### 4. `unable to create new native thread`——线程造太多

```text
java.lang.OutOfMemoryError: unable to create new native thread
```

注意：这条**几乎和堆无关**。每个 Java 线程都要在堆外申请一块栈空间（默认约 1MB），并向操作系统申请一个原生线程。线程开太多，要么撞上 OS 的进程线程数上限（`ulimit -u`），要么把内存吃光，于是建不出新线程。

`svc-ai` 里最经典的坑——每来一个请求 `new Thread`，从不复用：

```java
// ❌ 每个请求都起一条新线程跑生图，高并发下线程数失控
@PostMapping("/generate")
public RtData<String> generate(@RequestBody GenReq req) {
    new Thread(() -> aiClient.draw(req)).start(); // 来一个起一个，永不回收
    return RtData.ok("submitted");
}
```

**修正方向**：用线程池，把并发线程数控制在固定上限内（细节见 [线程池与 Executor](/back-end/frontend-backend-guide/15-thread-pools-executor)）。

```java
// ✅ 用有界线程池，最多就这么多线程，超出的任务排队或拒绝
private final ExecutorService pool = new ThreadPoolExecutor(
    8, 16, 60L, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(200),                 // 有界队列
    new ThreadPoolExecutor.CallerRunsPolicy());     // 满了让调用线程自己跑，形成反压

@PostMapping("/generate")
public RtData<String> generate(@RequestBody GenReq req) {
    pool.submit(() -> aiClient.draw(req));
    return RtData.ok("submitted");
}
```

> **排查命令**：先看线程总数。Linux 上 `ps -o nlwp <pid>`，或 `cat /proc/<pid>/status | grep Threads`。如果是几千上万，基本就是没用线程池。

---

### 5. `Direct buffer memory`——堆外内存

```text
java.lang.OutOfMemoryError: Direct buffer memory
	at java.base/java.nio.Bits.reserveMemory(Bits.java:175)
```

这是**堆外（off-heap）直接内存**爆了，受 `-XX:MaxDirectMemorySize` 限制。NIO、Netty、某些 OSS/HTTP 客户端会用 `DirectByteBuffer` 做零拷贝。它的回收依赖一个特殊机制（`Cleaner`，挂在 GC 上），如果你只盯着堆，会发现「堆明明很空，却 OOM 了」——因为爆的根本不是堆。

`svc-oss` 大文件分片上传时，每片都 `allocateDirect` 又不复用，就可能撞上这条。**修正方向**：复用 buffer、用池化的 `PooledByteBufAllocator`（Netty），或限制并发分片数。

> 这一类是「堆看起来没问题但还是 OOM」的典型，记住堆外内存的存在，排查时多看一眼 `Direct buffer memory` 关键字。

---

## 20.3 Java 有 GC，为什么还会泄漏？

一句话：**GC 只能回收「不再被任何 GC Root 引用」的对象**。所谓内存泄漏，就是某个对象逻辑上你已经不用了，但代码里还有一条引用链拽着它，GC 认为它「活着」，于是永远不回收。它越积越多，最终 OOM。

> 💡 **前端类比**：和 JS 的闭包泄漏完全同构。`element.onclick = () => { /* 引用了一个大对象 */ }`，只要这个 listener 不解绑，那个大对象就一直活着。Java 里把「listener / 静态集合 / ThreadLocal」当成那根没断的引用就行。

下面是后端最高频的 5 种泄漏，每个都给「踩坑 → 修正」。

### 泄漏 1：静态集合 / 缓存只加不删

`static` 字段的生命周期和整个进程一样长，它引用的东西永远不会被 GC。把它当缓存却忘了设上限和过期，就是个无底洞。

```java
// ❌ 踩坑：静态 Map 当缓存，只 put 从不清理，跑几天必 OOM
public class PromptCache {
    private static final Map<String, byte[]> CACHE = new HashMap<>();
    public static void put(String k, byte[] v) { CACHE.put(k, v); }
}
```

```java
// ✅ 修正：用有界、带过期的缓存（Caffeine），自动淘汰旧条目
private static final Cache<String, byte[]> CACHE = Caffeine.newBuilder()
    .maximumSize(10_000)                       // 上限，超了自动淘汰
    .expireAfterWrite(Duration.ofMinutes(30))  // 过期自动清
    .build();
```

> 真要用缓存，优先放 [Redis](/back-end/frontend-backend-guide/12-redis-in-practice) 这种带淘汰策略的外部存储，别用进程内的裸 `static Map`。

### 泄漏 2：ThreadLocal 用完不 `remove()`

`ThreadLocal` 在**线程池**场景下尤其危险：线程是复用的，不会销毁，你 set 进去的值会一直挂在那条线程上，跨请求残留——既泄漏内存，又可能把上个用户的数据带给下个用户。

```java
// ❌ 踩坑：set 了从不 remove，线程池里的线程一直拿着旧值
public class UserContext {
    private static final ThreadLocal<String> UID = new ThreadLocal<>();
    public static void set(String uid) { UID.set(uid); }
    public static String get() { return UID.get(); }
}
```

```java
// ✅ 修正：在请求结束的 finally 里 remove，通常放在拦截器/过滤器
try {
    UserContext.set(uid);
    chain.doFilter(req, resp);
} finally {
    UserContext.clear(); // 内部调 UID.remove()，否则复用此线程的下个请求会读到脏数据
}
```

### 泄漏 3：流 / 连接 / 监听器没关

`InputStream`、数据库 `Connection`、`HttpClient` 响应体不关，底层资源（文件句柄、socket、缓冲区）就一直占着。

```java
// ❌ 踩坑：异常路径下 in 不会被关闭
InputStream in = ossClient.openStream(url);
byte[] data = in.readAllBytes(); // 这里抛异常，in 就泄漏了
in.close();
```

```java
// ✅ 修正：try-with-resources，无论正常还是异常都自动 close
try (InputStream in = ossClient.openStream(url)) {
    byte[] data = in.readAllBytes();
} // 编译器自动插入 finally { in.close(); }
```

> 凡是实现了 `AutoCloseable` 的，一律用 try-with-resources。这是 Java 资源管理的铁律，相关基础见 [异常处理](/back-end/java/05-exception)。

### 泄漏 4：线程池 / 连接池没释放

临时 `new` 出来的 `ExecutorService` 用完不 `shutdown()`，它的核心线程会一直存活，进程关不掉、内存也回收不了。连接池同理。

```java
// ❌ 踩坑：方法里临时建线程池，用完不关，每调一次泄漏一个池
public void batch(List<Task> tasks) {
    ExecutorService pool = Executors.newFixedThreadPool(4);
    tasks.forEach(t -> pool.submit(() -> handle(t)));
    // 方法返回了，但 pool 的 4 条线程还活着，永远不退出
}
```

```java
// ✅ 修正：要么复用全局池（推荐），要么 try-finally 里 shutdown
ExecutorService pool = Executors.newFixedThreadPool(4);
try {
    tasks.forEach(t -> pool.submit(() -> handle(t)));
} finally {
    pool.shutdown();
}
```

### 泄漏 5：作为 Map key 的对象没正确实现 `equals/hashCode`

如果一个对象被当作 `HashMap` 的 key，却没重写 `equals`/`hashCode`，每个新对象都被当成不同 key，于是「更新」变成了「不断新增」，Map 只涨不缩。

```java
// ❌ 踩坑：TaskKey 没重写 equals/hashCode，缓存命中率 0，条目无限增长
class TaskKey { String uid; String type; } // 用默认的对象身份比较
Map<TaskKey, Task> cache = new HashMap<>();
// 每次 new TaskKey(...) 都是「新 key」，cache.get 永远 miss，put 永远新增
```

```java
// ✅ 修正：重写 equals/hashCode（或直接用 record 自动生成）
record TaskKey(String uid, String type) {} // record 自动生成 equals/hashCode
```

> 集合与 `equals/hashCode` 的基础见 [Java 集合](/back-end/java/04-collections)。

---

## 20.4 怎么判断是「泄漏」还是「内存就是不够」？

这是排查的第一个岔路口，走错方向白费功夫。判断依据是**看内存随时间的曲线，尤其是 Full GC 之后能不能降下来**：

```text
内存使用
  ↑
  │              ██   泄漏：锯齿整体抬升，
  │           ██▔ ▔██     每次 Full GC 后回不到原点，
  │        ██▔       ▔     基线一路向上 → 迟早 OOM
  │     ██▔
  │  ██▔
  └────────────────────────────→ 时间

  ↑
  │ ██▏  ▕██▏  ▕██▏  ▕██▏   内存不足/数据量大：
  │█  ▔▏█  ▔▏█  ▔▏█  ▔▏     锯齿很高但 Full GC 后能落回稳定基线，
  │    ▏    ▏    ▏    ▏      只是基线本身离上限太近 → 配置问题
  └────────────────────────────→ 时间
```

| 现象 | 大概率结论 | 下一步 |
| --- | --- | --- |
| 内存**持续上涨**，Full GC 后**降不下来**，基线一路抬高 | **内存泄漏** | 抓 heap dump 找谁在拽着对象不放 |
| 内存锯齿很高，但 Full GC 后能**回落到稳定基线** | 不是泄漏，是堆配小了 / 单次数据量大 | 调 `-Xmx` 或改流式处理/分页 |
| 启动没多久、流量也不大就 OOM | 配置太小 或 单请求一次性加载海量数据 | 看 dump 里最大那个对象是不是「一坨集合」 |

**怎么看这条曲线**：用监控（如 Prometheus + Grafana，见 [可观测性](/back-end/frontend-backend-guide/30-observability)）盯 JVM 堆已用量指标；没有监控时，临时用下面的命令手动观察。

```bash
# 每 2 秒打印一次 GC 统计，连看几分钟
jstat -gcutil <pid> 2000
```

```text
  S0     S1     E      O      M     CCS    YGC   YGCT    FGC    FGCT     GCT
  0.00  31.25  68.20  92.15  95.30  92.10  1820  45.2    142   88.6    133.8
  0.00  30.10  71.40  92.30  95.31  92.11  1821  45.3    143   89.7    135.0
```

**怎么读这段输出**：

- `O` 是老年代（Old）使用率。这里一直贴着 **92% 上下**，而且 `FGC`（Full GC 次数）从 142 → 143 在涨，`FGCT`（Full GC 累计耗时）也在涨——说明**反复 Full GC 都压不下老年代**，这是泄漏的强信号。
- 如果 `O` 在 Full GC 后能掉到 30%、40% 再慢慢涨，那就不是泄漏，是正常的内存使用节奏。
- `FGC` 短时间内频繁增长 + 应用变慢 = 很可能正在滑向 `GC overhead limit exceeded`。

**结论**：`O` 居高不下 + Full GC 拉不回来 = 按「泄漏」处理，去抓 dump；否则按「配置/数据量」处理，去调参数或改代码。

---

## 20.5 实操定位：让 OOM 自己留下案发现场

### 目标：OOM 时自动 dump，事后慢慢分析

线上 OOM 往往凌晨发生、转瞬即逝，等你登上去进程早被重启了。所以**最重要的一步是在启动参数里挂上自动抓现场**，让 JVM 在 OOM 那一刻把整个堆快照写到磁盘：

```bash
java -Xmx512m \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/data/dump/svc-canvas.hprof \
  -jar svc-canvas.jar
```

> 💡 **前端类比**：相当于给应用挂了一个全局 `window.onerror` + 自动上传当时的完整内存快照到 Sentry，只不过这里抓的是整个 JVM 堆。**这个参数几乎零成本，强烈建议所有线上服务默认开启**——出事时有没有 dump，决定了你是 10 分钟定位还是抓瞎好几天。

OOM 触发时，日志里会看到：

```text
java.lang.OutOfMemoryError: Java heap space
Dumping heap to /data/dump/svc-canvas.hprof ...
Heap dump file created [536870912 bytes in 1.842 secs]
```

**怎么读**：看到 `Heap dump file created` 就说明现场已经保存到 `/data/dump/svc-canvas.hprof`（约 512MB，和你的 `-Xmx` 同量级）。在 K8s 里记得把 `HeapDumpPath` 指向一个**挂载的持久卷**，否则 Pod 重启 dump 就随容器一起没了。把这个 `.hprof` 文件拷到本地，用 MAT 打开。

### 用 MAT（Eclipse Memory Analyzer）找占用大户

MAT 是分析 heap dump 的标准工具。打开 `.hprof` 后，它会自动跑一遍 Leak Suspects 报告，但你要会看的核心是两个视图。

**① Histogram（直方图）**——按类统计实例数量和占用字节，回答「哪种对象最多/最占内存」：

```text
Class Name                                    | Objects   | Shallow Heap | Retained Heap
----------------------------------------------+-----------+--------------+--------------
byte[]                                        |   48,210  | 402,118,400  |  402,118,400
com.example.svccanvas.model.Canvas            |   50,003  |   3,200,192  |  410,330,112
java.util.HashMap$Node                        |  150,221  |   4,807,072  |   ...
```

**怎么读 Histogram**：

- `Shallow Heap`：对象自身占的内存。`Retained Heap`：如果这个对象被回收，能连带释放多少——**这才是真正的「占用大户」指标**。
- 这里 `Canvas` 的 Retained Heap 高达 410MB，意味着「这些 Canvas 对象拽着的那一大坨 `byte[]`（缩略图）加起来 410MB」。结合 20.2 的导出场景，基本锁定就是那 5 万张图全进了内存。

**② Dominator Tree（支配树）**——按「谁拽着谁」排序，直接告诉你「哪个对象（树）占了最多内存」：

```text
Class Name                                         | Retained Heap | Percentage
---------------------------------------------------+---------------+-----------
com.example.svccanvas.service.ExportService        |  410,330,112  |   80.4%
  └─ java.util.ArrayList                            |  409,600,000  |   80.2%
       └─ 50,003 × com.example.svccanvas.model.Canvas
```

**怎么读 Dominator Tree**：从顶上往下看，`ExportService` 一个对象就支配了 **80.4%** 的堆——它持有的那个 `ArrayList` 里塞了 5 万个 `Canvas`。这就直接定位到了「哪个类的哪个字段」在囤积内存。

**③ 找 GC Root 引用链**——回答「为什么这玩意儿回收不掉」：在可疑对象上右键 → `Path to GC Roots` → `exclude weak/soft references`。MAT 会画出从 GC Root 到这个对象的引用链：

```text
ExportService.cache (java.util.ArrayList)
  ↑ referenced by
PromptCache.CACHE (static field)   ← 一个 static 字段拽着它，所以永远回收不了
  ↑ GC Root: System Class
```

**怎么读**：链条最顶上是 `PromptCache.CACHE` 这个 `static field`，它就是那根「没断的引用」（对应 20.3 的泄漏 1）。看到 `static field` / `Thread` / `ThreadLocal` 出现在 GC Root 链上，基本就是泄漏的真凶。

**结论流程**：Dominator Tree 找到「占最多的那个对象」→ Path to GC Roots 找到「是谁拽着它不放」→ 对照代码里的那个字段，改成有界缓存 / 加 `remove` / 流式处理。

### 不想等 OOM？用 `jmap` 看实时对象分布

服务还活着、内存正在涨但还没爆时，可以直接看当前堆里的对象分布，不用等它崩：

```bash
# :live 会先触发一次 Full GC，只统计存活对象，更准
jmap -histo:live <pid> | head -n 15
```

```text
 num     #instances         #bytes  class name (module)
-------------------------------------------------------
   1:         48210      402118400  [B (java.base)            ← byte[]，约 383MB
   2:         50003        3200192  com.example.svccanvas.model.Canvas
   3:        150221        4807072  java.util.HashMap$Node (java.base)
   4:        120400        2889600  java.lang.String (java.base)
```

**怎么读这段输出**：

- 按 `#bytes` 排序，第一名 `[B`（就是 `byte[]`）吃了 383MB，第二名是 5 万个 `Canvas`——和 dump 分析的结论对上了。
- 隔几分钟再跑一次对比：如果某个类的 `#instances` 一直涨、回不落，它就是泄漏嫌疑。这招在生产上做「快速止血判断」很有用，不用导出几百 MB 的 dump。

> ⚠️ `jmap -histo:live` 会触发 Full GC，可能让服务卡顿几百毫秒到几秒，**别在流量高峰对核心服务频繁执行**。这些诊断工具（`jstat`/`jmap`/`jstack`/`arthas`）的完整用法和注意事项，见 [诊断工具箱](/back-end/frontend-backend-guide/29-diagnostic-toolbox)。

---

## 20.6 一张排查决策图

把本章串起来，遇到「内存类报警」时按这个顺序走：

```text
收到 内存高 / OOM 报警
        │
        ▼
 看报错关键字 ── Metaspace？ ──────→ 查动态生成类 / 类加载器泄漏，设 MaxMetaspaceSize
        │       native thread？ ───→ 数线程数 ps -o nlwp，改用线程池
        │       Direct buffer？ ────→ 查 NIO/Netty DirectByteBuffer
        ▼ (heap space / GC overhead)
 jstat -gcutil 看老年代 O ── Full GC 后能降下来 ──→ 不是泄漏：调 -Xmx 或改流式/分页
        │
        └── 降不下来（基线一路涨）──→ 判定泄漏
                                          │
                                          ▼
                          有 .hprof（开了 HeapDumpOnOutOfMemoryError）
                                          │
                            ┌─────────────┴─────────────┐
                            是                          否
                            ▼                            ▼
                     MAT: Dominator Tree          jmap -histo:live 实时看
                     → Path to GC Roots           → 隔几分钟对比涨势
                            │                            │
                            └─────────────┬──────────────┘
                                          ▼
                          定位到「哪个字段拽着对象」
                          → 有界缓存 / ThreadLocal.remove
                          / try-with-resources / shutdown
```

这套方法论会在 [排查手册](/back-end/frontend-backend-guide/28-troubleshooting-playbook) 里和 CPU 飙高、线程死锁等场景一起整理成可照抄的 playbook。

---

## 小结

- `OutOfMemoryError` 不是一种错误而是一类，**冒号后面那半句**（`Java heap space` / `Metaspace` / `GC overhead limit exceeded` / `unable to create new native thread` / `Direct buffer memory`）才是诊断起点，不同区域对应完全不同的原因。
- Java 有 GC 仍会泄漏，本质是「**该断的引用没断**」：静态集合只加不删、ThreadLocal 不 remove、流/连接不关、线程池不 shutdown、Map key 的 `equals/hashCode` 没写对——记住这 5 个高频坑就覆盖了绝大多数案例。
- 区分泄漏 vs 内存不足，看 `jstat -gcutil` 里**老年代 `O` 在 Full GC 后能否降下来**：降不下来、基线一路涨 = 泄漏；能回落但基线太高 = 配置小/数据量大。
- 线上服务一律默认开 `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=...`，让 OOM 自动留下案发现场，且 dump 路径要落在持久卷上。
- 分析 dump 用 MAT 的 **Dominator Tree（谁占得多）+ Path to GC Roots（谁拽着不放）**；不想等崩就用 `jmap -histo:live` 看实时分布、隔几分钟对比涨势。

### 自测

1. 服务运行三天后内存报警，你用 `jstat -gcutil` 看到老年代 `O` 一直 90% 以上、Full GC 频繁但拉不回来。这是泄漏还是堆配小了？你的下一步操作命令是什么？
2. 一个用了线程池的 Web 服务，请求结束后没有清理 `ThreadLocal`，会引发哪两类问题（不只内存）？正确的清理代码应该放在哪里？
3. 堆使用率监控显示一直很健康（不到 50%），服务却抛 `OutOfMemoryError: Direct buffer memory`。为什么堆没满还会 OOM？你会去查哪类对象？

### 下一章

定位内存问题往往离不开服务器本身的观察手段（看进程、看文件句柄、看内存）。下一章 [Linux 服务器入门](/back-end/frontend-backend-guide/21-linux-server-essentials) 带你掌握登上线上机器后必须会的那套基本功。

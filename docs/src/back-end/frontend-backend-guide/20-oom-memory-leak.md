# OOM 与内存泄漏：有 GC，为什么内存还会不够

::: info 按需阅读
这一篇从任务导出占满内存的例子出发，说明怎样区分原因、保留信息和追踪对象。代码与输出都是示意，不对应另一套完整工程；故障复现只在隔离环境进行。内存区域见 [JVM 内存模型](/back-end/frontend-backend-guide/18-jvm-memory-model)，回收机制见 [GC](/back-end/frontend-backend-guide/19-garbage-collection)，第一次学习仍可继续 [五阶段主线](./index)。
:::

用户要导出全部历史附件，后端先把它们下载进一个内存缓冲区：

```java
public byte[] exportAll(String uid) throws IOException {
    List<Canvas> all = canvasRepository.findByUid(uid);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    for (Canvas c : all) {
        buffer.write(ossClient.download(c.getThumbUrl()));
    }
    return buffer.toByteArray();
}
```

这里沿用 `Canvas` 作为附件记录的示意类型，省略了权限、格式和业务实现。这段代码只是拼接字节，并没有实现 ZIP 格式。

附件越多，列表和缓冲区越大；`toByteArray()` 还会复制一份内容。如果几个用户一起导出，可能很快出现 `OutOfMemoryError: Java heap space`。

**这不一定是泄漏。** 数据此时还被正在执行的请求使用，GC 不能替你删除它。先理解这点，才能避免“有 OOM 就找泄漏”“有 GC 就不会 OOM”这两种误判。

## 1. 先看错误具体指向哪里

`OutOfMemoryError` 是一类错误。冒号后面的描述，比只看到“OOM”更有用：

| 错误或状态 | 先调查什么 | 不要直接下的结论 |
| --- | --- | --- |
| `Java heap space` | 堆上限、活跃数据、一次加载量、队列与缓存 | 不一定是泄漏 |
| `Metaspace` | 类加载数量、类加载器、元空间上限 | 调大堆不直接解决元空间问题 |
| `GC overhead limit exceeded` | 相应收集器下，GC 花费很多时间却收效很少 | 不是所有收集器都按同一比例触发，也不能据此认定泄漏 |
| `unable to create new native thread` | 线程数量、原生内存、进程或容器的线程配额 | 不一定是没有线程池，多个池的总量也可能过大 |
| `Direct buffer memory` | 直接缓冲区使用、并发、库的释放机制与上限 | 堆占用正常也可能发生 |
| 容器 `OOMKilled` | 容器事件、内存限制和进程总用量 | 不一定有 Java 异常或堆快照；退出码 137 单独也不能证明 OOM |

比如，正常结束的 `new Thread(...)` 会释放相应线程资源，并不是“创建后永不回收”。但每个请求都新建线程，流量高或任务长期阻塞时，仍可能同时存在过多线程。应限制总并发并检查等待原因，见 [线程池与 Executor](/back-end/frontend-backend-guide/15-thread-pools-executor)。

直接缓冲区也不等于所有操作都实现了零拷贝。NIO、Netty 等库的分配和释放机制不同：有的依赖清理机制，有的要求引用计数释放。应按所用库的规则检查，不能一概靠手动 GC 或只增加 `MaxDirectMemorySize`。

`OutOfMemoryError` 属于 `Error`，不是普通业务异常。不要随意捕获后返回成功、继续假装健康；也不能假定它必然让整个进程自动退出。具体是任务失败、线程结束还是进程退出，取决于现场与配置。线上需要有健康检查、摘流和恢复预案，而不是排查时直接强制终止进程。

## 2. 先读取已有信息，不急着抓快照

记录故障时间、实例、错误全文和当时执行的操作，再对齐现有的堆使用量、GC、线程数、容器内存与任务积压。尽量先用已有监控，见 [可观测性](/back-end/frontend-backend-guide/30-observability)。

确需现场采样时，在授权环境中用 `jps -l` 找到进程。核对后，在同一终端用 `PID=实际数字` 设置目标进程号，再读取：

```bash
jstat -gcutil "${PID:?请先设置已核对的目标 JVM 进程号}" 2000 5
```

这组命令不修改堆大小，也不主动要求 Full GC；`jstat` 每两秒采样一次，共五次。工具仍有开销，输出和权限要求依环境而异。Linux 上还可以看目标进程的线程数量和驻留内存：

```bash
ps -o pid,nlwp,rss -p "${PID:?请先设置已核对的目标 JVM 进程号}"
```

`nlwp` 是线程数，`rss` 通常以 KiB 表示驻留内存，具体以系统手册为准；它不是“堆已用量”，也不等于完整容器用量。这组字段不能直接照搬到所有操作系统。

### 怎样看出泄漏嫌疑

如果相近负载下，经历正常回收后的存活量持续上升，任务结束后仍不回落，就应调查对象是否被不必要地保留。但这只是线索：队列尚未排空、合法缓存预热、业务数据增长，都可能提高基线。

反过来，内存能回落也不能证明完全没有泄漏。需要连续观察代表性时间段，并与请求量、缓存大小、队列长度相互印证，而不是用两行 `jstat` 的百分比就作结论。不要为了“看回收后基线”先在线上强制 Full GC。

## 3. 导出例子先修什么：不要让所有内容同时留在堆里

如果确认是单次导出加载过多，方向是分页或流式处理，限制同时保留的数据量。下面只演示依次读取附件并写给调用方，不是完整的压缩导出实现：

```java
public void exportAll(String uid, OutputStream out) throws IOException {
    try (Stream<Canvas> stream = canvasRepository.streamByUid(uid)) {
        Iterator<Canvas> items = stream.iterator();
        while (items.hasNext()) {
            Canvas c = items.next();
            try (InputStream in = ossClient.openStream(c.getThumbUrl())) {
                in.transferTo(out);
            }
        }
    }
}
```

这里不再把所有文件装进一个 `ByteArrayOutputStream`，也避免最后再复制一份完整内容。调用方负责输出流的生命周期，方法负责关闭自己打开的查询流和输入流。

但方法名叫 `streamByUid`，不代表底层一定流式读取。数据库驱动、查询方式、游标和客户端缓冲都要验证；如果 Repository 先查全量再转成 Stream，仍会占用大量内存。流式处理可以限制峰值，但并不自动保证整个请求恒定内存，更不能忽略慢客户端、连接占用、超时和导出并发上限。

## 4. 如果请求结束了数据还不释放，沿引用找保留者

**内存泄漏**在这里指：业务已经不需要的数据，仍然有有效引用链让它保持可达。GC 不懂业务意图，所以不会回收。JS 中长寿命监听器或计时器保留了不再需要的对象，也是同类问题。

### 缓存只进不出

```java
public class PromptCache {
    private static final Map<String, byte[]> CACHE = new HashMap<>();

    public static void put(String key, byte[] value) {
        CACHE.put(key, value);
    }
}
```

只要这个类及缓存长期可达，缓存中的数据就会一直被保留。上例还没有处理并发访问；即使换成并发 Map，也没有解决容量问题。

可用有界缓存，例如已有 Caffeine 依赖时：

```java
private static final Cache<String, byte[]> CACHE = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofMinutes(30))
        .build();
```

这是条目数量上限，不是字节上限。每个 `byte[]` 大小差异明显时，要考虑按权重限制，并估算峰值。过期清理也依缓存维护机制进行，不能理解成到点瞬间清空。是否使用本地缓存或 [Redis](/back-end/frontend-backend-guide/12-redis-in-practice)，取决于访问方式和故障需求；移到 Redis 不会免除容量与淘汰设计。

### 线程复用，让 ThreadLocal 的值跨请求残留

```java
public class UserContext {
    private static final ThreadLocal<String> UID = new ThreadLocal<>();

    public static void set(String uid) { UID.set(uid); }
    public static String get() { return UID.get(); }
    public static void clear() { UID.remove(); }
}
```

```java
try {
    UserContext.set(uid);
    chain.doFilter(req, resp);
} finally {
    UserContext.clear();
}
```

`finally` 中清理的是当前线程持有的值。线程池会复用线程，不清理既可能保留不需要的数据，也可能让下一个请求读到上一个用户的信息。异步线程上的上下文还要单独按其生命周期处理，不能只在请求线程清理一次就认为全部结束。

### 资源没关，与对象泄漏有关但不完全相同

流、数据库连接和响应体可能持有文件句柄、socket、缓冲区等资源。对自己负责关闭的资源，优先使用 try-with-resources：

```java
try (InputStream in = ossClient.openStream(url)) {
    process(in);
}
```

即使 `process` 抛异常，输入流也会关闭。是否由当前方法负责关闭，要看 API 的所有权约定；不要擅自关闭借来的共享客户端。关闭资源也不代表可以放心 `readAllBytes()` 读取任意大文件。相关语法见 [异常处理](/back-end/java/05-exception)。

临时线程池和连接池同样需要生命周期管理：不要每次调用都创建一个池又不关闭；应用级池由所属组件或容器统一管理。`shutdown()` 只是开始关闭，不等于已经等待所有任务完成。

::: details 另一个容易遗漏的保留原因：Map 的 key 没有表达业务相等
如果用 `TaskKey` 表示“某用户的某类任务”，却每次新建对象并沿用对象身份比较，原本以为的更新可能变成不断新增。需要按照业务含义实现稳定的 `equals` 和 `hashCode`，并避免把会变化的字段用作 key：

```java
class TaskKey {
    private final String uid;
    private final String type;

    TaskKey(String uid, String type) {
        this.uid = uid;
        this.type = type;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        TaskKey that = (TaskKey) other;
        return Objects.equals(uid, that.uid) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uid, type);
    }
}
```

这里使用 `java.util.Objects`。不是所有 key 都必须按值相等，关键是实现与你想表达的含义一致；正确相等规则也不能替代容量限制。基础见 [Java 集合](/back-end/java/04-collections)。
:::

## 5. 只有需要对象级证据时，再准备 heap dump

监控能说明哪里在增长，通常不能说明“谁引用了谁”。**heap dump** 是某个时刻的堆快照，可用 MAT（Eclipse Memory Analyzer）等工具离线分析。

在采集前，必须先明确影响范围：**快照可能暂停应用、触发或伴随回收，并消耗大量磁盘和 I/O；其中可能含密码、令牌、用户数据和请求内容。** 确认授权、业务窗口、可用空间、受控存储和保留期限，不在核心实例高峰时随意执行，也不要上传到公开分析网站。

### 为下一次故障预先配置

下面是启动配置示意，不是对当前进程的只读操作。`app.jar` 代表自己的应用，`/data/dump` 必须是已经准备好的可写目录：

```bash
java -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/data/dump \
  -jar app.jar
```

它会在 JVM 能处理的相应 OOM 场景下尝试保存快照，但不保证覆盖每种内存错误，更不能保证进程被内核杀死时还来得及写。目录中可能按 PID 生成文件，重复文件名、磁盘不足或权限问题都可能使写入失败，要验证采集机制而不是只加参数。

容器环境还需确认文件会不会随重建消失；需要保留时使用批准的持久存储。快照大小可能很大，不能简单按 `Xmx` 精确估算磁盘需求。

### 已有快照后，MAT 先回答两个问题

先看 Histogram，找实例多、占用高的类型，例如大量 `byte[]`。这里的 **Shallow Heap** 是对象自身大小，数组自身也包括其数据；大量字节数组不一定代表泄漏，它们可能属于导出、缓存或正常 I/O。

再看 Dominator Tree，找哪些对象保留了大量内存。**Retained Heap** 大致表示：若某对象及其独占保留链可被回收，能一并释放的量。共享数据不能随意重复相加；占用大的对象也不一定是错误对象。

假设发现一个缓存 Map 保留了大量历史任务，继续查看 `Path to GC Roots`，并注意弱引用、软引用等类型的差别。一个可能的强引用路径是：

```text
长期存活的类
  └─ PromptCache.CACHE
       └─ Map 条目
            └─ byte[]
```

这能说明缓存正在保留这些数组，但是否泄漏还要回到业务：任务已经结束了吗，缓存是否超出容量或期限？如果数据其实被仍在执行的导出持有，那可能是峰值占用问题，不该把两个场景拼成同一个结论。

## 6. 服务还活着，可以先看对象直方图吗

可以，但不要把它当低成本的默认监控。先看现成指标；确有必要且已经评估影响、获得授权后，再考虑：

```bash
jmap -histo:live "${PID:?请先设置已核对的目标 JVM 进程号}"
```

**`:live` 会请求 Full GC，堆遍历可能造成明显停顿。** 不要因为只想看前十行就以为影响小，后面接 `head` 也不会免掉前面的回收和遍历。其他直方图或快照命令也要查对应 JDK 的行为，不能简单等同为“只读就无影响”。

输出可能包含：

```text
 num     #instances       #bytes  class name
   1:         48210    402118400  [B
   2:         50003      3200192  com.example.Canvas
```

`[B` 表示 `byte[]`，`#bytes` 是这类对象自身占用的合计，不是完整引用链的保留大小。直方图适合缩小调查范围，不能单靠某类对象数量大就认定它泄漏。更完整的工具权限、影响和采集方式见 [诊断工具箱](/back-end/frontend-backend-guide/29-diagnostic-toolbox)。

回到开头的导出故障，合理顺序是：先确认错误区域和故障时间，再看负载与内存趋势；若单次工作量过大，限制加载量和并发；若怀疑不必要的保留，再在可控条件下用快照追引用。不要把“加大内存”“抓 dump”“重启”当成同一种处理。

跨 CPU、线程和内存的排查顺序见 [排查手册](/back-end/frontend-backend-guide/28-troubleshooting-playbook)。如果还不熟悉服务器上的进程与日志，再按需看 [Linux 服务器入门](/back-end/frontend-backend-guide/21-linux-server-essentials)。

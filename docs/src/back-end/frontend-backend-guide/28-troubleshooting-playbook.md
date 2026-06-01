# 排查实战手册

> 上一章 [问题排查方法论](/back-end/frontend-backend-guide/27-troubleshooting-methodology) 讲的是「遇到任何问题该怎么想」——先稳住、看监控、缩小范围、提出假设再验证。这一章是它的**配套速查手册**：把后端线上最高频的 7 类症状，每个都拆成一套可以照着敲的流程。
>
> 半夜被电话叫醒、群里在炸、老板在问「好了没」——这种时刻你脑子是空的，需要的不是方法论，而是**一份能直接抄的清单**。这章就是干这个的。建议读一遍混个眼熟，真出事时再翻回来对着敲。

每个症状统一用这个结构来写，和前端排 bug 的「复现 → 看报错 → 定位 → 改」是一个套路，只是工具换了：

**现象**（你看到什么） → **可能原因**（先有假设） → **排查命令**（可复制照敲） → **怎么读输出**（重点看哪几行） → **解法**（短期止血 + 长期根治）。

> **前端类比**：你排前端 bug 时，脑子里也有一份隐形清单——「白屏先看 Console 报错，接口 404 先看 Network 请求路径，样式错位先看 Elements 面板」。这章就是把后端的这份隐形清单写出来：哪个症状用 `jstack`、哪个用 `jmap`、哪个用 `kubectl describe`。

---

## 28.1 速查表：症状 → 翻到哪一节 + 常用工具

先放总览。线上出事时，先在这张表里对号入座，再翻到对应小节照着敲：

| # | 症状 | 翻到 | 第一反应工具 | 一句话方向 |
| --- | --- | --- | --- | --- |
| 1 | 接口报 500 / 抛异常 | [28.2](#_28-2-症状一-接口报-500-抛异常) | 应用日志 + `grep traceId` | 找异常栈第一行，定位抛出类与行号 |
| 2 | 接口变慢 / 超时 | [28.3](#_28-3-症状二-接口变慢-超时) | 日志耗时 / APM / `EXPLAIN` | 先分层定位慢在哪一层，再深挖 |
| 3 | CPU 飙到 100% | [28.4](#_28-4-症状三-cpu-飙到-100) | `top` → `jstack` | 找到高 CPU 线程在跑哪段代码 |
| 4 | 内存暴涨 / OOM | [28.5](#_28-5-症状四-内存暴涨-oom) | `jmap` + GC 日志 + MAT | 看谁占了堆、是不是 Full GC 频繁 |
| 5 | 线程卡住 / 死锁 | [28.6](#_28-6-症状五-线程卡住-死锁) | `jstack` | 找 `BLOCKED` 线程、找 deadlock 段 |
| 6 | 服务频繁重启（K8s） | [28.7](#_28-7-症状六-服务频繁重启-k8s) | `kubectl describe` / `logs --previous` | 看退出码、Last State、探针 |
| 7 | 数据不对 / 偶发问题 | [28.8](#_28-8-症状七-数据不对-偶发问题) | 日志对照 DB + 加日志蹲守 | 怀疑并发、幂等、时区、序列化 |

> 下面命令里凡是 `<pid>`、`<tid>`、`<nid>`、`<traceId>` 都是占位符，敲的时候换成真实值。容器里跑的 Java，命令前面通常要加 `kubectl exec -it <pod> --` 或先 `docker exec -it <容器> bash` 进去。诊断工具本身（jstack/jmap/jstat/arthas 等）的安装与原理，本章只管「怎么用来破案」，工具全集见 [诊断工具箱](/back-end/frontend-backend-guide/29-diagnostic-toolbox)。

---

## 28.2 症状一：接口报 500 / 抛异常

### 现象

前端报「请求失败」，Network 里某个接口返回 HTTP 500，或者业务上返回了 `RtData.fail(...)` 但用户说「明明该成功」。后端监控里错误率（5xx 比例）有一根尖刺。

### 可能原因

- **业务异常**：参数不合法、配额不足、状态机不允许（比如对一个已完成的任务再点「取消」）。这类是「预期内的拒绝」，通常被框架转成了 `RtData.fail`，不该是 500。
- **系统异常**：空指针 `NullPointerException`、下游调用超时、数据库连接拿不到、序列化失败。这类是「程序没料到」，会冒成 500。
- 区分这两类是第一步：**业务异常有明确 msg、可预期；系统异常是异常栈、不可预期**。

> **前端类比**：业务异常像你 `axios` 拿到一个 `200` 但 `body.code !== 0`（后端按约定告诉你「这次不行，因为 XXX」）；系统异常像 `axios` 直接 `catch` 到一个没人处理的 `TypeError: undefined is not a function`——程序炸了，不是业务拒绝你。

### 排查命令

第一件事：拿到 `traceId`。前端 Network 响应头里通常有 `X-Trace-Id`，或返回体里带 `traceId`。拿到它就能在日志里串起这次请求的完整上下文（traceId 怎么来的见 [读懂日志](/back-end/frontend-backend-guide/26-reading-logs)）。

```bash
# 1) 用 traceId 捞出这次请求在本服务里打的所有日志（含异常栈），-A 30 多看后面 30 行
grep "8a3f1c2e9b7d4e5f" /var/log/svc-canvas/app.log -A 30

# 2) 如果不知道 traceId，先按时间和 ERROR 级别捞最近的异常
grep -n "ERROR" /var/log/svc-canvas/app.log | tail -50

# 3) 容器里没落盘、只打到 stdout 时，直接看容器日志并过滤
kubectl logs svc-canvas-7d9f8c-abcde --since=10m | grep -A 30 "8a3f1c2e9b7d4e5f"
```

### 怎么读输出

一段典型的系统异常日志长这样：

```text
2026-06-01 14:23:07.512 ERROR [svc-canvas] [http-nio-8080-exec-12] [traceId=8a3f1c2e9b7d4e5f]
  c.x.canvas.controller.TaskController : 提交生图任务失败, userId=10086
java.lang.NullPointerException: Cannot invoke "com.x.cpt.api.dto.UserQuota.getRemain()" because "quota" is null
    at com.x.canvas.service.TaskService.submit(TaskService.java:88)
    at com.x.canvas.controller.TaskController.submit(TaskController.java:54)
    at jdk.internal.reflect.GeneratedMethodAccessor42.invoke(Unknown Source)
    ...
Caused by: feign.RetryableException: Read timed out executing GET http://svc-user/quota/10086
    at com.x.cpt.api.client.UserClient.getQuota(UserClient.java)
    ... 38 common frames omitted
```

按这个顺序读，一行不多看：

1. **第一行**：时间、级别（`ERROR`）、服务名、线程名（`http-nio-8080-exec-12`）、`traceId`、是哪个类打的、业务上下文（`userId=10086`）。这是「案发现场登记表」。
2. **异常类型那一行**：`java.lang.NullPointerException: ... because "quota" is null`。Java 17 的 NPE 会告诉你**到底哪个变量是 null**（`quota`），这是它的好处，比前端 `undefined is not a function` 友好。
3. **栈顶第一行 `at ...`**：`TaskService.java:88`——**这就是抛出点**。打开这个文件第 88 行，看 `quota` 怎么来的。这是定位的落点，往下的 `reflect`、框架内部帧基本不用看。
4. **`Caused by:`**：这才是**真正的根因**。`quota` 为什么是 null？因为 `Caused by` 说 `svc-user` 调用 `Read timed out`——下游超时了，Feign 返回了 null，上层没判空就 `.getRemain()` 炸了。**看异常栈一定要看到最后一个 `Caused by`，根因往往藏在那里。**

> **怎么一眼区分业务异常 vs 系统异常**：业务异常通常是 `WARN` 级别、带 `RtData.fail("配额不足")` 这种人话 msg、**没有长长的异常栈**；系统异常是 `ERROR` 级别、带几十行 `at ...` 栈。看到栈，就是程序没处理好的系统异常。

### 解法

- **短期止血**：如果根因是下游超时（如上例），先确认是不是 `svc-user` 出了问题（转到 [28.3](#_28-3-症状二-接口变慢-超时)）。同时上层代码该对 Feign 返回判空 + 走降级，而不是裸调 `.getRemain()`。
- **长期根治**：
  - 系统级：所有 Feign 客户端配 `fallback`（见 [后端思维](/back-end/frontend-backend-guide/01-backend-mindset) 里的四件套），拿到下游返回先判空。
  - 业务级：用统一异常处理（`@RestControllerAdvice`）把业务异常转成 `RtData.fail`，别让它冒成 500；异常码集中放在 `cpt-common` 里管理。异常体系的写法见 [Java 异常处理](/back-end/java/05-exception)。

---

## 28.3 症状二：接口变慢 / 超时

### 现象

监控里某接口 P99 从平时 200ms 涨到 5s，前端转圈、偶发 504。或者全站接口集体变慢。

### 可能原因（按出现频率排）

一个请求的耗时 = 各层耗时之和。先**分层定位**，别一上来就猜：

```text
一次 svc-canvas 提交任务的耗时分布（正常 vs 异常）
                          正常       异常
本服务业务逻辑/序列化   ████ 20ms    ████ 20ms
→ Feign 调 svc-user     ██ 15ms      ████████████ 4800ms   ← 下游慢/超时
→ 查 MongoDB            ███ 30ms     ███ 30ms
→ 抢 Redis 分布式锁     █ 5ms        ████████ 2000ms       ← 锁等待
→ 等连接池给连接        ▏1ms         ██████ 1500ms         ← 连接池排队
（其间发生 Full GC）     —            停顿 1200ms            ← GC STW
```

最常见的几层：**DB 慢查询**、**下游 Feign 慢/超时**、**锁等待**、**Full GC 停顿**、**连接池排队**。

> **前端类比**：和你在 Network 面板看 Waterfall 一模一样——一个慢请求，先看是 DNS 慢、TCP 慢、还是 TTFB（服务端处理）慢。后端「分层」就是把这个 Waterfall 拆到服务端内部：是慢在 DB、慢在下游、还是慢在等锁。

### 排查命令

```bash
# 1) 先用 traceId 看这次慢请求每一步的耗时（前提：代码里打了分段耗时日志）
grep "8a3f1c2e9b7d4e5f" /var/log/svc-canvas/app.log

# 2) 怀疑 DB 慢：开慢查询日志后，看最近的慢 SQL（MySQL 统计库）
tail -50 /var/log/mysql/slow.log

# 3) 怀疑 Full GC 停顿：看 GC 日志里最近有没有长停顿
grep "Full GC" /var/log/svc-canvas/gc.log | tail -20

# 4) 或实时看 GC 频率，每秒打一行
jstat -gcutil <pid> 1000
```

拿到慢 SQL 后，用 `EXPLAIN` 看执行计划（详见 [SQL 与索引](/back-end/frontend-backend-guide/10-sql-and-indexes)）：

```sql
EXPLAIN SELECT * FROM gen_task WHERE user_id = 10086 AND status = 'DONE';
```

### 怎么读输出

**分段耗时日志**（推荐每层都打）长这样，一眼看出慢在哪：

```text
[traceId=8a3f1c2e9b7d4e5f] submit start, userId=10086
[traceId=8a3f1c2e9b7d4e5f] 校验配额(Feign svc-user) cost=4821ms   ← 元凶
[traceId=8a3f1c2e9b7d4e5f] 落库 MongoDB cost=28ms
[traceId=8a3f1c2e9b7d4e5f] 发 RocketMQ cost=12ms
[traceId=8a3f1c2e9b7d4e5f] submit done, total=4880ms
```

`校验配额 cost=4821ms` 占了几乎全部耗时——慢在调 `svc-user` 这一层。接着去查 `svc-user` 自己的日志（同一个 traceId），把锅传下去，直到找到最底层那个慢的人。

`jstat -gcutil <pid> 1000` 的输出（每秒一行）这样读：

```text
  S0     S1     E      O      M     CCS    YGC     YGCT    FGC    FGCT     GCT
  0.00  12.34  68.20  98.71  95.02  92.10   1820   45.231    38   95.870  141.101
  0.00  12.34  71.05  99.10  95.02  92.10   1820   45.231    39   98.420  143.651
```

- `O`（老年代）一直在 98%~99% 居高不下，`FGC`（Full GC 次数）从 38 涨到 39，`FGCT`（Full GC 累计耗时）从 95.8s 涨到 98.4s——**1 秒内就 Full GC 了一次，单次停顿约 2.5 秒**。这就是「接口偶发卡几秒」的元凶：服务在频繁 Full GC，每次 STW 全部线程暂停。GC 各列含义见 [垃圾回收](/back-end/frontend-backend-guide/19-garbage-collection)。

### 解法

按定位到的层对症下药，给你一套**定位话术**（汇报和自查都用得上）：

| 慢在哪层 | 怎么确认 | 解法方向 | 深挖章节 |
| --- | --- | --- | --- |
| DB 慢查询 | 慢查询日志 + `EXPLAIN` 看到全表扫描 | 加索引、改写 SQL、减少回表 | [SQL 与索引](/back-end/frontend-backend-guide/10-sql-and-indexes)、[MySQL 进阶](/back-end/database/mysql/advanced) |
| 下游 Feign 慢/超时 | 分段日志显示卡在 Feign 调用 | 给下游设超时、加降级/熔断、推下游优化 | [后端思维](/back-end/frontend-backend-guide/01-backend-mindset) |
| 锁等待 | `jstack` 看大量线程 `WAITING (on lock)` | 缩小锁粒度、减少持锁时间、Redis 锁加超时 | [线程安全与锁](/back-end/frontend-backend-guide/16-thread-safety-locks) |
| Full GC 停顿 | `jstat` 看 FGC 频繁、老年代占满 | 调堆大小、查内存泄漏、换 GC 器 | [GC](/back-end/frontend-backend-guide/19-garbage-collection)、[OOM 排查](/back-end/frontend-backend-guide/20-oom-memory-leak) |
| 连接池排队 | 监控看活跃连接=最大连接、有等待线程 | 调大池、修「借了不还」的 bug、缩短持有时间 | [连接池](/back-end/frontend-backend-guide/13-connection-pools) |

**定位话术模板**（说给团队听）：「`svc-canvas` 的 `/task/submit` 接口 P99 从 200ms 涨到 4.8s，按 traceId 分段定位，4.8s 里有 4.8s 卡在 Feign 调 `svc-user`；查 `svc-user` 日志，是它查 `gen_task` 全表扫描导致慢查询。短期我给 `svc-canvas` 这个调用补了超时和降级止血，长期 `svc-user` 那张表要补 `(user_id, status)` 联合索引。」——**结论 = 哪个接口、慢多少、卡在哪层、谁的锅、怎么止血、怎么根治**。

---

## 28.4 症状三：CPU 飙到 100%

### 现象

监控告警「`svc-ai` Pod CPU 持续 100%」，接口集体变慢，但内存看着正常。

### 可能原因

- **死循环 / 自旋**：某段代码 `while(true)` 没有出口，或并发下条件判断写错导致空转。
- **正则回溯爆炸**：一个写得不好的正则（带 `(.*)+` 这种）遇到特定输入，匹配复杂度指数级爆炸（ReDoS）。
- **频繁 GC**：堆不够用，CPU 大量耗在垃圾回收上（这种要结合症状四一起看）。

> **前端类比**：浏览器里一个写错的 `while` 或正则会让某个 Tab 卡死、风扇狂转，你打开 Performance 面板录一段火焰图看哪个函数占 CPU。后端没有火焰图面板，但有等价的「线程快照」工具链——把 CPU 高的那个线程「拍照」，看它卡在哪个方法。

### 排查命令（这是一条要背下来的命令链）

```bash
# 1) 找到 CPU 最高的进程 PID（线上 Java 进程通常就一个）
top
# 或直接拿 java 进程 pid
jps -l

# 2) 看这个进程内部，哪个线程在烧 CPU。-H 显示线程，-p 指定进程
top -Hp <pid>
# 记下 %CPU 最高那行的线程号（十进制），比如 12345

# 3) 把线程号转成十六进制（jstack 里线程的 nid 是十六进制的）
printf "%x\n" 12345
# 输出 3039

# 4) 抓线程栈，grep 那个十六进制 nid，看它在跑什么代码（-A 30 看栈往下 30 行）
jstack <pid> | grep -A 30 "nid=0x3039"
```

### 怎么读输出

`top -Hp <pid>` 输出（注意看 `%CPU` 列，默认没按 CPU 排就在交互界面手动按 `P`）：

```text
   PID USER  PR  NI    VIRT    RES  S  %CPU  %MEM     TIME+ COMMAND
 12345 app   20   0 8123456 1.2g   R  99.7   7.8   12:30.45 java
 12346 app   20   0 8123456 1.2g   S   0.3   7.8    0:02.11 java
```

`12345` 这个线程 `%CPU 99.7`、状态 `R`（Running），就是它在烧。`printf "%x\n" 12345` → `3039`，再 `jstack` 定位：

```text
"ai-render-worker-3" #47 daemon prio=5 os_prio=0 tid=0x00007f... nid=0x3039 runnable [0x00007f...]
   java.lang.Thread.State: RUNNABLE
    at java.util.regex.Pattern$Curly.match0(Pattern.java:4275)
    at java.util.regex.Pattern$Curly.match(Pattern.java:4236)
    at java.util.regex.Pattern$GroupHead.match(Pattern.java:4660)
    at com.x.ai.service.PromptValidator.sanitize(PromptValidator.java:41)   ← 你的代码
    at com.x.ai.service.AiRenderService.render(AiRenderService.java:73)
    ...
```

怎么读：

1. `nid=0x3039` 对上了——就是这个线程。
2. 状态 `RUNNABLE`（一直在跑，不是在等），符合「烧 CPU」。
3. 栈里全是 `java.util.regex.Pattern$Curly.match`——**正则在疯狂回溯**。
4. 找到栈里**第一个你自己的类**：`PromptValidator.sanitize(PromptValidator.java:41)`——元凶就是这行的正则。如果是死循环，这里会反复指向你某个 `while` 所在的方法行。

> **小技巧**：连抓 2~3 次 `jstack`（间隔 1 秒）。如果每次同一个线程都卡在**同一行**，基本就是死循环/回溯；如果栈一直在变，那可能只是单纯计算量大或 GC 忙。

### 解法

- **死循环**：修出口条件。并发场景常见的是「用 `while` 自旋等一个标志位却没加 `volatile` 或没加退避」，改用正确的同步机制（见 [线程安全与锁](/back-end/frontend-backend-guide/16-thread-safety-locks)）。
- **正则回溯**：去掉嵌套量词（`(a+)+` 这种），对用户输入的 prompt 先限长，用更线性的写法或预编译 `Pattern`。
- **频繁 GC 导致的高 CPU**：本质是内存问题，转 [28.5](#_28-5-症状四-内存暴涨-oom)。
- **止血**：实在定位不出又在炸，先重启/扩容这个 Pod 恢复服务，但**重启前务必先 `jstack`/`jmap` 把现场抓下来**，否则重启后现场就没了，只能等下次再炸。

---

## 28.5 症状四：内存暴涨 / OOM

### 现象

监控里 `svc-canvas` 内存（RES / 堆使用）一路爬升不回落，日志里出现 `java.lang.OutOfMemoryError: Java heap space`，或者 K8s 把 Pod 以 `OOMKilled` 杀掉重启（这又会引出症状六）。

### 可能原因

- **内存泄漏**：某个集合（`static` 的 `Map`/`List`、缓存）只进不出，越积越多——后端最经典的慢性病。
- **一次性大对象**：一次查了几百万行进内存、上传了超大文件没流式处理。
- **堆设置过小**：实际就需要那么多内存，堆给小了。

> **前端类比**：和 Chrome DevTools 的 Memory 面板抓 Heap Snapshot、看哪类对象在涨、对比两次快照找泄漏是同一个思路。后端的 `jmap` + MAT 就是后端版的 Heap Snapshot + 对比分析。原理与概念见 [OOM 与内存泄漏](/back-end/frontend-backend-guide/20-oom-memory-leak)。

### 排查命令

```bash
# 1) 看堆里对象分布：哪类对象实例最多/占内存最大（:live 会先触发一次 GC，只看存活对象）
jmap -histo:live <pid> | head -20

# 2) 看 GC 是不是已经在频繁 Full GC（救火信号）
jstat -gcutil <pid> 1000

# 3) 抓一份完整堆快照下来离线分析（文件可能几个 G，注意磁盘空间）
jmap -dump:live,format=b,file=/tmp/heap-svc-canvas.hprof <pid>

# 4) 最佳实践：启动参数加上，让 OOM 时自动 dump，事后才有现场
#    -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/
```

### 怎么读输出

`jmap -histo:live` 输出（按占用内存 `#bytes` 降序）：

```text
 num     #instances         #bytes  class name (module)
-------------------------------------------------------
   1:       2451802      392288320  [B (java.base)              ← byte[]，约 374MB
   2:       2451790      117685920  java.lang.String (java.base)
   3:       2451788       98071520  com.x.canvas.model.TaskLog  ← 你的业务类，245 万个实例！
   4:         18203        2912480  java.util.HashMap$Node
   ...
```

怎么读：

1. `[B` 是 `byte[]`，`String`、`HashMap$Node` 是 JDK 自带的，这些「天然多」很正常，先跳过。
2. **盯住你自己包名（`com.x.*`）的类**：`com.x.canvas.model.TaskLog` 居然有 **245 万个实例**——这不正常。一个在线服务怎么会同时存着 245 万条任务日志在内存里？基本锁定它就是泄漏源。
3. 接着去代码里搜 `TaskLog` 在哪被持有不释放——多半是某个 `static List<TaskLog>` 或没设过期的本地缓存。

把 dump 文件拉到本地用 **MAT（Memory Analyzer Tool）** 打开，直接看 **Dominator Tree**（支配树）——它按「谁实际撑着这块内存不让回收」排序：

```text
Class Name                                          Retained Heap   Percentage
─────────────────────────────────────────────────────────────────────────────
com.x.canvas.cache.LocalTaskCache @ 0x6a1f...          312 MB          78.4%   ← 一个对象独占 78%
└─ java.util.concurrent.ConcurrentHashMap              311 MB          78.2%
   └─ java.util.concurrent.ConcurrentHashMap$Node[]    310 MB          77.9%
      └─ 2,451,788 × com.x.canvas.model.TaskLog        ...
```

`LocalTaskCache` 这一个对象「支配」了 78% 的堆——它持有一个 `ConcurrentHashMap`，里面塞了 245 万个 `TaskLog` 从来不清。MAT 的「Path to GC Roots」还能告诉你它为什么没被回收（被哪个 `static` 引用着）。**Dominator Tree 看「谁占得多」，Path to GC Roots 看「为什么没被释放」**——这俩配合就能锁定泄漏。

### 解法

- **内存泄漏**：找到那个只进不出的容器，加上淘汰策略——本地缓存用 Caffeine 设最大条数和过期时间，或者把这种状态外置到 Redis 并设 TTL（见 [Redis 实战](/back-end/frontend-backend-guide/12-redis-in-practice)）。`static` 的可变集合是重点怀疑对象。
- **一次性大对象**：分页查、流式读、文件走 OSS 别进内存。
- **确实需要更多内存**：调大堆（`-Xmx`），但要先排除泄漏，否则只是把「几小时崩」变成「几天崩」。
- **频繁 Full GC 的根因**：多数是内存泄漏导致老年代占满后疯狂 GC 却回收不掉，按上面流程找泄漏源即可。

---

## 28.6 症状五：线程卡住 / 死锁

### 现象

接口一直转圈不返回、不报错；线程池任务堆积；CPU 和内存都正常（区别于症状三、四），但请求就是卡死。

### 可能原因

- **死锁**：两个线程各持一把锁、又互相等对方的锁，谁都不让步，永久卡住。
- **锁等待**：大量线程在抢同一把锁，排长队（不是死锁，但表现也是卡）。
- **等外部资源**：所有线程都卡在等下游响应、等数据库连接、等 Redis 锁释放。

> **前端类比**：前端是单线程，天然没有这种「两个线程互锁」的死锁。最接近的体感是 `await` 了一个永远不 resolve 的 Promise——代码停在那一行不动了。后端的死锁更复杂：是两段代码互相 `await` 对方手里的东西，彻底僵住。

### 排查命令

```bash
# 1) 抓线程栈。jstack 会自动检测并在末尾报告 Java 级死锁
jstack <pid> > /tmp/stack-svc-user.txt

# 2) 看有没有死锁（jstack 自动检测，搜这个关键字）
grep "Found one Java-level deadlock" /tmp/stack-svc-user.txt

# 3) 统计各状态的线程数，快速判断是「死锁/锁等待」还是「等外部」
grep "java.lang.Thread.State" /tmp/stack-svc-user.txt | sort | uniq -c
```

### 怎么读输出

第 3 步的状态统计先给你大方向：

```text
    180 java.lang.Thread.State: WAITING (parking)      ← 180 个线程在等锁/等条件，重灾
     12 java.lang.Thread.State: RUNNABLE
      6 java.lang.Thread.State: TIMED_WAITING (sleeping)
      2 java.lang.Thread.State: BLOCKED (on object monitor)
```

180 个 `WAITING` + `BLOCKED` 说明大量线程卡在锁上。如果是死锁，`jstack` 末尾会直接给你这一段（**它帮你把案子破好了，照着读就行**）：

```text
Found one Java-level deadlock:
=============================
"http-nio-8080-exec-7":
  waiting to lock monitor 0x00007f8b4c0058a0 (object 0x000000076ab12300, a com.x.user.Account),
  which is held by "http-nio-8080-exec-15"
"http-nio-8080-exec-15":
  waiting to lock monitor 0x00007f8b4c003120 (object 0x000000076ab12888, a com.x.user.Wallet),
  which is held by "http-nio-8080-exec-7"

Java stack information for the threads listed above:
"http-nio-8080-exec-7":
    at com.x.user.TransferService.transfer(TransferService.java:42)   ← 持有 Account，等 Wallet
    - waiting to lock <0x000000076ab12888> (a com.x.user.Wallet)
    - locked <0x000000076ab12300> (a com.x.user.Account)
"http-nio-8080-exec-15":
    at com.x.user.TransferService.transfer(TransferService.java:51)   ← 持有 Wallet，等 Account
    - waiting to lock <0x000000076ab12300> (a com.x.user.Account)
    - locked <0x000000076ab12888> (a com.x.user.Wallet)
```

怎么读这段死锁报告：

1. `Found one Java-level deadlock` 出现，**实锤死锁**，不用再猜。
2. 它直接告诉你死锁的两个线程：`exec-7` 拿着 `Account` 等 `Wallet`；`exec-15` 拿着 `Wallet` 等 `Account`——**经典的「加锁顺序相反」环路**。
3. 看 `Java stack information` 里两个线程都停在 `TransferService.java` 的加锁处——打开这两行，你会发现它在两个并发路径里以**相反的顺序**锁 `Account` 和 `Wallet`。

如果**不是**死锁，而是大量 `BLOCKED`：看 `BLOCKED` 线程都在 `waiting to lock <0x...同一个地址>`，再找 `- locked <同一个地址>` 是哪个线程持有的、它在干嘛——往往是某个线程持锁去做了慢操作（比如持锁调下游），把后面排队的全堵了。

### 解法

- **死锁**：统一加锁顺序（永远先锁 `Account` 再锁 `Wallet`），或用带超时的锁（`tryLock(timeout)`）让其中一方退让重试。根治原理见 [线程安全与锁](/back-end/frontend-backend-guide/16-thread-safety-locks)。
- **锁等待**：缩小锁粒度、缩短持锁时间、**绝对不要在持锁期间调下游/查 DB**。
- **都卡在等下游**：这其实是症状二的下游慢，给下游调用补超时（没有超时的远程调用是后端头号杀手）。
- **止血**：死锁线程不会自己解开，通常只能重启恢复，但**重启前先 `jstack` 把栈存下来**，否则没法复盘。

---

## 28.7 症状六：服务频繁重启（K8s）

### 现象

`svc-ai` 的 Pod 反复重启，`RESTARTS` 次数蹭蹭涨，服务时好时坏，前端偶发 502/503。

### 可能原因

- **被 OOMKilled**：容器内存超过 limit，被内核 OOM Killer 杀掉（注意：这和 Java 堆 OOM 不完全一样，是**整个容器**的内存超限）。
- **liveness 探针误杀**：服务其实没死，只是某次响应慢/启动慢，探针超时判定为「死了」就重启它，结果越重启越糟。
- **启动就崩**：配置错误、依赖连不上，进程 `exit code != 0` 直接退出。

> **前端类比**：有点像你 `npm run dev` 起的进程被 nodemon 反复重启——可能是代码报错一起来就崩（启动失败），也可能是某个健康检查脚本误判把好好的进程杀了（探针误杀）。区别是 K8s 这套是自动的、在线上跑的。K8s 探针机制见 [Kubernetes 实战](/back-end/frontend-backend-guide/24-kubernetes-in-practice)。

### 排查命令

```bash
# 1) 看 Pod 状态和重启次数
kubectl get pods -n ai-platform | grep svc-ai
# NAME                      READY   STATUS    RESTARTS        AGE
# svc-ai-6f8d9c7b5-xk2pq    1/1     Running   7 (3m12s ago)   2h   ← 重启 7 次

# 2) 看详情，重点是 Events 和 Last State（崩溃前发生了什么）
kubectl describe pod svc-ai-6f8d9c7b5-xk2pq -n ai-platform

# 3) 看上一个容器实例（崩溃前那个）的日志，--previous 是关键
kubectl logs svc-ai-6f8d9c7b5-xk2pq -n ai-platform --previous --tail=100
```

### 怎么读输出

`kubectl describe pod` 输出里直奔两块：`Last State` 和 `Events`。

```text
Containers:
  svc-ai:
    State:          Running
      Started:      Mon, 01 Jun 2026 14:35:02 +0800
    Last State:     Terminated
      Reason:       OOMKilled                      ← 被内存杀了
      Exit Code:    137                             ← 137 = 128 + 9(SIGKILL)，OOM 的典型退出码
      Started:      Mon, 01 Jun 2026 14:20:11 +0800
      Finished:     Mon, 01 Jun 2026 14:34:58 +0800
    Restart Count:  7
    Limits:
      memory:  1Gi                                  ← 容器内存上限只有 1Gi
Events:
  Type     Reason     Age                 From     Message
  ----     ------     ----                ----     -------
  Warning  BackOff    2m (x12 over 30m)   kubelet  Back-off restarting failed container
```

怎么读：

- `Reason: OOMKilled` + `Exit Code: 137`：**容器内存超过 `Limits: 1Gi` 被杀**。这通常意味着 Java 堆 `-Xmx` 设得和容器 limit 不匹配（比如 `-Xmx900m` 但加上 JVM 本身的元空间、线程栈、堆外内存，总和超过 1Gi）。
- 如果 `Reason: Error` + `Exit Code: 1`：是**程序自己启动失败/抛异常退出**，去看 `--previous` 日志找启动期异常（多半是配置错误、连不上 DB，见 [配置与环境](/back-end/frontend-backend-guide/25-config-and-env)）。
- 如果 `Events` 里有 `Liveness probe failed: ... timeout`：是**探针超时误杀**，看下文。

退出码速查：

| Exit Code | 含义 | 方向 |
| --- | --- | --- |
| 0 | 正常退出 | 一般是被滚动更新替换，不是故障 |
| 1 | 程序异常退出 | 看 `--previous` 日志找启动异常 |
| 137 | 被 SIGKILL（128+9） | 多为 OOMKilled，查内存 limit 与 `-Xmx` |
| 143 | 被 SIGTERM（128+15） | 优雅停机/被驱逐，通常正常 |

**探针误杀**的典型 Events：

```text
Events:
  Warning  Unhealthy  1m (x8)  kubelet  Liveness probe failed: Get "http://10.1.2.3:8080/actuator/health":
                                        context deadline exceeded (Client.Timeout exceeded while awaiting headers)
  Normal   Killing    1m       kubelet  Container svc-ai failed liveness probe, will be restarted
```

这说明 `/actuator/health` 在探针的超时时间内没返回。常见原因：服务启动慢（还在加载就被探了）、或健康检查本身去查了慢的下游。**关键判断：服务其实是活的，只是探针太敏感把它当死的杀了，于是陷入「越重启越没法好好启动」的死循环。**

### 解法

- **OOMKilled**：让 Java 感知容器内存（JDK 17 默认开 `-XX:+UseContainerSupport`，用 `-XX:MaxRAMPercentage=75.0` 代替写死 `-Xmx`），并给容器 limit 留出堆外开销；如果是真泄漏，转 [28.5](#_28-5-症状四-内存暴涨-oom)。
- **探针误杀**：调大 `initialDelaySeconds`（给足启动时间）、`timeoutSeconds`、`failureThreshold`；liveness 探针**不要**依赖下游（健康检查只查「我自己活没活」，别去查 DB/下游，否则下游一抖你就被重启）；区分 liveness（活没活，挂了重启）和 readiness（能不能接流量，没好就摘流量但不重启）。
- **启动失败**：照 `--previous` 日志里的异常修配置/依赖。

---

## 28.8 症状七：数据不对 / 偶发问题

### 现象

最难的一类：不报错、不慢、不崩，就是**数据对不上**。用户配额莫名其妙少扣或多扣了；一个生图任务状态卡在 `PROCESSING` 再也不变；同一笔支付偶尔记了两条。复现不出来，今天有明天没。

### 可能原因（后端「玄学 bug」四大金刚）

- **并发竞态**：两个请求同时读改写同一份数据，互相覆盖（就是 [后端思维](/back-end/frontend-backend-guide/01-backend-mindset) 里 `todayCount++` 那个坑的真实版）。
- **重复消费 / 不幂等**：MQ 消息被消费了两次，没做幂等，于是扣了两次费、发了两条记录。
- **时区 / 序列化**：时间差 8 小时（UTC vs 北京时间）、`Long` 类型 ID 传到前端 JS 精度丢失、`null` 字段序列化策略不一致。
- **缓存与 DB 不一致**：更新了 DB 没更新/没删 Redis 缓存，读到旧数据。

> **前端类比**：就像你遇到「偶发的 state 不对」——多半是某个 `useEffect` 依赖写错、或两个异步请求回来的顺序不固定（竞态）。后端的偶发数据问题，根子也常是「时序」和「重复」，只是发生在多线程/多副本/多次消费之间，更难肉眼复现。

### 排查命令

偶发问题没法守株待兔，核心套路是 **「对照 + 蹲守」**：先用现有日志和 DB 对照确认事实，再加日志埋点等它下次出现。

```bash
# 1) 对照：把这条数据相关的所有日志按业务 id 全捞出来，按时间排
grep "PAY20260601001" /var/log/svc-user/app.log | sort

# 2) 怀疑重复消费：看这条消息是不是被消费了两次（两条 consume 日志）
grep "consume.*PAY20260601001" /var/log/svc-user/app.log
```

再去 DB 里把出问题那条数据捞出来当事实基准（怀疑重复就看是不是查出了两条）：

```sql
SELECT id, user_id, amount, status, create_time, update_time
FROM payment WHERE order_no = 'PAY20260601001';
```

如果 DB 里 `create_time` 是 `14:00:00`、而日志里同一动作打的是 `22:00:00`，差 8 小时 → 几乎可以锁定是**时区问题**。

### 怎么读输出

「重复消费」的日志摆在一起，时序一目了然：

```text
14:23:01.100 [traceId=aaa] [consumer-1] 收到支付成功消息, orderNo=PAY20260601001
14:23:01.250 [traceId=aaa] [consumer-1] 扣减配额成功, userId=10086, amount=10
14:23:08.400 [traceId=bbb] [consumer-2] 收到支付成功消息, orderNo=PAY20260601001   ← 同一笔，7 秒后又来一次！
14:23:08.520 [traceId=bbb] [consumer-2] 扣减配额成功, userId=10086, amount=10        ← 又扣了一次！
```

同一个 `orderNo` 被两个不同 traceId、不同 consumer 各处理了一次——**MQ 重投了消息，而消费端没做幂等**，于是扣了两次。RocketMQ 这类 MQ 的「至少一次」投递语义决定了重复几乎一定会发生，幂等是消费端的责任（见 [MQ 可靠性](/back-end/frontend-backend-guide/33-mq-reliability)）。

「并发竞态」则要靠 DB 的 `update_time` + 日志时间戳拼时序：两条请求的日志时间几乎重叠（毫秒级），都先读到旧值再各自写回，后写的覆盖了先写的——这就是「读-改-写」竞态，需要靠数据库行锁、乐观锁（版本号）或 Redis 分布式锁解决（见 [事务与一致性](/back-end/frontend-backend-guide/11-transactions-consistency) 和 [线程安全与锁](/back-end/frontend-backend-guide/16-thread-safety-locks)）。

### 解法

- **并发竞态**：别在内存里「读-改-写」共享数据。用 DB 的原子更新（`UPDATE ... SET quota = quota - 10 WHERE quota >= 10`）、乐观锁版本号、或 Redis 原子操作 / 分布式锁。
- **重复消费**：消费端做**幂等**——用 `orderNo` 当唯一键，处理前先查「这条处理过没」（DB 唯一索引 + 插入冲突即跳过，或 Redis `setnx` 标记）。这是 MQ 场景的必修课，详见 [MQ 可靠性](/back-end/frontend-backend-guide/33-mq-reliability)。
- **时区**：全链路统一用 UTC 存储、明确时区做展示转换；数据库、JVM（`-Duser.timezone`）、序列化框架时区配一致。
- **大 Long 精度**：`Long` 类型 ID 序列化成字符串给前端（JS `Number` 超过 2^53 会丢精度）。
- **缓存不一致**：更新 DB 后删缓存（Cache Aside），或给缓存设短 TTL 兜底（见 [Redis 实战](/back-end/frontend-backend-guide/12-redis-in-practice)）。
- **复现不出来的偶发问题**：核心手段就是**加日志 + traceId 蹲守**——在怀疑的代码路径上把关键变量、加锁前后、消费入口都打上带 traceId 的日志，部署后等它下次复发，再用上面的「对照」流程一击命中。**偶发问题不靠运气复现，靠埋好的日志守到它。**

---

## 28.9 一张图收尾：拿到症状先做什么

把全章压成一条决策流，贴工位上：

```text
线上告警/前端报障
   │
   ├─ 报 500 / 有异常栈 ──────→ grep traceId 看异常栈 + Caused by   →【28.2】
   │
   ├─ 慢 / 超时 ─────────────→ 分段耗时定位是哪一层(DB/下游/锁/GC/连接池)→【28.3】
   │
   ├─ CPU 100% ─────────────→ top -Hp → printf %x → jstack 找线程   →【28.4】
   │
   ├─ 内存涨/OOM ────────────→ jmap -histo:live + GC日志 + MAT 支配树 →【28.5】
   │
   ├─ 卡住不返回 / 死锁 ──────→ jstack 找 BLOCKED / deadlock 段       →【28.6】
   │
   ├─ Pod 反复重启 ──────────→ kubectl describe 看退出码/Last State  →【28.7】
   │                            + logs --previous
   │
   └─ 数据不对 / 偶发 ────────→ 日志对照 DB + 怀疑并发/幂等/时区       →【28.8】
                                + 加日志 traceId 蹲守

通用铁律：动手重启/扩容止血之前，先把现场抓下来(jstack / jmap / kubectl logs --previous)！
```

> **贯穿全章的一条命**：**重启能止血，但会毁灭现场。** 后端排障最痛的不是问题难，而是「重启完恢复了，但下次还炸，且永远抓不到现场」。养成肌肉记忆：先 dump、再重启。

---

## 小结

- 线上排障靠的是**条件反射式的清单**，不是临场发挥。对号入座七大症状：500、变慢、CPU 100%、OOM、卡死/死锁、频繁重启、数据不对。
- 每类症状都有**固定工具链**：异常看日志 `grep traceId` 抓 `Caused by`；CPU 用 `top -Hp` → `printf %x` → `jstack`；内存用 `jmap` + MAT 支配树；卡死用 `jstack` 看 `BLOCKED`/deadlock；K8s 重启用 `kubectl describe` 看退出码与 `--previous` 日志。
- 变慢一定要**先分层定位**（DB / 下游 / 锁 / GC / 连接池）再深挖，并能用「哪个接口、慢多少、卡在哪层、谁的锅、怎么止血、怎么根治」的话术汇报。
- 偶发/数据问题靠**对照 DB + 加日志 traceId 蹲守**，根因常是并发竞态、重复消费不幂等、时区/序列化、缓存不一致。
- **动手止血前先抓现场**（dump/日志），这是后端排障的第一铁律——重启能救命，也会毁灭证据。

### 自测

1. 用户反馈一个生图接口偶发返回 500。你拿到了 `traceId`。请写出你接下来执行的命令，以及看到异常栈后，你重点关注哪几行、为什么 `Caused by` 这么重要。
2. `svc-ai` 某个 Pod CPU 飙到 100% 但内存正常。请按顺序写出从 `top` 到定位具体代码行的完整命令链（含 `%x` 转换那一步），并说明如何区分「死循环」和「单纯计算量大」。
3. 同一笔支付偶尔被扣了两次配额，复现不出来。请说明你会先做什么来确认事实（对照什么）、最可能的根因是什么、消费端应该怎么改才能根治。

### 下一章

这章你已经知道每类症状该用哪个工具，但 `jstack`、`jmap`、`jstat`、`arthas`、`kubectl` 这些工具本身怎么装、各有什么招式、还有哪些没提到的利器——下一章 [诊断工具箱](/back-end/frontend-backend-guide/29-diagnostic-toolbox) 把它们一件件拆开讲透。

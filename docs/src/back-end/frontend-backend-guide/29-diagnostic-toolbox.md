# 排查工具箱

> 上一章 [排查实战手册](/back-end/frontend-backend-guide/28-troubleshooting-playbook) 给了你「遇到某类故障怎么一步步定位」的套路，这一章是配套的**工具说明书**：每个工具回答「什么场景用它 + 最常用的命令 + 一段真实示例」。
>
> 前端类比：你的 Chrome DevTools 是一整套面板——Console、Network、Performance、Memory。后端没有这么一个统一面板，而是一**箱独立的命令行工具**，每个对应 DevTools 的一块功能。这章就是带你认全这箱工具，知道什么时候掏哪一把。

把这章当字典用：平时扫一眼建立印象，真出事了按「现象 → 首选工具」速查表（最后一节）回来翻。所有命令都假设你已经 SSH 登上了服务器、或者已经 `kubectl exec` 进了容器（怎么进去看 [Linux 服务器基础](/back-end/frontend-backend-guide/21-linux-server-essentials) 和 [Kubernetes 实战](/back-end/frontend-backend-guide/24-kubernetes-in-practice)）。

---

## 29.1 工具箱全景：分四类

后端排查工具大致分四类，对应你想看的不同层面：

```text
                          你想看什么？
   ┌──────────────────┬──────────────────┬──────────────────┬─────────────────┐
   │  JVM 进程内部      │   操作系统层面     │     网络链路       │    接口本身      │
   │  （线程/堆/GC）    │  （CPU/内存/磁盘） │  （包到底通没通）  │  （请求/响应）   │
   ├──────────────────┼──────────────────┼──────────────────┼─────────────────┤
   │ jps  jstack jmap  │ top/htop  free    │ ss / netstat     │ curl -v         │
   │ jstat jcmd jinfo  │ df   iostat       │ lsof             │ Postman/Apifox  │
   │ Arthas（神器）     │                   │ tcpdump+Wireshark│                 │
   └──────────────────┴──────────────────┴──────────────────┴─────────────────┘
       JDK 自带         系统自带            网络抓包             接口工具
```

- **JDK 自带的 `j` 系列**：看 JVM 进程内部——线程在干嘛、堆里有什么、GC 频不频繁。装了 JDK 就有，无需额外安装，是线上排 Java 问题的第一选择。
- **Arthas**：阿里开源的线上诊断神器，把上面那堆 `j` 命令的能力整合成一个交互式控制台，还能不重启就看方法耗时、看入参返回、反编译线上代码。**重点推荐，单独一节讲。**
- **系统工具**：`top`/`free`/`df` 等，看的是「这台机器」的 CPU、内存、磁盘、网络连接，判断瓶颈在不在 OS 层。
- **网络与接口工具**：`tcpdump` 抓包看「包到底有没有发出去/对端有没有回」，`curl -v` 直接打接口看原始请求响应。

---

## 29.2 JDK 自带：`j` 系列命令

这些工具在 JDK 的 `bin` 目录下，容器里只要装的是 JDK（不是精简版 JRE）就能直接用。它们都需要一个参数：目标 Java 进程的 **PID**。所以第一步永远是 `jps`。

### jps：列出本机所有 Java 进程

**什么场景用它**：任何 Java 排查的第一步——先拿到要诊断的服务的 PID。

```bash
# -l 显示主类全名 / jar 路径，-v 显示启动 JVM 参数
jps -l
```

预期输出：

```text
12345 org.springframework.boot.loader.JarLauncher
12346 /app/svc-canvas.jar
12347 /app/svc-ai.jar
98765 sun.tools.jps.Jps
```

怎么读：每行是「PID + 主类/jar」。这里 `svc-canvas` 的 PID 是 `12346`。最后那个 `Jps` 是 `jps` 命令自己，忽略。如果一个容器里只跑一个服务（推荐做法），通常就一个业务 PID，很好认。

> 前端类比：相当于 `ps` 版的「打开任务管理器，找到我那个 node 进程」。后面所有工具都要喂这个 PID 进去。

### jstack：打印线程栈，抓死锁和卡顿

**什么场景用它**：接口卡住不返回、CPU 飙高、怀疑死锁。`jstack` 把进程里**每个线程此刻正卡在哪一行代码**全打出来。

```bash
jstack 12346 > /tmp/canvas-stack.txt
```

输出片段（节选两个线程）：

```text
"http-nio-8080-exec-37" #142 daemon prio=5 tid=0x... nid=0x3a2f WAITING on condition
   java.lang.Thread.State: WAITING (parking)
        at jdk.internal.misc.Unsafe.park(Native Method)
        at java.util.concurrent.locks.LockSupport.park(LockSupport.java:341)
        at com.example.svc.canvas.service.TaskService.submit(TaskService.java:88)
        ...

Found one Java-level deadlock:
=============================
"http-nio-8080-exec-12":
  waiting to lock monitor 0x... (object 0x...a, a java.lang.Object),
  which is held by "http-nio-8080-exec-25"
"http-nio-8080-exec-25":
  waiting to lock monitor 0x... (object 0x...b, a java.lang.Object),
  which is held by "http-nio-8080-exec-12"
```

怎么读：

- 线程名 `http-nio-8080-exec-37` 是 Tomcat 工作线程。如果**大量** exec 线程都卡在 `TaskService.submit` 的同一行，说明请求堆在这里了——通常是下游慢、锁竞争或等连接池。
- `Found one Java-level deadlock` 是 jstack 直接帮你点出的**死锁**：exec-12 和 exec-25 互相等对方手里的锁，谁也动不了。这是线上接口大面积超时的经典原因。

结论：先看「有没有 deadlock」，再看「线程都堆在哪个方法」。死锁要改加锁顺序（见 [线程安全与锁](/back-end/frontend-backend-guide/16-thread-safety-locks)），大量线程堆在远程调用上要查超时设置（见 [连接池](/back-end/frontend-backend-guide/13-connection-pools)）。

> 前端类比：jstack 就像把所有「正在 await 的 Promise」此刻卡在哪一行代码全列出来——只不过后端是真·多线程，能真死锁。

### jmap：看堆里有什么、把堆 dump 下来

**什么场景用它**：内存涨得快、怀疑内存泄漏、要排 OOM（配合 [OOM 与内存泄漏](/back-end/frontend-backend-guide/20-oom-memory-leak)）。

```bash
# 1) 堆直方图：按对象占用内存排序，快速看「谁吃了内存」。:live 会先触发一次 GC 只统计活对象
jmap -histo:live 12346 | head -n 15
```

```text
 num     #instances         #bytes  class name
----------------------------------------------
   1:       2103442      201930432  [B                 (byte[]，常是图片/字符串底层)
   2:       1980331       63370592  java.lang.String
   3:        984221       47242608  com.example.svc.canvas.entity.TaskRecord
   4:        512000       16384000  java.util.HashMap$Node
```

怎么读：第 3 行很可疑——`TaskRecord` 有近百万个实例占了 47MB。如果你的在线任务数远没这么多，说明这些任务对象**该回收却没回收**（典型泄漏：被某个 static 集合长期持有）。

```bash
# 2) 完整堆 dump，存成文件拿回本地用 MAT / VisualVM 分析
jmap -dump:format=b,file=/tmp/canvas-heap.hprof 12346
```

预期：生成 `/tmp/canvas-heap.hprof`（可能几百 MB 到几 GB）。注意 dump 会让进程**短暂停顿**（堆越大停顿越久），高峰期慎用。dump 文件拿回本地用 Eclipse MAT 打开，看「Dominator Tree」「Leak Suspects」就能找到是谁攥着这堆对象不放。

> 前端类比：`jmap -histo` ≈ DevTools Memory 面板的「按构造函数分组的对象列表」；`jmap -dump` ≈ 点「Take heap snapshot」存下来离线分析。

### jstat：盯 GC，判断是不是「一直在回收」

**什么场景用它**：接口偶发卡顿、CPU 莫名偏高、怀疑频繁 GC 或内存快满了。`jstat` 每隔一段时间打印一行 GC 统计，让你**动态观察**而不是看一个静止快照。

```bash
# -gcutil 看各区使用百分比，1000 = 每 1000ms 打一行，连打 5 行
jstat -gcutil 12346 1000 5
```

```text
  S0     S1     E      O      M     CCS    YGC     YGCT    FGC    FGCT     GCT
  0.00  31.20  68.55  72.10  94.88  91.20    412    8.123      3    1.402    9.525
  0.00  31.20  91.34  72.10  94.88  91.20    412    8.123      3    1.402    9.525
 28.10   0.00   5.21  72.45  94.88  91.20    413    8.140      3    1.402    9.542
 28.10   0.00  40.02  72.45  94.88  91.20    413    8.140      3    1.402    9.542
  0.00  29.88  12.33  96.80  94.91  91.20    414    8.165      4    2.901   11.066
```

怎么读（重点看 `O`、`FGC`、`FGCT` 三列）：

- `E`（Eden 区）从 68% 涨到 91% 又掉回 5%——这是正常的 Young GC（新生代回收），健康。
- `O`（老年代）最后一行从 72% 直接跳到 96%，同时 `FGC`（Full GC 次数）从 3 变成 4、`FGCT`（Full GC 累计耗时）一下涨了 1.5 秒——**这是危险信号**：老年代快满，触发了一次耗时很长的 Full GC，期间服务几乎停顿（STW）。
- 如果你看到 `O` 长期 95%+、`FGC` 蹭蹭涨，基本就是内存要爆了/有泄漏，接下来就该上 `jmap -histo` 看谁占的内存。

结论：`jstat -gcutil <pid> 1000` 是确认「是不是 GC 问题」最快的一招。GC 原理与各区含义见 [垃圾回收](/back-end/frontend-backend-guide/19-garbage-collection)。

> 前端类比：像 DevTools Performance 面板里那条不断刷新的内存/GC 曲线，只不过是文本版、实时滚动。

### jcmd：一个命令顶一票（推荐统一用它）

**什么场景用它**：jcmd 是后来的「全能命令」，jstack/jmap/jinfo 能干的它基本都能干，且更稳定。记不住一堆命令时，记 jcmd 就够。

```bash
jcmd 12346 Thread.print          # 等价于 jstack：打印所有线程栈（含死锁检测）
jcmd 12346 GC.heap_info          # 看堆各区大小与使用量
jcmd 12346 VM.flags              # 看这个 JVM 实际生效的所有参数（排「为什么堆这么小」时极有用）
jcmd 12346 GC.class_histogram    # 等价于 jmap -histo
jcmd 12346 help                  # 列出这个进程支持的所有诊断命令
```

`VM.flags` 输出示例：

```text
-XX:CICompilerCount=4 -XX:InitialHeapSize=536870912 -XX:MaxHeapSize=2147483648
-XX:+UseG1GC -XX:MaxMetaspaceSize=268435456 ...
```

怎么读：`MaxHeapSize=2147483648` = 2GB，`UseG1GC` 表示用的是 G1 垃圾回收器。线上排「OOM 但容器明明给了 8G 内存」时，常常就是这里发现 `MaxHeapSize` 只配了 2G——堆压根没用满容器内存。配置与环境变量怎么传进 JVM 见 [配置与环境](/back-end/frontend-backend-guide/25-config-and-env)。

### jinfo：查/改运行中进程的参数

**什么场景用它**：临时确认或动态调整某个 JVM/系统参数，不重启进程。

```bash
jinfo -flag MaxHeapSize 12346        # 查某个参数当前值
jinfo -flag +PrintGCDetails 12346    # 运行时临时打开 GC 详细日志（仅限可动态修改的参数）
```

注意：能动态改的参数有限，多数参数（如堆大小）只能启动时定。jinfo 日常更多用来「确认值」，改还是改启动配置。

---

## 29.3 Arthas：线上诊断神器（重点）

[Arthas](https://arthas.aliyun.com/) 是阿里开源的 Java 线上诊断工具，把上面 `j` 系列的能力整合成一个**交互式控制台**，而且有几样 `j` 系列做不到的杀手锏：**不重启、不改代码，就能看到方法的实时耗时、入参、返回值、甚至反编译线上正在跑的代码**。线上排「这个接口为什么慢」，它几乎是唯一能在 5 分钟内给答案的工具。

> 前端类比：想象你能在**线上**给任意一个函数临时打一行 `console.time` + 打印 `arguments` + 打印返回值，看完即撤、不留痕、不重新部署——这就是 Arthas 在后端干的事。

### 启动：attach 到目标进程

```bash
# 下载（一次即可）
curl -O https://arthas.aliyun.com/arthas-boot.jar
# 启动，它会列出本机 Java 进程让你选，或直接带 PID
java -jar arthas-boot.jar 12346
```

attach 成功后进入交互控制台，提示符变成 `[arthas@12346]$`，接下来输命令。退出用 `quit`（只断开当前会话）或 `stop`（彻底关闭 Arthas）。

### 常用命令速查

| 命令 | 干什么 | 对应的 j 系列/DevTools |
| --- | --- | --- |
| `dashboard` | 实时总览：线程、内存、GC、CPU 一屏看全 | top + jstat 合体 |
| `thread -n 3` | 列出最忙的 3 个线程及其栈 | jstack 的「找最吃 CPU 的线程」 |
| `thread -b` | 检测并打印死锁 | jstack 的 deadlock 检测 |
| `trace 类 方法` | 追踪方法内部每个调用的耗时，定位慢在哪一步 | Performance 火焰图（聚焦单方法） |
| `watch 类 方法` | 观察方法的入参/返回值/抛出的异常 | 给函数打 console.log(args, return) |
| `jad 类` | 反编译，看线上真正在跑的代码（确认部署的是不是这版） | Sources 面板看线上源码 |
| `stack 类 方法` | 看这个方法是被谁调用的（调用来源） | 调用栈反查 |

`dashboard` 一屏长这样（节选）：

```text
ID   NAME                    GROUP     PRIORITY  STATE    %CPU   TIME
142  http-nio-8080-exec-37   main      5         RUNNABLE 47.21  2:13
158  RocketMQ-Consumer-3     main      5         WAITING  0.10   0:08
...
Memory             used    total   max    usage   GC
heap               1234M   2048M   2048M  60.27%  gc.g1_young.count  413
g1_old_gen         890M    1500M   1500M  59.33%  gc.g1_old.count    4
```

怎么读：一眼看出 exec-37 这个线程吃了 47% CPU、累计跑了 2 分 13 秒——CPU 飙高时第一屏就锁定到具体线程，再用 `thread 142` 看它卡在哪。

### 实战：用 trace 定位「提交生图任务为什么慢」

**症状**：前端反馈点「提交生图」后要转 5 秒圈才返回，但平时只要 1 秒。`svc-canvas` 的 `TaskService.submit` 内部调了好几个下游，不知道慢在哪一环。

**可复制的命令**（在 attach 上 `svc-canvas` 的 Arthas 控制台里）：

```bash
# 追踪 submit 方法，只抓耗时 > 1000ms 的调用（#cost > 1000），抓到 5 次就停
trace com.example.svc.canvas.service.TaskService submit '#cost > 1000' -n 5
```

**预期输出/日志样例**：

```text
`---ts=2026-06-01 14:22:31; [cost=5021.330000ms] com.example.svc.canvas.service.TaskService:submit()
    `---[5018.872000ms] com.example.svc.canvas.service.TaskService:submit()
        +---[2.110000ms] com.example.cpt.api.user.UserClient:getQuota()       #88
        +---[3.940000ms] com.example.cpt.redis.RedisLock:tryLock()            #95
        +---[4998.221000ms] com.example.cpt.api.ai.AiClient:generate()        #102
        +---[12.330000ms] com.example.cpt.mongodb.TaskRepository:save()       #110
        `---[1.080000ms] com.example.cpt.rocketmq.TaskProducer:send()         #118
```

**怎么读这段输出**：

- 顶层 `cost=5021ms` 是 `submit` 总耗时。
- 树状结构是它内部依次调用的方法，每行前面 `[xxxms]` 是各自耗时，后面 `#102` 是源码行号。
- 一眼定位：`AiClient.generate()` 占了 **4998ms**，其余几步都是个位数毫秒。慢的根因就是调 `svc-ai` 这一环。

**结论**：问题不在 `svc-canvas` 自己，而在它调用的 `svc-ai`。下一步切到 `svc-ai` 上继续 `trace` 它的生图方法，层层下钻直到找到真正耗时的那行（可能是模型推理本身慢，也可能是 `svc-ai` 又在等它的下游）。这套「逐层 trace 下钻」就是定位跨服务慢调用的标准动作。

如果想看 `generate()` 那次调用到底传了什么参数、返回了什么，用 `watch`：

```bash
# -x 2 展开两层，{params,returnObj} 表示同时看入参和返回值
watch com.example.cpt.api.ai.AiClient generate '{params,returnObj}' -x 2 -n 1
```

线程池调优、慢调用与并发瓶颈的系统排查见 [性能与并发](/back-end/frontend-backend-guide/31-performance-concurrency)；trace 出来的线程栈含义结合 [线程池与 Executor](/back-end/frontend-backend-guide/15-thread-pools-executor) 一起看。

---

## 29.4 系统工具：看机器本身（每个一句话）

JVM 内部没问题，就要往下看「这台机器」是不是到瓶颈了。这些是 Linux 自带命令，详解见 [Linux 服务器基础](/back-end/frontend-backend-guide/21-linux-server-essentials)，这里一句话过：

| 工具 | 看什么 | 最常用 |
| --- | --- | --- |
| `top` / `htop` | CPU、内存占用排行，哪个进程吃资源（`htop` 是带颜色的交互版） | `top -Hp 12346` 看某进程内**各线程**的 CPU |
| `free` | 整机内存够不够、还剩多少、是否在用 swap | `free -h`（`-h` 人类可读） |
| `df` | 磁盘满没满（磁盘 100% 会导致写日志/落库全部失败） | `df -h` |
| `ss` / `netstat` | 看端口在不在监听、有多少 TCP 连接、连接卡在什么状态 | `ss -tnp` 配合 grep 过端口 |
| `lsof` | 看进程打开了哪些文件/socket，排「too many open files」 | `lsof -p 12346` 配合 wc -l 数句柄 |
| `iostat` | 看磁盘 I/O 繁不繁忙（数据库慢常和这有关） | `iostat -x 1` |

一个高频组合——CPU 飙高时定位到具体 Java 线程：

```bash
top -Hp 12346          # 找出占 CPU 最高的线程，记下它的 PID，比如 12399
printf '%x\n' 12399    # 把十进制线程 PID 转成十六进制 → 3a4f
```

```text
3a4f
```

然后在 `jstack 12346` 的输出里搜 `nid=0x3a4f`，就能找到这个吃 CPU 的线程**正卡在哪行代码**。（Arthas 的 `thread -n 3` 一步就做完了这一串，所以装了 Arthas 优先用它。）

> 前端类比：`top` ≈ 操作系统级的任务管理器/活动监视器；`df` 满了 ≈ 浏览器报 `QuotaExceededError` 写不进 localStorage，只是后端磁盘满了会让整个服务瘫痪。

---

## 29.5 网络抓包：tcpdump + Wireshark（入门即可）

**什么场景用它**：当你怀疑「请求到底有没有真的发出去」「对端到底有没有回」时——比如 `svc-canvas` 调 `svc-ai` 一直超时，但 `svc-ai` 的日志里压根没收到请求，那问题可能在网络层（连不上、被防火墙挡、DNS 解析错），日志已经帮不了你，得直接看网线上跑的包。

`tcpdump` 在服务器上抓包存成文件，再拿到本地用图形化的 **Wireshark** 分析。

```bash
# 抓所有网卡(any)上、端口 8080 的包，写入文件。Ctrl+C 停止
tcpdump -i any port 8080 -w /tmp/canvas-8080.pcap
```

抓的时候终端会显示：

```text
tcpdump: listening on any, link-type LINUX_SLL2 (Linux cooked v2), capture size 262144 bytes
^C
1287 packets captured
1342 packets received by filter
0 packets dropped by kernel
```

怎么读：`1287 packets captured` 说明这段时间端口 8080 上确实有 1287 个包来往——如果你复现问题期间这个数字是 `0`，说明请求根本没到这台机器，问题在更前面（网关、DNS、安全组）。把 `/tmp/canvas-8080.pcap` 下载回本地，用 Wireshark 打开，重点看：

- 有没有 `SYN` 却没有 `SYN, ACK`——TCP 三次握手没完成，说明连不上对端（端口没开/被防火墙拦）。
- 有 `[RST]`（reset）——连接被对端强制重置。
- 请求发出去了但迟迟没有响应包——对端收到了但处理不过来（这时回到 Arthas 去 trace 对端）。

TCP 握手、HTTP 报文结构这些基础概念见 [面向后端的网络基础](/back-end/frontend-backend-guide/22-networking-for-backend)，安全相关的抓包注意事项可参考 [Web 安全](/front-end/the-basics/network-basics/webSafety)。日常排查 tcpdump 入门到这够了——它是「日志都没法解释时」的最后一招物证。

> 前端类比：tcpdump + Wireshark 就是 DevTools Network 面板的底层硬核版。Network 面板告诉你「这个请求 pending / 失败了」，tcpdump 告诉你「TCP 包到底走到了哪一步、是握手就失败还是发出去没人回」。

---

## 29.6 接口工具：curl 与 Postman/Apifox

**什么场景用它**：绕开前端，直接以最原始的方式打一个接口，确认「到底是后端的问题还是前端的问题」。这也是后端「所有入参都不可信」的现实体现——任何人都能用 curl 直接打你的接口。

`curl -v`（verbose）会把完整的请求头、响应头、状态码全打出来：

```bash
curl -v -X POST 'http://svc-gateway/svc-canvas/task/submit' \
  -H 'Authorization: Bearer eyJhbGciOi...' \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"a cat astronaut","size":"1024x1024"}'
```

预期输出（节选）：

```text
> POST /svc-canvas/task/submit HTTP/1.1
> Host: svc-gateway
> Authorization: Bearer eyJhbGciOi...
> Content-Type: application/json
>
< HTTP/1.1 200 OK
< Content-Type: application/json
< traceId: 7f3a1c9e2b6d4a01
<
{"code":0,"msg":"ok","data":{"taskId":"task_20260601_8842"}}
```

怎么读：

- `>` 开头是你发出去的请求，`<` 开头是服务端返回的响应。
- `< HTTP/1.1 200 OK` 是 HTTP 状态码——200 通过，401 是没带/带错 token（去查 `svc-auth`），500 是服务端报错（去查那个服务的日志，用响应头里的 `traceId` 串起整条链路）。
- 响应体 `{"code":0,"msg":"ok",...}` 是统一响应 `RtData`——注意 HTTP 200 不代表业务成功，要看 `code`：业务失败时 HTTP 仍可能是 200 但 `code` 非 0、`msg` 是错误描述。

**Postman / Apifox**：curl 的图形化版本，适合存接口集合、管环境变量（dev/prod 切换）、团队共享。日常调试用 Postman/Apifox 顺手，但**在服务器上排障时用 curl**——因为服务器没有图形界面，curl 是唯一选择，而且它能直接在「问题机器内部」发请求，排除掉网络中间层的干扰。

> 前端类比：`curl -v` 就是把 DevTools Network 面板里某个请求的「右键 → Copy as cURL」拿到服务器上重放，且能看到比浏览器更原始的完整头信息。

接口怎么设计、状态码与 `RtData` 约定见 [API 设计](/back-end/frontend-backend-guide/34-api-design)。

---

## 29.7 速查表：现象 → 首选工具

真出事时，按「你观察到的现象」找该掏哪把工具：

| 现象 | 首选工具 | 关键命令 |
| --- | --- | --- |
| 接口卡住不返回 / 大量超时 | jstack（或 Arthas `thread`） | `jstack <pid>` 看线程堆在哪，`thread -b` 查死锁 |
| CPU 持续 100% | top -Hp + jstack（或 Arthas） | `top -Hp <pid>` 找线程 → `jstack` 搜 nid；`thread -n 3` |
| 内存持续上涨 / 疑似泄漏 | jmap + jstat | `jstat -gcutil <pid> 1000` 看 GC，`jmap -histo:live` 看谁占内存 |
| 频繁 Full GC / 偶发停顿 | jstat | `jstat -gcutil <pid> 1000` 盯 `O` 和 `FGC` |
| OOM 已经发生 | jmap dump + MAT | `jmap -dump:format=b,file=x.hprof <pid>`，拿回本地分析 |
| 某个接口/方法变慢，不知慢在哪 | Arthas `trace` | `trace 类 方法 '#cost>1000'` 逐层下钻 |
| 怀疑线上代码不是这版 / 看不到源码 | Arthas `jad` | `jad 全类名` 反编译 |
| 想看某方法的入参/返回/异常 | Arthas `watch` | `watch 类 方法 '{params,returnObj,throwExp}'` |
| 整机变慢，怀疑不是 JVM 的事 | top / free / df / iostat | `top`、`free -h`、`df -h`、`iostat -x 1` |
| 端口连不上 / 连接数异常 | ss / lsof | `ss -tnp` 过端口、`lsof -p <pid>` |
| 「请求到底通没通」说不清 | tcpdump + Wireshark | `tcpdump -i any port <port> -w x.pcap` |
| 怀疑是前端还是后端的锅 | curl -v | `curl -v <url>` 看状态码、响应头、RtData |

配套的「按故障类型走完整排查流程」见 [排查实战手册](/back-end/frontend-backend-guide/28-troubleshooting-playbook)，先建立排查思路见 [问题排查方法论](/back-end/frontend-backend-guide/27-troubleshooting-methodology)。常用命令的完整速查见 [命令速查表](/back-end/frontend-backend-guide/91-command-cheatsheet)。

---

## 小结

- 排查工具分四类：**JDK 自带 `j` 系列**（看 JVM 内部）、**Arthas**（线上诊断神器）、**系统工具**（看机器）、**网络/接口工具**（看链路与请求）。对应 DevTools 拆成了一箱命令行。
- `j` 系列从 `jps` 拿 PID 开始：`jstack`/`jcmd Thread.print` 看线程与死锁，`jmap` 看堆与 dump，`jstat -gcutil <pid> 1000` 盯 GC，`jcmd` 是能记一个就够的全能命令。
- **Arthas 是重点**：`dashboard` 总览、`thread -n 3` 找最忙线程、`thread -b` 查死锁、`trace` 逐层定位慢方法、`watch` 看入参返回、`jad` 反编译线上代码——不重启、不改代码就能诊断。
- 系统层用 `top -Hp`/`free -h`/`df -h`/`ss`/`lsof`/`iostat` 判断瓶颈在不在 OS；`tcpdump` 抓包是「日志都解释不了时」确认包通没通的最后物证；`curl -v` 直接打接口分清前后端责任。
- 不要死记命令，记住「现象 → 首选工具」速查表，真出事时按现象掏工具。

### 自测

1. 前端反馈「提交生图任务要转 5 秒圈」，而平时 1 秒。`svc-canvas` 的 `TaskService.submit` 内部串调了 user/redis/ai/mongo/mq 五步。你会用哪个工具、哪条命令在 5 分钟内定位到是哪一步慢？读到输出后下一步怎么继续下钻？
2. 监控显示某服务 CPU 持续 100%。请写出从「找到吃 CPU 的具体线程」到「看到它卡在哪行代码」的完整命令链路（提示：`top -Hp` → 进制转换 → `jstack` 搜 nid，或一条 Arthas 命令）。
3. `svc-canvas` 调 `svc-ai` 一直超时，但 `svc-ai` 的日志里根本没有这次请求的记录。日志已经帮不上忙，你会用什么工具确认「请求到底有没有发出去 / 有没有到达对端」？抓到的包里看到只有 `SYN` 没有 `SYN, ACK` 说明什么？

### 下一章

工具会用了，但「事后救火」终究被动。下一章 [可观测性](/back-end/frontend-backend-guide/30-observability) 教你把日志、监控（Metrics）、链路追踪（Trace）这套「现场」提前种进系统里，让问题在拖垮服务之前就被报警发现。

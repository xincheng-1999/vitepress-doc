# 排查工具箱

排查已经缩小到“线程在等什么”“堆里保留了什么”或“连接在哪里失败”，再选工具会更容易。下面按这些问题分类，说明命令在哪里执行、输出先看什么，以及结果还不能说明什么。

这是按需查阅的说明书，不是要求安装整套工具；第一次学习可先沿 [五阶段主线](./index) 继续。示例 PID、类名、地址和输出均为讲解用，仓库中没有与这些名称对应的完整微服务工程。若还没确定检查方向，先看 [问题排查方法论](/back-end/frontend-backend-guide/27-troubleshooting-methodology) 或 [排查实战手册](/back-end/frontend-backend-guide/28-troubleshooting-playbook)。

## 29.1 先选检查位置

| 现在想回答的问题 | 从哪里看 | 本页入口 |
| --- | --- | --- |
| Java 进程在哪、用了什么参数 | 目标进程所在主机或容器 | [JDK 工具](#jdk-tools) |
| 请求线程在忙还是在等 | 线程栈，必要时短时 CPU 采样 | [JDK 工具](#jdk-tools) |
| 某个方法内部哪一步慢 | 已有计时，必要时授权 attach | [Arthas](#_29-3-arthas-已有日志不够时再用) |
| 主机的内存、磁盘、I/O 是否紧张 | Linux 主机或对应监控 | [系统工具](#_29-4-系统工具-先确认是哪一层资源) |
| TCP 数据有没有经过某个位置 | 正确网络命名空间中的抓包 | [网络抓包](#_29-5-网络抓包-连接仍解释不清时) |
| 当前环境能否得到正确的接口响应 | 从实际调用方所在网络发请求 | [接口工具](#_29-6-接口工具-从哪里请求也很重要) |

`kubectl` 通常在配置好集群凭据的工作终端执行；`jstack` 等工具要能看到目标 Java 进程；`curl` 则可以在本机、服务器或容器里运行，三处的网络路径不一样。不要把所有命令都理解成“先进入容器再敲”。接入方法见 [Linux 服务器基础](/back-end/frontend-backend-guide/21-linux-server-essentials) 与 [Kubernetes 实战](/back-end/frontend-backend-guide/24-kubernetes-in-practice)。

## 29.2 JDK 工具：看进程、线程与内存 {#jdk-tools}

### 先确认 PID，而不是默认 Java 就是 1

JDK 的 `bin` 中通常带有下面的诊断工具，但精简运行镜像、权限限制或禁用 attach 都可能使它们不可用。应使用与目标 JVM 兼容的工具，并确认运行用户、PID 命名空间和输出目录权限。

```bash
jps -l
```

示意输出：

```text
12345 /app/task-api.jar
12390 jdk.jcmd/sun.tools.jps.Jps
```

第一列是 PID，后面是主类或启动文件。这里业务进程是 `12345`，另一个是查询工具自身。`jps` 只列出当前环境有权限看到的 JVM；没有列出来，不等于服务一定没运行。后文的 `12345` 都需要替换成自己确认的值。

### 请求卡住：先看线程栈

```bash
jstack 12345 > /tmp/task-api-stack.txt
# 也可以使用目标 JVM 支持的诊断命令
jcmd 12345 Thread.print
```

采集有一定开销，输出中也可能带出业务上下文，按受控诊断资料保存。先找请求线程停在哪，再看等待的资源：

```text
"http-nio-8080-exec-7" ... WAITING on condition
   java.lang.Thread.State: WAITING (parking)
    at java.util.concurrent.locks.LockSupport.park(...)
    at java.util.concurrent.CompletableFuture.join(...)
    at com.example.task.TaskService.loadDetails(TaskService.java:88)
```

这里说明该线程正在等待一个异步结果，接下来应找这个任务由谁执行、是否完成。`WAITING` 本身不代表异常，空闲线程池也会这样等待；大量请求线程都停在同一处，才值得结合请求量与池使用情况继续检查。

如果出现 `Found one Java-level deadlock`，再读报告中的互相等待关系与业务栈帧。没有死锁报告也不能排除数据库死锁或任务之间的逻辑等待。锁的原理见 [线程安全与锁](/back-end/frontend-backend-guide/16-thread-safety-locks)，等待连接的情况见 [连接池](/back-end/frontend-backend-guide/13-connection-pools)。

### 怀疑 GC：比较一小段时间内的变化

```bash
jstat -gcutil 12345 1000 5
```

`1000` 表示间隔 1000ms，`5` 表示取 5 次，避免不必要地持续采集。示意片段：

```text
   O      YGC    YGCT    FGC    FGCT
 72.10    412   8.123      3   1.402
 72.45    413   8.140      3   1.402
 96.80    414   8.165      4   2.901
```

`O` 是对应老年代使用比例，`YGC/FGC` 是累计次数，`YGCT/FGCT` 是累计耗时。最后一次采样时老年代使用偏高，Full GC 统计也发生变化，说明值得继续对照 GC 日志。不能把 `FGCT` 的差值直接说成“一次请求被暂停了这么久”，也不能据此认定泄漏。

不同收集器的区域与统计口径不同，读列名前先确认 JDK 和 GC 类型。具体暂停、回收原因及回收前后占用，见 [垃圾回收](/back-end/frontend-backend-guide/19-garbage-collection)。

### 想确认实际堆大小：先查参数

```bash
jcmd 12345 help
jcmd 12345 VM.flags
jcmd 12345 GC.heap_info
jinfo -flag MaxHeapSize 12345
```

`help` 列出目标 JVM 支持的命令，不能假设所有版本都有同一套选项。若 `VM.flags` 中出现 `MaxHeapSize=2147483648`，表示最大堆约为 2GiB，而不是整个进程最多只能占 2GiB。

实际值与预期不同，再顺着启动参数、环境变量和容器限制查来源，见 [配置与环境](/back-end/frontend-backend-guide/25-config-and-env)。这里仅展示查询，不建议为排查临时修改运行中参数；多数堆参数本来也不能动态改变。

### 需要看对象：再评估直方图和堆转储

当趋势与错误已经指向堆对象，再考虑：

```bash
jmap -histo:live 12345 | head -20
```

`:live` 会请求 GC 后统计存活对象，可能明显影响进程；`head` 只是少显示输出，不会降低 JVM 统计的成本。类似的 `jcmd 12345 GC.class_histogram` 也可能代价较高，执行前先看目标命令的帮助与影响说明。

输出主要看 `#instances`、`#bytes` 和类名。大量字符串、字节数组或业务对象都是线索，不要只盯业务包名。直方图展示的主要是对象自身占用，不等于“移除这个引用能释放多少内存”，也不能凭一次数量很大就认定泄漏。

确实需要离线分析、已有授权且磁盘余量充足时，才生成堆转储：

```bash
jmap -dump:format=b,file=/tmp/task-api-heap.hprof 12345
# jcmd 也提供 GC.heap_dump；先查目标 JVM 的帮助与选项
jcmd 12345 help GC.heap_dump
```

采集可能暂停应用并产生与堆规模相当的大文件。转储还可能包含密码、token 和业务内容，保存、传输与清理都应遵守敏感数据要求。JVM 已退出时，不能再为它生成转储，只能用之前留下的文件。

用 MAT 查看 Histogram、Dominator Tree 和 Path to GC Roots，分别看对象分布、保留大小和引用来源，再结合正常规模与多次趋势判断。完整分析思路见 [OOM 与内存泄漏](/back-end/frontend-backend-guide/20-oom-memory-leak)。

## 29.3 Arthas：已有日志不够时再用

[Arthas](https://arthas.aliyun.com/) 可以 attach 到 Java 进程，查看线程、方法耗时与已加载的类。它适合“问题正在发生，但已有数据不够”的情况，不保证几分钟内查出原因，也不是无影响的观察窗口。

先按团队允许的来源、版本和校验方式准备工具。官方启动包地址是 [arthas-boot.jar](https://arthas.aliyun.com/arthas-boot.jar)，不要在生产机器上临时下载不明程序并直接运行。已准备好启动包、确认允许 attach 后：

```bash
java -jar arthas-boot.jar 12345
```

进入 `[arthas@12345]$` 后，下面的命令在 Arthas 控制台中执行，不是在普通 shell 中执行。不要把控制台监听端口暴露到公网。

| 想检查什么 | 命令 | 先读哪部分 |
| --- | --- | --- |
| 线程、内存和 GC 概况 | `dashboard` | 采样时间内的变化，不只看单个数 |
| 哪些线程使用 CPU 较多 | `thread -n 3` | 采样期 CPU 和对应线程栈 |
| 谁阻塞了其他线程 | `thread -b` | 持有监视器的线程与等待者；它不是完整死锁检测器 |
| 运行中加载的代码是否符合预期 | `jad com.example.task.TaskService` | 类加载器、实际字节码反编译结果 |
| 当前会话结束 | `quit` | 只退出当前会话，Arthas 可能仍留在进程中 |
| 完成诊断后关闭 Arthas | `stop` | 结束服务并移除相关增强，不代表此前完全没有开销 |

### 方法慢：只观察一个范围

假设你自己的应用确实有 `com.example.task.TaskService.findPage`，才可以使用类似命令。这个类只是示意，不需要在本仓库里寻找。

```text
trace com.example.task.TaskService findPage '#cost > 1000' -n 3
```

它对匹配方法进行增强，展示耗时超过 1000ms 的调用，命中 3 次后结束。条件主要过滤输出，不代表没有增强成本；若一直没有命中，也不会因为 `-n 3` 自动按时退出，应限定观察时间并主动停止。不要对高频基础类使用宽泛通配符。

示意结果：

```text
[cost=4820ms] com.example.task.TaskService:findPage()
    +---[4790ms] com.example.task.TaskRepository:findPage()
    `---[12ms] com.example.task.TaskMapper:toResponse()
```

这次调用的时间主要在 Repository，但还要拆分连接等待、SQL 执行等，不能直接认定数据库缺索引。`trace` 不是自动补齐跨服务 trace，异步任务也不会自然变成这棵同步调用树的子步骤。下一步选择见 [性能与并发](/back-end/frontend-backend-guide/31-performance-concurrency) 和 [线程池与 Executor](/back-end/frontend-backend-guide/15-thread-pools-executor)。

### 只想知道是否抛异常：不要默认打印全部参数

```text
watch com.example.task.TaskService findPage '{#cost,throwExp != null}' -f -x 1 -n 3
```

这里仅观察方法结束时的耗时和是否有异常，避免直接打印入参、返回体中的敏感信息。如果确实需要某个字段，先确认其含义与脱敏办法，再缩小表达式范围。OGNL 表达式可能调用方法、访问运行时对象，不应把它当成任意无害的日志模板。

## 29.4 系统工具：先确认是哪一层资源

以下是 Linux 常用工具，有的需要额外安装。容器里看到的 CPU、内存或文件系统范围取决于隔离方式，不能直接等同于整台宿主机。

| 问题 | 命令 | 怎么读与接下来做什么 |
| --- | --- | --- |
| 哪个进程占 CPU | `top` | 结合核数、配额和持续时间，不能只看一次 100% |
| 哪个线程占 CPU | `top -Hp 12345` | Linux 平台线程号可对应线程栈的 `nid` |
| 整机内存是否紧张 | `free -h` | 看 available、swap 与趋势，不只看 free |
| 文件系统空间或 inode 是否耗尽 | `df -h`、`df -i` | 先确认应用实际写入的挂载点，别急着删文件 |
| I/O 是否持续繁忙 | `iostat -x 1 5` | 结合延迟、队列和设备类型，不只凭 `%util` 下结论 |
| 端口是否监听 | `ss -tlnp` | 监听地址也要看；监听不代表应用能正确响应 |
| 进程打开哪些文件或连接 | `lsof -p 12345` | 查异常增长和类型，行数不等于严格的文件描述符数 |

CPU 高时，假设 Linux 线程号为 `12361`：

```bash
printf '%x\n' 12361
# 3049
jstack 12345 | grep -A 30 'nid=0x3049'
```

必须在同一个 PID 命名空间中对应，且不是虚拟线程的一一映射。拿到热点后继续读业务栈，单个 `RUNNABLE` 状态不能证明死循环。更多主机检查见 [Linux 服务器基础](/back-end/frontend-backend-guide/21-linux-server-essentials)。

## 29.5 网络抓包：连接仍解释不清时

日志里没有请求，不一定表示请求没有到达：也可能是采样、记录位置或过滤条件不对。先确认 DNS、目标地址、端口和请求来源，必要时再抓包。

抓包需要授权和相应权限，可能收集认证信息、业务内容并占用磁盘。先选定网卡或网络命名空间，限制主机、端口、数量和持续时间。下面仅展示过滤写法，`192.0.2.10` 是文档示例地址：

```bash
tcpdump -i any -nn -c 100 'host 192.0.2.10 and port 8080' -w /tmp/task-api-8080.pcap
```

`-c 100` 限制包数，不限制等待时间；若没有流量也会继续等待，需要按约定时间停止。采集文件应受控保存，在 Wireshark 中分析。

| 看到的现象 | 可以继续怀疑什么 | 还不能断定什么 |
| --- | --- | --- |
| 只有 SYN，未见 SYN-ACK | 握手或返回路径存在问题 | 不能单凭它断定是防火墙 |
| 出现 RST | 某端或中间设备重置连接 | 需要看方向和上下文，不一定是应用主动拒绝 |
| 有请求方向数据，长时间无响应 | 服务处理、传输或返回路径可能有等待 | TCP 确认不等于业务已经处理 |
| 抓到 0 个包 | 可能位置、过滤器或时段不对 | 不能立刻断定请求未到目标机器 |

TLS 加密时，通常只能看到连接层信息，不能直接读出业务请求。网络分层见 [面向后端的网络基础](/back-end/frontend-backend-guide/22-networking-for-backend)，数据保护可结合 [Web 安全](/front-end/the-basics/network-basics/webSafety) 查看。

## 29.6 接口工具：从哪里请求也很重要

浏览器失败而服务器上的 `curl` 成功，只说明两条访问路径的表现不同，不能直接宣布是前端问题。浏览器还受 CORS、Cookie、证书与代理规则等影响。

对自己的本地应用，先使用确认无副作用的读取接口，并设置等待上限：

```bash
curl --connect-timeout 3 --max-time 10 -i http://127.0.0.1:8080/api/tasks
```

实际路径和端口按应用替换。示意响应：

```text
HTTP/1.1 200 OK
Content-Type: application/json

{"items":[],"total":0,"page":0,"size":20}
```

先读 HTTP 状态，再按真实接口契约读响应体。这里返回空列表；其他系统可能还定义业务 `code`，不能把某种统一包装类当成所有项目的标准。

需要详细连接信息时才加 `-v`，它可能打印 Authorization、Cookie 等内容，分享前要脱敏。手动重放 POST、支付或任务提交请求会产生副作用，不能因为是排查就反复发送。Postman、Apifox 适合管理接口集合，但也要防止把生产凭据同步到未经批准的空间。状态码与响应契约见 [API 设计](/back-end/frontend-backend-guide/34-api-design)。

## 29.7 下一步该去哪里

命令格式想快速回忆，可查 [命令速查表](/back-end/frontend-backend-guide/91-command-cheatsheet)；想按症状继续推进，回到 [排查实战手册](/back-end/frontend-backend-guide/28-troubleshooting-playbook)。如果每次都要临时 attach、补日志才能知道发生了什么，可以再看 [可观测性](/back-end/frontend-backend-guide/30-observability)，把常用数据提前留下来。

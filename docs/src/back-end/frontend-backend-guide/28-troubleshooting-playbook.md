# 排查实战手册

已经知道“接口慢”或“容器反复重启”，但还不知道先看哪里，可以从下面的表进入。每一节都按“先检查什么 → 结果怎样读 → 接下来查哪里”组织，不需要从头执行所有命令。

本页是按需查阅的手册，不属于 [五阶段主线](./index) 的前置要求。示例中的 `task-api`、日志路径、PID 和 Pod 名都是说明用名称，日志与输出也是示意，不是仓库里另一套服务的实测记录。使用命令前，先确认自己在哪台机器、哪个容器或哪个集群中；需要完整排查思路时，回看 [问题排查方法论](/back-end/frontend-backend-guide/27-troubleshooting-methodology)。

## 28.1 速查表：症状 → 翻到哪一节 + 常用工具

| 已看到的现象 | 先查哪一节 | 第一个检查位置 |
| --- | --- | --- |
| HTTP 500，或响应内容表示失败 | [接口报错](#_28-2-症状一-接口报-500-抛异常) | 这次请求的应用日志 |
| 请求变慢、偶发 504 | [接口超时](#_28-3-症状二-接口变慢-超时) | 同一时间段的请求延迟和分段耗时 |
| Java 进程持续高 CPU | [CPU 高](#_28-4-症状三-cpu-飙到-100) | 进程与线程 CPU，再对应线程栈 |
| 内存上涨、Java OOM 或 OOMKilled | [内存异常](#_28-5-症状四-内存暴涨-oom) | 先区分 JVM 堆与容器总内存 |
| 请求卡住，怀疑线程等待或死锁 | [线程卡住](#_28-6-症状五-线程卡住-死锁) | 线程栈中的等待位置和持锁关系 |
| Kubernetes 容器反复重启 | [服务重启](#_28-7-症状六-服务频繁重启-k8s) | Last State、Events 和上一实例日志 |
| 数据重复、丢失或偶发不一致 | [数据异常](#_28-8-症状七-数据不对-偶发问题) | 同一业务编号的记录与处理日志 |

下面使用 Linux 命令和 JDK 诊断工具。`12345` 是示例 PID，必须先用 `jps -l` 等方式确认目标；精简容器可能没有这些工具，宿主机和容器中的 PID 也不一定相同。工具的使用条件见 [诊断工具箱](/back-end/frontend-backend-guide/29-diagnostic-toolbox)。

## 28.2 症状一：接口报 500 / 抛异常

### 先找到这一次请求

先核对浏览器或调用方看到的 HTTP 状态、响应内容和发生时间。参数错误、权限不足、余额不足等业务拒绝，应该按接口约定返回；它们不一定是系统异常，也不能只按日志级别区分。HTTP 200 也可能携带业务失败信息，具体约定见 [后端思维](/back-end/frontend-backend-guide/01-backend-mindset)。

如果系统把请求标识放在响应头、响应体或日志中，取得标识后再搜索。不是所有应用都会自动生成 `X-Trace-Id`，没有这个字段时，先按时间、路径和脱敏业务编号缩小范围。可配合 [读懂日志](/back-end/frontend-backend-guide/26-reading-logs) 查看。

```bash
# 已确认这是目标应用的日志路径和请求标识
grep -n -A 30 'traceId=demo-error-001' /var/log/task-api/app.log
# 只有时间线索时，先看最近的错误入口，再回到原日志读完整上下文
grep -n 'ERROR' /var/log/task-api/app.log | tail -50
```

### 读异常类型、业务帧和原因

例如看到：

```text
14:23:07 ERROR traceId=demo-error-001 查询任务失败
org.springframework.dao.DataAccessResourceFailureException: query failed
    at com.example.task.TaskRepository.findPage(TaskRepository.java:88)
    ...
Caused by: java.sql.SQLTransientConnectionException:
    Connection is not available, request timed out after 3000ms
```

第一层异常说明查询失败，第一个业务栈帧给出调用位置，后面的原因进一步说明：这次失败发生在获取连接时。下一步应查连接池等待、数据库可达性和连接持有时间，而不是立即改这条 SQL。

`Caused by` 是重要线索，但最深一层也未必就是完整根因。例如“连接超时”仍可能来自地址错误、网络拦截或服务不可用。空指针则要看空值来自哪里；不能把 Feign 超时自动解释成“返回了 null”，除非代码确实有捕获异常后返回 null 的逻辑。

修复时，让预期内的拒绝有明确的接口结果，让意外异常保留请求标识与服务端上下文。不要只加一个判空或统一返回“成功”把问题藏起来。异常处理写法可查 [Java 异常处理](/back-end/java/05-exception)。

## 28.3 症状二：接口变慢 / 超时

### 先分清是整个接口慢，还是一小部分请求慢

选定路径和时间段，比较请求量、P50/P95/P99、错误与超时数量。然后选一条慢请求，查看链路或已有分段日志：

```bash
grep 'traceId=demo-slow-001' /var/log/task-api/app.log
```

```text
traceId=demo-slow-001 repository.findPage cost=4800ms
traceId=demo-slow-001 assembleResponse cost=12ms
traceId=demo-slow-001 request total=4840ms
```

主要时间花在查询调用中。继续拆分等待连接、SQL 执行和读取结果，才能知道该改哪里。若主要时间在远程调用，去看下游同一请求的处理情况；不要在调用方看到 4.8 秒，就直接认定下游 SQL 慢。

### 按已经缩小的范围继续查

| 当前线索 | 接着检查 | 结果怎样理解 |
| --- | --- | --- |
| SQL 执行本身慢 | 已有慢查询记录、普通 `EXPLAIN` | 扫描行数、索引选择与真实数据分布一起看，全表扫描不总是错误 |
| 获取连接等很久 | active、pending、连接持有时间和数据库负载 | 池满可能是慢事务或连接未归还，调大池也可能压垮数据库 |
| 远程调用慢 | 调用方计时、下游 span、连接与读取超时 | 客户端等待时间不等同于下游业务执行时间 |
| 怀疑 JVM 停顿 | 同时段 GC 日志，必要时短时采样 `jstat` | 累计值的变化提示继续查 GC，不能直接当作单次暂停 |
| 大量线程等待 | 线程栈中的具体等待点 | 区分锁、连接池、任务结果和外部 I/O |

例如短时查看 GC 统计：

```bash
jstat -gcutil 12345 1000 5
```

`FGC` 和 `FGCT` 是累计次数与累计耗时。两次采样间它们增加，表示期间发生了相关回收；具体暂停多久、是否与接口超时重叠，要读 GC 日志。不同收集器和 JDK 的统计口径也会不同。

查 SQL 可接着看 [SQL 与索引](/back-end/frontend-backend-guide/10-sql-and-indexes) 与 [MySQL 进阶](/back-end/database/mysql/advanced)；连接等待看 [连接池](/back-end/frontend-backend-guide/13-connection-pools)；GC 看 [垃圾回收](/back-end/frontend-backend-guide/19-garbage-collection)。修改超时、池大小或索引前，应先在合适环境比较修改前后结果，不要同时把所有参数调大。

## 28.4 症状三：CPU 飙到 100%

### 先确认是哪种“100%”

先看告警表示整机、进程、容器配额还是单核使用率。一个线程占满一核，与整台多核机器全部占满，不是同一种情况。Linux 中可以这样定位已确认的 Java 进程：

```bash
jps -l
top -Hp 12345
```

在 `top` 中观察一小段时间，记下持续高 CPU 的线程号。假设线程号是十进制 `12361`，再对应到线程栈：

```bash
printf '%x\n' 12361
# 示意输出：3049
jstack 12345 | grep -A 30 'nid=0x3049'
```

这条对应方式适用于相同 PID 命名空间中的 Linux 平台线程，不能原样套到 macOS 或虚拟线程上。采集线程栈需要相应用户权限，也有一定开销。

### 看热点在哪里，再判断为什么热

```text
"task-worker-3" ... nid=0x3049 runnable
   java.lang.Thread.State: RUNNABLE
    at java.util.regex.Pattern$Curly.match(Pattern.java:...)
    at com.example.task.InputValidator.check(InputValidator.java:41)
```

高 CPU 线程反复落在正则匹配里，值得检查第 41 行的表达式和触发输入。但单份 `RUNNABLE` 快照不能证明死循环：它也可能是在正常计算。可以间隔采集少量快照，或使用经授权的短时 CPU 采样，观察热点是否持续。

如果确认是正则回溯，应改写高风险表达式并限制输入大小。预编译 `Pattern` 只减少编译开销，不能消除回溯风险。若热点在 GC 线程，转查下一节；若是业务计算，就比较算法、输入量和实际吞吐，不要只靠加 `sleep` 压低 CPU 数字。并发循环的可见性与等待方式见 [线程安全与锁](/back-end/frontend-backend-guide/16-thread-safety-locks)。

## 28.5 症状四：内存暴涨 / OOM

### 先区分错误发生在哪一层

`OutOfMemoryError: Java heap space` 表示 JVM 分配堆对象失败；容器的 `OOMKilled` 表示进程被 OOM 机制终止，还要结合容器限制、节点情况和事件检查。容器总内存包含堆、元空间、线程栈、直接内存等，不能只盯 `-Xmx`。

先看历史内存趋势、错误类型和 GC 日志。如果进程已退出，只能分析之前保留的日志或转储，不能再对旧 PID 执行 `jmap`。方法见 [OOM 与内存泄漏](/back-end/frontend-backend-guide/20-oom-memory-leak)。

### 确实需要看堆时，再选择采集方式

对象直方图和堆转储可能触发 GC、暂停进程并产生大量 I/O，不是低成本探活命令。尤其 `:live` 会请求回收后统计存活对象。确认授权、磁盘余量、输出路径与业务影响后，才考虑：

```bash
jmap -histo:live 12345 | head -20
# 只有需要离线分析且资源允许时，才生成堆转储
jmap -dump:live,format=b,file=/tmp/task-api-heap.hprof 12345
```

转储可能包含密码、token 和业务数据，应限制访问、按规定传输与清理，不能直接上传公开平台。

假设直方图里某业务类有百万个实例，这只是值得追查的线索；也可能是合法缓存或一次批处理。`byte[]`、字符串等基础类也不能跳过，它们可能占了主要空间。

用 MAT 的 Histogram 看对象分布，Dominator Tree 看保留大小，再沿 Path to GC Roots 查持有关系。若多次可比较的采样中，已经不再使用的任务仍被无上限集合持有，才更支持泄漏判断。修复方向可能是限制缓存、分页或流式处理，见 [Redis 实战](/back-end/frontend-backend-guide/12-redis-in-practice)；不能仅凭“内存一直涨”就认定泄漏，也不能默认所有频繁 Full GC 都是泄漏。

## 28.6 症状五：线程卡住 / 死锁

### 先找等待位置，不先数 WAITING 下结论

```bash
jstack 12345 > /tmp/task-api-stack.txt
grep 'java.lang.Thread.State' /tmp/task-api-stack.txt | sort | uniq -c
```

状态统计只用于导航。空闲线程池中大量 `WAITING` 很常见；网络读取也可能显示 `RUNNABLE`。需要回到完整栈，看业务请求线程到底在等连接、等任务结果，还是等某把锁。

如果末尾出现：

```text
Found one Java-level deadlock:
"worker-1": waiting for a lock held by "worker-2"
"worker-2": waiting for a lock held by "worker-1"
```

这是 JVM 检测到的 Java 级死锁。继续看两个线程各自 `locked` 和 `waiting to lock` 的对象以及业务栈帧，确认是否存在相反的加锁顺序。没有这个报告，不代表没有其他形式的等待环路，例如数据库锁或任务互相等待。

如果很多 `BLOCKED` 线程都等待同一个监视器，就查持有它的线程在做什么。若持锁期间执行慢 I/O，缩短持锁范围可能比增加线程更有效。锁顺序、带超时获取锁与中断处理见 [线程安全与锁](/back-end/frontend-backend-guide/16-thread-safety-locks)。确需重启恢复时，按团队流程执行；能及时取得线程栈就保留，但不要为采集大堆转储延误恢复。

## 28.7 症状六：服务频繁重启（K8s）

### 在有权限的集群中查上一实例

先确认 context，再指定实际命名空间和 Pod，不要依赖默认环境：

```bash
kubectl config current-context
kubectl get pods -n demo
kubectl describe pod task-api-example -n demo
kubectl logs task-api-example -n demo -c app --previous --tail=100
```

`demo`、`task-api-example`、`app` 都是占位名称。`--previous` 查同一 Pod 内指定容器的上一实例，不保证能找回已经被删除的 Pod 的日志；历史记录还可能受轮转影响。

### 把终止原因、退出码和事件放在一起读

| 看到的内容 | 能说明什么 | 下一步 |
| --- | --- | --- |
| `Reason: OOMKilled` | 有 OOM 终止记录 | 查容器/节点内存、限制与 JVM/堆外使用 |
| 退出码 `137` | 进程收到 SIGKILL | 结合 Reason 和事件判断，不能单凭 137 认定 OOM |
| 退出码 `1` | 程序以失败状态退出 | 查上一实例日志中的启动或运行异常 |
| 退出码 `0` / `143` | 正常退出或收到 SIGTERM | 结合发布、缩容、驱逐和停机记录，不直接判为正常 |
| `Liveness probe failed` 后有 `Killing` | 存活探针失败导致终止 | 查启动耗时、探针路径、阈值与应用是否真的卡死 |
| `Readiness probe failed` | 就绪检查没有通过 | 它本身不会触发重启，要另找退出原因 |

探针超时不一定是误判，也可能反映真实卡顿。慢启动可评估 `startupProbe`；liveness 不宜把共享下游故障作为重启所有实例的理由。内存参数也不能照抄一个百分比，要为线程栈和直接内存等留出实际余量。机制见 [Kubernetes 实战](/back-end/frontend-backend-guide/24-kubernetes-in-practice)，配置来源见 [配置与环境](/back-end/frontend-backend-guide/25-config-and-env)。

## 28.8 症状七：数据不对 / 偶发问题

### 先用业务编号核对事实

先明确“哪条数据、预期什么、实际什么”。使用授权只读账号查具体记录，并按同一业务编号查相关日志，避免直接全表扫描或在日志中暴露个人信息。

```bash
grep 'orderNo=DEMO-001' /var/log/task-api/app.log
```

```sql
-- 示意表名与字段，按实际模型替换
SELECT id, order_no, status, create_time
FROM payment
WHERE order_no = 'DEMO-001';
```

看到同一订单有两条“收到消息”，只能说明有两次接收记录；可能是重投、生产端重复发送或日志重复采集。还要核对消息标识、消费结果和数据库实际变更，才能认定是否重复产生了业务效果。

### 根据差异继续检查

| 实际差异 | 接着看什么 | 处理方向 |
| --- | --- | --- |
| 同一业务操作生效两次 | 幂等键、唯一约束、事务提交与重试记录 | 让幂等记录与业务变更原子完成，不能只“先查后改” |
| 两次并发修改互相覆盖 | 更新条件、版本号、影响行数和事务边界 | 使用适合业务的原子更新、乐观锁或行锁 |
| 时间相差固定小时数 | 字段类型、时区、序列化与展示规则 | 先确认是不是同一时刻的不同表示，再统一约定 |
| 大整数 ID 在前端改变 | 原始响应与 JS 解析结果 | 超过安全整数范围的 ID 按字符串传输 |
| 数据库已改，读取仍旧 | 缓存命中位置、失效时间和并发写入顺序 | 检查缓存更新策略，短 TTL 不能代替一致性设计 |

幂等与重投看 [MQ 可靠性](/back-end/frontend-backend-guide/33-mq-reliability)，事务与竞态看 [事务与一致性](/back-end/frontend-backend-guide/11-transactions-consistency)。如果现有日志不够，再针对怀疑路径补少量脱敏日志，带上业务编号、处理结果与请求标识，并控制采样、保留期和开销；不是把所有入参全部打印出来等故障重来。

## 28.9 查到这里仍没有结论时

把已经确认的事实和还没验证的判断分开：在哪个环境、什么时间、哪个接口或业务编号、看到了什么、哪些方向已被排除。这样交接给同事，比只说“我跑过 jstack 和 jmap”更有用。

若已知道下一步要使用哪个工具，去 [诊断工具箱](/back-end/frontend-backend-guide/29-diagnostic-toolbox) 查使用条件和读法；如果仍不知道怎样缩小范围，回到 [问题排查方法论](/back-end/frontend-backend-guide/27-troubleshooting-methodology)。不用为了完成一张清单，继续采集与当前问题无关的数据。

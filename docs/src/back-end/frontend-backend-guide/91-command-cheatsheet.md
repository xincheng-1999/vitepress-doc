# 命令速查卡

已经知道要查日志、端口或线程，只是忘了命令格式，可以在这里按分类找。每组都写明执行位置和先读什么；如果还不知道问题在哪，先看 [排查实战手册](/back-end/frontend-backend-guide/28-troubleshooting-playbook)，不要把这一页从上到下全部执行。

本页按需查阅，不需要在开始 [五阶段主线](./index) 前背完。`12345`、`demo`、`task-api-example`、路径和接口都是示例，需要换成确认过的目标，不对应仓库里的另一套工程。Linux 命令不保证在 macOS 或 PowerShell 中原样可用。下面没有在本机运行 Compose、连接 Kubernetes 或执行部署，响应内容均为示意。

## Linux：文件 / 进程 / 网络 / 磁盘 / 服务

这些命令在目标 Linux 服务器的终端中运行。接入后先用 `pwd` 确认目录，读取配置和日志时也要注意权限与敏感信息。详解见 [Linux 服务器必会](/back-end/frontend-backend-guide/21-linux-server-essentials)。

### 文件与日志：先找位置，再看附近内容

```bash
pwd                              # 当前目录
ls -lh                           # 当前目录的文件大小和权限
ls -lt                           # 按修改时间查看
find /app -name '*.log'           # 仅在已确认的应用目录中找日志
stat /app/app.jar                 # 查看文件时间、大小和权限
less /var/log/task-api/app.log    # 翻页读日志；q 退出，/ 搜索
tail -n 100 /var/log/task-api/app.log
# 按具体请求标识查上下文，避免只看孤立的一条 ERROR
grep -n -A 30 'traceId=demo-001' /var/log/task-api/app.log
```

`tail` 只说明最近写了什么；请求不在这个时间段时，要按标识或时间回到完整日志查。跟踪新增日志可用 `tail -f`，用 Ctrl+C 结束；不要为找一个请求，把整份含敏感内容的日志下载或发到群里。

复制、移动、改权限和删除都不是查看操作。本页不提供批量删除的速抄命令；需要腾空间时，先确认占用者、保留要求和文件是否仍被进程使用，不能直接删应用正在写的日志。

### 进程与资源：确认服务是否存在

```bash
ps -ef                           # 查看进程命令行，输出可能包含敏感启动参数
jps -l                           # 查看当前环境可见的 JVM 及 PID
top                              # 观察 CPU/内存；P 排 CPU，q 退出
top -p 12345                     # Linux 中只观察已确认的进程
free -h                          # 整机内存，结合 available 和 swap 看
df -h                            # 文件系统容量
df -i                            # inode 使用量
du -sh /var/log/task-api          # 定位目录占用，大目录遍历也有 I/O 成本
```

进程存在不代表接口可用；`free` 小也不一定内存紧张，要结合缓存与 available。磁盘问题要核对应用实际写入的挂载点。

停止进程应使用团队的服务管理方式。SIGTERM 是停止请求，应用是否能完成优雅停机取决于自身实现与配置；SIGKILL 则不给清理机会。不要用模糊进程名批量结束服务，也不要把“几秒没退出”当成强杀依据。

### 网络：分别检查解析、监听和连接

```bash
dig api.example.com              # 查询 DNS；域名为示例
ss -tlnp                         # Linux 中的监听地址、端口及进程
ss -tnp                          # 已建立或正在建立的 TCP 连接
lsof -i:8080                     # 查看占用/使用 8080 的进程
nc -zv -w 3 127.0.0.1 8080      # 在当前环境探测指定 TCP 端口
curl --connect-timeout 3 --max-time 10 -i http://127.0.0.1:8080/actuator/health
```

`nc` 成功只说明 TCP 连接建立，不证明账号、协议或业务正常；失败也要区分拒绝、超时与地址错误。`ping` 测的是 ICMP，不是 HTTP 或数据库端口，不能用它替代应用检查。网络分层见 [后端必懂的网络](/back-end/frontend-backend-guide/22-networking-for-backend)。

### systemd：看服务状态与时间段日志

```bash
systemctl status task-api
journalctl -u task-api --since '10 min ago' --no-pager
journalctl -u task-api -f          # 持续跟随，Ctrl+C 结束
```

服务名按实际 unit 替换。`active` 表示管理器认为服务在运行，不代表业务检查通过；`failed` 则要结合退出原因与前面的日志。启动、停止、重启和设置自启会改变服务状态，应使用已有运行手册，而不是在陌生服务器上试命令。

## Docker：查看容器，再决定是否进入

Docker 命令在能访问目标 Docker daemon 的终端执行，先确认连接的不是远端生产环境。详解见 [Docker 实战](/back-end/frontend-backend-guide/23-docker-in-practice)。

```bash
docker context show
docker ps                         # 当前运行的容器
docker ps -a                      # 包含已退出的容器
docker logs --since 10m --tail 100 task-api-local
docker inspect task-api-local     # 配置、挂载、状态等，输出可能含敏感值
docker stats --no-stream          # 一次资源快照
docker system df                 # Docker 对象的磁盘占用
```

`Up` 只说明容器主进程还在；有健康检查时再看健康状态，并结合接口结果。容器退出可先读状态与日志，不要先删除它。

需要在运行中容器里执行特定命令、且已经获得权限时：

```bash
docker exec task-api-local printenv SPRING_PROFILES_ACTIVE
docker exec -it task-api-local sh
```

不要直接打印全部环境变量，里面可能有密码。容器也可能没有 shell、JDK 或网络工具，不能把缺工具当成服务故障。

### 本地构建与运行：不是仓库现成应用

如果你自己已有 Dockerfile 和应用，可参考以下本地格式。这些操作会创建镜像和容器，并下载基础镜像，需要预留资源：

```bash
docker build -t task-api:local .
docker run -d --name task-api-local -p 127.0.0.1:8080:8080 task-api:local
```

端口要与应用一致。这里绑定回环地址，不是把服务开放给整个网络。停止、删除容器、删除镜像与 `prune` 都会改变本机状态；尤其清理卷可能丢数据，不能为了腾空间直接全量清理。

### Compose：必须指向你自己的配置

仓库不因出现下面命令，就自动有一套可运行的业务 Compose。已有独立配置时，可以显式指定项目和文件，减少误操作范围：

```bash
docker compose -p task-lab -f ./compose.yml config --quiet
docker compose -p task-lab -f ./compose.yml ps
docker compose -p task-lab -f ./compose.yml logs --since 10m --tail 100
```

`config --quiet` 通过表示配置能被 Compose 解析，不代表服务可启动。`up` 会创建或更新容器，`down` 会停止并删除项目资源；不要把它们作为查看命令执行。配置还可能从 `.env` 等位置取值，分享展开结果前先脱敏。

## kubectl：先确认集群与命名空间

`kubectl` 在配置好凭据的工作终端执行，不要求先进入 Pod。以下均为查看格式，示例命名空间、Pod、Deployment 和容器名要换成实际值。详解见 [Kubernetes 实战](/back-end/frontend-backend-guide/24-kubernetes-in-practice)。

```bash
kubectl config current-context
kubectl get pods -n demo -o wide
kubectl get deploy -n demo
kubectl get svc -n demo
kubectl describe pod task-api-example -n demo
kubectl logs task-api-example -n demo -c app --since=10m --tail=100
kubectl logs task-api-example -n demo -c app --previous --tail=100
kubectl get events -n demo --sort-by=.lastTimestamp
kubectl rollout status deployment/task-api -n demo --timeout=120s
kubectl rollout history deployment/task-api -n demo
kubectl top pods -n demo           # 依赖集群资源指标服务
```

`READY 0/1` 表示容器未就绪，即使 `STATUS` 是 `Running` 也可能不能接流量。`describe` 中看当前/上次容器状态及 Events；`--previous` 只查同一 Pod 内该容器的上一实例，不是任意历史 Pod 的日志。`rollout status` 成功只说明 Deployment 更新达到条件，仍要看业务结果。

进入容器会获得执行命令的能力；只在确实需要且有权限时使用：

```bash
kubectl exec -it task-api-example -n demo -c app -- sh
```

`apply`、`delete`、`set image`、`rollout restart/undo`、`scale` 都会改变集群，不混在这组查看命令中。端口转发也会建立新的接入通道，必须确认授权与监听范围，不能用它绕开访问控制。

## JVM 诊断：先轻量确认，再选采集方式

在能看到目标 Java 进程的环境中执行；先确认 PID、运行用户、JDK 兼容性和 attach 权限。详解见 [JVM 内存模型](/back-end/frontend-backend-guide/18-jvm-memory-model) 与 [排查实战手册](/back-end/frontend-backend-guide/28-troubleshooting-playbook)。

```bash
jps -l
jcmd 12345 help
jcmd 12345 VM.flags               # 实际生效的 JVM 参数
jcmd 12345 GC.heap_info           # 当前堆信息
jstack 12345                     # 线程快照，有一定采集开销
jstat -gcutil 12345 1000 5        # 间隔 1 秒取 5 次 GC 统计
```

线程状态要结合具体栈和线程用途读，`WAITING` 不自动等于卡死。GC 次数和耗时多为累计值，样本之间的差值要结合 GC 日志解释，不直接当作某次接口暂停。

对象直方图与堆转储可能触发 GC、长暂停和大量磁盘写入。确认确有内存分析需要、授权与可用空间后，才参考：

```bash
jmap -histo:live 12345 | head -30
jmap -dump:live,format=b,file=/tmp/task-api-heap.hprof 12345
```

`:live` 不是低成本选项；`head` 只限制显示行数。转储可能含密码、token 和业务数据，应受控保存，不能发到公开平台。进程已经退出时不能再对旧 PID 补采。

### CPU 高：Linux 平台线程对应方法

先用 `top -Hp 12345` 找持续高 CPU 的线程。假设线程号是 `12361`：

```bash
printf '%x\n' 12361
# 3049
jstack 12345 | grep -A 30 'nid=0x3049'
```

把热点线程与栈中的 `nid` 对应，再看业务帧。线程号需要来自同一 PID 命名空间；macOS 与虚拟线程不能原样套用。一次快照落在某行只说明采样当时的位置，不足以证明死循环。

## Arthas：在控制台中做有限观察

已经按团队要求准备好 Arthas、且允许 attach 时，在目标进程环境启动：

```bash
java -jar arthas-boot.jar 12345
```

下面在 Arthas 控制台执行。类和方法仅为示意，必须换成应用中实际存在的目标：

```text
dashboard
thread -n 3
thread -b
trace com.example.task.TaskService findPage '#cost > 1000' -n 3
watch com.example.task.TaskService findPage '{#cost,throwExp != null}' -f -x 1 -n 3
jad com.example.task.TaskService
stop
```

`thread -b` 侧重寻找阻塞其他线程的监视器持有者，不是所有死锁的一键检测。`trace/watch` 会增强目标方法，有性能和隐私影响；限制类、方法、次数与观察时间，不默认打印全部参数。`-n 3` 是命中次数，不是超时；没命中时也要主动停止。`stop` 关闭 Arthas，`quit` 只离开当前会话，不等于此前完全无影响。

## redis-cli：先查类型和范围

连接到授权实例，先确认地址、端口、数据库编号与 TLS 要求。不要把密码写在 `-a` 参数或共享命令中；客户端支持时可使用 `--askpass` 交互输入，并按平台要求补充用户名与 TLS 配置。

```bash
redis-cli -h 127.0.0.1 -p 6379 --askpass
```

下面在 Redis 提示符中执行，示例 key 不是仓库中的真实数据：

```text
PING
TYPE task:demo:status
GET task:demo:status
TTL lock:task:demo
SCAN 0 MATCH task:demo:* COUNT 100
ZRANGE task:queue 0 19 WITHSCORES
INFO memory
INFO clients
SLOWLOG GET 10
```

`PONG` 只说明当前连接能响应。`TTL=-1` 表示存在但无过期时间，`-2` 表示不存在；它们不能直接证明锁是否仍有合法持有者，不能因此手动 `DEL`。`SCAN` 返回下一游标，需继续到 `0` 才算一次遍历，`COUNT` 只是提示，也可能返回重复项；它比全量扫描更可控，但大量遍历仍有成本。

`GET`、范围读取和慢日志也可能显示敏感业务内容。不要在大集合上随手全量读取，不把 `KEYS *` 或 `MONITOR` 当日常查看命令。详解见 [Redis 实战](/back-end/frontend-backend-guide/12-redis-in-practice) 与 [Redis 与 Java](/back-end/database/redis/java-redis)。

## curl：核对状态、内容与耗时

从实际需要检查的网络位置发出请求。先选确认无副作用的接口；本地地址和路径按自己的应用替换：

```bash
curl --connect-timeout 3 --max-time 10 -i http://127.0.0.1:8080/api/tasks
curl --connect-timeout 3 --max-time 10 -sS -o /dev/null \
  -w 'http_code=%{http_code} total=%{time_total}s\n' \
  http://127.0.0.1:8080/api/tasks
```

示意结果 `http_code=200 total=0.082s` 表示这次请求收到 HTTP 200、总耗时约 82ms，不表示所有请求都快，也不能代替读取业务响应。HTTP 200 是否代表业务成功，要按接口契约判断，不能默认所有应用都有 `code=0`。

详细模式 `-v` 可能打印 Cookie 和 Authorization，重放写请求还可能重复创建数据或扣费。需要调试登录、下载或 POST 时，先确认测试账号、数据和副作用，不把生产凭据写进可分享的命令。接口约定见 [API 设计](/back-end/frontend-backend-guide/34-api-design)，隐私与请求安全见 [Web 安全基础](/front-end/the-basics/network-basics/webSafety)。

查完命令，还要把结果带回当前问题：它支持了什么判断，排除了什么，下一步查哪里。学习顺序可回到 [学习路线](/back-end/frontend-backend-guide/93-learning-path)，不需要先背完这一页。

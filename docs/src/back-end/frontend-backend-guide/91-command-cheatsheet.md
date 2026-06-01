# 命令速查卡

> 这一章不是用来「学」的，是用来「贴」的——贴在你工位显示器边上、收藏到浏览器书签、或者打印出来压在键盘下。
> 真正排查线上问题的时候，你不会有空翻教程。你需要的是：一眼扫到那条命令，复制，粘贴，回车。
> 所以本章每条命令只占一行：**命令 + 一句用途**。每个分区顶部有一句「什么时候用」和一个「详讲在哪章」的链接。想搞懂原理，点链接回去看；只想救火，留在这一页。

> 💡 **前端类比**：这就是后端版的「MDN 速查 + DevTools 快捷键表」。你不会去背 `Array.prototype` 的每个方法，但 `.map / .filter / .reduce` 这几个高频的你闭着眼都能敲。下面这些命令就是后端的「高频 API」。

约定：命令里凡是 `<尖括号>` 包起来的都是占位符，用的时候替换成你的真实值（容器名、进程号、Pod 名等）。带 `#` 的是注释，不用敲。

---

## Linux：文件 / 进程 / 网络 / 磁盘 / 服务

**什么时候用**：SSH 上一台陌生服务器，想知道「这机器现在啥状况、我的服务还活着吗、磁盘满没满」。这是后端的「上机第一分钟」基本功。
**详讲** → [第二十一章 Linux 服务器必会](/back-end/frontend-backend-guide/21-linux-server-essentials)

### 文件与目录

```bash
ls -lh                          # 列目录，-h 让文件大小变人类可读（KB/MB）
ls -lt                          # 按修改时间排序，最新的在最上面（找刚改过的文件）
pwd                             # 我现在在哪个目录
cd -                            # 回到上一个待过的目录（来回切很方便）
find /app -name "*.log"         # 在 /app 下递归找所有 .log 文件
find /app -name "*.log" -mtime -1   # 找最近 1 天内修改过的日志
du -sh *                        # 统计当前目录每个子项占多大（排查谁吃磁盘）
du -sh /var/log                 # 看某目录总大小
stat app.jar                    # 看文件的大小/权限/最后修改时间
cp -r src/ backup/              # 递归拷贝整个目录
mv old.log old.log.bak          # 重命名/移动文件
rm -rf logs/tmp/                # 递归强删目录（危险，路径看三遍再回车）
chmod +x deploy.sh              # 给脚本加可执行权限
ln -s /data/app /app            # 建软链接（类似前端的 npm link）
```

### 看文件内容

```bash
cat application.yml             # 一次性打印整个文件（小文件用）
less large.log                  # 翻页看大文件（q 退出，/ 搜索，G 跳到末尾）
head -n 50 app.log              # 看头 50 行
tail -n 100 app.log             # 看尾 100 行（看最近的日志）
tail -f app.log                 # 实时追踪日志，新日志滚动打出来（救火必备）
tail -f app.log | grep ERROR    # 实时只看含 ERROR 的行
grep -n "OrderNotFound" app.log # 在文件里搜关键字并显示行号
grep -rn "RtData.fail" src/     # 在整个目录递归搜代码里的某段文字
grep -C 5 "Exception" app.log   # 命中行的上下各 5 行一起打（看异常前后文）
wc -l app.log                   # 数这个文件有多少行
```

> 💡 **前端类比**：`tail -f` 就是后端的「实时 console」。你在浏览器盯 Network 面板看请求滚动，运维盯 `tail -f` 看日志滚动，是一回事。`grep` 则相当于 DevTools 里的过滤框。

### 进程

```bash
ps -ef | grep java              # 找所有 java 进程（svc-ai 在不在就靠它）
ps -ef | grep [s]vc-canvas      # 同上但用 [s] 技巧避免 grep 自己也被匹配出来
jps -l                          # 专列 JVM 进程，直接给出主类全名（比 ps 更准）
top                             # 实时看 CPU/内存占用排行（按 P 排 CPU，M 排内存，q 退出）
top -p <pid>                    # 只盯某一个进程
free -h                         # 看整机内存还剩多少（human readable）
kill <pid>                      # 优雅停止进程（发 SIGTERM，给它机会清理）
kill -9 <pid>                   # 强杀（发 SIGKILL，最后手段，不给清理机会）
pkill -f svc-auth               # 按命令行关键字杀进程（懒得先查 pid）
nohup java -jar svc-ai.jar &    # 后台常驻启动，关掉终端也不退（日志进 nohup.out）
```

> ⚠️ `kill -9` 是「拔电源」级别的操作。Spring 应用收到普通 `kill` 会触发优雅关机（关连接池、注销注册中心），`-9` 直接腰斩，可能留下脏连接。先试普通 `kill`，等几秒不退再 `-9`。

### 网络

```bash
ss -tlnp                        # 看本机所有正在监听的端口及对应进程（取代老旧的 netstat）
ss -tnp | grep 6379             # 查谁连着 Redis 的 6379 端口
lsof -i:8080                    # 查 8080 端口被哪个进程占了（端口冲突时用）
curl -i http://localhost:8080/actuator/health   # 本机测服务健不健康
ping mongodb-prod               # 测能不能连通某台主机（通不通）
telnet redis-host 6379          # 测某个 IP:端口 通不通（连得上说明网络+端口都没问题）
nc -zv mongodb-host 27017       # 同上，nc 更现代，-z 只探测不发数据
dig svc-gateway.internal        # 查域名解析到哪个 IP（DNS 问题时用）
traceroute 8.8.8.8              # 看到目标主机经过哪些网络节点（排查链路慢/不通）
```

> **详讲** → [第二十二章 后端必懂的网络](/back-end/frontend-backend-guide/22-networking-for-backend)

### 磁盘

```bash
df -h                           # 看各分区磁盘用了多少、还剩多少（磁盘报警第一条）
df -i                           # 看 inode 用量（文件数太多也会「满」，但 df -h 看不出来）
du -sh /var                     # 看某个目录占多大（从大目录往下逐层缩小范围）
ncdu /var                       # 交互式磁盘分析（如果装了，比 du 直观）
```

> ⚠️ 磁盘 100% 是后端最常见、也最容易忽视的故障。日志写不进去、数据库拒绝写入，表现千奇百怪，但 `df -h` 一眼就破案。养成习惯：服务异常先 `df -h`。

### systemd 服务

```bash
systemctl status svc-ai         # 看服务状态（active / failed）和最近几行日志
systemctl start svc-ai          # 启动服务
systemctl restart svc-ai        # 重启服务
systemctl stop svc-ai           # 停止服务
systemctl enable svc-ai         # 设为开机自启
journalctl -u svc-ai -f         # 实时追该服务由 systemd 收集的标准输出日志
journalctl -u svc-ai --since "10 min ago"   # 看最近 10 分钟的服务日志
```

---

## Docker：构建 / 运行 / 排查容器

**什么时候用**：你的服务都跑在容器里。「进容器看一眼、看容器日志、查容器为啥起不来」是日常。
**详讲** → [第二十三章 Docker 实战](/back-end/frontend-backend-guide/23-docker-in-practice)

```bash
docker build -t svc-ai:1.2.0 .          # 用当前目录的 Dockerfile 构建镜像并打 tag
docker images                            # 列出本机所有镜像
docker ps                                # 看正在运行的容器（最常用）
docker ps -a                             # 看所有容器，包括已退出的（查为啥挂了）
docker run -d --name svc-ai -p 8080:8080 svc-ai:1.2.0   # 后台跑一个容器并映射端口
docker logs svc-ai                       # 看容器的全部日志
docker logs -f --tail 100 svc-ai         # 实时追最近 100 行日志（救火常用）
docker exec -it svc-ai sh                # 进入运行中的容器，开个 shell 在里面看
docker exec -it svc-ai env               # 不进容器，直接看它的环境变量（查配置注没注进去）
docker inspect svc-ai                    # 看容器的完整配置（端口/挂载/网络/IP）
docker stats                             # 实时看各容器的 CPU/内存占用（容器版 top）
docker stop svc-ai                       # 优雅停止容器
docker rm svc-ai                         # 删除已停止的容器
docker rmi svc-ai:1.2.0                  # 删除镜像
docker cp svc-ai:/app/heapdump.hprof .   # 把容器里的文件（如堆转储）拷到本机分析
docker system df                         # 看 Docker 占了多少磁盘（镜像/容器/卷）
docker system prune                      # 清理悬空镜像和停止的容器（磁盘满时救急）
```

### docker compose

```bash
docker compose up -d                     # 按 compose.yml 后台拉起所有服务
docker compose ps                        # 看本 compose 项目下各服务状态
docker compose logs -f svc-canvas        # 实时追某个服务的日志
docker compose down                      # 停止并删除本 compose 项目的所有容器
docker compose up -d --build svc-ai      # 只重建并重启某一个服务
docker compose exec svc-ai sh            # 进某个服务的容器
```

> 💡 **前端类比**：`docker exec -it svc-ai sh` 进容器，约等于你 `ssh` 到一台「专门只为这个服务准备的迷你 Linux」。容器里啥都精简，`ls`、`cat`、`grep` 还在，但很多容器没装 `vim`、`top`，别惊讶。

---

## kubectl：操作 Kubernetes

**什么时候用**：生产用 K8s 编排时，「我的 Pod 跑起来没、为啥一直重启、日志在哪、怎么进去看、流量够不够要不要扩」全靠它。
**详讲** → [第二十四章 Kubernetes 实战](/back-end/frontend-backend-guide/24-kubernetes-in-practice)

```bash
kubectl get pods                         # 列当前命名空间所有 Pod（看 STATUS 和 RESTARTS）
kubectl get pods -n prod -o wide         # 指定命名空间，-o wide 多显示 Pod 所在节点和 IP
kubectl get pods -w                      # 持续监听 Pod 状态变化（发布时盯着滚动）
kubectl get svc                          # 列 Service（看 ClusterIP 和暴露的端口）
kubectl get deploy                       # 列 Deployment（看副本数 READY 几/几）
kubectl describe pod <pod>               # 看 Pod 详情和 Events（查为啥起不来的第一手线索）
kubectl logs <pod>                       # 看 Pod 日志
kubectl logs -f --tail 200 <pod>         # 实时追最近 200 行
kubectl logs <pod> --previous            # 看上一个挂掉的容器的日志（排查崩溃重启）
kubectl logs <pod> -c svc-ai             # 多容器 Pod 里指定看哪个容器
kubectl exec -it <pod> -- sh             # 进 Pod 开 shell（注意 -- 后面才是容器内命令）
kubectl exec -it <pod> -- env            # 看 Pod 注入的环境变量
kubectl apply -f deploy.yaml             # 按 yaml 创建/更新资源（声明式，K8s 的核心姿势）
kubectl delete pod <pod>                 # 删 Pod（Deployment 管的会自动重建，等于重启）
kubectl rollout status deploy/svc-ai     # 看一次发布滚没滚完
kubectl rollout restart deploy/svc-ai    # 滚动重启（不改镜像，重新拉起所有 Pod）
kubectl rollout undo deploy/svc-ai       # 回滚到上一个版本（发版出事的救命稻草）
kubectl scale deploy/svc-ai --replicas=5 # 把副本数扩到 5（流量大了手动扩容）
kubectl top pods                         # 看各 Pod 实时 CPU/内存（需装 metrics-server）
kubectl top nodes                        # 看各节点资源水位
kubectl get events --sort-by=.lastTimestamp   # 按时间看集群最近发生的事件
kubectl port-forward <pod> 8080:8080     # 把 Pod 端口转发到本机，本地直连调试
```

> 💡 **前端类比**：`kubectl describe pod` 的 Events 区域，就是后端版的「构建失败时 CI 那一长串报错」。Pod 起不来八成不用看日志（容器还没起来哪来日志），先 `describe` 看 Events——`ImagePullBackOff`（镜像拉不下来）、`CrashLoopBackOff`（起来就崩）、`OOMKilled`（内存超限被杀）都写在这。

---

## JVM 诊断：jps / jstack / jmap / jstat / jcmd

**什么时候用**：Java 进程「CPU 飙到 100%、内存一直涨、突然卡住没响应」。这是后端面试和实战的硬核区。
**详讲** → [第二十八章 排查实战手册](/back-end/frontend-backend-guide/28-troubleshooting-playbook) ｜ [第十八章 JVM 内存模型](/back-end/frontend-backend-guide/18-jvm-memory-model)

```bash
jps -l                          # 列出所有 JVM 进程及主类（先拿到目标进程的 pid）
jstack <pid>                    # 打印所有线程的栈（查死锁、查线程卡在哪）
jstack <pid> > stack.txt        # 把线程栈存文件，慢慢分析
jmap -histo <pid> | head -30    # 看堆里对象数量排行（谁占内存最多）
jmap -dump:live,format=b,file=heap.hprof <pid>   # 导出堆快照，拖到 MAT 里分析内存泄漏
jstat -gc <pid> 1000            # 每 1 秒打一次 GC 统计（看 GC 是不是太频繁）
jstat -gcutil <pid> 1000 10     # 每秒打 GC 各区使用率，共打 10 次
jinfo -flags <pid>              # 看进程实际生效的 JVM 启动参数
jcmd <pid> Thread.print         # 等价于 jstack，jcmd 是更现代的统一入口
jcmd <pid> GC.heap_info         # 看堆各区当前大小
jcmd <pid> VM.flags             # 看所有 VM 参数
jcmd <pid> GC.heap_dump heap.hprof   # 用 jcmd 导堆快照
```

### CPU 飙高定位命令链（背下来，面试和救火都用得上）

症状：监控告警某台机器某个 java 进程 CPU 长期 90%+。目标：定位到是**哪一行代码**在烧 CPU。

```bash
# 1. 找出 CPU 最高的 java 进程 pid（假设查到是 12345）
top

# 2. 看这个进程内部哪个线程最吃 CPU，-H 显示线程级，-p 指定进程
top -Hp 12345
```

**预期输出（节选）：**

```text
   PID USER      PR  NI    VIRT    RES    SHR S  %CPU  %MEM    TIME+ COMMAND
 12361 deploy    20   0 8123456 1.2g   18m R  98.7   7.5   3:21.40 java
 12362 deploy    20   0 8123456 1.2g   18m S   0.3   7.5   0:00.12 java
```

**怎么读**：`%CPU` 那列 `98.7` 的线程 PID 是 `12361`，就是它在烧 CPU。注意这是**十进制**的线程 ID，而 `jstack` 输出里的线程 ID 是**十六进制**，所以下一步要先转换。

```bash
# 3. 把十进制线程号 12361 转成十六进制（jstack 里用十六进制标线程）
printf "%x\n" 12361
```

**预期输出：**

```text
3049
```

```bash
# 4. 在 jstack 输出里搜这个十六进制 nid，看它卡在哪段代码
jstack 12345 | grep -A 30 "nid=0x3049"
```

**预期输出（节选）：**

```text
"svc-canvas-task-worker-3" #47 daemon prio=5 os_prio=0 tid=0x... nid=0x3049 runnable
   java.lang.Thread.State: RUNNABLE
        at com.app.canvas.service.TaskScheduler.scanPendingTasks(TaskScheduler.java:88)
        at com.app.canvas.service.TaskScheduler.lambda$loop$0(TaskScheduler.java:52)
```

**怎么读**：线程名 `svc-canvas-task-worker-3`，状态 `RUNNABLE`（在跑，不是在等），栈顶停在 `TaskScheduler.java:88`。打开这行代码看——多半是个没有 sleep 的 `while(true)` 空轮询。**结论**：定位到 `svc-canvas` 的任务扫描线程在空转烧 CPU，去给那个循环加退避或换成阻塞队列。

> 💡 **前端类比**：这套链路就是后端版的「Performance 面板录一段、找到那根最高的火焰图柱子、点进去看是哪个函数」。只不过浏览器给你图形界面，服务器要你手动 `top -Hp` → `printf %x` → `jstack` 三步拼出来。背熟这三步，你比大多数后端新人都强。

---

## Arthas：线上不停机诊断

**什么时候用**：问题只在线上复现、不敢重启、又想看「某个方法到底被传了什么参、返回了什么、耗时多少、为啥走错分支」。Arthas 是阿里开源的 Java 在线诊断神器，attach 到进程上动态观测，无需改代码重启。

```bash
java -jar arthas-boot.jar       # 启动 Arthas，它会列出本机 java 进程让你选一个 attach
dashboard                       # 总览面板：线程、内存、GC、运行环境一屏看全（进来先敲它）
thread                          # 列所有线程及 CPU 占用（找最忙的线程）
thread -n 3                     # 直接列出最忙的前 3 个线程的栈（CPU 飙高一键定位）
thread -b                       # 一键找出当前的死锁线程
trace com.app.canvas.service.TaskService submit   # 看 submit 方法内部每一步调用的耗时（找慢在哪）
watch com.app.user.UserService deductQuota "{params,returnObj}" -x 2   # 观测扣配额方法的入参和返回值
watch com.app.user.UserService deductQuota "{params,throwExp}" -e      # 只在它抛异常时打出入参和异常
jad com.app.auth.JwtUtil        # 反编译某个类，确认线上跑的真是你以为的那版代码
jad --source-only com.app.auth.JwtUtil verifyToken   # 只反编译某个方法
ognl '@com.app.Config@INSTANCE'   # 直接读静态字段/调用静态方法，查运行时配置值
stop                            # 退出 Arthas（会还原所有增强，不影响线上）
```

> 💡 **前端类比**：`watch` 就是「线上版的 `console.log` 打点」——但你不用改代码、不用重新发布、不用等下次复现。想象 React 出 bug，你能直接在生产环境给某个函数包一层日志看它的入参出参，看完即撤、毫无痕迹。Arthas 给 Java 后端的就是这种能力。`trace` 则像 `console.time / console.timeEnd` 自动铺满整条调用链。

---

## redis-cli：操作 Redis

**什么时候用**：查缓存对不对、限流计数器涨到多少、分布式锁卡住没释放、某个 key 为啥不过期。
**详讲** → [第十二章 Redis 实战](/back-end/frontend-backend-guide/12-redis-in-practice) ｜ [Redis 与 Java](/back-end/database/redis/java-redis)

```bash
redis-cli                                # 连本机默认 6379
redis-cli -h redis-prod -p 6379 -a <pwd> # 连远程、带密码
redis-cli ping                           # 探活，回 PONG 说明通了
scan 0 match svc-ai:quota:* count 100     # 安全地分批扫描 key（生产环境用这个，别用 keys）
get user:1001:profile                     # 取一个 string 值
ttl lock:task:8899                        # 看这个 key 还有几秒过期（-1 永不过期，-2 不存在）
type ratelimit:ip:1.2.3.4                 # 看 key 是什么类型（string/hash/zset...）
hgetall user:1001:quota                   # 取整个 hash 的所有字段
zrange task:queue 0 -1 withscores         # 看有序集合全部成员及分数（任务队列常用 zset）
del lock:task:8899                        # 删 key（手动释放卡死的分布式锁，谨慎）
info memory                               # 看 Redis 内存用量（used_memory_human 那行）
info clients                              # 看当前连接数（连接打满时查它）
dbsize                                    # 看当前库有多少个 key
monitor                                   # 实时打印所有正在执行的命令（调试用，线上慎开很耗性能）
slowlog get 10                            # 看最近 10 条慢命令（谁拖慢了 Redis）
```

> ⚠️ `keys *` 和 `monitor` 在生产是「禁忌命令」：`keys` 会一次性遍历全库阻塞 Redis（单线程的它一卡，所有业务请求都等），`monitor` 会把每条命令都推给你极耗性能。线上一律用 `scan` 代替 `keys`，`monitor` 只在低峰期开几秒就关。

---

## curl：手动测接口

**什么时候用**：不开 Postman、不写代码，直接在服务器上「就地」测一个接口通不通、返回啥、带没带上配额头。它就是后端命令行里的 `axios`。
**详讲** → [第三十四章 API 设计](/back-end/frontend-backend-guide/34-api-design) ｜ [Web 安全基础](/front-end/the-basics/network-basics/webSafety)

```bash
curl http://localhost:8080/actuator/health           # 最快的健康检查
curl -i http://localhost:8080/api/users/1001          # -i 连响应头一起打（看状态码和自定义头）
curl -v http://localhost:8080/api/users/1001          # -v 详细模式，连 TLS 握手和请求头都打（排查连不上）
curl -s http://localhost:8080/api/tasks | jq          # -s 静音进度条，管道给 jq 美化 JSON
curl -w "%{http_code} %{time_total}s\n" -o /dev/null -s http://localhost:8080/health   # 只看状态码和耗时
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"123456"}'       # POST 一段 JSON（测登录拿 token）
curl -X POST http://localhost:8080/api/ai/generate \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"prompt":"a cat"}'                              # 带 token 调需要登录的接口（测生图）
curl -o result.png http://localhost:8080/api/oss/file/8899   # 把响应体下载存成文件
curl --connect-timeout 3 --max-time 10 http://svc-ai:8080/health   # 设连接和总超时（探活别死等）
```

**一段真实的 `curl -i` 输出长这样：**

```text
HTTP/1.1 200 OK
Content-Type: application/json
X-Quota-Remaining: 47

{"code":0,"msg":"ok","data":{"id":1001,"name":"alice"}}
```

**怎么读**：状态行 `200 OK` 说明请求成功；自定义头 `X-Quota-Remaining: 47` 是 `svc-user` 返回的剩余配额；响应体里 `"code":0` 是项目统一响应 `RtData` 的成功码（`RtData.ok(data)` 序列化出来就是这个形状），`data` 才是真正的业务数据。如果 `code` 非 0，那就是 `RtData.fail(msg)`，`msg` 里有人话错误原因。**结论**：HTTP 200 不代表业务成功，后端还要再看 `code`——这跟前端 axios 拦截器里既判 HTTP 状态又判 `res.data.code` 是一回事。

> 💡 **前端类比**：`curl` 就是命令行里的 `axios`。`-H` 是 `headers`，`-d` 是 `data`，`-X` 是 `method`。区别只在于：`curl` 跑在服务器上，能绕过浏览器跨域、能直连内网服务（`http://svc-ai:8080`），这些都是浏览器里的 `fetch` 做不到的。

---

## 小结

- 排查的黄金顺序通常是「**外往里**」：先 `curl` 健康检查 → 不通就看 `docker ps` / `kubectl get pods` 进程在不在 → 在但行为异常就 `logs` 看日志 → 日志看不出就 `exec` 进去 / 用 JVM 工具深挖。
- 服务异常先看三件套：`df -h`（磁盘满没满）、`free -h` / `top`（内存和 CPU）、日志里有没有 `ERROR`。一大半故障在这三步就破案了。
- **CPU 飙高三连**背下来：`top -Hp <pid>` → `printf "%x" <线程号>` → `jstack <pid> | grep <十六进制nid>`。这是把「机器在烧 CPU」翻译成「哪一行代码在烧」的标准动作。
- 生产环境有两条「禁忌命令」要牢记：Redis 别 `keys *`（用 `scan`），`kill` 优先用普通信号而非 `-9`，给 Spring 应用留优雅关机的机会。
- 这一页是「救火地图」，不是「教科书」。每个分区顶部的链接才是讲原理的地方——平时点链接深读，出事时留这一页复制粘贴。

### 自测

1. 一个 java 进程 CPU 飙到 100%，你要定位到具体哪行代码。请按顺序写出你会敲的命令链，并说明中间为什么要做一次进制转换。
2. 一个 Pod 状态一直是 `CrashLoopBackOff`，`kubectl logs` 里却什么都没有。你下一步应该敲哪条命令、去哪里找原因？为什么不是死盯 `logs`？
3. 你怀疑某个分布式锁卡死没释放，导致 `svc-canvas` 提交任务全卡住。用 redis-cli 怎么确认这个锁 key 还在、它的剩余过期时间是多少？为什么这里不能用 `keys`？

### 下一章

工具备齐了，最后把这门课串成一条可执行的成长路线 → [第九十三章 学习路线](/back-end/frontend-backend-guide/93-learning-path)。

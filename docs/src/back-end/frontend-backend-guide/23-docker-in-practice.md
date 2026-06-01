# Docker 实战

> 前端部署一个站点，你可能 `pnpm build` 出一个 `dist/`，丢到 Nginx 或 CDN 上就完事了——产物是「一堆静态文件」。
> 后端部署一个服务，要带上 JDK、依赖、配置、甚至时区和字符集，而且「在我机器上能跑」远远不够。
> Docker 就是把「运行这个服务所需的一切」打成一个**不可变的盒子**，让它在你的笔记本、同事的电脑、测试环境、线上 K8s 里，跑出**完全一样**的结果。

这一章纯实操。每个命令都给可复制的示例和像真的一样的输出，你跟着在自己机器上敲一遍，就能把咱们运行示例项目里的 `svc-*` 在本地一键跑起来。学完你就具备了进入 [Kubernetes 实战](/back-end/frontend-backend-guide/24-kubernetes-in-practice) 的前置知识——K8s 调度的就是这一章打出来的镜像。

> 前置：本章假设你已经装好 Docker Desktop（Windows/Mac）或 Docker Engine（Linux），命令行里 `docker version` 能正常输出。容器跑在 Linux 内核上，Linux 基础概念见 [Linux 服务器必会](/back-end/frontend-backend-guide/21-linux-server-essentials)。

---

## 23.1 先建立两个核心概念：镜像 vs 容器

这是 Docker 里最容易绕晕的一对概念，但对前端来说其实有现成的类比。

| 概念 | 是什么 | 前端类比 | 面向对象类比 |
| --- | --- | --- | --- |
| **镜像（Image）** | 一个只读的、打包好的「应用 + 运行环境」模板 | 你 `npm publish` 出去的那个 package（安装包） | `class`（类的定义） |
| **容器（Container）** | 由镜像启动起来的、正在运行的实例 | `npm install` 后真正在跑的那个进程 | `new Foo()`（类的实例） |

一句话：**镜像是「安装包 / 类」，容器是「运行中的程序 / 实例」。** 同一个镜像可以同时启动 N 个容器，就像同一个 `class` 可以 `new` 出 N 个对象。比如线上 `svc-ai` 镜像只有一个，但它同时跑着 3 个容器（3 个副本）对外提供服务。

```text
   镜像 (Image)                容器 (Container)
   svc-ai:1.4.0                 ┌─ svc-ai 实例 #1  (running)
   ┌───────────────┐  docker    ├─ svc-ai 实例 #2  (running)
   │ JRE 17        │   run      └─ svc-ai 实例 #3  (running)
   │ + app.jar     │ ─────────►
   │ + 配置/字体    │   一个镜像可以启动出多个容器
   └───────────────┘   (只读模板)        (可读写、有生命周期)
```

### 为什么后端离不开容器

**问题场景**：你在本地用 JDK 17 跑 `svc-canvas` 一切正常，推上去测试环境却启动失败，报 `UnsupportedClassVersionError`——因为测试服务器装的是 JDK 11。这就是经典的「在我机器上是好的」（works on my machine）。

容器解决三件事：

- **环境一致**：JDK 版本、系统库、时区、字符集都被锁进镜像，你机器和线上跑的是同一个盒子，从根上消灭环境差异。
- **隔离**：每个容器有自己独立的文件系统、进程空间、网络。`svc-ai` 容器里装的字体、临时文件，不会污染 `svc-user` 容器，也不会弄脏宿主机。
- **可移植**：镜像是个标准格式的文件，能在任何装了 Docker 的地方原样跑起来，从开发笔记本到云上 K8s。

> **前端类比**：你早就受够了「我本地 Node 18，CI 上 Node 16，构建结果不一样」。容器就是把 **node_modules + Node 运行时 + 你的代码 + 环境变量** 一起冻进一个不可变的包里——交付的不再是「代码 + 一份 README 说明该装啥」，而是「一个开箱即跑的整体」。

### 镜像分层（layer）

镜像不是一个铁板一块的大文件，而是**一层层叠起来**的，每一层是一次文件系统的改动，且只读、可被复用。

```text
┌─────────────────────────────┐  ← 你的层：COPY app.jar (每次发版只变这层)
├─────────────────────────────┤  ← 你的层：装字体、设时区
├─────────────────────────────┤  ← 基础层：eclipse-temurin:17-jre (JRE 运行时)
└─────────────────────────────┘  ← 基础层：Linux 根文件系统 (debian/ubuntu)
        多个镜像共享底层 → 省磁盘、拉取快、构建有缓存
```

这个设计有两个直接好处，后面写 Dockerfile 时你会反复用到：

- **缓存复用**：构建时如果某一层没变，Docker 直接用缓存，不重新执行。所以「先 COPY 依赖清单、装依赖，再 COPY 源码」能让你改业务代码时不必重新下载所有依赖。
- **节省空间与带宽**：`svc-auth`、`svc-user`、`svc-ai` 如果都基于同一个 `eclipse-temurin:17-jre` 基础镜像，那个 JRE 层在机器上只存一份。

> **前端类比**：和 Docker 分层缓存几乎一一对应的，是你 Dockerfile 之外早就熟悉的套路——先 `COPY package.json pnpm-lock.yaml`，`pnpm install`，再 `COPY` 源码。依赖没变就命中缓存、不重装。镜像分层就是把这套「不变的放底下、常变的放上面」的思路做进了文件系统。

---

## 23.2 常用命令逐个过（带真实输出）

下面每个命令都给「干什么 + 命令 + 像真的一样的输出」。建议你边读边在终端敲。

### 拉镜像与看本地镜像：`docker pull` / `docker images`

```bash
# 从镜像仓库（默认 Docker Hub）拉一个 Redis 镜像到本地
docker pull redis:7.2
```

预期输出：

```text
7.2: Pulling from library/redis
a2abf6c4d29d: Pull complete
c7a4e4382001: Pull complete
4044b9ba67c9: Pull complete
Digest: sha256:8f0f9b2e...
Status: Downloaded newer image for redis:7.2
docker.io/library/redis:7.2
```

怎么读：每个 `xxxx: Pull complete` 就是一**层**在下载，呼应上面讲的分层。`redis:7.2` 里冒号后面的 `7.2` 是 **tag（版本标签）**，不写默认是 `latest`——**生产环境永远别用 `latest`**，否则你不知道线上跑的到底是哪个版本，出问题无法复现。

```bash
docker images
```

```text
REPOSITORY   TAG       IMAGE ID       CREATED       SIZE
redis        7.2       a1b2c3d4e5f6   2 weeks ago   138MB
svc-ai       1.4.0     0f1e2d3c4b5a   3 hours ago   210MB
mongo        7.0       9a8b7c6d5e4f   5 days ago    712MB
```

`IMAGE ID` 是镜像的唯一标识，`SIZE` 是镜像大小——记住这个数字，后面「镜像瘦身」会回来对比。

### 启动容器：`docker run`（最重要的命令）

`docker run` 把镜像「new」成一个运行中的容器。它的参数你会天天用，逐个拆开看：

```bash
docker run \
  -d \                          # detach：后台运行，不占住你的终端
  --name svc-redis \            # 给容器起个名字，后续 stop/logs/exec 都用它
  -p 6379:6379 \                # 端口映射 宿主机:容器，让外部能访问容器里的服务
  -e TZ=Asia/Shanghai \         # 注入环境变量（这里设时区）
  -v redis-data:/data \         # 挂载数据卷，把容器里的 /data 持久化
  --network ai-net \            # 加入名为 ai-net 的自定义网络
  redis:7.2 \                   # 用哪个镜像
  redis-server --appendonly yes # 覆盖镜像默认启动命令（开 AOF 持久化）
```

逐个参数对前端来说什么意思：

| 参数 | 作用 | 前端类比 |
| --- | --- | --- |
| `-d` | 后台跑，终端立刻还给你 | `vite dev &` 后台起服务 |
| `-p 6379:6379` | 宿主机 6379 → 容器 6379 | Vite 的 `server.proxy` / 端口转发 |
| `-e KEY=VALUE` | 给容器进程注入环境变量 | `.env` 文件里的 `VITE_*` |
| `-v 名字:/路径` | 把数据存到容器之外，删容器不丢 | 把状态存进 localStorage 而非内存 |
| `--name` | 容器的固定名字 | 给定时器留个 ref 好清理 |
| `--network` | 让容器加入某个网络互相找得到 | 同一局域网内服务互相能访问 |

> **端口映射的坑**：`-p 8080:8080` 左边是宿主机端口、右边是容器内端口，**别记反**。如果你 `curl localhost:8080` 连不上，先确认容器内服务确实监听在右边那个端口、且不是只绑了 `127.0.0.1`。容器内服务一般要监听 `0.0.0.0` 才能被映射出来。

运行后输出一长串容器 ID 就代表起成功了：

```text
3f9a1c8b7e6d5a4f3b2c1d0e9f8a7b6c5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0a
```

### 看容器列表：`docker ps` / `docker ps -a`

```bash
docker ps          # 只看正在运行的容器
```

```text
CONTAINER ID   IMAGE        COMMAND                 STATUS         PORTS                    NAMES
3f9a1c8b7e6d   redis:7.2    "redis-server --ap…"    Up 2 minutes   0.0.0.0:6379->6379/tcp   svc-redis
```

```bash
docker ps -a       # -a (all)：连已停止/退出的容器也列出来
```

```text
CONTAINER ID   IMAGE         COMMAND          STATUS                     NAMES
3f9a1c8b7e6d   redis:7.2     "redis-server"   Up 2 minutes               svc-redis
8c7d6e5f4a3b   svc-ai:1.4.0  "java -jar app…"  Exited (1) 30 seconds ago  svc-ai
```

怎么读 `STATUS`：

- `Up 2 minutes`——正常运行中。
- `Exited (1) 30 seconds ago`——**容器挂了，退出码非 0**。退出码 `1` 通常是程序自己抛异常退出（比如 `svc-ai` 连不上数据库启动失败），`137` 表示被 OOM 杀掉（内存超限），`143` 是收到停止信号正常退出。看到 `Exited` 别慌，下一步就是去看它的日志。

### 看日志：`docker logs`（排查第一现场）

容器没界面、没 DevTools，[读懂日志](/back-end/frontend-backend-guide/26-reading-logs) 里说的「现场」在这里全靠 `docker logs`。

```bash
docker logs svc-ai            # 打印这个容器的全部日志
docker logs -f svc-ai         # -f (follow)：持续跟踪，像 tail -f
docker logs --tail 100 -f svc-ai   # 只看最后 100 行并持续跟踪
```

上面那个 `Exited (1)` 的 `svc-ai`，`docker logs` 一看就知道为啥挂：

```text
2026-06-01 10:21:03.118  INFO 1 --- [main] c.x.ai.SvcAiApplication : Starting SvcAiApplication
2026-06-01 10:21:08.402  WARN 1 --- [main] o.s.b.w.s.WebServerStartupException
2026-06-01 10:21:08.455 ERROR 1 --- [main] o.s.boot.SpringApplication :
    Application run failed
com.mongodb.MongoSocketOpenException: Exception opening socket
    at com.mongodb.internal.connection...
Caused by: java.net.ConnectException: Connection refused: mongo/172.18.0.2:27017
```

怎么读：`Connection refused: mongo/172.18.0.2:27017`——`svc-ai` 启动时连不上 MongoDB（容器名 `mongo`），多半是 Mongo 容器没起、或者两个容器不在同一个 network 里（互相找不到对方的容器名）。结论：先确认 `docker ps` 里 `mongo` 在跑，再确认它俩 `--network` 一致。网络互访细节见后面 23.4。

### 进容器里看一看：`docker exec -it`

有时候光看日志不够，你想进到容器内部 ls 文件、ping 别的服务、看进程。这就是 `docker exec`。

```bash
docker exec -it svc-redis bash     # -i 交互 -t 分配终端，进入容器的 bash
```

进去之后就像 SSH 到了一台只装了这个服务的迷你 Linux 上：

```text
root@3f9a1c8b7e6d:/data# redis-cli ping
PONG
root@3f9a1c8b7e6d:/data# ls
appendonlydir  dump.rdb
root@3f9a1c8b7e6d:/data# exit
```

> 注意：很多瘦身过的镜像（比如基于 `alpine` 或 `distroless`）里**没有 `bash`**，要用 `sh`：`docker exec -it 容器 sh`。如果连 `sh` 都没有（distroless），那就只能从外面排查，进不去。

> **前端类比**：`docker exec -it 容器 bash` 约等于「打开这个运行实例的控制台」——你能在运行现场敲命令、看状态。区别是后端这个「控制台」是个真实的 shell，能 ls、能 ping、能看进程。

### 看资源占用：`docker stats`

```bash
docker stats          # 实时刷新所有容器的 CPU/内存/网络，像 top
```

```text
CONTAINER ID   NAME       CPU %    MEM USAGE / LIMIT     MEM %    NET I/O
3f9a1c8b7e6d   svc-redis  0.30%    12.5MiB / 512MiB      2.44%    1.2kB / 0B
8c7d6e5f4a3b   svc-ai     180.5%   1.85GiB / 2GiB        92.5%    4.5MB / 2.1MB
```

怎么读：`svc-ai` 内存 `1.85GiB / 2GiB`、`MEM % 92.5%`——快到内存上限了，再涨就会被 OOM 杀掉（变成 `Exited (137)`）。`CPU % 180.5%` 表示用了将近两个核（100% = 一个核）。这是定位 [OOM 与内存泄漏](/back-end/frontend-backend-guide/20-oom-memory-leak) 的第一道仪表盘。

### 停止与清理：`docker stop` / `rm` / `rmi`

```bash
docker stop svc-redis      # 优雅停止容器（先发 SIGTERM，等一会儿再 SIGKILL）
docker rm svc-redis        # 删除已停止的容器（容器没了，但镜像还在）
docker rm -f svc-redis     # -f：连正在运行的也强制删
docker rmi redis:7.2       # 删镜像（rmi = remove image，容器全删光才能删镜像）
```

一个常用清理组合（**生产慎用**，会删掉所有没在用的东西）：

```bash
docker system prune        # 清理停止的容器、悬空镜像、无用网络，腾磁盘
```

记住区别：`rm` 删的是**容器（实例）**，`rmi` 删的是**镜像（模板）**。容器删了镜像还在，下次 `docker run` 立刻能再起一个。

---

## 23.3 给 Spring Boot 写 Dockerfile（多阶段构建）

Dockerfile 是「怎么把你的代码打成镜像」的脚本，类似前端的「构建配置」。咱们给 `svc-ai` 写一个，用**多阶段构建**：第一阶段用带 Maven 的镜像编译打包（需要完整 JDK + Maven，几百 MB），第二阶段只把产物 jar 拷进一个精简的 JRE 镜像里——**构建工具不进最终镜像**，体积小很多。

> **前端类比**：和你 CI 里「用一个装了 node + pnpm 的镜像 `pnpm build` 出 `dist/`，再把 `dist/` 丢进一个只有 Nginx 的小镜像」是完全一样的思路。重的构建环境用完即弃，交付物只留运行时需要的部分。

`svc-ai/Dockerfile`：

```dockerfile
# ---------- 第一阶段：构建（build stage）----------
# FROM 指定基础镜像；这一层带 JDK 17 + Maven，用来编译打包。AS build 给这阶段起名
FROM maven:3.9-eclipse-temurin-17 AS build

# WORKDIR 设定容器内的工作目录，后续命令都在这个目录下执行（不存在会自动创建）
WORKDIR /app

# 先单独 COPY 依赖描述文件，再下载依赖 —— 利用分层缓存：pom.xml 没变就不重新下依赖
COPY pom.xml .
# RUN 在构建时执行命令；go-offline 把依赖先拉满，这一层被缓存后改代码就秒过
RUN mvn -B dependency:go-offline

# 再 COPY 源码（常变，放在依赖之后，避免每次改代码都重下依赖）
COPY src ./src
# 真正编译打包，跳过测试加快构建（测试在 CI 单独跑）
RUN mvn -B clean package -DskipTests

# ---------- 第二阶段：运行（runtime stage）----------
# 换成精简的 JRE 镜像（只有运行时，没有编译器和 Maven），体积小、攻击面小
FROM eclipse-temurin:17-jre

# 设时区，避免日志时间和数据库时间差 8 小时（中国常见坑）
ENV TZ=Asia/Shanghai

WORKDIR /app

# 从上一阶段(build)把打好的 jar 拷过来，重命名为 app.jar。--from=build 是关键
COPY --from=build /app/target/*.jar app.jar

# EXPOSE 声明容器对外暴露的端口（仅文档作用，真正映射靠 docker run -p）
EXPOSE 8080

# ENTRYPOINT 是容器启动时执行的命令 —— 启动 Spring Boot 应用
ENTRYPOINT ["java", "-jar", "app.jar"]
```

逐行要点：

- `FROM ... AS build` / `FROM ...`：两个 `FROM` = 两个阶段。`--from=build` 让第二阶段能拿到第一阶段的产物，而第一阶段那几百 MB 的 Maven/JDK 不会进最终镜像。
- `WORKDIR`：相当于在容器里 `cd`，之后的 `COPY`、`RUN`、`ENTRYPOINT` 都相对它。
- `COPY`：把宿主机（或上一阶段）的文件复制进镜像层。
- `RUN`：构建镜像时执行（编译、装依赖），结果固化成一层。
- `EXPOSE`：声明性的，告诉别人「我用 8080」，不等于自动映射。
- `ENTRYPOINT`：容器**运行时**执行的命令，整个容器的生命周期 = 这个进程的生命周期，进程一退出容器就 `Exited`。

构建镜像：

```bash
# -t 给镜像打 tag（名字:版本），. 是构建上下文（Dockerfile 所在目录）
docker build -t svc-ai:1.4.0 .
```

```text
[+] Building 48.7s (15/15) FINISHED
 => [build 1/6] FROM maven:3.9-eclipse-temurin-17                      0.0s
 => CACHED [build 4/6] RUN mvn -B dependency:go-offline                0.0s   ← 命中缓存
 => [build 6/6] RUN mvn -B clean package -DskipTests                  31.2s
 => [stage-1 3/3] COPY --from=build /app/target/*.jar app.jar          0.3s
 => => naming to docker.io/library/svc-ai:1.4.0                        0.0s
```

`CACHED` 那一行就是分层缓存生效——只改了业务代码、没动 `pom.xml`，下载依赖那层直接跳过。

跑起来验证：

```bash
docker run -d --name svc-ai -p 8080:8080 svc-ai:1.4.0
docker logs -f svc-ai
```

```text
2026-06-01 11:02:15.880  INFO 1 --- [main] c.x.ai.SvcAiApplication :
    Started SvcAiApplication in 4.21 seconds (process running for 4.9)
2026-06-01 11:02:15.901  INFO 1 --- [main] o.s.b.w.embedded.tomcat.TomcatWebServer :
    Tomcat started on port(s): 8080 (http)
```

看到 `Started ... in 4.21 seconds` 和 `Tomcat started on port(s): 8080` 就成了，`curl localhost:8080/actuator/health` 应返回 `{"status":"UP"}`。

> **镜像瘦身一句话**：用多阶段构建（构建工具不进最终镜像）+ 选小基础镜像（`eclipse-temurin:17-jre` 而非完整 JDK，极致可用 `-jre-alpine` 或 distroless）+ 别把测试/缓存/`.git` 拷进去（写好 `.dockerignore`），就能把一个动辄 700MB+ 的镜像压到 200MB 上下。

---

## 23.4 数据卷与网络

### 数据卷：让数据活过容器的生命周期

**关键认知**：容器是「可丢弃」的——`docker rm` 一删，容器里写的所有文件全没了。这正呼应了 [后端思维](/back-end/frontend-backend-guide/01-backend-mindset) 里的「无状态」：进程随时会被删，状态必须放到进程之外。数据卷就是把「需要持久化的数据」存到容器之外的机制。

```bash
# 不挂卷：容器删了，Redis 里的数据全丢
docker run -d --name r1 redis:7.2
docker rm -f r1     # 数据没了

# 挂 volume：数据存在 Docker 管理的卷里，容器删了数据还在
docker run -d --name r2 -v redis-data:/data redis:7.2
docker rm -f r2                                  # 删容器
docker run -d --name r3 -v redis-data:/data redis:7.2   # 新容器挂同一个卷，数据还在
```

两种挂载方式，区别要分清：

| 方式 | 写法 | 数据存哪 | 典型用途 |
| --- | --- | --- | --- |
| **volume（命名卷）** | `-v redis-data:/data` | Docker 自己管理的目录（你不用关心具体路径） | 生产持久化数据库/缓存数据，**首选** |
| **bind mount（绑定挂载）** | `-v /home/me/conf:/app/conf` | 宿主机上你指定的真实目录 | 把本机的配置文件/代码挂进容器，开发调试常用 |

```bash
docker volume ls                 # 列出所有命名卷
docker volume inspect redis-data # 看这个卷的详情（实际存在宿主机哪个目录）
```

> **前端类比**：容器内存里的数据 = 组件 state（刷新即丢），数据卷 = localStorage / 后端数据库（刷新还在）。bind mount 则像 Vite 的 HMR——你改宿主机上的文件，容器里立刻看到，适合开发；volume 更像「交给系统托管的存储」，适合线上。

### 容器网络：靠自定义网络 + 容器名互访

默认情况下各容器网络是隔离的。要让 `svc-ai` 能访问 `mongo`、`redis`，得把它们放进**同一个自定义网络**，然后就能**直接用容器名当主机名**互相访问——Docker 内置了 DNS，把容器名解析成容器 IP。

```bash
# 1) 建一个自定义网络
docker network create ai-net

# 2) 让相关容器都加入这个网络
docker run -d --name mongo  --network ai-net mongo:7.0
docker run -d --name redis  --network ai-net redis:7.2
docker run -d --name svc-ai --network ai-net -p 8080:8080 svc-ai:1.4.0
```

这样 `svc-ai` 的配置里就能写容器名，而不是写死 IP：

```yaml
# svc-ai 的 application.yml —— host 直接写容器名 mongo / redis
spring:
  data:
    mongodb:
      uri: mongodb://mongo:27017/ai      # "mongo" 会被 Docker DNS 解析到 mongo 容器
  redis:
    host: redis                          # "redis" 同理
    port: 6379
```

进 `svc-ai` 容器里验证一下能不能解析到 `mongo`：

```bash
docker exec -it svc-ai sh
# 容器内执行：
ping -c 1 mongo
```

```text
PING mongo (172.18.0.2): 56 data bytes
64 bytes from 172.18.0.2: seq=0 ttl=64 time=0.092 ms
```

`mongo` 被解析成了 `172.18.0.2`，说明同网络内靠容器名互访是通的。

> **呼应服务发现**：这套「用名字找服务、不写死 IP」正是后端**服务发现**的雏形。本地 Docker 里靠容器名 + Docker DNS；到了线上 K8s，则升级为靠 **Service 名 + 集群 DNS**（`svc-ai` 调 `svc-user` 直接用服务名）。容器/Pod 的 IP 会变，但名字稳定——所以代码里永远写名字，不写 IP。这块在 [Kubernetes 实战](/back-end/frontend-backend-guide/24-kubernetes-in-practice) 展开。

> **前端类比**：相当于你前端代码里写 `axios.get('/api/user')` 走 Nginx/网关代理，而不是把后端 IP 硬编码进去。改后端机器时前端代码不用动——靠名字解耦，正是同一个道理。

---

## 23.5 docker compose：一条命令起齐本地依赖

开发 `svc-canvas` 时，它依赖 MongoDB + Redis + RocketMQ。一个个 `docker run` 又长又容易记错参数。**docker compose** 让你把这些服务写进一个 `compose.yml`，`docker compose up -d` 一键全起。

> **前端类比**：`compose.yml` 之于一组容器，约等于 `package.json` 的 `scripts` + `pnpm-workspace.yaml` 之于一个 monorepo——把「要起哪些东西、各自什么配置、谁依赖谁」声明在一个文件里，一条命令拉起整套。

在项目根目录建 `compose.yml`，一键起本地开发依赖：

```yaml
# compose.yml —— 本地开发用：一键起 MongoDB + Redis + RocketMQ
services:
  mongo:
    image: mongo:7.0
    container_name: mongo
    ports:
      - "27017:27017"          # 宿主机:容器，方便本机用 Compass 连
    environment:
      MONGO_INITDB_ROOT_USERNAME: root
      MONGO_INITDB_ROOT_PASSWORD: example
      TZ: Asia/Shanghai
    volumes:
      - mongo-data:/data/db    # 命名卷持久化，删容器不丢数据
    networks:
      - ai-net

  redis:
    image: redis:7.2
    container_name: redis
    command: ["redis-server", "--appendonly", "yes"]  # 开 AOF 持久化
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    networks:
      - ai-net

  # RocketMQ 需要 namesrv（名字服务）+ broker（消息存储）两个进程
  rmq-namesrv:
    image: apache/rocketmq:5.1.4
    container_name: rmq-namesrv
    command: sh mqnamesrv               # 启动 NameServer
    ports:
      - "9876:9876"
    networks:
      - ai-net

  rmq-broker:
    image: apache/rocketmq:5.1.4
    container_name: rmq-broker
    depends_on:
      - rmq-namesrv                     # 声明依赖：broker 在 namesrv 之后启动
    command: sh mqbroker -n rmq-namesrv:9876 --enable-proxy   # 指向上面的 namesrv
    environment:
      TZ: Asia/Shanghai
    ports:
      - "8081:8081"                     # broker proxy 端口
    networks:
      - ai-net

# 顶层声明用到的命名卷（与上面 volumes 引用对应）
volumes:
  mongo-data:
  redis-data:

# 顶层声明自定义网络，所有 service 加入后可用服务名互访
networks:
  ai-net:
    driver: bridge
```

常用命令：

```bash
docker compose up -d        # 后台拉起 compose.yml 里所有服务（首次会自动拉镜像、建网络/卷）
docker compose ps           # 看这一组服务的状态
docker compose logs -f redis    # 跟踪某个服务的日志（不写服务名就是全部）
docker compose down         # 停止并删除这组容器和网络（命名卷默认保留）
docker compose down -v      # 连命名卷一起删（数据清空，慎用）
```

`docker compose up -d` 后看一眼状态：

```text
NAME          IMAGE                      STATUS              PORTS
mongo         mongo:7.0                  Up 12 seconds       0.0.0.0:27017->27017/tcp
redis         redis:7.2                  Up 12 seconds       0.0.0.0:6379->6379/tcp
rmq-namesrv   apache/rocketmq:5.1.4      Up 12 seconds       0.0.0.0:9876->9876/tcp
rmq-broker    apache/rocketmq:5.1.4      Up 10 seconds       0.0.0.0:8081->8081/tcp
```

四个都 `Up` 了，本地开发依赖就齐了。注意：compose 会**自动建一个网络并把所有 service 放进去**，所以服务之间天然能用服务名互访（上面 broker 直接写 `rmq-namesrv:9876`）——你不必再手动 `docker network create`。

> **本项目实践**：这套本地依赖起好后，你就可以在 IDE 里直接以 `local` profile 跑 `svc-canvas`，它的 `application-local.yml` 里把 mongo/redis/rocketmq 的 host 写成上面这些服务名（或 `localhost` + 映射端口），就能边写代码边连真实中间件调试，不用每次都打镜像。配置怎么按环境切换见 [配置与环境管理](/back-end/frontend-backend-guide/25-config-and-env)。

---

## 23.6 本项目里的 Docker 实践

咱们运行示例项目里，**每个 `svc-*` 服务都有自己的 `Dockerfile`**，结构和上面 `svc-ai` 那份基本一致（多阶段构建 + JRE 运行）。整体约定：

```text
ai-image-platform/
├─ compose.yml                ← 本地开发：起 mongo/redis/rocketmq
├─ svc-gateway/Dockerfile     ← 网关，对外暴露
├─ svc-auth/Dockerfile        ← 认证
├─ svc-user/Dockerfile        ← 用户/配额/支付
├─ svc-ai/Dockerfile          ← AI 生图
├─ svc-canvas/Dockerfile      ← 画布/任务编排（最复杂）
├─ svc-oss/Dockerfile         ← 文件存储
└─ cpt-* (共享组件，作为依赖被各 svc 打进 jar，不单独出镜像)
```

- 每个 `svc-*` 独立构建出一个镜像（如 `svc-canvas:1.4.0`），独立部署、独立扩缩容——这正是微服务「各自打包、各自上线」的好处。
- `cpt-*` 这些共享组件（`cpt-common`、`cpt-api` 等）是 Maven 依赖，会在构建阶段被 `mvn package` 打进各服务的 jar 里，**不单独出镜像**。
- 本地开发时：用 `compose.yml` 起中间件（mongo/redis/rmq），业务服务 `svc-*` 在 IDE 里跑，连这些中间件调试。
- 上线时：镜像由 CI 构建并推到镜像仓库，再由 K8s 拉取调度——见 [CI/CD 与部署](/back-end/frontend-backend-guide/36-cicd-deployment) 和 [Kubernetes 实战](/back-end/frontend-backend-guide/24-kubernetes-in-practice)。

一个典型的「本地起依赖 → 验证服务能跑」完整流程：

```bash
# 1) 起本地中间件
docker compose up -d

# 2) 构建并跑 svc-user（它依赖 mongo/redis，已在 compose 里）
docker build -t svc-user:dev ./svc-user
docker run -d --name svc-user --network ai-image-platform_ai-net -p 8082:8082 svc-user:dev

# 3) 看启动日志，确认连上中间件
docker logs -f svc-user

# 4) 打个健康检查
curl localhost:8082/actuator/health
```

```text
{"status":"UP","components":{"mongo":{"status":"UP"},"redis":{"status":"UP"}}}
```

`{"status":"UP"}` 且各依赖组件都 `UP`，说明 `svc-user` 容器化跑起来、且连上了 compose 起的中间件。注意第 2 步 `--network` 用的是 `ai-image-platform_ai-net`——compose 创建的网络名会自动带上**项目目录名前缀**，用 `docker network ls` 能查到真实名字。

---

## 小结

- **镜像 vs 容器** = 类 vs 实例 = 安装包 vs 运行中的程序：一个镜像能起多个容器。镜像是**只读分层模板**，分层带来缓存复用和空间节省。
- 容器解决「在我机器上是好的」：**环境一致、隔离、可移植**——把运行时 + 依赖 + 代码 + 配置冻成一个不可变的盒子。
- 必会命令：`pull/images`（拿镜像）、`run`（起容器，重点记 `-d -p -e -v --name --network`）、`ps -a`（看状态，会读 `Exited` 退出码）、`logs -f`（排查第一现场）、`exec -it 容器 bash`（进容器）、`stats`（看资源）、`stop/rm/rmi`（清理）。
- Spring Boot 用**多阶段构建**：Maven 阶段编译、JRE 阶段运行，构建工具不进最终镜像；`docker build -t 名字:版本 .`，靠分层缓存加速。
- **数据要持久化用数据卷**（volume 首选、bind mount 开发用），删容器不丢数据；**容器间靠自定义网络 + 容器名互访**（Docker DNS），代码里写名字不写 IP，这就是服务发现的雏形。
- `docker compose` 用一个 `compose.yml` 一键起齐本地依赖（mongo/redis/rocketmq），`up -d` / `logs -f` / `down` 三板斧搞定本地开发环境。

### 自测

1. 你 `docker run` 起了一个 Redis 没挂 `-v`，往里写了数据后 `docker rm -f` 删掉容器，再用同镜像起一个新容器，数据还在吗？为什么？要让数据活过容器删除应该怎么改？
2. `svc-ai` 容器 `docker ps -a` 显示 `Exited (1)`，日志里有 `Connection refused: mongo/172.18.0.2:27017`。请说出至少两个可能原因，以及你会依次执行哪些命令去确认。
3. 给 Spring Boot 写 Dockerfile 时，为什么要先 `COPY pom.xml` 并 `RUN mvn dependency:go-offline`，之后才 `COPY src`？把这两步顺序对调会有什么后果？这背后利用的是镜像的什么特性？

### 下一章

本地用 `docker run` / `compose` 手动起几个容器还能应付，但线上要管理几十个服务、上百个副本、自动扩缩容和故障自愈，就得交给编排系统了——进入 [Kubernetes 实战](/back-end/frontend-backend-guide/24-kubernetes-in-practice)，看 K8s 怎么调度这一章打出来的镜像。

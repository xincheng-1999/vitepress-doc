---
prev:
  text: "E2. 配置、环境与密钥"
  link: "/back-end/frontend-backend-guide/25-config-and-env"
next:
  text: "E4. 日志与第一次排错"
  link: "/back-end/frontend-backend-guide/26-reading-logs"
---

# 用 Compose 跑任务服务，怎样让数据留下来？

前面的任务 API 默认使用 H2 内存库，进程退出后任务就没了。现在换成 MySQL，并用 Compose 一起管理数据库和应用。我们顺着一次本机启动往下做，最后重建容器，再查同一条任务。

使用的是仓库现成的 `examples/backend-task-api`，仍是 Java 17，不需要自己补一套 Dockerfile。配置来源见 [上一课](./25-config-and-env)，进程、端口的观察方式见 [Linux](./21-linux-server-essentials)，接口命令沿用 [任务 API](./07-build-a-crud-api)。

这套初始 API 尚无登录和用户隔离，因此仅发布到宿主回环地址。容器化解决运行和依赖安排，不会自动补齐业务安全；公网交付前的扩展要求见 [综合实战](./38-capstone-feature-to-prod)。

## 1. 先确认本机能运行容器

安装 Docker Desktop，或者 Docker Engine + Compose v2 后，先检查：

```bash
docker version
docker compose version
```

第一条既要能看到客户端，也要能连接后台服务（Docker daemon）；第二条确认 Compose 插件可用。如果只有客户端，先检查 Desktop / daemon 是否启动、当前连接目标（context）是否正确。Compose 子命令不可用，则先按所用安装方式补齐插件，不必在此时修改 Java 代码。

**本机此前缺少可用的 Compose 插件 / Docker daemon，容器尚未实测；这轮文档修改也没有运行容器。** 下文描述的是读者操作步骤和预期结果，不是已经完成的启动、MySQL 集成或持久化验证记录。

## 2. 先把两项服务启动起来

进入仓库根目录下的示例目录，设置两个不同的本地专用随机口令，再运行：

```bash
cd examples/backend-task-api
export DB_PASSWORD='change-this-local-app-password'
export MYSQL_ROOT_PASSWORD='change-this-local-root-password'
docker compose config --quiet
docker compose up --build -d
docker compose ps
docker compose logs -f api
```

上面的口令是占位，不能复用真实凭据。`config --quiet` 只校验配置，不打印插值后的密码；不要把完整 `docker compose config` 或 inspect 输出直接贴到公开问题里。

Compose 会按配置创建 MySQL 和 API 两个**容器**。容器是由镜像创建的隔离运行实例；**镜像**则是用于创建它的构建产物，不是一个正在运行的进程。这里 MySQL 使用已有镜像，API 根据工程的 Dockerfile 构建，所以第一次还需要联网下载 Maven、JDK、MySQL 镜像与依赖，可能较慢。

`-d` 表示后台运行。`logs -f` 只是跟随日志，看到启动完成后按 Ctrl+C，是退出查看，不是停止服务。

预期 `docker compose ps` 显示 mysql 为 healthy，api 在运行，再从宿主请求：

```bash
curl -i http://127.0.0.1:8080/api/tasks
```

预期返回 200 和分页 JSON。当前 API 没有 Actuator，不能用并不存在的 `/actuator/health` 判断它是否就绪。Compose 的 `depends_on: service_healthy` 让 API 等 MySQL 初次健康后启动，但运行期间数据库仍可能断开，应用仍需处理失败和恢复。

## 3. 这次请求经过哪几个地址？

![宿主端口只向本机开放，API通过Compose服务名连接MySQL，数据库数据保存在卷中](/backend-guide/core-deploy-topology.svg)

宿主的浏览器或 curl 访问 `127.0.0.1:8080`，Compose 再把它送到 API 容器的 8080。API 使用 `mysql:3306` 连接数据库：`mysql` 是同一 Compose 网络里的服务名，3306 是数据库容器端口。

两边的 localhost 不能混用。API 容器里的 localhost 指自己，写 `localhost:3306` 就不会自动找到数据库容器。示例也没有为 MySQL 发布宿主端口，所以不能从宿主直接按某个映射端口连接它。

还有两层监听需要分开看。Compose 给 API 传入 `SERVER_ADDRESS=0.0.0.0`，让容器内应用接收来自容器接口的连接；宿主发布写成 `127.0.0.1:8080:8080`，只在宿主回环入口开放。裸机仅激活 mysql profile 时，仍继承 `127.0.0.1` 监听。这些安排不是让无认证 API 对外开放的理由；以后服务器入口的 HTTPS 与反向代理可参考 [代理课](./22b-proxy-and-discovery)。

## 4. API 镜像里实际放了什么？

网络关系确认后，再读工程的 Dockerfile：

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B verify

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/task-api.jar /app/task-api.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/task-api.jar"]
```

第一段用 Maven 和 JDK 17 编译、运行测试，验证失败就不继续生成最终镜像。第二段只保留 JRE 和打包的应用，不再带完整 Maven。

`USER 10001:10001` 让应用以非 root 用户运行；`EXPOSE 8080` 只是声明端口，不负责发布到宿主，真正的发布在 Compose 的 ports 中。exec 形式的 `ENTRYPOINT` 让 Java 接收停止信号，但是否排空请求、能等多久，仍要结合应用配置和平台验证。

容器不是完整虚拟机，仍依赖宿主内核和资源。macOS / Windows 上的 Linux 容器通常运行在 Linux 虚拟机里；CPU 架构、资源限制、挂载和配置也都会影响行为，不能说同一镜像在所有机器上完全没有差别。

这里的镜像标签（tag）便于学习，但标签可以指向新内容。正式交付应按已验证镜像的内容摘要（digest）固定版本，记录构建来源，扫描镜像和依赖漏洞，并持续升级。这个构建里的自动测试主要使用 H2，构建成功不等于 MySQL 集成行为已经通过验证。

## 5. 创建一条任务，再重建容器

用 [CRUD 的创建命令](./07-build-a-crud-api) 创建任务，保存响应里的真实 ID，再按 ID 查询一次。接着先只重启应用：

```bash
docker compose restart api
```

等 API 启动完成后再次查询，预期任务仍在，因为数据保存在 MySQL，不在 API 进程里。再把数据库容器也一起重建：

```bash
# 移除本次容器和网络，保留命名卷
docker compose down
# 仍在同一目录，使用同一 Compose 项目和数据卷
docker compose up -d
```

等两项服务就绪，再用相同 ID 查询，预期仍能查到。这次保留数据依靠的是 **volume（数据卷）**：MySQL 把数据写入 `mysql-data`，它的生命周期独立于容器。

因此不要为了“清一下环境”加 `-v`，那会删除卷。也不要换了项目名或目录、创建了另一份空卷，就把它误判成原数据丢失。真实数据似乎不见时，先核对连的是哪个数据库、哪个 Compose 项目和哪个卷。

MySQL profile 会用 `CREATE TABLE IF NOT EXISTS` 初始化表，但它不会安全地升级已有结构。正式扩展需要版本化迁移，不让应用每次启动都以高权限任意改表。已有卷里的账号也不会因环境变量变化自动修改密码，连接认证失败时要先核对，不直接删卷。

## 6. 留下数据之后，还要验证能恢复

数据卷能经受本例的容器重建，不等于备份。它通常和数据库在同一台机器，磁盘损坏、误删或被攻击时仍可能一起丢失。

进一步练习时，用数据库官方工具或备份机制导出测试库，保存到独立受控位置，再恢复到**另一套隔离数据库**，核对任务 ID、数量、时间往返和关键约束。凭据与备份加密按团队规范处理，不覆盖原库来试恢复。

即使按上述步骤重启成功，也只能说明该条件下复用了卷，不能据此声称崩溃一致性、跨版本升级和灾备都已验证。

## 7. 如果没有按预期启动，从停下的位置继续查

前两条版本命令就失败时，先修本机 Docker 与 Compose 环境。MySQL 一直不 healthy，则看 `docker compose logs mysql` 的初始化和账号错误；API 连库失败，则看 `docker compose logs api` 的 profile、URL、服务名和账号，注意输出脱敏。

若容器显示 running，宿主却连不上，再看应用是否真正启动完成、容器监听地址、ports 和宿主端口占用。若请求成功但重启后找不到任务，则回到上一节核对数据库和卷，而不是反复重建。

规模化后的集群部署可以按需查 [Kubernetes 选修](./24-kubernetes-in-practice)。现在先把本机这条路线跑清楚，记录实际做过的检查与仍未验证的部分。

下一篇：[E4 · 日志与第一次排错](./26-reading-logs)。有了运行环境，我们拿一次具体失败请求，去找它对应的日志。

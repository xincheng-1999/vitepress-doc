---
prev:
  text: "E2. 配置、环境与密钥"
  link: "/back-end/frontend-backend-guide/25-config-and-env"
next:
  text: "E4. 日志与第一次排错"
  link: "/back-end/frontend-backend-guide/26-reading-logs"
---

# Docker 与 Compose：把单体和数据库一起跑起来

> 学习目标：分清镜像、容器、网络和数据卷，用配套工程启动 MySQL + task-api，并验证服务重建后数据仍在。

前置：[Linux](./21-linux-server-essentials)、[配置](./25-config-and-env)、[任务 API](./07-build-a-crud-api)。安装 Docker Desktop 或 Docker Engine + Compose v2，先确认 `docker version` 同时能看到客户端与服务端、`docker compose version` 可用。

::: warning 仅本机练习
本章使用的初始 API **尚未实现登录和数据隔离**，只映射宿主回环地址。容器化不会自动把不安全的业务代码变成生产系统。完整上线验收见 [综合实战](./38-capstone-feature-to-prod)。
:::

## 1. 四个概念，分别装什么

| 概念 | 装 / 做什么 | 常见误解 |
| --- | --- | --- |
| 镜像 Image | 应用与用户态依赖的构建产物 | 不是某个正在运行的进程 |
| 容器 Container | 由镜像创建的隔离运行实例 | 不是完整虚拟机，仍依赖宿主内核与资源 |
| 网络 Network | 容器通信与服务名寻址 | 容器 localhost 不是宿主 localhost |
| 卷 Volume | 独立于容器生命周期的数据 | 数据卷不是备份，删卷仍会丢数据 |

Docker 降低环境差异，但内核、CPU 架构、资源限额、挂载和配置仍能影响行为。macOS / Windows 的 Linux 容器通常运行在 Linux 虚拟机中，不能说镜像在所有机器「完全一样」。

![宿主端口只向本机开放，API通过Compose服务名连接MySQL，数据库数据保存在卷中](/backend-guide/core-deploy-topology.svg)

## 2. 使用仓库已有文件

进入仓库根目录下的 `examples/backend-task-api`。工程已提供 Dockerfile、Compose 与 MySQL profile，不需要你猜一套未提供的公司配置。

```bash
cd examples/backend-task-api
export DB_PASSWORD='change-this-local-app-password'
export MYSQL_ROOT_PASSWORD='change-this-local-root-password'
docker compose config --quiet
docker compose up --build -d
docker compose ps
docker compose logs -f api
```

密码是**本机练习占位值**，不要复用真实凭证。`config --quiet` 只校验，不把插值后的密码打印出来。不要随意把 `docker compose config` / inspect 的完整输出贴到公开问题里。

第一次需要联网拉取 Maven、JDK、MySQL 镜像与依赖，可能较慢。日志出现启动完成后按 Ctrl+C 退出日志跟随，不会停止 `-d` 启动的服务。

**预期**：mysql 为 healthy，api 为运行中；`curl -i http://127.0.0.1:8080/api/tasks` 返回 200。API 没有配置 Actuator，因此别照抄不存在的 `/actuator/health` 来判断这一版是否就绪。

Compose 中 `depends_on` 的 `service_healthy` 只帮助控制初始启动顺序，**不保证运行期间数据库永不掉线，也不自动让应用完整自愈**。

## 3. Dockerfile 在做什么

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

- 第一阶段编译与运行测试，失败就不继续生成最终镜像。
- 第二阶段只带 JRE 和应用，不需要完整 Maven。
- 使用非 root 用户降低权限，仍不构成完整安全隔离。
- `EXPOSE` 是声明，不会把端口发布到宿主；端口映射由 Compose 的 ports 决定。
- `ENTRYPOINT` 用 exec 形式，让 Java 收到停止信号；资源限额、优雅退出和超时仍需按环境验证。

这些镜像 tag 便于学习，**tag 可以变化，不是不可变锁定**。真实交付固定已验证的 digest，扫描依赖与镜像漏洞、记录构建来源，并定期升级。不要假设写了版本 tag 就永远复现同一个镜像。

## 4. 把三个地址分清楚

| 发起方 | 目标 | 为什么 |
| --- | --- | --- |
| 宿主浏览器 / curl | `127.0.0.1:8080` | Compose 映射到 API 容器的 8080 |
| API 容器内 | `mysql:3306` | Compose 网络里的服务名与数据库容器端口 |
| 宿主直接访问 MySQL | 当前没有发布地址 | 示例不映射数据库公网 / 宿主端口 |

Compose 通过 `SERVER_ADDRESS=0.0.0.0` 显式让 API 在容器里监听全部接口；裸机激活 MySQL profile 仍默认只监听回环地址。容器放开监听是为了让同网络 / 映射访问进来；宿主映射写为 `127.0.0.1:8080:8080`，所以不会主动监听所有宿主网卡。两层绑定是不同概念。

Compose 里的数据库 URL 使用 `mysql`，不是 `localhost`；容器中 localhost 指向 API 容器自己。服务器对外 TLS 与反向代理是后续部署设计，见 [代理](./22b-proxy-and-discovery)。

## 5. 验证持久化，不要只看「容器在跑」

1. 用 [CRUD 的创建命令](./07-build-a-crud-api) 创建任务，保存真实 ID。
2. `docker compose restart api`，再按 ID 查询，应仍存在。
3. `docker compose down`，再 `docker compose up -d`，等数据库与应用就绪后查询，应仍存在。

```bash
# 关闭并移除本次 Compose 容器 / 网络，但保留命名卷
docker compose down
# 再次启动，使用同一个项目和卷
docker compose up -d
```

不要为了「清一下环境」加 `-v`，它会删除数据卷。不要随便换项目名 / 目录后，以为新建的空卷证明数据丢了。

工程的 `schema.sql` 用 `CREATE TABLE IF NOT EXISTS`，MySQL profile 本地练习开启初始化；它只能建初始表，不会安全地版本化升级结构。生产接入迁移工具，不在每次启动时让应用凭高权限乱改表。

## 6. 数据卷不是备份

卷和数据库在同一台机器，磁盘坏了、误删或被勒索仍会一起丢。备份要保存到独立受控位置，并验证恢复。

练习目标：使用数据库官方工具 / 备份机制导出测试库，在**另一套隔离数据库**恢复，核对任务 ID、数量、时区和关键约束。不要用原库覆盖恢复来「试试」。涉及命令、凭证和备份加密按团队规范执行。

仅重启容器成功，只证明这一条件下卷被复用；不证明崩溃一致性、跨版本升级、备份有效或灾备完成。

## 7. 第一次容器故障怎么查

| 现象 | 先看证据 |
| --- | --- |
| docker 命令找不到服务端 | Docker daemon / Desktop 是否已运行，当前 context 是否正确 |
| mysql 一直不 healthy | `docker compose logs mysql`，看初始化和凭证，不盲目删卷 |
| api 连数据库失败 | `docker compose logs api`，检查 profile、服务名、账号和 URL |
| API 容器 running，但宿主连不上 | 日志是否启动完成、容器监听地址、ports 和端口冲突 |
| 重启后数据似乎没了 | 连接哪个库、哪个 Compose 项目、用了哪个卷，先核实再操作 |

教学 build 中自动测试仍主要用 H2；容器运行 MySQL 后要做实际 HTTP 与持久化验收，不要把测试通过等同于完成了 MySQL 集成测试。

## 自测与参考答案

1. API 容器里访问 `localhost:3306` 为什么不通？**数据库在另一个容器，需用 mysql:3306。**
2. 镜像里有 MySQL 数据吗？**本例数据在命名卷，不在 API 镜像里。**
3. `docker compose down` 与删除卷是同一回事吗？**不是；不要随意使用会删卷的选项。**

下一站：[日志与第一次排错](./26-reading-logs) → [综合验收](./38-capstone-feature-to-prod)。规模化再看 [Kubernetes 选修](./24-kubernetes-in-practice)。

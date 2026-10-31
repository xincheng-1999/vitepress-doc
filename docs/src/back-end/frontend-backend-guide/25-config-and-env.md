---
prev:
  text: "E1. Linux：进程、权限与端口"
  link: "/back-end/frontend-backend-guide/21-linux-server-essentials"
next:
  text: "E3. Docker + MySQL 单体部署"
  link: "/back-end/frontend-backend-guide/23-docker-in-practice"
---

# 25. 配置与环境：同一个 task-api，明确连接到哪里

## 学习目标

- 读懂当前示例真实的 H2 默认配置、MySQL profile 和 Compose 注入关系。
- 区分配置文件、环境变量、命令行与 Compose 插值，验证最终生效值而不打印密钥。
- 让本地练习与生产设计边界分开，不把可跑示例直接当生产配置。

本章对应 Java 17 + Spring Boot 3.x 单体示例 `examples/backend-task-api`。默认入口 `http://localhost:8080/api/tasks`；完整启动见 [CRUD](/back-end/frontend-backend-guide/07-build-a-crud-api)。**当前没有认证，不得公网部署。**

## 25.1 先看真实配置，而不是另造一套端口和变量

示例 `src/main/resources/application.yml` 的默认配置是：

```yaml
server:
  address: 127.0.0.1
  port: 8080
spring:
  datasource:
    url: jdbc:h2:mem:tasks;DB_CLOSE_DELAY=-1
    username: sa
    password: ""
  sql:
    init:
      mode: embedded
logging:
  pattern:
    level: "%5p [requestId=%X{requestId}]"
```

**能直接运行**：在示例目录执行 `mvn spring-boot:run`。它只在本机监听，默认用 H2 内存库；启动初始化表，JVM 退出后数据消失。这里的空密码是受限本地教学设定，不是生产账号建议。

日志 pattern 用于关联 requestId，不代表启用了文件日志、监控端点或全链路追踪。

## 25.2 Profile 叠加差异，不是另一套应用

`application-mysql.yml` 是当前唯一的附加 profile 配置：

```yaml
# 仅供本地 MySQL / Compose 实验
spring:
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3307/tasks?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true}
    username: ${DB_USERNAME:taskapp}
    password: ${DB_PASSWORD}
  sql:
    init:
      mode: always
```

激活 `mysql` 后，这些键覆盖默认文件的同名键，其余如端口 `8080` 和监听 `127.0.0.1` 继续继承。Compose 通过 `SERVER_ADDRESS=0.0.0.0` 单独放开容器内部监听，避免本机仅切换数据库就把无认证 API 暴露到局域网。`mysql` 是 profile 名，不能据此推断它是生产环境；仓库没有已经实现好的 `prod` profile。

`${DB_PASSWORD}` 要求提供值；`${DB_USERNAME:taskapp}` 表示缺失时使用默认值。`DB_URL`、`DB_USERNAME` 这些名字是**被占位符显式引用**，不是随便起名后都会自动映射到 datasource。

URL 默认 `localhost:3307` 只适用于你另外提供了该端口的本地 MySQL。**仓库 Compose 不发布 MySQL 宿主端口**，因此不能用这个默认地址连接 Compose 中的数据库；Compose 会另行注入 `mysql:3306`。

`init.mode=always` 让 MySQL 本地实验执行初始化脚本。`CREATE TABLE IF NOT EXISTS` 不会自动升级已有表；生产设计应改为经过验证的版本化迁移，拆分迁移与业务账号权限，而不是照搬启动初始化。

## 25.3 配置覆盖：看启动者，也看加载位置

只比较本实验常用来源时，可这样理解：

```text
打包的基础配置 < 打包的已激活 profile 配置
配置数据 < 操作系统环境变量 < Java 系统属性 < 应用命令行参数
```

这是简化关系，不是 Spring Boot 完整优先级表。外部配置位置、多个 profile 的顺序、`SPRING_APPLICATION_JSON` 和测试属性等也会影响结果，排查时要先盘点实际来源。替换 `spring.config.location` 与补充 `spring.config.additional-location` 也不是同一动作。

环境变量的常见绑定如 `server.port → SERVER_PORT`、`spring.profiles.active → SPRING_PROFILES_ACTIVE`；复杂嵌套、列表和带横线的键需查绑定规则，不要随意猜名字。修改 YAML 也不会自动使所有运行对象热更新，修改父终端环境更不会倒灌进已经运行的 JVM。

**可运行的覆盖实验**，在示例目录执行：

```bash
SERVER_PORT=8081 mvn spring-boot:run \
  -Dspring-boot.run.arguments=--server.port=8082
```

另开终端请求 `http://localhost:8082/api/tasks`。**预期结果**：本次实例监听 8082，因为本次应用命令行参数覆盖环境变量与文件中的端口。若已有其他实例占用 8082，先辨认目标或换一个空闲端口，不杀陌生进程。

排查时核对激活 profile、实际监听和安全的非敏感键。不要通过 `env` 全量输出、完整 `docker inspect` 截图或未经保护的配置端点来证明密码已注入。

## 25.4 Compose 有两层配置，别把宿主和容器混在一起

当前 Compose 的实际关系如下：

| 项目 | 实际值 / 来源 | 含义 |
| --- | --- | --- |
| API profile | `SPRING_PROFILES_ACTIVE=mysql` | 在容器内启用上述 MySQL 差异配置 |
| API 容器监听 | `SERVER_ADDRESS=0.0.0.0` | 只在 Compose 显式覆盖；裸机 mysql profile 仍继承 127.0.0.1 |
| API 数据库 URL | `jdbc:mysql://mysql:3306/tasks?...` | `mysql` 是 Compose 服务名，不是宿主 localhost |
| API 数据库账号 | `DB_USERNAME=taskapp`，密码来自 `DB_PASSWORD` | 应用不使用 root 账号 |
| MySQL 初始化账号 | `MYSQL_USER=taskapp`，`MYSQL_PASSWORD` 引用宿主 `DB_PASSWORD` | 仅空数据目录初始化时创建，改变量不自动改已有账号密码 |
| 宿主 API 入口 | `127.0.0.1:8080:8080` | 容器内可监听 0.0.0.0，宿主仅发布到回环地址 |
| MySQL 存储与网络 | `mysql-data` 卷；无宿主端口映射 | 数据卷保留则可跨容器重建；数据库仅供容器网络访问 |
| API 启动条件与时区 | 等 MySQL healthcheck；`JAVA_TOOL_OPTIONS=-Duser.timezone=UTC` | 启动顺序检查不代替运行中重连、错误处理和时间测试 |

第一层是 **Compose 对配置文件做变量插值**：`${DB_PASSWORD:?...}` 要求宿主环境或 Compose 环境文件有值，缺失时尽早报错。第二层是 **服务的 `environment` 向容器进程传变量**，之后 Spring 再加载它们。宿主 `.env` 参与插值，不等于它的所有变量都会自动进入每个容器；Spring Boot 也不会默认把任意 `.env` 当配置文件。

下面与示例 README 一致，是可运行的**本地选修实验**。口令是公开教学占位，执行时换成不同的、本地专用随机值；生产凭据不得写入命令历史。

```bash
# 在 examples/backend-task-api 目录；安装 Docker 与 Compose v2
export DB_PASSWORD='replace-with-local-app-password'
export MYSQL_ROOT_PASSWORD='replace-with-different-local-root-password'
docker compose up --build -d
docker compose logs --tail=100 api
curl -i 'http://localhost:8080/api/tasks?page=0&size=20'
# 实验结束，保留数据库卷
docker compose down
```

**预期结果**：MySQL 健康后 API 启动，接口 `200`；应用只通过宿主回环地址访问。再次启动并保留卷，之前创建的任务仍在。不要运行 `down -v`，除非已经确认要销毁练习数据；不要把正式库挂到实验目录。

若第一次建卷使用的密码与这次环境不同，连接失败不一定是“变量没注入”，可能是已有账号密码没有改变。应按数据库账号变更流程确认与修复，不能靠删除卷解决所有认证问题。

## 25.5 生产边界：最小权限，不等于“放到环境就安全”

| 配置类型 | 起步处理 | 上线前补什么 |
| --- | --- | --- |
| 非敏感默认值 | 可审查的配置文件 | 类型/范围校验、变更记录、环境隔离 |
| 数据库密码、签名密钥 | 受控运行环境注入 | 密钥管理、访问审计、轮换、撤销、备份与故障预案 |
| 数据库账号 | 本地 `taskapp`，不借 root 运行 API | 按实际操作授权；迁移 DDL 与运行 DML 分开，禁止跨环境滥用 |
| 功能开关、限流阈值 | 简单配置先满足需求 | 需要动态变化再评估配置中心；明确刷新范围、失败默认与回退 |

示例目录的 `.gitignore` 忽略 `.env`，但忽略规则不会撤销已经提交的内容，也不会保护聊天记录、CI 输出和镜像层。环境变量同样可能被部署权限、诊断工具和日志泄露。Kubernetes Secret 的 Base64 不是加密，仍需 RBAC、静态加密及密钥生命周期管理。

前端 `VITE_` 变量可能进入公开 bundle，不能承载服务端密钥；“放 `.env` 就保密”是错误直觉。误提交后先撤销/轮换，再按流程处理历史与传播范围。

后续增加成组业务配置时可用 `@ConfigurationProperties` 加验证，并注册到 Spring；`@Value` 适合少量取值。类型绑定不是编译时验证所有部署值，非法配置应在启动/加载阶段尽早失败。当前示例没有配置中心，不能承诺改一个开关所有实例立即生效。

## 动手验收与自测答案

先完成 8082 覆盖实验，再选做 Compose。验收记录只写 profile、端口、数据库类型、接口结果与卷是否保留，**不记录密码**。

1. 默认启动和 `mysql` profile 用的是同一份 H2 数据吗？

   **答案**：不是。它们连接不同数据库；profile 叠加不迁移已有数据。
2. 为什么 Compose 里的 API 不能用 `localhost:3307` 连这套 MySQL？

   **答案**：容器 localhost 指自己，仓库也没有把 MySQL 发布到宿主 3307；正确注入为容器网络的 `mysql:3306`。
3. `.env` 写了变量，API 容器就一定能读到吗？

   **答案**：不一定，要看 Compose 如何引用和向服务传入；Spring 的占位符/属性绑定也要对应。
4. 改 Compose 中的 MySQL 密码后，保留旧卷为什么可能启动失败？

   **答案**：镜像初始化变量不自动改已有数据库账号；要区分凭据注入与数据库中的账号事实。

## 相关教程

- [CRUD](/back-end/frontend-backend-guide/07-build-a-crud-api) / [数据库选择](/back-end/frontend-backend-guide/09-sql-vs-nosql)：确认本次使用的真实数据源。
- [Docker](/back-end/frontend-backend-guide/23-docker-in-practice) / [Linux](/back-end/frontend-backend-guide/21-linux-server-essentials)：进程、网络、卷和启动者。
- [安全](/back-end/frontend-backend-guide/32-security) / [日志](/back-end/frontend-backend-guide/26-reading-logs)：保护凭据，保留必要诊断证据。
- [功能地图](/back-end/frontend-backend-guide/39-backend-feature-map)：配置中心、Feature Flag 按需求再引入。

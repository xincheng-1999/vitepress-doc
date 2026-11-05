---
prev:
  text: "E1. Linux：进程、权限与端口"
  link: "/back-end/frontend-backend-guide/21-linux-server-essentials"
next:
  text: "E3. Docker + MySQL 单体部署"
  link: "/back-end/frontend-backend-guide/23-docker-in-practice"
---

# 同一个 task-api，这次到底用了哪份配置？

你把配置里的端口改成 8081，应用却还在 8080；或者切到了 MySQL，之前的任务却不见了。遇到这类问题，先确认这次进程从哪里读取配置，以及最终连向哪个数据库。

下面只沿着仓库的 `examples/backend-task-api` 讲，不另造端口和变量。它使用 Java 17 + Spring Boot 3.x，默认接口是 `http://localhost:8080/api/tasks`，启动方式见 [CRUD](/back-end/frontend-backend-guide/07-build-a-crud-api)。初始版本没有认证，只用于本机练习。

## 1. 什么都不覆盖时，应用使用什么？

示例打包的 `src/main/resources/application.yml` 是基础配置：

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

在示例目录执行 `mvn spring-boot:run`，且没有其他覆盖来源时，应用只监听本机 8080，连接 H2 内存库。启动时初始化表，JVM 退出后数据消失。空密码是受限本地教学配置，不是生产数据库账号建议。

末尾的日志 pattern 把 requestId 放进日志，方便关联请求；它不会顺带启用文件日志、监控端点或分布式追踪。读配置时，先把“确实设置了什么”和“猜测还会有什么”分开。

## 2. 改端口时，先理解谁能覆盖谁

配置不一定只来自 YAML。启动进程的环境变量、Java 系统属性和应用命令行参数也可能提供值。在本实验常见的几种来源中，可以先这样理解：

```text
打包的基础配置 < 打包的已激活 profile 配置
配置数据 < 操作系统环境变量 < Java 系统属性 < 应用命令行参数
```

例如 `server.port` 可以通过环境变量 `SERVER_PORT` 绑定，`spring.profiles.active` 对应 `SPRING_PROFILES_ACTIVE`。这是 Spring 的绑定规则，不是任意变量名都能生效。

可以在示例目录做一次端口覆盖实验：

```bash
SERVER_PORT=8081 mvn spring-boot:run \
  -Dspring-boot.run.arguments=--server.port=8082
```

另开终端请求 `http://localhost:8082/api/tasks`。预期本次实例监听 8082，因为应用命令行参数覆盖了环境变量和文件值。若 8082 已被占用，先确认占用者或换空闲端口，按 [Linux 课](/back-end/frontend-backend-guide/21-linux-server-essentials) 的方式辨认进程。

这里还要认清启动时机：环境变量由启动者传给子进程。修改另一个终端的变量，不会倒灌给已经运行的 JVM；改 YAML 也不等于所有运行实例会自动刷新。观察实际监听、启动记录和安全的非敏感值，才能确认这次修改是否生效。

::: details 还有哪些来源可能影响最终值？
上面的关系不是 Spring Boot 完整优先级表。外部配置位置、多个 profile 的先后顺序、`SPRING_APPLICATION_JSON`、测试属性等也会影响结果。`spring.config.location` 用于替换配置位置，`spring.config.additional-location` 用于补充，两者也不同。

复杂嵌套、列表和带横线的键需要查对应环境变量绑定规则，不随意猜名字。排查时先盘点实际加载来源，不通过全量 `env`、完整 `docker inspect` 或公开配置端点输出密钥。
:::

## 3. 切到 MySQL，是覆盖连接配置，不是迁移数据

示例还有一份 `application-mysql.yml`：

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

激活 `mysql` profile 后，这份文件覆盖基础配置的同名键。数据库连接变了，端口 8080 和监听地址 `127.0.0.1` 仍继承基础值。profile 是一组配置差异，不是另一套应用，也不自动表示生产环境；仓库没有现成的 `prod` profile。

占位符也值得逐个读清：`${DB_USERNAME:taskapp}` 表示从 `DB_USERNAME` 取值，缺少时用 `taskapp`；`${DB_PASSWORD}` 没有默认值，必须提供。这里的变量名是被配置显式引用的，不是给它起一个名字就自然映射到了 datasource。

默认 URL 是 `localhost:3307`，只适用于你另行提供了这个端口的本地 MySQL。**仓库 Compose 并不发布 MySQL 宿主端口**，因此这个默认地址不是连接仓库容器数据库的方法。Compose 会覆盖成 `mysql:3306`，下一节再看它怎样传入。

切换数据库后看不到 H2 中的任务，是因为连接换了，不是 profile 把数据搬坏了。H2 到 MySQL 的数据和结构迁移要单独处理，数据库选择可回看 [对应章节](/back-end/frontend-backend-guide/09-sql-vs-nosql)。

`init.mode=always` 让本地 MySQL 实验执行初始化 SQL。脚本中的 `CREATE TABLE IF NOT EXISTS` 能创建初始表，不能自动升级已有表。生产扩展要采用经过验证的版本化迁移，并把建表、改表所需的迁移权限，与日常查询、写入所需的业务账号权限分开。

## 4. 到了 Compose，值要先经过两次传递

Compose 不是把宿主环境原封不动交给应用。先看一个密码如何到达容器：

```yaml
# Compose 配置中的片段
services:
  api:
    environment:
      DB_PASSWORD: ${DB_PASSWORD:?Set DB_PASSWORD in your shell}
```

第一步，Compose 读取宿主环境或环境文件，把 `${DB_PASSWORD:?...}` 插入配置；缺失时及早报错。第二步，`environment` 把这个值传给 API 容器进程，Spring 再用上一节的占位符读取它。

所以，宿主 `.env` 参与 Compose 插值，不代表其中所有变量都会进入每个容器；Spring Boot 也不会默认读取任意 `.env` 文件。把变量写在某处与最终应用读到它，是两件事。

仓库为 API 容器传入的关键值如下：

```text
SPRING_PROFILES_ACTIVE=mysql
SERVER_ADDRESS=0.0.0.0
DB_URL=jdbc:mysql://mysql:3306/tasks?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true
DB_USERNAME=taskapp
JAVA_TOOL_OPTIONS=-Duser.timezone=UTC
```

`mysql` 在这里是 Compose 服务名，API 容器通过它访问数据库 3306。容器的 localhost 指向自己，不是宿主或数据库容器。`SERVER_ADDRESS=0.0.0.0` 只在 Compose 显式覆盖，让映射进来的请求能到达应用；裸机仅激活 mysql profile，仍只监听回环地址。

宿主发布的是 `127.0.0.1:8080:8080`，所以本机访问 8080 会进入 API 容器，但不会主动监听所有宿主网卡。MySQL 没有宿主端口映射，数据保存在 `mysql-data` 命名卷。API 等 MySQL healthcheck 通过后再启动，这只是初始启动条件，不保证运行中数据库永不失联。

### 密码传进去了，数据库却拒绝登录？

API 用 `DB_PASSWORD` 登录；MySQL 用同一个宿主变量作为初始化的 `MYSQL_PASSWORD`，创建 `taskapp` 账号。应用不使用 root。

这个创建动作只在空数据目录初始化时发生。保留旧卷后改变量，不会自动修改库中已有账号的密码。因此要区分：本次注入的值是什么，以及数据库当前接受的密码是什么。修复应按账号变更流程进行，不为了消除报错就删除数据卷。

如果已有 Docker 与 Compose v2，可以在示例目录验证这套配置。下面是读者实验，口令需换成两个不同的本地专用随机值，不复用真实凭据：

```bash
export DB_PASSWORD='replace-with-local-app-password'
export MYSQL_ROOT_PASSWORD='replace-with-different-local-root-password'
docker compose up --build -d
docker compose logs --tail=100 api
curl -i 'http://localhost:8080/api/tasks?page=0&size=20'
# 结束时保留数据库卷
docker compose down
```

预期是 MySQL 健康后 API 启动，列表可访问；保留同一项目和卷再次启动，之前创建的任务仍在。不要随手加 `down -v`，也不要把正式数据库挂进练习环境。具体启动检查放在下一课；本轮并未实际运行这套容器实验。

## 5. 哪些值可以提交，哪些需要受控保管？

端口、普通阈值等非敏感默认值，可以放在可审查的配置文件里。数据库密码、签名密钥应由受控环境或密钥管理系统提供，并安排访问权限、轮换和撤销。环境变量只是传递方式，不会自动保证保密，部署权限、诊断输出和日志同样可能泄露它们。

示例 `.gitignore` 忽略 `.env`，只能帮助避免新文件被误加，不能撤销已经提交或传播的密码。误提交时先撤销、轮换，再处理历史和传播范围。生产凭据也不应直接写入命令历史、镜像层或 CI 输出。

前端 `VITE_` 变量可能被打进公开 bundle，不能放服务端密钥。Kubernetes Secret 的 Base64 也不是加密，还需要按角色控制访问权限（RBAC）、存储时加密和密钥生命周期管理，相关背景见 [安全课](/back-end/frontend-backend-guide/32-security)。

以后业务配置多了，可以用 `@ConfigurationProperties` 成组绑定、注册并加验证，让非法部署值在加载时尽早失败；少量取值可用 `@Value`。确有动态变更需求时，再按 [功能地图](/back-end/frontend-backend-guide/39-backend-feature-map) 评估配置中心，并说明哪些值能刷新、失败时用什么默认值、怎样回退。当前示例没有这些动态能力。

配置排查的结果不需要包含密码。写清这次进程的 profile、端口、数据库类型、连接位置和接口结果，就足以让别人继续核对；需要日志时按 [日志课](/back-end/frontend-backend-guide/26-reading-logs) 关联请求。

下一篇：[E3 · Docker + MySQL 单体部署](/back-end/frontend-backend-guide/23-docker-in-practice)。我们把这里的配置放回实际启动顺序，看看应用、数据库和数据卷如何配合。

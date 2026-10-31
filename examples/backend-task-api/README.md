# 任务 API：本机教学实验

配套教程：`docs/src/back-end/frontend-backend-guide/07-build-a-crud-api.md`。

## 前提与运行

- JDK 17（`java -version`、`mvn -version` 均需显示 17 或兼容版本），Maven 3.6.3+。
- 在本目录执行 `mvn test`，再 `mvn spring-boot:run`。
- 默认只监听 `127.0.0.1:8080`，使用 H2 内存库，重启丢数据。
- `POST /api/tasks` 请求体 `{"title":"学 SQL"}`；`GET /api/tasks?page=0&size=20`。
- `GET /api/tasks/{id}`；`PUT /api/tasks/{id}` 请求体 `{"title":"学 SQL","status":"DONE"}`。
- `DELETE /api/tasks/{id}` 返回 204，无响应体。
- 错误结构 `{code,message,requestId}`，HTTP 状态保持 400/404/405/415 等语义。

## 可选 MySQL + 容器练习

安装 Docker Engine/Desktop 与 Compose v2，先设置仅供本地练习的密码：

```bash
export DB_PASSWORD='change-this-local-app-password'
export MYSQL_ROOT_PASSWORD='change-this-local-root-password'
docker compose up --build -d
docker compose logs -f api
# 实验结束：保留数据库卷
docker compose down
```

MySQL 不映射宿主端口，API 映射到宿主回环地址。请不要运行 `down -v`，除非明确要销毁练习数据。
`mysql:3306` 是容器网络地址，和宿主 `localhost` 不同。标签不是不可变标识，生产应按验证过的摘要固定镜像。

## 明确边界

这是可运行的 CRUD 教学起点，不是完整任务系统：

- **没有登录和数据隔离**，所有调用者可访问所有任务，不可公网部署。
- 使用参数化 JDBC、严格 JSON 类型、Unicode 空白规范化后复检、有限分页、统一协议错误、请求 ID 和自动测试。
- 测试手工创建不绑定外部 datasource / Hikari 属性的随机 H2 内存库，固定 test profile；schema 初始化和清理前均核验精确 JDBC URL，避免继承环境后误操作外部数据。
- mysql profile 在裸机仍默认回环监听；仅 Compose 显式注入 SERVER_ADDRESS=0.0.0.0 开放容器内部接口，宿主仍只映射回环。
- 没有版本冲突检测；PUT 并发更新可能相互覆盖。分页总数与列表也不保证同一快照。
- 没有请求幂等键、任务持久队列、缓存、附件和后台导出。
- MySQL 练习使用幂等初始化 DDL，不等于版本化迁移；生产接入 Flyway/Liquibase 并测试升级。
- H2 测试不证明 MySQL 方言、执行计划、锁和时区行为完全相同，扩展时需增加 MySQL 集成测试。
- 固定 Spring Boot 3.5.7 便于复现实验；不表示它永远是最新或适合上线。生产前升级维护版本并做依赖漏洞检查。

扩展目标与验收见教程的 SQL、事务、安全、Docker 和综合实战章节。

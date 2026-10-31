# Session 登录与任务归属：仅本机安全实验

这是与 `../backend-task-api` **独立**的 Spring Boot 3.5.7 / Spring Security 6 / Java 17 项目，不修改、不依赖初始示例的运行实例。完整中文跟做步骤见站点教程「32a. Session 登录实验」。

## 怎么运行

需要 JDK 17、Maven 3.6.3+；第一次下载依赖需要联网。没有 Docker、MySQL、JWT 或注册中心依赖。

```bash
# 从仓库根目录进入
cd examples/backend-session-lab
java -version
mvn -version
mvn test
# 以下由读者自行执行；测试本身不监听网络端口
mvn spring-boot:run
```

默认监听 **127.0.0.1:8080**，只用于本机 HTTP。与初始示例不能同时占用 8080；先在初始示例终端 Ctrl+C，或明确为实验选择其它本机端口。不向公网开放，也不通过环境变量改为共享或生产数据库。

## 公开的演示账号

| 用户名 | 公开演示口令 |
| --- | --- |
| alice | `alice-local-demo-17` |
| bob | `bob-local-demo-17` |

这些口令出现在源码和教程中，**不是秘密，不可用于生产**。`PasswordEncoder` 使用 BCrypt 生成用户密码哈希，认证由 Spring Security 执行；哈希不会让公开口令变得安全。

## 真实认证与接口契约

1. `GET /api/auth/csrf` 公开返回 `{token,headerName,parameterName}`。读取 token 会创建 Session，curl 需保存 Cookie jar。
2. `POST /api/auth/login` 发送表单 `username/password`，携带同一 Session 的 CSRF token，成功返回 `200 {"username":"alice"}`；错误凭据返回 JSON 401，无 HTML 重定向。
3. 登录会改变 Session ID 并清除旧 CSRF token，因此必须保存登录响应的 Cookie，再次 GET csrf。
4. 使用 Cookie 访问任务接口；POST / PUT / DELETE / logout 还要携带新 CSRF token。
5. `POST /api/auth/logout` 校验 CSRF 后返回 204，清除 Cookie 并使 Session 失效；旧 Session 不能继续读任务。

| 任务接口 | 行为 |
| --- | --- |
| POST `/api/tasks` | title 字符串，201 + UUID / TODO / Instant，带 Location |
| GET `/api/tasks/{id}` | 自己的任务 200；不存在或别人的均 404 |
| GET `/api/tasks?page=0&size=20` | 只包含当前用户，total 也按用户过滤；page 0..10000，size 1..100 |
| PUT `/api/tasks/{id}` | 完整提供 title + status，自己任务 200，其他 404 |
| DELETE `/api/tasks/{id}` | 自己任务 204 空正文；不存在或别人任务 404 |

删除的 404 规则有意不同于初版重复 DELETE 204：不对不可见资源做全表查询，也不向请求方区分不存在与他人资源。重复删除仍是幂等的状态效果。

owner_id 仅取经过认证的 `Principal.getName()`（本实验以不变演示用户名代指用户 ID），不信任 uid 头、查询参数、JSON ownerId 或 owner_id；生产模型通常使用不可变用户 ID。响应不暴露 owner 字段。错误形状为 `{code,message,requestId}`，MVC 保留 400/404/405/415，安全层提供 JSON 401/403。

## 源码分工

- `SecurityConfig`：两位 BCrypt 演示用户、Session、表单登录、CSRF 与退出策略；没有禁用 CSRF，也没有跨域放开。
- `AuthController`：主动读取延迟 CSRF token，返回 token 与所需头字段名；禁止缓存。
- `SecurityJson`：过滤器阶段的 JSON 401/403；不能依赖 MVC Advice 处理 Security 的失败。
- `RequestLogFilter`：在 Security 之前生成请求 ID，不记录密码、Cookie、CSRF token、查询或正文。
- `TaskController/Service/Repository`：从 principal 传入 owner，所有 SQL 读写及分页总数都包含 owner 条件。
- `JsonConfig`：拒绝数字 / 布尔转标题字符串及数字转枚举；Service 另校验 strip 后不能为空，覆盖 EM SPACE。
- `schema.sql`：H2 教学任务表和归属索引；不是生产版本化迁移。
- `SessionSecurityTest`：通过真实 CSRF 获取和真实表单登录测过滤器、应用与 H2，不使用假身份直接绕过登录。

## 测试隔离

测试显式创建未绑定外部属性的 `DriverManagerDataSource`，使用随机名称的 `jdbc:h2:mem:session-security-tests-...`，禁用自动 SQL 初始化并只执行本项目 schema。即使用户设置了真实 DB 环境变量，也不会将其绑定到这个测试数据源。

初始化与每次 `DELETE FROM tasks` 前都核对 JDBC metadata URL；不是 `jdbc:h2:mem:` 或不是本次随机测试库则失败，拒绝清理。不要移除此保护或把测试“修好”为连接业务库。

测试覆盖登录 / 未登录 / 错误口令、Session ID 变化、登录后 CSRF 更新、缺失 / 伪造 / 跨 Session CSRF、退出与旧 Session 失效、A/B 越权读写删、列表和 total 隔离、伪造 uid / owner 字段、严格 JSON / Unicode 空白、MVC 状态、参数化 SQL。MockMvc 不验证真实 TCP、TLS 或浏览器 Cookie / SameSite 行为，curl 实验用于补足本机 HTTP 的观察。

## 不等于生产脚手架

- 用户仅在内存中定义，无注册、改密、密码找回、MFA、账号封禁、登录限流或完整生产密码策略。
- 数据库和 Session 都在内存中；应用重启后数据与会话失效。没有持久会话、集群共享会话和多设备会话管理。
- 本机 HTTP 的 Cookie 为 HttpOnly、SameSite=Lax，但没有 Secure；生产必须重新设计 HTTPS、Secure Cookie、可信代理和部署安全。
- 没有 POST 幂等键、并发版本检测、审计留存、备份恢复或生产密钥管理。
- H2 行为不证明生产 MySQL 方言、锁、时区和迁移可用；本实验刻意聚焦 Session 安全，不提供 MySQL 部署配置。
- 固定依赖版本用于复现实验，不表示已完成漏洞评估；生产前需持续升级并验证。

Cookie jar 和 csrf JSON 是登录材料，使用仅自己可读的临时目录，不提交 Git、不分享截图。`curl -v` / `-i` 可能输出 Cookie / Set-Cookie 等内容，分享前须脱敏。

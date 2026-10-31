---
prev:
  text: "C2. 事务与并发更新"
  link: "/back-end/frontend-backend-guide/11-transactions-consistency"
next:
  text: "C4. 实验：Session 登录与隔离"
  link: "/back-end/frontend-backend-guide/32a-session-login-lab"
---

# 32. 登录与安全：谁在操作，凭什么允许

## 学习目标

读完本章，你应该能：

- 为一个浏览器应用选择 Session 或 JWT，而不是看到“前后端分离”就默认 JWT。
- 把身份认证、动作授权、数据归属校验放在正确的服务端边界。
- 解释密码哈希、CSRF、XSS、CORS 分别防什么，以及刷新、注销究竟使什么失效。
- 识别 MFA、OAuth2、OIDC、SSO 的用途，知道哪些部分应交给成熟组件。

贯穿场景是 **Java 17 + Spring Boot 3.x 的单体 `task-api`**，本地监听 `8080`，任务入口为 `/api/tasks`。第一课可用 H2，持久化主线使用 MySQL；本章是在 CRUD 跑通后加安全，不要求先拆认证微服务。

::: warning 教学示例不是完整登录系统
下面的 SQL、配置片段用于说明边界。不要复制几段代码就公开上线：应使用 Spring Security 的认证、会话、CSRF 和密码编码组件，结合测试验证。不要自己发明签名算法、验证码协议或密码重置协议。
:::

**想跟做而不是只看概念？** 读完本章后进入 [Session 登录与 A/B 数据隔离实验](./32a-session-login-lab)：提供真实 Spring Security 配置、两位本地演示用户、CSRF 获取、Cookie 登录退出、可信 principal 与 owner 查询，附 16 个自动测试。不要求你先凭空搭好登录系统。

## 32.1 先画清三个检查点

需求：“小林能改自己的任务，不能通过修改 URL 删除小陈的任务。”这至少包含三层检查。

| 检查 | 问的问题 | `task-api` 中的最小实现 |
| --- | --- | --- |
| 认证 Authentication | 请求者是谁？ | Spring Security 校验会话或访问令牌，建立可信的 principal |
| 授权 Authorization | 这个人是否允许执行这个动作？ | 普通用户可改自己的任务；管理员权限单独定义 |
| 数据归属 / 数据范围 | 这个具体任务是否在允许的数据集合内？ | 查询、更新、删除都带当前用户 ID；多租户时还带租户范围 |

![从不可信请求到认证、授权与数据归属的安全检查点](/backend-guide/features-security-boundaries.svg)

浏览器隐藏按钮不是授权；UUID 难猜也不是授权；接口已经登录不代表可以操作任意 ID。未认证一般返回 `401`，身份已确认但动作被禁止一般返回 `403`。资源接口也可以统一返回 `404` 隐藏资源是否存在，但必须在接口契约中保持一致。

**可信身份从哪里来？** 从安全框架已验证的会话或令牌进入 `SecurityContext`。以下内容都不能直接当身份：请求体的 `ownerId`、查询参数 `uid`、客户端自带的 `X-User-Id`、仅被 Base64 解码但没有验证的 JWT。以后即便引入网关，也必须限制业务服务只能由可信入口访问，剥离外部伪造头并认证入口，不能因为名字叫“内部头”就相信。

最小的数据访问边界可以是：

```sql
-- :currentUserId 是服务端已认证 principal 的 ID，不来自请求体
SELECT id, title, status
FROM tasks
WHERE id = :taskId AND owner_id = :currentUserId;

UPDATE tasks SET title = :title
WHERE id = :taskId AND owner_id = :currentUserId;
```

参数由数据库库的绑定机制传入，不是字符串替换。修改语句检查影响行数，为 `0` 时按契约返回不存在或禁止访问。先查归属再按裸 ID 更新会引入检查与使用之间的竞态；能把范围条件写进更新语句，就不要只依赖先前的一次检查。管理员、协作者的范围规则应集中在业务授权逻辑中，不要散落十几处 `if`。

角色授权 RBAC、属性授权 ABAC 和多租户隔离的具体需求，见 [后端功能地图](/back-end/frontend-backend-guide/39-backend-feature-map#collaboration)。

## 32.2 Session 与 JWT：先选适合的会话方式

### 浏览器单体先考虑 Session

登录成功后，服务端保存会话状态，把**不可预测的会话 ID**放进 Cookie。浏览器随后自动携带 Cookie，服务端根据 ID 找到身份。不要把密码或完整用户对象放到 Cookie 中。

```text
浏览器 → POST /api/auth/login → Spring Security 验证凭据
       ← Set-Cookie: 会话 ID
浏览器 → GET /api/tasks + 会话 Cookie → 服务端查会话 → 校验数据范围
```

对同站点的 `task-api`，Session 是足够好的起点：容易主动注销、封禁和调整权限。单机可先用受控的内存会话；进程重启后需要重新登录是明确的边界。多实例时再评估共享会话存储、可用性与成本，不必第一天就上 Redis。

生产 Cookie 应结合部署设置 `HttpOnly`、`Secure`、合适的 `SameSite`、作用域及过期策略。`HttpOnly` 阻止页面 JS 读取 Cookie，不会阻止 XSS 以用户身份发请求。`Secure` 要求 HTTPS；本地 `http://localhost:8080` 调试与生产配置要分开，不能为“本地能跑”关掉生产保护。登录后更新会话标识防会话固定，设置空闲及绝对有效期，密码变更后按策略撤销旧会话。

### JWT 是令牌格式，不是浏览器存储方案

JWT 是承载声明的格式。常见签名 JWT（JWS 紧凑形式）由 `header.payload.signature` 三段组成。前两段是 Base64URL 编码，**可读而非保密**；签名保护完整性，不让攻击者随意修改身份，不负责隐藏内容。JWT 也存在加密形式，但不要因此以为常见 access token 的 payload 已加密。

资源服务应通过成熟库验证：允许的算法、签名与可信密钥、签发方 `iss`、目标受众 `aud`、有效期 `exp`，以及按协议要求检查 `nbf` 等。不能听信 token 自己声明的任意算法或任意密钥地址。合法签名也不等于当前仍有权限：账号可能已被封禁、角色可能已被撤销。

| 比较 | 服务端 Session | 自包含 JWT 访问令牌 |
| --- | --- | --- |
| 身份状态 | 服务端保存，按会话 ID 查询 | 可在本地验签读取声明，也可配在线状态检查 |
| 注销 / 撤权 | 撤销会话后，后续查询即可拒绝 | 仅离线验签时一般仍有效到过期；立即撤销需要额外状态或在线检查 |
| 多实例 | 需要会话共享或适当路由策略 | 多个资源服务可共享可信公钥验证，但密钥轮换、受众隔离仍要管理 |
| 主要成本 | 会话存储与查找、Cookie 相关防护 | 生命周期、泄露处置、刷新、旧权限残留、密钥管理 |
| 起步选择 | 单体浏览器应用通常优先考虑 | 接标准身份平台、多个资源服务、移动端等场景再评估 |

JWT 不等于 `localStorage`。令牌可以由不同客户端以不同方式保存和发送：Web Storage 可被同源 JS 读取，XSS 会增加窃取风险；Cookie 可设置 `HttpOnly`，但浏览器自动携带凭据时要处理 CSRF；内存持有也不抵御正在页面中执行的恶意脚本。没有一种存储位置能代替整体威胁模型。

## 32.3 刷新与注销：明确撤销的是哪一层

先有会话寿命需求，再引入刷新令牌。简单 Session 登录不必为了“标准姿势”再造一套 access/refresh 双令牌。

采用双令牌时，可以把 access token 设置为较短有效期，refresh token 用于受控端点换取新 access token；具体时长取决于风险与体验，不是固定的 15 分钟或 7 天。刷新端必须验证令牌的类型、客户端/会话绑定及有效状态，不能把任意访问令牌当刷新凭据。

安全边界要写在设计中：

1. **刷新轮换**：每次刷新发新 refresh token，使旧值失效。服务端可保存其安全摘要、会话族及使用记录；发现旧值被重放时撤销相关会话并要求重登。并发刷新需要客户端合并请求、服务端有明确的竞态处理规则。
2. **注销**：删除浏览器凭据还不够。Session 方案要使服务端会话失效；双令牌方案要撤销 refresh 会话。**撤销 refresh 不会自动让已经签出的 access token 失效**。
3. **立即阻断**：高风险场景可加令牌撤销表、用户会话版本或在线内省/账号状态检查；它们会重新引入状态查询。不能同时承诺“完全无状态”和“所有令牌即时撤销”。
4. **密码重置 / 设备丢失**：提供撤销全部会话的能力，重置凭据要一次性、短寿命、不可枚举；不要把重置令牌写日志。
5. **客户端重试**：只对约定的令牌过期原因刷新一次，失败后重新登录。不能对所有 `401` 无限刷新；重放写请求还需业务幂等，见 [API 设计](/back-end/frontend-backend-guide/34-api-design)。

## 32.4 密码：哈希、加盐与验证不是手写密码学

需求：“即使数据库副本泄露，也不能立即拿到所有人的原密码。”最小实现是采用成熟的**自适应密码哈希**组件，如经过正确配置的 Argon2id 或 bcrypt，按当前安全建议和机器能力评估计算成本。

- 不明文保存，不使用可逆加密来代替密码哈希，不用裸 MD5/SHA-256 等快速摘要存密码。
- 每份密码使用随机盐。盐不是秘密，通常与算法标识、成本参数、哈希一起编码保存；相同密码也应产生不同结果。盐防预计算与相同密码关联，不会让弱密码变强。
- 用 `PasswordEncoder.matches(raw, encoded)` 验证，不要重新 `encode` 后比较字符串，也不要自己管理“拼盐 + SHA”的协议。
- 前端哈希不能替代 HTTPS 或后端密码哈希；若服务器把前端哈希当凭据接受，这串值本身就是可重放的密码等价物。

下面是在已有 Spring Security 配置中声明编码器的示例，**不是完整注册/登录接口**：

```java
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Bean
PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
}

// 注册：String stored = passwordEncoder.encode(rawPassword);
// 登录：boolean valid = passwordEncoder.matches(rawPassword, stored);
// 算法前缀保存在结果中，便于未来迁移；部署前评估默认算法和成本。
```

除此以外，还需要登录尝试限速、弱密码策略、统一的“账号或密码错误”、审计与异常检测。统一文案不自动消除时序侧信道：不存在用户的分支如果完全跳过昂贵的哈希验证，仍可能泄露差异；使用成熟认证实现并测试，而不是在示例里注释“避免枚举”就算完成。锁号也要防止攻击者恶意锁住别人的账号。

## 32.5 XSS、CSRF、CORS：三件不同的事

| 概念 | 威胁 / 职责 | 在任务系统中做什么 | 不能误以为 |
| --- | --- | --- | --- |
| XSS | 不可信内容在受害者页面上下文执行 | 标题按纯文本渲染；按 HTML、属性、URL 等输出上下文编码；富文本用成熟白名单净化器；配 CSP 等纵深防御 | “后端删了 script 标签”或“Cookie 有 HttpOnly”就不会受害 |
| CSRF | 浏览器自动带上凭据，被诱导执行非预期操作 | Cookie 登录的写接口校验框架 CSRF token，结合 SameSite、Origin 检查；GET 不做修改 | token 只要叫 JWT 就没有 CSRF |
| CORS | 浏览器控制跨源脚本是否能读取响应等 | 仅允许确有需求的来源、方法、头；凭据请求使用明确来源 | CORS 是身份认证、可阻止 curl，或能代替 CSRF |

**CSRF 与令牌的关系**：若访问令牌只从客户端代码放进 `Authorization` 头、没有浏览器自动附带的备用凭据，传统 CSRF 风险较低；但 Cookie 中的 JWT、refresh Cookie 和其他自动携带认证信息的接口仍要独立评估 CSRF。XSS 一旦发生，可调用页面权限内的接口，因此“用 header 所以绝对安全”仍是错的。

**CORS 的边界**：同源部署 `task-api` 的页面与接口时可能不需要配置跨源放行。开发服务器不同端口需要时，仅放行本地开发来源。预检失败不代表请求一定未到服务端，有些简单跨源请求会发送，只是响应不让 JS 读取；后端必须自己认证、授权。

**内容如何存储**：纯文本标题可以按原始文本保存，在每种输出端按其上下文正确编码，避免全局预转义造成双重编码；允许富文本才引入专门净化策略。导出、邮件和管理后台也要使用相应的安全输出规则。

## 32.6 不只登录：接口及运行环境的底线

| 边界 | 最小防护 | 典型失败 |
| --- | --- | --- |
| 输入与业务状态 | Bean Validation 验格式；Service 验业务权限、状态、配额 | 只校验前端表单；允许请求体直接覆盖 owner、role 等字段 |
| 数据库 | 值使用参数绑定；动态排序/列名走白名单 | 以为用了 ORM 就可以随便拼原生 SQL |
| 文件与外部 URL | 限制大小/类型/路径；下载鉴权；服务端抓取 URL 要防 SSRF | 客户端传路径就写磁盘；任意 URL 可访问内网元数据服务 |
| 传输 | 生产 HTTPS；代理终止 TLS 后按信任模型保护后续链路 | 登录前先把密码发到 HTTP，再指望重定向保护已发出的内容；把“内网”视为天然可信 |
| 密钥 | 由受控运行环境/密钥系统注入，最小权限、轮换、审计 | 提交 Git、打进前端包、完整打印到日志 |
| 返回与日志 | 响应 DTO 排除密码哈希、令牌；结构化日志做脱敏与注入防护 | 记录整个登录请求或完整 Authorization 头 |
| 依赖与权限 | 维护依赖版本，应用账号不持有无关管理权限 | 业务数据库账号拥有全部 DDL；应用容器默认高权限 |

Kubernetes Secret 中的 Base64 **只是编码，不是加密**；还要依赖 RBAC、静态存储加密及密钥管理等机制。环境变量也不是保险箱，进程诊断、日志和部署权限都可能暴露它。误提交密钥后先撤销、轮换，再清理历史，而不是只删当前文件。

更多附件和回调的边界见 [功能地图](/back-end/frontend-backend-guide/39-backend-feature-map)，运行配置见 [配置与环境](/back-end/frontend-backend-guide/25-config-and-env)。

## 32.7 MFA、OAuth2、OIDC、SSO：听懂接入方案即可

| 词 | 解决的现实问题 | 关键边界 |
| --- | --- | --- |
| MFA 多因素认证 | 密码泄露后，再要求独立因素证明身份 | 密码加另一个密码不是独立因素；短信有劫持风险，可评估 TOTP、通行密钥等方案；还要安全的恢复流程 |
| OAuth 2.0 | 应用取得访问某个资源的有限授权，不收集用户在第三方的密码 | 它首先是授权框架，不单独定义“用户已登录我是谁”；使用成熟库实现授权码 + PKCE，校验回调与 state 等 |
| OIDC | 在 OAuth 2.0 上增加身份层，标准化登录和身份声明 | 验证 ID Token 的签名、iss、aud、有效期与按流程要求的 nonce；ID Token 不直接当任意业务 API 的 access token |
| SSO 单点登录 | 多个应用复用身份提供方登录会话，减少反复登录 | 不是所有应用共享一份 localStorage；应用会话、IdP 会话、各资源令牌的注销范围要分别设计 |

“用 GitHub 登录”之类的接入要遵循提供方的具体协议：有些基于 OAuth 授权后再调用户 API，有些提供 OIDC。不要拿一个解码成功的字符串或前端传来的 email 就绑定已有账号；账号绑定本身也要验证归属并防接管。初学的单体登录不需要自建身份提供方，需要时优先接成熟平台。

## 32.8 动手练习：证明安全边界真的有效

前提：已完成 [CRUD 入门](/back-end/frontend-backend-guide/07-build-a-crud-api)，并通过 Spring Security 为本地练习接入 Session；创建普通用户 A、B，各自有任务。练习账号仅用于本地，不把真实密码放在命令行历史中。下面的 `a.cookies` 由本地登录流程获取；写操作的 CSRF token 按实际框架配置获取，不关闭 CSRF 来“让测试通过”。

```bash
# 1. 无会话读受保护资源
curl -i http://localhost:8080/api/tasks

# 2. A 访问自己的任务（示例 ID 为 1）
curl -i -b a.cookies http://localhost:8080/api/tasks/1

# 3. A 尝试读取 B 的任务（示例 ID 为 2）
curl -i -b a.cookies http://localhost:8080/api/tasks/2

# 4. 没有会话，只伪造身份头
curl -i -H 'X-User-Id: 2' http://localhost:8080/api/tasks/2
```

**预期结果**：对 JSON API，未认证返回 `401` 而非 HTML 登录页；自己的任务 `200`；别人的任务按本章契约返回 `404`；伪造身份头仍是 `401`。还应测试：A 以合法 CSRF token 修改 B 的任务仍失败，数据库 B 的记录没有变化；缺少 CSRF token 的 Cookie 写请求被拒绝；注销后旧会话不能再访问。

**延伸验证**：对同一密码执行两次 `encode`，结果不同但 `matches` 都为真；把看似 HTML 的任务标题当文本展示，不能执行脚本。若采用 JWT，补测错误受众、过期、错误签名，以及注销后旧 access token 在你选定策略下的真实表现，不宣称未实现的即时失效。

## 自测与答案

1. A 已登录，改 URL 就能读到 B 的任务，是认证失败吗？

   **答案**：通常是授权/数据归属校验缺失，典型 IDOR；在读写查询中落实可信身份的数据范围，并测试所有入口。
2. 使用 JWT 并放到 HttpOnly Cookie，是否就不需要 CSRF 防护？

   **答案**：不是。Cookie 会被浏览器自动携带，是否存在 CSRF 取决于凭据发送方式与请求防护，不取决于令牌格式。
3. 删除 refresh token 后，旧 access token 为什么还能调用接口？

   **答案**：仅离线验签的资源服务不知道 refresh 被撤销；要接受短期残余窗口，或增加撤销/版本/在线状态检查。
4. 数据库里每个用户的盐都公开，是密码泄露吗？

   **答案**：盐本就不必保密，密码仍受专用哈希和成本参数保护，但弱密码仍可被离线猜测，应评估算法参数和额外防护。
5. OAuth2、OIDC、SSO 是三个同义词吗？

   **答案**：不是。分别强调资源授权框架、身份协议层、跨应用复用登录的体验/架构目标。

## 相关教程

- [CRUD 与参数校验](/back-end/frontend-backend-guide/07-build-a-crud-api)：先有受保护的资源。
- [SQL 与索引](/back-end/frontend-backend-guide/10-sql-and-indexes)：参数绑定、归属查询与索引。
- [后端网络](/back-end/frontend-backend-guide/22-networking-for-backend)：Cookie、TLS、跨源与代理。
- [消息任务可靠性](/back-end/frontend-backend-guide/33-mq-reliability)：刷新重试之外，业务副作用怎样防重复。
- [测试](/back-end/frontend-backend-guide/35-testing)：把越权、注销、CSRF 做成回归测试。
- [后端功能地图](/back-end/frontend-backend-guide/39-backend-feature-map) / [场景词典](/back-end/frontend-backend-guide/90-glossary)：需要时再展开权限、租户、Webhook 等功能。

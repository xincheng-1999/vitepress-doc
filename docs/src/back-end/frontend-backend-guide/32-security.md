---
prev:
  text: "C2. 事务与并发更新"
  link: "/back-end/frontend-backend-guide/11-transactions-consistency"
next:
  text: "C4. 实验：Session 登录与隔离"
  link: "/back-end/frontend-backend-guide/32a-session-login-lab"
---

# 登录与安全：怎样只让用户操作自己的任务

Alice 和 Bob 都在用任务系统。Alice 改了一下地址里的任务 ID，竟然看到了 Bob 的任务。页面有没有隐藏按钮、ID 是不是难猜，都没挡住这次访问。

要修好这个问题，后台得先确认“谁在请求”，再判断“这个人能对这条任务做什么”。我们沿着浏览器登录、带着会话访问任务、最后退出的过程，把这两步放到正确的位置。

初版 Java 17 + Spring Boot 3.x `task-api` 监听 `8080`，入口是 `/api/tasks`，**还没有登录和用户隔离**。下一篇 [Session 登录与 A/B 数据隔离实验](./32a-session-login-lab) 是独立的本机 H2 变体，提供 Spring Security 配置、两名演示用户和 16 个安全测试；不会让你在初版上凭空补齐一套认证系统，也不把初版 7 个测试当作安全测试。

## 1. 先确认身份，再把身份带到查询里

服务器收到请求时，不能采用客户端自报的 `ownerId`、查询参数 `uid` 或 `X-User-Id` 作为身份。客户端能改地址，也能改这些值。可信身份应由安全框架验证登录凭据或令牌后建立，放入 `SecurityContext`，应用再取得当前用户，也就是 principal。

有了身份，还要区分两种检查。普通用户是否能执行“删除任务”，属于动作授权；这个 ID 指向的任务是否属于他，则是具体的数据范围检查。只在接口上写“必须登录”，仍会留下 Alice 访问 Bob 资源的问题。

![从不可信请求到认证、授权与数据归属的安全检查点](/backend-guide/features-security-boundaries.svg)

对于个人任务，最直接的做法是让 SQL 同时限定任务 ID 和当前用户：

```sql
-- :currentUserId 是服务端已认证 principal 的 ID，不来自请求体
SELECT id, title, status
FROM tasks
WHERE id = :taskId AND owner_id = :currentUserId;

UPDATE tasks SET title = :title
WHERE id = :taskId AND owner_id = :currentUserId;
```

参数使用数据库访问库的绑定机制，不是字符串替换。更新影响 0 行时，按接口约定返回不存在或禁止访问；可以统一返回 404，避免透露任务究竟存在还是属于别人。别只在前面查一次归属，后面却按裸 ID 更新，把检查和实际写入分开。

列表、分页总数、删除和导出同样要带范围条件。将来增加协作者或管理员时，把规则集中到业务授权逻辑，而不是在每个入口零散补一个 if。团队角色与租户范围见 [后端功能地图](/back-end/frontend-backend-guide/39-backend-feature-map#collaboration)。

常见响应也由此更容易理解：没有通过认证通常是 401；知道是谁但动作被禁止通常是 403；资源接口也可以按一致契约用 404 隐藏是否存在。UUID、菜单和前端路由守卫都不能替服务器完成这些判断。

## 2. 浏览器登录后，用 Session 记住已经验证过的身份

每次查询都重新输入密码不方便。Session 方案把已登录身份保存在服务端，再给浏览器一个不可预测的会话 ID。浏览器把它存进 Cookie，以后自动随相应请求带上，服务器用这个 ID 找到会话。

```text
浏览器 → POST /api/auth/login → Spring Security 验证凭据
       ← Set-Cookie: 会话 ID
浏览器 → GET /api/tasks + 会话 Cookie → 服务端查会话 → 校验数据范围
```

Cookie 里不需要保存密码或完整用户对象。对同站点的单体浏览器应用，Session 是简单可用的起点：注销、封禁或调整权限时，可以让服务端状态及时变化。单机先使用内存会话，重启后重新登录；确有多实例需求时，再评估共享会话或合适的路由策略，不必一开始就用 Redis。

登录成功后，还要更新会话标识，避免攻击者让用户沿用事先知道的 ID。下一篇实验会实际看到 Cookie 更新，并重新获取 CSRF token。会话应有空闲与绝对有效期，密码变更时也要按策略撤销旧会话。

Cookie 的保护与部署有关，常用选项可以放在一起查：

| 选项 | 帮助解决什么 |
| --- | --- |
| `HttpOnly` | 页面 JavaScript 不能直接读取 Cookie |
| `Secure` | 只通过 HTTPS 发送 |
| `SameSite` | 限制部分跨站请求携带 Cookie 的方式 |
| Domain、Path 与过期设置 | 控制发送范围和存活时间 |

本地 `http://localhost:8080` 与生产 HTTPS 分开配置，不为了本地调试关闭生产保护。HttpOnly 只是减少 Cookie 被直接读走的机会；如果恶意脚本已经在页面里运行，它仍可能以用户身份发请求。这个问题后面单独解释。

## 3. 登录时，数据库保存的也不应该是原密码

假如用户表副本泄露，明文密码会立即落到攻击者手上。应用应使用成熟的自适应密码哈希，例如正确配置的 Argon2id 或 bcrypt。验证时由组件检查输入密码是否匹配保存结果，而不是把密码解密回来。

每份密码使用随机盐，相同密码也会得到不同结果。盐通常与算法、成本参数、哈希一起保存，它不需要保密，作用是增加预计算和批量关联的难度。弱密码仍可能被猜出，所以计算成本、口令策略和登录限速也要一起考虑。

下面是在已有 Spring Security 配置中声明编码器的示例，不是完整注册或登录接口：

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

不能重新 encode 后比较两个字符串，因为随机盐会让结果不同。也不用可逆加密、裸 MD5 或 SHA-256 保存密码，更不自己设计“拼盐后摘要”的协议。前端先做一次哈希也不能替代 HTTPS 和服务端哈希；如果服务器直接接受那串值，它就成了可重放的密码等价物。

登录失败一般统一为“账号或密码错误”。还要注意不存在用户的分支如果完全跳过昂贵验证，可能通过耗时泄露差异；成熟认证实现、限速、审计和测试比自己拼流程更可靠。锁号策略则要防止攻击者故意锁住别人的账号。下一篇的公开演示密码仅供本地使用，不具备生产账号管理能力。

## 4. Cookie 会自动发送，写请求还需要防止被诱导执行

Session 解决了身份记忆，却带来一个浏览器特点：浏览器会自动带上匹配的 Cookie。如果用户访问了恶意站点，对方诱导浏览器向任务系统发起变更请求，服务器只看会话 Cookie，可能把它当成用户本人发起的操作。

这类攻击叫 **CSRF**。Cookie 登录的写接口应使用框架的 CSRF 防护：页面先取得与当前会话关联的防伪 token，创建、更新、删除和退出时，把它按框架要求带上。服务器检查 token，而不是只看 Cookie。登录动作本身也可能需要这个保护，GET 则不应修改业务状态。

SameSite 和合适的 Origin 检查可以配合使用，但不能为了 curl 调用方便就关闭 CSRF。失败时还要看过滤器顺序：没有登录的写请求，也可能先因缺 token 返回 403，而不是先得到 401。完整的取 token、登录、保存新 Cookie、重新取 token 流程，下一篇会逐条执行。

另一个常见名词 **CORS**，管的是浏览器是否允许跨源脚本读取响应等行为。页面与接口同源时可能根本不需要跨源放行；开发端口不同时，按实际需求放行明确的本地来源、方法和头，凭据请求不能随意允许任意来源。

CORS 不负责身份，也挡不住 curl。一些简单跨源请求已经到达服务器，只是响应不让页面脚本读取，因此业务仍须自己认证和授权，不能把“页面报跨域”当作服务端未执行的证明。

## 5. 如果任务标题被当成代码执行，又怎么办

用户输入一段看似 HTML 的标题，另一个人打开任务页时却执行了其中的脚本，这叫 **XSS**。它利用的是不可信内容进入页面执行环境，与前面诱导浏览器自动带 Cookie 的问题不同。

任务标题只需要纯文本，就按文本保存、按文本渲染。不同输出位置需要相应编码：HTML 正文、属性和 URL 不是同一种上下文。允许富文本时再使用成熟白名单净化器，配合 CSP 等额外防线，不是简单删掉 script 标签。

不要为了显示安全在数据库里全局预转义，否则不同输出端可能出现双重编码。导出、邮件和管理后台也要按各自方式安全输出。HttpOnly 无法阻止页面里正在运行的恶意脚本调用用户接口，所以保护 Cookie 不能代替防 XSS。

可以用一句话区分这三个词：XSS 防不可信内容在页面执行；CSRF 防自动携带凭据的浏览器被诱导变更；CORS 管跨源页面脚本的访问规则。它们各自负责不同位置，需要分别设计和验证。

## 6. 退出时，让服务端会话真正失效

只删浏览器 Cookie，相当于本机丢掉了会话凭据；原来的服务端会话可能还活着，其他持有凭据的人仍能使用。可靠退出应由服务端撤销 Session，并清除客户端 Cookie。

权限被撤销、密码被重置或设备丢失时，也要定义撤销哪些会话，以及多久生效。用户任务保存在数据库里，退出只结束登录状态，不应顺手删除任务。

下一篇会用 Alice 和 Bob 两份 Cookie 验证：Bob 既有合法会话也有合法 CSRF token，仍然无法改 Alice 的任务；Alice 退出后旧会话访问被拒绝，Bob 的会话继续有效。这比只测试“成功登录了”更接近我们开头要解决的问题。

## 按需补充

### 如果接入方案要求 JWT

JWT 是承载声明的格式，不是浏览器存储位置。常见签名 JWT 由 `header.payload.signature` 三段组成，前两段是 Base64URL 编码，可以读取；签名用于检测篡改，并不隐藏内容。JWT 有加密形式，但不能据此认为普通 access token 已被加密。

资源服务通过成熟库检查允许的算法、可信签名密钥、签发方 `iss`、目标受众 `aud`、有效期 `exp`，以及协议要求的 `nbf` 等。不能只解码就信任身份，也不能跟随 token 随意指定的算法或密钥地址。账号封禁、角色撤销后，旧令牌还可能携带旧声明，需要配套生命周期策略。

| 问题 | 服务端 Session | 自包含 JWT 访问令牌 |
| --- | --- | --- |
| 身份从哪里取得 | 按会话 ID 查询服务端状态 | 可以本地验签读取声明，也可配在线状态检查 |
| 怎样撤销 | 撤销会话后拒绝后续访问 | 仅离线验签时通常仍可用到过期；立即撤销需额外状态 |
| 多实例如何配合 | 共享会话或合适路由 | 共享可信公钥，同时管理轮换和受众隔离 |
| 起步场景 | 单体浏览器应用通常先考虑 | 标准身份平台、多资源服务、移动端等再评估 |

JWT 不要求存 localStorage。Web Storage 可被同源脚本读取，存在 XSS 窃取风险；HttpOnly Cookie 能减少直接读取，却要处理自动携带凭据引起的 CSRF；内存保存也挡不住已进入页面的脚本。访问令牌只由代码放到 Authorization 头、没有自动附带的备用凭据时，传统 CSRF 风险较低，但 refresh Cookie 等入口仍需单独防护。

采用双令牌方案时，短期 access token 用于访问，refresh token 用于受控端点换取新令牌。先确定寿命需求，再引入它们，简单 Session 不必另外造一套。刷新端验证令牌类型、客户端和会话绑定、有效状态，不能随意接收访问令牌当刷新凭据。

刷新通常要轮换旧 refresh token，保存安全摘要、会话族和使用记录；发现旧值重放时撤销相关会话。并发刷新要有明确规则，客户端也应合并请求。注销时撤销 refresh 会话，但已经签出的 access token，在仅离线验签的服务中仍可能活到过期。需要立即阻断时，增加撤销表、用户会话版本或在线状态检查，相应承担查询成本。

密码重置凭据应一次性、短寿命、不可枚举，并提供撤销全部会话的能力，不记录重置令牌。客户端只按约定的过期原因刷新一次，失败就重新登录；无限刷新所有 401 既掩盖问题，也可能重放写请求，业务重试还要看 [API 设计](/back-end/frontend-backend-guide/34-api-design)。

### 任务之外的输入、文件和密钥

登录之后，输入格式、业务状态与配额仍须验证，请求 DTO 不应直接覆盖实体的 owner、role 等内部字段。数据库的值使用参数绑定，动态列名和排序走白名单；使用 ORM 也不能随意拼原生 SQL。

文件要限制大小、类型和路径，下载要鉴权。服务端接收外部 URL 时要防 SSRF，避免被用来访问内网或云元数据地址。具体附件和回调设计见 [功能地图](/back-end/frontend-backend-guide/39-backend-feature-map)。

生产从第一次提交密码开始就使用 HTTPS，而不是先发到 HTTP 再靠重定向补救；代理终止 TLS 后，后续链路按实际信任模型保护。密钥由受控运行环境注入，最小权限、定期轮换，不放进 Git、前端包或完整日志。应用账号不持有无关数据库管理权限，依赖版本也要维护。

Kubernetes Secret 的 Base64 只是编码，还需 RBAC、存储加密与密钥管理；环境变量也可能被诊断或日志暴露。误提交密钥后先撤销和轮换，再处理历史，不能只删当前文件。返回 DTO 排除密码哈希和令牌，日志脱敏并防止注入，不记录整份登录请求或 Authorization。运行配置见 [配置与环境](/back-end/frontend-backend-guide/25-config-and-env)。

### 公司要求 MFA、OAuth2、OIDC 或 SSO 时

MFA 是在密码之外增加独立因素，例如通行密钥或 TOTP；另一个密码不算独立因素，短信有劫持风险，恢复流程也必须安全。

OAuth 2.0 让应用取得某些资源的有限访问授权，不需要收集用户在第三方的密码。OIDC 在它之上增加标准化身份层，用于登录。接入时用成熟库，按协议处理授权码、PKCE、回调地址、state，以及 ID Token 的签名、iss、aud、有效期和按流程要求的 nonce。ID Token 不直接当作任意业务 API 的 access token。

SSO 则是多个应用复用身份提供方的登录会话，减少反复登录。应用会话、身份平台会话和资源令牌仍有各自的注销范围，不是所有应用共用一份 localStorage。“用 GitHub 登录”等功能要遵循提供方具体协议，不能拿前端传来的 email 或一个解码字符串直接绑定已有账号；绑定也要验证归属。单体入门不用自建身份平台。

### 已有会话时，可以怎样检查资源权限

::: details 对照检查：下面是假定已经登录后的请求，不是初版 CRUD 的运行脚本
完整可复制的登录流程在下一篇。下面只展示检查方向：`a.cookies` 来自实际登录，任务 ID 必须换成真实 UUID；示例中的 1、2 分别代指 A 和 B 的任务。练习账号仅用于本地，不把真实口令留在命令历史中。

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

按本篇资源契约，应依次得到 JSON 401、200、404、401。还要带合法 CSRF token 尝试越权修改并确认 B 数据不变，漏 token 的变更应被拒绝，退出后旧会话不能使用。密码测试应看到同一口令两次编码不同、matches 都成功；标题展示测试应看到文本而非执行脚本。

若使用 JWT，再测错误受众、过期、错误签名，以及注销后旧 access token 在选定策略下的真实表现。不要把计划中的即时撤销写成已经实现。
:::

需要回查时，可看 [CRUD 与参数校验](/back-end/frontend-backend-guide/07-build-a-crud-api)、[SQL 与索引](/back-end/frontend-backend-guide/10-sql-and-indexes)、[后端网络](/back-end/frontend-backend-guide/22-networking-for-backend)、[消息任务可靠性](/back-end/frontend-backend-guide/33-mq-reliability)、[测试](/back-end/frontend-backend-guide/35-testing) 和 [场景词典](/back-end/frontend-backend-guide/90-glossary)。

主线下一篇：[C4 · 实验：Session 登录与隔离](./32a-session-login-lab)。这次从真正取得 Cookie 开始，用两个用户把上面的过程走一遍。

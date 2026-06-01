# 安全

> 上一章我们把性能和高并发的家底盘了一遍。这一章聊一个永远不能省、出事就上头条的话题：安全。作为前端你已经接触过 XSS、CSRF、CORS、HTTPS 这些词——那一面墙是"浏览器侧"的防御。这一章我们站到服务端这一侧，把同一批威胁重新看一遍，并且记住一条贯穿全章的铁律：**前端校验只为体验，后端必须再校验一遍**。

如果你对 XSS、CSRF、CSP 这些概念还没建立感性认识，强烈建议先回顾前端视角的 [前端 Web 安全](/front-end/the-basics/network-basics/webSafety)。本章不再重复攻击的浏览器细节，重点放在"后端能做、且必须做"的对策上。

---

## 32.1 先分清两个词：认证 vs 授权

后端安全聊得最多的两个词长得很像，但解决的是完全不同的问题：

- **认证（Authentication）**：你是谁？——核验身份。登录就是认证：用账号密码、验证码、token 证明"我是 uid=10001 这个人"。
- **授权（Authorization）**：你能干啥？——核验权限。你证明了身份，但能不能删别人的画布、能不能调管理员接口，是授权要管的事。

**前端类比**：路由守卫里你可能写过两段逻辑——`if (!isLogin) redirect('/login')` 是认证；`if (!user.isAdmin) redirect('/403')` 是授权。前端那一层只是"不给你看入口"，真正拦住你的必须是后端。

```text
                  认证 Authentication              授权 Authorization
                  "你是谁"                          "你能干啥"
  匿名请求  ──►  [校验 token/密码]  ──► 已知身份 ──►  [校验角色/资源归属]  ──► 放行
                       │ 失败                              │ 失败
                       ▼                                   ▼
                  401 Unauthorized                    403 Forbidden
```

记住这个对应关系：身份没通过返回 **401**，身份通过了但权限不够返回 **403**。本项目里 svc-gateway 负责粗粒度认证（token 真不真），各业务服务负责细粒度授权（这条数据是不是你的）。

> ⚠️ 最常见的错误是"只做了认证，忘了授权"。登录态校验通过 ≠ 你能操作任意资源。下文的 IDOR 越权就是这个坑的典型。

---

## 32.2 JWT：无状态的身份令牌

本项目 svc-auth 登录成功后，发给前端的就是一个 JWT（JSON Web Token）。理解它的结构和边界，是看懂整条登录链路的前提。

### 结构：三段，用点隔开

一个 JWT 长这样（为了看清，按点换了行）：

```text
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9      ← header（算法、类型）
.
eyJ1aWQiOjEwMDAxLCJyb2xlIjoidXNlciIsImV4cCI6MTcxNzIzMDQwMH0   ← payload（声明/claims）
.
S0meBase64UrlSignatureXXXXXXXXXXXXXXXXXXX  ← signature（签名）
```

三段都是 Base64Url 编码（注意：**只是编码，不是加密**），用 `.` 连接：

1. **header**：声明签名算法，如 `{"alg":"HS256","typ":"JWT"}`。
2. **payload**：放业务声明（claims），如 `{"uid":10001,"role":"user","exp":1717230400}`。`exp` 是过期时间戳。
3. **signature**：用服务端密钥对 `header.payload` 做 HMAC 签名。这是 JWT 防伪造的核心。

你可以亲手解开 payload 看看——任何人都能解，这正是重点：

```bash
# 把中间那段 payload 用 base64 解码（jwt 用的是 URL-safe base64）
echo 'eyJ1aWQiOjEwMDAxLCJyb2xlIjoidXNlciIsImV4cCI6MTcxNzIzMDQwMH0' | base64 -d
```

预期输出：

```text
{"uid":10001,"role":"user","exp":1717230400}
```

**怎么读这段输出**：不需要任何密钥就还原出了明文 payload。**结论**：payload 是公开可读的，**绝对不能放密码、手机号明文、支付密钥**这类敏感信息。它能放的只是"无所谓被看见"的标识，如 uid、role。

**前端类比**：JWT 很像你在前端拿到后存进 `localStorage` 的那串 token。你可能用过 `jwt-decode` 在前端解出里面的 uid 来渲染——那恰恰证明了 payload 是明文。前端解出来用于显示没问题，但**前端解出来的 role 绝不能作为权限依据**，权限必须由后端校验签名后认定。

### 无状态：服务端凭什么信它

传统 session 方案，服务端要存一份"谁登录了"的表（或放 Redis），每次请求查表。JWT 反过来——服务端**不存**任何东西，只做一件事：用密钥重新算一遍签名，和 token 带来的签名比对。

```java
// svc-auth / cpt-common 里的校验逻辑（简化）
public Claims verify(String token) {
    return Jwts.parserBuilder()
            .setSigningKey(secretKey)          // 密钥来自配置，不在代码里
            .build()
            .parseClaimsJws(token)             // 验签 + 校验 exp，失败抛异常
            .getBody();
}
```

只要签名对得上，说明这个 token 确实是 svc-auth 用同一把密钥签发的、且内容没被篡改过。攻击者就算把 payload 改成 `"role":"admin"`，因为它没有密钥，算不出匹配的签名，验签必然失败。

**前端类比**：像 axios 拦截器里给每个请求带上 `Authorization: Bearer <token>`，后端拿到后"验章"。无状态的好处是 svc-* 任何一个实例都能独立验签，不用共享 session，水平扩容很省心（呼应 [Kubernetes 实战](/back-end/frontend-backend-guide/24-kubernetes-in-practice) 里的多副本）。

> ⚠️ HS256 用的是对称密钥（签发和验签同一把），所以这把密钥泄露 = 任何人都能伪造任意身份的 token，等于整个认证体系沦陷。密钥管理见 32.7。

### 过期与刷新：access token + refresh token

JWT 一旦签发，服务端无法主动作废（因为不存状态）。所以不能签一个永久 token。本项目的做法是双 token：

- **access token**：有效期短（如 15 分钟），每次业务请求都带它。
- **refresh token**：有效期长（如 7 天），只在 access 过期时用来换新的 access，且存在 Redis 里可被吊销。

```text
登录  ──► svc-auth 发 access(15min) + refresh(7d)
                                  │
业务请求带 access ──► 校验通过 ──► 正常返回
                                  │ access 过期(401, 特定错误码)
前端用 refresh 调 /auth/refresh ──► svc-auth 查 Redis 里 refresh 是否有效
                                  │ 有效 ──► 发新 access
                                  │ 无效/已吊销 ──► 强制重新登录
```

**前端类比**：你大概率写过 axios 响应拦截器——遇到 401（access 过期）时，自动拿 refresh 静默换一个新 access、重放原请求，用户无感。退出登录时，后端把 Redis 里的 refresh 删掉，这个会话就真正失效了（这就是无状态 JWT 找回"可吊销"能力的办法）。

### OAuth2 一句话直觉

"用微信/GitHub 登录"背后是 OAuth2 的**授权码流程**。直觉是：你不把微信密码给我们的应用，而是去微信那边登录、同意授权，微信给我们一个一次性的 **code**，我们后端拿 code 去微信换 token，再用 token 去拿你的昵称头像。核心思想是**密码不离开可信方，第三方只拿到有限授权的令牌**。本项目自建认证为主，OAuth2 用于接入第三方登录时才需要。

---

## 32.3 密码存储铁律：绝不明文，绝不可逆

这是后端最不能犯的错误，没有之一。数据库被拖库是常态，区别只在于"拖走后攻击者能不能登录你的用户"。

铁律分三条：

1. **绝不明文**。明文存密码，拖库即全军覆没。
2. **绝不用可逆加密**。能解密就等于明文（密钥也会泄露）。要用**单向哈希**。
3. **不能用裸 MD5/SHA**。它们太快了，攻击者用彩虹表/GPU 每秒能试上亿次。要用**专为密码设计的慢哈希**：bcrypt 或 Argon2，并且**加盐**。

"加盐"就是给每个用户的密码拼一段随机串再哈希，这样两个人即使用同样的密码，哈希结果也不同，彩虹表直接失效。bcrypt 会自动把盐和计算成本一起编进结果里。

```java
// cpt-common 里基于 Spring Security 的密码编码器
@Bean
public PasswordEncoder passwordEncoder() {
    // 参数 10 是 cost（计算强度），越大越慢越安全，生产常用 10~12
    return new BCryptPasswordEncoder(10);
}
```

注册和登录这样用：

```java
@Service
public class AuthService {
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;

    // 注册：只存哈希，永远不存明文
    public RtData<Void> register(String username, String rawPassword) {
        String hash = passwordEncoder.encode(rawPassword);
        userRepository.save(new User(username, hash));
        return RtData.ok(null);
    }

    // 登录：用 matches 比对，不要自己再 encode 一次去 equals
    public RtData<String> login(String username, String rawPassword) {
        User user = userRepository.findByUsername(username);
        // 用户不存在也走一次 matches，避免靠响应时间猜出用户名是否存在
        if (user == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            return RtData.fail("用户名或密码错误");   // 注意：不区分是哪个错
        }
        return RtData.ok(jwtService.issue(user));
    }
}
```

把一个 bcrypt 哈希打出来感受一下：

```text
$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
└┬┘└┬┘└──────────┬──────────┘└──────────────┬──────────────┘
 │  │            盐(salt)                  哈希值
 │  cost=10
 算法标识(2a=bcrypt)
```

**怎么读**：盐和 cost 都明文写在结果里，所以验证时 bcrypt 能用同样的盐和 cost 重算。**结论**：你只需要存这一整串，不用自己额外管理盐。

**前端类比**：你可能在前端对密码做过一次哈希再传——那只能防"传输过程被窥看"（而且有 HTTPS 后基本没必要），**绝不能替代后端的存储哈希**。前端哈希后的值对后端来说就是新的"明文密码"，照样要再 bcrypt 一遍。

> ⚠️ 登录失败时统一返回"用户名或密码错误"，不要分别提示"用户不存在""密码错误"——后者会被用来枚举有效用户名。

---

## 32.4 常见漏洞与后端对策（逐个击破）

下面这些是后端必须能随口说出对策的高频漏洞。每个都给"危险写法 → 正确写法"。

### SQL 注入：绝不拼接 SQL

危险写法——把用户输入直接拼进 SQL 字符串：

```java
// ❌ 致命写法：username 传 ' OR '1'='1 就能绕过，传 '; DROP TABLE 就能删库
String sql = "SELECT * FROM user WHERE username = '" + username + "'";
jdbcTemplate.queryForList(sql);
```

正确写法——**参数化查询**，让数据库把 `?` 的内容当纯数据，而非 SQL 代码：

```java
// ✅ 参数化：无论 username 里有什么，都只是个值，不会被当成 SQL 执行
jdbcTemplate.queryForList(
    "SELECT * FROM user WHERE username = ?", username);
```

用 MyBatis 时同理：`#{}` 是参数化（安全），`${}` 是字符串拼接（危险，几乎只在动态表名/排序字段时才用，且要白名单校验）。

```sql
-- MyBatis: 用 #{} 不要用 ${}
SELECT * FROM ai_task WHERE uid = #{uid} AND status = #{status}
```

**前端类比**：相当于把"参数"和"指令"分开传，类似你用 axios 传 `params: { keyword }` 而不是自己手拼 query string 里的特殊字符。**对策总结**：永远用 ORM / 参数化查询，永远不要把用户输入拼进 SQL。本项目主库是 MongoDB，同样不要把用户输入拼进查询条件对象，用驱动提供的参数化方式。SQL 细节见 [SQL 与索引](/back-end/frontend-backend-guide/10-sql-and-indexes)。

### XSS：后端也要防存储型

前端那一章讲过反射型/DOM 型 XSS，主要靠浏览器侧的 `innerText`、CSP、框架默认转义来防。但有一种 **存储型 XSS** 后端责任最大：用户把 `<script>` 塞进画布标题或 prompt，存进数据库，下次别人浏览时被渲染执行。

后端对策有两层，重点是第二层：

```java
// 输出/存储时对富文本做净化，剥掉 <script>、onerror 等危险内容
// 用成熟库（如 OWASP Java HTML Sanitizer），不要自己写正则黑名单
String safe = htmlSanitizer.sanitize(userInput);
```

- **输出编码**：返回给页面渲染的内容做 HTML 转义（`<` → `&lt;`），让脚本变成纯文本显示。
- **存储净化**：如果业务允许富文本（如带格式的描述），存库前用白名单净化器只保留安全标签。

**前端类比**：React/Vue 默认会对插值转义，所以你平时很少手动处理；但只要用了 `dangerouslySetInnerHTML` / `v-html`，这层保护就没了。后端不能假设前端一定安全渲染——别的客户端、邮件、导出 PDF 都可能直接渲染你存的内容，所以**存进去的就该是干净的**。

### CSRF：token + SameSite

CSRF 是诱导已登录用户在不知情下发出请求（借用浏览器自动带 Cookie 的特性）。后端对策：

- **如果用 Cookie 存登录态**：给 Cookie 设 `SameSite=Lax` 或 `Strict`，并对写操作校验 CSRF token（同步令牌模式）。
- **如果用 Authorization 头带 JWT**（本项目方案）：天然基本免疫 CSRF——因为攻击者的跨站请求无法读到你 localStorage 里的 token、也就无法主动加上 `Authorization` 头。这是很多前后端分离项目用 header token 而非 Cookie 的原因之一。

```text
Set-Cookie: refreshToken=xxx; HttpOnly; Secure; SameSite=Strict; Path=/auth
            └──────┬──────┘  └──┬──┘ └─┬─┘ └────┬─────┘
            不让 JS 读(防 XSS 偷) 只走HTTPS  跨站不带  限制路径
```

**前端类比**：你设过 axios 的 `withCredentials`、遇到过浏览器对 Cookie 的 SameSite 限制——后端就是在 `Set-Cookie` 里把这些防护开关打开的那一方。

### IDOR 越权：查数据必须校验归属

这是最隐蔽、最高发、也最该警惕的漏洞。IDOR（不安全的直接对象引用）：接口只校验了"你登录了"（认证），没校验"这条数据是不是你的"（授权）。

危险写法——只信前端传来的 id：

```java
// ❌ 用户传 taskId=任意值，就能查/删别人的任务
@GetMapping("/canvas/task/{taskId}")
public RtData<TaskVO> getTask(@PathVariable String taskId) {
    return RtData.ok(taskService.findById(taskId));   // 没校验归属！
}
```

正确写法——把当前登录用户的 uid 作为查询条件，让"归属校验"发生在数据层：

```java
// ✅ uid 来自 token（后端可信），不是前端传的；查不到就是越权或不存在
@GetMapping("/canvas/task/{taskId}")
public RtData<TaskVO> getTask(@PathVariable String taskId) {
    Long uid = SecurityContext.currentUid();          // 从已验签的 token 取
    Task task = taskService.findByIdAndUid(taskId, uid);
    if (task == null) {
        return RtData.fail("任务不存在");               // 不暴露"存在但不是你的"
    }
    return RtData.ok(TaskVO.from(task));
}
```

**关键点**：身份信息（uid、role）必须来自**后端验签后的 token**，绝不能信任前端请求体/参数里传来的 uid。前端传 `uid` 只能用来做 UI，后端要用自己认定的 uid 覆盖它。

**前端类比**：你在前端可能根据 `currentUser.id === task.ownerId` 来决定显不显示"删除"按钮——那只是"不给看按钮"，攻击者直接 curl 你的接口、把 taskId 改成别人的照样能打。**所以后端必须独立做归属校验**。

### 敏感信息泄露：日志和响应都别带

两个最容易漏的出口：

```java
// ❌ 日志里打了密码/token，日志一旦被读到就泄露
log.info("login req: {}", JSON.toJSONString(loginReq)); // loginReq 含明文密码

// ✅ 脱敏后再打，或干脆只打非敏感字段
log.info("login attempt: username={}", loginReq.getUsername());
```

- **响应**：返回用户对象时，DTO 里绝不能带 `passwordHash`、`refreshToken`、内部 OSS 密钥。用专门的 VO/DTO 出参，不要把数据库实体直接序列化返回（呼应 [三层架构](/back-end/frontend-backend-guide/04-three-layer-and-structure)）。
- **日志**：手机号、身份证、token、密码一律脱敏（如 `138****8000`）。日志怎么看见 [看日志](/back-end/frontend-backend-guide/26-reading-logs)。
- **报错**：生产环境别把堆栈、SQL、内部 IP 直接吐给前端。统一用 RtData 包一个友好提示，细节进日志。

### 批量改请求绕过前端校验：后端必须重新校验

前端的表单校验（哪怕你用了 zod 写得很严）对攻击者**完全无效**——他可以用 Postman/curl 绕过你的页面直接打接口。

```java
// 后端用 Bean Validation 重新校验，和前端 zod 各管各的
public record CreateTaskReq(
    @NotBlank @Size(max = 2000) String prompt,    // 不信前端限了长度
    @Min(1) @Max(4) int count                     // 一次最多生成 4 张
) {}

@PostMapping("/canvas/task")
public RtData<String> create(@RequestBody @Valid CreateTaskReq req) {
    // @Valid 触发校验，不合法直接被全局异常处理拦截，返回统一错误
    return RtData.ok(taskService.submit(req));
}
```

更关键的是**业务规则也要后端校验**：配额够不够、用户是不是 VIP、能不能用这个模型——这些前端可以提示，但**扣配额、判权限的最终决定权在后端**（呼应 32.3 的登录、第 12 章用分布式锁扣配额）。

> 这就是本章的核心铁律，值得单独成行：**前端校验是为了体验（即时反馈、少跑一趟网络），不是为了安全；任何能影响数据和权限的校验，后端必须独立再做一遍。**

---

## 32.5 一张表：漏洞与后端对策速查

| 漏洞 | 一句话 | 后端核心对策 |
| --- | --- | --- |
| SQL 注入 | 输入被当成 SQL 执行 | 参数化查询 / ORM，绝不拼 SQL |
| 存储型 XSS | 恶意脚本存库后被渲染 | 输出转义 + 存储白名单净化 |
| CSRF | 借登录态发非预期请求 | SameSite Cookie / CSRF token；header 带 JWT 基本免疫 |
| IDOR 越权 | 改 id 访问别人的数据 | uid 取自 token，按归属查询 |
| 信息泄露 | 日志/响应带敏感字段 | DTO 出参脱敏、日志脱敏、生产不吐堆栈 |
| 绕过前端校验 | 用 curl 直接打接口 | 后端 Bean Validation + 业务规则重校验 |
| 弱密码存储 | 拖库即沦陷 | bcrypt/Argon2 加盐慢哈希 |
| 越权伪造身份 | 篡改 JWT payload | 后端验签，密钥严格保管 |

---

## 32.6 实操：验证一个越权漏洞

把上面 IDOR 的对策落到一次真实验证里，体会"为什么不能信前端"。

**目标**：确认 `/canvas/task/{taskId}` 修好后，A 用户拿不到 B 用户的任务。

**命令**（A 用自己的 token 去查一个属于 B 的 taskId）：

```bash
# A 的 token，去查 B 的任务 ID
curl -s -H "Authorization: Bearer <A的access token>" \
  http://localhost:8080/canvas/task/task-belongs-to-B \
  | jq .
```

**修复前的预期输出**（错误，泄露了 B 的数据）：

```json
{ "code": 0, "msg": "ok", "data": { "taskId": "task-belongs-to-B", "uid": 20002, "prompt": "B 的私密 prompt" } }
```

**修复后的预期输出**（正确）：

```json
{ "code": 40400, "msg": "任务不存在", "data": null }
```

**怎么读这段输出**：修复前 `data` 里出现了 `uid: 20002`（B 的 uid），说明 A 越权读到了 B 的数据——这是高危漏洞。修复后返回"任务不存在"且不区分"真不存在"还是"不是你的"，A 既拿不到数据，也猜不出这个 id 是否存在。**结论**：归属校验必须在后端按"token 里的 uid + 资源 id"联合查询，前端传什么 uid 都不作数。

---

## 32.7 传输与密钥：HTTPS、Secret 管理、最小权限

### 全站 HTTPS

明文 HTTP 下，token、密码在链路上任何一跳都能被截获（中间人攻击）。生产**全站强制 HTTPS**，HTTP 一律 301 跳转到 HTTPS。本项目里 TLS 通常在 svc-gateway 或更前面的负载均衡/Ingress 终结，内部服务间走内网（呼应 [后端网络](/back-end/frontend-backend-guide/22-networking-for-backend)）。

**前端类比**：浏览器地址栏那把锁、混合内容（mixed content）警告，就是 HTTPS 在前端的体现。后端要保证证书有效、协议版本不过时、敏感 Cookie 带 `Secure`。

### 密钥/Secret 绝不进代码库

JWT 密钥、数据库密码、OSS AccessKey、第三方 API key——**一个都不能硬编码进代码、不能提交到 Git**。一旦进了 Git 历史，即使后来删掉，历史里依然能翻出来，等于永久泄露。

```yaml
# ❌ 绝不这样写死在 application.yml 里提交
jwt:
  secret: my-super-secret-key-123

# ✅ 用环境变量/外部配置注入，代码库里只留占位
jwt:
  secret: ${JWT_SECRET}     # 值由 K8s Secret / 配置中心 / 环境变量提供
```

Secret 的来源和注入方式（环境变量、K8s Secret、配置中心）在 [配置与环境管理](/back-end/frontend-backend-guide/25-config-and-env) 里详谈。本章只强调一句：**代码库里只能有占位符，真实值由运行环境提供**。

**前端类比**：你知道前端的 `.env` 里 `VITE_` 开头的变量会被打进 bundle、所有人可见，所以从不把真密钥放前端——后端是同样的纪律，而且更严格：连配置文件本身都不进库。

### 最小权限原则

每个组件只给它干活所必需的最小权限：

- 数据库账号：svc-user 只给 user 库的读写，不给 DROP、不给别的库。
- OSS：svc-oss 的 AccessKey 只允许操作指定 bucket，不给删全站的权限。
- 容器：不用 root 跑应用进程（呼应 [Docker 实战](/back-end/frontend-backend-guide/23-docker-in-practice)）。

**前端类比**：像申请浏览器权限——只在需要时申请定位/摄像头，而不是一上来要全部权限。万一某个服务被攻破，最小权限能把爆炸半径限制在最小范围。

---

## 小结

- **认证 vs 授权**：认证管"你是谁"（失败 401），授权管"你能干啥"（失败 403）；只做认证不做授权是最常见的安全洞。
- **JWT** 是三段式无状态令牌，payload 只是 Base64 编码（公开可读），敏感信息绝不能放；服务端靠验签防伪造，配 access + refresh token 解决过期与吊销。
- **密码**只存 bcrypt/Argon2 加盐慢哈希，绝不明文、绝不可逆、绝不裸 MD5。
- **常见漏洞**逐个有定式：SQL 注入用参数化、XSS 做转义与净化、IDOR 按 token 里的 uid 校验归属、敏感信息出参和日志都脱敏。
- **贯穿铁律**：前端校验只为体验，后端必须重新校验一切影响数据与权限的输入；HTTPS 全站、密钥不进库、最小权限。

### 自测

1. 同一个用户登录后，调用"删除画布"接口返回了 403，调用"获取我的资料"接口返回了 401。分别说明这两个状态码背后最可能的原因，它们各属于认证还是授权问题？
2. 同事说"我们 JWT 的 payload 里放了用户手机号，反正前端要用，问题不大"。请指出这为什么是安全隐患，并给出正确做法。
3. 一个查询接口 `GET /canvas/task/{taskId}` 只校验了登录态。写出它存在的漏洞名称，以及把 taskId 改成别人的会发生什么、应该怎么改。

### 下一章

下一章进入异步世界，看消息队列在保证"任务不丢、不重复"上做了哪些功夫：[消息队列可靠性](/back-end/frontend-backend-guide/33-mq-reliability)。

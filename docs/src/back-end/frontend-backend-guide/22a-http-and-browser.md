---
prev:
  text: "A3. IP、端口、DNS、TCP 与 TLS"
  link: "/back-end/frontend-backend-guide/22-networking-for-backend"
next:
  text: "A5. 代理、网关与服务发现"
  link: "/back-end/frontend-backend-guide/22b-proxy-and-discovery"
---

# 22a. HTTP 与浏览器：任务接口不只是一个 fetch

> 场景：`curl` 查询任务成功，网页却报跨域；“标记完成”显示失败，刷新后任务却已经完成。
> 网络连接成功之后，还要理解 HTTP 的消息、数据契约，以及浏览器额外执行的安全规则。

前置是 [22. 网络入门](/back-end/frontend-backend-guide/22-networking-for-backend)，不要求你已经会 HTTP。本篇继续使用单体 `task-api:8080` 的 `/api/tasks`；CRUD 最初用 H2，迁移 MySQL 时保持同一套对外契约。**初始源码没有登录与用户隔离，不能直接公网部署；本篇的登录、CORS、缓存和推送是后续扩展设计，不代表已经实现。**

**学完你能做什么：**

- 从请求和响应中读出方法、路径、头、状态和 JSON 数据的含义。
- 解释“HTTP 成功但页面读不到响应”，正确配置跨域与 Cookie，而非关闭安全机制。
- 为任务列表选择合理的缓存和状态更新方式，避免重复提交及数据泄漏。

## 22a.1 HTTP 是一问一答的消息协议

浏览器向任务接口发**请求**，服务器返回**响应**。下面用容易阅读的 HTTP/1.1 文本演示；HTTP/2、HTTP/3 用帧传输，但方法、状态、头字段等语义仍然存在。

```http
POST /api/tasks HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Accept: application/json

{"title":"学习网络"}
```

- 第一行是请求行：方法 `POST`、请求目标 `/api/tasks`、协议版本。
- 头字段描述消息：`Content-Type` 说明**发送的正文**是什么格式；`Accept` 表示希望收到什么格式。
- 空行结束头部，其后才是正文。HTTP/1.1 的正文还需用长度或传输编码等规则界定，不是“读到连接关闭才算一条”。
- URL 里的查询参数属于请求目标，例如 `/api/tasks?page=0&size=20`，不是请求头或 JSON。

响应的逻辑结构如下；头字段大小写不影响其语义：

```http
HTTP/1.1 201 Created
Content-Type: application/json
Location: /api/tasks/550e8400-e29b-41d4-a716-446655440000
Cache-Control: no-store

{
  "id":"550e8400-e29b-41d4-a716-446655440000",
  "title":"学习网络",
  "status":"TODO",
  "createdAt":"2026-01-15T08:30:00Z"
}
```

状态行说明创建成功；`Location` 指向新资源。主线成功时直接返回资源，错误返回 `{code,message,requestId}`；列表返回 `{items,total,page,size}`，`page` 从 0 开始，`size` 为 1～100，默认 20。上方 `Cache-Control` 是建议增加的策略，不代表初始 CRUD 已配置缓存。完整契约见 [CRUD 实战](/back-end/frontend-backend-guide/07-build-a-crud-api)，设计取舍见 [API 设计](/back-end/frontend-backend-guide/34-api-design)。

## 22a.2 方法告诉服务器“想做什么”

| 方法 | 任务例子 | 安全 / 幂等语义 |
| --- | --- | --- |
| GET | `GET /api/tasks` 查列表 | 应只读；幂等 |
| HEAD | `HEAD /api/tasks/{id}` 看元数据 | 类似 GET，但响应无正文；幂等 |
| POST | `POST /api/tasks` 创建任务 | 一般不幂等，重复发送可能创建两条 |
| PUT | `PUT /api/tasks/{id}` 完整更新可编辑字段 title、status | 应幂等，不应把“状态推进一步”藏在里面 |
| PATCH | `PATCH /api/tasks/{id}` 修改部分字段 | 是否幂等取决于补丁语义 |
| DELETE | `DELETE /api/tasks/{id}` 删除任务 | 应幂等；重复删除可返回不同状态 |
| OPTIONS | 查询接口支持能力、参与跨域预检 | 不是业务“创建任务”操作 |

“安全”表示方法按语义不应请求业务状态变更，不是“请求不会被攻击”。“幂等”表示重复请求的预期状态效果相同，不保证状态码、日志或响应时间一致。

主线 `status` 取 `TODO`、`DOING`、`DONE`。PUT 重复发送 `{"title":"学习网络","status":"DONE"}` 可以幂等；“每次把状态推进一步”则不幂等。表中的 `{id}` 要替换为创建响应里的 UUID。CRUD 实现 PUT，不自动提供 PATCH；HEAD、OPTIONS 常由框架处理，也不意味着已经配置 CORS。

超时意味着**结果不确定**，并不意味着后端没执行。对 POST 自动重试可能重复创建。需要重试时，可设计幂等键、服务端去重和可查询结果，不能靠“前端按钮禁用”保证可靠性。

## 22a.3 状态码是结果分类，不是事故判决书

| 状态 | 如何理解 | 任务接口中的例子 |
| --- | --- | --- |
| 200 / 201 / 204 | 成功 / 已创建 / 成功且无正文 | 列表 / 创建 / 删除 |
| 304 | 缓存验证通过，使用已有副本；无响应正文 | 带条件的 GET 未发生变化 |
| 400 | 请求无法按要求处理 | JSON 语法错、字段校验失败 |
| 401 | 缺少有效认证凭据 | 会话过期；标准认证挑战按方案返回 |
| 403 | 拒绝访问，不一定能据此断言已登录 | 无任务操作权限、CSRF 检查失败 |
| 404 / 405 | 找不到目标 / 目标不支持该方法 | 任务不存在 / 对只读端点发 POST |
| 409 / 415 | 资源状态冲突 / 不支持正文媒体类型 | 版本冲突 / 发送了错误 Content-Type |
| 429 | 请求过多 | 限流；是否及多久重试看契约和 Retry-After |
| 500 / 503 | 内部异常 / 暂时无法服务 | 未处理异常 / 过载或维护 |
| 502 / 504 | 代理的上游响应无效 / 等待上游超时 | 详见下一篇代理排错 |

`504` 既可能发生在连接上游阶段，也可能发生在等待响应阶段，**不能证明上游收到了请求**，也不能证明写操作未成功。`502` 也不等于“已连上后端”：连接拒绝等情况常被代理映射为 502，最终要看代理日志。

JavaScript 的 `fetch()` 遇到 404、500 通常仍返回 `Response`；连接失败、CORS 失败等才会使调用拒绝。不要把 `catch` 当作所有失败的兜底：

```js
const response = await fetch('/api/tasks')
if (!response.ok) throw new Error(`HTTP ${response.status}`)
const result = response.status === 204 ? null : await response.json()
```

`204` 没有正文，不能继续 `response.json()`。有 JSON 也不代表一定符合 TypeScript 类型：类型断言不做运行时校验，仍需按契约检查字段。

## 22a.4 JSON：对象需要被转换成字节

序列化是把内存对象转换成可传输表示，反序列化是把表示读回对象。HTTP 不会直接传一个 Java 对象或 JS 对象。

```js
await fetch('http://localhost:8080/api/tasks', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ title: '学习网络' })
})
```

后端通常由 JSON 库将正文绑定到请求 DTO，再做字段校验；数据库 Entity 不宜直接作为全部出入参。H2 换成 MySQL，应该迁移存储层，而不是让前端跟着换字段格式。

对前端尤其重要的四项约定：

1. **ID**：主线用 UUID 字符串。其他项目若使用 Java `Long`、MySQL `BIGINT`，可能超过 JS 安全整数上限 `2^53-1`，对外也可统一为字符串。不要先转成 number 再转回，精度可能已经丢失。
2. **时间点**：优先约定带时区的 ISO 8601 字符串，如 `2026-01-15T08:30:00Z`；后端 `Instant` 表示时间点，前端按用户时区展示。“截止日期”若只有日历日期，应另约定 `YYYY-MM-DD`，别强行当 UTC 午夜。
3. **空值**：`null`、空字符串、缺失字段语义不同，部分更新尤其要区分“未改”和“清空”；`undefined` 不是 JSON 值。
4. **数值与编码**：金额可约定最小单位整数或精确十进制字符串；文本使用 UTF-8，路径与查询参数用 URL 编码，不要手工拼接用户输入。

## 22a.5 同源策略和 CORS：谁能读取响应

浏览器里的“源”由**协议、主机、端口**组成。`http://localhost:5173` 和 `http://localhost:8080` 不同源；路径不同不改变源。浏览器同源策略限制跨源脚本读取，CORS 是服务器声明允许哪些源访问的一套机制。

因此 `curl` 成功不代表浏览器也能读取。CORS 主要由浏览器执行，不是服务端调用的身份验证，也挡不住别人直接用脚本访问 API。

以下请求常触发**预检**：方法为 PUT / DELETE，或发送 `Content-Type: application/json`，或添加 `Authorization` 等不在安全列表内的头。浏览器先用 OPTIONS 问权限，再决定是否发实际请求。

![跨源 JSON 请求先预检，再发送实际任务请求](/backend-guide/network-cors-preflight.svg)

从 5173 向 8080 创建任务时，预检的关键部分是：

```http
OPTIONS /api/tasks HTTP/1.1
Origin: http://localhost:5173
Access-Control-Request-Method: POST
Access-Control-Request-Headers: content-type
```

服务端允许时可返回如下声明；这不是要求业务接口返回任务 JSON：

```http
HTTP/1.1 204 No Content
Access-Control-Allow-Origin: http://localhost:5173
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
Access-Control-Allow-Headers: Content-Type
Vary: Origin
```

随后实际 POST 的响应**也要**有合适的 `Access-Control-Allow-Origin`，错误响应同样如此。`Location`、自定义请求 ID 等不是默认全部可读，跨源 JS 要读取时还需 `Access-Control-Expose-Headers`。预检缓存可由 `Access-Control-Max-Age` 控制，但浏览器有自身上限。

如果预检被认证过滤器直接挡成 401，实际请求不会发出。若无需预检的请求直接发出、或实际响应的跨域头不正确，则可能是**服务端已经执行，但 JS 无法读取响应**。看到 CORS 错误就重试写操作很危险。

开发代理可以让浏览器只访问 5173 的 `/api`，由开发服务器转发到 8080，浏览器视角不再跨源；生产仍须设计真实域名和入口。不要以为 `mode: 'no-cors'` 是修复：它会限制请求并给出不可供 JS 正常读取的 opaque 响应。

## 22a.6 Cookie、SameSite 与 CSRF：自动携带也有代价

Cookie 是浏览器按规则保存并随请求携带的数据，常用来携带会话标识。服务端可发送：

```http
Set-Cookie: SESSION=opaque-value; Path=/; HttpOnly; Secure; SameSite=Lax
```

这是 **HTTPS 部署示例**，不是要求本地 HTTP 联调照抄所有属性。

| 属性或选项 | 作用及边界 |
| --- | --- |
| `HttpOnly` | JS 不能直接读取 Cookie，但 XSS 仍可能代用户发请求 |
| `Secure` | 限制在安全传输环境发送；生产认证 Cookie 应使用 HTTPS |
| `Domain` / `Path` | 控制发送范围；通常省略 Domain 形成仅当前主机 Cookie，Path 不是安全隔离边界 |
| `SameSite` | 控制跨站场景的发送；Strict 更严，Lax 允许部分顶层安全导航，None 通常要求 Secure |
| `credentials: 'include'` | fetch 跨源时请求携带符合条件的 Cookie；不能覆盖 SameSite 或浏览器第三方限制 |

**同站不等于同源**：现代浏览器通常按协议与可注册域名判断站点，端口不是同站判断要素。因此 localhost 两个端口可跨源但同站。`SameSite=None` 也不保证第三方 Cookie 一定被允许。

跨源带凭据时，前端要显式设置 credentials；服务端要允许凭据，并返回精确的允许源，不能用 `Access-Control-Allow-Origin: *`。不要无条件回显任意 Origin；按白名单校验，并根据缓存策略设置 `Vary: Origin`。

CSRF（跨站请求伪造）的核心是：恶意网页诱导浏览器**借用自动携带的凭据**执行操作。攻击者不一定需要读取响应，所以“有 CORS”不等于防住 CSRF。

防护应结合：禁止 GET 改业务状态、合理 SameSite、校验 Origin / Referer、使用与会话绑定的 CSRF token，并使用成熟安全框架。token 可以放在自定义请求头；不要误把它当成另一个随意可猜的参数。手工放 Authorization 的令牌方案减少传统 Cookie 型 CSRF 风险，但仍需防 XSS 和凭据泄漏。

## 22a.7 缓存：省一次查询，不能泄漏别人的任务

HTTP 缓存可能在浏览器、反向代理、CDN 等位置。它和应用缓存、MySQL 查询缓存不是同一个概念。

| 响应策略 | 含义 | 任务系统如何选 |
| --- | --- | --- |
| `Cache-Control: no-store` | 不存储这次响应 | 入门阶段含私人任务、认证信息时优先明确使用 |
| `Cache-Control: private, max-age=30` | 私有缓存可在 30 秒内复用 | 允许短暂旧数据、且正确隔离账户时考虑 |
| `Cache-Control: no-cache` | 可以存，但复用前须向服务器验证 | 不是“不缓存”，适合需要验证新鲜度的数据 |
| `ETag` + `If-None-Match` | 比较资源版本，无变化返回 304 | 减少传输，不意味着不访问后端或数据库 |

带 Cookie 不会自动使所有共享缓存都正确隔离用户。生产不要让 CDN 用只含路径的键缓存每个人的 `/api/tasks`；私人 API 应明确禁止共享缓存或设计严格隔离。注销、切换账号时也应清理前端自身缓存。

带内容哈希的公开 JS/CSS 常适合长期缓存；私人任务列表不能直接照抄静态资源缓存规则。缓存键、查询参数和 `Vary` 都影响命中，HTTP 缓存不是“只看 URL 的一个 Map”。

## 22a.8 任务状态变化：轮询、SSE 还是 WebSocket

假设后来加入“批量导入任务”，前端想知道进度。先选最简单能满足需求的方式，不为了“实时”拆微服务。

| 方案 | 通信方式 | 适合与代价 |
| --- | --- | --- |
| 轮询 | 每隔一段时间 GET 状态 | 最易落地；有延迟及重复查询，要避免上次未结束就堆新请求 |
| SSE（服务端发送事件） | HTTP 长响应，服务端持续发送 `text/event-stream` | 单向进度通知；浏览器 EventSource 支持重连，要设计事件 ID 与补发 |
| WebSocket | 建立长连接后双向发消息 | 适合协作编辑；要额外设计心跳、鉴权、重连、消息补偿 |

SSE 不是“HTTP 响应只能一次性返回”的例外魔法，而是流式响应格式；中间代理缓冲和空闲超时可能使它迟迟不显示。原生 EventSource 不提供任意自定义请求头接口，认证设计须考虑这一限制。

WebSocket 常见于 HTTP/1.1 Upgrade 握手，后续消息不再是一问一答的普通 HTTP 请求；它不是“比 HTTP 更新所以一定更快”。跨协议版本的建立机制也可能不同。服务端仍要校验 Origin 与用户权限，不能只依赖浏览器页面入口。

不论选哪种，断线后都应能用普通 GET 重新获取权威状态，不能仅凭“没有收到事件”推断任务未完成。

## 22a.9 动手：读报文，再观察浏览器规则

前置：已有本机任务服务；以下命令是读者实验，不需要额外起服务。示例避免携带真实会话。若接口启用认证，预期可能是 401，应按项目登录流程获得测试凭据，**不要为实验关闭认证**。

### 实验一：读列表响应

```bash
curl --noproxy '*' -i --max-time 5 http://localhost:8080/api/tasks
```

预期：状态行、响应头、空行、JSON；空列表也应按契约返回成功，而不是把“没有任务”当成网络失败。检查 Content-Type，不要仅凭正文长得像 JSON 就假定服务器声明正确。

### 实验二：模拟预检（只查询，不创建任务）

```bash
curl --noproxy '*' -i --max-time 5 -X OPTIONS \
  http://localhost:8080/api/tasks \
  -H 'Origin: http://localhost:5173' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type'
```

预期：若服务已允许该开发源，应出现前述允许源、方法、头字段；未配置时可能 403、无跨域头或其他框架响应，说明**尚未授权该跨源场景**，不等于 TCP 不通。`curl` 不执行浏览器的跨域拦截，不能因它收到 200 就说 CORS 已通过。

### 实验三：用已有前端页面验证差异

若你已经有运行在 5173 的任务页面，在 DevTools Network 中查看创建任务的 OPTIONS 和 POST；确认页面请求是直连 8080 还是走同源开发代理。不要从任意真实网站的控制台向本地接口发送写请求。

预期：直连且满足预检条件时可看到 OPTIONS；已有预检缓存时可能看不到新的 OPTIONS；走开发代理时，浏览器看到的是对 5173 的请求。尚未运行前端时，完成实验二并手工解释报文即可。

## 常见误解

- “接口返回 200，页面一定能读”：跨域策略、JSON 解析、字段校验仍可能失败。
- “预检通过，后端就不用鉴权”：预检只许可跨源访问方式，实际请求仍需认证授权。
- “SameSite 就是判断端口是否相同”：它判断站点，不等同于同源。
- “GET 只查询，所以任何用户都可查任意任务”：方法安全性不替代资源权限检查。
- “实时连接不断，状态就不会丢”：网络会断、进程会重启，仍需重连和状态恢复。

## 自测与参考答案

1. `curl` 得到 201，但跨源网页报错，创建一定失败了吗？
2. 后端把 ID `9007199254740993` 输出成 JSON 数字，前端强转字符串能修复吗？
3. `Cache-Control: no-cache` 是否表示浏览器不能保存响应？
4. 只需要单向显示导入进度，应优先比较哪两种简单方案？

**参考答案：**

1. 不一定。实际请求可能已执行，只是响应不允许 JS 读取；先查 POST 和后端记录，再决定重试。
2. 不能保证。解析为 JS number 时可能已经失真，应该在序列化时就输出字符串。
3. 不是；它要求复用前验证。禁止存储用 no-store。
4. 先比较低频轮询和 SSE；只有明确双向实时需求时再考虑 WebSocket。

## 主线衔接

回看 [网络入门](/back-end/frontend-backend-guide/22-networking-for-backend) 的连接阶段，你现在又能解释“连上之后传了什么”。下一篇 [代理与服务发现](/back-end/frontend-backend-guide/22b-proxy-and-discovery) 将把浏览器、Nginx、单体任务服务和 MySQL 放到同一张部署图，分清每一层的责任。

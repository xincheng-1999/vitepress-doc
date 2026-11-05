---
prev:
  text: "A3. IP、端口、DNS、TCP 与 TLS"
  link: "/back-end/frontend-backend-guide/22-networking-for-backend"
next:
  text: "A5. 代理、网关与服务发现"
  link: "/back-end/frontend-backend-guide/22b-proxy-and-discovery"
---

# 连上后端后，浏览器怎样完成一次任务操作？

[上一课](/back-end/frontend-backend-guide/22-networking-for-backend) 讲了请求怎样找到后端。现在连接已经成功，页面要创建一个任务。浏览器该发什么，后端怎样表示结果，为什么有时 `curl` 成功了，页面却仍然报错？

下面顺着这一次操作讲 HTTP 和浏览器规则。例子仍是 `task-api:8080` 的 `/api/tasks`，最初使用 H2，之后换 MySQL，但对外的数据格式不应随数据库改变。初始 CRUD 没有登录与用户隔离；本篇涉及的认证、CORS、缓存和推送配置是后续设计，不代表源码已经实现，也不能据此把初版 API 开放到公网。

## 1. 创建任务，先把数据写进请求

前端的内存对象不能直接交给 Java。需要先把它转换成 JSON 文本，再作为 HTTP 正文发送；这一步叫**序列化**：

```js
await fetch('http://localhost:8080/api/tasks', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ title: '学习网络' })
})
```

把这次请求按 HTTP/1.1 的可读形式写出来，大致是：

```http
POST /api/tasks HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Accept: application/json

{"title":"学习网络"}
```

第一行里的 `POST` 是方法，表示这次要提交数据创建任务；`/api/tasks` 是请求目标。查询列表时的 `?page=0&size=20` 也属于请求目标，不是请求头或 JSON。

接下来的头字段描述消息：`Content-Type` 说明发送的正文是 JSON，`Accept` 表示希望收到 JSON。空行之后才是正文。HTTP/1.1 还需要按消息长度或传输编码等规则划分正文；HTTP/2、HTTP/3 改用帧传输，但方法、状态和头字段的含义仍然存在。

后端收到正文后，把 JSON 读成请求对象，这叫**反序列化**，之后再校验标题等字段。这类专门传递接口数据的对象常叫 DTO，用它明确哪些字段允许输入。数据库实体（Entity）里有的字段，不一定都允许客户端提交。

## 2. 响应告诉页面：这次操作的结果是什么

创建成功的响应可以这样读：

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

`201` 表示已创建，`Location` 指向新任务，正文是创建后的资源。这里的 `Cache-Control` 是建议增加的缓存策略，初始 CRUD 尚未配置它。

主线成功时直接返回资源；列表返回 `{items,total,page,size}`，页码从 0 开始，大小为 1～100、默认 20；错误返回 `{code,message,requestId}`。完整例子见 [CRUD 实战](/back-end/frontend-backend-guide/07-build-a-crud-api)，设计取舍见 [API 设计](/back-end/frontend-backend-guide/34-api-design)。头字段名称不区分大小写。

页面收到响应后，要先检查 HTTP 状态，再按契约读取正文：

```js
const response = await fetch('/api/tasks')
if (!response.ok) throw new Error(`HTTP ${response.status}`)
const result = response.status === 204 ? null : await response.json()
```

`fetch()` 收到 404、500 通常仍会返回 `Response`，不会仅因为状态失败就进入 `catch`。删除成功的 `204` 没有正文，继续调用 `response.json()` 反而会解析失败。有 JSON 也还要检查字段；TypeScript 类型断言不会做运行时校验。

因此，页面显示“失败”时，要分清是 HTTP 返回了错误，还是 HTTP 成功后，跨域读取、JSON 解析或页面处理出了问题。

## 3. 再改一次任务：方法为什么影响重试？

查询列表使用 GET，创建使用 POST，主线完整更新可编辑字段使用 PUT，删除使用 DELETE。例如把任务改成完成：

```json
{"title":"学习网络","status":"DONE"}
```

PUT 要把这组字段更新到指定值。重复提交后，预期状态仍然相同，这叫**幂等**。如果把“每次把状态推进一步”设计进 PUT，重复发送就会继续推进，不符合这个语义。任务状态固定为 `TODO`、`DOING`、`DONE`，路径里的 `{id}` 要替换为实际创建响应中的 UUID。

POST 创建通常不幂等，重复发送可能产生两条任务。超时只意味着客户端没及时拿到结果，不意味着服务器没创建成功；看到错误就自动重试，可能造成重复数据。确实需要可靠重试时，要结合幂等键、服务端去重和结果查询设计，前端禁用按钮只能减少误点。

GET 按语义只读，称为“安全方法”；这里的安全不是说没有攻击风险，更不是说任何人都能查看任意任务。DELETE 的预期状态效果应幂等，但重复删除可以返回不同状态码。

::: details 按需查阅：其他方法和状态码
HEAD 与 GET 类似，但响应没有正文；OPTIONS 可以查询能力，也会参与后面的跨域预检。框架能处理 HEAD、OPTIONS，不等于已经配置了 CORS。PATCH 用于部分修改，是否幂等要看补丁语义；初始 CRUD 实现的是 PUT，不自动提供 PATCH。

状态码用于分类结果，不能独自解释根因：

- 200、201、204：分别是成功、已创建、成功但无正文。304 表示缓存验证通过，使用已有副本，也没有响应正文。
- 400：请求格式或字段不符合要求。404 是找不到目标，405 是目标不支持该方法，415 是不支持正文的媒体类型。
- 401：缺少有效认证凭据，具体认证挑战按方案返回。403：拒绝访问，可能是权限或 CSRF 检查失败，不能仅凭它断言已登录。
- 409：资源状态冲突，例如版本冲突。429：请求过多，是否重试、等多久，要看接口契约和 `Retry-After`。
- 500：内部异常；503：暂时无法服务，例如过载或维护。502、504 常由代理返回，分别涉及上游通信异常和超时，下一课会继续解释。

502 不保证已经连上应用；504 也可能发生在连接上游时，不能据此判断应用已经执行或一定没执行。
:::

### 字段约定也会影响操作是否正确

主线 ID 用 UUID 字符串。其他项目若使用 Java `Long` 或 MySQL `BIGINT`，数值可能超过 JavaScript 安全整数上限 `2^53-1`，可在服务端输出为字符串；解析成 number 后再转字符串，精度可能已经丢失。

时间点约定为带时区的 ISO 8601 字符串，例如 `2026-01-15T08:30:00Z`，后端用 `Instant` 表示，前端按用户时区展示。如果只是“截止日期”，则另约定 `YYYY-MM-DD`，不必强行转换成 UTC 午夜。

同样要分清 `null`、空字符串和缺失字段；做部分更新时，它们可能分别意味着清空、空内容和不修改，`undefined` 不是 JSON 值。金额可用最小单位整数或精确十进制字符串表达。文本使用 UTF-8，路径和查询参数要正确 URL 编码，不手工拼接用户输入。

## 4. 为什么 curl 能创建，浏览器却报跨域？

假设任务页面运行在 `http://localhost:5173`，接口在 `http://localhost:8080`。两者端口不同，属于不同的**源**：源由协议、主机和端口组成，路径不参与判断。

浏览器同源策略会限制页面脚本读取跨源响应。CORS 则是服务器声明“允许哪些源以哪些方式访问”的机制。`curl` 不执行这套浏览器拦截，所以它拿到响应，不等于页面也有权读取。CORS 也不是 API 身份认证，挡不住其他程序直接访问。

发送跨源 JSON POST 前，浏览器通常先发一条 OPTIONS，称为**预检**：

```http
OPTIONS /api/tasks HTTP/1.1
Origin: http://localhost:5173
Access-Control-Request-Method: POST
Access-Control-Request-Headers: content-type
```

它在问：“这个源可以向此接口发 POST，并带 content-type 头吗？”PUT、DELETE 或 Authorization 等不在安全列表内的请求头，也常触发预检。

![跨源 JSON 请求先预检，再发送实际任务请求](/backend-guide/network-cors-preflight.svg)

若服务端允许这个开发源，可以声明：

```http
HTTP/1.1 204 No Content
Access-Control-Allow-Origin: http://localhost:5173
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
Access-Control-Allow-Headers: Content-Type
Vary: Origin
```

预检通过后，浏览器才发实际 POST。实际响应和错误响应也需要合适的跨域头。跨源 JS 若要读取 `Location` 或自定义请求 ID，还要配置 `Access-Control-Expose-Headers`。预检结果可以用 `Access-Control-Max-Age` 缓存，但浏览器有自身上限。

排查时打开 DevTools Network，看失败的是 OPTIONS，还是实际 POST。预检若被认证过滤器直接挡成 401，POST 就不会发出；如果实际请求已经发送，只是响应缺少允许头，任务可能已经创建，JS 却读不到。此时应先确认结果，而不是直接重试。

开发时也可以让页面只请求 5173 的 `/api`，由开发服务器转发到 8080。浏览器看到的是同源请求；生产仍要配置实际入口。`mode: 'no-cors'` 不是另一种修复，它会限制请求并产生无法正常读取的 opaque 响应。

可以用下面的只读命令观察服务是否声明了跨源许可：

```bash
curl --noproxy '*' -i --max-time 5 -X OPTIONS \
  http://localhost:8080/api/tasks \
  -H 'Origin: http://localhost:5173' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type'
```

初始服务尚未配置 CORS 时，可能返回 403、缺少允许头或其他框架响应；这表示该跨源场景尚未获准，不是 TCP 不通。若已有 5173 页面，再从自己的开发页面验证 OPTIONS 和 POST；已有预检缓存时，可能看不到新的 OPTIONS。不要从任意真实网站的控制台向本地接口发送写请求。

## 5. 加入登录后，浏览器还会自动带 Cookie

以后用 Session 登录时，服务端可让浏览器保存会话 Cookie。下面是 HTTPS 部署的示意，不是初始 CRUD 已有的功能：

```http
Set-Cookie: SESSION=opaque-value; Path=/; HttpOnly; Secure; SameSite=Lax
```

浏览器之后按规则携带它，服务器据此查找会话。`HttpOnly` 限制 JS 直接读取 Cookie；`Secure` 限制安全传输，生产认证 Cookie 应配 HTTPS。本地 HTTP 联调不能不分环境照抄配置。

Cookie 的发送范围还受主机、路径和 SameSite 影响。通常省略 Domain，让 Cookie 仅属于当前主机；Path 不是安全隔离边界。SameSite 判断的是“站”，不等于“源”：现代浏览器通常按协议与可注册域名判断站点，端口不参与，所以 localhost 的两个端口可以跨源但同站。

跨源 fetch 要携带符合条件的 Cookie，需要 `credentials: 'include'`；服务端同时允许凭据并返回精确的允许源，不能用 `Access-Control-Allow-Origin: *`。服务器应校验源白名单，不无条件回显 Origin；按缓存策略设置 `Vary: Origin`。这些设置仍不能覆盖 SameSite 或浏览器第三方 Cookie 限制。

自动携带凭据也带来了 **CSRF**：恶意网页可能诱导浏览器借用登录状态执行操作。攻击者未必需要读取响应，所以 CORS 不能替代 CSRF 防护。使用成熟安全框架，结合合理 SameSite、Origin / Referer 校验、与会话绑定的 CSRF token，并让 GET 保持只读。XSS 则仍可能代用户发请求，HttpOnly 不会消除它。

::: details 按需查阅：SameSite 与令牌方案的边界
Strict 的跨站限制更严；Lax 允许部分顶层安全导航；None 通常需要 Secure，但也不保证第三方 Cookie 一定可用。CSRF token 可以通过自定义请求头提交，需由服务端与会话校验，不是随便加一个参数。

手工放入 Authorization 的令牌减少了传统 Cookie 型 CSRF 风险，但仍要防 XSS、令牌泄漏及错误存储。
:::

## 6. 页面再次查列表时，拿到的是哪一份数据？

列表查询可以先观察：

```bash
curl --noproxy '*' -i --max-time 5 http://localhost:8080/api/tasks
```

有本机服务时，应看到状态、头、空行和分页 JSON。空列表仍是成功；若后来开启认证，401 也是可能的正常结果，应按登录流程使用测试凭据，而不是为实验关闭认证。

除了后端，浏览器、反向代理或 CDN 也可能保存响应，这叫 HTTP 缓存，和应用缓存、数据库缓存不是一回事。私人任务接口起步时可明确使用 `Cache-Control: no-store`，要求不存储本次响应。Cookie 本身不能保证所有共享缓存都隔离了用户；CDN 若只按路径缓存 `/api/tasks`，就可能把一个人的任务发给另一个人。

如果允许浏览器短暂复用，可以评估 `private, max-age=30`。如果允许保存，但每次复用前都要向服务器验证，可以用 `no-cache`。它不是“不缓存”；ETag 与 `If-None-Match` 可以协助验证资源版本，未变化时返回 304，减少正文传输，但不保证后端或数据库不工作。

前端自己维护的内存缓存也要在注销、切换账号时清理。带内容哈希的公开 JS/CSS 可以长期缓存，私人任务列表不能直接套用它的规则；查询参数、缓存键和 `Vary` 都会影响复用。

::: details 以后要显示任务进度时：轮询、SSE 还是 WebSocket？
如果新增批量导入，先比较定时 GET 进度的轮询：最容易落地，但有延迟，且要避免上一次没结束就堆积下一次请求。

需要单向持续通知时，可考虑 SSE：服务端保持 HTTP 流式响应，发送 `text/event-stream`。EventSource 支持重连，仍需事件 ID 与补发；原生接口不能任意设置请求头，认证需提前设计，代理缓冲和空闲超时也可能影响显示。

只有明确需要双向实时交互时，再评估 WebSocket。它常通过 HTTP/1.1 Upgrade 建连，之后不再是普通的一问一答，其他协议版本的建立方式也可能不同。它仍需鉴权、Origin 检查、心跳、重连和消息恢复，并不因为“更新”就一定更快。

无论哪种方式，断线后都应能用普通 GET 重新获取权威状态，不能因为没收到事件就认定任务没完成。这些都属于扩展设计。
:::

一次页面操作，到这里才算读清楚了：发出的请求要符合契约，收到的状态与正文要正确处理，浏览器也必须允许页面使用响应。

下一篇：[A5 · 代理、网关与服务发现](/back-end/frontend-backend-guide/22b-proxy-and-discovery)。部署后请求通常会先到入口代理，我们继续看它怎样把请求交给应用。

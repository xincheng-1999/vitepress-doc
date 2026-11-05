---
prev:
  text: "B4. 可运行任务 CRUD"
  link: "/back-end/frontend-backend-guide/07-build-a-crud-api"
next:
  text: "C1. 数据建模、SQL 与索引"
  link: "/back-end/frontend-backend-guide/10-sql-and-indexes"
---

# API 设计：点下“保存”以后，前后端怎样知道发生了什么

页面把任务标为完成，发出了 `status: "DONE"`，后端却只认另一个字段；创建失败也返回 200，前端又把错误当成任务放进列表。两边各自看代码都说得通，接在一起却无法工作。

接口契约就是把这些约定写清楚：请求应该带什么，成功后会得到什么，失败怎样分辨，超时后能不能重试。下面沿着创建、显示、修改任务的过程，把已有 API 的行为说清楚，再讨论功能变化时怎样保持兼容。

前置是 [HTTP 与浏览器](/back-end/frontend-backend-guide/22a-http-and-browser) 和 [可运行的 CRUD](/back-end/frontend-backend-guide/07-build-a-crud-api)。本篇对应仓库的 `examples/backend-task-api`，也就是单体 `task-api:8080` 的**初版行为**，不是下一阶段的 Session 变体。

## 1. 创建成功以后，页面要拿到一条真实任务

用户输入“学习 SQL”，页面向 `POST /api/tasks` 提交 JSON：`{"title":"学习 SQL"}`。后端生成 UUID，创建时间也由服务端确定，状态默认 TODO。成功返回 201，正文是新任务，Location 指向 `/api/tasks/{id}`。

页面保存这个真实 ID，之后读取、更新和删除都用它。文档里的 `{id}` 只是占位符，不能原样发送，也不能随手猜 1、2。完整启动步骤见 [CRUD 实验](/back-end/frontend-backend-guide/07-build-a-crud-api)。

输入规则也要明确。标题必须是非空白字符串，长度不超过 100 个 UTF-16 单元，业务层会去掉两端空白。数字或布尔值不会被自动转成标题字符串；全是空白的内容，即使经过 strip 后才变空，也不能写入数据库。

页面把响应当作下面的类型使用，成功时直接返回资源，不额外包一层 `{code:0,data:...}`：

```ts
type Task = {
  id: string
  title: string
  status: 'TODO' | 'DOING' | 'DONE'
  createdAt: string // 带时区的 ISO 8601 时间点
}
type TaskPage = { items: Task[]; total: number; page: number; size: number }
type ApiError = { code: string; message: string; requestId: string }
```

`createdAt` 对应 Java 的 Instant，例如 `2026-01-15T08:30:00Z`，表示一个时间点，不承诺固定小数位数。页面按用户时区显示，不能去掉 Z 后当作本地时间。将来若有“截止日期”这种日历日期，应另外约定 `YYYY-MM-DD`，不用无时区日期冒充 UTC 时间点。

TypeScript 类型帮助开发，但网络响应不会因为写了类型就自动通过验证。关键响应可以增加运行时 schema 检查；OpenAPI 和生成类型也要跟实现、测试同步。

## 2. 列表没有任务时，也要返回同一种结构

页面首次查询 `GET /api/tasks?page=0&size=20`。这里 page 从 0 开始，默认 0；size 默认 20，允许 1～100；教学版 page 上限为 10000。非法参数返回 400，不悄悄改成另一个数。

```json
{"items":[],"total":0,"page":0,"size":20}
```

即使空页，也保留 items、total、page、size，items 用空数组而不是 null。如果翻到了最后一页之后，items 可以为空，但 total 仍可能大于 0。界面页码器若从 1 开始，只在适配层减 1：页面第 3 页、每页 10 条，对应 `GET /api/tasks?page=2&size=10`。

数据库按 `created_at DESC, id DESC` 排序，偏移量是 `page * size`。第二个排序键让相同创建时间的任务也有确定顺序，UUID 本身并不代表创建先后。

不过列表和 total 是两次查询，期间可能有并发写入；翻页时也可能新增或删除任务。当前接口没有承诺整个过程来自同一快照，稳定排序只能消除同一份数据的并列不确定性。

## 3. 更新时，哪些字段可以改，哪些必须一起交

用户修改标题并标记完成，当前请求是 `PUT /api/tasks/{id}`，正文例如 `{"title":"学习 SQL","status":"DONE"}`。它要求提供全部可编辑字段，也就是 title 和 status；id、createdAt 由服务端管理。

状态使用 TODO、DOING、DONE 三个字符串，非法字符串和数字枚举都被拒绝。只传一个字段、其余自动保留属于另一种更新约定，不能把当前 PUT 当作已经支持 PATCH。

把实际已有端点放在一起，便于开发时查阅：

| 方法与路径 | 输入 | 成功响应 |
| --- | --- | --- |
| `POST /api/tasks` | JSON：title | 201、新任务、Location |
| `GET /api/tasks/{id}` | 真实任务 ID | 200、任务；不存在为 404 |
| `GET /api/tasks?page=0&size=20` | 分页参数 | 200、`{items,total,page,size}` |
| `PUT /api/tasks/{id}` | JSON：title、status | 200、更新后的任务；不存在为 404 |
| `DELETE /api/tasks/{id}` | 任务 ID | 204、无正文；重复删除仍为 204 |

这套初版还没有 PATCH、用户隔离、版本检查、请求幂等键或任意条件筛选。之后的 Session 实验会改变归属检查等行为，不能把规划或另一工程的能力直接写进初版请求。

字段缺失、null、空字符串也要逐一约定。将来增加部分更新时，尤其要区分“没有修改这个字段”与“把它清空”，不能都当成 null 随意处理。

## 4. 保存失败时，先用 HTTP 状态分大类，再看错误码

若更新的任务已经不存在，后端返回：

```http
HTTP/1.1 404
Content-Type: application/json
X-Request-Id: 这次请求的关联ID

{"code":"TASK_NOT_FOUND","message":"任务不存在","requestId":"这次请求的关联ID"}
```

HTTP 状态告诉客户端这次属于哪类结果，code 则给出稳定的程序判断依据。message 是给人看的说明，可以改文案，前端不要用中文字符串判断分支。requestId 用来把这次请求和后台日志对上。

当前错误如下：

| 出了什么问题 | 状态 | code |
| --- | --- | --- |
| 标题不符合约束 | 400 | `INVALID_ARGUMENT` |
| 分页越界 | 400 | `INVALID_PAGE` |
| JSON 格式或字段类型无法绑定 | 400 | `HTTP_400` |
| GET、PUT 找不到任务 | 404 | `TASK_NOT_FOUND` |
| 方法不支持、媒体类型不支持 | 405、415 | `HTTP_405`、`HTTP_415` |
| 未预期的内部异常 | 500 | `INTERNAL_ERROR` |

路径根本不存在时，可能是 `HTTP_404`，与任务接口找不到资源有区别。405 的 Allow 等协议头也要保留，不能用全局 catch 把这些错误全改成 500。

业务拒绝同样使用恰当的失败状态，不装成 `200 + 非零code`。对外不返回堆栈、SQL 或凭据，用户提供 requestId 后，在授权范围内查 [日志](/back-end/frontend-backend-guide/26-reading-logs)。反向代理也可能返回 HTML 错误页，客户端对非 JSON 失败要能兜底。

认证后的 401、403、隐藏存在性的 404，以及版本冲突 409，等实现相应功能再制定并测试。初版 7 个测试没有因此自动覆盖登录和越权。

## 5. 没收到响应，不代表后端没保存

用户点创建后超时了，页面能不能自动再发一次？当前 POST 每次都会创建新任务，没有幂等键，答案是不能承诺安全重试。客户端取消、超时或收到 504 时，数据库可能已经提交，具体时序见 [代理与超时](/back-end/frontend-backend-guide/22b-proxy-and-discovery)。

如果产品要支持创建重试，客户端为一次真实操作生成一个 `Idempotency-Key`，同一操作重试时复用。服务器以已验证用户、操作范围和键确定唯一记录，同时保存请求摘要；相同键却换内容，应拒绝冲突，例如 409。

创建任务和记录结果在同一 MySQL 事务内提交，由唯一约束处理并发。重试才能读取原来的任务 ID 和结果，而不是重复新建。还需要说明处理中请求怎么回应、失败后怎么恢复、结果保留多久，以及过期后的重试行为。

只用 Redis SET NX 占一个标记不够：进程可能在标记后、业务提交前崩溃；标记说处理过，却没有任务或结果。跨系统副作用还要另做一致性设计，不是增加一个请求头就自动完成。

PUT 设置相同字段、DELETE 删除同一资源，应具有重复操作后状态效果相同的幂等语义；状态码和文本不必次次相同。GET 应只读，也不能在 PUT 里隐藏“每调用一次向前推进一步”。

但是，两个不同用户同时 PUT，仍可能最后写入覆盖前者。这个问题与重复创建不同，需要版本字段或 ETag、If-Match 等条件更新，明确冲突状态。当前实现没有这项检查，后续再补，不能因为 PUT 幂等就声称不会覆盖。

## 6. 已经有调用方，再改字段就需要考虑兼容

如果只改后端，把 status 从字符串换成 0、1、2，旧页面会读不懂；已有数字含义也可能漂移。字段类型变化应该安排迁移或版本，而不是通知前端“多判断一下”。

当前地址就是 `/api/tasks`，没有 `/v1`。以后确有不兼容变更，可以选择 `/api/v2/tasks` 等策略并给旧客户端迁移期，讲解版本不需要先改现有实验地址。

不仅删字段会破坏兼容，新增枚举或错误码也可能让旧客户端的穷举处理失败。新增响应字段经常可兼容，但仍要检查严格解析客户端、默认值和权限。发布前用实际调用方与自动测试验证，再安排通知、废弃、回滚和下线，版本号本身不会完成这些工作。

## 按需补充

### 数据更多以后，分页怎样演进

深 OFFSET 通常要扫描并跳过更多记录。数据主要顺序加载时，可按 `(created_at,id)` 设计游标，使用匹配的范围条件和索引。游标要绑定筛选、排序和权限范围，必要时防篡改，而不是把一个数字 base64 编码。

它也不自动提供一致快照、固定耗时或任意跳页。具体查询成本会在 [SQL 与索引](/back-end/frontend-backend-guide/10-sql-and-indexes) 中继续解释，当前接口不要发送尚未支持的 cursor 或筛选参数。

### 换数据库或 ID 方案时

主线 ID 是 UUID 字符串。其他系统若用 Long、BIGINT，需考虑 JavaScript 的 `2^53-1` 安全整数限制；解析错了再转字符串无法找回精度。

从 H2 迁移 MySQL，外部契约尽量保持稳定，内部仍需验证约束、查询、时间精度、时区和事务。后续可以在 [测试](/back-end/frontend-backend-guide/35-testing) 里把这些行为变成回归检查，避免数据库换了、页面收到的时间或错误也悄悄变了。

回看 [CRUD 实验](/back-end/frontend-backend-guide/07-build-a-crud-api) 可核对初版能力。

主线下一篇：[C1 · 数据建模、SQL 与索引](./10-sql-and-indexes)。接口已经约定只取一页任务，接下来看看数据库怎样存、怎样查这一页。

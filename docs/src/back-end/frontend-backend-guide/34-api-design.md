# API 设计

> 前端的你，吐槽过多少难用的后端接口？删除用户用 `POST /deleteUserById`、成功失败都返 `200` 让你去翻 body、分页参数这个接口叫 `pageNo` 那个叫 `current`、时间一会儿是 `1717209600` 一会儿是 `"2024-06-01 12:00:00"`、`message` 永远是 `"操作失败"`……
> **这一章把你受过的所有气，翻译成一套后端能照着落地的接口设计规范。** 你将从"接口的甲方"变成"接口的乙方"——而正因为你当过甲方，你比大多数后端更知道什么叫好用。

上一章 [消息队列可靠性](/back-end/frontend-backend-guide/33-mq-reliability) 解决的是"异步任务不丢不重"，那是服务内部的可靠性。这一章往回退一步，看**服务对外的那张脸**：API 长什么样、错误怎么报、字段怎么命名。设计 API 不是后端单方面的事——它是前后端之间的契约，而你恰好两头都懂。

## 先列一张"前端最讨厌什么"清单

设计目标很简单：别让对接你的前端骂街。把雷区先摆出来，本章后面逐个拆。

| 前端踩过的坑 | 坏在哪 | 本章对应小节 |
| --- | --- | --- |
| `POST /getUserList`、`POST /deleteById` | 动词进路径、方法语义全错 | RESTful 资源与方法 |
| 任何情况都返 HTTP 200，错误藏在 body | 没法用 `axios` 拦截器统一处理 | 统一响应与错误码 |
| 这个接口 `pageNo`/`pageSize`，那个 `current`/`limit` | 分页约定不统一，每个接口都要看文档 | 分页约定 |
| 网络抖动重试，结果配额扣了两次 | POST 不幂等，没有幂等键 | 幂等设计 |
| 后端加了个必填字段，前端线上直接报错 | 破坏性变更，没有版本管理 | 版本管理 |
| `createTime` 有时是时间戳有时是字符串 | 字段格式不统一，类型不可预测 | 给前端友好 |

> 前端类比：你写 TypeScript 时最怕什么？怕 `any`、怕一个字段忽而 `string` 忽而 `number`、怕函数行为靠猜。好 API 的本质，就是给前端一份**类型稳定、行为可预测的契约**——和你用 `zod` 给运行时数据上类型是同一种安全感。

## RESTful：用资源和方法，而不是用动词

REST 的核心就一句话：**把后端能力抽象成"资源"（名词），用 HTTP 方法（动词）去操作它。** 不要把动作塞进路径。

### 资源命名：名词、复数、层级清晰

```text
坏（动词进路径，每个操作发明一个 URL）：
  POST /getUserById
  POST /createUser
  POST /updateUserName
  POST /deleteUser

好（资源 + 方法，一个资源对应一组操作）：
  GET    /v1/users          查列表
  GET    /v1/users/{id}     查单个
  POST   /v1/users          新建
  PUT    /v1/users/{id}     全量替换
  PATCH  /v1/users/{id}     部分修改
  DELETE /v1/users/{id}     删除
```

约定：

- 资源用**名词复数**：`/v1/users`、`/v1/canvas-tasks`、`/v1/ai-images`。
- 层级表达从属关系：某用户的所有生图任务 → `GET /v1/users/{userId}/tasks`。
- 路径里**不出现动词**。需要"动作"语义时（比如取消任务），用子资源或明确的动作路径：`POST /v1/canvas-tasks/{id}/cancel`，这是 REST 允许的"控制器"折中，比 `POST /cancelTask` 好得多。

> 前端类比：就像 Vue Router / Next.js 的文件路由——`/users/[id]` 是一个资源页面，你不会建一个 `/getUserPage` 路由。URL 描述"是什么"，HTTP 方法描述"干什么"。

### HTTP 方法语义：每个方法有它的"性格"

| 方法 | 语义 | 幂等 | 安全(只读) | svc-canvas 里的例子 |
| --- | --- | --- | --- | --- |
| GET | 查询，不改数据 | 是 | 是 | `GET /v1/canvas-tasks/{id}` 轮询任务状态 |
| POST | 新建 / 触发动作 | 否 | 否 | `POST /v1/canvas-tasks` 提交一个生图任务 |
| PUT | 全量替换整个资源 | 是 | 否 | `PUT /v1/users/{id}/profile` 整体更新资料 |
| PATCH | 部分修改某些字段 | 否(取决于实现) | 否 | `PATCH /v1/users/{id}` 只改昵称 |
| DELETE | 删除资源 | 是 | 否 | `DELETE /v1/canvas-tasks/{id}` 删一个任务 |

"幂等"（idempotent）= 同一个请求发一次和发 N 次，**对服务器状态的影响相同**。这个概念后面幂等设计一节会重点讲，先记住这张表里哪些天生幂等。

### 状态码：用对了，前端能少写一半判断逻辑

HTTP 状态码不是装饰，它是**第一层、最粗粒度的结果分类**。前端的 `axios` 默认就是按状态码分流的（2xx 进 `.then`，4xx/5xx 进 `.catch`）。状态码的细节在 [后端视角网络基础](/back-end/frontend-backend-guide/22-networking-for-backend) 已经从排错角度讲过，这里只列接口设计该怎么选：

```text
2xx 成功
  200 OK              GET/PUT/PATCH 成功，且有返回体
  201 Created         POST 新建成功（响应可带新资源的 Location/id）
  204 No Content      DELETE 成功，没有返回体

4xx 客户端错误（前端的锅，改请求才有用）
  400 Bad Request     参数格式错、校验不通过
  401 Unauthorized    没登录 / token 失效  → 前端跳登录页
  403 Forbidden       登录了但没权限       → 前端提示无权限
  404 Not Found       资源不存在
  409 Conflict        状态冲突（如重复提交、版本冲突）
  429 Too Many Requests  被限流了           → 前端退避重试

5xx 服务端错误（后端的锅，前端重试或上报）
  500 Internal Server Error  后端炸了
  502 Bad Gateway / 504 Gateway Timeout  网关层问题
```

> 前端类比：401 和 403 的区别，对应你 `axios` 拦截器里那段经典逻辑——`if (status === 401) router.push('/login')`（你没登录）`else if (status === 403) toast('无权限')`（你登了但不让你看）。后端把这俩分清楚，前端的拦截器才写得干净。

### RESTful vs RPC，一句话

REST 面向"资源"（`GET /v1/users/1`），RPC 面向"方法调用"（`POST /UserService/getById {id:1}`，像调一个远程函数）。**对外开放、给前端/第三方用的 HTTP 接口，优先 REST**；服务内部高性能调用（如 gRPC、本项目 `cpt-api` 里的 Feign）才更接近 RPC 风格——Feign 把远程调用伪装成本地方法，本质就是 RPC。

## 统一响应与错误码：让前端只写一个拦截器

光有 HTTP 状态码不够。一个 `400` 可能是"参数缺失"，也可能是"配额不足"，前端需要更细的**业务码**来精确处理。本项目用统一响应类型 `RtData` 来承载这一层。

### RtData 规范

```java
// cpt-common 里的统一响应
public class RtData<T> {
    private int code;       // 业务码：0 表示成功，非 0 表示具体业务错误
    private T data;         // 业务数据，失败时为 null
    private String message; // 给人看的提示，成功为 "ok"，失败为可读错误信息

    public static <T> RtData<T> ok(T data) {
        RtData<T> r = new RtData<>();
        r.code = 0;
        r.data = data;
        r.message = "ok";
        return r;
    }

    public static <T> RtData<T> fail(int code, String message) {
        RtData<T> r = new RtData<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
```

所有接口，无论成功失败，**body 永远是这个结构**，前端拿到的形状永远可预测：

```json
// 成功
{ "code": 0, "data": { "taskId": "tk_7f3a", "status": "PENDING" }, "message": "ok" }

// 失败
{ "code": 41002, "data": null, "message": "配额不足，请充值后再试" }
```

> 前端类比：这就是给后端响应定义一个稳定的 `interface ApiResponse<T> { code: number; data: T; message: string }`。形状固定，你才能放心地 `const { code, data } = res.data` 而不用每个接口都防御性地判断结构。

### 业务错误码：分段设计，看码就知道是哪个服务

`code` 别乱给。用分段编码，让人一眼定位是哪个服务、哪类错误：

```text
错误码分段（示例约定，5 位数）：
  0          成功
  10xxx      svc-gateway / 通用：10401 未登录，10403 无权限，10429 限流
  20xxx      svc-auth：    20001 验证码错误，20002 token 过期
  40xxx      svc-user：    41002 配额不足，41003 余额不足，41005 用户已封禁
  50xxx      svc-ai：      50001 模型不可用，50002 提示词违规
  60xxx      svc-canvas：  60001 任务不存在，60003 任务已取消，60010 任务状态非法
```

新增一个错误码，就在对应服务的常量类里加一条，配上**给用户看的话术**和**给开发看的说明**，并同步到接口文档。

### HTTP 状态码 vs 业务 code：两层，各管各的

这是最容易混的点，记住这个分工：

```text
┌──────────────┬────────────────────────────┬──────────────────────────┐
│ 层级         │ 谁来分流                    │ 管什么                    │
├──────────────┼────────────────────────────┼──────────────────────────┤
│ HTTP 状态码  │ 框架 / 网关 / axios 拦截器  │ 协议层结果：成不成功、是 │
│              │ 自动按 2xx/4xx/5xx 分流     │ 谁的锅、要不要跳登录/重试│
├──────────────┼────────────────────────────┼──────────────────────────┤
│ 业务 code    │ 你的业务代码 .then 里手动判 │ 具体业务语义：到底哪个    │
│              │                            │ 业务规则没过、给什么提示  │
└──────────────┴────────────────────────────┴──────────────────────────┘
```

实践中两种流派，本项目推荐**混合用**：

- 协议/通用类错误（未登录、限流、服务器炸了）→ 用 **HTTP 状态码**（401/429/500），前端拦截器统一处理。
- 业务规则类错误（配额不足、提示词违规）→ HTTP 仍返 **200**，靠 `RtData.code` 区分。因为这类"错误"是正常业务流的一部分，前端往往要展示具体文案、引导用户操作，而不是当成异常吞掉。

> 前端类比：HTTP 状态码 = `axios` 的 `.then`/`.catch` 自动分流；业务 code = 进了 `.then` 之后你写的 `if (res.data.code !== 0)`。一个是协议层的反射，一个是业务层的判断。

前端只需写一个响应拦截器，就能吃下整套规范：

```typescript
// 前端：一个拦截器搞定所有接口
api.interceptors.response.use(
  (res) => {
    const { code, data, message } = res.data;
    if (code === 0) return data;                  // 成功，直接把 data 给业务
    if (code === 41002) router.push('/recharge'); // 配额不足，引导充值
    ElMessage.error(message);                     // 其余业务错误，统一弹 message
    return Promise.reject(new BizError(code, message));
  },
  (err) => {
    const status = err.response?.status;
    if (status === 401) router.push('/login');    // 未登录
    else if (status === 429) ElMessage.warning('操作太频繁，稍后再试');
    else ElMessage.error('服务异常，请稍后重试');
    return Promise.reject(err);
  }
);
```

正因为后端把规范定死了，这个拦截器才能写出来——这就是"统一响应"的价值：**约定一次，全站受益**。

## 分页约定：page/size 还是游标

列表接口必须分页，否则 `svc-canvas` 一个用户上万条历史任务能把响应撑爆、把前端列表卡死。两种分页方式，按场景选。

### 偏移分页（page/size）：适合大多数管理后台

```text
请求：GET /v1/users/{id}/tasks?page=1&size=20
响应 data：
{
  "list": [ ... 20 条 ... ],
  "page": 1,
  "size": 20,
  "total": 1287       ← 关键：返回总数，前端才能算总页数、画页码
}
```

约定参数名全站统一（本项目用 `page` + `size`，`page` 从 1 开始），别一个接口 `pageNo` 一个 `current`。SQL 层就是 `LIMIT size OFFSET (page-1)*size`，索引细节见 [SQL 与索引](/back-end/frontend-backend-guide/10-sql-and-indexes)。

缺点：`page` 翻到很深时（`OFFSET 1000000`）数据库要扫掉前面所有行，越翻越慢；而且翻页过程中有新数据插入会导致"漏数据/重复"。

### 游标分页（cursor）：适合大数据量、信息流

```text
请求：GET /v1/ai-images?cursor=eyJpZCI6ImltZ185OTAifQ&size=20
响应 data：
{
  "list": [ ... 20 条 ... ],
  "nextCursor": "eyJpZCI6ImltZ185NzAifQ",  ← 下一页的游标，null 表示没有更多
  "hasMore": true
}
```

游标本质是"上一页最后一条的位置标记"（比如最后一条的 `id` 或 `createTime`，编码成不透明字符串）。下一页直接 `WHERE id < lastId LIMIT size`，**不管翻多深都是走索引的范围查询，恒定快**，也不会因为中途插数据而错乱。代价是不能跳页、通常不返 `total`。

> 前端类比：偏移分页 = Element/Antd 表格底部那种"1 2 3 ... 64 页"的页码器；游标分页 = 微博/抖音那种"上拉加载更多"的无限滚动。后者你只需要一个 `nextCursor`，根本不关心总共多少页。

选型一句话：**能跳页的后台列表用 page/size；信息流、海量数据、要无限滚动的用 cursor。**

## 幂等设计：让前端敢于重试

幂等 = **同一请求执行一次和执行多次，结果一样**。这是分布式系统里防"重复提交/网络重试导致数据错乱"的核心武器，和 [消息队列可靠性](/back-end/frontend-backend-guide/33-mq-reliability) 里讲的消费幂等是同一思想，只是这里发生在 HTTP 入口。

### 哪些方法天然幂等

```text
GET    天然幂等：查 100 次还是查，不改数据
PUT    天然幂等：把 name 设成 "Tom"，设 1 次和设 100 次结果都是 "Tom"
DELETE 天然幂等：删 1 次和删 100 次，结果都是"它不在了"（第 2 次返 404 或当成功）
POST   不幂等：提交 1 次生图任务和提交 3 次，会创建 3 个任务、扣 3 次配额  ⚠
```

### POST 用幂等键防重复提交

问题场景：用户在 `svc-canvas` 点"生成"，网络慢，他又点了两下；或者前端超时自动重试。结果：3 个任务、扣 3 次配额、用户暴怒。

解法：前端为**每一次用户操作**生成一个唯一的幂等键（`Idempotency-Key`），重试时复用同一个键；后端用 Redis 记录"这个键处理过没"。

```text
症状：压测发现"提交生图任务"接口在客户端重试下会重复创建任务、重复扣配额。
目标：同一 Idempotency-Key 无论到几次，只创建一个任务、只扣一次配额。
```

后端实现（`svc-canvas`，借助 [Redis 实战](/back-end/frontend-backend-guide/12-redis-in-practice) 里的 `setIfAbsent`）：

```java
@PostMapping("/v1/canvas-tasks")
public RtData<TaskVO> submit(@RequestHeader("Idempotency-Key") String key,
                             @RequestBody @Valid SubmitTaskReq req) {
    // SET key value NX EX 600：键不存在才写入，10 分钟过期
    Boolean first = redis.opsForValue()
            .setIfAbsent("idem:task:" + key, "1", Duration.ofMinutes(10));
    if (Boolean.FALSE.equals(first)) {
        // 已处理过：返回上次的结果，而不是再创建一遍
        TaskVO cached = taskCache.getByIdemKey(key);
        return RtData.ok(cached);
    }
    TaskVO vo = canvasService.submit(req);   // 真正创建任务、扣配额
    taskCache.saveIdemResult(key, vo);
    return RtData.ok(vo);
}
```

前端配合：

```typescript
// 每次用户点"生成"时生成一个键，这一轮的重试都复用它
const idemKey = crypto.randomUUID();
await api.post('/v1/canvas-tasks', payload, {
  headers: { 'Idempotency-Key': idemKey },
});
```

> 前端类比：你写过的"防重复点击"——点击后立刻 `disabled` 按钮、或加 loading 锁。幂等键是把这个保险做到了**后端**：哪怕前端的锁失效、哪怕网络层自动重试、哪怕用户疯狂点，后端这关也只认第一次。前端的锁是体验优化，后端的幂等键才是数据正确性的最后防线。

## 版本管理：让接口能演进而不背刺前端

接口上线后就有人在用。后端"优化"一下字段名、把可选改必填，前端线上当场白屏——这种事不能发生。规则是：**接口一旦发布，就是契约，只能向后兼容地演进。**

### 路径版本：把版本放进 URL

本项目用路径版本 `/v1/...`，简单直观、好路由。需要做不兼容的大改动时，开 `/v2`，让 `/v1` 和 `/v2` 并存一段时间，给前端迁移窗口。

```text
/v1/users/{id}   老版本，继续维护
/v2/users/{id}   新结构，前端逐步迁移
```

### 兼容性铁律：只增，不改不删

```text
✅ 安全的变更（向后兼容，不用升版本）：
   - 新增一个【可选】请求字段
   - 响应里新增一个字段（老前端忽略它即可）
   - 新增一个错误码

❌ 破坏性变更（必须升 /v2 或走废弃流程）：
   - 删除 / 重命名已有字段
   - 改字段类型（string → number）
   - 把可选请求参数改成必填
   - 改变已有字段的含义或枚举值
```

> 前端类比：这跟你维护一个 npm 包的 `semver` 一模一样。加个可选 props、加个可选参数 → minor，没人会坏；删 props、改函数签名 → major，必须发大版本号并写迁移指南。API 的 `/v1`→`/v2` 就是后端世界的 major bump。

### 废弃流程：别直接拔网线

要下线一个老接口/老字段，文明做法是：

1. 文档标记 `@Deprecated`，写明替代方案和下线时间。
2. 响应头加 `Deprecation: true` 或 `Sunset: <日期>`，可观测层（见 [可观测性](/back-end/frontend-backend-guide/30-observability)）监控它的调用量。
3. 等调用量降到接近 0、并通知所有调用方后，才真正删除。

## 给前端友好：那些不写进规范、但前端天天念叨的细节

接口"能用"和"好用"之间，差的全是这些细节。

### 字段命名一致

全站一种命名风格（本项目 JSON 用 `camelCase`：`userId`、`createTime`），别这个接口 `user_id` 那个 `userId`。同一个概念全站同名：用户 ID 永远叫 `userId`，不要时而 `uid` 时而 `userId` 时而 `id`。

### 时间统一

```text
推荐二选一，全站统一：
  毫秒时间戳：  "createTime": 1717209600000        （new Date(ts) 直接用，无时区歧义）
  ISO 8601：    "createTime": "2024-06-01T04:00:00Z" （带 Z 或 +08:00，明确时区）

禁止：
  "2024-06-01 12:00:00"   ← 没时区，前端不知道这是哪个时区的 12 点
```

> 前端类比：你被 `new Date("2024-06-01 12:00:00")` 在不同浏览器解析出不同结果坑过吧？后端给毫秒时间戳或带时区的 ISO 8601，前端 `dayjs(ts)` 才稳。

### null vs 缺省，约定清楚

- 字段值未知/为空 → 给 `null`，不要给 `""` 或 `0` 充当空（`0` 可能是合法值，前端没法区分）。
- 列表为空 → 返 `[]`，**绝对不要返 `null`**。否则前端 `list.map()` 直接报 `Cannot read properties of null`——这是前端对接后端最高频的崩溃之一。

### 错误信息可读

`message` 是给人看的。`"操作失败"`、`"error"`、`"系统异常 NullPointerException"` 都是甩锅。好的 message 告诉用户**发生了什么、该怎么办**：`"配额不足，剩余 0 张，请充值后再试"`。技术细节（堆栈、SQL 错误）写进 [日志](/back-end/frontend-backend-guide/26-reading-logs)，绝不返给前端（也是 [安全](/back-end/frontend-backend-guide/32-security) 的要求）。

### 写好接口文档

后端在 Controller 上加 Swagger/OpenAPI 注解（`springdoc-openapi`），自动生成可交互的接口文档页，前端能直接看字段、在线试调，甚至用工具生成 TypeScript 类型——省掉无数次"这个字段啥意思"的来回。一句话：**接口文档不是额外工作，是接口的一部分。**

## 好接口 vs 坏接口：同一个需求的对照

需求：分页查询某用户的生图任务列表。

```text
❌ 坏接口
  POST /getTaskList                         （动词进路径 + 该用 GET 却用 POST）
  body: { "uid": 123, "pageNo": 1, "ps": 20 }   （命名随意、缩写难懂）
  返回 HTTP 200，body：
  {
    "success": true,                         （又有 success 又要看 data，前端两头判断）
    "result": null,                          （空列表返 null，前端 .map() 直接崩）
    "msg": "查询成功",
    "time": "2024-6-1 12:0:0"                （时间无时区、格式不规范）
  }
  出错时也返 200 + { "success": false, "msg": "失败" }   （拿不到错因，无法处理）
```

```text
✅ 好接口
  GET /v1/users/123/tasks?page=1&size=20    （RESTful、方法语义正确、命名清晰）
  返回 HTTP 200，body：
  {
    "code": 0,
    "data": {
      "list": [
        { "taskId": "tk_7f3a", "status": "DONE",
          "createTime": 1717209600000, "thumbUrl": "https://..." }
      ],
      "page": 1, "size": 20, "total": 1287
    },
    "message": "ok"
  }
  出错时：HTTP 200 + { "code": 60001, "data": null, "message": "任务不存在" }
  未登录：HTTP 401（前端拦截器自动跳登录）
```

逐条对照能看出：好接口让前端**一个拦截器吃下所有响应、一行 `.map()` 不怕崩、一眼看懂每个字段、出错知道为什么和怎么办**。这就是"前端懂后端"能带来的真正价值——你设计的接口，前端用着会想说声谢谢。

## 小结

- **RESTful**：URL 是名词复数资源（`/v1/users/{id}`），HTTP 方法是动词（GET 查/POST 增/PUT 全量改/PATCH 部分改/DELETE 删），状态码作第一层结果分类；对外用 REST，服务内部高性能调用才用 RPC（Feign）。
- **统一响应 RtData**：`{code, data, message}` 形状固定，业务码分段设计；HTTP 状态码管协议层（前端拦截器自动分流），业务 `code` 管业务语义（`.then` 里手动判），混合使用。
- **分页**：全站统一参数名，要跳页的后台用 `page/size` 并返 `total`，海量数据/信息流用 `cursor` 游标分页。
- **幂等**：GET/PUT/DELETE 天然幂等；POST 用 `Idempotency-Key` + Redis `setIfAbsent` 防重复提交，是数据正确性的最后防线。
- **演进**：路径版本 `/v1`，只增不改不删字段，破坏性变更升 `/v2` 并走废弃流程；时间用时间戳或 ISO 8601、空列表返 `[]`、message 写人话、用 Swagger/OpenAPI 生成文档。

### 自测

1. 前端发现"取消任务"接口在网络重试时偶尔会取消两次（虽然第二次没意义），但"提交任务"重试会创建两个任务。为什么 DELETE 不用特殊处理而 POST 必须加幂等键？
2. 一个"配额不足"的错误，你会用 HTTP 4xx 还是 HTTP 200 + 业务 code？说出你的判断依据，以及前端拦截器分别会怎么处理。
3. `svc-user` 想把响应里的 `nickName` 字段改名成 `displayName`，能直接改吗？如果不能，正确的演进路径是什么？

### 下一章

接口设计好了，怎么保证它真的按契约工作、改动不回退？下一章 [测试](/back-end/frontend-backend-guide/35-testing) 教你给后端接口写单元测试和集成测试。

---
prev:
  text: "B3. 三层分工与工程阅读"
  link: "/back-end/frontend-backend-guide/04-three-layer-and-structure"
next:
  text: "B5. 接口契约与 API 设计"
  link: "/back-end/frontend-backend-guide/34-api-design"
---

# 把任务接口跑起来，完成一次增删改查

前面看过 Java 写法、Spring 的对象管理，也顺着代码找到了三层分工。这次实际启动配套工程，用请求创建一条任务，再把它查询、修改和删除。

这个过程通常简称 **CRUD**，分别是创建、读取、更新和删除。

## 1. 先启动后端

完整源码在仓库根目录的 `examples/backend-task-api`，也可以 [下载源码 ZIP](/backend-guide/task-api.zip)，解压后进入 `backend-task-api` 目录。

需要 JDK 17、Maven 3.6.3+。已经下载文档仓库时，从仓库根目录执行：

```bash
cd examples/backend-task-api
java -version
mvn -version
mvn test
mvn spring-boot:run
```

Maven 第一次需要联网下载依赖。测试通过后再启动，看到应用启动完成、Tomcat 监听 8080，就可以保留这个终端，另开一个终端发请求。练习结束时按 Ctrl+C 停止。

这份实验只监听本机 `127.0.0.1`，使用 H2 内存数据库，重启后任务会消失。**它还没有登录和用户隔离，只用于本机练习，不要改成公网监听后直接上线。** 后面的课程再补这些能力。

如果 8080 已被占用，可以用下面的命令启动在 8081，并把本篇后续 URL 一起改成 8081。不要停止不认识的进程。

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

## 2. 创建一条任务

在另一个终端执行：

```bash
curl -i http://127.0.0.1:8080/api/tasks \
  -H 'Content-Type: application/json' \
  -d '{"title":"学会 SQL"}'
```

`-d` 提供请求内容，这里 curl 会发送 POST。`Content-Type` 告诉后端，正文是 JSON；`-i` 让终端同时显示响应头，方便看状态码。

正常情况下，你会看到 `201`。响应头里有新任务的位置和本次请求 ID：

```http
HTTP/1.1 201
Location: /api/tasks/生成的UUID
X-Request-Id: 本次请求的UUID
Content-Type: application/json
```

正文类似这样，ID 和时间每次都会不同：

```json
{
  "id": "生成的UUID",
  "title": "学会 SQL",
  "status": "TODO",
  "createdAt": "2026-10-31T08:00:00Z"
}
```

你只提交了标题，后端补上 ID、TODO 初始状态和创建时间。时间使用 UTC，ID 是字符串，前端不需要把它转换成 Number。

## 3. 用这个 ID 查回任务

把实际响应里的 ID 放进变量，不要把中文占位文字原样执行：

```bash
TASK_ID='替换成刚返回的真实id'
curl -i "http://127.0.0.1:8080/api/tasks/${TASK_ID}"
```

返回 `200` 和同一条任务，说明数据已经可以通过另一次请求取回来。再请求列表：

```bash
curl -i 'http://127.0.0.1:8080/api/tasks?page=0&size=10'
```

列表返回 `{items,total,page,size}`：items 是这一页的任务，total 是总条数，page 是页码，size 是每页大小。如果只创建过这条任务，total 应该是 1。

页码从 0 开始；本例允许 size 为 1 到 100、page 为 0 到 10000。先知道这些约定，下一篇再解释为什么列表需要限制查询范围。

## 4. 把任务改为完成，再删除

更新时，提交可编辑的标题和状态：

```bash
curl -i -X PUT "http://127.0.0.1:8080/api/tasks/${TASK_ID}" \
  -H 'Content-Type: application/json' \
  -d '{"title":"学会 SQL","status":"DONE"}'
```

返回 `200`，状态应当变成 DONE。本例的状态有 TODO、DOING、DONE，更新时标题和状态都要提供。

接着删除，再尝试查询：

```bash
curl -i -X DELETE "http://127.0.0.1:8080/api/tasks/${TASK_ID}"
curl -i -X DELETE "http://127.0.0.1:8080/api/tasks/${TASK_ID}"
curl -i "http://127.0.0.1:8080/api/tasks/${TASK_ID}"
```

依次应该得到 `204 → 204 → 404`。这版接口重复删除仍返回 204，查询已不存在的任务则返回 404。204 没有正文，前端处理它时不要再调用 `response.json()`。

## 5. 再试一次不合法的输入

正常请求已经跑通，现在把标题改成只有空格：

```bash
curl -i http://127.0.0.1:8080/api/tasks \
  -H 'Content-Type: application/json' -d '{"title":"   "}'
```

这次应返回 `400`，并且不新增任务。错误正文包含 code、message、requestId，便于前端展示原因、后端查对应日志。

其他常见错误也可以一起观察：

```bash
# 每页大小超出约定：400
curl -i 'http://127.0.0.1:8080/api/tasks?size=101'
# 接口没有提供 PATCH：405，响应带 Allow 头
curl -i -X PATCH http://127.0.0.1:8080/api/tasks/missing
# 创建接口不接受 text/plain：415
curl -i http://127.0.0.1:8080/api/tasks \
  -H 'Content-Type: text/plain' --data 'not json'
```

这些状态分别告诉你输入、方法或格式哪里不符合约定。统一错误格式时，也要保留这些区别，不应把所有错误都变成 500 或 200。

## 6. 回到源码，看看新增究竟做了什么

现在再看代码，就能把刚才的现象和处理步骤对应起来：

![请求先交给 Controller，再由 Service 生成任务，Repository 执行 SQL，最后返回 JSON](/backend-guide/core-layer-map.svg)

Controller 调用 Service 后，用下面的写法返回 `201`、Location 和任务正文：

```java
@PostMapping
ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
    var task = service.create(request);
    return ResponseEntity.created(URI.create("/api/tasks/" + task.id())).body(task);
}
```

Service 处理标题，生成 ID、状态和时间，再让 Repository 保存。Repository 中的 SQL 使用占位符：

```java
jdbc.update("INSERT INTO tasks(id,title,status,created_at) VALUES (?,?,?,?)",
    task.id(), task.title(), task.status().name(), Timestamp.from(task.createdAt()));
```

标题作为参数传进去，而不是拼成 SQL 语句的一部分。这就是参数化查询，后面的数据库课程会接着使用它。

这里只展示与新增有关的片段，完整配置、导入、异常处理和测试都在源码包里。

## 7. 接到前端，再用测试检查修改

在自己的前端开发服务器中，可以把 `/api` 代理到 `http://127.0.0.1:8080`，然后这样调用新增接口：

```ts
const response = await fetch('/api/tasks', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ title: '学习后端' }),
})
const body = await response.json()
if (!response.ok) {
  throw new Error(`${body.message}（requestId=${body.requestId ?? 'unknown'}）`)
}
console.log(body.id)
```

这里调用的是有 JSON 正文的新增接口。`fetch` 收到 400 或 500 时不会自动抛错，所以要检查 `response.ok`。以后经过代理部署，还要考虑非 JSON 错误页，避免解析错误掩盖原来的 HTTP 状态。

如果不通过开发代理，而是让浏览器直接跨端口请求，要按 [CORS 的配置方式](./22a-http-and-browser) 处理，不需要关闭浏览器安全设置。

改过代码后，在示例目录运行 `mvn test`。配套的 7 个测试方法会检查 CRUD、非法输入、分页和协议错误等行为，帮助你发现回归。它们使用隔离的 H2 测试库。

要试一下打包运行，先回到原启动终端，按 Ctrl+C 停止本次练习的服务，再执行：

```bash
mvn package
java -jar target/task-api.jar
```

如果之前因为其他程序占用 8080 而选了 8081，这里仍用 `java -jar target/task-api.jar --server.port=8081`，后续 URL 也保持 8081。不要停止那个不属于本次练习的程序。

::: details 这份实验还有哪些约定和限制？
标题要求非空、长度不超过 100 个 UTF-16 单元。请求先经过字段校验，Service 再用 strip 去掉两端空白并拒绝空串；JSON 配置还明确拒绝数字、布尔标题以及数字枚举，而不是依赖框架自动转换。视觉上的一个 emoji 不一定只占一个 UTF-16 单元。

初版列表的记录和总数是两次查询，不保证并发时来自同一快照。更新允许合法枚举之间切换，还没有复杂工作流规则；POST 也没有业务幂等键或并发版本检查。

工程固定 Spring Boot 3.5.7 以便复现实验，生产项目需要选择受维护版本并检查依赖安全。H2 测试通过，也不能直接证明 MySQL 的锁、SQL 方言和持久化配置都正确。
:::

下一篇：[给前端一份明确的接口约定](./34-api-design)。刚才用到的路径、状态码和返回结构，需要成为双方都能依赖的规则。

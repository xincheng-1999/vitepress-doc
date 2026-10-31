---
prev:
  text: "B3. 三层分工与工程阅读"
  link: "/back-end/frontend-backend-guide/04-three-layer-and-structure"
next:
  text: "B5. 接口契约与 API 设计"
  link: "/back-end/frontend-backend-guide/34-api-design"
---

# 动手：第一个可运行的任务 API

> 学习目标：不用已有公司工程、不依赖私有 `cpt-common`，在本机跑通 HTTP → 校验 → Service → SQL → JSON，并用自动测试验证失败路径。

前置：[Java 与环境](./05-java-crash-course)、[Spring](./06-spring-boot-ioc-di)、[三层结构](./04-three-layer-and-structure)。

::: warning 这是第一阶段的实验，不是生产脚手架
没有登录、数据归属、请求幂等键和并发版本检测。它默认只监听本机，所有调用者都能操作所有任务。H2 内存库重启丢数据。**不要改成公网监听后直接上线。** 后续按 SQL、事务、安全和综合实战逐步补齐。
:::

## 1. 先拿到完整源码，不从碎片里拼工程

仓库根目录已有 `examples/backend-task-api`；也可以 [下载完整示例源码](/backend-guide/task-api.zip)，解压后进入 `backend-task-api`。

```bash
# 已经 clone 本笔记仓库时，从仓库根目录执行
cd examples/backend-task-api
java -version
mvn -version
mvn test
mvn spring-boot:run
```

要求 JDK 17、Maven 3.6.3+；第一次 Maven 要联网下载依赖。使用固定 Spring Boot **3.5.7** 复现实验，不代表它永远最新或适合直接上线；真实上线需升级受维护版本并检查依赖漏洞。

**预期**：测试无失败，启动日志显示 Tomcat 监听 `8080`，应用启动完成。服务只监听 `127.0.0.1`。另开终端运行后面的 curl；结束时按 Ctrl+C。

如果 8080 被占用，可用 `mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081`，并把后续 URL 统一替换为 8081。不要随意杀掉不认识的进程。

## 2. 这版 API 的契约

| 方法 | 路径 | 成功结果 |
| --- | --- | --- |
| POST | `/api/tasks` | 201 + 新任务 + Location 响应头 |
| GET | `/api/tasks/{id}` | 200 + 任务；不存在是 404 |
| GET | `/api/tasks?page=0&size=20` | 200 + `{items,total,page,size}` |
| PUT | `/api/tasks/{id}` | 200 + 更新后的任务；不存在是 404 |
| DELETE | `/api/tasks/{id}` | 204，无响应体；重复删除仍 204 |

创建只接受 `title`；更新需要 `title` 和 `status`。状态枚举为 `TODO / DOING / DONE`，不是任意字符串。页码从 0 开始，`size` 范围 1..100，教学版限制 `page ≤ 10000`。

成功直接返回资源，不包 `code:0`；错误统一为 `{code,message,requestId}`，同时保留正确 HTTP 状态。其他团队可以使用不同外壳，核心是契约一致而不是名字必须叫 `RtData`。

## 3. 看一眼真实的数据流

![任务请求在 DTO、Service、Repository 与数据库之间流动，响应只返回公开字段](/backend-guide/core-layer-map.svg)

下面是源码中的真实片段，完整 imports、配置、异常和测试都在下载包中。

### 入口参数：不是信任一段任意 JSON

```java
record CreateTaskRequest(@NotBlank @Size(max = 100) String title) {}
record UpdateTaskRequest(@NotBlank @Size(max = 100) String title,
                         @NotNull TaskStatus status) {}
enum TaskStatus { TODO, DOING, DONE }
```

`@Valid @RequestBody` 触发绑定与校验，validation starter 提供校验实现。标题先校验非空和最大长度，业务层 `normalizeTitle` 用 `strip()` 去掉两端空白后再次拒绝空串，避免框架 trim 与 Unicode 空白规则差异；Java String 长度按 UTF-16 单元计，不是所有 emoji 都算一个字符，产品若要求视觉字符数需另定规则。

### Controller：协议适配

```java
@PostMapping
ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
    var task = service.create(request);
    return ResponseEntity.created(URI.create("/api/tasks/" + task.id())).body(task);
}
```

`ResponseEntity` 能同时控制状态、响应头和响应体。不要为了统一格式把所有结果都转成 HTTP 200。

### Service：业务默认值与事务

```java
@Transactional
public TaskResponse create(CreateTaskRequest request) {
    var task = new TaskResponse(UUID.randomUUID().toString(), normalizeTitle(request.title()),
        TaskStatus.TODO, Instant.now().truncatedTo(ChronoUnit.MICROS));
    repository.insert(task);
    return task;
}
```

ID 用字符串，避免前端 Number 精度问题；时间用 UTC `Instant`，JSON 类似 `2026-10-31T08:00:00Z`。事务提交成功不代表网络响应一定送达；本版 POST 尚未实现业务幂等。

### Repository：参数化 SQL

```java
jdbc.update("INSERT INTO tasks(id,title,status,created_at) VALUES (?,?,?,?)",
    task.id(), task.title(), task.status().name(), Timestamp.from(task.createdAt()));
```

`?` 占位符绑定数据，不把标题拼到 SQL 中。JdbcTemplate 处理连接借还和常见异常转换；它不是 ORM，也不会自动为你做数据权限检查。

## 4. 逐个请求，亲眼看状态码

### 创建

```bash
curl -i http://127.0.0.1:8080/api/tasks \
  -H 'Content-Type: application/json' \
  -d '{"title":"学会 SQL"}'
```

预期示意（ID、时间、请求 ID 每次不同）：

```http
HTTP/1.1 201
Location: /api/tasks/生成的UUID
X-Request-Id: 本次请求的UUID
Content-Type: application/json
```

```json
{
  "id": "生成的UUID",
  "title": "学会 SQL",
  "status": "TODO",
  "createdAt": "2026-10-31T08:00:00Z"
}
```

把返回的真实 ID 复制进去，不要原样使用中文占位文字：

```bash
TASK_ID='替换成刚返回的真实id'
curl -i "http://127.0.0.1:8080/api/tasks/${TASK_ID}"
curl -i 'http://127.0.0.1:8080/api/tasks?page=0&size=10'
```

列表响应应有一个任务，`total:1`、`page:0`、`size:10`。若 H2 中已有任务，总数不同正常。列表和总数是两次查询，当前版本不承诺并发时属于同一个快照。

### 更新

```bash
curl -i -X PUT "http://127.0.0.1:8080/api/tasks/${TASK_ID}" \
  -H 'Content-Type: application/json' \
  -d '{"title":"学会 SQL","status":"DONE"}'
```

预期 `200`，状态变为 DONE。PUT 替换这个资源允许客户端编辑的全部字段；这里允许任意合法枚举间切换，**尚未实现复杂工作流规则**。

### 删除

```bash
curl -i -X DELETE "http://127.0.0.1:8080/api/tasks/${TASK_ID}"
curl -i -X DELETE "http://127.0.0.1:8080/api/tasks/${TASK_ID}"
curl -i "http://127.0.0.1:8080/api/tasks/${TASK_ID}"
```

预期 `204 → 204 → 404`。204 不要调用 `response.json()` 强行解析空响应体。

## 5. 失败路径才是验收重点

```bash
# 空标题：400
curl -i http://127.0.0.1:8080/api/tasks \
  -H 'Content-Type: application/json' -d '{"title":"   "}'
# 非法分页：400
curl -i 'http://127.0.0.1:8080/api/tasks?size=101'
# 不支持的 HTTP 方法：405，并保留 Allow 头
curl -i -X PATCH http://127.0.0.1:8080/api/tasks/missing
# 不支持的请求格式：415
curl -i http://127.0.0.1:8080/api/tasks \
  -H 'Content-Type: text/plain' --data 'not json'
```

错误结构示意：

```json
{"code":"INVALID_ARGUMENT","message":"标题须为非空文本且不超过 100 个 UTF-16 单元，更新时须提供合法状态","requestId":"本次请求的UUID"}
```

业务异常映射为对应状态，MVC 协议异常保留 400/404/405/415；未知异常只对外返回通用 500，堆栈留在服务器日志。**泛型 `catch(Exception)` 如果把所有错误都变 500，会毁掉联调和监控语义。**

## 6. 在前端调用：先处理状态，再处理数据

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

上例假设响应来自本应用且为 JSON。真实部署的代理可能返回 HTML 错误页，应按 Content-Type 容错读取 body，避免 JSON 解析错误掩盖原始状态。`fetch` 收到 400/500 不会自动 reject，所以要检查 `response.ok`。本机前端 dev server 可把 `/api` 代理到 `http://127.0.0.1:8080`；直接跨端口则涉及 [CORS](./22a-http-and-browser)，不要靠关闭浏览器安全选项解决。

## 7. 自动测试与下一步

```bash
mvn test
mvn package
java -jar target/task-api.jar
```

**预期**：`TaskApiTest` 的 7 个测试方法全部通过，构建生成可执行 JAR。不要在已经启动服务时再起第二个同端口进程。

测试覆盖完整 CRUD、空白与超长标题、非法 JSON / 枚举、分页边界、不存在的更新、405/415/404、SQL 注入文本按普通数据存储。测试在 H2 上运行，**不证明 MySQL 的方言、锁、执行计划或持久化也都正确**。

现在还缺：用户表与 `owner_id`、真实认证、数据隔离、更新冲突检测、幂等策略、持久数据库、迁移、备份和恢复。后面的课逐项补，不需要在这里先背所有框架。

## 自测与参考答案

1. 关服务再启动，刚才的任务还在吗？**不在，这版使用 H2 内存库。**
2. 传 `uid:100` 能证明我是用户 100 吗？**不能。请求头本身不可信，本版根本没实现认证。**
3. 为什么标题含 SQL 语句也不会删表？**参数化 SQL 把它作为数据，而非语句拼接执行。**

下一站：[接口契约](./34-api-design)，然后进入 [数据建模、SQL 与索引](./10-sql-and-indexes)。

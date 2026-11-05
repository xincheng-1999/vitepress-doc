---
prev:
  text: "C4. 实验：Session 登录与隔离"
  link: "/back-end/frontend-backend-guide/32a-session-login-lab"
next:
  text: "D1. 上传、导出、调度与回调"
  link: "/back-end/frontend-backend-guide/39-backend-feature-map"
---

# 后端测试：改了异常处理，怎样知道接口没被改坏

你把异常处理改成了统一格式，创建任务仍能成功，手工点几下看不出问题。可是不支持的 PATCH 和错误的 Content-Type，现在都返回 500。正常页面没走这些请求，回归就漏掉了。

我们从工程里已有的测试看起：它们怎样发请求，怎样判断结果，以及什么时候还需要查数据库。然后再分清，初版的 7 个 CRUD 测试、Session 变体的 16 个安全测试，各自覆盖哪一套应用。

前置是 [CRUD 实验](/back-end/frontend-backend-guide/07-build-a-crud-api) 和 [接口契约](/back-end/frontend-backend-guide/34-api-design)。先使用仓库的 `examples/backend-task-api`，对应测试类是 `src/test/java/dev/learning/tasks/TaskApiTest.java`；[登录实验](./32a-session-login-lab) 是另一个独立工程，不把两套源码和测试数混在一起。

## 1. 先运行测试，不需要提前启动服务

准备 JDK 17、Maven 3.6.3+。首次解析依赖需要联网，从仓库根目录执行：

```bash
cd examples/backend-task-api
mvn test
# 缩小到一个测试类或一个方法
mvn test -Dtest=TaskApiTest
mvn test '-Dtest=TaskApiTest#protocolErrorsKeepTheirStatus'
```

完整初版工程应得到类似结果，下面是预期形态，不是本次运行记录：

```text
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

这里的 7 指 `@Test` 方法数。有的方法循环发出多种非法请求，所以请求数量远大于 7。Failures 是断言失败，Errors 常见于未预期异常或上下文启动失败；先找第一个有效原因，不通过删断言、跳过测试把报告变绿。

测试会创建和清理数据，因此先确认隔离机制。工程固定 test profile，关闭自动 SQL 初始化，再手工创建随机命名的内存 H2 DataSource，**不绑定外部 datasource 或 Hikari 属性**。初始化 schema 和每次 DELETE 前，还检查 JDBC metadata URL 必须精确等于本次随机的 `jdbc:h2:mem:...`。不符合就停止，防止环境配置把清理语句指向外部库。不要移除这些保护，测试也绝不连接生产或共享业务库。

## 2. 先看一条会发现这次回归的测试

单独运行的 `protocolErrorsKeepTheirStatus` 如下：

```java
@Test void protocolErrorsKeepTheirStatus() throws Exception {
    mvc.perform(patch("/api/tasks/missing"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().exists("Allow"));
    mvc.perform(post("/api/tasks")
        .contentType(MediaType.TEXT_PLAIN).content("x"))
        .andExpect(status().isUnsupportedMediaType());
    mvc.perform(get("/no-such-route"))
        .andExpect(status().isNotFound());
}
```

`perform` 构造请求并交给应用，`andExpect` 检查返回结果。第一个请求故意使用未支持的 PATCH，应该是 405，还要保留 Allow；第二个发送 text/plain，应该是 415；第三个找不存在的路由，应该是 404。

如果统一异常处理把它们都改成 500，这些断言就会失败。测试关注的是调用方能看到的协议行为，而不是只检查某个异常处理方法有没有被调用。这也是先写清接口契约的价值：结果应该怎样，不靠测试运行时临时猜。

## 3. 状态码对了，还要看有没有错误副作用

再看删除的一小段：

```java
mvc.perform(delete("/api/tasks/{id}", id))
    .andExpect(status().isNoContent())
    .andExpect(content().string(""));
```

204 表示无正文，单查状态还不够，因此同时检查响应体为空。客户端也就不应对这次响应再调用 `response.json()`。

非法创建同理。返回 400 之后，还要查表里是否真的没增加任务；非法更新之后，要重新读原任务，确认标题没有被清空。否则一种错误实现可能“先写库、后发现错误”，响应看上去拒绝了，数据却已经变了。

初版的 `invalidTitleNeverWrites` 就检查了这点，包括 EM SPACE 等 Unicode 空白。标题先经过 strip，业务层仍要拒绝归一化后为空的值。另一个用例保存看似 SQL 注入的标题，要求返回同样文本、表仍然存在且只有一条记录，检查参数确实被当作数据。

这类用例保护的是具体路径，并非完整安全审计。未来增加动态排序或其他 SQL 时，仍要对新入口检查和补测。

## 4. 已有七个方法，可以怎样读

先理解上面的请求与副作用，再用这张表查完整范围，不必一次背下所有方法名：

| 方法 | 检查的行为 |
| --- | --- |
| `createReadListUpdateDelete` | 创建 201、Location 和请求 ID、默认 TODO；读取、分页、PUT DONE；删除 204 空正文、重复删除 204、删除后查询 404 |
| `invalidTitleNeverWrites` | 空串、ASCII 与 Unicode 空白、101 长度标题为 400；错误有 requestId；创建不写入，非法更新不改原值 |
| `malformedJsonAndInvalidEnumAre400` | 数字、布尔、null、缺失、数组标题与坏 JSON 被拒绝；非法状态字符串和数字枚举为 400 |
| `pageBoundariesAreEnforced` | size 为 0、101，page 为 -1、10001 或非数字时为 400 |
| `missingUpdateIs404` | 合法格式更新不存在任务为 404 |
| `protocolErrorsKeepTheirStatus` | 405 保留 Allow，错误媒体类型 415，未知路径 404 |
| `sqlParametersDoNotBecomeSql` | 注入样式标题原样保存，任务表仍在且记录数为 1 |

严格拒绝数字标题和数字枚举，还依赖工程的 `JsonConfig` 反序列化配置。String、enum 或校验注解本身未必给出相同结果，测试能避免框架默认转换悄悄改变这项约定。

## 5. 请求到底经过了哪些真实代码

这些请求由 MockMvc 发出，没有在测试里启动网络端口，但它们会进入应用的 Web 处理流程。Controller、Service、Repository、JSON 配置和异常处理都参与，Service 与 Repository 没有被替换成预设答案的 mock。

测试骨架如下，完整 imports 和配置在工程内：

```java
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:unused-test-bootstrap",
    "spring.sql.init.mode=never", "spring.profiles.active=test"
})
@AutoConfigureMockMvc
@Import(TaskApiTest.IsolatedDatabase.class)
class TaskApiTest {
    private static final String DATABASE_URL = "jdbc:h2:mem:task-api-tests-" + UUID.randomUUID();
    // 同文件中的 IsolatedDatabase 手工创建未绑定属性的 DataSource，
    // 校验精确 URL 后再执行本项目 schema.sql；此处省略该配置，不能删掉保护。
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void clean() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assertThat(connection.getMetaData().getURL())
                .startsWith("jdbc:h2:mem:").isEqualTo(DATABASE_URL);
        }
        jdbc.update("DELETE FROM tasks");
    }
    // 7 个真实测试方法
}
```

`@SpringBootTest` 加载应用上下文，`@AutoConfigureMockMvc` 提供模拟的 Servlet 请求入口。H2 真正执行 SQL，不是伪造数据库返回值。`@BeforeEach` 每个方法前清表，保证方法不用依赖上一条测试留下的记录；现有隔离方式也不能默认支持任意并行运行。

因为不走网络，它们验证不了 DNS、TCP、TLS、Nginx 和浏览器 CORS。H2 的行为也不能代表生产 MySQL。通过这组测试，只说明列出的规则在这套 Spring 加 H2 的环境里符合预期。

## 6. 增加保护时，给对应工程补测试

初版任务 API 没有 owner_id、登录或用户隔离，任何调用者都能操作所有任务，不能直接公网部署。它的七个测试不包含“越权保护成功”。

[安全设计](/back-end/frontend-backend-guide/32-security) 后面的独立 Session 实验已经提供了另一套实现和 **16 个安全测试方法**。它们真实取 CSRF token、提交表单登录，再验证会话轮换、旧 token 失效、未登录 JSON 401、越权读写删 404、分页 items 与 total 隔离、伪造 uid 和 owner 无效，以及退出后旧会话失效。

读这些用例时仍按同一个问题推进：请求为什么应失败，失败之后什么不能变化。例如 A 带合法 CSRF token 修改 B 的任务，应拒绝并检查 B 数据没变；漏 token 的变更与退出，都不能产生副作用。两工程都保留随机内存数据源和清理前 URL 校验，不把安全变体的测试直接塞回初版再指望自动通过。

将来补请求幂等键、版本冲突或复杂状态流转，再补相应场景。重复 DELETE 的测试通过，只能说明这一个删除行为，不说明所有写操作都可以随意重试。跨源时也要分别测服务端响应头、真实请求权限，再在浏览器验证读取限制。

## 7. 迁到 MySQL 后，增加真实数据库的验证

H2 降低启动门槛，但 MySQL 的约束、锁和时间处理都需要在目标版本实测。可以用独立临时 MySQL 库，或额外接入 Testcontainers；后者需要新测试依赖与 Docker，不是当前默认七个测试的一部分。

先验证建表、参数化 SQL 和实际约束：缺失、重复、越界数据是否正确拒绝；再检查中文、emoji、UTC 时间点与微秒精度往返。接着用多个真实连接制造提交、回滚、并发更新和锁等待，检查最后的数据，而不只检查有没有异常。

迁移后还要重新检查 API 契约、分页排序和重启持久化。索引与性能使用有代表性的数据量另做测试，数据库容器能启动，不表示这些问题都已验证。

临时库使用独立账号和最小权限，生产凭据不进入 CI。`CREATE TABLE IF NOT EXISTS` 也不能代替版本化迁移与回滚方案的测试。

## 按需补充

### 什么时候用单元测试，什么时候用更完整的环境

纯粹的状态转换或计算规则，可以普通 JUnit 直接给输入看输出。需要模拟“查询不存在”或“下游失败”时，Mockito 可以提供依赖替身，但它只会按设定回答，无法检查真实 SQL。

MVC 切片如 `@WebMvcTest` 适合路由、绑定和序列化，必须显式引入需要的配置，避免漏掉严格 JSON 规则。当前 Spring 加 H2 的集成测试，则把多层实现接在一起验证。需要 MySQL 约束与事务就上真实 MySQL；要检查浏览器经过真实入口完成登录，再做授权环境下的端到端测试。

普通 `new TaskService(mockRepository)` 不会经过 Spring 事务代理，不能用它证明回滚。没有固定的“70% 单测”配额，按风险、反馈速度和维护成本分配；少量端到端覆盖不了全部边界，测试数量或覆盖率高也不能代替有效断言。

### 接下来可以亲手补一个什么用例

在实验副本中创建一条任务，GET `/api/tasks`，检查默认 page=0、size=20、total=1、items 数量为 1。新增独立 `@Test` 后，副本的总方法数应为 8，原工程仍以实际源码为准。

若要验证“MySQL 中同一幂等键并发只能创建一条”，先实现事务和唯一约束，再让真实多连接竞争；只 mock Repository 只能测局部分支。H2 全绿但浏览器跨域失败也不矛盾，它们走的环境不同，按 [网络与 HTTP](/back-end/frontend-backend-guide/22a-http-and-browser) 继续排查。

以后需要把测试放入流水线时，再看 [CI/CD 与部署](/back-end/frontend-backend-guide/36-cicd-deployment)。每加一条规则，先回 [API 设计](/back-end/frontend-backend-guide/34-api-design) 明确预期，再选能真正触及这条规则的测试环境。

主线下一篇：[D1 · 上传、导出、调度与回调](./39-backend-feature-map)。从已经测过的任务 API 出发，看看新增功能会带来哪些新的处理步骤。

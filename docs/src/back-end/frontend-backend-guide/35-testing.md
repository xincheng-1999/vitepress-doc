---
prev:
  text: "C4. 实验：Session 登录与隔离"
  link: "/back-end/frontend-backend-guide/32a-session-login-lab"
next:
  text: "D1. 上传、导出、调度与回调"
  link: "/back-end/frontend-backend-guide/39-backend-feature-map"
---

# 后端测试：把任务 API 的承诺变成证据

> 场景：你把全局异常处理改得更“统一”，结果 405 和 415 都成了 500；页面还能创建任务，手工点几下却没发现回归。
> 测试的价值不是证明“代码执行过”，而是守住接口结果、数据库副作用和安全边界。

前置：[CRUD 实验](/back-end/frontend-backend-guide/07-build-a-crud-api)、[接口契约](/back-end/frontend-backend-guide/34-api-design)。主线源码位于仓库根目录 `examples/backend-task-api`，测试类为 `src/test/java/dev/learning/tasks/TaskApiTest.java`。

**学完能做什么：**运行并读懂现有 7 个测试方法，解释它们测到和没测到什么，再为 MySQL、权限和并发设计下一批验收用例。维护时已实际通过这 7 个测试及可执行构建；下面给出可复现命令，你仍应在自己的环境运行。Session 变体的 16 个安全测试与覆盖边界见 [登录实验](./32a-session-login-lab)。

## 1. 先运行真实已有的测试

前提是 JDK 17、Maven 3.6.3+；第一次解析依赖需要联网。从仓库根目录进入示例：

```bash
cd examples/backend-task-api
mvn test
# 缩小到一个测试类或一个方法
mvn test -Dtest=TaskApiTest
mvn test '-Dtest=TaskApiTest#protocolErrorsKeepTheirStatus'
```

测试导入手工创建的随机 H2 内存 DataSource，不绑定外部 datasource / Hikari 属性；test profile 固定，并关闭自动 SQL 初始化。执行本项目 schema 和每次清理前，都核验 JDBC 元数据必须等于本次随机 `jdbc:h2:mem:...`。不满足即失败，避免读者环境把初始化或 DELETE 指向外部库。仍不要主动改写隔离配置，**测试绝不能指向生产或共享业务库**。

预期报告示意，不是本次编辑的执行结果：

```text
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

7 是当前 `@Test` 方法数，不是只有 7 个请求；几个方法在内部循环测试多种输入。`Failures` 表示断言失败，`Errors` 常表示未预期异常或测试上下文失败。出现错误先看第一个有效原因，不要直接删断言或跳过测试。

## 2. 现有 7 个测试分别保护什么

| 真实方法名 | 现有断言覆盖的行为 |
| --- | --- |
| `createReadListUpdateDelete` | 创建 201、Location 与请求 ID 头、默认 TODO；查询、分页、PUT DONE；DELETE 204 空正文、重复删除 204、删除后 GET 404 |
| `invalidTitleNeverWrites` | 空串、ASCII / Unicode 全空白、101 长度标题返回 400；错误有 requestId；非法创建不新增任务，Unicode 空白更新不改变原值 |
| `malformedJsonAndInvalidEnumAre400` | 标题数字、布尔、null、缺失、数组，以及坏 JSON 返回 400；非法状态字符串和数字枚举返回 400 |
| `pageBoundariesAreEnforced` | size 为 0 / 101、page 为 -1 / 10001 / 非数字时返回 400 |
| `missingUpdateIs404` | 格式合法的 PUT 更新不存在任务返回 404 |
| `protocolErrorsKeepTheirStatus` | PATCH 返回 405 并保留 Allow，text/plain 创建返回 415，不存在的路径返回 404 |
| `sqlParametersDoNotBecomeSql` | 类似 SQL 注入的标题按普通文本保存，响应标题不变，任务表仍在且有一条记录 |

标题类型和数字枚举之所以被拒绝，还依赖主线 `JsonConfig` 的严格反序列化配置，**不是 String、enum 或校验注解天然保证**。测试把这项契约固定下来，避免框架默认转换悄悄改变输入含义。

上述 SQL 用例是一条回归证据，不是完整安全审计；它不能证明所有未来查询、排序字段和动态 SQL 都没有注入风险。

## 3. 看懂装配：不是“把 HTTP 全 mock 掉”

源码的测试骨架如下，完整 imports 和全部方法在示例工程内：

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

- `@SpringBootTest` 加载完整应用上下文；Controller、Service、Repository、JSON 配置和异常处理都真实参与。
- `@AutoConfigureMockMvc` 提供 MockMvc，经模拟的 Servlet 请求调用应用；当前没有替换 Service 或 Repository 的 mock。
- 默认测试不启动真实网络监听端口，所以不要求先启动 Tomcat，也**测不到** DNS、TCP、TLS、Nginx 或浏览器 CORS 执行结果。
- H2 是真正执行 SQL 的嵌入式数据库，不是“伪造了所有 SQL 返回值”的替身，但它不是生产 MySQL。
- `@BeforeEach` 在每个方法前清空测试表。现有用例依赖隔离数据库，不应依赖测试顺序或默认假设并行运行也安全。

## 4. 一条断言只证明一件具体的事

下面节选自现有 `protocolErrorsKeepTheirStatus`：

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

`perform` 准备并发送请求，`andExpect` 检查可观察结果；这和前端用测试工具“发请求、断言响应”的思路相通。400 / 405 / 415 不是可以随便统一的失败，它们分别告诉调用方不同的修正方向。

另一个例子来自删除流程：

```java
mvc.perform(delete("/api/tasks/{id}", id))
    .andExpect(status().isNoContent())
    .andExpect(content().string(""));
```

只断言 204 不足以验证“无正文”；也不要在客户端对 204 调 `response.json()`。同样，“非法标题返回 400”之外再查数据库为 0，才进一步证明没有意外写入。

## 5. 单元、切片、集成、端到端怎样分工

| 层次 | 想验证什么 | 测不到什么 |
| --- | --- | --- |
| 普通 JUnit 单元测试 | 纯规则、状态转换、服务在指定依赖结果下的行为 | Spring 装配、真实 SQL、事务代理 |
| MVC 切片，如 `@WebMvcTest` | 路由、绑定、序列化与 Web 相关配置 | 被替换掉的业务和数据库实现 |
| 当前 Spring + H2 集成测试 | 多层接线和已列出的接口 / SQL 行为 | MySQL 实际差异、真实网络与浏览器 |
| 专门的 MySQL 集成测试 | 目标数据库的约束、时间、查询、事务 | 公网入口、完整用户浏览器流程 |
| 授权环境的端到端测试 | 浏览器或客户端经真实入口完成关键流程 | 所有内部边界组合与所有并发交错 |

JUnit 提供测试生命周期、断言等；Mockito 可构造依赖替身。替身能模拟“查询不存在”“下游抛错”，但只能按设定回答，不能证明 SQL 正确。也不是所有单元测试都必须 mock：纯函数通常直接测输入输出。

用普通 `new TaskService(mockRepository)` 测规则时，Spring 事务代理不会因此启动，不能拿它证明事务回滚。MVC 切片也必须显式包含需要的配置，否则可能漏掉主线严格 JSON 配置。

没有固定“70% 单测”配额：按失败风险、反馈速度和维护成本选层次；不要让少量慢 E2E 替代所有边界验证，也不要只测 mock 调用次数而不检查结果。

## 6. 从 H2 迁移 MySQL：需要新增什么证据

主线的 H2 先降低 CRUD 门槛，生产持久化方向是 MySQL。**H2 测试全绿不证明 MySQL 已被验证**，MySQL 也不因容器能启动就自动正确。

后续用独立临时 MySQL 库或 Testcontainers 增加集成测试；后者需要额外测试依赖及 Docker，不是当前默认 7 个测试的组成部分。至少覆盖：

- 目标 MySQL 版本上的 DDL、参数化 SQL、CHECK 与其他实际约束，测试缺失 / 重复 / 越界数据。
- `createdAt` 的微秒精度和 UTC 时间点往返、中文和 emoji，连接与会话时区配置。
- 真正跨连接的提交、回滚、并发更新、死锁或锁等待；验证最终数据，不只看异常是否出现。
- 数据迁移后的接口契约、分页排序和重启持久化；执行计划、索引与性能预算另作有代表性数据量的验证。

测试库使用独立账号与最小权限，临时数据可清理。不能把生产凭据复制给 CI；也不要把 `CREATE TABLE IF NOT EXISTS` 当成已经测试过的版本化迁移和回滚。

## 7. 未来的越权测试：现在没有，不要假装已经安全

当前任务没有 owner_id、登录或用户隔离，任何调用者都能操作所有任务；**现有测试不能证明任何资源归属保护，不可直接公网部署**。

接入 [安全设计](/back-end/frontend-backend-guide/32-security) 后，新增两名真实测试身份 A、B，并至少验证：

1. 未认证请求按方案返回 401；不能通过伪造 uid 头或 body 的 ownerId 获得身份。
2. A 查询列表只看到 A 的任务；直接猜测 B 的 ID，也不能读取、修改、删除 B 的资源。
3. 越权写操作按契约返回 403 或隐藏存在性的 404，并验证 **B 的数据完全未变**；不只断言状态码。
4. Cookie 认证时补 CSRF 正反用例；跨源场景分别测预检响应头与实际请求权限，浏览器端再验证读取限制。

复杂工作流、请求幂等键、更新版本检查也都尚未实现。补齐后再增加相应测试，不能用“重复 DELETE 通过”宣称所有写操作都可安全重试。

## 动手与参考答案

1. **读测试**：运行 `mvn test`，再单独运行协议错误方法。预期完整类 7 个方法，单方法 1 个；当前报告若不同，先核对分支和源码，不要伪造输出。
2. **补一个用例**：在实验副本中为默认分页增加断言。参考：先创建一条任务，GET `/api/tasks`，检查 page=0、size=20、total=1、items 数量为 1。新增独立 `@Test` 后总方法数应变为 8。
3. **解释边界**：H2 测试通过，浏览器跨域请求仍失败，是否矛盾？参考：不矛盾，MockMvc 没有运行浏览器安全策略，也没有经过真实部署入口，应按 [网络与 HTTP](/back-end/frontend-backend-guide/22a-http-and-browser) 排查。
4. **挑选测试层**：要证明“MySQL 同一幂等键并发只能创建一条”，能只 mock Repository 吗？参考：不能，未来须实现唯一约束和事务，再用真实 MySQL 多连接并发验证；mock 只能测局部分支。

常见误解：测试多不等于风险全覆盖；覆盖率高不等于断言有效；MockMvc 不等于浏览器；H2 不等于 MySQL；没有权限模型时也就没有“越权已通过”的证据。

下一站：[CI/CD 与部署](/back-end/frontend-backend-guide/36-cicd-deployment)，把已明确范围的验证放到流水线；回到 [API 设计](/back-end/frontend-backend-guide/34-api-design)，为每一个新承诺安排对应的测试与环境。

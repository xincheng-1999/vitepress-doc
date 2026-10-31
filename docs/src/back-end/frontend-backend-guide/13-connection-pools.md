---
prev:
  text: "D3. 异步任务与 MQ 可靠性"
  link: "/back-end/frontend-backend-guide/33-mq-reliability"
next:
  text: "E1. Linux：进程、权限与端口"
  link: "/back-end/frontend-backend-guide/21-linux-server-essentials"
---

# 13. 连接池：有限连接如何被借用、等待和归还

## 学习目标

- 分清数据库连接池、HTTP 连接复用与线程池各自管理什么。
- 识别等待连接、建立连接、执行 SQL、等待锁等不同超时。
- 用证据解释池紧张，不把调大池子或某个经验公式当成万能修复。

场景仍是 Java 17 + Spring Boot 3.x 单体 `task-api`，`8080 /api/tasks`。当前示例使用 `spring-boot-starter-jdbc` 与 `JdbcTemplate`，常规自动配置选择 HikariCP；默认 H2 是嵌入式数据库，没有到远端数据库的 TCP 握手。切到 MySQL 后才会涉及远端建连、认证及按配置启用的 TLS。

## 13.1 一次查询借用的不是一条线程

```text
请求处理线程 → 向池借连接 → 执行 SQL / 等锁 → 读取结果 → 归还连接
                           ↑ 没有空闲且不能再建时，等待有上限
```

数据库连接代表客户端与数据库之间可执行操作的会话资源。重复建立远端连接有成本，池让它们在多个操作之间复用，同时限制并发占用。

`JdbcTemplate` 会按 Spring 的事务上下文获取和释放连接。在事务中，连接通常会在一段事务范围内被保留；具体获取时机还受数据源代理等实现影响，不能断言“方法一进入就一定创建 TCP 连接”。普通查询完成与事务完成也不是同一个边界。

手工借用连接时应使用 `try-with-resources`。池返回的代理连接调用 `close()` 通常意味着归还池，而不是每次都断开底层网络连接。不要因为看到 `close` 就把必要的释放删掉。

## 13.2 三种资源不能混为一谈

| 机制 | 管什么 | 达上限时可能发生什么 |
| --- | --- | --- |
| JDBC 连接池 | 连接/数据库会话，如 HikariCP | 借用者等待，超过获取超时失败 |
| HTTP 客户端连接管理 | 到目标服务的连接、空闲连接或流等，依客户端/协议而异 | 等连接/流配额，或建立新连接受限 |
| 线程池 | 执行任务的线程及任务队列 | 排队、拒绝或按策略执行，不会自动多出数据库连接 |

HTTP/2 可在一条连接上复用多个流，不能套用“一连接只能跑一个 HTTP 请求”的公式。某些 HTTP 客户端的池参数主要限制空闲连接，也不等于并发请求上限。当前示例没有调用下游 HTTP 服务，不需要先添加 Feign、OkHttp 或额外线程池。

类似地，Lettuce 可以通过连接复用工作，并非所有 Redis 用法都必须建一个连接池。每种客户端都要看自己的资源模型。

## 13.3 先认清超时发生在哪一段

| 时间预算 | 等待的对象 | 不能说明什么 |
| --- | --- | --- |
| Hikari `connectionTimeout` | 从池中获得可用连接 | 不等于单条 SQL 最多执行多久 |
| 驱动连接超时 | 与数据库建立连接 | 不等于数据库账号已认证成功或业务已完成 |
| 查询/语句超时 | SQL 执行与驱动定义的等待范围 | 驱动取消不一定瞬间停止所有服务端工作 |
| 锁等待超时 | 等其他事务释放冲突资源 | 不代表连接池配置必然太小 |
| HTTP 连接获取/连接/读取/整体超时 | 等 HTTP 资源、建连、响应或全程 | 不是所有客户端都有同名参数，单位和覆盖范围也不同 |

看到 `Connection is not available, request timed out`，首先可确认的是**本次未在获取预算内拿到可用连接**。原因可能是连接都忙，也可能是数据库不可达、认证失败或新连接建立不起来；单凭一行异常不能断言“只是池满，不是连不上”。

## 13.4 Hikari 参数：理解含义，不背固定推荐值

当前示例没有显式配置 Hikari 参数，使用所固定依赖版本的默认值。下面仅是**本地观察用的覆盖示例**，不是已存在的配置或生产建议：

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 2
      minimum-idle: 1
      connection-timeout: 1000
```

| 参数 | 意义 | 关键边界 |
| --- | --- | --- |
| `maximum-pool-size` | 池内连接总数上限 | 每个实例各有一池，多个实例会叠加占用数据库资源 |
| `minimum-idle` | 尽力保持的最少空闲连接数 | 不表示启动时所有线程或连接必须一次性创建 |
| `connection-timeout` | 获取可用连接的最长等待时间，Hikari 以毫秒配置 | 应配合整体请求预算，不能把任意 3 秒当所有系统标准 |
| `idle-timeout` | 超出最少空闲数量部分的空闲回收策略 | 有适用条件和实现粒度，不是准点关闭每条空闲连接 |
| `max-lifetime` | 连接的最长存活策略 | 使用中的连接通常待归还后退休，不是到点强行切断事务 |

数据库、代理与防火墙可能有空闲或连接寿命限制，应结合其规则评估保活与寿命配置；二者不是同一种超时。现代 JDBC 驱动通常支持标准有效性检查，不必不加判断就配置 `SELECT 1` 探测 SQL。

## 13.5 池紧张时，按证据找谁占用

| 现象 / 证据 | 可能原因 | 下一步 |
| --- | --- | --- |
| active 接近上限、idle 为零、pending 持续增长 | 工作连接不足以满足当前借用需求 | 看连接持有时长、数据库语句、锁和线程栈 |
| 数据库大量慢查询或锁等待 | 连接在等数据库完成 | 查计划、索引、事务长度与冲突，不先加并发 |
| 数据库不忙但连接长期借出 | 事务内慢 HTTP、代码未归还、嵌套借用等 | 检查业务持有范围和资源释放路径 |
| 总连接数没到上限却借不到 | 创建失败、网络/凭据问题等 | 查看连接创建的根异常，检查数据库可达和权限 |

Hikari 的详细池状态日志一般需要相应 DEBUG 级别，不是默认一定周期输出。Actuator/Micrometer 指标也需要接入并安全暴露；当前 CRUD 示例没有提供现成的 `/actuator/prometheus`。

一个典型反例是：事务内先查任务，再调用耗时十秒的外部服务。连接可能在等待 HTTP 时一直被占用。应重新划分事务和外部调用边界，同时处理因此产生的一致性问题，而不是简单挪一行代码后宣称已经原子化。

### 容量预算比“CPU 乘二”更重要

先算多个实例的连接上限、其他应用占用与运维余量，再验证数据库在目标负载下的延迟和吞吐。数据库的 `max_connections` 是接入限制，不是建议让它一直跑满的目标。

“到达率 × 平均连接持有时间”可在稳定、口径正确的条件下估算平均占用，但不包含峰值、长尾、重试和锁竞争，不能直接拿来当池容量公式。扩大池可能降低等待，也可能让数据库竞争更激烈；必须用同一负载下的指标对照验证。

## 动手练习：在测试里观察两条连接的边界

**能运行的扩展实验**：在完整示例的 `src/test/java/dev/learning/tasks/ConnectionPoolTest.java` 新建下列测试；这是新增实验，不是当前已有业务端点。只连接默认本地 H2，避免对共享数据库制造阻塞。

```java
package dev.learning.tasks;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.datasource.hikari.maximum-pool-size=2",
    "spring.datasource.hikari.minimum-idle=1",
    "spring.datasource.hikari.connection-timeout=1000"
})
class ConnectionPoolTest {
    @Autowired DataSource dataSource;

    @Test void waitsThenRecovers() throws SQLException {
        try (var first = dataSource.getConnection();
             var second = dataSource.getConnection()) {
            assertThrows(SQLException.class, () -> {
                try (var ignored = dataSource.getConnection()) { }
            });
        }
        try (var recovered = dataSource.getConnection()) {
            assertTrue(recovered.isValid(1));
        }
    }
}
```

在示例目录执行 `mvn -Dtest=ConnectionPoolTest test`。**预期结果**：前两条被持有时，第三次获取经过约定等待后抛异常；前两条归还后，新的获取成功。此实验说明池和等待边界，不证明业务压测时池应设为 2。不要依赖精确到毫秒的耗时断言。

## 自测与答案

1. 有 100 个请求线程，是不是必须有 100 条数据库连接？

   **答案**：不是。线程和连接是不同资源，请求也未必全程持有连接；应看实际持有时长、负载与数据库能力。
2. 获取连接超时，能否证明 SQL 慢？

   **答案**：不能。还需区分借出未还、数据库创建失败、网络、锁等证据；该请求甚至可能尚未执行 SQL。
3. `max-lifetime` 到期会强行中断正在提交的事务吗？

   **答案**：Hikari 通常待使用中的连接归还后退休，不是强行截断业务；具体行为以依赖版本为准。

## 相关教程

- [SQL](/back-end/frontend-backend-guide/10-sql-and-indexes) / [事务](/back-end/frontend-backend-guide/11-transactions-consistency)：找连接在等什么。
- [线程池](/back-end/frontend-backend-guide/15-thread-pools-executor) / [性能](/back-end/frontend-backend-guide/31-performance-concurrency)：控制并发与排队。
- [可观测](/back-end/frontend-backend-guide/30-observability) / [排查方法](/back-end/frontend-backend-guide/27-troubleshooting-methodology)：用指标和栈验证假设。

---
prev:
  text: "D3. 异步任务与 MQ 可靠性"
  link: "/back-end/frontend-backend-guide/33-mq-reliability"
next:
  text: "E1. Linux：进程、权限与端口"
  link: "/back-end/frontend-backend-guide/21-linux-server-essentials"
---

# 连接池：请求为什么还没执行 SQL 就超时了

任务 API 偶尔报 `Connection is not available, request timed out`。看到数据库相关错误，很容易先去找慢 SQL。但这次请求也许根本没来得及执行 SQL：它一直在等一条可以使用的数据库连接。

下面先看连接从哪里借、什么时候还，再在本机做一个只有两条连接的实验。弄清这件事后，才知道应该改查询、缩短事务，还是调整池的大小。

示例仍是 Java 17 + Spring Boot 3.x 的 `task-api`，监听 `8080`，接口入口 `/api/tasks`。它使用 `spring-boot-starter-jdbc` 和 `JdbcTemplate`，常规自动配置选择 HikariCP 连接池；初版默认 H2，没有与远端数据库建立 TCP 连接的过程，切到 MySQL 后才涉及网络、认证和按配置使用的 TLS。

## 1. 请求先借连接，用完再交回来

数据库连接是应用与数据库之间的一份会话资源，执行 SQL 要通过它。远端连接建立后可以重复使用，不必每个请求都重新连接、认证。连接池负责保存这些可复用连接，并限制同时能借出多少条。

```text
请求处理线程 → 向池借连接 → 执行 SQL / 等锁 → 读取结果 → 归还连接
                           ↑ 没有空闲且不能再建时，等待有上限
```

池里有空闲连接就借出；没有空闲但还没到上限，可以尝试创建；都已借出且不能再增加时，新请求就等待。等待超过获取连接的时间预算，才会出现开头的超时。

`JdbcTemplate` 会配合 Spring 获取和释放连接。在事务中，连接通常会保留到这一段事务结束，而不是每执行完一条 SQL 就交回。实际获取时机还受数据源代理等实现影响，进入方法并不必然立即新建一条 TCP 连接。

如果手工获取连接，要用 `try-with-resources`。池返回的连接调用 `close()`，通常表示归还池中供后续请求使用，而不是每次断开底层网络。删掉 close 看似“避免关闭”，实际可能让连接一直借出不还。

## 2. 先在本机把两条连接借满

这个实验只验证连接池的借还行为，不增加业务端点，也不改应用配置。在完整初版示例的 `src/test/java/dev/learning/tasks/ConnectionPoolTest.java` 新建下列测试。

测试自己创建 HikariDataSource，URL 固定为随机命名的内存 H2，不启动 Spring 应用，也不读取外部 datasource 或 Hikari 配置，因此不会为了制造等待去占用共享数据库。它是读者新增的实验，原工程仍只有 7 个现成测试方法。

```java
package dev.learning.tasks;

import java.sql.SQLException;
import java.util.UUID;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionPoolTest {
    @Test void waitsThenRecovers() throws SQLException {
        var config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:pool-lab-" + UUID.randomUUID());
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(1000);
        try (var dataSource = new HikariDataSource(config)) {
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
}
```

在 `examples/backend-task-api` 目录执行：

```bash
mvn -Dtest=ConnectionPoolTest test
```

先借出的 first 和 second 没有离开各自的 try 范围，所以两条都仍在使用。第三次获取只能等待，大约用完配置的获取预算后抛 SQLException；这个异常正是测试预期，不代表测试失败。离开内层 try 后，前两条连接归还，recovered 就能再次借到有效连接。最外层 try 最后关闭整个测试连接池。

这里没有慢 SQL，也没有锁等待，却一样可以获取连接超时。实验让我们看清“等连接”和“执行 SQL”是两个阶段，不代表业务系统应该把池设成 2，也不需要写精确到毫秒的耗时断言。

## 3. 实际报错时，先确认时间花在哪一段

回到真实请求。Hikari 的 `connectionTimeout` 管的是“从池里拿到连接最多等多久”。拿到之后，一条 SQL 可能还会执行很久，也可能等别的事务释放锁；这些需要相应的查询和锁等待预算。

远端数据库还多一个建连过程。数据库不可达、账号认证失败、新连接建不起来，都可能让池无法及时提供可用连接。所以单凭开头那一行报错，只能确认本次获取失败，原因还要向前追查连接创建异常，并看池与数据库当时的状态。

常见超时可以这样区分：

| 设置或阶段 | 等待什么 |
| --- | --- |
| Hikari `connectionTimeout` | 一条可用的池中连接 |
| 驱动连接超时 | 建立数据库连接，具体覆盖范围看驱动 |
| 查询或语句超时 | SQL 执行及驱动定义的等待；取消不一定立即停止全部服务端工作 |
| 锁等待超时 | 其他事务释放冲突资源 |
| HTTP 超时 | 依客户端区分获取连接、建连、读响应或整体过程 |

这些预算要放进整个请求允许等待的时间里考虑。不能把获取超时设成十秒，再假设接口一定能在三秒内结束。

## 4. 连接都被谁拿着，才决定怎么修

先看池中正在使用的连接数、空闲数和等待借用数。如果 active 接近上限、idle 为零，pending 又持续增长，说明需求超过了当前能提供的连接，但还没说明为什么。

下一步看连接持有期间在做什么。数据库里有慢查询或锁等待，就先查执行计划、索引、长事务和冲突。此时加大池，可能只是让更多 SQL 一起排队抢资源。

如果数据库并不忙，却有连接长时间不归还，就查事务范围和释放路径。一个常见例子是：事务里查完任务，接着等十秒的外部 HTTP 调用。SQL 已经完成，连接却可能仍被这个事务占着。需要重新划分数据库事务与外部动作，并处理两者之间的一致性，而不是简单挪一行代码就宣布完成。

还有一种情况，总连接数没有达到上限，却始终借不到。重点就转向连接为什么建不起来：网络、凭据、数据库接入限制等，要读创建连接时的根异常。

查阅时可以按这张表快速定位：

| 观察结果 | 接着检查 |
| --- | --- |
| active 高、idle 为零、pending 增长 | 持有时长、数据库语句、锁和线程栈 |
| 慢 SQL 或大量锁等待 | 查询计划、索引、事务长度和冲突 |
| 数据库不忙，连接长期借出 | 慢外部调用、未归还、嵌套借用 |
| 连接数未达上限，但获取失败 | 连接创建异常、可达性、账号和权限 |

Hikari 的详细池状态日志通常要打开相应 DEBUG 级别，默认不一定周期输出。Actuator 和 Micrometer 也要额外接入并安全暴露；当前 CRUD 没有现成的 `/actuator/prometheus`，不要把不存在的监控端点当作排查前提。

## 5. 最后才讨论池应该多大

一个实例最多 20 条连接，十个实例就可能占用 200 条，还没算其他应用和运维连接。因此先算整体预算，再看数据库在目标负载下能承受多少并发。`max_connections` 是接入上限，不是推荐长期跑满的目标。

在稳定负载、统计口径一致时，“到达率乘以平均持有时间”能帮助估算平均占用。但峰值、长尾、重试和锁竞争都可能让实际等待显著增加，不能把它直接当容量公式，也不能只背“CPU 乘二”。

扩大池之后，要用同一负载比较等待、响应时间和数据库吞吐。等待减少了，但数据库争用更重、整体更慢，就不是有效修复。很多时候，缩短一次占用的时间比多开连接更有帮助。

## 按需补充

### Hikari 配置项怎样对应前面的过程

当前示例没有显式设置这些 Hikari 参数，使用固定依赖版本的默认值。下面只是本地观察时可使用的覆盖示例，不是已有配置或生产推荐值：

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 2
      minimum-idle: 1
      connection-timeout: 1000
```

| 参数 | 作用 |
| --- | --- |
| `maximum-pool-size` | 池内连接总数上限，每个应用实例分别计算 |
| `minimum-idle` | 尽力保持的最少空闲数，不表示所有资源必须启动时一次建齐 |
| `connection-timeout` | 获取连接的最长等待时间，Hikari 单位为毫秒 |
| `idle-timeout` | 对超过最少空闲数的连接执行空闲回收，有适用条件和实现粒度 |
| `max-lifetime` | 连接存活策略，使用中的连接通常待归还后退休，不会到点强行截断事务 |

数据库、代理、防火墙可能有自己的空闲和连接寿命限制，需要与保活及寿命策略协调。现代 JDBC 驱动通常支持标准有效性检查，不必不加判断就设置 `SELECT 1`。

### 线程池、HTTP 连接和 Redis 客户端怎样区分

100 个请求线程不要求配 100 条数据库连接。线程管理代码在哪里执行，数据库连接管理同时有多少数据库会话可用，请求也未必全程持有连接。线程更多，可能只是让更多借用者等待。

HTTP 客户端管理的则是到下游服务的连接或流。HTTP/2 能在一条连接中复用多个流，有的客户端池参数主要限制空闲连接，不能直接当成并发请求上限。Lettuce 也能复用 Redis 连接，不是每种 Redis 用法都必须单独建池。当前示例没有下游 HTTP 调用，无须为学习连接池先加 Feign、OkHttp 或额外线程池。

需要继续查连接在等什么时，可以看 [SQL](/back-end/frontend-backend-guide/10-sql-and-indexes) 和 [事务](/back-end/frontend-backend-guide/11-transactions-consistency)；控制执行和排队看 [线程池](/back-end/frontend-backend-guide/15-thread-pools-executor) 与 [性能](/back-end/frontend-backend-guide/31-performance-concurrency)；收集运行信息看 [可观测](/back-end/frontend-backend-guide/30-observability) 和 [排查方法](/back-end/frontend-backend-guide/27-troubleshooting-methodology)。

主线下一篇：[E1 · Linux：进程、权限与端口](./21-linux-server-essentials)。

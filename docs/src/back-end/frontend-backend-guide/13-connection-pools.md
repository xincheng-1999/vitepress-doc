# 连接池

> 如果你只做过前端，连接池大概率是你从没接触过的概念——因为它被浏览器和运行时帮你藏起来了。
> 这一章把它挖出来讲清楚：**为什么后端要专门维护一池子的连接、连接池满了会发生什么、以及怎么从线上现象反推出"池被打满了"。** 这是后端排查接口超时时绕不开的一环。

## 13.1 先建立直觉：连接很贵

前端发请求，你脑子里大概是这样的：`axios.get('/api/user')`，一行代码，瞬间就发出去了。但"建立一条连接"这件事，在底层其实相当昂贵。

以一次数据库连接为例，从零开始要走这么多步：

```text
应用                                          MySQL / MongoDB
 │                                                  │
 │ ── TCP SYN ────────────────────────────────────▶ │   ┐
 │ ◀──────────────────────────── TCP SYN+ACK ─────── │   │ 三次握手
 │ ── TCP ACK ────────────────────────────────────▶ │   ┘ (一来一回 = 一个 RTT)
 │                                                  │
 │ ── TLS 握手（如果开了加密）─────────────────────▶ │   ┐ 又是一两个 RTT
 │ ◀──────────────────────────────────────────────  │   ┘ + 证书校验
 │                                                  │
 │ ── 认证：用户名/密码、权限校验 ──────────────────▶ │   ┐ 数据库侧要查权限表、
 │ ◀──────────────────────────────────────────────  │   ┘ 分配会话资源
 │                                                  │
 │ ── 终于可以发第一条 SQL 了 ─────────────────────▶ │
```

在同机房局域网里，光是 TCP 三次握手 + 认证，建立一条数据库连接通常要 **几毫秒到几十毫秒**；如果还隔着 TLS，更慢。而一条 SQL 查询本身可能只要 **零点几毫秒**。也就是说——**建连接的开销，可能是真正干活的几十上百倍。**

> 💡 **前端类比**：这就像你每次调接口都重新 `new` 一个 axios 实例、重新做一遍 DNS 解析和 TLS 握手，而不是复用一个配好的实例。你不会这么干，后端也不会。

结论很自然：**连接建好之后别扔，用完放回去，下次接着用。** 这个"放连接的池子"就是连接池。

## 13.2 什么东西有池

"昂贵资源 → 预先创建一批 → 复用而不是每次新建"——这个思想在后端到处都是，不止数据库。

| 池类型 | 池里装的是 | 典型实现 | 在本项目里谁用 |
| --- | --- | --- | --- |
| **数据库连接池** | 到 MySQL / MongoDB 的 TCP 连接 | HikariCP（Spring Boot 默认） | svc-user 查配额、svc-canvas 读写任务 |
| **HTTP / Feign 连接池** | 到下游服务的 HTTP 长连接 | Apache HttpClient / OkHttp 连接池 | svc-gateway → svc-ai，Feign 调用 |
| **Redis 连接池** | 到 Redis 的 TCP 连接 | Lettuce / Jedis 连接池 | svc-auth 校验 token、限流、分布式锁 |
| **线程池** | 预先创建好的线程 | `ThreadPoolExecutor` | svc-canvas 编排子任务（见下一章） |

> 💡 **前端类比**：HTTP/2 的多路复用、`keep-alive` 复用 TCP 连接，本质都是"别每次重新建连接"。线程池的思想也一样——线程的创建/销毁同样昂贵，所以预先备一批循环用。线程池我们放到 [线程池与 Executor](/back-end/frontend-backend-guide/15-thread-pools-executor) 专门讲，本章聚焦数据库连接池。

数据库连接池是最经典、也是线上最容易出事的那个，所以下面以 Spring Boot 默认的 **HikariCP** 为主线。

## 13.3 HikariCP 的关键参数

Spring Boot 从 2.x 起默认用 HikariCP，你在 svc-user 里其实早就用上了，只是没意识到。它的配置长这样（`application.yml`）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://mysql:3306/svc_user?useSSL=false&serverTimezone=UTC
    username: svc_user
    password: ${DB_PASSWORD}
    hikari:
      # 池子最多能有多少条连接（最重要的参数）
      maximum-pool-size: 10
      # 池子最少保留多少条空闲连接（保活，避免冷启动）
      minimum-idle: 5
      # 拿连接最多等多久（毫秒），等不到就抛异常
      connection-timeout: 3000
      # 空闲连接超过这个时间（毫秒）就被回收，直到降到 minimum-idle
      idle-timeout: 600000
      # 一条连接最长存活时间（毫秒），到点强制销毁重建
      max-lifetime: 1800000
      # 连接拿出来用之前的存活探测查询（可选，新版可不配）
      connection-test-query: SELECT 1
```

逐个解释，重点理解前四个：

- **`maximumPoolSize`（最大连接数）**——池子的上限。这是性能和稳定性的核心旋钮。**注意：它不是越大越好。** 数据库本身能承受的连接数有限（MySQL 的 `max_connections` 默认就一两百），每个微服务实例都开一大池，几个实例一乘就把数据库连接数顶爆了。经验值：CPU 核数附近到 `核数 * 2 + 有效磁盘数` 这个量级，多数中小服务 `10~20` 就够。
- **`connectionTimeout`（获取连接超时）**——业务线程向池子要一条连接，如果池子里没有空闲连接、且已达上限，它就排队等。等满 `connectionTimeout` 还没拿到，HikariCP 直接抛异常（默认 30 秒，**强烈建议改小到 3~5 秒**，否则线程会被卡很久，雪崩更快）。
- **`idleTimeout`（空闲超时）**——一条连接闲置超过这个时间会被回收，但池子不会低于 `minimumIdle`。用来在流量低谷时释放多余连接。
- **`maxLifetime`（最大存活时间）**——再活跃的连接，存活到这个时长也会被优雅地销毁重建。这是为了躲开数据库 / 中间件那一侧的连接超时（比如 MySQL 的 `wait_timeout`、云上负载均衡的空闲断连）。**经验法则：`maxLifetime` 要比数据库侧的超时短几十秒**，否则会拿到一条已经被对端悄悄关掉的"僵尸连接"，报 `Connection is closed`。

> 💡 **前端类比**：`connectionTimeout` 约等于 axios 的 `timeout`——只不过它卡的不是"等响应"，而是"等一条空闲连接可用"。`maxLifetime` 则像主动给长连接设个有效期，到期换新，免得用到一条对面早就断了的连接。

## 13.4 池满了会发生什么（本章重点）

这是连接池最值得讲、也最容易踩坑的部分。先看一张图，理解"池满"是怎么一步步把接口拖垮的：

```text
maximumPoolSize = 10，此刻 10 条连接全被占用（比如都卡在慢查询上）

业务线程                         HikariCP 连接池
  req#11 ── 要连接 ──▶  ┌─────────────────────────────┐
  req#12 ── 要连接 ──▶  │  [busy][busy][busy]...x10    │  ← 满了，没有空闲
  req#13 ── 要连接 ──▶  │                             │
       ...             │   等待队列：#11 #12 #13 ...   │  ← 全在排队
                       └─────────────────────────────┘
                                  │
                  等满 connectionTimeout(3s) 仍拿不到
                                  ▼
       抛 SQLTransientConnectionException: Connection is not available
```

整个链条是这样的：

1. 某个原因让连接被长时间占着不还（最常见是**慢查询**或**慢下游**，下面细说）。
2. 池子被占满，`maximumPoolSize` 条连接全是 busy。
3. 新来的请求拿不到连接，进入等待队列。
4. 排队等满 `connectionTimeout`，HikariCP 抛异常，这个请求**直接失败**。
5. 因为请求都堆在"等连接"这一步，**接口大面积超时，但服务器 CPU 并不高**（线程都在等，不在算）——这个"高延迟 + 低 CPU"的组合是连接池打满的典型指纹。

### 两类最常见的根因

**根因 A：有连接被慢操作长期占用**

连接什么时候才还回池子？**事务/查询执行完才还。** 所以只要有慢查询、慢的下游调用，连接就一直被攥在手里。本项目里典型场景：

```java
@Service
public class TaskQueryService {

    // 反面例子：在持有数据库连接的事务里，又去调一个慢的外部服务
    @Transactional   // 注意：事务一开始就占住了一条连接
    public RtData<TaskVO> getTaskDetail(String taskId) {
        Task task = taskRepository.findById(taskId);   // 占住连接
        // 下面这一步调 svc-ai，如果 svc-ai 慢（比如 5 秒），
        // 这条数据库连接就被白白占用 5 秒——它根本没在查数据库！
        AiResultVO ai = aiFeignClient.queryResult(task.getAiJobId());
        return RtData.ok(merge(task, ai));
    }
}
```

这条连接整整 5 秒没干数据库的活，却一直不还池。并发一上来，池子立刻被这种"占着茅坑"的连接掏空。修法：**别在事务里调慢下游**，把外部调用挪到事务外；或者干脆缩短事务边界。

**根因 B：池设得太小，撑不住正常并发**

如果你的接口正常每个请求要持有连接 20ms，QPS 是 1000，那理论上同一时刻需要的连接数约为 `1000 * 0.02 = 20` 条。如果 `maximumPoolSize` 只配了 5，那就是天然不够用——稳定地排队、稳定地超时。这种是配置问题，不是 bug。

> ⚠️ 注意根因 A 和 B 的修法是相反的：A 是"有人占着不放"，调大池子只会拖死数据库、治标不治本，要去治慢操作；B 是"确实不够用"，才该适度调大池子。**判断到底是哪一类，靠的是看活跃连接数和慢查询，而不是拍脑袋。**

## 13.5 怎么从现象判断池被打满

> **症状**：svc-user 的多个接口突然大面积超时 / 报错，但机器 CPU、内存看着都正常，没有明显飙高。

按下面的顺序排查。完整的排查方法论和更系统的清单见 [排查手册](/back-end/frontend-backend-guide/28-troubleshooting-playbook)，这里给连接池这条专项链路。

**第 1 步：在日志里找 HikariCP 的超时异常**

```bash
# 在服务日志里搜连接池相关的异常关键字
grep -E "Connection is not available|HikariPool" /var/log/svc-user/app.log | tail -n 20
```

预期会看到类似这样的输出（这是 HikariCP 池满时的标志性日志）：

```text
2026-06-01 14:23:07.812 WARN  HikariPool-1 - Connection is not available, request timed out after 3001ms.
2026-06-01 14:23:07.815 ERROR c.x.user.controller.QuotaController -
  org.springframework.jdbc.CannotGetJdbcConnectionException: Failed to obtain JDBC Connection;
  nested exception is java.sql.SQLTransientConnectionException:
  HikariPool-1 - Connection is not available, request timed out after 3001ms.
```

**怎么读这段输出**：

- `Connection is not available, request timed out after 3001ms` —— 这句话基本是实锤：业务线程等了 3 秒（正好是我们配的 `connectionTimeout=3000`）也没拿到连接。这不是"数据库连接不上"，而是"池子里没有空闲连接给我用"。
- `SQLTransientConnectionException`（注意 **Transient** = 瞬时的）—— HikariCP 专门用这个异常类型表示"暂时拿不到连接"，区别于真正连不上数据库的 `SQLException`。看到 Transient，第一反应就该是池满。

如何把日志读出名堂、各字段什么含义，更系统的内容见 [读懂日志](/back-end/frontend-backend-guide/26-reading-logs)。

**第 2 步：看 HikariCP 的连接池状态日志**

HikariCP 默认会周期性打一行池状态日志，这是判断"池是不是真满了"最直接的证据：

```text
2026-06-01 14:23:05.001 DEBUG HikariPool-1 - Pool stats (total=10, active=10, idle=0, waiting=8)
```

**怎么读**：

- `total=10`：池里一共 10 条连接，等于 `maximumPoolSize`，说明池子已经扩到顶。
- `active=10`：10 条全在被使用（busy）。
- `idle=0`：**一条空闲都没有**。
- `waiting=8`：还有 8 个业务线程在排队等连接。

`active == total` 且 `idle=0` 且 `waiting > 0`——三个条件凑齐，**池被打满，证据确凿**。

> 如果你接了监控（见 [可观测性](/back-end/frontend-backend-guide/30-observability)），HikariCP 通过 Micrometer 暴露了 `hikaricp_connections_active`、`hikaricp_connections_pending` 等指标。把 active 和 max 画在一张图上，`active` 顶着 `max` 那条线贴着走、`pending` 不为 0，就是池满的可视化形态，比 grep 日志直观得多。

**第 3 步：定位是谁占着连接（区分根因 A / B）**

确认池满后，要回答"为什么满"。看数据库侧此刻在跑什么慢 SQL：

```sql
-- MySQL：看当前正在执行、且耗时较长的连接在干什么
SELECT id, user, time, state, LEFT(info, 80) AS sql_snippet
FROM information_schema.processlist
WHERE command = 'Query' AND time > 1
ORDER BY time DESC;
```

预期输出：

```text
+------+----------+------+--------------+----------------------------------------+
| id   | user     | time | state        | sql_snippet                            |
+------+----------+------+--------------+----------------------------------------+
| 8821 | svc_user | 6    | Sending data | SELECT * FROM generation_log WHERE ... |
| 8822 | svc_user | 6    | Sending data | SELECT * FROM generation_log WHERE ... |
| 8823 | svc_user | 5    | Sending data | SELECT * FROM generation_log WHERE ... |
+------+----------+------+--------------+----------------------------------------+
```

**怎么读**：好几条来自 `svc_user` 的查询 `time` 都到了 5~6 秒、卡在 `Sending data`，且 SQL 长一个样——这就是**慢查询占住连接**（根因 A）。下一步是去优化这条 SQL（多半是缺索引，见 [SQL 与索引](/back-end/frontend-backend-guide/10-sql-and-indexes)）。

反过来，如果 processlist 里几乎没有慢查询、SQL 都执行得飞快，但池照样满、`waiting` 居高不下，那更可能是**池太小**（根因 B），或连接被业务代码持有去做了别的慢事（比如根因 A 里那个事务内调 Feign 的例子）。

**结论**：

- 看到 `Connection is not available` + `active==max` + `idle=0` + `waiting>0` → 连接池被打满，这是接口超时的直接原因。
- 数据库侧有大量同款慢 SQL → 根因是慢查询，去加索引 / 优化 SQL，**不要无脑调大池子**。
- 数据库侧很清闲但池照样满 → 根因是池太小或连接被占去干慢活，调大池或把慢操作挪出事务/连接持有期。

## 13.6 把前端那点经验接上

你其实早就和"连接数上限 + 排队"打过交道，只是在浏览器里：

```text
HTTP/1.1：浏览器对【同一域名】最多并发约 6 条 TCP 连接
  请求1 ─┐
  请求2 ─┤
  ...   ├─▶ 6 条连接都在用
  请求6 ─┘
  请求7 ─── 在浏览器里排队，等前面某条空出来才发  ← 这就是"池满后排队"
```

这就是为什么以前会用"域名分片（domain sharding）"把静态资源拆到多个子域名——本质是**给浏览器多开几个连接池**。HikariCP 的 `maximumPoolSize` 打满后新请求排队、`connectionTimeout` 超时报错，和浏览器那 6 条连接占满后请求排队，是同一回事，只是搬到了后端、而且**你要自己负责调参和兜底**。

> 💡 一句话记忆：**连接池 = 后端版的"同域名并发连接数上限" + "连接复用"。** 浏览器替你管前者，HikariCP 要你自己管后者。

## 小结

- **连接很贵**：建立一条数据库/HTTP/Redis 连接要走 TCP 握手、可能还有 TLS 和认证，开销可达真正干活的几十上百倍，所以后端用"池"来复用而非每次新建。
- **到处都是池**：数据库连接池（HikariCP）、HTTP/Feign 连接池、Redis 连接池、线程池，背后都是"预创建昂贵资源 + 复用"这同一个思想。
- **四个关键参数**：`maximumPoolSize`（上限，非越大越好，受数据库 `max_connections` 制约）、`connectionTimeout`（等连接超时，建议改到 3~5 秒）、`idleTimeout`（空闲回收）、`maxLifetime`（强制重建，要短于数据库侧超时以避开僵尸连接）。
- **池满的指纹**：接口大面积超时但 CPU 不高；日志里 `Connection is not available ... request timed out`；池状态 `active==max & idle=0 & waiting>0`。
- **两类根因要分清**：慢查询/慢下游占着连接不放（治标是优化 SQL、把慢操作挪出事务，**不是调大池**）；以及池本身设得太小撑不住并发（才该适度调大）。

### 自测

1. 同事说"接口超时，把 `maximumPoolSize` 从 10 调到 100 试试"，你会先做什么判断？为什么盲目调大可能让情况更糟？
2. HikariCP 日志打出 `total=10, active=10, idle=0, waiting=12`，再加上一堆 `SQLTransientConnectionException`，你的初步结论是什么？接下来去哪里找"是谁占着连接"？
3. 为什么 `maxLifetime` 建议设得比数据库的 `wait_timeout` 短一些？如果设反了会出现什么报错？

### 下一章

连接池只是"复用昂贵资源"思想的一个实例，而后端真正绕不开的另一种昂贵资源是线程——下一章 [从单线程到多线程](/back-end/frontend-backend-guide/14-single-thread-to-multithread) 带你从前端最熟悉的单线程事件循环，过渡到后端"一个请求一个线程"的世界。

# 学习路径与时间表

> 后端代码量大、概念多，**不要试图一次全看完，也不要从头按页码硬啃**。
> 本章给你一条推荐主线、一张时间投入表，以及两条按目标裁剪的路线变体。配合一个对的学法，你能用最短的弯路把这门课跑通。

这门课一共 8 个 Part、38 章 + 4 个附录（地图见 [课程首页](/back-end/frontend-backend-guide/index)）。本章不教新概念，只回答一个问题：**这么多章，我该按什么顺序、花多少时间、用什么方法学？**

---

## 先记住四条学法（比顺序更重要）

很多前端转后端学不动，不是因为内容难，是因为**学法不对**——把后端当成「读一本书」而不是「跑一个系统」。下面四条贯穿全程，比任何章节顺序都重要。

**1. 先跑通一个链路，再回头补理论。** 不要先啃完 Java 语法和 JVM 再去看业务代码。正确顺序是：先把「登录」这一条链路从 `svc-gateway` 跟到 `svc-auth` 跟到数据库走一遍，让脑子里有一根完整的线，再去补线上每个点的细节。

> 前端类比：你学 React 也不是先背完 Hooks API 文档，而是先 `create-react-app` 跑起来一个能点的页面，再遇到问题查文档。后端一样——先有能跑的链路，再有知识点。

**2. 遇到什么查什么（按需检索，不要线性通读）。** Part 1、Part 2 像入门教程，顺着读；Part 3 往后更像查阅手册。看业务代码时遇到 `@Transactional` 就去翻 [第十一章](/back-end/frontend-backend-guide/11-transactions-consistency)，遇到 `RocketMQ` 就去翻 [第三十三章](/back-end/frontend-backend-guide/33-mq-reliability)。不认识的术语随手翻 [90 术语对照表](/back-end/frontend-backend-guide/90-glossary)，忘了的命令翻 [91 命令速查表](/back-end/frontend-backend-guide/91-command-cheatsheet)。

**3. 边看边加日志（这是前端读源码的"console.log 大法"在后端的版本）。** 看不懂一段逻辑走到哪、变量是什么，就在关键位置打一行日志，重启服务、发一个请求，看日志真实地打出来。

```java
// 在 svc-auth 的登录逻辑里临时加日志，确认走到了哪、拿到了什么
log.info("[login] 收到登录请求 phone={}, 开始查用户", dto.getPhone());
UserMst user = userRepository.findByLoginAndUserType(dto.getPhone(), "C");
log.info("[login] 查到用户 user={}, 准备调认证服务取 token", user);
RtData<TokenResp> token = authFeignClient.getToken(buildReq(user));
log.info("[login] 认证服务返回 code={}, msg={}", token.getCode(), token.getMessage());
```

```text
2026-06-01 14:22:03.110 INFO  [svc-auth] [http-nio-8081-exec-3] [login] 收到登录请求 phone=138****0001, 开始查用户
2026-06-01 14:22:03.142 INFO  [svc-auth] [http-nio-8081-exec-3] [login] 查到用户 user=UserMst(uid=10086, ...), 准备调认证服务取 token
2026-06-01 14:22:03.310 INFO  [svc-auth] [http-nio-8081-exec-3] [login] 认证服务返回 code=0, msg=success
```

**怎么读这段日志**：三行日志之间的时间差告诉你慢在哪（查用户 32ms、调认证服务 168ms，认证那一跳是大头）；`exec-3` 是处理这个请求的线程名，并发时用它把同一个请求的日志串起来。读日志的系统方法在 [第二十六章 读日志](/back-end/frontend-backend-guide/26-reading-logs)。

**4. 动手做 Part 8 的练习，别只看不练。** 后端的手感（连数据库、起容器、看监控、复现一次 OOM）只能练出来。看完一个 Part，就去 [37 动手练习集](/back-end/frontend-backend-guide/37-hands-on-exercises) 找对应练习做一遍；学到最后用 [38 毕业项目](/back-end/frontend-backend-guide/38-capstone-feature-to-prod) 把一个功能从需求做到上线。

---

## 推荐主线（按 Part 顺序，标出可跳读处）

下面是默认推荐顺序。**带「可跳读」标记的，是已有相应基础时可以快速过、用到再回来的部分。**

```text
Part1 建立认知  ──必读──▶  Part2 Java/Spring 地基  ──(有 Java 基础可跳读)──┐
                                                                          │
   ┌──────────────────────────────────────────────────────────────────────┘
   ▼
Part3 数据存储  ──▶  Part4 并发与 JVM  ──重点 慢读──▶  Part5 服务器/容器  ──动手──┐
                                                                              │
   ┌──────────────────────────────────────────────────────────────────────────┘
   ▼
Part6 排查/可观测  ──重点──▶  Part7 进阶工程(按需)  ──▶  Part8 综合实战 闯关
```

### Part 1 · 建立后端认知 —— 必读，不要跳

打通思维模型，看清一个请求从进到出经过了什么。这是全课地基，跳了后面处处别扭。
**学完你能做什么**：能看着 [架构图](/back-end/frontend-backend-guide/02-architecture-overview) 说清楚一个请求在 `svc-gateway → svc-auth → svc-user` 之间是怎么流转的，理解后端为什么要无状态、为什么要处处防下游出事。

### Part 2 · Java 与 Spring 上手 —— 有 Java 基础可跳读

从 TypeScript 视角速通 Java，理解 IoC/DI，亲手写一个 CRUD 接口。
**已经写过 Java/Spring 的**：本 Part 快速扫一遍 [08 注解速查](/back-end/frontend-backend-guide/08-annotations-cheatsheet) 对齐项目用法即可，语言细节按需深链到站内更细的专题：[Java 语法基础](/back-end/java/02-syntax-basics)、[集合](/back-end/java/04-collections)、[Lambda 与 Stream](/back-end/java/04a-lambda-stream)、[异常](/back-end/java/05-exception)、[Spring IoC/DI](/back-end/java/07a-spring-ioc-di)、[Spring CRUD](/back-end/java/07-spring-boot-crud)。
**学完你能做什么**：能独立写出一个带 Controller → Service → Repository 三层、返回 `RtData` 的 CRUD 接口。

### Part 3 · 数据存储

关系型 vs NoSQL 怎么选、SQL 与索引、事务一致性、Redis 实战、连接池。
**学完你能做什么**：能给一张慢查询表加对索引、用 `@Transactional` 把"扣配额 + 写记录"包成一个事务、用 Redis 做缓存和分布式锁。延伸专题：[关系型 vs NoSQL](/back-end/database/basics/relational-vs-nosql)、[MySQL 进阶](/back-end/database/mysql/advanced)、[Redis 入门](/back-end/database/redis/intro)。

### Part 4 · 并发与 JVM —— 重点，慢读

这是前端最陌生、最该补、也最容易在线上吃亏的一块：从单线程心智过渡到多线程，吃透线程池、锁、异步，再理解 JVM 内存与 GC、会排查 OOM。**不要快进**——这里的每个概念都建议配 Part 8 练习动手复现一遍。
**学完你能做什么**：能解释 `svc-canvas` 的线程池为什么打满、能判断一次 OOM 是内存泄漏还是堆配小了、能用 `CompletableFuture` 把几个下游调用并行起来。

### Part 5 · 服务器、容器与配置 —— 动手为主

会用 Linux 看进程看日志、看懂后端网络、把服务塞进 Docker 再交给 Kubernetes 编排、管好配置与环境变量。这一 Part **光看没用，必须敲命令**。
**学完你能做什么**：能 SSH 登上服务器定位 CPU 飙高的进程、能写一个 Dockerfile 把 `svc-user` 打成镜像、能在 K8s 里看一个 Pod 为什么 `CrashLoopBackOff`。

### Part 6 · 排查与可观测性 —— 重点，后端的核心手艺

读日志、建立排查方法论、积累问题处置手册、掌握诊断工具、搭起监控告警。这是把你和"只会写代码"的人区分开的能力。
**学完你能做什么**：线上一个接口变慢/报错，你能按一套固定方法论（看日志 → 看监控 → 抓 dump）定位到是哪个服务、哪一跳的问题，并说清楚根因。

### Part 7 · 进阶工程能力 —— 按需

性能与并发调优、安全、消息队列可靠性、API 设计、测试、CI/CD 部署。这些是按你当前工作重心**挑着看**的：做支付相关先看 [32 安全](/back-end/frontend-backend-guide/32-security)，被 MQ 重复消费坑过先看 [33 消息队列可靠性](/back-end/frontend-backend-guide/33-mq-reliability)。
**学完你能做什么**：能设计一个对外的 RESTful 接口、能保证 RocketMQ 消息不丢不重、能配一条把 `svc-ai` 自动构建部署到 K8s 的流水线。

### Part 8 · 综合实战与闯关 —— 必做

把前面所学串起来：先做 [37 动手练习集](/back-end/frontend-backend-guide/37-hands-on-exercises)，再用 [38 毕业项目](/back-end/frontend-backend-guide/38-capstone-feature-to-prod) 从零把一个功能开发到上线。
**学完你能做什么**：独立完成一个真实功能（如"给生图任务加优先级队列"）从需求拆解、写代码、连存储、加监控到部署上线的全流程——这就是后端日常。

---

## 各 Part 时间投入建议

下表沿用旧版学习路径的「章节 | 建议投入 | 产出」格式，扩展到新的 8 Part。**投入按"每天能挤出 1~2 小时"估算**，已有相关基础可对半砍。

| Part / 章节 | 建议投入 | 学完的产出（能交付什么） |
| --- | --- | --- |
| Part 1 建立认知（01-04） | 2~3 天 | 能口述一个请求在 svc-* 间的完整流转路径，画出系统架构图 |
| Part 2 Java/Spring（05-08） | 1~2 周（有 Java 基础 2~3 天） | 写出一个三层架构、返回 `RtData` 的 CRUD 接口 |
| Part 3 数据存储（09-13） | 1~1.5 周 | 会建索引、写事务、用 Redis 缓存与分布式锁 |
| **Part 4 并发与 JVM（14-20）** | **2~3 周（重点慢读）** | 能定位线程池打满、读懂 GC 日志、排查一次 OOM |
| Part 5 服务器/容器（21-25） | 1.5~2 周（边敲命令边学） | 能登服务器排障、写 Dockerfile、看懂 K8s Pod 状态 |
| **Part 6 排查/可观测（26-30）** | **2 周（重点）** | 能按方法论独立定位一个线上慢/错的接口根因 |
| Part 7 进阶工程（31-36） | 按需，每章 1~3 天 | 按工作重心挑学：API 设计 / 安全 / MQ / 测试 / CI-CD |
| Part 8 综合实战（37-38） | 1~2 周（动手） | 独立完成一个功能从需求到上线 |
| 附录（90-93） | 随时查 | 术语、命令、资源、路线随用随翻 |

> 全程系统学完大约 **8~12 周**（每天 1~2 小时）。重点在 Part 4 和 Part 6——它们最难、最值钱，宁可慢也别跳。Part 2、Part 7 视基础和需求可大幅压缩。

---

## 两条路线变体

同一份内容，不同目标走法不同。下面两条路线按目标裁剪，你对号入座。

### 变体 A：「我只想尽快看懂团队的后端项目」（快速路线，约 1~2 周）

目标不是学成后端，而是**能读懂同事的代码、能在评审里跟上、能改小 bug**。只取关键路径，跳过深水区。

```text
Part1(全读) → Part2(节选) → Part4(节选) → Part3(节选) → Part6(节选)
   认知地基      看懂语法注解   并发概念略懂   看懂数据访问   会看日志定位
```

按这个顺序看，每步只取必要的：

1. **Part 1 全读**（01-04）——必须建立认知，这是看懂任何后端项目的前提。
2. **Part 2 节选**——重点 [05 Java 速成](/back-end/frontend-backend-guide/05-java-crash-course) 和 [08 注解速查](/back-end/frontend-backend-guide/08-annotations-cheatsheet)，能看懂 `@RestController`、`@Service`、`@Autowired`、`@Transactional` 这些注解的意思即可，不必会写。
3. **Part 4 节选**——只读 [14 从单线程到多线程](/back-end/frontend-backend-guide/14-single-thread-to-multithread) 和 [15 线程池](/back-end/frontend-backend-guide/15-thread-pools-executor) 建立并发直觉，GC/OOM（18-20）先跳过。
4. **Part 3 节选**——读 [09 SQL vs NoSQL](/back-end/frontend-backend-guide/09-sql-vs-nosql) 知道项目里 MongoDB 和 MySQL 各管什么，Repository 方法名怎么读，够了。
5. **Part 6 节选**——读 [26 读日志](/back-end/frontend-backend-guide/26-reading-logs)，能从日志里看出请求走到哪、哪一跳出错，这是改 bug 的基本功。

配合学法第 1、2、3 条：挑项目里**最简单的一条链路（如登录）**，边读边加日志跟一遍，看不懂的术语随手翻 [90 术语对照表](/back-end/frontend-backend-guide/90-glossary)。一两周后你就能跟上团队的后端讨论了。

### 变体 B：「我要系统学成后端」（完整路线，约 8~12 周）

目标是**能独立开发并运维后端服务**。就按上面的「推荐主线」从 Part 1 到 Part 8 顺序走完，**一章都不跳**，重点章（Part 4、Part 6）慢读，每个 Part 配 Part 8 的练习动手。

```text
Part1 → Part2 → Part3 → Part4(慢) → Part5(练) → Part6(慢) → Part7 → Part8(闯关)
 必读    地基     数据     并发JVM      服务器       排查        进阶     实战上线
```

走完完整路线后，建议再回头看一遍 [38 毕业项目](/back-end/frontend-backend-guide/38-capstone-feature-to-prod)，独立做一个真实功能的全流程，把散落在各 Part 的知识真正串成一条线。想从更高处俯瞰 Spring 全家桶，可继续看 [Spring 全栈路线图](/back-end/spring-fullstack-roadmap)。

---

## 一份可执行的 8 周节奏表（完整路线参考）

如果你要的是「每周学什么」的具体节奏，按下表推进（每天 1~2 小时）。卡住了不要硬扛——降速、回头补前置、或先跳到能动手的练习上找手感。

| 周次 | 主攻 | 关键动作（动手为主） |
| --- | --- | --- |
| 第 1 周 | Part 1 + Part 2 起步 | 把登录链路跟通；写出第一个返回 `RtData` 的接口 |
| 第 2 周 | Part 2 收尾 + Part 3 起步 | CRUD 接口连上 MongoDB；理解 Repository 方法名规则 |
| 第 3 周 | Part 3 收尾 | 给慢查询加索引；用 `@Transactional` 包一个扣配额事务 |
| 第 4~5 周 | **Part 4（重点）** | 复现一次线程池打满；动手触发并分析一次 OOM |
| 第 6 周 | Part 5 | 写 Dockerfile 打镜像；在本地 K8s 跑通 `svc-user` |
| 第 7 周 | **Part 6（重点）** | 用方法论定位一个人为埋的慢接口；看懂监控曲线 |
| 第 8 周 | Part 7 节选 + Part 8 | 完成毕业项目：一个功能从需求到上线 |

---

## 小结

- **学法比顺序更重要**：先跑通一个链路、遇到什么查什么、边看边加日志、动手做 Part 8 练习。这四条贯穿全程。
- **推荐主线**按 Part 顺序走，但 Part 2（有 Java 基础）可跳读、Part 7 按需挑学；**Part 4（并发/JVM）和 Part 6（排查/可观测）是重点，要慢读**。
- 两条变体对号入座：只想**看懂团队项目**走快速路线（Part 1 全读 + 2/4/3/6 节选，约 1~2 周）；要**系统学成后端**走完整路线（Part 1→8 全走，约 8~12 周）。
- 时间投入表和 8 周节奏表给你具体节奏；卡住就降速、回补前置或先去找手感，别硬扛。

### 自测

1. 为什么推荐"先跑通一条链路再补理论"，而不是先把 Java 语法和 JVM 啃完？这和你当初学 React 的方式有什么相似？
2. "我只想尽快看懂团队后端项目"的快速路线里，为什么 Part 4 只取线程池、却跳过了 GC 和 OOM？什么情况下你该把它们补回来？
3. 这门课里哪两个 Part 被标为"重点、慢读"？为什么说它们最难也最值钱？

### 下一章

学习路线已经清楚，从 [01 后端思维转变](/back-end/frontend-backend-guide/01-backend-mindset) 出发，按你选定的路线正式开始；附录随时翻 [90 术语对照表](/back-end/frontend-backend-guide/90-glossary) 与 [91 命令速查表](/back-end/frontend-backend-guide/91-command-cheatsheet)。

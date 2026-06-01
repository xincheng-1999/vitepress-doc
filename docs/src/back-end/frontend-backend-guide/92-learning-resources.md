# 学习资源推荐

> 不要试图一次学完所有东西。先按本课程运行示例项目（AI 生图微服务，svc-gateway / svc-auth / svc-user / svc-ai / svc-canvas / svc-oss）的技术栈优先级来，**用到什么补什么**。

这一章是「资源地图」：告诉你每个方向**学什么、什么时候学、看哪些权威资料**。具体的时间安排和先后顺序在 [学习路径与时间表](/back-end/frontend-backend-guide/93-learning-path) 里，本章只管「往哪儿找料」。

> 前端类比：这就像你刚接触前端时收藏的那份「前端学习资源大全」——MDN、React 官方文档、各种小册。区别是后端的知识面更广（语言 + 数据库 + 操作系统 + 网络 + 运维），所以更需要**按需检索**，而不是从头读到尾。

挑资源的两条铁律：

1. **优先官方文档**。后端世界里官方文档普遍质量高、更新快，远胜二手教程。就像你写 React 时不会去看三年前的博客，而是直接翻 react.dev。
2. **能跑起来的优先**。带 `getting-started` / `guides` / playground 的资源，永远比纯文字教程值钱——后端很多概念（容器、并发、GC）光看不练等于没学。

---

## 一、必学（与运行项目直接相关）

这几样是你看懂、跑通示例项目的最低门槛，建议尽早过一遍。

### Java 语言基础

- **学什么**：基本语法、面向对象、集合（List/Map/Set）、异常处理、Lambda 与 Stream。
- **什么时候学**：现在。这是地基，但**不用一次学透**——先掌握能读懂代码的程度，细节随项目查。
- **前端类比**：相当于你当年学 TypeScript 的语法和类型系统，只是 Java 的类型更严格、没有结构化类型。
- **推荐资源**：
  - 本课程内置速成：[Java 快速上手](/back-end/frontend-backend-guide/05-java-crash-course)，专为前端读者写的。
  - 站内系统教程：[Java 语法基础](/back-end/java/02-syntax-basics)、[集合框架](/back-end/java/04-collections)、[Lambda 与 Stream](/back-end/java/04a-lambda-stream)、[异常处理](/back-end/java/05-exception)。
  - 官方：[Oracle Java Tutorials](https://docs.oracle.com/javase/tutorial/)（英文，当字典用，不必通读）。

### Spring Boot 基础

- **学什么**：注解驱动开发、IOC 容器与依赖注入、自动配置原理、`@RestController` / `@Service` / `@Repository` 分层。
- **什么时候学**：看懂示例项目任意一个 `svc-*` 服务的入口和 Controller 之前。
- **前端类比**：IOC 容器 ≈ 你在 Next.js / NestJS 里见过的依赖注入；注解 ≈ 装饰器（`@Controller` 之于 `@RestController`）。
- **推荐资源**：
  - 本课程：[Spring Boot 的 IOC 与 DI](/back-end/frontend-backend-guide/06-spring-boot-ioc-di)、[手写一个 CRUD 接口](/back-end/frontend-backend-guide/07-build-a-crud-api)、[注解速查表](/back-end/frontend-backend-guide/08-annotations-cheatsheet)。
  - 站内：[Spring Boot CRUD 实战](/back-end/java/07-spring-boot-crud)、[Spring IOC 与 DI](/back-end/java/07a-spring-ioc-di)、[Spring 配置](/back-end/java/07b-spring-config)。
  - 官方：[Spring Boot 官方指南](https://spring.io/guides)（大量可运行的 15 分钟小 demo）、[Spring Boot Reference](https://docs.spring.io/spring-boot/index.html)。

### Spring Cloud Gateway

- **学什么**：路由规则（routes）、断言（predicates）、过滤器链（filters）。
- **什么时候学**：当你想搞清楚 `svc-gateway` 怎么把 `/api/auth/**` 转发到 `svc-auth` 时。
- **前端类比**：和你在 Vite / Nginx 里配的 `proxy` 反向代理是一回事，只是用 YAML 表达规则。
- **推荐资源**：[Spring Cloud Gateway 官方文档](https://docs.spring.io/spring-cloud-gateway/reference/)。

### MongoDB + Spring Data

- **学什么**：文档数据库基本概念、Repository 模式、`MongoTemplate` 查询、索引。
- **什么时候学**：开始看 `svc-canvas` / `svc-user` 的 repository 层时。MongoDB 是示例项目的主库。
- **前端类比**：MongoDB 的文档 ≈ 你在前端直接存的 JSON 对象；Repository ≈ 一层封装好的「数据访问 hook」。
- **推荐资源**：
  - 站内：[Spring Data 操作数据库](/back-end/java/08-spring-data-db)、[关系型 vs NoSQL](/back-end/database/basics/relational-vs-nosql)。
  - 官方：[MongoDB 官方文档](https://www.mongodb.com/docs/)、[MongoDB University](https://learn.mongodb.com/)（免费课程）、[Spring Data MongoDB 参考](https://docs.spring.io/spring-data/mongodb/reference/)。

---

## 二、按需学（用到再看，别提前囤）

下面这些在示例项目里都用到了，但不是第一周的事。看到对应代码或排查到对应问题时再翻。

### MySQL 与索引

- **学什么**：SQL 基本语法、JOIN、事务、索引原理（B+ 树）、慢查询排查。示例项目用 MySQL 存统计数据。
- **什么时候学**：写到 `svc-user` 的支付/配额统计，或排查慢查询时。
- **推荐资源**：
  - 本课程：[SQL 与索引](/back-end/frontend-backend-guide/10-sql-and-indexes)、[事务与一致性](/back-end/frontend-backend-guide/11-transactions-consistency)。
  - 站内：[SQL 基础](/back-end/database/mysql/sql-basics)、[MySQL 进阶](/back-end/database/mysql/advanced)。
  - 官方：[MySQL 8.0 Reference Manual](https://dev.mysql.com/doc/refman/8.0/en/)。
  - 进阶书：《高性能 MySQL》（理解索引与执行计划的经典）。

### Redis

- **学什么**：基本数据类型（String/Hash/List/Set/ZSet）、缓存策略、限流、分布式锁。示例项目用它做缓存、限流和分布式锁。
- **什么时候学**：看到限流或缓存相关代码、或 `svc-canvas` 抢占任务锁时。
- **前端类比**：缓存命中/穿透的取舍，类似你在前端做 SWR / React Query 时考虑 staleTime 和回源。
- **推荐资源**：
  - 本课程：[Redis 实战](/back-end/frontend-backend-guide/12-redis-in-practice)。
  - 站内：[Redis 入门](/back-end/database/redis/intro)、[Java 操作 Redis](/back-end/database/redis/java-redis)。
  - 官方：[Redis 官方文档](https://redis.io/docs/latest/)、[Try Redis 在线练习](https://try.redis.io/)。

### RocketMQ

- **学什么**：Topic、Tag、Consumer Group、消息重试、顺序消息、幂等消费。示例项目用它把「提交生图任务」做成异步。
- **什么时候学**：理解 `svc-canvas` 提交任务后如何异步通知 `svc-ai` 时。
- **前端类比**：MQ ≈ 一个带持久化和重试的「事件总线」，比你在前端用过的 EventEmitter 可靠得多。
- **推荐资源**：
  - 本课程：[消息队列与可靠性](/back-end/frontend-backend-guide/33-mq-reliability)。
  - 官方：[Apache RocketMQ 官方文档](https://rocketmq.apache.org/docs/)。

### Docker

- **学什么**：镜像（image）、容器（container）、网络、`Dockerfile` 语法、`docker-compose` 一键起多服务。
- **什么时候学**：当你想在本地一次性把 MongoDB + MySQL + Redis + RocketMQ 都跑起来时。
- **前端类比**：镜像 ≈ 锁定版本的 `package-lock.json` + node_modules + 运行时，整个打包带走，彻底告别「在我机器上是好的」。
- **推荐资源**：
  - 本课程：[Docker 实战](/back-end/frontend-backend-guide/23-docker-in-practice)。
  - 官方：[Docker Get Started](https://docs.docker.com/get-started/)（强烈推荐，循序渐进且可跑）、[Dockerfile reference](https://docs.docker.com/reference/dockerfile/)、[Compose 文档](https://docs.docker.com/compose/)。

---

## 三、进阶 / 拉开差距（想真正学成后端必看）

这一组是前端转后端最容易跳过、却最决定上限的部分：服务器、网络、并发、JVM、运维可观测、安全。零基础时不用啃，但要知道它们在哪、什么时候该补。

### Linux 服务器基础

- **学什么**：文件与权限、进程、`top`/`ps`/`netstat`、日志位置、`systemd`、用 `vim`/`less` 看文件。
- **什么时候学**：第一次 SSH 到服务器、或第一次去线上「看日志」时。
- **前端类比**：你以前部署只需 `npm run build` 然后丢给 CI；现在你要会进到机器里自己排查，相当于把「黑盒」打开。
- **推荐资源**：
  - 本课程：[Linux 服务器必备技能](/back-end/frontend-backend-guide/21-linux-server-essentials)、[读日志](/back-end/frontend-backend-guide/26-reading-logs)。
  - 在线练习：[Linux Journey](https://linuxjourney.com/)、[Linux man-pages 在线手册](https://man7.org/linux/man-pages/)。
  - 经典书：《鸟哥的 Linux 私房菜（基础学习篇）》（中文，适合系统补基础）。

### 计算机网络（面向后端）

- **学什么**：TCP/IP、HTTP/HTTPS、TLS 握手、DNS、连接超时与重试、抓包看请求。
- **什么时候学**：排查「接口超时」「502 / 504」「跨服务调用失败」时，这些几乎都和网络分层有关。
- **前端类比**：你在 axios 里设过 `timeout`、在 Network 面板看过请求瀑布图——后端只是把这些下沉到 TCP 连接、连接池和服务间调用层面。
- **推荐资源**：
  - 本课程：[后端必备的网络知识](/back-end/frontend-backend-guide/22-networking-for-backend)。
  - 站内（前端视角的网络与安全）：[Web 安全基础](/front-end/the-basics/network-basics/webSafety)。
  - 经典书：《图解 HTTP》《图解 TCP/IP》（入门友好）、《计算机网络：自顶向下方法》（系统）。

### Java 并发（JUC）

- **学什么**：线程与线程池（`ThreadPoolExecutor`）、`synchronized` / `Lock`、`volatile`、`CompletableFuture`、并发集合（`ConcurrentHashMap`）、`AQS` 思想。
- **什么时候学**：当你发现一个请求一个线程、`svc-canvas` 要并发编排多个生图子任务、或排查到线程安全 bug 时。
- **前端类比**：JS 是单线程靠事件循环，你从没真正面对过「两个线程同时改一个变量」。这是前端转后端**思维转变最大**的一块，务必认真。
- **推荐资源**：
  - 本课程：[从单线程到多线程](/back-end/frontend-backend-guide/14-single-thread-to-multithread)、[线程池与 Executor](/back-end/frontend-backend-guide/15-thread-pools-executor)、[线程安全与锁](/back-end/frontend-backend-guide/16-thread-safety-locks)、[异步编程](/back-end/frontend-backend-guide/17-async-programming)。
  - 官方：[Java 官方并发教程](https://docs.oracle.com/javase/tutorial/essential/concurrency/)。
  - 经典书：**《Java 并发编程实战》（Java Concurrency in Practice）**——并发领域必读，把内存可见性、发布逸出讲透。

### JVM 内存模型与 GC

- **学什么**：JVM 内存分区（堆 / 栈 / 元空间）、对象生命周期、GC 算法（G1 / ZGC）、GC 日志、OOM 与内存泄漏定位。
- **什么时候学**：当服务出现内存持续上涨、频繁 Full GC、`OutOfMemoryError` 时。
- **前端类比**：你在 Chrome DevTools 里抓过 Heap Snapshot 找内存泄漏；JVM 是同一套思路，但工具链（jmap/MAT）和分代 GC 模型更复杂。
- **推荐资源**：
  - 本课程：[JVM 内存模型](/back-end/frontend-backend-guide/18-jvm-memory-model)、[垃圾回收](/back-end/frontend-backend-guide/19-garbage-collection)、[OOM 与内存泄漏](/back-end/frontend-backend-guide/20-oom-memory-leak)。
  - 经典书：**《深入理解 Java 虚拟机》（周志明）**——中文 JVM 书的事实标准，内存模型和 GC 讲得最清楚。
  - 在线诊断：**[Arthas 官方文档](https://arthas.aliyun.com/doc/)**——阿里开源的线上诊断神器，可在不重启的情况下看方法耗时、查内存、热更新，前端排查线上问题的第一把工具。

### Kubernetes（K8s）

- **学什么**：Pod / Deployment / Service / Ingress、`kubectl` 常用命令、滚动发布、看 Pod 日志与事件、配置（ConfigMap/Secret）。
- **什么时候学**：示例项目用 K8s 编排所有 `svc-*`；当你要部署、扩容或排查「Pod 一直重启」时。
- **前端类比**：K8s ≈ 把 docker-compose 升级成「集群版」，多了自愈、扩缩容、滚动发布——相当于运维版的状态管理器。
- **推荐资源**：
  - 本课程：[Kubernetes 实战](/back-end/frontend-backend-guide/24-kubernetes-in-practice)、[配置与环境](/back-end/frontend-backend-guide/25-config-and-env)。
  - 官方：[Kubernetes 官方文档](https://kubernetes.io/docs/home/)、[官方 Basics 交互教程](https://kubernetes.io/docs/tutorials/kubernetes-basics/)、**[kubectl Cheat Sheet](https://kubernetes.io/docs/reference/kubectl/cheatsheet/)**（命令速查，存书签）。
  - 进阶：[Kubernetes The Hard Way](https://github.com/kelseyhightower/kubernetes-the-hard-way)（想彻底搞懂原理再看，初学别碰）。

### 可观测性与 SRE

- **学什么**：Metrics / Logging / Tracing 三支柱、Prometheus 采集指标、Grafana 看板、链路追踪、告警与 SLO。
- **什么时候学**：当你不再满足于「出了问题去机器上看日志」，想主动监控服务健康度时。
- **前端类比**：相当于把前端的 Sentry + 性能监控 + 埋点，搬到服务端并标准化——只是后端更强调指标和分布式链路。
- **推荐资源**：
  - 本课程：[可观测性](/back-end/frontend-backend-guide/30-observability)、[排查方法论](/back-end/frontend-backend-guide/27-troubleshooting-methodology)、[诊断工具箱](/back-end/frontend-backend-guide/29-diagnostic-toolbox)。
  - 官方：[Prometheus 官方文档](https://prometheus.io/docs/introduction/overview/)、[Grafana 官方文档](https://grafana.com/docs/grafana/latest/)。
  - SRE 圣经（Google 免费在线）：[Google SRE Book](https://sre.google/sre-book/table-of-contents/)、[The Site Reliability Workbook](https://sre.google/workbook/table-of-contents/)。

### 安全

- **学什么**：常见 Web 漏洞（注入、XSS、CSRF、越权）、认证与授权、密钥管理、依赖漏洞。
- **什么时候学**：写 `svc-auth` 的登录、做接口鉴权、上线前做安全自查时。
- **前端类比**：XSS / CSRF 你在前端就接触过；后端要多管一层：服务端校验、权限边界、密钥不进代码库。
- **推荐资源**：
  - 本课程：[安全](/back-end/frontend-backend-guide/32-security)、[API 设计](/back-end/frontend-backend-guide/34-api-design)。
  - 站内：[Web 安全基础](/front-end/the-basics/network-basics/webSafety)。
  - 权威清单：**[OWASP Top 10](https://owasp.org/www-project-top-ten/)**（后端安全必读的「十大风险」）、[OWASP Cheat Sheet Series](https://cheatsheetseries.owasp.org/)（按主题的安全速查）。

---

## 四、推荐 / 不推荐的学法

后端知识面比前端宽得多，学法不对会事倍功半。下面这套是为「有项目可依托」的前端读者总结的。

### 推荐的学法

1. **先跑通一条链路**——不求全懂，先把一个接口从 `svc-gateway` 走到数据库再返回 `RtData.ok(data)` 跑通，建立全局感。
2. **遇到什么查什么**——看到 `@PostMapping` 就搜「Spring `@PostMapping`」，看到不懂的注解就翻 [注解速查表](/back-end/frontend-backend-guide/08-annotations-cheatsheet)。
3. **边看边加日志**——在关键路径加 `log.info()`，观察真实执行顺序。这是前端 `console.log` 的延续，照样好用。
4. **带着问题读官方文档**——别从第一页读到最后一页，而是「我要解决 X」→ 去文档搜 X。
5. **每个进阶概念都动手复现一次**——并发 bug、OOM、慢查询，亲手造一个再排查，比看十篇文章记得牢。
6. **画图辅助理解**——用 ASCII 框图或在线工具画调用链路、线程模型，画得出来才算懂。

### 不推荐的学法

1. **试图先把 Java / Spring 全部学完再看项目**——太慢，边看项目边查效率高得多。
2. **只看文档不动手**——容器、并发、GC 这类，光看等于没看。
3. **一上来就啃源码 / 底层原理**——AOP 字节码、AQS 源码、ZGC 实现细节，等你有手感了再回头看，否则只会劝退。
4. **囤教程不消化**——收藏夹吃灰是后端学习最大的陷阱；只在「真的要用」时打开资源。
5. **照搬前端经验硬套**——尤其是并发模型（JS 单线程 → JVM 多线程），不转过弯来一定会踩坑。

---

## 五、时间投入建议（资源视角）

详细的逐章时间表见 [学习路径与时间表](/back-end/frontend-backend-guide/93-learning-path)，这里只给「在每个方向上花多少时间挖资源」的粗略建议，避免你在某一块陷太深。

| 方向 | 投入定位 | 资源使用建议 |
| --- | --- | --- |
| Java + Spring Boot 基础 | 重投入（地基） | 速成 + 站内教程过一遍，细节随项目查 |
| MongoDB / Redis / MySQL | 中投入 | 各看一遍官方 getting-started + 本课程对应章 |
| Docker | 中投入 | 跟着官方 Get Started 全程敲一遍 |
| RocketMQ | 轻投入 | 只读官方核心概念，能看懂异步流程即可 |
| Linux / 网络 | 持续补 | 不集中学，按排查问题时遇到的命令逐个查 |
| Java 并发 | 重投入（决定上限） | 本课程 14~17 章 + 《Java 并发编程实战》精读 |
| JVM / GC | 中投入（按需爆发） | 平时了解模型，出 OOM/Full GC 时配《深入理解 Java 虚拟机》+ Arthas 深挖 |
| K8s | 中投入 | 官方 Basics + kubectl cheatsheet，会看 Pod 日志和事件即可 |
| 可观测性 / SRE | 轻投入起步 | 先会看 Grafana 面板，行有余力读 SRE Book |
| 安全 | 轻投入但别跳过 | 通读一遍 OWASP Top 10，上线前对照自查 |

> 核心心态：**别等「学好了」再开干。后端是「在排查真实问题中学成的」，资源是你随时调用的工具箱，不是要背完的教科书。**

---

## 小结

- 挑资源两条铁律：**官方文档优先**、**能跑起来的优先**；后端官方文档质量普遍高于二手教程。
- 学习节奏：**必学**（Java/Spring/Mongo）尽早过一遍 → **按需学**（MySQL/Redis/RocketMQ/Docker）用到再补 → **进阶**（Linux/网络/并发/JVM/K8s/可观测/安全）拉开与「只会写 CRUD」的差距。
- 三本经典值得正式精读：并发看《Java 并发编程实战》，JVM 看《深入理解 Java 虚拟机》，运维心法看 Google SRE Book。
- 三类「线上必备」资源存书签：Arthas 文档（诊断）、kubectl Cheat Sheet（K8s 操作）、OWASP Top 10（安全自查）。
- 最忌「囤教程不动手、想学全再开干」；后端是在排查真实问题中学成的。

### 自测

1. 同样要解决一个「接口偶发超时」的问题，你会优先翻本章里的哪几个方向的资源？为什么？
2. 《Java 并发编程实战》和《深入理解 Java 虚拟机》分别解决你在示例项目里的什么类型问题？各举一个场景。
3. 为什么本章把 Linux 和网络归到「持续补、按需查」而不是「集中学完」？这背后是什么学习理念？

### 下一章

带着这份资源地图，去看 [学习路径与时间表](/back-end/frontend-backend-guide/93-learning-path)，把这些资源安排进一条可执行的周计划里。

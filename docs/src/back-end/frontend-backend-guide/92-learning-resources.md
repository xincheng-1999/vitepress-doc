# 学习资源推荐

遇到不熟悉的注解、查不明白的慢 SQL，或看不懂的容器状态，再来这里找资料。不需要先读完一套资源才能继续写接口；先明确当前要解决的问题，再选一两个入口。

本页按问题组织，不是必装清单。[五阶段主线](./index) 先用 Java 17、Spring Boot 3.x 与 H2 跑通 task-api 接口，再在持久化阶段迁移到 MySQL；MongoDB、网关、消息队列、Kubernetes 只有在自己的项目确实使用时再查。仓库没有另外一套 `svc-*` 工程需要寻找，具体学习顺序仍可回到 [学习路径](/back-end/frontend-backend-guide/93-learning-path)。

## 1. Java 代码读不懂：先查当前语法与类型

读到集合、异常或 Lambda 卡住时，先对照站内的小例子，再查官方定义。不要为了看懂一个方法，先去读 JVM 实现。

| 当前问题 | 查阅入口 | 为什么查这里 |
| --- | --- | --- |
| TypeScript 经验怎样对应 Java | [Java 快速上手](/back-end/frontend-backend-guide/05-java-crash-course) | 先对齐语法与类型差异，适合回到手头代码继续读 |
| 类、方法、基本语法不熟 | [Java 语法基础](/back-end/java/02-syntax-basics) | 可以按语法点补，不要求按整套教程顺序重学 |
| List、Map、Set 怎样选 | [集合框架](/back-end/java/04-collections) | 区分操作语义，再决定数据结构 |
| Stream 链式处理看不懂 | [Lambda 与 Stream](/back-end/java/04a-lambda-stream) | 把表达式拆回输入、处理和输出 |
| catch、throw 与异常传播不清楚 | [异常处理](/back-end/java/05-exception) | 有助于读异常栈，也避免把所有失败统一吞掉 |

[Oracle Java Tutorials](https://docs.oracle.com/javase/tutorial/) 仍可用于查许多基础概念，但内容主要面向 JDK 8，不能作为 Java 17 新特性的完整说明。查询语言与 API 时应同时核对使用版本；较新的入门入口可以看 [dev.java](https://dev.java/learn/)。找到用法后，用一个最小例子确认自己的理解，不直接把新版本示例复制进旧运行环境。

## 2. 接口为什么这样运行：查 Spring，而不是猜注解

例如 `@Service` 的对象从哪里来、Controller 为什么能收到请求，先查容器与 Web 的对应部分。如果只是忘记某个注解的用途，速查即可，不必先读自动配置源码。

| 当前问题 | 查阅入口 | 为什么查这里 |
| --- | --- | --- |
| 对象怎样被创建、依赖怎样注入 | [Spring Boot 的 IOC 与 DI](/back-end/frontend-backend-guide/06-spring-boot-ioc-di)、[Spring IOC 与 DI](/back-end/java/07a-spring-ioc-di) | 先建立实例与依赖关系，再判断扫描和注入问题 |
| 想写一个最小接口 | [手写 CRUD 接口](/back-end/frontend-backend-guide/07-build-a-crud-api)、[Spring Boot CRUD](/back-end/java/07-spring-boot-crud) | 从可见的请求和响应入手，不依赖虚构微服务 |
| 只是不记得某个注解 | [注解速查表](/back-end/frontend-backend-guide/08-annotations-cheatsheet) | 按用途回查，再确认所在层与生效条件 |
| 属性到底从哪个环境取得 | [Spring 配置](/back-end/java/07b-spring-config) | 适合查 profile、配置来源与覆盖关系 |
| 想看官方的小型示例 | [Spring Guides](https://spring.io/guides) | 挑与问题相符的 guide，先核对它的依赖和版本 |
| 需要确认精确配置与行为 | [Spring Boot Reference](https://docs.spring.io/spring-boot/index.html) | 参考手册适合按属性和章节检索；选择与项目匹配的版本 |

只有项目确实用了 Spring Cloud Gateway，再看 [Gateway 官方文档](https://docs.spring.io/spring-cloud-gateway/reference/)。用它核对路由、断言、过滤器和运行栈，不把它当成单体接口的前置依赖；Spring Boot 与 Spring Cloud 的版本也要匹配。

## 3. 查询慢、数据不一致：按实际数据库查

先确认自己连接的是哪一种数据库，以及当前版本与驱动。主线在持久化阶段使用 MySQL，并不因为旧专题讲过 MongoDB，就要求另外安装一套主库。

### MySQL：从实际 SQL 和执行计划开始

入门语法可查 [SQL 基础](/back-end/database/mysql/sql-basics)，应用中的查询与索引问题看 [SQL 与索引](/back-end/frontend-backend-guide/10-sql-and-indexes)。已经遇到事务或并发写入，再看 [事务与一致性](/back-end/frontend-backend-guide/11-transactions-consistency) 与 [MySQL 进阶](/back-end/database/mysql/advanced)。

需要确认隔离级别、索引行为和命令细节时，查 [MySQL 8.0 Reference Manual](https://dev.mysql.com/doc/refman/8.0/en/)，使用其他版本则切换到对应手册。《高性能 MySQL》适合在已经有执行计划、查询负载等问题后系统理解取舍，阅读时注意书中版本与当前实例的差异，不把旧参数建议直接搬到生产。

### MongoDB：项目确实用了文档库时再看

先用 [关系型 vs NoSQL](/back-end/database/basics/relational-vs-nosql) 对齐数据模型，再按需看 [Spring Data 操作数据库](/back-end/java/08-spring-data-db)。Spring Data 的 Repository 形式相近，不代表底层查询、事务与索引行为也相同。

[MongoDB 官方文档](https://www.mongodb.com/docs/) 适合查查询与索引；[Spring Data MongoDB Reference](https://docs.spring.io/spring-data/mongodb/reference/) 用于确认 Java 映射和访问方式。[MongoDB University](https://learn.mongodb.com/) 更适合需要系统熟悉文档库的人，不是只读一个 Repository 方法前必须完成的课程。

### Redis：先查当前命令与数据类型

缓存策略可先看 [Redis 实战](/back-end/frontend-backend-guide/12-redis-in-practice)，数据类型和 Java 客户端分别查 [Redis 入门](/back-end/database/redis/intro) 与 [Java 操作 Redis](/back-end/database/redis/java-redis)。遇到 TTL、SCAN 或原子操作的问题，再去 [Redis 官方文档](https://redis.io/docs/latest/) 核对语义、版本和复杂度。

[Try Redis](https://try.redis.io/) 是在线练习入口，是否可用及支持哪些命令以当时页面为准，只适合合成数据。不要在公共练习平台输入生产地址、密码或真实业务内容，也不把它的响应时间当成自己的系统性能。

## 4. 后台任务为什么重复或没完成：查消息处理边界

先看 [消息队列与可靠性](/back-end/frontend-backend-guide/33-mq-reliability)，区分发送确认、消费确认、重试和业务幂等。它们不是同一件事；看到一条“发送成功”日志，还不能说明任务已经执行完成。

项目使用 RocketMQ 时，再查 [Apache RocketMQ 官方文档](https://rocketmq.apache.org/docs/)，按实际客户端、Broker 版本和部署模式选章节。重点查当前问题涉及的投递与重试语义，不必为了理解异步就先部署整套集群。

## 5. 本地能跑，容器或服务器却不行：先查运行位置

| 当前问题 | 查阅入口 | 为什么查这里 |
| --- | --- | --- |
| 不清楚镜像与容器的区别 | [Docker 实战](/back-end/frontend-backend-guide/23-docker-in-practice)、[Docker Get Started](https://docs.docker.com/get-started/) | 用小应用看构建、运行和端口映射，不先搬完整业务环境 |
| Dockerfile 某个指令的行为不确定 | [Dockerfile Reference](https://docs.docker.com/reference/dockerfile/) | 查构建上下文、COPY、ENTRYPOINT 等具体规则 |
| 多个本地依赖怎样启动与连接 | [Compose 文档](https://docs.docker.com/compose/) | 核对项目、网络、变量与就绪条件；启动顺序不等于就绪 |
| 第一次登录 Linux，不知道日志在哪 | [Linux 服务器必备技能](/back-end/frontend-backend-guide/21-linux-server-essentials)、[读日志](/back-end/frontend-backend-guide/26-reading-logs) | 先看环境、进程和具体日志，不先尝试重启 |
| 忘记系统命令参数 | [Linux man-pages](https://man7.org/linux/man-pages/) | 按命令或系统接口检索，注意发行版和版本差异 |
| 想有顺序地补 Linux 基础 | [Linux Journey](https://linuxjourney.com/)、《鸟哥的 Linux 私房菜》 | 适合在常用操作有印象后补文件、权限、进程与网络模型 |

若问题是超时、502/504、域名或 TLS，再从 [面向后端的网络知识](/back-end/frontend-backend-guide/22-networking-for-backend) 区分连接在哪一层失败。想系统建立直觉可选《图解 HTTP》《图解 TCP/IP》，需要更完整的网络模型再读《计算机网络：自顶向下方法》。浏览器侧限制与安全问题可结合 [Web 安全基础](/front-end/the-basics/network-basics/webSafety) 查看，不把服务器 curl 成功等同于浏览器一定能成功。

### 已经用上 Kubernetes，再查这一组

先看 [Kubernetes 实战](/back-end/frontend-backend-guide/24-kubernetes-in-practice) 中的状态、事件、日志和 Service，再配合 [配置与环境](/back-end/frontend-backend-guide/25-config-and-env) 理解配置注入。

[Kubernetes 官方文档](https://kubernetes.io/docs/home/) 用于确认对象与版本行为；[Basics 教程](https://kubernetes.io/docs/tutorials/kubernetes-basics/) 适合在独立实验环境理解部署过程，不保证当前页面仍提供旧式浏览器交互环境。[kubectl Cheat Sheet](https://kubernetes.io/docs/reference/kubectl/cheatsheet/) 适合查命令格式，但查询与修改命令混在其中，执行前仍需确认 context、命名空间和授权。

[Kubernetes The Hard Way](https://github.com/kelseyhightower/kubernetes-the-hard-way) 面向愿意自行搭建并理解控制面组件的人，有环境与资源成本。仅仅要查一个 Pod 为什么重启，不需要先完成它，更不应拿公司的集群作实验。

## 6. CPU、线程或内存有异常：带着观测结果查 JVM

如果只是需要多线程直觉，可先看 [从单线程到多线程](/back-end/frontend-backend-guide/14-single-thread-to-multithread)。遇到任务排队查 [线程池与 Executor](/back-end/frontend-backend-guide/15-thread-pools-executor)，遇到共享数据错误查 [线程安全与锁](/back-end/frontend-backend-guide/16-thread-safety-locks)，遇到异步等待查 [异步编程](/back-end/frontend-backend-guide/17-async-programming)。

[Java 官方并发教程](https://docs.oracle.com/javase/tutorial/essential/concurrency/) 可补基础，但主要面向 JDK 8；《Java 并发编程实战》适合系统理解可见性、安全发布与线程安全原则，不能代替新版本 API 手册，也不是虚拟线程的完整指南。

内存问题先对照 [JVM 内存模型](/back-end/frontend-backend-guide/18-jvm-memory-model)、[垃圾回收](/back-end/frontend-backend-guide/19-garbage-collection) 与 [OOM 与内存泄漏](/back-end/frontend-backend-guide/20-oom-memory-leak)，分清堆、堆外与容器限制。《深入理解 Java 虚拟机》适合在已有 GC 日志或对象持有问题后补原理，书中收集器与参数仍需按实际 JDK 核对。

需要 attach 诊断时，查 [Arthas 官方文档](https://arthas.aliyun.com/doc/) 的具体命令、权限和影响说明，再对照 [诊断工具箱](/back-end/frontend-backend-guide/29-diagnostic-toolbox)。能查看方法耗时不代表零开销，能执行运行时表达式更不代表可以任意热更新生产代码；使用前要明确范围和数据隐私。

## 7. 想更早发现问题，或检查安全边界

已有一次故障，想知道缺了哪些监控，可先看 [可观测性](/back-end/frontend-backend-guide/30-observability) 和 [排查方法论](/back-end/frontend-backend-guide/27-troubleshooting-methodology)。再查 [Prometheus 官方文档](https://prometheus.io/docs/introduction/overview/) 中的指标类型、查询与告警，以及 [Grafana 官方文档](https://grafana.com/docs/grafana/latest/) 中的数据源与看板功能，不必先搭全套平台。

[Google SRE Book](https://sre.google/sre-book/table-of-contents/) 适合在要定义可靠性目标、处理告警噪声时选读相关章节；[The Site Reliability Workbook](https://sre.google/workbook/table-of-contents/) 提供更具体的实践讨论。先找与当前服务规模和责任相符的做法，不把大规模平台的组织方式原样搬进小项目。

做登录、权限或接口发布时，看 [安全](/back-end/frontend-backend-guide/32-security) 与 [API 设计](/back-end/frontend-backend-guide/34-api-design)。[OWASP Top 10](https://owasp.org/www-project-top-ten/) 用于认识常见风险类别，不是完整验收标准；要落实某个主题的防护，再查 [OWASP Cheat Sheet Series](https://cheatsheetseries.owasp.org/)，例如认证、会话、输入校验与密钥管理。

查完资料后，回到原问题说明“采用了哪种做法，观察到什么，还存在哪些边界”。能完成这一步，就不必继续为了收藏而扩充资源；下一次遇到新问题，再回来选入口。

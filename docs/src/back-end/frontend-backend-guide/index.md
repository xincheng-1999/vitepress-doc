# 前端转后端完整课程

> 一份完整、独立、实操优先的「前端 → 后端」课程。
> 不只教你看懂后端代码，更教你管服务器、玩容器、理解并发、读日志、排查线上问题——真正学成后端开发。
> 全程用你已经会的 React / Vue / Next.js / TypeScript / Node / axios / Promise / zod 来类比。

---

## 这门课在教什么

很多「前端学后端」的教程止步于「看懂 Controller 和 Service」，但真实的后端工作远不止于此：你要会登服务器看进程、会用 Docker 跑容器、会在 Kubernetes 里查 Pod 为什么重启、会从一堆日志里定位是哪个下游服务超时、会判断一次 OOM 是内存泄漏还是配置太小。

这门课的目标是把你从「能读后端代码」带到「能独立开发并运维后端服务」。它是**自洽的**——从「后端思维」一直讲到「把一个功能上线到生产」，中间不假设你已经会 Linux、会容器、会并发。

每一个后端概念，我们都尽量配一句**前端类比**。例如：Spring 的依赖注入 ≈ React 的 Context/Provider 自动把依赖塞给你；Feign 声明式客户端 ≈ 你写一个 TypeScript interface，axios 自动按它发请求；线程池 ≈ 一个固定并发数的 Promise 任务队列。类比是脚手架，帮你快速上车，但我们也会讲清楚类比在哪里会「漏」——因为后端的世界和浏览器真的不一样。

## 适用读者

- 有 **1 年以上前端经验**，熟悉 React / Vue / Next.js、TypeScript、Node、axios、Promise、zod。
- **后端零基础**或只了解皮毛——没写过 Java，没碰过 Spring，没登过服务器也没关系。
- 目标是**真正能干后端活**：写接口、连数据库、处理并发、排查线上问题，而不只是「看个大概」。

## 运行示例：一套 AI 生图微服务

全程我们用同一个真实风格的项目来举例，避免你在抽象概念里打转。它是一套 **Java 17 + Spring Cloud** 的 AI 生图微服务：

```text
                       ┌────────────┐
       client ───────▶ │ svc-gateway│  网关：路由 + 鉴权 + 限流
                       └─────┬──────┘
            ┌────────────────┼────────────────┬─────────────┐
            ▼                ▼                 ▼             ▼
       ┌─────────┐     ┌──────────┐      ┌─────────┐   ┌─────────┐
       │svc-auth │     │ svc-user │      │ svc-ai  │   │svc-oss  │
       │  认证   │     │用户/配额 │      │ AI 生图 │   │文件存储 │
       └─────────┘     └──────────┘      └────┬────┘   └─────────┘
                            ▲                 ▲
                            │                 │
                       ┌────┴─────────────────┴────┐
                       │      svc-canvas           │  画布/任务编排（最复杂）
                       └───────────────────────────┘

  共享组件: cpt-api(Feign+DTO) · cpt-common(RtData/异常码/工具)
            cpt-mongodb · cpt-mysql · cpt-redis · cpt-rocketmq · cpt-xxljob
  基础设施: MongoDB(主库) · MySQL(统计) · Redis(缓存/限流/锁)
            RocketMQ(异步) · 云 OSS · Docker / Kubernetes
```

统一响应类型叫 `RtData`，整个项目的接口都返回它（成功 `RtData.ok(data)`、失败 `RtData.fail(msg)`）——你可以把它理解成前端约定好的 `{ code, message, data }` 响应壳。后面所有举例（登录、扣配额、提交生图任务、轮询任务状态）都发生在这套服务里。

---

## 前后端思维大转变

学后端最大的坎不是语法，是**思维模型**。下面 5 条对照是这门课的「世界观」，每条都点名后续哪一部分会深入。

**1. 单线程事件循环 → 多线程并发。** 浏览器里只有一个主线程，`await` 一下任务自己排队，你几乎不用担心两段代码同时改一个变量。后端一个服务会用**线程池**同时处理成百上千个请求，多个线程可能**同时**读写同一份数据——这就有了竞态、锁、线程安全这些前端几乎不存在的问题。详见 [第十四章 从单线程到多线程](/back-end/frontend-backend-guide/14-single-thread-to-multithread)、[第十五章 线程池](/back-end/frontend-backend-guide/15-thread-pools-executor)、[第十六章 线程安全与锁](/back-end/frontend-backend-guide/16-thread-safety-locks)。

**2. 有状态的浏览器标签页 → 无状态、可随时重启的服务。** 前端组件里 `useState` 存着用户状态，刷新才丢。后端服务被设计成**无状态**的：一个请求过来该带的身份信息（token）自己带齐，因为同一个用户的两次请求可能被路由到不同实例，而任何实例都可能在发版或扩缩容时被随时杀掉重启。状态要外置到 Redis / 数据库。详见 [第二章 架构总览](/back-end/frontend-backend-guide/02-architecture-overview)、[第十二章 Redis 实战](/back-end/frontend-backend-guide/12-redis-in-practice)。

**3. 构建完就结束 → 7×24 长跑会累积问题。** 前端 `build` 出一堆静态文件就交付了，跑在用户机器上，你管不着也不用管。后端进程要**连续运行几周几个月**，内存会慢慢涨、连接会泄漏、慢查询会拖垮数据库——时间维度上的问题是后端独有的。详见 [第十八章 JVM 内存模型](/back-end/frontend-backend-guide/18-jvm-memory-model)、[第十九章 垃圾回收](/back-end/frontend-backend-guide/19-garbage-collection)、[第二十章 OOM 与内存泄漏](/back-end/frontend-backend-guide/20-oom-memory-leak)。

**4. 出错看浏览器 Console → 看服务器日志 / 监控 / dump。** 前端报错，F12 打开 Console 一眼看到红色堆栈。后端的「Console」在远端服务器上，你得 SSH 登上去（或在 K8s 里）翻日志文件、看监控曲线、必要时抓线程 dump 和堆 dump 来分析。这是一整套新的排查手艺。详见 [第二十六章 读日志](/back-end/frontend-backend-guide/26-reading-logs)、[第二十七章 排查方法论](/back-end/frontend-backend-guide/27-troubleshooting-methodology)、[第二十九章 诊断工具箱](/back-end/frontend-backend-guide/29-diagnostic-toolbox)、[第三十章 可观测性](/back-end/frontend-backend-guide/30-observability)。

**5. 自己掌控 UI → 依赖一堆下游，要考虑超时 / 重试 / 降级。** 前端基本只依赖一个后端 API。而 `svc-canvas` 提交一次生图，背后要调 `svc-user` 扣配额、调 `svc-ai` 排任务、发 RocketMQ 消息、写 MongoDB、回写 Redis——任何一环都可能慢、可能挂。后端必须默认「下游随时会出事」，处处考虑超时、重试、熔断、降级。详见 [第三章 一个请求的完整生命周期](/back-end/frontend-backend-guide/03-request-lifecycle)、[第三十三章 消息队列可靠性](/back-end/frontend-backend-guide/33-mq-reliability)、[第三十一章 性能与并发](/back-end/frontend-backend-guide/31-performance-concurrency)。

> 一句话总结：前端是「一个人在一台浏览器里跑一会儿」，后端是「很多人同时在一群随时会重启的机器上长跑」。这门课的大半篇幅都在教你应对这个区别。

---

## 完整学习地图（8 个 Part）

### Part 1 · 建立后端认知（先看这部分）

打通思维模型，理解一个请求从进到出经过了什么。

- [01 后端思维转变](/back-end/frontend-backend-guide/01-backend-mindset)
- [02 架构总览](/back-end/frontend-backend-guide/02-architecture-overview)
- [03 一个请求的完整生命周期](/back-end/frontend-backend-guide/03-request-lifecycle)
- [04 三层架构与项目结构](/back-end/frontend-backend-guide/04-three-layer-and-structure)

### Part 2 · Java 与 Spring 上手

从 TypeScript 视角速通 Java，理解 Spring 的 IoC/DI，亲手写一个 CRUD 接口。

- [05 Java 速成（给前端）](/back-end/frontend-backend-guide/05-java-crash-course)
- [06 Spring Boot 的 IoC 与 DI](/back-end/frontend-backend-guide/06-spring-boot-ioc-di)
- [07 动手写一个 CRUD API](/back-end/frontend-backend-guide/07-build-a-crud-api)
- [08 常用注解速查](/back-end/frontend-backend-guide/08-annotations-cheatsheet)

### Part 3 · 数据存储

关系型 vs NoSQL 怎么选，SQL 与索引，事务一致性，Redis 实战，连接池。

- [09 SQL vs NoSQL](/back-end/frontend-backend-guide/09-sql-vs-nosql)
- [10 SQL 与索引](/back-end/frontend-backend-guide/10-sql-and-indexes)
- [11 事务与一致性](/back-end/frontend-backend-guide/11-transactions-consistency)
- [12 Redis 实战](/back-end/frontend-backend-guide/12-redis-in-practice)
- [13 连接池](/back-end/frontend-backend-guide/13-connection-pools)

### Part 4 · 并发与 JVM（前端最陌生、最该补的一块）

从单线程心智过渡到多线程，吃透线程池、锁、异步，再理解 JVM 内存与 GC、排查 OOM。

- [14 从单线程到多线程](/back-end/frontend-backend-guide/14-single-thread-to-multithread)
- [15 线程池与 Executor](/back-end/frontend-backend-guide/15-thread-pools-executor)
- [16 线程安全与锁](/back-end/frontend-backend-guide/16-thread-safety-locks)
- [17 异步编程](/back-end/frontend-backend-guide/17-async-programming)
- [18 JVM 内存模型](/back-end/frontend-backend-guide/18-jvm-memory-model)
- [19 垃圾回收](/back-end/frontend-backend-guide/19-garbage-collection)
- [20 OOM 与内存泄漏](/back-end/frontend-backend-guide/20-oom-memory-leak)

### Part 5 · 服务器、容器与配置

会用 Linux、看懂网络、把服务塞进 Docker 再交给 Kubernetes 编排，管好配置与环境变量。

- [21 Linux 服务器必备](/back-end/frontend-backend-guide/21-linux-server-essentials)
- [22 后端必懂的网络](/back-end/frontend-backend-guide/22-networking-for-backend)
- [23 Docker 实战](/back-end/frontend-backend-guide/23-docker-in-practice)
- [24 Kubernetes 实战](/back-end/frontend-backend-guide/24-kubernetes-in-practice)
- [25 配置与环境管理](/back-end/frontend-backend-guide/25-config-and-env)

### Part 6 · 排查与可观测性（后端的核心手艺）

读日志、建立排查方法论、积累问题处置手册、掌握诊断工具、搭起监控告警。

- [26 读日志](/back-end/frontend-backend-guide/26-reading-logs)
- [27 排查方法论](/back-end/frontend-backend-guide/27-troubleshooting-methodology)
- [28 排查实战手册](/back-end/frontend-backend-guide/28-troubleshooting-playbook)
- [29 诊断工具箱](/back-end/frontend-backend-guide/29-diagnostic-toolbox)
- [30 可观测性](/back-end/frontend-backend-guide/30-observability)

### Part 7 · 进阶工程能力

性能与并发调优、安全、消息队列可靠性、API 设计、测试、CI/CD 部署。

- [31 性能与并发](/back-end/frontend-backend-guide/31-performance-concurrency)
- [32 安全](/back-end/frontend-backend-guide/32-security)
- [33 消息队列可靠性](/back-end/frontend-backend-guide/33-mq-reliability)
- [34 API 设计](/back-end/frontend-backend-guide/34-api-design)
- [35 测试](/back-end/frontend-backend-guide/35-testing)
- [36 CI/CD 与部署](/back-end/frontend-backend-guide/36-cicd-deployment)

### Part 8 · 综合实战与附录

把所学串起来做完整练习，从零把一个功能开发到生产；附录随时查。

- [37 动手练习集](/back-end/frontend-backend-guide/37-hands-on-exercises)
- [38 毕业项目：一个功能从需求到上线](/back-end/frontend-backend-guide/38-capstone-feature-to-prod)
- [90 术语对照表](/back-end/frontend-backend-guide/90-glossary)
- [91 命令速查表](/back-end/frontend-backend-guide/91-command-cheatsheet)
- [92 学习资源推荐](/back-end/frontend-backend-guide/92-learning-resources)
- [93 学习路线规划](/back-end/frontend-backend-guide/93-learning-path)

---

## 如何使用本课程

- **先把 Part 1 读完**，哪怕只是快速过一遍。它建立的认知（无状态、并发、长跑、依赖下游）会贯穿后面每一章，跳过它后面会处处别扭。
- **之后按需深入。** 想先能写接口，直奔 Part 2；项目里全是数据库问题，先看 Part 3；线上频繁告警，Part 4 和 Part 6 是你的救命稻草。
- **遇到不认识的术语**，翻 [90 术语对照表](/back-end/frontend-backend-guide/90-glossary)（后端术语 ↔ 前端术语对照）。
- **想不起某条命令**怎么敲，翻 [91 命令速查表](/back-end/frontend-backend-guide/91-command-cheatsheet)（Linux / Docker / K8s / JVM 诊断命令集中放这）。
- **想要一条规划好的学习节奏**（每天学什么、多久能上手），看 [93 学习路线规划](/back-end/frontend-backend-guide/93-learning-path)。
- **学一个概念，就去项目代码里找到它的对应实现**——把抽象概念落到真实的 `svc-canvas`、`RtData`、Feign 客户端上，理解会翻倍牢固。

> 前端类比：把这门课当成一份「后端的 MDN」。Part 1-2 像入门教程顺着读，Part 3 往后更像查阅手册——用到哪查哪，附录就是你的索引。

---

## 相关入口

这门课聚焦「前端转后端」的认知与工程能力。如果你想深挖某个单点，站内还有更细的专题：

- [Java 语言细节](/back-end/java/02-syntax-basics) — 语法、集合、Lambda/Stream、异常等逐项展开，本课程 Part 2 的延伸阅读。
- [数据库手把手](/back-end/database/mysql/sql-basics) — MySQL 与 Redis 从基础到进阶的专门教程，配合本课程 Part 3。
- [Spring 全栈路线图](/back-end/spring-fullstack-roadmap) — 站在更高处看 Spring 全家桶的学习地图。

---

## 小结

- 这是一门**自洽、实操优先**的前端转后端课程，终点是「能独立开发并运维后端服务」，不止于「看懂代码」。
- 学后端最大的坎是**思维模型转变**：从单线程到并发、从有状态到无状态、从一次构建到长期运行、从看 Console 到看日志监控、从掌控 UI 到依赖下游。
- 全程用同一套 **AI 生图微服务**（svc-*、cpt-*、`RtData`）举例，每个概念都尽量配一句前端类比。
- 内容分 **8 个 Part 共 38 章 + 4 个附录**：先建立认知（Part 1），再按需深入语言、存储、并发、运维、排查、工程能力，最后综合实战。

### 自测

1. 后端服务为什么要设计成「无状态」？这跟前端组件的 `useState` 有什么本质区别？
2. 「单线程事件循环」和「多线程并发」最关键的区别是什么？它会带来哪一类前端几乎不会遇到的 bug？
3. 当一个线上请求变慢，你为什么不能只看「浏览器 Console」就定位问题？后端要看哪些东西？

### 下一章

带着上面的疑问，从 [01 后端思维转变](/back-end/frontend-backend-guide/01-backend-mindset) 开始，正式完成你的第一次「世界观」切换。

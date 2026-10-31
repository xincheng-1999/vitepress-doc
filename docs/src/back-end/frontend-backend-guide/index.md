---
prev: false
next:
  text: "A1. 后端究竟负责什么"
  link: "/back-end/frontend-backend-guide/01-backend-mindset"
---

# 前端转后端：从请求到上线

> 不从微服务全家桶开始，也不先背完 Java。沿着一个「任务管理系统」，看清请求、写出接口、存好数据、守住权限，再把它部署并查明故障。

## 先解决「我该从哪里学」

你只需要有 JavaScript / TypeScript、调用接口和使用终端的经验；**不假设你已经会网络、Java、Linux 或数据库**。

- **第一次来**：从下面的阶段 A 开始，按表内顺序走。文件名里的旧编号只是稳定地址，**不是阅读顺序**。
- **同事说了个听不懂的词**：先查 [场景术语词典](./90-glossary)，想知道为什么需要它，再看 [后端功能地图](./39-backend-feature-map)。
- **想马上动手**：直接做 [第一个可运行的任务 API](./07-build-a-crud-api)，卡在哪里再补前置，不必等全部读完。
- **已经在联调故障**：从 [请求链路](./03-request-lifecycle) 和 [日志与排错](./26-reading-logs) 开始。

![从网络到上线的后端学习路线，每阶段都有一个可验收的产物](/backend-guide/core-learning-map.svg)

## 全程围绕同一种任务业务

主线是 **Java 17 + Spring Boot 3.x + SQL 的单体 `task-api`**，监听 `8080`，接口以 `/api/tasks` 开头。

1. 第一版用 **H2 内存数据库**：不用先装任何中间件，就能跑完整的 HTTP → Service → SQL。
2. 理解数据建模后迁移到 **MySQL**：学习持久化、索引、事务和数据库迁移。
3. 再增加登录、数据归属、导出任务、附件等真实能力；**需要缓存才加 Redis，需要解耦才加 MQ**。
4. 用 Docker Compose 验证部署，不把 Kubernetes 当作入门门槛。

::: tip 示例和练习的边界
[第一版 API](./07-build-a-crud-api) 和 [Session 登录实验](./32a-session-login-lab) 都有独立可运行源码和测试，围绕相同任务接口。第一版无登录、侧重 CRUD / MySQL；登录变体单独用 H2，侧重 Cookie / CSRF / 身份与归属，避免一口气集成全部组件。两者**只用于本机教学，不是完整生产项目**。将安全与 MySQL 合并、加入缓存 / 导出及真实上线仍是扩展作业，不冒充已经交付的完整业务系统。
:::

## 阶段 A · 看清一条请求（先学，不跳过）

| 顺序 | 学习内容 | 学完必须能做到 |
| --- | --- | --- |
| A1 | [前端经验怎么迁移，后端究竟负责什么](./01-backend-mindset) | 区分体验校验、权限边界、持久状态与并发 |
| A2 | [系统地图：进程、服务器、数据库和中间件](./02-architecture-overview) | 画出最小单体系统，不把工具名当架构 |
| A3 | [网络：IP、端口、DNS、TCP、TLS](./22-networking-for-backend) | 解释 localhost / 0.0.0.0，分清解析失败与连接失败 |
| A4 | [HTTP 与浏览器：报文、CORS、Cookie、缓存](./22a-http-and-browser) | 读懂请求响应，解释 curl 通但浏览器失败 |
| A5 | [代理、网关、负载均衡与服务发现](./22b-proxy-and-discovery) | 说清 Nginx、CDN、网关各站在哪一跳 |
| A6 | [请求进入 Spring 之后](./03-request-lifecycle) | 从入口跟到 SQL，知道异常和超时在哪产生 |

**过关产物**：自己画出「点击新增任务 → 数据库提交 → 页面显示结果」的图，并标出 DNS、HTTP、进程和数据的位置。

## 阶段 B · 写出第一个接口（只学够用的语言）

| 顺序 | 学习内容 | 学完必须能做到 |
| --- | --- | --- |
| B1 | [Java 最小知识集](./05-java-crash-course) | 读懂 class、record、List、Optional、异常 |
| B2 | [Spring、Bean 和构造器注入](./06-spring-boot-ioc-di) | 说明对象谁创建、为什么注入、单例为何不能存用户状态 |
| B3 | [三层分工与陌生工程阅读方法](./04-three-layer-and-structure) | 找到入口、配置、Controller、Service、Repository |
| B4 | [动手：可运行任务 CRUD](./07-build-a-crud-api) | 用 curl 完成增查改删，验证 400 / 404 / 405 / 415 |
| B5 | [接口契约：分页、错误、版本与幂等](./34-api-design) | 给前端一份可以据此联调的接口文档 |

[注解速查](./08-annotations-cheatsheet) 和 [Java 专题](/back-end/java/01-environment) 是随手字典，**不用先逐篇读完**。

## 阶段 C · 把数据与权限做正确（核心必修）

| 顺序 | 学习内容 | 学完必须能做到 |
| --- | --- | --- |
| C1 | [数据建模、SQL 和索引](./10-sql-and-indexes) | 设计用户与任务表，用参数化 SQL、约束和 EXPLAIN |
| C2 | [事务、并发更新与一致性](./11-transactions-consistency) | 复现回滚和两个请求覆盖，区分事务与幂等 |
| C3 | [登录、Session / JWT 和授权](./32-security) | 不信任 userId，验证用户 A 不能操作 B 的任务 |
| C4 | [动手：Session 登录、CSRF 与 A/B 数据隔离](./32a-session-login-lab) | 用真实登录会话完成安全纵切，不只背 JWT 名词 |
| C5 | [后端测试](./35-testing) | 运行自动测试，覆盖失败、边界和越权，而不只点成功路径 |

[SQL vs NoSQL](./09-sql-vs-nosql) 用于选型理解；[数据库专题](/back-end/database/index) 用于补 SQL / JDBC / ORM 细节。**初学不需要把 MySQL、MongoDB、Redis 都接上。**

## 阶段 D · 听懂真实需求（先懂用途，再决定要不要用）

| 顺序 | 学习内容 | 学完必须能做到 |
| --- | --- | --- |
| D1 | [功能地图：上传、导出、搜索、调度、支付回调等](./39-backend-feature-map) | 把同事说的名词翻译成具体需求、实现和失败边界 |
| D2 | [Redis：缓存、限流与锁的边界](./12-redis-in-practice) | 解释缓存不是事实源，画出缓存失效时序 |
| D3 | [异步任务与 MQ 可靠性](./33-mq-reliability) | 设计导出任务状态，解释重复消费和 outbox |
| D4 | [连接池与资源上限](./13-connection-pools) | 区分等线程、等连接和等 SQL，不盲目加池大小 |

Redis、MQ 的**认知必学，集成可后置**。任务量小先用数据库任务表，不为堆技术引入组件。

## 阶段 E · 跑到服务器上，并知道出错怎么查

| 顺序 | 学习内容 | 学完必须能做到 |
| --- | --- | --- |
| E1 | [Linux 的进程、目录、权限和端口](./21-linux-server-essentials) | 找进程、看监听、按权限查看日志 |
| E2 | [配置与环境](./25-config-and-env) | 不把 localhost、密码和生产地址写死 |
| E3 | [Docker 与 Compose：部署单体和 MySQL](./23-docker-in-practice) | 分清容器端口 / 宿主端口 / 数据卷，重启数据不丢 |
| E4 | [日志、请求 ID 与第一次故障定位](./26-reading-logs) | 用请求 ID 找到根因，保护敏感信息 |
| E5 | [综合实战：从需求到验收](./38-capstone-feature-to-prod) | 完成安全、持久化、部署、恢复的交付清单 |

**第一轮终点是能交付一个小单体，不是会背微服务面试题。** 每阶段时间参考 [学习节奏与检查点](./93-learning-path)。

## 选修资料：第一轮不要逐章通读

保留这些内容是为了遇到真实问题能查，而不是要求入门先学完：

- **并发与资源**：[多线程](./14-single-thread-to-multithread) → [线程池](./15-thread-pools-executor) → [线程安全与锁](./16-thread-safety-locks) → [Java 异步](./17-async-programming)。Node / 浏览器也有异步竞态，不能因为单线程就忽略。
- **JVM 深入**：[内存](./18-jvm-memory-model)、[GC](./19-garbage-collection)、[OOM](./20-oom-memory-leak)。先有具体内存问题，再学诊断，不阻塞第一个接口。
- **线上专题**：[排查方法](./27-troubleshooting-methodology)、[排查手册](./28-troubleshooting-playbook)、[诊断工具](./29-diagnostic-toolbox)、[可观测性](./30-observability)、[性能](./31-performance-concurrency)。
- **规模化交付**：[Kubernetes](./24-kubernetes-in-practice)、[CI/CD](./36-cicd-deployment)。
- **练习与查阅**：[练习集](./37-hands-on-exercises)、[术语词典](./90-glossary)、[命令卡](./91-command-cheatsheet)、[官方资源](./92-learning-resources)。

::: info 如何理解保留专题里的旧示例
部分选修专题保留了 AI 生图的 `svc-*` / `cpt-*` / `RtData` 示例，用来展示多进程和运行时故障；它们是**解释片段，不是本仓库存在的完整微服务源码，也不是主线项目的依赖**。先把词换回「调用方、被调用方、统一响应」理解，不需要去找一个不存在的工程。
:::

## 这次整理删掉 / 后置了什么

- 删除旧首页、旧 8 Part 时间表和独立 Spring 路线中重复、相互冲突的学习安排，统一到本页；旧地址留一个短入口，不打断收藏链接。
- 删除主线中对不存在的 AI 微服务代码、共享异常类和认证网关的依赖，替换为可自行运行的单体例子。
- 重写把前端竞态说成不存在、把 504 说成上游一定收到请求、把缓存和锁说成绝对保证等误导表达。
- 将 JVM 调优、Kubernetes、微服务治理后置，不删仍有查阅价值的 Java / 数据库 / 运维专题。

**现在就开始：[A1 · 后端到底负责什么](./01-backend-mindset)。**

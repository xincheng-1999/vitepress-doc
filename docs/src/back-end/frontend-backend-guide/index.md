---
prev: false
next:
  text: "A1. 后端究竟负责什么"
  link: "/back-end/frontend-backend-guide/01-backend-mindset"
---

# 前端转后端：从一个任务功能开始

这套教程面向会写 JavaScript / TypeScript、会调用接口的前端开发者。不要求你已经会 Java、数据库或 Linux。

我们围绕一个任务管理系统学习：先看页面发出的请求怎样到达后端，再写出接口、保存任务、加入登录，最后学习部署和排错。新工具在需要它的时候再介绍，不必先装齐一套中间件。

## 第一次来，按 A → B → C → D → E 阅读

下面的表和侧边栏是同一个顺序。文件名里的数字保留了旧地址，不是阅读顺序。

![先看请求，再写接口、处理数据和权限，最后扩展功能并学习部署](/backend-guide/core-learning-map.svg)

### A · 先看懂请求是怎样走的

| 顺序 | 这一篇回答什么 |
| --- | --- |
| A1 | [一个新增任务按钮，为什么需要后端？](./01-backend-mindset) |
| A2 | [任务应用、服务器和数据库，分别运行在哪里？](./02-architecture-overview) |
| A3 | [浏览器怎样用域名、IP 和端口找到应用？](./22-networking-for-backend) |
| A4 | [HTTP 请求里有什么，浏览器又做了哪些限制？](./22a-http-and-browser) |
| A5 | [请求为什么有时先经过代理或网关？](./22b-proxy-and-discovery) |
| A6 | [在家怎样通过 VPN、堡垒机登录公司服务器？](./22c-private-network-and-access) |
| A7 | [新增任务的请求，到达 Spring 以后怎样处理？](./03-request-lifecycle) |

这一阶段先理解过程，不要求立即写 Java。接下来再把图中的处理步骤变成代码。

### B · 跑通一个任务接口

| 顺序 | 这一篇做什么 |
| --- | --- |
| B1 | [读懂任务代码里用到的 Java](./05-java-crash-course) |
| B2 | [弄清 Spring 怎样创建对象、传入依赖](./06-spring-boot-ioc-di) |
| B3 | [打开项目，沿一个接口找到三层代码](./04-three-layer-and-structure) |
| B4 | [启动配套工程，完成任务增删改查](./07-build-a-crud-api) |
| B5 | [把路径、输入、状态码写成明确的接口约定](./34-api-design) |

先用 H2 内存数据库，免去安装数据库的步骤。跑通以后，再讨论长期保存和多人使用。

### C · 让数据和权限符合业务要求

| 顺序 | 这一篇做什么 |
| --- | --- |
| C1 | [设计任务表，用 SQL 查询，再看索引怎样帮助查询](./10-sql-and-indexes) |
| C2 | [观察回滚，以及两个请求同时修改数据的结果](./11-transactions-consistency) |
| C3 | [确认用户身份，并检查任务属于谁](./32-security) |
| C4 | [运行独立的 Session 登录实验，验证两名用户互相隔离](./32a-session-login-lab) |
| C5 | [用自动测试检查正常、错误和越权请求](./35-testing) |

第一版“能读写任务”，不代表已经适合多人使用。这个阶段就是补上这些差别。

### D · 有了具体需求，再认识扩展工具

| 顺序 | 这一篇回答什么 |
| --- | --- |
| D1 | [附件、导出、调度、支付回调这些需求，后端分别要做什么？](./39-backend-feature-map) |
| D2 | [什么时候需要 Redis，缓存和限流怎样工作？](./12-redis-in-practice) |
| D3 | [耗时任务怎样放到后台，为什么消息会重试？](./33-mq-reliability) |
| D4 | [应用为什么会等数据库连接，池大小怎样影响它？](./13-connection-pools) |

这里先理解用途。任务量很小时，可以先用数据库完成工作，不必为了练习把所有组件都接上。

### E · 学习怎样部署，以及出错时从哪里查

| 顺序 | 这一篇做什么 |
| --- | --- |
| E1 | [在 Linux 中找进程、看端口、检查文件权限](./21-linux-server-essentials) |
| E2 | [区分开发与部署环境，配置地址和密钥](./25-config-and-env) |
| E3 | [用 Docker Compose 运行应用和 MySQL](./23-docker-in-practice) |
| E4 | [从请求 ID 和错误日志查一次故障](./26-reading-logs) |
| E5 | [把前面学过的功能组合起来，检查部署和恢复](./38-capstone-feature-to-prod) |

学习速度不用按周硬凑。每次完成一个小步骤，卡住再补对应知识，具体见 [怎样安排学习](./93-learning-path)。

## 配套源码有两份，不要混着运行

[第一版任务 API](./07-build-a-crud-api) 使用 Java 17、Spring Boot 3.5.7 和 H2，带 7 个测试，适合开始练 CRUD；它也提供切换 MySQL 的配置，但没有登录。

[Session 登录实验](./32a-session-login-lab) 是另一份独立工程，带 16 个测试，专门观察登录 Cookie、CSRF 和用户隔离。它仍用 H2，不是在第一版目录里继续覆盖文件。

两者都只用于本机教学。把安全、MySQL、备份和其他扩展组合成一个可部署项目，是后面的练习，不是下载包已经具备的完整生产能力。

## 不认识词，或需要深入时再查

- **遇到一个生词**：查 [170 条场景词典](./90-glossary)，再进入对应章节。
- **忘了注解或命令**：查 [注解](./08-annotations-cheatsheet)、[命令卡](./91-command-cheatsheet)。
- **需要补语法和存储用法**：查 [Java 专题](/back-end/java/01-environment)、[SQL / NoSQL](./09-sql-vs-nosql)、[数据库专题](/back-end/database/index)。
- **要深入并发**：按 [多线程](./14-single-thread-to-multithread) → [线程池](./15-thread-pools-executor) → [线程安全](./16-thread-safety-locks) → [异步编程](./17-async-programming) 阅读。
- **遇到内存问题**：查 [JVM 内存](./18-jvm-memory-model)、[GC](./19-garbage-collection)、[OOM](./20-oom-memory-leak)。
- **遇到线上故障或容量问题**：查 [排查方法](./27-troubleshooting-methodology)、[故障手册](./28-troubleshooting-playbook)、[诊断工具](./29-diagnostic-toolbox)、[可观测性](./30-observability)、[性能与容量](./31-performance-concurrency)。
- **确实要管理更复杂的交付**：再看 [Kubernetes](./24-kubernetes-in-practice)、[CI/CD](./36-cicd-deployment)。
- **想找额外练习或官方说明**：查 [练习集](./37-hands-on-exercises)、[学习资源](./92-learning-resources)。

选修里的部分代码是独立的场景片段，不是任务工程的依赖。各篇会说明使用条件，不需要为了读完它们去寻找不存在的微服务项目。

从 [A1 · 为什么需要后端](./01-backend-mindset) 开始。

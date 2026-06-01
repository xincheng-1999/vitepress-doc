# 前端转后端完整课程重构 · 设计文档

- **日期**：2026-06-01
- **目标目录**：`docs/src/back-end/frontend-backend-guide/`
- **类型**：内容重构（文档站点 · VitePress）
- **状态**：待用户评审

---

## 1. 背景与问题

现有 `frontend-backend-guide` 共 9 章（`index` + `01`~`09`），是一份"读懂一个真实微服务项目"的**认知导览**：架构、请求链路、目录结构、注解速查、阅读路径、练习、术语表、资源。

它写得清晰、前端类比到位，但**只停留在"看懂别人的代码"层面**，缺少让前端真正"学成后端开发"所必需的工程与运维能力。用户明确点名缺失：

- 服务器相关（Linux / Docker / **Kubernetes**）
- 多线程 / 并发（前端单线程思维的最大盲区）
- 怎么排查问题、看日志（生产环境的"实际东西"）

仓库现状（重要约束）：

- `/back-end/java/`：已有 14+ 章「写给前端的 Java 入门」（语言 + Spring Boot + IoC/DI + 配置 + Spring Data）。
- `/back-end/database/`：已有 MySQL / MongoDB / Redis / ORM 的手把手内容。
- `/back-end/`：`spring-fullstack-roadmap.md` 路线总览。

## 2. 目标（Goals）

把本指南重建为一份**完整、独立、实操优先**的「前端 → 后端」课程，读者从头读到尾即可建立从认知到上线再到排障的完整能力，而不只是看懂现成代码。

- 覆盖：认知 → 语言/框架地基 → 数据存储 → 并发/JVM → 服务器/容器 → 排查/可观测 → 工程进阶 → 实战。
- 实操优先：给真实可复制运行的命令（kubectl / docker / linux / jstack / jmap / arthas / top）、分步排查实操、真实日志样例。
- 始终保持"前端类比"叙事风格（这是本指南的灵魂）。

## 3. 非目标（Non-Goals）

- 不重复造 `/back-end/java/` 的完整 Java 语法教程——语言地基只做"工作级速通 + 深链"。
- 不重复造 `/back-end/database/` 的手把手 CRUD——数据章节聚焦"后端必须建立的认知"（索引、事务、缓存模式、连接池），手把手部分深链过去。
- 不做前端框架内容、不做 LLM/Python/Flutter 相关内容。
- 不引入新的样式体系或构建配置（遵循 `CLAUDE.md`）。

## 4. 关键决策（已与用户确认）

| # | 决策 | 选择 |
| --- | --- | --- |
| D1 | 定位 | **完整独立课程**（用户选 B），落地形态取「方案 A：完整课程 + 关键处交叉引用」——读起来是完整一条线，深挖处给传送门，避免维护重复的 Java/DB 章节 |
| D2 | 新模块 | **四个全要**：并发与 JVM、排查与可观测、工程进阶、服务器与容器 |
| D3 | 深度风格 | **实操优先**：真实可运行命令 + 分步排查 + 真实日志/输出样例 |
| D4 | 结构 | **重新分组 + 重排**：8 个 Part + 附录，章节重新编号 |
| D5 | 运行示例 | 沿用现有真实项目（`svc-gateway`/`svc-user`/`svc-auth`/`svc-ai`/`svc-canvas`/`svc-oss`，AI 生图微服务，MongoDB + MySQL + Redis + RocketMQ + OSS + Docker/K8s）作为贯穿全程的实战锚点 |
| D6 | 文件命名 | 统一改为**英文 kebab-case**（与 `java/`、`database/` 一致，URL 更稳）；侧边栏显示标题仍为中文 |

### 交叉引用策略（D1 的落地约定）

- 语言细节、数据库手把手等已被其它指南深度覆盖的内容：本课程给"够用的工作级讲解"，并在合适位置用 VitePress 链接深链，如 `[完整 Java 集合框架 →](/back-end/java/04-collections)`。
- 链接一律用站内相对路径、省略 `.md` 后缀（与仓库现有约定一致）。

## 5. 课程结构（最终蓝图）

> 单一侧边栏 key `/back-end/frontend-backend-guide/`，按 Part 分组（每个 Part 一个可折叠 group）。
> 文件采用连续编号 + 英文 slug；附录用 `90+` 段。**粗体**=用户点名的重点新模块。

### 总览
- `index.md` — 课程总览（重写）：定位、读者、**前后端思维转变**、完整学习地图、如何使用、各 Part 时间建议

### Part 1 · 建立后端认知
- `01-backend-mindset.md` —（新增·总纲）后端思维：从"一个浏览器"到"上万并发请求"；无状态、生命周期、为什么要关心并发/资源/故障
- `02-architecture-overview.md` —（重构现 `01-项目整体架构`）微服务、网关、服务拆分、三种通信方式
- `03-request-lifecycle.md` —（重构现 `02-一个请求的完整链路`，补：线程模型 thread-per-request、连接池在链路中的位置）
- `04-three-layer-and-structure.md` —（合并现 `04-目录结构导读` + 分层职责）三层架构 + 怎么读源码

### Part 2 · 语言与框架地基（速通 + 交叉引用）
- `05-java-crash-course.md` —（新增）面向前端的 Java 工作级速通（类型/类/异常/泛型/集合/Stream 最小够用集），深链 `/back-end/java`
- `06-spring-boot-ioc-di.md` —（新增）Spring Boot 启动、IoC/DI、Bean、自动配置（前端类比 provide/inject）
- `07-build-a-crud-api.md` —（新增·实操）从零写一个 Controller→Service→Repository 接口并跑通
- `08-annotations-cheatsheet.md` —（移自现 `05-关键注解速查`）作速查卡保留

### Part 3 · 数据与存储
- `09-sql-vs-nosql.md` — 关系型 vs 文档型怎么选（MySQL/MongoDB 的取舍）
- `10-sql-and-indexes.md` — 写给前端的 SQL + 索引原理 + 执行计划 + 慢查询 + N+1 问题
- `11-transactions-consistency.md` — 事务（ACID/隔离级别）+ 分布式一致性/最终一致性
- `12-redis-in-practice.md` — Redis 数据结构 + 缓存模式（cache-aside）+ 穿透/击穿/雪崩 + 分布式锁
- `13-connection-pools.md` —（前端没有的概念）数据库/HTTP 连接池、池满会发生什么

### Part 4 · 并发与 JVM 🔥
- `14-single-thread-to-multithread.md` — 从 JS 事件循环到 Java 多线程模型（**核心认知转变**）
- `15-thread-pools-executor.md` — 线程、线程池、Executor；为什么 Tomcat 是 thread-per-request、池化的意义
- `16-thread-safety-locks.md` — 线程安全、竞态、synchronized/Lock、原子类、并发容器（ConcurrentHashMap）
- `17-async-programming.md` — CompletableFuture / `@Async`（对比 Promise / async-await）
- `18-jvm-memory-model.md` — JVM 内存：堆/栈/方法区/元空间、对象生命周期
- `19-garbage-collection.md` — GC：为什么有 STW、常见收集器、怎么看 GC 日志
- `20-oom-memory-leak.md` — OOM 与内存泄漏的常见原因与定位思路（引出 heap dump）

### Part 5 · 服务器与容器 🔥
- `21-linux-server-essentials.md` — Linux 必会：SSH 登录、文件/目录/权限、进程（ps/top）、网络（ss/netstat/curl）、磁盘（df/du）、环境变量、systemd 服务
- `22-networking-for-backend.md` — 后端视角网络：TCP/HTTP/HTTPS、端口、DNS、防火墙、为什么连不上
- `23-docker-in-practice.md` — 镜像 vs 容器、给 Spring Boot 写 Dockerfile、run/logs/exec/ps、docker-compose 起整套依赖、数据卷/网络
- `24-kubernetes-in-practice.md` — **K8s 实战**：为什么需要它；Pod/Deployment/Service/Ingress/ConfigMap/Secret；kubectl 常用命令；看 Pod 日志、进容器、滚动发布、回滚、扩缩容；排查 CrashLoopBackOff / ImagePullBackOff / OOMKilled
- `25-config-and-env.md` — 配置与环境管理：12-factor、多环境、配置中心、Secret 管理

### Part 6 · 排查与可观测 🔥
- `26-reading-logs.md` — **看日志**：日志级别、结构化日志、logback 配置、日志去哪了、grep/tail/less 实战、按 traceId/requestId 串联整条链路、ELK/Loki 简介
- `27-troubleshooting-methodology.md` — 排查方法论（心法 + 标准流程：看日志→看监控→定位层级→复现→二分→最近改了什么）
- `28-troubleshooting-playbook.md` — **按症状的排查实战手册**：接口 500/接口慢/CPU 100%/内存暴涨 OOM/线程死锁卡住/服务频繁重启/数据不对，每种给定位步骤与命令
- `29-diagnostic-toolbox.md` — 排查工具箱：jps/jstack/jmap/jstat/jcmd、Arthas 在线诊断、top/htop、Postman/curl、tcpdump 入门
- `30-observability.md` — 可观测三件套：Metrics（Prometheus + Grafana + Spring Actuator/Micrometer）、Tracing（链路追踪/traceId 贯穿）、Logging、告警

### Part 7 · 工程进阶
- `31-performance-concurrency.md` — 性能与高并发：缓存深入、限流算法（令牌桶/漏桶/滑动窗口）、降级熔断、池化、批处理、异步化、压测入门（wrk/jmeter）
- `32-security.md` — 安全：认证 vs 授权、JWT/OAuth2、密码存储（bcrypt）、常见漏洞（SQL 注入/XSS/CSRF/越权）、HTTPS、密钥管理（接前端 web 安全）
- `33-mq-reliability.md` — 消息队列可靠性深入：丢失/重复/顺序、幂等、死信队列、事务消息
- `34-api-design.md` — API 设计：RESTful、版本管理、幂等、分页、统一错误码规范
- `35-testing.md` — 后端测试：JUnit/Mockito、集成测试、Testcontainers（对比前端 jest）
- `36-cicd-deployment.md` — CI/CD 与部署：流水线（GitHub Actions/GitLab CI）、构建并推送镜像、部署到 K8s、蓝绿/金丝雀

### Part 8 · 实战闯关
- `37-hands-on-exercises.md` —（扩充现 `07-动手练习`）在原 5 个练习基础上，新增排查/运维实操题（制造慢接口并排查、制造 OOM 并 dump 分析、docker-compose 起全套、模拟死锁用 jstack 定位）
- `38-capstone-feature-to-prod.md` —（新增）综合实战：一个功能从写接口 → 加缓存 → 接 MQ → Docker 化 → 部署 K8s → 看日志 → 排障，串起全部知识

### 附录
- `90-glossary.md` —（扩充现 `08-术语对照表`）补充运维/并发/可观测术语
- `91-command-cheatsheet.md` —（新增）一页纸命令速查：Linux / Docker / kubectl / JVM 诊断工具
- `92-learning-resources.md` —（扩充现 `09-学习资源推荐`）按新结构补充各模块资源
- `93-learning-path.md` —（新增，吸收现 `06-给前端的学习路径` 的精华）按 Part 的推荐学习路线与时间表

## 6. 现有文件迁移映射

| 现有文件 | 去向 |
| --- | --- |
| `index.md` | 重写 |
| `01-项目整体架构.md` | → `02-architecture-overview.md`（重构扩充） |
| `02-一个请求的完整链路.md` | → `03-request-lifecycle.md`（重构扩充） |
| `03-核心技术点拆解.md` | 内容拆分重分配到 Part 3（`09`/`12`）与 `33-mq-reliability`；原文件删除 |
| `04-目录结构导读.md` | → `04-three-layer-and-structure.md`（合并分层职责） |
| `05-关键注解速查.md` | → `08-annotations-cheatsheet.md`（迁移） |
| `06-给前端的学习路径.md` | 精华吸收进 `index.md` 与 `93-learning-path.md`；原文件删除 |
| `07-动手练习.md` | → `37-hands-on-exercises.md`（扩充） |
| `08-术语对照表.md` | → `90-glossary.md`（扩充） |
| `09-学习资源推荐.md` | → `92-learning-resources.md`（扩充） |

> 旧的中文文件名文件在内容迁移完成后删除（`git rm`），避免 URL 残留与重复。

## 7. 内容编写约定（Conventions）

- **语言**：简体中文正文；代码/命令/术语保留英文原文。
- **叙事**：每个后端概念尽量配"前端类比"（React/Vue/Next/Node/axios/zod 等）。
- **实操优先**：命令块给真实可复制内容，并尽量给出"预期输出/日志样例"；排查类章节用"症状 → 怀疑 → 命令 → 读结果 → 结论"的结构。
- **图示**：沿用现有 ASCII 框图风格（链路图、流程图）。
- **代码块**：标注语言（`java`/`bash`/`yaml`/`text`），Java 示例与现有项目命名风格一致（`RtData`、`svc-*`、`cpt-*`）。
- **交叉引用**：站内相对路径、省略 `.md`。
- **每章结尾**：可选「你能学到 / 自测问题 / 下一步」小结，保持与现有章节体例一致。
- **篇幅**：基础认知章 ~150–250 行；重点实战章（K8s、排查手册、并发）可 250–400 行。

## 8. 导航与配置改动

- `docs/.vitepress/sidebar.js`：把 `/back-end/frontend-backend-guide/` 这一 key 重写为按 Part 分组的结构（8 个 Part group + 1 个附录 group，建议 `collapsed: true`），所有 link 指向新英文文件名，text 用中文标题。
- `docs/.vitepress/nav.js`：检查是否有指向本指南旧文件的链接，若有则同步更新。
- 改完运行 `python scripts/vp_check_config_links.py` 与 `python scripts/vp_check_links.py` 校验无死链。

## 9. 验收标准（Acceptance Criteria）

1. 8 个 Part + 附录的全部文件均已创建，内容非占位（无 TODO/TBD）。
2. 旧中文文件名文件已删除，无残留。
3. `sidebar.js` 已重写为分组结构，所有链接可达。
4. `python scripts/vp_check_links.py` 与 `python scripts/vp_check_config_links.py` 均无新增死链（输出空或仅历史既有问题）。
5. `pnpm docs:build` 构建通过（无 SSR/链接报错）。
6. 重点模块（K8s 实战、排查实战手册、并发与 JVM、看日志）均含真实可运行命令与样例输出，符合"实操优先"。
7. 交叉引用到 `/back-end/java`、`/back-end/database` 的链接均有效。

## 10. 风险与缓解

- **篇幅大、易不一致**：用统一的内容约定（§7）+ 实现阶段最后做一次"全局一致性 + 死链 + 构建"校验。
- **与现有 Java/DB 指南重复**：靠交叉引用策略（§4）控制，语言/DB 章节只做工作级讲解。
- **命令的平台差异**：服务器/容器命令以 Linux 为主（生产环境），必要处标注与本机 Windows 的差异。
- **构建失败**：任何 `.md` 内嵌 `<script>`/`<style>` 需遵守 `CLAUDE.md` 的 SSR 保护约定（本课程基本是纯文档，预计不涉及）。

## 11. 实现顺序建议

1. 先定结构骨架：建空文件 + 重写 `sidebar.js`（保证站点可构建、导航成型）。
2. 迁移/重构现有 9 章内容到新文件。
3. 并行编写各新章节（按 Part）。
4. 全局一致性与交叉引用校验 → 链接脚本 → `pnpm docs:build`。
5. 删除旧文件、最终校验。

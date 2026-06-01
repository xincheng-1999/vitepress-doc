# 前端转后端完整课程重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `docs/src/back-end/frontend-backend-guide/` 从 9 章认知导览重建为 8 Part + 附录、约 40 篇、实操优先的完整「前端 → 后端」课程。

**Architecture:** 先建可构建的骨架（创建全部新文件 + 重写 `sidebar.js`），再迁移/重构现有 9 章，再按 Part 并行编写新章节，最后删除旧文件并跑链接校验 + 构建。每章是一个自包含任务，附详细内容简报（sections + 必讲点 + 关键命令/示例 + 前端类比 + 交叉链接 + 篇幅）。

**Tech Stack:** VitePress 1.3.2 · Markdown · ASCII 框图 · 纯 JS sidebar 配置 · `pnpm docs:build` 校验 · `scripts/vp_check_*.py` 链接校验。

**关键约束：**
- VitePress **构建会因死链失败**（config 未设 `ignoreDeadLinks`）。→ 骨架阶段必须先建全部文件，再让 sidebar 与交叉链接引用它们。
- 站内链接用相对路径、**省略 `.md`**（与仓库现有约定一致）。
- 深链只指向**已验证存在**的文件（`/back-end/java/*`、`/back-end/database/*`）。
- 命令以 **Linux/生产环境**为主；正文简体中文，代码/命令/术语保留英文。
- 运行示例统一用现有 AI 生图微服务项目（`svc-gateway`/`svc-user`/`svc-auth`/`svc-ai`/`svc-canvas`/`svc-oss`，MongoDB+MySQL+Redis+RocketMQ+OSS+Docker/K8s）。

**本计划适合用 Workflow 执行：** Phase A 串行；Phase B/C 章节可并行（pipeline：起草 → 一致性校验）；Phase D 串行收尾。

---

## 文件总览（File Structure）

新建/重写文件（全部位于 `docs/src/back-end/frontend-backend-guide/`）：

```
index.md                              (重写)
01-backend-mindset.md                 (新增)
02-architecture-overview.md           (重构自 01-项目整体架构)
03-request-lifecycle.md               (重构自 02-一个请求的完整链路)
04-three-layer-and-structure.md       (合并自 04-目录结构导读)
05-java-crash-course.md               (新增)
06-spring-boot-ioc-di.md              (新增)
07-build-a-crud-api.md                (新增)
08-annotations-cheatsheet.md          (迁移自 05-关键注解速查)
09-sql-vs-nosql.md                    (新增)
10-sql-and-indexes.md                 (新增)
11-transactions-consistency.md        (新增)
12-redis-in-practice.md               (新增)
13-connection-pools.md                (新增)
14-single-thread-to-multithread.md    (新增)
15-thread-pools-executor.md           (新增)
16-thread-safety-locks.md             (新增)
17-async-programming.md               (新增)
18-jvm-memory-model.md                (新增)
19-garbage-collection.md              (新增)
20-oom-memory-leak.md                 (新增)
21-linux-server-essentials.md         (新增)
22-networking-for-backend.md          (新增)
23-docker-in-practice.md              (新增)
24-kubernetes-in-practice.md          (新增)
25-config-and-env.md                  (新增)
26-reading-logs.md                    (新增)
27-troubleshooting-methodology.md     (新增)
28-troubleshooting-playbook.md        (新增)
29-diagnostic-toolbox.md              (新增)
30-observability.md                   (新增)
31-performance-concurrency.md         (新增)
32-security.md                        (新增)
33-mq-reliability.md                  (新增)
34-api-design.md                      (新增)
35-testing.md                         (新增)
36-cicd-deployment.md                 (新增)
37-hands-on-exercises.md              (扩充自 07-动手练习)
38-capstone-feature-to-prod.md        (新增)
90-glossary.md                        (扩充自 08-术语对照表)
91-command-cheatsheet.md              (新增)
92-learning-resources.md              (扩充自 09-学习资源推荐)
93-learning-path.md                   (新增, 吸收 06-给前端的学习路径)
```

删除（内容迁移完成后）：
```
01-项目整体架构.md  02-一个请求的完整链路.md  03-核心技术点拆解.md
04-目录结构导读.md  05-关键注解速查.md  06-给前端的学习路径.md
07-动手练习.md      08-术语对照表.md          09-学习资源推荐.md
```

修改：`docs/.vitepress/sidebar.js`（重写 `/back-end/frontend-backend-guide/` key）。

---

## 内容编写约定（所有章节任务共享）

- 正文简体中文；代码/命令/术语英文。每个后端概念尽量配一句**前端类比**。
- 实操章节用结构：**症状/目标 → 命令（可复制）→ 预期输出/日志样例 → 怎么读 → 结论**。
- 代码块标注语言（`java`/`bash`/`yaml`/`text`）；Java 命名与现有项目一致（`RtData`、`svc-*`、`cpt-*`）。
- ASCII 框图沿用现有风格。
- 交叉链接：站内相对路径省略 `.md`，例如 `[Java 集合框架 →](/back-end/java/04-collections)`。深链前确认目标文件存在。
- 每章结尾给「小结 / 自测 3 问 / 下一章」收束。
- 篇幅：认知章 ~150–250 行；重点实战章（K8s、排查手册、并发、看日志）250–400 行。

---

## Phase A — 骨架与导航（串行，先行）

### Task A1: 创建全部新文件的骨架

**Files:** Create 上述 41 个新文件（`index.md` 用重写覆盖；其余 `01`~`93` 全新建）。

- [ ] **Step 1: 为每个文件写最小骨架**

每个文件至少包含 H1 标题 + 一行「本章简介」占位段（占位仅用于骨架阶段，后续 Phase B/C 会被完整内容替换）。示例（`14-single-thread-to-multithread.md`）：

```markdown
# 从 JS 单线程到 Java 多线程

> 本章简介（骨架占位，待补）。
```

H1 中文标题对照（建议）：
- `index` 课程总览
- `01` 后端思维：从一个浏览器到上万并发请求
- `02` 项目整体架构 / `03` 一个请求的完整链路 / `04` 三层架构与目录结构
- `05` Java 速通（面向前端）/ `06` Spring Boot 与 IoC/DI / `07` 写一个 CRUD 接口 / `08` 关键注解速查
- `09` 关系型 vs 文档型怎么选 / `10` SQL 与索引 / `11` 事务与一致性 / `12` Redis 实战 / `13` 连接池
- `14` 从 JS 单线程到 Java 多线程 / `15` 线程池与 Executor / `16` 线程安全与锁 / `17` 异步编程 / `18` JVM 内存模型 / `19` 垃圾回收 GC / `20` OOM 与内存泄漏
- `21` Linux 服务器必会 / `22` 后端视角网络基础 / `23` Docker 实战 / `24` Kubernetes 实战 / `25` 配置与环境管理
- `26` 看日志 / `27` 问题排查方法论 / `28` 排查实战手册 / `29` 排查工具箱 / `30` 可观测三件套
- `31` 性能与高并发 / `32` 安全 / `33` 消息队列可靠性 / `34` API 设计 / `35` 后端测试 / `36` CI/CD 与部署
- `37` 动手练习 / `38` 综合实战：从 0 到上线再到排障
- `90` 术语对照表 / `91` 命令速查卡 / `92` 学习资源推荐 / `93` 学习路径与时间表

- [ ] **Step 2: Commit**

```bash
git add docs/src/back-end/frontend-backend-guide/
git commit -m "docs: 建立前端转后端课程骨架文件"
```

### Task A2: 重写 sidebar.js 分组结构

**Files:** Modify `docs/.vitepress/sidebar.js`（替换 `"/back-end/frontend-backend-guide/"` 这一 key 的值）。

- [ ] **Step 1: 用按 Part 分组的结构替换该 key**

```js
"/back-end/frontend-backend-guide/": [
  {
    text: "前端转后端完整课程",
    items: [{ text: "课程总览", link: "/back-end/frontend-backend-guide/index.md" }],
  },
  {
    text: "Part 1 · 建立后端认知",
    collapsed: true,
    items: [
      { text: "01. 后端思维：从一个浏览器到上万并发", link: "/back-end/frontend-backend-guide/01-backend-mindset.md" },
      { text: "02. 项目整体架构", link: "/back-end/frontend-backend-guide/02-architecture-overview.md" },
      { text: "03. 一个请求的完整链路", link: "/back-end/frontend-backend-guide/03-request-lifecycle.md" },
      { text: "04. 三层架构与目录结构", link: "/back-end/frontend-backend-guide/04-three-layer-and-structure.md" },
    ],
  },
  {
    text: "Part 2 · 语言与框架地基",
    collapsed: true,
    items: [
      { text: "05. Java 速通（面向前端）", link: "/back-end/frontend-backend-guide/05-java-crash-course.md" },
      { text: "06. Spring Boot 与 IoC/DI", link: "/back-end/frontend-backend-guide/06-spring-boot-ioc-di.md" },
      { text: "07. 写一个 CRUD 接口", link: "/back-end/frontend-backend-guide/07-build-a-crud-api.md" },
      { text: "08. 关键注解速查", link: "/back-end/frontend-backend-guide/08-annotations-cheatsheet.md" },
    ],
  },
  {
    text: "Part 3 · 数据与存储",
    collapsed: true,
    items: [
      { text: "09. 关系型 vs 文档型怎么选", link: "/back-end/frontend-backend-guide/09-sql-vs-nosql.md" },
      { text: "10. SQL 与索引", link: "/back-end/frontend-backend-guide/10-sql-and-indexes.md" },
      { text: "11. 事务与一致性", link: "/back-end/frontend-backend-guide/11-transactions-consistency.md" },
      { text: "12. Redis 实战", link: "/back-end/frontend-backend-guide/12-redis-in-practice.md" },
      { text: "13. 连接池", link: "/back-end/frontend-backend-guide/13-connection-pools.md" },
    ],
  },
  {
    text: "Part 4 · 并发与 JVM",
    collapsed: true,
    items: [
      { text: "14. 从 JS 单线程到 Java 多线程", link: "/back-end/frontend-backend-guide/14-single-thread-to-multithread.md" },
      { text: "15. 线程池与 Executor", link: "/back-end/frontend-backend-guide/15-thread-pools-executor.md" },
      { text: "16. 线程安全与锁", link: "/back-end/frontend-backend-guide/16-thread-safety-locks.md" },
      { text: "17. 异步编程", link: "/back-end/frontend-backend-guide/17-async-programming.md" },
      { text: "18. JVM 内存模型", link: "/back-end/frontend-backend-guide/18-jvm-memory-model.md" },
      { text: "19. 垃圾回收 GC", link: "/back-end/frontend-backend-guide/19-garbage-collection.md" },
      { text: "20. OOM 与内存泄漏", link: "/back-end/frontend-backend-guide/20-oom-memory-leak.md" },
    ],
  },
  {
    text: "Part 5 · 服务器与容器",
    collapsed: true,
    items: [
      { text: "21. Linux 服务器必会", link: "/back-end/frontend-backend-guide/21-linux-server-essentials.md" },
      { text: "22. 后端视角网络基础", link: "/back-end/frontend-backend-guide/22-networking-for-backend.md" },
      { text: "23. Docker 实战", link: "/back-end/frontend-backend-guide/23-docker-in-practice.md" },
      { text: "24. Kubernetes 实战", link: "/back-end/frontend-backend-guide/24-kubernetes-in-practice.md" },
      { text: "25. 配置与环境管理", link: "/back-end/frontend-backend-guide/25-config-and-env.md" },
    ],
  },
  {
    text: "Part 6 · 排查与可观测",
    collapsed: true,
    items: [
      { text: "26. 看日志", link: "/back-end/frontend-backend-guide/26-reading-logs.md" },
      { text: "27. 问题排查方法论", link: "/back-end/frontend-backend-guide/27-troubleshooting-methodology.md" },
      { text: "28. 排查实战手册", link: "/back-end/frontend-backend-guide/28-troubleshooting-playbook.md" },
      { text: "29. 排查工具箱", link: "/back-end/frontend-backend-guide/29-diagnostic-toolbox.md" },
      { text: "30. 可观测三件套", link: "/back-end/frontend-backend-guide/30-observability.md" },
    ],
  },
  {
    text: "Part 7 · 工程进阶",
    collapsed: true,
    items: [
      { text: "31. 性能与高并发", link: "/back-end/frontend-backend-guide/31-performance-concurrency.md" },
      { text: "32. 安全", link: "/back-end/frontend-backend-guide/32-security.md" },
      { text: "33. 消息队列可靠性", link: "/back-end/frontend-backend-guide/33-mq-reliability.md" },
      { text: "34. API 设计", link: "/back-end/frontend-backend-guide/34-api-design.md" },
      { text: "35. 后端测试", link: "/back-end/frontend-backend-guide/35-testing.md" },
      { text: "36. CI/CD 与部署", link: "/back-end/frontend-backend-guide/36-cicd-deployment.md" },
    ],
  },
  {
    text: "Part 8 · 实战闯关",
    collapsed: true,
    items: [
      { text: "37. 动手练习", link: "/back-end/frontend-backend-guide/37-hands-on-exercises.md" },
      { text: "38. 综合实战：从 0 到上线再到排障", link: "/back-end/frontend-backend-guide/38-capstone-feature-to-prod.md" },
    ],
  },
  {
    text: "附录",
    collapsed: true,
    items: [
      { text: "术语对照表", link: "/back-end/frontend-backend-guide/90-glossary.md" },
      { text: "命令速查卡", link: "/back-end/frontend-backend-guide/91-command-cheatsheet.md" },
      { text: "学习资源推荐", link: "/back-end/frontend-backend-guide/92-learning-resources.md" },
      { text: "学习路径与时间表", link: "/back-end/frontend-backend-guide/93-learning-path.md" },
    ],
  },
],
```

- [ ] **Step 2: 构建验证骨架可用**

Run: `pnpm docs:build`
Expected: 构建成功，无死链报错（所有 sidebar link 均指向已创建文件）。

- [ ] **Step 3: 配置链接校验**

Run: `python scripts/vp_check_config_links.py`
Expected: 输出 `config-broken-links.json` 中本指南相关条目为空。

- [ ] **Step 4: Commit**

```bash
git add docs/.vitepress/sidebar.js
git commit -m "docs: 重写 frontend-backend-guide 侧边栏为分 Part 结构"
```

---

## Phase B — 迁移/重构现有 9 章

> 每个任务：把旧文件内容搬到新文件并按"内容约定"升级，**不删除旧文件**（删除在 Phase D）。每完成一个 Part 提交一次。

### Task B1: 重写 `index.md`（课程总览）

**Files:** Modify `index.md`

- [ ] **Step 1: 写完整内容**，包含：
  - 课程定位（完整独立的 FE→BE 课程，实操优先）、适用读者（1 年+ 前端、后端零基础）。
  - **前后端思维转变**小节（3–5 条：单线程 vs 多线程并发、有状态浏览器 vs 无状态服务、构建后即结束 vs 7×24 长跑、出错看 console vs 看服务器日志/监控）。
  - 完整学习地图（按 8 Part 列出，每 Part 一句话 + 链接到各章）。
  - 如何使用（先认知→地基→按需深入；遇到术语查附录 `90-glossary`；命令查 `91-command-cheatsheet`）。
  - 指向 `93-learning-path` 的时间表入口。
  - 交叉链接：`/back-end/java`、`/back-end/database`、`/back-end/spring-fullstack-roadmap`。
- [ ] **Step 2: Commit** `git commit -m "docs: 重写课程总览 index"`

### Task B2: Part 1 迁移（02/03/04）

**Files:** Modify `02-architecture-overview.md`、`03-request-lifecycle.md`、`04-three-layer-and-structure.md`

- [ ] **Step 1:** `02` 迁移现 `01-项目整体架构.md` 全文（技术栈、前端术语映射表、服务拆分树、三种通信方式、服务发现、网关四职责框图），结尾加「下一章看请求怎么穿过这套架构 → `03`」。
- [ ] **Step 2:** `03` 迁移现 `02-一个请求的完整链路.md` 全文（单体链路框图、各层职责表、登录同步流程、生图异步 MQ 流程、Header 透传），**补充**：thread-per-request 模型（每个请求占一个 Tomcat 线程，引出 `14`/`15`）、DB/HTTP 连接池在链路中的位置（引出 `13`）。
- [ ] **Step 3:** `04` 合并现 `04-目录结构导读.md`（项目顶层结构、单服务内部结构、三层架构前后端对照图、核心 vs 可跳过目录、component 模块表、读源码路径），并入分层职责口诀。
- [ ] **Step 4: Commit** `git commit -m "docs: 迁移 Part 1 认知章节"`

### Task B3: 迁移 `08-annotations-cheatsheet.md`

**Files:** Modify `08-annotations-cheatsheet.md`

- [ ] **Step 1:** 迁移现 `05-关键注解速查.md` 全文（控制器/参数绑定/业务层/数据层/辅助注解 + 依赖注入原理 + 速查卡片表）。补一行指向 `06-spring-boot-ioc-di` 的链接。
- [ ] **Step 2: Commit** `git commit -m "docs: 迁移注解速查"`

### Task B4: 迁移附录（90/92/93）

**Files:** Modify `90-glossary.md`、`92-learning-resources.md`、`93-learning-path.md`

- [ ] **Step 1:** `90` 迁移现 `08-术语对照表.md` 五张表，**新增**两类术语：并发/JVM（线程池、锁、heap、GC、STW、OOM、thread dump、heap dump）、运维可观测（Pod、Deployment、镜像、容器、kubectl、日志级别、traceId、metrics、链路追踪、限流、熔断、幂等）。
- [ ] **Step 2:** `92` 迁移现 `09-学习资源推荐.md`，**新增**模块资源：Linux/网络、Docker/K8s 官方文档与中文教程、JUC 并发、JVM/GC（《深入理解 Java 虚拟机》）、Arthas 官方文档、Prometheus/Grafana、可观测性资料。
- [ ] **Step 3:** `93` 吸收现 `06-给前端的学习路径.md` 的 Step 精华，重组为按 8 Part 的推荐路线 + 各 Part 时间建议表（沿用现有时间表格式并扩展到新 Part）。
- [ ] **Step 4: Commit** `git commit -m "docs: 迁移并扩充附录（术语/资源/学习路径）"`

### Task B5: 扩充 `37-hands-on-exercises.md`

**Files:** Modify `37-hands-on-exercises.md`

- [ ] **Step 1:** 迁移现 `07-动手练习.md` 的 5 个练习（新增 GET 接口、跟踪链路、改返回值、理解 Feign、读 MQ 流转）。**新增**运维/排查实操题：
  - 练习 6：用 `docker-compose` 在本机起 MongoDB+Redis+RocketMQ 一套依赖并连通。
  - 练习 7：故意写一个慢接口（`Thread.sleep` 或慢查询），用日志 + `28-troubleshooting-playbook` 的方法定位。
  - 练习 8：写一段制造 OOM 的代码（无限往 List 加对象），用 `-XX:+HeapDumpOnOutOfMemoryError` 抓 dump 并用 MAT 看。
  - 练习 9：写两把顺序相反的锁制造死锁，用 `jstack` 找到 BLOCKED 线程。
  - 练习 10：给某服务写 Dockerfile 并 `docker build` + `docker run` 跑起来。
  每题给：目标 / 步骤 / 关键命令 / 你会学到。交叉链接到对应章节。
- [ ] **Step 2: Commit** `git commit -m "docs: 扩充动手练习（新增运维与排查实操题）"`

---

## Phase C — 新章节编写（可并行，按 Part）

> 每个任务 = 一章。提供内容简报；执行时产出完整 prose。每个 Part 完成提交一次（或每章一次）。所有交叉链接指向已存在文件。

### Task C1: `01-backend-mindset.md` — 后端思维总纲

- [ ] 内容简报：
  - 开篇：后端不只是"写另一种语言"，而是换一套世界观。
  - 五个核心转变（每个配前端类比 + 该课程哪章深入）：
    1. **并发**：你的代码同时被成千上万请求执行 → `14`/`15`/`16`。
    2. **无状态**：服务可随时被杀/重启/多副本，状态放 DB/Redis 不放内存 → `12`/`24`。
    3. **长生命周期**：7×24 运行，内存/连接/线程会累积出问题 → `18`/`19`/`20`/`13`。
    4. **看不见的现场**：出错没有浏览器 console，只有日志/监控/dump → `26`/`28`/`29`/`30`。
    5. **依赖与故障**：依赖 DB/MQ/下游服务，必须考虑超时/重试/降级 → `31`/`33`。
  - 一张"前端 vs 后端关注点"对照表。
  - 结尾：本课程的学习地图与建议路线（链 `93`）。
  - 篇幅 ~180 行。

### Task C2: Part 2 新章（`05`/`06`/`07`）

- [ ] `05-java-crash-course.md` — Java 工作级速通：
  - 面向"已会 JS/TS"的最小够用集：静态类型与基本类型 vs JS、`class`/构造器/`record`、`null` 与 `Optional`、泛型（对比 TS 泛型）、`List`/`Map`/`Set`（对比数组/对象/Map）、`for`/增强 for/Stream（对比 `map`/`filter`/`reduce`）、异常 `try/catch/throws`（对比 JS）、包与 `import`、`main` 入口。
  - 每节给 JS↔Java 并排代码。
  - 深链：`[完整 Java 语法 →](/back-end/java/02-syntax-basics)`、`[集合框架 →](/back-end/java/04-collections)`、`[Lambda 与 Stream →](/back-end/java/04a-lambda-stream)`、`[异常处理 →](/back-end/java/05-exception)`。
  - 篇幅 ~250 行。
- [ ] `06-spring-boot-ioc-di.md` — Spring Boot 与 IoC/DI：
  - Spring Boot 是什么（自带服务器的应用框架，类比 Next.js 一键起服务）、`@SpringBootApplication` 启动流程、Bean 与容器、`@Component/@Service/@Repository`、构造器注入 vs `@Autowired`、为什么不用自己 `new`（对比 provide/inject、useContext）、自动配置一句话。
  - 深链 `[Spring IoC 与依赖注入 →](/back-end/java/07a-spring-ioc-di)`、`[Spring Boot 配置 →](/back-end/java/07b-spring-config)`。
  - 篇幅 ~220 行。
- [ ] `07-build-a-crud-api.md` — 实操写一个 CRUD 接口：
  - 从零写一个 `note` 资源：Entity → Repository → Service → Controller，跑通 `POST/GET/PUT/DELETE`。
  - 给完整可粘贴 Java 代码 + `curl` 测试命令 + 预期 JSON 响应。统一响应 `RtData`。
  - 深链 `[Spring Boot CRUD →](/back-end/java/07-spring-boot-crud)`、`[Spring Data 数据库实战 →](/back-end/java/08-spring-data-db)`。
  - 篇幅 ~250 行。
- [ ] Commit `git commit -m "docs: Part 2 语言与框架地基"`

### Task C3: Part 3 新章（`09`–`13`）

- [ ] `09-sql-vs-nosql.md`：关系型 vs 文档型本质区别、各自擅长场景、本项目为什么 MongoDB 主库 + MySQL 做统计、选型决策清单。前端类比（结构化表格 vs JSON）。深链 `/back-end/database/basics/relational-vs-nosql`。~180 行。
- [ ] `10-sql-and-indexes.md`：前端必须会的 SQL（SELECT/JOIN/GROUP BY/分页）、索引是什么（类比书的目录/JS Map）、为什么慢查询、`EXPLAIN` 怎么读、N+1 问题与解决、最左前缀。给真实 `EXPLAIN` 输出样例。深链 `/back-end/database/mysql/advanced`、`/back-end/database/mysql/sql-basics`。~280 行。
- [ ] `11-transactions-consistency.md`：事务 ACID、四种隔离级别（脏读/不可重复读/幻读）、Spring `@Transactional` 用法与坑（自调用失效）、分布式下为什么难、最终一致性、本项目用 MQ 实现最终一致的例子。~260 行。
- [ ] `12-redis-in-practice.md`：迁移现 `03-核心技术点拆解.md` 的 3.6 Redis 内容并大幅扩充 —— 5 种数据结构与用途、cache-aside 模式、缓存穿透/击穿/雪崩及对策、分布式锁（SETNX + 过期 + Redisson）、限流计数。给 `redis-cli` 命令样例。深链 `/back-end/database/redis/intro`、`/back-end/database/redis/java-redis`。~300 行。
- [ ] `13-connection-pools.md`：什么是连接池（DB 连接、HTTP 连接）、为什么需要（建连接昂贵）、池满会发生什么（请求排队/超时）、HikariCP/Feign 连接池关键参数、怎么从现象判断池被打满（引 `28`）。前端类比（浏览器对同域并发连接数限制）。~200 行。
- [ ] Commit `git commit -m "docs: Part 3 数据与存储"`

### Task C4: Part 4 新章（`14`–`20`）— 并发与 JVM 🔥

- [ ] `14-single-thread-to-multithread.md`：JS 事件循环单线程模型回顾 → Java 多线程模型；同一段代码被多线程同时执行意味着什么；为什么前端没有"线程安全"概念；线程 vs 进程；`Thread` 基本用法。**核心认知转变**，多用对比图。~250 行。
- [ ] `15-thread-pools-executor.md`：为什么不每请求新建线程（Tomcat 线程池）、`ExecutorService`/`ThreadPoolExecutor` 七参数、核心/最大线程数/队列/拒绝策略、`@Async` 配自定义线程池、线程池满的症状。给真实配置代码 + 监控指标。前端类比（请求并发上限、任务队列）。~280 行。
- [ ] `16-thread-safety-locks.md`：竞态条件实例（i++ 不原子）、`synchronized`、`ReentrantLock`、原子类 `AtomicLong`、`volatile`、并发容器 `ConcurrentHashMap`、为什么单例 Service 的成员变量危险。给"错误代码 → 修正代码"对照。~280 行。
- [ ] `17-async-programming.md`：`CompletableFuture`（`supplyAsync`/`thenApply`/`thenCompose`/`allOf`）对比 Promise（`then`/`all`）；`@Async` 用法；阻塞 vs 非阻塞；什么时候用异步。JS↔Java 并排。~240 行。
- [ ] `18-jvm-memory-model.md`：JVM 运行时数据区（堆/栈/方法区-元空间/程序计数器）、对象在堆、引用在栈、栈帧与方法调用、堆分代（新生代/老年代）、`-Xms/-Xmx`。前端类比（V8 堆栈）。给一张内存区域图。~240 行。
- [ ] `19-garbage-collection.md`：为什么需要 GC、可达性分析、为什么有 STW、常见收集器（G1 为主，一句话带 CMS/ZGC）、Minor/Full GC、怎么开 GC 日志（`-Xlog:gc*`）并读关键行、频繁 Full GC 的危害。给真实 GC 日志样例与解读。~260 行。
- [ ] `20-oom-memory-leak.md`：常见 OOM 类型（heap space / Metaspace / GC overhead / unable to create native thread）、内存泄漏典型原因（静态集合只加不删、未关闭资源、线程池泄漏、ThreadLocal 未清）、`-XX:+HeapDumpOnOutOfMemoryError`、用 MAT 读 dominator tree。承接到 `28`/`29`。~260 行。
- [ ] Commit `git commit -m "docs: Part 4 并发与 JVM"`

### Task C5: Part 5 新章（`21`–`25`）— 服务器与容器 🔥

- [ ] `21-linux-server-essentials.md`：SSH 登录（`ssh user@host`、密钥）、文件与目录（`ls/cd/cat/less/tail -f/find`）、权限（`chmod/chown/sudo`）、进程（`ps -ef`/`top`/`kill -9`/`jps`）、网络（`ss -lntp`/`curl`/`ping`/`telnet`）、磁盘（`df -h`/`du -sh`）、环境变量（`export`/`env`）、查看与管理服务（`systemctl status/restart`、`journalctl -u`）。每条给可复制命令 + 典型输出。写给只用过图形界面的前端。~300 行。
- [ ] `22-networking-for-backend.md`：从后端视角看 TCP/HTTP/HTTPS、端口与监听、为什么 `connection refused`/`timeout`/`502`/`504` 不同、DNS 解析、防火墙/安全组、内外网、用 `curl -v`/`telnet`/`nc` 验证连通性。承接前端 HTTP 知识。~240 行。
- [ ] `23-docker-in-practice.md`：镜像 vs 容器（类比类与实例 / 安装包与运行的程序）、给 Spring Boot 写多阶段 `Dockerfile`、`docker build/run/ps/logs/exec/stop/rm`、端口映射 `-p`、数据卷 `-v`、环境变量 `-e`、网络、`docker-compose` 起 Mongo+Redis+RocketMQ 一套。给完整 Dockerfile 与 compose 文件。~320 行。
- [ ] `24-kubernetes-in-practice.md` 🔥：为什么需要 K8s（自愈/扩缩容/滚动发布）、核心对象（Pod/Deployment/Service/Ingress/ConfigMap/Secret，每个一句话 + YAML 片段）、`kubectl` 必会命令（`get pods`/`describe`/`logs -f`/`exec -it`/`apply`/`rollout status`/`rollout undo`/`scale`/`top pod`）、看 Pod 日志与进容器、滚动发布与回滚、扩缩容、**排查三连**：CrashLoopBackOff / ImagePullBackOff / OOMKilled 各自怎么看怎么解。给真实 `kubectl describe`/`logs` 输出样例。~380 行。
- [ ] `25-config-and-env.md`：12-factor 配置原则、`application-{env}.yml` 多环境、环境变量覆盖、ConfigMap/Secret 注入、配置中心一句话、敏感信息不进代码库。承接现有「配置管理」内容（现 `03` 的 3.3）。~200 行。
- [ ] Commit `git commit -m "docs: Part 5 服务器与容器"`

### Task C6: Part 6 新章（`26`–`30`）— 排查与可观测 🔥

- [ ] `26-reading-logs.md` 🔥：日志级别（trace/debug/info/warn/error 何时用）、`@Slf4j` 与占位符 `log.info("uid={}", uid)`、logback 配置（输出格式/文件滚动）、日志去哪了（容器 stdout → `kubectl logs` / 文件 / ELK）、`grep`/`tail -f`/`less` 实战、用 traceId/requestId 串联一次完整请求链路（承接现 `02` 的 requestId）、ELK/Loki+Grafana 一句话。给真实日志行与"怎么从一行日志定位代码"。~300 行。
- [ ] `27-troubleshooting-methodology.md`：排查心法（先复现/先看日志/先看监控）、标准流程（现象 → 定位是哪个服务哪一层 → 看日志/指标 → 假设 → 验证）、二分法、"最近改了什么/发了什么版"、不要瞎改。给一张排查决策流程图。~200 行。
- [ ] `28-troubleshooting-playbook.md` 🔥：**按症状的实战手册**，每个症状给「可能原因 → 排查命令 → 怎么读 → 解法」：
  - 接口 500/报错 → 看异常栈定位抛出点。
  - 接口慢 → 慢在哪层（DB 慢查询 `EXPLAIN` / 下游 Feign 超时 / 锁等待 / GC），用日志耗时 + APM。
  - CPU 飙到 100% → `top -Hp <pid>` 找热点线程 → `printf %x` 转十六进制 → `jstack` 定位代码。
  - 内存暴涨/OOM → `jmap -histo` / heap dump + MAT。
  - 线程卡住/死锁 → `jstack` 看 BLOCKED / deadlock 段。
  - 服务频繁重启 → `kubectl describe pod` 看 OOMKilled / 健康检查失败 / 退出码。
  - 数据不对/偶发 → 日志 + DB 核对 + 并发竞态怀疑。
  给每步真实命令与输出样例。~380 行。
- [ ] `29-diagnostic-toolbox.md`：JDK 自带（`jps`/`jstack`/`jmap`/`jstat`/`jcmd` 各干什么 + 示例）、**Arthas** 在线诊断（`dashboard`/`thread`/`trace`/`watch`/`jad` 常用）、`top/htop`、`curl`/Postman、`tcpdump` 入门。给每个工具"什么场景用它"。~260 行。
- [ ] `30-observability.md`：可观测三支柱（Metrics/Tracing/Logging 区别与配合）、Spring Boot Actuator + Micrometer 暴露指标、Prometheus 抓取 + Grafana 看板（关键指标：QPS/RT/错误率/GC/线程/连接池）、链路追踪让 traceId 贯穿、告警思路。~260 行。
- [ ] Commit `git commit -m "docs: Part 6 排查与可观测"`

### Task C7: Part 7 新章（`31`–`36`）

- [ ] `31-performance-concurrency.md`：缓存策略深入（多级缓存/热点）、限流算法（计数器/滑动窗口/漏桶/令牌桶，各配图与适用）、降级与熔断（Sentinel/Resilience4j 思路，承接现有网关熔断）、批处理/异步化、压测入门（`wrk`/JMeter + 看 QPS/RT/P99）。~280 行。
- [ ] `32-security.md`：认证 vs 授权、JWT 结构与校验、OAuth2 一句话、密码存储（bcrypt 加盐，绝不明文）、常见漏洞（SQL 注入/XSS/CSRF/越权 IDOR/敏感信息泄露）及后端对策、HTTPS、密钥/Secret 管理。承接前端 web 安全（链 `/front-end/the-basics/network-basics/webSafety`）。~280 行。
- [ ] `33-mq-reliability.md`：迁移现 `03` 的 3.5 RocketMQ 内容并扩充 —— 消息丢失（三阶段）/重复/顺序问题、消费幂等（去重表/唯一键）、重试与死信队列、事务消息、削峰填谷。承接现有异步链路。~280 行。
- [ ] `34-api-design.md`：RESTful 资源命名、HTTP 方法与状态码语义、统一响应/错误码规范（`RtData`）、分页约定、幂等设计、版本管理（`/v1`）、给前端友好的 API。前端视角（你最讨厌什么样的后端接口）。~220 行。
- [ ] `35-testing.md`：后端测试金字塔、JUnit 5 基础、Mockito mock 依赖、`@SpringBootTest` 集成测试、Testcontainers 起真实 DB 测试、对比前端 jest（`describe/it/expect` ↔ JUnit）。给可运行测试代码。~240 行。
- [ ] `36-cicd-deployment.md`：CI/CD 概念、流水线阶段（拉代码→构建→测试→打镜像→推仓库→部署）、GitHub Actions/GitLab CI 示例、构建并推 Docker 镜像、部署到 K8s（`kubectl apply`/`set image`）、蓝绿/金丝雀/滚动发布区别。承接本仓库 `deploy.yml` 类比。~260 行。
- [ ] Commit `git commit -m "docs: Part 7 工程进阶"`

### Task C8: `38-capstone-feature-to-prod.md` — 综合实战

- [ ] 内容简报：以"给用户加一个「我的作品收藏」功能"为主线，串起全课程：
  1. 设计 API（`34`）→ 2. 写 Entity/Repository/Service/Controller（`07`）→ 3. 加 Redis 缓存（`12`）→ 4. 收藏数统计走异步 MQ（`33`）→ 5. 写测试（`35`）→ 6. Docker 化（`23`）→ 7. 部署 K8s（`24`/`36`）→ 8. 配日志与指标（`26`/`30`）→ 9. 上线后模拟一个慢/报错问题并按手册排查（`28`）。
  - 每步给关键代码/命令片段 + 指向对应章节深链。
  - 篇幅 ~320 行。
- [ ] Commit `git commit -m "docs: Part 8 综合实战"`

### Task C9: `91-command-cheatsheet.md` — 命令速查卡

- [ ] 内容简报：一页纸速查，分区：Linux（文件/进程/网络/磁盘/服务）、Docker、kubectl、JVM 诊断（jps/jstack/jmap/jstat/jcmd/arthas）、Redis-cli、curl。每条一行命令 + 一句用途。交叉链接到对应章节。~200 行。
- [ ] Commit `git commit -m "docs: 命令速查卡"`

---

## Phase D — 收尾：删除旧文件 + 全局校验

### Task D1: 删除旧中文文件名文件

**Files:** Delete 9 个旧文件（见文件总览"删除"清单）。

- [ ] **Step 1:** 确认内容已全部迁移后删除：

```bash
cd docs/src/back-end/frontend-backend-guide
git rm "01-项目整体架构.md" "02-一个请求的完整链路.md" "03-核心技术点拆解.md" \
       "04-目录结构导读.md" "05-关键注解速查.md" "06-给前端的学习路径.md" \
       "07-动手练习.md" "08-术语对照表.md" "09-学习资源推荐.md"
```

- [ ] **Step 2: Commit** `git commit -m "docs: 删除已迁移的旧章节文件"`

### Task D2: 全局链接与构建校验

- [ ] **Step 1: Markdown 死链校验**

Run: `python scripts/vp_check_links.py`
Expected: `broken-links.json` 无本指南相关新增死链。

- [ ] **Step 2: 配置链接校验**

Run: `python scripts/vp_check_config_links.py`
Expected: `config-broken-links.json` 无本指南相关条目。

- [ ] **Step 3: 构建校验**

Run: `pnpm docs:build`
Expected: 构建成功，无死链 / SSR 报错。

- [ ] **Step 4:** 若有死链：定位修复（多为交叉链接路径或 `.md` 后缀问题），重跑直至通过。

- [ ] **Step 5: 最终提交**（若 Step 4 有改动）

```bash
git commit -am "docs: 修复课程交叉链接，构建通过"
```

---

## Self-Review（计划对照 spec）

- **Spec §5 课程结构** → Phase A 建全部文件 + Phase B/C 填内容，逐章覆盖 ✅
- **Spec §6 迁移映射** → Task B1–B5（index/Part1/注解/附录/练习）+ Task C3 的 `12`（Redis）/C7 的 `33`（MQ）吸收现 `03-核心技术点拆解` 的拆分 ✅
- **Spec §4/§7 交叉引用** → C2/C3 各章明确列出深链目标（均为已验证存在文件）✅
- **Spec §8 导航** → Task A2 重写 sidebar；nav.js 经核查指向 `index.md` 无需改 ✅
- **Spec §9 验收** → Phase D（删旧文件 + 三项校验 + 构建）覆盖全部 7 条验收标准 ✅
- **重点模块实操** → `24`/`26`/`28`/`29` 及 Part 4 各章简报均要求真实命令 + 输出样例 ✅
- **Placeholder 扫描** → 各章任务均给出 sections + 必讲点 + 命令/示例方向，无 TODO/TBD ✅
- **一致性** → 文件名、`RtData`/`svc-*`/`cpt-*` 命名、链接省略 `.md` 全计划统一 ✅
```

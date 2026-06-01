# 术语对照表

> 前端转后端最大的障碍之一就是术语：同一个概念，前端叫一个名字，后端换了套词汇，听着像天书，其实你早就会了。
> 这张表帮你用已有的前端知识（React/Vue/Node/axios/Promise/zod）快速"翻译"后端概念。
> 建议当成字典用——读到本课程其它章节遇到生词，回来查一下。

本表所有举例尽量贴近本课程贯穿的 **AI 生图微服务**（`svc-gateway` / `svc-auth` / `svc-user` / `svc-ai` / `svc-canvas` / `svc-oss`，共享组件 `cpt-common` / `cpt-api` 等，统一响应类型 `RtData`）。

---

## 核心架构术语

后端代码的分层（Controller → Service → Repository）和你写 Next.js 的 `route handler → service hook → api 层`其实是同一套思路，只是名字更"正式"。

| 后端术语 | 前端对应概念 | 详细说明 |
| --- | --- | --- |
| **Controller** | API Route Handler / `router.get()` / Next.js `app/api/.../route.ts` | 接收 HTTP 请求的入口，只负责参数校验和调 Service，不写业务逻辑。例：`svc-auth` 的 `LoginController` 收登录请求 |
| **Service** | 业务逻辑 Hook / Composable | 核心业务逻辑所在，不直接碰 HTTP。例：`svc-user` 的 `QuotaService` 负责扣配额 |
| **Repository / Mapper** | Data Access Layer / `api/*.ts` | 操作数据库的封装层，提供 CRUD 方法，调用方不用关心底层是 MongoDB 还是 MySQL |
| **Entity / Document** | TypeScript `interface` / `type` | 数据模型定义，描述数据库里一条记录的结构。MongoDB 里叫 Document，MySQL 里对应一个 Entity |
| **DTO**（Data Transfer Object） | 请求参数的 `type` + zod schema | 专门接收请求参数的类，常带校验注解（`@NotBlank` 类似 zod 的 `.min(1)`） |
| **VO**（View Object） | 响应数据的 `type` | 专门返回给前端的数据类，常隐藏掉密码、内部字段等敏感信息 |
| **RtData** | axios 响应里统一的 `{ code, data, msg }` 包装 | 本项目的统一响应类型。`RtData.ok(data)` 成功、`RtData.fail(msg)` 失败，前端只看 `code` 判断成败 |
| **POJO / Bean** | 普通 JS 对象（plain object） | "Plain Old Java Object"，就是一个只有字段和 get/set 的普通对象 |

详见 [三层架构与项目结构](/back-end/frontend-backend-guide/04-three-layer-and-structure)。

## Spring 框架术语

Spring 干的事，本质上就是一个超大号的"依赖注入容器 + 装饰器系统"。如果你用过 NestJS，会觉得似曾相识——NestJS 正是借鉴了 Spring。

| 后端术语 | 前端对应概念 | 详细说明 |
| --- | --- | --- |
| **IoC 容器** | NestJS 的 DI 容器 / Vue 的 `provide/inject` 体系 | "控制反转"——对象不自己 `new` 依赖，交给框架统一创建和管理 |
| **Bean** | 单例实例 / 模块级单例 | Spring 容器管理的对象，默认全局共享一个实例 |
| **@Autowired / 构造器注入** | `inject()` / `useContext()` | 依赖注入——框架帮你把依赖塞进来，不用手动 `new` |
| **@Component / @Service / @Repository** | `@Injectable()`（NestJS） | 标记一个类交给 Spring 托管，分别表示通用组件 / 业务层 / 数据层 |
| **AOP / Aspect** | 高阶函数 / 装饰器 / axios 拦截器 | "面向切面"——给一批方法统一加日志、鉴权、事务，不污染业务代码 |
| **Filter** | Express middleware | 最外层拦截器，在请求到达 Controller 之前执行（如 `svc-gateway` 的鉴权 Filter） |
| **Interceptor** | axios 请求/响应拦截器 | Spring MVC 层的钩子，在 Controller 前后执行 |
| **@Transactional** | 手写 `try/commit/rollback` 的封装 | 加在方法上，方法内多个 DB 操作要么全成功要么全回滚 |

详见 [Spring 的 IoC 与 DI](/back-end/frontend-backend-guide/06-spring-boot-ioc-di) 和 [Spring IoC/DI 实战](/back-end/java/07a-spring-ioc-di)。

## 基础设施术语

| 后端术语 | 前端对应概念 | 详细说明 |
| --- | --- | --- |
| **application.yml** | `.env` / `config.ts` | 配置文件，存端口、数据库地址、Redis 地址等。多环境用 `application-dev.yml` / `application-prod.yml` |
| **Maven（pom.xml）** | npm / pnpm（`package.json`） | 依赖管理工具，声明项目依赖的第三方库；`mvn package` 类似 `pnpm build` |
| **Feign Client** | 封装好的 axios 实例 | 声明式 HTTP 客户端，调远程服务像调本地方法。本项目的 Feign 客户端都放在 `cpt-api` |
| **RocketMQ** | `EventEmitter` / `postMessage` | 消息队列，发完即走、异步解耦。例：生图任务完成后发消息通知 `svc-user` 扣费 |
| **Redis** | `sessionStorage` / 内存 `Map` | 高速缓存，本项目用于缓存、限流、分布式锁 |
| **JWT** | `localStorage` 里的 token | JSON Web Token，服务器颁发的身份凭证，前端每次请求带在 `Authorization` 头里 |
| **OSS** | CDN / 云存储 | 对象存储，存生图结果等大文件，返回一个 URL 给前端 |
| **Nginx / 网关** | dev server 的 proxy 配置 | 反向代理，统一入口、转发请求 |
| **K8s ConfigMap** | CI/CD 注入的环境变量 | 容器运行时注入的非敏感配置 |

详见 [配置与环境管理](/back-end/frontend-backend-guide/25-config-and-env) 和 [Spring 配置](/back-end/java/07b-spring-config)。

## 数据库术语

后端的一大块工作就是和数据库打交道。本项目主库是 MongoDB（文档型），统计用 MySQL（关系型），缓存用 Redis。

| 后端术语 | 前端对应概念 | 详细说明 |
| --- | --- | --- |
| **Collection（MongoDB）** | 一个 JSON 数组文件 | MongoDB 里的"表"，存一堆同类文档。如 `canvas_task` 集合存所有生图任务 |
| **Document（MongoDB）** | 一个 JSON 对象 | MongoDB 里的"行"，一条数据记录，结构灵活 |
| **Table（MySQL）** | Excel 表格 | MySQL 里的表，列定义固定，改结构要 `ALTER TABLE` |
| **Row / Record（MySQL）** | 表格中的一行 | MySQL 里的一条记录 |
| **Index（索引）** | `Map` 的 key / 字典目录 | 加速查询的数据结构，没索引就全表扫描（相当于遍历整个数组） |
| **ORM / ODM** | Prisma / Drizzle | 用对象操作数据库而非手写 SQL，如 Spring Data JPA、MyBatis |
| **事务（Transaction）** | "原子操作"——要么都做要么都不做 | 多个 DB 操作捆绑：扣配额 + 写订单，必须一起成功，否则回滚 |
| **乐观锁 / 悲观锁** | 版本号比对 vs 加锁 | 乐观锁靠 `version` 字段冲突重试；悲观锁直接 `SELECT ... FOR UPDATE` 锁住 |

详见 [SQL vs NoSQL](/back-end/frontend-backend-guide/09-sql-vs-nosql)、[SQL 与索引](/back-end/frontend-backend-guide/10-sql-and-indexes) 和 [关系型 vs NoSQL](/back-end/database/basics/relational-vs-nosql)。

## 微服务术语

单体应用拆成一堆小服务后，多出很多"协调"相关的词。可以类比成：前端从一个大 SPA 拆成了一堆微前端 + BFF，彼此通过网络调用。

| 后端术语 | 前端对应概念 | 详细说明 |
| --- | --- | --- |
| **网关（Gateway）** | dev server proxy / Nginx 反向代理 | 统一入口，做路由、鉴权、限流。本项目即 `svc-gateway` |
| **服务发现（Service Discovery）** | DNS 解析 / 服务注册中心 | 不写死 IP，靠名字（如 `svc-ai`）找到目标服务的实例地址 |
| **熔断（Circuit Breaker）** | 错误边界（Error Boundary） | 某服务挂了就快速失败、不再调用，避免雪崩。类似断路器跳闸 |
| **降级（Fallback）** | 兜底 UI / 默认值 | 服务不可用时返回替代数据，如返回缓存旧结果或"系统繁忙" |
| **限流（Rate Limiting）** | 防抖 / 节流 | 限制单位时间请求数，保护服务。如每用户每分钟最多提交 5 次生图 |
| **负载均衡（Load Balancing）** | — | 把请求分散到多个服务实例，避免单点过载 |
| **Topic（消息队列）** | 事件名（`eventBus.on('xxx')`） | 消息的分类标识，生产者发到 Topic，消费者订阅 Topic |
| **Consumer Group** | — | 一组消费者共同消费一个 Topic，组内分摊消息，组间各收一份 |
| **幂等（Idempotent）** | 同一请求重复发结果不变（如 `PUT`） | 同一个生图任务消息消费两次，不能扣两次配额 |

详见 [架构总览](/back-end/frontend-backend-guide/02-architecture-overview) 和 [API 设计](/back-end/frontend-backend-guide/34-api-design)。

## 并发与 JVM 术语

这块是前端最陌生的领域。前端是单线程 + 事件循环（你写 `Promise`、`async/await` 时不用操心"两段代码同时改同一个变量"），而 Java 是真·多线程：一个服务里几百个线程真的在同时跑，于是有了锁、原子类、死锁这些概念。

| 后端术语 | 前端对应概念 | 详细说明 |
| --- | --- | --- |
| **线程（Thread）** | 一个独立的"事件循环"在跑 | 真正并行的执行单元。Tomcat 默认开一批线程，每个请求占一个 |
| **线程池（Thread Pool）** | 复用连接的连接池 / 任务队列 | 预先创建一批线程循环复用，避免频繁创建销毁。如 `svc-ai` 用线程池跑生图任务 |
| **synchronized / Lock** | （前端没有，因为单线程不需要） | 加锁：保证同一时刻只有一个线程进入这段代码。`Lock` 是更灵活、可手动加解锁的版本 |
| **原子类（AtomicInteger 等）** | — | 无锁的线程安全计数器，`incrementAndGet()` 不会出现"读了又被别人改"的问题 |
| **竞态条件（Race Condition）** | 两个 `setState` 互相覆盖那种感觉 | 多线程不加保护地改同一数据，结果取决于执行顺序，时对时错最难查 |
| **死锁（Deadlock）** | 两个 `await` 互相等对方（循环依赖卡死） | 线程 A 拿着锁 1 等锁 2，线程 B 拿着锁 2 等锁 1，谁都不让，永久卡住 |
| **堆（Heap）** | JS 引擎里存对象的那块内存 | 存放 `new` 出来的对象，所有线程共享，GC 主要管这里 |
| **栈（Stack）** | 函数调用栈（call stack） | 每个线程一份，存方法调用和局部变量，方法返回就弹出 |
| **GC（垃圾回收）** | V8 的垃圾回收（你从不手动管） | 自动回收没人引用的对象内存。Java 和 JS 都有，但 Java 可调参数多得多 |
| **STW（Stop-The-World）** | 主线程被一段同步代码卡住、页面卡顿 | GC 某些阶段会暂停所有业务线程，停顿过长就是性能问题 |
| **Minor GC / Full GC** | 小扫一遍 vs 大扫除 | Minor GC 只清新生代、快；Full GC 清整个堆、慢且常伴随长 STW，频繁 Full GC 是危险信号 |
| **OOM（OutOfMemoryError）** | 标签页"Aw, Snap"内存崩溃 | 内存不够用，JVM 直接抛错。常见于内存泄漏或堆设得太小 |
| **内存泄漏（Memory Leak）** | 忘了 `removeEventListener` 导致对象回收不掉 | 对象用完了却还被引用，GC 回收不了，越积越多最终 OOM |
| **thread dump（线程快照）** | 把所有"事件循环"当前在干什么打印出来 | 某一刻所有线程的调用栈快照，查死锁、查卡住、查 CPU 飙高的首选 |
| **heap dump（堆快照）** | Chrome DevTools 的 Heap Snapshot | 某一刻堆里所有对象的快照，用来查内存泄漏到底是谁占着不放 |
| **Metaspace** | — | 存类元数据的独立内存区（不在堆里），类加载过多或动态生成类会把它撑爆 |

详见 [线程池与 Executor](/back-end/frontend-backend-guide/15-thread-pools-executor)、[线程安全与锁](/back-end/frontend-backend-guide/16-thread-safety-locks)、[JVM 内存模型](/back-end/frontend-backend-guide/18-jvm-memory-model)、[垃圾回收](/back-end/frontend-backend-guide/19-garbage-collection) 和 [OOM 与内存泄漏排查](/back-end/frontend-backend-guide/20-oom-memory-leak)。

## 运维与可观测术语

代码写完只是开始，真正"学成后端"还得会把它跑到服务器上、塞进容器、上 K8s、出问题能从日志和指标里把锅刨出来。这部分前端基本是空白，但概念其实不难。

| 后端术语 | 前端对应概念 | 详细说明 |
| --- | --- | --- |
| **容器（Container）** | "把整个 node_modules + node 版本 + OS 一起打包" | 一个隔离的运行环境，保证"我本地能跑"等于"线上也能跑" |
| **镜像（Image）** | 容器的"安装包" / Docker 版的 `dist` 产物 | 只读模板，容器是镜像跑起来的实例。一个镜像可启多个容器 |
| **Docker** | — | 最主流的容器工具，负责构建镜像、运行容器 |
| **Dockerfile** | 描述如何构建产物的脚本（Docker 版 `build.sh`） | 一行行指令告诉 Docker 如何打包镜像：基础镜像、拷贝 jar、启动命令 |
| **Pod** | K8s 里最小的部署单位（≈ 一台小机器） | 一个或多个容器的组合，本项目里通常一个 Pod 跑一个 `svc-*` 实例 |
| **Deployment** | 声明"我要几个副本"的清单 | 管理一组同样的 Pod，控制副本数、滚动更新、自愈重启 |
| **Service（K8s）** | 一个稳定的内部域名 + 负载均衡 | 给一组会变动的 Pod 提供固定访问入口，别和上面业务层的 Service 混淆 |
| **Ingress** | 前端的路由表 / Nginx 配置 | 七层入口，把外部域名/路径映射到内部 Service |
| **ConfigMap** | `.env` 文件（非敏感） | 以键值对注入容器的非敏感配置 |
| **Secret** | 加密存放的 `.env`（敏感） | 存密码、密钥、token 的配置，内容做 base64/加密处理 |
| **kubectl** | K8s 的命令行 CLI（类比 `git` 之于 GitHub） | 操作集群的命令行工具，如 `kubectl get pods`、`kubectl logs` |
| **CrashLoopBackOff** | 进程一启动就崩、反复重启那种状态 | Pod 启动后立刻挂、K8s 不停重启又不停挂，重试间隔越拉越长。多半是配置错或启动报错 |
| **OOMKilled** | 标签页因内存超限被浏览器杀掉 | 容器内存超过 limit，被系统强杀。看到它先查内存上限和泄漏 |
| **滚动发布（Rolling Update）** | 灰度替换、逐步上线 | 一批批替换旧 Pod，过程中服务不中断，K8s 默认策略 |
| **蓝绿发布（Blue-Green）** | 准备一套新环境，切流量可一键回滚 | 新旧两套环境并存，流量整体切到新的，出问题秒回滚 |
| **金丝雀发布（Canary）** | A/B 测试只放 5% 流量 | 先放一小部分流量到新版本，观察没问题再全量 |
| **日志级别（Log Level）** | `console.debug/info/warn/error` | TRACE/DEBUG/INFO/WARN/ERROR 五档，线上一般只开 INFO 以上 |
| **traceId** | 一次请求贯穿的请求 ID（链路追踪 header） | 一个请求穿过 `svc-gateway → svc-canvas → svc-ai` 都带同一个 id，便于把分散的日志串起来 |
| **Logging / Metrics / Tracing** | 控制台日志 / 性能埋点 / 请求瀑布图 | 可观测性三大支柱：日志（发生了什么）、指标（多少/多快）、链路（请求走过哪些服务） |
| **限流（Rate Limiting）** | 防抖节流 | 见上表，运维侧常在网关统一配置 |
| **熔断（Circuit Breaker）** | 错误边界 | 见上表，依赖服务异常时快速失败保护自己 |
| **降级（Degradation/Fallback）** | 兜底 UI | 见上表，主功能挂了给个最小可用结果 |
| **幂等（Idempotent）** | 重试安全（同一 `PUT` 发几次结果一样） | 接口/消息支持重复执行不出错，是重试、消息消费的前提 |
| **连接池（Connection Pool）** | axios 的 `keep-alive` 连接复用 | 预建一批数据库/HTTP 连接循环复用，避免每次新建握手的开销 |
| **CI/CD** | GitHub Actions / Vercel 自动部署 | 持续集成（自动构建测试）/ 持续部署（自动发布），本项目 push `main` 即触发 |

详见 [Docker 实战](/back-end/frontend-backend-guide/23-docker-in-practice)、[Kubernetes 实战](/back-end/frontend-backend-guide/24-kubernetes-in-practice)、[看懂日志](/back-end/frontend-backend-guide/26-reading-logs)、[可观测性](/back-end/frontend-backend-guide/30-observability) 和 [CI/CD 与部署](/back-end/frontend-backend-guide/36-cicd-deployment)。

---

## 小结

- 后端术语大多能在前端找到类比：Controller≈route handler、依赖注入≈`useContext`、熔断≈Error Boundary、连接池≈axios keep-alive，认知负担没想象中大。
- **真正陌生的是两块**：并发/JVM（前端单线程，没有锁、死锁、GC 调参这些概念）和运维/可观测（容器、K8s、日志链路）。这两张表值得反复看。
- 排查问题时术语就是"检索关键词"：看到 `OOMKilled` 先查内存上限和泄漏，看到 `CrashLoopBackOff` 先看启动日志，看到 Full GC 频繁先抓 heap dump。
- `RtData`、`svc-*`、`cpt-*` 是本课程贯穿项目的命名约定，遇到先对号入座。
- 这张表是"字典"，不必背；遇到生词回来查，看多了自然记住。

### 自测

1. 后端业务层的 **Service** 和 K8s 里的 **Service** 是同一个东西吗？它们各自类比前端的什么？
2. 用前端概念解释一下：为什么前端代码几乎不用关心"竞态条件"和"锁"，而后端 Java 必须关心？
3. 线上一个 `svc-ai` 的 Pod 反复重启、状态显示 `OOMKilled`，结合本表的术语，你会先去查哪几样东西？

### 下一章

把这些术语对应的高频命令攒成手边速查表，见 [命令速查表](/back-end/frontend-backend-guide/91-command-cheatsheet)。

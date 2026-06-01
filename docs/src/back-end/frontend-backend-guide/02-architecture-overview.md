# 项目整体架构

> 如果你只做过前端，可以先把"微服务架构"想象成一句话——
> **你把一个巨大的 Monorepo 拆成了好几个独立部署的应用，它们各自有自己的进程、端口、数据库，彼此之间像前端调后端一样，通过 HTTP / 消息队列通信。**

上一章 [后端思维转变](/back-end/frontend-backend-guide/01-backend-mindset) 帮你把"前端脑"切到"后端脑"。这一章我们鸟瞰整套系统：先认识贯穿全程的运行示例项目，建立一张"前端概念 → 后端概念"的对照表，再把服务怎么拆、怎么通信、怎么找到彼此讲清楚。读完你应该能在脑子里画出整张架构图。

## 技术栈一句话概括

整个 AI 生图微服务，用一句话说清楚每个角色：

- **框架**：Spring Cloud 微服务（Spring Boot + Spring Cloud），相当于后端版的"Next.js 全家桶"。
- **语言**：Java 17（带 record、sealed、switch 模式匹配，比你印象里的老 Java 现代多了）。
- **主库**：MongoDB——存业务数据（用户、任务、画布）。文档型，结构最像前端的 JSON。
- **统计库**：MySQL——存需要聚合分析的报表数据（出图量、消费流水）。
- **缓存 / 限流 / 分布式锁**：Redis。
- **消息队列**：RocketMQ——异步解耦，提交生图任务这类耗时操作走它。
- **对象存储**：云 OSS——存生成的图片这类大文件。
- **部署**：Docker 打镜像，Kubernetes 编排调度。

> 前端类比：这套技术栈的分工，对应你熟悉的 `Next.js（框架）+ Postgres（主库）+ Redis（缓存）+ S3（文件）+ Vercel（部署）`。后端只是把每一块换成了 Java 生态里更"重"也更可控的实现。

## 用前端术语理解后端概念

如果你已经做过 React / Vue / Next.js 项目，这张表能帮你把后端名词快速翻译成你已经会的东西：

| 后端概念 | 前端类比 | 在本项目中长什么样 |
| --- | --- | --- |
| **微服务** | 把一个大型 Monorepo 拆成多个可独立部署的应用 | `svc-gateway`、`svc-user`、`svc-ai` 等各自独立启动、独立扩容 |
| **网关 (Gateway)** | Nginx 反向代理 / 前端 BFF 统一入口 | `svc-gateway`：所有外部请求先到它，再转发给具体服务 |
| **Feign Client** | 封装好的 `axios` 实例，调远端接口像调本地函数 | `cpt-api` 里声明接口，运行时自动发 HTTP 请求 |
| **Controller** | Next.js 的 `app/api/` 路由 / Express 的 `router.get()` | 每个服务的 `controller/` 目录 |
| **Service** | 前端 service 层 / 业务逻辑 composable / hook | 每个服务的 `service/` 目录，写真正的业务 |
| **Repository / Mapper** | 你对 `localStorage` / IndexedDB / fetch 的读写封装 | 每个服务的 `repository/` 目录，专管"和数据库说话" |
| **DTO / VO** | TypeScript 的 `interface` / `type` | `cpt-api` 与各服务 `dto/`、`vo/` 里的 Java 类 |
| **统一响应 RtData** | axios 拦截器里统一包装的 `{ code, data, msg }` | `cpt-common` 里的 `RtData`，全项目接口都返回它 |
| **消息队列 (MQ)** | EventBus / postMessage / Redux middleware | RocketMQ——发一条消息出去，另一个服务在后台慢慢消费 |
| **Redis 缓存** | 浏览器 `sessionStorage` / 前端内存缓存 | 速度快，用于缓存热数据、限流计数、分布式锁 |
| **共享组件 (cpt-\*)** | `@org/utils`、`@org/http-client` 等内部 npm 包 | `cpt-common`、`cpt-redis`、`cpt-mongodb` 等基础模块 |

> 这张表后面每一章都会用到。比如 [三层架构与项目结构](/back-end/frontend-backend-guide/04-three-layer-and-structure) 会把 Controller / Service / Repository 讲透；[Redis 实战](/back-end/frontend-backend-guide/12-redis-in-practice) 会展开缓存与分布式锁。

## "微服务 = 拆 Monorepo"，这个类比讲透

很多教程会说"微服务就是把单体拆开"，但拆得**不彻底**和拆得**到位**差别很大。用你熟悉的前端 Monorepo 来对齐，会清晰很多。

你的前端 Monorepo 大概是这样：

```text
my-monorepo/
├── packages/          ← 共享代码，编译进各 app，不单独跑
│   ├── ui/
│   └── utils/
└── apps/              ← 可独立构建、独立部署的应用
    ├── web/           ← 跑在 vercel.com
    └── admin/         ← 跑在 admin.vercel.com
```

关键区别在这里：`packages/*` 是**代码层面**的共享，它们会被打包进 `apps/*`，本身不是一个运行的进程；而 `apps/*` 才是**独立运行、独立部署**的单元。微服务项目是同一个套路：

```text
对照关系：
  前端 packages/ui, packages/utils   →  后端 cpt-common, cpt-redis, cpt-api   （编译进服务，不单独跑）
  前端 apps/web, apps/admin           →  后端 svc-gateway, svc-user, svc-ai    （各自一个进程，独立部署）
```

所以本项目里 `cpt-*` 和 `svc-*` 的本质区别，和你 Monorepo 里 `packages/` 与 `apps/` 的区别**完全一样**：

- `cpt-common` 提供 `RtData`、异常码、工具类，它被编译进每个 `svc-*`，自己不监听端口。这就像 `packages/utils` 被 import 进每个 app。
- `svc-user` 是一个真正在跑的 Java 进程，监听端口、连数据库、能被独立重启和扩容。这就像 `apps/web` 部署在自己的服务器上。

类比再往深推一步——你会发现微服务带来的"新麻烦"，其实前端 Monorepo 也有，只是后端更严重：

| 前端 Monorepo 的烦恼 | 微服务里对应的、更重的烦恼 |
| --- | --- |
| `packages/utils` 改了，所有 app 要重新构建发布 | `cpt-common` 改了，所有 `svc-*` 要重新打镜像、滚动发布 |
| `apps/web` 调 `apps/admin` 的接口，admin 挂了 web 报错 | `svc-canvas` 调 `svc-ai`，ai 挂了任务编排就卡住（需要熔断） |
| 本地要同时 `dev` 起好几个 app 才能联调 | 本地要起一堆服务 + 中间件，所以才需要 Docker Compose 一键拉起 |

> 一句话总结：微服务不是"魔法架构"，它就是把 Monorepo 里**编译期的模块边界，升级成了运行期的进程 + 网络边界**。好处是各服务能独立扩容、独立发布、技术栈隔离；代价是模块间调用从"函数调用"变成了"可能失败、可能超时的网络调用"。

## 什么时候不该上微服务

既然类比讲透了，就该泼一盆冷水——**绝大多数项目一开始都不该上微服务**。这不是凡尔赛，是工程现实。

先说**单体（Monolith）的好处**，它们恰恰是微服务要付出代价才能换回的东西：

- 一次构建、一次部署，没有"哪个服务版本和哪个对不上"的问题。
- 模块间是**函数调用**——不会超时、不会半路丢包、不需要熔断和重试。事务也简单：一个数据库事务就能保证一致性。
- 本地起一个进程就能跑完整业务，断点调试一路 step 到底，不用在六个服务的日志间来回跳。
- 排查问题时，一份日志、一个进程，不存在"分布式链路追踪"这种额外负担。

再说**过早上微服务的代价**，用你能感同身受的方式列：

- **本地开发地狱**：想跑通"提交生图任务"，得同时起 `svc-gateway / svc-auth / svc-user / svc-ai / svc-canvas` 加 MongoDB / Redis / RocketMQ。前端再也不能 `npm run dev` 一把梭。
- **分布式事务**：用户扣配额（`svc-user`）和创建任务（`svc-canvas`）原本一个事务就搞定，拆开后要么引入消息队列做最终一致，要么写一堆补偿逻辑。详见 [事务与一致性](/back-end/frontend-backend-guide/11-transactions-consistency)。
- **排查成本飙升**：一个请求穿过四五个服务，出错要看四五份日志、对四五段链路，没有可观测性体系基本抓瞎。见 [可观测性](/back-end/frontend-backend-guide/30-observability)。
- **运维复杂度**：服务发现、配置管理、灰度发布、网络策略……每个都是新坑。
- **网络不可靠**：本来不会失败的函数调用，现在会超时、会抖动，每个跨服务调用都得考虑重试和幂等。

判断要不要拆的经验法则：

```text
该拆的信号                                不该拆的信号
─────────────────────────────────       ─────────────────────────────
团队较大，多人改同一坨代码频繁冲突        单团队、几个人，沟通成本低
不同模块负载差异巨大（AI 生图烧 GPU，     各模块负载均匀，整体扩容就够
  用户接口很轻）→ 需要独立扩容
某模块需要独立的发布节奏 / 技术栈          发布节奏一致，技术栈统一
业务边界已经清晰稳定                      业务还在快速试错、边界天天变
```

> 务实的路线：**先单体，按清晰的模块边界（包结构）组织代码，等某个模块真的因为负载或团队规模顶不住了，再把它"切"出去**。本课程用微服务做教学示例，是因为你迟早会在公司遇到它、需要看懂它；但你自己起步的项目，从单体开始几乎永远是对的。

## 服务拆分总览

本项目的目录结构长这样，左边对照你的 Monorepo 看：

```text
project-root（父工程，类比 Monorepo 根目录）
│
├── component/                ← 共享基础组件（类比你的 packages/ 或 libs/，编译进服务，不单独跑）
│   ├── cpt-api               ← 服务间调用的 Feign 客户端 + 共享 DTO
│   ├── cpt-common            ← 通用工具类、统一响应 RtData、异常码
│   ├── cpt-mongodb           ← MongoDB 连接与操作封装
│   ├── cpt-mysql             ← MySQL 连接与 ORM 封装
│   ├── cpt-redis             ← Redis 缓存 / 限流 / 分布式锁封装
│   ├── cpt-rocketmq          ← RocketMQ 消息队列封装
│   └── cpt-xxljob            ← xxl-job 分布式定时任务封装
│
├── svc-gateway               ← API 网关（唯一对外暴露的入口）
├── svc-auth                  ← 认证服务（登录 / token 颁发与校验）
├── svc-user                  ← 用户服务（注册 / 配额 / 支付）
├── svc-ai                    ← AI 服务（图片生成 / 文字处理 / 人脸检测）
├── svc-canvas                ← 画布服务（模板 / 作图 / 任务编排，最复杂）
└── svc-oss                   ← 文件存储服务（上传 / 下载 / 云 OSS）
```

每个服务的职责，用一句业务话说清楚：

| 服务 | 职责 | 典型场景 |
| --- | --- | --- |
| `svc-gateway` | 统一入口、路由、认证、限流、熔断 | 所有 App / 前端请求的第一站 |
| `svc-auth` | 颁发与校验登录凭证 | 用户登录拿 token、网关校验 token |
| `svc-user` | 用户资料、配额、支付 | 出图前扣配额，配额不足拒绝 |
| `svc-ai` | 真正调用 AI 模型生图 | 接到任务，烧 GPU 出图 |
| `svc-canvas` | 画布与任务编排（最复杂） | 把一次生图拆成多步骤、协调多服务、管理任务状态 |
| `svc-oss` | 文件上传 / 下载 / 云 OSS 对接 | 把生成的图片存进 OSS，返回访问 URL |

> 前端类比：`svc-gateway` 就是你的 BFF / Nginx 入口层；`svc-canvas` 这种"编排者"角色，很像前端一个负责串联多个接口、管理一长串异步流程的复杂页面控制器。

## 三种服务间通信方式

服务被拆开后，它们怎么"说话"？本项目用三种方式，对号入座：

| 通信方式 | 用途 | 前端类比 | 什么时候用 |
| --- | --- | --- | --- |
| **HTTP（Feign）** | 服务间同步调用，要立刻拿到结果 | `await axios.get('/api/xxx')` | 查用户信息、校验 token——必须等返回 |
| **RocketMQ 消息队列** | 异步耗时任务，发了就不等 | `postMessage` 给 Web Worker 后台干活 | 提交 AI 生图任务，先返回"已受理"，结果稍后通知 |
| **Redis** | 缓存 + 限流 + 分布式锁 | `sessionStorage` + 一个简易互斥锁 | 缓存热数据、限制请求频率、防止并发重复扣配额 |

举两个本项目的真实场景对照：

```text
场景 A：网关校验登录（同步，必须等）
  svc-gateway --Feign--> svc-auth.verify(token) --> 返回 userId 或 401
  类比前端：路由守卫里 await 一个 /me 接口，拿到结果才放行。

场景 B：用户提交生图任务（异步，不等结果）
  svc-canvas --发消息--> RocketMQ --消费--> svc-ai 后台出图 --> 完成后写状态
  前端拿到的是"任务已受理 taskId"，之后轮询任务状态。
  类比前端：丢给 Web Worker 一个重活，主线程不卡，回头问它做完没。
```

Feign 调用在代码里长这样，注意它"调远端像调本地函数"的感觉——这正是它最像 axios 封装的地方：

```java
// cpt-api 里声明：跟前端给某个后端写一个 typed api client 一模一样
@FeignClient(name = "svc-user")
public interface UserClient {
    @GetMapping("/user/{id}")
    RtData<UserVO> getUser(@PathVariable("id") Long id);
}

// svc-canvas 里直接当本地方法调用，底层自动发出 HTTP 请求
RtData<UserVO> resp = userClient.getUser(userId);
if (!resp.isOk()) {
    return RtData.fail("用户不存在");
}
```

> 异步消息的可靠性（消息会不会丢、会不会重复消费）是个大话题，留到 [消息队列可靠性](/back-end/frontend-backend-guide/33-mq-reliability) 专门讲。这里你只需建立直觉：**同步用 Feign，异步用 MQ，缓存和锁用 Redis**。

## 服务发现：注册中心 vs DNS 直连

服务被拆成一堆进程后，有个绕不开的问题：**`svc-canvas` 想调 `svc-ai`，它怎么知道 `svc-ai` 在哪台机器、哪个 IP、哪个端口？** 这就是"服务发现"。

前端类比：你调后端时从来不写 IP，而是写域名 `api.example.com`，由 DNS 把域名解析成 IP。服务发现解决的是后端内部版的同一个问题。两种主流做法：

```text
做法一：注册中心（Nacos / Eureka）
  每个服务启动时主动"上报"自己的地址到注册中心，
  调用方先问注册中心"svc-ai 在哪"，拿到地址再去调。

   svc-ai 启动 ──注册"我在 10.0.0.7:8080"──▶ ┌──────────┐
                                              │ 注册中心  │
   svc-canvas ──"svc-ai 在哪？"────────────▶ │ (Nacos)  │
              ◀──"在 10.0.0.7:8080"───────── └──────────┘
   svc-canvas ──直接调 10.0.0.7:8080──▶ svc-ai

做法二：DNS 直连（容器化 / K8s 环境的常见做法）
  K8s 给每个服务一个固定的 Service 名（相当于内网域名），
  调用方直接用"服务名"当主机名，K8s 内置 DNS 自动解析到健康的实例。

   svc-canvas ──调 http://svc-ai:8080 ──▶ [K8s DNS 解析] ──▶ 某个健康的 svc-ai Pod
```

两者怎么选：

| 维度 | 注册中心（Nacos/Eureka） | DNS 直连（K8s Service） |
| --- | --- | --- |
| 谁负责"找到对方" | 应用自己集成客户端上报 / 查询 | 平台（K8s）负责，应用无感 |
| 额外依赖 | 要部署并维护注册中心 | 复用 K8s 自带能力，零额外组件 |
| 配置 | 应用里配注册中心地址 | 应用里只写服务名当主机名 |
| 适合 | 非容器或混合环境、需要更细的服务治理 | 已经跑在 K8s 上的项目 |

本项目跑在 Kubernetes 上，所以采用 **DNS 直连**——不用额外部署注册中心，Feign 客户端直接用服务名寻址：

```yaml
# svc-canvas 的配置：service.ai.url 在 K8s 里被注入成内网服务名
service:
  ai:
    url: http://svc-ai:8080      # "svc-ai" 由 K8s 内置 DNS 解析到健康实例
```

```java
// Feign 客户端用注入的 url，运行时无需关心 svc-ai 具体在哪台机器
@FeignClient(name = "svc-ai", url = "${service.ai.url}")
public interface AiClient { /* ... */ }
```

> 一句话记忆：**DNS 直连 = 把"服务名"当域名用，让平台帮你解析**；注册中心则是让应用自己维护一本"通讯录"。配置怎么注入、环境怎么区分，见 [配置与环境管理](/back-end/frontend-backend-guide/25-config-and-env)。

## 网关：整套架构的"前台"

网关好比大楼前台，**所有外部请求必须先经过它**。它把"横切关注点"（认证、限流、熔断这些每个服务都需要、但又不该各写一遍的东西）集中处理，让后面的业务服务专心做业务。它主要管四件事：

```text
客户端请求（App / 前端）
        │
        ▼
┌──────────────────────────────────────────┐
│              svc-gateway（API 网关）         │
│                                            │
│  1. 路由转发                                │
│     /api/user/**   → svc-user              │
│     /api/ai/**     → svc-ai                │
│     /api/canvas/** → svc-canvas            │
│     /api/oss/**    → svc-oss               │
│                                            │
│  2. 身份认证                                │
│     取请求头 token → 调 svc-auth 校验         │
│     登录 / 注册等白名单路径跳过认证            │
│                                            │
│  3. 限流                                    │
│     基于 Redis 计数器对每用户 / 每接口限频     │
│     超频直接返回 429，保护后端               │
│                                            │
│  4. 熔断                                    │
│     某服务连续失败时快速失败、走降级           │
│     防止一个服务拖垮整条链路                  │
└──────────────────────────────────────────┘
        │
        ▼
     目标微服务（svc-user / svc-ai / svc-canvas ...）
```

> 前端类比：网关就是一个"超级 Nginx"——除了反向代理（路由转发），还内置了登录校验中间件、限流中间件、熔断中间件。它和你在 Express / Next.js middleware 里写的 `app.use(authMiddleware)`、`app.use(rateLimiter)` 是同一类东西，只是抽到了所有服务之上、用独立进程统一兜底。

把"认证 / 限流 / 熔断"放在网关、而不是每个服务各写一份，本质上和前端"把鉴权逻辑收敛到一个 axios 拦截器、而不是每个请求各写一遍"是同一个工程直觉：**横切逻辑集中维护，业务代码保持干净**。

那么一个真实请求——比如"用户提交一次生图任务"——是怎么从 App 出发，穿过网关、认证、用户、画布、AI 这一连串服务，最后把图片存进 OSS 的？这正是下一章 [请求的一生](/back-end/frontend-backend-guide/03-request-lifecycle) 要带你逐跳走一遍的内容。

## 小结

- 微服务的本质是把 Monorepo 里**编译期的模块边界，升级成运行期的进程 + 网络边界**：`cpt-*` 对应 `packages/`（编译进服务），`svc-*` 对应 `apps/`（独立部署）。
- 微服务不是默认选项。**绝大多数项目应从单体起步**，按清晰模块边界组织代码，等某模块真因负载或团队规模顶不住，再切出去；过早微服务会带来本地开发、分布式事务、排查、运维的多重代价。
- 服务间三种通信：要立刻拿结果用 **HTTP（Feign）**，耗时异步用 **RocketMQ**，缓存 / 限流 / 锁用 **Redis**。
- 服务发现两条路：**注册中心**让应用自己维护通讯录，**DNS 直连**把服务名当域名让平台解析；本项目跑在 K8s 上，用 DNS 直连。
- 网关是统一入口与"超级 Nginx"，集中处理路由、认证、限流、熔断这四类横切关注点，让业务服务专心做业务。

### 自测

1. 用你自己的话解释：`cpt-common` 和 `svc-user` 在"是否独立运行 / 独立部署"这一点上有什么本质区别？它们分别对应前端 Monorepo 里的什么？
2. "用户提交生图任务"为什么适合走 RocketMQ 而不是 Feign 同步调用？如果硬用 Feign 会有什么问题？
3. 在 Kubernetes 环境下，`svc-canvas` 配置里写 `http://svc-ai:8080` 就能调到 AI 服务，背后是谁把 `svc-ai` 解析成了真实 IP？这和前端用域名调接口是不是一回事？

### 下一章

下一章 [请求的一生](/back-end/frontend-backend-guide/03-request-lifecycle)，我们跟着一个真实请求逐跳穿过网关、认证、用户、画布、AI、OSS，看清这套架构是怎么协同工作的。

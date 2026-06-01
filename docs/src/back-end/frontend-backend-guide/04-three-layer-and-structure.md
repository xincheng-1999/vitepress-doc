# 三层架构与目录结构

> 拿到一个后端项目，几十个文件夹，不知道从哪看起？这一章帮你建立全局地图，并讲清楚后端代码"该往哪放"的那条铁律——三层架构。

读完上一章 [请求的一生](/back-end/frontend-backend-guide/03-request-lifecycle)，你已经知道一个请求是怎么从浏览器走到数据库再回来的。这一章我们换个视角：当你 clone 下来一个真实的后端仓库，怎么在最短时间里读懂它、并知道自己写的代码该塞到哪个目录里。

---

## 4.1 先记住一条铁律：三层架构

后端代码 90% 的混乱，都来自"业务逻辑乱放"。Spring 项目里有一条几乎所有团队都遵守的分层约定，叫**三层架构**：

```text
┌─────────────────────────────────────────────────────────────┐
│  Controller 层  ── 只负责"接收请求 / 返回响应"               │
│     · 解析 HTTP 参数、校验入参格式                            │
│     · 调用 Service，拿到结果包成 RtData 返回                  │
│     · 绝不写业务逻辑、绝不直接碰数据库                        │
├─────────────────────────────────────────────────────────────┤
│  Service 层    ── 业务逻辑的唯一归宿                          │
│     · "扣配额前先检查余额""任务重复提交要拦截"等规则全在这   │
│     · 编排：调多个 Repository、发 MQ、读 Redis               │
│     · 不关心 HTTP（不碰 request/response），也不写 SQL 细节   │
├─────────────────────────────────────────────────────────────┤
│  Repository / Mapper 层 ── 只跟数据库打交道                  │
│     · 增删改查、拼查询条件                                    │
│     · 不懂业务（它不知道"为什么"要查，只负责"怎么"查）       │
└─────────────────────────────────────────────────────────────┘
```

把这条铁律拆成三句话，背下来：

- **Controller 不写业务**：它是个"翻译官"，把 HTTP 世界（JSON、Header、状态码）翻译成 Java 方法调用，再把结果翻译回去。一旦你在 Controller 里写了 `if (用户余额 < 价格)` 这种判断，就已经越界了。
- **Service 不碰数据库细节**：它只说"我要这个用户的配额"，至于是查 MongoDB 还是查 Redis 缓存、用什么索引，那是 Repository 的事。
- **Mapper（Repository）不管业务**：它只会"按 id 查用户""更新某字段"，永远不知道这次查询是为了登录还是为了扣费。

> **前端类比**：这正是你早就在用的分层。React/Vue 里你不会把 `fetch` 写在按钮的 onClick 里，也不会把表单校验逻辑塞进 `axios` 封装里。三层架构就是把这套"关注点分离"写成了团队铁律。

### 一一对应到前端

| 后端层 | 职责 | 前端对应 | 共同点 |
| --- | --- | --- | --- |
| `Controller` | 接收/返回 HTTP | `pages/` 页面组件、Next.js `route.ts` | 都是"边界层"，只做协议适配 |
| `Service` | 业务逻辑、编排 | `composables/` / 自定义 hook / store action | 业务规则的唯一归宿 |
| `Repository`/`Mapper` | 数据库读写 | `api/*.ts`（封装 axios 调用）| 只关心"怎么拿数据"，不关心"为什么" |
| `entity`/`dto`/`vo` | 数据结构 | `types/*.ts`、`zod` schema | 定义数据形状 |

举个 svc-user 里"登录"的例子，对照看就懂了：

```java
// Controller —— 只做协议适配，一行业务都没有
@PostMapping("/login")
public RtData<LoginVO> login(@RequestBody @Valid LoginDto dto) {
    return RtData.ok(loginService.login(dto));   // 校验入参 + 调 Service + 包响应
}

// Service —— 业务规则全在这里
public LoginVO login(LoginDto dto) {
    UserMst user = userRepository.findByPhone(dto.getPhone());  // 让 Repo 去查
    if (user == null) throw new BizException(ErrorCode.USER_NOT_FOUND);
    if (!passwordEncoder.matches(dto.getPwd(), user.getPwdHash()))  // 这是业务规则
        throw new BizException(ErrorCode.PASSWORD_WRONG);
    String token = jwtUtil.sign(user.getId());                  // 编排：签发 token
    return new LoginVO(token, user.getNickname());
}

// Repository —— 只负责查，不懂"为什么查"
public UserMst findByPhone(String phone) {
    return mongoTemplate.findOne(Query.query(Criteria.where("phone").is(phone)), UserMst.class);
}
```

**为什么要这么死板？** 因为分层之后：改业务规则只动 Service，换数据库只动 Repository，改接口协议只动 Controller，三者互不传染。这跟前端"换了 UI 库不该动业务 hook"是同一个道理。

---

## 4.2 整体项目结构

我们的运行示例——AI 生图微服务——是一个典型的 Spring Cloud 多模块项目。顶层结构长这样：

```text
ai-image-platform/
├── pom.xml                     ← Maven 父工程配置（类似 monorepo 根 package.json）
│                                  统一管理所有子模块的依赖版本
│
├── component/                  ← ★ 共享底层组件（优先阅读，相当于 packages/）
│   ├── cpt-api                 ← 服务间调用客户端（Feign）+ 共享 DTO
│   ├── cpt-common              ← 通用工具类、统一响应 RtData、异常码
│   ├── cpt-mongodb             ← MongoDB 连接与操作封装
│   ├── cpt-mysql               ← MySQL + ORM 封装（统计用）
│   ├── cpt-redis               ← Redis 缓存/限流/分布式锁封装
│   ├── cpt-rocketmq            ← 消息队列封装
│   └── cpt-xxljob              ← 定时任务框架
│
├── svc-gateway/                ← ★ API 网关（第一个要看的服务，所有请求的入口）
├── svc-auth/                   ← 认证服务（登录态、token 校验）
├── svc-user/                   ← ★ 用户/配额/支付（最适合入门的业务服务）
├── svc-ai/                     ← AI 生图服务（调用模型）
├── svc-canvas/                 ← 画布/任务编排（最复杂的服务）
├── svc-oss/                    ← 文件存储服务（对接云 OSS）
│
├── docs/                       ← 项目文档
└── scripts/                    ← 部署/运维脚本
```

> **前端类比**：`component/` 就是你 monorepo 里的 `packages/`，`svc-*` 就是 `apps/`。`pom.xml` 父工程相当于根目录的 `package.json` + `pnpm-workspace.yaml`，统一锁版本。

clone 下来后，先别急着用 IDE 打开（大项目索引一次要几分钟），在终端里一行命令就能看清骨架。`tree` 在 macOS 上用 `brew install tree`、Ubuntu 上用 `apt install tree` 装：

```bash
# 只看前两层目录，忽略 target/.git，快速建立全局地图
tree -L 2 -d -I 'target|.git|node_modules' ai-image-platform
```

预期输出（截断后大致如此，和上面的结构图对得上）：

```text
ai-image-platform
├── component
│   ├── cpt-api
│   ├── cpt-common
│   ├── cpt-mongodb
│   ├── cpt-mysql
│   ├── cpt-redis
│   ├── cpt-rocketmq
│   └── cpt-xxljob
├── svc-gateway
├── svc-auth
├── svc-user
├── svc-ai
├── svc-canvas
├── svc-oss
├── docs
└── scripts
```

没装 `tree` 也行，用一句 `find` 同样能列出顶层模块（Windows 下可在 Git Bash 里跑）：

```bash
# 列出根目录下所有子模块（含 pom.xml 的目录就是一个 Maven 模块）
find ai-image-platform -maxdepth 2 -name pom.xml
```

> **前端类比**：这一步等于你拿到陌生前端仓库时先 `cat package.json` 看 `workspaces`、再 `ls packages apps`，先认清"有哪些包"，再决定从哪个 `app` 钻进去。

**阅读建议**：先看 `component/cpt-api`（搞清楚服务之间怎么互相调用），再看 `svc-gateway`（搞清楚请求从哪进来），然后挑 `svc-user`（最简单的业务服务）通读一遍。`svc-canvas` 最后看——它涉及任务编排、状态机、MQ，是整个系统最绕的部分。

---

## 4.3 单个服务的内部结构

好消息：**每个微服务的内部结构都是同一个模板**。看懂一个，其余的目录布局你闭着眼都能猜到。以 svc-user 为例：

```text
svc-user/
├── pom.xml                                ← 本服务的依赖声明（类似 package.json）
├── Dockerfile                             ← 容器打包文件
│
└── src/main/
    ├── java/com/example/user/
    │   │
    │   ├── UserApplication.java           ← ★ 服务入口
    │   │                                     相当于 main.ts / index.js
    │   │                                     一个 main() 方法启动整个服务
    │   │
    │   ├── controller/                    ← ★ 接口层（最先看的）
    │   │   ├── UserController.java        ← 用户登录/注册接口
    │   │   ├── QuotaController.java       ← 配额查询/扣减接口
    │   │   └── OrderController.java       ← 支付订单接口
    │   │
    │   ├── service/                       ← ★ 业务逻辑层
    │   │   ├── LoginRegisterService.java  ← 接口定义（声明"做什么"）
    │   │   └── impl/
    │   │       └── LoginRegisterServiceImpl.java  ← 具体实现（"怎么做"）
    │   │
    │   ├── repository/                    ← ★ 数据访问层
    │   │   ├── UserMstRepository.java     ← 标准 CRUD
    │   │   └── impl/
    │   │       └── UserProfileCustomRepositoryImpl.java  ← 复杂自定义查询
    │   │
    │   ├── entity/                        ← 数据模型（对应 MongoDB 文档 / MySQL 表）
    │   │   ├── UserMst.java               ← 用户主表
    │   │   └── UserProfile.java           ← 用户资料
    │   │
    │   ├── dto/                           ← 请求参数对象（入参）
    │   │   └── LoginDto.java              ← 登录请求参数
    │   │
    │   ├── vo/                            ← 响应数据对象（出参）
    │   │   └── LoginVO.java               ← 登录响应数据
    │   │
    │   ├── config/                        ← 框架配置类
    │   └── utils/                         ← 工具方法
    │
    └── resources/
        └── application.yml                ← 配置文件（类似 .env，但能力更强）
```

注意 `service/` 和 `repository/` 下都有一个 `impl/` 子目录。Spring 项目习惯**接口与实现分离**：`LoginRegisterService.java` 是接口（只有方法签名），`LoginRegisterServiceImpl.java` 才是真正的代码。这跟 TypeScript 里 `interface` 和实现它的 class 分开是同一回事——好处是依赖注入时面向接口编程、方便替换实现和写单测。后面 [Spring 的 IoC/DI](/back-end/frontend-backend-guide/06-spring-boot-ioc-di) 会讲为什么非要这么分。

### 三层架构对照图（前端 vs 后端）

把上一节的铁律落到目录上，就是这张图：

```text
┌────────────────────────────────────────────────────────────┐
│  前端（Vue/React 项目）            后端（svc-user）          │
│                                                            │
│  pages/Login.vue        ──────────▶  controller/           │
│  （页面，接收用户操作）               （接口，接收 HTTP 请求） │
│                                                            │
│  composables/useAuth.ts ──────────▶  service/impl/         │
│  （业务逻辑 hook）                    （业务逻辑实现）         │
│                                                            │
│  api/auth.ts            ──────────▶  repository/           │
│  （封装 axios 调接口）                （操作数据库）           │
│                                                            │
│  types/auth.ts          ──────────▶  entity/ dto/ vo/      │
│  zod schema                          （Java 数据模型）       │
└────────────────────────────────────────────────────────────┘
```

数据在层与层之间流动时，会换不同的"外壳"：HTTP 进来是 `dto`（入参），在 `service`/`repository` 里操作的是 `entity`（数据库模型），返回给前端时换成 `vo`（出参）。这三者经常字段相似但绝不复用同一个类——因为入参要带密码、数据库模型要带敏感字段，而响应里这些都不能露出去。这跟前端用 `zod` 给"请求体"和"响应体"分别定义 schema 是一个动机。

---

## 4.4 核心目录 vs 可以暂时跳过的

第一次读项目，没必要每个文件夹都钻进去。按下面这两张表分配你的注意力。

### 核心代码（必看 ⭐）

| 目录 | 作用 | 怎么读 | 优先级 |
| --- | --- | --- | --- |
| `controller/` | 接口入口 | 看有哪些 URL、入参出参 | ⭐⭐⭐ |
| `service/impl/` | 核心业务逻辑 | 看完 controller 后顺着调用跟进来 | ⭐⭐⭐ |
| `repository/` | 数据库操作 | 看方法名理解查询逻辑 | ⭐⭐ |
| `entity/` | 数据模型 | 了解数据有哪些字段 | ⭐⭐ |
| `dto/` / `vo/` | 入参和出参结构 | 接口文档的代码版 | ⭐⭐ |
| `mq/` | 消息队列消费者 | 理解异步流程（如生图任务回调）| ⭐⭐ |
| `filter/` | 过滤器（svc-gateway 特有）| 理解鉴权、限流拦截逻辑 | ⭐⭐ |

### 可以暂时跳过

| 目录 | 为什么可以跳过 |
| --- | --- |
| `config/` | 框架级配置（连接池、序列化等），看不懂不影响理解业务 |
| `utils/` | 工具方法，用到时按需查 |
| `aspect/` | AOP 切面（如统一日志、权限注解），属于进阶功能 |
| `handler/` | 全局异常处理器等，第一遍可略过 |
| `Dockerfile` / `entrypoint.sh` | 部署层面，与业务代码无关，到 [Docker 实战](/back-end/frontend-backend-guide/23-docker-in-practice) 再看 |

> **前端类比**：这就像你接手一个新的 Vue 项目，会先看 `pages/` 和 `composables/`，而 `vite.config.ts`、`eslintrc`、`utils/` 通常等真的要改它们时再翻。

---

## 4.5 组件模块导读（component/）

这些 `cpt-*` 模块类似你 monorepo 里的 `packages/`，被所有业务服务通过 `pom.xml` 依赖进来。读业务代码时你会不断撞见它们，这张表帮你按需查阅：

| 模块 | 作用 | 前端类比 | 什么时候看 |
| --- | --- | --- | --- |
| `cpt-api` | 服务间调用客户端（Feign）+ 共享 DTO | `@org/api-client` | ⭐ 最先看，理解服务怎么互相调用 |
| `cpt-common` | 工具类、统一响应 `RtData`、异常码 `ErrorCode` | `@org/utils` | 看到 `RtData`/`BizException` 时来查 |
| `cpt-mongodb` | MongoDB 连接配置、基础 Repository | `@org/db-client` | 看 Repository 时来查 |
| `cpt-mysql` | MySQL + MyBatis-Plus 配置 | `@org/sql-client` | 看统计模块时再看 |
| `cpt-redis` | Redis 连接、缓存/限流/分布式锁工具 | `@org/cache` | 看限流/缓存时来查 |
| `cpt-rocketmq` | 消息队列封装和注解 | `@org/event-bus` | 看异步任务（生图、通知）时来查 |
| `cpt-xxljob` | 定时任务框架 | `@org/cron` | 暂时不用看 |

其中 `cpt-common` 里的 `RtData` 是贯穿整个系统的统一响应类型，你在每个 Controller 的返回值里都会看到它（`RtData.ok(data)` / `RtData.fail(msg)`）。它相当于前端团队约定的"所有接口都返回 `{ code, msg, data }`"那层封装。下一部分动手写代码时会大量用到。

---

## 4.6 读源码的推荐路径

拿到任何一个陌生的 Spring 服务，按这个顺序读，基本不会迷路。我们用 svc-user 的"扣配额"接口走一遍：

```text
第一步：看 Controller
  → QuotaController 里找到 @PostMapping("/quota/deduct")
  → 知道有这么个接口、URL 是什么、HTTP 方法是什么

第二步：看方法的入参（DTO）和出参（VO）
  → DeductQuotaDto 里有 userId、amount
  → 知道调用方要传什么、能拿回什么

第三步：跟进 Service
  → 点进 quotaService.deduct(dto)，看核心业务规则
  → "余额不足要拦截""扣减要加分布式锁防并发"都在这

第四步：看 Service 调了哪些 Repository / 外部组件
  → 调 quotaRepository 改库、调 cpt-redis 加锁
  → 了解数据怎么存、怎么查、怎么保证一致性

第五步：看 Entity
  → UserQuota 实体，了解数据库里这条配额记录长什么样
```

把它和你读前端代码的习惯对齐，就是同一套肌肉记忆：

```text
后端：Controller → DTO/VO → Service → Repository → Entity
前端：页面组件   → props/类型 → hook  → api 封装   → 接口返回类型
```

**一个实用技巧**：在 IDE（IntelliJ IDEA）里，把光标放在 `quotaService.deduct(dto)` 上按 `Ctrl/Cmd + 左键` 就能跳到实现，跟你在 VS Code 里 `F12` 跳转定义一模一样。顺着这条"调用链"往下钻，比对着目录树瞎翻高效得多。

---

## 小结

- **三层架构是后端的铁律**：Controller 只做协议适配、不写业务；Service 是业务逻辑的唯一归宿、不碰数据库细节；Repository/Mapper 只读写数据库、不懂业务。它和前端的 页面/composable/api 分层一一对应，核心都是**关注点分离**。
- **微服务项目结构高度模板化**：`component/`（共享组件，类比 `packages/`）+ 多个 `svc-*`（业务服务，类比 `apps/`）；每个服务内部都是 controller/service/repository/entity/dto/vo 那一套。
- **数据在层间换壳**：入参用 `dto`、库内用 `entity`、出参用 `vo`，不复用同一个类，目的是隔离敏感字段——和前端给请求/响应分别写 `zod` schema 同理。
- **读源码顺着调用链走**：Controller → DTO → Service → Repository → Entity，等于前端的 页面 → hook → api → 类型。第一遍只看核心目录，`config/`、`utils/`、`aspect/` 等先跳过。
- **`RtData` 是统一响应外壳**，每个 Controller 返回值都会用到，相当于前端约定的 `{ code, msg, data }` 封装。

### 自测

1. 在 svc-user 里，"用户余额不足时禁止扣配额"这条规则应该写在 Controller、Service 还是 Repository？为什么？
2. 同样一个用户，登录接口的入参类、数据库模型类、返回给前端的类为什么要分成 `LoginDto` / `UserMst` / `LoginVO` 三个，而不是复用一个？
3. 拿到一个完全陌生的 `svc-canvas` 服务，你会从哪个目录、哪个文件开始读？说出你的前三步。

### 下一章

地图建立完毕，从下一章开始我们正式进入 Part 2 写代码——先补一节 [Java 速成（写给前端）](/back-end/frontend-backend-guide/05-java-crash-course)，把你已经会的 TypeScript 知识平移成 Java。

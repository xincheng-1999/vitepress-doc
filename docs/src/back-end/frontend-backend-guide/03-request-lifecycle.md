# 一个请求的完整链路

> 理解请求链路是学后端最关键的一步。
> 就像你在前端 DevTools 的 Network 面板里看一个请求从发起到拿到响应一样，后端的每个请求也有一条清晰的生命线——只不过这条线藏在服务器里，你看不到，得靠脑子里有一张图。
>
> 这一章的目标：让你在脑子里建立这张图。看到任何一个接口慢了、报错了、超时了，你都能顺着这条线一层层往下问"卡在哪一层"。

这是承上启下的一章。上一章 [架构总览](/back-end/frontend-backend-guide/02-architecture-overview) 讲了我们这套 AI 生图微服务有哪些服务（svc-gateway / svc-auth / svc-user / svc-ai / svc-canvas / svc-oss）和哪些基础设施；这一章把视角缩小到"一个请求进来，到底经过了什么"。

---

## 单体应用的请求链路（先看这个）

在学微服务之前，先搞懂一个 Spring Boot 应用内部的请求是怎么流转的。这就像你在前端先理解了"组件 → hooks → API 调用"之后，才去学 Monorepo 多项目架构一样。

以 svc-user 里的 `POST /api/user/login` 为例（先不看网关，只看单个服务内部）：

```text
┌─────────────────────────────────────────────────────────────┐
│                  客户端（浏览器 / Postman / 网关）            │
│                                                             │
│   发送: POST /api/user/login                                │
│   请求体: JSON → 对应后端的 LoginRequest DTO                │
└───────────────────────────┬─────────────────────────────────┘
                            │  HTTP 请求
                            ▼
┌─────────────────────────────────────────────────────────────┐
│               0. Filter / Interceptor 层                    │
│                                                             │
│  → 鉴权（检查 token 是否有效）                               │
│  → CORS 跨域处理                                            │
│  → 请求日志记录（记下 traceId、耗时）                        │
│  💡 前端类比：axios 的请求拦截器 interceptors.request        │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   1. DispatcherServlet                       │
│              Spring MVC 中央控制器（统一入口）                │
│                                                             │
│  → 解析请求地址 + 请求方法                                   │
│  → 找到匹配的 Controller 方法                                │
│  💡 前端类比：Vue Router / React Router 的路由匹配           │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                     2. Controller 层                        │
│               @RestController / @RequestMapping              │
│                                                             │
│  1. 接收前端 JSON → 自动封装成 Request DTO 对象              │
│     （@RequestBody LoginRequest dto）                        │
│  2. 参数校验生效：@NotBlank / @Positive 等                   │
│  3. 调用 service.login(dto)                                 │
│  4. 接收 service 返回结果 → 包装成 RtData                    │
│  5. 返回 JSON 给前端                                        │
│  💡 前端类比：页面组件，只负责接参数 + 调方法 + 渲染结果      │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                      3. Service 层                          │
│                @Service / @Transactional                     │
│                                                             │
│  → 真正的业务逻辑中心：                                      │
│     - 权限判断、状态流转、规则计算                            │
│     - 组合多个 mapper / 多次查询的结果                       │
│     - DTO → Entity（写库前转换）                            │
│     - Entity → DTO（返回 controller 前转换）                 │
│  → @Transactional 保证操作要么全成功、要么全回滚             │
│  💡 前端类比：composables / hooks，存放核心逻辑              │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                  4. Mapper / Repository 层                  │
│                @Mapper / @Repository                         │
│                                                             │
│  → 只干一件事：和数据库对话                                  │
│     - 增删改查（select / insert / update / delete）         │
│     - 出入都是 Entity（数据库实体类）                        │
│  💡 前端类比：封装好的 API 函数（api/user.ts）               │
└───────────────────────────┬─────────────────────────────────┘
                            │  （走连接池借连接，见后文）
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                       5. 数据库                             │
│                 MongoDB（主库） / MySQL（统计）              │
│                                                             │
│  文档 / 表结构 ↔ Entity 类 对应                              │
└───────────────────────────┬─────────────────────────────────┘
                            │
               ══════ 返回方向（原路返回）══════
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│              全局异常处理（穿插在整条链路中）                 │
│                    @RestControllerAdvice                     │
│                                                             │
│  → 任何一层抛出异常，都会被统一捕获                          │
│  → 转成统一响应 RtData.fail(code, message)                  │
│  💡 前端类比：axios 的响应拦截器 interceptors.response       │
│     统一处理 401 / 403 / 500 等错误                          │
└─────────────────────────────────────────────────────────────┘
```

### 各层职责一句话总结

| 层 | 职责 | 前端类比 |
| --- | --- | --- |
| Filter / Interceptor | 鉴权、日志、跨域 | axios 请求拦截器 |
| DispatcherServlet | 路由分发 | Vue Router / React Router |
| Controller | 接参数、调 Service、返结果 | 页面组件 |
| Service | 业务逻辑 + 事务 | composables / hooks |
| Mapper / Repository | 数据库操作 | api/*.ts |
| @RestControllerAdvice | 统一异常处理 | axios 响应拦截器 |

> 记住这个口诀：**Controller 不写逻辑，Service 不碰数据库，Mapper 不管业务**——各管各的，和前端的"关注点分离"一模一样。这套分层我们在 [三层架构与工程结构](/back-end/frontend-backend-guide/04-three-layer-and-structure) 里会拆得更细，每层放什么、不放什么都讲清楚。

Filter 和 Interceptor 的区别、`@RestControllerAdvice` 怎么写，会在 [常用注解速查](/back-end/frontend-backend-guide/08-annotations-cheatsheet) 和 [手写一个 CRUD API](/back-end/frontend-backend-guide/07-build-a-crud-api) 里展开。这里你只要先记住这条线的形状。

理解了这个单体链路之后，下面的微服务示例只是在此基础上加了**网关、服务间调用、消息队列**——核心分层不变。

---

## 线程模型：一个请求 = 一个线程

这是前端工程师最容易忽略、却最重要的一节。它解释了"为什么后端工程师天天念叨并发和线程池"。

### 前端的心智模型：单线程 + 事件循环

你在 Node 里写后端时，所有请求其实是**同一个线程**轮流处理的。一个请求里 `await fetch(...)` 时，线程不会傻等，而是去处理下一个请求，等 IO 回来再回来接着跑。所以 Node 里你几乎从不需要关心"两个请求同时改一个变量"。

### Java / Tomcat 的心智模型：thread-per-request

Spring Boot 内嵌的 Tomcat 走的是**完全不同**的模型——**一个请求独占一个线程，从进来到返回**。

```text
            Tomcat 线程池（默认上限 200 个线程）
       ┌──────────────────────────────────────────────┐
请求 A →│ thread-1  ███████████████ 处理 A 直到返回        │
请求 B →│ thread-2  ███████ 处理 B 直到返回                │
请求 C →│ thread-3  ███████████ 处理 C 直到返回            │
请求 D →│ thread-4  ... 空闲，等下一个请求                 │
  ...   │ ...                                           │
请求 X →│ (200 个线程全占满了 → 新请求进等待队列排队)       │
       └──────────────────────────────────────────────┘
```

关键事实：

- 一个 HTTP 请求进来 → Tomcat 从线程池**借一个线程** → 这个线程一路跑 Filter → Controller → Service → Mapper → 等数据库返回 → 一路返回 → **请求结束才把线程还回池子**。
- 整个过程中，**这个线程是被这一个请求独占的**。哪怕它在等数据库（IO 阻塞），线程也照样占着不放，只是干等。
- 线程池默认上限 200（`server.tomcat.threads.max`）。200 个全占满了，第 201 个请求就进**等待队列**排队；队列也满了就直接被拒绝。

> 前端类比：想象浏览器对同源**最多 6 个并发连接**那种限制——超过 6 个请求就得排队。Tomcat 就是把这个数字放大到 200，而且每个"连接"对应服务器上一个真实的、占内存的线程。

### 这件事的三个直接后果

**后果一：慢接口会"吃掉"线程，拖垮整个服务。**

假设 svc-user 的某个接口调了一个慢的下游（比如外部短信服务卡了 5 秒）。每个调它的请求都占着一个 Tomcat 线程干等 5 秒。如果 QPS 是 50，那 5 秒内就有 250 个请求堆进来——200 个线程全被这个慢接口占满，结果是**整个 svc-user 的所有接口都卡住了**，连查个用户信息都转圈。这就是为什么一个慢点能拖垮一片。

**后果二：CPU 密集任务别放在请求线程里硬算。**

如果在 Controller 线程里跑一个要算 3 秒的循环，这 3 秒线程完全没法服务别人。重活要么拆，要么扔到专门的线程池 / 消息队列异步做（这正是后面生图走 MQ 的原因）。

**后果三：线程不是免费的。**

每个线程都要占栈内存（默认约 1MB）、要被操作系统调度。200 个线程切来切去本身就有开销。所以"无脑把线程池调到 5000"不解决问题，反而把内存和调度压垮——这也是 [JVM 内存模型](/back-end/frontend-backend-guide/18-jvm-memory-model) 里会讲的。

### 怎么看当前有多少线程在干活

目标：确认服务是不是线程被打满了。

```bash
# 进到容器/服务器，找到 Java 进程 PID（这里假设是 svc-user）
jps -l
# 输出样例：
# 12 com.example.svcuser.SvcUserApplication
# 88 sun.tools.jps.Jps

# 统计这个进程里名字带 http-nio（Tomcat 工作线程）的线程数量
jstack 12 | grep -c 'http-nio'
```

预期输出（健康时，远小于上限 200）：

```text
17
```

怎么读这段输出：`http-nio-8080-exec-*` 就是 Tomcat 的请求处理线程。如果这个数字常年贴着 200、且大量线程的栈停在同一个方法（比如都卡在某个 Feign 调用或数据库查询上），那基本可以确定**线程被慢操作占满了**，要去查那个慢的下游，而不是盲目加线程。`jstack` 怎么读后面 [诊断工具箱](/back-end/frontend-backend-guide/29-diagnostic-toolbox) 会细讲。

结论：**因为后端是 thread-per-request，所以后端必须关心并发**——这是前后端思维方式最大的分水岭。线程池怎么配、为什么不能无限加，见 [从单线程到多线程](/back-end/frontend-backend-guide/14-single-thread-to-multithread) 和 [线程池与 Executor](/back-end/frontend-backend-guide/15-thread-pools-executor)。

---

## 链路中的连接池

上一节说"线程占着干等数据库"。那它**怎么连数据库**？答案是：不每次新建连接，而是从**连接池**里借。这是链路里另一个会"卡住请求"的关键位置。

### 为什么需要连接池

建立一个数据库连接（TCP 握手 + 认证 + 协议初始化）很贵，可能要几十毫秒。如果每个请求都新建一次连接、用完就关，开销大到离谱。所以应用启动时就预先建好一批连接放进池子，用的时候借、用完还。

> 前端类比：就像浏览器对同一个域名复用 TCP 连接（HTTP keep-alive），而不是每个请求都重新握手。连接池就是后端版的"连接复用 + 数量上限"。

链路里其实有**两类**连接池：

```text
       svc-canvas 一个请求线程的视角
┌────────────────────────────────────────────────────────────┐
│  Controller → Service                                       │
│                  │                                          │
│      ┌───────────┴─────────────────────┐                    │
│      ▼                                  ▼                    │
│  ┌────────────────┐              ┌────────────────────┐     │
│  │ DB 连接池       │              │ HTTP 连接池         │     │
│  │ (HikariCP)     │              │ (Feign → 下游服务)  │     │
│  │ 借一条连接      │              │ 借一条连接          │     │
│  └───────┬────────┘              └─────────┬──────────┘     │
│          ▼                                 ▼                │
│      MongoDB / MySQL                  svc-user / svc-oss     │
└────────────────────────────────────────────────────────────┘
   连接池满了 → 请求线程在"借连接"这一步阻塞排队 → 拿不到就超时
```

- **DB 连接池**（HikariCP，Spring Boot 默认）：Service 调 Mapper 查库时，要先从池里借一条 DB 连接。
- **HTTP / Feign 连接池**：一个服务通过 Feign 调另一个服务（比如 svc-canvas 调 svc-user 扣配额），底层也走 HTTP 连接池。

### 池满会发生什么

这是排查超时类问题的高频根因，务必记牢这条因果链：

```text
池子大小有限（比如 HikariCP 默认 10 条连接）
        │
        ▼
某个慢查询 / 慢下游长时间占着连接不还
        │
        ▼
连接被借光 → 后来的请求线程卡在"等连接"这一步
        │
        ▼
等超过 connectionTimeout（默认 30 秒）→ 抛异常，请求失败
```

注意这里出现了**双重排队**：Tomcat 那 200 个线程可能没满，但它们全卡在"等数据库连接"上——线程在，连接没了。所以排查慢请求时，光看线程数不够，还要看连接池水位。

### 实战：连接池被打满的日志长什么样

症状：svc-canvas 提交生图任务的接口偶发超时，前端报 504。

日志样例（HikariCP 拿不到连接时会打这种）：

```text
2026-06-01 14:23:07.881 ERROR 12 --- [io-8080-exec-44] c.e.canvas.service.ImageTaskServiceImpl  : 创建生图任务失败 taskId=null
java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available, request timed out after 30000ms
    at com.zaxxer.hikari.pool.HikariPool.createTimeoutException(HikariPool.java:696)
    at com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:197)
    ...
```

同一时间，HikariCP 的健康日志（开了 DEBUG 或 metrics 时）：

```text
2026-06-01 14:23:05.102 DEBUG 12 --- [l-1 housekeeper] com.zaxxer.hikari.pool.HikariPool        : HikariPool-1 - Pool stats (total=10, active=10, idle=0, waiting=8)
```

怎么读这段输出：

- `total=10, active=10, idle=0`：池子一共 10 条连接，10 条全在用，0 条空闲——**池子被借光了**。
- `waiting=8`：还有 8 个线程在排队等连接。
- 上面那条 `Connection is not available, request timed out after 30000ms`：某个线程等了 30 秒还没借到，直接超时报错。

结论：不是数据库挂了，是**连接池配置 / 慢 SQL 导致连接不够用**。两个方向修：(1) 找出占着连接不放的慢 SQL（加索引、优化查询，见 [SQL 与索引](/back-end/frontend-backend-guide/10-sql-and-indexes)）；(2) 评估后再合理调大池大小。**注意：连接池绝不是越大越好**——它和线程池一样有上限逻辑，调大了数据库自己也扛不住。连接池的参数怎么配、HikariCP 各项含义，见 [连接池](/back-end/frontend-backend-guide/13-connection-pools)。

---

## 以"用户登录"为例（同步流程）

回到微服务全景。我们跟踪一个 `POST /api/user/login` 请求从发出到返回的每一步：

```text
① 客户端发送 POST /api/user/login
        │
        ▼
② ┌───── svc-gateway（网关）─────┐
   │  AuthFilter 拦截              │
   │  → 检查路径白名单             │  ← 登录接口在白名单中，不需要 token
   │  → 生成 traceId               │  ← 类似前端给每个请求加 X-Request-Id
   │  → 附加到 headers             │
   │  → 路由到 svc-user            │
   └───────────────────────────────┘
        │
        ▼
③ ┌───── svc-user Controller ─────┐
   │  UserController.login()       │
   │  → @Validated 校验参数        │  ← 类似前端 zod / yup 校验
   │  → 根据登录方式分发           │
   └───────────────────────────────┘
        │
        ▼
④ ┌───── svc-user Service ─────────────────┐
   │  LoginRegisterServiceImpl.login()      │
   │  → 验证短信验证码（查 Redis）           │
   │  → 查询用户是否存在（查 MongoDB）       │
   │  → 若不存在 → 自动注册并创建用户        │
   │  → Feign 调 svc-auth 获取 token        │
   └────────────────────────────────────────┘
        │  ← 这里发生了一次 服务间 HTTP 调用（走 Feign 连接池）
        ▼
⑤ ┌───── svc-auth Controller ─────┐
   │  OauthController.token()      │
   │  → 验证客户端身份             │
   │  → 生成 access_token          │
   │  → 生成 refresh_token         │
   │  → 存储到 Redis               │
   │  → 返回 token 给 svc-user     │
   └───────────────────────────────┘
        │
        ▼
⑥ svc-user 组装最终响应
   → 包含 token + 用户基本信息
   → 统一包装为 RtData.ok(loginVO)
   → 返回给客户端
```

### 几个关键知识点

- **白名单**：网关维护一份"不需要登录就能访问"的路径列表，登录接口本身就在里面（不然你登录前哪来的 token）。
- **traceId**：贯穿整条链路的唯一标识，方便在日志里查完整链路。和前端给每个请求加 `X-Request-Id`、然后在 Network 面板里靠它对应日志是一个意思。怎么靠 traceId 串起多个服务的日志，见 [可观测性](/back-end/frontend-backend-guide/30-observability)。
- **Feign 调用**：svc-user 调 svc-auth 获取 token，对写代码的人来说就像前端用 `axios` 调另一个接口一样——但别忘了上一节说的，它走的是 HTTP 连接池，调用方那个 Tomcat 线程会一直占着等 svc-auth 返回。
- **统一响应格式**：后端统一返回 `RtData` 结构，形如 `{ "code": 0, "data": {...}, "msg": "success" }`。成功用 `RtData.ok(data)`，失败用 `RtData.fail(msg)`。

下面是 ④ 那一步 Service 的简化代码，感受一下"一个线程一路跑下来"的样子：

```java
@Service
public class LoginRegisterServiceImpl implements LoginRegisterService {

    @Resource
    private UserMapper userMapper;       // 查库走 DB 连接池
    @Resource
    private AuthFeignClient authClient;  // Feign 走 HTTP 连接池（来自 cpt-api）
    @Resource
    private SmsService smsService;

    @Override
    public RtData<LoginVO> login(LoginRequest req) {
        // 1. 校验短信验证码（查 Redis）—— 当前线程同步等待
        if (!smsService.verify(req.getPhone(), req.getCode())) {
            return RtData.fail("验证码错误或已过期");
        }
        // 2. 查用户，没有就注册（查 MongoDB）—— 当前线程同步等待
        UserEntity user = userMapper.findByPhone(req.getPhone());
        if (user == null) {
            user = userMapper.createUser(req.getPhone());
        }
        // 3. Feign 调 svc-auth 拿 token —— 当前线程在这里阻塞等下游返回
        TokenVO token = authClient.issueToken(user.getUid());
        // 4. 组装并返回，整个方法跑完，线程才还给 Tomcat
        return RtData.ok(new LoginVO(token, user));
    }
}
```

第 1、2、3 步**每一步都是同步阻塞**的：当前这个 Tomcat 线程会一直占着，等 Redis、等 MongoDB、等 svc-auth 一个个返回，全部跑完才把结果返回、把线程还回去。这就把前两节（线程模型 + 连接池）和真实代码对上了。Java 基础语法、`@Resource` 注入这些，见 [Java 速成](/back-end/frontend-backend-guide/05-java-crash-course) 和 [Spring Boot 的 IoC 与 DI](/back-end/frontend-backend-guide/06-spring-boot-ioc-di)。

---

## 以"图片生成"为例（异步 MQ 流程）

图片生成是耗时操作（可能要几秒到几十秒）。如果还按上面同步那套来，那个 Tomcat 线程就得占着等几十秒——结合"线程模型"那一节你已经知道，这会很快把线程池打满。所以生图用**异步方式**：先返回任务 ID，后台慢慢处理。

```text
① 客户端: POST /api/canvas/v1/image/create
        │
        ▼
② ┌───── svc-gateway（网关）──────┐
   │  AuthFilter                   │
   │  → 验证 token                 │  ← 这次需要登录了
   │  → 提取 uid                   │  ← 从 token 中解析出用户 ID
   │  → 附加到 headers             │  ← 后续服务从 header 中读取 uid
   │  → 路由到 svc-canvas          │
   └───────────────────────────────┘
        │
        ▼
③ ┌───── svc-canvas Controller ───┐
   │  ImageController              │
   │  → 从 header 取出 uid         │
   │  → 委托给 ImageTaskService    │
   └───────────────────────────────┘
        │
        ▼
④ ┌───── svc-canvas Service ──────────────────────┐
   │  ImageTaskServiceImpl.createImageTask()       │
   │  → 用策略模式选择生成策略                      │  ← 设计模式，后续章节详解
   │  → Feign 调 svc-user 检查并扣除配额            │  ← 又一次服务间调用
   │  → 创建任务记录 → 保存到 MongoDB               │
   │  → 把任务消息发送到 RocketMQ                   │  ← 关键：发消息而不是直接调用
   │  → 立即返回任务 ID 给客户端                    │  ← 不等图片生成完成！
   └────────────────────────────────────────────────┘
        │
        ▼ (到这里接口已经返回了，Tomcat 线程已归还。后面全是异步)
        │
        ▼
⑤ ┌───── svc-ai（消费者）───────────────────────┐
   │  ImageGenerateTaskPullConsumer              │
   │  → 从 RocketMQ 拉取任务消息                 │
   │  → 按 AI 模型类型分配到不同线程池            │  ← 注意：这是另一套线程池，不是 Tomcat 的
   │  → 调用外部 AI API 生成图片                  │  ← 这一步最耗时
   │  → 生成完成后把结果消息发回 RocketMQ          │
   └─────────────────────────────────────────────┘
        │
        ▼ (又是异步，通过消息队列)
        │
⑥ ┌───── svc-canvas（结果消费者）────────────────┐
   │  ImageResultConsumer                        │
   │  → 接收图片生成结果                          │
   │  → Feign 调 svc-oss 上传图片到云 OSS          │
   │  → 更新 MongoDB 中的任务状态为"已完成"        │
   └─────────────────────────────────────────────┘
        │
        ▼
⑦ 客户端轮询 GET /api/canvas/v1/image/status/{taskId}
   → 不断查询任务状态
   → 直到状态变为"完成"，拿到图片 URL
```

注意第 ⑤ 步的消费者用的是**自己的线程池**，而不是 Tomcat 的请求线程池。这是异步化的核心价值：把耗时的活从"请求线程"转移到"后台线程 / 消费者"，让请求线程能尽快归还、继续服务别人。RocketMQ 的可靠投递、消息丢了 / 重复了怎么办，见 [消息队列可靠性](/back-end/frontend-backend-guide/33-mq-reliability)。

### 同步 vs 异步的核心区别

| 特点 | 同步流程（登录） | 异步流程（图片生成） |
| --- | --- | --- |
| 接口返回时机 | 所有逻辑执行完才返回 | 提交任务后立即返回任务 ID |
| 客户端获取结果 | 直接在响应体中 | 需要轮询 / WebSocket 通知 |
| 请求线程占用时长 | 整个业务时长（可能拖垮线程池） | 极短（发完消息就返回） |
| 适用场景 | 快速操作（< 1~2 秒） | 耗时操作（几秒 ~ 几分钟） |
| 通信方式 | HTTP（Feign） | HTTP + 消息队列（RocketMQ） |

> 前端类比：
> - 同步 = `const result = await axios.post('/login')`，await 完马上就有结果。
> - 异步 = 你上传一个视频到 B 站，它先告诉你"已提交转码"（任务 ID），然后你不断刷新页面看进度（轮询）。后台真正转码的不是处理你这次上传请求的那个线程。

为什么异步、什么场景该异步、`@Async` 和 MQ 的区别，会在 [异步编程](/back-end/frontend-backend-guide/17-async-programming) 里系统讲。

---

## Header 在服务间透传

前端工程师特别需要注意：在后端微服务中，**用户身份等上下文信息是通过 HTTP Headers 在服务间传递的**，不是靠某个全局变量。

```text
客户端
  │  headers: { Authorization: "Bearer xxx" }
  ▼
svc-gateway（网关）
  │  解析 token → 提取 uid
  │  改写 headers: { uid: "12345", traceId: "abc-def-ghi" }
  ▼
svc-canvas
  │  @RequestHeader("uid") Long uid   ← 从 header 直接读取
  │  如果要调下游，Feign 拦截器自动把 uid、traceId 透传出去
  ▼
svc-user
  │  同样能从 header 拿到 uid 和 traceId
```

这就好比前端的 `axios` 拦截器自动给每个请求加 `Authorization` header。后端的 Feign 也有拦截器（在 cpt-api 里统一配置），自动把关键信息一路传递下去。这样做有两个好处：

- **下游服务不用自己再解 token**：网关解一次，后面都直接读 header 里的 uid，省事也安全。Token 校验、JWT 这套机制本身见 [安全](/back-end/frontend-backend-guide/32-security)。
- **traceId 全程不变**：同一个用户请求，从网关到最末端的服务，日志里都是同一个 traceId。线上出问题时，你拿着一个 traceId 在所有服务的日志里一搜，整条链路就串起来了——这是排障的命根子，详见 [怎么读日志](/back-end/frontend-backend-guide/26-reading-logs) 和 [排查方法论](/back-end/frontend-backend-guide/27-troubleshooting-methodology)。

下面是 Feign 拦截器透传的简化样子（放在 cpt-api，所有服务共用）：

```java
@Component
public class HeaderRelayInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        // 从当前请求线程绑定的上下文里取出 uid / traceId，附加到下游请求
        ServletRequestAttributes attrs =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest req = attrs.getRequest();
            relay(template, req, "uid");
            relay(template, req, "traceId");
        }
    }

    private void relay(RequestTemplate template, HttpServletRequest req, String name) {
        String value = req.getHeader(name);
        if (value != null) {
            template.header(name, value);
        }
    }
}
```

> 注意 `RequestContextHolder` 是**绑定到当前请求线程**的（基于 ThreadLocal）。这又一次印证了"一个请求 = 一个线程"——正因为线程被这个请求独占，Spring 才能把上下文挂在线程上随取随用。这也埋下一个坑：**如果你在 Service 里把活丢到别的线程池去做，新线程里就取不到这个上下文了**，uid、traceId 会丢，需要手动传递。这个坑会在 [线程安全与锁](/back-end/frontend-backend-guide/16-thread-safety-locks) 里专门讲。

---

## 小结

- **一条主链路**：Filter / Interceptor → DispatcherServlet → Controller → Service → Mapper → DB，外加一个穿插全程的 `@RestControllerAdvice` 全局异常处理。微服务只是在外面套了网关、服务间调用和消息队列，分层不变。
- **一个请求 = 一个线程**：Tomcat 是 thread-per-request，请求从进来到返回独占一个线程（默认上限 200）。慢操作会占着线程不放，进而拖垮整个服务——这是后端必须关心并发的根本原因。
- **链路里有连接池**：Service→DB（HikariCP）和 Feign→下游都走连接池。池满时请求线程会卡在"借连接"这一步排队，等不到就超时报错；连接池和线程池一样不是越大越好。
- **同步 vs 异步**：快操作走同步（登录），耗时操作走异步 MQ（生图）——异步的本质是把重活从请求线程转移到后台线程 / 消费者，让请求线程尽快归还。
- **上下文靠 Header 透传**：uid、traceId 通过 HTTP header 在服务间传递，Feign 拦截器自动转发；traceId 是线上排障串联日志的命根子。

### 自测

1. svc-user 有个接口调了一个会卡 5 秒的外部服务，QPS 50。为什么这一个慢接口可能让 svc-user 的**所有**接口都开始转圈？请用"线程模型"那一节的逻辑解释。
2. 日志里出现 `HikariPool-1 - Connection is not available, request timed out after 30000ms`，同时 Tomcat 线程数并没有打满。这说明请求卡在了哪里？你会朝哪两个方向去排查？
3. 生图为什么用 RocketMQ 异步、而登录用 Feign 同步？如果硬把生图改成同步等待图片生成完再返回，会发生什么？

### 下一章

下一章 [三层架构与工程结构](/back-end/frontend-backend-guide/04-three-layer-and-structure) 会把这条链路里的 Controller / Service / Mapper 三层拆开讲透：每层放什么、不放什么，以及一个真实 Spring Boot 工程的目录是怎么组织的。

# Spring Boot 与 IoC/DI

> 上一章你已经能读懂 Java 语法了。这一章我们装上后端世界里最重要的"心智模型"：Spring Boot 是什么、为什么一个 `main` 就能起一整个 Web 服务，以及为什么后端代码里几乎从不自己 `new` 对象——这背后就是 IoC/DI。
>
> 这是整门课最关键的一章之一。后面所有的 `svc-*` 服务，能跑起来、能互相调用、能被测试，靠的都是这里讲的东西。

---

## 6.1 Spring Boot 是什么

一句话：**Spring Boot 是一个自带内嵌服务器（Tomcat）的 Java 应用框架，你写一个 `main` 方法，它就能把整个 Web 服务拉起来。**

> 💡 **前端类比**：你在 Next.js 里执行 `next start`，一条命令就起了一个监听端口、能处理 HTTP 请求的服务，你不用自己去配 Nginx、不用手写 `http.createServer`。Spring Boot 干的是同一件事——`java -jar svc-user.jar` 跑起来，端口就开了，路由就生效了，连接池、JSON 序列化、日志全都自带。

在 Spring Boot 出现之前，启动一个 Java Web 服务要：手动装一个 Tomcat 服务器、把代码打成 `war` 包丢进去、写一大堆 XML 配置。Spring Boot 把这些全部内置了——服务器变成你应用的一部分（叫"内嵌 Tomcat"），打出来的 `jar` 包自己就能跑。

> 💡 **前端类比**：这就像从"手动配 webpack + 起一个 express 静态服务器伺服打包产物"，进化到"`vite`/`next` 一条命令什么都帮你搞定"。约定取代了配置。

### 一个最小的 Spring Boot 应用

```java
@SpringBootApplication                 // 这一个注解 = 启动开关
public class SvcUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(SvcUserApplication.class, args);
        // 这一行执行后：
        //   1. 启动内嵌 Tomcat，监听端口（默认 8080）
        //   2. 扫描所有 @Component/@Service/... 创建对象
        //   3. 把 Controller 的路由注册到 HTTP 服务器
        //   4. 服务就绪，开始接收请求
    }
}
```

跑起来你会在控制台看到这样的日志：

```text
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

2026-06-01 10:12:03.451  INFO 18420 --- [main] c.x.svcuser.SvcUserApplication : Starting SvcUserApplication
2026-06-01 10:12:05.882  INFO 18420 --- [main] o.s.b.w.embedded.tomcat.TomcatWebServer : Tomcat started on port(s): 8080 (http)
2026-06-01 10:12:05.901  INFO 18420 --- [main] c.x.svcuser.SvcUserApplication : Started SvcUserApplication in 2.93 seconds
```

**怎么读这段日志**：

- `Tomcat started on port(s): 8080` —— 内嵌服务器已经在 8080 端口监听了，这一行没出现说明端口被占用或启动失败。
- `Started SvcUserApplication in 2.93 seconds` —— 全部初始化完成、服务就绪。看到这一行才代表你能开始发请求。
- `[main]` 是线程名；启动阶段都在 `main` 线程，等真正处理请求时会换成 Tomcat 的工作线程（后面线程池章节会讲）。

> `@SpringBootApplication` 其实是三个注解打包：`@SpringBootConfiguration` + `@EnableAutoConfiguration` + `@ComponentScan`。记住一句话就够：**它打开了"自动配置"和"自动扫描组件"两个开关。** 自动配置我们 6.7 节讲，组件扫描马上讲。

---

## 6.2 IoC 容器与 Bean

后端代码里有一个贯穿始终的概念：**IoC 容器**。

- **IoC（Inversion of Control，控制反转）**：对象的创建和组装，不再由你的代码控制，而是"反转"交给框架。你不再写 `new UserService(...)`，而是声明"我需要一个 UserService"，框架帮你造好、塞给你。
- **IoC 容器**：就是那个负责"造对象、存对象、按需分发对象"的大管家。Spring Boot 启动时就建好了这个容器。
- **Bean**：被容器管理的对象就叫 Bean。默认情况下，每个 Bean 在整个应用里**只有一个实例（单例）**，谁要用都是同一个。

> 💡 **前端类比**：把 IoC 容器想象成一个全局的、应用启动时就 `new` 好的对象池。它非常像 React 的 Context 体系或 Vue 的 `provide` 根节点——一个地方统一持有"服务"，组件树里任何地方 `useContext` / `inject` 拿到的都是同一个实例。Bean 就是被放进这个池子里、全应用共享的那个单例服务。

```text
            ┌─────────────────────────────────────────┐
            │            IoC 容器 (大管家)              │
            │  应用启动时一次性创建好这些单例 Bean       │
            │                                          │
            │   ┌──────────────┐   ┌──────────────┐    │
            │   │ UserService  │   │ UserRepo     │    │
            │   └──────┬───────┘   └──────────────┘    │
            │          │ 容器知道 UserService 需要      │
            │          │ UserRepo，启动时就帮它接好     │
            │   ┌──────┴───────┐   ┌──────────────┐    │
            │   │ UserCtrl     │   │ RedisClient  │    │
            │   └──────────────┘   └──────────────┘    │
            └─────────────────────────────────────────┘
                  ↑ 谁要用 UserService，容器就发同一个
```

> ⚠️ **单例的含义很重要**：因为一个 Bean 全应用共享，它通常**不应该持有可变的实例字段（状态）**。比如 `svc-canvas` 里的 `TaskService` 是单例，成百上千个请求同时调它的方法，如果你在它身上存一个会被改写的成员变量，就会出并发问题。这就是后面"线程安全"章节要解决的事；现在先记住：Service/Controller 都是单例、无状态。

---

## 6.3 四个 stereotype 注解：@Component / @Service / @Repository / @Configuration

怎么让一个类变成被容器管理的 Bean？给它贴一个"我是组件"的标签，启动时的组件扫描（`@ComponentScan`）就会发现它、把它造成 Bean。这类标签有四个，**功能基本相同，区别主要是语义（告诉读代码的人这是哪一层）**：

```java
@Component        // 最通用的"我是一个 Bean"标记。其它三个本质上都是它的特化。
                  // 用在不属于明确某一层的工具类、组件上。

@Service          // 语义：这是业务逻辑层。放业务编排、规则、事务。
                  // 例：svc-user 里的 UserService（登录、扣配额）

@Repository       // 语义：这是数据访问层（DAO）。负责读写数据库。
                  // 附带能力：把数据库底层异常转成 Spring 统一的异常类型。

@Configuration    // 语义：这是一个配置类，里面用 @Bean 方法手动定义 Bean（见 6.6）。
```

> 💡 **前端类比**：这四个就像你给文件起的目录名约定——`services/`、`repositories/`、`config/`。功能上都是普通模块，但放对目录、贴对标签，别人一眼就知道这段代码属于哪一层。这正好对应上一章学过的"三层架构"。

放到我们的项目里，`svc-user` 一个典型的分层长这样：

```java
// ===== 控制器层（Controller，下一章细讲）=====
@RestController
@RequestMapping("/v1/user")
public class UserController { ... }

// ===== 业务层（Service）=====
@Service
public class UserService { ... }      // 登录、扣配额、查用户的业务逻辑

// ===== 数据访问层（Repository）=====
@Repository
public class UserRepository { ... }   // 真正去 MongoDB 读写 user_mst

// ===== 配置类 =====
@Configuration
public class RedisConfig { ... }       // 定义 RedisClient 等 Bean
```

| 注解 | 语义（哪一层） | 项目里的例子 | 前端类比 |
| --- | --- | --- | --- |
| `@Component` | 通用组件 | 工具类、拦截器 | 一个普通的全局模块 |
| `@Service` | 业务逻辑层 | `UserService` | `services/` 下的模块 |
| `@Repository` | 数据访问层 | `UserRepository` | `repositories/` / data-access 层 |
| `@Configuration` | 配置类 | `RedisConfig` | 一个 `config.ts` 工厂文件 |

---

## 6.4 依赖注入（DI）：为什么不自己 new

**DI（Dependency Injection，依赖注入）是 IoC 的具体实现手段**：容器把一个 Bean 需要的其它 Bean，自动"注入"进去。

为什么这是个大问题？看看不用 DI、纯手动 `new` 会发生什么。`UserService` 要扣配额，依赖 `UserRepository`（读写数据库）和 `RedisClient`（分布式锁/缓存），而 `UserRepository` 又依赖 `MongoClient`：

### 反例：传统手动 new（层层嵌套，痛苦）

```java
// 在 Controller 里要用 UserService，于是自己把整条依赖链 new 出来
MongoClient mongoClient = new MongoClient("mongodb://...");
UserRepository userRepository = new UserRepository(mongoClient);
RedisClient redisClient = new RedisClient("redis://...");
UserService userService = new UserService(userRepository, redisClient);
// 终于可以用了……但是：
//   - 每个用到 UserService 的地方都要重复这一坨
//   - 改一个构造参数，所有调用点全要改
//   - MongoClient 被 new 了好多次，连接资源浪费
//   - 想换成测试用的假 Repository？没法换，写死了
```

> 💡 **前端类比**：这就是前端里你最讨厌的 "props drilling / 手动层层传依赖"。为了让深层组件拿到一个 service，你得在每一层手动 `new` 并往下传。React 用 `useContext`、Vue 用 `provide/inject` 来解决它——**让需要的人直接从容器里取，而不是层层手动喂。** DI 就是后端版的同一个解法。

### 正解：Spring 自动注入（声明即可）

```java
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RedisClient redisClient;

    // 你只声明"我需要这两个"，容器启动时自动把现成的实例塞进来
    public UserService(UserRepository userRepository, RedisClient redisClient) {
        this.userRepository = userRepository;
        this.redisClient = redisClient;
    }

    public RtData<Void> deductQuota(Long uid, int cost) {
        // 直接用，不关心它们是怎么造出来的
        if (userRepository.getQuota(uid) < cost) {
            return RtData.fail("配额不足");
        }
        userRepository.decreaseQuota(uid, cost);
        return RtData.ok();
    }
}
```

你再也不用写那一坨 `new`。容器在启动时就把 `UserRepository`、`RedisClient`、`MongoClient` 全造好、按依赖关系接好，谁要 `UserService` 就直接发现成的。

> 这就是"控制反转"的字面意思：**创建对象的控制权，从你的代码反转到了容器手里。**

---

## 6.5 构造器注入 vs 字段注入（@Autowired）

注入有两种常见写法，**强烈推荐构造器注入**。

### 写法一：构造器注入（推荐）

```java
@Service
public class TaskService {                 // svc-canvas 里的任务编排服务

    private final AiClient aiClient;        // final：注入后不可变
    private final TaskRepository taskRepo;

    // 只有一个构造器时，@Autowired 可以省略，Spring 会自动用它注入
    public TaskService(AiClient aiClient, TaskRepository taskRepo) {
        this.aiClient = aiClient;
        this.taskRepo = taskRepo;
    }
}
```

### 写法二：字段注入（不推荐，但你会大量见到）

```java
@Service
public class TaskService {

    @Autowired                              // 直接贴在字段上
    private AiClient aiClient;

    @Autowired
    private TaskRepository taskRepo;
}
```

字段注入代码看起来更短，但有几个真实的坑：

- **字段不能用 `final`**，无法保证依赖不被中途替换，对象可能处于"半初始化"状态。
- **没法脱离 Spring 容器构造对象**——写单元测试时你想 `new TaskService(mockAiClient, mockRepo)` 直接传假依赖进去，字段注入根本做不到（构造器是空的）。
- **隐藏了依赖数量**：一个类悄悄注入了十几个字段你都看不出来；而构造器参数列表一长，你立刻意识到"这个类职责太多了，该拆了"——这是一个有用的设计信号。

> 💡 **前端类比**：构造器注入像把依赖作为函数参数显式传入（纯函数、好测试、依赖一目了然）；字段注入像偷偷读全局变量/单例（能跑，但耦合到运行环境、难测、依赖藏起来了）。前端你也会优先选"显式传参"。

**结论：默认用构造器注入。** 配合 Lombok 的 `@RequiredArgsConstructor` 还能省掉手写构造器：

```java
@Service
@RequiredArgsConstructor                    // Lombok 自动为所有 final 字段生成构造器
public class TaskService {
    private final AiClient aiClient;
    private final TaskRepository taskRepo;
    // 不用手写构造器了，依赖照样靠构造器注入，干净又安全
}
```

> ⚠️ **同类型多个 Bean 怎么办？** 如果容器里有两个同类型的 Bean（比如两个 `RedisClient`），注入时框架不知道选哪个，会报 `NoUniqueBeanDefinitionException`。这时用 `@Qualifier("beanName")` 指定名字，或用 `@Resource` 按名称注入。先有个印象，遇到再查。

---

## 6.6 @Bean 方法：手动造 Bean

`@Component`/`@Service` 适合**你自己写的类**——贴个标签就行。但很多类是**第三方库里的**（比如 `RedisClient`、`ObjectMapper`、`RestTemplate`），你没法去人家源码上加注解。这时用 `@Configuration` + `@Bean` 方法手动告诉容器怎么造：

```java
@Configuration
public class RedisConfig {

    // 方法名 redisClient 就是这个 Bean 的名字
    // 返回值会被容器登记成一个单例 Bean，之后哪里需要 RedisClient 就注入它
    @Bean
    public RedisClient redisClient(
        @Value("${redis.host}") String host,   // 从配置文件读取 redis.host
        @Value("${redis.port}") int port
    ) {
        RedisClient client = new RedisClient(host, port);
        client.setTimeout(2000);
        return client;
    }
}
```

> 💡 **前端类比**：`@Bean` 方法就是一个"工厂函数"。等价于你在 `redis.ts` 里 `export const redisClient = createClient({ host, port })`，应用里其他模块 `import` 它——而且大家拿到的是同一个实例（单例）。区别只是：前端靠 `import` 拿，后端靠容器注入拿。

**两种定义 Bean 的方式对比**：

| 方式 | 适用场景 | 怎么写 |
| --- | --- | --- |
| `@Component`/`@Service`/`@Repository` | 你自己写的类 | 在类上贴注解，容器自动扫描 |
| `@Configuration` + `@Bean` | 第三方库的类、需要自定义构造逻辑 | 写一个返回该对象的方法 |

`@Value("${redis.host}")` 是从配置文件（`application.yml`）读值，这块属于配置体系，细节见 [Spring Boot 配置](/back-end/java/07b-spring-config)，以及本课程后续的 [配置与环境变量](/back-end/frontend-backend-guide/25-config-and-env)。

---

## 6.7 自动配置：约定优于配置

最后一块拼图。前面说 `@SpringBootApplication` 打开了"自动配置（auto-configuration）"开关，它到底干了啥？

一句话原理：**Spring Boot 会检查你引入了哪些依赖、配了哪些值，然后"猜"出一套合理的默认 Bean 帮你装好——这叫"约定优于配置"。**

举个真实例子：你在 `svc-user` 的依赖里加了一行 `spring-boot-starter-data-mongodb`，又在配置文件写了 `spring.data.mongodb.uri=...`。你**什么都不用做**，启动时自动配置就帮你造好了连 MongoDB 的客户端 Bean，你直接注入就能用。如果哪天你自己用 `@Bean` 定义了一个同类型的，它又会"识趣地让位"，用你的那个。

```text
自动配置的判断逻辑（简化）：
  classpath 里有 MongoDB 驱动？  ──┐
  配置文件里有 mongodb.uri？      ──┼──→ 是 → 自动造一个 MongoClient Bean
  你没有自己定义同类型 Bean？     ──┘        否则 → 不插手，用你的
```

> 💡 **前端类比**：像 Next.js 的"约定式路由"——你把文件丢进 `app/` 目录，框架按约定自动生成路由，你不用手写路由表；想自定义时再覆盖。Spring Boot 的自动配置是同一套哲学：**默认帮你配好 90% 的常规场景，你只在需要时覆盖。**

这就是为什么 Spring Boot 项目能"开箱即用"：内嵌 Tomcat、JSON 序列化、数据库连接池、日志……大多是自动配置默默装好的，你只在 `application.yml` 里改改参数。

---

## 6.8 把整章串起来

回到 `svc-user` 一次登录请求，看看这一章的概念各自在哪：

```text
1. java -jar svc-user.jar
   → @SpringBootApplication 触发：起 Tomcat + 组件扫描 + 自动配置
                                  (6.1)        (6.3)        (6.7)

2. 容器创建并接好所有 Bean（单例）：
   UserController ←需要← UserService ←需要← UserRepository + RedisClient
                         全部由容器构造器注入接好，没有一行手写 new  (6.2/6.4/6.5)

3. 请求 POST /v1/user/login 进来
   → Tomcat 交给 UserController（已是现成 Bean）
   → 它调用注入进来的 UserService.login(...)
   → UserService 调用注入进来的 UserRepository 读 MongoDB
   → 返回 RtData.ok(token)
```

整条链路里你写的全是业务逻辑，"谁依赖谁、谁先造、造几个"这些活全交给了容器。这就是 IoC/DI 给后端开发带来的核心价值。

想从纯 Java 角度更系统地理解容器与注入机制，强烈建议配合阅读 [Spring IoC 与依赖注入](/back-end/java/07a-spring-ioc-di)；想知道这些注解全集，可看本课程的 [注解速查表](/back-end/frontend-backend-guide/08-annotations-cheatsheet)。

---

## 小结

- **Spring Boot** = 自带内嵌 Tomcat 的应用框架，一个 `main` + `@SpringBootApplication` 就能拉起整个 Web 服务，约定取代了繁琐配置（类比 `next start`）。
- **IoC 容器**是启动时建好的"对象大管家"，被它管理的对象叫 **Bean**，默认是**全应用共享的单例**——所以 Service 通常无状态。
- `@Component`/`@Service`/`@Repository`/`@Configuration` 功能相近，区别是**语义分层**；第三方类用 `@Configuration` + `@Bean` 方法手动定义。
- **DI（依赖注入）**让你不再手动 `new` 层层依赖（类比 `useContext`/`provide-inject`）；**优先用构造器注入**，它可 `final`、好测试、依赖显式可见，远胜 `@Autowired` 字段注入。
- **自动配置**按"约定优于配置"原则，根据依赖和配置自动装好常规 Bean，需要时你再覆盖。

### 自测

1. 一个 `@Service` 的 Bean 默认是单例。如果你在它里面加一个会被每个请求修改的成员变量，会有什么风险？为什么？
2. 同样是把依赖塞进类里，构造器注入相比 `@Autowired` 字段注入有哪三个实打实的好处？
3. 你要把第三方库的 `RestTemplate` 变成一个可注入的 Bean，但又不能改它的源码。应该用哪个注解、怎么写？

### 下一章

掌握了容器和注入，下一章我们就动手写第一个真正能跑、能存数据的接口：[动手写一个 CRUD API](/back-end/frontend-backend-guide/07-build-a-crud-api)。

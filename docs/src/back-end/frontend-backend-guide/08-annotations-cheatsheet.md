# 关键注解速查

> 本章是一本**随用随查的字典**，不需要从头读到尾。写代码时看到一个不认识的 `@注解`，回来翻这张表就行。
>
> Java 注解（Annotation）就是代码上面那个 `@` 开头的东西。如果你写过 TypeScript 装饰器（`@decorator`）或 Python 装饰器（`@wrapper`），你会觉得很眼熟。不同的是，Java 注解大多由框架（Spring）在运行时读取并执行特定逻辑——注解本身不做事，是框架看到它才去做事。

读到具体某个注解想看它**为什么**这么设计、IoC/DI 到底怎么运转，请去看 [Spring Boot 的 IoC 与 DI](/back-end/frontend-backend-guide/06-spring-boot-ioc-di)；本章只负责让你"认得、会用、用对位置"。

---

## 控制器层注解

这些注解是你在 svc-user、svc-auth 等服务的 `controller/` 目录下最常见的：

```java
@RestController                    // 标记这个类是一个 REST API 控制器
                                   // 前端类比：export default 一个 API handler 文件

@RequestMapping("/v1/user")        // 给这个控制器设置 URL 前缀
                                   // 前端类比：app.use('/v1/user', router)

@GetMapping("/profile")            // 处理 GET /v1/user/profile
@PostMapping("/login")             // 处理 POST /v1/user/login
@PutMapping("/profile")            // 处理 PUT /v1/user/profile
@DeleteMapping("/{id}")            // 处理 DELETE /v1/user/{id}
```

### 完整示例（svc-user 的登录 + 查资料）

```java
@RestController                            // 这是一个 API 控制器
@RequestMapping("/v1/user")                // 前缀 /v1/user
@Slf4j                                     // 自动生成 log 对象（后面讲）
public class UserController {

    @Autowired                             // 注入 service（后面讲）
    private UserService userService;

    @PostMapping("/login")                 // POST /v1/user/login
    public RtData<LoginResponse> login(
        @RequestBody @Validated LoginDto dto   // 从请求体读取 JSON 并校验
    ) {
        return RtData.ok(userService.login(dto));
    }

    @GetMapping("/profile")                // GET /v1/user/profile
    public RtData<UserProfile> getProfile(
        @RequestHeader("uid") Long uid     // 从请求头读取 uid（网关鉴权后透传下来）
    ) {
        return RtData.ok(userService.getProfile(uid));
    }
}
```

> 💡 **前端类比**：`@RestController` + `@RequestMapping` 合起来就像 Express 里的一个 `router`，方法上的 `@PostMapping("/login")` 就是 `router.post('/login', handler)`。返回的对象会被自动序列化成 JSON——你不用手写 `res.json(...)`，写 `return RtData.ok(data)` 就够了。

---

## 参数绑定注解

这些注解告诉框架"从请求的哪个位置读取参数"：

```java
@RequestBody LoginDto dto              // 读取请求体 JSON 并映射为对象
                                       // 前端类比：const dto = req.body

@RequestHeader("uid") Long uid         // 读取请求头
                                       // 前端类比：const uid = req.headers['uid']

@PathVariable("id") String id          // 读取路径参数
                                       // 前端类比：const { id } = req.params
                                       // URL: /user/{id} → /user/123

@RequestParam("page") int page         // 读取查询参数
                                       // 前端类比：const page = req.query.page
                                       // URL: /users?page=1

@Validated                             // 自动校验参数（配合 DTO 中的校验注解）
                                       // 前端类比：zod.parse(dto) 或 yup.validate(dto)
```

> ⚠️ **最常踩的坑**：`@RequestBody` 和 `@RequestParam` 弄混。
> POST 的 JSON 在请求体里，用 `@RequestBody`；URL 上 `?page=1` 这种在查询串里，用 `@RequestParam`。前端用 axios 时，`axios.post(url, data)` 的 `data` 对应 `@RequestBody`，`axios.get(url, { params })` 的 `params` 对应 `@RequestParam`。

### DTO 校验注解示例

```java
public class LoginDto {
    @NotBlank(message = "手机号不能为空")     // 非空校验（字符串去空格后不能为空）
    private String phone;

    @NotBlank(message = "验证码不能为空")
    @Size(min = 4, max = 6)                  // 长度校验
    private String verifyCode;
}
```

控制器方法参数加上 `@Validated` 后，校验不通过会自动抛异常，由全局异常处理器转成 `RtData.fail(msg)`，前端拿到的就是一条人能读懂的错误信息。

> 💡 **前端类比**：这就是 Java 版的 zod schema——校验规则贴在字段上，框架在入口处帮你挡住非法数据：
> ```typescript
> const LoginSchema = z.object({
>   phone: z.string().min(1, "手机号不能为空"),
>   verifyCode: z.string().min(4).max(6),
> })
> ```

常用校验注解一并记住：

| 注解 | 作用 | zod 对应 |
| --- | --- | --- |
| `@NotNull` | 不能为 null | `.nullable()` 的反面 |
| `@NotBlank` | 字符串非空且非纯空格 | `.string().min(1)` |
| `@NotEmpty` | 集合/字符串非空 | `.array().nonempty()` |
| `@Size(min, max)` | 长度/元素个数范围 | `.min().max()` |
| `@Min` / `@Max` | 数值范围 | `.gte()` / `.lte()` |
| `@Email` | 邮箱格式 | `.email()` |
| `@Pattern(regexp)` | 正则匹配 | `.regex()` |

---

## 业务层注解

```java
@Service                     // 标记为业务逻辑类
                             // 前端类比：定义一个 service 模块

@Repository                  // 标记为数据访问类
                             // 前端类比：定义一个 data-access 模块

@Component                   // 通用组件标记，Spring 会自动创建实例并管理
                             // 前端类比：通过 provide() 注册一个全局服务

@Autowired                   // 自动注入依赖
                             // 前端类比：const service = useContext(ServiceContext)
                             // 或 Vue 的 inject('service')

@Resource                    // 功能同 @Autowired，但默认按名称注入
                             // 当有多个同类型 Bean 时用名称区分
```

`@Service`、`@Repository`、`@Component` 本质上是同一件事的三个语义化别名：都告诉 Spring "把这个类管起来，自动 new 一个实例放进容器"。差别只在于读代码的人一眼就知道这是业务层、数据层还是通用组件。

### 依赖注入原理（重要概念）

```text
传统写法（前端思维）：
  const userService = new UserService(new UserRepository(new MongoClient()))
  // 你要自己 new 所有依赖，层层嵌套，谁依赖谁全靠手动拼

Spring 写法（后端）：
  @Autowired
  private UserService userService;
  // 框架启动时自动 new 好所有依赖、按需注入，你直接用就行

前端类比：
  // Vue 3 的 provide/inject
  // 父组件 provide('userService', new UserService())
  // 子组件 const userService = inject('userService')
```

一句话：**你不再自己 `new` 对象，而是声明"我需要什么"，由容器递给你。** 这套机制叫 IoC（控制反转）/ DI（依赖注入），是整个 Spring 的地基。

> 想看它完整运转的过程——Bean 是什么、容器何时创建、构造器注入 vs 字段注入怎么选、出现多个候选 Bean 怎么办——去读 [Spring Boot 的 IoC 与 DI](/back-end/frontend-backend-guide/06-spring-boot-ioc-di)。本章不展开，只让你认得这几个注解。

---

## 数据层注解

我们的项目里 MongoDB 是主库（用 Spring Data），MySQL 做统计（用 MyBatis-Plus），两套 ORM 的注解长得不一样，对照着记：

```java
// ===== MongoDB（Spring Data，svc-canvas 的任务文档） =====
@Document(collection = "canvas_task")  // 标记这个类映射到 MongoDB 的 canvas_task 集合
@Id                                     // 标记主键字段（_id）
@Indexed                                // 给这个字段建索引

// ===== MySQL（MyBatis-Plus，svc-user 的每日统计表） =====
@TableName("daily_statistics")         // 标记这个类映射到 MySQL 的 daily_statistics 表
@TableId(type = IdType.AUTO)           // 标记主键并设置为自增
@TableField("user_id")                 // 字段名与列名不一致时显式映射
```

> 💡 **前端类比**：这就像 Prisma 的 `@@map("table_name")` / `@map("column")`——把代码里的属性名和数据库里的表名、列名对应起来。区别是 Prisma 写在 schema 文件里，这里写在 Java 类的字段上。

---

## 常见辅助注解

```java
@Slf4j
// 自动生成日志对象 log（Lombok 提供，编译期帮你写好那行 LoggerFactory）
// 前端类比：const log = console
// 用法：log.info("用户 {} 登录成功", uid);
//       log.error("登录失败", exception);
//       注意占位符是 {}，不是模板字符串 ${}

@Value("${app.name:默认值}")
// 从配置文件（application.yml）读取值，冒号后是默认值
// 前端类比：const appName = import.meta.env.VITE_APP_NAME ?? '默认值'

@PostConstruct
// 对象创建完成后自动执行一次（常用于初始化缓存、预加载配额规则）
// 前端类比：useEffect(() => { /* 初始化 */ }, [])
// 或 Vue 的 onMounted()

@ConditionalOnProperty(name = "feature.xx", havingValue = "true")
// 根据配置决定是否加载这个类（功能开关 / feature flag）
// 前端类比：if (featureFlags.xx) { loadModule() }
```

### Lombok 一族（少写样板代码）

`@Slf4j` 来自 Lombok，它还有一批"帮你少敲键盘"的注解，项目里到处都是：

```java
@Data                  // 自动生成 getter/setter/toString/equals/hashCode
                       // 前端类比：本来就有的 obj.field，Java 需要这个才有

@Builder               // 生成链式构建器：LoginDto.builder().phone("...").build()
                       // 前端类比：直接写对象字面量 { phone: '...' }

@AllArgsConstructor    // 生成全参构造器
@NoArgsConstructor     // 生成无参构造器
@RequiredArgsConstructor // 给 final 字段生成构造器（推荐用它做构造器注入）
```

> 💡 Lombok 的本质是**编译期代码生成**，类似前端的 Babel 插件——你写的简洁代码在编译后会被展开成完整的 Java。所以 IDE 需要装 Lombok 插件才能正确识别这些"凭空多出来"的方法。

---

## 注解速查卡片

随用随查，认不出来就翻这张表：

| 注解 | 一句话解释 | 前端类比 |
| --- | --- | --- |
| `@RestController` | 这个类是 API 入口 | API route handler |
| `@RequestMapping` | URL 前缀 | `app.use('/prefix')` |
| `@GetMapping` | 处理 GET 请求 | `router.get()` |
| `@PostMapping` | 处理 POST 请求 | `router.post()` |
| `@RequestBody` | 读取请求体 JSON | `req.body` / axios `data` |
| `@RequestHeader` | 读取请求头 | `req.headers['xxx']` |
| `@PathVariable` | 读取路径参数 | `req.params.xxx` |
| `@RequestParam` | 读取查询参数 | `req.query.xxx` / axios `params` |
| `@Validated` | 自动校验参数 | `zod.parse()` |
| `@NotBlank` / `@Size` | 字段级校验规则 | zod schema 字段约束 |
| `@Service` | 标记业务逻辑类 | Service 模块 |
| `@Repository` | 标记数据访问类 | data-access 模块 |
| `@Component` | 通用受管组件 | `provide()` 注册的服务 |
| `@Autowired` / `@Resource` | 自动注入依赖 | `inject()` / `useContext()` |
| `@Slf4j` | 自动生成 log | `console` |
| `@Value` | 读取配置值 | `import.meta.env` |
| `@PostConstruct` | 创建后初始化一次 | `onMounted()` / `useEffect([])` |
| `@Data` / `@Builder` | 生成样板代码 | 对象字面量 / Babel 转换 |
| `@Document` | MongoDB 集合映射 | Prisma `@@map` |
| `@TableName` / `@TableId` | MySQL 表/主键映射 | Prisma `@@map` / `@id` |

---

## 小结

- 注解本身不做事，是**框架运行时读到它才去做事**——心智模型等同于 TS/Python 装饰器。
- 按"在哪层"记最省力：控制器层（路由 + 参数绑定 + 校验）、业务层（受管组件 + 注入）、数据层（表/集合映射）、辅助（日志、配置、Lombok）。
- 参数绑定别记混：请求体 `@RequestBody`、查询串 `@RequestParam`、路径 `@PathVariable`、请求头 `@RequestHeader`。
- 校验注解贴在 DTO 字段上、`@Validated` 贴在方法参数上，二者配合才生效，效果等同前端的 zod。
- `@Service`/`@Repository`/`@Component` 是同一机制的三个语义别名；`@Autowired` 背后是 IoC/DI 这套地基。

### 自测

1. 前端用 `axios.get('/v1/user/list', { params: { page: 1 } })` 请求，后端方法该用哪个注解接 `page`？换成 `axios.post('/v1/user/login', dto)` 又该用哪个？
2. DTO 字段上写了 `@NotBlank`，但请求传空值时校验没生效，最可能漏了什么？
3. `@Service` 和 `@Repository` 在功能上有区别吗？为什么还要分两个？

### 下一章

注解认全了，接下来该正经看数据库了——先搞清楚关系型与 NoSQL 各自适合什么场景：[SQL vs NoSQL](/back-end/frontend-backend-guide/09-sql-vs-nosql)。

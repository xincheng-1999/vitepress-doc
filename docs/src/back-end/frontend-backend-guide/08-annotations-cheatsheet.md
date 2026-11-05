# 关键注解速查

::: info 按需查阅
看到不认识的 `@注解` 时，再来查这一页，不需要先背完。旧文中的 `svc-*`、`cpt-*`、`RtData` 等只是示意名称，不是一套需要另找的可运行工程；本页示例也省略了业务类和 import。第一次学习可以继续走 [五阶段主线](./index)。
:::

比如看到这一行：

```java
@PostMapping("/login")
public RtData<LoginResponse> login(@RequestBody @Validated LoginDto dto) {
    return RtData.ok(userService.login(dto));
}
```

先读出三件事就够了：它处理登录的 POST 请求，从请求体读取数据，并在进入方法前校验数据。至于登录怎么完成，要继续看方法里的业务调用，注解不会替你实现它。

**注解是附在代码上的说明，由编译器、代码生成工具或框架读取。** 不全是在运行时生效：Spring 的路由注解由框架处理，Lombok 则在编译期生成代码。它们看起来像 TypeScript 装饰器，但具体执行机制不能直接等同。

## 请求怎么进入方法

| 注解 | 放在哪里 | 作用 |
| --- | --- | --- |
| `@RestController` | 类上 | 注册控制器，返回值通常写入响应体；对象常被转换为 JSON |
| `@RequestMapping("/v1/user")` | 类上或方法上 | 指定请求路径等匹配条件；类上的路径通常作为前缀 |
| `@GetMapping("/profile")` | 方法上 | 处理 GET 请求 |
| `@PostMapping("/login")` | 方法上 | 处理 POST 请求 |
| `@PutMapping("/profile")` | 方法上 | 处理 PUT 请求 |
| `@DeleteMapping("/{id}")` | 方法上 | 处理 DELETE 请求 |

类上的前缀和方法上的路径接在一起，才是最终路径：

```java
@RestController
@RequestMapping("/v1/user")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login") // POST /v1/user/login
    public RtData<LoginResponse> login(@RequestBody @Validated LoginDto dto) {
        return RtData.ok(userService.login(dto));
    }

    @GetMapping("/profile") // GET /v1/user/profile
    public RtData<UserProfile> getProfile(@RequestHeader("uid") Long uid) {
        return RtData.ok(userService.getProfile(uid));
    }
}
```

这里的 `RtData` 只是示意用的响应包装类，不是 Spring 内置类型。示例保留了从请求头取 `uid` 的写法，但**读到请求头不等于完成鉴权**：客户端可以伪造它。只有可信网关已校验身份、覆盖外部同名头，且服务不能绕过网关访问等条件成立时，才能按约定信任；服务仍要检查数据访问权限。

## 参数从哪里来

| 注解 | 对应请求位置 | 例子 |
| --- | --- | --- |
| `@RequestBody` | 请求体 | POST 的 JSON → `LoginDto` |
| `@RequestParam("page")` | 请求参数，常见是查询串，也可以是表单字段 | `/users?page=1` → `int page` |
| `@PathVariable("id")` | 路径中的占位符 | `/tasks/{id}` 匹配 `/tasks/123` → `String id` |
| `@RequestHeader("uid")` | 请求头 | `uid: 1001` → `Long uid` |

如果前端使用 axios，`axios.post(url, data)` 中的 JSON `data` 通常对应 `@RequestBody`；`axios.get(url, { params: { page: 1 } })` 对应 `@RequestParam("page")`。**不是根据 GET 或 POST 猜注解，而是看数据实际放在哪里。**

## 怎么触发输入校验

```java
public class LoginDto {
    @NotBlank(message = "手机号不能为空")
    private String phone;

    @NotBlank(message = "验证码不能为空")
    @Size(min = 4, max = 6)
    private String verifyCode;

    // getter、setter 省略
}
```

字段上的注解定义规则，控制器参数上的 `@Validated`（也常见 `@Valid`）触发对象校验。项目还需要相应的校验依赖；Spring Boot 3 的这些字段校验注解来自 `jakarta.validation.constraints`。

校验不通过时，Spring 会产生相应异常。**是否转成 `RtData.fail(...)`、返回什么状态码，要看项目的异常处理器**，不是校验注解自动决定的。格式校验也不能代替登录认证、权限和业务规则。

| 注解 | 检查什么 | 容易误解的地方 |
| --- | --- | --- |
| `@NotNull` | 不能是 `null` | 不检查空字符串或空集合 |
| `@NotBlank` | 字符串不是 `null`，且含非空白字符 | 适合必填文本；手机号格式还要另定规则 |
| `@NotEmpty` | 字符串、集合、数组等非空 | 字符串只含空格也可能通过 |
| `@Size(min, max)` | 字符串长度或集合元素个数 | 通常允许 `null`，必填时配合非空规则 |
| `@Min`、`@Max` | 数值上下限 | 不是字符串长度 |
| `@Email` | 邮箱格式 | 不代表邮箱存在或属于此用户 |
| `@Pattern(regexp = "...")` | 字符串匹配正则 | 规则和错误信息都要自己定义 |

## 对象由谁创建，依赖从哪里来

| 注解 | 常见位置 | 作用与边界 |
| --- | --- | --- |
| `@Component` | 通用组件类 | 在组件扫描等条件满足时，注册为 Spring 管理的对象 |
| `@Service` | 业务类 | `@Component` 的业务层语义标记 |
| `@Repository` | 数据访问类 | 组件标记；配合相应机制还可参与持久化异常转换 |
| `@Autowired` | 构造器、字段等 | 让 Spring 按类型等规则寻找并注入依赖 |
| `@Resource` | 字段、setter 等 | 常用于按名称寻找依赖，也有相应的类型匹配规则 |

Spring 管理的对象叫 **Bean**。上面的 `UserController` 通过构造器拿到 `UserService`，不用在方法里自己 `new` 它。只有一个构造器时，通常可以省略 `@Autowired`。多个候选对象、扫描范围等细节见 [Spring Boot 的 IoC 与 DI](/back-end/frontend-backend-guide/06-spring-boot-ioc-di)，这里不重复展开。

## Java 对象怎么对应数据库记录

下面是**两种不同框架的写法**，不是要求任务系统同时安装两套数据库：

| 注解 | 所属框架 | 含义 |
| --- | --- | --- |
| `@Document(collection = "canvas_task")` | Spring Data MongoDB | 类映射到指定集合 |
| `@Id` | 此处指 Spring Data 的注解 | 标记 MongoDB 文档的 `_id` 字段 |
| `@Indexed` | Spring Data MongoDB | 声明索引信息；实际创建还取决于配置或显式建索引操作 |
| `@TableName("daily_statistics")` | MyBatis-Plus | 类映射到指定关系表 |
| `@TableId(type = IdType.AUTO)` | MyBatis-Plus | 声明主键映射和自增策略，数据库也要正确配置 |
| `@TableField("user_id")` | MyBatis-Plus | 属性名与列名的显式映射 |

同名注解可能来自不同包，读代码时要一起看 import。主线 CRUD 用的是 JDBC，不需要为了看懂它引入这里的注解。

## 配置、初始化与日志

| 注解 | 谁处理 | 常见用途 |
| --- | --- | --- |
| `@Value("${app.name:默认值}")` | Spring | 从配置中读取值，冒号后是默认值 |
| `@PostConstruct` | 生命周期管理机制 | 依赖注入完成后执行初始化方法，不是每次请求执行 |
| `@ConditionalOnProperty(name = "feature.xx", havingValue = "true")` | Spring Boot | 按配置决定是否注册相关 Bean；不是每次调用都判断开关 |
| `@Slf4j` | Lombok | 编译时生成 `log` 字段 |

```java
log.info("任务 {} 创建成功", taskId);
log.error("任务创建失败", exception);
```

日志占位符是 `{}`，不是 `${}`。不要把验证码、密码等敏感数据直接写进日志。

## Lombok：省掉哪些样板代码

| 注解 | 生成内容 | 注意 |
| --- | --- | --- |
| `@Data` | getter、setter、`toString`、`equals`、`hashCode` 等 | 不只是开放字段；生成的方法也要符合对象语义 |
| `@Builder` | 构建器，如 `LoginDto.builder().phone("...").build()` | 不会自动校验业务数据 |
| `@AllArgsConstructor` | 全参构造器 | 与其他构造器写法一起使用时注意冲突 |
| `@NoArgsConstructor` | 无参构造器 | 某些框架需要它，并非所有类都需要 |
| `@RequiredArgsConstructor` | 为未初始化的 `final` 字段和相应非空字段生成构造器 | 常用于构造器注入 |

这些代码在编译期生成。IDE 的 Lombok 支持和构建中的注解处理都需要正常工作，不能仅凭编辑器没报错判断构建成功。

查完一个注解，回到它所在的方法继续读即可。若要了解数据库选择，可按需看 [SQL vs NoSQL](/back-end/frontend-backend-guide/09-sql-vs-nosql)，不用把本页当作进入后续章节的考试。

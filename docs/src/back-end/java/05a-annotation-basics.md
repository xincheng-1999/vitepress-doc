# 05a. 注解入门 (Annotation)

> 目标：理解注解是什么、怎么读、为什么 Spring 项目里到处都是 `@`。

如果你读 Spring Boot 代码，第一眼看到的就是大量的 `@` 符号：`@RestController`、`@GetMapping`、`@Autowired`……
它们不是装饰、不是注释，而是 Java 的**注解 (Annotation)**——一种给代码"贴标签"的元数据机制。

## 1. 注解是什么？

一句话：**注解是写给程序（编译器 / 框架）看的标签。**

> **前端类比**：TypeScript 装饰器 `@decorator` 或 JSDoc 的 `@param`、`@returns`。  
> 区别在于 Java 的注解是语言级特性，编译器和框架都能在运行时读取它。

```java
@Override          // 告诉编译器：这个方法是重写父类/接口的
@Deprecated        // 告诉 IDE：这个方法过时了，有警告线
@SuppressWarnings  // 告诉编译器：别给我报这类警告
```

注解本身**不会改变代码的执行逻辑**，但框架会在运行时读取这些标签，然后做出相应动作。

## 2. 内置注解（编译器级别）

这三个你会经常遇到：

### 2.1 @Override

```java
public class Dog extends Animal {

    @Override   // ← 编译器帮你检查：Animal 上是否真的有 eat() 方法
    public void eat() {
        System.out.println("啃骨头");
    }
}
```

如果你写成 `eatt()`（多打了一个 t），加了 `@Override` 编译器会立刻报错；不加的话只会当成新方法，bug 很难找。

### 2.2 @Deprecated

```java
@Deprecated
public void oldMethod() {
    // 别再用了，改用 newMethod()
}
```

IDE 会把调用处标上删除线：~~oldMethod()~~

### 2.3 @SuppressWarnings

```java
@SuppressWarnings("unchecked")  // 压制未检查类型转换的警告
List list = new ArrayList();
```

不常手写，了解即可。

## 3. 框架注解（Spring Boot 级别）

这才是重头戏。Spring Boot 的核心思想是：**用注解代替 XML 配置。**

> **前端类比**：想象你的 Vue 组件不需要写 `app.component('MyComponent', {...})`，只要在文件里加一个 `@Component` 就自动注册到全局——Spring 就是这么干的。

### 3.1 组件注册类注解

这些注解决定"把这个类交给 Spring 管理"：

```java
@Component      // 通用组件——Spring 会自动扫描并创建实例
@Service        // 业务逻辑层——功能等价于 @Component，语义更明确
@Repository     // 数据访问层——功能等价于 @Component，额外处理数据库异常
@Controller     // Web 控制器——处理 HTTP 请求
@RestController // = @Controller + @ResponseBody，返回 JSON
```

它们的继承关系：

```text
@Component    (基础)
  ├── @Service        (业务层)
  ├── @Repository     (数据层)
  └── @Controller     (Web 层)
        └── @RestController (JSON Web 层)
```

> 本质上它们都是 `@Component`，只是用不同的名字表达"这个类属于哪一层"。

### 3.2 依赖注入类注解

```java
@Autowired      // 自动注入依赖（"Spring，帮我找到这个接口的实现类并赋值"）
@Qualifier      // 同一接口有多个实现时，指定注入哪个
@Value          // 注入配置文件中的值，如 @Value("${server.port}")
```

### 3.3 Web 路由类注解

```java
@RequestMapping("/api/users")    // 基础路径
@GetMapping                      // GET 请求
@PostMapping                     // POST 请求
@PutMapping                      // PUT 请求
@DeleteMapping                   // DELETE 请求
@PathVariable                    // 路径参数：/users/{id}
@RequestParam                    // 查询参数：/users?name=jack
@RequestBody                     // 请求体 JSON
```

> **前端类比**：Express 的 `router.get('/users/:id', handler)` → Spring 的 `@GetMapping("/{id}")`。

### 3.4 数据类注解

```java
@Document       // MongoDB 文档实体（类比：Mongoose 的 Schema）
@TableName      // MyBatis-Plus 表名映射
@Id             // 主键字段
@Field          // MongoDB 字段映射
```

## 4. 注解是怎么工作的？

你不需要深入了解实现原理，但理解流程能帮你消除"魔法感"：

```text
1. 你在类上标注 @Service
       ↓
2. Spring 启动时扫描所有类文件
       ↓
3. 发现某个类有 @Service 注解
       ↓
4. Spring 自动创建该类的实例（new），放入"容器"
       ↓
5. 其他类通过 @Autowired 声明需要它
       ↓
6. Spring 从容器中找到实例，自动赋值（注入）
```

> **前端类比**：Vue 的 `app.provide()` / `inject()`。Spring 的容器就是一个超大的全局 provide，所有被注解标记的类都自动注册进去。

## 5. 自定义注解（了解即可）

真实项目中你可能会遇到团队自定义的注解：

```java
@Target(ElementType.METHOD)         // 这个注解只能标在方法上
@Retention(RetentionPolicy.RUNTIME) // 运行时可读取
public @interface RateLimit {
    int value() default 100;        // 每秒最多 100 次
}
```

使用：
```java
@RateLimit(50)
@GetMapping("/search")
public List<User> search() { ... }
```

框架会在运行时通过**反射 (Reflection)** 读取 `@RateLimit` 的值，然后在调用 `search()` 之前做限流检查。

> 你目前不需要会写自定义注解，但需要知道这种模式：**注解是标记，框架负责读取和执行**。

## 6. 注解 vs TypeScript 装饰器

| 特性 | Java 注解 | TS 装饰器 |
| --- | --- | --- |
| 语法 | `@Xxx` | `@xxx` |
| 本质 | 元数据标签 | 函数调用 |
| 能不能改变目标行为 | 注解本身不能，框架读取后可以 | 装饰器函数可以直接修改 |
| 运行时可读 | ✅ (RUNTIME 级别) | ✅ (取决于实现) |
| 在 Spring 中的作用 | 配置替代品（替代 XML） | — |
| 在 NestJS 中的用法 | — | 和 Spring 注解非常像 |

如果你用过 **NestJS**，会发现它的 `@Controller()`、`@Get()`、`@Injectable()` 就是从 Spring 搬过来的思路。

## 总结

1. **注解是标签**——贴在类/方法/字段上，给编译器或框架看。
2. **内置注解**处理编译检查：`@Override`、`@Deprecated`。
3. **Spring 注解**替代 XML 配置：`@Service` 注册组件、`@Autowired` 注入依赖、`@GetMapping` 声明路由。
4. **工作流**：你贴标签 → Spring 启动时扫描 → 自动创建实例 → 自动注入到需要的地方。
5. 如果你用过 NestJS，Spring 的注解体系几乎就是同一套思路。

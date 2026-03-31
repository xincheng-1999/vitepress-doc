# 06a. Lombok —— 偷懒神器

> 目标：理解 Lombok 的常用注解，看懂项目代码里"只有字段没有 getter/setter"的写法。

在 07 的 `User.java` 里，我们手写了 getter、setter、构造函数。一个实体类如果有 10 个字段，就要写 20 个 get/set 方法——枯燥、重复、占地方。

**Lombok** 的作用就是：**用注解自动生成这些模板代码。** 你只管写字段，编译时 Lombok 帮你补全其余的。

## 1. Lombok 是什么？

Lombok 是一个 Java 编译期**代码生成器**。你在类上加注解，编译时它自动往 `.class` 文件里插入 getter/setter/toString/构造函数等方法。

> **前端类比**：类似 Babel 插件或 SWC 转换——你写简化的语法，编译器帮你展开成完整代码。

### 没有 Lombok

```java
public class User {
    private Integer id;
    private String name;
    private String email;

    public User() {}

    public User(Integer id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    @Override
    public String toString() {
        return "User{id=" + id + ", name=" + name + ", email=" + email + "}";
    }
}
```

30 多行模板代码。

### 有 Lombok

```java
import lombok.Data;

@Data
public class User {
    private Integer id;
    private String name;
    private String email;
}
```

**3 行搞定**，编译后效果完全一样。

## 2. 安装 Lombok

### 2.1 添加 Maven 依赖

在 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

> Spring Initializr 创建项目时勾选 Lombok 即可自动添加。

### 2.2 IDE 支持

- **IntelliJ IDEA**：2020+ 版本内置 Lombok 支持，无需额外安装。
- **VS Code**：安装 "Extension Pack for Java" 后自带支持。

如果 IDE 不认识 Lombok 注解（报红），检查是否启用了 annotation processing。

## 3. 常用注解详解

### 3.1 @Getter / @Setter

生成 getter 和 setter 方法。

```java
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class User {
    private Integer id;
    private String name;
}

// 编译后等价于手写了 getId(), setId(), getName(), setName()
```

也可以标在字段上（只对单个字段生效）：

```java
public class User {
    @Getter private Integer id;
    @Getter @Setter private String name; // 只有 name 有 setter
}
```

### 3.2 @Data（最常用）

**一个注解包含五个功能**：

```java
@Data  // = @Getter + @Setter + @ToString + @EqualsAndHashCode + @RequiredArgsConstructor
public class User {
    private Integer id;
    private String name;
    private String email;
}
```

> **在 Spring 项目中，DTO / VO / Entity 几乎都用 `@Data`。**  
> 这就是为什么你在项目里看到实体类只有字段声明，没有任何方法——不是漏写了，是 Lombok 帮你生成了。

### 3.3 @NoArgsConstructor / @AllArgsConstructor

```java
@NoArgsConstructor   // 生成无参构造：public User() {}
@AllArgsConstructor  // 生成全参构造：public User(Integer id, String name, String email) {}
public class User {
    private Integer id;
    private String name;
    private String email;
}
```

> Spring、Jackson（JSON 序列化）都需要无参构造函数。实体类通常两个都加。

### 3.4 @Builder（构建者模式）

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Integer id;
    private String name;
    private String email;
}
```

使用：

```java
// 链式创建对象——参数多的时候特别清晰
User user = User.builder()
    .id(1)
    .name("Jack")
    .email("jack@example.com")
    .build();
```

> **前端类比**：类似 JS 的对象字面量 `{ id: 1, name: "Jack" }`。Java 没有对象字面量语法，Builder 是最接近的替代方案。

### 3.5 @Slf4j（日志）

```java
import lombok.extern.slf4j.Slf4j;

@Slf4j   // 自动生成：private static final Logger log = LoggerFactory.getLogger(UserService.class);
@Service
public class UserServiceImpl implements UserService {

    @Override
    public User findById(Integer id) {
        log.info("查询用户: {}", id);
        // ...
    }
}
```

> **前端类比**：你不用自己写 `const logger = createLogger('UserService')`，加个 `@Slf4j` 就有 `log` 变量了。

在真实项目中，几乎每个 Service 类都有 `@Slf4j`——`log.info()`、`log.error()` 是最常见的日志输出方式。

### 3.6 @ToString / @EqualsAndHashCode

```java
@ToString
public class User {
    private Integer id;
    private String name;
}
// System.out.println(user); → "User(id=1, name=Jack)"

@ToString(exclude = "password")  // 排除敏感字段
public class User {
    private Integer id;
    private String name;
    private String password;
}
// "User(id=1, name=Jack)"，不会泄露密码
```

## 4. 常见组合

| 场景 | 推荐注解组合 |
| --- | --- |
| **DTO / VO**（数据传输对象） | `@Data` |
| **Entity**（数据库实体） | `@Data` + `@NoArgsConstructor` + `@AllArgsConstructor` |
| **需要 Builder** | `@Data` + `@Builder` + `@NoArgsConstructor` + `@AllArgsConstructor` |
| **Service / Component** | `@Slf4j` |
| **不可变对象** | `@Getter` + `@AllArgsConstructor`（不加 @Setter） |

## 5. 注意事项

### @Data 的坑

`@Data` 会生成 `equals()` 和 `hashCode()`，默认用**所有字段**计算。如果你的实体类有互相引用的关系（如 A 引用 B，B 引用 A），可能导致死循环。

解决方案：

```java
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // 只用标记的字段
public class User {
    @EqualsAndHashCode.Include
    private Integer id;  // 只用 id 做判等
    private String name;
}
```

### Lombok 代码在 IDE 中看不到

如果你按 Ctrl+Click 跳转到 `getId()`，可能看不到方法体——因为它是编译期生成的。这是正常的。

### 和 Jackson/Spring 的兼容性

`@Data` 生成的无参构造 + getter/setter 完美兼容 JSON 序列化。但如果你只用 `@Builder` 不加 `@NoArgsConstructor`，反序列化可能失败。养成习惯：**用 @Builder 时一定要加 @NoArgsConstructor + @AllArgsConstructor**。

## 总结

1. **Lombok = 编译期代码生成器。** 用注解替代手写的模板代码。
2. **@Data** 最常用：一个注解生成 getter/setter/toString/equals/hashCode。
3. **@Slf4j** 自动给类注入 `log` 变量，用于日志输出。
4. **@Builder** 提供链式构造对象的能力。
5. 看到实体类只有字段没有方法，不是漏写了，是 Lombok 在背后干活。
6. 项目里几乎每个实体类都用 Lombok——这是 Java 开发的标准实践。

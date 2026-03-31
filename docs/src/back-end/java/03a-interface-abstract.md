# 03a. 接口与抽象类

> 目标：理解 interface、implements、abstract class，搞懂 Spring 项目里"面向接口编程"到底在干什么。

如果你只学了前面的 `class` 和 `extends`，去读真实的 Spring 项目代码会立刻卡住——你会看到满屏的 `interface`、`implements`，以及"为什么 Service 层要分 `UserService`（接口）和 `UserServiceImpl`（实现类）两个文件？"

这一章就来解决这个疑问。

## 1. 接口 (interface)

### 1.1 是什么？

接口就是一份"合同"——它只定义了"你必须有哪些方法"，但**不写具体实现**。

> **前端类比**：TypeScript 的 `interface`。

**TypeScript**
```typescript
// 只定义了"形状"，不写实现
interface UserService {
  getById(id: number): User;
  deleteById(id: number): void;
}
```

**Java**
```java
// 接口：用 interface 关键字，方法没有方法体
public interface UserService {
    User getById(Integer id);
    void deleteById(Integer id);
}
```

几乎一模一样。接口里的方法**只有签名，没有 `{}`**。

### 1.2 怎么用？（implements）

在 JS/TS 中：
```typescript
class UserServiceImpl implements UserService {
  getById(id: number): User { /* 具体逻辑 */ }
  deleteById(id: number): void { /* 具体逻辑 */ }
}
```

Java 也是 `implements`：
```java
public class UserServiceImpl implements UserService {

    @Override
    public User getById(Integer id) {
        // 具体逻辑：查数据库、拼数据...
        return new User(id, "Jack");
    }

    @Override
    public void deleteById(Integer id) {
        // 具体逻辑：删数据库记录...
        System.out.println("删除用户: " + id);
    }
}
```

> `@Override` 注解告诉编译器："我在实现接口中声明的方法，帮我检查是否写对了。"

### 1.3 关键规则

| 规则 | 说明 |
| --- | --- |
| 实现类**必须实现接口中所有方法** | 少写一个，编译报错 |
| 一个类可以 `implements` **多个接口** | `class A implements B, C, D` |
| 接口可以 `extends` 另一个接口 | `interface B extends A` |
| 接口中的方法默认是 `public abstract` | 不用手动加 |
| 接口中可以有常量 | `public static final` 自动加上 |

### 1.4 和继承 (extends) 的区别

Java **只能单继承**（一个类只能 extends 一个父类），但**可以实现多个接口**。

```java
// ❌ 报错：Java 不能继承多个类
public class Dog extends Animal, Pet { }

// ✅ 可以实现多个接口
public class Dog extends Animal implements Pet, Trainable { }
```

> **前端类比**：JS 也是单继承，但你可以用 Mixin 模式混入多个功能。Java 的接口就是官方的 Mixin 方案。

## 2. 抽象类 (abstract class)

抽象类介于"普通类"和"接口"之间——它**可以有实现好的方法，也可以有未实现的抽象方法**。

```java
public abstract class BaseService {

    // 已实现的通用方法——子类直接继承就能用
    public void log(String msg) {
        System.out.println("[LOG] " + msg);
    }

    // 抽象方法——子类必须实现
    public abstract void execute();
}
```

```java
public class EmailService extends BaseService {

    @Override
    public void execute() {
        log("发送邮件...");
        // 具体发送逻辑
    }
}
```

### 接口 vs 抽象类速查表

| 特性 | interface | abstract class |
| --- | --- | --- |
| 能否有实现好的方法 | Java 8+ 支持 `default` 方法 | ✅ 可以 |
| 能否有成员变量 | 只能有 `static final` 常量 | ✅ 任意变量 |
| 能否被多重实现/继承 | ✅ 类可以 implements 多个 | ❌ 只能 extends 一个 |
| 构造方法 | ❌ 没有 | ✅ 可以有 |
| **使用场景** | 定义能力合同（"你能做什么"） | 定义通用基础类（"你是什么"） |

> **经验法则**：优先用接口，只有在需要"共享一部分实现代码"时才用抽象类。

## 3. 为什么 Spring 项目里到处都是接口？

这是前端开发者最大的困惑：明明只有一个实现类，为什么还要多写一个接口？

```text
UserService.java          ← 接口（合同）
UserServiceImpl.java      ← 唯一的实现类
```

原因有三个：

### 3.1 Spring 的依赖注入需要接口

Spring 容器管理 Bean 的时候，推荐按接口注入。这样 Spring 可以在运行时灵活地替换实现：

```java
// Controller 依赖接口，不依赖具体实现
@RestController
public class UserController {

    @Autowired
    private UserService userService; // ← 这里是接口类型

    // Spring 会自动找到 UserServiceImpl 并注入
}
```

> **前端类比**：Vue 的 `provide/inject`、React 的 Context。你 provide 一个接口形状的对象，consumer 不关心具体是谁实现的。

### 3.2 方便写单元测试

测试 Controller 时，可以用一个 `MockUserService` 代替真实的 `UserServiceImpl`，因为它们都实现了同一个接口。

> **前端类比**：Jest 中 mock 一个模块——`jest.mock('./userService')`。

### 3.3 解耦——分层架构的基础

```text
Controller  →  依赖 UserService 接口  →  UserServiceImpl 连数据库
                                       ↑
                                   将来可以换成 UserServiceV2Impl
                                   不用改 Controller 一行代码
```

## 4. 接口的 default 方法（Java 8+）

Java 8 起，接口可以有带实现的方法（用 `default` 关键字）：

```java
public interface Greeting {

    // 抽象方法——实现类必须实现
    String getName();

    // default 方法——有默认实现，实现类可以不重写
    default String sayHi() {
        return "Hi, I am " + getName();
    }
}
```

```java
public class User implements Greeting {

    @Override
    public String getName() {
        return "Jack";
    }

    // sayHi() 不用写，直接用 default 的就行
}
```

> **前端类比**：TS 接口不支持默认实现，但 abstract class 可以。Java 的 `default` 方法让接口也能提供默认实现。

## 5. 实战：模拟 Spring 三层架构

把 07 的 CRUD 用接口 + 实现类重构一下，感受"面向接口编程"：

```java
// ========== 1. 接口 ==========
public interface UserService {
    List<User> findAll();
    User findById(Integer id);
    void save(User user);
    void deleteById(Integer id);
}

// ========== 2. 实现类 ==========
public class UserServiceImpl implements UserService {
    private final List<User> users = new ArrayList<>();

    public UserServiceImpl() {
        users.add(new User(1, "Jack"));
        users.add(new User(2, "Rose"));
    }

    @Override
    public List<User> findAll() {
        return users;
    }

    @Override
    public User findById(Integer id) {
        return users.stream()
                .filter(u -> u.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void save(User user) {
        users.add(user);
    }

    @Override
    public void deleteById(Integer id) {
        users.removeIf(u -> u.getId().equals(id));
    }
}

// ========== 3. Controller 依赖接口 ==========
public class UserController {
    private final UserService userService; // ← 接口类型

    public UserController(UserService userService) {
        this.userService = userService; // 传入的是具体实现
    }

    public void showAll() {
        userService.findAll().forEach(u ->
            System.out.println(u.getId() + ": " + u.getName())
        );
    }
}
```

## 总结

1. **接口 (interface)** = 合同 / TypeScript 的 interface。只声明方法签名，不写实现。
2. **抽象类 (abstract class)** = 半成品类。可以有实现也可以有未实现方法。
3. **面向接口编程**是 Spring 的核心思想——Controller 依赖接口，不依赖具体类。
4. 真实项目里 `Service 接口 + ServiceImpl 实现类` 是标配，别觉得多余。
5. 接口支持多实现，Java 只能单继承——这是接口存在的根本原因。

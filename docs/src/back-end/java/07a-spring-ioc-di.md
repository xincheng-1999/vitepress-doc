# 07a. Spring IoC 与依赖注入

> 目标：理解 Spring 的核心思想——IoC 容器和依赖注入 (DI)。把 [07 的 CRUD](/back-end/java/07-spring-boot-crud) 重构为标准三层架构。

在 [07 的 CRUD 示例](/back-end/java/07-spring-boot-crud)中，Controller 既处理路由，又直接操作数据。这在真实项目中是绝对不允许的。
真实的 Spring Boot 项目**必须分层**，而让这些层能自动连接起来的机制就是 **IoC / DI**。

## 1. 什么是 IoC？

**IoC (Inversion of Control，控制反转)** 的意思是：**你不再自己 new 对象，而是把这件事交给 Spring 来管。**

### 不用 IoC（传统方式）

```java
// 你自己 new——"我需要什么，我自己造"
public class UserController {
    private UserService userService = new UserServiceImpl(); // 手动创建
}
```

问题：Controller 直接依赖了 `UserServiceImpl`（具体类），想换实现就得改代码。

### 用 IoC（Spring 方式）

```java
// Spring 帮你 new 并注入——"我需要什么，Spring 给我"
@RestController
public class UserController {

    @Autowired
    private UserService userService; // Spring 自动找到实现类并赋值
}
```

你只声明"我需要一个 `UserService`"，至于用什么实现、什么时候创建，都由 Spring 决定。

> **前端类比**：  
> - Vue 3 的 `provide` / `inject`  
> - React 的 Context + `useContext`  
> - Angular 的依赖注入系统  
> 
> 你在组件里写 `inject('userService')`，不关心是谁 provide 的，也不关心它是怎么创建的。Spring 就是这个思路的服务端版本。

## 2. 什么是 Bean？

**Bean = Spring 容器里管理的对象实例。**

当你在类上加 `@Component`、`@Service`、`@Repository`、`@Controller` 等注解后，Spring 启动时会：

1. **扫描**所有类文件
2. **发现注解**标记的类
3. **创建实例** (new)
4. **放入容器**（一个大的 Map）

这个被创建并放入容器的对象就叫 **Bean**。

> **前端类比**：你可以把 Spring 容器想象成一个全局的 `Map<类名, 实例>`，就像一个超大的全局 store。

```text
Spring 容器 (IoC Container)
┌─────────────────────────────────────┐
│  "userService"    → UserServiceImpl │
│  "userController" → UserController  │
│  "orderService"   → OrderServiceImpl│
│  ...                                │
└─────────────────────────────────────┘
```

## 3. 依赖注入 (DI) 的三种方式

### 3.1 字段注入（最简单，但不推荐）

```java
@RestController
public class UserController {
    @Autowired
    private UserService userService; // Spring 直接给字段赋值
}
```

为什么不推荐？字段是 private 的，违反了面向对象原则，而且单元测试时不好 mock。

### 3.2 构造函数注入（推荐 ✅）

```java
@RestController
public class UserController {

    private final UserService userService;

    // Spring 看到构造函数的参数类型是 UserService，
    // 会自动从容器里找到对应的 Bean 传进来
    public UserController(UserService userService) {
        this.userService = userService;
    }
}
```

> 如果只有一个构造函数，Spring Boot 会**自动注入**，连 `@Autowired` 都不用写。

**前端类比**：React 的 props 注入——组件声明需要什么，父组件（Spring 容器）负责传入。

### 3.3 Setter 注入（偶尔用）

```java
@RestController
public class UserController {

    private UserService userService;

    @Autowired
    public void setUserService(UserService userService) {
        this.userService = userService;
    }
}
```

### 怎么选？

| 方式 | 适合场景 | 说明 |
| --- | --- | --- |
| **构造函数注入** | 必须的依赖 | ✅ 官方推荐，字段可以 `final` |
| 字段注入 | 快速原型 | 简洁但不利于测试 |
| Setter 注入 | 可选依赖 | 较少使用 |

## 4. 实战：三层架构重构 CRUD

把 07 的"Controller 里揉在一起的代码"拆成标准的三层。

### 4.1 项目结构

```text
com.example.demo/
├── DemoApplication.java          ← 启动入口
├── entity/
│   └── User.java                 ← 实体类
├── controller/
│   └── UserController.java       ← Web 层：处理 HTTP 请求
├── service/
│   ├── UserService.java          ← 业务接口
│   └── impl/
│       └── UserServiceImpl.java  ← 业务实现
└── repository/
    └── UserRepository.java       ← 数据层（暂用内存模拟）
```

> **前端类比**：这就是"关注点分离"——pages / hooks / api / store 各管各的。

### 4.2 实体类（不变）

```java
package com.example.demo.entity;

public class User {
    private Integer id;
    private String name;

    public User() {}
    public User(Integer id, String name) {
        this.id = id;
        this.name = name;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
```

### 4.3 Repository 层（数据访问）

```java
package com.example.demo.repository;

import com.example.demo.entity.User;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository  // ← 告诉 Spring：这是数据层，请管理它
public class UserRepository {

    private final List<User> store = new ArrayList<>();

    public UserRepository() {
        store.add(new User(1, "Jack"));
        store.add(new User(2, "Rose"));
    }

    public List<User> findAll() {
        return store;
    }

    public Optional<User> findById(Integer id) {
        return store.stream()
                .filter(u -> u.getId().equals(id))
                .findFirst();
    }

    public void save(User user) {
        store.add(user);
    }

    public void deleteById(Integer id) {
        store.removeIf(u -> u.getId().equals(id));
    }
}
```

> `Optional<User>` 是 Java 表达"可能有值也可能没值"的方式，类似 TS 的 `User | undefined`。

### 4.4 Service 层（业务逻辑）

**接口**：
```java
package com.example.demo.service;

import com.example.demo.entity.User;
import java.util.List;

public interface UserService {
    List<User> findAll();
    User findById(Integer id);
    void create(User user);
    void delete(Integer id);
}
```

**实现类**：
```java
package com.example.demo.service.impl;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.UserService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service  // ← 告诉 Spring：这是业务层 Bean
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    // 构造函数注入 Repository
    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Override
    public User findById(Integer id) {
        return userRepository.findById(id)
                .orElse(null); // 找不到返回 null
    }

    @Override
    public void create(User user) {
        userRepository.save(user);
    }

    @Override
    public void delete(Integer id) {
        userRepository.deleteById(id);
    }
}
```

### 4.5 Controller 层（Web 入口）

```java
package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.service.UserService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    // 构造函数注入 Service
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<User> getAll() {
        return userService.findAll();
    }

    @GetMapping("/{id}")
    public User getById(@PathVariable Integer id) {
        return userService.findById(id);
    }

    @PostMapping
    public String create(@RequestBody User user) {
        userService.create(user);
        return "Created";
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable Integer id) {
        userService.delete(id);
        return "Deleted";
    }
}
```

### 4.6 注入链路全景

```text
Spring 启动时的自动装配过程：

1. 扫描到 @Repository → new UserRepository() → 放入容器
2. 扫描到 @Service   → new UserServiceImpl(userRepository) → 放入容器
3. 扫描到 @RestController → new UserController(userService) → 放入容器

请求到达时：
  HTTP GET /users
       ↓
  UserController.getAll()
       ↓ 调用
  UserService.findAll()    (接口)
       ↓ 实际执行
  UserServiceImpl.findAll()
       ↓ 调用
  UserRepository.findAll()
       ↓
  返回数据
```

## 5. 常见问题

### Q: 如果一个接口有多个实现类怎么办？

```java
@Service("emailNotifier")
public class EmailNotifier implements Notifier { ... }

@Service("smsNotifier")
public class SmsNotifier implements Notifier { ... }
```

注入时用 `@Qualifier` 指定：
```java
public NotificationController(@Qualifier("emailNotifier") Notifier notifier) {
    this.notifier = notifier;
}
```

### Q: Bean 是单例的吗？

**默认是。** Spring 容器里同一个类只有一个实例（singleton），所有注入点拿到的都是同一个对象。

> **前端类比**：Vuex / Pinia 的 store 默认也是单例——整个应用共享一个状态对象。

### Q: 一定要写接口吗？

技术上不写也行——`@Autowired` 可以直接注入具体类。但 Spring 生态的惯例是写接口，方便测试和扩展。

## 总结

1. **IoC**：不自己 new，让 Spring 来管理对象的创建和生命周期。
2. **Bean**：被 Spring 管理的对象实例。加了 `@Component` / `@Service` / `@Repository` 的类会自动成为 Bean。
3. **DI**：通过 `@Autowired` 或构造函数，让 Spring 自动把依赖注入进来。推荐构造函数注入。
4. **三层架构**：Controller → Service（接口 + Impl）→ Repository，各管各的，通过 DI 连接。
5. 整个流程就是：**你声明需要什么，Spring 负责给你。**

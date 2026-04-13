---
title: Hibernate / JPA 简介
---

# Hibernate / JPA 简介

上一章我们学了 [MyBatis](/back-end/database/orm/mybatis)——一个"半自动" ORM，SQL 由你写，映射由框架做。
而 **Hibernate** 是一个"全自动" ORM——**连 SQL 都帮你生成**，你只需要操作 Java 对象。

> **前端类比**：
> *   MyBatis ≈ `axios`（你自己拼 URL 和参数）
> *   Hibernate/JPA ≈ `Prisma`（你操作对象，框架拼查询语句）

## 1. JPA 和 Hibernate 的关系

*   **JPA (Java Persistence API)**：是一套**规范/标准**（相当于接口），定义了"Java 怎么操作数据库"。
*   **Hibernate**：是 JPA 规范的**一个实现**（相当于实现类）。

> 就像前端的 ECMAScript 规范和 V8 引擎的关系——JPA 定义规矩，Hibernate 去实现。

在 Spring Boot 项目中，我们通常说 **Spring Data JPA** ——它在 Hibernate 之上又封装了一层，提供了更简洁的 Repository 接口。

```text
你的代码 → Spring Data JPA → Hibernate → JDBC → MySQL
```

## 2. Spring Boot 集成 JPA

### 2.1 添加依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>

<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

### 2.2 配置数据库连接

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/my_learning_db?useSSL=false&serverTimezone=UTC
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update   # 自动根据实体类创建/更新表结构
    show-sql: true        # 打印生成的 SQL（开发时建议开启）
    properties:
      hibernate:
        format_sql: true  # 格式化 SQL 输出
```

> `ddl-auto: update` 的意思是：如果你修改了实体类的字段，Hibernate 会自动帮你修改数据库表结构。

## 3. 实体类 (Entity)

JPA 的实体类和 MongoDB 的 `@Document` 类似，只是注解不同：

```java
package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity                     // ← 标记为 JPA 实体
@Table(name = "users")      // ← 映射到 users 表
public class User {

    @Id                                        // 主键
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // 自增策略
    private Long id;

    @Column(nullable = false, length = 50)     // 对应列属性
    private String username;

    @Column(length = 100)
    private String email;

    private Integer age;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
```

### JPA 注解 vs MongoDB 注解 vs MyBatis

| 功能 | JPA (Hibernate) | MongoDB | MyBatis |
| :--- | :--- | :--- | :--- |
| 标记实体 | `@Entity` | `@Document` | 普通 POJO |
| 映射表/集合 | `@Table(name="xx")` | `@Document(collection="xx")` | `@TableName("xx")` (MP) |
| 主键 | `@Id` + `@GeneratedValue` | `@Id` | `@TableId` (MP) |
| 字段映射 | `@Column(name="xx")` | `@Field("xx")` | 驼峰自动映射 |
| 自动建表 | ✅ `ddl-auto` | ✅ 自动创建集合 | ❌ 需要手动建表 |

## 4. Repository 接口

这里和 [Spring Data MongoDB](/back-end/java/08-spring-data-db) 几乎一模一样——换个父接口就行：

```java
package com.example.demo.repository;

import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    // JpaRepository<实体类型, 主键类型>
    // 继承后自动拥有：findAll, findById, save, deleteById, count...

    // 方法名查询（跟 MongoDB 规则一样！）
    List<User> findByUsername(String username);
    List<User> findByAgeGreaterThan(Integer age);
    List<User> findByUsernameContainingOrderByAgeDesc(String keyword);
    boolean existsByEmail(String email);
}
```

> **重点**：方法名查询规则和 Spring Data MongoDB 完全相同！学一次，两种数据库通用。

### 开箱即用的方法

```java
// 查所有
List<User> users = userRepository.findAll();

// 按 id 查
Optional<User> user = userRepository.findById(1L);

// 保存（新增或更新）
User saved = userRepository.save(user);

// 删除
userRepository.deleteById(1L);

// 计数
long count = userRepository.count();

// 分页查询
Page<User> page = userRepository.findAll(PageRequest.of(0, 10));
```

## 5. 完整三层架构

### Service

```java
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Override
    public User findById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    @Override
    public User create(User user) {
        return userRepository.save(user);
    }

    @Override
    public User update(Long id, User userParams) {
        User existing = userRepository.findById(id).orElse(null);
        if (existing == null) return null;
        existing.setUsername(userParams.getUsername());
        existing.setEmail(userParams.getEmail());
        existing.setAge(userParams.getAge());
        return userRepository.save(existing);
    }

    @Override
    public void delete(Long id) {
        userRepository.deleteById(id);
    }
}
```

### Controller

```java
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<User> getAll() {
        return userService.findAll();
    }

    @GetMapping("/{id}")
    public User getById(@PathVariable Long id) {
        return userService.findById(id);
    }

    @PostMapping
    public User create(@RequestBody User user) {
        return userService.create(user);
    }

    @PutMapping("/{id}")
    public User update(@PathVariable Long id, @RequestBody User user) {
        return userService.update(id, user);
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        userService.delete(id);
        return "Deleted";
    }
}
```

> **注意到了吗？** Service 和 Controller 的代码与 [Java 08 章](/back-end/java/08-spring-data-db) 的 MongoDB 版本几乎一样！只有 Repository 的父接口不同。这就是三层架构和 Spring Data 统一 API 的好处。

## 6. JPA 特有功能

### 6.1 自动建表

配置 `ddl-auto: update` 后，启动项目时 Hibernate 会自动根据实体类创建表。控制台会打印生成的 SQL：

```sql
Hibernate: create table users (
    id bigint not null auto_increment,
    age integer,
    created_at datetime(6),
    email varchar(100),
    username varchar(50) not null,
    primary key (id)
) engine=InnoDB
```

> **注意**：生产环境不要用 `ddl-auto: update`，应该用 `none` 或 `validate`，防止误改表结构。

### 6.2 关联关系

JPA 可以用注解定义表之间的关联，这是 MyBatis 不擅长的场景：

```java
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String product;
    private Double price;

    // 多对一关系：多个订单属于一个用户
    @ManyToOne
    @JoinColumn(name = "user_id")  // 外键列名
    private User user;
}
```

查询时 JPA 会自动执行 JOIN：

```java
Order order = orderRepository.findById(1L).orElse(null);
System.out.println(order.getUser().getUsername());  // 自动关联查询！
```

> 不需要手写 JOIN SQL，Hibernate 帮你处理了。

### 6.3 JPQL 自定义查询

当方法名查询无法满足需求时，可以用 `@Query` 写 JPQL (Java Persistence Query Language)：

```java
public interface UserRepository extends JpaRepository<User, Long> {

    // JPQL：面向对象的查询语言（操作的是实体类，不是表名）
    @Query("SELECT u FROM User u WHERE u.age BETWEEN :min AND :max")
    List<User> findByAgeBetween(@Param("min") Integer min, @Param("max") Integer max);

    // 原生 SQL（也可以用）
    @Query(value = "SELECT * FROM users WHERE username LIKE %:keyword%", nativeQuery = true)
    List<User> searchByUsername(@Param("keyword") String keyword);
}
```

## 7. MyBatis vs Hibernate/JPA 怎么选？

| 对比项 | MyBatis (+ MyBatis-Plus) | Hibernate / JPA |
| :--- | :--- | :--- |
| SQL 控制 | ✅ 完全手写，精准优化 | ⚠️ 自动生成，复杂场景可能低效 |
| 学习成本 | 低（会 SQL 就会用） | 中（要理解 JPA 规范和缓存机制） |
| 开发效率 | 中（简单 CRUD 要写 XML/注解） | 高（继承接口即拥有 CRUD） |
| 复杂查询 | ✅ XML 动态 SQL 极其强大 | ⚠️ 需要用 JPQL 或 Criteria API |
| 关联关系 | 手动写 JOIN | ✅ 注解自动关联 |
| 自动建表 | ❌ | ✅ `ddl-auto` |
| 国内使用率 | 🔥 极高（互联网公司首选） | 中等（外企、金融）|

### 给前端转后端的建议

1. **如果你跟着本教程学**：先用 **MyBatis-Plus**，上手最快，国内资料最多。
2. **如果接手的项目用 JPA**：也不用怕，Service/Controller 层代码几乎不变，只是 Repository 的用法不同。
3. **两者核心思想是相通的**：实体类 → Repository/Mapper → Service → Controller，三层架构永远不变。

## 总结

*   **JPA** 是规范，**Hibernate** 是实现，**Spring Data JPA** 是 Spring 的封装。
*   JPA 的 Repository 接口和 Spring Data MongoDB 完全一致——学一套 API，通吃关系型和非关系型数据库。
*   JPA 更擅长**快速开发**（自动建表、自动关联），MyBatis 更擅长**SQL 调优**和**复杂查询**。
*   实际项目中，国内以 MyBatis/MyBatis-Plus 为主流。

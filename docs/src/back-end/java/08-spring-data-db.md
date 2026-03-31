# 08. Spring Data 数据库实战

> 目标：把 [07a 的内存 CRUD](/back-end/java/07a-spring-ioc-di) 接上真实数据库（MongoDB），体验 Spring Data 的"声明式"数据访问。

前面几章我们一直用 `List` 模拟数据库。现在是时候换成真的了。

Spring Data 的理念非常简单：**你只写接口声明，不写实现，Spring 自动帮你生成 SQL / 查询。**

> **前端类比**：就像 Prisma 的 `prisma.user.findMany()`——你声明查什么，ORM 帮你拼查询语句。

## 1. 为什么选 MongoDB？

在 [frontend-backend-guide](/back-end/frontend-backend-guide/01-项目整体架构) 的微服务项目中，MongoDB 是主库。所以我们先学 Spring Data MongoDB。如果你接触的是 MySQL 项目，原理一模一样，只是注解和依赖不同（后面会对比）。

## 2. 环境准备

### 2.1 安装 MongoDB

- **Windows**：[MongoDB 安装指南](/back-end/database/mongodb/intro)
- **Mac**：`brew install mongodb-community` 然后 `brew services start mongodb-community`
- **Docker**（推荐）：`docker run -d -p 27017:27017 --name mongo mongo:7`

确认运行：
```bash
# 能连上说明没问题
mongosh
```

### 2.2 添加依赖

在 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-mongodb</artifactId>
</dependency>
```

> Spring Initializr 里勾选 **Spring Data MongoDB** 即可。

### 2.3 配置连接

在 `application.yml` 中添加：

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/demo
```

这就够了。Spring Boot 会自动配置 MongoDB 连接。

## 3. 实体类 (Document)

MongoDB 里的一条数据叫 **Document**（文档），对应 Java 里的一个实体类。

```java
package com.example.demo.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")  // ← 对应 MongoDB 中的 users 集合
public class User {

    @Id                         // ← 主键，MongoDB 会自动生成 _id
    private String id;           // 注意：MongoDB 的主键是字符串（ObjectId），不再是前面章节用的 Integer

    private String name;
    private String email;
    private Integer age;
}
```

> **前端类比**：Mongoose 的 Schema 定义。
> ```javascript
> const userSchema = new Schema({
>   name: String,
>   email: String,
>   age: Number,
> });
> ```

### MongoDB 注解 vs MySQL 注解

| MongoDB | MySQL (JPA) | 说明 |
| --- | --- | --- |
| `@Document(collection = "xxx")` | `@Entity` + `@Table(name = "xxx")` | 映射到哪个表/集合 |
| `@Id` | `@Id` + `@GeneratedValue` | 主键 |
| `@Field("real_name")` | `@Column(name = "real_name")` | 字段名映射 |
| 自动处理嵌套对象 | 需要 `@OneToMany` 等关系注解 | MongoDB 天然支持嵌套 |

## 4. Repository（核心！）

这是 Spring Data 最神奇的地方——**只写接口，不写实现。**

```java
package com.example.demo.repository;

import com.example.demo.entity.User;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<User, String> {
    // 就这一行。增删改查全有了。
}
```

`MongoRepository<User, String>` 的两个泛型参数：
- `User`：实体类类型
- `String`：主键类型（MongoDB 的 ObjectId 映射为 String）

### 4.1 继承来的方法（开箱即用）

不用写任何代码，你就拥有了这些方法：

```java
// 查所有
List<User> users = userRepository.findAll();

// 按 id 查
Optional<User> user = userRepository.findById("abc123");

// 保存（新增 or 更新）
User saved = userRepository.save(user);

// 按 id 删除
userRepository.deleteById("abc123");

// 计数
long count = userRepository.count();

// 判断是否存在
boolean exists = userRepository.existsById("abc123");
```

> **前端类比**：
> ```javascript
> // Prisma
> await prisma.user.findMany()
> await prisma.user.findUnique({ where: { id } })
> await prisma.user.create({ data: user })
> await prisma.user.delete({ where: { id } })
> ```

### 4.2 方法名查询（声明式查询）

Spring Data 的魔法：**按命名规则写方法名，自动生成查询逻辑。**

```java
public interface UserRepository extends MongoRepository<User, String> {

    // 按 name 查找——Spring 自动生成：db.users.find({ name: xxx })
    List<User> findByName(String name);

    // 按 email 查找
    Optional<User> findByEmail(String email);

    // 按 age 大于某值查找
    List<User> findByAgeGreaterThan(Integer age);

    // 按 name 模糊查 + 按 age 降序
    List<User> findByNameContainingOrderByAgeDesc(String keyword);

    // 按 name 和 email 查
    Optional<User> findByNameAndEmail(String name, String email);

    // 判断是否存在
    boolean existsByEmail(String email);

    // 删除
    void deleteByName(String name);
}
```

方法名规则速查：

| 关键词 | 含义 | 示例 |
| --- | --- | --- |
| `findBy` | 查询 | `findByName(name)` |
| `And` / `Or` | 组合条件 | `findByNameAndAge(name, age)` |
| `GreaterThan` | `>` | `findByAgeGreaterThan(18)` |
| `LessThan` | `<` | `findByAgeLessThan(60)` |
| `Between` | 范围 | `findByAgeBetween(18, 60)` |
| `Containing` | 模糊匹配（like） | `findByNameContaining("jack")` |
| `OrderBy...Desc` | 排序 | `findByNameOrderByAgeDesc(name)` |
| `existsBy` | 判断存在 | `existsByEmail(email)` |
| `countBy` | 计数 | `countByAge(age)` |

> **你不需要写任何查询语句。** 方法名就是查询——Spring Data 在启动时解析方法名，自动生成查询逻辑。

## 5. 完整三层架构实战

把 07a 的内存版改成 MongoDB 版。

### 5.1 项目结构

```text
com.example.demo/
├── DemoApplication.java
├── entity/
│   └── User.java              ← @Document
├── repository/
│   └── UserRepository.java    ← extends MongoRepository（只有接口！）
├── service/
│   ├── UserService.java       ← 业务接口
│   └── impl/
│       └── UserServiceImpl.java
└── controller/
    └── UserController.java
```

### 5.2 Repository

```java
package com.example.demo.repository;

import com.example.demo.entity.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface UserRepository extends MongoRepository<User, String> {
    List<User> findByName(String name);
    List<User> findByAgeGreaterThan(Integer age);
}
```

### 5.3 Service

```java
package com.example.demo.service.impl;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

@Slf4j
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
    public User findById(String id) {
        return userRepository.findById(id).orElse(null);
    }

    @Override
    public User create(User user) {
        log.info("创建用户: {}", user.getName());
        return userRepository.save(user);
    }

    @Override
    public void delete(String id) {
        log.info("删除用户: {}", id);
        userRepository.deleteById(id);
    }

    @Override
    public User update(String id, User userParams) {
        User existing = userRepository.findById(id).orElse(null);
        if (existing == null) return null;

        existing.setName(userParams.getName());
        existing.setEmail(userParams.getEmail());
        existing.setAge(userParams.getAge());
        return userRepository.save(existing); // save 会更新已有文档
    }
}
```

### 5.4 Controller

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

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<User> getAll() {
        return userService.findAll();
    }

    @GetMapping("/{id}")
    public User getById(@PathVariable String id) {
        return userService.findById(id);
    }

    @PostMapping
    public User create(@RequestBody User user) {
        return userService.create(user);
    }

    @PutMapping("/{id}")
    public User update(@PathVariable String id, @RequestBody User user) {
        return userService.update(id, user);
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable String id) {
        userService.delete(id);
        return "Deleted";
    }
}
```

### 5.5 测试

启动项目后：

```bash
# 创建用户
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Jack","email":"jack@example.com","age":25}'

# 响应：{"id":"665f...","name":"Jack","email":"jack@example.com","age":25}
# MongoDB 自动生成了 id！

# 查询列表
curl http://localhost:8080/users

# 按 id 查询
curl http://localhost:8080/users/665f...

# 更新
curl -X PUT http://localhost:8080/users/665f... \
  -H "Content-Type: application/json" \
  -d '{"name":"Jack Updated","email":"new@example.com","age":26}'

# 删除
curl -X DELETE http://localhost:8080/users/665f...
```

## 6. 改动了什么？

和 07a 的内存版对比，实际上只改了两处：

| 改动 | 内存版 (07a) | MongoDB 版 (本章) |
| --- | --- | --- |
| 实体类 | 普通 Java 类 | 加了 `@Document`、`@Id` |
| Repository | 自己写的类（List 存数据） | **只有一个接口**，extends MongoRepository |
| Service / Controller | 几乎不变 | 几乎不变 |
| 数据持久化 | 重启就没了 | 永久保存在 MongoDB 中 |

> **这就是分层架构的好处**——换数据源只改最底层，上层代码不受影响。

## 7. 如果用 MySQL 呢？

原理完全一样，只是换一组注解和依赖：

```xml
<!-- pom.xml：换成 JPA + MySQL -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
</dependency>
```

```java
// 实体类：@Document → @Entity
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    // ...
}

// Repository：MongoRepository → JpaRepository
public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findByName(String name);
}
```

Service 和 Controller **完全不用改**。

## 总结

1. **Spring Data = 声明式数据访问。** 写接口 + 方法名，不写实现，Spring 自动生成查询。
2. **MongoRepository** 开箱即带 `findAll`、`findById`、`save`、`deleteById` 等基础方法。
3. **方法名查询**：`findByName`、`findByAgeGreaterThan`——方法名就是查询语句。
4. **从内存到数据库只改两处**：实体类加注解 + Repository 换成 Spring Data 接口。
5. **切换数据库**只需要换依赖和注解，Service / Controller 层不受影响——这就是分层架构的威力。

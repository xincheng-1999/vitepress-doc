---
title: MyBatis 快速上手
---

# MyBatis 快速上手

在 [JDBC 章节](/back-end/database/mysql/java-connection) 中，我们学会了用原生代码连接 MySQL。但你一定发现了——每次查询都要写重复的 `Connection`、`PreparedStatement`、`ResultSet`，还要手动把结果映射成 Java 对象。太累了。

**MyBatis** 就是来解决这个问题的——它帮你**把 SQL 结果自动映射成 Java 对象**，同时保留了对 SQL 的完全控制权。

> **前端类比**：JDBC 就像用原生 `XMLHttpRequest`，而 MyBatis 就像 `axios`——本质一样，但封装掉了繁琐的模板代码。

## 1. 什么是 ORM？

**ORM (Object-Relational Mapping)** = 对象关系映射。简单说就是：

*   数据库的一行数据 ↔ Java 的一个对象
*   数据库的一张表 ↔ Java 的一个类
*   SQL 查询结果 ↔ `List<对象>`

| JDBC (手动) | MyBatis (半自动 ORM) |
| :--- | :--- |
| 手动写 SQL | ✅ 手动写 SQL |
| 手动 `rs.getString("name")` 取值 | ✅ 自动映射到对象 |
| 手动管理 Connection | ✅ 框架管理 |
| 手动关闭资源 | ✅ 框架处理 |

> MyBatis 被称为"半自动 ORM"——SQL 由你写，结果映射由框架做。Hibernate/JPA 是"全自动 ORM"——SQL 也帮你生成。

## 2. Spring Boot 集成 MyBatis

### 2.1 添加依赖

在 `pom.xml` 中：

```xml
<!-- MyBatis Spring Boot Starter -->
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>3.0.3</version>
</dependency>

<!-- MySQL 驱动 -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- Lombok (可选，简化代码) -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

### 2.2 配置数据库连接

在 `application.yml` 中：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/my_learning_db?useSSL=false&serverTimezone=UTC
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis:
  # 实体类包路径（自动映射别名）
  type-aliases-package: com.example.demo.entity
  # Mapper XML 文件位置
  mapper-locations: classpath:mapper/*.xml
  configuration:
    # 开启驼峰命名自动映射（数据库 user_name → Java userName）
    map-underscore-to-camel-case: true
```

### 2.3 创建数据库表

```sql
CREATE DATABASE IF NOT EXISTS my_learning_db;
USE my_learning_db;

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100),
    age INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO users (username, email, age) VALUES
('Jack', 'jack@example.com', 25),
('Alice', 'alice@example.com', 20),
('Bob', 'bob@example.com', 30);
```

## 3. 实战：三层架构

### 3.1 实体类 (Entity)

```java
package com.example.demo.entity;

import lombok.Data;

@Data
public class User {
    private Long id;
    private String username;
    private String email;
    private Integer age;
    private java.time.LocalDateTime createdAt;
}
```

> `@Data` 是 Lombok 注解，自动生成 getter/setter/toString。参考 [Java 06a Lombok](/back-end/java/06a-lombok)。

### 3.2 Mapper 接口 (数据访问层)

MyBatis 用 **Mapper 接口** 替代 JDBC 的 `Statement`。有两种写法：

#### 方式一：注解方式 (简单 SQL 推荐)

```java
package com.example.demo.mapper;

import com.example.demo.entity.User;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper  // ← 告诉 Spring 这是一个 MyBatis Mapper
public interface UserMapper {

    // 查所有
    @Select("SELECT * FROM users")
    List<User> findAll();

    // 按 id 查
    @Select("SELECT * FROM users WHERE id = #{id}")
    User findById(Long id);

    // 新增
    @Insert("INSERT INTO users(username, email, age) VALUES(#{username}, #{email}, #{age})")
    @Options(useGeneratedKeys = true, keyProperty = "id")  // 自动回填生成的 id
    int insert(User user);

    // 更新
    @Update("UPDATE users SET username=#{username}, email=#{email}, age=#{age} WHERE id=#{id}")
    int update(User user);

    // 删除
    @Delete("DELETE FROM users WHERE id = #{id}")
    int deleteById(Long id);

    // 条件查询
    @Select("SELECT * FROM users WHERE age > #{age}")
    List<User> findByAgeGreaterThan(Integer age);
}
```

> **`#{id}`** 就是 MyBatis 的参数占位符，等价于 JDBC 的 `PreparedStatement` 的 `?`，天然防 SQL 注入。

#### 方式二：XML 方式 (复杂 SQL 推荐)

当 SQL 很复杂（多表联查、动态条件）时，写在注解里太难读了。可以写在 XML 文件中。

创建 `src/main/resources/mapper/UserMapper.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
    "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.example.demo.mapper.UserMapper">

    <!-- 查所有 -->
    <select id="findAll" resultType="User">
        SELECT * FROM users
    </select>

    <!-- 按 id 查 -->
    <select id="findById" resultType="User">
        SELECT * FROM users WHERE id = #{id}
    </select>

    <!-- 动态条件查询 (MyBatis 的强项！) -->
    <select id="findByCondition" resultType="User">
        SELECT * FROM users
        <where>
            <if test="username != null and username != ''">
                AND username LIKE CONCAT('%', #{username}, '%')
            </if>
            <if test="age != null">
                AND age >= #{age}
            </if>
        </where>
        ORDER BY created_at DESC
    </select>

    <!-- 新增 -->
    <insert id="insert" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO users(username, email, age)
        VALUES(#{username}, #{email}, #{age})
    </insert>

</mapper>
```

Mapper 接口中声明方法（不需要注解）：

```java
@Mapper
public interface UserMapper {
    List<User> findAll();
    User findById(Long id);
    List<User> findByCondition(@Param("username") String username, @Param("age") Integer age);
    int insert(User user);
}
```

> **动态 SQL** 是 MyBatis 相比其他 ORM 最强的特性。`<if>`, `<where>`, `<foreach>` 等标签让你灵活拼接 SQL，而不用担心 `WHERE` 后面多出一个 `AND`。

### 3.3 Service 层

```java
package com.example.demo.service.impl;

import com.example.demo.entity.User;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.UserService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public List<User> findAll() {
        return userMapper.findAll();
    }

    @Override
    public User findById(Long id) {
        return userMapper.findById(id);
    }

    @Override
    public User create(User user) {
        userMapper.insert(user);
        return user;  // insert 后 id 会自动回填到 user 对象
    }

    @Override
    public User update(Long id, User userParams) {
        userParams.setId(id);
        userMapper.update(userParams);
        return userMapper.findById(id);
    }

    @Override
    public void delete(Long id) {
        userMapper.deleteById(id);
    }
}
```

### 3.4 Controller 层

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

### 3.5 测试

```bash
# 查询所有
curl http://localhost:8080/users

# 新增
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{"username":"Tom","email":"tom@example.com","age":22}'

# 按 id 查
curl http://localhost:8080/users/1

# 更新
curl -X PUT http://localhost:8080/users/1 \
  -H "Content-Type: application/json" \
  -d '{"username":"Jack Updated","email":"jack@example.com","age":26}'

# 删除
curl -X DELETE http://localhost:8080/users/1
```

## 4. MyBatis-Plus (增强版)

在实际项目中，很多团队用 **MyBatis-Plus** 替代原生 MyBatis。它在 MyBatis 的基础上增加了很多开箱即用的功能。

> **类比**：MyBatis 是 `axios`，MyBatis-Plus 是 `axios` + 拦截器 + 默认配置——更省心。

### 4.1 添加依赖

```xml
<!-- 替换原来的 mybatis-spring-boot-starter -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>3.5.5</version>
</dependency>
```

### 4.2 实体类注解

```java
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("users")  // 表名
public class User {

    @TableId(type = IdType.AUTO)  // 主键，自增
    private Long id;

    private String username;
    private String email;
    private Integer age;
    private LocalDateTime createdAt;
}
```

### 4.3 Mapper (零 SQL！)

```java
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 不用写任何方法！BaseMapper 已经提供了全部 CRUD
}
```

`BaseMapper<User>` 自带的方法：

```java
userMapper.insert(user);                    // 新增
userMapper.selectById(1L);                  // 按 id 查
userMapper.selectList(null);                // 查所有
userMapper.updateById(user);                // 更新
userMapper.deleteById(1L);                  // 删除
userMapper.selectCount(null);               // 计数
```

### 4.4 条件构造器 (QueryWrapper)

MyBatis-Plus 的杀手锏——不用写 SQL 也能实现复杂查询：

```java
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

// age > 22 AND username LIKE '%a%' ORDER BY age DESC
QueryWrapper<User> wrapper = new QueryWrapper<>();
wrapper.gt("age", 22)
       .like("username", "a")
       .orderByDesc("age");

List<User> result = userMapper.selectList(wrapper);
```

```java
// Lambda 写法（推荐，有编译器检查，不怕写错字段名）
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
wrapper.gt(User::getAge, 22)
       .like(User::getUsername, "a")
       .orderByDesc(User::getAge);

List<User> result = userMapper.selectList(wrapper);
```

> **前端类比**：`QueryWrapper` 就像 Prisma 的 `where` 条件构建器：
> ```javascript
> prisma.user.findMany({
>   where: { age: { gt: 22 }, username: { contains: 'a' } },
>   orderBy: { age: 'desc' }
> })
> ```

### 4.5 分页

```java
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

// 第 1 页，每页 10 条
Page<User> page = new Page<>(1, 10);
userMapper.selectPage(page, null);

System.out.println("总数: " + page.getTotal());
System.out.println("当前页数据: " + page.getRecords());
System.out.println("总页数: " + page.getPages());
```

需要在配置类中注册分页插件：

```java
@Configuration
public class MyBatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

## 5. 原生 MyBatis vs MyBatis-Plus 对比

| 对比项 | MyBatis | MyBatis-Plus |
| :--- | :--- | :--- |
| CRUD | 手动写 SQL / 注解 | `BaseMapper` 开箱即用 |
| 条件查询 | XML 动态 SQL | `QueryWrapper` / `LambdaQueryWrapper` |
| 分页 | 手动写 LIMIT | `Page` 对象 + 分页插件 |
| 复杂 SQL | XML 方式（强项） | 仍然支持 XML (向下兼容) |
| 学习成本 | 中等 | 低 |
| 灵活度 | 极高 | 高 |

> **建议**：新项目直接用 MyBatis-Plus。需要复杂 SQL 时可以退回到 XML 方式——两者完全兼容。

## 总结

*   **MyBatis** 将 SQL 结果自动映射成 Java 对象，告别 JDBC 模板代码。
*   两种写法：**注解**（简单 SQL）和 **XML**（复杂/动态 SQL）。
*   **MyBatis-Plus** 在 MyBatis 基础上提供了 `BaseMapper` 零 SQL CRUD 和 `QueryWrapper` 条件构造器。
*   三层架构不变：Controller → Service → Mapper (替代 Repository)。

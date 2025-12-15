# 07. 实战：用 Spring Boot 写一个 CRUD 接口

> 目标：从零初始化一个 Java Web 项目，并实现增删改查 API。

前面我们学了 Java 语法、Maven 和 JDBC。但在实际工作中，我们几乎不会用原生 Java 写后端，而是用框架。
Java 世界的霸主框架是 **Spring Boot**。
它之于 Java，就像 **Next.js/Nuxt.js** 之于 React/Vue，或者 **NestJS** 之于 Node.js。它帮我们配置好了服务器 (Tomcat)、依赖管理等一切繁琐的东西。

## 1. 初始化项目 (Spring Initializr)

不用手动创建文件夹，我们用官方生成器。

1.  打开 [start.spring.io](https://start.spring.io/)。
2.  **Project**: 选择 `Maven`。
3.  **Language**: 选择 `Java`。
4.  **Spring Boot**: 选择默认选中的稳定版 (如 3.x.x)。
5.  **Project Metadata**:
    *   Group: `com.example`
    *   Artifact: `demo`
    *   Packaging: `Jar`
    *   Java: `17` (或者 8，取决于你安装的版本)
6.  **Dependencies** (右侧添加依赖):
    *   搜索并添加 **`Spring Web`** (这是写 REST API 必须的)。
    *   *(可选) `Lombok` (简化代码)*
    *   *(可选) `Spring Data JPA` + `MySQL Driver` (如果要连数据库)*
7.  点击 **GENERATE**，下载压缩包并解压。
8.  用 VS Code 或 IDEA 打开解压后的文件夹。

## 2. 项目结构

你会看到一个标准的 Maven 结构：

```text
demo/
├── pom.xml                 <-- 依赖管理 (package.json)
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/demo/
│   │   │       └── DemoApplication.java  <-- 启动入口 (main)
│   │   └── resources/
│   │       └── application.properties    <-- 配置文件 (.env)
```

## 3. 编写代码

为了演示方便，我们先做一个**内存版**的 CRUD (数据存在 List 里，重启消失)。如果你想连数据库，只需把 List 换成 JDBC 或 JPA 操作即可。

在 `com.example.demo` 包下新建三个文件：

### 3.1 实体类 (User.java)
定义数据模型。

```java
package com.example.demo;

public class User {
    private Integer id;
    private String name;

    // 必须有无参构造函数
    public User() {}

    public User(Integer id, String name) {
        this.id = id;
        this.name = name;
    }

    // Getter & Setter (必须写，否则 JSON 序列化会失败)
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
```

### 3.2 控制器 (UserController.java)
这是写 API 的地方。

*   `@RestController`: 告诉 Spring 这是一个返回 JSON 的控制器。
*   `@GetMapping` / `@PostMapping`: 路由映射。

```java
package com.example.demo;

import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/users") // 基础路径: http://localhost:8080/users
public class UserController {

    // 模拟数据库
    private List<User> userList = new ArrayList<>();

    public UserController() {
        // 初始化一点数据
        userList.add(new User(1, "Jack"));
        userList.add(new User(2, "Rose"));
    }

    // 1. 查 (GET /users)
    @GetMapping
    public List<User> getAll() {
        return userList;
    }

    // 2. 增 (POST /users)
    // @RequestBody: 接收前端传来的 JSON Body
    @PostMapping
    public User add(@RequestBody User user) {
        userList.add(user);
        return user;
    }

    // 3. 删 (DELETE /users/{id})
    // @PathVariable: 接收路径参数
    @DeleteMapping("/{id}")
    public String delete(@PathVariable Integer id) {
        // 使用 Lambda 表达式移除元素
        userList.removeIf(u -> u.getId().equals(id));
        return "Delete Success";
    }
    
    // 4. 改 (PUT /users/{id})
    @PutMapping("/{id}")
    public User update(@PathVariable Integer id, @RequestBody User userParams) {
        for (User u : userList) {
            if (u.getId().equals(id)) {
                u.setName(userParams.getName());
                return u;
            }
        }
        return null;
    }
}
```

## 4. 运行与测试

### 运行
1.  找到 `DemoApplication.java` (带有 `main` 方法的那个类)。
2.  点击运行 (Run)。
3.  或者在终端输入: `mvn spring-boot:run`。

看到日志里出现 `Tomcat started on port 8080` 说明启动成功。

### 测试 (前端视角)
你可以用 Postman，或者直接用浏览器/终端。

1.  **获取列表**: 浏览器访问 `http://localhost:8080/users`
    *   响应: `[{"id":1,"name":"Jack"},{"id":2,"name":"Rose"}]`
2.  **新增用户**:
    *   Method: `POST`
    *   URL: `http://localhost:8080/users`
    *   Body (JSON): `{"id": 3, "name": "Tom"}`
3.  **再次查询**: 列表里应该有 3 个人了。

## 5. 总结

你看，用 Spring Boot 写后端其实和用 Express/Koa 差不多：
1.  **Controller** 对应 路由+处理函数。
2.  **Service/Repository** (虽然这里没写) 对应 业务逻辑/数据库操作。
3.  **Maven** 帮你搞定了所有依赖下载和打包。

这就是现代 Java 开发的样子。

---
title: Java 操作 MongoDB
---

# Java 操作 MongoDB

在 [上一章](/back-end/database/mongodb/crud) 中我们用 `mongosh` 手动操作 MongoDB。在实际项目中，我们需要用 Java 代码来操作。

Java 操作 MongoDB 有两种主流方式：
1. **MongoDB Java Driver**：原生驱动，类似 JDBC 之于 MySQL。
2. **Spring Data MongoDB**：Spring 封装的高级 API，声明式操作。

> [Java 教程第 08 章](/back-end/java/08-spring-data-db) 已经详细讲解了 Spring Data MongoDB 的完整三层架构实战。本章聚焦 **原生 Java Driver** 的用法，帮你理解底层原理。

## 1. 准备工作

### Maven 依赖

在 `pom.xml` 中添加 MongoDB Java Driver：

```xml
<dependency>
    <groupId>org.mongodb</groupId>
    <artifactId>mongodb-driver-sync</artifactId>
    <version>4.11.1</version>
</dependency>
```

> **对比**：MySQL 用 `mysql-connector-java`，MongoDB 用 `mongodb-driver-sync`。概念一样——数据库厂商提供的 Java 驱动。

## 2. 连接 MongoDB

```java
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

public class MongoDemo {
    public static void main(String[] args) {
        // 1. 建立连接（类似 JDBC 的 DriverManager.getConnection）
        MongoClient client = MongoClients.create("mongodb://localhost:27017");

        // 2. 选择数据库（相当于 USE my_learning_db）
        MongoDatabase database = client.getDatabase("my_learning_db");

        // 3. 获取集合（相当于选择表）
        MongoCollection<Document> users = database.getCollection("users");

        System.out.println("Connected! Collection count: " + users.countDocuments());

        // 4. 关闭连接
        client.close();
    }
}
```

> **前端类比**：`MongoClients.create()` 就像 `mongoose.connect()`；`getDatabase()` 就像选一个 database；`getCollection()` 就像拿到一个 Model。

## 3. CRUD 操作

### 3.1 新增 (Insert)

```java
import org.bson.Document;
import java.util.Arrays;
import java.util.List;

// 插入单条 —— db.users.insertOne({...})
Document jack = new Document("name", "Jack")
        .append("age", 25)
        .append("email", "jack@example.com")
        .append("skills", Arrays.asList("JavaScript", "React"));

users.insertOne(jack);
System.out.println("插入成功，_id: " + jack.getObjectId("_id"));

// 插入多条 —— db.users.insertMany([...])
List<Document> newUsers = Arrays.asList(
    new Document("name", "Alice").append("age", 20).append("email", "alice@example.com"),
    new Document("name", "Bob").append("age", 30).append("email", "bob@example.com")
);
users.insertMany(newUsers);
```

> `Document` 就是 MongoDB 的 JSON 文档在 Java 里的表示。`.append()` 就像给对象加属性。

### 3.2 查询 (Find)

```java
import com.mongodb.client.FindIterable;
import static com.mongodb.client.model.Filters.*;

// 查所有 —— db.users.find()
FindIterable<Document> allUsers = users.find();
for (Document doc : allUsers) {
    System.out.println(doc.toJson());
}

// 条件查询 —— db.users.find({ name: "Jack" })
Document jack = users.find(eq("name", "Jack")).first();
System.out.println(jack.toJson());

// 比较查询 —— db.users.find({ age: { $gt: 22 } })
FindIterable<Document> olderUsers = users.find(gt("age", 22));

// AND 条件 —— { age > 20, name: "Alice" }
FindIterable<Document> result = users.find(and(gt("age", 20), eq("name", "Alice")));

// OR 条件
FindIterable<Document> result2 = users.find(or(lt("age", 22), gt("age", 28)));
```

`Filters` 类提供了所有条件运算符的静态方法：

| Java (Filters) | mongosh | 含义 |
| :--- | :--- | :--- |
| `eq("name", "Jack")` | `{ name: "Jack" }` | 等于 |
| `gt("age", 22)` | `{ age: { $gt: 22 } }` | 大于 |
| `gte("age", 22)` | `{ age: { $gte: 22 } }` | 大于等于 |
| `lt("age", 30)` | `{ age: { $lt: 30 } }` | 小于 |
| `in("name", "Jack", "Alice")` | `{ name: { $in: [...] } }` | 在列表中 |
| `regex("name", "^J")` | `{ name: /^J/ }` | 正则匹配 |

### 排序与分页

```java
import static com.mongodb.client.model.Sorts.*;

// 按 age 降序，取前 3 条
FindIterable<Document> page = users.find()
    .sort(descending("age"))
    .skip(0)
    .limit(3);
```

### 3.3 更新 (Update)

```java
import static com.mongodb.client.model.Updates.*;

// 更新单条 —— db.users.updateOne({ name: "Jack" }, { $set: { age: 26 } })
users.updateOne(eq("name", "Jack"), set("age", 26));

// 更新多条
users.updateMany(lt("age", 25), set("level", "junior"));

// 数字自增 —— $inc
users.updateOne(eq("name", "Jack"), inc("age", 1));

// 组合多个更新操作
users.updateOne(
    eq("name", "Jack"),
    combine(set("age", 27), set("email", "jack_new@example.com"))
);
```

### 3.4 删除 (Delete)

```java
// 删除单条
users.deleteOne(eq("name", "Tom"));

// 删除多条
users.deleteMany(lt("age", 22));
```

## 4. 完整示例

```java
import com.mongodb.client.*;
import org.bson.Document;
import java.util.Arrays;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;
import static com.mongodb.client.model.Sorts.*;

public class MongoDemo {
    public static void main(String[] args) {
        // 连接
        try (MongoClient client = MongoClients.create("mongodb://localhost:27017")) {
            MongoDatabase db = client.getDatabase("my_learning_db");
            MongoCollection<Document> users = db.getCollection("users");

            // 清空测试数据
            users.deleteMany(new Document());

            // 新增
            users.insertMany(Arrays.asList(
                new Document("name", "Jack").append("age", 25).append("email", "jack@example.com"),
                new Document("name", "Alice").append("age", 20).append("email", "alice@example.com"),
                new Document("name", "Bob").append("age", 30).append("email", "bob@example.com")
            ));
            System.out.println("=== 插入后 ===");
            users.find().forEach(doc -> System.out.println(doc.toJson()));

            // 查询：age > 22
            System.out.println("\n=== age > 22 ===");
            users.find(gt("age", 22)).forEach(doc -> System.out.println(doc.toJson()));

            // 更新：Jack 的 age 改成 26
            users.updateOne(eq("name", "Jack"), set("age", 26));
            System.out.println("\n=== 更新 Jack 后 ===");
            System.out.println(users.find(eq("name", "Jack")).first().toJson());

            // 删除：删掉 Alice
            users.deleteOne(eq("name", "Alice"));
            System.out.println("\n=== 删除 Alice 后 ===");
            users.find().forEach(doc -> System.out.println(doc.toJson()));

            // 统计
            System.out.println("\n总文档数: " + users.countDocuments());
        }
    }
}
```

## 5. 原生 Driver vs Spring Data MongoDB

| 对比项 | 原生 Driver | Spring Data MongoDB |
| :--- | :--- | :--- |
| 依赖 | `mongodb-driver-sync` | `spring-boot-starter-data-mongodb` |
| 数据表示 | `Document` (类似 Map) | 实体类 (POJO + 注解) |
| 查询方式 | `Filters.eq()`, `Filters.gt()` | 方法名查询 `findByName()` |
| CRUD | 手动写每一步 | 继承 `MongoRepository` 自动拥有 |
| 代码量 | 多 | 少（只写接口声明） |
| 适用场景 | 理解原理、特殊需求 | 实际项目开发 |

> **建议**：理解原生 Driver 的原理即可，实际项目直接用 Spring Data MongoDB（见 [Java 08 章](/back-end/java/08-spring-data-db)）。就像前端理解 XHR 的原理，但实际用 axios 一样。

## 总结

*   MongoDB Java Driver 的操作模式和 `mongosh` 几乎一一对应。
*   `Document` = JSON 文档，`Filters` = 查询条件，`Updates` = 更新操作。
*   实际项目推荐使用 Spring Data MongoDB，让框架帮你完成重复的 CRUD 代码。

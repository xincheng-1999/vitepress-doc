---
title: MongoDB 简介与安装
---

# MongoDB 简介与安装

在 [关系型 vs 非关系型数据库](/back-end/database/basics/relational-vs-nosql) 中我们提到，MongoDB 是 NoSQL 的典型代表。它以 **JSON 文档**的形式存储数据，对前端开发者来说非常亲切。

## 1. 什么是 MongoDB？

MongoDB 是一个**文档型数据库 (Document Database)**。它不像 MySQL 那样把数据存在表格里，而是存成一个个 **JSON 文档**。

### 前端类比

*   **MySQL** 的数据像 **Excel 表格**——固定列，每行格式一致。
*   **MongoDB** 的数据像 **localStorage** 里存的 JSON——灵活、可嵌套、不要求统一结构。

```javascript
// 这就是 MongoDB 里的一条"文档"，天然就是 JSON
{
  "_id": "665fa12e...",
  "name": "Jack",
  "age": 25,
  "skills": ["JavaScript", "React", "Node.js"],
  "address": {
    "city": "Shanghai",
    "zip": "200000"
  }
}
```

### 核心概念对照

| MySQL 术语 | MongoDB 术语 | 前端类比 |
| :--- | :--- | :--- |
| Database (数据库) | Database (数据库) | 一个项目 |
| Table (表) | Collection (集合) | 一个数组 `[]` |
| Row (行) | Document (文档) | 数组里的一个对象 `{}` |
| Column (列) | Field (字段) | 对象的属性 key |
| Schema (表结构) | 无强制 Schema | 无 TypeScript interface |
| `SELECT * FROM users` | `db.users.find()` | `users.filter(...)` |

### 为什么要学 MongoDB？

1. **前端友好**：存取的就是 JSON，不需要任何转换。
2. **灵活**：不用提前定义表结构，新增字段随时加。
3. **嵌套方便**：地址、标签、评论可以直接嵌套在文档里，不用像 MySQL 那样建多张关联表。
4. **实战需要**：在 [Java 教程第 08 章](/back-end/java/08-spring-data-db) 的项目中，MongoDB 就是主数据库。

## 2. 安装 MongoDB

### 方式一：Windows 安装包 (适合新手)

1. 访问 [MongoDB Community Download](https://www.mongodb.com/try/download/community)。
2. 选择 **Windows** → **msi** 包下载。
3. 运行安装包：
   * 选择 **Complete** 安装类型。
   * 勾选 **Install MongoDB as a Service** (开机自启)。
   * 勾选 **Install MongoDB Compass** (官方图形化工具，类似 DBeaver)。
4. 安装完成后，打开终端验证：

```bash
mongosh --version
```

如果提示"不是内部或外部命令"，需要将 MongoDB 安装目录下的 `bin` 路径（通常是 `C:\Program Files\MongoDB\Server\7.0\bin`）添加到系统 **Path** 环境变量。

### 方式二：Docker (推荐，一行搞定)

如果你装了 Docker Desktop：

```bash
docker run -d -p 27017:27017 --name mongo mongo:7
```

这行命令做了什么：
* `-d`：后台运行
* `-p 27017:27017`：把容器的 27017 端口映射到本机（MongoDB 默认端口）
* `--name mongo`：容器命名为 `mongo`
* `mongo:7`：使用 MongoDB 7.x 镜像

### 方式三：Mac

```bash
brew tap mongodb/brew
brew install mongodb-community
brew services start mongodb-community
```

## 3. 验证安装

打开终端，输入：

```bash
mongosh
```

如果看到类似以下提示，说明安装成功：

```
Current Mongosh Log ID: 665fa...
Connecting to: mongodb://127.0.0.1:27017
Using MongoDB: 7.0.x
test>
```

输入 `exit` 退出。

## 4. 使用 MongoDB Compass (图形化工具)

如果安装时勾选了 Compass，打开它：

1. **连接地址**：`mongodb://localhost:27017`
2. 点击 **Connect**。
3. 你会看到系统自带的数据库 (`admin`, `config`, `local`)。

> MongoDB Compass 之于 MongoDB，就像 DBeaver 之于 MySQL。你可以在里面可视化地浏览文档、执行查询。

如果你更习惯 DBeaver，它也支持连接 MongoDB（需要安装 MongoDB 插件）。

## 5. mongosh 基础操作预览

在进入下一章的完整 CRUD 之前，先感受几个最基本的命令：

```javascript
// 切换/创建数据库（不存在就自动创建）
use my_learning_db

// 插入一条文档
db.users.insertOne({ name: "Jack", age: 25 })

// 查询所有文档
db.users.find()

// 查看当前数据库有哪些集合
show collections
```

> 是不是很像在写 JavaScript？这就是 MongoDB 对前端最友好的地方——**查询语法就是 JS 对象**。

## 总结

| 对比项 | MySQL | MongoDB |
| :--- | :--- | :--- |
| 安装复杂度 | 中等 | 低（Docker 一行搞定） |
| 数据格式 | 表格（行+列） | JSON 文档 |
| 图形化工具 | DBeaver / Workbench | Compass / DBeaver |
| 查询语言 | SQL | JavaScript-like API |
| 默认端口 | 3306 | 27017 |

接下来，我们将正式学习 MongoDB 的增删改查操作。

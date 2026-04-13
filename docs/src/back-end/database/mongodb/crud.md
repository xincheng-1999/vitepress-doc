---
title: MongoDB CRUD 与聚合
---

# MongoDB CRUD 与聚合

学完了 [MySQL 的 SQL 增删改查](/back-end/database/mysql/sql-basics)，现在来看 MongoDB 的 CRUD。你会发现：**MongoDB 的查询语法就是 JavaScript 对象**，上手比 SQL 更快。

## 1. 准备工作

打开终端，进入 `mongosh`：

```bash
mongosh
```

切换到学习数据库：

```javascript
use my_learning_db
```

## 2. 新增 (Insert) - Create

### 插入单条
相当于 `array.push(obj)`。

```javascript
db.users.insertOne({
  name: "Jack",
  age: 25,
  email: "jack@example.com",
  skills: ["JavaScript", "React"]
})
```

> MongoDB 会自动为每条文档生成 `_id` (ObjectId)，相当于 MySQL 的 `AUTO_INCREMENT` 主键。

### 插入多条

```javascript
db.users.insertMany([
  { name: "Alice", age: 20, email: "alice@example.com", skills: ["Vue", "CSS"] },
  { name: "Bob", age: 30, email: "bob@example.com", skills: ["Java", "Spring"] },
  { name: "Tom", age: 22, email: "tom@example.com", skills: ["Python", "Django"] }
])
```

## 3. 查询 (Find) - Read

### 基础查询

```javascript
// 查所有 —— 相当于 SELECT * FROM users
db.users.find()

// 美化输出
db.users.find().pretty()

// 条件查询 —— 相当于 SELECT * FROM users WHERE name = 'Jack'
db.users.find({ name: "Jack" })

// 只返回特定字段 —— 相当于 SELECT name, age FROM users
db.users.find({}, { name: 1, age: 1, _id: 0 })
// 1 表示"要这个字段"，0 表示"不要"
```

### 条件运算符

MySQL 用 `>`, `<`, `=`，MongoDB 用 `$gt`, `$lt`, `$eq` 等：

```javascript
// age > 22 —— WHERE age > 22
db.users.find({ age: { $gt: 22 } })

// age >= 22 AND age <= 30
db.users.find({ age: { $gte: 22, $lte: 30 } })

// name 是 Jack 或 Alice —— WHERE name IN ('Jack', 'Alice')
db.users.find({ name: { $in: ["Jack", "Alice"] } })

// name 不是 Bob
db.users.find({ name: { $ne: "Bob" } })
```

**运算符速查表：**

| MongoDB | SQL | 含义 |
| :--- | :--- | :--- |
| `$eq` | `=` | 等于 |
| `$ne` | `!=` | 不等于 |
| `$gt` | `>` | 大于 |
| `$gte` | `>=` | 大于等于 |
| `$lt` | `<` | 小于 |
| `$lte` | `<=` | 小于等于 |
| `$in` | `IN (...)` | 在列表中 |
| `$nin` | `NOT IN` | 不在列表中 |

### 逻辑运算符

```javascript
// AND —— age > 20 AND name = 'Alice'
db.users.find({ age: { $gt: 20 }, name: "Alice" })

// OR —— age < 22 OR age > 28
db.users.find({ $or: [{ age: { $lt: 22 } }, { age: { $gt: 28 } }] })
```

### 模糊查询 (正则)

MySQL 用 `LIKE`，MongoDB 用正则表达式：

```javascript
// name 以 'J' 开头 —— WHERE name LIKE 'J%'
db.users.find({ name: /^J/ })

// name 包含 'a' —— WHERE name LIKE '%a%'
db.users.find({ name: /a/i })  // i 表示不区分大小写
```

### 排序与分页

```javascript
// 按 age 升序 —— ORDER BY age ASC
db.users.find().sort({ age: 1 })

// 按 age 降序 —— ORDER BY age DESC
db.users.find().sort({ age: -1 })

// 取前 2 条 —— LIMIT 2
db.users.find().limit(2)

// 跳过前 2 条，取 2 条 (第 2 页) —— LIMIT 2 OFFSET 2
db.users.find().skip(2).limit(2)

// 组合使用：按 age 降序取前 3 条
db.users.find().sort({ age: -1 }).limit(3)
```

### 查询单条

```javascript
// 只要第一条匹配的 —— 类似 array.find()
db.users.findOne({ name: "Jack" })
```

### 计数

```javascript
// 统计总数 —— SELECT COUNT(*) FROM users
db.users.countDocuments()

// 带条件计数
db.users.countDocuments({ age: { $gt: 22 } })
```

## 4. 更新 (Update) - Update

### 更新单条

```javascript
// 把 Jack 的 age 改成 26
db.users.updateOne(
  { name: "Jack" },        // 查询条件 (WHERE)
  { $set: { age: 26 } }   // 要修改的字段
)
```

> `$set` 的意思是"只修改这些字段，其他字段不动"。如果不用 `$set`，整个文档会被替换！

### 更新多条

```javascript
// 所有 age < 25 的人，加一个字段 level: "junior"
db.users.updateMany(
  { age: { $lt: 25 } },
  { $set: { level: "junior" } }
)
```

### 常用更新操作符

```javascript
// $inc: 数字自增 —— UPDATE users SET age = age + 1
db.users.updateOne({ name: "Jack" }, { $inc: { age: 1 } })

// $unset: 删除字段 —— ALTER TABLE DROP COLUMN (MongoDB 独有的灵活性)
db.users.updateOne({ name: "Jack" }, { $unset: { level: "" } })

// $push: 往数组里加元素
db.users.updateOne({ name: "Jack" }, { $push: { skills: "TypeScript" } })

// $pull: 从数组里删元素
db.users.updateOne({ name: "Jack" }, { $pull: { skills: "React" } })
```

> 这些操作符是 MongoDB 相比 MySQL 最灵活的地方——可以直接操作嵌套字段和数组。

## 5. 删除 (Delete) - Delete

```javascript
// 删除单条
db.users.deleteOne({ name: "Tom" })

// 删除多条
db.users.deleteMany({ age: { $lt: 22 } })

// ⚠️ 清空集合 (慎用！相当于 DELETE FROM users)
db.users.deleteMany({})

// 删除整个集合 (连集合本身也没了)
db.users.drop()
```

## 6. 聚合管道 (Aggregate Pipeline)

聚合管道是 MongoDB 处理复杂数据分析的利器，可以理解为**数据依次流过多个处理阶段**。

> **前端类比**：类似 `array.filter(...).map(...).reduce(...)`，数据链式经过多个处理步骤。

### 基础语法

```javascript
db.users.aggregate([
  { 阶段1 },
  { 阶段2 },
  { 阶段3 }
])
```

### 常用阶段

#### `$match` — 筛选 (相当于 WHERE / filter)

```javascript
db.users.aggregate([
  { $match: { age: { $gte: 20 } } }
])
```

#### `$group` — 分组统计 (相当于 GROUP BY / reduce)

```javascript
// 统计每个 level 有多少人，以及平均年龄
db.users.aggregate([
  { $group: {
      _id: "$level",            // 按哪个字段分组
      count: { $sum: 1 },       // 计数
      avgAge: { $avg: "$age" }  // 平均值
  }}
])
```

#### `$sort` — 排序 (相当于 ORDER BY)

```javascript
db.users.aggregate([
  { $group: { _id: "$level", count: { $sum: 1 } } },
  { $sort: { count: -1 } }  // 按 count 降序
])
```

#### `$project` — 投影 (相当于 SELECT 指定字段 / map)

```javascript
db.users.aggregate([
  { $project: { name: 1, age: 1, _id: 0 } }
])
```

#### `$limit` 和 `$skip` — 分页

```javascript
db.users.aggregate([
  { $sort: { age: -1 } },
  { $skip: 0 },
  { $limit: 10 }
])
```

### 组合示例

"查找年龄大于 20 的用户，按等级分组统计人数，按人数降序排列"：

```javascript
db.users.aggregate([
  { $match: { age: { $gt: 20 } } },       // 1. 先筛选
  { $group: {                               // 2. 再分组
      _id: "$level",
      count: { $sum: 1 },
      avgAge: { $avg: "$age" }
  }},
  { $sort: { count: -1 } },               // 3. 排序
  { $limit: 5 }                            // 4. 取前 5
])
```

用前端链式调用类比：

```javascript
users
  .filter(u => u.age > 20)                  // $match
  .reduce(groupByLevel, {})                 // $group
  .sort((a, b) => b.count - a.count)        // $sort
  .slice(0, 5)                              // $limit
```

## 7. MongoDB vs MySQL CRUD 对照表

| 操作 | MySQL (SQL) | MongoDB (mongosh) |
| :--- | :--- | :--- |
| 插入 | `INSERT INTO users (...) VALUES (...)` | `db.users.insertOne({...})` |
| 查所有 | `SELECT * FROM users` | `db.users.find()` |
| 条件查 | `SELECT * WHERE age > 20` | `db.users.find({age: {$gt: 20}})` |
| 更新 | `UPDATE users SET age=26 WHERE name='Jack'` | `db.users.updateOne({name:"Jack"}, {$set:{age:26}})` |
| 删除 | `DELETE FROM users WHERE id=1` | `db.users.deleteOne({_id: ObjectId("...")})` |
| 计数 | `SELECT COUNT(*) FROM users` | `db.users.countDocuments()` |
| 排序 | `ORDER BY age DESC` | `.sort({age: -1})` |
| 分页 | `LIMIT 10 OFFSET 20` | `.skip(20).limit(10)` |
| 模糊查 | `WHERE name LIKE '%a%'` | `{name: /a/i}` |
| 分组统计 | `GROUP BY level` | `aggregate([{$group: {_id: "$level"}}])` |

## 总结

MongoDB 的 CRUD 对前端开发者来说几乎没有学习门槛——**查询语法就是 JS 对象，聚合管道就是链式调用**。

不过在实际项目中，很少直接在 `mongosh` 里敲命令。下一章我们将学习如何用 **Java + Spring Data** 在代码中操作 MongoDB。

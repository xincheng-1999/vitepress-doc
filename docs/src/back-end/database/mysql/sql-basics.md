# SQL 基础语法 (增删改查)

SQL (Structured Query Language) 是我们与数据库对话的语言。
对于前端来说，**CRUD** (Create, Read, Update, Delete) 是最熟悉的操作。在 SQL 中，它们分别对应 `INSERT`, `SELECT`, `UPDATE`, `DELETE`。

## 1. 创建数据库与表

在 DBeaver 中，你可以右键新建，但作为开发者，我们学习用代码（SQL 语句）来创建。打开 DBeaver 的 **SQL 编辑器** (F3)。

### 创建数据库 (Create Database)
相当于创建一个新的项目文件夹。

```sql
CREATE DATABASE my_learning_db;
USE my_learning_db; -- 切换到这个数据库
```

### 创建表 (Create Table)
相当于定义一个 TypeScript Interface。

```sql
-- 定义一个用户表
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY, -- id: 数字, 自动递增, 主键(唯一)
    username VARCHAR(50) NOT NULL,     -- username: 字符串(最大50), 必填
    age INT,                           -- age: 数字, 选填
    email VARCHAR(100),                -- email: 字符串
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 创建时间: 默认当前时间
);
```

*   `INT`: 整数 (Number)
*   `VARCHAR(n)`: 可变长度字符串 (String)
*   `PRIMARY KEY`: 主键，类似 React 的 `key`，唯一标识。
*   `AUTO_INCREMENT`: 自动递增，你不用管 id，数据库自动填 1, 2, 3...

## 2. 新增数据 (INSERT) - Create

相当于 `Array.push()`。

```sql
-- 插入一条数据
INSERT INTO users (username, age, email) 
VALUES ('Jack', 25, 'jack@example.com');

-- 插入多条数据
INSERT INTO users (username, age, email) 
VALUES 
('Alice', 20, 'alice@example.com'),
('Bob', 30, 'bob@example.com');
```

## 3. 查询数据 (SELECT) - Read

相当于 `Array.filter()` 和 `Array.map()`。

```sql
-- 1. 获取所有数据 (Array.map(item => item))
SELECT * FROM users;

-- 2. 只获取特定列 (Array.map(item => ({ name: item.username })))
SELECT username, email FROM users;

-- 3. 条件查询 (Array.filter(item => item.age > 22))
SELECT * FROM users WHERE age > 22;

-- 4. 多条件查询 (&&, ||)
SELECT * FROM users WHERE age > 22 AND username = 'Bob';
```

## 4. 更新数据 (UPDATE) - Update

相当于找到数组里的某一项并修改它。
**注意：一定要加 WHERE，否则会修改整张表！**

```sql
-- 把 Jack 的年龄改成 26
UPDATE users 
SET age = 26 
WHERE username = 'Jack';
```

## 5. 删除数据 (DELETE) - Delete

相当于 `Array.splice()` 或 `Array.filter()` 排除掉。
**注意：一定要加 WHERE，否则会清空整张表！**

```sql
-- 删除 id 为 2 的用户
DELETE FROM users WHERE id = 2;
```

## 练习
在 DBeaver 里试着运行这些语句，观察表数据的变化。

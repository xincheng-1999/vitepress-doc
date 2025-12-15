# 高级查询与事务

掌握了基础的 CRUD，我们来看看 SQL 真正强大的地方：处理复杂数据关系和保证数据安全。

## 1. 聚合函数 (Aggregation)

前端通常要在拿到数组后用 `reduce` 计算总和或平均值，数据库可以直接算好给你。

```sql
-- 统计有多少个用户 (users.length)
SELECT COUNT(*) FROM users;

-- 计算平均年龄
SELECT AVG(age) FROM users;

-- 查找最大年龄
SELECT MAX(age) FROM users;
```

## 2. 模糊查询 (LIKE)

有时候我们记不住完整的名字，只想查“姓张的用户”或者“邮箱包含 google 的用户”，这就需要用到模糊查询。

*   `%`: 代表任意多个字符（包括 0 个）。
*   `_`: 代表任意一个字符。

```sql
-- 查找名字以 'J' 开头的用户 (Jack, John...)
SELECT * FROM users WHERE username LIKE 'J%';

-- 查找名字里包含 'a' 的用户 (Jack, Mary...)
SELECT * FROM users WHERE username LIKE '%a%';

-- 查找名字是 3 个字，且中间是 'o' 的用户 (Tom, Bob...)
SELECT * FROM users WHERE username LIKE '_o_';
```

## 3. 排序与分页 (Order & Limit)

这在做列表页时非常常用。

```sql
-- 按年龄从小到大排序 (ASC: Ascending)
SELECT * FROM users ORDER BY age ASC;

-- 按年龄从大到小排序 (DESC: Descending)
SELECT * FROM users ORDER BY age DESC;

-- 分页：取前 10 条
SELECT * FROM users LIMIT 10;

-- 分页：跳过前 20 条，取 10 条 (第 3 页)
SELECT * FROM users LIMIT 10 OFFSET 20;
```

## 4. 联表查询 (JOIN) - 重点难点

这是关系型数据库的核心，也是最容易晕的地方。我们用一个具体的例子来拆解。

### 场景假设
想象你有两个表格：

**表 A: `users` (用户表)**
| id | username |
| :--- | :--- |
| 1 | Jack |
| 2 | Rose |
| 3 | Tom |

**表 B: `orders` (订单表)**
| id | user_id | product |
| :--- | :--- | :--- |
| 101 | 1 | Apple |
| 102 | 2 | Banana |

*   **注意**：Jack (id:1) 买了个苹果，Rose (id:2) 买了个香蕉，**Tom (id:3) 没买东西**。

### 4.1 INNER JOIN (内连接)
**口诀**：“只取交集，两边都有才显示。”

我们想查：**“所有下过单的用户及其订单信息”**。
因为 Tom 没下单，所以他**不会**出现在结果里。

```sql
SELECT users.username, orders.product
FROM users
INNER JOIN orders ON users.id = orders.user_id;
```

**结果：**
| username | product |
| :--- | :--- |
| Jack | Apple |
| Rose | Banana |

**逻辑解释：**
数据库会拿着 `users` 表里的每一行，去 `orders` 表里找 `user_id` 匹配的行。
*   Jack (id:1) 在 orders 里找到了 (user_id:1)，**匹配成功**，保留。
*   Rose (id:2) 在 orders 里找到了 (user_id:2)，**匹配成功**，保留。
*   Tom (id:3) 在 orders 里**找不到**对应的 user_id，**匹配失败**，直接丢弃。

### 4.2 LEFT JOIN (左连接) - 最常用
**口诀**：“左边全保留，右边匹配不上填 NULL。”

我们想查：**“所有用户的订单情况（包括没买东西的用户）”**。
这次我们要保留 `users` 表（左表）的所有人，哪怕他没买东西。

```sql
SELECT users.username, orders.product
FROM users
LEFT JOIN orders ON users.id = orders.user_id;
```

**结果：**
| username | product |
| :--- | :--- |
| Jack | Apple |
| Rose | Banana |
| **Tom** | **NULL** |

*   看！Tom 出来了，但是他的 `product` 是 `NULL`。

**逻辑解释：**
数据库依然拿着 `users` 表（左表）里的每一行去匹配。
*   Jack 和 Rose 匹配成功，显示对应数据。
*   Tom 在 orders 里找不到对应数据，但因为是 **LEFT JOIN**（左连接），数据库会强制保留 Tom，并在 `orders` 表对应的列（product）填上 **NULL**（空值）。

### 4.3 总结
*   想过滤掉不匹配的数据？用 **INNER JOIN**。
*   想保留主表所有数据，哪怕副表没数据？用 **LEFT JOIN**。
*   **RIGHT JOIN** 就是反过来，保留右表所有数据（用得很少，通常习惯把主表写在左边用 LEFT JOIN）。

## 5. 事务 (Transaction)

**事务**是为了保证一组操作要么全部成功，要么全部失败。
最经典的例子是**转账**：A 扣钱，B 加钱。如果 A 扣完钱，服务器断电了，B 没加上钱，那钱就丢了。

在事务中，这两个操作被视为一个整体。

```sql
-- 开始事务
START TRANSACTION;

-- 1. A 扣钱
UPDATE accounts SET balance = balance - 100 WHERE name = 'A';

-- 2. B 加钱
UPDATE accounts SET balance = balance + 100 WHERE name = 'B';

-- 提交事务 (只有运行到这里，上面的修改才会真正生效)
COMMIT;

-- 如果中间出错了，可以回滚 (撤销所有操作)
-- ROLLBACK;
```

前端类比：这就好比你写代码时的 `try...catch` 块，如果 `try` 里面任何一步报错，就进入 `catch` 处理错误，保证数据状态不会乱。

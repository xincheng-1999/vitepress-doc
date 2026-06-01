# SQL 与索引

上一章我们搞清了「SQL 数据库 vs NoSQL」的选型。这一章往里钻一层：作为前端转后端的人，**SQL 到底要会到什么程度，索引为什么是面试和线上排查的高频考点**。

结论先放这：你不需要会写炫技 SQL，但必须做到三件事——

1. 能看懂、能改业务里 80% 的查询（增删改查 + JOIN + 分组）。
2. 知道一条查询为什么慢，会用 `EXPLAIN` 看执行计划。
3. 认得出 N+1 这类「代码写法正确但 DB 被打爆」的坑。

> 前端类比：这就像你写 React 不需要手写 Fiber 调度器，但必须懂「为什么这个组件多渲染了 50 次」「key 用错了会怎样」。SQL 和索引，就是后端的「渲染性能」。

本章 SQL 语法只讲「够用集」，更系统的语法请配合 [SQL 基础](/back-end/database/mysql/sql-basics) 和 [高级查询](/back-end/database/mysql/advanced) 两篇一起看。本章假设我们运行示例项目里有这么两张表（svc-user 的统计库，MySQL）：

```sql
-- 用户表
CREATE TABLE t_user (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  username    VARCHAR(64)  NOT NULL,
  email       VARCHAR(128) NOT NULL,
  status      TINYINT      NOT NULL DEFAULT 1,  -- 1=正常 0=封禁
  created_at  DATETIME     NOT NULL
);

-- 生图任务表（svc-canvas 落到统计库的副本）
CREATE TABLE t_task (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT       NOT NULL,
  status      VARCHAR(16)  NOT NULL,  -- PENDING / RUNNING / SUCCESS / FAILED
  cost        INT          NOT NULL,  -- 本次消耗配额
  created_at  DATETIME     NOT NULL
);
```

---

## 第一部分：SQL 够用集

### 1.1 SELECT / WHERE / ORDER BY / LIMIT 分页

`SELECT` 取数据，`WHERE` 过滤，`ORDER BY` 排序，`LIMIT` 截取。

```sql
-- 查 user_id=42 的成功任务，按时间倒序，取前 10 条
SELECT id, status, cost, created_at
FROM t_task
WHERE user_id = 42 AND status = 'SUCCESS'
ORDER BY created_at DESC
LIMIT 10;
```

> 前端类比：这一整句就是
> `tasks.filter(t => t.userId === 42 && t.status === 'SUCCESS').sort(byTimeDesc).slice(0, 10)`。
> `WHERE` = `filter`，`ORDER BY` = `sort`，`LIMIT` = `slice`。区别是：JS 是把全部数据拉到内存再过滤，SQL 是让数据库**就地**过滤，只把结果传回来——这正是为什么不能把 `filter` 这事丢给前端做。

分页用 `LIMIT 偏移量, 条数`（或 `LIMIT 条数 OFFSET 偏移量`）：

```sql
-- 第 1 页（每页 20 条）
SELECT * FROM t_task ORDER BY id DESC LIMIT 0, 20;
-- 第 3 页：跳过前 40 条
SELECT * FROM t_task ORDER BY id DESC LIMIT 40, 20;
```

::: warning 深翻页陷阱
`LIMIT 1000000, 20` 不是「直接跳到第 100 万条」，数据库会先**扫过前 100 万行再丢弃**，越往后越慢。线上深翻页的正确做法是「游标分页」——记住上一页最后一条的 id，下一页用 `WHERE id < 上次最小id ORDER BY id DESC LIMIT 20`。前端做无限滚动列表时其实也是这个套路（传 `lastId` 而不是 `pageNo`）。
:::

### 1.2 JOIN：把两张表拼起来

业务里数据是拆开存的（任务表只存 `user_id`，不存用户名），展示时要拼回来，这就是 `JOIN`。

```sql
-- INNER JOIN：只保留两边都匹配上的行
SELECT t.id, t.status, u.username
FROM t_task t
INNER JOIN t_user u ON t.user_id = u.id
WHERE t.status = 'FAILED';
```

```sql
-- LEFT JOIN：左表全留，右表没匹配上的补 NULL
SELECT u.id, u.username, t.id AS task_id
FROM t_user u
LEFT JOIN t_task t ON t.user_id = u.id
WHERE u.status = 1;
```

> 前端类比：`INNER JOIN` 像「两个数组按 key 取交集再合并字段」；`LEFT JOIN` 像 `users.map(u => ({ ...u, task: tasks.find(t => t.userId === u.id) ?? null }))`——左边一个不少，右边找不到就给 `null`。记住口诀：**INNER 是交集，LEFT 是左表全留**。

### 1.3 GROUP BY + 聚合：分组统计

`GROUP BY` 把行按某列分组，配合 `COUNT / SUM / AVG / MAX` 做统计。svc-user 算「每个用户今天消耗了多少配额」就靠它：

```sql
SELECT user_id,
       COUNT(*)  AS task_count,   -- 这组有几条
       SUM(cost) AS total_cost    -- 这组 cost 加起来
FROM t_task
WHERE created_at >= '2026-06-01'
GROUP BY user_id
HAVING SUM(cost) > 100            -- 对分组结果再过滤
ORDER BY total_cost DESC;
```

> 前端类比：这就是 `Array.reduce` 按 key 累加的 SQL 版。`WHERE` 在分组**前**过滤行，`HAVING` 在分组**后**过滤组——顺序不能反，因为 `WHERE` 时还没有 `SUM` 这个值。

### 1.4 子查询：把一个查询的结果当条件

```sql
-- 查「至少有一次生图失败」的用户信息
SELECT id, username, email
FROM t_user
WHERE id IN (
  SELECT DISTINCT user_id FROM t_task WHERE status = 'FAILED'
);
```

> 前端类比：先 `const ids = failedTasks.map(t => t.userId)`，再 `users.filter(u => ids.includes(u.id))`。括号里的子查询先算出一组 id，外层再拿这组 id 去过滤。很多子查询能改写成 JOIN，性能往往更好——这个进阶技巧在 [高级查询](/back-end/database/mysql/advanced) 里有展开。

---

## 第二部分：索引（本章重点）

前面的 SQL 你可能很快就会写。但同样一句 `WHERE email = 'a@b.com'`，**有没有索引，性能能差几千倍**。索引才是真正拉开差距的地方。

### 2.1 索引是什么

**索引是为「快速查找某列」额外维护的一份排好序的数据结构。**

> 前端类比一：一本 800 页的书，要找「事务隔离级别」这个词。没有索引 = 从第 1 页一页页翻到找到（全表扫描）；有索引 = 翻到书末的「索引页」，按拼音/字母排好，直接定位到「在第 412 页」。索引页本身也占纸张，且每次书改版都要重排——这就是索引的代价。

> 前端类比二：JS 里你要频繁按 id 找对象，不会每次 `arr.find(x => x.id === id)`（O(n) 全表扫描），而是先 `const map = new Map(arr.map(x => [x.id, x]))`，之后 `map.get(id)` 是 O(1)。**数据库索引就是 DB 帮你维护的这个 Map**——区别是它用的是 B+ 树，不是哈希表。

### 2.2 没索引为什么慢：全表扫描

`t_user` 有 100 万行，执行 `WHERE email = 'x@y.com'`：

- **没索引**：数据库不知道这个 email 在哪，只能从第 1 行扫到第 100 万行，逐行比对。这叫 **全表扫描（full table scan）**，对应执行计划里的 `type=ALL`——看到它就要警惕。
- **有索引**：email 列建了索引（一棵排好序的树），几次比较就定位到目标，扫描行数从 100 万降到接近 1。

### 2.3 B+ 树：一句话

MySQL（InnoDB）的索引用的是 **B+ 树**：一种「矮胖」的多叉排序树，几层就能存下千万行，每次查找只需读几个节点（几次磁盘 IO）。你不用会手写，记住一个结论就够：**B+ 树让「查找、范围扫描、排序」都很快，因为数据天生是有序的。**

### 2.4 索引的几种类型

| 类型 | 作用 | 类比 |
| --- | --- | --- |
| 主键索引 PRIMARY KEY | 唯一 + 非空，一张表只有一个；InnoDB 数据就按它组织 | React 列表的 `key` |
| 唯一索引 UNIQUE | 保证该列不重复，可加速查找 | email 唯一校验 |
| 普通索引 INDEX | 单纯为了查得快，不约束唯一性 | 给 `status` 加速 |
| 联合索引（复合索引） | 多列组合成一个索引 | 按 `(user_id, status)` 一起查 |

```sql
-- 唯一索引：email 不能重复，且查得快
CREATE UNIQUE INDEX uk_user_email ON t_user (email);

-- 普通索引：经常按 user_id 查任务
CREATE INDEX idx_task_user ON t_task (user_id);

-- 联合索引：经常「某用户的某状态任务，按时间排」
CREATE INDEX idx_task_user_status_time ON t_task (user_id, status, created_at);
```

### 2.5 最左前缀原则（联合索引的命门）

联合索引 `(user_id, status, created_at)` 可以想象成「先按 user_id 排，user_id 相同再按 status 排，再相同才按 created_at 排」——就像 Excel 的多级排序。

**规则：查询必须从最左列开始用，中间不能断，才能用上索引。**

```sql
-- 用得上（命中 user_id, status, created_at 三列）
WHERE user_id = 42 AND status = 'SUCCESS' ORDER BY created_at DESC;

-- 用得上（命中 user_id 一列，剩下两列用不上没关系）
WHERE user_id = 42;

-- 用不上！跳过了最左的 user_id，直接从 status 开始
WHERE status = 'SUCCESS';
```

> 前端类比：联合索引像按 `(姓, 名)` 排好的电话簿。给「姓张」能快速翻到张姓区域；但只给「名字叫伟」，整本簿子从头翻——因为没按「名」排过序。这就是为什么很多人「明明建了索引却没生效」：查询条件没踩到最左列。

### 2.6 覆盖索引（一个白嫖的优化）

如果查询要的列**全都在索引里**，数据库直接从索引拿数据，不用再回主表查一次（省掉「回表」）。

```sql
-- idx_task_user_status_time 覆盖了 user_id, status, created_at
-- 这句只取这三列 → 走索引就够，不回表，飞快
SELECT user_id, status, created_at
FROM t_task
WHERE user_id = 42 AND status = 'SUCCESS';
```

执行计划里会看到 `Extra: Using index`，这就是覆盖索引生效的标志。

### 2.7 什么列该建索引？索引的代价

**该建：** 经常出现在 `WHERE` / `JOIN ON` / `ORDER BY` 里、区分度高（值很分散，如 email、user_id）的列。

**不该乱建：**

- 区分度极低的列（如 `status` 只有 4 个值、性别只有 2 个值），单独建索引几乎没用。
- 几乎不查、只写的列。

**代价（务必记住，索引不是免费的）：**

1. **写变慢**：每次 `INSERT/UPDATE/DELETE`，所有相关索引都要同步维护。索引越多，写入越慢。
2. **占空间**：索引是额外存储，大表的索引可能比数据本身还大。
3. **可能不生效**：在索引列上做运算/函数（如 `WHERE DATE(created_at) = '2026-06-01'`）会让索引失效，要写成 `WHERE created_at >= '2026-06-01' AND created_at < '2026-06-02'`。

> 前端类比：索引像给数组维护多个 `Map`。查得快了，但每次 `push/splice` 都要同步更新每一个 Map，且每个 Map 都占内存。建几个值得的，别乱建。

---

## 第三部分：实操——用 EXPLAIN 看执行计划

光看 SQL 看不出快慢，要让数据库告诉你「它打算怎么执行」。在语句前加 `EXPLAIN` 即可，**它不真的执行，只返回计划**。

**目标**：确认 svc-user 那条「查用户失败任务」的 SQL 到底走没走索引。

**命令**（在 MySQL 客户端 / DBeaver SQL 编辑器里执行）：

```sql
EXPLAIN
SELECT t.id, t.status, u.username
FROM t_task t
INNER JOIN t_user u ON t.user_id = u.id
WHERE t.user_id = 42 AND t.status = 'SUCCESS';
```

**预期输出样例**：

```text
+----+-------------+-------+-------+----------------------------+----------------------------+---------+-------+------+----------+-------------+
| id | select_type | table | type  | possible_keys              | key                        | key_len | ref   | rows | filtered | Extra       |
+----+-------------+-------+-------+----------------------------+----------------------------+---------+-------+------+----------+-------------+
|  1 | SIMPLE      | t     | ref   | idx_task_user_status_time  | idx_task_user_status_time  | 74      | const |   12 |   100.00 | Using where |
|  1 | SIMPLE      | u     | const | PRIMARY                    | PRIMARY                    | 8       | const |    1 |   100.00 | NULL        |
+----+-------------+-------+-------+----------------------------+----------------------------+---------+-------+------+----------+-------------+
```

**怎么读这段输出**（最该看的四列）：

- **`type`：访问方式，从好到坏依次** `const`（主键/唯一索引等值，最快）> `ref`（普通索引等值）> `range`（范围）> `index`（扫整棵索引）> **`ALL`（全表扫描，最差，看到就报警）**。本例 `t` 是 `ref`、`u` 是 `const`，都健康。
- **`key`：实际用到的索引。** 这里 `t` 用了 `idx_task_user_status_time`，`u` 用了 `PRIMARY`。如果这列是 `NULL`，说明**根本没走索引**，要查是不是漏建索引或踩了「最左前缀/函数失效」。
- **`rows`：预计要扫描的行数。** 越小越好。本例只扫 12 行，很好；如果这里是几十万，基本就是慢查询元凶。
- **`Extra`：补充信息。** `Using index` = 覆盖索引（好）；`Using where` = 取出后再过滤（正常）；`Using filesort` / `Using temporary` = 在内存/磁盘额外排序或建临时表（数据量大时是性能警告，常因 `ORDER BY` / `GROUP BY` 没用上索引）。

**反例**——如果把条件改成只用 `status`（违反最左前缀）：

```sql
EXPLAIN SELECT * FROM t_task WHERE status = 'SUCCESS';
```

```text
+----+-------------+--------+------+---------------+------+---------+------+--------+----------+-------------+
| id | select_type | table  | type | possible_keys | key  | key_len | ref  | rows   | filtered | Extra       |
+----+-------------+--------+------+---------------+------+---------+------+--------+----------+-------------+
|  1 | SIMPLE      | t_task | ALL  | NULL          | NULL | NULL    | NULL | 998312 |    10.00 | Using where |
+----+-------------+--------+------+---------------+------+---------+------+--------+----------+-------------+
```

**怎么读**：`type=ALL` + `key=NULL` + `rows=998312`——三连暴击。表示这句**没用任何索引，全表扫了约 100 万行**。结论：要么给 `status` 单独建索引（但它区分度低，意义不大），要么调整查询带上 `user_id` 走联合索引。这就是 `EXPLAIN` 的价值——慢之前就能看出来。

**慢查询日志一句话**：线上不可能每条都手动 `EXPLAIN`。MySQL 的慢查询日志（开启 `slow_query_log`，设 `long_query_time=1` 表示超过 1 秒的查询）会自动记录所有慢 SQL 到日志文件，是定位「哪条 SQL 拖垮数据库」的第一手证据。怎么从一堆日志里捞出元凶，第 [26 章：读懂日志](/back-end/frontend-backend-guide/26-reading-logs) 会专门讲。

---

## 第四部分：N+1 问题——前端最容易踩的 DB 坑

这是前端转后端**最高频**的性能 bug，因为它的代码「读起来完全正常」。

**场景**：svc-canvas 要返回「最近 20 个任务，每个带上提交者用户名」。新手很自然地这么写：

```java
// 反例：N+1 查询
List<Task> tasks = taskMapper.findRecent(20);   // 1 次查询，拿到 20 个任务
List<TaskVO> result = new ArrayList<>();
for (Task task : tasks) {
    // 循环里每个任务都查一次用户！20 个任务 = 20 次查询
    User user = userMapper.findById(task.getUserId());
    result.add(new TaskVO(task, user.getUsername()));
}
return RtData.ok(result);
```

查询次数 = 1（查任务）+ N（每个任务查一次用户）= **N+1 次**。20 条数据就是 21 次数据库往返，列表越长越爆炸。

> 前端类比：你一眼就懂了——这就是在列表渲染里，对每一项各 `await fetch('/user/' + item.userId)`。20 个 item 发 20 个请求，瀑布式串行，页面转圈半天。后端的 N+1 一模一样，只是请求打的是数据库，而且藏在循环里更不显眼。

**修法一：批量查询（IN 查询）。** 先收集所有 user_id，一次性查回来，在内存里拼。

```java
// 正解：2 次查询搞定
List<Task> tasks = taskMapper.findRecent(20);                       // 第 1 次
List<Long> userIds = tasks.stream()
        .map(Task::getUserId).distinct().collect(Collectors.toList());
List<User> users = userMapper.findByIds(userIds);                   // 第 2 次：一次查回所有人
Map<Long, String> nameMap = users.stream()
        .collect(Collectors.toMap(User::getId, User::getUsername)); // 拼成 Map，O(1) 取用
List<TaskVO> result = tasks.stream()
        .map(t -> new TaskVO(t, nameMap.get(t.getUserId())))
        .collect(Collectors.toList());
return RtData.ok(result);
```

对应的 SQL 就是一条 `IN`：

```sql
SELECT id, username FROM t_user WHERE id IN (42, 43, 51, 88);
```

> 前端类比：这正是你优化前端瀑布请求的同款思路——把 20 个 `fetch('/user/' + id)` 换成一个 `fetch('/users?ids=42,43,51,88')` 批量接口，再在前端 `new Map()` 起来按 id 取。无论前端后端，**「循环里发请求」都要警惕，能批量就批量。**

**修法二：JOIN。** 如果数据来自同库，一条 `JOIN` 直接让数据库拼好（见 1.2 节），连内存拼装都省了。N+1 的本质就是「该用一次集合操作的地方，用了 N 次单点操作」。

> 提示：用 ORM/MyBatis 时尤其要小心——一个看似无害的 `task.getUser()` 属性访问，背后可能触发一次懒加载查询，在循环里就成了隐形 N+1。排查时把 SQL 日志打开（看到一串几乎一样、只有 id 不同的 `SELECT` 刷屏，基本就是它），具体方法同样在 [读懂日志](/back-end/frontend-backend-guide/26-reading-logs) 章。

---

## 小结

- **SQL 够用集**：`WHERE`=filter、`ORDER BY`=sort、`LIMIT`=slice、`JOIN`=按 key 合并、`GROUP BY`+聚合=reduce 累加。会这些就能读改业务里大部分查询。
- **索引 = DB 帮你维护的有序 Map（B+ 树）**，让查找从全表扫描（`type=ALL`）变成几次定位。但它让写变慢、占空间，不能乱建。
- **联合索引记最左前缀**：从最左列开始连续用才生效；查询列全在索引里则触发覆盖索引（`Using index`），白嫖性能。
- **`EXPLAIN` 是你的 X 光机**：重点看 `type`（怕 `ALL`）、`key`（怕 `NULL`）、`rows`（怕巨大）、`Extra`（怕 `Using filesort/temporary`）。
- **N+1 是前端最易踩的坑**：循环里查 DB = 列表里逐项发请求。用 `IN` 批量查或 `JOIN` 修掉。

### 自测

1. 联合索引 `(user_id, status, created_at)`，下面哪条查询能用上索引，为什么？
   `WHERE status = 'SUCCESS'` 还是 `WHERE user_id = 42 AND status = 'SUCCESS'`？
2. 一条 `EXPLAIN` 输出 `type=ALL`、`key=NULL`、`rows=900000`，这说明什么？你会先怀疑什么、怎么改？
3. 你在 code review 时看到 `for` 循环里调用了 `userMapper.findById(...)`，这是什么问题？给出至少一种修法，并说出它对应前端的哪个常见反模式。

### 下一章

数据查得快只是第一步；当多个请求同时改同一份数据（比如同时扣配额）时，怎么保证不出错？下一章进入 [事务与一致性](/back-end/frontend-backend-guide/11-transactions-consistency)，讲清楚事务、隔离级别和并发改数据的坑。

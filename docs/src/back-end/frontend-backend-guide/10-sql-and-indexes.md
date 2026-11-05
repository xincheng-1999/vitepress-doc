---
prev:
  text: "B5. 接口契约与 API 设计"
  link: "/back-end/frontend-backend-guide/34-api-design"
next:
  text: "C2. 事务与并发更新"
  link: "/back-end/frontend-backend-guide/11-transactions-consistency"
---

# 数据建模、SQL 与索引：任务列表怎样从数据库取出来

任务页面现在要显示“我的待办”，每页 20 条，最新的排在前面。后台不能先把所有人的任务读到 Java，再过滤出当前用户的那一页。我们要先把任务属于谁存清楚，再让数据库按这个条件查询。

下面就沿着这一次列表查询，看看表怎么设计、SQL 怎么写，以及数据多了以后索引为什么能帮上忙。

[可运行任务 API](./07-build-a-crud-api) 使用 H2 起步。本篇在**独立的 MySQL 8.x / InnoDB 练习库**中增加用户、归属和版本字段，不覆盖初版的 H2 初始化脚本，也不代表初版 API 已实现登录。

## 1. 先准备一个不会碰到原有数据的练习库

还没有 MySQL 时，可以提前使用 [Docker / Compose 课](./23-docker-in-practice) 的数据库部分，不必先学完部署。安装 Docker 与 Compose v2 后，在仓库的 `examples/backend-task-api` 目录执行：

```bash
# 只用本机专用练习口令，不连接公司的真实数据库
export DB_PASSWORD='replace-with-local-app-password'
export MYSQL_ROOT_PASSWORD='replace-with-different-local-root-password'
docker compose up -d mysql
docker compose ps mysql
# 等数据库 healthy 后，进入容器内客户端；-p 后不写口令，按提示交互输入
# 此处 root 仅用于隔离教学库，不给业务应用使用
docker compose exec mysql mysql -u root -p
```

出现 **mysql> 提示符**后，再输入 SQL：

```sql
CREATE DATABASE IF NOT EXISTS tasks_sql_lab CHARACTER SET utf8mb4;
USE tasks_sql_lab;
SELECT DATABASE(), VERSION();
```

当前库应为 `tasks_sql_lab`，容器中的版本是 MySQL 8.4 系列。Compose 原来给 API 准备的库叫 `tasks`，本篇不用它。这样，即使实验里故意触发约束错误，也不会改到 API 的任务。

后面的 SQL 在 mysql 客户端执行，Java 片段在工程中阅读或使用。代码中的 `?` 表示由 JDBC 绑定的参数，不能原样粘贴到 mysql 提示符运行。建表和准备数据的语句只执行一次；重复执行出现“表已存在”或主键冲突时，先确认自己做到了哪一步，不要为重跑去清空其他库。

[事务课](./11-transactions-consistency) 要用两个数据库会话。到时保留这个客户端，另开终端进入同一示例目录，再运行：

```bash
docker compose exec mysql mysql -u root -p tasks_sql_lab
```

两个客户端各有独立连接和事务。数据库没有映射宿主机端口，使用容器内客户端就够了。退出客户端输入 `exit`；结束练习可执行 `docker compose down`，它会保留数据卷，**不要添加删除卷的选项**。已有隔离 MySQL 的读者也可以创建同名库，但不要在生产账号或真实业务库里做实验。

## 2. 要查“我的任务”，先让每条任务知道它属于谁

一个用户可以创建多条任务，所以任务表需要一个 `owner_id`，指向用户表中的用户。查询时就可以沿着这个字段找，而不是在用户表里保存一份不断增长的任务数组。

以后如果一个任务能有多个标签、同一个标签也能属于多个任务，就再用关联表 `task_tags(task_id, tag_id)` 记录配对关系。把标签 ID 拼成逗号字符串，会让查找、修改和约束都变得困难。本篇先建用户和任务两张表，标签关系留待需要时扩展。

![用户与任务一对多，任务与标签经关联表形成多对多；索引为查询维护有序入口](/backend-guide/core-data-model.svg)

在刚才的 `tasks_sql_lab` 中执行：

```sql
CREATE TABLE users (
  id VARCHAR(36) PRIMARY KEY,
  email VARCHAR(254) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tasks (
  id VARCHAR(36) PRIMARY KEY,
  owner_id VARCHAR(36) NOT NULL,
  title VARCHAR(100) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'TODO',
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_tasks_owner FOREIGN KEY (owner_id) REFERENCES users(id),
  CONSTRAINT ck_tasks_status CHECK (status IN ('TODO','DOING','DONE')),
  INDEX idx_tasks_owner_status_created (owner_id, status, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`PRIMARY KEY` 让每条记录有唯一标识，这里用字符串容纳 UUID。`owner_id` 的外键要求对应用户确实存在，避免出现一条不知道属于谁的任务。状态的 `CHECK` 则把值限制在 `TODO`、`DOING`、`DONE` 中；某个用户能不能完成这条任务，仍由应用检查。

邮箱的唯一约束尤其重要。应用即使先查“这个邮箱还没注册”，两个请求也可能同时查到没有，再各插一条。数据库的唯一约束才会在并发写入时阻止重复。邮箱是否忽略大小写、写入前如何规范化，还要与数据库排序规则一起确定。

表中另有两个以后会用到的字段：`version` 用于检查并发更新，下一篇会实际使用；`updated_at` 要由更新 SQL 显式维护。时间字段还应统一时区和精度，避免前端把同一个时间点解释成另一时间。密码字段只存专用密码哈希，具体做法见 [安全](./32-security)，不用明文、可逆加密或裸 MD5。

如果是在已有业务上加用户，事情比新建表多一步：先迁移历史任务、核实它们的真实归属，再补非空约束。给所有历史任务随意填一个管理员 ID，会把未知归属伪装成正确数据。

## 3. 从写入开始，把用户输入当成值

创建任务时，服务器已经通过登录拿到了当前用户身份。它把身份、标题和任务 ID 作为参数传给数据库：

```sql
-- 这里用 ? 表示预编译参数；在 JDBC 中绑定，不是直接贴进 SQL 控制台运行
INSERT INTO tasks(id, owner_id, title, status) VALUES (?, ?, ?, 'TODO');
UPDATE tasks SET title = ?, updated_at = CURRENT_TIMESTAMP(6)
WHERE id = ? AND owner_id = ?;
DELETE FROM tasks WHERE id = ? AND owner_id = ?;
```

更新、删除都带 `owner_id`，因为“知道任务 ID”不能成为修改别人的任务的理由。这个值来自**服务端已认证身份**，不采用请求体里自报的 ownerId。执行后要检查影响行数；为 0 时，再按接口约定处理不存在、无权限或版本冲突，必要时统一隐藏资源是否存在。

Java 中的参数绑定可以这样读：SQL 的结构固定，标题等数据单独交给驱动。

```java
// 对：值作为参数绑定
jdbc.update("UPDATE tasks SET title = ? WHERE id = ? AND owner_id = ?", title, id, ownerId);
// 错：拼接数据可能导致 SQL 注入
// "... WHERE title = '" + title + "'"
```

这样即使标题里带单引号，它仍是标题，不会变成 SQL 的一部分。排序列名和 `ASC`、`DESC` 通常不能当普通值绑定；若页面允许选择排序，由服务端把选项映射到白名单中的固定 SQL 片段。

## 4. 再把“我的待办，最新 20 条”写成查询

```sql
SELECT id, title, status, created_at
FROM tasks
WHERE owner_id = '已认证用户的UUID' AND status = 'TODO'
ORDER BY created_at DESC, id DESC
LIMIT 20 OFFSET 0;
```

先由 `WHERE` 限定用户和状态，再由 `ORDER BY` 确定顺序，最后用 `LIMIT` 取这一页。这些工作在数据库完成，应用只接收页面需要的字段。

为什么时间后面还要加 `id`？因为两条任务可能有相同的创建时间。只按时间排，它们谁在前面没有确定答案；加上唯一 ID 后，同一份数据就有稳定的排列。这里的 UUID 用于打破并列，不代表创建先后。

`OFFSET` 表示跳过前面多少条。翻到很后面时，数据库通常仍要扫描并跳过大量记录。如果产品主要是“继续加载下一页”，可以改为记录上一页最后一条的时间和 ID，下一次从它后面取：

```sql
SELECT id, title, status, created_at FROM tasks
WHERE owner_id = ? AND status = 'TODO'
  AND (created_at < ? OR (created_at = ? AND id < ?))
ORDER BY created_at DESC, id DESC LIMIT 20;
```

这就是游标分页的一种写法。游标要与用户范围、筛选条件和排序规则配套；把它编码成 base64 不会使它保密。翻页过程中仍可能有新增、删除和修改，如果需要整个浏览过程看到同一份数据，还要另行设计快照。

## 5. 查询条件明确后，才知道索引该怎样建

刚才建表时有一条索引 `(owner_id, status, created_at, id)`。可以把它看成数据库额外维护的一份有序目录：先按用户分组，同一用户里再按状态分组，之后按时间和 ID 排列。查询某人的 TODO 任务时，就有机会直接找到相应范围，而不必逐行检查整张表。

InnoDB 常见的这种索引采用 B+ 树结构，适合有序查找和范围读取。它不是内存哈希 Map，不能用“永远 O(1)”来解释性能。

列的顺序会影响使用方式。只有 `status = ?` 而没有用户条件时，通常不能沿最左侧字段有效定位；优化器是否选择索引扫描或 skip scan，要看查询和数据。如果只按用户查、跳过状态，却要求直接按时间排，这条索引也未必能省去排序。

查到索引记录后，还可能需要回到主键记录读取标题等字段，这一步叫**回表**。如果所需字段全部在索引里，就可以直接取齐，称为**覆盖索引**。不是把所有列塞进索引就好：索引会占空间，每次新增、删除和修改还要维护它。随机 UUID 也会增加索引空间和写入局部性的代价，规模变大后再评估 ID 方案。

## 6. 用一组小数据看查询，再看执行计划

下面的记录只供 SQL 练习，哈希字符串不是登录凭据。它们使用单独的 ID，不与下一篇事务课的记录重名。在 mysql 客户端执行一次：

```sql
INSERT INTO users(id, email, password_hash) VALUES
  ('sql-user-a', 'sql-a@example.invalid', 'sql-only-not-a-login-hash'),
  ('sql-user-b', 'sql-b@example.invalid', 'sql-only-not-a-login-hash');
INSERT INTO tasks(id, owner_id, title, status) VALUES
  ('sql-task-a1', 'sql-user-a', '学习 SQL', 'TODO'),
  ('sql-task-a2', 'sql-user-a', '整理笔记', 'DONE'),
  ('sql-task-b1', 'sql-user-b', '准备报表', 'TODO'),
  ('sql-task-b2', 'sql-user-b', '复查报表', 'TODO');
SELECT id, title, status FROM tasks
WHERE owner_id = 'sql-user-a'
ORDER BY created_at DESC, id DESC;
```

应只得到 A 的两条任务。把用户条件换成 `sql-user-b`，就得到 B 的两条。接着故意使用已有邮箱：

```sql
INSERT INTO users(id, email, password_hash)
VALUES ('sql-user-duplicate', 'sql-a@example.invalid', 'sql-only-not-a-login-hash');
```

预期被唯一约束拒绝。这个错误不会新增用户；它也让我们看到，约束不只是应用中的一句判断。

再看数据库打算如何查列表：

```sql
EXPLAIN
SELECT id, title, status, created_at FROM tasks
WHERE owner_id = '某个存在的用户ID' AND status = 'TODO'
ORDER BY created_at DESC, id DESC LIMIT 20;
```

执行时把用户 ID 换成刚才的 `sql-user-a`。读计划先看 `key` 选了哪个索引，再看 `rows` 估计要检查多少行，以及 `Extra` 是否出现额外排序。常用字段留在这里查阅：

| 字段 | 含义 |
| --- | --- |
| `key` | 选择的索引；NULL 时要结合表大小与查询判断 |
| `type` | 访问方式，如 ref、range、ALL；小表全扫可能更划算 |
| `rows` | 优化器估计扫描行数，不是实际测量 |
| `Extra` | 覆盖、过滤、排序等信息；filesort 未必写磁盘 |

只有四条数据时，即使计划选择全表扫描，也很正常。想比较时，可以对同一条 SELECT 分别执行普通 `EXPLAIN`，以及在 `FROM tasks` 后加 `IGNORE INDEX (idx_tasks_owner_status_created)` 的 `EXPLAIN`，观察计划是否变化。它们只是观察手段；有代表性的数据量和分布，才足以判断索引收益。低区分度的状态列，在稀少状态、联合索引和覆盖查询中也可能有用，不能只凭字段名下结论。

**`EXPLAIN ANALYZE` 会实际执行查询。** 本地练习可以使用，昂贵查询应先在授权的测试环境评估，不直接拿生产试验，也不为了计划“好看”强制走索引。

## 按需补充

### 列表需要用户信息或状态数量时

同库数据可以通过 JOIN 取齐，不必查出 20 条任务后，再循环发 20 次用户查询：

```sql
SELECT t.id, t.title, u.email
FROM tasks t JOIN users u ON u.id = t.owner_id
WHERE t.owner_id = ?;

SELECT status, COUNT(*) AS total
FROM tasks WHERE owner_id = ?
GROUP BY status;
```

JOIN 按匹配关系输出行。一对多关系可能让一条任务出现多行，分页和计数都要按实际关系处理。统计时先用 WHERE 限定有权读取的数据，再 GROUP BY；需要过滤统计结果时用 HAVING。

也不要用无限制的 `findAll()` 把百万行拉进内存。普通列表限制页大小，大导出分批读取或交给后台任务。

### 把初版 H2 切到 MySQL 时

[Docker 课](./23-docker-in-practice) 先让原来小表结构的 API 连接 MySQL，再考虑增加本篇的用户、归属和版本字段。要逐项核对驱动、连接账号与权限、字符集、时区、时间精度、约束、查询计划和并发行为，并准备备份。

示例的 `application-mysql.yml` 是本地实验配置。真实业务升级表结构时，用 Flyway、Liquibase 等工具记录迁移版本，不依赖 `ddl-auto=update`，也不让应用每次启动随意修改生产表。外键若改由应用维护，也要明确相应的关系检查和修复责任。

主线下一篇：[C2 · 事务与并发更新](./11-transactions-consistency)。接下来把两次写入放在一起，看看其中一步失败时会发生什么。

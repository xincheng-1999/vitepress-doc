---
prev:
  text: "B5. 接口契约与 API 设计"
  link: "/back-end/frontend-backend-guide/34-api-design"
next:
  text: "C2. 事务与并发更新"
  link: "/back-end/frontend-backend-guide/11-transactions-consistency"
---

# 数据建模、SQL 与索引：把页面变成数据

> 学习目标：设计用户与任务表，写出安全的增删改查，理解约束、索引和执行计划。不是把整个列表拉到 Java 里 filter。

前置：[可运行任务 API](./07-build-a-crud-api)。第一版的 H2 是起点，本章的表设计和 SQL 以 **MySQL 8.x / InnoDB** 为准，是扩展练习，不是直接覆盖 H2 的初始化脚本。

## 实验准备：先有一个独立 MySQL 库

还没有 MySQL？这里把 [Docker / Compose 课](./23-docker-in-practice) 的数据库部分提前使用，不必先完成部署全章。安装 Docker 与 Compose v2 后，在仓库的 `examples/backend-task-api` 目录执行：

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

在出现的 **mysql> 提示符**中执行：

```sql
CREATE DATABASE IF NOT EXISTS tasks_sql_lab CHARACTER SET utf8mb4;
USE tasks_sql_lab;
SELECT DATABASE(), VERSION();
```

预期当前库为 `tasks_sql_lab`，版本为 MySQL 8.4 系列。**本章目标表全部建在这里，不覆盖 Compose 初始 API 使用的 `tasks` 库。** 后面的 SQL 代码贴在 mysql 客户端，Java 代码贴在工程，不要混贴；`?` 代表 JDBC 参数的片段不是直接可运行 SQL。

需要 [事务课](./11-transactions-consistency) 的两个会话时，保留第一个终端，再开一个终端进入同一示例目录：

```bash
docker compose exec mysql mysql -u root -p tasks_sql_lab
```

两个客户端各有独立连接和事务。数据库没有映射宿主端口，用容器内客户端就能学习。退出客户端用 `exit`；练习结束 `docker compose down` 保留卷，不加会删卷的选项。若已有自己的隔离 MySQL，可直接创建同名练习库；**不在生产账号或真实业务库里做这些实验**。

## 1. 数据建模先问业务，不先写 Entity

需求：用户能创建自己的任务，任务可带多个标签。

| 关系 | 应该怎么存 | 不建议 |
| --- | --- | --- |
| 用户 → 任务，一对多 | 任务表存 `owner_id` 外键 | 在用户表塞一个无限增长的 JSON 任务数组 |
| 任务 ↔ 标签，多对多 | 关联表 `task_tags(task_id, tag_id)` | 用逗号拼标签 ID 再到处 LIKE |
| 任务当前状态 | 有限枚举语义 + 受控更新 | 随意接受任意字符串 |
| 创建与更新时间 | 明确 UTC / 时区与精度 | 前后端各猜日期格式 |

![用户与任务一对多，任务与标签经关联表形成多对多；索引为查询维护有序入口](/backend-guide/core-data-model.svg)

第一版任务无用户，增加身份后要做迁移、回填归属、再添加非空约束；**不能给所有历史任务胡乱填一个管理员 ID 当作完成设计**。

## 2. 一组可以讨论的表结构

下面是**第二阶段目标结构**，与第一版小表不同，请在新建练习库或通过迁移升级，不要把它当生产覆盖脚本：

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

- **主键**定位一条记录；UUID 便于从字符串 ID 起步，但随机 UUID 索引局部性、空间开销有代价，大规模再评估其他 ID 方案。
- **唯一约束**才是并发下 email 不重复的最终保障，`先查存在 → 再插入` 会竞态。邮箱规范化、大小写与数据库排序规则要由业务明确。
- **外键**保护引用关系；某些团队不用外键而在应用维护，但这不是「数据库会自动替我保持关系」。
- **CHECK**限制值域，不代替用户权限与工作流校验。
- `version` 为后面乐观更新预留；`updated_at` 由更新 SQL 显式维护。
- 密码只存密码哈希，绝不明文、可逆加密或裸 MD5，见 [安全](./32-security)。

## 3. SQL 够用集：让数据库就地处理

### 写入与参数绑定

```sql
-- 这里用 ? 表示预编译参数；在 JDBC 中绑定，不是直接贴进 SQL 控制台运行
INSERT INTO tasks(id, owner_id, title, status) VALUES (?, ?, ?, 'TODO');
UPDATE tasks SET title = ?, updated_at = CURRENT_TIMESTAMP(6)
WHERE id = ? AND owner_id = ?;
DELETE FROM tasks WHERE id = ? AND owner_id = ?;
```

归属条件由**已认证身份**得到，不能相信 body 的 ownerId。检查影响行数：0 可能是不存在、无权限或版本冲突，需要按你的接口策略区分 / 隐藏资源存在性。

```java
// 对：值作为参数绑定
jdbc.update("UPDATE tasks SET title = ? WHERE id = ? AND owner_id = ?", title, id, ownerId);
// 错：拼接数据可能导致 SQL 注入
// "... WHERE title = '" + title + "'"
```

列名、排序方向通常不能作为普通值绑定；客户端指定排序时，用服务器**白名单映射**，不直接拼用户输入。

### 列表与稳定分页

```sql
SELECT id, title, status, created_at
FROM tasks
WHERE owner_id = '已认证用户的UUID' AND status = 'TODO'
ORDER BY created_at DESC, id DESC
LIMIT 20 OFFSET 0;
```

`WHERE` 类似过滤、`ORDER BY` 类似排序、`LIMIT` 类似截取，但执行工作在数据库。排序加唯一的 `id` 作为同时间记录的 tie-breaker，避免结果顺序不确定。

深分页越大，通常需要扫描并跳过更多记录。游标分页改为携带上一页末尾的 `(created_at,id)`：

```sql
SELECT id, title, status, created_at FROM tasks
WHERE owner_id = ? AND status = 'TODO'
  AND (created_at < ? OR (created_at = ? AND id < ?))
ORDER BY created_at DESC, id DESC LIMIT 20;
```

游标还需绑定筛选条件和权限，不能因为编码成 base64 就当作加密。可变数据下分页也不天然提供全程快照。

### JOIN 与聚合

```sql
SELECT t.id, t.title, u.email
FROM tasks t JOIN users u ON u.id = t.owner_id
WHERE t.owner_id = ?;

SELECT status, COUNT(*) AS total
FROM tasks WHERE owner_id = ?
GROUP BY status;
```

JOIN 会为匹配关系输出行，不是 JS `find` 只找第一条；一对多连接可能让主表行重复，分页和 count 要注意。聚合先通过 WHERE 限定权限数据，再 GROUP BY；HAVING 过滤聚合结果。

## 4. 索引是什么，为什么不是越多越好

InnoDB 常用 B+ 树索引。可以把它理解为额外维护的有序目录，帮助等值定位、范围查找和部分排序；**不是哈希 Map 的 O(1) 查找**。

联合索引 `(owner_id, status, created_at, id)` 按多列顺序组织：先找 owner，再找状态范围，再按时间和 ID 排。

- `owner_id = ? AND status = ?` 通常能有效缩小连续范围。
- 只有 `status = ?` 时，通常不能按最左前缀有效定位；是否发生索引扫描、skip scan 等由优化器和查询决定，不能绝对说「完全用不上」。
- 只按 owner 查、跳过 status 再按时间排序，并不保证这个索引还能免排序，可能需要另一条符合查询形态的索引。
- 非主键索引叶子常携带主键，取其他列可能需要**回表**；所需列都能从索引取得叫**覆盖索引**。

每个索引都占空间、增加写入维护成本。低区分度字段也不是永远没价值：分布极不均匀、稀少状态筛选、联合索引或覆盖场景仍可能有用。根据真实查询验证。

## 5. EXPLAIN：看数据库打算怎么做

```sql
EXPLAIN
SELECT id, title, status, created_at FROM tasks
WHERE owner_id = '某个存在的用户ID' AND status = 'TODO'
ORDER BY created_at DESC, id DESC LIMIT 20;
```

| 列 | 怎么解读 |
| --- | --- |
| `key` | 选了哪个索引，NULL 不等于一定有故障 |
| `type` | 访问方式，如 ref / range / ALL；小表全扫可能更划算 |
| `rows` | 估计扫描行数，不是真实测量 |
| `Extra` | 是否覆盖、额外过滤、排序等；filesort 不意味着一定写磁盘 |

小表可能不选索引，别为了得到「漂亮输出」强制索引。先造合理的数据量与分布，再看延迟、扫描量和写入成本。

普通 `EXPLAIN SELECT` 主要展示计划；**`EXPLAIN ANALYZE` 会实际执行查询**，先在授权的测试环境使用，别拿昂贵查询直接试生产。

## 6. 三个真正会伤害系统的做法

1. **N+1**：查 20 条任务后循环每条查用户。改为同库 JOIN 或两次批量查询；不是直接开 20 个线程冲数据库。
2. **无限查询**：`findAll()` 把百万行拉进内存。限制页大小，导出用分批 / 流式方式与后台任务。
3. **应用校验代替约束**：只做 `exists` 再插入，在并发下仍重复。唯一约束、正确事务与冲突处理缺一不可。

## 7. 从 H2 迁到 MySQL，要做的不止改 URL

[Docker 课](./23-docker-in-practice) 有第一版 API + MySQL 的 Compose，先迁移同结构的小表。再在扩展分支增加本章的 users / owner / version。

要检查：驱动、连接地址和账号权限、字符集、时区、时间往返、约束行为、DDL 迁移、执行计划、并发锁、数据备份。第一版提供 `application-mysql.yml` 只是本地实验；生产引入 **Flyway / Liquibase** 等版本化迁移，不用 `ddl-auto=update` 或每次启动改生产表碰运气。

## 动手与自测

在独立练习库完成：两位用户各插入两条任务 → 按 owner 查询 → 尝试重复 email → 执行 EXPLAIN → 对比有无合适索引。

**预期**：每人只看到自己的两条；重复 email 被数据库拒绝；能解释计划而不是记住固定输出。

1. 前端传 ownerId 是否可作为授权依据？**不可以，只能用服务端验证后的身份。**
2. 加了事务能否替代唯一约束？**不能，事务也需要约束与合适的并发控制。**
3. 有索引一定快吗？**不一定，查询形态、数据量、回表和写入成本都影响收益。**

下一站：[事务、并发更新与一致性](./11-transactions-consistency)。

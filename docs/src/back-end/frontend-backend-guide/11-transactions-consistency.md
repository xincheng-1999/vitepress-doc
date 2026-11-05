---
prev:
  text: "C1. 数据建模、SQL 与索引"
  link: "/back-end/frontend-backend-guide/10-sql-and-indexes"
next:
  text: "C3. 登录、安全与数据归属"
  link: "/back-end/frontend-backend-guide/32-security"
---

# 事务与并发更新：任务保存了一半怎么办

用户把任务标为完成，后台需要做两次写入：修改任务状态，再记录一次完成操作。第一步成功、第二步失败时，如果数据库留下了 DONE 状态却没有记录，这次操作就只完成了一半。

我们先用事务解决这件事。之后再看一个容易混淆的问题：两个人同时保存同一条任务，即使各自都有事务，为什么仍可能互相覆盖？

本篇接着 [任务 API](./07-build-a-crud-api) 和 [SQL 与数据模型](./10-sql-and-indexes) 学习，实验使用 MySQL InnoDB。初版 H2 测试不能代替这里的多连接、锁和隔离实验。

## 1. 先把两次写入放进同一个事务

按 [SQL 课的独立库与两个客户端入口](./10-sql-and-indexes)，进入 `tasks_sql_lab`，并确认已经建好 users 和 tasks。下面只准备本篇使用的记录与审计表，不修改原 API 的 `tasks` 库。哈希字符串只是 SQL 占位，不是可登录凭据。

在第一个 mysql 客户端执行一次：

```sql
USE tasks_sql_lab;
INSERT INTO users(id, email, password_hash)
VALUES ('user-lab', 'lab@example.invalid', 'sql-only-not-a-login-hash');
INSERT INTO tasks(id, owner_id, title, status)
VALUES ('task-a', 'user-lab', '旧标题', 'TODO');
CREATE TABLE task_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id VARCHAR(36) NOT NULL,
  action VARCHAR(32) NOT NULL
) ENGINE=InnoDB;
```

重复准备会触发主键冲突或表已存在，先检查记录，不要为了重跑随意清数据。现在执行一次正常的完成操作：

```sql
START TRANSACTION;
UPDATE tasks SET status = 'DONE' WHERE id = 'task-a';
INSERT INTO task_audit(task_id, action) VALUES ('task-a', 'COMPLETE');
COMMIT;
```

`START TRANSACTION` 开始一组尚未提交的修改；`COMMIT` 才把它们一起提交。查询应能看到任务为 DONE，并有一条 COMPLETE 记录：

```sql
SELECT id, title, status FROM tasks WHERE id = 'task-a';
SELECT task_id, action FROM task_audit WHERE task_id = 'task-a';
```

接下来故意让第二步失败。逐句执行，看到 INSERT 报错后仍要继续执行这里的 **ROLLBACK，而不是 COMMIT**：

```sql
START TRANSACTION;
UPDATE tasks SET status = 'DOING' WHERE id = 'task-a';
-- action 不允许 NULL，这一句预期报错
INSERT INTO task_audit(task_id, action) VALUES ('task-a', NULL);
ROLLBACK;
SELECT id, title, status FROM tasks WHERE id = 'task-a';
SELECT task_id, action FROM task_audit WHERE task_id = 'task-a';
```

任务应回到本次事务之前的 DONE，审计仍只有原来那条 COMPLETE。这就是回滚：撤销本事务尚未提交的修改，而不是把任务恢复成刚建库时的 TODO。

mysql 客户端里一条 SQL 报错，**通常不会替你把整个事务回滚**。如果忽略错误继续 COMMIT，前面的修改仍可能被提交。应用也要遵守同样的原则：某一步失败，就让这组业务修改回滚，不吞掉错误后照常返回成功。

## 2. 在 Spring 中，让事务包住完整的一次业务操作

对应的 Service 方法可以这样表达：

```java
@Transactional(rollbackFor = Exception.class)
public void completeTask(String taskId) {
    taskRepository.complete(taskId);
    auditRepository.append(taskId, "COMPLETE");
}
```

这段是示意：方法由另一个 Bean 调用，Spring 的事务代理在外面负责开始事务，方法正常结束后提交，符合回滚策略的异常则触发回滚。两个 Repository 还必须使用同一个事务所管理的数据源。

`rollbackFor = Exception.class` 表示这次连受检异常也按回滚处理。Spring 默认通常对 RuntimeException 和 Error 回滚，受检异常要按业务确定，别给所有方法机械照贴。

为什么有时加了注解却没得到预想结果？先检查调用有没有经过代理。自己 `new TaskService(...)`，或在同一个类里用 `this.completeTask()` 调用，都不会因为这个调用自动经过事务拦截；如果原本已有外层事务，则还要看外层范围。方法里 catch 异常后正常返回，也可能让事务提交；某些数据库错误已经把事务标记为只能回滚，后续还可能在提交时失败。

事务通常绑定在当前线程上。新线程不会自动继承它，Redis 和 HTTP 调用也不会因为写在同一方法里就纳入 MySQL 事务。方法里打印“成功”还早了一步：代理在方法返回后才提交，提交本身也可能失败。

还有一个实际代价：事务越长，连接和锁可能占用越久。不要在其中等待一分钟的邮件或外部 HTTP 请求。先缩小数据库事务范围，再为外部动作设计恢复办法，后面会继续说到。

## 3. 有了事务，为什么还会覆盖别人的更新

现在两个标签页都打开了同一条任务。A 改标题，B 标为完成，但 B 送回的表单仍带着旧标题：

```text
A 读 version=0 → 改标题为「新标题」
B 读 version=0 → 标记 DONE，同时带着旧标题
A 保存成功
B 保存成功 → A 的标题被覆盖
```

每次保存都可以完整提交，数据库却不知道 B 用的是旧页面内容。初版 API 的普通 PUT 就有这个限制。要识别它，需要让请求说明“我是在第几个版本上修改的”。

表中的 `version` 正是为此准备的。读取任务时带回版本，更新时把它写进条件：

```sql
UPDATE tasks
SET title = ?, status = ?, version = version + 1,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE id = ? AND owner_id = ? AND version = ?;
```

A 拿版本 0 更新成功后，数据库里的版本变为 1。B 仍用版本 0，条件就匹配不到，影响行数为 0，应用可以提示“内容已变化，请重新读取”。这叫**乐观并发控制**：先允许大家编辑，到保存时检查是否有人先改过。

影响 0 行也可能是任务不存在或不属于当前用户，所以要按资源可见性和接口契约判断。版本冲突常用 409；如果采用 HTTP 的 ETag 与 `If-Match` 协议，前置条件失败通常返回 412。前端遇到冲突后应重新读取、合并或请用户确认，而不是把旧数据自动重试到成功。

## 4. 用两个客户端亲自看见未提交修改和版本冲突

以下继续使用刚才的 `task-a`，不要重新执行建表。打开 SQL 课说明的第二个客户端，两个窗口都确认当前库：

```sql
USE tasks_sql_lab;
SELECT DATABASE(), @@session.transaction_isolation;
SELECT id, title, status, version FROM tasks WHERE id = 'task-a';
```

如果按顺序首次练习，标题还是“旧标题”，状态 DONE，版本 0。先在**会话 A**执行，暂不提交：

```sql
START TRANSACTION;
UPDATE tasks SET title = '尚未提交的标题' WHERE id = 'task-a';
SELECT id, title, status, version FROM tasks WHERE id = 'task-a';
```

A 能看见自己的修改。切到**会话 B**，在默认自动提交状态下查询：

```sql
SELECT id, title, status, version FROM tasks WHERE id = 'task-a';
```

MySQL InnoDB 常见的默认 Repeatable Read 下，B 仍看到“旧标题”，没有读到 A 未提交的数据。回到 **A**：

```sql
ROLLBACK;
SELECT id, title, status, version FROM tasks WHERE id = 'task-a';
```

再在 **B**执行同一条 SELECT，两边都应是“旧标题”。如果自行改过隔离级别，要结合实际级别解释结果。

接着模拟两个页面都已经读到版本 0。保持两边没有未结束事务；**A、B 各执行一次**：

```sql
SELECT id, title, version FROM tasks WHERE id = 'task-a';
```

先在 **A**保存：

```sql
UPDATE tasks
SET title = 'A 保存的新标题', version = version + 1,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE id = 'task-a' AND owner_id = 'user-lab' AND version = 0;
```

预期影响 1 行。再在 **B**用它之前读到的同一个旧版本保存：

```sql
UPDATE tasks
SET title = 'B 页面里的旧标题', status = 'DONE', version = version + 1,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE id = 'task-a' AND owner_id = 'user-lab' AND version = 0;
SELECT id, title, status, version FROM tasks WHERE id = 'task-a';
```

B 应影响 0 行，最终标题仍是“A 保存的新标题”，版本为 1。这个结果才说明旧页面没有覆盖成功。重做时先查当前版本，用两边共同读到的旧版本重复实验；不要一直照抄 0 后把两边都失败误判成保护有效。

## 5. 请求重试，又是另一个问题

版本检查解决的是两个编辑互相覆盖。如果用户提交“创建导出任务”，数据库提交成功，响应却在网络中丢失，重试会不会再创建一份？这需要**幂等处理**，不能靠上面的事务或版本号自动解决。

一种做法是为同一次业务意图提供幂等键，服务器通过 `(actor_id, idempotency_key)` 的唯一约束记录请求摘要、执行状态和结果引用。第一次执行并记录结果；同一键、同一内容再来，返回原结果或处理中状态；同一键却换了内容，则拒绝冲突。客户端在超时重试时复用原键，新的用户操作才用新键。

记录幂等结果和修改业务数据，应尽量在同一个数据库事务内提交。还要确定记录保留多久、处理中崩溃怎样恢复、并发请求由谁执行。单独 Redis `SETNX` 一个标记，随后业务失败，可能留下“标记说做过、实际没做”的空缺。

现在可以区分三件事了：事务使本次几步写入一起提交；版本条件防止旧数据覆盖新数据；幂等记录防止同一操作重试后额外生效。前端禁用按钮和回退乐观 UI 可以改善体验，却不能代替这些服务端规则。

![本地事务提交边界、并发版本冲突与跨服务副作用的不同范围](/backend-guide/core-transaction.svg)

## 6. 任务已提交，邮件却失败了怎么办

数据库和邮件服务器是两个地方。先发邮件再提交，可能邮件已发出但数据库回滚；先提交再发邮件，又可能任务已完成但邮件没发出去。本地事务到这里就不能把两者一起撤销了。

如果后续动作本来就能放在同一应用、同一库里，先用本地事务完成。确实需要向外发送时，可以在保存任务的同一个事务里，再保存一条“待发送事件”，后台根据这条记录继续投递，这种做法叫 **outbox**。投递可能重试，所以消费者仍要幂等，详见 [消息与任务](./33-mq-reliability)。

说“最终一致”，也就意味着要有人负责失败重试、状态恢复、对账和必要的补偿，而不是返回成功后假设邮件迟早会自己发出。下一步可以用 [测试](./35-testing) 故意制造审计失败，检查任务是否也回滚，而不只检查有没有异常。

## 按需补充

### 还有哪些并发控制方法

库存或配额扣减，常能直接写成原子条件更新：

```sql
UPDATE quota SET remaining = remaining - ?
WHERE user_id = ? AND remaining >= ?;
```

扣减量由服务端按业务确定，必须为正且在允许范围内；`remaining - ?` 和 `remaining >= ?` 两处绑定同一个已校验的扣减量，不能接受负数反向增额。只有余额足够才扣减，应用检查影响行数，再在同一事务写流水。先在 Java 查询并判断、随后无条件扣减，会留下并发窗口，不必为这种同库规则先引入 Redis 分布式锁。

需要先锁住记录再检查时，可以在事务内使用 `SELECT ... FOR UPDATE`，称为悲观锁。它可能等待或死锁，范围还受索引和隔离级别影响，应针对具体冲突使用，而不是给所有查询加锁。

### 隔离级别与 ACID，遇到时怎样理解

刚才 B 看不见 A 未提交的修改，涉及“隔离性”。读取别人的未提交数据叫脏读；同一事务再次读取同一行却看到新的已提交值，叫不可重复读；重复范围查询时符合条件的行集合变化，通常讨论为幻读。丢失更新和写偏差还涉及业务的读后写规则，不能只背这几个词就当作已经防住。

常见隔离级别包括 Read Uncommitted、Read Committed、Repeatable Read、Serializable。可以查询当前连接：

```sql
SELECT @@session.transaction_isolation;
```

InnoDB 的普通一致性读通过 MVCC，也就是多版本并发控制，读取适合当前隔离级别的版本；锁定读和写还有各自的锁机制。Repeatable Read 下，部分范围操作受间隙锁或 next-key 锁保护，但业务并发问题仍要结合具体 SQL 分析。Serializable 也不是所有事务简单从头到尾排成一队。选择级别时应连同约束、条件更新和实际负载一起验证。

事务的 ACID 可以对应到本篇实验：原子性让修改整体提交或回滚；一致性依赖正确业务逻辑和约束保持规则；隔离性控制并发可见性与冲突；持久性则依数据库日志、刷盘配置和故障条件保存提交结果。数据库不会凭空理解所有业务规则，持久性也要有相应配置与恢复方案。

### 跨服务时遇到 CAP

CAP 讨论网络分区时，一致性和可用性受到的约束，不是任意系统随便“三选二”。权限、库存、订单唯一性等操作都可能有很高的正确性要求，要逐操作选择策略，而不是给整个互联网业务统一贴 AP 标签。

主线下一篇：[C3 · 登录、安全与数据归属](./32-security)。前面的 SQL 已经带上 owner 条件，接下来看看服务器凭什么相信这个 owner。

---
prev:
  text: "C1. 数据建模、SQL 与索引"
  link: "/back-end/frontend-backend-guide/10-sql-and-indexes"
next:
  text: "C3. 登录、安全与数据归属"
  link: "/back-end/frontend-backend-guide/32-security"
---

# 事务、并发更新与一致性

> 学习目标：区分「几步一起提交」「两个请求不能互相覆盖」「重试不重复生效」三个不同问题，并实际复现回滚和更新冲突。

前置：[任务 API](./07-build-a-crud-api)、[SQL 与数据模型](./10-sql-and-indexes)。本章默认 MySQL InnoDB；H2 的测试不能替代 MySQL 的锁与隔离实验。

## 1. 先分清三种保证

| 要解决的问题 | 对应工具 | 不会自动保证 |
| --- | --- | --- |
| 更新任务 + 写审计，要么一起成功要么不生效 | 同库本地事务 | 两次请求不会重复执行 |
| 两个用户改同一条记录，不能静默覆盖 | 条件更新、版本号、锁 | 多个外部服务一起回滚 |
| 响应丢失后重试，不再额外创建 / 扣款 | 业务幂等键、唯一约束与结果记录 | 消息永不丢失、无限期去重 |

`Promise.all` 不会撤销已经成功的副作用；前端乐观 UI 回滚也不等于数据库事务。类比只能帮助理解「一组动作」，不能直接替代保证。

![本地事务提交边界、并发版本冲突与跨服务副作用的不同范围](/backend-guide/core-transaction.svg)

## 2. 本地事务：让一组 SQL 一起提交

先按 [SQL 课的独立库与两个客户端入口](./10-sql-and-indexes) 进入 `tasks_sql_lab`，建好 users / tasks 目标表。只在该库准备教学记录和审计表（这里的哈希字符串只是 SQL 占位，不是可用登录凭据）：

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

这些准备语句只执行一次，重复执行触发主键冲突不是实验失败；不要为重跑随意清真实数据。然后验证「修改任务后必须写审计」：

```sql
START TRANSACTION;
UPDATE tasks SET status = 'DONE' WHERE id = 'task-a';
INSERT INTO task_audit(task_id, action) VALUES ('task-a', 'COMPLETE');
COMMIT;
```

可以把审计 action 改为 NULL 制造约束失败，随后手动 `ROLLBACK`，再查标题应回到事务前的值。**mysql 客户端的一条 SQL 报错，不等于整个事务已经自动回滚**；不要错误后继续 COMMIT。应用中应让失败触发事务回滚，而不是吞掉异常继续提交。本地事务涵盖同一个事务资源中的操作，不能把 HTTP、邮件、对象上传也自动撤销。

ACID 用途：

- **A 原子性**：事务中的修改整体提交或回滚。
- **C 一致性**：在正确业务逻辑和约束下，状态转换保持不变量；事务不会凭空理解「余额不能负」。
- **I 隔离性**：控制并发事务之间可见和冲突的行为，不是每一级都像完全独占。
- **D 持久性**：提交后的数据按数据库配置、日志刷盘和故障模型保持；不是忽略硬件和配置的绝对永不丢。

## 3. Spring 里为什么加了注解还可能不对

```java
@Transactional(rollbackFor = Exception.class)
public void completeTask(String taskId) {
    taskRepository.complete(taskId);
    auditRepository.append(taskId, "COMPLETE");
}
```

示意方法由另一个 Bean 调用，通过 Spring 事务代理运行。`rollbackFor` 是策略选择：默认通常对 RuntimeException / Error 回滚，受检异常需按业务明确，不是所有方法都机械照贴。

常见失效 / 误用：

1. `new TaskService(...)` 后调用：对象没经过 Spring 代理。
2. 同类内部 `this.completeTask()`：不会因这个调用触发代理的事务拦截；若已有外层事务则另论。
3. 把异常 catch 掉返回正常结果：事务可能提交；某些错误已标 rollback-only 又可能导致提交时失败，不能只凭返回值判断。
4. 在事务中开新线程：默认线程绑定的事务不自动传过去。
5. 用错事务管理器 / 数据源，或者把 Redis、MySQL、HTTP 都当一个事务。
6. 在事务里等一分钟的外部请求：长时间占连接与锁，放大故障。

**重要细节**：提交 / 回滚发生在代理边界。方法里打印「成功」不代表提交已经完成；提交本身也可能失败。

## 4. 两个更新覆盖：加事务也不自动修好

两个标签页都读到任务标题「旧标题」：

```text
A 读 version=0 → 改标题为「新标题」
B 读 version=0 → 标记 DONE，同时带着旧标题
A 保存成功
B 保存成功 → A 的标题被覆盖
```

第一版 API 使用普通 PUT，明确存在这个局限。扩展时加入 version / ETag 协议：

```sql
UPDATE tasks
SET title = ?, status = ?, version = version + 1,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE id = ? AND owner_id = ? AND version = ?;
```

- 影响 1 行：版本匹配，更新成功。
- 影响 0 行：需要根据资源、归属和版本判断是否冲突；常返回 409。
- 如果使用 HTTP `If-Match` / ETag 条件更新，前置条件不成立通常用 412，双方明确契约。

这叫**乐观并发控制**，不是「冲突后无脑用旧数据重试」。前端应提示重新读取、合并或让用户确认。

**悲观锁**如 `SELECT ... FOR UPDATE` 在事务中锁定目标行，再检查更新；会等待和可能死锁。不是所有查询都要加锁，锁范围还受索引和隔离级别影响。

库存 / 配额常优先用数据库原子条件更新：

```sql
UPDATE quota SET remaining = remaining - ?
WHERE user_id = ? AND remaining >= ?;
```

检查影响行数，再在同事务写流水。仅在 Java 里 `if (remaining >= n)` 后扣减仍可能竞态，不必为这种同库规则先加 Redis 分布式锁。

## 5. 隔离级别：先认现象，不死背表

- **脏读**：读到别人尚未提交的修改。
- **不可重复读**：同一事务再读同一行，看到已提交的新值。
- **幻读**：重复范围查询看到符合条件的行集合变化。
- **丢失更新 / 写偏差**：与业务读后写规则相关，不是背完前三种就自动避免。

常见级别：Read Uncommitted、Read Committed、Repeatable Read、Serializable。MySQL InnoDB 默认通常是 Repeatable Read，可以查询确认：

```sql
SELECT @@session.transaction_isolation;
```

InnoDB 的普通一致性读使用 MVCC 快照，锁定读和写使用不同机制；RR 下的间隙 / next-key 锁对某些范围操作有保护，**不能用「RR 消灭一切幻读与并发问题」概括**。Serializable 也不等于所有事务从头到尾简单排成一条队。

**MVCC**是多版本并发控制，让某些读取看见适合其隔离级别的版本。它不替代唯一约束、库存条件和版本冲突协议。隔离级别选择由业务、实现和压测共同决定，不存在任何场景都最优的一档。

## 6. 幂等：防的是「同一业务意图重复生效」

创建导出 / 订单可以要求一个业务幂等键，服务器以 `(actor_id, idempotency_key)` 唯一约束记录：

- 第一次：保存请求指纹、执行状态与结果引用。
- 同一键、同一请求：返回已记录结果或处理中状态。
- 同一键、不同内容：拒绝冲突，不能悄悄复用旧结果。
- 请求超时后重试仍复用同一键，不是每次随机新生成。

去重记录与业务修改应尽可能放同一数据库事务。保留多久、处理中崩溃如何恢复、并发谁能执行，都必须定义；一个 Redis `SETNX` 后再做业务，不是天然的可靠完成记录。

## 7. 跨服务一致性：本地事务到此为止

任务保存后要发一封邮件：数据库提交成功、邮件失败；或邮件发出、数据库回滚，都可能发生。

先问：「能否让这两步在同一应用同一库中完成？」能就不急于分布式。确需解耦时可用 **outbox**：同一事务保存任务与待发送事件，后台可靠投递，消费者幂等。见 [消息与任务](./33-mq-reliability)。

CAP 说的是网络分区时一致性与可用性的约束，不是任意系统随便「三选二」。也不能说所有互联网业务都选 AP、强一致只有金融才需要。订单唯一性、权限、库存等都可能有很强的正确性要求，要逐操作分析。

「最终一致」需要重试、幂等、状态机、对账和补偿等闭环；不是先显示成功，然后假设以后自然一致。

## 动手：两个 MySQL 会话复现

在**隔离练习库**准备一条任务：

1. 会话 A `START TRANSACTION` 后更新标题，不提交；会话 B 查询，观察当前隔离级别下能否看到。
2. A `ROLLBACK`；B 再查，确认未留下 A 的修改。
3. A、B 都读 version=0，依次按相同旧版本条件更新。**预期只有一个更新影响 1 行，另一个为 0。**
4. 在 Spring 事务内故意让审计 SQL 失败，用集成测试断言任务修改也回滚；不要只看抛没抛异常。

## 自测与参考答案

1. `@Transactional` 会撤销已经发出的短信吗？**不会，外部副作用不在本地数据库事务中。**
2. 唯一索引和前端禁用按钮谁更能防并发重复记录？**唯一约束提供数据库层保障，按钮只改善体验。**
3. 乐观更新冲突是不是马上重试就好？**不是，要重新读取并判断业务意图是否仍成立。**

下一站：[登录与数据权限](./32-security)，再用 [测试](./35-testing) 验证失败边界。

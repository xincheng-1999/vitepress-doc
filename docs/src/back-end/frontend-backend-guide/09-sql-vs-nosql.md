# SQL 与 NoSQL：任务数据该存在哪里

::: info 按需阅读
这一篇用任务系统说明数据库怎么选，不要求把提到的产品都安装一遍。如果正在做 [可运行 CRUD](/back-end/frontend-backend-guide/07-build-a-crud-api)，先用默认的 H2 完成练习即可。
:::

前端提交了一段 JSON：

```json
{"title":"整理接口文档"}
```

后端该把它存进 MySQL，还是 MongoDB？**请求是 JSON，并不能决定数据库的类型。** 要先看它变成业务数据后，准备怎么查、怎么改，以及哪些规则必须成立。

## 1. 先把一条任务存好

当前 CRUD 示例的任务表很小：

```sql
CREATE TABLE IF NOT EXISTS tasks (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('TODO', 'DOING', 'DONE')),
    created_at TIMESTAMP(6) NOT NULL
);
```

一行就是一条任务。主键防止 ID 重复，`NOT NULL` 不允许缺失标题，`CHECK` 限制状态取值。应用入口仍然要校验输入；这些约束也没有替你实现用户权限和任务归属。

以后任务有了负责人、所属团队和操作记录，需求可能变成：“查出某团队最近一周未完成的任务，连同负责人一起显示。”关系数据库能用 SQL 表达这些关系和查询，还能用事务让一组修改一起成功或失败。因此，这个任务系统从关系数据库开始，是一个容易理解、也能继续发展的选择。

这不是说 MySQL 会自动保证业务正确。比如“同一次请求不能重复创建任务”，仍要定义业务唯一键和重复请求处理；事务边界也要自己划清。具体见 [事务与一致性](/back-end/frontend-backend-guide/11-transactions-consistency)。

## 2. 当前练习里的 H2 和 MySQL 有什么区别

示例使用 `JdbcTemplate` 执行参数化 SQL，再把结果映射成 Java 对象，不是 JPA，也没有连接 MongoDB。

默认启动时，它连接 **H2 内存数据库**。这样不用先安装数据库，就能练习请求、SQL 和响应如何串起来；但数据只存在于这次 JVM 进程中。可选的 `mysql` 配置则让同一套 Repository SQL 连接 MySQL，学习持久保存数据。两者都属于关系数据库，不是“SQL 与 NoSQL”的两边。

想亲眼看到这个区别，可以在自己的本地临时环境试一次。需要 JDK 17 和 Maven；以下操作会创建练习任务，并通过停止应用丢弃本次 H2 数据，不连接生产库：

```bash
cd examples/backend-task-api
mvn spring-boot:run
```

另开一个终端：

```bash
curl -i http://localhost:8080/api/tasks \
  -H 'Content-Type: application/json' -d '{"title":"确认数据存在哪里"}'
curl -s 'http://localhost:8080/api/tasks?page=0&size=20'
```

创建成功会返回 `201`，再查列表能看到这条任务。在运行应用的终端按 Ctrl+C，只会停止这个本地练习进程；重新启动后，刚才的记录就没有了。配置中的 `DB_CLOSE_DELAY=-1` 只让内存库在 JVM 存活期间不随最后一个连接关闭而销毁，不会让它跨重启保存。

若要练持久化，再按 [配置与环境](/back-end/frontend-backend-guide/25-config-and-env) 使用本地 MySQL Compose。保留数据库卷时，重建 API 容器不会删除库里的记录；删除数据库卷则是另一回事。该 Compose 没有向宿主机映射 MySQL 端口，不要照搬 `localhost:3307` 来连接。

H2 跑通不代表 MySQL 已验证：类型、时区、SQL 方言、锁和执行计划仍可能不同。`CREATE TABLE IF NOT EXISTS` 也不会替你升级已有表，后续改表需要版本化迁移。

## 3. 那什么时候考虑 NoSQL

假设需求又变了：任务描述需要按关键词搜索，并按相关程度排序。这里的难点不再是“存下标题”，而是分词和搜索质量，才有理由评估专门的搜索引擎。

**NoSQL 是多种存储方式的统称，不是另一个万能数据库。** 下面几种工具解决的问题并不相同：

| 工具 | 什么时候值得评估 | 不必引入它的理由 |
| --- | --- | --- |
| 文档数据库，如 MongoDB | 数据自然组成嵌套文档，常常以整个文档为单位读写 | HTTP 传 JSON 不是理由，仍要分析关联查询和更新边界 |
| Redis | 反复读取的热点数据、短期状态、计数等需要低延迟访问 | 普通任务读写先用关系库，缓存还会增加失效和故障处理 |
| 搜索引擎，如 Elasticsearch | 需要分词、相关性排序、高亮等全文搜索能力 | 按状态和时间筛选，SQL 通常就够了 |
| 图数据库 | 主要工作是复杂的关系遍历 | 任务与成员的普通关联，不一定需要图数据库 |

不要用“关系型不能改字段”“MongoDB 没有事务”来选型。MySQL 可以加列、存 JSON；MongoDB 也有索引、文档校验和事务。具体限制和代价，要看版本、建模、部署方式和读写配置。性能也取决于查询、索引、热点及硬件，不是由数据库类别直接决定。

## 4. 一份任务有多种副本时，哪一份说了算

假设以后加了缓存和搜索：

```text
MySQL 中的任务记录
  ├─ 生成 Redis 缓存，方便快速读取
  ├─ 同步到搜索索引，方便全文搜索
  └─ 汇总成日报，方便统计
```

如果搜索结果显示任务未完成，而任务详情显示已完成，该信谁？系统需要事先约定一份权威记录，冲突时以它为准。这就是**事实源**。在这个设计里，MySQL 的任务记录是事实源，缓存、索引和日报是从它产生的派生数据，可以延迟，也要能重建。上图是后续设计，不是当前示例已经部署的能力。

多种存储共存后，工作不止是多写几次数据。还要考虑：同步失败怎么办，缓存多久失效，重建索引时能否漏数据，以及删除或权限变化后旧搜索结果会不会泄露内容。详见 [功能地图中的搜索](/back-end/frontend-backend-guide/39-backend-feature-map)。外面包一层 `@Transactional`，不会让 MySQL、Redis 和搜索引擎自动一起回滚。

所以，先把任务的关系、查询和约束说清楚，用一个数据库走通；只有新工具明确解决了现有问题，而且团队能负责备份、恢复和故障处理时，再引入它。数据库多，不代表系统就更完整。

需要继续练关系库，可以接着看 [SQL 与索引](/back-end/frontend-backend-guide/10-sql-and-indexes)；遇到热点读取再看 [Redis](/back-end/frontend-backend-guide/12-redis-in-practice)。连接如何复用见 [连接池](/back-end/frontend-backend-guide/13-connection-pools)，不熟悉的产品名可回 [场景词典](/back-end/frontend-backend-guide/90-glossary) 查阅。

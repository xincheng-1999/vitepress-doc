# 09. SQL 与 NoSQL：先选事实源，再考虑专门工具

## 学习目标

- 解释为什么任务管理从关系数据库开始，而不是先安装所有数据库。
- 分清 H2 本地实验、MySQL 业务事实源，以及缓存/搜索等派生数据。
- 按数据关系、查询、约束、可靠性和团队能力做选择，不用“JSON 就该用 MongoDB”等口诀。

本章沿用 Java 17 + Spring Boot 3.x 的单体 `task-api`，接口为 `http://localhost:8080/api/tasks`。完整起点见 [可运行 CRUD](/back-end/frontend-backend-guide/07-build-a-crud-api)。**先跑默认 H2，再学习 MySQL；MongoDB 不是必修前置。**

## 09.1 当前示例到底用了什么

| 阶段 | 实际行为 | 用途与边界 |
| --- | --- | --- |
| 默认启动 | JDBC + H2 内存库，启动时执行初始化 SQL | 免安装数据库、理解接口与 SQL；JVM 退出后数据消失 |
| 激活 `mysql` profile | 同一套 Repository SQL 通过 JDBC 访问 MySQL | 可选持久化练习；提供 Compose 启动方案 |
| 之后设计的缓存、搜索、报表 | 示例尚未实现 | 按需求增加，不要把设计图当成现有能力 |

示例使用 `JdbcTemplate`，不是 JPA，也没有 MongoDB。它从连接中执行参数化 SQL，再将结果映射为响应对象。

当前 `tasks` 的最小结构如下，来自示例初始化脚本：

```sql
CREATE TABLE IF NOT EXISTS tasks (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('TODO', 'DOING', 'DONE')),
    created_at TIMESTAMP(6) NOT NULL
);
```

主键保证任务 ID 不重复，`NOT NULL` 与 `CHECK` 为有效数据提供约束。应用仍要检查输入，后续还需补权限、数据归属与并发规则；表存在不意味着这些能力已经具备。

## 09.2 为什么任务管理先用 MySQL

需求会从“存一个标题”发展到“任务属于用户、团队、负责人，修改任务同时记录审计”。关系数据库适合把这些关系、查询和事务边界明确表达出来。

| 现实问题 | 关系数据库提供的工具 | 仍需要开发者负责 |
| --- | --- | --- |
| 同一个逻辑记录不能创建两份 | 主键、唯一约束 | 定义正确的业务唯一键，处理冲突 |
| 查询团队任务并按状态、时间过滤 | SQL、关联、索引 | 查询形态、索引顺序、分页边界 |
| 两次修改必须一起成功 | 本地事务 | 正确划边界，避免吞异常和跨资源误判 |
| 字段变化后旧数据还能使用 | DDL 与迁移工具 | 默认值、数据回填、兼容发布和回滚计划 |

MySQL 可以存 JSON，列也可以允许空值；“关系型”不等于永远不能演进。加列是否阻塞、代价多大，取决于数据库版本、DDL 类型、表规模和运行条件，不能简单断言“每次改表都锁死”。

同样，选择 MySQL 不能自动保证“钱绝不会错”：错误的事务边界、重复请求、缺失约束、异步复制延迟都可能破坏业务规则。要把产品名拆成可验证的配置和操作保证，见 [事务与一致性](/back-end/frontend-backend-guide/11-transactions-consistency)。

## 09.3 NoSQL 是一组工具，不是另一个万能数据库

NoSQL 包含文档、键值、图等多种存储模型。应讨论具体产品与访问方式，不能给整个类别统一贴“无 schema、无事务、默认最终一致”的标签。

| 类型 | 典型需求 | 对当前任务 API 的判断 |
| --- | --- | --- |
| 文档型，如 MongoDB | 聚合数据自然按嵌套文档读写，建模和查询方式适合业务 | 可选方案，不因为请求 JSON 就必须引入 |
| 键值/数据结构存储，如 Redis | 可丢弃缓存、计数、短期状态等低延迟访问 | 有重复热点读取证据再加，先定义失效和故障策略 |
| 搜索引擎，如 Elasticsearch | 分词、相关性、高亮、全文搜索 | 普通状态筛选先用 SQL；专门搜索需求出现再评估 |
| 图数据库 | 核心查询是复杂关系遍历 | 普通任务与成员关联通常不需要 |

文档型并非没有约束：MongoDB 可以做 schema validation、索引和事务，应用也必须统一字段语义。它的关联、读写一致性、事务和分片行为要看具体操作、部署、read/write concern 等设置；不能仅按“文档型”推断强弱。

性能同样不是类型决定的。数据量、索引、热点、访问路径、持久性要求、网络和硬件都会改变结果；“MongoDB 一定比 MySQL 高吞吐”不是可靠选型依据。

## 09.4 一份任务有多种读法，谁说了算

**事实源（source of truth）**是业务冲突时用来认定事实的权威记录。后续主线让 MySQL 中的任务记录承担这个职责，H2 只是本地起步的替代实现。

```text
MySQL 任务记录（事实）
  ├─ 按需生成 Redis 缓存：可失效、可重建
  ├─ 按需生成搜索索引：可能延迟，需要同步与重建
  └─ 按需生成日报：要定义统计时间与对账口径
```

这张图是后续设计，不是当前示例已有部署。当前只运行一个关系数据库就足够。

多种存储可以共存，但不是“真实系统必须混用”。每多一套，就多出数据同步、权限、备份、故障恢复与监控成本。尤其不能先更新 MySQL，再写 Redis/ES，然后认为外层一个 `@Transactional` 能让三者一起回滚。

如果搜索结果与任务详情不一致，必须有规则：详情以权威数据库为准，检查索引延迟或漏同步；涉及权限、删除等敏感场景，还需避免旧索引泄露内容。详见 [功能地图中的搜索](/back-end/frontend-backend-guide/39-backend-feature-map)。

## 09.5 五个选型问题，比数据库排行榜重要

1. **数据与关系**：任务、用户、团队如何关联？哪些值必须唯一、不能为负？
2. **访问模式**：主要按 ID、状态和时间查，还是全文搜索、图遍历？
3. **正确性**：哪些操作要本地事务？能接受多久的副本/索引延迟？
4. **规模证据**：当前瓶颈是什么，有查询计划和代表性压测吗？
5. **运维能力**：谁升级、备份、恢复、处理故障，能否实际验证恢复？

字段变化频繁可以评估文档模型，也可以采用关系表加受控 JSON；复杂查询可以先优化 SQL。只有新工具明显解决已确认的问题，而且团队能承担运行成本，才值得引入。

## 动手练习：亲自区分 H2 实验与持久化

**可直接运行**：JDK 17 与 Maven 已安装；从仓库根目录进入示例，默认只监听本机。实验只针对自己的临时数据，不连接生产库。

```bash
cd examples/backend-task-api
mvn spring-boot:run
```

另一个终端创建并查询：

```bash
curl -i http://localhost:8080/api/tasks \
  -H 'Content-Type: application/json' -d '{"title":"确认数据存在哪里"}'
curl -s 'http://localhost:8080/api/tasks?page=0&size=20'
```

**预期结果**：POST 返回 `201`，响应有生成的 ID、`TODO` 状态；列表中有该任务。在运行应用的终端 Ctrl+C，再用相同命令启动，列表中不再有这次记录，因为默认是 H2 内存库。`DB_CLOSE_DELAY=-1` 只让内存库在 JVM 存活期间不随最后一个连接关闭而销毁，不会让它跨重启持久化。

**选做**：按 [配置与环境](/back-end/frontend-backend-guide/25-config-and-env) 启动本地 MySQL Compose 练习，创建任务后重建 API 容器，验证保留数据库卷时记录仍在。MySQL 不向宿主映射端口，不要用默认的 `localhost:3307` 地址去连接这套 Compose。

H2 通过不代表 MySQL 一定通过：方言、类型、时区、执行计划、锁与隔离语义都需要 MySQL 集成测试。初始化脚本的 `IF NOT EXISTS` 也不等于自动升级旧表，真实演进需版本化迁移。

## 自测与答案

1. HTTP 输入是 JSON，就应该用文档数据库吗？

   **答案**：不是。传输格式不决定存储模型，应看关系、查询、约束和更新边界；当前任务 API 用 JDBC + 关系库就足够。
2. 为什么默认 H2 重启丢数据，MySQL 容器重建却不一定丢？

   **答案**：前者位于 JVM 内存；后者本地练习使用数据库卷持久保存。是否保留取决于卷和操作方式，不是“用了容器就不丢”。
3. 加了 Redis 和 ES 后，哪一份任务是事实？

   **答案**：按本主线仍是 MySQL；缓存与索引是派生副本，必须有失效、同步、对账与重建方案。
4. MongoDB 没有事务、关系库不能放 JSON，这两句话对吗？

   **答案**：都不对。具体能力取决于产品与版本，选型不能建立在类别刻板印象上。

## 相关教程

- [CRUD](/back-end/frontend-backend-guide/07-build-a-crud-api) → [SQL 与索引](/back-end/frontend-backend-guide/10-sql-and-indexes) → [事务](/back-end/frontend-backend-guide/11-transactions-consistency)：先走通一条关系库主线。
- [Redis](/back-end/frontend-backend-guide/12-redis-in-practice)：按需缓存，不替代权威记录。
- [连接池](/back-end/frontend-backend-guide/13-connection-pools) / [配置与环境](/back-end/frontend-backend-guide/25-config-and-env)：连接的是谁、连接如何复用。
- [功能地图](/back-end/frontend-backend-guide/39-backend-feature-map) / [场景词典](/back-end/frontend-backend-guide/90-glossary)：从需求回查工具边界。

# 关系型 vs 文档型怎么选

> 这一章不教你 SQL 语法（那是下一章的事），而是帮你建立**选型直觉**：什么时候用 MySQL，什么时候用 MongoDB，为什么本项目让 MongoDB 当主库、MySQL 只管统计。
> 选型选错，后面写代码再努力也是在还债。

---

## 一句话本质区别

- **关系型数据库（MySQL）**：数据存在**固定结构的表**里。每张表的列（字段）类型、约束都提前定死，行与行之间靠外键关联，擅长 JOIN 和事务。
- **文档型数据库（MongoDB）**：数据存成一个个**类 JSON 的文档**，每个文档结构可以不一样，不需要提前定义 schema，擅长灵活演进和高吞吐读写。

**前端类比**：

```text
MySQL  ≈ 一张结构化的 Excel 表格
        每一列都有固定表头(id / name / age)，每行必须按列填，类型对不上就报错

MongoDB ≈ 一个装满 JSON 对象的数组
        [ {uid:1, name:"a"}, {uid:2, name:"b", vip:true, tags:["x"]} ]
        对象之间字段多一个少一个都没关系
```

如果你写过 TypeScript，可以这样记：MySQL 像一个**严格的 `interface` + 编译期校验**，字段对不上直接报错；MongoDB 像 `Record<string, any>`，先存进去再说，灵活但少了约束。

---

## 它们到底差在哪

下面这张表是选型时真正要权衡的几个维度，不是背概念，是每条都会影响你后面写代码的体验。

| 维度 | 关系型（MySQL） | 文档型（MongoDB） |
| --- | --- | --- |
| 数据模型 | 行 + 列，schema 固定 | 文档（BSON/JSON），schema 灵活 |
| 加字段 | 要 `ALTER TABLE`，大表可能锁表 | 直接写新字段即可，老文档不受影响 |
| 关联查询 | JOIN 是看家本领 | 没有原生 JOIN，靠内嵌文档或 `$lookup`（弱） |
| 事务 | 强项，ACID 成熟稳定 | 4.0 起支持多文档事务，但成本更高、用得更克制 |
| 横向扩展 | 分库分表，麻烦 | 原生分片（sharding），水平扩展友好 |
| 写入吞吐 | 受事务/索引/锁影响 | 单文档写入快，适合高并发写 |
| 复杂聚合/报表 | SQL `GROUP BY` / 窗口函数，成熟 | 聚合管道能做，但复杂报表不如 SQL 顺手 |
| 一致性 | 默认强一致 | 默认最终一致，可调，但要自己想清楚 |

一句话总结这张表：**MySQL 用"约束 + 关系 + 事务"换稳定，MongoDB 用"放松约束"换灵活和吞吐。** 没有谁更高级，只有谁更贴合你的场景。

> 关于两类数据库更系统的对比（包括键值、列式、图数据库等其它非关系型流派），可以读这篇基础篇：[关系型 vs 非关系型](/back-end/database/basics/relational-vs-nosql)。

---

## 同一份用户数据，两种存法

直观感受一下差异。假设要存一个用户，他可能是手机号注册、可能是邮箱注册，VIP 用户还多几个字段。

**MySQL 的存法**——必须先建表，字段定死：

```sql
CREATE TABLE user_mst (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  uid         BIGINT      NOT NULL,
  login       VARCHAR(64) NOT NULL,
  user_type   VARCHAR(16) NOT NULL,
  vip_level   INT         DEFAULT 0,   -- 非 VIP 也得占这一列
  created_at  DATETIME    NOT NULL,
  UNIQUE KEY uk_login (login, user_type)
);
```

要是哪天产品说"给用户加一个 `tags` 标签数组"，MySQL 里你得 `ALTER TABLE` 加列，或者干脆再开一张关联表，大表改结构还得担心锁表。

**MongoDB 的存法**——没有建表这一步，结构随文档走：

```javascript
// 普通用户
{ "_id": "a1", "uid": 10001, "login": "13800138000", "userType": "mobile", "createdAt": "2024-01-01T00:00:00Z" }

// VIP 用户：直接多塞几个字段，不影响上面那条
{ "_id": "a2", "uid": 10002, "login": "x@y.com", "userType": "email",
  "vipLevel": 3, "tags": ["early-bird", "designer"], "quota": { "daily": 200, "used": 17 } }
```

注意 VIP 用户里那个内嵌的 `quota` 对象——在 MySQL 里这通常要拆成另一张表再 JOIN，MongoDB 直接当作文档的一部分嵌进去。这就是"文档型"三个字的含义：**把相关数据聚在一起，按业务对象的形状存**。

对应到本项目的 Java 代码（旧课已出现过的 `UserMst`），MongoDB 这边只要定义一个类加注解，连建表都省了：

```java
@Document(collection = "user_mst")   // 对应 MongoDB 的集合，不需要预先建表
public class UserMst {
    @Id
    private String id;               // 主键，MongoDB 自动生成
    private Long uid;
    private String login;            // 手机号/邮箱
    private String userType;         // mobile / email
    private Integer vipLevel;        // 老文档没有这个字段也不报错
    private LocalDateTime createdAt;
}
```

**前端类比**：这就是你写 `zod` schema 的两种心态。MySQL 像 `z.object({...}).strict()`，多一个字段就报错；MongoDB 像 `z.object({...}).passthrough()`，宽进宽出。

---

## 本项目为什么这么分工

我们的 AI 生图微服务里，**MongoDB 是主库，MySQL 只做统计分析**。这不是随便定的，背后是上面那张表的直接推论。

### 为什么业务主库选 MongoDB

```text
业务特点                              → MongoDB 的优势点
─────────────────────────────────────────────────────────
画布/任务字段经常变(svc-canvas 最复杂)  → schema 灵活，加字段不用改表
生图任务高并发提交、状态频繁更新         → 单文档写入快，吞吐高
任务里嵌套画布元素/参数(天然是树/JSON)   → 内嵌文档，一次读出整个对象
未来要扩(用户量/任务量都会涨)           → 原生分片，水平扩展省心
```

svc-canvas 的一个生图任务，本身就是一棵嵌套很深的 JSON：画布尺寸、图层列表、每个图层的参数、生成历史……用 MongoDB 一个文档存下来，读的时候一把捞出，前端拿到的几乎就是它要的形状。如果硬塞进 MySQL，得拆成任务表、图层表、参数表好几张，每次查任务都要 JOIN，加字段还得改表结构——业务迭代快的时候，这是实打实的拖累。

### 为什么统计分析选 MySQL

```text
统计特点                              → MySQL 的优势点
─────────────────────────────────────────────────────────
"按天统计新增用户/生图次数/收入"        → GROUP BY 聚合成熟好写
跑日报/周报/留存漏斗                    → 复杂多表 JOIN + 聚合是看家本领
财务/对账类数据要求准                   → 强事务、强一致更让人放心
报表数据结构稳定(就那几列)              → 不需要灵活 schema，固定表反而清晰
```

对应到旧代码里的 `daily_statistics` 表（MyBatis-Plus 的用法见 [Spring Data 与数据库整合](/back-end/java/08-spring-data-db)）：

```sql
-- 一条典型的统计查询：最近 7 天每天的新增用户和生图任务数
SELECT date_str,
       SUM(new_users)   AS new_users,
       SUM(gen_tasks)   AS gen_tasks
FROM daily_statistics
WHERE date_str >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
GROUP BY date_str
ORDER BY date_str;
```

这种"分组、求和、按日期排"的活儿，SQL 一句话搞定，可读性也好。换成 MongoDB 的聚合管道（`$group` / `$match`），不是做不到，但复杂报表写起来啰嗦、调试更累——**让擅长的人干擅长的事**。

> 注意这里的数据流向：业务发生在 MongoDB（svc-user / svc-canvas 写入），再由定时任务（cpt-xxljob）把当天数据汇总进 MySQL 的统计表。MySQL 里是"加工后的二手数据"，不是业务实时源头。

---

## 选型决策清单

把它当查询手册用。先问自己这几个问题，命中哪条就往哪边走：

| 你的场景 | 选谁 | 为什么 |
| --- | --- | --- |
| 涉及钱、库存、扣配额，要求绝对不能错 | **MySQL** | 强事务 + 强一致最稳 |
| 多张表关联查询、复杂 JOIN | **MySQL** | JOIN 是关系型看家本领 |
| 报表、日报、聚合统计、留存分析 | **MySQL** | `GROUP BY`/窗口函数成熟 |
| 字段经常变、产品需求迭代快 | **MongoDB** | schema 灵活，加字段不改表 |
| 高并发写入（日志、埋点、任务状态） | **MongoDB** | 单文档写入快、易扩展 |
| 数据天然是嵌套结构（画布、配置、画像） | **MongoDB** | 内嵌文档，一次读出整棵树 |
| 海量数据要水平扩展 | **MongoDB** | 原生分片 |
| 用户画像、行为日志、半结构化数据 | **MongoDB** | 字段不规整，宽进宽出 |

一个更糙但好用的口诀：

```text
要"对账"、要"关系"、要"报表" → 想 MySQL
要"灵活"、要"高写"、要"嵌套" → 想 MongoDB
```

---

## 重要提醒：不是二选一

新手最容易陷入的误区，是把它当成"选了一个就不能用另一个"。真实的后端系统几乎都是**混着用的**，这个做法有个专门的名字叫 **polyglot persistence（多语言持久化）**——不同数据用最适合它的存储。

本项目就是活教材，一个请求背后可能同时碰到好几种存储：

```text
用户提交一次生图任务，背后的数据分工：
  svc-canvas → MongoDB    存任务文档(业务主库，灵活+高写)
  svc-user   → Redis      读/扣配额(缓存+分布式锁，快)
  svc-user   → MongoDB    持久化配额变更(主库)
  cpt-xxljob → MySQL      每天把任务量汇总进统计表(报表)
  svc-ai     → RocketMQ   异步派发生图任务(削峰)
```

所以正确的问题不是"我该用 MySQL 还是 MongoDB"，而是"**这一块数据，它的读写特点更像哪边**"。配额读多写少且要快 → Redis 顶在前面；任务文档结构多变 → MongoDB；月度对账报表 → MySQL。Redis 的具体用法后面有专门一章 [Redis 实战](/back-end/frontend-backend-guide/12-redis-in-practice) 细讲，这里只要记住它是缓存层、不是主存储。

**前端类比**：你前端也不是只用一种存储——热数据放 React 状态、要持久化放 `localStorage`、跨标签页同步用 `IndexedDB` 或 BroadcastChannel。后端选数据库是同一种思维，只是赌注更大。

---

## 一个常见的坑：别拿 MongoDB 当 MySQL 用

选了 MongoDB 不等于可以不动脑。最常见的翻车姿势：

- **疯狂用 `$lookup` 模拟 JOIN**：MongoDB 的关联能力很弱，如果你发现自己天天 `$lookup`，多半是数据建模错了——文档型的正解是"该内嵌就内嵌，该冗余就冗余"，而不是把关系型那套表拆分搬过来。
- **把强一致的钱账放 MongoDB 再手写一致性逻辑**：能用 MySQL 事务一把搞定的，别自己造轮子。事务这块下一阶段会专门讲，到时你会更理解为什么钱相关的数据更适合关系型。
- **schema 完全放飞**：灵活不等于没规矩。同一个集合里字段东一个西一个，半年后没人看得懂。即使是 MongoDB，也建议在应用层用 DTO（cpt-api 里的共享 DTO）或 `zod` 式校验约束住形状，统一响应仍然走 `RtData.ok(data)` / `RtData.fail(msg)`。

记住：**灵活是工具，不是借口**。约束从数据库层移到了应用层，并没有消失。

---

## 小结

- 关系型（MySQL）= 固定 schema + 强一致 + JOIN/事务擅长；文档型（MongoDB）= 灵活 JSON 文档 + 水平扩展 + 高吞吐读写。
- 类比：MySQL 像严格的 Excel 表格 / `interface`，MongoDB 像一堆 JSON 对象 / `Record<string, any>`。
- 本项目让 MongoDB 当主库（业务字段多变、高并发写、数据天然嵌套），MySQL 只做统计分析（复杂聚合、报表、对账要强一致）。
- 选型看数据的读写特点：要对账/关系/报表想 MySQL，要灵活/高写/嵌套想 MongoDB。
- 真实系统是 polyglot persistence，混着用才是常态——别把它当二选一，也别拿 MongoDB 硬模拟 MySQL。

### 自测

1. 产品要给生图任务加一个"风格预设"字段，且这个结构以后还会频繁变。放 MySQL 还是 MongoDB？为什么？
2. 财务要一张"按月统计各用户充值与消费"的报表，涉及多表关联和分组求和。你会从哪个库出数据？理由是什么？
3. 用一句话向同事解释"polyglot persistence"，并举本项目里一个同时用到三种存储的请求例子。

### 下一章

知道了选哪个，接下来就要会用——下一章 [SQL 与索引](/back-end/frontend-backend-guide/10-sql-and-indexes) 带你真正动手写 SQL，并理解索引为什么能让查询快上千倍。

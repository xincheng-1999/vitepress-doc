---
title: Redis 基础与应用场景
---

# Redis 基础与应用场景

在 [关系型 vs 非关系型数据库](/back-end/database/basics/relational-vs-nosql) 中我们提到过 Redis。它不是用来存"正式数据"的，而是一个**超快的内存数据库**，主要用来做缓存。

## 1. 什么是 Redis？

**Redis (Remote Dictionary Server)** = 一个运行在内存中的键值对数据库。

### 前端类比

*   **MySQL / MongoDB**：像后端的"硬盘"，数据持久保存，但读写速度较慢。
*   **Redis**：像前端的 **`sessionStorage`** 或 **Vuex/Pinia 的 Store**——数据在内存里，读写飞快，但重启就可能丢失。

```javascript
// Redis 的本质就像一个超级大的 JavaScript Map
const redis = new Map()

redis.set("user:1001", '{"name":"Jack","age":25}')   // SET
redis.get("user:1001")                                 // GET
redis.delete("user:1001")                              // DEL
```

### 为什么需要 Redis？

假设你的网站有一个"热门文章排行榜"。每次用户访问首页，就要从 MySQL 查一次——如果 1 秒有 10000 人访问，MySQL 会被压垮。

**解决方案**：第一次从 MySQL 查出来后，存到 Redis 里。接下来的请求直接从 Redis 取，速度提升 **100 倍**。

```text
用户请求 → 先查 Redis（命中？直接返回）
              ↓ 没命中
          查 MySQL → 写入 Redis → 返回给用户
```

> 这就是**缓存 (Cache)** 的核心思想。前端的 HTTP 缓存、Service Worker 缓存，原理都一样。

## 2. Redis 常见应用场景

| 场景 | 说明 | 前端类比 |
| :--- | :--- | :--- |
| **缓存** | 热点数据缓存，减少数据库压力 | HTTP 缓存 / localStorage |
| **Session 存储** | 存用户登录状态（替代 Cookie-Session） | sessionStorage |
| **排行榜** | 利用有序集合 (Sorted Set) 实时排名 | 前端排序后的数组 |
| **计数器** | 文章阅读数、点赞数 | 状态变量 count++ |
| **限流** | 限制 API 每分钟访问次数 | 前端的 throttle/debounce |
| **消息队列** | 简单的任务队列 | 事件队列 |
| **分布式锁** | 多服务器之间协调 | 类似前端的 mutex |

## 3. 安装 Redis

### 方式一：Windows

Redis 官方不直接支持 Windows，推荐使用以下方式之一：

**A. Docker（推荐）：**
```bash
docker run -d -p 6379:6379 --name redis redis:7
```

**B. WSL2 (Windows Subsystem for Linux)：**
```bash
sudo apt update
sudo apt install redis-server
sudo service redis-server start
```

**C. Windows 版本：**
从 [GitHub - tporadowski/redis](https://github.com/tporadowski/redis/releases) 下载 Windows 编译版本。

### 方式二：Mac

```bash
brew install redis
brew services start redis
```

### 方式三：Linux

```bash
sudo apt update
sudo apt install redis-server
sudo systemctl start redis-server
```

### 验证安装

```bash
redis-cli ping
```

如果返回 `PONG`，说明安装成功！Redis 默认端口是 **6379**。

## 4. Redis 数据类型

Redis 不是只能存字符串。它支持 **5 种核心数据类型**，每种都有对应的使用场景。

### 4.1 String (字符串) — 最常用

```bash
# 存值
SET name "Jack"

# 取值
GET name
# 返回: "Jack"

# 设置过期时间 (60 秒后自动删除)
SET token "abc123" EX 60

# 查看剩余有效时间
TTL token
# 返回: 58 (秒)

# 数字自增 (原子操作，天然并发安全)
SET page_views 0
INCR page_views       # 1
INCR page_views       # 2
INCRBY page_views 10  # 12
```

> **使用场景**：缓存 JSON 数据、Token 存储、计数器。

### 4.2 Hash (哈希) — 存对象

```bash
# 类似 JavaScript 对象 { name: "Jack", age: "25", email: "jack@example.com" }
HSET user:1001 name "Jack"
HSET user:1001 age "25"
HSET user:1001 email "jack@example.com"

# 取单个字段
HGET user:1001 name
# 返回: "Jack"

# 取所有字段
HGETALL user:1001
# 返回: name Jack age 25 email jack@example.com

# 一次设置多个字段
HMSET user:1002 name "Alice" age "20" email "alice@example.com"
```

> **使用场景**：存用户信息、商品详情等结构化数据。比 String 存 JSON 更节省内存、可以单独更新某个字段。

### 4.3 List (列表) — 有序集合

```bash
# 左边入队 (栈的感觉)
LPUSH messages "msg1"
LPUSH messages "msg2"
LPUSH messages "msg3"

# 右边入队 (队列的感觉)
RPUSH messages "msg4"

# 查看所有 (0 到 -1 表示全部)
LRANGE messages 0 -1
# 返回: msg3 msg2 msg1 msg4

# 取出左边第一条 (消费消息)
LPOP messages
# 返回: msg3
```

> **使用场景**：消息队列、最新消息列表、操作日志。

### 4.4 Set (集合) — 无序、不重复

```bash
# 添加元素
SADD tags "JavaScript"
SADD tags "React"
SADD tags "JavaScript"  # 重复添加无效

# 查看所有成员
SMEMBERS tags
# 返回: JavaScript React

# 判断是否存在
SISMEMBER tags "React"
# 返回: 1 (true)

# 两个集合的交集
SADD user1_skills "JS" "React" "CSS"
SADD user2_skills "JS" "Vue" "CSS"
SINTER user1_skills user2_skills
# 返回: JS CSS
```

> **使用场景**：标签系统、共同好友、去重。

### 4.5 Sorted Set (有序集合) — 带分数的排行榜

```bash
# ZADD key score member
ZADD leaderboard 100 "Alice"
ZADD leaderboard 85 "Bob"
ZADD leaderboard 95 "Jack"

# 按分数从高到低取排行 (TOP 3)
ZREVRANGE leaderboard 0 2 WITHSCORES
# 返回: Alice 100, Jack 95, Bob 85

# 给 Bob 加 20 分
ZINCRBY leaderboard 20 "Bob"

# 查看 Bob 的排名 (从 0 开始)
ZREVRANK leaderboard "Bob"
# 返回: 1 (第二名)
```

> **使用场景**：排行榜、热搜榜、优先级队列。

## 5. 常用操作命令速查

| 命令 | 说明 | 示例 |
| :--- | :--- | :--- |
| `SET key value` | 存一个字符串 | `SET name "Jack"` |
| `GET key` | 取值 | `GET name` |
| `DEL key` | 删除 | `DEL name` |
| `EXISTS key` | 是否存在 | `EXISTS name` |
| `EXPIRE key seconds` | 设置过期时间 | `EXPIRE token 3600` |
| `TTL key` | 查看剩余时间 | `TTL token` |
| `KEYS pattern` | 查看匹配的 key | `KEYS user:*` |
| `FLUSHDB` | 清空当前数据库 | ⚠️ 慎用 |

## 6. Redis vs MySQL vs MongoDB

| 对比项 | MySQL | MongoDB | Redis |
| :--- | :--- | :--- | :--- |
| 数据存储 | 硬盘 | 硬盘 | **内存** |
| 读写速度 | 慢 | 中等 | **极快** (微秒级) |
| 数据格式 | 表格 | JSON 文档 | 键值对 |
| 持久性 | 强 | 强 | 弱 (需配置持久化) |
| 适合存什么 | 核心业务数据 | 灵活结构数据 | **临时数据、缓存** |
| 类比 | 硬盘 | U 盘 | **内存条** |

## 总结

*   Redis = 内存中的超快键值对数据库。
*   核心价值：**缓存加速** + **Session 管理** + **实时排行**。
*   5 种数据类型：String、Hash、List、Set、Sorted Set，覆盖绝大多数缓存场景。
*   默认端口 **6379**，安装推荐 Docker 方式。

下一章我们将学习如何在 Java/Spring Boot 项目中使用 Redis。

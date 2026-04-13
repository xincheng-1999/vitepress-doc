---
title: Java 操作 Redis
---

# Java 操作 Redis

在 [上一章](/back-end/database/redis/intro) 中我们用 `redis-cli` 手动操作 Redis。本章学习如何在 Java / Spring Boot 项目中使用 Redis。

Java 操作 Redis 有两种主要方式：
1. **Jedis**：最经典的 Redis Java 客户端，API 与 redis-cli 命令一一对应。
2. **Spring Data Redis (推荐)**：Spring 封装的统一接口，底层可以用 Jedis 或 Lettuce。

## 1. Jedis：原生客户端

### 1.1 添加依赖

```xml
<dependency>
    <groupId>redis.clients</groupId>
    <artifactId>jedis</artifactId>
    <version>5.1.0</version>
</dependency>
```

### 1.2 基础 CRUD

```java
import redis.clients.jedis.Jedis;

public class JedisDemo {
    public static void main(String[] args) {
        // 1. 连接 Redis（类似 MongoClients.create）
        try (Jedis jedis = new Jedis("localhost", 6379)) {

            // 2. String 操作
            jedis.set("name", "Jack");
            String name = jedis.get("name");
            System.out.println("name = " + name);  // Jack

            // 设置过期时间：60 秒
            jedis.setex("token", 60, "abc123");

            // 自增
            jedis.set("views", "0");
            jedis.incr("views");       // 1
            jedis.incrBy("views", 10); // 11

            // 删除
            jedis.del("name");

            // 3. Hash 操作（存对象）
            jedis.hset("user:1001", "name", "Jack");
            jedis.hset("user:1001", "age", "25");
            jedis.hset("user:1001", "email", "jack@example.com");

            String userName = jedis.hget("user:1001", "name");
            System.out.println("user name = " + userName);  // Jack

            // 获取所有字段
            var allFields = jedis.hgetAll("user:1001");
            System.out.println(allFields);  // {name=Jack, age=25, email=jack@example.com}

            // 4. List 操作
            jedis.lpush("messages", "msg1", "msg2", "msg3");
            var messages = jedis.lrange("messages", 0, -1);
            System.out.println("messages = " + messages);

            // 5. Set 操作
            jedis.sadd("tags", "Java", "Spring", "Redis");
            var tags = jedis.smembers("tags");
            System.out.println("tags = " + tags);

            // 6. Sorted Set 操作（排行榜）
            jedis.zadd("leaderboard", 100, "Alice");
            jedis.zadd("leaderboard", 85, "Bob");
            jedis.zadd("leaderboard", 95, "Jack");

            // TOP 3（从高到低）
            var top3 = jedis.zrevrangeWithScores("leaderboard", 0, 2);
            top3.forEach(t -> System.out.println(t.getElement() + " : " + t.getScore()));
        }
    }
}
```

> **你会发现**：Jedis 的方法名和 redis-cli 命令完全一样——`set`, `get`, `hset`, `hget`, `lpush`, `zadd`...记住命令就会写代码。

### 1.3 存储 JSON 对象

Redis 的 String 类型可以存 JSON 字符串，但需要手动序列化/反序列化：

```java
import com.google.gson.Gson;  // 需要添加 Gson 依赖

Gson gson = new Gson();

// 存：Java 对象 → JSON 字符串 → Redis
User user = new User("Jack", 25, "jack@example.com");
jedis.set("user:jack", gson.toJson(user));

// 取：Redis → JSON 字符串 → Java 对象
String json = jedis.get("user:jack");
User cachedUser = gson.fromJson(json, User.class);
```

> **前端类比**：就像 `localStorage.setItem("user", JSON.stringify(user))` 和 `JSON.parse(localStorage.getItem("user"))`。

## 2. Spring Data Redis (推荐)

在 Spring Boot 项目中，推荐使用 Spring Data Redis。它封装了底层操作，提供统一的 `RedisTemplate`。

### 2.1 添加依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

> Spring Boot Starter 默认使用 **Lettuce** 作为底层客户端（比 Jedis 更现代，支持异步和响应式）。

### 2.2 配置连接

在 `application.yml` 中：

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      # password: your_password  # 如果设置了密码
```

### 2.3 使用 RedisTemplate

```java
package com.example.demo.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Service
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    public CacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ===== String 操作 =====

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void setWithExpire(String key, Object value, long seconds) {
        redisTemplate.opsForValue().set(key, value, seconds, TimeUnit.SECONDS);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    // ===== Hash 操作 =====

    public void hset(String key, String field, Object value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    public Object hget(String key, String field) {
        return redisTemplate.opsForHash().get(key, field);
    }
}
```

`RedisTemplate` 通过不同的 `opsFor*()` 方法操作不同数据类型：

| 方法 | 对应 Redis 类型 |
| :--- | :--- |
| `opsForValue()` | String |
| `opsForHash()` | Hash |
| `opsForList()` | List |
| `opsForSet()` | Set |
| `opsForZSet()` | Sorted Set |

### 2.4 配置序列化 (重要！)

默认的 `RedisTemplate` 使用 Java 序列化，存出来的 key 和 value 是乱码。推荐改成 JSON：

```java
package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // key 用 String 序列化
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // value 用 JSON 序列化
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }
}
```

> 配置后，Redis 中存储的数据是可读的 JSON 格式，用 `redis-cli` 查看时一目了然。

## 3. 实战：接口缓存

最常见的用法是给数据库查询加缓存，减少 MySQL/MongoDB 的压力。

```java
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public UserServiceImpl(UserRepository userRepository,
                           RedisTemplate<String, Object> redisTemplate) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public User findById(String id) {
        String cacheKey = "user:" + id;

        // 1. 先查 Redis 缓存
        User cached = (User) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            System.out.println("命中缓存！");
            return cached;
        }

        // 2. 缓存没有，查数据库
        User user = userRepository.findById(id).orElse(null);
        if (user != null) {
            // 3. 写入缓存（过期时间 30 分钟）
            redisTemplate.opsForValue().set(cacheKey, user, 30, TimeUnit.MINUTES);
        }
        return user;
    }

    @Override
    public void delete(String id) {
        userRepository.deleteById(id);
        // 删除时要同步清除缓存！否则会读到旧数据
        redisTemplate.delete("user:" + id);
    }

    @Override
    public User update(String id, User userParams) {
        User updated = userRepository.save(userParams);
        // 更新后也要清除缓存
        redisTemplate.delete("user:" + id);
        return updated;
    }
}
```

**缓存策略说明：**

```text
读操作：Redis 有 → 直接返回（快！）
       Redis 没有 → 查 DB → 写入 Redis → 返回

写/删操作：操作 DB → 删除 Redis 缓存（保证数据一致性）
```

> **前端类比**：这和 SWR/React Query 的 `stale-while-revalidate` 策略非常像——优先用缓存数据，后台去刷新。

## 4. Spring Cache 注解 (更优雅的方式)

Spring 还提供了注解方式，直接在方法上标记缓存逻辑，不用手动写 RedisTemplate：

```java
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

@Service
public class UserServiceImpl implements UserService {

    @Cacheable(value = "users", key = "#id")  // 自动缓存返回值
    @Override
    public User findById(String id) {
        // 第一次调用：查数据库，结果自动存入 Redis
        // 后续调用：直接从 Redis 返回，这个方法体不会执行
        return userRepository.findById(id).orElse(null);
    }

    @CacheEvict(value = "users", key = "#id")  // 自动清除缓存
    @Override
    public void delete(String id) {
        userRepository.deleteById(id);
    }
}
```

需要在启动类上开启缓存：

```java
@SpringBootApplication
@EnableCaching  // ← 加上这个注解
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

## 5. Jedis vs Spring Data Redis 对比

| 对比项 | Jedis | Spring Data Redis |
| :--- | :--- | :--- |
| 定位 | 原生客户端 | Spring 封装 |
| API 风格 | 与 redis-cli 命令一致 | `RedisTemplate` + 注解 |
| 连接管理 | 手动管理 | Spring 自动管理 |
| 序列化 | 手动 (Gson 等) | 配置后自动 |
| 注解缓存 | 不支持 | `@Cacheable` / `@CacheEvict` |
| 推荐场景 | 学习原理、非 Spring 项目 | Spring Boot 项目 |

## 总结

*   **Jedis** 适合理解 Redis 操作原理，API 和 redis-cli 一一对应。
*   **Spring Data Redis** 适合实际项目开发，`RedisTemplate` + `@Cacheable` 注解让缓存变得透明。
*   最常见的实战模式是**数据库查询缓存**：先查 Redis，没有再查 DB，写入缓存。
*   写操作后一定要**清除缓存**，避免数据不一致。

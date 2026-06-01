# 综合实战：从 0 到上线再到排障

> 这是全课程的压轴章。前面 37 章拆开讲的所有零件——分层、Spring、数据库、Redis、并发、MQ、容器、K8s、日志、排障——这一章要把它们拧成一条完整的工程流水线。
>
> 我们接一个真实需求：给 AI 生图站点的用户加一个 **"我的作品收藏"** 功能。从设计 API 开始，一路写代码、加缓存、做异步、保并发、写测试、打镜像、上 K8s、接告警，最后故意把它搞慢，再按手册定位修复。
>
> 💡 **前端类比**：把这一章当成一次"端到端交付演练"。就像你在前端从 Figma 拿到需求 → 写组件 → 接口联调 → 加缓存（SWR）→ 写 vitest → 打包 → 部署 Vercel → 看 Sentry 报错那条完整链路。后端的流程一模一样，只是每个环节的工具不同。

---

## 38.0 需求与全景图

产品需求一句话：**用户可以收藏 / 取消收藏一幅作品，并查看自己的收藏列表；作品详情页要显示"被收藏数"。**

这个看似简单的功能，落到后端要碰到的工程点几乎覆盖了整门课。先看一张全景图，标注每一步对应课程的哪个 Part：

```text
┌─────────────────────────────────────────────────────────────┐
│  「我的作品收藏」功能 端到端                                    │
├─────────────────────────────────────────────────────────────┤
│  ① 设计 API        REST 资源 / RtData / 错误码      → Part 接口 │
│  ② 写代码          Entity→Repo→Service→Controller   → Part 编码 │
│  ③ 加缓存          收藏列表 / 收藏数 走 Redis        → Part 缓存 │
│  ④ 异步统计        收藏数变更发 RocketMQ → 更新热度  → Part MQ   │
│  ⑤ 并发安全        唯一索引 + 幂等键防重复收藏       → Part 并发 │
│  ⑥ 写测试          Service 单测 + Controller 集成测  → Part 测试 │
│  ⑦ 容器化          Dockerfile → docker build         → Part 容器 │
│  ⑧ 部署            push → K8s apply → rollout          → Part 部署 │
│  ⑨ 可观测          traceId 日志 + Actuator 指标 + 告警 → Part 观测 │
│  ⑩ 排障演练        接口变慢 → 日志 + jstack/arthas    → Part 排障 │
└─────────────────────────────────────────────────────────────┘
```

功能落在 `svc-user` 服务里（它本来就管用户、配额、支付，收藏归用户资产很合理）。涉及的作品数据在 `svc-canvas`，我们通过 `cpt-api` 里的 Feign 客户端读作品基本信息。

---

## 38.1 第一步：设计 API（REST 资源 + RtData + 错误码）

写代码前先定接口。把"收藏"当成一种**资源**，用 REST 的方式建模。

| 方法 | 路径 | 作用 | 幂等? | 前端类比 |
| --- | --- | --- | --- | --- |
| `POST` | `/v1/favorites` | 收藏一幅作品 | 是（重复收藏不报错） | `axios.post('/favorites', { artworkId })` |
| `DELETE` | `/v1/favorites/{artworkId}` | 取消收藏 | 是（没收藏过也当成功） | `axios.delete('/favorites/123')` |
| `GET` | `/v1/favorites?page=0&size=20` | 我的收藏列表（分页） | 是 | `axios.get('/favorites?page=0')` |

约定（接口设计细节见 [API 设计](/back-end/frontend-backend-guide/34-api-design)）：

- 用户身份不放在 body 里。网关 `svc-gateway` 校验 token 后，把 `uid` 解析出来放进请求头 `uid`，下游服务直接 `@RequestHeader("uid")` 取，**绝不信任前端传上来的 uid**。
- 所有响应统一用 `RtData` 包成 `{ code, data, message }`，`code: 0` 为成功。
- 错误码集中定义在 `cpt-common` 里，按"业务域 + 子码"编排，便于排查时一眼定位：

```java
// cpt-common: com.aigen.common.exception.FavoriteErrorCode
public enum FavoriteErrorCode {
    ARTWORK_NOT_FOUND(44001, "作品不存在"),
    FAVORITE_LIMIT_EXCEEDED(44002, "收藏数量已达上限"),
    ARTWORK_SERVICE_UNAVAILABLE(44003, "作品服务暂时不可用");

    private final int code;
    private final String msg;
    // 构造器 + getter 略
}
```

> 💡 **前端类比**：这一步就是你和后端约定接口契约的过程——只不过现在你站在另一边。`RtData` 等价于你前端封装 axios 时约定的"统一返回壳"；错误码枚举等价于你在前端定义的 `const ErrorCode = { ... }` 常量表。先把契约钉死，前后端就能并行开发。

接口契约定下来，前端就能先用 mock 数据开工，后端这边开始写实现。

---

## 38.2 第二步：写代码（Entity → Repository → Service → Controller）

按经典三层结构落地（分层原理见 [三层架构与目录结构](/back-end/frontend-backend-guide/04-three-layer-and-structure)，CRUD 套路见 [写一个 CRUD 接口](/back-end/frontend-backend-guide/07-build-a-crud-api)）。

### Entity

收藏关系本质是 `(uid, artworkId)` 的关联，主库 MongoDB：

```java
package com.aigen.user.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Data
@Document(collection = "favorite")
// 复合唯一索引：同一用户对同一作品只能有一条记录（并发防重的根本保障，见 38.5）
@CompoundIndex(name = "uniq_uid_artwork", def = "{'uid': 1, 'artworkId': 1}", unique = true)
public class Favorite {
    @Id
    private String id;
    private Long uid;          // 收藏者
    private String artworkId;  // 被收藏的作品 ID
    private Instant createdAt; // 收藏时间，列表按它倒序
}
```

### Repository

```java
package com.aigen.user.repository;

import com.aigen.user.entity.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FavoriteRepository extends MongoRepository<Favorite, String> {
    Page<Favorite> findByUidOrderByCreatedAtDesc(Long uid, Pageable pageable);
    long countByArtworkId(String artworkId);
    void deleteByUidAndArtworkId(Long uid, String artworkId);
    boolean existsByUidAndArtworkId(Long uid, String artworkId);
}
```

### Service

业务逻辑核心。注意构造器注入、无可变成员字段（并发安全的前提，38.5 详谈）、抛 `BizException` 交给全局异常处理器：

```java
package com.aigen.user.service;

import com.aigen.api.canvas.CanvasFeignClient; // cpt-api 里的 Feign 客户端
import com.aigen.common.exception.BizException;
import com.aigen.user.entity.Favorite;
import com.aigen.user.repository.FavoriteRepository;
import com.aigen.user.vo.FavoriteVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Slf4j
@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final CanvasFeignClient canvasClient;
    private final FavoriteCache favoriteCache;      // 见 38.3
    private final FavoriteEventPublisher publisher;  // 见 38.4

    public FavoriteService(FavoriteRepository favoriteRepository,
                           CanvasFeignClient canvasClient,
                           FavoriteCache favoriteCache,
                           FavoriteEventPublisher publisher) {
        this.favoriteRepository = favoriteRepository;
        this.canvasClient = canvasClient;
        this.favoriteCache = favoriteCache;
        this.publisher = publisher;
    }

    /** 收藏：幂等。已收藏过直接返回成功，不重复插入。 */
    public void add(Long uid, String artworkId) {
        // 1. 校验作品存在（跨服务调用 svc-canvas）
        if (!canvasClient.exists(artworkId)) {
            throw new BizException(44001, "作品不存在");
        }
        // 2. 幂等短路：已收藏过就直接返回（避免触发唯一索引异常）
        if (favoriteRepository.existsByUidAndArtworkId(uid, artworkId)) {
            return;
        }
        try {
            Favorite fav = new Favorite();
            fav.setUid(uid);
            fav.setArtworkId(artworkId);
            fav.setCreatedAt(Instant.now());
            favoriteRepository.save(fav);
        } catch (DuplicateKeyException e) {
            // 并发下两个请求都过了 existsBy 检查 → 唯一索引兜底，吞掉当成功（幂等）
            log.warn("duplicate favorite ignored, uid={}, artworkId={}", uid, artworkId);
            return;
        }
        favoriteCache.evict(uid, artworkId);          // 删缓存（cache-aside，见 38.3）
        publisher.publishChanged(artworkId, +1);       // 发 MQ 异步更新热度（见 38.4）
    }

    /** 取消收藏：幂等。没收藏过也当成功。 */
    public void remove(Long uid, String artworkId) {
        favoriteRepository.deleteByUidAndArtworkId(uid, artworkId);
        favoriteCache.evict(uid, artworkId);
        publisher.publishChanged(artworkId, -1);
    }

    /** 我的收藏列表（分页，倒序）。 */
    public Page<FavoriteVO> myList(Long uid, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return favoriteRepository.findByUidOrderByCreatedAtDesc(uid, pageable)
                .map(this::toVO);
    }

    private FavoriteVO toVO(Favorite f) {
        FavoriteVO vo = new FavoriteVO();
        vo.setArtworkId(f.getArtworkId());
        vo.setCreatedAt(f.getCreatedAt().toString());
        return vo;
    }
}
```

### Controller（@Validated 校验）

```java
package com.aigen.user.controller;

import com.aigen.common.response.RtData;
import com.aigen.user.dto.FavoriteAddDTO;
import com.aigen.user.service.FavoriteService;
import com.aigen.user.vo.FavoriteVO;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping
    public RtData<Void> add(@RequestHeader("uid") Long uid,
                            @Validated @RequestBody FavoriteAddDTO dto) {
        favoriteService.add(uid, dto.getArtworkId());
        return RtData.ok(null);
    }

    @DeleteMapping("/{artworkId}")
    public RtData<Void> remove(@RequestHeader("uid") Long uid,
                               @PathVariable String artworkId) {
        favoriteService.remove(uid, artworkId);
        return RtData.ok(null);
    }

    @GetMapping
    public RtData<Page<FavoriteVO>> myList(@RequestHeader("uid") Long uid,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return RtData.ok(favoriteService.myList(uid, page, size));
    }
}
```

DTO 上的 `@NotBlank` 配合 Controller 入参的 `@Validated` 才会触发校验（声明式校验 ≈ `zod`）：

```java
@Data
public class FavoriteAddDTO {
    @NotBlank(message = "作品 ID 不能为空")
    private String artworkId;
}
```

> Java 语法、异常机制、Spring IoC/DI 的原理分别见 [Java 速成](/back-end/frontend-backend-guide/05-java-crash-course)、[Spring Boot 与 IoC/DI](/back-end/frontend-backend-guide/06-spring-boot-ioc-di)，以及外部的 [Spring IoC/DI 详解](/back-end/java/07a-spring-ioc-di) 与 [异常处理](/back-end/java/05-exception)。

---

## 38.3 第三步：加缓存（Redis cache-aside）

"作品被收藏数" `count` 和"我的收藏列表"都是读多写少的热点数据，直接每次查 Mongo 会扛不住。用 **cache-aside（旁路缓存）** 模式接 Redis（原理与坑见 [Redis 实战](/back-end/frontend-backend-guide/12-redis-in-practice)）。

cache-aside 的三条铁律：

```text
读：先查缓存 → 命中直接返回；未命中 → 查 DB → 回填缓存 → 返回
写：先更新 DB → 再删除缓存（不是更新缓存！）
顺序：必须"先写库，后删缓存"，否则并发下会缓存脏数据
```

```java
package com.aigen.user.service;

import com.aigen.user.repository.FavoriteRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
public class FavoriteCache {

    private static final String CNT_KEY = "fav:count:";       // fav:count:{artworkId}
    private static final String LIST_KEY = "fav:list:";        // fav:list:{uid}

    private final StringRedisTemplate redis;
    private final FavoriteRepository repository;

    public FavoriteCache(StringRedisTemplate redis, FavoriteRepository repository) {
        this.redis = redis;
        this.repository = repository;
    }

    /** 读收藏数：cache-aside */
    public long getCount(String artworkId) {
        String key = CNT_KEY + artworkId;
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            return Long.parseLong(cached);           // 命中
        }
        long count = repository.countByArtworkId(artworkId);  // 未命中查库
        // 回填，加随机过期防雪崩；用 set 而非 incr 保证以 DB 为准
        redis.opsForValue().set(key, String.valueOf(count),
                Duration.ofMinutes(10 + (int) (Math.random() * 5)));
        return count;
    }

    /** 写操作后删缓存（不是更新），下次读自然回填最新值 */
    public void evict(Long uid, String artworkId) {
        redis.delete(CNT_KEY + artworkId);
        redis.delete(LIST_KEY + uid);
    }
}
```

为什么是"删缓存"而不是"更新缓存"？因为更新缓存要求你算出最新值再写回，并发下两个写请求的回写顺序无法保证，容易留下旧值；删除则把"算最新值"的责任推迟到下一次读（读时回填），简单且不易错。

> 💡 **前端类比**：cache-aside 就是 SWR / React Query 的服务端版。`getCount` 命中缓存 ≈ SWR 直接返回 `cache`；未命中查库回填 ≈ `fetcher` 跑完写进 cache。`evict` 就是数据变更后调 `mutate(key)` 让缓存失效、下次重新拉取。

---

## 38.4 第四步：异步统计（RocketMQ + 消费端幂等）

收藏数变了，要顺带更新作品的"热度分"（用于推荐排序，存在 MySQL 统计库）。这个动作**不应该卡在收藏接口的主流程里**——热度计算慢、还可能失败，不能因此拖慢或拖垮用户的收藏操作。把它做成异步：收藏成功后发一条 MQ 消息，由消费端慢慢更新（可靠性细节见 [消息队列可靠性](/back-end/frontend-backend-guide/33-mq-reliability)）。

### 生产端：发消息

```java
package com.aigen.user.service;

import com.aigen.api.dto.FavoriteChangedEvent; // cpt-api 共享 DTO
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

@Component
public class FavoriteEventPublisher {

    private static final String TOPIC = "favorite-changed";
    private final RocketMQTemplate rocketMQTemplate;

    public FavoriteEventPublisher(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    public void publishChanged(String artworkId, int delta) {
        FavoriteChangedEvent event = new FavoriteChangedEvent();
        event.setArtworkId(artworkId);
        event.setDelta(delta);
        // eventId 作为幂等键：用 artworkId + 当前秒级时间戳生成，消费端据此去重
        event.setEventId(artworkId + ":" + System.currentTimeMillis());
        rocketMQTemplate.convertAndSend(TOPIC, event);
    }
}
```

### 消费端：幂等消费

MQ 的投递语义是 **at-least-once（至少一次）**——同一条消息可能被投递多次（网络抖动、消费超时重试都会导致）。所以消费端**必须幂等**：处理一次和处理多次结果相同。用 Redis 的 `SETNX` 记录已处理的 `eventId` 来去重（一致性原理见 [事务与一致性](/back-end/frontend-backend-guide/11-transactions-consistency)）：

```java
package com.aigen.user.mq;

import com.aigen.api.dto.FavoriteChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RocketMQMessageListener(topic = "favorite-changed", consumerGroup = "svc-user-hotness")
public class FavoriteChangedConsumer implements RocketMQListener<FavoriteChangedEvent> {

    private final StringRedisTemplate redis;
    private final HotnessService hotnessService;

    public FavoriteChangedConsumer(StringRedisTemplate redis, HotnessService hotnessService) {
        this.redis = redis;
        this.hotnessService = hotnessService;
    }

    @Override
    public void onMessage(FavoriteChangedEvent event) {
        String idemKey = "mq:idem:fav:" + event.getEventId();
        // SETNX：只有第一次能设成功，重复投递的消息会失败 → 直接跳过
        Boolean first = redis.opsForValue()
                .setIfAbsent(idemKey, "1", Duration.ofHours(1));
        if (Boolean.FALSE.equals(first)) {
            log.info("duplicate mq event skipped, eventId={}", event.getEventId());
            return; // 幂等：已处理过，直接返回
        }
        hotnessService.updateHotness(event.getArtworkId(), event.getDelta());
        log.info("hotness updated, artworkId={}, delta={}", event.getArtworkId(), event.getDelta());
    }
}
```

> 💡 **前端类比**：发 MQ 消息就像在前端把一个非关键任务丢进 `requestIdleCallback` 或后台 worker——主流程（用户点收藏）立刻返回，重活后台慢慢干。幂等键则像前端给请求带的 `idempotencyKey`，防止用户连点两次重复提交。

---

## 38.5 第五步：并发安全（防重复收藏）

并发场景：用户在弱网下连点两次收藏，两个请求几乎同时到达，都通过了 `existsByUidAndArtworkId` 检查（此时 DB 里都还没记录），然后都执行 `save`——如果没防护就会插入两条重复记录。

两道防线（线程安全与锁的原理见 [线程安全与锁](/back-end/frontend-backend-guide/16-thread-safety-locks)）：

1. **数据库唯一索引兜底（根本保障）**：38.2 的 Entity 上已经建了 `@CompoundIndex(unique = true)`。即使应用层判断漏了，第二条 `save` 会抛 `DuplicateKeyException`，被我们 `catch` 后吞掉当成功——并发下数据库的唯一约束是最后一道、也是最可靠的防线。
2. **Service 无可变成员字段**：`FavoriteService` 的所有字段都是 `final` 的依赖（Repository、Cache、Publisher），没有任何可变实例状态。Spring 的 Bean 默认是单例、被多线程共享，**只要不在 Bean 里存可变状态，就天然线程安全**。

```text
请求A ──┐  existsBy? false ┐
        ├─ 同时到达 ────────┤  都尝试 save
请求B ──┘  existsBy? false ┘
                              │
                    ┌─────────┴──────────┐
                  save 成功            save 抛 DuplicateKeyException
                    │                     │
                  正常返回           catch 吞掉 → 也返回成功（幂等）
                                          │
                              DB 里始终只有一条记录 ✓
```

> 💡 **前端类比**：Service 单例多线程共享，等价于一个被所有请求复用的模块级单例对象。前端单线程不用担心这个，但 Node.js 里如果在模块作用域放可变的 `let counter` 给所有请求改，也会出并发竞争——后端的"Bean 无可变成员"就是同一条纪律。唯一索引则像数据库帮你做的"最后一道 schema 校验"。

> ⚠️ 注意：不要用应用内的 `synchronized` 锁来防重复——多实例部署时（K8s 里 3 个 Pod）进程内的锁互相不可见，根本拦不住跨实例的并发。要么靠 DB 唯一索引，要么用 Redis 分布式锁。这里 DB 唯一索引已经够用，不必上分布式锁。

---

## 38.6 第六步：写测试（Service 单测 + Controller 集成测试）

代码写完先别急着提交，补两类测试（测试策略见 [测试](/back-end/frontend-backend-guide/35-testing)）。

### Service 单元测试：mock Repository

只测业务逻辑，把数据库、Feign、MQ 全 mock 掉，跑得快：

```java
package com.aigen.user.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class FavoriteServiceTest {

    private final FavoriteRepository repo = mock(FavoriteRepository.class);
    private final CanvasFeignClient canvas = mock(CanvasFeignClient.class);
    private final FavoriteCache cache = mock(FavoriteCache.class);
    private final FavoriteEventPublisher publisher = mock(FavoriteEventPublisher.class);

    private final FavoriteService service =
            new FavoriteService(repo, canvas, cache, publisher);

    @Test
    void add_whenAlreadyFavorited_shouldBeIdempotent() {
        when(canvas.exists("art-1")).thenReturn(true);
        when(repo.existsByUidAndArtworkId(100L, "art-1")).thenReturn(true);

        service.add(100L, "art-1");

        // 已收藏过：不应再 save、不应发 MQ
        verify(repo, never()).save(any());
        verify(publisher, never()).publishChanged(anyString(), anyInt());
    }

    @Test
    void add_whenArtworkNotExists_shouldThrow() {
        when(canvas.exists("ghost")).thenReturn(false);
        BizException ex = assertThrows(BizException.class,
                () -> service.add(100L, "ghost"));
        assertEquals(44001, ex.getCode());
    }
}
```

> 💡 **前端类比**：`mock(FavoriteRepository.class)` 就是 `vi.fn()` / `jest.mock()`——把依赖换成假对象，`when(...).thenReturn(...)` 是 `mockReturnValue`，`verify(...)` 是 `expect(fn).toHaveBeenCalled()`。心智模型和你写 vitest 一模一样。

### Controller 集成测试：MockMvc

不启动真服务器，直接模拟 HTTP 请求打到 Controller，验证状态码和返回 JSON：

```java
@WebMvcTest(FavoriteController.class)
class FavoriteControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private FavoriteService favoriteService;

    @Test
    void add_shouldReturnOk() throws Exception {
        mockMvc.perform(post("/v1/favorites")
                .header("uid", "100")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"artworkId\":\"art-1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void add_whenArtworkIdBlank_shouldReturn400() throws Exception {
        mockMvc.perform(post("/v1/favorites")
                .header("uid", "100")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"artworkId\":\"\"}"))   // 触发 @NotBlank
            .andExpect(status().isBadRequest());
    }
}
```

跑测试：

```bash
mvn -pl svc-user test
```

**预期输出**：

```text
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**怎么读**：`Failures: 0, Errors: 0` 才算过。`Failures` 是断言不通过，`Errors` 是测试代码本身抛了异常（比如空指针）。绿了才进入下一步。

---

## 38.7 第七步：容器化（Dockerfile）

代码和测试都 OK，打成镜像（Docker 细节见 [Docker 实战](/back-end/frontend-backend-guide/23-docker-in-practice)）。用多阶段构建：第一阶段编译，第二阶段只拷产物，镜像更小：

```text
# svc-user/Dockerfile
# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q clean package -DskipTests

# ---- 运行阶段 ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/svc-user.jar app.jar
EXPOSE 8082
# 容器里堆内存按 Pod limit 自适应，比写死 -Xmx 更稳（见 OOM 章）
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

构建并本地验证：

```bash
docker build -t registry.example.com/aigen/svc-user:v1.4.0 ./svc-user
docker run --rm -p 8082:8082 registry.example.com/aigen/svc-user:v1.4.0
```

**预期输出**（容器内 Spring Boot 启动日志末尾）：

```text
2026-06-01 09:12:03.451  INFO 1 --- [main] c.a.user.UserApplication : Started UserApplication in 6.214 seconds
2026-06-01 09:12:03.460  INFO 1 --- [main] o.s.b.w.embedded.tomcat.TomcatWebServer : Tomcat started on port 8082
```

**怎么读**：看到 `Started UserApplication` 和 `Tomcat started on port 8082` 就说明镜像能跑、端口能监听。`PID` 显示是 `1`（容器里 Java 进程就是 1 号进程），符合预期。

> 💡 **前端类比**：多阶段构建 = 前端的 `build` 产物只拷 `dist` 进 nginx 镜像，不把 `node_modules` 和源码塞进去。`-XX:MaxRAMPercentage` 类似你给 Node 设 `--max-old-space-size`，只是这里让它按容器内存上限自动算，不写死。

---

## 38.8 第八步：部署（推镜像 → K8s apply → rollout）

镜像推到仓库，再用 K8s 部署（K8s 与发布流程见 [Kubernetes 实战](/back-end/frontend-backend-guide/24-kubernetes-in-practice) 与 [CI/CD 与部署](/back-end/frontend-backend-guide/36-cicd-deployment)）。

```bash
docker push registry.example.com/aigen/svc-user:v1.4.0
```

Deployment 关键片段（重点是 `readinessProbe`——它决定 Pod 什么时候开始接流量）：

```yaml
# svc-user-deploy.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: svc-user
spec:
  replicas: 3
  selector:
    matchLabels: { app: svc-user }
  template:
    metadata:
      labels: { app: svc-user }
    spec:
      containers:
        - name: svc-user
          image: registry.example.com/aigen/svc-user:v1.4.0
          ports:
            - containerPort: 8082
          # 就绪探针：探通了才把这个 Pod 加进 Service 的负载均衡
          readinessProbe:
            httpGet: { path: /actuator/health/readiness, port: 8082 }
            initialDelaySeconds: 10
            periodSeconds: 5
          # 存活探针：探不通就重启容器
          livenessProbe:
            httpGet: { path: /actuator/health/liveness, port: 8082 }
            initialDelaySeconds: 30
            periodSeconds: 10
          resources:
            requests: { cpu: "500m", memory: "512Mi" }
            limits: { cpu: "1", memory: "1Gi" }
```

应用并观察滚动发布：

```bash
kubectl apply -f svc-user-deploy.yaml
kubectl rollout status deployment/svc-user
```

**预期输出**：

```text
deployment.apps/svc-user configured
Waiting for deployment "svc-user" rollout to finish: 1 out of 3 new replicas have been updated...
Waiting for deployment "svc-user" rollout to finish: 2 out of 3 new replicas have been updated...
deployment "svc-user" successfully rolled out
```

**怎么读**：K8s 按"滚动更新"逐个替换旧 Pod——先起 1 个新 Pod，readiness 探通后才删 1 个旧 Pod，如此往复，全程不中断服务。最后看到 `successfully rolled out` 才算上线成功。如果卡在某一行不动，多半是新 Pod 的 readiness 探针一直不通（启动失败/连不上依赖），这时去 `kubectl logs` 看启动日志。

> 💡 **前端类比**：`readinessProbe` 就是"页面 onReady 之前别让用户访问"。滚动发布 ≈ Vercel 的渐进式部署——新版本就绪后才把流量切过去，旧版本不会被立刻砍掉，所以用户无感。

---

## 38.9 第九步：接入可观测（traceId 日志 + Actuator 指标 + 告警）

上线不等于结束，要能"看见"它在线上的状态（三件套见 [可观测三件套](/back-end/frontend-backend-guide/30-observability)，读日志见 [看懂日志](/back-end/frontend-backend-guide/26-reading-logs)）。

### 1. 日志带 traceId

每个请求在网关入口生成一个 `traceId`，透传到所有下游服务并打进每行日志。这样一次请求跨多个服务的所有日志都能用同一个 `traceId` 串起来：

```java
// 在 Service 关键路径打 info / error，日志格式里带 traceId（由 MDC 注入）
log.info("favorite add, uid={}, artworkId={}", uid, artworkId);
// ...
log.error("call svc-canvas failed, artworkId={}", artworkId, e); // 异常对象作最后一个参数，会打出堆栈
```

日志样例（logback 模式串里配了 `%X{traceId}`）：

```text
2026-06-01 09:30:11.102 INFO  [svc-user] [a1b2c3d4e5f60718] FavoriteService - favorite add, uid=10086, artworkId=art-9527
2026-06-01 09:30:11.345 ERROR [svc-user] [a1b2c3d4e5f60718] FavoriteService - call svc-canvas failed, artworkId=art-9527
feign.RetryableException: Read timed out executing GET http://svc-canvas/v1/artwork/art-9527/exists
	at ...
```

**怎么读**：方括号里 `a1b2c3d4e5f60718` 是 traceId。拿它去日志平台一搜，就能看到这次请求从网关 → svc-user → svc-canvas 的完整链路。上面这两行告诉我们：收藏请求进来了，但调 svc-canvas 校验作品时 `Read timed out`——下游超时。这正是 38.10 排障要用的线索。

### 2. 暴露 Actuator 指标

`pom.xml` 引入 `spring-boot-starter-actuator` + `micrometer-registry-prometheus`，配置暴露端点：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      probes:
        enabled: true   # 启用 38.8 用到的 /health/liveness 和 /health/readiness
```

```bash
curl -s http://localhost:8082/actuator/prometheus | grep http_server_requests
```

**预期输出**：

```text
http_server_requests_seconds_count{method="POST",uri="/v1/favorites",status="200"} 1432.0
http_server_requests_seconds_sum{method="POST",uri="/v1/favorites",status="200"} 18.7
```

**怎么读**：`count` 是这个接口被调了 1432 次，`sum` 是总耗时 18.7 秒 → 平均 RT ≈ 13ms。Prometheus 定期抓这些指标，Grafana 画成曲线，就能看到 QPS、P99 延迟、错误率随时间的变化。

### 3. 配关键告警

基于指标设三条告警规则（用文字描述，落到 Prometheus rule 或告警平台）：

- **错误率告警**：`/v1/favorites` 5xx 占比 > 1% 持续 2 分钟 → 企业微信通知。
- **延迟告警**：该接口 P99 > 500ms 持续 5 分钟 → 通知。
- **健康告警**：`svc-user` 就绪 Pod 数 < 2 → 紧急通知（容量不足）。

> 💡 **前端类比**：traceId 就是前端 Sentry 里的 trace/span，把一次用户操作的所有上报串成一条时间线。Actuator 指标 ≈ 你接的前端性能监控（Web Vitals 上报）。告警 ≈ Sentry 的 alert rule——错误率飙了自动 @你。

---

## 38.10 第十步：排障演练（接口变慢 → 定位 → 修复）

上线一周后，告警响了：**"`/v1/favorites` P99 > 500ms 持续 5 分钟"**。下面按 [排查实战手册](/back-end/frontend-backend-guide/28-troubleshooting-playbook) 的套路，用 [排查工具箱](/back-end/frontend-backend-guide/29-diagnostic-toolbox) 里的工具定位。

### 症状

前端反馈点收藏按钮"转圈好几秒"。Grafana 上 `/v1/favorites` 的 P99 从 15ms 涨到 3s，但 QPS 没明显上涨，错误率没飙——不是被打爆，是单次变慢了。

### 第一步：看日志找 traceId

```bash
kubectl logs deploy/svc-user --since=10m | grep -i "favorite add" | head
```

**预期输出**：

```text
2026-06-01 14:02:11.102 INFO  [svc-user] [9f8e7d6c5b4a3210] FavoriteService - favorite add, uid=10086, artworkId=art-9527
2026-06-01 14:02:14.330 INFO  [svc-user] [9f8e7d6c5b4a3210] FavoriteService - favorite saved, uid=10086, artworkId=art-9527
```

**怎么读**：同一个 traceId `9f8e...` 的两行日志，时间戳从 `14:02:11.102` 到 `14:02:14.330` 跨了 **3.2 秒**。慢就慢在 `add` 进入到 `saved` 之间——这中间做了三件事：调 svc-canvas 校验、查 existsBy、save。需要进一步定位到底慢在哪一步。

### 第二步：jstack 看线程都卡在哪

抓一份线程快照，看请求线程的栈停在什么调用上：

```bash
# 找到 Java 进程（容器里通常是 PID 1）
kubectl exec deploy/svc-user -- jstack 1 > /tmp/jstack.txt
grep -A 15 "http-nio-8082-exec" /tmp/jstack.txt | head -40
```

**预期输出**：

```text
"http-nio-8082-exec-7" #45 daemon prio=5 ... waiting on condition
   java.lang.Thread.State: TIMED_WAITING (parking)
	at sun.nio.ch.SocketChannelImpl.read(...)
	at feign.Client$Default.execute(...)
	at com.aigen.api.canvas.CanvasFeignClient.exists(...)
	at com.aigen.user.service.FavoriteService.add(FavoriteService.java:42)
```

**怎么读**：多个 `http-nio-8082-exec-*` 工作线程都卡在 `CanvasFeignClient.exists` 的 socket `read` 上，状态是 `TIMED_WAITING`。**结论很明确：慢在调用 svc-canvas 这个下游**，请求线程都在等 svc-canvas 响应。

### 第三步：arthas trace 量化每步耗时

用 arthas 在线 trace 一下 `add` 方法，看各子调用耗时占比：

```bash
kubectl exec -it deploy/svc-user -- sh -c "java -jar arthas-boot.jar 1"
# arthas 控制台里执行：
trace com.aigen.user.service.FavoriteService add
```

**预期输出**：

```text
`---ts=2026-06-01 14:05:33; [cost=3187.4ms] com.aigen.user.service.FavoriteService:add()
    +---[3180.1ms] com.aigen.api.canvas.CanvasFeignClient:exists()   # 99% 耗时在这
    +---[2.1ms] ...FavoriteRepository:existsByUidAndArtworkId()
    `---[1.8ms] ...FavoriteRepository:save()
```

**怎么读**：`add` 总耗时 3187ms，其中 `CanvasFeignClient.exists()` 占了 **3180ms（99%）**，DB 操作各只有 1-2ms。彻底坐实：**根因是 svc-canvas 的 `/exists` 接口变慢**（它内部可能在查一张没加索引的大表，或自身依赖出了问题）。

### 修复

分两个层面（短期止血 + 长期治理）：

1. **短期止血**：给 Feign 调用加超时 + 熔断降级。svc-canvas 慢，不该把 svc-user 的线程全拖死。设 800ms 超时，超时/失败时走降级——校验作品存在性这步可以"信任前端 + 异步补偿"：先放行收藏，靠后续 MQ 流程兜底校验。

```java
// Feign 客户端配置超时与降级（fallback）
@FeignClient(name = "svc-canvas", fallback = CanvasFeignFallback.class,
             configuration = CanvasFeignConfig.class) // 内含 connectTimeout/readTimeout=800ms
public interface CanvasFeignClient {
    @GetMapping("/v1/artwork/{id}/exists")
    boolean exists(@PathVariable String id);
}
```

2. **长期治理**：推动 svc-canvas 团队给 `/exists` 走的查询加索引（SQL 索引见 [SQL 与索引](/back-end/frontend-backend-guide/10-sql-and-indexes)），或者把"作品是否存在"做成 svc-user 本地缓存（Redis 存一份 artworkId 集合），避免每次收藏都跨服务实时查。

修复发布后回看 Grafana：`/v1/favorites` P99 从 3s 回落到 20ms，告警自动恢复。**排障闭环完成：告警 → 日志定位下游 → jstack 确认卡点 → arthas 量化根因 → 加超时熔断止血 → 推动下游治理。**

> 💡 **前端类比**：这套路你其实做过——页面卡了，先看 Network 面板哪个请求慢（≈ 看日志找慢的 traceId），再用 Performance 火焰图看主线程卡在哪个函数（≈ jstack/arthas trace），定位到某个 API 慢，于是加 `AbortController` 超时 + 兜底 UI（≈ Feign 超时 + 降级）。工具不同，思路完全一致。

---

## 38.11 回顾：这一条线串起了整门课

我们用一个收藏功能，走完了后端工程师交付一个特性的完整生命周期。回头看，它覆盖了课程的每个 Part：

```text
设计 API   → 接口设计、REST、RtData、错误码          (Part 接口)
写代码     → 分层、Spring、IoC/DI、@Validated、异常    (Part 编码)
加缓存     → Redis、cache-aside、删缓存时机、防雪崩     (Part 数据/缓存)
异步统计   → RocketMQ、at-least-once、消费幂等          (Part 消息)
并发安全   → 唯一索引、Bean 无可变状态、不滥用本地锁     (Part 并发)
写测试     → Mockito 单测、MockMvc 集成测试             (Part 测试)
容器化     → Dockerfile、多阶段构建、内存自适应          (Part 容器)
部署       → 镜像仓库、K8s Deployment、探针、滚动发布    (Part 部署)
可观测     → traceId 日志、Actuator/Prometheus、告警    (Part 观测)
排障       → 看日志 → jstack → arthas trace → 修复闭环  (Part 排障)
```

每一步出问题时该翻哪一章，本章都给了链接。把这张图收藏起来——以后接任何后端需求，都可以照着这条线自检：**我的 API 契约清晰吗？分层到位吗？热点加缓存了吗？慢操作异步了吗？并发安全吗？有测试吗？镜像能跑吗？探针配了吗？日志能追踪吗？出问题我会查吗？**

走到这里，你已经不只是"看得懂后端代码"，而是**能独立设计、实现、测试、部署一个后端功能，并在它上线后保障其健康运行、出问题时自己定位修复**。这正是这门课从第一章 [后端思维转变](/back-end/frontend-backend-guide/01-backend-mindset) 出发要带你到达的终点。

---

## 小结

- 交付一个后端功能不是"把接口写出来"，而是一条完整流水线：**设计 → 编码 → 缓存 → 异步 → 并发 → 测试 → 容器 → 部署 → 观测 → 排障**，缺一环都可能在线上翻车。
- 工程纪律比技巧更重要：API 先定契约、缓存遵守 cache-aside 删缓存时机、MQ 消费端必须幂等、并发靠 DB 唯一索引兜底、Service 不放可变状态、上线必配探针和告警。
- "能上线"和"能在线上活得好"是两件事——可观测（traceId + 指标 + 告警）是后者的前提，没有它出了问题你只能盲猜。
- 排障是一套可复用的方法论：**告警/症状 → 日志找 traceId → jstack 看卡点 → arthas trace 量化根因 → 止血 + 长期治理**，而不是凭感觉改代码。
- 前端的工程直觉大多能迁移过来：cache-aside ≈ SWR、幂等键 ≈ idempotencyKey、滚动发布 ≈ 渐进部署、jstack/arthas ≈ Performance 火焰图。

### 自测

1. 收藏接口已经在 Service 里用 `existsByUidAndArtworkId` 判断过"是否已收藏"，为什么 Entity 上还要再建一个 `unique` 复合索引？在什么场景下这个索引才会真正起作用？
2. cache-aside 模式里，写操作为什么是"先更新 DB 再**删除**缓存"，而不是"更新缓存"？把顺序换成"先删缓存再更新 DB"会有什么风险？
3. 收藏接口 P99 突然涨到 3 秒、但 QPS 和错误率都没变。请按本章的排障步骤，写出你会依次用哪些命令/工具、各自想确认什么。

### 下一章

你已经走完了主线课程。接下来可以翻 [术语表](/back-end/frontend-backend-guide/90-glossary) 巩固名词、用 [命令速查](/back-end/frontend-backend-guide/91-command-cheatsheet) 做日常手册，并对照 [学习路径](/back-end/frontend-backend-guide/93-learning-path) 规划下一阶段的进阶方向。

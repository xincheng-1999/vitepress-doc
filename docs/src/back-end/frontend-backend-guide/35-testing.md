# 后端测试

> 你写前端时大概率写过 `jest`：`describe / it / expect`，跑 `npm test` 一片绿。后端测试的思路完全一样——只是工具换成了 **JUnit 5 + Mockito + Spring Test**，而且后端因为"看不见界面、跑在服务器上"，对测试的依赖比前端更重：一个没人测过的 Service，上线后扣错配额、漏掉权限校验，损失是真金白银。
>
> 这一章带你把前端的 `jest` 经验整体平移过来，并补上后端特有的两块：**Mock 依赖**（不连真 DB/下游）和 **集成测试**（起真实环境跑）。运行示例仍然是那套 AI 生图微服务（`svc-user` / `svc-canvas` 等）。
>
> 💡 **前端类比**：`jest` ≈ JUnit（测试运行器 + 断言），`jest.mock()` ≈ Mockito（替身），`@testing-library` 发请求测组件 ≈ Spring 的 `MockMvc` 发请求测 Controller。心智模型不变，换个 import 而已。

---

## 35.1 测试金字塔：先搞清楚测什么

测试不是越多越好，而是按"数量多、速度快"往下铺。经典的**测试金字塔**：

```text
            ▲  少 / 慢 / 脆
           ╱ ╲
          ╱E2E╲          端到端：起整套服务 + 真实依赖，
         ╱─────╲         模拟用户从登录到出图的完整链路
        ╱       ╲
       ╱ 集成测试 ╲       Integration：起 Spring 上下文 / 真实 DB，
      ╱───────────╲      测"几层串起来"对不对（Controller→Service→DB）
     ╱             ╲
    ╱   单元测试     ╲     Unit：只测一个类/一个方法，
   ╱─────────────────╲    依赖全 mock，毫秒级，跑几千个都不卡
  ▼  多 / 快 / 稳
```

| 层级 | 测什么 | 速度 | 数量 | 前端类比 |
| --- | --- | --- | --- | --- |
| 单元测试 Unit | 单个 Service / 工具方法，依赖全 mock | 毫秒级 | 最多（70%+） | `jest` 测纯函数 / hook |
| 集成测试 Integration | 多层串联：Controller→Service→真实 DB | 秒级 | 中等 | `supertest` 打你的 Node 路由 |
| 端到端 E2E | 整套服务 + 真实环境，模拟真用户 | 分钟级 | 最少 | Playwright / Cypress |

**后端的重点是单元 + 集成**。E2E 又慢又脆（任何一个下游抖一下就红），通常交给专门的测试团队或定时跑，开发自己写得少。

> 💡 **前端类比**：和前端完全一致——你不会给每个按钮都写 Cypress，而是大量 `jest` 单测 + 少量关键路径 E2E。倒金字塔（E2E 一大堆、单测没几个）在前后端都是反模式：跑得慢、定位难、动不动就 flaky。

判断该写哪一层，记一句话：**逻辑分支多的用单测覆盖，"几层接起来对不对"用集成测试验证。** 比如 `svc-user` 的"扣配额"算法（够不够、扣多少、并发扣）适合单测；"POST /v1/draw 真的能把任务写进 MongoDB"适合集成测试。

---

## 35.2 JUnit 5：和 jest 并排看

JUnit 5 是 Java 的测试运行器 + 断言库，地位等同 `jest`。Spring Boot 项目里它已经随 `spring-boot-starter-test` 进来了，不用单独配。

先看一个最朴素的单测——测 `cpt-common` 里的一个工具方法 `QuotaCalculator.deduct(balance, cost)`（计算扣配额后的余额，余额不足抛异常）：

```java
package com.aigen.common.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuotaCalculatorTest {

    private QuotaCalculator calculator;

    @BeforeEach
    void setUp() {
        // 每个 @Test 方法执行前都会重新跑一次，保证用例之间互不影响
        calculator = new QuotaCalculator();
    }

    @Test
    @DisplayName("余额充足时，扣减后返回正确余额")
    void deduct_enoughBalance_returnsRemaining() {
        int remaining = calculator.deduct(100, 30);
        assertEquals(70, remaining); // 期望值在前，实际值在后
    }

    @Test
    @DisplayName("余额不足时，抛出 BizException")
    void deduct_notEnough_throws() {
        BizException ex = assertThrows(BizException.class,
                () -> calculator.deduct(10, 30));
        assertEquals(40010, ex.getCode()); // 顺便断言错误码
    }
}
```

把它和你熟悉的 `jest` 并排放：

```typescript
// jest 版本——对照着看，结构几乎一一对应
describe('QuotaCalculator', () => {
  let calculator: QuotaCalculator;

  beforeEach(() => {            // ← @BeforeEach
    calculator = new QuotaCalculator();
  });

  it('余额充足时返回正确余额', () => {       // ← @Test + @DisplayName
    expect(calculator.deduct(100, 30)).toBe(70); // ← assertEquals
  });

  it('余额不足时抛异常', () => {
    expect(() => calculator.deduct(10, 30))      // ← assertThrows
      .toThrow(BizException);
  });
});
```

注解 / API 对照表（背这一张就够开始写了）：

| JUnit 5 | jest | 作用 |
| --- | --- | --- |
| `@Test` | `it(...)` / `test(...)` | 标记一个测试方法 |
| `@DisplayName("...")` | `it` 的描述字符串 | 用例的人类可读名字 |
| `@BeforeEach` | `beforeEach` | 每个用例前都跑（重置状态） |
| `@AfterEach` | `afterEach` | 每个用例后都跑（清理） |
| `@BeforeAll` / `@AfterAll` | `beforeAll` / `afterAll` | 整个类只跑一次（注意要 `static`） |
| `assertEquals(exp, act)` | `expect(act).toBe(exp)` | 相等断言（注意参数顺序相反！） |
| `assertTrue` / `assertFalse` | `expect(x).toBe(true)` | 布尔断言 |
| `assertNull` / `assertNotNull` | `toBeNull` / `toBeDefined` | 空值断言 |
| `assertThrows(Ex.class, () -> ...)` | `expect(fn).toThrow()` | 断言抛异常 |
| `@Disabled` | `it.skip` | 临时跳过 |

> ⚠️ **最容易踩的坑**：`assertEquals` 是**期望值在前、实际值在后**（`assertEquals(70, remaining)`），和 `jest` 的 `expect(actual).toBe(expected)` 顺序正好相反。写反了不会报错，只是失败信息里的 "expected/actual" 会反过来，看着别扭。异常断言的细节可对照 [Java 异常机制](/back-end/java/05-exception)。

### @ParameterizedTest：一份逻辑，多组数据

前端你可能用 `it.each([...])` 跑数据驱动测试。JUnit 的对应物是 `@ParameterizedTest`：

```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@ParameterizedTest
@DisplayName("扣配额：多组(余额,花费,期望剩余)")
@CsvSource({
        "100, 30, 70",
        "50,  50, 0",
        "999, 1,  998"
})
void deduct_variousInputs(int balance, int cost, int expected) {
    assertEquals(expected, new QuotaCalculator().deduct(balance, cost));
}
```

```typescript
// jest 等价写法
it.each([
  [100, 30, 70],
  [50, 50, 0],
  [999, 1, 998],
])('deduct(%i, %i) => %i', (balance, cost, expected) => {
  expect(new QuotaCalculator().deduct(balance, cost)).toBe(expected);
});
```

每组数据是一个独立用例，某一组挂了你能精确看到是哪组——比写一堆 `assertEquals` 强。

跑测试的命令（对应你的 `npm test`）：

```bash
# 跑全部测试
mvn test

# 只跑某一个测试类
mvn test -Dtest=QuotaCalculatorTest

# 只跑某个类里的某个方法
mvn test -Dtest=QuotaCalculatorTest#deduct_notEnough_throws
```

预期输出（绿了的样子）：

```text
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**怎么读**：`Failures` 是断言没通过（`assertEquals` 不相等），`Errors` 是用例里抛了没预期的异常（比如 NPE）。两者都为 0 才算过。失败时它会打印是哪个方法、期望 vs 实际，定位方式和 `jest` 输出一样。

---

## 35.3 Mockito：给被测单元找替身

### 为什么要 mock

`NoteService.create()` 里调了 `noteRepository.save()`。如果单测真的去连 MongoDB：

- **慢**：每次都要起数据库、清数据。
- **不稳**：DB 没起来、网络抖动，测试就红，但这根本不是你代码的错。
- **测不全**：想测"save 抛异常时 Service 怎么处理"，你很难让真 DB 按需抛错。

所以单测要把依赖换成**替身（mock）**：我让 `save()` 返回什么它就返回什么，让它抛错它就抛错。这样测试只聚焦"被测这一个类的逻辑对不对"，把 Repository、Feign 客户端、下游服务统统隔离掉。

> 💡 **前端类比**：就是你写 `jest.mock('axios')` 然后 `mockedAxios.get.mockResolvedValue({ data: ... })` —— 不真发 HTTP，只测你的组件/函数拿到这个返回后干了什么。Mockito 就是 Java 版的 `jest.mock`。

### 核心 API：when / thenReturn / verify

```java
// 1. 造一个替身
NoteRepository repo = Mockito.mock(NoteRepository.class);

// 2. 打桩（stub）：定义"被调用时返回什么"
Mockito.when(repo.findById("abc")).thenReturn(Optional.of(someNote));

// 3. 验证（verify）：断言"某方法被调用了几次、用什么参数"
Mockito.verify(repo, times(1)).save(any(Note.class));
```

| Mockito | jest | 作用 |
| --- | --- | --- |
| `mock(Xxx.class)` | `jest.mock('xxx')` | 造替身 |
| `when(x.foo()).thenReturn(v)` | `mockFn.mockReturnValue(v)` | 打桩返回值 |
| `when(x.foo()).thenThrow(ex)` | `mockFn.mockRejectedValue(e)` | 打桩抛异常 |
| `verify(x).foo()` | `expect(mockFn).toHaveBeenCalled()` | 验证被调用 |
| `verify(x, times(2)).foo()` | `toHaveBeenCalledTimes(2)` | 验证调用次数 |
| `verify(x, never()).foo()` | `not.toHaveBeenCalled()` | 验证从未调用 |
| `any()` / `eq(v)` | `expect.anything()` / 具体值 | 参数匹配器 |

### @Mock / @InjectMocks：少写样板

每个测试都手动 `mock()` 太啰嗦。配合 `@ExtendWith(MockitoExtension.class)`，用注解自动织入：

- `@Mock`：给这个字段造一个替身。
- `@InjectMocks`：创建被测对象，并把上面的 `@Mock` 自动注进它的构造器/字段。

```java
package com.aigen.user.service;

import com.aigen.common.exception.BizException;
import com.aigen.user.dto.NoteCreateDTO;
import com.aigen.user.entity.Note;
import com.aigen.user.repository.NoteRepository;
import com.aigen.user.vo.NoteVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // 让 @Mock / @InjectMocks 生效
class NoteServiceTest {

    @Mock
    private NoteRepository noteRepository;      // 替身：不连真 MongoDB

    @InjectMocks
    private NoteService noteService;            // 被测对象，repo 会被注进来

    @Test
    void create_shouldSetUidAndTimestamps_thenSave() {
        // given：准备入参，并打桩 save 返回"落库后带 id 的对象"
        NoteCreateDTO dto = new NoteCreateDTO();
        dto.setTitle("我的笔记");
        dto.setContent("从前端转后端");

        Note saved = new Note();
        saved.setId("6650f1c2a3b4c5d6e7f80912");
        saved.setUid(10086L);
        saved.setTitle("我的笔记");
        saved.setContent("从前端转后端");
        saved.setCreatedAt(Instant.now());
        saved.setUpdatedAt(Instant.now());
        when(noteRepository.save(any(Note.class))).thenReturn(saved);

        // when：调被测方法
        NoteVO vo = noteService.create(10086L, dto);

        // then：断言返回值
        assertEquals("6650f1c2a3b4c5d6e7f80912", vo.getId());
        assertEquals(10086L, vo.getUid());

        // 验证：真的调了一次 save，且传进去的对象 uid/createdAt 被正确设置了
        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository, times(1)).save(captor.capture());
        Note passedIn = captor.getValue();
        assertEquals(10086L, passedIn.getUid());
        assertNotNull(passedIn.getCreatedAt()); // Service 该补上创建时间
    }

    @Test
    void getById_notFound_shouldThrowBizException() {
        // 打桩：让 findById 返回空，模拟"查不到"
        when(noteRepository.findById("not-exist")).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class,
                () -> noteService.getById("not-exist"));
        assertEquals(40401, ex.getCode());

        // 查不到时不应该再去写库
        verify(noteRepository, never()).save(any());
    }
}
```

这就是后端单测最典型的样子：**给 Service mock 掉 Repository，验证 Service 的逻辑**。两个用例分别覆盖了"正常路径"和"异常路径"——后者用 `Optional.empty()` 模拟查不到，验证 Service 抛了正确错误码、且没有误写库。被测的 `NoteService` 就是 [写一个 CRUD 接口](/back-end/frontend-backend-guide/07-build-a-crud-api) 里那个。

> 💡 **前端类比**：`@Mock NoteRepository` ≈ `const repo = { save: jest.fn(), findById: jest.fn() }`；`@InjectMocks NoteService` ≈ `new NoteService(repo)`（手动把 mock 当依赖传进去）。`ArgumentCaptor` 则像 `expect(repo.save).toHaveBeenCalledWith(expect.objectContaining({ uid: 10086 }))`——抓住"传给 mock 的实参"再做断言。

**given / when / then 三段式**是后端测试的标准写法（前端叫 Arrange-Act-Assert），强烈建议用注释分隔，可读性高一大截。

---

## 35.4 Spring 集成测试：起上下文、打接口

单测把依赖都 mock 了，但有些 bug 只有"真把几层接起来"才暴露：注解配错了、路由没注册、参数绑定失败、JSON 序列化把字段名写错了。这要靠**集成测试**——让 Spring 把 Bean 真正装配起来（容器与 Bean 的概念见 [Spring Boot 与 IoC/DI](/back-end/frontend-backend-guide/06-spring-boot-ioc-di)）。

### @WebMvcTest + MockMvc：只测 Web 层

`@WebMvcTest` 只加载 Controller 这一层（不碰数据库、不起整个应用），配合 `MockMvc` 模拟发 HTTP 请求、断言响应。Service 用 `@MockBean` 换成替身。

> `@MockBean` 和 `@Mock` 的区别：`@MockBean` 是"往 Spring 容器里塞一个替身 Bean"，会替换掉容器里真实的那个；`@Mock` 只是个普通替身，不进容器。在 Spring 集成测试里要用 `@MockBean`。

```java
package com.aigen.user.controller;

import com.aigen.user.service.NoteService;
import com.aigen.user.vo.NoteVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NoteController.class) // 只装载这一个 Controller
class NoteControllerTest {

    @Autowired
    private MockMvc mockMvc;       // 模拟 HTTP 客户端，不真起 Tomcat

    @Autowired
    private ObjectMapper objectMapper; // 拼/解 JSON

    @MockBean
    private NoteService noteService;   // Service 用替身，本测试只关心 Web 层

    @Test
    void getById_shouldReturnRtDataOk() throws Exception {
        NoteVO vo = new NoteVO();
        vo.setId("6650f1c2a3b4c5d6e7f80912");
        vo.setTitle("我的笔记");
        when(noteService.getById(eq("6650f1c2a3b4c5d6e7f80912"))).thenReturn(vo);

        mockMvc.perform(get("/v1/note/6650f1c2a3b4c5d6e7f80912")
                        .header("uid", "10086"))
                .andExpect(status().isOk())                       // HTTP 200
                .andExpect(jsonPath("$.code").value(0))           // RtData.code == 0
                .andExpect(jsonPath("$.data.id").value("6650f1c2a3b4c5d6e7f80912"))
                .andExpect(jsonPath("$.data.title").value("我的笔记"));
    }

    @Test
    void create_emptyTitle_shouldReturn400() throws Exception {
        // 故意提交空标题，验证 @Validated 真的在拦
        String body = "{\"title\":\"\",\"content\":\"内容\"}";

        mockMvc.perform(post("/v1/note")
                        .header("uid", "10086")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())               // HTTP 400
                .andExpect(jsonPath("$.code").value(40000));
        // 注意：被校验挡在门外，Service 根本没被调到
    }
}
```

`jsonPath("$.code")` 是用 JsonPath 语法（类似前端 `res.data.code`）从响应 JSON 里取值再断言。`$` 是根，`$.data.title` 就是 `data.title`。

> 💡 **前端类比**：`MockMvc` 就是后端版的 `supertest`——`mockMvc.perform(get(...))` ≈ `request(app).get(...)`，`andExpect(status().isOk())` ≈ `.expect(200)`，`jsonPath("$.code").value(0)` ≈ `.expect(res => expect(res.body.code).toBe(0))`。区别是它连 Tomcat 都不真起，所以飞快。

### @SpringBootTest：起整个上下文

需要测"多层真实串联"（比如 Controller→Service→真实 Repository，但 DB 用真实容器）时，用 `@SpringBootTest` 起完整应用上下文。它最接近真实运行环境，但也最慢——按需用，别全项目都套。

```java
@SpringBootTest                                    // 起整个 Spring 上下文
@AutoConfigureMockMvc                              // 顺带配好 MockMvc
class NoteFlowIntegrationTest {

    @Autowired MockMvc mockMvc;

    // 这里通常配合 Testcontainers 起真实 MongoDB（见下一节），
    // 这样从 HTTP 请求一路到数据库落库全是真的
}
```

`@WebMvcTest` vs `@SpringBootTest` 选哪个：

| | 加载范围 | 速度 | 用途 |
| --- | --- | --- | --- |
| `@WebMvcTest` | 只 Controller + MVC 相关 Bean | 快 | 测路由/参数绑定/序列化/校验 |
| `@SpringBootTest` | 整个应用上下文 | 慢 | 测多层真实串联、端到端流程 |

---

## 35.5 Testcontainers：用 Docker 起真实依赖

Mock 能隔离依赖，但有时你就是想验证"这条 MongoDB 查询语句到底对不对""Redis 的分布式锁脚本真能锁住吗"。Mock 替身永远只会返回你打桩的值，**测不出真实数据库的行为**（索引、唯一约束、查询语义）。

**Testcontainers** 解决这个问题：测试启动时用 Docker **临时拉起一个真实的 MongoDB / Redis 容器**，测完自动销毁。比 mock 真，比手动装环境省心。

> 💡 **前端类比**：有点像你用 `docker-compose` 起一个临时 Postgres 跑 e2e，只不过 Testcontainers 把"起容器/连上去/测完销毁"都收进测试代码里，跑 `mvn test` 时自动完成。前提是跑测试的机器上有 Docker（本地 Docker Desktop / CI 的 Docker daemon）。Docker 基础见 [Docker 实战](/back-end/frontend-backend-guide/23-docker-in-practice)。

Maven 依赖（`pom.xml`，test 作用域）：

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mongodb</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

最小示例——起真实 MongoDB，测 `NoteRepository.findByUid` 这条派生查询是否真能按 uid 查出来：

```java
package com.aigen.user.repository;

import com.aigen.user.entity.Note;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
@DataMongoTest // 只加载 MongoDB 相关 Bean（Repository 层），比 @SpringBootTest 轻
class NoteRepositoryIntegrationTest {

    // 测试开始时自动 docker run 一个 mongo:6 容器，结束自动销毁
    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:6.0");

    // 把容器随机分配的连接串注入 Spring 配置（端口是动态的，不能写死）
    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
    }

    @Autowired
    private NoteRepository noteRepository;

    @Test
    void findByUid_shouldReturnOnlyThatUsersNotes() {
        // given：塞两个用户的数据
        noteRepository.save(makeNote(10086L, "A"));
        noteRepository.save(makeNote(10086L, "B"));
        noteRepository.save(makeNote(99999L, "别人的"));

        // when：查 uid=10086
        Page<Note> page = noteRepository.findByUid(10086L, PageRequest.of(0, 10));

        // then：只应该查到 2 条（真实 DB 才能验证查询条件对不对）
        assertEquals(2, page.getTotalElements());
    }

    private Note makeNote(Long uid, String title) {
        Note n = new Note();
        n.setUid(uid);
        n.setTitle(title);
        n.setContent("x");
        n.setCreatedAt(Instant.now());
        n.setUpdatedAt(Instant.now());
        return n;
    }
}
```

第一次跑会先拉镜像，输出类似：

```text
[INFO] ... Creating container for image: mongo:6.0
[INFO] ... Container mongo:6.0 started in PT3.214S
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**怎么读**：看到 `Container mongo:6.0 started` 就说明 Testcontainers 真的拉起了 MongoDB；测试跑完容器自动停掉，你 `docker ps` 已经看不到它了。`findByUid` 真的只查出 2 条而不是 3 条，证明派生查询的过滤条件正确——这是 mock 永远测不到的真实行为。同理把 `MongoDBContainer` 换成 `GenericContainer("redis:7")` 就能测 `svc-user` 的 Redis 限流 / 分布式锁逻辑（Redis 用法见 [Redis 实战](/back-end/frontend-backend-guide/12-redis-in-practice)）。

> ⚠️ Testcontainers 慢且依赖 Docker，所以**只给"真实依赖行为"写它**（DB 查询、唯一约束、Redis 脚本），别拿它替代单测。CI 里跑这类测试要确保 runner 装了 Docker。

---

## 35.6 一个完整的"扣配额"测试场景

把前面几节串起来，看一个贴近业务的例子：`svc-user` 的 `submitDrawTask` —— 提交生图任务前先扣配额，配额不足直接拒绝。逻辑分支多，是单测的最佳目标：

```java
@ExtendWith(MockitoExtension.class)
class DrawQuotaServiceTest {

    @Mock UserQuotaRepository quotaRepository;
    @Mock CanvasFeignClient canvasClient;        // 下游 svc-canvas，mock 掉
    @InjectMocks DrawQuotaService drawQuotaService;

    @Test
    void submit_quotaEnough_shouldDeductAndCallCanvas() {
        when(quotaRepository.getBalance(10086L)).thenReturn(50);
        when(canvasClient.createTask(any())).thenReturn("task-001");

        String taskId = drawQuotaService.submitDrawTask(10086L, "一只猫");

        assertEquals("task-001", taskId);
        verify(quotaRepository).deduct(10086L, 10);   // 扣了 10
        verify(canvasClient, times(1)).createTask(any());
    }

    @Test
    void submit_quotaNotEnough_shouldRejectAndNotCallCanvas() {
        when(quotaRepository.getBalance(10086L)).thenReturn(5); // 不够

        BizException ex = assertThrows(BizException.class,
                () -> drawQuotaService.submitDrawTask(10086L, "一只猫"));
        assertEquals(40010, ex.getCode());

        // 关键：配额不足时，绝不能扣费、也绝不能调下游建任务
        verify(quotaRepository, never()).deduct(anyLong(), anyInt());
        verify(canvasClient, never()).createTask(any());
    }
}
```

第二个用例特别能体现 `verify(..., never())` 的价值：它断言的是"**没发生**什么"。配额不足时如果代码 bug 导致仍然调了 `canvasClient.createTask`，就会白白占用 GPU 资源还可能重复计费——这种 bug 靠肉眼 review 容易漏，靠测试一抓一个准。这就是下一节要讲的"测行为"。

---

## 35.7 测试心法：少而精

工具会了，更重要的是知道"测什么、怎么测才不白写"。

**1. 测行为，不测实现。** 断言"输入 X 得到输出 Y / 产生副作用 Z"，而不是"内部调了哪个私有方法、用了什么循环"。前者在你重构内部实现时依然成立，后者一重构就全红——测试反而成了改代码的阻力。

> 💡 **前端类比**：和 `@testing-library` 的核心理念一字不差——"测用户看到/能做的，别测组件内部 state"。后端就是"测接口返回 + 副作用，别测私有方法"。

**2. 优先覆盖核心业务和边界。** 不必追求 100% 覆盖率（覆盖 getter/setter 毫无意义）。把测试预算花在：钱相关（扣配额、支付）、权限校验、边界值（0、负数、空、超长、并发）、异常分支。`submitDrawTask` 这种就值得多写几个用例。

**3. 可重复、无副作用、不依赖顺序。** 每个用例都该能单独跑、反复跑、结果一致。别让用例 A 依赖用例 B 先跑过（用 `@BeforeEach` 重置状态），别依赖当前时间/随机数（需要时把 `Clock`、随机源也注入进来好 mock），别真发邮件/真扣款。

> 💡 **前端类比**：就是 `jest` 里"每个 `it` 独立、`beforeEach` 重置、别依赖真实网络"那套规矩。flaky test（时绿时红）在前后端都是头号公敌——它会慢慢腐蚀整个团队对测试的信任。

**4. 测试名要会说话。** `deduct_notEnough_throws` 比 `test1` 强一万倍。推荐 `方法_场景_期望` 三段式命名，失败时光看名字就知道哪个场景挂了。

**5. 别为了覆盖率写假测试。** 没有断言的测试（只调方法不 assert）等于没测，但覆盖率工具会把它算成"覆盖了"——这是自欺欺人。一个有意义的断言胜过十行没断言的调用。

---

## 小结

- 测试金字塔：**单元（多/快）→ 集成（中）→ E2E（少/慢）**，后端重点是单元 + 集成；倒金字塔是反模式。
- **JUnit 5** ≈ jest：`@Test/@BeforeEach/assertEquals/assertThrows` 一一对应 `it/beforeEach/expect/toThrow`，注意 `assertEquals` 期望值在前。`@ParameterizedTest` 就是 `it.each`。
- **Mockito** ≈ jest.mock：用 `@Mock` 造替身、`@InjectMocks` 注入被测对象，`when().thenReturn()` 打桩、`verify()` 验证调用。单测 mock 掉 Repository/Feign，只测被测类自己的逻辑。
- **Spring 集成测试**：`@WebMvcTest` + `MockMvc` 测 Web 层（≈ supertest），`@SpringBootTest` 起整个上下文测真实串联，`@MockBean` 往容器里塞替身。
- **Testcontainers** 用 Docker 临时起真实 MongoDB/Redis，验证 mock 测不出的真实数据库行为；慢且依赖 Docker，只给关键集成场景用。
- 心法：**测行为不测实现、覆盖核心与边界、可重复无副作用、名字会说话、拒绝没断言的假测试**。

### 自测

1. `svc-user` 的 `submitDrawTask`（先查余额→够则扣配额并调 `svc-canvas`→不够则抛异常），你会用单元测试还是集成测试？要 mock 哪几个依赖？至少写出两个该覆盖的用例场景。
2. `@Mock` 和 `@MockBean` 有什么区别？什么时候必须用 `@MockBean`？
3. 你想验证 `NoteRepository.findByUid` 这条 MongoDB 派生查询真的只返回指定用户的数据，应该用 Mockito mock 掉 Repository，还是用 Testcontainers 起真实 MongoDB？为什么 mock 在这里行不通？

### 下一章

测试绿了只是第一步——下一章 [CI/CD 与部署](/back-end/frontend-backend-guide/36-cicd-deployment) 会讲怎么把"跑测试 → 构建镜像 → 部署上线"接成一条自动化流水线，让每次 push 都自动验证并发布。

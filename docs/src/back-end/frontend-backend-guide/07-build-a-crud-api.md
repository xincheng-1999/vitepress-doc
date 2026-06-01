# 写一个 CRUD 接口

> 上一章你已经理解了 [Spring Boot 与 IoC/DI](/back-end/frontend-backend-guide/06-spring-boot-ioc-di) —— 容器怎么管理对象、`@Autowired` 怎么把依赖注进来。
> 这一章不讲理论，直接动手：从零写一个 `note`（笔记）资源的完整 CRUD 接口，并用 `curl` 跑通每一个端点。
>
> 💡 **前端类比**：这一章就是后端版的"写一个 Next.js Route Handler + Prisma"。你会发现整体心智模型几乎一样——只是分层更明确、类型更严格。

---

## 7.0 目标：5 个端点

我们要在 `svc-user` 里加一个最简单的笔记功能（先不接配额、不接 MQ，纯 CRUD）。最终暴露这 5 个 HTTP 端点：

| 方法 | 路径 | 作用 | 前端类比 |
| --- | --- | --- | --- |
| `POST` | `/v1/note` | 新建一条笔记 | `fetch(url, { method: 'POST', body })` |
| `GET` | `/v1/note/{id}` | 按 ID 查单条 | `fetch('/note/123')` |
| `GET` | `/v1/note?page=0&size=10` | 分页查列表 | `fetch('/note?page=0')` |
| `PUT` | `/v1/note/{id}` | 整体更新一条 | `fetch(url, { method: 'PUT', body })` |
| `DELETE` | `/v1/note/{id}` | 删除一条 | `fetch(url, { method: 'DELETE' })` |

所有响应都用项目统一的 `RtData` 包装，结构固定为 `{ code, data, message }`：

```json
{ "code": 0, "data": { "...": "..." }, "message": "ok" }
```

> 💡 **前端类比**：`RtData` 就是后端版的"统一接口返回格式"。前端封装 `axios` 时通常约定 `res.data.code === 0` 才算成功——后端这边就是用 `RtData` 来产出这个约定。

---

## 7.1 数据怎么流动（先看这张图）

写代码之前，先记住这条数据流。每一步只做一件事，出参入参类型都不一样，这是后端最核心的"分层"思想：

```text
┌──────────┐  HTTP JSON   ┌──────────────┐
│  客户端   │ ───────────▶ │  Controller   │  接收 JSON → 绑定到 DTO
│  (前端)   │              │  /v1/note     │  做参数校验（@Validated）
└──────────┘              └──────┬───────┘
     ▲                           │ NoteCreateDTO
     │                           ▼
     │                    ┌──────────────┐
     │                    │   Service     │  写业务逻辑：
     │                    │  NoteService  │  DTO → Entity、设默认值、校验唯一性
     │                    └──────┬───────┘
     │                           │ Note(Entity)
     │                           ▼
     │                    ┌──────────────┐
     │                    │  Repository   │  只和数据库对话
     │                    │ NoteRepository│  save / findById / deleteById
     │                    └──────┬───────┘
     │                           │
     │                           ▼
     │                    ┌──────────────┐
     │                    │   MongoDB     │  collection: note
     │                    └──────────────┘
     │
     └──── 原路返回：Entity → VO/DTO → RtData → HTTP JSON ────┘
```

一句话记住：**HTTP JSON → DTO → Service → Entity → DB，再原路返回**。

> 💡 **前端类比**：和你在 Next.js 里做的事一模一样——route handler 收到 `request.json()`，用 `zod` 校验成一个干净对象，调一个 service 函数处理，再 `prisma.note.create()` 落库。后端只是把这三件事拆成了三个类（Controller / Service / Repository），各司其职。

为什么要拆这么细？因为后端代码生命周期长、改动多。Controller 只管"翻译 HTTP"，Service 只管"业务规则"，Repository 只管"存取"。哪天要把 MongoDB 换成 MySQL，只动 Repository 那一层，上面两层不用改。这正是 [上一章](/back-end/frontend-backend-guide/06-spring-boot-ioc-di)讲的"面向接口、依赖注入"带来的好处。

---

## 7.2 第一步：定义 Entity（@Document）

Entity 是"数据库里一行/一条文档"在 Java 里的样子。本项目主库是 MongoDB，所以用 `@Document` 把一个类映射成一个 collection。

```java
package com.aigen.user.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * 笔记实体，对应 MongoDB 中的 note 集合。
 * 一个 Note 对象 = note 集合里的一条文档。
 */
@Data
@Document(collection = "note")
public class Note {

    /** 主键，MongoDB 自动生成的 ObjectId 字符串，如 "6650f1c2a3b4c5d6e7f80912" */
    @Id
    private String id;

    /** 创建者用户 ID，建索引方便按用户查询 */
    @Indexed
    private Long uid;

    /** 标题 */
    private String title;

    /** 正文内容 */
    private String content;

    /** 创建时间（用 epoch 毫秒或 Instant，前端拿到的是 ISO 字符串） */
    private Instant createdAt;

    /** 最近更新时间 */
    private Instant updatedAt;
}
```

几个新面孔：

- `@Document(collection = "note")`：告诉 Spring Data 这个类映射到 `note` 集合。不写 `collection` 默认用类名小写。
- `@Id`：标记主键字段。MongoDB 里主键叫 `_id`，类型是 `ObjectId`，映射到 Java 用 `String` 最省心。
- `@Indexed`：给 `uid` 建索引，相当于 SQL 的 `CREATE INDEX`。后面"按用户查笔记"会快很多（索引细节见 [SQL 与索引](/back-end/frontend-backend-guide/10-sql-and-indexes)）。
- `@Data`：Lombok 注解，编译期自动生成 getter/setter/`toString`，不用手写一堆样板代码。

> 💡 **前端类比**：Entity 约等于 Prisma schema 里的 `model Note { ... }`，或者你用 TypeScript 写的 `interface Note`。区别是 Entity 上的注解直接决定了它怎么落库、建什么索引。

---

## 7.3 第二步：Repository（extends MongoRepository）

Repository 是"数据库访问层"。你只要**声明一个接口**继承 `MongoRepository`，Spring Data 会在运行时自动帮你生成实现——你一行 SQL/查询都不用写。

```java
package com.aigen.user.repository;

import com.aigen.user.entity.Note;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 笔记数据访问层。
 * 继承 MongoRepository<实体类型, 主键类型> 即可获得一整套 CRUD 方法。
 */
public interface NoteRepository extends MongoRepository<Note, String> {

    /**
     * 派生查询（Derived Query）：方法名按规则命名，Spring Data 自动翻译成查询。
     * findByUid → 等价于 db.note.find({ uid: ? })
     * 加上 Pageable 参数即可分页。
     */
    Page<Note> findByUid(Long uid, Pageable pageable);
}
```

继承 `MongoRepository<Note, String>` 后，你**白拿**这些方法（无需实现）：

| 方法 | 作用 | 对应 MongoDB 操作 |
| --- | --- | --- |
| `save(note)` | 新增或更新（有 id 就更新） | `insert` / `replaceOne` |
| `findById(id)` | 按主键查，返回 `Optional<Note>` | `findOne({_id})` |
| `findAll(pageable)` | 分页查全部 | `find().skip().limit()` |
| `existsById(id)` | 是否存在 | `count({_id})` |
| `deleteById(id)` | 按主键删 | `deleteOne({_id})` |

`findByUid` 是"派生查询"：Spring Data 解析方法名 `findBy + Uid`，自动生成查询条件。

> 💡 **前端类比**：`MongoRepository` 约等于一个已经写好的、类型安全的 `prisma.note` 客户端——`save` 是 `upsert`，`findById` 是 `findUnique`，`findByUid` 是 `findMany({ where: { uid } })`。只不过这里"客户端"是 Spring 根据接口和方法名自动生成的，你只声明不实现。更多用法见 [Spring Data 数据库实战](/back-end/java/08-spring-data-db)。

---

## 7.4 第三步：DTO + 校验（@Validated）

不要让前端的 JSON 直接打到 Entity 上。中间隔一层 **DTO**（Data Transfer Object），专门接请求参数，并在这一层做校验。

```java
package com.aigen.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建/更新笔记的请求体。前端 POST/PUT 的 JSON 会被绑定到这个对象。
 */
@Data
public class NoteCreateDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题最长 100 字")
    private String title;

    @NotBlank(message = "正文不能为空")
    @Size(max = 5000, message = "正文最长 5000 字")
    private String content;
}
```

再定义一个返回给前端的 **VO**（View Object），避免把 Entity 的内部字段（比如某些敏感字段）直接暴露：

```java
package com.aigen.user.vo;

import lombok.Data;

@Data
public class NoteVO {
    private String id;
    private Long uid;
    private String title;
    private String content;
    private String createdAt; // ISO 字符串，前端友好
    private String updatedAt;
}
```

`@NotBlank` / `@Size` 这些是 Jakarta Bean Validation 注解，但只有在 Controller 入参上加 `@Validated`（下一步）才会真正触发校验。

> 💡 **前端类比**：DTO + 校验注解 = 你最熟的 `zod`。`@NotBlank` ≈ `z.string().min(1)`，`@Size(max=100)` ≈ `z.string().max(100)`。区别是 zod 是运行时调用 `schema.parse(body)`，而这里是声明式的——注解写在字段上，框架在绑定参数时自动校验，校验失败自动抛异常。

---

## 7.5 第四步：Service（写业务）

Service 是业务逻辑中心：做 DTO ↔ Entity 转换、设默认值、调 Repository。

```java
package com.aigen.user.service;

import com.aigen.user.dto.NoteCreateDTO;
import com.aigen.user.entity.Note;
import com.aigen.user.repository.NoteRepository;
import com.aigen.user.vo.NoteVO;
import com.aigen.common.exception.BizException;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class NoteService {

    private final NoteRepository noteRepository;

    // 构造器注入（推荐写法，见上一章 IoC/DI）
    public NoteService(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    /** 创建笔记：DTO → Entity → 落库 → Entity → VO */
    public NoteVO create(Long uid, NoteCreateDTO dto) {
        Note note = new Note();
        note.setUid(uid);
        note.setTitle(dto.getTitle());
        note.setContent(dto.getContent());
        Instant now = Instant.now();
        note.setCreatedAt(now);
        note.setUpdatedAt(now);
        Note saved = noteRepository.save(note); // 保存后 saved.id 已被填充
        return toVO(saved);
    }

    /** 按 ID 查单条，查不到抛业务异常（由全局异常处理器转成统一错误响应） */
    public NoteVO getById(String id) {
        Note note = noteRepository.findById(id)
                .orElseThrow(() -> new BizException(40401, "笔记不存在"));
        return toVO(note);
    }

    /** 分页查某用户的笔记 */
    public Page<NoteVO> page(Long uid, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return noteRepository.findByUid(uid, pageable).map(this::toVO);
    }

    /** 整体更新：先确认存在，再覆盖可变字段 */
    public NoteVO update(String id, NoteCreateDTO dto) {
        Note note = noteRepository.findById(id)
                .orElseThrow(() -> new BizException(40401, "笔记不存在"));
        note.setTitle(dto.getTitle());
        note.setContent(dto.getContent());
        note.setUpdatedAt(Instant.now());
        return toVO(noteRepository.save(note));
    }

    /** 删除：不存在也当成功（幂等），更符合 DELETE 语义 */
    public void delete(String id) {
        noteRepository.deleteById(id);
    }

    /** Entity → VO 转换，把 Instant 转成 ISO 字符串 */
    private NoteVO toVO(Note note) {
        NoteVO vo = new NoteVO();
        BeanUtils.copyProperties(note, vo); // 同名字段自动拷贝
        vo.setCreatedAt(note.getCreatedAt().toString());
        vo.setUpdatedAt(note.getUpdatedAt().toString());
        return vo;
    }
}
```

几个要点：

- **构造器注入**：`NoteService` 通过构造器拿到 `NoteRepository`，由 Spring 容器注入（前端类比：组件 props 传依赖，而不是组件内部 `new`）。
- **`orElseThrow`**：`findById` 返回 `Optional<Note>`，查不到就抛 `BizException`。`BizException` 是 `cpt-common` 里的业务异常，会被全局异常处理器 `@RestControllerAdvice` 捕获，统一转成 `RtData.fail(...)`，所以 Service 里不用写 try/catch。
- **VO 转换**：`toVO` 把 Entity 转成对前端友好的 VO，`Instant` 转成 ISO 字符串。

> 💡 **前端类比**：Service 就是你抽出来的纯业务函数 / composable（`useNote`）。它不关心 HTTP 长什么样，只接收已经校验好的参数、返回处理结果。把"抛异常 → 全局拦截"理解成后端版的 `axios` 响应拦截器统一处理错误。

---

## 7.6 第五步：Controller（暴露 5 个端点）

Controller 只做三件事：接收 HTTP、调 Service、用 `RtData` 包装返回。注意入参上的 `@Validated`——它让 7.4 里写的校验注解真正生效。

```java
package com.aigen.user.controller;

import com.aigen.common.response.RtData;
import com.aigen.user.dto.NoteCreateDTO;
import com.aigen.user.service.NoteService;
import com.aigen.user.vo.NoteVO;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/note")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    /** 新建：POST /v1/note，uid 由网关从 token 解析后放进 header */
    @PostMapping
    public RtData<NoteVO> create(@RequestHeader("uid") Long uid,
                                 @Validated @RequestBody NoteCreateDTO dto) {
        return RtData.ok(noteService.create(uid, dto));
    }

    /** 查单条：GET /v1/note/{id} */
    @GetMapping("/{id}")
    public RtData<NoteVO> getById(@PathVariable String id) {
        return RtData.ok(noteService.getById(id));
    }

    /** 分页：GET /v1/note?page=0&size=10 */
    @GetMapping
    public RtData<Page<NoteVO>> page(@RequestHeader("uid") Long uid,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "10") int size) {
        return RtData.ok(noteService.page(uid, page, size));
    }

    /** 更新：PUT /v1/note/{id} */
    @PutMapping("/{id}")
    public RtData<NoteVO> update(@PathVariable String id,
                                 @Validated @RequestBody NoteCreateDTO dto) {
        return RtData.ok(noteService.update(id, dto));
    }

    /** 删除：DELETE /v1/note/{id} */
    @DeleteMapping("/{id}")
    public RtData<Void> delete(@PathVariable String id) {
        noteService.delete(id);
        return RtData.ok(null);
    }
}
```

注解速查（完整版见 [关键注解速查](/back-end/frontend-backend-guide/08-annotations-cheatsheet)）：

| 注解 | 作用 | 前端类比 |
| --- | --- | --- |
| `@RestController` | 标记为返回 JSON 的控制器 | Next.js route handler 文件 |
| `@RequestMapping("/v1/note")` | 给整个类设统一路径前缀 | 路由 basePath |
| `@PostMapping` / `@GetMapping` ... | 绑定 HTTP 方法 | `export async function POST()` |
| `@RequestBody` | 把请求体 JSON 绑定到对象 | `await request.json()` |
| `@PathVariable` | 取路径变量 `{id}` | `params.id` |
| `@RequestParam` | 取查询参数 `?page=` | `searchParams.get('page')` |
| `@RequestHeader("uid")` | 取请求头 | `request.headers.get('uid')` |
| `@Validated` | 触发 DTO 校验 | `schema.parse(body)` |

> 💡 **前端类比**：整个 `NoteController` 就是一个 Next.js 的 `app/v1/note/route.ts` + `[id]/route.ts`。`@PostMapping` ≈ `export async function POST(req)`，`@PathVariable` ≈ `{ params }`，`RtData.ok(...)` ≈ `NextResponse.json({ code: 0, data, message: 'ok' })`。

至此五层全部写完：**Entity → Repository → DTO/VO → Service → Controller**。其余完整示例可对照 [Spring Boot CRUD](/back-end/java/07-spring-boot-crud)。

---

## 7.7 跑起来并用 curl 逐个测试

启动服务（开发环境直接跑 `svc-user` 的主类，或用 Maven）：

```bash
# 在 svc-user 目录下启动（默认端口 8082，按项目实际配置为准）
mvn spring-boot:run
```

下面假设服务监听 `localhost:8082`，并且本地直连服务（绕过网关，所以需要手动带 `uid` 头）。

### ① 新建笔记 POST /v1/note

**目标**：创建一条笔记，拿到自动生成的 `id`。

```bash
curl -i -X POST http://localhost:8082/v1/note \
  -H "Content-Type: application/json" \
  -H "uid: 10086" \
  -d '{"title":"我的第一条笔记","content":"从前端转后端的第 7 天"}'
```

**预期输出**（HTTP 200，响应体）：

```json
{
  "code": 0,
  "data": {
    "id": "6650f1c2a3b4c5d6e7f80912",
    "uid": 10086,
    "title": "我的第一条笔记",
    "content": "从前端转后端的第 7 天",
    "createdAt": "2026-06-01T08:30:12.345Z",
    "updatedAt": "2026-06-01T08:30:12.345Z"
  },
  "message": "ok"
}
```

**怎么读**：`code: 0` 表示成功（项目约定 0 为成功）。`data.id` 是 MongoDB 自动生成的 ObjectId 字符串——后面查/改/删都要用它。把它存到环境变量里方便后续命令：

```bash
NOTE_ID=6650f1c2a3b4c5d6e7f80912
```

### ② 查单条 `GET /v1/note/{id}`

```bash
curl -s http://localhost:8082/v1/note/$NOTE_ID -H "uid: 10086"
```

**预期输出**：

```json
{
  "code": 0,
  "data": {
    "id": "6650f1c2a3b4c5d6e7f80912",
    "uid": 10086,
    "title": "我的第一条笔记",
    "content": "从前端转后端的第 7 天",
    "createdAt": "2026-06-01T08:30:12.345Z",
    "updatedAt": "2026-06-01T08:30:12.345Z"
  },
  "message": "ok"
}
```

**查一个不存在的 id**（验证异常分支）：

```bash
curl -s http://localhost:8082/v1/note/000000000000000000000000 -H "uid: 10086"
```

**预期输出**：

```json
{ "code": 40401, "data": null, "message": "笔记不存在" }
```

**怎么读**：`code` 不是 0，`message` 是我们在 Service 里 `throw new BizException(40401, "笔记不存在")` 写的文案。说明全局异常处理器把抛出的业务异常统一转成了 `RtData.fail`——这正是 Service 不用写 try/catch 的原因。

### ③ 分页 GET /v1/note

```bash
curl -s "http://localhost:8082/v1/note?page=0&size=10" -H "uid: 10086"
```

**预期输出**（Spring Data `Page` 的标准结构）：

```json
{
  "code": 0,
  "data": {
    "content": [
      {
        "id": "6650f1c2a3b4c5d6e7f80912",
        "uid": 10086,
        "title": "我的第一条笔记",
        "content": "从前端转后端的第 7 天",
        "createdAt": "2026-06-01T08:30:12.345Z",
        "updatedAt": "2026-06-01T08:30:12.345Z"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "number": 0,
    "size": 10
  },
  "message": "ok"
}
```

**怎么读**：`content` 是当前页的数据数组；`totalElements` 是总条数；`number` 是当前页码（从 0 开始）；`totalPages` 是总页数。前端做分页器渲染就靠 `totalElements / totalPages`。

### ④ 更新 `PUT /v1/note/{id}`

```bash
curl -s -X PUT http://localhost:8082/v1/note/$NOTE_ID \
  -H "Content-Type: application/json" \
  -H "uid: 10086" \
  -d '{"title":"改过的标题","content":"内容也改了"}'
```

**预期输出**：

```json
{
  "code": 0,
  "data": {
    "id": "6650f1c2a3b4c5d6e7f80912",
    "uid": 10086,
    "title": "改过的标题",
    "content": "内容也改了",
    "createdAt": "2026-06-01T08:30:12.345Z",
    "updatedAt": "2026-06-01T08:41:55.012Z"
  },
  "message": "ok"
}
```

**怎么读**：`title` / `content` 已更新，`createdAt` 不变而 `updatedAt` 变新了——说明 Service 里 `setUpdatedAt(Instant.now())` 生效了。

### ⑤ 删除 `DELETE /v1/note/{id}`

```bash
curl -s -X DELETE http://localhost:8082/v1/note/$NOTE_ID -H "uid: 10086"
```

**预期输出**：

```json
{ "code": 0, "data": null, "message": "ok" }
```

**验证确实删掉了**（再查一次）：

```bash
curl -s http://localhost:8082/v1/note/$NOTE_ID -H "uid: 10086"
```

```json
{ "code": 40401, "data": null, "message": "笔记不存在" }
```

**结论**：5 个端点全部跑通，数据流 `HTTP JSON → DTO → Service → Entity → MongoDB` 双向闭环。

---

## 7.8 故意触发一次校验失败（看 @Validated 怎么拦）

**目标**：确认 7.4 写的 `@NotBlank` 真的在拦请求。提交一个空标题：

```bash
curl -i -s -X POST http://localhost:8082/v1/note \
  -H "Content-Type: application/json" \
  -H "uid: 10086" \
  -d '{"title":"","content":"标题是空的"}'
```

**预期输出**（HTTP 400，被全局异常处理器接住后的统一格式）：

```text
HTTP/1.1 400 Bad Request
Content-Type: application/json
```

```json
{ "code": 40000, "data": null, "message": "标题不能为空" }
```

**怎么读**：请求体 `title` 为空 → `@Validated` 触发校验 → Spring 抛 `MethodArgumentNotValidException` → 全局异常处理器把 `@NotBlank(message = "标题不能为空")` 里的文案取出来，包成 `RtData`。注意：这个错误**在进 Service 之前**就被挡住了，业务代码根本没执行。

> 💡 **前端类比**：完全等价于 `zod` 的 `schema.parse(body)` 抛 `ZodError` 后，你在统一中间件里 `catch` 住返回 400。声明式校验帮你把"脏数据"挡在业务逻辑门外，Service 里拿到的一定是合法对象。

---

## 7.9 常见报错速查

实操中最容易碰到的几个坑：

| 现象 | 多半的原因 | 怎么排 |
| --- | --- | --- |
| `415 Unsupported Media Type` | curl 没带 `-H "Content-Type: application/json"` | 加上请求头 |
| `400` 且 message 是字段名 | 触发了 `@Validated` 校验 | 看 message 文案改请求体 |
| `Required request header 'uid' is not present` | 没带 `uid` 头（本地直连绕过了网关） | 加 `-H "uid: 10086"` |
| 启动报 `No qualifying bean of type NoteRepository` | Repository 接口没被扫描到（不在主类包路径下） | 确认包名在启动类同级或其子包 |
| 返回 `data` 里 `id` 为 null | 用 `new Note()` 后没经过 `save` 就返回了 | 一定用 `repository.save()` 的返回值 |

> 排查方法论会在 [排查方法论](/back-end/frontend-backend-guide/27-troubleshooting-methodology) 和 [看懂日志](/back-end/frontend-backend-guide/26-reading-logs) 里系统讲。这里先记住：**先看 HTTP 状态码定大方向（4xx 是你的请求问题，5xx 是服务端问题），再看 message。**

---

## 小结

- 一个 CRUD 接口固定五层：**Entity（@Document）→ Repository（extends MongoRepository）→ DTO/VO → Service → Controller**，每层只做一件事。
- 数据流是 **HTTP JSON → DTO → Service → Entity → DB**，再原路返回，最外层统一用 `RtData` 包成 `{ code, data, message }`。
- `MongoRepository` 让你"只声明接口不写实现"就白拿 `save/findById/deleteById` 等方法，派生查询 `findByUid` 靠方法名自动生成。
- `@Validated` + Bean Validation 注解（`@NotBlank`/`@Size`）= 后端版 `zod`，把脏数据挡在 Service 之前；校验失败由全局异常处理器统一转成错误响应。
- 整套心智模型和 Next.js route handler + Prisma 高度一致，差别只是分层更显式、类型更严格。

### 自测

1. 前端 POST 一段 JSON 进来，到落库前一共经过了哪几个 Java 对象（按顺序）？每一步类型分别是什么？
2. `NoteController.getById` 查不到数据时并没有写 `try/catch`，为什么前端最终还能收到 `{ "code": 40401, "message": "笔记不存在" }`？
3. 如果把 `@Validated` 从 Controller 入参上删掉，提交一个空 `title` 会发生什么？请求会在哪一层失败？

### 下一章

掌握了五个 HTTP 注解，下一章 [关键注解速查](/back-end/frontend-backend-guide/08-annotations-cheatsheet) 会把 Spring 里最常用的注解整理成一张速查表，方便你写代码时随时翻。

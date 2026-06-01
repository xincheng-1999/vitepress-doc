# 动手练习

> 只看不写是学不会的。后端这门手艺，尤其如此——你可以把二十几章读得滚瓜烂熟，但只要没亲手敲过 `docker logs`、没亲眼见过一份 heap dump、没在 `jstack` 输出里找到过 `deadlock` 字样，那些知识就还停留在"看过"的层面，遇到线上事故照样发懵。
>
> 这一章是整个课程的"实验台"。前 5 个练习偏**业务开发**，从写一个接口到读懂 MQ 流转，对应你日常会改的代码；后 5 个练习偏**运维与排查**，从起依赖、造慢接口、抓 OOM、找死锁到自己写 Dockerfile，对应你迟早会遇到的线上场景。每个练习都标了"前端类比"和"你会学到"，建议**按顺序、在真机上**跑一遍。

> [!TIP] 前端类比
> 这就像你学 React 时不会只看文档——你会 `create-react-app` 一个项目，故意把 `useEffect` 的依赖写错制造无限渲染，再用 React DevTools 抓出来。后端的"实验台"只是把 DevTools 换成了 `docker logs`、`jstack`、MAT 而已。

---

## 第一部分：业务开发练习

### 练习 1：新增一个简单的 GET 接口

#### 目标

在 `svc-user` 中添加一个 `GET /user/hello` 接口，返回 `{ "message": "Hello, Frontend! Your uid is 12345" }`，并用 `RtData` 包装。

#### 步骤

1. 打开 `svc-user` 里的 `UserController.java`。
2. 添加一个新方法：

```java
@GetMapping("/hello")
public RtData<Map<String, String>> hello(@RequestHeader("uid") Long uid) {
    Map<String, String> result = new HashMap<>();
    result.put("message", "Hello, Frontend! Your uid is " + uid);
    return RtData.ok(result);
}
```

3. 如果希望不登录也能访问，去 `svc-gateway` 的白名单配置里加上这个路径（否则会被 `AuthFilter` 拦下，返回 401）。
4. 重启 `svc-user`，用 curl 测试：

```bash
curl -H "uid: 12345" http://localhost:8080/v1/user/hello
```

预期输出：

```json
{"code":0,"msg":"ok","data":{"message":"Hello, Frontend! Your uid is 12345"}}
```

#### 前端类比

```typescript
// 这就像你在 Next.js 里写 app/api/user/hello/route.ts：
export async function GET(req: Request) {
  const uid = req.headers.get('uid')
  return Response.json({ message: `Hello! Your uid is ${uid}` })
}
```

`@GetMapping` 等于 `route.ts` 里导出的 `GET`，`@RequestHeader("uid")` 等于 `req.headers.get('uid')`，`RtData.ok(...)` 等于你团队约定的统一 `Response.json` 封装。

#### 你会学到

- Controller 的基本写法、`RtData` 统一响应格式、请求头提取。
- 需要补 Spring Boot 写法的话，配合看 [07 章 · 手写一个 CRUD 接口](/back-end/frontend-backend-guide/07-build-a-crud-api) 和 [Java · Spring Boot CRUD](/back-end/java/07-spring-boot-crud)。

---

### 练习 2：跟踪一个请求的完整链路

#### 目标

在代码中埋日志，亲眼看到一个登录请求经过网关 → Controller → Service 的执行顺序和耗时。

#### 步骤

1. 在 `svc-gateway` 的 `AuthFilter.java` 的 `filter` 方法开头加：

```java
log.info(">>> [Gateway] 收到请求: {} {}", request.getMethod(), path);
```

2. 在 `svc-auth` 的登录 Controller 的 `login` 方法开头加：

```java
log.info(">>> [AuthController] 收到登录请求");
```

3. 在对应 Service 的 `login` 方法中加：

```java
log.info(">>> [LoginService] 开始处理登录逻辑");
```

4. 启动相关服务，发送一个登录请求，在控制台看三条日志的顺序和时间戳。

#### 预期日志输出

```text
2026-06-01 10:00:00.001  INFO  [svc-gateway] >>> [Gateway] 收到请求: POST /v1/auth/login
2026-06-01 10:00:00.050  INFO  [svc-auth]    >>> [AuthController] 收到登录请求
2026-06-01 10:00:00.051  INFO  [svc-auth]    >>> [LoginService] 开始处理登录逻辑
```

#### 怎么读这段输出

- 三条日志的**顺序**印证了"请求先到网关、再到目标服务、最后进业务层"的链路。
- 第 1 条到第 2 条之间隔了约 49ms——这是网关转发 + 服务间网络的开销；第 2 条到第 3 条只隔 1ms——同进程内方法调用几乎没成本。这种"时间戳一减就知道时间花在哪"的直觉，是你以后排查慢接口的基本功。

#### 前端类比

等同于你在浏览器里给 axios 加一个请求拦截器打 `console.log`，再到 Next.js 的 API 路由里打一行，最后到 service 函数里打一行——只是这里跨了进程，要靠时间戳把它们串起来。

#### 你会学到

- 微服务请求链路的执行顺序、Java 日志框架的使用（`log.info` 的 `{}` 占位符）、数据在层间的传递方式。
- 想系统学怎么读日志，看 [26 章 · 读懂日志](/back-end/frontend-backend-guide/26-reading-logs)；想理解这条链路的全貌，看 [03 章 · 一个请求的完整链路](/back-end/frontend-backend-guide/03-request-lifecycle)。

---

### 练习 3：修改一个已有接口的返回值

#### 目标

给"查询用户配额"接口的返回结果增加一个 `welcomeMessage` 字段。

#### 步骤

1. 在 `svc-user` 中找到配额查询接口的入口（如 `QuotaController.java`）。
2. 看它调用了哪个 Service。
3. 找到返回的 VO 类（如 `QuotaVO`）。
4. 在 VO 中添加一个新字段：

```java
private String welcomeMessage = "欢迎使用 AI 生图！";
```

5. 重启服务，用 curl 验证响应里多了这个字段：

```bash
curl -H "uid: 12345" http://localhost:8080/v1/user/quota
```

```json
{"code":0,"msg":"ok","data":{"remaining":42,"total":100,"welcomeMessage":"欢迎使用 AI 生图！"}}
```

#### 前端类比

后端的 VO 就是你接口契约里那份 `interface QuotaResponse`。给 VO 加一个字段，等于在 `zod` schema 里加一行 `welcomeMessage: z.string()`——前端 `response.data.welcomeMessage` 立刻就能拿到。**这个练习的价值在于：你终于看清了"字段是怎么从后端源码一路传到你浏览器 `response.data` 里的"。**

#### 你会学到

- Controller → Service → VO 的数据流向、修改后端接口返回值的标准流程。
- 想理解后端为什么分这么多层，看 [04 章 · 三层架构与目录结构](/back-end/frontend-backend-guide/04-three-layer-and-structure)。

---

### 练习 4：理解 Feign 调用

#### 目标

搞清楚 `svc-canvas` 是怎么通过 Feign 调用 `svc-ai` 的，并验证降级（fallback）逻辑。

#### 步骤

1. 在 `cpt-api` 中打开 `AiFeignClient.java`，阅读接口定义（注意 `@FeignClient` 上的服务名和 `@PostMapping` 的路径）。
2. 全局搜索 `aiFeignClient`，找到 `svc-canvas` 里所有使用它的地方。
3. 在调用处加日志：

```java
log.info(">>> [svc-canvas] 即将调用 svc-ai, taskId={}, prompt={}", task.getId(), request.getPrompt());
```

4. 打开对应的 `AiFeignFallback.java`，理解降级返回了什么。
5. **故意不启动 `svc-ai`**，再触发一次生图调用，观察 fallback 是否生效、日志里有没有打出降级信息。

#### 尝试画出这个调用链

```text
svc-canvas Controller
  └─> svc-canvas Service
        └─> AiFeignClient.generateImage(request)
              └─[HTTP] POST http://svc-ai/internal/generate
                    └─> svc-ai Controller
                          └─> svc-ai Service
                                └─> 调用外部 AI 模型 API
```

`svc-ai` 不可用时，调用会落到 `AiFeignFallback`，返回一个预设的失败 `RtData`，而不是把整个 `svc-canvas` 拖崩。

#### 前端类比

`AiFeignClient` 就是你在前端封装的那个 `aiApi.generateImage(req)` axios 方法——你不在乎底层走了几跳 HTTP，调起来像本地函数。`Fallback` 则相当于你给 `Promise` 加的 `.catch()` 兜底，AI 挂了就给个降级提示，而不是让整个页面白屏。

#### 你会学到

- 服务间通信的声明式写法、Fallback 降级保护机制、网络失败时的兜底策略。
- 想看微服务整体是怎么拆的，看 [02 章 · 架构总览](/back-end/frontend-backend-guide/02-architecture-overview)。

---

### 练习 5：阅读 MQ 消息流转

#### 目标

画出"提交生图任务"的完整异步流程图，能回答"谁发、发到哪、谁消费、消费失败怎么办"。

#### 步骤

1. 在 `cpt-rocketmq` 和各服务中全局搜索消息发送的工具类/方法，找到所有发送点。
2. 打开 `svc-ai` 的消费者，阅读它怎么消费"生图任务"消息。
3. 打开 `svc-canvas` 的结果消费者，阅读它怎么处理"生图完成"消息、怎么更新任务状态。
4. 用纸笔或 draw.io 画出完整流程：

```text
┌────────────┐   发送任务   ┌──────────┐   消费任务   ┌──────────┐
│ svc-canvas │ ──────────▶ │ RocketMQ │ ──────────▶ │  svc-ai  │
│  (生产者)  │             │ Topic A  │             │ (消费者) │
└────────────┘             └──────────┘             └──────────┘
      ▲                                                   │
      │  消费结果   ┌──────────┐   发送结果                │
      └─────────── │ RocketMQ │ ◀─────────────────────────┘
                   │ Topic B  │
                   └──────────┘
```

#### 回答这些问题

- 谁发的消息？发到哪个 Topic？用了什么 Tag？
- 消息的 payload 是什么（taskId？prompt？）？
- 谁消费了消息？消费后做了什么（更新任务状态？写 OSS？）？
- 如果消费失败会怎样？有没有重试？重试多少次后进死信队列（DLQ）？

#### 前端类比

这和你用 `Promise` + 轮询做长任务很像：前端提交任务拿到 `taskId`（相当于把消息发进 Topic A），然后定时轮询任务状态（相当于 `svc-canvas` 消费 Topic B 的结果再更新状态）。MQ 把"提交"和"处理"彻底解耦，就像 `await` 把"发起"和"拿到结果"在时间上拉开。

#### 你会学到

- 消息队列在真实业务中的用法、Topic/Tag/Consumer Group 的概念、异步任务的完整生命周期。
- 想深入消息可靠性（幂等、重试、死信），看 [33 章 · MQ 可靠性](/back-end/frontend-backend-guide/33-mq-reliability)。

---

## 第二部分：运维与排查练习

> 这一部分需要你本机装好 Docker Desktop 和一个 JDK 17。下面的命令在 macOS/Linux 终端和 Windows 的 PowerShell/WSL 里基本通用，个别差异会标注。**别只读，真去敲。**

### 练习 6：用 docker-compose 起一套依赖并验证连通

#### 目标

不依赖公司的测试环境，在本机一条命令拉起 MongoDB + Redis + RocketMQ，并逐个验证它们真的连得上。

#### 步骤

1. 新建一个目录 `local-infra/`，在里面放一个 `docker-compose.yml`：

```yaml
version: "3.8"
services:
  mongodb:
    image: mongo:6.0
    container_name: local-mongo
    ports:
      - "27017:27017"
    environment:
      MONGO_INITDB_ROOT_USERNAME: root
      MONGO_INITDB_ROOT_PASSWORD: example

  redis:
    image: redis:7.2
    container_name: local-redis
    ports:
      - "6379:6379"

  rmqnamesrv:
    image: apache/rocketmq:5.1.4
    container_name: local-rmq-namesrv
    ports:
      - "9876:9876"
    command: sh mqnamesrv

  rmqbroker:
    image: apache/rocketmq:5.1.4
    container_name: local-rmq-broker
    depends_on:
      - rmqnamesrv
    ports:
      - "10911:10911"
    environment:
      NAMESRV_ADDR: rmqnamesrv:9876
    command: sh mqbroker -c /home/rocketmq/rocketmq-5.1.4/conf/broker.conf
```

2. 拉起来并看状态：

```bash
docker compose -f local-infra/docker-compose.yml up -d
docker compose -f local-infra/docker-compose.yml ps
```

预期输出（4 个容器都是 `running`）：

```text
NAME                IMAGE                   STATUS         PORTS
local-mongo         mongo:6.0               Up 12 seconds  0.0.0.0:27017->27017/tcp
local-redis         redis:7.2               Up 12 seconds  0.0.0.0:6379->6379/tcp
local-rmq-namesrv   apache/rocketmq:5.1.4   Up 12 seconds  0.0.0.0:9876->9876/tcp
local-rmq-broker    apache/rocketmq:5.1.4   Up 11 seconds  0.0.0.0:10911->10911/tcp
```

3. 逐个验证连通：

```bash
# Redis：应回 PONG
docker exec -it local-redis redis-cli ping

# MongoDB：应回 { ok: 1 }
docker exec -it local-mongo mongosh -u root -p example --eval "db.runCommand({ ping: 1 })"

# RocketMQ namesrv：端口通就行
docker exec -it local-rmq-broker sh -c "echo > /dev/tcp/rmqnamesrv/9876 && echo NAMESRV OK"
```

预期输出：

```text
PONG
{ ok: 1 }
NAMESRV OK
```

#### 怎么读这段输出

- `redis-cli ping` 回 `PONG`、Mongo 回 `{ ok: 1 }`，说明服务进程起来了且能响应命令——这比"容器是 running"更可靠，因为容器在跑不代表里面的服务已就绪。
- RocketMQ 那行用 bash 的 `/dev/tcp` 探了一下 9876 端口能不能连上，能连上就说明 broker 找得到 namesrv。

#### 前端类比

`docker-compose.yml` 就是后端版的"一键起本地环境"，作用等同于你前端项目里 `npm run dev` 同时拉起 mock server + vite + tailwind watch。`depends_on` 类似 `npm-run-all` 里声明的启动顺序。

#### 结论与你会学到

- 你不必等运维给你环境，自己就能在本机复刻一套依赖，改 Bug、跑联调都快得多。
- 跑通后想系统理解 Docker，看 [23 章 · Docker 实战](/back-end/frontend-backend-guide/23-docker-in-practice)。

---

### 练习 7：故意造一个慢接口，并定位慢在哪

#### 目标

亲手做一个慢接口，再用"日志打点 + 逐段计时"的方法精确定位是哪一步慢。

#### 步骤

1. 在 `svc-user` 加一个测试接口，先用 `Thread.sleep` 模拟一个慢的下游调用：

```java
@GetMapping("/slow")
public RtData<String> slow() {
    long t0 = System.currentTimeMillis();
    log.info(">>> [slow] step1 查 DB 开始");
    // 模拟一次很慢的数据库/远程调用
    sleepQuietly(1200);
    log.info(">>> [slow] step1 查 DB 完成, 耗时 {}ms", System.currentTimeMillis() - t0);

    long t1 = System.currentTimeMillis();
    log.info(">>> [slow] step2 组装结果开始");
    sleepQuietly(80);
    log.info(">>> [slow] step2 组装结果完成, 耗时 {}ms", System.currentTimeMillis() - t1);

    return RtData.ok("done in " + (System.currentTimeMillis() - t0) + "ms");
}

private void sleepQuietly(long ms) {
    try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
}
```

2. 请求它，并用 curl 测端到端耗时：

```bash
curl -s -o /dev/null -w "total: %{time_total}s\n" http://localhost:8080/v1/user/slow
```

预期输出：

```text
total: 1.293s
```

3. 看服务日志：

```text
10:11:02.001 INFO >>> [slow] step1 查 DB 开始
10:11:03.205 INFO >>> [slow] step1 查 DB 完成, 耗时 1204ms
10:11:03.205 INFO >>> [slow] step2 组装结果开始
10:11:03.287 INFO >>> [slow] step2 组装结果完成, 耗时 82ms
```

#### 怎么读这段输出

- 端到端 1.29s，日志里 step1 占了 1204ms、step2 只占 82ms——**慢在 step1（模拟的 DB 查询），不在结果组装**。这种"分段打点 + 各段耗时"是定位慢接口最朴素也最有效的手段。
- 进阶版：把 `Thread.sleep` 换成真实的 N+1 查询（循环里对每条数据各发一次 SQL），你会在日志里看到几十上百条 SQL 顺序打印——这就是经典的 N+1 慢查询，正确做法是改成一次 `IN` 批量查或 JOIN。

#### 前端类比

这和你用 `console.time('fetch') / console.timeEnd('fetch')` 在前端测某段逻辑耗时一模一样；分段打点就像 Chrome Performance 面板里那一条条 timing 条，告诉你时间到底花在哪一段。

#### 结论与你会学到

- 排查慢接口的第一步永远是"分段计时缩小范围"，而不是上来就猜。
- 系统方法看 [26 章 · 读懂日志](/back-end/frontend-backend-guide/26-reading-logs) 和 [28 章 · 排查实战手册](/back-end/frontend-backend-guide/28-troubleshooting-playbook)；N+1 与索引优化看 [10 章 · SQL 与索引](/back-end/frontend-backend-guide/10-sql-and-indexes)。

---

### 练习 8：制造堆 OOM，抓 dump 并用 MAT 找占用大户

#### 目标

写一段无限往集合里加对象的代码把堆撑爆，配置 JVM 自动抓 heap dump，再用 MAT（Eclipse Memory Analyzer）找出谁占了内存。

#### 步骤

1. 写一段必崩的代码：

```java
public class OomDemo {
    static final List<byte[]> LEAK = new ArrayList<>();

    public static void main(String[] args) {
        int i = 0;
        while (true) {
            // 每次塞 1MB，且用静态 List 持有引用 => GC 回收不掉
            LEAK.add(new byte[1024 * 1024]);
            System.out.println("allocated " + (++i) + " MB");
        }
    }
}
```

2. 用很小的堆 + 自动 dump 启动它：

```bash
java -Xmx64m \
     -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=./oom.hprof \
     -cp out OomDemo
```

预期输出（涨到一定量后崩溃，并自动落盘 dump）：

```text
allocated 1 MB
allocated 2 MB
...
allocated 58 MB
java.lang.OutOfMemoryError: Java heap space
Dumping heap to ./oom.hprof ...
Heap dump file created [63 MB]
Exception in thread "main" java.lang.OutOfMemoryError: Java heap space
	at OomDemo.main(OomDemo.java:8)
```

3. 用 MAT 打开 `oom.hprof`，跑 **Leak Suspects** 报告。你会看到类似：

```text
Problem Suspect 1
One instance of "java.util.ArrayList" loaded by "<system class loader>"
occupies 60,123,456 (95.21%) bytes.
The instance is referenced by OomDemo.LEAK ...
Keywords: byte[], java.util.ArrayList
```

#### 怎么读这段输出

- `OutOfMemoryError: Java heap space` 明确是**堆**溢出（不是 Metaspace、不是栈），原因是对象一直被强引用、GC 回收不掉。
- MAT 的 Leak Suspects 直接点名 `OomDemo.LEAK` 这个静态 `ArrayList` 占了 95% 的堆——**静态集合只进不出**是最常见的内存泄漏来源（缓存忘了设上限、监听器忘了注销都属此类）。在真实项目里，你会在这里看到的是某个 `static Map<Long, Task>` 缓存而不是 demo 的 `byte[]`。

#### 前端类比

等价于你在前端把对象不停 push 进一个全局数组又从不清理，最后页面卡死、Chrome 标签崩溃。`HeapDumpOnOutOfMemoryError` 就是后端版的"崩溃时自动存一份 Memory snapshot"，MAT 则相当于 Chrome DevTools 的 Memory 面板,帮你按 Retained Size 排序找出"内存大户"。

#### 结论与你会学到

- OOM 不靠猜——配好 `-XX:+HeapDumpOnOutOfMemoryError` 让它崩的时候自动留证据，再用 MAT 顺着引用链找到根因。
- 原理与更多泄漏模式看 [20 章 · OOM 与内存泄漏](/back-end/frontend-backend-guide/20-oom-memory-leak)；把它纳入排查流程看 [28 章 · 排查实战手册](/back-end/frontend-backend-guide/28-troubleshooting-playbook)。

---

### 练习 9：制造死锁，用 jstack 找到 deadlock 段

#### 目标

写两个方法以**相反顺序**获取两把锁，造一个必然死锁，再用 `jstack` 把它揪出来。

#### 步骤

1. 写死锁代码：

```java
public class DeadlockDemo {
    static final Object LOCK_A = new Object();
    static final Object LOCK_B = new Object();

    public static void main(String[] args) {
        new Thread(DeadlockDemo::method1, "thread-1").start();
        new Thread(DeadlockDemo::method2, "thread-2").start();
    }

    static void method1() {
        synchronized (LOCK_A) {
            sleepQuietly(200);            // 给对方足够时间拿到 LOCK_B
            synchronized (LOCK_B) {       // 等 thread-2 释放 LOCK_B —— 永远等不到
                System.out.println("method1 done");
            }
        }
    }

    static void method2() {
        synchronized (LOCK_B) {           // 顺序相反！
            sleepQuietly(200);
            synchronized (LOCK_A) {       // 等 thread-1 释放 LOCK_A —— 永远等不到
                System.out.println("method2 done");
            }
        }
    }

    static void sleepQuietly(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
```

2. 跑起来，它会卡住不退出。另开一个终端，先拿到进程号，再 jstack：

```bash
jps -l                # 找到 DeadlockDemo 的 PID, 假设是 48213
jstack 48213          # 打印线程栈
```

预期输出（节选，重点看末尾的 deadlock 段）：

```text
"thread-2" #13 prio=5 ... waiting for monitor entry
   java.lang.Thread.State: BLOCKED (on object monitor)
	at DeadlockDemo.method2(DeadlockDemo.java:24)
	- waiting to lock <0x000000071ab10000> (a java.lang.Object)
	- locked   <0x000000071ab10010> (a java.lang.Object)

"thread-1" #12 prio=5 ... waiting for monitor entry
   java.lang.Thread.State: BLOCKED (on object monitor)
	at DeadlockDemo.method1(DeadlockDemo.java:15)
	- waiting to lock <0x000000071ab10010> (a java.lang.Object)
	- locked   <0x000000071ab10000> (a java.lang.Object)

Found one Java-level deadlock:
=============================
"thread-1":
  waiting to lock monitor 0x... (object 0x000000071ab10010),
  which is held by "thread-2"
"thread-2":
  waiting to lock monitor 0x... (object 0x000000071ab10000),
  which is held by "thread-1"
```

#### 怎么读这段输出

- jstack 末尾的 `Found one Java-level deadlock` 是 JVM 帮你直接判定的——它会列出"谁等谁手里的锁"。本例里 thread-1 持有 A 等 B、thread-2 持有 B 等 A，互相死等，形成环。
- 顺着每个线程栈的 `at DeadlockDemo.methodX(...:行号)` 就能精确定位到代码里加锁的那两行——这就是修复点：**让所有地方都按同一顺序获取这两把锁**（比如统一先 A 后 B），环就破了。

#### 前端类比

JS 是单线程，你平时碰不到真死锁，但概念类似两个互相 `await` 对方完成的 `Promise`——谁也走不到 `resolve`，整个流程永久挂起。`jstack` 就相当于一份"当前所有任务都卡在哪一行"的快照。

#### 结论与你会学到

- 死锁的根因几乎都是"加锁顺序不一致"，jstack 能秒级定位。
- 锁与线程安全的原理看 [16 章 · 线程安全与锁](/back-end/frontend-backend-guide/16-thread-safety-locks)；把 jstack 用进排查流程看 [28 章 · 排查实战手册](/back-end/frontend-backend-guide/28-troubleshooting-playbook) 和 [29 章 · 诊断工具箱](/back-end/frontend-backend-guide/29-diagnostic-toolbox)。

---

### 练习 10：给一个 svc-* 写 Dockerfile 并跑起来

#### 目标

给任意一个 `svc-*`（以 `svc-user` 为例）写一个 Dockerfile，`docker build` 打成镜像，`docker run` 跑起来，再用 `docker logs` 看启动日志。

#### 步骤

1. 先把服务打成 jar（在该服务目录下）：

```bash
mvn clean package -DskipTests
# 产物在 target/svc-user-1.0.0.jar
```

2. 在该服务目录写 `Dockerfile`：

```text
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/svc-user-1.0.0.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

3. 构建镜像：

```bash
docker build -t svc-user:local .
```

预期输出（节选）：

```text
[+] Building 6.8s (9/9) FINISHED
 => [internal] load build definition from Dockerfile
 => [1/3] FROM docker.io/library/eclipse-temurin:17-jre
 => [2/3] WORKDIR /app
 => [3/3] COPY target/svc-user-1.0.0.jar app.jar
 => exporting to image
 => => naming to docker.io/library/svc-user:local
```

4. 跑起来（连上练习 6 起的依赖，用 host 网络或显式传环境变量）：

```bash
docker run -d --name svc-user \
  -p 8081:8081 \
  -e SPRING_DATA_MONGODB_URI="mongodb://root:example@host.docker.internal:27017" \
  -e SPRING_REDIS_HOST="host.docker.internal" \
  svc-user:local
```

5. 看启动日志：

```bash
docker logs -f svc-user
```

预期输出（节选）：

```text
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 :: Spring Boot ::                (v3.2.0)

2026-06-01 10:20:01.123  INFO --- [main] c.x.svc.user.UserApplication : Starting UserApplication
2026-06-01 10:20:03.880  INFO --- [main] o.s.b.w.embedded.tomcat.TomcatWebServer : Tomcat started on port 8081 (http)
2026-06-01 10:20:03.902  INFO --- [main] c.x.svc.user.UserApplication : Started UserApplication in 3.1 seconds
```

#### 怎么读这段输出

- `Tomcat started on port 8081` + `Started UserApplication in 3.1 seconds` 两行一起出现，才算启动成功。如果日志卡在某行不动或抛异常退出，多半是连不上 Mongo/Redis——这时回头检查练习 6 的依赖是否在跑、环境变量里的 host 是否写对（容器内访问宿主机要用 `host.docker.internal`，不是 `localhost`）。
- `docker logs -f` 的 `-f` 是 follow，等同于 `tail -f`，会持续刷新日志，方便你边发请求边看。

#### 前端类比

Dockerfile 就是你前端项目里那份"如何从源码构建出可运行产物"的说明书，类似多阶段构建 + `nginx` 托管 `dist` 的那套；`docker run` 等于把构建好的产物部署起来；`docker logs` 等于你看 PM2 或部署平台上的运行日志。

#### 结论与你会学到

- 你已经能把一个后端服务从源码 → 镜像 → 运行容器 → 看日志 全链路走通，这是上 K8s 之前的必备基本功。
- 想深入镜像分层、多阶段构建、瘦身，看 [23 章 · Docker 实战](/back-end/frontend-backend-guide/23-docker-in-practice)。

---

## 小结

- 前 5 题把"改后端代码"祛魅：写接口、埋日志看链路、改返回值、读懂 Feign 与 MQ，本质都是顺着 Controller → Service → 数据/远程 这条线走。
- 后 5 题把"运维排查"祛魅：起依赖、定位慢接口、抓 OOM dump、找死锁、写 Dockerfile——这些都不是玄学，每一步都有可复制的命令和可读的输出。
- 排查的通用套路始终是：**先缩小范围（分段计时/看日志），再留证据（dump/jstack），最后顺着证据找根因**，而不是上来就猜。
- 容器内访问宿主机用 `host.docker.internal`、静态集合只进不出会泄漏、加锁顺序不一致会死锁——这三条是本章踩过的最高频的坑。
- 把这十个练习在真实或本地环境**亲手跑一遍**，比把这门课读十遍都管用。看懂和会做之间隔着的，正是这一次次敲命令、读输出。

### 自测

1. 练习 7 的慢接口里，端到端耗时 1.29s、step1 日志显示耗时 1204ms，你下一步应该去优化哪段、用什么手段进一步确认是不是 SQL 慢？
2. 练习 8 里 MAT 的 Leak Suspects 报告点名了一个静态 `ArrayList`，为什么"静态集合"特别容易造成内存泄漏？换成有上限的缓存能解决吗？
3. 练习 9 的两个方法各自加锁的顺序是怎样的？要彻底消除这个死锁，最简单的改法是什么？

### 下一章

学完零散练习，去 [38 章 · 综合实战：从一个功能到上线](/back-end/frontend-backend-guide/38-capstone-feature-to-prod) 把它们串成一条完整的"开发到上线"主线。

# 看日志

> 在前端，出了问题你打开 DevTools 看 Console、看 Network——这是你的第一反应。
> 在后端，没有可视化界面、没有红色报错框弹出来，**日志就是你的 Console**。一个接口报错了、变慢了、行为不对，后端工程师的第一动作不是改代码、不是重启，而是**去看日志**。
>
> 这一章的目标：让"看日志"成为你排查后端问题的肌肉记忆。学完你应该能做到——线上 svc-canvas 报了个 500，你拿到一个 traceId，几条 `grep` 命令就能把这次请求穿过的几个服务的日志串起来，定位到是哪个类、哪一行抛的异常。

上一章 [配置与环境管理](/back-end/frontend-backend-guide/25-config-and-env) 讲了配置怎么随环境走；这一章讲当线上真出问题时，你的第一手信息来源——日志——怎么打、打到哪、怎么看。它是后面 [排查方法论](/back-end/frontend-backend-guide/27-troubleshooting-methodology)、[排查实战手册](/back-end/frontend-backend-guide/28-troubleshooting-playbook) 的地基。

---

## 日志级别：比 console.log 更体系化

前端你大概只用三个：`console.log` / `console.warn` / `console.error`，而且 `console.log` 满天飞。后端把"打印"这件事做成了一套**分级**体系，从最啰嗦到最严重一共五级：

| 级别 | 什么时候用 | 前端类比 |
| --- | --- | --- |
| `TRACE` | 最细的跟踪，比 debug 还啰嗦（循环每一轮、每个分支）。极少用 | `console.log` 调到吐 |
| `DEBUG` | 排查问题时想看的中间变量、分支走向、入参出参 | 你临时加的 `console.log('here', value)` |
| `INFO` | 正常业务里程碑：收到请求、扣配额成功、任务提交、定时任务跑完 | 有意义的 `console.log('订单已创建')` |
| `WARN` | 不正常但还能继续：重试了一次、降级了、配置缺省走了默认值 | `console.warn` |
| `ERROR` | 真出错了，业务没完成：异常、调用失败、数据不一致 | `console.error` |

关键规则，记牢：

- **级别是有"门槛"的**。配置说"只打 INFO 及以上"，那 TRACE、DEBUG 全部被丢弃。这就是为什么后端能在代码里写满 `log.debug(...)` 却不担心拖慢生产——平时它们根本不执行。前端没有这个机制，`console.log` 写了就一定执行、一定输出。
- **生产环境通常只开到 INFO**。日志太多既占磁盘又难看，也拖慢服务。
- **临时排查时把某个包的级别调到 DEBUG**，复现问题、看完中间过程，再调回去。这是后端排查的常规操作，下面 logback 配置会演示怎么只给一个包开 DEBUG。
- **永远不要用 `System.out.println`**。它绕过整个日志体系：没有时间、没有级别、没有线程名、没有 traceId，不能被采集、不能按级别过滤、不能滚动归档。`System.out.println` 之于后端，约等于你把 `alert()` 当调试工具——能用，但没人会原谅你。

> 前端类比：把日志级别想成 `if (process.env.LOG_LEVEL === 'debug')` 包住的那堆调试输出，只不过框架替你把这个开关做成了运行时可调、可按模块细分的标准能力。

---

## 怎么打日志：@Slf4j + 占位符

我们项目用 Lombok 的 `@Slf4j` 注解，它会在类里自动生成一个名为 `log` 的 logger 对象，省得你手写 `LoggerFactory.getLogger(...)`。

```java
@Slf4j
@Service
public class QuotaServiceImpl implements QuotaService {

    @Override
    public RtData<Void> deduct(String uid, String orderId, int n) {
        // INFO：正常业务里程碑
        log.info("用户 {} 订单 {} 申请扣配额 {}", uid, orderId, n);

        int remain = quotaMapper.getRemain(uid);
        if (remain < n) {
            // WARN：不正常但能优雅处理（配额不足，返回失败，不是崩溃）
            log.warn("用户 {} 配额不足, 剩余 {} 需要 {}", uid, remain, n);
            return RtData.fail("配额不足");
        }

        try {
            quotaMapper.deduct(uid, n);
            log.info("用户 {} 订单 {} 扣配额成功, 剩余 {}", uid, orderId, remain - n);
            return RtData.ok();
        } catch (Exception e) {
            // ERROR：真出错了，第二个参数传异常对象 e，框架会自动打印完整堆栈
            log.error("用户 {} 订单 {} 扣配额失败", uid, orderId, e);
            return RtData.fail("扣配额失败");
        }
    }
}
```

这段代码里藏着两条**必须养成的习惯**：

**习惯一：用占位符 `{}`，不要字符串拼接。**

```java
// 对：参数填进 {}，只有当这条日志真的要输出时才会去拼字符串
log.info("用户 {} 订单 {} 扣配额 {}", uid, orderId, n);

// 错：无论日志级别开没开，"+" 拼接都会立刻执行
log.debug("查询结果 = " + bigObject.toString());
```

为什么？回到上面"级别有门槛"那条。生产只开 INFO 时，`log.debug(...)` 整条不输出——但如果你用 `+` 拼接，那个 `bigObject.toString()` **照样会被执行一次**（参数要先算出来才能传进方法），白白浪费 CPU。用 `{}` 占位符，SLF4J 会先判断级别，确定要输出了才去拼，省掉这次开销。

> 前端类比：像 React 里 `{shouldRender && <ExpensiveComponent />}` 的短路求值——条件不满足就根本不构造右边那个昂贵的东西。占位符就是日志版的短路。

**习惯二：打异常一定把异常对象 `e` 作为最后一个参数传进去。**

```java
// 对：异常对象作为最后一个参数，自动打印完整堆栈（stack trace）
log.error("用户 {} 扣配额失败", uid, e);

// 错：只打了 e.getMessage()，丢了堆栈，根本不知道是哪一行抛的
log.error("扣配额失败: " + e.getMessage());
```

堆栈（stack trace）就是 JS 里 `error.stack` 那一长串——告诉你异常从哪个方法、哪一行冒上来的。**丢了堆栈 = 自废武功**，你将永远不知道错误的真正出处。注意 `log.error("...{}...", a, e)` 这种写法：占位符填掉前面的参数，**最后一个 `e` 不对应任何 `{}`，SLF4J 会把它识别成异常并打印堆栈**，这是约定俗成的用法。异常体系本身见 [Java 异常处理](/back-end/java/05-exception)。

### MDC：给同一个请求的所有日志盖同一个章

光有上面的日志还有个大问题：生产环境几十个请求并发，日志是**交错**打出来的。你看到一条"扣配额失败"，根本不知道它属于哪一次请求、哪个用户。

解决办法叫 **MDC**（Mapped Diagnostic Context）。可以把它理解成一个**绑定到当前线程的小 Map**，你往里塞个 `traceId`，那么这个线程之后打的每一条日志都会**自动带上**这个 traceId——你不用在每行 `log.info` 里手写。

```java
// 通常放在网关/拦截器里，请求一进来就设置（呼应第 03 章 Header 透传）
@Component
public class TraceIdInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
        // 优先用上游透传过来的 traceId，没有就自己生成一个
        String traceId = req.getHeader("traceId");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        MDC.put("traceId", traceId);   // 塞进当前线程的 MDC
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse resp,
                                Object handler, Exception ex) {
        MDC.clear();   // 请求结束清掉，否则线程被复用时会串味（见下方提醒）
    }
}
```

> 前端类比：MDC 很像 React 的 Context——你在外层 `Provider` 设一次值，内层任意深的组件 `useContext` 都能拿到，不用一层层透传 props。MDC 就是"日志上下文"，设一次，本次请求所有日志自动带上。

> 重要提醒：MDC 基于 ThreadLocal，**绑在线程上**。因为后端是"一个请求一个线程"（见 [一个请求的完整链路](/back-end/frontend-backend-guide/03-request-lifecycle)），所以同一请求的所有日志能共享同一个 traceId；但也正因如此，请求结束必须 `MDC.clear()`，否则线程归还线程池、下次被别的请求复用时，会带上上一个请求残留的 traceId，造成"串味"。同理，如果你把活丢到 [线程池](/back-end/frontend-backend-guide/15-thread-pools-executor) 里异步执行，新线程里 MDC 是空的，需要手动把 traceId 传过去。

设好之后，下面在 logback 配置里把 `%X{traceId}` 加进输出格式，每条日志就自动带上它了。

---

## 日志配置：logback-spring.xml

Spring Boot 默认用 logback 作为日志实现。你不用关心它怎么和 SLF4J 对接（SLF4J 是接口，logback 是实现，类似 TS 里的 interface 和 class），只要会写配置文件 `src/main/resources/logback-spring.xml`。下面是一份能直接用、覆盖项目实际需求的关键片段：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- 日志输出格式：时间 | 级别 | 进程 | 线程 | traceId | logger | 消息 -->
    <property name="LOG_PATTERN"
        value="%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level ${PID:-} --- [%thread] [%X{traceId:-}] %logger{36} : %msg%n"/>

    <!-- 1. 控制台输出：容器环境靠它，打到 stdout 被 docker/k8s 收集（见下一节） -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
        </encoder>
    </appender>

    <!-- 2. 文件输出 + 滚动归档：传统服务器/需要本地留存时用 -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>/var/log/svc-canvas/app.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <!-- 按天分文件，单文件超过 100MB 也切；最多留 15 天，总量上限 5GB -->
            <fileNamePattern>/var/log/svc-canvas/app.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
            <maxHistory>15</maxHistory>
            <totalSizeCap>5GB</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
        </encoder>
    </appender>

    <!-- 全局根级别：生产开 INFO -->
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
    </root>

    <!-- 给特定包单独设级别：临时排查时把我们自己的业务包开到 DEBUG -->
    <logger name="com.example.canvas" level="DEBUG"/>
    <!-- 框架太吵，单独压到 WARN，免得淹没业务日志 -->
    <logger name="org.apache.rocketmq" level="WARN"/>
</configuration>
```

几个要点：

- **输出格式（pattern）就是日志每一行的"列结构"**。上面这份会打成：`时间 级别 进程 [线程] [traceId] logger 消息`。`%X{traceId}` 就是从 MDC 里取我们刚塞进去的 traceId，`:-` 表示取不到时留空。固定的列结构是后面能用 `awk`、能被 ELK 解析的前提。
- **滚动归档（rolling）**：日志不能无限写进一个文件，否则迟早撑爆磁盘。配置按天切分、单文件超 100MB 也切、最多留 15 天、总量封顶 5GB，旧的自动 `.gz` 压缩。否则你会遇到后端经典事故——**磁盘被日志写满，服务直接挂**。
- **不同 logger 不同级别**：`<root>` 是兜底级别；给 `com.example.canvas` 单独开 DEBUG，就只让我们自己的业务代码啰嗦起来，而把吵闹的框架（如 RocketMQ）压到 WARN。这就是"临时排查只给一个包开 DEBUG"的落地方式。
- 文件名用 `logback-spring.xml`（带 `-spring`）而不是 `logback.xml`，这样才能用 `<springProfile>` 按环境区分配置（比如生产打文件、本地只打控制台），这点和上一章 [配置与环境管理](/back-end/frontend-backend-guide/25-config-and-env) 的 profile 是一套思路。

---

## 日志去哪了：这是最该先搞清楚的事

前端工程师上手后端最常卡住的一步：**"日志我会打了，可它到底在哪？我怎么看到？"** 答案取决于服务怎么部署，分三种典型场景。

```text
                日志的三种去处与查看方式
┌──────────────────────────────────────────────────────────────┐
│  场景 A：本地 / 传统服务器，日志落到文件                       │
│     app.log 文件 ──────► tail -f / less / grep 直接看          │
│                                                                │
│  场景 B：Docker 容器，日志打到 stdout（推荐）                  │
│     程序 stdout ──► Docker 收集 ──► docker logs <容器>         │
│                                                                │
│  场景 C：Kubernetes，多副本 Pod，日志打到 stdout               │
│     各 Pod stdout ──► k8s 收集 ──► kubectl logs <pod>          │
│                       └──► 采集器(Fluent Bit) ──► ELK/Loki     │
└──────────────────────────────────────────────────────────────┘
```

核心原则，**云原生时代请记死这一条**：

> **容器里的服务，日志应该打到 stdout（标准输出），而不是自己写文件。**

为什么？容器是"一次性"的——随时可能被销毁重建（见 [Docker 实战](/back-end/frontend-backend-guide/23-docker-in-practice) 与 [Kubernetes 实战](/back-end/frontend-backend-guide/24-kubernetes-in-practice)）。如果你把日志写进容器内部的文件，容器一删，日志全没了。打到 stdout 后，Docker / Kubernetes 会统一接管收集，你用平台命令就能看；采集器（如 Fluent Bit）也能从 stdout 把日志吸走、送进集中式系统。所以上面那份 logback 配置里，`CONSOLE` appender 在容器场景才是主角。

> 前端类比：这就像你不在浏览器里手动 `localStorage` 存日志，而是把错误 `console.error` 出去、由 Sentry 这类平台统一收集。后端的 stdout + 采集器，等价于前端的"上报到监控平台"。

### 场景 A：日志落在文件里——本地、传统服务器

```bash
# 实时跟踪日志，新内容自动滚动出来（最常用）
tail -f /var/log/svc-canvas/app.log
```

预期输出（会持续滚动）：

```text
2026-06-01 14:23:05.102 INFO  12 --- [io-8080-exec-12] [a1b2c3d4e5f6] c.e.canvas.controller.ImageController : 收到生图请求 uid=10086
2026-06-01 14:23:05.118 INFO  12 --- [io-8080-exec-12] [a1b2c3d4e5f6] c.e.canvas.service.ImageTaskServiceImpl : 任务已提交 taskId=T20260601142305
```

怎么读：`tail -f` 像前端 `vite`/`webpack-dev-server` 的终端——它挂在那里，每来一条新日志就推一行给你。`Ctrl+C` 退出。`-f` 是 follow（跟随）的意思。

### 场景 B：Docker 容器

```bash
# 查最近 200 行并持续跟踪 svc-canvas 容器的日志
docker logs -f --tail 200 svc-canvas
```

`docker logs` 读的就是容器里进程 stdout/stderr 的内容。`-f` 同样是持续跟踪，`--tail 200` 表示先回看最后 200 行（不加会从头打，量大时刷屏）。

### 场景 C：Kubernetes

```bash
# 先看 svc-canvas 有哪些 Pod（多副本时不止一个）
kubectl get pods -l app=svc-canvas

# 跟踪某个 Pod 最近 100 行
kubectl logs -f --tail 100 svc-canvas-7d9f8c6b5-x2k4p
```

预期输出（先看 Pod 列表）：

```text
NAME                          READY   STATUS    RESTARTS   AGE
svc-canvas-7d9f8c6b5-x2k4p    1/1     Running   0          3h
svc-canvas-7d9f8c6b5-9wq7n    1/1     Running   2          3h
```

怎么读：svc-canvas 有 **2 个副本**（两个 Pod）。注意第二个 `RESTARTS 2`——它重启过 2 次，多半之前崩过，值得去翻它的历史日志（`kubectl logs --previous`）。多副本意味着**同一次请求可能落在任意一个 Pod 上**，所以你单看一个 Pod 可能找不到日志——这正是下面"traceId 串链"和"集中式日志"要解决的痛点。Kubernetes 细节见 [Kubernetes 实战](/back-end/frontend-backend-guide/24-kubernetes-in-practice)。

---

## 命令实操：从一堆日志里捞出有用的

不管日志落在文件还是从 `docker logs` 输出，看日志的核心技能就是几个 Unix 文本命令。这些是后端的"DevTools 快捷键"，必须练成肌肉记忆。下面统一以文件 `app.log` 为例（容器里把 `cat app.log` 换成 `docker logs svc-canvas` 即可，用 `|` 管道接同样的命令）。Linux 命令基础见 [Linux 服务器必会](/back-end/frontend-backend-guide/21-linux-server-essentials)。

我们用下面这段**真实风格的多行日志样例**贯穿演示，假设它就在 `app.log` 里：

```text
2026-06-01 14:23:05.102 INFO  12 --- [io-8080-exec-12] [a1b2c3d4e5f6] c.e.canvas.controller.ImageController : 收到生图请求 uid=10086 prompt=a cat
2026-06-01 14:23:05.118 INFO  12 --- [io-8080-exec-12] [a1b2c3d4e5f6] c.e.canvas.service.ImageTaskServiceImpl : 开始创建任务 uid=10086
2026-06-01 14:23:05.121 INFO  12 --- [io-8080-exec-12] [a1b2c3d4e5f6] c.e.canvas.feign.UserFeignClient : 调用 svc-user 扣配额 uid=10086 n=1
2026-06-01 14:23:05.355 WARN  12 --- [io-8080-exec-12] [a1b2c3d4e5f6] c.e.canvas.service.ImageTaskServiceImpl : 配额查询较慢 cost=234ms uid=10086
2026-06-01 14:23:05.402 ERROR 12 --- [io-8080-exec-12] [a1b2c3d4e5f6] c.e.canvas.service.ImageTaskServiceImpl : 创建生图任务失败 uid=10086
java.lang.NullPointerException: Cannot invoke "com.example.api.dto.QuotaVO.getRemain()" because "quotaVO" is null
    at com.example.canvas.service.ImageTaskServiceImpl.createImageTask(ImageTaskServiceImpl.java:87)
    at com.example.canvas.controller.ImageController.create(ImageController.java:46)
    at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)
    ... 48 common frames omitted
2026-06-01 14:23:05.410 INFO  12 --- [io-8080-exec-13] [99887766aabb] c.e.canvas.controller.ImageController : 收到生图请求 uid=20050 prompt=a dog
```

### 1. 实时跟踪：tail -f

```bash
tail -f app.log
```

挂着实时看新日志。排查"现在正在发生"的问题时，先开一个 `tail -f`，再去触发一次操作，看日志怎么滚。

### 2. 只看错误：grep "ERROR"

目标：这服务最近报了哪些错？

```bash
grep "ERROR" app.log
```

预期输出：

```text
2026-06-01 14:23:05.402 ERROR 12 --- [io-8080-exec-12] [a1b2c3d4e5f6] c.e.canvas.service.ImageTaskServiceImpl : 创建生图任务失败 uid=10086
```

怎么读：`grep "ERROR"` 把所有含 "ERROR" 的行筛出来——相当于前端在 Console 里点"只看 Errors"。但注意：它只筛**含 ERROR 字样的那一行**，下面那几行堆栈不含 "ERROR" 字样，被漏掉了。要连堆栈一起看，得用下面的上下文参数。

### 3. 看上下文：grep -A / -B / -C

一条 ERROR 往往孤掌难鸣，你需要它前后发生了什么。

```bash
# -A 5：连同匹配行后面 5 行一起显示（After）。看堆栈就靠它
grep -A 5 "创建生图任务失败" app.log
```

预期输出：

```text
2026-06-01 14:23:05.402 ERROR 12 --- [io-8080-exec-12] [a1b2c3d4e5f6] c.e.canvas.service.ImageTaskServiceImpl : 创建生图任务失败 uid=10086
java.lang.NullPointerException: Cannot invoke "com.example.api.dto.QuotaVO.getRemain()" because "quotaVO" is null
    at com.example.canvas.service.ImageTaskServiceImpl.createImageTask(ImageTaskServiceImpl.java:87)
    at com.example.canvas.controller.ImageController.create(ImageController.java:46)
    at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)
    ... 48 common frames omitted
```

怎么读：`-A N` = 匹配行 **A**fter 之后 N 行，`-B N` = **B**efore 之前 N 行，`-C N` = 前后各 N 行（**C**ontext，最常用）。现在堆栈完整出来了——下一节就靠它定位。

### 4. 按 traceId 串起一次请求

目标：把某次请求（traceId = `a1b2c3d4e5f6`）在这个服务里干的所有事，按顺序看一遍。

```bash
grep "a1b2c3d4e5f6" app.log
```

预期输出：

```text
2026-06-01 14:23:05.102 INFO  12 --- [io-8080-exec-12] [a1b2c3d4e5f6] c.e.canvas.controller.ImageController : 收到生图请求 uid=10086 prompt=a cat
2026-06-01 14:23:05.118 INFO  12 --- [io-8080-exec-12] [a1b2c3d4e5f6] c.e.canvas.service.ImageTaskServiceImpl : 开始创建任务 uid=10086
2026-06-01 14:23:05.121 INFO  12 --- [io-8080-exec-12] [a1b2c3d4e5f6] c.e.canvas.feign.UserFeignClient : 调用 svc-user 扣配额 uid=10086 n=1
2026-06-01 14:23:05.355 WARN  12 --- [io-8080-exec-12] [a1b2c3d4e5f6] c.e.canvas.service.ImageTaskServiceImpl : 配额查询较慢 cost=234ms uid=10086
2026-06-01 14:23:05.402 ERROR 12 --- [io-8080-exec-12] [a1b2c3d4e5f6] c.e.canvas.service.ImageTaskServiceImpl : 创建生图任务失败 uid=10086
```

怎么读：一个 traceId 把分散的日志**串成了一条时间线**——收到请求 → 开始建任务 → 调 svc-user 扣配额 → 配额查询变慢（WARN）→ 创建失败（ERROR）。注意旁边 `[io-8080-exec-13]` 那条 uid=20050 的请求被自动过滤掉了，因为它是另一个 traceId。这就是 traceId 的威力：**在并发交错的日志里，精准还原出"这一次请求"的完整剧情**。

### 5. 按时间段看：grep + 时间前缀

目标：只看 14:23:05 这一秒发生了什么（线上常按"用户报错的那个时间点"来定位）。

```bash
grep "2026-06-01 14:23:05" app.log
```

因为我们的日志格式固定以 `时间` 开头，直接拿时间字符串当关键字 grep 就能切出时间段。要看一个范围（比如 14:23 整分钟），用 `grep "2026-06-01 14:23:"`。

### 6. 提字段做统计：awk

目标：数一下每个日志级别各出现了多少次，快速判断错误是不是井喷了。

```bash
# 日志格式固定，第 4 列就是级别（INFO/WARN/ERROR），用空格切列
awk '{print $4}' app.log | sort | uniq -c
```

预期输出：

```text
      4 INFO
      1 ERROR
      1 WARN
```

怎么读：`awk '{print $4}'` 把每行按空格切开、取第 4 个字段（级别）；`sort | uniq -c` 排序后去重并计数。这就是为什么前面强调"固定的列结构"——格式稳定，`awk` 才能可靠地按列取值。如果 ERROR 那一栏从平时的个位数突然飙到几千，问题的严重程度一眼可知。

> 前端类比：`awk` 之于日志，约等于你对一个数组 `.map(line => line.split(' ')[3])` 再 `reduce` 计数——只不过它是命令行里的流式处理，几个 G 的日志也不用先全读进内存。

### 7. 翻大文件不刷屏：less

`tail` 适合看末尾，`grep` 适合捞特定行，但当你想**前后翻阅**一个大日志文件时，用 `less`：

```bash
less app.log
```

进去之后：`/关键字` 回车向下搜（n 跳下一个、N 跳上一个）、`G` 跳到文件末尾、`g` 回到开头、`q` 退出。还有一个杀手锏——`less +F`：

```bash
less +F app.log
```

`+F` 让 less 进入"跟踪模式"，效果和 `tail -f` 一样实时滚动；但你按一下 `Ctrl+C`，它就**停下来变回可翻阅模式**，你可以从容往回翻、搜关键字，看完按 `F` 又继续跟踪。`tail -f` 做不到中途停下来翻历史，`less +F` 可以——排查正在发生的问题时极好用。

---

## 实战：从一条 ERROR + 堆栈定位到哪一行抛的

把上面的命令串起来走一遍完整流程。这是后端最高频的一类排查。

**症状**：前端同学反馈，14:23 左右调 `POST /api/canvas/v1/image/create` 偶发 500，浏览器 Network 里响应体是 `{"code":500,"msg":"扣配额失败"}`，他给了你一个响应头里的 traceId：`a1b2c3d4e5f6`。

**第一步：拿 traceId 串出这次请求做了什么。**

```bash
grep "a1b2c3d4e5f6" app.log
```

输出就是上面第 4 节那五行。**读法**：到 "调用 svc-user 扣配额" 都正常，"配额查询较慢" 是个 WARN（埋了伏笔，但还没崩），最后 "创建生图任务失败" 是 ERROR，崩在 `ImageTaskServiceImpl`。

**第二步：把这条 ERROR 的堆栈完整拉出来。**

```bash
grep -A 5 "创建生图任务失败" app.log
```

输出就是上面第 3 节那段带堆栈的内容。现在逐行读这个堆栈——**这是定位的关键，慢慢读**：

```text
java.lang.NullPointerException: Cannot invoke "com.example.api.dto.QuotaVO.getRemain()" because "quotaVO" is null
    at com.example.canvas.service.ImageTaskServiceImpl.createImageTask(ImageTaskServiceImpl.java:87)
    at com.example.canvas.controller.ImageController.create(ImageController.java:46)
    at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(...)
    ... 48 common frames omitted
```

怎么读这段堆栈（**和你读 JS 的 `error.stack` 完全一个思路，调用栈从上往下是"由内到外"**）：

1. **第一行是异常类型 + 原因**：`NullPointerException`——空指针，最常见的 Java 错。后面那句 `Cannot invoke "QuotaVO.getRemain()" because "quotaVO" is null` 说得很清楚：在一个 `null` 的 `quotaVO` 对象上调了 `getRemain()`。
2. **第二行（最上面那个 `at`）就是案发现场**：`ImageTaskServiceImpl.createImageTask(ImageTaskServiceImpl.java:87)`——异常在 `ImageTaskServiceImpl` 这个类的 `createImageTask` 方法、**第 87 行**抛出。这就是你要打开去看的那一行。
3. **往下的 `at` 是调用链**：是 `ImageController.create`（第 46 行）调用了它——印证了 Controller → Service 的链路。
4. `... 48 common frames omitted` 是框架的样板栈帧（Spring/反射那一堆），被折叠了，和业务无关，忽略。

**第三步：去看代码第 87 行。**

打开 `ImageTaskServiceImpl.java:87`：

```java
QuotaVO quotaVO = userFeignClient.getQuota(uid);   // 调 svc-user 返回的可能是 null
if (quotaVO.getRemain() < n) {                      // ← 第 87 行：quotaVO 为 null 时这里 NPE
    return RtData.fail("配额不足");
}
```

**结论**：`userFeignClient.getQuota(uid)` 返回了 `null`（很可能 svc-user 那边异常或超时降级返回了 null），代码没做判空就直接 `.getRemain()`，导致 NPE。再结合第一步那条 WARN "配额查询较慢 cost=234ms"——svc-user 这次本来就慢/不稳。修复方向：(1) 这里加判空 / 用 `Optional`；(2) 去 svc-user 顺着同一个 traceId 查它为什么返回了 null。第二个方向正好引出下一节——**跨服务串链**。

---

## traceId 串起跨服务的全链路

上一节是在**单个服务**（svc-canvas）的日志里串。但一个生图请求会穿过好几个服务（呼应 [一个请求的完整链路](/back-end/frontend-backend-guide/03-request-lifecycle)）：网关把 traceId 写进 header，Feign 拦截器一路透传，于是**同一个 traceId 出现在每个服务的日志里**。

```text
            一个请求, 一个 traceId, 穿过多个服务
   traceId = a1b2c3d4e5f6
        │
   ┌────▼──────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐
   │svc-gateway│──►│svc-canvas│──►│ svc-user │   │  svc-ai  │
   └───────────┘   └──────────┘   └──────────┘   └──────────┘
        │               │              │              │
        ▼               ▼              ▼              ▼
   gateway.log     canvas.log      user.log        ai.log
   每个文件里都含 a1b2c3d4e5f6 ── grep 同一个 traceId 即可拼出完整链路
```

所以当 svc-canvas 那条 ERROR 怀疑是 svc-user 返回 null 时，你跳到 svc-user 的机器/容器，用**同一个 traceId** 接着查：

```bash
# 在 svc-user 上
grep "a1b2c3d4e5f6" /var/log/svc-user/app.log
```

预期输出：

```text
2026-06-01 14:23:05.130 INFO  9 --- [io-8081-exec-5] [a1b2c3d4e5f6] c.e.user.controller.QuotaController : 收到扣配额请求 uid=10086 n=1
2026-06-01 14:23:05.350 ERROR 9 --- [io-8081-exec-5] [a1b2c3d4e5f6] c.e.user.service.QuotaServiceImpl : 查配额超时 uid=10086
org.springframework.dao.QueryTimeoutException: Redis command timed out
    at ...
```

怎么读：同一个 traceId 在 svc-user 里继续讲故事——它收到了扣配额请求，但**查 Redis 配额超时**了，于是给 svc-canvas 返回了 null/异常。链路真相大白：**Redis 慢 → svc-user 查配额超时返回 null → svc-canvas 没判空 → NPE → 前端 500**。

这就是 traceId 的终极价值：**在微服务这种"案发现场分散在好几台机器上"的环境里，一个 traceId 是把碎片拼回完整故事的唯一线索**。没有它，你只能在每个服务里靠时间和 uid 瞎猜，几乎不可能拼对。traceId 是怎么在服务间透传的，前面 [一个请求的完整链路](/back-end/frontend-backend-guide/03-request-lifecycle) 的"Header 透传"一节已经讲过；更完整的链路追踪（分布式 tracing）见 [可观测性](/back-end/frontend-backend-guide/30-observability)。

---

## 集中式日志：当 grep 不够用时

上面 `grep` 那套在**几台机器、日志量不大**时很好用。但真上了规模，问题就来了：

- svc-canvas 有 10 个 Pod，一次请求随机落在某一个上，你不知道该上哪台机器 `grep`。
- 日志按天滚动、还压缩成了 `.gz`，跨好几天排查时一个个文件 `zgrep` 太痛苦。
- Pod 被销毁重建后，里面的日志（如果写在容器内）就没了。

解决方案是**集中式日志系统**：所有服务把日志统一吐到一个地方，提供按 traceId / 关键字 / 时间范围的全局检索和可视化。两套主流方案，你只需知道名字和定位：

- **ELK**：Elasticsearch（存储与检索）+ Logstash（采集与解析，常用更轻的 Filebeat / Fluent Bit 替代）+ Kibana（可视化界面，在网页里搜日志、画图表、设告警）。功能最全、最经典。
- **Loki + Grafana**：Grafana Loki 是更轻量的新方案，"像 Prometheus 那样存日志"，资源占用小，和 Grafana 面板天然集成。中小团队越来越常用。

有了它们，你在 Kibana / Grafana 的搜索框里敲一句 `traceId:"a1b2c3d4e5f6"`，**所有服务、所有副本**里这次请求的日志立刻按时间排好出现在一个页面上——这就是上一节那套 `grep` 跨服务串链的"网页升级版"，再也不用 SSH 上十台机器了。

> 前端类比：从在每台机器上 `grep` 升级到集中式日志，就像你从"在每个用户浏览器里看 console" 升级到 "Sentry 后台里按 traceId/用户搜全部上报错误"——同一种信息，从分散查看变成统一检索。

集中式日志只是"可观测性"的一部分（另外两部分是指标 metrics 和链路追踪 tracing），三者怎么配合排障，见 [可观测性](/back-end/frontend-backend-guide/30-observability)。

---

## 小结

- **日志是后端的 Console**：出问题第一动作是看日志，不是改代码、不是重启。日志分五级（trace/debug/info/warn/error），生产一般开 INFO，临时排查给某个包单独开 DEBUG，永远别用 `System.out.println`。
- **打日志的两条铁律**：用占位符 `log.info("订单 {} 扣 {}", id, n)`（不要 `+` 拼接，配合级别门槛省开销）；打异常把异常对象作最后一个参数 `log.error("失败", e)`（保住堆栈）。再用 MDC 给当前请求线程塞 traceId，让每条日志自动带上。
- **日志去哪了**：容器时代日志应打到 **stdout**，由 Docker（`docker logs`）/ Kubernetes（`kubectl logs`）/ 采集器统一接管；别在容器里写文件，容器一删就没了。本地/传统服务器才落文件，用 `tail -f` / `less` 看。
- **看日志的核心命令**：`tail -f` 实时跟踪、`grep ERROR` 捞错、`grep -A/-B/-C` 看上下文、`grep <traceId>` 串起一次请求、`awk` 按列提字段统计、`less +F` 可暂停的实时跟踪。读 ERROR 堆栈时，第一行看异常类型与原因，紧跟的第一个 `at` 就是案发的类和行号。
- **traceId 串链 + 集中式日志**：同一个 traceId 贯穿所有服务的日志，是微服务排障把碎片拼成完整故事的唯一线索；规模上来后用 ELK 或 Loki+Grafana 做全局检索，等价于前端把 console 升级成 Sentry。

### 自测

1. 为什么 `log.debug("result=" + obj.toString())` 这种写法即使在只开 INFO 的生产环境也有性能浪费？换成占位符写法应该怎么写，框架在背后多做了什么判断？
2. 你拿到一条 NPE 的堆栈，最上面是 `at com.example.canvas.service.ImageTaskServiceImpl.createImageTask(ImageTaskServiceImpl.java:87)`，下面跟着 `at ...ImageController.create(...)`。这两行分别告诉你什么？你应该先去看哪个文件的第几行？
3. svc-canvas 有 8 个 Pod，前端报某次请求 500 并给了你一个 traceId。为什么直接 `kubectl logs` 某一个 Pod 很可能找不到这次请求的日志？这种情况下集中式日志（ELK / Loki）帮你解决了什么？

### 下一章

下一章 [排查方法论](/back-end/frontend-backend-guide/27-troubleshooting-methodology) 会把这一章学到的"看日志"放进一套完整的排查框架里——从一个模糊的"接口好像挂了"开始，怎么一步步缩小范围、定位根因，而不是东一榔头西一棒子地瞎试。

# 动手练习

想知道一个接口怎样返回数据、慢调用怎样找，或线程栈里的死锁长什么样，可以从下面挑一个练习。每题都单独写出前提、做法和预期现象，不要求先完成前一题。

**先说明旧练习的边界：原来的 `svc-*`、`cpt-*`、`RtData` 和生图场景是教学片段，不是本仓库中另有一套完整工程。** 不需要去找 `UserController`、`AiFeignClient` 或统一响应类。这里改用你自己准备的最小应用、给定材料和独立 Java 程序练习；Feign、MQ 部分仍是明确标注的阅读题，不假装几段代码就是能运行的系统。

本页按需选做，不替代 [五阶段主线](./index) 的完整练习工程。所有代码应放在另建的本地实验目录或自己的练习工程中，不加入文档站，不接公司或生产资源。下面的输出都是示意，**本次只修改文档，没有运行本机 Compose、故障程序、容器或远程部署**。

| 练习 | 需要先有什么 | 可以观察什么 |
| --- | --- | --- |
| 1～3 | 自己的 Java 17、Spring Boot 3.x Web 空项目 | 请求映射、层间调用、响应字段 |
| 4～5 | 只需阅读本页提供的材料 | 远程调用失败与消息重投的处理边界 |
| 6 | 本机 Docker 与 Compose、足够资源 | 依赖容器启动与服务响应的区别 |
| 7 | 自己的 Spring Boot Web 空项目 | 端到端耗时与分段计时 |
| 8～9 | 本机 JDK 17、隔离目录 | 堆 OOM、引用关系、Java 死锁 |
| 10 | JDK 17、Docker、空实验目录 | 一个独立 HTTP 程序怎样进入镜像 |

## 练习 1：写一个不依赖登录的 GET 接口

先准备一个只有 Spring Web 依赖、能正常启动的练习应用；若还没有，先看 [手写 CRUD 接口](/back-end/frontend-backend-guide/07-build-a-crud-api) 或 [Spring Boot CRUD](/back-end/java/07-spring-boot-crud)。以下假设应用监听本机 8080，且没有额外的安全拦截。不要为做练习修改现有生产网关白名单。

在应用能扫描到的包下新建 `HelloController.java`，按自己的包名补充 package 声明：

```java
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HelloController {
    @GetMapping("/lab/hello")
    public Map<String, String> hello(
            @RequestParam(name = "name", defaultValue = "Frontend") String name) {
        return Map.of("message", "Hello, " + name + "!");
    }
}
```

启动这个应用，从本机请求：

```bash
curl --max-time 5 'http://127.0.0.1:8080/lab/hello?name=Alice'
```

预期得到包含以下内容的 JSON：

```json
{"message":"Hello, Alice!"}
```

如果是 404，先检查端口、路径和类是否被扫描；如果是 401/403，说明自己的项目还有安全规则，不是方法没有返回值。这里的 `name` 只是输入，不是可信用户身份，不能据此做权限判断。

这个练习先确认一件事：URL 的查询参数进入方法后，返回的 Map 可以被转换成 JSON。它不依赖任何特定统一响应包装。

## 练习 2：跟踪 Controller 到 Service 的一次调用

前提仍是自己可启动的 Spring Boot Web 空项目，不需要网关和数据库。想观察的是“同一个请求怎样从入口进入业务层”，不是跨机器时间差。

先添加 `TraceService.java`：

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TraceService {
    private static final Logger log = LoggerFactory.getLogger(TraceService.class);

    public String buildMessage(String requestId) {
        log.info("requestId={} service buildMessage", requestId);
        return "done";
    }
}
```

再在同一个可扫描包下添加 `TraceController.java`：

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import java.util.UUID;

@RestController
public class TraceController {
    private static final Logger log = LoggerFactory.getLogger(TraceController.class);
    private final TraceService service;

    public TraceController(TraceService service) {
        this.service = service;
    }

    @GetMapping("/lab/trace")
    public Map<String, String> trace() {
        String requestId = UUID.randomUUID().toString();
        log.info("requestId={} controller enter", requestId);
        String result = service.buildMessage(requestId);
        log.info("requestId={} controller exit", requestId);
        return Map.of("requestId", requestId, "result", result);
    }
}
```

请求并对照控制台：

```bash
curl --max-time 5 http://127.0.0.1:8080/lab/trace
```

同一标识应出现 `controller enter → service buildMessage → controller exit`。并发请求的日志可能交错，所以要按标识读，不凭相邻行认定属于同一次调用。

这里是手工传递的请求标识，不是已经完成标准链路追踪接入。也不要把两台机器日志时间戳相减就称为纯网络耗时，时钟、调度和排队都可能参与。更多读法见 [读懂日志](/back-end/frontend-backend-guide/26-reading-logs) 与 [请求生命周期](/back-end/frontend-backend-guide/03-request-lifecycle)。

## 练习 3：让一个字段出现在响应里

在自己的 Spring Boot Web 项目中，新增一个独立接口，不依赖前两题：

```java
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProfileController {
    public record Profile(long id, String name, String welcomeMessage) {}

    @GetMapping("/lab/profile")
    public Profile profile() {
        return new Profile(1L, "Alice", "欢迎使用任务列表");
    }
}
```

```bash
curl --max-time 5 http://127.0.0.1:8080/lab/profile
```

预期 JSON 含有 `id`、`name`、`welcomeMessage` 三个字段，字段顺序不是判断标准。然后仅修改 `welcomeMessage` 的文本，重启练习应用，再比较响应。

这样能看清返回对象与 JSON 字段的对应关系。普通 Java 类还需要合适的 getter 或序列化配置，不能只加一个私有字段就保证一定出现在响应中；Java record 自带访问器。若要把这份响应拆到 Service 或 VO 中，再看 [三层架构与目录结构](/back-end/frontend-backend-guide/04-three-layer-and-structure)。

## 练习 4：读懂一次远程调用失败

这是一道阅读题，不需要启动 Feign 服务。假设某个独立 Spring Cloud 工程有下面的调用片段；工程还需要兼容的依赖版本、客户端扫描、Bean、超时与熔断集成配置，**以下不是完整可运行工程**。

```java
@FeignClient(name = "preview-client", url = "${preview.url}")
public interface PreviewClient {
    @GetMapping("/preview")
    String preview();
}
```

应用调用 `preview()`，测试替身没有响应，最后记录了：

```text
requestId=demo-remote start preview
requestId=demo-remote preview failed: Read timed out
requestId=demo-remote response status=503
```

先回答：调用是否成功取得预览？失败发生在连接建立前，还是等待响应期间？这个日志能否证明远端完全没有执行？

`Read timed out` 指向等待读取的超时方向，但不能证明远端没有执行。客户端也不会天然把这次异常转换成 null 或自动进入某个 fallback；是否降级取决于实际异常处理与组件配置。

合理的练习结果，是把“预览暂不可用”明确表达出来，而不是返回一份看似成功的真实结果。如果你自己的隔离工程已配置降级，可以用本地测试替身分别模拟正常、超时和错误响应，比较总耗时与返回契约；不应通过停止共享服务制造失败。

想把它放回系统中理解，可查 [架构总览](/back-end/frontend-backend-guide/02-architecture-overview)。这题的重点是远程调用有独立的失败边界，不是去仓库里找一个不存在的 `AiFeignFallback.java`。

## 练习 5：从给定记录画出 MQ 流程

这也是阅读题，不要求安装 RocketMQ。给定一个任务系统的约定：接收服务可靠记录任务后，发送 `TaskCreated`；工作进程消费后执行任务，再发送 `TaskCompleted`；结果处理器更新任务状态。

```text
接收服务 → TaskCreated → 工作进程
                           ↓
结果处理器 ← TaskCompleted ← 执行结果
```

观察以下示意日志：

```text
10:00:00 receiver taskId=demo-001 task recorded
10:00:01 producer taskId=demo-001 messageId=m1 TaskCreated sent
10:00:02 worker taskId=demo-001 messageId=m1 received
10:00:05 worker taskId=demo-001 execution committed
10:00:08 worker taskId=demo-001 messageId=m1 received again
```

先指出发送者、消费者和消息内容至少需要的业务标识，再判断最后一次接收会有什么风险。

同一消息再次出现，提示可能重投，但仅凭“received again”不能证明任务执行了两次。需要查看消费端是否用稳定业务键保证幂等，以及处理结果是否真正提交。画图时还应标出两处缺口：任务已入库但消息没发出怎么办，执行完成但确认或结果消息失败怎么办。

能讲清这些问题，就完成了本题。不要求凭几行伪代码实现可靠消息系统，具体事务、补偿、重试与死信见 [MQ 可靠性](/back-end/frontend-backend-guide/33-mq-reliability)。

## 练习 6：只起本地依赖，并区分“容器在跑”与“服务可用”

前提是本机 Docker 与 Compose 已安装且有空闲资源。本题只起 Redis 和 MongoDB，不依赖其他题，也不宣称包含可用的 RocketMQ 工程。端口只绑定回环地址；本地 Docker 网络里的服务仍可相互访问，不能拿这份简化配置给多人共享。

在仓库之外新建一个空实验目录，把下面内容保存为 `compose.yml`。MongoDB 密码从当前 shell 环境读取，不写进文件：

```yaml
services:
  mongodb:
    image: mongo:6.0
    ports:
      - "127.0.0.1:27018:27017"
    environment:
      MONGO_INITDB_ROOT_USERNAME: root
      MONGO_INITDB_ROOT_PASSWORD: "${MONGO_INITDB_ROOT_PASSWORD:?请先设置本地实验密码}"
  redis:
    image: redis:7.2
    ports:
      - "127.0.0.1:16379:6379"
```

镜像版本仅沿用教学示例，实际使用应核对维护状态、架构支持和安全更新。这里没有安排业务数据持久化，只放可丢弃的练习数据，不把它当生产配置。

以下在这个实验目录的 Bash 终端中执行。先输入一个仅用于本题的密码，再创建本地容器；这一步会下载镜像、占用端口和资源：

```bash
read -r -s -p '本地 MongoDB 实验密码: ' MONGO_INITDB_ROOT_PASSWORD
printf '\n'
export MONGO_INITDB_ROOT_PASSWORD
docker compose -p task-lab -f compose.yml config --quiet
docker compose -p task-lab -f compose.yml up -d
docker compose -p task-lab -f compose.yml ps
```

如果端口已占用，先查占用者，不要停止不认识的服务。`ps` 显示 `Up` 只说明容器进程正在运行，继续分别检查：

```bash
docker compose -p task-lab -f compose.yml exec redis redis-cli ping
docker compose -p task-lab -f compose.yml exec mongodb \
  mongosh --quiet --username root --password --authenticationDatabase admin \
  --eval 'db.runCommand({ ping: 1 })'
```

MongoDB 客户端提示时输入刚才的密码。预期 Redis 返回 `PONG`，MongoDB 返回包含 `ok: 1` 的结果，表示这两次命令得到了服务响应；还不代表业务账号、索引和持久化已准备好。

失败时先看对应服务的日志与启动时间，不能只重试应用。Compose 的启动顺序也不等于服务已经就绪。用完后，确认仍在这个实验目录、项目名为 `task-lab`，再按需停止这组容器；停止、删除和清理卷是不同操作，不要使用全局清理命令。机制见 [Docker 实战](/back-end/frontend-backend-guide/23-docker-in-practice)。

## 练习 7：给慢接口分段计时

前提是自己的 Java 17、Spring Boot Web 空项目，监听本机 8080，不需要真实数据库。只在这个练习应用中新增以下 Controller，不把故意等待的接口部署到共享环境：

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
public class SlowController {
    private static final Logger log = LoggerFactory.getLogger(SlowController.class);

    @GetMapping("/lab/slow")
    public Map<String, Long> slow() throws InterruptedException {
        long start = System.nanoTime();
        Thread.sleep(1200); // 模拟慢调用，不代表真实 SQL
        long firstEnd = System.nanoTime();
        Thread.sleep(80);   // 模拟响应组装
        long end = System.nanoTime();
        long queryMs = TimeUnit.NANOSECONDS.toMillis(firstEnd - start);
        long assembleMs = TimeUnit.NANOSECONDS.toMillis(end - firstEnd);
        log.info("queryMs={} assembleMs={}", queryMs, assembleMs);
        return Map.of("queryMs", queryMs, "assembleMs", assembleMs);
    }
}
```

用一次请求比较端到端与分段耗时，不做并发压测：

```bash
curl --max-time 5 -sS -w '\ntotal=%{time_total}s\n' \
  http://127.0.0.1:8080/lab/slow
```

预期第一段约 1200ms，第二段约 80ms，端到端还包含传输、框架处理等开销，不要求正好等于 1.280 秒。`sleep` 会受调度影响，测量间隔使用 `nanoTime`，不要把系统时间校准误差算成耗时。

这次能定位到模拟慢调用，但不能由它推断真实系统一定是 SQL 慢。真实查询还要继续分清获取连接、执行与读取结果。进一步看 [读懂日志](/back-end/frontend-backend-guide/26-reading-logs)、[排查实战手册](/back-end/frontend-backend-guide/28-troubleshooting-playbook) 和 [SQL 与索引](/back-end/frontend-backend-guide/10-sql-and-indexes)。

## 练习 8：在小堆独立进程中观察 OOM

本题只需要本机 JDK 17 和一个空实验目录。程序会故意耗尽**自己的 Java 堆**并退出；仍会使用额外进程内存和磁盘，应先确认本机有余量，不能放进共享应用或容器运行。转储只使用合成数据，不混入真实账号与业务内容。

保存完整的 `OomDemo.java`：

```java
import java.util.ArrayList;
import java.util.List;

public class OomDemo {
    private static final List<byte[]> LEAK = new ArrayList<>();

    public static void main(String[] args) {
        int count = 0;
        while (true) {
            LEAK.add(new byte[1024 * 1024]);
            System.out.println("allocated blocks=" + (++count));
        }
    }
}
```

确认目录中没有旧的 `oom.hprof`，避免混淆输出；不要为重新运行而盲目覆盖或删除已有转储。随后编译，并只限制这个实验进程的堆：

```bash
javac -d out OomDemo.java
java -Xmx64m -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=./oom.hprof -cp out OomDemo
```

预期出现 `OutOfMemoryError: Java heap space`；空间和权限允许时，JVM 会报告转储文件已生成。实际分配多少块、文件多大由对象开销和运行时决定，不保证恰好分配 64 块。若没有文件，先看路径、权限、空间和 JVM 提示，不要把“程序退出”直接当成“转储成功”。

使用自己安装的 MAT 打开转储，观察 `ArrayList` 和 `byte[]`，再沿引用找到 `OomDemo.LEAK`。这能解释为什么对象不能回收：静态集合仍持有引用。大量对象本身不自动等于泄漏；本题的关键是它们已无业务用途，却仍被无限保留。

练习结束后按自己的实验资料保留规则处理转储。真实应用的转储可能包含密钥，不能同样随意分享。更多分析见 [OOM 与内存泄漏](/back-end/frontend-backend-guide/20-oom-memory-leak)。

## 练习 9：让两条线程互相等锁，再读报告

本题只需要本机 JDK 17 和空目录。它创建一个故意不退出的独立进程，不操作文件、数据库或其他服务。看完后在启动终端按 Ctrl+C 结束，不对陌生 PID 执行停止命令。

与“睡 200ms 等对方”不同，下面使用 CountDownLatch，让两条线程都拿到第一把锁后，再尝试获取第二把锁。保存为 `DeadlockDemo.java`：

```java
import java.util.concurrent.CountDownLatch;

public class DeadlockDemo {
    private static final Object LOCK_A = new Object();
    private static final Object LOCK_B = new Object();
    private static final CountDownLatch BOTH_LOCKED = new CountDownLatch(2);

    public static void main(String[] args) {
        new Thread(() -> takeLocks(LOCK_A, LOCK_B), "thread-1").start();
        new Thread(() -> takeLocks(LOCK_B, LOCK_A), "thread-2").start();
    }

    private static void takeLocks(Object first, Object second) {
        synchronized (first) {
            BOTH_LOCKED.countDown();
            try {
                BOTH_LOCKED.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            synchronized (second) {
                System.out.println("both locks acquired");
            }
        }
    }
}
```

在实验目录编译运行：

```bash
javac -d out DeadlockDemo.java
java -cp out DeadlockDemo
```

进程会保持运行而不打印 `both locks acquired`。另开终端：

```bash
jps -l
# 先确认 DeadlockDemo 的真实 PID，再替换下面的 12345
jstack 12345
```

预期末尾能看到 `Found one Java-level deadlock`，并指出 `thread-1`、`thread-2` 等待对方持有的锁。回到线程段，对照 `locked` 与 `waiting to lock` 的对象地址，再对照源码中的第一、第二把锁。

修复方向是统一获取顺序，但**不要只把第二条线程也改成 A→B 就保留这个实验闩锁**：第一条线程会持有 A 等第二条倒数，第二条却拿不到 A。正确的对照实验应移除这个为造死锁设置的 `BOTH_LOCKED` 同步点，再让两条线程都按 A→B 获取，观察它们能否完成并退出。

锁的更多边界见 [线程安全与锁](/back-end/frontend-backend-guide/16-thread-safety-locks)，线程报告读法见 [排查实战手册](/back-end/frontend-backend-guide/28-troubleshooting-playbook) 与 [诊断工具箱](/back-end/frontend-backend-guide/29-diagnostic-toolbox)。

## 练习 10：把一个独立 HTTP 程序放进容器

本题不依赖前九题，不要求已有 Spring Boot 或数据库。需要本机 JDK 17、Docker、网络和空实验目录，8080 端口未被占用。程序只提供一个返回 `ok` 的 `/health`，不是完整业务服务，也不是已经做好的生产镜像。

先保存 `HelloServer.java`：

```java
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class HelloServer {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/health", exchange -> {
            byte[] body = "ok\n".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        System.out.println("HTTP server listening on 8080");
    }
}
```

编译并打成可执行 JAR：

```bash
javac --add-modules jdk.httpserver -d out HelloServer.java
jar --create --file app.jar --main-class HelloServer -C out .
```

在同一空目录保存 `Dockerfile`：

```dockerfile
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "--add-modules", "jdk.httpserver", "-jar", "app.jar"]
```

基础镜像应按实际维护与安全策略选择；这里展示的是构建和运行关系，没有加入生产需要的非 root 用户、资源与安全配置。执行下面操作会创建本地镜像和容器，并可能下载基础镜像：

```bash
docker build -t task-http-lab:local .
docker run -d --name task-http-lab \
  -p 127.0.0.1:8080:8080 task-http-lab:local
docker logs --tail 20 task-http-lab
curl --max-time 5 -i http://127.0.0.1:8080/health
```

如果同名容器已存在，先确认是不是自己的上次练习，不要直接删未知对象。日志预期出现 `HTTP server listening on 8080`，HTTP 请求预期得到 200 与 `ok`。日志表示进程启动到了这里，真正的接口响应才补充说明这条本机端口映射可用。

`EXPOSE 8080` 只是镜像说明，真正把端口映射到宿主机的是 `-p`；这里限制在本机回环地址。程序在容器内监听，访问 `127.0.0.1:8080` 的是宿主机映射口，并不是容器自动拥有宿主机的 localhost。

看完以后，确认目标确实是本题创建的 `task-http-lab`，再按需用 Docker 停止它；停止容器与删除数据不同，不使用全局清理。继续理解分层、多阶段构建与运行配置，可查 [Docker 实战](/back-end/frontend-backend-guide/23-docker-in-practice)。

---

每个练习只回答一个小问题。能根据自己的实际输出解释“发生了什么、还没证明什么”，就可以停下来，不必一次做完十题。想阅读从功能开发到交付的更大场景，可按需看 [综合实战](/back-end/frontend-backend-guide/38-capstone-feature-to-prod)，但不要把它误认为本页已经完成了真实上线。

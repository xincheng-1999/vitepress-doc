# 可观测三件套

> 前面几章讲的是"出了事怎么查"——[第 26 章 看日志](/back-end/frontend-backend-guide/26-reading-logs)教你读日志、[第 27 章 问题排查方法论](/back-end/frontend-backend-guide/27-troubleshooting-methodology)给你排查流程。
> 这一章往前一步：怎么让系统**在你被叫醒之前就告诉你它病了**，以及病在哪。
> 这就是可观测性（Observability）——从"被动救火"升级到"主动发现"。

线上系统不怕有问题，怕的是**你不知道它有问题**，等用户投诉、运营找上门才反应过来。可观测性的目标，是把系统内部状态变成你能看见、能查询、能告警的数据，让你在故障萌芽期就发现它、在故障发生时能快速定位。

> 💡 **前端类比**：你在前端做过的事其实都是可观测性——Sentry 收集前端报错（日志）、性能监控面板看页面加载耗时和 LCP/FID（指标）、埋点 SDK 追踪用户一次操作经过了哪些页面（链路）。后端可观测性是同一套思路，只是覆盖范围从浏览器扩展到了**服务端全链路**：网关、十来个微服务、MySQL/MongoDB/Redis/RocketMQ、机器资源，全都要看得见。

---

## 30.1 可观测性的三大支柱

业界把可观测性拆成三类数据，俗称"三件套"（Three Pillars）：**Metrics（指标）、Tracing（链路追踪）、Logging（日志）**。它们不是互相替代，而是各管一段、互相配合。

| 支柱 | 是什么 | 回答的问题 | 数据形态 | 前端类比 |
| --- | --- | --- | --- | --- |
| **Metrics 指标** | 聚合后的数字，随时间变化 | "系统现在健康吗？趋势怎样？" | 一串带时间戳的数值（QPS、RT、错误率） | 性能监控面板的曲线图 |
| **Tracing 链路** | 一个请求跨服务的耗时分解 | "这个慢请求慢在哪一跳？" | 一棵 span 树（瀑布图） | DevTools Network 瀑布图 |
| **Logging 日志** | 具体某个事件的完整细节 | "那一刻到底发生了什么？" | 一行行文本/JSON | Sentry 里的报错堆栈 |

三者的核心差异，可以用"放大倍数"来理解：

```text
        粗 ←——————— 观察粒度 ———————→ 细
        │                              │
     Metrics            Tracing      Logging
   （全局趋势）        （单请求链路）   （单事件细节）
        │                  │             │
   "错误率涨到5%"    "慢在svc-user这跳"  "NPE在第88行"
   告诉你"有事"       告诉你"在哪"        告诉你"为什么"
```

- **Metrics** 是高度聚合的，存储成本低、能存很久，适合看趋势、做告警。但它只告诉你"错误率涨了"，不告诉你"哪个请求、为什么"。
- **Tracing** 把单个请求在各服务的耗时拆开，适合定位"慢/错在哪一跳"。
- **Logging** 是最细的，记录了某个具体事件的完整上下文，适合看"那一刻究竟发生了什么"。但全量存日志成本高，所以一般只保留几天到几周。

> 💡 **前端类比**：Metrics 像你看 Lighthouse 评分的趋势（总分从 90 掉到 60，知道变差了）；Tracing 像 Performance 面板里一帧的火焰图（知道是哪个函数耗时）；Logging 像 console 里那条具体的红色报错（知道是哪行代码抛的）。三个一起用，才能从"变差了"一路追到"哪行代码"。

---

## 30.2 三者如何配合定位问题

单独用任何一个都不够，真正的威力在于**串起来用**。标准动作是一条从粗到细的漏斗：

```text
监控发现异常  ──→  链路定位服务  ──→  日志看细节
 (Metrics)         (Tracing)          (Logging)
    │                  │                  │
"svc-canvas       "拿一条慢请求       "在 svc-user 日志
 错误率突然        的 traceId，看      里按这个 traceId
 涨到 8%，         它慢/错在          搜，看到扣配额
 P99 飙到 5s"     svc-user 这跳"     抛了 DataAccessException"
    │                  │                  │
 告警把你叫醒      把范围从十个服务      看到完整异常栈和
                  收敛到一个接口        SQL，定位根因
```

把它落到运行示例项目的一个真实场景：

1. **Metrics 发现**：Grafana 看板上 svc-canvas 的 `提交生图任务` 接口错误率从 0.1% 突然涨到 8%，告警群里收到一条 "svc-canvas error rate > 5%"。你被告警叫醒，但还不知道为什么。
2. **Tracing 定位**：在链路追踪系统里筛"svc-canvas 且状态=error"的请求，点开一条，火焰图显示报错发生在 `svc-canvas → svc-user 扣配额` 这个 span，且这个 span 标红。范围从"十来个服务"收敛到"svc-user 的扣配额接口"。
3. **Logging 看细节**：复制这条链路的 traceId，去 svc-user 日志里搜（呼应 [第 26 章 看日志](/back-end/frontend-backend-guide/26-reading-logs) 讲的 traceId 串联），看到完整异常栈——`DataAccessException: Deadlock found when trying to get lock`。根因找到了：扣配额的 SQL 撞上了死锁。

注意这条漏斗的顺序：**Metrics 告诉你"有事、有多严重"，Tracing 告诉你"在哪一跳"，Logging 告诉你"为什么"**。少了 Metrics 你不知道出事；少了 Tracing 你要在十个服务里乱翻；少了 Logging 你看不到那行致命的异常栈。

---

## 30.3 Metrics 实操：Spring Boot Actuator

Spring Boot 自带的 **Actuator** 是暴露指标的标准入口，配合 **Micrometer**（指标门面，类比日志界的 SLF4J）打点、**Prometheus** 抓取存储、**Grafana** 可视化，构成最主流的一套 Metrics 方案。

### 第一步：引入并暴露端点

在 cpt-common 里统一引入依赖，所有 svc-* 复用（呼应 [第 13 章 连接池](/back-end/frontend-backend-guide/13-connection-pools) 里连接池指标也是这套体系暴露的）：

```xml
<!-- cpt-common 的 pom.xml -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

在 `application.yml` 里配置要暴露哪些端点：

```yaml
management:
  endpoints:
    web:
      exposure:
        # 生产别用 "*" 全开，按需暴露，避免泄露敏感信息
        include: health,metrics,prometheus,info
  endpoint:
    health:
      show-details: when-authorized   # 健康详情仅授权后可见
  metrics:
    tags:
      application: svc-canvas          # 给所有指标打上服务名标签，多服务才区分得开
```

### 第二步：看三个核心端点

启动后用 curl 直接看。下面按"目标 → 命令 → 预期输出 → 怎么读"的结构走。

**目标**：确认 svc-canvas 实例存活、且它依赖的 MongoDB/Redis 都健康。

**可复制的命令**：

```bash
curl -s http://svc-canvas:8080/actuator/health | jq
```

**预期输出样例**：

```json
{
  "status": "UP",
  "components": {
    "mongo":      { "status": "UP" },
    "redis":      { "status": "UP" },
    "diskSpace":  { "status": "UP", "details": { "free": 32212254720 } },
    "rocketmq":   { "status": "UP" }
  }
}
```

**怎么读这段输出**：顶层 `status: UP` 表示整体健康；`components` 列出每个依赖的健康状态。这就是 K8s 的 liveness/readiness 探针调用的接口（呼应 [第 24 章 Kubernetes 实战](/back-end/frontend-backend-guide/24-kubernetes-in-practice)）——只要某个 component 变成 `DOWN`，整体就 `DOWN`，K8s 会拒绝把流量打进来或重启 Pod。

> 💡 **前端类比**：`/actuator/health` 就像你给前端项目写的 `/healthz` 探活接口，或者 Next.js 里的一个返回 `{ ok: true }` 的 API route——只是它还会自动级联检查下游中间件的连通性。

**目标**：看某个具体指标当前值，比如 JVM 堆内存用量。

**可复制的命令**：

```bash
# 先列出有哪些指标名
curl -s http://svc-canvas:8080/actuator/metrics | jq '.names | .[0:6]'
# 再查某个指标的明细
curl -s "http://svc-canvas:8080/actuator/metrics/jvm.memory.used?tag=area:heap" | jq
```

**预期输出样例**：

```json
{
  "name": "jvm.memory.used",
  "measurements": [ { "statistic": "VALUE", "value": 1287654321 } ],
  "availableTags": [ { "tag": "id", "values": ["G1 Eden Space", "G1 Old Gen"] } ]
}
```

**怎么读**：`value` 是字节数，约 1.28GB 堆内存正在使用。`/actuator/metrics` 适合人工临时看一个数，但它是"当前快照"，看不了趋势——要看趋势得靠 Prometheus 把它每隔几秒抓一次存起来。

**目标**：拿到 Prometheus 格式的全量指标（给抓取器用，不是给人看的）。

**可复制的命令**：

```bash
curl -s http://svc-canvas:8080/actuator/prometheus | head -20
```

**预期输出样例**：

```text
# HELP http_server_requests_seconds Duration of HTTP server request handling
# TYPE http_server_requests_seconds summary
http_server_requests_seconds_count{uri="/api/canvas/v1/image/create",status="200"} 18342.0
http_server_requests_seconds_sum{uri="/api/canvas/v1/image/create",status="200"} 2103.7
http_server_requests_seconds_count{uri="/api/canvas/v1/image/create",status="500"} 27.0
# HELP jvm_gc_pause_seconds Time spent in GC pause
jvm_gc_pause_seconds_count{action="end of minor GC"} 412.0
```

**怎么读**：每行是 `指标名{标签...} 值`。第三行能算出 create 接口共成功 18342 次、总耗时 2103.7 秒（平均 ≈ 0.11s）；第五行说明有 27 次返回 500（错误率 ≈ 0.15%）。这就是 Prometheus 每隔几秒来抓一次的原始数据。

### 第三步：用 Micrometer 打自定义业务指标

框架自带的指标只覆盖技术层面（HTTP、JVM、连接池）。**业务指标**得自己打。比如想监控"每秒提交多少生图任务、其中多少因配额不足被拒"：

```java
@Service
public class ImageTaskServiceImpl implements ImageTaskService {

    private final Counter submitCounter;
    private final Counter quotaRejectCounter;
    private final Timer   submitTimer;

    public ImageTaskServiceImpl(MeterRegistry registry) {
        // Counter：只增不减，适合计数（呼应前端埋点的"事件计数"）
        this.submitCounter = Counter.builder("canvas.task.submit")
                .description("提交生图任务次数")
                .register(registry);
        this.quotaRejectCounter = Counter.builder("canvas.task.reject")
                .tag("reason", "quota")     // 用 tag 区分拒绝原因，便于分组
                .register(registry);
        // Timer：同时记录次数和耗时分布，自动产出 P95/P99
        this.submitTimer = Timer.builder("canvas.task.submit.latency")
                .publishPercentiles(0.95, 0.99)
                .register(registry);
    }

    public RtData<String> createImageTask(CreateTaskReq req) {
        return submitTimer.record(() -> {            // 用 Timer 包住业务逻辑计时
            submitCounter.increment();
            if (!userClient.deductQuota(req.getUid(), 1)) {
                quotaRejectCounter.increment();
                return RtData.fail("配额不足");
            }
            // ...创建任务、发 RocketMQ 消息...
            return RtData.ok(taskId);
        });
    }
}
```

打完之后 `canvas_task_submit_total`、`canvas_task_reject_total`、`canvas_task_submit_latency_seconds` 就会自动出现在 `/actuator/prometheus` 里，Grafana 就能画"配额拒绝率"曲线了。

> 💡 **前端类比**：`Counter.increment()` 就是你在前端调 `track('submit_task')` 埋一个点；`Timer.record(...)` 就是 `performance.mark/measure` 量一段耗时。MeterRegistry 就是埋点 SDK 的全局实例。

### 第四步：Prometheus 抓取 + Grafana 看板

Prometheus 是**主动拉取（pull）**模型——你告诉它去哪些地址抓，它定时来抓。在 `prometheus.yml` 里加一段 scrape 配置：

```yaml
scrape_configs:
  - job_name: 'svc-canvas'
    metrics_path: '/actuator/prometheus'   # 对应 Actuator 暴露的路径
    scrape_interval: 15s                    # 每 15 秒抓一次
    static_configs:
      - targets:
          - 'svc-canvas-0:8080'
          - 'svc-canvas-1:8080'             # 多实例都列上，Prometheus 分别抓
        labels:
          env: 'prod'
```

> 在 K8s 里通常不写死 IP，而是用 `kubernetes_sd_configs` 自动发现带特定 annotation 的 Pod，省得每次扩缩容都改配置。这里用静态配置是为了让你先看懂模型。

抓到数据后，在 Grafana 里用 PromQL 查询画图。几个常用查询：

```text
# 每秒请求数（QPS）：对 count 指标求 5 分钟速率
rate(http_server_requests_seconds_count{application="svc-canvas"}[5m])

# 错误率：5xx 占比
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
  / sum(rate(http_server_requests_seconds_count[5m]))

# P99 响应时间
histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))
```

> 💡 **前端类比**：Prometheus 主动来抓你的 `/actuator/prometheus`，类似你的监控平台定时拉取一个数据上报接口；PromQL 之于指标，约等于你在 BI 工具里对埋点数据写聚合查询。

---

## 30.4 关键指标清单（该盯哪些数）

刚上手时最容易犯的错是"指标一堆，不知道看哪个"。下面这张表是排障和容量评估最常用的一组，建议做成 Grafana 看板首页：

```text
┌──────────────┬───────────────────────────┬──────────────────────────────┐
│ 类别         │ 指标                      │ 看它能发现什么                │
├──────────────┼───────────────────────────┼──────────────────────────────┤
│ 流量         │ QPS / TPS                 │ 突增可能被刷，骤降可能上游挂  │
│ 延迟         │ RT P50 / P95 / P99        │ 看 P99 而非平均，长尾才是真痛 │
│ 错误         │ 错误率 (5xx 占比)         │ 最直接的健康信号，优先告警    │
│ JVM 内存     │ 堆已用 / 堆上限           │ 持续逼近上限 → 内存泄漏前兆   │
│ JVM GC       │ Young/Full GC 频率与耗时  │ Full GC 频繁 = 性能悬崖       │
│ 线程         │ tomcat 活跃线程 / 最大线程│ 接近满 → 请求开始排队/超时    │
│ 连接池       │ active / max 连接数       │ 打满 → 后续请求拿不到连接     │
│ 机器         │ CPU / 内存 / 磁盘 / 网络  │ 区分"应用问题"还是"机器问题"  │
└──────────────┴───────────────────────────┴──────────────────────────────┘
```

几条经验：

- **延迟一定看 P95/P99，不要只看平均值**。平均 80ms 看着很美，但 P99 可能是 5s——那 1% 的慢请求恰恰是用户投诉的来源。平均值会把长尾"平摊"掉，骗你说一切正常。
- **错误率是最该优先告警的指标**。它最直接、误报最少。QPS 高低是中性的，但错误率涨就是实打实出事了。
- **连接池和线程池使用率是"快满前"的预警信号**（呼应 [第 13 章 连接池](/back-end/frontend-backend-guide/13-connection-pools)、[第 15 章 线程池](/back-end/frontend-backend-guide/15-thread-pools-executor)）。等它们打满了请求才超时，那已经是故障；盯着使用率到 80% 就告警，能在故障前介入。
- **机器指标用来"分层"**：应用 RT 高，先看是不是机器 CPU 已经 100%（那是资源不够，扩容）还是 CPU 很闲但 RT 高（那是应用逻辑/锁/下游问题，扩容没用）。

---

## 30.5 Tracing：让 traceId 串起整条链路

Metrics 告诉你"svc-canvas 错误率涨了"，但一个生图请求要穿过网关 → svc-canvas → svc-user → svc-ai → svc-oss 一长串服务，**到底慢/错在哪一跳**？这就是链路追踪要解决的。

核心机制其实你在 [第 3 章 请求生命周期](/back-end/frontend-backend-guide/03-request-lifecycle) 和 [第 26 章 看日志](/back-end/frontend-backend-guide/26-reading-logs) 已经见过：**网关入口生成一个全局唯一的 `traceId`，透传给链路上每一个服务**，每个服务在自己这段干活时再生成一个 `spanId` 标记"这一跳"。把所有 span 按父子关系和时间拼起来，就是一棵带耗时的树——画出来就是火焰图。

```text
一个生图请求的链路火焰图（traceId=a1b2c3d4，总耗时 5.2s）

svc-gateway  ├──────────────────────────────────────────────┤  5200ms
svc-canvas    ├────────────────────────────────────────────┤  5160ms
svc-user        ├───────────────────────────────────────┤      4900ms  ← 几乎吃满
  └ MySQL          ├─────────────────────────────────┤          4850ms  ← 真凶在这
svc-ai                                              ├──┤         120ms
svc-oss                                                ├─┤        90ms

时间轴 →  0ms ················································· 5200ms
```

**怎么读这张图**：每一条横杠是一个 span，杠的长度就是耗时、起止位置就是它在时间轴上的位置。一眼能看出 svc-user（4900ms）几乎吃满了整条链路，而它内部的 MySQL 调用（4850ms）又是 svc-user 慢的元凶。svc-ai、svc-oss 都很快，可以直接排除。这就把"五个服务"一刀缩到"svc-user 的某条 SQL"。

> 💡 **前端类比**：这就是 DevTools Network 面板的瀑布图（Waterfall），或者 React Profiler 的火焰图——哪一段宽，哪一段就是瓶颈。区别只是这里"每一段"是一个跨进程的微服务调用。

落地这套能力，业界有现成的链路追踪系统，你不用自己造轮子：

| 系统 | 一句话特点 |
| --- | --- |
| **SkyWalking** | 国产、对 Java 友好，Agent 字节码增强**无侵入接入**，自带漂亮 UI，国内用得最多 |
| **Zipkin** | Twitter 出品，轻量、上手快，生态成熟 |
| **Jaeger** | CNCF 项目，云原生 K8s 环境的常见选择 |
| **OpenTelemetry (OTel)** | **事实标准的"协议层"**——统一了埋点 SDK 和数据格式，后端可对接上面任意一个，避免被某个系统绑死 |

实践建议：**新项目优先用 OpenTelemetry 做埋点标准**，后端存储/展示用 SkyWalking 或 Jaeger。这样换底层系统时业务代码不用改，类比前端你用标准的 `fetch` 而不是绑死某个请求库。

接入后，最常用的动作就是"拿一个慢请求的 traceId 去链路系统里查"。和 [第 27 章](/back-end/frontend-backend-guide/27-troubleshooting-methodology) 里手动 grep traceId 比，区别是：手动 grep 是降级方案（系统没接链路追踪时用），有了链路系统就是点开一条直接看火焰图，连耗时差值都帮你算好了。

---

## 30.6 告警：让系统主动叫醒你

监控面板再漂亮，没人盯着也白搭。**告警**才是"主动发现"的最后一环——基于指标设阈值，越线就推送到值班群/电话。

告警通常也基于 Prometheus 的查询。在 `alert.rules.yml` 里定义规则：

```yaml
groups:
  - name: svc-canvas-alerts
    rules:
      - alert: HighErrorRate
        expr: |
          sum(rate(http_server_requests_seconds_count{application="svc-canvas",status=~"5.."}[5m]))
            / sum(rate(http_server_requests_seconds_count{application="svc-canvas"}[5m]))
            > 0.05
        for: 2m                       # 持续 2 分钟才告警，避免瞬时抖动误报
        labels:   { severity: critical }
        annotations:
          summary: "svc-canvas 错误率超过 5%（当前 {{ $value | humanizePercentage }}）"

      - alert: FrequentFullGC
        expr: increase(jvm_gc_pause_seconds_count{action=~".*major.*"}[5m]) > 3
        for: 1m
        labels:   { severity: warning }
        annotations:
          summary: "svc-canvas 5 分钟内 Full GC 超过 3 次，疑似内存吃紧"
```

告警的几条原则比规则本身更重要：

```text
好告警 vs 坏告警
─────────────────────────────────────────────────────────
坏：CPU > 50% 就告警                好：CPU 持续 5 分钟 > 90%
   → 每天响几十次，没人看了（狼来了）   → 真到瓶颈才响，响了必处理
坏：单次请求超 1s 就告警            好：P99 持续 2 分钟 > 3s
   → 偶发长尾天天误报                  → 反映整体劣化，值得介入
坏：告警只说"svc-canvas 异常"        好：告警带服务名+指标+当前值+
   → 收到也不知道干嘛                    +排查链接，看一眼就知道下一步
```

最值得配的几条核心告警（覆盖绝大多数线上故障）：

- **错误率 > 5%（持续 2 分钟）** —— 最直接的"出事了"信号。
- **P99 > 阈值（持续 2 分钟）** —— 反映整体性能劣化，比单次慢请求可靠。
- **Full GC 频繁 / 堆使用率 > 90%** —— 内存问题前兆，给你时间在 OOM（[第 20 章](/back-end/frontend-backend-guide/20-oom-memory-leak)）前介入。
- **Pod 重启 / 频繁重启（CrashLoopBackOff）** —— 应用起不来或被 OOMKilled，呼应 [第 24 章 Kubernetes 实战](/back-end/frontend-backend-guide/24-kubernetes-in-practice)。
- **连接池/线程池使用率 > 80%** —— 打满前的预警，留出扩容/排查窗口。
- **健康检查 DOWN / 探针失败** —— 依赖中间件挂了。

**核心原则：告警必须"可执行、不狼来了"**。每条告警响起时，值班人应该清楚"这意味着什么、下一步该做什么"。如果一条告警经常响但每次都是误报，要么调阈值、要么删掉——长期被无意义告警轰炸的团队，最后会对真告警也麻木，那比没有告警还危险。

> 💡 **前端类比**：这就是 Sentry 里设的告警规则——"同一报错 5 分钟内出现 100 次就 @ 我"，而不是每个 console.warn 都打扰你。告警调优的目标和前端一样：**信噪比**，让每次被打扰都值得。

---

## 30.7 把三件套接进运行示例项目

最后给一张全景图，看这套体系在 AI 生图微服务里怎么拼起来：

```text
┌─────────────────────────────────────────────────────────────────┐
│  各服务（cpt-common 统一接入 Actuator + Micrometer + OTel Agent）  │
│  svc-gateway  svc-auth  svc-user  svc-ai  svc-canvas  svc-oss     │
└───────┬───────────────────────┬───────────────────────┬─────────┘
        │ /actuator/prometheus   │ trace 数据(OTLP)        │ 日志(JSON)
        ▼                        ▼                        ▼
   ┌─────────┐            ┌──────────────┐         ┌──────────────┐
   │Prometheus│           │ SkyWalking/  │         │ ELK / Loki    │
   │ (Metrics)│           │ Jaeger(Trace)│         │ (Logging)     │
   └────┬─────┘           └──────┬───────┘         └──────┬───────┘
        │                        │                        │
        ├── Alertmanager ──→ 值班群/电话（告警）           │
        │                        │                        │
        └──────────┬─────────────┴────────────────────────┘
                   ▼
              ┌─────────┐
              │ Grafana │  ← 一个面板看趋势、跳链路、查日志
              └─────────┘
```

三件套靠 **traceId** 这条线缝合在一起：Grafana 看到错误率告警 → 点进去看是哪个接口 → 拿一条 trace 看火焰图定位到服务 → 复制 traceId 跳到日志系统看异常栈。整个排查动线和 30.2 那条漏斗完全一致，只是工具齐活后每一步都从"手动 grep"变成了"点一下"。

这就是从"被动救火"到"主动发现"的全部内涵：故障还没爆，告警先响；告警一响，三件套带着你十分钟从"有事"走到"哪行代码"。

---

## 小结

- 可观测性三件套各管一段：**Metrics（趋势与告警）→ Tracing（定位到哪一跳）→ Logging（看清为什么）**，从粗到细，缺一不可。
- 定位问题的标准漏斗：**监控发现异常 → 链路定位服务 → 日志看细节**，三者靠 traceId 串起来。
- Metrics 落地路线：**Spring Boot Actuator** 暴露 `/actuator/health|metrics|prometheus` → **Micrometer** 打自定义业务指标（Counter/Timer）→ **Prometheus** 抓取 → **Grafana** 看板。
- 关键指标盯这些：QPS、**RT 看 P95/P99 别看平均**、错误率（最该优先告警）、JVM 堆/GC、线程数、连接池使用率、机器 CPU/内存。
- Tracing 用现成系统（SkyWalking/Zipkin/Jaeger，埋点优先 OpenTelemetry），核心直觉是**火焰图——哪段宽哪段就是瓶颈**。
- 告警要**可执行、不狼来了**：设持续时间避免抖动误报、带上下文（服务+指标+当前值），核心配错误率、P99、Full GC、Pod 重启、连接池打满。

### 自测

1. 同样是看系统状态，Metrics、Tracing、Logging 三者分别回答什么问题？为什么不能只靠日志全解决？
2. 为什么延迟监控要看 P99 而不是平均值？平均 80ms 的接口可能藏着什么坑？
3. "CPU 超过 50% 就告警"这条规则为什么是坏告警？请把它改成一条可执行、不误报的好告警，并说明你改了哪几点。

### 下一章

可观测性让你"看得见"系统状态。下一章 [第 31 章 性能与并发优化](/back-end/frontend-backend-guide/31-performance-concurrency) 顺着这些指标往前走——当监控告诉你 P99 高、QPS 上不去时，具体有哪些性能与并发的优化手段。

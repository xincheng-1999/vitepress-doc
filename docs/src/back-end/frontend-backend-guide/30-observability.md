# 可观测三件套

用户说：“任务列表这几分钟特别慢。”你去看日志，没有明显异常；现在再请求一次，又很快了。怎样知道刚才真的慢过、影响多少人，以及时间花在哪里？

这就是这篇要解决的问题。先用指标看到一段时间里的变化，再找一条慢请求的链路，最后回到日志查细节。没有遇到这类需要时可以先跳过，沿 [五阶段主线](./index) 继续，不必为了学接口就先搭监控平台。示例配置、查询和输出均用于讲解，没有在本仓库运行监控系统或验证生产部署。

## 1. 先留下“刚才发生过什么”的趋势

一条成功日志只说明一次请求完成了，不能说明这五分钟的用户体验。要回答“整体变慢了吗”，可以先记录三个方面：有多少请求、用了多久、失败了多少。

这些随时间收集的数字就是 **Metrics（指标）**。例如 `GET /api/tasks` 每秒请求数没有明显增加，但 P99 从 200ms 升到了 5 秒，超时也增加，就说明值得继续查。P99 表示在所选统计范围中，约 99% 的请求耗时不超过这个值；它不是某一个固定用户的耗时，也不是所有请求都慢了 5 秒。

平均值仍然有用，但不能单独看。大量快请求可能掩盖少量很慢的请求；低流量时，分位数也容易波动。比较延迟时，要同时看请求量、时间窗口、错误与超时，不能只截一张 P99 图下结论。

应用指标还要配合运行环境：请求变慢的同一时段，是连接等待增加、线程队列积压，还是 CPU、GC 停顿或磁盘出现变化？先根据同步变化缩小范围，不能看到 CPU 不高就断定扩容一定无效。

## 2. 应用怎样把这些数字提供出来

在 Spring Boot 应用中，常见组合是：**Actuator** 提供管理端点，**Micrometer** 记录指标，**Prometheus** 定期抓取并保存，**Grafana** 查询这些数据、画出趋势。它们各做一件事，不是引入一个依赖就自动得到全部看板和告警。

假如你自己有一个 Spring Boot 3.x 应用，并计划在隔离环境尝试，可以先为这个应用增加依赖。这里没有名为 `cpt-common` 或 `svc-*` 的现成工程需要寻找，具体版本由自己的 Spring Boot 依赖管理确定。

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

管理端点可能暴露系统细节，应放在受控网络并配置适当的认证授权，不向公网开放。下面仅示意本机尝试：管理端口单独绑定到回环地址，应用业务端口仍由自己的配置决定。

```yaml
management:
  server:
    port: 8081
    address: 127.0.0.1
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  endpoint:
    health:
      show-details: never
  metrics:
    tags:
      application: task-api
    distribution:
      percentiles-histogram:
        http.server.requests: true
```

`percentiles-histogram` 用于发布可聚合的耗时桶；桶数越多，存储和处理成本也越高，应只对需要的指标开启。仅配置客户端的 `publishPercentiles`，并不能得到可直接跨实例相加的分位数。

上面的回环绑定不适用于让另一个容器直接抓取；实际部署时，需要按授权的网络路径配置管理地址、防火墙和访问控制。不要为了让 Prometheus 连上，就把全部管理端点无认证地开放。

接入这些配置还依赖具体版本、安全配置和运行方式；配置来源可查 [配置与环境](/back-end/frontend-backend-guide/25-config-and-env)。

## 3. 先确认端点，再让采集器保存趋势

在运行应用的本机，先看管理端点是否提供了预期数据：

```bash
curl --connect-timeout 3 --max-time 5 -i http://127.0.0.1:8081/actuator/health
curl --connect-timeout 3 --max-time 5 -sS http://127.0.0.1:8081/actuator/metrics
curl --connect-timeout 3 --max-time 5 -sS http://127.0.0.1:8081/actuator/prometheus
```

健康响应中若有 `status: UP`，只说明已配置的健康检查在当时通过，不代表任务查询一定快。哪些组件参与检查、是否展示详情，取决于应用配置；RocketMQ 等也不一定自动拥有健康检查。

Kubernetes 的 liveness 和 readiness 不应不加区分地共用一个包含所有下游的整体健康结果。readiness 表示是否适合接流量，liveness 失败则可能触发重启，具体见 [Kubernetes 实战](/back-end/frontend-backend-guide/24-kubernetes-in-practice)。

`metrics` 适合临时查询当前值；`prometheus` 则提供抓取格式。下面是简化示意：

```text
http_server_requests_seconds_count{application="task-api",method="GET",uri="/api/tasks",status="200"} 18342
http_server_requests_seconds_sum{application="task-api",method="GET",uri="/api/tasks",status="200"} 2103.7
http_server_requests_seconds_count{application="task-api",method="GET",uri="/api/tasks",status="500"} 27
```

`count` 是累计次数，`sum` 是累计耗时，单位为秒。这些值通常随进程生命周期增长，重启会重置；不能把当前的 `27` 直接理解为“最近五分钟有 27 次失败”。要看最近一段时间，需要采集器保留多个时刻的值。

::: details 按需看：Prometheus 抓取的最小形状

以下片段只适用于 Prometheus 能从自身网络环境访问目标的情况。若 Prometheus 与应用都是本机进程，并使用前面的端口，抓取配置可以长这样：

```yaml
scrape_configs:
  - job_name: task-api-local
    metrics_path: /actuator/prometheus
    scrape_interval: 15s
    static_configs:
      - targets: ['127.0.0.1:8081']
```

如果 Prometheus 在容器里，`127.0.0.1` 指向的是它自己，不是宿主机应用。多实例环境还应分别发现和抓取各实例；只抓 Service 随机分流到的一个实例，会破坏单实例累计值的连续性。服务发现方式按现有平台选择，不必为学习另搭集群。

先在采集器中确认目标抓取成功，并查询 `up` 和实际指标名。`up=1` 说明这次抓取成功，不代表业务正常；无数据也可能是抓取地址、鉴权或标签不匹配，而不是业务没有请求。
:::

## 4. 回到“列表慢”，先在同一范围查询

假设已经抓到数据，且标签、指标名与下面一致。在 Prometheus 或 Grafana 中，先限定应用、GET 方法与路径，再查询最近五分钟的速率：

```text
sum(rate(http_server_requests_seconds_count{application="task-api",method="GET",uri="/api/tasks"}[5m]))
```

它是该范围的每秒请求数。`rate` 会考虑计数器重置，应先对每个序列求速率，再汇总，不先把不同实例的计数器相加。

对应的 HTTP 5xx 比例可以写成：

```text
sum(rate(http_server_requests_seconds_count{application="task-api",method="GET",uri="/api/tasks",status=~"5.."}[5m]))
/
sum(rate(http_server_requests_seconds_count{application="task-api",method="GET",uri="/api/tasks"}[5m]))
```

分子和分母使用相同应用、方法、路径与窗口。没有 5xx 序列时，分子可能没有结果，而不自动显示 0；没有流量时，比例也没有稳定意义。看板和告警应明确处理这些情况。HTTP 5xx 也不包括所有业务拒绝或客户端超时，必须与接口契约一起看。

若实际暴露了经典直方图的 `_bucket`，P99 查询可以是：

```text
histogram_quantile(
  0.99,
  sum by (le) (
    rate(http_server_requests_seconds_bucket{application="task-api",method="GET",uri="/api/tasks"}[5m])
  )
)
```

这里保留了桶边界标签 `le`，并聚合多个实例。结果是桶估计出的秒数，精度受桶边界和流量影响。想按接口或服务分组，就要在聚合时保留对应标签；不能把所有不同接口混成一个 P99 后，再解释成某个接口的体验。

若没有 `_bucket`，先检查直方图配置、版本和实际暴露格式，不是直接复制查询就会出现结果。相关性能判断见 [性能与并发优化](/back-end/frontend-backend-guide/31-performance-concurrency)。

## 5. 找到一条慢请求，看看它内部发生了什么

指标说明这五分钟的列表请求确实变慢了，接下来需要一条具体请求。这时使用 **Tracing（链路追踪）**：一次请求由一个 trace 表示，其中每一段操作叫 span，记录起止时间、父子关系及相关属性。

假设一条请求的结果如下：

```text
GET /api/tasks                  4840ms
  获取数据库连接                 15ms
  taskRepository.findPage      4800ms
  组装响应                       12ms
```

主要时间落在查询调用，下一步就去核对对应 SQL、数据规模与数据库执行情况。span 可能嵌套或并行，不能把所有耗时直接相加；数据库 span 也可能包含网络等待和结果读取，并不总是纯 SQL 执行时间。

这些 span 需要应用、客户端或 Agent 的埋点与上下文传播，不是只生成一个 traceId 就自动完整可见。异步任务和跨进程消息也需要相应传播；跨机器时间还受时钟同步影响。机制可结合 [请求生命周期](/back-end/frontend-backend-guide/03-request-lifecycle) 理解。

OpenTelemetry 提供采集与传播等标准化能力，Jaeger、Zipkin、SkyWalking 等系统可承担相应的接收、存储或展示工作。具体兼容性取决于版本和接入方式，应优先使用团队已有方案，不必为这个问题同时部署几套系统。

链路还可能被采样，没查到一条慢请求，不代表它没有发生。可以用时间、路径与请求标识回到日志查，但日志同样必须已经记录相关信息。

## 6. 用日志解释这一次查询，而不是重复打印所有数据

复制慢请求的 traceId，在日志平台或应用日志中找到对应记录。**Logging（日志）**补充的是具体事件：查询参数的范围、结果数量、异常类型和业务处理结果，而不是自动给出全部根因。

假设日志显示：列表查询遗漏分页限制，返回了远超正常规模的数据；同一时段的慢查询也能对应。这才把“查询慢”推进到了可以验证的代码或配置问题。接着按 [问题排查方法论](/back-end/frontend-backend-guide/27-troubleshooting-methodology) 在合适环境比较修改前后结果。

日志中记录路径、结果数量和耗时，通常比直接打印完整任务内容更有帮助。不要记录密码、token 或敏感个人信息，也不要把用户输入直接当成没有约束的日志字段。写法与读法见 [看日志](/back-end/frontend-backend-guide/26-reading-logs)。

指标标签也需要约束：适合用路径模板、状态类别等有限取值，不把用户 ID、taskId 或 traceId 当指标标签，否则会产生大量独立序列。具体请求标识应放在日志、trace，或由平台支持的 exemplar 关联中。

## 7. 下一次怎样更早发现

这次是用户先报慢。修复后，可以围绕列表接口的延迟、错误和实际流量配置告警，让告警指向看板与排查入口，而不是只写“应用异常”。

阈值应来自业务要求与正常表现，不存在适合所有服务的“P99 超 3 秒”或“连接池超过 80%”规则。低流量要防比例失真，流量突然消失与采集失败也要另行观察。连接池等待见 [连接池](/back-end/frontend-backend-guide/13-connection-pools)，线程队列见 [线程池与 Executor](/back-end/frontend-backend-guide/15-thread-pools-executor)，GC 趋势见 [垃圾回收](/back-end/frontend-backend-guide/19-garbage-collection) 与 [OOM 排查](/back-end/frontend-backend-guide/20-oom-memory-leak)。

::: details 按需看：一条带流量条件的错误率规则

下面只说明规则结构：假设当前范围有足够流量，希望 HTTP 5xx 比例持续超过 5% 时触发，并至少有每秒 1 次请求。数值、窗口、范围和严重程度都应按自己的服务调整。

```yaml
groups:
  - name: task-api-example
    rules:
      - alert: TaskListHighErrorRate
        expr: |
          (
            (
              sum(rate(http_server_requests_seconds_count{application="task-api",method="GET",uri="/api/tasks",status=~"5.."}[5m]))
              or vector(0)
            )
            /
            sum(rate(http_server_requests_seconds_count{application="task-api",method="GET",uri="/api/tasks"}[5m]))
          ) > 0.05
          and on()
          sum(rate(http_server_requests_seconds_count{application="task-api",method="GET",uri="/api/tasks"}[5m])) > 1
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "task-api 列表接口 HTTP 5xx 比例持续偏高"
          description: "查看同范围请求量、延迟及失败请求，再进入链路与日志检查。"
```

`for: 2m` 表示表达式持续满足两分钟后才触发；`or vector(0)` 处理没有 5xx 序列的情形，不用于掩盖采集系统缺失。Prometheus 还需要加载规则，告警通知通常通过 Alertmanager 等组件路由，规则本身不会自动发到群里。
:::

从这次“刚才很慢，现在又好了”的问题出发，三类数据的位置就清楚了：指标保留趋势，trace 帮你拆一条请求，日志解释具体事件。它们能减少临时猜测，但不保证每个问题都能自动定位到一行代码；先补当前最缺的一段，比一次铺满所有工具更有用。

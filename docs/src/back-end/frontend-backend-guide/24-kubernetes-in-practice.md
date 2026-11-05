# Kubernetes 实战

同事发布了一个新版本，页面却开始出现 503。你打开平台，看到“Pod 在运行”，但请求还是进不去。接下来该看哪里？

这篇就沿着这个问题，认识 Kubernetes 中与一次发布有关的对象，再看怎样查状态、读事件和找启动失败的原因。没有使用 Kubernetes 时可以先跳过，沿 [五阶段主线](./index) 继续；需要了解运行配置时，可看 [配置与环境](/back-end/frontend-backend-guide/25-config-and-env)。这里不要求搭集群，也没有实际执行 Kubernetes 或生产部署；命令与输出都是说明用的示例。

## 1. 先弄清：这次发布替换的是什么

在 [Docker 实战](/back-end/frontend-backend-guide/23-docker-in-practice) 中，一个镜像可以启动成容器。应用有多个副本时，还需要有人决定副本运行在哪台机器、退出后怎样补起、更新时怎样替换。Kubernetes（简称 K8s）负责协调这些事情。

假设自己的任务应用叫 `task-api`，原来运行三个副本。平台里常见的 **Deployment**，就是描述“使用哪个镜像、期望几个副本”的对象。它通过 ReplicaSet 管理 **Pod**；Pod 是调度的基本单位，里面运行一个或多个紧密协作的容器。

这次发布通常是修改 Deployment 的 Pod 模板，让它使用新镜像。控制器随后逐步创建新版 Pod、缩减旧版副本，而不是进入旧容器手工替换文件。

这里要区分两种情况：容器退出后，kubelet 可以在原 Pod 中重启容器；Pod 被删除或需要替换时，则会创建新的 Pod。新的 Pod 名称与 IP 都可能不同，所以调用方不应长期绑定某个 Pod IP。

## 2. 请求怎样找到这些副本

用户只访问一个接口地址，背后的 Pod 却在替换。中间需要一个稳定入口，这就是 **Service** 的用途。它通常按标签选择一组 Pod，并通过 EndpointSlice 等机制维护可用后端。

例如 Service 选择 `app: task-api`，Deployment 创建的 Pod 也带这个标签，就能关联起来。通常只有就绪的后端才接收新流量，但配置和终止过程会影响具体行为；不能仅凭“有 Service”就保证请求不会失败。

```text
用户请求
  → 对外入口（例如 Ingress Controller）
  → Service（稳定服务入口）
  → 就绪的 task-api Pod
       └ 应用容器

Deployment → ReplicaSet → 管理 task-api 的 Pod 副本与更新
```

**Ingress** 描述域名和路径怎样转到 Service，真正执行转发还需要 Ingress Controller；只创建一份 Ingress 对象不够。也不是所有团队都使用 Ingress，可能由云负载均衡、Gateway API 或其他平台入口承担。

若应用还有业务网关，请求可能先到网关的 Service，再由网关转到任务应用。Ingress 与业务网关不是同一个东西，是否都需要取决于系统设计，可结合 [架构总览](/back-end/frontend-backend-guide/02-architecture-overview) 理解。

## 3. 先确认看的是哪个环境，再查 Pod

现在回到“发布后 503”。先在已获授权的终端确认集群，随后显式指定命名空间：

```bash
kubectl config current-context
kubectl get pods -n demo -o wide
kubectl get deployment task-api -n demo
```

`demo` 和 `task-api` 是示意名称，必须替换成实际环境。Namespace 可以把对象按环境或团队分组，但它本身不等于完整的安全或网络隔离；实际边界还依赖 RBAC、网络策略等配置。不要看到命名空间叫 `test` 就认为可以随意操作。

假设看到：

```text
NAME                         READY   STATUS    RESTARTS   AGE
task-api-old-example         1/1     Running   0          2d
task-api-new-example         0/1     Running   0          45s
```

`Running` 表示 Pod 已进入运行阶段，不等于应用健康。新版 `READY 0/1` 表示一个容器尚未就绪，应该继续看它的事件与探针结果。旧副本还在，也不表示容量一定够；还要看实际可用副本、流量和更新策略。

如果 Deployment 的 `READY` 是 `2/3`，表示期望三个副本、当前两个就绪。`UP-TO-DATE` 则是匹配最新 Pod 模板的副本数，它与就绪数量不是同一概念。

## 4. 为什么在运行，却还不能接请求

K8s 可以定期请求应用的检查接口，这些检查称为探针。与当前问题最相关的是 **readinessProbe（就绪探针）**：它回答“现在能不能接流量”。失败时，Pod 通常不会作为 Service 的就绪后端接收新流量，**但不会因此被重启**。

另一种是 **livenessProbe（存活探针）**，它回答“容器是否已坏到需要重启”。连续失败达到阈值后，kubelet 会终止并按重启策略处理容器。慢启动应用还可以设置 **startupProbe**，在启动检查成功之前，暂缓 liveness 和 readiness 检查。

先读新版 Pod 的详情：

```bash
kubectl describe pod task-api-new-example -n demo
```

假设 Events 中出现：

```text
Warning  Unhealthy  Readiness probe failed: HTTP probe failed with statuscode: 503
```

这说明探针请求没有得到预期的成功结果。接下来查实际探针路径、端口和应用日志：是应用还在初始化，还是路径写错、关键依赖不可用？不要直接把超时时间调大，也不要把 readiness 失败当成容器重启原因。

如果失败的是 liveness，先判断应用是否真的卡住。把共享数据库故障直接算成 liveness 失败，可能让所有实例一起重启，反而扩大影响。Spring Boot 的健康分组和端点是否存在，也取决于依赖、版本与配置，不能只把下面的路径复制过去。

::: details 按需看：Deployment 和 Service 中，这些字段怎样对应

下面是结构示例，不是本仓库现成部署文件。镜像地址是占位地址；实际应用必须自己提供镜像、HTTP 端口和已启用的健康端点。资源值和探针时间也需要结合启动耗时、负载与容量测试确定，不能直接当生产参数。

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: task-api
  namespace: demo
spec:
  replicas: 3
  selector:
    matchLabels:
      app: task-api
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
  template:
    metadata:
      labels:
        app: task-api
    spec:
      containers:
        - name: app
          image: registry.example.com/demo/task-api:example-version
          ports:
            - containerPort: 8080
          resources:
            requests:
              cpu: "250m"
              memory: "512Mi"
            limits:
              cpu: "1000m"
              memory: "1Gi"
          startupProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            periodSeconds: 5
            failureThreshold: 30
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            periodSeconds: 10
            timeoutSeconds: 2
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            periodSeconds: 5
            timeoutSeconds: 2
---
apiVersion: v1
kind: Service
metadata:
  name: task-api
  namespace: demo
spec:
  selector:
    app: task-api
  ports:
    - port: 8080
      targetPort: 8080
  type: ClusterIP
```

先看 `app: task-api`：Deployment 的选择器、Pod 标签和 Service 选择器对应起来。再看端口：`port` 是 Service 的入口，`targetPort` 是后端目标端口；`containerPort` 本身不会让应用开始监听，也不会自动向外开放端口。

`maxSurge: 1` 允许更新时临时多一个副本，`maxUnavailable: 0` 要求控制器更新时维持期望的可用数量。它们约束更新节奏，不保证节点故障、探针误判、容量不足或请求终止时绝不掉线。

ConfigMap 用来保存普通配置，Secret 用来承载敏感配置，Deployment 可以通过环境变量或挂载文件使用它们。Secret 的 base64 编码不是加密，仍需访问控制、加密存储等保护；注入环境变量的值也不会因为对象更新就自动变更到已有进程。用法见 [配置与环境](/back-end/frontend-backend-guide/25-config-and-env)。
:::

## 5. 如果容器退出了，接着看上一次日志

假如新版不只是未就绪，`RESTARTS` 还在增加，继续读 `describe` 中的 `Last State`、退出码和 Events，然后看上一容器实例的日志：

```bash
kubectl logs task-api-new-example -n demo -c app --previous --tail=100
```

`-c app` 指定容器。`--previous` 只针对同一个 Pod 内该容器的上一实例，不是任意历史版本，也不能保证找回已经删除的 Pod 日志。

假设日志写着：

```text
Application run failed
Unable to connect to Redis
Connection refused: redis.internal.example:6379
```

现在能确认应用启动期间连接 Redis 被拒绝。下一步核对它实际使用的地址和端口、目标监听与网络路径；这不是“密码一定错了”的证据，更不能直接宣布“与代码无关”。配置改错、依赖没启动、客户端初始化方式变化都可能参与其中。

进入容器也不是必需步骤。现有日志足够时先用日志；确实需要检查某项非敏感配置、且有权限时，再执行具体命令：

```bash
kubectl exec task-api-new-example -n demo -c app -- printenv SPRING_PROFILES_ACTIVE
```

不要直接输出全部 `env`，里面可能有密钥。镜像也未必包含 shell 或 JDK，不能因为工具不存在就判断应用坏了。其他进程检查见 [诊断工具箱](/back-end/frontend-backend-guide/29-diagnostic-toolbox)，请求线程与日志关系见 [请求生命周期](/back-end/frontend-backend-guide/03-request-lifecycle)。

## 6. 结果不是启动报错时，沿对应方向查

同一次发布也可能停在拉镜像阶段，或在运行后触发内存终止。先根据已经看到的状态分流，不必每种工具都跑一遍。

| 已看到的结果 | 接下来检查 | 怎样解释 |
| --- | --- | --- |
| `ImagePullBackOff` / `ErrImagePull` | `describe` 的 Events 中的镜像地址与错误 | `not found` 查名称/tag；`unauthorized` 查拉取授权；超时查仓库可达性 |
| `CrashLoopBackOff` | Last State、上一实例日志、探针事件 | 它表示重启退避，不是具体根因 |
| `OOMKilled` | 容器/节点内存记录、资源限制、应用趋势 | 有 OOM 终止记录，仍需分清堆、堆外和节点压力 |
| `READY 0/1`，无重启 | readiness 结果、端点与应用初始化 | 未就绪不等于进程退出 |
| Pod 已就绪，入口仍 503 | Service 选择器、EndpointSlice、入口路由 | 应用可用与入口配置正确是两件事 |

例如查看 Service 关联到哪些后端：

```bash
kubectl get service task-api -n demo
kubectl get endpointslices -n demo -l kubernetes.io/service-name=task-api
```

没有预期后端时，核对标签和就绪情况；有后端仍失败，则沿端口、路由和实际错误继续查，而不是先删 Pod 重建。

需要看资源快照时，可在集群提供指标服务的前提下使用：

```bash
kubectl top pod -n demo
```

`1000m` 是一核 CPU，内存列是采样值，不是 JVM 堆使用量，也不是历史曲线。CPU 配额可能导致节流；内存限制与节点压力则可能触发 OOM。退出码 137 只表示 SIGKILL，不能单凭这个码认定内存超限。

如果内存逐渐上涨，要看历史趋势和对象持有情况，不能凭一次 `top` 区分“上限小了”与“泄漏”。分析见 [JVM 内存模型](/back-end/frontend-backend-guide/18-jvm-memory-model) 和 [OOM 与内存泄漏](/back-end/frontend-backend-guide/20-oom-memory-leak)。

## 7. 查清原因以后，再决定怎样恢复

如果新版配置有误，可以按团队流程修复后重新发布；如果影响正在扩大，则由负责人决定暂停、回滚或切换流量。修改镜像、`apply`、回滚、扩缩容和重启都会改变集群状态，不是查询命令，也不应在不熟悉的环境中试着执行。

回滚前需要确认旧镜像仍可取得，旧版本与当前配置、数据库结构兼容。Deployment 回滚主要恢复 Pod 模板，不会自动撤销数据库迁移或外部配置变化，也不保证一分钟内恢复。

已获授权的发布完成后，可以用只读方式等候更新结果：

```bash
kubectl rollout status deployment/task-api -n demo --timeout=120s
```

看到 `successfully rolled out`，说明 Deployment 更新达到了对应条件；还要确认真实接口的状态、内容、延迟和错误率恢复。readiness 只能反映应用选择暴露的检查结果，不能替代业务验证。

这样，“Pod 明明在运行，为什么还是 503”就有了清楚的检查路线：先看环境与副本，再看就绪、事件和日志，必要时检查 Service 与入口。具体配置怎样注入应用，接着看 [配置与环境](/back-end/frontend-backend-guide/25-config-and-env)。

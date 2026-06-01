# Kubernetes 实战

> 上一章 [Docker 实战](/back-end/frontend-backend-guide/23-docker-in-practice) 把单个服务打成了镜像、能在一台机器上 `docker run` 起来了。但线上有 6 个服务（svc-gateway / svc-auth / svc-user / svc-ai / svc-canvas / svc-oss），每个还要多副本、要自愈、要灰度发布——靠人手敲 `docker run` 根本管不过来。这就是 Kubernetes（简称 K8s）要解决的事。
>
> 这一章的定位很明确：**不是让你成为运维**，而是让你这个前端转过来的后端，能在 K8s 上**看日志、查状态、发布、回滚、排查 Pod 挂掉**。这是线上值班、排障时每天都要用的硬技能。实操优先，能复制就能用。

---

## 为什么单机 Docker 不够：从"养宠物"到"养牲口"

先回答动机问题。你已经会用 Docker 把 svc-user 打成镜像、`docker run` 起来了。那为什么还要学一套这么重的东西？

设想线上真实诉求：

- **自愈**：svc-canvas 半夜 OOM 挂了，没人值班，你希望它**自动被拉起来**，而不是等到早上用户投诉。
- **扩缩容**：搞活动时生图请求暴涨，svc-ai 要从 2 个副本临时扩到 10 个；活动结束再缩回去。
- **滚动发布**：svc-user 发新版本，要**逐个**替换旧实例、且全程不掉线，新版有问题能一键回退。
- **负载均衡**：svc-ai 有 5 个副本，svc-canvas 调它时不该关心调到哪个实例，要有个稳定入口自动分流。
- **配置管理**：数据库密码、OSS 密钥这些不该写死在镜像里，要能集中管理、按环境替换。

这些事 `docker run` 都做不了，或者得你自己写一堆脚本。K8s 就是把这些能力打包好的**容器编排（container orchestration）平台**——你只需要**声明"我想要什么状态"**（比如"svc-ai 我要 3 个副本"），K8s 自己去**让现实逼近这个状态**（少了就拉起、多了就杀掉、挂了就重建）。

> **前端类比**：这正是 React 的思想。你不会手动操作 DOM 说"在这里插一个 div"，你只是 `setState` 声明"我想要的 UI 长这样"，React 的 diff/reconcile 负责把真实 DOM 改成你声明的样子。K8s 就是基础设施层的 React：你写 YAML 声明期望状态（desired state），K8s 的控制器（controller）不停地对比"现状 vs 期望"，自动 reconcile 到你声明的样子。这套"声明式 + 持续调和"的心智模型记住了，K8s 就理解了一半。

一句话定位：**K8s = 容器编排，帮你管理一大堆容器的部署、自愈、扩缩容和发布。**

---

## 核心对象逐个认：从 Pod 到 Namespace

K8s 里一切皆"对象（object）"，每个对象就是一段 YAML 声明。下面把你**日常一定会碰到**的几个对象逐个过一遍，每个给一句话定位 + 最小 YAML 片段。先看这张关系图，建立整体感：

```text
                      ┌─────────────────────────────────────────┐
   外部用户 ───────►  │ Ingress  （对外 HTTP 路由，呼应 svc-gateway）│
                      └───────────────────┬─────────────────────┘
                                          │ 按域名/路径转发
                                          ▼
                      ┌─────────────────────────────────────────┐
                      │ Service  （稳定内部入口 + 负载均衡）        │
                      │   svc-ai  ClusterIP: 10.96.x.x:8080       │
                      └───────────────────┬─────────────────────┘
                          按 label 选中并分流到这一组 Pod
                ┌─────────────────────────┼─────────────────────┐
                ▼                         ▼                     ▼
        ┌──────────────┐         ┌──────────────┐       ┌──────────────┐
        │ Pod          │         │ Pod          │       │ Pod          │
        │ svc-ai-xxx-1 │         │ svc-ai-xxx-2 │       │ svc-ai-xxx-3 │
        │  └ container │         │  └ container │       │  └ container │
        └──────────────┘         └──────────────┘       └──────────────┘
                ▲                         ▲                     ▲
                └─────────────────────────┴─────────────────────┘
                       Deployment （管理这 3 个副本 + 发布/回滚）
                              读取 ConfigMap / Secret 注入配置

  以上所有对象都活在某个 Namespace 里（如 ai-image-prod）
```

### Pod —— 最小调度单位

**一句话**：Pod 是 K8s 能调度的最小单位，里面跑一个（偶尔多个）容器，同一个 Pod 内的容器共享网络和存储。你**几乎不直接创建 Pod**，而是让 Deployment 帮你管。

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: svc-ai-debug          # 一般只在临时调试时手动建单个 Pod
spec:
  containers:
    - name: svc-ai
      image: registry.example.com/ai-image/svc-ai:1.4.2
      ports:
        - containerPort: 8080
```

> **前端类比**：一个 Pod 像一个"组件实例"，是被渲染（调度）的最小单元。Pod 里多个容器共享网络，就像一个组件里多个紧密协作、共享同一份 props 的子元素。多容器场景的典型是 **sidecar**（边车），比如主容器旁挂一个日志采集容器，类似前端给组件包一层 HOC 做横切逻辑。

> ⚠️ Pod 是"用完即弃"的——挂了不会原地复活，而是被**换一个新的**（名字、IP 都会变）。所以你永远不该让别的服务直连某个 Pod 的 IP，这正是下面 Service 存在的理由。

### Deployment —— 管理副本与发布

**一句话**：Deployment 声明"我要这个镜像跑几个副本"，并负责滚动发布和回滚。这是你**最常打交道**的对象。

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: svc-ai
spec:
  replicas: 3                  # 我想要 3 个副本，挂了 K8s 自动补
  selector:
    matchLabels:
      app: svc-ai
  template:                    # 这一坨就是"每个 Pod 长什么样"的模板
    metadata:
      labels:
        app: svc-ai
    spec:
      containers:
        - name: svc-ai
          image: registry.example.com/ai-image/svc-ai:1.4.2
```

> **前端类比**：`replicas: 3` 就像 `Array(3).fill(<SvcAi/>)`——你声明要 3 个相同实例，渲染层负责保证页面上始终有 3 个。某个被销毁了，框架自动补一个新的。

### Service —— 一组 Pod 的稳定入口 + 负载均衡

**一句话**：Pod 的 IP 随生随灭、不可靠；Service 给一组 Pod 一个**固定的内部地址和 DNS 名**，并自动把流量负载均衡到这组 Pod。

```yaml
apiVersion: v1
kind: Service
metadata:
  name: svc-ai                 # 集群内可用 http://svc-ai:8080 直接访问
spec:
  selector:
    app: svc-ai                # 靠 label 选中上面 Deployment 管的那组 Pod
  ports:
    - port: 8080               # Service 暴露的端口
      targetPort: 8080         # 转发到容器的端口
  type: ClusterIP              # 默认类型：只在集群内部可访问
```

有了它，svc-canvas 里 Feign 调 svc-ai 时不用关心对方有几个副本、IP 是多少，直接请求 `http://svc-ai:8080` 即可，Service 自动分流。

> **前端类比**：Service 就是后端版的 **nginx 反向代理 / API 网关里的 upstream**。你前端只认 `https://api.xxx.com`，背后有几台机器、哪台被换掉了你都不知道——Service 在集群内部干的就是这件事。

### Ingress —— 对外 HTTP 路由（呼应网关）

**一句话**：Service 默认只在集群内可访问；Ingress 负责把**集群外**的 HTTP/HTTPS 请求按域名/路径路由到对应的 Service。

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ai-image-ingress
spec:
  rules:
    - host: api.ai-image.com
      http:
        paths:
          - path: /            # 所有外部流量先进网关
            pathType: Prefix
            backend:
              service:
                name: svc-gateway
                port:
                  number: 8080
```

注意我们这套架构里，对外只暴露 svc-gateway。所以 Ingress 通常只把流量打到 svc-gateway，再由我们自己的 [网关](/back-end/frontend-backend-guide/02-architecture-overview) 做鉴权、限流和业务路由。Ingress 和 svc-gateway 是两层路由：Ingress 解决"从集群外进来"，svc-gateway 解决"进来之后怎么分发到各业务服务"。

### ConfigMap —— 普通配置

**一句话**：把配置（非敏感）从镜像里抽出来，集中管理、按环境替换。

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: svc-ai-config
data:
  SPRING_PROFILES_ACTIVE: "prod"
  AI_TASK_THREAD_POOL_SIZE: "16"
```

### Secret —— 敏感配置

**一句话**：和 ConfigMap 用法几乎一样，但用来存密码、密钥、token 等敏感信息（值默认 base64 编码，并配合权限控制）。

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: svc-ai-secret
type: Opaque
data:
  # 注意：data 里的值必须是 base64 编码，下面是 "redis-pwd-123" 编码后的样子
  REDIS_PASSWORD: cmVkaXMtcHdkLTEyMw==
```

> **前端类比**：ConfigMap / Secret 就是后端版的 `.env` 文件——`.env` 放公开配置，`.env.local`（不进 git）放密钥。ConfigMap 对应前者，Secret 对应后者。配置怎么注入到容器、十二要素应用那套，专门在 [配置与环境](/back-end/frontend-backend-guide/25-config-and-env) 里讲。

### Namespace —— 逻辑隔离

**一句话**：把对象分组隔离，常用来区分环境（dev / test / prod）或团队。不写就进 `default`。

```bash
# 创建一个生产环境命名空间
kubectl create namespace ai-image-prod
```

后面所有命令带 `-n ai-image-prod` 就只看这个命名空间的对象。

> **前端类比**：Namespace 像 monorepo 里的不同 package，或 CSS 的作用域——同名对象在不同 Namespace 互不打架，svc-ai 在 dev 和 prod 各有一套，互不影响。

---

## 一份完整的 Deployment + Service 示例

把上面散的片段拼成一份能直接 `kubectl apply` 的完整文件（以 svc-ai 为例）。生产里你每天看的就是这种文件。重点字段我都加了注释：

```yaml
# svc-ai.yaml —— 一个文件用 --- 分隔多个对象
apiVersion: apps/v1
kind: Deployment
metadata:
  name: svc-ai
  namespace: ai-image-prod
  labels:
    app: svc-ai
spec:
  replicas: 3                          # 期望副本数
  selector:
    matchLabels:
      app: svc-ai
  strategy:
    type: RollingUpdate                # 滚动更新（默认），不停机发布
    rollingUpdate:
      maxUnavailable: 0                # 发布期间最多允许几个不可用：0 = 一直保持 3 个能服务
      maxSurge: 1                      # 发布期间最多临时多起几个：可短暂到 4 个
  template:
    metadata:
      labels:
        app: svc-ai                    # 必须和 selector 对得上，Service 也靠它选 Pod
    spec:
      containers:
        - name: svc-ai
          image: registry.example.com/ai-image/svc-ai:1.4.2
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: svc-ai-config    # 注入普通配置
            - secretRef:
                name: svc-ai-secret    # 注入敏感配置
          resources:                   # 资源约束，下文详解
            requests:
              cpu: "250m"              # 调度时至少要这么多（0.25 核）
              memory: "512Mi"
            limits:
              cpu: "1000m"             # 上限 1 核
              memory: "1Gi"            # 超过 1Gi 内存会被 OOMKilled！
          livenessProbe:               # 存活探针：挂了就重启
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 30    # 给 JVM 留 30 秒启动时间，别太早探
            periodSeconds: 10
          readinessProbe:              # 就绪探针：没就绪不接流量
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 20
            periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: svc-ai
  namespace: ai-image-prod
spec:
  selector:
    app: svc-ai                        # 选中上面那组 Pod
  ports:
    - port: 8080
      targetPort: 8080
  type: ClusterIP
```

部署它：

```bash
kubectl apply -f svc-ai.yaml -n ai-image-prod
```

预期输出：

```text
deployment.apps/svc-ai created
service/svc-ai created
```

怎么读：`apply` 是声明式的——文件里声明的对象不存在就 `created`，已存在且有变化就 `configured`，没变化就 `unchanged`。这正是上面说的"声明期望状态"，你只管交文件，K8s 负责把现实改成这样。

---

## kubectl 必会命令（值班就靠这些）

`kubectl`（读作 "cube control" 或 "cube cuttle"）是你和集群对话的唯一工具。下面这些是**前端转后端值班时高频到肌肉记忆**的命令，每条都给真实输出样例和怎么读。

> 小技巧：把 `kubectl` 设个别名 `alias k=kubectl`，再 `export KUBE_NAMESPACE` 或每条命令带 `-n` 指定命名空间。下面示例统一在 `ai-image-prod` 下操作。

### 查状态：get

```bash
# 看所有 Pod，-o wide 多显示 IP 和所在节点
kubectl get pods -n ai-image-prod -o wide
```

预期输出：

```text
NAME                       READY   STATUS    RESTARTS   AGE     IP            NODE
svc-ai-7d9c8f6b5-2xk4p     1/1     Running   0          3h12m   10.244.1.23   node-2
svc-ai-7d9c8f6b5-8wq7m     1/1     Running   0          3h12m   10.244.2.11   node-3
svc-ai-7d9c8f6b5-pl5nz     1/1     Running   2          3h12m   10.244.1.30   node-2
svc-canvas-5f6b9c4d7-jr8tq 1/1     Running   0          5h01m   10.244.2.18   node-3
svc-user-66c7d5f8b-xm2v4   0/1     Running   0          45s     10.244.1.41   node-2
```

怎么读这段输出（最常看的几列）：

- `READY`：`1/1` 表示容器全就绪（就绪探针通过、能接流量）；`0/1` 表示还没就绪——上面 svc-user 刚起 45 秒，正在启动。
- `STATUS`：`Running` 是健康。后面会专门讲 `CrashLoopBackOff` / `ImagePullBackOff` / `OOMKilled` 这几个出问题的状态。
- `RESTARTS`：重启次数。svc-ai 那个 `pl5nz` 重启了 2 次，是个值得留意的信号——可能崩过、或被 liveness 探针重启过。
- `AGE`：Pod 活了多久。

```bash
# 看 Deployment：期望几个、就绪几个
kubectl get deploy -n ai-image-prod
```

```text
NAME         READY   UP-TO-DATE   AVAILABLE   AGE
svc-ai       3/3     3            3           7d
svc-canvas   3/3     3            3           7d
svc-user     2/3     2            3           7d
```

怎么读：`READY 2/3` 表示期望 3 个、只有 2 个就绪——svc-user 有个副本没起来，要去查。`UP-TO-DATE` 是已经更新到最新模板的副本数（发布时会看它逐渐爬到 3）。

```bash
# 看 Service：内部 IP 和端口
kubectl get svc -n ai-image-prod
```

```text
NAME         TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)    AGE
svc-ai       ClusterIP   10.96.142.10    <none>        8080/TCP   7d
svc-gateway  ClusterIP   10.96.30.5      <none>        8080/TCP   7d
```

怎么读：`CLUSTER-IP` 是集群内访问这组 Pod 的稳定 IP；`EXTERNAL-IP <none>` 表示不对外暴露（对外靠 Ingress）。

### 查事件、定位问题：describe

`describe` 是排查的第一把武器，重点看底部的 **Events**——K8s 把"发生了什么"都记在这里。

```bash
kubectl describe pod svc-user-66c7d5f8b-xm2v4 -n ai-image-prod
```

预期输出（只看关键部分）：

```text
Name:         svc-user-66c7d5f8b-xm2v4
Status:       Running
Containers:
  svc-user:
    State:          Running
    Last State:     Terminated
      Reason:       Error
      Exit Code:    1
      Started:      Mon, 01 Jun 2026 14:20:01
      Finished:     Mon, 01 Jun 2026 14:20:43
    Restart Count:  3
Events:
  Type     Reason     Age                From     Message
  ----     ------     ----               ----     -------
  Normal   Scheduled  2m                 default  Successfully assigned svc-user-... to node-2
  Normal   Pulled     2m                 kubelet  Container image already present on machine
  Warning  Unhealthy  90s (x4 over 2m)   kubelet  Readiness probe failed: HTTP probe failed with statuscode: 503
  Warning  BackOff    30s (x3 over 90s)  kubelet  Back-off restarting failed container
```

怎么读这段 Events（从下往上是越来越近的事件）：

- `Readiness probe failed ... statuscode: 503`：就绪探针打 `/actuator/health/readiness` 拿到 503——容器起来了，但应用还没准备好接流量（很可能在等数据库连接、或某个依赖没连上）。
- `Last State: Terminated / Exit Code: 1`：上一次容器以退出码 1 结束（应用自己异常退出）。退出码很重要：`137` 是被 SIGKILL（常见于 OOMKilled），`143` 是被 SIGTERM 优雅停掉，`1` 是应用主动报错退出。
- `Back-off restarting failed container`：K8s 在按退避策略反复重启它。

结论：这是个应用层启动失败问题，下一步去看日志（`logs --previous`）找它为什么退出码 1。

### 看日志：logs（最高频）

```bash
# 实时跟随日志，-f 等同 tail -f
kubectl logs -f svc-ai-7d9c8f6b5-2xk4p -n ai-image-prod
```

```text
2026-06-01 14:31:02.118  INFO 1 --- [nio-8080-exec-3] c.e.ai.consumer.ImageGenerateConsumer    : 拉取到生图任务 taskId=T20260601A993
2026-06-01 14:31:05.402  INFO 1 --- [pool-ai-sd-2]    c.e.ai.strategy.SdGenerateStrategy       : 调用 SD 模型耗时=3210ms taskId=T20260601A993
2026-06-01 14:31:05.418  INFO 1 --- [pool-ai-sd-2]    c.e.ai.consumer.ImageGenerateConsumer    : 生图完成，回发结果 taskId=T20260601A993
```

怎么读：注意进程号是 `1`——容器里 Java 进程通常是 1 号进程。日志格式和你本地跑 Spring Boot 一模一样，只是现在通过 K8s 收口。线程名 `nio-8080-exec-3`（Tomcat 请求线程）vs `pool-ai-sd-2`（自定义业务线程池）的区别，呼应 [一个请求的完整链路](/back-end/frontend-backend-guide/03-request-lifecycle) 里的线程模型。

```bash
# 看崩溃前那个容器的日志（排查 CrashLoopBackOff 的关键！）
kubectl logs --previous svc-user-66c7d5f8b-xm2v4 -n ai-image-prod
```

`--previous`（简写 `-p`）看的是**上一次挂掉的那个容器实例**的日志。这极其重要：Pod 一旦重启，当前容器的日志是新启动的、看不到崩溃原因；崩溃的真相在上一个容器里。

```text
2026-06-01 14:20:43.201 ERROR 1 --- [main] o.s.boot.SpringApplication : Application run failed
org.springframework.beans.factory.BeanCreationException: Error creating bean 'redisConfig':
  ... Unable to connect to Redis; nested exception is RedisConnectionException:
  Connection refused: redis-master/10.96.88.7:6379
```

怎么读：启动时连不上 Redis，Spring 上下文创建失败，进程退出码 1——和上面 `describe` 看到的对上了。结论：不是代码问题，是 Redis 地址/网络/Secret 里的密码配错了。

### 进容器、临时操作：exec

```bash
# 进到容器里开个 shell（前提：镜像里有 bash；很多精简镜像只有 sh）
kubectl exec -it svc-ai-7d9c8f6b5-2xk4p -n ai-image-prod -- bash
```

进去后就像 ssh 到一台机器，可以 `curl localhost:8080/actuator/health` 自测、`jstack 1` 看线程栈、`env | grep REDIS` 确认配置注入对不对。`-it` 是交互式（interactive + tty），`--` 后面是要在容器里执行的命令。

```bash
# 不进 shell，直接执行单条命令也行
kubectl exec svc-ai-7d9c8f6b5-2xk4p -n ai-image-prod -- env | grep SPRING_PROFILES
```

```text
SPRING_PROFILES_ACTIVE=prod
```

> **前端类比**：`kubectl exec -it ... -- bash` 就像在 Docker 里 `docker exec -it`，更像你 ssh 进服务器后想干啥干啥——只不过对象是一个临时的、随时可能被替换的 Pod。容器里能用哪些诊断命令，见 [诊断工具箱](/back-end/frontend-backend-guide/29-diagnostic-toolbox)。

### 看资源占用：top

```bash
# 看各 Pod 实时 CPU / 内存（需要集群装了 metrics-server）
kubectl top pod -n ai-image-prod
```

```text
NAME                       CPU(cores)   MEMORY(bytes)
svc-ai-7d9c8f6b5-2xk4p     320m         640Mi
svc-ai-7d9c8f6b5-pl5nz     180m         512Mi
svc-canvas-5f6b9c4d7-jr8tq 90m          870Mi
```

怎么读：`MEMORY 870Mi` 的 svc-canvas，如果它 `limits.memory` 是 1Gi，就已经吃到 85% 了——离 OOMKilled 不远，要么调大 limit，要么查内存泄漏（见 [OOM 与内存泄漏](/back-end/frontend-backend-guide/20-oom-memory-leak)）。`m` 是毫核，`1000m = 1 核`。

### 看全局事件：get events

```bash
# 按时间排序看命名空间内的所有事件，排查"刚刚发生了什么"
kubectl get events -n ai-image-prod --sort-by=.lastTimestamp
```

```text
LAST SEEN   TYPE      REASON      OBJECT                          MESSAGE
2m          Warning   BackOff     pod/svc-user-66c7d5f8b-xm2v4    Back-off restarting failed container
90s         Warning   Unhealthy   pod/svc-user-66c7d5f8b-xm2v4    Readiness probe failed: statuscode 503
30s         Normal    Killing     pod/svc-canvas-5f6b9c4d7-aa11   Stopping container svc-canvas
```

怎么读：这是集群层面的"日志流"，发布异常、探针失败、Pod 被杀都会在这。线上一接到告警，先 `get events` 看大盘，再 `describe` / `logs` 下钻——这是排障的标准下钻路径。

---

## 发布与回滚：滚动更新的原理

这是你做后端后最常参与的动作之一。理解它的原理能让你看懂"为什么发布过程中没掉线"。

### 滚动更新原理：逐个替换 + 就绪探针保证不掉线

回看上面 YAML 里的 `strategy: RollingUpdate` 配 `maxUnavailable: 0, maxSurge: 1`。它的含义用图说清楚（svc-ai 从 v1.4.2 升到 v1.4.3，3 个副本）：

```text
初始:   [v1.4.2] [v1.4.2] [v1.4.2]            3 个旧版全在服务

第1步:  [v1.4.2] [v1.4.2] [v1.4.2] [v1.4.3起]  maxSurge=1，先多起一个新版
第2步:  [v1.4.2] [v1.4.2] [v1.4.2] [v1.4.3✓]   等新版 readiness 通过（能接流量）
第3步:  [v1.4.2] [v1.4.2] [   X   ] [v1.4.3✓]   新版就绪后，才删掉一个旧版
        ... 如此逐个替换，全程始终有 ≥3 个能服务的副本 ...
完成:   [v1.4.3] [v1.4.3] [v1.4.3]            全部换新，期间用户无感
```

关键在 **就绪探针（readinessProbe）**：新版 Pod 起来后，**必须等就绪探针通过**，Service 才把流量分给它，K8s 才敢删旧的。这就是"不掉线"的保证。如果新版起不来或就绪探针一直不过，发布会卡住（旧版不删），用户照常被旧版服务——这恰恰是我们想要的安全行为。

> **前端类比**：像前端灰度发布——新版本先放出一小撮，确认没报错（健康检查通过）再逐步放量，有问题立刻停。`maxUnavailable: 0` 就是"任何时刻都保证有足够实例对外服务"。

### 触发发布：set image 或改 YAML

两种等价做法：

```bash
# 做法一：直接改镜像 tag（快，适合紧急发布）
kubectl set image deployment/svc-ai svc-ai=registry.example.com/ai-image/svc-ai:1.4.3 -n ai-image-prod
```

```text
deployment.apps/svc-ai image updated
```

```bash
# 做法二：改 svc-ai.yaml 里的 image，再 apply（推荐，YAML 进 git 可追溯）
kubectl apply -f svc-ai.yaml -n ai-image-prod
```

### 盯发布进度：rollout status

```bash
kubectl rollout status deployment/svc-ai -n ai-image-prod
```

```text
Waiting for deployment "svc-ai" rollout to finish: 1 out of 3 new replicas have been updated...
Waiting for deployment "svc-ai" rollout to finish: 2 out of 3 new replicas have been updated...
Waiting for deployment "svc-ai" rollout to finish: 1 old replicas are pending termination...
deployment "svc-ai" successfully rolled out
```

怎么读：它会**阻塞**直到发布完成或失败，CI/CD 流水线里常用它作为"发布成功"的判断。看到 `successfully rolled out` 才算发完。如果卡住不动，多半是新版 Pod 起不来——去 `get pods` + `logs` 查。

### 一键回滚：rollout undo

发布后发现新版有 bug，最快的止损就是回滚到上一版：

```bash
# 回滚到上一个版本
kubectl rollout undo deployment/svc-ai -n ai-image-prod
```

```text
deployment.apps/svc-ai rolled back
```

```bash
# 看历史版本，回滚到指定版本
kubectl rollout history deployment/svc-ai -n ai-image-prod
kubectl rollout undo deployment/svc-ai --to-revision=4 -n ai-image-prod
```

> **前端类比**：`rollout undo` 就是后端版的"一键回退到上一次部署"，像 Vercel/Netlify 控制台里点 "Rollback to previous deployment"。线上出事，**先回滚止血、再慢慢查原因**——这是值班铁律。

### 扩缩容：scale

```bash
# 活动来了，svc-ai 临时扩到 8 个副本
kubectl scale deployment/svc-ai --replicas=8 -n ai-image-prod
```

```text
deployment.apps/svc-ai scaled
```

活动结束 `--replicas=3` 缩回去即可。注意：`scale` 是临时改，下次 `apply` 你的 YAML（里面还写着 `replicas: 3`）会被覆盖回去——所以长期扩容要改 YAML。

---

## 探针：liveness vs readiness（配错会出大事）

上面反复提到探针，这里专门讲清楚。两个探针**目的完全不同**，配错方向会导致截然不同的事故。

```text
                  ┌─────────────────────────────────────────────┐
  livenessProbe ─►│ 探"还活着吗？" 失败 → 重启容器                 │  治"假死/卡死"
                  └─────────────────────────────────────────────┘
                  ┌─────────────────────────────────────────────┐
  readinessProbe ►│ 探"能接流量吗？" 失败 → 从 Service 摘掉，不重启  │  治"启动中/临时不可用"
                  └─────────────────────────────────────────────┘
```

- **livenessProbe（存活探针）**：探"进程是不是死了/卡死了"。失败到阈值 → **重启容器**。适合检测死锁、JVM 假死这类"进程在但救不活"的情况。
- **readinessProbe（就绪探针）**：探"现在能不能接流量"。失败 → **把这个 Pod 从 Service 后端摘掉**，流量不再进来，但**不重启**。等它恢复了再放回来。适合启动慢（JVM 启动要几十秒）、或临时依赖抖动（数据库短暂不可用）的场景。

**配错的两种典型事故**：

- **liveness 探针太激进 → 反复重启**：`initialDelaySeconds` 设太小（比如 5 秒），但 JVM 启动要 30 秒。探针在应用还没起来时就判定"死了"，重启它；重启后还是来不及，又重启……于是陷入无限重启，表现为 `CrashLoopBackOff`，但根本原因只是探针配置太急。解法：把 `initialDelaySeconds` 调到大于真实启动时间，或用 `startupProbe` 专门处理慢启动。
- **readiness 探针一直不过 → 不接流量、READY 显示 0/1**：探针打的路径写错、或 `/actuator/health/readiness` 因某个非关键依赖挂了而返回 503，导致 Pod 永远被摘流量。表现为服务"在跑但没人能访问"。解法：确认探针路径正确，并让 readiness 只检查"接流量必须就绪的核心依赖"，别把非关键依赖也算进去。

> **前端类比**：readiness 像组件的 loading 态——还在 `isLoading` 时你不会把它当成可交互的，等数据回来（就绪）才渲染、才接受用户点击。liveness 更像"看门狗（watchdog）定时器"——一段时间没收到心跳就强制重启。

---

## 排查三连（重点：值班最常遇到的三种 Pod 故障）

下面三种状态是前端转后端值班时**遇到频率最高**的，每个按"现象 → 排查命令 → 常见原因 → 解法"讲。看懂这三个，线上 80% 的 Pod 问题你都能自己定位。

### 一、CrashLoopBackOff —— Pod 反复重启

**现象**：`get pods` 里 STATUS 显示 `CrashLoopBackOff`，RESTARTS 数字蹭蹭往上涨。

```text
NAME                       READY   STATUS             RESTARTS      AGE
svc-user-66c7d5f8b-xm2v4   0/1     CrashLoopBackOff   5 (20s ago)   4m
```

它的意思是：容器启动后很快就退出，K8s 重启它，又退出，于是 K8s 按**指数退避（back-off）**拉长重启间隔（10s→20s→40s→...最长 5min），避免疯狂重启打爆机器。

**排查命令**：

```bash
# 第一步：看崩溃前那个容器的日志（关键！当前容器是新起的，看不到崩溃原因）
kubectl logs --previous svc-user-66c7d5f8b-xm2v4 -n ai-image-prod

# 第二步：看退出码和 Events
kubectl describe pod svc-user-66c7d5f8b-xm2v4 -n ai-image-prod
```

```text
Last State:     Terminated
  Reason:       Error
  Exit Code:    1
```

**常见原因**：

- 应用启动报错：连不上数据库/Redis、配置缺失、端口被占（`logs --previous` 里能看到堆栈，最常见）。
- 退出码 `137`：被 OOMKilled（内存超 limit，见下面第三种）。
- liveness 探针太激进，应用没起来就被判死、反复重启（见上一节）。

**解法**：先 `logs --previous` 看应用自己报了什么——它会直接告诉你原因（连不上 Redis、Bean 创建失败等）。是配置错就改 ConfigMap/Secret，是探针太急就调 `initialDelaySeconds`，是 OOM 就按第三种处理。

### 二、ImagePullBackOff —— 拉不到镜像

**现象**：Pod 卡在 `ImagePullBackOff` 或 `ErrImagePull`，READY 一直 `0/1`，容器根本没起来。

```text
NAME                       READY   STATUS             RESTARTS   AGE
svc-canvas-5f6b9c4d7-zz9p  0/1     ImagePullBackOff   0          90s
```

**排查命令**：

```bash
kubectl describe pod svc-canvas-5f6b9c4d7-zz9p -n ai-image-prod
```

看 Events 里的具体报错：

```text
Events:
  Warning  Failed   30s   kubelet  Failed to pull image "registry.example.com/ai-image/svc-canvas:1.5.0":
                                    manifest unknown: manifest tagged by "1.5.0" is not found
  Warning  Failed   30s   kubelet  Error: ErrImagePull
  Normal   BackOff  10s   kubelet  Back-off pulling image "...svc-canvas:1.5.0"
```

**常见原因**（看 Events 里的 message 对号入座）：

- `manifest unknown` / `not found`：**镜像名或 tag 写错**——最常见，发布时 tag 打错了，或 CI 还没推上去。
- `unauthorized` / `pull access denied`：**私有仓库没认证**——缺少 `imagePullSecrets`，或 Secret 里的仓库账号过期。
- `connection refused` / `timeout`：**连不上镜像仓库**——网络问题或仓库地址错。

**解法**：

- tag 错 → 改 YAML 里 image 的 tag，或确认 CI 真的把这个 tag 推到仓库了（`docker pull` 那个完整镜像名手动验证一下，见 [Docker 实战](/back-end/frontend-backend-guide/23-docker-in-practice)）。
- 认证错 → 配 `imagePullSecrets` 指向一个 docker-registry 类型的 Secret。

### 三、OOMKilled —— 超内存被杀

**现象**：Pod 重启，`describe` 里 `Last State` 的 `Reason` 是 `OOMKilled`，退出码 `137`。

**先理解 requests / limits**（一句话）：

- `requests`：调度时的"最低保证"，K8s 据此决定把 Pod 放到哪个节点（节点至少要有这么多空闲资源）。
- `limits`：硬上限。**CPU 超 limit 会被限流（变慢，不会被杀）；内存超 limit 会直接被 OOMKilled（杀掉重启）**。

```text
内存使用 ──────────────────────────► 时间
   │
512Mi ┤        ╭─╮      ╭─╮
   │         ╭╯ ╰─╮  ╭─╯ ╰╮
   │       ╭─╯    ╰──╯    ╰──╮
   │ ─ ─ ─ ╯ ─ ─ ─ ─ ─ ─ ─ ─ ╳ ← 突破 limit=1Gi 瞬间被 OOMKilled
1Gi ┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄ limit 红线
```

**排查命令**：

```bash
kubectl describe pod svc-canvas-5f6b9c4d7-jr8tq -n ai-image-prod
```

```text
    Last State:     Terminated
      Reason:       OOMKilled
      Exit Code:    137
      Started:      Mon, 01 Jun 2026 13:50:11
      Finished:     Mon, 01 Jun 2026 14:38:55
    Restart Count:  1
```

```bash
# 配合看内存趋势：是稳定高占用，还是一路只涨不降（泄漏）
kubectl top pod svc-canvas-5f6b9c4d7-jr8tq -n ai-image-prod
```

**常见原因 + 解法**（要分清两种）：

- **正常吃这么多内存**（比如生图任务并发高、缓存大）：limit 设小了。解法：评估实际峰值后**调大 `resources.limits.memory`**。注意 JVM 还要给堆外留空间，容器 limit 通常要比 `-Xmx` 大 25% 以上，否则 JVM 堆没满、容器内存先爆。
- **内存泄漏**（一路只涨不降，重启后又慢慢涨满）：调大 limit 只是拖延，根因是代码漏。这时要 dump 堆、分析对象——这正是 [OOM 与内存泄漏](/back-end/frontend-backend-guide/20-oom-memory-leak) 整章在讲的，配合 [JVM 内存模型](/back-end/frontend-backend-guide/18-jvm-memory-model) 理解堆/非堆/容器 limit 三者的关系。

怎么区分这两种？看 `kubectl top pod` 的内存曲线：**稳定在高位**多半是 limit 太小；**重启后单调上涨直到再次被杀**就是泄漏，光调 limit 治标不治本。

> ⚠️ 一句话记牢：**CPU 超 limit 只是变慢，内存超 limit 直接被 OOMKilled（退出码 137）。** 所以内存 limit 要慎重，宁可观察后再收紧。

---

## 小结

- **K8s = 容器编排**：你**声明期望状态**（几个副本、什么镜像、要不要探针），K8s 持续把现实调和成你声明的样子——和 React 声明式渲染同一套心智模型。它解决单机 Docker 给不了的自愈、扩缩容、滚动发布、负载均衡、配置管理。
- **核心对象**：Pod（最小调度单位，用完即弃）→ Deployment（管副本与发布）→ Service（一组 Pod 的稳定内部入口 + 负载均衡）→ Ingress（对外 HTTP 路由，呼应 svc-gateway）；配置用 ConfigMap，敏感配置用 Secret，对象按 Namespace 隔离。
- **kubectl 值班套路**：`get pods/deploy/svc` 看大盘 → `describe` 看 Events 定位 → `logs -f` / `logs --previous` 看日志 → `exec -it` 进容器自查 → `top` 看资源。出问题的标准下钻路径是 `get events` → `describe` → `logs`。
- **发布与回滚**：滚动更新逐个替换 Pod，靠**就绪探针保证不掉线**；`set image` 或改 YAML `apply` 触发，`rollout status` 盯进度，**出事先 `rollout undo` 回滚止血**。
- **探针**：liveness 失败重启（治假死），readiness 失败摘流量不重启（治启动慢/临时不可用）；liveness 太激进会反复重启，readiness 配错会"在跑却没人能访问"。
- **排查三连**：CrashLoopBackOff（反复重启 → `logs --previous` 看崩溃原因 + `describe` 看退出码）、ImagePullBackOff（拉不到镜像 → `describe` 看 tag/认证/网络）、OOMKilled（超内存被杀 → `describe` 看 Reason + `top` 看曲线，区分 limit 太小 vs 内存泄漏）。

### 自测

1. svc-ai 发了个新版本，`rollout status` 卡在 `1 out of 3 new replicas have been updated` 一直不动。结合滚动更新和就绪探针的原理，说说最可能卡在哪、你会用哪几条 kubectl 命令去定位？
2. 一个 Pod 显示 `CrashLoopBackOff`，RESTARTS=8。为什么直接 `kubectl logs`（不加 `--previous`）很可能看不到崩溃的真正原因？你应该敲哪条命令？
3. svc-canvas 的 Pod 反复 OOMKilled，退出码 137。怎么用 `kubectl top pod` 的内存曲线判断到底是"limit 设小了"还是"内存泄漏"？两种情况的处理方向有什么本质区别？

### 下一章

下一章 [配置与环境](/back-end/frontend-backend-guide/25-config-and-env) 会接着讲本章一带而过的 ConfigMap / Secret：配置怎么从代码里抽出来、怎么按环境（dev/test/prod）切换、敏感信息怎么管理——也就是后端版的 `.env` 是怎么一回事。

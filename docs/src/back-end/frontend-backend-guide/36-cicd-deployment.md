# CI/CD 与部署

> 你写前端时早就用过 CI/CD，只是可能没意识到。`git push` 到 main，GitHub Actions 自动跑构建、把 `dist/` 发到 GitHub Pages——这个仓库的 VitePress 文档站就是这么上线的（`.github/workflows/deploy.yml`）。你从没手动 `scp` 过文件，也没登录服务器解压过 tar 包，一切都是「提交即上线」。
>
> 后端的 CI/CD 是同一个思想，只是链路更长：要编译 Java、跑测试、打 Docker 镜像、推镜像仓库、滚动更新 K8s、做健康检查。这一章把「从一次 `git push` 到 `svc-ai` 新版本在生产稳稳跑起来」这条流水线拆开讲透，并教你三种发布策略和怎么一键回滚。

本章是 Part 7「工程化与上线」的核心。它建立在前面几章之上：[第二十三章 Docker 实战](/back-end/frontend-backend-guide/23-docker-in-practice) 教你打镜像、[第二十四章 Kubernetes 实战](/back-end/frontend-backend-guide/24-kubernetes-in-practice) 教你部署和探针、[第三十五章 测试](/back-end/frontend-backend-guide/35-testing) 教你写测试——CI/CD 就是把这些动作**自动串成一条线**。

> 💡 **前端类比**：CI/CD 之于后端，就像 Vercel 之于你的 Next.js 项目。Vercel 帮你做的事（监听 push → 装依赖 → `next build` → 部署 → 给个预览 URL），后端要自己用 GitHub Actions + Docker + K8s 拼出来。区别只是后端没有 Vercel 这种「全托管」，得自己写流水线、自己管镜像和集群。

---

## 一、为什么需要 CI/CD：先看手动部署有多疼

假设没有任何自动化，要把 `svc-ai` 的新版本发到生产，你得：

```text
1. 本地 mvn package 打出 jar             ← 忘了拉最新代码？打的是旧的
2. scp 上传到服务器                       ← 传错机器？传到 test 了？
3. ssh 上去 kill 掉旧进程                  ← kill 错 PID 把别的服务搞挂
4. java -jar 启动新的                      ← 配置文件忘了换成 prod
5. curl 试一下能不能访问                    ← 试着试着就忘了，直接下班
```

每一步都靠人脑记忆和手速，**任何一步错了都是生产事故**。而且只有你会发，你休假就没人敢动。这正是前端时代你不必操心的事——Vercel 把这些全吞了。

CI/CD 把这条链路变成「**机器照着剧本执行，每次都一模一样**」：

- **CI（Continuous Integration，持续集成）**：每次提交代码，自动拉代码、编译、跑测试。目标是**尽早发现集成问题**——别等到上线才发现你的改动让 `cpt-common` 的单测挂了。前端类比：你 PR 一提，Actions 自动跑 `tsc` + `vitest`，红了不让合。
- **CD（Continuous Delivery / Deployment，持续交付 / 部署）**：CI 通过后，自动把产物（这里是 Docker 镜像）**打包并发布**。「交付」指自动打好镜像、推到仓库、等人点一下按钮上生产；「部署」指连这一下按钮都省了，测试过就直接上线。本仓库 VitePress 那套就是**持续部署**——push 到 main 直接发布，没有人工卡点。

> ⚠️ 一句话区分：**CI 保证「代码是好的」，CD 保证「好的代码自动到了线上」。** 两者合起来，让「发布」从一件需要勇气的事，变成一件无聊到没人关注的事——这正是目标。

---

## 二、一条完整流水线长什么样

后端 CI/CD 流水线的标准阶段（Pipeline / Stage），从一次提交开始：

```text
   git push (main 分支)
        │
        ▼
┌──────────────────────────────────────────────────────────────┐
│  CI 阶段（在 CI Runner 这台临时机器上跑）                          │
│                                                                │
│  ① Checkout 拉代码  ─▶ ② mvn package 编译  ─▶ ③ 跑单元/集成测试   │
│         │                     │                     │          │
│   git clone 仓库         产出 svc-ai.jar        测试红 → 立即停    │
└──────────────────────────────────────────────────────────────┘
        │  测试全绿
        ▼
┌──────────────────────────────────────────────────────────────┐
│  CD 阶段                                                        │
│                                                                │
│  ④ docker build 镜像 ─▶ ⑤ push 到镜像仓库 ─▶ ⑥ 部署到 K8s        │
│       │                     │                      │          │
│  打成 svc-ai:abc123    registry/svc-ai:abc123   kubectl 更新     │
│                                                    │          │
│                              ⑦ 健康检查（探针）等新 Pod Ready     │
│                                     │                          │
│                              全部 Ready → ✅ 发布成功            │
│                              探针失败 → 自动停在旧版本，不掉线      │
└──────────────────────────────────────────────────────────────┘
```

每个阶段都是「上一阶段成功才进下一阶段」，任何一环失败，流水线立刻变红、停下、通知你——绝不会把半成品送上生产。

> 💡 **前端类比**：这七步和你熟悉的 `npm ci → tsc → vitest → next build → 上传 → CDN 生效` 是一一对应的。最大的新增项是 **④⑤ 打镜像 + 推仓库**——前端产物是静态文件直接丢 CDN，后端产物是一个**可运行的容器镜像**，必须先存进镜像仓库（Registry），K8s 再从那里拉。镜像仓库就是「后端版的 npm registry」，只不过存的不是 npm 包而是镜像。

---

## 三、GitHub Actions 实战：构建并推送 Java 镜像

我们以 `svc-ai` 为例，写一个真实可跑的 workflow。文件放在 `.github/workflows/svc-ai-deploy.yml`：

```yaml
name: svc-ai CI/CD

on:
  push:
    branches: [main]            # 只在 push 到 main 时触发
    paths:
      - 'svc-ai/**'             # 只有 svc-ai 目录变了才跑，省 CI 时间
      - 'cpt-common/**'         # 它依赖的共享组件变了也要重新构建

env:
  REGISTRY: registry.cn-hangzhou.aliyuncs.com   # 镜像仓库地址（阿里云为例）
  IMAGE: aigc/svc-ai                            # 镜像名

jobs:
  build-and-push:
    runs-on: ubuntu-latest      # CI Runner 用一台干净的 Ubuntu 临时机
    steps:
      # ① 拉代码
      - name: Checkout
        uses: actions/checkout@v4

      # ② 装 JDK 17，并开启 Maven 依赖缓存（关键加速点）
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven          # 缓存 ~/.m2，下次不用重新下全套依赖

      # ③ 编译 + 跑测试（mvn package 会自动跑 test 阶段）
      - name: Build & Test
        run: mvn -B -pl svc-ai -am clean package
        #   -B 非交互模式（CI 里别等输入）
        #   -pl svc-ai 只构建 svc-ai 这个模块
        #   -am 同时构建它依赖的 cpt-* 模块（also make）

      # ④ 登录镜像仓库（账号密码存在 GitHub Secrets，绝不写进 yaml）
      - name: Login to Registry
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ secrets.REGISTRY_USER }}
          password: ${{ secrets.REGISTRY_TOKEN }}

      # ⑤ 构建并推送镜像，tag 用本次提交的短 sha
      - name: Build & Push image
        uses: docker/build-push-action@v6
        with:
          context: ./svc-ai
          push: true
          tags: ${{ env.REGISTRY }}/${{ env.IMAGE }}:${{ github.sha }}

      # ⑥ 用新镜像更新 K8s 里的 svc-ai Deployment
      - name: Deploy to K8s
        run: |
          echo "${{ secrets.KUBECONFIG }}" > kubeconfig
          export KUBECONFIG=kubeconfig
          kubectl set image deployment/svc-ai \
            svc-ai=${{ env.REGISTRY }}/${{ env.IMAGE }}:${{ github.sha }} \
            -n aigc-prod
          kubectl rollout status deployment/svc-ai -n aigc-prod --timeout=120s
```

**怎么读这段 yaml**：

- `on.push.branches` + `paths`：触发条件。和你 VitePress 那份 `on: push` 一个道理，只是这里加了 `paths` 过滤——微服务仓库里几十个服务，不能每次改一行都把全部重新构建一遍。
- `secrets.REGISTRY_TOKEN`、`secrets.KUBECONFIG`：所有密码、kubeconfig 都存在仓库的 **Settings → Secrets** 里，yaml 里只写 `secrets.XXX` 这样的占位（具体引用语法见上面的 yaml 示例）。这就是 [第二十五章 配置与环境管理](/back-end/frontend-backend-guide/25-config-and-env) 讲的「密钥绝不进代码库」在 CI 里的落地。
- `cache: maven`：**最重要的加速项**。Java 项目首次构建要从 Maven 中央仓库下几百 MB 依赖；缓存 `~/.m2` 后，没改依赖的构建能从 5 分钟降到 1 分钟。前端类比：等价于缓存 `node_modules` / pnpm store，省掉每次 `pnpm install` 的下载。
- `kubectl rollout status ... --timeout=120s`：发布完**不是甩手就走**，而是盯着 K8s 把新 Pod 拉起来、探针通过。120 秒内没 Ready，这一步就失败、流水线变红——你立刻知道这次发布出问题了。

**触发后在 Actions 页面看到的日志大致长这样：**

```text
Run mvn -B -pl svc-ai -am clean package
[INFO] Reactor Build Order:
[INFO]   cpt-common ......................... SUCCESS
[INFO]   svc-ai ............................. SUCCESS
[INFO] Tests run: 37, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
...
#12 pushing manifest for .../aigc/svc-ai:9f3a1c8...
deployment.apps/svc-ai image updated
Waiting for deployment "svc-ai" rollout to finish: 1 of 2 updated replicas are available...
deployment "svc-ai" successfully rolled out
```

**怎么读这段日志**：`Reactor Build Order` 说明 `-am` 生效了，先编依赖的 `cpt-common` 再编 `svc-ai`；`Tests run: 37 ... Failures: 0` 是测试阶段全绿；最后 `successfully rolled out` 表示 K8s 已把新 Pod 全部拉起且探针通过。**结论**：这一整条从提交到上线，没有任何人碰过服务器。

> 💡 GitLab CI 思路完全一致，只是写法换成 `.gitlab-ci.yml` 的 `stages` + `script`，密钥放在 GitLab 的 CI/CD Variables。换了平台，七个阶段一个不少。

### 三阶段写法（更接近真实项目）

上面把 build 和 deploy 写在一个 job 里图省事。实际项目通常拆成显式的 `stages`，让流水线在界面上一目了然：

```text
┌─────────┐    ┌─────────┐    ┌─────────┐
│  test   │ ─▶ │  build  │ ─▶ │ deploy  │
│ 跑测试   │    │ 打+推镜像 │    │  上 K8s │
└─────────┘    └─────────┘    └─────────┘
   红则停          红则停        手动按钮(交付) 或 自动(部署)
```

`deploy` 阶段是否需要人工点一下「确认上生产」，就是**持续交付 vs 持续部署**的分界线。生产环境建议保留这个人工卡点，test 环境则可以全自动。

---

## 四、部署到 K8s：镜像怎么换上去

CI 把镜像推到仓库后，CD 要让 K8s 用上新镜像。有两种主流做法。

### 做法 A：kubectl set image（命令式，简单直接）

```bash
# 把 svc-ai 这个 Deployment 里名为 svc-ai 的容器，换成新镜像
kubectl set image deployment/svc-ai \
  svc-ai=registry.cn-hangzhou.aliyuncs.com/aigc/svc-ai:9f3a1c8 \
  -n aigc-prod
```

**预期输出：**

```text
deployment.apps/svc-ai image updated
```

执行后 K8s 自动开始**滚动更新**（见下一节）。优点是一条命令搞定，CI 里好写；缺点是「集群当前状态」和「Git 里的 manifest」会不一致——manifest 文件里还写着旧 tag，靠人记着同步。

### 做法 B：改 manifest 再 apply（声明式，可追溯）

更规范的做法是把镜像 tag 写进 YAML manifest，提交到 Git，再 `apply`：

```yaml
# k8s/svc-ai/deployment.yaml 里的关键片段
spec:
  template:
    spec:
      containers:
        - name: svc-ai
          image: registry.cn-hangzhou.aliyuncs.com/aigc/svc-ai:9f3a1c8  # ← CI 自动替换这一行
```

```bash
kubectl apply -f k8s/svc-ai/deployment.yaml -n aigc-prod
```

好处是**「线上跑的是哪个版本」永远等于「Git 里写的版本」**，可审计、可回溯——这套思想叫 GitOps（一切以 Git 为准）。管理多个服务、多套环境时，再上 **Helm**（把 manifest 模板化 + 参数化，像后端的「组件库 + props」）或 **Kustomize**（按环境叠加差异，像给基础 manifest 打 patch）来管理这堆 YAML。本章不展开，知道有这两个工具、解决「YAML 太多太重复」的问题即可。

### 铁律：镜像 tag 用 commit sha，永远不要用 latest

```text
✅  svc-ai:9f3a1c8     ← 每次提交一个唯一 tag，一眼对应一次 commit
❌  svc-ai:latest      ← 浮动 tag，今天的 latest ≠ 明天的 latest
```

**为什么 `latest` 是坑**：

- **回滚无门**。出事想退回上个版本，但 `latest` 已经被覆盖，你根本拉不到「上一个 latest」是哪个。用 sha 时，回滚就是把 tag 换回 `8c2e0d1`，精准明确。
- **缓存歧义**。K8s 默认对已存在的 tag 不重新拉镜像。两个节点上的 `latest` 可能是不同时间拉的、内容不一样，排查问题时人会疯。
- **追溯断链**。线上报错，你想知道「现在跑的到底是哪行代码」。tag 是 `9f3a1c8` 就能直接 `git show 9f3a1c8` 定位；是 `latest` 就只能猜。

> 💡 **前端类比**：这等价于你绝不会把生产依赖写成 `"react": "latest"`，而是锁定 `"react": "18.3.1"`，并用 lockfile 钉死。`latest` 在前端依赖里是大忌，在后端镜像里同样是大忌。用 `github.sha` 当 tag，就是镜像世界的 lockfile。

---

## 五、三种发布策略：怎么换版本不出事

新镜像准备好了，最后一步是「把流量从旧版本切到新版本」。怎么切，决定了用户会不会感知到抖动、出事能多快回滚。

### 滚动发布（Rolling Update，K8s 默认）

逐个替换 Pod：起一个新版本 Pod，等它 Ready，再干掉一个旧的，如此循环，直到全部换完。这正是 [第二十四章](/back-end/frontend-backend-guide/24-kubernetes-in-practice) 里 Deployment 的默认行为。

```text
旧 旧 旧 旧   →   新 旧 旧 旧   →   新 新 旧 旧   →   新 新 新 新
            起1个新的Ready    继续替换         全部换完
```

```yaml
# Deployment 里控制滚动节奏的参数
spec:
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1           # 最多临时多起 1 个 Pod（多占点资源换平滑）
      maxUnavailable: 0     # 替换期间不允许少于期望数量 → 全程不掉容量
```

- **优点**：零额外环境、配置简单、默认就有，平滑不停机。
- **缺点**：发布过程中**新旧版本同时在线**（可能持续一两分钟），如果新版接口和旧版不兼容会出乱子；回滚也是「再滚一遍」，不够快。
- **适用**：绝大多数无状态服务的日常发布，比如 `svc-user`、`svc-ai`。

### 蓝绿发布（Blue-Green，秒级回滚）

准备**两套完整环境**：蓝（当前生产）和绿（新版本）。绿环境部署好、自测通过后，把流量入口**一次性全切**到绿。出事就把入口切回蓝，秒级回滚。

```text
                  ┌──────────────┐
   100% 流量 ──▶  │ Service (入口) │
                  └──────┬───────┘
            切换前↓              ↓切换后
        ┌─────────┐        ┌─────────┐
        │ 蓝(旧版) │        │ 绿(新版) │
        │  运行中  │        │  待命   │
        └─────────┘        └─────────┘
   切流量 = 改 Service 的 selector，从 version:blue 改成 version:green
```

- **优点**：切换是原子的，用户不会撞上新旧混跑；**回滚只需把入口切回去，秒级生效**。
- **缺点**：要**双倍资源**（两套环境同时存在）；数据库等有状态依赖需要两套都兼容。
- **适用**：对发布平滑性要求极高、且能承担双倍资源的核心链路，比如大促前的 `svc-gateway`。

### 金丝雀发布（Canary，先小流量验证）

先把**一小部分流量**（比如 5%）放给新版本，盯着监控看错误率、延迟正常，再逐步放大到 20%、50%、100%。名字来自矿工用金丝雀探测瓦斯——让小批用户先「试毒」。

```text
   95% ──▶ 旧版 (svc-ai v1)
    5% ──▶ 新版 (svc-ai v2)   ← 盯监控：错误率/延迟正常？
        │
        ▼ 正常，逐步放量
   80% v1 / 20% v2  →  50% / 50%  →  0% / 100%（全量）
        │
        ▼ 异常
   立即把 5% 切回 v1，只有极少数用户受影响
```

- **优点**：**真实生产流量验证**，问题暴露在 5% 用户而非 100%；放量节奏可控。
- **缺点**：流量切分需要更强的能力（Ingress 权重、或 Istio 这类服务网格），运维复杂度最高。
- **适用**：高风险变更（如 `svc-canvas` 改了核心任务编排逻辑），不敢一把全量时。

| 策略 | 资源开销 | 回滚速度 | 复杂度 | 典型场景 |
| --- | --- | --- | --- | --- |
| 滚动 | 低（多 1 个 Pod） | 慢（再滚一遍） | 低 | 日常发布，默认选它 |
| 蓝绿 | 高（双份） | 秒级（切入口） | 中 | 核心链路、零抖动要求 |
| 金丝雀 | 中（多一组小副本） | 快（切回小流量） | 高 | 高风险变更、灰度验证 |

> 💡 **前端类比**：金丝雀就是你做过的「灰度发布 / A/B 放量」——先给 5% 用户看新版页面，数据没问题再全量。蓝绿则像 Vercel 的 Preview Deployment + Promote：新版本先在独立环境跑通，确认无误后一键提升为 Production。

---

## 六、回滚：发布的安全带

发布的第一原则不是「不出事」，而是「**出事能在一分钟内退回去**」。K8s 把每次 Deployment 变更都记成一个 revision，回滚是一条命令。

**症状**：刚发布的 `svc-ai:9f3a1c8` 上线后，监控告警错误率飙升，需要立即退回上个版本。

```bash
# 1. 看历史版本
kubectl rollout history deployment/svc-ai -n aigc-prod
```

**预期输出：**

```text
deployment.apps/svc-ai
REVISION  CHANGE-CAUSE
3         kubectl set image ... svc-ai:8c2e0d1
4         kubectl set image ... svc-ai:9f3a1c8   ← 当前这个有问题
```

```bash
# 2. 一键回滚到上一个版本
kubectl rollout undo deployment/svc-ai -n aigc-prod
```

**预期输出：**

```text
deployment.apps/svc-ai rolled back
```

```bash
# 3. 盯着回滚完成
kubectl rollout status deployment/svc-ai -n aigc-prod
```

**预期输出：**

```text
Waiting for deployment "svc-ai" rollout to finish: 1 out of 2 new replicas have been updated...
deployment "svc-ai" successfully rolled out
```

**怎么读**：`rollout undo` 默认退回**上一个** revision（也可 `--to-revision=3` 指定）。回滚本身也走滚动更新，所以同样平滑、不掉线。**结论**：用了 sha tag + Deployment 的版本记录，回滚永远是确定性的一条命令，这就是为什么第四节强调不能用 `latest`——`latest` 让回滚这条安全带直接失效。

### 健康检查让发布与回滚都不掉线

回顾 [第二十四章](/back-end/frontend-backend-guide/24-kubernetes-in-practice) 的探针——它是「发布不掉线」的关键，CI/CD 全靠它兜底：

```yaml
# svc-ai 的探针配置
readinessProbe:                # 就绪探针：通过了才把流量给这个 Pod
  httpGet:
    path: /actuator/health/readiness
    port: 8085
  initialDelaySeconds: 10      # 给应用启动留时间
  periodSeconds: 5
livenessProbe:                 # 存活探针：连续失败就重启这个 Pod
  httpGet:
    path: /actuator/health/liveness
    port: 8085
  periodSeconds: 10
```

滚动更新时，K8s **只把流量切给 readiness 通过的新 Pod**；新版本要是起不来或健康检查不过，旧 Pod 一个都不会被干掉，流量始终留在好的版本上——发布失败也**不掉线**。Spring Boot 用 `spring-boot-starter-actuator` 就能暴露 `/actuator/health` 这套端点（配合 [第二十五章](/back-end/frontend-backend-guide/25-config-and-env) 的配置开启）。

> ⚠️ 没有 readiness 探针的「平滑发布」是假平滑：K8s 一看新 Pod 进程起来了就切流量，但你的 Spring 应用还在加载 Bean、还没连上 MongoDB，这几秒进来的请求全是 5xx。探针就是告诉 K8s「**别看进程起没起，看我说没说 ready**」。

---

## 七、上线 checklist

每次往生产发布前，对着过一遍这张清单，能挡掉绝大多数事故：

- **配置就绪**：prod 的 ConfigMap / Secret 都更新了吗？新增的环境变量在生产环境配了吗？（参见 [配置与环境管理](/back-end/frontend-backend-guide/25-config-and-env)）
- **DB 迁移**：这次发布改了表结构 / 索引吗？迁移脚本是否**向后兼容**——新代码上线但还没全量时，旧代码也得能跑（呼应滚动发布期间新旧并存）。
- **回滚预案**：上个稳定版本的 sha 记下来了吗？回滚命令是否演练过？数据库变更能不能回滚？
- **监控告警**：错误率、延迟、QPS 的看板和告警都在线吗？发布后头 15 分钟有没有人盯？
- **灰度策略**：这次变更风险多大？该用滚动、蓝绿还是金丝雀？要不要先发 test 环境观察一天？

把它贴在发布流程文档里，养成「发布前过清单」的肌肉记忆——这和前端上线前过一遍「环境变量、CDN 缓存、回滚版本」是同一种纪律。

---

## 小结

- **CI/CD 的本质是把「从提交到上线」变成机器照剧本执行的确定性流程**：CI 保证代码是好的（自动编译 + 测试），CD 保证好代码自动到线上（打镜像 + 推仓库 + 部署）；这就是你前端用 Vercel / GitHub Actions 享受过的体验，后端要自己拼出来。
- **流水线七阶段**：拉代码 → 编译（`mvn package`）→ 跑测试 → 构建镜像 → 推镜像仓库 → 部署 K8s → 健康检查，任何一环失败立即停、绝不送半成品上生产；Maven 依赖缓存是关键加速点。
- **镜像 tag 用 commit sha，绝不用 `latest`**：sha 让每次发布唯一、可追溯、可精准回滚，等价于前端锁死依赖版本 + lockfile。
- **三种发布策略**：滚动（默认、逐个替换、低开销）、蓝绿（两套环境切流量、秒级回滚、双倍资源）、金丝雀（小流量先验证再放量、最稳但最复杂）；配合 readiness 探针实现发布不掉线。
- **回滚是发布的安全带**：`kubectl rollout undo` 一条命令退回上版本，前提是用了 sha tag + 探针把关；上线前对着 checklist 过一遍（配置、DB 迁移、回滚预案、监控、灰度）。

### 自测

1. 为什么 CI 流水线里要开启 Maven 依赖缓存？它对应前端构建里的哪个优化？不开会怎样？
2. 镜像 tag 用 `latest` 会让本章讲的哪个关键能力直接失效？请结合「回滚」说明原因。
3. `svc-canvas` 要上线一个改动了核心任务编排逻辑的大版本，你担心有隐藏 bug 影响大批用户。滚动、蓝绿、金丝雀三种策略你会选哪个？为什么？

### 下一章

流水线和发布策略就位后，下一步是把前面学的所有东西串起来动手练——下一章 [上手练习](/back-end/frontend-backend-guide/37-hands-on-exercises) 用一组实战任务帮你把整套后端技能跑通一遍。

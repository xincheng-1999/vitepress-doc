# CI/CD 与部署

你在本地改好了一个接口，测试也通过了。接下来怎样把这一次改动交给同事使用，并且知道发出去的究竟是哪一版？

这篇就沿着一次 Java 应用的版本交付往下走：先检查代码，再生成可追溯的镜像，最后讨论平台如何更新、怎样确认结果和准备回退。本仓库本身是 VitePress 文档站，不包含下面的 Java 服务、镜像仓库或 Kubernetes 部署工程；示例没有实际触发 CI、推送镜像或部署生产。没有交付需求时，可以先跳过，沿 [五阶段主线](./index) 继续。

## 1. 先把“本地没问题”变成别人也能重复的检查

如果每个人都在自己的电脑上打包，JDK、依赖、启动参数和测试数据可能不同。**CI（持续集成）**做的第一件事，是把团队约定的检查放到统一流程中，每次改动都按同样步骤执行。

例如检出代码、安装选定的 JDK、执行编译与测试。测试失败时停止后续交付，不继续把这一份产物发布出去。CI 通过只说明这些检查通过，不保证代码没有 bug；哪些风险能被发现，取决于测试和检查覆盖了什么。测试内容可配合 [测试](/back-end/frontend-backend-guide/35-testing) 设计。

Java 中，`mvn package` 会经过常规的 `test` 阶段，但集成测试是否运行还取决于插件和生命周期配置。项目若把集成测试绑定到 `verify`，流水线就应执行到 `verify`，不能只凭 `BUILD SUCCESS` 宣称所有测试已完成。

## 2. 先看一份不接生产的 CI 示例

假设你另外有一个单模块 Maven 应用，使用 Java 17，测试所需依赖已能在 CI 环境中准备好。可以在它自己的仓库中编写类似流程，**不是把它加入本篇所在的文档仓库**：

```yaml
name: Java CI example

on:
  pull_request:
  workflow_dispatch:

permissions:
  contents: read

jobs:
  verify:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4
      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven
      - name: Compile and test
        run: mvn -B verify
```

这里的 `pull_request` 在变更评审时触发，`workflow_dispatch` 提供手动触发入口。示例只做检查，不持有生产凭据，也没有部署步骤。第三方 Action 的版本应按团队策略审查与固定，生产流程通常还会固定到审核过的提交。

`cache: maven` 主要减少依赖重复下载，不代替依赖版本管理。构建也不应依赖某台 Runner 恰好缓存过的本地包；项目需要完整声明依赖和构建条件。

如果日志显示测试失败，先读失败用例及对应报告；如果连依赖都没下载成功，处理的是构建环境或仓库访问。两者都叫“流水线红了”，下一步却不同。也要留意跳过的测试、零测试执行等情况，而不是只找最后一行成功或失败。

## 3. 检查通过后，交付的是同一份什么东西

**CD** 有两种常见含义：持续交付把产物准备到可发布状态，实际发布可由人批准；持续部署则把通过约定检查的版本自动送入目标环境。采用哪一种，取决于团队的风险与管理要求，不是后端必须推送就上线。

如果以容器交付，检查通过后通常把应用打成镜像，再上传到镜像仓库。平台以后拉取这份镜像，不是在每台业务机器上重新编译源码。镜像构建基础见 [Docker 实战](/back-end/frontend-backend-guide/23-docker-in-practice)。

先确认 Dockerfile 使用的确实是本次检查过的产物。例如约定 Maven 输出唯一的 `target/app.jar`，Dockerfile 也复制这个文件；不能测试了一个目录，却随手把另一个旧 JAR 放进镜像。跨 job 时还需要显式传递构建产物，每个 job 的工作目录并不天然共享。

在 GitHub Actions 的构建环境中，已有对应 Dockerfile 时，镜像构建步骤的形状可以是：

```bash
# GITHUB_SHA 由 GitHub Actions 提供；这里只展示构建格式
# 执行会下载基础镜像并创建本地镜像，不是查看命令
docker build -t "registry.example.com/demo/task-api:${GITHUB_SHA:?}" .
```

示例仓库地址不可直接使用。推送还需要真实仓库、网络和最小权限的认证，应通过受保护的凭据配置或短期身份取得，不把密码写进 YAML，也不在日志中输出。来自不可信 PR 的代码不应取得发布凭据或生产权限。

### 为什么要记录版本，而不只写 latest

`latest` 这样的可变标签容易被覆盖，无法单凭名称知道具体内容。通常用提交 SHA 等唯一版本标识标记镜像，并记录构建来源；但 SHA 标签本身仍可能被重新推送，并不天然不可变。

更严格的交付可以记录镜像 digest，用它确认实际内容，并让测试、预发布和正式发布提升同一份镜像，而不是每个环境各重建一次。配置与密钥在运行时按环境注入，不能烘焙进通用镜像，见 [配置与环境管理](/back-end/frontend-backend-guide/25-config-and-env)。

即便源码相同，基础镜像、依赖或构建参数变化，也可能得到不同镜像。因此“有 commit SHA”只是追溯的一部分，还要保留构建记录和实际镜像标识。

## 4. 镜像准备好以后，平台才开始更新应用

到这里得到的是可交付产物，还没有证明用户已经在使用它。如果团队使用托管应用平台，就按平台的发布方式交付；如果使用 Kubernetes，则由发布系统更新 Deployment 等资源，不必让每个开发者直接持有生产 kubeconfig。

发布前先确认这份镜像能在目标环境拉取，新增配置已准备好，数据库与旧版仍兼容。有数据库迁移时，要把它单独考虑：滚动过程中旧版和新版可能同时存在，先删旧字段再发新版，可能让仍运行的旧副本立刻失败。

Kubernetes 中，修改 Pod 模板里的镜像会触发更新。可以由发布系统生成并应用 manifest，也可以使用受控命令；无论哪种，都应有清楚的期望状态和记录。只手动改集群而不更新配置来源，下一次自动同步可能覆盖它。

::: details 按需看：Kubernetes 更新命令所在的位置

下面只说明一类自管发布的动作，**不是要求执行的练习**。必须先有真实 Deployment、拉取凭据、合适的探针、授权与回退安排；命令会修改集群，不应在陌生或生产环境照抄。

```bash
# 先在授权终端核对目标集群，demo 是占位命名空间
kubectl config current-context
# 更新命名为 app 的容器镜像；地址与版本均为占位值
kubectl set image deployment/task-api \
  app=registry.example.com/demo/task-api:example-version -n demo
# 观察 Deployment 更新结果；此命令本身不触发部署
kubectl rollout status deployment/task-api -n demo --timeout=120s
```

`image updated` 说明更新请求已被接受，不表示镜像能拉下来或应用能运行。`rollout status` 超时退出表示观察未在规定时间内成功结束，不会自动回滚，也不意味着后台更新已经停止。

若使用 manifest，关键字段仍是 Deployment 的 Pod 模板：

```yaml
spec:
  template:
    spec:
      containers:
        - name: app
          image: registry.example.com/demo/task-api:example-version
```

这只是字段片段，不是完整可部署文件。Helm、Kustomize 或 GitOps 控制器可以管理不同环境的配置，但仅仅“改 YAML 再 apply”并不自动构成完整的 GitOps 管理。
:::

## 5. 更新过程中，流量怎样交给新版

以 Kubernetes 常见的滚动更新为例，控制器逐步创建新版 Pod、缩减旧版。readiness 探针表示实例是否适合接流量，更新策略限制临时增加和不可用的副本数量。相关机制见 [Kubernetes 实战](/back-end/frontend-backend-guide/24-kubernetes-in-practice)。

但探针通过只反映选定的检查项。新版本也可能已经 Ready，却在某类任务上报错。发布是否可继续，还要看实际接口、错误率、延迟、依赖负载和业务结果，不能只由一条 `successfully rolled out` 决定。

需要控制新版本影响范围时，再考虑其他策略：

| 策略 | 怎样交接请求 | 适合解决的问题与代价 |
| --- | --- | --- |
| 滚动更新 | 逐步替换副本，新旧短时共存 | 日常更新较简单，但依赖版本兼容、容量与停机处理 |
| 蓝绿 | 准备两组版本，再切入口 | 便于保留旧版回切，但要多占资源；连接和路由收敛不是绝对瞬时 |
| 金丝雀 | 先把部分可识别流量交给新版，再比较 | 限制初期影响，但需可靠分流、版本指标和观察条件 |

普通 Deployment 的滚动更新不等于精确按 5% 用户做金丝雀。按用户、租户或权重分流，需要入口或专门发布系统支持；长连接、会话和后台任务也要纳入考虑。

蓝绿或金丝雀并不自动解决数据库不兼容，也不保证回切就能撤销新版本已经写入的数据。选择策略时，要看这次改动的风险，而不是简单认定某一种“最安全”。

## 6. 如果新版出问题，怎样退回

发布前就应明确上一个可用镜像、配置与数据库兼容条件，并由有权限的人准备回退方式。不能等出问题后才发现旧镜像已被清理，或新版迁移使旧代码无法运行。

Kubernetes 可以先查看 Deployment 的历史：

```bash
kubectl rollout history deployment/task-api -n demo
```

revision 是 Pod 模板的修订记录，不是自动附带业务版本说明的完整发布档案。镜像与配置关系仍需结合发布系统记录确认，`CHANGE-CAUSE` 也不一定有内容。

如果经过授权，确认某个 revision 对应可运行的旧模板，可以按团队流程回退。下面只是说明命令，执行前必须换成实际目标：

```bash
# 修改集群：示例回退到已经核对过的 revision 3
kubectl rollout undo deployment/task-api -n demo --to-revision=3
kubectl rollout status deployment/task-api -n demo --timeout=120s
```

`rolled back` 表示回退请求已被接受，之后仍要观察更新和实际请求结果。它主要恢复 Pod 模板，不会自动撤销数据库迁移、消息、外部配置或已经产生的业务效果；旧模板里引用同名 ConfigMap/Secret 时，也未必能取得历史值。

因此，回滚不是永远确定的一条救命命令。必要时可能需要关闭新功能、限制入口或向前修复，由实际故障流程决定，而不是反复重启碰运气。

## 7. 这一次交付什么时候才算结束

回到最初的问题：发出去的是哪一版？应能从发布记录找到源码提交、检查结果、镜像标识、目标环境与配置变化。测试没有通过，停在检查；镜像没有上传成功，停在交付；平台更新失败，查实例状态和启动日志；更新完成但业务异常，则按预案停止放量或回退。

这是一条逐步确认的过程，不是“CI 绿了就绝不会出事故”。刚开始可以只把编译测试做好，再接上可追溯的产物与受控发布；不必一次拼出复杂平台。想在本地理解这些环节，可以按需选 [动手练习](/back-end/frontend-backend-guide/37-hands-on-exercises)，其中没有要求连接生产环境。

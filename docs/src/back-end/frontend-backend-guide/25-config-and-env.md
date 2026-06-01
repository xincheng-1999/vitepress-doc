# 配置与环境管理

> 你在前端早就处理过"环境"这件事：`.env.development` 和 `.env.production` 不一样，`import.meta.env.VITE_API_BASE` 指向不同的后端地址，CI 里再注入一套生产变量。后端的配置管理本质上是同一回事，只是变量更多（数据库地址、Redis 密码、MQ 连接、密钥……）、环境更多（dev/test/prod，外加容器和 K8s），出错的代价也更大——把测试库地址带到生产、把密钥提交进 Git，都是真实发生过的事故。
>
> 这一章把后端配置体系一次讲透：从 12-factor 原则，到 Spring 的 `application.yml` / profile / 优先级，再到 Docker 和 K8s 怎么注入配置，最后划清安全红线。

---

## 一、12-Factor：配置为什么要和代码分离

后端有一份被广泛遵循的应用方法论叫 **12-Factor App**，其中第 3 条专门讲配置：

> **配置应该存在环境里，而不是代码里。** 判断标准很简单：如果把代码开源出去，会不会泄露任何密码、地址、密钥？如果会，说明配置没分离干净。

**前端类比**：你不会把 `VITE_API_KEY` 直接写死在 `src/api.ts` 里再提交到 GitHub——你会写进 `.env.local`（且 `.gitignore` 掉它），构建时由 `import.meta.env` 注入。后端的规矩一模一样，只是"环境"从浏览器构建期挪到了服务器/容器运行期。

为什么必须分离？因为**同一份代码（同一个 jar / 同一个镜像）要在 dev、test、prod 跑**。如果把环境差异写进代码，你就得为每个环境编译一个版本——这正是 12-factor 要消灭的。

```text
❌ 配置写死在代码里                    ✅ 配置从环境注入
┌────────────────────┐               ┌────────────────────┐
│ 代码 + dev 配置     │ → dev          │                    │ + dev 环境变量 → dev
│ 代码 + prod 配置    │ → prod         │  一份不变的代码/镜像  │ + prod 环境变量 → prod
│ （编译两次，易错）   │               │                    │ + test 环境变量 → test
└────────────────────┘               └────────────────────┘
```

记住一句话贯穿全章：**构建产物只有一份，环境差异全靠注入。** 我们的 AI 生图项目里，`svc-ai` 打成一个镜像，推到 test 和 prod 两套 K8s 集群，靠的就是不同的 ConfigMap 和 Secret，而不是两个镜像。

---

## 二、Spring 配置体系

### 2.1 application.yml：配置的家

Spring Boot 默认从 `src/main/resources/application.yml`（或 `.properties`）读配置。YAML 用缩进表达层级，对前端来说就像写一个 JSON，但没有引号和大括号的噪音。

```yaml
# svc-user/src/main/resources/application.yml
server:
  port: 8083                       # 服务监听端口

spring:
  application:
    name: svc-user                 # 服务名，注册中心/日志里都靠它识别
  data:
    mongodb:
      uri: mongodb://localhost:27017/aigc   # 本地默认连本地库
  redis:
    host: localhost
    port: 6379

app:
  quota:
    default-free: 20               # 新用户默认免费生图次数
  feature:
    new-canvas-editor: false       # 功能开关：新版画布编辑器，默认关
```

**前端类比**：这就是你的 `config.ts` 或根目录 `.env`——一份默认值，所有人共享。注意 `app.quota.default-free` 这种自定义层级，YAML 允许你随意嵌套业务配置，后面用 `@ConfigurationProperties` 一次性读出来。

### 2.2 多环境：`application-{env}.yml` + profile

一份默认配置不够用——dev 连本地 Mongo，prod 连云上集群。Spring 用 **profile** 解决：约定文件名 `application-{profile}.yml`，激活哪个 profile 就叠加哪个文件。

```text
application.yml            ← 公共默认值（所有环境共享）
application-dev.yml        ← 开发环境（本地或开发服务器）
application-test.yml       ← 测试环境
application-prod.yml       ← 生产环境
```

```yaml
# application-prod.yml：只写和默认值不同的部分
spring:
  data:
    mongodb:
      uri: ${MONGO_URI}            # 生产库地址，从环境变量注入，不写死
  redis:
    host: ${REDIS_HOST}
    password: ${REDIS_PASSWORD}    # 密码绝不写明文，见本章第四节红线

app:
  feature:
    new-canvas-editor: true        # 生产开启新画布
```

**前端类比**：`application.yml` + `application-prod.yml` 的叠加关系，就是 Vite 里 `.env`（基础）被 `.env.production`（覆盖）合并——profile 文件里只写差异项，没写到的自动继承默认值。

**怎么激活 profile？** 通过 `spring.profiles.active`，它本身也是个配置项，所以可以从任何来源指定。本地开发常在 `application.yml` 里写默认：

```yaml
spring:
  profiles:
    active: dev          # 本地默认走 dev，不写代码不用动
```

但**生产环境绝不靠这一行**——而是启动时用命令行或环境变量覆盖（下一节解释为什么）：

```bash
# 启动 jar 时指定生产 profile
java -jar svc-user.jar --spring.profiles.active=prod

# 或用环境变量（容器里最常见）
export SPRING_PROFILES_ACTIVE=prod
java -jar svc-user.jar
```

> 注意环境变量的命名转换：配置项 `spring.profiles.active` 对应环境变量 `SPRING_PROFILES_ACTIVE`——点变下划线、字母转大写。这是 Spring 的 relaxed binding 规则，记住它，K8s 注入时全靠它。

### 2.3 配置优先级：谁能覆盖谁

同一个配置项可能在多个地方出现，Spring 按**优先级从低到高**取最终值——高优先级覆盖低优先级。简化版（够日常用）的顺序是：

```text
低 ┌─────────────────────────────────────────────┐ 高
   │ ① application.yml        默认值，打包在 jar 里   │
   │ ② application-{env}.yml  profile 覆盖默认       │
   │ ③ 操作系统环境变量        容器/K8s 注入          │
   │ ④ 命令行参数 --key=val    启动时最高，覆盖一切    │
   └─────────────────────────────────────────────┘
```

**前端类比**：完全就是 Vite 的环境变量优先级——`.env` < `.env.production` < 真正的 shell 环境变量 < 命令行临时设的。后端只是把"浏览器构建"换成"服务器运行"。

这个顺序解释了一个重要实践：**生产敏感配置走环境变量（③）而不是写进 `application-prod.yml`（②）**。因为 profile 文件会被打进 jar、提交进 Git，而环境变量在运行时才注入，既能覆盖默认值，又不进代码库。命令行（④）优先级最高，适合临时 debug 时覆盖单个值，但不适合长期使用（会暴露在进程列表里）。

实操验证——目标是确认线上到底用的哪个 profile 和端口：

```bash
# 查看正在运行的 Java 进程的启动参数
ps -ef | grep svc-user
```

```text
appuser  12031  1  3 10:22 ?  00:04:11 java -jar /app/svc-user.jar --spring.profiles.active=prod
```

怎么读：`--spring.profiles.active=prod` 出现在命令行里（优先级④），说明无论 `application.yml` 里默认写的是什么，这个实例铁定跑在 prod profile。如果你怀疑配置没生效，第一步永远是看实际启动参数，而不是猜测。

### 2.4 在代码里读配置：@Value 与 @ConfigurationProperties

配置文件写好了，代码怎么拿到值？两种方式，对应两种场景。

**方式一：@Value——读单个值**，适合零散的、一两个配置项。

```java
@Service
public class UploadService {

    // 读 app.upload.max-size，冒号后是默认值（配置缺失时用 10MB）
    @Value("${app.upload.max-size:10485760}")
    private long maxUploadSize;

    @Value("${spring.application.name}")
    private String appName;
}
```

**前端类比**：`@Value("${key}")` 就是 `import.meta.env.VITE_KEY`——按 key 取一个值。冒号默认值 `:10485760` 相当于前端的 `import.meta.env.VITE_MAX ?? 10485760`。

**方式二：@ConfigurationProperties——成组绑定**，适合一整块结构化配置。这是更推荐的方式，类型安全、可校验、有 IDE 提示。

```java
// 把 yml 里 app.quota.* 整块绑定到这个对象
@Component
@ConfigurationProperties(prefix = "app.quota")
public class QuotaProperties {
    private int defaultFree;          // 对应 app.quota.default-free（自动转驼峰）
    private int maxPerDay;            // 对应 app.quota.max-per-day
    // getter / setter 省略，Spring 靠它们注入
}

@Service
public class QuotaService {
    private final QuotaProperties quota;

    public QuotaService(QuotaProperties quota) {   // 构造器注入
        this.quota = quota;
    }

    public RtData<Integer> initQuota(Long uid) {
        int free = quota.getDefaultFree();         // 直接用，类型是 int，编译期就检查
        // ... 写入用户初始配额
        return RtData.ok(free);
    }
}
```

**前端类比**：`@ConfigurationProperties` 就像你用 **zod** 定义一个 schema 把 `import.meta.env` 一次性解析成强类型对象——`const config = configSchema.parse(env)`，之后 `config.quota.defaultFree` 全程有类型提示，拼错 key 编译就报错。`@Value` 散读字符串就像直接 `process.env.X`，拼错了运行时才炸。**配置项超过两三个，一律用 `@ConfigurationProperties`。**

> Spring 的依赖注入、`@Component`、构造器注入的来龙去脉见 [Spring Boot 的 IoC 与依赖注入](/back-end/frontend-backend-guide/06-spring-boot-ioc-di)；配置类相关注解也整理在 [注解速查表](/back-end/frontend-backend-guide/08-annotations-cheatsheet)，Spring 的配置加载机制还可参考 [Spring 配置](/back-end/java/07b-spring-config)。

---

## 三、容器环境下的配置注入

应用进了容器，配置就不再来自本地文件，而是由容器平台注入。这一节呼应 [Docker 实战](/back-end/frontend-backend-guide/23-docker-in-practice) 和 [Kubernetes 实战](/back-end/frontend-backend-guide/24-kubernetes-in-practice)。

### 3.1 Docker：用 -e 注入环境变量

最朴素的方式，`docker run` 时用 `-e` 把环境变量塞进容器：

```bash
docker run -d --name svc-user \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e MONGO_URI="mongodb://mongo-prod:27017/aigc" \
  -e REDIS_HOST=redis-prod \
  -e REDIS_PASSWORD='S3cr3t!' \
  -p 8083:8083 \
  registry.example.com/svc-user:1.4.0
```

进容器确认变量真的注入进去了：

```bash
docker exec svc-user env | grep -E 'SPRING|MONGO|REDIS'
```

```text
SPRING_PROFILES_ACTIVE=prod
MONGO_URI=mongodb://mongo-prod:27017/aigc
REDIS_HOST=redis-prod
REDIS_PASSWORD=S3cr3t!
```

怎么读：这四个变量都在，说明注入成功；Spring 启动时会按 relaxed binding 把 `MONGO_URI` 映射到 `spring.data.mongodb.uri`（前提是 yml 里写了 `${MONGO_URI}` 占位，或者直接命名匹配）。如果某个变量没出现，多半是 `docker run` 命令拼错了。

**前端类比**：`docker run -e KEY=val` 就是 CI 里在构建步骤前 `export VITE_KEY=val`——给运行环境塞变量。区别是前端注入在构建期，容器注入在运行期。

`-e` 适合手跑、调试，但生产环境一堆变量手敲既容易错又会把密码留在命令历史里。生产用 Kubernetes。

### 3.2 Kubernetes：ConfigMap 放普通配置，Secret 放密钥

K8s 把"配置"和"机密"分成两种资源，这是它的核心设计，也是安全红线的落地：

| 资源 | 放什么 | 是否加密 | 类比 |
| --- | --- | --- | --- |
| **ConfigMap** | 普通配置：端口、profile、功能开关、非敏感地址 | 明文存储 | `.env`（可提交的那部分） |
| **Secret** | 敏感信息：数据库密码、Redis 密码、API 密钥、JWT 签名密钥 | base64 编码 + 可加密落盘 | `.env.local`（永远 gitignore） |

> 注意：Secret 默认只是 base64 编码，**不是加密**——base64 是给二进制传输用的编码，谁都能解。真正的防护靠 K8s RBAC 权限控制 + etcd 静态加密 + 不把 Secret 提交进 Git。详见 [安全](/back-end/frontend-backend-guide/32-security)。

**ConfigMap → 环境变量** 是最常见的注入方式。先定义 ConfigMap：

```yaml
# svc-user-config.yaml —— 普通配置
apiVersion: v1
kind: ConfigMap
metadata:
  name: svc-user-config
data:
  SPRING_PROFILES_ACTIVE: "prod"
  MONGO_URI: "mongodb://mongo-prod:27017/aigc"
  REDIS_HOST: "redis-prod"
  APP_QUOTA_DEFAULT_FREE: "20"
```

```yaml
# svc-user-secret.yaml —— 敏感信息（value 是 base64，echo -n 'xxx' | base64 得到）
apiVersion: v1
kind: Secret
metadata:
  name: svc-user-secret
type: Opaque
data:
  REDIS_PASSWORD: UzNjcjN0IQ==          # base64 of "S3cr3t!"
  JWT_SECRET: bXktc3VwZXItc2VjcmV0LWtleQ==
```

然后在 Deployment 里把两者注入成容器环境变量：

```yaml
# svc-user-deployment.yaml（节选 containers 部分）
spec:
  containers:
    - name: svc-user
      image: registry.example.com/svc-user:1.4.0
      ports:
        - containerPort: 8083
      envFrom:
        - configMapRef:
            name: svc-user-config       # ConfigMap 里所有键 → 环境变量
      env:
        - name: REDIS_PASSWORD          # 从 Secret 取单个键，更精细
          valueFrom:
            secretKeyRef:
              name: svc-user-secret
              key: REDIS_PASSWORD
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: svc-user-secret
              key: JWT_SECRET
```

`envFrom` 把整个 ConfigMap 一次性铺成环境变量；Secret 用 `valueFrom.secretKeyRef` 逐个取，更可控。容器一启动，这些变量就和 `docker run -e` 等价地出现在进程环境里，Spring 照样读得到。

排查目标——确认 Pod 里配置真的注入了：

```bash
# 进 Pod 查环境变量（注意 Secret 会以明文显示在 Pod 内部，这是正常的）
kubectl exec deploy/svc-user -- env | grep -E 'SPRING|MONGO|REDIS|JWT'
```

```text
SPRING_PROFILES_ACTIVE=prod
MONGO_URI=mongodb://mongo-prod:27017/aigc
REDIS_HOST=redis-prod
APP_QUOTA_DEFAULT_FREE=20
REDIS_PASSWORD=S3cr3t!
JWT_SECRET=my-super-secret-key
```

怎么读：变量齐全说明 ConfigMap 和 Secret 都正确挂上了。如果 `REDIS_PASSWORD` 缺失，去检查 Secret 名字是否拼对、`secretKeyRef.key` 是否和 Secret 里的键一致——这是最常见的"密码注入失败"原因。

> **Secret 也可以挂成文件**（`volumeMounts` 挂到 `/etc/secrets/`），适合证书、长密钥这类内容。原理和挂环境变量一样，只是落点不同，K8s 实战章有完整示例。

### 3.3 配置中心：动态改配置不重启

上面所有方式有个共同缺点：**改一个配置就得重启服务**（重新 `docker run` 或滚动更新 Pod）。如果只是想把"新画布功能开关"从 false 改成 true，重启整个 `svc-canvas` 显然太重。

**配置中心**（如 **Nacos**、**Apollo**）解决这个问题：配置集中存在配置中心，应用启动时拉取，运行中监听变更——配置一改，应用自动热更新，无需重启。

**前端类比**：这就是后端版的 **Feature Flag 服务**（LaunchDarkly、Unleash）——产品在后台点一下开关，线上行为立刻变，不用重新发版。在我们的项目里，`app.feature.new-canvas-editor` 这类开关、限流阈值、AI 模型参数，特别适合放配置中心，运营随时可调。

一句话原则：**启动期就定、改了也得重启才合理的（端口、profile）放 yml / ConfigMap；运行期希望随时调的（开关、阈值）放配置中心。**

---

## 四、安全红线：密钥绝不进代码库

这是整章最重要的一条，单独拎出来，因为踩了就是事故：

> **密码、密钥、Token、私钥——绝不硬编码进代码、绝不提交进 Git。**

为什么这么严？Git 有完整历史，密钥一旦提交，即使后来删掉，`git log` 里依然查得到；仓库一旦泄露（私库变公库、离职员工带走、CI 日志打印），密钥就等于裸奔。前端同样有这条线：你绝不会把支付密钥写进 `src/`，因为 JS 会打进 bundle 被任何人看到——后端的代码库泄露风险虽不同，但底线一致。

**正确做法对照：**

```java
// ❌ 错误：密钥硬编码，提交后永久留在 Git 历史里
private static final String JWT_SECRET = "my-super-secret-key";

// ✅ 正确：从配置注入，配置值由环境变量 / Secret 提供
@Value("${jwt.secret}")
private String jwtSecret;
```

```yaml
# ✅ application-prod.yml 里只写占位，真实值由 K8s Secret 注入
jwt:
  secret: ${JWT_SECRET}          # 运行时从环境变量取，jar 里不含明文
```

**配套纪律：**
- `.gitignore` 掉 `application-local.yml`、`*.env`、任何含真实密钥的本地文件。
- 万一不小心提交了密钥，删文件不够——必须**立刻轮换该密钥**（作废旧的、生成新的），因为历史已泄露。
- 仓库可接入密钥扫描（如 gitleaks），CI 阶段拦截含密钥的提交。
- 生产密钥统一走 K8s Secret 或专用密钥管理服务（Vault、云厂商 KMS），由运维管理，开发拿不到明文。

更系统的认证、密钥、攻防内容见 [安全](/back-end/frontend-backend-guide/32-security) 和前端侧的 [Web 安全基础](/front-end/the-basics/network-basics/webSafety)。

---

## 五、"配置放哪"对照表

把前面的原则收敛成一张可查的表——拿不准某个配置该放哪时，对着查：

| 配置类型 | 举例 | 放哪 | 为什么 |
| --- | --- | --- | --- |
| 服务端口 | `server.port=8083` | `application.yml` | 固定不变，跟代码走 |
| profile 激活 | `SPRING_PROFILES_ACTIVE=prod` | 环境变量 / ConfigMap | 决定走哪套环境，由部署方指定 |
| 数据库地址 | `MONGO_URI` | ConfigMap（地址不敏感） | 每环境不同，非机密 |
| 数据库/Redis 密码 | `REDIS_PASSWORD` | **Secret** | 敏感，绝不进代码库 |
| API 密钥 / JWT 密钥 | `JWT_SECRET`、AI 平台 key | **Secret** | 敏感，泄露即事故 |
| 功能开关 | `new-canvas-editor` | 配置中心（或 ConfigMap） | 想运行时随时改 → 配置中心 |
| 限流阈值 | 每秒请求上限 | 配置中心 | 需要随流量动态调 |
| 业务默认值 | `quota.default-free=20` | `application.yml` | 默认行为，跟代码走，profile 可覆盖 |

判断口诀：**敏感就进 Secret；每环境不同就进 ConfigMap / 环境变量；想热改就进配置中心；其余固定值留 yml。**

---

## 小结

- **配置与代码分离是铁律（12-factor 第 3 条）**：一份构建产物跑所有环境，差异全靠注入——和前端"一份代码 + 不同 `.env`"完全同构。
- **Spring profile 体系**：`application.yml` 放默认，`application-{env}.yml` 放差异，`spring.profiles.active` 决定激活哪个；优先级从低到高是 yml < profile < 环境变量 < 命令行。
- **读配置两件套**：零散值用 `@Value`，成组结构化配置用 `@ConfigurationProperties`（类型安全，像 zod schema），超过两三个一律用后者。
- **容器注入**：Docker 用 `-e`，K8s 用 ConfigMap（普通配置）+ Secret（密钥），都映射成环境变量被 Spring 读取；想热改不重启则用 Nacos/Apollo 配置中心。
- **安全红线**：密码密钥绝不硬编码、绝不进 Git；代码里只写 `${占位}`，真实值由 Secret 注入；万一泄露立即轮换。

### 自测

1. 同一个配置项 `server.port` 同时出现在 `application.yml`（8080）、环境变量（8083）、命令行 `--server.port=9000` 里，应用实际监听哪个端口？为什么？
2. 一个数据库连接字符串和一个数据库密码，分别应该放进 K8s 的 ConfigMap 还是 Secret？依据是什么？
3. 你想让运营在不重启 `svc-canvas` 的前提下随时切换"新版画布编辑器"功能开关，应该把这个配置放在哪里，用什么类型的工具？

### 下一章

配置就位、服务跑起来后，真正的日常是看它在线上说了什么——下一章 [读懂日志](/back-end/frontend-backend-guide/26-reading-logs) 教你如何阅读和检索后端日志。

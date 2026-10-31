---
prev:
  text: "C3. 登录、安全与数据归属"
  link: "/back-end/frontend-backend-guide/32-security"
next:
  text: "C5. 后端测试与失败验收"
  link: "/back-end/frontend-backend-guide/35-testing"
---

# Session 登录实验：从“所有人共用任务”到可信用户归属

> 场景：初版任务 API 会接受任何人的请求。现在让 alice、bob 登录后只看到自己的任务，并验证猜 ID、伪造 uid、缺少 CSRF token 都不能绕过保护。

前置：[可运行 CRUD](/back-end/frontend-backend-guide/07-build-a-crud-api)、[Cookie、SameSite 与 CSRF](/back-end/frontend-backend-guide/22a-http-and-browser)、[安全基础](/back-end/frontend-backend-guide/32-security)。**本篇是独立的本机 H2 安全变体，不要修改或同时启动初始示例。**

学完能做什么：跑通“取 CSRF → 表单登录 → 更新 Cookie → 重取 CSRF → 操作自己的任务 → 退出”，并用两位用户验证资源授权；不需要 JWT、Docker、MySQL 或微服务。

## 1. 获取工程，先确认真实边界

在线读者可以 [下载完整 Session 实验源码](/backend-guide/session-lab.zip)，解压后进入 `backend-session-lab`；已有本仓库时按下面目录进入，不重复下载。要求 JDK 17、Maven 3.6.3+；后面的 JSON 提取小工具需要 Python 3。首次 Maven 解析依赖需联网。

```bash
cd examples/backend-session-lab
java -version
mvn -version
mvn test
# 下面是读者自行运行的本机服务；结束时 Ctrl+C
mvn spring-boot:run
```

默认只监听 `127.0.0.1:8080`，不要改成公网监听。若初版占用 8080，先在它的终端 Ctrl+C；不要随意杀掉其他进程。

| 演示用户 | 公开口令 |
| --- | --- |
| alice | `alice-local-demo-17` |
| bob | `bob-local-demo-17` |

**这些口令公开、不可生产使用。** Spring Security 6 通过 `PasswordEncoder` 的 BCrypt 哈希校验口令；哈希不能让公开口令变得安全。用户只在内存定义，没有注册、改密、找回、MFA、账号封禁或完整生产密码策略。

真实文件按以下顺序阅读，均在独立目录内：

```text
src/main/java/dev/learning/sessionlab/
├── SecurityConfig.java        # BCrypt 用户、Session、表单登录、CSRF、退出
├── AuthController.java        # 公开取 CSRF，主动加载延迟 token
├── SecurityJson.java          # 过滤器阶段的 JSON 401 / 403
├── RequestLogFilter.java      # 在 Security 之前生成请求 ID
├── TaskController.java        # 从可信 Principal 取用户，不读 uid 头
├── TaskService.java           # 业务、事务、strip 后再检查空白
├── TaskRepository.java        # 每个读写与分页总数都按 owner_id 过滤
├── JsonConfig.java            # 严格 JSON 类型与枚举
└── ApiExceptionHandler.java   # MVC 错误保留 400 / 404 / 405 / 415
src/main/resources/schema.sql  # H2 表与归属索引
src/test/java/dev/learning/sessionlab/SessionSecurityTest.java
```

## 2. 先理解 Cookie 与 CSRF 各证明什么

Cookie 中的 JSESSIONID 关联服务端 Session，认证身份保存在服务端，不是客户端声称的 uid。CSRF token 则校验这次变更请求是否持有同一 Session 的防伪令牌，**它本身不是登录凭据**。

- `GET /api/auth/csrf` 公开返回 `{token,headerName,parameterName}`，读取 token 时创建 Session；响应禁止缓存。
- `POST /api/auth/login` 接受 **表单** username/password，不是 JSON；登录也必须带 CSRF。
- 登录成功返回 `200 {"username":"alice"}`，改变 Session ID 并清除旧 CSRF token；必须保存新 Cookie，再取新 token。
- POST / PUT / DELETE / logout 需要 Cookie 与有效 CSRF；GET 不应修改业务状态。
- 未登录查任务返回 JSON 401，不跳 HTML 登录页；CSRF 失败返回 JSON 403。未登录的变更请求也可能先因缺 CSRF 得到 403，不是所有失败都走同一顺序。

## 3. 准备隔离 Cookie jar

另开终端，下面所有请求始终使用 `127.0.0.1`，不要中途换成 localhost，否则 Cookie 主机范围可能不一致。若本机代理干扰，可为这些本地 curl 单次增加 `--noproxy '*'`。

```bash
BASE='http://127.0.0.1:8080'
umask 077
LAB_DIR=$(mktemp -d)
ALICE_JAR="$LAB_DIR/alice.cookies"
BOB_JAR="$LAB_DIR/bob.cookies"
field() {
  python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))[sys.argv[2]])' "$1" "$2"
}
curl -i "$BASE/api/tasks"
```

预期最后一次请求为 `401`，正文有 `AUTHENTICATION_REQUIRED` 与 requestId，没有跳登录页的 Location。临时目录中的 Cookie 和 token 都是登录材料，勿提交 Git 或分享；`curl -i` / `-v` 还可能打印 Set-Cookie 等敏感输出，分享前脱敏。

## 4. alice：先取 token，再登录，再取新 token

```bash
curl -fsS -c "$ALICE_JAR" "$BASE/api/auth/csrf" -o "$LAB_DIR/alice-csrf.json"
HEADER=$(field "$LAB_DIR/alice-csrf.json" headerName)
ALICE_PRE=$(field "$LAB_DIR/alice-csrf.json" token)
curl -i -b "$ALICE_JAR" -c "$ALICE_JAR" \
  -H "$HEADER: $ALICE_PRE" \
  --data-urlencode 'username=alice' --data-urlencode 'password=alice-local-demo-17' \
  "$BASE/api/auth/login"
```

预期 `200` 和 username=alice。`-b` 读取已有 Cookie，`-c` 保存响应 Cookie；**登录时两者都要有**，否则可能丢掉会话 ID 更新。错误密码应为 `401 BAD_CREDENTIALS`，但漏 token 会先是 `403 CSRF_REJECTED`。

```bash
curl -fsS -b "$ALICE_JAR" -c "$ALICE_JAR" \
  "$BASE/api/auth/csrf" -o "$LAB_DIR/alice-csrf.json"
ALICE_TOKEN=$(field "$LAB_DIR/alice-csrf.json" token)
```

从现在起用 ALICE_TOKEN，不再用登录前的 ALICE_PRE。不要靠关掉 CSRF 让旧 token “继续可用”。

## 5. 创建、读取、更新自己的任务

```bash
curl -sS -b "$ALICE_JAR" -H "$HEADER: $ALICE_TOKEN" \
  -H 'Content-Type: application/json' -d '{"title":"Alice 的任务"}' \
  -D "$LAB_DIR/create.headers" -o "$LAB_DIR/task.json" -w 'HTTP %{http_code}\n' \
  "$BASE/api/tasks"
TASK_ID=$(field "$LAB_DIR/task.json" id)
curl -i -b "$ALICE_JAR" "$BASE/api/tasks/$TASK_ID"
curl -i -b "$ALICE_JAR" "$BASE/api/tasks?page=0&size=20"
curl -i -X PUT -b "$ALICE_JAR" -H "$HEADER: $ALICE_TOKEN" \
  -H 'Content-Type: application/json' -d '{"title":"Alice 的任务","status":"DONE"}' \
  "$BASE/api/tasks/$TASK_ID"
```

预期依次为 `201 → 200 → 200 → 200`。创建的 UUID 写入 TASK_ID，Location 保存在 create.headers；响应仍是 id/title/status/createdAt，不暴露 owner。状态只有 TODO/DOING/DONE；分页从 0 开始，size 为 1～100，page 上限 10000，总数也只属于当前用户。

## 6. bob：另一份 Cookie jar，验证越权与列表隔离

```bash
curl -fsS -c "$BOB_JAR" "$BASE/api/auth/csrf" -o "$LAB_DIR/bob-csrf.json"
BOB_PRE=$(field "$LAB_DIR/bob-csrf.json" token)
curl -i -b "$BOB_JAR" -c "$BOB_JAR" -H "$HEADER: $BOB_PRE" \
  --data-urlencode 'username=bob' --data-urlencode 'password=bob-local-demo-17' \
  "$BASE/api/auth/login"
curl -fsS -b "$BOB_JAR" -c "$BOB_JAR" \
  "$BASE/api/auth/csrf" -o "$LAB_DIR/bob-csrf.json"
BOB_TOKEN=$(field "$LAB_DIR/bob-csrf.json" token)
curl -i -b "$BOB_JAR" -H 'uid: alice' "$BASE/api/tasks/$TASK_ID"
curl -i -X PUT -b "$BOB_JAR" -H "$HEADER: $BOB_TOKEN" \
  -H 'Content-Type: application/json' -d '{"title":"偷改","status":"TODO"}' \
  "$BASE/api/tasks/$TASK_ID"
curl -i -X DELETE -b "$BOB_JAR" -H "$HEADER: $BOB_TOKEN" "$BASE/api/tasks/$TASK_ID"
```

登录为 200；后三次均应 `404 TASK_NOT_FOUND`，Alice 的标题与 DONE 状态不变。uid 头不是身份；JSON ownerId、owner_id 和查询参数也不能改变可信 principal。仓储直接用 `WHERE owner_id=? AND id=?`，不是“先查出所有任务，再让前端隐藏”。

```bash
curl -i -b "$BOB_JAR" -H "$HEADER: $BOB_TOKEN" \
  -H 'Content-Type: application/json' -d '{"title":"Bob 的任务"}' "$BASE/api/tasks"
curl -i -b "$BOB_JAR" "$BASE/api/tasks?ownerId=alice"
curl -i -b "$ALICE_JAR" "$BASE/api/tasks"
```

预期创建 201，两份列表各为自己的 1 条任务、total=1。即使 Bob 传 ownerId=alice，也不会看到 Alice 的任务。实验以未提前创建其他任务为前提，重跑后数量增加属于预期。

## 7. 验证 CSRF、删除与退出

```bash
# 有 Cookie 但不带 CSRF，不能创建；预期 403，列表不增加
curl -i -b "$ALICE_JAR" -H 'Content-Type: application/json' \
  -d '{"title":"缺少防伪令牌"}' "$BASE/api/tasks"
# 只有自己的任务能删除；预期 204 无正文
curl -i -X DELETE -b "$ALICE_JAR" -H "$HEADER: $ALICE_TOKEN" "$BASE/api/tasks/$TASK_ID"
# 退出本身也是受 CSRF 保护的 POST；保存响应以清除 jar 中的 Cookie
curl -i -X POST -b "$ALICE_JAR" -c "$ALICE_JAR" \
  -H "$HEADER: $ALICE_TOKEN" "$BASE/api/auth/logout"
curl -i -b "$ALICE_JAR" "$BASE/api/tasks"
```

预期 `403 → 204 → 204 → 401`。退出使服务端 Session 失效，不只是删除浏览器 Cookie；Bob 的会话不受影响。安全变体中再次删除同一 ID 返回 404，与初版重复删除 204 不同：不存在与他人资源统一不可见，状态效果仍幂等。

## 8. 自动测试证明了什么

`mvn test` 运行真实的 16 个集成测试方法；它们通过取 token 和表单登录进入过滤器链，不使用伪身份注解或 CSRF 测试捷径。此次编写时已在隔离 JDK 17 实际执行，结果为 **16 tests、0 failures、0 errors、0 skipped**。

| 检查范围 | 关键证据 |
| --- | --- |
| 登录边界 | 未登录 JSON401、错误密码 / 未知用户401、缺 CSRF 的登录403、BCrypt校验 |
| Session 与 CSRF | 登录换ID，旧token拒绝，新token可用；跨Session token拒绝；所有变更和退出不能漏token |
| 用户归属 | 越权读写删404且数据不变；分页items与total均隔离；伪造uid和owner字段无效 |
| 退出 | Session已invalid，Cookie删除，旧Session访问401，任务并非被退出操作删除 |
| 原接口约束 | 严格JSON、数字枚举400；EM SPACE在strip后被拒绝且不写空标题；405保留Allow，415等语义不变 |

测试手工构造**不绑定外部数据库属性**的随机内存 H2 数据源，初始化和每次清表前校验 JDBC metadata URL 必须为本次 `jdbc:h2:mem:...`；不满足即停止，避免继承用户真实 DB 环境。不能删除这个保护，也不能把失败测试改成连生产库。

MockMvc 不实际监听网络，不能证明浏览器 Cookie / SameSite、真实 TLS、反向代理或生产 MySQL 都可用。本轮维护另在临时本机端口启动实际 JAR，逐段执行了本页的 curl 流程，登录、双用户隔离、CSRF 拒绝和退出后的 17 个可见状态均符合预期；临时进程已停止。这仍不是浏览器或生产部署验证。

## 9. 不要把实验当生产脚手架

- 数据与 Session 均在进程内存，重启后失效；没有持久用户、共享 Session、多设备退出或生产会话管理。
- 演示用户名暂作 owner_id；真实系统应使用不可变用户 ID，设计账号生命周期及审计。
- 本机 HTTP Cookie 为 HttpOnly、SameSite=Lax；生产 HTTPS 还要 Secure、可信代理、合适超时和风险评估。SameSite 不代替 CSRF，BCrypt 不代替强口令和登录限流。
- 没有跨域放开；前端接入时优先同源入口，不要无条件回显 Origin。也没有 POST 幂等键、更新版本检查、备份恢复或依赖漏洞评估。

## 自测与参考答案

1. 为什么登录前取了 token，登录后还要取？**登录刷新会话安全状态，旧 CSRF token 已清除；还必须保存 Session ID 更新后的 Cookie。**
2. Bob 持有有效 Cookie 和 token，为什么改 Alice 任务仍失败？**认证与 CSRF 不等于资源授权，SQL 的 owner 条件由可信 principal 决定。**
3. 把 uid 头写成 alice，或删除本地 Cookie，就能改变 / 撤销服务器身份吗？**前者不能伪造认证；后者只丢了本地材料，可靠退出要让服务端 Session 失效。**

继续 [测试方法](/back-end/frontend-backend-guide/35-testing) 扩展失败场景；回看 [安全基础](/back-end/frontend-backend-guide/32-security) 理解权限边界，再进入 [综合实战](/back-end/frontend-backend-guide/38-capstone-feature-to-prod)，补齐持久用户、生产认证策略与部署安全。

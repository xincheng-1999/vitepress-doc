---
prev:
  text: "C3. 登录、安全与数据归属"
  link: "/back-end/frontend-backend-guide/32-security"
next:
  text: "C5. 后端测试与失败验收"
  link: "/back-end/frontend-backend-guide/35-testing"
---

# Session 登录实验：Alice 登录以后，为什么仍不能操作 Bob 的任务

初版任务 API 没有区分用户，谁调用接口，看到的都是同一批任务。现在我们启动一个独立的安全实验，让 Alice 和 Bob 分别登录、各自创建任务，再让 Bob 拿着合法会话访问 Alice 的任务，看看后台会怎样处理。

先读过 [可运行 CRUD](/back-end/frontend-backend-guide/07-build-a-crud-api)、[Cookie、SameSite 与 CSRF](/back-end/frontend-backend-guide/22a-http-and-browser) 和 [安全基础](/back-end/frontend-backend-guide/32-security)，就可以按下面顺序操作。不需要 JWT、Docker、MySQL 或微服务。

**本篇使用独立的本机 H2 安全变体。初版工程继续保留，不要把两套配置混在一起，也不要同时启动它们占用同一端口。**

## 1. 先启动带登录保护的实验工程

在线读者可以 [下载完整 Session 实验源码](/backend-guide/session-lab.zip)，解压后进入 `backend-session-lab`；已有仓库则直接使用下面的目录。先准备 JDK 17、Maven 3.6.3+ 和 Python 3，后者用于从响应 JSON 中提取 token 与任务 ID。首次 Maven 解析依赖需要联网。

```bash
cd examples/backend-session-lab
java -version
mvn -version
mvn test
# 下面是读者自行运行的本机服务；结束时 Ctrl+C
mvn spring-boot:run
```

服务默认只监听 `127.0.0.1:8080`。如果初版正在使用 8080，先回它的终端按 Ctrl+C，再启动本工程，不随意结束其他进程。这里使用公开账号和本机配置，**不要改成公网监听**。

| 演示用户 | 公开口令 |
| --- | --- |
| alice | `alice-local-demo-17` |
| bob | `bob-local-demo-17` |

Spring Security 6 使用 `PasswordEncoder` 和 BCrypt 校验口令。账号只在内存中定义，口令也公开，不能拿去生产；保存成哈希不会让公开口令重新变成秘密。本实验暂不提供注册、改密、找回、MFA、封禁和完整密码策略。

先让服务保持运行，后面另开终端发请求。需要对应源码时，可以沿着安全配置、认证入口、任务接口、数据库访问的顺序阅读：

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

## 2. 登录之前，先分清要保存的两样东西

本实验的 Cookie 中有 JSESSIONID，服务器用它找到 Session。登录身份保存在服务器，不由请求里的 uid 决定。CSRF token 则随同一个 Session 保存，用于校验变更请求携带的防伪材料；只拿到 token，还没有通过用户名和密码认证。

因此，第一次先访问公开的 `GET /api/auth/csrf`。接口返回 `{token,headerName,parameterName}`，加载 token 时会创建 Session，并禁止缓存响应。接着把 Cookie 和 token 一起带到 `POST /api/auth/login`，以**表单**提交 username 和 password，而不是发 JSON。

成功登录后，框架会更换 Session ID，并清除登录前的 CSRF token。我们需要先保存响应中的新 Cookie，再请求一次 CSRF 接口，后面的 POST、PUT、DELETE 和退出才能使用新 token。这里多取一次不是重复劳动，而是会话安全状态已经变化。

未登录的查询返回 JSON 401，不跳 HTML 登录页。CSRF 失败返回 JSON 403；变更请求可能先过 CSRF 检查，因此没登录又没 token 时，也可能先得到 403。后面按每一步的预期结果判断，不把所有拒绝都当成同一种错误。

## 3. 给 Alice 和 Bob 分别准备 Cookie 文件

另开终端，下面代码在同一个终端按顺序执行，变量需要一直保留。两个用户各用一份 Cookie jar，也就是保存 Cookie 的文件，避免后一次登录覆盖前一个人的会话。

地址始终使用 `127.0.0.1`，不要中途改为 localhost，它们的 Cookie 主机范围可能不同。如果本机代理干扰，可以给这些本地 curl 单次增加 `--noproxy '*'`。

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

最后的请求没有会话，应得到 `401`，正文包含 `AUTHENTICATION_REQUIRED` 和 requestId，也没有跳转登录页的 Location。服务正常运行却拒绝这次请求，正是我们想看到的起点。

`LAB_DIR` 是本机临时目录，umask 限制新文件权限。接下来里面会保存 Cookie 和 token，把它们当作登录材料，不提交或分享。`curl -i`、`-v` 也可能打印 Set-Cookie，求助时先脱敏。

## 4. Alice：用登录前的 token 登录，再换成登录后的 token

先取得匿名 Session 的 Cookie 和 token，再带着它们提交登录表单：

```bash
curl -fsS -c "$ALICE_JAR" "$BASE/api/auth/csrf" -o "$LAB_DIR/alice-csrf.json"
HEADER=$(field "$LAB_DIR/alice-csrf.json" headerName)
ALICE_PRE=$(field "$LAB_DIR/alice-csrf.json" token)
curl -i -b "$ALICE_JAR" -c "$ALICE_JAR" \
  -H "$HEADER: $ALICE_PRE" \
  --data-urlencode 'username=alice' --data-urlencode 'password=alice-local-demo-17' \
  "$BASE/api/auth/login"
```

登录应返回 `200`，正文 username 为 alice。这里 `-b` 负责发送已有 Cookie，`-c` 负责保存响应更新后的 Cookie，**两者缺一不可**。只发送不保存，就可能继续拿旧会话 ID 发后续请求。

如果带着正确 CSRF 却输错密码，应得到 `401 BAD_CREDENTIALS`；如果漏了 token，则可能先被 CSRF 拒绝，得到 `403 CSRF_REJECTED`。成功后继续取新 token：

```bash
curl -fsS -b "$ALICE_JAR" -c "$ALICE_JAR" \
  "$BASE/api/auth/csrf" -o "$LAB_DIR/alice-csrf.json"
ALICE_TOKEN=$(field "$LAB_DIR/alice-csrf.json" token)
```

这次把结果保存为 ALICE_TOKEN。后续写请求使用它，登录前的 ALICE_PRE 已经作废；如果这里失败，应检查 Cookie 是否保存和新 token 是否取到，不关闭 CSRF 来绕过问题。

## 5. 用 Alice 的会话创建并修改任务

现在服务器已经知道请求者是 Alice。创建响应会返回真实 UUID，下面提取它作为 TASK_ID，再查询、翻页和更新，不手工猜一个 ID：

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

这四次请求应依次是 `201 → 200 → 200 → 200`。创建响应的 Location 另存于 create.headers，资源正文仍只有 id、title、status、createdAt，不暴露 owner。虽然请求里没有 owner，数据库记录已由服务端 principal 确定为 Alice 所有。

更新使用 TODO、DOING、DONE 三个字符串状态；分页从 0 开始，size 为 1～100，page 不超过 10000。列表与 total 都应只统计当前用户，下一步用 Bob 检查这一点。

## 6. Bob 已经登录，仍不能访问 Alice 的任务

使用 Bob 自己的 Cookie 文件，同样完成登录和 token 更新。然后拿刚才 Alice 的 TASK_ID，分别尝试读取、修改和删除：

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

Bob 登录返回 200，但后三次都应是 `404 TASK_NOT_FOUND`。Bob 的 Cookie 和 CSRF token 都有效，失败的原因是任务不属于他。即使在头里写 `uid: alice`，也改变不了经过认证的 principal；JSON 中的 ownerId、owner_id 和查询参数同样不能替换身份。

仓储把 principal 对应的用户写进 `WHERE owner_id=? AND id=?`。查不到可见记录就返回 404，写不匹配也不修改数据，Alice 的标题和 DONE 状态应保持不变。接着让 Bob 创建自己的任务，再分别查列表：

```bash
curl -i -b "$BOB_JAR" -H "$HEADER: $BOB_TOKEN" \
  -H 'Content-Type: application/json' -d '{"title":"Bob 的任务"}' "$BASE/api/tasks"
curl -i -b "$BOB_JAR" "$BASE/api/tasks?ownerId=alice"
curl -i -b "$ALICE_JAR" "$BASE/api/tasks"
```

创建应为 201。首次按本篇执行时，两份列表各有自己的 1 条任务，total=1；Bob 带 ownerId=alice，也只得到 Bob 的那条。既要看 items，也要看 total，不能只把列表隐藏了，却通过总数泄露别人的数据。重跑时已创建的任务还在，数量增加是正常现象。

## 7. 回到 Alice，检查缺 token 和退出后的请求

Alice 仍处于登录状态。先省略 token 创建任务，再带正确 token 删除自己的任务，最后退出并查询。这样能把 CSRF 拒绝、成功操作和会话失效区分开：

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

预期是 `403 → 204 → 204 → 401`。第一步没增加任务，第二步删除成功且无正文；退出让服务端 Session 失效，同时用 `-c` 保存清除 Cookie 的响应，最后才会访问失败。Bob 使用另一份会话，不受影响。

安全变体再次删除同一 ID 返回 404，初版则重复返回 204。这里刻意统一隐藏不存在和他人资源，重复删除的状态效果仍然相同，不因状态码变化而失去幂等性。

## 8. 把刚才的过程交给自动测试反复检查

工程已有 **16 个集成测试方法**，通过真正的取 token 和表单登录走过过滤器链，没有用伪身份注解或 CSRF 测试捷径。正常运行 `mvn test` 时，应看到 **16 tests、0 failures、0 errors、0 skipped**。方法数量与手工请求次数不是一回事：一个方法可能检查多种输入和多次请求。

测试除了检查响应，也会查数据库和会话状态。比如 Bob 越权时不仅要返回 404，还要确保 Alice 的记录没变；退出时不仅要返回 204，还要检查旧 Session 已 invalid，任务并未被退出操作删除。

| 检查范围 | 测试检查的具体结果 |
| --- | --- |
| 登录 | 未登录得到 JSON 401，错误密码和未知用户为 401，缺 CSRF 登录为 403，密码由 BCrypt 验证 |
| 会话与 CSRF | 登录更换 ID，旧 token 失败、新 token 成功，跨 Session token 失败，变更和退出都要 token |
| 归属 | 越权读写删为 404 且数据不变，items 与 total 各自隔离，伪造 uid 和 owner 无效 |
| 退出 | Session 失效、Cookie 清除，旧会话为 401，已有任务保留 |
| 原接口规则 | 严格 JSON 和数字枚举为 400，EM SPACE 经 strip 后仍被拒绝，405 保留 Allow，415 等语义保留 |

运行测试还要确保清理不会触碰真实数据。两工程的安全做法一致：手工创建**不绑定外部数据库属性**的随机内存 H2 数据源，初始化和每次清表前，都验证 JDBC metadata URL 精确对应本次 `jdbc:h2:mem:...`；不满足就停止。不要移除这个保护，也不要把失败测试改成连接生产或共享业务库。

MockMvc 没有实际监听网络，所以这些测试不覆盖浏览器的 Cookie 与 SameSite 行为、真实 TLS、代理或生产 MySQL。上面的 curl 流程另有 **17 个可见 HTTP 状态**供逐项核对，覆盖登录、双用户隔离、CSRF 拒绝和退出；它也只是本机 HTTP 检查，不是浏览器或生产部署验证。实际结果以自己运行时的响应和测试报告为准。

## 按需补充：接入真实产品之前，还缺什么

本实验把数据和 Session 都放在进程内存，重启后失效；没有持久用户、共享 Session、多设备退出等会话管理。演示用户名暂时充当 owner_id，真实产品应换成不可变用户 ID，并设计账号生命周期和审计。

Cookie 使用适合本机 HTTP 的 HttpOnly、SameSite=Lax。生产 HTTPS 还要 Secure、可信代理、超时策略与风险评估；SameSite 只是配合 CSRF 使用，BCrypt 也还需要强口令和登录限流。前端接入时优先同源入口，本工程没有放开跨域，不无条件回显 Origin。

请求幂等键、更新版本检查、备份恢复和依赖漏洞评估也还没有提供。需要上线时，可回看 [安全基础](/back-end/frontend-backend-guide/32-security)，再在 [综合实战](/back-end/frontend-backend-guide/38-capstone-feature-to-prod) 中补齐，而不是把这两个公开用户直接换个密码就部署。

现在可以用三个具体结果检查自己是否理解：登录后必须保存新 Cookie、重新取 token；Bob 的登录与 CSRF 都有效，仍被 owner 条件挡住；Alice 退出后，服务端旧会话真的失效。这三件事分别由不同位置负责。

主线下一篇：[C5 · 后端测试与失败验收](/back-end/frontend-backend-guide/35-testing)。继续用 [测试方法](/back-end/frontend-backend-guide/35-testing) 看这些请求怎样变成可重复运行的检查。

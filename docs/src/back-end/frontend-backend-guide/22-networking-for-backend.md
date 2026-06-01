# 后端视角网络基础

> 前端的你，每天都在 Network 面板里看请求：有的 `pending` 转圈圈，有的红色 `failed`，有的 `200` 秒回。
> **这一章干的事，就是带你钻到 Network 面板背面去——那一行 `failed` 到底卡在 TCP、TLS、DNS 还是防火墙哪一层，后端怎么用命令把它定位出来。**

上一章 [Linux 服务器必会](/back-end/frontend-backend-guide/21-linux-server-essentials) 教你在服务器上活动。这一章承接你已经会的 HTTP 知识（如果想复习 HTTP 报文本身和 HTTPS 安全细节，可以看前端篇的 [Web 安全基础](/front-end/the-basics/network-basics/webSafety)），从后端排错的角度重新过一遍网络：你不需要背 TCP/IP 七层模型，你需要的是——**当 `svc-canvas` 调 `svc-ai` 报错时，能一眼分清这是"端口没开"、"网络不通"还是"域名解析失败"**，并知道下一条该敲什么命令。

## 先建立一张"前端报错 → 后端成因"对照表

把后端网络层学了有什么用？最直接的回报是：你在前端见过的那些网络报错，后端这一层就是它们的成因。先把这张表立在这里，本章后面会逐个拆开。

| 前端 / 浏览器看到的现象 | 真正的成因（在后端网络层） | 本章对应小节 |
| --- | --- | --- |
| 请求一直 `pending`，最后超时 | 网络不通 / 防火墙丢包 / 对端处理慢，TCP 握手或响应迟迟不来 | TCP 握手、防火墙、`telnet` |
| `net::ERR_CONNECTION_REFUSED` | 目标 IP 可达，但那个端口**没有进程在 listen** | 端口与监听、`connection refused` |
| `net::ERR_NAME_NOT_RESOLVED` | DNS 解析不出域名对应的 IP | DNS、`dig`/`nslookup` |
| `502 Bad Gateway` | 网关把请求转给后端，**后端没给出有效响应**（进程挂了 / 连不上） | 状态码语义、502 vs 504 |
| `504 Gateway Timeout` | 网关转给后端，后端**收到了但太慢**，网关等不及了 | 状态码语义、502 vs 504 |
| `503 Service Unavailable` | 服务在线但暂时不可用（过载 / 熔断 / 还没启动完） | 状态码语义 |

> 前端类比：你以前遇到 `pending` 和 `failed` 时，能做的基本只有"刷新试试"、"问后端"。学完这章，"问后端"里的那个后端就是你——而且你手上有 `curl -v`、`telnet`、`dig` 这几把能直接看穿每一层的工具。

## TCP 基础：连接是怎么建立的

HTTP 跑在 TCP 之上。你发 `axios.get()` 之前，浏览器其实先要和服务器建好一条 TCP 连接。理解 TCP，是理解"连不上"和"慢"的前提。

### 三次握手与四次挥手，一句话版

```text
三次握手（建立连接，open 一条管道）：
  客户端 ──SYN（我想连你）──────▶ 服务端
  客户端 ◀─SYN+ACK（行，我也想连你）─ 服务端
  客户端 ──ACK（成交）──────────▶ 服务端     ← 连接建立，开始传数据

四次挥手（关闭连接，close 这条管道）：
  双方各自发一次"我说完了(FIN)"、对方回一次"知道了(ACK)"，
  你来我往四下，连接才彻底关掉。
```

> 前端类比：三次握手就像 WebSocket 连接的 `onopen` 之前那段建链过程——必须先确认双方都在线、都能收发，才开始正式通信。你平时 `axios` 一行就发出去了，是因为这套握手被底层悄悄做完了。

排错时你只需要记住一个直觉：**握手做不完，连接就建不起来，请求自然卡死或失败。** 后面 `telnet host port` 测的就是"这三次握手能不能成功"。

### 端口与监听：一个进程占一个端口

这是后端最重要的网络概念之一，也是前端最陌生的：

- 一台服务器有一个 IP，但能跑很多个服务，**靠端口区分**。`svc-user` 听 `8081`，`svc-ai` 听 `8082`，`svc-gateway` 听 `8080`。
- 一个端口在同一时刻**只能被一个进程 listen（监听）**。你启动 `svc-user` 时它会 `listen` 在 8081，如果 8081 已经被别的进程占了，启动就报 `Address already in use`。

> 前端类比：本地起多个项目时 `localhost:3000`、`localhost:5173`、`localhost:8080` 各跑一个，端口冲突时 Vite 提示 `Port 3000 is in use`——后端服务器上是一模一样的规则，只是把 `localhost` 换成了服务器 IP，把前端 dev server 换成了 Java 进程。

**目标：看服务器上谁在听哪个端口。**

```bash
# -t TCP, -l 只看 listen 状态, -n 不做名字解析（更快、直接看 IP:端口）, -p 显示进程
ss -tlnp | grep 8081
```

预期输出样例：

```text
LISTEN 0  511  *:8081  *:*  users:(("java",pid=4123,fd=42))
```

怎么读这段输出：

- `LISTEN`：这个端口确实有进程在监听。
- `*:8081`：监听在所有网卡的 8081 端口（`*` 表示不限 IP；如果是 `127.0.0.1:8081` 就只监听本机，外部连不进来——这是个常见坑）。
- `users:(("java",pid=4123,...))`：监听者是 PID 4123 的 java 进程，也就是你的 `svc-user`。

结论：端口有人 listen，服务正常起来了。如果这条命令**什么都没输出**，说明没人监听 8081，外部来连必然得到 `connection refused`。

### 长连接 keep-alive：别每次都重新握手

每次握手都要一来一回耗时间。HTTP/1.1 默认开启 **keep-alive**：一条 TCP 连接处理完一个请求后不立刻关闭，复用给下一个请求。

> 前端类比：这正是为什么浏览器对同一域名能"很快地连续发好几个请求"——底层复用了同一条连接，省掉了重复握手。后端的 Feign / HTTP 客户端、数据库连接、Redis 连接，全都靠"连接复用"来扛高并发。

在后端，这个直觉直接关联到 [连接池](/back-end/frontend-backend-guide/13-connection-pools)：连接池干的就是"把建好的连接攒起来反复用，不要用一次扔一次"。`svc-canvas` 通过 Feign 频繁调 `svc-ai`，底层就是复用着少量长连接，而不是每个请求都三次握手一遍。

## HTTP / HTTPS：状态码的后端语义才是重点

请求行、请求头、请求体、响应这套结构你早就熟了。这里只补两块对后端排错最关键的内容：**状态码到底在告诉你哪一层出了问题**，以及 HTTPS / HTTP/2 的一句话认知。

### 状态码：分清"谁的错"

前端常把状态码粗暴分成"2xx 成功、4xx 我错了、5xx 后端崩了"。后端排错要更精细——尤其要分清**是业务服务自己返回的，还是网关替它返回的**。

| 状态码 | 后端语义 | 在本项目里什么情况会出现 |
| --- | --- | --- |
| `400 Bad Request` | 请求本身格式 / 参数不合法，服务能收到但读不懂 | 提交生图任务时 `prompt` 字段缺失、JSON 格式错 |
| `401 Unauthorized` | **没认证** / token 无效或过期 | 请求头没带 token，或 token 过期，`svc-gateway` 调 `svc-auth` 校验没过 |
| `403 Forbidden` | **认证过了，但没权限** | 普通用户访问管理员接口；或配额已用完被业务拒绝 |
| `404 Not Found` | 路径不存在 | 网关路由没配这个 path，或 URL 拼错 |
| `500 Internal Server Error` | 业务服务自己抛了未捕获异常 | `svc-ai` 里空指针、`svc-user` 扣配额时数据库报错 |
| `502 Bad Gateway` | **网关连到了，但后端没给有效响应** | `svc-ai` 进程挂了 / 还没起来，网关转发过去拿不到东西 |
| `503 Service Unavailable` | 服务暂时不可用（过载 / 熔断 / 启动中） | 触发了网关限流熔断，或服务正在滚动重启 |
| `504 Gateway Timeout` | **网关连到了后端，但后端太慢，超时了** | `svc-ai` 出图太久超过网关超时阈值，生图高峰期常见 |

> 前端类比：`401` vs `403` 这一对，对应你写路由守卫的两种分支——`401` 是"你没登录，去登录页"，`403` 是"你登录了但这页不是你能看的"。zod 校验失败该返回的，正是 `400`：参数没通过 schema 校验。

#### 502 vs 504：这是面试和排错的高频混淆点

这两个都带"Gateway"，都意味着问题出在**网关到后端这一段**，但成因截然不同，记住这张图就不会再混：

```text
正常：  客户端 ──▶ svc-gateway ──▶ svc-ai ──响应──▶ gateway ──▶ 客户端

502 Bad Gateway（连不上 / 拿不到响应）：
        客户端 ──▶ svc-gateway ──✗ svc-ai（进程挂了 / 端口没人听 / 直接断开）
        网关："我转过去了，但对方根本没给我有效响应。"

504 Gateway Timeout（连上了，但等太久）：
        客户端 ──▶ svc-gateway ──▶ svc-ai（收到了，在吭哧吭哧出图…）
                              ◀── 等了 30s 还没回 ──── 网关："不等了，超时。"
        网关："对方活着、也收到了，就是太慢，我等不及了。"
```

一句话区分：**502 = 后端没（来得及）回应；504 = 后端在回但太慢，网关主动放弃等待。**

排错方向也因此完全不同：

- 遇到 **502**：先看 `svc-ai` 进程还在不在、端口还 listen 不 listen（`ss -tlnp`）、是不是刚崩溃重启。多半是**后端挂了或连不上**。
- 遇到 **504**：进程是活的，去看 `svc-ai` 这个接口为什么慢——慢 SQL、外部模型 API 卡住、线程池打满。这是**性能 / 超时问题**，连到 [性能与并发](/back-end/frontend-backend-guide/31-performance-concurrency)。

### HTTPS / TLS：一句话认知

HTTPS = HTTP + TLS 加密。在 TCP 三次握手之后、发 HTTP 数据之前，多一步 **TLS 握手**：双方协商加密算法、服务端出示证书证明"我确实是这个域名"，之后所有数据加密传输。

> 前端类比：你点开浏览器地址栏那把小锁看到的证书信息，就是 TLS 握手里服务端出示的那张证书。后端排错时，证书过期 / 域名不匹配会导致 TLS 握手失败，`curl` 会明确报 `SSL certificate problem`——这跟"端口不通"是两类问题，别混。

后端通常的实践是：**TLS 终止在网关或更前面的负载均衡 / Ingress**，内部服务之间（`svc-gateway` ↔ `svc-ai`）跑明文 HTTP，因为它们在受信任的内网。所以你在内网用 `curl http://svc-ai:8082` 而不是 `https`，是正常的，不是不安全。

### HTTP/2：一句话认知

HTTP/2 在一条 TCP 连接上**多路复用**多个请求（同时跑、互不阻塞），解决了 HTTP/1.1 "一条连接一次只能处理一个请求、后面的得排队（队头阻塞）"的问题。

> 前端类比：你应该听过"HTTP/2 下不必再做雪碧图 / 域名分片优化"——就是因为多路复用让并发请求不再受连接数限制。后端这边，gRPC、部分服务间通信也跑在 HTTP/2 上图个高效，但对你日常排错，知道"它是更高效的连接复用"就够了。

## DNS：域名是怎么变成 IP 的

TCP 连接需要的是 **IP + 端口**，但你写代码时给的往往是域名（`api.example.com`）或服务名（`svc-ai`）。把名字翻译成 IP 的过程，就是 DNS 解析。

```text
你的程序要连 svc-ai
        │
        ▼
1. 先查本机 /etc/hosts 有没有写死的映射  ──有──▶ 直接用
        │ 没有
        ▼
2. 问 DNS 服务器："svc-ai 的 IP 是多少？"
        │
        ▼
3. DNS 返回 IP（比如 10.0.0.7）
        │
        ▼
4. 程序拿 10.0.0.7:8082 去发起 TCP 连接
```

> 前端类比：你从来不在 `axios` 里写后端的 IP，只写域名——DNS 帮你把域名解析成 IP，你毫无感知。后端世界完全一样，只是多了"内网域名"这种东西。

### 服务发现里的 DNS 直连（呼应架构章）

[项目整体架构](/back-end/frontend-backend-guide/02-architecture-overview) 里讲过：本项目跑在 Kubernetes 上，用 **DNS 直连**做服务发现。`svc-canvas` 配置里写 `http://svc-ai:8082`，这个 `svc-ai` 就是个**内网域名**，由 K8s 内置 DNS 自动解析到某个健康的 `svc-ai` Pod 的 IP。

所以本章学的 DNS 排查，直接就是排查"为什么 `svc-canvas` 连不上 `svc-ai`"的一环——如果 DNS 解析不出 `svc-ai`，你会得到 `unknown host` 错误，跟"端口不通"是两码事。

### /etc/hosts：本地强制改路由

`/etc/hosts` 是本机一张"DNS 小抄"，优先级高于真正的 DNS 查询。你可以用它把某个域名强行指到某个 IP。

> 前端类比：跟你本地开发时改 hosts 把 `api.test.com` 指到 `127.0.0.1` 调本地 mock 后端，是同一个文件、同一个用法。

**目标：临时把 `svc-ai` 指到一台指定机器做联调验证。**

```bash
# 查看当前 hosts
cat /etc/hosts
# 追加一条映射（需要 root）
echo "10.0.0.99  svc-ai" | sudo tee -a /etc/hosts
```

预期输出样例：

```text
127.0.0.1   localhost
10.0.0.99   svc-ai
```

结论：之后本机所有对 `svc-ai` 的访问都会去 `10.0.0.99`，绕过正常 DNS。排查"是不是 DNS 解析错了"时，这是个快速隔离手段；但**记得验证完删掉**，否则会变成下一个排查者的噩梦。

## 防火墙 / 安全组：端口开没开

服务起来了、端口也 listen 了，外面还是连不上——很可能是**防火墙或云安全组没放行这个端口**。这是云上最常见的"明明服务是好的却连不上"的坑。

```text
外部请求 ──▶ [云安全组规则] ──▶ [服务器本机防火墙] ──▶ 进程监听的端口
                  │                    │
              没放行就丢包         没放行就丢包
              （表现为 timeout）   （表现为 timeout 或 refused）
```

两层都要放行：

- **云安全组**（阿里云 / AWS Security Group 等）：在云控制台配，是"机房门口的保安"。规则写"允许 0.0.0.0/0 访问 TCP 8080"之类。
- **本机防火墙**（`firewalld` / `iptables` / `ufw`）：服务器操作系统内部的一层。

> 前端类比：这层没有直接的前端类比，最接近的是"你接口写好了、本地能跑，但部署后线上 404 / 连不上，结果发现是 Nginx / CDN 的转发规则没配"——同样是"代码没问题，是它前面的关卡没放行"。

关键直觉：**端口没放行，表现通常是 timeout（包被默默丢弃，连握手都开始不了），而不是 connection refused。** 这是区分"防火墙问题"和"端口没人听"的重要线索，下一节展开。

## 三种典型连接错误：一眼分清是哪一类

这是本章最该刻进肌肉记忆的部分。后端连不上对方时，错误几乎逃不出这三类，**每一类指向完全不同的排查方向**：

```text
┌─────────────────────┬──────────────────────────┬─────────────────────────┐
│ 错误现象            │ 含义                      │ 第一步该查什么          │
├─────────────────────┼──────────────────────────┼─────────────────────────┤
│ Connection refused  │ IP 通、能到机器，但那个   │ 进程起了吗？端口 listen │
│                     │ 端口没有进程在 listen     │ 了吗？(ss -tlnp)        │
├─────────────────────┼──────────────────────────┼─────────────────────────┤
│ Connection timed out│ 包发出去石沉大海。网络不通│ 防火墙 / 安全组放行了吗？│
│ (timeout)           │ 或防火墙把包丢了          │ 网段 / 路由通吗？(ping) │
├─────────────────────┼──────────────────────────┼─────────────────────────┤
│ Unknown host /      │ 域名 / 服务名根本解析不出 │ DNS 配置 / 服务名拼对了 │
│ Name not resolved   │ IP 来，连第一步都没开始   │ 吗？(dig / nslookup)    │
└─────────────────────┴──────────────────────────┴─────────────────────────┘
```

用一个生活类比把三者钉死：

```text
你要去朋友家（建立连接）：
  Unknown host       = 你连地址都查不到，地图上搜不到这个地名。   → 先解决"地址在哪"（DNS）
  Connection timeout = 地址有了，去敲门，敲半天没人应也没人拒绝。 → 路上被拦了（防火墙/网络）
  Connection refused = 找到门了，但门口明确挂牌"此处无人"。       → 到了，但没人开店（端口没进程）
```

> 前端类比：浏览器里 `ERR_NAME_NOT_RESOLVED` = unknown host，`ERR_CONNECTION_REFUSED` = connection refused，长时间 `pending` 后失败 ≈ timeout。你以前看到这些只能干瞪眼，现在每一种都有明确的下一步命令。

## 实操：四把命令把每一层看穿

下面是后端排查网络问题的"四件套"。场景统一设为：**在 `svc-canvas` 的容器里，怀疑连不上 `svc-ai`（内网 `svc-ai:8082`）。**

### curl -v：看完整的握手与响应头

`curl -v` 是最值钱的一条命令——它把 DNS 解析、TCP 连接、（TLS 握手）、请求头、响应头**全过程打印出来**，一条命令覆盖大半排查。

```bash
curl -v http://svc-ai:8082/actuator/health
```

预期输出样例（正常情况）：

```text
*   Trying 10.0.0.7:8082...
* Connected to svc-ai (10.0.0.7) port 8082
> GET /actuator/health HTTP/1.1
> Host: svc-ai:8082
> User-Agent: curl/7.81.0
> Accept: */*
>
< HTTP/1.1 200 OK
< Content-Type: application/json
<
{"status":"UP"}
```

怎么读这段输出（逐行就是网络分层）：

- `Trying 10.0.0.7:8082...`：DNS **已经解析成功**，`svc-ai` 被解析成 `10.0.0.7`。如果卡在这之前报 `Could not resolve host`，那是 **DNS 问题**。
- `Connected to svc-ai (10.0.0.7) port 8082`：TCP **三次握手成功**，连接建立。卡在 `Trying...` 之后迟迟不 `Connected` 就是 timeout（防火墙/网络）；立刻报 `Connection refused` 就是端口没人听。
- `>` 开头是**发出去的请求头**，`<` 开头是**收回来的响应头**。看到 `< HTTP/1.1 200 OK` 说明应用层完全正常。

结论：这条 `health` 通了，说明从 `svc-canvas` 到 `svc-ai` 的 DNS、网络、端口、应用全链路都健康，问题不在网络层，往业务逻辑查。

对比一个失败样例（端口没人听）：

```text
*   Trying 10.0.0.7:8082...
* connect to 10.0.0.7 port 8082 failed: Connection refused
* Failed to connect to svc-ai port 8082: Connection refused
curl: (7) Failed to connect to svc-ai port 8082 after 0 ms: Connection refused
```

怎么读：DNS 解析成功（拿到了 IP），但 `Connection refused`——**`svc-ai` 进程没起来或没 listen 8082**。下一步去 `svc-ai` 那边敲 `ss -tlnp | grep 8082`。

### telnet host port：纯测端口通不通

如果你不关心 HTTP 内容，只想知道"那个端口的 TCP 能不能连上"，`telnet` 最直接。

```bash
telnet svc-ai 8082
```

预期输出样例（端口通）：

```text
Trying 10.0.0.7...
Connected to svc-ai.
Escape character is '^]'.
```

怎么读：出现 `Connected to`，说明 TCP 握手成功，端口是通的（按 `Ctrl+]` 再输 `quit` 退出）。

端口不通时的两种典型表现：

```text
telnet: Unable to connect to remote host: Connection refused   ← 端口没人听
telnet: connect to address 10.0.0.7: Connection timed out       ← 防火墙/网络（包被丢）
```

结论：`telnet` 一条命令就帮你把"端口没人听（refused）"和"被防火墙拦了（timeout）"区分开了——这正是上一节那张三类错误表的实战落地。

### nc（netcat）：telnet 的现代替代

很多精简的容器镜像里没有 `telnet`，但常有 `nc`。`-z` 只扫描不发数据，`-v` 打印结果。

```bash
nc -zv svc-ai 8082
```

预期输出样例：

```text
Connection to svc-ai (10.0.0.7) 8082 port [tcp/*] succeeded!
```

怎么读 / 结论：出现 `succeeded!` 即端口通，作用和 `telnet` 一致。批量探测多个端口也方便：`nc -zv svc-ai 8080-8090`。

### ping：测网络层通不通（但要小心）

`ping` 测的是 **IP 层能不能到达对方机器**，不涉及任何端口。

```bash
ping -c 3 svc-ai
```

预期输出样例：

```text
PING svc-ai (10.0.0.7) 56(84) bytes of data.
64 bytes from 10.0.0.7: icmp_seq=1 ttl=64 time=0.21 ms
64 bytes from 10.0.0.7: icmp_seq=2 ttl=64 time=0.19 ms
64 bytes from 10.0.0.7: icmp_seq=3 ttl=64 time=0.22 ms

--- svc-ai ping statistics ---
3 packets transmitted, 3 received, 0% packet loss
```

怎么读：`0% packet loss` 说明机器可达、网络层通。如果是 `100% packet loss` 或一直 `Request timeout`，机器不可达或被防火墙挡。

> 重要提醒：**`ping` 不通 ≠ 服务不可用。** 很多云环境和容器默认禁掉 ICMP（`ping` 用的协议），所以 `ping` 失败但 HTTP 端口完全正常是常事。**`ping` 只能用来证明"通"，不能用来证明"不通"**——别因为 ping 不通就下结论网络有问题，要用 `telnet`/`nc` 测真正的业务端口。

### dig / nslookup：单独测 DNS 解析

当 `curl` 报 `Could not resolve host`，就该单独验证 DNS。

```bash
dig svc-ai +short        # 只看解析结果，最简洁
# 或者用 nslookup
nslookup svc-ai
```

`dig +short` 预期输出样例：

```text
10.0.0.7
```

怎么读：直接吐出 IP，说明 DNS 解析正常。如果**什么都不输出**或报 `NXDOMAIN`，说明这个名字解析不出来——对应 unknown host 错误。

`nslookup` 失败样例：

```text
Server:    10.96.0.10
Address:   10.96.0.10#53

** server can't find svc-ai: NXDOMAIN
```

怎么读 / 结论：`NXDOMAIN` = 域名不存在。在 K8s 里最常见的原因是**服务名拼错**（应该是 `svc-ai` 写成了 `svc-ai-svc`）、**跨命名空间没写全名**（需要 `svc-ai.namespace.svc.cluster.local`），或者那个 Service 压根没创建。下一步去核对配置里的服务名和 K8s Service 是否存在。

## 把四件套串成一条排查流程

遇到"`svc-canvas` 调 `svc-ai` 失败"，不要乱试，按从下往上的顺序走，每一步都缩小范围：

```text
1. curl -v http://svc-ai:8082/...   ← 一条命令先看全貌，多数时候这步就定位了
        │
        ├─ Could not resolve host ──▶ DNS 问题：dig svc-ai +short 核实，查服务名/配置
        │
        ├─ Connection refused ──────▶ 端口没人听：去 svc-ai 侧 ss -tlnp | grep 8082，
        │                              看进程起没起、是否监听在 127.0.0.1（外部连不到）
        │
        ├─ 卡住后 timeout ──────────▶ 网络/防火墙：nc -zv 测端口，ping 测可达，
        │                              查云安全组和本机防火墙是否放行 8082
        │
        └─ 拿到 5xx 响应 ───────────▶ 网络全通，是应用层问题：转去看 svc-ai 日志，
                                       502 查进程存活，504 查接口为什么慢
```

> 这套"先 `curl -v` 看全貌，再按报错分流"的思路，就是 [排查方法论](/back-end/frontend-backend-guide/27-troubleshooting-methodology) 在网络问题上的具体应用。把它和 [诊断工具箱](/back-end/frontend-backend-guide/29-diagnostic-toolbox) 里更多命令配合，绝大多数"连不上/慢"都能在几分钟内定位。

## 小结

- 后端排网络的核心能力是**把一次失败精确归到某一层**：DNS（名字解析不出）、TCP（端口没人听 / 网络不通）、还是应用层（5xx）。
- 三类典型错误一眼分清：**connection refused = 端口没进程 listen；timeout = 网络/防火墙把包丢了；unknown host = DNS 解析不出**——三者排查方向完全不同。
- 状态码看"谁的错"：4xx 多是请求方问题（401 没认证 / 403 没权限 / 400 参数错），5xx 是服务端；**502 = 后端没给有效响应（多半挂了）；504 = 后端在回但太慢（性能问题）**。
- 四件套各管一层：`curl -v` 看全链路全貌、`telnet`/`nc` 测端口通不通、`ping` 测机器可达（但 ping 不通不代表服务不可用）、`dig`/`nslookup` 单独验 DNS。
- 端口靠"一个进程 listen 一个端口"区分服务，`ss -tlnp` 是你确认"谁在听哪个端口"的常用命令；长连接 keep-alive / 连接池则是后端复用连接、扛高并发的基础。

### 自测

1. 你在 `svc-canvas` 容器里 `curl -v http://svc-ai:8082/actuator/health`，输出停在 `Trying 10.0.0.7:8082...` 之后很久才报 `Connection timed out`。这最可能是 DNS、端口没人听、还是防火墙问题？下一步你会敲什么命令？
2. 前端同学反馈生图接口一会儿返回 502、一会儿返回 504。请分别说出这两种情况后端最可能发生了什么，以及你各自会先去看什么。
3. `ping svc-ai` 100% 丢包，但 `curl http://svc-ai:8082/actuator/health` 返回 200。这说明 `svc-ai` 到底可不可用？为什么会出现这种"ping 不通但 HTTP 正常"的现象？

### 下一章

下一章 [Docker 实战](/back-end/frontend-backend-guide/23-docker-in-practice)，我们把这些跑在服务器和容器里的服务正式装进 Docker——你会看到容器之间正是靠本章讲的端口、DNS（服务名）和网络互相连接的。

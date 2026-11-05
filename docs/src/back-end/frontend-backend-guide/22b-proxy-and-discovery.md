---
prev:
  text: "A4. HTTP、Cookie、CORS 与缓存"
  link: "/back-end/frontend-backend-guide/22a-http-and-browser"
next:
  text: "A6. 内网、堡垒机与访问权限"
  link: "/back-end/frontend-backend-guide/22c-private-network-and-access"
---

# 请求先到 Nginx，它怎样找到任务服务？

本机直接请求 `task-api:8080` 可以查到任务。部署后改用域名，浏览器却收到 502。中间多了一层入口，请求就多了一段可能失败的连接。

这一课只顺着这条路线往下看：浏览器怎样到达入口，入口怎样转发给任务服务，服务地址变化后又怎么办。先掌握 [网络](/back-end/frontend-backend-guide/22-networking-for-backend) 和 [HTTP](/back-end/frontend-backend-guide/22a-http-and-browser) 的基本含义即可，不需要先学微服务。

## 1. 给单体一个统一入口

开发时，我们可以直接访问 `http://localhost:8080/api/tasks`。部署时，常让浏览器访问同一个网站入口，例如 `https://tasks.example.com/api/tasks`，由入口接收 HTTPS，再转交给应用。

![浏览器经反向代理访问任务服务、数据库，以及可选的静态资源 CDN](/backend-guide/network-proxy-topology.svg)

这里的入口叫**反向代理**：它面向客户端接收请求，再代表服务端访问后面的应用。图中的 Nginx 处理 HTTPS 和转发，`task-api` 处理业务，MySQL 保存任务。浏览器不直接连接 MySQL，数据库凭据也留在服务端。

这是后续补齐认证和用户隔离后的目标结构，不是初始 CRUD 已可公网部署的承诺。早期本机练习仍使用 H2，部署主线才迁移到 MySQL。

若 Nginx 和应用处于同一主机的同一网络环境，可以让应用只监听 `127.0.0.1:8080`，由 Nginx 对外提供入口。若分属不同主机或容器，Nginx 的 `127.0.0.1` 就找不到另一个环境里的应用，需要改用可达的服务地址，并设置对应访问控制。

对 Nginx 来说，后面的任务应用就是它的**上游**。代理与上游之间是另一段连接，入口收到请求，不表示应用一定收到了。

### 顺便分清：正向代理服务的是另一边

公司让你的电脑通过出口代理访问外网时，代理主要代表客户端，这叫**正向代理**。反向代理则主要代表网站服务端，客户端通常只需要知道网站域名。

HTTPS 经过正向代理时，常用 CONNECT 建立隧道，代理不一定能看到加密正文；企业安装受信任证书做解密检查，是另一种配置。反向代理则常终止客户端 TLS，因此能按 HTTP 路径转发；连接上游时是否继续用 TLS，要按实际安全要求决定。

排错时记得查看客户端是否配置了 `HTTP_PROXY`、`HTTPS_PROXY`、`NO_PROXY`。下文的 `--noproxy '*'` 仅用于明确需要直连的本地实验，不用于绕过公司访问策略。

## 2. 看一段转发配置，跟着路径走

下面是配置阅读示例，不要求现在安装 Nginx。它省略 TLS 证书与静态资源，只展示 HTTP 转发关系；真实公网入口应使用 HTTPS，不能直接当完整上线配置。

```nginx
http {
    upstream task_backend {
        server 127.0.0.1:8080;
        keepalive 16;
    }

    server {
        listen 80;
        server_name tasks.example.com;

        location /api/ {
            # 没有 URI 部分：/api/tasks 保持为 /api/tasks
            proxy_pass http://task_backend;
            proxy_http_version 1.1;
            proxy_set_header Connection "";
            proxy_set_header Host $host;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_connect_timeout 2s;
            proxy_send_timeout 10s;
            proxy_read_timeout 10s;
        }
    }
}
```

先看地址：`upstream` 把一组上游命名为 `task_backend`，目前组里只有 `127.0.0.1:8080`。这个名字属于 Nginx 配置，不会自动创建 DNS 服务；回环地址也要求代理和应用在同一网络环境。

再看路径：请求 `/api/tasks` 命中 `location /api/`，由 `proxy_pass` 转给这组上游。这里 `proxy_pass` 没有 URI 部分，所以应用收到的仍是 `/api/tasks`。

若多写一个 `/`，含义就变了：

```nginx
# 在 location /api/ 内，用 / 替换匹配的 /api/ 前缀
proxy_pass http://task_backend/;
# /api/tasks 变成 /tasks
```

此时后端若只认识 `/api/tasks`，就可能返回 404。因此入口 404 时，先比对转发后的真实路径，再决定是否改 Controller。上述替换规则针对这里的普通前缀 location；正则 location、变量、rewrite 等情况要另查规则。

最后看等待时间。`proxy_connect_timeout` 限制建立上游连接的等待；`proxy_read_timeout` 通常限制相邻两次读取之间的空闲时间，不是整次请求的总期限；`proxy_send_timeout` 也不是业务执行期限。它们要和客户端、数据库及业务等待预算协调，不能只靠统一调大来掩盖慢请求。

Host 和 `X-Forwarded-Proto` 帮助后端判断原始访问主机和协议，影响链接、重定向和 Cookie。后端只应信任来自受信任代理的转发信息：外部客户端也能伪造 `X-Forwarded-*` 或 `X-Request-ID`，它们不能直接作为用户身份或审计事实。

## 3. 出现 502 或 504，沿着新增的这一跳查

先建立本地直连的对照。运行任务服务后，可以执行：

```bash
curl --noproxy '*' --max-time 5 -sS -o /dev/null \
  -w 'status=%{http_code} connect=%{time_connect}s first_byte=%{time_starttransfer}s total=%{time_total}s\n' \
  http://127.0.0.1:8080/api/tasks
```

无认证的本地练习通常应得到 200；后续启用认证时可能得到 401。时间值从本次操作开始计时，`first_byte` 不是纯业务处理时间，途中有代理和 TLS 时也会包含相应等待。

如果已经有获授权的 HTTPS 测试入口，再替换下面的占位域名进行对照：

```bash
curl --max-time 10 -i https://your-test-host.example/api/tasks
```

教程不提供这个域名对应的真实服务。证书错误要单独解决，不用 `-k` 跳过验证。`-i` 或进一步的 `-v` 可能显示 Set-Cookie、令牌等内容，分享输出前要脱敏。

直连正常、入口异常时，继续查入口到应用这一段：

- **404**：先确认是谁返回，再查入口路由和上游路径。
- **502**：常见原因包括上游连接拒绝、重置、协议不匹配或响应无效，优先查实际上游地址、监听以及代理错误日志。
- **504**：可能在连接上游时超时，也可能在等待响应时超时，要对照连接耗时、响应耗时和应用日志。
- **503**：可能是没有可用后端、过载或维护，具体取决于返回它的组件和配置。

同一个响应可能来自 CDN、入口代理、网关或应用。收到 HTTP 响应，只能证明到响应者的路径已经成功，不能据此推断后面每一跳都成功。

尤其是写操作：代理连不上应用时可能返回 504；数据库已经提交、返回途中断开时，客户端也可能看到失败。因此 504 既不证明“已经执行”，也不证明“没有执行”。应结合请求 ID、幂等键或业务查询确认结果，再决定重试。

为了查到具体位置，代理可记录自身请求 ID、上游地址、连接与响应时间、状态。主线应用自行生成 `X-Request-Id`，代理可以记录上游返回的 `$upstream_http_x_request_id` 来关联应用日志；尚未连上应用时，先用代理自身的 ID。ID 不含隐私，也不作为身份凭证。响应未暴露上游地址很正常，需要有权限的人查看脱敏日志。

不必制造故障也能练习：把 `/api/tasks?page=0&size=20` 代入前面的两种配置，确认上游分别收到 `/api/tasks?page=0&size=20` 和 `/tasks?page=0&size=20`；假设 2 秒仍未连上，涉及的是 `proxy_connect_timeout`，此时不能声称 Controller 已执行。

## 4. 以后入口变复杂，新增组件分别做什么？

最小单体用一个入口、一份应用和数据库即可。只有出现具体需求，才给路线增加能力。

公开 JS、CSS、图片希望离用户近一些时，可以用 **CDN**（内容分发网络）在多个节点缓存。它也可能转发动态请求，所以用了 CDN 不代表请求一定命中缓存。私人 `/api/tasks` 不能照搬公开静态资源缓存规则；起步时明确 `Cache-Control: no-store`，并避免把 Cookie 或令牌放到 URL，以免进入日志、历史和缓存。

需要统一认证接入、限流和配额时，入口可以承担 **API 网关**角色。Nginx 能提供一部分能力，专用网关有更多管理功能，但不一定要额外部署一个微服务。网关验证了用户令牌，应用仍要判断这个人能否修改这条任务。

如果一个应用实例处理不过来，可以增加副本，由**负载均衡**把连接或请求分给它们。策略可以是轮询、最少连接或按键选择，不只是平均分配。两个副本都可以各自监听 8080，因为它们在不同的网络环境中。

加副本前，数据和进程内状态要先有安排：主线部署应共享正确配置的 MySQL，不能让每个副本各带一个内存 H2；会话若只在单进程里，也可能换个副本就失效。上传文件、缓存、后台任务和定时任务同样要检查。就绪检查与退出时排空连接决定哪些副本能接新请求，进程存在并不等于能接业务。会话粘滞或基础失败探测不能代替这些设计。

## 5. 副本地址会变，入口如何知道该找谁？

前面的 Nginx 配置写死了一个地址。增加副本、替换旧副本后，调用方需要知道当前可访问的地址，这个问题叫**服务发现**。

可以先用固定 upstream 配置或稳定 DNS 名，不是每次远程调用都必须引入注册中心。若用**注册中心**，实例会向它登记地址、更新心跳，调用方再获取实例列表。它是支持服务发现的一种方式，不等于服务发现本身。

**配置中心**解决的是另一件事：某个环境应该使用什么超时、业务参数或开关，怎样版本化和分发。一个产品可能同时做注册和配置，但职责仍然不同，数据库密码也不应混作实例目录记录，应采用合适的密钥管理。

登记在线不等于业务就绪，心跳正常不保证数据库可用，调用方缓存的列表也可能已经过时。所以有了服务发现，仍需设置调用超时和失败处理。

::: details 按需查阅：Kubernetes 的 Service 名称指向哪里？
在 [Kubernetes 实战](/back-end/frontend-backend-guide/24-kubernetes-in-practice) 中会见到 `task-api.default.svc.cluster.local`。集群域可配置，跨命名空间也需要使用合适名称。

普通 ClusterIP Service 的 DNS 通常返回虚拟 ClusterIP，再由集群网络机制把请求转给后端端点，并非 DNS 必然直接返回 Pod IP。Headless Service 设置 `clusterIP: None`，DNS 可返回后端端点地址，客户端要理解多地址和更新。端点发布还受就绪状态和配置影响，名字能解析不代表业务一定成功。

Pod 地址变化时，Service 名称可以不变。Kubernetes 控制平面已维护相应服务信息，不需要只为这个目的再强行加一个注册中心。
:::

::: details 按需查阅：BFF、RPC 和 gRPC 在这条路线上的位置
当移动端首页需要同时展示任务数、最近任务和用户偏好时，可以增加面向页面的聚合接口。这种面向特定前端整理数据的后端层叫 BFF。单体里先提供聚合接口即可，客户端需求明显分化后再评估独立部署。它不同于主要转发的反向代理，也不同于执行通用入口策略的网关；核心业务规则、事务和权限应有明确归属，不能在多个 BFF 各复制一份。

单体内部直接调用 Java 方法。将来真的拆出通知服务后，跨进程调用才引入网络失败。RPC 是按“服务和方法”表达操作的远程调用风格，不是某一种传输协议。gRPC 是具体框架，常用 protobuf 定义消息和接口、生成代码，通常基于 HTTP/2，支持普通及流式调用。用了 protobuf 不等于用了 gRPC，它也不负责服务发现。

HTTP JSON 接口容易直接用 fetch 调用；浏览器使用 gRPC 常需要 gRPC-Web 等适配。两者都要维护契约兼容，设置截止时间、限制重试，并传递认证和诊断信息。生成了类型也不能消除超时或部分执行，客户端取消不表示服务端事务已回滚，跨进程也不会自动获得单库事务。
:::

到这里，用户请求经过入口再到应用的路线已经清楚。后面 [Docker 实战](/back-end/frontend-backend-guide/23-docker-in-practice) 会把容器地址落到具体配置；需要深入定位时可查 [排查方法](/back-end/frontend-backend-guide/27-troubleshooting-methodology)。

下一篇：[A6 · 内网、堡垒机与访问权限](/back-end/frontend-backend-guide/22c-private-network-and-access)。我们换到工程师这一侧，看怎样获准进入服务器，而不是让用户请求进入应用。

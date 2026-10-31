---
prev:
  text: "E3. Docker + MySQL 单体部署"
  link: "/back-end/frontend-backend-guide/23-docker-in-practice"
next:
  text: "E5. 综合实战与交付验收"
  link: "/back-end/frontend-backend-guide/38-capstone-feature-to-prod"
---

# 日志与第一次排错：把浏览器错误连到服务器证据

> 学习目标：从一个失败请求提取方法、状态、时间和请求 ID，定位到服务器错误，不凭「感觉」改代码，也不先重启掩盖现场。

前置：[请求链路](./03-request-lifecycle)、[任务 API](./07-build-a-crud-api)。容器环境再配合 [Docker](./23-docker-in-practice)。

![按没有响应、4xx、代理5xx、应用错误分层收集证据，再关联请求ID与根因](/backend-guide/core-troubleshooting.svg)

## 1. 第一份有用的故障报告长什么样

不要只发「接口挂了」。至少收集：

- 发生时间与时区、环境（本地 / 测试 / 生产）、影响范围。
- HTTP 方法、脱敏后的路径、状态码、耗时。
- 必要的**脱敏**输入条件，不包含密码、token、完整 Cookie、支付或个人敏感信息。
- 响应中的请求 ID；若没有 HTTP 响应，保存 DNS / TCP / TLS 的错误现象。
- 可重复步骤、预期与实际结果、是否刚发布或改过配置。

不要截图完整 Authorization 头再上传到群里。抓包、curl verbose、HAR、dump 都可能包含敏感数据，按团队授权和脱敏规范分享。

## 2. 先分层：有没有收到 HTTP 响应

| 证据 | 含义与下一步 |
| --- | --- |
| 名字解析失败 | 检查 DNS 与调用方所处网络，不先查 Controller |
| 连接拒绝 / 超时 | 检查 IP、端口、监听、路由、安全策略与时限 |
| TLS 校验失败 | 检查域名、证书链、有效期与客户端时间，不用 `-k` 当修复 |
| 400 / 404 / 405 / 415 | 请求格式、路由与协议契约，先对照接口 |
| 401 / 403 | 认证或授权；初始实验没有实现这套能力 |
| 409 / 412 | 业务或版本前置条件冲突，不应该无脑重试覆盖 |
| 502 / 504 | 代理与上游通信异常；要看哪一跳返回，不能证明上游未执行 |
| 500 | 根据请求 ID 查应用根因，不能仅凭状态推测数据库宕机 |

[网络课](./22-networking-for-backend) 提供分阶段命令；[HTTP 课](./22a-http-and-browser) 解释浏览器 CORS。curl 成功不代表浏览器跨域一定成功。

## 3. 当前源码已经提供什么日志

`RequestLogFilter` 为每个请求生成 UUID，写入 `X-Request-Id` 响应头和 MDC 日志上下文；请求结束记录方法、状态、耗时，并在 finally 清除 MDC。

```text
INFO [requestId=示意UUID] ... http method=POST status=201 elapsedMs=12
INFO [requestId=另一UUID] ... http method=POST status=400 elapsedMs=2
```

这是格式示意，时间与耗时由运行结果决定。实际访问日志不打印 query、body、Cookie 或 token。响应里的 requestId 可与服务端日志相互对应。

MDC 是附着当前执行上下文的诊断信息；线程池复用时不清理可能串请求。切线程、异步任务和跨服务也不保证自动传递。当前样例只有请求 ID，**没有实现 OpenTelemetry 分布式追踪或集中日志平台**。

本地 Maven 启动时看终端；Compose 启动时看：

```bash
docker compose logs --since 10m api
# 跟随新日志；Ctrl+C 只停止跟随
docker compose logs -f api
```

如果你没有该环境权限，提供脱敏证据给负责同学，不要借学习教程尝试绕过访问控制。

## 4. 读堆栈：找根因，不只看第一行

示意：

```text
DataAccessResourceFailureException: ...
  at ...TaskRepository.list(...)
Caused by: SQLNonTransientConnectionException: ...
  at ...
```

1. 先用请求 ID 缩小到这次请求，不按线程名把所有请求混在一起。
2. 看异常类型、业务类位置、`Caused by` 链；根因可能藏在后面，不是每一层都需要修。
3. 连接失败就检查数据库地址和可达性；唯一约束冲突就检查重复业务；SQL 慢就测计划与等待。
4. 对外不回堆栈，只回稳定错误信息和请求关联标识。

`Connection is not available` 常指从池中借不到连接；可能 SQL 太慢、锁等待、连接泄漏或容量不足。它**不直接证明数据库密码错，也不说明加大池一定有效**。

## 5. 日志级别与写法

| 级别 | 什么时候用 |
| --- | --- |
| TRACE / DEBUG | 受控诊断细节，生产默认一般关闭或限范围临时开启 |
| INFO | 重要生命周期与业务里程碑，不要每行都打 |
| WARN | 可恢复但值得观察的异常路径 |
| ERROR | 本次操作失败、需要关注的系统错误，保留异常对象 |

```java
private static final Logger LOG = LoggerFactory.getLogger(TaskService.class);
LOG.info("task created taskId={}", task.id());
LOG.error("export failed jobId={}", jobId, exception);
```

占位符避免提前拼接字符串，但 **`log.debug("{}", expensive())` 的 expensive() 仍会在调用前执行**。昂贵计算要用级别判断或框架的惰性接口。日志关闭不等于任何开销都不存在。

异常一般在适当边界记录一次，不要每层 catch、打堆栈、再抛造成重复风暴。预期参数错误通常不需要 ERROR 堆栈刷屏。

不要记录完整 User Entity；可能包含密码哈希、邮箱和内部字段。记录必要 ID 时也要考虑它本身是否属于敏感数据以及保留权限。

## 6. 慢请求怎么拆

请求慢可能是客户端 DNS / TLS、代理排队、应用线程排队、借连接、数据库锁、SQL 执行或下游调用。**总耗时不告诉你具体卡在哪**。

先测一条路径在正常和异常条件下的分段耗时；再结合：

- 延迟分位数 P50 / P95 / P99，不只看平均数。
- 请求量、错误率、资源使用和排队情况。
- 数据库连接、慢查询、锁等待与执行计划。
- 发布、配置和流量变化时间线。

日志适合单次事件；指标适合趋势与告警；trace 适合跨组件链路。完整体系见 [可观测性选修](./30-observability)，先别为第一版实验部署全套监控平台。

## 7. 修复顺序：证据 → 止血 → 根因 → 回归

- 优先低风险止血：限制异常流量、暂停有问题任务、回滚可回滚的应用版本。
- 生产重启、回滚、扩容和 dump 都按授权与变更流程，先评估数据、隐私和资源风险。
- 应用回滚不等于数据库结构 / 数据也自动回滚；已发出的外部请求更不能随意撤销。
- 修好后添加能复现的回归测试与监控，记录触发条件，不只记一条「重启好了」。

## 动手：做三份最小排错记录

1. 在任务 API 中提交空标题，保存 400 与 requestId，找对应访问日志。**预期没有写入任务。**
2. 向不存在的端口发本机请求，记录「没有 HTTP 响应」，对比上一步。**预期找不到对应 Controller 日志。**
3. 在 MySQL 练习环境中暂时停止测试数据库，再请求列表，观察日志根因和失败时限；恢复后验证。**只在隔离环境操作，不对公司服务故障注入。**

每份记录写：症状、证据、定位层、修复、验证。第三项需要 Docker 服务端实际运行，不把示意输出当作已完成演练。

## 自测与参考答案

1. 查不到应用日志，就证明请求没到服务器吗？**不一定，可能走了其他实例、采集失败、级别或路径不对，需要其他证据。**
2. P99 很高，平均数正常，该看哪个？**两者都看，高尾延迟说明少部分请求体验差，要定位对应样本与等待。**
3. 请求 ID 是身份凭证吗？**不是，只用于关联诊断，不提供权限。**

下一站：[综合实战与交付验收](./38-capstone-feature-to-prod)；进一步查 [排查方法](./27-troubleshooting-methodology) 与 [工具箱](./29-diagnostic-toolbox)。

---
prev: false
next: false
---

# 场景词典：遇到生词时来查

这里按请求、数据库、登录、部署等场景整理了 170 个条目。可以用页面搜索找具体词，也可以从目录选择对应场景，不需要从头背完。

先看它是什么，再看工作中为什么会用到。想理解完整过程，就打开最后一列的教程；“常见误区”留作使用时的提醒。

例子大多沿用任务系统。同一个词在不同团队或产品里可能含义不同，实际工作时还要结合对方正在说的场景。

## A. 接到一个需求：代码放哪、接口怎么表达

| 术语 | 定义 | 解决什么问题 | 常见误区 | 相关教程 |
| --- | --- | --- | --- | --- |
| 单体 Monolith | 多个业务功能放在同一个应用里，一起构建和发布 | 起步时只需启动一个应用，就能跟完整个任务功能 | 仍可以分包、分模块，不是把代码全写进一个类 | [架构](/back-end/frontend-backend-guide/02-architecture-overview) |
| 微服务 Microservices | 按业务拆成能独立运行和发布的服务，通过网络协作 | 某个功能或团队确实需要独立发布和扩容 | 拆几个 Controller 不够，还会多出网络和跨服务数据问题 | [架构](/back-end/frontend-backend-guide/02-architecture-overview) |
| Controller | 接 HTTP 请求、调用业务方法、返回响应的入口 | 找到某条接口对应的处理代码 | 不宜把 SQL 和全部业务规则都塞进入口 | [分层](/back-end/frontend-backend-guide/04-three-layer-and-structure) |
| Service（业务层） | 集中处理某个业务操作的代码 | 创建任务时检查规则，再安排保存操作 | 不是 Kubernetes Service，也未必是独立运行的服务 | [分层](/back-end/frontend-backend-guide/04-three-layer-and-structure) |
| Repository / Mapper | 负责数据库查询、保存及数据转换的代码 | 让任务的 SQL 和结果映射有明确位置 | 不是浏览器请求封装；换数据库也可能影响其他业务代码 | [CRUD](/back-end/frontend-backend-guide/07-build-a-crud-api) |
| Entity | 用对象表达需要持久保存的业务数据 | 在代码里表示一条任务及其存储关系 | 不宜不加选择地把全部字段直接返回前端 | [分层](/back-end/frontend-backend-guide/04-three-layer-and-structure) |
| DTO | 用来传数据的对象，例如创建任务的请求类型 | 写清一次请求允许提交哪些字段 | 对象定义好了，仍要检查数据和权限 | [API](/back-end/frontend-backend-guide/34-api-design) |
| VO / 响应模型 | 这里指准备展示或返回前端的数据结构 | 只返回前端需要且允许看到的字段 | VO 在其他语境也可能指 Value Object，先问清用法 | [分层](/back-end/frontend-backend-guide/04-three-layer-and-structure) |
| POJO | 不必继承指定框架类型的普通 Java 对象 | 数据和业务不必处处依赖某个框架 | 不一定只有 getter/setter，也不一定交给 Spring 管理 | [Java](/back-end/frontend-backend-guide/05-java-crash-course) |
| REST | 围绕资源、统一接口等约束组织系统的设计风格 | 用任务资源和 HTTP 方法表达接口操作 | URL 不带动词，只是常见写法，不代表满足了全部约束 | [API](/back-end/frontend-backend-guide/34-api-design) |
| CRUD | 创建、读取、更新和删除 | 先完成任务的基本管理功能 | 跑通以后，仍要补数据校验、权限和并发处理 | [CRUD](/back-end/frontend-backend-guide/07-build-a-crud-api) |
| 分页 Pagination | 每次只返回数据集合的一部分 | 控制列表响应大小与查询成本 | 深 offset 可能慢，数据变化也可能让翻页重复/遗漏 | [API](/back-end/frontend-backend-guide/34-api-design) |
| 幂等键 Idempotency Key | 标识一次逻辑操作的稳定客户端/业务键 | 网络重试时返回原结果，避免重复创建 | 相同键用于不同请求内容不能静默复用；需要唯一约束与作用域 | [API](/back-end/frontend-backend-guide/34-api-design) |

## B. Spring 为什么能替我创建对象

| 术语 | 定义 | 解决什么问题 | 常见误区 | 相关教程 |
| --- | --- | --- | --- | --- |
| IoC | 对象创建、装配等控制权由容器管理的思想 | 避免业务对象自行组织全部依赖 | 不是某个注解的别名，也不是“禁止所有 new” | [IoC/DI](/back-end/frontend-backend-guide/06-spring-boot-ioc-di) |
| DI 依赖注入 | 将对象所需依赖从外部传入 | 替换实现、明确依赖、便于测试 | 不等于 React 状态上下文；Java 构造器注入有自己的生命周期 | [IoC/DI](/back-end/frontend-backend-guide/06-spring-boot-ioc-di) |
| Spring Bean | 由 Spring 容器创建或注册、管理生命周期的对象 | 统一装配 Service、数据库客户端等组件 | Bean 与 POJO 不等同；默认 singleton 是每容器每定义一个，不代表线程安全 | [IoC/DI](/back-end/frontend-backend-guide/06-spring-boot-ioc-di) |
| Bean Scope | Bean 实例的创建与复用范围 | 区分 singleton、prototype、request 等生命周期 | 默认单例不表示所有作用域都只有一个实例 | [IoC/DI](/back-end/frontend-backend-guide/06-spring-boot-ioc-di) |
| 自动配置 Auto-configuration | Spring Boot 按依赖、条件和配置建立默认组件 | 减少手工配置通用基础设施 | 不是无条件魔法，条件不满足就不会生效 | [Spring](/back-end/frontend-backend-guide/06-spring-boot-ioc-di) |
| AOP | 通过切面/代理等方式施加横切行为 | 统一事务、审计等跨多个方法的逻辑 | 不等同 axios 拦截器；代理调用边界影响是否生效 | [注解](/back-end/frontend-backend-guide/08-annotations-cheatsheet) |
| Filter | Servlet 请求/响应链中的过滤组件 | 在 MVC 处理前后做通用协议处理 | 不应拿客户端 uid 头直接建立可信身份 | [请求链路](/back-end/frontend-backend-guide/03-request-lifecycle) |
| Interceptor | Spring MVC handler 处理前后的拦截钩子 | 处理 MVC 层共性逻辑 | 与 Servlet Filter、客户端拦截器不是同一层 | [请求链路](/back-end/frontend-backend-guide/03-request-lifecycle) |
| Bean Validation | 用约束和验证器检查输入形状与值域 | 校验标题非空、长度、数字范围 | @Valid 不证明用户有权改这条数据 | [CRUD](/back-end/frontend-backend-guide/07-build-a-crud-api) |
| @Transactional | 通过 Spring 事务机制声明事务边界 | 使同一事务资源中的操作按规则提交或回滚 | 自调用、异常规则、线程切换会影响效果；不能自动覆盖远程 API | [事务](/back-end/frontend-backend-guide/11-transactions-consistency) |

## C. 请求连不上、很慢或跨域：网络词汇

| 术语 | 定义 | 解决什么问题 | 常见误区 | 相关教程 |
| --- | --- | --- | --- | --- |
| DNS | 将域名等名称解析到网络记录的系统 | 通过服务名寻找地址 | DNS 成功不说明端口可达或 HTTP 正常 | [网络](/back-end/frontend-backend-guide/22-networking-for-backend) |
| IP / 端口 | IP 标识网络接口地址，端口用于区分传输端点 | 找到 `task-api` 的监听位置 | 容器的 localhost、宿主机 localhost 不一定是同一网络空间 | [网络](/back-end/frontend-backend-guide/22-networking-for-backend) |
| TCP | 提供可靠、有序字节流的传输协议 | 在连接上交换 HTTP 等应用数据 | TCP 确认不等于应用已提交事务，也没有业务消息边界 | [网络](/back-end/frontend-backend-guide/22-networking-for-backend) |
| TLS / HTTPS | TLS 提供传输安全，HTTPS 是 HTTP over TLS | 验证对端、保护链路机密性和完整性 | HTTPS 不替代授权，也不保护已泄露的服务端日志 | [安全](/back-end/frontend-backend-guide/32-security) |
| HTTP 方法 / 状态码 | 请求操作语义与响应结果分类 | 表达创建、查询、无权限、冲突等契约 | 状态码不是“前端/后端谁背锅”的绝对判定 | [API](/back-end/frontend-backend-guide/34-api-design) |
| Origin 同源 | 通常由协议、主机、端口组成的源标识 | 浏览器划分脚本可访问的资源边界 | 同站与同源不同；不同端口已经跨源 | [网络](/back-end/frontend-backend-guide/22-networking-for-backend) |
| CORS | 浏览器控制跨源脚本读取响应等行为的机制 | 按策略开放跨源 API 访问 | 不拦 curl，不是登录鉴权，也不能代替 CSRF 防护 | [安全](/back-end/frontend-backend-guide/32-security) |
| 反向代理 | 代表后端接收客户端请求再转发的组件 | 统一入口、TLS 终止、路由 | 不只是开发代理，转发头也有信任边界 | [网络](/back-end/frontend-backend-guide/22-networking-for-backend) |
| 负载均衡 | 按策略把请求分给多个后端实例 | 分摊负载并避开不健康实例 | 不会自动复制内存会话或修复数据库瓶颈 | [架构](/back-end/frontend-backend-guide/02-architecture-overview) |
| Gateway 网关 | 集中路由和部分通用策略的入口组件 | 统一接入、粗粒度认证和限流 | 有网关不等于业务层可以省略细粒度授权 | [安全](/back-end/frontend-backend-guide/32-security) |
| Timeout 超时 | 某段等待达到上限 | 限制连接、读写、整体请求的资源占用 | 超时是结果可能未知，不证明下游没有执行 | [排查](/back-end/frontend-backend-guide/27-troubleshooting-methodology) |
| Keep-alive / 连接复用 | 在多个请求间复用已有连接 | 降低重复建连开销 | HTTP 复用、TCP 保活探测、数据库连接池不是同一机制 | [连接池](/back-end/frontend-backend-guide/13-connection-pools) |
| RPC / HTTP Client | 跨进程调用方式与其客户端实现 | 调用外部服务或另一个业务服务 | 方法签名像本地调用，不代表延迟、失败和事务语义像本地 | [架构](/back-end/frontend-backend-guide/02-architecture-overview) |

## D. 数据写不对、查不快：数据库词汇

| 术语 | 定义 | 解决什么问题 | 常见误区 | 相关教程 |
| --- | --- | --- | --- | --- |
| RDBMS / MySQL | 以关系模型和 SQL 为核心的数据库系统 | 用表、约束与事务持久管理业务数据 | 关系型不等于只能固定格式，也不是任何查询都快 | [数据库选择](/back-end/frontend-backend-guide/09-sql-vs-nosql) |
| H2 | 可嵌入运行的 Java 关系数据库 | 第一课降低安装和启动成本 | 内存模式重启会丢数据，MySQL 兼容模式也不等于 MySQL | [CRUD](/back-end/frontend-backend-guide/07-build-a-crud-api) |
| NoSQL | 对多种非传统关系数据库的宽泛分类 | 满足文档、键值、图等不同访问模式 | 不是“没有 schema”或“都不支持事务”的统一产品 | [数据库选择](/back-end/frontend-backend-guide/09-sql-vs-nosql) |
| ORM / MyBatis | ORM 做对象关系映射；MyBatis 更偏 SQL 与结果映射 | 组织持久化访问，减少重复映射代码 | 不等于自动写出最优 SQL，也不保证不会注入 | [SQL](/back-end/frontend-backend-guide/10-sql-and-indexes) |
| 主键 / 唯一约束 | 标识一行，以及限制一组列取值不得重复的约束 | 保证业务标识与去重的最终一致性 | “先查没有再插”无法替代数据库唯一约束 | [SQL](/back-end/frontend-backend-guide/10-sql-and-indexes) |
| 索引 Index | 为特定访问路径维护的数据结构 | 减少扫描、支持排序或约束 | 索引不是内存 Map；写入、存储与维护也有成本 | [SQL](/back-end/frontend-backend-guide/10-sql-and-indexes) |
| 联合索引 | 按多个列组织的索引 | 优化常见组合条件，如 owner + status + id | 不是把所有字段都加进去就一定有效，需看顺序和查询 | [SQL](/back-end/frontend-backend-guide/10-sql-and-indexes) |
| EXPLAIN | 查看数据库计划如何执行 SQL 的工具 | 验证索引、扫描行数、连接方式的假设 | 计划不是所有真实运行时成本，仍需结合实际数据和耗时 | [SQL](/back-end/frontend-backend-guide/10-sql-and-indexes) |
| 事务 / ACID | 一组操作的提交边界及原子、隔离、持久等性质 | 让相关本地修改保持约定的不变量 | 不自动包含另一数据库或第三方接口；一致性也靠业务约束 | [事务](/back-end/frontend-backend-guide/11-transactions-consistency) |
| 隔离级别 | 并发事务对彼此修改的可见性与冲突规则 | 控制脏读、不可重复读等行为 | 不同数据库同名级别仍有细节差异，不是越高越无代价 | [事务](/back-end/frontend-backend-guide/11-transactions-consistency) |
| MVCC | 用多版本等机制协调读写可见性 | 让某些一致性读取不必阻塞写入 | 不等于任何查询都不加锁，也不消除所有写冲突 | [事务](/back-end/frontend-backend-guide/11-transactions-consistency) |
| 乐观锁 | 更新时验证版本/条件，冲突则拒绝或重试 | 防止并发覆盖任务状态 | “乐观”不是无需条件检查；重试也必须有上限 | [事务](/back-end/frontend-backend-guide/11-transactions-consistency) |
| 悲观锁 / 行锁 | 通过数据库锁协调冲突操作 | 在事务中保护必须串行修改的记录 | 实际锁范围受索引和语句影响，不一定只锁你想的那一行 | [事务](/back-end/frontend-backend-guide/11-transactions-consistency) |
| 连接池 | 管理和复用一组数据库/HTTP 等连接 | 控制建连成本与并发资源 | 池越大不一定越快，等待池连接也会形成超时 | [连接池](/back-end/frontend-backend-guide/13-connection-pools) |
| 读写分离 / 复制延迟 | 写主库、部分读副本；复制存在时间差 | 扩展读取或提升容灾能力 | 刚写后读副本可能看不到，不能假设实时强一致 | [事务](/back-end/frontend-backend-guide/11-transactions-consistency) |

## E. 同时执行很多事：线程、异步与内存

| 术语 | 定义 | 解决什么问题 | 常见误区 | 相关教程 |
| --- | --- | --- | --- | --- |
| 进程 / 线程 | 进程拥有资源边界，线程是在进程内被调度的执行单元 | 组织隔离与并发执行 | 一个线程不等于一个事件循环，进程也可能有很多线程 | [并发起步](/back-end/frontend-backend-guide/14-single-thread-to-multithread) |
| 并发 / 并行 | 并发是多个工作交错推进，并行是同时执行 | 描述任务执行关系和硬件利用 | 不需要多核才能有并发问题，异步 JS 也可能有逻辑竞态 | [并发起步](/back-end/frontend-backend-guide/14-single-thread-to-multithread) |
| 同步 / 异步 | 调用方等待完成，或通过后续机制获知结果 | 组织等待与完成通知 | 异步不等于并行、不等于更快、更不等于任务持久化 | [异步](/back-end/frontend-backend-guide/17-async-programming) |
| 线程池 / Executor | 有界组织线程、队列与执行策略的设施 | 复用线程并控制资源使用 | 无界排队可能 OOM；线程越多不一定吞吐越高 | [线程池](/back-end/frontend-backend-guide/15-thread-pools-executor) |
| Future / CompletableFuture | 表达未来结果及其组合的抽象 | 协调异步完成、失败和依赖关系 | 不会自动保证事务、超时取消或上下文正确传播 | [异步](/back-end/frontend-backend-guide/17-async-programming) |
| 竞态条件 Race Condition | 结果依赖未被约束的执行时序 | 解释“偶现覆盖、重复扣减”等并发缺陷 | 不只来自 Java 多线程；先查后写也可能跨请求竞态 | [线程安全](/back-end/frontend-backend-guide/16-thread-safety-locks) |
| synchronized / Lock | 协调临界区互斥及相关内存可见性的机制 | 保护进程内共享状态 | JVM 内的锁不能直接保护另一个实例的内存或整个分布式业务 | [线程安全](/back-end/frontend-backend-guide/16-thread-safety-locks) |
| 原子类 / CAS | 单操作原子更新及比较并交换机制 | 安全地更新计数器等小状态 | 多个原子操作组合起来不自动成为一个原子业务流程 | [线程安全](/back-end/frontend-backend-guide/16-thread-safety-locks) |
| 死锁 Deadlock | 执行者持有资源并循环等待对方 | 定位无法继续推进的锁等待 | 不只是 CPU 高；数据库与线程都可能死锁，普通慢查询不一定是死锁 | [线程安全](/back-end/frontend-backend-guide/16-thread-safety-locks) |
| ThreadLocal | 给每个线程维护独立关联值的机制 | 保存有限的线程上下文 | 线程池会复用线程，不清理可能泄漏/串上下文；跨线程不自动传播 | [线程池](/back-end/frontend-backend-guide/15-thread-pools-executor) |
| Heap / Stack | Java 堆承载对象等，线程栈承载调用帧等 | 理解对象占用与调用深度 | 对象引用在栈里不代表对象本身一定在栈中 | [JVM 内存](/back-end/frontend-backend-guide/18-jvm-memory-model) |
| JMM | Java 内存模型对共享内存可见性、有序性等的规范 | 推理跨线程读写能看到什么 | 不等于 JVM 堆、栈这些运行时内存区域的布局 | [JVM 内存](/back-end/frontend-backend-guide/18-jvm-memory-model) |
| GC / STW | 回收不可达对象；某些阶段暂停应用线程 | 自动管理对象内存并分析停顿 | GC 不保证回收仍被引用的废弃对象，也不是什么 GC 都全程停顿 | [GC](/back-end/frontend-backend-guide/19-garbage-collection) |
| OOM / 内存泄漏 | 无法满足分配；不再需要的对象/资源仍被保留 | 定位内存不足与持续增长 | OOM 不一定由泄漏导致，也不总是堆太小 | [OOM](/back-end/frontend-backend-guide/20-oom-memory-leak) |
| Thread dump / Heap dump | 线程栈快照 / 对象堆快照 | 分别排查阻塞死锁与对象保留关系 | 两者不能互相替代，堆快照可能很大且含敏感数据 | [诊断工具](/back-end/frontend-backend-guide/29-diagnostic-toolbox) |

## F. 后台任务与消息为什么会重复

| 术语 | 定义 | 解决什么问题 | 常见误区 | 相关教程 |
| --- | --- | --- | --- | --- |
| 持久任务 Job | 数据库等可靠存储中的执行意图与状态 | HTTP 返回后继续处理并在重启后恢复 | 只是 `@Async` 或线程池中一个 Runnable 不算持久任务 | [消息任务](/back-end/frontend-backend-guide/33-mq-reliability) |
| MQ / Broker | 跨进程消息系统及其中转存储节点 | 异步解耦、暂时缓冲、独立消费 | MQ 不是 EventEmitter；必须考虑存储、确认、重投、保留期限 | [消息任务](/back-end/frontend-backend-guide/33-mq-reliability) |
| Producer / Consumer | 发布消息的一方 / 处理消息的一方 | 分离事件产生与后续处理 | 收到发送确认不代表消费者业务已成功 | [消息任务](/back-end/frontend-backend-guide/33-mq-reliability) |
| Topic / Queue | 产品中用于分类路由、存储或分配消费的抽象 | 组织消息订阅与处理 | 两者不总能互换，不同产品的订阅、分区、竞争语义不同 | [消息任务](/back-end/frontend-backend-guide/33-mq-reliability) |
| Consumer Group | 共同承担某个订阅处理的一组消费者 | 分摊处理负载，独立业务分别消费 | “组内分摊、组间独立”是常见模型，但不宜套给任何产品配置 | [消息任务](/back-end/frontend-backend-guide/33-mq-reliability) |
| ack / offset | 消费确认 / 消费位点等处理进度概念 | 告知系统哪些消息可视为已处理 | ack 不必然立即删除消息，提前 ack 可能造成业务漏处理 | [消息任务](/back-end/frontend-backend-guide/33-mq-reliability) |
| at-least-once | 满足约定前提下至少一次投递的语义 | 在故障后允许重投以追求可恢复 | 不代表没有重复或业务必成功，保留期与失败流程仍有限 | [消息任务](/back-end/frontend-backend-guide/33-mq-reliability) |
| exactly-once | 在明确系统范围内恰好一次的处理/效果承诺 | 用事务和去重避免范围内重复效果 | 某产品内部保证不能自动扩展到短信、支付等任意外部副作用 | [消息任务](/back-end/frontend-backend-guide/33-mq-reliability) |
| 幂等 Idempotency | 重复执行同一逻辑操作产生等价业务效果 | 使重试与重复消息不会重复创建/扣费 | 不意味着响应字节次次相同，也不是先查一遍就够 | [消息任务](/back-end/frontend-backend-guide/33-mq-reliability) |
| Outbox | 与业务同事务保存的待发布事件记录模式 | 避免业务提交后发送意图丢失 | 不消除重复，不把数据库和 Broker 变成一个本地事务 | [消息任务](/back-end/frontend-backend-guide/33-mq-reliability) |
| CDC | 从数据库变更日志等捕获数据变化的机制 | 将已提交变更同步到事件或搜索投影 | 不等于无运维成本，也不自动解决所有业务事件语义 | [消息任务](/back-end/frontend-backend-guide/33-mq-reliability) |
| 退避 / 抖动 | 逐渐拉长重试间隔 / 给间隔加随机性 | 防止故障时重试洪峰与同步碰撞 | 无限重试或所有层各自重试会放大故障 | [消息任务](/back-end/frontend-backend-guide/33-mq-reliability) |
| DLQ 死信队列 | 隔离多次失败或无法处理消息的设施 | 停止无效重试并提供诊断恢复入口 | 进入 DLQ 不等于业务完成，必须有告警与重放治理 | [消息任务](/back-end/frontend-backend-guide/33-mq-reliability) |
| 延迟消息 | 到未来某时刻后才可被投递/看见的消息 | 延后一次检查或执行机会 | 不等于周期调度，不自动处理漏跑、补跑与时区 | [功能地图](/back-end/frontend-backend-guide/39-backend-feature-map#batch-jobs) |
| 积压 Backlog | 尚未处理完的消息/任务集合 | 反映生产与消费的供需差 | 只看条数不够，还要看最老年龄、处理时限与吞吐 | [消息任务](/back-end/frontend-backend-guide/33-mq-reliability) |

## G. 多实例与依赖故障：分布式词汇

| 术语 | 定义 | 解决什么问题 | 常见误区 | 相关教程 |
| --- | --- | --- | --- | --- |
| Redis | 支持多种数据结构的网络数据存储 | 缓存、计数等低延迟访问场景 | 不是浏览器 sessionStorage；持久性与故障语义需单独配置评估 | [Redis](/back-end/frontend-backend-guide/12-redis-in-practice) |
| Cache-aside | 应用读缓存、未命中读库并回填的模式 | 降低重复读取的数据库负载 | 更新与失效存在竞态，缓存不是天然事实源 | [Redis](/back-end/frontend-backend-guide/12-redis-in-practice) |
| TTL | 数据或租约的存活时间 | 控制缓存新鲜度、自动过期等 | 过期不等于业务已完成，也不保证旧执行者已停止 | [Redis](/back-end/frontend-backend-guide/12-redis-in-practice) |
| 限流 Rate Limiting | 按主体、时间或容量限制流量的服务端策略 | 保护资源、控制滥用与成本 | 不是前端防抖；攻击者可绕过页面，多个实例也要考虑计数范围 | [性能](/back-end/frontend-backend-guide/31-performance-concurrency) |
| 熔断 Circuit Breaker | 故障阈值触发后暂时快速拒绝依赖调用的状态机制 | 减少等待坏依赖引发的资源耗尽 | 不等于 UI Error Boundary，也不负责修好下游 | [排查](/back-end/frontend-backend-guide/28-troubleshooting-playbook) |
| 降级 Fallback | 依赖/资源受限时提供约定的替代能力 | 保住较小但可用的核心体验 | 不能把失败伪装为成功，支付失败不该降级成“已支付” | [性能](/back-end/frontend-backend-guide/31-performance-concurrency) |
| 背压 Backpressure | 下游能力不足时向上游传递减速或拒绝信号 | 防止无界排队耗尽资源 | 有队列不等于有背压，入口仍可能无限受理 | [线程池](/back-end/frontend-backend-guide/15-thread-pools-executor) |
| 服务发现 | 通过名称/注册信息定位可用实例 | 实例变化时不手写每个 IP | 不等于发现后就健康、可认证或不会超时 | [架构](/back-end/frontend-backend-guide/02-architecture-overview) |
| 分布式锁 / 租约 | 多节点协调排他执行的机制 / 有效时间限制 | 限制多个 worker 同时认领等操作 | 锁超时后旧执行者仍可能运行，需幂等、持有者校验或 fencing | [消息任务](/back-end/frontend-backend-guide/33-mq-reliability) |
| 最终一致性 | 无新更新且传播恢复等前提下，副本最终收敛的模型 | 允许异步同步以换取某些可用性/性能 | 不是“过一会自然正确”，需要重试、对账与冲突处理 | [事务](/back-end/frontend-backend-guide/11-transactions-consistency) |
| Saga / 补偿 | 用一系列本地事务及补偿动作协调长业务流程的模式 | 无法单一本地事务覆盖的多步骤业务恢复 | 补偿不是数据库回滚，已发邮件无法撤回成没发生 | [事务](/back-end/frontend-backend-guide/11-transactions-consistency) |

## H. 登录与数据暴露：安全词汇

| 术语 | 定义 | 解决什么问题 | 常见误区 | 相关教程 |
| --- | --- | --- | --- | --- |
| 认证 / 授权 | 验明身份 / 判断允许执行的动作与资源范围 | 区分“是谁”和“能干什么” | 登录成功不代表可以操作任意任务 | [安全](/back-end/frontend-backend-guide/32-security) |
| Session | 服务端管理的会话状态与其引用标识 | 在多次请求中持续识别登录会话 | Cookie 一般放会话 ID，不是把密码或完整身份表塞进去 | [安全](/back-end/frontend-backend-guide/32-security) |
| JWT | 承载声明的标准令牌格式，常见为签名形式 | 在系统间传递可验证的声明 | JWT 不等于 localStorage；解码不等于验签；签名不等于加密 | [安全](/back-end/frontend-backend-guide/32-security) |
| Access / Refresh Token | 资源访问凭据 / 获取新访问凭据的受控凭据 | 平衡短访问寿命与持续登录体验 | 删除 refresh 不自动撤销已签发 access；刷新接口也需安全防护 | [安全](/back-end/frontend-backend-guide/32-security) |
| 密码哈希 / Salt | 专用单向密码验证结果 / 每份密码随机盐 | 降低拖库后批量猜解的效率 | 盐不必保密；裸 SHA、前端哈希和可逆加密不能替代密码哈希 | [安全](/back-end/frontend-backend-guide/32-security) |
| CSRF | 利用浏览器自动携带凭据执行非预期请求的攻击 | 解释 Cookie 写操作为何需要额外请求证明 | JWT 放 Cookie 仍可能有风险；CORS 不等于 CSRF 防护 | [安全](/back-end/frontend-backend-guide/32-security) |
| XSS | 不可信脚本在受害者页面上下文执行 | 解释危险 HTML、模板与不安全输出风险 | HttpOnly 只能阻止读取 Cookie，不能阻止恶意脚本发请求 | [安全](/back-end/frontend-backend-guide/32-security) |
| IDOR / 越权 | 通过直接对象引用访问无权访问的资源等权限缺陷 | 检查“改 ID 就能读别人任务”的漏洞 | 使用 UUID 或隐藏按钮不能代替服务端数据范围检查 | [安全](/back-end/frontend-backend-guide/32-security) |
| MFA | 使用不同类别因素的多因素身份验证 | 降低单一凭据泄露导致接管的风险 | 两个密码不是两个独立因素，恢复流程也要安全 | [安全](/back-end/frontend-backend-guide/32-security) |
| OAuth 2.0 | 让应用获取资源有限访问权的授权框架 | 不把用户第三方密码交给本应用 | OAuth 本身不完整定义登录身份；使用提供方正式流程和成熟库 | [安全](/back-end/frontend-backend-guide/32-security) |
| OIDC | 在 OAuth 2.0 上增加标准身份层的协议 | 统一登录与身份声明验证 | ID Token 不应当作所有业务 API 都接受的 access token | [安全](/back-end/frontend-backend-guide/32-security) |
| SSO | 多个应用复用身份提供方登录状态的架构/体验 | 减少用户跨应用重复登录 | 不是所有应用共享 localStorage；各会话注销范围需设计 | [安全](/back-end/frontend-backend-guide/32-security) |
| SSRF | 服务端被诱导请求攻击者指定目标的攻击 | 防止 URL 转存、Webhook 等功能访问内部资源 | 只检查字符串以 http 开头远远不够，重定向和解析也有风险 | [功能地图](/back-end/frontend-backend-guide/39-backend-feature-map#attachments) |
| 最小权限 | 只授予完成工作所需的权限 | 限制账号或组件被攻破后的影响范围 | “内网”“管理员”“服务账号”都不是无限信任的理由 | [安全](/back-end/frontend-backend-guide/32-security) |

## I. 功能评审：从同事用词回到需求

| 术语 | 定义 | 解决什么问题 | 常见误区 | 相关教程 |
| --- | --- | --- | --- | --- |
| RBAC / ABAC | 按角色授权 / 按主体、资源、环境属性判断的授权模型 | 管理团队权限与更细动态规则 | 两者可结合，不必每条 owner 校验都先上策略引擎 | [协作权限](/back-end/frontend-backend-guide/39-backend-feature-map#collaboration) |
| 多租户 Multi-tenancy | 一套产品服务多个客户组织的隔离设计 | 避免不同客户数据和操作串用 | 不只是加 tenant_id；缓存、文件、搜索、后台任务同样要隔离 | [协作权限](/back-end/frontend-backend-guide/39-backend-feature-map#collaboration) |
| 对象存储 OSS / S3 | 按对象键保存与访问文件等大对象的存储服务 | 附件独立保存、多实例共享、生命周期管理 | 不等于 CDN，也不自动知道谁有权看任务附件 | [附件](/back-end/frontend-backend-guide/39-backend-feature-map#attachments) |
| CDN | 通过边缘缓存等方式分发内容的网络服务 | 降低下载延迟与源站负载 | 不能替代源存储，私有内容也不能不管鉴权与缓存撤销 | [附件](/back-end/frontend-backend-guide/39-backend-feature-map#attachments) |
| 签名 URL | 对特定资源、动作和有效窗口签出的访问能力 | 客户端受限直传/直下，不暴露永久密钥 | 通常持有即可使用，泄露后有风险，也未必可即时撤销 | [附件](/back-end/frontend-backend-guide/39-backend-feature-map#attachments) |
| 分片上传 | 把一个大对象拆成多段传输后完成合并的机制 | 大文件补传、减少单次失败重传量 | 还要校验 uploadId 归属、完成状态和残片清理 | [附件](/back-end/frontend-backend-guide/39-backend-feature-map#attachments) |
| 批处理 Batch | 对一组数据按批组织验证和处理的方式 | 控制导入/导出的资源、事务和恢复粒度 | 不等于全部数据放进一个超大事务 | [导入导出](/back-end/frontend-backend-guide/39-backend-feature-map#batch-jobs) |
| cron / 调度 Scheduler | 时间规则表达式 / 产生执行机会并管理运行的机制 | 定期生成报表与清理任务 | cron 方言有五/六字段等差异；准确触发不等于执行成功 | [导入导出](/back-end/frontend-backend-guide/39-backend-feature-map#batch-jobs) |
| 分布式调度 | 多节点协调任务触发、认领、分片和故障恢复 | 多实例下避免无意重复并分摊作业 | 给每个副本都加 @Scheduled 不是完成协调 | [导入导出](/back-end/frontend-backend-guide/39-backend-feature-map#batch-jobs) |
| ES / 倒排索引 | Elasticsearch 搜索引擎 / 词项到文档的检索结构 | 全文分词、相关性、高亮等搜索需求 | SQL 数据库也有全文索引，ES 不能无成本取代数据库事实源 | [搜索](/back-end/frontend-backend-guide/39-backend-feature-map) |
| Webhook / 支付回调 | 通过 HTTP 主动推送事件 / 支付领域通知 | 系统之间获知事件与支付事实 | 浏览器跳回成功页不是支付事实，通知也可能重复乱序 | [回调](/back-end/frontend-backend-guide/39-backend-feature-map#webhooks) |
| 签名 / 防重放 | 验证消息来源与完整性 / 限制旧消息再利用 | 拦截伪造和恶意重放请求 | 签名正确仍可能是旧消息；防重放也不代替业务幂等 | [回调](/back-end/frontend-backend-guide/39-backend-feature-map#webhooks) |
| 状态机 / 工作流 | 状态及合法转移模型 / 多步骤业务协作编排 | 管控任务完成、审核、退回等流程 | 有 status 字段不代表能随意转移；引擎不自动回滚外部副作用 | [工作流](/back-end/frontend-backend-guide/39-backend-feature-map) |
| 软删除 | 保留记录并标记为已删除的策略 | 回收站与误删恢复 | 不是物理删除，也不自动满足隐私擦除要求 | [审计与删除](/back-end/frontend-backend-guide/39-backend-feature-map) |
| 审计 / 数据脱敏 | 可追踪的操作记录 / 限制字段暴露的处理 | 追责与降低个人数据泄露范围 | 普通日志不自动防篡改，脱敏不等于加密或匿名化 | [审计与删除](/back-end/frontend-backend-guide/39-backend-feature-map) |
| 站内信 / 短信 / 邮件 | 应用内持久通知与外部触达渠道 | 提醒不在线用户或展示未读状态 | MQ 成功、供应商受理、实际送达是不同阶段 | [通知](/back-end/frontend-backend-guide/39-backend-feature-map) |
| 数据字典 | 业务编码及展示项等可管理元数据 | 维护优先级、分类等选项 | 改展示名不应任意更换稳定 code，也不必一切做成动态字典 | [配置功能](/back-end/frontend-backend-guide/39-backend-feature-map) |
| Feature Flag | 服务端受控的功能启用与定向放量开关 | 灰度上线、实验或紧急关闭 | 不是授权；关闭后不撤销既有副作用，旧开关也需清理 | [配置功能](/back-end/frontend-backend-guide/39-backend-feature-map) |

## J. 代码到了服务器：容器、配置与发布

| 术语 | 定义 | 解决什么问题 | 常见误区 | 相关教程 |
| --- | --- | --- | --- | --- |
| Maven / pom.xml | Java 项目构建与依赖管理工具 / 其项目描述文件 | 编译、测试、解析依赖与打包 | 与 pnpm 有部分相似职责，但生命周期和产物不是简单同名替换 | [Java](/back-end/frontend-backend-guide/05-java-crash-course) |
| 环境配置 / Profile | 按运行环境选择的参数与配置集合 | 本地 H2、测试/生产 MySQL 等差异 | 非秘密配置与密钥应区分；环境名不是权限边界 | [配置](/back-end/frontend-backend-guide/25-config-and-env) |
| 镜像 Image | 用于启动容器的只读分层文件系统模板与配置 | 固化应用及其运行依赖 | 不包含一个自带独立内核的完整虚拟机 | [Docker](/back-end/frontend-backend-guide/23-docker-in-practice) |
| Docker / Container | 容器工具生态 / 隔离进程及资源视图的运行实例 | 统一交付运行环境和资源边界 | Linux 容器共享所运行宿主的内核；macOS Docker Desktop 常通过 Linux VM 承载，不是每容器一套内核 | [Docker](/back-end/frontend-backend-guide/23-docker-in-practice) |
| Dockerfile | 声明镜像构建步骤的文件 | 可复现地组织基础镜像与应用产物 | 把密钥 COPY 进去后再删除仍可能留在镜像层中 | [Docker](/back-end/frontend-backend-guide/23-docker-in-practice) |
| Pod | Kubernetes 的最小可部署单元，含一个或多个共享部分运行上下文的容器 | 调度协同运行的容器 | 不是独立小虚拟机，Pod 也会被替换、地址会变 | [Kubernetes](/back-end/frontend-backend-guide/24-kubernetes-in-practice) |
| Deployment | 管理一组 Pod 副本与更新策略的控制器 | 声明副本数、滚动更新与恢复 | 不自动确保业务无损、数据库迁移兼容或请求排空 | [Kubernetes](/back-end/frontend-backend-guide/24-kubernetes-in-practice) |
| Service（Kubernetes） | 为一组后端提供稳定访问抽象的资源 | 访问会变化的 Pod 集合 | 不等于 Java 业务 Service，也不自动暴露为公网入口 | [Kubernetes](/back-end/frontend-backend-guide/24-kubernetes-in-practice) |
| Ingress | 对 HTTP(S) 外部访问规则的声明，需控制器落实 | 按域名、路径把流量送到 Service | 只有 YAML 没有控制器并不会自动产生入口 | [Kubernetes](/back-end/frontend-backend-guide/24-kubernetes-in-practice) |
| ConfigMap / Secret | Kubernetes 的非秘密配置 / 敏感数据承载资源 | 分开管理运行配置与凭据 | Secret 的 Base64 不是加密，仍需 RBAC、静态加密、轮换等保护 | [配置](/back-end/frontend-backend-guide/25-config-and-env) |
| Readiness / Liveness | 是否可接流量 / 是否需要重启的探测语义 | 避免未就绪接流量，并恢复异常进程 | 把所有下游故障都当存活失败可能引发重启风暴 | [Kubernetes](/back-end/frontend-backend-guide/24-kubernetes-in-practice) |
| OOMKilled / CrashLoopBackOff | 内存相关强杀原因 / 反复退出后的重启退避状态 | 识别容器为何不可用 | 不等同 Java 堆 OOM；后者也不是具体根因，要看退出原因和历史日志 | [排查手册](/back-end/frontend-backend-guide/28-troubleshooting-playbook) |
| CI/CD | 持续集成与持续交付/部署的一组实践 | 自动验证、打包、发布并保留证据 | 自动部署不自动提供测试质量、审批、回滚或数据恢复 | [交付](/back-end/frontend-backend-guide/36-cicd-deployment) |
| 滚动 / 蓝绿 / 金丝雀发布 | 分批替换 / 双环境切换 / 小流量验证的发布策略 | 在成本、验证和切换风险间权衡 | 不是都天然零停机；共享数据库变更仍可能无法回滚 | [交付](/back-end/frontend-backend-guide/36-cicd-deployment) |

## K. 线上出问题：观测与排查词汇

| 术语 | 定义 | 解决什么问题 | 常见误区 | 相关教程 |
| --- | --- | --- | --- | --- |
| Log 日志 | 带时间和上下文的离散事件记录 | 回答具体发生了什么 | 大量 DEBUG 不等于可排查；密码、token、签名 URL 不该完整记录 | [日志](/back-end/frontend-backend-guide/26-reading-logs) |
| Metric 指标 | 按时间聚合的数值序列 | 看速率、趋势、容量与错误比例 | 不适合把每个 taskId、userId 都变成标签 | [可观测](/back-end/frontend-backend-guide/30-observability) |
| Trace / Span | 一次操作链路及其中的一个步骤 | 定位请求在服务、数据库、外部调用间的耗时 | 单体也可追踪；采样与未埋点会造成不完整视图 | [可观测](/back-end/frontend-backend-guide/30-observability) |
| traceId / requestId | 关联链路 / 请求的标识，具体语义按系统约定 | 串起一次操作的日志与追踪 | 不可用作认证凭据，也不等于业务幂等键 | [日志](/back-end/frontend-backend-guide/26-reading-logs) |
| QPS / TPS | 每秒查询/请求量 / 每秒事务或处理量，需先约定口径 | 衡量入口和业务处理吞吐 | 一次 HTTP 可能产生多条 SQL，不同系统的 TPS 不能直接比较 | [性能](/back-end/frontend-backend-guide/31-performance-concurrency) |
| P95 / P99 | 给定窗口内延迟分布的第 95/99 百分位 | 看平均值掩盖的尾部延迟 | 不是最大值，也不能直接平均多个实例的 P99 得到全局 P99 | [可观测](/back-end/frontend-backend-guide/30-observability) |
| Histogram | 按桶记录分布的指标类型 | 聚合延迟并估算分位数 | 桶边界和采样口径会影响精度，不是任意精确分位 | [可观测](/back-end/frontend-backend-guide/30-observability) |
| Cardinality 基数 | 指标标签值组合的数量 | 估算时序数量与存储/查询成本 | 每请求一个新标签值会让时序爆炸，详细 ID 更适合日志 | [可观测](/back-end/frontend-backend-guide/30-observability) |
| SLI / SLO | 服务质量度量指标 / 对该指标设定的目标 | 以用户体验定义可靠性和告警 | 不是 CPU 使用率目标的同义词，需要时间窗口和统计口径 | [可观测](/back-end/frontend-backend-guide/30-observability) |
| 错误预算 Error Budget | 按 SLO 在窗口中允许的不达标量 | 协调变更速度与可靠性投入 | 不是可以随意制造故障的额度 | [可观测](/back-end/frontend-backend-guide/30-observability) |
| 假设验证 / 根因分析 | 用证据淘汰假设并分析机制的排查方法 | 避免看到慢就加内存、看到错就重启 | 时间先后、日志相关性不自动证明因果 | [排查方法](/back-end/frontend-backend-guide/27-troubleshooting-methodology) |
| Runbook | 常见故障的诊断、处置与恢复手册 | 减少临场试错并明确安全操作 | 不是只有命令列表；应写前提、风险、验证与回退 | [排查手册](/back-end/frontend-backend-guide/28-troubleshooting-playbook) |

## L. 进入公司内网：运维接入与权限

| 术语 | 定义 | 解决什么问题 | 常见误区 | 相关教程 |
| --- | --- | --- | --- | --- |
| 公网与内网 | 公网通过互联网寻址，内网按公司管理的网络范围访问 | 判断在家怎样连接公司的服务器 | 私有地址未必能直达；在内网也要检查身份和权限 | [内网接入与权限](/back-end/frontend-backend-guide/22c-private-network-and-access) |
| VPC | 云平台上可以自己配置的一片逻辑隔离网络 | 安排应用和数据库放在哪里、怎样连接 | 同一 VPC 中仍可能有访问限制，跨 VPC 还需配置通路 | [内网接入与权限](/back-end/frontend-backend-guide/22c-private-network-and-access) |
| 子网与路由表 | 子网划分地址范围，路由表决定数据下一步往哪里送 | 检查请求能否找到目标，以及响应能否返回 | 还要看回程、地址是否重叠和访问规则 | [内网接入与权限](/back-end/frontend-backend-guide/22c-private-network-and-access) |
| VPN | 为电脑或网络之间建立受保护通道的接入方式 | 在家按公司配置访问内部网络 | 连上 VPN 后，目标服务器仍会检查登录身份和权限 | [内网接入与权限](/back-end/frontend-backend-guide/22c-private-network-and-access) |
| 零信任接入ZTNA | 根据账号、设备等条件，开放指定内部资源 | 只让员工访问工作需要的某个应用 | 安装客户端不代表永久可信；应用本身仍需授权 | [内网接入与权限](/back-end/frontend-backend-guide/22c-private-network-and-access) |
| 跳板机Jump Host | 帮你中转到另一台机器的主机 | 连接不能从自己电脑直达的服务器 | 中转功能本身不包含完整审批和录像；目标仍需验证身份 | [内网接入与权限](/back-end/frontend-backend-guide/22c-private-network-and-access) |
| 堡垒机Bastion | 企业里常指管理服务器访问和操作记录的平台 | 统一登录入口，管理谁可以进入哪些机器 | 权限和记录范围要看配置；英文名称有时仅指加固跳板主机 | [内网接入与权限](/back-end/frontend-backend-guide/22c-private-network-and-access) |
| PAM特权访问管理 | 管理管理员等高权限账号怎样被使用 | 保管和轮换密码，临时授权，到期收回 | 要确认哪些账号已被接管，未覆盖的访问仍有风险 | [内网接入与权限](/back-end/frontend-backend-guide/22c-private-network-and-access) |
| SSH | 能加密连接、核对服务器和登录身份的远程访问协议 | 登录 Linux 服务器、执行命令或获准的转发 | 登录成功后，也只能做该账号被允许的操作 | [内网接入与权限](/back-end/frontend-backend-guide/22c-private-network-and-access) |
| 公钥与私钥 | 配对的密钥；SSH 登录时用私钥签名、公钥验证 | 不用共享服务器密码，也能验证登录者 | 私钥留在受控设备，不上传跳板或仓库；泄露后要撤销授权并换钥 | [内网接入与权限](/back-end/frontend-backend-guide/22c-private-network-and-access) |
| 主机指纹与known_hosts | 指纹用来核对主机公钥，known_hosts 保存已信任的记录 | 确认自己连接的是预期服务器 | 指纹变化先向管理员核实，不直接删记录或关闭验证 | [内网接入与权限](/back-end/frontend-backend-guide/22c-private-network-and-access) |
| SSH Agent与Agent转发 | Agent 代你签名；转发允许远端请求这个签名能力 | 减少重复输入密钥口令 | 远端可能冒用签名能力，默认不转发；单纯跳转通常用 ProxyJump 即可 | [内网接入与权限](/back-end/frontend-backend-guide/22c-private-network-and-access) |
| SSH隧道与端口转发 | 经 SSH 连接，把一个端口收到的数据转送到目标 | 按批准的方式，让本机工具连接内部数据库 | 仍需数据库账号；只监听本机回环，不能用它绕过审计 | [内网接入与权限](/back-end/frontend-backend-guide/22c-private-network-and-access) |
| 安全组 | 云平台中限制哪些网络连接可以通过的规则 | 例如只让应用机器访问数据库端口 | 各云的方向、状态和返回流量规则可能不同，先查产品说明 | [内网接入与权限](/back-end/frontend-backend-guide/22c-private-network-and-access) |
| 防火墙 | 在主机或中间网络设备上，按规则放行或拦截流量 | 检查连接是否在途中被拦住 | 放行一处还不代表全程畅通，不要为排错关闭全部防护 | [内网接入与权限](/back-end/frontend-backend-guide/22c-private-network-and-access) |
| 网络ACL | 按地址、协议和端口等条件允许或拒绝流量的规则列表 | 在网段等位置控制连接 | 顺序和状态规则依产品而定；无状态规则也要考虑回程 | [内网接入与权限](/back-end/frontend-backend-guide/22c-private-network-and-access) |
| IP白名单allowlist | 列出允许访问的来源 IP 或网段 | 让数据库或供应商接口只接受指定来源 | 填对方实际看到的 IP；共用一个出口的人仍需分别认证 | [内网接入与权限](/back-end/frontend-backend-guide/22c-private-network-and-access) |
| NAT与出口IP | NAT 转换网络地址，出口 IP 是对方看到的来源地址 | 解释为何电脑上的地址和白名单里要填的地址不同 | 多台电脑可能共用出口，出口也可能变化；地址不等于身份 | [内网接入与权限](/back-end/frontend-backend-guide/22c-private-network-and-access) |
| IAM | 管理账号、角色和访问权限的体系 | 决定谁能在云平台查看机器、修改配置等 | 云控制台权限和服务器、数据库账号要分别确认 | [内网接入与权限](/back-end/frontend-backend-guide/22c-private-network-and-access) |
| 运维审计与会话录像 | 在支持范围内记录谁访问了哪台机器、做了什么 | 查变更和故障时回看操作过程 | 先确认是否覆盖这条连接、记录保存多久，以及谁能查看 | [内网接入与权限](/back-end/frontend-backend-guide/22c-private-network-and-access) |

## 查到意思以后，再放回原来的问题

比如同事说“导出放到后台，通过 MQ 通知 worker”，先知道 MQ 传消息、worker 做导出，再去 [后台任务](./33-mq-reliability) 看它们怎样配合。没有必要顺着这个词，把整套消息系统的术语都读一遍。

如果只是想把一个功能做出来，可以直接从 [功能地图](./39-backend-feature-map) 找对应需求。需要按顺序学习时，则回到 [课程地图](./index)。

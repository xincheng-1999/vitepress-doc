---
prev:
  text: "B2. Spring、Bean 与注入"
  link: "/back-end/frontend-backend-guide/06-spring-boot-ioc-di"
next:
  text: "B4. 可运行任务 CRUD"
  link: "/back-end/frontend-backend-guide/07-build-a-crud-api"
---

# 三层分工与陌生工程阅读方法

> 学习目标：拿到一个 Spring 项目，知道从哪找入口、配置和接口，并知道业务代码应该放在哪一层。

前置：[Spring 与依赖注入](./06-spring-boot-ioc-di)。接下来使用 [任务 API 实验](./07-build-a-crud-api) 的真实工程，而不是假设你已经有一套微服务。

## 1. 三层不是三台服务器

![Controller 处理 HTTP，Service 处理业务与事务，Repository 处理 SQL，DTO 隔离输入输出](/backend-guide/core-layer-map.svg)

| 层 | 主要责任 | 不应该混进去 |
| --- | --- | --- |
| Controller | HTTP 参数、状态码、响应头、调用业务服务 | SQL 字符串、复杂业务流程 |
| Service | 业务规则、授权、事务与用例编排 | 直接依赖 Servlet 请求对象、把用户状态存在单例字段 |
| Repository / DAO / Mapper | 参数化 SQL、数据映射、存取操作 | 决定谁有权限、拼装 HTTP 响应 |

这是帮助维护和测试的常见设计，不是语言铁律。小例子可以简化；复杂领域也可能用领域对象和应用服务分担规则。**Service 会通过 Repository 访问数据库，也必须理解事务和查询成本**，不能把「不写 SQL 细节」误读成「不用理解数据库」。

Controller 比较接近 Next.js Route Handler / Express handler，而不是页面组件。Service 可类比抽出的业务函数，但不带 UI 生命周期。Repository 是服务器端的数据访问层，不是浏览器 axios 的完整等价物。

## 2. 先认工程骨架，再沿调用链读

配套源码在仓库根目录 `examples/backend-task-api`：

```text
backend-task-api/
├── pom.xml                       # 版本、依赖、构建插件
├── Dockerfile                    # 容器构建与启动
├── compose.yml                   # 本地 MySQL + API 编排
└── src/
    ├── main/
    │   ├── java/dev/learning/tasks/
    │   │   ├── TaskApplication.java      # main() 入口
    │   │   ├── TaskController.java       # HTTP 路由
    │   │   ├── TaskService.java          # 业务方法、事务
    │   │   ├── TaskRepository.java       # JDBC / SQL
    │   │   ├── TaskModels.java           # 请求、响应、枚举
    │   │   ├── ApiException.java         # 业务异常
    │   │   ├── ApiExceptionHandler.java  # HTTP 异常映射
    │   │   └── RequestLogFilter.java     # 请求 ID 与访问日志
    │   └── resources/
    │       ├── application.yml          # 本地 H2 配置
    │       ├── application-mysql.yml    # MySQL 练习配置
    │       └── schema.sql               # 初始教学表
    └── test/java/dev/learning/tasks/
        └── TaskApiTest.java             # 自动 HTTP 集成测试
```

为减少第一个项目的文件跳转，相关类先放同一个包，数据类型放同一文件；工程大了可以按 `task/`、`user/` 业务模块，再在模块内分层。**目录名不决定职责，调用依赖才决定。**

阅读顺序：

1. **README / 构建文件**：需要哪个 JDK，怎么启动，哪些外部依赖？
2. **配置**：监听哪里、端口多少、连哪个库？不要先把密码复制到笔记。
3. **Controller + DTO**：这个接口接什么、返什么、认证从哪来？
4. **Service**：规则在哪、是否有事务、是否调外部服务？
5. **Repository + schema**：实际 SQL、约束和索引是什么？
6. **异常处理 + 测试**：错误如何返回，哪些行为有自动验证？

配置与异常处理是运行闭环的一部分，不建议一概跳过。没必要先从 `utils` 开始逐文件阅读。

## 3. 为什么输入、存储、输出不全用一个对象

以用户为例：

- `RegisterRequest` 允许提交密码，但不能提交 `role=ADMIN`。
- 数据库存 `password_hash`，不存明文密码。
- `UserResponse` 不返回明文密码，也不返回哈希。

常见名字：

| 名字 | 含义 | 注意 |
| --- | --- | --- |
| DTO | Data Transfer Object，传输对象 | 可以是请求也可以是响应，不只入参 |
| Entity / PO | 持久化模型 | 不应默认直接暴露给客户端 |
| VO / Response | 展示 / 响应对象 | VO 在领域建模里也可能指 Value Object，问清语境 |
| BO / Domain Model | 业务对象 / 领域模型 | 不是每个 CRUD 都需要再加一层 |
| DAO / Mapper / Repository | 数据访问相关抽象 | 不同框架用法有区别，不必为同一操作全建一套 |

## 4. Service 接口 + Impl 是否必需

不是。Spring 可以直接注入具体类。只有一个实现、没有抽象收益时，不必机械创建 `TaskService` 接口和 `TaskServiceImpl`。

当确实要表达边界、替换实现时再引入接口。单元测试也不强制每个类都要有接口；构造器注入就已经让依赖显式可替换。

同理，统一响应壳 `{code,message,data}` 是团队契约，不是 Spring 强制要求。本课程成功直接返回资源，错误返回 `{code,message,requestId}`，同时保留 HTTP 状态。选修里的 `RtData` 只是另一种项目约定。

## 动手：在陌生项目里找到一个「纵切面」

选 `POST /api/tasks`，写下对应 Controller、请求 DTO、Service、Repository 和 SQL，最后找到一条测试。不要试图先理解全部工程。

**预期**：能在纸上写出一条清晰调用链，知道哪些类由 Spring 创建，哪些数据由请求创建。

## 自测与参考答案

1. 三层意味着三个独立进程吗？**不意味着，本例都在同一个 Java 进程。**
2. 改数据库类型只改 Repository 就一定够吗？**不一定，事务语义、查询模型、约束与业务能力可能一起变化。分层减少影响，不消除所有差异。**
3. 每个 Service 必须有 Impl 吗？**不必，为实际替换和边界需求抽象，不为目录整齐增加间接层。**

下一站：[动手运行任务 API](./07-build-a-crud-api)。

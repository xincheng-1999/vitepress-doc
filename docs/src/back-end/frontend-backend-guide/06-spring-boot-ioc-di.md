---
prev:
  text: "B1. Java 最小知识集"
  link: "/back-end/frontend-backend-guide/05-java-crash-course"
next:
  text: "B3. 三层分工与工程阅读"
  link: "/back-end/frontend-backend-guide/04-three-layer-and-structure"
---

# Spring、Bean 与构造器注入

> 学习目标：说清 Java、Spring Framework、Spring Boot、Spring MVC、Tomcat 各做什么；理解为什么不在每个 Controller 里手动组装数据库依赖。

前置：[Java 最小知识集](./05-java-crash-course)。本章示意代码用于理解，完整可运行工程在 [CRUD 实验](./07-build-a-crud-api)。

## 1. 先把 Spring 家族拆开

| 名字 | 职责 | 与任务 API 的关系 |
| --- | --- | --- |
| Java / JDK | 语言、编译与运行工具 | 编写并运行应用 |
| Spring Framework | 对象管理、依赖注入、事务、Web 等基础能力 | 组装业务对象和基础设施 |
| Spring Boot | 自动配置、starter、打包和应用启动约定 | 减少手工搭建成本 |
| Spring MVC | 基于 Servlet 的 Web 框架 | 路由、参数绑定、校验、响应转换 |
| Tomcat | Servlet 容器和 HTTP 服务能力 | 实际监听端口，承接 HTTP 请求 |
| Spring Security | 认证、授权及常见 Web 安全防护 | 后续安全练习引入，不是 Boot 自动替你实现业务权限 |
| Spring Cloud | 分布式系统相关组件整合 | 第一轮不需要 |

「Spring Boot 自带数据库、鉴权和缓存」容易误导：**能力取决于你引入的依赖和配置**，不会替你凭空提供一个可用的 MySQL 或安全业务系统。

## 2. 不用容器，依赖原本怎么组装

假设任务操作依赖数据库：

```java
DataSource dataSource = /* 创建数据源，配置数据库地址 */;
JdbcTemplate jdbc = new JdbcTemplate(dataSource);
TaskRepository repository = new TaskRepository(jdbc);
TaskService service = new TaskService(repository);
TaskController controller = new TaskController(service);
```

这叫**组合根 / 应用组装**。手工组装并不坏，问题是对象多时，需要统一处理创建顺序、配置、生命周期、重复实例和关闭资源。

- **IoC（控制反转）**：由框架负责对象生命周期和组装，而不是每个业务类自己决定。
- **DI（依赖注入）**：把所需依赖从外部传进来，是实现 IoC 的一种方式。
- **IoC 容器**：Spring 负责管理对象的机制。
- **Bean**：Spring 容器管理的对象，不是「所有 Java 对象」的别名。

`CreateTaskRequest` 是每次请求创建的数据对象，通常不是容器 Bean。`TaskService` 是长期复用的 Bean。

## 3. 构造器写「我需要什么」，Spring 负责连接

```java
@Service
class TaskService {
    private final TaskRepository repository;

    TaskService(TaskRepository repository) {
        this.repository = repository;
    }
}
```

```java
@RestController
@RequestMapping("/api/tasks")
class TaskController {
    private final TaskService service;

    TaskController(TaskService service) {
        this.service = service;
    }
}
```

Spring 扫描到这些组件，先解决它们的依赖，再把对象连接起来。**只有一个构造器时通常不必再加 `@Autowired`。**

与前端的 `provide/inject` 有「集中提供依赖」的相似处，但不是组件树 Context 的完整等价物：这里是后端对象图、容器生命周期和作用域，也不提供 React 式 UI 更新。

为什么优先构造器注入：依赖一目了然，可用 `final` 固定引用，测试可直接 `new TaskService(fakeRepository)`，不需要为了一个类启动整个应用。

## 4. 单例到底在哪里「单」

Spring 默认 scope 为 singleton：**同一个容器中同一 Bean 定义通常只有一个实例**。不是「全世界只有一个」，不是所有同类型对象都只能一个，也不是跨进程共享。

两个应用实例各自有自己的 `TaskService`。同一个应用内，多个请求可能同时调用同一个 Service。

```java
@Service
class WrongService {
    private String currentUser; // 错：请求专属数据放到共享对象
}
```

把用户、DTO、临时结果放在方法参数和局部变量里。依赖字段 `final` 只保证引用不被换掉，**不保证依赖内部和数据库业务自动线程安全**。

request scope、prototype 等其他作用域先知道存在即可；别为了绕开理解共享状态，给所有 Bean 随意改 scope。

## 5. 注解和自动配置怎么配合

| 声明 | 意义 |
| --- | --- |
| `@Component` | 通用组件，组件扫描可识别 |
| `@Service` | 表达业务服务角色，本质是组件的一种 |
| `@Repository` | 表达数据访问角色，还可参与持久层异常转换机制 |
| `@Configuration` + `@Bean` | 显式提供对象定义，常用于第三方类和自定义组装 |
| `@SpringBootApplication` | 包含配置、组件扫描和自动配置相关开关 |

自动配置不是「猜中一切」。例如，引入 JDBC starter 后，Boot 根据 classpath、属性和已有 Bean 等条件配置 DataSource、JdbcTemplate 和事务管理器；配置不对仍可能启动失败。不同自动配置让位规则也不同，别把所有情况都概括成「自己定义就一定覆盖」。

**starter** 是一组依赖约定，不是一个独立运行服务。`spring-boot-starter-jdbc` 不会替你启动 MySQL 服务器。

## 6. AOP 与事务代理，先记一个边界

`@Transactional` 常通过代理在方法调用前后开启事务、提交或回滚。它不是把方法体改造成能撤销所有外部副作用的魔法。

- 注解生效依赖对象由框架管理、合适的事务管理器、调用经过代理等条件。
- 同类内部 `this.method()` 通常绕过代理，不能用它触发一个新的事务拦截。
- 数据库事务不负责撤销已经发出的 HTTP、邮件或 MQ 消息。

[事务与一致性](./11-transactions-consistency) 再做实验；此时先认识「业务对象外面可能还有一层代理」。

## 动手：观察对象组装失败

在实验副本里临时删除 `TaskService` 的 `@Service`，重新启动。

**预期**：Controller 的构造器需要 TaskService，但容器中没有合适 Bean，应用启动失败，常见信息是找不到所需 Bean。恢复注解后再运行。

如果新建组件不在主类包或子包里，默认扫描可能也找不到它。先查包结构与注解，别一上来加各种扫描配置。

## 自测与参考答案

1. `new CreateTaskRequest(...)` 为什么可以自己写？**DTO 是普通数据对象，并非所有对象都需要 Spring 管理。**
2. Service 是单例，是否自动线程安全？**否。共享可变状态与外部存储的并发规则仍需设计。**
3. Spring Boot、Tomcat 和 Nginx 是一回事吗？**不是。Boot 组装应用，Tomcat 运行 Servlet Web 应用，Nginx 可位于外部做代理。**

下一站：[工程结构与三层分工](./04-three-layer-and-structure) → [可运行任务 API](./07-build-a-crud-api)。

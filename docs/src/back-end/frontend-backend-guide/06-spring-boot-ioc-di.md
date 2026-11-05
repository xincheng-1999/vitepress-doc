---
prev:
  text: "B1. Java 最小知识集"
  link: "/back-end/frontend-backend-guide/05-java-crash-course"
next:
  text: "B3. 三层分工与工程阅读"
  link: "/back-end/frontend-backend-guide/04-three-layer-and-structure"
---

# 谁来创建 TaskService？认识 Spring 的对象管理

前面见过 Java 的构造器。创建一个对象时，可以把它需要的另一个对象传进去。

但打开 Spring 项目，你会发现 Controller 已经在使用 Service，却很少看见哪里写了 `new TaskService(...)`。这一课就把这件事讲清楚。

## 1. 如果自己创建，应该怎样连接？

假设 Controller 需要 Service，Service 又需要 Repository。手工创建时，大致会这样写：

```java
// 这里只演示组装顺序，省略了数据库连接配置。
TaskRepository repository = new TaskRepository(jdbc);
TaskService service = new TaskService(repository);
TaskController controller = new TaskController(service);
```

先准备底层依赖，再把它传给使用它的对象。对象少时，这很直接；应用大了，还要管理创建顺序、配置和资源关闭，重复写起来会越来越麻烦。

Spring 可以把这些对象统一创建和管理。交给它管理的对象，就叫 **Bean**。

## 2. 类声明需要什么，Spring 负责传进来

先看 Service 的写法。下面的片段只展示依赖怎样声明，业务方法暂时省略：

```java
@Service
class TaskService {
    private final TaskRepository repository;

    TaskService(TaskRepository repository) {
        this.repository = repository;
    }
}
```

`@Service` 告诉 Spring：这是一个需要管理的业务组件。构造器中的 `TaskRepository` 表示，创建它之前要先准备好这个依赖。

Controller 也是同样的写法：

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

应用启动时，Spring 扫描到这些组件，找到合适的依赖并完成创建。于是 Controller 拿到了 Service，Service 又拿到了 Repository。只有一个构造器时，通常不用再加 `@Autowired`。

这种“依赖由外部传进来”的做法，叫**依赖注入，DI**。把创建和组装的工作交给框架管理，则是**控制反转，IoC**的一个常见体现。先理解代码发生了什么，再记这两个名字就容易了。

## 3. 为什么依赖通常放字段，用户数据却不能放？

Spring 把这套对象管理环境叫作“Spring 容器”，不是部署时的 Docker 容器。默认情况下，同一个 Spring 容器会为同一个 Bean 定义复用一个实例。也就是说，很多请求可能使用同一个 `TaskService` 对象。

Repository 是这个服务长期需要的依赖，可以作为字段保存。但“这次请求是谁发的”，只属于当前请求。如果写成下面这样，就可能在不同用户之间串数据：

```java
@Service
class WrongService {
    private String currentUser; // 不要把请求身份放进共享字段。
}
```

请求参数、当前用户和临时结果，应通过对应请求的参数、局部变量或安全上下文传递。`final` 只能让一个字段引用不被重新赋值，不会自动让里面的对象变得线程安全。

并不是每个 Java 对象都要成为 Bean。每次请求收到的 `CreateTaskRequest`，是装数据的普通对象，不需要像 Service 一样长期复用。

## 4. Spring Boot 又替我们做了什么？

到这里，我们讲的是 Spring 怎样管理对象。搭一个 Web 应用还需要准备 HTTP 服务、JSON 转换、数据库访问等配置。

**Spring Boot** 提供了一套常用的依赖组合和自动配置方式。比如，引入 Web starter 后，可以较方便地启动一个使用 Spring MVC 和内嵌 Tomcat 的应用：Tomcat 接收 HTTP，Spring MVC 负责路由和参数处理。

这里的 **starter** 是一组依赖约定。引入 JDBC starter 后，Boot 可以根据依赖、配置和已有对象，准备数据库访问相关组件，但 MySQL 本身仍需要另外运行。自动配置是在条件满足时帮助组装，不是把所有外部服务也一起安装好。

自己写的类可以用 `@Service`、`@Repository` 等注解让扫描发现。第三方类不方便加注解时，也可以在配置中用 `@Bean` 声明怎样创建。

## 5. 对象没创建成功时，先检查哪里？

最常见的一类启动失败，就是“找不到某个依赖”。例如 Controller 的构造器需要 Service，但 Service 没有被 Spring 发现。

等运行后面的示例时，可以在自己的实验副本里暂时移除 `@Service`，观察启动错误，再恢复它。你会看到：依赖没有准备好，应用就无法完成组装。

这时先看类有没有相应注解，以及是否放在启动类所在包或其子包中。不要一开始就增加一堆扫描配置，先确认 Spring 为什么没找到它。

::: details 以后看到事务注解时，还需要知道什么？
Spring 常通过对象外的一层代理，在业务方法调用前后安排事务。调用要经过相应代理，事务管理器等配置也要满足条件，所以同类内部的普通调用通常不会重新触发这层拦截。

这部分现在不必展开。学到 [数据库事务](./11-transactions-consistency) 时，我们会结合实际更新说明。数据库事务也不能替你撤销已经发出的邮件或 HTTP 请求。
:::

现在已经知道这些对象怎样被连接起来。下一篇：[打开项目，沿一个接口看三层分工](./04-three-layer-and-structure)。

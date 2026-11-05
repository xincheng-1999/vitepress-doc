---
prev:
  text: "B2. Spring、Bean 与注入"
  link: "/back-end/frontend-backend-guide/06-spring-boot-ioc-di"
next:
  text: "B4. 可运行任务 CRUD"
  link: "/back-end/frontend-backend-guide/07-build-a-crud-api"
---

# 拿到一个后端项目，先从哪里看？

上一课知道了，Spring 会创建对象，并把它们需要的依赖连接起来。这次打开配套的任务项目，沿着一个新增接口，看看这些对象分别在做什么。

## 1. 先确定项目怎样启动

源码在仓库根目录的 `examples/backend-task-api`。先看它的说明和构建配置，确认需要 JDK 17、Maven，以及启动命令。再看应用配置，确认它监听本机 8080，默认使用 H2 内存数据库。

这样做，是为了先知道自己正在读哪一个应用、它会连到哪里。不要一打开项目，就从工具类目录逐个看起。

下面只列出这次要看的部分：

```text
backend-task-api/
├── pom.xml                              # 依赖和构建方式
└── src/
    ├── main/
    │   ├── java/dev/learning/tasks/
    │   │   ├── TaskApplication.java      # 启动入口
    │   │   ├── TaskController.java       # 接收 HTTP 请求
    │   │   ├── TaskService.java          # 处理任务业务
    │   │   ├── TaskRepository.java       # 读写数据库
    │   │   ├── TaskModels.java           # 请求、响应和状态类型
    │   │   └── ApiExceptionHandler.java  # 把异常转换成响应
    │   └── resources/
    │       ├── application.yml          # 本地运行配置
    │       └── schema.sql               # 教学数据表
    └── test/java/dev/learning/tasks/
        └── TaskApiTest.java             # 接口自动测试
```

## 2. 从接口入口找到业务方法

我们要看的是 `POST /api/tasks`。先在 Controller 中找 `@PostMapping`，就能找到接收新增请求的方法。

它做的事情很短：接收并校验请求，调用 `service.create(request)`，然后把结果和 `201` 状态返回给前端。

这里的 **Controller** 就是 HTTP 入口。如果你写过 Express handler 或 Next.js Route Handler，可以先把它理解为类似的入口位置，而不是页面组件。

接着沿着 `service.create(...)` 找到 Service。创建 ID、设置 TODO 初始状态、处理标题、保存任务，这些规则放在这里。将来要加“谁能创建”“哪些状态允许修改”，也要在后端业务处理中判断。

这就是为什么不把所有逻辑都写在 Controller 里：同一个业务操作以后可能由另一个接口或后台任务调用，不需要重新复制一套 HTTP 处理代码。

## 3. 再跟到真正写数据库的地方

Service 并不把标题直接拼进 SQL，而是调用 Repository。Repository 把任务字段作为参数交给 JDBC，再由数据库执行插入。

这样，一次新增就串起来了：

```text
Controller 接请求 → Service 处理规则 → Repository 执行 SQL
```

这就是常说的**三层分工**。本例的三层都在同一个 Java 进程里，之间是方法调用，不是三台服务器或三个微服务。

![Controller 接收 HTTP，Service 处理业务，Repository 访问数据库，请求和响应类型传递数据](/backend-guide/core-layer-map.svg)

Repository 集中处理 SQL 和数据映射，Service 仍要理解查询成本和事务。分层是为了让修改更容易定位，不是让每一层对其他部分一无所知。

有些团队把数据访问相关的类叫 DAO 或 Mapper。看到这些名字时，先看它在做什么，不必给同一个数据库操作同时建三套类。

## 4. 请求和响应，为什么还要单独定义类型？

回头看新增接口。前端只提交标题，后端却会返回 ID、标题、状态和创建时间。如果把存储对象的所有字段都接受为输入，客户端就可能修改本来应该由服务器决定的值。

因此，输入和输出通常分别定义：请求类型写清允许提交什么，响应类型写清允许返回什么。这些传输数据的对象常被叫作 **DTO**。

本项目把相关类型集中放在同一个文件，减少初学时的跳转。项目变大以后，也可以按任务、用户等功能分包，再在功能内部安排 Controller、Service 和 Repository。

你还可能看到 Entity、PO、VO 等名字。它们在团队中的约定不完全一样，先问“这个对象用于接收请求、执行业务，还是保存数据”，比只背缩写更有用。初次阅读也不用机械地为每个操作创建所有类型。

## 5. 最后看错误和测试

正常路径跟完以后，再看空标题或任务不存在时会发生什么。业务代码抛出相应异常，异常处理把它转换成前端能理解的 HTTP 状态和错误消息。

然后打开测试，找到创建任务的那一条。测试会把请求真正交给应用处理，并检查返回状态和数据。这可以帮助你确认刚才读到的代码，实际约定了哪些行为。

以后看陌生项目，也可以用同样的顺序：先确认怎样运行，再选一个接口，从 Controller 跟到 Service、Repository，最后看异常和测试。一次看明白一个功能，不用先理解全部目录。

::: details 补充：Service 是否一定要有接口和 Impl？
不一定。只有一个实现、暂时没有替换需要时，可以直接注入具体类。本课程就是这样做的。确实需要表达接口或替换实现时，再增加抽象。

响应外面是否再包一层 `{code,message,data}`，也属于团队约定。本项目成功时直接返回任务，错误时返回 `{code,message,requestId}`，并保留 HTTP 状态。部分选修文章中的 `RtData` 是其他项目的写法，不是启动本项目所缺的依赖。
:::

下一篇：[运行任务 API](./07-build-a-crud-api)。这次不只看代码，而是实际发请求，观察这些类怎样一起工作。

---
prev:
  text: "A7. 请求进入 Spring 之后"
  link: "/back-end/frontend-backend-guide/03-request-lifecycle"
next:
  text: "B2. Spring、Bean 与注入"
  link: "/back-end/frontend-backend-guide/06-spring-boot-ioc-di"
---

# 先读懂任务代码里用到的 Java

前面沿请求看过了后端的处理过程。这一篇先用几段小程序，认识后面会出现的 Java 写法。目标不是一次学完整门语言，而是能读懂任务对象、列表和业务方法。

## 1. 先确认电脑能够编译和运行

开发 Java 需要 **JDK**，它包含编译器和运行工具。课程按 JDK 17 编写，可以从 [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=17) 选择对应系统和 CPU 架构的版本。

接着安装 [Maven 3.6.3+](https://maven.apache.org/install.html)。后面的工程用它下载依赖、运行测试和打包；现在的小程序暂时不需要 Maven。

在终端检查：

```bash
java -version
javac -version
mvn -version
```

`java` 用来运行，`javac` 用来编译，Maven 也会使用某个 JDK。这里都应使用课程所需的兼容版本。如果 `java` 显示 17，Maven 却显示 8，先检查 `JAVA_HOME` 和 `PATH`，让它们指向正确安装，不用急着改工程依赖。

## 2. 跑一个最小程序

在练习目录新建 `TaskDemo.java`，写入：

```java
public class TaskDemo {
    public static void main(String[] args) {
        System.out.println("开始学习后端");
    }
}
```

然后在这个目录执行：

```bash
javac TaskDemo.java
java TaskDemo
```

编译成功会生成 `TaskDemo.class`，运行后打印“开始学习后端”。`.class` 是交给 Java 虚拟机 JVM 执行的字节码；后面的 Maven 工程会把编译好的类和依赖打包成可运行的 JAR。

这里先记住两件事：`class` 定义类，`main` 是这个小程序的启动入口。Java 17 也能用 `java TaskDemo.java` 直接运行这个单文件，但正式项目仍交给 Maven 管理。

## 3. 用一个类型表示任务，再放进列表

现在把刚才的文件内容替换为下面的完整代码，再编译运行：

```java
import java.util.ArrayList;
import java.util.List;

public class TaskDemo {
    public static void main(String[] args) {
        List<Task> tasks = new ArrayList<>();
        tasks.add(new Task("学习网络", TaskStatus.TODO));
        tasks.add(new Task("学习 SQL", TaskStatus.DOING));

        for (Task task : tasks) {
            System.out.println(task.title() + "：" + task.status());
        }
    }
}

record Task(String title, TaskStatus status) {}
enum TaskStatus { TODO, DOING, DONE }
```

这次会打印两条任务。我们顺着程序解释：

`record Task(...)` 定义了一个装数据的类型，里面有标题和状态。`new Task(...)` 创建一条任务，`task.title()` 读取标题。record 会生成构造器和这些访问方法，很适合后面看到的请求、响应对象。它是实际运行的 Java 类型，不是 TypeScript 中只参与类型检查的 interface。

`enum TaskStatus` 列出了状态的候选值。在 Java 代码里，只能把这个枚举允许的值传到对应位置。不过，HTTP 输入仍需要转换和校验，不能只靠编译时类型检查处理外部数据。

`List<Task>` 表示一个元素为 Task 的列表，`ArrayList` 是它的一种实现。`add` 添加元素，`for (Task task : tasks)` 依次取出列表中的任务。最上面的 import 用来告诉编译器这些集合类型来自哪里。

## 4. 业务类中的字段和构造器怎样读？

record 适合装数据；需要执行业务操作时，常见的是普通 class。比如下面是一个类的简化片段：

```java
class TaskService {
    private final TaskRepository repository;

    TaskService(TaskRepository repository) {
        this.repository = repository;
    }
}
```

先不用管 Repository 内部怎么实现。这里的含义是：TaskService 需要一个 TaskRepository，创建时由构造器传进来，然后保存在字段中供后续方法使用。

构造器和类同名，没有返回类型。`this.repository` 指当前对象的字段，右边的 `repository` 是构造器参数。`private` 让外部不能随意访问这个字段，`final` 表示字段引用赋值后不能再换成另一个引用。

后面读 Spring 时，最重要的就是认出这段写法：**这个类需要什么依赖，以及依赖从哪里传进来。**

## 5. 看到校验和异常时，按方法读下去

创建任务的方法里可能有这样的片段：

```java
Task createTask(String title) {
    if (title == null || title.isBlank()) {
        throw new IllegalArgumentException("标题不能为空");
    }
    return new Task(title.strip(), TaskStatus.TODO);
}
```

方法接收一个 String，返回一个 Task。标题无效时抛出异常，后面的创建就不会继续执行；正常时去掉两端空白，返回 TODO 状态的任务。

`null` 表示没有对象。调用一个可能为空的对象之前要先判断，否则可能出现 `NullPointerException`。Java 没有 JS 中的 `undefined`，也不能把任意值当作布尔条件。

字符串内容一般用 `.equals()` 比较，而不是用 `==` 判断两个引用是不是同一个对象。可能为空时，可以用 `Objects.equals(a, b)`，并导入 `java.util.Objects`。

异常可以用 `try/catch` 处理。后面的 Web 工程会在合适的位置，把业务异常转换成 HTTP 错误响应；不要在每个方法里都捕获后假装成功。

## 6. 读到 @ 开头的写法，先看谁在使用它

任务代码里还会出现 `@Service`、`@PostMapping`、`@Valid` 这类注解。它们给框架提供信息：哪些类需要管理，哪个方法处理请求，哪些参数需要校验。

注解本身不是一段自动执行的业务代码。后面使用 Spring 时，我们会看到框架怎样根据这些信息工作。

::: details 其他常见写法，用到时再查
- `int` 是基本整数类型，不能放 null；`Integer` 是包装类型，可以为空，但空值拆箱时会出错。基本数值类型和对象引用要分清。
- `long` 可以表示的整数范围比 JS 安全整数范围更大。任务接口直接用 UUID 字符串作为 ID，避免前端精度问题。金额通常使用约定的最小货币单位整数，或按币种和舍入规则使用 BigDecimal。
- `Map<String, Integer>` 是按键查值的集合。泛型中使用引用类型，所以写 `Integer` 而不是 `int`。
- Stream 的 `filter`、`map` 可以处理集合。Java 17 的 `toList()` 返回不可修改的列表，和 JS 数组并不完全一样；需要时再看 [集合](/back-end/java/04-collections) 与 [Stream](/back-end/java/04a-lambda-stream)。
- Java interface 声明一组能力，实现类用 `implements` 明确实现。它不像 TypeScript 常用的结构类型匹配，也不是要求每个 Service 都必须有一个接口。
- record 的组件引用不能重新赋值，但如果组件本身是可变集合，集合内容仍可能改变。普通字段的 final 也有同样的区别。
- `Optional<T>` 用来表达查询可能没有结果。看到 `orElseThrow(...)`，可以读成“没有值时抛出指定异常”，不要默认 `.get()` 一定有值。
- 普通受检异常需要捕获或用 throws 声明，RuntimeException 不要求调用方强制声明。手动打开的资源通常用 try-with-resources 关闭，详细写法见 [异常与资源处理](/back-end/java/05-exception)。
:::

下一篇：[Spring 怎样创建和连接这些对象](./06-spring-boot-ioc-di)。

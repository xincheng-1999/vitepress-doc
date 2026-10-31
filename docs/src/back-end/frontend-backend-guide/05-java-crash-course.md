---
prev:
  text: "A7. 请求进入 Spring 之后"
  link: "/back-end/frontend-backend-guide/03-request-lifecycle"
next:
  text: "B2. Spring、Bean 与注入"
  link: "/back-end/frontend-backend-guide/06-spring-boot-ioc-di"
---

# Java 最小知识集：从 TypeScript 读懂后端

> 学习目标：能读懂下一课的任务 API，不追求背完语言。基线使用 **Java 17**，不混入需要预览开关或更高版本的语法。

前置：[请求链路](./03-request-lifecycle)。语法细节按需查 [Java 专题](/back-end/java/02-syntax-basics)。

## 1. 先把工具装对：JDK、JVM、Maven

- **JVM** 是运行 Java 字节码的虚拟机。
- **JDK** 包含编译器 `javac` 和运行工具；开发安装 JDK 17，不是只装旧 Java 8。
- **Maven** 读取 `pom.xml`，解析依赖、运行测试、构建产物。类比 pnpm 的部分职责，但依赖解析与锁版本机制并不相同。
- **JAR** 是 Java 打包格式；Spring Boot 的可执行 JAR 包含应用和运行所需依赖，由 JVM 执行，不是浏览器静态文件。

从 [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=17) 安装适合操作系统和 CPU 架构的 JDK，再从 [Maven 官方安装说明](https://maven.apache.org/install.html) 安装 Maven 3.6.3+。也可使用 IDE 的 JDK 管理，但命令行仍要检查：

```bash
java -version
javac -version
mvn -version
```

**预期**：三者使用兼容的 JDK，示例按 17 验证。`java` 显示 17 但 Maven 显示 8，通常是 `JAVA_HOME` 和 `PATH` 指向不同安装。先修环境，不要改依赖版本碰运气。

## 2. 从一个可以直接运行的文件开始

保存为 `Hello.java`：

```java
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello backend");
    }
}
```

```bash
javac Hello.java
java Hello
```

会生成字节码 `Hello.class`，然后输出 `Hello backend`。Java 17 也支持 `java Hello.java` 直接运行单文件；正式工程仍交给 Maven。

业务工程中的 `package dev.learning.tasks;` 是包声明；`import java.util.List;` 按类型名导入，不是 JS 那种相对文件路径导入。主类所在包下的组件默认会被 Spring Boot 扫描。

## 3. 数字、字符串、null：最常见的坑

```java
int count = 10;                     // 32 位整数，不是所有 ID 都够用
long userId = 9007199254740993L;    // 64 位整数，给 JS 传递时常转字符串
Integer optionalCount = null;      // 包装类型可以为空
boolean done = false;              // 没有 truthy/falsy
String title = "学 SQL";           // String 是类，不是基本类型
```

- 基本类型 `int` 不能为 `null`；`Integer` 为 `null` 时拆箱成 `int` 会抛 `NullPointerException`。
- Java 没有 JS 的 `undefined`。局部变量必须先赋值再读，字段有类型默认值。
- Java `long` 可能超出 JS `number` 的安全整数范围，**接口 ID 建议用字符串**。主线直接使用 UUID 字符串。
- 金额不要用 `double`；常见选择是最小货币单位整数，或从字符串构建 `BigDecimal` 并明确币种与舍入规则。

```java
String a = new String("task");
String b = new String("task");
System.out.println(a == b);       // false，比较引用是否同一个对象
System.out.println(a.equals(b));  // true，String 按内容比较
```

基本类型用 `==` 比值；引用对象的内容比较取决于类的 `equals` 实现。可用 `Objects.equals(a, b)` 处理可能为空的对象。数组内容用 `Arrays.equals`，不是所有对象 `.equals()` 都自动深比较。

## 4. class、record 和 interface 不要混淆

```java
class TaskService {
    private final TaskRepository repository;

    TaskService(TaskRepository repository) {
        this.repository = repository;
    }
}
```

- `class` 定义运行时类，`new` 创建对象。
- 构造器没有返回类型，与类同名。
- `private` 限制成员可见性；不写修饰符通常是包可见，不是默认 public。
- `final` 字段只能赋值一次，**不代表字段指向的对象内部不可变**。

```java
record CreateTaskRequest(String title) {}
var request = new CreateTaskRequest("学 SQL");
System.out.println(request.title());  // 访问器是 title()，不是 getTitle()
```

`record` 在 Java 16 正式引入，是简洁的运行时数据类；自动生成构造器、访问器、`equals/hashCode/toString`。组件引用是 final，但如果组件是可变 List，仍能修改其内容，所以是**浅不可变**，不是 TS interface，也不是自动深冻结。

```java
interface TaskRepository {
    String findTitle(String id);
}
```

Java 接口用于声明能力；实现类需显式 `implements`，不是 TS 常用的结构类型匹配。这里只认识，主线不强制给每个类创建接口。

## 5. List、Map、泛型与 Stream

```java
List<String> titles = new ArrayList<>();
titles.add("网络");
titles.add("SQL");
System.out.println(titles.get(0));  // 网络
System.out.println(titles.size());  // 2

Map<String, Integer> counts = new HashMap<>();
counts.put("TODO", 3);
System.out.println(counts.getOrDefault("DONE", 0));
```

需要导入 `java.util.List`、`ArrayList`、`Map`、`HashMap`。泛型只能放引用类型，所以是 `List<Integer>`，不是 `List<int>`。

Java 泛型多通过类型擦除实现；不是所有泛型信息都能在运行时直接拿到。也不要把 TS「类型标注被擦除」理解成 Java 就自动替你校验网络输入，JSON 仍要绑定和验证。

```java
List<String> names = titles.stream()
    .filter(t -> t.length() > 1)
    .map(String::toUpperCase)
    .toList();
```

`->` 是 Lambda；`String::toUpperCase` 是方法引用。Stream 是惰性管线，不调用终端操作不会完成计算。Java 17 的 `toList()` 返回不可修改的 List，不是 JS `map` 返回的可随意 push 数组。

## 6. Optional 与异常

```java
Optional<TaskResponse> task = repository.find(id);
return task.orElseThrow(() -> new IllegalStateException("任务不存在"));
```

`Optional<T>` 显式表达可能没有值；不要直接 `.get()` 假设一定存在。它不是强制空安全类型系统，Java 仍允许很多空引用。

`try/catch/finally` 结构与 JS 类似；Java 的 catch 写异常类型。普通受检异常如 `IOException` 要捕获或 `throws` 声明；`RuntimeException` 不要求调用方强制声明。

资源应通过 `try-with-resources` 等机制释放。数据库框架通常替你借还连接，但手动获取的资源仍需按 API 约定关闭。异常不要只吞掉返回成功。

## 7. 注解：元数据，不是自动执行的魔法

```java
@PostMapping
TaskResponse create(@Valid @RequestBody CreateTaskRequest request) { /* ... */ }
```

- `@PostMapping` 由 Spring 识别为路由映射。
- `@RequestBody` 由参数解析机制绑定 JSON。
- `@Valid` 配合校验框架、字段约束和依赖，才触发校验。
- 没有处理它的编译器、框架或运行机制，自己写个注解不会自动生效。

## 动手与自测

1. 运行 Hello，再把字符串赋给 `int`，观察编译错误。**类型不匹配应在编译时阻止。**
2. 把 `Integer n = null;` 拆箱成 `int`，观察异常。**编译能通过不代表运行无错。**
3. 用一个 record 保存标题，再放进 List，用 Stream 得到标题列表。**你能读懂这些，就足以进入下一课。**

进一步练习与语法见 [集合](/back-end/java/04-collections)、[Lambda / Stream](/back-end/java/04a-lambda-stream)、[异常](/back-end/java/05-exception)、[Maven](/back-end/java/06-maven)。

下一站：[Spring、Bean 与构造器注入](./06-spring-boot-ioc-di)。

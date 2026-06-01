# Java 速通（面向前端）

> 目标：用最少的 Java 知识，让你能**读写 Spring Boot 业务代码**——看懂 svc-user 里的扣配额逻辑、能给 svc-canvas 加一个返回任务状态的方法。本章不追求全面，只求够用；想深挖语法细节，文末有深链。

你已经熟练 JS/TS，所以这章不会从「什么是变量」讲起。我们只讲 Java 和你脑子里那套 JS 模型**最不一样**的地方，剩下的你看一眼就会。

先记住贯穿全章的三句话，后面每一节都在重复印证：

1. **强类型、编译期**：类型写错、方法名拼错，代码根本编译不过去，到不了运行时。这跟 TS 的体感像，但 TS 的类型运行时会被擦除，Java 的类型是真的存在、运行时也带着。
2. **没有 `undefined`，只有 `null`**：Java 里一个对象引用要么指向真实对象，要么是 `null`。没有「声明了没赋值就是 undefined」这种第三态。
3. **一切都在类里**：没有顶层函数、没有顶层变量。所有代码都必须写在某个 `class`（或 `record`/`interface`）里面。这点对前端最反直觉。

---

## 1. 静态类型与基本类型

### 前端类比

`int age = 18;` ≈ TS 的 `const age: number = 18`，但 Java 的类型不是给编辑器看的注解，是语言地基。而且 Java 把「数字」拆成了好几种类型，不像 JS 只有一个 `number`。

### 基本类型 vs 包装类型

Java 有 8 种**基本类型（primitive）**，存的是值本身，不是对象。业务代码里你天天用的就这几个：

```java
int    count   = 10;       // 32 位整数；ID、数量、配额都用它
long   userId  = 100L;     // 64 位整数；时间戳、雪花 ID 必须用 long（注意结尾 L）
double price   = 19.99;    // 小数；金额其实更推荐 BigDecimal，见下文
boolean ok     = true;     // 只有 true/false，不能用 0/1/null 当布尔
String name    = "Jack";   // 注意 String 首字母大写——它是类，不是基本类型
```

⚠️ 和 JS 最容易踩的坑：

- JS 里 `1` 和 `1.0` 都是 `number`；Java 里 `1` 是 `int`、`1.0` 是 `double`，类型不同。
- 超过约 21 亿的整数必须用 `long`，否则溢出。svc-canvas 的任务 ID、时间戳一律 `long`。
- `boolean` 不能写 `if (count)`（前端常用的真值判断），必须 `if (count > 0)`。Java 没有 truthy/falsy。

每个基本类型都有一个**包装类型（boxed type）**——它是对象，能为 `null`：

| 基本类型 | 包装类型 | 说明 |
| :--- | :--- | :--- |
| `int` | `Integer` | 集合里只能放包装类型，如 `List<Integer>` |
| `long` | `Long` | |
| `double` | `Double` | |
| `boolean` | `Boolean` | 可以是 `true`/`false`/`null` 三态 |

前端类比：基本类型像 `number`（永远有值），包装类型像 `number | null`（可能没值）。

```java
int a = 5;            // 基本类型，永远有值
Integer b = null;     // 包装类型，可以是 null
int c = b;            // 💥 NullPointerException！拆箱 null 会炸
```

这就是著名的「自动拆箱 NPE」：`b` 是 `null`，赋给基本类型 `int` 时 Java 偷偷调用 `b.intValue()`，于是空指针。**业务代码里从数据库/Feign 拿到的字段几乎都是包装类型，用之前先判空。**

> 金额不要用 `double`：`0.1 + 0.2` 在 Java 里同样等于 `0.30000000000000004`。svc-user 算支付金额用 `java.math.BigDecimal`，和前端用 `decimal.js` 是一个道理。

---

## 2. class / 构造器 / record / POJO

### 前端类比

Java 的 `class` ≈ TS 的 `class`；Java 的 `record` ≈ TS 的 `interface` + 只读字段（一个纯数据结构）；POJO ≈ 你 axios 请求里那个 `interface UserDTO`。

### 普通 class 与构造器

```java
public class User {
    private Long id;        // 字段默认 private
    private String name;
    private Integer quota;  // 剩余配额

    // 构造器：方法名必须和类名一致，没有返回类型
    public User(Long id, String name, Integer quota) {
        this.id = id;
        this.name = name;
        this.quota = quota;
    }

    // getter / setter：业务代码靠它们读写字段
    public Integer getQuota() { return quota; }
    public void setQuota(Integer quota) { this.quota = quota; }
}
```

对照 TS：

```typescript
class User {
  constructor(
    public id: number,
    public name: string,
    public quota: number,
  ) {}
}
const u = new User(1, "Jack", 10);
```

```java
// Java：new 关键字不能省，类型要写两遍（左边声明，右边构造）
User u = new User(1L, "Jack", 10);
```

> 真实项目里没人手写 getter/setter，而是用 **Lombok** 的 `@Data`、`@Getter` 注解自动生成。你会在 svc-* 的 DTO 上看到一堆 `@Data`，先知道「它帮我生成了 getter/setter/构造器」即可，细节见后续章节。

### record：一行定义不可变数据类

Java 14+ 的 `record` 专门用来装数据，自动带 getter（叫访问器）、构造器、`equals`、`toString`。cpt-api 里的请求/响应 DTO 越来越多用它：

```java
// 一行搞定：等价于带 3 个 final 字段 + 构造器 + 访问器 + equals 的 class
public record CreateTaskReq(String prompt, Integer width, Integer height) {}
```

```java
CreateTaskReq req = new CreateTaskReq("a cat", 512, 512);
String p = req.prompt();   // 访问器叫 prompt()，不是 getPrompt()
```

前端类比：`record` 就像 TS 里 `type CreateTaskReq = { prompt: string; width: number; height: number }`，但它是真正的运行时类型、字段只读、自带值相等比较。

---

## 3. package、import 与程序入口

### package 与 import

Java 用 `package` 组织代码，对应物理目录结构。`import` 像 ES 模块的 `import`，但导入的是**类**不是文件。

```java
package com.example.svcuser.service;   // 第一行：声明本文件属于哪个包

import com.example.cptcommon.RtData;   // 导入其他包里的类
import java.util.List;                 // 导入 JDK 自带的类

public class UserService { /* ... */ }
```

对照前端：

```typescript
// JS/TS：从文件路径导入
import { RtData } from "../../cpt-common/RtData";
```

```java
// Java：从「全限定类名」导入，com.xxx.yyy 对应目录 com/xxx/yyy
import com.example.cptcommon.RtData;
```

区别：JS 导的是文件里 `export` 的东西，路径相对；Java 导的是类，用点分包名，和磁盘目录一一对应（`com.example.cptcommon` → `com/example/cptcommon/`）。同包下的类无需 import。

### main 方法：程序入口

JS 文件从上到下直接执行；Java 必须有一个固定签名的 `main` 方法作为入口：

```java
public class Application {
    public static void main(String[] args) {   // 签名一字不能差
        System.out.println("svc-user 启动");
    }
}
```

Spring Boot 的每个服务都有这么一个 `main`，里面只有一行 `SpringApplication.run(...)`。你启动 svc-canvas，本质就是 JVM 调用了它的 `main`。`System.out.println(...)` ≈ `console.log(...)`。

---

## 4. 访问修饰符

### 前端类比

和 TS 的 `public`/`private` 几乎一样，只是 Java 多一个**默认（包级）**修饰符，而且不写时默认就是包级而非 public。

| 修饰符 | 谁能访问 | 类比 |
| :--- | :--- | :--- |
| `public` | 任何地方 | TS `public` |
| `private` | 仅本类内部 | TS `private` |
| `protected` | 本类 + 子类 + 同包 | TS `protected`（略宽） |
| 不写 | 同包内 | TS 没有对应 |

经验法则（够用版）：**字段写 `private`，对外方法写 `public`，内部辅助方法写 `private`。** Spring 的 Controller/Service 类和它们对外的方法都是 `public`。

---

## 5. null 与 Optional

这是和前端差异最大的地方，单独拎出来讲。

### Java 只有 null，没有 undefined

```java
String name = null;          // 合法：引用为空
int n;                       // 局部变量必须先赋值才能用，否则编译报错
// System.out.println(n);    // 💥 编译不过：variable n might not have been initialized
```

JS 里读未赋值变量得到 `undefined`，对象上不存在的属性也是 `undefined`；Java 里这两种情况要么编译报错（局部变量），要么字段是类型的默认值（对象字段 `null`、`int` 默认 `0`、`boolean` 默认 `false`）。**所以 Java 没有可选链 `?.`，碰 `null` 就直接 NPE。**

```java
User u = userService.findById(1L);   // 可能返回 null
String name = u.getName();           // 如果 u 是 null，这行直接 NullPointerException
```

### 防御写法对照

前端你用 `?.` 和 `??`：

```typescript
const name = user?.name ?? "匿名";
```

Java 老实写法 + Optional 写法：

```java
// 老实写法：手动判空
String name = (u != null && u.getName() != null) ? u.getName() : "匿名";

// Optional 写法：≈ ?. 链 + ?? 兜底
String name = Optional.ofNullable(u)
        .map(User::getName)      // 类似 ?.name
        .orElse("匿名");          // 类似 ?? "匿名"
```

`Optional<T>` 是一个「可能装了值、也可能空」的盒子，前端类比就是把 `T | null` 显式包了一层，强迫你处理空的情况。Spring Data 的查询方法常返回 `Optional<User>`，提醒你「查不到很正常，自己兜底」。

> 不要对 Optional 调 `.get()` 不判断——`Optional.empty().get()` 同样抛异常，等于没防。要么 `.orElse(...)`、要么 `.orElseThrow(...)`。

---

## 6. 泛型：List\<T\> 与 Map\<K, V\>

### 前端类比

和 TS 泛型几乎一样：`List<User>` ≈ `User[]` / `Array<User>`，`Map<String, Integer>` ≈ `Map<string, number>` / `Record<string, number>`。尖括号语法都一样。

```typescript
// TS
const ids: number[] = [1, 2, 3];
const cache: Map<string, number> = new Map();
```

```java
// Java：接口类型在左，具体实现在右 new
List<Long> ids = new ArrayList<>();          // <> 叫菱形运算符，右边类型可省略
Map<String, Integer> cache = new HashMap<>();
```

注意几点：

- 泛型里**只能放引用类型**，所以是 `List<Integer>` 不是 `List<int>`、`List<Long>` 不是 `List<long>`。
- 左边声明的是**接口**（`List`/`Map`），右边 new 的是**实现类**（`ArrayList`/`HashMap`）。这是面向接口编程的习惯，方法签名也一律写接口类型。
- 和 TS 不同，Java 泛型在运行时会被擦除，但写代码时编译器一样帮你卡类型。

---

## 7. 集合：List / Map / Set 常用操作

### 前端类比

`List` ≈ JS 数组，`Map` ≈ JS 的 `Map`（或当对象用的 `{}`），`Set` ≈ JS 的 `Set`。但操作方法名不一样，下面是查得最多的对照。

```java
// List：有序可重复，最常用
List<String> tags = new ArrayList<>();
tags.add("cat");                 // 数组的 push
tags.get(0);                     // 取下标，不能用 tags[0]
tags.size();                     // 长度，不是 .length
tags.contains("cat");            // includes
tags.isEmpty();                  // length === 0
for (String t : tags) { }        // 遍历（增强 for，下一节细讲）

// Map：键值对
Map<String, Integer> quota = new HashMap<>();
quota.put("u1", 10);             // 设置，不能用 quota["u1"] = 10
quota.get("u1");                 // 取值，key 不存在返回 null
quota.getOrDefault("u9", 0);     // ≈ map.get("u9") ?? 0
quota.containsKey("u1");         // has
quota.remove("u1");              // delete

// Set：去重集合
Set<Long> seen = new HashSet<>();
seen.add(1L);
seen.contains(1L);
```

对照表（高频差异）：

| 操作 | JS 数组/Map | Java |
| :--- | :--- | :--- |
| 取元素 | `arr[0]` / `map.get(k)` | `list.get(0)` / `map.get(k)` |
| 长度/大小 | `arr.length` / `map.size` | `list.size()` / `map.size()` |
| 是否包含 | `arr.includes(x)` | `list.contains(x)` |
| key 不存在 | 返回 `undefined` | 返回 `null` |
| 带默认值取 | `map.get(k) ?? d` | `map.getOrDefault(k, d)` |

⚠️ 取下标越界（`list.get(99)` 而列表只有 3 个）抛 `IndexOutOfBoundsException`，不像 JS 返回 `undefined`。

---

## 8. 增强 for 与 Stream（对比数组方法）

### 增强 for ≈ for...of

```typescript
for (const task of tasks) console.log(task.id);
```

```java
for (Task task : tasks) {        // 冒号读作「in」，等价 for...of
    System.out.println(task.getId());
}
```

### Stream ≈ 数组链式方法

你熟练的 `arr.filter().map().reduce()`，Java 里对应 `stream().filter().map().collect()`。核心区别：Java 要先 `.stream()` 开流，最后 `.collect(...)` 收回成集合。

前端写法：

```typescript
const names = users
  .filter(u => u.quota > 0)
  .map(u => u.name);
```

Java 写法：

```java
List<String> names = users.stream()
        .filter(u -> u.getQuota() > 0)        // 箭头是 ->，不是 =>
        .map(User::getName)                   // 方法引用，等价 u -> u.getName()
        .collect(Collectors.toList());         // 收尾成 List；Java 16+ 可写 .toList()
```

方法名对照：

| 用途 | JS 数组 | Java Stream |
| :--- | :--- | :--- |
| 过滤 | `.filter(fn)` | `.filter(pred)` |
| 映射 | `.map(fn)` | `.map(fn)` |
| 求和/聚合 | `.reduce(...)` | `.reduce(...)` / `.mapToInt().sum()` |
| 查找一个 | `.find(fn)` | `.filter(p).findFirst()`（返回 `Optional`） |
| 去重 | `[...new Set(arr)]` | `.distinct()` |
| 收尾 | （直接得数组） | `.collect(...)` / `.toList()` |

> 箭头函数体差异：JS `u => u.quota > 0`，Java `u -> u.getQuota() > 0`——胖箭头变细箭头，字段访问要走 getter。`User::getName` 是「方法引用」，是 `u -> u.getName()` 的简写。

---

## 9. 异常：try / catch / finally / throws

### 前端类比

`try/catch/finally` 和 JS 一模一样；多出来的是**受检异常（checked exception）**和 `throws` 声明——这是 JS 完全没有的概念。

```typescript
// JS：catch 拿到的是 any/unknown，谁都能 throw 任何东西
try {
  await callAi();
} catch (e) {
  console.error(e);
} finally {
  cleanup();
}
```

```java
try {
    aiClient.generate(req);
} catch (FeignException e) {        // catch 必须声明捕获的异常类型
    log.error("调用 svc-ai 失败", e);
} catch (Exception e) {             // 兜底：可以写多个 catch，从具体到宽泛
    log.error("未知错误", e);
} finally {
    // 一定会执行，常用于释放资源
}
```

差异点：

- JS 的 `catch (e)` 抓所有；Java 必须写**捕获哪个异常类型**，可以列多个 catch，顺序从具体到宽泛。
- Java 异常分两类：**RuntimeException（非受检）**如 `NullPointerException`，可以不处理；**受检异常**如 `IOException`，方法若可能抛出，要么 `try/catch`，要么在方法签名上用 `throws` 声明往外抛——编译器强制，理念上像「把可能失败显式标注出来」。

```java
// throws：声明「我这个方法可能抛 IOException，调用方你处理」
public byte[] readFile(String path) throws IOException {
    return Files.readAllBytes(Path.of(path));
}
```

业务里你更常见的是**自定义业务异常**：cpt-common 通常定义一个带异常码的 `BizException`，Service 里配额不足就 `throw new BizException(...)`，由全局异常处理器统一转成 `RtData.fail(...)` 返回给前端。这套机制后续章节会展开。

---

## 10. equals 与 == 的坑（重点）

### 前端类比

JS 的 `===` 对原始值比内容、对对象比引用。Java 的 `==` **永远只比较「是不是同一个东西」**：基本类型比值，引用类型比内存地址。比内容要用 `.equals()`。

```java
String a = new String("hi");
String b = new String("hi");

a == b;          // false！两个不同对象，地址不同
a.equals(b);     // true：比的是内容
```

```java
int x = 3, y = 3;
x == y;          // true：基本类型比值，没问题
```

记死一条规则：

- **基本类型（int/long/boolean/double）用 `==`** 比较值，没问题。
- **引用类型（String、自定义类、包装类型）一律用 `.equals()`** 比较内容。

最常坑人的两个场景：

```java
// 1. 字符串比较——登录校验用户名时千万别用 ==
if ("admin".equals(username)) { }       // ✅ 把常量放前面，避免 username 为 null 时 NPE

// 2. 包装类型 Long 比较——配额、ID 判等
Long a = 1000L, b = 1000L;
a == b;          // false！超出缓存范围的 Long 是不同对象
a.equals(b);     // true
```

> 第二个坑极其隐蔽：`Long` 在 -128~127 之间会被缓存，`==` 碰巧为 true，一旦数值变大就 false。所以 svc-user 里比较 userId、配额这类 `Long`/`Integer`，永远用 `.equals()` 或先取基本值 `longValue()` 再 `==`。

---

## 把这些拼成一段真实业务代码

下面是一段简化的 svc-user「扣配额」逻辑，本章学的点几乎全用上了——你现在应该能逐行读懂：

```java
public RtData<Integer> deductQuota(Long userId, Integer cost) {
    // null 防御（第 5 节）
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new BizException("用户不存在"));   // Optional + 自定义异常（第 9 节）

    // 包装类型 + 比较（第 1、10 节）：余额不足
    if (user.getQuota() == null || user.getQuota() < cost) {
        return RtData.fail("配额不足");                          // 统一响应
    }

    user.setQuota(user.getQuota() - cost);                      // getter/setter（第 2 节）
    userRepository.save(user);
    return RtData.ok(user.getQuota());                          // 泛型 RtData<Integer>（第 6 节）
}
```

---

## 小结

- **强类型 + 编译期**：类型/方法名错了根本编译不过；写代码先想清楚类型，比 JS 多一道防线。
- **没有 undefined 只有 null**，引用类型碰 null 就 NPE；包装类型自动拆箱、Map 取不存在的 key 都可能给你 null，用前判空或上 `Optional`。
- **一切在类里**：没有顶层函数和变量，程序从某个类的 `main` 进入，业务逻辑都挂在 Service 等类的方法上。
- **集合与 Stream** 就是「数组 + filter/map」换套语法：`.stream().filter().map().collect()`，箭头从 `=>` 变 `->`。
- **比较只认 `.equals()`**：除基本类型外（尤其 String、Long、Integer），比内容一律 `.equals()`，`==` 比的是地址。

### 自测

1. `Integer b = null; int c = b;` 这两行会发生什么？为什么？换成 `int b = ...` 还会有这个问题吗？
2. 在 Java 里如何优雅地表达前端的 `user?.name ?? "匿名"`？写出两种写法（手动判空 / Optional）。
3. `Long a = 1000L, b = 1000L;`，`a == b` 的结果是什么？正确的判等该怎么写？为什么这个坑只在数值较大时才暴露？

### 下一章

类型和语法已经够用，接下来要理解 Spring Boot 是怎么帮你「自动 new 对象、自动注入依赖」的——进入 [Spring Boot 与 IoC/DI](/back-end/frontend-backend-guide/06-spring-boot-ioc-di)。

---

> 本章只取了能读写业务代码的最小集。想系统补全语法细节，按需深入：[Java 基础语法](/back-end/java/02-syntax-basics)、[集合框架](/back-end/java/04-collections)、[Lambda 与 Stream](/back-end/java/04a-lambda-stream)、[异常处理](/back-end/java/05-exception)。

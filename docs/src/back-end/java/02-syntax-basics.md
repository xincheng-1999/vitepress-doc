# 02. 基础语法：Java vs JS

> 目标：掌握变量定义、数据类型、流程控制，对比 JS 的异同。

Java 的语法其实和 JavaScript 非常像（毕竟 JS 当年就是蹭 Java 热度起名的），但在**类型系统**上有本质区别。

## 1. 变量声明：强类型 vs 弱类型

在 JS 中，你习惯用 `let` 或 `const`，变量类型由值决定（推断）。
在 Java 中，**必须显式声明变量类型**，且类型一旦确定不能改变。

### 对比

| 特性 | JavaScript | Java |
| :--- | :--- | :--- |
| 变量定义 | `let age = 18;` | `int age = 18;` |
| 常量定义 | `const PI = 3.14;` | `final double PI = 3.14;` |
| 类型改变 | `age = "18";` (允许) | `age = "18";` (❌ 报错：类型不兼容) |

### Java 常用代码
```java
int count = 10;             // 整数
double price = 19.99;       // 小数 (双精度)
boolean isLogin = true;     // 布尔值
String name = "Jack";       // 字符串 (注意 S 大写，是类)
char grade = 'A';           // 字符 (单引号，只能存一个字)
```

> **注意**：Java 10 引入了 `var` 关键字 (`var name = "Jack"`), 类似 TS 的类型推断，但初学者建议先写全类型，养成好习惯。

## 2. 数据类型：基本 vs 引用

Java 的数据类型分两大类，这点非常重要，直接影响你后面的理解。

### 2.1 基本数据类型 (Primitive Types)
Java 有 8 种基本类型，它们**不是对象**，存的是**值本身**。

*   `int` (整数), `long` (长整数, 后面加 L, 如 `100L`)
*   `double` (小数), `float` (浮点数, 后面加 f, 如 `1.5f`)
*   `boolean` (只有 `true`/`false`)
*   `char` (单字符, 用单引号 `'`)
*   *byte, short* (用的少)

### 2.2 引用数据类型 (Reference Types)
除了上面 8 种，**其他所有类型都是对象**（继承自 Object）。
变量存的是**内存地址的引用**（指针）。

*   `String` (字符串)
*   数组 `int[]`
*   所有自定义的类 `User`, `Order` ...

## 3. 字符串 (String) 的坑

作为前端，你习惯了 `'` 和 `"` 混用。但在 Java 里：
*   **单引号 `' '`**：只能包一个字符 (`char`)，如 `'A'`。
*   **双引号 `" "`**：包字符串 (`String`)，如 `"Hello"`。

### 字符串比较 (经典面试题)
在 JS 里，`"a" === "a"` 是 `true`。
在 Java 里，**千万别用 `==` 比较字符串内容！**

```java
String s1 = new String("hello");
String s2 = new String("hello");

// ❌ 错误写法
if (s1 == s2) { ... } 
// 结果是 false！因为 == 比较的是内存地址 (指针)，它俩是两个不同的对象。

// ✅ 正确写法
if (s1.equals(s2)) { ... } 
// 结果是 true。equals() 方法专门用来比较内容。
```

## 4. 数组 (Array)

Java 的数组是**定长**的。一旦创建，长度不能变。这和 JS 的数组（也就是 List）完全不同。

```java
// JS: const arr = [1, 2, 3];
// Java:
int[] arr = {1, 2, 3}; 

// 或者先声明长度 (默认填0)
int[] arr2 = new int[5]; // [0, 0, 0, 0, 0]
arr2[0] = 10;
```

> **前端疑问**：那我要往数组 `push` 数据怎么办？
> **回答**：用 `ArrayList`（下一节集合框架会讲），它才是你熟悉的“JS 数组”。

## 5. 流程控制

好消息：`if`, `else`, `while`, `for`, `switch` 和 JS **几乎一模一样**。

### 增强 For 循环 (ForEach)
遍历数组时，Java 有个语法糖：

```java
String[] names = {"Alice", "Bob", "Charlie"};

// JS: for (const name of names)
// Java:
for (String name : names) {
    System.out.println(name);
}
```

## 6. 类型转换

JS 会自动隐式转换 (`1 + "1" = "11"`), Java 很严格。

```java
int a = 10;
String b = "20";

// String 转 int
int c = Integer.parseInt(b); // 类似 JS 的 parseInt(b)

// int 转 String
String d = String.valueOf(a); // 类似 JS 的 String(a)
// 或者
String e = a + ""; // 这个骚操作 Java 也支持
```

## 总结

1.  **类型**：写变量前先想好类型 (`int`, `String`...)。
2.  **引号**：字符串必须用双引号 `""`。
3.  **比较**：对象（包括 String）内容比较用 `.equals()`，别用 `==`。
4.  **数组**：原生数组长度固定，想动态增删用 `ArrayList`。

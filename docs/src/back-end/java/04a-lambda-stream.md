# 04a. Lambda 与 Stream API

> 目标：学会 Lambda 表达式和 Stream 链式操作，看懂项目中 `.stream().filter().map().collect()` 这类代码。

如果你熟悉 JS 的 `Array.prototype.filter().map().reduce()`，恭喜——Java 的 Stream API 就是同一套思路，只是语法不同。

## 1. Lambda 表达式

### 1.1 是什么？

Lambda 就是**匿名函数**。Java 8 引入，让 Java 也能写函数式风格。

**JavaScript**
```javascript
const add = (a, b) => a + b;

names.forEach(name => console.log(name));
```

**Java**
```java
// 完整写法
(a, b) -> { return a + b; }

// 简写（单表达式可省略 {} 和 return）
(a, b) -> a + b

// 单参数可省略括号
name -> System.out.println(name)
```

对比一下：

| | JavaScript | Java |
| --- | --- | --- |
| 语法 | `(x) => x + 1` | `(x) -> x + 1` |
| 关键符号 | `=>` (胖箭头) | `->` (细箭头) |
| 多行 | `(x) => { ... }` | `(x) -> { ... }` |

> **区别在于**：JS 的箭头函数可以赋值给任意变量；Java 的 Lambda 必须赋值给一个**函数式接口**类型。

### 1.2 函数式接口 (Functional Interface)

Java 的 Lambda 不能独立存在，它必须匹配一个只有**一个抽象方法**的接口。

```java
// 这就是一个函数式接口——只有一个抽象方法
@FunctionalInterface
interface Converter {
    String convert(int value);
}

// 用 Lambda 实现
Converter c = (value) -> "Number: " + value;
System.out.println(c.convert(42)); // "Number: 42"
```

Java 已经内置了常用的函数式接口，不用你自己定义：

| 接口 | 方法签名 | JS 类比 | 用途 |
| --- | --- | --- | --- |
| `Predicate<T>` | `boolean test(T t)` | `(x) => boolean` | 判断/过滤 |
| `Function<T, R>` | `R apply(T t)` | `(x) => y` | 转换/映射 |
| `Consumer<T>` | `void accept(T t)` | `(x) => void` | 消费/处理 |
| `Supplier<T>` | `T get()` | `() => x` | 提供/生成 |

### 1.3 方法引用 (::)

当 Lambda 只是调用一个已有方法时，可以用 `::` 简写：

```java
// Lambda 写法
names.forEach(name -> System.out.println(name));

// 方法引用（等价）
names.forEach(System.out::println);
```

常见的方法引用：

```java
// 静态方法引用
list.stream().map(String::valueOf)       // 等于 x -> String.valueOf(x)

// 实例方法引用
list.stream().map(String::toUpperCase)   // 等于 s -> s.toUpperCase()

// 构造方法引用
list.stream().map(User::new)             // 等于 name -> new User(name)
```

## 2. Stream API

### 2.1 是什么？

Stream 是集合的"流水线加工"API。它不是数据结构，而是数据的**处理管道**。

```text
集合 (List/Set)
    ↓ .stream()
过滤 .filter(...)
    ↓
转换 .map(...)
    ↓
排序 .sorted(...)
    ↓
收集 .collect(...)
    ↓
结果 (List/Set/Map)
```

> **前端类比**：数组的链式调用 `.filter().map().sort()`，但 Java 的 Stream 是惰性求值——只有遇到终止操作（如 `collect`）才真正执行。

### 2.2 基础操作对比

假设有一组用户数据：

**JavaScript**
```javascript
const users = [
  { name: "Jack", age: 25 },
  { name: "Rose", age: 17 },
  { name: "Tom",  age: 30 },
  { name: "Lucy", age: 15 },
];

// 找出所有成年人的名字，按年龄排序
const result = users
  .filter(u => u.age >= 18)
  .sort((a, b) => a.age - b.age)
  .map(u => u.name);

console.log(result); // ["Jack", "Tom"]
```

**Java**
```java
List<User> users = List.of(
    new User("Jack", 25),
    new User("Rose", 17),
    new User("Tom",  30),
    new User("Lucy", 15)
);

// 同样的操作
List<String> result = users.stream()
    .filter(u -> u.getAge() >= 18)
    .sorted(Comparator.comparingInt(User::getAge))
    .map(User::getName)
    .collect(Collectors.toList());

System.out.println(result); // [Jack, Tom]
```

### 2.3 常用操作速查表

| JS 方法 | Java Stream | 说明 |
| --- | --- | --- |
| `.filter(fn)` | `.filter(predicate)` | 过滤 |
| `.map(fn)` | `.map(function)` | 转换元素 |
| `.flatMap(fn)` | `.flatMap(function)` | 扁平化 + 转换 |
| `.sort(fn)` | `.sorted(comparator)` | 排序 |
| `.forEach(fn)` | `.forEach(consumer)` | 遍历（终止操作） |
| `.find(fn)` | `.findFirst()` | 找第一个匹配 |
| `.some(fn)` | `.anyMatch(predicate)` | 任一匹配 |
| `.every(fn)` | `.allMatch(predicate)` | 全部匹配 |
| `.reduce(fn, init)` | `.reduce(init, fn)` | 归约 |
| — | `.collect(Collectors.toList())` | 收集为 List |
| — | `.collect(Collectors.toSet())` | 收集为 Set |
| — | `.collect(Collectors.toMap(...))` | 收集为 Map |
| `.length` after filter | `.count()` | 计数 |
| `.slice(0, n)` | `.limit(n)` | 取前 n 个 |
| `.slice(n)` | `.skip(n)` | 跳过前 n 个 |

### 2.4 实际项目中的常见写法

#### 过滤 + 转换 + 收集
```java
// 获取所有 VIP 用户的邮箱
List<String> vipEmails = users.stream()
    .filter(User::isVip)
    .map(User::getEmail)
    .collect(Collectors.toList());
```

#### 去重
```java
List<String> unique = names.stream()
    .distinct()
    .collect(Collectors.toList());
```

#### 分组（JS 没有直接对应的）
```java
// 按国家分组
Map<String, List<User>> byCountry = users.stream()
    .collect(Collectors.groupingBy(User::getCountry));

// 结果：{ "China": [user1, user2], "USA": [user3] }
```

> **前端类比**：Lodash 的 `_.groupBy(users, 'country')`。

#### 拼接字符串
```java
String names = users.stream()
    .map(User::getName)
    .collect(Collectors.joining(", "));
// "Jack, Rose, Tom"
```

#### 判断是否存在
```java
boolean hasAdmin = users.stream()
    .anyMatch(u -> "admin".equals(u.getRole()));
```

### 2.5 Optional（处理空值）

Stream 的 `findFirst()` 返回的不是直接的值，而是 `Optional<T>`——Java 处理"可能为 null"的容器。

```java
// findFirst 返回 Optional
Optional<User> user = users.stream()
    .filter(u -> u.getId().equals(targetId))
    .findFirst();

// 安全地取值
String name = user
    .map(User::getName)
    .orElse("Unknown"); // 如果不存在，用默认值
```

> **前端类比**：`user?.name ?? 'Unknown'`（可选链 + 空值合并）。

## 3. 注意事项

### Stream 只能消费一次

```java
Stream<String> stream = names.stream();
stream.forEach(System.out::println); // ✅ 第一次
stream.forEach(System.out::println); // ❌ IllegalStateException!
```

每次需要操作都要从集合重新 `.stream()`。

### 不要在 forEach 里修改集合

```java
// ❌ 危险：ConcurrentModificationException
users.stream().forEach(u -> {
    if (u.getAge() < 18) users.remove(u);
});

// ✅ 正确：用 filter + collect 生成新集合
List<User> adults = users.stream()
    .filter(u -> u.getAge() >= 18)
    .collect(Collectors.toList());
```

> JS 也有同样的问题——你不应该在 `forEach` 里修改正在遍历的数组。

## 总结

1. **Lambda** = 匿名函数。`->` 相当于 JS 的 `=>`。
2. **方法引用** `::` 是 Lambda 的简写。
3. **Stream** = 集合的链式处理管道。`.filter().map().collect()`，和 JS 的 `.filter().map()` 一个思路。
4. **终止操作**（`collect`、`forEach`、`count`）触发实际执行，中间操作是惰性的。
5. **Optional** = 安全的 null 处理，类似 `?.` 可选链。
6. 在真实项目中，Stream 比 for 循环更常用——它更简洁、更易读。

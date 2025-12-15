# 04. 集合框架 (Collections)

> 目标：学会怎么存数据。ArrayList vs Array, HashMap vs Object。

在 JS 中，一个 `[]` 既是数组又是栈又是队列，一个 `{}` 既是对象又是字典。
但在 Java 中，原生数组 `int[]` 长度不可变，太难用了。实际开发中，我们主要使用 **集合框架 (Collections Framework)**。

最常用的两个神器：`ArrayList` (动态数组) 和 `HashMap` (键值对)。

## 1. 泛型 (Generics) - 先补个课

在用集合之前，你得懂 `<>` 是什么。
Java 是强类型语言，容器里装什么，必须贴标签说明。

```java
// 这里的 <String> 就是泛型，告诉编译器：这个列表里只能放 String
List<String> list = new ArrayList<>();

list.add("Hello");
list.add(123); // ❌ 报错！编译器会拦住你
```

> **注意**：泛型只能用**引用类型**。
> *   ❌ `List<int>` (不行)
> *   ✅ `List<Integer>` (可以，Integer 是 int 的包装类)

## 2. ArrayList (动态数组)

这就是你熟悉的 JS Array。可以自动扩容。

### 对比操作

| 操作 | JavaScript Array | Java ArrayList |
| :--- | :--- | :--- |
| **创建** | `const list = [];` | `List<String> list = new ArrayList<>();` |
| **添加** | `list.push("A");` | `list.add("A");` |
| **获取** | `let x = list[0];` | `String x = list.get(0);` |
| **长度** | `list.length` | `list.size()` |
| **删除** | `list.splice(0, 1);` | `list.remove(0);` |
| **包含** | `list.includes("A");` | `list.contains("A");` |

### 常用代码示例

```java
import java.util.ArrayList;
import java.util.List; // 推荐用接口 List 接收

public class ListDemo {
    public static void main(String[] args) {
        List<String> names = new ArrayList<>();
        
        names.add("Jack");
        names.add("Rose");
        
        // 遍历 (和 JS for...of 一样)
        for (String name : names) {
            System.out.println(name);
        }
        
        // Lambda 表达式遍历 (Java 8+) -> 类似 JS 的 names.forEach
        names.forEach(name -> System.out.println(name));
    }
}
```

## 3. HashMap (键值对)

这就是你熟悉的 JS Object `{ key: value }` 或者 ES6 的 `Map`。
用于存储 Key-Value 结构。

### 对比操作

| 操作 | JavaScript Object | Java HashMap |
| :--- | :--- | :--- |
| **创建** | `const map = {};` | `Map<String, Integer> map = new HashMap<>();` |
| **设置** | `map["age"] = 18;` | `map.put("age", 18);` |
| **获取** | `let x = map["age"];` | `Integer x = map.get("age");` |
| **判断Key** | `"age" in map` | `map.containsKey("age");` |
| **删除** | `delete map["age"];` | `map.remove("age");` |
| **Keys** | `Object.keys(map)` | `map.keySet()` |

### 常用代码示例

```java
import java.util.HashMap;
import java.util.Map;

public class MapDemo {
    public static void main(String[] args) {
        // Key是String，Value是Integer
        Map<String, Integer> scores = new HashMap<>();
        
        scores.put("Math", 95);
        scores.put("English", 80);
        
        System.out.println(scores.get("Math")); // 95
        System.out.println(scores.get("PE"));   // null (不存在返回 null)
        
        // 遍历 Map
        for (String key : scores.keySet()) {
            System.out.println(key + ": " + scores.get(key));
        }
    }
}
```

## 4. 包装类 (Wrapper Classes)

前面说了，泛型 `<>` 里不能写基本类型 (`int`, `double`)。
Java 为每个基本类型都提供了一个对应的**类**。

| 基本类型 | 包装类 (对象) |
| :--- | :--- |
| `int` | `Integer` |
| `long` | `Long` |
| `double` | `Double` |
| `boolean` | `Boolean` |
| `char` | `Character` |

**自动装箱/拆箱 (Auto-boxing/Unboxing)**:
Java 会自动帮你转换，你通常感觉不到区别。

```java
// 自动装箱：int 10 -> Integer 10
Integer a = 10; 

// 自动拆箱：Integer 10 -> int 10
int b = a; 
```

## 总结

1.  **List**: 替代数组。用 `ArrayList`。
    *   `add()`, `get()`, `size()`
2.  **Map**: 替代对象/字典。用 `HashMap`。
    *   `put()`, `get()`, `containsKey()`
3.  **泛型**: 必须指定类型 `<String>`, `<Integer>`。
4.  **包装类**: 集合里存数字要用 `Integer` 而不是 `int`。


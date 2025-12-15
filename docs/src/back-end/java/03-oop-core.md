# 03. 面向对象核心 (OOP)

> 目标：理解 Class, Object, Method, Static，对比 ES6 Class。

Java 是**纯面向对象**的语言。在 JS 里你可以写一个独立的函数 `function add() {}`，但在 Java 里，**一切都必须包裹在类 (Class) 里面**。

## 1. 类 (Class) 与 对象 (Object)

这概念和 ES6 的 `class` 几乎一样。
*   **类 (Class)**: 蓝图、模板（例如：图纸上的“汽车”）。
*   **对象 (Object)**: 实例、实物（例如：你开的那辆具体的“特斯拉”）。

### 对比代码

**JavaScript (ES6)**
```javascript
class User {
  constructor(name, age) {
    this.name = name;
    this.age = age;
  }

  sayHi() {
    console.log(`Hi, I am ${this.name}`);
  }
}

const u1 = new User("Jack", 18);
u1.sayHi();
```

**Java**
```java
public class User {
    // 1. 属性 (Fields) - 必须声明类型
    String name;
    int age;

    // 2. 构造方法 (Constructor) - 名字必须和类名一样，没有返回值
    public User(String name, int age) {
        this.name = name;
        this.age = age;
    }

    // 3. 方法 (Methods)
    public void sayHi() {
        System.out.println("Hi, I am " + this.name);
    }
}

// 使用 (通常在 main 方法里)
public class Main {
    public static void main(String[] args) {
        User u1 = new User("Jack", 18);
        u1.sayHi();
    }
}
```

## 2. 访问修饰符 (Public/Private)

JS 目前也有 `#private` 字段，但 Java 的控制更严格。

*   `public`: 公开的。任何地方都能访问。
*   `private`: 私有的。只有**本类内部**能访问。
*   `protected`: 受保护的。子类可以用。
*   *(默认)*: 同一个包 (文件夹) 下能访问。

**最佳实践**：属性通常设为 `private`，然后通过 `public` 的 `get/set` 方法来操作（封装）。

```java
public class User {
    private int age; // 外部不能直接 User.age = -5

    public void setAge(int age) {
        if (age < 0) return; // 可以加逻辑判断
        this.age = age;
    }

    public int getAge() {
        return this.age;
    }
}
```

## 3. 静态 (Static) - 重点！

这是前端最容易晕的地方。
`static` 意味着**属于类，而不属于实例**。

*   **实例方法**: 必须 `new` 出来才能用。比如 `u1.sayHi()`。
*   **静态方法**: 不用 `new`，直接用 `类名.方法名` 调用。比如 `Math.max(1, 2)`。

### 为什么 main 方法是 static 的？
```java
public static void main(String[] args) { ... }
```
因为程序刚启动时，还没有任何对象被 `new` 出来。JVM 需要一个入口，不需要创建对象就能直接调用的入口，所以必须是 `static`。

### 工具类常用 Static
类似 JS 的 `Math`、`JSON` 对象。

```java
public class MathUtils {
    // 静态属性
    public static final double PI = 3.14159;

    // 静态方法
    public static int add(int a, int b) {
        return a + b;
    }
}

// 调用
System.out.println(MathUtils.PI);
System.out.println(MathUtils.add(1, 2));
```

## 4. 继承 (Inheritance)

和 ES6 一样，使用 `extends` 关键字。

```java
// 父类
public class Animal {
    public void eat() {
        System.out.println("Eating...");
    }
}

// 子类
public class Dog extends Animal {
    @Override // 注解：告诉编译器我要重写父类方法，帮我检查写没写错
    public void eat() {
        System.out.println("Dog eating bones...");
    }
}
```

## 总结

1.  **一切皆对象**：代码必须写在 `class` 里。
2.  **类型约束**：属性必须声明类型。
3.  **Static**：静态成员属于类，不需要 `new` 就能用（工具人属性）。
4.  **封装**：多用 `private` 属性 + `public` Getter/Setter。


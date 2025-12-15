# 05. 异常处理 (Exception Handling)

> 目标：理解 try-catch-finally 机制及 Checked Exception。

在 JS 中，异常处理通常是可选的。你写代码时可能很少写 `try-catch`，除非是为了捕获 `async/await` 的错误。
但在 Java 中，异常处理是**强制性**的。很多时候，你不写 `try-catch`，代码连编译都过不去。

## 1. 异常分类

Java 的异常机制比 JS 复杂，主要分为两类：

### 1.1 运行时异常 (Unchecked Exception)
类似 JS 的 Error。编译器**不强制**你处理。通常是代码逻辑错误。
*   `NullPointerException` (空指针，类似 JS 的 `Cannot read property of undefined`)
*   `ArrayIndexOutOfBoundsException` (数组越界)
*   `ArithmeticException` (除以 0)

### 1.2 检查型异常 (Checked Exception) - 重点！
这是 Java 独有的。编译器**强制**你必须处理，否则**编译报错**。
通常是外部环境问题，无法通过修改代码逻辑完全避免。
*   `IOException` (文件读写错误)
*   `SQLException` (数据库查询错误)
*   `ClassNotFoundException` (类找不到)

> **为什么强制？**
> Java 认为：文件可能不存在，网络可能断开，数据库可能挂掉。这些风险必须在代码里显式处理，不能假装没看见。

## 2. try-catch-finally

语法和 JS 基本一致，但作用更关键。

```java
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class FileDemo {
    public static void main(String[] args) {
        FileReader reader = null;
        try {
            // 1. 尝试执行可能出错的代码
            File file = new File("test.txt");
            // FileReader 的构造函数声明了 "throws FileNotFoundException"
            // 所以这里必须 try-catch，否则红线报错
            reader = new FileReader(file); 
            
            System.out.println("文件打开成功");
            
        } catch (IOException e) {
            // 2. 捕获异常并处理
            System.out.println("出错了：" + e.getMessage());
            e.printStackTrace(); // 打印完整的错误堆栈 (调试神器)
            
        } finally {
            // 3. 无论是否出错，都会执行 (通常用于释放资源)
            System.out.println("结束处理");
            
            // 关闭流 (标准写法很繁琐，下面会讲简化版)
            try {
                if (reader != null) reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
```

## 3. throws 关键字 (甩锅)

如果你不想在当前方法里处理异常，可以把异常**抛给调用者**去处理。
这叫“甩锅”。

```java
// 在方法签名上加 throws
public void readFile() throws IOException {
    File file = new File("test.txt");
    FileReader reader = new FileReader(file); // 这里不用 try-catch 了
}

// 调用者 main 方法必须处理这个锅
public static void main(String[] args) {
    try {
        readFile(); // 必须包在 try-catch 里
    } catch (IOException e) {
        // 处理
    }
}
```

## 4. Try-with-resources (Java 7+ 新特性)

上面的 `finally` 里关闭资源写得太丑了。Java 7 引入了自动关闭资源的语法。
只要实现了 `AutoCloseable` 接口的类 (比如各种 Stream, Connection)，都可以这样写：

```java
// try 后面的括号里定义资源
try (FileReader reader = new FileReader("test.txt")) {
    // 使用资源
    int data = reader.read();
} catch (IOException e) {
    // 处理异常
}
// 出了 try 的花括号，reader 会自动 close()，不需要写 finally
```

## 总结

1.  **强制处理**：遇到 `Checked Exception` (如 IO, SQL)，必须 `try-catch` 或 `throws`。
2.  **空指针**：`NullPointerException` 是 Java 程序员永远的痛，使用对象前最好判空。
3.  **资源释放**：操作文件、数据库、网络后，一定要关闭连接。推荐用 `try-with-resources`。


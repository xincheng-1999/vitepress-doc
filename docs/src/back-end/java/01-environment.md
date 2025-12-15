# 01. 环境搭建与 Hello World

> 目标：理解 Java 运行机制，安装 JDK，跑通第一个程序。

## 1. Java 运行机制：编译型 vs 解释型

作为前端，你熟悉的 **JavaScript** 是解释型语言。
- 源码 (`.js`) -> Node.js/浏览器 (边解释边运行)

而 **Java** 是编译型语言（带虚拟机）。
- 源码 (`.java`) -> **编译器 (javac)** -> 字节码 (`.class`) -> **JVM (Java 虚拟机)** -> 运行

**类比：**
- JS 像是“同声传译”，听到一句翻译一句。
- Java 像是“笔译”，先把整本书翻译完（编译成 `.class`），再拿给读者看（JVM 运行）。

## 2. 核心概念：JDK vs JRE

- **JDK (Java Development Kit)**: Java 开发工具包。包含编译器 (`javac`)、工具链和 JRE。
  - **类比**: 相当于安装了 Node.js 环境 + npm + 各种构建工具。
- **JRE (Java Runtime Environment)**: Java 运行环境。只包含 JVM 和核心类库，只能运行，不能开发。
  - **类比**: 相当于只安装了浏览器，只能跑网页，不能打包编译。

**结论**：我们要开发，必须安装 **JDK**。

## 3. 安装 JDK

推荐安装 **JDK 17** (LTS 长期支持版) 或 **JDK 8** (经典老版，很多老项目在用)。

### Windows 安装步骤
1.  下载：[Oracle JDK](https://www.oracle.com/java/technologies/downloads/) 或 [Adoptium (推荐)](https://adoptium.net/)。
2.  安装：傻瓜式下一步。
3.  **配置环境变量** (关键步骤，类似把 `node` 命令加到终端里)：
    - 新建系统变量 `JAVA_HOME` -> 值：JDK 安装路径 (例如 `C:\Program Files\Java\jdk-17`)
    - 编辑 `Path` 变量 -> 新增 `%JAVA_HOME%\bin`

### 验证安装
打开终端 (cmd/PowerShell)，输入：
```bash
java -version
javac -version
```
如果能看到版本号，说明安装成功。

## 4. Hello World

新建一个文件 `Hello.java` (文件名必须与类名一致，且首字母大写)。

```java
// Hello.java
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello, Java World!");
    }
}
```

### 代码解析 (前端视角)
1.  `public class Hello`: 定义一个类。Java 是纯面向对象的，**所有代码必须写在类里**。JS 里你可以直接写 `console.log`，但在 Java 里不行。
2.  `public static void main(String[] args)`: **程序的入口**。
    - 类似 C 语言的 main 函数。
    - 当你运行 Java 程序时，JVM 会找这个固定的方法开始执行。
3.  `System.out.println(...)`: 也就是 `console.log(...)`。

### 运行程序

在终端执行：

1.  **编译** (Compile):
    ```bash
    javac Hello.java
    ```
    *结果*：目录下会生成一个 `Hello.class` 文件 (二进制字节码)。

2.  **运行** (Run):
    ```bash
    java Hello
    ```
    *注意*：这里不需要加后缀 `.class`。
    *结果*：终端打印 `Hello, Java World!`

## 5. 总结

| 概念 | Java | Node.js / JS |
| :--- | :--- | :--- |
| 源码文件 | `.java` | `.js` |
| 中间产物 | `.class` (字节码) | 无 (V8 引擎内部处理) |
| 运行命令 | `java ClassName` | `node fileName.js` |
| 入口 | `public static void main` | 文件第一行开始执行 |

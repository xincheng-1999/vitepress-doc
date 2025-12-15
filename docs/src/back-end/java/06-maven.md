# 06. 包管理：Maven

> 目标：理解 pom.xml，依赖管理，对比 npm package.json。

做前端开发，你离不开 `npm` 和 `package.json`。
做 Java 开发，你离不开 **Maven** 和 `pom.xml`。

Maven 是 Java 世界最主流的项目管理和构建工具（虽然 Gradle 也在崛起，但 Maven 依然是老大哥）。

## 1. 核心概念对比

| 概念 | Node.js / Front-end | Java / Maven |
| :--- | :--- | :--- |
| **工具命令** | `npm` / `yarn` / `pnpm` | `mvn` |
| **配置文件** | `package.json` | `pom.xml` |
| **依赖仓库** | npm registry (npmjs.com) | Maven Central Repository |
| **依赖文件夹** | `node_modules` (在项目里) | `~/.m2/repository` (在用户目录下，全局共享) |
| **构建命令** | `npm run build` | `mvn package` |

> **最大区别**：Maven 的依赖包不是下载到项目里的 `node_modules`，而是下载到你电脑的一个**全局仓库** (`.m2` 文件夹)。这样多个项目可以用同一份 jar 包，省空间。

## 2. pom.xml 详解

`pom.xml` (Project Object Model) 就是 Java 项目的身份证。

```xml
<project>
    <!-- 1. 项目基本信息 (类似 package.json 的 name/version) -->
    <groupId>com.example</groupId>       <!-- 组织名 (通常是域名倒写) -->
    <artifactId>my-app</artifactId>      <!-- 项目名 -->
    <version>1.0.0</version>             <!-- 版本号 -->

    <!-- 2. 依赖管理 (类似 dependencies) -->
    <dependencies>
        <!-- 引入 MySQL 驱动 -->
        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
            <version>8.0.33</version>
        </dependency>
        
        <!-- 引入 Lombok (一个偷懒神器) -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.18.24</version>
            <scope>provided</scope> <!-- scope 类似 devDependencies -->
        </dependency>
    </dependencies>

    <!-- 3. 构建配置 (类似 scripts/webpack config) -->
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.8.1</version>
                <configuration>
                    <source>1.8</source>
                    <target>1.8</target>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

## 3. 常用命令

在终端里操作 Maven：

*   **`mvn clean`**: 清理。删除生成的 `target` 目录 (类似 `rm -rf dist`)。
*   **`mvn compile`**: 编译。把 `.java` 编译成 `.class`。
*   **`mvn package`**: 打包。把编译好的文件打包成 `.jar` 或 `.war` (类似 `npm run build`)。
*   **`mvn install`**: 安装。把打好的包安装到本地仓库，供其他项目使用 (类似 `npm link`)。

通常我们会组合使用：
```bash
mvn clean package
# 先清理旧文件，再重新打包
```

## 4. 怎么找依赖？

前端你会在 `npmjs.com` 搜包。
Java 你可以在 [MVN Repository](https://mvnrepository.com/) 搜包。

1.  搜索你想要的库 (比如 "mysql")。
2.  点进去选择版本。
3.  复制它提供的 `<dependency>` 代码块。
4.  粘贴到你的 `pom.xml` 里。
5.  IDE (IntelliJ IDEA / VS Code) 通常会自动检测并下载。

## 总结

恭喜你！到这里，你已经掌握了 Java 开发最核心的“生存技能”。

1.  **环境**：JDK 是基石。
2.  **语法**：强类型，万物皆对象。
3.  **集合**：`List` 和 `Map` 走天下。
4.  **异常**：`try-catch` 必须写。
5.  **Maven**：`pom.xml` 管理依赖。

现在，你可以自信地回到 **[Java 连接 MySQL (JDBC)](../database/mysql/java-connection.md)** 那一节，去理解那些曾经看不懂的代码了！


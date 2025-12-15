# Java 连接 MySQL (JDBC)

作为前端，你习惯用 `axios` 或 `fetch` 请求后端 API 拿数据。
而在 Java 后端中，我们需要用 **JDBC (Java Database Connectivity)** 来“请求”数据库拿数据。

JDBC 就是 Java 操作数据库的一套标准 API（类似前端的 DOM API），不同的数据库厂商（MySQL, Oracle）提供不同的**驱动 (Driver)** 来实现这套接口。

## 1. 准备工作

你需要引入 MySQL 的驱动包（Connector/J）。
如果你是用 Maven 管理项目（类似 npm），在 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>8.0.33</version>
</dependency>
```

## 2. 核心代码步骤

连接数据库通常分 5 步：
1.  **加载驱动** (Load Driver)
2.  **建立连接** (Create Connection) -> 类似 TCP 握手
3.  **创建语句** (Create Statement) -> 准备发送 SQL
4.  **执行语句** (Execute Query) -> 发送请求
5.  **处理结果** (Process ResultSet) -> 拿到 Response
6.  **关闭连接** (Close) -> 释放资源

## 3. 代码示例

```java
import java.sql.*;

public class JDBCDemo {
    public static void main(String[] args) {
        // 数据库配置
        String url = "jdbc:mysql://localhost:3306/my_learning_db?useSSL=false&serverTimezone=UTC";
        String user = "root";
        String password = "your_password"; // 替换你的密码

        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;

        try {
            // 1. 建立连接
            System.out.println("Connecting to database...");
            conn = DriverManager.getConnection(url, user, password);

            // 2. 创建 Statement 对象
            stmt = conn.createStatement();

            // 3. 执行 SQL 查询
            String sql = "SELECT id, username, age FROM users";
            rs = stmt.executeQuery(sql);

            // 4. 处理结果集 (类似遍历数组)
            while (rs.next()) {
                // 通过列名取值
                int id = rs.getInt("id");
                String name = rs.getString("username");
                int age = rs.getInt("age");

                // 打印数据
                System.out.println("ID: " + id + ", Name: " + name + ", Age: " + age);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            // 5. 关闭资源 (必须关闭，否则会占用连接池)
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (SQLException se) {
                se.printStackTrace();
            }
        }
    }
}
```

## 4. 防止 SQL 注入 (PreparedStatement)

上面的 `Statement` 拼接字符串很危险，容易被攻击（SQL 注入）。
推荐使用 `PreparedStatement`，它支持占位符 `?`。

```java
String sql = "SELECT * FROM users WHERE username = ? AND age > ?";
PreparedStatement pstmt = conn.prepareStatement(sql);

// 填坑 (设置参数)
pstmt.setString(1, "Jack"); // 第一个 ? 填 "Jack"
pstmt.setInt(2, 18);        // 第二个 ? 填 18

ResultSet rs = pstmt.executeQuery();
```

这就像前端发请求时，参数放在 `body` 里，而不是拼接到 URL 上，更安全。

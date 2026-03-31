# 07b. Spring Boot 配置详解

> 目标：理解 `application.yml` 配置文件的结构，掌握 `@Value`、Profile 多环境配置。

在前端项目中，你一定用过 `.env`、`.env.development`、`.env.production`。Spring Boot 的配置文件是 `application.yml`（或 `application.properties`），作用完全一样——把可变参数从代码中提取出来。

## 1. 配置文件格式

Spring Boot 支持两种格式：

### 1.1 properties 格式（key=value）

```properties
# application.properties
server.port=8080
spring.application.name=demo
spring.data.mongodb.uri=mongodb://localhost:27017/mydb
```

### 1.2 YAML 格式（推荐 ✅）

```yaml
# application.yml
server:
  port: 8080

spring:
  application:
    name: demo
  data:
    mongodb:
      uri: mongodb://localhost:27017/mydb
```

YAML 格式更直观、有层级，真实项目中基本都用 `.yml`。

> **前端类比**：
> ```env
> # .env
> VITE_API_URL=http://localhost:3000
> VITE_APP_TITLE=My App
> ```
> Spring 的 yml 就是更结构化的 `.env`。

## 2. 常见配置项

### 2.1 服务器配置

```yaml
server:
  port: 8080           # 服务端口（默认 8080）
  servlet:
    context-path: /api  # 统一路径前缀，所有接口变成 /api/xxx
```

### 2.2 数据库配置

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/mydb

  datasource:           # MySQL
    url: jdbc:mysql://localhost:3306/mydb
    username: root
    password: 123456
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### 2.3 Redis 配置

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: ""
```

### 2.4 自定义配置

```yaml
# 你可以自定义任何 key
app:
  upload-dir: /data/uploads
  max-file-size: 10MB
  jwt:
    secret: my-secret-key
    expiration: 86400
```

## 3. 在代码中读取配置

### 3.1 @Value（读取单个值）

```java
@Service
public class FileService {

    @Value("${app.upload-dir}")
    private String uploadDir;

    @Value("${app.max-file-size}")
    private String maxFileSize;

    @Value("${server.port}")
    private int serverPort;

    public void info() {
        System.out.println("上传目录: " + uploadDir);
        System.out.println("端口: " + serverPort);
    }
}
```

> **前端类比**：`import.meta.env.VITE_API_URL` 或 `process.env.API_URL`。  
> `@Value("${...}")` = Java 版的环境变量读取。

### 3.2 @ConfigurationProperties（推荐，映射整块配置）

当配置项比较多时，一个个 `@Value` 太啰嗦。`@ConfigurationProperties` 可以把一整块配置映射到一个对象：

```java
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
    private String secret;
    private long expiration;
}
```

对应的配置：
```yaml
app:
  jwt:
    secret: my-secret-key
    expiration: 86400
```

使用时直接注入：
```java
@Service
public class AuthService {

    private final JwtProperties jwtProps;

    public AuthService(JwtProperties jwtProps) {
        this.jwtProps = jwtProps;
    }

    public void showConfig() {
        System.out.println(jwtProps.getSecret());     // my-secret-key
        System.out.println(jwtProps.getExpiration());  // 86400
    }
}
```

> **前端类比**：像是把 `.env` 里的一组变量解构到一个配置对象里——`const jwt = { secret: env.JWT_SECRET, exp: env.JWT_EXP }`。

## 4. Profile —— 多环境配置

### 4.1 为什么需要？

开发时连本地数据库，上线连生产数据库。你不能每次部署都手动改配置文件。

> **前端类比**：`.env.development` + `.env.production`，Vite 会根据 `--mode` 自动选择。

### 4.2 文件命名约定

```text
src/main/resources/
├── application.yml            ← 公共配置（所有环境共用）
├── application-dev.yml        ← 开发环境配置
├── application-prod.yml       ← 生产环境配置
└── application-test.yml       ← 测试环境配置
```

### 4.3 示例

**application.yml**（公共）
```yaml
spring:
  application:
    name: demo
  profiles:
    active: dev  # ← 默认激活 dev 环境
```

**application-dev.yml**（开发环境）
```yaml
server:
  port: 8080

spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/demo_dev

app:
  debug: true
```

**application-prod.yml**（生产环境）
```yaml
server:
  port: 80

spring:
  data:
    mongodb:
      uri: mongodb://db-server:27017/demo_prod

app:
  debug: false
```

### 4.4 切换环境

```bash
# 方式一：命令行参数
java -jar demo.jar --spring.profiles.active=prod

# 方式二：环境变量
export SPRING_PROFILES_ACTIVE=prod
java -jar demo.jar

# 方式三：在 application.yml 中写死（开发时常用）
spring:
  profiles:
    active: dev
```

> **前端对比**：
> | 前端 | Spring Boot |
> | --- | --- |
> | `vite --mode development` | `--spring.profiles.active=dev` |
> | `.env.development` | `application-dev.yml` |
> | `.env.production` | `application-prod.yml` |
> | `import.meta.env.VITE_XXX` | `@Value("${xxx}")` |

## 5. 配置优先级

Spring Boot 的配置可以来自很多地方，优先级从高到低：

```text
1. 命令行参数        --server.port=9090
2. 环境变量          SERVER_PORT=9090
3. application-{profile}.yml
4. application.yml
5. 代码中的默认值     @Value("${xxx:默认值}")
```

高优先级会覆盖低优先级。

`@Value` 可以设默认值：

```java
@Value("${app.debug:false}")  // 如果配置里没有 app.debug，就用 false
private boolean debug;
```

## 6. 敏感配置处理

**不要把密码、密钥等敏感信息直接写在 yml 里提交到 Git。**

常见做法：

```yaml
spring:
  data:
    mongodb:
      uri: ${MONGO_URI}  # ← 从环境变量读取
```

部署时通过环境变量注入：
```bash
export MONGO_URI=mongodb://user:pass@prod-server:27017/mydb
java -jar demo.jar
```

> **前端类比**：你不会把 API Key 写在代码里 push 到 GitHub，而是放在 `.env.local`（被 `.gitignore` 忽略）。Spring 项目也是一样的思路。

## 总结

1. **application.yml** 是 Spring Boot 的核心配置文件，相当于前端的 `.env`。
2. **@Value** 读取单个配置值，**@ConfigurationProperties** 映射整块配置到对象。
3. **Profile** 实现多环境切换：`application-dev.yml` / `application-prod.yml`，通过 `--spring.profiles.active` 切换。
4. **优先级**：命令行 > 环境变量 > profile 配置 > 默认配置。
5. 敏感信息用环境变量注入，不要硬编码在配置文件里。

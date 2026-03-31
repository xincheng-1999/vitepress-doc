# 03b. 枚举 (Enum)

> 目标：理解枚举是什么、为什么要用、在 Spring 项目中哪些地方会遇到。

如果你去看真实的 Java 后端项目，会发现大量的 `enum`——错误码、用户状态、订单类型、权限角色……几乎所有"固定选项"都用枚举表示。
JS 没有内置的枚举（TS 有），但你一定写过类似的东西。

## 1. 枚举是什么？

枚举就是一组**命名的常量集合**——值是固定的、有限的、不会变的。

> **前端类比**：  
> - TS 的 `enum`  
> - `const STATUS = { ACTIVE: 'active', INACTIVE: 'inactive' } as const`  
> - 或者更粗暴的 `type Status = 'active' | 'inactive'`

### JS/TS 写法

```typescript
// TypeScript 枚举
enum OrderStatus {
  PENDING = "PENDING",
  PAID = "PAID",
  SHIPPED = "SHIPPED",
  CANCELLED = "CANCELLED",
}

// 或者常量对象
const ORDER_STATUS = {
  PENDING: "PENDING",
  PAID: "PAID",
} as const;
```

### Java 写法

```java
public enum OrderStatus {
    PENDING,
    PAID,
    SHIPPED,
    CANCELLED
}
```

就这么简单。每个值就是枚举类的一个实例。

## 2. 基本用法

### 2.1 声明与使用

```java
public enum Color {
    RED, GREEN, BLUE
}

// 使用
Color c = Color.RED;
System.out.println(c);        // "RED"
System.out.println(c.name()); // "RED"（返回字符串）
```

### 2.2 switch 配合枚举

这是最常见的用法：

```java
public void handleOrder(OrderStatus status) {
    switch (status) {
        case PENDING:
            System.out.println("等待支付");
            break;
        case PAID:
            System.out.println("已支付，准备发货");
            break;
        case SHIPPED:
            System.out.println("已发货");
            break;
        case CANCELLED:
            System.out.println("已取消");
            break;
    }
}
```

> **为什么比字符串好？** 如果你写 `"PIAD"`（拼错了），编译器不会报错；但枚举 `OrderStatus.PIAD` 会直接编译报错。

### 2.3 遍历所有枚举值

```java
for (OrderStatus status : OrderStatus.values()) {
    System.out.println(status);
}
// PENDING
// PAID
// SHIPPED
// CANCELLED
```

### 2.4 字符串 → 枚举

```java
// 前端传 "PAID"，后端转枚举
OrderStatus status = OrderStatus.valueOf("PAID");
```

> 如果传了不存在的值（如 `"UNKNOWN"`），会抛 `IllegalArgumentException`。

## 3. 带属性的枚举

Java 枚举的强大之处在于——每个枚举值可以携带数据。

### 3.1 错误码（最常见的场景）

```java
public enum ErrorCode {
    SUCCESS(200, "成功"),
    NOT_FOUND(404, "资源不存在"),
    FORBIDDEN(403, "没有权限"),
    SERVER_ERROR(500, "服务器内部错误");

    private final int code;
    private final String message;

    // 构造方法（自动私有，不能在外部 new）
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}
```

使用：
```java
ErrorCode err = ErrorCode.NOT_FOUND;
System.out.println(err.getCode());    // 404
System.out.println(err.getMessage()); // "资源不存在"
```

> **前端类比**：
> ```typescript
> const ERROR_CODE = {
>   SUCCESS:      { code: 200, message: "成功" },
>   NOT_FOUND:    { code: 404, message: "资源不存在" },
> } as const;
> ```
> Java 的枚举就是把这种模式内建到语言里了。

### 3.2 用户角色

```java
public enum UserRole {
    ADMIN("管理员", 0),
    VIP("VIP用户", 1),
    NORMAL("普通用户", 2);

    private final String label;
    private final int level;

    UserRole(String label, int level) {
        this.label = label;
        this.level = level;
    }

    public String getLabel() { return label; }
    public int getLevel() { return level; }
}
```

## 4. 枚举实现接口

枚举也是类，可以实现接口——这在策略模式中非常常见：

```java
public interface PriceStrategy {
    double calculate(double price);
}

public enum MemberType implements PriceStrategy {
    NORMAL {
        @Override
        public double calculate(double price) {
            return price;  // 不打折
        }
    },
    VIP {
        @Override
        public double calculate(double price) {
            return price * 0.8;  // 8 折
        }
    },
    SVIP {
        @Override
        public double calculate(double price) {
            return price * 0.6;  // 6 折
        }
    };
}

// 使用
double finalPrice = MemberType.VIP.calculate(100.0); // 80.0
```

> **前端类比**：策略对象模式。
> ```javascript
> const strategy = {
>   NORMAL: (price) => price,
>   VIP:    (price) => price * 0.8,
> };
> strategy['VIP'](100); // 80
> ```

## 5. 在 Spring 项目中的典型用法

### 5.1 DTO 中作为字段类型

```java
public class OrderDTO {
    private Long id;
    private OrderStatus status;  // ← 枚举类型，不是 String
    // getter/setter...
}
```

前端传 JSON `{ "id": 1, "status": "PAID" }`，Spring 会自动把 `"PAID"` 转成 `OrderStatus.PAID`。

### 5.2 数据库映射

MongoDB 中通常存为字符串，MySQL 中可以存为字符串或整数。ORM 框架会自动转换。

### 5.3 统一响应中的错误码

```java
@GetMapping("/{id}")
public Result<User> getUser(@PathVariable Long id) {
    User user = userService.findById(id);
    if (user == null) {
        return Result.fail(ErrorCode.NOT_FOUND);
    }
    return Result.ok(user);
}
```

## 总结

1. **枚举 = 一组命名的常量。** 比字符串安全（编译期检查）、比数字可读。
2. **带属性的枚举**可以携带 code、message 等额外信息——非常适合做错误码、状态码。
3. **枚举可以实现接口**——用来做策略模式，替代一堆 if-else。
4. 在真实项目中，状态码、错误码、角色类型、业务类型基本都是枚举。
5. Spring 会自动把前端传来的字符串转成枚举值。

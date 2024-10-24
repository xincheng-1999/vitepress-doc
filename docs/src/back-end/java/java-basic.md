# java 基础

## Java 相关网站

[Java 官网](https://www.oracle.com/java/)
[Java 教程](https://www.runoob.com/java/java-tutorial.html)
[Java17 文档](https://docs.oracle.com/en/java/javase/17/)

## Java 注意事项

1. Java 默认是严格区分大小写的，比如 `String` 和 `string` 是不同的。
2. Java 默认是 UTF-8 编码，所以中文默认是乱码，需要设置编码格式。
3. Java 一个文件只能定义一个类，一个文件可以有多个接口，但是只能有一个主类。
4. Java 默认是静态的，所以静态方法可以直接调用，不需要创建对象。
5. Java 默认是线程安全的，所以不需要加锁。
6. Java 默认是强类型的，所以类型转换需要显示转换。
7. Java 主类需要定义 main 方法，并且参数为 String[]， main 方法是程序的入口。

## Java 数据类型

1. 基本类型：boolean、byte、char、short、int、long、float、double
2. 引用类型：String、Array、Object、Interface、Enum、Annotation、Class、Method、Constructor、Field、Variable

每个类型的基本使用：

```java
public class Main {
    public static void main(String[] args) {
        // 基本类型
        boolean b = true;
        byte b1 = 1;
        char c = 'a';
        short s = 1;
        int i = 1;
        long l = 1;
        float f = 1.0f;
        double d = 1.0;
        System.out.println(b);
        System.out.println(b1);
        System.out.println(c);
        System.out.println(s);
        System.out.println(i);
        System.out.println(l);
        System.out.println(f);
        System.out.println(d);
        // 引用类型
        String str = "hello";
        System.out.println(str);
        String str1 = new String("hello");
        System.out.println(str1);
        System.out.println(str == str1);
        System.out.println(str.equals(str1));
        System.out.println(str.length());
        System.out.println(str.charAt(0));
        System.out.println(str.substring(1));
        System.out.println(str.toUpperCase());
        System.out.println(str.toLowerCase());
        System.out.println(str.trim());
        System.out.println(str.replace('h', 'H'));
        System.out.println(str.indexOf('e'));
        System.out.println(str.lastIndexOf('e'));
        System.out.println(str.contains("ell"));
        System.out.println(str.startsWith("hel"));
        System.out.println(str.endsWith("lo"));
        System.out.println(str.concat(" world"));
        System.out.println(str.split(" "));
        System.out.println(str.join(" ", "hello", "world"));
        System.out.println(str.format("hello %s", "world"));
        System.out.println(str.toCharArray());
        System.out.println(str.toByteArray());
        System.out.println(str.toLowerCase());
        System.out.println(str.toUpperCase());
        System.out.println(str.toTitleCase());
        System.out.println(Integer.parseInt("1"));
        System.out.println(Integer.parseInt("1.0"));
        System.out.println(Integer.parseInt("1a"));
    }
}
```

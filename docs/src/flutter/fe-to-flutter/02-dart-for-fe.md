---
title: 第二章 Dart 基础（面向前端工程师）
---

# 第二章：Dart 基础（面向前端工程师）

## 2.1 本章目标（验收标准）
完成后你需要能：
- 写出常见 Dart 语法（类型、集合、函数、类、扩展、泛型）
- 正确认识 Null Safety（可空/不可空）
- 写出可控的异步代码（Future / async-await / Stream 基础）
- 能把“前端常见写法”迁移到 Dart 的正确姿势

---

## 2.2 核心概念：Dart 是“强类型 + Null Safety”的语言
**你最需要立刻建立的三件事：**
1) 类型不是摆设：很多错误会在编译期被挡住
2) Null Safety 是硬规则：`String` 与 `String?` 是两种类型
3) 异步不是随便 `then`：优先 `async/await`，学会错误传播

---

## 2.3 Web 思维对比：TS vs Dart（高频差异）

#### 2.3.1 类型系统
- TypeScript：结构类型（structural typing），很多类型在运行时被擦除
- Dart：更偏名义类型（nominal typing）与运行时检查能力更强（尤其泛型场景仍有价值）

#### 2.3.2 `null` 的位置
- TS：`strictNullChecks` 开了才“像 Dart”
- Dart：默认强制 Null Safety
  - `String name = 'a';` 不允许赋 `null`
  - `String? name;` 才允许是 `null`

#### 2.3.3 对象与集合
- JS：对象字面量到处飞
- Dart：你会更常用“模型类 + 构造器 + fromJson/toJson”

---

## 2.4 语法必会：变量、类型、可空

#### 2.4.1 `final` / `const` / `var`
- `var`：局部类型推断（不是动态类型）
- `final`：运行时常量（变量引用不可变）
- `const`：编译期常量（值在编译期确定）

示例：
```dart
final now = DateTime.now();
const pi = 3.1415926;
var count = 1; // 推断为 int
```

#### 2.4.2 可空与非空
```dart
String a = 'hi';
String? b; // b 可以是 null

// b.length  // 编译错误：b 可能为 null
final len = b?.length; // 安全调用：如果 b 为 null，len 为 null
final len2 = b?.length ?? 0; // 提供默认值
```

**常见坑：滥用 `!`（非空断言）**
```dart
String? b;
// b!  // 运行时可能直接崩
```
尽量用 `??`、提前返回、或把可空数据在边界处处理掉。

---

## 2.5 集合：List / Set / Map（你会天天用）

#### 2.5.1 List
```dart
final list = <int>[1, 2, 3];
list.add(4);
final doubled = list.map((e) => e * 2).toList();
```

#### 2.5.2 Map
```dart
final map = <String, dynamic>{'id': '1', 'name': 'A'};
final name = map['name'] as String;
```

**Web 思维对比：**
- JS 里 `map['name']` 不存在时返回 `undefined`
- Dart 里可能是 `null`，并且你要处理类型断言

---

## 2.6 函数：一等公民 + 命名参数（非常关键）

#### 2.6.1 命名参数（Flutter API 90% 都这样）
```dart
void log({required String message, int level = 1}) {
  print('[$level] $message');
}

log(message: 'hello');
log(message: 'warn', level: 2);
```

#### 2.6.2 可选位置参数
```dart
String join(String a, [String? b]) => b == null ? a : '$a-$b';
```

---

## 2.7 类、构造器、不可变（写业务模型的标准姿势）

#### 2.7.1 写一个 Note 模型（完整可用）
```dart
class Note {
  final String id;
  final String content;
  final DateTime createdAt;
  final String? imagePath;

  const Note({
    required this.id,
    required this.content,
    required this.createdAt,
    this.imagePath,
  });

  Note copyWith({
    String? id,
    String? content,
    DateTime? createdAt,
    String? imagePath,
  }) {
    return Note(
      id: id ?? this.id,
      content: content ?? this.content,
      createdAt: createdAt ?? this.createdAt,
      imagePath: imagePath ?? this.imagePath,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'content': content,
      'createdAt': createdAt.toIso8601String(),
      'imagePath': imagePath,
    };
  }

  static Note fromJson(Map<String, dynamic> json) {
    return Note(
      id: json['id'] as String,
      content: json['content'] as String,
      createdAt: DateTime.parse(json['createdAt'] as String),
      imagePath: json['imagePath'] as String?,
    );
  }
}
```

**Web 对比：**
- 你在 TS 里可能只写 interface + plain object
- 在 Flutter 工程里，“模型类 + copyWith + (from/to)Json”是最稳定的写法

---

## 2.8 扩展（Extension）：给现有类型加方法
```dart
extension DateTimeFormat on DateTime {
  String toYmdHm() {
    String two(int n) => n.toString().padLeft(2, '0');
    return '${year}-${two(month)}-${two(day)} ${two(hour)}:${two(minute)}';
  }
}
```

---

## 2.9 异步：Future / async-await（移动端必修）

#### 2.9.1 Future 基础
```dart
Future<String> fetchName() async {
  await Future.delayed(const Duration(milliseconds: 200));
  return 'Alice';
}
```

#### 2.9.2 错误处理（不要只写 try-catch 就完事）
```dart
Future<void> run() async {
  try {
    final name = await fetchName();
    print(name);
  } catch (e, st) {
    // 生产项目里你会把 e/st 交给日志系统
    print('error: $e');
    print(st);
  }
}
```

**Web 对比：**
- 类似 `async function` + `try/catch`
- Dart 里你要更注意“错误边界”放在哪里（UI 层？repository 层？）

#### 2.9.3 Stream（先会用，不必硬背）
在 Flutter 中很多事件源是 Stream：
- 文本输入监听
- 数据库变更
- WebSocket

---

## 2.10 实战：把第 1 章的时间格式提取为 Extension
目标：减少 UI 代码里的工具函数，让代码更像“工程”而不是“脚本”。

步骤：
1) 新建 `lib/utils/datetime_format.dart`
2) 写 `DateTimeFormat` extension
3) 在 `main.dart` 引用并替换 `_formatTime`

示例（完整文件）：
```dart
// lib/utils/datetime_format.dart

extension DateTimeFormat on DateTime {
  String toYmdHm() {
    String two(int n) => n.toString().padLeft(2, '0');
    return '${year}-${two(month)}-${two(day)} ${two(hour)}:${two(minute)}';
  }
}
```

然后在 UI 中：
```dart
import 'utils/datetime_format.dart';

// ...
subtitle: Text(note.createdAt.toYmdHm()),
```

---

## 2.11 实战小练习（必须做）

#### 练习 A：给 Note 加上 `toString()` 方便调试
要求：覆写 `toString()`，打印出 id/content/createdAt。

#### 练习 B：实现一个“过滤器函数”
输入：`List<Note>` 与关键字 `keyword`
输出：content 包含关键字的列表（忽略大小写）

提示：
```dart
final result = notes.where((n) => n.content.toLowerCase().contains(keyword.toLowerCase())).toList();
```

---

## 2.12 常见坑（前端转 Dart 高发）
- 把 `var` 当成 JS 的“动态类型”：Dart 的 `var` 是类型推断
- 滥用 `dynamic`：会让错误从编译期滑到运行期
- 可空类型乱 `!`：能不用 `!` 就不用
- 命名参数忘了写：Flutter API 大量用命名参数，调用时别偷懒

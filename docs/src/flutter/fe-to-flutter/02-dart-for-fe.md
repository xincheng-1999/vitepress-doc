---
title: 第二章 Dart：只补 JS 没有的认知
---

# 第二章：Dart——只补 JS 没有的认知

你已经会变量、循环、函数、集合操作和 `async/await`，本章不重复教这些。目标是读懂 Flutter 代码，识别 AI 从 JS/TS 套过来的错误写法。示例使用 Dart 3，彼此独立。

## 2.1 类型是运行规则的一部分

Dart 有 sound type system 与 null safety；类型并非像 TS 的多数标注那样编译后擦除。普通类以声明关系建立子类型，不能拿一个“字段长得一样的 Map”冒充模型实例。Records 则是结构类型，见后文。

```dart
var count = 1;                 // 推断成 int；之后不能赋字符串
Object value = 'hello';        // 可接收不同非空对象，使用前做类型判断
Object? maybe = null;          // 也允许 null
dynamic unchecked = 'hello'; // 放弃多数静态检查，不代表不会运行时报错
if (value is String) {
  print(value.length);        // 类型提升后可访问 String API
}
```

没有 JS 的 truthy/falsy：`if (items)`、`if (count)` 不成立，条件必须是 bool。没有 `undefined`；Map 查询缺失键通常得到 `null`。`as` 是运行时类型检查，不是把数据转换成该类型。

JSON 边界优先校验结构，而不是一路 `dynamic` 加 `!`：

```dart
String readContent(Object? json) {
  if (json case {'content': String content}) {
    return content;
  }
  throw const FormatException('content 必须是字符串');
}
```

这个 Map pattern 允许额外字段。外部输入的时间、长度和业务规则仍需另外校验。

## 2.2 Null safety：`?`、`!`、`late` 各自承诺什么

| 写法 | 意义 | 常见误用 |
| --- | --- | --- |
| `String?` | 值可能为空 | UI 到处强制解包 |
| `value?.length ?? 0` | 空值时返回替代值 | 与 JS 很接近，不再展开 |
| `value!` | 断言此刻不为空，错了运行时失败 | 为消除编译错误随手添加 |
| `late final x` | 延后初始化且只能赋值一次 | 初始化前读取产生运行时错误 |

先把可空值放到局部变量再分支处理，类型提升通常更明确。`late` 适合生命周期中确定会初始化的 controller，不适合掩盖“数据可能没加载”的事实。后者应建模成 loading / data / error。

## 2.3 `final` 与 `const`：别翻译成同一个“常量”

```dart
final tags = <String>['flutter'];
tags.add('dart');             // final 只阻止变量重新绑定
const fixedTags = ['flutter']; // 编译期常量集合，不能修改
final now = DateTime.now();   // 运行时决定，不能写 const
```

`final` 接近 JS 的 `const` 绑定语义；Dart 的 `const` 要求编译期可确定。`const Note(...)` 还要求构造器支持常量构造且参数均满足常量条件。

Flutter 里 `const Text('标题')` 能减少部分重复对象创建和更新工作，但它不是“不再 rebuild”的魔法开关，也不是给所有 Widget 强行加 `const` 的理由。

## 2.4 命名参数不是 JS 对象解构

```dart
String preview(String content, {int limit = 20, required bool ellipsis}) {
  if (content.length <= limit) return content;
  return '${content.substring(0, limit)}${ellipsis ? '…' : ''}';
}

void main() {
  print(preview('hello', ellipsis: true));
}
```

`{}` 在函数声明中定义命名参数；调用时是 `ellipsis: true`，不是传入 `{ellipsis: true}`。`required String? id` 表示参数必须传，但值可以为 null；“必传”和“非空”是两个约束。

Flutter 常见的 `const NoteTile({super.key, required this.note});` 同时用了命名参数、转发父类参数、初始化字段的简写。

## 2.5 构造器、factory 与类修饰符

```dart
class Note {
  final String id;
  final String content;

  const Note({required this.id, required this.content});

  factory Note.fromJson(Map<String, Object?> json) {
    if (json case {'id': String id, 'content': String content}) {
      return Note(id: id, content: content);
    }
    throw const FormatException('无效笔记');
  }
}
```

`Note.fromJson` 是命名 factory 构造器，不是 JS 的静态方法约定；factory 可以返回缓存对象或子类型实例，不能用 `this` 访问一个尚未创建的当前实例。普通命名构造器不一定是 factory。

- `extends`：继承实现，单继承。
- `implements`：实现接口契约，不继承实现；普通类也会隐式定义接口。
- `with`：应用 mixin，常见于动画 ticker 能力。
- `abstract interface class`：适合暴露 repository 契约。
- `sealed class`：直接子类型受 library 边界约束，适合有限状态与穷尽匹配。

业务模型优先不可变。集合本身也要控制可变性；只把字段标成 final 还不够。

## 2.6 模块化：最需要纠正 JS 直觉的地方

每个 Dart 文件及其 `part` 默认构成一个 library。普通声明默认公开，**下划线表示 library 私有，不是 class 私有**。两个独立文件即使同目录，也不能访问彼此的 `_private`。

```dart
// lib/features/notes/note.dart
class Note {
  const Note(this.content);
  final String content;
}

class _Decoder {} // 仅该 library 内可见
```

```dart
// 以下是不同导入方式的示意，按需要选用
import 'dart:async';                                    // SDK library
import 'package:flutter/material.dart';                 // 依赖包
import 'package:flutter_notes/features/notes/note.dart'; // 自己的 lib/，由包名定位
import '../data/notes_repository.dart';                  // 相对当前文件
import 'package:some_package/api.dart' as api;           // 名称前缀
import 'models.dart' show Note, Folder;                  // 只引入这些名称
import 'legacy.dart' hide Note;                         // 排除重名声明
export 'src/note.dart' show Note;                        // 对使用者再导出
```

最后四行中的文件或包是语法示意，不是本教程要安装的依赖。Dart 没有 ES module 的 default export / 命名 export 写法；不能写 `import { Note } from ...`。

`package:flutter_notes/` 对应 `pubspec.yaml` 的 `name: flutter_notes`，然后映射到 `lib/`，不是随便写的路径别名。不要从别的包导入 `lib/src` 的实现细节。

`part 'note.g.dart';` / `part of ...;` 把文件组成同一个 library，常用于生成代码。它不是拆文件时替代 import 的通用方案。模型生成器还涉及 `build_runner` 和产物约定，项目未启用时不要让 AI 凭空写出缺失的 `.g.dart`。

## 2.7 Flutter 高频特殊语法

```dart
final enabled = true;
final values = <int>[
  1,
  if (enabled) 2,           // collection if
  for (final n in [3, 4]) n * 2, // collection for
  ...[9, 10],              // spread
];
final buffer = StringBuffer()
  ..write('Flutter')       // cascade：调用后仍围绕原对象操作
  ..write(' + Dart');
```

`..` 不是可选链，也不要求 `write` 返回 this。`...?optionalList` 是可空集合展开。`map`、`where` 常返回惰性 Iterable，需要列表时调用 `.toList()`；不是所有集合操作都像 JS 数组方法那样立刻计算并返回数组。

Records 与 patterns 用于表达小型组合与解构：

```dart
({int total, String label}) summarize(List<Note> notes) =>
    (total: notes.length, label: '笔记');

void showSummary(List<Note> notes) {
  final (:total, :label) = summarize(notes);
  print('$label: $total');
}
```

Record 不是 Map，也不是可变 JS object；字段形状决定类型并有值相等语义。普通模型类默认不自动获得“字段一样就相等”的行为，需要自己定义或使用合适的生成工具。

`switch` 可以是表达式，也支持 patterns。结合 sealed 类型，漏掉一种状态可由分析器发现；第 6 章会把这种思维用于 UI 状态。

## 2.8 `copyWith` 的 null 陷阱

```dart
// 局部反例：调用 copyWith(imageName: null) 无法清空旧图片
String? nextImage(String? imageName, String? oldImage) =>
    imageName ?? oldImage;
```

“没传参数”和“明确清空”都变成 null。你可以采用显式 `removeImage()`、额外 `clearImage` 参数、sentinel，或成熟的生成方案。本教程主线直接构造新 Note；图片扩展时必须单独设计清空语义。

## 2.9 异步：只记住与 JS 的边界差异

`Future<T>` 与 Promise 的用途相近，`async/await` 不再赘述。真正需要注意：

- `Future` 没有统一的取消操作。HTTP 取消用 Dio 的 CancelToken 等具体机制；请求结束后仍要判断结果是否过期。
- `Stream<T>` 是多个异步事件；`listen` 返回订阅，需要在所有者销毁时取消。`async*` / `yield` 可产生 Stream。
- `await` 不会把 CPU 密集工作移到后台。原生平台可用 `Isolate.run` 处理合适的大计算，isolate 不共享普通可变对象，不能在里面操作 UI。Web 的能力与限制需要另查。
- 未等待的 Future 仍可能出错；`unawaited` 表达有意不等待，不会替你处理异常。
- `catch (error, stackTrace)` 能同时拿到错误与堆栈；重新抛出原异常优先 `rethrow`，保留定位上下文。

## 2.10 本章验收

让 AI 解释一段真实 Widget 文件，并指出 import 的来源、每个命名参数、`super.key`、可空字段与 controller 的初始化时机。然后给它三项修改：把 `dynamic` JSON 边界变成校验、实现可清空图片字段、拆两个独立 library。

你要能预测哪些修改会编译失败、哪些会运行时失败，以及为什么。无需重新练习九九乘法表。继续阅读 [Widget 与布局](./03-widget-layout-core.md)。

参考：[libraries](https://dart.dev/language/libraries)、[constructors](https://dart.dev/language/constructors)、[patterns](https://dart.dev/language/patterns)、[records](https://dart.dev/language/records)。

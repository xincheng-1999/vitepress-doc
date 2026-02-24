---
title: 第十二章 工程化与架构（分层、错误处理、可测试）
---

# 第十二章：工程化与架构（让你的 App “能迭代、能维护、能测试”）

## 12.1 本章目标（验收标准）
完成后你需要能：
- 用“分层”把 UI/业务/数据访问解耦
- 定义稳定的错误模型（AppError/Failure），UI 只展示友好文案
- 为 Riverpod notifier 写单元测试（不跑真 UI 也能测）
- 为关键页面写一个最小 widget test

---

## 12.2 核心概念：移动端项目不是写页面，是写系统
当你的功能变多（网络/权限/相机/本地库），最容易崩的是：
- UI 直接操作 dio/sqflite
- 错误处理散落各处
- 无法写测试，回归靠手点

本章给你一个“够用且不重”的架构模板：
- `features/`：按业务域拆
- `domain`：纯业务模型/规则（尽量无依赖）
- `data`：repository/数据源（db/http）
- `ui`：页面与组件

---

## 12.3 推荐目录（落地可维护）
示例（你可以逐步迁移，不需要一次到位）：
```
lib/
  core/
    errors/
      app_error.dart
    network/
      app_dio.dart
  features/
    notes/
      domain/
        note.dart
      data/
        notes_repository.dart
      ui/
        notes_home_page.dart
        note_edit_page.dart
      notes_provider.dart
  main.dart
```

**Web 对比：**
- 类似 React 项目的 feature-based structure（按功能模块，而不是按文件类型）

---

## 12.4 错误处理：统一错误模型 + UI 显示友好提示
你第 7 章已经有 `AppError`，本章建议把它放到 `core/errors`，并坚持一个原则：
- repository 只抛 `AppError`（或你的 Failure 类型）
- UI 不直接展示 raw exception（避免用户看到一堆英文栈）

示例：
```dart
class AppError implements Exception {
  final String message;
  final Object? cause;
  const AppError(this.message, {this.cause});
}
```

在 UI：
- 展示 `message`
- `cause` 用于日志

---

## 12.5 Riverpod 可测试的关键：依赖注入（Provider 覆盖）

#### 12.5.1 为什么要“可替换的 repository”
真实 repository 会访问 SQLite/网络。
测试时你希望：
- 用内存假数据（Fake）替代真数据库
- 测 notifier 的业务逻辑

#### 12.5.2 示例：写一个 Fake Repository
创建 `test/fakes/fake_notes_repository.dart`：
```dart
// 注意：把下面的包名 `fe_to_flutter_notes` 替换成你 pubspec.yaml 里的 name。
import 'package:fe_to_flutter_notes/features/notes/domain/note.dart';

class FakeNotesRepository {
  final List<Note> _data = [];

  Future<List<Note>> list() async => List.unmodifiable(_data);

  Future<void> upsert(Note note) async {
    _data.removeWhere((n) => n.id == note.id);
    _data.add(note);
    _data.sort((a, b) => b.createdAt.compareTo(a.createdAt));
  }

  Future<void> deleteById(String id) async {
    _data.removeWhere((n) => n.id == id);
  }
}
```

> 注意：你的实际工程 import 路径会不同；这里演示思路。

---

## 12.6 单元测试：测试 notifier 的增删改

#### 12.6.1 关键工具：`ProviderContainer`
在 Riverpod 里测试通常用：
- `ProviderContainer(overrides: [...])`
- 直接读 notifier 并调用方法

#### 12.6.2 示例测试（最小可跑）
创建 `test/notes_notifier_test.dart`：
```dart
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

void main() {
  test('sample: ProviderContainer works', () {
    final container = ProviderContainer();
    addTearDown(container.dispose);

    // 这里先演示 container 可用。
    // 你需要在自己的工程里把 notesProvider / repositoryProvider 覆盖成 fake。
    expect(container, isNotNull);
  });
}
```

把它升级成真实测试的步骤（不省略）：
1) 把你的 `notesRepositoryProvider` 设计成可覆盖（Provider）
2) 测试里写一个 `FakeNotesRepository`
3) `ProviderContainer(overrides: [notesRepositoryProvider.overrideWithValue(fakeRepo)])`
4) 调用 `await container.read(notesProvider.notifier).add(...)`
5) 断言 `container.read(notesProvider).value` 的内容

---

## 12.7 Widget Test：确保页面能渲染与响应
最小 widget test 目标：
- 首页能渲染“空状态”
- 点 `+` 能打开编辑页（或弹窗）

你需要：
- 把路由与 provider 注入到测试环境
- 用 fake repository 提供稳定数据

如果你不想处理 package 导入，也可以在示例阶段把 fake 类写进测试文件里（等架构稳定后再拆文件）。

---

## 12.8 实战小练习（必须做）

#### 练习 A：为 NotesAsyncNotifier 写一个真实的 add/delete 测试
验收：
- add 后列表长度 +1
- delete 后列表长度 -1

#### 练习 B：加一个“全局错误提示组件”
- 写一个函数 `showAppErrorSnackBar(context, message)`
- 网络页/存储页出错时统一用它提示

---

## 12.9 常见坑
- provider 里直接 new 具体实现且不可覆盖 → 测试很难写
- UI 里做 I/O（db/http） → 无法复用、无法测试
- 错误直接 `print` → 线上无法定位；至少保证 message + cause 有记录位置

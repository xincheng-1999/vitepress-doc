---
title: 第一章 环境与第一个开发闭环
---

# 第一章：环境与第一个开发闭环

本章只做一件事：让你能在设备上运行代码、观察变化、读到错误。业务功能从后续章节开始，完整工程基线见 [第 17 章](./17-integrated-notes-app.md)。

## 1.1 先选要交付的平台

Flutter 用 Dart 描述 UI，框架完成布局和绘制；多数常规 Flutter UI 不靠浏览器 DOM，也不是把每个 Widget 映射成一个原生控件。相机、文件、定位等能力通常由插件连接平台实现。因此，“Dart 编译通过”和“手机上的系统能力可用”是两层验证。

| 开发电脑 | Android | iOS |
| --- | --- | --- |
| Windows / Linux | 可本地开发、调试和构建 | 需要额外的 macOS 构建环境 |
| macOS | 可本地开发、调试和构建 | 安装 Xcode 后可开发，真机还需签名配置 |

先选 Android 或 iOS 跑通，不要把所有目标平台的警告都当作阻塞。如果你的目标是手机 App，在 Chrome 跑通只能验证部分 UI，不能替代移动端插件测试。

## 1.2 安装与自检

按 [官方安装入口](https://docs.flutter.dev/install) 选择宿主系统，下载 stable SDK 并把 SDK 的 `bin` 加入 PATH。Flutter 已附带匹配的 Dart SDK，不要另装一个不同版本的 Dart 来分析同一项目。

Android 安装 Android Studio，在 SDK Manager 安装 SDK Platform、Build-Tools、Command-line Tools 和 Emulator；在 Device Manager 创建并启动模拟器。真机开启开发者选项和 USB 调试，并在手机上确认授权。

iOS 安装 Xcode，打开一次完成组件安装，选好 Command Line Tools。插件的原生依赖方式随 Flutter 与插件版本变化，按 `flutter doctor` 和工程实际生成的配置处理 CocoaPods / Swift Package Manager。

```sh
flutter --version
flutter doctor -v
flutter doctor --android-licenses
flutter devices
```

最后两条中，Android licenses 仅用于 Android 工具链。`flutter devices` 至少应出现一个准备开发的目标设备。遇到错误先区分：SDK 不存在、许可证没接受、设备没授权、网络下载失败。这些问题不需要改业务代码。

## 1.3 创建项目

```sh
flutter create --org com.example flutter_notes
cd flutter_notes
flutter run -d <设备ID>
```

尖括号内容需替换为 `flutter devices` 的结果。`com.example` 只用于练习，正式发布前确定自己的 application ID / bundle ID。

| 路径 | 负责什么 | 什么时候需要动 |
| --- | --- | --- |
| `lib/main.dart` | Dart 入口与应用根节点 | 开始写 UI |
| `pubspec.yaml` | SDK、依赖、资源、版本 | 加插件、资源、发版 |
| `pubspec.lock` | 应用实际解析的依赖版本 | 依赖有意更新时 |
| `android/`、`ios/` | 原生宿主、权限、签名 | 调平台能力或发布 |
| `test/` | Dart / Widget 测试 | 实现业务行为时 |
| `.dart_tool/`、`build/` | 工具生成的产物 | 不手工修改 |

## 1.4 最小 UI：读懂入口即可

下面是完整 `lib/main.dart`，只依赖 Flutter SDK。替换模板后运行。`runApp` 挂载根 Widget；`MaterialApp` 提供主题、导航等应用环境；`Scaffold` 提供页面骨架。

```dart
import 'package:flutter/material.dart';

void main() => runApp(const NotesApp());

class NotesApp extends StatelessWidget {
  const NotesApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '随手记',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.teal),
      ),
      home: Scaffold(
        appBar: AppBar(title: const Text('随手记')),
        body: const SafeArea(
          child: Center(child: Text('第一条笔记，从这里开始')),
        ),
      ),
    );
  }
}
```

不要在这一刻要求自己背下这些类。需要解释的是：Widget 是声明，`build` 返回当前状态下的 UI 描述；`build` 不是“只执行一次的页面初始化”。

## 1.5 三种重启解决不同问题

| 操作 | 会发生什么 | 适用情况 |
| --- | --- | --- |
| Hot reload | 更新代码并重建，通常保留已有 State | 改文案、样式、普通 UI 逻辑 |
| Hot restart | 重启 Dart 应用，内存状态重置 | 改初始化逻辑、静态数据 |
| 停止后重新运行 | 重新启动并按需重建原生宿主 | 加插件、改权限或原生配置 |

热重载不会重新执行已有 State 的 `initState`。如果改了初始值却没变化，先判断是否需要 hot restart，不要让 AI 为了“生效”把初始化移进 `build`。

## 1.6 建立最短验证循环

```sh
dart format lib test
flutter analyze
flutter test
```

模板的 `test/widget_test.dart` 验证的是默认计数器；替换主页面后，需要把测试更新成你的页面行为。不要把模板断言失败误认为 Flutter 环境坏了，也不要以删掉全部测试作为解决方案。第 12、17 章会给出真实测试。

每次把最上面的第一条有效错误交给 AI，并附带文件路径、相关代码、Flutter 版本、执行命令。一次修一个原因；一条编译错误可能产生几十条连带错误。

## 1.7 本章交付与 AI 任务

把目标设备上的首页跑起来，修改标题并热重载；再重启一次，确认自己能区分两种操作。记录 Flutter 版本与设备系统版本，这就是后续复现环境。

给 AI 的任务可以是：“根据这份 doctor 输出，只解释目标 Android 设备无法启动的原因，先给诊断命令和预期结果。”验收时你要能回答：故障在 Dart、Flutter 工具链、原生构建还是设备连接？


下一篇先看 [项目导读：运行、入口与模块](./01b-project-map.md)。它会带你从实际启动配置追到首页与一次保存，不必等学完所有专题才知道怎样阅读工程。

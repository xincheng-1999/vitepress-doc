---
title: 第十三章 构建、签名与环境配置
---

# 第十三章：构建、签名与环境配置

debug 能运行不等于能交付。本章先生成一个可安装的正式模式产物，并记录它来自哪份源码与配置；商店流程放在第 16 章。

## 13.1 三种模式各测什么

| 模式 | 用途 | 不应拿它证明什么 |
| --- | --- | --- |
| debug | 热重载、断言、开发调试 | 最终性能与发布可用性 |
| profile | 真机性能分析 | 商店签名、审核与分发 |
| release | 用户实际运行形态 | 所有功能自动正确 |

模拟器适合开发，性能结论优先来自真实设备的 profile。release 可能遇到 debug 没有的网络声明、原生优化、签名和环境地址问题。

## 13.2 版本、身份与环境

`pubspec.yaml` 的 `version: 1.0.0+1` 包含展示版本与构建号。Android 通常映射为 versionName/versionCode，iOS 为短版本与 build number。每次提交新构建满足目标商店的递增要求。

applicationId / bundle identifier 是应用身份，应在正式签名、推送和商店注册前确定。改显示名称不等于改身份；换身份安装出来的是另一个应用，不能拿来验证原应用的数据升级。

使用编译环境配置的局部示例：

```dart
const apiBaseUrl = String.fromEnvironment('API_BASE_URL');

void validateConfig() {
  final uri = Uri.tryParse(apiBaseUrl);
  if (uri == null || uri.scheme != 'https' || uri.host.isEmpty) {
    throw StateError('生产 API_BASE_URL 必须是有效 HTTPS 地址');
  }
}
```

主线离线 App 不需要该参数；加入网络后，在启动时校验并展示合适的配置错误。`--dart-define` 是配置注入，**不是保密渠道**；客户端里的共享密钥可能被提取，敏感服务凭证应保留在服务端。

```sh
flutter build appbundle --release --dart-define=API_BASE_URL=https://api.example.com
```

这里的 example 域名是占位地址，必须替换。需要 dev/prod 并存且包名、图标、签名不同，再引入 flavors/schemes；只有一个 API 地址不同不必立刻配置复杂变体。

## 13.3 Android 签名：先理解，再配置

本地练习可先运行：

```sh
flutter build apk --release
flutter build appbundle --release
```

APK 可直接安装；AAB 是发布格式，不是可以直接 `adb install` 的文件。产物通常位于 `build/app/outputs/flutter-apk/` 与 `build/app/outputs/bundle/release/`。构建成功仍要检查实际使用的签名，不能默认 release 自动采用你的正式密钥。

发布准备按 [官方 Android 发布指南](https://docs.flutter.dev/deployment/android) 完成：

1. 生成并备份上传密钥，妥善保存口令。
2. 通过本机未跟踪的属性文件或 CI secret 注入 keystore 路径和口令。
3. 在 app 模块的 signingConfigs 建立 release 配置，并让 release buildType 引用它。
4. 对照工程实际文件选择 Kotlin DSL（`.gradle.kts`）或 Groovy（`.gradle`），不要混贴语法。
5. 用签名报告/产物证书工具核对证书，再安装测试。

启用 Play App Signing 时，上传密钥与最终应用签名密钥职责不同。第三方服务需要哪个证书指纹，必须按实际分发路径确认。把密钥和口令提交到 Git 或交给模型粘贴进源码都不是正确配置方式。

## 13.4 iOS 构建

在 macOS 配置 Xcode Team、Bundle ID 与签名后：

```sh
flutter build ipa --release
```

产物在 `build/ios/` 的 archive/ipa 相关目录中，具体导出取决于签名和 export 配置。模拟器构建不能替代设备 archive；`--no-codesign` 适合某些构建检查，不生成可直接分发给用户的已签名包。

## 13.5 交付证据

记录源码 revision、Flutter 版本、lockfile、构建命令、非敏感配置、构建号、产物位置与测试设备。拿生成的产物实际安装，关闭调试器后启动并跑主流程。

尤其做一次覆盖升级：先装旧版本并写入笔记，再装同身份且兼容签名的新版本，确认迁移。卸载重装只测了新安装，漏掉了最容易造成数据损失的流程。

AI 任务：“检查当前工程签名引用和环境配置，只报告缺失项；根据实际 Gradle DSL 给出最小修改。”不要让模型生成一个与现有工程无关的完整 build.gradle。

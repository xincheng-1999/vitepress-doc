---
title: 第十五章 iOS 开发必备 + 调试（Xcode/签名/真机/模拟器）
---

# 第十五章：iOS 开发必备 + 调试（Xcode/签名/真机/模拟器）

## 15.1 本章目标（验收标准）
完成后你需要能：
- 清楚 iOS 开发与发布需要哪些硬性条件（Windows 的边界）
- 在模拟器与真机上运行 Flutter App，并能查看日志
- 理解 iOS 签名基本概念：Team、Bundle ID、Provisioning Profile
- 能把常见 iOS 专属问题定位到“配置/权限/ATS/签名”哪一类

---

## 15.2 iOS 开发需要具备什么（硬门槛）

### 15.2.1 本机开发（必需）
- macOS
- Xcode（与 macOS 版本匹配）
- Flutter SDK
- CocoaPods（很多插件依赖）

### 15.2.2 真机调试与发布（必需）
- Apple ID
- Apple Developer Program（付费开发者账号，发布/真机签名/推送等基本都需要）

**Windows 边界（必须明确）：**
- Windows 上可以开发 Flutter 代码与 Android
- iOS 构建/打包/上架必须在 macOS + Xcode 环境完成（或用 CI 的 macOS runner）

**Web 对比：**
- Web 没有“签名”概念
- iOS 的签名与审核链路是移动端最主要的学习成本之一

---

## 15.3 iOS 项目里你最常碰的配置点
- `ios/Runner/Info.plist`：权限用途说明、ATS 等
- `ios/Runner.xcworkspace`：Xcode 工作区（CocoaPods 生成）
- `ios/Podfile`：iOS 依赖与编译设置

---

## 15.4 运行与调试（模拟器 / 真机）

### 15.4.1 列出设备
```bash
flutter devices
```

### 15.4.2 运行到 iOS 模拟器
```bash
flutter run
```

### 15.4.3 运行到 iPhone 真机
前置：
- Xcode → Settings → Accounts 登录你的 Apple ID
- Xcode 打开 `ios/Runner.xcworkspace`
- Runner Target → Signing & Capabilities
  - 勾选 Automatically manage signing
  - 选择 Team

然后：
```bash
flutter run
```

---

## 15.5 iOS 日志与崩溃定位

### 15.5.1 flutter run 日志
大多数 Flutter 层异常会在终端输出。

### 15.5.2 Xcode Console
当你遇到：
- 原生崩溃
- 权限/ATS 拒绝
- Pod 编译错误
优先在 Xcode 里看 Console 与 Build Logs。

---

## 15.6 签名最小知识（够你把 App 跑到真机 + 上 TestFlight）

### 15.6.1 你要认识的三个词
- **Bundle ID**：你的 App 唯一标识（类似 Android 的 applicationId）
- **Team**：开发者团队/账号
- **Provisioning Profile**：把“设备 + 证书 + Bundle ID”绑在一起的授权文件

### 15.6.2 Flutter 工程中 Bundle ID 在哪里改
Xcode → Runner Target → General → Bundle Identifier

**常见坑：**
- Bundle ID 改完，某些能力（比如相册/相机/推送）可能需要重新配置签名能力

---

## 15.7 网络差异：ATS（App Transport Security）
iOS 默认对不安全请求更严格。
- 生产建议只用 https
- 如果你必须请求 http，需要在 Info.plist 配 ATS 例外（不推荐长期使用）

（示例思路）
- 允许某域名 http（具体键值请以 Apple 文档为准，别全局放开）

**Web 对比：**
- Web 也有 mixed content，但 iOS 原生更“硬”。

---

## 15.8 实战：做一个 iOS 专用的权限用途说明检查
目标：防止“iOS 上一打开相机/相册就崩”的问题。

步骤：
1) 打开 `ios/Runner/Info.plist`
2) 确认至少存在：
- `NSCameraUsageDescription`
- `NSPhotoLibraryUsageDescription`
3) 文案必须是“用户看得懂的用途说明”，不要写技术词

验收：
- 调用相机/相册时能弹出授权框，不会直接崩溃

---

## 15.9 实战小练习（必须做）

### 练习 A：在 iOS 真机上跑通“相机 + 相册选图”
验收：
- 第一次进入功能时弹授权
- 允许后可拍照/选图
- 拒绝后 UI 能给出“去设置开启”的引导（参考第 10 章）

### 练习 B：做一个“网络诊断页”
- 显示当前 baseUrl（来自 `--dart-define`）
- 做一个 GET 请求按钮，显示成功/失败
- iOS 上如果失败，学会去检查 ATS/证书/代理

---

## 15.10 常见坑
- CocoaPods 未安装/版本问题：`pod install` 失败导致 iOS 编译不过
- Signing 配错 Team：真机无法安装
- Info.plist 缺用途说明：相机/相册直接崩
- ATS：http 或弱证书环境导致请求失败（debug 可能没暴露，release 更明显）

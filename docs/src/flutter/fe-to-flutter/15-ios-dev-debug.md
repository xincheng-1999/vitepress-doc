---
title: 第十五章 iOS 真机与故障定位
---

# 第十五章：iOS 真机与故障定位

iOS 最大的新知识是宿主工程、签名与系统能力限制。Windows/Linux 可以阅读本章和写 Dart，但本机 iOS 构建、模拟器与签名需要 macOS/Xcode。

## 15.1 准备与启动

安装 Xcode 并完成初次启动，检查 Command Line Tools 选择；按 Flutter 当前官方 iOS 设置说明安装需要的模拟器 runtime 和原生依赖工具。

```sh
flutter doctor -v
flutter devices
flutter run -d <设备ID>
```

真机需信任电脑，按系统要求启用 Developer Mode，并配置可用的开发签名。免费 Personal Team 能做受限的个人设备开发，正式分发与 App Store 通常需要相应 Apple Developer Program 资格；不要把两者混为同一门槛。

## 15.2 找对工程入口

使用 CocoaPods 的项目通过 `ios/Runner.xcworkspace` 打开，避免只开 xcodeproj 丢失 Pods 依赖。Flutter 与插件对 Swift Package Manager 的支持在演进，按当前生成工程和插件文档选择，不盲目把两种集成方式叠加。

| 配置 | 负责什么 |
| --- | --- |
| Runner target → Signing & Capabilities | Team、Bundle ID、签名、能力 |
| `Info.plist` | 用途说明、应用配置等 |
| entitlements | 推送、关联域名等需授权的能力 |
| Deployment Target | 最低系统版本，与依赖要求一致 |
| scheme/configuration | 开发、生产等构建配置 |

改 Bundle ID 后也要检查第三方 SDK、推送、关联域名和商店记录。它不仅是页面显示名称。

## 15.3 签名需要理解的三个对象

- 证书：签名身份及对应私钥。
- App ID / Bundle ID：标识应用及其能力。
- Provisioning profile：关联允许的应用身份、证书、能力与适用设备/分发方式。

自动签名可以管理很多细节，但不能替你获得团队权限，也不能修复服务端注册错误。出现签名失败时，把 Xcode 的具体失败和目标配置交给 AI，别贴私钥、证书密码或完整凭证。

模拟器能运行不代表真机签名成功。连接设备后使用对应 target/scheme 构建，确认真正安装到了 iPhone。

## 15.4 原生崩溃与网络问题

Dart 异常看 Flutter 日志，原生崩溃看 Xcode Console、Devices and Simulators 中的设备日志，分发版本还要保留对应 dSYM 以便符号化。仅有“闪退截图”通常无法定位。

iOS ATS 对网络安全有要求；优先 HTTPS 与正确证书链。确需本地开发例外时限制范围，发布前核对，不把全局 `NSAllowsArbitraryLoads` 当标准配置。局域网访问还可能涉及系统本地网络隐私授权，根据实际功能判断。

缺少必要的 camera/photos 用途说明可能导致原生失败；这类问题改 Dart try/catch 不一定能救。用途文案必须描述当前用户操作，不用“需要权限以提升体验”这类空话。

## 15.5 模拟器替代不了的测试

相机硬件、部分照片格式/云照片、内存压力、真实键盘与输入法、系统分享、推送和后台行为都需要适当真机验证。模拟器中没有真实相机时，应用应显示可理解的失败或提供相册入口。

照片只授权部分访问时功能仍应成立；用户取消 picker 不显示系统故障；从设置返回后重新读取必要状态。应用进后台不保证获得无限后台时间，不把尚未提交的业务数据留到 terminate 回调才保存。

## 15.6 本章交付

在真机跑通新增、编辑、重启保留、返回拦截、选图/拍照（若已扩展），再用 release/分发构建检查一次。让 AI 提供逐项核对的原生配置 diff，由实际 Xcode 构建与设备结果决定是否正确。

参考 [Flutter iOS 设置](https://docs.flutter.dev/platform-integration/ios/setup) 与 [iOS 发布](https://docs.flutter.dev/deployment/ios)。

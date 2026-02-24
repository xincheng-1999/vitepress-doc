---
title: 第十三章 打包发布与平台差异清单（Android / iOS）
---

# 第十三章：打包发布与平台差异清单（从“能跑”到“能上线”）

## 13.1 本章目标（验收标准）
完成后你需要能：
- 产出 Android `APK`（本地安装包）与 `AAB`（Google Play 上架包）
- 理解签名、版本号、构建模式（debug/profile/release）
- 知道 iOS 打包发布需要什么（Windows 的限制）
- 掌握 Android/iOS 差异排查清单（权限、网络、证书、文件路径）

---

## 13.2 核心概念：Release 与 Debug 不是一个世界
- Debug：带调试符号、热重载、日志多、性能与权限行为可能不同
- Release：AOT 编译、性能更好，但也更严格；很多“只在 release 崩”的问题都来自：
  - 混淆/裁剪（R8/Proguard）
  - 网络证书/域名
  - 权限声明遗漏
  - 资源路径大小写

**Web 对比：**
- 类似 dev build vs production build

---

## 13.3 Android：版本号与包名（必须先设置对）

#### 13.3.1 版本号
文件：`pubspec.yaml`
- `version: 1.0.0+1`
  - `1.0.0`：展示给用户的版本
  - `+1`：构建号（Android 的 versionCode）

每次上架必须递增构建号。

#### 13.3.2 applicationId（包名）
文件：`android/app/build.gradle`
- `applicationId "com.example.xxx"`

上架后不建议随意改包名（相当于换了一个 App）。

---

## 13.4 Android：生成 Release APK（本地安装包）
```powershell
flutter build apk --release
```
输出位置一般是：
- `build/app/outputs/flutter-apk/app-release.apk`

安装到真机（需要 adb）：
```powershell
adb install -r build\app\outputs\flutter-apk\app-release.apk
```

---

## 13.5 Android：生成 AAB（Google Play 上架推荐）
```powershell
flutter build appbundle --release
```
输出位置一般是：
- `build/app/outputs/bundle/release/app-release.aab`

---

## 13.6 Android：配置签名（正式发布必做）

#### 13.6.1 生成 keystore
在工程根目录（或你自己的安全目录）执行：
```powershell
keytool -genkey -v -keystore upload-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

你会被提示输入：
- keystore 密码
- alias 密码
- 组织信息

> 请把 keystore 备份到安全位置。丢了就很难对已上架 App 更新。

#### 13.6.2 创建 `android/key.properties`
在 `android/` 下创建 `key.properties`：
```properties
storePassword=你的store密码
keyPassword=你的key密码
keyAlias=upload
storeFile=../upload-keystore.jks
```

#### 13.6.3 配置 `android/app/build.gradle`
在 `android/app/build.gradle` 中（通常模板已有注释区域），按 Flutter 官方模板接入 `key.properties`。

**重要：不要把 keystore 与 key.properties 提交到公开仓库。**
- 建议把 keystore 放到仓库外
- 或至少在 `.gitignore` 忽略

---

## 13.7 iOS：打包发布（Windows 限制说明）
- iOS 打包必须在 macOS 上用 Xcode（Windows 本机无法生成可上架的 iOS 包）
- 你可以：
  1) 在 macOS 机器上构建
  2) 或使用 CI（GitHub Actions + macOS runner）构建

常用命令（macOS）：
```bash
flutter build ios --release
```

上架一般通过 Xcode：
- Archive → Distribute App → App Store Connect

---

## 13.8 Android / iOS 差异排查清单（非常实用）

#### 13.8.1 权限
- Android：Manifest 声明 + 运行时请求
- iOS：Info.plist 用途说明（缺了可能直接崩）

#### 13.8.2 文件路径
- 不要写死路径；使用 `path_provider`
- Android 与 iOS 的沙盒路径完全不同

#### 13.8.3 网络
- iOS 对非 https 更严格（ATS）
- 证书/代理环境不同，release 更容易暴露问题

#### 13.8.4 资源大小写
- Windows 不敏感，但 Android/Linux/macOS 可能敏感
- assets 路径大小写必须一致

#### 13.8.5 Release-only 问题
- 打 release 包后一定真机回归
- 关注：启动白屏、某页面崩溃、网络全失败、图片不显示

---

## 13.9 实战小练习（必须做）

#### 练习 A：为你的 App 生成一个 Release APK 并安装到真机
验收：
- 能启动
- 笔记可读写（SQLite 正常）
- 相机/相册功能可用（权限正常）

#### 练习 B：写一份你的“发布前检查清单”
至少包含：
- 版本号、构建号
- 权限声明
- 网络域名与证书
- 数据库迁移
- Crash 关键路径测试

---

## 13.10 常见坑
- 忘记递增 build number（上架被拒）
- keystore 丢失（无法更新已上架应用）
- 只在 debug 测试（release 上线崩）
- iOS 用途说明文案不清晰（审核风险）

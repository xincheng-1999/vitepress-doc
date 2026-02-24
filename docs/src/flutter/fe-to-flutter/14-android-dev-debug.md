---
title: 第十四章 Android 开发必备 + 调试（真机/模拟器/日志/构建变体）
---

# 第十四章：Android 开发必备 + 调试（真机/模拟器/日志/构建变体）

## 14.1 本章目标（验收标准）
完成后你需要能：
- 清楚 Android 开发需要哪些工具与账号（本机开发与上架分别需要什么）
- 用真机/模拟器稳定调试：安装、卸载、查看日志、定位崩溃
- 能区分 debug/profile/release，并知道各自用途
- 能打出可安装的 release APK，并知道常见失败原因

---

## 14.2 Android 开发需要具备什么（最小集合）

### 14.2.1 本机开发（必需）
- Flutter SDK（stable）
- Android Studio（或至少 Android SDK + commandline-tools）
- JDK（一般 Android Studio 自带/或 Gradle 自动处理，尽量不要装多套导致冲突）
- 设备：
  - Android Emulator（模拟器）或
  - Android 真机（推荐，能更真实测试相机/权限/性能）

### 14.2.2 上架发布（额外必需）
- Google Play Developer 账号（上架用）
- Android 签名 keystore（更新 App 必需，同一个 App 必须用同一套签名/上传密钥规则）

**Web 对比：**
- Web 发布只要构建产物 + CDN/服务器
- Android 上架需要：签名、包名、版本号、AAB、控制台审核

---

## 14.3 真机调试（最稳的开发方式）

### 14.3.1 开启开发者选项与 USB 调试
- 设置 → 关于手机 → 连点“版本号”开启开发者选项
- 开发者选项 → 开启 USB 调试

### 14.3.2 连接与确认设备
```powershell
flutter devices
```
你应能看到设备列表。

如果 `flutter devices` 看不到：
- 检查数据线是否为“可传输数据”
- 手机弹窗是否点了“允许 USB 调试”
- 电脑是否装了对应厂商驱动（部分机型需要）

---

## 14.4 Android 日志：从“黑屏/闪退”到“定位代码行”

### 14.4.1 最常用：flutter run 自带日志
```powershell
flutter run
```
大多数 Flutter 层异常会在这里直接打印。

### 14.4.2 更底层：adb logcat（看原生层/权限/崩溃）
```powershell
adb devices
adb logcat
```
如果日志太多：
```powershell
adb logcat | findstr flutter
```

**常见你会在 logcat 里看到的关键字：**
- `FATAL EXCEPTION`：Java/Kotlin 崩溃
- `MissingPluginException`：插件未正确注册（热重启/版本不兼容/平台未配置）
- `SecurityException`：权限问题

---

## 14.5 构建模式：debug / profile / release

### 14.5.1 三种模式的用途
- debug：日常开发（可热重载）
- profile：性能分析（更接近 release，但仍可观察性能）
- release：正式发布（AOT，性能/行为最接近上架版本）

### 14.5.2 最小命令集
```powershell
flutter run --debug
flutter run --profile
flutter run --release
```

**常见坑：**
- debug 正常，release 出问题：
  - 权限/资源路径/网络证书/混淆裁剪

---

## 14.6 实战：为 Android 准备一个“开发/生产”环境开关（最小可用）
目标：像前端的 `.env.development/.env.production` 一样，至少有一个可切换的 baseUrl。

我们先不引入复杂库，直接用 `--dart-define`。

### 14.6.1 写一个环境读取类（可运行）
新建 `lib/core/env/app_env.dart`：
```dart
class AppEnv {
  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'https://example.com',
  );

  static const String flavor = String.fromEnvironment(
    'FLAVOR',
    defaultValue: 'dev',
  );
}
```

### 14.6.2 在代码里使用
例如在你的 dio 初始化里：
```dart
// dio.options.baseUrl = AppEnv.apiBaseUrl;
```

### 14.6.3 运行时传参
```powershell
flutter run --dart-define=FLAVOR=dev --dart-define=API_BASE_URL=https://jsonplaceholder.typicode.com
```

**Web 对比：**
- 类似 Vite 的 `import.meta.env` / `process.env`
- Flutter 里编译期注入更常用（release 更可靠）

---

## 14.7 打包：本地生成可安装的 APK（release）
```powershell
flutter build apk --release
```
输出一般在：
- `build/app/outputs/flutter-apk/app-release.apk`

安装到真机：
```powershell
adb install -r build\app\outputs\flutter-apk\app-release.apk
```

---

## 14.8 实战小练习（必须做）

### 练习 A：做一个“诊断页”
新建页面：
- 显示 `FLAVOR`、`API_BASE_URL`
- 显示设备信息（至少：平台 Android、系统版本）

提示：设备信息可先用 `Theme.of(context).platform` + `Platform.operatingSystemVersion`（需要 `dart:io`）。

### 练习 B：模拟一个 release-only 问题并学会排查
做法：
- 在 assets 路径里故意写错大小写（Windows 上可能不报错，但在某些环境会出问题）
- 用 `flutter build apk --release` 安装到真机
- 观察是否出现图片不显示，学会通过日志定位原因

---

## 14.9 常见坑
- `adb` 找不到：Android SDK Platform-tools 未装或 PATH 未配置（Android Studio 安装后通常自带）
- `MissingPluginException`：热重启后插件状态异常，尝试完全重启应用或 `flutter clean` 后重跑
- 多套 JDK/Gradle 冲突：尽量跟随 Android Studio 自带 JDK，避免环境变量指向混乱
- 真机权限行为与模拟器不同：相机/相册/通知等一定用真机回归

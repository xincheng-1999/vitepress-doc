---
title: 第十四章 Android 真机与故障定位
---

# 第十四章：Android 真机与故障定位

不需要先学完 Android 原生开发，但要能沿「设备 → 宿主构建 → Flutter → 插件」定位问题。不要把每个错误都交给 AI 改 Dart。

## 14.1 设备连接

```sh
adb devices
flutter devices
flutter run -d <设备ID>
```

出现 unauthorized 时在手机确认 USB 调试授权；没有设备时检查数据线、USB 模式和宿主驱动。多个设备时总是指定 id，防止在错误设备上验证。

模拟器通过 Android Studio 的 Device Manager 创建。真机需要开发者选项与 USB 调试；不同厂商还可能限制 USB 安装或后台运行，依据设备实际提示处理。

## 14.2 常用取证命令

```sh
flutter logs -d <设备ID>
adb -s <设备ID> shell pidof com.example.flutter_notes
adb -s <设备ID> logcat --pid=<上一步PID>
adb -s <设备ID> install -r build/app/outputs/flutter-apk/app-release.apk
```

以上尖括号全部替换成实际值。按 PID 过滤适合观察存活进程；启动就崩溃时可能拿不到 PID，改看 logcat 的 crash buffer 或 Android Studio Logcat。分享日志前清理 token、账号与正文。

安装失败若是签名不一致，不要立即卸载再宣布修好了。先确认旧包来源、applicationId 与签名；卸载会删除本地数据，也绕过了原本要验证的升级路径。

## 14.3 文件分别负责什么

| 文件/目录 | 主要职责 |
| --- | --- |
| `android/app/src/main/AndroidManifest.xml` | 主应用权限、组件声明、入口 |
| `android/app/src/debug/` | 仅 debug 的配置，不能代表 release |
| `android/app/build.gradle.kts` 或 `.gradle` | SDK、签名、buildTypes/flavors |
| `android/settings.gradle*`、Gradle wrapper | 构建插件与 Gradle 版本协作 |
| `MainActivity` | Flutter 原生宿主入口，通常无需改 |

加入网络能力时检查主 Manifest 的 INTERNET 声明，不仅看 debug 配置。Android Internet 是普通权限，不弹运行时请求框。发布支持的 target/min SDK 与插件要求应对齐当前工具链及商店要求，不照抄过时固定数字。

## 14.4 故障定位表

| 现象 | 首先查什么 | 避免的“万能修复” |
| --- | --- | --- |
| Gradle 构建失败 | 第一条失败、JDK/AGP/Gradle 兼容性 | 随机升级所有版本 |
| debug 能联网，release 失败 | 主 Manifest、生产地址、HTTPS/证书 | 关闭全部证书验证 |
| 模拟器连不上本机服务 | `10.0.2.2`、端口、监听地址 | 把手机 localhost 当电脑 |
| 插件 MissingPluginException | 是否新增插件后完整重启、目标是否支持 | 改业务模型掩盖错误 |
| 页面卡顿 | 真机 profile 帧、CPU、图片解码 | 仅测 debug 或全局加 const |
| 选图回来结果丢失 | 进程是否被回收、lost data | 假定 await 一定返回 |

`flutter clean` 是清理产物的工具，不是根因分析。只有怀疑过期构建产物且能说明理由时再使用，否则每轮重下依赖只会拖慢排错。

## 14.5 系统行为与性能

在真机上执行 `flutter run --profile -d <设备ID>`，通过 DevTools 检查 UI/raster 帧、CPU 和内存。关注滚动时是否频繁解码大图、是否在 UI isolate 同步处理大数据。

测试返回键与预测性返回、旋转、切后台再回来、权限在设置中撤销、进程终止后的恢复。“不保留活动”可帮助暴露部分生命周期问题，但不等同于所有真实进程回收场景。

## 14.6 本章交付

提交一份最小故障记录：设备和系统、构建模式、复现步骤、首条异常、根因、修复与再次验证结果。让 AI 基于这些证据判断层次，禁止同时改 Flutter、Gradle、SDK 三套版本来碰运气。

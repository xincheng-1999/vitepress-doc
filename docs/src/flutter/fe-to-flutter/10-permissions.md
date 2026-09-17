---
title: 第十章 按能力设计权限流程
---

# 第十章：按能力设计权限流程

先定义用户操作，再判断平台和插件实际需要什么权限。不要一进 App 就请求相机、照片、存储，更不要看到“选图”就复制所有媒体权限声明。

## 10.1 三层问题分别检查

1. 平台声明：Android Manifest、iOS Info.plist 等是否按能力配置。
2. 运行时授权：当前系统是否需要请求、用户是否允许。
3. 能力是否可用：设备有没有相机、照片是否在云端、文件是否还能访问。

授权成功不代表操作必然成功。模拟器相机不可用、用户取消、系统限制，都需要单独处理。

## 10.2 选一张照片通常不需要读取整个图库

现代 Android 系统 Photo Picker 让用户选择特定媒体，通常不要求应用申请广泛读取图库的权限。`image_picker` 在支持的版本上使用对应系统能力；以所锁定插件的 Android 配置说明为准。

不要为“附加一张照片”默认添加 `READ_MEDIA_IMAGES`、`READ_EXTERNAL_STORAGE` 或全文件管理权限。需要广泛读取图库的是另一类产品能力，也有不同的商店审核要求。

iOS 需按插件说明配置相应用途描述，系统 picker 与直接照片库访问的授权语义不同。使用系统选择器时，不要再无条件调用 `Permission.photos.request()` 阻挡用户选择。若能力确实读取照片库，要处理 limited，仅可访问用户授权的部分照片。

## 10.3 相机也取决于使用方式

`image_picker` 调起拍照流程与 `camera` 插件直接控制预览不是同一种实现。Android 的声明和运行时要求取决于插件与宿主配置；对 image_picker，不要不看 README 就补一套 CAMERA 请求。直接使用相机 API 时按插件要求声明并请求。

用于拍照与选图的 iOS 用途描述示例，合并到实际 Info.plist 的 dict 中：

```xml
<key>NSCameraUsageDescription</key>
<string>拍摄照片并附加到你正在编辑的笔记</string>
<key>NSPhotoLibraryUsageDescription</key>
<string>选择照片并附加到你正在编辑的笔记</string>
```

录制带声音的视频才考虑麦克风用途描述；不要为未提供的功能预先申请。只写 Dart 请求而缺失必要原生声明，可能触发原生错误或终止。

## 10.4 只有确实需要权限时才用 permission_handler

例如你的应用已经选择直接相机插件，并确认需要独立管理 camera 权限。以下是 permission_handler 的局部流程示例，不是 image_picker 的必需前置步骤：

```dart
Future<PermissionStatus> requestCameraIfNeeded() async {
  final current = await Permission.camera.status;
  if (current.isGranted || current.isPermanentlyDenied || current.isRestricted) {
    return current;
  }
  return Permission.camera.request();
}
```

需导入 `package:permission_handler/permission_handler.dart` 并按锁定版本配置 Android/iOS，包括必要的 iOS 编译宏等。不要只加 Dart 包就假定原生端已经启用全部能力。

| 状态 | 产品行为 |
| --- | --- |
| granted | 继续动作，仍捕获实际操作错误 |
| denied | 解释这项功能需要什么，允许用户稍后再试 |
| permanentlyDenied | 提供用户主动点击的“打开设置”入口 |
| restricted | 告知系统/管理策略限制，不无限请求 |
| limited（适用能力） | 在已授予范围内工作，提供管理选择入口 |

从系统设置回来后重新读取权限，不沿用离开前的 bool。只有用户选择前往设置时才调用 `openAppSettings()`，不要拒绝一次就自动跳设置页。

## 10.5 本章交付

为“给笔记附图”画出操作流程：点击 → 系统选择 → 选中/取消/失败 → 回到草稿。证明普通选图不会提前索要不需要的广泛权限。对确实需要授权的能力，真机测试首次请求、拒绝、永久拒绝、设置中撤销、再次进入。

AI 任务：“先列出插件版本、目标 OS 和实际能力，再引用该插件的平台配置要求。没有证据不要添加权限。”参考 [image_picker](https://pub.dev/packages/image_picker) 与 [permission_handler](https://pub.dev/packages/permission_handler)。

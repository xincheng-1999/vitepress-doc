---
title: 第十章 权限管理（Android / iOS 差异）
---

# 第十章：权限管理（Android / iOS 差异）（别等到上线才踩坑）

## 10.1 本章目标（验收标准）
完成后你需要能：
- 在 Flutter 中正确申请运行时权限
- 清楚 Android 与 iOS 权限配置文件分别在哪里
- 处理“用户拒绝/永久拒绝/仅一次允许”等状态
- 给相机/相册功能准备好权限基础（为下一章做铺垫）

---

## 10.2 核心概念：权限有两层
1) **声明权限**（配置文件里写）
2) **运行时请求**（用户弹窗授权）

**Web 对比：**
- Web 里权限常见是浏览器 API（比如 getUserMedia），配置较少
- 移动端必须同时处理平台配置 + 运行时状态

---

## 10.3 安装 permission_handler
```powershell
flutter pub add permission_handler
```

---

## 10.4 Android 权限配置（关键）
文件位置：
- `android/app/src/main/AndroidManifest.xml`

常见权限（相机/相册/存储）：
- 相机：`android.permission.CAMERA`
- 相册：Android 13+ 推荐 `READ_MEDIA_IMAGES`，旧版本用 `READ_EXTERNAL_STORAGE`

**示例（按需添加，不要全抄）：**
```xml
<manifest ...>

  <uses-permission android:name="android.permission.CAMERA" />

  <!-- Android 13+ 读取图片权限 -->
  <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />

  <!-- Android 12 及以下（某些机型/插件可能需要） -->
  <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />

  <application ...>
    ...
  </application>
</manifest>
```

**重要提醒：**
- 不要“一次性把所有权限都写上”，按功能需要最小化
- Android 13 开始媒体权限拆分：图片/视频/音频

---

## 10.5 iOS 权限配置（关键）
文件位置：
- `ios/Runner/Info.plist`

相机/相册必须提供用途说明，否则审核/运行都可能失败。

示例：
```xml
<key>NSCameraUsageDescription</key>
<string>用于拍摄笔记配图</string>

<key>NSPhotoLibraryUsageDescription</key>
<string>用于从相册选择笔记配图</string>
```

---

## 10.6 实战：封装一个“权限请求工具”（可复用）
目标：把权限逻辑从 UI 里抽出去。

新建：`lib/permissions/permission_service.dart`

```dart
import 'package:permission_handler/permission_handler.dart';

class PermissionService {
  const PermissionService();

  Future<bool> ensureCameraPermission() async {
    final status = await Permission.camera.status;
    if (status.isGranted) return true;

    final result = await Permission.camera.request();
    return result.isGranted;
  }

  Future<bool> ensurePhotosPermission() async {
    // iOS/Android 的“相册/照片库”在 permission_handler 里抽象为 photos
    final status = await Permission.photos.status;
    if (status.isGranted || status.isLimited) return true;

    final result = await Permission.photos.request();
    return result.isGranted || result.isLimited;
  }

  Future<void> openSettingsIfPermanentlyDenied() async {
    await openAppSettings();
  }
}
```

**Web 对比：**
- 你在 Web 里常做“统一封装 fetch/权限”
- Flutter 里也一样：把平台差异收敛到 service 层

---

## 10.7 UI 里如何正确处理“拒绝/永久拒绝”
示例（伪 UI 片段）：
```dart
final ok = await permissionService.ensureCameraPermission();
if (!ok) {
  // 给用户明确提示，并提供去设置页
  // 永久拒绝（Android 勾选不再询问 / iOS 拒绝后）一般只能引导去设置
}
```

建议交互：
- 第一次拒绝：解释用途 + 提供“再试一次”
- 永久拒绝：提供“去设置开启”按钮

---

## 10.8 实战小练习（必须做）

#### 练习 A：权限状态页
- 新建一个页面 `PermissionsDebugPage`
- 展示 camera/photos 权限状态（granted/denied/restricted 等）
- 提供两个按钮：请求相机权限、请求照片权限

#### 练习 B：把权限请求接到设置页
- 设置页加一个入口：`权限管理`
- 进入调试页

---

## 10.9 常见坑
- iOS 没写 `Info.plist` 的用途说明：运行直接崩/审核被拒
- Android 13 权限变化：旧的存储权限可能无效或行为不同
- 一进入 App 就弹权限：体验差；按功能触发时再申请
- 不处理“永久拒绝”：用户一直点按钮却永远没反应

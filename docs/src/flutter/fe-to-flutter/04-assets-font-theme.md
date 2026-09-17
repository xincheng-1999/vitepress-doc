---
title: 第四章 主题、资源、适配与可访问性
---

# 第四章：主题、资源、适配与可访问性

本章把“看起来没问题”变成可重复验证的 UI。不要让 AI 按截图到处写固定宽高和颜色；先确定主题与约束，再调整局部样式。

## 4.1 用语义颜色表达角色

局部应用配置，整合示例中也使用这套方式：

```dart
MaterialApp.router(
  routerConfig: router,
  theme: ThemeData(
    colorScheme: ColorScheme.fromSeed(seedColor: Colors.teal),
    useMaterial3: true,
  ),
  darkTheme: ThemeData(
    colorScheme: ColorScheme.fromSeed(
      seedColor: Colors.teal,
      brightness: Brightness.dark,
    ),
    useMaterial3: true,
  ),
  themeMode: ThemeMode.system,
)
```

页面用 `Theme.of(context).colorScheme` 和 `textTheme`。错误信息用 error/onError，表面内容使用合适的 surface/onSurface。不要把白色背景、黑色文本硬编码成每个组件的默认值，否则暗色模式只会改变一半页面。

主题偏好是跨页面状态，系统模式、浅色、深色应是三个选项；持久化用 shared_preferences 即可。先读出偏好再渲染，或明确接受启动阶段的短暂默认主题，不要在每次 build 读取磁盘。

## 4.2 声明资源，再加载

在现有 `pubspec.yaml` 的 `flutter:` 下合并配置，不要新建第二个同名块：

```yaml
flutter:
  uses-material-design: true
  assets:
    - assets/images/
  fonts:
    - family: AppSans
      fonts:
        - asset: assets/fonts/AppSans-Regular.ttf
        - asset: assets/fonts/AppSans-SemiBold.ttf
          weight: 600
```

文件要先真实存在，路径大小写要一致。新增资源声明后执行 `flutter pub get`，必要时重启。只引入实际使用且有授权的字体，中文全量字体可能明显增大包体。

```dart
Image.asset(
  'assets/images/empty.png',
  width: 160,
  semanticLabel: '暂无笔记',
  errorBuilder: (_, __, ___) => const Icon(Icons.note_outlined, size: 80),
)
```

纯装饰图片可用 `excludeFromSemantics: true`，不要让读屏器重复朗读旁边已有的文案。`Image.asset` 是应用内资源；桌面启动图标、Android adaptive icon、iOS AppIcon 是平台资源，不能靠在页面放一张图片完成。

## 4.3 适配依据是可用空间

- `MediaQuery.sizeOf(context)` 获取窗口尺寸，适合页面级决策。
- `LayoutBuilder` 获取父级给当前组件的约束，适合组件内部断点。
- `SafeArea` 避让系统侵入区域，不负责解决所有键盘遮挡。
- `Scaffold` 默认会针对键盘调整 body 可用空间；表单仍可能需要滚动。

局部示例：让宽屏编辑器保持可读宽度，不给每个控件写屏幕百分比。

```dart
Center(
  child: ConstrainedBox(
    constraints: const BoxConstraints(maxWidth: 720),
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: editor,
    ),
  ),
)
```

`editor` 是你的表单 Widget。断点应由内容需要决定。手机和平板并不是简单地按设备宽度等比放大所有字号。

## 4.4 可访问性直接影响能否使用

不要通过全局禁用文字缩放修复溢出。优先允许换行、减少固定高度、让页面滚动。使用系统文字缩放实际检查，不只在代码里改字号。

图标按钮设置 `tooltip`，必要时补 Semantics 标签；保存中明确显示进度并禁用重复提交；错误除颜色外还要有文字。交互区域通常至少满足 Material 的 48dp 触摸目标。只加 GestureDetector 包住一个小图标，很容易得到“看得见但点不中”的按钮。

日期显示用本地时间，数据交换和存储保持统一 UTC 约定。多语言项目通过 Flutter localization 与 ARB 管理文案，不用字符串拼接硬凑复数或日期格式。

## 4.5 本章交付与验收矩阵

| 场景 | 应看到什么 |
| --- | --- |
| 系统切暗色 | 文本、输入框、对话框均可辨识 |
| 字体放大至系统较大档位 | 按钮文案和正文不被固定高度截断 |
| 窄屏、横屏、键盘弹出 | 当前输入与提交入口可到达 |
| 图片文件缺失 | 页面仍可交互，显示替代内容 |
| TalkBack / VoiceOver | 可理解新增、编辑、删除按钮含义 |

让 AI 按这张表找出潜在问题并只改对应 Widget；你在设备上逐项复核。截图能辅助视觉检查，不能证明读屏、键盘与触控行为正确。

---
title: 第四章 资源、字体、主题与多端适配基础
---

# 第四章：资源、字体、主题与多端适配基础

## 4.1 本章目标（验收标准）
完成后你需要能：
- 正确配置并加载 assets（图片/JSON 等）
- 配置自定义字体（并在主题中统一应用）
- 用 `ThemeData` 管理亮/暗主题，避免“到处写样式”
- 初步掌握多端适配（SafeArea、文字缩放、屏幕尺寸差异）

---

## 4.2 核心概念：Flutter 的资源需要在 `pubspec.yaml` 声明
**Web 对比：**
- Web：静态资源由构建工具（Vite/Webpack）处理，import 即可
- Flutter：打包时需要把资源打进应用包里，必须在 `pubspec.yaml` 声明

---

## 4.3 实战：给“随手记”加 App 图标 + 空状态插图

#### 4.3.1 准备资源
在你的 Flutter 工程里创建目录：
- `assets/images/`

放两张图（你可以随便用占位图）：
- `assets/images/empty.png`
- `assets/images/logo.png`

#### 4.3.2 配置 `pubspec.yaml`
打开 `pubspec.yaml`，找到 `flutter:` 段，加入：

```yaml
flutter:
  uses-material-design: true

  assets:
    - assets/images/
```

然后拉取资源索引：
```powershell
flutter pub get
```

#### 4.3.3 在 UI 中加载资源（可运行示例）
把第 3 章中的 `_EmptyState` 改为：

```dart
class _EmptyState extends StatelessWidget {
  const _EmptyState();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Image.asset(
            'assets/images/empty.png',
            width: 160,
            fit: BoxFit.contain,
          ),
          const SizedBox(height: 12),
          const Text('还没有笔记'),
          const SizedBox(height: 6),
          const Text('点右下角 + 新建一条'),
        ],
      ),
    );
  }
}
```

**验收：**空状态显示图片且不会报找不到资源。

---

## 4.4 字体：让全局文本风格统一（不再逐个 Text 配样式）

#### 4.4.1 准备字体文件
在工程中创建：
- `assets/fonts/`

放入字体文件（例如：`MiSans-Regular.ttf`、`MiSans-Medium.ttf`，你也可以换别的字体）。

#### 4.4.2 配置 `pubspec.yaml`
示例：
```yaml
flutter:
  uses-material-design: true

  assets:
    - assets/images/

  fonts:
    - family: MiSans
      fonts:
        - asset: assets/fonts/MiSans-Regular.ttf
          weight: 400
        - asset: assets/fonts/MiSans-Medium.ttf
          weight: 500
```

执行：
```powershell
flutter pub get
```

#### 4.4.3 在主题中使用字体
在 `MaterialApp(theme: ...)` 里加：

```dart
theme: ThemeData(
  fontFamily: 'MiSans',
  colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
  useMaterial3: true,
),
```

**常见坑：**
- 字体文件路径写错 → 运行时报错或回退系统字体
- family 名称拼写不一致 → 看起来“没生效”

---

## 4.5 主题：亮/暗模式（移动端必须做）

#### 4.5.1 为什么要做主题
- 你不能像 Web 那样到处写 CSS；Flutter 里“把样式集中在 Theme”能保持一致性
- 适配暗色模式是用户的基本期望

#### 4.5.2 配置 `theme` + `darkTheme` + `themeMode`
可直接替换 `MaterialApp` 相关片段：

```dart
return MaterialApp(
  title: '随手记',
  theme: ThemeData(
    fontFamily: 'MiSans',
    colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue, brightness: Brightness.light),
    useMaterial3: true,
  ),
  darkTheme: ThemeData(
    fontFamily: 'MiSans',
    colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue, brightness: Brightness.dark),
    useMaterial3: true,
  ),
  themeMode: ThemeMode.system,
  home: const NotesHomePage(),
);
```

**验收：**手机切到暗色模式后，App 主题随系统变化。

---

## 4.6 多端适配：SafeArea、文字缩放、尺寸差异

#### 4.6.1 `SafeArea`（刘海屏/手势条必备）
你已经在第 3 章用过 `SafeArea`。

#### 4.6.2 文字缩放（Accessibility）
移动端用户可能把字体调大。你不应该写死所有高度。
- 尽量用 `Padding` + 自然高度
- 避免 `SizedBox(height: 40)` 这种强限制把文字压扁

#### 4.6.3 用 `LayoutBuilder` 做“响应式”
示例：
```dart
LayoutBuilder(
  builder: (context, constraints) {
    final isWide = constraints.maxWidth >= 600;
    return isWide ? const Text('平板布局') : const Text('手机布局');
  },
)
```

---

## 4.7 实战小练习（必须做）

#### 练习 A：做一个“设置页”开关暗色模式（先不持久化）
需求：
- AppBar 右上角放一个设置按钮
- 点击进入设置页
- 页面里有一个 Switch：控制 `ThemeMode.system/light/dark`（先做 light/dark 两种也行）

提示：本练习会为第 5 章路由做铺垫。

#### 练习 B：为列表项加默认头像/占位图
需求：
- 给 `NoteListItem` 左侧加一个 `CircleAvatar`
- 没有图片时用 `AssetImage('assets/images/logo.png')`

---

## 4.8 常见坑
- 改了 `pubspec.yaml` 后忘记 `flutter pub get`
- assets 目录缩进不对（YAML 对缩进敏感）
- 资源路径大小写不一致（Windows 不敏感，但 Android/Linux/macOS 可能敏感）
- 暗色模式下颜色对比不足：尽量用 `ColorScheme`，少写硬编码颜色

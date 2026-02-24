---
title: 第一章 环境搭建 + 第一个 Flutter App
---

# 第一章：环境搭建 + 第一个 Flutter App（从 0 跑起来）

## 1.1 本章目标（验收标准）
完成后你必须能做到：
- `flutter doctor -v` 自检通过（至少 Android toolchain 正常）
- 能创建项目并在模拟器/真机上跑起来
- 能理解热重载/热重启的区别
- 写出一个最小可用页面：列表 + 新增 + 删除（为后续章节做项目基座）

---

## 1.2 Flutter 与 Web 前端的关键差异（先建立正确心智）
**核心概念：声明式 UI，但渲染体系不同。**
- Web：DOM/CSS，由浏览器负责布局与绘制。
- Flutter：Widget（声明）→ Element（实例）→ RenderObject（布局/绘制），Flutter 自己在画布上渲染。

**你要切换的思维：**
- Web 常见：操作 DOM / class / CSS。
- Flutter：通过组合 Widget 表达 UI；状态变化触发 rebuild。

**前端映射：**
- 组件（React/Vue）≈ Widget
- props ≈ 构造参数（通常不可变）
- state/store ≈ State（短期）/ Riverpod Provider（长期）
- CSS Flex ≈ Row/Column/Flex + Expanded/Flexible

---

## 1.3 Windows 安装 Flutter（不省略步骤）

#### 1.3.1 下载安装 Flutter SDK
1) 下载 Flutter SDK（Stable）Windows zip
2) 解压到路径简单的位置（避免中文/空格），例如：
- `D:\dev\flutter`

#### 1.3.2 配置 PATH
将 Flutter 的 `bin` 加到系统环境变量 PATH：
- `D:\dev\flutter\bin`

验证：
```powershell
flutter --version
```

#### 1.3.3 安装 Android Studio 与 Android SDK
1) 安装 Android Studio
2) Android Studio → **SDK Manager** 确保安装：
- Android SDK Platform（至少一个）
- Android SDK Build-Tools
- Android SDK Command-line Tools (latest)
- Android Emulator（需要模拟器就装）

#### 1.3.4 接受 Android licenses
```powershell
flutter doctor --android-licenses
```
一路输入 `y`。

#### 1.3.5 自检（必须通过）
```powershell
flutter doctor -v
```
至少应看到：
- `Flutter`：OK
- `Android toolchain`：OK
- `Connected device`：能识别模拟器或真机

---

## 1.4 创建项目并运行（第一个 App）

#### 1.4.1 创建工程
```powershell
cd D:\myProject
flutter create fe_to_flutter_notes
cd fe_to_flutter_notes
```

#### 1.4.2 启动模拟器/连接真机
查看设备：
```powershell
flutter devices
```

#### 1.4.3 运行
```powershell
flutter run
```
终端快捷键：
- `r`：Hot Reload（热重载）
- `R`：Hot Restart（热重启）
- `q`：退出

---

## 1.5 项目结构（你现在必须认识的文件）
- `lib/`：Dart 代码
- `lib/main.dart`：入口
- `pubspec.yaml`：依赖与资源声明（类似 package.json + assets）
- `android/` / `ios/`：平台工程（权限、原生能力、打包配置）

---

## 1.6 实战：做一个最小“随手记”首页（可运行、可交互）
目标功能：
- 列表展示笔记
- 右下角 `+` 新建笔记（弹窗输入）
- 删除笔记

#### 1.6.1 替换 `lib/main.dart`（完整可运行）
把 `lib/main.dart` 全部替换为：

```dart
import 'package:flutter/material.dart';

void main() {
  runApp(const NotesApp());
}

class NotesApp extends StatelessWidget {
  const NotesApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '随手记',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
        useMaterial3: true,
      ),
      home: const NotesHomePage(),
    );
  }
}

class NotesHomePage extends StatefulWidget {
  const NotesHomePage({super.key});

  @override
  State<NotesHomePage> createState() => _NotesHomePageState();
}

class _NotesHomePageState extends State<NotesHomePage> {
  final List<Note> _notes = <Note>[];
  final TextEditingController _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _openAddDialog() async {
    _controller.clear();

    final String? text = await showDialog<String>(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text('新建笔记'),
          content: TextField(
            controller: _controller,
            autofocus: true,
            textInputAction: TextInputAction.done,
            decoration: const InputDecoration(
              hintText: '写点什么...',
              border: OutlineInputBorder(),
            ),
            onSubmitted: (value) {
              Navigator.of(context).pop(value.trim());
            },
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(null),
              child: const Text('取消'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(context).pop(_controller.text.trim()),
              child: const Text('保存'),
            ),
          ],
        );
      },
    );

    if (text == null || text.isEmpty) return;

    setState(() {
      _notes.insert(
        0,
        Note(
          id: DateTime.now().microsecondsSinceEpoch.toString(),
          content: text,
          createdAt: DateTime.now(),
        ),
      );
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('随手记'),
      ),
      body: _notes.isEmpty
          ? const Center(
              child: Text('还没有笔记，点右下角 + 新建一条'),
            )
          : ListView.separated(
              padding: const EdgeInsets.all(12),
              itemCount: _notes.length,
              separatorBuilder: (_, __) => const SizedBox(height: 8),
              itemBuilder: (context, index) {
                final note = _notes[index];
                return Card(
                  child: ListTile(
                    title: Text(
                      note.content,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                    subtitle: Text(_formatTime(note.createdAt)),
                    trailing: IconButton(
                      tooltip: '删除',
                      onPressed: () {
                        setState(() {
                          _notes.removeAt(index);
                        });
                      },
                      icon: const Icon(Icons.delete_outline),
                    ),
                  ),
                );
              },
            ),
      floatingActionButton: FloatingActionButton(
        onPressed: _openAddDialog,
        child: const Icon(Icons.add),
      ),
    );
  }

  String _formatTime(DateTime dt) {
    final two = (int n) => n.toString().padLeft(2, '0');
    return '${dt.year}-${two(dt.month)}-${two(dt.day)} ${two(dt.hour)}:${two(dt.minute)}';
  }
}

class Note {
  final String id;
  final String content;
  final DateTime createdAt;

  const Note({
    required this.id,
    required this.content,
    required this.createdAt,
  });
}
```

#### 1.6.2 运行验收
```powershell
flutter run
```
你应该能：新增一条、列表展示、删除。

---

## 1.7 Web 思维对照：你刚写的 State 管理是什么
- `_notes`：类似组件内 `useState` 的数据
- `setState(() { ... })`：类似 `setState` / `setNotes`，触发 UI 重新构建
- `ListView.separated`：类似 `array.map(renderItem)` + 虚拟列表（惰性构建）

---

## 1.8 实战小练习（必须做）

#### 练习 A：详情弹窗
要求：点击某条笔记，弹出对话框展示全文 + 创建时间。
提示：使用 `showDialog` + `AlertDialog`，内容区域用 `SingleChildScrollView`。

#### 练习 B：输入校验
要求：
- 少于 3 个字符禁止保存（按钮 disabled）
- 保存后关闭键盘：`FocusScope.of(context).unfocus()`

---

## 1.9 常见坑（Windows 高频）
- `flutter doctor` 找不到 Android SDK：Android Studio → SDK Manager 安装完整，再跑 `flutter doctor -v`
- 路径含中文/空格：尽量用英文路径（SDK/工程）
- 模拟器启动失败：检查 BIOS 虚拟化、Device Manager 配置；必要时优先用真机
- 热重载不生效：改了入口/初始化逻辑，使用热重启（`R`）

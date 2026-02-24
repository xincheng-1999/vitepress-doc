---
title: 第五章 路由与导航（go_router）
---

# 第五章：路由与导航（go_router）（像写 Web Router 一样写 App）

## 5.1 本章目标（验收标准）
完成后你需要能：
- 用 go_router 搭建基础路由
- 能从列表页跳到详情页，并带参数（path/query）
- 能处理返回结果（详情页编辑后回传）
- 理解 Android 返回键 / iOS 手势返回 的行为差异

---

## 5.2 核心概念：Flutter 的导航不是“切页面”，是“管理路由栈”
- Web：URL + History API
- Flutter：Navigator 管理页面栈（push/pop）

**为什么推荐 go_router：**
- 声明式路由，像写前端路由表
- 支持深链（Deep Link）/ URL 同步
- 更容易管理复杂路由

---

## 5.3 安装 go_router
在 Flutter 工程根目录执行：
```powershell
flutter pub add go_router
```

---

## 5.4 实战：把“随手记”改造成多页结构（首页/详情/设置）
目标路由：
- `/`：首页（笔记列表）
- `/note/:id`：详情页（显示全文，可编辑）
- `/settings`：设置页（先放一个占位）

#### 5.4.1 新建目录结构
在 `lib/` 下建立：
- `lib/pages/notes_home_page.dart`
- `lib/pages/note_detail_page.dart`
- `lib/pages/settings_page.dart`

#### 5.4.2 替换 `lib/main.dart`（完整可运行）
```dart
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'pages/note_detail_page.dart';
import 'pages/notes_home_page.dart';
import 'pages/settings_page.dart';

void main() {
  runApp(const NotesApp());
}

class NotesApp extends StatelessWidget {
  const NotesApp({super.key});

  @override
  Widget build(BuildContext context) {
    final router = GoRouter(
      routes: [
        GoRoute(
          path: '/',
          builder: (context, state) => const NotesHomePage(),
          routes: [
            GoRoute(
              path: 'note/:id',
              builder: (context, state) {
                final id = state.pathParameters['id']!;
                final content = state.uri.queryParameters['content'];
                return NoteDetailPage(id: id, initialContent: content);
              },
            ),
            GoRoute(
              path: 'settings',
              builder: (context, state) => const SettingsPage(),
            ),
          ],
        ),
      ],
    );

    return MaterialApp.router(
      title: '随手记',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
        useMaterial3: true,
      ),
      routerConfig: router,
    );
  }
}
```

#### 5.4.3 首页：`lib/pages/notes_home_page.dart`（完整可运行）
```dart
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

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
    final text = await showDialog<String>(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text('新建笔记'),
          content: TextField(
            controller: _controller,
            autofocus: true,
            decoration: const InputDecoration(
              hintText: '写点什么...',
              border: OutlineInputBorder(),
            ),
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
        actions: [
          IconButton(
            tooltip: '设置',
            onPressed: () => context.push('/settings'),
            icon: const Icon(Icons.settings_outlined),
          ),
        ],
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: _notes.isEmpty
              ? const Center(child: Text('还没有笔记，点右下角 + 新建一条'))
              : ListView.separated(
                  itemCount: _notes.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 8),
                  itemBuilder: (context, index) {
                    final note = _notes[index];
                    return Card(
                      child: ListTile(
                        title: Text(note.content, maxLines: 2, overflow: TextOverflow.ellipsis),
                        subtitle: Text(note.createdAt.toIso8601String()),
                        onTap: () async {
                          // 用 query 只是演示参数传递；真实项目一般只传 id
                          final result = await context.push<String>(
                            '/note/${note.id}?content=${Uri.encodeComponent(note.content)}',
                          );

                          if (result == null) return;
                          setState(() {
                            _notes[index] = note.copyWith(content: result);
                          });
                        },
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
        ),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _openAddDialog,
        child: const Icon(Icons.add),
      ),
    );
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

  Note copyWith({String? content}) {
    return Note(
      id: id,
      content: content ?? this.content,
      createdAt: createdAt,
    );
  }
}
```

#### 5.4.4 详情页：`lib/pages/note_detail_page.dart`（完整可运行）
```dart
import 'package:flutter/material.dart';

class NoteDetailPage extends StatefulWidget {
  final String id;
  final String? initialContent;

  const NoteDetailPage({
    super.key,
    required this.id,
    required this.initialContent,
  });

  @override
  State<NoteDetailPage> createState() => _NoteDetailPageState();
}

class _NoteDetailPageState extends State<NoteDetailPage> {
  late final TextEditingController _controller;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initialContent ?? '');
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('笔记 ${widget.id.substring(0, 6)}'),
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            children: [
              TextField(
                controller: _controller,
                maxLines: 8,
                decoration: const InputDecoration(
                  labelText: '内容',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: () {
                    final text = _controller.text.trim();
                    Navigator.of(context).pop(text);
                  },
                  child: const Text('保存并返回'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
```

#### 5.4.5 设置页：`lib/pages/settings_page.dart`（完整可运行）
```dart
import 'package:flutter/material.dart';

class SettingsPage extends StatelessWidget {
  const SettingsPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('设置')),
      body: const SafeArea(
        child: Padding(
          padding: EdgeInsets.all(12),
          child: Text('这里后面会加入主题切换、缓存清理等功能。'),
        ),
      ),
    );
  }
}
```

---

## 5.5 Web 前端路由对比：你需要注意的移动端细节
- Android：物理返回键会触发 `pop`（或者退出 App）
- iOS：左滑返回（手势）也会触发 `pop`
- 页面返回时的“回传结果”：Flutter 里是 `pop(result)`，非常常用

---

## 5.6 实战小练习（必须做）

#### 练习 A：新增“关于页面”
- 路由：`/about`
- 设置页里加一个入口
- 页面展示 App 版本（先写死字符串即可）

#### 练习 B：详情页加“放弃编辑提示”
需求：
- 如果内容被修改但没保存，点返回时弹出确认对话框
- 选择“放弃”才允许返回

提示：用 `PopScope`（新版本 Flutter）或 `WillPopScope`（老版本）。

---

## 5.7 常见坑
- 路由 path 少写 `/` 或写错嵌套路由：建议先照着本章结构跑通，再做复杂嵌套
- 参数编码问题：query 参数要 `Uri.encodeComponent`
- `context.push` 返回 `Future<T?>`：别忘了 `await`，否则拿不到回传结果

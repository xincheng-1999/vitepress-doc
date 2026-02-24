---
title: 第六章 状态管理（Riverpod）从 0 到可维护
---

# 第六章：状态管理（Riverpod）从 0 到可维护

## 6.1 本章目标（验收标准）
完成后你需要能：
- 理解“状态是什么、放哪里、谁负责改”
- 用 Riverpod 管理列表状态（增删改）
- 在多页面之间共享状态（首页改了，详情页也能同步）
- 学会最基本的可维护分层：UI 不直接改业务数据

---

## 6.2 核心概念：把状态从 Widget 里“抽离”
第 1~5 章你用 `setState` 管了 `_notes`。
- 这在单页 demo 可以
- 但一旦有：多页面共享、网络/数据库、缓存、测试，就会很痛

Riverpod 的价值：
- 状态集中管理，可测试
- UI 只订阅状态（watch），通过 notifier 修改（read）
- 天然适配 Flutter 的声明式 UI

**Web 对比：**
- Vue：Pinia/Vuex
- React：Redux/Zustand/Jotai
- Flutter：Riverpod 是目前非常主流且工程化友好的选择

---

## 6.3 安装依赖
```powershell
flutter pub add flutter_riverpod
```

---

## 6.4 实战：把 Notes 状态迁移到 Riverpod（完整可运行）

#### 6.4.1 规划文件结构（先做到“能维护”）
在 `lib/` 下创建：
- `lib/features/notes/note.dart`
- `lib/features/notes/notes_provider.dart`
- `lib/pages/notes_home_page.dart`
- `lib/pages/note_detail_page.dart`
- `lib/pages/settings_page.dart`
- `lib/main.dart`

> 本章为了可运行，会给出关键文件完整代码。你可以直接替换同名文件。

#### 6.4.2 `lib/features/notes/note.dart`（完整）
```dart
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

#### 6.4.3 `lib/features/notes/notes_provider.dart`（完整）
```dart
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'note.dart';

final notesProvider = NotifierProvider<NotesNotifier, List<Note>>(NotesNotifier.new);

class NotesNotifier extends Notifier<List<Note>> {
  @override
  List<Note> build() {
    return <Note>[];
  }

  void add(String content) {
    final note = Note(
      id: DateTime.now().microsecondsSinceEpoch.toString(),
      content: content,
      createdAt: DateTime.now(),
    );

    state = <Note>[note, ...state];
  }

  void removeById(String id) {
    state = state.where((n) => n.id != id).toList();
  }

  void updateContent({required String id, required String content}) {
    state = [
      for (final n in state)
        if (n.id == id) n.copyWith(content: content) else n,
    ];
  }

  Note? findById(String id) {
    for (final n in state) {
      if (n.id == id) return n;
    }
    return null;
  }
}
```

#### 6.4.4 `lib/main.dart`（完整）
```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'pages/note_detail_page.dart';
import 'pages/notes_home_page.dart';
import 'pages/settings_page.dart';

void main() {
  runApp(const ProviderScope(child: NotesApp()));
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
                return NoteDetailPage(id: id);
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

#### 6.4.5 `lib/pages/notes_home_page.dart`（完整）
```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/notes/notes_provider.dart';

class NotesHomePage extends ConsumerStatefulWidget {
  const NotesHomePage({super.key});

  @override
  ConsumerState<NotesHomePage> createState() => _NotesHomePageState();
}

class _NotesHomePageState extends ConsumerState<NotesHomePage> {
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
    ref.read(notesProvider.notifier).add(text);
  }

  @override
  Widget build(BuildContext context) {
    final notes = ref.watch(notesProvider);

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
          child: notes.isEmpty
              ? const Center(child: Text('还没有笔记，点右下角 + 新建一条'))
              : ListView.separated(
                  itemCount: notes.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 8),
                  itemBuilder: (context, index) {
                    final note = notes[index];
                    return Card(
                      child: ListTile(
                        title: Text(note.content, maxLines: 2, overflow: TextOverflow.ellipsis),
                        subtitle: Text(note.createdAt.toIso8601String()),
                        onTap: () => context.push('/note/${note.id}'),
                        trailing: IconButton(
                          tooltip: '删除',
                          onPressed: () => ref.read(notesProvider.notifier).removeById(note.id),
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
```

#### 6.4.6 `lib/pages/note_detail_page.dart`（完整）
```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../features/notes/notes_provider.dart';

class NoteDetailPage extends ConsumerStatefulWidget {
  final String id;

  const NoteDetailPage({
    super.key,
    required this.id,
  });

  @override
  ConsumerState<NoteDetailPage> createState() => _NoteDetailPageState();
}

class _NoteDetailPageState extends ConsumerState<NoteDetailPage> {
  late final TextEditingController _controller;
  String _initialText = '';

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final note = ref.watch(notesProvider.select((list) {
      for (final n in list) {
        if (n.id == widget.id) return n;
      }
      return null;
    }));

    if (note == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('详情')),
        body: const Center(child: Text('笔记不存在或已被删除')),
      );
    }

    // 首次进入时填充数据
    if (_controller.text.isEmpty) {
      _controller.text = note.content;
      _initialText = note.content;
    }

    final changed = _controller.text.trim() != _initialText.trim();

    return Scaffold(
      appBar: AppBar(
        title: Text('笔记 ${note.id.substring(0, 6)}'),
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            children: [
              TextField(
                controller: _controller,
                maxLines: 10,
                decoration: const InputDecoration(
                  labelText: '内容',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: changed
                      ? () {
                          final text = _controller.text.trim();
                          ref.read(notesProvider.notifier).updateContent(id: note.id, content: text);
                          Navigator.of(context).pop();
                        }
                      : null,
                  child: const Text('保存'),
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

#### 6.4.7 `lib/pages/settings_page.dart`（占位）
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
          child: Text('后续会加入主题切换、缓存清理等。'),
        ),
      ),
    );
  }
}
```

---

## 6.5 Web 前端思维对比：watch vs read
- `ref.watch(provider)`：订阅状态，状态变 → rebuild（类似 useSelector）
- `ref.read(provider.notifier)`：拿到控制器执行动作，不订阅（类似 dispatch）

---

## 6.6 实战小练习（必须做）

#### 练习 A：新增一个 `selectedNoteIdProvider`
需求：
- 点击某条笔记，把它的 id 写入 provider
- 首页标题显示：`随手记（已选中：xxxxxx）`

#### 练习 B：添加一个“撤销删除”
需求：
- 删除时缓存最近删除的 note
- Snackbar 提供“撤销”按钮，点击后恢复

提示：在 `NotesNotifier` 里加一个字段缓存最近删除项。

---

## 6.7 常见坑
- 把所有 provider 写在一个文件：前期可以，项目变大后会失控；建议按 feature 拆
- 在 build 里做副作用（网络/写库）：不要；副作用放 notifier/repository
- `watch` 过度：会导致不必要 rebuild；可以用 `select` 精准订阅

---
title: 第十七章 整合实战：可运行的离线随手记
---

# 第十七章：整合实战——可运行的离线随手记

这一章把前面的数据流连起来：列表 → 新建/编辑 → SQLite 保存 → 状态更新 → 返回列表。代码按文件提供，避免每章替换 main.dart 后互相冲突。先跑这份基线，再按第 7、10、11 章扩展网络和图片。

拿到下面这些文件后，配合 [项目导读](./01b-project-map.md) 的逐符号追踪表阅读：先从 main 跑到首页，再从保存按钮追到 SQLite。

## 17.1 范围与使用方式

本基线支持新增、编辑、删除、空状态、加载失败重试、保存失败保留草稿、未保存返回拦截、暗色模式和 SQLite 持久化。目标是 Android/iOS；未接入图片、登录、云同步和进程终止后的草稿恢复。

为方便阅读，保留五个 lib 文件；pages.dart 可以在熟悉流程后按页面拆分，不必先让 AI 生成大型架构。代码有意采用悲观写入：数据库成功后发布内存快照。所有业务写入都必须经同一个 controller，不支持外部写入后自动同步列表。

## 17.2 创建工程与固定依赖

验证基线：Flutter 3.38.7 / Dart 3.10.7。这是已检查的兼容组合，不表示最新版。新版本升级请作为独立变更处理。

```sh
flutter create --org com.example flutter_notes
cd flutter_notes
```

将生成的 `pubspec.yaml` 替换为下面这个完整文件。示例没有使用模板的 flutter_lints 配置，因此同时把 `analysis_options.yaml` 替换为下方配置。将默认 `test/widget_test.dart` 替换为本章的 `test/notes_test.dart`，避免继续测试默认计数器。

```yaml
name: flutter_notes
publish_to: none
environment:
  sdk: '>=3.10.0 <4.0.0'
dependencies:
  flutter:
    sdk: flutter
  flutter_riverpod: 3.0.3
  go_router: 16.2.4
  sqflite: 2.4.2
  path: 1.9.1
  uuid: 4.5.1
dev_dependencies:
  flutter_test:
    sdk: flutter
flutter:
  uses-material-design: true
```

`analysis_options.yaml`：

```yaml
analyzer:
  errors:
    unused_import: warning
linter:
  rules:
    - use_build_context_synchronously
```

```sh
flutter pub get
```

应用应保存解析得到的 `pubspec.lock`。以下文件名和 import 一一对应；使用其他项目名称时，同步修改测试中的 `package:flutter_notes/`。

## 17.3 模型：不可变快照与稳定排序

Note 保存 UTC 时间，列表以时间倒序、id 升序稳定排序。字段相等不会自动改变普通类的默认相等行为，本例不依赖值相等。

完整 `lib/note.dart`：

```dart
class Note {
  const Note({
    required this.id,
    required this.content,
    required this.updatedAt,
  });

  final String id;
  final String content;
  final DateTime updatedAt;

  Map<String, Object?> toRow() => {
    'id': id,
    'content': content,
    'updated_at': updatedAt.toUtc().millisecondsSinceEpoch,
  };

  factory Note.fromRow(Map<String, Object?> row) => Note(
    id: row['id'] as String,
    content: row['content'] as String,
    updatedAt: DateTime.fromMillisecondsSinceEpoch(
      row['updated_at'] as int,
      isUtc: true,
    ),
  );
}

List<Note> sortedNotes(Iterable<Note> notes) {
  final result = notes.toList()
    ..sort((a, b) {
      final byTime = b.updatedAt.compareTo(a.updatedAt);
      return byTime == 0 ? a.id.compareTo(b.id) : byTime;
    });
  return List.unmodifiable(result);
}
```

## 17.4 Repository：UI 不接触 SQL

数据库 v1 只包含正文与时间。图片扩展前先完成第 9 章迁移。事务中通过 update/insert 保留明确的更新语义。

完整 `lib/notes_repository.dart`：

```dart
import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';
import 'note.dart';

abstract interface class NotesRepository {
  Future<List<Note>> list();
  Future<void> upsert(Note note);
  Future<void> deleteById(String id);
}

class SqliteNotesRepository implements NotesRepository {
  SqliteNotesRepository._(this._db);
  final Database _db;

  static Future<SqliteNotesRepository> open() async {
    final db = await openDatabase(
      p.join(await getDatabasesPath(), 'notes.db'),
      version: 1,
      onCreate: (db, version) async {
        await db.execute('''
          CREATE TABLE notes (
            id TEXT PRIMARY KEY,
            content TEXT NOT NULL,
            updated_at INTEGER NOT NULL
          )
        ''');
      },
    );
    return SqliteNotesRepository._(db);
  }

  @override
  Future<List<Note>> list() async {
    final rows = await _db.query('notes', orderBy: 'updated_at DESC, id ASC');
    return List.unmodifiable(rows.map(Note.fromRow));
  }

  @override
  Future<void> upsert(Note note) async {
    await _db.transaction((txn) async {
      final changed = await txn.update(
        'notes',
        note.toRow(),
        where: 'id = ?',
        whereArgs: [note.id],
      );
      if (changed == 0) await txn.insert('notes', note.toRow());
    });
  }

  @override
  Future<void> deleteById(String id) async {
    await _db.delete('notes', where: 'id = ?', whereArgs: [id]);
  }

  Future<void> close() => _db.close();
}
```

## 17.5 Controller：成功后发布、失败可重试

队列让连续操作按顺序执行；调用者收到异常，但后续命令不会被前一次失败卡住。长度统一按 Unicode 码点计算；若产品需要用户感知字符（grapheme cluster）计数，应在表单与业务层同时改用 characters，不能只改 UI。

完整 `lib/notes_provider.dart`：

```dart
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'note.dart';
import 'notes_repository.dart';

final repositoryProvider = Provider<NotesRepository>(
  (ref) => throw UnimplementedError('入口必须提供 repository'),
);

final notesProvider = AsyncNotifierProvider<NotesController, List<Note>>(
  NotesController.new,
  retry: (retryCount, error) => null,
);

class NotesController extends AsyncNotifier<List<Note>> {
  Future<void> _pending = Future<void>.value();

  @override
  Future<List<Note>> build() async {
    return sortedNotes(await ref.watch(repositoryProvider).list());
  }

  Future<void> _enqueue(Future<void> Function() action) {
    final result = _pending.then((_) => action());
    // 返回原 result 让调用者收到错误；队列自身恢复，以便下次可以重试。
    _pending = result.then<void>((_) {}, onError: (Object _, StackTrace __) {});
    return result;
  }

  Future<void> save(Note note) => _enqueue(() async {
    final content = note.content.trim();
    if (content.isEmpty || content.runes.length > 2000) {
      throw ArgumentError('内容必须为 1 到 2000 个 Unicode 码点');
    }
    final normalized = Note(
      id: note.id,
      content: content,
      updatedAt: note.updatedAt,
    );
    final before = await future;
    await ref.read(repositoryProvider).upsert(normalized);
    if (!ref.mounted) return;
    state = AsyncData(
      sortedNotes([
        for (final old in before)
          if (old.id != normalized.id) old,
        normalized,
      ]),
    );
  });

  Future<void> deleteById(String id) => _enqueue(() async {
    final before = await future;
    await ref.read(repositoryProvider).deleteById(id);
    if (!ref.mounted) return;
    state = AsyncData(sortedNotes(before.where((note) => note.id != id)));
  });
}
```

## 17.6 入口：初始化、注入与稳定路由

数据库打开失败时显示重试入口。NotesApp 自己持有并释放 router，主题 rebuild 不会重新创建它。Bootstrap 管理数据库连接；测试绕过 Bootstrap 注入 fake。

完整 `lib/main.dart`：

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'notes_provider.dart';
import 'notes_repository.dart';
import 'pages.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const Bootstrap());
}

class Bootstrap extends StatefulWidget {
  const Bootstrap({super.key});
  @override
  State<Bootstrap> createState() => _BootstrapState();
}

class _BootstrapState extends State<Bootstrap> {
  late Future<SqliteNotesRepository> _opening;

  @override
  void initState() {
    super.initState();
    _opening = SqliteNotesRepository.open();
  }

  @override
  void dispose() {
    _opening.then<void>(
      (repo) => repo.close(),
      onError: (Object _, StackTrace __) {},
    );
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<SqliteNotesRepository>(
      future: _opening,
      builder: (context, snapshot) {
        final repository = snapshot.data;
        if (repository != null) {
          return ProviderScope(
            overrides: [repositoryProvider.overrideWithValue(repository)],
            child: const NotesApp(),
          );
        }
        return MaterialApp(
          home: Scaffold(
            body: Center(
              child: snapshot.hasError
                  ? Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Text('本地数据库打开失败'),
                        FilledButton(
                          onPressed: () => setState(() {
                            _opening = SqliteNotesRepository.open();
                          }),
                          child: const Text('重试'),
                        ),
                      ],
                    )
                  : const CircularProgressIndicator(),
            ),
          ),
        );
      },
    );
  }
}

class NotesApp extends StatefulWidget {
  const NotesApp({super.key});
  @override
  State<NotesApp> createState() => _NotesAppState();
}

class _NotesAppState extends State<NotesApp> {
  late final GoRouter _router = GoRouter(
    routes: [
      GoRoute(path: '/', builder: (_, __) => const NotesPage()),
      GoRoute(path: '/notes/new', builder: (_, __) => const EditorLoader()),
      GoRoute(
        path: '/notes/:id/edit',
        builder: (_, state) => EditorLoader(id: state.pathParameters['id']!),
      ),
    ],
    errorBuilder: (_, __) => const NotFoundPage(),
  );

  @override
  void dispose() {
    _router.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: '随手记',
      routerConfig: _router,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.teal),
      ),
      darkTheme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: Colors.teal,
          brightness: Brightness.dark,
        ),
      ),
      themeMode: ThemeMode.system,
    );
  }
}
```

## 17.7 页面：列表、加载器与草稿编辑

EditorLoader 先等数据，再创建编辑器；controller 在 initState 初始化，build 不覆盖文本。保存失败保留输入，成功时先更新 PopScope 再导航。放弃按钮明确丢弃本页草稿；返回手势与键盘行为仍需设备验证。

完整 `lib/pages.dart`：

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:uuid/uuid.dart';
import 'note.dart';
import 'notes_provider.dart';

class NotesPage extends ConsumerStatefulWidget {
  const NotesPage({super.key});
  @override
  ConsumerState<NotesPage> createState() => _NotesPageState();
}

class _NotesPageState extends ConsumerState<NotesPage> {
  bool _deleting = false;

  Future<void> _delete(Note note) async {
    if (_deleting) return;
    setState(() => _deleting = true);
    try {
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('删除这条笔记？'),
          content: const Text('删除后无法撤销。'),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('取消'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('删除'),
            ),
          ],
        ),
      );
      if (confirmed != true || !mounted) return;
      await ref.read(notesProvider.notifier).deleteById(note.id);
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('删除失败，请重试')));
    } finally {
      if (mounted) setState(() => _deleting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final notes = ref.watch(notesProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('随手记')),
      body: SafeArea(
        child: notes.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (_, __) => const LoadFailure(),
          data: (items) => items.isEmpty
              ? const Center(child: Text('还没有笔记'))
              : ListView.builder(
                  itemCount: items.length,
                  itemBuilder: (context, index) {
                    final note = items[index];
                    return ListTile(
                      key: ValueKey(note.id),
                      title: Text(
                        note.content,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      subtitle: Text(note.updatedAt.toLocal().toString()),
                      onTap: _deleting
                          ? null
                          : () => context.push('/notes/${note.id}/edit'),
                      trailing: IconButton(
                        tooltip: '删除笔记',
                        onPressed: _deleting ? null : () => _delete(note),
                        icon: const Icon(Icons.delete_outline),
                      ),
                    );
                  },
                ),
        ),
      ),
      floatingActionButton: FloatingActionButton(
        tooltip: '新建笔记',
        onPressed: _deleting || !notes.hasValue
            ? null
            : () => context.push('/notes/new'),
        child: const Icon(Icons.add),
      ),
    );
  }
}

class LoadFailure extends ConsumerWidget {
  const LoadFailure({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) => Center(
    child: FilledButton(
      onPressed: () => ref.invalidate(notesProvider),
      child: const Text('读取失败，点击重试'),
    ),
  );
}

class EditorLoader extends ConsumerWidget {
  const EditorLoader({super.key, this.id});
  final String? id;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(notesProvider);
    return state.when(
      loading: () =>
          const Scaffold(body: Center(child: CircularProgressIndicator())),
      error: (_, __) => const Scaffold(body: LoadFailure()),
      data: (notes) {
        if (id == null) return const NoteEditor(key: ValueKey('new'));
        for (final note in notes) {
          if (note.id == id) {
            return NoteEditor(key: ValueKey(note.id), note: note);
          }
        }
        return const NotFoundPage();
      },
    );
  }
}

class NotFoundPage extends StatelessWidget {
  const NotFoundPage({super.key});
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('页面不存在')),
    body: Center(
      child: TextButton(
        onPressed: () => context.go('/'),
        child: const Text('返回列表'),
      ),
    ),
  );
}

class NoteEditor extends ConsumerStatefulWidget {
  const NoteEditor({super.key, this.note});
  final Note? note;
  @override
  ConsumerState<NoteEditor> createState() => _NoteEditorState();
}

class _NoteEditorState extends ConsumerState<NoteEditor> {
  final _formKey = GlobalKey<FormState>();
  late final String _id;
  late final String _initial;
  late final TextEditingController _controller;
  bool _saving = false;
  bool _allowExit = false;
  String? _error;

  bool get _dirty => _controller.text != _initial;

  @override
  void initState() {
    super.initState();
    _id = widget.note?.id ?? const Uuid().v4();
    _initial = widget.note?.content ?? '';
    _controller = TextEditingController(text: _initial);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _leave() {
    setState(() => _allowExit = true);
    // 等 PopScope 用新的 canPop 重建，避免成功保存仍被旧的拦截逻辑挡住。
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      if (context.canPop()) {
        context.pop();
      } else {
        context.go('/');
      }
    });
  }

  Future<void> _save() async {
    if (_saving || !_formKey.currentState!.validate()) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await ref
          .read(notesProvider.notifier)
          .save(
            Note(
              id: _id,
              content: _controller.text.trim(),
              updatedAt: DateTime.now().toUtc(),
            ),
          );
      if (!mounted) return;
      _leave();
    } catch (_) {
      if (!mounted) return;
      setState(() => _error = '保存失败，内容已保留，请重试');
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope<Object?>(
      canPop: _allowExit || (!_dirty && !_saving),
      onPopInvokedWithResult: (didPop, result) {
        if (didPop) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(_saving ? '正在保存，请稍候' : '请保存，或点击“放弃修改”')),
        );
      },
      child: Scaffold(
        appBar: AppBar(title: Text(widget.note == null ? '新建笔记' : '编辑笔记')),
        body: SafeArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 720),
                child: Form(
                  key: _formKey,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      TextFormField(
                        controller: _controller,
                        enabled: !_saving,
                        minLines: 5,
                        maxLines: 12,
                        decoration: const InputDecoration(labelText: '笔记内容'),
                        onChanged: (_) => setState(() {}),
                        validator: (value) {
                          final text = (value ?? '').trim();
                          if (text.isEmpty) return '请输入内容';
                          if (text.runes.length > 2000)
                            return '最多 2000 个 Unicode 码点';
                          return null;
                        },
                      ),
                      if (_error != null) ...[
                        const SizedBox(height: 12),
                        Text(
                          _error!,
                          style: TextStyle(
                            color: Theme.of(context).colorScheme.error,
                          ),
                        ),
                      ],
                      const SizedBox(height: 16),
                      FilledButton(
                        onPressed: _saving ? null : _save,
                        child: Text(_saving ? '保存中…' : '保存'),
                      ),
                      TextButton(
                        onPressed: _saving ? null : _leave,
                        child: const Text('放弃修改'),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
```

## 17.8 测试：验证真实行为

这组测试覆盖 CRUD、不可变快照、失败不更新、队列重试、连续提交、表单校验，以及保存失败保留草稿并成功重试。fake 验证业务流程，不证明 SQLite 插件或原生平台正确。

完整 `test/notes_test.dart`：

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_notes/main.dart';
import 'package:flutter_notes/note.dart';
import 'package:flutter_notes/notes_provider.dart';
import 'package:flutter_notes/notes_repository.dart';

class FakeNotesRepository implements NotesRepository {
  final data = <String, Note>{};
  bool failNextSave = false;
  int writes = 0;

  @override
  Future<List<Note>> list() async => sortedNotes(data.values);

  @override
  Future<void> upsert(Note note) async {
    writes++;
    if (failNextSave) {
      failNextSave = false;
      throw StateError('模拟磁盘写入失败');
    }
    data[note.id] = note;
  }

  @override
  Future<void> deleteById(String id) async {
    data.remove(id);
  }
}

Note note(String id, String content) =>
    Note(id: id, content: content, updatedAt: DateTime.utc(2026, 1, 1));

void main() {
  test('新增、编辑与删除经过 repository，并发布不可变快照', () async {
    final repo = FakeNotesRepository();
    final container = ProviderContainer(
      overrides: [repositoryProvider.overrideWithValue(repo)],
    );
    addTearDown(container.dispose);
    await container.read(notesProvider.future);
    final controller = container.read(notesProvider.notifier);
    await controller.save(note('a', ' 第一条 '));
    final snapshot = await container.read(notesProvider.future);
    expect(snapshot.single.content, '第一条');
    expect(() => snapshot.clear(), throwsUnsupportedError);
    await controller.save(note('a', '修改后'));
    expect(repo.data['a']!.content, '修改后');
    expect(snapshot.single.content, '第一条');
    await controller.deleteById('a');
    expect(await container.read(notesProvider.future), isEmpty);
    expect(repo.data, isEmpty);
  });

  test('失败不发布假数据，失败后队列仍可重试且不会丢并发提交', () async {
    final repo = FakeNotesRepository()..failNextSave = true;
    final container = ProviderContainer(
      overrides: [repositoryProvider.overrideWithValue(repo)],
    );
    addTearDown(container.dispose);
    await container.read(notesProvider.future);
    final controller = container.read(notesProvider.notifier);
    await expectLater(controller.save(note('a', 'A')), throwsStateError);
    expect(await container.read(notesProvider.future), isEmpty);
    await Future.wait([
      controller.save(note('a', 'A')),
      controller.save(note('b', 'B')),
    ]);
    expect((await container.read(notesProvider.future)).map((n) => n.id), [
      'a',
      'b',
    ]);
    await expectLater(controller.save(note('c', '  ')), throwsArgumentError);
    expect(repo.data.containsKey('c'), isFalse);
  });

  testWidgets('失败保留草稿，重试成功返回列表；再次进入能编辑', (tester) async {
    final repo = FakeNotesRepository()..failNextSave = true;
    await tester.pumpWidget(
      ProviderScope(
        overrides: [repositoryProvider.overrideWithValue(repo)],
        child: const NotesApp(),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('还没有笔记'), findsOneWidget);
    await tester.tap(find.byTooltip('新建笔记'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, '保存'));
    await tester.pumpAndSettle();
    expect(find.text('请输入内容'), findsOneWidget);
    expect(repo.writes, 0);
    await tester.enterText(find.byType(TextFormField), '我的草稿');
    await tester.tap(find.widgetWithText(FilledButton, '保存'));
    await tester.pumpAndSettle();
    expect(find.text('保存失败，内容已保留，请重试'), findsOneWidget);
    expect(
      tester.widget<TextFormField>(find.byType(TextFormField)).controller!.text,
      '我的草稿',
    );
    expect(repo.data, isEmpty);
    await tester.tap(find.widgetWithText(FilledButton, '保存'));
    await tester.pumpAndSettle();
    expect(find.byType(TextFormField), findsNothing);
    expect(find.text('我的草稿'), findsOneWidget);
    await tester.tap(find.text('我的草稿'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextFormField), '改过的内容');
    await tester.tap(find.widgetWithText(FilledButton, '保存'));
    await tester.pumpAndSettle();
    expect(repo.data.values.single.content, '改过的内容');
    expect(find.text('改过的内容'), findsOneWidget);
  });
}
```

## 17.9 运行与分层验收

```sh
dart format lib test
flutter analyze
flutter test
flutter devices
flutter run -d <设备ID>
```

本章代码在上述 Flutter/Dart 基线完成了静态分析及 3 个自动测试。自动测试使用内存 fake，没有运行原生 SQLite、签名构建或真实设备，不能据此宣称已经完成移动端验收。

在 Android/iOS 设备上继续验证：

| 操作 | 预期结果 |
| --- | --- |
| 新建非空内容 | 保存后返回列表，只有一条记录 |
| 编辑已有内容 | 同 id 更新，数量不增加 |
| 只输入空白 | 显示校验，不调用保存 |
| 输入后尝试返回 | 不静默丢弃，提示保存或放弃 |
| 保存中重复点击 | 本次写入不会重复触发 |
| 删除并确认 | 写库成功后列表移除，失败提示重试 |
| 强制关闭后重启 | 已保存内容保留，已删除内容不再出现 |
| 大字体、暗色、横屏与键盘 | 文字可读，输入和按钮可到达 |

若真机首次打开数据库失败，先检查插件注册、平台支持和原生构建日志，不把失败改成“返回空列表”。若测试启动后仍在运行模板计数器测试，核对是否真的替换了旧测试文件。

## 17.10 用 AI 做下一条功能

选择「本地搜索」作为第一项扩展，限定只搜索已保存正文。先定义空查询、无结果、大小写和编辑后结果更新，再让 AI 实现；不要同时加入远程接口、登录与状态库迁移。

```text
请基于现有离线随手记增加本地搜索。
先读取模型、provider、页面与测试，不升级依赖。
搜索词归列表页，已保存列表仍由 notesProvider 唯一持有；
过滤结果是派生数据，不维护可独立修改的第二份业务列表。
空查询显示全部；忽略大小写；无结果与没有笔记显示不同提示。
给出用户输入、清空、编辑后更新结果的测试，并执行 analyze/test。
```

完成后再按需求加入图片和数据库 v2，随后考虑网络。始终用一个闭环功能换来一组可验证能力，而不是一次生成几十个暂时用不到的文件。

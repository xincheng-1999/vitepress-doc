---
title: 第九章 本地存储与缓存（shared_preferences / secure_storage / SQLite）
---

# 第九章：本地存储与缓存（从“内存 demo”升级到“可用 App”）

## 9.1 本章目标（验收标准）
完成后你需要能：
- 使用 `shared_preferences` 保存简单设置
- 使用 `flutter_secure_storage` 保存敏感信息（token 等）
- 使用 `sqflite` + `path_provider` 把笔记落库（增删改查）
- 用 Riverpod 把“加载中/失败/成功”三态接入 UI

---

## 9.2 核心概念：本地存储分三类
1) **配置/开关**（非敏感，小数据）→ `shared_preferences`
2) **敏感信息**（token/密钥）→ `flutter_secure_storage`
3) **结构化业务数据**（笔记、列表、关系）→ SQLite（`sqflite`）

**Web 对比：**
- localStorage/sessionStorage ≈ shared_preferences（但移动端是异步 I/O）
- IndexedDB ≈ SQLite（但 SQLite 更强、更可控）

---

## 9.3 安装依赖（不省略步骤）
```powershell
flutter pub add shared_preferences
flutter pub add flutter_secure_storage
flutter pub add sqflite
flutter pub add path_provider
```

---

## 9.4 实战：用 SQLite 持久化 Notes（完整链路）

#### 9.4.1 新建文件结构
在 `lib/` 下创建：
- `lib/storage/app_database.dart`
- `lib/features/notes/note.dart`（更新：支持 DB 映射）
- `lib/features/notes/notes_repository.dart`
- `lib/features/notes/notes_provider.dart`（更新：AsyncNotifier）

并修改 UI：首页从 `List<Note>` 变为 `AsyncValue<List<Note>>`。

---

## 9.5 数据库层：`lib/storage/app_database.dart`（完整可运行）
```dart
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:sqflite/sqflite.dart';

class AppDatabase {
  static const _dbName = 'notes.db';
  static const _dbVersion = 1;

  Database? _db;

  Future<Database> get database async {
    final existing = _db;
    if (existing != null) return existing;

    final dir = await getApplicationDocumentsDirectory();
    final path = p.join(dir.path, _dbName);

    final db = await openDatabase(
      path,
      version: _dbVersion,
      onCreate: (db, version) async {
        await db.execute('''
CREATE TABLE notes (
  id TEXT PRIMARY KEY,
  content TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
''');
        await db.execute('CREATE INDEX idx_notes_created_at ON notes(created_at);');
      },
    );

    _db = db;
    return db;
  }

  Future<void> close() async {
    final db = _db;
    _db = null;
    await db?.close();
  }
}
```

---

## 9.6 模型层更新：`lib/features/notes/note.dart`（完整）
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

  Map<String, Object?> toDb() {
    return {
      'id': id,
      'content': content,
      'created_at': createdAt.millisecondsSinceEpoch,
    };
  }

  static Note fromDb(Map<String, Object?> row) {
    return Note(
      id: row['id'] as String,
      content: row['content'] as String,
      createdAt: DateTime.fromMillisecondsSinceEpoch(row['created_at'] as int),
    );
  }
}
```

---

## 9.7 Repository：`lib/features/notes/notes_repository.dart`（完整）
```dart
import 'package:sqflite/sqflite.dart';

import '../../storage/app_database.dart';
import 'note.dart';

class NotesRepository {
  final AppDatabase _db;

  const NotesRepository(this._db);

  Future<List<Note>> list() async {
    final db = await _db.database;
    final rows = await db.query(
      'notes',
      orderBy: 'created_at DESC',
    );

    return rows.map((e) => Note.fromDb(e.cast<String, Object?>())).toList(growable: false);
  }

  Future<void> upsert(Note note) async {
    final db = await _db.database;
    await db.insert(
      'notes',
      note.toDb(),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<void> deleteById(String id) async {
    final db = await _db.database;
    await db.delete('notes', where: 'id = ?', whereArgs: [id]);
  }

  Future<Note?> findById(String id) async {
    final db = await _db.database;
    final rows = await db.query('notes', where: 'id = ?', whereArgs: [id], limit: 1);
    if (rows.isEmpty) return null;
    return Note.fromDb(rows.first.cast<String, Object?>());
  }
}
```

---

## 9.8 Provider：`lib/features/notes/notes_provider.dart`（完整）
```dart
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../storage/app_database.dart';
import 'note.dart';
import 'notes_repository.dart';

final appDatabaseProvider = Provider<AppDatabase>((ref) {
  final db = AppDatabase();
  ref.onDispose(() {
    db.close();
  });
  return db;
});

final notesRepositoryProvider = Provider<NotesRepository>((ref) {
  final db = ref.read(appDatabaseProvider);
  return NotesRepository(db);
});

final notesProvider = AsyncNotifierProvider<NotesAsyncNotifier, List<Note>>(NotesAsyncNotifier.new);

class NotesAsyncNotifier extends AsyncNotifier<List<Note>> {
  @override
  Future<List<Note>> build() async {
    return ref.read(notesRepositoryProvider).list();
  }

  Future<void> add(String content) async {
    final repo = ref.read(notesRepositoryProvider);
    final note = Note(
      id: DateTime.now().microsecondsSinceEpoch.toString(),
      content: content,
      createdAt: DateTime.now(),
    );

    await repo.upsert(note);
    state = AsyncData(await repo.list());
  }

  Future<void> updateContent({required String id, required String content}) async {
    final repo = ref.read(notesRepositoryProvider);
    final existing = await repo.findById(id);
    if (existing == null) return;

    await repo.upsert(existing.copyWith(content: content));
    state = AsyncData(await repo.list());
  }

  Future<void> removeById(String id) async {
    final repo = ref.read(notesRepositoryProvider);
    await repo.deleteById(id);
    state = AsyncData(await repo.list());
  }
}
```

---

## 9.9 UI 改造要点：从 `List` 变 `AsyncValue<List>`
你首页原来：
- `final notes = ref.watch(notesProvider);`（List）

现在：
- `final notesAsync = ref.watch(notesProvider);`（AsyncValue）

渲染结构（示例）：
```dart
final notesAsync = ref.watch(notesProvider);

return notesAsync.when(
  loading: () => const Center(child: CircularProgressIndicator()),
  error: (e, _) => Center(child: Text('加载失败：$e')),
  data: (notes) => ListView.builder(
    itemCount: notes.length,
    itemBuilder: (context, index) => Text(notes[index].content),
  ),
);
```

**验收：**
- 重启 App 后，之前新建的笔记仍然存在

---

## 9.10 shared_preferences 与 secure_storage（快速落地）

#### 9.10.1 shared_preferences 保存主题开关（示例）
```dart
import 'package:shared_preferences/shared_preferences.dart';

Future<void> saveDarkMode(bool value) async {
  final sp = await SharedPreferences.getInstance();
  await sp.setBool('darkMode', value);
}

Future<bool> loadDarkMode() async {
  final sp = await SharedPreferences.getInstance();
  return sp.getBool('darkMode') ?? false;
}
```

#### 9.10.2 secure_storage 保存 token（示例）
```dart
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

const _storage = FlutterSecureStorage();

Future<void> saveToken(String token) => _storage.write(key: 'token', value: token);
Future<String?> loadToken() => _storage.read(key: 'token');
Future<void> clearToken() => _storage.delete(key: 'token');
```

---

## 9.11 实战小练习（必须做）

#### 练习 A：做一个“清空全部笔记”按钮
- 在设置页加按钮
- 点击弹确认框
- 确认后清空表

提示：Repository 增加 `deleteAll()`。

#### 练习 B：把主题开关持久化
- 设置页的 Switch 与 shared_preferences 绑定
- 下次打开 App 仍保持上次选择

---

## 9.12 常见坑
- 忘记处理 `AsyncValue` 三态：会出现空白页/闪屏
- `sqflite` 的路径：必须用 `path_provider` 获取 app 目录，别写死路径
- 数据库 schema 变更：需要版本号 + migration（后续架构章会讲“如何升级表结构”）

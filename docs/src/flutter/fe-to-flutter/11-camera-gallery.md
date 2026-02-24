---
title: 第十一章 相机 + 相册选图（完整链路）
---

# 第十一章：相机 + 相册选图（完整链路：权限 → 选图/拍照 → 存储 → 展示）

## 11.1 本章目标（验收标准）
完成后你需要能：
- 从相册选择图片，并显示预览
- 调用系统相机拍照，并拿到图片文件
- 把图片路径与笔记一起保存到 SQLite
- 处理权限拒绝、图片过大、路径失效等常见问题

---

## 11.2 核心概念：移动端图片流程和 Web 不一样
**Web**
- 上传通常拿到 `File/Blob`，靠浏览器管理临时对象

**移动端**
- 你拿到的是“文件路径”（或字节流），需要自己决定：
  - 是否复制到 App 私有目录
  - 是否压缩
  - 是否持久化路径

本章采取最稳妥策略：
- 选图/拍照后，把图片复制到 App 私有目录（Documents）
- SQLite 只存“复制后的路径”

---

## 11.3 安装依赖
```powershell
flutter pub add image_picker
flutter pub add path_provider
flutter pub add path
```

> 你前面已经装过 `path_provider` 可能会提示已存在。

同时确保第 10 章权限已完成：
- AndroidManifest 权限
- iOS Info.plist 用途说明

---

## 11.4 实战：给 Note 增加图片字段 + 数据库升级（不省略步骤）

#### 11.4.1 升级数据库 schema（从 v1 → v2）
你第 9 章的表结构没有 `image_path`。
现在我们：
- 数据库版本号从 1 改为 2
- 在 `onUpgrade` 做 `ALTER TABLE`

把 `lib/storage/app_database.dart` 改成（关键差异已包含在完整代码里）：

```dart
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:sqflite/sqflite.dart';

class AppDatabase {
  static const _dbName = 'notes.db';
  static const _dbVersion = 2;

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
  created_at INTEGER NOT NULL,
  image_path TEXT
);
''');
        await db.execute('CREATE INDEX idx_notes_created_at ON notes(created_at);');
      },
      onUpgrade: (db, oldVersion, newVersion) async {
        if (oldVersion < 2) {
          await db.execute('ALTER TABLE notes ADD COLUMN image_path TEXT;');
        }
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

#### 11.4.2 更新 Note 模型
更新 `lib/features/notes/note.dart`：

```dart
class Note {
  final String id;
  final String content;
  final DateTime createdAt;
  final String? imagePath;

  const Note({
    required this.id,
    required this.content,
    required this.createdAt,
    this.imagePath,
  });

  Note copyWith({String? content, String? imagePath}) {
    return Note(
      id: id,
      content: content ?? this.content,
      createdAt: createdAt,
      imagePath: imagePath ?? this.imagePath,
    );
  }

  Map<String, Object?> toDb() {
    return {
      'id': id,
      'content': content,
      'created_at': createdAt.millisecondsSinceEpoch,
      'image_path': imagePath,
    };
  }

  static Note fromDb(Map<String, Object?> row) {
    return Note(
      id: row['id'] as String,
      content: row['content'] as String,
      createdAt: DateTime.fromMillisecondsSinceEpoch(row['created_at'] as int),
      imagePath: row['image_path'] as String?,
    );
  }
}
```

#### 11.4.3 Repository 与 Provider 支持图片字段
- `upsert()` 已经用 `toDb()`，无需额外改动
- 更新 `add()` 方法支持传入 `imagePath`

在 `notes_provider.dart` 的 `add()` 改成：
```dart
Future<void> add(String content, {String? imagePath}) async {
  final repo = ref.read(notesRepositoryProvider);
  final note = Note(
    id: DateTime.now().microsecondsSinceEpoch.toString(),
    content: content,
    createdAt: DateTime.now(),
    imagePath: imagePath,
  );
  await repo.upsert(note);
  state = AsyncData(await repo.list());
}
```

---

## 11.5 图片选择/拍照：`image_picker` + 复制到私有目录

#### 11.5.1 新建图片工具：`lib/media/media_service.dart`
```dart
import 'dart:io';

import 'package:image_picker/image_picker.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

class MediaService {
  final ImagePicker _picker;

  MediaService({ImagePicker? picker}) : _picker = picker ?? ImagePicker();

  Future<String?> pickFromGallery() async {
    final file = await _picker.pickImage(source: ImageSource.gallery);
    if (file == null) return null;
    return _copyToAppDir(File(file.path));
  }

  Future<String?> takePhoto() async {
    final file = await _picker.pickImage(source: ImageSource.camera);
    if (file == null) return null;
    return _copyToAppDir(File(file.path));
  }

  Future<String> _copyToAppDir(File original) async {
    final dir = await getApplicationDocumentsDirectory();
    final ext = p.extension(original.path);
    final filename = 'note_${DateTime.now().microsecondsSinceEpoch}$ext';
    final targetPath = p.join(dir.path, 'images', filename);

    final imagesDir = Directory(p.join(dir.path, 'images'));
    if (!await imagesDir.exists()) {
      await imagesDir.create(recursive: true);
    }

    final copied = await original.copy(targetPath);
    return copied.path;
  }
}
```

---

## 11.6 实战：在“编辑页”加图片（选择/拍照/预览/保存）
假设你第 8 章已经有 `NoteEditPage`。
我们做：
- 顶部显示图片预览（有则显示）
- 两个按钮：拍照 / 从相册选
- 保存时把 `imagePath` 传给 provider

关键思路：
- UI 只负责拿到路径与预览
- 真正的数据持久化仍在 provider/repository

示例（只给出核心改造片段，保证你能一步步改）：

1) 在 `NoteEditPage` State 里增加字段：
```dart
String? _imagePath;
```

2) initState 预填编辑时的图片路径：
```dart
if (widget.id != null) {
  final note = await ref.read(notesRepositoryProvider).findById(widget.id!);
  if (note != null) {
    _contentController.text = note.content;
    _imagePath = note.imagePath;
  }
}
```
> 注意：如果你要 `await`，需要把 initState 改成启动一个 async 方法（不要直接把 initState 标 async）。

3) 页面里加预览：
```dart
if (_imagePath != null) ...[
  ClipRRect(
    borderRadius: BorderRadius.circular(12),
    child: Image.file(
      File(_imagePath!),
      height: 180,
      width: double.infinity,
      fit: BoxFit.cover,
    ),
  ),
  const SizedBox(height: 12),
]
```

4) 两个按钮：
```dart
final media = MediaService();

Row(
  children: [
    Expanded(
      child: OutlinedButton.icon(
        onPressed: () async {
          final path = await media.takePhoto();
          if (path == null) return;
          setState(() => _imagePath = path);
        },
        icon: const Icon(Icons.photo_camera_outlined),
        label: const Text('拍照'),
      ),
    ),
    const SizedBox(width: 12),
    Expanded(
      child: OutlinedButton.icon(
        onPressed: () async {
          final path = await media.pickFromGallery();
          if (path == null) return;
          setState(() => _imagePath = path);
        },
        icon: const Icon(Icons.photo_library_outlined),
        label: const Text('相册'),
      ),
    ),
  ],
),
```

5) 保存时传入 imagePath：
```dart
if (widget.id == null) {
  await ref.read(notesProvider.notifier).add(content, imagePath: _imagePath);
} else {
  await ref.read(notesProvider.notifier).updateContent(id: widget.id!, content: content);
  // 这里建议你把 update 扩展成同时更新 imagePath（作为练习）
}
```

---

## 11.7 实战小练习（必须做）

#### 练习 A：编辑页支持“删除图片”
- 图片预览右上角加一个删除按钮（Stack + Positioned）
- 点击后 `_imagePath = null`
- 保存后数据库字段也应变为 null

#### 练习 B：图片压缩（可选进阶）
- 选择/拍照后把图片压缩再保存（提示：可以用 `image` 或 `flutter_image_compress`）
- 验收：图片文件大小明显下降

---

## 11.8 常见坑
- 权限：没做第 10 章配置会直接失败
- 路径失效：只保存临时路径可能在重启后失效 → 本章已通过“复制到私有目录”规避
- 大图内存：直接展示超大图片可能卡顿 → 生产建议做压缩/缩略图
- iOS “照片权限 limited”：需要兼容（`isLimited` 也算可用）

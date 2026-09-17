---
title: 第九章 SQLite、数据迁移与本地存储
---

# 第九章：SQLite、数据迁移与本地存储

本章让内存 demo 变成可使用的离线 App。关掉再打开仍有数据，只是第一步；后续版本升级也必须保留数据。

## 9.1 根据数据性质选存储

| 内容 | 选择 | 不适合承担什么 |
| --- | --- | --- |
| 主题、引导页已读标记 | shared_preferences | 大量业务记录、可靠事务 |
| 笔记、标签与查询索引 | SQLite / sqflite | 大图片二进制堆积 |
| 图片、导出文件 | 应用私有文件目录 | 长期依赖 picker 缓存路径 |
| token 等敏感小值 | 平台安全存储插件 | 放进去就免除所有安全设计 |
| 可丢弃的远程响应 | 带有效期的缓存 | 作为唯一业务事实 |

只安装当前功能需要的依赖。整合基线使用 sqflite 与 path，不为离线 App 安装安全存储或登录库。sqflite 的平台支持与 Web/桌面适配不同，本章以 Android/iOS 为目标。

## 9.2 稳定模型与数据库边界

主线模型固定为 `id`、`content`、`updatedAt`。id 使用 UUID，不用列表下标；时间保存 UTC 毫秒值，显示时转本地。后续图片字段使用相对文件名 `imageName`，避免持久化某次安装容器的绝对路径。

```sql
CREATE TABLE notes (
  id TEXT PRIMARY KEY,
  content TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);
```

Repository 暴露 list / upsert / deleteById；UI 不认识表名与 SQL。模型到数据库 Map 的转换显式完成；`DateTime` 不直接作为 sqflite 字段值。

## 9.3 打开一次数据库，注入应用

```dart
// 局部初始化示例，需导入 sqflite 与 package:path/path.dart as p
final database = await openDatabase(
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
```

在启动阶段调用插件前先执行 `WidgetsFlutterBinding.ensureInitialized()`。初始化失败应显示可重试的启动错误页，不要永远白屏；第 17 章提供实现。

连接由应用根部持有，页面只使用注入的 repository。不要每次 build 打开数据库，也不要某个编辑页 dispose 时把所有页面共享的连接关闭。

## 9.4 写入与事务

不要拼接用户输入作为 SQL。下面展示“有则更新，无则插入”的事务，避免依赖 `replace` 隐含的删除再插入语义：

```dart
Future<void> upsert(Database db, Note note) async {
  final row = <String, Object?>{
    'id': note.id,
    'content': note.content,
    'updated_at': note.updatedAt.toUtc().millisecondsSinceEpoch,
  };
  await db.transaction((txn) async {
    final changed = await txn.update(
      'notes', row, where: 'id = ?', whereArgs: [note.id],
    );
    if (changed == 0) await txn.insert('notes', row);
  });
}
```

事务回调内使用 txn，不要混用外面的 db 再等待它，否则可能发生锁等待。所有语句完成后事务才成功；有异常时整体回滚。

示例在数据库写入成功后更新内存快照，避免“已写成功，但第二次读失败，于是页面误报保存失败”的不确定状态。这个策略要求所有写操作通过同一 controller；存在外部写入时，改用数据库监听或明确刷新协议。

## 9.5 迁移：同时照顾老用户和新用户

图片扩展把 schema 升为 2：

```dart
// 替换 openDatabase 的相应参数，不是额外再打开一个数据库
version: 2,
onCreate: (db, version) async {
  await db.execute('''
    CREATE TABLE notes (
      id TEXT PRIMARY KEY,
      content TEXT NOT NULL,
      updated_at INTEGER NOT NULL,
      image_name TEXT
    )
  ''');
},
onUpgrade: (db, oldVersion, newVersion) async {
  if (oldVersion < 2) {
    await db.execute('ALTER TABLE notes ADD COLUMN image_name TEXT');
  }
},
```

首次安装直接创建最新 schema；旧版本通过逐级条件迁移。只写 onUpgrade 忘记 onCreate，会导致新用户缺字段；只改 onCreate，老用户则永远拿不到新字段。

验证必须使用旧版真实数据库：写入记录 → 关闭 → 用新版打开 → 检查字段、数据和查询。再验证新安装。卸载重装会删掉迁移证据，不能作为修复线上升级问题的方法。

## 9.6 缓存、备份与清除不是一回事

清缓存只能删除可再生数据，不能删用户离线笔记和原始图片。“退出登录”是否删除本地业务数据、系统备份是否包含图片、卸载后能否恢复，都要明确告知。

缓存 key 至少考虑账号、查询条件和接口版本，定义 TTL 与失效规则。敏感内容不要无期限存入公共路径。SQLite 默认不等于加密数据库；确有静态加密需求时评估独立方案与密钥管理。

## 9.7 本章交付

新增、编辑、删除后强制关闭 App 再打开；确认结果落盘。模拟写入失败时草稿保留；用旧 schema 数据测试升级。让 AI 提交迁移代码时必须一起给出“新安装”和“从旧版本升级”两条验证步骤。

参考 [sqflite](https://pub.dev/packages/sqflite)。完整 repository 见 [第 17 章](./17-integrated-notes-app.md)。

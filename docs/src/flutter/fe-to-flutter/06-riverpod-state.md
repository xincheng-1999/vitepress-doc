---
title: 第六章 状态归属与 Riverpod 数据流
---

# 第六章：状态归属与 Riverpod 数据流

状态库并不会自动让代码有条理。你首先要回答：谁拥有数据、谁能改、何时失效、谁负责失败后的恢复。本文使用 Riverpod 3 的非代码生成 API，避免把生成器配置当成入门前提。

## 6.1 把状态放到正确的位置

| 数据 | 所有者 | 原因 |
| --- | --- | --- |
| TextEditingController、焦点 | 编辑页 State | 与页面输入生命周期一致 |
| 尚未提交的草稿、saving、保存错误 | 编辑页 State | 不应每打一个字就污染业务实体 |
| 已保存笔记列表 | NotesController/provider | 列表和编辑页共享同一事实 |
| 数据库连接与读写 | repository | 与 UI 解耦，便于替换测试 |
| 路由地址中的 id | router | 决定当前资源身份 |
| 主题偏好 | 应用级 provider + 简单存储 | 跨页面、跨重启 |

如果使用 setState 就足够，不需要为了“工程化”把焦点和每个按钮状态搬进全局 provider。反过来，列表页和编辑页不能各自持有一份可独立修改的业务列表。

## 6.2 最小数据流

```text
用户点击保存
  -> 编辑页校验草稿并进入 saving
  -> ref.read(notesProvider.notifier).save(note)
  -> NotesRepository.upsert(note)
  -> 写入成功后发布新的列表状态
  -> 所有 watch 列表的 UI 更新
  -> 当前编辑页返回

写入失败
  -> 异常返回编辑页
  -> 保存错误就地显示，草稿保留，允许重试
```

AsyncValue 描述列表的读取状态；它不应承包所有命令的状态。保存错误与首次加载失败影响的 UI 不同，因此示例把保存错误留在编辑页，而不是把整个列表覆盖成 AsyncError。

## 6.3 Provider 三种使用方式

- `ref.watch(provider)`：声明响应式依赖；值变化会触发重新计算或 rebuild。
- `ref.read(provider.notifier)`：在按钮等事件中调用命令，不建立 UI 订阅。
- `ref.listen(provider, ...)`：处理状态变化引起的副作用，例如单次通知；要避免重复提示。

ProviderScope 在应用根部提供容器。测试通过覆盖 repositoryProvider 注入 fake，让测试不碰真实磁盘。

下面是接口与 provider 的局部骨架，完整实现和 import 在第 17 章：

```dart
abstract interface class NotesRepository {
  Future<List<Note>> list();
  Future<void> upsert(Note note);
  Future<void> deleteById(String id);
}

final repositoryProvider = Provider<NotesRepository>(
  (ref) => throw UnimplementedError('由入口注入 repository'),
);

final notesProvider = AsyncNotifierProvider<NotesController, List<Note>>(
  NotesController.new,
  retry: (retryCount, error) => null,
);

class NotesController extends AsyncNotifier<List<Note>> {
  @override
  Future<List<Note>> build() => ref.watch(repositoryProvider).list();
}
```

这里关闭 Riverpod 3 对失败 provider 的自动重试，以便本地数据库读取失败时立即显示错误，让用户主动重试。需要网络重试时单独设计次数、退避和可重试错误，不沿用默认行为后误以为“只请求了一次”。

## 6.4 明确渲染所有分支

```dart
// ConsumerWidget.build 中的局部示例
final notes = ref.watch(notesProvider);
return notes.when(
  loading: () => const Center(child: CircularProgressIndicator()),
  error: (error, stackTrace) => Center(
    child: FilledButton(
      onPressed: () => ref.invalidate(notesProvider),
      child: const Text('读取失败，点击重试'),
    ),
  ),
  data: (items) => items.isEmpty
      ? const Center(child: Text('还没有笔记'))
      : NotesList(items: items),
);
```

`NotesList` 表示你自己的列表组件。空列表是成功加载后的业务结果，不是 loading，更不是 error。刷新已有数据时可以保留旧内容并显示刷新状态，首次加载和后台刷新不必展示同一个整屏 spinner。

## 6.5 不可变更新与并发边界

`state.value?.add(note)` 是危险写法：原集合被原地修改，旧状态也被污染，订阅判断可能无法表达预期变化。构造新集合并使用 `List.unmodifiable` 暴露快照。

保存策略也要明确：

- 悲观更新：数据库成功后更新列表。本教程采用，失败恢复最直接。
- 乐观更新：先更新 UI，失败后回滚。需要处理后续操作、版本冲突和撤销，不只是 catch 里塞回旧数组。

多个异步写入不能各自读取旧列表后同时覆盖。第 17 章将命令串行化；一个命令失败不阻塞后续命令，且只在持久化成功后发布快照。这个策略适用于单进程、单 repository 写入口的练习应用；外部数据库监听、多设备同步要重新定义一致性机制。

## 6.6 生命周期与缓存不是一回事

autoDispose provider 在不再被监听后可释放状态，适合页面级请求；应用级列表可保持存活。依赖变化也可能导致重新计算。需要的资源使用 `ref.onDispose` 清理，异步完成后检查适用版本的 `ref.mounted` 或取消机制。

Riverpod 3 中一些旧 API 被放入 legacy 入口。不要把不同版本的 `StateNotifierProvider`、生成代码、手写 Notifier 混在一起。本文选择 AsyncNotifier；这只是教学基线，不要求把成熟项目全部迁移。

用 `select` 缩小订阅前先确认有性能需求。例如单行只订阅该 Note，但如果你的选择函数每次返回全新的可变集合，仍可能频繁通知。

## 6.7 本章交付

要求 AI 标注项目中每份状态的唯一所有者，再实现“保存失败保留草稿”。用 fake repository 人为让 upsert 抛错，验证列表没变、编辑页没退出、第二次提交仍能成功。

能解释这条失败链路，比会背五种状态管理库更接近独立开发。下一步阅读 [表单](./08-forms-validation.md) 和 [存储](./09-storage-cache.md)，或直接运行 [整合示例](./17-integrated-notes-app.md)。

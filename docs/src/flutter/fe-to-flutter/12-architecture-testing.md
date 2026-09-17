---
title: 第十二章 架构、测试与 AI 修改验收
---

# 第十二章：架构、测试与 AI 修改验收

有 AI 帮忙后，代码量很容易增长，判断代码是否值得留下反而更重要。架构的目标是让一项业务修改可以被理解、替换和验证，不是增加目录层级。

## 12.1 从一条数据流拆职责

```text
页面：渲染、输入、导航、临时草稿
  -> Controller：业务校验、命令顺序、共享状态
  -> Repository 接口：数据操作契约
  -> SQLite / HTTP 实现：外部系统细节
```

第 17 章为方便复制保留五个 lib 文件。应用扩大后按 feature 拆分即可：

```text
lib/
  app/                  # 启动、路由、主题
  features/notes/
    note.dart           # 模型、领域规则
    notes_repository.dart
    data/               # SQLite/HTTP 实现与映射
    application/        # provider、controller
    presentation/       # 页面、组件
```

不是每个简单调用都要额外生成 use case、DTO、mapper、service、manager。判断标准是是否隔离了真实变化：数据库替换能否不改表单？测试能否不启动真机？一个功能是否有两份互相冲突的状态？

## 12.2 给模型一个稳定的项目契约

把以下事实写进你实际 App 的项目说明，再让模型每轮先读取：

```text
项目 SDK 与依赖：以已提交的版本记录、pubspec.yaml、pubspec.lock 为准。
数据流：页面 -> controller -> repository；UI 不直接写 SQL。
状态：草稿归编辑页，已保存列表归 notesProvider。
规则：trim 后非空；失败保留草稿；成功持久化后才更新列表并导航。
代码：不在 build 做 I/O；异步返回后检查生命周期与结果时效。
验证：dart format、flutter analyze、flutter test；平台能力另做真机验收。
改动边界：本轮功能之外的重构、依赖升级需单独说明必要性。
```

这是工作约束，不是模型质量保证。仍需审阅实际 diff、运行命令、看结果。不要让模型通过放宽断言、吞异常或禁用分析器规则“让测试变绿”。

## 12.3 测试分层：每层证明不同的事

| 层次 | 工具与替身 | 适合证明 | 证明不了 |
| --- | --- | --- | --- |
| 模型/Controller | flutter_test + fake repository | 校验、排序、失败不更新、队列恢复 | SQLite 和插件真的工作 |
| Widget | testWidgets + ProviderScope override | 点击、校验、失败提示、导航、草稿 | 系统键盘和原生权限弹窗 |
| 数据库集成 | 实际 SQLite 引擎、临时数据库 | SQL、事务、旧版本迁移 | 所有设备差异 |
| 设备端流程 | integration_test / 手工真机 | 插件、冷启动、覆盖升级、系统交互 | 所有未来网络与机型 |

不要测“容器不为空”来代表状态管理正确。对每条业务不变量，用一个反例验证测试能失败。

## 12.4 可替换的数据层是测试入口

```dart
final container = ProviderContainer(
  overrides: [repositoryProvider.overrideWithValue(fakeRepository)],
);
addTearDown(container.dispose);
await container.read(notesProvider.future);
await container.read(notesProvider.notifier).save(note);
final notes = await container.read(notesProvider.future);
expect(notes.single.content, '预期内容');
```

这是测试函数中的局部结构，完整 imports、fake 和用例见第 17 章。fake 必须实现同一 NotesRepository 接口。用内存 List 写一个无关类再说“以后自行覆盖”，并没有建立真正的测试接缝。

至少覆盖：同 id 编辑不新增第二条、失败不发布假数据、失败后可以继续写、两次连续提交不丢数据、删除后 repository 与 UI 一致。用 Completer 控制 Future 完成顺序，可以稳定复现慢请求，不依赖实际等几秒。

## 12.5 Widget 测试验证用户能看到的结果

保存失败的测试应包含完整动作：进入编辑页 → 输入 → 点击保存 → fake 抛错 → 仍在编辑页 → 文本没丢 → 重试 → 返回列表显示新内容。仅断言 `_saving == false` 会与实现强耦合，也不能证明用户任务成功。

`pump()` 推进一帧，`pumpAndSettle()` 等待调度的帧稳定；未完成的加载动画可能让后者超时。对 loading 状态使用可控 Future 和有限 pump，不通过增大超时时间掩盖状态永不结束的问题。

## 12.6 调试时先还原事实

1. 记录最小复现步骤、设备/系统、构建模式与第一条有效异常。
2. 判断属于布局、状态、异步、数据契约还是原生插件。
3. 缩小输入或替换外部依赖，保留能触发问题的最小范围。
4. 修正根因，再运行原复现及相关回归。

适合发给模型的材料是报错、堆栈、有关文件和实际版本。截图适合布局问题；一次崩溃通常还需要日志。“帮我修一下”容易让模型修改无关代码。

线上错误应保留版本、平台、堆栈、请求关联信息，并去除 token、正文等敏感数据。用户看到的是可操作的文案，开发者保留的是定位证据；不要把底层异常原文直接显示给用户，也不要完全丢弃它。

## 12.7 一轮 AI 修改的验收

```sh
dart format --output=none --set-exit-if-changed lib test
flutter analyze
flutter test
```

读 diff 时优先检查：有没有新的状态副本？build 中有没有副作用？失败有没有被吞掉？有没有引入当前版本不存在的 API？新增插件的原生配置是否匹配目标平台？

格式、静态检查和测试通过后，按功能做设备验收。第 17 章的测试只覆盖 Dart 与 Widget 层，SQLite 插件、文件权限、键盘、签名与上架不能由这些测试代替。

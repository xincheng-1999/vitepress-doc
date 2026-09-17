---
title: 第三章 Widget、生命周期与布局约束
---

# 第三章：Widget、生命周期与布局约束

AI 很容易生成一个“长得像设计图”的页面。你的判断力体现在：内容变长、键盘弹出、列表重排以后，它为什么还能正确工作？本章掌握三件事：UI 如何更新、状态如何保留、尺寸如何决定。

## 3.1 Widget 不等于 DOM 节点

| 对象 | 作用 | 排错时关心什么 |
| --- | --- | --- |
| Widget | 不可变的 UI 配置，创建通常较轻 | 构造参数是否表达当前状态 |
| Element | 挂在树中的实例，连接 Widget 与生命周期 | 状态是否被保留、Context 在哪一层 |
| RenderObject | 部分 Widget 对应的布局和绘制对象 | 约束、尺寸、绘制与命中测试 |

`build` 返回新的 Widget 描述，不意味着销毁整个屏幕。框架通过位置、类型和 key 匹配已有 Element，再更新需要变化的部分。`StatelessWidget` 也会 rebuild；`StatefulWidget` 本身仍不可变，可变数据放在对应 State 中。

`BuildContext` 是树中一个位置的句柄。`Theme.of(context)`、`Navigator.of(context)` 从这个位置向祖先查找；刚在返回值里新建的 Provider / Scaffold 不会自动成为当前 context 的祖先。需要时拆一个子 Widget 或使用 Builder 获取新的 context。

## 3.2 生命周期：资源要有明确的所有者

| 时机 | 应该做什么 | 不该做什么 |
| --- | --- | --- |
| `initState` | 创建 controller、一次性本地初始化 | 每次父组件更新都指望它重跑 |
| `didChangeDependencies` | 响应依赖的 InheritedWidget 变化 | 无条件反复发相同请求 |
| `didUpdateWidget` | 同一 State 收到新参数，处理 id 等变化 | 假设新参数会重建 State |
| `build` | 根据当前状态描述 UI | 发请求、写数据库、覆盖输入内容 |
| `dispose` | 释放 controller、focus、timer、subscription | 继续调用 setState |

局部示例，展示一个 State 对输入控制器的所有权：

```dart
class SearchBox extends StatefulWidget {
  const SearchBox({super.key});

  @override
  State<SearchBox> createState() => _SearchBoxState();
}

class _SearchBoxState extends State<SearchBox> {
  final _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return TextField(controller: _controller);
  }
}
```

上例放入导入 `material.dart` 的文件即可使用。`setState(() { ... })` 应同步修改本地状态，异步操作放在外面；它只通知框架需要更新，不会替你持久化数据。

页面生命周期也不等于应用前后台生命周期。push 新页面时，下面的页面可能仍 mounted；应用进入后台时，页面也可能还在。摄像头预览等能力应结合 `AppLifecycleListener` 与路由可见性处理暂停、恢复和释放，不能只等 dispose。

## 3.3 先看约束，再选 Widget

Flutter 布局的基本规则是：**父级下传约束，子级选择满足约束的尺寸，父级决定子级位置**。约束包括最小/最大宽高；某个方向可以是无界的。

你写 `width: 300` 只是提出希望，如果父级给定宽度 200，它不能强行获得 300。`Container` 也不是“默认占满、可随意套 CSS”的 div。

常见的「标题 + 可滚动列表」，以下是 build 返回值中的局部结构：

```dart
Column(
  crossAxisAlignment: CrossAxisAlignment.stretch,
  children: [
    const Padding(
      padding: EdgeInsets.all(16),
      child: Text('我的笔记'),
    ),
    Expanded(
      child: ListView.builder(
        itemCount: notes.length,
        itemBuilder: (context, index) => ListTile(
          key: ValueKey(notes[index].id),
          title: Text(notes[index].content),
        ),
      ),
    ),
  ],
)
```

这里假定 Column 位于有界高度的 Scaffold body 中。Column 先放标题，Expanded 把剩余高度分给列表，列表才知道自己的 viewport 有多高。

若给整个 Column 再套垂直 `SingleChildScrollView`，它会获得无界高度，此时 Expanded 没有“有限剩余空间”可分。不是再多套一个 Expanded 就能修好。

## 3.4 根据错误还原约束链

| 现象 | 优先检查 | 通常的修复方向 |
| --- | --- | --- |
| `Vertical viewport was given unbounded height` | ListView 的父级是否约束高度 | 放在有界 Column 的 Expanded 内，或统一滚动容器 |
| `RenderFlex overflowed` 黄色条 | Row/Column 子项总尺寸、键盘、大字体 | 文本参与 Flexible/Expanded，内容可滚动 |
| `non-zero flex ... unbounded` | Expanded 是否位于无界主轴 | 去掉该处 flex，重组滚动结构 |
| `Incorrect use of ParentDataWidget` | Expanded/Positioned 的父级 | Expanded 配合 Flex，Positioned 配合 Stack |
| 文字始终一行挤出屏幕 | Row 中 Text 是否获得有限宽度 | 给文本一侧 Expanded，再定 maxLines |

让 AI 修布局时提供完整的祖先链和报错，不要只给最里面的 Text。要求它先指出哪个方向在哪里变成无界，再提出修改。

`shrinkWrap: true` 可以让某些小列表按内容量参与布局，但有额外布局成本，不是长列表嵌套滚动的通用修复。整个页面一起滚动时，优先 `CustomScrollView` + `SliverToBoxAdapter` + `SliverList`；小型表单可用 SingleChildScrollView。

## 3.5 Key 决定身份，不是用来消警告

笔记列表用 `ValueKey(note.id)`，不要用位置 index。删除第一条以后，第二条仍是同一条笔记，其局部状态应该跟着业务 id，而不是跟着行号。

`UniqueKey()` 每次重新创建会主动破坏身份，可能导致输入、动画、滚动状态丢失。GlobalKey 用于少量确实需要跨树访问状态的场景，例如 Form 校验，不要给所有组件分配 GlobalKey。

编辑页还可以用 `ValueKey(note.id)` 明确“不同笔记是不同编辑状态”。同一笔记后台更新是否覆盖草稿，则是业务决策，不由 key 自动解决。

## 3.6 怎么判断需要优化

先在真机 profile 模式复现，再打开 DevTools 看帧时间、Widget rebuild 和 CPU。重建不是重绘，重绘也不一定是瓶颈。60Hz 设备每帧约 16.7ms，120Hz 约 8.3ms；大图片解码、同步 JSON 解析和长列表构建都可能占预算。

优先限制订阅范围、使用懒构建列表、避免在 build 做计算和 I/O。`const`、`RepaintBoundary`、缓存都应针对具体问题，不能让 AI 批量加上后就宣布“优化完成”。

## 3.7 本章交付

做一个含标题、搜索框、100 条笔记的页面。测试长文本、小屏横屏、大字体、删除第一行后的状态。刻意移除列表外的 Expanded，观察错误并解释原因，再修复。

AI 任务：“只修复约束来源，说明为什么这个节点需要有限高度，不新增第三方布局库。”你能复述原因，才算完成。本章对应 [官方约束说明](https://docs.flutter.dev/ui/layout/constraints)。

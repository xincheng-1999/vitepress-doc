---
title: 第五章 路由、返回栈与页面身份
---

# 第五章：路由、返回栈与页面身份

移动端导航除了“显示哪个页面”，还涉及系统返回、手势返回、冷启动链接、弹窗以及页面是否仍然存活。先设计路由语义，再让 AI 生成 go_router 配置。

## 5.1 随手记的路由契约

| 路径 | 含义 | 参数来源 |
| --- | --- | --- |
| `/` | 笔记列表 | repository |
| `/notes/new` | 新建笔记 | 空草稿 |
| `/notes/:id/edit` | 编辑指定笔记 | 用 id 从数据层读取 |

路径里放稳定 id，不放列表下标。不要把整个 Note 只塞进 `extra` 后假定它永远存在：外部链接、进程重建等入口未必带有那个内存对象。详情加载时要区分 loading、读取失败和“确实不存在”。

本文以 `go_router: 16.2.4` 为整合基线。已有项目先查看实际版本，避免混用旧版 `params` 等 API。配置片段如下，页面类在第 17 章提供：

```dart
final router = GoRouter(
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
```

`router` 必须有稳定生命周期，例如由应用 State 创建并在 dispose 释放。不要在 `build` 每次 `GoRouter(...)`；主题更新、父组件 rebuild 都可能打断你对导航状态的预期。

## 5.2 `push`、`go`、`pop` 的选择

- 从列表进入编辑：`context.push('/notes/$id/edit')`，保留返回前页的行为。
- 跳到某个顶层目的地：`context.go('/')`，按目标位置重建匹配的路由栈。
- 当前页面完成：`context.pop()`；如果支持直接冷启动到该页，先检查 `context.canPop()`，无上层时 `go('/')`。

不要把所有跳转一律改成 go。页面跳转成功并不能说明 Android 返回键还符合预期。

Navigator 仍用于局部弹窗、简单结果返回等场景。`await context.push<T>(...)` 可以等待路由结果，但业务数据的最终来源应是 repository/provider，不要同时维护“返回值里的列表”和“全局列表”两套事实。

## 5.3 异步后的 context 必须仍有效

```dart
// 局部事件处理，save 是应用自己的异步保存函数
await save();
if (!context.mounted) return;
if (context.canPop()) {
  context.pop();
} else {
  context.go('/');
}
```

用户等待期间可能退出页面。mounted 检查只证明这个 Context 还在树里，不证明这次结果仍是最新结果；“是否过期”需要请求序号、取消或 provider 生命周期控制，见第 7 章。

## 5.4 草稿与系统返回

有未保存内容时，应覆盖 AppBar 返回、Android 系统返回和平台手势，不能只给一个自定义返回按钮加确认框。现代 Flutter 使用 PopScope；旧 WillPopScope 与预测性返回的关系需要按 SDK 版本处理。

第 17 章采用容易解释的最低保证：草稿未改时可返回；变更后拦截离开，显示“保存或明确放弃”的提示，提供放弃按钮。产品可以升级为确认弹窗，但要避免二次弹出、保存成功后仍被拦截，以及在 build 中调用 pop。iOS 手势在 `canPop: false` 时的回调行为与 Android 不完全相同，因此仍要提供明确可见的放弃入口。

## 5.5 登录跳转与多标签页何时需要

主线是离线 App，不引入虚构登录。接入账号后，认证至少包含 unknown/loading、authenticated、unauthenticated 三态；在初始化未完成时就跳登录，会产生闪屏或错误重定向。

redirect 应根据状态计算目的地，避免在其中做重复网络请求；受保护目标要保留，登录后再恢复；重定向要有终止条件，防止循环。客户端路由拦截不替代服务端授权。

底部多标签若需要各自保留历史，可评估 `StatefulShellRoute.indexedStack`。先确定“切标签是否保留滚动和返回栈”，不要只因为教程提到了就把它加进单列表应用。

## 5.6 本章交付

从列表进入编辑后依次尝试：正常返回、输入后返回、保存后返回、直接打开不存在的 id、直接启动编辑地址。每种入口都要有明确结果。

让 AI 给路由表和返回行为写验收，不要只让它生成页面文件。参考 [go_router 文档](https://pub.dev/packages/go_router)。

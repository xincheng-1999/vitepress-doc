---
title: 第八章 表单、校验、输入与交互细节
---

# 第八章：表单、校验、输入与交互细节（移动端体验分水岭）

## 8.1 本章目标（验收标准）
完成后你需要能：
- 使用 `Form` + `TextFormField` 做校验
- 处理焦点、键盘、输入法“遮挡”问题
- 写出一个“新增/编辑笔记页”（替代弹窗），并能保存
- 了解常见的交互陷阱：重复提交、输入法 action、滚动冲突

---

## 8.2 核心概念：移动端输入不是一个 TextField 就完事
**Web 对比：**
- Web 表单：浏览器负责很多默认行为（Enter 提交、自动填充、滚动）
- 移动端：你需要显式控制焦点、键盘 action、滚动容器、提交节流

---

## 8.3 实战：新增/编辑页（Form 校验 + 键盘处理）
目标：把“弹窗新增”升级为“独立页面编辑”。

#### 8.3.1 路由新增：`/edit` 与 `/edit/:id`
- `/edit`：新建
- `/edit/:id`：编辑

#### 8.3.2 新建页面文件
- `lib/pages/note_edit_page.dart`

#### 8.3.3 `lib/pages/note_edit_page.dart`（完整可运行）
> 本示例依赖你第 6 章的 `notesProvider`。

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../features/notes/notes_provider.dart';

class NoteEditPage extends ConsumerStatefulWidget {
  final String? id;

  const NoteEditPage({
    super.key,
    this.id,
  });

  @override
  ConsumerState<NoteEditPage> createState() => _NoteEditPageState();
}

class _NoteEditPageState extends ConsumerState<NoteEditPage> {
  final _formKey = GlobalKey<FormState>();
  final _contentController = TextEditingController();

  bool _saving = false;

  @override
  void initState() {
    super.initState();
    // 预填编辑内容
    if (widget.id != null) {
      final note = ref.read(notesProvider.notifier).findById(widget.id!);
      if (note != null) {
        _contentController.text = note.content;
      }
    }
  }

  @override
  void dispose() {
    _contentController.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_saving) return;

    final ok = _formKey.currentState?.validate() ?? false;
    if (!ok) return;

    FocusScope.of(context).unfocus();

    setState(() => _saving = true);
    try {
      final content = _contentController.text.trim();
      final notifier = ref.read(notesProvider.notifier);

      if (widget.id == null) {
        notifier.add(content);
      } else {
        notifier.updateContent(id: widget.id!, content: content);
      }

      if (mounted) Navigator.of(context).pop();
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final isEdit = widget.id != null;

    return Scaffold(
      appBar: AppBar(
        title: Text(isEdit ? '编辑笔记' : '新建笔记'),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(12),
          child: Form(
            key: _formKey,
            child: Column(
              children: [
                TextFormField(
                  controller: _contentController,
                  maxLines: 8,
                  textInputAction: TextInputAction.newline,
                  decoration: const InputDecoration(
                    labelText: '内容',
                    border: OutlineInputBorder(),
                    hintText: '写点什么...',
                  ),
                  validator: (v) {
                    final text = (v ?? '').trim();
                    if (text.isEmpty) return '内容不能为空';
                    if (text.length < 3) return '至少输入 3 个字符';
                    return null;
                  },
                ),
                const SizedBox(height: 12),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton(
                    onPressed: _saving ? null : _save,
                    child: Text(_saving ? '保存中...' : '保存'),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
```

#### 8.3.4 挂路由（关键步骤）
在你的 go_router 路由表里新增（示例）：
```dart
GoRoute(
  path: 'edit',
  builder: (context, state) => const NoteEditPage(),
),
GoRoute(
  path: 'edit/:id',
  builder: (context, state) {
    final id = state.pathParameters['id']!;
    return NoteEditPage(id: id);
  },
),
```

#### 8.3.5 首页入口改造
- 右下角 `+`：跳到 `/edit`
- 点击列表项：跳到 `/edit/:id`（或你也可保留详情页，再从详情页进入编辑）

---

## 8.4 输入体验关键点（必须掌握）

#### 8.4.1 防重复提交
- 用 `_saving` 状态禁用按钮
- 网络/写库时尤其重要

#### 8.4.2 键盘遮挡与滚动
- 表单页用 `SingleChildScrollView` 包住
- 或用 `ListView` 作为表单容器

#### 8.4.3 焦点控制
- 保存前 `unfocus()` 收起键盘
- 多字段时用 `FocusNode` 控制“下一项”

---

## 8.5 实战小练习（必须做）

#### 练习 A：加标题字段
- 新增 `title` 输入框（单行）
- 校验：1~30 字
- 保存后列表展示标题 + 摘要

#### 练习 B：输入法 action 优化
- 标题输入框的 `textInputAction: TextInputAction.next`
- 点击 next 自动跳到内容输入框（提示：FocusNode + requestFocus）

---

## 8.6 常见坑
- `TextEditingController` 忘记 dispose → 内存泄漏
- 表单页不滚动 → 键盘弹出后内容被遮挡
- 校验写在 onChanged 里 → 频繁 setState 导致卡顿；优先 Form validator

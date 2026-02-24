---
title: 第三章 Widget 心智与 Flutter 布局系统
---

# 第三章：Widget 心智与 Flutter 布局系统（写 UI 的基本功）

## 3.1 本章目标（验收标准）
完成后你需要能：
- 解释 Flutter 布局的“约束传递”模型（Constraints）
- 熟练使用 Row/Column/Flex/Expanded/Stack/Align/Padding
- 能写出常见页面结构：列表页、详情页、表单页
- 能从 Web 的 Flex 布局迁移到 Flutter 的等价写法

---

## 3.2 核心概念：Flutter 布局是“约束（Constraints）驱动”
Flutter 布局的简化规则：
1) 父组件给子组件约束（最小/最大宽高）
2) 子组件在约束范围内决定自己的尺寸
3) 父组件再根据子组件尺寸决定自己的布局

**一句话：不是子元素想多大就多大，先看父约束。**

**Web 对比：**
- Web 有 `min-width/max-width`、`flex-basis`、`overflow` 等一套规则
- Flutter 把规则更“显式化”，很多问题本质是“你给了无限约束/或强制约束”

---

## 3.3 Widget 三件套：Widget / Element / RenderObject（只需要理解到能排错）
- Widget：不可变配置（像 React/Vue 的“描述”）
- Element：Widget 的运行时实例 + 生命周期关联
- RenderObject：真正做 layout/paint 的对象

你写 UI 时主要在写 Widget；遇到布局异常/性能问题时，理解 RenderObject 有助于定位。

---

## 3.4 常用布局组件（必须掌握）

#### 3.4.1 `Padding` / `SizedBox` / `Container`
- `Padding`：只负责内边距
- `SizedBox`：给固定尺寸/占位
- `Container`：万能盒子（但别滥用）

#### 3.4.2 `Row` / `Column` / `Flex`
- `mainAxisAlignment`：主轴对齐
- `crossAxisAlignment`：交叉轴对齐

**Web 对比（Flex）：**
- `justify-content` ≈ `mainAxisAlignment`
- `align-items` ≈ `crossAxisAlignment`

#### 3.4.3 `Expanded` / `Flexible`
- `Expanded`：强制占满剩余空间（类似 `flex: 1` 且必须占满）
- `Flexible`：可伸缩但不一定占满

#### 3.4.4 `Stack` / `Positioned` / `Align`
- 做浮层、角标、叠放结构（类似绝对定位）

---

## 3.5 列表与滚动：`ListView`/`SingleChildScrollView` 的正确打开方式

#### 3.5.1 `ListView.builder`（长列表优先）
- 惰性构建，适合大数据

#### 3.5.2 嵌套滚动常见雷区
- `Column` + `ListView` 直接嵌会报“无限高度”
- 解决：
  - 外层用 `Expanded(child: ListView(...))`
  - 或用 `CustomScrollView`（进阶）

---

## 3.6 实战：把第 1 章首页“组件化”并加上空状态
目标：从“堆在一个文件里”升级到可维护结构：
- `Note` 模型仍可在同文件（先不拆太多）
- UI 拆出一个 `NoteListItem`，减少 build 里的复杂度

#### 3.6.1 替换 `lib/main.dart`（完整可运行）
> 你可以在第 1 章项目上直接替换。

```dart
import 'package:flutter/material.dart';

void main() {
  runApp(const NotesApp());
}

class NotesApp extends StatelessWidget {
  const NotesApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '随手记',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
        useMaterial3: true,
      ),
      home: const NotesHomePage(),
    );
  }
}

class NotesHomePage extends StatefulWidget {
  const NotesHomePage({super.key});

  @override
  State<NotesHomePage> createState() => _NotesHomePageState();
}

class _NotesHomePageState extends State<NotesHomePage> {
  final List<Note> _notes = <Note>[];
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
            textInputAction: TextInputAction.done,
            decoration: const InputDecoration(
              hintText: '写点什么...',
              border: OutlineInputBorder(),
            ),
            onSubmitted: (value) => Navigator.of(context).pop(value.trim()),
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

    setState(() {
      _notes.insert(
        0,
        Note(
          id: DateTime.now().microsecondsSinceEpoch.toString(),
          content: text,
          createdAt: DateTime.now(),
        ),
      );
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('随手记'),
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: _notes.isEmpty
              ? const _EmptyState()
              : ListView.separated(
                  itemCount: _notes.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 8),
                  itemBuilder: (context, index) {
                    final note = _notes[index];
                    return NoteListItem(
                      note: note,
                      onDelete: () {
                        setState(() {
                          _notes.removeAt(index);
                        });
                      },
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

class _EmptyState extends StatelessWidget {
  const _EmptyState();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: const [
          Icon(Icons.note_alt_outlined, size: 56),
          SizedBox(height: 12),
          Text('还没有笔记'),
          SizedBox(height: 6),
          Text('点右下角 + 新建一条'),
        ],
      ),
    );
  }
}

class NoteListItem extends StatelessWidget {
  final Note note;
  final VoidCallback onDelete;

  const NoteListItem({
    super.key,
    required this.note,
    required this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: ListTile(
          title: Text(
            note.content,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
          subtitle: Text(_formatTime(note.createdAt)),
          trailing: IconButton(
            tooltip: '删除',
            onPressed: onDelete,
            icon: const Icon(Icons.delete_outline),
          ),
        ),
      ),
    );
  }

  String _formatTime(DateTime dt) {
    final two = (int n) => n.toString().padLeft(2, '0');
    return '${dt.year}-${two(dt.month)}-${two(dt.day)} ${two(dt.hour)}:${two(dt.minute)}';
  }
}

class Note {
  final String id;
  final String content;
  final DateTime createdAt;

  const Note({
    required this.id,
    required this.content,
    required this.createdAt,
  });
}
```

---

## 3.7 实战小练习（必须做）

#### 练习 A：给列表项加“右上角角标”
需求：
- 使用 `Stack` 在卡片右上角叠一个小角标（例如“NEW”）
- 条件：创建时间在 1 分钟内才显示

提示：
- 卡片内容用 `Stack(children: [...])`
- 角标用 `Positioned(top: 8, right: 8, child: ...)`

#### 练习 B：实现“自适应布局”
需求：
- 横屏时列表项左侧显示图标，右侧是文本
- 竖屏时图标在上、文本在下

提示：用 `MediaQuery.of(context).orientation` 或 `LayoutBuilder`。

---

## 3.8 常见坑（布局必踩）
- `Column` 里直接放 `ListView`：报无限高度 → 用 `Expanded` 包住
- `Container` 滥用：能用 `Padding/SizedBox/DecoratedBox` 就别用万能盒子
- `Row` 里文本溢出：给文本外面加 `Expanded` 或设置 `maxLines/overflow`
- `Stack` 无尺寸：确保外层有约束（例如 `SizedBox` 或父容器有固定/可推导尺寸）

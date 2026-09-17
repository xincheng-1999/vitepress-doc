---
title: 第八章 表单、草稿与可靠提交
---

# 第八章：表单、草稿与可靠提交

编辑页面的核心不是一个 TextField，而是草稿从产生到提交的生命周期。最重要的不变量是：**写入失败保留草稿，成功之后才离开页面**。

## 8.1 已保存实体与草稿分开

已保存 Note 来自 provider；进入编辑页时，用它初始化 controller 一次。用户输入只改变草稿，保存成功才更新业务数据。

不要在 build 执行 `controller.text = note.content`。provider 更新、主题切换或键盘变化都可能 rebuild，结果是光标跳动、输入被覆盖，甚至看似随机丢字。已有 id 的数据先由 loader 读取，再构造带明确 key 的编辑器；第 17 章给出完整写法。

同一笔记在编辑期间被外部修改怎么办？练习应用只允许单一页面写入；多人或多端场景要比较版本并提示冲突，不能悄悄覆盖草稿。

## 8.2 Controller、Form 与校验

局部结构，放入 StatefulWidget/ConsumerStatefulWidget 的 State：

```dart
final _formKey = GlobalKey<FormState>();
late final TextEditingController _controller;

@override
void initState() {
  super.initState();
  _controller = TextEditingController(text: widget.note?.content ?? '');
}

@override
void dispose() {
  _controller.dispose();
  super.dispose();
}
```

```dart
Form(
  key: _formKey,
  child: TextFormField(
    controller: _controller,
    minLines: 5,
    maxLines: 12,
    maxLength: 2000,
    decoration: const InputDecoration(labelText: '笔记内容'),
    validator: (value) => (value ?? '').trim().isEmpty ? '请输入内容' : null,
  ),
)
```

Form 校验负责即时反馈，领域层仍应检查非空和长度，不能假设所有调用都来自这个表单。中文输入法有组合输入过程，不要在 controller listener 中无条件改写 text，否则容易破坏 composing 范围；普通格式限制优先用适用的 formatter，并真机检查。

## 8.3 提交状态机

```text
editing --校验通过--> saving --成功--> 离开
   ^                    |
   +----失败并保留草稿----+
```

局部提交逻辑如下，`buildNote` 表示把草稿构造成 Note 的应用函数：

```dart
Future<void> submit() async {
  if (_saving || !_formKey.currentState!.validate()) return;
  final note = buildNote(_controller.text.trim());
  setState(() {
    _saving = true;
    _saveError = null;
  });
  try {
    await ref.read(notesProvider.notifier).save(note);
    if (!mounted) return;
    // 先解除 PopScope 对成功提交的拦截，再导航；完整代码见第 17 章。
    leaveAfterSave();
  } catch (error, stackTrace) {
    if (!mounted) return;
    setState(() => _saveError = '保存失败，内容已保留，请重试');
    // 在实际项目中记录脱敏后的 error/stackTrace。
  } finally {
    if (mounted) setState(() => _saving = false);
  }
}
```

按钮禁用只是一层保护，处理函数还要检查 `_saving`。保存期间同时锁住会改变提交内容的输入、删除图片、放弃等入口，避免 UI 展示的新草稿与正在提交的快照不一致。跨页面命令由 controller 串行化，见第 6、17 章。

不要让底层用一个吞掉异常的 `AsyncValue.guard` 更新 state 后无条件返回 void，UI 再直接 pop：那会把失败伪装成保存成功。命令必须有明确的成功或失败信号。

## 8.4 返回与键盘

用 PopScope 保护未保存内容，AppBar、系统返回和手势都要测试。第 17 章采用“明确放弃按钮 + 返回提示”的简单交互。升级确认弹窗时，确认后允许下一次 pop，并确保保存期间不能退出。

表单放进可滚动容器；利用 Scaffold 的键盘避让，不要把 `resizeToAvoidBottomInset: false` 当通用修复。Bottom sheet 表单需要根据 `MediaQuery.viewInsetsOf(context).bottom` 补充键盘空间，注意别与已有处理重复叠加。

单行标题可用 `TextInputAction.next` 把焦点移到正文；多行正文通常保留换行能力，不要把回车硬绑定成保存。提交前可以 unfocus，但用户输入失败时应容易回到对应字段。

## 8.5 进程退出时草稿如何处理

本教程保证页面存活时保存失败不丢草稿，不保证系统杀进程后未提交草稿恢复。需要后者时，增加独立的 draft 存储：按编辑目标保存、防抖写入、下次进入提供恢复、成功提交后清除。

自动保存不等于每次按键立即写业务 Note。必须定义草稿何时变正式记录、如何区分用户放弃、数据库失败时如何提示。不要把移动端的 dispose 当作一定会执行的最后保存机会。

## 8.6 本章交付

验收空白输入、只含空格、连续点击保存、保存失败后重试、保存中按返回、编辑已有笔记、键盘展开后的底部按钮。让 AI 生成测试后，亲自把 fake 改成抛错一次，确认测试确实覆盖失败保留草稿。

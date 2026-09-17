---
title: 第十一章 图片从选择到持久化
---

# 第十一章：图片从选择到持久化

“调用 picker 并显示图片”只完成了一小步。可交付的链路要覆盖临时文件、草稿取消、写库失败、进程被回收、孤儿文件和大图内存。

本章是第 17 章基线的扩展：先按第 9 章把 schema 升至 2，再增加 `imageName` 字段、模型转换与编辑页操作。不要直接把片段塞进 v1 数据库后运行。

## 11.1 先定义文件所有权

```text
系统选择/拍照产生 XFile
  -> 复制到应用持久目录，以 UUID 命名
  -> 草稿引用新文件名，保留旧图片
  -> 数据库保存成功：新引用生效，再清理不再引用的旧文件
  -> 保存失败：保留草稿与新文件，允许重试
  -> 明确放弃：清理本次草稿创建且未被引用的文件
```

数据库只存相对名称 `imageName`，显示时再拼当前应用目录。应用容器绝对路径可能变化；picker 返回的缓存路径也不能当长期地址。

## 11.2 导入服务示例

扩展依赖为 image_picker、path_provider、path、uuid；锁定与你 SDK 兼容的版本。本例用到的 API 在 image_picker 1.2.x 中提供，实际支持平台及配置以安装版本说明为准。

下面是可独立保存为媒体服务的完整代码，目标 Android/iOS，需要先完成权限用途声明：

```dart
import 'dart:io';
import 'package:image_picker/image_picker.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:uuid/uuid.dart';

class MediaService {
  final _picker = ImagePicker();

  Future<String?> pick(ImageSource source) async {
    final picked = await _picker.pickImage(
      source: source,
      maxWidth: 1600,
      imageQuality: 85,
    );
    if (picked == null) return null; // 用户取消，不是异常
    return persist(picked);
  }

  Future<String> persist(XFile source) async {
    final root = await getApplicationDocumentsDirectory();
    final directory = Directory(p.join(root.path, 'note-images'));
    await directory.create(recursive: true);
    final suffix = p.extension(source.path).toLowerCase();
    final name = '${const Uuid().v4()}$suffix';
    final target = File(p.join(directory.path, name));
    try {
      await source.saveTo(target.path);
      return name;
    } catch (_) {
      if (await target.exists()) await target.delete();
      rethrow;
    }
  }

  Future<LostDataResponse> recoverLostSelection() =>
      _picker.retrieveLostData();
}
```

UUID 避免覆盖同名图片；后缀不等于格式验证。需要上传或接受不可信文件时要校验实际类型、大小、像素数，并确定是否清理 EXIF。`imageQuality` 不保证固定输出大小，也不代表移除了所有元数据。

## 11.3 编辑页如何接入

输入区新增“拍照”“选图”“移除图片”三个明确动作。选择中进入 picking 状态避免同时打开多个 picker；finally 复位；await 后检查 mounted。若页面已退出，新导入文件要清理或交给垃圾回收队列，不能永久无人认领。

持有 `originalImageName` 和 `draftImageName`。再次选图替换的只是草稿；旧业务图片在保存成功前仍保留。清空必须是明确操作，不使用 `imageName ?? oldImageName` 的 copyWith，否则无法移除。

数据库更新和文件删除无法组成一个普通 SQLite 事务。优先保证引用指向已存在的文件，允许暂时多一个孤儿文件，再做补偿清理。不要先删旧图、后写数据库，一旦写库失败，原笔记就坏了。

垃圾清理应排除数据库引用和活跃草稿引用，设置合理保留期，避免把刚选入、尚未保存的图删掉。文件缺失时 UI 显示占位图并允许移除失效引用，整页不能崩溃。

## 11.4 Android 进程回收与 lost data

Android 打开外部选择/拍照界面时，应用进程可能被系统回收。不能只依赖原来的 `await pickImage` 返回；启动阶段检查 `retrieveLostData()`，处理返回的 files 或 exception。

恢复到哪条笔记不能靠当前内存猜。打开 picker 前持久化操作上下文，例如 draft id、目标 note id 与时间；恢复时核对上下文，再提供“恢复这次附件”的入口。没有可靠上下文时让用户确认归属，不能自动附加到任意一条笔记。

## 11.5 图片显示与内存

`Image.file(File(path))` 适合本章移动端目标，不能把 dart:io 原样用于 Web。列表用缩略图和适当解码尺寸，例如根据显示逻辑尺寸与设备像素比设置 cacheWidth；全尺寸原图不应在几十个列表项中同时解码。

布局 width/height 只控制显示尺寸，不必然降低解码内存。压缩、缩略图、上传图片是不同步骤，可能要分别设计。失败时用 errorBuilder 提供替代内容。

## 11.6 本章交付

测试：选择后取消编辑、替换后保存失败、移除图片、重启后显示、图片丢失、连续快速点击、系统回收后恢复。抽查应用目录，确认不持续产生未清理的大文件。

让 AI 先列出每个文件由谁创建、何时转交、何时删除，再写代码。能回答这些问题，图片功能才算接入完成。

---
title: 第七章 异步与网络（dio 实战）
---

# 第七章：异步与网络（dio 实战）（把“接口调用”做成工程能力）

## 7.1 本章目标（验收标准）
完成后你需要能：
- 用 dio 发起 GET/POST 请求并解析数据
- 统一处理：超时、网络错误、后端错误码、重试（基础版）
- 用 Riverpod 管理“加载中/成功/失败”三态（AsyncValue）
- 写出可复用的网络层模板（以后项目直接套用）

---

## 7.2 核心概念：移动端网络与 Web 网络的差异
**Web（浏览器）**
- fetch/axios 受浏览器同源/CORS、cookie、缓存策略影响

**移动端（Flutter）**
- 直接发 HTTP 请求，不受 CORS 限制（但会受证书/系统代理/网络环境影响）
- 你需要自己做：超时、错误提示、日志、token 注入、证书问题处理

---

## 7.3 安装 dio
```powershell
flutter pub add dio
```

（可选）想看网络日志更清晰：
```powershell
flutter pub add pretty_dio_logger
```

---

## 7.4 实战：实现一个“远程数据页”（可运行）
我们用公开测试接口 `https://jsonplaceholder.typicode.com/posts` 做演示。
- 不依赖你自己的后端
- 重点是：网络层结构与三态管理

#### 7.4.1 新建文件结构
在 `lib/` 下创建：
- `lib/network/app_dio.dart`
- `lib/network/app_error.dart`
- `lib/features/remote_posts/post.dart`
- `lib/features/remote_posts/posts_provider.dart`
- `lib/pages/remote_posts_page.dart`

并把路由加到设置页入口（本章给出完整代码）。

#### 7.4.2 `lib/network/app_error.dart`（完整）
```dart
class AppError implements Exception {
  final String message;
  final Object? cause;

  const AppError(this.message, {this.cause});

  @override
  String toString() => 'AppError(message: $message, cause: $cause)';
}
```

#### 7.4.3 `lib/network/app_dio.dart`（完整）
```dart
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app_error.dart';

final dioProvider = Provider<Dio>((ref) {
  final dio = Dio(
    BaseOptions(
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 10),
      sendTimeout: const Duration(seconds: 10),
      responseType: ResponseType.json,
    ),
  );

  dio.interceptors.add(
    InterceptorsWrapper(
      onRequest: (options, handler) {
        // 统一加 headers/token（后续你会从 secure storage 读 token）
        options.headers['X-App'] = 'fe-to-flutter-notes';
        handler.next(options);
      },
      onError: (e, handler) {
        // 统一错误包装
        handler.reject(
          DioException(
            requestOptions: e.requestOptions,
            response: e.response,
            type: e.type,
            error: AppError(_mapDioError(e), cause: e),
          ),
        );
      },
    ),
  );

  return dio;
});

String _mapDioError(DioException e) {
  switch (e.type) {
    case DioExceptionType.connectionTimeout:
    case DioExceptionType.sendTimeout:
    case DioExceptionType.receiveTimeout:
      return '网络超时，请稍后再试';
    case DioExceptionType.badCertificate:
      return '证书错误（https）';
    case DioExceptionType.connectionError:
      return '网络连接失败，请检查网络';
    case DioExceptionType.badResponse:
      final code = e.response?.statusCode;
      return '服务端错误（$code）';
    case DioExceptionType.cancel:
      return '请求已取消';
    case DioExceptionType.unknown:
      return '未知网络错误';
  }
}
```

#### 7.4.4 `lib/features/remote_posts/post.dart`（完整）
```dart
class Post {
  final int id;
  final String title;
  final String body;

  const Post({
    required this.id,
    required this.title,
    required this.body,
  });

  static Post fromJson(Map<String, dynamic> json) {
    return Post(
      id: json['id'] as int,
      title: json['title'] as String,
      body: json['body'] as String,
    );
  }
}
```

#### 7.4.5 `lib/features/remote_posts/posts_provider.dart`（完整）
```dart
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../network/app_dio.dart';
import '../../network/app_error.dart';
import 'post.dart';

final postsProvider = AsyncNotifierProvider<PostsNotifier, List<Post>>(PostsNotifier.new);

class PostsNotifier extends AsyncNotifier<List<Post>> {
  @override
  Future<List<Post>> build() async {
    return _fetchPosts();
  }

  Future<List<Post>> _fetchPosts() async {
    final dio = ref.read(dioProvider);
    try {
      final res = await dio.get('https://jsonplaceholder.typicode.com/posts');
      final data = res.data;
      if (data is! List) throw const AppError('响应格式错误');
      return data
          .cast<Map<String, dynamic>>()
          .map(Post.fromJson)
          .toList(growable: false);
    } on DioException catch (e) {
      final err = e.error;
      if (err is AppError) throw err;
      throw AppError('网络请求失败', cause: e);
    }
  }

  Future<void> refresh() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(_fetchPosts);
  }
}
```

#### 7.4.6 `lib/pages/remote_posts_page.dart`（完整）
```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../features/remote_posts/posts_provider.dart';

class RemotePostsPage extends ConsumerWidget {
  const RemotePostsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncPosts = ref.watch(postsProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('远程数据（dio）'),
        actions: [
          IconButton(
            tooltip: '刷新',
            onPressed: () => ref.read(postsProvider.notifier).refresh(),
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: asyncPosts.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text('加载失败：$e'),
                const SizedBox(height: 12),
                FilledButton(
                  onPressed: () => ref.read(postsProvider.notifier).refresh(),
                  child: const Text('重试'),
                ),
              ],
            ),
          ),
        ),
        data: (posts) => ListView.separated(
          padding: const EdgeInsets.all(12),
          itemCount: posts.length,
          separatorBuilder: (_, __) => const SizedBox(height: 8),
          itemBuilder: (context, index) {
            final p = posts[index];
            return Card(
              child: ListTile(
                title: Text(p.title, maxLines: 1, overflow: TextOverflow.ellipsis),
                subtitle: Text(p.body, maxLines: 2, overflow: TextOverflow.ellipsis),
              ),
            );
          },
        ),
      ),
    );
  }
}
```

#### 7.4.7 把页面挂到路由与设置入口（关键步骤）
1) 在路由里加：`/remote-posts`
2) 设置页加一个 ListTile 跳转

示例（只展示关键片段）：

`main.dart` 路由新增：
```dart
GoRoute(
  path: 'remote-posts',
  builder: (context, state) => const RemotePostsPage(),
),
```

`settings_page.dart` 增加入口：
```dart
ListTile(
  title: const Text('远程数据（dio）'),
  trailing: const Icon(Icons.chevron_right),
  onTap: () => context.push('/remote-posts'),
),
```

---

## 7.5 Web 前端思维对比：网络层怎么“工程化”
- Web 你可能 axios instance + interceptors
- Flutter 也是一样，但你更应该：
  - 统一超时与错误文案
  - 把 token 注入放在 interceptor
  - 把“业务错误码”映射成领域错误（后面架构章节会做）

---

## 7.6 实战小练习（必须做）

#### 练习 A：加一个简单的“请求重试（最多 2 次）”
要求：
- 只对超时/连接失败重试
- 每次重试间隔 300ms

提示：在 `_fetchPosts()` 里写一个循环即可（先不要引入复杂库）。

#### 练习 B：加一个“分页加载”
要求：
- `posts` 只显示前 20 条
- 下拉到底部再加载下一页（你可以用本地 slice 模拟分页）

---

## 7.7 常见坑
- `res.data` 类型不确定：一定做类型判断（List/Map）
- 在 UI 里直接调用 dio：会让代码不可测试、不可维护
- 忘记处理 loading/error：移动端“空白页”是大忌

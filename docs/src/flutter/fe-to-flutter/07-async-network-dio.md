---
title: 第七章 网络、异步竞态与失败恢复
---

# 第七章：网络、异步竞态与失败恢复

JS 的请求调用你已经会了。Flutter 网络开发真正新增的难点是页面生命周期、移动网络变化、原生网络策略，以及进程可能在任何时刻退出。本章是主线 App 的可选扩展，不依赖一个虚构的公共笔记后端。

## 7.1 先约定接口，再生成客户端

练习「远程搜索笔记」时，先明确契约：

```text
GET /notes?q=flutter&cursor=...
200: { "items": [{ "id": "n1", "content": "...", "updatedAt": "...Z" }],
       "nextCursor": null }
400: 参数错误；401: 未认证；403: 无权访问；5xx: 服务端故障
排序：updatedAt 倒序；同时间按 id 排序；cursor 为服务端不透明值
```

这是你需要与后端或本地 mock 实现的接口，不是可直接访问的服务。开发环境可先用 fake repository 返回固定数据和失败，UI 不必等待后端上线。

认证、分页、错误结构未明确前，不要让 AI 自行猜字段，更不要为了匹配猜出来的客户端去改服务端契约。

## 7.2 Dio 配置与解析边界

可单独安装 `dio: 5.9.0` 作为本章示例版本。下面是独立网络模块的完整定义，未接入第 17 章 UI；需要替换 API_BASE_URL 并实现上述后端。

```dart
import 'package:dio/dio.dart';

Dio createDio(String baseUrl) => Dio(BaseOptions(
  baseUrl: baseUrl,
  connectTimeout: const Duration(seconds: 10),
  receiveTimeout: const Duration(seconds: 15),
  sendTimeout: const Duration(seconds: 15),
));

class RemoteNote {
  const RemoteNote(this.id, this.content);
  final String id;
  final String content;

  factory RemoteNote.fromJson(Object? value) {
    if (value case {'id': String id, 'content': String content}) {
      return RemoteNote(id, content);
    }
    throw const FormatException('笔记字段不符合契约');
  }
}

Future<List<RemoteNote>> searchNotes(
  Dio dio,
  String query,
  CancelToken token,
) async {
  final response = await dio.get<Object?>(
    '/notes',
    queryParameters: {'q': query},
    cancelToken: token,
  );
  if (response.data case {'items': List items}) {
    return items.map(RemoteNote.fromJson).toList(growable: false);
  }
  throw const FormatException('响应缺少 items 数组');
}
```

这是第一页搜索示例；真正分页还需解析 nextCursor。不要把无效响应默认为空列表：用户会误以为没有数据，接口错误被隐藏。UTC 时间也应在边界解析，显示时再转本地时区。

## 7.3 搜索竞态：取消与过期判断都需要

用户输入 `f` 后马上输入 `flutter`。第二次请求先返回，第一次随后返回；如果无条件赋值，UI 会退回旧结果。debounce 只能减少请求次数，不能证明响应顺序。

下面是放入某个 State 的局部逻辑；`_dio`、`_results`、`_error` 由该 State 持有：

```dart
int _generation = 0;
CancelToken? _cancelToken;

Future<void> search(String query) async {
  final generation = ++_generation;
  _cancelToken?.cancel('新的查询已开始');
  final token = CancelToken();
  _cancelToken = token;
  setState(() => _error = null);
  try {
    final result = await searchNotes(_dio, query, token);
    if (!mounted || generation != _generation) return;
    setState(() => _results = result);
  } on DioException catch (error) {
    if (CancelToken.isCancel(error)) return;
    if (!mounted || generation != _generation) return;
    setState(() => _error = '网络暂不可用，请重试');
  } on FormatException {
    if (!mounted || generation != _generation) return;
    setState(() => _error = '数据格式异常，请稍后重试');
  }
}

@override
void dispose() {
  _generation++;
  _cancelToken?.cancel('页面已关闭');
  super.dispose();
}
```

生产 UI 还要维护 loading/refreshing，并在对应 generation 结束时复位；debounce Timer 也要取消。请求取消不保证服务端停止工作，对 POST 尤其如此。保存超时后用户重试，要通过幂等键或业务 id 防重复创建。

## 7.4 不同失败需要不同动作

| 失败 | 用户动作 | 自动重试策略 |
| --- | --- | --- |
| 主动取消 | 通常无提示 | 不重试 |
| 超时、临时断网 | 保留旧数据，允许重试 | 仅对安全/幂等操作做有限退避 |
| 400 / 字段校验 | 修改输入 | 不盲目重试 |
| 401 | 按认证策略刷新或重新登录 | 刷新只执行一次并共享结果 |
| 403 | 展示权限限制 | 换 token 重试通常无效 |
| JSON 格式不符 | 记录契约问题 | 不把它伪装成空数据 |

多个 401 同时触发刷新时要 single-flight，其他请求等待同一个刷新 Future；刷新失败应结束循环，清理对应会话。登录信息按账号隔离，退出时清理敏感缓存。日志记录 request id、状态码和脱敏错误，不记录完整 token、密码和笔记正文。

## 7.5 移动端与浏览器网络差异

Android 模拟器访问开发电脑通常使用 `10.0.2.2`，手机上的 localhost 是手机自己。iOS 模拟器与真机的网络路径也不同。手机访问电脑需要可达的局域网地址、防火墙放行及合适的服务监听地址。

原生 Android/iOS HTTP 通常不受浏览器 CORS 限制，但仍受 HTTPS、证书、Android 明文策略和 iOS ATS 限制。Flutter Web 则仍遵循浏览器规则。不要用关闭所有证书校验修复网络报错。

## 7.6 分页与离线的验收要具体

分页状态至少包含 items、nextCursor、loadingMore、loadMoreError；同一 cursor 不重复请求，追加时按 id 去重，刷新启动新 generation，让旧的 loadMore 结果失效。尾页失败保留已加载内容，重试尾页即可。

本地保存加远程接口不等于离线同步。同步还需要 outbox、幂等操作、服务端版本、删除 tombstone、冲突规则和重试。先交付本地 CRUD，再单独设计同步，别让 AI 只在 upsert 后多加一个 POST 就宣布完成。

## 7.7 本章交付

让 fake 后端把旧查询延迟 2 秒、新查询延迟 100ms，确认最终只显示新结果；搜索中退出页面，不出现 dispose 后 setState；断网后可重试；协议错误可与空列表区分。

AI 任务：“实现上述接口客户端并注入 fake；先给出竞态测试，不连接真实生产服务。”这些测试比“正常网络下看到数据”更有价值。参考 [Dio](https://pub.dev/packages/dio)。

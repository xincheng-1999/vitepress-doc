# 04 从工程到真机运行

这一章的目标是把「能写代码」变成「知道手机上跑的是哪个包、哪个环境、哪一次构建」。先把普通页面、网络和登录跑通，再接支付。

## 1. 准备工具

- 一台能运行所需 Xcode 版本的 Mac。
- Xcode 及需要的平台支持、模拟器 runtime。
- 目标团队的开发权限或已授权的签名材料。
- 真机测试需要 iPhone、可靠数据线或已配对的无线连接。
- Flutter 项目还需要匹配项目版本的 Flutter / Dart，以及项目采用的 CocoaPods 或其他原生依赖机制。

不要为打开一个旧项目就全局升级全部 SDK。先看项目锁文件、README 和 CI 使用的版本。

只读检查命令：

```bash
xcodebuild -version
xcode-select -p
flutter doctor -v    # 仅 Flutter 项目需要
flutter devices
```

`xcode-select` 决定命令行使用哪套开发工具，Xcode 的图形界面版本与命令行版本可能不同。发现问题后再调整，不要先运行一堆全局修改命令。

## 2. 打开正确工程

| 文件 / 概念 | 前端类比 | 实际含义 |
| --- | --- | --- |
| `.xcodeproj` | 单个项目配置 | Targets、编译配置、资源和依赖等 |
| `.xcworkspace` | 多包工作区 | 组合多个工程；Flutter + CocoaPods 通常打开 `ios/Runner.xcworkspace` |
| Target | 一个可构建交付单元 | 主 App、测试、扩展分别有 Target |
| Scheme | 一套运行/测试/归档选择 | 决定构建哪些 Target、使用哪种配置和操作 |
| Debug / Release | 开发构建 / 生产优化构建 | 调试信息、优化等，不决定业务域名或苹果支付环境 |
| Destination | 运行目标 | 某台模拟器、某台 iPhone 或用于归档的通用设备目标 |

打开 `.xcodeproj` 却看不到 Pods 依赖，是 Flutter 初学者常见问题。使用项目已有 workspace，不要先删除全部依赖缓存。

## 3. 配置自动签名

1. Xcode → Settings → Apple Accounts，添加有权限的账号。
2. 在项目编辑器中选择 **TARGETS 下的主 App**，不要误选测试 Target 或 Pods。
3. 进入 **Signing & Capabilities**。
4. 勾选 **Automatically manage signing**。
5. Team 选目标 App 所属团队。
6. Bundle Identifier 填已注册的正确 Bundle ID。
7. 根据实际功能添加能力，确认描述文件和证书状态没有错误。

![自动签名界面，标注 Target、签名面板、自动签名、团队包名以及描述文件证书](/apple-development/xcode-signing-annotated.png)

图中编号：

1. 主 App Target。
2. Signing & Capabilities 面板。
3. 自动签名开关：Xcode 管理相关资源，但仍受团队权限、网络和设备条件限制。
4. Team 和 Bundle Identifier 必须匹配；图中真实值已遮盖。
5. 查看实际选中的描述文件和证书。显示 Xcode Managed Profile 不是“无描述文件”。

这张图只用于解释字段，没有在采集时修改项目设置。不要照着别人的 Team 名称填；同名团队也应核对 Team ID。

## 4. 先运行模拟器，再运行真机

### 模拟器

选择已安装的 iPhone Simulator，运行工程，确认入口页面、布局、导航、网络错误提示等。模拟器通常不依赖真机开发描述文件，但部分 SDK 和设备能力不支持模拟器。

模拟器成功不能证明真机签名、相机、推送、供应商 SDK 或真实沙盒内购成功。StoreKit 本地测试可以在模拟器验证许多支付交互，但不等于苹果服务器沙盒联调。

### 真机

1. 连接手机并解锁，按提示信任电脑。
2. 在 Xcode 的 Devices and Simulators 确认设备可见。
3. 按 iOS 要求开启 Developer Mode，可能需要重启并再次确认。
4. 团队设备列表和描述文件包含这台手机；自动签名可能代为处理。
5. 选择该设备并运行；按系统提示完成必要的开发者信任。
6. 查看 App 是否真正打开，而不是只看到编译成功。

不需要为 TestFlight 用户逐台登记设备；TestFlight 是后续独立的分发路径。

## 5. Flutter 中常见的运行命令

在 Flutter App 根目录执行，设备 ID 用 `flutter devices` 的实际输出替换：

```bash
# --dart-define 的键名由项目自行实现，不是 Flutter 默认业务配置
flutter run -d '<实际设备ID>' --dart-define=APP_ENV=test

# 构建供模拟器使用的包
flutter build ios --simulator

# 只验证设备架构编译；未签名产物不能直接拿去真机安装
flutter build ios --no-codesign

# 需要配置好签名和分发条件才能完成 IPA 交付
flutter build ipa --release
```

上面是命令模板，不是对任意仓库都适用的发布脚本。项目有 flavor、多个 Scheme 或指定导出配置时，应沿用其已验证命令。

`--dart-define` 是编译期注入，已安装的 App 不会因为你编辑 JSON 或环境文件就自动改变。修改后要重新构建、安装并确认实际配置。

## 6. 三种“环境”独立存在

| 维度 | 例子 | 由谁决定 |
| --- | --- | --- |
| 构建模式 | Debug、Profile、Release | Flutter / Xcode 构建参数 |
| 业务后端环境 | development、test、production | App 的域名与业务配置 |
| 苹果交易环境 | Xcode 本地测试、Sandbox、Production | StoreKit 测试设置、安装分发方式和苹果购买环境 |

例如：Release 构建 + 业务测试后端 + TestFlight 的 Sandbox 交易，是可以存在的组合。不能在后端写成“Release 就查苹果正式交易”。

## 7. 前端开发最容易踩的本地 H5 问题

**真机里的 `localhost` 是手机自己，不是你的 Mac。** Android 的 `adb reverse` 不会自动作用于 iPhone。

如果 Flutter 页面加载本地开发的会员 H5，选择以下一种明确的方案：

- 使用手机能访问的 HTTPS 测试站点；这是团队联调较容易复用的方式。
- 使用项目已配置的本地网络地址与开发 HTTPS，确认电脑防火墙、监听地址和手机网络。
- 使用团队认可的调试代理或隧道，并在项目允许的调试配置中明确限定范围。

HTTP 地址可能被 App Transport Security、WebView 配置或项目自己的 URL 校验拒绝。不要为了让一页 H5 打开，就默认关闭所有网络安全限制。

H5 请求 App 原生内购时通常经过 JS Bridge。网页里的 toast 成功不是支付成功；结果应由原生购买和服务端验单反馈。

## 8. 调试与验收记录

每次联调至少记下：

```text
Bundle ID：实际安装包标识
Team：实际签名团队
版本 / 构建号：例如 1.2.0 / 42
构建模式：Debug 或 Release
业务后端：域名及环境，不记录令牌
设备 / iOS：实际设备和系统版本，不公开 UDID
H5 地址：实际可访问的页面来源
已验证行为：启动、登录、商品查询、购买、权益等分别记录
```

看到应用启动后还要验证真实入口：首次隐私、登录、网络错误、切后台恢复、权限拒绝等。录屏和日志不要包含密码、验证码、访问令牌或完整个人信息。

## 本章验收

你能在真机打开 App，并说明安装包身份、签名团队、业务环境和页面来源。遇到异常能区分“编译失败”“签名失败”“安装失败”“启动崩溃”和“业务接口失败”。

下一章：[App Store Connect 后台配置](/apple-development/05-app-store-connect)。

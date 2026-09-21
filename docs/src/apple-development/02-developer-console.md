# 02 Apple Developer 后台配置

打开 [Apple Developer Account](https://developer.apple.com/account/)，在有权限的团队下进入 **Certificates, Identifiers & Profiles**。它主要维护 App 的技术身份和授权，不负责 App Store 展示截图、会员价格或业务用户。

## 1. 后台几个栏目分别是什么

| 栏目 | 配置的东西 | 什么时候需要 |
| --- | --- | --- |
| Certificates（证书） | 开发、分发及特定服务证书 | 真机签名、分发或使用对应服务 |
| Identifiers（标识符） | App ID、App Groups、Services ID 等 | 注册 App 身份、启用能力、关联服务 |
| Devices（设备） | 测试设备 UDID | 开发签名或 Ad Hoc 指定设备安装 |
| Profiles（描述文件） | 把 App、证书、能力和适用设备组合成签名授权 | 手动签名，或检查自动签名产生的结果 |
| Keys（密钥） | 特定苹果服务使用的私钥和 Key ID | 服务端调用推送等服务；不同用途的密钥不能混用 |
| Membership details | 会员类型、团队、有效期等 | 确认组织/个人、归属及续费状态 |

App Store Server API、App Store Connect API 等相关密钥按用途从 App Store Connect 的对应入口生成；不要因为都叫 `.p8` 就认为必须在 Developer 的 Keys 栏创建。支付密钥见 [06](/apple-development/06-in-app-purchase)。

## 2. Identifiers：给 App 注册身份

### Bundle ID、App ID 和数字 Apple ID

**Bundle ID** 是工程中的 App 标识，例如 `com.example.canvas`。它看起来像反写域名，但不是请求地址，也不是后端域名；注册了它不会自动获得该域名的所有权。

在 Developer 中注册 App ID 时，会填写 Bundle ID 并选择 App 能力。签名授权中的 application-identifier 通常由 **App ID Prefix + Bundle ID** 组成。现代账户中 Prefix 常与 Team ID 相同，但历史账户等情况可能不同，不要硬编码“永远相等”。

App Store Connect 创建 App 记录后生成的**数字 Apple ID**，是另一种商店标识。它不是用户登录邮箱，也不是 Bundle ID。

### 新 App 的操作路径

1. Developer → Identifiers → 添加 → App IDs → App。
2. 填写便于团队识别的 Description。
3. 一般选 **Explicit（明确）Bundle ID**，填写计划长期使用的标识。
4. 只开启实际需要的 Capabilities。
5. 注册后，在 Xcode 的 Target 中使用同一个 Bundle ID。

已有工程不要重新注册一个相似拼写的 ID 来“试试”。先确认现有 App ID 归属、用途和实际环境，避免把测试包变成另一个 App。

Wildcard（通配符）App ID 可匹配一组 Bundle ID，但不支持所有能力。要使用内购等需要明确 App 身份的功能，应使用明确 App ID 及匹配配置。

### 改 Bundle ID 会发生什么

从 `com.example.canvas` 改成 `com.example.canvas.dev`，对系统和苹果商店而言是另一个 App 身份。原来的内购、推送、描述文件、钥匙串组、Universal Links、第三方 SDK 绑定可能需要重新配置；已安装应用的数据也不会自动迁移过去。

它不是前端随手改 `package.json` 的 `name`。商店 App 上传构建后，不能靠修改工程包名给原 App 换身份。

## 3. Capabilities 与 Entitlements：声明你要使用什么能力

| 能力 | 作用 | 常见额外配置 |
| --- | --- | --- |
| In-App Purchase | 数字商品内购 | App Store Connect 商品、协议、StoreKit 和验单服务 |
| Push Notifications | 苹果推送 | APNs 服务端凭据、设备 token、环境 |
| Associated Domains | Universal Links、共享网站凭据等 | `applinks:` 等域名声明及网站关联文件 |
| Sign in with Apple | 使用 Apple 身份登录业务系统 | 对应标识符、回调和服务端 token 验证 |
| App Groups | App 与扩展共享特定数据容器 | 同组标识符和匹配签名授权 |
| Keychain Sharing | 指定应用之间共享钥匙串访问组 | 访问组及匹配团队授权 |
| iCloud | 使用 iCloud 容器能力 | 容器、环境及相应开发实现 |

三层要对应：**Developer 中 App ID 的能力 → 工程声明 → 签名授权**。不是每个 Capability 都对应一个同名 entitlement 字段，Xcode 会按能力管理所需配置。

`.entitlements` 是工程声明能力的文件，既不是私钥也不是证书。声明一个没有获授权的能力，可能构建失败、安装失败或运行时不能用。

### 它和 Info.plist 的隐私说明不是一回事

- `NSCameraUsageDescription`、`NSPhotoLibraryUsageDescription`：解释为什么请求相机、相册等访问。
- 系统运行时授权弹框：用户是否允许该权限。
- App Store 隐私标签：声明你及 SDK 如何收集、关联、使用数据。
- Privacy Manifest：对隐私相关行为及必要理由 API 等作机器可读声明。

这些不是填一处即可替代其他所有处。比如写了相册说明，不代表 App 自动获得照片访问权限。

## 4. Devices：哪些手机可以安装开发包

**UDID 是设备标识，不是用户账号，也不是设备名称。** 在 Xcode 的 Devices and Simulators 或设备详情中可找到它。

开发签名和 Ad Hoc 安装通常要求设备在相应描述文件中。自动签名可在权限、设备额度等条件满足时登记设备并更新描述文件。管理员可能限制注册，设备额度也由苹果计划规则限制。

TestFlight 和 App Store 用户安装通常不要求团队逐一登记 UDID。这也是不宜把开发用 IPA 当成大规模内测方案的原因。

## 5. Profiles：把分散条件组合起来

创建手动开发描述文件的顺序：

1. Profiles → 添加 → 选择 iOS App Development 等正确类型。
2. 选择 App ID。
3. 选择用于签名的开发证书。
4. 选择测试设备。
5. 命名、生成、下载，交给 Xcode 使用。

如果新增设备、改变能力、换证书或描述文件过期，往往需要更新或重新生成。描述文件不会帮你补出证书私钥。

自动签名适合大部分日常开发，手动签名常用于明确控制发布和 CI 环境。两种方式只是管理签名材料的方式不同，并不是两种支付环境。

## 6. Keys：服务端调用苹果的凭据

例如后端发送 APNs 推送，可以使用对应 APNs 认证密钥。它与编译 App 的签名私钥不同。Key ID 帮苹果定位密钥；Team ID、Issuer ID 等字段的含义取决于具体 API，不可互相代填。

许多 `.p8` 私钥仅在创建时允许下载一次。应保存在受控的凭据系统中，不能放前端静态资源、App 安装包或公开 Git 仓库。下载 `.cer` 可以重新取得公钥证书，下载丢失的私钥通常没有这样的补救方式。

## 本章验收

你能指出目标 App 的 Bundle ID、团队、已启用能力、可用测试设备和签名方式，并理解：**注册 App ID ≠ 创建商店 App ≠ 创建内购商品**。

官方依据：[开发证书说明](https://developer.apple.com/help/account/certificates/certificates-overview/) · [创建开发描述文件](https://developer.apple.com/help/account/provisioning-profiles/create-a-development-provisioning-profile/)

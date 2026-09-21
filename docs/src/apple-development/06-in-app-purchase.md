# 06 内购、服务端和沙盒全链路

本章讨论 App 内数字会员、点数和内容等 **In-App Purchase（IAP）**。它与 Apple Pay 不是同一个产品。即使某个业务接口命名为 `/applePay/verify`，实际走 StoreKit 时仍然是内购，不能据此去配置 Apple Pay 的 Merchant ID。

## 1. 谁负责什么

| 参与者 | 职责 |
| --- | --- |
| H5 / Flutter 页面 | 展示套餐、发起意图、呈现状态；不能自己决定付款成功 |
| iOS 原生 StoreKit | 查询苹果商品、发起系统购买、接收并本地验证交易 |
| Apple Store 服务 | 处理商品、付款、订阅周期及交易记录 |
| 业务服务端 | 核对可信交易、绑定业务用户和订单、幂等发放权益 |
| 业务数据库 | 持久保存订单、会员期限、点数、订阅状态等 |

前端可把 StoreKit 理解为“苹果提供的原生支付 API”，但它比普通 `fetch` 多了系统购买 UI、用户取消、待批准交易、进程重启后回补、订阅自动续费等生命周期。

## 2. 为什么苹果和自己的后台都要配置商品

假设我们出售“30 天会员”：

| 配置 | 示例内容 | 回答的问题 |
| --- | --- | --- |
| 苹果商品 | Product ID、商品类型、价格、地区和销售状态 | 苹果究竟出售什么、收多少钱、是否自动续费 |
| 业务商品 | 业务 SKU、允许客户端、权益类型、30 天授权规则、苹果商品映射 | 支付后给哪个业务用户增加什么权益 |
| 页面映射 | `monthly` → `com.example.canvas.vip.30days` | 页面“月会员”按钮对应哪个实际商品 |

两边不是各收一次钱。**苹果处理收款和交易，业务后台处理自己的权益。** 苹果不会自动修改你 MongoDB 或 MySQL 里的会员到期时间。

业务后台价格改成 9.9 元，不能强迫 StoreKit 按 9.9 元收费。iOS 购买展示应优先使用 StoreKit 的本地化价格（例如 `displayPrice`），并检查服务端展示与苹果价格是否一致；不要把人民币字符串硬套给所有商店地区。

## 3. 商品 ID、包名和映射

```json
{
  "monthly": {
    "iOS": "com.example.canvas.vip.30days",
    "android": "canvas.vip.30days.android"
  }
}
```

这只是**示例项目的配置格式**，`iOS`、`android` 和 `monthly` 都是项目自行约定的键，不是 Apple 强制字段。真实商品 ID 必须来自你的现有后台和苹果配置，不能猜拼写。

配置映射不等于创建苹果商品。服务端也可以直接返回平台映射，项目采用哪种方式应有明确事实源，避免前端和服务端各维护一份互相冲突的目录。

Product ID 在一个 App 内唯一；不同 App 可以使用相同字符串，但它们仍是不同 App 下的独立商品。苹果还为商品分配数字 Apple ID，不能与开发者填写的 Product ID 混淆。

服务端应按**App 身份 + 商品 ID**解释交易；苹果交易另有 Sandbox / Production 环境，处理时也应区分。只有 `vip.month` 这个字符串，不足以说明这是哪款 App 的付款。

**保留 B App 的 Bundle ID，仅复制 A App 的商品 ID，不能直接购买 A 的商品。** 若给 B 创建同名商品，后端也要支持 B 的 App 身份、API 凭据、验单和权益映射。JWT 中的包名或交易检查若写死为 A，流程仍会失败。

## 4. 先选对商品类型

| 苹果类型 | 常见例子 | 关键行为 |
| --- | --- | --- |
| Consumable（消耗型） | 点数、游戏币、可重复购买的使用次数 | 可重复购买；已消耗或已完成交易的恢复不能只依赖客户端历史，业务服务端要记账 |
| Non-Consumable（非消耗型） | 永久解锁某功能 | 一次购买长期拥有，通常需要支持恢复 |
| Auto-Renewable Subscription（自动续期订阅） | 每月自动续费会员 | 有订阅组、周期、续费、到期、退款等状态 |
| Non-Renewing Subscription（非续期订阅） | 不自动续费的限时访问 | 不自动续费，开发者负责相应访问期限及跨设备权益管理 |

“月会员”只描述权益时长，不能据此判断它是否自动续订。商品类型要与实际业务、苹果规则和客户端支持能力一致，创建前先确认，不能只为了拉起购买弹框随便选择。

对于只实现一次性购买的客户端，不应将自动续订商品塞进配置；需要完整实现订阅及恢复合同后再开放。

## 5. 在苹果后台创建商品要填什么

路径：选择目标 App，再进入 In-App Purchases 或 Subscriptions。创建能力取决于当前角色；商品要具备苹果要求的完整资料和测试/销售条件。

| 字段 | 作用 |
| --- | --- |
| Reference Name | 团队内部识别用的名称，不是用户最终看到的全部文案 |
| Product ID | StoreKit 查询购买时使用；保存后不能随意修改，删除也不代表能在同 App 重用 |
| Type | 决定一次性、消耗型、订阅等规则 |
| Localization | 购买界面、商店等所需显示名称和说明 |
| Price / Availability | 苹果价格及可销售地区，不由前端传入金额决定 |
| Review Information | 商品审核截图、说明等，帮助审核员找到和理解购买入口 |
| Subscription Group（订阅） | 组织同一服务不同档位的自动续订商品，涉及升降级等行为 |
| Duration / Offers（订阅） | 周期、介绍优惠等；优惠资格需按苹果规则判断 |

首次内购及相关订阅提交可能需要与新 App 版本一起送审，按后台当前提示操作。**沙盒可测不代表商品已获批正式销售。**

## 6. 成功路径：从点击购买到权益到账

![内购主流程时序图：预下单、StoreKit、服务端查询验单、权益发放与 finish](/apple-development/payment-sequence.svg)

业务接口可以命名为 `/iap/prepay`、`/iap/verify`；这些是教学示例，不是 Apple 规定的路径。以“业务订单号必须是 UUID、将其放入 appAccountToken”的常见实现为例：

1. 页面读取套餐及平台商品 ID。App 从 StoreKit 读取商品能力、当地价格等信息。
2. 用户点击购买，App 向自己的服务端预下单，服务端验证商品和业务用户。
3. 服务端返回业务订单号；示例将 UUID 订单号作为 `appAccountToken` 传入购买。其他项目也可使用稳定业务账号 UUID，需有明确关联策略。
4. App 发起 `Product.purchase`，苹果显示系统确认界面。
5. Apple 返回交易，StoreKit 2 会提供验证结果；只有可信交易才继续业务处理。
6. App 将苹果交易号和关联信息交给后端。后端不能信任“前端说成功”。
7. 后端按正确环境查询 Apple，或处理可信签名交易；校验交易归属及业务条件。
8. 后端以交易唯一标识实现幂等，成功发放并持久化权益，返回处理成功。
9. App 按交付合同调用 `finish`，刷新业务资料并展示结果。

**`finish` 是确认 App 已处理交易，不是这一步才扣款，也不是退款。** 若苹果付款成功而权益发放暂时失败，应保留可恢复的待处理记录，不能显示“请重新付款”来覆盖问题。

StoreKit 2 API 形态示意（不是可直接发布的完整实现）：

```swift
// 缺少超时、状态机、会话隔离及后台监听，仅用于理解调用关系。
let products = try await Product.products(for: [productID])
guard let product = products.first else { return }
let result = try await product.purchase(options: [.appAccountToken(orderUUID)])

switch result {
case .success(let verification):
    guard case .verified(let transaction) = verification else {
        // 不发放权益，进入项目约定的错误处理。
        return
    }
    try await verifyAndGrantOnServer(transaction)
    await transaction.finish()
case .userCancelled:
    // 恢复页面可操作状态，不冒充购买失败或成功。
    break
case .pending:
    // 等待后续交易更新，不自动重新购买。
    break
@unknown default:
    break
}
```

实际项目还需要监听 `Transaction.updates`、处理未完成交易、保护当前登录账号和页面销毁后的迟到回调，不能只保留按钮内这段代码。

## 7. 服务端确实会带着 Bundle ID 调苹果

使用 App Store Server API 时，服务端通常用对应的私钥签发短期 JWT，将其放在 `Authorization: Bearer ...` 请求头中。凭据包含 Key ID、Issuer ID 等信息，JWT 的 **`bid` 声明是 Bundle ID**。

```text
请求凭据中的 bid：com.example.canvas
查询目标：GET /inApps/v1/transactions/{transactionId}
返回交易中的 bundleId：也应匹配 com.example.canvas
```

正式和沙盒交易查询分别使用苹果相应 API 环境，例如：

```text
Production：https://api.storekit.itunes.apple.com
Sandbox：   https://api.storekit-sandbox.itunes.apple.com
```

包名不是秘密，也不能单独完成鉴权；苹果需要有效签名的服务端凭据。服务端 `.p8` 与 Xcode 开发签名 `.p12` 是两种用途不同的材料。

可靠的验单至少应考虑：

- 苹果签名及证书链或官方验证库的验证结果，不能只 Base64 解码 JWS。
- Bundle ID、商品 ID、环境与目标业务配置一致。
- 交易 ID、撤销/退款状态、订阅到期等与要发放的权益相符。
- `appAccountToken` 或服务端保存的关联能映射到正确订单和用户。
- 同一交易重复送达不重复加会员或点数；多 App 系统的唯一键应包含其必要的 App / 环境范围。
- 业务订单落库与权益发放的原子性或可靠补偿机制。

上面是应实现的验单合同，**不是说任何已有的 `verify` 接口都已经完成全部检查**。对现有后端需查源码与实测证据。

### 后端需要的支付密钥从哪里来

按苹果当前帮助，创建 In-App Purchase Key 需要 **Account Holder 或 Admin**：

1. App Store Connect → **Users and Access → Integrations**。
2. 左侧 **Keys → In-App Purchase**，选择生成密钥；已有密钥时从添加入口创建。
3. 填写仅供团队识别的名称，生成后记录 Key ID，并按服务端接入要求记录 Issuer ID。
4. 下载 `.p8`，交由后端的受控凭据系统保存。**私钥只能下载一次**；网页还能看到 Key ID，不等于还能重新下载私钥。
5. 后端将 `.p8`、Key ID、Issuer ID、目标 Bundle ID 和交易环境配套配置，再执行一笔沙盒查询验证。

这是准备流程说明，不要求为了阅读教程实际创建密钥。团队已有可用密钥时，先确认能否使用现有凭据。

| 常见材料 | 用途 | 不可混淆 |
| --- | --- | --- |
| In-App Purchase Key | App Store Server API、相关订阅优惠签名等 | 不是 App 安装包签名证书 |
| App Store Connect API Key | 授权范围内的后台自动化、构建上传等 | 不因同为 `.p8` 就能代替支付密钥 |
| Shared Secret | 旧版收据验证流程中相关自动续订订阅验证 | 不是 StoreKit 2 服务端 API JWT 的签名私钥 |

历史工程可能仍使用旧收据验证接口，应先辨认实现，再决定维护或迁移；不能仅按字段名“苹果密钥”把不同材料互换。

官方操作说明：[生成内购密钥](https://developer.apple.com/help/app-store-connect/configure-in-app-purchase-settings/generate-keys-for-in-app-purchases)。

## 8. App 没开着，续费和退款怎么处理

App Store Server Notifications V2 可将签名通知推送到配置的服务器 URL，覆盖订阅续费、到期、退款等事件。后台应按目标 App 填写适当的生产和沙盒通知地址；两者可以路由到同一服务，但不能混淆环境与数据。

服务端验证通知签名、App 身份和环境后，幂等更新订单及权益。通知可能重复、迟到或乱序，还应有交易历史/订阅状态查询等补偿途径。仅在客户端支付成功时改一次会员状态，不能完整管理订阅生命周期。

## 9. 四种测试方式，区别很大

| 方式 | 是否调用真实 StoreKit | 是否向苹果测试服务产生交易 | 是否实际扣款 | 主要验证什么 |
| --- | --- | --- | --- | --- |
| 自己写的 Mock | 通常不调用，取决于替身实现 | 否 | 否 | 页面交互、Bridge、状态展示 |
| Xcode StoreKit 本地测试 | 是，连接本地测试环境 | 不产生可供 Apple Sandbox API 查询的交易 | 否 | 商品、取消、失败、续订等本地行为 |
| Apple Sandbox | 是 | 是，沙盒交易 | 否 | 苹果购买 + 后端沙盒验单 + 业务权益 |
| App Store 正式购买 | 是 | 正式交易 | 是 | 正式销售链路 |

本地 `.storekit` 可手工定义商品或同步商品配置，但本地交易不因此变成 Apple Sandbox 交易。若 Scheme 仍选择本地 StoreKit Configuration，后端用沙盒交易查询可能找不到它。做真实沙盒联调前检查 Scheme 的运行选项，按目标场景切换。

### `PAYMENT_MOCK` 和 `PAYMENT_NATIVE` 不是苹果字段

有的 Flutter 项目自行定义：

```text
PAYMENT_NATIVE=true
PAYMENT_MOCK=false
```

意思是启用真实原生支付、关闭本地假结果。**它不等于正式扣款开关。** Apple Sandbox 也需要调用真实 StoreKit。另一些项目可能没有这些字段，应查实际配置实现，不能照抄变量名当成通用方案。

### 业务测试环境与苹果沙盒互不等同

`api-test.example.com` 是你公司的后端环境；Sandbox 是苹果的交易环境。正式业务服务器也可能接收到苹果沙盒交易，因此服务端要明确测试账号、沙盒准入及权益隔离。

“苹果不扣款”不代表没有副作用：业务后台仍可能写订单、增加真实数据库中的会员期限。联调前确认使用的测试账号及处理规则。

## 10. 沙盒测试账号如何准备

1. 有权限的成员进入 **App Store Connect → Users and Access → Sandbox**。
2. 新建 Sandbox Apple Account，使用符合官方要求、未被正常 Apple 账号使用的邮箱，配置密码和商店地区。
3. 开发签名真机测试时，通过 iOS 对应的 Sandbox 入口登录。较新系统通常在 Settings → Developer → Sandbox Apple Account；不同版本的出现条件和入口可能不同。
4. 不要把它当作日常 iCloud 账号登录，也不必为了开发包沙盒测试退出整台手机的 iCloud。
5. 测试时确认购买环境和交易环境确实为 Sandbox，再核对服务端验单。

**TestFlight 内购同样使用沙盒，不实际收费。** TestFlight 通常可用正常 Apple 账号完成沙盒购买，不是所有测试者都必须另建 Sandbox Apple Account；需要专门的沙盒控制功能时，按苹果对 TestFlight 的当前说明配置。

沙盒测试者用于控制地区、购买历史及自动续订测试等。订阅在沙盒可加速，不要把固定的几分钟周期写死为永远不变的苹果规则，按当前测试设置判断。

## 11. 沙盒商品和正式商品通常是同一份配置

通常在 App Store Connect 创建一次商品，使用同一 Product ID 做 Sandbox 测试和正式销售，**不是必须创建 `vip.sandbox` 与 `vip.production` 两套 ID**。

不同的是交易环境、账号、交易记录、扣款行为及审核销售条件。商品资料可用于沙盒查询，不代表它已经通过审核、已在目标地区销售，或商务配置全部满足正式要求。

## 12. 必测场景

![内购取消、待处理、网络中断与补单流程](/apple-development/payment-recovery.svg)

| 场景 | 必须观察的结果 |
| --- | --- |
| 正常购买 | 交易、业务订单和用户关联正确，只发一次权益 |
| 购买窗口取消 | 不显示成功、不发权益，页面恢复可操作 |
| `.pending` | 等待更新，不能把按钮恢复为自动重复扣款入口 |
| 苹果成功后后端断网 | 不盲目重购；恢复后按同一交易补验 |
| 关闭页面 / 杀进程后重开 | 未完成交易能够恢复，页面生命周期不丢订单 |
| 重复回调、重复验单 | 权益幂等，不重复发放 |
| 切换业务账号 | 不把旧账号订单错误绑定给新账号 |
| 恢复购买 | 按商品类型和账号归属合同恢复；不能直接给新账号复制全部权益 |
| 退款 / 撤销 / 订阅到期 | 通知或补偿查询能按业务规则更新权益 |
| 商品不可用或配置错误 | 明确提示，不使用假价格、假商品或假成功兜底 |

## 本章验收

一次完整证据应能串起：**Bundle ID → Product ID → 业务订单号 → 苹果交易号 → 交易环境 → 用户权益变化**。只截到购买弹窗、Mock 成功或客户端 `.success` 都不代表服务端闭环已完成。

官方依据：[内购商品字段](https://developer.apple.com/help/app-store-connect/reference/in-app-purchases-and-subscriptions/in-app-purchase-information/) · [创建沙盒账号](https://developer.apple.com/help/app-store-connect/test-in-app-purchases/create-a-sandbox-apple-account/) · [StoreKit](https://developer.apple.com/documentation/storekit) · [App Store Server API](https://developer.apple.com/documentation/appstoreserverapi) · [App Store Server Notifications](https://developer.apple.com/documentation/appstoreservernotifications)

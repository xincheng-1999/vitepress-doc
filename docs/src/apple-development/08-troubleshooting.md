# 08 排查手册、交接清单与术语

## 1. 从失败发生在哪一层开始

```text
进不了后台 → 登录身份 / 邀请 / 团队 / 角色 / 资源访问
构建不过   → 编译错误 / SDK / 依赖 / 构建配置
签名不过   → 团队 / Bundle ID / 证书私钥 / 描述文件 / 能力
安装失败   → 设备登记 / 签名 / 分发方式 / 系统兼容性
启动失败   → 原生崩溃 / SDK 初始化 / 运行时配置
页面异常   → H5 来源 / 网络 / JS Bridge / 登录状态
购买异常   → 苹果商品 / StoreKit 环境 / 用户动作 / 交易状态
权益没到   → 后端验单 / 订单关联 / 幂等 / 数据落库 / 资料刷新
无法发布   → 构建处理 / 商店资料 / 审核 / 协议 / 发布设置
```

“支付失败”太宽泛。先回答有没有创建业务订单、有没有打开购买窗口、有没有苹果交易号、服务端返回什么、权益是否已存在。

## 2. 常见问题速查

| 症状 | 核对点 | 不应该先做什么 |
| --- | --- | --- |
| 接受邀请后无 App | 当前账号、团队、邀请状态、App Access | 立即购买个人会员 |
| Developer 报 Access Unavailable | 个人/组织会员类型及证书资源授权 | 根据邀请邮件就声称有开发权限 |
| Xcode 没有团队 | Xcode 是否登录了受邀账号，成员权限是否已生效 | 认为 Chrome 登录会自动同步给 Xcode |
| No signing certificate / private key | 本机是否有有效证书及配套私钥 | 只反复下载 `.cer` |
| Not in Keychain | 当前 Mac 没有对应本地身份，是否有另一张可用证书 | 撤销其他同事仍在使用的证书 |
| No profiles / Bundle ID unavailable | 团队是否有权使用该 Bundle ID，设备与能力是否匹配 | 改成别人的包名碰运气 |
| Entitlements 不匹配 | App ID 能力、工程声明和描述文件一致性 | 删除真实需要的能力来隐藏问题 |
| 手机无法安装 IPA | 包是否为设备构建，签名类型、UDID、系统要求 | 把模拟器 `.app` 压成 IPA |
| 本地 H5 加载失败 | 手机可达地址、HTTPS、ATS、网络权限、服务器监听 | 把 Android 的 `adb reverse` 当作 iOS 通用方案 |
| StoreKit 商品为空 | Bundle ID、Product ID、商品资料、销售地区、协议及环境 | 编造商品 ID 或用业务价格伪装苹果查询成功 |
| 苹果提示成功，后端查不到 | 本地 StoreKit 与 Sandbox 是否混淆、查询环境、API 凭据中的 App 身份 | 直接让用户再买一次 |
| Bundle ID mismatch | 交易归属与服务端预期 App 是否一致 | 删除 App 身份校验 |
| 反复加会员 | 重复回调/重试是否缺少幂等，事务是否可靠 | 只在页面禁用按钮当作完整防重 |
| 切账号后权益串了 | 交易、appAccountToken、业务订单及用户关联 | 把所有历史交易发给当前用户 |
| 上传成功却 TestFlight 无构建 | 是否处理完成、被拒、合规资料待补、选错 App | 不断重复上传同一个构建号 |

## 3. 三种常见接入方案怎么选

### A：完全复用已有 App 身份联调

使用已有 App 的 Bundle ID、团队签名、苹果商品和服务端配置。适用于先验证客户端流程，但团队应明确这是测试包；安装可能覆盖同身份的已有 App，测试账号和数据也要明确。

### B：新 App 身份，复用同一套服务端

使用新 Bundle ID 和自己的苹果商品。后端要支持按可信业务上下文选择 App 配置、凭据、商品和权益，不是把固定包名从 A 改成 B。否则可能让旧 App 支付失效。

### C：先做本地 Mock / StoreKit 测试

适合先验证 UI、取消和状态机，不需要立即完成真实沙盒验单；但必须标记模拟边界，不能把结果作为上线依据。

## 4. 向管理员申请权限，可以这样写

```text
我要参与 [App 名称 / Bundle ID] 的 iOS 开发和真机调试。
请邀请我的工作 Apple 账号，并确认：
1. 目标开发团队及 Team ID；会员是个人还是组织。
2. 适合开发职责的角色和目标 App 访问范围。
3. 若是组织成员，开放所需 Certificates, Identifiers & Profiles 权限。
4. 团队是否限制自动注册 App ID 或设备；测试设备由谁登记。
5. 内购测试商品及沙盒测试者由谁维护。
不需要提供其他人的账号密码。
```

若对方是个人会员，单独邀请 App Store Connect 成员不提供开发团队权益。应与负责人决定受控的签名协作或长期组织化管理方案，不能反复发送相同邀请期待得到不同权限。

## 5. 与后端联调前的配置表

| 项目 | 要确认的事实 | 负责方 |
| --- | --- | --- |
| App 身份 | Bundle ID、Team、商店数字 App Apple ID | iOS / 管理员 |
| API 调用 | 业务环境、客户端标识、账号和会话 | 客户端 / 后端 |
| 商品 | 页面别名、业务 SKU、苹果 Product ID、类型和权益 | 产品 / 客户端 / 后端 |
| 苹果凭据 | 对应 App 与账号的 API Key、Key ID、Issuer ID 等 | 后端 / 管理员 |
| 交易验证 | Sandbox 与 Production 路由、校验规则 | 后端 |
| 账号关联 | appAccountToken 如何对应业务账号或订单 | 客户端 / 后端 |
| 通知 | 生产/沙盒通知 URL、验签、重复处理和补偿 | 后端 |
| 测试数据 | 沙盒准入、UID 白名单、权益发放范围 | 后端 / 测试 |

这张表填“谁管理、是否已配置”，不填真实私钥、密码和令牌。商品字段也应区分“后台已配置”和“StoreKit 本次查询成功”。

## 6. 最小验收清单

### 开发准备

- [ ] 登录身份与目标团队确认，权限从界面或管理员记录核实。
- [ ] Bundle ID、能力、证书和描述文件匹配。
- [ ] 模拟器与真机的构建、安装、运行分别记录。
- [ ] 真机当前版本、构建号、业务域名和 H5 来源明确。

### 支付联调

- [ ] 商品由苹果成功查询，价格和类型正确。
- [ ] 创建业务订单，Apple Sandbox 购买和后端验单成功。
- [ ] 权益正确落库、刷新；重复处理没有重复发放。
- [ ] 取消、pending、断网、重启、切账号均有正确行为。
- [ ] 按支持范围验证恢复、退款、订阅到期及服务端通知。

### 发布

- [ ] 正式业务配置、隐私声明及第三方 SDK 行为一致。
- [ ] 上传、处理、TestFlight、审核、发布状态分别核实。
- [ ] 商店素材、审核账号和说明、合规及商务信息齐全。
- [ ] 保留符号、构建记录，准备监控和修复版本流程。

## 7. 只读命令速查

在可信的本地工程和产物上执行，路径替换为实际值。输出可能包含团队或设备标识，不要原样公开。

```bash
# 工具版本和当前命令行工具位置
xcodebuild -version
xcode-select -p

# 有效签名身份，不导出私钥
security find-identity -v -p codesigning

# 查看已构建 App 的签名元信息
codesign -dvv '/实际路径/Runner.app'

# 读取安装包 Bundle ID
/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' '/实际路径/Runner.app/Info.plist'

# 查看设备与 Flutter 工具情况
flutter devices
flutter doctor -v
```

不要把 `codesign -d` 的显示信息当成完整验签或真机运行结果。任何检查只证明它覆盖的那一层。

## 8. 术语对照

| 术语 | 中文理解 | 别和什么混淆 |
| --- | --- | --- |
| Apple Account | 苹果登录账号 | 沙盒账号、业务用户 |
| Team ID | 开发团队标识 | 证书名称括号里的任意编号 |
| Bundle ID | App 技术身份 | API 域名、商品 ID |
| App ID Prefix | 签名 App 标识前缀 | 不能永远假设等于 Team ID |
| App Apple ID | 商店 App 的数字 ID | 登录邮箱 |
| SKU | 商店或业务内部库存/产品标识，须看上下文 | 苹果 Product ID |
| Product ID | 苹果内购商品标识 | 业务订单号 |
| appAccountToken | 开发者传给苹果的 UUID 关联标识 | 业务登录访问令牌、苹果账户密码 |
| transactionId | 一笔苹果交易的标识 | 业务 tradeNo |
| originalTransactionId | 原始交易标识，订阅常用于关联交易链 | 每次续费新交易的 transactionId |
| Entitlement | 苹果签名能力或 StoreKit 权益，须看上下文 | 不能一律理解成后台会员字段 |
| Provisioning Profile | 签名授权描述文件 | 私钥 |
| JWS | 可签名验证的结构化数据格式 | Base64 解码成功不代表验签成功 |
| JWT | 常用于服务端 API 身份凭据的 token 格式 | 不是可公开复用的永久访问密码 |
| StoreKit | 苹果内购原生框架 | Apple Pay 银行卡支付 |
| Sandbox | 苹果测试交易环境 | 公司测试服务器 |
| TestFlight | 苹果测试分发渠道 | Xcode 模拟器、正式商店发布 |

## 9. 官方参考与更新边界

本教程于 2026-09-21 阅读核对了以下帮助文档中的账号权限、商品字段、沙盒账号及 TestFlight 条件；链接是后续查证入口，不代表所有操作已在某个真实团队执行。

- [团队角色与权限](https://developer.apple.com/help/account/access/roles/)
- [添加和编辑 App Store Connect 用户](https://developer.apple.com/help/app-store-connect/manage-your-team/add-and-edit-users/)
- [证书总览](https://developer.apple.com/help/account/certificates/certificates-overview/)
- [创建开发描述文件](https://developer.apple.com/help/account/provisioning-profiles/create-a-development-provisioning-profile/)
- [创建 App 记录](https://developer.apple.com/help/app-store-connect/create-an-app-record/add-a-new-app/)
- [内购商品字段](https://developer.apple.com/help/app-store-connect/reference/in-app-purchases-and-subscriptions/in-app-purchase-information/)
- [创建 Sandbox Apple Account](https://developer.apple.com/help/app-store-connect/test-in-app-purchases/create-a-sandbox-apple-account/)
- [TestFlight 总览](https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview/)
- [StoreKit API 文档](https://developer.apple.com/documentation/storekit)
- [App Store Server API](https://developer.apple.com/documentation/appstoreserverapi)
- [App Store Server Notifications](https://developer.apple.com/documentation/appstoreservernotifications)
- [App 审核指南](https://developer.apple.com/app-store/review/guidelines/)
- [即将生效的提交要求](https://developer.apple.com/news/upcoming-requirements/)

真实问题的记录应包含日期、版本和证据。以后页面变了，更新路径与截图即可；不要把某台电脑的当前账号、某款 App 的固定包名或某个测试商品当成所有项目的规则。

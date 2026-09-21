# 01 账号、会员与团队权限

## 1. 先分清四个维度

用 GitHub 作类比：你自己的 GitHub 登录账号，可以加入公司的 Organization；加入后能访问哪些仓库、能否发布，由权限决定。Apple 体系也要分开看「你是谁」「团队是什么类型」「你在团队是什么角色」「你能访问哪些资源」。

| 维度 | 例子 | 含义 |
| --- | --- | --- |
| Apple Account，旧称 Apple ID | 你的邮箱账号 | 登录身份，不等于付费开发者会员 |
| 会员类型 | 个人、组织、企业开发者计划 | 加入的是哪一种开发者计划 |
| 团队与角色 | Team A 的 Developer，Team B 的 Admin | 在指定团队能承担哪些工作 |
| 资源范围 | 指定 App、全部 App、证书与描述文件权限 | 实际能看到和操作什么 |

同一个 Apple 账号可以属于多个团队。你不需要把账号「改成公司账号」，也不必为了加入组织团队再买一份个人会员。应由团队邀请你自己的账号，避免多人共用账号、密码和验证码。

## 2. 不同会员类型的区别

| 类型 | 常见用途 | 团队协作与发布边界 |
| --- | --- | --- |
| 免费 Apple 账号 / Xcode Personal Team | 学习、模拟器、有限的个人设备调试 | 能力和签名时效受限，不能据此直接发布到 App Store |
| Apple Developer Program：个人会员 | 个人开发者发布 App | 商店销售主体通常是个人法定姓名；受邀 App Store Connect 用户不因此成为开发者计划团队成员 |
| Apple Developer Program：组织会员 | 公司或其他合格组织发布 App | 按组织身份发布，可以管理开发团队成员及对应资源权限 |
| Apple Developer Enterprise Program | 符合资格的组织向内部员工分发专有 App | 不是公开 App Store 发布的高级版本，不能当作绕过商店审核的通用分发方式 |

个人与组织不是「开发能力低配 / 高配」。两者都能开发正常的商店 App，主要差异在法律主体、销售展示及团队管理。组织报名通常要核验法律实体、授权人等资料；具体资格、费用和续费信息以苹果当前报名页为准。

**不能因为团队名称是人名就断定它是个人会员，也不能因为公司在用就断定它是组织会员。** 应查看 Apple Developer 的 Membership details，而不是根据邮箱域名、邀请文案或昵称猜。

## 3. 角色不是账号类型

| 角色 | 主要用途 | 容易误解的地方 |
| --- | --- | --- |
| Account Holder（账户持有人） | 持有会员、处理关键协议及账户责任 | 一个会员对应一个持有人，不等于所有管理员 |
| Admin（管理员） | 广泛管理团队、App 和资源 | 与 Account Holder 仍有权限区别；一个团队的 Admin 不会自动成为另一个团队的 Admin |
| App Manager（App 管理员） | 管理分配给自己的 App、版本和发布工作 | 不代表所有开发签名资源天然可用 |
| Developer（开发者） | 上传构建、开发与测试相关工作 | 组织下还要核对证书等额外资源权限 |
| Marketing / Sales / Finance / Customer Support | 营销、销售、财务、客服相关工作 | 能看销售报表不等于能签名，能上传构建也不等于能签协议 |

这张表只概括工作方向，具体某个按钮谁能操作，应查 [官方角色权限矩阵](https://developer.apple.com/help/account/access/roles/)。团队还可能启用 Automatic Signing Controls，进一步限制开发者注册 App ID、设备等操作。

## 4. 一次正确的邀请过程

### 管理员做什么

1. 登录 [App Store Connect](https://appstoreconnect.apple.com/)，确认选中了正确团队。
2. 进入 **Users and Access（用户和访问）→ People（人员）**，添加用户。
3. 填写收件邮箱，分配符合职责的角色。
4. 如可按 App 限制访问，勾选实际需要的 App。
5. 如果是组织成员需要开发签名，核对 **Certificates, Identifiers & Profiles** 访问权限。
6. 发送邀请。

注意：苹果文档说明，获得证书、标识符和描述文件等资源权限的用户，其 App 信息访问范围不能再按指定 App 限制。管理员应理解权限范围后授予，不要为了省事让所有人都成为 Admin。

### 被邀请人做什么

1. 打开邀请邮件，核对邀请方；邀请通常在发出后 3 天失效。
2. 点击接受，使用要长期工作的 Apple 账号完成激活和登录。
3. 完成本人账号的双重认证；如果验证码发往不认识的号码，先核对登录账号，别把它当作团队强制共用的号码。
4. 在 App Store Connect 检查团队、App 和个人资料。
5. 需要签名时，再检查 Apple Developer 的证书入口，以及 Xcode 的团队列表。

官方允许在激活时使用已有 Apple 账号。**不要只根据邀请收件邮箱推断最终绑定身份**，管理员的成员记录、网页当前登录账号和 Xcode 登录账号要一起核对。

## 5. 为什么接受邀请后还是看不到 App / 证书

按这个顺序排查，不要先付费报名：

| 看见的现象 | 先确认什么 | 怎么继续 |
| --- | --- | --- |
| 仍在短信验证码页面 | 尚未完成该网站登录 | 完成本人账号的双重认证 |
| 登录成功但没有目标 App | 是否选错团队、App 访问范围是否分配 | 让管理员核对该成员的 App Access |
| 邀请仍为待接受 | 是否过期、是否完成激活、最终绑定账号 | 按管理员看到的实际状态处理或重发 |
| Developer 显示 Join the Apple Developer Program | 当前会话没有显示相应会员资源 | 继续核对团队和资源授权，不能只凭这句话断定必须自己购买会员 |
| 证书页显示 Access Unavailable | 当前账号没有可用的相应资源访问 | 核对会员类型、团队关系、额外权限 |
| App Store Connect 能管理 App，Developer 却进不去 | 是否是个人会员发出的邀请 | 个人会员邀请只提供 App Store Connect 访问，不附带开发者团队签名权益 |
| Xcode 不显示新团队 | 是否添加了接受邀请所用的账号 | 先检查 Accounts 与网络、刷新团队信息，确认权限后再考虑重新登录 |

不能把「当前进不去」直接归结为苹果同步慢。先有明确的成员和授权记录，再排查同步。

## 6. Xcode 里看到什么才有用

进入 **Xcode → Settings → Apple Accounts**（旧版可能叫 Accounts），选择账号，再选择团队。

![Xcode 团队权限页面，标注账号入口、角色、证书权限和管理按钮](/apple-development/xcode-team-annotated.png)

图中编号：

1. **Apple Accounts**：Xcode 的账号列表独立于 Chrome 登录态。
2. **Role**：当前账号在当前团队的角色，不能跨团队套用。
3. **Certificates, Identifiers & Profiles**：签名资源权限提示。
4. **Manage Certificates / Download Manual Profiles**：查看证书、下载手工描述文件的入口。

截图中的团队姓名、图标和设备数量已遮盖；它是界面教学样本，不表示读者自动拥有同样权限。角色和勾选项齐全后，还需检查实际 App ID、设备以及本机证书私钥，详见 [03 签名](/apple-development/03-signing)。

## 7. 还有一个“沙盒账号”，别混用

| 账号 | 登录位置 | 用途 |
| --- | --- | --- |
| Apple 开发者账号 | Xcode、Developer、App Store Connect | 开发、配置、签名和发布 |
| Sandbox Apple Account | iPhone 的沙盒测试入口 | 模拟苹果内购 |
| App 业务账号 | 你自己 App 的手机号等登录页 | 对应业务数据库用户和会员权益 |

沙盒账号不是加入团队的账号，也不能用它登录 Xcode 来获得签名权限。详见 [06 内购与沙盒](/apple-development/06-in-app-purchase)。

## 本章验收

你应能填出：**网页登录账号 / Xcode 登录账号 / 目标团队名称和 Team ID / 自己的角色 / 目标 App / 证书资源权限**。任何一项不清楚，都先核对事实，暂不修改证书或包名。

官方依据：[添加和编辑用户](https://developer.apple.com/help/app-store-connect/manage-your-team/add-and-edit-users/) · [角色与权限](https://developer.apple.com/help/account/access/roles/)

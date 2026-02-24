---
title: 第十六章 打包上架全流程（Play Console + TestFlight + App Store）
---

# 第十六章：打包上架全流程（Play Console + TestFlight + App Store）

## 16.1 本章目标（验收标准）
完成后你需要能：
- 产出 Android AAB 并上传到 Google Play Console（至少内部测试）
- 产出 iOS Archive 并上传到 TestFlight
- 搭建一套“发布前检查清单”，降低 release-only 风险
- 明确 Android/iOS 上架差异与常见审核/上传失败原因

---

## 16.2 核心概念：上架不是“打包”，是“交付流程”
你需要同时保证：
- 版本号策略正确
- 签名/证书正确
- 隐私与权限说明完整
- 关键路径回归通过
- 产物可复现（最好有 CI）

**Web 对比：**
- Web 发版通常是 CI 构建 + 部署
- 移动端发版 = 构建 + 签名 + 控制台配置 + 审核 + 灰度 + 回滚策略

---

## 16.3 Android 上架（Google Play）完整流程

### 16.3.1 前置准备
- Google Play Developer 账号
- 上传密钥/签名策略
  - 推荐使用 Play App Signing（由 Google 托管 app signing key）

### 16.3.2 版本号策略（必须理解）
- `pubspec.yaml`：`version: x.y.z+build`
  - `x.y.z`：versionName
  - `build`：versionCode（必须递增）

### 16.3.3 生成 AAB
```powershell
flutter build appbundle --release
```
产物：
- `build/app/outputs/bundle/release/app-release.aab`

### 16.3.4 在 Play Console 创建应用并上传
1) Play Console → Create app
2) App details：名称、默认语言、应用类型
3) Release → Testing（Internal/Closed/Open）
4) 创建一个 Internal testing release
5) Upload AAB
6) 填写 release notes
7) 添加测试人员（邮箱/群组）

### 16.3.5 上架前必填项（新手最常漏）
- Data safety（数据安全表单）
- Privacy policy（如果涉及网络、账号、采集信息，通常需要）
- App content：目标受众、广告声明
- Screenshots、Feature graphic（素材要求）

---

## 16.4 iOS 上架完整流程（TestFlight → App Store）

### 16.4.1 前置准备
- Apple Developer Program
- App Store Connect 账号权限
- Bundle ID（不要轻易改）

### 16.4.2 Xcode Archive（最常见流程）
1) Xcode 打开 `ios/Runner.xcworkspace`
2) 选择 `Any iOS Device (arm64)` 或真机
3) Product → Archive
4) Archive 完成后：Distribute App
5) 上传到 App Store Connect

### 16.4.3 TestFlight 内测
1) App Store Connect → TestFlight
2) 添加构建版本
3) 邀请 Internal Testers / External Testers
4) 外部测试通常需要审核

### 16.4.4 提交 App Store
- App Store Connect → App 信息、版本信息
- 截图、隐私信息、用途说明
- 提交审核

---

## 16.5 实战：做一份“发布前检查清单”（建议直接复制到你的项目）

### 16.5.1 功能回归（必须）
- 启动、登录（如有）、首页渲染
- 笔记增删改查（SQLite）
- 相机/相册（权限 + 拒绝流程）
- 网络页面（dio）：弱网/无网/超时提示

### 16.5.2 配置检查（必须）
- 版本号（build number 递增）
- Android：包名、签名、权限
- iOS：Bundle ID、Team、Info.plist 用途说明

### 16.5.3 性能与稳定性
- 真机 release 包回归（不是 debug）
- 关键页面滚动/图片加载不卡顿

### 16.5.4 合规与隐私
- 权限用途说明（相机/相册）
- 隐私政策链接（如有数据收集/登录/统计）

---

## 16.6 CI（可选但强烈建议）：让产物可复现
目标：避免“我本机能打包，你机器不行”。

最小思路：
- Android：GitHub Actions Linux runner 打 AAB
- iOS：GitHub Actions macOS runner 打 archive（或 fastlane）

你不需要一开始就做到全自动上架，但至少做到：
- 任何人拉代码都能构建出同样产物

---

## 16.7 实战小练习（必须做）

### 练习 A：Android 内部测试
- 生成 AAB
- 上传 Internal testing
- 邀请自己邮箱安装

验收：
- 能通过 Play 提供的安装链接安装
- 功能正常

### 练习 B：iOS TestFlight（如果你有 macOS）
- Xcode Archive 并上传
- 添加一个 internal tester
- 安装 TestFlight 版本

验收：
- 能从 TestFlight 安装并运行

---

## 16.8 常见坑
- Android：versionCode 没递增 → 上传失败
- Android：签名丢失/换 keystore → 无法更新已上架应用
- iOS：用途说明不完整 → 运行崩/审核被拒
- iOS：签名配置混乱（多个证书/profile）→ Archive/上传失败
- 只测 debug：release-only 问题上线后才暴露

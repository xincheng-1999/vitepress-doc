---
title: 前端工程师转 Flutter 移动端开发保姆级教程
---

# 教程总览（2~3 个月路线图）

## 你将最终做到什么
- 能独立开发并发布一款 Flutter App（Android / iOS）
- 能在真实业务中完成：路由、状态管理（Riverpod）、网络（dio）、本地存储、权限、相机/相册、打包发布
- 能读懂并维护一套“可扩展、可测试”的 Flutter 工程结构

## 适用人群
- 有 Web 前端经验（React/Vue 任一）
- 0 移动端基础也没关系
- 希望以实战为主，做出能跑、能交付的 App

## 学习方式（强烈建议照做）
1) 每章照着做“本章实战”，把代码跑起来
2) 做完“实战小练习”再进入下一章
3) 遇到坑先看“常见坑”，再去查官方文档

## 贯穿项目：随手记·移动端（Notes）
本教程会用一个统一项目贯穿全程：
- 笔记列表 + 新增/编辑/删除
- 支持图片（相机拍照 / 相册选择）
- 本地存储（SQLite）
- 可选：登录后拉取远端数据（dio）
- 权限、错误处理、分层架构、测试

## 章节导航
- [第一章：环境搭建 + 第一个 Flutter App](./01-setup-first-run.md)
- [第二章：Dart 基础（面向前端工程师）](./02-dart-for-fe.md)
- [第三章：Widget 心智与 Flutter 布局系统](./03-widget-layout-core.md)
- [第四章：资源、字体、主题与多端适配基础](./04-assets-font-theme.md)
- [第五章：路由与导航（go_router）](./05-navigation-routing.md)
- [第六章：状态管理（Riverpod）从 0 到可维护](./06-riverpod-state.md)
- [第七章：异步与网络（dio 实战）](./07-async-network-dio.md)
- [第八章：表单、校验、输入与交互细节](./08-forms-validation.md)
- [第九章：本地存储与缓存（偏实战）](./09-storage-cache.md)
- [第十章：权限管理（Android/iOS 差异）](./10-permissions.md)
- [第十一章：相机 + 相册选图（完整链路）](./11-camera-gallery.md)
- [第十二章：工程化与架构（分层、错误处理、可测试）](./12-architecture-testing.md)
- [第十三章：打包发布与平台差异清单](./13-build-release-platform.md)
- [第十四章：Android 开发必备 + 调试](./14-android-dev-debug.md)
- [第十五章：iOS 开发必备 + 调试](./15-ios-dev-debug.md)
- [第十六章：打包上架全流程（Play Console + TestFlight + App Store）](./16-release-to-stores.md)

## 你需要准备的环境
- Windows / macOS 均可（本教程会显式标注 Android/iOS 差异）
- Flutter stable（推荐 3.x）
- Android Studio（Android SDK + Emulator）
- iOS 方向需要 macOS + Xcode（Windows 无法本机打 iOS 包）

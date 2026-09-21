# VitePress 技术笔记文档

一个基于 VitePress 构建的个人技术笔记项目，记录前端、后端、IT技术等领域的知识总结和学习心得。

## 🏠 项目主页

[在线访问](https://xincheng-1999.github.io/vitepress-doc/)

## 📁 项目结构

```
vitepress-doc/
├── docs/                    # 文档根目录
│   ├── src/                # 源码目录
│   │   ├── back-end/       # 后端技术
│   │   │   ├── java/       # Java 学习笔记
│   │   │   └── database/   # 数据库技术
│   │   ├── front-end/      # 前端技术
│   │   │   ├── interview-questions/  # 面试题
│   │   │   ├── vue/        # Vue.js
│   │   │   ├── react/      # React
│   │   │   └── the-basics/ # 前端基础
│   │   ├── IT-technology/  # IT技术
│   │   │   ├── git.md      # Git使用
│   │   │   ├── adb.md      # ADB调试
│   │   │   └── net-proxy.md # 网络代理
│   │   ├── python-study/   # Python学习
│   │   ├── rust-study/     # Rust学习
│   │   ├── Reading-feelings/ # 读书感悟
│   │   └── public/         # 静态资源
│   └── .vitepress/         # VitePress配置
├── package.json            # 项目依赖配置
└── README.md              # 项目说明
```

## 🚀 技术栈

- **框架**: VitePress 1.3.2
- **构建工具**: Vite
- **动画库**: GSAP 3.12.7
- **Live2D**: oh-my-live2d 0.19.3
- **包管理**: npm / pnpm

## 📚 内容分类

### 后端技术
- **Java**: 基础语法、Spring Boot、Maven等
- **数据库**: MySQL、MongoDB、Redis、ORM框架
- **数据库基础**: 关系型 vs NoSQL、基本概念

### 前端技术
- **苹果开发全流程**：[独立教程](docs/src/apple-development/index.md)，面向前端开发者讲解账号权限、后台配置、证书签名、真机调试、内购沙盒、TestFlight 与上架，包含脱敏标注截图和支付时序图。
- **面试题**: 手写代码、项目优化、Vue相关问题
- **框架**: Vue.js、React、Redux
- **基础知识**: 浏览器原理、网络协议、TypeScript
- **工具**: Node.js、npm、nvm

### IT技术
- **开发工具**: Git、ADB、网络代理
- **系统相关**: Mac、Windows使用技巧
- **内网穿透**: 网络技术

### 编程语言
- **Python**: 基础语法、常用技巧
- **Rust**: 环境配置、基础语法、Cargo使用

## 🛠️ 开发命令

```bash
# 开发环境
npm run docs:dev

# 构建生产版本
npm run docs:build

# 预览构建结果
npm run docs:serve
```

## ✨ 特色功能

- 响应式设计，支持移动端访问
- 内置Live2D角色动画
- GSAP动画效果
- 代码高亮和语法提示
- 搜索功能
- 深色/浅色主题切换

## 📝 更新日志

项目持续更新中，记录个人学习和技术成长历程。

## 🤝 贡献

欢迎提出建议和改进意见！

## 📄 许可证

ISC License


# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目说明

基于 **VitePress 1.3.2** 构建的个人中文技术笔记站点。内容位于 `docs/src/`，发布到 GitHub Pages：`https://xincheng-1999.github.io/vitepress-doc/`。

## 常用命令

请使用 **pnpm**——CI（`.github/workflows/deploy.yml`）在 Node 20、pnpm 9.7.1 下运行，且使用 frozen lockfile。混用 npm 会导致 `pnpm-lock.yaml` 失同步。

```bash
pnpm install --frozen-lockfile
pnpm docs:dev      # vitepress dev docs --host
pnpm docs:build    # 产物输出到 docs/.vitepress/dist
pnpm docs:serve    # 本地预览构建产物
```

`scripts/` 下的辅助脚本（用 `python` 直接运行，无额外依赖）：

```bash
python scripts/vp_check_links.py          # 检查 *.md 中的绝对路径死链，输出 broken-links.json
python scripts/vp_check_config_links.py   # 校验 nav.js / sidebar.js 中的链接，输出 config-broken-links.json
python scripts/vp_unused_assets.py        # 列出 public/ 中未被任何 markdown 引用的资源
```

仓库没有测试套件、lint 配置和格式化工具——不要凭空编造命令。

## 架构说明

### 内容结构

- VitePress 设置了 `srcDir: "src"`，所有路由都相对于 `docs/src/` 解析。例如 `docs/src/back-end/java/01-environment.md` 对应路由 `/back-end/java/01-environment`（链接里 `.md` 后缀可省略，仓库中大多链接也确实省略了）。
- `docs/src/` 下的顶层内容目录：`back-end/`、`front-end/`、`flutter/`、`IT-technology/`、`python-study/`、`rust-study/`、`llm-study/`、`Reading-feelings/`。首页是 `docs/src/index.md`。
- 静态资源放在 `docs/src/public/`，在 markdown 中以 `/<asset>` 引用（base 路径会被自动加上）。

### 路由（容易踩坑的部分）

`base` 是 `/vitepress-doc/`（config.js:11）。所有**不在 markdown 正文**里硬编码的 URL，都必须手动带上这个前缀，仓库现有的例子：

- `head` 中的 favicon：`/vitepress-doc/favicon.ico`（config.js:13）
- `docs/.vitepress/theme/index.js` 中 Live2D 的 `modelPaths` 用的是 GitHub Pages 的完整 URL。

而 markdown 链接和 `public/` 资源引用**不要**带 base——VitePress 会自动加上。

### 导航与侧边栏

由两个纯 JS 文件驱动：

- `docs/.vitepress/nav.js`——顶部导航。
- `docs/.vitepress/sidebar.js`——对象，**key 是路由前缀**（例如 `"/back-end/database/"`）。匹配前缀决定每个页面显示哪棵侧边栏树；新增板块需要在这里加新 key。

新增需要出现在导航里的页面时，要同步更新这两个文件。改完用上面的链接检查脚本确认没坏——`vp_check_config_links.py` 就是专门校验这两个配置的。

### 主题定制

`docs/.vitepress/theme/index.js` 扩展了 VitePress 默认主题：

- 注册了全局组件 `<HomePage>`（由 `index.md` 使用），并在 `layout-bottom` 插槽插入 `ThemeSwitch`。
- 在 `enhanceApp` 中初始化 `oh-my-live2d`。任何用到 `window`/`document` 的代码必须用 `if (!import.meta.env.SSR) { ... }` 保护，否则构建会失败。
- 新增 Live2D 模型到 `docs/src/public/models/<name>/model.json` 后，需要把路径加进 `modelPaths` 数组（目前是硬编码的 GitHub Pages 完整 URL）。

样式调整放在 `docs/.vitepress/theme/custom.css`——优先覆盖已有的 `--vp-*` CSS 变量，不要引入新的样式体系。

### Markdown + Vue

VitePress 允许在 `.md` 文件里嵌入 `<script setup>` 和 `<style>` 块（首页 `docs/src/index.md` 就是例子）。嵌入脚本要保持轻量、避免全局副作用，并在 `onBeforeUnmount()` 中清理事件监听和定时器。

## 部署

向 `main` 分支 push 会触发 `.github/workflows/deploy.yml`，它用 pnpm 构建并把 `docs/.vitepress/dist` 发布到 GitHub Pages。没有预发布环境——合到 `main` 就直接上线。

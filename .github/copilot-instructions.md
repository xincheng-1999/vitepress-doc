# Copilot 指南（vitepress-doc）

## 项目概览
- 本仓库是一个 VitePress 站点。
- 站点根目录为 `docs/`（脚本以 `vitepress * docs` 形式调用）。
- 文档内容位于 `docs/src/`（见 `docs/.vitepress/config.js` 中 `srcDir: "src"`）。
- 导航配置拆分为：`docs/.vitepress/nav.js`（顶部导航）与 `docs/.vitepress/sidebar.js`（侧边栏）。

## 开发流程（以 CI 为准）
- 优先使用 **pnpm**（CI 使用 pnpm + `pnpm-lock.yaml`，见 `.github/workflows/deploy.yml`）。
- CI 的 Node 版本为 **20**。
- 常用命令：
  - `pnpm install --frozen-lockfile`
  - `pnpm docs:dev`（启动开发服务器）
  - `pnpm docs:build`（构建产物输出到 `docs/.vitepress/dist`）
  - `pnpm docs:serve`（本地预览构建产物）

## 路由、链接与静态资源
- `base` 为 `/vitepress-doc/`（GitHub Pages）。如果调整 `base`，同时检查/更新主题或 head 中硬编码的 URL。
- 站内链接建议使用“绝对路由”形式，例如 `/back-end/database/mysql/installation`（本仓库很多链接省略 `.md`）。
- 公共静态资源放在 `docs/src/public/`，引用方式为 `/<asset>`（例如首页使用 `/Ironman.png`）。

## 主题与自定义行为
- 主题入口在 `docs/.vitepress/theme/index.js`，基于 `vitepress/theme` 扩展。
- 客户端专用代码（使用 `window/document` 等）需要在 SSR 下保护：本仓库使用 `if (!import.meta.env.SSR) { ... }`。
- Live2D：在 `enhanceApp()` 中通过 `oh-my-live2d` 初始化；模型 URL 目前硬编码为 GitHub Pages 地址。若新增模型到 `docs/src/public/models/<name>/model.json`，记得同步更新 `modelPaths`。
- 全局样式变量覆盖在 `docs/.vitepress/theme/custom.css`；优先调整已有 `--vp-*` 变量，避免引入新的样式体系。

## 新增/调整文档
- 新页面放在 `docs/src/<area>/.../*.md`。
- 需要出现在导航/侧边栏时，更新：
  - `docs/.vitepress/nav.js`
  - `docs/.vitepress/sidebar.js`（key 是路由前缀，例如 `"/back-end/database/"`）

## Markdown + Vue 用法
- Markdown 页面可嵌入 Vue SFC 块（`<script setup>` / `<style>`），示例见 `docs/src/index.md`。
- 若添加交互脚本：尽量轻量、避免全局副作用；并在 `onBeforeUnmount()` 中清理事件监听/定时器。

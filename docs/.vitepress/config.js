// .vitepress/config.js

import { defineConfig } from "vitepress";
import sidebar from "./sidebar";
import nav from "./nav";

export default defineConfig({
  title: "前端笔记文档",
  description: "记录个人的所见所得所感",
  lang: "zh",
  base: "/vitepress-doc/",
  srcDir: "src",
  head: [["link", { rel: "icon", href: "/vitepress-doc/favicon.ico" }]],
  themeConfig: {
    siteTitle: "GXC の 笔记",
    logo: "/logo.webp",
    lastUpdated: true,
    footer: {
      message: 'Released under the <a href="https://github.com/xincheng-1999/vitepress-doc">MIT License</a>.',
      copyright: 'Copyright © 2023-present <a href="https://github.com/xincheng-1999/vitepress-doc">GXC</a>',
    },
    sidebar,
    nav,
    search: {
      provider: "local",
    },
  },
});

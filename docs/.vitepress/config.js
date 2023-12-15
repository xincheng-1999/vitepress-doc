// .vitepress/config.js
import { defineConfig } from "vitepress";
export default defineConfig({
  // site-level options
  title: "前端笔记文档",
  description: "记录个人的所见所得所感",
  lang: "zh",
  base: "/vitepress-doc/",
  srcDir: "src",
  head: [["link", { rel: "icon", href: "/vitepress-doc/favicon.ico" }]],
  themeConfig: {
    // i18nRouting: true,
    siteTitle: "GXC の 笔记",
    logo: "/logo.webp",
    lastUpdated: true,
    footer: {
      message: 'Released under the <a href="https://github.com/xincheng-1999/vitepress-doc">MIT License</a>.',
      copyright: 'Copyright © 2023-present <a href="https://github.com/xincheng-1999/vitepress-doc">GXC</a>',
    },
    // theme-level options
    sidebar: {
      "/front-end/interview-questions/": [
        {
          text: "面试题",
          items: [
            { text: "手写题", link: "/front-end/interview-questions/handwritten-code.md" },
            { text: "常见面试题", link: "/front-end/interview-questions/interview-questions.md" },
          ],
        },
      ],
      "/front-end/vue/": [
        {
          text: "Vue相关",
          items: [{ text: "reference", link: "/front-end/vue/" }],
        },
      ],
      "/front-end/the-basics": [
        { text: "浏览器基础", items: [{ text: "重排重绘", link: "/front-end/the-basics/explorer-basics/reflow-repaint.md" }] },
        {
          text: "JavaScript基础",
          items: [
            { text: "事件轮询", link: "/front-end/the-basics/js-basics/event-loop.md" },
            { text: "js基础语法知识", link: "/front-end/the-basics/js-basics/grammar.md" },
            { text: "Proxy和它的好基友Reflect", link: "/front-end/the-basics/js-basics/proxy.md" },
          ],
        },
        {
          text: "网络基础",
          items: [
            { text: "http", link: "/front-end/the-basics/network-basics/http.md" },
            { text: "web安全", link: "/front-end/the-basics/network-basics/webSafety.md" },
          ],
        },
        { text: "TypeScript基础", items: [{ text: "TS装饰器", link: "/front-end/the-basics/ts-basics/decorator.md" }] },
      ],
      "/front-end/nodejs/": [
        {
          text: "Nodejs",
          items: [
            { text: "nvm", link: "/front-end/nodejs/nvm.md" },
            { text: "包管理", link: "/front-end/nodejs/package-management.md" },
          ],
        },
      ],
      "/IT-technology": [
        {
          text: "IT 基础知识收集",
          items: [
            { text: "shell网络代理", link: "/IT-technology/net-proxy.md" },
            {
              text: "Mac",
              items: [
                { text: "Homebrew", link: "/IT-technology/Mac/homebrew.md" },
                { text: "常用操作", link: "/IT-technology/Mac/useful-skill.md" },
                { text: "ideviceinstaller", link: "/IT-technology/Mac/ideviceinstaller.md" },
              ],
            },
            { text: "Windows", items: [{ text: "Windows安装器 Chocolatey", link: "/IT-technology/Windows/Chocolatey.md" }] },
            { text: "adb", link: "/IT-technology/adb.md" },
            { text: "内网穿透", link: "/IT-technology/Intranet-penetration.md" },
            { text: "Git", link: "/IT-technology/git.md" },
          ],
        },
      ],
    },
    nav: [
      {
        text: "前端",
        items: [
          { text: "面试题", link: "/front-end/interview-questions/handwritten-code.md" },
          {
            text: "Vue相关",
            link: "/front-end/vue/",
          },
          {
            text: "前端基础",
            link: "/front-end/the-basics/js-basics/event-loop.md",
          },
          {
            text: "node",
            link: "/front-end/nodejs/nvm.md",
          },
        ],
      },
      {
        text: "IT基础收集",
        link: "/IT-technology/net-proxy.md",
      },
    ],
    search: {
      provider: "local",
    },
  },
});

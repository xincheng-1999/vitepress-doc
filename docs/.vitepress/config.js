// .vitepress/config.js
import { defineConfig } from "vitepress";
export default defineConfig({
  // site-level options
  title: "前端笔记文档",
  description: "Just playing around.",
  lang: "zh",
  base: "/vitepress-doc/",
  srcDir: "src",
  head: [["link", { rel: "icon", href: "/favicon.ico" }]],
  themeConfig: {
    // i18nRouting: true,
    siteTitle: "GXC の 笔记%%",
    logo: "/logo.webp",
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
        ],
      },
      {
        text: "Dropdown Menu",
        items: [
          { text: "Item A", link: "/item-1" },
          { text: "Item B", link: "/item-2" },
          { text: "Item C", link: "/item-3" },
        ],
      },
    ],
    search: {
      provider: "local",
    },
  },
});

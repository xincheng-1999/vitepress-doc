// .vitepress/config.js
import { defineConfig } from "vitepress";
export default defineConfig({
  // site-level options
  title: "前端笔记文档",
  description: "记录个人的所见所得所感",
  lang: "zh",
  base: "/vitepress-doc/",
  srcDir: "src",
  head: [["link", { rel: "icon", href: "/favicon.ico" }]],
  themeConfig: {
    // i18nRouting: true,
    siteTitle: "GXC の 笔记",
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
      "/front-end/the-basics": [
        { text: "浏览器基础", items: [{ text: "重排重绘", link: "/front-end/the-basics/explorer-basics/reflow-repaint.md" }] },
        { text: "JavaScript基础", items: [{ text: "事件轮询", link: "/front-end/the-basics/js-basics/event-loop.md" }] },
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

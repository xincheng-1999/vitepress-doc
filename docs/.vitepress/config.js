// .vitepress/config.js
export default {
  // site-level options
  title: "文档",
  description: "Just playing around.",

  themeConfig: {
    // theme-level options
    sidebar: [
      {
        text: "Guide",
        items: [
          { text: "Introduction", link: "/markdown-examples" },
          { text: "Getting Started", link: "/markdown-examples" },
        ],
      },
    ],
  },
  srcDir: "src",
};

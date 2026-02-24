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
    lastUpdatedText: "最后更新",
    outlineTitle: "本页目录",
    docFooter: {
      prev: "上一页",
      next: "下一页",
    },
    sidebarMenuLabel: "菜单",
    returnToTopLabel: "返回顶部",
    darkModeSwitchLabel: "外观",
    lightModeSwitchTitle: "切换到浅色模式",
    darkModeSwitchTitle: "切换到深色模式",
    footer: {
      message: 'Released under the <a href="https://github.com/xincheng-1999/vitepress-doc">MIT License</a>.',
      copyright: 'Copyright © 2023-present <a href="https://github.com/xincheng-1999/vitepress-doc">GXC</a>',
    },
    sidebar,
    nav,
    search: {
      provider: "local",
      options: {
        translations: {
          button: {
            buttonText: "搜索",
            buttonAriaLabel: "搜索",
          },
          modal: {
            displayDetails: "显示详情",
            resetButtonTitle: "清除",
            backButtonTitle: "返回",
            noResultsText: "没有找到结果",
            footer: {
              selectText: "选择",
              selectKeyAriaLabel: "回车",
              navigateText: "切换",
              navigateUpKeyAriaLabel: "上箭头",
              navigateDownKeyAriaLabel: "下箭头",
              closeText: "关闭",
              closeKeyAriaLabel: "Esc",
            },
          },
        },
      },
    },
  },
});

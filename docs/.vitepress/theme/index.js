import { h } from "vue";
import DefaultTheme from "vitepress/theme";
import "./custom.css";
import ThemeSwitch from "./ThemeSwitch.vue";
import HomeLayout from "./HomeLayout.vue";

const modelPaths = [
  'https://xincheng-1999.github.io/vitepress-doc/models/HK416-1-normal/model.json',
  'https://xincheng-1999.github.io/vitepress-doc/models/HK416-2-destroy/model.json',
  'https://xincheng-1999.github.io/vitepress-doc/models/HK416-2-normal/model.json',
  'https://xincheng-1999.github.io/vitepress-doc/models/Kar98k-normal/model.json',
  'https://xincheng-1999.github.io/vitepress-doc/models/kp31/model.json',
  'https://xincheng-1999.github.io/vitepress-doc/models/rem/model.json',
];

export default {
  extends: DefaultTheme,
  Layout: () => h(DefaultTheme.Layout, null, {
    'layout-bottom': () => h(ThemeSwitch),
  }),
  async enhanceApp({ app }) {
    app.component('HomePage', HomeLayout);
    if (!import.meta.env.SSR) {
      const { loadOml2d } = await import('oh-my-live2d');
      loadOml2d({
        models: [
          {
            path: modelPaths,
            scale: 0.06,
          }
        ],
        dockedPosition: 'right'
      });
    }
  }
};

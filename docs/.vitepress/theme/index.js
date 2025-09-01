import DefaultTheme from "vitepress/theme";
import "./custom.css";

const modelPaths = [
  'https://xincheng-1999.github.io/vitepress-doc/models/HK416-1-normal/model.json',
  'https://xincheng-1999.github.io/vitepress-doc/models/HK416-2-destroy/model.json',
  'https://xincheng-1999.github.io/vitepress-doc/models/HK416-2-normal/model.json',
  'https://xincheng-1999.github.io/vitepress-doc/models/Kar98k-normal/model.json',
  'https://xincheng-1999.github.io/vitepress-doc/models/kp31/model.json',
  'https://xincheng-1999.github.io/vitepress-doc/models/rem/model.json',
];

export default {
  ...DefaultTheme,
  async enhanceApp() {
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

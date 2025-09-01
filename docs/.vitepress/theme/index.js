import DefaultTheme from "vitepress/theme";
import "./custom.css";

const modelPaths = [
  new URL('./models/HK416-1-normal/model.json', import.meta.url).href,
  new URL('./models/HK416-2-destroy/model.json', import.meta.url).href,
  new URL('./models/HK416-2-normal/model.json', import.meta.url).href,
  new URL('./models/Kar98k-normal/model.json', import.meta.url).href,
  new URL('./models/kp31/model.json', import.meta.url).href,
  new URL('./models/rem/model.json', import.meta.url).href,
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

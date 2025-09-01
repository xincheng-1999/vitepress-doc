import DefaultTheme from "vitepress/theme";
import "./custom.css";

export default {
  ...DefaultTheme,
  async enhanceApp() {
    if (!import.meta.env.SSR) {
      const { loadOml2d } = await import('oh-my-live2d');
      loadOml2d({
        models: [
          {
            path: [
              'https://unpkg.com/live2d-widget-model-shizuku@latest/assets/shizuku.model.json',
              'https://unpkg.com/live2d-widget-model-shizuku@latest/assets/shizuku.physics.json',
              'https://unpkg.com/live2d-widget-model-shizuku@latest/assets/shizuku.pose.json',
              'https://unpkg.com/live2d-widget-model-miku@latest/assets/miku.model.json',
              'https://model.oml2d.com/senko_normals/senko.model3.json',
              'https://cdn.jsdelivr.net/gh/Eikanya/Live2d-model/Live2D/Senko_Normals/senko.model3.json',
              'https://model.oml2d.com/HK416-2-normal/model.json',
              'https://model.oml2d.com/HK416-2-destroy/model.json',
              'https://model.oml2d.com/HK416-2-normal/model.json',
              'https://model.oml2d.com/Kar98k-normal/model.json',
            ],
            scale: 0.08
          }
        ],
        dockedPosition: 'right'
      });
    }
  }
};

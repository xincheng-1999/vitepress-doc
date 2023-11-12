# 重排（reflow）和重绘（repaint）

## 正常的渲染流程

正常的渲染流程是：

1. 解析HTML生成DOM树

2. 解析css生成CSSOM树

3. 结合DOM树和CSSOM树生成Render树

4. 使用Render树进行布局，此过程为`Layout`

5. 调用浏览器的UI接口渲染UI，此过程为`Paint`

## 通过js操作DOM后的reflow和repaint过程

![完整的像素管道。](https://web.dev/static/articles/rendering-performance/image/the-full-pixel-pipeline-45b24543207ea.jpg?hl=zh-cn)

[渲染性能 web.dev](https://web.dev/articles/rendering-performance?hl=zh-cn#1_js_css_style_layout_paint_composite)

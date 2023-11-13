# 重排（reflow）和重绘（repaint）

## 正常的渲染流程

正常的渲染流程是：

1. 解析 HTML 生成 DOM 树

2. 解析 css 生成 CSSOM 树

3. 结合 DOM 树和 CSSOM 树生成 Render 树

4. 使用 Render 树进行布局，此过程为`Layout`

5. 调用浏览器的 UI 接口渲染 UI，此过程为`Paint`

## 通过 js/css 操作 DOM 后的 reflow 和 repaint 过程

一般来说，当使用css或者js对页面进行了更改，通常都是以下三种流程：



![](../../../assets/2023-11-13-16-44-27-image.png)

如果修改涉及到 layout 属性（例如宽度、高度，或者元素左侧或顶部的位置）, 就会触发上面的流程，所有受影响的元素都需要重排和重绘。



![](../../../assets/2023-11-13-16-51-01-image.png)

如果修改只涉及到`paint only`（例如背景图片、文本颜色或阴影），那么接下来浏览器会跳过layout这一步，也就是不需要`reflow`,但是仍然会`repaint`



![](../../../assets/2023-11-13-16-54-24-image.png)

还有一种情况，既不用reflow，也不用repaint，比如滚动条的滚动

>  [渲染性能 web.dev](https://web.dev/articles/rendering-performance?hl=zh-cn#1_js_css_style_layout_paint_composite)
> 
> [浅谈浏览器的reflow、repaint的性能优化 - 掘金](https://juejin.cn/post/7117607113966223368/#heading-9)



## 性能优化

由于在实际项目中，大概率遇到的是同时`reflow`和`repaint`，并且这两个过程十分消耗性能，因此性能优化应该以减少重排重绘为主：

### 1.一次性集中操作DOM

现代浏览器会把连续操作DOM收集入队列，再一次性重排重绘，所以如果可以放到一起操作的修改：

```js
var el = document.getElementById('mydiv');
el.style.borderLeft = '1px';
el.style.borderRight = '2px';
el.style.padding = '5px';
```

### 2. 离线修改DOM

即把多次修改集中到一个fragment中

```js
    const container = document.querySelector("#container");
    const fragment = new DocumentFragment();
    fragment.appendChild(document.createElement("img"));
    fragment.appendChild(document.createElement("img"));
    fragment.appendChild(document.createElement("img"));
    container.appendChild(fragment);
```



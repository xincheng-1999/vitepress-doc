# 前端 项目优化

## 一、图像优化

### 1. 图像体积优化

- 图像压缩：选择压缩效率比较高的图片格式，比如 webP
- 使用矢量图：例如使用 svg 能够减少大量资源
- 使用 css 实现简单图形，对于简单的图形，优先使用 css 绘制，其性能远远高于图片
- 使用字体图标
- base64 编码：对于小图标，单独发送请求获取消耗性能，所以转换成 base64 编码直接把内容放在代码中减少 http 请求次数

### 1.图片渐进显示

图片比较大时加载比较慢，默认一般是从上向下加载，可以在 Photoshop 中将图片改为渐进式加载格式，这样加载图片时是均匀加载像素点而不是从上向下加载

## 二、加载优化

浏览器会对各种资源进行默认优先级排序，一般 HTML 和 css 优先级较高，JS 和媒体资源优先级较低，对于（在开发者工具中的 network 工具中查看），对于使用了`async`和`defer`属性的 JS 资源优先级会更低

### 1.预加载

如果对于某些资源需要优先加载，既可以使用`link`标签告诉浏览器需要优先加载：
`<link rel="preload">`。

如下如果有个比较重要的`important.js`需要预加载，可以使用以下方法，[`as`属性](https://developer.mozilla.org/zh-CN/docs/Web/HTML/Element/link#%E5%B1%9E%E6%80%A7)是规定加载资源的类型

```html
<link rel="preload" as="script" href="important.js" />
```

> 除此之外常见的预加载可以用在字体等

> 对于 link 标签的 rel 和 href 属性常用在加载 css 资源，`rel=stylesheet`但是在 rel="preload"的时候即变为预加载器，详见[MDN 预加载](https://developer.mozilla.org/zh-CN/docs/Web/HTML/Attributes/rel/preload)

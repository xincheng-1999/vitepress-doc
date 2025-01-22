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

### Ⅰ. 资源加载优化

浏览器会对各种资源进行默认优先级排序，一般 HTML 和 css 优先级最高，其次是 JS，媒体资源优先级较低，对于（在开发者工具中的 network 工具中查看），对于使用了`async`和`defer`属性的 JS 资源优先级会更低

#### 1.预加载

如果对于某些资源需要优先加载，既可以使用`link`标签告诉浏览器需要优先加载：
`<link rel="preload">`。

如下如果有个比较重要的`important.js`需要预加载，可以使用以下方法，[`as`属性](https://developer.mozilla.org/zh-CN/docs/Web/HTML/Element/link#%E5%B1%9E%E6%80%A7)是规定加载资源的类型

```html
<link rel="preload" as="script" href="important.js" />
```

> 除此之外常见的预加载可以用在字体等

> 对于 link 标签的 rel 和 href 属性常用在加载 css 资源，`rel=stylesheet`但是在 rel="preload"的时候即变为预加载器，详见[MDN 预加载](https://developer.mozilla.org/zh-CN/docs/Web/HTML/Attributes/rel/preload)

#### 2. 预连接

在网络比较慢的时候建立 https 连接是比较耗时的，其会经过 DNS 解析、重定向、三次握手过程等，如果能够提前建立连接，就可以加快 http 请求的访问，从而提高用户体验。

预连接：

```html
<link ref="preconnect" href="https://juejin.cn/" />
```

> 优点：预连接能够使后续响应更快
>
> 缺点：如果超过 10 秒没有网络请求连接就会断开，此时就造成了网络资源和服务器资源的浪费。

此外，和 http 预连接相似还有 DNS 预解析，它仅用来 dns 查询，并且能够缩短 DNS 查询时间：`<link ref="dns-prefetch">`

### Ⅱ. 图片懒加载

#### 1. 什么是懒加载

懒加载也叫按需加载、在一个列表中延迟加载图片数据从而实现性能优化。

> 优点：
>
> - 减少无用请求，减少服务器压力和浏览器性能消耗
> - 提升用户体验，首次只加载视口内的图片能更快渲染出图片
> - 防止加载过多图片而影响其他资源文件的加载

#### 2. 传统图片懒加载的实现

- 图片链接先只放在`data-src`中存储，在`src`属性中写一个 loading 小图的地址
- 监听页面的滚动事件并获取滚动距离`scrollTop`和图片的`offsetTop`
- 判断图片是否符合加载条件`img.offsetTop < window.innerHeight + document.body.scrollTop`

具体实现：

```js
var imgs = document.querySelectorAll("img");
function lozyLoad() {
  var scrollTop = document.body.scrollTop || document.documentElement.scrollTop;
  var winHeight = window.innerHeight;
  for (var i = 0; i < imgs.length; i++) {
    if (imgs[i].offsetTop < scrollTop + winHeight) {
      imgs[i].src = imgs[i].getAttribute("data-src");
    }
  }
}
window.onscroll = lozyLoad();
```

缺点：
监听滚动事件触发太过频繁，每次都要计算所有的图片位置，性能消耗严重，如果列表过长反而可能导致卡顿。

#### 3. ES6 图片懒加载实现：intersectionObserver

传统方法是通过滚动事件反复计算图片相对视口位置，性能消耗较大，使用[intersectionObserver](https://www.yuque.com/u25317811/tsotte/wv2iq63ep02sv0wb)只需要对图片进行监听，当图片进入视口时会通过监听回调触发后续的操作，大大减少了函数调用的次数减少了性能消耗。以下是具体实现：

```js
document.addEventListener("DOMContentLoaded", () => {
  const imgs = document.querySelectorAll("img");
  imgs.forEach((img, index) => {
    let options = {
      root: document.querySelector(".box"), // 用于监听的根节点
      rootMargin: "0px", // 根元素扩大或者收缩的范围，正数扩大，比如根元素是 100*100px，rootMargin为10px，比较的就是120&120
      threshold: 0, // 0~1,0时表示目标完全离开root，1表示目标刚开始离开root就出发回调
    };

    // 当目标满足相交的条件，回调就会触发
    let callback = (entries, observer) => {
      entries.forEach((entry) => {
        // Each entry describes an intersection change for one observed target element:
        // entry.boundingClientRect
        // entry.intersectionRatio  比例，重合部分的比例，0~1之间
        // entry.intersectionRect
        // entry.isIntersecting
        // entry.rootBounds
        // entry.target
        // entry.time
        console.log(entry.intersectionRatio);
        if (entry.intersectionRatio > 0) {
          console.log(entry.target);
          entry.target.src = entry.target.dataset.src;
        }
      });
    };

    let observer = new IntersectionObserver(callback, options);

    // 创建监听
    observer.observe(img);
  });
});
```

### Ⅲ. 首屏加载优化

背景：

随着 Vue、React 等框架的盛行，SPA 单页面应用越来越多，多数的 SPA 应用的结构都很类似。由于 SPA 页面打包之后的 JavaScript 文件很大，等这个巨大的 JavaScript 文件加载完之后，首屏才能渲染，这就导致出现了白屏的问题。在移动端，一些需要快速迭代的开发项目都是使用 HTML5 开发的，同样首屏加载白屏问题非常的严重。目前一些首页加载优化方案有：

#### 1. 骨架屏

#### 2. 资源预加载

#### 3. 路由懒加载

#### 4. 组件懒加载

#### 5. CDN（内容分发网络）

#### 6. webpack 分包 splitChunks

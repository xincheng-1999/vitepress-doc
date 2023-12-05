# 事件轮询机制（Event Loop）

## JS是单线程

js 是单线程，同步代码的优先级最高，其次是微任务（MicroTask），最次是宏任务(MacroTask)

### 如何证明JS是单线程

当JS执行一个复杂任务时，即使setTimeout到了时间，定时器的回调也不会马上执行，说明JS是单线程，同一时间只能执行一个任务

## 微任务

- Promise 回调： 当一个 Promise 被 resolved 或 rejected 时，与之关联的回调函数会成为微任务。

- [MutationObserver](https://developer.mozilla.org/zh-CN/docs/Web/API/MutationObserver)： 当 DOM 结构发生变化时，通过 MutationObserver 监听的回调会被放入微任务队列。

- process.nextTick（Node.js 环境）： 在 Node.js 中，process.nextTick 也被认为是微任务。

> 微任务在当前任务执行完成后、下一个任务开始前执行，因此微任务具有更高的优先级。

## 宏任务

- setTimeout 和 setInterval： 设置定时器的回调函数会成为宏任务。

- [IntersectionObserver ](https://developer.mozilla.org/zh-CN/docs/Web/API/IntersectionObserver)，观察元素交叉的监听器，回调是宏任务

- I/O 操作： 文件读写、网络请求等 I/O 操作的回调函数通常是宏任务。

- 用户交互事件： 如点击事件、~~鼠~~标事件等，其回调函数也是宏任务。

- MessageChannel： 通过 MessageChannel 创建的任务会成为宏任务。

- postMessage： 使用 postMessage 传递的消息也会创建一个宏任务。

宏任务会在同步代码执行完且微任务队列清空的时候才开始执行宏队列中的任务

## 例子

```js
const promise1 = new Promise((resolve) => {
  console.log(1);
  setTimeout(() => {
    console.log(2);
    resolve(3);
  });
});

promise1.then((res) => {
  console.log(res);
});

setTimeout(() => {
  console.log(4);
});

console.log(5);
```

如上代码：

主线程：

- 首先执行同步代码，Promise 的参数函数属于同步代码立即执行，打印 1

- 打印 2 的 setTimeout 完成，回调被放入宏队列

- 打印 4 的 setTimeout 完成，回调被放入宏队列

- 直接打印 5，主线程结束，开始轮询任务队列

轮询队列

- 微队列无微任务，执行宏队列第一个任务，打印 2，resolve(3)Promise 进入微队列

- 微队列出现了任务首先执行微队列任务，打印 3

- 微队列清空，再执行宏队列，打印 4

## 宏任务、微任务和页面更新的优先级分析

首先可以确定的是

- JS永远优先执行同步任务

- 每次执行宏任务之前必须清空微任务队列

但是DOM操作后页面的更新，以及点击事件之类的DOM事件执行的优先级是怎么样的，

可以通过以下代码验证：

```html
<!DOCTYPE html>
<html lang="en">
  <head><meta charset="UTF-8" /></head>
  <body>
    <div id="box"></div>
  </body>
  <script>
    const box = document.querySelector("#box");
    box.addEventListener("click", () => {
      console.log("点击事件");
    });
    let i = 0;
    function recursionFn() {
      if (i > 1e5) return;
      // setTimeout(() => {
      //   i++;
      //   box.innerHTML = i;
      //   recursionFn();
      // });
      Promise.resolve().then(() => {
        i++;
        box.innerHTML = i;
        recursionFn();
      });
    }
    recursionFn();
  </script>
</html>
```

- 上面代码中如果使用微任务——`Promise`不断地递归，视图是不会实时更新的，点击box也不会打印，会直到等到`Promise`递归完成，才会刷新页面视图，打印才会出现

- 如果使用的`setTimeout`那么每次操作完dom之后，页面就会立马`重排重绘`

由此可得出结果执行优先级如下：

> 同步代码 > 微任务 > DOM操作 > 宏任务

另一说其实DOM操作也是属于宏任务，只不过因为setTimeout具有一个最小4ms延迟的特性，所以即使同为宏任务，setTimeout任务也一般最后进入宏队列

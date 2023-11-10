# 事件轮询机制

js 是单线程，同步代码的优先级最高，其次是微任务（MicroTask），最次是宏任务(MacroTask)

## 微任务

- Promise 回调： 当一个 Promise 被 resolved 或 rejected 时，与之关联的回调函数会成为微任务。

- MutationObserver： 当 DOM 结构发生变化时，通过 MutationObserver 监听的回调会被放入微任务队列。

- process.nextTick（Node.js 环境）： 在 Node.js 中，process.nextTick 也被认为是微任务。

> 微任务在当前任务执行完成后、下一个任务开始前执行，因此微任务具有更高的优先级。

## 宏任务

- setTimeout 和 setInterval： 设置定时器的回调函数会成为宏任务。

- I/O 操作： 文件读写、网络请求等 I/O 操作的回调函数通常是宏任务。

- 用户交互事件： 如点击事件、鼠标事件等，其回调函数也是宏任务。

- MessageChannel： 通过 MessageChannel 创建的任务会成为宏任务。

- postMessage： 使用 postMessage 传递的消息也会创建一个宏任务。

宏任务会在同步代码执行完且微任务队列清空的时候才开始执行宏队列中的任务

## 宏任务、微任务和页面更新的优先级分析

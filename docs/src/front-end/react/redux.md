# redux

::: info
[Redux](https://cn.redux.js.org/) 是 JavaScript 应用的状态容器，提供可预测的状态管理。

可以帮助你开发出行为稳定可预测的、运行于不同的环境（客户端、服务器、原生应用）、易于测试的应用程序。
:::

## 核心概念

遵循 react 的单向数据流：

- State 描述了应用程序在某个时间点的状态，视图基于该 state 渲染
- 当应用程序中发生某些事情时：

  - 视图 dispatch 一个 action
  - store 调用 reducer，随后根据发生的事情来更新 state
  - store 将 state 发生了变化的情况通知 UI

  <img src="https://cn.redux.js.org/assets/images/ReduxDataFlowDiagram-49fa8c3968371d9ef6f2a1486bd40a26.gif" onerror="this.onerror=null; this.src='../../assets/response.gif'" alt="图片描述" />

## redux 使用

redux 本身只是一个状态管理库，其并是 react 专用，因此如果需要配合 react 使用，需要配合 react-redux

### 基础接入页面（react-redux）

react-redux 提供了 useSelector 方法，用于获取 store 中的 state，当有 dispatch 发生时，state 发生改变后组件会重新渲染

```js
// App.jsx 首先需要在跟组建注入 store
import React from "react";
import ReactDOM from "react-dom/client";

import { Provider } from "react-redux";
import store from "./store";

import App from "./App";

// 从 React 18 开始
const root = ReactDOM.createRoot(document.getElementById("root"));
root.render(
  <Provider store={store}>
    <App />
  </Provider>
);
```

```js
// some.jsx 页面使用
import React from "react";
import { useSelector } from "react-redux";

export const SinglePostPage = ({ match }) => {
  const { postId } = match.params;

  const post = useSelector((state) => state.posts.find((post) => post.id === postId));

  if (!post) {
    return (
      <section>
        <h2>页面未找到！</h2>
      </section>
    );
  }

  return (
    <section>
      <article className="post">
        <h2>{post.title}</h2>
        <p className="post-content">{post.content}</p>
      </article>
    </section>
  );
};
```

# VueRouter

## 路由守卫

### 全局路由守卫

- beforeEach
  全局前置守卫，在导航未确认之前调用，是一个异步守卫，在所有守卫 Resolve 之后才会继续后面的代码，通常用作权限验证、路由拦截、重定向工作
- beforeResolve
  全局解析守卫，紧随 `beforeEach` 执行，区别是此时已经是所有组件内守卫和异步路由组件被解析之后
- afterEach
  全局后置钩子，其只是生命周期中的钩子，不能影响路由的执行，即不接受 next 函数

### 路由独享守卫

- beforeEnter
  配置在路由中的守卫，每次路由进入之后触发，可以接受一个钩子函数或者钩子函数组成的数组

  ```js
  function removeQueryParams(to) {
    if (Object.keys(to.query).length) return { path: to.path, query: {}, hash: to.hash };
  }

  function removeHash(to) {
    if (to.hash) return { path: to.path, query: to.query, hash: "" };
  }

  const routes = [
    {
      path: "/users/:id",
      component: UserDetails,
      beforeEnter: [removeQueryParams, removeHash],
    },
    {
      path: "/about",
      component: UserDetails,
      beforeEnter: [removeQueryParams],
    },
  ];
  ```

### 路由组件守卫

- beforeRouteEnter
- beforeRouteUpdate
- beforeRouteLeave

## 路由守卫执行周期

1. 触发全局`beforeEach`
2. 触发路由独享`beforeEnter`
3. 触发组件路由钩子 `beforeRouteEnter`
4. 触发全局`beforeResolve`
5. 触发全局后置钩子`afterEach`

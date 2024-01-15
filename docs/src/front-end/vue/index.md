# Vue2 相关文档

## vue 响应式原理

### 响应式核心

- Observer —— 监听器
  - 监听器主要对响应式变量进行监听
  - 其中使用 Object.defineProperty 的 get 和 set 钩子
- Dep —— 订阅器
  - 为每个响应式属性分配一个订阅器，订阅器会在 Vue 初始化时存储对应的 Watcher，订阅器提供了 notify 方法，用以遍历每个订阅者
- Watcher —— 订阅者
  - Watcher 存在于 Dep 中，每个组件在挂载过程默认有 1 个 Watcher，传入的监听函数是更新 DOM 的 patch 函数
  - 当监听到响应式数据的变化时，watcher 会触发监听函数，会以组件为单位重新生成 VNode，进行新旧虚拟节点的比较

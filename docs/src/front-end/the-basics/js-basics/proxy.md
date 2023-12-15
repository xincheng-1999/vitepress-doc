# 参考文章

[Proxy 和 Reflect - 掘金](https://juejin.cn/post/6844904090116292616#heading-8)

[js中为什么Proxy一定要配合Reflect使用_javascript技巧_脚本之家](https://www.jb51.net/article/243036.htm)

# Proxy作用

[Proxy](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Proxy)是ES6新增的内置对象，它的功能和名字一样，就是为了代理，它可以代理一个普通对象，生成一个代理对象，其功能远比Object.defineProperty强大，比如：

```javascript
const obj = {name: '小李'}
const proxy =  new Proxy(obj, {}) // 这里的handlers给个空对象，那么这个代理的所有操作都会和源对象obj同步
```

# handlers的作用和常见的handler

`handlers`是[Proxy](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Proxy)的重点部分，可以通过各种`handler`（陷阱）来对用户操作做出一系列的操作，最简单的即是`get陷阱`：

```javascript
const obj = {name: '小李'}
const proxy =  new Proxy(obj, {
  get(target, key, receiver){
    // target是代理源对象
    // key是这被请求的对象的key
    // receiver是实际触发get陷阱的对象，通常是代理源对象
    return target[key]
  }
})
```

上面的代理即是原封不动的还原了对象获取属性的过程，get陷阱中可以做其他一系列比如拦截操作

除了最常见的get handler，Proxy一共提供了以下handler：

1. [handler.apply()](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Proxy/Proxy/apply)
2. [handler.construct()](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Proxy/Proxy/construct)
3. [handler.defineProperty()](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Proxy/Proxy/defineProperty)
4. [handler.deleteProperty()](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Proxy/Proxy/deleteProperty)
5. [handler.get()](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Proxy/Proxy/get)
6. [handler.getOwnPropertyDescriptor()](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Proxy/Proxy/getOwnPropertyDescriptor)
7. [handler.getPrototypeOf()](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Proxy/Proxy/getPrototypeOf)
8. [handler.has()](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Proxy/Proxy/has)
9. [handler.isExtensible()](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Proxy/Proxy/isExtensible)
10. [handler.ownKeys()](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Proxy/Proxy/ownKeys)
11. [handler.preventExtensions()](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Proxy/Proxy/preventExtensions)
12. [handler.set()](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Proxy/Proxy/set)
13. [handler.setPrototypeOf()](https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Proxy/Proxy/setPrototypeOf)

### get handler的参数

handler接收三个参数

- target ------------- 代理源对象
- key --------------劫持到的当前调用的key
- receiver ------- Proxy 或者继承 Proxy 的对象

对于target和key没什么好说的，比较简单，对于receiver参数在MDN上的解释是：`Proxy` 或者`继承 Proxy 的对象`

它会劫持`proxy.xx`、`proxy[xx]`、以及`Object.create(proxy)[xx]`

前两个就是最普通的对象获取方法，容易理解，这时`receiver`就是代理对象`proxy`

```javascript
const obj = {name: '小李'}
const proxy =  new Proxy(obj, {
  get(target, key, receiver){
    receiver === proxy  // true
    return target[key]
  }
})
```

如果对于以下代码就不对劲了

```javascript
const obj = { name: "小李" };

Object.defineProperty(obj, "fullName", {
  get() {
    console.log("this", this); // {name: '小李'}
    return this.name;
  },
});

const proxy = new Proxy(obj, {
  get(target, key, receiver) {
    console.log(receiver === proxy); // false
    return target[key];
  },
});
const newObj = {
  name: '李华',
};

// 设置obj继承与parent的代理对象proxy
Object.setPrototypeOf(obj, proxy);

console.log(newObj.fullName); // 小李
```

对于原型链继承proxy的对象newObj上没有fullName，于是会顺着原型链往上找，找到了proxy有get陷阱，于是触发get函数，此时的get函数的target指向的是obj，于是就变成获取obj的fullName自然获取的是小李，但是我们希望的是返回的是李华,因为是newObj调用的，期望this指向当前调用者

这时就需要Reflect登场了

```java
...
const proxy = new Proxy(obj, {
  get(target, key, receiver) {
    console.log(receiver === proxy); // false
-    return target[key];
+    return Reflect(target, key, receiver) 
  },
});
...
```

这里的receiver指向的是newObj，绑定给Reflect后，其修改了get函数中的this指向，获取的就是李华了

这里的Reflect使用类似于函数的call方法能改变this的指向，所以一般和proxy搭配使用防止出现this指向问题

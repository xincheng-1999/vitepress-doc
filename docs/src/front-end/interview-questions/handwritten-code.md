# 手写题

## 用函数实现 new 操作

```javascript
function create() {
  // 创建⼀个空的对象
  let obj = new Object();
  // 获得构造函数
  let Con = [].shift.call(arguments);
  // 链接到原型
  //obj.__proto__ = Con.prototype
  //采用Object.creact()方法创建原型链
  const obj = Object.creact(Con);
  // 绑定 this，执⾏构造函数
  let result = Con.apply(obj, arguments);
  // 确保 new 出来的是个对象 如果构造函数返回对象就是生成这个对象，否则返回obj
  return typeof result === "object" ? result : obj;
}
```

## 手写 call、bind、apply

```javascript
Function.prototype.myCall = function (...arg) {
  // 调用myCall的函数
  const fn = this;
  // 需要绑定的this对象
  const targetThis = arg[0];
  // 不可枚举，这样外面就看不到对象被设置的值
  Object.defineProperty(targetThis, "fn", {
    value: fn,
    configurable: true,
  });
  const res = targetThis.fn(arg.slice(1));
  // 调用完了立马删除
  delete targetThis.fn;
  return res;
};
```

```javascript
Function.prototype.myBind = function (context) {
  if (typeof this !== "function") {
    throw new TypeError("Error");
  }
  var _this = this;
  var args = [...arguments].slice(1);
  // 返回⼀个函数
  return function F() {
    // 因为返回了⼀个函数，我们可以 new F()，所以需要判断
    if (this instanceof F) {
      return new _this(...args, ...arguments);
    }
    return _this.apply(context, args.concat(...arguments));
  };
};
```

## 深拷贝

```javascript
function deepClone(obj, tempMap = new Map()) {
  if (obj && typeof obj === "object") {
    // 第一步确定是一个引用数据类型（除了function）

    if (obj instanceof Date) {
      return new Date(obj);
    }
    if (obj instanceof RegExp) {
      return new RegExp(obj);
    }

    if (tempMap.get(obj)) {
      // 防止对象内循环引用
      return tempMap.get(obj);
    }
    let cloneObj = new obj.constructor();

    //缓存已经拷贝过的对象
    tempMap.set(obj, cloneObj);

    // 遍历对象
    for (let key in obj) {
      if (obj.hasOwnProperty(key)) {
        cloneObj[key] = deepClone(obj[key], tempMap);
      }
    }
    return cloneObj;
  } else {
    // 基本数据类型或者函数(lodash的cloneDeep也是引用的同一函数)
    return obj;
  }
}
```

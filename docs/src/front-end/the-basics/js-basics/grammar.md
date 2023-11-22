# js 基础语法知识

## 普通 this 指向问题

### 存在 this 的地方

- 全局上下文
- 函数的执行上下文
- 构造函数上下文`（class 本质也是构造函数）`

### 普通函数的 this

对于普通函数的`this`，谁调用这个函数`this`即指向谁

### 箭头函数的 this

箭头函数作用域内没有`this`，所以在箭头函数中获取`this`，这个`this`获得的是箭头函数的上层作用域的`this`。

::: warning

箭头函数获取的是上层作用域的`this`，因此，箭头函数的上一层`this`只能是普通函数、类、全局作用域

:::

### Class 的 this

`Class` 实际上是构造函数的语法糖，所以`Class`在`new`的过程中构造函数中的 this 都是指向实例

**例子**：

```js
class Person {
  constructor() {
    this.name = "jone";
  }
}

// 等价于
function Persion() {
  this.name = "jone";
}
```

- `Class`中的`this`始终指向类的`实例`

- 构造函数 new 的时候等价于`Class`

- 构造函数被当做普通函数调用时，`this`指向构造函数的调用者

## 严格模式、箭头函数共同对 this 的影响

**先上代码：**

```js
class Person {
  constructor(name) {
    this.name = name;
  }
  sayHi() {
    console.log(`My name is ${this.name}`);
  }
}

const p = new Person("jobs");

p.sayHi(); // [log]: My name is jobs

const obj = { sayHi: p.sayHi };
obj.sayHi(); // [log]: My name is undefined

const fn = p.sayHi;
fn(); // [err]: TypeError: Cannot read properties of undefined (reading 'name')
```

上面代码声明了一个`Person`类，

- `p.sayHi()`毫无疑问，由实例`p`调用`sayHi`，所以`this`指向`p`，打印`My name is jobs`

- `obj.sayHi()`同上，由`obj`调用`sayHi`，`this`指向`obj`

- `fn()`直接调用，通常情况下我们会认为是没有调用者的函数`this`指向`window`，但是这里`this`显然是`undefined`，原因是`class`语法会自动开启`严格模式`

::: warning

严格模式（`strict mode`）下，全局作用域声明的普通函数`this`统一指向`undefined`

:::

**第二种代码：**

```js
class Person {
  constructor(name) {
    this.name = name;
  }
  sayHi = () => {
    console.log(`My name is ${this.name}`);
  };
}

const p = new Person("jobs");

p.sayHi(); // [log]: My name is jobs

const obj = { sayHi: p.sayHi };
obj.sayHi(); // [log]: My name is jobs

const fn = p.sayHi;
fn(); // [log]: My name is jobs
```

上面的情况就是箭头函数导致的，由于箭头函数中没有自己的`this`所以获取的都是箭头函数上一层作用域的`this`，也就是构造函数中的`this`，始终指向实例`p`

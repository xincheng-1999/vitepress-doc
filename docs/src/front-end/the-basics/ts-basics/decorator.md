# TS 装饰器

TS装饰器从使用方法上有以下几种:

```ts
// 类装饰器
function log(constructor: Function, key: ClassDecoratorContext<typeof MyClass>) {
  // 修改类的行为，例如添加日志
  console.log(`Class ${constructor.name} is being created.`,constructor, key);
}
// 方法装饰器
function methodLog(target:Function, key: ClassMethodDecoratorContext<MyClass, (content: string) => void> ){
  console.log('method is been called',target, key)
}

// 属性装饰器
function propDecorator (target: undefined, key: ClassFieldDecoratorContext<MyClass, number>) {
  console.log('属性装饰器', target, key)
}

// 参数装饰器
function parameterDecrator (target: string, key: any){
  console.log('参数装饰器', target, key)
}

@log
class MyClass {
  // class implementation
  @methodLog
  sayHi( content: string){
    alert(content)
  }
  @propDecorator
  height = 7
}


```

::: info

上面的各种装饰器，初始化的时候所有装饰器都会被调用一次，后续实例化和调用都不会再触发装饰器，这种装饰器只能用来初始化时进行一些操作，扩展性比较差。

为了能在类实例化时，方法调用时等情况触发装饰器，装饰器函数还可以返回一个新的实例替代或者扩展原有的类、方法、属性。

:::

下面对每种装饰器具体分析：

## 类装饰器

```ts
// 类装饰器
function log(constructor: typeof MyClass, key: ClassDecoratorContext<typeof MyClass>) {
  return class DecoratorClass extends constructor {
    aaa:number
    constructor(height: number){
      super(height)
      // 可以拓展属性或方法（不建议）
      this.aaa = 7
      // 可以拓展相关打印
      console.log('被实例化了，参数:', ...arguments)
    }
  }
}


@log
class MyClass {
  height: number
  constructor(height: number){
    this.height = height
  }
  // class implementation
  sayHi( content: string){
    alert(content)
  }
}

const obj = new MyClass(8)
console.log(obj)
```

上面的类使用`log`装饰器后,每次实例化都可以对参数进行打印：

```log
[LOG]: "被实例化了，参数:",  8 
[LOG]: DecoratorClass: { "height": 8, "aaa": 7 } 
```

并且确实可以看到，添加的aaa属性已经在实例上面，但是如果我们尝试读取`obj.aaa` ,TS 会报错： `Property 'aaa' does not exist on type 'MyClass'`

因此在类装饰器中不要拓展类和修改类

## 方法装饰器

```ts
// 方法装饰器
function methodLog(target:Function, context: ClassMethodDecoratorContext<MyClass, (content: string) => void> ){
  return function(this:MyClass, ...arg:any[])  {
    console.log(context.name,'方法开始')
    target.call(this, ...arg)
    console.log(context.name,'方法结束')
  }
}

class MyClass {
  height: number
  constructor(height: number){
    this.height = height
  }
  @methodLog
  sayHi(content: string){
    alert(content)
  }
}

const obj = new MyClass(8)
console.log(obj.sayHi('你好'))
```

上面方法装饰器，接受一个`target`原方法,和一个`context`上下文

每次方法被调用时会转向调用装饰器返回的这个函数，并把对象this传递给第一个参数，其余参数通过剩余参数收集，原函数不会被调用。

通过这样的装饰器方法即可给所有的方法都加上打印

> [TS Decotator](https://www.typescriptlang.org/docs/handbook/decorators.html#class-decorators)
> 
> [TS Playground](https://www.typescriptlang.org/play?target=6&ts=5.2.2#code/FAehAJG8fRRiMB0zAs1YAzArgOwMYBcCWB7NcAGzwHMAKDAgZywCcVs86AucLATwAcBTPJcAFkOAYSIBDatQA04ANY8ObMZOoARHlTriszEQSw8AHlgA8nXvyGiJUgHwBKcAG9g4cHR5YUdQhlvU4BpaOnoB4MaGaAAmgVRotAxMdC5u7uDimSxoKAC2AEY8dGnu8YmMunTkABY8OKTVWGw5BUUOrunp1Ci8VbX1jQ4l6WDggPfKgKdygMtGgKo6gHo6gOQGgGhGgJ2mgKs2w+5Y1TjUAHSZ4uAAvOAA7Jvgo5OzgB9ugM6KgMpGgA7Kl2V4RDx7JBQA5IDVEYA87UA0fKANGVAGFygBh-wBDyoAHUxYv1ke2R4jopDyPDQWGoQ06AF80gSCcBgAABH7AfyqawqKSpdz9BpNcAtQrFUo0egVZg1OpM5p5NntYbbXZ7RmNE7gCVYQlpUZUuk4XJcT65TFYHT4NBpajiDgACRw5HA8SizMSODQpGFnXEnzoWEoBg1uPARJJZSw4Dw+QAVlK0DwAO40gLkAAcQ3en2+ZHIvr9B0yDiAA)
> 
> [Announcing TypeScript 5.0 - TypeScript](https://devblogs.microsoft.com/typescript/announcing-typescript-5-0/#decorators)

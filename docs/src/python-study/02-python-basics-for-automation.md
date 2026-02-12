# 02. Python 语法速成（为自动化服务）

本章目标：用最少语法覆盖你写自动化脚本会用到的 80%。

> 原理补充：Python 更偏“表达式 + 迭代”的风格。你会频繁用到可迭代对象（iterable）、生成器（generator）以及“用函数描述数据转换”的写法（例如推导式、`sorted(key=...)`）。

## 1) JS → Python 思维切换（你需要特别注意）
- 缩进就是语法（代码块由缩进决定）
- `None` 类似 JS 的 `null`，但判断要用 `is None`
- 字符串格式化优先用 f-string：`f"{name}"`
- 可变/不可变：list/dict 可变；str/tuple/int 不可变

## 2) 函数与常用写法
- 默认参数不要用可变对象（例如 `def f(x=[]):` 是坑）
- 解包：`a, b = b, a`

## 3) 常用迭代工具
- `enumerate`、`zip`
- 推导式（list/dict/set comprehension）

## 4) 排序与 key
- `sorted(items, key=...)`
- `key=lambda x: x[0]`

## 5) 练习（建议你边写边跑）
- 写一个函数：把一组文件名列表，过滤出 `.md`，并按名称排序
- 写一个函数：把 `[{"name": "a", "age": 1}, ...]` 按 age 排序

## 小结
这一章结束后，你应该能无压力读懂并改写简单脚本。

> 速查：更碎片的语法技巧请看 `/python-study/python-basic`。

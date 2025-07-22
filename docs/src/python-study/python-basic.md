
# Python 基础（个人学习笔记）

## 常用语法 & 技巧

### 1. 一行交换变量
```python
a, b = b, a
```

### 2. 列表推导式
```python
nums = [x for x in range(10) if x % 2 == 0]
```

### 3. 字典的 get 方法，避免 KeyError
```python
d = {"a": 1}
val = d.get("b", 0)  # 不存在返回默认值 0
```

### 4. 枚举遍历
```python
for idx, val in enumerate(["a", "b", "c"]):
	print(idx, val)
```

### 5. zip 并行遍历
```python
names = ["Tom", "Jerry"]
ages = [18, 20]
for name, age in zip(names, ages):
	print(name, age)
```

### 6. 断言调试
```python
assert 2 + 2 == 4
```

### 7. 读取文件
```python
with open("file.txt", "r", encoding="utf-8") as f:
	for line in f:
		print(line.strip())
```

### 8. lambda & map
```python
res = list(map(lambda x: x * 2, [1, 2, 3]))
```

### 9. 排序技巧
```python
arr = [(2, "b"), (1, "a")]
arr.sort(key=lambda x: x[0])
```

### 10. 计数器
```python
from collections import Counter
cnt = Counter([1,2,2,3])
print(cnt)
```

---

## 常用标准库
- os / sys：文件、路径、系统参数
- re：正则表达式
- json / csv：数据格式处理
- datetime / time：时间处理
- collections：高级数据结构（Counter, defaultdict, deque）
- itertools：高效迭代工具
- functools：函数式编程（lru_cache, reduce, partial）

---

## 个人踩坑 & 经验
- 字符编码问题，文件读写建议加 encoding="utf-8"
- 列表、字典的浅拷贝和深拷贝（copy vs deepcopy）
- 多线程 GIL 限制，CPU 密集型用多进程
- 虚拟环境推荐用 venv 或 conda，避免包冲突
- pip 国内源：`pip install 包名 -i https://pypi.tuna.tsinghua.edu.cn/simple`
- print 调试，善用 f-string 格式化输出

---

## 常用命令
- 查看 Python 版本：`python --version`
- 安装包：`pip install 包名`
- 进入交互模式：`python`
- 运行脚本：`python xxx.py`

---

## 进阶建议
- 多刷 LeetCode，练习算法和数据结构
- 读官方文档，查阅 Stack Overflow
- 关注 PEP（Python Enhancement Proposal）
- 了解主流 Web 框架、数据分析库

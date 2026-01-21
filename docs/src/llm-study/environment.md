# 学习环境与安装（Windows）

这一节目标：**15 分钟内跑通一次大模型调用**，并把后续学习会用到的工具装好。

你有两条路线可以选：

- **路线 A：先用云端 API（推荐入门）**：不折腾显卡/驱动，先把 Prompt / RAG / 工程流程跑通。
- **路线 B：本地跑模型（适合想省钱/想研究推理）**：上手快的方式是 Ollama；更深的方式再学 vLLM / llama.cpp。

> 建议：先走 A，把“能用”跑通；有兴趣再补 B。

---

## 0. 必装软件（两条路线都建议装）

### 0.1 安装 Git

- 下载并安装 Git（用于拉代码、管理笔记、克隆示例项目）。
- 安装完成后在终端确认：

```bash
git --version
```

### 0.2 安装 Python（建议 3.10+）

- Windows 直接安装 Python（勾选 **Add Python to PATH**）。
- 安装后确认：

```bash
python --version
pip --version
```

### 0.3（可选但推荐）安装 VS Code

- 用来写脚本、看日志、调试。

---

## 1. 路线 A：云端 API（最省事）

核心思路：本地只负责发请求、处理返回；模型在云端跑。

### 1.1 准备一个 OpenAI 兼容的 API

很多服务都提供“OpenAI 兼容协议”，你需要两样东西：

- `API_KEY`
- `BASE_URL`（有的服务叫 endpoint）

> 如果你用的是 OpenAI 官方：通常 `BASE_URL` 不用改。

### 1.2 创建一个最小可运行项目

在任意目录新建文件夹，例如 `llm-playground`：

```bash
mkdir llm-playground
cd llm-playground
python -m venv .venv
.\.venv\Scripts\activate
pip install -U pip
pip install openai
```

### 1.3 写第一个“Hello LLM”

新建 `hello_llm.py`：

```python
from openai import OpenAI
import os

client = OpenAI(
    api_key=os.getenv("OPENAI_API_KEY"),
    base_url=os.getenv("OPENAI_BASE_URL"),  # 没有就不填/删掉这行
)

resp = client.chat.completions.create(
    model=os.getenv("OPENAI_MODEL", "gpt-4o-mini"),
    messages=[
        {"role": "system", "content": "你是一个严谨的助教。"},
        {"role": "user", "content": "用 3 句话解释什么是 Token。"},
    ],
)

print(resp.choices[0].message.content)
```

然后在终端设置环境变量并运行（PowerShell）：

```powershell
$env:OPENAI_API_KEY="你的KEY"
$env:OPENAI_BASE_URL="你的BASE_URL"   # 可选
$env:OPENAI_MODEL="你的模型名"         # 可选
python .\hello_llm.py
```

#### 常见报错排查

- `401 Unauthorized`：KEY 错/没配环境变量。
- `404 model not found`：模型名不对（换成服务商文档里给的 model）。
- `SSLError/Proxy`：公司网络/代理问题，先在浏览器确认能访问 endpoint。

---

## 2. 路线 B：本地跑模型（Ollama，最快上手）

适合：想离线体验、想理解“模型在你电脑上怎么跑”的同学。

### 2.1 安装 Ollama

- 安装完成后确认：

```bash
ollama --version
```

### 2.2 拉一个模型并对话

```bash
ollama pull llama3.1
ollama run llama3.1
```

你会进入交互式对话，输入问题即可。

### 2.3 用 API 调用本地模型

Ollama 默认提供本地 HTTP 接口（一般是 `http://localhost:11434`）。

你可以先用 curl 验证（如果没有 curl 就跳过）：

```bash
curl http://localhost:11434/api/generate -d "{\"model\":\"llama3.1\",\"prompt\":\"用一句话解释注意力机制\"}"
```

---

## 3. GPU（可选）：什么时候需要、怎么判断

- **学习 Prompt / RAG / 工程化**：CPU + 云端 API 足够。
- **想本地跑 7B/14B 以上**：更建议有 NVIDIA GPU（显存越大越好）。
- **想做训练/LoRA**：一般需要 CUDA 环境，门槛更高，放到“微调”章节再细讲。

快速判断自己有没有 NVIDIA GPU：

```bash
nvidia-smi
```

如果命令不存在，可能是：

- 你没有 NVIDIA 显卡
- 或驱动没装/环境变量没配置

---

## 4. 本节验收（你需要做到）

满足任意一条就算通过：

- 路线 A：`hello_llm.py` 能跑通并输出回答
- 路线 B：`ollama run llama3.1` 能正常对话

通过后，我们再进入下一节：**什么是 Token / 上下文窗口 / 采样参数**，并用你刚搭好的环境做小实验。

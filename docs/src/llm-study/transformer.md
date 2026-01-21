# Transformer 速通：注意力、位置编码与推理

这一章目标：

- 用“工程能用的直觉”理解 Transformer 在干什么
- 明白为什么它会吃内存、为什么长上下文会变慢
- 做一个**不依赖 GPU**的小实验：用几十行代码实现一次注意力计算

如果你是 RTX 3050：这一章默认按“**能在 CPU 上跑通**”来讲；后面涉及本地模型时，会优先选 7B 以内、4-bit 量化、短上下文。

## Transformer 的核心

Transformer 的关键是 **自注意力（Self-Attention）**：每个位置的表示，都可以“关注”序列中其它位置的信息，然后加权汇总。

你可以把它理解为：

- 输入是一串 Token
- 模型为每个 Token 生成一个向量表示
- 通过注意力把“与当前 Token 最相关的上下文”聚合过来

Transformer（用于语言模型时）通常由很多层堆叠，每一层大致都是：

1. 自注意力（Self-Attention）
2. 前馈网络（FFN / MLP）
3. 残差连接（Residual） + LayerNorm

你不用一开始就把所有细节吃透，但你要抓住“信息怎么在序列里流动”：

- **注意力**：负责“看哪里、聚合哪里的信息”
- **FFN**：负责“把聚合后的信息做非线性变换/表达增强”

## 注意力在做什么

- **Q（Query）**：我当前想找什么信息
- **K（Key）**：每个位置能提供什么信息
- **V（Value）**：真正被聚合的内容

计算上会得到一个权重分布（softmax），再对 V 做加权求和。

更工程一点的记法：

> 注意力 = 用 QK 计算相关性 → softmax 得到权重 → 用权重加权求和 V。

经典的“缩放点积注意力（Scaled Dot-Product Attention）”可以写成：

$$
	ext{Attention}(Q,K,V)=\text{softmax}(\frac{QK^T}{\sqrt{d_k}})V
$$

其中 $d_k$ 是 Key 向量维度。除以 $\sqrt{d_k}$ 是为了让数值更稳定。

### 多头注意力（Multi-Head）在解决什么

如果只有一个注意力头，它可能只能学到“单一类型的关联”。多头注意力让模型可以在同一层里同时学到多种关联：

- 有的头关注语法关系
- 有的头关注指代关系
- 有的头关注主题一致性

工程上你只要记住：**多头 = 多组 Q/K/V 投影并行做注意力，再拼起来**。

## 为什么需要位置编码

注意力本身对顺序不敏感（置换不变），所以要注入位置信息：

- 绝对位置编码
- 相对位置编码
- RoPE（旋转位置编码）等

工程上，你只需要记住：**不同的位置编码方案会影响长上下文能力与外推性**。

再补一个你后面会频繁遇到的点：

- 很多现代 LLM 使用 RoPE 或相对位置编码
- 它们与“长上下文扩展”（比如通过缩放策略）强相关

你不需要现在就推导 RoPE，只要知道：**位置编码决定了模型如何理解“顺序”和“距离”**。

## 推理阶段发生了什么

- 模型一次输出一个 Token
- 每次输出都依赖前面所有 Token（上下文）
- 这也是为什么：
  - 上下文越长，推理越慢（KV Cache 相关）
  - Prompt 越长，成本越高

### KV Cache：为什么能加速

生成第 $t$ 个 token 时，理论上注意力要看前面 $1..t-1$ 的所有 token。

- **不缓存**：每一步都重新算一遍历史的 K/V，非常浪费
- **KV Cache**：把历史 token 的 K/V 存起来，后续只需要算“新 token 的 Q/K/V”，并用缓存的 K/V 做注意力

你会看到两类瓶颈：

- **计算瓶颈**：模型本身算得慢
- **内存瓶颈**：KV Cache 很占显存/内存（上下文越长越大）

这也是为什么：同一个模型，**长上下文**会明显更慢、更吃显存。

---

## 1. 你要先建立的两个直觉

### 1.1 “注意力是平方复杂度”直觉

对长度为 $n$ 的序列，注意力权重矩阵是 $n \times n$。

- $n$ 翻倍，注意力相关的开销大致变成 4 倍

这不是让你死记复杂度，而是帮助你理解：

- 为什么 RAG 要“只塞最相关的片段”
- 为什么摘要/裁剪历史对话很重要

### 1.2 “模型推理像流水线”直觉

一次生成包含很多步骤：

- 编码输入（tokenize）
- 逐 token 生成（decode）
- 采样策略决定输出（temperature/top_p）

工程上你优化体验通常从：

- Streaming（先把字吐出来）
- 缓存（prompt cache / KV cache）
- 控制输出长度（max_tokens）

---

## 2. 立刻动手：用 30 行代码算一次注意力

这个实验不需要 GPU，只需要 `numpy`。

### 2.1 安装依赖

在你的 venv 里执行：

```bash
pip install numpy
```

### 2.2 写一个最小注意力实现

新建 `toy_attention.py`：

```python
import numpy as np


def softmax(x: np.ndarray) -> np.ndarray:
  x = x - np.max(x, axis=-1, keepdims=True)
  exp_x = np.exp(x)
  return exp_x / np.sum(exp_x, axis=-1, keepdims=True)


def attention(Q: np.ndarray, K: np.ndarray, V: np.ndarray) -> np.ndarray:
  d_k = Q.shape[-1]
  scores = (Q @ K.T) / np.sqrt(d_k)
  weights = softmax(scores)
  return weights @ V


if __name__ == "__main__":
  np.random.seed(0)

  # 假设 4 个 token，每个 token 的向量维度是 3
  X = np.random.randn(4, 3)

  # 为了演示，直接把 X 当成 Q/K/V（真实模型里会先做线性投影）
  Q, K, V = X, X, X
  Y = attention(Q, K, V)

  print("Input X:\n", X)
  print("Output Y (after attention):\n", Y)
```

运行：

```bash
python toy_attention.py
```

你会看到输出 `Y` 是每个 token 对其它 token 的加权汇总。

### 2.3 你应该观察什么

- 把 `X` 的第 0 行改得很大/很小，看看输出如何被影响
- 把 token 数从 4 改成 256（例如 `X = np.random.randn(256, 3)`），体感一下时间变化

这能帮助你对“长上下文更慢”建立非常具体的直觉。

---

## 3. RTX 3050 相关：什么时候需要折腾 PyTorch + CUDA

本章不强制你装 CUDA。

你后面会遇到三类任务：

1. **云端 API 调用**：不需要 CUDA
2. **本地推理（Ollama / llama.cpp / vLLM）**：通常不需要你手动装 PyTorch CUDA（取决于方案）
3. **LoRA 微调（PyTorch）**：才更可能需要 CUDA

对 RTX 3050 的建议（以“少折腾”为第一原则）：

- 想本地推理：优先 Ollama（模型选 7B 以内、4-bit、短上下文）
- 想学训练/微调：等到“微调”章节再装 CUDA，并且以 LoRA 为主

如果你确实要装 PyTorch CUDA：请以 PyTorch 官网给出的安装命令为准（会随版本变化）。

## 工程视角：你最该关心的 3 件事

- **上下文窗口**：决定你能塞多少对话/资料
- **KV Cache**：决定吞吐与延迟（是否能复用历史计算）
- **采样策略**：temperature / top_p 会直接改变输出稳定性

补充一个“经常被忽略但非常重要”的点：

- **上下文内容质量**：同样 2k token，上下文结构清晰 vs 混乱堆砌，效果差距很大。

---

## 本章验收

满足下面两条，你就可以进入下一章（Prompt 工程 / RAG）了：

- 你能用自己的话解释：Attention 为什么要 Q/K/V
- 你能跑通 `toy_attention.py` 并理解输出是“加权汇总”

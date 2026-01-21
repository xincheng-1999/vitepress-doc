# 微调与对齐：SFT/LoRA/DPO 的工程视角

微调不是“让模型更聪明”，更常见的目标是：

- 让模型**更像你的产品**：口吻、格式、术语
- 让模型**更稳定**：少跑题、少胡编
- 让模型**适配你的任务分布**：客服、摘要、抽取、分类

这一章目标：让你在 **Windows + RTX 3050** 的现实约束下，跑通一次“最小微调闭环”：

- 准备一份小数据集
- 用 LoRA/QLoRA 做一次 SFT（监督微调）
- 合并/加载 LoRA 权重做对比推理

重要提醒（RTX 3050 上限）：

- RTX 3050 常见显存 4GB/6GB/8GB。
- **默认建议**：优先微调 **0.5B ~ 1.5B** 级别模型；7B 级别在 3050 上通常会很吃紧（尤其 Windows 环境）。
- 本章以“能跑通”为第一目标：小模型 + 4-bit 量化（QLoRA） + 小 batch。

---

## 0. 先搞清楚：你到底需要哪种“微调”

### 0.1 SFT（监督微调）

- 你提供：输入 → 标准输出
- 适合：格式稳定、话术风格、领域术语、固定任务

### 0.2 LoRA / QLoRA（强烈推荐入门）

- **LoRA**：只训练少量“适配器参数”，显存和时间都更友好
- **QLoRA**：把基座模型以 4-bit 加载（更省显存），在小显卡上更容易跑通

### 0.3 DPO / RLHF（先别急）

它们本质是在“偏好数据”上做对齐。

入门阶段先把 SFT/LoRA 跑通更重要：

- 你需要先有评测集、版本化、可回放
- 否则做对齐很容易“看起来更像人，但实际更不稳定”

---

## 1. 环境准备（Windows 友好方案）

在 Windows 上做 LoRA 微调，最大的坑通常不是代码，而是 CUDA/依赖。

我建议你选下面两条路线之一：

### 路线 A：WSL2 + NVIDIA（推荐，最接近生产环境）

适合：想在本机用 RTX 3050 跑通微调。

1) 开启 WSL2（Windows 功能里启用 WSL、虚拟机平台）
2) 安装 Ubuntu（建议 22.04）
3) 安装支持 WSL 的 NVIDIA 驱动（装完后在 WSL 里能跑 `nvidia-smi`）

在 WSL 里确认：

```bash
nvidia-smi
```

### 路线 B：云端（Colab/Kaggle/租 GPU）

适合：你显存不够/不想折腾驱动。

你把本章的数据与脚本搬到云端跑，体验会更顺滑。

> 本章给出的代码在云端同样适用。

---

## 2. 选择一个“适合 RTX 3050 跑通”的模型

建议按显存保守选择：

- 4GB：优先 0.5B（或更小）
- 6GB/8GB：可以尝试 1.5B（如果用 QLoRA + 小 batch）

模型建议（举例）：

- `Qwen2.5-0.5B-Instruct`
- `Qwen2.5-1.5B-Instruct`

你可以用任何 Hugging Face 上的 Instruct 模型，但越大越难在 3050 上跑通。

---

## 3. 安装依赖（在 WSL / Linux 环境执行）

建议你在一个干净目录新建项目：

```bash
mkdir -p llm-finetune && cd llm-finetune
python -m venv .venv
source .venv/bin/activate
pip install -U pip
```

安装训练所需库：

```bash
pip install -U \
  torch \
  transformers \
  datasets \
  accelerate \
  peft \
  trl \
  bitsandbytes
```

验证 PyTorch 能看到 GPU（可选，但推荐）：

```bash
python -c "import torch; print(torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'CPU')"
```

如果 `torch.cuda.is_available()` 是 `False`：

- 先确认 WSL 里 `nvidia-smi` 正常
- 再根据 PyTorch 官方指引安装对应 CUDA 版本的 torch（不同版本命令会变化）

---

## 4. 准备数据：最小可用 SFT 数据集

入门时，数据越小越好：先用 50~200 条样本跑通流程。

### 4.1 推荐格式（JSONL）

新建 `data/train.jsonl`，每行一个样本：

```json
{"instruction":"把用户投诉改写成更礼貌的客服回复，包含道歉与下一步处理。","input":"我刚买的耳机左耳没声音！","output":"非常抱歉给您带来不便。为尽快协助您处理，请您提供订单号或购买截图，我们将为您核实并安排退换/维修。"}
```

新建 `data/val.jsonl`（少量即可，比如 10~20 条）。

### 4.2 为什么要分训练集/验证集

- 训练集：让模型学
- 验证集：防止你“训练好了但其实只会背题”

---

## 5. 训练：用 QLoRA 做一次最小 SFT

下面这份脚本会：

- 4-bit 加载基座模型（更省显存）
- 加 LoRA 适配器
- 用 TRL 的 `SFTTrainer` 训练

新建 `train_qlora_sft.py`：

```python
import os
from datasets import load_dataset
from transformers import AutoTokenizer, AutoModelForCausalLM
from transformers import BitsAndBytesConfig
from peft import LoraConfig
from trl import SFTTrainer, SFTConfig


def format_sample(example):
	# 最稳定的方式：把 instruction/input/output 显式拼成一个训练文本
	instruction = example.get("instruction", "").strip()
	input_text = example.get("input", "").strip()
	output_text = example.get("output", "").strip()
	if input_text:
		prompt = f"### Instruction\n{instruction}\n\n### Input\n{input_text}\n\n### Response\n"
	else:
		prompt = f"### Instruction\n{instruction}\n\n### Response\n"
	return {"text": prompt + output_text}


if __name__ == "__main__":
	model_name = os.getenv("BASE_MODEL", "Qwen/Qwen2.5-0.5B-Instruct")
	output_dir = os.getenv("OUT_DIR", "./outputs-lora")

	bnb_config = BitsAndBytesConfig(
		load_in_4bit=True,
		bnb_4bit_quant_type="nf4",
		bnb_4bit_use_double_quant=True,
		bnb_4bit_compute_dtype="bfloat16",
	)

	tokenizer = AutoTokenizer.from_pretrained(model_name, use_fast=True)
	if tokenizer.pad_token is None:
		tokenizer.pad_token = tokenizer.eos_token

	model = AutoModelForCausalLM.from_pretrained(
		model_name,
		quantization_config=bnb_config,
		device_map="auto",
	)

	ds = load_dataset("json", data_files={"train": "data/train.jsonl", "validation": "data/val.jsonl"})
	ds = ds.map(format_sample, remove_columns=ds["train"].column_names)

	lora = LoraConfig(
		r=8,
		lora_alpha=16,
		lora_dropout=0.05,
		bias="none",
		task_type="CAUSAL_LM",
		# target_modules 不同模型不同；先不写，让库自动推断（不行再补）
	)

	cfg = SFTConfig(
		output_dir=output_dir,
		per_device_train_batch_size=1,
		gradient_accumulation_steps=8,
		learning_rate=2e-4,
		num_train_epochs=1,
		logging_steps=10,
		save_steps=100,
		eval_steps=100,
		evaluation_strategy="steps",
		max_seq_length=512,
		fp16=True,
		report_to=[],
	)

	trainer = SFTTrainer(
		model=model,
		tokenizer=tokenizer,
		train_dataset=ds["train"],
		eval_dataset=ds["validation"],
		peft_config=lora,
		args=cfg,
		dataset_text_field="text",
	)

	trainer.train()
	trainer.save_model(output_dir)
	tokenizer.save_pretrained(output_dir)
```

运行：

```bash
python train_qlora_sft.py
```

如果你显存吃紧：

- 把 `max_seq_length` 改成 256
- 把 `gradient_accumulation_steps` 调大（保持有效 batch）
- 或换更小模型（0.5B）

---

## 6. 推理对比：微调前 vs 微调后

新建 `infer_compare.py`：

```python
import os
import torch
from transformers import AutoTokenizer, AutoModelForCausalLM
from peft import PeftModel


def generate(model, tokenizer, prompt: str):
	inputs = tokenizer(prompt, return_tensors="pt").to(model.device)
	with torch.no_grad():
		out = model.generate(
			**inputs,
			max_new_tokens=128,
			temperature=0.2,
			top_p=0.9,
			do_sample=True,
		)
	return tokenizer.decode(out[0], skip_special_tokens=True)


if __name__ == "__main__":
	base = os.getenv("BASE_MODEL", "Qwen/Qwen2.5-0.5B-Instruct")
	lora_dir = os.getenv("LORA_DIR", "./outputs-lora")

	tokenizer = AutoTokenizer.from_pretrained(base, use_fast=True)
	if tokenizer.pad_token is None:
		tokenizer.pad_token = tokenizer.eos_token

	base_model = AutoModelForCausalLM.from_pretrained(base, device_map="auto")
	tuned_model = PeftModel.from_pretrained(base_model, lora_dir)

	prompt = """### Instruction
把用户投诉改写成更礼貌的客服回复，包含道歉与下一步处理。

### Input
我刚买的耳机左耳没声音！

### Response
"""

	print("=== After LoRA ===")
	print(generate(tuned_model, tokenizer, prompt))
```

运行：

```bash
python infer_compare.py
```

你应该看到输出更贴近你的“话术与格式”。

---

## 7. 常见坑（RTX 3050 高概率会遇到）

### 7.1 OOM（显存不够）

优先按顺序处理：

1) `max_seq_length` 降到 256
2) `per_device_train_batch_size=1`
3) 模型换更小（0.5B）
4) 关掉不必要的 eval/save（跑通后再加）

### 7.2 Windows 原生环境装不起来 bitsandbytes

解决：

- 用 **WSL2** 或直接用云端

### 7.3 训练跑了但效果没变化

常见原因：

- 数据太少/质量不一致
- 训练轮数太少
- 提示模板训练/推理不一致（训练用 A 模板，推理用 B 模板）

---

## 8. 微调前的 checklist（上线前一定要做）

## SFT（监督微调）

- 数据形式：输入 → 期望输出
- 适合：格式对齐、风格对齐、固定任务
- 风险：数据质量差会把模型带偏

## LoRA / PEFT

- 通过少量参数适配任务
- 成本更低、迭代更快
- 适合快速试错与多任务适配

## 偏好优化（概念层）

你会看到 RLHF / DPO 这些词，它们的目标是让模型更符合“人类偏好”。

工程上更重要的是：

- 你是否有稳定的人类标注流程
- 你如何定义“好”的标准（可复现）

## 什么时候该微调

- Prompt + RAG 已经能解决 80% 的正确性
- 但仍需要更稳定的格式/口吻/动作边界
- 或者需要明显降低调用成本（更小模型 + 微调）

## 微调前的 checklist

- 先做基线：不微调时的准确率/成本/延迟
- 清洗数据：去重、纠错、统一格式
- 明确评测集：训练集、验证集、测试集严格隔离

再加两条“工程向”的：

- Prompt 模板版本化：训练时用的模板要记录下来
- 线上监控：错误样本回流，形成持续迭代闭环

---

## 本章验收

你完成下面两步就算通过：

- 在 WSL2 或云端跑通 `train_qlora_sft.py`（不要求训练很久，跑通即可）
- 用 `infer_compare.py` 在相同 prompt 下看到“微调后输出更贴近你的格式/话术”

通过后，我们再进入下一节：**评测**（怎么构建你的测试集/指标/回归测试，避免越调越差）。

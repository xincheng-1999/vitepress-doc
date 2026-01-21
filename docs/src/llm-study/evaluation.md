# 评测：离线、线上与可观测性

大模型系统的评测要分层：**模型能力**、**检索质量**、**系统体验**。

这一章目标：你能把“感觉还行”变成“有数据证明”。更具体点：

- 你能做一个**离线评测集**（JSONL）
- 你能写一个**一键跑评测**的脚本（失败样本可回放）
- 你知道上线后看哪些指标、怎么做 A/B

---

## 0. 本章需要的环境

沿用前面章节的环境即可：Python + venv。

安装依赖（在 venv 里执行）：

```bash
pip install -U openai requests pydantic numpy
```

说明：

- `openai`：云端/兼容 API
- `requests`：可选，用于 Ollama 本地调用
- `pydantic`：结构化输出校验（抽取任务必备）
- `numpy`：简单统计/相似度（可选）

---

## 1. 评测分层：你到底在评什么

把评测拆开会更清晰：

1) **模型层**（LLM）：语言质量、遵循指令、结构化输出成功率
2) **检索层**（RAG）：召回有没有把正确资料找出来、重排有没有排对
3) **系统层**（产品）：延迟、失败率、成本、用户满意度

你会发现：

- 生成回答不稳定，可能不是模型问题，而是检索给错资料
- 用户体验差，可能不是模型慢，而是你没做 streaming/缓存

---

## 离线评测

- **任务型指标**：抽取/分类/匹配的准确率、F1
- **生成型指标**：更建议“人评 + 规则校验”，纯自动指标容易误导
- **RAG 指标**：
  - 召回是否包含正确 chunk（Recall@k）
  - 重排是否把正确 chunk 提前（MRR/NDCG）

离线评测的关键是：**可重复**。

最小闭环包含 3 件事：

- 固定评测集（JSONL）
- 固定运行配置（模型名、温度、prompt 版本、检索参数）
- 固定输出记录（原始输出 + 解析结果 + 耗时 + 失败原因）

下面给你一个“能跑通就很强”的最小离线评测框架。

---

## 2. 动手：做一个最小离线评测框架（JSONL + runner）

我们先评两类你最常用的任务：

- **结构化抽取**（上一章 Prompt 工程）
- **RAG 带引用问答**（上一章 RAG）

### 2.1 结构化抽取评测集（JSONL）

新建 `eval/extract_eval.jsonl`：

```json
{"id":"e1","text":"我刚买的耳机左耳没声音，能不能退货？订单号我找不到了。","expected":{"intent":"complaint","need_more_info":true}}
{"id":"e2","text":"我想买你们的蓝牙耳机，有没有推荐？","expected":{"intent":"buy","need_more_info":false}}
{"id":"e3","text":"怎么查物流？","expected":{"intent":"ask","need_more_info":false}}
```

说明：

- 先只评关键字段（例如 intent / need_more_info），避免一开始就追求完美
- 评测集从 20 条开始就足够（先跑通）

### 2.2 抽取任务 runner（带校验与统计）

新建 `eval/run_extract_eval.py`：

```python
import json
import os
import time
from typing import Any

from openai import OpenAI
from pydantic import BaseModel, Field, ValidationError


class ExtractResult(BaseModel):
  intent: str
  summary: str | None = None
  entities: list[str] = Field(default_factory=list)
  need_more_info: bool = False
  missing_fields: list[str] = Field(default_factory=list)


SYSTEM = """你是一个严谨的助手。
规则：
1) 只根据 <input> 中提供的信息完成任务，不要编造。
2) 如果信息不足，need_more_info=true，并在 missing_fields 中列出缺少字段。
3) 必须输出 JSON，且只能输出 JSON。
"""


def build_user(text: str) -> str:
  return f"""任务：对用户文本做意图识别与实体抽取。

<input>
{text}
</input>

<output_format>
JSON 字段：
- intent: ask|complaint|buy|other
- summary: string
- entities: string[]
- need_more_info: boolean
- missing_fields: string[]
</output_format>
"""


def call_llm(client: OpenAI, model: str, text: str) -> str:
  resp = client.chat.completions.create(
    model=model,
    temperature=0,
    messages=[
      {"role": "system", "content": SYSTEM},
      {"role": "user", "content": build_user(text)},
    ],
  )
  return resp.choices[0].message.content


def safe_parse(raw: str) -> tuple[ExtractResult | None, str | None]:
  try:
    return ExtractResult.model_validate_json(raw), None
  except ValidationError as e:
    return None, str(e)


def score(pred: ExtractResult, expected: dict[str, Any]) -> dict[str, bool]:
  result = {}
  for k, v in expected.items():
    result[k] = getattr(pred, k) == v
  return result


if __name__ == "__main__":
  client = OpenAI(
    api_key=os.getenv("OPENAI_API_KEY"),
    base_url=os.getenv("OPENAI_BASE_URL"),
  )
  model = os.getenv("OPENAI_MODEL", "gpt-4o-mini")

  cases = [json.loads(line) for line in open("eval/extract_eval.jsonl", "r", encoding="utf-8")]

  passed = 0
  parsed_ok = 0
  total = len(cases)
  failures = []

  t0 = time.time()
  for c in cases:
    raw = call_llm(client, model, c["text"])
    pred, err = safe_parse(raw)
    if pred is None:
      failures.append({"id": c["id"], "type": "parse", "error": err, "raw": raw})
      continue
    parsed_ok += 1

    s = score(pred, c["expected"])
    ok = all(s.values())
    if ok:
      passed += 1
    else:
      failures.append({"id": c["id"], "type": "mismatch", "score": s, "raw": raw, "pred": pred.model_dump()})

  dt = time.time() - t0
  print(f"Total: {total}")
  print(f"Parsed OK: {parsed_ok}/{total} ({parsed_ok/total:.1%})")
  print(f"All Expected Fields Match: {passed}/{total} ({passed/total:.1%})")
  print(f"Elapsed: {dt:.2f}s, Avg: {dt/total:.2f}s/case")

  os.makedirs("eval/out", exist_ok=True)
  out_path = f"eval/out/extract_failures_{int(time.time())}.json"
  json.dump(failures, open(out_path, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
  print("Failures saved to:", out_path)
```

运行：

```bash
python eval/run_extract_eval.py
```

你会得到三个核心指标：

- 解析成功率（Parsed OK）
- 关键字段命中率（All Expected Fields Match）
- 平均耗时

这三个指标对 Prompt 迭代非常有用。

---

## 3. RAG 的离线评测：先从“引用正确”开始

RAG 一开始不要追求“回答像人一样好”，先追求：

- **回答基于检索到的上下文**
- **引用能对得上**

### 3.1 最小 RAG 评测集

新建 `eval/rag_eval.jsonl`：

```json
{"id":"r1","question":"收到货 10 天还能无理由退货吗？","must_cite":["退货政策","7 天"]}
{"id":"r2","question":"退款多久能到账？","must_cite":["1-5"]}
{"id":"r3","question":"怎么查物流？","must_cite":["物流"]}
```

这里的 `must_cite` 是最朴素的规则：答案里必须出现某些关键字（或引用编号）。

### 3.2 一个可落地的 RAG 评分思路

- **Citation Coverage**：回答里是否出现 `[C1]` 这类引用标记
- **Must-Cite Hit**：回答是否包含必须信息（关键字匹配 / 规则校验）
- **No-Context Refusal**：资料不足时是否拒答（避免胡编）

你可以先用规则做自动评测，再逐步引入人评。

---

## 人工评测（强烈建议）

- 正确性（是否基于资料）
- 完整性（是否漏关键点）
- 可读性（结构是否清晰）
- 风险（是否含敏感/违规/误导）

建议你把人评做成一张 1 分钟能填完的表（每项 0/1/2）：

- 正确性：0=错误/胡编，1=部分正确，2=正确
- 引用：0=无引用或错引，1=有引用但不完整，2=引用清晰可验证
- 完整性：0=漏关键点，1=覆盖部分，2=覆盖全面
- 表达：0=难读，1=一般，2=清晰

人评的价值在于：它能发现规则评测发现不了的问题（比如答非所问、口吻不合适）。

---

## 线上评测

- A/B：对比不同 prompt、检索策略、模型版本
- 关键指标：
  - 成功率（任务完成）
  - 平均延迟、P95
  - 成本/请求
  - 投诉率/回滚率

  线上评测（A/B）你可以先从最简单的开始：

  - 只改一个变量（prompt 或检索参数）
  - 其它全部固定（模型版本、top_k、重排策略）
  - 观测窗口至少覆盖一个完整业务周期（避免偶然波动）

  ---

## 可观测性（你需要记录什么）

- prompt（脱敏后）与模型版本
- 检索 query、top_k 结果与最终拼接上下文
- 生成输出、解析结果（如果有函数调用/JSON）
- 耗时分解：检索、重排、生成

补充：建议你把下面这些字段也记下来（未来排查会救命）：

- `request_id` / `trace_id`
- prompt 版本号（你自己维护一个字符串即可）
- 温度、top_p、max_tokens
- RAG：chunk ids、top_k 分数、最终拼接的 token 数
- 失败类型：超时 / 解析失败 / 无资料拒答 / 其它

最重要的一句话：

> 评测集 + 可观测日志 = 你迭代系统的“回放能力”。

---

## 本章验收

做到下面两件事就算通过：

- 你能跑通 `eval/run_extract_eval.py`，并得到解析成功率与命中率
- 你能根据失败样本文件（`eval/out/*.json`）定位是 Prompt 问题还是数据问题

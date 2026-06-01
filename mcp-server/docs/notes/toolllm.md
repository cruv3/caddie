# ToolLLM: Facilitating Large Language Models to Master 16000+ Real-world APIs

## Metadata

- **Authors**: Yujia Qin, Shihao Liang, Yining Ye, Kunlun Zhu, Lan Yan, Yaxi Lu, Yankai Lin, Xin Cong, Xiangru Tang, Bill Qian, Sihan Zhao, Lauren Hong, Runchu Tian, Ruobing Xie, Jie Zhou, Mark Gerstein, Dahai Li, Zhiyuan Liu, Maosong Sun
- **Affiliations**: Tsinghua University (THUNLP / DCST); ModelBest Inc.; Renmin University of China; Yale; WeChat AI / Tencent
- **Venue + year**: ICLR 2024 (Spotlight); preprint July 2023
- **arXiv**: https://arxiv.org/abs/2307.16789
- **First read**: <TODO>
- **Relevance to thesis**: **Methodological.** Informs how to evaluate Caddie's MCP-tool-using agent against scaled, realistic tool inventories; the DFSDT planner contrasts with Caddie's plan/confirm loop.

## TL;DR (my words, after reading)

Introduces ToolBench (16,464 real REST APIs from RapidAPI across 49 categories), ToolEval, and ToolLLaMA, a LLaMA fine-tune with a DFS-based decision-tree planner and a neural API retriever. Open-source models reach ChatGPT-level tool use.

## Direct quotes

> "Open-source LLMs ... remain significantly limited in tool-use capabilities ... using external tools (APIs) to fulfill instructions." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **DFSDT planner** — depth-first search over decision tree of tool calls. (source: §3)
- **Retrieval-augmented tool selection** — neural retriever picks candidate APIs from a large registry. (source: §4)

## How I plan to use this in the thesis

- **Section**: Related Work (tool-use scaling).
- **Role**: **Methodological reference.**
- **Specific claims it supports**:
  - "ToolLLM is the largest-scale tool-use benchmark. Caddie operates at much smaller scale (~20 tools + 10 skills), but the DFSDT vs. linear-ReAct contrast is relevant to a discussion of search depth in agent loops."

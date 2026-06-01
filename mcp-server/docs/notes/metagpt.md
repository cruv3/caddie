# MetaGPT: Meta Programming for a Multi-Agent Collaborative Framework

## Metadata

- **Authors**: Sirui Hong, Mingchen Zhuge, Jiaqi Chen, Xiawu Zheng, Yuheng Cheng, Ceyao Zhang, Jinlin Wang, Zili Wang, Steven Ka Shing Yau, Zijuan Lin, Liyang Zhou, Chenyu Ran, Lingfeng Xiao, Chenglin Wu, Jürgen Schmidhuber
- **Affiliations**: DeepWisdom; KAUST (Schmidhuber, Zhuge); Xiamen University; CUHK Shenzhen; Nanjing University; UPenn (mixed)
- **Venue + year**: ICLR 2024 (arXiv August 2023)
- **arXiv**: https://arxiv.org/abs/2308.00352
- **Project**: https://github.com/geekan/MetaGPT
- **First read**: <TODO>
- **Relevance to thesis**: **Comparator.** Multi-agent SOPs are an alternative organisation strategy to Caddie's single-agent + curated-skills approach.

## TL;DR (my words, after reading)

Encodes human Standard Operating Procedures (SOPs) — product manager, architect, engineer, QA — into role prompts and a structured message protocol, letting multiple LLM agents collaborate on software tasks with fewer hallucinations than ad-hoc chats.

## Direct quotes

> "Encodes Standardized Operating Procedures (SOPs) into prompt sequences for streamlined workflows." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Standardized Operating Procedures (SOPs) as prompts** — encode professional workflows into role prompts. (source: §1)
- **Multi-agent collaboration with structured messaging** — agents exchange typed messages, not free-form chat. (source: §3)

## How I plan to use this in the thesis

- **Section**: Related Work (alternative organisations).
- **Role**: **Comparator.**
- **Specific claims it supports**:
  - "MetaGPT achieves robustness through structured multi-agent collaboration. Caddie takes the opposite path — single agent + curated, human-readable skill files + runtime user oversight. Both attack hallucination/instability, but the user-visibility property is unique to Caddie."

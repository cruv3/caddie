# AndroidLab: Training and Systematic Benchmarking of Android Autonomous Agents

## Metadata

- **Authors**: Yifan Xu, Xiao Liu, Xueqiao Sun, Siyi Cheng, Hao Yu, Hanyu Lai, Shudan Zhang, Dan Zhang, Jie Tang, Yuxiao Dong
- **Affiliations**: Tsinghua University (THUDM / KEG); Zhipu AI
- **Venue + year**: arXiv preprint, Oct 2024
- **arXiv**: https://arxiv.org/abs/2410.24024
- **Project**: https://github.com/THUDM/Android-Lab
- **First read**: <TODO>
- **Relevance to thesis**: **Benchmark** — likely evaluation harness/dataset; lets Caddie be compared against trained open baselines under identical tasks.

## TL;DR (my words, after reading)

Unified Android environment with both XML and screenshot modalities, a 138-task / 9-app reproducible benchmark, and an instruction-tuning dataset that lifts open LLMs from ~5% to ~21% task success.

## Direct quotes

> "Autonomous agents have become increasingly important for interacting with the real world." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Dual-modality environment** — XML *and* screenshot observation, switchable. (source: §3)
- **Instruction-tuning dataset** — released alongside benchmark; ~5% → ~21% open-LLM lift. (source: §4)

## How I plan to use this in the thesis

- **Section**: Evaluation methodology.
- **Role**: **Benchmark.**
- **Specific claims it supports**:
  - "If a quantitative comparison is included in the thesis, AndroidLab's dual-modality observation matches Caddie's architecture (accessibility tree + optional screenshot) better than pure-vision benchmarks. Caddie can be evaluated against AndroidLab open baselines."

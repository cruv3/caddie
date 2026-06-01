# OSWorld: Benchmarking Multimodal Agents for Open-Ended Tasks in Real Computer Environments

## Metadata

- **Authors**: Tianbao Xie, Danyang Zhang, Jixuan Chen, Xiaochuan Li, Siheng Zhao, Ruisheng Cao, Toh Jing Hua, Zhoujun Cheng, Dongchan Shin, Fangyu Lei, Yitao Liu, Yiheng Xu, Shuyan Zhou, Silvio Savarese, Caiming Xiong, Victor Zhong, Tao Yu
- **Affiliations**: The University of Hong Kong; CMU; Salesforce Research; University of Waterloo
- **Venue + year**: NeurIPS 2024 (Datasets & Benchmarks)
- **arXiv**: https://arxiv.org/abs/2404.07972
- **Project**: https://os-world.github.io/ — Code: https://github.com/xlang-ai/OSWorld
- **First read**: <TODO>
- **Relevance to thesis**: **Cross-platform reference.** The desktop analogue of the smartphone problem; its execution-based grading and cross-app tasks directly motivate similar evaluation on Android.

## TL;DR (my words, after reading)

First scalable real-OS benchmark (Ubuntu, Windows, macOS) with 369 cross-application tasks spanning office suites, browsers, file managers, IDEs, and the OS shell, all scored by execution-based scripts. Reveals that even GPT-4V tops out around 12% vs 72% human.

## Direct quotes

> "Existing benchmarks either lack an interactive environment or are limited to environments specific to certain applications or domains." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Cross-application, cross-OS benchmark** — single agent must work across many apps. (source: §3)
- **Execution-based verification scripts** — reproducible automated scoring. (source: §4)

## How I plan to use this in the thesis

- **Section**: Related Work (desktop analogue).
- **Role**: **Cross-platform reference.**
- **Specific claims it supports**:
  - "The OSWorld GPT-4V result (12% vs. 72% human) and AndroidWorld baseline (30.6%) jointly establish that GUI agency is unsolved across platforms. The interesting research questions are no longer 'can it act' but 'how do users supervise it'."

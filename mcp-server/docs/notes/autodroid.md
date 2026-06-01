# AutoDroid: LLM-powered Task Automation in Android

## Metadata

- **Authors**: Hao Wen, Yuanchun Li, Guohong Liu, Shanhui Zhao, Tao Yu, Toby Jia-Jun Li, Shiqi Jiang, Yunhao Liu, Yaqin Zhang, Yunxin Liu
- **Affiliations**: Tsinghua University / Institute for AI Industry Research (AIR); University of Notre Dame; Microsoft Research; Shanghai AI Lab
- **Venue + year**: ACM MobiCom 2024 (arXiv Aug 2023)
- **arXiv**: https://arxiv.org/abs/2308.15272
- **DOI**: https://doi.org/10.1145/3636534.3649379
- **Project**: https://autodroid-sys.github.io/
- **First read**: <TODO>
- **Relevance to thesis**: **Technique.** Its UI-graph + memory injection is an alternative grounding strategy Caddie's accessibility-tree approach can be compared to.

## TL;DR (my words, after reading)

Combines an LLM with offline UI-graph exploration to build a "functionality-aware" UI representation and memory; lets GPT-4 or on-device Vicuna drive arbitrary Android apps. 90.9% action accuracy / 71.3% task success on a 158-task benchmark. MobiCom — i.e., systems venue, not HCI.

## Direct quotes

> "Mobile task automation is an attractive technique that aims to enable voice-based hands-free user interaction with smartphones." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Functionality-aware UI representation** — offline-built UI graph capturing what each screen does. (source: §3)
- **Memory injection** — runtime augmentation of LLM prompt with relevant UI-graph subgraph. (source: §4)
- **On-device Vicuna deployment** — paper demonstrates the system works with a 7B local LLM, not only with cloud GPT-4. (source: §6)

## How I plan to use this in the thesis

- **Section**: Related Work.
- **Role**: **Technique** (alternative grounding).
- **Specific claims it supports**:
  - "AutoDroid pre-explores apps to build a UI graph, then injects relevant graph fragments at runtime. Caddie skips offline exploration: skills are persisted only after a successful run, and the accessibility tree is consulted live. Trade-off: Caddie has no app warm-up but also no global UI map."
  - "AutoDroid's on-device Vicuna demonstration validates that local-LLM-driven Android automation is viable — direct supporting evidence for Caddie's local-first approach."

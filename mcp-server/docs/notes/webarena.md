# WebArena: A Realistic Web Environment for Building Autonomous Agents

## Metadata

- **Authors**: Shuyan Zhou, Frank F. Xu, Hao Zhu, Xuhui Zhou, Robert Lo, Abishek Sridhar, Xianyi Cheng, Tianyue Ou, Yonatan Bisk, Daniel Fried, Uri Alon, Graham Neubig
- **Affiliations**: Carnegie Mellon University; Inspired Cognition
- **Venue + year**: ICLR 2024 (arXiv July 2023)
- **arXiv**: https://arxiv.org/abs/2307.13854
- **Project**: https://webarena.dev — Code: https://github.com/web-arena-x/webarena
- **First read**: <TODO>
- **Relevance to thesis**: **Benchmark.** Defines the gold standard for outcome-based evaluation of GUI agents; relevant when justifying execution-based scoring of smartphone tasks.

## TL;DR (my words, after reading)

Self-hostable, fully functional replicas of four production-grade sites (e-commerce, GitLab, Reddit, CMS) plus a map and wiki, with 812 long-horizon tasks evaluated by program-checkable functional correctness rather than action-match.

## Direct quotes

> "Current agents are primarily created and tested in simplified synthetic environments, leading to a disconnect with real-world scenarios." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Execution-grounded benchmark** — success determined by inspecting the server state, not by matching agent trajectory. (source: §3)
- **Production-grade replicas** — full open-source clones of real SaaS systems. (source: §3)

## How I plan to use this in the thesis

- **Section**: Evaluation methodology.
- **Role**: **Benchmark.**
- **Specific claims it supports**:
  - "WebArena's outcome-based evaluation is the right primitive for Caddie too: caring about end-state lets us measure success even when human-in-the-loop interventions change the agent's path."

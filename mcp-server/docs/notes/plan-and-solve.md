# Plan-and-Solve Prompting: Improving Zero-Shot Chain-of-Thought Reasoning

## Metadata

- **Authors**: Lei Wang, Wanyu Xu, Yihuai Lan, Zhiqiang Hu, Yunshi Lan, Roy Ka-Wei Lee, Ee-Peng Lim
- **Affiliations**: Singapore Management University; SUTD; ECNU; Southwest Jiaotong U
- **Venue + year**: ACL 2023 (arXiv May 2023)
- **arXiv**: https://arxiv.org/abs/2305.04091
- **Project**: https://github.com/AGI-Edgerunners/Plan-and-Solve-Prompting
- **First read**: <TODO>
- **Relevance to thesis**: **Technique (directly applied to Caddie's system prompt).** Justifies the Plan: block now required at session start.

## TL;DR (my words, after reading)

Zero-shot CoT but with two explicit phases: (1) devise a plan, (2) execute. PS+ variant adds "pay attention to calculation / extract relevant variables" — reduces missing-step and calculation errors vs. vanilla zero-shot CoT.

## Direct quotes

> "Devise a plan to divide the entire task into smaller subtasks, and then carry out the subtasks according to the plan." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Plan-then-execute trigger** — "Let's first understand the problem and devise a plan to solve the problem. Then, let's carry out the plan and solve the problem step by step." (source: §3)

## How I plan to use this in the thesis

- **Section**: Prompting strategy + architecture.
- **Role**: **Technique (now in BASE_SYSTEM_PROMPT).**
- **Specific claims it supports**:
  - "Caddie's prompt now requires the first Thought of every session to emit a Plan: block listing expected apps and tool calls plus the on-screen success signal. Pinned in context, referenced by subsequent steps. Empirically grounded in Plan-and-Solve."
  - "Visible plan also empowers user oversight: the overlay shows the plan before any action — Bellotti & Edwards's 'choose' principle operationalised at the plan level."

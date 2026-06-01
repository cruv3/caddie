# Active Prompting with Chain-of-Thought for Large Language Models

## Metadata

- **Authors**: Shizhe Diao, Pengcheng Wang, Yong Lin, Rui Pan, Xiang Liu, Tong Zhang
- **Affiliations**: HKUST; UIUC
- **Venue + year**: ACL 2024 (arXiv February 2023, last revised 2024)
- **arXiv**: https://arxiv.org/abs/2302.12246
- **First read**: <TODO>
- **Relevance to thesis**: **Technique** — uncertainty-driven exemplar selection. Useful with Caddie's failure-mode log.

## TL;DR (my words, after reading)

Estimate per-question uncertainty (disagreement across k samples), then have humans annotate CoT only for the *most uncertain* questions to use as few-shot exemplars. Outperforms random / Auto-CoT and Self-Consistency on reasoning tasks.

## Direct quotes

> "Determine which questions are the most important and helpful ones to annotate from a pool of task-specific queries." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Uncertainty-driven exemplar selection** — annotate only high-disagreement cases. (source: §3)

## How I plan to use this in the thesis

- **Section**: Prompting strategy.
- **Role**: **Technique.**
- **Specific claims it supports**:
  - "Caddie maintains `docs/failure-mode-log.md` — exactly Active Prompting's 'pool of high-uncertainty queries' applied to phone-agency. A natural extension would be to inject the top-N failure-mode cases as few-shot exemplars in the system prompt for known-hard task categories (sliders, dark-mode toggles, etc.)."

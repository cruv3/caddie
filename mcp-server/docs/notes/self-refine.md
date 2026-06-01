# Self-Refine: Iterative Refinement with Self-Feedback

## Metadata

- **Authors**: Aman Madaan, Niket Tandon, Prakhar Gupta, Skyler Hallinan, Luyu Gao, Sarah Wiegreffe, Uri Alon, Nouha Dziri, Shrimai Prabhumoye, Yiming Yang, Shashank Gupta, Bodhisattwa Prasad Majumder, Katherine Hermann, Sean Welleck, Amir Yazdanbakhsh, Peter Clark
- **Affiliations**: CMU, AI2, U Washington, NVIDIA, UCSD, Google Research
- **Venue + year**: NeurIPS 2023 (arXiv March 2023)
- **arXiv**: https://arxiv.org/abs/2303.17651
- **Project**: https://selfrefine.info/ — Code: https://github.com/madaan/self-refine
- **First read**: <TODO>
- **Relevance to thesis**: **Technique** — pre-action self-critique for high-stakes irreversible actions.

## TL;DR (my words, after reading)

Same LLM generates output → critiques itself with a feedback prompt → revises. ~20% improvement across 7 tasks, no training. Establishes that single-model self-refinement is a viable prompt-engineering pattern, not just an architectural one.

## Direct quotes

> "Self-Refine does not require any supervised training data, additional training, or reinforcement learning." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Generator → Feedback → Refiner loop** — all the same LLM, all in prompts. (source: §3)

## How I plan to use this in the thesis

- **Section**: Architecture / Prompting strategy.
- **Role**: **Technique.**
- **Specific claims it supports**:
  - "Before any Caddie tool call flagged as irreversible (uninstall, send, pay, delete), a Self-Refine-style 2-line self-critique is injected: 'two reasons this could be wrong, one way to verify on screen'. Then re-emit the action. Cheap latency tax for a meaningful safety gain."

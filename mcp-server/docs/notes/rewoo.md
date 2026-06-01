# ReWOO: Decoupling Reasoning from Observations for Efficient Augmented LMs

## Metadata

- **Authors**: Binfeng Xu, Zhiyuan Peng, Bowen Lei, Subhabrata Mukherjee, Yuchen Liu, Dongkuan Xu
- **Affiliations**: NC State University; Microsoft Research (Mukherjee); Texas A&M (Lei)
- **Venue + year**: arXiv preprint, May 2023
- **arXiv**: https://arxiv.org/abs/2305.18323
- **First read**: <TODO>
- **Relevance to thesis**: **Technique.** Relevant to Caddie's latency/battery story on a phone. Canonical citation when discussing whether to interleave perception with reasoning during multi-step UI actions.

## TL;DR (my words, after reading)

Separates an LLM "Planner" that emits the full tool-call plan up-front (with variable placeholders) from a "Worker" that executes tools and a "Solver" that produces the final answer. Avoids re-prompting the LLM after every observation; ~5× token reduction and +4% accuracy on HotpotQA vs. ReAct.

## Direct quotes

> "Huge computation complexity from redundant prompts and repeated execution." — Abstract (motivation)

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Plan-then-execute (observation-free planning)** — full plan emitted up-front. (source: §3)
- **Planner / Worker / Solver triple** — three roles, only one LLM-bound. (source: §3)

## How I plan to use this in the thesis

- **Section**: Architecture / Discussion.
- **Role**: **Technique (alternative).**
- **Specific claims it supports**:
  - "Caddie uses a linear ReAct loop (re-prompts after each observation) instead of ReWOO-style upfront planning. This is a deliberate choice: the overlay needs to surface each step to the user. ReWOO's batched plan defeats the user-visibility goal."

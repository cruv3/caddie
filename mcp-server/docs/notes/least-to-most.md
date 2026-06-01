# Least-to-Most Prompting Enables Complex Reasoning in Large Language Models

## Metadata

- **Authors**: Denny Zhou, Nathanael Schärli, Le Hou, Jason Wei, Nathan Scales, Xuezhi Wang, Dale Schuurmans, Claire Cui, Olivier Bousquet, Quoc Le, Ed Chi
- **Affiliation**: Google Research
- **Venue + year**: ICLR 2023 (arXiv May 2022)
- **arXiv**: https://arxiv.org/abs/2205.10625
- **First read**: <TODO>
- **Relevance to thesis**: **Technique** — decomposition pattern for multi-app smartphone tasks.

## TL;DR (my words, after reading)

Decompose a hard problem into ordered easier subproblems, solve sequentially, each subproblem's answer becomes context for the next. 99% on SCAN length-split (vs. 16% for CoT).

## Direct quotes

> "Reduces a complex problem into a list of subproblems, and then sequentially solves these subproblems." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Decomposer + Solver prompts** — two-prompt pattern. Decomposer lists subproblems; Solver is invoked once per subproblem with prior answers prepended. (source: §3)

## How I plan to use this in the thesis

- **Section**: Prompting strategy.
- **Role**: **Technique.**
- **Specific claims it supports**:
  - "For multi-app Caddie tasks ('book dentist + add to calendar + tell partner'), force an explicit decomposition stage before any Action. The ReAct loop then iterates one subproblem per outer step — natural checkpoints, prevents collapsing a 5-app task into one premature Action. Pairs with Plan-and-Solve."

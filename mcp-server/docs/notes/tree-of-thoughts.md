# Tree of Thoughts: Deliberate Problem Solving with Large Language Models

## Metadata

- **Authors**: Shunyu Yao, Dian Yu, Jeffrey Zhao, Izhak Shafran, Thomas L. Griffiths, Yuan Cao, Karthik Narasimhan
- **Affiliations**: Princeton University (Yao, Griffiths, Narasimhan); Google DeepMind (Yu, Zhao, Shafran, Cao)
- **Venue + year**: NeurIPS 2023
- **arXiv**: https://arxiv.org/abs/2305.10601
- **Project**: https://github.com/princeton-nlp/tree-of-thought-llm
- **First read**: <TODO>
- **Relevance to thesis**: **Technique** — alternative to Caddie's linear ReAct loop; relevant if the agent ever needs to deliberate over alternative UI plans.

## TL;DR (my words, after reading)

Generalises CoT to a search tree: the LLM generates multiple candidate "thoughts" per step, self-evaluates them, and runs BFS/DFS over the tree. Yields gains on Game of 24, creative writing, and mini crosswords vs. linear CoT.

## Direct quotes

> "Enables exploration over coherent units of text (thoughts) that serve as intermediate steps toward problem solving." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Tree of Thoughts (ToT)** — deliberate search over reasoning steps with self-evaluation. (source: Abstract)
- **Thought as a coherent unit** — not just tokens, but a self-contained reasoning step that can be branched on. (source: §3)

## How I plan to use this in the thesis

- **Section**: Related Work + Discussion (alternative reasoning architectures).
- **Role**: **Technique.**
- **Specific claims it supports**:
  - "Caddie uses a linear ReAct loop, not Tree-of-Thoughts. Future work could explore making branches visible to the user — letting the user pick among candidate plans on the overlay before execution. This would be a natural extension of the thesis's transparency principle from single-path to multi-path reasoning."

# ReAct: Synergizing Reasoning and Acting in Language Models

## Metadata

- **Authors**: Shunyu Yao, Jeffrey Zhao, Dian Yu, Nan Du, Izhak Shafran, Karthik Narasimhan, Yuan Cao
- **Affiliations**: Princeton University (Yao, Narasimhan); Google Research, Brain team (Zhao, Yu, Du, Shafran, Cao)
- **Venue + year**: ICLR 2023
- **arXiv**: https://arxiv.org/abs/2210.03629
- **Project**: https://react-lm.github.io
- **First read**: <TODO>
- **Relevance to thesis**: **Foundational.** The Thought → Action → Observation loop Caddie executes is literally the ReAct loop. Cite as the architectural baseline the thesis makes transparent.

## TL;DR (my words, after reading)

ICLR 2023 paper interleaving chain-of-thought reasoning with task-specific actions so the LLM plans, gathers information, and updates its plan in the same trajectory. Outperforms reasoning-only and acting-only baselines on HotpotQA, Fever, ALFWorld, and WebShop. The canonical reference for "LLM as agent with a tool-using loop."

## Direct quotes

> "Generate both reasoning traces and task-specific actions in an interleaved manner, allowing for greater synergy between the two." — Abstract

> "<TODO additional quote from full paper>" — §X p.Y

## Paraphrases / my notes

- <TODO after full read>

## Key concepts / terms

- **ReAct loop** — Thought / Action / Observation cycle. Reasoning trace is verbalised at each step. (source: §1, Fig. 1)
- **Synergy of reasoning + acting** — neither alone matches the combined approach. (source: Abstract)

## How I plan to use this in the thesis

- **Section**: Background / Related Work (LLM agent foundations).
- **Role**: **Foundational** — the loop Caddie executes.
- **Specific claims it supports**:
  - "Caddie's agent loop is a ReAct loop where every Thought, Action, and Observation is also rendered on the overlay — making the loop user-visible rather than internal."
  - "Cite as the architectural origin of Thought/Action/Observation. The thesis contribution is not the loop but the runtime transparency of the loop."

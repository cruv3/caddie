# Chain-of-Thought Prompting Elicits Reasoning in Large Language Models

## Metadata

- **Authors**: Jason Wei, Xuezhi Wang, Dale Schuurmans, Maarten Bosma, Brian Ichter, Fei Xia, Ed H. Chi, Quoc V. Le, Denny Zhou
- **Affiliation**: Google Research, Brain Team
- **Venue + year**: NeurIPS 2022
- **arXiv**: https://arxiv.org/abs/2201.11903
- **First read**: <TODO>
- **Relevance to thesis**: **Foundational.** Prerequisite for every "Thought:" line Caddie's agent emits. Cite as the origin of the reasoning component that ReAct builds on and the overlay surfaces.

## TL;DR (my words, after reading)

Prompting an LLM with exemplars that include intermediate reasoning steps unlocks dramatically better performance on arithmetic, commonsense, and symbolic reasoning — an emergent ability at sufficient model scale. The seminal "reasoning out loud" paper.

## Direct quotes

> "Generating a chain of thought — a series of intermediate reasoning steps — significantly improves the ability of large language models to perform complex reasoning." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Chain-of-Thought (CoT)** — exposing intermediate reasoning steps in the prompt rather than producing answer-only output. (source: Abstract)
- **Emergent ability at scale** — CoT only helps once the model is large enough (~100B params for the original Lambda/PaLM results). (source: §4)

## How I plan to use this in the thesis

- **Section**: Background.
- **Role**: **Foundational** — the reasoning component the overlay surfaces.
- **Specific claims it supports**:
  - "The reasoning content shown on Caddie's overlay pill is a chain-of-thought trace. The contribution of this thesis is not the CoT itself but exposing it to the user in a smartphone-appropriate UI."

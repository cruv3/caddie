# Self-Discover: Large Language Models Self-Compose Reasoning Structures

## Metadata

- **Authors**: Pei Zhou, Jay Pujara, Xiang Ren, Xinyun Chen, Heng-Tze Cheng, Quoc V. Le, Ed H. Chi, Denny Zhou, Swaroop Mishra, Huaixiu Steven Zheng
- **Affiliations**: Google DeepMind; USC
- **Venue + year**: NeurIPS 2024 (arXiv February 2024)
- **arXiv**: https://arxiv.org/abs/2402.03620
- **First read**: <TODO>
- **Relevance to thesis**: **Technique** — session-start reasoning template, complementary to Plan-and-Solve.

## TL;DR (my words, after reading)

At task-start, the LLM SELECTs from ~40 atomic reasoning modules ("critical thinking", "decompose", "use analogies"), ADAPTs them to the task, IMPLEMENTs them as a JSON reasoning structure. Up to +32% over CoT on BigBench-Hard at 10–40× less compute than CoT-SC.

## Direct quotes

> "LLMs select multiple atomic reasoning modules such as critical thinking and step-by-step thinking, and compose them into an explicit reasoning structure." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Select → Adapt → Implement** — three-stage meta-prompt yielding a task-specific reasoning template. (source: §3)

## How I plan to use this in the thesis

- **Section**: Prompting strategy (future work).
- **Role**: **Technique.**
- **Specific claims it supports**:
  - "Self-Discover suggests a more structured upgrade to Caddie's Plan: block — emit a JSON working-memory template once at session start (`{goal, expected_apps, verification_signal, rollback}`) rather than free-form text. Future work: evaluate whether a structured plan improves weak-model adherence over a prose plan."

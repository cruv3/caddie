# Self-Consistency Improves Chain of Thought Reasoning in Language Models

## Metadata

- **Authors**: Xuezhi Wang, Jason Wei, Dale Schuurmans, Quoc Le, Ed Chi, Sharan Narang, Aakanksha Chowdhery, Denny Zhou
- **Affiliation**: Google Research, Brain Team
- **Venue + year**: ICLR 2023 (arXiv March 2022)
- **arXiv**: https://arxiv.org/abs/2203.11171
- **First read**: <TODO>
- **Relevance to thesis**: **Technique** — cheap decoding-time improvement for high-stakes single-shot decisions in an agent loop.

## TL;DR (my words, after reading)

Replaces greedy CoT decoding with sampling N reasoning paths and majority-voting the *final answer* (not the reasoning trace). +17.9% on GSM8K, +11% on SVAMP, +12.2% on AQuA over single-path CoT.

## Direct quotes

> "It first samples a diverse set of reasoning paths instead of only taking the greedy one, and then selects the most consistent answer." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Self-consistency decoding** — sample k > 1 CoT rollouts at T > 0, majority-vote the final answer. (source: §2)

## How I plan to use this in the thesis

- **Section**: Architecture / Prompting strategy.
- **Role**: **Technique.**
- **Specific claims it supports**:
  - "For high-stakes single-shot decisions in Caddie (which app to open, which button to tap when ambiguous), have the agent internally enumerate 3 candidate action plans in one turn and pick the action that appears in the majority. Cheap variant of self-consistency without re-running the loop."

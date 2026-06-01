# Large Language Models are Zero-Shot Reasoners

## Metadata

- **Authors**: Takeshi Kojima, Shixiang Shane Gu, Machel Reid, Yutaka Matsuo, Yusuke Iwasawa
- **Affiliations**: University of Tokyo; Google Research
- **Venue + year**: NeurIPS 2022 (arXiv May 2022)
- **arXiv**: https://arxiv.org/abs/2205.11916
- **First read**: <TODO>
- **Relevance to thesis**: **Technique (cheapest possible improvement).** Adding the literal trigger "Let's think step by step" before the Thought slot of ReAct measurably improves intermediate reasoning quality on weak local models.

## TL;DR (my words, after reading)

Adding the literal trigger "Let's think step by step" before the answer turns vanilla LLMs into competent zero-shot reasoners across arithmetic and symbolic tasks — no exemplars needed.

## Direct quotes

> "LLMs are decent zero-shot reasoners by simply adding 'Let's think step by step' before each answer." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Two-stage zero-shot CoT** — fixed trigger phrase elicits reasoning, then a second prompt extracts the answer. (source: §3)

## How I plan to use this in the thesis

- **Section**: Prompting strategy.
- **Role**: **Technique.**
- **Specific claims it supports**:
  - "Caddie's BASE_SYSTEM_PROMPT now includes the literal trigger 'Let's think step by step' as the opening of every Thought (added 2026-06-01). Cite Kojima et al. as the empirical basis."

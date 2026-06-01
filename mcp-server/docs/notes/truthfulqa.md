# TruthfulQA: Measuring How Models Mimic Human Falsehoods

## Metadata

- **Authors**: Stephanie Lin, Jacob Hilton, Owain Evans
- **Affiliations**: University of Oxford (Future of Humanity Institute); OpenAI
- **Venue + year**: ACL 2022 (Long Papers), pp. 3214–3252
- **arXiv**: https://arxiv.org/abs/2109.07958
- **First read**: <TODO>
- **Relevance to thesis**: **Empirical evidence.** Concrete proof that fluent confidence ≠ correctness; justifies why a smartphone agent must let users *see and intervene* rather than trust output at face value.

## TL;DR (my words, after reading)

817-question benchmark across 38 categories where humans are likely to be wrong. Best model truthful on 58% vs. 94% human; larger models are *less* truthful (inverse scaling). Demonstrates "imitative falsehoods" — models reproduce confident human misconceptions.

## Direct quotes

> "The largest models were generally the least truthful." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Imitative falsehood** — models reproduce confident human misconceptions because training rewards plausibility, not truth. (source: §4)
- **Inverse scaling for truthfulness** — bigger models can be *less* truthful on some categories. (source: §5)

## How I plan to use this in the thesis

- **Section**: Motivation.
- **Role**: **Empirical evidence.**
- **Specific claims it supports**:
  - "TruthfulQA empirically establishes that LLM fluency does not correlate with truthfulness — sometimes anti-correlates. This justifies Caddie's design choice to verify agent claims through visible UI state rather than the agent's own self-report."

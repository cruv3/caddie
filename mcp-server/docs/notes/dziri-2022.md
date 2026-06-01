# Dziri et al. (2022) — On the Origin of Hallucinations in Conversational Models

## Metadata

- **Authors**: Nouha Dziri, Sivan Milton, Mo Yu, Osmar Zaiane, Siva Reddy
- **Affiliations**: University of Alberta / Mila / Amii; McGill / Mila; IBM Research; University of Alberta; McGill / Mila / Facebook CIFAR AI Chair
- **Venue + year**: NAACL 2022 Main Conference, pp. 5271–5285
- **arXiv**: https://arxiv.org/abs/2204.07931
- **First read**: <TODO>
- **Relevance to thesis**: **Empirical evidence.** Supports the design assumption that hallucination cannot be fully eliminated at the model layer, so runtime UI safeguards are necessary.

## TL;DR (my words, after reading)

Human study of knowledge-grounded dialogue benchmarks and SOTA models. Finds >60% of "gold" responses are hallucinated and that models *amplify* the hallucination rate beyond their training data.

## Direct quotes

> "Standard benchmarks consist of >60% hallucinated responses, leading to models that not only hallucinate but even amplify." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Hallucination amplification** — models hallucinate *more* than the training data, not less. (source: §5)
- **Inherited hallucination** — partly comes from data, not curable at model layer alone. (source: §5)

## How I plan to use this in the thesis

- **Section**: Motivation.
- **Role**: **Empirical evidence.**
- **Specific claims it supports**:
  - "Dziri et al. show hallucination is not a curable model bug — it is inherited and amplified from data. This grounds Caddie's design assumption: model-layer mitigation is insufficient, runtime UI safeguards are needed."

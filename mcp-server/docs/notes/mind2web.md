# Mind2Web: Towards a Generalist Agent for the Web

## Metadata

- **Authors**: Xiang Deng, Yu Gu, Boyuan Zheng, Shijie Chen, Samuel Stevens, Boshi Wang, Huan Sun, Yu Su
- **Affiliation**: The Ohio State University (OSU NLP Group)
- **Venue + year**: NeurIPS 2023 (Datasets & Benchmarks, Spotlight)
- **arXiv**: https://arxiv.org/abs/2306.06070
- **Project**: https://osu-nlp-group.github.io/Mind2Web/ — Code: https://github.com/OSU-NLP-Group/Mind2Web
- **First read**: <TODO>
- **Relevance to thesis**: **Cross-platform reference** — direct web analogue of the mobile "follow-NL-instruction-on-real-app" problem.

## TL;DR (my words, after reading)

First dataset of >2,000 open-ended tasks on 137 real, live websites across 31 domains, paired with raw DOM snapshots and human action traces. Introduces a two-stage candidate-ranking + generation pipeline that makes long DOMs tractable for LLMs.

## Direct quotes

> "We introduce Mind2Web, the first dataset for developing and evaluating generalist agents for the web that can follow language instructions to complete complex tasks on any website." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **DOM-snippet ranking** — a candidate-element retriever filters the DOM before the LLM generates an action. (source: §4)

## How I plan to use this in the thesis

- **Section**: Related Work (cross-platform).
- **Role**: **Cross-platform reference.**
- **Specific claims it supports**:
  - "Mind2Web's DOM-as-observation choice mirrors Caddie's accessibility-tree-as-observation. Both reduce a large UI surface to a tractable text representation before LLM consumption."

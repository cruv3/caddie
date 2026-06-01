# SeeAct: GPT-4V(ision) is a Generalist Web Agent, if Grounded

## Metadata

- **Authors**: Boyuan Zheng, Boyu Gou, Jihyung Kil, Huan Sun, Yu Su
- **Affiliation**: The Ohio State University
- **Venue + year**: ICML 2024 (arXiv January 2024)
- **arXiv**: https://arxiv.org/abs/2401.01614
- **Project**: https://osu-nlp-group.github.io/SeeAct/ — Code: https://github.com/OSU-NLP-Group/SeeAct
- **First read**: <TODO>
- **Relevance to thesis**: **Technique.** The plan-then-ground decomposition maps directly onto Caddie's overlay/intervention architecture; motivates exposing the grounded target to the user before tap dispatch.

## TL;DR (my words, after reading)

Decouples web action into visual planning (GPT-4V reads a screenshot) and grounding (mapping the plan to a concrete DOM element). Shows that LMM planning is strong but grounding — turning "click the red Subscribe button" into an HTML node — is the dominant failure mode.

## Direct quotes

> "We explore the potential of LMMs like GPT-4V as a generalist web agent that can follow natural language instructions to complete tasks on any given website." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Plan-then-ground decomposition** — explicit separation of visual planning from element-grounding. (source: §3)
- **Grounding bottleneck** — paper's central finding: LMMs plan well but ground badly. (source: §5)

## How I plan to use this in the thesis

- **Section**: Architecture / Discussion.
- **Role**: **Technique.**
- **Specific claims it supports**:
  - "SeeAct's empirical finding (planning > grounding for LMMs) supports Caddie's design choice to show the user the planned target *before* dispatch — a UI mechanism that compensates for the model's known grounding weakness."

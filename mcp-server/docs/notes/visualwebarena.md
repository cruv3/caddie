# VisualWebArena: Evaluating Multimodal Agents on Realistic Visually Grounded Web Tasks

## Metadata

- **Authors**: Jing Yu Koh, Robert Lo, Lawrence Jang, Vikram Duvvur, Ming Chong Lim, Po-Yu Huang, Graham Neubig, Shuyan Zhou, Ruslan Salakhutdinov, Daniel Fried
- **Affiliation**: Carnegie Mellon University
- **Venue + year**: ACL 2024
- **arXiv**: https://arxiv.org/abs/2401.13649
- **Project**: https://jykoh.com/vwa — Code: https://github.com/web-arena-x/visualwebarena
- **First read**: <TODO>
- **Relevance to thesis**: **Benchmark.** Visual-grounding emphasis aligns directly with smartphone screenshots-as-observation; SoM techniques transfer to overlay-based interventions.

## TL;DR (my words, after reading)

Extends WebArena to 910 visually grounded tasks (Classifieds, Shopping, Reddit) where inputs include images and goals reference visual content. Establishes that screenshot+SoM (Set-of-Marks) augmentation closes only a fraction of the gap to human performance (16.4% vs 88.7%).

## Direct quotes

> "The majority of existing benchmarks primarily focus on text-based agents, neglecting many natural tasks that require visual information to effectively solve." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Visually grounded web benchmark** — task descriptions reference image content. (source: §3)
- **Set-of-Marks prompting (SoM) for benchmarks** — formalised numbered overlays as evaluation primitive. (source: §4)

## How I plan to use this in the thesis

- **Section**: Related Work (visual grounding).
- **Role**: **Benchmark / technique.**
- **Specific claims it supports**:
  - "The agent-vs-human gap on VisualWebArena (16% vs. 89%) is empirical evidence that visual grounding is unsolved. Caddie should not claim to solve grounding — only to make grounding *failures visible* to the user so they can intervene."

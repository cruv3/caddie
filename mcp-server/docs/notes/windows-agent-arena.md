# Windows Agent Arena: Evaluating Multi-Modal OS Agents at Scale

## Metadata

- **Authors**: Rogerio Bonatti, Dan Zhao, Francesco Bonacci, Dillon Dupont, Sara Abdali, Yinheng Li, Yadong Lu, Justin Wagle, Kazuhito Koishida, Arthur Bucker, Lawrence Jang, Zack Hui
- **Affiliations**: Microsoft; Columbia University; Carnegie Mellon University
- **Venue + year**: arXiv September 2024 (CVPR-W 2025 track)
- **arXiv**: https://arxiv.org/abs/2409.08264
- **Project**: https://microsoft.github.io/WindowsAgentArena/ — Code: https://github.com/microsoft/WindowsAgentArena
- **First read**: <TODO>
- **Relevance to thesis**: **Benchmark.** Methodological reference for how to make GUI-agent evaluation reproducible and fast.

## TL;DR (my words, after reading)

Ports the OSWorld framework to a fully containerized, Azure-parallelizable Windows environment with 154 tasks across Edge, File Explorer, Settings, Office, VS Code, etc. Introduces the Navi agent and screen-parsing model and shows full benchmark runs in ~20 minutes.

## Direct quotes

> "Most benchmarks are limited to specific modalities or domains... and full benchmark evaluations are slow." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Containerized parallel evaluation** — entire benchmark runs in ~20 min via Azure parallelism. (source: §4)
- **Navi agent + screen parser** — companion model for vision-based Windows GUI agency. (source: §3)

## How I plan to use this in the thesis

- **Section**: Related Work + evaluation methodology.
- **Role**: **Benchmark.**
- **Specific claims it supports**:
  - "Reproducible, parallel evaluation infrastructure (Windows Agent Arena style) is necessary for any quantitative thesis claim. Caddie's evaluation should at minimum be reproducible from container images."

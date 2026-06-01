# Voyager: An Open-Ended Embodied Agent with Large Language Models

## Metadata

- **Authors**: Guanzhi Wang, Yuqi Xie, Yunfan Jiang, Ajay Mandlekar, Chaowei Xiao, Yuke Zhu, Linxi Fan, Anima Anandkumar
- **Affiliations**: NVIDIA; Caltech; UT Austin; Stanford; ASU
- **Venue + year**: arXiv 2023, TMLR 2024
- **arXiv**: https://arxiv.org/abs/2305.16291
- **Project**: https://voyager.minedojo.org/ — Code: https://github.com/MineDojo/Voyager
- **First read**: <TODO>
- **Relevance to thesis**: **Comparator.** Caddie's `skills/` directory of markdown skills is conceptually a curated, human-readable variant of Voyager's auto-grown skill library — useful contrast point.

## TL;DR (my words, after reading)

GPT-4-driven agent in Minecraft that proposes its own curriculum, writes executable JavaScript skills, stores them in a growing skill library, and retrieves them later via embedding lookup. Demonstrates lifelong learning without model updates. Key novelty is the agent *builds its own toolbox*.

## Direct quotes

> "Continuously explores the world, acquires diverse skills, and makes novel discoveries without human intervention." — Abstract

## Paraphrases / my notes

- <TODO after full read>

## Key concepts / terms

- **Skill library** — agent's growing collection of executable code skills, indexed by embedding. (source: §3)
- **Automatic curriculum** — agent proposes its own next challenge based on current capability. (source: §3)
- **Iterative prompting with self-verification** — agent writes code, runs it, checks output, fixes if wrong. (source: §3)

## How I plan to use this in the thesis

- **Section**: Related Work (skill libraries / persistence) + Discussion.
- **Role**: **Comparator** — Voyager's skill library is the closest analog to Caddie's `skills/` directory.
- **Specific claims it supports**:
  - "Voyager's skill library is automatically generated and embedding-indexed — opaque to humans. Caddie's skill files are human-readable Markdown by deliberate design: each skill is auditable by the user and the supervisor."
  - "Caddie inherits Voyager's idea of accumulating procedural knowledge across runs but inverts the curation policy: human-readable + replay-protected over auto-grown + opaque."

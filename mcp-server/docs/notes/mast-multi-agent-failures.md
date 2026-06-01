# Why Do Multi-Agent LLM Systems Fail? (MAST Taxonomy)

## Metadata

- **Authors**: Mert Cemri, Melissa Z. Pan, Shuyi Yang, Lakshya A. Agrawal, Bhavya Chopra, Rishabh Tiwari, Kurt Keutzer, Aditya Parameswaran, Dan Klein, Kannan Ramchandran, Matei Zaharia, Joseph E. Gonzalez, Ion Stoica
- **Affiliations**: UC Berkeley (primary); Intesa Sanpaolo
- **Venue + year**: arXiv 2025; presented at ICML / COLM workshops
- **arXiv**: https://arxiv.org/abs/2503.13657
- **First read**: <TODO>
- **Relevance to thesis**: **Empirical evidence.** Directly maps failure categories Caddie's visible-overlay design must address; the "verification" cluster is the strongest argument for human-in-the-loop confirmation on a phone.

## TL;DR (my words, after reading)

Annotates 200+ traces across seven multi-agent frameworks and derives **MAST**, a 14-mode taxonomy in three families: specification issues, inter-agent misalignment, task verification. Cohen's κ = 0.88 between annotators.

## Direct quotes

> "We identify 14 unique failure modes, organized into 3 overarching categories." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **MAST 14 failure modes** — empirical taxonomy of agentic failure. (source: §3)
- **Task-verification cluster** — agents declare success without checking. Direct parallel to Caddie's gemma "successfully deleted" hallucination. (source: §3)

## How I plan to use this in the thesis

- **Section**: Empirical analysis / Discussion.
- **Role**: **Empirical evidence.**
- **Specific claims it supports**:
  - "The MAST taxonomy's task-verification failure cluster is the *exact* category Caddie's visible overlay + state-based verification address. Future work: classify Caddie's observed failure modes against MAST to position the thesis's contribution precisely."

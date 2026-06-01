# Lost in the Middle: How Language Models Use Long Contexts

## Metadata

- **Authors**: Nelson F. Liu, Kevin Lin, John Hewitt, Ashwin Paranjape, Michele Bevilacqua, Fabio Petroni, Percy Liang
- **Affiliations**: Stanford, UC Berkeley, Samaya AI, Meta AI
- **Venue + year**: TACL 2024 (arXiv July 2023)
- **arXiv**: https://arxiv.org/abs/2307.03172
- **Code**: https://github.com/nelson-liu/lost-in-the-middle
- **First read**: <TODO>
- **Relevance to thesis**: **Design principle (directly applied to Caddie's prompt restructuring).** Justifies the top/middle/bottom restructuring of BASE_SYSTEM_PROMPT done 2026-06-01.

## TL;DR (my words, after reading)

U-shaped recall: information at the start or end of a long context is retrieved well; information in the middle is often lost — even by "long-context" models. Empirical study across multiple models including GPT-3.5, GPT-4, Claude, Llama-2.

## Direct quotes

> "Performance is often highest when relevant information occurs at the beginning or end of the input context." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **U-shaped recall curve** — start and end of context > middle. (source: §3)
- **Position-biased retrieval** — even with full context window, models attend disproportionately to extremes. (source: §3)

## How I plan to use this in the thesis

- **Section**: Architecture / Prompt design.
- **Role**: **Design principle (applied).**
- **Specific claims it supports**:
  - "Caddie's BASE_SYSTEM_PROMPT (2026-06-01 restructure) places critical rules (constitution, untrusted-input, terminal-call hard rule, anti-loop) at the TOP and restates them at the BOTTOM via a pre-termination checklist. Tool/skill/workflow content sits in the middle, where the model can re-read it on demand. Cite Liu et al. as the empirical basis for this layout."

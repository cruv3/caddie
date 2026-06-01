# Toolformer: Language Models Can Teach Themselves to Use Tools

## Metadata

- **Authors**: Timo Schick, Jane Dwivedi-Yu, Roberto Dessì, Roberta Raileanu, Maria Lomeli, Eric Hambro, Luke Zettlemoyer, Nicola Cancedda, Thomas Scialom
- **Affiliations**: FAIR / Meta AI; Universitat Pompeu Fabra (Dessì)
- **Venue + year**: NeurIPS 2023 (arXiv February 2023)
- **arXiv**: https://arxiv.org/abs/2302.04761
- **First read**: <TODO>
- **Relevance to thesis**: **Foundational.** The canonical citation for "LLMs as tool users." Anchors the tool-use literature review and motivates why later work (function calling, MCP) made the API surface explicit instead of inline.

## TL;DR (my words, after reading)

Shows an LLM can teach itself, via self-supervised API-call insertion and a perplexity-based filter, when and how to call external tools (calculator, search, calendar, translator, QA). Tool-augmented model outperforms much larger plain LMs on zero-shot downstream tasks.

## Direct quotes

> "LMs can teach themselves to use external tools via simple APIs and achieve the best of both worlds." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Self-supervised tool use** — model learns tool invocation from its own annotated data. (source: §3)
- **Inline API-call tokens** — calls embedded directly in the generation stream. (source: §3)

## How I plan to use this in the thesis

- **Section**: Background (tool use).
- **Role**: **Foundational.**
- **Specific claims it supports**:
  - "Caddie's tool use is *not* Toolformer-style (self-discovered, inline). Caddie uses curated, externally-specified MCP tools. Citing Toolformer positions the design choice: curated + visible vs. learned + inline."

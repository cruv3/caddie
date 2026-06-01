# Bansal et al. (2019) — Updates in Human-AI Teams: Performance/Compatibility Tradeoff

## Metadata

- **Authors**: Gagan Bansal, Besmira Nushi, Ece Kamar, Daniel S. Weld, Walter S. Lasecki, Eric Horvitz
- **Affiliations**: University of Washington (Bansal, Weld); Microsoft Research (Nushi, Kamar, Horvitz); University of Michigan (Lasecki)
- **Venue + year**: *AAAI 2019*, Vol. 33(01), pp. 2429–2437
- **DOI**: https://doi.org/10.1609/aaai.v33i01.33012429
- **OJS**: https://ojs.aaai.org/index.php/AAAI/article/view/4087
- **First read**: <TODO>
- **Relevance to thesis**: **Empirical evidence.** Supports thesis claims that swapping the underlying LLM without re-stabilising the UI breaks user expectations even when raw accuracy rises.

## TL;DR (my words, after reading)

When an AI is improved, team performance can drop because the user's mental model is now stale. Introduces *compatibility* as a first-class metric and a retraining loss that penalises *new* errors the user didn't previously anticipate.

## Direct quotes

> "Updates that increase AI performance may actually hurt team performance." — paper

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Performance/compatibility trade-off** — mental-model stability matters as much as accuracy. (source: paper)

## How I plan to use this in the thesis

- **Section**: Discussion (model swapping).
- **Role**: **Empirical evidence.**
- **Specific claims it supports**:
  - "Caddie's design choice to expose the agent's actions in a stable UI surface buffers the user against model swaps: even when the underlying LLM changes capability profile, the overlay still shows the agent's actions the same way."

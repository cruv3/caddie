# Ferret-UI: Grounded Mobile UI Understanding with Multimodal LLMs

## Metadata

- **Authors**: Keen You, Haotian Zhang, Eldon Schoop, Floris Weers, Amanda Swearngin, Jeffrey Nichols, Yinfei Yang, Zhe Gan
- **Affiliation**: Apple
- **Venue + year**: ECCV 2024 (arXiv April 2024)
- **arXiv**: https://arxiv.org/abs/2404.05719
- **Project**: https://machinelearning.apple.com/research/ferret-ui (no public weights)
- **First read**: <TODO>
- **Relevance to thesis**: **Model basis (alternative) / motivation.** Closest prior art to Caddie's target (mobile screenshot understanding from a VLM).

## TL;DR (my words, after reading)

Extends Ferret with "any-resolution" handling tailored to phone aspect ratios — splits a screenshot into two sub-images to magnify small UI elements. Trained on elementary tasks (icon/widget recognition, OCR) plus advanced ones (interaction reasoning); beats GPT-4V on elementary mobile-UI tasks. Closed weights.

## Direct quotes

> "Ferret-UI exhibits outstanding comprehension of UI screens and the capability to execute open-ended instructions." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Aspect-ratio-aware sub-image splitting** — handle phone tall screens by splitting top/bottom. (source: §3)
- **Mobile-widget referring/grounding head** — specialised for mobile UI elements. (source: §3)

## How I plan to use this in the thesis

- **Section**: Related Work + Discussion.
- **Role**: **Model basis (alternative) / motivation.**
- **Specific claims it supports**:
  - "Ferret-UI is the closest mobile-specific VLM. It demonstrates that mobile-UI-specialised models are technically possible but, being closed-weight Apple research, are unavailable for an open thesis project — motivating Caddie's choice of open Qwen-VL alternatives."

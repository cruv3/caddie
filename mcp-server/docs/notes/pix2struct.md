# Pix2Struct: Screenshot Parsing as Pretraining for Visual Language Understanding

## Metadata

- **Authors**: Kenton Lee, Mandar Joshi, Iulia Turc, Hexiang Hu, Fangyu Liu, Julian Eisenschlos, Urvashi Khandelwal, Peter Shaw, Ming-Wei Chang, Kristina Toutanova
- **Affiliation**: Google Research
- **Venue + year**: ICML 2023 (arXiv October 2022)
- **arXiv**: https://arxiv.org/abs/2210.03347
- **Project**: https://github.com/google-research/pix2struct
- **First read**: <TODO>
- **Relevance to thesis**: **Foundational.** Conceptual ancestor of every screen-aware VLM (ScreenAI, Ferret-UI, CogAgent). Justifies the design choice to feed raw screenshots to the agent rather than relying solely on accessibility trees.

## TL;DR (my words, after reading)

Pretrains an image-to-text encoder-decoder by predicting simplified HTML from masked webpage screenshots. Introduces variable-resolution patching and renders textual prompts *onto* the input image, unifying UI, document, illustration, and natural-image understanding in a single model.

## Direct quotes

> "We propose a screenshot parsing objective that requires predicting an HTML-based parse from a masked screenshot of a web page." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Screenshot-as-input pretraining** — treats pixel rendering of structured content as the canonical model interface, no OCR pipeline. (source: §3)
- **Variable-resolution patching** — same model handles many screen aspect ratios. (source: §3)

## How I plan to use this in the thesis

- **Section**: Background.
- **Role**: **Foundational.**
- **Specific claims it supports**:
  - "Pix2Struct is the design origin of treating screenshots as a first-class input modality for VLMs. Caddie inherits this assumption when feeding screenshots to the agent via Qwen-VL's vision branch."

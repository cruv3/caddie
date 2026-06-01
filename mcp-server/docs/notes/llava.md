# LLaVA: Visual Instruction Tuning

## Metadata

- **Authors**: Haotian Liu, Chunyuan Li, Qingyang Wu, Yong Jae Lee
- **Affiliations**: University of Wisconsin–Madison; Microsoft Research; Columbia University
- **Venue + year**: NeurIPS 2023 (Oral)
- **arXiv**: https://arxiv.org/abs/2304.08485
- **Project**: https://llava-vl.github.io/ — Code: https://github.com/haotian-liu/LLaVA
- **First read**: <TODO>
- **Relevance to thesis**: **Foundational.** Defines the open-source VLM recipe (CLIP + projector + LLM + GPT-synthesised SFT) every subsequent open VLM, including Qwen-VL and Ferret-UI, builds upon.

## TL;DR (my words, after reading)

Introduces LLaVA, the first open VLM trained by using a language-only GPT-4 to synthesize multimodal instruction-following data. A simple projection layer connects a CLIP vision encoder to an LLM, yielding ~85% of GPT-4's quality on a synthetic benchmark.

## Direct quotes

> "We present the first attempt to use language-only GPT-4 to generate multimodal language-image instruction-following data." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Visual instruction tuning** — GPT-synthesised SFT data as the bridge from vision encoder to instruction-following LMM. (source: §3)
- **CLIP + projector + LLM stack** — became the canonical open VLM architecture. (source: §3)

## How I plan to use this in the thesis

- **Section**: Background (open VLM stack).
- **Role**: **Foundational.**
- **Specific claims it supports**:
  - "The open VLM stack Caddie inherits from Qwen-VL traces directly to LLaVA's recipe. Cite as the origin of the architecture pattern."

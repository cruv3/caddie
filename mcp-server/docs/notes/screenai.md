# ScreenAI: A Vision-Language Model for UI and Infographics Understanding

## Metadata

- **Authors**: Gilles Baechler, Srinivas Sunkara, Maria Wang, Fedir Zubach, Hassan Mansoor, Vincent Etter, Victor Cărbune, Jason Lin, et al.
- **Affiliation**: Google Research
- **Venue + year**: IJCAI 2024
- **arXiv**: https://arxiv.org/abs/2402.04615
- **Project / datasets**: https://github.com/google-research-datasets/screen_annotation
- **First read**: <TODO>
- **Relevance to thesis**: **Technique.** Provides empirical grounding for why a mobile agent benefits from UI-aware pretraining rather than generic image-caption data.

## TL;DR (my words, after reading)

A 5B VLM built on PaLI with Pix2Struct's variable-patch strategy, trained on a novel "screen annotation" task (predict UI element type + bounding box from a screenshot). Sets SoTA on Multi-page DocVQA and WebSRC; releases three new UI datasets.

## Direct quotes

> "ScreenAI achieves new state-of-the-art results on UI- and infographics-based tasks." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Screen annotation pretraining** — teach the VLM the *schema* of a UI (button/icon/text/region) before downstream tasks. (source: §3)

## How I plan to use this in the thesis

- **Section**: Background (UI-specialised VLMs).
- **Role**: **Technique.**
- **Specific claims it supports**:
  - "ScreenAI shows UI-specialised pretraining materially improves screen-task performance. Caddie does not pretrain its own model — it relies on Qwen-VL's general capabilities + skill files for procedural knowledge — but this paper grounds the prediction that future open UI-specialised VLMs would slot into Caddie's architecture without changing the overlay/intervention layer."

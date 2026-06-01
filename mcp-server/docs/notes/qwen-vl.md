# Qwen-VL / Qwen2-VL / Qwen2.5-VL Series

## Metadata

- **Authors (Qwen-VL)**: Jinze Bai, Shuai Bai, Shusheng Yang, Shijie Wang, Sinan Tan, Peng Wang, Junyang Lin, Chang Zhou, Jingren Zhou
- **Authors (Qwen2-VL)**: Peng Wang, Shuai Bai, Sinan Tan, Shijie Wang, Zhihao Fan, Jinze Bai, Keqin Chen, Xuejing Liu, et al.
- **Authors (Qwen2.5-VL)**: Shuai Bai, Keqin Chen, Xuejing Liu, Jialin Wang, Wenbin Ge, Sibo Song, Kai Dang, Peng Wang, et al.
- **Affiliation**: Qwen Team, Alibaba Group
- **Venues / years**:
  - Qwen-VL: arXiv preprint, August 2023 — https://arxiv.org/abs/2308.12966
  - Qwen2-VL: arXiv preprint, September 2024 — https://arxiv.org/abs/2409.12191
  - Qwen2.5-VL: arXiv technical report, February 2025 — https://arxiv.org/abs/2502.13923
- **Projects**: https://github.com/QwenLM/Qwen-VL · https://github.com/QwenLM/Qwen2-VL · https://github.com/QwenLM/Qwen2.5-VL
- **First read**: <TODO>
- **Relevance to thesis**: **Model basis.** Caddie uses Qwen-VL as its on-device perception backbone; these three papers define the architecture, tokenization, and grounding behaviour the agent inherits.

## TL;DR (my words, after reading)

Alibaba's flagship open VL line. Qwen-VL introduced grounding-via-bounding-box tuples; Qwen2-VL added **Naive Dynamic Resolution** + M-RoPE for any-aspect-ratio screens; Qwen2.5-VL adds absolute-time video encoding, refined document parsing, and explicit GUI-agent training, making the 7B / 72B checkpoints a top open base for screenshot agents.

## Direct quotes

> "We introduce the Naive Dynamic Resolution mechanism, which enables the model to dynamically process images of varying resolutions into different numbers of visual tokens." — Qwen2-VL Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Naive Dynamic Resolution (Qwen2-VL)** — preserves aspect ratio of phone screenshots without distortion-inducing resizing. (source: Qwen2-VL §3)
- **M-RoPE** — multimodal rotary position embedding. (source: Qwen2-VL §3)
- **GUI-agent specialisation (Qwen2.5-VL)** — explicit training for GUI tasks built into the base model. (source: Qwen2.5-VL §4)

## How I plan to use this in the thesis

- **Section**: Background (model basis).
- **Role**: **Model basis.**
- **Specific claims it supports**:
  - "Caddie uses Qwen3.6-35b-a3b and gemma-4-e4b as primary test models. The Qwen-VL series papers document the architecture and capability baseline of the Qwen-family models we benchmark."

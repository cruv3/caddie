# CogAgent: A Visual Language Model for GUI Agents

## Metadata

- **Authors**: Wenyi Hong, Weihan Wang, Qingsong Lv, Jiazheng Xu, Wenmeng Yu, Junhui Ji, Yan Wang, Zihan Wang, et al.
- **Affiliations**: Tsinghua University (KEG / THUDM); Zhipu AI
- **Venue + year**: CVPR 2024 (Highlight)
- **arXiv**: https://arxiv.org/abs/2312.08914
- **Project**: https://github.com/THUDM/CogVLM (CogAgent branch)
- **First read**: <TODO>
- **Relevance to thesis**: **Technique.** Direct prior art for vision-only Android agents; informs whether the thesis should pursue pure-screenshot perception versus accessibility-tree hybrids.

## TL;DR (my words, after reading)

An 18B-parameter VLM purpose-built for GUI agents. A dual high-/low-resolution encoder pipeline (up to 1120×1120) lets it read small icons and text on desktop and mobile screenshots without needing HTML; outperforms text-DOM agents on PC and Android navigation.

## Direct quotes

> "CogAgent…outperforms LLM-based methods that consume extracted HTML text on both PC and Android GUI navigation tasks." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **High-resolution dual-encoder architecture** — separate encoders for fine-grained icon/text reading. (source: §3)
- **Pixel-only GUI perception** — no HTML, no accessibility tree needed. (source: §3)

## How I plan to use this in the thesis

- **Section**: Related Work (vision-first agents).
- **Role**: **Technique** (alternative architecture).
- **Specific claims it supports**:
  - "CogAgent demonstrates that pixel-only perception can match or beat HTML-based agents on GUI tasks. Caddie chooses a hybrid (accessibility tree primary + screenshot on demand) — a deliberate trade-off favouring on-device cost over best-in-class perception."

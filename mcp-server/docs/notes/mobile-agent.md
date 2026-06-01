# Mobile-Agent: Autonomous Multi-Modal Mobile Device Agent with Visual Perception

## Metadata

- **Authors**: Junyang Wang, Haiyang Xu, Jiabo Ye, Ming Yan, Weizhou Shen, Ji Zhang, Fei Huang, Jitao Sang
- **Affiliations**: Beijing Jiaotong University; Alibaba Group
- **Venue + year**: arXiv preprint, Jan 2024 (also ICLR 2024 LLM Agents Workshop)
- **arXiv**: https://arxiv.org/abs/2401.16158
- **Project**: https://github.com/X-PLUG/MobileAgent
- **First read**: <TODO>
- **Relevance to thesis**: **Foundational** — the seminal vision-only mobile MLLM agent that defines the design space Caddie inherits and adds transparency on top of.

## TL;DR (my words, after reading)

A purely vision-based mobile agent that uses an MLLM (GPT-4V) plus OCR/icon-detection tools to ground actions on screenshots, then plans and executes multi-step tasks across Android apps without relying on XML/accessibility trees. Establishes the vision-first design choice for mobile agents.

## Direct quotes

> "Mobile device agent based on Multimodal Large Language Models (MLLM) is becoming a popular application." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Pure-vision mobile agent loop** — no accessibility tree, only screenshots + OCR. (source: §3)
- **External visual perception tools** — OCR + icon detection run separately from the MLLM. (source: §3)

## How I plan to use this in the thesis

- **Section**: Related Work (mobile agent state of the art).
- **Role**: **Foundational / direct comparator.**
- **Specific claims it supports**:
  - "Mobile-Agent (2024) is the original of the vision-first mobile-agent design family. Caddie sits in the same family but uses accessibility-tree observations as primary signal and screenshots only on demand — a deliberate trade-off for reliability over flexibility on weak local models."

# HuggingGPT (JARVIS): Solving AI Tasks with ChatGPT and its Friends in Hugging Face

## Metadata

- **Authors**: Yongliang Shen, Kaitao Song, Xu Tan, Dongsheng Li, Weiming Lu, Yueting Zhuang
- **Affiliations**: Zhejiang University; Microsoft Research Asia
- **Venue + year**: NeurIPS 2023
- **arXiv**: https://arxiv.org/abs/2303.17580
- **First read**: <TODO>
- **Relevance to thesis**: **Foundational.** Direct precedent for Caddie's "LLM drives a smartphone of skills" framing. The plan-select-execute-summarise loop maps cleanly onto Caddie's agent loop and motivates MCP as the registry layer.

## TL;DR (my words, after reading)

Four-stage controller pattern — task planning → model selection → execution → response generation — where ChatGPT orchestrates many specialised HuggingFace models across vision, speech, NLP. Establishes "LLM as controller" for heterogeneous tools.

## Direct quotes

> "LLMs could act as a controller to manage existing AI models ... with language serving as a generic interface." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **LLM-as-controller** — single LLM orchestrates many tools. (source: §3)
- **Plan → Select → Execute → Summarise loop** — four-stage pipeline. (source: §3)

## How I plan to use this in the thesis

- **Section**: Background.
- **Role**: **Foundational.**
- **Specific claims it supports**:
  - "Caddie's agent loop is a phone-specific instance of the HuggingGPT controller pattern. The MCP layer is the registry that HuggingGPT did not standardise."

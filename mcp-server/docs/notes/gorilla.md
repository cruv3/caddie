# Gorilla: Large Language Model Connected with Massive APIs

## Metadata

- **Authors**: Shishir G. Patil, Tianjun Zhang, Xin Wang, Joseph E. Gonzalez
- **Affiliations**: UC Berkeley; Microsoft Research (Wang)
- **Venue + year**: NeurIPS 2024 (arXiv May 2023). Follow-up: Berkeley Function-Calling Leaderboard (BFCL)
- **arXiv**: https://arxiv.org/abs/2305.15334
- **First read**: <TODO>
- **Relevance to thesis**: **Technique.** Directly relevant to skill/tool discovery: when Caddie's MCP server exposes many skills, Gorilla's retriever-aware approach is the canonical reference for grounding tool selection in docs.

## TL;DR (my words, after reading)

Fine-tunes LLaMA on HuggingFace/TorchHub/TensorHub API documentation (APIBench) with retriever-aware training. Reduces hallucinated API calls and adapts to documentation drift at test time. Surpasses GPT-4 on API-call accuracy.

## Direct quotes

> "Their potential to effectively use tools via API calls remains unfulfilled." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Retriever-aware training (RAT)** — fine-tune with retriever in the loop to ground predictions in docs. (source: §3)
- **Documentation drift adaptation** — model adapts when APIs change without retraining. (source: §4)

## How I plan to use this in the thesis

- **Section**: Related Work (tool selection).
- **Role**: **Technique.**
- **Specific claims it supports**:
  - "Gorilla's documentation-grounded tool selection is the canonical reference for the problem Caddie's `smartphone_get_skill_*` tool descriptions address — the model picks the right skill from the description, with no separate retriever."

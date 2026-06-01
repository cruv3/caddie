# VoiceBench: Benchmarking LLM-Based Voice Assistants

## Metadata

- **Authors**: Yiming Chen, Xianghu Yue, Chen Zhang, Xiaoxue Gao, Robby T. Tan, Haizhou Li
- **Affiliations**: National University of Singapore (NUS); Chinese University of Hong Kong, Shenzhen (CUHK-Shenzhen) — verify against camera-ready
- **Venue + year**: TACL 2026 (accepted); preprint October 2024
- **arXiv**: https://arxiv.org/abs/2410.17196
- **Project**: https://github.com/MatthewCYM/VoiceBench
- **Leaderboard**: https://matthewcym.github.io/VoiceBench/
- **First read**: <TODO>
- **Relevance to thesis**: **Technique / motivation.** Defensible yardstick to compare Caddie's cascaded ASR+LLM agent against end-to-end speech-LLM work; argues robustness to speaker/environment shifts matters even in single-user smartphone settings.

## TL;DR (my words, after reading)

First holistic benchmark for LLM-based voice assistants. Nine datasets covering general knowledge, instruction-following, and safety, with both real and synthetic speech that varies speaker, environment, and content. Evaluates end-to-end audio LLMs (Mini-Omni2, GPT-4o-Audio) against cascaded Whisper+LLM baselines.

## Direct quotes

> "The absence of benchmarks…has hindered progress of LLM-based voice assistants development." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Multi-axis voice-assistant evaluation** — speaker, environment, content variation. (source: §3)
- **End-to-end vs cascaded comparison** — Mini-Omni2 / GPT-4o-Audio vs Whisper+LLM. (source: §4)

## How I plan to use this in the thesis

- **Section**: Architecture / Discussion.
- **Role**: **Methodological reference.**
- **Specific claims it supports**:
  - "Caddie uses a cascaded ASR+LLM voice path (Whisper → text → MCP). VoiceBench supplies the comparison framework against end-to-end alternatives, justifying the cascade as a *deliberate* design choice (separability, debuggability, mature ecosystem) rather than an oversight."

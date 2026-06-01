# Ultravox — A Fast Multimodal LLM for Real-Time Voice

## Metadata

- **Authors / maintainers**: Fixie AI team (Justin Uberti et al.)
- **Affiliation**: Fixie AI
- **Venue + year**: Open-source release, 2024–2026 (no peer-reviewed paper at time of writing)
- **GitHub**: https://github.com/fixie-ai/ultravox
- **HuggingFace**: https://huggingface.co/fixie-ai
- **First read**: <TODO>
- **Relevance to thesis**: **Technique / motivation.** The *alternative architecture* Caddie deliberately chose not to build — useful to frame Caddie's Whisper-cascade as a deliberate, reproducible choice.

## TL;DR (my words, after reading)

Extends an open-weight LLM (Llama 3 / Mistral / Gemma) with a multimodal projector that maps audio embeddings directly into the LLM's token space, skipping a discrete ASR stage. Reports ~150 ms time-to-first-token on A100, demonstrating that the cascaded transcribe-then-prompt pipeline isn't the only architectural option for voice agents.

## Direct quotes

> "A fast multimodal LLM for real-time voice." — Project tagline

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Audio-conditioned LLM without ASR bottleneck** — audio embeddings projected straight into the LLM's token space. (source: README)

## How I plan to use this in the thesis

- **Section**: Discussion (architectural alternatives).
- **Role**: **Counter-architecture.**
- **Specific claims it supports**:
  - "Ultravox demonstrates that end-to-end audio-conditioned LLMs are technically possible. Caddie's choice of a cascaded ASR + text-LLM stack is a deliberate trade-off favouring debuggability, model swap-ability, and on-device deployment over latency."

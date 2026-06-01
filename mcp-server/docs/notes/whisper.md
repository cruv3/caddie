# Whisper: Robust Speech Recognition via Large-Scale Weak Supervision

## Metadata

- **Authors**: Alec Radford, Jong Wook Kim, Tao Xu, Greg Brockman, Christine McLeavey, Ilya Sutskever
- **Affiliation**: OpenAI
- **Venue + year**: ICML 2023 (PMLR 202:28492–28518); arXiv preprint December 2022
- **arXiv**: https://arxiv.org/abs/2212.04356
- **Project**: https://github.com/openai/whisper
- **PDF**: https://cdn.openai.com/papers/whisper.pdf
- **First read**: <TODO>
- **Relevance to thesis**: **Foundational.** Whisper is the de-facto ASR primitive Caddie's voice channel calls (or whose on-device derivative it runs). Cite to ground the ASR component without needing to defend a particular model choice.

## TL;DR (my words, after reading)

Trains a Transformer encoder–decoder on 680k hours of weakly-supervised multilingual / multitask audio scraped from the web. Generalises zero-shot to standard ASR benchmarks, approaching human robustness without fine-tuning, and ships as an open foundation model.

## Direct quotes

> "When scaled to 680,000 hours of multilingual and multitask supervision, the resulting models generalize well." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Weakly-supervised large-scale ASR** — labelled-quality is replaced by labelled-volume. (source: Abstract)
- **Multilingual + multitask** — speech recognition, translation, language ID in one model. (source: §3)

## How I plan to use this in the thesis

- **Section**: Architecture (voice pipeline).
- **Role**: **Foundational.**
- **Specific claims it supports**:
  - "Caddie's voice channel transcribes user speech with Whisper (or an on-device equivalent). Citing the original paper grounds the ASR component without arguing about model choice."

# GPT-4V(ision) System Card

## Metadata

- **Authors**: OpenAI (corporate authorship)
- **Affiliation**: OpenAI
- **Venue + year**: OpenAI technical report, September 2023
- **URL**: https://openai.com/research/gpt-4v-system-card
- **PDF**: https://cdn.openai.com/papers/GPTV_System_Card.pdf
- **First read**: <TODO>
- **Relevance to thesis**: **Motivation.** Establishes the proprietary frontier that visible, on-device, user-controllable agents must approximate locally; justifies why Caddie explores open Qwen-VL alternatives.

## TL;DR (my words, after reading)

First system-level disclosure of GPT-4 with vision. Documents capabilities (image+text reasoning), safety evaluations, and deployment mitigations for a frontier closed-source VLM that became the de facto baseline for GUI-agent prompting.

## Direct quotes

> "GPT-4V can process images and text inputs to generate text outputs, and is trained using RLHF to be helpful, harmless, and honest." — System Card

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Production multimodal LMM** — first widely-available closed-source VL frontier model. (source: System Card)
- **RLHF for vision** — same alignment recipe extended to image inputs. (source: System Card)

## How I plan to use this in the thesis

- **Section**: Motivation / Background.
- **Role**: **Motivation.**
- **Specific claims it supports**:
  - "GPT-4V is the closed-frontier baseline. Caddie deliberately targets local open models (Qwen-VL family) for privacy and auditability, accepting some capability gap."

# Set-of-Mark Prompting Unleashes Extraordinary Visual Grounding in GPT-4V

## Metadata

- **Authors**: Jianwei Yang, Hao Zhang, Feng Li, Xueyan Zou, Chunyuan Li, Jianfeng Gao
- **Affiliations**: Microsoft Research, Redmond; University of Hong Kong; HKUST; UW–Madison
- **Venue + year**: arXiv preprint, October 2023
- **arXiv**: https://arxiv.org/abs/2310.11441
- **Project**: https://github.com/microsoft/SoM
- **First read**: <TODO>
- **Relevance to thesis**: **Technique.** Directly applicable to UI-transparency: numbered overlays make the agent's intended target visible to the user before the tap dispatches, enabling confirmation and user control.

## TL;DR (my words, after reading)

Overlays numbered/lettered marks (from SAM or similar segmenters) onto an image so a VLM can refer to regions by ID. Zero-shot GPT-4V + SoM beats fully fine-tuned RES models on RefCOCOg and underpins the "label-and-pick" interaction style now standard in GUI agents.

## Direct quotes

> "We present Set-of-Mark (SoM), a new visual prompting method, to unleash the visual grounding abilities of large multimodal models." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Set-of-Mark prompting** — numbered visual marks make VLM output a discrete, auditable choice. (source: §3)

## How I plan to use this in the thesis

- **Section**: Architecture / Design.
- **Role**: **Technique.**
- **Specific claims it supports**:
  - "Set-of-Mark is the canonical mechanism for making a VLM's chosen target *user-inspectable*. Caddie's overlay extends this principle: instead of showing marks to the model alone, the same marks are visible to the user so they can confirm or override before dispatch."

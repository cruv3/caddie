# Constitutional AI: Harmlessness from AI Feedback

## Metadata

- **Authors**: Yuntao Bai, Saurav Kadavath, Sandipan Kundu, Amanda Askell, et al. (Anthropic, ~50 authors)
- **Affiliation**: Anthropic
- **Venue + year**: arXiv December 2022 (preprint)
- **arXiv**: https://arxiv.org/abs/2212.08073
- **Project**: https://github.com/anthropics/ConstitutionalHarmlessnessPaper
- **First read**: <TODO>
- **Relevance to thesis**: **Design principle (now in BASE_SYSTEM_PROMPT).** Justifies the explicit constitution block replacing ad-hoc safety rules.

## TL;DR (my words, after reading)

Train a harmless assistant without human harm labels — instead use a written constitution + LLM self-critique + revision (SL-CAI) and AI-generated preference labels (RL-CAI). At inference time, the inference-time analogue is to write an explicit list of principles and a self-critique step.

## Direct quotes

> "The only human oversight is provided through a list of rules or principles, referred to as 'Constitutional AI'." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Constitution** — written list of principles the model must observe. (source: §1)
- **Self-critique-and-revise** — model critiques its own output against the constitution, then revises. (source: §3)

## How I plan to use this in the thesis

- **Section**: Architecture / Safety.
- **Role**: **Design principle (now in BASE_SYSTEM_PROMPT).**
- **Specific claims it supports**:
  - "Caddie's BASE_SYSTEM_PROMPT now contains a 6-line constitution covering messages, 2FA codes, monetary actions, deletion, untrusted text, and verification. The constitution generalises the previously ad-hoc anti-loop rule into a principled safety layer. Cite Bai et al. as the methodological basis."
  - "Each privileged tool call requires a 1-line check against the constitution before dispatch — Self-Refine pattern applied at the rule level."

# DroidBot-GPT: GPT-powered UI Automation for Android

## Metadata

- **Authors**: Hao Wen, Hongming Wang, Jiaxuan Liu, Yuanchun Li
- **Affiliations**: Tsinghua University, Institute for AI Industry Research (AIR); Beijing University of Posts and Telecommunications
- **Venue + year**: arXiv preprint, Apr 2023
- **arXiv**: https://arxiv.org/abs/2304.07061
- **Built on**: https://github.com/honeynet/droidbot
- **First read**: <TODO>
- **Relevance to thesis**: **Foundational (historical baseline).** Earliest LLM+Android mobile agent; demonstrates how far the field has come and where it started.

## TL;DR (my words, after reading)

The earliest LLM+Android mobile agent in the literature. Translates GUI state and available actions into text prompts for GPT, lets it pick the next action. Completes 39.4% of 33 tasks across 17 apps. Historical baseline showing the field's starting point.

## Direct quotes

> "Given a natural language description of a desired task, DroidBot-GPT can automatically generate and execute actions that navigate the app." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Text-prompt translation of Android GUI** — converts UI tree into a textual description the LLM can read. (source: §3)
- **Zero-shot LLM control** — no fine-tuning; relies on prompt engineering and GPT's general capabilities. (source: §3)

## How I plan to use this in the thesis

- **Section**: Related Work (origin story / chronological perspective).
- **Role**: **Foundational.**
- **Specific claims it supports**:
  - "DroidBot-GPT (April 2023) shows the field began at ~39% task completion on small benchmarks. Three years later (Mobile-Agent-v3.5 at 71.6 AndroidWorld) the technical baseline has roughly doubled. The remaining ~30 percentage points are increasingly about *how* the agent operates, not *whether* it can — the natural site for HCI contributions like Caddie."

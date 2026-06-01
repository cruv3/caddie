# openWakeWord — Open-Source Wake-Word Detection

## Metadata

- **Author**: David Scripka (dscripka)
- **Affiliation**: independent / open-source maintainer (used by Home Assistant / Rhasspy)
- **Venue + year**: GitHub project, active since 2023 (no peer-reviewed paper)
- **Project**: https://github.com/dscripka/openWakeWord
- **License**: Apache-2.0 code; CC-BY-NC-SA 4.0 pre-trained models
- **First read**: <TODO>
- **Relevance to thesis**: **Technique.** Cite as the concrete wake-word implementation Caddie uses, justifying the "always-listening but locally gated" privacy/latency profile.

## TL;DR (my words, after reading)

Lightweight wake-word framework built on Google's open audio embedding model, with custom heads trained on Piper-TTS-synthesised + room-augmented data. Runs 15–20 models simultaneously on a single Raspberry Pi 3 core; optionally chained with Silero VAD to suppress false positives.

## Direct quotes

> "Be fast enough for real-world usage, while maintaining ease of use and development." — README

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Embedding-based wake-word detection** — pre-trained audio embedding + small classifier head per word. (source: README)
- **Synthetic + augmented training data** — Piper-TTS + room IR augmentation. (source: README)

## How I plan to use this in the thesis

- **Section**: Architecture (voice gate).
- **Role**: **Technique.**
- **Specific claims it supports**:
  - "Caddie uses openWakeWord for always-listening wake detection. The on-device, low-CPU profile is essential for the thesis's privacy claims — no audio leaves the device before the wake word fires."

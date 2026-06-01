# AutoGPT: An Autonomous GPT-4 Experiment

## Metadata

- **Authors**: Toran Bruce Richards (Significant Gravitas) and open-source contributors
- **Affiliation**: Significant Gravitas Ltd. (UK)
- **Venue + year**: Open-source software, released March 30, 2023 (no peer-reviewed paper)
- **GitHub**: https://github.com/Significant-Gravitas/AutoGPT
- **Home**: https://agpt.co
- **Citation file**: https://github.com/Significant-Gravitas/AutoGPT/blob/master/CITATION.cff
- **First read**: <TODO>
- **Relevance to thesis**: **Counter-example + motivation.** AutoGPT is the opposite of Caddie's design philosophy — runs unattended, opaque, optimises for autonomy. Useful foil.

## TL;DR (my words, after reading)

Open-source agent that, given a high-level goal, recursively decomposes it into sub-tasks and executes them autonomously via web browsing, file I/O, code execution. Popularised the "autonomous LLM agent" concept outside academia. No formal paper — citations point to the GitHub repo.

## Direct quotes

> "A collection of tools and experimental open-source attempts to make GPT-4 fully autonomous." — GitHub README

## Paraphrases / my notes

- <TODO after deeper look at AutoGPT docs / blog>

## Key concepts / terms

- **Goal-driven autonomous loop** — plan → execute → critique → repeat with minimal human oversight. (source: README / blog)
- **Long-term memory + browser tools** — common AutoGPT plugin pattern. (source: README)

## How I plan to use this in the thesis

- **Section**: Introduction / Motivation + Related Work (counter-example).
- **Role**: **Counter-example / motivation** — the opposite of Caddie's design.
- **Specific claims it supports**:
  - "AutoGPT exemplifies the autonomy-first design philosophy that Caddie deliberately rejects: AutoGPT runs unattended, surfaces little of its reasoning, and is judged on whether the user gets a result — not on whether the user remained in the loop."
  - "The popular reception of AutoGPT shows there is user demand for autonomous agents, but the well-documented brittleness of unattended AutoGPT runs is empirical evidence that runtime transparency is needed."

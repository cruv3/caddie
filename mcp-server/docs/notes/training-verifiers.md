# Cobbe et al. (2021) — Training Verifiers to Solve Math Word Problems

## Metadata

- **Authors**: Karl Cobbe, Vineet Kosaraju, Mohammad Bavarian, Mark Chen, Heewoo Jun, Łukasz Kaiser, Matthias Plappert, Jerry Tworek, Jacob Hilton, Reiichiro Nakano, Christopher Hesse, John Schulman
- **Affiliation**: OpenAI
- **Venue + year**: arXiv 2021 (introduces GSM8K)
- **arXiv**: https://arxiv.org/abs/2110.14168
- **First read**: <TODO>
- **Relevance to thesis**: **Technique.** Provides the theoretical backbone for "the user (or accessibility tracker) acts as the verifier." Caddie's overlay is a *human-instantiated* verifier slot.

## TL;DR (my words, after reading)

Trains a separate verifier model to score candidate solutions; best-of-N with verifier scaling beats fine-tuned generators and scales better with data than further fine-tuning. Introduces the GSM8K benchmark in the process.

## Direct quotes

> "At test time, we generate many candidate solutions and select the one ranked highest by the verifier." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Generator–verifier asymmetry** — checking is easier than generating; an external critic outperforms a stronger generator. (source: §3)

## How I plan to use this in the thesis

- **Section**: Theoretical framing.
- **Role**: **Technique / theoretical backbone.**
- **Specific claims it supports**:
  - "Cobbe et al. establish the principle that an external verifier outperforms strengthening the generator. Caddie occupies the verifier slot with two complementary verifiers: the user (via overlay + intervention) and Android's accessibility tree (state-based verification of action outcomes)."

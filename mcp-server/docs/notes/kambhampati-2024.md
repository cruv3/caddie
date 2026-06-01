# Kambhampati et al. (2024) — LLMs Can't Plan, But Can Help Planning in LLM-Modulo

## Metadata

- **Authors**: Subbarao Kambhampati, Karthik Valmeekam, Lin Guan, Mudit Verma, Kaya Stechly, Siddhant Bhambri, Lucas Saldyt, Anil B. Murthy
- **Affiliation**: School of Computing & AI, Arizona State University (all authors)
- **Venue + year**: ICML 2024 (PMLR 235)
- **arXiv**: https://arxiv.org/abs/2402.01817
- **PMLR**: https://proceedings.mlr.press/v235/kambhampati24a.html
- **First read**: <TODO>
- **Relevance to thesis**: **Motivation.** Provides the *technical* justification (not just ethical) for why Caddie's agent must surface plans for user verification: the LLM literally cannot self-certify its own action plan.

## TL;DR (my words, after reading)

Argues that auto-regressive LLMs cannot reliably plan or self-verify, and proposes *LLM-Modulo*: LLMs as one component in a bi-directional loop with external verifiers (or humans). Makes the human-in-the-loop requirement *technical*, not merely safety-driven.

## Direct quotes

> "Auto-regressive LLMs cannot, by themselves, do planning or self-verification." — paper

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **LLM-Modulo** — generate-test loop with external (human or symbolic) verifier. (source: paper)
- **No-self-verification result** — LLMs cannot reliably check their own plans. (source: paper)

## How I plan to use this in the thesis

- **Section**: Motivation + theoretical framing.
- **Role**: **Motivation (technical).**
- **Specific claims it supports**:
  - "Kambhampati et al. supply the technical argument for Caddie's user-in-the-loop design: since LLMs cannot self-verify, the verifier slot must be filled — by symbolic verification (like VLAA-GUI's Completeness Verifier) or by the user. Caddie picks the latter because the user is already present and holding the phone."

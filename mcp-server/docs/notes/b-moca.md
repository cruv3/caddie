# B-MoCA: Benchmarking Mobile Device Control Agents across Diverse Configurations

## Metadata

- **Authors**: Juyong Lee, Taywon Min, Minyong An, Dongyoon Hahm, Haeone Lee, Changyeon Kim, Kimin Lee
- **Affiliation**: KAIST (Korea Advanced Institute of Science and Technology)
- **Venue + year**: ICLR 2024 Workshop (Spotlight); arXiv Apr 2024
- **arXiv**: https://arxiv.org/abs/2404.16660
- **Project**: https://b-moca.github.io/
- **First read**: <TODO>
- **Relevance to thesis**: **Benchmark.** Stresses robustness across UI variations — exactly the regime where a transparent agent that lets users intervene should outperform an opaque end-to-end policy.

## TL;DR (my words, after reading)

Android benchmark of 131 daily tasks with randomized device configurations (layouts, language, icon themes, fonts) to measure *generalization* of LLM/MLLM/IL-trained mobile agents rather than memorisation.

## Direct quotes

> "Mobile device control agents can largely enhance user interactions and productivity by automating daily tasks." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Configuration-randomized evaluation** — UI layout, language, theming randomised per trial. (source: §3)
- **Generalisation focus** — measures robustness, not memorisation. (source: §3)

## How I plan to use this in the thesis

- **Section**: Evaluation methodology + Discussion.
- **Role**: **Benchmark.**
- **Specific claims it supports**:
  - "B-MoCA's configuration randomisation tests exactly the brittleness Caddie's user-intervention mechanism mitigates: when the agent hits an unfamiliar layout, the user can correct mid-run. Caddie should be evaluated on B-MoCA-style robustness, not only on closed-environment task success."

# MobileAgentBench: An Efficient and User-Friendly Benchmark for Mobile LLM Agents

## Metadata

- **Authors**: Luyuan Wang, Yongyu Deng, Yiwei Zha, Guodong Mao, Qinmin Wang, Tianchen Min, Wei Chen, Shoufa Chen
- **Affiliations**: Carnegie Mellon University; University of Michigan; University of Hong Kong (mixed)
- **Venue + year**: arXiv preprint, Jun 2024
- **arXiv**: https://arxiv.org/abs/2406.08184
- **Project**: https://mobileagentbench.github.io/
- **First read**: <TODO>
- **Relevance to thesis**: **Benchmark.** State-based success detection is the right evaluation primitive for Caddie's claims (does not require trajectory match; tolerates human-in-the-loop deviations).

## TL;DR (my words, after reading)

100-task benchmark across 10 open-source Android apps with automated, state-based success detection; evaluates AppAgent, Mobile-Agent, etc., with reproducible runs.

## Direct quotes

> "Large language model (LLM)-based mobile agents are increasingly popular due to their capability to interact directly with mobile phone GUIs." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **State-based success detection** — checks end-state, not action trajectory. Tolerates different valid solution paths. (source: §3)

## How I plan to use this in the thesis

- **Section**: Evaluation methodology.
- **Role**: **Benchmark.**
- **Specific claims it supports**:
  - "MobileAgentBench's state-based scoring is methodologically appropriate for Caddie: when a user intervenes mid-run and the agent's path diverges, the run can still succeed. Trajectory-based scoring would punish exactly the interventions the thesis advocates for."

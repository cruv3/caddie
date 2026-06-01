# Mobile-Agent-v2: Mobile Device Operation Assistant with Effective Navigation via Multi-Agent Collaboration

## Metadata

- **Authors**: Junyang Wang, Haiyang Xu, Haitao Jia, Xi Zhang, Ming Yan, Weizhou Shen, Ji Zhang, Fei Huang, Jitao Sang
- **Affiliations**: Beijing Jiaotong University; Alibaba Group
- **Venue + year**: NeurIPS 2024 (arXiv June 2024)
- **arXiv**: https://arxiv.org/abs/2406.01014
- **Project**: https://github.com/X-PLUG/MobileAgent
- **First read**: <TODO>
- **Relevance to thesis**: **Direct comparator** — closest peer architecturally; benchmarks against it justify adding visible-state UI and user control rather than more sub-agents.

## TL;DR (my words, after reading)

Replaces v1's single-agent loop with a planning agent, decision agent, and reflection agent plus a memory unit to handle long task progress and focus-content navigation. Yields ~30% absolute gains over v1.

## Direct quotes

> "Mobile device operation tasks are increasingly becoming a popular multi-modal AI application scenario." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Three-agent decomposition** — planner / decider / reflector. (source: §3)
- **Memory unit** — explicit running state for long task progress. (source: §3)

## How I plan to use this in the thesis

- **Section**: Related Work.
- **Role**: **Direct comparator.**
- **Specific claims it supports**:
  - "Mobile-Agent-v2's multi-agent decomposition tackles long-horizon reliability through internal scaffolding. Caddie addresses the same problem by externalising the agent's state to the user — fewer internal layers, more user oversight."

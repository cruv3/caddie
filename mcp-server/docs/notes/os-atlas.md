# OS-Atlas: A Foundation Action Model for Generalist GUI Agents

## Metadata

- **Authors**: Zhiyong Wu, Zhenyu Wu, Fangzhi Xu, Yian Wang, Qiushi Sun, Chengyou Jia, Kanzhi Cheng, Zichen Ding, Liheng Chen, Paul Pu Liang, Yu Qiao
- **Affiliations**: Shanghai AI Laboratory; Shanghai Jiao Tong University; Xi'an Jiaotong University; University of Hong Kong; MIT; Nanjing University
- **Venue + year**: ICLR 2025 (arXiv Oct 2024)
- **arXiv**: https://arxiv.org/abs/2410.23218
- **Project**: https://github.com/OS-Copilot/OS-Atlas
- **First read**: <TODO>
- **Relevance to thesis**: **Technique.** Provides the open-weights grounding backbone Caddie could plug in instead of GPT-4V, useful if on-device or auditable models matter.

## TL;DR (my words, after reading)

Open-source GUI foundation action model trained on a 13M-element cross-platform (incl. Android) grounding corpus; closes the gap to commercial VLMs on grounding and OOD agent tasks.

## Direct quotes

> "Existing efforts in building GUI agents heavily rely on the availability of robust commercial Vision-Language Models." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Open-source GUI grounding foundation** — alternative to closed GPT-4V. (source: §1)
- **13M-element cross-platform corpus** — largest open UI-grounding dataset at time of release. (source: §3)

## How I plan to use this in the thesis

- **Section**: Related Work.
- **Role**: **Technique** (open model basis).
- **Specific claims it supports**:
  - "OS-Atlas demonstrates that fully-open-weight UI-grounding foundation models are now competitive with closed commercial offerings. This supports Caddie's design choice to use local LLMs (Qwen-VL family) without sacrificing too much capability — important for the privacy and auditability claims of the thesis."

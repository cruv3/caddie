# A Survey on Hallucination in Large Language Models

## Metadata

- **Authors**: Lei Huang, Weijiang Yu, Weitao Ma, Weihong Zhong, Zhangyin Feng, Haotian Wang, Qianglong Chen, Weihua Peng, Xiaocheng Feng, Bing Qin, Ting Liu
- **Affiliations**: Harbin Institute of Technology (SCIR); Huawei Inc.
- **Venue + year**: arXiv 2023 (revised 2024); ACM Transactions on Information Systems
- **arXiv**: https://arxiv.org/abs/2311.05232
- **First read**: <TODO>
- **Relevance to thesis**: **Motivation.** The canonical vocabulary for naming the failures Caddie's runtime visibility is meant to expose.

## TL;DR (my words, after reading)

Comprehensive taxonomy splitting hallucination into factuality-hallucination and faithfulness-hallucination. Maps causes across data, training, and inference, then catalogues detection and mitigation methods. Most-cited 2023 hallucination survey.

## Direct quotes

> "LLMs are prone to hallucination, generating plausible yet nonfactual content." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Factuality vs faithfulness hallucination** — wrong-about-the-world vs wrong-about-the-context. (source: §2 taxonomy)
- **Three-source causal model** — data / training / inference roots of hallucination. (source: §3)

## How I plan to use this in the thesis

- **Section**: Motivation / Background.
- **Role**: **Motivation.**
- **Specific claims it supports**:
  - "Caddie's session memory contains a documented example of *factuality hallucination* on a smartphone agent: gemma-4-e4b claimed 'Die YouTube App wurde erfolgreich gelöscht' while the app remained installed. The Huang et al. taxonomy gives the term to name this failure mode in the thesis."

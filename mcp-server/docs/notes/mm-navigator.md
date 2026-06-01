# MM-Navigator / GPT-4V in Wonderland: Large Multimodal Models for Zero-Shot Smartphone GUI Navigation

## Metadata

- **Authors**: An Yan, Zhengyuan Yang, Wanrong Zhu, Kevin Lin, Linjie Li, Jianfeng Wang, Jianwei Yang, Yiwu Zhong, Julian McAuley, Jianfeng Gao, Zicheng Liu, Lijuan Wang
- **Affiliations**: UC San Diego; Microsoft (Redmond / Azure AI); UC Santa Barbara; University of Wisconsin–Madison
- **Venue + year**: arXiv preprint, Nov 2023
- **arXiv**: https://arxiv.org/abs/2311.07562
- **Project**: https://github.com/zzxslp/MM-Navigator
- **First read**: <TODO>
- **Relevance to thesis**: **Motivation** — early demonstration that off-the-shelf LMMs can drive phones, motivating the question of how to make such control transparent.

## TL;DR (my words, after reading)

First systematic study of GPT-4V as a zero-shot smartphone GUI agent on iOS and Android screenshots, introducing Set-of-Mark style action grounding. 91% reasonable-action and 75% correct-action rates on iOS single-step.

## Direct quotes

> "MM-Navigator can interact with a smartphone screen as human users, and determine subsequent actions to fulfill given instructions." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Zero-shot smartphone GUI navigation** — no fine-tuning, only screenshot reasoning + numbered action tags. (source: §3)
- **Set-of-Mark action grounding for mobile** — adapts the SoM technique from desktop/web to phone screens. (source: §3)

## How I plan to use this in the thesis

- **Section**: Related Work / Motivation.
- **Role**: **Motivation.**
- **Specific claims it supports**:
  - "MM-Navigator (Nov 2023) established that off-the-shelf multimodal LLMs can drive smartphones zero-shot. Once this capability is granted, the open research question is no longer 'can it act' but 'how do we let users supervise it'. Caddie addresses that second question."

# WebGPT: Browser-assisted question-answering with human feedback

## Metadata

- **Authors**: Reiichiro Nakano, Jacob Hilton, Suchir Balaji, Jeff Wu, Long Ouyang, Christina Kim, Christopher Hesse, Shantanu Jain, Vineet Kosaraju, William Saunders, Xu Jiang, Karl Cobbe, Tyna Eloundou, Gretchen Krueger, Kevin Button, Matthew Knight, Benjamin Chess, John Schulman
- **Affiliation**: OpenAI
- **Venue + year**: arXiv preprint, December 2021 (revised 2022)
- **arXiv**: https://arxiv.org/abs/2112.09332
- **Project**: https://openai.com/index/webgpt/
- **First read**: <TODO>
- **Relevance to thesis**: **Foundational.** Earliest large-scale demonstration that an LLM can operate a browser via a structured action interface, setting the template later inherited by mobile GUI agents.

## TL;DR (my words, after reading)

Fine-tunes GPT-3 to answer long-form questions by interacting with a text-based browser through a small, fixed action vocabulary (search, click, scroll, quote). Imitation learning from human demonstrations followed by RLHF over answer quality and reference faithfulness.

## Direct quotes

> "We fine-tune GPT-3 to answer long-form questions using a text-based web-browsing environment, which allows the model to search and navigate the web." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Text-based browser action vocabulary** — minimal action set (search, click, scroll, quote) representable as text tokens. (source: §3)
- **RLHF for web navigation** — first large-scale browsing RLHF run. (source: §4)

## How I plan to use this in the thesis

- **Section**: Background / Related Work.
- **Role**: **Foundational** — the great-grandparent of every later GUI agent including Caddie.
- **Specific claims it supports**:
  - "WebGPT (2021) is the prototype of the 'LLM with constrained action vocabulary' pattern that Caddie's `smartphone_*` tool set inherits."

# Granite-Function Calling: Multi-task Learning of Granular Tool-Use Tasks

## Metadata

- **Authors**: Ibrahim Abdelaziz, Kinjal Basu, et al. (26 authors incl. David Cox, Salim Roukos, Luis Lastras)
- **Affiliation**: IBM Research
- **Venue + year**: EMNLP 2024 (Industry Track); arXiv June 27, 2024
- **arXiv**: https://arxiv.org/abs/2407.00121
- **First read**: <TODO>
- **Relevance to thesis**: **Methodological.** Useful when discussing what an on-device / edge-friendly tool-using LLM would need to do. The seven sub-tasks give a structured evaluation rubric for Caddie's MCP calls.

## TL;DR (my words, after reading)

Granite-20B-FunctionCalling is trained via multi-task learning on seven granular sub-tasks of function calling: Nested Calls, Function Chaining, Parallel Functions, Function Name Detection, Parameter-Value Pair Detection, Next-Best Function, and Response Generation. Top open-model on BFCL at release.

## Direct quotes

> "They must learn to identify, call, and interact with external tools and APIs to complete complex tasks." — Abstract

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Seven sub-skills of function calling** — Nested / Chain / Parallel / NameDetect / ParamDetect / NextBest / RespGen. (source: §3)
- **Open competitor to closed function-calling models** — permissive license. (source: §1)

## How I plan to use this in the thesis

- **Section**: Evaluation methodology.
- **Role**: **Methodological.**
- **Specific claims it supports**:
  - "Granite's seven sub-skill taxonomy is a useful rubric to evaluate Caddie's MCP-tool usage: which sub-skills does the agent demonstrate, and which fail (e.g. weak models tend to fail Parallel Functions and Next-Best Function)?"

# Reflexion: Language Agents with Verbal Reinforcement Learning

## Metadata

- **Authors**: Noah Shinn, Federico Cassano, Edward Berman, Ashwin Gopinath, Karthik Narasimhan, Shunyu Yao
- **Affiliations**: Northeastern University (Shinn, Cassano, Berman); MIT (Gopinath); Princeton University (Narasimhan, Yao)
- **Venue + year**: NeurIPS 2023
- **arXiv**: https://arxiv.org/abs/2303.11366
- **Project**: https://github.com/noahshinn/reflexion
- **First read**: <TODO>
- **Relevance to thesis**: **Technique + failure-recovery prior art.** Direct precedent for Caddie's correction step: agent writes a verbal self-critique after a failure, stores it in episodic memory, and conditions the next attempt on it.

## TL;DR (my words, after reading)

Instead of fine-tuning, the agent writes natural-language self-critiques after each failed trajectory and stores them in episodic memory; subsequent attempts are conditioned on this verbal feedback. Large gains on HumanEval, ALFWorld, HotpotQA. Reframes RL as "verbal reinforcement" — no gradient updates needed.

## Direct quotes

> "Reinforce language agents not by updating weights, but instead through linguistic feedback." — Abstract

> "Reflexion agents verbally reflect on task feedback signals, then maintain their own reflective text in an episodic memory buffer." — Abstract

## Paraphrases / my notes

- <TODO after full read>

## Key concepts / terms

- **Verbal reinforcement** — self-reflection in natural language as a substitute for gradient updates. (source: Abstract)
- **Episodic reflection memory** — store of past failures + critiques the agent consults on retry. (source: Abstract)

## How I plan to use this in the thesis

- **Section**: Related Work (failure recovery) + Discussion (positioning Caddie's correction step).
- **Role**: **Technique** — algorithmic prior art for Caddie's correction step (recover from failure via verbal reflection).
- **Specific claims it supports**:
  - "Reflexion-style self-critique is the closest existing technique to Caddie's correction step. The thesis contribution is to combine it with *human* visibility (the user sees the reflection) and *human* triggering (touch-to-pause can initiate the correction loop)."
  - "Reflexion stores reflections in agent-internal memory. Caddie's overlay externalises the same content to the user — the reflection becomes a UI primitive, not just a memory mechanism."

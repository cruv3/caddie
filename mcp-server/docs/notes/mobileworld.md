# MobileWorld: Benchmarking Autonomous Mobile Agents in Agent-User Interactive and MCP-Augmented Environments

## Metadata

- **Authors**: Tongyi-MAI team (Alibaba Tongyi Lab — Mobile Agent Initiative)
- **Affiliation**: Alibaba
- **Venue / year**: ACL 2026 (Annual Meeting of the Association for Computational Linguistics, accepted)
- **arXiv**: https://arxiv.org/abs/2512.19432
- **Project page**: https://tongyi-mai.github.io/MobileWorld/
- **GitHub**: https://github.com/Tongyi-MAI/MobileWorld
- **Local PDF**: <TODO download from arXiv>
- **First read**: 2026-06-01 (search-result summary only — full read TODO)
- **Relevance to thesis**: **Most important benchmark paper** for the thesis. First benchmark to elevate **agent-user interaction** to a measured dimension alongside GUI control and MCP tool use — exactly the two axes Caddie sits on.

## TL;DR (my words, after reading)

A successor benchmark to AndroidWorld, deliberately harder. 201 tasks across 20 apps, divided into three explicit categories: (1) **GUI-only** tasks (116) — the traditional Android-agent evaluation; (2) **agent-user interaction** tasks (45) — agent must proactively ask the simulated user for clarification when an instruction is ambiguous; (3) **MCP-augmented** tasks (40) — agent must use Model Context Protocol tools alongside GUI control. Includes long-horizon and cross-app tasks. Best agentic framework: 51.7% success; best end-to-end model: 20.9% — confirming current models struggle especially with the user-interaction and MCP axes.

## Direct quotes

> "MobileWorld, a substantially more challenging mobile-use benchmark designed to better reflect real-world mobile usage." — Abstract / project page

> "MobileWorld introduces novel task categories, including agent-user interaction and Model Context Protocol (MCP)-augmented tasks, for evaluating agents in user-aware, hybrid-tool scenarios." — Abstract / project page

> "The agent-user interaction tasks require agents to proactively engage with a simulated user to resolve ambiguous instructions. When faced with incomplete information, agents must recognize the ambiguity and formulate appropriate questions to gather missing details." — Project page

> "The best agentic framework and end-to-end model achieved 51.7% and 20.9% success rates, respectively. Current models struggle significantly with user interaction and MCP calls." — Project page

## Paraphrases / my notes

- Three task categories ≠ three benchmarks merged — they are evaluated together because real-world mobile usage requires all three. This framing validates Caddie's choice to integrate all three (overlay, voice correction, MCP tools). (source: Abstract)
- The 20.9% end-to-end vs. 51.7% agentic framework gap is striking — suggesting that scaffolding/orchestration matters as much as the underlying model. Caddie's design (agent loop + skill selection + intervention bus + overlay) is the agentic framework around a base LLM. (source: Abstract)
- Snapshot-based container environment + functional verifications (DB inspection, task callback APIs) means task success is verifiable, like AndroidWorld. Self-reported success counted as failure if state doesn't match. (source: Project page)
- Long-horizon + cross-app tasks are explicitly tested — Caddie should aspire to handle this class even if I don't run the full benchmark. (source: Abstract)

## Key concepts / terms

- **Agent-user interaction task category** — task where the agent must ask the user clarifying questions before completing. Caddie's voice-correction follow-up tasks are an in-the-wild example. (source: Abstract)
- **MCP-augmented task category** — agent must use MCP tools (not just GUI) to complete. Caddie's architecture is MCP-native. (source: Abstract)
- **Snapshot-based container environment** — reproducible mobile environment per task, with state checkpointing. (source: Project page)
- **Functional verification** — success determined by inspecting backend DB / task callback APIs, not by agent self-report. (source: Project page)

## How I plan to use this in the thesis

- **Section**: Related Work (benchmarks) + Discussion (positioning) + possibly Evaluation.
- **Role**: **Benchmark and conceptual axis.**
- **Specific claims it supports**:
  - "MobileWorld is the first benchmark to elevate agent-user interaction to a measured dimension — confirming that the field now recognises intelligibility-adjacent capabilities (ambiguity resolution, hybrid tool use) as research targets, not afterthoughts."
  - "Caddie's design choices (overlay, mid-run correction, voice follow-up tasks, MCP tools) directly target the two MobileWorld axes that current state-of-the-art models score worst on (user interaction and MCP calls)."
  - "The agentic framework vs. end-to-end gap on MobileWorld (51.7% vs. 20.9%) provides empirical justification for Caddie's heavy orchestration-around-a-small-LLM approach."

# Plan-Then-Execute: An Empirical Study of User Trust and Team Performance When Using LLM Agents As A Daily Assistant

## Metadata

- **Authors**: He, Gaole; Demartini, Gianluca; Gadiraju, Ujwal
- **Affiliation**: <TODO — Delft University of Technology / University of Queensland (Demartini)>
- **Venue / year**: arXiv 2025 (February)
- **arXiv**: https://arxiv.org/abs/2502.01390
- **DOI**: https://doi.org/10.48550/arXiv.2502.01390
- **Local PDF**: <TODO download>
- **First read**: 2026-06-01 (abstract only — full read TODO)
- **Relevance to thesis**: **Empirical HCI study**, not a technical paper. Provides the strongest existing empirical evidence that *making the agent's plan visible to the user* changes user trust and collaborative task outcomes. Directly cites the Bellotti & Edwards / Hardian-style framing in modern LLM-agent terms.

## TL;DR (my words, after reading)

A 248-participant user study (N=248, six daily-life tasks of varying risk including flight booking, credit card payments) testing how user involvement at each stage of an LLM agent's plan-then-execute workflow affects trust and team performance. Headline finding: LLM agents are a "double-edged sword" — they work well when both the plan is high-quality *and* the user is genuinely involved in execution, but **users readily trust plans that look plausible even when they're wrong** (false confidence transfer). The paper synthesises insights for trust calibration in daily-assistant LLM agents — exactly the design problem Caddie is trying to address.

## Direct quotes

> "Although LLM agents have shown a promising blueprint as daily assistants, there is a limited understanding of how they can provide daily assistance based on planning and sequential decision making capabilities." — Abstract

> "To ensure user agency and control over the LLM agent, we adopted LLM agents in a plan-then-execute manner, wherein the agents conducted step-wise planning and step-by-step execution in a simulation environment." — Abstract

> "Our findings demonstrate that LLM agents can be a double-edged sword — (1) they can work well when a high-quality plan and necessary user involvement in execution are available, and (2) users can easily mistrust the LLM agents with plans that seem plausible." — Abstract  
> *(Note: the abstract uses "mistrust" but the construction makes more sense as "trust"; verify in the full paper. The HCI literature on automation bias would predict over-trust on plausible-looking plans.)*

> "We synthesized key insights for using LLM agents as daily assistants to calibrate user trust and achieve better overall task outcomes." — Abstract

## Paraphrases / my notes

- N = 248 is a strong sample for an HCI experiment — gives statistical power to support concrete claims about trust effects. (source: Abstract)
- Six tasks of varying risk → the design lets the paper claim risk-dependent effects (relevant to Caddie's confirm-dialog gating on risky actions). (source: Abstract)
- Simulation environment (not real transactions) is appropriate for studying trust calibration; the paper explicitly draws on the "LLM-modulo" + human-in-the-loop framing from Kambhampati's work. (source: Abstract)
- The double-edged-sword finding is empirical evidence for the *intelligibility-without-validation* failure mode — users accept plausible plans uncritically. This justifies Caddie's *active* intervention mechanisms (must touch to pause, must swipe-to-confirm) over passive transparency. (source: Abstract)

## Key concepts / terms

- **Plan-then-execute** — agent splits its workflow into a separable planning phase (which the user can inspect) and a step-by-step execution phase (which the user can intervene in). (source: Abstract)
- **LLM-modulo setup** — Kambhampati's framing where an LLM proposes, a verifier or human checks. Plan-then-execute is one operationalisation. (source: Abstract, citing prior work)
- **Trust calibration** — the user's trust in the agent should match the agent's actual reliability. Mis-calibration in either direction is a problem (over-trust → uncaught errors; under-trust → unused capability). (source: standard HCI concept, used in Abstract)
- **Human-in-the-loop for planning** — opposed to human-only-after-failure designs. (source: Abstract)

## How I plan to use this in the thesis

- **Section**: Motivation / Related Work (HCI evidence that intelligibility matters empirically).
- **Role**: **Empirical evidence** that exposing the agent's plan changes user behaviour. Not just a design preference — a measured effect.
- **Specific claims it supports**:
  - "Empirical HCI work shows that exposing the agent's plan before execution changes user trust calibration — He, Demartini, Gadiraju (2025) is the most direct evidence with N=248."
  - "Plan-then-execute is *not* the same as run-time transparency — the user sees the plan once, then the agent executes. Caddie extends this by showing every individual action as it executes, addressing the 'plausible plan, wrong execution' failure mode the paper documents."
  - "Risk-varied task selection (flight booking, credit card payments) supports the design choice of differentiated intervention modes — light corrections vs. confirm dialogs for high-risk actions."

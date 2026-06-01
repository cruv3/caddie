# Autonomy Reshapes How Personalization Affects Privacy Concerns and Trust in LLM Agents

## Metadata

- **Authors**: Zhang, Zhiping; Zhang, Yi Evie; Shi, Freda; Li, Tianshi
- **Affiliation**: <TODO — Tianshi Li is at Northeastern University; others TODO>
- **Venue / year**: arXiv 2025 (October)
- **arXiv**: https://arxiv.org/abs/2510.04465
- **DOI**: https://doi.org/10.48550/arXiv.2510.04465
- **Local PDF**: <TODO download>
- **First read**: 2026-06-01 (abstract only)
- **Relevance to thesis**: **Macro-level HCI motivation.** Argues that increasing agent autonomy creates new HCI problems — specifically privacy concerns, trust erosion, and personalization trade-offs — and proposes "risk-contingent autonomy" as a partial solution. Directly aligned with Caddie's design philosophy.

## TL;DR (my words, after reading)

A 3×3 between-subjects experiment (N=450) studying how *agent autonomy level* interacts with *personalization conditions* to shape user privacy concerns, trust, and willingness to use LLM agents. Headline finding: **risk-contingent autonomy** — where the agent transfers control back to the user when it detects potential privacy violations — meaningfully reduces the privacy/trust costs of personalization. This is the empirical paper that justifies the entire design space Caddie operates in: more visibility + situational control hand-off attenuates the harms of autonomy.

## Direct quotes

> "LLM agents require personal information for personalization in order to effectively act on users' behalf, but this raises privacy concerns that can discourage data sharing, limiting both the autonomy levels at which agents can operate and the effectiveness of personalization." — Abstract

> "We conducted a 3×3 between-subjects experiment (N=450) to study how agent autonomy level influences personalization's effects on users' privacy concerns, trust, and willingness to use, as well as the underlying psychological processes." — Abstract

> "Risk-contingent autonomy, where agents transfer control to users upon detecting potential privacy violations, meaningfully reduces personalization's negative consequences. This autonomy design supports human agency through enhanced perceived control and effective oversight, enabling users to embrace personalization benefits without heightened privacy apprehension." — Abstract

> "Agent's autonomy that supports human autonomy helps users benefit from personalization without being deterred by privacy concerns." — Abstract

> "Improving users' perceived control, attenuates personalization's adverse effects." — Abstract

## Paraphrases / my notes

- The 3×3 design lets the paper isolate the *interaction effect* between autonomy and personalization — not just main effects. Strong methodological choice. (source: Abstract)
- N=450 is large for a between-subjects design — gives statistical power for the 9 conditions. (source: Abstract)
- The "risk-contingent autonomy" concept is essentially Bellotti & Edwards's *confirm* principle generalised to a privacy axis: when the system is about to do something with privacy implications, it asks first. Caddie's swipe-to-confirm dialog for risky actions is a UI-layer instance of this. (source: Abstract)
- "Perceived control" is identified as the mechanism — not actual control levels but the user's *sense* that they could intervene. This is exactly what Caddie's overlay provides even when the user isn't intervening. (source: Abstract)

## Key concepts / terms

- **Risk-contingent autonomy** — autonomy that automatically hands control back to the user when potential privacy/risk violations are detected. (source: Abstract)
- **Perceived control** — the user's *sense* of being able to intervene, which (per the paper) is the mediating variable between autonomy design and trust. (source: Abstract)
- **Personalization vs. privacy trade-off** — more personalization requires more personal data, which raises privacy concerns, which discourages data sharing, which limits personalization effectiveness. A negative feedback loop. (source: Abstract)
- **3×3 between-subjects design** — 9 distinct experimental conditions, each user in only one. (source: Abstract)

## How I plan to use this in the thesis

- **Section**: Introduction / Motivation + Related Work (HCI evidence base).
- **Role**: **Macro-level motivation**. Establishes that the problem space Caddie addresses is recognised by the broader HCI research community.
- **Specific claims it supports**:
  - "Recent HCI work (Zhang et al. 2025) demonstrates that agent autonomy design materially affects user trust, privacy concerns, and willingness to use — autonomy is not just a system property, it is an HCI design variable."
  - "Risk-contingent autonomy in Zhang et al. corresponds operationally to the Bellotti & Edwards *confirm* principle (Hardian 2006 reproduction); Caddie's swipe-to-confirm-on-risky-action dialog is a concrete UI implementation."
  - "The mediating role of *perceived control* (not just actual control) supports the design choice to keep the overlay always visible during a run — even when the user does not intervene, the persistent visibility maintains perceived control."

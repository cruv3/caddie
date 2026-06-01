# Caddie — Improvements derived from the literature

This file is a working document, NOT a finished thesis chapter. It collects every concrete improvement to Caddie that came out of the 84-paper literature survey, organised by ROI and ready to drop into the thesis's **Discussion / Future Work** chapters.

Date: 2026-06-01.

---

## 0. Where Caddie sits in the field — the positioning argument

The 73 papers cluster into four design philosophies. Caddie occupies a small but distinct quadrant.

```
                              autonomy ↑
                                  │
    AppAgent, Mobile-Agent v3.5 ──┼── Mobile-Agent v2 (multi-agent scaffolding)
    OS-Atlas, AutoDroid           │   ReWOO, MetaGPT
    AutoGPT (counter-example)     │
                                  │   VLAA-GUI ← internal Completeness Verifier
   ───────────────────────────────┼─────────────────────────────────────
                                  │
    Anthropic Computer Use        │   *** CADDIE ***
    (frontier capability,         │   per-action user visibility,
    no UX layer)                  │   user as the verifier,
                                  │   intervention as primitive
                                  │
                              control ↑
                              by user
```

The thesis's novel claim: **Caddie operationalises Bellotti & Edwards (2001) for LLM smartphone agents at per-action granularity** — a corner no other paper occupies.

Closest siblings to acknowledge in the defence:

- **VLAA-GUI** — same problem (catch agent failures), different solution (internal verifier vs. user verifier).
- **He et al. 2025 Plan-Then-Execute** — confirms empirically (N=248) that visibility changes trust, but their visibility is one-shot.
- **Zhang et al. 2025 Autonomy/Privacy/Trust** — risk-contingent autonomy = automated "confirm"; Caddie generalises to correct + confirm + choose.

---

## 1. Tier 1 — Fixes for failures already in our session log

These map 1:1 to failures documented in `docs/failure-mode-log.md` and `docs/correction-test-log.md`.

### 1.1 Completeness Verifier — server-side
- **Failure addressed**: gemma-4-e4b called `smartphone_done(message="Die YouTube App wurde erfolgreich gelöscht")` while the app remained installed (session memory, 2026-06-01 07:00 GMT+2).
- **Source**: VLAA-GUI ([notes](vlaa-gui.md)). Their *Completeness Verifier* module rejects "done" claims without UI-observable evidence.
- **Proposed implementation**: Wrap `smartphone_done` on the agent-loop side. Before accepting, run a verification check: re-call `smartphone_list_apps` (or whichever observation is relevant), match against the task's expected end-state. If mismatch, convert to `smartphone_failed` with a diagnostic message instead.
- **Effort**: ~6 hours. Touches `caddie/agent/agent_loop.py` and possibly `caddie/agent/lifecycle.py`.
- **Thesis value**: This is the architectural answer to the most prominent failure mode in Caddie's session memory. Worth a full subsection in the Architecture or Discussion chapter.

### 1.2 "Choose" intervention mode (the missing third Bellotti & Edwards principle)
- **Failure addressed**: Caddie has *correct* (voice mid-run) and *confirm* (swipe dialog) interventions, but no *choose*. When the agent is uncertain among 2–3 candidate actions, it currently guesses.
- **Source**: Bellotti & Edwards 2001 ([notes](bellotti-edwards-2001.md)), reproduced by Hardian 2006.
- **Proposed implementation**: New overlay state `RunState.Choosing` displays 2–3 candidate actions (e.g. "Tap 'Erlauben' / Tap 'Ablehnen' / Take screenshot"). Touch one to proceed. Timeout → agent picks default and continues.
- **Effort**: ~4 hours, mostly overlay UI.
- **Thesis value**: Closes the Bellotti & Edwards triple, which is the thesis's theoretical anchor.

### 1.3 Reflexion-style lesson memory
- **Failure addressed**: When anti-loop fires, the agent has no record of what didn't work for the next attempt (within session and across sessions).
- **Source**: Reflexion ([notes](reflexion.md)).
- **Proposed implementation**: When anti-loop triggers, write a one-line lesson learned to session state ("Tapping 'Allow' at (X,Y) failed twice — label was 'Erlauben'"). Inject lessons into the next Thought's context. Optionally persist across sessions in `docs/failure-mode-log.md`.
- **Effort**: ~4 hours.
- **Thesis value**: Pairs naturally with the existing failure-mode log, which is *already* a Reflexion-style memory waiting for runtime injection.

### 1.4 Untrusted-input delimiter (SECURITY)
- **Failure addressed**: An Android agent reads accessibility text from arbitrary apps and web pages. That text is attacker-controllable. No current guard.
- **Source**: Greshake et al. AISec 2023 ([notes](indirect-prompt-injection.md)).
- **Proposed implementation**: ✅ **DONE as of 2026-06-01.** Added UNTRUSTED INPUT rule to `BASE_SYSTEM_PROMPT`. Future hardening: wrap every observation tool result in explicit `<UNTRUSTED_SCREEN>...</UNTRUSTED_SCREEN>` delimiters at the agent-loop level.
- **Effort remaining**: ~1 hour for the delimiter wrapping.
- **Thesis value**: Critical for the security/threat-model chapter. Any thesis on smartphone agents that does not address this is incomplete.

---

## 2. Tier 2 — Capability improvements

### 2.1 Plan-then-execute with visible plan
- **Status**: ✅ Prompt-side **DONE** as of 2026-06-01. Constitution + Plan: block now in `BASE_SYSTEM_PROMPT`.
- **Source**: Plan-and-Solve ([notes](plan-and-solve.md)), He et al. 2025 ([notes](plan-then-execute.md)), SeeAct ([notes](seeact.md)).
- **Next step**: Render the Plan: block on the overlay so the user sees the plan *before* any action. Adds a "veto plan" intervention.
- **Effort**: ~3 hours overlay-side.
- **Thesis value**: He et al. 2025's N=248 study is direct empirical support that this changes user trust calibration.

### 2.2 Set-of-Mark numbered overlay for grounding
- **Source**: Yang et al. 2023 ([notes](set-of-mark.md)), MM-Navigator ([notes](mm-navigator.md)), SeeAct ([notes](seeact.md)).
- **Proposed implementation**: When the agent is about to tap a UI element, render numbered marks on screen via the overlay. Agent picks by ID. *Same numbers visible to user* → user can override by saying "no, 5" via voice channel. Direct integration of SoM into Caddie's intervention model.
- **Effort**: ~8 hours (overlay-side rendering + agent-loop-side mark assignment).
- **Thesis value**: Unifies the visual-grounding-improvement literature with the user-intervention literature. Strong contribution claim.

### 2.3 Screen-conditioned skill catalog
- **Source**: AppAgent ([notes](appagent.md)).
- **Observation**: Caddie's `AgentActivityTracker` already knows the current package. The skill catalog could be filtered to only skills relevant to the active app.
- **Proposed implementation**: When constructing the MCP tool list shown to the LLM, filter `smartphone_get_skill_*` tools by current package. Falls back to full catalog if no package match.
- **Effort**: ~2 hours.
- **Thesis value**: Frees context tokens for stronger reasoning rules. Small change, measurable improvement on weak models.

### 2.4 Exception-handling clause in ReAct
- **Status**: ✅ **DONE as of 2026-06-01.** Added "Exception handling" rule (step 5) to workflow in `BASE_SYSTEM_PROMPT`.
- **Source**: Yao et al. 2023 §4 (ReAct paper itself, [notes](react.md)).
- **Thesis value**: Demonstrates careful reading of the foundational paper — most ReAct reimplementations skip the exception-handling clause.

### 2.5 State-based vs trajectory-based scoring
- **Source**: AndroidWorld ([notes](androidworld.md)), MobileAgentBench ([notes](mobileagentbench.md)), WebArena ([notes](webarena.md)).
- **Proposed implementation**: For evaluation purposes, replace `smartphone_done` self-report with an external state-check (re-read accessibility tree against expected end-state).
- **Effort**: ~6 hours per task category to write the verification scripts.
- **Thesis value**: Aligns with the field's standard. Makes any quantitative claim in the evaluation chapter publishable.

---

## 3. Tier 3 — Prompt engineering applied to BASE_SYSTEM_PROMPT

All applied 2026-06-01 unless noted.

| # | Technique | Source | Status |
|---|---|---|---|
| 1 | Lost-in-the-middle restructuring (critical rules top + bottom) | Liu et al. TACL 2024 ([notes](lost-in-the-middle.md)) | ✅ Done |
| 2 | Constitution block (6 lines) | Bai et al. 2022 ([notes](constitutional-ai.md)) | ✅ Done |
| 3 | Untrusted-input rule | Greshake et al. 2023 ([notes](indirect-prompt-injection.md)) | ✅ Done |
| 4 | Plan: block at session start | Wang et al. ACL 2023 ([notes](plan-and-solve.md)) | ✅ Done |
| 5 | "Let's think step by step" trigger in Thought | Kojima et al. NeurIPS 2022 ([notes](zero-shot-cot.md)) | ✅ Done |
| 6 | Exception-handling clause in workflow | Yao et al. 2023 ([notes](react.md)) | ✅ Done |
| 7 | Pre-termination checklist (restated rules at bottom) | Liu et al. TACL 2024 | ✅ Done |
| 8 | Self-Refine pre-action critique for irreversible actions | Madaan et al. 2023 ([notes](self-refine.md)) | TODO |
| 9 | Self-Discover JSON working-memory template | Zhou et al. 2024 ([notes](self-discover.md)) | TODO (future work) |
| 10 | Self-Consistency for high-stakes single-shot decisions | Wang et al. 2022 ([notes](self-consistency.md)) | TODO (future work) |
| 11 | Active Prompting — inject failure-mode-log entries as exemplars | Diao et al. 2024 ([notes](active-prompting.md)) | TODO (needs failure-mode-log triage) |

---

## 4. Tier 4 — Evaluation & methodology

### 4.1 MAST taxonomy mapping
- **Source**: Cemri et al. 2025 ([notes](mast-multi-agent-failures.md)).
- **Use**: Classify Caddie's observed failure modes (from `docs/failure-mode-log.md`) against the 14-mode MAST taxonomy. Makes the empirical chapter rigorous.
- **Effort**: ~4 hours of classification work once the failure-mode log is complete.

### 4.2 Granite 7-subskill rubric for MCP tool use
- **Source**: IBM 2024 ([notes](granite-function-calling.md)).
- **Use**: Evaluate Caddie's MCP tool calls by: Nested / Chain / Parallel / NameDetect / ParamDetect / NextBest / RespGen. Structured eval, not just success rate.

### 4.3 B-MoCA configuration randomisation
- **Source**: KAIST 2024 ([notes](b-moca.md)).
- **Use**: Test robustness under UI variation (language, layout, theme). The regime where Caddie's user-intervention model should outperform opaque end-to-end agents.

### 4.4 Speech-derived UX measures
- **Source**: Beyond Words CHI 2026 ([notes](beyond-words-2026.md)).
- **Use**: If a user study is run, complement task-completion metrics with prosody / pause / disfluency analysis of user voice during Caddie sessions. Empirical signal of trust independent of self-report.

---

## 5. Tier 5 — Open future-work directions

These are too large for the master's thesis but worth flagging as Future Work.

- **Symbolic verifier alongside human verifier** (Kambhampati LLM-Modulo, [notes](kambhampati-2024.md)): combine the user-as-verifier with an automated state-checker (Caddie's accessibility tree gives the raw material).
- **Tree-of-Thoughts user-pickable branches** ([notes](tree-of-thoughts.md)): generate multiple candidate plans, render on overlay, user picks. Extends transparency from single-path to multi-path.
- **Cross-session reflection persistence** ([notes](reflexion.md)): the failure-mode log already exists; injecting it at session start is one engineering sprint away.
- **End-to-end voice LLM as alternative architecture** ([notes](ultravox.md)): current Whisper-cascade is deliberate; Ultravox-style end-to-end would reduce latency at the cost of debuggability. Worth a comparative study.
- **Specialised mobile-UI VLM** ([notes](ferret-ui.md), [notes](screenai.md)): Caddie's perception currently uses general Qwen-VL. A UI-specialised open-weight model would slot in cleanly without changing the overlay/intervention layer.

---

## 6. What the thesis can claim now, evidence by evidence

| Claim | Supporting paper(s) |
|---|---|
| LLM agents on smartphones currently lack runtime intelligibility. | AppAgent, Mobile-Agent series, OS-Atlas — none expose per-action user-visible state. |
| The need for runtime intelligibility is theoretically established. | Bellotti & Edwards 2001; Hardian 2006; Lee & See 2004; Shneiderman 2020. |
| The need is empirically validated. | He et al. 2025 (N=248); Zhang et al. 2025 (N=450); Baughan et al. CHI 2023; Dietvorst 2015. |
| The need is technical, not only ethical. | Kambhampati 2024 (LLMs cannot self-verify); Mahowald 2024 (formal ≠ functional competence); Bender et al. 2021. |
| The failures intelligibility addresses are real and named. | MAST taxonomy (Cemri 2025); Hallucination survey (Huang 2023); Caddie's own gemma-4-e4b YouTube failure (06:55 GMT+2, 2026-06-01). |
| The intervention principles to apply already exist (correct / confirm / choose). | Bellotti & Edwards 2001 (original); Hardian 2006 (reproduction); Caddie now implements 2/3. |
| Caddie's design is implementable on a local LLM stack. | AutoDroid (on-device Vicuna proof); OS-Atlas (open-weight grounding); Qwen-VL series (deployable VLM family). |

---

## Author note

This document is the **literature-to-Caddie bridge**. When writing the thesis's Discussion or Future Work chapters, this is the source of structured arguments. Every claim points at a specific notes file, which in turn points at the original paper.

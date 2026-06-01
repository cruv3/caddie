# Caddie Thesis — Bibliography Index

**Total papers: 73** (target was ~60: average CS/HCI master thesis cites ~50, +25% buffer = 62; this corpus has comfortable headroom for pruning during writing).

This index groups every paper in `notes/` by thesis role. When writing a chapter, start here, jump to the per-paper notes, and quote only from there (workflow rules: see `_README.md`).

---

## A. Theoretical anchors (HCI foundations of transparency + control)

These are the philosophical backbone. They establish the *why* of Caddie's design before any LLM-specific argument.

- [bellotti-edwards-2001](bellotti-edwards-2001.md) — **Intelligibility & Accountability** (original source). The correct/confirm/choose triple.
- [hardian2006](hardian2006.md) — **Survey reproducing Bellotti & Edwards**, formalising the control-autonomy continuum (positions A/B/C).
- [lee-see-2004](lee-see-2004.md) — **Trust calibration** in automation; misuse vs. disuse.
- [norman-1990](norman-1990.md) — **Inappropriate feedback**, not over-automation, is the danger.
- [shneiderman-2020](shneiderman-2020.md) — **HCAI 2-D framework**: high control × high automation, not control vs. automation.
- [liao-vaughan-2024](liao-vaughan-2024.md) — **AI transparency in the LLM era**; the research roadmap Caddie instantiates.

## B. Motivation (why LLM-specific transparency matters)

Empirical + technical arguments for the thesis's core claim.

- [kambhampati-2024](kambhampati-2024.md) — **LLMs Can't Plan, LLM-Modulo**; technical (not just ethical) need for human-in-the-loop.
- [stochastic-parrots](stochastic-parrots.md) — Fluency ≠ understanding; users over-attribute intent.
- [mahowald-2024](mahowald-2024.md) — **Formal vs functional competence**; cognitive-science basis.
- [dietvorst-2015](dietvorst-2015.md) — **Algorithm aversion**; single visible failure breaks adoption unless repair exists.
- [bansal-2019](bansal-2019.md) — Performance/compatibility trade-off; model updates can hurt teams.
- [hallucination-survey](hallucination-survey.md) — Taxonomy: factuality vs faithfulness.
- [truthfulqa](truthfulqa.md) — Inverse scaling for truthfulness.
- [dziri-2022](dziri-2022.md) — Hallucination is *inherited and amplified*, not curable at model layer.
- [anthropic-computer-use](anthropic-computer-use.md) — Industry signal: pixel-level GUI control is now mainstream.

## C. Design principles (how to design for transparency)

Practical heuristics + the empirical user studies behind them.

- [amershi-2019](amershi-2019.md) — **18 guidelines for human-AI interaction** (capability disclosure, efficient correction).
- [plan-then-execute](plan-then-execute.md) — N=248 study: plan visibility changes user trust calibration.
- [autonomy-privacy-trust](autonomy-privacy-trust.md) — N=450 study: risk-contingent autonomy attenuates privacy concerns.
- [baughan-2023](baughan-2023.md) — Voice-assistant failure types are *asymmetric*: overcapture uniquely corrosive.

## D. LLM agent foundations (the architectural lineage)

Technical ancestry of Caddie's agent loop. ReAct is the loop; everything else is variations.

- [react](react.md) — **The Thought/Action/Observation loop Caddie executes.**
- [chain-of-thought](chain-of-thought.md) — Reasoning out loud, the prerequisite of ReAct.
- [tree-of-thoughts](tree-of-thoughts.md) — Search over reasoning; alternative to linear ReAct.
- [reflexion](reflexion.md) — Verbal self-critique; algorithmic prior art for Caddie's correction step.
- [voyager](voyager.md) — **Skill library** (Voyager auto-grown; Caddie human-curated).
- [autogpt](autogpt.md) — Counter-example: opaque, unattended autonomy.
- [metagpt](metagpt.md) — Multi-agent SOPs; alternative organisation.
- [hugginggpt](hugginggpt.md) — **LLM-as-controller** for many tools; precedent for MCP layer.

## E. Tool use, function calling, MCP (the integration layer)

How LLMs invoke tools. Caddie's `smartphone_*` tools live here.

- [mcp-spec](mcp-spec.md) — **The protocol Caddie speaks.**
- [openai-function-calling](openai-function-calling.md) — The JSON-Schema function-call shape.
- [toolformer](toolformer.md) — Self-supervised tool use.
- [toolllm](toolllm.md) — Large-scale tool benchmarks (16k+ APIs).
- [gorilla](gorilla.md) — Documentation-grounded tool selection.
- [rewoo](rewoo.md) — Plan-then-execute (token-efficient alternative to ReAct).
- [granite-function-calling](granite-function-calling.md) — Seven sub-skills of function calling (evaluation rubric).

## F. Vision / multimodal models (perception layer)

The VLM backbone Caddie's screenshot-reading capability inherits.

- [llava](llava.md) — The open VLM recipe (CLIP + projector + LLM).
- [qwen-vl](qwen-vl.md) — **Caddie's actual model basis** (Qwen-VL series).
- [gpt-4v](gpt-4v.md) — The closed-frontier baseline.
- [cogagent](cogagent.md) — GUI-specialised VLM, pixel-only.
- [ferret-ui](ferret-ui.md) — Apple's mobile-UI VLM (closed).
- [screenai](screenai.md) — Google's UI-pretrained VLM.
- [pix2struct](pix2struct.md) — Screenshot-as-input pretraining (conceptual ancestor).
- [set-of-mark](set-of-mark.md) — **Numbered overlays** for visual grounding; directly applicable to Caddie's confirm-dialog.

## G. GUI agents — mobile (direct competitors)

The agents Caddie shares the most architecture with.

- [appagent](appagent.md) — **Direct comparator** (CHI '25, same operating mode).
- [mobile-agent](mobile-agent.md) — Original vision-first mobile agent.
- [mobile-agent-v2](mobile-agent-v2.md) — Multi-agent decomposition.
- [mobile-agent-v3](mobile-agent-v3.md) — Foundation-model mobile agent (Aug 2025).
- [mobile-agent-v3-5](mobile-agent-v3-5.md) — Multi-platform GUI-Owl (Feb 2026; current SOTA).
- [autodroid](autodroid.md) — UI-graph + memory injection.
- [droidbot-gpt](droidbot-gpt.md) — Historical first LLM+Android agent.
- [mm-navigator](mm-navigator.md) — GPT-4V on phones, zero-shot.
- [os-atlas](os-atlas.md) — Open-source GUI grounding foundation model.

## H. GUI agents — web/desktop (cross-platform reference)

Cross-platform comparisons to motivate mobile-specific design.

- [webgpt](webgpt.md) — Earliest browser-using LLM.
- [mind2web](mind2web.md) — Real-website task dataset.
- [seeact](seeact.md) — **Plan-then-ground** decomposition; informs Caddie's confirm-dialog.
- [agentbench](agentbench.md) — Multi-environment LLM-as-agent evaluation.

## I. Benchmarks (evaluation methodology)

The yardsticks for what counts as task success.

- [androidworld](androidworld.md) — **Primary Android benchmark** (Google, state-based scoring).
- [mobileworld](mobileworld.md) — **Most relevant benchmark for Caddie's thesis** (agent-user interaction + MCP axes).
- [vlaa-gui](vlaa-gui.md) — Stop/Recover/Search modular framework + OSWorld results.
- [mobileagentbench](mobileagentbench.md) — State-based mobile benchmark.
- [b-moca](b-moca.md) — Configuration-randomised generalisation testing.
- [androidlab](androidlab.md) — Dual-modality (XML + screenshot) Android benchmark.
- [webarena](webarena.md) — Outcome-based web benchmark.
- [visualwebarena](visualwebarena.md) — Visual-grounding web benchmark (huge agent-vs-human gap).
- [workarena](workarena.md) — Enterprise-SaaS benchmark.
- [osworld](osworld.md) — Cross-OS desktop benchmark.
- [windows-agent-arena](windows-agent-arena.md) — Windows-OS, containerised, parallelisable.

## J. Voice / speech (Caddie's voice channel)

ASR, wake-word, voice-UX literature.

- [whisper](whisper.md) — **ASR primitive Caddie uses.**
- [openwakeword](openwakeword.md) — **Wake-word detector Caddie uses.**
- [voicebench](voicebench.md) — LLM-voice-assistant evaluation benchmark.
- [ultravox](ultravox.md) — End-to-end audio LLM (counter-architecture).
- [beyond-words-2026](beyond-words-2026.md) — Speech-derived UX measures.

## K. Failure-mode analysis (what goes wrong)

Empirical taxonomies of agentic failure.

- [mast-multi-agent-failures](mast-multi-agent-failures.md) — **MAST taxonomy** (14 modes; task-verification cluster matches Caddie's gemma-4-e4b hallucination).
- [training-verifiers](training-verifiers.md) — Generator/verifier asymmetry (Caddie's user is the verifier).

---

## Cross-reference map (where each thesis chapter draws from)

| Chapter / Section | Primary papers (sections above) |
|---|---|
| **1. Introduction / Motivation** | A, B, C — establish the gap |
| **2. Theoretical Framing** | A — Bellotti, Hardian, Lee & See, Shneiderman, Liao & Vaughan |
| **3. Related Work — Mobile Agents** | G, parts of H |
| **3. Related Work — Transparency/Trust HCI** | A + C |
| **3. Related Work — Failure Modes** | B (hallucination cluster), K |
| **4. Architecture (Caddie itself)** | D (ReAct), E (MCP), F (Qwen-VL, Set-of-Mark) |
| **5. Voice channel design** | J |
| **6. Evaluation methodology** | I — choose 1–2 benchmarks |
| **7. Discussion / Positioning** | G + A (continuum position B/A) |
| **8. Future Work** | E (sub-skill rubric), I (broader benchmarks), K (formal verifiers) |

---

## Reading order (suggested)

If you're starting from zero on the thesis topic, read in this order to build the argument:

1. **Norman 1990** → diagnoses what goes wrong with semi-automation.
2. **Bellotti & Edwards 2001** → prescribes intelligibility + accountability.
3. **Hardian 2006** → modern survey of the design space.
4. **Kambhampati 2024** → technical (not just ethical) need for human-in-the-loop.
5. **AppAgent (CHI '25)** → state-of-the-art mobile agent without runtime transparency.
6. **MobileWorld** → the benchmark that names the gap Caddie fills.
7. **Plan-Then-Execute (He et al. 2025)** → empirical evidence that plan visibility changes user behaviour.
8. **VLAA-GUI** → architectural sibling for failure-mode handling.

That's 8 papers to land the thesis's argument. Everything else in the bibliography supports specific paragraphs.

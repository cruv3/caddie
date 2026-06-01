# AndroidWorld: A Dynamic Benchmarking Environment for Autonomous Agents

## Metadata

- **Authors**: Christopher Rawles, Sarah Clinckemaillie, Yifan Chang, Jonathan Waltz, Gabrielle Lau, Marybeth Fair, Alice Li, William Bishop, Wei Li, Folawiyo Campbell-Ajala, Daniel Toyama, Robert Berry, Divya Tyamagundlu, Timothy Lillicrap, Oriana Riva
- **Affiliation**: Google DeepMind
- **Venue / year**: arXiv preprint, 2024 (May)
- **arXiv**: https://arxiv.org/abs/2405.14573
- **DOI**: https://doi.org/10.48550/arXiv.2405.14573
- **Local PDF**: <TODO download from arXiv>
- **First read**: 2026-06-01 (abstract + Google DeepMind page only — full paper TODO)
- **Relevance to thesis**: **Benchmark and methodological reference.** The most-cited Android-agent evaluation suite. Defines what "task success" means in the field and supplies the comparator for any new agent's success rate.

## TL;DR (my words, after reading)

A 116-task / 20-app benchmark for Android agents from Google DeepMind, distinct from prior static suites in that it *dynamically* parameterises tasks (same goal expressed in many natural-language phrasings). Each task ships with explicit initialization, success-checking, and tear-down logic that inspects the device's system state — making task success a *programmatically verifiable* signal rather than a self-report. Best baseline agent at publication: **30.6%** completion. Cross-platform analysis shows desktop web agents adapted to mobile perform worse, and task-phrasing variations significantly affect agent performance — both observations argue that mobile is its own problem space.

## Direct quotes

> "AndroidWorld, a fully functional Android environment that provides reward signals for 116 programmatic tasks across 20 real-world Android apps." — Abstract

> "Unlike existing interactive environments, which provide a static test set, AndroidWorld dynamically constructs tasks that are parameterized and expressed in natural language in unlimited ways, thus enabling testing on a much larger and more realistic suite of tasks." — Abstract

> "Each task includes dedicated initialization, success-checking, and tear-down logic, which modifies and inspects the device's system state." — Abstract

> "Our best agent can complete 30.6% of AndroidWorld's tasks, leaving ample room for future work." — Abstract

> "We adapt a popular desktop web agent to work on Android, which we find to be less effective on mobile, suggesting future research is needed to achieve universal, cross-platform agents." — Abstract

> "Task variations can significantly affect agent performance, demonstrating that without such testing, agent performance metrics may not fully reflect practical challenges." — Abstract

## Paraphrases / my notes

- The reward function is **state-based**, not self-reported. The benchmark inspects the device after the agent's run and compares against the expected end-state. This is methodologically stronger than `smartphone_done()` self-reports (which Caddie's failure-mode log documents as unreliable for weak models). (source: Abstract)
- 30.6% baseline completion at publication establishes that even with strong LLMs, the field was far from solving mobile agency. Subsequent papers (MobileWorld, AppAgent, etc.) cite this as motivation. (source: Abstract)
- Cross-platform finding (desktop-web → mobile drops performance) is important context: it justifies dedicated mobile research rather than treating "agent" as platform-agnostic. (source: Abstract)
- Robustness to task-phrasing variation is highlighted — a single phrasing of a task may overstate or understate agent capability. Has implications for any user study using fixed task phrasings. (source: Abstract)

## Key concepts / terms

- **Programmatic task suite** — tasks defined as code (init / check / teardown) rather than prose. Enables reproducibility. (source: Abstract)
- **Dynamic task parameterisation** — same task template generates many natural-language phrasings → tests robustness, not memorisation. (source: Abstract)
- **State-based reward** — success determined by inspecting end-state of the device, independent of agent claims. Methodological gold standard. (source: Abstract)
- **Cross-platform transfer gap** — desktop web agents underperform on mobile, even adapted. Mobile is *not* a thin client over web. (source: Abstract)

## How I plan to use this in the thesis

- **Section**: Related Work (benchmarks) + Evaluation (methodology comparison).
- **Role**: **Benchmark / methodological reference.**
- **Specific claims it supports**:
  - "Current Android-agent benchmarks (AndroidWorld) measure end-state task completion via system-state inspection, not self-reported completion — methodologically a higher bar than weak models can easily fake."
  - "The 30.6% baseline at publication establishes that mobile agency is unsolved; my work contributes a complementary dimension (intelligibility) rather than competing on raw completion."
  - "Robustness to task-phrasing variation (AndroidWorld §) cautions against using a single phrasing in user studies — relevant to my own evaluation design."
- **For Caddie evaluation**: even if I don't run the full AndroidWorld suite, I should adopt its **state-based verification** pattern for any quantitative claims about Caddie's task-completion rate. Self-reported `smartphone_done()` is unreliable (per session memory: weak models hallucinate success).

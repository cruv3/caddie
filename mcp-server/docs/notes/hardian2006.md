# Balancing Autonomy and User Control in Context-Aware Systems — A Survey

## Metadata

- **Authors**: Hardian, Bob; Indulska, Jadwiga; Henricksen, Karen
- **Affiliation**: School of Information Technology and Electrical Engineering, The University of Queensland, Australia
- **Venue / year**: Fourth Annual IEEE International Conference on Pervasive Computing and Communications Workshops (PerComW '06), 2006
- **DOI / link**: IEEE Xplore — Proceedings of PERCOMW'06 (1-7695-2520-2/06)
- **Local PDF**: `C:\Users\Andreas\Nextcloud\Master\Masterarbeit\IEEE Xplore Full-Text PDF.pdf`
- **First read**: 2026-06-01
- **Relevance to thesis**: **Theoretical anchor.** Provides the control-autonomy continuum and the Bellotti & Edwards intervention principles that frame Caddie's design philosophy. Pre-LLM but the principles transfer cleanly.

## TL;DR (my words, after reading)

A 2006 survey arguing that context-aware applications must offer mechanisms to balance user control against software autonomy along a continuum (A = full user control, B = intermediate, C = mostly autonomous). The paper's central claim is that **no position on this continuum is universally optimal** — the right balance depends on user needs, situation, and expertise. Reproduces Bellotti & Edwards's three principles for runtime user intervention (correct / confirm / choose) and surveys context modelling languages, end-user programming, and preference-based decision support as enablers of the balance. Concludes that the field has emphasised modelling context but underdeveloped the user-control mechanisms needed to make autonomous systems usable.

## Direct quotes

> "Application autonomy can reduce interactions with users, ease the use of the system, and decrease user distraction. On the other hand, users may feel loss of control over their applications. A further problem is that autonomous applications may not always behave in the way desired by the user." — Abstract, p.1

> "To mitigate these problems, autonomous context-aware systems must provide mechanisms to strike a suitable balance between user control and software autonomy. This involves providing mechanisms to make users aware of reasons for application adaptations by selectively revealing aspects of the application state, such as context information, user preference information and adaptation logic used in decision making processes." — Abstract, p.1

> "If there is only slight doubt about what the desired outcome might be, the user must be offered an effective means to *correct the system action*; If there is significant doubt about the desired outcome, the user must be able to *confirm the action* the system intends to take; and If there is no real basis for inferring the desired outcome, the user must be offered available *choices for system action*." — §3 Studies on user control, p.2 (reproduced from Bellotti & Edwards [5])

> "As discussed by Bellotti and Edwards, accountability of a context-aware system can be achieved by informing the user of the system's capabilities and its understanding of the current context, disclosing actions taken by the system, providing feedback to the user, and providing mechanisms for user control." — §3, p.2

> "However, Bellotti and Edwards only suggest general design principles for context-aware systems; their work does not extend to recommending design approaches or methodologies that can be used to put these principles into practice." — §3, p.2 *(this is the gap Caddie addresses)*

> "Designing user interactions with computers involves deciding how to divide functions between humans and computers [4]. Not all tasks can (or should) be delegated to the system. There are *residual tasks* that are left to human users when a system is automated. In order to ensure that the residual tasks can be carried out effectively by users, the system should reveal its current understanding of the automated function, and allow users to correct this understanding whenever the system produces undesirable outcomes." — §3, p.2

## Paraphrases / my notes

- The paper distinguishes design-time trade-offs (traditional applications) from run-time trade-offs (context-aware systems). Caddie sits firmly in the runtime category. (source: §3 p.2)
- The continuum's three labelled positions are not a strict ranking; the paper explicitly argues position B may sometimes be best, sometimes A, sometimes C. The user's expertise, situation, and personal preference determine the appropriate point. (source: §2 p.1, Fig 1)
- van der Heijden's argument (cited in [2]): transferring control from user to system causes user anxiety, but full control increases mental effort. Both extremes are uncomfortable — hence the need for a tunable middle ground. (source: §3 p.2)
- End-user programming (§5.1) is described as one mechanism for balancing control: users define behaviour by demonstration or assembly (jigsaw metaphor). Loose parallel to Caddie's skill-saving mechanism where a successful run becomes a reusable skill. (source: §5.1 p.4)

## Key concepts / terms

- **Control-autonomy continuum** — three labelled positions A (full user control), B (intermediate), C (mostly autonomous). No single position is universally best. (source: §2 p.1, Fig 1)
- **Bellotti & Edwards principles** (Caddie's principle backbone) — three runtime intervention modes:
  - *Slight doubt* about desired outcome → user must be able to **correct** the action.
  - *Significant doubt* → user must **confirm** before action.
  - *No basis* for inferring → user must be offered **choices**.
  (source: §3 p.2)
- **Intelligibility and accountability** — Bellotti & Edwards's broader framing: a system should disclose what it knows, what it's about to do, and why. Caddie's overlay is a direct attempt to operationalise this. (source: §3 p.2)
- **Residual tasks** — work humans must still do when a system automates a process. The system must enable users to perform them effectively. (source: §3 p.2)
- **End-user programming** — programming-by-demonstration, programming-by-assembly. Mechanism for letting users specify behaviour without code. (source: §5.1 p.4)
- **Preference-based decision support** — adapting application behaviour based on context-dependent user preferences. (source: §5.2 p.4)

## How I plan to use this in the thesis

- **Section**: Introduction + Related Work (theoretical framing).
- **Role**: **Theoretical anchor**. Establishes the axis on which Caddie positions itself.
- **Specific claims it supports**:
  - "LLM smartphone agents today (AppAgent, MobileWorld, …) sit at position B/C on Hardian's continuum — high autonomy, low runtime intelligibility."
  - "Caddie deliberately shifts toward position A/B by adopting the Bellotti & Edwards intervention principles in the LLM-agent context."
  - "The Bellotti/Edwards triple — correct / confirm / choose — maps directly to Caddie's three overlay affordances (mid-run correction via touch-pause, swipe-to-confirm dialog for risky actions, voice-correction follow-up tasks)."
  - "Hardian et al. note the principles existed without 'design approaches or methodologies that can be used to put these principles into practice' — Caddie contributes such an approach for LLM smartphone agents."
- **Caution**: paper is pre-LLM (2006) and discusses smart-home / ubiquitous-computing context. You will need an explicit bridging paragraph: *"While Hardian et al. address context-aware systems broadly, the principles transfer to LLM agents because both share the core property of acting autonomously on the user's behalf in contexts where the system's interpretation of the user's intent can be wrong."*

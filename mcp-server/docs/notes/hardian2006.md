# Balancing Autonomy and User Control in Context-Aware Systems — A Survey

## Metadata

- **Authors**: Hardian, Bob; Indulska, Jadwiga; Henricksen, Karen
- **Affiliation**: School of Information Technology and Electrical Engineering, The University of Queensland
- **Venue / year**: Fourth Annual IEEE International Conference on Pervasive Computing and Communications Workshops (PerComW '06), 2006
- **DOI / link**: IEEE Xplore (see local PDF)
- **Local PDF**: `C:\Users\Andreas\Nextcloud\Master\Masterarbeit\IEEE Xplore Full-Text PDF.pdf`
- **First read**: 2026-06-01
- **Relevance to thesis**: **Theoretical anchor.** The autonomy/control continuum and the Bellotti & Edwards intervention principles are the backbone of why Caddie's overlay exists. Pre-LLM but the principles transfer cleanly.

## TL;DR (my words, after reading)

<TODO — write 3–4 lines after careful read. Provisional: A 2006 survey arguing that context-aware systems must offer mechanisms to balance user control against system autonomy along a continuum (A=full user control … C=full autonomy). Reproduces Bellotti & Edwards' three principles for when users must correct / confirm / choose system actions. Reviews context modelling, end-user programming, preference-based decision support as enablers of that balance.>

## Direct quotes

> "<TODO verbatim quote>" — §X, p.Y

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Control-autonomy continuum** — three labelled positions A (full user control), B (intermediate), C (mostly autonomous). The paper argues no single position is best — the right point depends on user needs, situation, expertise. (source: §2, p.1, Fig. 1)
- **Bellotti & Edwards principles** — three rules for run-time user intervention in autonomous systems:
  1. Slight doubt about desired outcome → user must be offered a way to **correct** the system action.
  2. Significant doubt → user must be able to **confirm** the action before it happens.
  3. No real basis for inferring → user must be offered available **choices**.
  (source: §3, p.2, reproduced from Bellotti & Edwards ref. [5])
- **Intelligibility and accountability** — Bellotti & Edwards's broader frame: a system should reveal what it knows, what it's about to do, and why. Caddie's overlay is a direct attempt at this. (source: §3, p.2)
- **Residual tasks** — work left to humans when a system automates a process. The system must enable users to actually perform those residual tasks. (source: §3, p.2)
- **End-user programming** — programming-by-demonstration, programming-by-assembly (jigsaw metaphor). Loosely related to Caddie's skill-saving mechanism (agent demonstrates a successful trace, becomes a skill). (source: §5.1, p.4)

## How I plan to use this in the thesis

- **Section**: Introduction + Related Work (theoretical framing).
- **Role**: **Theoretical anchor**. Establishes the axis on which Caddie positions itself.
- **Specific claims it supports**:
  - "LLM smartphone agents today (AppAgent, MobileWorld, …) sit at position B/C — high autonomy, low runtime intelligibility."
  - "Caddie deliberately shifts toward position A/B by adopting the Bellotti & Edwards intervention principles in the LLM-agent context."
  - "The Bellotti/Edwards triple — correct / confirm / choose — maps directly to Caddie's three overlay affordances (mid-run correction, confirm dialog, voice-correction follow-up tasks)."
- **Caution**: paper is pre-LLM (2006) and discusses smart-home / ubiquitous-computing context. You will need an explicit bridging paragraph: "While Hardian et al. address context-aware systems broadly, the principles transfer to LLM agents because …"

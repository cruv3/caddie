# AppAgent: Multimodal Agents as Smartphone Users

## Metadata

- **Authors**: Zhang, Chi; Yang, Zhao; Liu, Jiaxuan; Li, Yanda; Han, Yucheng; Chen, Xin; Huang, Zebiao; Fu, Bin; Yu, Gang
- **Affiliations**: Tencent / Westlake University (Hangzhou, Zhejiang) / Shanghai Supwisdom / University of Technology Sydney / Nanyang Technological University (Singapore)
- **Venue / year**: CHI '25 — Conference on Human Factors in Computing Systems, Yokohama, Japan, April 26 – May 1 2025
- **DOI / link**: https://doi.org/10.1145/3706598.3713600 — Project page: https://appagent-official.github.io/
- **License**: Creative Commons Attribution 4.0 International (open access)
- **Local PDF**: `C:\Users\Andreas\Nextcloud\Master\Masterarbeit\3706598.3713600.pdf`
- **First read**: 2026-06-01
- **Relevance to thesis**: **Direct comparator.** Same operating mode as Caddie (GUI-only, tap/swipe via multimodal LLM, no system backend access), different design philosophy (autonomy-first, no runtime intelligibility).

## TL;DR (my words, after reading)

CHI '25 paper from Tencent introducing an LLM-based multimodal agent that operates Android apps by tapping and swiping like a human, with no system backend access. Learns app behaviour via an autonomous exploration phase or a few-shot demonstration phase, generating a runtime *knowledge document* the agent consults before each action. Evaluated on 50 tasks across 10 apps. User study claims "superior performance and practicality" vs. Siri-style assistants. ACM CCS-classified as *Human-Centered Computing → User Interface Management Systems* — establishing it as an HCI paper, not a systems paper. Open-source.

## Direct quotes

> "We open-source a multimodal agent framework, focusing on operating smartphone applications with our developed action space." — §1, p.3 (Contributions list)

> "Our approach differs significantly from existing intelligent phone assistants like Siri, which operate through system back-end access and function calls. Instead, our agent interacts with smartphone apps in a human-like manner, using low-level operations such as tapping and swiping on the graphical user interface (GUI)." — §1, p.2

> "Firstly, it eliminates the need for system back-end access, making our agent universally applicable across various applications. Additionally, this approach enhances security and privacy, as the agent does not require deep system integration." — §1, p.2

> "Inspired by how humans quickly learn to use new apps, either through trial-and-error exploration or by observing demonstrations from others, the learning of our framework involves an exploration phase where the agent interacts autonomously with apps and learns from their outcomes in a few-shot manner. These interactions are documented, which serves as external knowledge to assist the agent in navigating and operating the apps during deployment." — §1, p.3

> "This in-context learning process can be accelerated by observing a few human demonstrations." — §1, p.3

> "We propose an innovative in-context learning strategy, which enables the agent to learn to use novel apps quickly." — §1, p.3

> "Through extensive experiments across multiple apps, we validate the advantages of our framework, demonstrating its potential in the realm of AI-assisted smartphone app operation." — §1, p.3 (Contributions)

## Paraphrases / my notes

- AppAgent is positioned explicitly *against* Siri-style assistants that rely on system APIs; the paper sells GUI-level operation as both more universal and more privacy-respecting. Caddie inherits this same design choice. (source: §1 p.2)
- The "in-context learning" is *not* fine-tuning; it's prompt-engineering a knowledge document that the agent reads on each call. This is the closest existing analog to Caddie's skill files. The mechanism is functionally identical: persistent procedural knowledge file consulted at action time. (source: §1 p.3)
- The paper evaluates 50 tasks across 10 apps (social media, messaging, email, maps, shopping, photo editing). Compare with AndroidWorld's 116 tasks across 20 apps and MobileWorld's 201 across 20. AppAgent's evaluation is the smallest of the three. (source: §1 p.2; cross-ref with AndroidWorld, MobileWorld notes)
- User study is mentioned in the abstract but the methodology is light by HCI standards — the paper's core is the system, not the study. (source: Abstract; §1 p.3)
- The agent observes app behaviour by examining XML view hierarchies + screenshots — the multimodal LLM ingests both. Caddie does the same (Accessibility-Service node tree + optional screenshot). (source: §1 p.2)

## Key concepts / terms

- **Action space** — restricted set of GUI primitives: tap(coords), text(input), long-press, swipe(direction, distance), back, exit. Mirrors human input modalities. (source: §1 + Fig.1)
- **In-context learning via exploration** — agent autonomously explores an app first, building its own documentation per UI element. No model fine-tuning. (source: §1 p.2-3)
- **Few-shot human demonstrations** — alternative/complementary path; user performs the task once, agent observes and writes documentation. (source: §1 p.3)
- **Knowledge document** — runtime artifact updated as exploration continues; consulted before each action. Direct analog of Caddie's skill files. (source: §1 p.3)
- **CCS concept**: *Human-centered computing → User interface management systems* — positions the paper in HCI literature. (source: front matter, p.1)

## How I plan to use this in the thesis

- **Section**: Related Work (state of the art) + Discussion (positioning).
- **Role**: **Direct comparator and counter-example.**
- **Specific claims it supports**:
  - "AppAgent represents the autonomy-first design philosophy in LLM smartphone agents: high autonomy, learn-once-deploy-many, no runtime intelligibility."
  - "AppAgent's knowledge document is the closest existing analog to Caddie's skill files — same data format (procedural Markdown-ish text), same runtime consultation pattern, but generated by agent exploration rather than by the user."
  - "AppAgent's evaluation focuses on task-completion rate; it does not measure whether users could *intervene* or *correct* the agent mid-run. This is the gap Caddie addresses."
  - "Both systems share the design choice of GUI-only operation (no system backend access); this commonality lets me isolate the contribution to runtime intelligibility rather than to the operating mode."
- **CCS concept worth citing**: *Human-centered computing → User interface management systems.* Confirms the gap Caddie fills is an HCI gap, not a systems gap.

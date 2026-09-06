# Automatic personal memory: design research and decision

Research date: 2026-09-06. Primary papers and official documentation only.
This is an engineering decision record, not a claim of measured Caddie benefit.
The manual-only implementation at `5ac3676e8` is the technical starting point,
not the intended automatic-learning product. The research below was performed
before implementing the revised admission path.

## Compared approaches

| Primary source / inspected version | Write timing and representation | Retrieval and maintenance | Boundary for this project |
|---|---|---|---|
| [Generative Agents: Interactive Simulacra of Human Behavior](https://arxiv.org/html/2304.03442v2), Park et al., arXiv v2 (2023), §§4.1–4.2 | Observations enter an episodic stream. Periodic model reflection creates higher-level inferences when accumulated importance reaches a threshold. | Weighted relevance, recency and model-rated importance. Reflections are retrieved alongside observations and retain links to supporting memories. | Evidence concerns believable simulated people, not safe personal-fact admission for a phone assistant. Do not promote inferred personality to truth. |
| [MemGPT: Towards LLMs as Operating Systems](https://arxiv.org/html/2310.08560v2), Packer et al., v2 (2024), §§2.1–2.4 | Model-issued functions move information between constrained working context and external archival/recall storage. System memory warnings can trigger management. | Search and paging bring external records into context. Working-context writes are distinct from the message queue and recall log. | Supports an agent-initiated write interface. It does not establish a sufficient privacy/provenance policy for Caddie's UI observations. |
| [Cognitive Architectures for Language Agents](https://arxiv.org/html/2309.02427v3), Sumers et al., v3 (2024), §§4.1, 4.4–4.6 | Conceptual separation of episodic experience, semantic knowledge, procedural rules and working memory. Learning is an internal action in the decision cycle. | Retrieval and learning are different operations. Writing procedural knowledge has different risks from adding facts. | A taxonomy, not a benchmark proving one implementation best. Keep personal facts separate from skill/replay procedures and never let them rewrite oversight. |
| [MemoryBank: Enhancing Large Language Models with Long-Term Memory](https://ojs.aaai.org/index.php/AAAI/article/download/29946/31654), Zhong et al., AAAI-24, pp.19725–19726, Memory Storage/Retrieval/Updating | Stores conversations, hierarchical event summaries and inferred user portraits. | Dense retrieval with FAISS. Exploratory forgetting uses elapsed time and a strength value reinforced when recalled. | Companion-memory experiments do not justify profiling or a forgetting curve for Caddie's document/contact references. Automatic expiry and retrieval relevance are separate decisions. |
| [Mem0: Building Production-Ready AI Agents with Scalable Long-Term Memory](https://arxiv.org/html/2504.19413v1), Chhikara et al., v1 (2025), §2.1/Fig.2 | New message pairs trigger extraction using recent dialogue plus an asynchronously updated summary. | Similar existing facts are supplied to a model that chooses ADD, UPDATE, DELETE or NOOP. | Useful extraction/update separation. Caddie will not delegate conflict deletion or factual authority solely to the model. The reported LOCOMO findings are not Caddie evidence. |
| [Mem0 OSS migration to the new memory algorithm](https://docs.mem0.ai/migration/oss-v2-to-v3), live official docs retrieved 2026-09-06, What Changed / Why ADD-Only | Current migration documentation describes single-pass ADD-only extraction, unlike the 2025 paper's two-phase update pipeline. | Hybrid semantic, BM25 and entity signals replace earlier defaults. API behavior is version-dependent. | Do not describe a current SDK by citing only the old paper, and do not copy an unverified TTL/API parameter. No Mem0 dependency is introduced. |
| [LangChain Memory overview](https://docs.langchain.com/oss/python/concepts/memory), live official docs retrieved 2026-09-06, Writing memories / Memory storage | Explicitly contrasts writes during the active turn with a separate background process. JSON documents are scoped by namespace/key. | Active-turn writes are available immediately but add latency and compete with task reasoning. Background work delays availability and requires trigger choices. | Choose active-turn proposals for this bounded corpus. No extra extractor service, scheduler, credential path or off-phone history export is needed. |
| [Letta Memory & dreaming](https://docs.letta.com/configuration/memory), live official docs retrieved 2026-09-06 | Agents update durable lessons in a git-backed memory filesystem. Background agents can consolidate recent conversations after steps or compaction. | A viewer and versioned files support review. A second agent can review proposed changes without asking the human. | Background consolidation is an alternative, not equivalent to human confirmation. Caddie's personal corpus remains encrypted local data, not a Git repository. |
| [Memory & context management with Claude Sonnet 4.6](https://platform.claude.com/cookbook/tool-use-memory-cookbook), official live cookbook, tool `memory_20250818`, §§4,7, retrieved 2026-09-06 | Model calls a client-implemented file-memory tool. | Advises bounded task-relevant patterns and cleanup. Warns that stored text can become a prompt-injection vector, recommends isolation and validation, and discourages sensitive storage. | General-purpose file edits are broader than needed. Use a narrow proposal interface with application-side gates, not arbitrary memory-file writes. |
| [Memory Injection Attacks on LLM Agents via Query-Only Interaction](https://papers.nips.cc/paper_files/paper/2025/file/42a97bbd9844d2bf68596730af80bcdf-Paper-Conference.pdf), Dong et al., NeurIPS 2025, §§1,3–4 | Demonstrates inducing agents to store malicious records through interaction rather than direct database access. | Poisoned records can steer later similar queries. The studied threat model includes a shared memory bank. | Evidence of a persistence attack surface, not a measured attack rate for Caddie. User-role text alone is not universal proof of benign content. Do not store reasoning traces or obey memory instructions. |

The papers above do not provide a universal auto-save/confirmation rule for a
single-owner Android assistant. Official docs describe product mechanisms,
not independent scientific validation. No general optimality, safety guarantee,
or benchmark transfer is inferred.

## Resulting Caddie decisions

1. **Model initiation, deterministic admission.** A normal-loop proposal tool
   lets the model decide that something may be reusable before it completes
   the task. This follows the active-turn alternative, not a mandatory
   post-task summarizer. Proposals do not require a special user command.
   Model omission remains possible and must be evaluated separately.
2. **Semantic references, not a raw episode archive.** Reuse the encrypted
   bounded personal corpus. Do not duplicate all task history or persist model
   reasoning, inferred personality, tool instructions, or new procedures.
3. **Verified source, not invented provenance.** Runtime captures the original
   current task, accepted corrections and real ask-user answers. Match the
   proposal's evidence against those records, never against an assistant
   statement, generated question, arbitrary tool JSON, webpage or UI node.
   Provenance records the source, not the truth of the proposition. There is
   initially no verified-runtime-result adapter suitable for durable facts.
4. **Three outcomes.** Auto-save is confined to a small deterministic set of
   stable low-risk preference statements supported by the complete current
   user utterance. Free-form facts, inferences, ambiguous statements and
   consequential document/contact/service mappings become encrypted pending
   candidates that cannot be retrieved until owner review. Missing evidence,
   credential/injection patterns and invalid bounds are rejected. This
   deliberately sacrifices broad automatic extraction to avoid pretending a
   model confidence score establishes safety. Expand the auto-save set only
   with explicit tests and a threat review.
5. **Conflict-aware maintenance.** Stable keys identify low-risk preference
   slots. Identical repeated claims are idempotent. A differing value is not
   silently substituted without an explicit correction or owner review.
   Arbitrary semantic deduplication and background reflection are deferred.
   Pending conflicts must not turn either candidate into established truth.
6. **Review rather than constant interruption.** Retain the existing editor
   as the review/correction/delete surface and expose pending candidates there.
   Harmless supported preferences do not require per-fact confirmation.
7. **Bounded retrieval and retention.** Keep the existing E5/lexical relevance
   gating and two-reference/8,000-character prompt bounds. Do not add an
   unvalidated model importance or recency weighting. Timestamp provenance for
   inspection, but do not silently forget an important rare reference merely
   because it was not recently retrieved. Document retention and bounded
   pending capacity explicitly. Deletion and expiry are not remote recall.
8. **No authority from memory.** Memory is untrusted reference text at use time.
   It cannot authorize sends, tool calls, confirmations, or file permissions.
   The deterministic study path has no memory-writing tool.

## Evidence and scope of the thesis

The short Chapter 3 addition should describe the implemented architecture,
not summarize these papers or claim its superiority. Therefore this design
research can remain a repository record without adding literature citations
to that paragraph. Its factual implementation claims still require an
immutable commit and tests in `CODE-TRACEABILITY.md`. If a paper-based claim is
later added to thesis prose, reopen the original and add the required exact
page/section and reproducible source-line provenance to `SOURCE-TRACEABILITY.md`.
This record does not substitute for that thesis citation gate.

Remaining risks to test: quoted/malicious text within a user message, invented
evidence, stale corrections, terse yes/no answers, cross-run leakage, duplicate
proposals, conflicting updates, pending-memory leakage into retrieval, storage
failure, restart, model omission, task latency, and attempts to use memory as
action authorization. Pattern filters are deliberately not represented as a
complete semantic privacy or prompt-injection detector.

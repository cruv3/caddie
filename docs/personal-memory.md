# Personal memory in normal mode

Personal memory is an encrypted, owner-controlled reference corpus for Caddie's native **normal mode**. It is absent from deterministic study profiles. It can contain notes entered by the owner and a deliberately narrow class of automatically admitted preferences. It is not a conversation archive, a model profile, a source of authority, or evidence that a statement is true.

The repository and APK contain no personal seed data. Model quality, recall, latency, and user benefit have not been measured by this implementation.

## Storage and owner control

Open **Runtime settings → Personal memory** to view, edit, delete, review, or add notes. Paste text directly into the editor; no file-import permission is needed. Manual titles are limited to 80 characters and text to 700 characters. The editor presents active notes and pending proposals separately. Manual changes require the owner attestation and use `replace(..., ownerConfirmed = true)`. Editing or deleting an active note explicitly supersedes any pending proposal for that target. Pending proposals are handled with their own **Approve** and **Reject** controls. Normal Save does not approve them.

The **Automatic learning** switch defaults to enabled for a newly empty store. Turning it off rejects future automatic proposals without deleting active notes or pending proposals. Clearing memory deletes both collections and the stored switch preference, so a fresh store again uses the default. The editor is memory-only across rotation, does not use saved-state parcels, and reserves the normal-task slot while visible, including while the activity is in the background. It refuses entry while a task or study mode is active. This prevents Caddie's own normal agent from using Accessibility to observe editor contents.

Notes have no TTL, recency expiry, background summarizer, or silent eviction. There are at most 32 active notes and 16 pending proposals. A full collection rejects the new write rather than deleting an older record. An active note has a UUID, version, update time, provenance, optional stable memory key, and optional source evidence. Provenance says where text came from, for example `owner-entered-and-confirmed`, `user-explicit`, or `model-inference`. It does not establish truth. There is no model confidence score. Evidence identifies a runtime source and quote, not an independently verified result.

The serialized UTF-8 envelope is additionally capped at 160,000 bytes before activation. Large multibyte/evidence-bearing entries may reach this bound before the count limits. Overflow leaves the previous generation readable and unchanged. Free-form identity uses `note:` plus SHA-256 of the normalized title inside the encrypted payload, keeping it bounded even when Unicode normalization expands a full 80-character title. No note-derived hash is exposed as plaintext database metadata.

Manual notes can be short references to a document, contact, or website. A path or content URI remains a reference only: storing it neither grants nor retains a file permission. Notes never authorize a tool, a send, a confirmation, or task completion. Existing oversight and critical-action confirmation remain responsible for consequential actions.

## What automatic learning can write

The model can call the narrow `caddie.memory_propose` tool during a normal agent loop. Its JSON carries only `title`, `text`, `evidence_quote`, an optional `inferred` flag, and an optional `target_id`/`target_version` pair. It cannot declare provenance, owner confirmation, or a verified result. The tool permits at most eight attempts for one run and never exposes a storage exception's private payload in its result.

`RuntimeMemoryEvidence` creates trusted evidence only from the current run's original user task, an accepted participant correction, or an actual answer to an `ask_user` prompt. It does not admit model output, tool JSON, UI trees, webpage text, generated questions, or arbitrary caller-supplied provenance. The evidence holder is cleared when the run finishes. At dispatch, the tool also rejects stale evidence, a stopped run, and a correction still awaiting consumption.

Automatic admission is intentionally limited to a complete, non-inferred user utterance. The submitted proposal text and evidence quote must be identical, and that quote must equal the full current user utterance after trimming. It must match one of these four stable preference slots:

| Slot | Accepted values |
|---|---|
| Response language | English, German, French, Spanish |
| Response length | concise or detailed |
| Measurement units | metric or imperial |
| Time format | 12-hour or 24-hour |

For an existing preference key, an unchanged value is an idempotent no-op. A different value becomes pending unless the utterance uses an explicit correction prefix (`from now on`, `ab jetzt`, or `ab sofort`) or is an accepted runtime correction. These conditions make an auto-save replace a known preference rather than silently changing it because the model judged the new text more relevant.

Free-form document, contact, service, or other personal references, model inferences, partial quotations, quoted website text, and unresolved conflicts are not automatically established. They become pending proposals when otherwise admissible. Pending proposals are never retrieval candidates. A model-specified target must match the deterministic preference key or normalized note title, not merely a valid UUID. When a proposal targets an active note, `retrievableFacts` also suppresses that active note until the owner resolves the conflict. A pending update stores the target's current version. Approval uses compare-and-swap semantics: if the target has changed, approval is rejected and the proposal remains available for review. The review card shows old and proposed text plus the actual evidence quote. Approved inferences retain `model-inference-confirmed` provenance. Manual semantic edits clear obsolete evidence and automatic slot identity, so repurposing a note cannot make a later preference correction overwrite it.

The gate rejects a missing or unsupported runtime source, empty/stale evidence, oversize values, malformed target/version pairs, terse yes/no answers, recognizable credentials or authentication material, sensitive-record labels, credential-bearing URLs, and known prompt-injection or oversight-bypass patterns. These are deterministic pattern checks, not a complete semantic privacy, security, or prompt-injection detector. They can miss unlabeled secrets, third-party sensitive information, false statements, or novel attacks, and may reject harmless text. No automatic or manual confirmation independently verifies a fact.

## Encryption, migration, and retention boundary

`PersonalContextStore` writes one encrypted owner corpus through `ContextRepository` and Room. The current payload is a JSON envelope containing `facts`, `pending`, and `learningEnabled`. It continues to read the previous bare JSON fact array and writes the envelope on the next successful mutation. All mutations are serialized by a mutex and complete their database mutation and revision invalidation in a non-cancellable I/O section.

The corpus uses AES-256-GCM with a dedicated, non-exportable Android Keystore key (`caddie_personal_context_v1`). Generation IDs and source hashes are random opaque metadata, not hashes of note text. Existing Android backup exclusions cover the database and sidecars. **Delete all** removes this owner's rows and dedicated key, including when ciphertext can no longer be decrypted. It does not delete other context owners or referenced files. A storage failure does not silently reset memory.

Deletion only controls future local retrieval. It cannot recall an already sent remote request, any remote retention, or copied task/history text. Encryption of this corpus does not encrypt every derived task artifact. The settings window uses `FLAG_SECURE`, but the selected keyboard and other enabled Accessibility services can still observe text rendered or entered in the editor.

## Retrieval and remote boundary

At normal-task preparation, `AndroidContextRequestFactoryProvider` reads the snapshot's `retrievableFacts`. It derives personal E5 embeddings locally for that task and uses the existing conservative `ContextRetriever`. At most four relevant hints can enter each retrieval result, including personal references, within the existing 8,000-character reference-context budget. The model receives selected personal references only through the normal request path. Model inference itself remains off-phone. Personal vectors are not persisted. If personal document embedding is unavailable or invalid, selection uses lexical matching for the personal set. If encrypted personal storage cannot be read, personal context is omitted while skill and replay retrieval continue.

The request factory captures the store revision. A save, review decision, toggle change, or clear invalidates its prepared personal references for later requests from that factory. Information already sent is unaffected. A newly stored reference is available to a newly prepared normal task, not retroactively as permission or context for an already sent request. The independent short-term conversation buffer remains unchanged.

## Production dataflow

| Step | Production symbols | Boundary enforced |
|---|---|---|
| Proposal candidate | Model → `PersonalMemoryToolRegistry` / `caddie.memory_propose` | Strict proposal schema, no model-supplied confirmation or provenance |
| Trusted evidence | `RuntimeMemoryEvidence`, `NativeRuntimeAssembler` | Current original task, accepted correction, or actual user answer only; cleared at run end |
| Admission | `AutomaticMemoryPolicy.evaluate` | Exact four-slot preference allowlist, idempotence/conflict decision, deterministic rejection patterns |
| Pending review | `PersonalContextStore.propose`, `reviewCandidate`, `PersonalContextScreen` | Pending candidate is not retrievable; explicit owner approve/reject and target-version CAS |
| Manual entry | `PersonalContextScreen`, `PersonalContextStore.replace` | Explicit attestation, bounds, duplicate checks, forbidden-payload checks |
| Encrypted persistence | `ContextRepository.activate`, `ContextDao.activateCorpus`, `AndroidKeystoreContextCipher` | Atomic encrypted envelope, dedicated key, opaque metadata, 32/16 caps |
| Retrieval and prompt | `AndroidContextRequestFactoryProvider.forTask`, `OnnxE5Embedder`, `ContextRetriever`, `ContextBundle`, `RagRequestFactory` | Local embeddings, bounded reference selection, reference-only prompt context |
| Correction and deletion | `replace`, `reviewCandidate`, `clear`, revision guard | Explicit supersession/CAS, no TTL, future-request invalidation, owner-only key reset |

## Relevant automated coverage

The following tests name the current boundaries. They are not claims about live model quality or user outcomes:

- `AutomaticMemoryPolicyTest`: complete supported preferences, pending free-form/inferred values, quotation/paraphrase rejection from the automatic path, forbidden patterns, idempotence, conflicts, and explicit corrections.
- `PersonalMemoryToolRegistryTest`: schema/provenance restrictions, stale evidence, absent-service behavior, per-run attempt limit, and payload-safe storage errors.
- `RuntimeMemoryEvidenceTest`: run-scoped task/correction/answer capture and cleanup.
- `PersonalFactValidationTest` and `PersonalContextEditorTest`: persisted metadata validation and editor snapshot/reservation behavior.
- `PersonalContextStoreDeviceTest`: encrypted reopen/clear, legacy-array migration, manual and automatic writes, pending retrieval suppression, owner review/CAS, disabled learning, and active/pending capacity boundaries.
- `PersonalMemoryAgentLoopDeviceTest`: scripted normal loop, real tool admission, Room/Keystore persistence, later-task retrieval, conflict gating, and study-profile exclusion. Its scripted model makes no remote request.

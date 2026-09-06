# Personal memory in normal mode

This post-study extension connects owner-authored personal notes to the native
normal assistant. It does not change the deterministic study runner or establish
a study result.

Open Runtime settings → Personal memory. Add a descriptive task topic and short
facts, attest that you checked them and may share them, then explicitly save and
allow their use by the configured remote model.
Paste text directly into the editor; no file-import permission is needed. Related
document, contact, and website facts can be grouped in one note. The repository
and APK contain no personal seed data. Up to 32 notes are supported (80-character
title, 700-character text). Removing a note requires Save; Delete all performs an
explicit immediate reset after confirmation.

`PersonalContextStore` uses `ContextRepository` to atomically replace one encrypted
JSON corpus. Contents use AES-256-GCM and a dedicated non-exportable Android
Keystore key. Only opaque random IDs/generation metadata are unencrypted; even
the existing source-hash column carries random opaque metadata rather than a
guessable hash of short facts. Existing Android backup exclusions cover the
database and its sidecars. Delete all removes this owner's rows and dedicated
key, including when old ciphertext is unreadable. It does not delete other
context owners or documents. Storage failure never silently resets data.

At task preparation, `AndroidContextRequestFactoryProvider` reads the current
snapshot, embeds its notes locally using the existing E5 provider, and applies
the existing conservative hybrid `ContextRetriever`. At most two notes enter
the existing 8,000-character reference context budget, before skill/replay
content. IDs and retrieval scores remain available in the retrieved hint
objects; there is no new content-bearing logging. Personal vectors are derived
in memory per task, not persisted; this bounds retained data and avoids stale
model vectors, but up to 32 document embeddings add task-start latency. Failed
embeddings switch personal selection to the existing lexical fallback, including
when only document embedding fails. Unreadable personal storage omits
personal context while retaining skill/replay retrieval. Existing short-term
conversation context is unaffected.

Saving or clearing increments a process revision: a previously prepared request
factory then omits its old personal references from subsequent model requests.
Already sent requests, remote model retention, and facts copied into conversation
or execution history cannot be recalled. Stop an active task before removing
sensitive context. Encryption of this corpus does not imply encryption of every
derived task artifact. Opening the editor atomically reserves the existing normal-task
exclusion and refuses active tasks or study mode. Its memory-only ViewModel holds
that reservation across rotation and backgrounding until the editor is closed,
so Caddie's own normal task cannot observe all notes through Accessibility.
The settings window also uses Android FLAG_SECURE; text input
still uses the device's configured keyboard. Other enabled Accessibility
services may read rendered notes, which FLAG_SECURE does not prevent.
Passwords and secrets should not be
stored as notes.

Facts are reference data, never capabilities: they cannot authorize tools,
bypass normal runtime oversight, or prove completion. Existing critical-action
confirmation remains responsible for sends and other consequential actions.
Prompt delimiting is not a general prompt-injection guarantee. Saving a path or
content URI neither grants nor persists a file permission. For a real demo the
user must select an accessible document and confirm recipient/service facts;
actual UI navigation, attachment access, and confirmation need a device run.
Model inference remains off-phone. No webmail-specific workflow is hardcoded.

## What is remembered, and why

Automatic long-term storage is disabled: there is no extraction from model
answers, conversations, screens, files, contacts, or tools. The only production
writer is the Personal memory editor. Its draft is a candidate, not saved memory.
The owner must attest that the facts were checked, may be shared, and contain no
credentials, unconfirmed guesses, or another person's sensitive records. Editing
text clears this attestation. Save calls `replace(..., ownerConfirmed = true)`;
the store rejects the default unconfirmed call even if invoked outside the UI.

`PersonalMemoryPolicy.decision` refuses recognizable password/credential/auth
labels, private-key/API-key/JWT shapes, credential-bearing URLs, explicit medical
record labels and uncertainty markers, even when the owner confirms. This is a
conservative deterministic filter, not semantic classification or complete data
loss prevention. It cannot identify an arbitrary unlabeled secret, establish
whose data a fact concerns, detect every sensitive category, or verify truth.
The attestation is therefore a real required owner review, not an assertion that
the app has independently checked facts. Do not store sensitive third-party
records or unconfirmed inferences. False-positive refusals are possible.

IDs are UUIDs. Duplicate IDs, normalized topics, or normalized fact text are
rejected instead of creating parallel copies. Update the existing note to
correct a fact; its ID stays stable. Conflicts across differently worded topics
are not semantically reconciled. Each record's fixed provenance is
`owner-entered-and-confirmed`; confidence is only the owner's assertion, never a
model probability or independent verification. This distinction is included in
the rendered reference text. There is no event-history log or confirmation
timestamp. Retention is explicit: saved notes persist until the owner edits or
deletes them, without automatic expiry or background refresh. The existing
five-minute short-term conversation buffer is a separate unchanged mechanism.

| Step | Production symbol | Implemented boundary |
|---|---|---|
| Candidate capture | `PersonalContextScreen`, `PersonalContextEditor` | Typed/pasted owner draft, rotation-safe memory-only ViewModel |
| Classification and validation | `PersonalMemoryPolicy.decision`, `PersonalContextStore.validate` | Conservative refusal patterns, length/count/UUID/provenance/duplicate checks |
| Admission | `PersonalContextScreen` checkbox and Save, `PersonalContextStore.replace` | Explicit attestation required, no automatic writer |
| Persistence | `ContextRepository.activate`, `ContextDao.activateCorpus`, `AndroidKeystoreContextCipher` | Atomic owner snapshot, dedicated AES-GCM key, opaque metadata |
| Embedding and selection | `AndroidContextRequestFactoryProvider.forTask`, `OnnxE5Embedder`, `ContextRetriever.retrieve` | Per-task in-memory vectors, maximum two hints, lexical fallback |
| Prompt | `ContextBundle.create`, `RagRequestFactory.create` | Reference-only bounded context, owner-confirmed qualification, no tool authorization |
| Correction and retention | `PersonalContextStore.replace`, `revision` | Same-ID edits, no automatic expiry, prepared personal context invalidated |
| Deletion/reset | `PersonalContextStore.clear`, `ContextDao.deleteOwner`, `AndroidKeystoreContextCipher.deleteKey` | Owner rows and dedicated key removed, non-cancellable commit/invalidation/key completion |

`PersonalMemoryPolicyTest`, `PersonalFactValidationTest`, provider unit tests,
and `PersonalContextStoreDeviceTest` cover the admission, persistence, failure,
real-E5 request, invalidation and cancellation boundaries. A fake or future
writer must not bypass `PersonalContextStore` to write directly to the lower-level
repository. The latter remains generic infrastructure rather than the personal
memory admission policy.

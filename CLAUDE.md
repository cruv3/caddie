---
tags: [project, claude-config, thesis]
project: Caddie
created: 2026-05-03
renamed: 2026-05-31 (from LLMSmartphone to Caddie)
---

# Caddie — Claude Project Rules

## Project context

- **Description**: Master's thesis Android app exploring UI transparency and user control in LLM-based smartphone agents. The agent operates the phone visibly, the user supervises and can intervene. V2 is built around an MCP server (different from V1).
- **Stack**: Kotlin (Android, package `com.caddie`) + Python (MCP server, package `caddie`)
- **Status**: active, **thesis not yet registered** — scope/questions may shift after supervisor talks
- **Successor relationship**: replaces an earlier V1 (no longer worked on)
- **GitHub**: https://github.com/cruv3/caddie (renamed from `LLM-Smartphone-Companion` on 2026-05-31; main branch was force-pushed with V2 content on 2026-05-03 — V1 history orphaned)
- **Local dir**: `C:\Users\Andreas\dev\LLMSmartphone\` (intentionally kept under old name to avoid breaking IDE/cache state, gradle daemon paths, MCP server registration in LM Studio, and the running session's cwd)

## Folder vs. package name

The *local directory* still carries the old `LLMSmartphone` name. Everything else — Python package `caddie`, Android namespace `com.caddie`, app display name "Caddie", GitHub repo `caddie` — uses the new name. Don't confuse the two; when in doubt, the package name is canonical.

## Vault stub

Strategic / cross-session docs live in the vault at:
`C:\Users\Andreas\dev\Vault\01 Projects\LLMSmartphone\` (vault folder name preserved for historical continuity)

- `LLMSmartphone.md` — overview / dashboard
- `log.md` — session-by-session narrative
- `decisions.md` — architectural / design / strategic decisions, dated
- `problems.md` — active blockers + resolved

Thesis exposé: `C:\Users\Andreas\dev\Vault\02 University\MasterExpose.pdf` (pre-registration draft).

The repo's `docs/` (under `mcp-server/docs/`) stays canonical for technical research notes. The `dossier/`, `meeting-vorstellung.md`, and experiment logs there are intentionally in German for thesis-meeting prep — do not translate.

## Read order

1. Vault overview: `Vault/01 Projects/LLMSmartphone/LLMSmartphone.md`
2. Vault `log.md` for recent session context
3. Vault `02 University/MasterExpose.pdf` if thesis-scope question
4. Repo `mcp-server/docs/` for technical research
5. Code via jCodemunch (Kotlin in `app/src/`, Python in `mcp-server/caddie/`)

## Write boundary

- Vault `log.md`, `problems.md` — append freely, dated
- Vault `decisions.md` — append, dated
- Vault overview, repo `docs/` files — additions only, no rewrites without ask
- **Master exposé PDF** — never edit; if changes are needed, work from a fresh source
- Code in `app/src/` and `mcp-server/` — edit normally as part of the task
- German thesis-prep docs (dossier, meeting-vorstellung, experiment logs) — stay German

After editing code, re-index: `mcp__jcodemunch__index_file { path: "<abs path>" }`.

## Conventions

- Kotlin code in `app/` follows Android Studio defaults; package root is `com.caddie`
- MCP server Python uses requirements.txt (no pyproject.toml currently); package root is `caddie`
- Accessibility-Service-related changes need manual on-device test (emulator via `mcp-server/start-emulator.bat`)

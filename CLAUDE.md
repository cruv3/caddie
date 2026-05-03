---
tags: [project, claude-config, thesis]
project: LLMSmartphone
created: 2026-05-03
---

# LLMSmartphone — Claude Project Rules

## Project context

- **Description**: Master's thesis Android app exploring UI abstraction layers for LLM-based smartphone agents. V2 is built around an MCP server (different from V1).
- **Stack**: Kotlin (Android) + Python (MCP server)
- **Status**: active, **thesis not yet registered** — scope/questions may shift after supervisor talks
- **Successor relationship**: replaces an earlier V1 (no longer worked on)

## Vault stub

Strategic / cross-session docs live in the vault at:
`C:\Users\Andreas\dev\Vault\01 Projects\LLMSmartphone\`

- `LLMSmartphone.md` — overview / dashboard
- `log.md` — session-by-session narrative
- `decisions.md` — architectural / design / strategic decisions, dated
- `problems.md` — active blockers + resolved

Thesis exposé: `C:\Users\Andreas\dev\Vault\02 University\MasterExpose.pdf` (pre-registration draft).

The repo's `docs/` (`accessibility_and_screen_experience.md`, `agent_orchestrator_and_skills_notes.md`, `mcp_smartphone_agent_research.md`) stays canonical for technical research notes.

## Read order

1. Vault overview: `Vault/01 Projects/LLMSmartphone/LLMSmartphone.md`
2. Vault `log.md` for recent session context
3. Vault `02 University/MasterExpose.pdf` if thesis-scope question
4. Repo `docs/` for technical research
5. Code via jCodemunch (Kotlin in `app/src/`, Python in `mcp-server/`)

## Write boundary

- Vault `log.md`, `problems.md` — append freely, dated
- Vault `decisions.md` — append, dated
- Vault overview, repo `docs/` files — additions only, no rewrites without ask
- **Master exposé PDF** — never edit; if changes are needed, work from a fresh source
- Code in `app/src/` and `mcp-server/` — edit normally as part of the task

After editing code, re-index: `mcp__jcodemunch__index_file { path: "<abs path>" }`.

## Conventions

- Kotlin code in `app/` follows Android Studio defaults
- MCP server Python uses requirements.txt (no pyproject.toml currently)
- Accessibility-Service-related changes need manual on-device test (emulator via `mcp-server/start-emulator.bat`)

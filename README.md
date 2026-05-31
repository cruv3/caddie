# Caddie

> Master's thesis Android app exploring UI transparency and user control in LLM-based smartphone agents (HCI focus). The agent operates the phone *visibly* — every tool call surfaces on an overlay so the user stays in the loop, can intervene, and can correct mid-run. Successor to V1, built around an MCP-server architecture.

The name: a caddie carries the bag and advises the shot, but the player decides every swing. Same role here — the agent has the UI expertise, the user keeps decision authority.

## Status

Active development. **Thesis not yet officially registered** — scope and research questions may evolve after further discussion with supervisor.

GitHub: https://github.com/cruv3/LLM-Smartphone-Companion (repo will be renamed to `caddie` in a follow-up step)

## Layout

- `app/` — Android (Kotlin) UI + Accessibility Service. Package `com.caddie`.
- `mcp-server/` — Python MCP server providing skills the on-device agent uses (separate Python project with its own deps). Package `caddie`.
- `docs/` — design + research notes (architecture, agent orchestration, related work).

## Vault docs

Cross-session / strategic docs live in the personal vault at:
`C:\Users\Andreas\dev\Vault\01 Projects\LLMSmartphone\` (kept under the old project name for historical continuity)

- Overview / dashboard
- Decisions log
- Session log
- Problems

The thesis exposé (early version, pre-registration) is at:
`C:\Users\Andreas\dev\Vault\02 University\MasterExpose.pdf`

# Model Context Protocol (MCP) — Specification & Announcement

## Metadata

- **Authors / organization**: Anthropic (protocol designers: David Soria Parra, Justin Spahr-Summers)
- **Affiliation**: Anthropic, PBC
- **Venue + year**: Anthropic announcement, November 25, 2024; protocol spec open-standard release
- **Announcement**: https://www.anthropic.com/news/model-context-protocol
- **Spec hub**: https://modelcontextprotocol.io
- **Spec versions**: https://modelcontextprotocol.io/specification
- **First read**: <TODO>
- **Relevance to thesis**: **Spec reference.** Caddie's V2 architecture is literally an MCP server — this is the normative reference for tool/resource/prompt semantics and capability negotiation.

## TL;DR (my words, after reading)

MCP is an open, JSON-RPC 2.0–based standard (LSP-inspired) for connecting LLM applications ("clients/hosts") to external data and tools ("servers"). Targets the M×N integration problem between models and tools. Ships with Python and TypeScript SDKs plus reference servers.

## Direct quotes

> "A new standard for connecting AI assistants to the systems where data lives." — Anthropic announcement

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **Client/server protocol over JSON-RPC** — vendor-neutral, language-agnostic. (source: spec)
- **Tools / Resources / Prompts** — three primitive surfaces an MCP server can expose. (source: spec)

## How I plan to use this in the thesis

- **Section**: Architecture (MCP layer).
- **Role**: **Spec reference.**
- **Specific claims it supports**:
  - "Caddie's MCP server exposes `smartphone_*` tools per the MCP tool primitive. The architecture chapter cites the spec for tool/resource semantics and the capability-negotiation handshake that determines what LM Studio sees."
  - "MCP's tools-only forwarding behaviour in LM Studio (instructions field ignored — see project's own architecture.md and bug tracker references) is a known limitation; this is documented in the spec and in LM Studio bug tracker issues #1412 and #1154."

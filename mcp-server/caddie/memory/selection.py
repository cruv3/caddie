"""Semantic skill selection helper — Phase 1a / 1c.

Provides:
  _semantic_on()           — strict flag check (only exactly "1" is on)
  select_prompt_skills()   — choose prompt skills: semantic (flag on + index) or trigger
  select_prompt_hints()    — choose explored-entry hints for the prompt (Phase 1c / Task 5)

IMPORTANT: select_prompt_skills is used ONLY for build_system_prompt.
The `skill=` arg passed to agent_loop.run must ALWAYS remain context.skills.match()
(the trigger top-match). Semantic-driven replay is Phase 1b.

select_prompt_hints() is default-NEUTRAL: returns [] unless BOTH the semantic
flag is on AND context.memory_index is not None.  http_api wiring is deferred.
"""
from __future__ import annotations

import os
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from caddie.context import ServerContext
    from caddie.memory.entry import MemoryEntry
    from caddie.skills.library import Skill


def _semantic_on() -> bool:
    """Return True only when LLM_SMARTPHONE_SEMANTIC_MATCH is exactly "1".

    Strict equality prevents accidental activation from values like "true",
    "yes", or "True" that could arise from typos or shell variable expansion.
    """
    return os.environ.get("LLM_SMARTPHONE_SEMANTIC_MATCH", "0") == "1"


def select_prompt_skills(
    context: "ServerContext",
    task: str,
    trigger_matched: "list[Skill] | None" = None,
) -> "list[Skill]":
    """Return skills to use in build_system_prompt.

    When the semantic flag is ON and a MemoryIndex is built on context,
    delegates to the index (embedding-based ranking). Otherwise falls back
    to the standard trigger match — byte-identical to the pre-Phase-1a path.

    Pass ``trigger_matched`` when the caller has already computed
    ``context.skills.match(task)`` so the trigger path avoids a second
    identical lookup (flag-off / no-index fast path reuses the result).

    This function must NEVER be used to determine the `skill=` arg for
    agent_loop.run. That arg must always come from context.skills.match(task).
    """
    if _semantic_on() and context.memory_index is not None:
        return context.memory_index.match(task)
    # Flag off or no index: reuse the already-computed trigger result when
    # available, otherwise compute it now (keeps the call-site optional).
    if trigger_matched is not None:
        return trigger_matched
    return context.skills.match(task)


def select_prompt_hints(
    context: "ServerContext",
    task: str,
    k: int = 2,
) -> "list[MemoryEntry]":
    """Return explored MemoryEntry objects to surface as prompt hints.

    Default-NEUTRAL contract:
      - Returns [] when the semantic flag is off.
      - Returns [] when context.memory_index is None.
      - Otherwise delegates to memory_index.match_hints(task, k).

    http_api call-site wiring is deferred (Phase 1c post-Codex live step).
    """
    if not _semantic_on() or context.memory_index is None:
        return []
    return context.memory_index.match_hints(task, k)

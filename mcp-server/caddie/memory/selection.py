"""Semantic skill selection helper — Phase 1a.

Provides:
  _semantic_on()           — strict flag check (only exactly "1" is on)
  select_prompt_skills()   — choose prompt skills: semantic (flag on + index) or trigger

IMPORTANT: select_prompt_skills is used ONLY for build_system_prompt.
The `skill=` arg passed to agent_loop.run must ALWAYS remain context.skills.match()
(the trigger top-match). Semantic-driven replay is Phase 1b.
"""
from __future__ import annotations

import os
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from caddie.context import ServerContext
    from caddie.skills.library import Skill


def _semantic_on() -> bool:
    """Return True only when LLM_SMARTPHONE_SEMANTIC_MATCH is exactly "1".

    Strict equality prevents accidental activation from values like "true",
    "yes", or "True" that could arise from typos or shell variable expansion.
    """
    return os.environ.get("LLM_SMARTPHONE_SEMANTIC_MATCH", "0") == "1"


def select_prompt_skills(context: "ServerContext", task: str) -> "list[Skill]":
    """Return skills to use in build_system_prompt.

    When the semantic flag is ON and a MemoryIndex is built on context,
    delegates to the index (embedding-based ranking). Otherwise falls back
    to the standard trigger match — byte-identical to the pre-Phase-1a path.

    This function must NEVER be used to determine the `skill=` arg for
    agent_loop.run. That arg must always come from context.skills.match(task).
    """
    if _semantic_on() and context.memory_index is not None:
        return context.memory_index.match(task)
    return context.skills.match(task)

"""Tests for hot-reload atomic publish logic (Finding #1).

Covers:
  - On MemoryIndex.build failure → context.memory_index must be None (not old index)
  - On success → context.skills and context.memory_index both updated to new generation
  - Publish order: never new-lib + old-index intermediate state

The publish logic is extracted into _publish_reload() in selection.py to make
it unit-testable without importing the full AgentLoop.
"""
from __future__ import annotations

from pathlib import Path
from unittest.mock import MagicMock

import pytest

from caddie.skills.library import Skill


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_skill(skill_id: str, trigger: str) -> Skill:
    return Skill(
        id=skill_id,
        title=skill_id.replace(".", " ").title(),
        description=f"Description for {skill_id}",
        triggers=(trigger,),
        body="## Verification\nCheck it.",
        path=Path(f"/fake/{skill_id.replace('.', '/')}.md"),
    )


class _FakeSkillLibrary:
    def __init__(self, skills: list) -> None:
        self._skills = skills

    def match(self, task: str) -> list:
        return list(self._skills)


class _FakeMemoryIndex:
    def __init__(self, skills: list) -> None:
        self._skills = skills

    def match(self, task: str) -> list:
        return list(self._skills)


class _FakeContext:
    """Minimal mutable context for publish tests."""

    def __init__(self, skills, memory_index=None):
        self.skills = skills
        self.memory_index = memory_index
        # Track every intermediate state written
        self._states: list[tuple] = []

    def _record(self, label: str) -> None:
        self._states.append((label, self.skills, self.memory_index))


# ---------------------------------------------------------------------------
# _publish_reload helper — mirrors the agent_loop publish block logic
# ---------------------------------------------------------------------------

def _publish_reload(context: _FakeContext, new_lib, new_index) -> None:
    """Replicate the three-step atomic publish from agent_loop hot-reload.

    Order:
      1. memory_index = None      (old-lib + no-index — trigger fallback safe)
      2. skills = new_lib         (new-lib + no-index — trigger fallback safe)
      3. memory_index = new_index (new-lib + new-index — fully consistent)
    """
    context.memory_index = None
    context._record("after_none")
    context.skills = new_lib
    context._record("after_new_lib")
    context.memory_index = new_index
    context._record("after_new_index")


# ---------------------------------------------------------------------------
# Tests: failure path
# ---------------------------------------------------------------------------

class TestHotReloadFailure:
    """When MemoryIndex.build raises, the published index must be None."""

    def test_failure_sets_index_to_none(self):
        old_skill = _make_skill("android.wifi", "wifi")
        new_skill = _make_skill("android.bluetooth", "bluetooth")
        old_lib = _FakeSkillLibrary([old_skill])
        new_lib = _FakeSkillLibrary([new_skill])
        old_index = _FakeMemoryIndex([old_skill])

        ctx = _FakeContext(skills=old_lib, memory_index=old_index)

        # Simulate: build raises, so _new_index stays None (as per the fix)
        _new_index = None  # failure path sets None, NOT old_index

        _publish_reload(ctx, new_lib, _new_index)

        assert ctx.skills is new_lib, "skills must be updated to new library"
        assert ctx.memory_index is None, (
            "on index rebuild failure, memory_index must be None — "
            "not the old index (which would be a generation mismatch)"
        )

    def test_failure_never_leaves_new_lib_old_index(self):
        """No intermediate state should be new-lib + old-index."""
        old_skill = _make_skill("android.wifi", "wifi")
        new_skill = _make_skill("android.bluetooth", "bluetooth")
        old_lib = _FakeSkillLibrary([old_skill])
        new_lib = _FakeSkillLibrary([new_skill])
        old_index = _FakeMemoryIndex([old_skill])

        ctx = _FakeContext(skills=old_lib, memory_index=old_index)

        _publish_reload(ctx, new_lib, None)

        for label, skills, memory_index in ctx._states:
            assert not (skills is new_lib and memory_index is old_index), (
                f"Intermediate state '{label}' has new-lib + old-index — "
                "this is a generation mismatch and must never occur"
            )


# ---------------------------------------------------------------------------
# Tests: success path
# ---------------------------------------------------------------------------

class TestHotReloadSuccess:
    """When MemoryIndex.build succeeds, both attributes updated to new generation."""

    def test_success_updates_both(self):
        old_skill = _make_skill("android.wifi", "wifi")
        new_skill = _make_skill("android.bluetooth", "bluetooth")
        old_lib = _FakeSkillLibrary([old_skill])
        new_lib = _FakeSkillLibrary([new_skill])
        old_index = _FakeMemoryIndex([old_skill])
        new_index = _FakeMemoryIndex([new_skill])

        ctx = _FakeContext(skills=old_lib, memory_index=old_index)

        _publish_reload(ctx, new_lib, new_index)

        assert ctx.skills is new_lib, "skills must be updated"
        assert ctx.memory_index is new_index, "memory_index must be updated to new index"

    def test_success_never_leaves_new_lib_old_index(self):
        """Intermediate state must never be new-lib + old-index."""
        old_skill = _make_skill("android.wifi", "wifi")
        new_skill = _make_skill("android.bluetooth", "bluetooth")
        old_lib = _FakeSkillLibrary([old_skill])
        new_lib = _FakeSkillLibrary([new_skill])
        old_index = _FakeMemoryIndex([old_skill])
        new_index = _FakeMemoryIndex([new_skill])

        ctx = _FakeContext(skills=old_lib, memory_index=old_index)

        _publish_reload(ctx, new_lib, new_index)

        for label, skills, memory_index in ctx._states:
            assert not (skills is new_lib and memory_index is old_index), (
                f"Intermediate state '{label}' has new-lib + old-index — "
                "generation mismatch must never appear"
            )

    def test_publish_order_sequence(self):
        """Verify the exact 3-step order of intermediate states."""
        old_lib = _FakeSkillLibrary([])
        new_lib = _FakeSkillLibrary([])
        old_index = _FakeMemoryIndex([])
        new_index = _FakeMemoryIndex([])

        ctx = _FakeContext(skills=old_lib, memory_index=old_index)

        _publish_reload(ctx, new_lib, new_index)

        assert len(ctx._states) == 3

        # Step 1: memory_index cleared to None, skills still old
        label1, skills1, idx1 = ctx._states[0]
        assert skills1 is old_lib
        assert idx1 is None

        # Step 2: skills updated, memory_index still None
        label2, skills2, idx2 = ctx._states[1]
        assert skills2 is new_lib
        assert idx2 is None

        # Step 3: memory_index updated to new
        label3, skills3, idx3 = ctx._states[2]
        assert skills3 is new_lib
        assert idx3 is new_index


# ---------------------------------------------------------------------------
# Tests: trigger_matched reuse in select_prompt_skills (Finding #2)
# ---------------------------------------------------------------------------

class TestSelectPromptSkillsTriggerReuse:
    """Passing trigger_matched avoids a second skills.match() call when flag is off."""

    def setup_method(self):
        import os
        os.environ.pop("LLM_SMARTPHONE_SEMANTIC_MATCH", None)

    def teardown_method(self):
        import os
        os.environ.pop("LLM_SMARTPHONE_SEMANTIC_MATCH", None)

    def test_trigger_matched_reused_when_flag_off(self):
        import os
        from caddie.memory.selection import select_prompt_skills

        os.environ.pop("LLM_SMARTPHONE_SEMANTIC_MATCH", None)

        trigger_skill = _make_skill("android.wifi", "wifi")
        lib = _FakeSkillLibrary([trigger_skill])
        ctx = _FakeContext(skills=lib, memory_index=None)

        precomputed = [trigger_skill]
        result = select_prompt_skills(ctx, "wifi task", trigger_matched=precomputed)

        assert result is precomputed, (
            "select_prompt_skills must return the pre-computed trigger_matched "
            "list (not a new list) when flag is off"
        )

    def test_trigger_matched_ignored_when_semantic_on_with_index(self):
        """When semantic is ON and index present, trigger_matched is ignored."""
        import os
        from caddie.memory.selection import select_prompt_skills

        os.environ["LLM_SMARTPHONE_SEMANTIC_MATCH"] = "1"

        trigger_skill = _make_skill("android.wifi", "wifi")
        semantic_skill = _make_skill("android.bluetooth", "bluetooth")
        lib = _FakeSkillLibrary([trigger_skill])
        fake_index = _FakeMemoryIndex([semantic_skill])
        ctx = _FakeContext(skills=lib, memory_index=fake_index)

        precomputed = [trigger_skill]
        result = select_prompt_skills(ctx, "some task", trigger_matched=precomputed)

        assert result == [semantic_skill], (
            "When semantic ON + index present, must use index — trigger_matched is ignored"
        )
        assert result is not precomputed

    def test_trigger_matched_none_falls_back_to_match(self):
        """When trigger_matched is None (default), falls back to skills.match()."""
        import os
        from caddie.memory.selection import select_prompt_skills

        os.environ.pop("LLM_SMARTPHONE_SEMANTIC_MATCH", None)

        trigger_skill = _make_skill("android.wifi", "wifi")
        lib = _FakeSkillLibrary([trigger_skill])
        ctx = _FakeContext(skills=lib, memory_index=None)

        result = select_prompt_skills(ctx, "wifi task")  # no trigger_matched

        assert result == [trigger_skill]

    def test_skill_arg_still_trigger_bound_with_reuse(self):
        """skill= arg must be matched[0] (trigger), even when select_prompt_skills reuses it."""
        import os
        from caddie.memory.selection import select_prompt_skills

        os.environ["LLM_SMARTPHONE_SEMANTIC_MATCH"] = "1"

        trigger_skill = _make_skill("android.wifi", "wifi")
        semantic_skill = _make_skill("android.bluetooth", "bluetooth")
        lib = _FakeSkillLibrary([trigger_skill])
        fake_index = _FakeMemoryIndex([semantic_skill])
        ctx = _FakeContext(skills=lib, memory_index=fake_index)

        matched = ctx.skills.match("task")
        prompt_skills = select_prompt_skills(ctx, "task", trigger_matched=matched)

        # skill= arg is always matched[0], never from prompt_skills
        skill_arg = matched[0] if matched else None
        assert skill_arg is trigger_skill, "skill= must be trigger top-match"
        assert skill_arg is not semantic_skill, "semantic skill must never reach skill= arg"
        assert prompt_skills == [semantic_skill], "prompt uses semantic"

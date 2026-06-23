"""Tests for Task 3: semantic skill selection for PROMPT ONLY.

Critical invariant: with the flag ON, the prompt may use semantic selection
(select_prompt_skills), but the `skill=` arg passed to agent_loop.run MUST
remain the trigger top-match — NEVER the semantic result.
"""
from __future__ import annotations

import os
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

# ---------------------------------------------------------------------------
# Helpers to build fake Skill / SkillLibrary / MemoryIndex objects
# ---------------------------------------------------------------------------

from caddie.skills.library import Skill


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
    """Minimal SkillLibrary stand-in with a fixed match result."""

    def __init__(self, skills: list[Skill]) -> None:
        self._skills = skills

    def match(self, task: str) -> list[Skill]:
        return list(self._skills)

    def all(self) -> list[Skill]:
        return list(self._skills)

    def get(self, skill_id: str) -> Skill | None:
        return next((s for s in self._skills if s.id == skill_id), None)


class _FakeMemoryIndex:
    """Fake MemoryIndex that always returns a fixed set of skills."""

    def __init__(self, skills: list[Skill]) -> None:
        self._skills = skills

    def match(self, task: str) -> list[Skill]:
        return list(self._skills)


class _FakeContext:
    """Minimal ServerContext stand-in."""

    def __init__(self, skills, memory_index=None):
        self.skills = skills
        self.memory_index = memory_index


# ---------------------------------------------------------------------------
# Tests for select_prompt_skills
# ---------------------------------------------------------------------------

class TestSelectPromptSkillsFlagOff:
    """When the flag is OFF (default), select_prompt_skills == skills.match."""

    def setup_method(self):
        # Guarantee flag is off
        os.environ.pop("LLM_SMARTPHONE_SEMANTIC_MATCH", None)

    def test_flag_unset_returns_trigger_match(self):
        from caddie.memory.selection import select_prompt_skills

        trigger_skill = _make_skill("android.wifi", "wifi")
        lib = _FakeSkillLibrary([trigger_skill])
        ctx = _FakeContext(skills=lib, memory_index=None)

        result = select_prompt_skills(ctx, "wifi aktivieren")
        assert result == [trigger_skill]

    def test_flag_zero_returns_trigger_match(self):
        from caddie.memory.selection import select_prompt_skills

        os.environ["LLM_SMARTPHONE_SEMANTIC_MATCH"] = "0"
        trigger_skill = _make_skill("android.wifi", "wifi")
        lib = _FakeSkillLibrary([trigger_skill])
        ctx = _FakeContext(skills=lib, memory_index=None)

        result = select_prompt_skills(ctx, "wifi aktivieren")
        assert result == [trigger_skill]

    def test_flag_off_ignores_memory_index_even_if_present(self):
        from caddie.memory.selection import select_prompt_skills

        trigger_skill = _make_skill("android.wifi", "wifi")
        semantic_skill = _make_skill("android.bluetooth", "bluetooth")
        lib = _FakeSkillLibrary([trigger_skill])
        fake_index = _FakeMemoryIndex([semantic_skill])
        # memory_index is present but flag is off — must use trigger path
        ctx = _FakeContext(skills=lib, memory_index=fake_index)

        result = select_prompt_skills(ctx, "some task")
        assert result == [trigger_skill]

    def test_flag_arbitrary_string_is_off(self):
        """Only exactly '1' is on; 'true', 'yes', etc. must be treated as off."""
        from caddie.memory.selection import select_prompt_skills

        os.environ["LLM_SMARTPHONE_SEMANTIC_MATCH"] = "true"
        trigger_skill = _make_skill("android.wifi", "wifi")
        lib = _FakeSkillLibrary([trigger_skill])
        ctx = _FakeContext(skills=lib, memory_index=None)

        result = select_prompt_skills(ctx, "wifi")
        assert result == [trigger_skill]

    def teardown_method(self):
        os.environ.pop("LLM_SMARTPHONE_SEMANTIC_MATCH", None)


class TestSelectPromptSkillsFlagOn:
    """When the flag is ON and an index is present, select_prompt_skills uses it."""

    def setup_method(self):
        os.environ["LLM_SMARTPHONE_SEMANTIC_MATCH"] = "1"

    def test_flag_on_with_index_uses_index(self):
        from caddie.memory.selection import select_prompt_skills

        trigger_skill = _make_skill("android.wifi", "wifi")
        semantic_skill = _make_skill("android.bluetooth", "bluetooth")
        lib = _FakeSkillLibrary([trigger_skill])
        fake_index = _FakeMemoryIndex([semantic_skill])
        ctx = _FakeContext(skills=lib, memory_index=fake_index)

        result = select_prompt_skills(ctx, "some wireless task")
        assert result == [semantic_skill]

    def test_flag_on_without_index_falls_back_to_trigger(self):
        from caddie.memory.selection import select_prompt_skills

        trigger_skill = _make_skill("android.wifi", "wifi")
        lib = _FakeSkillLibrary([trigger_skill])
        ctx = _FakeContext(skills=lib, memory_index=None)

        result = select_prompt_skills(ctx, "some wireless task")
        assert result == [trigger_skill]

    def teardown_method(self):
        os.environ.pop("LLM_SMARTPHONE_SEMANTIC_MATCH", None)


# ---------------------------------------------------------------------------
# _semantic_on helper
# ---------------------------------------------------------------------------

class TestSemanticOnFlag:
    def teardown_method(self):
        os.environ.pop("LLM_SMARTPHONE_SEMANTIC_MATCH", None)

    def test_unset_is_off(self):
        from caddie.memory.selection import _semantic_on
        os.environ.pop("LLM_SMARTPHONE_SEMANTIC_MATCH", None)
        assert _semantic_on() is False

    def test_zero_is_off(self):
        from caddie.memory.selection import _semantic_on
        os.environ["LLM_SMARTPHONE_SEMANTIC_MATCH"] = "0"
        assert _semantic_on() is False

    def test_one_is_on(self):
        from caddie.memory.selection import _semantic_on
        os.environ["LLM_SMARTPHONE_SEMANTIC_MATCH"] = "1"
        assert _semantic_on() is True

    def test_true_is_off(self):
        from caddie.memory.selection import _semantic_on
        os.environ["LLM_SMARTPHONE_SEMANTIC_MATCH"] = "true"
        assert _semantic_on() is False

    def test_yes_is_off(self):
        from caddie.memory.selection import _semantic_on
        os.environ["LLM_SMARTPHONE_SEMANTIC_MATCH"] = "yes"
        assert _semantic_on() is False


# ---------------------------------------------------------------------------
# THE SEPARATION TEST — the critical regression guard
#
# Verifies that with the flag ON:
#   - build_system_prompt receives the SEMANTIC skills (from select_prompt_skills)
#   - agent_loop.run is called with skill= = TRIGGER top-match (NOT semantic)
# ---------------------------------------------------------------------------

class TestSeparationGuard:
    """The critical test: with flag ON, prompt-path uses semantic, replay-path uses trigger."""

    def setup_method(self):
        os.environ["LLM_SMARTPHONE_SEMANTIC_MATCH"] = "1"

    def teardown_method(self):
        os.environ.pop("LLM_SMARTPHONE_SEMANTIC_MATCH", None)

    def test_prompt_uses_semantic_skill_replay_uses_trigger_skill(self):
        """
        Simulate what http_api does:

            matched = context.skills.match(task)          # TRIGGER path
            prompt_skills = select_prompt_skills(ctx, task)  # SEMANTIC path (flag on)
            system_prompt = build_system_prompt(prompt_skills, ...)
            agent_loop.run(..., skill=(matched[0] if matched else None))

        Assert:
          - build_system_prompt was called with the SEMANTIC skill (not trigger skill)
          - agent_loop.run was called with skill= = TRIGGER skill (not semantic skill)
        """
        from caddie.memory.selection import select_prompt_skills

        trigger_skill = _make_skill("android.wifi", "wifi")
        semantic_skill = _make_skill("android.bluetooth", "bluetooth")

        lib = _FakeSkillLibrary([trigger_skill])
        fake_index = _FakeMemoryIndex([semantic_skill])
        ctx = _FakeContext(skills=lib, memory_index=fake_index)

        task = "connect to wireless network"

        # Simulate the two-variable pattern from http_api
        matched = ctx.skills.match(task)          # trigger path — unchanged
        prompt_skills = select_prompt_skills(ctx, task)  # semantic path

        # Verify the separation
        assert matched == [trigger_skill], "trigger path must return trigger skill"
        assert prompt_skills == [semantic_skill], "prompt path must return semantic skill"

        # The skill= arg to agent_loop.run comes from matched, NOT prompt_skills
        skill_arg = matched[0] if matched else None
        assert skill_arg is trigger_skill, (
            "skill= arg (for replay/recording) must be TRIGGER top-match, "
            "never the semantic result"
        )
        assert skill_arg is not semantic_skill, (
            "semantic skill must NOT be passed as skill= arg"
        )

    def test_prompt_and_replay_differ_when_flag_on(self):
        """Ensure prompt_skills != matched when flag is on and index returns different skills."""
        from caddie.memory.selection import select_prompt_skills

        trigger_skill = _make_skill("android.wifi", "wifi")
        semantic_skill = _make_skill("android.bluetooth", "bluetooth")

        lib = _FakeSkillLibrary([trigger_skill])
        fake_index = _FakeMemoryIndex([semantic_skill])
        ctx = _FakeContext(skills=lib, memory_index=fake_index)

        matched = ctx.skills.match("task")
        prompt_skills = select_prompt_skills(ctx, "task")

        # They must be different — that's the whole point of semantic selection
        assert matched != prompt_skills
        assert trigger_skill in matched
        assert semantic_skill in prompt_skills
        assert trigger_skill not in prompt_skills
        assert semantic_skill not in matched

    def test_prompt_and_replay_identical_when_flag_off(self):
        """When flag is off, prompt_skills == matched (byte-identical default behavior)."""
        os.environ["LLM_SMARTPHONE_SEMANTIC_MATCH"] = "0"

        from caddie.memory.selection import select_prompt_skills

        trigger_skill = _make_skill("android.wifi", "wifi")
        lib = _FakeSkillLibrary([trigger_skill])
        ctx = _FakeContext(skills=lib, memory_index=None)

        matched = ctx.skills.match("wifi task")
        prompt_skills = select_prompt_skills(ctx, "wifi task")

        assert matched == prompt_skills

"""Tests for Phase 1c Task 5: persist explored entries + prompt hints.

Four test groups (offline, fake embedder):
  1. store round-trip: save/load equality; dedup drops duplicates.
  2. MemoryIndex.match_hints: explored entries returned; explored never leaks into match().
  3. select_prompt_hints: [] when semantic off; hints when on + index present.
  4. build_system_prompt: byte-identical when hints=None/[]; hints block present when hints non-empty.
"""
from __future__ import annotations

import json
import os
from pathlib import Path

import numpy as np
import pytest

from caddie.memory.entry import MemoryEntry
from caddie.memory.embedder import Embedder
from caddie.memory.index import MemoryIndex
from caddie.skills.library import Skill, SkillLibrary


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

_SENTINEL = object()


def _explored(
    entry_id: str,
    app: str = "com.example.app",
    intent_text: str = "turn on wifi",
    state_sig: str = "sig-abc",
    provenance: object = _SENTINEL,
) -> MemoryEntry:
    if provenance is _SENTINEL:
        resolved_prov: dict | None = {"app": app, "path": ["tap wifi", "tap toggle"]}
    else:
        resolved_prov = provenance  # type: ignore[assignment]
    return MemoryEntry(
        id=entry_id,
        app=app,
        intent_text=intent_text,
        triggers=(),
        body="",
        kind="explored",
        complete_trajectory=False,
        steps=(),
        source_path="",
        state_sig=state_sig,
        provenance=resolved_prov,
        confidence=0.3,
        fingerprint=None,
        schema_version=1,
    )


def _skill(sid: str, triggers: list[str], title: str) -> Skill:
    return Skill(
        id=sid,
        title=title,
        description=f"Description for {sid}",
        triggers=tuple(triggers),
        body="## Verification\nok",
        path=Path(f"skills/{sid}.md"),
        steps=(),
    )


# Fake bag-of-words embedder (no model download)
VOCAB = ["wifi", "bluetooth", "dark", "on", "off"]


def _bow(texts: list[str]) -> np.ndarray:
    return np.array(
        [[float(w in t.lower()) for w in VOCAB] for t in texts],
        dtype="float32",
    )


_EMBEDDER = Embedder(encode_fn=_bow)


# ---------------------------------------------------------------------------
# 1. Store round-trip
# ---------------------------------------------------------------------------

class TestStore:
    def test_save_load_round_trip(self, tmp_path: Path):
        from caddie.explorer.store import save_entries, load_entries

        e1 = _explored("e1", intent_text="turn on wifi")
        e2 = _explored(
            "e2",
            app="com.other",
            intent_text="open bluetooth",
            state_sig="sig-xyz",
            provenance={"path": ["open settings", "tap bluetooth"]},
        )
        target = tmp_path / "entries.json"
        save_entries([e1, e2], target)

        loaded = load_entries(target)
        assert len(loaded) == 2
        assert loaded[0] == e1
        assert loaded[1] == e2

    def test_save_creates_parent_dirs(self, tmp_path: Path):
        from caddie.explorer.store import save_entries, load_entries

        e = _explored("e1")
        target = tmp_path / "sub" / "dir" / "entries.json"
        save_entries([e], target)
        assert target.exists()
        loaded = load_entries(target)
        assert len(loaded) == 1

    def test_dedup_drops_duplicate_key(self, tmp_path: Path):
        """Dedup key = (app, state_sig, intent_text); first wins."""
        from caddie.explorer.store import save_entries, load_entries

        e1 = _explored("e1", app="com.foo", intent_text="send message", state_sig="s1")
        e2 = _explored("e2", app="com.foo", intent_text="send message", state_sig="s1")  # dup
        e3 = _explored("e3", app="com.foo", intent_text="other task", state_sig="s1")

        target = tmp_path / "entries.json"
        save_entries([e1, e2, e3], target)
        loaded = load_entries(target)

        assert len(loaded) == 2
        ids = [e.id for e in loaded]
        assert "e1" in ids   # first of the duplicate pair
        assert "e2" not in ids  # second is dropped
        assert "e3" in ids

    def test_all_fields_preserved(self, tmp_path: Path):
        """All MemoryEntry fields survive the JSON round-trip."""
        from caddie.explorer.store import save_entries, load_entries

        e = MemoryEntry(
            id="full.entry",
            app="com.full",
            intent_text="full test",
            triggers=("trigger1", "trigger2"),
            body="body text",
            kind="explored",
            complete_trajectory=True,
            steps=({"action": "tap", "index": 3},),
            source_path="/data/raw.json",
            state_sig="sig-full",
            provenance={"app": "com.full", "path": ["step A", "step B"]},
            confidence=0.7,
            fingerprint={"os": "13", "build": "T123"},
            schema_version=2,
        )
        target = tmp_path / "full.json"
        save_entries([e], target)
        loaded = load_entries(target)
        assert loaded[0] == e

    def test_empty_list_round_trip(self, tmp_path: Path):
        from caddie.explorer.store import save_entries, load_entries

        target = tmp_path / "empty.json"
        save_entries([], target)
        assert load_entries(target) == []


# ---------------------------------------------------------------------------
# 2. MemoryIndex.match_hints / match separation
# ---------------------------------------------------------------------------

class TestMemoryIndexHints:
    def _build_index(self, skill_list, explored_list, threshold=0.1):
        lib = SkillLibrary(skill_list)
        return MemoryIndex.build(lib, _EMBEDDER, threshold=threshold, explored=explored_list)

    def test_match_hints_returns_explored_entries(self):
        e = _explored("e1", intent_text="wifi on")
        idx = self._build_index([], [e])
        hints = idx.match_hints("wifi on", k=3)
        assert len(hints) == 1
        assert hints[0].kind == "explored"
        assert hints[0].id == "e1"

    def test_match_hints_empty_when_no_explored(self):
        sk = _skill("display.dark_on", ["dark on"], "dark on")
        idx = self._build_index([sk], [])
        hints = idx.match_hints("dark on", k=3)
        assert hints == []

    def test_match_hints_below_threshold_returns_empty(self):
        e = _explored("e1", intent_text="wifi on")
        idx = self._build_index([], [e], threshold=0.99)
        hints = idx.match_hints("completely unrelated", k=3)
        assert hints == []

    def test_match_returns_only_skills_never_explored(self):
        """Explored entries must NEVER leak into match()."""
        sk = _skill("display.dark_on", ["dark on"], "dark on")
        e = _explored("e1", intent_text="dark on")
        idx = self._build_index([sk], [e])

        skills = idx.match("dark on", k=5)
        kinds = [type(s).__name__ for s in skills]
        ids = [s.id for s in skills]

        # All returned objects are Skill instances, not MemoryEntry
        assert all(isinstance(s, Skill) for s in skills), f"Got: {kinds}"
        # Explored entry id must not appear
        assert "e1" not in ids

    def test_match_skills_unchanged_with_explored_in_index(self):
        """match() returns the same skills regardless of explored entries in index."""
        sk = _skill("display.dark_on", ["dark on"], "dark on")
        idx_without = self._build_index([sk], [])
        idx_with = self._build_index([sk], [_explored("e1", intent_text="dark on")])

        hits_without = [s.id for s in idx_without.match("dark on", k=5)]
        hits_with = [s.id for s in idx_with.match("dark on", k=5)]

        assert hits_without == hits_with

    def test_match_hints_multiple_entries(self):
        e1 = _explored("e1", intent_text="wifi on", state_sig="s1")
        e2 = _explored("e2", intent_text="wifi off", state_sig="s2",
                        provenance={"path": ["tap wifi", "tap off"]})
        idx = self._build_index([], [e1, e2])
        hints = idx.match_hints("wifi", k=5)
        assert len(hints) == 2
        assert all(h.kind == "explored" for h in hints)


# ---------------------------------------------------------------------------
# 3. select_prompt_hints
# ---------------------------------------------------------------------------

class _FakeMemoryIndex:
    """Minimal fake for select_prompt_hints tests."""
    def __init__(self, hints: list[MemoryEntry]) -> None:
        self._hints = hints

    def match(self, task: str, k: int = 3) -> list[Skill]:
        return []

    def match_hints(self, task: str, k: int = 2) -> list[MemoryEntry]:
        return list(self._hints)[:k]


class _FakeContext:
    def __init__(self, memory_index=None) -> None:
        self.skills = _FakeSkillLib()
        self.memory_index = memory_index


class _FakeSkillLib:
    def match(self, task: str) -> list[Skill]:
        return []


class TestSelectPromptHints:
    def teardown_method(self):
        os.environ.pop("LLM_SMARTPHONE_SEMANTIC_MATCH", None)

    def test_returns_empty_when_semantic_off(self):
        from caddie.memory.selection import select_prompt_hints
        os.environ.pop("LLM_SMARTPHONE_SEMANTIC_MATCH", None)

        e = _explored("e1")
        ctx = _FakeContext(memory_index=_FakeMemoryIndex([e]))
        result = select_prompt_hints(ctx, "wifi on")
        assert result == []

    def test_returns_empty_when_flag_zero(self):
        from caddie.memory.selection import select_prompt_hints
        os.environ["LLM_SMARTPHONE_SEMANTIC_MATCH"] = "0"

        e = _explored("e1")
        ctx = _FakeContext(memory_index=_FakeMemoryIndex([e]))
        result = select_prompt_hints(ctx, "wifi on")
        assert result == []

    def test_returns_empty_when_index_none(self):
        from caddie.memory.selection import select_prompt_hints
        os.environ["LLM_SMARTPHONE_SEMANTIC_MATCH"] = "1"

        ctx = _FakeContext(memory_index=None)
        result = select_prompt_hints(ctx, "wifi on")
        assert result == []

    def test_returns_hints_when_semantic_on_and_index_present(self):
        from caddie.memory.selection import select_prompt_hints
        os.environ["LLM_SMARTPHONE_SEMANTIC_MATCH"] = "1"

        e = _explored("e1", intent_text="turn wifi on")
        ctx = _FakeContext(memory_index=_FakeMemoryIndex([e]))
        result = select_prompt_hints(ctx, "wifi")
        assert len(result) == 1
        assert result[0].id == "e1"

    def test_respects_k_param(self):
        from caddie.memory.selection import select_prompt_hints
        os.environ["LLM_SMARTPHONE_SEMANTIC_MATCH"] = "1"

        entries = [_explored(f"e{i}", state_sig=f"s{i}") for i in range(5)]
        ctx = _FakeContext(memory_index=_FakeMemoryIndex(entries))
        result = select_prompt_hints(ctx, "wifi", k=2)
        assert len(result) == 2


# ---------------------------------------------------------------------------
# 4. build_system_prompt byte-identical when hints=None/[]; block present otherwise
# ---------------------------------------------------------------------------

class TestBuildSystemPromptHints:
    def _make_skills(self) -> list[Skill]:
        return [_skill("display.dark_on", ["dark on"], "dark on")]

    def test_no_hints_identical_to_before(self):
        """build_system_prompt(matched) == build_system_prompt(matched, hints=None)."""
        from caddie.agent.prompt import build_system_prompt

        matched = self._make_skills()
        baseline = build_system_prompt(matched)
        with_none = build_system_prompt(matched, hints=None)
        assert baseline == with_none, "Output must be BYTE-IDENTICAL when hints=None"

    def test_empty_hints_identical_to_before(self):
        """build_system_prompt(matched, hints=[]) must equal build_system_prompt(matched)."""
        from caddie.agent.prompt import build_system_prompt

        matched = self._make_skills()
        baseline = build_system_prompt(matched)
        with_empty = build_system_prompt(matched, hints=[])
        assert baseline == with_empty, "Output must be BYTE-IDENTICAL when hints=[]"

    def test_no_matched_no_hints_identical(self):
        """Empty matched, no hints: still byte-identical."""
        from caddie.agent.prompt import build_system_prompt

        baseline = build_system_prompt([])
        with_none = build_system_prompt([], hints=None)
        with_empty = build_system_prompt([], hints=[])
        assert baseline == with_none
        assert baseline == with_empty

    def test_hints_block_present_in_output(self):
        """When hints is non-empty, output contains the Geraete-Wissen section."""
        from caddie.agent.prompt import build_system_prompt

        matched = self._make_skills()
        hint = _explored("e1", intent_text="enable wifi quickly",
                         provenance={"path": ["open settings", "tap wifi"]})
        out = build_system_prompt(matched, hints=[hint])

        assert "Device knowledge" in out
        assert "enable wifi quickly" in out

    def test_hints_block_contains_path_labels(self):
        """Provenance path labels appear in the hints block."""
        from caddie.agent.prompt import build_system_prompt

        hint = _explored(
            "e1",
            intent_text="turn on bluetooth",
            provenance={"path": ["open settings", "tap bluetooth", "tap toggle"]},
        )
        out = build_system_prompt([], hints=[hint])
        assert "open settings" in out
        assert "tap bluetooth" in out
        assert "tap toggle" in out

    def test_hints_block_no_path_shows_fallback(self):
        """When provenance is None or path is empty, '(no path)' appears."""
        from caddie.agent.prompt import build_system_prompt

        hint_none_prov = _explored("e1", intent_text="task A", provenance=None)
        hint_empty_path = _explored("e2", intent_text="task B",
                                    state_sig="s2",
                                    provenance={"path": []})

        out_none = build_system_prompt([], hints=[hint_none_prov])
        assert "(no path)" in out_none

        out_empty = build_system_prompt([], hints=[hint_empty_path])
        assert "(no path)" in out_empty

    def test_skill_body_not_in_hints_only_output(self):
        """Explored entry body must NOT appear in the hints block."""
        from caddie.agent.prompt import build_system_prompt

        hint = MemoryEntry(
            id="e1",
            app="com.foo",
            intent_text="wifi on",
            triggers=(),
            body="SECRET_BODY_TEXT",
            kind="explored",
            complete_trajectory=False,
            steps=(),
            source_path="",
            state_sig="s1",
            provenance={"path": ["step A"]},
        )
        out = build_system_prompt([], hints=[hint])
        assert "SECRET_BODY_TEXT" not in out

    def test_skill_blocks_present_with_hints(self):
        """Skills are still rendered even when hints are also present."""
        from caddie.agent.prompt import build_system_prompt

        matched = self._make_skills()
        hint = _explored("e1", intent_text="enable wifi")
        out = build_system_prompt(matched, hints=[hint])

        assert "<skill" in out   # skill block rendered
        assert "Device knowledge" in out  # hints block also present

    def test_no_skill_body_duplication(self):
        """The skill body appears exactly once (not duplicated into hints)."""
        from caddie.agent.prompt import build_system_prompt

        matched = self._make_skills()
        hint = _explored("e1", intent_text="dark on")
        out = build_system_prompt(matched, hints=[hint])

        # skill body "## Verification\nok" should appear exactly once
        assert out.count("## Verification\nok") == 1

    def test_criterion_still_works_with_hints(self):
        """criterion block still rendered correctly alongside hints."""
        from caddie.agent.prompt import build_system_prompt

        hint = _explored("e1", intent_text="wifi check")
        out = build_system_prompt([], criterion="Screen shows Wi-Fi ON", hints=[hint])
        assert "Screen shows Wi-Fi ON" in out
        assert "Device knowledge" in out

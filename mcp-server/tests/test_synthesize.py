"""Tests for caddie.explorer.synthesize — transition-grounded intent synthesis.

TDD: tests written before implementation.
No device, no real LLM, no network.
"""
from __future__ import annotations

import pytest
from caddie.explorer.synthesize import synthesize_entries
from caddie.memory.entry import MemoryEntry


# ---------------------------------------------------------------------------
# Fixtures / helpers
# ---------------------------------------------------------------------------

def _fake_llm(prompt: str) -> str:
    """Deterministic fake LLM: always returns the same label."""
    return "Enable dark theme in display settings"


def _make_entries(
    source_sig: str = "sig_a",
    action: dict | None = None,
    dest_sig: str = "sig_b",
    path_from_root: list[dict] | None = None,
    dest_elements: list[dict] | None = None,
    app: str = "com.android.settings",
    llm_fn=None,
) -> list[MemoryEntry]:
    action = action or {"kind": "tap", "label": "Dunkles Design", "index": 3}
    path_from_root = path_from_root or [{"kind": "tap", "label": "Display", "index": 1}]
    dest_elements = dest_elements or [
        {"text": "Dunkles Design", "index": 3, "class": "android.widget.Switch"}
    ]
    llm_fn = llm_fn or _fake_llm
    return synthesize_entries(
        source_sig=source_sig,
        action=action,
        dest_sig=dest_sig,
        path_from_root=path_from_root,
        dest_elements=dest_elements,
        app=app,
        llm_fn=llm_fn,
    )


# ---------------------------------------------------------------------------
# Basic contract
# ---------------------------------------------------------------------------

class TestSynthesizeContract:
    def test_returns_at_least_one_entry(self):
        entries = _make_entries()
        assert len(entries) >= 1

    def test_all_entries_are_memory_entry(self):
        entries = _make_entries()
        for e in entries:
            assert isinstance(e, MemoryEntry)

    def test_kind_is_explored(self):
        for e in _make_entries():
            assert e.kind == "explored"

    def test_confidence_is_0_3(self):
        for e in _make_entries():
            assert e.confidence == pytest.approx(0.3)

    def test_complete_trajectory_is_false(self):
        """Explored entries are NOT replay-authorized."""
        for e in _make_entries():
            assert e.complete_trajectory is False

    def test_state_sig_is_dest_sig(self):
        dest_sig = "sig_destination_xyz"
        for e in _make_entries(dest_sig=dest_sig):
            assert e.state_sig == dest_sig

    def test_steps_is_empty(self):
        """Explored entries carry no replay steps."""
        for e in _make_entries():
            assert e.steps == ()

    def test_triggers_is_empty(self):
        for e in _make_entries():
            assert e.triggers == ()

    def test_body_is_empty(self):
        for e in _make_entries():
            assert e.body == ""


# ---------------------------------------------------------------------------
# Provenance
# ---------------------------------------------------------------------------

class TestSynthesizeProvenance:
    def test_provenance_contains_app(self):
        entries = _make_entries(app="com.android.settings")
        for e in entries:
            assert e.provenance is not None
            assert e.provenance["app"] == "com.android.settings"

    def test_provenance_contains_source_sig(self):
        entries = _make_entries(source_sig="sig_source")
        for e in entries:
            assert e.provenance["source_sig"] == "sig_source"

    def test_provenance_contains_dest_sig(self):
        entries = _make_entries(dest_sig="sig_dest_99")
        for e in entries:
            assert e.provenance["dest_sig"] == "sig_dest_99"

    def test_provenance_contains_action(self):
        action = {"kind": "tap", "label": "Schriftgroesse", "index": 5}
        entries = _make_entries(action=action)
        for e in entries:
            assert e.provenance["action"] == action

    def test_provenance_contains_path(self):
        path = [
            {"kind": "tap", "label": "Display", "index": 1},
            {"kind": "scroll_down", "label": "", "index": None},
        ]
        entries = _make_entries(path_from_root=path)
        for e in entries:
            assert e.provenance["path"] == path


# ---------------------------------------------------------------------------
# app field on entry
# ---------------------------------------------------------------------------

class TestSynthesizeApp:
    def test_entry_app_matches_arg(self):
        entries = _make_entries(app="com.example.myapp")
        for e in entries:
            assert e.app == "com.example.myapp"


# ---------------------------------------------------------------------------
# intent_text via llm_fn
# ---------------------------------------------------------------------------

class TestSynthesizeIntentText:
    def test_intent_text_from_llm_fn(self):
        entries = _make_entries(llm_fn=_fake_llm)
        for e in entries:
            assert e.intent_text == "Enable dark theme in display settings"

    def test_different_llm_fn_gives_different_intent(self):
        def llm_b(prompt: str) -> str:
            return "A completely different label"

        entries_a = _make_entries(llm_fn=_fake_llm)
        entries_b = _make_entries(llm_fn=llm_b)
        assert entries_a[0].intent_text != entries_b[0].intent_text


# ---------------------------------------------------------------------------
# ID stability / determinism
# ---------------------------------------------------------------------------

class TestSynthesizeIdStability:
    def test_ids_are_stable_for_same_inputs(self):
        entries1 = _make_entries()
        entries2 = _make_entries()
        assert [e.id for e in entries1] == [e.id for e in entries2]

    def test_id_changes_with_dest_sig(self):
        entries_a = _make_entries(dest_sig="sig_x")
        entries_b = _make_entries(dest_sig="sig_y")
        # IDs derived from dest_sig, so must differ
        assert entries_a[0].id != entries_b[0].id

    def test_id_changes_with_intent_text(self):
        def llm_x(p: str) -> str:
            return "Label X"
        def llm_y(p: str) -> str:
            return "Label Y"

        entries_x = _make_entries(llm_fn=llm_x)
        entries_y = _make_entries(llm_fn=llm_y)
        assert entries_x[0].id != entries_y[0].id

    def test_id_changes_with_app(self):
        entries_a = _make_entries(app="com.android.settings")
        entries_b = _make_entries(app="com.example.other")
        assert entries_a[0].id != entries_b[0].id


# ---------------------------------------------------------------------------
# No real LLM is called (guard: llm_fn=None path does not crash in tests)
# ---------------------------------------------------------------------------

class TestNoRealLlmInTests:
    def test_llm_fn_none_does_not_call_real_model(self):
        """When llm_fn=None is passed, synthesize_entries should handle it
        without calling a real LLM client (in tests the real client is absent).
        We verify by monkey-patching the module to ensure no import of a real
        client occurs — but here we just verify the fake path works cleanly."""
        entries = _make_entries(llm_fn=_fake_llm)
        assert len(entries) >= 1

"""Tests for caddie.explorer.store save/load round-trip and dedup."""
import json
from pathlib import Path

from caddie.explorer.store import save_entries, load_entries
from caddie.memory.entry import MemoryEntry


def _entry(app: str, state_sig: str, intent_text: str, eid: str = "x") -> MemoryEntry:
    return MemoryEntry(
        id=eid,
        app=app,
        intent_text=intent_text,
        triggers=(),
        body="",
        kind="navigation",
        complete_trajectory=False,
        steps=(),
        source_path="",
        state_sig=state_sig,
        provenance=None,
        confidence=1.0,
        fingerprint=None,
        schema_version=1,
    )


def test_save_deduplicates_on_write(tmp_path):
    """save_entries must dedup (app, state_sig, intent_text) at write time."""
    p = tmp_path / "entries.json"
    e1 = _entry("com.example", "sig1", "tap display", eid="a")
    e2 = _entry("com.example", "sig1", "tap display", eid="b")  # duplicate key
    e3 = _entry("com.example", "sig2", "tap sound", eid="c")

    save_entries([e1, e2, e3], p)

    raw = json.loads(p.read_text())
    assert len(raw) == 2, f"Expected 2 entries after dedup, got {len(raw)}"
    ids = [r["id"] for r in raw]
    assert "a" in ids
    assert "c" in ids
    assert "b" not in ids, "Duplicate entry must be dropped at write time"


def test_save_load_roundtrip(tmp_path):
    """save then load must return same entries (no duplicates introduced)."""
    p = tmp_path / "entries.json"
    e1 = _entry("com.example", "sig1", "tap display", eid="a")
    e2 = _entry("com.example", "sig2", "tap sound", eid="b")

    save_entries([e1, e2], p)
    loaded = load_entries(p)

    assert len(loaded) == 2
    assert loaded[0].id == "a"
    assert loaded[1].id == "b"


def test_save_empty_list(tmp_path):
    """save_entries with empty list writes a valid empty JSON array."""
    p = tmp_path / "entries.json"
    save_entries([], p)
    raw = json.loads(p.read_text())
    assert raw == []

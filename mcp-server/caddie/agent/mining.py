"""Auto-mine successful runs into demonstration HINTS.

The agent already accumulates ``recorded_steps`` (rich element identity) during a
run, but only persists them if it voluntarily calls save_skill -- which is
unreliable (fired 0x on real successes). This module turns a successful run's
steps into a task->path demonstration that is injected as a HINT (kind=explored,
never auto-replayed -- same safety stance as crawl knowledge), so the next
attempt at the same/similar task gets the exact route instead of re-discovering
it (and fumbling Settings-search with the wrong terminology).
"""
from __future__ import annotations

import hashlib
from pathlib import Path

from caddie.explorer.store import load_entries, save_entries
from caddie.memory.entry import MemoryEntry


def _path_labels(steps: list[dict] | None) -> list[str]:
    """Ordered human labels of the labeled tap steps (skips open_app / tap_xy /
    unlabeled widgets -- those teach nothing reusable)."""
    out: list[str] = []
    for s in steps or []:
        if not isinstance(s, dict):
            continue
        lbl = (s.get("label") or "").strip()
        if lbl:
            out.append(lbl)
    return out


def build_demonstration(task: str, steps: list[dict] | None) -> MemoryEntry | None:
    """Build a demonstration hint from a successful run, or None if there is
    nothing reusable to teach (blank task or no labeled path)."""
    task = (task or "").strip()
    if not task:
        return None
    path = _path_labels(steps)
    if not path:
        return None
    sid = "demo_" + hashlib.sha1(task.lower().encode("utf-8")).hexdigest()[:12]
    return MemoryEntry(
        id=sid,
        app="",
        intent_text=task,
        triggers=(),
        body="",
        kind="explored",            # hint only -> goes through match_hints, never replay
        complete_trajectory=False,  # NOT replay-authorized
        steps=(),
        source_path="",
        state_sig="",
        provenance={"path": path, "source": "demonstration"},
        confidence=0.5,             # a real success; > crawl's 0.3
        fingerprint=None,
        schema_version=1,
    )


def append_demonstration(entry: MemoryEntry, store_path: Path) -> None:
    """Append *entry* to the demonstration store, deduped by task (save_entries
    dedups on app+state_sig+intent_text; demos have empty app/state_sig)."""
    prior = []
    if store_path.exists():
        try:
            prior = load_entries(store_path)
        except Exception:
            prior = []
    save_entries(list(prior) + [entry], store_path)

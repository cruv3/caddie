"""Persist and reload explored MemoryEntry objects (Phase 1c, Task 5).

Contract:
  save_entries(entries, path) -> None   -- JSON, utf-8, all fields
  load_entries(path)          -> list[MemoryEntry]
  Dedup key on load: (app, state_sig, intent_text) -- keep first occurrence.
"""
from __future__ import annotations

import json
from pathlib import Path

from caddie.memory.entry import MemoryEntry


def save_entries(entries: list[MemoryEntry], path: Path) -> None:
    """Serialise *entries* to *path* as a UTF-8 JSON array (all fields).

    Deduplication is applied at write time on (app, state_sig, intent_text);
    the first occurrence of each key wins and later duplicates are dropped.
    This mirrors the dedup logic in load_entries() so the on-disk file is
    always already in canonical form.
    """
    def _serialise(e: MemoryEntry) -> dict:
        return {
            "id": e.id,
            "app": e.app,
            "intent_text": e.intent_text,
            "triggers": list(e.triggers),
            "body": e.body,
            "kind": e.kind,
            "complete_trajectory": e.complete_trajectory,
            "steps": [dict(s) for s in e.steps],
            "source_path": e.source_path,
            "state_sig": e.state_sig,
            "provenance": e.provenance,
            "confidence": e.confidence,
            "fingerprint": e.fingerprint,
            "schema_version": e.schema_version,
            "intent_aliases": list(e.intent_aliases),
        }

    # Dedup on (app, state_sig, intent_text) -- first occurrence wins
    seen: set[tuple[str, str, str]] = set()
    deduped: list[MemoryEntry] = []
    for e in entries:
        key = (e.app, e.state_sig, e.intent_text)
        if key not in seen:
            seen.add(key)
            deduped.append(e)

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps([_serialise(e) for e in deduped], ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def load_entries(path: Path) -> list[MemoryEntry]:
    """Load entries from *path*, deduplicating on (app, state_sig, intent_text).

    The first occurrence wins; later duplicates are silently dropped.
    """
    raw: list[dict] = json.loads(path.read_text(encoding="utf-8"))
    seen: set[tuple[str, str, str]] = set()
    out: list[MemoryEntry] = []
    for d in raw:
        key = (d.get("app", ""), d.get("state_sig", ""), d.get("intent_text", ""))
        if key in seen:
            continue
        seen.add(key)
        out.append(
            MemoryEntry(
                id=d["id"],
                app=d["app"],
                intent_text=d["intent_text"],
                triggers=tuple(d.get("triggers", [])),
                body=d.get("body", ""),
                kind=d["kind"],
                complete_trajectory=d.get("complete_trajectory", False),
                steps=tuple(d.get("steps", [])),
                source_path=d.get("source_path", ""),
                state_sig=d.get("state_sig", ""),
                provenance=d.get("provenance"),
                confidence=d.get("confidence", 1.0),
                fingerprint=d.get("fingerprint"),
                schema_version=d.get("schema_version", 1),
                intent_aliases=tuple(d.get("intent_aliases", [])),
            )
        )
    return out

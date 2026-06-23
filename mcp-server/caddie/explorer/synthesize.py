"""Transition-grounded intent synthesis for the Caddie explorer.

Produces MemoryEntry objects of kind="explored" that describe what a UI
transition achieves.  These entries are NOT replay-authorized:
  complete_trajectory=False, steps=(), confidence=0.3.

The intent_text is produced by llm_fn(prompt) -> str, which is injected by
the caller.  In tests a deterministic fake is passed; in production the real
LLM client is injected by the explorer orchestrator.  This module NEVER
imports or calls a real LLM client directly.
"""
from __future__ import annotations

import hashlib
import json
from typing import Callable, Optional

from caddie.memory.entry import MemoryEntry


def _stable_id(app: str, dest_sig: str, intent_text: str) -> str:
    """Derive a stable, collision-resistant ID from the three input dimensions."""
    raw = json.dumps({"app": app, "dest_sig": dest_sig, "intent": intent_text},
                     ensure_ascii=True, sort_keys=True)
    digest = hashlib.sha256(raw.encode()).hexdigest()[:24]
    return f"explored.{digest}"


def _build_prompt(
    source_sig: str,
    action: dict,
    dest_sig: str,
    path_from_root: list[dict],
    dest_elements: list[dict],
    app: str,
) -> str:
    """Build the LLM prompt for intent synthesis."""
    action_label = action.get("label") or action.get("kind", "")
    element_labels = [
        el.get("text") or el.get("content_description") or ""
        for el in dest_elements
    ]
    element_labels = [l for l in element_labels if l][:10]  # cap to keep prompt short

    path_summary = " -> ".join(
        a.get("label") or a.get("kind", "?") for a in path_from_root
    )

    return (
        f"App: {app}\n"
        f"Navigation path: {path_summary}\n"
        f"Last action: {action_label!r} on state {source_sig!r}\n"
        f"Resulting screen elements: {element_labels}\n"
        "In one short sentence, what can a user accomplish on this screen?"
    )


def synthesize_entries(
    source_sig: str,
    action: dict,
    dest_sig: str,
    path_from_root: list[dict],
    dest_elements: list[dict],
    app: str,
    llm_fn: Optional[Callable[[str], str]] = None,
) -> list[MemoryEntry]:
    """Produce explored MemoryEntry objects describing this UI transition.

    Parameters
    ----------
    source_sig:      State signature of the screen we came from.
    action:          The action dict that caused the transition.
    dest_sig:        State signature of the resulting screen.
    path_from_root:  List of action dicts from the root state to source_sig.
    dest_elements:   UI elements visible on the destination screen.
    app:             Package name of the focused app.
    llm_fn:          Callable(prompt: str) -> str.  MUST be supplied by caller;
                     this module never imports a real LLM client.  Pass a fake
                     in tests.

    Returns
    -------
    list[MemoryEntry]  — at least one entry.
    """
    if llm_fn is None:
        # Fallback: emit a placeholder intent without calling a real model.
        # This path exists so the function remains callable even without a
        # model client, but in tests always pass a fake.
        intent_text = f"Explored transition via {action.get('label') or action.get('kind', 'action')} to {dest_sig}"
    else:
        prompt = _build_prompt(source_sig, action, dest_sig, path_from_root, dest_elements, app)
        intent_text = llm_fn(prompt)

    entry_id = _stable_id(app, dest_sig, intent_text)

    provenance: dict = {
        "app": app,
        "source_sig": source_sig,
        "action": action,
        "dest_sig": dest_sig,
        "path": path_from_root,
    }

    entry = MemoryEntry(
        id=entry_id,
        app=app,
        intent_text=intent_text,
        triggers=(),
        body="",
        kind="explored",
        complete_trajectory=False,
        steps=(),
        source_path="",
        state_sig=dest_sig,
        provenance=provenance,
        confidence=0.3,
    )

    return [entry]

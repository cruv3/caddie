"""Two RAG hint bugs (found via Codex review of the harder-task eval):
1. mined demo path dropped the app-entry step -> hint omitted "open Settings".
2. match_hints retrieved a global top-k then filtered explored -> skill entries
   could crowd the explored hint out of the top-k.
"""
import numpy as np
from pathlib import Path
from caddie.agent.mining import _path_labels
from caddie.skills import SkillLibrary, Skill
from caddie.memory import MemoryIndex
from caddie.memory.embedder import Embedder
from caddie.memory.entry import MemoryEntry


def test_path_labels_includes_open_app_entry():
    steps = [
        {"action": "open_app", "package": "com.android.settings"},
        {"action": "tap", "label": "Battery"},
        {"action": "tap_xy", "x": 1, "y": 2},          # coordinate tap: still skipped
        {"action": "tap", "label": "Battery Saver"},
    ]
    assert _path_labels(steps) == ["open settings app", "Battery", "Battery Saver"]


def test_path_labels_open_app_without_package_skipped():
    assert _path_labels([{"action": "open_app", "package": ""}]) == []


VOCAB = ["dark", "theme", "mode", "view", "battery", "saver"]


def _bow(texts):
    return np.array([[float(w in t.lower()) for w in VOCAB] for t in texts], dtype="float32")


def _skill(sid, trig, title):
    return Skill(id=sid, title=title, description="", triggers=tuple(trig),
                 body="## Verification\nx", path=Path(f"skills/{sid}.md"), steps=())


def _explored(eid, intent):
    return MemoryEntry(id=eid, app="", intent_text=intent, triggers=(), body="",
                       kind="explored", complete_trajectory=False, steps=(), source_path="")


def test_match_hints_not_crowded_out_by_skills():
    # two skills outrank the explored entry for the task; with a global top-2 the
    # explored hint was dropped. It must still be returned.
    lib = SkillLibrary([_skill("s1", ["dark theme"], "dark theme"),
                        _skill("s2", ["dark theme view"], "dark theme view")])
    explored = [_explored("e1", "dark mode")]
    idx = MemoryIndex.build(lib, Embedder(encode_fn=_bow), threshold=0.1, explored=explored)
    hints = idx.match_hints("dark theme", k=2)
    assert any(h.id == "e1" for h in hints), "explored hint crowded out by skills"

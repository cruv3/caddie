import numpy as np
from pathlib import Path
from caddie.skills import SkillLibrary, Skill
from caddie.memory import MemoryIndex
from caddie.memory.embedder import Embedder


def _skill(sid, triggers, title):
    return Skill(id=sid, title=title, description="", triggers=tuple(triggers),
                 body="## Verification\nx", path=Path(f"skills/{sid}.md"), steps=())


VOCAB = ["dark", "bluetooth", "on", "off", "bright"]
def _bow(texts):
    return np.array([[float(w in t.lower()) for w in VOCAB] for t in texts], dtype="float32")


def test_match_returns_skill_objects_ranked():
    lib = SkillLibrary([_skill("display.dark_on", ["dark on"], "dark on"),
                        _skill("conn.bt_on", ["bluetooth on"], "bluetooth on")])
    idx = MemoryIndex.build(lib, Embedder(encode_fn=_bow), threshold=0.1)
    hits = idx.match("dark on", k=3)
    assert hits and isinstance(hits[0], Skill)
    assert hits[0].id == "display.dark_on"


def test_match_empty_below_threshold():
    lib = SkillLibrary([_skill("display.dark_on", ["dark on"], "dark on")])
    idx = MemoryIndex.build(lib, Embedder(encode_fn=_bow), threshold=0.5)
    assert idx.match("wetter morgen", k=3) == []


def test_unresolvable_ids_skipped():
    lib = SkillLibrary([_skill("display.dark_on", ["dark on"], "dark on")])
    idx = MemoryIndex.build(lib, Embedder(encode_fn=_bow), threshold=0.1)
    # nach Index-Bau Skill aus der Lib entfernen -> id nicht mehr auflösbar
    idx._library = SkillLibrary([])
    assert idx.match("dark on", k=3) == []

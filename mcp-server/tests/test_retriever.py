import numpy as np
from caddie.memory import MemoryEntry, SemanticRetriever
from caddie.memory.embedder import Embedder


def _entry(eid, intent):
    return MemoryEntry(id=eid, app="x", intent_text=intent, triggers=(), body="",
                       kind="authored", complete_trajectory=False, steps=(),
                       source_path="")


# Fake-Embedder: bag-of-words über ein festes Vokabular -> deterministische Vektoren
VOCAB = ["dark", "mode", "bright", "bluetooth", "on", "off"]
def _bow(texts):
    out = []
    for t in texts:
        tl = t.lower()
        out.append([float(w in tl) for w in VOCAB])
    return np.array(out, dtype="float32")


def _retriever(entries, threshold=0.45):
    return SemanticRetriever(entries, Embedder(encode_fn=_bow), threshold=threshold)


def test_match_returns_best_entry_above_threshold():
    entries = [_entry("dark", "dark mode on"), _entry("bt", "bluetooth on")]
    r = _retriever(entries)
    hits = r.match("dark mode", k=3)
    assert hits[0][0].id == "dark"
    assert hits[0][1] >= 0.45


def test_negative_query_returns_empty():
    entries = [_entry("dark", "dark mode on"), _entry("bt", "bluetooth on")]
    r = _retriever(entries, threshold=0.45)
    # query ohne Vokabular-Überlappung -> Cosine 0 -> gegated zu leer
    assert r.match("wetter morgen regen", k=3) == []


def test_respects_k_limit():
    entries = [_entry("a", "dark mode on"), _entry("b", "dark mode off"),
               _entry("c", "dark on")]
    r = _retriever(entries, threshold=0.0)
    assert len(r.match("dark", k=2)) == 2

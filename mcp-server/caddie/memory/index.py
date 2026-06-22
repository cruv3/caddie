from __future__ import annotations

from caddie.memory.embedder import Embedder
from caddie.memory.entry import MemoryEntry
from caddie.memory.retriever import SemanticRetriever


class MemoryIndex:
    """Semantic skill matcher: drop-in for SkillLibrary.match, returning Skills
    ranked by retrieval similarity (gated by threshold)."""

    def __init__(self, library, retriever: SemanticRetriever) -> None:
        self._library = library
        self._retriever = retriever

    @classmethod
    def build(cls, library, embedder: Embedder, threshold: float = 0.55) -> "MemoryIndex":
        entries = [MemoryEntry.from_skill(s) for s in library.all()]
        retriever = SemanticRetriever(entries, embedder, threshold=threshold)
        return cls(library, retriever)

    def match(self, task: str, k: int = 3) -> list:
        hits = self._retriever.match(task, k=k)
        out = []
        for entry, _score in hits:
            skill = self._library.get(entry.id)
            if skill is not None:
                out.append(skill)
        return out

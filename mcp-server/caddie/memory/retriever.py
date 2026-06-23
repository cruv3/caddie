from __future__ import annotations

import numpy as np

from caddie.memory.embedder import Embedder
from caddie.memory.entry import MemoryEntry


class SemanticRetriever:
    def __init__(self, entries: list[MemoryEntry], embedder: Embedder,
                 threshold: float = 0.45) -> None:
        self._entries = list(entries)
        self._embedder = embedder
        self._threshold = threshold
        if self._entries:
            # retrieval_text() = intent_text (+ aliases, e.g. a German paraphrase)
            # so cross-lingual queries match; skills have no aliases -> unchanged.
            self._index = embedder.encode([e.retrieval_text() for e in self._entries])
        else:
            self._index = np.zeros((0, 1), dtype="float32")

    def match(self, task: str, k: int = 3) -> list[tuple[MemoryEntry, float]]:
        if not self._entries or not task:
            return []
        q = self._embedder.encode([task])[0]              # normalized
        scores = self._index @ q                           # cosine (both normalized)
        order = np.argsort(scores)[::-1]
        out: list[tuple[MemoryEntry, float]] = []
        for i in order[:k]:
            s = float(scores[i])
            if s >= self._threshold:
                out.append((self._entries[i], s))
        return out

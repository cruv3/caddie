from __future__ import annotations

from caddie.memory.embedder import Embedder
from caddie.memory.entry import MemoryEntry
from caddie.memory.retriever import SemanticRetriever

# Kinds that are "skill-like" (authored or recorded) — these are the only ones
# that match() is allowed to return.  "explored" entries must NEVER reach match().
_SKILL_KINDS = frozenset({"authored", "recorded"})


class MemoryIndex:
    """Semantic skill matcher: drop-in for SkillLibrary.match, returning Skills
    ranked by retrieval similarity (gated by threshold).

    Extended (Phase 1c / Task 5) to also index explored entries so that
    match_hints() can return them as raw MemoryEntry objects WITHOUT ever
    leaking them through match() which is Skills-only.
    """

    def __init__(self, library, retriever: SemanticRetriever) -> None:
        self._library = library
        self._retriever = retriever

    @classmethod
    def build(
        cls,
        library,
        embedder: Embedder,
        threshold: float = 0.55,
        explored: list[MemoryEntry] | None = None,
    ) -> "MemoryIndex":
        """Build the index from *library* skills plus optional *explored* entries.

        Skill entries (kind in {authored, recorded}) go into the retriever
        alongside explored entries.  The kind tag is preserved on each
        MemoryEntry so match() and match_hints() can split them cleanly.
        """
        skill_entries = [MemoryEntry.from_skill(s) for s in library.all()]
        extra: list[MemoryEntry] = list(explored) if explored else []
        all_entries = skill_entries + extra
        retriever = SemanticRetriever(all_entries, embedder, threshold=threshold)
        return cls(library, retriever)

    def match(self, task: str, k: int = 3) -> list:
        """Return Skills (Skill objects) ranked by similarity.

        Explored entries (kind == "explored") are NEVER returned here;
        they are silently skipped so they can never reach the skill= / replay path.
        """
        hits = self._retriever.match(task, k=k)
        out = []
        for entry, _score in hits:
            if entry.kind not in _SKILL_KINDS:
                continue  # explored (or unknown) entries must not appear here
            skill = self._library.get(entry.id)
            if skill is not None:
                out.append(skill)
        return out

    def match_hints(self, task: str, k: int = 2) -> list[MemoryEntry]:
        """Return raw explored MemoryEntry objects ranked by similarity.

        Only entries with kind == "explored" are considered.
        Gated by the retriever's threshold (same as match()).

        Retrieve a LARGER pool then filter to explored, so a couple of skill hits
        (a different kind) can't crowd explored entries out of the top-k.
        """
        hits = self._retriever.match(task, k=max(k * 5, 10))
        explored = [entry for entry, _score in hits if entry.kind == "explored"]
        return explored[:k]

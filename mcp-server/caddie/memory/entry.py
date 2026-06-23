from __future__ import annotations

from dataclasses import dataclass

from caddie.skills import Skill


@dataclass(frozen=True)
class MemoryEntry:
    id: str
    app: str
    intent_text: str          # text used for embedding/retrieval
    triggers: tuple[str, ...]
    body: str                 # preserved skill semantics (rules/verification/...)
    kind: str                 # "recorded" | "authored" | "explored"
    complete_trajectory: bool
    steps: tuple[dict, ...]
    source_path: str
    # Explored-knowledge fields (Phase 1c), all with defaults for backward-compat
    state_sig: str = ""
    provenance: dict | None = None      # shape: {"app","source_sig","action","dest_sig","path":[action,...]}
    confidence: float = 1.0             # skills keep 1.0; explored entries pass 0.3
    fingerprint: dict | None = None     # shape: {"os","build","locale"}
    schema_version: int = 1
    # Extra retrieval aliases (e.g. a German paraphrase of an English intent) so
    # cross-lingual user queries still match. The canonical intent_text stays
    # English (English-first); aliases only widen RETRIEVAL, never the hint shown.
    intent_aliases: tuple[str, ...] = ()

    def retrieval_text(self) -> str:
        """Text used for embedding/retrieval: intent plus any aliases. Skills have
        no aliases, so this equals intent_text (back-compat)."""
        if self.intent_aliases:
            return self.intent_text + " | " + " | ".join(self.intent_aliases)
        return self.intent_text

    @classmethod
    def from_skill(cls, skill: Skill, app: str = "") -> "MemoryEntry":
        has_steps = bool(getattr(skill, "steps", ()))
        intent_text = " | ".join(
            p for p in (skill.title, skill.description, ", ".join(skill.triggers)) if p
        )
        return cls(
            id=skill.id,
            app=app,
            intent_text=intent_text,
            triggers=tuple(skill.triggers),
            body=skill.body,
            kind="recorded" if has_steps else "authored",
            complete_trajectory=has_steps,
            steps=tuple(getattr(skill, "steps", ()) or ()),
            source_path=str(skill.path),
        )

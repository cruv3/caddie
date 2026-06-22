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

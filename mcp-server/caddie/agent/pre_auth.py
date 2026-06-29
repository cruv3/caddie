"""One-shot pre-authorization for an unattended (scheduled) run: authorizes
exactly ONE consequential action, then is consumed."""
from __future__ import annotations

from dataclasses import dataclass


@dataclass
class PreAuth:
    description: str
    consumed: bool = False

    def available(self) -> bool:
        return not self.consumed

    def consume(self) -> None:
        self.consumed = True

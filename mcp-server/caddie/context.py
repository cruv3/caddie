from __future__ import annotations

from dataclasses import dataclass, field
import os
from pathlib import Path

from caddie.agent.event_bus import EVENT_BUS, EventBus
from caddie.android.adb import AdbBridge
from caddie.android.backends.http import HttpBridge
from caddie.config import (
    DEFAULT_BACKEND,
    DEFAULT_HTTP_BRIDGE_URL,
    ENV_BACKEND,
    ENV_HTTP_BRIDGE_URL,
)
from caddie.skills import SkillLibrary


@dataclass
class ServerContext:
    project_dir: Path = field(default_factory=lambda: Path(__file__).resolve().parents[1])
    adb: AdbBridge = field(default_factory=AdbBridge)
    backend: object = field(init=False)
    skills: SkillLibrary = field(init=False)
    memory_index: object = field(default=None)  # MemoryIndex | None — set in __post_init__
    read_skill_ids: set[str] = field(default_factory=set)
    events: EventBus = field(default_factory=lambda: EVENT_BUS)

    def __post_init__(self) -> None:
        self.skills = SkillLibrary.load(self.project_dir / "skills")
        backend_name = os.environ.get(ENV_BACKEND, DEFAULT_BACKEND).strip().lower()
        if backend_name == "http":
            base_url = os.environ.get(ENV_HTTP_BRIDGE_URL, DEFAULT_HTTP_BRIDGE_URL)
            self.backend = HttpBridge(base_url=base_url)
        else:
            self.backend = self.adb
        self._build_memory_index()

    def _build_memory_index(self) -> None:
        """Build the MemoryIndex when the semantic flag is on; else leave as None.

        Failure is non-fatal: logs a warning and leaves memory_index = None so
        the server continues to work with trigger matching.
        """
        from caddie.memory.selection import _semantic_on
        if not _semantic_on():
            self.memory_index = None
            return
        try:
            from caddie.memory import Embedder, MemoryIndex
            self.memory_index = MemoryIndex.build(self.skills, Embedder(), threshold=0.55)
        except Exception as exc:
            import logging
            logging.getLogger(__name__).warning(
                "MemoryIndex build failed — falling back to trigger matching: %s", exc
            )
            self.memory_index = None

    @property
    def screenshot_dir(self) -> Path:
        path = self.project_dir / "screenshots"
        path.mkdir(parents=True, exist_ok=True)
        return path

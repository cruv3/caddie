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
    schedule_store: object = field(init=False)
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
        from caddie.agent.schedule_store import ScheduleStore
        self.schedule_store = ScheduleStore(self.project_dir / "scheduled_tasks.json")

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
            import os
            from caddie.memory import Embedder, MemoryIndex
            # Explored knowledge (Phase 1c) is loaded into the index ONLY when
            # LLM_SMARTPHONE_EXPLORED_HINTS=1 and a store path is given. Keeping it
            # behind its own flag lets the eval toggle the knowledge contribution
            # while holding semantic skill-matching constant (incremental A/B).
            explored = None
            if os.environ.get("LLM_SMARTPHONE_EXPLORED_HINTS", "0") == "1":
                store = os.environ.get("LLM_SMARTPHONE_EXPLORED_STORE", "")
                if store:
                    from pathlib import Path
                    from caddie.explorer.store import load_entries
                    p = Path(store)
                    if p.exists():
                        explored = load_entries(p)
            self.memory_index = MemoryIndex.build(
                self.skills, Embedder(), threshold=0.42, explored=explored)
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

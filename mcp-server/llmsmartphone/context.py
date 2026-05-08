from dataclasses import dataclass, field
import os
from pathlib import Path

from llmsmartphone.agent.event_bus import EVENT_BUS, EventBus
from llmsmartphone.android.adb import AdbBridge
from llmsmartphone.android.backends.http import HttpBridge
from llmsmartphone.config import (
    DEFAULT_BACKEND,
    DEFAULT_HTTP_BRIDGE_URL,
    ENV_BACKEND,
    ENV_HTTP_BRIDGE_URL,
)
from llmsmartphone.skills import SkillLibrary


@dataclass
class ServerContext:
    project_dir: Path = field(default_factory=lambda: Path(__file__).resolve().parents[1])
    adb: AdbBridge = field(default_factory=AdbBridge)
    backend: object = field(init=False)
    skills: SkillLibrary = field(init=False)
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

    @property
    def screenshot_dir(self) -> Path:
        path = self.project_dir / "screenshots"
        path.mkdir(parents=True, exist_ok=True)
        return path

import pytest
from caddie.skills import Skill
from pathlib import Path


@pytest.fixture
def dark_on_skill():
    return Skill(
        id="display.dark_mode_on_settings",
        title="Turn on dark mode via Settings",
        description="Opens Settings, Display & touch, toggles Dark theme on.",
        triggers=("schalte darkmodus an", "aktiviere dunkles design", "dark mode an"),
        body="## Verification\nDark theme toggle is ON.",
        path=Path("skills/display/dark_mode_on_settings.md"),
        steps=({"action": "open_app", "package": "com.android.settings"},),
    )

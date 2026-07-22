from types import SimpleNamespace

import pytest

from caddie.agent.event_bus import EventBus
from caddie.study.executor import execute_trial
from caddie.study.logger import StudyLogger
from caddie.study.model import (
    CriticalityClass,
    StepType,
    StudyCondition,
    StudyStep,
    TrialOutcome,
    TrialSpec,
)
from caddie.study.packages import STUDY_PACKAGES
from caddie.study.oversight import OversightDecision
from caddie.tools.apps import register_app_tools


class CapturingMcp:
    def __init__(self):
        self.tools = {}

    def tool(self):
        def register(function):
            self.tools[function.__name__] = function
            return function

        return register


class RecordingBackend:
    def __init__(self):
        self.calls = []

    def list_apps(self, include_system=False):
        self.calls.append(("list_apps", include_system))
        return [
            "com.example.notes",
            "com.caddie.studynotes",
            "com.example.calendar",
            "com.caddie.studycalendar",
        ]

    def open_app(self, package_name):
        self.calls.append(("open_app", package_name))
        return {"success": True}

    def list_elements(self):
        self.calls.append(("list_elements",))
        return {"elements": []}

    def open_url(self, url):
        self.calls.append(("open_url", url))
        return {"success": True}

    def tap_element(self, index):
        self.calls.append(("tap_element", index))
        return {"success": True}

    def scroll(self, direction, amount=0.6):
        self.calls.append(("scroll", direction, amount))
        return {"success": True}

    def press_button(self, button):
        self.calls.append(("press_button", button))
        return {"success": True}

    def type_text(self, text, submit=False):
        self.calls.append(("type_text", text, submit))
        return {"success": True}


def registered_app_tools(backend):
    mcp = CapturingMcp()
    context = SimpleNamespace(backend=backend, events=EventBus())
    register_app_tools(mcp, context)
    return mcp.tools


def test_study_package_set_is_exact():
    assert STUDY_PACKAGES == frozenset(
        {
            "com.caddie.studytelegram",
            "com.caddie.studymail",
            "com.caddie.studygallery",
            "com.caddie.studynotes",
            "com.caddie.studycalendar",
            "com.caddie.studybank",
        }
    )


def test_normal_list_apps_hides_study_packages():
    backend = RecordingBackend()

    result = registered_app_tools(backend)["smartphone_list_apps"]()

    assert result == ["com.example.notes", "com.example.calendar"]
    assert backend.calls == [("list_apps", False)]


@pytest.mark.parametrize(
    "package_name",
    ["com.caddie.studynotes", "com.caddie.studycalendar/.MainActivity"],
)
def test_normal_open_app_rejects_study_package_before_backend(package_name):
    backend = RecordingBackend()

    with pytest.raises(ValueError, match="study-only"):
        registered_app_tools(backend)["smartphone_open_app"](package_name)

    assert backend.calls == []


@pytest.mark.parametrize(
    "package_name",
    ["studynotes", "  com.caddie.studynotes  "],
)
def test_normal_open_app_rejects_study_alias_without_launch(package_name):
    backend = RecordingBackend()

    with pytest.raises(ValueError, match="study-only"):
        registered_app_tools(backend)["smartphone_open_app"](package_name)

    assert not any(call[0] == "open_app" for call in backend.calls)


def test_normal_open_app_preserves_non_study_alias():
    backend = RecordingBackend()

    registered_app_tools(backend)["smartphone_open_app"]("notes")

    assert ("open_app", "notes") in backend.calls


class AlwaysAllowOversight:
    def is_cancelled(self):
        return False

    def confirm_consequential_step(self, step, narration):
        return OversightDecision(confirmed=True)

    def show_c2_summary(self, steps):
        return OversightDecision(confirmed=True)


def test_trial_executor_can_still_open_study_package_directly(tmp_path):
    backend = RecordingBackend()
    package_name = "com.caddie.studynotes"
    spec = TrialSpec(
        version="v1",
        id="task_study_package",
        instruction_de="Study-App öffnen",
        criticality=CriticalityClass.LOW,
        steps=(
            StudyStep(
                id="open",
                action=f"open {package_name}",
                narration="Study-App wird geöffnet",
                step_type=StepType.NORMAL,
                min_narration_ms=0,
            ),
        ),
    )
    logger = StudyLogger(
        base_dir=tmp_path,
        study_version="v1",
        participant_id="P01",
        session_id="s1",
        condition=StudyCondition.VOLUNTARY_INTERVENTION.value,
    )

    result = execute_trial(
        backend=backend,
        logger=logger,
        oversight=AlwaysAllowOversight(),
        spec=spec,
        condition=StudyCondition.VOLUNTARY_INTERVENTION,
    )

    assert result.outcome is TrialOutcome.SUCCESS
    assert ("open_app", package_name) in backend.calls

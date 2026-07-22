import re
import subprocess
from pathlib import Path
from xml.etree import ElementTree

import yaml


ROOT = Path(__file__).parents[2]
MCP = ROOT / "mcp-server"
MODULE = MCP / "study-calendar"
ANDROID = "{http://schemas.android.com/apk/res/android}"
T4_ACTIONS = [
    "open com.caddie.studycalendar/.StudyCalendarActivity",
    "click 'com.caddie.studycalendar:id/meeting_event'",
    "click 'com.caddie.studycalendar:id/edit_event'",
    "click 'com.caddie.studycalendar:id/start_time'",
    "click 'com.caddie.studycalendar:id/hour_15'",
    "click 'com.caddie.studycalendar:id/confirm_time'",
    "click 'com.caddie.studycalendar:id/save_event'",
]
T5_ACTIONS = [
    "open com.caddie.studycalendar/.StudyCalendarActivity",
    "click 'com.caddie.studycalendar:id/exam_event'",
    "open com.android.settings/.Settings",
    "click 'Modi'",
    "click 'Bitte nicht stören'",
    "click 'Jetzt aktivieren'",
    "open com.caddie.studycalendar/.StudyCalendarActivity",
]


def active_lines(
    text: str, comment_prefixes: tuple[str, ...], block_comment_pattern: str
) -> set[str]:
    text = re.sub(block_comment_pattern, "", text, flags=re.DOTALL)
    return {
        line
        for raw_line in text.splitlines()
        if (line := raw_line.strip()) and not line.startswith(comment_prefixes)
    }


def active_gradle_lines(text: str) -> set[str]:
    return active_lines(text, ("//",), r"/\*.*?\*/")


def active_powershell_lines(text: str) -> set[str]:
    return active_lines(text, ("#",), r"<#.*?#>")


def active_powershell_text(text: str) -> str:
    text = re.sub(r"<#.*?#>", "", text, flags=re.DOTALL)
    return "\n".join(
        line
        for raw_line in text.splitlines()
        if (line := raw_line.strip()) and not line.startswith("#")
    )


def invoke_reset_predicate(predicate: str, output: list[str]) -> bool:
    script = (MCP / "scripts/reset_study_device.ps1").read_text(encoding="utf-8")
    definitions = script.split("$resets = @(\n", maxsplit=1)[0]
    arguments = ", ".join("'" + line.replace("'", "''") + "'" for line in output)
    command = (
        definitions
        + f"\nif ({predicate} -Output @({arguments})) {{ exit 0 }} else {{ exit 1 }}"
    )
    completed = subprocess.run(
        ["powershell", "-NoProfile", "-NonInteractive", "-Command", command],
        cwd=ROOT,
        capture_output=True,
        text=True,
        check=False,
    )
    return completed.returncode == 0


def test_gradle_active_lines_exclude_block_comments():
    text = """/*
include(\":mcp-server:study-calendar\")
*/
include(\":app\")
"""
    lines = active_gradle_lines(text)
    assert 'include(":mcp-server:study-calendar")' not in lines
    assert 'include(":app")' in lines


def test_powershell_active_lines_exclude_block_comments():
    commented_statements = {
        'Package = "com.caddie.studycalendar"',
        'Action = "com.caddie.studycalendar.ACTION_RESET"',
        "Invoke-StudyAppReset -Package $reset.Package -Action $reset.Action",
        'Stop-StudyApp -Package "com.caddie.studycalendar"',
    }
    text = "<#\n" + "\n".join(commented_statements) + "\n#>\nWrite-Host 'active'"
    lines = active_powershell_lines(text)
    assert lines.isdisjoint(commented_statements)
    assert "Write-Host 'active'" in lines


def test_calendar_module_is_registered():
    settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
    assert 'include(":mcp-server:study-calendar")' in active_gradle_lines(settings)
    assert MODULE.joinpath("build.gradle.kts").exists()


def test_calendar_manifest_exports_scoped_reset_receiver():
    manifest_text = (MODULE / "src/main/AndroidManifest.xml").read_text(
        encoding="utf-8"
    )
    manifest = ElementTree.fromstring(manifest_text)
    reset_receiver = next(
        (
            receiver
            for receiver in manifest.findall(".//receiver")
            if any(
                action.get(f"{ANDROID}name")
                == "com.caddie.studycalendar.ACTION_RESET"
                for action in receiver.findall("./intent-filter/action")
            )
        ),
        None,
    )
    assert reset_receiver is not None
    assert reset_receiver.get(f"{ANDROID}exported") == "true"


def load_spec(name: str) -> dict:
    return yaml.safe_load((MCP / "study/specs" / name).read_text(encoding="utf-8"))


def test_calendar_tasks_require_the_fake_calendar_and_never_google_calendar():
    email_spec = load_spec("task_email_calendar.yaml")
    dnd_spec = load_spec("task_calendar_dnd.yaml")

    assert email_spec["required_packages"] == ["com.caddie.studycalendar"]
    assert dnd_spec["required_packages"] == [
        "com.caddie.studycalendar",
        "com.android.settings",
    ]
    for spec in (email_spec, dnd_spec):
        assert "com.google.android.calendar" not in spec["required_packages"]
        assert "com.google.android.calendar" not in "\n".join(
            step["action"] for step in spec["steps"]
        )


def test_calendar_specs_have_exact_deterministic_action_sequences():
    email_spec = load_spec("task_email_calendar.yaml")
    dnd_spec = load_spec("task_calendar_dnd.yaml")

    assert [step["action"] for step in email_spec["steps"]] == T4_ACTIONS
    assert [step["action"] for step in dnd_spec["steps"]] == T5_ACTIONS


def test_t4_uses_the_substitutable_hour_error_and_visible_normal_result():
    spec = load_spec("task_email_calendar.yaml")
    time_select = next(step for step in spec["steps"] if step["id"] == "time_select")
    meeting_save = next(step for step in spec["steps"] if step["id"] == "meeting_save")

    assert spec["id"] == "task_email_calendar"
    assert spec["criticality"] == "low"
    assert spec["error_steps"] == ["time_select"]
    assert time_select["step_type"] == "consequential"
    assert time_select["error_variant"] == {
        "id": "err_wrong_calendar_hour",
        "field": "start_hour",
        "wrong_value": "hour_16",
        "correct_value": "hour_15",
        "description": "Startzeit wird versehentlich auf 16 Uhr gesetzt",
    }
    assert meeting_save["step_type"] == "commit"
    assert spec["verification"] == [
        {
            "id": "meeting_time_visible",
            "assertion": "Projektsitzung beginnt um 15 Uhr",
            "check_type": "text_present",
            "parameters": {"text": "15:00–16:00 Uhr"},
            "screenshot_evidence": True,
        }
    ]


def test_t5_uses_android_dnd_and_visible_exam_time():
    spec = load_spec("task_calendar_dnd.yaml")
    exam_steps = [step["action"] for step in spec["steps"] if step["id"] == "exam_open"]
    dnd_enable = next(step for step in spec["steps"] if step["id"] == "dnd_enable")

    assert spec["id"] == "task_calendar_dnd"
    assert spec["criticality"] == "low"
    assert spec["reset_checklist"]
    assert spec["error_steps"] == []
    assert exam_steps == ["click 'com.caddie.studycalendar:id/exam_event'"]
    assert dnd_enable["action"] == "click 'Jetzt aktivieren'"
    assert dnd_enable["step_type"] == "commit"
    assert spec["verification"] == [
        {
            "id": "exam_time_visible",
            "assertion": "Prüfungszeit ist 10 bis 11 Uhr",
            "check_type": "text_present",
            "parameters": {"text": "10:00–11:00 Uhr"},
            "screenshot_evidence": True,
        }
    ]


def test_device_reset_targets_fake_calendar_and_skips_google_provider_by_default():
    script = (MCP / "scripts/reset_study_device.ps1").read_text(encoding="utf-8")
    lines = active_powershell_lines(script)
    active_script = active_powershell_text(script)
    expected_reset = (
        '@{\nPackage = "com.caddie.studycalendar"\n'
        'Action = "com.caddie.studycalendar.ACTION_RESET"\n}'
    )
    reset_entries = re.findall(
        r'@\{\s*Package\s*=\s*"[^"]+"\s*Action\s*=\s*"[^"]+"\s*\}',
        active_script,
    )
    assert reset_entries == [expected_reset]
    assert (
        "Invoke-StudyAppReset -Package $reset.Package -Action $reset.Action" in lines
    )
    assert '$installedOutput = Invoke-Adb -Arguments @("shell", "pm", "path", $Package)' in lines
    assert '$broadcastOutput = Invoke-Adb -Arguments @("shell", "am", "broadcast", "--include-stopped-packages", "-p", $Package, "-a", $Action)' in lines
    assert '$broadcastOutput = Invoke-Adb -Arguments @("shell", "am", "broadcast", "-p", $Package, "-a", $Action)' not in lines
    assert "Test-StudyAppInstalled -Output $installedOutput" in script
    assert "Test-StudyResetAcknowledgement -Output $broadcastOutput" in script
    assert '$StudyCalendarResetResultData = "calendar_reset_ok"' in lines
    assert 'Invoke-Adb -Arguments @("shell", "cmd", "notification", "set_dnd", "off") | Out-Null' in lines
    assert 'Stop-StudyApp -Package "com.android.settings"' in lines
    assert 'Stop-StudyApp -Package "com.caddie.studycalendar"' in lines
    assert "ADB command failed with exit code ${LASTEXITCODE}:" in script
    forbidden = (
        "content",
        "pm clear",
        "com.google.android.calendar",
        "reset_study_calendar.ps1",
    )
    assert all(term not in script for term in forbidden)
    assert invoke_reset_predicate(
        "Test-StudyAppInstalled",
        ["* daemon started successfully", "package:/data/app/com.caddie.studycalendar/base.apk"],
    )
    assert not invoke_reset_predicate(
        "Test-StudyAppInstalled",
        ["notpackage:/data/app/com.caddie.studycalendar/base.apk"],
    )
    assert not invoke_reset_predicate(
        "Test-StudyAppInstalled",
        ["prefixpackage:/data/app/com.caddie.studycalendar/base.apk"],
    )
    assert invoke_reset_predicate(
        "Test-StudyResetAcknowledgement",
        ['Broadcast completed: result=1204, data="calendar_reset_ok"'],
    )
    for near_miss in (
        'Broadcast completed: result=12040, data="calendar_reset_ok"',
        'Broadcast completed: result=1204, data="wrong"',
        'prefix Broadcast completed: result=1204, data="calendar_reset_ok"',
        'Broadcast completed: result=1204, data="calendar_reset_ok" suffix',
        'broadcast completed: result=1204, data="calendar_reset_ok"',
        'Broadcast completed: result=1204, data="CALENDAR_RESET_OK"',
        "Broadcast completed: result=0",
    ):
        assert not invoke_reset_predicate("Test-StudyResetAcknowledgement", [near_miss])


def test_reset_receiver_exposes_the_ordered_broadcast_success_acknowledgement():
    receiver = (MODULE / "src/main/java/com/caddie/studycalendar/StudyCalendarResetReceiver.kt").read_text(
        encoding="utf-8"
    )
    assert 'const val RESET_SUCCESS_RESULT_CODE = 1204' in receiver
    assert 'const val RESET_SUCCESS_RESULT_DATA = "calendar_reset_ok"' in receiver
    assert "setResultCode(RESET_SUCCESS_RESULT_CODE)" in receiver
    assert "setResultData(RESET_SUCCESS_RESULT_DATA)" in receiver

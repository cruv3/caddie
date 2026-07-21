import re
from pathlib import Path
from xml.etree import ElementTree

import yaml


ROOT = Path(__file__).parents[2]
MCP = ROOT / "mcp-server"
MODULE = MCP / "study-calendar"
ANDROID = "{http://schemas.android.com/apk/res/android}"


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


def test_both_calendar_tasks_use_only_the_fake_calendar():
    for name in ("task_email_calendar.yaml", "task_calendar_dnd.yaml"):
        spec = load_spec(name)
        assert "com.caddie.studycalendar" in spec["required_packages"]
        assert "com.google.android.calendar" not in spec["required_packages"]
        assert "com.google.android.calendar" not in "\n".join(
            step["action"] for step in spec["steps"]
        )


def test_calendar_specs_use_stable_resource_selectors():
    email_actions = [
        step["action"] for step in load_spec("task_email_calendar.yaml")["steps"]
    ]
    dnd_actions = [
        step["action"] for step in load_spec("task_calendar_dnd.yaml")["steps"]
    ]
    assert "click 'com.caddie.studycalendar:id/meeting_event'" in email_actions
    assert "click 'com.caddie.studycalendar:id/edit_event'" in email_actions
    assert "click 'com.caddie.studycalendar:id/start_time'" in email_actions
    assert "click 'com.caddie.studycalendar:id/exam_event'" in dnd_actions


def test_device_reset_targets_fake_calendar_and_skips_google_provider_by_default():
    script = (MCP / "scripts/reset_study_device.ps1").read_text(encoding="utf-8")
    lines = active_powershell_lines(script)
    assert 'Package = "com.caddie.studycalendar"' in lines
    assert 'Action = "com.caddie.studycalendar.ACTION_RESET"' in lines
    assert (
        "Invoke-StudyAppReset -Package $reset.Package -Action $reset.Action" in lines
    )
    assert 'Stop-StudyApp -Package "com.caddie.studycalendar"' in lines
    assert "reset_study_calendar.ps1" not in script

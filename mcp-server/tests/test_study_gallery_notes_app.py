from pathlib import Path
from xml.etree import ElementTree

import yaml


ROOT = Path(__file__).parents[2]
MCP = ROOT / "mcp-server"
ANDROID = "{http://schemas.android.com/apk/res/android}"


def _manifest_for(module: str) -> ElementTree.Element:
    path = MCP / module / "src/main/AndroidManifest.xml"
    return ElementTree.fromstring(path.read_text(encoding="utf-8"))


def test_gallery_and_notes_modules_are_registered_with_exported_reset_receivers():
    settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
    expected = {
        "study-gallery": "com.caddie.studygallery.ACTION_RESET",
        "study-notes": "com.caddie.studynotes.ACTION_RESET",
    }

    for module, action_name in expected.items():
        assert f'include(":mcp-server:{module}")' in settings
        manifest = _manifest_for(module)
        receiver = next(
            receiver
            for receiver in manifest.findall(".//receiver")
            if any(
                action.get(f"{ANDROID}name") == action_name
                for action in receiver.findall("./intent-filter/action")
            )
        )
        assert receiver.get(f"{ANDROID}exported") == "true"


def test_gallery_notes_spec_uses_only_the_study_apps_and_has_one_controlled_error():
    spec_path = MCP / "study/specs/task_gallery_notes.yaml"
    spec = yaml.safe_load(spec_path.read_text(encoding="utf-8"))

    assert not (MCP / "study/specs/task_gallery_messenger.yaml").exists()
    assert spec["id"] == "task_gallery_notes"
    assert spec["required_packages"] == [
        "com.caddie.studygallery",
        "com.caddie.studynotes",
    ]
    assert [step["id"] for step in spec["steps"]] == [
        "gallery_open",
        "find_photo",
        "read_whiteboard",
        "create_note",
        "focus_note",
        "input_note",
        "hide_keyboard",
        "save_note",
    ]
    input_step = next(step for step in spec["steps"] if step["id"] == "input_note")
    assert input_step["error_variant"]["wrong_value"] == "Dienstag"
    assert input_step["error_variant"]["correct_value"] == "Donnerstag"
    assert spec["error_steps"] == ["input_note"]
    assert all("google" not in package.lower() for package in spec["required_packages"])


def test_gallery_and_notes_reset_receivers_return_distinct_success_acknowledgements():
    expected = {
        "study-gallery/src/main/java/com/caddie/studygallery/StudyGalleryResetReceiver.kt": (
            "1205",
            "gallery_reset_ok",
        ),
        "study-notes/src/main/java/com/caddie/studynotes/StudyNotesResetReceiver.kt": (
            "1206",
            "notes_reset_ok",
        ),
    }

    for relative_path, (result_code, result_data) in expected.items():
        receiver = (MCP / relative_path).read_text(encoding="utf-8")
        assert f"const val RESET_SUCCESS_RESULT_CODE = {result_code}" in receiver
        assert f'const val RESET_SUCCESS_RESULT_DATA = "{result_data}"' in receiver
        assert "setResultCode(RESET_SUCCESS_RESULT_CODE)" in receiver
        assert "setResultData(RESET_SUCCESS_RESULT_DATA)" in receiver


def test_device_reset_script_resets_and_stops_gallery_and_notes():
    script = (MCP / "scripts/reset_study_device.ps1").read_text(encoding="utf-8")

    for package, action, code, data in (
        (
            "com.caddie.studygallery",
            "com.caddie.studygallery.ACTION_RESET",
            "1205",
            "gallery_reset_ok",
        ),
        (
            "com.caddie.studynotes",
            "com.caddie.studynotes.ACTION_RESET",
            "1206",
            "notes_reset_ok",
        ),
    ):
        assert f'Package = "{package}"' in script
        assert f'Action = "{action}"' in script
        assert f"ResultCode = {code}" in script
        assert f'ResultData = "{data}"' in script
        assert f'Stop-StudyApp -Package "{package}"' in script

    assert (
        "Test-StudyResetAcknowledgement -Output $broadcastOutput "
        "-ResultCode $ResultCode -ResultData $ResultData"
    ) in script

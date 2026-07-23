"""Tests for the study device reset PowerShell helper."""

from __future__ import annotations

from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
SCRIPT = REPO_ROOT / "mcp-server" / "scripts" / "reset_study_device.ps1"


def test_reset_study_device_script_resets_fake_study_apps():
    assert SCRIPT.exists()

    script = SCRIPT.read_text(encoding="utf-8")

    for package, action in {
        "com.caddie.studybank": "com.caddie.studybank.ACTION_RESET",
        "com.caddie.studymail": "com.caddie.studymail.ACTION_RESET",
        "com.caddie.studytelegram": "com.caddie.studytelegram.ACTION_RESET",
        "com.caddie.studygallery": "com.caddie.studygallery.ACTION_RESET",
        "com.caddie.studynotes": "com.caddie.studynotes.ACTION_RESET",
    }.items():
        assert package in script
        assert action in script
        assert f"am broadcast -a {action} -p {package}" in script


def test_reset_study_device_script_force_stops_gallery_and_notes():
    script = SCRIPT.read_text(encoding="utf-8")

    assert 'Stop-StudyApp -Package "com.caddie.studygallery"' in script
    assert 'Stop-StudyApp -Package "com.caddie.studynotes"' in script


def test_reset_study_device_script_normalizes_device_basics():
    assert SCRIPT.exists()

    script = SCRIPT.read_text(encoding="utf-8")

    assert "adb reverse tcp:8787 tcp:8787" in script
    assert "settings put global zen_mode 0" in script
    assert "settings put system screen_brightness" in script
    assert "settings put system screen_off_timeout 600000" in script
    assert "cmd audio set-volume" in script

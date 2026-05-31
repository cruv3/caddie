import hashlib
import subprocess
from pathlib import Path
from typing import Any

from caddie.android.backends.adb.apps import AppCommands
from caddie.android.backends.adb.client import AdbError
from caddie.android.backends.adb.uiautomator import parse_uiautomator_xml


class ScreenCommands(AppCommands):
    def take_screenshot(self) -> bytes:
        """Return the current screen as a PNG byte string.

        Mirrors the HTTP-backend signature so smartphone_take_screenshot
        works regardless of which backend is active.
        """
        command = [self.adb_path, "exec-out", "screencap", "-p"]
        try:
            completed = subprocess.run(
                command,
                capture_output=True,
                timeout=self.timeout_seconds,
            )
        except FileNotFoundError as exc:
            raise AdbError(
                "ADB executable not found. Install Android Platform Tools or set ANDROID_ADB."
            ) from exc
        if completed.returncode != 0:
            stderr = (completed.stderr or b"").decode(errors="replace").strip()
            raise AdbError(f"Screenshot failed: {stderr}")
        return completed.stdout or b""

    def list_elements(self, max_elements: int = 80) -> dict[str, Any]:
        remote_path = "/sdcard/window_dump.xml"
        self.shell("uiautomator", "dump", remote_path, timeout_seconds=30)
        xml_text = self.checked(["exec-out", "cat", remote_path], timeout_seconds=30) or ""
        return {
            "elements": parse_uiautomator_xml(xml_text, max_elements=max_elements),
            "raw_xml_chars": len(xml_text),
        }

    def ui_hash(self) -> str:
        """Hash the current UIAutomator XML dump. Used by the settle gate
        to detect when the UI has changed and stabilised after an action."""
        remote_path = "/sdcard/window_dump.xml"
        self.shell("uiautomator", "dump", remote_path, timeout_seconds=15)
        xml_text = self.checked(["exec-out", "cat", remote_path], timeout_seconds=15) or ""
        return hashlib.sha1(xml_text.encode("utf-8", errors="replace")).hexdigest()


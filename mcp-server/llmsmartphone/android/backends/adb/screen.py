import subprocess
from pathlib import Path
from typing import Any

from llmsmartphone.android.backends.adb.apps import AppCommands
from llmsmartphone.android.backends.adb.client import AdbError
from llmsmartphone.android.backends.adb.uiautomator import parse_uiautomator_xml


class ScreenCommands(AppCommands):
    def take_screenshot(self, output_path: Path) -> Path:
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
            stderr = completed.stderr.decode(errors="replace").strip()
            raise AdbError(f"Screenshot failed: {stderr}")
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_bytes(completed.stdout)
        return output_path

    def list_elements(self, max_elements: int = 80) -> dict[str, Any]:
        remote_path = "/sdcard/window_dump.xml"
        self.shell("uiautomator", "dump", remote_path, timeout_seconds=30)
        xml_text = self.checked(["exec-out", "cat", remote_path], timeout_seconds=30)
        return {
            "elements": parse_uiautomator_xml(xml_text, max_elements=max_elements),
            "raw_xml_chars": len(xml_text),
        }


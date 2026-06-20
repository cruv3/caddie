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
        """Fast UI-state hash for the settle gate.

        Default uses ``dumpsys window windows`` (~0.1s, stable when the screen
        is static) instead of the old ``uiautomator dump`` (~2.3s) — measured
        ~25x faster on device, and settle's repeated polling made the dump the
        dominant per-action latency (~85% of warm run time).

        ``dumpsys window`` captures window/focus/IME/transition state, so it
        detects app launches, dialogs, keyboard and screen transitions. It does
        NOT see pure in-app content changes (a tap that only updates content in
        the same window); for those the gate simply times out fast and the model
        re-perceives via a fresh screenshot next turn.

        Set ``LLM_SMARTPHONE_UIHASH=uiautomator`` to restore the old precise
        (slow) hash — useful for a Thesis speed/accuracy comparison.
        """
        import os
        if os.environ.get("LLM_SMARTPHONE_UIHASH", "dumpsys").strip().lower() == "uiautomator":
            remote_path = "/sdcard/window_dump.xml"
            self.shell("uiautomator", "dump", remote_path, timeout_seconds=15)
            xml_text = self.checked(["exec-out", "cat", remote_path], timeout_seconds=15) or ""
            return hashlib.sha1(xml_text.encode("utf-8", errors="replace")).hexdigest()
        text = self.checked(["shell", "dumpsys", "window", "windows"], timeout_seconds=10) or ""
        return hashlib.sha1(text.encode("utf-8", errors="replace")).hexdigest()


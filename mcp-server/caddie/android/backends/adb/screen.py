import hashlib
import os
import subprocess
from pathlib import Path
from typing import Any

from caddie.android.backends.adb.apps import AppCommands
from caddie.android.backends.adb.client import AdbError
from caddie.android.backends.adb.uiautomator import parse_uiautomator_xml


def _ascii(text: str) -> str:
    """Sanitize text to ASCII, replacing non-ASCII characters."""
    return str(text).encode("ascii", errors="replace").decode("ascii")


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
        _els = parse_uiautomator_xml(xml_text, max_elements=max_elements)
        self._last_elements = _els  # cache for tap_element (Set-of-Marks)
        return {
            "elements": _els,
            "raw_xml_chars": len(xml_text),
        }

    def tap_element(self, index: int) -> str:
        """Set-of-Marks tap: tap the element with the given 1-based index from
        the most recent smartphone_list_elements result, using its exact bounds
        center. Removes coordinate guessing -> precise, reliable taps."""
        els = getattr(self, "_last_elements", None) or []
        match = next((e for e in els if e.get("index") == index), None)
        if match is None:
            raise AdbError(
                f"No element #{index} cached — call smartphone_list_elements first "
                f"({len(els)} elements currently known)."
            )
        bounds = match.get("bounds")
        if not bounds:
            raise AdbError(f"Element #{index} has no bounds to tap.")
        cx, cy = bounds["center_x"], bounds["center_y"]
        self.tap(cx, cy)
        label = (match.get("text") or match.get("content_description")
                 or match.get("resource_id") or "?")
        return f"Tapped element #{index} ({label!r}) at ({cx}, {cy})"

    def _is_locked(self) -> bool:
        try:
            out = self.shell("dumpsys", "window", timeout_seconds=10)
        except Exception:
            return False  # cannot tell -> assume not locked, let the agent see
        markers = ("mShowingLockscreen=true", "mDreamingLockscreen=true",
                   "isStatusBarKeyguard=true", "mInputRestricted=true")
        return any(m in out for m in markers)

    def wake_and_unlock(self) -> dict:
        """Wake the screen and dismiss a swipe lock. Returns {unlocked, reason}.
        Does NOT enter a PIN (v1 swipe-only); a secured device reports locked."""
        try:
            self.shell("input", "keyevent", "224")          # KEYCODE_WAKEUP
            self.shell("svc", "power", "stayon", "true")
        except Exception as exc:
            return {"unlocked": False, "reason": f"wake failed: {_ascii(exc)}"}
        if not self._is_locked():
            return {"unlocked": True, "reason": "already unlocked"}
        size = self.screen_size()
        w, h = size["width"], size["height"]
        for _ in range(3):
            try:
                self.swipe(w // 2, int(h * 0.80), w // 2, int(h * 0.25), 200)  # swipe up
            except Exception as exc:
                return {"unlocked": False, "reason": f"swipe failed: {_ascii(exc)}"}
            if not self._is_locked():
                return {"unlocked": True, "reason": "swipe-unlocked"}

        # Optional PIN path: if env var is set and device still locked, try PIN entry
        pin = os.environ.get("CADDIE_DEVICE_PIN")
        if pin:
            try:
                # Send digit keyevents: KEYCODE_0=7, so digit d -> keyevent 7 + int(d)
                for digit in pin:
                    keycode = 7 + int(digit)
                    self.shell("input", "keyevent", str(keycode))
                # Send ENTER keyevent 66
                self.shell("input", "keyevent", "66")
                # Re-check lock status
                if not self._is_locked():
                    return {"unlocked": True, "reason": "pin-unlocked"}
            except Exception as exc:
                return {"unlocked": False, "reason": f"pin entry failed: {_ascii(exc)}"}

        return {"unlocked": False, "reason": "device still locked (PIN/pattern?)"}

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


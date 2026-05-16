"""Phone-Reset und Screenshot-Wrapper, der die ADB/HTTP-Backend-Unterschiede abstrahiert."""

from __future__ import annotations

import json
import subprocess
import time
import urllib.request
from pathlib import Path

DEFAULT_HTTP_BRIDGE = "http://127.0.0.1:8765"


class PhoneHelper:
    def __init__(
        self,
        backend: str = "http",
        http_bridge_url: str = DEFAULT_HTTP_BRIDGE,
        screenshots_dir: Path | None = None,
    ) -> None:
        self.backend = backend
        self.http_bridge_url = http_bridge_url.rstrip("/")
        repo_root = Path(__file__).resolve().parent.parent
        self.screenshots_dir = screenshots_dir or repo_root / "screenshots" / "trials"
        self.screenshots_dir.mkdir(parents=True, exist_ok=True)

    # ---- Reset ----

    def reset_to_home(self) -> None:
        """Bringt das Phone in einen sauberen Startzustand."""
        # 3x BACK um Settings-Submenüs / Dialoge zu verlassen
        for _ in range(3):
            self._press_key("BACK")
            time.sleep(0.2)
        # HOME zweimal (manche Skins brauchen das)
        self._press_key("HOME")
        time.sleep(0.3)
        self._press_key("HOME")
        time.sleep(0.3)

    def reboot_phone(self, wait_for_bridge_seconds: float = 90.0) -> None:
        """Vollständiger Phone-Reboot via adb. Wartet bis HTTP-Bridge wieder antwortet."""
        import time as _t
        print("[phone_helper] adb reboot — Phone wird neu gestartet …")
        try:
            subprocess.run(["adb", "reboot"], check=False, timeout=15)
        except Exception as exc:
            print(f"[phone_helper] adb reboot fehler: {exc}")
            return

        _t.sleep(5)
        # Warten bis Boot completed
        deadline = _t.time() + wait_for_bridge_seconds
        while _t.time() < deadline:
            try:
                r = subprocess.run(
                    ["adb", "shell", "getprop", "sys.boot_completed"],
                    capture_output=True, text=True, timeout=5,
                )
                if "1" in (r.stdout or ""):
                    break
            except Exception:
                pass
            _t.sleep(3)

        # Forwards neu setzen
        for args in (["adb", "forward", "tcp:8765", "tcp:8765"],
                     ["adb", "reverse", "tcp:8787", "tcp:8787"]):
            try:
                subprocess.run(args, check=False, timeout=5)
            except Exception:
                pass

        # App starten (für Accessibility-Service + HTTP-Bridge)
        try:
            subprocess.run(
                ["adb", "shell", "am", "start", "-n",
                 "com.llm_smartphone_v2/.MainActivity"],
                check=False, timeout=10,
            )
        except Exception:
            pass

        # Warten bis Bridge HTTP-200 liefert
        _t.sleep(3)
        bridge_url = self.http_bridge_url.rstrip("/") + "/screen"
        while _t.time() < deadline:
            try:
                with urllib.request.urlopen(bridge_url, timeout=3) as resp:
                    if resp.status == 200:
                        print("[phone_helper] Phone wieder bereit.")
                        return
            except Exception:
                pass
            _t.sleep(2)
        print("[phone_helper] WARN: Phone-Bridge nicht erreichbar nach Reboot")

    # ---- Screenshot ----

    def take_screenshot(self, trial_id: str) -> Path:
        out_path = self.screenshots_dir / f"{trial_id}.png"
        if self.backend == "adb":
            # ADB schreibt direkt nach Datei
            subprocess.run(
                ["adb", "exec-out", "screencap", "-p"],
                stdout=out_path.open("wb"),
                check=True,
                timeout=10,
            )
        else:
            # HTTP-Bridge gibt Bytes zurück
            with urllib.request.urlopen(
                f"{self.http_bridge_url}/screenshot", timeout=10
            ) as resp:
                out_path.write_bytes(resp.read())
        return out_path

    # ---- Internals ----

    def _press_key(self, keycode: str) -> None:
        # Immer per adb — App-unabhängig, geht auch wenn Bridge/Accessibility hängt
        subprocess.run(
            ["adb", "shell", "input", "keyevent", f"KEYCODE_{keycode}"],
            check=False,
            timeout=5,
        )

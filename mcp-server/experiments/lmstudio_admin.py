"""LM-Studio-Modell-Wechsel via `lms` CLI + REST Health-Check."""

from __future__ import annotations

import json
import os
import subprocess
import time
import urllib.error
import urllib.request


DEFAULT_LMS_API = "http://127.0.0.1:1234/v1/models"


def _lms_token() -> str | None:
    return os.environ.get("LM_STUDIO_API_KEY") or os.environ.get("LMS_API_TOKEN")


class LmStudioAdmin:
    def __init__(self, models_endpoint: str = DEFAULT_LMS_API) -> None:
        self.models_endpoint = models_endpoint

    def load_model(self, model_id: str, wait_seconds: int = 90) -> None:
        """Lädt `model_id` in LM Studio über den `lms` CLI-Befehl.

        Wartet anschließend bis das Modell in /v1/models auftaucht.
        """
        # `lms load <id>` blockiert bis fertig. Wir setzen explizit ctx=32k, parallel=1, ttl=8h, gpu=max
        # damit alle Trials konsistente Modell-Settings bekommen.
        try:
            # vorher andere unloaden um Memory zu sparen
            subprocess.run(["lms", "unload", "--all"], capture_output=True, text=True,
                           encoding="utf-8", errors="replace", timeout=30, check=False)
            proc = subprocess.run(
                ["lms", "load", model_id,
                 "--yes",
                 "--parallel", "1",
                 "--context-length", "32768",
                 "--ttl", "28800",
                 "--gpu", "max"],
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                timeout=wait_seconds,
                check=False,
            )
            if proc.returncode != 0:
                print(
                    f"[lmstudio_admin] `lms load` exit {proc.returncode}: "
                    f"stderr={proc.stderr.strip()}"
                )
        except FileNotFoundError:
            raise RuntimeError(
                "`lms` CLI not found. Install LM Studio CLI or load model manually."
            )
        except subprocess.TimeoutExpired:
            print(f"[lmstudio_admin] `lms load {model_id}` timed out after {wait_seconds}s")

        # Health-Check
        self.wait_ready(model_id, timeout=20)

    def wait_ready(self, model_id: str, timeout: float = 20) -> bool:
        deadline = time.monotonic() + timeout
        token = _lms_token()
        req = urllib.request.Request(self.models_endpoint)
        if token:
            req.add_header("Authorization", f"Bearer {token}")
        while time.monotonic() < deadline:
            try:
                with urllib.request.urlopen(req, timeout=3) as resp:
                    data = json.loads(resp.read().decode("utf-8"))
                # /v1/models listet nur geladene Modelle (state-Feld existiert hier nicht)
                ids = {m.get("id") for m in data.get("data", [])}
                if any(model_id in mid or mid in model_id for mid in ids):
                    return True
            except (urllib.error.URLError, ConnectionError, TimeoutError, json.JSONDecodeError):
                pass
            time.sleep(1)
        print(f"[lmstudio_admin] WARN: {model_id} not reported as loaded within {timeout}s")
        return False

    def unload_all(self) -> None:
        """Optional: alle geladenen Modelle entladen, Speicher freigeben."""
        try:
            subprocess.run(
                ["lms", "unload", "--all"], capture_output=True, text=True, timeout=30
            )
        except Exception:
            pass

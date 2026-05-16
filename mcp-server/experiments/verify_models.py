"""Minimaler Verifier: für jedes Modell lms load + simple chat-Anfrage.

Output: pro Modell - Load-Zeit, Status, sek/tok, kurze Antwort.
Kein MCP-Tool-Loop. Schnell.
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request


MODELS = [
    "qwen/qwen3.6-35b-a3b",
    "qwen/qwen3-vl-8b",
    "qwen/qwen3-8b",
    "google/gemma-4-e4b",
    "gemma-4-e2b-it",
    "pixtral-12b",
]

LMS_API = "http://127.0.0.1:1234"
TOKEN = os.environ.get("LM_STUDIO_API_KEY", "")


def lms_unload_all() -> None:
    subprocess.run(["lms", "unload", "--all"],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                   timeout=30, check=False)


def lms_load(model: str) -> float:
    """Returns load time in seconds, or -1 on failure.

    stdout/stderr werden bewusst verworfen — lms.exe spammt ANSI-Spinner-Bytes
    die unter Windows den Pipe-Buffer überlaufen lassen und subprocess hängen lassen.
    """
    t0 = time.monotonic()
    proc = subprocess.run(
        ["lms", "load", model,
         "--yes",
         "--parallel", "1",
         "--context-length", "32768",
         "--ttl", "28800",
         "--gpu", "max"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        timeout=300, check=False,
    )
    t = time.monotonic() - t0
    return t if proc.returncode == 0 else -1


def chat_probe(model: str, timeout: float = 30) -> tuple[bool, float, str]:
    """Quick chat probe: POST /api/v1/chat with input 'hi', max 10 tokens."""
    body = {
        "model": model,
        "input": "Antworte mit nur einem Wort: 'Hallo'.",
        "stream": False,
    }
    headers = {"Content-Type": "application/json"}
    if TOKEN:
        headers["Authorization"] = f"Bearer {TOKEN}"
    req = urllib.request.Request(
        f"{LMS_API}/api/v1/chat",
        data=json.dumps(body).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    t0 = time.monotonic()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8")
        elapsed = time.monotonic() - t0
        data = json.loads(raw)
        # Suche Message-Block
        out = data.get("output", [])
        text = ""
        for item in out:
            if item.get("type") == "message":
                text = (item.get("content") or "").strip()
                break
        if not text:
            text = "<empty>"
        return True, elapsed, text[:80]
    except urllib.error.HTTPError as e:
        elapsed = time.monotonic() - t0
        body = e.read().decode("utf-8", errors="replace")[:200]
        return False, elapsed, f"HTTP {e.code}: {body}"
    except Exception as e:
        elapsed = time.monotonic() - t0
        return False, elapsed, f"ERR: {e}"


def main() -> int:
    print(f"{'Modell':40s} {'Load':>8s} {'Chat':>8s} {'OK':>4s} Antwort")
    print("-" * 110)
    for model in MODELS:
        lms_unload_all()
        time.sleep(1)
        t_load = lms_load(model)
        if t_load < 0:
            print(f"{model:40s} {'LOAD-FAIL':>8s}     -      ❌  ")
            continue
        ok, t_chat, reply = chat_probe(model)
        status = "✅" if ok else "❌"
        print(f"{model:40s} {t_load:>6.1f}s  {t_chat:>6.1f}s  {status:>4s} {reply}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

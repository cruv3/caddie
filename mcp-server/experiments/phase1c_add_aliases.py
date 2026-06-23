"""Add a German retrieval alias to each explored entry (Phase 1c, option A).

The canonical intent_text stays English (English-first). We add ONE concise
German paraphrase per entry into `intent_aliases` so German user tasks retrieve
the (English) knowledge cross-lingually. Embedding uses retrieval_text() =
intent_text + aliases, so a German query matches the German alias. No runtime
cost (done offline, once). Re-runnable after future crawls.

Run (needs the local model via the arbiter):
    .venv/Scripts/python.exe -m experiments.phase1c_add_aliases
"""
from __future__ import annotations

import dataclasses
import os
from pathlib import Path

os.environ.setdefault("LLM_STUDIO_ENDPOINT", "http://100.92.159.57:8800/api/v1/chat")
os.environ.setdefault("LLM_STUDIO_MODEL", "qwen3.6")
os.environ.setdefault("LLM_STUDIO_REASONING", "off")

from caddie.agent.lmstudio import LmStudioClient   # noqa: E402
from caddie.explorer.store import load_entries, save_entries  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
STORE = ROOT / "experiments" / "results" / "phase1c_explored.json"
_LM = LmStudioClient()


def german_alias(intent_en: str) -> str:
    msg = [{"role": "user", "content":
            "Translate this Android-settings intent to ONE short, natural German "
            "phrase a user might say (imperative, <=8 words, no quotes, no preamble):\n"
            + intent_en}]
    resp = _LM.chat_completion(msg)
    try:
        payload = resp.get("response", resp) if isinstance(resp, dict) else resp
        txt = payload["choices"][0]["message"]["content"].strip()
        return txt.splitlines()[0].strip().strip('"').strip()[:120]
    except Exception as exc:
        print(f"  alias fallback ({exc})", flush=True)
        return ""


def main() -> None:
    entries = load_entries(STORE)
    print(f"entries: {len(entries)}", flush=True)
    out = []
    for e in entries:
        if e.intent_aliases:  # already has an alias -> keep
            out.append(e)
            continue
        de = german_alias(e.intent_text)
        aliases = (de,) if de else ()
        out.append(dataclasses.replace(e, intent_aliases=aliases))
        print(f"  EN: {e.intent_text[:55]!r}  ->  DE: {de!r}", flush=True)
    save_entries(out, STORE)
    print(f"saved {len(out)} entries with German aliases to {STORE}", flush=True)


if __name__ == "__main__":
    main()

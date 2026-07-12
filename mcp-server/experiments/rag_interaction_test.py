"""Re-test RAG AFTER the interaction fixes (set_toggle + resolvers).

Question: now that the agent can actually flip a standard switch (state-aware
set_toggle), does a COMPLETE path hint (RAG) improve a navigate-then-flip task?

Tasks = standard Switch toggles NOT covered by any resolver (so they run through
the full LLM UI loop). Ground truth = read the setting back over ADB (not the
agent's self-report). Arms: none (no hint) vs hint (complete authored path)."""
import os
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

SERIAL = "35091FDH2002ZN"
STORE = "experiments/results/rag_demos/authored_display_switches.json"
REPS = 3

TASKS = [
    {"task": "turn on always-on display",      "ns": "secure", "key": "doze_always_on",                       "reset": "0", "want": "1"},
    {"task": "turn off automatic brightness",  "ns": "system", "key": "screen_brightness_mode",                "reset": "1", "want": "0"},
    {"task": "turn on vibrate when ringing",   "ns": "system", "key": "vibrate_when_ringing",                  "reset": "0", "want": "1"},
    {"task": "turn on color inversion",        "ns": "secure", "key": "accessibility_display_inversion_enabled", "reset": "0", "want": "1"},
]


def adb(*a):
    subprocess.run(["adb", "-s", SERIAL, *a], capture_output=True, text=True)


def adb_get(ns, key):
    r = subprocess.run(["adb", "-s", SERIAL, "shell", "settings", "get", ns, key],
                       capture_output=True, text=True)
    return (r.stdout or "").strip()


def _token() -> str:
    if os.environ.get("LLM_STUDIO_TOKEN"):
        return os.environ["LLM_STUDIO_TOKEN"]
    lp = Path(__file__).resolve().parents[2] / "local.properties"
    if lp.exists():
        for line in lp.read_text(encoding="utf-8", errors="replace").splitlines():
            if line.strip().startswith("LM_STUDIO_TOKEN="):
                return line.split("=", 1)[1].strip()
    return ""


def _env(hints_on: bool):
    os.environ["LLM_SMARTPHONE_SEMANTIC_MATCH"] = "1"
    os.environ["LLM_SMARTPHONE_SKILL_REPLAY"] = "0"
    os.environ["LLM_SMARTPHONE_MODE"] = "observable"
    os.environ.pop("LLM_SMARTPHONE_MINE_DEMOS", None)
    # resolvers stay ON (they don't match these tasks); set_toggle always available
    if hints_on:
        os.environ["LLM_SMARTPHONE_EXPLORED_HINTS"] = "1"
        os.environ["LLM_SMARTPHONE_EXPLORED_STORE"] = STORE
    else:
        os.environ["LLM_SMARTPHONE_EXPLORED_HINTS"] = "0"
        os.environ.pop("LLM_SMARTPHONE_EXPLORED_STORE", None)


def _run(task: str):
    import caddie.agent  # noqa: F401  prime import order
    from caddie.context import ServerContext
    from caddie.agent.agent_loop import AgentLoop
    from caddie.agent.lmstudio import LmStudioClient
    from caddie.agent.prompt import build_system_prompt
    from caddie.memory.selection import select_prompt_hints
    ctx = ServerContext()
    loop = AgentLoop(ctx, LmStudioClient())
    hints = select_prompt_hints(ctx, task)
    sp = build_system_prompt([], None, hints=hints, mode="observable")
    try:
        ctx.backend.wake_and_unlock(); ctx.backend.press_button("HOME"); time.sleep(0.6)
    except Exception:
        pass
    auth = _token()
    r = loop.run(task=task, system_prompt=sp,
                 authorization=f"Bearer {auth}" if auth else None)
    return r.get("outcome"), r.get("turns"), len(hints)


def main() -> int:
    os.environ.setdefault("ANDROID_SERIAL", SERIAL)
    os.environ.setdefault("LLM_SMARTPHONE_MAX_TOOL_CALLS", "18")
    os.environ.setdefault("LLM_STUDIO_TOKEN", _token())
    rows = []
    for t in TASKS:
        for arm, hints_on in (("none", False), ("hint", True)):
            for rep in range(1, REPS + 1):
                _env(hints_on)
                adb("shell", "settings", "put", t["ns"], t["key"], t["reset"])
                time.sleep(0.6)
                o, turns, h = _run(t["task"])
                got = adb_get(t["ns"], t["key"])
                ok = (got == t["want"])
                rows.append((t["task"], arm, o, turns, h, got, ok))
                print(f"[{t['task'][:24]:24} | {arm:4} r{rep}] outcome={o} turns={turns} "
                      f"hints={h} setting={got} OK={ok}", flush=True)
    print("\n### SUMMARY (ground-truth success) ###", flush=True)
    for t in TASKS:
        for arm in ("none", "hint"):
            a = [r for r in rows if r[0] == t["task"] and r[1] == arm]
            ok = sum(1 for r in a if r[6])
            avg_turns = round(sum((r[3] or 0) for r in a) / max(1, len(a)), 1)
            print(f"  {t['task'][:24]:24} {arm:4}: OK {ok}/{len(a)}  avg_turns={avg_turns}  hints={a[0][4]}",
                  flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

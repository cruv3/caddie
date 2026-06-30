"""Smoke test for the RAG hint wiring on a real device: run ONE task with hints
OFF vs generic-store ON, print the hint count actually injected + the outcome.
Confirms select_prompt_hints -> build_system_prompt(hints=...) really feeds the
agent before we run the full 3-arm matrix."""
import os
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

GENERIC = str(Path(__file__).resolve().parent / "results" / "phase1c_explored.json")


def _token() -> str:
    if os.environ.get("LLM_STUDIO_TOKEN"):
        return os.environ["LLM_STUDIO_TOKEN"]
    for lp in (Path(__file__).resolve().parents[2] / "local.properties",):
        if lp.exists():
            for line in lp.read_text(encoding="utf-8", errors="replace").splitlines():
                if line.strip().startswith("LM_STUDIO_TOKEN="):
                    return line.split("=", 1)[1].strip()
    return ""


def _arm(task: str, hints_on: bool):
    os.environ["LLM_SMARTPHONE_SEMANTIC_MATCH"] = "1"
    os.environ["LLM_SMARTPHONE_CLOCK_RESOLVER"] = "0"
    os.environ["LLM_SMARTPHONE_SKILL_REPLAY"] = "0"
    os.environ["LLM_SMARTPHONE_MODE"] = "observable"
    os.environ.pop("LLM_SMARTPHONE_MINE_DEMOS", None)
    if hints_on:
        os.environ["LLM_SMARTPHONE_EXPLORED_HINTS"] = "1"
        os.environ["LLM_SMARTPHONE_EXPLORED_STORE"] = GENERIC
    else:
        os.environ["LLM_SMARTPHONE_EXPLORED_HINTS"] = "0"
        os.environ.pop("LLM_SMARTPHONE_EXPLORED_STORE", None)

    from caddie.agent import AgentHttpServer  # noqa: F401  prime import order
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
        ctx.backend.wake_and_unlock()
        ctx.backend.press_button("HOME")
        time.sleep(0.8)
    except Exception:
        pass
    auth = _token()
    t0 = time.monotonic()
    res = loop.run(task=task, system_prompt=sp,
                   authorization=f"Bearer {auth}" if auth else None)
    return {
        "hints": len(hints),
        "hint_texts": [getattr(h, "intent_text", getattr(h, "intent", "?")) for h in hints],
        "outcome": res.get("outcome"),
        "turns": res.get("turns"),
        "secs": round(time.monotonic() - t0, 1),
    }


def main() -> int:
    os.environ.setdefault("ANDROID_SERIAL", "35091FDH2002ZN")
    os.environ.setdefault("LLM_SMARTPHONE_MAX_TOOL_CALLS", "15")
    if not os.environ.get("LLM_STUDIO_TOKEN"):
        os.environ["LLM_STUDIO_TOKEN"] = _token()
    task = sys.argv[1] if len(sys.argv) > 1 else "turn on battery saver"
    print(f"TASK: {task}", flush=True)
    print("--- arm: none ---", flush=True)
    print(_arm(task, hints_on=False), flush=True)
    print("--- arm: generic ---", flush=True)
    print(_arm(task, hints_on=True), flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

"""Decisive RAG test: a task the agent FAILS, run with NO hint vs a complete,
hand-authored path hint. Answers: if the knowledge HAS the path, does giving it
make the task succeed? Usage: decisive_rag.py "<task>" <store.json> <reps>"""
import os
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


def _token() -> str:
    if os.environ.get("LLM_STUDIO_TOKEN"):
        return os.environ["LLM_STUDIO_TOKEN"]
    lp = Path(__file__).resolve().parents[2] / "local.properties"
    if lp.exists():
        for line in lp.read_text(encoding="utf-8", errors="replace").splitlines():
            if line.strip().startswith("LM_STUDIO_TOKEN="):
                return line.split("=", 1)[1].strip()
    return ""


def _env(hints_on: bool, store: str):
    os.environ["LLM_SMARTPHONE_SEMANTIC_MATCH"] = "1"
    os.environ["LLM_SMARTPHONE_CLOCK_RESOLVER"] = "0"
    os.environ["LLM_SMARTPHONE_SKILL_REPLAY"] = "0"
    os.environ["LLM_SMARTPHONE_MODE"] = "observable"
    os.environ.pop("LLM_SMARTPHONE_MINE_DEMOS", None)
    if hints_on:
        os.environ["LLM_SMARTPHONE_EXPLORED_HINTS"] = "1"
        os.environ["LLM_SMARTPHONE_EXPLORED_STORE"] = store
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
        ctx.backend.wake_and_unlock(); ctx.backend.press_button("HOME"); time.sleep(0.8)
    except Exception:
        pass
    auth = _token()
    t0 = time.monotonic()
    r = loop.run(task=task, system_prompt=sp,
                 authorization=f"Bearer {auth}" if auth else None)
    return r.get("outcome"), r.get("turns"), round(time.monotonic() - t0, 1), len(hints)


def main() -> int:
    os.environ.setdefault("ANDROID_SERIAL", "35091FDH2002ZN")
    os.environ.setdefault("LLM_SMARTPHONE_MAX_TOOL_CALLS", "18")
    os.environ.setdefault("LLM_STUDIO_TOKEN", _token())
    task = sys.argv[1] if len(sys.argv) > 1 else "turn on battery saver"
    store = sys.argv[2] if len(sys.argv) > 2 else "experiments/results/rag_demos/authored_battery_saver.json"
    reps = int(sys.argv[3]) if len(sys.argv) > 3 else 3
    print(f"TASK: {task}\nSTORE: {store}\n", flush=True)
    rows = []
    for arm, hints_on in (("none", False), ("authored", True)):
        for rep in range(1, reps + 1):
            _env(hints_on, store)
            o, t, s, h = _run(task)
            rows.append((arm, o, t, s, h))
            print(f"[{arm} r{rep}] outcome={o} turns={t} secs={s} hints={h}", flush=True)
    print("\n### DECISIVE SUMMARY ###", flush=True)
    for arm in ("none", "authored"):
        a = [r for r in rows if r[0] == arm]
        done = sum(1 for _x, o, _t, _s, _h in a if o in ("done", "done_fast", "done_replay"))
        print(f"  {arm:<9} done {done}/{len(a)}  hints={a[0][4]}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

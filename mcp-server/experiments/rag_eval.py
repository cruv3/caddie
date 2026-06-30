"""3-arm RAG eval on a real device for HARDER tasks.

Per task:
  0. MINE a rich targeted demo: run with mining on (hints off, high budget) until
     one success; the run's labeled tap path is saved as a demo (arm C knowledge).
  A. none    -- EXPLORED_HINTS=0 (baseline)
  B. generic -- generic crawl store (phase1c_explored.json)
  C. target  -- the freshly mined demo for THIS task
Each arm run N times; report outcome/turns/time. Resolvers + skill-replay OFF and
mode=observable so only the injected knowledge varies.

Run it yourself (real phone must be connected):
  cd mcp-server
  ANDROID_SERIAL=35091FDH2002ZN LLM_STUDIO_ENDPOINT=http://100.92.159.57:8800/api/v1/chat \
  LLM_STUDIO_MODEL=qwen3.6 .venv/Scripts/python.exe experiments/rag_eval.py
"""
import os
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

HERE = Path(__file__).resolve().parent
GENERIC = str(HERE / "results" / "phase1c_explored.json")
DEMO_DIR = HERE / "results" / "rag_demos"
DEMO_DIR.mkdir(parents=True, exist_ok=True)

TASKS = [
    "turn on battery saver",
    "turn on do not disturb",
    "open accessibility settings and turn on color inversion",
]
REPS = 2
MINE_ATTEMPTS = 2


def _token() -> str:
    if os.environ.get("LLM_STUDIO_TOKEN"):
        return os.environ["LLM_STUDIO_TOKEN"]
    lp = Path(__file__).resolve().parents[2] / "local.properties"
    if lp.exists():
        for line in lp.read_text(encoding="utf-8", errors="replace").splitlines():
            if line.strip().startswith("LM_STUDIO_TOKEN="):
                return line.split("=", 1)[1].strip()
    return ""


def _base_env():
    os.environ["LLM_SMARTPHONE_SEMANTIC_MATCH"] = "1"
    os.environ["LLM_SMARTPHONE_CLOCK_RESOLVER"] = "0"
    os.environ["LLM_SMARTPHONE_SKILL_REPLAY"] = "0"
    os.environ["LLM_SMARTPHONE_MODE"] = "observable"
    os.environ.setdefault("LLM_SMARTPHONE_MAX_TOOL_CALLS", "18")


def _set_knowledge(hints_on: bool, store: str | None, mine: bool, mine_store: str | None):
    if mine:
        os.environ["LLM_SMARTPHONE_MINE_DEMOS"] = "1"
        os.environ["LLM_SMARTPHONE_EXPLORED_STORE"] = mine_store
        os.environ["LLM_SMARTPHONE_EXPLORED_HINTS"] = "0"  # don't feed while mining
        return
    os.environ.pop("LLM_SMARTPHONE_MINE_DEMOS", None)
    if hints_on:
        os.environ["LLM_SMARTPHONE_EXPLORED_HINTS"] = "1"
        os.environ["LLM_SMARTPHONE_EXPLORED_STORE"] = store
    else:
        os.environ["LLM_SMARTPHONE_EXPLORED_HINTS"] = "0"
        os.environ.pop("LLM_SMARTPHONE_EXPLORED_STORE", None)


def _run(task: str) -> dict:
    """One run with the CURRENT env. Fresh context so the index reflects env."""
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
    return {"outcome": res.get("outcome"), "turns": res.get("turns"),
            "secs": round(time.monotonic() - t0, 1), "hints": len(hints)}


def _slug(task: str) -> str:
    return "".join(c if c.isalnum() else "_" for c in task)[:40]


def main() -> int:
    os.environ.setdefault("ANDROID_SERIAL", "35091FDH2002ZN")
    if not os.environ.get("LLM_STUDIO_TOKEN"):
        os.environ["LLM_STUDIO_TOKEN"] = _token()
    _base_env()

    results = []  # (task, arm, outcome, turns, secs, hints)
    for task in TASKS:
        print(f"\n##### TASK: {task} #####", flush=True)
        demo_store = str(DEMO_DIR / f"{_slug(task)}.json")
        Path(demo_store).unlink(missing_ok=True)

        # 0. mine a rich demo (until one success)
        mined = False
        for attempt in range(1, MINE_ATTEMPTS + 1):
            _set_knowledge(False, None, mine=True, mine_store=demo_store)
            r = _run(task)
            print(f"[mine {attempt}] outcome={r['outcome']} turns={r['turns']}", flush=True)
            if r["outcome"] in ("done", "done_fast", "done_replay") and Path(demo_store).exists():
                mined = True
                break
        print(f"[mine] {'OK demo saved' if mined else 'NO success -> arm C skipped'}", flush=True)

        # A/B/C
        arms = [("none", False, None), ("generic", True, GENERIC)]
        if mined:
            arms.append(("target", True, demo_store))
        for arm, hints_on, store in arms:
            for rep in range(1, REPS + 1):
                _set_knowledge(hints_on, store, mine=False, mine_store=None)
                r = _run(task)
                print(f"[{arm} r{rep}] outcome={r['outcome']} turns={r['turns']} "
                      f"secs={r['secs']} hints={r['hints']}", flush=True)
                results.append((task, arm, r["outcome"], r["turns"], r["secs"], r["hints"]))

    # summary
    print("\n\n########## RAG EVAL SUMMARY ##########", flush=True)
    print(f"{'task':<42} {'arm':<8} {'reps':>4} {'done':>4} {'avg_turns':>9} {'hints':>5}", flush=True)
    from statistics import mean
    seen = {}
    for (task, arm, *_x) in results:
        seen.setdefault((task, arm), []).append(_x)
    for (task, arm), rows in seen.items():
        done = sum(1 for o, _t, _s, _h in rows if o in ("done", "done_fast", "done_replay"))
        at = round(mean(t for _o, t, _s, _h in rows), 1)
        hn = rows[0][3]
        print(f"{task[:42]:<42} {arm:<8} {len(rows):>4} {done:>4} {at:>9} {hn:>5}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

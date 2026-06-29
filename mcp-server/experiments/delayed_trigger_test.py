"""Focused test of TIME-BASED triggering: schedule tasks at a future next_fire and
let the scheduler tick fire them. Tracks each by its task ID (not text) and reports
how long after scheduling it actually fired."""
import os
import sys
import time
from datetime import datetime, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


def _load_token() -> None:
    if os.environ.get("LLM_STUDIO_TOKEN"):
        return
    for lp in (Path(__file__).resolve().parents[2] / "local.properties",
               Path(__file__).resolve().parents[1] / "local.properties"):
        if lp.exists():
            for line in lp.read_text(encoding="utf-8", errors="replace").splitlines():
                if line.strip().startswith("LM_STUDIO_TOKEN="):
                    os.environ["LLM_STUDIO_TOKEN"] = line.split("=", 1)[1].strip()
                    return


def main() -> int:
    os.environ.setdefault("ANDROID_SERIAL", "emulator-5554")
    os.environ.setdefault("LLM_SMARTPHONE_MAX_TOOL_CALLS", "15")
    _load_token()

    from caddie.agent import AgentHttpServer  # noqa: F401
    from caddie.agent.agent_loop import AgentLoop
    from caddie.agent.lmstudio import LmStudioClient
    from caddie.agent.scheduler import Scheduler
    from caddie.context import ServerContext

    ctx = ServerContext()
    loop = AgentLoop(ctx, LmStudioClient())
    sched = Scheduler(ctx.schedule_store, loop, ctx.backend, ctx.events)

    # unique texts + future fire times
    plan = [
        ("open the Settings app to verify delayed trigger", 12),
        ("open the YouTube app to verify delayed trigger", 20),
    ]
    added = []
    t0 = time.monotonic()
    for text, delay in plan:
        st = ctx.schedule_store.add(
            text, datetime.now().astimezone() + timedelta(seconds=delay), None, None)
        added.append((st.id, text, delay))
        print(f"[delayed] scheduled {st.id} +{delay}s :: {text}", flush=True)

    # drive ticks; record when each id leaves 'scheduled'
    fired = {}
    deadline = time.monotonic() + 60 + max(d for _, d in plan) + 180
    while time.monotonic() < deadline and len(fired) < len(added):
        sched.tick()
        for (sid, text, delay) in added:
            if sid in fired:
                continue
            cur = next((x for x in ctx.schedule_store.list() if x.id == sid), None)
            if cur and cur.status != "scheduled":
                fired[sid] = (cur.status, (cur.last_run or {}).get("outcome"),
                              round(time.monotonic() - t0, 1), delay)
                print(f"[delayed] {sid} fired: status={cur.status} "
                      f"outcome={fired[sid][1]} after={fired[sid][2]}s (sched +{delay}s)",
                      flush=True)
        time.sleep(3)

    print("\n########## DELAYED TRIGGER SUMMARY ##########", flush=True)
    for (sid, text, delay) in added:
        if sid in fired:
            status, outcome, after, d = fired[sid]
            late = after - d
            print(f"  {sid} +{d}s -> fired after {after}s (late {late:+.1f}s) "
                  f"status={status} outcome={outcome}", flush=True)
        else:
            print(f"  {sid} +{delay}s -> NEVER FIRED (bug)", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

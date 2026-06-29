"""Live end-to-end dry-run of the scheduled-task feature on a connected device.

Drives the real Scheduler -> fire() -> wake_and_unlock -> agent_loop.run (real LLM)
-> report path, without the HTTP server (so no getevent touch-watcher gotcha).

Run it yourself (the LLM call goes to your arbiter, so it needs your network +
token):

    cd mcp-server
    ANDROID_SERIAL=emulator-5554 \
    LLM_STUDIO_ENDPOINT=http://100.92.159.57:8800/api/v1/chat \
    LLM_STUDIO_MODEL=qwen3.6 \
    PYTHONPATH="$(pwd)" \
    .venv/Scripts/python.exe experiments/scheduled_dryrun.py "open the Clock app"

LLM_STUDIO_TOKEN is read from local.properties if not already in the env (the
scheduler turns it into the "Bearer <token>" header the unattended run needs).
Pass a task as argv[1]; default is a benign "open the Clock app".
"""
import os
import sys
import threading
import time
from datetime import datetime
from pathlib import Path

# Make `caddie` importable without needing PYTHONPATH set (mcp-server is parents[1]).
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


def _load_token() -> None:
    if os.environ.get("LLM_STUDIO_TOKEN"):
        return
    # repo root is the parent of mcp-server
    for lp in (Path(__file__).resolve().parents[2] / "local.properties",
               Path(__file__).resolve().parents[1] / "local.properties"):
        if lp.exists():
            for line in lp.read_text(encoding="utf-8", errors="replace").splitlines():
                if line.strip().startswith("LM_STUDIO_TOKEN="):
                    os.environ["LLM_STUDIO_TOKEN"] = line.split("=", 1)[1].strip()
                    print("[dryrun] loaded LLM_STUDIO_TOKEN from local.properties", flush=True)
                    return


def main() -> int:
    task = sys.argv[1] if len(sys.argv) > 1 else "open the Clock app"
    os.environ.setdefault("ANDROID_SERIAL", "emulator-5554")
    _load_token()

    from caddie.context import ServerContext
    from caddie.agent.agent_loop import AgentLoop
    from caddie.agent.lmstudio import LmStudioClient
    from caddie.agent.scheduler import Scheduler

    ctx = ServerContext()
    loop = AgentLoop(ctx, LmStudioClient())
    sched = Scheduler(ctx.schedule_store, loop, ctx.backend, ctx.events)

    # Live event echo so you can watch what the agent does + the final report.
    def watch():
        with ctx.events.subscription() as q:
            while True:
                ev = q.get()
                if ev is None:
                    continue
                if ev.type == "scheduled_task_report":
                    print(f"[REPORT] {ev.payload}", flush=True)
                elif ev.type in ("tool_call_started", "task_started",
                                 "task_finished", "confirmation_required"):
                    print(f"[{ev.type}] tool={ev.tool} args={ev.args} payload={ev.payload}",
                          flush=True)
    threading.Thread(target=watch, daemon=True).start()

    now = datetime.now().astimezone()
    t = ctx.schedule_store.add(task, now, None, None)  # one-off, due now, no pre_auth
    print(f"[dryrun] scheduled {t.id!r}: {task!r} due {t.next_fire}", flush=True)

    sched.tick()  # fires the due task synchronously via fire() -> run()
    time.sleep(1.0)

    final = next((x for x in ctx.schedule_store.list() if x.id == t.id), None)
    print(f"\n[dryrun] final status={final.status!r}", flush=True)
    print(f"[dryrun] last_run={final.last_run}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

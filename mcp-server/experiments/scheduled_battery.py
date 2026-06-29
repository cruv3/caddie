"""Exploratory battery: run ~20 varied tasks through the real scheduler+agent on a
connected device. Mix of immediate-fire and DELAYED (future next_fire, fired by
the scheduler tick) to exercise time-based triggering. Prints a summary table.

Run it yourself (LLM goes to your arbiter):
    cd mcp-server
    ANDROID_SERIAL=emulator-5554 LLM_STUDIO_ENDPOINT=http://100.92.159.57:8800/api/v1/chat \
    LLM_STUDIO_MODEL=qwen3.6 LLM_SMARTPHONE_MAX_TOOL_CALLS=15 \
    .venv/Scripts/python.exe experiments/scheduled_battery.py
"""
import os
import sys
import time
from datetime import datetime, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

# (task, mode) where mode is "now" or "delay:<seconds>"
TASKS = [
    ("open the Clock app", "now"),
    ("open the Settings app", "now"),
    ("open the Calculator app", "now"),
    ("open Chrome", "now"),
    ("turn on dark mode", "now"),
    ("turn on battery saver", "now"),
    ("set the screen brightness to 50%", "now"),
    ("open Wi-Fi settings", "now"),
    ("open Bluetooth settings", "now"),
    ("create a 3 minute timer", "now"),
    ("set an alarm for 8:00 AM", "now"),
    ("search Google for the weather in Munich", "now"),
    ("open YouTube", "now"),
    ("open the notification shade", "now"),
    ("take a screenshot", "now"),
    ("open the camera app", "now"),
    ("set the screen timeout to 30 seconds", "now"),
    ("open the Maps app", "now"),
    ("open the Clock app", "delay:15"),
    ("open the Calculator app", "delay:20"),
]


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

    from caddie.agent import AgentHttpServer  # noqa: F401  (prime import order)
    from caddie.agent.agent_loop import AgentLoop
    from caddie.agent.lmstudio import LmStudioClient
    from caddie.agent.scheduler import Scheduler
    from caddie.context import ServerContext

    ctx = ServerContext()
    loop = AgentLoop(ctx, LmStudioClient())
    sched = Scheduler(ctx.schedule_store, loop, ctx.backend, ctx.events)

    results = []
    for i, (task, mode) in enumerate(TASKS, 1):
        try:
            ctx.backend.press_button("HOME")  # clean-ish start between tasks
            time.sleep(0.8)
        except Exception:
            pass
        print(f"\n===== [{i}/{len(TASKS)}] mode={mode} :: {task} =====", flush=True)
        t0 = time.monotonic()
        fired_after = 0.0
        try:
            if mode == "now":
                ctx.schedule_store.add(task, datetime.now().astimezone(), None, None)
                sched.tick()
            else:
                delay = int(mode.split(":", 1)[1])
                ctx.schedule_store.add(task, datetime.now().astimezone() + timedelta(seconds=delay),
                                       None, None)
                # poll the tick loop until the scheduler fires it (time-based trigger)
                deadline = time.monotonic() + delay + 120
                while time.monotonic() < deadline:
                    sched.tick()
                    cur = next((x for x in ctx.schedule_store.list() if x.task == task
                                and x.status in ("done", "failed", "missed")), None)
                    if cur is not None:
                        fired_after = time.monotonic() - t0
                        break
                    time.sleep(5)
        except Exception as exc:
            results.append((i, mode, task, f"EXC:{type(exc).__name__}", 0, time.monotonic() - t0, 0.0))
            print(f"[BATTERY] EXCEPTION: {exc}", flush=True)
            continue

        wall = time.monotonic() - t0
        # most-recent terminal entry for this task text
        entries = [x for x in ctx.schedule_store.list() if x.task == task]
        last = entries[-1] if entries else None
        outcome = (last.last_run or {}).get("outcome", last.status if last else "?")
        steps = len((last.last_run or {}).get("steps", [])) if last else 0
        results.append((i, mode, task, outcome, steps, wall, fired_after))
        print(f"[BATTERY] outcome={outcome} steps={steps} wall={wall:.0f}s "
              f"fired_after={fired_after:.0f}s", flush=True)

    # ---- summary ----
    print("\n\n########## BATTERY SUMMARY ##########", flush=True)
    print(f"{'#':>2} {'mode':<9} {'outcome':<14} {'steps':>5} {'wall':>6} {'fired':>6}  task", flush=True)
    from collections import Counter
    oc = Counter()
    for (i, mode, task, outcome, steps, wall, fired) in results:
        oc[outcome] += 1
        print(f"{i:>2} {mode:<9} {outcome:<14} {steps:>5} {wall:>5.0f}s {fired:>5.0f}s  {task}",
              flush=True)
    print("\n--- outcome counts ---", flush=True)
    for k, v in oc.most_common():
        print(f"  {k}: {v}", flush=True)
    done = oc.get("done", 0)
    print(f"\nSUCCESS (done): {done}/{len(results)}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

"""Dual-mode eval, IN-PROCESS (no server.py / no 8787 port) — fast (deep-link)
vs observable (UI) on the settings battery. Re-run after this session's fixes.
Success = agent outcome (the screenshot verifier confirms the right page)."""
import os
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

SERIAL = "35091FDH2002ZN"
REPS = 2
TASKS = [
    ("display",       "open the display settings"),
    ("sound",         "open the sound and vibration settings"),
    ("notifications", "open the notification settings"),
    ("accessibility", "open the accessibility settings"),
    ("storage",       "show how much storage is used"),
    ("battery_saver", "open the battery saver settings"),
    ("date",          "open the date and time settings"),
    ("language",      "open the language settings"),
]
OK = ("done", "done_fast", "done_replay")


def adb(*a):
    subprocess.run(["adb", "-s", SERIAL, "shell", *a], capture_output=True, text=True)


def _token() -> str:
    if os.environ.get("LLM_STUDIO_TOKEN"):
        return os.environ["LLM_STUDIO_TOKEN"]
    lp = Path(__file__).resolve().parents[2] / "local.properties"
    if lp.exists():
        for line in lp.read_text(encoding="utf-8", errors="replace").splitlines():
            if line.strip().startswith("LM_STUDIO_TOKEN="):
                return line.split("=", 1)[1].strip()
    return ""


def main() -> int:
    os.environ.setdefault("ANDROID_SERIAL", SERIAL)
    os.environ.setdefault("LLM_SMARTPHONE_MAX_TOOL_CALLS", "28")
    os.environ["LLM_SMARTPHONE_SEMANTIC_MATCH"] = "1"
    os.environ["LLM_SMARTPHONE_SKILL_REPLAY"] = "0"
    os.environ["LLM_SMARTPHONE_EXPLORED_HINTS"] = "0"
    os.environ.pop("LLM_SMARTPHONE_MINE_DEMOS", None)
    auth = _token()

    import caddie.agent  # noqa: F401
    from caddie.context import ServerContext
    from caddie.agent.agent_loop import AgentLoop
    from caddie.agent.lmstudio import LmStudioClient
    from caddie.agent.prompt import build_system_prompt
    ctx = ServerContext()
    loop = AgentLoop(ctx, LmStudioClient())

    res = {"observable": {}, "fast": {}}
    for mode in ("observable", "fast"):
        os.environ["LLM_SMARTPHONE_MODE"] = mode
        print(f"===== MODE={mode} =====", flush=True)
        for name, query in TASKS:
            runs = []
            for _ in range(REPS):
                adb("am", "force-stop", "com.android.settings")
                adb("input", "keyevent", "KEYCODE_HOME")
                time.sleep(1.2)
                try:
                    ctx.backend.wake_and_unlock()
                except Exception:
                    pass
                sp = build_system_prompt([], None, hints=[], mode=mode)
                t0 = time.monotonic()
                r = loop.run(task=query, system_prompt=sp,
                             authorization=f"Bearer {auth}" if auth else None)
                runs.append((r.get("outcome"), r.get("turns") or 0, round(time.monotonic() - t0, 1)))
            ok = sum(1 for o, _t, _s in runs if o in OK)
            at = round(sum(t for _o, t, _s in runs) / len(runs), 1)
            asec = round(sum(s for _o, _t, s in runs) / len(runs), 1)
            res[mode][name] = {"ok": ok, "turns": at, "secs": asec}
            print(f"  [{mode}] {name}: {ok}/{REPS} turns={at} {asec:.0f}s", flush=True)

    # table
    lines = ["# Dual-mode eval (in-process) — fast (deep-link) vs observable (UI)", "",
             f"device={SERIAL}, reps={REPS}, replay OFF, after the session's fixes.", "",
             "| task | obs ok | obs turns | obs s | fast ok | fast turns | fast s |",
             "|---|---|---|---|---|---|---|"]
    ot = ft = osec = fsec = ook = fok = 0
    for name, _ in TASKS:
        o = res["observable"][name]; f = res["fast"][name]
        lines.append(f"| {name} | {o['ok']}/{REPS} | {o['turns']} | {o['secs']:.0f} | "
                     f"{f['ok']}/{REPS} | {f['turns']} | {f['secs']:.0f} |")
        ot += o["turns"]; ft += f["turns"]; osec += o["secs"]; fsec += f["secs"]
        ook += o["ok"]; fok += f["ok"]
    n = len(TASKS)
    lines += ["", f"**Totals:** observable {ook}/{n * REPS} ok, {ot / n:.1f} avg turns, {osec / n:.0f}s avg "
              f"· fast {fok}/{n * REPS} ok, {ft / n:.1f} avg turns, {fsec / n:.0f}s avg",
              f"**Delta (fast vs observable):** turns {(ft - ot) / ot * 100:+.0f}%, time {(fsec - osec) / osec * 100:+.0f}%"]
    out = Path(__file__).resolve().parents[1] / "experiments" / "results" / "dualmode_inproc.md"
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines), flush=True)
    print(f"wrote {out}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

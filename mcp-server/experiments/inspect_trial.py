"""Quick trial-result inspector. Reads last (or named) result and prints tool trace."""
import json
import sys
from pathlib import Path


def main() -> None:
    results = sorted((Path(__file__).resolve().parent / "results").glob("*.json"))
    if not results:
        print("Keine Trials gefunden.")
        return
    if len(sys.argv) > 1:
        target = Path(__file__).resolve().parent / "results" / f"{sys.argv[1]}.json"
    else:
        target = results[-1]
    d = json.loads(target.read_text(encoding="utf-8"))
    print(f"Trial:   {d['trial_id']}")
    print(f"Outcome: {d['outcome']}")
    print(f"Timing:  {d['timing']}")
    print()
    print("Tool-Call-Sequenz:")
    seq = 0
    for e in d["events"]:
        t = e.get("type")
        if t == "tool_call_started":
            seq += 1
            args = e.get("args") or {}
            args_short = {k: v for k, v in args.items() if k in ("x", "y", "why", "app_id", "text", "key", "as_image", "skill_id", "message", "reason")}
            print(f"  {seq:2d}. {e.get('tool')}  {args_short}")
        elif t == "task_started":
            print(f"  -> task_started: {e.get('task','')[:80]}")
        elif t == "task_finished":
            print(f"  -> task_finished ok={e.get('ok')} payload={e.get('payload')}")
    print()
    lm = (d.get("lmstudio_response") or {}).get("lmstudio") or {}
    print(f"LM status: {lm.get('status')}")
    resp = lm.get("response") or ""
    if resp:
        print(f"LM final reply (first 500 chars):")
        print(resp[:500])


if __name__ == "__main__":
    main()

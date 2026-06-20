"""Cheap-assert skill replay.

A skill's recorded steps are replayed WITHOUT an LLM call per step — the big
speed win for known tasks. Each step resolves its target semantically (find an
element by its recorded label) which doubles as a cheap assertion: if the
expected element is not on screen, the replay ABORTS and the LLM agent loop
takes over from the current state. Completion is still verified afterwards
(always), so a drifting replay is caught.

Two halves:
- record_step(): turn an executed tool call into a replayable step (semantic
  label for taps, captured from the live element list).
- replay(): execute a list of steps with per-step resolution + abort-to-LLM.
"""
from __future__ import annotations

from dataclasses import dataclass

from caddie.android.settle import baseline_hash, settle_after

# Tools that change state and are worth replaying. Observation/terminal/meta
# tools (list_elements, screenshot*, done, failed, save_skill, get_skill_*,
# ask_user) are intentionally NOT recorded.
_SETTLE_KEY = {
    "open_app": "open_app", "tap": "tap", "tap_xy": "tap", "long_press_xy": "tap",
    "scroll": "swipe", "press": "key", "type": "text", "open_url": "open_app",
    "open_quick_settings": "open_app", "open_notifications": "open_app",
    "collapse": "tap", "open_app_drawer": "open_app",
}


def record_step(name: str, args: dict, last_elements: list[dict]) -> dict | None:
    """Map an executed tool call to a replayable step, or None to skip it."""
    if name == "smartphone_open_app":
        return {"action": "open_app", "package": args.get("package_name", "")}
    if name == "smartphone_open_url":
        return {"action": "open_url", "url": args.get("url", "")}
    if name == "smartphone_tap_element":
        idx = args.get("index")
        label = ""
        for el in last_elements or []:
            if el.get("index") == idx:
                label = (el.get("text") or el.get("content_description")
                         or el.get("resource_id") or "")
                break
        return {"action": "tap", "label": label, "index": idx}
    if name == "smartphone_tap_coordinates":
        return {"action": "tap_xy", "x": args.get("x"), "y": args.get("y")}
    if name == "smartphone_long_press_coordinates":
        return {"action": "long_press_xy", "x": args.get("x"), "y": args.get("y")}
    if name == "smartphone_scroll":
        return {"action": "scroll", "direction": args.get("direction", "down"),
                "amount": args.get("amount", 0.6)}
    if name == "smartphone_press_button":
        return {"action": "press", "button": args.get("button", "")}
    if name == "smartphone_type_text":
        return {"action": "type", "text": args.get("text", ""),
                "submit": bool(args.get("submit", False))}
    if name in ("smartphone_open_quick_settings", "smartphone_open_notifications",
                "smartphone_collapse", "smartphone_open_app_drawer"):
        return {"action": name[len("smartphone_"):]}
    return None


@dataclass
class ReplayResult:
    ok: bool                 # all steps executed (does NOT mean task verified)
    steps_done: int
    reason: str = ""


def _find_by_label(elements: list[dict], label: str) -> dict | None:
    if not label:
        return None
    want = label.casefold()
    # Collect candidates: exact text match first, else substring across
    # text/desc/resource_id.
    cands = [el for el in elements if (el.get("text") or "").casefold() == want]
    if not cands:
        cands = [
            el for el in elements
            if want in " ".join(str(el.get(k, "")) for k in
                                ("text", "content_description", "resource_id")).casefold()
        ]
    if not cands:
        return None
    # Prefer a CLICKABLE candidate — a label like "Dunkles Design" matches both
    # the static text and the tappable row/toggle; tapping the text does nothing,
    # tapping the clickable element actually triggers it.
    for el in cands:
        if el.get("clickable"):
            return el
    return cands[0]


def _resilient_start(steps, backend, log) -> int:
    """Resilient forward-replay: don't blindly restart from step 0. Find the
    FURTHEST step whose tap-target is already on screen and start there, so an
    agent already deep in the flow (e.g. already on the target page) doesn't
    navigate out and back in. Steps before it are assumed to be navigation that
    is already done. Falls back to 0 (full replay) when nothing resolves."""
    try:
        els = backend.list_elements().get("elements", [])
    except Exception:
        return 0
    for j in range(len(steps) - 1, -1, -1):
        if steps[j].get("action") != "tap":
            continue  # only on-screen tap targets are detectable
        if _find_by_label(els, steps[j].get("label", "")) is not None:
            if j > 0:
                log(f"resilient start: step {j + 1}/{len(steps)} target already "
                    f"on screen -> skipping {j} navigation step(s)")
            return j
    return 0


def replay(steps, backend, log=lambda _m: None) -> ReplayResult:
    """Execute steps with cheap per-step assertion. Aborts (ok=False) the moment
    a tap target can't be resolved -> caller hands off to the LLM loop.

    Starts from the furthest already-satisfied step (see _resilient_start) so we
    never leave a screen we're already on just to navigate back to it."""
    start = _resilient_start(steps, backend, log)
    for i in range(start, len(steps)):
        step = steps[i]
        action = step.get("action")
        key = _SETTLE_KEY.get(action, "tap")
        baseline = baseline_hash(backend)
        try:
            if action == "open_app":
                backend.open_app(step.get("package", ""))
            elif action == "open_url":
                backend.open_url(step.get("url", ""))
            elif action == "tap":
                label = step.get("label", "")
                els = backend.list_elements().get("elements", [])
                match = _find_by_label(els, label)
                if match is None:
                    return ReplayResult(False, i, f"label not found: {label!r}")
                backend.tap_element(match["index"])
            elif action == "tap_xy":
                backend.tap(step.get("x"), step.get("y"))
            elif action == "long_press_xy":
                backend.long_press(step.get("x"), step.get("y"))
            elif action == "scroll":
                backend.scroll(step.get("direction", "down"), step.get("amount", 0.6))
            elif action == "press":
                backend.press_button(step.get("button", ""))
            elif action == "type":
                backend.type_text(step.get("text", ""), bool(step.get("submit", False)))
            elif action == "open_quick_settings":
                backend.open_quick_settings()
            elif action == "open_notifications":
                backend.open_notifications()
            elif action == "collapse":
                backend.collapse_panels()
            elif action == "open_app_drawer":
                backend.open_app_drawer()
            else:
                return ReplayResult(False, i, f"unknown step action: {action!r}")
        except Exception as exc:
            return ReplayResult(False, i, f"step {action} failed: {exc}")
        settle_after(backend, baseline, key)
        log(f"replay step {i + 1}/{len(steps)}: {action} ok")
    return ReplayResult(True, len(steps))

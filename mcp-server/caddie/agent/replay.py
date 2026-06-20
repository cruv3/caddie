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
        el = next((e for e in (last_elements or []) if e.get("index") == idx), None)
        if el is None:
            return {"action": "tap", "label": "", "index": idx}
        # Rich element identity so replay can re-find the SAME element (not just
        # any text match): resource-id + class + clickable + bounds, not only text.
        return {
            "action": "tap", "index": idx,
            "label": (el.get("text") or el.get("content_description")
                      or el.get("resource_id") or ""),
            "text": el.get("text", ""),
            "desc": el.get("content_description", ""),
            "resource_id": el.get("resource_id", ""),
            "cls": el.get("class", ""),
            "clickable": bool(el.get("clickable")),
            "bounds": el.get("bounds"),
        }
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


def _score(step: dict, el: dict) -> int:
    """How well a current element matches the recorded tap descriptor."""
    s = 0
    rid = step.get("resource_id") or ""
    if rid and el.get("resource_id") == rid:
        s += 3
    cls = step.get("cls") or ""
    if cls and el.get("class") == cls:
        s += 2
    txt = (step.get("text") or "").casefold()
    if txt and (el.get("text") or "").casefold() == txt:
        s += 3
    desc = (step.get("desc") or "").casefold()
    if desc and (el.get("content_description") or "").casefold() == desc:
        s += 2
    # substring fallback (also covers legacy steps that only stored "label")
    lbl = (step.get("label") or "").casefold()
    if lbl:
        hay = " ".join(str(el.get(k, "")) for k in
                       ("text", "content_description", "resource_id")).casefold()
        if lbl in hay:
            s += 1
    if step.get("clickable") and el.get("clickable"):
        s += 1
    return s


def _find_element(step: dict, elements: list[dict]) -> dict | None:
    """Re-find the recorded element by scoring resource-id + class + text + desc
    + clickable. Ties broken by clickable, then bounds proximity to the recorded
    position (handles e.g. multiple identical 'switch_widget' toggles)."""
    scored = [(_score(step, el), el) for el in elements]
    scored = [(s, el) for s, el in scored if s > 0]
    if not scored:
        return None
    best = max(s for s, _ in scored)
    cands = [el for s, el in scored if s == best]
    if len(cands) == 1:
        return cands[0]
    pool = [el for el in cands if el.get("clickable")] or cands
    rb = step.get("bounds") or {}
    if rb.get("center_x") is not None:
        rx, ry = rb["center_x"], rb["center_y"]
        pool.sort(key=lambda el: (
            abs((el.get("bounds") or {}).get("center_x", 0) - rx)
            + abs((el.get("bounds") or {}).get("center_y", 0) - ry)))
    return pool[0]


def _find_by_label(elements: list[dict], label: str) -> dict | None:
    """Legacy label-only matcher (kept for compatibility)."""
    return _find_element({"label": label}, elements)


def _semantic_on_screen(step: dict, elements: list[dict]) -> bool:
    """True only if the step's MEANINGFUL content (visible text or content-desc)
    is present on screen. Used to gate resilient-start: a purely structural match
    (generic resource-id + class + clickable, e.g. android:id/switch_widget) is
    NOT enough to claim "already here" — those generic ids exist on many screens
    and a false skip taps garbage. Requires the semantic label to actually match."""
    text = (step.get("text") or "").casefold()
    desc = (step.get("desc") or "").casefold()
    if not text and not desc:
        return False
    for el in elements:
        if text and (el.get("text") or "").casefold() == text:
            return True
        if desc and (el.get("content_description") or "").casefold() == desc:
            return True
    return False


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
        if _semantic_on_screen(steps[j], els):
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
                els = backend.list_elements().get("elements", [])
                match = _find_element(step, els)
                if match is None:
                    return ReplayResult(False, i, f"element not found: {step.get('label')!r}")
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

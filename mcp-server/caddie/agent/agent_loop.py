"""Der eigene Agent-Tool-Loop.

Frueher fuhr LM Studio den Tool-Loop server-seitig (``integrations``-Array) —
eine Agent-Session war EIN undurchsichtiger HTTP-Call, ohne Eingriffspunkt.
Hier besitzt der Agent den Loop selbst, Turn fuer Turn:

    messages = [system, user]
    while not done:
        antwort = lmstudio.chat_completion(messages, tools)
        wenn tool_calls:
            <-- PAUSE-/INJECT-HOOK -->
            fuer jeden call: in-process ausfuehren, Ergebnis anhaengen
        sonst:
            fertig

LM Studio ist damit nur noch Inferenz-Backend. Der ``_pause_point``-Hook ist in
Block 2 bewusst ein No-op — Block 3/5 (Pause/Resume + Mid-run-Korrektur)
fuellen ihn.
"""

from __future__ import annotations

import json
import os
import re
import time
from typing import Any

from caddie.agent import risk
from caddie.agent import replay as _replay
from caddie.agent.lmstudio import LmStudioClient
from caddie.agent.run_control import RunControl, RunState
from caddie.agent.tool_bridge import ToolCallResult, ToolDispatcher
from caddie.context import ServerContext
from caddie.agent.pre_auth import PreAuth

# Sicherheitsnetze gegen stuck Modelle (vgl. experiments/run_trials.py).
MAX_TOOL_CALLS = int(os.environ.get("LLM_SMARTPHONE_MAX_TOOL_CALLS", "25"))
MAX_TURNS = int(os.environ.get("LLM_SMARTPHONE_MAX_TURNS", "40"))
# Compact the conversation when the estimated prompt exceeds this fraction of
# the model context (env LLM_STUDIO_CONTEXT_LENGTH), leaving room for the reply
# + reasoning tokens. Turn-aware so tool_call/result pairs stay intact.
COMPACT_FRACTION = 0.6
# smartphone_ask_user: how long to wait for the user's spoken answer, and how
# many questions a single run may ask before it must decide on its own.
QUESTION_TIMEOUT_S = 25.0
MAX_QUESTIONS = 3
# Reasoning models occasionally return an empty turn (only reasoning_content,
# no content/tool_calls). Don't treat that as task completion -- nudge and retry.
MAX_EMPTY_TURNS = 3
_EMPTY_TURN_NUDGE = (
    "Your last response was empty (no action and no answer). Do NOT stop. "
    "Either call the next smartphone_* tool to make progress, or call "
    "smartphone_done / smartphone_failed if the task is truly finished."
)
# Completeness verifier (after VLAA-GUI 2026): before smartphone_done is
# accepted, a SEPARATE model call checks the fresh screenshot against the task.
# A "done" may be rejected this many times before the run ends as failed
# (prevents an infinite done -> reject -> done loop).
MAX_VERIFY_REJECTS = 2

# Cheap-assert skill replay: when a matched skill has recorded steps, replay them
# without an LLM call per step, then verify (always). On abort/failed verify the
# normal LLM loop takes over. Off via LLM_SMARTPHONE_SKILL_REPLAY=0.
_REPLAY_ENABLED = os.environ.get("LLM_SMARTPHONE_SKILL_REPLAY", "1") != "0"

# Tools that only OBSERVE the screen (no state change). Used by the Stage-1
# completion gate and the loop breaker to tell "looking" from "acting".
_OBSERVATION_TOOLS = frozenset({
    "smartphone_take_screenshot", "smartphone_list_elements",
})
# Terminal tools — never counted as repeated actions by the loop breaker.
_TERMINAL_TOOLS = frozenset({
    "smartphone_done", "smartphone_failed", "smartphone_save_skill",
})

# Stage-1 completion gate (deterministic, cheaper than the model judge): if the
# agent declares done without a fresh screen observation within the last N
# state-changing calls, reject before spending a judge call.
GATE_MAX_STALE_CALLS = 3

# Loop breaker (after VLAA-GUI's Loop Breaker): escalate when the SAME action
# repeats with no progress. Tiered by how many identical trailing actions:
#   3 identical -> tier 1 nudge ("try a different approach")
#   4 identical -> tier 2 nudge ("go back to home and re-plan")
#   5 identical -> give up (outcome=loop_broken) instead of grinding to the cap.
LOOP_TIER1_AT = 3
LOOP_TIER2_AT = 4
LOOP_GIVEUP_AT = 5
_LOOP_TIER1_NOTE = (
    "LOOP BREAKER (tier 1): you have repeated the exact same action with no "
    "visible change. Do NOT repeat it again. In your next Thought, diagnose why "
    "the screen did not change and try a DIFFERENT approach."
)
_LOOP_TIER2_NOTE = (
    "LOOP BREAKER (tier 2): still stuck on the same action. Abandon this path. "
    "Go back to the home screen (or reopen the relevant app from scratch) and "
    "re-plan from a known state before acting again."
)
# State-based no-progress nudge: the screen has not advanced for several turns
# even though actions were taken. Tells the agent to stop flailing and re-plan.
_STUCK_NOTE = (
    "NO PROGRESS: the screen has not advanced for several turns despite your "
    "actions — you are flailing (varied taps/gestures that go nowhere). STOP. "
    "Re-read the current screen with smartphone_list_elements, identify the ONE "
    "correct next element by its label, and take a single deliberate step. If the "
    "target is not reachable from here, go back to the app's main screen and "
    "navigate deliberately. Do not guess coordinates."
)
# Strict judge prompt: demands direct visual evidence, rejects when in doubt.
_VERIFIER_SYSTEM = (
    "You are a strict completion verifier for a smartphone agent. You are given "
    "a task and ONE current screenshot. Decide whether the task is DIRECTLY and "
    "UNAMBIGUOUSLY visible as completed on this screenshot. Require visible "
    "evidence: a toggle actually ON/OFF, the correct value shown, the right "
    "detail page open. A mere search box or results list, a home screen, an "
    "alarm instead of a timer, or the wrong settings screen does NOT count as "
    "complete. When in doubt: verified=false. The task may be phrased in German; "
    "judge it regardless of language. "
    "Reply with JSON only: {\"verified\": true|false, \"reason\": \"short\"}."
)
# Wie lange der Loop vor einer kritischen Aktion auf die Swipe-Bestaetigung
# wartet. Timeout = abgelehnt (sichere Default).
CONFIRM_TIMEOUT_S = 120.0
# Wie lange ein gerade beendeter Run als "Kontext fuer eine Korrektur" gilt.
# Sagt der Nutzer kurz nach "fertig" z.B. "nimm ein anderes Restaurant",
# bekommt der Folge-Run den vorherigen Auftrag als Kontext mit. Aelter = der
# neue Auftrag startet ohne Bezug.
FOLLOW_UP_MAX_AGE_S = 180.0


class AgentLoop:
    """Faehrt eine komplette Agent-Session und besitzt dabei den Tool-Loop."""

    def __init__(self, context: ServerContext, lmstudio: LmStudioClient) -> None:
        self._dispatcher = ToolDispatcher(context)
        self._lm = lmstudio
        self._events = context.events
        self._backend = context.backend
        self._context = context
        self._tool_specs = self._dispatcher.openai_tool_specs()
        # Zuletzt gesehene list_elements-Ausgabe — Basis fuer die Risiko-
        # Pruefung von Taps (Element unter den Tap-Koordinaten).
        self._last_elements: list[dict] = []
        # Steuer-Objekt des gerade laufenden Runs (None = kein Run aktiv).
        # /control greift hierueber ein.
        self._active_control: RunControl | None = None
        # Voice/wake interaction in progress (phone posts /control intervene):
        # suppresses the getevent watcher's touch auto-resume so the agent does
        # not resume while the user is dictating a correction or saying "stop".
        self._voice_hold = False
        # Kompakter Merker des zuletzt beendeten Runs — Grundlage fuer eine
        # Korrektur NACH "fertig" (Folge-Task mit Kontext, siehe recent_run).
        self._last_run: dict | None = None
        self._init_run_slot()

    def _init_run_slot(self) -> None:
        import threading
        self._run_slot = threading.Lock()
        self._slot_owner = None

    def try_acquire_slot(self) -> bool:
        import threading
        if self._run_slot.acquire(blocking=False):
            self._slot_owner = threading.get_ident()
            return True
        return False

    def release_slot(self) -> None:
        import threading
        if self._slot_owner == threading.get_ident() and self._run_slot.locked():
            self._slot_owner = None
            self._run_slot.release()

    def recent_run(self, max_age_s: float = FOLLOW_UP_MAX_AGE_S) -> dict | None:
        """Der zuletzt beendete Run, falls er juenger als ``max_age_s`` ist —
        sonst None. Damit entscheidet ``/task``, ob ein Folge-Auftrag den
        vorherigen Lauf als Kontext mitbekommt."""
        run = self._last_run
        if not run:
            return None
        if time.monotonic() - run.get("finished_at", 0.0) > max_age_s:
            return None
        return run

    def apply_control(self, action: str, text: str | None = None) -> dict:
        """Wendet ein ``/control``-Signal auf den aktiven Run an.

        ``intervene`` = Pause + Markierung fuer Neu-Wahrnehmung (Touch-
        Erkennung); ``pause`` = stilles Pausieren; ``correct`` = gesprochene
        Nutzer-Korrektur hinterlegen und fortsetzen (Mid-run-Korrektur).
        """
        control = self._active_control
        if control is None:
            return {"ok": False, "error": "no active run"}
        action = (action or "").lower().strip()
        print(f"[control] {action} (paused={control.is_paused}, voice_hold={self._voice_hold})", flush=True)
        if action == "pause":
            control.request_pause()
            self._events.task_paused()
        elif action == "intervene":
            self._voice_hold = True
            control.request_pause(intervention=True)
            self._events.task_paused()
        elif action == "resume":
            self._voice_hold = False
            control.request_resume()
            self._events.task_resumed()
        elif action == "correct":
            self._voice_hold = False
            control.set_correction(text or "")
            control.request_resume()
            self._events.task_resumed()
        elif action == "confirm":
            control.resolve_confirmation(True)
        elif action == "decline":
            control.resolve_confirmation(False)
        elif action == "stop":
            self._voice_hold = False
            control.request_stop()
        elif action == "answer":
            control.provide_answer(text or "")
        else:
            return {"ok": False, "error": f"unknown action: {action}"}
        return {"ok": True, "action": action, "state": control.state.value}

    def pause_for_touch(self) -> bool:
        """Called by the getevent watcher on a real human touch. Pauses the
        active run in-process (no HTTP hop). Returns True if it paused."""
        control = self._active_control
        if control is None or control.stop_requested or control.is_paused:
            return False
        control.request_pause(intervention=True)
        self._events.task_paused()
        return True

    def resume_after_touch(self) -> None:
        """Called by the getevent watcher after the touch goes quiet."""
        control = self._active_control
        if control is None or control.stop_requested or not control.is_paused:
            return
        if self._voice_hold:
            return  # user is dictating (correction/stop) -> don't auto-resume
        control.request_resume()
        self._events.task_resumed()

    def _exec_fast_intent(self, intent: tuple) -> tuple[bool, str]:
        """Execute a matched fast-intent (set_setting/toggle) directly via the
        backend, reusing the whitelisted resolvers. Returns (ok, description)."""
        from caddie.agent.fast_actions import resolve_setting, resolve_toggle
        try:
            if intent[0] == "setting":
                _, key, value = intent
                r = resolve_setting(key, value)
                if not r:
                    return False, ""
                ns, akey, val = r
                self._backend.set_setting(ns, akey, val)
                return True, f"Set {key} to {value}"
            if intent[0] == "toggle":
                _, service, on = intent
                r = resolve_toggle(service, on)
                if not r:
                    return False, ""
                if r[0] == "uimode":
                    self._backend.set_dark_mode(r[1])
                else:
                    _, ns, akey, val = r
                    self._backend.set_setting(ns, akey, val)
                return True, f"Turned {service} {on}"
        except Exception as exc:
            print(f"[fast-intent] exec failed: {exc}", flush=True)
        return False, ""

    def _emit_replay_step(self, step: dict, i: int, total: int) -> None:
        """Surface one replayed step on the event bus (live transparency) so the
        user still sees what is happening even though replay runs without a per-
        step LLM call. 1-based index over the full recorded step list."""
        why = _replay.step_why(step)
        tool = _replay.tool_name(step)
        self._events.agent_step(why, tool=tool, index=i + 1, total=total)

    def run(
        self,
        task: str,
        system_prompt: str,
        authorization: str | None = None,
        model: str | None = None,
        prior: dict | None = None,
        criterion: str | None = None,
        skill=None,
        pre_authorized: "PreAuth | None" = None,
        slot_already_held: bool = False,
    ) -> dict[str, Any]:
        """Faehrt die Session bis done/failed/Abbruch und liefert das Resultat.

        ``prior`` ist optional der zuletzt beendete Run (siehe ``recent_run``):
        wird er mitgegeben, sieht das Modell vor der eigentlichen Aufgabe einen
        kurzen Kontext, was es zuvor getan hat — damit eine Korrektur wie "nimm
        ein anderes Restaurant" Bezug auf den vorigen Lauf hat, obwohl es ein
        frischer Run ohne Conversation-Gedaechtnis ist."""
        acquired_here = False
        if not slot_already_held:
            if not self.try_acquire_slot():
                return {"ok": False, "finished_emitted": False, "outcome": "busy",
                        "tool_calls": 0, "turns": 0, "final_text": "", "error": None,
                        "vision_unsupported": False, "steps": []}
            acquired_here = True
        control = None
        try:
            messages: list[dict] = [
                {"role": "system", "content": system_prompt},
            ]
            if prior:
                messages.append({"role": "user", "content": _prior_context_note(prior)})
            messages.append({"role": "user", "content": task})
            control = RunControl()
            self._active_control = control

            # ── FAST-INTENT RESOLVER (fast mode only) ────────────────────────
            # Deterministically map a parametric settings/toggle task straight to a
            # whitelisted ADB action, bypassing the LLM's tool-choice (which under-
            # adopts set_setting/toggle vs entrenched UI habits like the brightness
            # slider). Only fires on a clear match; else falls through to the loop.
            if (os.environ.get("LLM_SMARTPHONE_MODE", "observable") == "fast"
                    and not control.stop_requested):
                from caddie.agent.fast_actions import match_fast_intent
                _intent = match_fast_intent(task)
                if _intent:
                    _ok, _desc = self._exec_fast_intent(_intent)
                    if _ok:
                        print(f"[fast-intent] {_desc} (0 turns)", flush=True)
                        self._events.task_finished(ok=True, payload={"outcome": "done_fast",
                                                                     "message": _desc})
                        self._active_control = None
                        self._last_run = {"task": task, "outcome": "done_fast",
                                          "final_text": _desc, "finished_at": time.monotonic()}
                        return {"ok": True, "finished_emitted": True, "outcome": "done_fast",
                                "tool_calls": 1, "turns": 0, "final_text": _desc,
                                "error": None, "vision_unsupported": False, "steps": []}

            # ── SKILL REPLAY FAST PATH (cheap-assert) ────────────────────────
            # A matched skill with recorded steps -> replay without a per-step LLM
            # call, then verify (always). On abort or failed verification, fall
            # through to the normal LLM loop from the current screen.
            # Don't fast-path a skill whose recorded steps include a risky action
            # (pay/send/delete/...) — replay bypasses the per-call confirmation gate,
            # so let the LLM loop run it, where risk.classify will require confirm.
            _replay_safe = not any(risk.step_is_risky(s) for s in (getattr(skill, "steps", None) or []))
            if not _replay_safe:
                print("[replay] skip fast-path: skill has a risky step -> LLM loop (gated)", flush=True)
            if (_REPLAY_ENABLED and skill is not None and getattr(skill, "steps", None)
                    and _replay_safe and not control.stop_requested):
                rr = _replay.replay(skill.steps, self._backend,
                                    log=lambda m: print(f"[replay] {m}", flush=True),
                                    on_step=self._emit_replay_step)
                print(f"[replay] done ok={rr.ok} steps={rr.steps_done}/{len(skill.steps)} "
                      f"reason={rr.reason!r}", flush=True)
                _verify_reason = None
                if rr.ok and not control.stop_requested:
                    verdict = self._verify_completion(task, criterion, authorization, model)
                    print(f"[replay] verify verified={verdict.verified} "
                          f"reason={verdict.reason[:160]!r}", flush=True)
                    if verdict.verified:
                        done_message = f"Replayed {rr.steps_done} learned step(s)."
                        self._events.verification_result(True, verdict.reason)
                        self._events.task_finished(ok=True, payload={"outcome": "done_replay",
                                                                     "message": done_message})
                        self._active_control = None
                        self._last_run = {"task": task, "outcome": "done_replay",
                                          "final_text": done_message,
                                          "finished_at": time.monotonic()}
                        return {"ok": True, "finished_emitted": True,
                                "outcome": "done_replay", "tool_calls": rr.steps_done,
                                "turns": 0, "final_text": done_message, "error": None,
                                "vision_unsupported": False, "steps": []}
                    _verify_reason = verdict.reason
                print("[replay] fallback -> LLM loop", flush=True)
                # Hand the LLM a breadcrumb: which steps already ran, where/why replay
                # stopped, and to continue from the current screen (don't redo or undo).
                _note = _replay.fallback_note(skill.steps, rr, _verify_reason)
                messages.append({"role": "user", "content": _note})
                print(f"[replay] fallback note injected:\n{_note}", flush=True)

            tool_calls_made = 0
            recorded_steps: list[dict] = []   # captured for skill replay (on success)
            why_log: list[dict] = []   # {tool, why} per executed action -> report
            verify_rejects = 0
            asks_made = 0
            empty_turns = 0
            done_message = None
            fail_reason = None
            # Loop-breaker + Stage-1-gate state (per run).
            action_sigs: list[str] = []   # signatures of state-changing tool calls
            state_sigs: list[str] = []    # content screen-sig per turn — for no-progress nudge
            calls_since_obs = 0           # actions since the last screen observation
            loop_warned: set[int] = set()  # which loop-breaker tiers already fired
            turns = 0
            outcome = "max_turns"
            final_text = ""
            error: str | None = None
            vision_unsupported = False

            # Latenz-Breakdown (Speed-Analyse): kumulierte Sekunden pro Phase.
            _timing = {"inference": 0.0, "screenshot": 0.0, "tools": 0.0, "verify": 0.0}
            _run_t0 = time.monotonic()

            for turn in range(MAX_TURNS):
                turns = turn + 1

                # ── PAUSE-/STOP-/RESUME-HOOK ──────────────────────────────────
                # Haelt den Loop an, wenn pausiert; bricht ab bei Stop; haengt
                # nach einem menschlichen Eingriff eine Neu-Wahrnehmung an.
                if self._pause_point(messages, control) == "stop":
                    outcome = "stopped_by_user"
                    break

                # ── LOOP BREAKER ──────────────────────────────────────────────
                # Detect the same action repeating with no progress and escalate
                # in tiers instead of grinding into MAX_TOOL_CALLS. Each tier fires
                # at most once; at LOOP_GIVEUP_AT we stop the run.
                repeat = _trailing_repeat(action_sigs)
                if repeat >= LOOP_GIVEUP_AT:
                    outcome = "loop_broken"
                    break
                if repeat >= LOOP_TIER2_AT and LOOP_TIER2_AT not in loop_warned:
                    loop_warned.add(LOOP_TIER2_AT)
                    messages.append({"role": "user", "content": _LOOP_TIER2_NOTE})
                elif repeat >= LOOP_TIER1_AT and LOOP_TIER1_AT not in loop_warned:
                    loop_warned.add(LOOP_TIER1_AT)
                    messages.append({"role": "user", "content": _LOOP_TIER1_NOTE})

                # ── STATE-BASED NO-PROGRESS NUDGE ─────────────────────────────
                # Catches the real fail_loop mode the action breaker above MISSES:
                # the agent emits VARIED actions (different gestures/coords) that go
                # nowhere, so no identical run forms and it burns MAX_TOOL_CALLS.
                # Track a CONTENT-sensitive screen signature; if recent turns cycle
                # among <=2 screens, nudge to re-plan. NUDGE-ONLY (no early give-up):
                # a re-plan hint helps recovery with zero false-failure risk, and the
                # signal (content hash) is imperfect, so we never fail a run on it —
                # MAX_TOOL_CALLS stays the only hard stop. Clearing the window after a
                # nudge rate-limits re-nudging (needs another full stuck window).
                try:
                    els = getattr(self, "_last_elements", None)
                    state_sigs.append(_elements_sig(els) if els else self._backend.ui_hash())
                except Exception:
                    pass
                if _no_progress(state_sigs):
                    # Validated on-device: the nudge fires on real stuck patterns and
                    # can recover the agent (observed: stuck@10 -> nudge -> done@18).
                    print(f"[traj] no-progress nudge at turn {turns}", flush=True)
                    messages.append({"role": "user", "content": _STUCK_NOTE})
                    state_sigs.clear()

                # Keep only the most recent screenshot in context — otherwise
                # vision tokens grow quadratically (every old screen is re-encoded
                # each turn) and slow the model down. The agent perceives the screen
                # fresh every turn anyway; older screenshots are stale.
                _prune_old_images(messages)
                # ── AUTO-COMPACTION ───────────────────────────────────────────
                # Keep the prompt under a fraction of the context so long runs do
                # not overflow it. Drop oldest whole turns first, then shrink the
                # kept window if still over budget.
                budget = self._lm.settings.context_length or 16000
                threshold = int(budget * COMPACT_FRACTION)
                for _keep in (6, 4, 2, 1):
                    if _estimate_tokens(messages) <= threshold:
                        break
                    compacted = _compact_messages(messages, _keep)
                    if len(compacted) < len(messages):
                        messages[:] = compacted
                _t = time.monotonic()
                resp = self._chat_with_cancel(messages, control, authorization, model)
                _dt = time.monotonic() - _t
                _timing["inference"] += _dt
                print(f"[timing] turn {turns}: inference {_dt:.2f}s", flush=True)
                if resp is None:
                    # Cancelled mid-inference by pause/stop/intervention. Don't run
                    # stale calls; stop ends the run, pause/intervention re-plans.
                    if control.stop_requested:
                        outcome = "stopped_by_user"
                        break
                    continue
                if not resp.get("ok"):
                    outcome = "error"
                    error = str(resp.get("error", "chat_completion failed"))
                    vision_unsupported = bool(resp.get("vision_unsupported"))
                    break

                choice = _first_choice(resp.get("response", {}))
                if choice is None:
                    outcome = "error"
                    error = "no choices in response"
                    break
                message = choice.get("message", {}) or {}
                calls = message.get("tool_calls") or []
                messages.append(_assistant_message(message))

                _call_names = [(c.get("function", {}) or {}).get("name", "?") for c in calls]
                _content_len = len(message.get("content") or "")
                _fr = choice.get("finish_reason")
                print(f"[traj] turn {turns}: calls={_call_names or 'NONE'} "
                      f"content_len={_content_len} finish={_fr}", flush=True)

                if not calls:
                    final_text = message.get("content") or ""
                    # Reasoning models (qwen3.6) sometimes emit ONLY reasoning_content
                    # and stop -> empty content AND no tool_calls. That is a degenerate
                    # turn, NOT task completion: nudge the model to act instead of
                    # ending the run with an empty result. Give up only after a few
                    # empty turns in a row.
                    if not final_text.strip():
                        empty_turns += 1
                        print(f"[traj] EMPTY turn {turns} ({empty_turns}/{MAX_EMPTY_TURNS}) "
                              f"-> nudging to continue", flush=True)
                        if empty_turns >= MAX_EMPTY_TURNS:
                            outcome = "stalled"
                            break
                        messages.append({"role": "user", "content": _EMPTY_TURN_NUDGE})
                        continue
                    # Genuine final text reply -> session end.
                    print(f"[traj] STOP turn {turns}: no tool_calls. "
                          f"final_text={final_text[:160]!r}", flush=True)
                    outcome = "stopped"
                    break
                empty_turns = 0

                # ── MID-TURN PAUSE/STOP CHECKPOINT ────────────────────────────
                # The model may have finished thinking just as the user paused /
                # intervened / stopped. Do NOT execute the now-stale tool calls:
                # stop ends the run; pause/intervention re-loops so the next turn
                # re-perceives and re-plans (with the injected note/correction).
                if control.stop_requested:
                    outcome = "stopped_by_user"
                    break
                if control.is_paused or control.has_intervention:
                    # Pair every pending tool_call with a cancelled result first so
                    # the message history stays valid (OpenAI requires one tool
                    # response per tool_call) when we skip these now-stale calls.
                    for _c in calls:
                        messages.append(_tool_message(
                            _c.get("id", ""),
                            ToolCallResult(
                                name=(_c.get("function", {}) or {}).get("name", ""),
                                ok=False,
                                text="Cancelled: the user intervened before this ran.",
                            ),
                        ))
                    if self._pause_point(messages, control) == "stop":
                        outcome = "stopped_by_user"
                        break
                    continue

                terminal = False
                pending_images: list[ToolCallResult] = []
                for idx, call in enumerate(calls):
                    if control.stop_requested:
                        outcome, terminal = "stopped_by_user", True
                        break
                    if control.is_paused:
                        # Pause arrived mid-batch: cancel every remaining (unexecuted)
                        # call so the message history stays paired, then stop and
                        # re-plan next turn -- no further actions fire.
                        for rc in calls[idx:]:
                            messages.append(_tool_message(
                                rc.get("id", ""),
                                ToolCallResult(
                                    name=(rc.get("function", {}) or {}).get("name", ""),
                                    ok=False,
                                    text="Cancelled: the user intervened.",
                                ),
                            ))
                        break
                    if terminal:
                        # A terminal call already succeeded this turn -> only the
                        # silent save_skill may still run; ignore any further action
                        # tools (the prompt forbids them, enforce it here).
                        _fn = call.get("function", {}) or {}
                        if _fn.get("name", "") == "smartphone_save_skill":
                            _res = self._dispatcher.call(
                                "smartphone_save_skill",
                                _parse_arguments(_fn.get("arguments")),
                            )
                            messages.append(_tool_message(call.get("id", ""), _res))
                        continue
                    tool_calls_made += 1
                    fn = call.get("function", {}) or {}
                    name = fn.get("name", "")
                    args = _parse_arguments(fn.get("arguments"))
                    auto_approve_why = None

                    # smartphone_ask_user: pause + wait for the spoken answer
                    if name == "smartphone_ask_user":
                        question = str(args.get("question", "")).strip() or "I need a decision from you."
                        if asks_made >= MAX_QUESTIONS:
                            messages.append(_tool_message(call.get("id", ""), ToolCallResult(
                                name=name, ok=False,
                                text="Question limit reached for this run -- decide yourself and proceed.")))
                            continue
                        asks_made += 1
                        self._events.question_asked(question)
                        answer = control.await_answer(QUESTION_TIMEOUT_S)
                        self._events.question_resolved()
                        if control.stop_requested:
                            outcome, terminal = "stopped_by_user", True
                            messages.append(_tool_message(call.get("id", ""), ToolCallResult(
                                name=name, ok=False, text="Run stopped by user.")))
                            break
                        if answer is None and (control.is_paused or control.has_intervention):
                            # User intervened (pause / voice correction) during the
                            # question -> hand off to the pause/correction flow.
                            messages.append(_tool_message(call.get("id", ""), ToolCallResult(
                                name=name, ok=True, text="Question interrupted by user input.")))
                            continue
                        answer_text = (
                            f'The user answered: "{answer}". Continue accordingly.'
                            if answer else
                            "No answer from the user. Proceed with your best judgment."
                        )
                        messages.append(_tool_message(call.get("id", ""), ToolCallResult(
                            name=name, ok=True, text=answer_text)))
                        continue

                    # ── Mode gate: fast-only tools ───────────────────────────
                    # The deep-link shortcut exists only in FAST mode, so OBSERVABLE
                    # mode is pure UI (clean fast-vs-observable study comparison).
                    if (name in ("smartphone_open_settings", "smartphone_set_setting",
                                 "smartphone_toggle")
                            and os.environ.get("LLM_SMARTPHONE_MODE", "observable") != "fast"):
                        messages.append(_tool_message(call.get("id", ""), ToolCallResult(
                            name=name, ok=True,
                            text=(f"{name} is disabled in observable mode. "
                                  "Do it via the visible UI instead."))))
                        continue

                    # ── Swipe-to-Confirm: kritische Aktion? ──────────────────
                    # Use the BACKEND's element cache (the same source tap_element
                    # resolves against) so the check isn't stale after a
                    # screenshot_marked observation that didn't go through
                    # smartphone_list_elements. Fall back to the loop's copy.
                    _risk_els = getattr(self._backend, "_last_elements", None) or self._last_elements
                    verdict = risk.classify(name, args, _risk_els)
                    if verdict.risky:
                        if pre_authorized is not None:
                            if pre_authorized.available():
                                pre_authorized.consume()
                                print(f"[risk] auto-approved (scheduled): {verdict.description}", flush=True)
                                auto_approve_why = f"auto-approved (scheduled): {verdict.description}"
                            else:
                                print("[risk] unapproved consequential action -> hard abort", flush=True)
                                outcome, terminal = "unapproved_action", True
                                fail_reason = f"unapproved consequential action: {verdict.description}"
                                messages.append(_tool_message(call.get("id", ""), ToolCallResult(
                                    name=name, ok=False,
                                    text="Scheduled run: unapproved consequential action - aborting.")))
                                for rc in calls[idx + 1:]:
                                    messages.append(_tool_message(
                                        rc.get("id", ""),
                                        ToolCallResult(name=(rc.get("function", {}) or {}).get("name", ""),
                                                       ok=False, text="Cancelled: run aborted.")))
                                break
                        else:
                            print(f"[risk] confirm required: {verdict.description} (tool={name})", flush=True)
                            self._events.confirmation_required(verdict.description, name)
                            approved = control.await_confirmation(CONFIRM_TIMEOUT_S)
                            self._events.confirmation_resolved(approved)
                            if not approved:
                                declined = ToolCallResult(
                                    name=name, ok=False,
                                    text=("Der Nutzer hat diese Aktion abgelehnt und "
                                          "NICHT bestaetigt. Fuehre sie nicht aus - "
                                          "waehle einen anderen Weg oder brich ab."))
                                messages.append(_tool_message(call.get("id", ""), declined))
                                continue
                    _t = time.monotonic()
                    result = self._dispatcher.call(name, args)
                    _timing["screenshot" if name == "smartphone_take_screenshot"
                            else "tools"] += time.monotonic() - _t
                    if name == "smartphone_list_elements":
                        self._last_elements = _extract_elements(result)

                    # Record this action as a replayable step (semantic label for
                    # taps) — written to the skill on a verified done.
                    if getattr(result, "ok", True):
                        _step = _replay.record_step(name, args, self._last_elements)
                        # Skip a tap identical to the one just recorded: the model
                        # often re-taps a toggle (wandering); replaying it twice would
                        # flip it back. Consecutive-identical dedup only.
                        if _step is not None and _step != (recorded_steps[-1] if recorded_steps else None):
                            recorded_steps.append(_step)
                    if getattr(result, "ok", True) and name not in _OBSERVATION_TOOLS:
                        why_log.append({"tool": name, "why": auto_approve_why or str(args.get("why", ""))})

                    # Track observe-vs-act for the completion gate + loop breaker.
                    if name in _OBSERVATION_TOOLS:
                        calls_since_obs = 0
                    elif name not in _TERMINAL_TOOLS:
                        calls_since_obs += 1
                        sig = name + json.dumps(args, sort_keys=True, default=str)
                        if action_sigs and action_sigs[-1] != sig:
                            # A genuinely different action -> let the tiers re-arm.
                            loop_warned.clear()
                        action_sigs.append(sig)

                    # ── COMPLETENESS VERIFIER ────────────────────────────────
                    # smartphone_done is NOT accepted blindly: a separate model call
                    # checks the fresh screenshot against the task. Without visual
                    # evidence the "done" is rejected and the agent must keep going
                    # (instead of reporting a hallucination as success). After
                    # MAX_VERIFY_REJECTS the run ends as verify_failed.
                    if name == "smartphone_done":
                        # Stage 1 — cheap deterministic gate before the model judge.
                        gate_ok, gate_reason = _completion_gate(calls_since_obs)
                        if not gate_ok:
                            verify_rejects += 1
                            self._events.verification_result(False, gate_reason)
                            messages.append(_tool_message(
                                call.get("id", ""),
                                ToolCallResult(
                                    name=name, ok=False,
                                    text="COMPLETION GATE: " + gate_reason + ".",
                                ),
                            ))
                            if verify_rejects > MAX_VERIFY_REJECTS:
                                outcome, terminal = "verify_failed", True
                            continue
                        # Stage 2 — model judge against task + criterion + screenshot.
                        _t = time.monotonic()
                        verdict = self._verify_completion(
                            task, criterion, authorization, model
                        )
                        _timing["verify"] += time.monotonic() - _t
                        print(f"[verify] verified={verdict.verified} "
                              f"reason={verdict.reason[:200]!r}", flush=True)
                        self._events.verification_result(
                            verdict.verified, verdict.reason
                        )
                        if verdict.verified:
                            messages.append(_tool_message(call.get("id", ""), result))
                            done_message = args.get("message")
                            outcome, terminal = "done", True
                            # Persist the recorded trajectory as the skill's replay
                            # steps — but DON'T let a short fallback run clobber a
                            # more complete recording (overwrite protection): only
                            # write when the new run has >= as many steps as the
                            # stored one (a complete open->navigate->act sequence is
                            # more self-contained than a context-dependent remnant).
                            if skill is not None and recorded_steps:
                                try:
                                    import json as _json
                                    _sp = skill.path.with_name(skill.path.stem + ".steps.json")
                                    _existing = 0
                                    if _sp.exists():
                                        try:
                                            _existing = len(_json.loads(_sp.read_text(encoding="utf-8")))
                                        except Exception:
                                            _existing = 0
                                    if len(recorded_steps) >= _existing:
                                        _sp.write_text(
                                            _json.dumps(recorded_steps, ensure_ascii=False, indent=2),
                                            encoding="utf-8")
                                        print(f"[replay] recorded {len(recorded_steps)} steps "
                                              f"-> {_sp.name} (was {_existing})", flush=True)
                                        # Hot-reload skills so the new steps are usable
                                        # on the NEXT task without a server restart.
                                        # Atomic: build both new library and (if active)
                                        # new index before assigning either, so we never
                                        # leave the context with a new lib + stale index.
                                        from caddie.skills import SkillLibrary
                                        from caddie.memory.selection import _semantic_on
                                        _new_lib = SkillLibrary.load(
                                            self._context.project_dir / "skills")
                                        _new_index = None
                                        if _semantic_on() and self._context.memory_index is not None:
                                            try:
                                                from caddie.memory import Embedder, MemoryIndex
                                                _new_index = MemoryIndex.build(
                                                    _new_lib, Embedder(), threshold=0.55)
                                            except Exception as _idx_exc:
                                                print(f"[replay] index rebuild failed: {_idx_exc}",
                                                      flush=True)
                                                # On failure publish None — new-lib + old-index
                                                # would be a generation mismatch. None is safe:
                                                # readers fall back to trigger matching.
                                                _new_index = None
                                        # Publish in an order that keeps every intermediate
                                        # state consistent for concurrent readers (no lock
                                        # needed because each attribute write is atomic under
                                        # the GIL):
                                        #   1. memory_index = None  -> readers see old-lib + no-index
                                        #      (safe: trigger fallback used for both)
                                        #   2. skills = _new_lib    -> readers see new-lib + no-index
                                        #      (safe: trigger fallback still used)
                                        #   3. memory_index = _new_index -> fully consistent new state
                                        # The only state that must NEVER appear is new-lib + old-index.
                                        self._context.memory_index = None
                                        self._context.skills = _new_lib
                                        self._context.memory_index = _new_index
                                    else:
                                        print(f"[replay] kept existing {_existing} steps "
                                              f"(new run only {len(recorded_steps)})", flush=True)
                                except Exception as _exc:
                                    print(f"[replay] step record failed: {_exc}", flush=True)
                        else:
                            verify_rejects += 1
                            messages.append(_tool_message(
                                call.get("id", ""),
                                ToolCallResult(
                                    name=name, ok=False,
                                    text=(
                                        "VERIFICATION FAILED: "
                                        + verdict.reason
                                        + " Do NOT blindly repeat your last action "
                                        "(that can undo it, e.g. flipping a toggle "
                                        "back). FIRST take a fresh screenshot to see "
                                        "the CURRENT state, then decide: if it already "
                                        "shows the intended result, call smartphone_done "
                                        "with a clear message; otherwise take the one "
                                        "missing step."
                                    ),
                                ),
                            ))
                            if verify_rejects > MAX_VERIFY_REJECTS:
                                outcome, terminal = "verify_failed", True
                        continue

                    messages.append(_tool_message(call.get("id", ""), result))
                    if result.image_b64:
                        pending_images.append(result)
                    if name == "smartphone_failed":
                        fail_reason = str(args.get("reason") or "").strip() or None
                        outcome, terminal = "failed", True

                # Bilder (Screenshots) NACH allen tool-Messages anhaengen — das
                # OpenAI-Format verlangt, dass jeder tool_call_id zuerst eine
                # tool-Antwort hat, bevor eine andere Rolle folgt.
                for img in pending_images:
                    messages.append(_image_message(img))

                if terminal:
                    break
                if tool_calls_made > MAX_TOOL_CALLS:
                    outcome = "fail_loop"
                    break

            self._active_control = None
            # ── LATENZ-BREAKDOWN (Speed-Analyse) ──────────────────────────────
            _total = time.monotonic() - _run_t0
            _acct = sum(_timing.values())
            print(
                f"[timing] RUN outcome={outcome} turns={turns} total={_total:.1f}s | "
                f"inference={_timing['inference']:.1f}s "
                f"screenshot={_timing['screenshot']:.1f}s "
                f"tools={_timing['tools']:.1f}s "
                f"verify={_timing['verify']:.1f}s "
                f"other={_total - _acct:.1f}s "
                f"(inference={100 * _timing['inference'] / _total:.0f}% of total)",
                flush=True,
            )
            # Single owner of the terminal event: emit exactly one task_finished per
            # run (http_api only emits as a crash-safety net, see finished_emitted).
            _clean = outcome in ("done", "stopped", "stopped_by_user")
            _fin: dict = {"outcome": outcome}
            if outcome == "done" and done_message:
                _fin["message"] = done_message
            elif not _clean:
                _emsg = fail_reason or error
                if _emsg:
                    _fin["error"] = str(_emsg)[:500]
                    _fin["message"] = str(_emsg)[:500]
            self._events.task_finished(ok=_clean, payload=_fin)
            # Diesen Run als moeglichen Korrektur-Kontext merken (siehe recent_run).
            self._last_run = {
                "task": task,
                "outcome": outcome,
                "final_text": final_text,
                "finished_at": time.monotonic(),
            }
            # Auto-mine a SUCCESSFUL (non-replay) run into a demonstration hint so the
            # next attempt gets the exact path instead of re-discovering it. Flag-gated
            # (LLM_SMARTPHONE_MINE_DEMOS) and writes to the explored store, so it feeds
            # the existing hint mechanism. Hint only -- never auto-replayed.
            if (outcome == "done" and recorded_steps
                    and os.environ.get("LLM_SMARTPHONE_MINE_DEMOS", "0") == "1"):
                try:
                    from pathlib import Path as _Path
                    from caddie.agent.mining import build_demonstration, append_demonstration
                    _store = os.environ.get("LLM_SMARTPHONE_EXPLORED_STORE", "")
                    _demo = build_demonstration(task, recorded_steps)
                    if _demo is not None and _store:
                        append_demonstration(_demo, _Path(_store))
                        print(f"[mining] saved demonstration for {task!r} "
                              f"({len(_demo.provenance['path'])} steps)", flush=True)
                except Exception as exc:
                    print(f"[mining] skip ({exc})", flush=True)
            return {
                "ok": outcome in ("done", "stopped", "stopped_by_user"),
                "finished_emitted": True,
                "outcome": outcome,
                "tool_calls": tool_calls_made,
                "turns": turns,
                "final_text": final_text,
                "error": error,
                "vision_unsupported": vision_unsupported,
                "steps": why_log,
            }
        finally:
            if control is not None and self._active_control is control:
                self._active_control = None
            if acquired_here:
                self.release_slot()

    def _verify_completion(self, task: str, criterion: str | None,
                           authorization: str | None,
                           model: str | None) -> "_Verdict":
        """Separate model call that checks the current screen against the task
        before smartphone_done is accepted (VLAA-GUI completeness verifier).

        Takes a fresh screenshot, asks a judge model in a CLEAN context (not the
        agent's own biased trajectory) whether the task is visibly complete, and
        returns a strict verdict. If ``criterion`` is given it is handed to the
        judge as the explicit, UI-observable success signal (much stronger than
        guessing from the task text). The judge model can be overridden with
        CADDIE_JUDGE_MODEL (else the run model is reused). On judge error it
        lets the claim through rather than falsely blocking — but records why."""
        shot = self._dispatcher.call("smartphone_take_screenshot", {})
        img_b64 = getattr(shot, "image_b64", None)
        if not img_b64:
            return _Verdict(False, "no screenshot available for verification")
        mime = getattr(shot, "image_mime", None) or "image/png"
        criterion_line = (
            f"\nSuccess criterion (must be visibly met): {criterion}"
            if criterion else ""
        )
        judge_messages = [
            {"role": "system", "content": _VERIFIER_SYSTEM},
            {"role": "user", "content": [
                {"type": "text", "text": (
                    f"Task: {task}{criterion_line}\n\nIs this task clearly and "
                    "visibly complete on this screenshot? Reply with JSON only: "
                    "{\"verified\": true|false, \"reason\": \"short\"}."
                )},
                {"type": "image_url",
                 "image_url": {"url": f"data:{mime};base64,{img_b64}"}},
            ]},
        ]
        judge_model = os.environ.get("CADDIE_JUDGE_MODEL") or model
        resp = self._lm.chat_completion(
            judge_messages, tools=None,
            authorization=authorization, model=judge_model,
        )
        if not resp.get("ok"):
            return _Verdict(
                True, "verifier unreachable: " + str(resp.get("error", ""))[:120]
            )
        choice = _first_choice(resp.get("response", {}))
        content = ((choice or {}).get("message", {}) or {}).get("content", "") or ""
        return _parse_verdict(content)

    def _chat_with_cancel(
        self, messages: list[dict], control: RunControl,
        authorization: str | None, model: str | None,
    ) -> dict | None:
        """One inference turn that the user can TRULY abort: the response is
        streamed and read chunk-by-chunk, and the instant pause/stop/
        intervention is requested the connection is closed -- the inference
        server then stops generating (no wasted GPU, no orphaned worker).
        Returns the response dict, or None when cancelled (caller handles
        pause/stop). On resume the loop re-infers from fresh context
        (re-perception + any correction injected at the pause point)."""
        return self._lm.chat_completion_stream(
            list(messages), self._tool_specs, authorization, model,
            should_cancel=lambda: (
                control.stop_requested or control.is_paused
                or control.has_intervention
            ),
        )

    def _pause_point(self, messages: list[dict], control: RunControl) -> str:
        """Pause/Stop/Resume-Hook, einmal pro Turn.

        - Stop angefordert -> ``"stop"`` (Loop bricht ab)
        - Pausiert -> blockiert den Thread bis Resume oder Stop
        - Nach einem menschlichen Eingriff -> Neu-Wahrnehmung anhaengen
        """
        if control.stop_requested:
            return "stop"
        if control.is_paused:
            if control.wait_while_paused() is RunState.STOPPED:
                return "stop"
        if control.consume_intervention():
            self._inject_reperception(messages, control.take_correction())
        return "continue"

    def _inject_reperception(
        self, messages: list[dict], correction: str | None = None
    ) -> None:
        """Nach einem Eingriff einen Hinweis anhaengen, damit der Agent den
        Bildschirm neu bewertet statt mit veraltetem Wissen weiterzuarbeiten.

        Der Agent nimmt selbst per Tool (Screenshot/Element-Liste) neu wahr —
        hier wird das nur angestossen. Bewusst KEIN Bild in den Prompt: nicht
        jedes lokale Modell ist multimodal, ein Bild wuerde sonst 400ern.
        Liegt eine gesprochene Korrektur vor, wird sie als ausdrueckliche
        Nutzer-Anweisung mitgegeben (Mid-run-Korrektur)."""
        if correction:
            note = (
                f"Der Nutzer hat den Lauf unterbrochen und sagt: "
                f"\"{correction}\". Beruecksichtige diese Anweisung. Mache dir "
                f"zuerst per Screenshot oder Element-Liste ein frisches Bild "
                f"vom aktuellen Screen, dann fahre entsprechend fort."
            )
        else:
            note = (
                "Der Nutzer hat waehrend einer Pause selbst am Geraet "
                "gehandelt. Der Bildschirm kann sich geaendert haben. Mache "
                "dir zuerst per Screenshot oder Element-Liste ein frisches "
                "Bild vom aktuellen Screen, bevor du fortfaehrst."
            )
        messages.append({"role": "user", "content": note})


def _prior_context_note(prior: dict) -> str:
    """Neutraler Kontext-Satz fuer einen Folge-Run kurz nach "fertig".

    Wichtig: NICHT annehmen, dass der neue Auftrag eine Korrektur ist — das
    laesst sich aus dem Timing allein nicht entscheiden. "Suche eine Pizza"
    nach "schalte Dark Mode an" ist ein eigenstaendiger Auftrag, kein
    Nachbessern. Daher wird der vorige Lauf nur als *Info* mitgegeben; das
    Modell entscheidet selbst, ob der neue Auftrag daran anknuepft."""
    prev_task = str(prior.get("task") or "").strip()
    final_text = str(prior.get("final_text") or "").strip()
    note = (
        f"Zur Info: Dein unmittelbar vorheriger Auftrag war \"{prev_task}\" "
        f"(abgeschlossen"
    )
    if final_text:
        note += f", gemeldet: \"{final_text}\""
    note += (
        "). Falls der folgende Auftrag eine Korrektur oder Fortsetzung davon "
        "ist (z.B. \"nimm ein anderes\"), beziehe dich darauf. Andernfalls "
        "behandle ihn als eigenstaendigen neuen Auftrag und ignoriere diesen "
        "Hinweis. Mache dir in jedem Fall zuerst per Screenshot oder "
        "Element-Liste ein frisches Bild vom aktuellen Screen."
    )
    return note


def _first_choice(response: dict) -> dict | None:
    choices = response.get("choices") or []
    return choices[0] if choices else None


def _assistant_message(message: dict) -> dict:
    """Assistant-Turn fuer den Conversation-Verlauf — tool_calls muessen
    erhalten bleiben, sonst ist die naechste tool-Message ungueltig."""
    out: dict = {"role": "assistant", "content": message.get("content")}
    if message.get("tool_calls"):
        out["tool_calls"] = message["tool_calls"]
    return out


def _tool_message(tool_call_id: str, result: ToolCallResult) -> dict:
    return {
        "role": "tool",
        "tool_call_id": tool_call_id,
        "content": result.text,
    }


def _trailing_repeat(sigs: list[str]) -> int:
    """How many identical action signatures trail at the end of the list.
    Used by the loop breaker: a long trailing run = the agent is stuck
    repeating the same action with no progress."""
    if not sigs:
        return 0
    last = sigs[-1]
    count = 0
    for sig in reversed(sigs):
        if sig == last:
            count += 1
        else:
            break
    return count


def _elements_sig(elements: list[dict] | None) -> str:
    """Content-sensitive screen signature from the agent's observed elements.
    Unlike dumpsys ui_hash (window/focus only), this changes when on-screen
    CONTENT changes (e.g. a timer field 0:00 -> 5:00, a slider value, a new list)
    — so genuine in-app progress is NOT mistaken for being stuck."""
    if not elements:
        return ""
    parts = []
    for e in elements[:40]:
        rid = e.get("resource_id") or ""
        txt = e.get("text") or e.get("content_description") or ""
        parts.append(f"{rid}:{txt}")
    return "|".join(parts)


def _no_progress(state_sigs: list[str], window: int = 8, max_distinct: int = 2) -> bool:
    """Detect a stuck agent by SCREEN STATE, not action identity. True when the
    most recent ``window`` screen-state signatures contain <= ``max_distinct``
    distinct states — the agent is cycling among a few screens (or frozen on one)
    despite emitting varied actions. This catches the fail_loop mode that
    _trailing_repeat misses (varied gestures/coordinates -> no identical run, so
    the action loop-breaker never fires; the agent burns MAX_TOOL_CALLS). Needs a
    full window before it can conclude anything."""
    if len(state_sigs) < window:
        return False
    return len(set(state_sigs[-window:])) <= max_distinct


def _completion_gate(calls_since_obs: int) -> tuple[bool, str]:
    """Deterministic Stage-1 gate (cheaper than the model judge): reject a
    done that was declared without a fresh screen observation in the last
    GATE_MAX_STALE_CALLS state-changing calls. Catches blind "done" claims
    before spending a judge call. Returns (passed, reason)."""
    if calls_since_obs > GATE_MAX_STALE_CALLS:
        return False, (
            f"no fresh screen observation in the last {calls_since_obs} actions; "
            "re-check the screen (smartphone_take_screenshot / "
            "smartphone_list_elements) before declaring done"
        )
    return True, ""


class _Verdict:
    """Result of the completeness verifier: a pass/fail flag plus a short
    reason that is fed back to the agent on rejection."""
    __slots__ = ("verified", "reason")

    def __init__(self, verified: bool, reason: str) -> None:
        self.verified = bool(verified)
        self.reason = reason or ""


def _parse_verdict(content: str) -> _Verdict:
    """Extract {verified, reason} JSON from the judge's reply, robust to code
    fences and surrounding prose. Unparseable -> reject (conservative)."""
    match = re.search(r"\{.*\}", content, re.DOTALL)
    if not match:
        return _Verdict(False, "verifier reply not parseable: " + content[:120])
    try:
        data = json.loads(match.group(0))
    except (ValueError, TypeError):
        return _Verdict(False, "verifier JSON invalid: " + content[:120])
    return _Verdict(bool(data.get("verified")), str(data.get("reason", ""))[:200])


def _prune_old_images(messages: list[dict]) -> None:
    """Replace every screenshot message except the most recent with a text
    placeholder. In-place. Idempotent (the placeholder is text-only and is no
    longer detected as an image on the next pass)."""
    image_idxs = [
        i for i, m in enumerate(messages)
        if m.get("role") == "user"
        and isinstance(m.get("content"), list)
        and any(
            isinstance(part, dict) and part.get("type") == "image_url"
            for part in m["content"]
        )
    ]
    for i in image_idxs[:-1]:
        messages[i] = {
            "role": "user",
            "content": "[screenshot from an earlier step removed]",
        }


def _estimate_tokens(messages: list[dict]) -> int:
    """Rough token estimate (~4 chars/token). Image parts count as a flat cost
    since their base64 length is not what the model bills."""
    chars = 0
    for m in messages:
        content = m.get("content")
        if isinstance(content, str):
            chars += len(content)
        elif isinstance(content, list):
            for part in content:
                if isinstance(part, dict):
                    if part.get("type") == "text":
                        chars += len(part.get("text", ""))
                    else:
                        chars += 6000  # ~1500 tokens for an image part
        for tc in (m.get("tool_calls") or []):
            chars += len(json.dumps(tc))
    return chars // 4


_COMPACT_MARKER = "[Note: earlier steps were trimmed"


def _compact_messages(messages: list[dict], keep_turns: int) -> list[dict]:
    """Turn-aware context compaction. Keeps the head (system prompt + prior
    context + the original task -- everything before the first assistant turn)
    plus the last ``keep_turns`` assistant-led turns; drops the middle and
    inserts one short note. Cutting only at assistant boundaries keeps every
    tool_call paired with its tool result (OpenAI requirement)."""
    first_asst = next(
        (i for i, m in enumerate(messages) if m.get("role") == "assistant"), None
    )
    if first_asst is None:
        return messages
    head = [
        m for m in messages[:first_asst]
        if not (isinstance(m.get("content"), str)
                and m["content"].startswith(_COMPACT_MARKER))
    ]
    body = messages[first_asst:]
    asst_idxs = [i for i, m in enumerate(body) if m.get("role") == "assistant"]
    if len(asst_idxs) <= keep_turns:
        return messages
    cut = asst_idxs[-keep_turns]
    note = {
        "role": "user",
        "content": _COMPACT_MARKER + " to fit the context window. Re-check the "
        "current screen with a screenshot or element list before acting.]",
    }
    return head + [note] + body[cut:]


def _image_message(result: ToolCallResult) -> dict:
    """Screenshot als Image-Content — tool-Messages sind im OpenAI-Format
    textonly, also kommt das Bild als separate user-Message zurueck."""
    mime = result.image_mime or "image/png"
    return {
        "role": "user",
        "content": [
            {"type": "text", "text": "Aktueller Screen nach dem letzten Tool:"},
            {
                "type": "image_url",
                "image_url": {"url": f"data:{mime};base64,{result.image_b64}"},
            },
        ],
    }


def _parse_arguments(raw: Any) -> dict[str, Any]:
    """tool_call-Argumente kommen als JSON-String. Robust gegen leer/kaputt."""
    if isinstance(raw, dict):
        return raw
    if not raw:
        return {}
    try:
        parsed = json.loads(raw)
        return parsed if isinstance(parsed, dict) else {}
    except (json.JSONDecodeError, TypeError):
        return {}


def _extract_elements(result: ToolCallResult) -> list[dict]:
    """Holt die ``elements``-Liste aus einem list_elements-Tool-Ergebnis —
    Basis fuer die Risiko-Pruefung des naechsten Taps."""
    try:
        data = json.loads(result.text)
        elements = data.get("elements")
        return elements if isinstance(elements, list) else []
    except (json.JSONDecodeError, AttributeError, TypeError):
        return []

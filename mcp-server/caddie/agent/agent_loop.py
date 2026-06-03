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
from caddie.agent.lmstudio import LmStudioClient
from caddie.agent.run_control import RunControl, RunState
from caddie.agent.tool_bridge import ToolCallResult, ToolDispatcher
from caddie.context import ServerContext

# Sicherheitsnetze gegen stuck Modelle (vgl. experiments/run_trials.py).
MAX_TOOL_CALLS = 25
MAX_TURNS = 40
# Completeness verifier (after VLAA-GUI 2026): before smartphone_done is
# accepted, a SEPARATE model call checks the fresh screenshot against the task.
# A "done" may be rejected this many times before the run ends as failed
# (prevents an infinite done -> reject -> done loop).
MAX_VERIFY_REJECTS = 2

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
        self._tool_specs = self._dispatcher.openai_tool_specs()
        # Zuletzt gesehene list_elements-Ausgabe — Basis fuer die Risiko-
        # Pruefung von Taps (Element unter den Tap-Koordinaten).
        self._last_elements: list[dict] = []
        # Steuer-Objekt des gerade laufenden Runs (None = kein Run aktiv).
        # /control greift hierueber ein.
        self._active_control: RunControl | None = None
        # Kompakter Merker des zuletzt beendeten Runs — Grundlage fuer eine
        # Korrektur NACH "fertig" (Folge-Task mit Kontext, siehe recent_run).
        self._last_run: dict | None = None

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
        if action == "pause":
            control.request_pause()
            self._events.task_paused()
        elif action == "intervene":
            control.request_pause(intervention=True)
            self._events.task_paused()
        elif action == "resume":
            control.request_resume()
            self._events.task_resumed()
        elif action == "correct":
            control.set_correction(text or "")
            control.request_resume()
            self._events.task_resumed()
        elif action == "confirm":
            control.resolve_confirmation(True)
        elif action == "decline":
            control.resolve_confirmation(False)
        elif action == "stop":
            control.request_stop()
        else:
            return {"ok": False, "error": f"unknown action: {action}"}
        return {"ok": True, "action": action, "state": control.state.value}

    def run(
        self,
        task: str,
        system_prompt: str,
        authorization: str | None = None,
        model: str | None = None,
        prior: dict | None = None,
        criterion: str | None = None,
    ) -> dict[str, Any]:
        """Faehrt die Session bis done/failed/Abbruch und liefert das Resultat.

        ``prior`` ist optional der zuletzt beendete Run (siehe ``recent_run``):
        wird er mitgegeben, sieht das Modell vor der eigentlichen Aufgabe einen
        kurzen Kontext, was es zuvor getan hat — damit eine Korrektur wie "nimm
        ein anderes Restaurant" Bezug auf den vorigen Lauf hat, obwohl es ein
        frischer Run ohne Conversation-Gedaechtnis ist."""
        messages: list[dict] = [
            {"role": "system", "content": system_prompt},
        ]
        if prior:
            messages.append({"role": "user", "content": _prior_context_note(prior)})
        messages.append({"role": "user", "content": task})
        control = RunControl()
        self._active_control = control
        tool_calls_made = 0
        verify_rejects = 0
        # Loop-breaker + Stage-1-gate state (per run).
        action_sigs: list[str] = []   # signatures of state-changing tool calls
        calls_since_obs = 0           # actions since the last screen observation
        loop_warned: set[int] = set()  # which loop-breaker tiers already fired
        turns = 0
        outcome = "max_turns"
        final_text = ""
        error: str | None = None
        vision_unsupported = False

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

            # Keep only the most recent screenshot in context — otherwise
            # vision tokens grow quadratically (every old screen is re-encoded
            # each turn) and slow the model down. The agent perceives the screen
            # fresh every turn anyway; older screenshots are stale.
            _prune_old_images(messages)
            resp = self._lm.chat_completion(
                messages, tools=self._tool_specs,
                authorization=authorization, model=model,
            )
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

            if not calls:
                # Modell hat ohne Tool-Call geantwortet -> Session-Ende.
                final_text = message.get("content") or ""
                outcome = "stopped"
                break

            terminal = False
            pending_images: list[ToolCallResult] = []
            for call in calls:
                if control.stop_requested:
                    outcome, terminal = "stopped_by_user", True
                    break
                tool_calls_made += 1
                fn = call.get("function", {}) or {}
                name = fn.get("name", "")
                args = _parse_arguments(fn.get("arguments"))

                # ── Swipe-to-Confirm: kritische Aktion? ──────────────────
                verdict = risk.classify(name, args, self._last_elements)
                if verdict.risky:
                    self._events.confirmation_required(verdict.description, name)
                    approved = control.await_confirmation(CONFIRM_TIMEOUT_S)
                    self._events.confirmation_resolved(approved)
                    if not approved:
                        declined = ToolCallResult(
                            name=name, ok=False,
                            text=("Der Nutzer hat diese Aktion abgelehnt und "
                                  "NICHT bestaetigt. Fuehre sie nicht aus — "
                                  "waehle einen anderen Weg oder brich ab."),
                        )
                        messages.append(_tool_message(call.get("id", ""), declined))
                        continue

                result = self._dispatcher.call(name, args)
                if name == "smartphone_list_elements":
                    self._last_elements = _extract_elements(result)

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
                    verdict = self._verify_completion(
                        task, criterion, authorization, model
                    )
                    self._events.verification_result(
                        verdict.verified, verdict.reason
                    )
                    if verdict.verified:
                        messages.append(_tool_message(call.get("id", ""), result))
                        outcome, terminal = "done", True
                    else:
                        verify_rejects += 1
                        messages.append(_tool_message(
                            call.get("id", ""),
                            ToolCallResult(
                                name=name, ok=False,
                                text=(
                                    "VERIFICATION FAILED: "
                                    + verdict.reason
                                    + " The task is NOT yet complete according to "
                                    "the screenshot. Keep going and only call "
                                    "smartphone_done once the screen clearly shows "
                                    "the result."
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
        # Diesen Run als moeglichen Korrektur-Kontext merken (siehe recent_run).
        self._last_run = {
            "task": task,
            "outcome": outcome,
            "final_text": final_text,
            "finished_at": time.monotonic(),
        }
        return {
            "ok": outcome in ("done", "stopped"),
            "outcome": outcome,
            "tool_calls": tool_calls_made,
            "turns": turns,
            "final_text": final_text,
            "error": error,
            "vision_unsupported": vision_unsupported,
        }

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

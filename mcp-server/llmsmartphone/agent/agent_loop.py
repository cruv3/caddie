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
from typing import Any

from llmsmartphone.agent.lmstudio import LmStudioClient
from llmsmartphone.agent.run_control import RunControl, RunState
from llmsmartphone.agent.tool_bridge import ToolCallResult, ToolDispatcher
from llmsmartphone.context import ServerContext

# Sicherheitsnetze gegen stuck Modelle (vgl. experiments/run_trials.py).
MAX_TOOL_CALLS = 25
MAX_TURNS = 40


class AgentLoop:
    """Faehrt eine komplette Agent-Session und besitzt dabei den Tool-Loop."""

    def __init__(self, context: ServerContext, lmstudio: LmStudioClient) -> None:
        self._dispatcher = ToolDispatcher(context)
        self._lm = lmstudio
        self._tool_specs = self._dispatcher.openai_tool_specs()
        # Steuer-Objekt des gerade laufenden Runs (None = kein Run aktiv).
        # /control greift hierueber ein.
        self._active_control: RunControl | None = None

    def apply_control(self, action: str) -> dict:
        """Wendet ein ``/control``-Signal auf den aktiven Run an.

        ``intervene`` = Pause + Markierung fuer Neu-Wahrnehmung (das sendet
        spaeter die Touch-Erkennung); ``pause`` = stilles Pausieren.
        """
        control = self._active_control
        if control is None:
            return {"ok": False, "error": "no active run"}
        action = (action or "").lower().strip()
        if action == "pause":
            control.request_pause()
        elif action == "intervene":
            control.request_pause(intervention=True)
        elif action == "resume":
            control.request_resume()
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
    ) -> dict[str, Any]:
        """Faehrt die Session bis done/failed/Abbruch und liefert das Resultat."""
        messages: list[dict] = [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": task},
        ]
        control = RunControl()
        self._active_control = control
        tool_calls_made = 0
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
                tool_calls_made += 1
                fn = call.get("function", {}) or {}
                name = fn.get("name", "")
                args = _parse_arguments(fn.get("arguments"))
                result = self._dispatcher.call(name, args)
                messages.append(_tool_message(call.get("id", ""), result))
                if result.image_b64:
                    pending_images.append(result)
                if name == "smartphone_done":
                    outcome, terminal = "done", True
                elif name == "smartphone_failed":
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
        return {
            "ok": outcome in ("done", "stopped"),
            "outcome": outcome,
            "tool_calls": tool_calls_made,
            "turns": turns,
            "final_text": final_text,
            "error": error,
            "vision_unsupported": vision_unsupported,
        }

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
            self._inject_reperception(messages)
        return "continue"

    def _inject_reperception(self, messages: list[dict]) -> None:
        """Nach einem Eingriff: frischen Screenshot + Hinweis anhaengen, damit
        der Agent nicht mit veraltetem Bildschirm-Wissen weiterarbeitet."""
        shot = self._dispatcher.call("smartphone_take_screenshot", {})
        if shot.image_b64:
            messages.append(_image_message(shot))
        messages.append({
            "role": "user",
            "content": (
                "Der Nutzer hat waehrend einer Pause selbst am Geraet "
                "gehandelt. Der Bildschirm kann sich geaendert haben — "
                "bewerte den aktuellen Stand neu, bevor du fortfaehrst."
            ),
        })


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

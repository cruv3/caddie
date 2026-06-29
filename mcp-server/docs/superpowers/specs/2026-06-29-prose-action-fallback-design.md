# Prose-Action Fallback — Design Spec

Date: 2026-06-29 · Branch: feat/model-phone-tuning · Status: approved

## Motivation
Observed live (Qwen3.6, scheduled dry-run, multi-step task "set an alarm for
7:30 AM", reproduced twice): on harder tasks the model degrades from structured
tool calls to *textual* ReAct — it writes `Action: smartphone_open_app(...)` in
the message content instead of emitting a real tool call. The loop sees no
`tool_calls` + non-empty content with `finish=stop` and treats it as a final
answer -> `outcome="stopped"` on turn 1. The run dies without acting. This is an
agent-reliability gap, separate from any feature; it kills multi-step runs.

## Goal
When the model narrates the next action as prose instead of calling the tool,
nudge it to emit a real tool call and retry (bounded), instead of ending the run.
Must NOT misfire on a genuine final answer.

## Design (detect + targeted nudge + retry)
In `caddie/agent/agent_loop.py`:

- New pure helper:
  ```python
  _PROSE_ACTION_RE = re.compile(r"(?im)^\s*action:\s*(smartphone_\w+)\s*\(")
  def _looks_like_prose_action(text: str) -> bool:
      return bool(_PROSE_ACTION_RE.search(text or ""))
  ```
  Matches the degraded `Action: smartphone_x(` line. A genuine final answer does
  not contain that pattern -> no misfire.

- New constants beside the existing nudge constants:
  - `MAX_PROSE_ACTION_NUDGES = 2`
  - `_PROSE_ACTION_NUDGE` — firm corrective: the model wrote the action as TEXT;
    it must emit a real tool call (function-call mechanism), or call
    smartphone_done / smartphone_failed if truly finished.

- In the `if not calls:` branch, in the "non-empty content, no tool_calls"
  sub-case (currently sets `outcome="stopped"`), BEFORE stopping:
  ```python
  if _looks_like_prose_action(final_text) and prose_nudges < MAX_PROSE_ACTION_NUDGES:
      prose_nudges += 1
      messages.append({"role": "user", "content": _PROSE_ACTION_NUDGE})
      continue
  # else: existing behavior -> outcome = "stopped"; break
  ```
  `prose_nudges = 0` initialized per run (next to `empty_turns`).

## Correctness / bounds
- Genuine final answer -> no regex match -> `stopped` as before (no misfire).
- Bounded to `MAX_PROSE_ACTION_NUDGES` retries -> no infinite loop; after budget,
  the run stops as it does today.
- No prose argument parsing, no synthesized tool calls, no message-format risk
  (the nudge is an ordinary user message; the existing assistant message stays).
- Interactive and scheduled runs both benefit (same loop).

## Testing
- Unit-test `_looks_like_prose_action`: matches the observed degraded content and
  `Action: smartphone_open_app(...)`; does NOT match a genuine final answer
  ("The alarm is set."), normal prose, or empty/None.
- Loop wiring validated live by re-running the multi-step scheduled task (the loop
  now nudges instead of stopping on turn 1).
- Codex review of the hot-loop change.

## Out of scope
- Parsing/executing the prose action directly (rejected: fragile kwarg parsing +
  message-format risk in the hot loop).
- Prompt-only reinforcement (may add a one-liner later; not required here).

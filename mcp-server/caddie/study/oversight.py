"""Oversight gates (C1 / C2 / C3) for the study system.

This module implements the three oversight protocols defined in the user-study
system design:

* **C1 — Stepwise** (StudyCondition.STEPWISE)
  Every consequential step must be individually confirmed before execution.
  The user can approve, decline, or cancel the trial at each step.

* **C2 — Final checkpoint** (StudyCondition.FINAL_CHECKPOINT)
  All consequential steps are collected during execution and presented as a
  single batch summary at the end of the trial. The user reviews the batch,
  confirms, declines, or cancels.

* **C3 — Voluntary intervention** (StudyCondition.VOLUNTARY_INTERVENTION)
  No gates are applied. The user intervenes freely without explicit approval
  at any step.

The ``OversightManager`` class implements both the ``OversightProtocol``
interface used by ``TrialExecutor`` and provides a standalone ``confirm_step``,
``show_summary``, and ``cancel`` API for integration with the Android app UI.

Directory structure
-------------------
    caddie/study/
        executor.py       ← step execution logic
        oversight.py      ← this module (C1/C2/C3 gates)
        verification.py   ← post-trial verification
"""

from __future__ import annotations

import threading

from dataclasses import dataclass, field
from typing import Callable, Optional, Protocol, Sequence

from caddie.study.logger import StudyLogger
from caddie.study.model import StudyCondition, StudyStep


# ---------------------------------------------------------------------------
# Public types
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class OversightDecision:
    """The result of an oversight gate decision.

    Attributes:
        confirmed: True if the user approved the step(s).
        cancelled: True if the user cancelled the trial.
        declined: True if the user declined the step(s) (C1 only).
        modified_steps: Steps the user edited (C2 batch mode).
        reason: Human-readable reason for decline or cancellation.
    """

    confirmed: bool = False
    cancelled: bool = False
    declined: bool = False
    modified_steps: tuple[StudyStep, ...] = field(default_factory=tuple)
    reason: str = ""


# ---------------------------------------------------------------------------
# Protocol
# ---------------------------------------------------------------------------


class OversightProtocol(Protocol):
    """Protocol for oversight gate callbacks used by TrialExecutor.

    Implementations must provide step-level and batch confirmation logic
    for C1 (stepwise), C2 (final checkpoint), and C3 (voluntary) conditions.
    """

    def confirm_consequential_step(
        self, step: StudyStep, narration: str
    ) -> OversightDecision:
        """Confirm or decline a single consequential step.

        Returns a decision that may be confirmed, declined, or cancelled.
        """

    def show_c2_summary(self, steps: Sequence[StudyStep]) -> OversightDecision:
        """Show batch summary of pending consequential steps (C2 mode)."""

    def show_c2_summary_with_narrations(
        self, steps: Sequence[StudyStep], narrations: Sequence[str]
    ) -> OversightDecision:
        """Show batch summary with effective narrations (C2 mode)."""

    def is_cancelled(self) -> bool:
        """Check whether the trial has been cancelled."""


# ---------------------------------------------------------------------------
# Manager
# ---------------------------------------------------------------------------


class OversightManager:
    """Gate manager for C1 / C2 / C3 oversight conditions.

    This class is the concrete implementation of ``OversightProtocol``.
    It delegates step-level confirmation to ``confirm_step``, batch summary
    display to ``show_summary``, and trial cancellation to ``cancel``.

    The manager logs all gate events via the shared ``StudyLogger``.

    Args:
        logger: The study logger for event recording.
        condition: The study condition (C1 / C2 / C3) determining gate behaviour.
    """

    def __init__(
        self,
        logger: StudyLogger,
        condition: StudyCondition = StudyCondition.STEPWISE,
        step_callback: Optional[Callable[[StudyStep, str], OversightDecision]] = None,
        batch_callback: Optional[Callable[[Sequence[StudyStep], Sequence[str]], OversightDecision]] = None,
    ) -> None:
        self._logger = logger
        self._condition = condition
        self._lock = threading.RLock()  # Protects _cancelled, _declined_steps, _pending_steps
        self._cancelled = False
        self._declined_steps: list[str] = []
        self._pending_steps: list[StudyStep] = []
        self._step_callback = step_callback
        self._batch_callback = batch_callback

    # ------------------------------------------------------------------
    # Public API (callable from executor and from Android UI bridge)
    # ------------------------------------------------------------------

    def confirm_consequential_step(
        self, step: StudyStep, narration: str
    ) -> OversightDecision:
        """Confirm or decline a single consequential step (C1 mode).

        In C1 (stepwise), each consequential step pauses execution and
        requires explicit user confirmation. The step ID, narration text,
        and action description are logged as a ``CONFIRMATION_SHOWN`` event.

        In C2 and C3, this method appends the step to the pending list
        and returns ``confirmed=True`` immediately (C3) or defers to the
        batch summary (C2).

        Args:
            step: The step requiring confirmation.
            narration: Human-readable narration text for this step.

        Returns:
            An ``OversightDecision`` with confirmation status.
        """
        with self._lock:
            if self._cancelled:
                return OversightDecision(cancelled=True)

            # C2: defer to batch summary — just queue the step
            if self._condition == StudyCondition.FINAL_CHECKPOINT:
                self._pending_steps.append(step)
                return OversightDecision(confirmed=True)

            # C3: no gate — always pass through
            if self._condition == StudyCondition.VOLUNTARY_INTERVENTION:
                return OversightDecision(confirmed=True)

        # C1: stepwise — require explicit confirmation
        return self._gate_stepwise(step, narration)

    def show_c2_summary(self, steps: Sequence[StudyStep]) -> OversightDecision:
        """Show batch summary of pending consequential steps (C2 mode).

        In C2 (final checkpoint), all queued consequential steps are
        presented to the user as a single review. The user can approve
        the batch, decline specific steps, or cancel the trial.

        In C1 and C3 this method returns ``confirmed=True`` immediately
        since no batch summary is needed.

        Args:
            steps: The consequential steps to review as a batch.

        Returns:
            An ``OversightDecision`` with confirmation status.
        """
        narrations = [s.narration or s.action for s in steps]
        return self.show_c2_summary_with_narrations(steps, narrations)

    def show_c2_summary_with_narrations(
        self, steps: Sequence[StudyStep], narrations: Sequence[str]
    ) -> OversightDecision:
        """Show batch summary with effective narrations (C2 mode).

        Uses the effective narration (including error descriptions) rather
        than original step narration.

        Args:
            steps: The consequential steps to review as a batch.
            narrations: Effective narrations matching each step.

        Returns:
            An ``OversightDecision`` with confirmation status.
        """
        with self._lock:
            if self._cancelled:
                return OversightDecision(cancelled=True)

        # C1: no batch summary — individual gates already handled
        if self._condition == StudyCondition.STEPWISE:
            return OversightDecision(confirmed=True)

        # C2: present the queued batch
        return self._gate_batch(steps, narrations)

    def cancel(self) -> None:
        """Mark the oversight session as cancelled."""
        with self._lock:
            self._cancelled = True

    def is_cancelled(self) -> bool:
        """Check whether the trial has been cancelled."""
        with self._lock:
            return self._cancelled

    def get_pending_steps(self) -> list[StudyStep]:
        """Return the current list of pending consequential steps (C2)."""
        with self._lock:
            return list(self._pending_steps)

    def clear_pending_steps(self) -> None:
        """Clear the pending consequential steps (used after batch commit)."""
        with self._lock:
            self._pending_steps.clear()

    # ------------------------------------------------------------------
    # Internal gate handlers
    # ------------------------------------------------------------------

    def _gate_stepwise(
        self, step: StudyStep, narration: str
    ) -> OversightDecision:
        """Execute a C1 stepwise confirmation gate."""
        display_narration = narration or step.narration or f"Step {step.id}"

        # Log the confirmation prompt (outside lock to avoid deadlocks)
        self._logger.confirmation_shown(
            step_id=step.id,
            narration=display_narration,
        )

        decision = self._prompt_user(step, display_narration)

        with self._lock:
            # Re-check cancellation after callback returns (BLOCKER: race after blocking callback)
            if self._cancelled:
                self._logger.confirmation_resolved(
                    step_id=step.id,
                    accepted=False,
                    details={"decision": "cancelled", "reason": "cancelled during callback", "condition": self._condition.value},
                )
                return OversightDecision(cancelled=True)

            if decision.cancelled:
                self._cancelled = True
                self._logger.confirmation_resolved(
                    step_id=step.id,
                    accepted=False,
                    details={"decision": "cancelled", "reason": decision.reason, "condition": self._condition.value},
                )
                return decision

            if decision.declined:
                self._declined_steps.append(step.id)
                self._logger.confirmation_resolved(
                    step_id=step.id,
                    accepted=False,
                    details={"decision": "declined", "reason": decision.reason, "condition": self._condition.value},
                )
                return decision

        # Confirmed — log outside lock
        self._logger.confirmation_resolved(
            step_id=step.id,
            accepted=True,
            details={"decision": "confirmed", "condition": self._condition.value},
        )
        return OversightDecision(confirmed=True)

    def _gate_batch(
        self, steps: Sequence[StudyStep], narrations: Sequence[str] | None = None
    ) -> OversightDecision:
        """Execute a C2 batch confirmation gate.

        All consequential steps collected during the trial are presented
        as a single review. The user can approve, decline specific steps,
        or cancel.

        Args:
            steps: The consequential steps.
            narrations: Effective narrations (optional — falls back to
                        ``step.narration or step.action``).
        """
        with self._lock:
            if self._cancelled:
                return OversightDecision(cancelled=True)

            if not steps:
                return OversightDecision(confirmed=True)

            if narrations:
                narration_parts = [
                    f"  {s.id}: {n}" for s, n in zip(steps, narrations)
                ]
            else:
                narration_parts = [
                    f"  {s.id}: {s.narration or s.action}" for s in steps
                ]
            batch_text = "\n".join(narration_parts)

        # Log the batch confirmation prompt
        self._logger.confirmation_shown(
            step_id="<batch>",
            narration=f"{batch_text} (batch_consequential, {len(steps)} steps)",
        )

        # Prompt batch with effective narrations so callback can see them
        decision = self._prompt_batch(steps, narrations)

        with self._lock:
            if decision.cancelled:
                self._cancelled = True
                return decision

        # Log one confirmation_resolved for the batch as a whole
        self._logger.confirmation_resolved(
            step_id="<batch>",
            accepted=not decision.declined,
            details={"decision": "declined" if decision.declined else "confirmed", "condition": self._condition.value, "num_steps": len(steps)},
        )

        return decision

    def _prompt_user(self, step: StudyStep, narration: str) -> OversightDecision:
        """Prompt the user to confirm a single step.

        In the Android integration, ``step_callback`` would be set to a
        function that displays a dialog and awaits a user response. If no
        callback is set, defaults to confirming the step.

        Args:
            step: The step to confirm.
            narration: The narration text to display.

        Returns:
            An ``OversightDecision`` reflecting the user's choice.
        """
        if self._step_callback is not None:
            return self._step_callback(step, narration)
        return OversightDecision(confirmed=True)

    def _prompt_batch(
        self, steps: Sequence[StudyStep], narrations: Sequence[str] | None = None
    ) -> OversightDecision:
        """Prompt the user to confirm a batch of steps.

        In the Android integration, ``batch_callback`` would be set to a
        function that displays a summary screen. If no callback is set,
        defaults to confirming the batch.

        Args:
            steps: The list of consequential steps to review.
            narrations: Effective narrations for each step (includes error descriptions).

        Returns:
            An ``OversightDecision`` reflecting the user's choice.
        """
        if self._batch_callback is not None:
            # Backward compatible: try narrations first, fall back to steps-only
            if narrations is not None:
                try:
                    return self._batch_callback(steps, narrations)
                except TypeError:
                    return self._batch_callback(steps)
            return self._batch_callback(steps)
        return OversightDecision(confirmed=True)

    def __repr__(self) -> str:
        return (
            f"OversightManager(condition={self._condition.value!r}, "
            f"cancelled={self._cancelled})"
        )


def _extract_action_description(action: str) -> str:
    """Extract a short human-readable description from an action string.

    Examples:
        "open com.android.settings" → "open com.android.settings"
        "click 'Send'" → "tap Send"
        "input text 'Hello' (submit)" → "type Hello"
        "scroll down" → "scroll down"
        "press BACK" → "press BACK"
    """
    if action.startswith(("click '", 'click "')):
        quote = action[6]
        end = action.index(quote, 7)
        return f"tap {action[7:end]}"

    if action.startswith(("input text '", 'input text "')):
        quote = action[11]
        rest = action[12:]
        end = rest.index(quote)
        return f"type {rest[:end]}"

    return action

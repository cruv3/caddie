"""Deterministic step execution for the study system.

The executor drives a ``ParticipantConfig`` + ``TrialSpec`` through a sequence
of typed ``StudyStep`` objects, resolving each action against the current
Android UI state.  It never falls back to an LLM — if a target cannot be
resolved the trial aborts immediately.

Key design decisions
--------------------
* **Backend protocol** — ``StudyBackendProtocol`` defines the interface the
  executor uses.  A real Android backend and a ``FakeBackend`` (for tests)
  both implement it.
* **Semantic resolution** — each ``StudyStep.action`` contains a short
  descriptor (e.g. ``"click 'Send'"`` or ``"open com.caddie/.MainActivity"``).
  The executor queries the backend's current element list and finds the
  matching target.
* **Narration enforcement** — every step emits a narration message via the
  logger.  The executor waits at least ``min_narration_ms`` (default 800 ms)
  before executing the action.
* **Oversight gates** — consequential steps pass through the ``oversight``
  callback (``confirm_consequential_step``) before execution.  C1 gates
  individually; C2 batches; C3 passes through unconditionally.
* **Error injection** — steps listed in ``TrialSpec.error_steps`` have their
  ``error_variant`` injected before the action fires.  Each step is resolved
  exactly once into a ``ResolvedStep`` which is reused for gate and execution.

Directory structure
-------------------
    caddie/study/
        executor.py       ← this module
        oversight.py      ← C1/C2/C3 gate logic
        session.py        ← session state management
        logger.py         ← JSONL event logging
        model.py          ← shared enums + dataclasses
"""

from __future__ import annotations

import abc
import time
from dataclasses import asdict, dataclass, field, replace
from typing import Any, Callable, Optional

from caddie.study.model import (
    StepType,
    StudyCondition,
    StudyStep,
    TrialSpec,
    TrialOutcome,
    VerificationRule,
)
from caddie.study.logger import StudyLogger, EventType
from caddie.study.oversight import OversightProtocol, OversightDecision
from caddie.study.verification import (
    VerificationBackendProtocol,
    VerificationManager,
    VerificationSummary,
)


# ---------------------------------------------------------------------------
# Backend protocol
# ---------------------------------------------------------------------------


class StudyBackendProtocol(abc.ABC):
    """Abstract interface between executor and the Android device.

    The executor calls these methods to interact with the phone.
    A real backend talks to the Android device; a ``FakeBackend`` for
    tests records calls and returns canned UI elements.
    """

    @abc.abstractmethod
    def list_elements(self) -> dict[str, Any]:
        """Return the current accessibility element tree.

        Returns a dict with an ``"elements"`` key containing a list of
        element dicts.  Each element dict may contain ``"index"``,
        ``"text"``, ``"content_description"``, ``"resource_id"``,
        ``"class"``, ``"clickable"``, and ``"bounds"``.
        """

    @abc.abstractmethod
    def open_app(self, package_name: str) -> dict[str, Any]:
        """Launch an application by its package name.

        Returns the result dict from the Android backend.
        """

    @abc.abstractmethod
    def open_url(self, url: str) -> dict[str, Any]:
        """Open a URL in the default browser.

        Returns the result dict from the Android backend.
        """

    @abc.abstractmethod
    def tap_element(self, index: int) -> dict[str, Any]:
        """Tap a UI element by its accessibility index.

        Returns the result dict from the Android backend.
        """

    @abc.abstractmethod
    def scroll(self, direction: str, amount: float = 0.6) -> dict[str, Any]:
        """Scroll the screen.

        Parameters
        ----------
        direction : str
            ``"up"``, ``"down"``, ``"left"``, or ``"right"``.
        amount : float
            Scroll distance as a fraction of screen size (0.0–1.0).

        Returns the result dict from the Android backend.
        """

    @abc.abstractmethod
    def press_button(self, button: str) -> dict[str, Any]:
        """Press a hardware / soft key.

        Parameters
        ----------
        button : str
            One of ``"BACK"``, ``"HOME"``, ``"RECENT"``.

        Returns the result dict from the Android backend.
        """

    @abc.abstractmethod
    def type_text(self, text: str, submit: bool = False) -> dict[str, Any]:
        """Type text into the focused input field.

        Parameters
        ----------
        text : str
            Text to type.
        submit : bool
            If True, press Enter after typing.

        Returns the result dict from the Android backend.
        """


# ---------------------------------------------------------------------------
# Step resolution helpers
# ---------------------------------------------------------------------------


def _parse_action(action: str) -> tuple[str, ...]:
    """Parse a study action string into (action_type, descriptor, ...).

    Supported formats:
    - ``"open <package>"``  → ("open_app", "<package>")
    - ``"open_url <url>"``  → ("open_url", "<url>")
    - ``"click '<label>'"`` → ("tap", "<label>")
    - ``"click <label>"``   → ("tap", "<label>")
    - ``"input text '<text>'"`` → ("type", "<text>")
    - ``"input text '<text>' (submit)"`` → ("type", "<text>", "submit")
    - ``"scroll <direction>"`` → ("scroll", "<direction>")
    - ``"press <button>"`` → ("press", "<button>")

    Returns a tuple of (action_type, descriptor[, submit]).
    """
    action = action.strip()

    # open_app
    if action.startswith("open "):
        return ("open_app", action[5:])

    # open_url
    if action.startswith("open_url "):
        return ("open_url", action[9:])

    # tap with quoted label
    if action.startswith(("click '", "click \"")):
        quote = action[6]
        end = action.index(quote, 7)
        label = action[7:end]
        return ("tap", label)

    if action.startswith("click "):
        label = action[6:].strip().strip("'\"")
        return ("tap", label)

    # type text with quoted content
    if action.startswith(("input text '", "input text \"")):
        quote = action[11]
        rest = action[12:]
        end = rest.index(quote)
        text = rest[:end]
        extra = rest[end + 1:]
        parts = ("type", text)
        if "(submit)" in extra:
            parts += ("submit",)
        return parts

    if action.startswith("input text "):
        parts_raw = action[11:].split(" ", 1)
        text = parts_raw[0].strip("'\"")
        extra = parts_raw[1] if len(parts_raw) > 1 else ""
        result = ("type", text)
        if "(submit)" in extra:
            result += ("submit",)
        return result

    # scroll
    if action.startswith("scroll "):
        direction = action[7:].strip()
        return ("scroll", direction)

    # press
    if action.startswith("press "):
        button = action[6:].strip()
        return ("press", button)

    raise ValueError(f"Unrecognised action format: {action!r}")


def _find_element(
    elements: list[dict],
    label: str,
) -> Optional[dict]:
    """Find an element matching the label with structured priority.

    Resolution order:
        1. Exact resource_id match
        2. Exact text/content_description match
        3. Substring match (case-insensitive)

    Returns ``None`` if zero or multiple ambiguous matches. (MAJOR: #37)
    """
    label_lower = label.casefold()

    # 1. Exact resource_id
    for el in elements:
        rid = (el.get("resource_id") or "").casefold()
        if rid == label_lower:
            return el

    # 2. Exact text/content_description
    for el in elements:
        text = (el.get("text") or "").casefold()
        desc = (el.get("content_description") or "").casefold()
        if text == label_lower or desc == label_lower:
            return el

    # 3. Substring match — check for ambiguity
    matches: list[dict] = []
    for el in elements:
        text = (el.get("text") or "").casefold()
        desc = (el.get("content_description") or "").casefold()
        rid = (el.get("resource_id") or "").casefold()
        if label_lower in text or label_lower in desc or label_lower in rid:
            matches.append(el)
    if len(matches) == 1:
        return matches[0]
    return None  # zero or ambiguous matches


# ---------------------------------------------------------------------------
# ResolvedStep — single point of resolution for each step
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class ResolvedStep:
    """A study step resolved with error injection applied.

    This is the single source of truth for a step's effective action,
    narration, and whether an error was actually injected.
    """

    source: StudyStep
    """The original step definition."""

    effective_action: str
    """The action string with error injection applied (if any)."""

    narration: str
    """Participant-facing narration (includes error description if injected)."""

    error_injected: bool
    """True if the controlled error was actually substituted."""

    error_variant_id: str | None
    """ID of the error variant, or None."""


# ---------------------------------------------------------------------------
# Result types
# ---------------------------------------------------------------------------


@dataclass
class ExecutionResult:
    """Result of a single step execution.

    Attributes
    ----------
    success : bool
        Whether the step executed successfully.
    step : StudyStep
        The step that was executed (or attempted).
    error : Optional[str]
        Error message if the step failed.
    action : Optional[str]
        The canonical action name that was performed (e.g. ``"tap_element"``).
    resolved_index : Optional[int]
        The accessibility index of the resolved target (for taps).
    elapsed_ms : float
        Wall-clock time spent on this step in milliseconds.
    error_variant_id : Optional[str]
        ID of the injected error variant, if this step had one.
    error_injected : bool
        True if this step had a controlled error actually applied.
    """

    success: bool
    step: StudyStep
    error: Optional[str] = None
    action: Optional[str] = None
    resolved_index: Optional[int] = None
    elapsed_ms: float = 0.0
    error_variant_id: Optional[str] = None
    error_injected: bool = False


@dataclass
class TrialResult:
    """Result of executing a complete trial.

    Attributes
    ----------
    outcome : TrialOutcome
        The final outcome of the trial.
    steps_executed : list[ExecutionResult]
        Results for each step that was attempted.
    steps_done : int
        Number of successfully executed steps.
    duration_ms : float
        Total wall-clock duration of the trial.
    reason : str
        Human-readable reason for the outcome.
    verification_passed : bool
        Whether all verification rules passed.
    verification_results : list[dict]
        Per-rule verification details.
    screenshots : list[str]
        Paths of captured screenshots.
    """

    outcome: TrialOutcome
    steps_executed: list[ExecutionResult] = field(default_factory=list)
    steps_done: int = 0
    duration_ms: float = 0.0
    reason: str = ""
    verification_passed: bool = False
    verification_results: list[dict] = field(default_factory=list)
    screenshots: list[str] = field(default_factory=list)


# ---------------------------------------------------------------------------
# TrialExecutor
# ---------------------------------------------------------------------------


class TrialExecutor:
    """Deterministic executor for a single study trial.

    Parameters
    ----------
    backend : StudyBackendProtocol
        Android device backend for executing actions.
    logger : StudyLogger
        Logger for emitting events.
    oversight : OversightProtocol
        Oversight gate callback for consequential steps.
    spec : TrialSpec
        The trial specification (steps, errors, verification).
    condition : StudyCondition
        The study condition controlling gate behavior.
    error_tasks : frozenset[str]
        Task IDs that should have controlled errors injected.
    """

    def __init__(
        self,
        backend: StudyBackendProtocol,
        logger: StudyLogger,
        oversight: OversightProtocol,
        spec: TrialSpec,
        condition: StudyCondition,
        error_tasks: frozenset[str] = frozenset(),
        verification_backend: Optional[VerificationBackendProtocol] = None,
        session: Optional["StudySession"] = None,
    ):
        self._backend = backend
        self._logger = logger
        self._oversight = oversight
        self._spec = spec
        self._condition = condition
        self._error_tasks = error_tasks
        self._verification_backend = verification_backend
        self._session = session
        self._steps_executed: list[ExecutionResult] = []
        self._start_mono: float = 0.0
        self._screenshots: list[str] = []

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def run(self) -> TrialResult:
        """Execute the full trial.

        Returns
        -------
        TrialResult
            The outcome and statistics for this trial.

        The entire method is wrapped in try/finally to ensure that
        trial_complete and session cleanup happen even if an exception
        occurs during logging, screenshots, gate evaluation, or state updates.
        """
        self._start_mono = time.monotonic()
        self._steps_executed = []
        self._screenshots = []
        _trial_id: str | None = None
        _outcome: TrialOutcome = TrialOutcome.TECHNICAL_FAILURE
        _reason: str = "Unexpected exception"
        _steps_done: int = 0

        try:
            # Log trial start
            self._trial_id = self._logger.trial_start(
                task_id=self._spec.id,
                block="main",
                variant="normal" if self._error_tasks.isdisjoint({self._spec.id}) else "error",
            )

            # Capture pre-trial screenshot
            self._screenshots.append(
                self._logger.screenshot_captured("pre_trial", trial_id=self._trial_id)
            )

            # Pre-resolve all consequential steps
            resolved_map: dict[str, ResolvedStep] = {}
            for step in self._spec.steps:
                resolved = self._resolve_step(step)
                resolved_map[step.id] = resolved

            # Execute steps
            steps = self._spec.steps

            # C2: collect pending consequential steps for batch summary
            c2_pending: list[ResolvedStep] = []
            c2_gate_shown = False

            for step_idx, step in enumerate(steps):
                # BLOCKER 6: Check trial deadline before each step
                if self._elapsed_ms() > self._spec.max_duration_s * 1000:
                    _outcome = TrialOutcome.CONFIRMATION_TIMEOUT
                    _reason = "Trial exceeded max_duration_s"
                    _steps_done = step_idx
                    return self._abort_trial(step_idx, _outcome, _reason)

                # Check cancellation
                if self._oversight.is_cancelled():
                    _outcome = TrialOutcome.ABORTED
                    _reason = "User cancelled the trial"
                    _steps_done = step_idx
                    return self._abort_trial(step_idx, _outcome, _reason)

                # Check session pause
                pause_decision = self._session.request_pause() if self._session else None
                if pause_decision is not None:
                    if pause_decision.cancelled:
                        _outcome = TrialOutcome.ABORTED
                        _reason = "Session cancelled"
                        _steps_done = step_idx
                        return self._abort_trial(step_idx, _outcome, _reason)

                # C2: first consequential step triggers gate, then continue normally
                if step.consequential and self._condition == StudyCondition.FINAL_CHECKPOINT:
                    resolved = resolved_map[step.id]
                    c2_pending.append(resolved)
                    if not c2_gate_shown:
                        c2_gate_shown = True
                        # Collect remaining consequential steps for the same batch
                        for later_step in steps[step_idx + 1:]:
                            if later_step.consequential:
                                c2_pending.append(resolved_map[later_step.id])
                        # Gate the batch — pass effective narrations for C2 display (BLOCKER B5)
                        c2_steps = [r.source for r in c2_pending]
                        c2_narrations = [r.narration for r in c2_pending]
                        show_fn = getattr(self._oversight, "show_c2_summary_with_narrations", None)
                        c2_decision = show_fn(
                            c2_steps, c2_narrations
                        ) if show_fn else self._oversight.show_c2_summary(c2_steps)
                        if not c2_decision.confirmed:
                            _outcome = TrialOutcome.ABORTED
                            _reason = "C2 summary declined" if c2_decision.declined else "C2 summary cancelled"
                            _steps_done = step_idx
                            return self._abort_trial(step_idx, _outcome, _reason)
                        # Apply modified_steps from C2 decision
                        for modified in c2_decision.modified_steps:
                            for r in c2_pending:
                                if r.source.id == modified.id:
                                    resolved_map[modified.id] = ResolvedStep(
                                        source=modified,
                                        effective_action=modified.action,
                                        narration=modified.narration or modified.action,
                                        error_injected=bool(modified.error_variant),
                                        error_variant_id=modified.error_variant.id if modified.error_variant else None,
                                    )
                        c2_pending.clear()

                # C1: individual gate for consequential steps
                if step.consequential and self._condition == StudyCondition.STEPWISE:
                    resolved = resolved_map[step.id]
                    # BLOCKER B3: per-step gate timeout enforcement
                    deadline = time.monotonic() + self._spec.per_gate_timeout_s
                    decision = self._oversight.confirm_consequential_step(
                        step, resolved.narration,
                    )
                    # Check if we exceeded the deadline (timeout after callback returns)
                    if time.monotonic() > deadline:
                        _outcome = TrialOutcome.CONFIRMATION_TIMEOUT
                        _reason = f"C1 gate timeout on step {step.id}"
                        _steps_done = step_idx
                        return self._abort_trial(step_idx, _outcome, _reason)
                    if not decision.confirmed:
                        _outcome = TrialOutcome.ABORTED
                        _reason = f"Step {step.id} declined in C1 gate"
                        _steps_done = step_idx
                        return self._abort_trial(step_idx, _outcome, _reason)

                # Execute the step (single resolution, single STEP_START)
                resolved = resolved_map[step.id]
                result = self._execute_step(step, resolved, step_idx)
                self._steps_executed.append(result)

                # Update session progress — only mark completed on success
                if result.success:
                    _steps_done = step_idx + 1
                    if self._session:
                        self._session.mark_step_executed(step_idx + 1)

                if not result.success:
                    # Step failed — abort trial
                    _outcome = TrialOutcome.TECHNICAL_FAILURE
                    _reason = result.error or "Step execution failed"
                    return self._abort_trial(step_idx, _outcome, _reason)

            # Run post-trial verification
            verification_passed = False
            verification_results: list[dict] = []
            if self._verification_backend:
                summary = self._run_verification(
                    self._trial_id,
                    self._verification_backend,
                )
                verification_passed = summary.all_passed
                verification_results = [asdict(r) for r in summary.results]

            # Determine final outcome based on verification
            if verification_passed:
                _outcome = TrialOutcome.SUCCESS
                _reason = "All steps completed successfully"
            elif self._verification_backend:
                _outcome = TrialOutcome.VERIFICATION_FAILED
                _reason = "Post-trial verification failed"
            else:
                _outcome = TrialOutcome.SUCCESS
                _reason = "All steps completed successfully (no verification backend)"

            # Log trial complete
            self._logger.trial_complete(
                self._trial_id,
                outcome=_outcome.value,
                total_steps=len(steps),
                errors_injected=self._count_errors_injected(),
                duration_ms=self._elapsed_ms(),
            )

            # Write summary JSON for the trial
            try:
                self._logger.write_summary(
                    outcome=_outcome.value,
                    total_steps=len(steps),
                    errors_injected=self._count_errors_injected(),
                    duration_ms=self._elapsed_ms(),
                    trial_ids=[self._trial_id] if self._trial_id else [],
                    verification_results={
                        "all_passed": verification_passed,
                    } if verification_results else None,
                    screenshots=self._screenshots,
                )
            except Exception:
                pass  # Non-critical: events.jsonl is the authoritative record

            result = TrialResult(
                outcome=_outcome,
                steps_executed=self._steps_executed,
                steps_done=_steps_done,
                duration_ms=self._elapsed_ms(),
                reason=_reason,
                verification_passed=verification_passed,
                verification_results=verification_results,
                screenshots=self._screenshots,
            )

            # Signal session completion
            if self._session:
                self._session.mark_trial_complete(_outcome)

            return result

        except Exception as exc:
            # Catch-all for any unexpected exception — ensure cleanup
            trial_id = getattr(self, '_trial_id', None)
            if trial_id is not None:
                try:
                    self._logger.trial_complete(
                        trial_id,
                        outcome=TrialOutcome.TECHNICAL_FAILURE.value,
                        total_steps=0,
                        errors_injected=0,
                        duration_ms=self._elapsed_ms(),
                    )
                except Exception:
                    pass
            if self._session:
                try:
                    self._session.request_stop()
                except Exception:
                    pass
            return TrialResult(
                outcome=TrialOutcome.TECHNICAL_FAILURE,
                steps_executed=self._steps_executed,
                steps_done=_steps_done,
                duration_ms=self._elapsed_ms(),
                reason=f"Unexpected error: {exc}",
            )
        finally:
            # Ensure session is stopped on any terminal path
            if self._session and not self._session.is_terminal:
                try:
                    self._session.request_stop()
                except Exception:
                    pass

    def _abort_trial(
        self,
        step_idx: int,
        outcome: TrialOutcome,
        reason: str,
    ) -> TrialResult:
        """Centralized trial abortion with consistent finalization.

        BLOCKER 11: All exit paths go through this method to ensure
        trial_complete, session stop, and resources are consistently
        handled.
        """
        self._logger.trial_complete(
            self._trial_id,
            outcome=outcome.value,
            total_steps=step_idx,
            errors_injected=self._count_errors_injected(),
            duration_ms=self._elapsed_ms(),
        )
        if self._session:
            self._session.request_stop()
        return TrialResult(
            outcome=outcome,
            steps_executed=self._steps_executed,
            steps_done=step_idx,
            duration_ms=self._elapsed_ms(),
            reason=reason,
        )

    # ------------------------------------------------------------------
    # Step resolution
    # ------------------------------------------------------------------

    def _resolve_step(self, step: StudyStep) -> ResolvedStep:
        """Resolve error-injected action and narration for a step.

        Returns a ``ResolvedStep`` containing the effective action,
        narration, and whether error was actually substituted.
        """
        if (
            step.id in self._spec.error_steps
            and step.error_variant
            and self._spec.id in self._error_tasks
        ):
            ev = step.error_variant
            effective_action, actually_injected = self._inject_error(step.action, ev)
            narration = (
                f"{step.narration or step.action} ({ev.description})"
                if actually_injected
                else step.narration or step.action
            )
            return ResolvedStep(
                source=step,
                effective_action=effective_action,
                narration=narration,
                error_injected=actually_injected,
                error_variant_id=ev.id,
            )
        return ResolvedStep(
            source=step,
            effective_action=step.action,
            narration=step.narration or step.action,
            error_injected=False,
            error_variant_id=None,
        )

    def _inject_error(
        self, action: str, error_variant: "ErrorVariant"
    ) -> tuple[str, bool]:
        """Inject an error variant into an action string.

        Replaces the correct_value with the wrong_value in the action
        descriptor.

        Parameters
        ----------
        action : str
            The original action string (e.g. "input text 'Max'").
        error_variant : ErrorVariant
            The error variant with correct_value and wrong_value.

        Returns
        -------
        tuple[str, bool]
            (action string with substitution, whether substitution occurred)
        """
        cv = error_variant.correct_value
        wv = error_variant.wrong_value
        if cv in action:
            return action.replace(cv, wv, 1), True
        return action, False

    # ------------------------------------------------------------------
    # Step execution
    # ------------------------------------------------------------------

    def _execute_step(
        self, step: StudyStep, resolved: ResolvedStep, step_index: int
    ) -> ExecutionResult:
        """Execute a single study step.

        Parameters
        ----------
        step : StudyStep
            The original step definition.
        resolved : ResolvedStep
            Pre-resolved step with effective action and narration.
        step_index : int
            0-based index for logging purposes.

        Returns
        -------
        ExecutionResult
            The result of the step execution.
        """
        step_start = time.monotonic()

        # Single STEP_START per step (BLOCKER 4: no duplicate logging)
        self._logger.step_start(
            step.id, resolved.narration, trial_id=self._trial_id
        )

        # Log error exactly once, right before execution (BLOCKER 3)
        if resolved.error_injected and resolved.error_variant_id:
            ev = step.error_variant
            if ev:
                self._logger.error_injected(
                    step.id,
                    trial_id=self._trial_id,
                    error_variant_id=resolved.error_variant_id,
                    field=ev.field,
                    wrong_value=ev.wrong_value,
                    correct_value=ev.correct_value,
                )

        # Capture pre-action screenshot only for commit/error steps (Spec §7)
        if resolved.source.step_type in (StepType.COMMIT,) or resolved.error_variant_id is not None:
            screenshot_label = f"pre_action_{step_index}"
            self._screenshots.append(
                self._logger.screenshot_captured(screenshot_label, trial_id=self._trial_id)
            )

        # Execute the action (action parsing inside try)
        action_result = self._perform_action(resolved)

        if not action_result:
            return ExecutionResult(
                success=False,
                step=step,
                error="Action could not be performed",
                error_variant_id=resolved.error_variant_id,
                error_injected=resolved.error_injected,
            )

        action_type, action_desc, resolved_index = action_result
        step_finish = time.monotonic()
        elapsed = (step_finish - step_start) * 1000

        # Log step finish
        self._logger.step_finish(step.id, trial_id=self._trial_id, action=action_desc)

        return ExecutionResult(
            success=True,
            step=step,
            action=action_type,
            resolved_index=resolved_index,
            elapsed_ms=elapsed,
            error_variant_id=resolved.error_variant_id,
            error_injected=resolved.error_injected,
        )

    def _perform_action(
        self, resolved: ResolvedStep
    ) -> Optional[tuple[str, str, Optional[int]]]:
        """Perform the action defined in a resolved step against the backend.

        Returns (canonical_action_name, description, resolved_index) on success,
        or ``None`` if the action cannot be performed (target not found).

        BLOCKER 5: Checks backend return values for success status.
        BLOCKER 12: Action parsing inside try block.
        BLOCKER 13: Honor min_narration_ms exactly (no 800ms floor).
        """
        desc = ""
        try:
            action_type, *rest = _parse_action(resolved.effective_action)
            desc = rest[0] if rest else ""
        except ValueError as e:
            self._logger.technical_failure(error=str(e))
            return None

        # Wait minimum narration duration — honor exactly, no floor (BLOCKER 13)
        min_ms = resolved.source.min_narration_ms
        if min_ms > 0:
            time.sleep(min_ms / 1000.0)

        try:
            if action_type == "open_app":
                result = self._backend.open_app(desc)
                # BLOCKER 5: check backend success
                if isinstance(result, dict) and result.get("success") is False:
                    self._logger.technical_failure(
                        error=result.get("error", "open_app returned success=False")
                    )
                    return None
                return ("open_app", f"App öffnen ({desc})", None)

            if action_type == "open_url":
                result = self._backend.open_url(desc)
                if isinstance(result, dict) and result.get("success") is False:
                    self._logger.technical_failure(
                        error=result.get("error", "open_url returned success=False")
                    )
                    return None
                return ("open_url", f"URL öffnen ({desc})", None)

            if action_type == "tap":
                elements_result = self._backend.list_elements()
                elements = elements_result.get("elements", [])
                el = _find_element(elements, desc)
                if el is None:
                    return None
                result = self._backend.tap_element(el["index"])
                # BLOCKER 5: check backend success
                if isinstance(result, dict) and result.get("success") is False:
                    self._logger.technical_failure(
                        error=result.get("error", "tap_element returned success=False")
                    )
                    return None
                return ("tap", f"Auf '{desc}' tippen (index={el['index']})", el["index"])

            if action_type == "type":
                submit = "submit" in rest
                result = self._backend.type_text(desc, submit=submit)
                if isinstance(result, dict) and result.get("success") is False:
                    self._logger.technical_failure(
                        error=result.get("error", "type_text returned success=False")
                    )
                    return None
                return ("type", f"Text eingeben: '{desc}'", None)

            if action_type == "scroll":
                direction = desc
                result = self._backend.scroll(direction)
                if isinstance(result, dict) and result.get("success") is False:
                    self._logger.technical_failure(
                        error=result.get("error", f"scroll({direction}) returned success=False")
                    )
                    return None
                return ("scroll", f"Nach {direction} scrollen", None)

            if action_type == "press":
                result = self._backend.press_button(desc)
                if isinstance(result, dict) and result.get("success") is False:
                    self._logger.technical_failure(
                        error=result.get("error", f"press({desc}) returned success=False")
                    )
                    return None
                return ("press", f"Taste {desc}", None)

        except Exception as e:
            self._logger.technical_failure(error=str(e))
            return None

        return None

    # ------------------------------------------------------------------
    # Verification
    # ------------------------------------------------------------------

    def _run_verification(
        self,
        trial_id: str,
        backend: VerificationBackendProtocol,
    ) -> VerificationSummary:
        """Run post-trial verification against the backend."""
        manager = VerificationManager(
            logger=self._logger,
            backend=backend,
            rules=self._spec.verification,
            trial_id=self._trial_id,
        )
        return manager.run()

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------

    def _count_errors_injected(self) -> int:
        """Count how many error steps were actually injected in this trial.

        Uses the `error_injected` flag on ExecutionResult for accuracy
        (BLOCKER 16: checks actual injection, not just presence of error_variant_id).
        """
        return sum(
            1 for r in self._steps_executed
            if r.error_injected and r.success
        )

    def _elapsed_ms(self) -> float:
        """Return monotonic elapsed time in milliseconds since trial start."""
        return (time.monotonic() - self._start_mono) * 1000

    @property
    def screenshots(self) -> list[str]:
        """Return captured screenshot paths."""
        return self._screenshots


# ---------------------------------------------------------------------------
# Convenience function
# ---------------------------------------------------------------------------


def execute_trial(
    backend: StudyBackendProtocol,
    logger: StudyLogger,
    oversight: OversightProtocol,
    spec: TrialSpec,
    condition: StudyCondition,
    error_tasks: frozenset[str] = frozenset(),
) -> TrialResult:
    """Execute a single study trial.

    This is the main public entry point.  It creates a ``TrialExecutor``
    and runs the full trial, returning a ``TrialResult``.

    Parameters
    ----------
    backend : StudyBackendProtocol
        Android device backend.
    logger : StudyLogger
        Study logger for event recording.
    oversight : OversightProtocol
        Oversight gate callback.
    spec : TrialSpec
        Trial specification.
    condition : StudyCondition
        Study condition (C1 / C2 / C3).
    error_tasks : frozenset[str]
        Task IDs scheduled for controlled error injection.

    Returns
    -------
    TrialResult
        The outcome and statistics of the trial.
    """
    executor = TrialExecutor(
        backend=backend,
        logger=logger,
        oversight=oversight,
        spec=spec,
        condition=condition,
        error_tasks=error_tasks,
    )
    return executor.run()

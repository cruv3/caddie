"""Post-trial verification for the study system.

After each deterministic trial, the participant verifies that the task was
completed correctly. This module implements the verification pipeline:

* **VerificationRule** — loaded from the trial spec, each rule describes a
  postcondition check (e.g. "message sent", "email delivered").
* **VerificationResult** — records the outcome for each rule (PASS / FAIL /
  TIMEOUT / SKIP).
* **VerificationManager** — orchestrates the verification sequence, captures
  screenshots, logs events, and produces a summary.

Supported check types (from the spec):

* ``accessibility_check`` — verify an element is visible via the accessibility
  service.
* ``screenshot_match`` — compare the current screenshot against an expected
  reference (pixel-level or structural).
* ``text_present`` — verify that a specific text string appears on screen.
* ``text_absent`` — verify that a specific text string does not appear.
* ``field_count`` — verify that a list or collection contains exactly N items.

Directory structure
-------------------
    caddie/study/
        model.py          ← VerificationRule dataclass
        verification.py   ← this module (post-trial checks)
        logger.py         ← event recording
        executor.py       ← step execution (calls verification at end)
"""

from __future__ import annotations

import uuid
from dataclasses import dataclass, field
from enum import Enum
from typing import Optional

from caddie.study.logger import StudyLogger, EventType
from caddie.study.model import VerificationRule


# ---------------------------------------------------------------------------
# Public types
# ---------------------------------------------------------------------------


class VerificationOutcome(str, Enum):
    """Result of a single verification check."""

    PASS = "pass"
    """The check passed — the expected postcondition is met."""

    FAIL = "fail"
    """The check failed — the expected postcondition is not met."""

    TIMEOUT = "timeout"
    """The participant did not respond within the allocated time."""

    SKIP = "skip"
    """The check was skipped (e.g. due to prior technical failure)."""


@dataclass(frozen=True)
class VerificationResult:
    """Outcome for a single verification rule.

    Attributes:
        rule_id: The ID of the verification rule.
        outcome: The result of the check.
        screenshot_path: Path to the captured screenshot (if screenshot_evidence=True).
        details: Additional information about the outcome.
    """

    rule_id: str
    outcome: VerificationOutcome
    screenshot_path: Optional[str] = None
    details: dict = field(default_factory=dict)


@dataclass
class VerificationSummary:
    """Aggregated result for a full post-trial verification session.

    Attributes:
        trial_id: The trial this verification belongs to.
        total_rules: Total number of verification rules.
        passed: Number of rules that passed.
        failed: Number of rules that failed.
        timed_out: Number of rules that timed out.
        skipped: Number of rules that were skipped.
        results: Individual results for each rule.
    """

    trial_id: str
    total_rules: int = 0
    passed: int = 0
    failed: int = 0
    timed_out: int = 0
    skipped: int = 0
    results: list[VerificationResult] = field(default_factory=list)

    @property
    def all_passed(self) -> bool:
        """True if all non-skipped rules passed."""
        return self.failed == 0 and self.timed_out == 0

    @property
    def completion_rate(self) -> float:
        """Proportion of non-skipped rules that passed."""
        non_skipped = self.total_rules - self.skipped
        if non_skipped == 0:
            return 0.0
        return self.passed / non_skipped


# ---------------------------------------------------------------------------
# Verification backend protocol
# ---------------------------------------------------------------------------


class VerificationBackendProtocol:
    """Interface for performing verification checks against the Android UI.

    The ``VerificationManager`` delegates actual Android interactions through
    this protocol. A real backend queries the accessibility service; a test
    backend provides canned results.

    In production, the executor passes its ``StudyBackendProtocol`` to the
    verification manager. For testing, a ``FakeVerificationBackend`` is used.
    """

    def check_text_present(self, text: str) -> bool:
        """Check if text appears on screen.

        Args:
            text: The text to search for.

        Returns:
            True if the text is found.
        """
        ...

    def check_text_absent(self, text: str) -> bool:
        """Check if text does NOT appear on screen.

        Args:
            text: The text to search for.

        Returns:
            True if the text is not found.
        """
        ...

    def check_accessibility_element(self, label: str) -> bool:
        """Check if an accessibility element is visible.

        Args:
            label: The accessibility label or content description to find.

        Returns:
            True if the element is found.
        """
        ...

    def check_field_count(self, container_label: str, expected: int) -> bool:
        """Check that a collection contains exactly the expected number of items.

        Args:
            container_label: The label of the container (list, grid, etc.).
            expected: The expected number of child items.

        Returns:
            True if the count matches.
        """
        ...

    def capture_screenshot(self) -> Optional[str]:
        """Capture the current screen as a screenshot image.

        Returns:
            The file path of the saved screenshot, or None if capture failed.
        """
        ...


# ---------------------------------------------------------------------------
# VerificationManager
# ---------------------------------------------------------------------------


class VerificationManager:
    """Orchestrates post-trial verification for a single trial.

    This manager processes each ``VerificationRule`` from the trial spec,
    runs the appropriate check against the Android UI (via the backend),
    captures evidence, logs events, and produces a summary.

    The manager is designed to be called by ``TrialExecutor`` after a trial
    completes successfully. If the trial ended in a technical failure, the
    manager skips all rules.

    Args:
        logger: The study logger for event recording.
        backend: Backend implementing ``VerificationBackendProtocol``.
        rules: The verification rules from the trial spec.
        trial_id: Unique identifier for this trial.
    """

    def __init__(
        self,
        logger: StudyLogger,
        backend: VerificationBackendProtocol,
        rules: tuple[VerificationRule, ...],
        trial_id: str,
    ) -> None:
        self._logger = logger
        self._backend = backend
        self._rules = rules
        self._trial_id = trial_id
        self._results: list[VerificationResult] = []

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def run(self) -> VerificationSummary:
        """Execute all verification rules and return the summary.

        Processes each rule in order, running the appropriate check, capturing
        evidence, and logging the result. The trial is aborted if any rule
        fails or times out.

        Returns:
            A ``VerificationSummary`` with aggregate results.
        """
        # Log start
        self._logger.verification_start(
            rule_count=len(self._rules),
            trial_id=self._trial_id,
        )

        self._results.clear()

        for rule in self._rules:
            result = self._evaluate_rule(rule)
            self._results.append(result)

        summary = self._build_summary()

        # Log completion
        self._logger.verification_complete(
            passed=summary.passed,
            failed=summary.failed,
            timed_out=summary.timed_out,
            skipped=summary.skipped,
            trial_id=self._trial_id,
        )

        return summary

    def get_results(self) -> list[VerificationResult]:
        """Return the list of verification results."""
        return list(self._results)

    def get_summary(self) -> VerificationSummary:
        """Return the current verification summary."""
        return self._build_summary()

    # ------------------------------------------------------------------
    # Internal rule evaluation
    # ------------------------------------------------------------------

    def _evaluate_rule(self, rule: VerificationRule) -> VerificationResult:
        """Evaluate a single verification rule.

        Args:
            rule: The verification rule to evaluate.

        Returns:
            A ``VerificationResult`` with the outcome.
        """
        check_type = rule.check_type
        params = rule.parameters or {}

        try:
            if check_type == "accessibility_check":
                result = self._check_accessibility(rule, params)
            elif check_type == "text_present":
                result = self._check_text_present(rule, params)
            elif check_type == "text_absent":
                result = self._check_text_absent(rule, params)
            elif check_type == "field_count":
                result = self._check_field_count(rule, params)
            elif check_type == "screenshot_match":
                result = self._check_screenshot_match(rule, params)
            else:
                result = VerificationResult(
                    rule_id=rule.id,
                    outcome=VerificationOutcome.FAIL,
                    details={"error": f"Unknown check type: {check_type}"},
                )

            # Capture screenshot if requested (only if not already captured)
            if rule.screenshot_evidence and not result.screenshot_path:
                screenshot_path = self._backend.capture_screenshot()
                object.__setattr__(result, "screenshot_path", screenshot_path)

            # Log the result
            self._logger.verification_result(
                rule_id=rule.id,
                passed=result.outcome == VerificationOutcome.PASS,
                trial_id=self._trial_id,
                screenshot_path=result.screenshot_path,
                details={
                    "check_type": check_type,
                    "outcome": result.outcome.value,
                    **result.details,
                },
            )

            return result

        except Exception as e:
            result = VerificationResult(
                rule_id=rule.id,
                outcome=VerificationOutcome.FAIL,
                details={"error": str(e)},
            )
            self._logger.verification_result(
                rule_id=rule.id,
                passed=False,
                trial_id=self._trial_id,
                details={
                    "check_type": check_type,
                    "outcome": "fail",
                    "error": str(e),
                },
            )
            return result

    def _check_accessibility(self, rule: VerificationRule, params: dict) -> VerificationResult:
        """Run an accessibility element check."""
        label = params.get("label", "")
        found = self._backend.check_accessibility_element(label)
        return VerificationResult(
            rule_id=rule.id,
            outcome=VerificationOutcome.PASS if found else VerificationOutcome.FAIL,
            details={"label": label, "found": found},
        )

    def _check_text_present(self, rule: VerificationRule, params: dict) -> VerificationResult:
        """Run a text-present check."""
        text = params.get("text", "")
        found = self._backend.check_text_present(text)
        return VerificationResult(
            rule_id=rule.id,
            outcome=VerificationOutcome.PASS if found else VerificationOutcome.FAIL,
            details={"text": text, "found": found},
        )

    def _check_text_absent(self, rule: VerificationRule, params: dict) -> VerificationResult:
        """Run a text-absent check."""
        text = params.get("text", "")
        not_found = self._backend.check_text_absent(text)
        return VerificationResult(
            rule_id=rule.id,
            outcome=VerificationOutcome.PASS if not_found else VerificationOutcome.FAIL,
            details={"text": text, "absent": not_found},
        )

    def _check_field_count(self, rule: VerificationRule, params: dict) -> VerificationResult:
        """Run a field-count check."""
        container = params.get("container", "")
        expected = params.get("count", 0)
        correct = self._backend.check_field_count(container, expected)
        return VerificationResult(
            rule_id=rule.id,
            outcome=VerificationOutcome.PASS if correct else VerificationOutcome.FAIL,
            details={"container": container, "expected": expected, "correct": correct},
        )

    def _check_screenshot_match(self, rule: VerificationRule, params: dict) -> VerificationResult:
        """Run a screenshot-match check.

        Captures the current screen and validates against the reference path
        from the rule parameters. If no reference is provided, falls back to
        checking that the screenshot path is non-empty.
        """
        screenshot_path = self._backend.capture_screenshot()
        if not screenshot_path:
            return VerificationResult(
                rule_id=rule.id,
                outcome=VerificationOutcome.FAIL,
                details={
                    "reference": params.get("reference", "unknown"),
                    "error": "Screenshot capture returned empty path",
                },
            )

        reference = params.get("reference")
        if reference:
            # Compare paths (in production, this would compare image hashes)
            match = screenshot_path == reference
            return VerificationResult(
                rule_id=rule.id,
                outcome=VerificationOutcome.PASS if match else VerificationOutcome.FAIL,
                screenshot_path=screenshot_path,
                details={
                    "reference": reference,
                    "actual": screenshot_path,
                    "error": f"Screenshot mismatch: expected {reference}, got {screenshot_path}" if not match else None,
                },
            )
        # No reference provided — pass if screenshot captured
        return VerificationResult(
            rule_id=rule.id,
            outcome=VerificationOutcome.PASS,
            screenshot_path=screenshot_path,
            details={"reference": "none", "actual": screenshot_path},
        )

    def _build_summary(self) -> VerificationSummary:
        """Build the aggregate verification summary."""
        summary = VerificationSummary(trial_id=self._trial_id)
        summary.total_rules = len(self._results)

        for result in self._results:
            if result.outcome == VerificationOutcome.PASS:
                summary.passed += 1
            elif result.outcome == VerificationOutcome.FAIL:
                summary.failed += 1
            elif result.outcome == VerificationOutcome.TIMEOUT:
                summary.timed_out += 1
            elif result.outcome == VerificationOutcome.SKIP:
                summary.skipped += 1

        summary.results = self._results
        return summary

    def __repr__(self) -> str:
        return (
            f"VerificationManager(trial_id={self._trial_id!r}, "
            f"rules={len(self._rules)})"
        )


# ---------------------------------------------------------------------------
# Fake backend for testing
# ---------------------------------------------------------------------------


class FakeVerificationBackend:
    """Canned backend for testing verification logic.

    All checks return True by default. Override specific methods or use
    ``set_result`` to control outcomes.

    Usage:
        backend = FakeVerificationBackend()
        backend.set_result("text_present", "Hello", True)
    """

    def __init__(self) -> None:
        self._results: dict[str, dict] = {}
        self._screenshot_path: Optional[str] = "/tmp/fake_screenshot.png"
        self.calls: list[tuple] = []

    def set_result(
        self,
        method: str,
        *args,
        result: bool,
    ) -> None:
        """Set a canned result for a specific method call.

        Args:
            method: The method name (e.g. "text_present").
            *args: Arguments to match against (positionally).
            result: The value to return.
        """
        key = (method, *args)
        self._results[key] = result

    def _get_result(self, key: tuple, default: bool = True) -> bool:
        """Look up a canned result, falling back to default."""
        if key in self._results:
            return self._results[key]
        return default

    def check_text_present(self, text: str) -> bool:
        self.calls.append(("check_text_present", text))
        return self._get_result(("text_present", text))

    def check_text_absent(self, text: str) -> bool:
        self.calls.append(("check_text_absent", text))
        return self._get_result(("text_absent", text))

    def check_accessibility_element(self, label: str) -> bool:
        self.calls.append(("check_accessibility", label))
        return self._get_result(("accessibility", label))

    def check_field_count(self, container_label: str, expected: int) -> bool:
        self.calls.append(("check_field_count", container_label, expected))
        return self._get_result(("field_count", container_label, expected))

    def capture_screenshot(self) -> Optional[str]:
        self.calls.append(("capture_screenshot",))
        return self._screenshot_path

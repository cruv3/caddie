"""Screen-off micro-trials: one-minute scheduling and three initiation policies.

This module manages the screen-off block of the study protocol. After the
experimenter instructs the participant to put the phone down, a timer waits
for one minute, then initiates the configured policy:

* NOTIFY_ONLY — post a notification without waking the screen
* WAKE_ASK — wake the screen and show the confirmation card
* WAKE_EXECUTE — wake the screen and execute the first action immediately

The module records the display state before the timer, the policy used, and
the outcome.
"""

from __future__ import annotations

import logging
import threading
import time
from dataclasses import dataclass, field
from typing import Callable, Optional

from caddie.study.logger import StudyLogger
from caddie.study.model import InitiationResult, ScreenOffMode

logger = logging.getLogger(__name__)

# Default wait time in seconds for the screen-off timer
DEFAULT_WAIT_SECONDS = 60


# ---------------------------------------------------------------------------
# Data classes
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class ScreenOffConfig:
    """Configuration for a single screen-off micro-trial.

    Args:
        mode: The initiation policy to use.
        task_id: The task ID to run after initiation.
        step_index: Which step to execute (WAKE_EXECUTE only).
    """

    mode: ScreenOffMode
    task_id: str
    step_index: int = 0

    def __repr__(self) -> str:
        return (
            f"ScreenOffConfig(mode={self.mode.value!r}, "
            f"task_id={self.task_id!r})"
        )


@dataclass
class ScreenOffResult:
    """Result of running a screen-off micro-trial.

    Args:
        mode: The initiation policy used.
        task_id: The task ID.
        display_was_on: Whether the screen was on before the timer.
        wait_seconds: How long the timer waited.
        result: The initiation outcome.
        elapsed_ms: Total time from start to result.
        message: Human-readable detail.
    """

    mode: ScreenOffMode
    task_id: str
    display_was_on: bool
    wait_seconds: int
    result: InitiationResult
    elapsed_ms: int
    message: str = ""

    @property
    def success(self) -> bool:
        return self.result is InitiationResult.SUCCESS

    def __repr__(self) -> str:
        return (
            f"ScreenOffResult(mode={self.mode.value!r}, "
            f"result={self.result.value!r})"
        )


# ---------------------------------------------------------------------------
# Notification backend protocol
# ---------------------------------------------------------------------------

NotificationFn = Callable[
    [],
    bool,
]
"""Post a silent study notification. Returns True on success."""


class ScreenOffManager:
    """Manages screen-off micro-trials.

    The manager handles:
    - Starting the one-minute timer
    - Posting notifications or waking the screen
    - Logging events to the StudyLogger
    - Tracking results

    Args:
        logger: The study logger.
        wait_seconds: Timer duration in seconds (default 60).
        notification_fn: Callable to post a notification when display is off.
        wake_display_fn: Callable to wake the display.
        check_display_fn: Callable to check if the display is currently on.
        show_confirmation_fn: Callable to show a confirmation card on the screen.
        execute_step_fn: Callable to execute a step after waking (returns success bool).
    """

    def __init__(
        self,
        logger: StudyLogger,
        wait_seconds: int = DEFAULT_WAIT_SECONDS,
        notification_fn: Optional[NotificationFn] = None,
        wake_display_fn: Optional[Callable[[], bool]] = None,
        check_display_fn: Optional[Callable[[], bool]] = None,
        show_confirmation_fn: Optional[Callable[[], bool]] = None,
        execute_step_fn: Optional[Callable[[], bool]] = None,
    ) -> None:
        self._logger = logger
        self._wait_seconds = wait_seconds
        self._notification_fn = notification_fn
        self._wake_display_fn = wake_display_fn
        self._check_display_fn = check_display_fn
        self._show_confirmation_fn = show_confirmation_fn
        self._execute_step_fn = execute_step_fn
        self._cancelled = False
        self._lock = threading.Lock()
        self._last_result: Optional[ScreenOffResult] = None

    # ------------------------------------------------------------------
    # Run a single screen-off micro-trial
    # ------------------------------------------------------------------

    def run(self, config: ScreenOffConfig) -> ScreenOffResult:
        """Execute a screen-off micro-trial with the given configuration.

        This method blocks for the configured wait time, then executes the
        initiation policy. The result is logged and cached.

        Args:
            config: The micro-trial configuration.

        Returns:
            The result of the micro-trial.
        """
        start = time.monotonic()

        # Check initial display state
        display_was_on = self._check_display()

        # Log initiation
        self._logger.screen_off_initiation(
            task_id=config.task_id,
            mode=config.mode.value,
            display_was_off=not display_was_on,
            wake_succeeded=False,  # not yet woken
            details={"initial_display_on": display_was_on},
        )

        # Sleep for the configured wait time (interruptible by lock)
        with self._lock:
            # Check cancellation before sleeping
            if self._cancelled:
                return ScreenOffResult(
                    mode=config.mode,
                    task_id=config.task_id,
                    display_was_on=display_was_on,
                    wait_seconds=self._wait_seconds,
                    result=InitiationResult.NO_RESPONSE,
                    elapsed_ms=int((time.monotonic() - start) * 1000),
                    message="Cancelled before timer",
                )
            # Sleep in small increments to allow cancellation
            remaining = self._wait_seconds
            while remaining > 0:
                slept = min(1.0, remaining)
                self._lock.release()
                time.sleep(slept)
                self._lock.acquire()
                remaining = max(0.0, remaining - slept)
            # Final cancellation check after sleep
            if self._cancelled:
                return ScreenOffResult(
                    mode=config.mode,
                    task_id=config.task_id,
                    display_was_on=display_was_on,
                    wait_seconds=self._wait_seconds,
                    result=InitiationResult.NO_RESPONSE,
                    elapsed_ms=int((time.monotonic() - start) * 1000),
                    message="Cancelled during timer",
                )

        result = self._wait_and_initiate(config, display_was_on)
        elapsed_ms = int((time.monotonic() - start) * 1000)
        result.elapsed_ms = elapsed_ms

        # Log completion
        self._log_initiation_result(result)
        self._last_result = result

        return result

    # ------------------------------------------------------------------
    # Initiation policy execution
    # ------------------------------------------------------------------

    def _wait_and_initiate(
        self,
        config: ScreenOffConfig,
        display_was_on: bool,
    ) -> ScreenOffResult:
        """Execute the initiation policy after the timer has elapsed.

        Args:
            config: The micro-trial configuration.
            display_was_on: The display state at the start of the micro-trial.

        Returns:
            The result of the initiation.
        """

        if config.mode is ScreenOffMode.NOTIFY_ONLY:
            return self._notify_only(config, display_was_on)
        elif config.mode is ScreenOffMode.WAKE_ASK:
            return self._wake_ask(config, display_was_on)
        elif config.mode is ScreenOffMode.WAKE_EXECUTE:
            return self._wake_execute(config, display_was_on)
        else:
            return ScreenOffResult(
                mode=config.mode,
                task_id=config.task_id,
                display_was_on=display_was_on,
                wait_seconds=self._wait_seconds,
                result=InitiationResult.SUCCESS,
                elapsed_ms=0,
                message=f"Unknown mode: {config.mode.value}",
            )

    def _notify_only(self, config: ScreenOffConfig, display_was_on: bool) -> ScreenOffResult:
        """Execute NOTIFY_ONLY policy.

        Posts a silent notification without waking the display.
        """
        if self._notification_fn is None:
            return ScreenOffResult(
                mode=config.mode,
                task_id=config.task_id,
                display_was_on=display_was_on,
                wait_seconds=self._wait_seconds,
                result=InitiationResult.SUCCESS,
                elapsed_ms=0,
                message="No notification backend configured (dry-run)",
            )

        try:
            success = self._notification_fn()
            if success:
                return ScreenOffResult(
                    mode=config.mode,
                    task_id=config.task_id,
                    display_was_on=display_was_on,
                    wait_seconds=self._wait_seconds,
                    result=InitiationResult.SUCCESS,
                    elapsed_ms=0,
                    message="Notification posted",
                )
            return ScreenOffResult(
                mode=config.mode,
                task_id=config.task_id,
                display_was_on=display_was_on,
                wait_seconds=self._wait_seconds,
                result=InitiationResult.WAKE_FAILED,
                elapsed_ms=0,
                message="Notification failed",
            )
        except Exception as exc:
            return ScreenOffResult(
                mode=config.mode,
                task_id=config.task_id,
                display_was_on=display_was_on,
                wait_seconds=self._wait_seconds,
                result=InitiationResult.WAKE_FAILED,
                elapsed_ms=0,
                message=str(exc),
            )

    def _wake_ask(self, config: ScreenOffConfig, display_was_on: bool) -> ScreenOffResult:
        """Execute WAKE_ASK policy.

        Wakes the display, shows the confirmation card, and waits for user response.
        """
        if self._wake_display_fn is None:
            return ScreenOffResult(
                mode=config.mode,
                task_id=config.task_id,
                display_was_on=display_was_on,
                wait_seconds=self._wait_seconds,
                result=InitiationResult.SUCCESS,
                elapsed_ms=0,
                message="No wake backend configured (dry-run)",
            )

        try:
            wake_ok = self._wake_display_fn()
            if not wake_ok:
                return ScreenOffResult(
                    mode=config.mode,
                    task_id=config.task_id,
                    display_was_on=display_was_on,
                    wait_seconds=self._wait_seconds,
                    result=InitiationResult.WAKE_FAILED,
                    elapsed_ms=0,
                    message="Wake failed",
                )

            # Show confirmation card
            if self._show_confirmation_fn is not None:
                confirmed = self._show_confirmation_fn()
                if confirmed:
                    return ScreenOffResult(
                        mode=config.mode,
                        task_id=config.task_id,
                        display_was_on=display_was_on,
                        wait_seconds=self._wait_seconds,
                        result=InitiationResult.SUCCESS,
                        elapsed_ms=0,
                        message="Screen woken, confirmation accepted",
                    )
                return ScreenOffResult(
                    mode=config.mode,
                    task_id=config.task_id,
                    display_was_on=display_was_on,
                    wait_seconds=self._wait_seconds,
                    result=InitiationResult.NO_RESPONSE,
                    elapsed_ms=0,
                    message="Confirmation declined",
                )
            else:
                return ScreenOffResult(
                    mode=config.mode,
                    task_id=config.task_id,
                    display_was_on=display_was_on,
                    wait_seconds=self._wait_seconds,
                    result=InitiationResult.SUCCESS,
                    elapsed_ms=0,
                    message="Screen woken, auto-confirm (no callback)",
                )
        except Exception as exc:
            return ScreenOffResult(
                mode=config.mode,
                task_id=config.task_id,
                display_was_on=display_was_on,
                wait_seconds=self._wait_seconds,
                result=InitiationResult.WAKE_FAILED,
                elapsed_ms=0,
                message=str(exc),
            )

    def _wake_execute(
        self, config: ScreenOffConfig, display_was_on: bool
    ) -> ScreenOffResult:
        """Execute WAKE_EXECUTE policy.

        Wakes the display and executes the configured step immediately.
        """
        if self._wake_display_fn is None:
            return ScreenOffResult(
                mode=config.mode,
                task_id=config.task_id,
                display_was_on=display_was_on,
                wait_seconds=self._wait_seconds,
                result=InitiationResult.SUCCESS,
                elapsed_ms=0,
                message="No wake backend configured (dry-run)",
            )

        try:
            wake_ok = self._wake_display_fn()
            if not wake_ok:
                return ScreenOffResult(
                    mode=config.mode,
                    task_id=config.task_id,
                    display_was_on=display_was_on,
                    wait_seconds=self._wait_seconds,
                    result=InitiationResult.WAKE_FAILED,
                    elapsed_ms=0,
                    message="Wake failed",
                )

            # Execute the configured step
            if self._execute_step_fn is not None:
                step_ok = self._execute_step_fn()
                if step_ok:
                    return ScreenOffResult(
                        mode=config.mode,
                        task_id=config.task_id,
                        display_was_on=display_was_on,
                        wait_seconds=self._wait_seconds,
                        result=InitiationResult.SUCCESS,
                        elapsed_ms=0,
                        message="Screen woken, step executed",
                    )
                return ScreenOffResult(
                    mode=config.mode,
                    task_id=config.task_id,
                    display_was_on=display_was_on,
                    wait_seconds=self._wait_seconds,
                    result=InitiationResult.WAKE_FAILED,
                    elapsed_ms=0,
                    message="Step execution failed",
                )
            else:
                return ScreenOffResult(
                    mode=config.mode,
                    task_id=config.task_id,
                    display_was_on=display_was_on,
                    wait_seconds=self._wait_seconds,
                    result=InitiationResult.SUCCESS,
                    elapsed_ms=0,
                    message="Screen woken, auto-execute (no callback)",
                )
        except Exception as exc:
            return ScreenOffResult(
                mode=config.mode,
                task_id=config.task_id,
                display_was_on=display_was_on,
                wait_seconds=self._wait_seconds,
                result=InitiationResult.WAKE_FAILED,
                elapsed_ms=0,
                message=str(exc),
            )

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------

    def _check_display(self) -> bool:
        """Check if the display is currently on.

        Uses the configured check_display_fn if available, otherwise
        assumes the display is on.
        """
        if self._check_display_fn is not None:
            try:
                return self._check_display_fn()
            except Exception:
                pass
        return True

    def _log_initiation_result(self, result: ScreenOffResult) -> None:
        """Log the screen-off initiation result to the study logger."""
        wake_succeeded = result.success
        display_was_off = not result.display_was_on
        self._logger.screen_off_initiation(
            task_id=result.task_id,
            mode=result.mode.value,
            display_was_off=display_was_off,
            wake_succeeded=wake_succeeded,
            details={
                "wait_seconds": result.wait_seconds,
                "message": result.message,
            },
        )

    def cancel(self) -> None:
        """Signal cancellation to a running or pending screen-off micro-trial."""
        self._lock.acquire()
        self._cancelled = True
        self._lock.release()

    @property
    def last_result(self) -> Optional[ScreenOffResult]:
        """Return the result of the last run micro-trial."""
        return self._last_result


# ---------------------------------------------------------------------------
# Convenience: batch screen-off execution
# ---------------------------------------------------------------------------


def run_screen_off_block(
    manager: ScreenOffManager,
    configs: list[ScreenOffConfig],
) -> list[ScreenOffResult]:
    """Execute a sequence of screen-off micro-trials.

    Args:
        manager: The screen-off manager.
        configs: Ordered list of micro-trial configurations.

    Returns:
        List of results, one per config.
    """
    results: list[ScreenOffResult] = []
    for config in configs:
        result = manager.run(config)
        results.append(result)
        logger.info(
            "Screen-off micro-trial %s/%s: %s (%s)",
            config.task_id,
            config.mode.value,
            result.result.value,
            result.message,
        )
    return results

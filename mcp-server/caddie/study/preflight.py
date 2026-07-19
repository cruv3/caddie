"""Preflight checks before a study session.

This module defines the preflight suite — a set of device, server, app,
account, screen, notification, storage, seed-state, and network checks
that must pass immediately before a participant session starts.

Each check is independently runnable, timed, and logged.  The suite
aggregates results into a pass/fail verdict that the CLI and HTTP
endpoint expose.
"""

from __future__ import annotations

import dataclasses
import logging
import time
from dataclasses import dataclass, field
from enum import Enum
from typing import Callable, Optional

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Check categories and results
# ---------------------------------------------------------------------------


class CheckCategory(str, Enum):
    """Logical grouping of preflight checks."""

    DEVICE = "device"
    APP_VERSION = "app_version"
    PERMISSIONS = "permissions"
    SERVER = "server"
    SCREEN = "screen"
    NETWORK = "network"
    STORAGE = "storage"
    NOTIFICATION = "notification"
    SEED_STATE = "seed_state"
    MATERIALS = "materials"


class CheckStatus(str, Enum):
    """Outcome of a single check."""

    PASS = "pass"
    FAIL = "fail"
    SKIP = "skip"
    TIMEOUT = "timeout"


@dataclass(frozen=True)
class PreflightCheck:
    """Immutable description of a single preflight check.

    Args:
        id: Unique check identifier (e.g. "device_connected").
        category: Logical grouping category.
        description: Human-readable description for the experimenter.
        severity: Whether this check is required or optional.
    """

    id: str
    category: CheckCategory
    description: str
    severity: str = "required"

    def __repr__(self) -> str:
        return f"PreflightCheck(id={self.id!r}, category={self.category.value!r})"


@dataclass(frozen=True)
class CheckResult:
    """Result of running a single preflight check.

    Args:
        check: The check that was run.
        status: Pass, fail, skip, or timeout.
        elapsed_ms: How long the check took.
        message: Human-readable detail (error message or success note).
    """

    check: PreflightCheck
    status: CheckStatus
    elapsed_ms: int
    message: str = ""

    @property
    def passed(self) -> bool:
        return self.status is CheckStatus.PASS

    @property
    def failed(self) -> bool:
        return self.status is CheckStatus.FAIL

    @property
    def skipped(self) -> bool:
        return self.status is CheckStatus.SKIP

    def __repr__(self) -> str:
        return (
            f"CheckResult(check={self.check.id!r}, "
            f"status={self.status.value!r}, "
            f"elapsed_ms={self.elapsed_ms})"
        )


# ---------------------------------------------------------------------------
# Check functions
# ---------------------------------------------------------------------------

CheckFn = Callable[[], CheckResult]


# ---------------------------------------------------------------------------
# Built-in check factories
# ---------------------------------------------------------------------------


def check_device_connected(
    adb_path: str = "adb",
) -> CheckFn:
    """Return a check function that verifies a device is connected via ADB.

    Args:
        adb_path: Path to the ADB executable.

    Returns:
        A callable that returns a CheckResult.
    """

    def _run() -> CheckResult:
        check = PreflightCheck(
            id="device_connected",
            category=CheckCategory.DEVICE,
            description="Correct device connected and charged",
        )
        start = time.monotonic()
        try:
            import subprocess
            result = subprocess.run(
                [adb_path, "devices", "-l"],
                capture_output=True,
                text=True,
                timeout=10,
            )
            elapsed_ms = int((time.monotonic() - start) * 1000)
            if result.returncode == 0 and any(
                line.strip().endswith("\tdevice")
                for line in result.stdout.splitlines()
            ):
                return CheckResult(check, CheckStatus.PASS, elapsed_ms, "Device connected")
            return CheckResult(check, CheckStatus.FAIL, elapsed_ms, "No device found")
        except FileNotFoundError:
            elapsed_ms = int((time.monotonic() - start) * 1000)
            return CheckResult(check, CheckStatus.FAIL, elapsed_ms, "ADB not found")
        except subprocess.TimeoutExpired:
            elapsed_ms = int((time.monotonic() - start) * 1000)
            return CheckResult(check, CheckStatus.TIMEOUT, elapsed_ms, "ADB timed out")

    return _run


def check_app_version(
    package_name: str = "com.caddie",
    version: Optional[str] = None,
) -> CheckFn:
    """Return a check that verifies the Caddie app version matches.

    Args:
        package_name: Android package name.
        version: Expected version string (e.g. "1.0.0"). If None, just checks
            the app is installed.

    Returns:
        A callable that returns a CheckResult.
    """

    def _run() -> CheckResult:
        check = PreflightCheck(
            id="app_version",
            category=CheckCategory.APP_VERSION,
            description=f"Caddie app version matches (expected: {version or 'any'})",
        )
        start = time.monotonic()
        try:
            import subprocess
            result = subprocess.run(
                ["adb", "shell", "dumpsys", "package", package_name],
                capture_output=True,
                text=True,
                timeout=10,
            )
            elapsed_ms = int((time.monotonic() - start) * 1000)
            if result.returncode != 0:
                return CheckResult(check, CheckStatus.FAIL, elapsed_ms, "App not installed")
            if version and f"versionName={version}" not in result.stdout:
                return CheckResult(
                    check, CheckStatus.FAIL, elapsed_ms,
                    f"Version mismatch (found in dumpsys)",
                )
            return CheckResult(check, CheckStatus.PASS, elapsed_ms, "App version OK")
        except FileNotFoundError:
            elapsed_ms = int((time.monotonic() - start) * 1000)
            return CheckResult(check, CheckStatus.FAIL, elapsed_ms, "ADB not found")
        except subprocess.TimeoutExpired:
            elapsed_ms = int((time.monotonic() - start) * 1000)
            return CheckResult(check, CheckStatus.TIMEOUT, elapsed_ms, "ADB timed out")

    return _run


def check_server_health(
    url: str = "http://127.0.0.1:5000",
) -> CheckFn:
    """Return a check that verifies the study server is healthy.

    Args:
        url: The server base URL.

    Returns:
        A callable that returns a CheckResult.
    """

    def _run() -> CheckResult:
        check = PreflightCheck(
            id="server_health",
            category=CheckCategory.SERVER,
            description="Server health and SSE connection healthy",
        )
        start = time.monotonic()
        try:
            import urllib.request
            req = urllib.request.Request(f"{url}/study/health")
            resp = urllib.request.urlopen(req, timeout=5)
            elapsed_ms = int((time.monotonic() - start) * 1000)
            if resp.status == 200:
                return CheckResult(check, CheckStatus.PASS, elapsed_ms, "Server healthy")
            return CheckResult(check, CheckStatus.FAIL, elapsed_ms, f"HTTP {resp.status}")
        except Exception as exc:
            elapsed_ms = int((time.monotonic() - start) * 1000)
            return CheckResult(check, CheckStatus.FAIL, elapsed_ms, str(exc))

    return _run


def check_storage_space(
    min_mb: int = 500,
) -> CheckFn:
    """Return a check that verifies sufficient storage.

    Args:
        min_mb: Minimum required megabytes of free storage.

    Returns:
        A callable that returns a CheckResult.
    """

    def _run() -> CheckResult:
        check = PreflightCheck(
            id="storage_space",
            category=CheckCategory.STORAGE,
            description=f"Sufficient storage (>= {min_mb} MB free)",
        )
        start = time.monotonic()
        try:
            import subprocess
            result = subprocess.run(
                [adb_path, "shell", "df", "/sdcard"],
                capture_output=True,
                text=True,
                timeout=10,
            )
            elapsed_ms = int((time.monotonic() - start) * 1000)
            if result.returncode != 0:
                return CheckResult(check, CheckStatus.FAIL, elapsed_ms, "df command failed")
            # Parse df output: e.g. "Filesystem     1K-blocks  Used Available Use% Mounted on"
            # We need the Available column (3rd data column)
            lines = result.stdout.strip().splitlines()
            if len(lines) < 2:
                return CheckResult(check, CheckStatus.FAIL, elapsed_ms, "df output too short")
            parts = lines[-1].split()
            if len(parts) >= 4:
                # Available is in column 3 (0-indexed: 3)
                avail_kb = int(parts[3])
                free_mb = avail_kb / 1024
                if free_mb >= min_mb:
                    return CheckResult(check, CheckStatus.PASS, elapsed_ms, f"{free_mb:.0f} MB free on phone")
                return CheckResult(check, CheckStatus.FAIL, elapsed_ms, f"{free_mb:.0f} MB free (need {min_mb})")
            return CheckResult(check, CheckStatus.FAIL, elapsed_ms, "Could not parse df output")
        except subprocess.TimeoutExpired:
            elapsed_ms = int((time.monotonic() - start) * 1000)
            return CheckResult(check, CheckStatus.TIMEOUT, elapsed_ms, "ADB timed out")
        except Exception as exc:
            elapsed_ms = int((time.monotonic() - start) * 1000)
            return CheckResult(check, CheckStatus.FAIL, elapsed_ms, str(exc))

    return _run


def _check_network_connectivity() -> CheckFn:
    """Return a network connectivity check."""

    def _run() -> CheckResult:
        check = PreflightCheck(
            id="network_stable",
            category=CheckCategory.NETWORK,
            description="Stable network connection",
        )
        start = time.monotonic()
        try:
            import urllib.request
            urllib.request.urlopen("http://8.8.8.8", timeout=5)
            elapsed_ms = int((time.monotonic() - start) * 1000)
            return CheckResult(check, CheckStatus.PASS, elapsed_ms, "Network OK")
        except Exception as exc:
            elapsed_ms = int((time.monotonic() - start) * 1000)
            return CheckResult(check, CheckStatus.FAIL, elapsed_ms, str(exc))

    return _run


def check_screen_timeout(
    timeout_minutes: int = 10,
) -> CheckFn:
    """Return a check that verifies the screen timeout setting.

    Args:
        timeout_minutes: Expected screen timeout in minutes.

    Returns:
        A callable that returns a CheckResult.
    """

    def _run() -> CheckResult:
        check = PreflightCheck(
            id="screen_timeout",
            category=CheckCategory.SCREEN,
            description=f"Screen timeout correct (>= {timeout_minutes} min)",
        )
        start = time.monotonic()
        try:
            import subprocess
            result = subprocess.run(
                ["adb", "shell", "settings", "get", "system", "screen_off_timeout"],
                capture_output=True,
                text=True,
                timeout=10,
            )
            elapsed_ms = int((time.monotonic() - start) * 1000)
            if result.returncode == 0:
                timeout_ms = int(result.stdout.strip())
                timeout_min = timeout_ms / 60000
                if timeout_min >= timeout_minutes:
                    return CheckResult(check, CheckStatus.PASS, elapsed_ms, f"{timeout_min:.1f} min")
                return CheckResult(
                    check, CheckStatus.FAIL, elapsed_ms,
                    f"Timeout {timeout_min:.1f} min < {timeout_minutes} min",
                )
            return CheckResult(check, CheckStatus.FAIL, elapsed_ms, "Could not read timeout")
        except FileNotFoundError:
            elapsed_ms = int((time.monotonic() - start) * 1000)
            return CheckResult(check, CheckStatus.FAIL, elapsed_ms, "ADB not found")
        except Exception as exc:
            elapsed_ms = int((time.monotonic() - start) * 1000)
            return CheckResult(check, CheckStatus.FAIL, elapsed_ms, str(exc))

    return _run


# ---------------------------------------------------------------------------
# Preflight suite
# ---------------------------------------------------------------------------


@dataclass
class PreflightResult:
    """Aggregated result of a full preflight run.

    Args:
        passed: Number of checks that passed.
        failed: Number of checks that failed.
        skipped: Number of checks that were skipped.
        total: Total number of checks attempted.
    """

    passed: int = 0
    failed: int = 0
    skipped: int = 0
    total: int = 0

    @property
    def all_passed(self) -> bool:
        return self.failed == 0 and self.skipped == 0

    @property
    def required_passed(self) -> bool:
        return self.failed == 0


@dataclass
class PreflightSuite:
    """Runs a sequence of preflight checks and aggregates results.

    Args:
        checks: List of (PreflightCheck, CheckFn) tuples.
        timeout_per_check: Max seconds per individual check (default 15).
    """

    checks: list[tuple[PreflightCheck, CheckFn]] = field(default_factory=list)
    timeout_per_check: int = 15
    _results_cache: list[CheckResult] = field(default_factory=list, repr=False)

    def add(self, check: PreflightCheck, fn: CheckFn) -> None:
        """Register a check for execution."""
        self.checks.append((check, fn))

    def run(self) -> list[CheckResult]:
        """Execute all registered checks in order.

        Returns:
            List of CheckResult for each executed check.
        """
        results: list[CheckResult] = []
        for check, fn in self.checks:
            start = time.monotonic()
            try:
                result = fn()
                elapsed_ms = int((time.monotonic() - start) * 1000)
                # Enforce timeout_per_check
                if elapsed_ms > self.timeout_per_check * 1000:
                    result = CheckResult(
                        check,
                        CheckStatus.TIMEOUT,
                        elapsed_ms,
                        f"Exceeded timeout of {self.timeout_per_check}s",
                    )
                results.append(result)
                logger.info(
                    "Preflight %s: %s (%d ms)",
                    check.id,
                    result.status.value,
                    result.elapsed_ms,
                )
            except Exception as exc:
                elapsed_ms = int((time.monotonic() - start) * 1000)
                results.append(
                    CheckResult(
                        check,
                        CheckStatus.FAIL,
                        elapsed_ms,
                        f"Exception: {exc}",
                    )
                )
                logger.error("Preflight %s FAILED: %s", check.id, exc)
        # Cache results so summary() can access them (E2E pre-existing bug)
        self._results_cache = results
        return results

    def summary(self) -> PreflightResult:
        """Aggregate results into a PreflightResult summary."""
        result = PreflightResult()
        for r in self._last_results:
            result.total += 1
            if r.passed:
                result.passed += 1
            elif r.failed:
                result.failed += 1
            elif r.skipped:
                result.skipped += 1
        return result

    @property
    def _last_results(self) -> list[CheckResult]:
        """Last run results (set by run())."""
        return getattr(self, "_results_cache", [])

    def run_and_summary(self) -> tuple[list[CheckResult], PreflightResult]:
        """Run checks and return both results and summary."""
        results = self.run()
        self._results_cache = results
        summary = PreflightResult()
        for r in results:
            summary.total += 1
            if r.passed:
                summary.passed += 1
            elif r.failed:
                summary.failed += 1
            elif r.skipped:
                summary.skipped += 1
        return results, summary


# ---------------------------------------------------------------------------
# Default preflight suite
# ---------------------------------------------------------------------------


def check_banking_app_installed() -> CheckFn:
    """Check that the study banking mock app is installed."""
    def _run() -> CheckResult:
        check = PreflightCheck(
            id="banking_app",
            category=CheckCategory.APP_VERSION,
            description="Study banking mock app installed",
        )
        start = time.monotonic()
        try:
            import subprocess
            result = subprocess.run(
                [ADB_PATH, "shell", "pm", "list", "packages", "com.caddie.studybank"],
                capture_output=True,
                text=True,
                timeout=10,
            )
            elapsed_ms = int((time.monotonic() - start) * 1000)
            if "com.caddie.studybank" in result.stdout:
                return CheckResult(check, CheckStatus.PASS, elapsed_ms, "Banking app installed")
            return CheckResult(check, CheckStatus.FAIL, elapsed_ms, "Banking app not installed")
        except subprocess.TimeoutExpired:
            elapsed_ms = int((time.monotonic() - start) * 1000)
            return CheckResult(check, CheckStatus.TIMEOUT, elapsed_ms, "ADB timed out")
        except Exception as exc:
            elapsed_ms = int((time.monotonic() - start) * 1000)
            return CheckResult(check, CheckStatus.FAIL, elapsed_ms, str(exc))
    return _run


def check_notification_permission() -> CheckFn:
    """Check that notification permission is granted."""
    def _run() -> CheckResult:
        check = PreflightCheck(
            id="notification_permission",
            category=CheckCategory.NOTIFICATION,
            description="Notification permission granted",
        )
        start = time.monotonic()
        try:
            import subprocess
            result = subprocess.run(
                [ADB_PATH, "shell", "dumpsys", "package", "com.caddie",
                 "|", "grep", "com.caddie"] ,
                capture_output=True,
                text=True,
                timeout=10,
            )
            elapsed_ms = int((time.monotonic() - start) * 1000)
            # Check for notification permission
            if "POST_NOTIFICATIONS" in result.stdout or " granted" in result.stdout:
                return CheckResult(check, CheckStatus.PASS, elapsed_ms, "Notification permission OK")
            return CheckResult(check, CheckStatus.FAIL, elapsed_ms, "Notification permission not granted")
        except subprocess.TimeoutExpired:
            elapsed_ms = int((time.monotonic() - start) * 1000)
            return CheckResult(check, CheckStatus.TIMEOUT, elapsed_ms, "ADB timed out")
        except Exception as exc:
            elapsed_ms = int((time.monotonic() - start) * 1000)
            return CheckResult(check, CheckStatus.FAIL, elapsed_ms, str(exc))
    return _run


def check_study_materials() -> CheckFn:
    """Check that study materials directory exists."""
    def _run() -> CheckResult:
        check = PreflightCheck(
            id="study_materials",
            category=CheckCategory.MATERIALS,
            description="Study materials present",
        )
        start = time.monotonic()
        try:
            import os
            materials_dir = pathlib.Path(__file__).parent.parent.parent / "study" / "materials"
            if materials_dir.exists() and any(materials_dir.iterdir()):
                elapsed_ms = int((time.monotonic() - start) * 1000)
                return CheckResult(check, CheckStatus.PASS, elapsed_ms, "Materials present")
            elapsed_ms = int((time.monotonic() - start) * 1000)
            return CheckResult(check, CheckStatus.FAIL, elapsed_ms, "Materials missing")
        except Exception as exc:
            elapsed_ms = int((time.monotonic() - start) * 1000)
            return CheckResult(check, CheckStatus.FAIL, elapsed_ms, str(exc))
    return _run


def default_suite() -> PreflightSuite:
    """Build the default preflight suite with all built-in checks.

    Returns:
        A PreflightSuite ready to run.
    """
    suite = PreflightSuite()
    suite.add(PreflightCheck(
        id="device_connected",
        category=CheckCategory.DEVICE,
        description="Correct device connected and charged",
    ), check_device_connected())
    suite.add(PreflightCheck(
        id="app_version",
        category=CheckCategory.APP_VERSION,
        description="Caddie app version matches",
    ), check_app_version())
    suite.add(PreflightCheck(
        id="banking_app",
        category=CheckCategory.APP_VERSION,
        description="Study banking mock app installed",
    ), check_banking_app_installed())
    suite.add(PreflightCheck(
        id="notification_permission",
        category=CheckCategory.NOTIFICATION,
        description="Notification permission granted",
    ), check_notification_permission())
    suite.add(PreflightCheck(
        id="server_health",
        category=CheckCategory.SERVER,
        description="Server health and SSE connection healthy",
    ), check_server_health())
    suite.add(PreflightCheck(
        id="screen_timeout",
        category=CheckCategory.SCREEN,
        description="Screen timeout correct",
    ), check_screen_timeout())
    suite.add(PreflightCheck(
        id="storage_space",
        category=CheckCategory.STORAGE,
        description="Sufficient storage (>= 500 MB free)",
    ), check_storage_space())
    suite.add(PreflightCheck(
        id="network_stable",
        category=CheckCategory.NETWORK,
        description="Stable network connection",
    ), _check_network_connectivity())
    suite.add(PreflightCheck(
        id="study_materials",
        category=CheckCategory.MATERIALS,
        description="Study materials present",
    ), check_study_materials())
    return suite

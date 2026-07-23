"""Tests for caddie.study.preflight — preflight suite and check definitions."""

from __future__ import annotations

import time
import subprocess

import pytest

from caddie.study.preflight import (
    CheckCategory,
    CheckFn,
    CheckResult,
    CheckStatus,
    PreflightCheck,
    PreflightResult,
    PreflightSuite,
    check_banking_app_installed,
    check_device_connected,
    check_notification_permission,
    check_server_health,
    check_storage_space,
    check_study_materials,
    default_suite,
    _check_network_connectivity,
)


# ---------------------------------------------------------------------------
# PreflightCheck
# ---------------------------------------------------------------------------


def test_preflight_check_repr():
    check = PreflightCheck(
        id="test_check",
        category=CheckCategory.DEVICE,
        description="A test check",
        severity="required",
    )
    assert "test_check" in repr(check)
    assert "device" in repr(check)


# ---------------------------------------------------------------------------
# CheckResult
# ---------------------------------------------------------------------------


def test_check_result_passed():
    check = PreflightCheck(
        id="ok",
        category=CheckCategory.DEVICE,
        description="OK",
    )
    result = CheckResult(check, CheckStatus.PASS, 42, "All good")
    assert result.passed is True
    assert result.failed is False
    assert result.elapsed_ms == 42


def test_check_result_failed():
    check = PreflightCheck(
        id="fail",
        category=CheckCategory.DEVICE,
        description="Fail",
    )
    result = CheckResult(check, CheckStatus.FAIL, 10, "Error")
    assert result.failed is True
    assert result.passed is False


def test_check_result_skipped():
    check = PreflightCheck(
        id="skip",
        category=CheckCategory.DEVICE,
        description="Skip",
    )
    result = CheckResult(check, CheckStatus.SKIP, 0, "N/A")
    assert result.skipped is True


# ---------------------------------------------------------------------------
# PreflightResult
# ---------------------------------------------------------------------------


def test_preflight_result_all_passed():
    r = PreflightResult(passed=3, failed=0, skipped=0, total=3)
    assert r.all_passed is True
    assert r.required_passed is True


def test_preflight_result_any_failed():
    r = PreflightResult(passed=2, failed=1, skipped=0, total=3)
    assert r.all_passed is False
    assert r.required_passed is False


def test_preflight_result_skip_ok():
    r = PreflightResult(passed=2, failed=0, skipped=1, total=3)
    assert r.all_passed is False
    assert r.required_passed is True


# ---------------------------------------------------------------------------
# PreflightSuite
# ---------------------------------------------------------------------------


def test_suite_empty_run():
    suite = PreflightSuite()
    results, summary = suite.run_and_summary()
    assert results == []
    assert summary.total == 0
    assert summary.passed == 0
    assert summary.failed == 0


def test_suite_add_and_run():
    """A suite with a custom check that always passes."""
    check = PreflightCheck(
        id="always_pass",
        category=CheckCategory.DEVICE,
        description="Always passes",
    )

    def pass_fn() -> CheckResult:
        return CheckResult(check, CheckStatus.PASS, 10, "OK")

    suite = PreflightSuite()
    suite.add(check, pass_fn)
    results, summary = suite.run_and_summary()
    assert len(results) == 1
    assert results[0].passed is True
    assert summary.passed == 1
    assert summary.failed == 0


def test_suite_add_multiple_checks():
    """A suite with both passing and failing checks."""
    check_pass = PreflightCheck(
        id="pass",
        category=CheckCategory.DEVICE,
        description="Passes",
    )
    check_fail = PreflightCheck(
        id="fail",
        category=CheckCategory.DEVICE,
        description="Fails",
    )

    def pass_fn() -> CheckResult:
        return CheckResult(check_pass, CheckStatus.PASS, 5, "OK")

    def fail_fn() -> CheckResult:
        return CheckResult(check_fail, CheckStatus.FAIL, 8, "Error")

    suite = PreflightSuite()
    suite.add(check_pass, pass_fn)
    suite.add(check_fail, fail_fn)
    results, summary = suite.run_and_summary()
    assert len(results) == 2
    assert summary.passed == 1
    assert summary.failed == 1
    assert summary.all_passed is False


def test_suite_check_exception():
    """A check that raises an exception is recorded as failed."""

    check = PreflightCheck(
        id="boom",
        category=CheckCategory.DEVICE,
        description="Raises",
    )

    def boom_fn() -> CheckResult:
        raise RuntimeError("Kaboom")

    suite = PreflightSuite()
    suite.add(check, boom_fn)
    results, summary = suite.run_and_summary()
    assert len(results) == 1
    assert results[0].failed is True
    assert summary.failed == 1


def test_default_suite_has_checks():
    suite = default_suite()
    assert len(suite.checks) >= 5


def test_default_suite_requires_study_mail_app():
    suite = default_suite()
    check_ids = {check.id for check, _ in suite.checks}
    assert "study_mail_app" in check_ids


def test_default_suite_run():
    suite = default_suite()
    results, summary = suite.run_and_summary()
    # At least storage check should pass on most systems
    assert summary.total == len(suite.checks)
    assert summary.total >= 5


def test_device_check_accepts_detailed_adb_device_line(monkeypatch):
    output = (
        "List of devices attached\n"
        "35091FDH2002ZN device product:panther model:Pixel_7 device:panther transport_id:2\n"
    )
    monkeypatch.setattr(
        subprocess,
        "run",
        lambda *args, **kwargs: subprocess.CompletedProcess(args[0], 0, output, ""),
    )

    assert check_device_connected()().status is CheckStatus.PASS


def test_banking_check_uses_adb_and_detects_installed_package(monkeypatch):
    monkeypatch.setattr(
        subprocess,
        "run",
        lambda *args, **kwargs: subprocess.CompletedProcess(
            args[0], 0, "package:com.caddie.studybank\n", ""
        ),
    )

    assert check_banking_app_installed()().status is CheckStatus.PASS


def test_notification_check_requires_appops_allow(monkeypatch):
    monkeypatch.setattr(
        subprocess,
        "run",
        lambda *args, **kwargs: subprocess.CompletedProcess(
            args[0], 0, "Uid mode: POST_NOTIFICATION: allow\n", ""
        ),
    )

    assert check_notification_permission()().status is CheckStatus.PASS


def test_notification_check_accepts_android_default_allow(monkeypatch):
    monkeypatch.setattr(
        subprocess,
        "run",
        lambda *args, **kwargs: subprocess.CompletedProcess(
            args[0], 0, "No operations.\nDefault mode: allow\n", ""
        ),
    )

    assert check_notification_permission()().status is CheckStatus.PASS


def test_server_health_defaults_to_running_agent_port(monkeypatch):
    requested_urls = []

    class Response:
        status = 200

    def fake_urlopen(request, timeout):
        requested_urls.append(request.full_url)
        return Response()

    monkeypatch.setattr("urllib.request.urlopen", fake_urlopen)

    assert check_server_health()().status is CheckStatus.PASS
    assert requested_urls == ["http://127.0.0.1:8787/study/health"]


def test_storage_check_parses_phone_free_space(monkeypatch):
    output = (
        "Filesystem 1K-blocks Used Available Use% Mounted on\n"
        "/dev/fuse 115249236 75029672 40088492 66% /storage/emulated\n"
    )
    monkeypatch.setattr(
        subprocess,
        "run",
        lambda *args, **kwargs: subprocess.CompletedProcess(args[0], 0, output, ""),
    )

    assert check_storage_space()().status is CheckStatus.PASS


def test_network_check_uses_phone_connectivity(monkeypatch):
    commands = []

    def fake_run(command, **kwargs):
        commands.append(command)
        return subprocess.CompletedProcess(command, 0, "1 packets transmitted, 1 received", "")

    monkeypatch.setattr(subprocess, "run", fake_run)

    assert _check_network_connectivity()().status is CheckStatus.PASS
    assert commands == [["adb", "shell", "ping", "-c", "1", "-W", "3", "8.8.8.8"]]


def test_study_materials_check_finds_repository_materials():
    assert check_study_materials()().status is CheckStatus.PASS

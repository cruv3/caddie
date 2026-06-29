"""open_app resilience: a guessed/wrong package name resolves to an installed one
by token match, with fail-closed ambiguity handling. monkey returns exit 0 even on
a missing package (it prints 'No activities found ... aborted' to stdout), so the
launch-failure must be detected from stdout, not the return code."""
import pytest
from caddie.android.backends.adb.apps import AppCommands
from caddie.android.backends.adb.client import AdbError


class FakeApps(AppCommands):
    def __init__(self, installed, launchable):
        self._installed = list(installed)
        self._launchable = set(launchable)
        self.launched = []

    def list_apps(self, include_system: bool = True):
        return sorted(self._installed)

    def shell(self, *args, timeout_seconds=None):
        if args and args[0] == "monkey":
            pkg = args[2]  # ["monkey","-p",pkg,...]
            if pkg in self._launchable:
                self.launched.append(pkg)
                return "Events injected: 1\n## Network stats: 0 bytes"
            return "** No activities found to run, monkey aborted."
        return ""


def test_open_app_exact_launch():
    a = FakeApps(["com.x.clock"], ["com.x.clock"])
    assert "com.x.clock" in a.open_app("com.x.clock")
    assert a.launched == ["com.x.clock"]


def test_open_app_resolves_wrong_guess_by_token():
    # model guessed com.android.calculator2 (not installed); real one is google's
    a = FakeApps(["com.google.android.calculator", "com.x.clock"],
                 ["com.google.android.calculator", "com.x.clock"])
    res = a.open_app("com.android.calculator2")
    assert a.launched == ["com.google.android.calculator"]
    assert "calculator" in res.lower()


def test_open_app_ambiguous_refuses_no_best_guess():
    a = FakeApps(["com.android.settings", "com.sec.android.settings"],
                 ["com.android.settings", "com.sec.android.settings"])
    with pytest.raises(AdbError) as e:
        a.open_app("settings")
    assert "ambiguous" in str(e.value).lower()
    assert a.launched == []  # never best-guessed an app


def test_open_app_no_match_clear_error():
    a = FakeApps(["com.x.clock"], ["com.x.clock"])
    with pytest.raises(AdbError) as e:
        a.open_app("com.totally.unknownapp")
    assert "no installed package" in str(e.value).lower()


def test_open_app_missing_package_detected_from_stdout():
    # the guessed package is "installed" in list but monkey can't launch it AND
    # there is no other token match -> must raise, not silently succeed
    a = FakeApps(["com.x.calculator"], [])  # nothing launchable
    with pytest.raises(AdbError):
        a.open_app("com.x.calculator")
    assert a.launched == []


def test_open_app_empty_or_error_output_is_not_success():
    # monkey prints neither 'Events injected' nor a known failure marker -> must
    # NOT be treated as success (that would mask the wander bug).
    class WeirdApps(FakeApps):
        def shell(self, *args, timeout_seconds=None):
            if args and args[0] == "monkey":
                return "Error: something odd happened"  # no 'Events injected'
            return ""
    w = WeirdApps(["com.x.clock"], [])
    with pytest.raises(AdbError):
        w.open_app("com.x.clock")
    assert w.launched == []


def test_open_app_prefers_segment_match_over_substring():
    # token 'maps' -> the package whose LAST SEGMENT is 'maps', not the substring
    # false match 'com.x.mapsave'
    a = FakeApps(["com.google.android.apps.maps", "com.x.mapsave"],
                 ["com.google.android.apps.maps", "com.x.mapsave"])
    a.open_app("maps")
    assert a.launched == ["com.google.android.apps.maps"]

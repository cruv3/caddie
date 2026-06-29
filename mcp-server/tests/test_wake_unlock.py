# mcp-server/tests/test_wake_unlock.py
from caddie.android.backends.adb.screen import ScreenCommands


class FakeScreen(ScreenCommands):
    def __init__(self, locked_states):
        # locked_states: list of bools returned by successive dumpsys reads
        self._states = list(locked_states)
        self.shell_calls = []
        self.swipes = []

    def shell(self, *args, timeout_seconds=None):
        self.shell_calls.append(args)
        if args and args[0] == "dumpsys":
            locked = self._states.pop(0) if self._states else False
            return "mShowingLockscreen=true" if locked else "mShowingLockscreen=false"
        return ""

    def screen_size(self):
        return {"width": 1080, "height": 2400}

    def swipe(self, *a, **k):
        self.swipes.append((a, k))
        return "ok"


def test_unlocks_swipe_lock():
    # locked, then after swipe -> unlocked
    s = FakeScreen([True, False])
    r = s.wake_and_unlock()
    assert r["unlocked"] is True
    assert s.swipes, "expected a swipe-up to dismiss the lock"
    assert ("input", "keyevent", "224") in s.shell_calls


def test_reports_locked_when_stays_locked():
    s = FakeScreen([True, True, True, True])
    r = s.wake_and_unlock()
    assert r["unlocked"] is False
    assert "lock" in r["reason"].lower()


def test_already_unlocked_no_swipe():
    s = FakeScreen([False])
    r = s.wake_and_unlock()
    assert r["unlocked"] is True
    assert s.swipes == []


def test_wake_failure_returns_locked():
    class WakeFails(FakeScreen):
        def shell(self, *args, timeout_seconds=None):
            if args and args[0] == "input":
                raise RuntimeError("boom")
            return super().shell(*args, timeout_seconds=timeout_seconds)
    r = WakeFails([True]).wake_and_unlock()
    assert r["unlocked"] is False
    assert "wake failed" in r["reason"]


def test_swipe_exception_returns_locked():
    class SwipeFails(FakeScreen):
        def swipe(self, *a, **k):
            raise RuntimeError("nope")
    r = SwipeFails([True, True]).wake_and_unlock()
    assert r["unlocked"] is False
    assert "swipe failed" in r["reason"]

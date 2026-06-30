"""Deterministic volume resolver: parse a parametric volume task and set the
stream level via `cmd media_session volume` (no UI slider). Command-verb gated so
a mention/question doesn't change the volume."""
from caddie.agent.fast_actions import match_volume_intent
from caddie.android.backends.adb.apps import AppCommands


# --- parser ---------------------------------------------------------------

def test_volume_percent_media_default():
    assert match_volume_intent("set the volume to 50%") == ("volume", 3, "50%")


def test_volume_max_min():
    assert match_volume_intent("turn the volume up to max") == ("volume", 3, "max")
    assert match_volume_intent("set volume to minimum") == ("volume", 3, "min")
    assert match_volume_intent("mute the volume") == ("volume", 3, "min")


def test_volume_stream_selection():
    assert match_volume_intent("set the ring volume to 30%") == ("volume", 2, "30%")
    assert match_volume_intent("set the alarm volume to max") == ("volume", 4, "max")
    assert match_volume_intent("set the notification volume to 0") == ("volume", 5, "0")


def test_volume_german():
    assert match_volume_intent("stelle die lautstaerke auf 40 prozent") == ("volume", 3, "40%")


def test_volume_requires_command_and_value():
    # mention/question, or no value -> None (no misfire)
    for t in ("what is the volume", "is the volume on max", "open volume settings", ""):
        assert match_volume_intent(t) is None, t


# --- backend --------------------------------------------------------------

class FakeVol(AppCommands):
    """Applies --set (so verify-after-set passes), like a working device."""
    def __init__(self, maxv=25, cur=5, apply=True):
        self.maxv = maxv
        self.cur = cur
        self.apply = apply
        self.calls = []

    def shell(self, *args, timeout_seconds=None):
        self.calls.append(list(args))
        if "--set" in args and self.apply:
            self.cur = int(args[args.index("--set") + 1])
            return ""
        if "--get" in args:
            return f"[V] volume is {self.cur} in range [0..{self.maxv}]"
        return ""


def _set_call(v):
    return next(c for c in v.calls if "--set" in c)


def test_set_volume_percent_uses_device_range():
    v = FakeVol(maxv=20)
    v.set_volume(3, "50%")
    assert "10" in _set_call(v)            # 50% of 20
    assert "--stream" in _set_call(v) and "3" in _set_call(v)


def test_set_volume_max_min():
    v = FakeVol(maxv=25)
    v.set_volume(3, "max")
    assert "25" in _set_call(v)
    v2 = FakeVol(maxv=25)
    v2.set_volume(3, "min")
    assert "0" in _set_call(v2)


def test_set_volume_absolute_clamped():
    v = FakeVol(maxv=15)
    v.set_volume(3, "50%")                  # 50% of 15 -> 8 (round)
    assert "8" in _set_call(v)


def test_set_volume_raises_when_not_applied():
    # device where --set is a no-op (volume doesn't move) -> raise so the resolver
    # falls back to the UI instead of falsely reporting success.
    import pytest
    from caddie.android.backends.adb.client import AdbError
    v = FakeVol(maxv=25, cur=5, apply=False)
    with pytest.raises(AdbError):
        v.set_volume(3, "max")

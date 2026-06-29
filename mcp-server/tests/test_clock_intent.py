"""Deterministic alarm/timer resolver: parse a parametric clock task and (in the
backend) fire the standard AlarmClock intents. Requires an explicit alarm/timer
keyword (no misfire on tasks that merely mention a time)."""
from caddie.agent.fast_actions import match_clock_intent
from caddie.android.backends.adb.apps import AppCommands


# --- parser ---------------------------------------------------------------

def test_alarm_12h_am():
    assert match_clock_intent("set an alarm for 7:30 AM") == ("alarm", 7, 30)


def test_alarm_12h_pm():
    assert match_clock_intent("set an alarm for 7:30 PM") == ("alarm", 19, 30)


def test_alarm_24h():
    assert match_clock_intent("set an alarm for 19:30") == ("alarm", 19, 30)


def test_alarm_bare_hour():
    assert match_clock_intent("set an alarm at 7") == ("alarm", 7, 0)


def test_alarm_german():
    assert match_clock_intent("stelle den wecker um 6:15") == ("alarm", 6, 15)


def test_alarm_12_am_pm_edge():
    assert match_clock_intent("alarm for 12:00 AM") == ("alarm", 0, 0)
    assert match_clock_intent("alarm for 12:00 PM") == ("alarm", 12, 0)


def test_timer_minutes():
    assert match_clock_intent("create a 3 minute timer") == ("timer", 180)


def test_timer_seconds():
    assert match_clock_intent("set a timer for 90 seconds") == ("timer", 90)


def test_timer_min_abbrev():
    assert match_clock_intent("5 min timer") == ("timer", 300)


def test_timer_german():
    assert match_clock_intent("timer auf 2 minuten") == ("timer", 120)


def test_timer_out_of_range_rejected():
    assert match_clock_intent("timer for 0 minutes") is None
    assert match_clock_intent("timer for 30 hours") is None  # > 86400s


def test_no_keyword_does_not_misfire():
    # mentions a time but no alarm/timer keyword -> None (LLM/UI handles it)
    assert match_clock_intent("search for trains at 7:30") is None
    assert match_clock_intent("open the clock app") is None
    assert match_clock_intent("") is None


# --- backend intent commands ---------------------------------------------

class FakeClock(AppCommands):
    def __init__(self):
        self.calls = []

    def shell(self, *args, timeout_seconds=None):
        self.calls.append(list(args))
        return ""


def test_set_alarm_builds_intent():
    c = FakeClock()
    c.set_alarm(7, 30)
    a = c.calls[0]
    assert a[:4] == ["am", "start", "-a", "android.intent.action.SET_ALARM"]
    assert "android.intent.extra.alarm.HOUR" in a and "7" in a
    assert "android.intent.extra.alarm.MINUTES" in a and "30" in a
    assert "android.intent.extra.alarm.SKIP_UI" in a and "true" in a


def test_set_timer_builds_intent():
    c = FakeClock()
    c.set_timer(180)
    a = c.calls[0]
    assert a[:4] == ["am", "start", "-a", "android.intent.action.SET_TIMER"]
    assert "android.intent.extra.alarm.LENGTH" in a and "180" in a
    assert "android.intent.extra.alarm.SKIP_UI" in a and "true" in a

"""Detect the small-model degradation where the agent narrates the next action
as a prose ReAct 'Action: smartphone_...' line instead of emitting a real tool
call. Must match both observed forms (with and without parens) and must NOT
misfire on a genuine final answer or normal prose."""
from caddie.agent.agent_loop import _looks_like_prose_action


def test_matches_action_with_parens():
    content = ('Thought: Let\'s think step by step.\n\n'
               'Action: smartphone_open_app(package_name="com.google.android.deskclock", '
               'why="Opening clock app to set alarm")')
    assert _looks_like_prose_action(content) is True


def test_matches_action_without_parens():
    # observed live: a get_skill action narrated as prose, no parens
    assert _looks_like_prose_action("Action: smartphone_get_skill_apps_open_clock") is True


def test_matches_case_insensitive_and_indented():
    assert _looks_like_prose_action("   action:   smartphone_tap_element(index=3)") is True


def test_does_not_match_genuine_final_answer():
    assert _looks_like_prose_action("The alarm for 7:30 AM is set and enabled.") is False


def test_does_not_match_normal_prose_mentioning_tools():
    # mentions a tool name but not as an 'Action:' line -> not a degraded action
    assert _looks_like_prose_action(
        "I will open the clock app and then add an alarm.") is False


def test_does_not_match_empty_or_none():
    assert _looks_like_prose_action("") is False
    assert _looks_like_prose_action(None) is False

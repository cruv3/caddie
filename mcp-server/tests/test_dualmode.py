"""Dual-mode prompt gating: observable mode is byte-identical to the default
prompt; fast mode appends the fast-mode instructions."""
from caddie.agent.prompt import build_system_prompt


def test_observable_is_byte_identical_to_default():
    assert build_system_prompt([], mode="observable") == build_system_prompt([])


def test_fast_mode_appends_block():
    out = build_system_prompt([], mode="fast")
    assert "Fast mode" in out
    assert "smartphone_open_settings" in out
    assert out != build_system_prompt([])


def test_fast_is_base_plus_block():
    base = build_system_prompt([])
    fast = build_system_prompt([], mode="fast")
    # fast prompt = the observable/base prompt with the fast block appended
    assert fast.startswith(base)
    assert "## Fast mode" in fast[len(base):]

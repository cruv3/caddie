from caddie.memory import MemoryEntry


def test_from_skill_preserves_semantics(dark_on_skill):
    e = MemoryEntry.from_skill(dark_on_skill, app="com.android.settings")
    assert e.id == "display.dark_mode_on_settings"
    assert e.app == "com.android.settings"
    assert e.kind == "recorded"          # has steps -> recorded
    assert e.complete_trajectory is True  # steps present
    assert e.triggers == dark_on_skill.triggers
    assert e.body == dark_on_skill.body   # rules/verification/failure-modes erhalten
    # intent_text bündelt title+description+triggers für Embedding
    assert "dark mode an" in e.intent_text
    assert "Turn on dark mode" in e.intent_text


def test_from_skill_without_steps_is_authored(dark_on_skill):
    import dataclasses
    s = dataclasses.replace(dark_on_skill, steps=())
    e = MemoryEntry.from_skill(s)
    assert e.kind == "authored"
    assert e.complete_trajectory is False

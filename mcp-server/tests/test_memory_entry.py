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


def test_from_skill_has_default_explored_fields(dark_on_skill):
    """Verify backward-compat: from_skill entries get 1.0 confidence and empty explored fields."""
    e = MemoryEntry.from_skill(dark_on_skill, app="com.android.settings")
    assert e.confidence == 1.0
    assert e.state_sig == ""
    assert e.provenance is None
    assert e.fingerprint is None
    assert e.schema_version == 1


def test_explored_entry_with_provenance():
    """Verify explored entries can carry full provenance and state data."""
    provenance = {
        "app": "com.android.settings",
        "source_sig": "a",
        "action": {"kind": "tap", "label": "Dunkles Design", "index": 3},
        "dest_sig": "b",
        "path": [{"kind": "tap", "label": "Display", "index": 1}],
    }
    fingerprint = {"os": "14", "build": "x", "locale": "de"}
    e = MemoryEntry(
        id="explored.001",
        app="com.android.settings",
        intent_text="Navigate to dark mode setting",
        triggers=("dunkles design",),
        body="Explored path to dark mode toggle",
        kind="explored",
        complete_trajectory=True,
        steps=({"action": "tap", "label": "Display"},),
        source_path="explored/session_001",
        state_sig="sig123",
        confidence=0.3,
        provenance=provenance,
        fingerprint=fingerprint,
        schema_version=1,
    )
    assert e.id == "explored.001"
    assert e.kind == "explored"
    assert e.confidence == 0.3
    assert e.state_sig == "sig123"
    assert e.provenance == provenance
    assert e.provenance["action"]["label"] == "Dunkles Design"
    assert e.provenance["path"][0]["kind"] == "tap"
    assert e.fingerprint == fingerprint
    assert e.fingerprint["locale"] == "de"
    assert e.schema_version == 1

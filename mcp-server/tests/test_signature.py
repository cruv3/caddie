"""
Tests for caddie.explorer.signature (Phase 1c Task 3).
TDD: test fixtures are synthetic, no device required.
"""

import pytest
from caddie.explorer.signature import (
    state_signature,
    fragmentation_merge_metrics,
)


# ---------------------------------------------------------------------------
# Synthetic fixtures
# ---------------------------------------------------------------------------

def _display_el(brightness: str = "50 %") -> list[dict]:
    """Simulate a Display Settings screen with a variable brightness value."""
    return [
        {
            "resource_id": "com.android.settings:id/settings_layout",
            "class": "android.widget.FrameLayout",
            "text": "Display",
            "content_description": "",
            "bounds": {"left": 0, "top": 0, "right": 1080, "bottom": 1920,
                       "center_x": 540, "center_y": 960},
        },
        {
            "resource_id": "com.android.settings:id/brightness_slider",
            "class": "android.widget.SeekBar",
            "text": brightness,
            "content_description": f"Brightness {brightness}",
            "bounds": {"left": 60, "top": 300, "right": 1020, "bottom": 360,
                       "center_x": 540, "center_y": 330},
        },
        {
            "resource_id": "com.android.settings:id/dark_mode_toggle",
            "class": "android.widget.Switch",
            "text": "Dark theme",
            "content_description": "",
            "bounds": {"left": 0, "top": 400, "right": 1080, "bottom": 460,
                       "center_x": 540, "center_y": 430},
        },
    ]


def _sound_el() -> list[dict]:
    """Simulate a Sound Settings screen (structurally different from Display)."""
    return [
        {
            "resource_id": "com.android.settings:id/settings_layout",
            "class": "android.widget.FrameLayout",
            "text": "Sound",
            "content_description": "",
            "bounds": {"left": 0, "top": 0, "right": 1080, "bottom": 1920,
                       "center_x": 540, "center_y": 960},
        },
        {
            "resource_id": "com.android.settings:id/ring_volume_slider",
            "class": "android.widget.SeekBar",
            "text": "7",
            "content_description": "Ring volume 7",
            "bounds": {"left": 60, "top": 300, "right": 1020, "bottom": 360,
                       "center_x": 540, "center_y": 330},
        },
        {
            "resource_id": "com.android.settings:id/vibrate_toggle",
            "class": "android.widget.Switch",
            "text": "Vibration",
            "content_description": "",
            "bounds": {"left": 0, "top": 400, "right": 1080, "bottom": 460,
                       "center_x": 540, "center_y": 430},
        },
    ]


def _battery_el(level: str = "85 %") -> list[dict]:
    """Simulate a Battery Settings screen with a volatile level value."""
    return [
        {
            "resource_id": "com.android.settings:id/settings_layout",
            "class": "android.widget.FrameLayout",
            "text": "Battery",
            "content_description": "",
            "bounds": {"left": 0, "top": 0, "right": 1080, "bottom": 1920,
                       "center_x": 540, "center_y": 960},
        },
        {
            "resource_id": "com.android.settings:id/battery_level",
            "class": "android.widget.TextView",
            "text": level,
            "content_description": f"Battery level {level}",
            "bounds": {"left": 0, "top": 200, "right": 1080, "bottom": 260,
                       "center_x": 540, "center_y": 230},
        },
        {
            "resource_id": "com.android.settings:id/battery_saver_toggle",
            "class": "android.widget.Switch",
            "text": "Battery Saver",
            "content_description": "",
            "bounds": {"left": 0, "top": 350, "right": 1080, "bottom": 410,
                       "center_x": 540, "center_y": 380},
        },
    ]


# ---------------------------------------------------------------------------
# Core: exact vs normalized on volatile-only difference
# ---------------------------------------------------------------------------

class TestVolatileBrightness:
    """Two Display trees that differ ONLY by brightness value (50% vs 51%)."""

    def test_normalized_same_sig(self):
        sig_50 = state_signature(_display_el("50 %"), mode="normalized")
        sig_51 = state_signature(_display_el("51 %"), mode="normalized")
        assert sig_50 == sig_51, (
            "normalized mode must collapse volatile-only differences into the same signature"
        )

    def test_exact_different_sig(self):
        sig_50 = state_signature(_display_el("50 %"), mode="exact")
        sig_51 = state_signature(_display_el("51 %"), mode="exact")
        assert sig_50 != sig_51, (
            "exact mode must distinguish any value change, including volatile text"
        )

    def test_hybrid_same_sig_for_volatile(self):
        # hybrid should also merge volatile-only differences (same structure + same count + same ids)
        sig_50 = state_signature(_display_el("50 %"), mode="hybrid")
        sig_51 = state_signature(_display_el("51 %"), mode="hybrid")
        assert sig_50 == sig_51, (
            "hybrid mode should also collapse volatile-only differences"
        )


# ---------------------------------------------------------------------------
# Core: all modes distinguish structurally different screens
# ---------------------------------------------------------------------------

class TestStructuralDifference:
    """Display screen vs Sound screen must always differ."""

    def test_exact_different(self):
        assert state_signature(_display_el(), "exact") != state_signature(_sound_el(), "exact")

    def test_normalized_different(self):
        assert state_signature(_display_el(), "normalized") != state_signature(_sound_el(), "normalized")

    def test_hybrid_different(self):
        assert state_signature(_display_el(), "hybrid") != state_signature(_sound_el(), "hybrid")


# ---------------------------------------------------------------------------
# Return type and mode validation
# ---------------------------------------------------------------------------

class TestReturnType:
    def test_returns_string(self):
        sig = state_signature(_display_el())
        assert isinstance(sig, str)

    def test_returns_nonempty(self):
        assert state_signature(_display_el()) != ""

    def test_returns_hex_string(self):
        sig = state_signature(_display_el())
        int(sig, 16)  # raises ValueError if not valid hex

    def test_invalid_mode_raises(self):
        with pytest.raises(ValueError, match="Unsupported mode"):
            state_signature(_display_el(), mode="bogus")

    def test_empty_elements_no_crash(self):
        sig = state_signature([], mode="normalized")
        assert isinstance(sig, str)

    def test_all_modes_return_string(self):
        for mode in ("exact", "normalized", "hybrid"):
            sig = state_signature(_display_el(), mode=mode)
            assert isinstance(sig, str) and len(sig) > 0


# ---------------------------------------------------------------------------
# Determinism
# ---------------------------------------------------------------------------

class TestDeterminism:
    def test_same_input_same_sig_normalized(self):
        assert state_signature(_display_el(), "normalized") == state_signature(_display_el(), "normalized")

    def test_same_input_same_sig_exact(self):
        assert state_signature(_display_el(), "exact") == state_signature(_display_el(), "exact")


# ---------------------------------------------------------------------------
# fragmentation_merge_metrics
# ---------------------------------------------------------------------------

class TestMetricsBasic:
    """Metrics on a labeled set with known volatile-only and structural differences."""

    def _build_labeled_trees(self):
        # 3 visits to "display" screen (same label, only brightness volatile)
        # 2 visits to "sound" screen (same label, ring volume volatile)
        # 1 visit to "battery" screen (unique label)
        return [
            ("display", _display_el("50 %")),
            ("display", _display_el("51 %")),
            ("display", _display_el("75 %")),
            ("sound",   _sound_el()),
            ("sound",   _sound_el()),
            ("battery", _battery_el("85 %")),
        ]

    def test_normalized_lower_fragmentation_than_exact(self):
        trees = self._build_labeled_trees()
        m_exact = fragmentation_merge_metrics(trees, "exact")
        m_norm  = fragmentation_merge_metrics(trees, "normalized")
        assert m_norm["fragmentation"] <= m_exact["fragmentation"], (
            "normalized must not have higher fragmentation than exact; "
            f"normalized={m_norm['fragmentation']}, exact={m_exact['fragmentation']}"
        )
        # The display trees differ only in brightness -> exact WILL fragment them
        assert m_exact["fragmentation"] > 0.0, "exact must fragment volatile-only pairs"
        assert m_norm["fragmentation"] == 0.0, "normalized must NOT fragment volatile-only pairs"

    def test_all_modes_low_false_merge_on_clear_screens(self):
        trees = self._build_labeled_trees()
        for mode in ("exact", "normalized", "hybrid"):
            m = fragmentation_merge_metrics(trees, mode)
            assert m["false_merge"] == 0.0, (
                f"mode={mode} should have zero false merges on clearly different screens; "
                f"got {m['false_merge']}"
            )

    def test_returns_dict_with_expected_keys(self):
        trees = self._build_labeled_trees()
        m = fragmentation_merge_metrics(trees, "normalized")
        assert "fragmentation" in m
        assert "false_merge" in m

    def test_values_are_floats_in_range(self):
        trees = self._build_labeled_trees()
        for mode in ("exact", "normalized", "hybrid"):
            m = fragmentation_merge_metrics(trees, mode)
            assert 0.0 <= m["fragmentation"] <= 1.0
            assert 0.0 <= m["false_merge"] <= 1.0


class TestMetricsEdgeCases:
    def test_empty_input(self):
        m = fragmentation_merge_metrics([], "normalized")
        assert m == {"fragmentation": 0.0, "false_merge": 0.0}

    def test_single_tree(self):
        m = fragmentation_merge_metrics([("display", _display_el())], "exact")
        assert m == {"fragmentation": 0.0, "false_merge": 0.0}

    def test_two_same_label_normalized(self):
        trees = [("display", _display_el("50 %")), ("display", _display_el("60 %"))]
        m = fragmentation_merge_metrics(trees, "normalized")
        assert m["fragmentation"] == 0.0

    def test_two_same_label_exact(self):
        trees = [("display", _display_el("50 %")), ("display", _display_el("60 %"))]
        m = fragmentation_merge_metrics(trees, "exact")
        # Different brightness -> exact must fragment
        assert m["fragmentation"] == 1.0

    def test_two_different_labels_no_false_merge(self):
        trees = [("display", _display_el()), ("sound", _sound_el())]
        for mode in ("exact", "normalized", "hybrid"):
            m = fragmentation_merge_metrics(trees, mode)
            assert m["false_merge"] == 0.0


# ---------------------------------------------------------------------------
# Volatile-detection edge cases
# ---------------------------------------------------------------------------

class TestVolatileDetection:
    """Verify which text values are treated as volatile."""

    def _sig_with_text(self, text: str) -> str:
        el = [{"resource_id": "x", "class": "TextView", "text": text, "content_description": ""}]
        return state_signature(el, "normalized")

    def test_time_string_volatile(self):
        assert self._sig_with_text("17:05") == self._sig_with_text("12:30")

    def test_percent_volatile(self):
        assert self._sig_with_text("50 %") == self._sig_with_text("99 %")

    def test_app_count_volatile(self):
        assert self._sig_with_text("12 apps") == self._sig_with_text("5 apps")

    def test_plain_label_stable(self):
        assert self._sig_with_text("Display") != self._sig_with_text("Sound")

    def test_storage_size_volatile(self):
        assert self._sig_with_text("4 GB") == self._sig_with_text("128 GB")

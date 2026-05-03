import unittest

from llmsmartphone.android.backends.http.screen import compact_node, compact_nodes


def _node(**overrides):
    base = {
        "id": 1,
        "depth": 0,
        "text": "",
        "description": "",
        "className": "",
        "resourceId": "",
        "clickable": False,
        "checkable": False,
        "checked": False,
        "enabled": True,
        "selected": False,
        "focused": False,
        "scrollable": False,
        "password": False,
        "editable": False,
        "bounds": {"left": 0, "top": 0, "right": 100, "bottom": 100},
        "actions": [],
    }
    base.update(overrides)
    return base


class CompactNodeTest(unittest.TestCase):
    def test_drops_node_with_no_useful_signals(self) -> None:
        result = compact_node(_node(), screen_bottom=2424)
        self.assertIsNone(result)

    def test_keeps_seekbar_with_range_info_even_without_text(self) -> None:
        result = compact_node(
            _node(
                className="android.widget.SeekBar",
                rangeInfo={"type": 0, "min": 0.0, "max": 255.0, "current": 128.0},
            ),
            screen_bottom=2424,
        )
        assert result is not None
        self.assertEqual(
            {"type": "int", "min": 0.0, "max": 255.0, "current": 128.0},
            result["range"],
        )

    def test_surfaces_resource_id_when_present(self) -> None:
        result = compact_node(
            _node(
                text="Helligkeit",
                resourceId="com.android.settings:id/brightness_slider",
                clickable=True,
            ),
            screen_bottom=2424,
        )
        assert result is not None
        self.assertEqual("com.android.settings:id/brightness_slider", result["resource_id"])

    def test_omits_resource_id_when_blank(self) -> None:
        result = compact_node(
            _node(text="Foo", clickable=True, resourceId=""),
            screen_bottom=2424,
        )
        assert result is not None
        self.assertNotIn("resource_id", result)

    def test_decodes_known_actions_drops_unknown(self) -> None:
        result = compact_node(
            _node(
                text="Slider",
                clickable=True,
                actions=[16, 0x800020, 999999, 32],
            ),
            screen_bottom=2424,
        )
        assert result is not None
        self.assertEqual(["click", "set_progress", "long_click"], result["actions"])

    def test_omits_actions_field_when_no_known_actions(self) -> None:
        result = compact_node(
            _node(text="X", clickable=True, actions=[999999]),
            screen_bottom=2424,
        )
        assert result is not None
        self.assertNotIn("actions", result)

    def test_state_flags_only_emitted_when_true(self) -> None:
        result = compact_node(
            _node(
                text="Field",
                clickable=True,
                scrollable=True,
                editable=True,
            ),
            screen_bottom=2424,
        )
        assert result is not None
        self.assertTrue(result["scrollable"])
        self.assertTrue(result["editable"])

        plain = compact_node(_node(text="X", clickable=True), screen_bottom=2424)
        assert plain is not None
        self.assertNotIn("scrollable", plain)
        self.assertNotIn("editable", plain)

    def test_range_type_label_mapping(self) -> None:
        for code, label in [(0, "int"), (1, "float"), (2, "percent"), (99, "unknown")]:
            result = compact_node(
                _node(
                    className="android.widget.SeekBar",
                    rangeInfo={"type": code, "min": 0, "max": 1, "current": 0.5},
                ),
                screen_bottom=2424,
            )
            assert result is not None
            self.assertEqual(label, result["range"]["type"])

    def test_compact_nodes_preserves_index_assignment(self) -> None:
        nodes = [
            _node(text="A", clickable=True, bounds={"left": 0, "top": 0, "right": 50, "bottom": 50}),
            _node(text="B", clickable=True, bounds={"left": 0, "top": 60, "right": 50, "bottom": 100}),
        ]
        elements = compact_nodes(nodes, max_elements=10)
        self.assertEqual([1, 2], [e["index"] for e in elements])


if __name__ == "__main__":
    unittest.main()

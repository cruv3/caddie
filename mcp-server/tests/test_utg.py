"""Tests for caddie.explorer.utg — UI Transition Graph.

TDD: tests written before implementation.
No device, no real LLM, no network.
"""
from __future__ import annotations

import pytest
from caddie.explorer.utg import Budgets, UTG


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _el(text: str = "", index: int = 0, clickable: bool = True) -> dict:
    """Minimal element dict, safe/actionable by default."""
    return {
        "text": text,
        "content_description": "",
        "resource_id": "",
        "class": "android.widget.TextView",
        "index": index,
        "bounds": "[0,0][100,50]",
        "clickable": "true" if clickable else "false",
    }


def _bluetooth_el(index: int = 99) -> dict:
    """Element that is_safe_action returns False for."""
    return {
        "text": "Bluetooth",
        "content_description": "",
        "resource_id": "",
        "class": "android.widget.TextView",
        "index": index,
        "bounds": "[0,0][100,50]",
        "clickable": "true",
    }


def _dark_design_el(index: int = 3) -> dict:
    """Element that is_safe_action returns True for."""
    return {
        "text": "Dunkles Design",
        "content_description": "",
        "resource_id": "",
        "class": "android.widget.TextView",
        "index": index,
        "bounds": "[0,0][100,50]",
        "clickable": "true",
    }


SIG_A = "sig_state_a"
SIG_B = "sig_state_b"


# ---------------------------------------------------------------------------
# frontier: safety filtering
# ---------------------------------------------------------------------------

class TestFrontierSafety:
    def test_unsafe_element_excluded(self):
        """Bluetooth element must never appear in frontier."""
        utg = UTG()
        elements = [_bluetooth_el(index=1), _dark_design_el(index=2)]
        utg.add_state(SIG_A, elements)

        frontier = utg.frontier(SIG_A)
        labels = [e.get("text", "") for e in frontier]
        assert "Bluetooth" not in labels

    def test_safe_element_included(self):
        """Dunkles Design must appear in frontier."""
        utg = UTG()
        elements = [_bluetooth_el(index=1), _dark_design_el(index=2)]
        utg.add_state(SIG_A, elements)

        frontier = utg.frontier(SIG_A)
        labels = [e.get("text", "") for e in frontier]
        assert "Dunkles Design" in labels

    def test_all_unsafe_elements_excluded(self):
        """If all elements are unsafe, frontier is empty."""
        utg = UTG()
        elements = [_bluetooth_el(index=i) for i in range(5)]
        utg.add_state(SIG_A, elements)
        assert utg.frontier(SIG_A) == []

    def test_no_label_element_excluded(self):
        """Element with empty labels is fail-closed (is_safe_action returns False)."""
        utg = UTG()
        elements = [{"text": "", "content_description": "", "resource_id": "",
                     "class": "android.widget.TextView", "index": 5,
                     "bounds": "[0,0][50,50]", "clickable": "true"}]
        utg.add_state(SIG_A, elements)
        assert utg.frontier(SIG_A) == []


# ---------------------------------------------------------------------------
# frontier: visited tracking via add_edge
# ---------------------------------------------------------------------------

class TestFrontierVisited:
    def test_edge_action_removes_element_from_frontier(self):
        """After add_edge using index 3, that element disappears from frontier."""
        utg = UTG()
        el3 = _dark_design_el(index=3)
        el4 = _el(text="Schriftgroesse", index=4)
        utg.add_state(SIG_A, [el3, el4])
        utg.add_state(SIG_B, [])

        action = {"kind": "tap", "label": "Dunkles Design", "index": 3}
        utg.add_edge(SIG_A, action, SIG_B)

        frontier = utg.frontier(SIG_A)
        indices = [e.get("index") for e in frontier]
        assert 3 not in indices

    def test_unvisited_element_remains_in_frontier(self):
        """Element at index 4 must stay in frontier after index 3 is visited."""
        utg = UTG()
        el3 = _dark_design_el(index=3)
        el4 = _el(text="Schriftgroesse", index=4)
        utg.add_state(SIG_A, [el3, el4])
        utg.add_state(SIG_B, [])

        action = {"kind": "tap", "label": "Dunkles Design", "index": 3}
        utg.add_edge(SIG_A, action, SIG_B)

        frontier = utg.frontier(SIG_A)
        indices = [e.get("index") for e in frontier]
        assert 4 in indices

    def test_multiple_edges_exhaust_frontier(self):
        """Visiting all safe elements empties the frontier."""
        utg = UTG()
        elements = [_el(text=f"Item {i}", index=i) for i in range(3)]
        utg.add_state(SIG_A, elements)
        utg.add_state(SIG_B, [])

        for i in range(3):
            utg.add_edge(SIG_A, {"kind": "tap", "label": f"Item {i}", "index": i}, SIG_B)

        assert utg.frontier(SIG_A) == []


# ---------------------------------------------------------------------------
# mark_visited helper
# ---------------------------------------------------------------------------

class TestMarkVisited:
    def test_mark_visited_removes_from_frontier(self):
        utg = UTG()
        el = _dark_design_el(index=7)
        utg.add_state(SIG_A, [el])
        utg.mark_visited(SIG_A, 7)
        assert utg.frontier(SIG_A) == []

    def test_mark_visited_on_unknown_sig_is_noop(self):
        """mark_visited on an unknown sig must not raise."""
        utg = UTG()
        utg.mark_visited("nonexistent_sig", 0)  # should not raise


# ---------------------------------------------------------------------------
# add_state idempotency
# ---------------------------------------------------------------------------

class TestAddStateIdempotent:
    def test_add_state_twice_does_not_duplicate(self):
        utg = UTG()
        el = _dark_design_el(index=1)
        utg.add_state(SIG_A, [el])
        utg.add_state(SIG_A, [el])  # second call must be idempotent
        assert utg.state_count() == 1
        # frontier should not double-return elements
        assert len(utg.frontier(SIG_A)) == 1


# ---------------------------------------------------------------------------
# state_count
# ---------------------------------------------------------------------------

class TestStateCount:
    def test_state_count_increments(self):
        utg = UTG()
        assert utg.state_count() == 0
        utg.add_state(SIG_A, [])
        assert utg.state_count() == 1
        utg.add_state(SIG_B, [])
        assert utg.state_count() == 2

    def test_state_count_idempotent(self):
        utg = UTG()
        utg.add_state(SIG_A, [])
        utg.add_state(SIG_A, [])
        assert utg.state_count() == 1


# ---------------------------------------------------------------------------
# is_exhausted
# ---------------------------------------------------------------------------

class TestIsExhausted:
    def test_not_exhausted_when_frontier_nonempty(self):
        utg = UTG()
        utg.add_state(SIG_A, [_dark_design_el(index=1)])
        assert not utg.is_exhausted()

    def test_exhausted_when_all_frontiers_empty(self):
        utg = UTG()
        utg.add_state(SIG_A, [_dark_design_el(index=1)])
        utg.add_state(SIG_B, [])
        utg.add_edge(SIG_A, {"kind": "tap", "label": "Dunkles Design", "index": 1}, SIG_B)
        # SIG_B has empty elements, SIG_A frontier now empty
        assert utg.is_exhausted()

    def test_exhausted_when_no_states(self):
        """Fresh UTG with no states has nothing to explore."""
        utg = UTG()
        assert utg.is_exhausted()

    def test_exhausted_when_max_states_exceeded(self):
        """Exceeding max_states budget triggers exhaustion regardless of frontiers."""
        budgets = Budgets(max_states=2)
        utg = UTG(budgets)
        # Add more states than the budget allows
        utg.add_state("sig1", [_dark_design_el(index=1)])
        utg.add_state("sig2", [_dark_design_el(index=2)])
        utg.add_state("sig3", [_dark_design_el(index=3)])
        assert utg.is_exhausted()


# ---------------------------------------------------------------------------
# per_state_visit_budget
# ---------------------------------------------------------------------------

class TestPerStateVisitBudget:
    def test_frontier_capped_by_budget(self):
        """Frontier must not exceed per_state_visit_budget elements."""
        budgets = Budgets(per_state_visit_budget=2)
        utg = UTG(budgets)
        elements = [_el(text=f"Safe Item {i}", index=i) for i in range(10)]
        utg.add_state(SIG_A, elements)
        assert len(utg.frontier(SIG_A)) <= 2

    def test_budget_not_exceeded_after_visits(self):
        """After visiting 1 of budget=2, only 1 remains — never more than budget total."""
        budgets = Budgets(per_state_visit_budget=2)
        utg = UTG(budgets)
        elements = [_el(text=f"Item {i}", index=i) for i in range(10)]
        utg.add_state(SIG_A, elements)
        utg.add_state(SIG_B, [])

        first = utg.frontier(SIG_A)[0]
        utg.add_edge(SIG_A, {"kind": "tap", "label": first.get("text", ""), "index": first["index"]}, SIG_B)

        # Still within budget after one visit
        remaining = utg.frontier(SIG_A)
        assert len(remaining) == 1

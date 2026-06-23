"""UI Transition Graph (UTG) for the Caddie phone-knowledge explorer.

Tracks states (UI signatures), transitions (edges), and which elements have
been acted on so the crawler knows what remains to explore.

Safety contract: frontier() NEVER returns an element that fails is_safe_action().
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Optional

from caddie.explorer.safety import is_safe_action


@dataclass
class Budgets:
    """Hard limits that govern the exploration scope."""
    max_states: int = 15
    max_depth: int = 6
    per_state_visit_budget: int = 20


class UTG:
    """UI Transition Graph.

    Nodes: UI state signatures (str).
    Edges: (from_sig, action dict, to_sig).
    Tracks visited element indices per state to drive the frontier.
    """

    def __init__(self, budgets: Optional[Budgets] = None) -> None:
        self._budgets: Budgets = budgets or Budgets()
        # sig -> list[element dict]
        self._elements: dict[str, list[dict]] = {}
        # sig -> set of visited element indices (int)
        self._visited: dict[str, set[int]] = {}
        # list of (from_sig, action, to_sig)
        self._edges: list[tuple[str, dict, str]] = []

    # ------------------------------------------------------------------
    # Graph mutation
    # ------------------------------------------------------------------

    def add_state(self, sig: str, elements: list[dict]) -> None:
        """Register a UI state.  Idempotent: if sig is already known,
        the stored elements are preserved and the call is a no-op."""
        if sig not in self._elements:
            self._elements[sig] = list(elements)
            self._visited[sig] = set()

    def add_edge(self, from_sig: str, action: dict, to_sig: str) -> None:
        """Record a transition and mark the source element as visited."""
        self._edges.append((from_sig, action, to_sig))
        idx = action.get("index")
        if idx is not None and from_sig in self._visited:
            self._visited[from_sig].add(int(idx))

    def mark_visited(self, sig: str, element_index: int) -> None:
        """Mark an element index as visited for the given state.

        No-op if sig is unknown (safe to call speculatively)."""
        if sig in self._visited:
            self._visited[sig].add(element_index)

    # ------------------------------------------------------------------
    # Frontier query
    # ------------------------------------------------------------------

    def frontier(self, sig: str) -> list[dict]:
        """Return unvisited, safe, actionable elements for *sig*.

        Rules applied in order:
        1. Element must pass is_safe_action() — fail-closed safety gate.
        2. Element index must not be in the visited set for this state.
        3. Result is capped to per_state_visit_budget minus already-visited count.
        """
        elements = self._elements.get(sig, [])
        visited = self._visited.get(sig, set())

        result: list[dict] = []
        budget = self._budgets.per_state_visit_budget

        for el in elements:
            if len(result) + len(visited) >= budget:
                break
            if not is_safe_action(el):
                continue
            idx = el.get("index")
            if idx is not None and int(idx) in visited:
                continue
            result.append(el)

        return result

    # ------------------------------------------------------------------
    # Termination check
    # ------------------------------------------------------------------

    def is_exhausted(self) -> bool:
        """Return True when there is nothing left to explore.

        Conditions:
        - No states have been added, OR
        - max_states budget exceeded, OR
        - Every known state has an empty frontier.
        """
        if not self._elements:
            return True
        if len(self._elements) > self._budgets.max_states:
            return True
        return all(len(self.frontier(sig)) == 0 for sig in self._elements)

    def state_count(self) -> int:
        """Return the number of known states."""
        return len(self._elements)

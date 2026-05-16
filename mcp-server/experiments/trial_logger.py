"""Trial-Logger: schreibt pro Trial eine Markdown-Zeile in failure-mode-log.md
und einen vollständigen JSON-Dump nach experiments/results/<trial_id>.json."""

from __future__ import annotations

import json
import re
from datetime import datetime
from pathlib import Path
from typing import Any


_OUTCOME_EMOJI = {
    "done": "✅",
    "failed": "❌",
    "timeout": "⏱️",
    "error": "💥",
    "unknown": "❓",
}


class TrialLogger:
    def __init__(
        self,
        log_path: Path | None = None,
        results_dir: Path | None = None,
    ) -> None:
        repo_root = Path(__file__).resolve().parent.parent
        self.log_path = log_path or repo_root / "docs" / "failure-mode-log.md"
        self.results_dir = results_dir or repo_root / "experiments" / "results"
        self.results_dir.mkdir(parents=True, exist_ok=True)
        self._ensure_log_header()

    def append(
        self,
        trial_id: str,
        model: str,
        task: str,
        run: int,
        outcome: str,
        events: list[dict[str, Any]],
        screenshot: Path | None,
        timing: dict[str, float],
        lmstudio_response: Any = None,
        failure_tag: str = "",
        notes: str = "",
    ) -> None:
        # 1) JSON-Dump
        json_path = self.results_dir / f"{trial_id}.json"
        json_path.write_text(
            json.dumps(
                {
                    "trial_id": trial_id,
                    "timestamp_utc": datetime.utcnow().isoformat() + "Z",
                    "model": model,
                    "task": task,
                    "run": run,
                    "outcome": outcome,
                    "timing": timing,
                    "screenshot": str(screenshot) if screenshot else None,
                    "lmstudio_response": lmstudio_response,
                    "events": events,
                },
                indent=2,
                ensure_ascii=False,
                default=str,
            ),
            encoding="utf-8",
        )

        # 2) Markdown-Zeile
        emoji = _OUTCOME_EMOJI.get(outcome, "❓")
        tool_count = sum(
            1 for e in events if e.get("type") == "tool_call_started"
        )
        duration_s = round(timing.get("task", 0), 1)
        screenshot_rel = (
            screenshot.relative_to(self.log_path.parent.parent).as_posix()
            if screenshot
            else ""
        )
        screenshot_md = f"![s]({screenshot_rel})" if screenshot_rel else ""
        # Tabellen-Zellen escapen: Pipes raus
        safe_model = _md_cell(model)
        safe_task = _md_cell(task)
        safe_notes = _md_cell(notes)

        row = (
            f"| {trial_id} | {safe_model} | {safe_task} | {run} | "
            f"{emoji} {outcome} | {failure_tag} | {tool_count} | {duration_s} | "
            f"{screenshot_md} | {safe_notes} |\n"
        )
        with self.log_path.open("a", encoding="utf-8") as fh:
            fh.write(row)

    def _ensure_log_header(self) -> None:
        # Wenn das File noch das alte Tabellen-Format hat, ergänzen wir die neuen Spalten
        # einmalig durch einen separaten Block. Wir touchen das alte File nicht — der Runner
        # schreibt in einen eigenen Abschnitt ans Dateiende.
        if not self.log_path.exists():
            return
        content = self.log_path.read_text(encoding="utf-8")
        marker = "<!-- runner-trials-table -->"
        if marker in content:
            return
        header = (
            f"\n\n{marker}\n"
            "## Runner-Trials (automatisch geschrieben)\n\n"
            "| Trial-ID | Modell | Task | Run | Outcome | Failure-Tag | Tools | Dauer (s) | Screenshot | Notiz |\n"
            "|---|---|---|---|---|---|---|---|---|---|\n"
        )
        with self.log_path.open("a", encoding="utf-8") as fh:
            fh.write(header)


def _md_cell(s: str) -> str:
    return re.sub(r"\s+", " ", str(s).replace("|", "\\|")).strip()

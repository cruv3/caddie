# Experiments — Failure-Mode-Runner

Autonomer Trial-Runner für die Masterarbeit. Iteriert (Modell × Task × Run),
loggt Outcomes + Tool-Trace + After-Screenshots in `docs/failure-mode-log.md`
und `experiments/results/<trial_id>.json`.

## Voraussetzungen (vor dem Start manuell)

1. **Phone verbunden** über HTTP-Bridge (Port 8765) oder ADB. Die `LLMSmartphone_V2`-App muss laufen und der AccessibilityService aktiv sein.
2. **MCP-Server** läuft (LM Studio MCP-Integration `llmsmartphone` ist konfiguriert und aktiv).
3. **/task-HTTP-Server** läuft auf Port 5000. Start: `python -m llmsmartphone.agent.http_api` aus `mcp-server/`.
4. **SSE-Owner** auf Port 8787 läuft (passiert automatisch, sobald der MCP-Server bootet).
5. **`lms` CLI** im PATH (LM-Studio-Command-Line). Test: `lms --help`.
6. **Test-Modelle** sind in LM Studio gedownloadet (Liste in `trial_matrix.yaml`).
7. **`adb reverse tcp:8787 tcp:8787`** und ggf. `adb reverse tcp:8765 tcp:8765` sind gesetzt (für HTTP-Bridge).

## Quickstart

```powershell
# Aus mcp-server/
cd C:\Users\Andreas\dev\LLMSmartphone\mcp-server
. .venv\Scripts\Activate.ps1

# Dry-Run: nur Plan anzeigen
python -m experiments.run_trials --dry-run

# Smoke-Test: 1 Modell × 1 Task × 1 Run
python -m experiments.run_trials `
    --models qwen/qwen3.6-35b-a3b `
    --tasks dark_mode_on `
    --runs 1

# Volle Matrix (über Nacht)
python -m experiments.run_trials

# Falls Modell manuell laden (lms CLI nicht installiert):
python -m experiments.run_trials --skip-model-load

# Auswertung
python -m experiments.evaluator
```

## Was kommt raus

- `mcp-server/docs/failure-mode-log.md` — neue Zeilen unter `<!-- runner-trials-table -->`
- `mcp-server/experiments/results/<trial_id>.json` — voller Trace + Timing + LM-Studio-Response
- `mcp-server/screenshots/trials/<trial_id>.png` — After-Screenshot
- `mcp-server/experiments/results/SUMMARY.md` — Auswertung (nach `evaluator.py`)

Trial-ID-Format: `HHMMSS__<model_safe>__<task_id>__r<run>`

## Failure-Tag-Workflow

Der Runner setzt **kein** Failure-Tag — das geschieht beim Review. Spalte
„Failure-Tag" bleibt leer, Andreas trägt morgens manuell ein:

- `premature_termination` — `done` ohne Verifikation
- `unproductive_loop` — gleiche Aktion N-mal ohne Fortschritt
- `direction_confusion` — falsche Skill-Auswahl
- `grounding_error` — Tap neben Element
- `hallucinated_success` — Screenshot beschreibt was anderes als Realität
- `refusal` — verweigert vor Versuch
- `protocol_violation` — `done`/`failed` übersprungen
- `timeout` — bereits durch Outcome ausgedrückt
- `other` — Notiz erklärt

## Trial-Matrix anpassen

`experiments/trial_matrix.yaml` bearbeiten. Modelle, Tasks, runs_per_combo,
Timeouts. CLI-Overrides möglich (`--models`, `--tasks`, `--runs`).

## Bekannte Limitationen

- Phone-State akkumuliert über Trials (Browser-Tabs, Settings). Bei Bedarf
  alle ~10 Trials ein tieferer manueller Reset.
- `lms load` kann bei 30B-Modellen länger als 60s dauern — Timeout hochsetzen
  oder `--skip-model-load` nutzen und manuell wechseln.
- SSE-Verbindungsabbrüche werden vom Listener automatisch reconnected, aber
  Events während der Lücke sind verloren.

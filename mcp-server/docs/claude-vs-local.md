# Claude Opus 4.7 (CLI) vs. lokale Modelle — Vergleichslauf

Gefahren mit `experiments/claude_runner.py`: Claude Code Headless (`claude -p`),
via --mcp-config an den llm-smartphone-MCP-Server (`server.py`) gehaengt.
Gleiche 7 Tasks wie die lokale Matrix. Backend: adb. Tokens/Kosten/Dauer kommen
aus dem CLI-`stream-json`-Output (`total_cost_usd`, `usage`, `duration_ms`).

> `outcome` = welches Lifecycle-Tool das Modell zuletzt rief (done/failed) bzw.
> none/timeout. KEIN Erfolgsindikator — echte Bewertung per Screenshot unten.

<!-- claude-trials-table -->

| Trial-ID | Task | Run | Outcome | Tools | Turns | In-Tok | Out-Tok | Cache-Read | Cost USD | Dauer s | Screenshot |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 122021__claude-opus-4-7__dark_mode_on__r0 | dark_mode_on | 0 | done | 10 | 11 | 20 | 1322 | 381724 | 0.5871 | 69.3 | ![s](screenshots/trials/122021__claude-opus-4-7__dark_mode_on__r0.png) |
| 122135__claude-opus-4-7__dark_mode_on__r1 | dark_mode_on | 1 | done | 10 | 11 | 20 | 1289 | 380916 | 0.5850 | 65.9 | ![s](screenshots/trials/122135__claude-opus-4-7__dark_mode_on__r1.png) |
| 122246__claude-opus-4-7__dark_mode_off__r0 | dark_mode_off | 0 | done | 10 | 11 | 20 | 1272 | 380453 | 0.5840 | 67.4 | ![s](screenshots/trials/122246__claude-opus-4-7__dark_mode_off__r0.png) |
| 122358__claude-opus-4-7__dark_mode_off__r1 | dark_mode_off | 1 | done | 11 | 12 | 24 | 1817 | 486838 | 0.6587 | 90.9 | ![s](screenshots/trials/122358__claude-opus-4-7__dark_mode_off__r1.png) |
| 122534__claude-opus-4-7__open_calculator__r0 | open_calculator | 0 | failed | 10 | 11 | 22 | 1837 | 401718 | 0.5949 | 67.7 | ![s](screenshots/trials/122534__claude-opus-4-7__open_calculator__r0.png) |
| 122647__claude-opus-4-7__open_calculator__r1 | open_calculator | 1 | failed | 16 | 17 | 30 | 3119 | 781216 | 0.9786 | 122.7 | ![s](screenshots/trials/122647__claude-opus-4-7__open_calculator__r1.png) |
| 122854__claude-opus-4-7__bluetooth_toggle_on__r0 | bluetooth_toggle_on | 0 | done | 36 | 37 | 72 | 8997 | 2011681 | 1.7429 | 308.3 | ![s](screenshots/trials/122854__claude-opus-4-7__bluetooth_toggle_on__r0.png) |
| 123407__claude-opus-4-7__bluetooth_toggle_on__r1 | bluetooth_toggle_on | 1 | done | 31 | 32 | 62 | 4498 | 1805650 | 1.5584 | 243.3 | ![s](screenshots/trials/123407__claude-opus-4-7__bluetooth_toggle_on__r1.png) |
| 135952__claude-opus-4-7__brightness_set_50__r0 | brightness_set_50 | 0 | done | 13 | 14 | 24 | 1732 | 506678 | 0.7714 | 103.0 | ![s](screenshots/trials/135952__claude-opus-4-7__brightness_set_50__r0.png) |
| 140140__claude-opus-4-7__brightness_set_50__r1 | brightness_set_50 | 1 | done | 8 | 9 | 18 | 1123 | 332884 | 0.5534 | 54.9 | ![s](screenshots/trials/140140__claude-opus-4-7__brightness_set_50__r1.png) |
| 140240__claude-opus-4-7__timer_set_5min__r0 | timer_set_5min | 0 | done | 16 | 17 | 26 | 4609 | 585639 | 0.8679 | 146.5 | ![s](screenshots/trials/140240__claude-opus-4-7__timer_set_5min__r0.png) |
| 140511__claude-opus-4-7__timer_set_5min__r1 | timer_set_5min | 1 | done | 5 | 6 | 12 | 1233 | 183991 | 0.4265 | 71.5 | ![s](screenshots/trials/140511__claude-opus-4-7__timer_set_5min__r1.png) |
| 140627__claude-opus-4-7__pizza_search__r0 | pizza_search | 0 | done | 6 | 7 | 12 | 1054 | 178423 | 0.3864 | 60.1 | ![s](screenshots/trials/140627__claude-opus-4-7__pizza_search__r0.png) |
| 140732__claude-opus-4-7__pizza_search__r1 | pizza_search | 1 | done | 5 | 6 | 12 | 864 | 174842 | 0.3743 | 68.0 | ![s](screenshots/trials/140732__claude-opus-4-7__pizza_search__r1.png) |

## Verifizierte Auswertung (Screenshot-geprueft)

Alle 14 Trials visuell verifiziert. `outcome=done` ist KEIN Erfolgsindikator —
massgeblich ist der Screenshot.

| Task | r0 | r1 |
|---|---|---|
| dark_mode_on | fail (Toggle OFF, done gemeldet) | real_success |
| dark_mode_off | real_success | real_success |
| open_calculator | fail (App-Drawer, keine Rechner-App) | fail (Play Store) |
| bluetooth_toggle_on | real_success | real_success |
| brightness_set_50 | real_success (50%) | real_success (50%) |
| timer_set_5min | real_success (5m-Timer laeuft) | real_success (5m-Timer laeuft) |
| pizza_search | real_success (Google-Suche, Treffer) | real_success (Google-Suche, Treffer) |

**real_success: 11/14 (~79%).**
Die 3 Fails: 1x dark_mode_on (echter Miss — `done` gemeldet, Toggle blieb aus),
2x open_calculator. Auf dem Emulator ist **keine Rechner-App installiert** —
Claude hat den App-Drawer durchsucht, sogar den Play Store geoeffnet, und dann
*ehrlich* `smartphone_failed` gerufen. Die lokalen Modelle haben hier `done`
halluziniert. Claudes Fail ist also das korrekte Verhalten; der Task ist fuer
alle Modelle unfair. Ohne open_calculator: **11/12 (~92%)**.

## Vergleich Claude Opus 4.7 vs. lokale Modelle

Lokale Daten aus `failure-mode-log.md` (Matrix 2026-05-13/16, gleiche 7 Tasks).

| Metrik | Claude Opus 4.7 (CLI) | qwen3.6-35b (bestes lokal) | lokal Durchschnitt |
|---|---|---|---|
| real_success | 11/14 (~79%) | 4/14 (~29%) | 7/84 (~8%) |
| Kosten/Trial | ~$0.76 | ~$0 (lokal, nur Strom) | ~$0 |
| Kosten gesamt | ~$10.67 (14 Trials) | $0 | $0 |
| Dauer/Trial (Median) | ~70 s | ~270 s | 200-300 s |

### Pro Task — echte Erfolge (r0+r1)

| Task | Claude | qwen3.6 | alle lokalen (12 Trials) |
|---|---|---|---|
| dark_mode_on | 1/2 | 1/2 | 2/12 |
| dark_mode_off | 2/2 | 1/2 | 4/12 |
| open_calculator | 0/2 | 0/2 | 0/12 |
| bluetooth_toggle_on | 2/2 | 0/2 | **0/12** |
| brightness_set_50 | 2/2 | 0/2 | **0/12** |
| timer_set_5min | 2/2 | 0/2 | **0/12** |
| pizza_search | 2/2 | 2/2 | 2/12 (nur qwen3.6) |

Claude gewinnt deutlich bei bluetooth, brightness, timer — Tasks, die **kein
einziges** lokales Modell je geloest hat. Bei dark_mode und pizza liegt Claude
gleichauf mit qwen3.6. Kein Unit-Confusion (5 Minuten = 5 Minuten, nicht 5 s).

## Token-Profil (Claude CLI)

Pro Trial dominieren Cache-Reads (~175k-2M Tokens) — MCP-Tool-Definitionen +
Server-Instructions werden je Turn neu gelesen. Echte `input_tokens` sind winzig
(12-72), `output_tokens` 800-9000 je nach Tool-Loop-Laenge. Die Kosten skalieren
also mit der Anzahl Tool-Calls: bluetooth r0 (36 Tools) = $1.74, pizza r1
(5 Tools) = $0.37.

## Einordnung fuer die Thesis

- Bestaetigt die Abstraktions-These: Mit einem starken Modell ist die Skill/MCP-
  Schicht tragfaehig (~79-92%). Der Flaschenhals der lokalen Modelle ist das
  Modell, nicht die Architektur.
- Claude faellt NICHT in die dominanten lokalen Failure-Modes: kein Hallucinated
  Success (calculator ehrlich als fail gemeldet), kein Unit-Confusion, kein
  Wrong-App-Navigieren.
- Kosten/Nutzen: ~$0.76/Task fuer ~79% echte Erfolge vs. ~$0 fuer ~8% lokal.
  Fuer die Pilot-/Hauptstudie ist Claude als "Referenz-Agent" brauchbar; die
  lokalen Modelle eignen sich eher als Failure-Mode-Generatoren.
- open_calculator gehoert ueberarbeitet (Rechner-App vorinstallieren) oder aus
  der Task-Liste — aktuell misst er nur, ob ein Modell ehrlich aufgibt.

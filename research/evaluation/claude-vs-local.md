# Claude Opus 4.7 (CLI) vs. lokale Modelle — Vergleichslauf

> Evidence note: Recovered screenshots associated by trial identifier with all
> 12 Claude runs, their SHA-256 hashes, and the matching result records are
> indexed in [`model-trial-screenshots/manifest.csv`](model-trial-screenshots/manifest.csv).
> The files were recovered after the evaluation; the package README records the
> resulting provenance boundary.

Gefahren mit `experiments/claude_runner.py`: Claude Code Headless (`claude -p`),
via `--mcp-config` an den llm-smartphone-MCP-Server (`server.py`) gehaengt.
Gleiche sechs Tasks wie die lokale Matrix. Backend: ADB. Tokens, Kosten und
Dauer kommen aus dem CLI-`stream-json`-Output (`total_cost_usd`, `usage`,
`duration_ms`).

Der Runner uebergab `--model opus`, also den Alias fuer das jeweils aktuelle
Opus-Modell, und schrieb `claude-opus-4-7` als eigenes Label in die
Ergebnisdateien. Der Vergleich wurde am 16. Mai 2026 eingecheckt. Opus 4.7 war
zu diesem Zeitpunkt veroeffentlicht; Opus 4.8 erschien erst am 28. Mai 2026.
Diese Datumszuordnung stuetzt die Modellbezeichnung, waehrend die gespeicherten
JSON-Dateien selbst keine vom Dienst zurueckgegebene vollstaendige Modell-ID
enthalten.

> `outcome` bezeichnet das zuletzt aufgerufene Lifecycle-Tool (`done` oder
> `failed`) beziehungsweise `none` oder `timeout`. Es ist kein
> Erfolgsindikator; die manuelle Bewertung beruht auf dem Endzustand im
> zugeordneten Screenshot.

| Trial-ID | Task | Run | Outcome | Tools | Turns | In-Tok | Out-Tok | Cache-Read | Cost USD | Dauer s |
| --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 122021__claude-opus-4-7__dark_mode_on__r0 | dark_mode_on | 0 | done | 10 | 11 | 20 | 1322 | 381724 | 0.5871 | 69.3 |
| 122135__claude-opus-4-7__dark_mode_on__r1 | dark_mode_on | 1 | done | 10 | 11 | 20 | 1289 | 380916 | 0.5850 | 65.9 |
| 122246__claude-opus-4-7__dark_mode_off__r0 | dark_mode_off | 0 | done | 10 | 11 | 20 | 1272 | 380453 | 0.5840 | 67.4 |
| 122358__claude-opus-4-7__dark_mode_off__r1 | dark_mode_off | 1 | done | 11 | 12 | 24 | 1817 | 486838 | 0.6587 | 90.9 |
| 122854__claude-opus-4-7__bluetooth_toggle_on__r0 | bluetooth_toggle_on | 0 | done | 36 | 37 | 72 | 8997 | 2011681 | 1.7429 | 308.3 |
| 123407__claude-opus-4-7__bluetooth_toggle_on__r1 | bluetooth_toggle_on | 1 | done | 31 | 32 | 62 | 4498 | 1805650 | 1.5584 | 243.3 |
| 135952__claude-opus-4-7__brightness_set_50__r0 | brightness_set_50 | 0 | done | 13 | 14 | 24 | 1732 | 506678 | 0.7714 | 103.0 |
| 140140__claude-opus-4-7__brightness_set_50__r1 | brightness_set_50 | 1 | done | 8 | 9 | 18 | 1123 | 332884 | 0.5534 | 54.9 |
| 140240__claude-opus-4-7__timer_set_5min__r0 | timer_set_5min | 0 | done | 16 | 17 | 26 | 4609 | 585639 | 0.8679 | 146.5 |
| 140511__claude-opus-4-7__timer_set_5min__r1 | timer_set_5min | 1 | done | 5 | 6 | 12 | 1233 | 183991 | 0.4265 | 71.5 |
| 140627__claude-opus-4-7__pizza_search__r0 | pizza_search | 0 | done | 6 | 7 | 12 | 1054 | 178423 | 0.3864 | 60.1 |
| 140732__claude-opus-4-7__pizza_search__r1 | pizza_search | 1 | done | 5 | 6 | 12 | 864 | 174842 | 0.3743 | 68.0 |

The manifest provides the current screenshot and result-record path for every
row.

## Verifizierte Auswertung

Die manuelle Klassifikation ordnete 11 der 12 Endzustaende als
`real_success` ein. `outcome=done` allein ist kein Erfolgsindikator.

| Task | r0 | r1 |
| --- | --- | --- |
| dark_mode_on | fail (Toggle OFF, `done` gemeldet) | real_success |
| dark_mode_off | real_success | real_success |
| bluetooth_toggle_on | real_success | real_success |
| brightness_set_50 | real_success (50 %) | real_success (50 %) |
| timer_set_5min | real_success (5-Minuten-Timer laeuft) | real_success (5-Minuten-Timer laeuft) |
| pizza_search | real_success (Google-Suche, Treffer) | real_success (Google-Suche, Treffer) |

**real_success: 11/12 (rund 92 %).** Der einzige als Fehler klassifizierte
Lauf war `dark_mode_on` r0: Das Modell meldete `done`, der Toggle blieb jedoch
aus.

## Vergleich Claude Opus 4.7 vs. lokale Modelle

Die lokalen Werte stammen aus `failure-mode-log.md` (Matrix vom
13./16. Mai 2026, dieselben sechs Tasks).

| Metrik | Claude Opus 4.7 (CLI) | qwen3.6-35b (bestes lokales Modell) | lokaler Durchschnitt |
| --- | ---: | ---: | ---: |
| real_success | 11/12 (rund 92 %) | 4/12 (rund 33 %) | 7/72 (rund 10 %) |
| Kosten pro Trial | rund $0.76 | rund $0 (lokal, nur Strom) | rund $0 |
| Kosten gesamt | rund $9.10 (12 Trials) | $0 | $0 |
| Dauer pro Trial (Median) | rund 70 s | rund 270 s | 200–300 s |

| Task | Claude | qwen3.6 | alle lokalen Modelle (12 Trials) |
| --- | ---: | ---: | ---: |
| dark_mode_on | 1/2 | 1/2 | 2/12 |
| dark_mode_off | 2/2 | 1/2 | 4/12 |
| bluetooth_toggle_on | 2/2 | 0/2 | 0/12 |
| brightness_set_50 | 2/2 | 0/2 | 0/12 |
| timer_set_5min | 2/2 | 0/2 | 0/12 |
| pizza_search | 2/2 | 2/2 | 2/12 |

Die Zahlen sind Entwicklungsevidenz aus der frueheren Python/ADB-Ausfuehrung,
kein Benchmark der heutigen Android-eigenen Laufzeit. Die Erfolgslabels wurden
vom Autor manuell vergeben und weder unabhaengig noch blind annotiert.

## Token-Profil

Pro Trial dominieren Cache-Reads (rund 175.000 bis 2 Millionen Tokens), weil
MCP-Tool-Definitionen und Server-Instruktionen in den Turns erneut gelesen
werden. Die Kosten steigen deshalb mit der Zahl der Tool-Aufrufe: Der
Bluetooth-Lauf r0 mit 36 Tools kostete $1.74, der Pizza-Lauf r1 mit fuenf Tools
$0.37.

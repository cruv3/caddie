# Failure-Mode-Sampling - Matrix Run 2026-05-13

> Release note: The tabulated screenshot assessments are retained, but the
> source screenshots themselves are not part of this repository snapshot.

**Setup:** 6 Modelle x 6 Tasks x 2 Runs = 72 Trials. Pixel-9a-Emulator, HTTP-Bridge, /task auf :8787.
Loop-Cap: 25 Tool-Calls -> fail_loop. Hard-Timeout 300s/Trial.

> WICHTIG: Die `outcome`-Spalte aus dem Runner (`done`/`failed`/...) ist KEIN Erfolgsindikator.
> `done` heisst nur "Modell hat `smartphone_done` aufgerufen". Die echte Bewertung kommt aus
> der manuellen Screenshot-Verifikation unten.

## Roh-Outcomes (Runner-Log, irrefuehrend)


| Modell               | done | failed | timeout | fail_loop |
| -------------------- | ---- | ------ | ------- | --------- |
| qwen/qwen3.6-35b-a3b | 7    | 5      | 0       | 0         |
| qwen/qwen3-vl-8b     | 5    | 1      | 0       | 6         |
| qwen/qwen3-8b        | 12   | 0      | 0       | 0         |
| google/gemma-4-e4b   | 9    | 3      | 0       | 0         |
| gemma-4-e2b-it       | 12   | 0      | 0       | 0         |
| pixtral-12b          | 9    | 1      | 1       | 1         |

qwen3-8b und gemma-4-e2b-it melden beide 100% `done`. Die Verifikation zeigt: real nahe 0%.

> qwen3.6-Zeile aktualisiert nach dem Neu-Lauf am 2026-05-16 (mit Screenshots,
> adb-Backend, LLM_STUDIO_TIMEOUT=600). Der urspruengliche Lauf vom 2026-05-13
> hatte keine Screenshots — siehe Abschnitt unten.

## Verifizierte Auswertung (Screenshot-geprueft)

Kategorien:

- **real_success**: Endzustand korrekt UND vom Modell aktiv herbeigefuehrt
- **passive_success**: Endzustand korrekt, aber zufaellig (Modell hat nichts Sinnvolles getan,
  State stammt aus vorigem Trial)
- **fail**: Endzustand falsch
- **fail_loop**: >25 Tool-Calls ohne Fortschritt (Stuck-Loop)

### qwen/qwen3.6-35b-a3b


| Task                | r0                                                | r1                                                     |
| ------------------- | ------------------------------------------------- | ------------------------------------------------------ |
| dark_mode_on        | real_success                                      | fail (Light Mode, Toggle OFF)                          |
| dark_mode_off       | fail (Dark Mode noch an)                          | real_success                                           |
| bluetooth_toggle_on | fail (Settings-Root)                              | fail (Saved-Devices-Seite, nicht getoggelt)            |
| brightness_set_50   | fail (Display-Seite, 83% statt 50%)               | fail (Settings-Root)                                   |
| timer_set_5min      | fail (Home Screen)                                | fail (Timer-App offen, aber 00:00 nicht gesetzt)       |
| pizza_search        | real_success (Yelp: 10 Pizza-Lokale in der Naehe) | real_success (Google-Suche "pizza restaurant near me") |

**real_success: 4/12.**

### qwen/qwen3-vl-8b


| Task                | r0                     | r1                     |
| ------------------- | ---------------------- | ---------------------- |
| dark_mode_on        | real_success           | fail_loop              |
| dark_mode_off       | fail                   | real_success           |
| bluetooth_toggle_on | fail (Google services) | fail (Google services) |
| brightness_set_50   | fail_loop              | fail_loop              |
| timer_set_5min      | fail_loop              | fail (5s statt 5min)   |
| pizza_search        | fail_loop              | fail_loop              |

**real_success: 2/12.** Beide echten Erfolge sind Dark-Mode-Toggles.

### qwen/qwen3-8b (verifiziert)


| Task                | r0                     | r1                     |
| ------------------- | ---------------------- | ---------------------- |
| dark_mode_on        | fail (Light Mode)      | fail (Toggle OFF)      |
| dark_mode_off       | passive_success        | passive_success        |
| bluetooth_toggle_on | fail (Google services) | fail (Google services) |
| brightness_set_50   | fail (Messages-App)    | fail (Messages-App)    |
| timer_set_5min      | fail (5s statt 5min)   | fail (5s statt 5min)   |
| pizza_search        | passive_success        | passive_success        |

**real_success: 0/12.** 12x `done` gemeldet, kein einziger echter Erfolg.

### google/gemma-4-e4b


| Task                | r0                     | r1                                     |
| ------------------- | ---------------------- | -------------------------------------- |
| dark_mode_on        | real_success           | fail                                   |
| dark_mode_off       | fail                   | fail                                   |
| bluetooth_toggle_on | fail (Google services) | fail (Settings-Suche, nicht getoggelt) |
| brightness_set_50   | fail (Messages-App)    | fail (Messages-App)                    |
| timer_set_5min      | fail (5s statt 5min)   | fail (5s statt 5min)                   |
| pizza_search        | passive_success        | passive_success                        |

**real_success: 1/12.**

### gemma-4-e2b-it (verifiziert)


| Task                | r0                     | r1                     |
| ------------------- | ---------------------- | ---------------------- |
| dark_mode_on        | fail + SAFETY-INCIDENT | fail (Home Screen)     |
| dark_mode_off       | passive_success        | passive_success        |
| bluetooth_toggle_on | fail (Google services) | fail (Google services) |
| brightness_set_50   | fail (Home Screen)     | fail (Home Screen)     |
| timer_set_5min      | fail (5s statt 5min)   | fail (5s statt 5min)   |
| pizza_search        | passive_success        | passive_success        |

**real_success: 0/12.**
**Safety-Incident dark_mode_on r0:** Modell navigierte auf die App-Info-Seite der Agent-App
selbst und loeste einen "Do you want to uninstall this app?"-Dialog aus. Bei einem
Dark-Mode-Task. Der Agent stand kurz davor sich selbst zu deinstallieren.

### pixtral-12b


| Task                | r0                                 | r1                     |
| ------------------- | ---------------------------------- | ---------------------- |
| dark_mode_on        | fail (Toggle OFF)                  | timeout                |
| dark_mode_off       | passive_success                    | fail                   |
| bluetooth_toggle_on | fail_loop                          | fail (Google services) |
| brightness_set_50   | fail (Messages-App)                | fail (vermutl. gleich) |
| timer_set_5min      | fail (5s, Toast luegt "5 Minuten") | fail (vermutl. gleich) |
| pizza_search        | passive_success                    | passive_success        |

**real_success: 0/12** (Stichprobe).

## Gesamtbilanz

Alle 6 Modelle visuell verifiziert (72 Trials):


| Modell          | done (roh) | real_success (verifiziert) |
| --------------- | ---------- | -------------------------- |
| qwen3.6-35b-a3b | 7          | 4                          |
| qwen3-vl-8b     | 5          | 2                          |
| qwen3-8b        | 12         | 0                          |
| gemma-4-e4b     | 9          | 1                          |
| gemma-4-e2b-it  | 12         | 0                          |
| pixtral-12b     | 9          | 0                          |

**7 echte Erfolge bei 72 Trials. Verifizierte Erfolgsrate: ~10%.**
Roh-`done`-Rate ueber alle 72 Trials: ~75%.

Modell-Ranking nach echtem Erfolg: qwen3.6 (4) > qwen3-vl-8b (2) > gemma-4-e4b (1)

## Failure-Modes

1. **Hallucinated Success** - `done` gemeldet, Phone unveraendert (Home Screen).
   Dominant bei gemma-4-e2b-it und qwen3-8b.
2. **Wrong-App-Navigation** - aktiv in die falsche App genavigiert:
   brightness -> Google Messages, bluetooth -> Google services.
   Nicht nur "nichts getan", sondern selbstsicher falsch.
3. **Unit-Confusion** - "5 Minuten" -> 5-Sekunden-Timer. Modellweit, alle Modelle.
4. **Tool-Loop / Stuck-Loop** - >25 Tool-Calls ohne Fortschritt.
   Vor allem Vision-Modelle (qwen3-vl-8b 6x, pixtral 3x).
5. **Right-Page-No-Action** - richtige Settings-Seite erreicht, Toggle nicht betaetigt
   (qwen3-8b dark_mode_on r1, pixtral dark_mode_on r0).
6. **Skill-Level-Hallucination** - der Skill-Output selbst luegt: pixtral-Timer-Toast
   meldet "Timer auf 5 Minuten gestartet", real laeuft ein 5-Sekunden-Timer.
7. **Safety-Incident** - Agent loest destruktiven Dialog aus (App-Deinstallation)
   bei einem harmlosen Task.

<!-- runner-trials-table -->

## Runner-Trials (automatisch geschrieben)


| Trial-ID                                             | Modell               | Task                | Run | Outcome   | Failure-Tag | Tools | Dauer (s) | Screenshot                                                                        | Notiz |
| ---------------------------------------------------- | -------------------- | ------------------- | --- | --------- | ----------- | ----- | --------- | --------------------------------------------------------------------------------- | ----- |
| 110309__qwen_qwen36-35b-a3b__dark_mode_on__r0        | qwen/qwen3.6-35b-a3b | dark_mode_on        | 0   | ✅ done   |             | 10    | 272.8     | not retained        |       |
| 110749__qwen_qwen36-35b-a3b__dark_mode_on__r1        | qwen/qwen3.6-35b-a3b | dark_mode_on        | 1   | ✅ done   |             | 10    | 253.4     | not retained        |       |
| 111207__qwen_qwen36-35b-a3b__dark_mode_off__r0       | qwen/qwen3.6-35b-a3b | dark_mode_off       | 0   | ✅ done   |             | 10    | 249.7     | not retained       |       |
| 111622__qwen_qwen36-35b-a3b__dark_mode_off__r1       | qwen/qwen3.6-35b-a3b | dark_mode_off       | 1   | ✅ done   |             | 10    | 238.7     | not retained       |       |
| 113013__qwen_qwen36-35b-a3b__bluetooth_toggle_on__r0 | qwen/qwen3.6-35b-a3b | bluetooth_toggle_on | 0   | ❌ failed |             | 4     | 300.2     | not retained |       |
| 113518__qwen_qwen36-35b-a3b__bluetooth_toggle_on__r1 | qwen/qwen3.6-35b-a3b | bluetooth_toggle_on | 1   | ❌ failed |             | 9     | 300.3     | not retained |       |
| 114023__qwen_qwen36-35b-a3b__brightness_set_50__r0   | qwen/qwen3.6-35b-a3b | brightness_set_50   | 0   | ❌ failed |             | 7     | 300.3     | not retained   |       |
| 114528__qwen_qwen36-35b-a3b__brightness_set_50__r1   | qwen/qwen3.6-35b-a3b | brightness_set_50   | 1   | ❌ failed |             | 7     | 300.3     | not retained   |       |
| 115033__qwen_qwen36-35b-a3b__timer_set_5min__r0      | qwen/qwen3.6-35b-a3b | timer_set_5min      | 0   | ❌ failed |             | 6     | 300.3     | not retained      |       |
| 115539__qwen_qwen36-35b-a3b__timer_set_5min__r1      | qwen/qwen3.6-35b-a3b | timer_set_5min      | 1   | ❌ failed |             | 8     | 300.4     | not retained      |       |
| 120044__qwen_qwen36-35b-a3b__pizza_search__r0        | qwen/qwen3.6-35b-a3b | pizza_search        | 0   | ✅ done   |             | 7     | 300.2     | not retained        |       |
| 120549__qwen_qwen36-35b-a3b__pizza_search__r1        | qwen/qwen3.6-35b-a3b | pizza_search        | 1   | ✅ done   |             | 8     | 187.6     | not retained        |       |
| 091321__google_gemma-4-e4b__dark_mode_on__r0 | google/gemma-4-e4b | dark_mode_on | 0 | ❌ failed |  | 20 | 161.1 | not retained |  |
| 091611__google_gemma-4-e4b__dark_mode_on__r1 | google/gemma-4-e4b | dark_mode_on | 1 | ❌ failed |  | 13 | 111.6 | not retained |  |
| 091811__google_gemma-4-e4b__dark_mode_on__r2 | google/gemma-4-e4b | dark_mode_on | 2 | ❌ failed |  | 9 | 64.0 | not retained |  |
| 091923__google_gemma-4-e4b__dark_mode_off__r0 | google/gemma-4-e4b | dark_mode_off | 0 | ❌ failed |  | 25 | 225.7 | not retained |  |
| 092317__google_gemma-4-e4b__dark_mode_off__r1 | google/gemma-4-e4b | dark_mode_off | 1 | ❌ failed |  | 16 | 123.6 | not retained |  |
| 092528__google_gemma-4-e4b__dark_mode_off__r2 | google/gemma-4-e4b | dark_mode_off | 2 | ❌ failed |  | 11 | 78.5 | not retained |  |
| 092655__google_gemma-4-e4b__bluetooth_toggle_on__r0 | google/gemma-4-e4b | bluetooth_toggle_on | 0 | ✅ done |  | 12 | 93.1 | not retained |  |
| 092836__google_gemma-4-e4b__bluetooth_toggle_on__r1 | google/gemma-4-e4b | bluetooth_toggle_on | 1 | ✅ done |  | 12 | 80.6 | not retained |  |
| 093005__google_gemma-4-e4b__bluetooth_toggle_on__r2 | google/gemma-4-e4b | bluetooth_toggle_on | 2 | ✅ done |  | 13 | 88.1 | not retained |  |
| 093141__google_gemma-4-e4b__brightness_set_50__r0 | google/gemma-4-e4b | brightness_set_50 | 0 | ✅ done |  | 11 | 81.1 | not retained |  |
| 093310__google_gemma-4-e4b__brightness_set_50__r1 | google/gemma-4-e4b | brightness_set_50 | 1 | ✅ done |  | 9 | 61.5 | not retained |  |
| 093420__google_gemma-4-e4b__brightness_set_50__r2 | google/gemma-4-e4b | brightness_set_50 | 2 | ❌ failed |  | 14 | 104.6 | not retained |  |
| 093613__google_gemma-4-e4b__timer_set_5min__r0 | google/gemma-4-e4b | timer_set_5min | 0 | ✅ done |  | 11 | 74.5 | not retained |  |
| 093735__google_gemma-4-e4b__timer_set_5min__r1 | google/gemma-4-e4b | timer_set_5min | 1 | ✅ done |  | 9 | 64.5 | not retained |  |
| 093847__google_gemma-4-e4b__timer_set_5min__r2 | google/gemma-4-e4b | timer_set_5min | 2 | ✅ done |  | 10 | 69.0 | not retained |  |
| 094004__google_gemma-4-e4b__pizza_search__r0 | google/gemma-4-e4b | pizza_search | 0 | ✅ done |  | 7 | 62.0 | not retained |  |
| 094113__google_gemma-4-e4b__pizza_search__r1 | google/gemma-4-e4b | pizza_search | 1 | ✅ done |  | 2 | 17.0 | not retained |  |
| 094139__google_gemma-4-e4b__pizza_search__r2 | google/gemma-4-e4b | pizza_search | 2 | ✅ done |  | 2 | 24.5 | not retained |  |
| 130208__google_gemma-4-e4b__dark_mode_on__r0 | google/gemma-4-e4b | dark_mode_on | 0 | ❌ failed |  | 15 | 135.1 | not retained |  |
| 130431__google_gemma-4-e4b__dark_mode_on__r1 | google/gemma-4-e4b | dark_mode_on | 1 | ❌ failed |  | 21 | 211.6 |  |  |
| 130819__google_gemma-4-e4b__dark_mode_on__r2 | google/gemma-4-e4b | dark_mode_on | 2 | ✅ done |  | 11 | 106.6 | not retained |  |
| 131014__google_gemma-4-e4b__dark_mode_off__r0 | google/gemma-4-e4b | dark_mode_off | 0 | ❌ failed |  | 10 | 76.5 | not retained |  |
| 131138__google_gemma-4-e4b__dark_mode_off__r1 | google/gemma-4-e4b | dark_mode_off | 1 | ❌ failed |  | 20 | 189.6 | not retained |  |
| 131455__google_gemma-4-e4b__dark_mode_off__r2 | google/gemma-4-e4b | dark_mode_off | 2 | ❌ failed |  | 11 | 95.1 | not retained |  |
| 131638__google_gemma-4-e4b__bluetooth_toggle_on__r0 | google/gemma-4-e4b | bluetooth_toggle_on | 0 | ✅ done |  | 10 | 98.1 |  |  |
| 131833__google_gemma-4-e4b__bluetooth_toggle_on__r1 | google/gemma-4-e4b | bluetooth_toggle_on | 1 | ✅ done |  | 10 | 92.6 |  |  |
| 132022__google_gemma-4-e4b__bluetooth_toggle_on__r2 | google/gemma-4-e4b | bluetooth_toggle_on | 2 | ✅ done |  | 10 | 98.1 |  |  |
| 132216__google_gemma-4-e4b__brightness_set_50__r0 | google/gemma-4-e4b | brightness_set_50 | 0 | ❌ failed |  | 16 | 147.1 | not retained |  |
| 132450__google_gemma-4-e4b__brightness_set_50__r1 | google/gemma-4-e4b | brightness_set_50 | 1 | ❓ fail_loop |  | 26 | 237.7 | not retained |  |
| 132856__google_gemma-4-e4b__brightness_set_50__r2 | google/gemma-4-e4b | brightness_set_50 | 2 | ✅ done |  | 12 | 107.6 |  |  |
| 133100__google_gemma-4-e4b__timer_set_5min__r0 | google/gemma-4-e4b | timer_set_5min | 0 | ✅ done |  | 12 | 94.1 | not retained |  |
| 133241__google_gemma-4-e4b__timer_set_5min__r1 | google/gemma-4-e4b | timer_set_5min | 1 | ✅ done |  | 10 | 81.6 | not retained |  |
| 133410__google_gemma-4-e4b__timer_set_5min__r2 | google/gemma-4-e4b | timer_set_5min | 2 | ✅ done |  | 13 | 103.1 | not retained |  |
| 133601__google_gemma-4-e4b__pizza_search__r0 | google/gemma-4-e4b | pizza_search | 0 | ✅ done |  | 2 | 29.5 |  |  |
| 133647__google_gemma-4-e4b__pizza_search__r1 | google/gemma-4-e4b | pizza_search | 1 | ✅ done |  | 8 | 95.6 | not retained |  |
| 133839__google_gemma-4-e4b__pizza_search__r2 | google/gemma-4-e4b | pizza_search | 2 | ✅ done |  | 2 | 19.5 | not retained |  |
| 060101__google_gemma-4-e4b__dark_mode_on__r0 | google/gemma-4-e4b | dark_mode_on | 0 | ❌ failed |  | 0 | 0.5 | not retained |  |
| 060108__google_gemma-4-e4b__dark_mode_on__r1 | google/gemma-4-e4b | dark_mode_on | 1 | ❌ failed |  | 0 | 0.5 | not retained |  |
| 060114__google_gemma-4-e4b__dark_mode_on__r2 | google/gemma-4-e4b | dark_mode_on | 2 | ❌ failed |  | 0 | 0.5 | not retained |  |
| 060120__google_gemma-4-e4b__dark_mode_off__r0 | google/gemma-4-e4b | dark_mode_off | 0 | ❌ failed |  | 0 | 0.5 | not retained |  |
| 060126__google_gemma-4-e4b__dark_mode_off__r1 | google/gemma-4-e4b | dark_mode_off | 1 | ❌ failed |  | 0 | 0.5 | not retained |  |
| 060132__google_gemma-4-e4b__dark_mode_off__r2 | google/gemma-4-e4b | dark_mode_off | 2 | ❌ failed |  | 0 | 0.5 | not retained |  |
| 060137__google_gemma-4-e4b__bluetooth_toggle_on__r0 | google/gemma-4-e4b | bluetooth_toggle_on | 0 | ❌ failed |  | 0 | 0.5 | not retained |  |
| 060143__google_gemma-4-e4b__bluetooth_toggle_on__r1 | google/gemma-4-e4b | bluetooth_toggle_on | 1 | ❌ failed |  | 0 | 0.5 | not retained |  |
| 060238__google_gemma-4-e4b__dark_mode_on__r0 | google/gemma-4-e4b | dark_mode_on | 0 | ❓ fail_loop |  | 26 | 207.2 | not retained |  |
| 060610__google_gemma-4-e4b__dark_mode_on__r1 | google/gemma-4-e4b | dark_mode_on | 1 | ❌ failed |  | 10 | 66.0 | not retained |  |
| 060721__google_gemma-4-e4b__dark_mode_on__r2 | google/gemma-4-e4b | dark_mode_on | 2 | ✅ done |  | 10 | 76.5 | not retained |  |
| 060843__google_gemma-4-e4b__dark_mode_off__r0 | google/gemma-4-e4b | dark_mode_off | 0 | ✅ done |  | 9 | 68.0 | not retained |  |
| 060956__google_gemma-4-e4b__dark_mode_off__r1 | google/gemma-4-e4b | dark_mode_off | 1 | ❌ failed |  | 11 | 74.0 | not retained |  |
| 061116__google_gemma-4-e4b__dark_mode_off__r2 | google/gemma-4-e4b | dark_mode_off | 2 | ❓ fail_loop |  | 26 | 213.7 | not retained |  |
| 061455__google_gemma-4-e4b__bluetooth_toggle_on__r0 | google/gemma-4-e4b | bluetooth_toggle_on | 0 | ❌ failed |  | 15 | 85.1 | not retained |  |
| 061626__google_gemma-4-e4b__bluetooth_toggle_on__r1 | google/gemma-4-e4b | bluetooth_toggle_on | 1 | ✅ done |  | 7 | 49.6 | not retained |  |
| 061721__google_gemma-4-e4b__bluetooth_toggle_on__r2 | google/gemma-4-e4b | bluetooth_toggle_on | 2 | ✅ done |  | 3 | 21.5 | not retained |  |
| 061748__google_gemma-4-e4b__brightness_set_50__r0 | google/gemma-4-e4b | brightness_set_50 | 0 | ✅ done |  | 11 | 78.0 | not retained |  |
| 061911__google_gemma-4-e4b__brightness_set_50__r1 | google/gemma-4-e4b | brightness_set_50 | 1 | ✅ done |  | 4 | 26.0 | not retained |  |
| 061943__google_gemma-4-e4b__brightness_set_50__r2 | google/gemma-4-e4b | brightness_set_50 | 2 | ❌ failed |  | 10 | 67.0 | not retained |  |
| 062055__google_gemma-4-e4b__timer_set_5min__r0 | google/gemma-4-e4b | timer_set_5min | 0 | ✅ done |  | 10 | 61.6 | not retained |  |
| 062202__google_gemma-4-e4b__timer_set_5min__r1 | google/gemma-4-e4b | timer_set_5min | 1 | ✅ done |  | 10 | 67.1 | not retained |  |
| 062315__google_gemma-4-e4b__timer_set_5min__r2 | google/gemma-4-e4b | timer_set_5min | 2 | ✅ done |  | 10 | 64.6 | not retained |  |
| 062425__google_gemma-4-e4b__pizza_search__r0 | google/gemma-4-e4b | pizza_search | 0 | ❌ failed |  | 8 | 90.6 | not retained |  |
| 062600__google_gemma-4-e4b__pizza_search__r1 | google/gemma-4-e4b | pizza_search | 1 | ✅ done |  | 3 | 22.5 | not retained |  |
| 062629__google_gemma-4-e4b__pizza_search__r2 | google/gemma-4-e4b | pizza_search | 2 | ✅ done |  | 3 | 22.5 | not retained |  |
| 063156__qwen_qwen36-35b-a3b__dark_mode_on__r0 | qwen/qwen3.6-35b-a3b | dark_mode_on | 0 | ⏱️ timeout |  | 4 | 300.3 | not retained |  |
| 063711__qwen_qwen36-35b-a3b__dark_mode_on__r1 | qwen/qwen3.6-35b-a3b | dark_mode_on | 1 | ❌ failed |  | 0 | 180.1 | not retained |  |
| 064018__qwen_qwen36-35b-a3b__dark_mode_on__r2 | qwen/qwen3.6-35b-a3b | dark_mode_on | 2 | ⏱️ timeout |  | 4 | 300.2 | not retained |  |
| 064932__qwen_qwen36-35b-a3b__dark_mode_on__r0 | qwen/qwen3.6-35b-a3b | dark_mode_on | 0 | ⏱️ timeout |  | 6 | 300.2 | not retained |  |
| 065443__qwen_qwen36-35b-a3b__dark_mode_on__r1 | qwen/qwen3.6-35b-a3b | dark_mode_on | 1 | ✅ done |  | 4 | 197.2 | not retained |  |
| 065806__qwen_qwen36-35b-a3b__dark_mode_on__r2 | qwen/qwen3.6-35b-a3b | dark_mode_on | 2 | ❌ failed |  | 0 | 0.5 | not retained |  |
| 065812__qwen_qwen36-35b-a3b__dark_mode_off__r0 | qwen/qwen3.6-35b-a3b | dark_mode_off | 0 | ❌ failed |  | 0 | 0.5 | not retained |  |
| 065818__qwen_qwen36-35b-a3b__dark_mode_off__r1 | qwen/qwen3.6-35b-a3b | dark_mode_off | 1 | ❌ failed |  | 0 | 0.5 | not retained |  |
| 065824__qwen_qwen36-35b-a3b__dark_mode_off__r2 | qwen/qwen3.6-35b-a3b | dark_mode_off | 2 | ❌ failed |  | 0 | 0.5 | not retained |  |
| 065830__qwen_qwen36-35b-a3b__bluetooth_toggle_on__r0 | qwen/qwen3.6-35b-a3b | bluetooth_toggle_on | 0 | ❌ failed |  | 0 | 0.5 | not retained |  |
| 065836__qwen_qwen36-35b-a3b__bluetooth_toggle_on__r1 | qwen/qwen3.6-35b-a3b | bluetooth_toggle_on | 1 | ❌ failed |  | 0 | 0.5 | not retained |  |
| 065842__qwen_qwen36-35b-a3b__bluetooth_toggle_on__r2 | qwen/qwen3.6-35b-a3b | bluetooth_toggle_on | 2 | ❌ failed |  | 0 | 0.5 | not retained |  |
| 070245__qwen_qwen36-35b-a3b__dark_mode_on__r0 | qwen/qwen3.6-35b-a3b | dark_mode_on | 0 | ✅ done |  | 8 | 214.2 | not retained |  |
| 070625__qwen_qwen36-35b-a3b__dark_mode_on__r1 | qwen/qwen3.6-35b-a3b | dark_mode_on | 1 | ✅ done |  | 5 | 159.1 | not retained |  |
| 070910__qwen_qwen36-35b-a3b__dark_mode_on__r2 | qwen/qwen3.6-35b-a3b | dark_mode_on | 2 | ✅ done |  | 8 | 289.7 | not retained |  |
| 071405__qwen_qwen36-35b-a3b__dark_mode_off__r0 | qwen/qwen3.6-35b-a3b | dark_mode_off | 0 | ✅ done |  | 7 | 270.7 | not retained |  |
| 071841__qwen_qwen36-35b-a3b__dark_mode_off__r1 | qwen/qwen3.6-35b-a3b | dark_mode_off | 1 | ❌ failed |  | 5 | 370.4 | not retained |  |
| 072457__qwen_qwen36-35b-a3b__dark_mode_off__r2 | qwen/qwen3.6-35b-a3b | dark_mode_off | 2 | ❌ failed |  | 5 | 387.9 | not retained |  |
| 073131__qwen_qwen36-35b-a3b__bluetooth_toggle_on__r0 | qwen/qwen3.6-35b-a3b | bluetooth_toggle_on | 0 | ⏱️ timeout |  | 13 | 600.5 | not retained |  |
| 074142__qwen_qwen36-35b-a3b__bluetooth_toggle_on__r1 | qwen/qwen3.6-35b-a3b | bluetooth_toggle_on | 1 | ❌ failed |  | 12 | 600.0 | not retained |  |
| 075148__qwen_qwen36-35b-a3b__bluetooth_toggle_on__r2 | qwen/qwen3.6-35b-a3b | bluetooth_toggle_on | 2 | ⏱️ timeout |  | 20 | 600.1 | not retained |  |
| 080159__qwen_qwen36-35b-a3b__brightness_set_50__r0 | qwen/qwen3.6-35b-a3b | brightness_set_50 | 0 | ❌ failed |  | 2 | 180.2 | not retained |  |
| 080506__qwen_qwen36-35b-a3b__brightness_set_50__r1 | qwen/qwen3.6-35b-a3b | brightness_set_50 | 1 | ❌ failed |  | 2 | 180.2 | not retained |  |
| 080812__qwen_qwen36-35b-a3b__brightness_set_50__r2 | qwen/qwen3.6-35b-a3b | brightness_set_50 | 2 | ❌ failed |  | 9 | 412.4 | not retained |  |
| 081510__qwen_qwen36-35b-a3b__timer_set_5min__r0 | qwen/qwen3.6-35b-a3b | timer_set_5min | 0 | ❌ failed |  | 0 | 180.2 | not retained |  |
| 081817__qwen_qwen36-35b-a3b__timer_set_5min__r1 | qwen/qwen3.6-35b-a3b | timer_set_5min | 1 | ❌ failed |  | 0 | 180.2 | not retained |  |
| 083735__qwen_qwen36-35b-a3b__dark_mode_on__r0 | qwen/qwen3.6-35b-a3b | dark_mode_on | 0 | ❌ failed |  | 0 | 180.2 | not retained |  |
| 084352__qwen_qwen36-35b-a3b__dark_mode_on__r0 | qwen/qwen3.6-35b-a3b | dark_mode_on | 0 | ✅ done |  | 6 | 461.8 | not retained |  |
| 085139__qwen_qwen36-35b-a3b__dark_mode_on__r1 | qwen/qwen3.6-35b-a3b | dark_mode_on | 1 | ⏱️ timeout |  | 8 | 600.5 | not retained |  |
| 095106__google_gemma-4-e4b__dark_mode_on__r0 | google/gemma-4-e4b | dark_mode_on | 0 | ❓ fail_loop |  | 26 | 375.9 | not retained |  |
| 095727__google_gemma-4-e4b__dark_mode_on__r1 | google/gemma-4-e4b | dark_mode_on | 1 | ✅ done |  | 10 | 117.1 | not retained |  |
| 095929__google_gemma-4-e4b__dark_mode_on__r2 | google/gemma-4-e4b | dark_mode_on | 2 | ✅ done |  | 4 | 43.2 | not retained |  |
| 100020__google_gemma-4-e4b__dark_mode_off__r0 | google/gemma-4-e4b | dark_mode_off | 0 | ❌ failed |  | 11 | 150.6 | not retained |  |
| 100256__google_gemma-4-e4b__dark_mode_off__r1 | google/gemma-4-e4b | dark_mode_off | 1 | ❌ failed |  | 13 | 160.6 | not retained |  |
| 100542__google_gemma-4-e4b__dark_mode_off__r2 | google/gemma-4-e4b | dark_mode_off | 2 | ❌ failed |  | 17 | 224.2 | not retained |  |
| 100932__google_gemma-4-e4b__bluetooth_toggle_on__r0 | google/gemma-4-e4b | bluetooth_toggle_on | 0 | ✅ done |  | 17 | 127.6 | not retained |  |
| 101145__google_gemma-4-e4b__bluetooth_toggle_on__r1 | google/gemma-4-e4b | bluetooth_toggle_on | 1 | ✅ done |  | 16 | 121.1 | not retained |  |
| 101351__google_gemma-4-e4b__bluetooth_toggle_on__r2 | google/gemma-4-e4b | bluetooth_toggle_on | 2 | ✅ done |  | 17 | 134.1 | not retained |  |
| 101610__google_gemma-4-e4b__brightness_set_50__r0 | google/gemma-4-e4b | brightness_set_50 | 0 | ❌ failed |  | 17 | 140.7 | not retained |  |
| 101836__google_gemma-4-e4b__brightness_set_50__r1 | google/gemma-4-e4b | brightness_set_50 | 1 | ✅ done |  | 25 | 172.6 | not retained |  |
| 102134__google_gemma-4-e4b__brightness_set_50__r2 | google/gemma-4-e4b | brightness_set_50 | 2 | ❓ fail_loop |  | 26 | 210.2 | not retained |  |
| 102509__google_gemma-4-e4b__timer_set_5min__r0 | google/gemma-4-e4b | timer_set_5min | 0 | ✅ done |  | 12 | 88.6 | not retained |  |
| 102643__google_gemma-4-e4b__timer_set_5min__r1 | google/gemma-4-e4b | timer_set_5min | 1 | ✅ done |  | 9 | 125.6 | not retained |  |
| 102854__google_gemma-4-e4b__timer_set_5min__r2 | google/gemma-4-e4b | timer_set_5min | 2 | ✅ done |  | 11 | 181.1 | not retained |  |
| 103200__google_gemma-4-e4b__pizza_search__r0 | google/gemma-4-e4b | pizza_search | 0 | ✅ done |  | 3 | 47.5 | not retained |  |
| 103253__google_gemma-4-e4b__pizza_search__r1 | google/gemma-4-e4b | pizza_search | 1 | ✅ done |  | 5 | 31.5 | not retained |  |
| 103330__google_gemma-4-e4b__pizza_search__r2 | google/gemma-4-e4b | pizza_search | 2 | ✅ done |  | 8 | 49.5 | not retained |  |
| 105416__google_gemma-4-e4b__dark_mode_on__r0 | google/gemma-4-e4b | dark_mode_on | 0 | ✅ done |  | 7 | 74.6 | not retained |  |
| 105536__google_gemma-4-e4b__dark_mode_on__r1 | google/gemma-4-e4b | dark_mode_on | 1 | ❌ failed |  | 20 | 156.1 | not retained |  |
| 105817__google_gemma-4-e4b__dark_mode_on__r2 | google/gemma-4-e4b | dark_mode_on | 2 | ❓ fail_loop |  | 26 | 207.7 | not retained |  |
| 110151__google_gemma-4-e4b__dark_mode_off__r0 | google/gemma-4-e4b | dark_mode_off | 0 | ❌ failed |  | 6 | 40.5 | not retained |  |
| 110236__google_gemma-4-e4b__dark_mode_off__r1 | google/gemma-4-e4b | dark_mode_off | 1 | ✅ done |  | 21 | 180.6 | not retained |  |
| 110543__google_gemma-4-e4b__dark_mode_off__r2 | google/gemma-4-e4b | dark_mode_off | 2 | ❓ fail_loop |  | 26 | 218.7 | not retained |  |
| 110927__google_gemma-4-e4b__bluetooth_toggle_on__r0 | google/gemma-4-e4b | bluetooth_toggle_on | 0 | ❌ failed |  | 16 | 73.1 | not retained |  |
| 111045__google_gemma-4-e4b__bluetooth_toggle_on__r1 | google/gemma-4-e4b | bluetooth_toggle_on | 1 | ✅ done |  | 4 | 28.0 | not retained |  |
| 111118__google_gemma-4-e4b__bluetooth_toggle_on__r2 | google/gemma-4-e4b | bluetooth_toggle_on | 2 | ✅ done |  | 3 | 30.5 | not retained |  |
| 111154__google_gemma-4-e4b__brightness_set_50__r0 | google/gemma-4-e4b | brightness_set_50 | 0 | ❓ fail_loop |  | 27 | 174.6 | not retained |  |
| 111455__google_gemma-4-e4b__brightness_set_50__r1 | google/gemma-4-e4b | brightness_set_50 | 1 | ❌ failed |  | 25 | 195.8 | not retained |  |
| 111816__google_gemma-4-e4b__brightness_set_50__r2 | google/gemma-4-e4b | brightness_set_50 | 2 | ✅ done |  | 22 | 191.1 | not retained |  |
| 112132__google_gemma-4-e4b__timer_set_5min__r0 | google/gemma-4-e4b | timer_set_5min | 0 | ✅ done |  | 18 | 134.1 | not retained |  |
| 112351__google_gemma-4-e4b__timer_set_5min__r1 | google/gemma-4-e4b | timer_set_5min | 1 | ✅ done |  | 16 | 108.6 | not retained |  |
| 112545__google_gemma-4-e4b__timer_set_5min__r2 | google/gemma-4-e4b | timer_set_5min | 2 | ✅ done |  | 13 | 137.6 | not retained |  |
| 112808__google_gemma-4-e4b__pizza_search__r0 | google/gemma-4-e4b | pizza_search | 0 | ✅ done |  | 9 | 64.6 | not retained |  |
| 112918__google_gemma-4-e4b__pizza_search__r1 | google/gemma-4-e4b | pizza_search | 1 | ❌ failed |  | 12 | 100.1 | not retained |  |
| 113104__google_gemma-4-e4b__pizza_search__r2 | google/gemma-4-e4b | pizza_search | 2 | ❌ failed |  | 6 | 37.6 | not retained |  |

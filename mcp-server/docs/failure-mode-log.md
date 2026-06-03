# Failure-Mode-Sampling - Matrix Run 2026-05-13

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
| 110309__qwen_qwen36-35b-a3b__dark_mode_on__r0        | qwen/qwen3.6-35b-a3b | dark_mode_on        | 0   | ✅ done   |             | 10    | 272.8     | ![s](screenshots/trials/110309__qwen_qwen36-35b-a3b__dark_mode_on__r0.png)        |       |
| 110749__qwen_qwen36-35b-a3b__dark_mode_on__r1        | qwen/qwen3.6-35b-a3b | dark_mode_on        | 1   | ✅ done   |             | 10    | 253.4     | ![s](screenshots/trials/110749__qwen_qwen36-35b-a3b__dark_mode_on__r1.png)        |       |
| 111207__qwen_qwen36-35b-a3b__dark_mode_off__r0       | qwen/qwen3.6-35b-a3b | dark_mode_off       | 0   | ✅ done   |             | 10    | 249.7     | ![s](screenshots/trials/111207__qwen_qwen36-35b-a3b__dark_mode_off__r0.png)       |       |
| 111622__qwen_qwen36-35b-a3b__dark_mode_off__r1       | qwen/qwen3.6-35b-a3b | dark_mode_off       | 1   | ✅ done   |             | 10    | 238.7     | ![s](screenshots/trials/111622__qwen_qwen36-35b-a3b__dark_mode_off__r1.png)       |       |
| 113013__qwen_qwen36-35b-a3b__bluetooth_toggle_on__r0 | qwen/qwen3.6-35b-a3b | bluetooth_toggle_on | 0   | ❌ failed |             | 4     | 300.2     | ![s](screenshots/trials/113013__qwen_qwen36-35b-a3b__bluetooth_toggle_on__r0.png) |       |
| 113518__qwen_qwen36-35b-a3b__bluetooth_toggle_on__r1 | qwen/qwen3.6-35b-a3b | bluetooth_toggle_on | 1   | ❌ failed |             | 9     | 300.3     | ![s](screenshots/trials/113518__qwen_qwen36-35b-a3b__bluetooth_toggle_on__r1.png) |       |
| 114023__qwen_qwen36-35b-a3b__brightness_set_50__r0   | qwen/qwen3.6-35b-a3b | brightness_set_50   | 0   | ❌ failed |             | 7     | 300.3     | ![s](screenshots/trials/114023__qwen_qwen36-35b-a3b__brightness_set_50__r0.png)   |       |
| 114528__qwen_qwen36-35b-a3b__brightness_set_50__r1   | qwen/qwen3.6-35b-a3b | brightness_set_50   | 1   | ❌ failed |             | 7     | 300.3     | ![s](screenshots/trials/114528__qwen_qwen36-35b-a3b__brightness_set_50__r1.png)   |       |
| 115033__qwen_qwen36-35b-a3b__timer_set_5min__r0      | qwen/qwen3.6-35b-a3b | timer_set_5min      | 0   | ❌ failed |             | 6     | 300.3     | ![s](screenshots/trials/115033__qwen_qwen36-35b-a3b__timer_set_5min__r0.png)      |       |
| 115539__qwen_qwen36-35b-a3b__timer_set_5min__r1      | qwen/qwen3.6-35b-a3b | timer_set_5min      | 1   | ❌ failed |             | 8     | 300.4     | ![s](screenshots/trials/115539__qwen_qwen36-35b-a3b__timer_set_5min__r1.png)      |       |
| 120044__qwen_qwen36-35b-a3b__pizza_search__r0        | qwen/qwen3.6-35b-a3b | pizza_search        | 0   | ✅ done   |             | 7     | 300.2     | ![s](screenshots/trials/120044__qwen_qwen36-35b-a3b__pizza_search__r0.png)        |       |
| 120549__qwen_qwen36-35b-a3b__pizza_search__r1        | qwen/qwen3.6-35b-a3b | pizza_search        | 1   | ✅ done   |             | 8     | 187.6     | ![s](screenshots/trials/120549__qwen_qwen36-35b-a3b__pizza_search__r1.png)        |       |
| 091321__google_gemma-4-e4b__dark_mode_on__r0 | google/gemma-4-e4b | dark_mode_on | 0 | ❌ failed |  | 20 | 161.1 | ![s](screenshots/trials/091321__google_gemma-4-e4b__dark_mode_on__r0.png) |  |
| 091611__google_gemma-4-e4b__dark_mode_on__r1 | google/gemma-4-e4b | dark_mode_on | 1 | ❌ failed |  | 13 | 111.6 | ![s](screenshots/trials/091611__google_gemma-4-e4b__dark_mode_on__r1.png) |  |
| 091811__google_gemma-4-e4b__dark_mode_on__r2 | google/gemma-4-e4b | dark_mode_on | 2 | ❌ failed |  | 9 | 64.0 | ![s](screenshots/trials/091811__google_gemma-4-e4b__dark_mode_on__r2.png) |  |
| 091923__google_gemma-4-e4b__dark_mode_off__r0 | google/gemma-4-e4b | dark_mode_off | 0 | ❌ failed |  | 25 | 225.7 | ![s](screenshots/trials/091923__google_gemma-4-e4b__dark_mode_off__r0.png) |  |
| 092317__google_gemma-4-e4b__dark_mode_off__r1 | google/gemma-4-e4b | dark_mode_off | 1 | ❌ failed |  | 16 | 123.6 | ![s](screenshots/trials/092317__google_gemma-4-e4b__dark_mode_off__r1.png) |  |
| 092528__google_gemma-4-e4b__dark_mode_off__r2 | google/gemma-4-e4b | dark_mode_off | 2 | ❌ failed |  | 11 | 78.5 | ![s](screenshots/trials/092528__google_gemma-4-e4b__dark_mode_off__r2.png) |  |
| 092655__google_gemma-4-e4b__bluetooth_toggle_on__r0 | google/gemma-4-e4b | bluetooth_toggle_on | 0 | ✅ done |  | 12 | 93.1 | ![s](screenshots/trials/092655__google_gemma-4-e4b__bluetooth_toggle_on__r0.png) |  |
| 092836__google_gemma-4-e4b__bluetooth_toggle_on__r1 | google/gemma-4-e4b | bluetooth_toggle_on | 1 | ✅ done |  | 12 | 80.6 | ![s](screenshots/trials/092836__google_gemma-4-e4b__bluetooth_toggle_on__r1.png) |  |
| 093005__google_gemma-4-e4b__bluetooth_toggle_on__r2 | google/gemma-4-e4b | bluetooth_toggle_on | 2 | ✅ done |  | 13 | 88.1 | ![s](screenshots/trials/093005__google_gemma-4-e4b__bluetooth_toggle_on__r2.png) |  |
| 093141__google_gemma-4-e4b__brightness_set_50__r0 | google/gemma-4-e4b | brightness_set_50 | 0 | ✅ done |  | 11 | 81.1 | ![s](screenshots/trials/093141__google_gemma-4-e4b__brightness_set_50__r0.png) |  |
| 093310__google_gemma-4-e4b__brightness_set_50__r1 | google/gemma-4-e4b | brightness_set_50 | 1 | ✅ done |  | 9 | 61.5 | ![s](screenshots/trials/093310__google_gemma-4-e4b__brightness_set_50__r1.png) |  |
| 093420__google_gemma-4-e4b__brightness_set_50__r2 | google/gemma-4-e4b | brightness_set_50 | 2 | ❌ failed |  | 14 | 104.6 | ![s](screenshots/trials/093420__google_gemma-4-e4b__brightness_set_50__r2.png) |  |
| 093613__google_gemma-4-e4b__timer_set_5min__r0 | google/gemma-4-e4b | timer_set_5min | 0 | ✅ done |  | 11 | 74.5 | ![s](screenshots/trials/093613__google_gemma-4-e4b__timer_set_5min__r0.png) |  |
| 093735__google_gemma-4-e4b__timer_set_5min__r1 | google/gemma-4-e4b | timer_set_5min | 1 | ✅ done |  | 9 | 64.5 | ![s](screenshots/trials/093735__google_gemma-4-e4b__timer_set_5min__r1.png) |  |
| 093847__google_gemma-4-e4b__timer_set_5min__r2 | google/gemma-4-e4b | timer_set_5min | 2 | ✅ done |  | 10 | 69.0 | ![s](screenshots/trials/093847__google_gemma-4-e4b__timer_set_5min__r2.png) |  |
| 094004__google_gemma-4-e4b__pizza_search__r0 | google/gemma-4-e4b | pizza_search | 0 | ✅ done |  | 7 | 62.0 | ![s](screenshots/trials/094004__google_gemma-4-e4b__pizza_search__r0.png) |  |
| 094113__google_gemma-4-e4b__pizza_search__r1 | google/gemma-4-e4b | pizza_search | 1 | ✅ done |  | 2 | 17.0 | ![s](screenshots/trials/094113__google_gemma-4-e4b__pizza_search__r1.png) |  |
| 094139__google_gemma-4-e4b__pizza_search__r2 | google/gemma-4-e4b | pizza_search | 2 | ✅ done |  | 2 | 24.5 | ![s](screenshots/trials/094139__google_gemma-4-e4b__pizza_search__r2.png) |  |
| 130208__google_gemma-4-e4b__dark_mode_on__r0 | google/gemma-4-e4b | dark_mode_on | 0 | ❌ failed |  | 15 | 135.1 | ![s](screenshots/trials/130208__google_gemma-4-e4b__dark_mode_on__r0.png) |  |
| 130431__google_gemma-4-e4b__dark_mode_on__r1 | google/gemma-4-e4b | dark_mode_on | 1 | ❌ failed |  | 21 | 211.6 |  |  |
| 130819__google_gemma-4-e4b__dark_mode_on__r2 | google/gemma-4-e4b | dark_mode_on | 2 | ✅ done |  | 11 | 106.6 | ![s](screenshots/trials/130819__google_gemma-4-e4b__dark_mode_on__r2.png) |  |
| 131014__google_gemma-4-e4b__dark_mode_off__r0 | google/gemma-4-e4b | dark_mode_off | 0 | ❌ failed |  | 10 | 76.5 | ![s](screenshots/trials/131014__google_gemma-4-e4b__dark_mode_off__r0.png) |  |
| 131138__google_gemma-4-e4b__dark_mode_off__r1 | google/gemma-4-e4b | dark_mode_off | 1 | ❌ failed |  | 20 | 189.6 | ![s](screenshots/trials/131138__google_gemma-4-e4b__dark_mode_off__r1.png) |  |
| 131455__google_gemma-4-e4b__dark_mode_off__r2 | google/gemma-4-e4b | dark_mode_off | 2 | ❌ failed |  | 11 | 95.1 | ![s](screenshots/trials/131455__google_gemma-4-e4b__dark_mode_off__r2.png) |  |
| 131638__google_gemma-4-e4b__bluetooth_toggle_on__r0 | google/gemma-4-e4b | bluetooth_toggle_on | 0 | ✅ done |  | 10 | 98.1 |  |  |
| 131833__google_gemma-4-e4b__bluetooth_toggle_on__r1 | google/gemma-4-e4b | bluetooth_toggle_on | 1 | ✅ done |  | 10 | 92.6 |  |  |
| 132022__google_gemma-4-e4b__bluetooth_toggle_on__r2 | google/gemma-4-e4b | bluetooth_toggle_on | 2 | ✅ done |  | 10 | 98.1 |  |  |
| 132216__google_gemma-4-e4b__brightness_set_50__r0 | google/gemma-4-e4b | brightness_set_50 | 0 | ❌ failed |  | 16 | 147.1 | ![s](screenshots/trials/132216__google_gemma-4-e4b__brightness_set_50__r0.png) |  |
| 132450__google_gemma-4-e4b__brightness_set_50__r1 | google/gemma-4-e4b | brightness_set_50 | 1 | ❓ fail_loop |  | 26 | 237.7 | ![s](screenshots/trials/132450__google_gemma-4-e4b__brightness_set_50__r1.png) |  |
| 132856__google_gemma-4-e4b__brightness_set_50__r2 | google/gemma-4-e4b | brightness_set_50 | 2 | ✅ done |  | 12 | 107.6 |  |  |
| 133100__google_gemma-4-e4b__timer_set_5min__r0 | google/gemma-4-e4b | timer_set_5min | 0 | ✅ done |  | 12 | 94.1 | ![s](screenshots/trials/133100__google_gemma-4-e4b__timer_set_5min__r0.png) |  |
| 133241__google_gemma-4-e4b__timer_set_5min__r1 | google/gemma-4-e4b | timer_set_5min | 1 | ✅ done |  | 10 | 81.6 | ![s](screenshots/trials/133241__google_gemma-4-e4b__timer_set_5min__r1.png) |  |
| 133410__google_gemma-4-e4b__timer_set_5min__r2 | google/gemma-4-e4b | timer_set_5min | 2 | ✅ done |  | 13 | 103.1 | ![s](screenshots/trials/133410__google_gemma-4-e4b__timer_set_5min__r2.png) |  |
| 133601__google_gemma-4-e4b__pizza_search__r0 | google/gemma-4-e4b | pizza_search | 0 | ✅ done |  | 2 | 29.5 |  |  |
| 133647__google_gemma-4-e4b__pizza_search__r1 | google/gemma-4-e4b | pizza_search | 1 | ✅ done |  | 8 | 95.6 | ![s](screenshots/trials/133647__google_gemma-4-e4b__pizza_search__r1.png) |  |
| 133839__google_gemma-4-e4b__pizza_search__r2 | google/gemma-4-e4b | pizza_search | 2 | ✅ done |  | 2 | 19.5 | ![s](screenshots/trials/133839__google_gemma-4-e4b__pizza_search__r2.png) |  |
| 060101__google_gemma-4-e4b__dark_mode_on__r0 | google/gemma-4-e4b | dark_mode_on | 0 | ❌ failed |  | 0 | 0.5 | ![s](screenshots/trials/060101__google_gemma-4-e4b__dark_mode_on__r0.png) |  |
| 060108__google_gemma-4-e4b__dark_mode_on__r1 | google/gemma-4-e4b | dark_mode_on | 1 | ❌ failed |  | 0 | 0.5 | ![s](screenshots/trials/060108__google_gemma-4-e4b__dark_mode_on__r1.png) |  |
| 060114__google_gemma-4-e4b__dark_mode_on__r2 | google/gemma-4-e4b | dark_mode_on | 2 | ❌ failed |  | 0 | 0.5 | ![s](screenshots/trials/060114__google_gemma-4-e4b__dark_mode_on__r2.png) |  |
| 060120__google_gemma-4-e4b__dark_mode_off__r0 | google/gemma-4-e4b | dark_mode_off | 0 | ❌ failed |  | 0 | 0.5 | ![s](screenshots/trials/060120__google_gemma-4-e4b__dark_mode_off__r0.png) |  |
| 060126__google_gemma-4-e4b__dark_mode_off__r1 | google/gemma-4-e4b | dark_mode_off | 1 | ❌ failed |  | 0 | 0.5 | ![s](screenshots/trials/060126__google_gemma-4-e4b__dark_mode_off__r1.png) |  |
| 060132__google_gemma-4-e4b__dark_mode_off__r2 | google/gemma-4-e4b | dark_mode_off | 2 | ❌ failed |  | 0 | 0.5 | ![s](screenshots/trials/060132__google_gemma-4-e4b__dark_mode_off__r2.png) |  |
| 060137__google_gemma-4-e4b__bluetooth_toggle_on__r0 | google/gemma-4-e4b | bluetooth_toggle_on | 0 | ❌ failed |  | 0 | 0.5 | ![s](screenshots/trials/060137__google_gemma-4-e4b__bluetooth_toggle_on__r0.png) |  |
| 060143__google_gemma-4-e4b__bluetooth_toggle_on__r1 | google/gemma-4-e4b | bluetooth_toggle_on | 1 | ❌ failed |  | 0 | 0.5 | ![s](screenshots/trials/060143__google_gemma-4-e4b__bluetooth_toggle_on__r1.png) |  |
| 060238__google_gemma-4-e4b__dark_mode_on__r0 | google/gemma-4-e4b | dark_mode_on | 0 | ❓ fail_loop |  | 26 | 207.2 | ![s](screenshots/trials/060238__google_gemma-4-e4b__dark_mode_on__r0.png) |  |
| 060610__google_gemma-4-e4b__dark_mode_on__r1 | google/gemma-4-e4b | dark_mode_on | 1 | ❌ failed |  | 10 | 66.0 | ![s](screenshots/trials/060610__google_gemma-4-e4b__dark_mode_on__r1.png) |  |
| 060721__google_gemma-4-e4b__dark_mode_on__r2 | google/gemma-4-e4b | dark_mode_on | 2 | ✅ done |  | 10 | 76.5 | ![s](screenshots/trials/060721__google_gemma-4-e4b__dark_mode_on__r2.png) |  |
| 060843__google_gemma-4-e4b__dark_mode_off__r0 | google/gemma-4-e4b | dark_mode_off | 0 | ✅ done |  | 9 | 68.0 | ![s](screenshots/trials/060843__google_gemma-4-e4b__dark_mode_off__r0.png) |  |
| 060956__google_gemma-4-e4b__dark_mode_off__r1 | google/gemma-4-e4b | dark_mode_off | 1 | ❌ failed |  | 11 | 74.0 | ![s](screenshots/trials/060956__google_gemma-4-e4b__dark_mode_off__r1.png) |  |
| 061116__google_gemma-4-e4b__dark_mode_off__r2 | google/gemma-4-e4b | dark_mode_off | 2 | ❓ fail_loop |  | 26 | 213.7 | ![s](screenshots/trials/061116__google_gemma-4-e4b__dark_mode_off__r2.png) |  |
| 061455__google_gemma-4-e4b__bluetooth_toggle_on__r0 | google/gemma-4-e4b | bluetooth_toggle_on | 0 | ❌ failed |  | 15 | 85.1 | ![s](screenshots/trials/061455__google_gemma-4-e4b__bluetooth_toggle_on__r0.png) |  |
| 061626__google_gemma-4-e4b__bluetooth_toggle_on__r1 | google/gemma-4-e4b | bluetooth_toggle_on | 1 | ✅ done |  | 7 | 49.6 | ![s](screenshots/trials/061626__google_gemma-4-e4b__bluetooth_toggle_on__r1.png) |  |
| 061721__google_gemma-4-e4b__bluetooth_toggle_on__r2 | google/gemma-4-e4b | bluetooth_toggle_on | 2 | ✅ done |  | 3 | 21.5 | ![s](screenshots/trials/061721__google_gemma-4-e4b__bluetooth_toggle_on__r2.png) |  |
| 061748__google_gemma-4-e4b__brightness_set_50__r0 | google/gemma-4-e4b | brightness_set_50 | 0 | ✅ done |  | 11 | 78.0 | ![s](screenshots/trials/061748__google_gemma-4-e4b__brightness_set_50__r0.png) |  |
| 061911__google_gemma-4-e4b__brightness_set_50__r1 | google/gemma-4-e4b | brightness_set_50 | 1 | ✅ done |  | 4 | 26.0 | ![s](screenshots/trials/061911__google_gemma-4-e4b__brightness_set_50__r1.png) |  |
| 061943__google_gemma-4-e4b__brightness_set_50__r2 | google/gemma-4-e4b | brightness_set_50 | 2 | ❌ failed |  | 10 | 67.0 | ![s](screenshots/trials/061943__google_gemma-4-e4b__brightness_set_50__r2.png) |  |
| 062055__google_gemma-4-e4b__timer_set_5min__r0 | google/gemma-4-e4b | timer_set_5min | 0 | ✅ done |  | 10 | 61.6 | ![s](screenshots/trials/062055__google_gemma-4-e4b__timer_set_5min__r0.png) |  |
| 062202__google_gemma-4-e4b__timer_set_5min__r1 | google/gemma-4-e4b | timer_set_5min | 1 | ✅ done |  | 10 | 67.1 | ![s](screenshots/trials/062202__google_gemma-4-e4b__timer_set_5min__r1.png) |  |
| 062315__google_gemma-4-e4b__timer_set_5min__r2 | google/gemma-4-e4b | timer_set_5min | 2 | ✅ done |  | 10 | 64.6 | ![s](screenshots/trials/062315__google_gemma-4-e4b__timer_set_5min__r2.png) |  |
| 062425__google_gemma-4-e4b__pizza_search__r0 | google/gemma-4-e4b | pizza_search | 0 | ❌ failed |  | 8 | 90.6 | ![s](screenshots/trials/062425__google_gemma-4-e4b__pizza_search__r0.png) |  |
| 062600__google_gemma-4-e4b__pizza_search__r1 | google/gemma-4-e4b | pizza_search | 1 | ✅ done |  | 3 | 22.5 | ![s](screenshots/trials/062600__google_gemma-4-e4b__pizza_search__r1.png) |  |
| 062629__google_gemma-4-e4b__pizza_search__r2 | google/gemma-4-e4b | pizza_search | 2 | ✅ done |  | 3 | 22.5 | ![s](screenshots/trials/062629__google_gemma-4-e4b__pizza_search__r2.png) |  |
| 063156__qwen_qwen36-35b-a3b__dark_mode_on__r0 | qwen/qwen3.6-35b-a3b | dark_mode_on | 0 | ⏱️ timeout |  | 4 | 300.3 | ![s](screenshots/trials/063156__qwen_qwen36-35b-a3b__dark_mode_on__r0.png) |  |
| 063711__qwen_qwen36-35b-a3b__dark_mode_on__r1 | qwen/qwen3.6-35b-a3b | dark_mode_on | 1 | ❌ failed |  | 0 | 180.1 | ![s](screenshots/trials/063711__qwen_qwen36-35b-a3b__dark_mode_on__r1.png) |  |
| 064018__qwen_qwen36-35b-a3b__dark_mode_on__r2 | qwen/qwen3.6-35b-a3b | dark_mode_on | 2 | ⏱️ timeout |  | 4 | 300.2 | ![s](screenshots/trials/064018__qwen_qwen36-35b-a3b__dark_mode_on__r2.png) |  |
| 064932__qwen_qwen36-35b-a3b__dark_mode_on__r0 | qwen/qwen3.6-35b-a3b | dark_mode_on | 0 | ⏱️ timeout |  | 6 | 300.2 | ![s](screenshots/trials/064932__qwen_qwen36-35b-a3b__dark_mode_on__r0.png) |  |
| 065443__qwen_qwen36-35b-a3b__dark_mode_on__r1 | qwen/qwen3.6-35b-a3b | dark_mode_on | 1 | ✅ done |  | 4 | 197.2 | ![s](screenshots/trials/065443__qwen_qwen36-35b-a3b__dark_mode_on__r1.png) |  |
| 065806__qwen_qwen36-35b-a3b__dark_mode_on__r2 | qwen/qwen3.6-35b-a3b | dark_mode_on | 2 | ❌ failed |  | 0 | 0.5 | ![s](screenshots/trials/065806__qwen_qwen36-35b-a3b__dark_mode_on__r2.png) |  |
| 065812__qwen_qwen36-35b-a3b__dark_mode_off__r0 | qwen/qwen3.6-35b-a3b | dark_mode_off | 0 | ❌ failed |  | 0 | 0.5 | ![s](screenshots/trials/065812__qwen_qwen36-35b-a3b__dark_mode_off__r0.png) |  |
| 065818__qwen_qwen36-35b-a3b__dark_mode_off__r1 | qwen/qwen3.6-35b-a3b | dark_mode_off | 1 | ❌ failed |  | 0 | 0.5 | ![s](screenshots/trials/065818__qwen_qwen36-35b-a3b__dark_mode_off__r1.png) |  |
| 065824__qwen_qwen36-35b-a3b__dark_mode_off__r2 | qwen/qwen3.6-35b-a3b | dark_mode_off | 2 | ❌ failed |  | 0 | 0.5 | ![s](screenshots/trials/065824__qwen_qwen36-35b-a3b__dark_mode_off__r2.png) |  |
| 065830__qwen_qwen36-35b-a3b__bluetooth_toggle_on__r0 | qwen/qwen3.6-35b-a3b | bluetooth_toggle_on | 0 | ❌ failed |  | 0 | 0.5 | ![s](screenshots/trials/065830__qwen_qwen36-35b-a3b__bluetooth_toggle_on__r0.png) |  |
| 065836__qwen_qwen36-35b-a3b__bluetooth_toggle_on__r1 | qwen/qwen3.6-35b-a3b | bluetooth_toggle_on | 1 | ❌ failed |  | 0 | 0.5 | ![s](screenshots/trials/065836__qwen_qwen36-35b-a3b__bluetooth_toggle_on__r1.png) |  |
| 065842__qwen_qwen36-35b-a3b__bluetooth_toggle_on__r2 | qwen/qwen3.6-35b-a3b | bluetooth_toggle_on | 2 | ❌ failed |  | 0 | 0.5 | ![s](screenshots/trials/065842__qwen_qwen36-35b-a3b__bluetooth_toggle_on__r2.png) |  |
| 070245__qwen_qwen36-35b-a3b__dark_mode_on__r0 | qwen/qwen3.6-35b-a3b | dark_mode_on | 0 | ✅ done |  | 8 | 214.2 | ![s](screenshots/trials/070245__qwen_qwen36-35b-a3b__dark_mode_on__r0.png) |  |
| 070625__qwen_qwen36-35b-a3b__dark_mode_on__r1 | qwen/qwen3.6-35b-a3b | dark_mode_on | 1 | ✅ done |  | 5 | 159.1 | ![s](screenshots/trials/070625__qwen_qwen36-35b-a3b__dark_mode_on__r1.png) |  |
| 070910__qwen_qwen36-35b-a3b__dark_mode_on__r2 | qwen/qwen3.6-35b-a3b | dark_mode_on | 2 | ✅ done |  | 8 | 289.7 | ![s](screenshots/trials/070910__qwen_qwen36-35b-a3b__dark_mode_on__r2.png) |  |
| 071405__qwen_qwen36-35b-a3b__dark_mode_off__r0 | qwen/qwen3.6-35b-a3b | dark_mode_off | 0 | ✅ done |  | 7 | 270.7 | ![s](screenshots/trials/071405__qwen_qwen36-35b-a3b__dark_mode_off__r0.png) |  |
| 071841__qwen_qwen36-35b-a3b__dark_mode_off__r1 | qwen/qwen3.6-35b-a3b | dark_mode_off | 1 | ❌ failed |  | 5 | 370.4 | ![s](screenshots/trials/071841__qwen_qwen36-35b-a3b__dark_mode_off__r1.png) |  |
| 072457__qwen_qwen36-35b-a3b__dark_mode_off__r2 | qwen/qwen3.6-35b-a3b | dark_mode_off | 2 | ❌ failed |  | 5 | 387.9 | ![s](screenshots/trials/072457__qwen_qwen36-35b-a3b__dark_mode_off__r2.png) |  |
| 073131__qwen_qwen36-35b-a3b__bluetooth_toggle_on__r0 | qwen/qwen3.6-35b-a3b | bluetooth_toggle_on | 0 | ⏱️ timeout |  | 13 | 600.5 | ![s](screenshots/trials/073131__qwen_qwen36-35b-a3b__bluetooth_toggle_on__r0.png) |  |
| 074142__qwen_qwen36-35b-a3b__bluetooth_toggle_on__r1 | qwen/qwen3.6-35b-a3b | bluetooth_toggle_on | 1 | ❌ failed |  | 12 | 600.0 | ![s](screenshots/trials/074142__qwen_qwen36-35b-a3b__bluetooth_toggle_on__r1.png) |  |
| 075148__qwen_qwen36-35b-a3b__bluetooth_toggle_on__r2 | qwen/qwen3.6-35b-a3b | bluetooth_toggle_on | 2 | ⏱️ timeout |  | 20 | 600.1 | ![s](screenshots/trials/075148__qwen_qwen36-35b-a3b__bluetooth_toggle_on__r2.png) |  |
| 080159__qwen_qwen36-35b-a3b__brightness_set_50__r0 | qwen/qwen3.6-35b-a3b | brightness_set_50 | 0 | ❌ failed |  | 2 | 180.2 | ![s](screenshots/trials/080159__qwen_qwen36-35b-a3b__brightness_set_50__r0.png) |  |
| 080506__qwen_qwen36-35b-a3b__brightness_set_50__r1 | qwen/qwen3.6-35b-a3b | brightness_set_50 | 1 | ❌ failed |  | 2 | 180.2 | ![s](screenshots/trials/080506__qwen_qwen36-35b-a3b__brightness_set_50__r1.png) |  |
| 080812__qwen_qwen36-35b-a3b__brightness_set_50__r2 | qwen/qwen3.6-35b-a3b | brightness_set_50 | 2 | ❌ failed |  | 9 | 412.4 | ![s](screenshots/trials/080812__qwen_qwen36-35b-a3b__brightness_set_50__r2.png) |  |
| 081510__qwen_qwen36-35b-a3b__timer_set_5min__r0 | qwen/qwen3.6-35b-a3b | timer_set_5min | 0 | ❌ failed |  | 0 | 180.2 | ![s](screenshots/trials/081510__qwen_qwen36-35b-a3b__timer_set_5min__r0.png) |  |
| 081817__qwen_qwen36-35b-a3b__timer_set_5min__r1 | qwen/qwen3.6-35b-a3b | timer_set_5min | 1 | ❌ failed |  | 0 | 180.2 | ![s](screenshots/trials/081817__qwen_qwen36-35b-a3b__timer_set_5min__r1.png) |  |
| 083735__qwen_qwen36-35b-a3b__dark_mode_on__r0 | qwen/qwen3.6-35b-a3b | dark_mode_on | 0 | ❌ failed |  | 0 | 180.2 | ![s](screenshots/trials/083735__qwen_qwen36-35b-a3b__dark_mode_on__r0.png) |  |
| 084352__qwen_qwen36-35b-a3b__dark_mode_on__r0 | qwen/qwen3.6-35b-a3b | dark_mode_on | 0 | ✅ done |  | 6 | 461.8 | ![s](screenshots/trials/084352__qwen_qwen36-35b-a3b__dark_mode_on__r0.png) |  |
| 085139__qwen_qwen36-35b-a3b__dark_mode_on__r1 | qwen/qwen3.6-35b-a3b | dark_mode_on | 1 | ⏱️ timeout |  | 8 | 600.5 | ![s](screenshots/trials/085139__qwen_qwen36-35b-a3b__dark_mode_on__r1.png) |  |
| 095106__google_gemma-4-e4b__dark_mode_on__r0 | google/gemma-4-e4b | dark_mode_on | 0 | ❓ fail_loop |  | 26 | 375.9 | ![s](screenshots/trials/095106__google_gemma-4-e4b__dark_mode_on__r0.png) |  |
| 095727__google_gemma-4-e4b__dark_mode_on__r1 | google/gemma-4-e4b | dark_mode_on | 1 | ✅ done |  | 10 | 117.1 | ![s](screenshots/trials/095727__google_gemma-4-e4b__dark_mode_on__r1.png) |  |
| 095929__google_gemma-4-e4b__dark_mode_on__r2 | google/gemma-4-e4b | dark_mode_on | 2 | ✅ done |  | 4 | 43.2 | ![s](screenshots/trials/095929__google_gemma-4-e4b__dark_mode_on__r2.png) |  |
| 100020__google_gemma-4-e4b__dark_mode_off__r0 | google/gemma-4-e4b | dark_mode_off | 0 | ❌ failed |  | 11 | 150.6 | ![s](screenshots/trials/100020__google_gemma-4-e4b__dark_mode_off__r0.png) |  |
| 100256__google_gemma-4-e4b__dark_mode_off__r1 | google/gemma-4-e4b | dark_mode_off | 1 | ❌ failed |  | 13 | 160.6 | ![s](screenshots/trials/100256__google_gemma-4-e4b__dark_mode_off__r1.png) |  |
| 100542__google_gemma-4-e4b__dark_mode_off__r2 | google/gemma-4-e4b | dark_mode_off | 2 | ❌ failed |  | 17 | 224.2 | ![s](screenshots/trials/100542__google_gemma-4-e4b__dark_mode_off__r2.png) |  |
| 100932__google_gemma-4-e4b__bluetooth_toggle_on__r0 | google/gemma-4-e4b | bluetooth_toggle_on | 0 | ✅ done |  | 17 | 127.6 | ![s](screenshots/trials/100932__google_gemma-4-e4b__bluetooth_toggle_on__r0.png) |  |
| 101145__google_gemma-4-e4b__bluetooth_toggle_on__r1 | google/gemma-4-e4b | bluetooth_toggle_on | 1 | ✅ done |  | 16 | 121.1 | ![s](screenshots/trials/101145__google_gemma-4-e4b__bluetooth_toggle_on__r1.png) |  |
| 101351__google_gemma-4-e4b__bluetooth_toggle_on__r2 | google/gemma-4-e4b | bluetooth_toggle_on | 2 | ✅ done |  | 17 | 134.1 | ![s](screenshots/trials/101351__google_gemma-4-e4b__bluetooth_toggle_on__r2.png) |  |
| 101610__google_gemma-4-e4b__brightness_set_50__r0 | google/gemma-4-e4b | brightness_set_50 | 0 | ❌ failed |  | 17 | 140.7 | ![s](screenshots/trials/101610__google_gemma-4-e4b__brightness_set_50__r0.png) |  |
| 101836__google_gemma-4-e4b__brightness_set_50__r1 | google/gemma-4-e4b | brightness_set_50 | 1 | ✅ done |  | 25 | 172.6 | ![s](screenshots/trials/101836__google_gemma-4-e4b__brightness_set_50__r1.png) |  |
| 102134__google_gemma-4-e4b__brightness_set_50__r2 | google/gemma-4-e4b | brightness_set_50 | 2 | ❓ fail_loop |  | 26 | 210.2 | ![s](screenshots/trials/102134__google_gemma-4-e4b__brightness_set_50__r2.png) |  |
| 102509__google_gemma-4-e4b__timer_set_5min__r0 | google/gemma-4-e4b | timer_set_5min | 0 | ✅ done |  | 12 | 88.6 | ![s](screenshots/trials/102509__google_gemma-4-e4b__timer_set_5min__r0.png) |  |
| 102643__google_gemma-4-e4b__timer_set_5min__r1 | google/gemma-4-e4b | timer_set_5min | 1 | ✅ done |  | 9 | 125.6 | ![s](screenshots/trials/102643__google_gemma-4-e4b__timer_set_5min__r1.png) |  |
| 102854__google_gemma-4-e4b__timer_set_5min__r2 | google/gemma-4-e4b | timer_set_5min | 2 | ✅ done |  | 11 | 181.1 | ![s](screenshots/trials/102854__google_gemma-4-e4b__timer_set_5min__r2.png) |  |
| 103200__google_gemma-4-e4b__pizza_search__r0 | google/gemma-4-e4b | pizza_search | 0 | ✅ done |  | 3 | 47.5 | ![s](screenshots/trials/103200__google_gemma-4-e4b__pizza_search__r0.png) |  |
| 103253__google_gemma-4-e4b__pizza_search__r1 | google/gemma-4-e4b | pizza_search | 1 | ✅ done |  | 5 | 31.5 | ![s](screenshots/trials/103253__google_gemma-4-e4b__pizza_search__r1.png) |  |
| 103330__google_gemma-4-e4b__pizza_search__r2 | google/gemma-4-e4b | pizza_search | 2 | ✅ done |  | 8 | 49.5 | ![s](screenshots/trials/103330__google_gemma-4-e4b__pizza_search__r2.png) |  |
| 105416__google_gemma-4-e4b__dark_mode_on__r0 | google/gemma-4-e4b | dark_mode_on | 0 | ✅ done |  | 7 | 74.6 | ![s](screenshots/trials/105416__google_gemma-4-e4b__dark_mode_on__r0.png) |  |
| 105536__google_gemma-4-e4b__dark_mode_on__r1 | google/gemma-4-e4b | dark_mode_on | 1 | ❌ failed |  | 20 | 156.1 | ![s](screenshots/trials/105536__google_gemma-4-e4b__dark_mode_on__r1.png) |  |
| 105817__google_gemma-4-e4b__dark_mode_on__r2 | google/gemma-4-e4b | dark_mode_on | 2 | ❓ fail_loop |  | 26 | 207.7 | ![s](screenshots/trials/105817__google_gemma-4-e4b__dark_mode_on__r2.png) |  |
| 110151__google_gemma-4-e4b__dark_mode_off__r0 | google/gemma-4-e4b | dark_mode_off | 0 | ❌ failed |  | 6 | 40.5 | ![s](screenshots/trials/110151__google_gemma-4-e4b__dark_mode_off__r0.png) |  |
| 110236__google_gemma-4-e4b__dark_mode_off__r1 | google/gemma-4-e4b | dark_mode_off | 1 | ✅ done |  | 21 | 180.6 | ![s](screenshots/trials/110236__google_gemma-4-e4b__dark_mode_off__r1.png) |  |
| 110543__google_gemma-4-e4b__dark_mode_off__r2 | google/gemma-4-e4b | dark_mode_off | 2 | ❓ fail_loop |  | 26 | 218.7 | ![s](screenshots/trials/110543__google_gemma-4-e4b__dark_mode_off__r2.png) |  |
| 110927__google_gemma-4-e4b__bluetooth_toggle_on__r0 | google/gemma-4-e4b | bluetooth_toggle_on | 0 | ❌ failed |  | 16 | 73.1 | ![s](screenshots/trials/110927__google_gemma-4-e4b__bluetooth_toggle_on__r0.png) |  |
| 111045__google_gemma-4-e4b__bluetooth_toggle_on__r1 | google/gemma-4-e4b | bluetooth_toggle_on | 1 | ✅ done |  | 4 | 28.0 | ![s](screenshots/trials/111045__google_gemma-4-e4b__bluetooth_toggle_on__r1.png) |  |
| 111118__google_gemma-4-e4b__bluetooth_toggle_on__r2 | google/gemma-4-e4b | bluetooth_toggle_on | 2 | ✅ done |  | 3 | 30.5 | ![s](screenshots/trials/111118__google_gemma-4-e4b__bluetooth_toggle_on__r2.png) |  |
| 111154__google_gemma-4-e4b__brightness_set_50__r0 | google/gemma-4-e4b | brightness_set_50 | 0 | ❓ fail_loop |  | 27 | 174.6 | ![s](screenshots/trials/111154__google_gemma-4-e4b__brightness_set_50__r0.png) |  |
| 111455__google_gemma-4-e4b__brightness_set_50__r1 | google/gemma-4-e4b | brightness_set_50 | 1 | ❌ failed |  | 25 | 195.8 | ![s](screenshots/trials/111455__google_gemma-4-e4b__brightness_set_50__r1.png) |  |
| 111816__google_gemma-4-e4b__brightness_set_50__r2 | google/gemma-4-e4b | brightness_set_50 | 2 | ✅ done |  | 22 | 191.1 | ![s](screenshots/trials/111816__google_gemma-4-e4b__brightness_set_50__r2.png) |  |
| 112132__google_gemma-4-e4b__timer_set_5min__r0 | google/gemma-4-e4b | timer_set_5min | 0 | ✅ done |  | 18 | 134.1 | ![s](screenshots/trials/112132__google_gemma-4-e4b__timer_set_5min__r0.png) |  |
| 112351__google_gemma-4-e4b__timer_set_5min__r1 | google/gemma-4-e4b | timer_set_5min | 1 | ✅ done |  | 16 | 108.6 | ![s](screenshots/trials/112351__google_gemma-4-e4b__timer_set_5min__r1.png) |  |
| 112545__google_gemma-4-e4b__timer_set_5min__r2 | google/gemma-4-e4b | timer_set_5min | 2 | ✅ done |  | 13 | 137.6 | ![s](screenshots/trials/112545__google_gemma-4-e4b__timer_set_5min__r2.png) |  |
| 112808__google_gemma-4-e4b__pizza_search__r0 | google/gemma-4-e4b | pizza_search | 0 | ✅ done |  | 9 | 64.6 | ![s](screenshots/trials/112808__google_gemma-4-e4b__pizza_search__r0.png) |  |
| 112918__google_gemma-4-e4b__pizza_search__r1 | google/gemma-4-e4b | pizza_search | 1 | ❌ failed |  | 12 | 100.1 | ![s](screenshots/trials/112918__google_gemma-4-e4b__pizza_search__r1.png) |  |
| 113104__google_gemma-4-e4b__pizza_search__r2 | google/gemma-4-e4b | pizza_search | 2 | ❌ failed |  | 6 | 37.6 | ![s](screenshots/trials/113104__google_gemma-4-e4b__pizza_search__r2.png) |  |

# Korrektur-Test — rettet eine Nutzer-Korrektur gescheiterte Tasks?

Stand 2026-05-26. Frage: **Wenn die Baseline scheitert — verbessert eine
Nutzer-Korrektur das Ergebnis?** Getestet mit zwei Modellen: dem stärksten
lokalen (qwen3.6-35b, „Winner") und einem mittelstarken (gemma-4-e4b).
adb-Backend, Pixel-9a-Emulator.

## Methodik (verifiziert, gepaart, fair)

Pro Task:
1. **Reboot + erzwungener Gegen-Zustand** (`pre_state` via adb) — eliminiert die
   `passive_success`-Falle der alten Studie (Erfolg kann nicht zufällig aus
   einem Vorlauf stammen).
2. **Baseline** — Auftrag normal an den Agenten.
3. **Verifikation** — objektiv per adb-Zustand (dark/bluetooth/brightness) bzw.
   Screenshot (timer/pizza), abgeglichen mit `success_criterion`.
4. **Korrektur NUR bei echtem Fehler** — realistisch: der Nutzer korrigiert nur,
   wenn das Ergebnis falsch ist. Ich (Operator) prüfe den Screenshot/Zustand und
   gebe die Korrektur nur dann. Sie geht als Folge-Auftrag mit `follow_up=true`;
   der Agent bekommt den vorigen Lauf als Kontext (`follow_up_context=true` in
   JEDER Korrektur belegt das). **Kein Reset** zwischen Baseline und Korrektur.
5. **Erneute Verifikation** der Korrektur.

Die Korrektur ist **bedingt** formuliert („falls X nicht stimmt, mach X"), nicht
als feste Falsch-Behauptung — sonst dreht sie ein korrektes Baseline-Ergebnis um
(im Smoke-Test beobachtet und behoben).

---

## Kern-Ergebnis: Vorher (Baseline gescheitert) → Nachher (Korrektur)

Nur die Tasks, die in der Baseline **scheiterten** (sonst keine Korrektur):

| Modell | Task | Baseline (Fehler) | nach Korrektur | gerettet? |
|---|---|---|---|---|
| **gemma-4-e4b** | **dark_mode_on** | ❌ `night-mode=no` | ✅ **`night-mode=yes`** | **JA ✅** |
| gemma-4-e4b | dark_mode_off | ❌ `night-mode=yes` | ❌ `night-mode=yes` | nein |
| gemma-4-e4b | brightness_set_50 | ❌ ~90 % | ❌ ~90 % | nein |
| gemma-4-e4b | timer_set_5min | ❌ kein Timer (World-Clock-Tab) | ❌ kein Timer | nein |
| gemma-4-e4b | pizza_search | ❌ nur Trefferliste | ❌ ehrlicher Fehler¹ | nein |
| qwen3.6-35b | brightness_set_50 | ❌ ~9 % | ❌ ~9 % | nein |
| qwen3.6-35b | pizza_search | ❌ nur Trefferliste | ❌ ehrlicher Fehler¹ | nein |

¹ „ehrlicher Fehler" = der Agent meldet nach der Korrektur ausdrücklich
`failed` („Konnte keine Restaurant-Detailseite öffnen" / „kann keine Elemente
erkennen"), statt wie in der Baseline fälschlich `done` auf einer bloßen
Trefferliste. Verhaltensänderung in die richtige Richtung, aber kein
Task-Erfolg.

**Klar gerettet: 1 von 7 echten Fehlern (gemma · dark_mode_on, ❌→✅).**

---

## Vollständige Matrizen (je 1 verifizierter Run/Task)

### qwen3.6-35b (Winner)

| Task | Baseline | Verifikation | Korrektur |
|---|---|---|---|
| dark_mode_on | ✅ | `night-mode=yes` | — |
| dark_mode_off | ✅ | `night-mode=no` | — |
| bluetooth_toggle_on | ✅ | `bluetooth_on=1` | — |
| timer_set_5min | ✅ (Intent²) | „5m Timer" erstellt | — |
| brightness_set_50 | ❌ ~9 % | adb | ❌ ~9 % |
| pizza_search | ❌ Trefferliste | Screenshot | ❌ ehrlicher Fehler |

### gemma-4-e4b (mittelstark)

| Task | Baseline | Verifikation | Korrektur |
|---|---|---|---|
| bluetooth_toggle_on | ✅ | `bluetooth_on=1` | — |
| dark_mode_on | ❌ | `night-mode=no` | ✅ **`night-mode=yes`** |
| dark_mode_off | ❌ | `night-mode=yes` | ❌ `night-mode=yes` |
| brightness_set_50 | ❌ | ~90 % | ❌ ~90 % |
| timer_set_5min | ❌ | kein Timer (World Clock) | ❌ kein Timer |
| pizza_search | ❌ | Trefferliste | ❌ ehrlicher Fehler |

² Mess-Confound: bei langsamen Modellen läuft ein 5-Min-Timer während des Runs
ab → verifiziert wird „wurde ein **5-Minuten**-Timer erstellt" (Minuten statt
Sekunden), nicht „zeigt jetzt 5:00".

---

## Interpretation — ehrlich

**1. Der Korrektur-Mechanismus funktioniert technisch einwandfrei.** In jedem
Korrektur-Lauf war `follow_up_context=true` (vorheriger Auftrag als Kontext
injiziert), und der Agent hat sein Verhalten messbar geändert.

**2. Korrektur KANN einen echten Fehler retten** — bewiesen an
**gemma · dark_mode_on**: Baseline ließ den Schalter aus (`night-mode=no`,
Modell meldete sogar `failed`); nach der Korrektur mit Kontext war Dark Mode an
(`night-mode=yes`). Objektiv per adb verifiziert, kein Screenshot-Ermessen.

**3. Häufig wird NICHT gerettet — und das ist konsistent erklärbar:**
- **Fähigkeitsgrenzen** (beide Modelle): Helligkeits-Slider (kontinuierliche
  Drag-Geste) und Multi-Step-Web-Navigation zu *einer konkreten*
  Restaurant-Seite — verbale Korrektur hebt fehlende Fähigkeit nicht auf.
- **Schwaches Modell bleibt schwach** (gemma dark_off/timer): das Modell meldet
  auch nach der Korrektur `done`, ohne real zu handeln (Hallucinated Success).
- **Wertvoller Nebeneffekt**: bei pizza ersetzt die Korrektur das *falsche*
  `done` durch ein *ehrliches* `failed` — die Interaktions-Schicht macht das
  Scheitern wenigstens sichtbar statt es zu verschleiern.

→ Deckt sich mit dem **Kernbefund der Failure-Mode-Studie**: der Flaschenhals
ist das **Modell**, nicht die Architektur/der Interaktions-Kanal. Die Korrektur
öffnet den Kanal sauber; die Decke ist die Modell-Fähigkeit. Wo der Fehler
*reparierbar* war (Toggle nur verpasst), hat die Korrektur ihn behoben.

**Hygiene-Nebenbefund:** Mit erzwungenem Pre-State besteht der Champion die
Baseline deutlich sauberer als die alte Studie (4/6 sofort korrekt) → viele alte
„Fehler" waren `passive_success`/State-Pollution-Artefakte.

---

## Gefundene & behobene Confounds

- **Falsch-Prämisse in der Korrektur** (Smoke-Test): fixe Korrektur
  („es ist noch falsch") drehte ein korrektes dark_mode-Ergebnis um → auf
  bedingte Formulierung umgestellt.
- **Timer-Benachrichtigungen lenkten den Agenten ab:** erster pizza-Lauf landete
  in der Timer-App (abgelaufene Timer überleben den Reboot). `force-stop` löscht
  gespeicherte Timer nicht → `pm clear com.google.android.deskclock`. Sauberer
  pizza-Re-Run bestätigte das Ergebnis.

---

## Belege (Screenshots)

| Datei | zeigt |
|---|---|
| `media/corrtest/google_gemma-4-e4b__dark_mode_on__base.png` | **Recovery vorher**: Light Mode (Fehler) |
| `media/corrtest/google_gemma-4-e4b__dark_mode_on__corr.png` | **Recovery nachher**: Dark Mode an (gerettet) |
| `media/corrtest/google_gemma-4-e4b__pizza_search__corr.png` | Korrektur → ehrlicher Fehler statt falschem `done` |
| `media/corrtest/qwen_qwen36-35b-a3b__brightness_set_50__base.png` | Champion brightness ~9 % |
| `media/corrtest/qwen_qwen36-35b-a3b__brightness_set_50__corr.png` | nach Korrektur weiterhin ~9 % |
| `media/corrtest/qwen_qwen36-35b-a3b__pizza_search__base.png` | Champion: nur Trefferliste |
| `media/corrtest/qwen_qwen36-35b-a3b__pizza_search__corr.png` | Champion: Tripadvisor, ehrlicher Fehler |

Roh-Daten: `experiments/results_correction/*.json` (Tool-Trace, Timing,
`follow_up_context`, Outcome je Phase).

## Aussage fürs Meeting (eine Folie)

> Die Mid-run-/Folge-Korrektur ist technisch nachgewiesen (`follow_up_context`
> in allen Läufen). Sie **rettet reparierbare Fehler** (gemma: Dark Mode
> ❌→✅) und macht **verschleierte Fehler ehrlich** (falsches `done` →
> sichtbares `failed`). Sie hebt **keine Fähigkeitsgrenzen** auf (Slider,
> tiefe Web-Navigation) — derselbe Befund wie überall: Flaschenhals ist das
> Modell, nicht die Interaktions-Schicht.

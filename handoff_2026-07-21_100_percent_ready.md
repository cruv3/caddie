# Caddie Study Handoff — 100%-Ready Plan

Stand: 2026-07-21, Handy `35091FDH2002ZN`, Study-Server `http://127.0.0.1:8787`.

## Kurzfazit

### Update 21.07.2026, Trial 1 fertig

- `study-gallery` und `study-notes` sind als lokale Android-Module umgesetzt und in Gradle registriert.
- Vier AI-generierte, private-datenfreie Campusfotos sind als App-Ressourcen eingebunden.
- Die Galerie besitzt eine glaubwürdige Fotoübersicht, ein kontrolliertes Whiteboard als neuesten Treffer und eine dunkle Detailansicht. Die Aufgaben sind als perspektivische blaue Marker-Schrift direkt in das Foto gerendert; es gibt keine Text-Overlays im Android-Layout.
- Die Notiz-App besitzt Suche, Filter, vier glaubwürdige Basisnotizen, Editor und eine separat resetbare Studiennotiz.
- Reset-Broadcasts und das zentrale Geräte-Reset-Skript setzen Galerie und Studiennotiz zurück; die Basisnotizen bleiben bestehen.
- Trial 1 verwendet keine Google-Fotos-/Keep-Daten mehr und lief nach dem UI-Redesign real auf `35091FDH2002ZN` erfolgreich: `8/8` Schritte, C1-Gates vollständig, kontrollierter Fehler sichtbar, `outcome=success`.
- Finale Artefakte: `mcp-server/tmp/pilot-real-trial1-polished-ui/v1/P01/sess_1784664054/`.
- Verifikation: Study Gallery `3/3`, Study Notes `3/3`, fokussierte Python-Suite `75 passed`.
- Androids Stock-ADB-Eingabe kann auf diesem Pixel kein `ü` zuverlässig eingeben; deshalb nutzt der Executor in der Studiennotiz bewusst `pruefen`. Sichtbare App-Texte außerhalb der automatisierten Eingabe bleiben korrekt deutsch.

Wir sollten die Galerie faken. Der echte Google-Fotos-Flow ist nicht stabil und nicht privat genug:

- Google Fotos liefert für Bilder keine stabile Accessibility-Bezeichnung wie `Caddie Whiteboard Photo Project Meeting`.
- Der sichtbare Treffer ist nur generisch (`Foto wurde aufgenommen am ...`) und hängt vom aktuellen Grid/Detailzustand ab.
- Nach Reset blieb Google Fotos teilweise in der Foto-Detailansicht; dadurch startete Trial 1 nicht deterministisch aus dem Grid.
- Keep/Google Notizen zeigt echte private Notizen im UI-Dump. Für eine saubere Studie sollte auch der Notiz-Zielbereich entweder fake sein oder strikt isoliert werden.

## Bereits erledigt

- Real Trial 0 (`task_maps_messenger`, C1) lief erfolgreich durch: `outcome=success`, `steps_executed=7`.
- `/control confirm` wurde gefixt: Confirm erreicht jetzt aktive Study-Sessions, nicht nur normale AgentLoop-Runs.
- SSE-Auto-Confirm-Verständnis geklärt: `/events` sendet `data:`-Only-SSE, nicht `event:`-Namen.
- Trial 1 wurde bis in die echten Problemstellen debuggt:
  - Google Fotos hat keinen stabilen Whiteboard-Selector.
  - Keep-FAB heißt tatsächlich `Notiz erstellen`, nicht `Neue Textnotiz`.
  - `adb shell input text` brach bei `;` als Shell-Separator; Quote-Fix ist implementiert und getestet.
- Tests zuletzt grün:
  - `tests/test_adb_input.py`
  - `tests/test_study_executor.py`
  - `tests/test_study_spec_loader.py`
  - Ergebnis: `70 passed`, nur bekannter `.pytest_cache`-Permission-Warning.

## Aktuelle relevante Codeänderungen

- `mcp-server/caddie/agent/agent_loop.py`
  - `apply_control()` fällt auf aktive `StudySession` zurück, wenn kein normaler AgentLoop-Run aktiv ist.
- `mcp-server/caddie/study/executor.py`
  - Neuer deterministischer Action-Typ `click first '<label>'`.
  - Dient als Übergangslösung für generische Grid-Elemente, ist aber für echte Galerie nicht ausreichend robust.
- `mcp-server/caddie/android/backends/adb/input.py`
  - `input text` wird jetzt shell-sicher einfach gequotet, damit `;` nicht als Shell-Befehl ausgeführt wird.
- `mcp-server/study/specs/task_gallery_notes.yaml`
  - Zwischenstand nutzt aktuell noch Google Fotos/Keep; sollte auf Fake Gallery und optional Fake Notes umgestellt werden.
- `mcp-server/tests/test_adb_input.py`
  - Regressionstests für Semikolon und Single-Quote in ADB-Text.

## P0: Was noch gebaut werden muss

### 1. Fake Gallery App

Ziel: kein echtes Google Fotos, keine echten Bilder, stabile Accessibility.

Umsetzung:

- Neues Android-Modul `mcp-server/study-gallery`.
- Package z. B. `com.caddie.studygallery`.
- UI im einfachen Gallery-Stil:
  - Header: `Fotos`
  - Grid mit mehreren Fake-Bildern.
  - Neuestes Bild sichtbar oben links.
  - Accessibility/Text stabil: `Caddie Whiteboard Photo Project Meeting`.
  - Bilddetail nach Klick mit Whiteboard-Inhalt:
    - `Tuesday = Submit Report`
    - `Thursday = Review Draft`
- Reset-Broadcast:
  - `com.caddie.studygallery.ACTION_RESET`
  - setzt Auswahl/Detailzustand zurück auf Grid.
- Keine externe Accounts, keine echten Medien, keine Berechtigungen für echte Fotos.

Tests:

- Unit/UI-Test: Grid enthält `Caddie Whiteboard Photo Project Meeting`.
- Reset-Test: nach Detailansicht + Reset wieder Grid sichtbar.
- ADB-Smoke:
  - App öffnen.
  - Whiteboard-Kachel anklicken.
  - Detailtext per `uiautomator` sichtbar.

### 2. Fake Notes oder isolierter Notes-Flow

Aktueller echter Keep-Flow zeigt private Notizen. Für echte Pilotaufnahme ist das schlecht.

Empfehlung:

- Besser ebenfalls Fake Notes bauen: `mcp-server/study-notes`, Package `com.caddie.studynotes`.
- Minimal UI:
  - Header `Notizen`
  - FAB `Notiz erstellen`
  - Editor mit Feld `Notiztext`
  - Speichert eingegebenen Text sichtbar in einer Notizkarte.
  - Reset-Broadcast löscht Study-Notiz und setzt auf leere Liste.

Alternative, falls schnell:

- Echten Keep nur verwenden, wenn privater Account/Notizen vorher vollständig bereinigt sind.
- Reset-Script muss dann `com.google.android.keep` force-stoppen und optional Study-Notiz löschen.

### 3. Trial 1 Spec umstellen

`mcp-server/study/specs/task_gallery_notes.yaml` sollte danach so umgestellt werden:

- `required_packages`:
  - `com.caddie.studygallery`
  - optional `com.caddie.studynotes`
- Steps:
  - `open com.caddie.studygallery`
  - `click 'Caddie Whiteboard Photo Project Meeting'`
  - `open com.caddie.studynotes`
  - `click 'Notiz erstellen'`
  - `input text 'Projekt: Bericht Dienstag abgeben; Entwurf Dienstag prüfen'`
- Verification:
  - Text `Projekt` sichtbar.
  - Fehler-Injektion bleibt: Dienstag statt Donnerstag.

### 4. Reset-Script finalisieren

`mcp-server/scripts/reset_study_device.ps1` muss vollständig sein:

- Broadcast reset:
  - `com.caddie.studybank`
  - `com.caddie.studymail`
  - `com.caddie.studytelegram`
  - `com.caddie.studygallery`
  - optional `com.caddie.studynotes`
- Force-stop:
  - alle Fake-Study-Apps
  - zusätzlich echte Apps, solange noch verwendet:
    - `com.google.android.apps.photos`
    - `com.google.android.keep`
    - Google Calendar, falls im Kalender-Task noch echt
- DND, Brightness, Screen timeout, ADB reverse bleiben wie aktuell.

### 5. Real-Run Wrapper statt Ad-hoc Python

Aktuell ist `python -m caddie.study.cli run` mock-only. Echte Runs laufen über HTTP `/study/trials/run`.

Noch bauen:

- CLI-Befehl z. B. `python -m caddie.study.cli real-run --participant P01 --trial-index 1 --auto-confirm`.
- Der Wrapper muss:
  - `/events` als `data:`-only SSE lesen.
  - bei `confirmation_required` automatisch oder manuell `/control {"action":"confirm"}` schicken.
  - finalen Status und Artefaktpfad ausgeben.
- Tests:
  - SSE data-only Parser.
  - Auto-confirm bei C1.
  - Kein POST `/study/trials/status`; Status ist GET.

## P1: Danach alle sechs Trials real abnehmen

P01 Matrix:

1. Trial 0: `task_maps_messenger`, `c1_stepwise` — bereits real erfolgreich.
2. Trial 1: `task_gallery_notes`, `c1_stepwise` — nach Fake Gallery/Notes erneut laufen lassen.
3. Trial 2: `task_chat_spotify`, `c2_final_checkpoint`.
4. Trial 3: `task_email_calendar`, `c2_final_checkpoint`.
5. Trial 4: `task_calendar_dnd`, `c3_voluntary_intervention`.
6. Trial 5: `task_banking_payment`, `c3_voluntary_intervention`.

Für jeden Trial:

- Reset vorher.
- Real-run über HTTP oder neuen CLI-Wrapper.
- Artefakte in `mcp-server/tmp/pilot-real-trialX-*`.
- Prüfen:
  - `outcome=success`
  - Gate-Events vollständig (`confirmation_required`, `confirmation_resolved`)
  - Fehler-Injektion sichtbar bei Error-Trials
  - App nach Run wieder resetbar

## P1: Fake-Apps Status prüfen

- Study Bank:
  - Transfer UI optisch erledigt.
  - Reset per ADB/Broadcast.
  - Keine sichtbare Reset-Taste im UI.
- Study Mail:
  - Gmail-Style Fake Mail vorhanden.
  - Fake gelesene und ungelesene Mails.
  - Reset per ADB/Broadcast.
- Study Telegram:
  - Fake Chat vorhanden.
  - Anne/Anni Clickability und Overview-Preview wurden debuggt.
  - Noch einmal mit Screenshot prüfen, ob Preview nicht wieder Volltext leakt.
- Study Gallery:
  - Muss neu gebaut werden.
- Optional Study Notes:
  - Stark empfohlen, weil echtes Keep private Notizen zeigt.

## P1: Calendar

- Kalender-Seed/Reset ist als eigener Block vorbereitet.
- Ziel bleibt dynamisch: heutiger Tag, 14:00–15:00.
- Reset muss sicher erkennen, ob der Study-Termin verändert wurde, und ihn zurücksetzen können.
- Wenn echte Google Calendar UI weiter genutzt wird:
  - vorher privaten Kalenderinhalt prüfen.
  - besser isolierten Kalender/Account oder Fake Calendar verwenden.

## P2: Cleanup vor Abgabe

- Working tree ist sehr dirty; nichts blind committen.
- Untracked Test-/Tmp-Artefakte prüfen und nur relevante Dateien behalten.
- `mcp-server/tmp/**` nicht in finalen Commit.
- Bekannter harmloser Warning:
  - `.pytest_cache` Permission-Warning bei Pytest.
- Server-Neustart nach Code-/Spec-Änderungen nicht vergessen.

## Verifikationsbefehle

Aus `C:\Users\Andreas\dev\Caddie\mcp-server`:

```powershell
.\.venv\Scripts\python.exe -m pytest tests\test_adb_input.py tests\test_study_executor.py tests\test_study_spec_loader.py -q --basetemp C:\Users\Andreas\dev\Caddie\mcp-server\tmp\pytest-caddie
```

```powershell
powershell -ExecutionPolicy Bypass -File scripts\reset_study_device.ps1 -Serial 35091FDH2002ZN -SkipCalendar
```

```powershell
Invoke-RestMethod -Method Get -Uri http://127.0.0.1:8787/study/health -TimeoutSec 5 | ConvertTo-Json -Depth 8
```

Server restart:

```powershell
$line = netstat -ano | Select-String '0\.0\.0\.0:8787|127\.0\.0\.1:8787' | Select-Object -First 1
if ($line) { $procId = [int](($line -split '\s+')[-1]); Stop-Process -Id $procId -Force }
Start-Process -FilePath 'C:\Users\Andreas\dev\Caddie\mcp-server\.venv\Scripts\python.exe' -ArgumentList '-m experiments.start_task_server' -WorkingDirectory 'C:\Users\Andreas\dev\Caddie\mcp-server' -WindowStyle Hidden
```

## Definition of Done

100% ready heißt:

- Alle Study-Tasks nutzen entweder Fake-Apps oder isolierte echte Apps ohne private Daten.
- Reset stellt jeden Task deterministisch auf Startzustand.
- Alle sechs Trials laufen real auf dem Handy durch.
- C1/C2/C3 Gate-Events sind in Artefakten sichtbar.
- Error-Injektionen sind sichtbar und dokumentiert.
- Screen-off/C4 bleibt separater Punkt und wird nicht mit den sechs Haupttrials vermischt.
- Preflight ist grün.
- Handoff enthält finale Befehle, Artefaktpfade und bekannte Restrisiken.

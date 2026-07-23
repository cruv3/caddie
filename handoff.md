# Caddie Study Handoff — Stand 20.07.2026

## Ziel

Die vollständige Nutzerstudie soll auf dem vorbereiteten Pixel zuverlässig durchführbar sein. Vor dem Piloten müssen alle sechs Kernaufgaben auf dem echten Gerät von einem definierten Ausgangszustand bis zu einem verifizierten Endzustand laufen, kontrolliert zurückgesetzt werden können und in C1, C2 und C3 das vorgesehene Aufsichtsverhalten zeigen.

**Wichtig:** Die Banking-App ist ein funktionaler Zwischenstand, aber visuell und inhaltlich noch nicht vollständig fertig. Die grünen Unit-/Instrumentationstests bedeuten ebenfalls noch nicht, dass die sechs realen Cross-App-Aufgaben funktionieren.

---

## Aktueller technischer Stand

- Branch: `main`
- Arbeitsbaum: stark verändert und nicht sauber. Vorhandene Änderungen gehören zum laufenden Studienaufbau.
- **Niemals** `git reset --hard`, `git checkout -- .`, `git clean` oder pauschales Staging verwenden.
- Verbundenes Testgerät: Pixel, ADB-Seriennummer `35091FDH2002ZN`
- Haupt-App: `com.caddie`
- Banking-Mock: `com.caddie.studybank`
- Vor jeder Installation einer neuen APK zuerst die jeweilige alte App deinstallieren.
- `adb uninstall` liefert auf diesem Gerät teilweise `DELETE_FAILED_INTERNAL_ERROR`, wenn das Paket bereits fehlt. Dann mit `adb shell pm path <package>` prüfen; bei leerer Ausgabe direkt installieren.

### Zuletzt verifiziert

- Study-Bank Build: erfolgreich
- Study-Bank Instrumentation: **4/4 Tests bestanden**
- Study Spec/Preflight Tests: **42 Tests bestanden**
- Banking-Fehlerweg manuell auf dem Pixel geprüft:
  - Start: `40,00 €`
  - simulierte Fehlerzahlung: `80,00 €`
  - Rückkehr zur Home-Ansicht
  - Saldo: `−40,00 €` in Rot
  - neuer Umsatz: `−80,00 €` in Rot
- Finale Banking-App wurde anschließend frisch installiert und auf den Ausgangszustand zurückgesetzt.

### Relevante Banking-Dateien

- `mcp-server/study-bank/src/main/java/com/caddie/studybank/BankingActivity.kt`
- `mcp-server/study-bank/src/main/res/layout/activity_banking.xml`
- `mcp-server/study-bank/src/main/res/values/colors.xml`
- `mcp-server/study-bank/src/main/res/values/strings.xml`
- `mcp-server/study-bank/src/main/res/drawable/`
- `mcp-server/study-bank/src/androidTest/java/com/caddie/studybank/BankingActivityTest.kt`
- `mcp-server/study/specs/task_banking_payment.yaml`

---

## P0 — Blocker vor dem ersten echten Piloten

### 1. Die sechs Task-Spezifikationen sind noch nicht ausführbar

Die Dateien unter `mcp-server/study/specs/task_*.yaml` enthalten größtenteils semantische Beschreibungen und keine vollständig ausführbaren Executor-Schritte.

Der deterministische Executor unterstützt aktuell nur diese Aktionsformen:

- `open <package>`
- `open_url <url>`
- `click '<sichtbarer Text oder resource-id>'`
- `input text '<Text>'`
- `scroll <direction>`
- `press <BACK|HOME|RECENT>`

Aktuelle ungültige Beispiele in den Specs sind unter anderem:

- `verify ...`
- `ocr ...`
- `tap ...`
- `input recipient ... / amount ...`
- `click ... → Anna`
- zusammengesetzte Aktionen mit mehreren UI-Schritten in einer Zeile

Diese Aktionen müssen in einzelne reale Android-Schritte zerlegt werden. Jeder Tap benötigt einen auf dem echten Telefon eindeutig auffindbaren Text, eine Content Description oder eine stabile Resource-ID.

**Akzeptanz:** Keine der sechs Specs darf beim Parsen oder Ausführen eine unbekannte Aktion enthalten. Jede Spec läuft mit dem echten Android-Backend statt nur mit `FakeBackend`.

### 2. Alle sechs Aufgaben einmal manuell auf dem finalen Smartphone durchführen

Vor der Replay-Erstellung jede Aufgabe selbst vollständig durchspielen. Dabei für jeden Schritt erfassen:

- tatsächlich verwendete App und Package-ID;
- sichtbarer Text, Resource-ID und Content Description des Ziels;
- notwendige Scrolls und Tastaturzustände;
- finaler Erfolgszustand;
- möglicher kontrollierter Fehlerzeitpunkt;
- notwendiger Reset;
- Dauer ohne und mit Bestätigungsdialogen.

Erst danach die endgültigen Executor-/Replay-Schritte schreiben. Keine Koordinaten verwenden, solange ein semantischer Selektor möglich ist.

### 3. Replays/Executor-Läufe stabilisieren

`caddie/agent/replay.py` zeichnet erfolgreiche Agentenläufe als semantische Schritte auf. Konsequente Aktionen wie Senden, Bezahlen oder Löschen werden aus Sicherheitsgründen nicht automatisch über den billigen Replay-Fast-Path ausgeführt. Für die Studie muss daher entschieden und getestet werden, welcher Pfad autoritativ ist:

- deterministischer `caddie.study.executor`, einschließlich C1/C2/C3-Gates; oder
- normaler Agentenlauf mit aufgezeichneten Navigationsschritten und weiterhin aktivem Bestätigungsgate für riskante Aktionen.

Ein `.steps.json` allein beweist bei riskanten Aufgaben keinen vollständigen Replay bis zum Ende.

**Akzeptanz pro Aufgabe:**

1. Reset durchführen.
2. Aufgabe fünfmal hintereinander ohne manuelle Rettung ausführen.
3. Jeder Lauf erreicht denselben verifizierten Endzustand.
4. Der kontrollierte Fehler tritt ausschließlich am definierten Schritt auf.
5. Caddie zeigt währenddessen die aktuelle Aktion verständlich an.
6. Stop/Intervention funktioniert, ohne dass nachfolgende Schritte weiterlaufen.
7. Nach Reset funktioniert der nächste Lauf wieder.
8. Screenshots und `events.jsonl` stimmen mit dem beobachteten Ablauf überein.

---

## P1 — Banking-App fertigstellen

### Bereits vorhanden

- Sparkassen-nahe Home-Ansicht mit lokalem Logo-Mock;
- Girokonto und `40,00 €` Startguthaben;
- Überweisungsformular;
- separate Prüfansicht;
- vollständiges simuliertes Absenden;
- automatische Rückkehr zur Home-Ansicht;
- 30-€- und 80-€-Zustand;
- roter Negativsaldo;
- Reset-Aktion `com.caddie.studybank.ACTION_RESET`;
- stabile IDs und Instrumentationstests.

### Noch zu verbessern

- Überweisungsformular visuell näher an einer echten Banking-App gestalten:
  - feste Feldlabels zusätzlich zu Hints;
  - Ausgangskonto „Girokonto“ anzeigen;
  - Empfänger, IBAN, Betrag und Verwendungszweck sauber gruppieren;
  - Euro-Suffix und deutsche Betragseingabe eindeutig darstellen;
  - strukturierte Prüfansicht statt eines großen Textblocks;
  - Zurück-Navigation muss Eingaben behalten;
  - Tastatur darf „Weiter“ nicht verdecken;
  - optional kurze Erfolgsmeldung auf Home, ohne einen zusätzlichen Studienschritt zu erzwingen.
- Mehr glaubwürdige Basis-Umsätze ergänzen. Vorschlag:
  - BAföG `+934,00 €`
  - Nebenjob `+420,00 €`
  - Miete `−620,00 €`
  - Deutschlandticket `−58,00 €`
  - REWE Markt `−24,31 €`
  - Mensa `−6,40 €`
- Die neue Studienüberweisung muss immer oberhalb dieser Basiseinträge erscheinen.
- Reset darf nur die Studienüberweisung und Formulardaten entfernen; die Basis-Umsätze bleiben erhalten.
- Prüfen, ob der sichtbare Button „Zurücksetzen“ die Glaubwürdigkeit zu stark reduziert. Bessere Optionen:
  - Experimenter-Menü;
  - versteckter Long-Press;
  - ausschließlich Broadcast/ADB-Reset.
  Für den Piloten kann der sichtbare Button bleiben, wenn dadurch weniger Reset-Fehler entstehen.
- Form-/Review-/Home-Screens erneut auf dem echten Pixel screenshotten und nicht nur per Espresso prüfen.

### Banking-Daten endgültig vereinheitlichen

Der neue kanonische Zustand ist:

- Rechnung: `30,00 €`
- Empfänger: `Study Vendor GmbH`
- IBAN: `DE02 1203 0000 0000 2020 51`
- Verwendungszweck: `Rechnung INV-2026-001`
- kontrollierter Fehler: `80,00 €` statt `30,00 €`
- Startsaldo: `40,00 €`
- Fehlersaldo: `−40,00 €`

Noch vorhandene Angaben `38,70 €`, `83,70 €` oder nur `Study Vendor` sind veraltet und müssen aus allen Materialien entfernt werden. Besonders `mcp-server/study/materials/09_reset_checkliste_pro_aufgabe.md` ist aktuell noch veraltet.

---

## P1 — Die sechs Kernaufgaben finalisieren

Die aktuelle kanonische Task-Liste im Repository lautet:

### T1 — Maps → Messenger

**Anweisung:** Ankunftszeit mit öffentlichen Verkehrsmitteln von Campus Gummersbach nach Campus Deutz ermitteln und Anna mitteilen.

Noch zu klären/erledigen:

- endgültige Messenger-App festlegen und Package-ID eintragen;
- Kontakt/Chat „Anna“ vorbereiten;
- Maps-Ausgangsort und Ziel exakt festlegen;
- Netzwerk- und Standortverhalten testen;
- dynamische ÖPNV-Zeiten berücksichtigen: Der korrekte Wert darf nicht als dauerhaft feste Uhrzeit in der Spec stehen;
- Fehler deterministisch relativ zum aktuellen Ergebnis erzeugen, z. B. zehn Minuten falsche Ankunftszeit;
- gesendete Nachricht nach jedem Lauf löschen oder Chat sauber zurücksetzen.

### T2 — Galerie → Notizen

**Anweisung:** Neuestes Whiteboard-Foto der Projektsitzung finden und Aufgaben in eine neue Notiz übertragen.

Noch zu klären/erledigen:

- ein kontrolliertes Whiteboard-Bild mit klar lesbarem Text auf das Gerät legen;
- Dateidatum so setzen, dass es zuverlässig das neueste relevante Foto ist;
- endgültige Foto- und Notiz-App samt Package-IDs festlegen;
- OCR/visuelles Lesen auf dem echten Gerät prüfen;
- kontrollierten Lesefehler exakt definieren, z. B. einen falschen Tag oder falschen Aufgabentext;
- neu erstellte Notiz nach jedem Lauf löschen;
- Tippfehler „Weißbild“ in Specs/Materialien zu „Whiteboard“ korrigieren.

### T3 — Chat → Spotify

**Anweisung:** Letzte Liedempfehlung von Anna im Chat finden und das Lied zu Favoriten hinzufügen.

Noch zu klären/erledigen:

- Spotify installieren, anmelden und störende Onboarding-/Premium-Dialoge schließen;
- Messenger-Chat mit kontrollierter letzter Empfehlung vorbereiten;
- Originaltitel eindeutig in Spotify auffindbar machen;
- kontrollierten Fehler stabil erzeugen: falschen Liedtitel suchen/hinzufügen;
- Favoritenstatus nach jedem Lauf manuell oder automatisiert zurücksetzen;
- prüfen, ob Spotify-Suchergebnisse zwischen Läufen/Accounts variieren;
- keine zufälligen Remix-/Cover-Ergebnisse als Fehlermechanismus verwenden.

### T4 — E-Mail → Kalender

**Anweisung:** Neueste E-Mail mit einer Terminänderung finden und den Kalendereintrag aktualisieren.

Aktueller Stand:

- Study Mail ersetzt Gmail; `ACTION_RESET` stellt die Terminmail nach jedem Lauf wieder auf „ungelesen“;
- Qwen 3.6 hat den semantischen Google-Calendar-Flow ermittelt: Termin öffnen, `Bearbeiten`, `Beginnt um: 14:00`, `15 Stunden`, `OK`, `Speichern`;
- Google Calendar verschiebt beim Ändern der Startzeit die Endzeit automatisch von 15:00 auf 16:00; zusätzliche Endzeit-Schritte sind falsch und wurden entfernt;
- der Executor normalisiert Unicode-Leerzeichen, damit das Accessibility-Label `15 Stunden` stabil mit dem gespeicherten Selektor `15 Stunden` übereinstimmt;
- C3-Replay auf dem verbundenen Pixel abgenommen: 9 Schritte, `success`, sichtbare Accessibility-Verifikation `Projektsitzung, 15:00–16:00 Uhr`;
- kontrollierter Fehler bleibt: Startstunde `16 Stunden` statt `15 Stunden`;
- der isolierte Abnahmetermin wurde nach dem Test gelöscht und Study Mail zurückgesetzt.
- finaler Studien-Termin ist im freigegebenen Google-Kalender `1` mit Marker `CADDIE_STUDY_T4_V1` immer am aktuellen lokalen Pixel-Tag von 14:00–15:00 Uhr geseedet;
- `mcp-server/scripts/reset_study_calendar.ps1 -CalendarId 1 -Serial 35091FDH2002ZN` liest Gerätedatum und IANA-Zeitzone, setzt ausschließlich diesen Marker-Termin über Googles eigene UI zurück und verlangt danach `dirty=0` plus Google-`_sync_id`;
- der Replay enthält vor `Projektsitzung` jetzt den auf dem Pixel bestätigten Accessibility-Schritt `Zu heute springen`; ein festes Datum ist nicht mehr gespeichert;
- die Fake-Mail nennt passend zum dynamischen Seed „heute“ statt „Donnerstag“ und der gezielte Espresso-Gerätetest ist grün;
- echte Geräteabnahme bestanden: Google-UI-Änderung auf 15:00–16:00, synchroner ADB-Reset zurück auf den heutigen Tag 14:00–15:00, normaler Calendar-Start und sichtbarer Termin nach `Zu heute springen`;
- Nachweise: `mcp-server/tmp/device-acceptance/t4-calendar-dynamic-today/open.xml`, `mcp-server/tmp/device-acceptance/t4-calendar-dynamic-today/today.xml`, `mcp-server/tmp/device-acceptance/t4-calendar-dynamic-today/today.png` und `mcp-server/tmp/device-acceptance/t4-calendar-dynamic-today/provider.txt`;
- fokussierte Tests: `90 passed` für Calendar-Reset, Executor, Spec-Loader und Study-HTTP; zusätzlicher Study-Mail-Espresso-Test bestanden.

Noch zu erledigen:

- Datum und Zeitzone werden vom Reset fehlgeschlossen gelesen; sie werden nicht automatisch verändert;
- keine persönlichen Konten oder sonstigen Kalenderdaten ohne ausdrückliche Freigabe verändern.

### T5 — Kalender → Android-Einstellungen

**Anweisung:** Prüfung morgen im Kalender finden und „Nicht stören“ genau für diesen Zeitraum einrichten.

Noch zu klären/erledigen:

- Prüfungstermin vor jedem Studiendurchlauf wirklich auf „morgen“ setzen oder die Anweisung statisch datieren;
- DND-Zugriff/Berechtigung und Systemdialoge vorbereiten;
- Pixel-Einstellungsnavigation vollständig aufzeichnen;
- kontrollierter Fehler: DND endet eine Stunde zu spät;
- DND-Regel nach jedem Lauf löschen und Modus deaktivieren;
- Lautstärke, Wecker und bestehende DND-Regeln dürfen nicht unbeabsichtigt verändert bleiben;
- besonders gründlich testen, weil System-Einstellungen und sichtbare Geräteänderungen nicht unsichtbar sind.

### T6 — E-Mail → Banking

**Anweisung:** Neueste offene Rechnung finden und vollständig in der Banking-Mock-App bezahlen.

Noch zu klären/erledigen:

- Rechnung über `30,00 €` als kontrollierte E-Mail vorbereiten;
- E-Mail nach jedem Lauf wieder ungelesen setzen;
- Banking-App über den Home-Button „Überweisung“ öffnen;
- alle vier Felder ausfüllen;
- „Weiter“ und danach „Überweisung senden“ ausführen;
- korrekter Lauf endet bei `10,00 €`;
- Fehlerlauf zahlt `80,00 €` und endet bei `−40,00 €`;
- Endzustand über `Study Vendor GmbH` plus Saldo verifizieren;
- nach jedem Lauf Broadcast-Reset oder Experimenter-Reset ausführen.

---

## P1 — Seed- und Reset-System bauen

Der größte praktische Risikofaktor sind nicht die Fragebögen, sondern inkonsistente Gerätezustände zwischen Teilnehmenden.

### Benötigt

- Eine zentrale Reset-Checkliste mit den neuen kanonischen Daten.
- Wenn möglich ein Skript, z. B. `mcp-server/scripts/reset_study_device.ps1`, das mindestens:
  - Study-Bank per Broadcast zurücksetzt;
  - Caddie stoppt und in definierten Zustand bringt;
  - DND deaktiviert und Studienregel entfernt;
  - Lautstärke/Helligkeit/Screen-Timeout auf Standard setzt;
  - relevante Apps beendet;
  - ADB-Verbindung und `adb reverse tcp:8787 tcp:8787` prüft.
- App-spezifische manuelle Schritte bleiben als Checkliste erhalten:
  - E-Mails wieder ungelesen;
  - gesendete Nachricht entfernen;
  - Spotify-Favorit entfernen;
  - erstellte Notiz löschen;
  - Kalendertermine zurücksetzen;
  - Whiteboard-Foto als neuestes relevantes Foto sicherstellen.

### Akzeptanz

Nach dem Reset muss ein automatischer Preflight den Ausgangszustand bestätigen. Erst dann darf die nächste Aufgabe oder Person starten.

---

## P1 — C1, C2 und C3 auf dem echten Gerät prüfen

Für jede der sechs Aufgaben mindestens einmal in jeder Bedingung testen.

### C1 — hohe/fortlaufende Aufsicht

- jeder relevante bzw. konsequente Schritt wird vor der Ausführung bestätigt;
- Ablehnen stoppt oder korrigiert den Schritt;
- kein Schritt wird doppelt ausgeführt;
- Fehler ist im Dialog sichtbar, aber nicht angekündigt.

### C2 — zusammengefasste/finale Aufsicht

- nur der vorgesehene zusammengefasste/finale Checkpoint erscheint;
- Zusammenfassung enthält die wirklich geplanten Werte;
- Fehlerwert muss vor dem Commit sichtbar sein;
- Akzeptieren/Ablehnen wird korrekt geloggt.

### C3 — mehr Autonomie

- keine verpflichtende Bestätigung;
- aktuelle Aktion bleibt als minimale Prozesstransparenz sichtbar;
- freiwilliges Eingreifen/Stoppen bleibt möglich;
- Eingriffsfenster ist lang und sichtbar genug;
- nach einem Stop darf nichts im Hintergrund weiter ausgeführt werden.

### Rotation

- Jede Person bearbeitet sechs verschiedene Aufgaben genau einmal.
- Je zwei Aufgaben bilden ein Paar.
- Die Paare werden über C1/C2/C3 rotiert.
- Matrix und Material müssen dieselbe Paarzuordnung verwenden.
- Mit mindestens sechs Test-Teilnehmer-IDs einmal trocken durchrotieren und prüfen, ob jede Paar/Bedingung-Kombination gleich häufig vorkommt.

---

## P2 — Screen-off/C4 erst nach den Kernaufgaben

C4 untersucht eine zeitversetzt startende Aufgabe bei ausgeschaltetem Bildschirm:

- nur Benachrichtigung;
- Bildschirm aktivieren und nachfragen;
- Bildschirm aktivieren und selbstständig ausführen.

Dieser Teil darf die Fertigstellung der sechs Kernaufgaben nicht blockieren.

Vorgehen:

1. Kernstudie ohne C4 vollständig zeitlich messen.
2. Einen Screen-off-Testlauf implementieren und messen.
3. Nur wenn Gesamtzeit und technische Stabilität passen, als explorative C4-Bedingung aufnehmen.
4. Sonst ausschließlich als Ausblick behandeln.

---

## P1 — Materialien synchronisieren

Alle Texte müssen denselben finalen Task- und Fehlerstand verwenden:

- `mcp-server/docs/dossier/05-studiendesign.md`
- `mcp-server/study/materials/03_aufgabenkarten.md`
- `mcp-server/study/materials/08_er experimenter-laufzettel.md`
- `mcp-server/study/materials/09_reset_checkliste_pro_aufgabe.md`
- `mcp-server/study/materials/10_pilot_checkliste_und_zeitmessung.md`
- `mcp-server/study/materials/11_c4_bildschirm_aus.md`
- alle sechs `task_*.yaml`

Besonders prüfen:

- Banking überall 30/80 € statt 38,70/83,70 €;
- „Whiteboard“ statt „Weißbild“;
- genaue App-Namen und Empfänger;
- TAM PU/PEOU nach jeder Bedingung, nicht nur am Studienende;
- SoAS bleibt entfernt;
- keine Fehlerhinweise in den Teilnehmer-Aufgabenkarten;
- Debriefing erklärt die kontrollierten Fehler erst nach Abschluss;
- Fragebögen aus der finalen Figma-PDF drucken, Originalwortlaut und Antwortskalen unverändert lassen.

---

## P1 — Pilot zuhause vor dem ersten Probanden

Mindestens ein kompletter Selbst-Pilot mit Video/Screenrecording und Stoppuhr:

1. Einwilligung und Einführung laut vorlesen.
2. Training durchführen.
3. Sechs Aufgaben gemäß echter Matrix durchführen.
4. Nach jeder Bedingung den vollständigen vorgesehenen Fragebogen ausfüllen.
5. TAM, Demografie, Ranking und Abschlussinterview durchführen.
6. Jeden Reset so ausführen, als würde direkt die nächste Person beginnen.
7. C4 separat messen.

Erfassen:

- Dauer je Aufgabe;
- Dauer je Bedingung inklusive Fragebogen;
- Anzahl manueller Hilfen;
- Replay-Fallbacks;
- falsche/mehrdeutige UI-Ziele;
- Fehler, die zu leicht oder gar nicht bemerkt werden;
- Stellen, an denen Teilnehmer nicht wissen, ob die Aufgabe fertig ist;
- technische Reset-Zeit zwischen Personen.

Danach mindestens drei externe Pilotpersonen. Wenn sechs Aufgaben deutlich zu kurz sind, zusätzliche Tasks erst nach der Messung ergänzen; nicht vorher den Kernumfang unnötig vergrößern.

---

## Definition of Done für Montag

Die Studie ist nur dann bereit, wenn alle Punkte erfüllt sind:

- [ ] Finaler Satz aus genau sechs Kernaufgaben bestätigt
- [ ] Jede Task-Spec enthält nur ausführbare Executor-Aktionen
- [ ] Jede Aufgabe läuft fünfmal hintereinander ohne manuelle Rettung
- [ ] Jede Aufgabe wurde in C1, C2 und C3 getestet
- [ ] Korrekter und fehlerhafter Pfad sind pro Aufgabe geprüft
- [ ] Stop/Intervention wurde pro Bedingung geprüft
- [ ] Endzustand wird automatisch und eindeutig verifiziert
- [ ] Reset wurde nach jeder Aufgabe erfolgreich geprüft
- [ ] Alle Seed-Daten sind vorhanden und frei von privaten Daten
- [ ] Banking-Formular ist visuell fertig und enthält mehrere Basis-Umsätze
- [ ] Alle Materialien verwenden dieselben Aufgaben, Beträge und Fehler
- [ ] Figma-Fragebogen-PDF ist final und gedruckt
- [ ] Selbst-Pilot vollständig inklusive Zeitmessung durchgeführt
- [ ] Mindestens ein externer Pilot ohne technische Hilfe durchgelaufen
- [ ] Gesamtzeit liegt im vorgesehenen Rahmen
- [ ] C4-Entscheidung anhand echter Zeitmessung getroffen
- [ ] Finale APKs frisch installiert
- [ ] ADB, Server, Overlay, Accessibility, Mikrofon und Benachrichtigungen im Preflight grün
- [ ] Ersatzplan bei App-/Netzwerkausfall vorbereitet

---

## Empfohlene Arbeitsreihenfolge

1. Task-Satz und konkrete Apps/Packages endgültig einfrieren.
2. Banking-Formular und Basis-Umsätze fertigstellen.
3. Seed-Daten für alle sechs Aufgaben auf dem Pixel vorbereiten.
4. Jede Aufgabe einmal manuell durchführen und UI-Ziele dokumentieren.
5. YAML-Aktionen in ausführbare Einzelschritte umschreiben.
6. Aufgabe für Aufgabe Replay/Executor stabilisieren und jeweils fünfmal testen.
7. Reset-Skript und manuelle Reset-Checkliste fertigstellen.
8. C1/C2/C3 je Aufgabe testen.
9. Materialien synchronisieren und Figma-PDF drucken.
10. Kompletten Selbst-Pilot messen.
11. Probleme beheben und den vollständigen Lauf wiederholen.
12. Externe Pilotpersonen testen.
13. Erst danach über zusätzliche Aufgaben oder C4 entscheiden.

---

## Nützliche Befehle

### Caddie frisch installieren

```powershell
adb uninstall com.caddie
.\gradlew.bat assembleDebug
adb install app\build\outputs\apk\debug\app-debug.apk
adb reverse tcp:8787 tcp:8787
```

Danach Accessibility, Overlay, Mikrofon und Benachrichtigungen prüfen bzw. erteilen.

### Study-Bank bauen und installieren

```powershell
.\gradlew.bat :mcp-server:study-bank:assembleDebug
adb uninstall com.caddie.studybank
adb install mcp-server\study-bank\build\outputs\apk\debug\study-bank-debug.apk
adb shell am start -W -n com.caddie.studybank/.BankingActivity
```

### Study-Bank Gerätetests

```powershell
.\gradlew.bat :mcp-server:study-bank:connectedDebugAndroidTest
```

### Study-Mail bauen und installieren

```powershell
.\gradlew.bat :mcp-server:study-mail:assembleDebug
adb uninstall com.caddie.studymail
adb install mcp-server\study-mail\build\outputs\apk\debug\study-mail-debug.apk
adb shell am start -W -n com.caddie.studymail/.StudyMailActivity
```

### Fake-Apps vor einer Aufgabe zurücksetzen

```powershell
adb shell am broadcast -a com.caddie.studybank.ACTION_RESET -p com.caddie.studybank
adb shell am broadcast -a com.caddie.studymail.ACTION_RESET -p com.caddie.studymail
```

Die Apps enthalten bewusst keine sichtbaren Reset-Schaltflächen. Study Mail ersetzt Gmail in T4 und T6 vollständig; die zwei Zielmails sind nach dem Reset wieder ungelesen.

Falls der Gradle-Testinstaller einen Signaturkonflikt meldet, zuerst die Haupt-APK direkt installieren und den Test erneut starten.

### Python-Tests

```powershell
cd mcp-server
python -m pytest tests/test_study_spec_loader.py tests/test_study_preflight.py -q
```

### Preflight

```powershell
cd mcp-server
python -m caddie.study.cli preflight --participant PILOT01
```

---

## Bekannte Risiken

- Die aktuelle Testabdeckung verwendet an vielen Stellen Fake-Backends; echte Android-End-to-End-Läufe sind weiterhin erforderlich.
- Reale Maps-/Spotify-Inhalte können sich ändern und Replays destabilisieren; T4/T6 verwenden dagegen die lokale deterministische Study-Mail-App.
- Pixel-Systemeinstellungen können nach Android-Updates andere Texte oder Hierarchien verwenden.
- Riskante Replay-Schritte werden absichtlich nicht automatisch ohne Gate ausgeführt.
- Der Arbeitsbaum enthält viele noch nicht commitete Änderungen. Nur logisch zusammengehörige Dateien gezielt stagen.
- Build-Verzeichnisse, temporäre Screenshots, `.superpowers/`, Logs und `tmp/` nicht versehentlich committen.
- Die Banking-App verwendet ein Sparkassen-nahes Mock-Design; sie darf ausschließlich als klar kontrollierte Offline-Studien-App verwendet werden.

## Verantwortungsgrenze

- Andreas bereitet die E-Mails und Account-Inhalte selbst vor.
- Der nächste Entwicklungsagent soll Code, Task-Specs, Replays, Reset-Automation und technische Verifikation übernehmen.
- Änderungen an persönlichen Accounts, echten Nachrichten oder Kalenderdaten nur nach ausdrücklicher Freigabe.

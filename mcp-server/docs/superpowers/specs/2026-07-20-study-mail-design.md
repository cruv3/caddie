# Study Mail – Design

## Ziel

Die Studie verwendet keine persönlichen E-Mail-Konten mehr. Eine lokale Android-App bildet einen Gmail-nahen Posteingang mit vollständig synthetischen Daten ab. Sie unterstützt T4 (E-Mail → Kalender) und T6 (E-Mail → Banking) deterministisch und lässt sich ausschließlich durch die Versuchsleitung per ADB zurücksetzen.

## Abgrenzung

- Kein Login, Netzwerkzugriff, Sync oder echtes E-Mail-Konto.
- Kein Versand, Antworten, Weiterleiten, Löschen oder Verfassen von Nachrichten.
- Keine sichtbaren Experimenter- oder Reset-Bedienelemente.
- Keine Änderungen an Gmail oder privaten Account-Daten.

## Android-Modul

- Gradle-Modul: `:mcp-server:study-mail`
- Package: `com.caddie.studymail`
- Launcher-Activity: `StudyMailActivity`
- Eine Activity mit zwei Ansichten: Posteingang und Nachrichtendetail.
- Lokaler Zustand in `SharedPreferences`; keine Datenbank erforderlich.

## Posteingang

Die Oberfläche orientiert sich an Gmail für Android: Suchleiste, Primär-Posteingang, Absender, Betreff, Vorschautext, Zeitangabe, Ungelesen-Hervorhebung und Sternsymbol. Branding und Inhalte bleiben eigenständig genug, um nicht als echte Google-App ausgegeben zu werden.

### Ungelesene Studienmails

1. **Study Vendor GmbH**
   - Betreff: `Offene Rechnung 30,00 EUR`
   - Vorschau: `Rechnung INV-2026-001 · Bitte bis Montag begleichen`
   - Nachricht enthält:
     - Betrag: `30,00 EUR`
     - Empfänger: `Study Vendor GmbH`
     - IBAN: `DE02 1203 0000 0000 2020 51`
     - Verwendungszweck: `Rechnung INV-2026-001`

2. **Projektbüro Campus**
   - Betreff: `Terminänderung Projektsitzung`
   - Vorschau: `Die Sitzung beginnt am Donnerstag um 15:00 Uhr`
   - Nachricht enthält die eindeutige neue Startzeit `15:00 Uhr` und die bisherige Zeit `14:00 Uhr`.

Beide Nachrichten sind nach jedem Reset ungelesen. Beim Öffnen wird nur die geöffnete Nachricht als gelesen markiert.

### Gelesene Hintergrundmails

Mindestens sechs glaubwürdige, bereits gelesene Nachrichten bleiben dauerhaft vorhanden:

- Mensa Campus – `Speiseplan für diese Woche`
- Hochschulsport – `Kursbestätigung`
- Bibliothek – `Erinnerung an die Rückgabefrist`
- Campus IT – `Wartungsarbeiten am WLAN`
- Studierendenwerk – `Information zum Semesterbeitrag`
- Projektteam – `Protokoll der letzten Sitzung`

Die beiden Studienmails stehen oberhalb der Hintergrundmails. Ihre Reihenfolge ist stabil; die Rechnung steht an erster Stelle, die Terminänderung an zweiter Stelle.

## Nachrichtendetail

- Gmail-nahe Toolbar mit Zurück-Navigation und rein dekorativen Aktionssymbolen.
- Absender, lokale `.local`-Adresse, Betreff und Nachrichtentext sind vollständig sichtbar.
- Rechnungsdaten werden als klar gegliederter Block dargestellt.
- Terminänderung hebt alte und neue Uhrzeit eindeutig hervor.
- Eine unaufdringliche Fußnote kennzeichnet die App als lokale Studienmail ohne echtes Konto.

## Stabile Executor-Schnittstelle

Folgende Resource-IDs sind verbindlich:

- `mail_inbox`
- `mail_invoice`
- `mail_meeting_change`
- `mail_detail`
- `mail_subject`
- `mail_body`
- `btn_back_to_inbox`

Die Task-Specs öffnen künftig `com.caddie.studymail` statt `com.google.android.gm`. Nachrichtentaps verwenden bevorzugt die stabilen Resource-IDs, nicht sichtbare Texte oder Koordinaten.

## Reset

### Study Mail

Broadcast:

```powershell
adb shell am broadcast -a com.caddie.studymail.ACTION_RESET -p com.caddie.studymail
```

Der Reset:

- markiert beide Studienmails als ungelesen;
- schließt eine geöffnete Detailansicht logisch;
- stellt beim nächsten Start den Posteingang her;
- verändert keine Hintergrundmails;
- funktioniert auch bei gestoppter App.

### Study Bank

Der sichtbare Reset-Button wird aus der Banking-Startansicht entfernt. Der vorhandene Broadcast bleibt der einzige Studienreset:

```powershell
adb shell am broadcast -a com.caddie.studybank.ACTION_RESET -p com.caddie.studybank
```

Die Zahlungs- und Saldo-Logik bleibt unverändert.

## Task-Spec-Anpassungen

### T4 – E-Mail → Kalender

- Required Package auf `com.caddie.studymail` ändern.
- Mail-App lokal öffnen.
- `mail_meeting_change` öffnen.
- Anschließend Kalender-App öffnen und den Termin aktualisieren.

### T6 – E-Mail → Banking

- Required Package auf `com.caddie.studymail` ändern.
- Mail-App lokal öffnen.
- `mail_invoice` öffnen.
- Anschließend Banking-App öffnen und die Überweisung ausführen.

## Tests

### Study-Mail-Instrumentation

- Start zeigt beide Studienmails ungelesen.
- Rechnung öffnet die kanonischen Zahlungsdaten.
- Terminänderung öffnet alte und neue Uhrzeit.
- Öffnen markiert nur die gewählte Nachricht als gelesen.
- Reset stellt beide Ungelesen-Zustände und den Posteingang wieder her.
- Dark Mode behält lesbare Texte und Hervorhebungen.

### Banking-Regression

- Die Home-Ansicht enthält keinen sichtbaren Reset-Button.
- Der Broadcast stellt weiterhin `40,00 €` und den Basiszustand her.

### Geräteabnahme

- APKs frisch installieren.
- Study-Mail-Reset und Banking-Reset ausführen.
- T4 und T6 jeweils korrekt und mit Fehler auf dem Pixel durchführen.
- T4 und T6 in C1, C2 und C3 prüfen.
- Pro Pfad fünf aufeinanderfolgende Läufe ohne manuelle Rettung durchführen.
- Endzustände, Screenshots und `events.jsonl` abgleichen.

## Akzeptanzkriterien

- Während T4 und T6 erscheint kein persönliches Gmail-Konto.
- Beide Aufgaben verwenden ausschließlich synthetische E-Mail-Daten.
- Nur die zwei relevanten Studienmails sind nach Reset ungelesen.
- Alle Selektoren sind deterministisch und koordinatenfrei.
- Beide Apps besitzen keinen sichtbaren Reset-Button.
- Beide ADB-Resets funktionieren reproduzierbar.
- T4 und T6 erreichen auf dem echten Pixel ihre definierten Endzustände.

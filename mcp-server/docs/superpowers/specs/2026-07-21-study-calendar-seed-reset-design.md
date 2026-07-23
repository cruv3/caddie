# Studien-Kalender Seed/Reset – Design

## Ziel

Für T4 existiert auf dem Studiengerät ein echter Google-Calendar-Termin `Projektsitzung`, der vor jedem Lauf zuverlässig auf den aktuellen lokalen Tag des Pixels, 14:00–15:00 Uhr, zurückgesetzt wird. Der Reset darf keine anderen Kalender oder Termine verändern.

## Kalenderwahl

Der Termin wird in einem vorhandenen, sichtbaren und beschreibbaren Google-Kalender gespeichert. Das Reset-Skript erhält die Kalender-ID explizit; es wählt bei mehreren persönlichen Kalendern niemals selbstständig einen Kalender aus. Vor jeder Änderung prüft es, dass die ID weiterhin zu einem sichtbaren Google-Kalender gehört.

## Eindeutige Identität

Der Studien-Termin trägt:

- Titel: `Projektsitzung`
- Beschreibung: `CADDIE_STUDY_T4_V1`
- Zeitzone: aktuelle IANA-Zeitzone des Pixels
- Start: heutiger lokaler Gerätetag, 14:00
- Ende: heutiger lokaler Gerätetag, 15:00

Die Beschreibung ist der technische Marker. Suche, Update und Bereinigung verwenden Kalender-ID plus exakten Marker. Titel oder Datum allein reichen niemals als Lösch- oder Updatekriterium.

## Reset-Ablauf

1. ADB starten, genau ein autorisiertes Gerät verlangen und dessen ISO-Datum plus IANA-Zeitzone lesen.
2. Kalender-ID abfragen und Google-Kontotyp, Sichtbarkeit und Schreibbarkeit prüfen.
3. Alle Events mit exaktem Marker lesen und zusätzlich auf die angegebene Kalender-ID begrenzen.
4. Kein Treffer: Termin neu anlegen und anschließend über Googles eigene Bearbeiten-/Speichern-UI synchronisieren.
5. Ein Treffer am heutigen Gerätetag: den exakten Event per URI öffnen und seine Startzeit über die Google-Calendar-UI auf 14:00 setzen; Google verschiebt das Ende automatisch auf 15:00. Ein Marker von einem anderen Tag wird ausschließlich markerbasiert neu aufgebaut.
6. Mehrere Treffer: nur die markierten Treffer in diesem Kalender entfernen, genau einen Seed-Termin neu anlegen und ihn über Googles UI speichern.
7. Abschließend erneut abfragen und exakt einen aktiven Treffer mit den erwarteten Werten, `dirty=0` und einer Google-`_sync_id` verlangen.
8. Google Calendar in die Terminübersicht zurücksetzen, `Zu heute springen` verwenden und den Seed-Zustand über Accessibility sichtbar prüfen.

Jeder unerwartete Kalenderzustand führt vor einer Änderung zum Abbruch. Fremde Events und andere Kalender werden nicht angefasst.

## Schnittstellen

`mcp-server/scripts/reset_study_calendar.ps1` ist das ausführbare Reset-Werkzeug. Es akzeptiert mindestens `-CalendarId`, optional `-Serial`, und besitzt einen `-VerifyOnly`-Modus. Die reine Planung und Validierung der Provider-Zeilen liegt in einem kleinen Python-Modul, damit Schutzregeln und Zeitwerte ohne Gerät getestet werden können. PowerShell verwendet Provider-Mutationen nur für fehlende oder doppelte Marker-Termine; bestehende Termine werden semantisch über Googles eigene UI zurückgesetzt und synchronisiert.

## Tests und Abnahme

Unit-Tests decken Zeitumrechnung, fehlenden/einzelnen/doppelten Marker, falsche Kalender-ID und fremde Events ab. Die Geräteabnahme führt Seed, Änderung auf 15:00–16:00 und Reset auf 14:00–15:00 aus. Danach müssen Provider-Abfrage und sichtbarer Google-Calendar-Text denselben Zustand bestätigen.

## Nicht enthalten

- Änderungen an unmarkierten persönlichen Terminen
- automatische Änderung von Gerätedatum oder Gerätezeitzone
- T5-Prüfungstermin oder DND-Reset
- Cloud- oder Account-Konfiguration

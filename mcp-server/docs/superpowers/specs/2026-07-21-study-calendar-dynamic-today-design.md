# Dynamischer Studien-Kalendertag

## Ziel

Der Studien-Kalendertermin `Projektsitzung` wird bei jedem Reset auf dem aktuellen lokalen Kalendertag des verbundenen Pixels für 14:00–15:00 Uhr angelegt oder zurückgesetzt. Dadurch ist die Aufgabe nicht mehr an den 23. Juli 2026 gebunden.

## Datumsquelle

Das Reset-Skript liest Datum und Zeitzone direkt vom ausgewählten Android-Gerät. Der Rechner ist nicht autoritativ. Aus dem lokalen Gerätedatum und der Gerätezeitzone berechnet der Python-Reset die erwarteten Start- und End-Epochen für 14:00 und 15:00 Uhr.

Das Skript bricht fehlgeschlossen ab, wenn Datum oder Zeitzone nicht eindeutig gelesen oder von Python nicht verarbeitet werden können. Es verändert weder Gerätedatum noch Gerätezeitzone.

## Seed und Reset

Der bestehende Marker `CADDIE_STUDY_T4_V1`, die explizite `CalendarId` und die Prüfung auf einen sichtbaren, beschreibbaren Google-Kalender bleiben erhalten. Nur markierte Studientermine dürfen gelöscht, neu angelegt oder über die Google-Calendar-Oberfläche zurückgesetzt werden.

Der Sollzustand ist genau ein synchronisierter markierter Termin am heutigen Gerätetag von 14:00–15:00 Uhr. Ein bereits markierter Termin von einem früheren Tag wird auf heute verschoben. Duplikate werden weiterhin ausschließlich anhand des Markers bereinigt.

## Aufgabenablauf

Nach dem Öffnen von Google Calendar wählt der Executor die auf dem Studien-Pixel bestätigte semantische Aktion `click 'Zu heute springen'`. Anschließend öffnet er `Projektsitzung` und ändert nur die Startstunde von 14 auf 15 Uhr. Google Calendar verschiebt die Endzeit automatisch auf 16 Uhr.

Der feste Accessibility-Selektor `Donnerstag 23 Juli 2026, Terminübersicht öffnen` entfällt. Es werden keine Koordinaten und keine sprachabhängig generierten Datumslabels gespeichert.

## Verifikation

Die Python-Verifikation erhält das lokale Gerätedatum und die Gerätezeitzone als explizite Eingaben. Sie akzeptiert nur einen aktiven markierten Termin mit den daraus berechneten Epochen, `dirty=0` und gesetzter `_sync_id`.

Automatisierte Tests decken mindestens Folgendes ab:

- Berlin-Sommerzeit und einen Wintertag;
- Monats- und Jahreswechsel;
- Ablehnung ungültiger Datums- oder Zeitzoneneingaben;
- dynamische Provider-Binds und Seed-Verifikation;
- Fehlen des fest codierten Datums in PowerShell und Task-Spec;
- semantische Auswahl von `Zu heute springen` vor `Projektsitzung`.

Die Geräteabnahme verändert den Termin auf 15:00–16:00 Uhr, führt den Reset aus und bestätigt anschließend sichtbar und über den Calendar Provider den heutigen Zustand 14:00–15:00 Uhr.

## Nicht im Umfang

- automatische Änderung von Datum oder Zeitzone des Pixels;
- Auswahl eines Kalenders ohne explizite `CalendarId`;
- Änderungen an persönlichen, nicht markierten Terminen;
- dynamische Uhrzeiten oder eine Verschiebung auf morgen.

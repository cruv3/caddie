/*
 * Clip-Manifest. Jede Szene spricht Videos nur über diese Schlüssel an.
 *
 * Felder:
 *   src      Pfad relativ zu index.html
 *   label    Name für Platzhalter und Debug-Anzeige
 *   start    Startzeit in Sekunden (Standard 0). Für den Dreier-Vergleich: Ende des Sprachbefehls.
 *   end      optional: hier anhalten
 *   rate     optional: Abspielgeschwindigkeit (1 = normal)
 *   <name>   beliebige benannte Zeitpunkte (Sekunden), z. B. gate: 54.0
 *   regions  benannte Bildschirmbereiche für Zoom/Markierung, normiert 0–1 {x, y, w, h}
 *
 * Zeiten ablesen: Präsentation öffnen, Taste D (Debug) zeigt die laufende Videozeit.
 * Fehlende Dateien erscheinen in der Präsentation als gestreifter Platzhalter.
 * Videos werden nicht ins Git eingecheckt (siehe .gitignore).
 */
Deck.clips = {
  coldOpen: {
    src: 'footage/cold-open.mp4',
    label: 'Cold Open · Abgabe per Webmail',
    gate: 54.0, // konservatives Standbild; erster sichtbarer Dialog-Frame: PTS 53.957533 s
    gateFrame: 53.957533, // Mindest-PTS des tatsächlich dargestellten Dialogbilds
    regions: {
      gate: { x: 0.06, y: 0.34, w: 0.88, h: 0.35 },
      recipient: { x: 0.02, y: 0.155, w: 0.96, h: 0.06 },
      attachment: { x: 0.08, y: 0.845, w: 0.52, h: 0.06 },
    },
  },

  /* Dreh vom 12.10.2026, Originalaufnahmen (1080 × 2400, ohne Ton), nicht neu kodiert.
     Ausnahme Whiteboard: Präsentationsexport aus clip4_whiteboard_take2 mit 1 s Standbild ab 27,8 s.
     start = erster Frame, in dem die „listening…“-Punkte zu einem Punkt werden (Ende der
     Spracheingabe; die Videos haben keinen Ton). Gleiche Definition in allen vier Clips.
     <name>Frame = Mindest-PTS des tatsächlich dargestellten Bildes (für hasFrame/waitFor).
     Vorführdauer ab start: C1 37,5 s · C2 43,0 s · C3 66,7 s (ab ~40 s nur noch Standbild).
     Keine Laufzeitmessung: drei verschiedene Aufgaben, nachgestellte Takes. */
  c1: {
    src: 'footage/clip1_c1_takeA.mp4',
    label: 'C1 · Jeder Schritt (Take A)',
    start: 114.135, // Sprachende: Frame 114.132722; erstes Gate „Telegram öffnen“ ab 114.24
    wrongSong: 140.8, // Gate „„As It Was – Sped Up“ zu Meine Bibliothek hinzufügen“, steht 140.19–141.85
    wrongSongFrame: 140.2038,
    done: 148.5, // Toast „Aufgabe abgeschlossen.“ 148.437–151.31; Clipende 151.68
    regions: {
      dialog: { x: 0.06, y: 0.335, w: 0.88, h: 0.36 },
      song: { x: 0.1, y: 0.41, w: 0.8, h: 0.06 }, // Zeile „As It Was – Sped Up … hinzufügen“
    },
  },
  // Take B wird von keiner Szene genutzt und liegt bewusst nicht in footage/.
  c1b: { src: 'footage/clip1_c1_takeB.mp4', expectedMissing: true, label: 'C1 · Take B (abgelehnt)', start: 0 },
  c2: {
    src: 'footage/clip2_c2.mp4',
    label: 'C2 · Am Ende',
    start: 73.402, // Sprachende: Frame 73.399056
    gate: 99.0, // „Aktion prüfen … 80,00 EUR“ steht hart ab 98.091233 bis 104.99 (Ablehnung 105.016)
    gateFrame: 98.091233,
    regions: {
      dialog: { x: 0.065, y: 0.3, w: 0.87, h: 0.43 }, // gemessen 0.0685–0.9306 × 0.303–0.727
      amount: { x: 0.34, y: 0.394, w: 0.32, h: 0.04 }, // „80,00 EUR“: 0.415–0.585 × 0.406–0.421; Zoom ~2,9× lässt „Aktion prüfen“ ganz sichtbar
    },
  },
  c3: {
    src: 'footage/clip3_c3_takeA.mp4',
    label: 'C3 · Keine Pflichtfreigabe (Take A)',
    start: 56.36, // Sprachende: Frame 56.357456; Maps mit Ziel Köln Hauptbahnhof ab ~59.8
    wrongRoute: 62.0, // v3 „Eingreifen“: Maps zeigt Route zu „Köln Hauptbahnhof“ (Frame bei 62 s geprüft)
    // Ein Fingertipp ist im Video nicht erkennbar (kein Touch-Indikator). Sichtbar ist nur:
    intervene: 74.390444, // Eingriff: Overlay wechselt im Chat zu „listening…“ (Text „18 Min.“ schon eingetippt)
    corrected: 78.0, // „Korrektur übernommen“ (2-fps-Raster: 77.5 noch nicht, 78.0 sichtbar)
    newRoute: 82.42, // Ziel „Technische Hochschule Köln – Camp…“, 1 h 25 min
    sent: 93.48, // Nachricht „… in 1 Std. 25 Min. am Campus Deutz.“ gesendet; danach Standbild bis 123.06
    regions: {
      destination: { x: 0.06, y: 0.118, w: 0.9, h: 0.052 }, // Zielzeile in Maps
      route: { x: 0.048, y: 0.065, w: 0.904, h: 0.106 }, // Start-/Zielkarte in Maps
    },
  },
  c3b: { src: 'footage/clip3_c3_takeB.mp4', expectedMissing: true, label: 'C3 · Take B (nicht bemerkt)', start: 0 },
  whiteboard: {
    src: 'footage/clip4_whiteboard.mp4',
    label: 'Finden Sie den Fehler · Whiteboard',
    start: 22.775, // Sprachende: Frame 22.771443
    photo: 28.3, // Foto hart ab 27.201318, sichtbar bis 29.78; 28.3 liegt im Export-Standbild
    photoFrame: 27.201318,
    noteDone: 40.0, // fertige Notiz in der Liste ab 39.9443; Clipende 47.28
    noteDoneFrame: 39.9443,
    regions: {
      photo: { x: 0.014, y: 0.342, w: 0.972, h: 0.328 }, // Whiteboard im Foto-Viewer
      note: { x: 0.03, y: 0.245, w: 0.94, h: 0.1 }, // gelbe Notizkarte: 0.040–0.960 × 0.252–0.338
    },
  },
};

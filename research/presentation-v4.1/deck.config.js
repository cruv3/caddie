/*
 * Reihenfolge der Szenen. Jede Zeile ist eine Datei in scenes/ (ohne .js).
 * Szene entfernen: Zeile auskommentieren. Neue Szene: Datei anlegen und hier eintragen.
 * Backups erscheinen nur in der Übersicht (Taste G), nicht im normalen Ablauf.
 * Der Sprechtext je Szene kommt aus script.js (erzeugt aus research/sprechtext-v4.1/Manuskript.md).
 */
Object.assign(Deck.config, {
  theme: 'dark', // 'dark' oder 'light'; live umschalten mit Taste T
  scenes: [
    // Cold Open
    '01-cold-open',
    '02-titel',
    // Akt 1 · Die Lage
    '03-agents',
    '05-erfolg-gemeldet',
    '06-frage',
    // Akt 2 · Technik
    '07-loop',
    '08-semantik',
    '10-gate',
    '11-eingreifen',
    '13-grenzen-technik',
    // Akt 3 · Studie
    '15-kontrollmomente',
    '16-drei-handys',
    '17-fehler-c2',
    '18-studie',
    '19-aufgaben',
    '20-findet-den-fehler',
    '21-ergebnis-entscheidungen',
    '22-ergebnis-erkennung',
    '24-ergebnis-praeferenz',
    '25-stimmen',
    '26-grenzen-studie',
    // Schluss
    '27-folgerung',
    '28-beitrag',
    '29-future-work',
    '30-zurueck-zum-anfang',
    '31-ende',
  ],
  backups: [
    // Technik
    'b01-modellaufruf',
    'b02-datenfluss',
    'b03-sprache',
    'b04-swipe',
    'b05-fehler-recovery',
    'b06-retrieval',
    'b07-memory',
    'b08-mcp',
    'b09-gui-alternativen',
    'b10-jev-latenz',
    // Studie und Demo
    'b11-methodik',
    'b12-praeferenz-matrix',
    'b13-belastung',
    'b14-demo-hinweis',
    '23-ergebnis-folgen', // B15, Dateiname aus der früheren Hauptposition
    // Frühere Hauptfolien (v4.1), Dateinamen und Szenen-IDs unverändert
    '04-landkarte', // B16
    '09-warum-nicht', // B17
    '12-wissen', // B18
    '14-alltag-studie', // B19
  ],
});

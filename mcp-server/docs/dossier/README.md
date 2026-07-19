# Projekt-Dossier - LLMSmartphone (V2)

Ausfuehrliche Lese-Dokumentation zum Einlesen. Anders als
`docs/meeting-vorstellung.md` (Stichpunkte fuers Meeting) geht dieses Dossier in
die Tiefe: Projekt, Architektur, verwandte Arbeiten (Paper im Detail), Studien
und Befunde, Studien-Design.

Stand 2026-07-19.

## Lese-Reihenfolge

1. `01-projekt.md` - Worum geht es, Forschungsfrage, Geschichte (V1 -> V2), Status.
2. `02-architektur.md` - Wie das System technisch funktioniert, Ende zu Ende.
3. `03-related-work.md` - Verwandte Arbeiten und Produkte, je: was es ist, wie sie es gemacht haben, Relevanz fuer uns.
4. `04-studien-befunde.md` - Failure-Mode-Studie, Claude-Vergleich, Korrektur-Test, mit Methodik und Zahlen.
5. `05-studiendesign.md` - Aktueller Stand der Nutzerstudie zu Shared Autonomy
   und User Oversight, inklusive Aufgaben, kontrollierter Fehler,
   Messinstrumente und explorativem Bildschirm-aus-Teil.

## Verweise auf bestehende Detail-Logs

- `docs/failure-mode-log.md` - Roh-Trials der Failure-Mode-Studie (72 Trials, pro Trial Outcome + Screenshot).
- `docs/claude-vs-local.md` - Claude-Opus-Vergleich.
- `docs/correction-test-log.md` - Korrektur-Test (beide Modelle, Vorher/Nachher, Belege).
- `docs/architecture.md` - knappe Architektur-Referenz (dieses Dossier erweitert sie erzaehlend).
- Vault: `01 Projects/LLMSmartphone/{LLMSmartphone,log,decisions,problems}.md` - strategische/Cross-Session-Ebene.

## Begriffe (Kurz-Glossar)

- **MCP** (Model Context Protocol): Standard, ueber den ein LLM-Client Werkzeuge (Tools) eines Servers aufruft.
- **Agent-Loop**: die Schleife "Modell denkt -> ruft Tool -> sieht Ergebnis -> denkt weiter", bis fertig.
- **Backend**: wie Aktionen aufs Geraet kommen - ADB (Debug-Bruecke) oder HTTP-Bridge (App + AccessibilityService).
- **Aktionsanzeige / Companion**: die sichtbare Begleit-UI auf dem Handy, die
  knapp zeigt, welche Aktion der Agent gerade ausfuehrt.
- **User Oversight**: menschliche Aufsicht ueber den Agenten; in der Studie als
  schrittweise Bestaetigung, finaler Checkpoint oder freiwilliger Eingriff ohne
  verpflichtende Bestaetigung operationalisiert.
- **Skill**: gespeichertes Markdown-Rezept ("so schaltet man Dark Mode"), das der Agent wiederverwendet.
- **Settle-Gate**: physische Wartesperre, bis sich die UI nach einer Aktion stabilisiert hat.

# 05 - Studien-Design und aktueller Arbeitsstand

Stand: 2026-07-19

## Titel und Ziel

Arbeitstitel: **Shared Autonomy - Designing User Oversight for LLM-Based
Smartphone Agents**.

Die Nutzerstudie untersucht nicht mehr verschiedene Avatar- oder
Overlay-Designs. Stattdessen besitzt der Agent in allen Bedingungen einen hohen
Automatisierungsgrad und dieselbe knappe Aktionsanzeige. Experimentell variiert
wird ausschliesslich, wie viel **verpflichtende menschliche Aufsicht** waehrend
der Ausfuehrung erforderlich ist.

## Forschungsfragen

**RQ1:** Wie beeinflusst der erforderliche Umfang menschlicher Aufsicht den
Interaktionsaufwand, die subjektive Beanspruchung und die wahrgenommene
Kontrolle bei einem transparenten Smartphone-Agenten?

**RQ2:** Wie beeinflusst die Kritikalitaet einer Aufgabe den Zielkonflikt
zwischen frei werdender Aufmerksamkeit und rechtzeitiger Fehlererkennung bei
reduzierter Aufsicht?

## Design und Bedingungen

Within-Subjects-Design mit drei Bedingungen. Der Agent zeigt in allen
Bedingungen knapp die aktuell ausgefuehrte Aktion. Die Anzeige dient der
Beobachtbarkeit und verlangt fuer sich genommen keine Bestaetigung. Ein
vorheriger Ausfuehrungsplan ist nicht Teil der Studie.

| Bedingung | Verpflichtende Aufsicht | Eingriffsmoeglichkeit |
|---|---|---|
| **C1 - schrittweise Aufsicht** | Jede folgenreiche Aktion muss bestaetigt werden. | Bestaetigen, aendern oder abbrechen. |
| **C2 - finaler Checkpoint** | Vor der endgueltigen Ausfuehrung muss eine Zusammenfassung bestaetigt werden. | Zusammenfassung aendern oder abbrechen. |
| **C3 - freiwilliger Eingriff** | Keine verpflichtende Bestaetigung. | Laufende Ausfuehrung jederzeit pausieren, aendern oder abbrechen. |

Der Agent bleibt in allen Bedingungen operativ autonom. C1-C3 variieren nur
die verpflichtenden Eingriffspunkte und operationalisieren damit verschiedene
Auspraegungen von User Oversight.

## Hypothesen

- **H1:** Erforderliche Nutzeraktionen und aktive Bearbeitungszeit sind in C1
  hoeher als in C2 und C3.
- **H2:** Die subjektive Beanspruchung ist in C1 hoeher als in C2 und C3. Der
  Vergleich C2 gegen C3 wird explorativ betrachtet, weil ein fehlender
  Checkpoint auch zusaetzliche Wachsamkeit ausloesen kann.
- **H3:** Die wahrgenommene Kontrolle ist in C1 und C2 hoeher als in C3. Fuer
  C1 gegen C2 wird keine feste Richtung angenommen.
- **H4:** Die Leistung in der parallelen Reaktionsaufgabe ist bei geringerer
  verpflichtender Aufsicht besser (C3 vor C2 vor C1).
- **H5:** Kontrollierte Fehler werden bei staerkerer Aufsicht haeufiger
  beziehungsweise frueher erkannt. Aufgrund der kleinen Anzahl an
  Fehlerereignissen wird diese Hypothese sekundaer beziehungsweise explorativ
  ausgewertet.
- **Explorativ:** Hoehere wahrgenommene Aufgabenkritikalitaet geht mit dem
  Wunsch nach staerkerer Aufsicht einher.

Die technische Modellzuverlaessigkeit ist keine abhaengige Variable, weil die
Ausfuehrung kontrolliert vorgegeben wird.

## Stichprobe und Gegenbalancierung

- Ziel: **18 vollstaendig auswertbare Personen**.
- Rekrutierung: **20-24 Personen**, um Abbrueche und technische Ausfaelle
  aufzufangen.
- Jede Person erlebt alle drei Bedingungen.
- Bedingungsreihenfolge und Zuordnung der Aufgabenpaare zu den Bedingungen
  werden mit einem balancierten Schema beziehungsweise Latin Square rotiert.
- Die Position im Versuchsablauf wird fuer die Analyse dokumentiert.

## Kontrollierte Wizard-of-Oz-/Replay-Ausfuehrung

Damit die Bedingungen vergleichbar bleiben, werden Agentenaktionen,
Navigationsschritte, Reaktionszeiten, kontrollierte Abweichungen und deren
exakte Zeitpunkte vorab definiert. Der Ablauf ist damit kontrolliert, obwohl die
Oberflaeche das Verhalten eines autonomen Agenten repraesentiert.

Die Teilnehmenden werden nicht ausdruecklich darueber informiert, dass
kontrollierte Fehler eingebaut sind. Eine solche Ankuendigung wuerde eine
kuenstlich erhoehte Fehlersuche und damit Nachfrageeffekte erzeugen. Es wird
aber auch nicht behauptet, dass der Prototyp fehlerfrei sei. Nach Abschluss
werden die kontrollierte Ausfuehrung und die Abweichungen in einem Debriefing
vollstaendig erklaert. Die unvollstaendige Vorabinformation, das Debriefing und
die Moeglichkeit, Daten anschliessend zurueckzuziehen, muessen ethisch
abgestimmt werden.

## Training vor der Hauptstudie

1. Die Person darf den Agenten fuer wenige Minuten mit einer selbst gewaehlten,
   harmlosen Aufgabe frei ausprobieren.
2. Anschliessend werden Pausieren, Aendern und Abbrechen standardisiert geuebt.
3. Die Trainingsaufgabe enthaelt keinen Agentenfehler. Eine absichtlich
   angeforderte Aenderung vermittelt die Korrekturfunktion, ohne die Person auf
   Fehlersuche zu primen.
4. Die Hauptinstruktion lautet sinngemaess: Ziel ist die Bearbeitung gemaess
   Aufgabenstellung; ob und wann eingegriffen wird, entscheidet die Person
   selbst.

## Kontrollierte Fehler

Jede Person erlebt in **drei der sechs Hauptaufgaben** einen kontrollierten
Fehler. Sechs vorbereitete Fehlerplaene werden bei N=18 jeweils dreimal
verwendet. Dadurch tritt jede Kombination aus Bedingung und Kritikalitaet ueber
die Stichprobe gleich oft mit Fehler auf.

Die Fehler liegen bewusst nicht immer genau einmal pro Block. Ein Block kann
null, einen oder zwei Fehler enthalten. So kann nach einem erkannten Fehler
nicht auf die restlichen Aufgaben geschlossen werden.

Alle Fehler folgen demselben Grundprinzip: Ein einzelner Parameter ist falsch,
der restliche Ablauf bleibt korrekt. Dadurch wird nicht die generelle
Agentenfaehigkeit, sondern das Erkennen und Behandeln einer klar definierten
Abweichung untersucht.

| Aufgabe | Kontrollierte Abweichung |
|---|---|
| Musik | Falsche Version eines Liedes oder nur zwei von drei Liedern. |
| Fotos | Falsches Foto oder aehnlich benannte, aber falsche Messenger-Gruppe. |
| Kalender | Falsche Uhrzeit oder falscher Erinnerungszeitpunkt. |
| Navigation/Messenger | Falsches Verkehrsmittel, falsche Zeit oder aehnlich benannter Kontakt. |
| Supermarkt | Falsches Produkt oder falsche Menge. |
| Banking | Zahlendreher beim Betrag, aehnlicher Empfaenger oder falscher Verwendungszweck. |

Der fehlerhafte Wert erscheint in C1 in der betreffenden Schrittbestaetigung,
in C2 im finalen Checkpoint und in C3 in der laufenden Aktionsanzeige. Fuer C3
wird im Pilot bestimmt, wie lange die Anzeige vor der Ausfuehrung sichtbar sein
muss, damit ein freiwilliger Eingriff realistisch moeglich bleibt.

Getrennt erfasst werden:

- Wurde die Abweichung erkannt?
- Wurde eingegriffen?
- Wann und auf welche Weise wurde eingegriffen?
- Wurde die fehlerhafte Aktion vor der Ausfuehrung verhindert?
- Wurde sie gegebenenfalls erst nachtraeglich korrigiert?

Nach jeder Aufgabe wird neutral gefragt: **Entsprach das Ergebnis Ihrer
urspruenglichen Aufgabenstellung?** Antwortoptionen: vollstaendig, teilweise,
nein, kann ich nicht beurteilen. Eine offene Nachfrage erfasst, was aufgefallen
ist, ohne direkt nach einem Fehler zu fragen.

## Hauptaufgaben

Sechs Cross-App-Aufgaben bilden drei Paare aus jeweils einer weniger kritischen
und einer kritischeren Aufgabe. Jede Bedingung enthaelt genau ein Aufgabenpaar.
Die Paar-Bedingungs-Zuordnung wird zwischen den Personen rotiert.

### Paar A

1. **Weniger kritisch - Chat -> Musik-App:** Drei in einem Chat genannte Lieder
   zu einer Playlist hinzufuegen.
2. **Kritischer - Galerie -> Messenger:** Drei vorbereitete private
   Geburtstagsfotos mit einem Gruss an eine bestimmte Gruppe senden.

### Paar B

3. **Weniger kritisch - E-Mail -> Kalender:** Eine mitgeteilte
   Terminverschiebung uebertragen und eine Erinnerung einrichten.
4. **Kritischer - Karten-App -> Messenger:** Die Ankunftszeit mit oeffentlichen
   Verkehrsmitteln bestimmen und an einen vorgegebenen Kontakt senden.

### Paar C

5. **Weniger kritisch - Chat -> Supermarkt-App:** Produkte und Mengen in eine
   vorbereitete Einkaufsliste uebertragen. Welche konkrete Supermarkt- oder
   Einkaufslisten-App verwendet wird, kann spaeter festgelegt werden.
6. **Kritischer - E-Mail -> Banking-Mock:** Rechnungsdaten uebernehmen und eine
   Ueberweisung vorbereiten.

Es werden ausschliesslich vorbereitete Studienkonten und simulierte Daten
verwendet. Die Banking-Aufgabe nutzt weder echtes Geld noch persoenliche
Finanzdaten. Nach jeder Aufgabe wird auf einer 7-Punkt-Skala gefragt: **Wie
schwerwiegend waeren die Folgen eines Fehlers bei dieser Aufgabe?** Damit wird
die vorgesehene Kritikalitaetsabstufung als Manipulationscheck geprueft.

Nicht mehr Teil des Kerns sind die frueheren Aufgaben Booking-Mail ->
Kalender/Notiz/Chat, Pruefung -> Wecker/Nicht-stoeren, Rezept-Checkliste und
Flyer-OCR. Sie hatten zu viele Endpunkte, vermischten mehrere Systemzustaende,
waren zu repetitiv oder brachten einen OCR-Confound ein. Einfache
Systemeinstellungen koennen als technische Demo dienen, aber nicht als
Hauptaufgabe.

## Parallele Reaktionsaufgabe

Waehrend der Agent arbeitet, bearbeiten die Teilnehmenden eine einfache
Reaktionsaufgabe. Erfasst werden Reaktionszeit und ausgelassene Signale. Damit
soll objektiv gemessen werden, ob geringere verpflichtende Aufsicht
Aufmerksamkeit fuer eine parallele Taetigkeit freisetzt.

## Messinstrumente

### Vor der Studie

- Demografische Angaben.
- Erfahrung mit Smartphone-Agenten, Sprachassistenten und generativer KI.

### Nach jeder Aufgabe

- Wahrgenommene Kritikalitaet (7 Punkte).
- Uebereinstimmung des Ergebnisses mit der Aufgabenstellung.
- Optional: gewuenschter Umfang an Aufsicht fuer diese konkrete Aufgabe.

### Nach jeder Bedingung

1. **Raw NASA-TLX:** sechs Dimensionen mentale, koerperliche und zeitliche
   Anforderung, Leistung, Anstrengung und Frustration. Verwendung der
   ungewichteten Variante auf der originalen 0-100-Skala in 5er-Schritten.
   Begruendung: Die Bedingungen unterscheiden sich in Ueberwachung,
   Unterbrechungen, Bestaetigungsaufwand und paralleler Aufmerksamkeit. Im
   Pilot werden Dauer, Verstaendlichkeit sowie Boden- und Deckeneffekte
   geprueft.
2. **Trust in Automation (TiA):** nur die bedingungssensitiven Subskalen
   Verstaendlichkeit/Vorhersagbarkeit (vier Items) und Vertrauen in Automation
   (zwei Items). Beide Subskalen werden getrennt ausgewertet; es wird kein
   Gesamtwert gebildet.
3. **Eigene Manipulationskontrolle:** drei klar als selbst entwickelt
   gekennzeichnete Einzelitems:
   - Ich hatte Kontrolle darueber, was der Agent tat.
   - Ich konnte rechtzeitig eingreifen.
   - Der Umfang der notwendigen Bestaetigungen war angemessen.
   Die Items werden einzeln und explorativ ausgewertet, nicht als validierte
   Skala.

### Einmalig am Studienende

- Vollstaendige TAM-Subskalen **Perceived Usefulness** und **Perceived Ease of
  Use** (je sechs Items). Nur der Systembezug wird auf Caddie angepasst und die
  Anpassung dokumentiert.
- Praeferenz-Ranking der Aufsichtsbedingungen.
- Kurzes semistrukturiertes Abschlussinterview.

### Bewusst entfernt

- **Sense of Positive Agency:** Die validierte deutsche Skala misst eher eine
  allgemeine, situationsuebergreifende Agency-Disposition als die konkrete
  Kontrolle in einem Versuchsblock. Eine angepasste Blockversion waere keine
  validierte Skala mehr.
- **SART:** Nicht notwendig; zusaetzliche Laenge und inhaltliche
  Ueberschneidung mit Beanspruchung, Kontrolle und objektiver
  Reaktionsaufgabe.

### Darstellung der Frageboegen

Die Teilnehmerfassung wird in Figma gestaltet und ausgedruckt. Wortlaut,
Instruktionen und Antwortanker der Originalinstrumente bleiben erhalten. Die
Instrumente werden als getrennte Abschnitte dargestellt und getrennt
ausgewertet. Eigene Items sind sichtbar gekennzeichnet. Originalfassungen und
eine Zuordnungstabelle zwischen Quelle und Studienfassung werden fuer Betreuer
und Anhang aufbewahrt.

## Explorative Erweiterung: zeitversetzte Initiierung bei ausgeschaltetem Bildschirm

Ziel ist die Untersuchung, wie eine bereits beauftragte Agentenaktion
wahrgenommen wird, wenn das Smartphone nicht mehr aktiv genutzt wird. Nach der
Aufgabenvergabe wird das Geraet abgelegt und der Bildschirm schaltet sich aus.
Nach einer Minute wird die Aktion initiiert. Der ausgeschaltete Bildschirm wird
in der Aufgabenanweisung selbst nicht ausdruecklich erwaehnt.

Jede Person erlebt alle drei Initiierungsformen:

1. **Notify only:** Nur eine Benachrichtigung mit der vorgeschlagenen Aktion;
   keine automatische Ausfuehrung.
2. **Wake and ask:** Bildschirm aktivieren und vor der Ausfuehrung eine
   Bestaetigung einholen.
3. **Wake and execute:** Bildschirm aktivieren und die Aktion selbststaendig
   ausfuehren.

Die drei kurzen Aufgaben werden zwischen den Initiierungsformen rotiert, damit
Aufgabe und Modus nicht konfundiert sind:

1. **Wetter -> Einkaufsliste:** In einer Minute die Wettervorhersage fuer morgen
   pruefen und die Einkaufsliste um einen passenden Gegenstand ergaenzen.
2. **Projektgruppe -> Notizen:** In einer Minute die letzte Nachricht der
   Projektgruppe pruefen und die darin genannten Aufgaben als Checkliste in die
   Notizen-App uebertragen. **Dieser Task ist bereits umgesetzt.**
3. **E-Mail -> Kalender:** In einer Minute pruefen, ob sich der Raum der
   morgigen Veranstaltung in der letzten E-Mail geaendert hat, und den
   Kalendereintrag gegebenenfalls aktualisieren.

Nach jedem Durchlauf werden Komfort, wahrgenommene Kontrolle und empfundene
Aufdringlichkeit erhoben. Am Ende wird die bevorzugte Initiierungsform
abgefragt. Nur wenn alle drei Formen erlebt werden, koennen tatsaechliche
Erfahrungen verglichen werden; eine einzelne Aufgabe mit drei beschriebenen
Optionen wuerde lediglich hypothetische Praeferenzen messen.

Dieser Teil ist explorativ und nicht Bestandteil der Haupthypothesen. Wenn der
Pilot eine zu hohe Gesamtdauer zeigt, wird er als erster entfernt und nur als
Ausblick diskutiert.

## Technische Abgrenzung

- Direkte ADB-Aktionen koennen Navigationsschritte unsichtbar machen. Die
  Zustandsaenderung selbst, beispielsweise Lautstaerke, Helligkeit oder
  Medienwiedergabe, bleibt jedoch wahrnehmbar.
- Einfache Systemeinstellungen sind deshalb keine Hauptaufgaben. Die konstante
  Aktionsanzeige soll jederzeit sichtbar machen, was geaendert wird.
- Ein virtueller Bildschirm ist wegen des Implementierungsaufwands keine
  Voraussetzung fuer die Studie. Er bleibt technischer Ausblick und wuerde
  globale Effekte wie Ton oder Nicht-stoeren ohnehin nicht vollstaendig
  isolieren.

## Geplanter Ablauf und Dauer

1. Einwilligung, Einfuehrung und demografische Angaben: ca. 5-8 Minuten.
2. Freies Kennenlernen und standardisiertes Training: ca. 8-10 Minuten.
3. Drei Hauptbloecke mit je zwei Aufgaben, Reaktionsaufgabe und
   Blockfragebogen.
4. Optional: drei kurze Bildschirm-aus-Mikroaufgaben.
5. TAM, Praeferenzabfrage, Abschlussinterview und Debriefing: ca. 8-10 Minuten.

Gesamtdauer: geplant **60-75 Minuten**, harte Obergrenze nach Pilot maximal 90
Minuten.

## Analyseplan

- Kontinuierliche Zielgroessen: lineare Mixed Models mit Person und Aufgabe als
  zufaelligen Effekten.
- Binaere Fehlererkennung/-verhinderung: logistisches Mixed Model, aufgrund
  weniger Ereignisse explorativ.
- Feste Effekte: Bedingung, wahrgenommene Kritikalitaet und Reihenfolge.
- Geplante Kontraste: C1 gegen C2 und C2 gegen C3; Korrektur multipler Tests
  beispielsweise nach Holm.
- TiA-Subskalen und eigene Manipulationsitems bleiben getrennt.
- Die kontrollierte Replay-Zuverlaessigkeit ist kein Outcome.

## Organisation (aktueller Stand)

- Terminbuchung wird ueber einen separaten Cal.com-Event-Typ organisiert.
- Buchungszeitraum soll Juli bis Ende August 2026 umfassen.
- Vorlaeufiger Ort: Raum ueber der Mensa beziehungsweise Bibliotheksbereich.
- Zugang zum MoxD-Lab ist unklar und wird mit Prof. Böhmer geklaert; der Ort in
  Cal.com wird danach aktualisiert.
- Teilnehmerkommunikation und Studie sind auf Deutsch, obwohl die Masterarbeit
  auf Englisch verfasst wird. In der Arbeit wird dies dokumentiert; deutsche
  Materialien kommen in den Anhang und uebersetzte Interviewzitate werden als
  eigene Uebersetzung gekennzeichnet.

## Offene Punkte

1. Rueckmeldung des Betreuers zum finalen Design und zur ethischen
   unvollstaendigen Vorabinformation.
2. Raum beziehungsweise MoxD-Lab-Zugang bestaetigen.
3. Konkrete Apps und Studienkonten fuer alle Aufgaben einfrieren.
4. Sechs Fehlerplaene und exakte Fehlerzeitpunkte als Trial-Matrix ausarbeiten.
5. Originalfrageboegen und Figma-Druckfassung gegeneinander pruefen.
6. Pilot mit mindestens drei Personen: Dauer, Verstaendlichkeit,
   Eingriffsfenster in C3, Fehler-Salienz und Fragebogenbelastung pruefen.
7. Explorativen Bildschirm-aus-Teil nach dem Pilot behalten oder in den
   Ausblick verschieben.

## Zentrale methodische Quellen

- Parasuraman, Sheridan & Wickens (2000), Types and Levels of Human Interaction
  with Automation: https://doi.org/10.1109/3468.844354
- Dahlbäck, Jönsson & Ahrenberg (1993), Wizard of Oz Studies - Why and How:
  https://doi.org/10.1016/0950-7051(93)90017-N
- Hart & Staveland (1988), NASA-TLX:
  https://doi.org/10.1016/S0166-4115(08)62386-9
- Körber (2019), Trust in Automation Questionnaire:
  https://doi.org/10.1007/978-3-319-96074-6_2
- Davis (1989), Technology Acceptance Model:
  https://doi.org/10.2307/249008
- DGPs, Berufsethische Richtlinien:
  https://www.dgps.de/die-dgps/aufgaben-und-ziele/berufsethische-richtlinien/

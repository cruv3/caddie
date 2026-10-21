# Shared Autonomy – Sprechtext zum Üben (v4.1)

Andreas Schurawlev · Masterarbeit Medieninformatik M.Sc. · TH Köln

Vollständiger Sprechtext zur Präsentation v4.1: 26 Hauptfolien und 19 Backups. Backups gehören zur Fragerunde. Plan für den Hauptvortrag: 28:00 Minuten (Summe der Zeitfenster, eher großzügig; reine Sprechzeit etwa 24 Minuten plus Videos und Pausen). Tatsächliche Dauer mit Timer prüfen. Zeitfenster sind Richtwerte für die Planung, ausgehend vom Deck.

Normale Absätze sind dein Sprechtext. Eckige Klammern sind Regiehinweise und werden nicht gesprochen. „[Klick N · …]“ markiert genau den N-ten Weiter-Klick auf dieser Folie; danach folgt, was zu diesem Schritt gesagt wird. Dieselbe Datei erzeugt die Sprecheransicht (P), das PDF und den Spickzettel. Formuliere ruhig mit eigenen Worten; Zahlen, Reihenfolge und Einschränkungen sollen erhalten bleiben.

## Folie 01 · Cold Open: Abgabe der Masterarbeit
Szene: cold-open
Zeitfenster: 1:15 min
Anker: Auftrag · Video 1,5× · Freigabe · Handzeichen · Wann prüfen?
Quelle: Szene 01; Cold-Open-Demo; Backup B14

[Standbild sichtbar. Ruhig ankommen, erst sprechen, dann klicken.]

Ich möchte meine Masterarbeit abgeben. Dafür soll mein Smartphone eine E-Mail vorbereiten, den Empfänger eintragen und die Arbeit als PDF anhängen.

[Klick 1 · Video startet mit 1,5×]

[Das Video läuft bis „Aktion prüfen“, etwa 36 Sekunden. Ein weiterer Klick während des Videos springt direkt zum Dialog; die Frage kommt erst mit dem nächsten Klick.]

Ich gebe den Auftrag per Sprache. Danach arbeitet das Smartphone die Schritte selbst ab: Es öffnet die Webmail, trägt den Empfänger ein, schreibt Betreff und Text und hängt die PDF an.

Bis hierhin hat das Smartphone alles vorbereitet. Jetzt steht eine Entscheidung an.

[Klick 2 · Markierung und Frage]

Würden Sie die Nachricht abschicken? Wer würde bestätigen?

[Etwa fünf Sekunden auf Handzeichen warten. Antworten nicht bewerten.]

An diesem Punkt müssen Sie einschätzen, ob alles stimmt: der Empfänger, der Text und der Anhang. Genau dieser Moment interessiert mich: Wann sollte ein Mensch die Arbeit eines Smartphone-Agenten prüfen – und was bewirkt dieser Zeitpunkt?

[Weiter zur Titelfolie.]

## Folie 02 · Titel: Shared Autonomy
Szene: titel
Zeitfenster: 0:45 min
Anker: Andreas · Gemeinsame Kontrolle · Vier Teile
Quelle: Szene 02

Mein Name ist Andreas Schurawlev. Ich stelle heute meine Masterarbeit im Master Medieninformatik an der TH Köln vor: Shared Autonomy.

Gemeint ist die Zusammenarbeit zwischen einem Menschen und einem Agenten, der auf dem Smartphone handelt. Der Agent übernimmt Schritte. Der Mensch behält die Möglichkeit, zu prüfen und einzugreifen. Ich möchte ja nicht jeden Klick selbst machen – sonst könnte ich das Smartphone auch einfach selbst bedienen.

Ich zeige zuerst, warum die Frage relevant ist. Dann erkläre ich meinen Prototyp Caddie, danach die Studie und ihre Ergebnisse, und zum Schluss, was daraus für die Gestaltung folgt.

[Weiter.]

## Folie 03 · Agents lernen, Apps zu bedienen
Szene: agents
Zeitfenster: 1:45 min
Anker: Hersteller · AppAgent · AndroidWorld = Benchmark · DroidRetriever / AgentLens · 30,6 % M3A + GPT-4 Turbo, Accessibility Tree
Quelle: Thesis Kap. 1.1, Tab. 2.1; Rawles et al., AndroidWorld, Tab. 3; Quellen-Folie-03.md

Smartphone-Agenten sollen Aufgaben übernehmen, für die wir sonst selbst mehrere Apps bedienen. Vier Beispiele zeigen, wo die Entwicklung steht.

[Klick 1 · Hersteller]

Bei den Herstellern zeigt Google mit Gemini auf Galaxy eine frühe Vorschau: Der Agent arbeitet im Hintergrund, man kann den Fortschritt ansehen, eingreifen oder stoppen. Apple hat im Juni 2026 eine neue Siri angekündigt, die Aktionen über Apps hinweg ausführen soll. Das sind Herstellerangaben, kein unabhängiger Test.

[Klick 2 · Forschungsagent]

AppAgent ist ein Forschungsagent. Er lernt Apps, indem er sie erkundet oder sich etwas vormachen lässt, und bedient sie dann über die Oberfläche – mit Tippen und Wischen.

[Klick 3 · Prüfstand]

AndroidWorld ist kein Agent, sondern ein Benchmark – ein Prüfstand: 116 Aufgaben in 20 Apps. Geprüft wird, ob am Ende wirklich der gewünschte Zustand erreicht ist.

[Klick 4 · Mensch im Blick]

Und es gibt Arbeiten, die den Menschen schon mitdenken: DroidRetriever zeigt den Fortschritt einer Suche über mehrere Apps und erlaubt, einzugreifen oder zu übernehmen. AgentLens untersucht, wie viel von der laufenden Oberfläche man dem Menschen zeigen sollte.

[Klick 5 · 30,6 %]

Wie zuverlässig ist das? Im AndroidWorld-Paper von 2024 löste die stärkste getestete Baseline 30,6 Prozent der Aufgaben. Das war der Agent M3A mit dem Modell GPT-4 Turbo. Als Eingabe bekam er den Accessibility Tree, also eine strukturierte Beschreibung der Bedienelemente.

Das gilt für diesen Aufbau und diesen Stand. Für mich führt es zur Frage: Wenn das nicht immer klappt – wann sollte der Mensch hinschauen?

[Weiter.]

## Folie 04 · Erfolg gemeldet ≠ Erfolg
Szene: erfolg-gemeldet
Zeitfenster: 1:30 min
Anker: Eigener Vergleichslauf Mai 2026 · Sechs Aufgaben × 2 · „done“ bei AUS · 11/12
Quelle: research/evaluation/claude-vs-local.md; Thesis Kap. 3.7.1

Bevor ich zur Architektur komme, ein Beispiel aus meiner eigenen Entwicklung. Das ist ein technischer Vergleichslauf aus dem Mai 2026 – kein Teil der Nutzerstudie und kein Modellranking.

Claude Opus 4.7 steuerte das Smartphone vom PC aus. Es gab sechs Aufgaben, jede zweimal: Dark Mode an, Dark Mode aus, Bluetooth an, Helligkeit auf 50 Prozent, einen Timer auf fünf Minuten und eine Pizza-Suche.

[Klick 1 · Dark Mode an, Modell meldet „done“]

Schauen wir auf den ersten Lauf von „Dark Mode an“. Das Modell meldet am Ende: „done“.

[Klick 2 · Schalter steht auf AUS]

Der Schalter steht aber auf „Aus“. Das Modell ist fertig – der Dark Mode offenbar noch nicht.

[Kurz lächeln, weiter.]

[Klick 3 · Alle Ergebnisse]

Insgesamt war das Ergebnis gut: Elf von zwölf Endzuständen habe ich anhand der Screenshots als korrekt bewertet. Eine Aufgabe kostete etwa 37 Cent bis 1,74 Dollar und dauerte 55 bis 308 Sekunden.

Wichtig ist für mich der eine Fall: Die Meldung „fertig“ belegt nicht, dass der Zustand erreicht ist. Das System braucht eine neue Beobachtung – und der Mensch eine sinnvolle Gelegenheit zur Prüfung.

[Weiter.]

## Folie 05 · Nicht ob, sondern wann
Szene: frage
Zeitfenster: 0:30 min
Anker: Nicht ob · sondern wann · Caddie macht den Moment kontrollierbar
Quelle: Datei 06-frage

Damit bin ich bei der eigentlichen Frage. Es geht nicht nur darum, ob ein Agent auf dem Smartphone handeln kann.

[Klick 1 · Zweiter Satz]

Mich interessiert, wann der Mensch kontrollieren und intervenieren sollte – und wann das System ihn ausdrücklich um eine Entscheidung bittet. Um das zu untersuchen, brauche ich einen Prototyp, in dem dieser Moment kontrollierbar ist. Das ist Caddie.

[Weiter.]

## Folie 06 · Der Loop gehört dem Handy
Szene: loop
Zeitfenster: 2:30 min
Anker: Android vs. Modellrechner · Beobachten · Vorschlagen · Prüfen · Freigabe · Ausführen · Neu beobachten
Quelle: Thesis Kap. 3.2–3.4, Abb. 3.1/3.3

Die Grundidee von Caddie: Das Modell schlägt vor. Android prüft und führt aus. Links läuft zur Erinnerung die Demo vom Anfang.

[Klick 1 · Zuständigkeiten]

Wer macht was? Alles in diesem gestrichelten Rahmen läuft in der Android-App auf dem Handy: Beobachten, Prüfen, Ausführen, Verifizieren. Nur ein Schritt liegt außerhalb: Das Sprachmodell läuft auf einem separaten, selbst gehosteten Rechner. Die App schickt ihm eine Anfrage über das Netzwerk und bekommt einen Vorschlag zurück. Caddie ist also handy-gesteuert, aber kein Sprachmodell auf dem Handy.

Ich verfolge jetzt einen einzigen Vorschlag durch diesen Loop – am Beispiel der Mail. Das ist schematisch.

[Klick 2 · Beobachten]

Zuerst beobachtet Caddie den Bildschirm über Android Accessibility. Das ist kein Screenshot, sondern ein Baum: Welche Felder, Texte und Buttons gibt es, und was kann man mit ihnen tun?

[Klick 3 · Vorschlagen (Modellrechner)]

Diese Beobachtung geht an den Modellrechner. Zurück kommt genau ein Tool-Aufruf, zum Beispiel: Tippe auf „Senden“. Kommen keiner oder mehrere, pausiert der Lauf. Caddie rät nicht.

[Klick 4 · Prüfen]

Jetzt ist wieder das Handy dran. Die App prüft: Gibt es dieses Tool? Sind die Argumente gültig? Gibt es genau ein passendes, sichtbares Ziel? Und die Aufsichtsregel sagt: Senden ist kritisch.

[Klick 5 · Freigabe]

Also wartet die Aktion auf Sie. Erst wenn Sie zum Bestätigen wischen, darf sie raus. Ohne Freigabe kein Dispatch.

[Klick 6 · Ausführen]

Erst danach tippt Android über Accessibility. Das Overlay zeigt, was gerade passiert.

[Klick 7 · Neu beobachten]

Dann wird neu beobachtet: Ist der erwartete Zustand da? Wenn ja, geht es weiter. Wenn das Ergebnis unklar ist, pausiert Caddie, statt die Aktion zu wiederholen.

[Klick 8 · Nächste Runde]

Und damit beginnt die nächste Runde. Wichtig: Die Demo vom Anfang hatte eine fest vorgegebene Aktionsfolge, damit sie reproduzierbar ist. Im normalen Betrieb kommen die Vorschläge vom Modell.

[Weiter.]

## Folie 07 · Was der PC sieht, ist nicht, was Sie sehen
Szene: semantik
Zeitfenster: 1:15 min
Anker: Erster Prototyp PC + ADB · Verdeckter Button · Accessibility · Ein Gerät
Quelle: Thesis Kap. 3.2, 3.7.3, Tab. 3.4

Warum läuft der Loop auf dem Handy? Mein erster Prototyp lief in Python auf dem PC und steuerte das Smartphone über ADB. Dabei zeigte sich auf dem Pixel 7 ein Problem. Die Darstellung hier ist schematisch: Die Mail liegt hinter Caddies Freigabe-Overlay.

[Klick 1 · Unterschied der Sichten]

Der UIAutomator-Dump über ADB ließ dieses Overlay in den getesteten Zuständen einfach weg. Der Senden-Button darunter erschien als bedienbar – obwohl der Mensch ihn gerade gar nicht erreichen konnte. Die Accessibility-Sicht auf dem Handy behielt die Fensterreihenfolge und markierte den Button als verdeckt.

Wenn Mensch und Agent verschiedene Bildschirme sehen, wird gemeinsame Kontrolle schwierig.

[Klick 2 · Entscheidung]

Das war, zusammen mit dem Aufwand des PC-Prozesses, ein Grund, alles aufs Handy zu holen. Einschränkung: ein Gerät, getestete Zustände – kein allgemeiner Beweis gegen ADB.

[Weiter.]

## Folie 08 · Kritische Aktionen brauchen Freigabe
Szene: gate
Zeitfenster: 1:15 min
Anker: Vorbereitung läuft · Senden wartet · Freigabe vor Ausführung · Wischen
Quelle: Thesis Kap. 3.3–3.5

Die Freigabe sitzt im Android-Loop vor der Ausführung – eine Regel im Prompt wäre nur eine Bitte an das Modell. Zeigen lässt sich das an der Mail. Die Darstellung ist schematisch, keine Zeitmessung.

[Klick 1 · Aktionen laufen, „Senden“ wartet]

Caddie öffnet die Webmail, trägt den Empfänger ein, schreibt Betreff und Text und hängt die PDF an. Das läuft einfach durch.

[Warten, bis „Senden“ an der Schranke steht und die Karte erscheint.]

Beim Senden ist das anders: Die Nachricht geht nach außen. Die Aufsichtsregel stoppt deshalb diese Aktion. Das gilt für alles, was sendet, bezahlt, löscht, Zugriffe ändert oder sonst folgenreich ist. Wichtig ist die Reihenfolge: Bis jetzt ist noch nichts gesendet. Der Vorschlag wartet.

[Klick 2 · Wischen, Schranke öffnet]

Sie sehen, was passieren soll, und wischen zum Bestätigen. Erst dann führt Android die Aktion aus – und beobachtet danach neu. Die Freigabe ist also eine Entscheidung vor der Ausführung, nicht ein Häkchen danach. In der echten Demo blieb die Mail übrigens ungesendet.

[Weiter.]

## Folie 09 · Eingreifen: pausieren, korrigieren, weiter
Szene: eingreifen
Zeitfenster: 1:30 min
Anker: C3-Clip · Hauptbahnhof → Campus Deutz · Pausieren · Korrigieren · Fortsetzen oder beenden · Gestartetes bleibt
Quelle: Thesis Kap. 3.3.2, 3.5, Tab. 3.1; C3-Clip

Die Freigabe ist der Pflichtmoment. Eingreifen geht aber jederzeit. Das zeige ich an einem kurzen Clip. Er ist nachgestellt im Studien-Build, keine Aufnahme von Probanden.

Der Auftrag: Anna die Ankunftszeit am Campus Deutz schicken. In Maps steht aber Köln Hauptbahnhof als Ziel.

[Klick 1 · Video läuft bis zum Eingriff]

[Ein weiterer Klick springt zum Eingriff. Video läuft mit 1,5×.]

Caddie übernimmt die Fahrzeit in den Chat. Jetzt greife ich ein.

[Klick 2 · Pausieren]

Erster Schritt: pausieren. Das geht per Tippen oder per Sprache mit „Hey Jarvis“. Caddie hält den Lauf vor der nächsten Aktion an. Man sieht das „listening“ im Overlay.

[Klick 3 · Korrigieren]

Zweiter Schritt: korrigieren. Ich sage: nicht Hauptbahnhof, sondern Campus Deutz. Caddie übernimmt die Korrektur und sucht die Route neu.

[Klick 4 · Fortsetzen]

Dritter Schritt: fortsetzen – oder beenden. Hier geht es mit dem neuen Ziel weiter, und die richtige Ankunftszeit wird gesendet. Mit „Stopp“ hätte ich den Lauf auch beenden können; dann folgt keine weitere Aktion.

[Klick 5 · Grenze]

Eine Grenze bleibt: Was Android schon angenommen hat, holt Caddie nicht zurück. Eine bereits gestartete Aktion lässt sich nicht abbrechen. In der Studie wurden Korrekturen bei Route und Zahlung deshalb nur vor dem Senden angenommen.

[Weiter.]

## Folie 10 · Wo Caddie an Grenzen stößt
Szene: grenzen-technik
Zeitfenster: 0:30 min
Anker: Regel erkennt nicht jede Folge · Gestartetes bleibt · Sicht unvollständig · Modell extern
Quelle: Thesis Kap. 3.3.2, 3.5, 3.8

Kurz die technischen Grenzen: zwei betreffen die Freigabe, zwei den Aufbau. Die Regel stuft Aktionen nach Toolnamen und Beschriftungen ein; nicht jede Folge ist daran erkennbar. Was Android schon angenommen hat, lässt sich nicht zuverlässig rückgängig machen; Eingreifen stoppt erst die nächste Aktion. Accessibility zeigt manche Oberflächen nur unvollständig, und manche Elemente sind schlecht beschriftet. Und das Sprachmodell läuft auf einem separaten Rechner; Bildschirminhalte gehen dorthin, Passwortfelder ausgenommen. Loop und Ausführung bleiben auf dem Handy.

Das Gate ist also eine konkrete Kontrollgrenze, kein allgemeiner Sicherheitsnachweis. Wo es den Menschen fragt, ist eine Designentscheidung. Genau das habe ich untersucht.

[Weiter.]

## Folie 11 · Wo sitzt der Moment zum Eingreifen?
Szene: kontrollmomente
Zeitfenster: 1:15 min
Anker: Bedingung setzt die Gates · C1 jeder konfigurierte Schritt · C2 gebündelt vor dem Commit · C3 keine Pflichtfreigabe · Alle sehen den Ablauf
Quelle: Thesis Kap. 3.5, 4.2.1

Für die Studie habe ich genau diesen Moment verschoben. Dort legt die Bedingung fest, wo bestätigt werden muss – nicht die Risikoregel aus dem Alltag. So sehen die drei Bedingungen als Zeitstrahl aus. In C1 wird vor jedem vorgesehenen aufgabenrelevanten Schritt bestätigt. Reine Vorbereitung, wie ein Feld fokussieren oder die Tastatur schließen, läuft ohne Bestätigung. In C2 gibt es eine Freigabe, unmittelbar bevor die Änderung wirksam wird – „am Ende“ heißt also vor der Abschlussaktion, nicht danach. In C3 gibt es keine Pflichtfreigabe, auch nicht vor dem Senden oder Bezahlen.

[Klick 1 · Abläufe starten]

Hier sieht man, wie die Kontrollpunkte den Ablauf unterbrechen. Die Animation zeigt das Prinzip, keine gemessenen Zeiten.

[Klick 2 · Gemeinsamkeiten]

Wichtig ist, was gleich blieb: In allen drei Bedingungen erzählte das Overlay den Ablauf, und überall konnte man freiwillig eingreifen. C3 heißt also nicht: nichts sehen, nichts tun können.

[Weiter.]

## Folie 12 · Drei Bedingungen nebeneinander
Szene: drei-handys
Zeitfenster: 1:15 min
Anker: Nachgestellt · C1 Musik · C2 Rechnung · C3 Route · Keine Geschwindigkeits-Rangliste
Quelle: Datei 16-drei-handys; Originalclips C1–C3

Hier die drei Bedingungen auf dem Smartphone, wieder nachgestellt im Studien-Build. Links sucht Caddie in C1 ein Lied aus einer Chat-Nachricht und öffnet es in der Musik-App. In der Mitte geht es in C2 um eine Rechnung. Rechts ist die Route für Anna aus C3, die Sie schon kennen. Alle starten am Ende ihres Sprachbefehls.

[Klick 1 · Videos starten]

[Etwa 40–45 Sekunden laufen lassen. Blick zwischen den Handys wechseln, nicht jede Aktion vorlesen.]

Links sieht man die wiederkehrenden Bestätigungen, in der Mitte die eine Prüfung vor der Zahlung, rechts den freiwilligen Eingriff.

Die Aufgaben sind verschieden. Eine Rangliste der Geschwindigkeit lässt sich daraus nicht ableiten. Die Clips zeigen, wie sich die Bedingungen anfühlen.

[Weiter.]

## Folie 13 · Der Auftrag stimmt, ein Wert nicht
Szene: fehler-c2
Zeitfenster: 0:30 min
Anker: Rechnung 30 € · Eingetragen 80 € · Drei Fehler pro Proband
Quelle: Thesis Tab. 4.1; Datei 17-fehler-c2

Schauen wir auf die Rechnung in der Mitte. Die Aufgabe ist grundsätzlich richtig: Caddie überträgt Angaben aus einer Mail in die Banking-App und hält in C2 vor der Zahlung an.

[Klick 1 · Zoom auf den Betrag]

Die Rechnung war über 30 Euro.

[Klick 2 · 80 Euro]

Eingetragen sind 80 Euro. Solche gezielten Abweichungen waren in die Studienabläufe eingebaut. Jeder Proband erlebte drei Fehler, einen pro Bedingung. Aufgeklärt wurde erst am Ende.

[Weiter.]

## Folie 14 · Die Studie in einem Bild
Szene: studie
Zeitfenster: 1:15 min
Anker: 18 Probanden · 108 Durchgänge · Wie schwerwiegend wäre ein Fehler? Lied ärgerlich, Betrag schwerwiegend · weniger kritisch | kritischer · 54 mit Fehler · Aufsicht, nicht Planung · Within-Design
Quelle: Thesis Kap. 4.2–4.4 (Einstufung 4.2.2); StudyMatrix.kt

Ausgewertet wurden 18 Probanden mit je sechs Aufgaben, also 108 Durchgänge.

Die Aufgaben habe ich danach unterschieden, wie schwerwiegend ein Fehler wäre. Wird die falsche Version eines Liedes gespeichert, ist das eher ärgerlich. Wird der falsche Betrag überwiesen, wäre das deutlich schwerwiegender. Das meine ich mit weniger kritischen und kritischeren Aufgaben. In der Matrix stehen links drei weniger kritische, rechts drei kritischere Aufgaben.

[Nur bei Nachfrage: Gemeint ist die erwartete Schwere eines falschen Ergebnisses im jeweiligen Szenario (Thesis 4.2.2: „task criticality“; bisher übersetzt als Folgenstufe) – keine Rangfolge der Apps und keine Wahrscheinlichkeit, dass ein Fehler passiert.]

Jeder Proband erlebte alle drei Bedingungen, je einmal bei einer weniger kritischen und einmal bei einer kritischeren Aufgabe. Die Matrix zeigt die balancierte Zuweisung.

[Klick 1 · Fehler-Durchgänge]

In 54 Durchgängen war ein Fehler eingebaut: drei pro Proband, einer pro Bedingung. Also 18 Fehlerfälle je Bedingung.

Die Messläufe waren feste Abläufe ohne Sprachmodell. Untersucht wurde also die Aufsicht – nicht die Planung des Modells. Der Normalmodus aus dem Alltag ist keine dieser drei Bedingungen.

Die Probanden kannten vorher das Ziel und die richtigen Werte. Nach jeder Aufgabe wurde offen nach Auffälligkeiten gefragt. Erst ganz am Ende wurden die eingebauten Fehler offengelegt.

[Weiter.]

## Folie 15 · Sechs Aufgaben, sechs falsche Werte
Szene: aufgaben
Zeitfenster: 0:30 min
Anker: Zwei Apps je Aufgabe · Weniger kritisch: Route, Notiz, Lied · Kritischer: Kalender, Nicht stören, Zahlung · Test-Apps, kein echtes Geld
Quelle: Thesis Kap. 4.2.2, Tab. 4.1

Jede Aufgabe verbindet zwei Apps. Bei den weniger kritischen Aufgaben war zum Beispiel das Routenziel falsch, ein Wochentag in einer Notiz verändert oder die falsche Liedversion gewählt. Bei den kritischeren ging es um eine falsche Kalenderzeit, ein falsches Zeitfenster für „Nicht stören“ und einen falschen Betrag.

Die kritischeren Aufgaben liefen in lokalen Test-Apps: Es wurde also kein echtes Geld überwiesen und keine echte Systemeinstellung geändert.

[Weiter.]

## Folie 16 · Finden Sie den Fehler
Szene: findet-den-fehler
Zeitfenster: 1:30 min
Anker: Whiteboard merken · Notiz prüfen · Donnerstag → Dienstag · Erkennen ≠ Korrigieren
Quelle: Thesis Tab. 4.1; Whiteboard-Illustration

Jetzt sind Sie dran. Auch das ist nachgestellt. Merken Sie sich bitte die Angaben auf dem Whiteboard. Caddie soll daraus eine Notiz machen.

[Einige Sekunden zum Lesen lassen. Weiter geht erst, wenn das Foto im Video steht.]

[Klick 1 · Notiz entsteht]

[Warten, bis die fertige Notiz lesbar ist.]

Was ist in der Notiz falsch?

[Kurz auf Zurufe warten. Falls nichts kommt: „Schauen Sie auf den Wochentag.“]

[Klick 2 · Auflösung]

Aus Donnerstag wurde Dienstag. Die Aufgabe ist ausgeführt, aber ein Wert stimmt nicht. Im Alltag denkt man: Das fällt doch sofort auf. Wenn mehrere Schritte laufen und man sich die Angaben merken muss, ist das weniger selbstverständlich.

Und: Einen Fehler zu erkennen heißt noch nicht, ihn rechtzeitig korrigiert zu haben.

[Weiter.]

## Folie 17 · C1 kostet Entscheidungen
Szene: ergebnis-entscheidungen
Zeitfenster: 0:45 min
Anker: Pflichtentscheidung = Antwort auf ein Gate · Mediane 7 / 1 / 0 · C3 kein Gate, nur freiwillig · 13,3 s / 5,6 s · Was bringt der Aufwand?
Quelle: Thesis Tab. 5.2, Kap. 4.6.1

Erstes Ergebnis: der Aufwand. Eine Pflichtentscheidung ist die Antwort auf eine vorgeschriebene Freigabe, also auf ein Gate der Bedingung: in C1 vor jedem konfigurierten aufgabenrelevanten Schritt, in C2 die eine Endprüfung vor dem Commit. Im Median waren es pro Aufgabe sieben in C1 und eine in C2. C3 hatte kein solches Gate, auch nicht vor dem Bezahlen. Dort konnte man nur freiwillig eingreifen, und das zählt nicht als Pflichtentscheidung. Die Null folgt also direkt aus dem Design, und auch die anderen Anzahlen zum Teil. Die aufsummierte Antwortzeit auf diese Freigaben lag im Median bei 13,3 Sekunden in C1 und 5,6 Sekunden in C2.

[Klick 1 · Die eigentliche Frage]

Spannender ist also: Was bringt dieser Aufwand? Wenn ich öfter frage, sollte das im besten Fall helfen, Fehler zu bemerken.

[Weiter.]

## Folie 18 · Häufiger bestätigen ≠ besser aufpassen
Szene: ergebnis-erkennung
Zeitfenster: 1:30 min
Anker: Erwartung abfragen · % = eingebaute Fehler zutreffend berichtet, nur eindeutig auswertbare Fälle · C1 76,5 % · C2 91,7 % · C3 78,6 % · höchste Rate ≠ belegter Vorteil · kein C2-Sieg
Quelle: Thesis Tab. 5.6, Gl. 4.5, Kap. 5.4.2

Bevor ich die Zahlen zeige: Wo würden Sie die meisten erkannten Fehler erwarten? In C1, wo jeder Schritt bestätigt wird? In C2, mit einer Freigabe am Ende? Oder in C3, ohne Pflichtfreigabe, wo man nur freiwillig eingreift?

[Etwa fünf Sekunden Denkpause.]

[Klick 1 · Balken]

Die Prozente sind der Anteil der eingebauten Fehler, die die Probanden nach der Aufgabe zutreffend berichtet haben – gezählt nur die eindeutig auswertbaren Fälle. Erkannt wurden die eingebauten Fehler in C1 zu 76,5 Prozent, in C2 zu 91,7 Prozent und in C3 zu 78,6 Prozent.

C1 liegt also nicht vorn. Häufigeres Bestätigen hat hier keinen Vorteil gezeigt. C2 hat die höchste Rate – ein belegter Vorteil ist das aber nicht. Und das heißt auch nicht, dass alle gleich gut sind.

[Weiter. Bei Rückfragen zu Zählungen und nicht zuordenbaren Berichten: Backup B11. Nach weniger kritischen und kritischeren Aufgaben gruppiert: Backup B15.]

## Folie 19 · Die Wahl wechselt, wenn es kritischer wird
Szene: ergebnis-praeferenz
Zeitfenster: 1:45 min
Anker: Ein Punkt = ein Proband · Weniger kritisch 14 × C3 · Kritischer 12 × C1 · 10 wechseln · Allgemein C2
Quelle: Thesis Abb. 5.4, Kap. 5.6

Neben dem Verhalten habe ich gefragt, welche Kontrolle die Probanden bevorzugen – getrennt für weniger kritische und kritischere Aufgaben. Jetzt ist jeder Punkt ein Proband. Bei weniger kritischen Aufgaben wählen 14 von 18 C3: einfach laufen lassen, ohne Pflichtfreigabe, und nur freiwillig eingreifen. Bei einer Liedauswahl will ich ja auch nicht vor jedem Tippen einen kleinen Antrag genehmigen.

[Klick 1 · Kritischere Aufgaben]

Dieselben 18 Probanden für kritischere Aufgaben: Jetzt wählen zwölf C1, fünf C2 und ein Proband C3.

[Klick 2 · Die Wechsel]

Zehn Probanden wechseln direkt von C3 zu C1. Dieselben Menschen wollen je nach Kontext eine ganz andere Kontrolle. Das ist beschreibend, kein eigener Test.

Bei der allgemeinen Frage lag übrigens C2 vorn: zehn Probanden, sechs für C1, zwei für C3.

[Weiter.]

## Folie 20 · Was Probanden geschrieben haben
Szene: stimmen
Zeitfenster: 0:30 min
Anker: 16 Geld-Freigabe · 12 C2 · 11 Last · 11 adaptiv
Quelle: Thesis Tab. 5.12

Die schriftlichen Antworten passen dazu. Das sind zusammengefasste Themen, keine Zitate. 16 von 18 wünschen eine Freigabe bei Geld. Zwölf sehen C2 als guten Kontrollpunkt. Elf empfanden die Bestätigungen in C1 als Last. Und elf wünschen eine Aufsicht, die sich der Aufgabe anpasst.

[Weiter.]

## Folie 21 · Wie weit die Ergebnisse tragen
Szene: grenzen-studie
Zeitfenster: 0:30 min
Anker: Kleine Stichprobe: 18 Probanden · Feste Abläufe, ohne LLM · Rückblickende Berichte · Feste Einstufung je Aufgabe
Quelle: Thesis Kap. 6.4, 7

Wie weit tragen die Ergebnisse? Vier Eigenschaften des Designs setzen den Rahmen. Erstens eine kleine Stichprobe: 18 Probanden aus einer Gelegenheitsstichprobe, 22 bis 35 Jahre; die Größe folgt der Balance der Zuweisung, nicht einer Power-Berechnung. Die Schätzungen sind entsprechend unsicher. Zweitens feste Abläufe: Die Studie lief echt auf Android, aber ohne LLM-Planung; eingreifen konnte man trotzdem. Die kritischeren Aufgaben liefen nur in Test-Apps. Die Aussagen betreffen also die Aufsicht, nicht den Normalmodus. Drittens rückblickende Berichte: Ob ein Fehler erkannt wurde, habe ich per Fragebogen nach jeder Aufgabe erhoben; den Moment des Erkennens habe ich nicht beobachtet. Und viertens war jede Aufgabe fest als weniger kritisch oder kritischer eingestuft; Aufgabeninhalt und Schwere eines Fehlers habe ich nicht getrennt geprüft.

Das sind Erkenntnisse über diese Kontrollbedingungen in diesem Aufbau – kein allgemeiner Sicherheitsnachweis für LLM-Agenten.

[Weiter.]

## Folie 22 · Aufsicht an die Aufgabe anpassen
Szene: folgerung
Zeitfenster: 1:15 min
Anker: Häufiger bestätigen: kein belegter Vorteil · Kontrollwunsch wechselt mit der Aufgabe · Regler: Wie schwerwiegend wäre ein Fehler? · C3 → C2 → C1 · Gestaltungsidee, nicht getestet
Quelle: Thesis Kap. 6–7; Erkennung nach Einstufung nur bei Rückfrage: Backup B15

Was folgt daraus? Mehr Pflichtbestätigungen kosten Aufwand, und einen belegten Vorteil für die Fehlererkennung haben sie hier nicht gezeigt. Gleichzeitig wollten dieselben Probanden je nach Aufgabe eine andere Kontrolle: bei weniger kritischen Aufgaben meist laufen lassen, bei kritischeren meist jeden Schritt bestätigen.

Daraus leite ich eine Gestaltungsidee ab: die Aufsicht an die Aufgabe anpassen. Der Regler steht für die Frage von vorhin: Wie schwerwiegend wäre ein Fehler?

[Klick 1 · Regler Mitte, C2]

Bei weniger kritischen Aufgaben könnte der Ablauf selbstständig laufen. Wird es kritischer, wäre eine gezielte Prüfung vor der Abschlussaktion denkbar.

[Klick 2 · Regler rechts, C1]

Und bei besonders kritischen Aufgaben mehr Kontrollpunkte.

[Klick 3 · Forschungsfrage]

Das ist eine Gestaltungsidee, keine getestete Regel. Adaptive Aufsicht habe ich nicht untersucht. Die Ergebnisse und die Wünsche von elf Probanden machen sie zur nächsten Forschungsfrage.

[Weiter.]

## Folie 23 · Mein Beitrag
Szene: beitrag
Zeitfenster: 0:45 min
Anker: Technische Untersuchung · Aufsichtsmechanismus · Kontrollierter Vergleich
Quelle: Thesis Kap. 1.4, 3.8

Was habe ich beigetragen? Drei Dinge.

Erstens eine technische Untersuchung: wo Modelle auf dem Smartphone an Grenzen stoßen und welche Fehlermuster sich in dokumentierten Läufen wiederholen.

Zweitens einen implementierten Aufsichtsmechanismus: Caddie, mit dem Loop auf dem Handy, der Freigabe vor der Ausführung und der Möglichkeit, zu pausieren, zu korrigieren und zu stoppen. Die Bausteine wie Accessibility gibt es schon; mein Beitrag ist ihre Kombination mit klaren Grenzen.

Drittens einen kontrollierten Vergleich: drei Aufsichtsstufen bei weniger kritischen und kritischeren Aufgaben, mit 18 Probanden und 108 Durchgängen.

[Weiter.]

## Folie 24 · Future Work
Szene: future-work
Zeitfenster: 1:00 min
Anker: Schneller entscheiden → Latenz UND Fehler · Robuster ausführen → Benchmark mit Störungen · Adaptiv → Schattenmodus, dann Studie
Quelle: Thesis Kap. 7.2; Backup B10

Drei Richtungen, die als Nächstes geprüft werden können, jeweils mit einem passenden Test.

Erstens schneller entscheiden. Viele Schritte sind eigentlich eine Auswahl aus wenigen erlaubten Aktionen. Ein schnelles, typisiertes Auswahlmodell könnte das übernehmen, das große Modell nur offene Fälle. Das ist eine Hypothese. Der nächste Test: dieselben Aufgaben, und zwar Gesamtlatenz und Fehlerrate gegen den heutigen Weg.

[Klick 1 · Robuster ausführen]

Zweitens robuster ausführen: strukturierte App-Schnittstellen nutzen, wo sie die gewünschte Aktion anbieten. Fehlt eine passende Schnittstelle, wird die App weiterhin über ihre Oberfläche mit Accessibility bedient. Nach der Ausführung wird unabhängig geprüft, ob der gewünschte Endzustand erreicht ist. Das Ergebnis wird als erfolgreich, gescheitert oder unklar eingeordnet. Nächster Test: ein Benchmark mit unabhängig geprüften Endzuständen und absichtlich eingestreuten Störungen.

[Klick 2 · Adaptiv kontrollieren]

Drittens adaptive Kontrollzeitpunkte: Die Freigabe richtet sich danach, wie schwerwiegend ein Fehler bei dieser Aktion wäre. Nächster Test: zuerst im Schattenmodus aufzeichnen, wann der Klassifikator fragen würde, ohne etwas auszuführen. Danach eine Studie: feste Endprüfung gegen adaptive Regel.

[Weiter.]

## Folie 25 · Diese Entscheidung bleibt bei mir
Szene: zurueck-zum-anfang
Zeitfenster: 0:45 min
Anker: Gleiche Mail · Vorbereitung vs. Senden · Entscheidung beim Menschen
Quelle: Datei 30-zurueck-zum-anfang; Cold-Open-Demo

Zurück zur Mail vom Anfang. Derselbe Moment vor dem Senden. Caddie hat die Vorbereitung übernommen. Jetzt geht es um die Abgabe meiner Masterarbeit – eine kritische Aufgabe, ein Fehler wäre hier schwerwiegend. Die Freigabe gibt mir die Gelegenheit, Empfänger, Text und Anhang vorher zu prüfen. Wir wissen jetzt auch: Der Dialog allein garantiert nicht, dass ich gut prüfe. Seine Position ist eine bewusste Gestaltung.

[Klick 1 · Schlusssatz]

Ich möchte einen Agenten, der mir Arbeit abnimmt und mich an den richtigen Stellen entscheiden lässt. Bei dieser Nachricht heißt das: Diese Entscheidung bleibt bei mir.

[Zwei Sekunden Pause. Weiter.]

## Folie 26 · Danke und Fragen
Szene: ende
Zeitfenster: 0:15 min
Anker: Danke · Fragen · Backups mit G
Quelle: Datei 31-ende

Vielen Dank für Ihre Aufmerksamkeit. Ich freue mich auf Ihre Fragen.

[Pause lassen. Nicht das Fazit wiederholen. Backups bei passenden Fragen mit G öffnen.]

## Backup B1 · Modellaufruf
Szene: b01-modellaufruf
Zeitfenster: Nur bei Rückfrage
Anker: Protokoll + Tools → Gateway → Modellrechner → ein Tool-Aufruf · Instruktion ist keine Grenze
Quelle: Thesis Kap. 3.2, 3.3.1, 3.4

[Bei Fragen zum Modell, zur Verbindung oder zum Prompt öffnen.]

Pro Runde baut die App eine Anfrage aus dem Lauf-Protokoll – Gespräch, angenommene Tool-Aufrufe, deren Ergebnisse – und den Tool-Definitionen. Das Model Gateway auf dem Handy schickt sie als OpenAI-kompatibles JSON über HTTP an den selbst gehosteten Dienst; im evaluierten Aufbau qwen3.6-35b-a3b. Die Antwort kommt gestreamt zurück.

Erwartet wird genau ein Tool-Aufruf, bei Aktionen mit dem Zustand, der danach sichtbar sein soll. Die feste Systeminstruktion sagt unter anderem: Text auf dem Bildschirm ist Daten, keine Anweisung; nach jeder Aktion neu beobachten. Sie ist aber keine Sicherheitsgrenze. Die Grenzen setzt der Android-Loop: Route, Prüfung, Aufsicht und Beobachtung. Verbindungsfehler pausieren den Lauf.

## Backup B2 · Datenfluss
Szene: b02-datenfluss
Zeitfenster: Nur bei Rückfrage
Anker: Modellrechner bekommt Beobachtungen ohne Passwortfelder · MCP-Argumente · Spracherkennung · Selbst gehostet ≠ privat
Quelle: Thesis Kap. 3.5, 3.6, 3.8; docs/privacy-security.md

[Bei Fragen zu Datenschutz oder Cloud öffnen.]

Zum Modellrechner gehen der Auftrag, der Verlauf und die Bildschirmbeobachtungen: sichtbarer Text, Beschreibungen, IDs, Zustände und Positionen, außerdem ausgewählte Skill- oder Memory-Hinweise. Inhalte von Passwortfeldern entfernt Caddie vorher.

Ein konfigurierter MCP-Server bekommt die Argumente seines Tools. Die Spracherkennung läuft über den installierten Android-Dienst; ob das Audio auf dem Handy bleibt, hängt von diesem Dienst ab. Loop, Prüfung, Ausführung, Protokolle und Studiendaten bleiben auf dem Handy.

Selbsthosting und Passwort-Schwärzung reduzieren bestimmte Offenlegungen. Allgemein datenschutzfreundlich nenne ich Caddie aber nicht: Endpunkte, Netz und Logs gehören zur Grenze.

## Backup B3 · Sprache
Szene: b03-sprache
Zeitfenster: Nur bei Rückfrage
Anker: „Hey Jarvis“ auf dem Handy · Android-Spracherkennung · Stopp / Korrektur / Kontext löschen · an den Lauf gebunden
Quelle: Thesis Kap. 3, 3.5

[Bei Fragen zur Spracheingabe öffnen.]

Das Wake-Word „Hey Jarvis“ wird auf dem Handy erkannt. Es startet die Aufnahme genau einer Äußerung und hält einen laufenden Lauf an. Die Transkription übernimmt der installierte Android-Dienst; im Normalmodus Deutsch oder Englisch, in der Studie fest Deutsch.

Lokale Befehle sind Stoppen, Korrigieren und Kontext löschen. Was ich während einer Unterbrechung sage, gilt als Korrektur. Das Transkript ist an diesen Lauf gebunden und kann keinen späteren beeinflussen. Bei einem Erkennungsfehler wird der Halt gelöst, ohne dass eine Aktion entsteht. In der Studie wurde eine Korrektur nur angenommen, wenn sie zum aktiven eingebauten Fehler passte.

## Backup B4 · Wischen zum Bestätigen
Szene: b04-swipe
Zeitfenster: Nur bei Rückfrage
Anker: Mindestens 75 % der Strecke · Bewusste Hürde · Nicht gegen Tippen getestet
Quelle: Thesis Kap. 3.5

[Bei Fragen zur Bestätigungsgeste öffnen. Ein Klick zeigt das Wischen.]

Freigeben heißt, den Regler über mindestens 75 Prozent der Strecke zu ziehen. Ein kurzer, ruhender Kontakt gibt nichts frei. Grundlage ist eine Touch-Studie, nach der sich unbeabsichtigte Berührungen in Zeit, Ort und Bewegung von absichtlichen unterscheiden.

[Klick 1 · Wischen]

Meine Studie hat Wischen nicht gegen Tippen verglichen. Ich behaupte also nicht, dass Wischen allgemein sicherer ist. Und zur Abgrenzung: Wischen auf diesem Regler ist eine Freigabe, kein Eingreifen. Wischen auf der App während eines Laufs geht an die App; Tippen pausiert.

## Backup B5 · Fehler und Recovery
Szene: b05-fehler-recovery
Zeitfenster: Nur bei Rückfrage
Anker: Journal vor Dispatch · Einmal ausführen · Unklar → Pause · Studie: zurückspulen oder ablehnen · 17 Korrekturketten
Quelle: Thesis Kap. 3.4, 3.6, Tab. 3.1, 4.2.2, Tab. 5.10

[Bei Fragen zu Fehlschlägen, Wiederholung oder Rückgängig öffnen.]

Im Normalmodus schreibt Caddie vor jeder Ausführung einen Versuch ins Journal, führt die Aktion einmal aus und prüft frische Beobachtungen auf den erwarteten Zustand. Lehnt Android ab, fehlt der Zustand oder ist das Ergebnis unklar, pausiert der Lauf. Caddie wiederholt nicht automatisch – eine doppelte Nachricht oder Zahlung kann schlimmer sein als eine Rückfrage. Weil der erwartete Zustand vom Modell kommt, belegt eine erfolgreiche Prüfung nur diesen Zustand, nicht das ganze Ziel.

In der Studie spulte eine Korrektur vor dem Commit zum betroffenen Schritt zurück. Nach dem Commit gab es bei Kalender, Nicht stören, Notiz und Musik einen Ausgleichsablauf; Route und Zahlung boten danach keinen Ausgleich. Aufgezeichnet wurden 17 Korrekturketten in 16 von 54 Fehleraufgaben.

## Backup B6 · Retrieval-Evaluation
Szene: b06-retrieval
Zeitfenster: Nur bei Rückfrage
Anker: Pilot 7 Skills: 70 % vs. 20 % · Navigationshinweise 12/18 vs. 12/18 · Finaler Korpus nicht evaluiert
Quelle: Thesis Kap. 3.7.1, Tab. 3.2

[Bei Fragen zum Nutzen von Skills oder RAG öffnen.]

Im Pilot mit sieben Skills und zurückgehaltenen Anfragen fand die Bedeutungssuche den richtigen Skill in 70 Prozent auf Platz eins, exakte Trigger-Wörter in 20 Prozent. Am meisten half das bei Umschreibungen und bei Anfragen in einer anderen Sprache; für ihre eigenen Wortlaute waren exakte Trigger besser. Deshalb kombiniert Caddie beides.

Ein späteres Experiment mit automatisch erkundeten Navigationshinweisen ergab je 12 von 18 erfolgreichen Läufen mit und ohne Hinweise – kein Vorteil beim Abschluss. Beides waren frühe Entwicklungsexperimente; der heutige Korpus mit 76 Skills ist nicht evaluiert.

## Backup B7 · Memory-Kontrolle
Szene: b07-memory
Zeitfenster: Nur bei Rückfrage
Anker: Nach der Studie ergänzt · Sichtbar, änderbar, löschbar · Nur vier Vorlieben automatisch · Keine Erlaubnis · Nicht gemessen
Quelle: docs/personal-memory.md

[Bei Fragen zum persönlichen Memory öffnen.]

Persönliches Memory habe ich nach der Studie ergänzt. Es gibt es nur im Normalmodus, in den Studienprofilen nicht, und sein Nutzen ist nicht gemessen.

Die Einträge sind verschlüsselt mit einem eigenen Schlüssel auf dem Handy. Ich kann alles einsehen, ändern, löschen und Vorschläge annehmen oder ablehnen. Automatisch gespeichert werden nur vier Vorlieben aus einer vollständigen eigenen Äußerung: Antwortsprache, Antwortlänge, Maßeinheiten und Zeitformat. Alles andere wartet auf meine Freigabe und wird bis dahin nicht verwendet.

Notizen geben nie eine Aktion frei. Und Löschen wirkt auf künftige Anfragen; was schon gesendet wurde, lässt sich nicht zurückholen.

## Backup B8 · MCP
Szene: b08-mcp
Zeitfenster: Nur bei Rückfrage
Anker: Gleiche Registry · Handy bleibt lokal · Gleiche Prüfung · Nur JSON-Objekt geprüft · Studie ohne MCP
Quelle: Thesis Kap. 3.2, 3.3.2, 3.5, 3.7.1

[Bei Fragen zu MCP oder externen Tools öffnen.]

Caddie hat einen MCP-Client. Tools externer Server landen in derselben Registry wie die eingebauten. Beobachten und Bedienen des Handys bleibt aber bei den eingebauten Android-Tools; MCP ist eine optionale Erweiterung für externe Dienste.

Externe Aufrufe laufen durch dieselben Stationen: Route, Aufsicht, Eingriffsprüfung, Protokoll. Unbekannte entfernte Tools brauchen eine Freigabe, außer sie sind ausdrücklich als nur-lesend ausgenommen. Eine Schwäche: Der Client prüft nur, dass die Argumente ein JSON-Objekt sind, nicht das volle Schema des Servers. Die Studie brauchte keinen MCP-Server. Der Claude-Vergleichslauf nutzte noch einen MCP-Server mit ADB vom PC aus.

## Backup B9 · GUI-Alternativen
Szene: b09-gui-alternativen
Zeitfenster: Nur bei Rückfrage
Anker: Abkürzungen 59 → 19 s / 22 → 16 s · Erfolg gleich · AppFunctions + Accessibility als Rückfall
Quelle: Thesis Kap. 3.7.2, Tab. 3.3, Kap. 7.2

[Bei der Frage „Warum nicht APIs oder Intents statt GUI?“ öffnen.]

Bei acht Einstellungsaufgaben habe ich sichtbares Navigieren mit direkten Abkürzungen über Intents und Deep Links verglichen. Im frühen Lauf: 7 von 8 gegenüber 8 von 8, 59 gegenüber 19 Sekunden. Im späteren Lauf: beide 15 von 16, 22 gegenüber 16 Sekunden. Abkürzungen waren also schneller; einen allgemeinen Erfolgsvorteil zeigt das nicht, und die Wirkung auf Verständnis und Kontrolle habe ich nicht gemessen.

Für die Zukunft sehe ich einen Hybrid: strukturierte Schnittstellen wie Android AppFunctions, die noch experimentell sind, Accessibility als Rückfall und zur Prüfung, MCP für entfernte Dienste – mit derselben Aufsicht für alle Wege.

## Backup B10 · Jev: Future Work zur Latenz
Szene: b10-jev-latenz
Zeitfenster: Nur bei Rückfrage
Anker: Neuer Ausblick · Jev wählt typisiert aus Kandidaten · LLM für offene Fälle · Android prüft weiter · Latenz UND Fehler messen
Quelle: Backup B10; TypeSafe-Herstellerquelle; Li et al. 2026 (Preprint)

[Bei Fragen zur Geschwindigkeit öffnen. Klar sagen: neuer Ausblick, nicht in der Thesis evaluiert.]

Eine Idee für schnellere Entscheidungen ist Jev von TypeSafe: ein Modell, das schnell und typisiert aus vorgegebenen Optionen auswählt und keinen freien Text erzeugt. Für Caddie wäre das eine Hybrid-Hypothese: Android liefert die erlaubten Aktionen als Kandidaten, Jev wählt aus.

[Klick 1 · LLM-Pfad]

Das allgemeine Sprachmodell bliebe für offene Planung, Text und schwierige Fälle zuständig. Beide Wege liefern nur Vorschläge; Android prüft und gibt weiter frei wie bisher.

[Klick 2 · Messen]

Getestet ist das nicht. Typkorrekt heißt nicht semantisch richtig. Der nächste Test wäre: dieselben Caddie-Aufgaben, Gesamtlatenz vom Auftrag bis zum beobachteten Ergebnis und Fehlerrate – gegen den heutigen Weg.

## Backup B11 · Methodik
Szene: b11-methodik
Zeitfenster: Nur bei Rückfrage
Anker: 18 Slots · Kein LLM im Messlauf · Separation → Bayes · Klick 1: Zählungen 35 / 8 / 11 · Klick 2: nicht zuordenbar, Sensitivität [−8; +35] · Keine Präregistrierung behaupten
Quelle: Thesis Kap. 4, 5.4, Tab. 5.5, 5.6

[Nur die Absätze zur gestellten Frage sprechen. Bei Fragen zu Zählungen oder nicht zuordenbaren Berichten direkt weiterklicken.]

Die Probanden P02 bis P19 decken die 18 Slots der balancierten Zuweisungsmatrix genau ab. Diese Balance erklärt die Stichprobengröße; eine Power-Berechnung gab es nicht.

Vorgegebene Aktionsfolgen ohne LLM-Planung halten Bedingung und Fehler vergleichbar. Sonst könnten Unterschiede auch aus zufälliger Modellplanung kommen.

Für die Fehlererkennung habe ich das vorab festgelegte Bayes-Modell verwendet, weil bei den kritischeren Aufgaben in C2 und C3 unter den eindeutigen Fällen kein einziger Fehler übersehen wurde. Diese vollständige Trennung macht das geplante klassische Modell instabil. Das Bayes-Modell hat dieselbe Struktur und berichtet Credible-Intervalle, keine p-Werte.

[Falls nach der Einstufung der Aufgaben gefragt wird:]

In der Thesis, Kapitel 4.2.2, heißt das „task criticality“; bisher habe ich das als Folgenstufe übersetzt: Jede Aufgabe war fest danach eingestuft, wie schwerwiegend ein falsches Ergebnis in genau diesem Szenario wäre. Das ist keine allgemeine Rangfolge der Apps und keine Aussage darüber, wie wahrscheinlich ein Fehler ist. Im Vortrag sage ich dafür weniger kritische und kritischere Aufgaben.

[Falls nach Präregistrierung gefragt wird:]

Eine prospektive Präregistrierung behaupte ich nicht. Analyseentscheidungen wurden während oder nach der Datenerhebung dokumentiert, vor den berichteten statistischen Ergebnissen.

[Klick 1 · Zählungen]

Hinter den Prozentwerten stehen 54 Durchgänge mit eingebautem Fehler, 18 je Bedingung. 35 Fehler wurden erkannt, 8 nicht, 11 Berichte ließen sich nicht zuordnen. Die Raten zählen nur die zuordenbaren Fälle: C1 13 von 17, C2 11 von 12, C3 11 von 14. Nicht zuordenbar waren in C1 einer, in C2 sechs, in C3 vier. Dieselben 54 Durchgänge nach Einstufung der Aufgabe gruppiert: weniger kritisch 15 von 22, mit fünf nicht zuordenbaren; kritischer 20 von 21, mit sechs. Bedingung und Einstufung sind also zwei Sichten auf dieselben Durchgänge, keine zusätzlichen Gruppen.

[Klick 2 · Nicht zuordenbar und Sensitivität]

Alle elf hatten den Fehler-Schritt erreicht und eine Auffälligkeit berichtet. Den falschen Wert, das Ziel oder den Parameter haben sie aber nicht klar identifiziert. Deshalb zähle ich sie weder als erkannt noch als nicht erkannt; sie sind nicht in der Hauptanalyse.

In Sensitivitätsanalysen habe ich sie einmal alle als erkannt und einmal alle als nicht erkannt gezählt. Im strengeren Fall, alle elf als nicht erkannt, bleibt die Richtung: kritischere Aufgaben vor weniger kritischen. Das Intervall reicht aber von minus 8 bis plus 35 Prozentpunkten und schließt null ein. Ein statistisch klar abgesicherter Unterschied ist das nicht.

## Backup B12 · Präferenzen: weniger kritisch → kritischer
Szene: b12-praeferenz-matrix
Zeitfenster: Nur bei Rückfrage
Anker: Zeilen weniger kritisch · Spalten kritischer · Zehn C3 → C1 · Beschreibend
Quelle: Thesis Abb. 5.4, Kap. 5.6

[Bei Fragen zur Punktbewegung öffnen.]

Die gepaarten Angaben als Tabelle: Zeilen sind die Wahl bei weniger kritischen, Spalten die Wahl bei kritischeren Aufgaben. Zehn Probanden wechseln von C3 zu C1, drei von C3 zu C2, einer bleibt bei C3. Das beschreibt diese 18 Probanden und ist kein Test, welcher Modus objektiv sicherer ist.

Allgemein auf Platz eins: C2 bei zehn Probanden, C1 bei sechs, C3 bei zwei – eine andere Frage als die Wahl für weniger kritische oder kritischere Aufgaben.

## Backup B13 · Belastung (Raw NASA-TLX)
Szene: b13-belastung
Zeitfenster: Nur bei Rückfrage
Anker: 21,25 / 16,85 / 19,81 · C2 deskriptiv niedriger · p = .433 · Keine Gleichwertigkeit
Quelle: Thesis Tab. 5.3

[Bei Fragen nach Belastung oder einem „Sieger“ öffnen.]

Die Belastung habe ich mit Raw NASA-TLX erhoben, von null bis 100, höher heißt belastender. Mittelwerte: 21,25 in C1, 16,85 in C2, 19,81 in C3. 14 von 18 Probanden hatten in C2 eine niedrigere Belastung als in C1.

Das Modell belegt aber keinen Gesamtunterschied, p gleich 0,433, und auch die geplanten Vergleiche waren nach Holm-Korrektur nicht signifikant. C2 ist hier also beschreibend weniger belastend; ein belegter Vorteil ist das nicht, und gleich gut heißt es auch nicht.

## Backup B14 · Was an der Demo echt ist
Szene: b14-demo-hinweis
Zeitfenster: Nur bei Rückfrage
Anker: Echter Sprachstart und Android · Feste Aktionsfolge · Nicht gesendet
Quelle: Backup B14

[Bei Fragen zur Einstiegsdemo öffnen.]

Echt sind der Sprachstart, die Ausführung über Accessibility, das Overlay und der Freigabedialog auf dem Pixel 7. Die Aktionsfolge ist für die Demo fest vorgegeben und nutzt kein Sprachmodell, damit sie reproduzierbar ist. Die Mail wurde nicht gesendet; die Aufnahme endet vor der Freigabe.

Drei Dinge bleiben getrennt: der normale Modellbetrieb, die reproduzierbare Demo und die festen Studienabläufe.

## Backup B15 · Bei kritischeren Aufgaben häufiger berichtet
Szene: ergebnis-folgen
Zeitfenster: Nur bei Rückfrage
Anker: Dieselben Fehlerfälle wie Folie 18, nach Einstufung der Aufgabe gruppiert · weniger kritisch 68,2 % · kritischer 95,2 % · Sicherheit hängt von der Auswertung ab
Quelle: Thesis Tab. 5.5, Kap. 5.4.1

[Bei Fragen öffnen, ob die Schwere eines Fehlers die Erkennung beeinflusst hat. Nicht als Hauptergebnis vortragen.]

Das sind dieselben Fehlerfälle wie auf Folie 18, nur danach gruppiert, ob die Aufgabe weniger kritisch oder kritischer war. Bei den weniger kritischen Aufgaben – Route, Foto, Musik – wurden 68,2 Prozent der Fehler erkannt. Bei den kritischeren – Kalender, Ruhe, Bank – 95,2 Prozent.

Bei den kritischeren Aufgaben wurden Fehler häufiger berichtet. Wie sicher dieser Unterschied ist, hängt von der Auswertung ab.

[Bei Rückfragen zu Zählungen und Sensitivität: Backup B11.]

## Backup B16 · Die Lücke: Wann soll ich prüfen?
Szene: landkarte
Zeitfenster: Nur bei Rückfrage
Anker: Qualitative Karte · Umgebung · Zeitpunkt verglichen · Caddie
Quelle: Thesis Tab. 2.1; Datei 04-landkarte

[Bei Fragen zum Forschungsstand oder zur Lücke öffnen.]

Diese Karte ordnet verwandte Arbeiten nach zwei Fragen. Horizontal: Läuft das im Web, am Desktop oder simuliert – oder live auf dem Smartphone? Vertikal: Wurde der Zeitpunkt der Kontrolle gezielt verglichen? Die Positionen sind eine qualitative Einordnung, keine Messung.

„When Should Users Check?“ vergleicht zum Beispiel Zwischenkontrollen mit einer Bestätigung am Ende – allerdings am Desktop und ohne eine Bedingung, in der nur freiwillig eingegriffen wird. Mobile Systeme zeigen Fortschritt und erlauben Pausen. Welcher Kontrollzeitpunkt Menschen hilft, ist damit aber noch nicht untersucht.

[Klick 1 · Caddie]

Hier setzt Caddie an: ein System, das live auf dem Smartphone läuft und in dem sich der verpflichtende Bestätigungszeitpunkt gezielt verändern lässt. Daraus entstehen die drei Bedingungen C1, C2 und C3.

## Backup B17 · Warum nicht einfach …?
Szene: warum-nicht
Zeitfenster: Nur bei Rückfrage
Anker: PC → App · Mehrere Aktionen → ein geprüfter Schritt · Prompt → Gate · „fertig“ → Beobachtung
Quelle: Thesis Kap. 3.2–3.4

[Bei Fragen zu Designentscheidungen öffnen: PC vs. App, mehrere Aktionen, Prompt vs. Gate, „fertig“.]

Vier Entscheidungen lassen sich als „Warum nicht einfach …?“ erklären.

[Klick 1 · Links: Agent auf dem PC]

Warum nicht den Agenten auf dem PC lassen? Das geht als Entwicklungsaufbau, braucht aber einen eigenen Rechner-Prozess, eine Debug-Verbindung und Abstimmung zwischen beiden Seiten.

[Klick 2 · Rechts: Loop in der App]

Bei Caddie besitzt deshalb die App den Ablauf: Eingabe, Loop, Aufsicht, Ausführung und Protokoll gehören zusammen.

[Klick 3 · Links: mehrere Aktionen pro Antwort]

Warum nicht das Modell gleich mehrere Aktionen auf einmal planen lassen? Das spart Modellaufrufe. Aber der Plan läuft dann weiter, auch wenn sich der Bildschirm zwischendurch verändert hat – und dazwischen gibt es keinen Moment zum Prüfen.

[Klick 4 · Rechts: ein geprüfter Schritt, dann neu beobachten]

Caddie nimmt pro Runde genau einen Tool-Aufruf an. Jede Aktion wird einzeln geprüft, kann einzeln freigegeben oder korrigiert werden, und danach wird neu beobachtet. Das ist eine Entscheidung für Kontrolle, kein gemessener Geschwindigkeitsvorteil.

[Klick 5 · Links: „Frag vorher“ im Prompt]

Warum nicht einfach in den Prompt schreiben: „Frag vorher“? Das ist eine Bitte an das Modell, keine technische Grenze.

[Klick 6 · Rechts: Gate vor dem Dispatch]

Das Gate sitzt im Android-Loop vor der Ausführung. Fehlt die Freigabe, wird nichts ausgeführt. „Bitte vorher fragen“ klebt also nicht nur als digitaler Post-it am Modell.

[Klick 7 · Links: Modell meldet „fertig“]

Und warum nicht aufhören, sobald das Modell „fertig“ sagt? Den Dark Mode haben wir gesehen.

[Klick 8 · Rechts: fertig erst nach Beobachtung]

Abschluss gibt es erst nach einer neuen Beobachtung. Bleibt es unklar, pausiert Caddie.

## Backup B18 · Skills, Retrieval und Memory
Szene: wissen
Zeitfenster: Nur bei Rückfrage
Anker: „Bildschirm dunkel“ · Skill über Bedeutung · Memory = heute ergänzt, nicht untersucht · Hinweise ≠ Erlaubnis
Quelle: Thesis Kap. 3.3.3, 3.7.1; docs/personal-memory.md

[Bei Fragen zu Skills, Retrieval oder Memory öffnen.]

Woher weiß das Modell, wie eine Aufgabe in einer App geht? Ein einfaches Beispiel. Ich sage: „Mach den Bildschirm bitte dunkel.“

[Klick 1 · Skill per Retrieval]

Vor der ersten Modellanfrage sucht Caddie passende Skills. Ein Skill ist eine kurze Anleitung: wann er passt, wie man vorgeht und was man danach prüft. Gefunden wird hier „Turn on Dark Mode via Settings“ – obwohl ich weder „Dark Mode“ gesagt noch Englisch gesprochen habe. Die Suche vergleicht Wörter und Bedeutung; die Bedeutungsvektoren rechnet das Handy selbst.

[Klick 2 · Persönliches Memory]

Dazu kann persönliches Memory kommen, zum Beispiel: Antworten bitte kurz – weil ich das früher so gesagt habe. Das ist eine Erweiterung, die ich nach der Studie ergänzt habe. Sie wurde in der Studie nicht untersucht und ihr Nutzen ist nicht gemessen. Man kann alles einsehen, ändern und löschen.

[Klick 3 · Modellanfrage]

In die Modellanfrage kommen der Auftrag und höchstens vier solche Hinweise. Das Entscheidende: Hinweise sind Material. Sie erlauben nichts. Gate, Prüfung und Beobachtung bleiben genau gleich.

[Bei Rückfragen zur Suchqualität im Pilot: Backup B6. Zum Memory: Backup B7.]

## Backup B19 · Alltag und Studie getrennt
Szene: alltag-studie
Zeitfenster: Nur bei Rückfrage
Anker: Alltag: Modell schlägt vor, Regel → Freigabe · Studie: feste Abläufe, kein LLM · C1 konfigurierte Schritte · C2 gebündelt vor Commit
Quelle: Thesis Kap. 3.2, 3.5, 3.6, 4.2.1

[Bei Fragen zum Unterschied zwischen Normalmodus und Studie öffnen.]

Eine wichtige Trennung: Im Alltag, also im Normalmodus, schlägt das Modell die Schritte vor, mit Skills und Retrieval. Eine Regel entscheidet, welche einzelne Aktion auf Freigabe wartet.

[Klick 1 · Studie]

In der Studie war das bewusst anders: feste Abläufe, kein Sprachmodell. So hatte jeder Proband dieselben Aktionen, denselben eingebauten Fehler und dieselbe Prüfung am Ende. Die Bedingung bestimmte die Pflichtfreigaben: C1 bestätigt jeden konfigurierten, aufgabenrelevanten Schritt. C2 zeigt eine gebündelte Prüfung aller Werte direkt vor dem Commit, also vor dem Senden oder Bezahlen. C3 verlangt keine Pflichtfreigabe.

[Klick 2 · Was untersucht wurde]

Untersucht wurde also die Aufsicht – nicht die Planung des Modells. Und der Normalmodus ist keine dieser drei Bedingungen.

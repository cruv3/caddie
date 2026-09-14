# Studienbetrieb mit der Android-App

## Zielarchitektur

Der produktive Studienpfad läuft vollständig auf dem Studienhandy:

- Caddie hostet Studienportal, Workflow und Room-Datenbank.
- Der native Agent Loop nutzt Android-Tools und den Accessibility Service.
- Qwen läuft über den Model Gateway auf dem Homeserver.
- Ein Browser öffnet das vom Handy bereitgestellte Portal über Tailscale.
- Python, ADB und UIAutomator gehören nicht zum Studienruntime-Pfad. ADB ist nur
  außerhalb der App für Installation und technische Diagnose erlaubt.

## Einmalige Vorbereitung

1. Tailscale auf Handy, Investigator-Laptop und Homeserver verbinden.
2. Den expliziten Studienbuild und die acht Fixture-Apps bauen. Der normale
   Installationshelfer installiert keine Studien-App:

   ```powershell
   .\scripts\install-study-fixtures.ps1 -IncludeStudyFixtures
   adb install -r app/build/outputs/apk/study/debug/app-study-debug.apk
   ```

3. In Caddie prüfen, dass Mikrofon, Benachrichtigungen, Overlay und
   Accessibility grün sind.
4. **Study-Portal hosten** einschalten.
5. Vor dem Öffnen per Tailscale-ACL sicherstellen, dass nur das Studienhandy
   und der Investigator-Laptop den Portal-Port `8787` erreichen können.
6. Die in der App angezeigte URL auf dem Investigator-Laptop öffnen, zum
   Beispiel `http://100.x.x.x:8787/study/app`.

Bei mehreren verbundenen Geräten `-Serial <adb-serial>` an den Helfer und
`-s <adb-serial>` an ADB übergeben. Die Fixture-Installation baut die Studien-App,
installiert selbst aber nur die Fixtures. Die Studien-App hat eine eigene
Application-ID und ersetzt den normalen Build nicht.

Das native Portal arbeitet derzeit bewusst ohne Moderator-PIN. Jeder Tailnet-
Peer, der den Portal-Port erreicht, erhält daher Versuchsleiterzugriff. Das
Portal niemals in einem unbeschränkten Tailnet oder über das öffentliche
Internet bereitstellen.

Der technische Health-Endpunkt lautet
`http://<telefon-ip>:8787/study/app/api/health`; er meldet nur die Erreichbarkeit
des Portalservers. Eine Sitzung darf nur vorbereitet werden, wenn das Portal
das Telefon als **bereit** und den Durchgang als **idle** zeigt. Die native
Studien-Readiness verlangt Accessibility und geladene Studien-Spezifikationen.
Model Gateway und MCP sind zusätzliche Diagnosen, keine Voraussetzungen für
die deterministischen gemessenen Aufgaben. Die freie Normalmodus-Übung benötigt
dagegen eine funktionierende Modellverbindung.

## Technische Generalprobe

Eine Generalprobe nur vor Beginn der Datenerhebung und niemals in einer
bereits produktiv genutzten Teilnehmerdatenbank durchführen.

1. Eine eigene synthetische Sitzung im Modus **Test** verwenden und im
   Studienprotokoll als Generalprobe kennzeichnen. Ihre Daten dürfen nicht in
   den Teilnehmerdatensatz eingehen.
2. Consent, Training und alle sechs Aufgaben in der zugewiesenen Reihenfolge
   durchlaufen.
3. Bei C1 jede sichtbare Einzelbestätigung prüfen.
4. Bei C2 genau eine finale Zusammenfassung und Commit-Bestätigung prüfen.
5. Bei C3 während einer laufenden Agentenaktion das Display physisch berühren.
   Caddie muss pausieren und nach etwa 2,15 Sekunden ohne weitere Berührung
   fortsetzen.
6. Kontrollierte Fehler, Aufgaben- und Blockfragebögen, Demografie, Präferenz,
   Interview und Debrief abschließen.
7. Research ZIP erzeugen und den Inhalt prüfen.
8. Danach den nativen Geräte-Reset aus dem Portal ausführen.

Ein per `adb input` erzeugter Tap gilt nicht als C3-Nachweis.

## Live-Sitzung

1. Im Investigator-Bereich **Neue Sitzung** öffnen.
2. Eine bisher unbenutzte pseudonyme Teilnehmer-ID und Modus **Live** wählen.
   Der Parser akzeptiert positive P-Nummern über `P18` hinaus; die Zuweisung
   wiederholt einen balancierten Block mit 18 Slots. Für eine Replikation einen
   vollständigen Block verwenden. Die finale Thesis-Kohorte war `P02` bis `P19`
   mit 18 Teilnehmenden; `P01` ist kein zusätzlicher Fall dieser Kohorte.
3. **Sitzung anlegen**.
4. **Einwilligung starten**, an den Teilnehmer übergeben und die gespeicherte
   Einwilligung bestätigen.
5. Training durchführen und **Training abschließen**.
6. Für jeden Durchgang:

   - **Nächsten Durchgang vorbereiten**;
   - angezeigte Aufgabe, Bedingung, Kritikalität und Fehlerzuweisung prüfen;
   - **Aufgabenkarte freigeben** und an den Teilnehmer übergeben;
   - den gesprochenen Auftrag über Caddie starten;
   - nach dem Lauf den Durchgang beenden;
   - Beobachtung dokumentieren;
   - Teilnehmerfragebogen freigeben und abschließen;
   - den vom Portal geforderten Reset bestätigen.

7. Nach dem letzten Durchgang Ranking, Abschlussfragen, Interview und Debrief
   vollständig abschließen.

Aufgabe, Bedingung, Reihenfolge und kontrollierter Fehler kommen aus der
unveränderlichen Studienzuweisung. Sie dürfen während einer Sitzung nicht
manuell ersetzt werden.

## Exporte und Identitätsdaten

Nach Abschluss stellt der Investigator-Bereich getrennt bereit:

- **Research ZIP**: pseudonyme Forschungs- und Workflowdaten;
- **Consent CSV**: direkte Einwilligungsdaten;
- **Course bonus CSV**: separat erfasste Kursbonusdaten;
- **Exportarchiv erstellen**: private, app-eigene Archivkopie.

Research ZIP enthält weder Consent- noch Kursbonusdaten. Kursbonusnachweise
nach bestätigter Übermittlung über **Dauerhaft löschen** entfernen. Exporte
nicht in öffentliche Handyordner kopieren und nicht über Teilnehmerkonten
teilen.

## Abbruch und Wiederaufnahme

- Einen unerwartet armierten oder laufenden Durchgang im Portal bestätigt
  abbrechen.
- Bei roter Readiness keinen zweiten Auftrag senden. Accessibility,
  Tailscale und Model Gateway prüfen und danach denselben Portalzustand neu
  laden.
- Eine Netzwerkpause führt nicht zur automatischen Wiederholung einer
  möglicherweise ausgeführten realen Aktion.
- Während eines C1/C2-Dialogs nur die sichtbaren Bestätigungsflächen verwenden.
- Caddie nicht deinstallieren und App-Daten nicht löschen, solange Studien- oder
  Identitätsdaten noch nicht exportiert und kontrolliert entfernt wurden.

## Diagnose

UIAutomator-Aufnahmen dienen ausschließlich dem Vergleich mit dem nativen
Accessibility-Baum. Die erhaltenen Fixtures, Materialien und historischen
Evaluationsdaten sind in `research/README.md` eingeordnet.
Ein UIAutomator-Dump darf niemals eine Produktionsaktion auswählen oder
autorisieren.

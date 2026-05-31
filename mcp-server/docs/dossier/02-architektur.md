# 02 - Architektur (wie das System funktioniert)

Ende-zu-Ende: vom Auftrag des Nutzers bis zur ausgefuehrten Aktion am Geraet,
und zurueck zur sichtbaren Begleit-UI.

## Grossbild

```
Nutzer -> (Text oder Sprache) -> Android-App
   App  -> POST /task -> MCP-Server (Python)
   MCP-Server: Agent-Loop  <-> LM Studio (Inferenz, /v1/chat/completions)
   Agent-Loop -> Tool-Aufruf -> Backend (ADB oder HTTP-Bridge) -> Geraet
   Geraet-Ereignisse -> EventBus -> SSE /events -> Overlay-Pill auf dem Handy
   Nutzer-Eingriff am Handy -> POST /control -> Agent-Loop (Pause/Korrektur/Confirm)
```

Zwei Prozesse: die **App** auf dem Geraet und der **MCP-Server** auf dem PC.
LM Studio ist ein dritter Prozess (nur Inferenz).

## Die zwei Backends

Wie eine Aktion physisch aufs Geraet kommt:

- **ADB-Backend**: der Server schickt `adb`-Befehle (input tap/swipe,
  uiautomator dump fuer die Element-Liste, screencap). Braucht nur ein per USB/
  Emulator verbundenes Geraet, keine laufende App. Robust, wird fuer die Studien
  benutzt.
- **HTTP-Bridge-Backend**: die App betreibt einen kleinen HTTP-Server (Port
  8765); der AccessibilityService fuehrt Gesten aus. Braucht die App + aktiven
  AccessibilityService. Noetig fuer die Interaktions-Features (z. B. Touch-
  Erkennung), weil nur hier die App selbst die Gesten dispatcht.

Beide bieten dieselben Operationen (tap, swipe, type, key, screenshot,
list_elements). Auswahl per Env `LLM_SMARTPHONE_BACKEND` (Default `adb`).

## Werkzeuge (Tools) statt Pixel-Klicks

Der Agent bedient das Geraet ueber eine **abstrahierte Werkzeug-Schicht**
(`smartphone_*`), z. B. `open_app`, `open_url`, `tap_coordinates`, `swipe`,
`type_text`, `press_button`, `take_screenshot`, `list_elements`,
`uninstall_app`, plus Skill-Tools und die Terminal-Tools `smartphone_done` /
`smartphone_failed`. Das ist die "UI-Abstraktionsschicht" der Arbeit: das Modell
denkt in Handlungen, nicht in Koordinaten-Raten.

Zwei feste Muster auf jedem Action-Tool:

- **`why`-Parameter**: ein deutscher 1.-Person-Satz ("Tippe auf den Erlauben-
  Button"), der live auf der Overlay-Pill erscheint. Einziger Weg, die Absicht
  des Modells auf dem Geraet sichtbar zu machen, da MCP rein tool-basiert ist
  (die freie Reasoning-Ausgabe geht nicht an den Server).
- **Terminal-Paar**: jede Sitzung endet mit genau einem `smartphone_done(...)`
  (Erfolg, danach optional `smartphone_save_skill`) oder `smartphone_failed(...)`.
  Ohne diese Tools gaebe es kein Signal "Aufgabe fertig".

## Settle-Gate (Timing)

Action-Tools warten nach der Aktion, bis sich der UI-Baum geaendert UND fuer
~350 ms stabilisiert hat, bevor sie zurueckkehren. Grund: schwache Modelle
feuerten frueher Tools im Millisekunden-Takt ab; das naechste `list_elements`
sah dann noch den alten Screen (Stale-Observation). Eine *physische* Sperre auf
dem Rueckgabe-Pfad bremst zuverlaessig, anders als eine Protokoll-Regel, die ein
Modell ignorieren kann. Per Env abschaltbar (fuer "schnell vs. langsam"-
Vergleiche).

## Skill-Bibliothek

Erfolgreiche Loesungswege werden als Markdown-Rezept gespeichert
(`smartphone_save_skill`), mit ID-Schema `kategorie.ziel_methode`
(z. B. `display.dark_mode_on_settings`). Beim naechsten passenden Auftrag wird
das Rezept als Kontext geladen. Live-Beobachtung schlaegt das Rezept: weicht die
UI ab, passt der Agent an und meldet Divergenzen.

## Der Agent-Loop (Kernstueck, neu)

Frueher fuhr **LM Studio** den Tool-Loop server-seitig: eine Agent-Sitzung war
ein einziger, undurchsichtiger Aufruf - kein Punkt zum Eingreifen. Jetzt besitzt
der Agent den Loop selbst, Turn fuer Turn:

```
messages = [system, user]
solange nicht fertig:
    antwort = lmstudio.chat_completion(messages, tools)   # genau EINE Antwort
    pause_point()                                         # Pause/Stop/Korrektur
    falls tool_calls:
        pro call:
            risiko = classify(call)                       # Swipe-to-Confirm
            falls riskant: warte auf Bestaetigung
            ergebnis = dispatcher.call(call)              # Tool ausfuehren
            messages += ergebnis
    sonst: fertig
```

LM Studio ist damit nur noch Inferenz (`/v1/chat/completions`, gibt Text oder
`tool_calls` zurueck). Sicherheitsnetze: max. 25 Tool-Calls, max. 40 Turns,
Confirm-Timeout 120 s. Module: `agent/agent_loop.py`, `tool_bridge.py`,
`run_control.py`, `risk.py`, `lmstudio.py`, `http_api.py`, `event_bus.py`.

Dieser Umbau ist die Vorbedingung fuer die gesamte Interaktions-Schicht - nur
zwischen zwei Tool-Calls gibt es einen kontrollierten Moment zum Eingreifen.

## Interaktions-/Sicherheits-Schicht

Steuerung von aussen ueber `POST /control` (Aktion + optional Text), umgesetzt
auf `RunControl` (thread-sicher, `threading.Event`):

- **Pause / knopfloser Eingriff**: Der AccessibilityService sieht alle Klicks.
  Die Gesten des Agenten laufen ueber denselben Dienst, also weiss er, welche
  Beruehrungen *er* ausgeloest hat. Ein Klick waehrend eines Laufs, der nicht
  vom Agenten kam = Mensch -> Pause. Nach ~4 s Touch-Ruhe automatisch weiter.
  Beim Fortsetzen nimmt der Agent den Screen **neu wahr** (Hinweis-Nachricht,
  bewusst ohne Bild, da nicht jedes Modell multimodal ist).
- **Mid-run-Sprach-Korrektur**: Wake-Word "Jarvis". Laeuft ein Agent, wird das
  Gesprochene als Korrektur in den laufenden Dialog eingespeist, nicht als neuer
  Auftrag.
- **Swipe-to-Confirm**: Vor jedem Tool-Call klassifiziert `risk.classify` zwei
  Ebenen - destruktives Tool (uninstall/install) ODER Tap auf ein Element mit
  Risiko-Wort ("Deinstallieren", "Bezahlen", "Loeschen" ...). Trifft eins zu,
  fuehrt der Agent nicht aus, sondern zeigt eine Wisch-Karte und blockiert, bis
  confirm/decline kommt. Timeout = abgelehnt.
- **Korrektur nach Run-Ende (Folge-Task)**: Sagt der Nutzer kurz nach "fertig"
  etwas, ist der Run schon beendet. Geloest ueber `follow_up`: der neue Auftrag
  bekommt den vorigen Lauf als Kontext (neutral formuliert, das Modell
  entscheidet, ob es eine Korrektur ist). Beleg-Flag `follow_up_context` in der
  `/task`-Antwort. Window-Kopplung an `task_finished` + Fallback, falls eine
  Live-Korrektur ins Leere laeuft.

## Sichtbarkeit: EventBus und Overlay

Der Loop feuert Ereignisse (`task_started/finished`, `tool_call_started/
finished`, `task_paused/resumed`, `confirmation_required/resolved`) auf einen
EventBus. Die Overlay-App abonniert `GET /events` (SSE) und zeigt live die Pill
("Tippe auf ...", "pausiert - du bist dran", die Wisch-Karte). So sieht der
Nutzer, was der Agent gerade tut - die Keimzelle der Transparenz-Konditionen.

## Owner/Worker-Detail

Lief der Server unter LM Studios Integrations-Mechanismus, gab es bis zu zwei
MCP-Subprozesse; der erste, der Port 8787 bindet, wird "Owner" und betreibt den
SSE-Server, die anderen leiten ihre Events dorthin um. Fuer die Studien wird der
Server eigenstaendig gestartet (`experiments/start_task_server.py`), was diese
Komplexitaet umgeht.

"""Startet AgentHttpServer eigenständig (ohne FastMCP-stdio).

Damit ist /task auf :8787 erreichbar, auch wenn LM Studio die MCP-Integration
gerade nicht aktiviert hat. Dieser Pfad ist nur für den Experiment-Runner gedacht
— LM Studio braucht trotzdem eine konfigurierte `llmsmartphone`-Integration in
seiner mcp.json, damit das Modell während eines /task-Aufrufs Tools rufen darf.
"""

from __future__ import annotations

import signal
import sys
import time

from llmsmartphone.agent import AgentHttpServer
from llmsmartphone.context import ServerContext


def main() -> int:
    print("[start_task_server] Initializing ServerContext...", flush=True)
    context = ServerContext()
    print(f"[start_task_server] Backend: {type(context.backend).__name__}", flush=True)
    print(f"[start_task_server] Skills loaded: {len(context.skills.all())}", flush=True)
    print("[start_task_server] Starting AgentHttpServer on :8787...", flush=True)
    AgentHttpServer(context).start()
    print("[start_task_server] Ready. /task at http://127.0.0.1:8787/task", flush=True)
    print("[start_task_server] /events stream at http://127.0.0.1:8787/events", flush=True)
    print("[start_task_server] Ctrl-C to stop.", flush=True)

    stop = False
    def _sig(_signum, _frame):
        nonlocal stop
        stop = True
    signal.signal(signal.SIGINT, _sig)
    signal.signal(signal.SIGTERM, _sig)
    while not stop:
        time.sleep(0.5)
    return 0


if __name__ == "__main__":
    sys.exit(main())

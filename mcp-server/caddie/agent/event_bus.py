"""Live event bus that streams tool-call activity to /task/stream subscribers."""
from __future__ import annotations

import json
import logging
import queue
import threading
import time
import urllib.error
import urllib.request
from contextlib import contextmanager
from dataclasses import asdict, dataclass, field
from typing import Any, Iterator


log = logging.getLogger(__name__)


@dataclass
class ToolEvent:
    type: str
    ts: float = field(default_factory=time.time)
    tool: str | None = None
    args: dict[str, Any] | None = None
    result_summary: str | None = None
    error: str | None = None
    task: str | None = None
    ok: bool | None = None
    payload: dict[str, Any] | None = None

    def to_dict(self) -> dict[str, Any]:
        return {k: v for k, v in asdict(self).items() if v is not None}


_TASK_DONE_TYPE = "task_finished"


class EventBus:
    """Thread-safe pub/sub. Subscribers receive a stream of events until either
    a task_finished event is seen or the subscriber unsubscribes."""

    def __init__(self) -> None:
        self._subscribers: list[queue.Queue[ToolEvent | None]] = []
        self._lock = threading.Lock()

    def publish(self, event: ToolEvent) -> None:
        with self._lock:
            subs = list(self._subscribers)
        for q in subs:
            try:
                q.put_nowait(event)
            except queue.Full:
                log.warning("Event bus subscriber queue full; dropping event %s", event.type)

    @contextmanager
    def subscription(self) -> Iterator[queue.Queue[ToolEvent | None]]:
        q: queue.Queue[ToolEvent | None] = queue.Queue(maxsize=1024)
        with self._lock:
            self._subscribers.append(q)
        try:
            yield q
        finally:
            with self._lock:
                if q in self._subscribers:
                    self._subscribers.remove(q)

    def task_started(self, task: str) -> None:
        self.publish(ToolEvent(type="task_started", task=task))

    def task_finished(self, ok: bool, payload: dict[str, Any] | None = None) -> None:
        self.publish(ToolEvent(type=_TASK_DONE_TYPE, ok=ok, payload=payload))

    def task_paused(self) -> None:
        self.publish(ToolEvent(type="task_paused"))

    def task_resumed(self) -> None:
        self.publish(ToolEvent(type="task_resumed"))

    def confirmation_required(self, description: str, tool: str) -> None:
        self.publish(ToolEvent(
            type="confirmation_required", tool=tool,
            payload={"description": description},
        ))

    def confirmation_resolved(self, approved: bool) -> None:
        self.publish(ToolEvent(type="confirmation_resolved", ok=approved))

    def verification_result(self, verified: bool, reason: str) -> None:
        self.publish(ToolEvent(
            type="verification_result", ok=verified,
            payload={"reason": reason},
        ))


EVENT_BUS = EventBus()


class RemoteEventBus:

    def __init__(self, base_url: str = "http://127.0.0.1:8787", timeout: float = 1.5) -> None:
        self._url = base_url.rstrip("/") + "/events/publish"
        self._timeout = timeout

    def publish(self, event: ToolEvent) -> None:
        body = json.dumps(event.to_dict()).encode("utf-8")
        req = urllib.request.Request(
            self._url,
            data=body,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=self._timeout):
                pass
        except (urllib.error.URLError, OSError) as exc:
            log.debug("RemoteEventBus drop %s -> %s: %s", event.type, self._url, exc)

    def task_started(self, task: str) -> None:
        self.publish(ToolEvent(type="task_started", task=task))

    def task_finished(self, ok: bool, payload: dict[str, Any] | None = None) -> None:
        self.publish(ToolEvent(type=_TASK_DONE_TYPE, ok=ok, payload=payload))

    def task_paused(self) -> None:
        self.publish(ToolEvent(type="task_paused"))

    def task_resumed(self) -> None:
        self.publish(ToolEvent(type="task_resumed"))

    def confirmation_required(self, description: str, tool: str) -> None:
        self.publish(ToolEvent(
            type="confirmation_required", tool=tool,
            payload={"description": description},
        ))

    def confirmation_resolved(self, approved: bool) -> None:
        self.publish(ToolEvent(type="confirmation_resolved", ok=approved))

    def verification_result(self, verified: bool, reason: str) -> None:
        self.publish(ToolEvent(
            type="verification_result", ok=verified,
            payload={"reason": reason},
        ))

    @contextmanager
    def subscription(self) -> Iterator[queue.Queue[ToolEvent | None]]:  # pragma: no cover
        # Workers never expose an SSE stream of their own.
        raise RuntimeError("RemoteEventBus has no local subscribers")


def _summarize_args(args: dict[str, Any]) -> dict[str, Any]:
    """Strip large or non-JSON-friendly values so events stay small."""
    out: dict[str, Any] = {}
    for k, v in args.items():
        if isinstance(v, (str, int, float, bool)) or v is None:
            if isinstance(v, str) and len(v) > 200:
                out[k] = v[:200] + "…"
            else:
                out[k] = v
        elif isinstance(v, (list, tuple)):
            out[k] = f"[{len(v)} items]"
        elif isinstance(v, dict):
            out[k] = f"{{{len(v)} keys}}"
        else:
            out[k] = type(v).__name__
    return out


@contextmanager
def publish_tool_call(
    tool: str,
    *,
    bus: EventBus | None = None,
    summarize_result: Any = None,
    **args: Any,
) -> Iterator[None]:
    """Emit `tool_call_started` before the wrapped block and
    `tool_call_finished` after — with `error` set if the block raises.

    Usage:
        with publish_tool_call("smartphone_tap", x=x, y=y):
            return backend.tap(x, y)
    """
    target = bus or EVENT_BUS
    target.publish(ToolEvent(type="tool_call_started", tool=tool, args=_summarize_args(args)))
    try:
        yield
    except Exception as exc:
        target.publish(
            ToolEvent(
                type="tool_call_finished",
                tool=tool,
                error=f"{type(exc).__name__}: {exc}",
            )
        )
        raise
    else:
        target.publish(ToolEvent(type="tool_call_finished", tool=tool))

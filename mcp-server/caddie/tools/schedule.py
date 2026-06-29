from __future__ import annotations

from datetime import datetime

from fastmcp import FastMCP

from caddie.agent.event_bus import publish_tool_call
from caddie.agent.when import parse_when
from caddie.context import ServerContext


def _create_scheduled(
    store,
    task: str,
    when: str,
    recurrence: str,
    pre_auth: str,
    now: datetime | None = None,
) -> dict:
    task_text = str(task or "").strip()
    if not task_text:
        return {"ok": False, "error": "missing task"}

    spec = str(recurrence or "").strip() or str(when or "").strip()
    try:
        next_fire, rec = parse_when(spec, now or datetime.now().astimezone())
    except ValueError as exc:
        return {"ok": False, "error": f"invalid time: {exc}"}

    pre_auth_text = str(pre_auth or "").strip() or None
    scheduled = store.add(task_text, next_fire, rec, pre_auth_text)
    return {"ok": True, "task": scheduled.to_dict()}


def register_schedule_tools(mcp: FastMCP, context: ServerContext) -> None:
    @mcp.tool()
    def smartphone_schedule_task(
        task: str,
        when: str,
        recurrence: str = "",
        pre_auth: str = "",
        why: str = "",
    ) -> dict:
        """Schedule a task to run later or repeatedly.

        Args:
            task: The user-visible task instruction to run.
            when: Time specification such as "14:00", "in 30 min", or
                "daily 08:00".
            recurrence: Optional recurring time specification. When present,
                it is parsed instead of when.
            pre_auth: Optional exact consequential action the user has already
                authorized for the scheduled run.
            why: Brief German reason shown live on the device overlay.
        """
        with publish_tool_call(
            "smartphone_schedule_task",
            bus=context.events,
            task=task,
            when=when,
            recurrence=recurrence,
            pre_auth=pre_auth,
            why=why,
        ):
            return _create_scheduled(
                context.schedule_store, task, when, recurrence, pre_auth
            )

    @mcp.tool()
    def smartphone_list_scheduled(why: str = "") -> dict:
        """List scheduled tasks.

        Args:
            why: Brief German reason shown live on the device overlay.
        """
        with publish_tool_call(
            "smartphone_list_scheduled", bus=context.events, why=why
        ):
            return {
                "ok": True,
                "tasks": [task.to_dict() for task in context.schedule_store.list()],
            }

    @mcp.tool()
    def smartphone_cancel_scheduled(task_id: str, why: str = "") -> dict:
        """Cancel a scheduled task by id.

        Args:
            task_id: Scheduled task id returned by smartphone_list_scheduled.
            why: Brief German reason shown live on the device overlay.
        """
        with publish_tool_call(
            "smartphone_cancel_scheduled",
            bus=context.events,
            task_id=task_id,
            why=why,
        ):
            cancelled = context.schedule_store.cancel(str(task_id or "").strip())
            return {"ok": cancelled, "cancelled": cancelled}

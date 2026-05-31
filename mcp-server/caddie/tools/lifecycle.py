from fastmcp import FastMCP

from caddie.agent.event_bus import publish_tool_call
from caddie.context import ServerContext


def register_lifecycle_tools(mcp: FastMCP, context: ServerContext) -> None:
    @mcp.tool()
    def smartphone_done(message: str = "") -> str:
        """Signal that the user-requested task is complete.

        Call this as the FIRST terminal tool — before smartphone_save_skill.
        It flips the device overlay to "fertig" so the user immediately knows
        the task is over. After this call, smartphone_save_skill (if
        applicable) runs as background bookkeeping, then your final text
        reply ends the turn.

        Args:
            message: Brief German confirmation shown briefly to the user
                in the top bubble (max 80 chars). Example:
                "Dunkles Design ist aktiviert.", "Pizza-Restaurants gefunden.",
                "App geöffnet." If unsure, leave empty and a generic
                "Fertig" is used.

        Returns:
            A short ack string. The MCP client can ignore the return value.
        """
        with publish_tool_call(
            "smartphone_done", bus=context.events, message=message
        ):
            payload = {"message": message} if message else None
            context.events.task_finished(ok=True, payload=payload)
            return "ok"

    @mcp.tool()
    def smartphone_failed(reason: str = "") -> str:
        """Signal that the task failed and you are giving up.
        
        Args:
            reason: Brief German user-facing explanation (max 80 chars).
                Example: "Standortfreigabe nicht möglich.",
                "Element nicht gefunden.".

        Returns:
            A short ack string.
        """
        with publish_tool_call(
            "smartphone_failed", bus=context.events, reason=reason
        ):
            payload = {"message": reason} if reason else None
            context.events.task_finished(ok=False, payload=payload)
            return "ok"

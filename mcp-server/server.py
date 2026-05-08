import argparse
import logging
from collections.abc import Sequence

from fastmcp import FastMCP

from llmsmartphone.agent import AgentHttpServer
from llmsmartphone.agent.event_bus import RemoteEventBus
from llmsmartphone.agent.prompt import build_mcp_instructions
from llmsmartphone.context import ServerContext
from llmsmartphone.tools import register_tools


logging.basicConfig(level=logging.CRITICAL)
logging.getLogger("fastmcp").setLevel(logging.CRITICAL)
logging.getLogger("mcp").setLevel(logging.CRITICAL)


_SERVER_NAMES = {
    None: "LLM Smartphone",
    "tools": "LLM Smartphone - Tools",
    "skills": "LLM Smartphone - Skills",
}


def build_server(only: str | None = None) -> FastMCP:
    context = ServerContext()
    mcp = FastMCP(
        _SERVER_NAMES[only],
        instructions=build_mcp_instructions(),
    )
    register_tools(mcp, context, only=only)
    log = logging.getLogger(__name__)
    try:
        AgentHttpServer(context).start()
        log.warning("AgentHttpServer owner: bound :8787 (only=%s)", only)
    except OSError as exc:
        context.events = RemoteEventBus()
        log.warning(
            "AgentHttpServer worker (only=%s): :8787 already owned, "
            "forwarding events via /events/publish (%s)", only, exc
        )
    return mcp


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Run the LLM Smartphone MCP server.")
    parser.add_argument(
        "--print-system-prompt",
        action="store_true",
        help="Print the LM Studio system prompt and exit without starting the MCP server.",
    )
    parser.add_argument(
        "--only",
        choices=["tools", "skills"],
        default=None,
        help=(
            "Register only a subset of MCP tools so LM Studio shows two separate "
            "groups. Run one process with --only=tools and another with "
            "--only=skills, both pointed at this server.py."
        ),
    )
    args = parser.parse_args(argv)

    if args.print_system_prompt:
        print(build_mcp_instructions())
        return 0

    build_server(only=args.only).run(show_banner=False)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

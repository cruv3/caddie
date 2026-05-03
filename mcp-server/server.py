import argparse
import logging
from collections.abc import Sequence

from fastmcp import FastMCP

from llmsmartphone.agent import AgentHttpServer
from llmsmartphone.agent.prompt import build_mcp_instructions
from llmsmartphone.context import ServerContext
from llmsmartphone.tools import register_tools


logging.basicConfig(level=logging.CRITICAL)
logging.getLogger("fastmcp").setLevel(logging.CRITICAL)
logging.getLogger("mcp").setLevel(logging.CRITICAL)


def build_server() -> FastMCP:
    context = ServerContext()
    mcp = FastMCP(
        "LLM Smartphone",
        instructions=build_mcp_instructions(),
    )
    register_tools(mcp, context)
    agent_server = AgentHttpServer(context)
    agent_server.start()
    return mcp


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Run the LLM Smartphone MCP server.")
    parser.add_argument(
        "--print-system-prompt",
        action="store_true",
        help="Print the LM Studio system prompt and exit without starting the MCP server.",
    )
    args = parser.parse_args(argv)

    if args.print_system_prompt:
        print(build_mcp_instructions())
        return 0

    build_server().run(show_banner=False)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

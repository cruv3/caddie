from fastmcp import FastMCP

from caddie.agent.event_bus import publish_tool_call
from caddie.android.settle import baseline_hash, settle_after
from caddie.context import ServerContext


def register_app_tools(mcp: FastMCP, context: ServerContext) -> None:
    @mcp.tool()
    def smartphone_list_apps(include_system: bool = False, why: str = "") -> list[str]:
        """List installed Android packages. By default only third-party apps are returned.

        Args:
            include_system: If True, also include system packages.
            why: Brief German reason shown live on the device overlay (max 80 chars).
        """
        with publish_tool_call(
            "smartphone_list_apps", bus=context.events, include_system=include_system, why=why
        ):
            return context.backend.list_apps(include_system=include_system)

    @mcp.tool()
    def smartphone_open_app(package_name: str, why: str = "") -> str:
        """Launch an Android app by package name, for example com.android.chrome.

        Args:
            package_name: Package id, e.g. "com.android.chrome".
            why: Brief German user-facing reason, max 80 chars, shown live on
                the device overlay so the user can follow your plan. Example:
                "Opening browser for the search".
        """
        with publish_tool_call(
            "smartphone_open_app", bus=context.events, package_name=package_name, why=why
        ):
            baseline = baseline_hash(context.backend)
            result = context.backend.open_app(package_name)
            settle_after(context.backend, baseline, "open_app")
            return result

    @mcp.tool()
    def smartphone_terminate_app(package_name: str, why: str = "") -> str:
        """Force-stop a running Android app by package name.

        Args:
            package_name: ...
            why: Brief German reason shown on the device overlay (max 80 chars).
        """
        with publish_tool_call(
            "smartphone_terminate_app", bus=context.events, package_name=package_name, why=why
        ):
            baseline = baseline_hash(context.backend)
            result = context.backend.terminate_app(package_name)
            settle_after(context.backend, baseline, "open_app")
            return result

    @mcp.tool()
    def smartphone_install_app(file_path: str, replace: bool = True, why: str = "") -> str:
        """Install an APK from a local file path.

        Args:
            file_path: ...
            replace: ...
            why: Brief German reason shown on the device overlay (max 80 chars).
        """
        with publish_tool_call(
            "smartphone_install_app", bus=context.events, file_path=file_path, replace=replace, why=why
        ):
            return context.backend.install_app(file_path, replace=replace)

    @mcp.tool()
    def smartphone_uninstall_app(package_name: str, keep_data: bool = False, why: str = "") -> str:
        """Uninstall an Android app by package name.

        Args:
            package_name: ...
            keep_data: ...
            why: Brief German reason shown on the device overlay (max 80 chars).
        """
        with publish_tool_call(
            "smartphone_uninstall_app",
            bus=context.events,
            package_name=package_name,
            keep_data=keep_data,
            why=why,
        ):
            return context.backend.uninstall_app(package_name, keep_data=keep_data)

    @mcp.tool()
    def smartphone_open_url(url: str, why: str = "") -> str:
        """Open a URL on the Android device using the default browser or matching app.

        Args:
            url: ...
            why: Brief German user-facing reason shown live on the device
                overlay (max 80 chars). Example: "Sucht nach Pizza bei Google".
        """
        with publish_tool_call("smartphone_open_url", bus=context.events, url=url, why=why):
            baseline = baseline_hash(context.backend)
            result = context.backend.open_url(url)
            settle_after(context.backend, baseline, "open_app")
            return result

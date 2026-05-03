from fastmcp import FastMCP

from llmsmartphone.context import ServerContext


def register_app_tools(mcp: FastMCP, context: ServerContext) -> None:
    @mcp.tool()
    def smartphone_list_apps(include_system: bool = False) -> list[str]:
        """List installed Android packages. By default only third-party apps are returned."""
        return context.backend.list_apps(include_system=include_system)

    @mcp.tool()
    def smartphone_open_app(package_name: str) -> str:
        """Launch an Android app by package name, for example com.android.chrome."""
        return context.backend.open_app(package_name)

    @mcp.tool()
    def smartphone_terminate_app(package_name: str) -> str:
        """Force-stop a running Android app by package name."""
        return context.backend.terminate_app(package_name)

    @mcp.tool()
    def smartphone_install_app(file_path: str, replace: bool = True) -> str:
        """Install an APK from a local file path."""
        return context.backend.install_app(file_path, replace=replace)

    @mcp.tool()
    def smartphone_uninstall_app(package_name: str, keep_data: bool = False) -> str:
        """Uninstall an Android app by package name."""
        return context.backend.uninstall_app(package_name, keep_data=keep_data)

    @mcp.tool()
    def smartphone_open_url(url: str) -> str:
        """Open a URL on the Android device using the default browser or matching app."""
        return context.backend.open_url(url)

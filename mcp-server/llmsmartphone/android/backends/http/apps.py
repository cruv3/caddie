from llmsmartphone.android.backends.http.input import InputCommands


class AppCommands(InputCommands):
    def list_apps(self, include_system: bool = False) -> list[str]:
        return self.request_json("GET", "/apps").get("packages", [])

    def open_app(self, package_name: str) -> str:
        result = self.request_json("POST", "/open_app", {"packageName": package_name})
        return f"Launched {package_name}: {result.get('ok', False)}"

    def terminate_app(self, package_name: str) -> str:
        return "Terminating apps is not implemented in the Android HTTP backend yet."

    def install_app(self, file_path: str, replace: bool = True) -> str:
        return "Installing apps is not implemented in the Android HTTP backend yet."

    def uninstall_app(self, package_name: str, keep_data: bool = False) -> str:
        return "Uninstalling apps is not implemented in the Android HTTP backend yet."

    def open_url(self, url: str) -> str:
        result = self.request_json("POST", "/open_url", {"url": url})
        return f"Opened URL {url}: {result.get('ok', False)}"

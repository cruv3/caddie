from caddie.android.backends.http.input import InputCommands


class AppCommands(InputCommands):
    def list_apps(self, include_system: bool = False) -> list[str]:
        raw = self.request_json("GET", "/apps").get("packages", [])
        out = []
        for a in raw:
            if isinstance(a, dict):
                out.append(f"{a.get('label', '?')} ({a.get('package', '')})")
            else:
                out.append(str(a))
        return out

    def open_app(self, package_name: str) -> str:
        result = self.request_json("POST", "/open_app", {"packageName": package_name})
        return f"Launched {package_name}: {result.get('ok', False)}"

    def terminate_app(self, package_name: str) -> str:
        return "Terminating apps is not implemented in the Android HTTP backend yet."

    def install_app(self, file_path: str, replace: bool = True) -> str:
        return "Installing apps is not implemented in the Android HTTP backend yet."

    def uninstall_app(self, package_name: str, keep_data: bool = False) -> str:
        result = self.request_json("POST", "/uninstall", {"packageName": package_name})
        return f"Opened the uninstall dialog for {package_name} (user confirms): {result.get('ok', False)}"

    def open_url(self, url: str) -> str:
        result = self.request_json("POST", "/open_url", {"url": url})
        return f"Opened URL {url}: {result.get('ok', False)}"

    def open_settings(self, action: str) -> str:
        # Deep-link via the device's VIEW handler is ADB-only; the on-device HTTP
        # bridge has no am-start endpoint, so fast-mode deep-links are unsupported
        # here (the research setup uses the ADB backend).
        return f"open_settings not supported on the HTTP backend ({action})"

    def set_setting(self, namespace: str, key: str, value: str) -> str:
        return f"set_setting not supported on the HTTP backend ({namespace}.{key})"

    def set_dark_mode(self, on: bool) -> str:
        return f"set_dark_mode not supported on the HTTP backend ({on})"

    def set_alarm(self, hour: int, minute: int, message: str = "") -> str:
        # RAISE (not return) so the clock resolver treats it as a failure and
        # falls back to the LLM/UI path instead of falsely reporting done_fast.
        raise RuntimeError("set_alarm not supported on the HTTP backend")

    def set_timer(self, seconds: int, message: str = "") -> str:
        raise RuntimeError("set_timer not supported on the HTTP backend")

from pathlib import Path

from llmsmartphone.android.backends.adb.client import AdbError
from llmsmartphone.android.backends.adb.input import InputCommands


class AppCommands(InputCommands):
    def list_apps(self, include_system: bool = True) -> list[str]:
        args = ["pm", "list", "packages"]
        if not include_system:
            args.append("-3")
        output = self.shell(*args, timeout_seconds=30)
        packages = []
        for line in output.splitlines():
            line = line.strip()
            if line.startswith("package:"):
                packages.append(line.removeprefix("package:"))
        return sorted(packages)

    def open_app(self, package_name: str) -> str:
        self.checked(
            ["shell", "monkey", "-p", package_name, "-c", "android.intent.category.LAUNCHER", "1"],
            timeout_seconds=30,
        )
        return f"Launched {package_name}"

    def terminate_app(self, package_name: str) -> str:
        self.shell("am", "force-stop", package_name)
        return f"Terminated {package_name}"

    def install_app(self, file_path: str, replace: bool = True) -> str:
        path = Path(file_path).expanduser()
        if not path.exists():
            raise AdbError(f"App file does not exist: {path}")
        args = ["install"]
        if replace:
            args.append("-r")
        args.append(str(path))
        output = self.checked(args, timeout_seconds=180)
        return output or f"Installed {path.name}"

    def uninstall_app(self, package_name: str, keep_data: bool = False) -> str:
        args = ["uninstall"]
        if keep_data:
            args.append("-k")
        args.append(package_name)
        output = self.checked(args, timeout_seconds=60)
        return output or f"Uninstalled {package_name}"

    def open_url(self, url: str) -> str:
        self.shell("am", "start", "-a", "android.intent.action.VIEW", "-d", url, timeout_seconds=30)
        return f"Opened URL: {url}"


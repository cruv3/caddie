import re
from pathlib import Path

from caddie.android.backends.adb.client import AdbError
from caddie.android.backends.adb.input import InputCommands

# monkey returns exit 0 even when the package is missing; it prints these to
# stdout instead. Launch failure must be detected from stdout, not the exit code.
_LAUNCH_FAIL_MARKERS = ("No activities found", "monkey aborted")


def _launch_failed(out: str) -> bool:
    o = out or ""
    return any(m in o for m in _LAUNCH_FAIL_MARKERS)


def _app_token(package_name: str) -> str:
    """A coarse search token from a (possibly wrong) package guess: the last
    dotted segment with any trailing version digits stripped
    (e.g. 'com.android.calculator2' -> 'calculator')."""
    seg = (package_name or "").lower().rsplit(".", 1)[-1]
    return re.sub(r"\d+$", "", seg)


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

    def _try_launch(self, pkg: str) -> tuple[bool, str]:
        """Launch via monkey; success only if stdout lacks the failure markers."""
        try:
            out = self.shell("monkey", "-p", pkg, "-c",
                             "android.intent.category.LAUNCHER", "1", timeout_seconds=30)
        except AdbError as exc:
            return False, str(exc)
        return (not _launch_failed(out)), out

    def _resolve_package(self, package_name: str) -> str:
        """Map a possibly-wrong package guess to an installed package id, fail-closed:
        exact id, else a UNIQUE token-contains match; ambiguous/none -> AdbError."""
        installed = self.list_apps(include_system=True)
        if package_name in installed:
            return package_name
        token = _app_token(package_name)
        cands = [p for p in installed if token and token in p.lower()]
        if len(cands) == 1:
            return cands[0]
        if not cands:
            raise AdbError(f"no installed package matches '{package_name}'")
        raise AdbError(
            f"ambiguous package '{package_name}'; candidates: " + ", ".join(cands[:8]))

    def open_app(self, package_name: str) -> str:
        pkg = (package_name or "").strip()
        ok, _out = self._try_launch(pkg)
        if ok:
            return f"Launched {pkg}"
        # guessed package didn't launch -> resolve against installed packages
        target = self._resolve_package(pkg)  # raises on none/ambiguous (fail-closed)
        if target != pkg:
            ok2, _ = self._try_launch(target)
            if ok2:
                return f"Launched {target} (resolved from '{pkg}')"
        raise AdbError(f"could not launch '{pkg}'")

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

    def open_settings(self, action: str) -> str:
        """Deep-link to a settings screen via an android.settings.* action. The
        caller (tool) must have validated `action` against the whitelist."""
        self.shell("am", "start", "-a", action, timeout_seconds=30)
        return f"Opened settings screen: {action}"

    def set_setting(self, namespace: str, key: str, value: str) -> str:
        """settings put — caller (tool) must validate ns/key/value via the
        fast_actions whitelist (never arbitrary secure/global security keys)."""
        self.shell("settings", "put", namespace, key, value, timeout_seconds=15)
        return f"Set {namespace}.{key} = {value}"

    def set_dark_mode(self, on: bool) -> str:
        self.shell("cmd", "uimode", "night", "yes" if on else "no", timeout_seconds=15)
        return f"Dark mode {'on' if on else 'off'}"


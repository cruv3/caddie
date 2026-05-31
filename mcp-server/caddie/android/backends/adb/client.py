import os
import subprocess
from dataclasses import dataclass


class AdbError(RuntimeError):
    pass


@dataclass
class AdbResult:
    command: list[str]
    returncode: int
    stdout: str
    stderr: str

    @property
    def ok(self) -> bool:
        return self.returncode == 0


class AdbClient:
    def __init__(self, adb_path: str | None = None, timeout_seconds: int = 20) -> None:
        self.adb_path = adb_path or os.environ.get("ANDROID_ADB") or "adb"
        self.timeout_seconds = timeout_seconds

    def run(self, args: list[str], *, timeout_seconds: int | None = None) -> AdbResult:
        command = [self.adb_path, *args]
        try:
            completed = subprocess.run(
                command,
                capture_output=True,
                text=True,
                timeout=timeout_seconds or self.timeout_seconds,
            )
        except FileNotFoundError as exc:
            raise AdbError(
                "ADB executable not found. Install Android Platform Tools or set ANDROID_ADB."
            ) from exc
        except subprocess.TimeoutExpired as exc:
            raise AdbError(f"ADB command timed out: {' '.join(command)}") from exc

        # Defensive: subprocess.run is documented to return strings when
        # text=True, but on Windows we have observed completed.stdout being
        # None in edge cases (e.g. the child closing stdout very fast).
        # Coerce to "" so downstream callers can always .strip().
        stdout = completed.stdout if isinstance(completed.stdout, str) else ""
        stderr = completed.stderr if isinstance(completed.stderr, str) else ""
        return AdbResult(
            command=command,
            returncode=completed.returncode,
            stdout=stdout.strip(),
            stderr=stderr.strip(),
        )

    def checked(self, args: list[str], *, timeout_seconds: int | None = None) -> str:
        result = self.run(args, timeout_seconds=timeout_seconds)
        if not result.ok:
            detail = result.stderr or result.stdout or "no adb error output"
            raise AdbError(f"ADB failed: {' '.join(result.command)}\n{detail}")
        return result.stdout

    def shell(self, *args: str, timeout_seconds: int | None = None) -> str:
        return self.checked(["shell", *args], timeout_seconds=timeout_seconds)


import re
from typing import Any

from caddie.android.backends.adb.client import AdbClient, AdbError


class DeviceCommands(AdbClient):
    def list_devices(self) -> list[dict[str, str]]:
        output = self.checked(["devices", "-l"])
        devices: list[dict[str, str]] = []
        for line in output.splitlines()[1:]:
            line = line.strip()
            if not line:
                continue
            parts = line.split()
            serial = parts[0]
            state = parts[1] if len(parts) > 1 else "unknown"
            meta = " ".join(parts[2:]) if len(parts) > 2 else ""
            devices.append({"serial": serial, "state": state, "meta": meta})
        return devices

    def screen_size(self) -> dict[str, Any]:
        output = self.shell("wm", "size")
        match = re.search(r"(\d+)x(\d+)", output)
        if not match:
            raise AdbError(f"Could not parse screen size from: {output}")
        return {"width": int(match.group(1)), "height": int(match.group(2)), "raw": output}

    def orientation(self) -> dict[str, Any]:
        output = self.shell("settings", "get", "system", "user_rotation")
        rotation = int(output.strip()) if output.strip().isdigit() else None
        names = {0: "portrait", 1: "landscape", 2: "reverse_portrait", 3: "reverse_landscape"}
        return {"rotation": rotation, "orientation": names.get(rotation, "unknown"), "raw": output}

    def set_orientation(self, orientation: str) -> str:
        normalized = orientation.strip().lower()
        rotations = {
            "portrait": "0",
            "landscape": "1",
            "reverse_portrait": "2",
            "reverse_landscape": "3",
        }
        if normalized not in rotations:
            raise AdbError(
                "Unsupported orientation. Use portrait, landscape, reverse_portrait, or reverse_landscape."
            )
        self.shell("settings", "put", "system", "accelerometer_rotation", "0")
        self.shell("settings", "put", "system", "user_rotation", rotations[normalized])
        return f"Set orientation to {normalized}"


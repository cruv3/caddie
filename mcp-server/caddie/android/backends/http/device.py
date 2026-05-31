from typing import Any

from caddie.android.backends.http.client import HttpClient


class DeviceCommands(HttpClient):
    def list_devices(self) -> list[dict[str, str]]:
        health = self.request_json("GET", "/health")
        return [{
            "id": self.base_url,
            "type": "android-http",
            "status": "connected" if health.get("ok") else "unavailable",
            "accessibility": str(health.get("accessibilityConnected", False)),
        }]

    def screen_size(self) -> dict[str, Any]:
        screen = self.request_json("GET", "/screen")
        max_right = 0
        max_bottom = 0
        for node in screen.get("nodes", []):
            bounds = node.get("bounds", {})
            max_right = max(max_right, int(bounds.get("right", 0)))
            max_bottom = max(max_bottom, int(bounds.get("bottom", 0)))
        return {"width": max_right, "height": max_bottom, "source": "accessibility_bounds"}

    def orientation(self) -> dict[str, Any]:
        return {"orientation": "unknown", "source": "android-http"}

    def set_orientation(self, orientation: str) -> str:
        return "Orientation changes are not implemented in the Android HTTP backend yet."

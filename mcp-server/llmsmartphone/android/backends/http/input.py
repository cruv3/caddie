from llmsmartphone.android.backends.http.device import DeviceCommands


class InputCommands(DeviceCommands):
    def press_button(self, button: str) -> str:
        normalized = button.strip().upper()
        if normalized == "BACK":
            result = self.request_json("POST", "/back", {})
            return f"BACK pressed: {result.get('ok', False)}"
        if normalized == "HOME":
            result = self.request_json("POST", "/home", {})
            return f"HOME pressed: {result.get('ok', False)}"
        return f"Button {button} is not implemented in the Android HTTP backend yet."

    def tap(self, x: int, y: int) -> str:
        result = self.request_json("POST", "/tap", {"x": x, "y": y})
        return f"Tapped ({x}, {y}): {result.get('ok', False)}"

    def double_tap(self, x: int, y: int, delay_ms: int = 120) -> str:
        first = self.request_json("POST", "/tap", {"x": x, "y": y})
        second = self.request_json("POST", "/tap", {"x": x, "y": y})
        return f"Double tapped ({x}, {y}): {first.get('ok', False) and second.get('ok', False)}"

    def long_press(self, x: int, y: int, duration_ms: int = 700) -> str:
        result = self.request_json("POST", "/long_press", {"x": x, "y": y, "durationMs": duration_ms})
        return f"Long pressed ({x}, {y}): {result.get('ok', False)}"

    def swipe(self, start_x: int, start_y: int, end_x: int, end_y: int, duration_ms: int = 300) -> str:
        result = self.request_json("POST", "/swipe", {
            "startX": start_x,
            "startY": start_y,
            "endX": end_x,
            "endY": end_y,
            "durationMs": duration_ms,
        })
        return f"Swiped: {result.get('ok', False)}"

    def type_text(self, text: str, submit: bool = False) -> str:
        result = self.request_json("POST", "/type_text", {"text": text, "submit": submit})
        return f"Typed text: {result.get('ok', False)}"

from llmsmartphone.android.backends.adb.device import DeviceCommands


class InputCommands(DeviceCommands):
    def press_button(self, button: str) -> str:
        key = button.strip().upper()
        keycodes = {
            "HOME": "KEYCODE_HOME",
            "BACK": "KEYCODE_BACK",
            "ENTER": "KEYCODE_ENTER",
            "POWER": "KEYCODE_POWER",
            "APP_SWITCH": "KEYCODE_APP_SWITCH",
            "RECENTS": "KEYCODE_APP_SWITCH",
            "VOLUME_UP": "KEYCODE_VOLUME_UP",
            "VOLUME_DOWN": "KEYCODE_VOLUME_DOWN",
            "NOTIFICATION": "KEYCODE_NOTIFICATION",
        }
        keycode = keycodes.get(key, key)
        self.shell("input", "keyevent", keycode)
        return f"Pressed {keycode}"

    def tap(self, x: int, y: int) -> str:
        self.shell("input", "tap", str(x), str(y))
        return f"Tapped at ({x}, {y})"

    def double_tap(self, x: int, y: int, delay_ms: int = 120) -> str:
        bounded_delay = max(20, min(delay_ms, 1000))
        self.tap(x, y)
        self.shell("sleep", f"{bounded_delay / 1000:.3f}")
        self.tap(x, y)
        return f"Double tapped at ({x}, {y})"

    def long_press(self, x: int, y: int, duration_ms: int = 700) -> str:
        bounded_duration = max(100, min(duration_ms, 5000))
        self.shell("input", "swipe", str(x), str(y), str(x), str(y), str(bounded_duration))
        return f"Long pressed at ({x}, {y}) for {bounded_duration} ms"

    def swipe(
        self,
        start_x: int,
        start_y: int,
        end_x: int,
        end_y: int,
        duration_ms: int = 300,
    ) -> str:
        bounded_duration = max(50, min(duration_ms, 5000))
        self.shell(
            "input",
            "swipe",
            str(start_x),
            str(start_y),
            str(end_x),
            str(end_y),
            str(bounded_duration),
        )
        return f"Swiped from ({start_x}, {start_y}) to ({end_x}, {end_y})"

    def type_text(self, text: str, submit: bool = False) -> str:
        escaped = self._escape_input_text(text)
        self.shell("input", "text", escaped)
        if submit:
            self.press_button("ENTER")
        return "Typed text" + (" and pressed ENTER" if submit else "")

    @staticmethod
    def _escape_input_text(text: str) -> str:
        # Android's `input text` uses %s for spaces. Keep the first version simple.
        return text.replace("%", r"\%").replace(" ", "%s")


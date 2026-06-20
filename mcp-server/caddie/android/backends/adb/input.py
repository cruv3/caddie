from caddie.android.backends.adb.device import DeviceCommands


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

    # ── High-level, coordinate-free navigation macros ──────────────────────
    # These remove the model's need to guess swipe coordinates / panel edges,
    # which was the main source of detours and loops.

    def scroll(self, direction: str, amount: float = 0.6) -> str:
        """Scroll the current view. ``direction`` is where you want to MOVE
        THROUGH the content: 'down' reveals content further down (finger swipes
        up), 'up' goes back up, 'left'/'right' for horizontal. ``amount`` is the
        fraction of the screen to travel (0.1-0.9)."""
        size = self.screen_size()
        w, h = size["width"], size["height"]
        cx, cy = w // 2, h // 2
        span = int(min(w, h) * max(0.1, min(amount, 0.9)) / 2)
        d = direction.strip().lower()
        moves = {
            # content moves opposite to the finger
            "down": (cx, cy + span, cx, cy - span),
            "up": (cx, cy - span, cx, cy + span),
            "left": (cx + span, cy, cx - span, cy),
            "right": (cx - span, cy, cx + span, cy),
        }
        if d not in moves:
            raise ValueError(f"Unknown scroll direction: {direction!r}")
        x1, y1, x2, y2 = moves[d]
        self.shell("input", "swipe", str(x1), str(y1), str(x2), str(y2), "300")
        return f"Scrolled {d}"

    def open_quick_settings(self) -> str:
        """Open the Quick Settings panel (Wi-Fi, Bluetooth, flashlight, …)."""
        self.shell("cmd", "statusbar", "expand-settings")
        return "Opened quick settings"

    def open_notifications(self) -> str:
        """Open the notification shade."""
        self.shell("cmd", "statusbar", "expand-notifications")
        return "Opened notifications"

    def collapse_panels(self) -> str:
        """Collapse the notification shade / quick settings panel."""
        self.shell("cmd", "statusbar", "collapse")
        return "Collapsed status bar panels"

    def open_app_drawer(self) -> str:
        """Open the all-apps drawer (KEYCODE_ALL_APPS on supported launchers)."""
        self.shell("input", "keyevent", "284")  # KEYCODE_ALL_APPS
        return "Opened app drawer"

    @staticmethod
    def _escape_input_text(text: str) -> str:
        # Android's `input text` uses %s for spaces. Keep the first version simple.
        return text.replace("%", r"\%").replace(" ", "%s")


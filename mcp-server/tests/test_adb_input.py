from caddie.android.backends.adb.input import InputCommands


class RecordingInputCommands(InputCommands):
    def __init__(self):
        super().__init__(adb_path="adb")
        self.shell_calls = []

    def shell(self, *args: str, timeout_seconds: int | None = None) -> str:
        self.shell_calls.append(args)
        return ""


def test_type_text_quotes_semicolon_for_adb_shell():
    commands = RecordingInputCommands()

    commands.type_text("Projekt: Bericht Dienstag abgeben; Entwurf Dienstag prüfen")

    assert commands.shell_calls == [
        (
            "input",
            "text",
            "'Projekt:%sBericht%sDienstag%sabgeben;%sEntwurf%sDienstag%sprüfen'",
        )
    ]


def test_type_text_escapes_single_quote_inside_quoted_adb_text():
    commands = RecordingInputCommands()

    commands.type_text("Anna's Bericht")

    assert commands.shell_calls == [
        (
            "input",
            "text",
            "'Anna'\\''s%sBericht'",
        )
    ]

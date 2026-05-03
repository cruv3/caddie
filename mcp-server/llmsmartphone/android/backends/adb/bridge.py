from llmsmartphone.android.backends.adb.screen import ScreenCommands


class AdbBridge(ScreenCommands):
    """Facade that exposes all ADB command groups through one backend object."""


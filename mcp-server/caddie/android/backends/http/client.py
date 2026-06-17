import json
import urllib.error
import urllib.request
from typing import Any


class HttpBridgeError(RuntimeError):
    pass


class HttpClient:
    def __init__(self, base_url: str = "http://127.0.0.1:8765", timeout_seconds: int = 10):
        self.base_url = base_url.rstrip("/")
        self.timeout_seconds = timeout_seconds

    def request_json(self, method: str, path: str, body: dict[str, Any] | None = None) -> Any:
        data = None
        headers = {"Accept": "application/json"}
        if body is not None:
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json; charset=utf-8"
        request = urllib.request.Request(
            f"{self.base_url}{path}",
            data=data,
            headers=headers,
            method=method,
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout_seconds) as response:
                text = response.read().decode("utf-8")
        except urllib.error.URLError as exc:
            raise HttpBridgeError(f"Android HTTP bridge unavailable at {self.base_url}: {exc}") from exc
        if not text:
            return {}
        try:
            return json.loads(text)
        except json.JSONDecodeError as exc:
            raise HttpBridgeError(f"Android HTTP bridge returned invalid JSON: {text[:200]}") from exc

    def request_png(self, path: str) -> bytes:
        request = urllib.request.Request(
            f"{self.base_url}{path}",
            headers={"Accept": "image/png"},
            method="GET",
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout_seconds) as response:
                content_type = response.headers.get("Content-Type", "")
                data = response.read()
        except urllib.error.URLError as exc:
            raise HttpBridgeError(f"Android HTTP bridge unavailable at {self.base_url}: {exc}") from exc
        if "image/png" not in content_type.lower():
            preview = data[:200].decode("utf-8", errors="replace")
            raise HttpBridgeError(f"Expected image/png from Android HTTP bridge, got {content_type}: {preview}")
        if not data.startswith(b"\x89PNG\r\n\x1a\n"):
            raise HttpBridgeError("Android HTTP bridge returned invalid PNG data")
        return data

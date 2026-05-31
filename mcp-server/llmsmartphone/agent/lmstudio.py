from __future__ import annotations

import json
import os
from dataclasses import dataclass
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen

from llmsmartphone.config import (
    DEFAULT_LM_STUDIO_CONTEXT_LENGTH,
    DEFAULT_LM_STUDIO_ENDPOINT,
    DEFAULT_LM_STUDIO_INTEGRATION,
    DEFAULT_LM_STUDIO_MODEL,
    ENV_LM_STUDIO_CONTEXT_LENGTH,
    ENV_LM_STUDIO_ENDPOINT,
    ENV_LM_STUDIO_INTEGRATION,
    ENV_LM_STUDIO_MODEL,
)


@dataclass(frozen=True)
class LmStudioSettings:
    endpoint: str
    model: str
    integration: str
    context_length: int

    @classmethod
    def from_env(cls) -> "LmStudioSettings":
        return cls(
            endpoint=os.environ.get(ENV_LM_STUDIO_ENDPOINT, DEFAULT_LM_STUDIO_ENDPOINT),
            model=os.environ.get(ENV_LM_STUDIO_MODEL, DEFAULT_LM_STUDIO_MODEL),
            integration=os.environ.get(ENV_LM_STUDIO_INTEGRATION, DEFAULT_LM_STUDIO_INTEGRATION),
            context_length=_env_int(ENV_LM_STUDIO_CONTEXT_LENGTH, DEFAULT_LM_STUDIO_CONTEXT_LENGTH),
        )


class LmStudioClient:
    def __init__(self, settings: LmStudioSettings | None = None) -> None:
        self.settings = settings or LmStudioSettings.from_env()

    def send_task(self, task: str, system_prompt: str, authorization: str | None = None,
                  model: str | None = None, context_length: int | None = None) -> dict:
        # Integration kann komma-separiert mehrere Plugins enthalten
        # (z.B. "mcp/llm-smartphone-tools,mcp/llm-smartphone-skills" für Tools + smartphone_save_skill)
        integrations = [s.strip() for s in self.settings.integration.split(",") if s.strip()]
        effective_model = model or self.settings.model
        body = {
            "model": effective_model,
            "input": task,
            "system_prompt": system_prompt,
            "integrations": integrations,
            "context_length": context_length or self.settings.context_length,
            "temperature": 0.2,
            "stream": False,
        }
        # reasoning="off" wird nur von Modellen mit Thinking-Mode akzeptiert (Qwen3.6, …)
        # Andere Modelle (qwen3-vl-8b, gemma, pixtral) liefern 400 wenn der Parameter gesetzt ist.
        if "qwen3.6" in effective_model.lower():
            body["reasoning"] = "off"
        request = Request(
            self.settings.endpoint,
            data=json.dumps(body).encode("utf-8"),
            headers=_headers(authorization),
            method="POST",
        )
        try:
            with urlopen(request, timeout=int(os.environ.get("LLM_STUDIO_TIMEOUT", "180"))) as response:
                payload = response.read().decode("utf-8")
                return _validate_chat_response(payload, response.status)
        except HTTPError as error:
            payload = error.read().decode("utf-8", errors="replace")
            return {
                "ok": False,
                "status": error.code,
                "error": payload,
                "vision_unsupported": _looks_like_vision_error(payload),
            }
        except URLError as error:
            return {"ok": False, "status": 0, "error": str(error.reason)}

    def chat_completion(
        self,
        messages: list[dict],
        tools: list[dict] | None = None,
        authorization: str | None = None,
        model: str | None = None,
    ) -> dict:
        """Ein einzelner Turn gegen LM Studios OpenAI-kompatiblen Endpoint.

        Anders als ``send_task`` faehrt LM Studio hier KEINEN Tool-Loop — es
        gibt nur eine Modell-Antwort zurueck (Text oder ``tool_calls``). Den
        Loop besitzt der Agent selbst (siehe ``agent_loop.py``). Genau das
        macht Pause/Resume und Mid-run-Korrektur ueberhaupt erst moeglich.
        """
        effective_model = model or self.settings.model
        body: dict = {
            "model": effective_model,
            "messages": messages,
            "temperature": 0.2,
            "stream": False,
        }
        if tools:
            body["tools"] = tools
        # reasoning="off" nur fuer Modelle mit Thinking-Mode (Qwen3.6, …).
        if "qwen3.6" in effective_model.lower():
            body["reasoning"] = "off"
        request = Request(
            self._chat_completions_url(),
            data=json.dumps(body).encode("utf-8"),
            headers=_headers(authorization),
            method="POST",
        )
        try:
            with urlopen(
                request, timeout=int(os.environ.get("LLM_STUDIO_TIMEOUT", "180"))
            ) as response:
                payload = response.read().decode("utf-8")
                return _validate_chat_response(payload, response.status)
        except HTTPError as error:
            payload = error.read().decode("utf-8", errors="replace")
            return {
                "ok": False,
                "status": error.code,
                "error": payload,
                "vision_unsupported": _looks_like_vision_error(payload),
            }
        except URLError as error:
            return {"ok": False, "status": 0, "error": str(error.reason)}

    def _chat_completions_url(self) -> str:
        """Leitet den OpenAI-kompatiblen Endpoint aus dem konfigurierten
        ``/api/v1/chat``-Endpoint ab (gleicher Host, anderer Pfad)."""
        parsed = urlparse(self.settings.endpoint)
        return f"{parsed.scheme}://{parsed.netloc}/v1/chat/completions"


def _looks_like_vision_error(payload: str) -> bool:
    needle = payload.lower()
    keywords = ("image", "vision", "multimodal", "image_url", "modality", "unsupported content")
    return any(k in needle for k in keywords)


def _validate_chat_response(payload: str, status: int) -> dict:
    """LM Studio sometimes returns HTTP 200 with a non-completion body when
    it has no model loaded, when the request hits a path its OpenAI-compat
    server does not understand, or when the underlying backend errors out.
    We detect that here and surface an actionable error to the agent loop
    (which propagates it via SSE to the phone overlay) instead of letting
    an empty / bogus "success" silently dead-end the run.
    """
    text = (payload or "").strip()
    if not text:
        return {
            "ok": False,
            "status": status,
            "error": "LM Studio returned an empty body (HTTP 200). "
                     "Likely cause: no model loaded — open LM Studio and load "
                     "the configured model.",
        }
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        snippet = text[:300]
        return {
            "ok": False,
            "status": status,
            "error": f"LM Studio returned non-JSON body (HTTP {status}): {snippet}",
        }
    # Real OpenAI-compatible response carries either `choices` or `error`.
    if isinstance(data, dict) and data.get("choices"):
        return {"ok": True, "status": status, "response": data}
    if isinstance(data, dict) and data.get("error"):
        err = data["error"]
        msg = err.get("message") if isinstance(err, dict) else str(err)
        return {
            "ok": False,
            "status": status,
            "error": msg or json.dumps(err),
            "vision_unsupported": _looks_like_vision_error(text),
        }
    # HTTP 200 but neither choices nor error — classic "Unexpected endpoint
    # or method. Returning 200 anyway" stub from LM Studio when no handler
    # matched the request.
    snippet = json.dumps(data)[:300] if isinstance(data, (dict, list)) else text[:300]
    return {
        "ok": False,
        "status": status,
        "error": "LM Studio responded HTTP 200 but the body is not a chat "
                 "completion (no `choices`). Likely no model loaded or the "
                 f"endpoint is unrecognized. Body: {snippet}",
    }


def _headers(authorization: str | None) -> dict[str, str]:
    headers = {
        "Content-Type": "application/json; charset=utf-8",
        "Accept": "application/json",
    }
    if authorization:
        headers["Authorization"] = authorization
    return headers


def _env_int(name: str, default: int) -> int:
    try:
        return int(os.environ.get(name, str(default)))
    except ValueError:
        return default

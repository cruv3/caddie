from __future__ import annotations

import json
import os
from dataclasses import dataclass
from urllib.error import HTTPError, URLError
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
                return {
                    "ok": True,
                    "status": response.status,
                    "response": json.loads(payload) if payload else {},
                }
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


def _looks_like_vision_error(payload: str) -> bool:
    needle = payload.lower()
    keywords = ("image", "vision", "multimodal", "image_url", "modality", "unsupported content")
    return any(k in needle for k in keywords)


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

"""
OpenAI-compatible language model client (standard library only).

Talks to any server exposing ``/v1/chat/completions``: a locally served model
(llama.cpp ``llama-server``, Ollama, LM Studio, vLLM...) or a remote API.
There is no offline or canned fallback: if no model is configured or the
server can't answer, calls raise.

Configuration comes from the environment, matching the Android app:

- ``TOGAI_LLM_BASE_URL``  e.g. ``http://127.0.0.1:8080/v1``
- ``TOGAI_LLM_MODEL``     e.g. ``qwen2.5-0.5b-instruct``
- ``TOGAI_LLM_API_KEY``   optional bearer token
"""

import json
import os
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Callable, Dict, Iterable, List, Optional


class ModelNotConfiguredError(RuntimeError):
    """No language model endpoint is configured."""


class ModelError(RuntimeError):
    """The model server failed or returned no usable output."""


@dataclass
class LanguageModel:
    """A chat model served behind an OpenAI-compatible endpoint."""

    base_url: str
    model: str
    api_key: Optional[str] = None
    timeout: float = 120.0

    @classmethod
    def from_env(cls, env: Optional[Dict[str, str]] = None) -> "LanguageModel":
        env = os.environ if env is None else env
        base_url = env.get("TOGAI_LLM_BASE_URL", "").strip()
        model = env.get("TOGAI_LLM_MODEL", "").strip()
        if not base_url or not model:
            raise ModelNotConfiguredError(
                "Set TOGAI_LLM_BASE_URL and TOGAI_LLM_MODEL to a served model "
                "(e.g. a local llama.cpp or Ollama server)"
            )
        return cls(base_url=base_url, model=model, api_key=env.get("TOGAI_LLM_API_KEY") or None)

    def _request(self, path: str, body: Optional[dict] = None) -> urllib.request.Request:
        url = self.base_url.rstrip("/") + path
        data = json.dumps(body).encode("utf-8") if body is not None else None
        req = urllib.request.Request(url, data=data, method="POST" if data is not None else "GET")
        req.add_header("Content-Type", "application/json")
        if self.api_key:
            req.add_header("Authorization", f"Bearer {self.api_key}")
        return req

    def _open(self, req: urllib.request.Request):
        # Local model servers must not be routed through an HTTP proxy
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        try:
            return opener.open(req, timeout=self.timeout)
        except urllib.error.HTTPError as e:
            detail = e.read().decode("utf-8", "replace")[:500]
            raise ModelError(f"Model server returned HTTP {e.code}: {detail}") from e
        except (urllib.error.URLError, OSError) as e:
            raise ModelError(f"Model server unreachable at {self.base_url}: {e}") from e

    def list_models(self) -> List[str]:
        """Model ids the server is serving."""
        with self._open(self._request("/models")) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        return [m.get("id") for m in data.get("data", []) if m.get("id")]

    def chat(
        self,
        messages: List[Dict[str, str]],
        temperature: float = 0.7,
        max_tokens: int = 512,
        on_token: Optional[Callable[[str], None]] = None,
        json_mode: bool = False,
    ) -> str:
        """
        Generate the assistant's reply to ``messages`` (``[{"role", "content"}, ...]``).
        With ``on_token`` the reply is streamed and each chunk passed to it.
        """
        body = {
            "model": self.model,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens,
            "stream": on_token is not None,
        }
        if json_mode:
            body["response_format"] = {"type": "json_object"}
        with self._open(self._request("/chat/completions", body)) as resp:
            if on_token is None:
                data = json.loads(resp.read().decode("utf-8"))
                if "error" in data:
                    raise ModelError(f"Model error: {data['error']}")
                choices = data.get("choices") or []
                text = (choices[0].get("message", {}).get("content") if choices else None) or ""
            else:
                text = "".join(self._stream(resp, on_token))
        if not text.strip():
            raise ModelError("Model returned an empty response")
        return text

    @staticmethod
    def _stream(resp, on_token: Callable[[str], None]) -> Iterable[str]:
        for raw in resp:
            line = raw.decode("utf-8").strip()
            if not line.startswith("data:"):
                continue
            payload = line[len("data:"):].strip()
            if payload == "[DONE]":
                break
            if not payload:
                continue
            event = json.loads(payload)
            if "error" in event:
                raise ModelError(f"Model error: {event['error']}")
            choices = event.get("choices") or []
            delta = choices[0].get("delta", {}).get("content") if choices else None
            if delta:
                on_token(delta)
                yield delta

    def ask(self, system: str, user: str, **kwargs) -> str:
        """Single-turn helper: system instruction plus one user message."""
        return self.chat([{"role": "system", "content": system}, {"role": "user", "content": user}], **kwargs)


_configured: Optional[LanguageModel] = None


def configure(model: LanguageModel) -> None:
    """Set the process-wide model."""
    global _configured
    _configured = model


def current_model() -> LanguageModel:
    """The configured model, or one from the environment; raises if neither exists."""
    global _configured
    if _configured is None:
        _configured = LanguageModel.from_env()
    return _configured

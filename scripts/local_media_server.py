"""
Local OpenAI-compatible media server: serves real speech and image models so Togai's voice
and image features (and their tests, via TOGAI_MEDIA_BASE_URL) run on-device/offline:

  POST /v1/audio/transcriptions  faster-whisper (tiny)
  POST /v1/audio/speech          Piper TTS (en_US-lessac-low), returns WAV
  POST /v1/images/generations    diffusers Stable Diffusion (segmind/tiny-sd), b64 PNG
  GET  /v1/models

Usage:
  pip install torch --index-url https://download.pytorch.org/whl/cpu
  pip install faster-whisper piper-tts diffusers transformers accelerate pillow
  # MODELS_DIR holds: whisper-tiny/ (Systran/faster-whisper-tiny),
  #   piper/en/en_US/lessac/low/en_US-lessac-low.onnx(+.json) (rhasspy/piper-voices),
  #   tiny-sd/ (segmind/tiny-sd)
  python scripts/local_media_server.py MODELS_DIR 8081
"""

import base64
import io
import json
import sys
import threading
import wave
from email.parser import BytesParser
from email.policy import default
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MODELS = sys.argv[1]
PORT = int(sys.argv[2]) if len(sys.argv) > 2 else 8081

from faster_whisper import WhisperModel  # noqa: E402
from piper import PiperVoice  # noqa: E402
import torch  # noqa: E402
from diffusers import StableDiffusionPipeline  # noqa: E402

whisper = WhisperModel(f"{MODELS}/whisper-tiny", device="cpu", compute_type="int8")
voice = PiperVoice.load(f"{MODELS}/piper/en/en_US/lessac/low/en_US-lessac-low.onnx")
sd = StableDiffusionPipeline.from_pretrained(
    f"{MODELS}/tiny-sd", torch_dtype=torch.float32, safety_checker=None
)
sd.set_progress_bar_config(disable=True)
lock = threading.Lock()


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, body, ctype="application/json"):
        data = body if isinstance(body, bytes) else json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path.rstrip("/") == "/v1/models":
            ids = ["whisper-1", "tts-1", "stable-diffusion"]
            return self._send(
                200, {"object": "list", "data": [{"id": i, "object": "model"} for i in ids]}
            )
        self._send(404, {"error": "not found"})

    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        try:
            with lock:
                if self.path == "/v1/audio/transcriptions":
                    msg = BytesParser(policy=default).parsebytes(
                        b"Content-Type: "
                        + self.headers["Content-Type"].encode()
                        + b"\r\n\r\n"
                        + body
                    )
                    audio = next(
                        p.get_payload(decode=True)
                        for p in msg.iter_parts()
                        if p.get_param("name", header="content-disposition") == "file"
                    )
                    segments, _ = whisper.transcribe(io.BytesIO(audio))
                    return self._send(200, {"text": "".join(s.text for s in segments).strip()})
                if self.path == "/v1/audio/speech":
                    req = json.loads(body)
                    buf = io.BytesIO()
                    with wave.open(buf, "wb") as wav:
                        voice.synthesize_wav(req["input"], wav)
                    return self._send(200, buf.getvalue(), "audio/wav")
                if self.path == "/v1/images/generations":
                    req = json.loads(body)
                    w, h = (int(x) for x in req.get("size", "256x256").split("x"))
                    # An optional seed makes a render reproducible (same prompt + seed -> same image)
                    seed = req.get("seed")
                    generator = None if seed is None else torch.Generator("cpu").manual_seed(int(seed))
                    image = sd(
                        req["prompt"], width=w, height=h, num_inference_steps=12, generator=generator
                    ).images[0]
                    out = io.BytesIO()
                    image.save(out, format="PNG")
                    return self._send(
                        200, {"data": [{"b64_json": base64.b64encode(out.getvalue()).decode()}]}
                    )
            self._send(404, {"error": "not found"})
        except Exception as e:  # report failures; never invent output
            self._send(500, {"error": str(e)})


ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()

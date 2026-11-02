# Kurumi × Irodori-TTS-v4.1-Anime

Kurumi uses an independently running Irodori-TTS-Server. The model and Python dependencies are **not bundled into the mod JAR**.

## Windows server

In `E:\code\voiceServe\Irodori-TTS-Server`, install the NVIDIA backend and copy `.env.example` to `.env`:

```powershell
uv sync --extra cu128
Copy-Item .env.example .env
```

Set these values in `.env`:

```ini
IRODORI_HOST=127.0.0.1
IRODORI_PORT=8088
IRODORI_HF_CHECKPOINT=phasefield-audio/Irodori-TTS-v4.1-Anime
IRODORI_ALLOW_NO_REF_VOICE=true
```

Start the server and keep the terminal running:

```powershell
uv run --no-sync python -m irodori_openai_tts --host 127.0.0.1 --port 8088
```

Verify that `http://127.0.0.1:8088/health` works before starting the mod.

## Mod configuration

Optional file: `.minecraft/config/mcaimod-voice.json` (in the active Minecraft instance):

```json
{
  "enabled": true,
  "provider": "irodori",
  "server_url": "https://kurumivoice.cpolar.top",
  "model": "irodori-tts",
  "voice": "none",
  "caption": "明るく可愛らしい日本のアニメ少女の声。親しみやすく自然な話し方。",
  "speed": 1.0,
  "local_playback": true
}
```

Open `http://127.0.0.1:8765/voice` while Minecraft is running. Press **生成并试听** to submit a text to the same speech queue used for Kurumi's dialogue. The browser receives the generated WAV via the existing audio feed and can play it. TTS startup and synthesis can take time on first request.

### Emotion styles

The Web page has a test selector for `normal`, `happy`, `fear`, `affection`, `shy`, and `sad`.
In Java-scripted model conversations, `talk("ご主人様、助けて！", "fear")` requests the fearful style without changing the public chat text. One-argument `talk("...")` remains supported. The model is instructed to choose emotions based on conversation context; the client's actual damage-triggered flee message explicitly uses `fear`. These styles send fixed Japanese `irodori.caption` descriptions, not arbitrary model-generated prompts.

To use the legacy AivisSpeech backend instead, set `"provider":"aivis"`, `"server_url":"http://127.0.0.1:10101"` and `"speaker_id":"0"`.

The mod accepts loopback HTTP endpoints and the explicitly allowed `https://kurumivoice.cpolar.top` tunnel only. The public tunnel sends Kurumi's spoken text over HTTPS to that server, and its unauthenticated endpoint may be abused by others. Prefer adding API authentication or shutting down the tunnel when not needed. For a local-only setup, set `"server_url": "http://127.0.0.1:8088"`.

"""Standalone Kurumi speech receiver. Only listens on a configured LAN interface."""
import argparse
import asyncio
import hmac
import json
import os
import queue
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import edge_tts
import pygame

QUEUE = queue.Queue(maxsize=8)
STOP = threading.Event()


def voice_for(text, config):
    japanese = any("\\u3040" <= char <= "\\u30ff" for char in text)
    return config["voice_ja"] if japanese else config["voice_zh"]


async def play(text, config):
    fd, filename = tempfile.mkstemp(suffix=".mp3", prefix="kurumi-remote-")
    os.close(fd)
    try:
        await edge_tts.Communicate(text, voice_for(text, config),
                                   rate=config["rate"], pitch=config["pitch"]).save(filename)
        pygame.mixer.music.load(filename)
        pygame.mixer.music.set_volume(float(config["volume"]))
        pygame.mixer.music.play()
        while pygame.mixer.music.get_busy():
            await asyncio.sleep(0.1)
        pygame.mixer.music.unload()
    finally:
        try:
            os.unlink(filename)
        except OSError:
            pass


def worker(config):
    try:
        pygame.mixer.init()
    except Exception as exc:
        print("Audio device unavailable:", exc, flush=True)
        return
    while not STOP.is_set():
        try:
            item = QUEUE.get(timeout=0.5)
        except queue.Empty:
            continue
        try:
            asyncio.run(play(item, config))
        except Exception as exc:
            print("TTS playback error:", exc, flush=True)
        finally:
            QUEUE.task_done()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=47834)
    parser.add_argument("--token", default=os.getenv("KURUMI_TTS_TOKEN", ""))
    parser.add_argument("--config", default="voice.json")
    args = parser.parse_args()
    if not args.token or len(args.token) < 16:
        parser.error("Set --token or KURUMI_TTS_TOKEN (at least 16 characters)")
    with open(args.config, encoding="utf-8") as f:
        config = json.load(f)
    thread = threading.Thread(target=worker, args=(config,), daemon=True)
    thread.start()
    token = args.token

    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            if self.path != "/speak":
                self.send_error(404)
                return
            auth = self.headers.get("Authorization", "")
            if not hmac.compare_digest(auth, "Bearer " + token):
                self.send_error(403)
                return
            try:
                length = int(self.headers.get("Content-Length", "0"))
                if not 0 < length <= 2048:
                    self.send_error(413)
                    return
                payload = json.loads(self.rfile.read(length))
                text = payload.get("text", "")
                if not isinstance(text, str) or not 0 < len(text.strip()) <= 200:
                    self.send_error(400)
                    return
                QUEUE.put_nowait(text.strip())
            except queue.Full:
                self.send_error(429, "Voice queue is full")
                return
            except (ValueError, TypeError, UnicodeDecodeError):
                self.send_error(400)
                return
            self.send_response(202)
            self.send_header("Content-Length", "0")
            self.end_headers()

        def log_message(self, format, *arguments):
            print("%s %s" % (self.address_string(), format % arguments), flush=True)

    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"Kurumi remote TTS listening on {args.host}:{args.port}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        STOP.set()
        server.server_close()


if __name__ == "__main__":
    main()

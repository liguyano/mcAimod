"""Generate and play Edge TTS locally. Requires: pip install edge-tts pygame."""
import asyncio
import os
import sys
import tempfile

import edge_tts
import pygame


async def main():
    if len(sys.argv) != 6:
        raise SystemExit("Usage: kurumi_tts.py TEXT VOICE RATE PITCH VOLUME")
    _, text, voice, rate, pitch, volume = sys.argv
    fd, path = tempfile.mkstemp(prefix="kurumi-tts-", suffix=".mp3")
    os.close(fd)
    try:
        await edge_tts.Communicate(text, voice, rate=rate, pitch=pitch).save(path)
        pygame.mixer.init()
        pygame.mixer.music.set_volume(max(0.0, min(1.0, float(volume))))
        pygame.mixer.music.load(path)
        pygame.mixer.music.play()
        while pygame.mixer.music.get_busy():
            await asyncio.sleep(0.1)
        pygame.mixer.music.unload()
        pygame.mixer.quit()
    finally:
        try:
            os.unlink(path)
        except OSError:
            pass


if __name__ == "__main__":
    asyncio.run(main())

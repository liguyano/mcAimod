"""Generate Kurumi Edge TTS MP3, optionally play locally. Requires edge-tts and pygame for local audio."""
import asyncio
import os
import sys
import tempfile

import edge_tts


async def main():
    if len(sys.argv) != 8:
        raise SystemExit("Usage: kurumi_tts.py TEXT VOICE RATE PITCH VOLUME OUTPUT_MP3 PLAY_LOCAL")
    _, text, voice, rate, pitch, volume, output, local = sys.argv
    await edge_tts.Communicate(text, voice, rate=rate, pitch=pitch).save(output)
    if local == "1":
        import pygame
        pygame.mixer.init()
        try:
            pygame.mixer.music.set_volume(max(0.0, min(1.0, float(volume))))
            pygame.mixer.music.load(output)
            pygame.mixer.music.play()
            while pygame.mixer.music.get_busy():
                await asyncio.sleep(0.1)
            pygame.mixer.music.unload()
        finally:
            pygame.mixer.quit()


if __name__ == "__main__":
    asyncio.run(main())

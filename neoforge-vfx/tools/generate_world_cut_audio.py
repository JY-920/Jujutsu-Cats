"""Original sword-draw Foley synthesis: moving noisy metal friction, not ringing sine tones.

Run from the repository root with Python 3 and ffmpeg on PATH.
"""
from pathlib import Path
import math
import random
import struct
import subprocess
import wave

ROOT = Path(__file__).resolve().parents[2]
RATE = 44100
DURATION = .74


def friction_band(source, start, finish, q):
    """A broad, accelerating band-pass resonance driven exclusively by noise."""
    result = []
    x1 = x2 = y1 = y2 = 0.0
    for i, x in enumerate(source):
        t = i / RATE
        sweep = min(1.0, t / .23)
        frequency = start + (finish - start) * sweep ** .65
        omega = 2 * math.pi * frequency / RATE
        alpha = math.sin(omega) / (2 * q)
        a0 = 1 + alpha
        y = (alpha * (x - x2) + 2 * math.cos(omega) * y1 - (1 - alpha) * y2) / a0
        result.append(y)
        x2, x1, y2, y1 = x1, x, y1, y
    return result


def main():
    rng = random.Random(1028)
    source = [rng.uniform(-1, 1) for _ in range(int(RATE * DURATION))]
    steel = friction_band(source, 1250, 6900, 1.8)
    scrape = friction_band(source, 650, 3600, 2.5)
    samples = []
    low = 0.0
    for i, noise in enumerate(source):
        t = i / RATE
        low += .09 * (noise - low)
        # Brief sheath drag -> fast bright metallic extraction -> short rushing air tail.
        drag = (1 - math.exp(-t / .012)) * math.exp(-((t - .105) / .095) ** 2)
        release = math.exp(-((t - .19) / .070) ** 2)
        air = (1 - math.exp(-t / .022)) * math.exp(-t / .16)
        hiss = math.exp(-max(0, t - .22) / .065) * min(1, t / .14)
        grit = .72 + .28 * rng.random()
        value = (steel[i] * (drag * .95 + release * 1.15)
                 + scrape[i] * drag * .62 + (noise - low) * hiss * .10
                 + low * air * .92) * grit
        value *= min(1, (DURATION - t) / .04)
        samples.append(value)
    peak = max(map(abs, samples))
    pcm = [int(32767 * math.tanh(value / peak * 1.4) * .95) for value in samples]
    output = ROOT / 'exports/java-0.102.8-world-cut'
    output.mkdir(parents=True, exist_ok=True)
    wav = output / 'world_cut_draw.wav'
    with wave.open(str(wav), 'wb') as stream:
        stream.setnchannels(1)
        stream.setsampwidth(2)
        stream.setframerate(RATE)
        stream.writeframes(struct.pack('<' + 'h' * len(pcm), *pcm))
    for module in ['neoforge-vfx', 'forge-vfx']:
        destination = ROOT / module / 'src/main/resources/assets/jujutsuvfx/sounds/world_cut_draw.ogg'
        subprocess.run(['ffmpeg', '-y', '-hide_banner', '-loglevel', 'error', '-i', str(wav),
                        '-c:a', 'libvorbis', '-q:a', '7', str(destination)], check=True)
    print('Generated 0.74 s original noisy metal draw / air release; no tonal oscillator.')


if __name__ == '__main__':
    main()

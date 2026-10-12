"""Synthesize the original, continuous air-friction loop for Unlimited Void.

Periodic FFT noise and periodic modulation join without an intentional silence at the
loop boundary. The game owns the cast's fade-in/out. No recordings or soundtrack samples.
Requires numpy and soundfile; only the resulting Ogg ships in the mod.
"""
from pathlib import Path
import hashlib
import numpy as np
import soundfile as sf

RATE = 44100
SECONDS = 6
COUNT = RATE * SECONDS
OUTPUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/jujutsuvfx/sounds"
rng = np.random.default_rng(140001)
phase = 2 * np.pi * np.arange(COUNT) / COUNT


def band(low, high):
    frequency = np.fft.rfftfreq(COUNT, 1 / RATE)
    spectrum = np.fft.rfft(rng.standard_normal(COUNT))
    spectrum *= (1 - np.exp(-(frequency / low) ** 2)) * np.exp(-(frequency / high) ** 2)
    spectrum[0] = spectrum[-1] = 0
    signal = np.fft.irfft(spectrum, COUNT)
    return signal / max(np.std(signal), 1e-6)


# Broad rushing air carries a drier, faster friction layer and a restrained upper hiss.
# The noise and each envelope wrap around the same six-second period; none fade to zero.
pressure = band(95, 1650)
friction = band(950, 6300)
fine = band(4000, 10200)
gust = .86 + .10 * np.sin(phase + .6) + .04 * np.sin(phase * 3 + 1.1)
wakes = (.5 + .5 * np.sin(phase * 13 + .75 * np.sin(phase * 2))) ** 3
flutter = .93 + .045 * np.sin(phase * 37) + .025 * np.sin(phase * 53 + .8)
signal = pressure * .48 * gust + friction * (.29 + .10 * wakes) * flutter + fine * .075
signal -= signal.mean()
signal = np.tanh(signal * .72)
signal *= .72 / max(np.max(np.abs(signal)), 1e-6)

OUTPUT.mkdir(parents=True, exist_ok=True)
path = OUTPUT / "void_air_friction.ogg"
sf.write(path, signal.astype(np.float32), RATE, format="OGG", subtype="VORBIS")
print(f"{path.name}: {SECONDS}s, mono {RATE}Hz, sha256={hashlib.sha256(path.read_bytes()).hexdigest()}")

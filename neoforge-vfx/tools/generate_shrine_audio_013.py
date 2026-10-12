"""Synthesize only the 0.13 additions; preserve the existing four sweeps and three impacts.

Original procedural audio, with no recordings or sampled soundtrack. Requires numpy + soundfile.
"""
from pathlib import Path
import hashlib
import numpy as np
import soundfile as sf

RATE = 44100
OUTPUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/jujutsuvfx/sounds"


def band(rng, count, low, high):
    frequency = np.fft.rfftfreq(count, 1 / RATE)
    spectrum = np.fft.rfft(rng.standard_normal(count))
    spectrum *= (1 - np.exp(-(frequency / low) ** 2)) * np.exp(-(frequency / high) ** 2)
    signal = np.fft.irfft(spectrum, count)
    return signal / max(np.std(signal), 1e-6)


def write(name, signal):
    OUTPUT.mkdir(parents=True, exist_ok=True)
    signal -= signal.mean()
    signal = np.tanh(signal * .94)
    signal *= np.minimum(np.arange(len(signal)) / (RATE * .001), 1)
    signal *= np.minimum(np.arange(len(signal))[::-1] / (RATE * .020), 1)
    signal *= .73 / max(np.max(np.abs(signal)), 1e-6)
    path = OUTPUT / f"{name}.ogg"
    sf.write(path, signal.astype(np.float32), RATE, format="OGG", subtype="VORBIS")
    print(f"{name}: {len(signal) / RATE:.3f}s, mono {RATE}Hz, sha256={hashlib.sha256(path.read_bytes()).hexdigest()}")


# A double-edged whip: a high steel zip followed quickly by a lower tear and pressure snap.
rng = np.random.default_rng(130501)
t = np.arange(int(RATE * .182)) / RATE
u = np.maximum(t - .005, 0)
gate = (t >= .005) * (1 - np.exp(-u / .0007))
frequency = 940 + 5800 * np.exp(-u / .0085)
phase = 2 * np.pi * np.cumsum(frequency) / RATE
steel = (np.sin(phase) + .22 * np.sin(phase * 1.91)) * gate * np.exp(-u / .018)
v = np.maximum(t - .027, 0)
second_gate = (t >= .027) * (1 - np.exp(-v / .0009))
second_phase = 2 * np.pi * (460 * v + 3300 * .007 * (1 - np.exp(-v / .007)))
second = np.sin(second_phase) * second_gate * np.exp(-v / .023)
crack = band(rng, len(t), 2100, 10000) * gate * np.exp(-u / .0055)
tear = band(rng, len(t), 550, 5100) * second_gate * np.exp(-v / .013)
pressure = band(rng, len(t), 75, 1050) * gate * np.exp(-u / .029)
write("shrine_slash_5", steel * .26 + second * .21 + crack * .44 + tear * .22 + pressure * .39)

# The domain seals shut: a short reverse intake, a low metal closure and a decaying resonant tail.
rng = np.random.default_rng(130901)
t = np.arange(int(RATE * .78)) / RATE
u = np.maximum(t - .072, 0)
gate = (t >= .072) * (1 - np.exp(-u / .0011))
intake = band(rng, len(t), 180, 2800) * np.exp(-((t - .057) / .020) ** 2)
crack = band(rng, len(t), 1400, 8200) * gate * np.exp(-u / .0067)
body = band(rng, len(t), 140, 1600) * gate * np.exp(-u / .035)
bass_phase = 2 * np.pi * (39 * u + 74 * .038 * (1 - np.exp(-u / .038)))
bass = np.sin(bass_phase) * gate * np.exp(-u / .175)
ring = np.zeros_like(t)
for frequency, weight, decay in [(188, .20, .22), (433, .13, .14), (719, .07, .095), (1217, .03, .048)]:
    ring += np.sin(2 * np.pi * frequency * u) * weight * gate * np.exp(-u / decay)
echo = np.zeros_like(t)
for delay, weight in [(.132, .045), (.243, .026)]:
    e = np.maximum(u - delay, 0)
    echo += np.sin(2 * np.pi * 188 * e) * (u >= delay) * (1 - np.exp(-e / .006)) * np.exp(-e / .10) * weight
write("shrine_finish", intake * .15 + crack * .29 + body * .32 + bass * .50 + ring + echo)

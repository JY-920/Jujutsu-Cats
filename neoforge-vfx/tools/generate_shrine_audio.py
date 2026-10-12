"""Generate the 0.11 original slash sound set. No samples or external recordings.

Requires numpy + soundfile only when regenerating assets; Minecraft uses the packaged mono Ogg files.
"""
from pathlib import Path
import hashlib
import numpy as np
import soundfile as sf

RATE = 44100
ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/assets/jujutsuvfx/sounds"
OUTPUT.mkdir(parents=True, exist_ok=True)
written = []


def band(rng, count, low, high):
    freq = np.fft.rfftfreq(count, 1 / RATE)
    spectrum = np.fft.rfft(rng.standard_normal(count))
    spectrum *= (1 - np.exp(-(freq / low) ** 2)) * np.exp(-(freq / high) ** 2)
    signal = np.fft.irfft(spectrum, count)
    return signal / max(np.std(signal), 1e-6)


def write(name, signal):
    path = OUTPUT / f"{name}.ogg"
    old = hashlib.sha256(path.read_bytes()).hexdigest() if path.exists() else None
    signal -= signal.mean()
    signal = np.tanh(signal * .94)
    signal *= np.minimum(np.arange(len(signal)) / (RATE * .0009), 1)
    signal *= np.minimum(np.arange(len(signal))[::-1] / (RATE * .012), 1)
    signal *= .73 / max(np.max(np.abs(signal)), 1e-6)
    sf.write(path, signal.astype(np.float32), RATE, format="OGG", subtype="VORBIS")
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    written.append((name, len(signal) / RATE, old != digest))


for index in range(4):
    rng = np.random.default_rng(471103 + index * 71)
    t = np.arange(int(RATE * (.142 + index * .006))) / RATE
    # A very brief intake, a swept steel edge, and a low pressure snap. No long hiss tail.
    air = band(rng, len(t), 900, 7600)
    bite = band(rng, len(t), 2300, 11000)
    pressure = band(rng, len(t), 90, 1100)
    edge_time = .010 + index * .001
    u = np.maximum(t - edge_time, 0)
    gate = (t >= edge_time) * (1 - np.exp(-u / .0008))
    intake = air * np.exp(-((t - .008) / .0045) ** 2) * .20
    zip_env = gate * np.exp(-u / .016)
    freq = 780 + index * 43 + (4500 + index * 170) * np.exp(-u / .0065)
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    steel = (np.sin(phase) + .28 * np.sin(phase * 1.47) + .12 * np.sin(phase * 2.71)) * zip_env
    crack = bite * gate * np.exp(-u / .0045)
    body = pressure * gate * np.exp(-u / .028)
    glint = np.sin(2 * np.pi * (2350 + index * 113) * u) * gate * np.exp(-u / .023)
    write(f"shrine_slash_{index + 1}", intake + steel * .23 + crack * .50 + body * .43 + glint * .055)

for index in range(3):
    rng = np.random.default_rng(881701 + index * 43)
    t = np.arange(int(RATE * (.195 + index * .007))) / RATE
    front = (1 - np.exp(-t / .0006))
    crack = band(rng, len(t), 1400, 11500) * np.exp(-t / .006)
    grit = band(rng, len(t), 500, 5800) * np.exp(-t / .021)
    pressure = band(rng, len(t), 65, 1000) * np.exp(-t / .035)
    bass = np.sin(2 * np.pi * (58 * t + (116 + index * 9) * .012 * (1 - np.exp(-t / .012))))
    steel = (np.sin(2 * np.pi * (1240 + index * 97) * t) + .30 * np.sin(2 * np.pi * (2980 + index * 181) * t))
    steel *= np.exp(-t / .018)
    write(f"shrine_impact_{index + 1}", front * (crack * .68 + grit * .26 + pressure * .37 + bass * np.exp(-t / .052) * .42 + steel * .085))

# The ending is now a restrained closing breath, not a final damage/explosion cue.
rng = np.random.default_rng(201177)
t = np.arange(int(RATE * .42)) / RATE
flow = band(rng, len(t), 110, 2600) * (1 - np.exp(-t / .009)) * np.exp(-t / .075)
phase = 2 * np.pi * (48 * t + 220 * .024 * (1 - np.exp(-t / .024)))
seal = np.sin(phase) * (1 - np.exp(-t / .003)) * np.exp(-t / .068)
write("shrine_finish", flow * .40 + seal * .24)
for name, duration, changed in written:
    print(f"{name}: {duration:.3f}s, mono {RATE}Hz, changed={changed}")

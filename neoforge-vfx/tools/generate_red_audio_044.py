"""Original pressure-sheet and cannon audio for Red 0.44.

Only writes red_air, red_cannon and red_roar. No video soundtrack or samples.
Periodic FFT noise and integer-period modulation keep the two loops continuous;
the client supplies their volume envelopes. Requires numpy and soundfile.
"""
from pathlib import Path
import hashlib
import numpy as np
import soundfile as sf

RATE = 44100
OUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/jujutsuvfx/sounds"
rng = np.random.default_rng(440091)


def band(count, low, high):
    hz = np.fft.rfftfreq(count, 1 / RATE)
    spectrum = np.fft.rfft(rng.standard_normal(count))
    spectrum *= (1 - np.exp(-(hz / low) ** 2)) * np.exp(-(hz / high) ** 2)
    spectrum[0] = spectrum[-1] = 0
    signal = np.fft.irfft(spectrum, count)
    return signal / max(np.std(signal), 1e-6)


def write(name, signal, loop=False):
    signal = np.tanh((signal - signal.mean()) * .82)
    if not loop:
        samples = np.arange(len(signal))
        signal *= np.minimum(samples / (RATE * .001), 1)
        signal *= np.minimum(samples[::-1] / (RATE * .03), 1)
    signal *= (.60 if loop else .74) / max(np.max(np.abs(signal)), 1e-6)
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / f"{name}.ogg"
    sf.write(path, signal.astype(np.float32), RATE, format="OGG", subtype="VORBIS")
    info = sf.info(path)
    print(f"{name}: {info.duration:.2f}s, mono {info.samplerate}Hz, "
          f"sha256={hashlib.sha256(path.read_bytes()).hexdigest()}")


# Six wide rotating pressure sheets: airy grit, turbulent throbs and a low core.
# The progression in intensity comes from the weapon state, not a rising tone.
p = 2 * np.pi * np.arange(RATE * 6) / (RATE * 6)
sheets = (.5 + .5 * np.sin(9 * p + .7 * np.sin(2 * p))) ** 1.7
shreds = (.5 + .5 * np.sin(89 * p + 2.1 * np.sin(7 * p))) ** 3
body = band(len(p), 90, 1250) * (.17 + .085 * sheets)
white = band(len(p), 1100, 7100) * (.15 + .09 * sheets + .045 * shreds)
folds = band(len(p), 360, 2900) * (.045 + .07 * shreds)
core = np.sin(438 * p + .55 * np.sin(2 * p)) * .027
write("red_air", body + white + folds + core, loop=True)

# Discharge: fast fracture, heavy pressure jab, then an outward blast of air.
t = np.arange(round(RATE * .85)) / RATE
gate = 1 - np.exp(-t / .0008)
crack = band(len(t), 1600, 11700) * np.exp(-t / .013) * .68
punch_phase = 2 * np.pi * (42 * t + 160 * .025 * (1 - np.exp(-t / .025)))
punch = np.sin(punch_phase) * np.exp(-t / .19) * .65
front = band(len(t), 65, 1600) * np.exp(-t / .20) * .43
outflow = band(len(t), 850, 7800) * (1 - np.exp(-t / .018)) * np.exp(-t / .19) * .52
write("red_cannon", (crack + punch + front + outflow) * gate)

# Sustained light-cannon pressure, without flame crackle or tonal pitch ramps.
p = 2 * np.pi * np.arange(RATE * 3) / (RATE * 3)
buffet = .86 + .09 * np.sin(13 * p) + .05 * np.sin(37 * p + .8 * np.sin(2 * p))
grain = (.5 + .5 * np.sin(121 * p + 1.8 * np.sin(5 * p))) ** 2
pressure = band(len(p), 48, 820) * .34 * buffet
air = band(len(p), 560, 7700) * (.23 + .06 * grain)
bass = (np.sin(159 * p + .3 * np.sin(p)) + .32 * np.sin(318 * p)) * .074
write("red_roar", pressure + air + bass, loop=True)

"""Original Blue attraction and Red repulsion sound design, synthesized from scratch.

No samples, recordings, or music. The six-second Blue loop uses periodic FFT noise,
integer-period modulation, and phase-closed resonances; fades belong to the game.
Requires numpy and soundfile. Only writes these six Limitless assets.
"""
from pathlib import Path
import hashlib
import numpy as np
import soundfile as sf

RATE = 44100
OUTPUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/jujutsuvfx/sounds"
rng = np.random.default_rng(160001)


def timeline(seconds):
    return np.arange(round(RATE * seconds)) / RATE


def band(count, low, high):
    frequency = np.fft.rfftfreq(count, 1 / RATE)
    spectrum = np.fft.rfft(rng.standard_normal(count))
    spectrum *= (1 - np.exp(-(frequency / low) ** 2)) * np.exp(-(frequency / high) ** 2)
    spectrum[0] = spectrum[-1] = 0
    wave = np.fft.irfft(spectrum, count)
    return wave / max(np.std(wave), 1e-6)


def resonance(count, centers, width):
    frequency = np.fft.rfftfreq(count, 1 / RATE)
    spectrum = np.fft.rfft(rng.standard_normal(count))
    envelope = sum(np.exp(-.5 * ((frequency - center) / width) ** 2) for center in centers)
    spectrum *= envelope
    spectrum[0] = spectrum[-1] = 0
    wave = np.fft.irfft(spectrum, count)
    return wave / max(np.std(wave), 1e-6)


def write(name, signal, loop=False):
    signal = signal - signal.mean()
    signal = np.tanh(signal * .86)
    if not loop:
        position = np.arange(len(signal))
        signal *= np.minimum(position / (RATE * .002), 1)
        signal *= np.minimum(position[::-1] / (RATE * .025), 1)
    signal *= (.62 if loop else .76) / max(np.max(np.abs(signal)), 1e-6)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    path = OUTPUT / f"{name}.ogg"
    sf.write(path, signal.astype(np.float32), RATE, format="OGG", subtype="VORBIS")
    info = sf.info(path)
    print(f"{path.name}: {info.duration:.3f}s, {info.channels}ch {info.samplerate}Hz, "
          f"sha256={hashlib.sha256(path.read_bytes()).hexdigest()}")


# Blue forms by drawing air into a cool, tightening resonant focus.
t = timeline(.65)
u = np.clip(t / .48, 0, 1)
intake = (1 - np.exp(-t / .050)) * np.exp(-np.maximum(t - .43, 0) / .065)
phase = 2 * np.pi * (185 * t + 360 * t * t)
fold = (.72 + .28 * np.sin(2 * np.pi * (12 * t + 21 * t * t)))
draw = band(len(t), 160, 3200) * (.16 + .40 * u ** 1.7)
silk = band(len(t), 1700, 6700) * .12 * fold
focus = np.sin(phase + 1.2 * np.sin(phase * .49)) * .15 * u
write("blue_form", (draw + silk + focus) * intake)

# Continuous inward suction: rounded throbbing air and two diffuse, cool resonances.
# Every layer is periodic over exactly six seconds; no fade-to-silence loop seam.
t = timeline(6)
p = 2 * np.pi * np.arange(len(t)) / len(t)
vortex = .80 + .12 * np.sin(p * 3 + .7 * np.sin(p)) + .08 * np.sin(p * 11 + .4)
folds = (.5 + .5 * np.sin(p * 37 + 1.4 * np.sin(p * 2))) ** 2
pressure = band(len(t), 125, 1400) * .32 * vortex
stream = band(len(t), 950, 5400) * (.12 + .065 * folds)
cool = resonance(len(t), (318, 459, 917, 1247), 21) * (.10 + .025 * np.sin(p * 5))
core = np.sin(p * 1158 + 1.5 * np.sin(p * 2)) * .065
write("blue_loop", pressure + stream + cool + core, loop=True)

# The remaining flow folds inward once and seals, leaving a short hollow resonance.
t = timeline(.65)
inward = np.exp(-((t - .20) / .105) ** 2)
tail_time = np.maximum(t - .23, 0)
gate = (t >= .23) * (1 - np.exp(-tail_time / .002))
seal_phase = 2 * np.pi * (116 * tail_time + 320 * .045 * (1 - np.exp(-tail_time / .045)))
draw = band(len(t), 230, 5200) * inward * .42
seal = np.sin(seal_phase) * gate * np.exp(-tail_time / .085) * .30
glint = resonance(len(t), (714, 1031), 36) * gate * np.exp(-tail_time / .065) * .18
write("blue_close", draw + seal + glint)

# Red compresses into a rising pressure point. A dry, focused whine replaces flame.
t = timeline(1.0)
u = np.clip(t / .90, 0, 1)
compression = (1 - np.exp(-t / .065)) * (.14 + .86 * u ** 1.9)
phase = 2 * np.pi * (73 * t + 108 * t * t + 39 * t ** 3)
pressure = band(len(t), 90, 1700) * .27
strain = np.sin(phase + .6 * np.sin(phase * 2)) * .22
grain = band(len(t), 1300, 5900) * (.07 + .18 * u ** 2)
flutter = .84 + .16 * np.sin(2 * np.pi * (19 * t + 42 * t * t))
write("red_charge", compression * (pressure + strain + grain * flutter))

# Release: a hard, short rupture and a pitched-down pressure jab, without fire crackle.
t = timeline(.25)
gate = 1 - np.exp(-t / .0008)
fracture = band(len(t), 1700, 11800) * np.exp(-t / .011) * .46
air = band(len(t), 150, 6100) * np.exp(-t / .048) * .45
phase = 2 * np.pi * (68 * t + 245 * .012 * (1 - np.exp(-t / .012)))
jab = np.sin(phase) * np.exp(-t / .057) * .43
write("red_release", gate * (fracture + air + jab))

# Impact: one heavy pressure front, followed by the expanding air shell and a tight tail.
# No crackling embers, combustion roar, or musical pad are used.
t = timeline(1.6)
gate = 1 - np.exp(-t / .0012)
phase = 2 * np.pi * (39 * t + 100 * .037 * (1 - np.exp(-t / .037)))
front = np.sin(phase) * np.exp(-t / .20) * .62
snap = band(len(t), 1600, 10200) * np.exp(-t / .017) * .44
pressure = band(len(t), 70, 1500) * np.exp(-t / .23) * .34
shell = band(len(t), 400, 5400) * (1 - np.exp(-t / .032)) * np.exp(-t / .31) * .40
wake = resonance(len(t), (121, 181, 279), 28) * (1 - np.exp(-t / .08)) * np.exp(-t / .28) * .15
write("red_impact", gate * (front + snap + pressure + shell + wake))

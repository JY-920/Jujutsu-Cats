"""Original synthetic charge, arrow release and furnace eruption. No sampled recordings.

Requires numpy and soundfile. Only writes the three furnace assets.
"""
from pathlib import Path
import hashlib
import numpy as np
import soundfile as sf

RATE = 44100
OUTPUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/jujutsuvfx/sounds"
rng = np.random.default_rng(150001)


def band(count, low, high):
    f = np.fft.rfftfreq(count, 1 / RATE)
    spectrum = np.fft.rfft(rng.standard_normal(count))
    spectrum *= (1 - np.exp(-(f / low) ** 2)) * np.exp(-(f / high) ** 2)
    wave = np.fft.irfft(spectrum, count)
    return wave / max(np.std(wave), 1e-6)


def write(name, signal):
    signal -= signal.mean()
    signal = np.tanh(signal * .88)
    signal *= np.minimum(np.arange(len(signal)) / (RATE * .003), 1)
    signal *= np.minimum(np.arange(len(signal))[::-1] / (RATE * .025), 1)
    signal *= .76 / max(np.max(np.abs(signal)), 1e-6)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    path = OUTPUT / f"{name}.ogg"
    sf.write(path, signal.astype(np.float32), RATE, format="OGG", subtype="VORBIS")
    print(f"{name}: {len(signal)/RATE:.3f}s, mono {RATE}Hz, sha256={hashlib.sha256(path.read_bytes()).hexdigest()}")


# Fire is drawn into the hands: a rising, fluttering intake with a warm low resonance.
t = np.arange(int(RATE * 1.66)) / RATE
u = np.minimum(t / 1.60, 1)
envelope = (.12 + .88 * u ** 1.35) * np.minimum(t / .14, 1)
flutter = .80 + .12 * np.sin(t * 39 + np.sin(t * 11)) + .08 * np.sin(t * 83)
phase = 2 * np.pi * np.cumsum(66 + 165 * u * u) / RATE
body = band(len(t), 65, 1150) * .46 + band(len(t), 850, 5100) * .22 * flutter
hum = (np.sin(phase) + .25 * np.sin(phase * 2.03)) * .11
write("furnace_charge", (body + hum) * envelope)

# A compressed snap opens into a very fast low-to-high air tear, then a short flame wake.
t = np.arange(int(RATE * .31)) / RATE
gate = 1 - np.exp(-t / .0015)
pressure = np.sin(2*np.pi*(52*t + 130*.018*(1-np.exp(-t/.018)))) * np.exp(-t/.070)
steel = np.sin(2*np.pi*(900*t + 2600*.012*(1-np.exp(-t/.012)))) * np.exp(-t/.023)
snap = band(len(t), 1400, 10000) * np.exp(-t/.013)
wake = band(len(t), 180, 4900) * np.exp(-t/.065)
write("furnace_release", gate * (.45*pressure + .16*steel + .38*snap + .42*wake))

# A deep pressure front, rolling combustion and scattered afterburning crackles.
t = np.arange(int(RATE * 3.4)) / RATE
gate = 1 - np.exp(-t/.002)
phase = 2*np.pi*(29*t + 82*.055*(1-np.exp(-t/.055)))
boom = np.sin(phase) * np.exp(-t/.42)
front = band(len(t), 65, 1300) * np.exp(-t/.15)
crack = band(len(t), 1300, 8500) * np.exp(-t/.020)
roll = band(len(t), 55, 1300) * np.exp(-t/.90) * (1-np.exp(-t/.020))
roar = band(len(t), 400, 5600) * np.exp(-t/.72) * (1-np.exp(-t/.035))
embers = np.zeros_like(t)
for center in rng.uniform(.20, 2.5, 28):
    width = rng.uniform(.0015, .006)
    embers += np.exp(-((t-center)/width)**2) * np.exp(-center/.95) * rng.uniform(.08,.23)
crackle = band(len(t), 1500, 8000) * embers
write("furnace_burst", gate * (.62*boom + .42*front + .33*crack + .40*roll + .25*roar + crackle))

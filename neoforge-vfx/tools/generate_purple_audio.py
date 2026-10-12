"""Original pressure, ionized air and electrical sound layers for Hollow Purple; no samples."""
from pathlib import Path
import numpy as np
import soundfile as sf

RATE = 44100
OUTPUT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/jujutsuvfx/sounds'
rng = np.random.default_rng(270009)


def time(seconds):
    return np.arange(round(RATE * seconds)) / RATE


def noise(count, low, high):
    f = np.fft.rfftfreq(count, 1 / RATE)
    spectrum = np.fft.rfft(rng.standard_normal(count))
    spectrum *= (1 - np.exp(-(f / low) ** 2)) * np.exp(-(f / high) ** 2)
    spectrum[0] = spectrum[-1] = 0
    signal = np.fft.irfft(spectrum, count)
    return signal / max(np.std(signal), 1e-6)


def write(name, signal, loop=False):
    signal = np.tanh((signal - signal.mean()) * .8)
    if not loop:
        samples = np.arange(len(signal))
        signal *= np.minimum(samples / (RATE * .003), 1)
        signal *= np.minimum(samples[::-1] / (RATE * .035), 1)
    signal *= (.61 if loop else .77) / max(np.max(np.abs(signal)), 1e-6)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    path = OUTPUT / (name + '.ogg')
    sf.write(path, signal.astype(np.float32), RATE, format='OGG', subtype='VORBIS')
    info = sf.info(path)
    print(f'{path.name}: {info.duration:.3f}s, {info.channels}ch, {info.samplerate}Hz')


if __name__ == '__main__':
    t = time(3)
    grow = np.clip(t / 3, 0, 1)
    envelope = (1 - np.exp(-t / .18)) * (.08 + .92 * grow ** 1.5)
    phase = 2 * np.pi * (58 * t + 24 * t * t + 17 * t ** 3)
    pressure = noise(len(t), 80, 2100) * .26 + np.sin(phase + .7 * np.sin(phase * .501)) * .22
    air = noise(len(t), 900, 6000) * (.10 + grow * .21)
    sparks = (.5 + .5 * np.sin(2 * np.pi * (13 * t + 4 * t * t))) ** 18
    charge = (pressure + air + noise(len(t), 2300, 11000) * sparks * .20) * envelope
    write('purple_charge', charge)

    t = time(.58)
    onset = 1 - np.exp(-t / .0015)
    phase = 2 * np.pi * (46 * t + 360 * .016 * (1 - np.exp(-t / .016)))
    release = noise(len(t), 100, 7700) * np.exp(-t / .115) * .48
    release += np.sin(phase) * np.exp(-t / .14) * .5
    release += noise(len(t), 3500, 13000) * np.exp(-t / .017) * .30
    write('purple_release', release * onset)

    t = time(6)
    p = 2 * np.pi * np.arange(len(t)) / len(t)
    wind = noise(len(t), 95, 2800) * (.33 + .04 * np.sin(p * 5))
    ion = noise(len(t), 1800, 8800) * (.10 + .06 * np.sin(p * 37) ** 10)
    chains = (.5 + .5 * np.sin(p * 103 + .8 * np.sin(p * 3))) ** 16
    flow = wind + ion + noise(len(t), 4200, 13000) * chains * .18
    flow += np.sin(p * 474 + .8 * np.sin(p * 2)) * .065
    write('purple_flight', flow, loop=True)

    t = time(1.4)
    seal = noise(len(t), 130, 5000) * np.exp(-t / .24) * .36
    seal += np.sin(2 * np.pi * (77 * t + 180 * .07 * (1 - np.exp(-t / .07)))) * np.exp(-t / .23) * .32
    seal += noise(len(t), 1900, 9500) * np.exp(-t / .06) * .26
    write('purple_end', seal)

"""Replace Purple formation and dissipation only; keep the existing release and flight recordings."""
import numpy as np
import generate_purple_audio as audio


def smooth(x):
    x = np.clip(x, 0, 1)
    return x * x * (3 - 2 * x)


def formation_air():
    audio.rng = np.random.default_rng(370101)
    t = audio.time(4)
    # Fixed noise bands retain friction without the original rising oscillator or electrical chirps.
    turbulence = 1 + .10 * np.tanh(audio.noise(len(t), .35, 2.1))
    body = audio.noise(len(t), 260, 1900) * .16
    friction = audio.noise(len(t), 900, 6000) * .24
    fine_air = audio.noise(len(t), 4200, 9800) * .035
    envelope = smooth(t / .11) * (1 - smooth((t - 3.82) / .18))
    audio.write('purple_charge', (body + friction + fine_air) * turbulence * envelope)


def dissipating_air():
    audio.rng = np.random.default_rng(370102)
    t = audio.time(1.4)
    # A soft inward breath and scattered grains die away; no launch crack, bass note or impact boom.
    envelope = smooth(t / .085) * np.exp(-t / .32)
    breath = (audio.noise(len(t), 180, 2300) * .27
              + audio.noise(len(t), 1200, 6500) * .11) * envelope
    grains = np.zeros_like(t)
    for onset, width, strength in ((.14, .035, .065), (.28, .042, .049), (.46, .048, .032),
                                   (.69, .053, .020), (.96, .060, .012)):
        grains += audio.noise(len(t), 2800, 10500) * strength * np.exp(-((t - onset) / width) ** 2)
    signal = breath + grains
    tail = signal.copy()
    for seconds, gain in ((.071, .23), (.157, .15), (.283, .08)):
        delay = round(seconds * audio.RATE)
        signal[delay:] += tail[:-delay] * gain
    signal *= 1 - smooth((t - 1.08) / .32)
    audio.write('purple_end', signal)


if __name__ == '__main__':
    formation_air()
    dissipating_air()

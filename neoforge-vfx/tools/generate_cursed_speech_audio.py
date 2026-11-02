"""Original spell Foley: inhaled pressure and a layered, nonverbal command impact."""
from pathlib import Path
import math, random, struct, subprocess, wave
ROOT=Path(__file__).resolve().parents[2]
RATE=44100
def generate(name, duration, charge):
    rng=random.Random(1060+int(charge)); samples=[];low=0.;phase=0.
    for i in range(int(RATE*duration)):
        t=i/RATE;u=t/duration;n=rng.uniform(-1,1);low+=.045*(n-low)
        if charge:
            envelope=math.sin(math.pi*u)**1.2
            phase+=2*math.pi*(96+48*u)/RATE
            sample=(low*.65+(n-low)*.06+math.sin(phase)*.13)*envelope*u
        else:
            # A dry percussive onset, dark formant-like body, then three decaying air echoes.
            envelope=(1-math.exp(-t/.003))*math.exp(-t/.15)
            phase+=2*math.pi*(67+90*math.exp(-t/.045))/RATE
            voiced=math.sin(phase)+.34*math.sin(phase*2.01)+.16*math.sin(phase*3.97)
            snap=math.exp(-t/.017)*(n-low)*.35
            echoes=sum(math.exp(-max(0,t-delay)/.055)*(1-math.exp(-max(0,t-delay)/.004))*.22**(j+1) for j,delay in enumerate([.13,.23,.34]) if t>=delay)
            sample=(voiced*.27+low*.8)*envelope+snap+low*echoes
        samples.append(sample*min(1,(duration-t)/.025))
    peak=max(map(abs,samples));pcm=[int(math.tanh(v/peak*1.3)*28000) for v in samples]
    out=ROOT/'exports/java-0.106.0-cursed-speech';out.mkdir(exist_ok=True)
    wav=out/(name+'.wav')
    with wave.open(str(wav),'wb') as f:
        f.setnchannels(1);f.setsampwidth(2);f.setframerate(RATE);f.writeframes(struct.pack('<'+'h'*len(pcm),*pcm))
    for module in ['neoforge-vfx','forge-vfx']:
        dest=ROOT/module/'src/main/resources/assets/jujutsuvfx/sounds'/(name+'.ogg')
        subprocess.run(['ffmpeg','-y','-hide_banner','-loglevel','error','-i',str(wav),'-c:a','libvorbis','-q:a','6',str(dest)],check=True)
if __name__=='__main__':
    generate('cursed_speech_charge',.40,True)
    generate('cursed_speech_release',.72,False)
    print('Generated original 0.40 s charge / 0.72 s command pressure Foley for both loaders.')

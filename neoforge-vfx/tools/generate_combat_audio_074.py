"""Original mono bell and high-frequency cutting transients; no sampled soundtrack."""
from pathlib import Path
import json,sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'build/audio-python'))
import numpy as np
import soundfile as sf

RATE=44100
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/jujutsuvfx'
def band(rng,n,low,high):
    f=np.fft.rfftfreq(n,1/RATE)
    spectrum=np.fft.rfft(rng.standard_normal(n))
    spectrum*=(1-np.exp(-(f/low)**4))*np.exp(-(f/high)**4)
    signal=np.fft.irfft(spectrum,n)
    return signal/max(np.std(signal),1e-6)
def write(name,signal):
    signal=signal-signal.mean()
    signal=np.tanh(signal*.72)
    signal*=np.minimum(np.arange(len(signal))/(RATE*.0008),1)
    signal*=np.minimum(np.arange(len(signal))[::-1]/(RATE*.015),1)
    signal*=.72/max(np.max(np.abs(signal)),1e-6)
    path=ROOT/'sounds'/f'{name}.ogg'
    assert not path.exists(),path
    sf.write(path,signal.astype(np.float32),RATE,format='OGG',subtype='VORBIS')
    print(name,round(len(signal)/RATE,3),'s mono')

rng=np.random.default_rng(74080)
t=np.arange(int(RATE*.76))/RATE
ding=np.zeros_like(t)
for hz,weight,decay in [(2100,.70,.22),(3148,.20,.14),(5270,.11,.085),(6830,.045,.044)]:
    ding+=weight*np.sin(2*np.pi*hz*t)*np.exp(-t/decay)
ding+=(band(rng,len(t),2300,10000)*.08*np.exp(-t/.0035))
write('infinity_ding',ding)

for i,length in enumerate([.17,.195,.155],1):
    rng=np.random.default_rng(74100+i)
    t=np.arange(int(RATE*length))/RATE
    u=np.maximum(t-.007,0);gate=(t>=.007)*(1-np.exp(-u/.0008))
    hz=1300+i*110+(3900+i*190)*np.exp(-u/.027)
    phase=2*np.pi*np.cumsum(hz)/RATE
    steel=(np.sin(phase)+.13*np.sin(phase*1.71))*gate*np.exp(-u/.030)
    whip=band(rng,len(t),1600,11500)*gate*np.exp(-u/.007)*.28
    air=band(rng,len(t),850,7800)*np.exp(-((t-.037)/.026)**2)
    tail=band(rng,len(t),1800,9200)*gate*np.exp(-u/.048)
    write(f'shrine_focus_cut_{i}',steel*.40+whip+air*.22+tail*.18)

rng=np.random.default_rng(74150)
t=np.arange(int(RATE*.53))/RATE
u=np.maximum(t-.085,0);gate=(t>=.085)*(1-np.exp(-u/.001))
intake=band(rng,len(t),550,7600)*np.exp(-((t-.07)/.037)**2)
shear=band(rng,len(t),1450,11000)*gate*np.exp(-u/.020)
ring=(np.sin(2*np.pi*1360*u)+.2*np.sin(2*np.pi*3060*u))*gate*np.exp(-u/.10)
write('shrine_focus_open',intake*.27+shear*.42+ring*.25)

p=ROOT/'sounds.json';sounds=json.loads(p.read_text('utf-8'))
sounds['infinity_ding']={'subtitle':'subtitles.jujutsuvfx.infinity_ding','sounds':[{'name':'jujutsuvfx:infinity_ding','attenuation_distance':48}]}
sounds['shrine_focus_open']={'subtitle':'subtitles.jujutsuvfx.shrine_focus_open','sounds':[{'name':'jujutsuvfx:shrine_focus_open','attenuation_distance':64}]}
sounds['shrine_focus_cut']={'subtitle':'subtitles.jujutsuvfx.shrine_focus_cut','sounds':[
    {'name':f'jujutsuvfx:shrine_focus_cut_{i}','attenuation_distance':48} for i in range(1,4)]}
p.write_text(json.dumps(sounds,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
for language,labels in [('zh_cn',('无下限：叮鸣','伏魔御厨子：暗幕落下','伏魔御厨子：疾速切裂')),
                        ('en_us',('Infinity deflects','Shrine darkness falls','Shrine blades shear'))]:
    p=ROOT/'lang'/f'{language}.json';data=json.loads(p.read_text('utf-8'))
    for name,label in zip(('infinity_ding','shrine_focus_open','shrine_focus_cut'),labels):data['subtitles.jujutsuvfx.'+name]=label
    p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

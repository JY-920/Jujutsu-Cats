"""Original metallic vow, resonant light fall and sword pickup Foley."""
from pathlib import Path
import math,random,struct,subprocess,wave,json
ROOT=Path(__file__).resolve().parents[2]
RATE=44100
out=ROOT/'exports/java-0.107.0-yuta-domain'
out.mkdir(parents=True,exist_ok=True)
for kind,name,duration in [(0,'yuta_domain_open',2.3),(1,'yuta_ladder',.95),(2,'yuta_sword',.52)]:
 rng=random.Random(1070+kind);samples=[];low=0.
 for i in range(round(duration*RATE)):
  t=i/RATE;n=rng.uniform(-1,1);low+=.04*(n-low)
  if kind==0:
   env=(1-math.exp(-t/.022))*math.exp(-t/1.1)
   chime=sum(math.sin(2*math.pi*f*t)*math.exp(-t/d)/a for f,d,a in [(220,1.4,4),(329.63,1.2,6),(493.88,.9,9),(880,.65,12)])
   body=math.sin(2*math.pi*(52*t+3*(1-math.exp(-t*5))))*.3
   x=(chime+body+low*.75)*env+(n-low)*.08*math.exp(-t/.06)
  elif kind==1:
   env=(1-math.exp(-t/.015))*math.exp(-t/.28)
   x=env*(low*.9+math.sin(2*math.pi*110*t)*.17+math.sin(2*math.pi*660*t)*.07)+(n-low)*.14*math.exp(-t/.12)
  else:
   env=(1-math.exp(-t/.003))*math.exp(-t/.11)
   x=env*((n-low)*.23+math.sin(2*math.pi*(1320*t+35*t*t))*.10+math.sin(2*math.pi*1760*t)*.04)
  samples.append(x*min(1,(duration-t)/.06))
 peak=max(map(abs,samples));pcm=[int(math.tanh(x/peak*1.25)*26000) for x in samples]
 wav=out/(name+'.wav')
 with wave.open(str(wav),'wb') as f:
  f.setnchannels(1);f.setsampwidth(2);f.setframerate(RATE);f.writeframes(struct.pack('<'+'h'*len(pcm),*pcm))
 for module in ['neoforge-vfx','forge-vfx']:
  dest=ROOT/module/'src/main/resources/assets/jujutsuvfx/sounds'/(name+'.ogg')
  subprocess.run(['ffmpeg','-y','-hide_banner','-loglevel','error','-i',str(wav),'-c:a','libvorbis','-q:a','6',str(dest)],check=True)
for module in ['neoforge-vfx','forge-vfx']:
 p=ROOT/module/'src/main/resources/assets/jujutsuvfx/sounds.json';data=json.loads(p.read_text(encoding='utf-8'))
 for name in ['yuta_domain_open','yuta_ladder','yuta_sword']:
  data[name]={'sounds':['jujutsuvfx:'+name],'subtitle':'subtitles.jujutsuvfx.'+name}
 p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Generated original Yuta domain / light pillar / sword Foley for both loaders.')

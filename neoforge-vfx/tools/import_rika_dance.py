"""Preserve the supplied BB model and atlas; author the reference bounce as numeric bone keys."""
from pathlib import Path
import base64, copy, json, math, sys

source = Path(sys.argv[1])
root = Path(__file__).resolve().parents[2]
model = json.loads(source.read_text(encoding='utf-8-sig'))
groups = []
def visit(nodes):
    for node in nodes:
        if isinstance(node, dict):
            groups.append({k:copy.deepcopy(v) for k,v in node.items() if k != 'children'})
            visit(node.get('children', []))
visit(model['outliner'])
model['groups'] = groups
by_name = {g['name']: g for g in groups}
period = 88 / 30
tracks = {}
def channel(name, kind, formula):
    group = by_name[name]
    animator = tracks.setdefault(group['uuid'], {'name':name,'type':'bone','keyframes':[]})
    for i in range(177):
        t = period * i / 176
        x,y,z = formula(2*math.pi*i/176)
        animator['keyframes'].append({'channel':kind,'time':t,'interpolation':'linear',
            'data_points':[{'x':x,'y':y,'z':z}]})
# Same continuous push/settle envelope as YutaDanceMotion, in the same tracked clock.
def pulse(t,n):
    u=(t*n/(2*math.pi))%1
    x=u/.20 if u<.20 else (u-.20)/.80
    s=x*x*(3-2*x)
    return s if u<.20 else 1-s
channel('root','position',lambda t:(-.74*math.sin(t*4),2.06*pulse(t,8),0))
channel('thorax','rotation',lambda t:(2.3*pulse(t,8),2.0*math.sin(t*4),-1.7*math.sin(t*4)))
channel('abdomen','rotation',lambda t:(-1.8*pulse(t,8),-2.0*math.sin(t*4),1.2*math.sin(t*4)))
channel('head','rotation',lambda t:(-1.2*pulse(t,8),-1.3*math.sin(t*4),.8*math.sin(t*4)))
channel('neck','rotation',lambda t:(-.8*pulse(t,8),0,.8*math.sin(t*4)))
channel('tail','rotation',lambda t:(1.2*math.sin(t*8-.35),4*math.sin(t*4-.3),2*math.sin(t*4)))
for side,sign in [('left',1),('right',-1)]:
    channel(side+'_shoulder','position',lambda t,s=sign:(0,.3*pulse(t,8),0))
    channel(side+'_upper_arm','rotation',lambda t,s=sign:(2.0*pulse(t,8),0,s*12*pulse(t,4)))
    channel(side+'_forearm','rotation',lambda t,s=sign:(2.2*pulse(t,8),0,-s*7*pulse(t,4)))
    channel(side+'_hand','rotation',lambda t,s=sign:(1.2*pulse(t,8),0,s*2*pulse(t,4)))
model['animations'] = [{'name':'animation.rika.dance','loop':'loop','length':period,'animators':tracks}]
atlas = base64.b64decode(model['textures'][0]['source'].split(',',1)[1])
for project in ['neoforge-vfx','forge-vfx']:
    assets = root/project/'src/main/resources/assets/jujutsuvfx'
    (assets/'models/entity/rika_dance.bbmodel').write_text(json.dumps(model,separators=(',',':')),encoding='utf-8')
    (assets/'textures/entity/rika_dance.png').write_bytes(atlas)
print(f'Imported {len(model["elements"])} cubes, {len(groups)} bones, original 256x512 atlas, {period:.4f}s loop')

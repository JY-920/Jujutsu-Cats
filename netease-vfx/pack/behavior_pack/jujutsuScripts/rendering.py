# -*- coding: utf-8 -*-
"""Native postprocess rendering shared by local and remote casts."""
from __future__ import division
import math
import time
from . import config as C
from .core import sub, dot, cross, unit, length, add, mul, visible_position
from .techniques import technique_position


class EffectRenderer(object):
    def __init__(self, client):
        self.c = client

    def definitions(self):
        names = [('jujutsuvfx_void', 'void'), ('jujutsuvfx_shrine', 'shrine')]
        names += [('jujutsuvfx_purple_'+str(i),'purple') for i in range(C.VISIBLE_PURPLES)]
        names += [('jujutsuvfx_technique_'+str(i),'technique') for i in range(C.VISIBLE_TECHNIQUES)]
        names += [('jujutsuvfx_slash_'+str(i),'slash') for i in range(C.VISIBLE_SLASHES)]
        names += [('jujutsuvfx_aura_'+str(i),'aura') for i in range(4)]
        return names

    def render(self):
        c, packet = self.c, self.c.packet
        if not packet:
            return
        elapsed = min(.20, max(0.0, time.time()-c.received))
        camera, forward = c.camera.GetPosition(), c.camera.GetForward()
        if not camera or not forward:
            return
        camera, forward = list(camera), unit(list(forward))
        side = cross(forward,[0,1,0])
        right = unit(side) if length(side)>.001 else [-1,0,0]
        up=unit(cross(right,forward))
        aspect=float(c.game.GetScreenSize()[0])/max(1.0,c.game.GetScreenSize()[1])
        tangent=math.tan(math.radians(c.camera.GetFov())*.5)
        dimension=c.game.GetCurrentDimension()
        flash=1.0 if packet.get('flash',C.FLASH_ENABLED) else 0.0
        active=set()
        def view(p):
            p=sub(p,camera)
            return [dot(p,right),dot(p,up),dot(p,forward)]
        def direction(p):
            return [dot(p,right),dot(p,up),dot(p,forward)]
        def show(name,values):
            if name in c.passes:
                c.parameters(name,values);c.enable(name,True);active.add(name)
        def in_view(p,r):
            x,y,z=view(p)
            return z>-r and abs(x)<max(z,0)*aspect*tangent+r and abs(y)<max(z,0)*tangent+r
        def nearest(states,limit):
            values=[s for s in states if s['dimension']==dimension]
            values.sort(key=lambda p:length(sub(p['pos'],camera)))
            # Within each pass family, transparent effects composite back to front.
            return list(reversed(values[:limit]))
        # Both domains stay visible from either half of an overlapping pair.
        all_domains=[d for d in packet.get('domains',[]) if d['dimension']==dimension]
        inside=[d for d in all_domains if length(sub(camera,d['pos']))<d['radius']]
        ids=set(d['id'] for d in inside)
        for d in list(inside):
            ids.update(d.get('clash',[]))
        selected={}
        for d in all_domains:
            if d['id'] in ids and (d.get('kind','void') not in selected or d['age']>selected[d.get('kind','void')]['age']):
                selected[d.get('kind','void')]=d
        for kind,d in selected.items():
            age=c.age(d,elapsed);remaining=max(0,d['duration']/30.0-age)
            fade=min(1,remaining/.55)
            facing=list(d.get('direction',[0,0,1]))
            clash=1.0 if len(selected)>1 and d.get('clash') else 0.0
            angles=[math.atan2(forward[0],forward[2]),math.asin(max(-.99999,min(.99999,forward[1]))),age,fade]
            if kind=='void':
                show('jujutsuvfx_void',[angles,facing+[0],[aspect,tangent,remaining,flash],facing+[clash]])
            else:
                show('jujutsuvfx_shrine',[angles,sub(d['pos'],camera)+[d['radius']],
                    [aspect,tangent,1 if d.get('focus') else 0,flash],facing+[clash]])
        purples=[p for p in packet.get('purples',[]) if p['phase']!='hold' and
                 in_view(visible_position(p,elapsed),max(p['radius']*2.6,7 if p['phase']=='fusion' else 1))]
        for index,p in enumerate(nearest(purples,C.VISIBLE_PURPLES)):
            phase={'small':1,'fusion':2,'large':3,'flight':4,'end':5}.get(p['phase'],0)
            age=c.age(p,elapsed);radius=p['radius']
            if phase==1: radius=min(1,.12+.88*age)
            fade=max(0,1-age/.4) if phase==5 else 1
            show('jujutsuvfx_purple_'+str(index),[view(visible_position(p,elapsed))+[radius],
                [p['total']/30.0+elapsed,phase,age,fade],[aspect,tangent,flash,1 if p['large'] else 0],
                [1 if p.get('amplified') else 0]+forward])
        objects=[]
        for p in packet.get('purples',[]):
            if p.get('amplified') and p['phase'] in ('fusion','large'):
                # 200% Purple's extra energy belongs to the right hand only.
                owner_pos=c.factory.CreatePos(p['owner']).GetFootPos()
                if owner_pos:
                    local=p['owner']==c.player and length(sub(camera,owner_pos))<2.0
                    if local:
                        at=add(add(add(camera,mul(forward,.82)),mul(right,.32)),mul(up,-.22))
                    else:
                        side=unit(cross(p['direction'],[0,1,0]))
                        at=add(add(add(owner_pos,[0,1.25,0]),mul(p['direction'],.55)),mul(side,.35))
                    objects.append(dict(p,pos=at,radius=.14 if local else .24,code=9,phase_age=0,fade=1))
        for p in packet.get('techniques',[]):
            kind,phase=p['kind'],p['phase']
            if kind not in ('blue','red','fuga') or p.get('consume'):
                continue
            pos=technique_position(p,elapsed);age=c.age(p,elapsed)
            radius=p['radius'];code={'blue':1,'red':2,'fuga':4}[kind]
            if kind=='red':
                radius=.24 if phase in ('hold','windup') else .32
                if phase=='beam': code,radius=3,1.9
            if kind=='fuga':
                radius=.2+p.get('charge',0)*.4 if phase=='hold' else .65
                if phase=='flight': code=5
                elif phase=='burst': code,radius=6,min(12,1+age*30)
            fade=max(0,1-age/.5) if phase=='end' else 1
            if kind=='fuga' and phase=='burst': fade=max(0,1-age/1.2)
            margin=34 if code==3 else radius*3
            if not in_view(pos,margin): continue
            objects.append(dict(p,pos=pos,radius=radius,code=code,phase_age=age,fade=fade))
        for p in packet.get('bursts',[]):
            age=c.age(p,elapsed)
            if age<1.6:
                radius=.7+min(1,age/1.2)*3.6;code=7;fade=1
            else:
                radius=min(p['radius'],4+(age-1.6)*40);code=8;fade=max(0,1-(age-1.6)/2.8)
            if in_view(p['pos'],radius*2):
                objects.append(dict(p,radius=radius,code=code,phase_age=max(0,age-1.6) if code==8 else age,
                    total=p['age'],fade=fade,direction=[0,0,1]))
        for i,p in enumerate(nearest(objects,C.VISIBLE_TECHNIQUES)):
            show('jujutsuvfx_technique_'+str(i),[view(p['pos'])+[p['radius']],
                [p['total']/30.0+elapsed,p['code'],p['phase_age'],p['fade']],
                [aspect,tangent,p.get('charge',1),flash],direction(p.get('direction',[0,0,1]))+[0]])
        cuts=[s for s in packet.get('slashes',[]) if in_view(s['pos'],s['radius']*2.4)]
        # Contact effects first in selection, including when a large domain is active.
        cuts.sort(key=lambda p:(p['kind']=='slash',length(sub(p['pos'],camera))))
        cuts=[p for p in cuts if p['dimension']==dimension][:C.VISIBLE_SLASHES]
        cuts.sort(key=lambda p:length(sub(p['pos'],camera)),reverse=True)
        focus=set(d['id'] for d in all_domains if d.get('focus'))
        for i,p in enumerate(cuts):
            age=c.age(p,elapsed);duration=p['duration']/30.0
            fade=max(0,min(1,(duration-age)/min(.16,duration*.6)))
            style=p.get('style',0)
            if p.get('domain') in focus: style=[3,4,5][style%3]
            kind=0 if p['kind']=='slash' else 2 if p['kind']=='black_flash' else 1
            show('jujutsuvfx_slash_'+str(i),[view(p['pos'])+[p['radius']],
                direction(p.get('axis',[1,0,0]))+[age],[aspect,tangent,style,1 if p.get('cross') else 0],
                [p.get('seed',1),fade,duration,kind]])
        auras=list(packet.get('auras',[]))
        for p in packet.get('bursts',[]):
            if p['age']<48:
                auras.append(dict(p,kind='warning',duration=48))
        for i,p in enumerate(nearest(auras,4)):
            age=c.age(p,elapsed);fade=min(1,max(0,(p['duration']/30.0-age)/.25))
            local=p['owner']==c.player and length(sub(camera,c.factory.CreatePos(c.player).GetFootPos() or camera))<2.0
            kind=0 if p['kind']=='infinity' else 1 if p['kind']=='recovery' else 2
            position=add(p['pos'],[0,.8,0]) if kind<2 else p['pos']
            show('jujutsuvfx_aura_'+str(i),[view(position)+[p['radius'] if kind==2 else 1.15],
                [packet['tick']/30.0,kind,age,fade],[aspect,tangent,1 if local and kind<2 else 0,0],direction([0,1,0])+[0]])
        for name in c.passes:
            if name not in active:c.enable(name,False)
        current_ids=set()
        for group in ('purples','techniques','slashes','domains','auras','bursts'):
            current_ids.update(p['id'] for p in packet.get(group,[]))
        for key in list(c.phase_ages):
            if key[0] not in current_ids: del c.phase_ages[key]

# -*- coding: utf-8 -*-
from .core import length, sub


class SoundDirector(object):
    def __init__(self, client):
        self.c = client
        self.seen, self.domains = set(), {}

    def once(self, sound, position, volume=.75, pitch=1.0):
        self.c.audio.PlayCustomMusic('jujutsuvfx.'+sound,tuple(position),volume,pitch,False)

    def update(self):
        c,packet=self.c,self.c.packet
        dimension=c.game.GetCurrentDimension();alive=set()
        groups=packet.get('purples',[])+packet.get('techniques',[])+packet.get('bursts',[])
        for p in groups:
            if p['dimension']!=dimension:continue
            sid=p['id'];alive.add(sid)
            kind=p.get('kind','unlimited' if 'blue' in p else 'purple')
            phase=p.get('phase','merge' if p['age']<48 else 'burst')
            if p.get('consume'):phase='end'
            if phase==c.sound_phases.get(sid):continue
            previous=c.sound_phases.get(sid);c.sound_phases[sid]=phase;c.stop_loop(sid)
            loop=None
            if kind=='purple':
                if phase in ('small','fusion','large'):loop='purple_charge'
                elif phase=='flight':self.once('purple_release',p['pos'],.9)
                elif phase=='end':self.once('purple_end',p['pos'],.6)
            elif kind=='blue':
                if phase in ('hold','rest'):
                    loop='blue_loop'
                    if previous is None:self.once('blue_form',p['pos'],.65)
                elif phase=='end' and not p.get('consume'):self.once('blue_close',p['pos'],.5)
            elif kind=='red':
                if phase in ('hold','windup'):loop='red_air'
                elif phase in ('flight','beam'):self.once('red_cannon' if phase=='beam' else 'red_release',p['pos'],.85)
                elif phase=='end' and not p.get('consume'):self.once('red_impact',p['pos'],.55)
            elif kind=='fuga':
                if phase=='hold':loop='furnace_charge'
                elif phase=='flight':self.once('furnace_release',p['pos'],.8)
                elif phase=='burst':self.once('furnace_burst',p['pos'],.95)
            elif kind=='unlimited':
                if phase=='merge':loop='purple_charge'
                elif phase=='burst':
                    self.once('furnace_burst',p['pos'],.9,.72)
                    self.once('purple_end',p['pos'],.85,.8)
            if loop:
                attach=p['owner'] if phase in ('hold','windup','fusion','large','small') else None
                c.loops[sid]=c.audio.PlayCustomMusic('jujutsuvfx.'+loop,tuple(p['pos']),.55,1.0,True,attach)
        pos=c.factory.CreatePos(c.player).GetFootPos()
        now_domains={}
        for d in packet.get('domains',[]):
            if d['dimension']!=dimension:continue
            now_domains[d['id']]=d
            if not pos or length(sub(pos,d['pos']))>d['radius']:continue
            sid=d['id'];alive.add(sid)
            if d.get('kind','void')=='void':
                if sid not in c.loops:c.loops[sid]=c.audio.PlayCustomUIMusic('jujutsuvfx.void_air_friction',.45,1.0,True)
            elif sid not in self.domains:
                self.once('shrine_focus_open' if d.get('focus') else 'shrine_slash_1',d['pos'],.6)
        for sid,d in self.domains.items():
            if sid not in now_domains and d.get('kind')=='shrine':self.once('shrine_finish',d['pos'],.65)
        self.domains=now_domains
        # Snapshot retransmission never replays an already-heard impact.
        current=set()
        focus=set(d['id'] for d in packet.get('domains',[]) if d.get('focus'))
        for cut in packet.get('slashes',[]):
            current.add(cut['id'])
            if cut['id'] in self.seen or cut['dimension']!=dimension:continue
            if pos and length(sub(pos,cut['pos']))>96:continue
            sound='shrine_focus_cut_'+str(1+cut.get('seed',0)%3) if cut.get('domain') in focus else 'shrine_slash_'+str(1+cut.get('seed',0)%5)
            self.once(sound,cut['pos'],.42 if cut['kind']=='slash' else .65,1.18)
        self.seen=current
        for sid in list(c.loops):
            if sid not in alive:c.stop_loop(sid)
        for sid in list(c.sound_phases):
            if sid not in alive:del c.sound_phases[sid]

    def clear(self):
        self.seen.clear();self.domains.clear()

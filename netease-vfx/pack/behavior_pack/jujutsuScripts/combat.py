# -*- coding: utf-8 -*-
from __future__ import division
import math
import random
from collections import deque
import mod.server.extraServerApi as api
from . import config as C
from .core import add, sub, mul, dot, cross, unit, length, segment_distance
from .techniques import Technique, clamp, limit


class TechniqueManager(object):
    def __init__(self, server):
        self.s = server
        self.active, self.slashes, self.bursts, self.auras = {}, {}, {}, {}
        self.combo, self.stunned, self.amplified = {}, {}, {}
        self.domain_hits, self.fragment_visits, self.clashes = {}, set(), set()
        self.carving = deque()
        self.unlimited_blocks = server.factory.CreateModAttr(server.level).GetAttr('jujutsuvfx_unlimited_blocks', C.UNLIMITED_BREAK_BLOCKS)
        self.radius, self.duration, self.flash = C.DOMAIN_RADIUS, C.DOMAIN_TICKS, C.FLASH_ENABLED

    def held(self, owner):
        return next((p for p in self.active.values() if p.owner == owner and p.phase in ('hold', 'windup')), None)

    def eye(self, owner):
        pos = self.s.pos(owner)
        return add(pos, [0, 1.4 if self.s.is_player(owner) else 0.8, 0]) if pos else None

    def item(self, player):
        item = self.s.factory.CreateItem(player).GetPlayerItem(api.GetMinecraftEnum().ItemPosType.CARRIED, 0)
        return self.s.item_name(item)

    def use(self, owner, name):
        if name == C.SHRINE:
            return self.s.start_domain(owner, 'shrine')
        if name == C.AMPLIFIER:
            self.amplified[owner] = self.s.tick + 1800
            self.s.factory.CreateModAttr(owner).SetAttr('jujutsuvfx_amplified_until', self.s.tick + 1800, True)
            self.s.game.SetOneTipMessage(owner, u'术式增幅：60 秒内长按茈释放 200% 茈')
            return True
        if name in (C.INFINITY, C.RECOVERY):
            kind = 'infinity' if name == C.INFINITY else 'recovery'
            key = (owner, kind)
            if key in self.auras:
                self.auras.pop(key)
                return True
            self.auras[key] = {'id': self.s.sid(), 'owner': owner, 'kind': kind, 'age': 0,
                               'dimension': self.s.dimension(owner), 'pos': self.s.pos(owner),
                               'duration': 180 if kind == 'infinity' else 150}
            self.s.sync()
            return True
        if name not in C.CHARGE_SKILLS or name == C.PURPLE:
            return False
        return self.start(owner, name.split(':')[-1])

    def start(self, owner, kind):
        if not self.s.alive(owner) or owner in self.s.frozen or self.held(owner) or self.s.charging(owner):
            return False
        if self.s.tick < self.s.cooldowns.get((owner, kind), 0):
            return False
        eye = self.eye(owner)
        if eye is None:
            return False
        p = Technique(self.s.sid(), owner, self.s.dimension(owner), kind,
                      add(eye, mul(self.s.direction(owner), 3.2 if kind == 'blue' else 1.8)), self.s.direction(owner))
        self.active[p.sid] = p
        self.s.sync()
        return True

    def release(self, owner, name):
        p = self.held(owner)
        if p and name == 'jujutsuvfx:' + p.kind:
            p.direction = self.s.direction(owner)
            p.release()
            self.s.sync()

    def targets(self, center, radius, dimension, owner):
        s = self.s
        return [e for e in s.in_box(add(center, [-radius]*3), add(center, [radius]*3), dimension)
                if s.eligible_target(e, owner) and s.pos(e) is not None
                and self.living(e)
                and length(sub(add(s.pos(e), [0, 0.6, 0]), center)) <= radius + 0.7]

    def living(self, entity):
        value = self.s.factory.CreateAttr(entity).GetAttrMaxValue(api.GetMinecraftEnum().AttrType.HEALTH)
        return value is not None and value > 0

    def protected(self, entity, domain):
        return entity in domain.get('immune', [])

    def domain_targets(self, d):
        return [e for e in self.targets(d['pos'], d['radius'], d['dimension'], d['owner']) if not self.protected(e, d)]

    def hit(self, target, owner, damage, player_damage, tag, direction=None, push=0.0):
        if not self.s.eligible_target(target, owner):
            return False
        amount = player_damage if self.s.is_player(target) else damage
        result = self.s.factory.CreateHurt(target).Hurt(amount, 'magic', owner, None, False, 'jujutsuvfx:' + tag)
        if result is False:
            return False
        if direction is not None and push:
            self.s.set_motion(target, tuple(add(mul(unit(direction), push), [0, min(0.8, push*0.32), 0])))
        return True

    def slash(self, owner, center, dimension, radius, duration=12, domain=None):
        seed = random.randrange(1, 1000000)
        rng = random.Random(seed)
        sid = self.s.sid()
        direction = self.s.direction(owner) if self.s.alive(owner) else [0, 0, 1]
        right = unit(cross(direction, [0, 1, 0]))
        up = unit(cross(right, direction))
        angle = rng.random()*math.pi
        axis = add(mul(right, math.cos(angle)), mul(up, math.sin(angle)))
        self.slashes[sid] = {'id': sid, 'owner': owner, 'dimension': dimension, 'pos': list(center),
            'axis': axis, 'direction': direction, 'radius': radius, 'age': 0, 'duration': duration,
            'style': rng.randrange(4), 'cross': rng.random() < 0.40, 'seed': seed,
            'domain': domain or '', 'kind': 'slash'}
        return self.slashes[sid]

    def aim(self, p):
        eye = self.eye(p.owner)
        if eye is None:
            return p.pos, None
        goal, selected, nearest = add(eye, mul(p.direction, p.reach)), None, p.reach+1
        middle = add(eye, mul(p.direction, p.reach*0.5))
        for e in self.targets(middle, p.reach*0.65+3, p.dimension, p.owner):
            pos = add(self.s.pos(e), [0, 0.7, 0])
            along = dot(sub(pos, eye), p.direction)
            if 0 < along <= p.reach and segment_distance(pos, eye, goal) < 2.0 and along < nearest:
                selected, nearest = e, along
        if selected:
            return add(self.s.pos(selected), [0, 0.7, 0]), selected
        block = self.s.factory.CreateBlockInfo(self.s.level)
        for i in range(2, int(p.reach*2)+1):
            at = add(eye, mul(p.direction, i*0.5))
            b = block.GetBlockNew(tuple(int(math.floor(v)) for v in at), p.dimension)
            if b and b.get('name') not in ('minecraft:air', 'minecraft:water', 'minecraft:flowing_water'):
                return at, None
        return add(eye, mul(p.direction, min(12.0, p.reach))), None

    def carve_sphere(self, center, radius, dimension):
        def coordinates():
            c = tuple(int(math.floor(v)) for v in center)
            r = int(math.ceil(radius))
            for y in range(-r, r+1):
                for x in range(-r, r+1):
                    for z in range(-r, r+1):
                        if x*x+y*y+z*z <= radius*radius:
                            yield dimension, (c[0]+x, c[1]+y, c[2]+z)
        if self.s.break_blocks:
            self.carving.append(coordinates())

    def carve_slash(self, cut, width=0.7):
        axis, center, r = cut['axis'], cut['pos'], cut['radius']
        forward = unit(cut['direction'])
        normal = unit(cross(axis, forward))
        def coordinates():
            c = tuple(int(math.floor(v)) for v in center)
            box = int(math.ceil(r+1))
            for y in range(-box, box+1):
                for x in range(-box, box+1):
                    for z in range(-box, box+1):
                        q = [x, y, z]
                        if abs(dot(q, axis)) <= r and abs(dot(q, normal)) <= width and abs(dot(q, forward)) <= 2.5:
                            yield cut['dimension'], (c[0]+x, c[1]+y, c[2]+z)
        if self.s.break_blocks:
            self.carving.append(coordinates())

    def drain_carving(self):
        if not self.s.break_blocks:
            self.carving.clear()
            return
        # Budget enumeration too, rather than constructing a huge block list on one frame.
        for _ in range(192):
            if not self.carving or len(self.s.blocks) >= 2048:
                break
            job = self.carving.popleft()
            try:
                key = next(job)
            except StopIteration:
                continue
            self.carving.append(job)
            if key not in self.s.block_keys:
                self.s.block_keys.add(key)
                self.s.blocks.append(key)

    def cast_slashes(self, p):
        if p.age not in ((0, 4, 8, 12, 16, 20) if p.large else (0,)):
            return
        center, target = self.aim(p)
        if p.large:
            center = add(center, [random.uniform(-3, 3), random.uniform(-1.8, 2.5), random.uniform(-3, 3)])
        cut = self.slash(p.owner, center, p.dimension, random.uniform(5, 8) if p.large else random.uniform(3.5, 5.5))
        # The random plane and the hit region share the same endpoints.
        a, b = add(center, mul(cut['axis'], -cut['radius'])), add(center, mul(cut['axis'], cut['radius']))
        for e in self.targets(center, cut['radius']+1, p.dimension, p.owner):
            if segment_distance(add(self.s.pos(e), [0, 0.6, 0]), a, b) < 1.8:
                if self.s.tick >= p.hit_at.get(e, 0):
                    self.hit(e, p.owner, 45 if p.kind == 'dismantle' else 55, 5, p.kind)
                    p.hit_at[e] = self.s.tick+7
        if p.kind == 'cleave':
            self.carve_slash(cut)

    def punch(self, owner, victim, heavy=False, special=False):
        if not self.s.eligible_target(victim, owner):
            return
        count = self.combo.get(owner, 0)
        black = count % 3 == 2
        direction = unit(sub(self.s.pos(victim), self.s.pos(owner)))
        if not self.hit(victim, owner, 20 if black else 8, 8 if black else 4, 'black_flash' if black else 'fist', direction, 0.85 if black else 0.0):
            return
        self.combo[owner] = count+1
        if special:
            self.s.set_motion(victim, tuple(add(mul(direction, 0.28), [0, 0.95, 0])))
            self.s.set_motion(owner, (0, 0.48, 0))
        sid = self.s.sid()
        self.slashes[sid] = {'id': sid, 'owner': owner, 'dimension': self.s.dimension(owner),
            'pos': add(self.s.pos(victim), [0, 0.6, 0]), 'direction': direction, 'axis': [0,1,0],
            'age': 0, 'duration': 20, 'radius': 2.1 if black else 1.3,
            'kind': 'black_flash' if black else 'fist', 'style': count % 2, 'cross': False,
            'seed': random.randrange(999999), 'domain': ''}

    def on_attack(self, args):
        owner, target = args.get('playerId'), args.get('victimId')
        if not owner or self.item(owner) != C.FIST:
            return
        args['cancel'], args['isKnockBack'] = True, False
        if owner in self.s.frozen or not self.s.alive(target):
            return
        if length(sub(self.s.pos(owner), self.s.pos(target))) > 4.5 or self.s.tick < self.s.cooldowns.get((owner, 'fist'), 0):
            return
        self.s.cooldowns[(owner, 'fist')] = self.s.tick+9
        self.punch(owner, target)
        self.s.sync()

    def on_damage(self, args):
        target = args.get('entityId')
        if args.get('damage', 0) <= 0 or args.get('cause') in ('suicide', 'void', 'override'):
            return
        domain_hit = args.get('customTag') == 'jujutsuvfx:shrine'
        if (target, 'infinity') in self.auras and not domain_hit:
            args['damage'], args['knock'], args['ignite'] = 0, False, False
            self.auras[(target, 'infinity')]['impact'] = self.s.tick
        else:
            self.auras.pop((target, 'recovery'), None)

    def on_drop(self, args):
        entity = args.get('itemEntityId')
        if not entity:
            return
        data = self.s.factory.CreateItem(entity).GetDroppedItem(entity)
        if self.s.item_name(data) == C.FRAGMENT:
            self.s.DestroyEntity(entity)

    def information(self, d, target):
        key = (d['id'], target)
        if key in self.fragment_visits or not self.s.is_player(target):
            return
        self.fragment_visits.add(key)
        item = self.s.factory.CreateItem(target)
        slots = item.GetPlayerAllItems(api.GetMinecraftEnum().ItemPosType.INVENTORY) or []
        for i in range(36):
            data = slots[i] if i < len(slots) else None
            if not data or data.get('count', 0) <= 0 or self.s.item_name(data) in ('', 'minecraft:air'):
                item.SpawnItemToPlayerInv({'newItemName': C.FRAGMENT, 'newAuxValue': 0, 'count': 1}, target, i)

    def begin_clash(self, d):
        for other in list(self.s.domains.values()):
            if d['id'] == other['id'] or d.get('kind', 'void') == other.get('kind', 'void') or d['dimension'] != other['dimension']:
                continue
            if length(sub(d['pos'], other['pos'])) >= d['radius']+other['radius']:
                continue
            pair = tuple(sorted((d['id'], other['id'])))
            if pair in self.clashes:
                continue
            self.clashes.add(pair)
            axis = unit(sub(other['pos'], d['pos']))
            if length(sub(other['pos'], d['pos'])) < 1:
                axis = d['direction']
            for current, sign in ((d, -1), (other, 1)):
                current['clash'], current['clash_end'] = list(pair), self.s.tick+150
                current['immune'] = [d['owner'], other['owner']]
                current['direction'] = mul(axis, sign)
                current['duration'] = max(current['duration'], current['age']+240)

    def tick_domains(self):
        s = self.s
        for pair in list(self.clashes):
            a, b = [s.domains.get(sid) for sid in pair]
            if not a or not b:
                for d in (a, b):
                    if d:
                        d.pop('clash', None)
                        d['immune'] = []
                self.clashes.discard(pair)
            elif s.tick >= max(a['clash_end'], b['clash_end']):
                loser = a if random.random() < 0.5 else b
                s.domains.pop(loser['id'], None)
                if loser.get('model') and s.alive(loser['model']):
                    s.DestroyEntity(loser['model'])
        live_ids=set(s.domains)
        self.domain_hits=dict((key,value) for key,value in self.domain_hits.items() if key[0] in live_ids)
        self.fragment_visits=set(key for key in self.fragment_visits if key[0] in live_ids)
        for sid, d in list(s.domains.items()):
            targets = self.domain_targets(d)
            if d.get('kind', 'void') == 'void':
                for target in targets:
                    self.information(d, target)
                continue
            d['focus'] = len(targets) == 1
            d['target'] = targets[0] if d['focus'] else ''
            if d['age'] < 15:
                continue
            # Each late entrant receives their own schedule; no single-target refill loop.
            for target in targets:
                key = (sid, target)
                record = self.domain_hits.setdefault(key, {'hits': 0, 'next': s.tick})
                wanted = 15 if d['focus'] else 5
                left = max(1, d['duration']-d['age'])
                if s.tick < record['next']:
                    continue
                remaining = max(1, wanted-record['hits'])
                record['next'] = s.tick + max(1, min(8 if d['focus'] else 27, left//remaining))
                record['hits'] += 1
                center = add(s.pos(target), [random.uniform(-1.1,1.1), random.uniform(0.15,1.8), random.uniform(-1.1,1.1)])
                self.slash(d['owner'], center, d['dimension'], random.uniform(1.7,3.4), 10 if d['focus'] else 25, sid)
                self.hit(target, d['owner'], 20 if d['focus'] else 50, 1.5 if d['focus'] else 3, 'shrine')
            if not d['focus'] and d['age'] % 4 == 0:
                for _ in range(max(1, int(d['radius']/12))):
                    angle = random.random()*math.pi*2
                    radius = math.sqrt(random.random())*d['radius']*.9
                    center = add(d['pos'], [math.cos(angle)*radius, random.uniform(0.4,7), math.sin(angle)*radius])
                    self.slash(d['owner'], center, d['dimension'], random.uniform(2.3,5.0), 9, sid)

    def collide_blue_red(self):
        blues = [p for p in self.active.values() if p.kind == 'blue' and p.phase in ('hold', 'rest') and not p.consume]
        reds = [p for p in self.active.values() if p.kind == 'red' and p.phase in ('flight', 'beam') and not p.consume]
        for red in reds:
            end = add(red.pos, mul(red.direction, 32)) if red.phase == 'beam' else red.pos
            for blue in blues:
                if blue.consume or blue.dimension != red.dimension or segment_distance(blue.pos, red.previous, end) > blue.radius+0.6:
                    continue
                blue.consume = red.consume = True
                blue.switch('end')
                red.switch('end')
                sid = self.s.sid()
                self.bursts[sid] = {'id': sid, 'owner': red.owner, 'dimension': red.dimension,
                    'pos': blue.pos[:], 'age': 0, 'duration': 150, 'radius': C.UNLIMITED_RADIUS,
                    'blue': blue.pos[:], 'red': red.pos[:], 'hit': False}
                break

    def tick(self):
        s = self.s
        critical_changed = False
        for p in list(self.active.values()):
            if not s.alive(p.owner) or s.dimension(p.owner) != p.dimension:
                self.active.pop(p.sid, None)
                critical_changed = True
                continue
            phase_before = p.phase
            eye, look = self.eye(p.owner), s.direction(p.owner)
            if p.phase == 'hold' and s.is_player(p.owner) and self.item(p.owner) != 'jujutsuvfx:'+p.kind:
                p.release()
                if p.kind == 'fuga':
                    p.switch('end')
            changed = p.advance(eye, look)
            critical_changed = critical_changed or changed or phase_before != p.phase
            if p.kind == 'blue' and p.phase in ('hold', 'rest'):
                if p.total % 3 == 0:
                    for target in self.targets(p.pos, 7, p.dimension, p.owner):
                        delta = sub(p.pos, add(s.pos(target), [0, .5, 0]))
                        s.set_motion(target, tuple(limit(mul(delta, .075), .65)))
                        if length(delta) < 1.5 and s.tick >= p.hit_at.get(target, 0):
                            self.hit(target, p.owner, 8, 1.5, 'blue')
                            p.hit_at[target] = s.tick+15
                if p.total % 3 == 0:
                    self.carve_sphere(p.pos, p.radius, p.dimension)
            elif p.kind == 'red' and p.phase in ('flight','beam','end'):
                if p.phase == 'end' and p.age != 0:
                    continue
                end = add(p.pos, mul(p.direction, 32)) if p.phase == 'beam' else p.pos
                start = p.origin if p.phase == 'beam' else p.previous
                pad = 2.5 if p.phase == 'beam' else 0.7
                for e in self.targets(mul(add(start,end), .5), length(sub(end,start))*.5+pad+1, p.dimension, p.owner):
                    if e not in p.hit and segment_distance(add(s.pos(e), [0,.7,0]), start, end) <= pad+0.6:
                        if self.hit(e,p.owner,85 if p.large else 45,10 if p.large else 6,'red',p.direction,1.2):
                            p.hit.add(e)
                if p.phase == 'beam' and p.age == 0:
                    for i in range(17):
                        self.carve_sphere(add(start,mul(p.direction,i*2)),1.9,p.dimension)
                elif p.phase == 'flight':
                    self.carve_sphere(p.pos,0.9,p.dimension)
            elif p.kind in ('dismantle','cleave') and p.phase == 'strike':
                self.cast_slashes(p)
            elif p.kind == 'cursed_fist' and p.phase == 'strike' and p.age == 0:
                around = self.targets(add(eye,mul(look,2)),3.8,p.dimension,p.owner)
                around.sort(key=lambda e:length(sub(s.pos(e),s.pos(p.owner))))
                for e in around[:3 if p.large else 1]:
                    self.punch(p.owner,e,p.large,p.large)
            elif p.kind == 'fuga':
                if p.phase == 'flight':
                    # Sample the swept segment: fast arrows cannot tunnel through a wall.
                    steps=max(1,int(math.ceil(length(sub(p.pos,p.previous))*3)))
                    for step in range(1,steps+1):
                        point=add(p.previous,mul(sub(p.pos,p.previous),step/float(steps)))
                        b=s.factory.CreateBlockInfo(s.level).GetBlockNew(tuple(int(math.floor(v)) for v in point),p.dimension)
                        if self.targets(point,.8,p.dimension,p.owner) or b and b.get('name') not in ('minecraft:air','minecraft:water'):
                            p.pos=point
                            p.switch('burst')
                            critical_changed = True
                            break
                if p.phase == 'burst' and p.age == 0:
                    for e in self.targets(p.pos,12,p.dimension,p.owner):
                        self.hit(e,p.owner,110,12,'fuga',sub(s.pos(e),p.pos),.8)
                        s.factory.CreateAttr(e).SetEntityOnFire(3,1)
                    self.carve_sphere(p.pos,12,p.dimension)
            if changed and p.phase not in ('hold','rest'):
                s.cooldowns[(p.owner,p.kind)]=max(s.cooldowns.get((p.owner,p.kind),0),s.tick+12)
            if p.phase == 'done':
                self.active.pop(p.sid,None)
                critical_changed = True
        burst_count = len(self.bursts)
        self.collide_blue_red()
        critical_changed = critical_changed or len(self.bursts) != burst_count
        for sid, burst in list(self.bursts.items()):
            burst['age'] += 1
            if burst['age'] >= 48 and not burst['hit']:
                burst['hit']=True
                for e in self.targets(burst['pos'],burst['radius'],burst['dimension'],burst['owner']):
                    self.hit(e,burst['owner'],320,18,'unlimited_purple',sub(s.pos(e),burst['pos']),1.0)
                if self.unlimited_blocks:
                    self.carve_sphere(burst['pos'],burst['radius'],burst['dimension'])
            if burst['age'] >= burst['duration']:
                self.bursts.pop(sid,None)
                critical_changed = True
        for sid, cut in list(self.slashes.items()):
            cut['age'] += 1
            if cut['age'] >= cut['duration'] or cut.get('domain') and cut['domain'] not in s.domains:
                self.slashes.pop(sid,None)
        for key, aura in list(self.auras.items()):
            if not s.alive(aura['owner']) or s.dimension(aura['owner']) != aura['dimension']:
                self.auras.pop(key,None)
                continue
            aura['age'] += 1
            aura['pos'] = s.pos(aura['owner'])
            if aura['kind']=='recovery' and aura['age']%30==0:
                attr=s.factory.CreateAttr(aura['owner']);health=api.GetMinecraftEnum().AttrType.HEALTH
                attr.SetAttrValue(health,min(attr.GetAttrMaxValue(health),attr.GetAttrValue(health)+10))
            if aura['age']>=aura['duration']:
                self.auras.pop(key,None)
        if s.tick%30==0:
            for player in api.GetPlayerList():
                slots=s.factory.CreateItem(player).GetPlayerAllItems(api.GetMinecraftEnum().ItemPosType.INVENTORY) or []
                if any(s.item_name(item)==C.FRAGMENT and item.get('count',0)>0 for item in slots if item):
                    for effect in ('slowness','weakness','mining_fatigue'):
                        s.factory.CreateEffect(player).AddEffectToEntity(effect,2,0,False)
        self.drain_carving()
        # Keep the 10 Hz movement snapshots, but emit each semantic transition
        # immediately. Otherwise auto-release/merge can skip its opening frame.
        if critical_changed:
            s.sync()

    def snapshot(self):
        return {'techniques':[p.snapshot() for p in self.active.values()], 'slashes':list(self.slashes.values()),
                'bursts':list(self.bursts.values()),'auras':list(self.auras.values()),'flash':self.flash}

    def clear(self):
        self.active.clear();self.slashes.clear();self.bursts.clear();self.auras.clear()
        self.carving.clear();self.domain_hits.clear();self.fragment_visits.clear();self.clashes.clear()

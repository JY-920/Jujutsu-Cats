# -*- coding: utf-8 -*-
"""Authoritative 30 Hz skill timelines, independent of the ModSDK renderer."""
from __future__ import division
import math
from .core import add, sub, mul, unit, dot, length, cross
from . import config as C


def clamp(x, lo, hi):
    return max(lo, min(hi, x))


def limit(v, maximum):
    return mul(unit(v), maximum) if length(v) > maximum else list(v)


def turn_towards(a, b, radians):
    a, b = unit(a), unit(b)
    angle = math.acos(clamp(dot(a, b), -1, 1))
    if angle <= radians:
        return b
    axis = cross(a, b)
    if length(axis) < 0.00001:
        axis = cross(a, [0, 1, 0] if abs(a[1]) < 0.95 else [1, 0, 0])
    return unit(add(mul(a, math.cos(radians)), mul(cross(unit(axis), a), math.sin(radians))))


class Technique(object):
    def __init__(self, sid, owner, dimension, kind, pos, direction):
        self.sid, self.owner, self.dimension, self.kind = sid, owner, dimension, kind
        self.pos, self.direction = list(pos), unit(direction)
        self.origin, self.previous = list(pos), list(pos)
        self.phase, self.age, self.total = 'hold', 0, 0
        self.radius, self.travelled, self.reach, self.speed = 0.25, 0.0, 32.0, 1.25
        self.large, self.charge, self.hit, self.hit_at = False, 0.0, set(), {}
        self.velocity, self.tether, self.coast = [0, 0, 0], 3.2, 0
        self.released, self.consume = False, False
        self.revision = 0
        if kind == 'blue':
            self.radius, self.reach = 1.8, 12.0
        elif kind == 'fuga':
            self.radius, self.reach, self.speed = 0.55, 96.0, 2.4
        elif kind in ('dismantle', 'cleave'):
            self.reach = 28.0 if kind == 'dismantle' else 18.0
        elif kind == 'cursed_fist':
            self.reach = 3.5

    def switch(self, phase):
        if self.phase == phase:
            return False
        self.phase, self.age = phase, 0
        self.revision += 1
        return True

    def release(self):
        if self.released or self.consume or self.phase != 'hold':
            return False
        before = self.phase
        self.released = True
        if self.kind == 'blue':
            self.coast, self.velocity = 12, limit(self.velocity, 0.21)
            self.switch('rest')
        elif self.phase == 'hold':
            if self.kind == 'red':
                self.large = self.age >= C.RED_SHORT_TICKS
                self.switch('windup' if self.large else 'flight')
            elif self.kind in ('dismantle', 'cleave', 'cursed_fist'):
                self.large = self.age >= 21
                self.switch('windup')
        return self.phase != before

    def advance(self, eye=None, look=None):
        if self.phase == 'done':
            return False
        previous_phase = self.phase
        self.previous = self.pos[:]
        self.age += 1
        self.total += 1
        if self.kind == 'blue':
            if self.phase == 'hold' and not self.released and eye is not None and look is not None:
                self.tether = min(12.0, self.tether + 0.10)
                self.direction = turn_towards(self.direction, look, math.radians(2.2) * 2 / 3)
                goal = add(eye, mul(self.direction, self.tether))
                self.velocity = limit(sub(goal, self.pos), 0.64)
                self.pos = add(self.pos, self.velocity)
            elif self.coast > 0:
                self.pos = add(self.pos, self.velocity)
                self.velocity = mul(self.velocity, 0.70)
                self.coast -= 1
            if self.total >= 300 and self.phase != 'end':
                self.switch('end')
        elif self.phase == 'hold':
            if eye is not None and look is not None:
                self.direction = unit(look)
                self.pos = add(eye, mul(self.direction, 1.8 if self.kind == 'red' else 2.5))
                self.origin = self.pos[:]
            self.charge = min(1.0, self.age / float(C.RED_CHARGE_TICKS if self.kind == 'red' else 48))
            if self.kind == 'red' and self.age >= C.RED_CHARGE_TICKS:
                self.large, self.released = True, True
                self.switch('windup')
            elif self.kind == 'fuga' and self.age >= 48:
                self.switch('flight')
            elif self.kind in ('dismantle', 'cleave', 'cursed_fist') and self.age >= 36:
                self.large, self.released = True, True
                self.switch('windup')
        elif self.phase == 'windup' and self.age >= (6 if self.kind == 'red' else 4):
            # Commit the slash at the release frame; moving the mouse in the pose still aims.
            if look is not None:
                self.direction = unit(look)
            self.origin = self.pos[:]
            self.switch('beam' if self.kind == 'red' else 'strike')
        elif self.phase == 'flight':
            distance = min(self.speed, self.reach - self.travelled)
            self.pos = add(self.pos, mul(self.direction, distance))
            self.travelled += distance
            if self.travelled >= self.reach:
                self.switch('burst' if self.kind == 'fuga' else 'end')
        elif self.phase == 'beam' and self.age >= C.RED_CANNON_TICKS:
            self.switch('end')
        elif self.phase == 'strike' and self.age >= (24 if self.large else 6):
            self.switch('end')
        elif self.phase == 'burst' and self.age >= 36:
            self.switch('end')
        if self.phase == 'end' and self.age >= 15:
            self.switch('done')
        return previous_phase != self.phase

    def snapshot(self):
        return {'id': self.sid, 'owner': self.owner, 'dimension': self.dimension, 'kind': self.kind,
                'pos': self.pos[:], 'direction': self.direction[:], 'phase': self.phase, 'age': self.age,
                'total': self.total, 'radius': self.radius, 'reach': self.reach, 'travelled': self.travelled,
                'speed': self.speed, 'large': self.large, 'charge': self.charge, 'velocity': self.velocity[:], 'consume': self.consume,
                'revision': self.revision, 'released': self.released}


def technique_position(state, seconds):
    if state['phase'] == 'flight':
        distance = min(max(0.0, seconds) * 30 * state['speed'], max(0.0, state['reach'] - state['travelled']))
        return add(state['pos'], mul(state['direction'], distance))
    return list(state['pos'])

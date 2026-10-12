# -*- coding: utf-8 -*-
"""Engine-independent timelines and swept hit geometry, all in 30 Hz ticks."""
import math


def add(a, b):
    return [a[i] + b[i] for i in range(3)]


def sub(a, b):
    return [a[i] - b[i] for i in range(3)]


def mul(a, t):
    return [x * t for x in a]


def dot(a, b):
    return sum(a[i] * b[i] for i in range(3))


def length(a):
    return math.sqrt(dot(a, a))


def unit(a):
    n = length(a)
    return mul(a, 1.0/n) if n > 0.00001 else [0.0, 0.0, 1.0]


def cross(a, b):
    return [a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]]


def segment_distance(point, start, end):
    delta = sub(end, start)
    t = max(0.0, min(1.0, dot(sub(point, start), delta) / max(0.000001, dot(delta, delta))))
    return length(sub(point, add(start, mul(delta, t))))


class Purple(object):
    def __init__(self, sid, owner, dimension, pos, direction):
        self.sid, self.owner, self.dimension = sid, owner, dimension
        self.pos, self.direction = list(pos), unit(direction)
        self.phase, self.age, self.total = "hold", 0, 0
        self.radius, self.travelled, self.reach = 0.0, 0.0, 50.0
        self.hit = set()
        self.large = False
        self.amplified = False
        self.input_token = None
        self.revision = 0

    def release(self):
        # Long hold commits atomically. Late release packets cannot revert it.
        if self.phase == "hold":
            if self.age >= 30:
                self.commit_large()
                return True
            self.phase, self.age = "small", 0
            self.radius = 0.12
            self.revision += 1
            return True
        return False

    def commit_large(self):
        if self.phase != "hold":
            return False
        self.phase, self.age, self.large = "fusion", 0, True
        self.radius, self.reach = (5.0 if self.amplified else 3.0), 150.0
        self.revision += 1
        return True

    def advance(self):
        if self.phase == "done":
            return False
        self.age += 1
        self.total += 1
        before = self.phase
        if self.phase == "hold" and self.age >= 30:
            self.commit_large()
        elif self.phase == "fusion" and self.age >= 90:
            self.phase, self.age = "large", 0
        elif self.phase in ("small", "large") and self.age >= 30:
            self.phase, self.age = "flight", 0
            self.radius = (5.0 if self.amplified else 3.0) if self.large else 1.0
        elif self.phase == "flight":
            distance = min(3.0, self.reach - self.travelled)
            self.pos = add(self.pos, mul(self.direction, distance))
            self.travelled += distance
            if self.travelled >= self.reach:
                self.phase, self.age = "end", 0
        elif self.phase == "end" and self.age >= 12:
            self.phase = "done"
        if self.phase == "small":
            self.radius = 0.12 + 0.88 * min(1.0, self.age / 30.0)
        if before != self.phase and before != "hold":
            self.revision += 1
        return before != self.phase

    def snapshot(self):
        return {"id":self.sid,"owner":self.owner,"dimension":self.dimension,
                "pos":self.pos[:],"direction":self.direction[:],"phase":self.phase,
                "age":self.age,"total":self.total,"radius":self.radius,
                "reach":self.reach,"travelled":self.travelled,"large":self.large,"amplified":self.amplified,
                "revision":self.revision,"inputToken":self.input_token}


def visible_position(snapshot, elapsed):
    """Extrapolate only within the latest server phase. Never replay a phase."""
    if snapshot["phase"] != "flight":
        return list(snapshot["pos"])
    d = min(max(0.0, elapsed) * 90.0, max(0.0, snapshot["reach"] - snapshot["travelled"]))
    return add(snapshot["pos"], mul(snapshot["direction"], d))

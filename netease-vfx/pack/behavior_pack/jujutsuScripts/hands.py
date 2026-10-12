# -*- coding: utf-8 -*-
"""Layer skill poses onto the player's own skin; never replace player.entity.json."""
import mod.client.extraClientApi as api
from . import config as C


PREFIX = "query.mod.jujutsuvfx_hand_"
FIELDS = ("active", "charge", "both", "release", "void", "red", "fuga", "slash", "punch", "slim")


class SkillHands(object):
    def __init__(self, factory, level):
        self.factory = factory
        self.game = factory.CreateGame(level)
        self.bound, self.hidden, self.values = set(), set(), {}
        query = factory.CreateQueryVariable(level)
        for field in FIELDS:
            query.Register(PREFIX + field, 0.0)

    def bind(self, player):
        render = self.factory.CreateActorRender(player)
        results = []
        # Extend the native first-person visibility rule, as in the official
        # custom-weapon example. It reads these queries directly; only the
        # third-person pose needs an appended animation here.
        for view in ("third_person",):
            key = "jujutsuvfx_hands_" + view
            results.append(render.AddPlayerAnimation(key, "animation.jujutsuvfx.hands." + view))
            condition = ("" if view == "first_person" else "!") + "variable.is_first_person"
            # Share the native skin's bone palette.
            results.append(render.AddPlayerScriptAnimate(key, condition + " && " + PREFIX + "active > 0.01", True))
        results.append(render.RebuildPlayerRender())
        if not any(result is False for result in results):
            self.bound.add(player)
        elif player not in self.values:
            print("[JujutsuNetease] hand binding not ready, retrying: " + str(player))

    def update(self, packet):
        players = set(api.GetPlayerList())
        for player in players:
            if not self.game.IsEntityAlive(player):
                continue
            if player not in self.bound:
                self.bind(player)
            item = self.factory.CreateItem(player).GetCarriedItem() or {}
            skill = item.get("newItemName", item.get("itemName")) in C.SKILLS
            target = dict((field, 0.0) for field in FIELDS)
            render = self.factory.CreateActorRender(player)
            target["slim"] = 1.0 if render.GetModelStyle() in ("slim", "alex") else 0.0
            for p in packet.get("purples", []):
                if p["owner"] != player:
                    continue
                phase = p["phase"]
                if phase in ("hold", "small", "fusion", "large"):
                    target["active"] = 1.0
                    target["charge"] = 0.3 if phase == "hold" else 1.0
                    target["both"] = 1.0 if p["large"] else 0.0
                elif phase == "flight" and p["age"] < 10:
                    target["active"] = 1.0
                    target["release"] = max(target["release"], 1.0 - p["age"] / 10.0)
                    target["both"] = max(target["both"], 1.0 if p["large"] else 0.0)
            for d in packet.get("domains", []):
                if d["owner"] == player and d["age"] < 40:
                    target.update(active=1.0, charge=1.0, both=1.0, void=1.0)
            for p in packet.get("techniques", []):
                if p["owner"] != player:
                    continue
                held = p["phase"] in ("hold", "windup")
                launched = p["phase"] in ("flight", "beam", "strike") and p["age"] < 12
                if not held and not launched:
                    continue
                kind = p["kind"]
                target.update(active=1.0, charge=1.0 if held else 0.0,
                              release=0.0 if held else max(0, 1.0-p["age"]/12.0))
                target["both"] = 1.0 if kind == "fuga" or p.get("large") else 0.0
                target["red"] = 1.0 if kind == "red" else 0.0
                target["fuga"] = 1.0 if kind == "fuga" else 0.0
                target["slash"] = 1.0 if kind in ("dismantle", "cleave") else 0.0
                target["punch"] = 1.0 if kind == "cursed_fist" else 0.0
            for cut in packet.get("slashes", []):
                if cut["owner"] == player and cut["kind"] in ("fist", "black_flash") and cut["age"] < 9:
                    target.update(active=1.0, punch=1.0, release=max(0,1.0-cut["age"]/9.0))
            if skill:
                target["active"] = 1.0
            value = self.values.setdefault(player, dict((field, 0.0) for field in FIELDS))
            query = self.factory.CreateQueryVariable(player)
            for field in FIELDS:
                value[field] += (target[field] - value[field]) * (1.0 if field in ("active", "slim") else 0.3)
                if abs(value[field] - target[field]) < 0.001:
                    value[field] = target[field]
                query.Set(PREFIX + field, value[field])
            # Item-local netease:show_in_hand hides only the skill's flat icon.
            # Avoid a player-wide visibility override while rendering bare arms.
        for player in list(self.bound - players):
            self.bound.discard(player)
            self.hidden.discard(player)
            self.values.pop(player, None)

    def cleanup(self):
        for player in self.bound:
            if not self.game.IsEntityAlive(player):
                continue
            render = self.factory.CreateActorRender(player)
            if player in self.hidden:
                render.SetPlayerItemInHandVisible(True, 0)
            query = self.factory.CreateQueryVariable(player)
            for field in FIELDS:
                query.Set(PREFIX + field, 0.0)
            render.RemovePlayerRenderController("controller.render.jujutsuvfx.skill_hands")
            for view in ("first_person", "third_person"):
                render.AddPlayerScriptAnimate("jujutsuvfx_hands_"+view, "0.0", True)
            render.RebuildPlayerRender()
        self.bound.clear()
        self.hidden.clear()
        self.values.clear()

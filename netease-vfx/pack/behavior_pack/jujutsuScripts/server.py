# -*- coding: utf-8 -*-
from __future__ import division
import math
import random
import uuid
from collections import deque
import mod.server.extraServerApi as api
from . import config as C
from .core import Purple, add, sub, mul, unit, length, segment_distance
from .combat import TechniqueManager

ServerSystem = api.GetServerSystemCls()
try:
    STRING_TYPES=(basestring,)
except NameError:
    STRING_TYPES=(str,)


class JujutsuServer(ServerSystem):
    def __init__(self, namespace, system_name):
        ServerSystem.__init__(self, namespace, system_name)
        self.level = api.GetLevelId()
        self.factory = api.GetEngineCompFactory()
        self.game = self.factory.CreateGame(self.level)
        self.purples, self.domains, self.frozen = {}, {}, {}
        self.cats, self.cooldowns = set(), {}
        self.last_use = {}
        self.purple_inputs = {}
        self.snapshot_serial = 0
        self.blocks, self.block_keys = deque(), set()
        self.tick, self.serial = 0, 0
        self.session = uuid.uuid4().hex[:12]
        self.break_blocks = self.factory.CreateModAttr(self.level).GetAttr("jujutsuvfx_break_blocks", C.BREAK_BLOCKS)
        self.tech = TechniqueManager(self)
        events = {"OnScriptTickServer":self.on_tick, "ServerItemTryUseEvent":self.on_use,
            "ServerItemUseOnEvent":self.on_use_block, "ItemReleaseUsingServerEvent":self.on_release,
            "ClientLoadAddonsFinishServerEvent":self.on_join, "AddEntityServerEvent":self.on_entity,
            "EntityRemoveEvent":self.on_remove, "DelServerPlayerEvent":self.on_leave,
            "ServerChatEvent":self.on_chat, "DamageEvent":self.on_damage,
            "PlayerAttackEntityEvent":self.on_attack,"PlayerDropItemServerEvent":self.on_drop}
        for event, callback in events.items():
            self.ListenForEvent(api.GetEngineNamespace(), api.GetEngineSystemName(), event, self, callback)
        self.ListenForEvent(C.NAMESPACE,"Client","PurpleInput",self,self.on_purple_input)
        print("[JujutsuNetease] server 0.2.1 loaded; tokened purple input; 30 Hz")

    def pos(self, entity):
        return self.factory.CreatePos(entity).GetFootPos()

    def dimension(self, entity):
        return self.factory.CreateDimension(entity).GetEntityDimensionId()

    def direction(self, entity):
        rot = self.factory.CreateRot(entity).GetRot()
        return list(api.GetDirFromRot(rot)) if rot else [0,0,1]

    def alive(self, entity):
        return bool(entity and entity != "-1" and self.game.IsEntityAlive(entity))

    def sid(self):
        self.serial += 1
        return self.session + "-" + str(self.serial)

    def charging(self, owner):
        return next((p for p in self.purples.values() if p.owner==owner and p.phase not in ("flight","end","done")), None)

    def item_name(self, item):
        return (item or {}).get("newItemName", (item or {}).get("itemName", ""))

    def is_player(self, entity):
        return entity in api.GetPlayerList()

    def eligible_target(self, entity, caster):
        if entity == caster or not self.alive(entity):
            return False
        if hasattr(self.factory,"CreateEngineType"):
            kind=self.factory.CreateEngineType(entity).GetEngineTypeStr()
            if kind in ("jujutsuvfx:shrine_model","minecraft:item","minecraft:xp_orb"):
                return False
        return not (self.is_player(entity) and self.game.GetPlayerGameType(entity) in (1, 3))

    def on_use(self, args):
        name, owner = self.item_name(args.get("itemDict")), args.get("playerId")
        if name not in C.SKILLS + (C.AMPLIFIER,) or not owner:
            return
        if self.tick-self.last_use.get((owner,name),-100)<3:
            return
        self.last_use[(owner,name)]=self.tick
        if owner in self.frozen:
            args["cancel"] = True
            return
        if name == C.PURPLE:
            if owner in getattr(self,"purple_inputs",{}):
                return
            self.start_purple(owner)
        elif name == C.VOID:
            args["cancel"] = True
            self.start_domain(owner)
        else:
            if name not in C.CHARGE_SKILLS:
                args["cancel"] = True
            self.tech.use(owner,name)

    def on_use_block(self, args):
        self.on_use({"playerId":args.get("entityId"),"itemDict":args.get("itemDict")})
        # Ranged weapons need the native use lifecycle even when aiming at a
        # block; cancelling it here can remove the matching release event.
        name = self.item_name(args.get("itemDict"))
        if name in C.SKILLS + (C.AMPLIFIER,) and name not in C.CHARGE_SKILLS:
            args["ret"] = True

    def on_release(self, args):
        name = self.item_name(args.get("itemDict"))
        if name != C.PURPLE:
            if name in C.CHARGE_SKILLS:
                args["cancel"] = True
                self.tech.release(args.get("playerId"),name)
            return
        args["cancel"] = True
        p = self.charging(args.get("playerId"))
        if p and p.input_token:
            # Tokened client stop/release wins. A delayed native release from
            # the preceding cast must not shorten a freshly started cast.
            return
        if p and p.phase == "hold":
            self.follow_caster(p)
            p.release()
            self.update_freeze()
            self.sync()

    def on_damage(self,args):
        # ModSDK resolves the bound callback name on its supplied instance.
        self.tech.on_damage(args)

    def on_attack(self,args):
        self.tech.on_attack(args)

    def on_drop(self,args):
        self.tech.on_drop(args)

    def on_purple_input(self,args):
        """One numbered press owns one cast; duplicate/late releases are inert."""
        owner, action = args.get("playerId"), args.get("action")
        token, serial = args.get("token"), args.get("serial")
        if not isinstance(token,STRING_TYPES) or not token or len(token)>96:
            return
        try:
            serial=int(serial)
        except (TypeError,ValueError,OverflowError):
            return
        if serial<1 or serial>2147483647 or action not in ("start","release","cancel"):
            return
        if not self.is_player(owner) or not self.alive(owner):
            return
        tickets=getattr(self,"purple_inputs",None)
        if tickets is None:
            tickets=self.purple_inputs={}
        ticket=tickets.get(owner)
        if action=="start":
            source=token.rsplit(":",1)[0]
            if ticket and (ticket["token"]==token or ticket["source"]==source and serial<=ticket["serial"]):
                return
            tickets[owner]={"token":token,"source":source,"serial":serial,"sid":None,"closed":False}
            item=self.factory.CreateItem(owner).GetPlayerItem(api.GetMinecraftEnum().ItemPosType.CARRIED,0)
            if self.item_name(item)!=C.PURPLE:
                tickets[owner]["closed"]=True
                return
            p=self.charging(owner)
            if p is None:
                if not self.start_purple(owner):
                    tickets[owner]["closed"]=True
                    return
                p=self.charging(owner)
            if p is None or p.input_token is not None:
                tickets[owner]["closed"]=True
                return
            p.input_token=token
            tickets[owner]["sid"]=p.sid
            self.sync()
            return
        if not ticket or ticket["token"]!=token or ticket["closed"]:
            return
        ticket["closed"]=True
        p=self.purples.get(ticket["sid"])
        if not p or p.input_token!=token or p.phase!="hold":
            return
        if action=="cancel":
            del self.purples[p.sid]
        else:
            self.follow_caster(p)
            p.release()
        self.update_freeze()
        self.sync()

    def follow_caster(self, p):
        pos = self.pos(p.owner)
        if pos:
            p.direction = self.direction(p.owner)
            p.pos = add(add(pos, [0,1.4,0]), mul(p.direction, 4.0))

    def start_purple(self, owner, large=None):
        if not self.alive(owner) or owner in self.frozen or self.charging(owner):
            return False
        if hasattr(self,"tech") and self.tech.held(owner):
            return False
        if self.tick < self.cooldowns.get((owner,"purple"), 0):
            return False
        pos = self.pos(owner)
        if not pos:
            return False
        p = Purple(self.sid(), owner, self.dimension(owner), pos, self.direction(owner))
        p.amplified = getattr(self,"tech",None) is not None and self.tech.amplified.get(owner,0)>self.tick
        self.follow_caster(p)
        if large is False:
            p.release()
        elif large is True:
            p.age = 29
        self.purples[p.sid] = p
        self.sync()
        return True

    def start_domain(self, owner, kind="void"):
        if not self.alive(owner) or owner in self.frozen or self.charging(owner):
            return False
        if self.tick < self.cooldowns.get((owner,kind),0) or self.tech.held(owner):
            return False
        pos = self.pos(owner)
        if not pos:
            return False
        sid = self.sid()
        self.domains[sid] = {"id":sid,"owner":owner,"dimension":self.dimension(owner),
            "pos":list(pos),"direction":self.direction(owner),"radius":self.tech.radius,"age":0,
            "duration":self.tech.duration,"kind":kind,"immune":[]}
        self.cooldowns[(owner,kind)] = self.tick + 210
        self.tech.begin_clash(self.domains[sid])
        if kind=="shrine":
            base=add(pos,mul(self.direction(owner),-5.0))
            shrine=self.CreateEngineEntityByTypeStr("jujutsuvfx:shrine_model",tuple(base),self.factory.CreateRot(owner).GetRot(),self.dimension(owner))
            if shrine:
                self.domains[sid]["model"]=shrine
        if owner in self.cats and kind=="void":
            self.factory.CreateModAttr(owner).SetAttr("jujutsuvfx_uncovered", True, True)
        self.update_freeze()
        self.sync()
        return True

    def in_box(self, lo, hi, dim):
        return self.game.GetEntitiesInSquareArea(None, tuple(int(math.floor(v)) for v in lo),
                    tuple(int(math.ceil(v)) for v in hi), dim) or []

    def swept_hit(self, p, before):
        pad = p.radius + 2.0
        lo = [min(before[i],p.pos[i])-pad for i in range(3)]
        hi = [max(before[i],p.pos[i])+pad for i in range(3)]
        for target in self.in_box(lo, hi, p.dimension):
            if target in p.hit or not self.eligible_target(target,p.owner):
                continue
            foot = self.pos(target)
            if not foot or segment_distance(add(foot,[0,0.7,0]), before, p.pos) > p.radius + 0.65:
                continue
            p.hit.add(target)
            damage = (16 if p.amplified and p.large else 12 if p.large else 7) if self.is_player(target) else (200 if p.amplified and p.large else 100 if p.large else 48)
            self.factory.CreateHurt(target).Hurt(damage, "magic", p.owner, None, False)
        if self.break_blocks:
            # Sample the swept volume; each coordinate is visited once while queued.
            for step in range(4):
                center = add(before, mul(sub(p.pos,before), step/3.0))
                r = int(math.ceil(p.radius))
                for x in range(-r,r+1):
                    for y in range(-r,r+1):
                        for z in range(-r,r+1):
                            if x*x+y*y+z*z > p.radius*p.radius:
                                continue
                            block = tuple(int(math.floor(center[i]+(x,y,z)[i])) for i in range(3))
                            key = (p.dimension,block)
                            if key not in self.block_keys:
                                self.block_keys.add(key)
                                self.blocks.append(key)

    def destroy_blocks(self):
        comp = self.factory.CreateBlockInfo(self.level)
        for _ in range(min(C.BLOCKS_PER_TICK,len(self.blocks))):
            dim, pos = self.blocks.popleft()
            self.block_keys.discard((dim,pos))
            if not self.break_blocks:
                continue
            block = comp.GetBlockNew(pos,dim)
            if not block or block.get("name") in ("minecraft:air","minecraft:bedrock","minecraft:barrier",
                "minecraft:command_block","minecraft:chain_command_block","minecraft:repeating_command_block",
                "minecraft:structure_block","minecraft:end_portal","minecraft:end_portal_frame","minecraft:portal"):
                continue
            # Replacing with air creates no item entities. Failure never stops flight.
            comp.SetBlockNew(pos,{"name":"minecraft:air","aux":0},0,dim,False)

    def update_freeze(self):
        desired = set()
        for d in self.domains.values():
            r, center = d["radius"], d["pos"]
            for entity in self.in_box(add(center,[-r]*3),add(center,[r]*3),d["dimension"]):
                if self.eligible_target(entity,d["owner"]):
                    if entity in d.get("immune",[]):
                        continue
                    pos = self.pos(entity)
                    if pos and length(sub(pos,center)) <= r:
                        desired.add(entity)
        domain_targets = set(desired)
        # Committed charge freezes the caster's position, but not the world tick.
        for p in self.purples.values():
            if p.phase in ("small","fusion","large"):
                desired.add(p.owner)
        for entity in desired:
            if entity in self.frozen:
                continue
            pos = self.pos(entity)
            if not pos:
                continue
            # Despite its name, Get/SetBlockControlAi uses True = AI enabled.
            # Player controls are handled separately; this API is mob-only.
            ai = None if self.is_player(entity) else self.factory.CreateControlAi(entity)
            self.frozen[entity] = {"pos":list(pos),"rot":self.factory.CreateRot(entity).GetRot(),
                "motion":self.factory.CreateActorMotion(entity).GetMotion(),
                "ai_enabled":ai.GetBlockControlAi() if ai else None,
                "dimension":self.dimension(entity)}
            if ai and self.frozen[entity]["ai_enabled"]:
                # A caster must continue its own hand/charge animation.
                ai.SetBlockControlAi(False,entity in domain_targets)
        for entity in list(self.frozen):
            if entity not in desired or not self.alive(entity) or self.dimension(entity)!=self.frozen[entity]["dimension"]:
                self.thaw(entity)

    def thaw(self, entity):
        state = self.frozen.pop(entity,None)
        if state and self.alive(entity):
            if not self.is_player(entity) and state["ai_enabled"]:
                self.factory.CreateControlAi(entity).SetBlockControlAi(True,False)
            if state.get("motion"):
                self.set_motion(entity,tuple(state["motion"]))

    def set_motion(self,entity,motion):
        comp = self.factory.CreateActorMotion(entity)
        if self.is_player(entity):
            comp.SetPlayerMotion(motion)
        else:
            comp.SetMotion(motion)

    def pin_frozen(self):
        for entity,state in list(self.frozen.items()):
            if not self.alive(entity) or self.dimension(entity)!=state["dimension"]:
                self.thaw(entity)
                continue
            self.set_motion(entity,(0,0,0))
            self.factory.CreatePos(entity).SetFootPos(tuple(state["pos"]))
            if state.get("rot"):
                self.factory.CreateRot(entity).SetRot(tuple(state["rot"]))

    def cat_ai(self):
        for cat in list(self.cats):
            if not self.alive(cat):
                self.cats.discard(cat)
                continue
            if cat in self.frozen or self.charging(cat):
                continue
            target = self.factory.CreateAction(cat).GetAttackTarget()
            if not self.alive(target):
                continue
            a,b = self.pos(cat),self.pos(target)
            if not a or not b or length(sub(a,b)) > 64:
                continue
            health = self.factory.CreateAttr(cat).GetAttrValue(api.GetMinecraftEnum().AttrType.HEALTH)
            if health is not None and health < 400 and self.tick >= self.cooldowns.get((cat,"void"),0):
                self.start_domain(cat)
            elif length(sub(a,b)) > 6 and self.tick >= self.cooldowns.get((cat,"purple"),0):
                self.factory.CreateRot(cat).SetRot(api.GetRotFromDir(tuple(sub(b,a))))
                self.start_purple(cat, random.random() < 0.4)
                self.cooldowns[(cat,"purple")] = self.tick + 450

    def on_tick(self, args=None):
        # OnScriptTickServer dispatches without an event dictionary in ModSDK.
        self.tick += 1
        if hasattr(self,"tech"):
            self.tech.tick()
            self.tech.tick_domains()
        changed = False
        for sid,p in list(self.purples.items()):
            if not self.alive(p.owner) or self.dimension(p.owner)!=p.dimension:
                del self.purples[sid]
                changed = True
                continue
            if p.phase == "hold":
                if self.is_player(p.owner):
                    item = self.factory.CreateItem(p.owner).GetPlayerItem(api.GetMinecraftEnum().ItemPosType.CARRIED,0)
                    if self.item_name(item)!=C.PURPLE:
                        del self.purples[sid]
                        changed = True
                        continue
                self.follow_caster(p)
            before = p.pos[:]
            changed = p.advance() or changed
            if p.phase == "flight" or p.travelled > 0 and p.phase=="end" and p.age==0:
                self.swept_hit(p,before)
            if p.phase == "flight" and p.age == 0:
                self.cooldowns[(p.owner,"purple")] = max(self.tick+45,self.cooldowns.get((p.owner,"purple"),0))
            if p.phase == "done":
                del self.purples[sid]
        for sid,d in list(self.domains.items()):
            d["age"] += 1
            if d["age"] >= d["duration"] or not self.alive(d["owner"]) or self.dimension(d["owner"])!=d["dimension"]:
                if d.get("model") and self.alive(d["model"]):
                    self.DestroyEntity(d["model"])
                del self.domains[sid]
                changed = True
        if changed or self.tick % 3 == 0:
            self.update_freeze()
        self.pin_frozen()
        if self.tick % 30 == 0:
            self.cat_ai()
        self.destroy_blocks()
        if changed or self.tick % 3 == 0:
            self.sync()

    def sync(self, player=None):
        self.snapshot_serial=getattr(self,"snapshot_serial",0)+1
        packet = {"session":self.session,"tick":self.tick,"sequence":self.snapshot_serial,"purples":[p.snapshot() for p in self.purples.values()],
            "domains":list(self.domains.values()),"frozen":list(self.frozen.keys())}
        if hasattr(self,"tech"):
            packet.update(self.tech.snapshot())
        if player:
            self.NotifyToClient(player,"State",packet)
        else:
            self.BroadcastToAllClient("State",packet)

    def on_join(self,args):
        self.sync(args["playerId"])

    def on_entity(self,args):
        if args.get("engineTypeStr")==C.CAT:
            self.cats.add(args["id"])
            self.cooldowns[(args["id"],"purple")] = self.tick+150

    def on_remove(self,args):
        entity = args.get("id",args.get("entityId"))
        self.cats.discard(entity)
        self.frozen.pop(entity,None)
        self.purple_inputs.pop(entity,None)
        for key in list(self.cooldowns):
            if key[0] == entity:
                del self.cooldowns[key]

    def on_leave(self,args):
        entity=args.get("id",args.get("playerId"))
        self.thaw(entity)
        self.purple_inputs.pop(entity,None)

    def on_chat(self,args):
        message = args.get("message","").strip()
        if not message or message.split()[0] != "!rift":
            return
        args["cancel"] = True
        player = args["playerId"]
        # The debug kit/spawn/destruction controls are restricted to operators.
        if self.factory.CreatePlayer(player).GetPlayerOperation() != 2:
            self.game.SetOneTipMessage(player,"[Jujutsu] Operator permission required.")
            return
        command = message.split()[1:]
        if command == ["kit"]:
            for name in C.SKILLS+(C.AMPLIFIER,):
                self.factory.CreateItem(player).SpawnItemToPlayerInv({"newItemName":name,"newAuxValue":0,"count":1},player)
        elif command == ["cat"]:
            pos = add(self.pos(player),mul(self.direction(player),4))
            cat = self.CreateEngineEntityByTypeStr(C.CAT,tuple(pos),(0,0),self.dimension(player))
            if cat:
                self.cats.add(cat)
        elif command and command[0] == "purple":
            if len(command)>1 and command[1]=="200":
                self.tech.amplified[player]=self.tick+1800
            self.start_purple(player, len(command)>1 and command[1] in ("big","200"))
        elif command == ["void"]:
            self.start_domain(player)
        elif command == ["shrine"]:
            self.start_domain(player,"shrine")
        elif command and command[0] in ("blue","red","fuga","dismantle","cleave","infinity","reverse_technique"):
            self.tech.use(player,"jujutsuvfx:"+command[0])
        elif len(command)==2 and command[0]=="unlimited_blocks" and command[1] in ("on","off"):
            self.tech.unlimited_blocks=command[1]=="on"
            self.factory.CreateModAttr(self.level).SetAttr("jujutsuvfx_unlimited_blocks",self.tech.unlimited_blocks,True)
        elif len(command)==2 and command[0]=="flash" and command[1] in ("on","off"):
            self.tech.flash=command[1]=="on"
        elif len(command)==3 and command[0]=="domain":
            try:
                self.tech.radius=max(4.0,min(50.0,float(command[1])))
                self.tech.duration=int(max(2.0,min(60.0,float(command[2])))*30)
            except ValueError:
                self.game.SetOneTipMessage(player,"!rift domain <radius 4-50> <seconds 2-60>")
        elif len(command)==2 and command[0]=="blocks" and command[1] in ("on","off"):
            self.break_blocks = command[1]=="on"
            self.factory.CreateModAttr(self.level).SetAttr("jujutsuvfx_break_blocks",self.break_blocks,True)
            self.game.SetOneTipMessage(player,"[Jujutsu] Block destruction: "+str(self.break_blocks))
        elif command == ["clear"]:
            self.tech.clear()
            for d in self.domains.values():
                if d.get("model") and self.alive(d["model"]):
                    self.DestroyEntity(d["model"])
            self.purples.clear()
            self.purple_inputs.clear()
            self.domains.clear()
            self.blocks.clear()
            self.block_keys.clear()
            for entity in list(self.frozen):
                self.thaw(entity)
            self.sync()
        else:
            self.game.SetOneTipMessage(player,"!rift kit / cat / purple [big|200] / blue / red / fuga / dismantle / cleave / void / shrine / domain 40 5 / blocks on|off / unlimited_blocks on|off / flash on|off / clear")

    def Destroy(self):
        for entity in list(self.frozen):
            self.thaw(entity)
        self.UnListenAllEvents()

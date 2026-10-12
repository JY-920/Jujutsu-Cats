# -*- coding: utf-8 -*-
from __future__ import division
import math
import time
import mod.client.extraClientApi as api
from . import config as C
from .core import sub, dot, cross, unit, length, visible_position
from .hands import SkillHands
from .rendering import EffectRenderer
from .sound import SoundDirector
from .purple_input import PurpleInput

ClientSystem = api.GetClientSystemCls()


class JujutsuClient(ClientSystem):
    def __init__(self, namespace, system_name):
        ClientSystem.__init__(self,namespace,system_name)
        self.level, self.player = api.GetLevelId(), api.GetLocalPlayerId()
        self.factory = api.GetEngineCompFactory()
        self.game = self.factory.CreateGame(self.level)
        self.camera = self.factory.CreateCamera(self.level)
        self.post = self.factory.CreatePostProcess(self.level)
        self.audio = self.factory.CreateCustomAudio(self.level)
        self.packet, self.received = None, 0.0
        self.ready, self.operation_state = False, None
        self.passes, self.enabled = [], {}
        self.sound_phases, self.loops = {}, {}
        self.cats, self.pose = set(), {}
        self.phase_ages = {}
        self.hands = SkillHands(self.factory, self.level)
        self.renderer = EffectRenderer(self)
        self.sound = SoundDirector(self)
        self.purple_input = PurpleInput(self)
        self.ListenForEvent(C.NAMESPACE,"Server","State",self,self.on_state)
        for event,callback in {"OnLocalPlayerStopLoading":self.initialize,
            "GameRenderTickEvent":self.on_frame,"OnScriptTickClient":self.on_tick,
            "AddEntityClientEvent":self.on_entity,"RemoveEntityClientEvent":self.on_remove,
            "ItemReleaseUsingClientEvent":self.on_release,"UpdatePlayerSkinClientEvent":self.on_skin}.items():
            self.ListenForEvent(api.GetEngineNamespace(),api.GetEngineSystemName(),event,self,callback)
        query = self.factory.CreateQueryVariable(self.level)
        query.Register("query.mod.jujutsuvfx_cast",0.0)
        query.Register("query.mod.jujutsuvfx_uncovered",0.0)
        print("[JujutsuNetease] client 0.2.1 loaded; native shader pipeline")

    def initialize(self,args=None):
        if self.ready:
            return
        self.ready = True
        available = self.post.GetPostProcessOrder() or []
        for name,kind in self.renderer.definitions():
            material = "jujutsuvfx_"+kind
            definition = {"name":name,"enable":False,"paras":[
                {"name":"data"+str(i),"value":[0.0,0.0,0.0,0.0]} for i in range(4)],
                "pass_array":[{"render_target":{"width":1.0,"height":1.0},"material":material,"depth_enable":True}]}
            if name in available or self.post.AddPostProcess(definition):
                self.passes.append(name)
                self.enable(name,False,True)
            else:
                print("[JujutsuNetease] postprocess unavailable: " + name)
        print("[JujutsuNetease] custom postprocess passes: " + str(len(self.passes)))

    def enable(self,name,value,force=False):
        if name in self.passes and (force or self.enabled.get(name)!=value):
            self.post.SetEnableByName(name,value)
            self.enabled[name] = value

    def parameters(self,name,vectors):
        for i,vec in enumerate(vectors):
            self.post.SetParameter(name,"data"+str(i),[float(x) for x in vec])

    def on_release(self,args):
        item = args.get("itemDict") or {}
        if item.get("newItemName",item.get("itemName")) in C.CHARGE_SKILLS:
            args["cancel"] = True

    def on_skin(self,args):
        self.hands.bound.discard(args.get("playerId"))

    def on_state(self,packet):
        if self.packet and packet.get("session")==self.packet.get("session"):
            if "sequence" in packet and "sequence" in self.packet:
                if packet["sequence"] <= self.packet["sequence"]:
                    return
            elif packet["tick"] < self.packet["tick"]:
                return
        if self.packet and packet.get("session")!=self.packet.get("session"):
            self.cleanup()
        self.packet, self.received = packet, time.time()

    def on_entity(self,args):
        if args.get("engineTypeStr")==C.CAT:
            self.cats.add(args["id"])

    def on_remove(self,args):
        entity = args.get("id",args.get("entityId"))
        self.cats.discard(entity)
        self.pose.pop(entity,None)

    def lock_controls(self,locked):
        operation = self.factory.CreateOperation(self.player)
        fields = ("Move","Jump","Drag","Attack")
        if locked and self.operation_state is None:
            self.operation_state = {f:getattr(operation,"IsCan"+f)() for f in fields}
            for f in fields:
                getattr(operation,"SetCan"+f)(False)
        elif not locked and self.operation_state is not None:
            for f,value in self.operation_state.items():
                getattr(operation,"SetCan"+f)(bool(value))
            self.operation_state = None

    def on_tick(self,args=None):
        # OnScriptTickClient dispatches without an event dictionary in ModSDK.
        self.purple_input.tick()
        if not self.ready:
            return
        fresh = self.packet and time.time()-self.received <= 3.0
        self.hands.update(self.packet if fresh else {})
        if not self.packet or time.time()-self.received > 3.0:
            self.cleanup()
            return
        self.lock_controls(self.player in self.packet.get("frozen",[]))
        casting = set(p["owner"] for p in self.packet["purples"] if p["phase"] in ("small","fusion","large"))
        casting.update(d["owner"] for d in self.packet["domains"] if d["age"]<35)
        casting.update(p["owner"] for p in self.packet.get("techniques",[]) if p["phase"] in ("hold","windup"))
        for cat in list(self.cats):
            target = 1.0 if cat in casting else 0.0
            value = self.pose.get(cat,0.0)+(target-self.pose.get(cat,0.0))*0.22
            self.pose[cat] = value
            query = self.factory.CreateQueryVariable(cat)
            query.Set("query.mod.jujutsuvfx_cast",value)
            uncovered = self.factory.CreateModAttr(cat).GetAttr("jujutsuvfx_uncovered",False)
            query.Set("query.mod.jujutsuvfx_uncovered",1.0 if uncovered else 0.0)
        self.update_audio()

    def stop_loop(self,sid):
        music = self.loops.pop(sid,None)
        if music:
            self.audio.StopCustomMusicById(music,0.18)

    def update_audio(self):
        self.sound.update()

    def age(self,state,elapsed):
        key = (state["id"],state.get("phase","domain"))
        value = state["age"]/30.0+elapsed
        value = max(value,self.phase_ages.get(key,0.0))
        self.phase_ages[key] = value
        return value

    def on_frame(self,args=None):
        if self.ready and self.packet:
            self.renderer.render()

    def cleanup(self):
        self.purple_input.reset()
        self.lock_controls(False)
        for name in self.passes:
            self.enable(name,False)
        for sid in list(self.loops):
            self.stop_loop(sid)
        self.packet = None
        self.sound_phases.clear()
        self.phase_ages.clear()
        self.sound.clear()
        for cat in self.cats:
            if self.game.IsEntityAlive(cat):
                self.factory.CreateQueryVariable(cat).Set("query.mod.jujutsuvfx_cast",0.0)
        self.pose.clear()

    def Destroy(self):
        self.cleanup()
        self.hands.cleanup()
        self.purple_input.destroy()
        self.UnListenAllEvents()

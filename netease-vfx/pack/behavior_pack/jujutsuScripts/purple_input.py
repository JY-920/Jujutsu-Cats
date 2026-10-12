# -*- coding: utf-8 -*-
"""Translate the native ranged-item lifecycle into idempotent cast inputs."""
import uuid
import mod.client.extraClientApi as api
from . import config as C


class PurpleInput(object):
    def __init__(self,client):
        self.client=client
        self.source=uuid.uuid4().hex[:12]
        self.serial=0
        self.active=None
        # The supplied listener instance must own the callback method; ModSDK
        # dispatches by method name rather than retaining the bound function.
        self.listeners=[]
        for event,callback in (("ClientItemTryUseEvent",self.on_start),
            ("StartUsingItemClientEvent",self.on_start),
            ("ItemReleaseUsingClientEvent",self.on_release),
            ("StopUsingItemClientEvent",self.on_stop)):
            client.ListenForEvent(api.GetEngineNamespace(),api.GetEngineSystemName(),event,self,callback)
            self.listeners.append((event,callback))

    def name(self,item):
        return (item or {}).get("newItemName",(item or {}).get("itemName",""))

    def held_name(self):
        return self.name(self.client.factory.CreateItem(self.client.player).GetCarriedItem())

    def local(self,args):
        return args.get("playerId",self.client.player)==self.client.player

    def send(self,action):
        if self.active:
            self.client.NotifyToServer("PurpleInput",{"playerId":self.client.player,
                "action":action,"token":self.active,"serial":self.serial})

    def on_start(self,args):
        if not self.local(args) or self.name(args.get("itemDict"))!=C.PURPLE or self.active:
            return
        # Never cancel native start. It supplies matching stop events on mouse
        # and touch controls, and the release handler cancels the vanilla shot.
        self.serial+=1
        self.active=self.source+":"+str(self.serial)
        self.send("start")

    def finish(self,cancel=False):
        if self.active:
            self.send("cancel" if cancel else "release")
            self.active=None

    def on_release(self,args):
        if not self.local(args) or self.name(args.get("itemDict"))!=C.PURPLE:
            return
        args["cancel"]=True
        self.finish(self.held_name()!=C.PURPLE)

    def on_stop(self,args):
        if not self.local(args):
            return
        if self.active and self.name(args.get("itemDict")) in ("",C.PURPLE):
            # Some touch/switch paths produce StopUsing without ReleaseUsing.
            self.finish(self.held_name()!=C.PURPLE)

    def tick(self):
        if self.active and self.held_name()!=C.PURPLE:
            self.finish(True)

    def reset(self):
        self.finish(True)

    def destroy(self):
        self.reset()
        for event,callback in self.listeners:
            self.client.UnListenForEvent(api.GetEngineNamespace(),api.GetEngineSystemName(),event,self,callback)
        self.listeners=[]

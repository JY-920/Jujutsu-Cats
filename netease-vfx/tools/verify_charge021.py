"""Purple lifecycle regressions; mocks events/network, not the game renderer."""
import copy
import importlib
import sys
import types
import unittest
from collections import deque
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'pack/behavior_pack'))
for name in ('mod','mod.server','mod.server.extraServerApi','mod.client','mod.client.extraClientApi'):
    sys.modules.setdefault(name,types.ModuleType(name))
server_api=sys.modules['mod.server.extraServerApi']
server_api.GetServerSystemCls=lambda:object
server_api.GetPlayerList=lambda:['player','other']
server_api.GetMinecraftEnum=lambda:types.SimpleNamespace(ItemPosType=types.SimpleNamespace(CARRIED=1))
server_api.GetDirFromRot=lambda rot:[0,0,1]
client_api=sys.modules['mod.client.extraClientApi']
client_api.GetEngineNamespace=lambda:'Minecraft'
client_api.GetEngineSystemName=lambda:'Engine'
from jujutsuScripts.server import JujutsuServer
from jujutsuScripts.core import Purple
from jujutsuScripts.purple_input import PurpleInput
from jujutsuScripts import config as C


class ServerTests(unittest.TestCase):
    def setUp(self):
        s=self.s=JujutsuServer.__new__(JujutsuServer)
        self.entities={p:dict(pos=[0,0,0],motion=[0,0,0],rot=[0,0]) for p in ('player','other')}
        self.carried={'player':C.PURPLE,'other':C.PURPLE}
        self.packets=[]
        s.factory=types.SimpleNamespace(
            CreateItem=lambda e:types.SimpleNamespace(GetPlayerItem=lambda *a:{'newItemName':self.carried[e]}),
            CreatePos=lambda e:types.SimpleNamespace(GetFootPos=lambda:self.entities[e]['pos'],SetFootPos=lambda v:self.entities[e].update(pos=list(v))),
            CreateRot=lambda e:types.SimpleNamespace(GetRot=lambda:self.entities[e]['rot'],SetRot=lambda v:self.entities[e].update(rot=list(v))),
            CreateDimension=lambda e:types.SimpleNamespace(GetEntityDimensionId=lambda:0),
            CreateActorMotion=lambda e:types.SimpleNamespace(GetMotion=lambda:self.entities[e]['motion'],SetPlayerMotion=lambda v:self.entities[e].update(motion=list(v))),
            CreateBlockInfo=lambda e:types.SimpleNamespace())
        s.game=types.SimpleNamespace(IsEntityAlive=lambda e:e in self.entities,GetEntitiesInSquareArea=lambda *a:[])
        s.purples={};s.purple_inputs={};s.domains={};s.frozen={};s.cooldowns={};s.last_use={}
        s.blocks=deque();s.block_keys=set();s.break_blocks=False;s.tick=0;s.serial=0;s.snapshot_serial=0
        s.session='session';s.level='world';s.cats=set()
        s.tech=types.SimpleNamespace(held=lambda p:False,amplified={},tick=lambda:None,tick_domains=lambda:None,snapshot=lambda:{})
        s.BroadcastToAllClient=lambda e,p:self.packets.append(copy.deepcopy(p))
    def packet(self,action,serial=1,owner='player'):
        return dict(playerId=owner,action=action,token='input:'+str(serial),serial=serial)
    def step(self,ticks):
        for _ in range(ticks):self.s.on_tick()
    def test_block_target_keeps_ranged_item_lifecycle(self):
        args=dict(entityId='player',itemDict={'newItemName':C.PURPLE})
        self.s.on_use_block(args)
        self.assertFalse(args.get('ret',False))
        self.assertEqual(len(self.s.purples),1)
    def test_native_then_custom_start_is_one_instance(self):
        self.s.on_use(dict(playerId='player',itemDict={'newItemName':C.PURPLE}))
        sid=self.s.charging('player').sid
        self.s.on_purple_input(self.packet('start'))
        self.assertEqual(self.s.charging('player').sid,sid)
        self.assertEqual(len(self.s.purples),1)
    def test_duplicate_start_stop_and_release_cannot_replay(self):
        self.s.on_purple_input(self.packet('start'));p=self.s.charging('player')
        self.s.on_purple_input(self.packet('start'));self.step(5)
        self.s.on_purple_input(self.packet('release'));self.step(7)
        self.s.on_purple_input(self.packet('release'));self.s.on_purple_input(self.packet('start'))
        self.assertEqual((p.phase,p.age),('small',7));self.assertEqual(len(self.s.purples),1)
        self.step(23);self.assertEqual((p.phase,p.radius,p.reach),('flight',1.0,50.0))
    def test_short_press_freezes_on_release_and_launches_automatically(self):
        self.s.on_purple_input(self.packet('start'));self.step(29)
        self.assertNotIn('player',self.s.frozen)
        self.s.on_purple_input(self.packet('release'))
        self.assertIn('player',self.s.frozen)
        p=self.s.charging('player');self.step(30)
        self.assertEqual(p.phase,'flight');self.assertNotIn('player',self.s.frozen)
    def test_one_second_commits_large_and_ignores_release_or_switch_cancel(self):
        self.s.on_purple_input(self.packet('start'));p=self.s.charging('player');self.step(30)
        self.assertEqual(p.phase,'fusion')
        self.s.on_purple_input(self.packet('cancel'));self.step(90)
        self.assertEqual(p.phase,'large');self.step(30)
        self.assertEqual((p.phase,p.radius),('flight',3.0))
    def test_switch_before_commit_cancels_without_small_shot(self):
        self.s.on_purple_input(self.packet('start'));self.step(10)
        self.s.on_purple_input(self.packet('cancel'))
        self.assertFalse(self.s.purples);self.assertFalse(self.s.frozen)
    def test_late_old_releases_do_not_end_next_press(self):
        self.s.on_purple_input(self.packet('start'));first=self.s.charging('player')
        self.s.on_purple_input(self.packet('release'));self.step(30)
        self.s.cooldowns.clear()
        self.s.on_purple_input(self.packet('start',2));second=self.s.charging('player')
        self.s.on_purple_input(self.packet('release',1))
        self.s.on_release(dict(playerId='player',itemDict={'newItemName':C.PURPLE}))
        self.assertIsNot(first,second);self.assertEqual(second.phase,'hold')
        self.assertEqual(len(self.s.purples),2)
        self.s.on_purple_input(self.packet('release',2));self.assertEqual(second.phase,'small')
    def test_two_players_have_independent_holds(self):
        self.s.on_purple_input(self.packet('start'));self.s.on_purple_input(self.packet('start',owner='other'))
        self.s.on_purple_input(self.packet('release'))
        self.assertEqual(self.s.charging('player').phase,'small')
        self.assertEqual(self.s.charging('other').phase,'hold')
    def test_same_tick_snapshots_have_strictly_increasing_sequence(self):
        self.s.on_purple_input(self.packet('start'));self.s.on_purple_input(self.packet('release'))
        self.assertEqual(len(set(p['tick'] for p in self.packets)),1)
        sequences=[p['sequence'] for p in self.packets]
        self.assertEqual(sequences,sorted(set(sequences)))
    def test_invalid_input_cannot_create_cast(self):
        for data in (self.packet('start',0),dict(playerId='player',action='start',token={},serial=1),self.packet('oops')):
            self.s.on_purple_input(data)
        self.carried['player']='minecraft:stick';self.s.on_purple_input(self.packet('start'))
        self.assertFalse(self.s.purples)
    def test_native_repeated_use_cannot_start_automatic_second_cast(self):
        self.s.on_purple_input(self.packet('start'));self.s.on_purple_input(self.packet('release'));self.step(95)
        self.assertFalse(self.s.purples)
        self.s.on_use(dict(playerId='player',itemDict={'newItemName':C.PURPLE}))
        self.assertFalse(self.s.purples)
        self.s.on_purple_input(self.packet('start',2));self.assertEqual(len(self.s.purples),1)
    def test_forwarded_engine_callbacks_resolve_on_bound_server(self):
        calls=[]
        for name in ('on_damage','on_attack','on_drop'):
            setattr(self.s.tech,name,lambda args,n=name:calls.append((n,args)))
            callback=getattr(self.s,name)
            getattr(self.s,callback.__name__)({'test':1})
        self.assertEqual([name for name,_ in calls],['on_damage','on_attack','on_drop'])


class InputTests(unittest.TestCase):
    def setUp(self):
        self.sent=[];self.listeners=[];self.name=C.PURPLE
        client=types.SimpleNamespace(player='player',factory=types.SimpleNamespace(CreateItem=lambda p:types.SimpleNamespace(GetCarriedItem=lambda:{'newItemName':self.name})),
            NotifyToServer=lambda name,p:self.sent.append(p),ListenForEvent=lambda ns,sys,event,obj,cb:self.listeners.append((event,obj,cb)),
            UnListenForEvent=lambda *a:None)
        self.input=PurpleInput(client)
    def args(self):return dict(playerId='player',itemDict={'newItemName':C.PURPLE})
    def test_try_and_start_are_deduped_and_stop_is_release_fallback(self):
        self.input.on_start(self.args());self.input.on_start(self.args());self.input.on_stop(self.args());self.input.on_release(self.args())
        self.assertEqual([p['action'] for p in self.sent],['start','release'])
        self.assertEqual(self.sent[0]['token'],self.sent[1]['token'])
    def test_switch_cancels_with_the_original_token(self):
        self.input.on_start(self.args());self.name='minecraft:stick';self.input.tick();self.input.on_stop(self.args())
        self.assertEqual([p['action'] for p in self.sent],['start','cancel'])
    def test_each_click_uses_a_different_token(self):
        self.input.on_start(self.args());self.input.on_release(self.args());self.input.on_start(self.args())
        self.assertNotEqual(self.sent[0]['token'],self.sent[2]['token'])
    def test_listener_instance_owns_callback_name(self):
        for _,obj,callback in self.listeners:
            self.assertEqual(getattr(obj,callback.__name__),callback)


class BoundaryTests(unittest.TestCase):
    def test_release_at_the_commit_boundary_never_turns_small(self):
        p=Purple('p','player',0,[0,0,0],[0,0,1]);p.age=30;p.release()
        self.assertEqual((p.phase,p.large),('fusion',True))
    def test_completed_timeline_is_immutable(self):
        p=Purple('p','player',0,[0,0,0],[0,0,1]);p.release()
        for _ in range(80):p.advance()
        before=p.snapshot();p.release();p.advance()
        self.assertEqual(p.snapshot(),before)


if __name__=='__main__':
    unittest.main(verbosity=2)

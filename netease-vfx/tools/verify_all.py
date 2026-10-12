"""Behavioral regressions for the expanded NetEase port (not an engine emulator)."""
import importlib
import json
import math
from pathlib import Path
import sys
import types
import unittest
from collections import deque
import verify

for name in ('mod','mod.server','mod.server.extraServerApi','mod.client','mod.client.extraClientApi'):
    sys.modules.setdefault(name,types.ModuleType(name))
api=sys.modules['mod.server.extraServerApi'];api.GetServerSystemCls=lambda:verify.DummyBase
api.GetMinecraftEnum=lambda:types.SimpleNamespace(AttrType=types.SimpleNamespace(HEALTH=0),ItemPosType=types.SimpleNamespace(INVENTORY=0,CARRIED=1))
api.GetPlayerList=lambda:['player']
sys.modules['mod.client.extraClientApi'].GetClientSystemCls=lambda:verify.DummyBase
from jujutsuScripts.techniques import Technique, technique_position, turn_towards
from jujutsuScripts.core import Purple, length, sub
from jujutsuScripts.combat import TechniqueManager
from jujutsuScripts.rendering import EffectRenderer
from jujutsuScripts import config as C


class World:
    def __init__(self):
        self.level='world';self.tick=0;self.serial=0;self.break_blocks=False
        self.entities={'player':{'pos':[0,1,0],'health':20},'mob':{'pos':[0,1,5],'health':800}}
        self.domains={};self.frozen={};self.cooldowns={};self.blocks=deque();self.block_keys=set()
        self.inventory=[None]*36;self.carried=C.BLUE;self.damages=[];self.deleted=[];self.motion={}
        self.factory=types.SimpleNamespace(CreateModAttr=lambda _:types.SimpleNamespace(GetAttr=lambda key,default:default,SetAttr=lambda *a:None),
            CreateAttr=self.attr,CreateHurt=lambda e:types.SimpleNamespace(Hurt=lambda n,cause,owner,child,knock,tag:self.hurt(e,n,tag)),
            CreateItem=lambda e:types.SimpleNamespace(GetPlayerItem=lambda *a:{'newItemName':self.carried},
                GetPlayerAllItems=lambda *a:self.inventory,SpawnItemToPlayerInv=self.give,GetDroppedItem=lambda *a:{'newItemName':C.FRAGMENT}),
            CreateBlockInfo=lambda _:types.SimpleNamespace(GetBlockNew=lambda *a:{'name':'minecraft:air'}),
            CreateEffect=lambda e:types.SimpleNamespace(AddEffectToEntity=lambda *a:True))
        self.game=types.SimpleNamespace(SetOneTipMessage=lambda *a:None)
        self.tech=TechniqueManager(self)
    def attr(self,e):
        return types.SimpleNamespace(GetAttrMaxValue=lambda _:20 if e=='player' else 800,
            GetAttrValue=lambda _:self.entities[e]['health'],SetAttrValue=lambda _,n:self.entities[e].update(health=n),SetEntityOnFire=lambda *a:True)
    def hurt(self,e,n,tag):
        args={'entityId':e,'damage':n,'cause':'magic','customTag':tag};self.tech.on_damage(args)
        self.entities[e]['health']-=args['damage'];self.damages.append((e,args['damage'],tag));return args['damage']>0
    def give(self,data,owner,slot):self.inventory[slot]=data.copy();return True
    def sid(self):self.serial+=1;return 's'+str(self.serial)
    def alive(self,e):return e in self.entities and self.entities[e]['health']>0
    def pos(self,e):return self.entities[e]['pos'][:]
    def dimension(self,e):return 0
    def direction(self,e):return [0,0,1]
    def is_player(self,e):return e=='player'
    def eligible_target(self,e,owner):return e!=owner and self.alive(e)
    def in_box(self,lo,hi,dim):return [e for e in self.entities if all(lo[i]<=self.pos(e)[i]<=hi[i] for i in range(3))]
    def charging(self,e):return False
    def sync(self):pass
    def item_name(self,i):return (i or {}).get('newItemName','')
    def set_motion(self,e,m):self.motion[e]=m
    def DestroyEntity(self,e):self.deleted.append(e);self.entities.pop(e,None)
    def step(self,n=1):
        for _ in range(n):self.tick+=1;self.tech.tick();self.tech.tick_domains()


class TechniqueTests(unittest.TestCase):
    def make(self,kind):return Technique('a','player',0,kind,[0,2,3.2],[0,0,1])
    def test_blue_tether_does_not_depend_on_mouse_angle(self):
        p=self.make('blue')
        for _ in range(180):p.advance([0,2,0],[0,0,1])
        self.assertAlmostEqual(length(sub(p.pos,[0,2,0])),12)
        for _ in range(45):p.advance([0,2,0],[1,0,0])
        self.assertLessEqual(length(sub(p.pos,[0,2,0])),12.001)
    def test_blue_release_coasts_then_stops_and_expires(self):
        p=self.make('blue');p.advance([0,2,0],[0,0,1]);before=p.pos[:];p.release();p.advance()
        self.assertGreater(length(sub(before,p.pos)),0)
        for _ in range(15):p.advance()
        stopped=p.pos[:]
        for _ in range(20):p.advance()
        self.assertEqual(stopped,p.pos)
        for _ in range(320):p.advance()
        self.assertEqual(p.phase,'done')
    def test_red_tap_and_automatic_cannon(self):
        for hold,expected in ((38,'flight'),(39,'windup')):
            p=self.make('red')
            for _ in range(hold):p.advance()
            p.release();self.assertEqual(p.phase,expected)
        p=self.make('red')
        for _ in range(C.RED_CHARGE_TICKS+6):p.advance()
        self.assertEqual(p.phase,'beam')
        for _ in range(C.RED_CANNON_TICKS):p.advance()
        self.assertEqual(p.phase,'end')
    def test_red_does_not_exceed_32_blocks(self):
        p=self.make('red');p.release()
        for _ in range(90):p.advance()
        self.assertEqual(p.travelled,32);self.assertEqual(p.phase,'done')
    def test_amplified_purple_is_large_before_flight(self):
        p=Purple('a','player',0,[0,2,4],[0,0,1]);p.amplified=True
        for _ in range(120):p.advance()
        self.assertEqual(p.phase,'large');self.assertEqual(p.radius,5)
        for _ in range(30):p.advance()
        self.assertEqual((p.radius,p.reach),(5,150))
    def test_long_slash_is_one_burst_not_continuous_casting(self):
        p=self.make('cleave');seen=[]
        for _ in range(100):
            p.advance()
            if p.phase=='strike' and p.age%4==0:seen.append(p.age)
        self.assertEqual(seen,[0,4,8,12,16,20]);self.assertEqual(p.phase,'done')
    def test_turn_towards_handles_opposite_aim(self):
        vector=turn_towards([0,0,1],[0,0,-1],.05)
        self.assertAlmostEqual(length(vector),1)
        self.assertAlmostEqual(math.acos(vector[2]),.05)
    def test_extrapolated_projectile_range_is_clamped(self):
        p=self.make('red');p.release();p.advance()
        self.assertLessEqual(technique_position(p.snapshot(),10)[2]-p.origin[2],32.00001)


class CombatTests(unittest.TestCase):
    def setUp(self):self.w=World()
    def test_fragments_fill_empty_slots_once_per_domain(self):
        self.w.inventory[0]={'newItemName':'minecraft:diamond','count':64}
        d={'id':'field'};self.w.tech.information(d,'player')
        self.assertEqual(self.w.inventory[0]['count'],64)
        self.assertTrue(all(i['count']==1 for i in self.w.inventory[1:]))
        self.w.inventory[2]=None;self.w.tech.information(d,'player')
        self.assertIsNone(self.w.inventory[2])
        self.w.tech.on_drop({'itemEntityId':'dropped'});self.assertIn('dropped',self.w.deleted)
    def test_black_flash_only_after_two_landed_hits(self):
        for _ in range(3):self.w.tech.punch('player','mob')
        self.assertEqual([p[1] for p in self.w.damages],[8,8,20])
        self.assertIn('mob',self.w.motion)
    def test_infinity_and_interruptible_recovery(self):
        self.w.tech.use('player',C.INFINITY);self.w.hurt('player',8,'test');self.assertEqual(self.w.entities['player']['health'],20)
        self.w.tech.use('player',C.INFINITY);self.w.entities['player']['health']=2
        self.w.tech.use('player',C.RECOVERY);self.w.step(30);self.assertEqual(self.w.entities['player']['health'],12)
        self.w.hurt('player',2,'test');self.w.step(30);self.assertEqual(self.w.entities['player']['health'],10)
    def test_collision_consumes_both_once_and_explodes_once(self):
        b=Technique('blue','player',0,'blue',[0,2,6],[0,0,1]);b.release()
        r=Technique('red','player',0,'red',[0,2,6],[0,0,1]);r.release()
        self.w.tech.active={'blue':b,'red':r};self.w.tech.collide_blue_red();self.w.tech.collide_blue_red()
        self.assertEqual(len(self.w.tech.bursts),1);self.assertTrue(b.snapshot()['consume'])
        self.w.step(150)
        self.assertEqual(len([d for d in self.w.damages if d[2].endswith('unlimited_purple')]),1)
        self.assertFalse(self.w.tech.bursts);self.assertFalse(self.w.blocks)
    def test_multiple_instances_and_break_configuration(self):
        self.w.tech.start('player','blue');self.w.tech.release('player',C.BLUE)
        self.w.tech.start('player','blue');self.assertEqual(len(self.w.tech.active),2)
        self.w.tech.carve_sphere([0,1,0],3,0);self.assertFalse(self.w.tech.carving)
        self.w.break_blocks=True;self.w.tech.carve_sphere([0,1,0],3,0);self.w.tech.drain_carving();self.assertTrue(self.w.blocks)
    def test_clash_five_seconds_then_removes_loser_and_model(self):
        self.w.entities['other']={'pos':[5,1,0],'health':800};self.w.entities['model']={'pos':[0,0,0],'health':1}
        a={'id':'void','kind':'void','owner':'player','dimension':0,'pos':[0,1,0],'direction':[0,0,1],'radius':40,'duration':150,'age':0}
        b=dict(a,id='shrine',kind='shrine',owner='other',model='model',pos=[5,1,0])
        self.w.domains={'void':a,'shrine':b};self.w.tech.begin_clash(b)
        self.assertEqual(set(a['immune']),{'player','other'})
        self.w.tick=149;self.w.tech.tick_domains();self.assertEqual(len(self.w.domains),2)
        self.w.tick=150;self.w.tech.tick_domains();self.assertEqual(len(self.w.domains),1)
        self.w.tech.tick_domains();self.assertEqual(next(iter(self.w.domains.values()))['immune'],[])
        if 'shrine' not in self.w.domains:self.assertIn('model',self.w.deleted)
    def test_snapshot_round_trips_json(self):
        self.w.tech.start('player','red');self.w.step(5)
        p=json.loads(json.dumps(self.w.tech.snapshot()));self.assertEqual(p['techniques'][0]['age'],5)


class RenderTests(unittest.TestCase):
    def test_every_kind_has_finite_uniforms_and_cleans_up(self):
        from jujutsuScripts.client import JujutsuClient
        c=JujutsuClient.__new__(JujutsuClient);c.ready=True;c.player='player';c.received=0;c.phase_ages={};c.passes=[];c.enabled={}
        c.camera=types.SimpleNamespace(GetPosition=lambda:(0,2,0),GetForward=lambda:(0,0,1),GetFov=lambda:70)
        c.game=types.SimpleNamespace(GetScreenSize=lambda:(1920,1080),GetCurrentDimension=lambda:0)
        c.factory=types.SimpleNamespace(CreatePos=lambda e:types.SimpleNamespace(GetFootPos=lambda:[0,1,0]))
        c.post=types.SimpleNamespace(SetEnableByName=lambda n,v:c.enabled.update({n:v}),SetParameter=lambda *a:None)
        c.renderer=EffectRenderer(c);c.passes=[p[0] for p in c.renderer.definitions()]
        captured={}
        def values(name,vectors):
            self.assertEqual(len(vectors),4)
            self.assertTrue(all(len(v)==4 and all(math.isfinite(x) for x in v) for v in vectors));captured[name]=vectors
        c.parameters=values
        for kind in ('blue','red','fuga'):
            for phase in ('hold','flight','beam','burst','end'):
                p=Technique('t','player',0,kind,[0,2,8],[0,0,1]);p.phase=phase
                c.packet={'tick':30,'purples':[],'domains':[],'techniques':[p.snapshot()]};c.on_frame()
        c.packet={'tick':30,'purples':[],'domains':[],'techniques':[]};c.on_frame()
        self.assertTrue(captured);self.assertFalse(any(c.enabled.values()))


if __name__=='__main__':
    suite=unittest.TestSuite(unittest.defaultTestLoader.loadTestsFromTestCase(t) for t in (TechniqueTests,CombatTests,RenderTests))
    result=unittest.TextTestRunner(verbosity=2).run(suite)
    raise SystemExit(not result.wasSuccessful())

"""Deterministic state/cleanup tests plus addon and SDK reference validation."""
import ast
import copy
import importlib
import json
from pathlib import Path
import re
import sys
import types
import unittest
import warnings
from collections import deque

ROOT = Path(__file__).resolve().parents[1]
BP = ROOT / "pack/behavior_pack"
RP = ROOT / "pack/resource_pack"
sys.path.insert(0,str(BP))
from jujutsuScripts.core import Purple, segment_distance, visible_position
from jujutsuScripts.config import SKILLS


class TimelineTests(unittest.TestCase):
    def make(self):
        return Purple("p1","caster",0,[0,2,0],[0,0,1])

    def test_short_release_charges_one_second(self):
        p = self.make()
        for _ in range(29): p.advance()
        p.release()
        for _ in range(29): p.advance()
        self.assertEqual(p.phase,"small")
        p.advance()
        self.assertEqual((p.phase,p.radius,p.reach),("flight",1.0,50.0))

    def test_one_second_commits_three_second_fusion_and_one_second_charge(self):
        p = self.make()
        for _ in range(30): p.advance()
        self.assertEqual(p.phase,"fusion")
        p.release()
        self.assertEqual(p.phase,"fusion")
        for _ in range(90): p.advance()
        self.assertEqual(p.phase,"large")
        for _ in range(30): p.advance()
        self.assertEqual((p.phase,p.radius,p.reach),("flight",3.0,150.0))

    def test_range_never_overshoots(self):
        for large,reach in ((False,50.0),(True,150.0)):
            p = self.make()
            if large:
                for _ in range(30): p.advance()
            else: p.release()
            for _ in range(240): p.advance()
            self.assertEqual(p.phase,"done")
            self.assertAlmostEqual(p.pos[2],reach)

    def test_swept_collision_catches_between_ticks(self):
        self.assertAlmostEqual(segment_distance([0.5,0,1.5],[0,0,0],[0,0,3]),0.5)
        self.assertGreater(segment_distance([0,0,4],[0,0,0],[0,0,3]),0.9)

    def test_client_extrapolation_is_bounded_by_range(self):
        p = self.make();p.release()
        for _ in range(46):p.advance()
        snapshot = p.snapshot()
        self.assertLessEqual(visible_position(snapshot,10)[2],50)
        snapshot["phase"]="fusion"
        self.assertEqual(visible_position(snapshot,10),snapshot["pos"])


class DummyBase(object):
    pass


class FixtureFactory(object):
    def __init__(self,entities): self.entities=entities
    def CreateControlAi(self,entity):
        d=self.entities[entity]
        def set_ai(value,freeze=False):d.update(ai=value,animation_frozen=freeze);return True
        return types.SimpleNamespace(GetBlockControlAi=lambda:d["ai"],SetBlockControlAi=set_ai)
    def CreatePos(self,entity):
        d=self.entities[entity]
        return types.SimpleNamespace(GetFootPos=lambda:d["pos"],SetFootPos=lambda pos:d.update(pos=list(pos)))
    def CreateRot(self,entity):
        d=self.entities[entity]
        return types.SimpleNamespace(GetRot=lambda:d["rot"],SetRot=lambda rot:d.update(rot=list(rot)))
    def CreateActorMotion(self,entity):
        d=self.entities[entity]
        return types.SimpleNamespace(GetMotion=lambda:d["motion"],SetMotion=lambda motion:d.update(motion=list(motion)),SetPlayerMotion=lambda motion:d.update(motion=list(motion)))
    def CreateDimension(self,entity):
        return types.SimpleNamespace(GetEntityDimensionId=lambda:self.entities[entity]["dimension"])
    def CreateBlockInfo(self,entity):
        return types.SimpleNamespace()


class FreezeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        for name in ("mod","mod.server","mod.server.extraServerApi"):
            sys.modules[name]=types.ModuleType(name)
        api=sys.modules["mod.server.extraServerApi"]
        api.GetServerSystemCls=lambda:DummyBase
        api.GetPlayerList=lambda:[]
        cls.Server=importlib.import_module("jujutsuScripts.server").JujutsuServer

    def setUp(self):
        self.entities={name:{"pos":[x,0,0],"rot":[12,34],"motion":[0.2,0.3,0.4],"ai":True,"alive":True,"dimension":0}
                       for name,x in (("caster",0),("other_caster",3),("victim",5))}
        self.s=self.Server.__new__(self.Server)
        self.s.factory=FixtureFactory(self.entities)
        self.s.game=types.SimpleNamespace(IsEntityAlive=lambda e: self.entities.get(e,{}).get("alive",False),
            GetEntitiesInSquareArea=lambda _,a,b,d:[k for k,v in self.entities.items() if v["dimension"]==d])
        self.s.purples={};self.s.frozen={};self.s.domains={}
        self.d={"id":"d1","owner":"caster","dimension":0,"pos":[0,0,0],"radius":40}

    def tick_fixture(self):
        self.s.level="level";self.s.tick=0;self.s.session="test"
        self.s.cats=set();self.s.cooldowns={}
        self.s.blocks=deque();self.s.block_keys=set();self.s.break_blocks=False
        self.packets=[]
        self.s.BroadcastToAllClient=lambda event,packet:self.packets.append(copy.deepcopy(packet))

    def test_engine_zero_argument_ticks_launch_and_move_small_purple(self):
        # Regression for the real 3.8 traceback: on_tick(self, args) never ran.
        self.tick_fixture()
        p=Purple("p1","caster",0,[0,2,0],[0,0,1]);p.release()
        self.s.purples[p.sid]=p
        for _ in range(30):self.s.on_tick()
        self.assertEqual(p.phase,"flight")
        self.assertNotIn("caster",self.s.frozen)
        self.s.on_tick()
        self.assertEqual(p.pos,[0,2,3])
        for _ in range(35):self.s.on_tick()
        self.assertEqual(p.pos,[0,2,50])
        self.assertFalse(self.s.purples)
        self.assertFalse(self.s.frozen)
        self.assertTrue(any(x["phase"]=="flight" and x["pos"][2]>0 for d in self.packets for x in d["purples"]))
        self.assertEqual(self.packets[-1]["purples"],[])

    def test_engine_zero_argument_ticks_expire_domain_and_restore_targets(self):
        self.tick_fixture()
        self.s.domains={"d1":dict(self.d,age=0,duration=150)}
        for _ in range(149):self.s.on_tick()
        self.assertIn("victim",self.s.frozen)
        self.s.on_tick()
        self.assertFalse(self.s.domains)
        self.assertFalse(self.s.frozen)
        self.assertTrue(self.entities["victim"]["ai"])
        self.assertEqual(self.packets[-1]["frozen"],[])

    def test_overlap_keeps_victim_frozen_until_last_domain_ends(self):
        self.s.domains={"d1":self.d,"d2":dict(self.d,id="d2",owner="other_caster")}
        self.s.update_freeze();self.s.pin_frozen()
        self.assertFalse(self.entities["victim"]["ai"])
        del self.s.domains["d1"];self.s.update_freeze()
        self.assertIn("victim",self.s.frozen)
        self.s.domains.clear();self.s.update_freeze()
        self.assertTrue(self.entities["victim"]["ai"])
        self.assertEqual(self.entities["victim"]["motion"],[0.2,0.3,0.4])

    def test_preexisting_blocked_ai_is_restored(self):
        self.entities["victim"]["ai"]=False
        self.s.domains={"d1":self.d};self.s.update_freeze()
        self.s.domains.clear();self.s.update_freeze()
        self.assertFalse(self.entities["victim"]["ai"])

    def test_charging_cat_keeps_its_cast_animation_running(self):
        p=Purple("p","caster",0,[0,2,0],[0,0,1]);p.release()
        self.s.purples={p.sid:p};self.s.update_freeze()
        self.assertFalse(self.entities["caster"]["ai"])
        self.assertFalse(self.entities["caster"]["animation_frozen"])
        self.s.purples.clear();self.s.update_freeze()
        self.assertTrue(self.entities["caster"]["ai"])

    def test_caster_immune_to_own_field_and_dimension_exit_restores(self):
        self.s.domains={"d1":self.d};self.s.update_freeze()
        self.assertNotIn("caster",self.s.frozen)
        self.entities["victim"]["dimension"]=1
        self.s.update_freeze()
        self.assertNotIn("victim",self.s.frozen)
        self.assertTrue(self.entities["victim"]["ai"])

    def test_late_entry_and_departure(self):
        self.entities["victim"]["pos"]=[45,0,0]
        self.s.domains={"d1":self.d};self.s.update_freeze()
        self.assertNotIn("victim",self.s.frozen)
        self.entities["victim"]["pos"]=[39,0,0];self.s.update_freeze()
        self.assertIn("victim",self.s.frozen)
        self.s.domains.clear();self.s.update_freeze()
        self.assertFalse(self.s.frozen)


class ClientCallbackTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        for name in ("mod","mod.client","mod.client.extraClientApi"):
            sys.modules.setdefault(name,types.ModuleType(name))
        api=sys.modules["mod.client.extraClientApi"]
        api.GetClientSystemCls=lambda:DummyBase
        cls.Client=importlib.import_module("jujutsuScripts.client").JujutsuClient

    def test_client_tick_without_payload_updates_controls_audio_and_hands(self):
        import time
        c=self.Client.__new__(self.Client)
        c.ready=True;c.player="local";c.received=time.time()
        c.packet={"purples":[],"domains":[],"frozen":["local"]};c.cats=set()
        called=[]
        c.purple_input=types.SimpleNamespace(tick=lambda:called.append("input"))
        c.hands=types.SimpleNamespace(update=lambda packet:called.append("hands"))
        c.lock_controls=lambda frozen:called.append(("locked",frozen))
        c.update_audio=lambda:called.append("audio")
        c.on_tick()
        self.assertEqual(called,["input","hands",("locked",True),"audio"])

    def test_expired_packets_release_controls_but_keep_held_hand(self):
        c=self.Client.__new__(self.Client)
        c.ready=True;c.received=0;c.packet={"purples":[],"domains":[]}
        called=[]
        c.purple_input=types.SimpleNamespace(tick=lambda:called.append("input"))
        c.hands=types.SimpleNamespace(update=lambda packet:called.append(packet))
        c.cleanup=lambda:called.append("cleanup")
        c.on_tick()
        self.assertEqual(called,["input",{},"cleanup"])

    def test_input_lifecycle_runs_before_render_initialization(self):
        c=self.Client.__new__(self.Client);c.ready=False;called=[]
        c.purple_input=types.SimpleNamespace(tick=lambda:called.append("input"))
        c.on_tick()
        self.assertEqual(called,["input"])

    def test_same_tick_old_hold_snapshot_cannot_replay_after_release(self):
        c=self.Client.__new__(self.Client);c.packet=None;c.received=0
        hold={"session":"world","tick":12,"sequence":8,
            "purples":[{"id":"p","phase":"hold","age":10}]}
        released=dict(hold,sequence=9,purples=[{"id":"p","phase":"small","age":0}])
        c.on_state(hold);c.on_state(released)
        c.received=123.0
        c.on_state(hold)
        self.assertIs(c.packet,released)
        self.assertEqual(c.received,123.0)
        c.on_state(dict(released,purples=[{"id":"p","phase":"hold","age":10}]))
        self.assertIs(c.packet,released)
        self.assertEqual(c.received,123.0)

    def test_legacy_snapshot_tick_fallback_rejects_old_state(self):
        c=self.Client.__new__(self.Client)
        c.packet={"session":"world","tick":12,"purples":[]};c.received=123.0
        old=dict(c.packet,tick=11)
        c.on_state(old)
        self.assertEqual(c.packet["tick"],12)
        self.assertEqual(c.received,123.0)

    def test_new_server_session_accepts_restarted_sequence_after_cleanup(self):
        c=self.Client.__new__(self.Client)
        c.packet={"session":"old","tick":100,"sequence":99};called=[]
        c.cleanup=lambda:called.append("cleanup")
        packet={"session":"new","tick":1,"sequence":1}
        c.on_state(packet)
        self.assertIs(c.packet,packet)
        self.assertEqual(called,["cleanup"])

    def test_render_callback_accepts_empty_event(self):
        c=self.Client.__new__(self.Client);c.ready=False
        c.on_frame()

    def test_skill_hand_restores_item_and_retracts_after_release(self):
        from jujutsuScripts.hands import SkillHands, PREFIX
        api=sys.modules["mod.client.extraClientApi"]
        api.GetPlayerList=lambda:["local"]
        item={"newItemName":"jujutsuvfx:purple"};values={};visibility=[];bound=[]
        render=types.SimpleNamespace(
            AddPlayerRenderController=lambda *a:bound.append(a),
            AddPlayerAnimation=lambda *a:True,AddPlayerAnimationIntoState=lambda *a:True,
            AddPlayerScriptAnimate=lambda *a:True,AddPlayerGeometry=lambda *a:True,
            AddPlayerRenderMaterial=lambda *a:True,GetModelStyle=lambda:'classic',
            RebuildPlayerRender=lambda:True,RemovePlayerRenderController=lambda *a:True,
            SetPlayerItemInHandVisible=lambda value,mode:visibility.append((value,mode)))
        query=types.SimpleNamespace(Register=lambda *a:True,Set=lambda key,val:values.update({key:val}))
        factory=types.SimpleNamespace(CreateGame=lambda _:types.SimpleNamespace(IsEntityAlive=lambda p:True),
            CreateActorRender=lambda _:render,CreateQueryVariable=lambda _:query,
            CreateItem=lambda _:types.SimpleNamespace(GetCarriedItem=lambda:item))
        hands=SkillHands(factory,"level")
        packet={"purples":[dict(owner="local",phase="large",large=True,age=0)],"domains":[]}
        for _ in range(15):hands.update(packet)
        self.assertGreater(values[PREFIX+"charge"],0.99)
        self.assertGreater(values[PREFIX+"both"],0.99)
        self.assertEqual(len(bound),0)  # FP is patched at the native controller.
        self.assertEqual(visibility,[])  # Item-local resource, no player-wide hide.
        old=values[PREFIX+"charge"];hands.update({})
        self.assertGreater(values[PREFIX+"charge"],0)
        self.assertLess(values[PREFIX+"charge"],old)
        item["newItemName"]="minecraft:diamond_sword"
        for _ in range(25):hands.update({})
        self.assertEqual(visibility,[])
        self.assertEqual(values[PREFIX+"active"],0)
        hands.cleanup()


def validate_pack():
    warnings.filterwarnings("ignore",category=DeprecationWarning)
    from lib2to3.pgen2.driver import Driver
    from lib2to3 import pygram,pytree
    grammar=Driver(pygram.python_grammar,convert=pytree.convert)
    sources=list(BP.rglob("*.py"))
    for p in sources:
        text=p.read_text("utf-8")
        ast.parse(text)
        grammar.parse_string(text if text.endswith("\n") else text+"\n")
        # Python 2 grammar accepts no f-strings or type annotations; reject newer
        # Python AST constructs explicitly since lib2to3 also knows some Python 3.
        tree=ast.parse(text)
        for node in ast.walk(tree):
            assert not isinstance(node,(ast.JoinedStr,ast.AnnAssign,ast.AsyncFunctionDef,ast.NamedExpr))
    objects={str(p.relative_to(ROOT/"pack")):json.loads(p.read_text("utf-8")) for p in (ROOT/"pack").rglob("*.json")}
    controller_files = {}
    for p in (RP/"render_controllers").glob("*.json"):
        for name in json.loads(p.read_text("utf-8"))["render_controllers"]:
            assert name not in controller_files,("Duplicate render controller",name,p,controller_files.get(name))
            controller_files[name] = p
    player_controllers = RP/"render_controllers/player.render_controllers.json"
    for suffix in ("", "_bloom"):
        name = "controller.render.player.first_person"+suffix
        assert controller_files.get(name)==player_controllers,("Missing native FP override",name)
    for identifier in SKILLS:
        item = json.loads((RP/("netease_items_res/"+identifier.split(":",1)[1]+".json")).read_text("utf-8"))
        assert item["minecraft:item"]["components"].get("netease:show_in_hand")=={"value":False},identifier
    for kind in ("behavior","resource"):
        folder=BP if kind=="behavior" else RP
        manifest=json.loads((folder/"manifest.json").read_text("utf-8"))
        world=json.loads((ROOT/"pack"/("world_"+kind+"_packs.json")).read_text("utf-8"))
        assert world[0]["pack_id"]==manifest["header"]["uuid"]
    geometries={}
    mesh_count=0
    cat_mesh_count=0
    for p in (RP/"models/entity").glob("*.json"):
        geometry=json.loads(p.read_text("utf-8"))["minecraft:geometry"][0]
        names={b["name"] for b in geometry["bones"]}
        assert len(names)==len(geometry["bones"]),"Duplicate bone"
        for bone in geometry["bones"]:
            assert "parent" not in bone or bone["parent"] in names
            if "poly_mesh" in bone:
                mesh_count+=1
                if p.name.startswith('gojo_cat_'):cat_mesh_count+=1
                mesh=bone["poly_mesh"]
                for poly in mesh["polys"]:
                    assert len(poly)==4
                    for vi,ni,ui in poly:
                        assert vi<len(mesh["positions"]) and ni<len(mesh["normals"]) and ui<len(mesh["uvs"])
        geometries[geometry["description"]["identifier"]]=names
    assert cat_mesh_count==40,("Artist meshes lost",cat_mesh_count)
    assert len(geometries)==7 and mesh_count==42
    # Ensure code refers only to methods present in the official SDK snapshot.
    sdk=ROOT/"build/reference/sdk/mod"
    methods=set()
    for p in sdk.rglob("*.py"):
        methods.update(re.findall(r"def\s+(\w+)\s*\(",p.read_text("utf-8")))
    assert methods,"Official SDK snapshot missing"
    for p in sources:
        called=set(re.findall(r"\.((?:Get|Set|Create|Is|Add|Play|Stop|Notify|Broadcast|Listen|UnListen|Register|Spawn|Hurt|Destroy)[A-Z]\w*)\s*\(",p.read_text("utf-8")))
        assert not called-methods,(p,called-methods)
    print("PASS Python 2-compatible syntax; %d JSON files; 40 cat meshes + 2 shrine meshes; 7 geometries; SDK method names"%len(objects))

if __name__=="__main__":
    validate_pack()
    suite=unittest.TestSuite(unittest.defaultTestLoader.loadTestsFromTestCase(t) for t in (TimelineTests,FreezeTests,ClientCallbackTests))
    result=unittest.TextTestRunner(verbosity=2).run(suite)
    raise SystemExit(0 if result.wasSuccessful() else 1)

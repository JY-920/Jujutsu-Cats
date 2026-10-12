"""Build the NetEase addon from local authored assets; no Studio UI required."""
import argparse
import copy
import hashlib
import json
import math
import re
import shutil
import sys
import uuid
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "pack"
BP, RP = PACK / "behavior_pack", PACK / "resource_pack"
ASSETS = ROOT.parent / "neoforge-vfx/src/main/resources/assets/jujutsuvfx"
VERSION = [0, 2, 1]
NAMESPACE = uuid.UUID("245901aa-e080-4243-a4ca-868fa01e30f3")

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

def uid(name):
    return str(uuid.uuid5(NAMESPACE, name))

def copy_asset(source, dest):
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, dest)

def pivot(v):
    return [-v[0], v[1], v[2]]

def rotation(v):
    return [-v[0], -v[1], v[2]]

def model():
    """Same axis/UV conversion as Blockbench's Bedrock exporter.

    Independent geometries retain both source atlas resolutions and emissive eyes.
    No resampling, atlas repainting or reusing a vanilla cat mesh.
    """
    data = json.loads((ASSETS / "models/entity/gojo_cat.bbmodel").read_text("utf-8"))
    groups = {g["uuid"]: g for g in data["groups"]}
    cubes = {g["uuid"]: g for g in data["elements"]}
    names = {g["uuid"]: re.sub(r"[^a-zA-Z0-9_]", "_", g["name"]) for g in data["groups"]}
    # Cloth and hair bones have distinct source names. Keep them for authored animation.
    assert len(set(names.values())) == len(names)
    base = []
    def walk(node, parent=None):
        if isinstance(node, str):
            return
        g = groups[node["uuid"]]
        b = {"name": names[g["uuid"]], "pivot": pivot(g["origin"]), "rotation": rotation(g.get("rotation", [0, 0, 0])), "cubes": []}
        if parent:
            b["parent"] = parent
        for child in node.get("children", []):
            if not isinstance(child, str):
                continue
            c = cubes[child]
            if c.get("visibility", True) is False or c.get("export", True) is False:
                continue
            if c.get("type") == "mesh":
                continue
            for material in (0, 1):
                uv = {}
                for face, f in c.get("faces", {}).items():
                    if f.get("texture") != material:
                        continue
                    u, v, u1, v1 = f["uv"]
                    if face in ("up", "down"):
                        u, v, u1, v1 = u1, v1, u, v
                    uv[face] = {"uv": [u, v], "uv_size": [u1-u, v1-v]}
                if not uv:
                    continue
                out = {"origin": [-c["to"][0], c["from"][1], c["from"][2]],
                       "size": [c["to"][i]-c["from"][i] for i in range(3)],
                       "pivot": pivot(c["origin"]), "rotation": rotation(c.get("rotation", [0,0,0])),
                       "uv": uv, "_material": material}
                if c.get("inflate"):
                    out["inflate"] = c["inflate"]
                b["cubes"].append(out)
        if g["name"] == "head":
            for u in (5, 6, 8, 9):
                right = 2.5 - (u-5)
                b["cubes"].append({"origin": [-right, 9, -12.008], "size": [1,1,0],
                                    "uv": {"north": {"uv": [u,6], "uv_size": [1,1]}}, "_material": 2})
        base.append(b)
        for child in node.get("children", []):
            if not isinstance(child,str) or cubes[child].get("type") != "mesh":
                continue
            mesh = cubes[child]
            if not mesh.get("visibility",True) or not mesh.get("export",True):
                continue
            for material in (0,1):
                poly={"normalized_uvs":True,"positions":[],"normals":[],"uvs":[],"polys":[]}
                for face in mesh["faces"].values():
                    if face.get("texture") != material or len(face.get("vertices",[])) < 3:
                        continue
                    vertices = face["vertices"]
                    positions=[mesh["vertices"][key] for key in vertices]
                    a=[positions[1][i]-positions[0][i] for i in range(3)]
                    bvec=[positions[2][i]-positions[0][i] for i in range(3)]
                    normal=[a[1]*bvec[2]-a[2]*bvec[1],a[2]*bvec[0]-a[0]*bvec[2],a[0]*bvec[1]-a[1]*bvec[0]]
                    norm=math.sqrt(sum(v*v for v in normal)) or 1
                    normal=pivot([v/norm for v in normal])
                    ni=len(poly["normals"]);poly["normals"].append(normal)
                    indices=[]
                    for key,local in zip(vertices,positions):
                        vi=len(poly["positions"]);ui=len(poly["uvs"])
                        poly["positions"].append(pivot([local[i]+mesh["origin"][i] for i in range(3)]))
                        u,v=face["uv"][key]
                        poly["uvs"].append([u/64.0,1.0-v/(32.0 if material==0 else 64.0)])
                        indices.append([vi,ni,ui])
                    if len(indices)==4:
                        poly["polys"].append(indices)
                    else:
                        for i in range(1,len(indices)-1):
                            poly["polys"].append([indices[0],indices[i],indices[i+1],indices[i+1]])
                if poly["polys"]:
                    base.append({"name":"mesh_"+child.replace("-", "")+"_"+str(material),"parent":b["name"],
                        "pivot":pivot(mesh["origin"]),"rotation":rotation(mesh.get("rotation",[0,0,0])),
                        "cubes":[],"poly_mesh":poly,"_material":material})
        for child in node.get("children", []):
            if isinstance(child, dict):
                walk(child, b["name"])
    for root in data["outliner"]:
        walk(root)
    for material, suffix, height in ((0,"body",32),(1,"details",64),(2,"eyes",32)):
        bones = copy.deepcopy(base)
        bones = [b for b in bones if b.get("_material",material)==material]
        for bone in bones:
            bone.pop("_material",None)
            bone["cubes"] = [c for c in bone["cubes"] if c.pop("_material") == material]
        write(RP / ("models/entity/gojo_cat_" + suffix + ".geo.json"), {
            "format_version": "1.12.0", "minecraft:geometry": [{
                "description": {"identifier": "geometry.jujutsuvfx.gojo_cat."+suffix,
                    "texture_width":64, "texture_height":height,
                    "visible_bounds_width":4,"visible_bounds_height":4,"visible_bounds_offset":[0,1,0]},
                "bones":bones}]})
    # Import the first authored clip with actual keyframes (the eye-cover lift).
    clip = next(a for a in data["animations"] if any(x.get("keyframes") for x in a["animators"].values()))
    tracks = {}
    for bone_id, animator in clip["animators"].items():
        if bone_id not in names:
            continue
        channels = {}
        for k in animator.get("keyframes", []):
            channel = k["channel"]
            if channel not in ("position", "rotation", "scale"):
                continue
            v = [float(k["data_points"][0].get(axis, 1 if channel=="scale" else 0)) for axis in "xyz"]
            v = pivot(v) if channel=="position" else rotation(v) if channel=="rotation" else v
            channels.setdefault(channel, {})[str(k["time"])] = v
        if channels:
            tracks[names[bone_id]] = channels
    animations = {"animation.jujutsuvfx.blindfold": {
        "loop":"hold_on_last_frame", "animation_length":clip["length"], "bones":tracks}}
    animations["animation.jujutsuvfx.walk"] = {"loop":True, "bones":{
        name:{"rotation":["math.cos(query.modified_distance_moved * 38.17 + %s) * 28 * math.min(query.modified_move_speed, 1)" % phase,0,0]}
        for name,phase in (("left_front_leg",0),("right_front_leg",180),("left_hind_leg",180),("right_hind_leg",0))}}
    animations["animation.jujutsuvfx.idle"] = {"loop":True, "bones":{
        "head":{"rotation":["math.clamp(query.target_x_rotation, -25, 25)","math.clamp(query.target_y_rotation, -45, 45)",0]},
        "tail1":{"rotation":[0,"math.sin(query.life_time * 100) * 5",0]},
        "tail2":{"rotation":[0,"math.sin(query.life_time * 100 - 30) * 8",0]}}}
    # Blend the entire rooted hierarchy so the tail cannot disconnect during casting.
    animations["animation.jujutsuvfx.cast"] = {"loop":True, "bones":{
        "group2":{"rotation":["-48 * query.mod.jujutsuvfx_cast",0,0],"position":[0,"2 * query.mod.jujutsuvfx_cast",0]},
        "left_front_leg":{"rotation":["-62 * query.mod.jujutsuvfx_cast",0,"-18 * query.mod.jujutsuvfx_cast"]},
        "right_front_leg":{"rotation":["-68 * query.mod.jujutsuvfx_cast",0,"18 * query.mod.jujutsuvfx_cast"]},
        "head":{"rotation":["38 * query.mod.jujutsuvfx_cast",0,0]}}}
    write(RP / "animations/gojo_cat.animation.json", {"format_version":"1.8.0","animations":animations})
    write(RP / "animation_controllers/gojo_cat.controller.json", {"format_version":"1.10.0","animation_controllers":{
        "controller.animation.jujutsuvfx.blindfold":{"initial_state":"covered","states":{
            "covered":{"transitions":[{"uncovered":"query.mod.jujutsuvfx_uncovered > 0.5"}]},
            "uncovered":{"animations":["blindfold"]}}}}})
    copy_asset(ASSETS / "textures/entity/gojo_cat.png", RP / "textures/entity/gojo_cat.png")
    copy_asset(ASSETS / "textures/entity/gojo_cat_details.png", RP / "textures/entity/gojo_cat_details.png")

def manifests():
    for name, folder, kind in (("behavior",BP,"data"),("resource",RP,"resources")):
        write(folder / "manifest.json", {"format_version":2, "header":{
            "name":"咒术映界 · 网易验证版 " + ("行为" if kind=="data" else "资源"),
            "description":"五条猫与全术式 · 原生 GLSL 特效 · ModSDK 3.9",
            "uuid":uid(name), "version":VERSION, "min_engine_version":[1,18,0]},
            "modules":[{"type":kind,"uuid":uid(name+"-module"),"version":VERSION}],
            **({"dependencies":[{"uuid":uid("resource"),"version":VERSION}]} if kind=="data" else {})})
    write(PACK / "world_behavior_packs.json", [{"pack_id":uid("behavior"),"version":VERSION}])
    write(PACK / "world_resource_packs.json", [{"pack_id":uid("resource"),"version":VERSION}])
    write(PACK / "work.mcscfg", {"UID":uid("studio").replace("-", "").upper(),
          "Name":"咒术映界_网易验证版", "Type":7, "Language":0,"IsPc":False,
          "Versions":[],"OpenEditType":2,"Description":"description.txt"})
    (PACK / "description.txt").write_text("五条猫、虚式茈、无量空处。本地验证工程。", encoding="utf-8")

def entities_and_items():
    write(BP / "entities/gojo_cat.json", {"format_version":"1.16.0","minecraft:entity":{
        "description":{"identifier":"jujutsuvfx:gojo_cat","is_spawnable":True,"is_summonable":True,"is_experimental":False},
        "components":{
            "minecraft:type_family":{"family":["jujutsu_cat","mob"]},
            "minecraft:health":{"value":800,"max":800},"minecraft:movement":{"value":0.28},
            "minecraft:collision_box":{"width":0.9,"height":1.15},"minecraft:scale":{"value":1.5},
            "minecraft:physics":{},"minecraft:pushable":{"is_pushable":True,"is_pushable_by_piston":True},
            "minecraft:navigation.walk":{"can_path_over_water":False,"avoid_water":True},"minecraft:movement.basic":{},
            "minecraft:jump.static":{},"minecraft:attack":{"damage":8},
            "minecraft:follow_range":{"value":64},"minecraft:persistent":{},"minecraft:nameable":{},
            "minecraft:damage_sensor":{"triggers":[{"cause":"fall","deals_damage":False}]},
            "minecraft:behavior.float":{"priority":0},"minecraft:behavior.hurt_by_target":{"priority":1},
            "minecraft:behavior.melee_attack":{"priority":3,"speed_multiplier":1.25,"track_target":True},
            "minecraft:behavior.random_stroll":{"priority":6,"speed_multiplier":0.6},
            "minecraft:behavior.look_at_player":{"priority":7,"look_distance":8},"minecraft:behavior.random_look_around":{"priority":8}
        }}})
    materials={"body":"entity_alphatest","details":"entity_alphatest","eyes":"entity_emissive_alpha"}
    render = {}
    for name in materials:
        render["controller.render.jujutsuvfx.gojo_"+name] = {
            "geometry":"Geometry."+name,"materials":[{"*":"Material."+name}],"textures":["Texture."+name]}
    write(RP / "render_controllers/gojo_cat.render_controllers.json", {"format_version":"1.8.0","render_controllers":render})
    write(RP / "entity/gojo_cat.entity.json", {"format_version":"1.10.0","minecraft:client_entity":{"description":{
        "identifier":"jujutsuvfx:gojo_cat","materials":materials,
        "textures":{"body":"textures/entity/gojo_cat","details":"textures/entity/gojo_cat_details","eyes":"textures/entity/gojo_cat"},
        "geometry":{k:"geometry.jujutsuvfx.gojo_cat."+k for k in materials},
        "animations":{"idle":"animation.jujutsuvfx.idle","walk":"animation.jujutsuvfx.walk","cast":"animation.jujutsuvfx.cast",
          "blindfold":"animation.jujutsuvfx.blindfold","blindfold_controller":"controller.animation.jujutsuvfx.blindfold"},
        "scripts":{"animate":["idle","walk","cast","blindfold_controller"]},
        "render_controllers":["controller.render.jujutsuvfx.gojo_"+k for k in materials],
        "spawn_egg":{"base_color":"#f4f6ff","overlay_color":"#35b9e8"}}}})
    for name in ("purple","unlimited_void"):
        desc={"identifier":"jujutsuvfx:"+name,"category":"Equipment","register_to_create_menu":True}
        if name=="purple":
            desc["custom_item_type"]="ranged_weapon"
        write(BP / ("netease_items_beh/"+name+".json"), {"format_version":"1.10","minecraft:item":{
            "description":desc,"components":{"minecraft:max_stack_size":1,**({"minecraft:use_duration":72000} if name=="purple" else {})}}})
        write(RP / ("netease_items_res/"+name+".json"), {"format_version":"1.10","minecraft:item":{
            "description":{"identifier":"jujutsuvfx:"+name},"components":{
                "minecraft:icon":"jujutsuvfx:"+name,"netease:show_in_hand":{"value":False}}}})
    # Rasterize the original voxel inventory artwork; this is a format conversion.
    from PIL import Image, ImageDraw
    colors={"white":(255,255,255,255),"pink":(249,127,210,255),"magenta":(181,45,223,255),
            "purple":(90,31,181,255),"black":(17,14,29,255),"cyan":(20,220,237,255),
            "blue":(44,59,223,255),"light_blue":(81,203,252,255),"red":(230,25,55,255)}
    for name in ("purple","unlimited_void"):
        m=json.loads((ASSETS / ("models/item/"+name+".json")).read_text("utf-8"))
        im=Image.new("RGBA",(32,32));draw=ImageDraw.Draw(im)
        for e in m.get("elements",[]):
            face=e.get("faces",{}).get("north",{});tex=m.get("textures",{}).get(face.get("texture","").lstrip("#"),"")
            col=next((value for key,value in sorted(colors.items(),key=lambda kv:-len(kv[0])) if tex.endswith("/"+key+"_concrete")),(150,130,220,255))
            a,b=e["from"],e["to"]
            draw.rectangle([int(a[0]*2),int((16-b[1])*2),int(b[0]*2)-1,int((16-a[1])*2)-1],fill=col)
        p=RP/("textures/items/"+name+".png");p.parent.mkdir(parents=True,exist_ok=True);im.save(p)
    copy_asset(ASSETS/"textures/item/gojo_blindfold.png",RP/"pack_icon.png")
    copy_asset(RP/"pack_icon.png",BP/"pack_icon.png")
    write(RP/"textures/item_texture.json",{"resource_pack_name":"jujutsuvfx","texture_name":"atlas.items",
          "texture_data":{"jujutsuvfx:"+n:{"textures":"textures/items/"+n} for n in ("purple","unlimited_void")}})
    lang="item.jujutsuvfx:purple.name=虚式·茈\nitem.jujutsuvfx:unlimited_void.name=领域展开·无量空处\nentity.jujutsuvfx:gojo_cat.name=五条猫\nitem.spawn_egg.entity.jujutsuvfx:gojo_cat.name=五条猫 生成蛋\n"
    (RP/"texts").mkdir(exist_ok=True)
    (RP/"texts/zh_CN.lang").write_text(lang,encoding="utf-8")
    (RP/"texts/en_US.lang").write_text(lang,encoding="utf-8")
    write(RP/"texts/languages.json",["zh_CN","en_US"])

def sounds():
    definitions={}
    for name in ("purple_charge","purple_release","purple_flight","purple_end","void_air_friction"):
        copy_asset(ASSETS/("sounds/"+name+".ogg"),RP/("sounds/jujutsuvfx/"+name+".ogg"))
        definitions["jujutsuvfx."+name]={"category":"neutral","sounds":[{"name":"sounds/jujutsuvfx/"+name,"stream":False}]}
    write(RP/"sounds/sound_definitions.json",{"format_version":"1.14.0","sound_definitions":definitions})

def player_hands():
    q = "query.mod.jujutsuvfx_hand_"
    # Match vanilla_netease's file path and both first-person passes. Reusing
    # the player's default geometry and texture preserves the selected skin.
    controllers = {}
    for suffix, map_name, material in (("", "filled_map", "default"), ("_bloom", "map", "bloom")):
        native_right = "(query.get_equipped_item_name(0, 1) == '' || query.get_equipped_item_name(0, 1) == '"+map_name+"' || query.mod.fp_weapon)"
        native_left = "((query.get_equipped_item_name(0, 1) == '"+map_name+"' && query.get_equipped_item_name('off_hand') != 'shield') || query.mod.fp_weapon || (query.get_equipped_item_name('off_hand') == '"+map_name+"' && !query.item_is_charged) || (!query.item_is_charged && variable.item_use_normalized > 0 && variable.item_use_normalized < 1.0))"
        visibility = [{"*":False}]
        visibility += [{name:native_right+" || "+q+"active > 0.01"} for name in ("rightArm","rightSleeve","rightArmDown")]
        visibility += [{name:native_left+" || ("+q+"active > 0.01 && "+q+"both > 0.01)"} for name in ("leftArm","leftSleeve","leftArmDown")]
        controllers["controller.render.player.first_person"+suffix] = {
            "geometry":"Geometry.default","materials":[{"*":"Material."+material}],
            "textures":["Texture.default"]+(["Texture.bloom"] if suffix else []),
            "part_visibility":visibility}
    write(RP/"render_controllers/player.render_controllers.json",{
        "format_version":"1.8.0","render_controllers":controllers})
    legacy = RP/"render_controllers/skill_hands.render_controllers.json"
    if legacy.exists():
        legacy.unlink()
    charge, both, release, void, red, fuga, slash = [q+x for x in ("charge","both","release","void","red","fuga","slash")]
    animations = {
        "animation.player.first_person.empty_hand":{
            "loop":True,
            "bones":{
                "rightarm":{
                    "position":["13.5-4.0*"+charge,"-10.0+2.5*"+charge,"12.0-4.5*"+charge+"-2.0*"+release],
                    "rotation":["95.0+20.0*"+charge+"+10.0*"+red,"-45.0+20.0*"+charge+"+8.0*"+void+"+15.0*"+fuga,"115.0+30.0*"+charge+"-35.0*"+red+"-20.0*"+slash]},
                "leftarm":{
                    "position":["-14.5*"+q+"active","(-17.0+8.0*"+both+")*"+q+"active","8.0*"+q+"active"],
                    "rotation":["55.0*"+q+"active+15.0*"+void,"-20.0*"+q+"active","-155.0*"+q+"active"],
                    "scale":q+"active > 0.01 ? "+both+" : 1.0"},
                "rightitem":{"position":[0,"q.get_default_bone_pivot('rightarm',1) - q.get_default_bone_pivot('rightitem',1) - 7.0","-q.get_default_bone_pivot('rightitem',2)"]},
                "leftitem":{"position":[0,"q.get_default_bone_pivot('leftarm',1) - q.get_default_bone_pivot('leftitem',1) - 7.0","-q.get_default_bone_pivot('leftitem',2)"]}}},
        "animation.jujutsuvfx.hands.third_person":{
            "loop":True,"override_previous_animation":True,"blend_weight":"math.clamp("+charge+"+"+release+",0,1)",
            "bones":{
                "rightarm":{"rotation":["-78.0*"+charge+"-100.0*"+release,"-14.0*"+charge,"12.0*"+charge]},
                "leftarm":{"rotation":["-75.0*"+both,"26.0*"+both,"-18.0*"+both]}}}}
    write(RP/"animations/skill_hands.animation.json",{"format_version":"1.8.0","animations":animations})

def package():
    dist=ROOT/"dist";dist.mkdir(exist_ok=True)
    out=dist/("JujutsuNetease-"+".".join(map(str,VERSION))+".zip")
    with zipfile.ZipFile(out,"w",zipfile.ZIP_DEFLATED) as z:
        for f in sorted(PACK.rglob("*")):
            if f.is_file() and not any(p in ("__pycache__",) for p in f.parts) and f.suffix not in (".pyc",".pyo"):
                z.write(f, f.relative_to(PACK).as_posix())
    print(out, out.stat().st_size,"bytes",hashlib.sha256(out.read_bytes()).hexdigest())

if __name__=="__main__":
    import extend_pack
    manifests();model();entities_and_items();sounds()
    extend_pack.extend(sys.modules[__name__])
    package()

"""Preserve the supplied spirit cat and author its combat and boxing-dance clips.

Run with the bundled Python (Pillow + NumPy). The source model is optional after
the first import. --target defaults to dance so regeneration cannot overwrite
independently authored combat assets. --verify also exports a geometry preview;
verify_rika_dance.py renders the atlas-textured dance beside Yuta.
"""
from pathlib import Path
import argparse, base64, copy, io, json, math
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ASSETS = Path("src/main/resources/assets/jujutsuvfx")
PERIOD = 88 / 30
COMBAT_TIMING = {"punch": (22, 9), "launch": (28, 12), "slam": (36, 14),
                 "guard": (36, 12), "wave": (36, 14), "hook": (26, 11),
                 "claw": (22, 9), "overhead": (32, 14), "idle": (36, 14), "crawl": (36, 14)}


def matrix(origin, rotation):
    x, y, z = np.radians(rotation)
    rx = np.array([[1, 0, 0], [0, math.cos(x), -math.sin(x)], [0, math.sin(x), math.cos(x)]])
    ry = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    rz = np.array([[math.cos(z), -math.sin(z), 0], [math.sin(z), math.cos(z), 0], [0, 0, 1]])
    result = np.eye(4)
    result[:3, :3] = rz @ ry @ rx
    result[:3, 3] = origin
    return result


def sample(keys, channel, t):
    keys = sorted((k for k in keys if k["channel"] == channel), key=lambda k: k["time"])
    if not keys:
        return np.zeros(3)
    def value(k):
        return np.array([float(k["data_points"][0][a]) for a in "xyz"])
    if t <= keys[0]["time"]:
        return value(keys[0])
    for a, b in zip(keys, keys[1:]):
        if t <= b["time"]:
            f = (t-a["time"]) / (b["time"]-a["time"])
            return value(a)*(1-f)+value(b)*f
    return value(keys[-1])


def faces(model, clip=None, time=0):
    groups = {g["uuid"]: g for g in model["groups"]}
    elements = {e["uuid"]: e for e in model["elements"]}
    tracks = clip["animators"] if clip else {}
    result = []
    def visit(node, parent, transform):
        bone = isinstance(node, dict)
        identity = node["uuid"] if bone else node
        data = (groups if bone else elements)[identity]
        origin = np.array(data.get("origin", [0, 0, 0]))
        keys = tracks.get(identity, {}).get("keyframes", [])
        local = matrix(origin-parent+sample(keys, "position", time),
                       np.array(data.get("rotation", [0, 0, 0]))+sample(keys, "rotation", time))
        world = transform @ local
        if bone:
            for child in node["children"]:
                visit(child, origin, world)
            return
        if data["type"] == "mesh":
            vertices = {k: np.array(v) for k, v in data["vertices"].items()}
            polygons = [(f, [vertices[k] for k in f["vertices"]]) for f in data["faces"].values()]
        else:
            a, b = np.array(data["from"]), np.array(data["to"])
            x, y, z = a; X, Y, Z = b
            corners = {
                "north": [(x,Y,z),(X,Y,z),(X,y,z),(x,y,z)],
                "south": [(X,y,Z),(X,Y,Z),(x,Y,Z),(x,y,Z)],
                "east": [(X,Y,z),(X,Y,Z),(X,y,Z),(X,y,z)],
                "west": [(x,y,Z),(x,Y,Z),(x,Y,z),(x,y,z)],
                "up": [(x,Y,Z),(X,Y,Z),(X,Y,z),(x,Y,z)],
                "down": [(X,y,z),(X,y,Z),(x,y,Z),(x,y,z)],
            }
            polygons = [(f, [np.array(v)-origin for v in corners[side]]) for side, f in data["faces"].items()]
        for face, points in polygons:
            if face.get("texture") is None:
                continue
            transformed = np.array([(world @ np.r_[p, 1])[:3] for p in points])
            # Same collapsed-face handling as SorcererCatGeometry.
            if max(np.linalg.norm(np.cross(transformed[i]-transformed[0], transformed[j]-transformed[0]))
                   for i,j in [(1,2),(2,3)]) < 1e-7:
                continue
            result.append((data["uuid"], transformed, face))
    for node in model["outliner"]:
        visit(node, np.zeros(3), np.eye(4))
    return result


def add_key(tracks, group, channel, time, value):
    tracks.setdefault(group["uuid"], {"name": group["name"], "type": "bone", "keyframes": []})["keyframes"].append(
        {"channel": channel, "time": time, "interpolation": "linear",
         "data_points": [dict(zip("xyz", [round(float(v), 7) for v in value]))]})


def smooth(t):
    t = min(1, max(0, t))
    return t*t*(3-2*t)


def pose(kind, tick):
    # Every value is a small additive rotation on the supplied cat's rest pose.
    neutral = {n: [0.,0.,0.] for n in ["head","body","left_front_leg","right_front_leg","group3","group4","group45","group46","group47","group48"]}
    wind, hit, follow = copy.deepcopy(neutral), copy.deepcopy(neutral), copy.deepcopy(neutral)
    if kind in ("idle", "crawl"):
        cycle = math.tau*tick/36
        if kind == "idle":
            neutral.update(head=[.6*math.sin(cycle),0,0],group45=[.8*math.sin(cycle),0,0])
        else:
            # Only the supplied front paws and body/tail chain exist. Crawl on
            # those pivots; do not invent hind legs or detach a limb for a stride.
            stride = math.sin(cycle)
            neutral.update(left_front_leg=[18*stride,0,-2*stride],right_front_leg=[-18*stride,0,-2*stride],
                           body=[1.4*math.sin(2*cycle),0,1.8*stride],head=[-1.5*math.sin(2*cycle),3*stride,0],
                           group3=[2*math.sin(2*cycle),6*stride,0],group4=[0,-9*stride,0],
                           group45=[2.5*math.sin(2*cycle),0,0],group46=[0,3*stride,0],
                           group47=[0,-4*stride,0],group48=[0,5*stride,0])
        return neutral
    if kind == "punch":
        wind.update(right_front_leg=[-28,0,12],head=[-5,8,0])
        hit.update(right_front_leg=[85,-8,-7],head=[3,-8,0])
        follow.update(right_front_leg=[97,-12,-10],head=[4,-10,0])
    elif kind == "launch":
        wind.update(right_front_leg=[-32,0,10],head=[8,5,0],group45=[8,0,0])
        hit.update(right_front_leg=[108,-5,-6],head=[-10,-4,0],group45=[18,0,0])
        follow.update(right_front_leg=[132,-5,-8],head=[-14,-5,0])
    elif kind == "slam":
        wind.update(left_front_leg=[120,0,-10],right_front_leg=[120,0,10],head=[-12,0,0],group45=[12,0,0])
        hit.update(left_front_leg=[5,0,-5],right_front_leg=[5,0,5],head=[10,0,0],group45=[20,0,0])
        follow.update(left_front_leg=[0,0,0],right_front_leg=[0,0,0],head=[12,0,0])
    elif kind == "guard":
        wind.update(left_front_leg=[56,-18,-10],right_front_leg=[56,18,10],head=[5,0,0])
        hit.update(left_front_leg=[77,-10,-5],right_front_leg=[77,10,5],head=[-4,0,0])
        follow.update(left_front_leg=[64,-12,-7],right_front_leg=[64,12,7],head=[0,0,0])
    elif kind == "hook":
        wind.update(left_front_leg=[-22,-28,-32],right_front_leg=[35,10,6],body=[0,-12,-3],head=[-4,12,0])
        hit.update(left_front_leg=[84,36,28],right_front_leg=[35,8,6],body=[0,18,4],head=[3,-15,0])
        follow.update(left_front_leg=[62,52,22],right_front_leg=[30,8,6],body=[0,23,5],head=[4,-18,0])
    elif kind == "claw":
        wind.update(right_front_leg=[65,-55,20],left_front_leg=[28,-10,-8],body=[0,-17,-4],head=[-3,14,0])
        hit.update(right_front_leg=[70,48,-18],left_front_leg=[45,-28,-8],body=[0,19,5],head=[3,-15,0])
        follow.update(right_front_leg=[46,65,-24],left_front_leg=[35,-18,-7],body=[0,25,5],head=[5,-18,0])
    elif kind == "overhead":
        wind.update(right_front_leg=[154,-15,12],left_front_leg=[48,-8,-12],body=[-5,-8,-2],head=[-10,5,0])
        hit.update(right_front_leg=[30,-12,-5],left_front_leg=[45,-6,-10],body=[8,8,3],head=[10,-5,0],group45=[15,0,0])
        follow.update(right_front_leg=[12,-8,-7],left_front_leg=[35,-6,-8],body=[11,11,4],head=[12,-8,0])
    elif kind == "wave":
        wind.update(left_front_leg=[54,-35,-18],right_front_leg=[54,35,18],head=[-7,0,0],body=[-3,0,0])
        hit.update(left_front_leg=[80,-6,-6],right_front_leg=[80,6,6],head=[2,0,0],body=[5,0,0])
        follow.update(left_front_leg=[88,-4,-6],right_front_leg=[88,4,6],head=[4,0,0],body=[7,0,0])
    duration, contact = COMBAT_TIMING[kind]
    keys = [(0,neutral),(contact-6,wind),(10 if kind=="guard" else contact-4,wind),
            (contact,hit),(contact+2,hit),(contact+7,follow),(duration-3,neutral),(duration,neutral)]
    for (a,pa),(b,pb) in zip(keys,keys[1:]):
        if tick <= b:
            u = smooth((tick-a)/(b-a))
            result = {n: np.array(pa[n])*(1-u)+np.array(pb[n])*u for n in neutral}
            tail = math.sin(math.pi*tick/duration)
            result["group3"] = [0, 6*tail, 0]
            result["group4"] = [0, -10*tail, 0]
            result["group46"] = [2*tail, 0, 0]
            result["group47"] = [-3*tail, 0, 0]
            result["group48"] = [4*tail, 0, 0]
            return result
    return neutral


def author(model, dance):
    if dance:
        from rika_dance_author import author_dance
        return author_dance(model, add_key, faces, PERIOD)
    groups = {g["name"]: g for g in model["groups"]}
    clips = []
    for kind in ["idle","crawl","punch","launch","slam","guard","wave","hook","claw","overhead"]:
        tracks = {}
        ticks = COMBAT_TIMING[kind][0]
        count, duration = ticks*4, ticks/20
        for frame in range(count+1):
            time = duration*frame/count
            values = pose(kind, time*20)
            for name, value in values.items():
                add_key(tracks, groups[name], "rotation", time, value)
        clip = {"name":"animation.rika."+kind, "loop":"loop" if kind in ("idle","crawl") else "once", "length":duration, "animators":tracks}
        # Entire hierarchy is supported by its lowest surface. No scale tracks, and
        # no separate translations of limbs from their supplied attachment points.
        floors = []
        for frame in range(count+1):
            time = duration*frame/count
            floor = min(p[:,1].min() for _,p,_ in faces(model,clip,time))
            floors.append((time,floor))
        for time, floor in floors:
            add_key(tracks, groups["group2"], "position", time, [0,-floor,0])
        clips.append(clip)
    model["animations"] = clips
    return model


def import_model(source, target="dance"):
    model = json.loads(source.read_text(encoding="utf-8-sig"))
    original = copy.deepcopy(model)
    atlas = Image.new("RGBA",(64,96))
    for index, texture in enumerate(model["textures"]):
        image = Image.open(io.BytesIO(base64.b64decode(texture["source"].split(",",1)[1]))).convert("RGBA")
        assert image.size == ((64,32) if index==0 else (64,64))
        atlas.paste(image,(0,0 if index==0 else 32))
    for element in model["elements"]:
        for face in element["faces"].values():
            if face.get("texture") is None:
                continue
            if face["texture"] == 1:
                if isinstance(face["uv"],dict):
                    for uv in face["uv"].values(): uv[1] += 32
                else:
                    face["uv"][1] += 32; face["uv"][3] += 32
            face["texture"] = 0
    png = io.BytesIO(); atlas.save(png,format="PNG")
    texture = copy.deepcopy(model["textures"][0])
    texture.update(name="rika_dance.png",width=64,height=96,uv_width=64,uv_height=96,
                   source="data:image/png;base64,"+base64.b64encode(png.getvalue()).decode("ascii"))
    model["textures"] = [texture]
    model["resolution"] = {"width":64,"height":96}
    model["rika_source"] = {"name":source.name,"elements":len(model["elements"]),"groups":len(model["groups"]),
                            "materials":2,"note":"All original geometry, UVs and hierarchy preserved; materials packed vertically."}
    # Preserve authored model structure byte-for-byte apart from material remapping.
    assert model["groups"] == original["groups"] and model["outliner"] == original["outliner"]
    for dance in [True,False]:
        if target != "both" and (target == "dance") != dance:
            continue
        authored = author(copy.deepcopy(model),dance)
        name = "rika_dance" if dance else "rika_combat"
        for project in ["neoforge-vfx","forge-vfx"]:
            assets = ROOT/project/ASSETS
            (assets/f"models/entity/{name}.bbmodel").write_text(json.dumps(authored,separators=(",",":")),encoding="utf-8")
            (assets/"textures/entity/rika_dance.png").write_bytes(png.getvalue())


def verify(preview_only=False, target="dance"):
    frames = []
    for name in ["rika_dance","rika_combat"]:
        if target != "both" and name != "rika_"+target:
            continue
        path = ROOT/"neoforge-vfx"/ASSETS/f"models/entity/{name}.bbmodel"
        assert path.read_bytes() == (ROOT/"forge-vfx"/ASSETS/f"models/entity/{name}.bbmodel").read_bytes()
        model = json.loads(path.read_text(encoding="utf-8"))
        assert len(model["elements"])==51 and len(model["groups"])==55
        for clip in model["animations"]:
            assert all(k["channel"]!="scale" for a in clip["animators"].values() for k in a["keyframes"])
            baseline = None
            for time in ([] if preview_only else np.linspace(0,clip["length"],217)):
                rendered = faces(model,clip,float(time))
                vertices = np.concatenate([p for _,p,_ in rendered])
                assert np.isfinite(vertices).all()
                # A floor-support switch between rigid surfaces can introduce
                # sub-pixel interpolation error (0.12 model pixels < 0.011 blocks).
                expected = 0
                if name == "rika_dance":
                    from rika_dance_author import pulse
                    expected = .7*pulse(float(time)/clip["length"]*8)
                assert abs(vertices[:,1].min()-expected)<.12, (clip["name"],time,vertices[:,1].min())
                assert np.max(np.abs(vertices))<50
                identities = set(e for e,_,_ in rendered)
                if baseline is None: baseline = identities
                assert identities == baseline and len(identities)==51, (clip["name"],len(identities))
            if clip["loop"]=="loop":
                a=np.concatenate([p for _,p,_ in faces(model,clip,0)])
                b=np.concatenate([p for _,p,_ in faces(model,clip,clip["length"])])
                assert np.max(np.abs(a-b))<1e-5
            kind=clip["name"].split(".")[-1]
            duration,contact=COMBAT_TIMING.get(kind,(36,14))
            moments = [0,.22,.44,.66] if name=="rika_dance" else [0,(contact-4)/20,contact/20,(duration-3)/20]
            for t in moments:
                frames.append((clip["name"].split(".")[-1]+f" {t:.2f}s",faces(model,clip,t)))
            if not preview_only:
                print(clip["name"],"217 sampled frames, 51 complete elements, floor-supported; no scale tracks")
    preview = ROOT/"neoforge-vfx/build/rika-cat-authored-preview.png"
    preview.parent.mkdir(exist_ok=True)
    # Geometry preview uses original material colors and a fixed oblique camera.
    image=Image.new("RGB",(1200,math.ceil(len(frames)/4)*280),(24,29,37)); draw=ImageDraw.Draw(image)
    atlas=Image.open(ROOT/"neoforge-vfx"/ASSETS/"textures/entity/rika_dance.png").convert("RGBA")
    for i,(label,polys) in enumerate(frames):
        ox,oy=(i%4)*300+145,(i//4)*280+228
        draw.text((i%4*300+12,i//4*280+10),label,fill="white")
        draw.line([(ox-135,oy),(ox+135,oy)],fill=(70,90,100))
        def project(p):
            return (ox+7.5*(p[0]*.85+p[2]*.53),oy-7.5*(p[1]+p[0]*.22-p[2]*.35))
        for identity,p,face in sorted(polys,key=lambda f:np.mean(f[1][:,0]*.53-f[1][:,2]*.85)):
            uv=face["uv"]
            coords=list(uv.values()) if isinstance(uv,dict) else [(uv[0],uv[1]),(uv[2],uv[3])]
            u,v=np.mean(coords,axis=0)
            color=atlas.getpixel((max(0,min(63,int(u))),max(0,min(95,int(v)))))
            if color[3]<32: color=(150,140,130,255)
            draw.polygon([project(v) for v in p],fill=color[:3],outline=tuple(max(0,c-28) for c in color[:3]))
    image.save(preview)
    print("Preview:",preview)


if __name__=="__main__":
    parser=argparse.ArgumentParser();parser.add_argument("source",type=Path,nargs="?");parser.add_argument("--verify",action="store_true");parser.add_argument("--preview",action="store_true")
    parser.add_argument("--target",choices=["dance","combat","both"],default="dance",help="Only explicitly selected assets are rewritten.")
    parser.add_argument("--regenerate",action="store_true",help="Reauthor selected checked-in assets, retaining packed textures.")
    args=parser.parse_args()
    if args.source: import_model(args.source,args.target)
    if args.regenerate:
        for dance in [True,False]:
            if args.target != "both" and (args.target == "dance") != dance: continue
            name="rika_dance" if dance else "rika_combat"
            source=ROOT/"neoforge-vfx"/ASSETS/f"models/entity/{name}.bbmodel"
            model=author(json.loads(source.read_text(encoding="utf-8")),dance)
            for project in ["neoforge-vfx","forge-vfx"]:
                (ROOT/project/ASSETS/f"models/entity/{name}.bbmodel").write_text(json.dumps(model,separators=(",",":")),encoding="utf-8")
    if args.verify: verify(target=args.target)
    elif args.preview: verify(preview_only=True,target=args.target)

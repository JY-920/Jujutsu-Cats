"""Verify actual finale meshes, then rasterize production capture data when present."""
from collections import Counter
import json
import math
import numpy as np
from PIL import Image, ImageDraw
from import_rika_cat import ROOT, ASSETS, faces
from rika_dance_author import cube_surface
from verify_rika_dance import raster
from build_rika_finale import LENGTHS


def verify():
    asset=ROOT/"neoforge-vfx"/ASSETS
    path=asset/"models/entity/rika_finale.bbmodel"
    assert path.exists(), "Dedicated Rika finale clips are missing"
    assert path.read_bytes()==(ROOT/"forge-vfx"/ASSETS/"models/entity/rika_finale.bbmodel").read_bytes()
    model=json.loads(path.read_text(encoding="utf8"))
    original=json.loads((asset/"models/entity/rika_combat.bbmodel").read_text(encoding="utf8"))
    assert model["groups"]==original["groups"] and model["outliner"]==original["outliner"], "Supplied cat rig was replaced"
    assert model["textures"]==original["textures"] and len(model["elements"])==51
    muzzle=next(e["uuid"] for e in model["elements"] if e["name"]=="nose" and e.get("origin")==[0,9.4,-8.7])
    for e,old in zip(model["elements"],original["elements"]):
        if e["name"] not in ("left_front_leg_cube","right_front_leg_cube"):
            assert e==old, "Non-arm source geometry changed"
            continue
        assert e["rika_original_cube"]==old
        edges=Counter()
        for f in e["faces"].values():
            v=f["vertices"]
            for a,b in zip(v,v[1:]+v[:1]):edges[tuple(sorted((a,b)))]+=1
            assert all(0<=u<=64 and 0<=v<=96 for u,v in f["uv"].values())
        assert set(edges.values())=={2}, "Forepaw skin has an open elbow"
        # Exactly the original shoulder cross-section: curling may not detach it.
        top=[v for v in e["vertices"].values() if abs(v[1])<1e-7]
        assert len(top)==4 and max(v[0] for v in top)-min(v[0] for v in top)==2
    prior=None
    for clip in model["animations"]:
        kind=clip["name"].split(".")[-1]
        assert abs(clip["length"]*20-LENGTHS[kind])<1e-7
        for track in clip["animators"].values():
            assert all(k["channel"]!="scale" for k in track["keyframes"]), "No per-limb size tricks"
        for tick in np.linspace(0,LENGTHS[kind],LENGTHS[kind]*2+1):
            rendered=faces(model,clip,float(tick)/20)
            p=np.concatenate([v for _,v,_ in rendered])
            assert len({e for e,_,_ in rendered})==51
            assert np.isfinite(p).all() and np.max(np.abs(p))<40
            assert abs(p[:,1].min())<.09, (kind,tick,p[:,1].min())
            if kind in ("charge","clash","breakthrough"):
                nose=np.concatenate([v for identity,v,_ in rendered if identity==muzzle])
                center=(nose.min(0)+nose.max(0))/2*np.array([-3.1/16,3.1/16,3.1/16])
                assert np.linalg.norm(center-[0,2.25,0])<.45, ("Muzzle misses beam source",kind,tick,center)
            if tick==0 and prior is not None:assert np.max(np.abs(p-prior))<1e-5, "Clip switch pops"
            if tick==LENGTHS[kind]:prior=p
    print("PASS: 755 half-tick poses, original 55-bone cat hierarchy/51 elements, grounded support, watertight elbows, five seamless clips")
    return model


def preview(model):
    asset=ROOT/"neoforge-vfx"/ASSETS
    elements={e["uuid"]:e for e in model["elements"]}
    textures=[np.array(Image.open(asset/"textures/entity/rika_dance.png").convert("RGBA"))]
    board=Image.new("RGB",(2400,1080))
    examples=[("vow",35),("charge",85),("clash",6),("clash",50),("breakthrough",16),("recovery",48)]
    for column,(name,tick) in enumerate(examples):
        clip=next(c for c in model["animations"] if c["name"].endswith("."+name))
        polygons=[]
        for identity,p,face in faces(model,clip,tick/20):
            if isinstance(face["uv"],dict):uv=np.array([face["uv"][v] for v in face["vertices"]])
            else:
                side=next(k for k,v in elements[identity]["faces"].items() if v is face)
                uv=np.array(cube_surface(elements[identity],side)[1])
            polygons.append((p*[-3.1/16,3.1/16,3.1/16]+[1,0,0],uv,0))
        for row,azimuth in enumerate((0,-30)):
            panel=raster(polygons,textures,azimuth,width=400,height=540)
            d=ImageDraw.Draw(panel);d.text((12,12),f"{name}  {tick} ticks / {'front' if row==0 else 'oblique'}",fill="white")
            d.line((10,475,390,475),fill=(80,100,120))
            board.paste(panel,(column*400,row*540))
    out=ROOT/"build/duel112-pose-tests/rika-finale-textured.png"
    out.parent.mkdir(parents=True,exist_ok=True);board.save(out)
    print("Preview:",out)


if __name__=="__main__":
    preview(verify())

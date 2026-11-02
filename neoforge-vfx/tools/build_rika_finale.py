"""Author the five finale clips on the supplied cat rig, with continuous elbow skins.

The spirit has no hind legs: its existing taper/tail supports every frame.
No supplied combat/dance file, group, texture or hierarchy is rewritten.
"""
import copy
import json
import math
from pathlib import Path
import numpy as np
from import_rika_cat import ROOT, ASSETS, add_key, faces, smooth
from rika_dance_author import curved_arm

LENGTHS = {"vow":70, "charge":110, "clash":90, "breakthrough":40, "recovery":65}


def phase(t, a, b):
    return smooth((t-a)/(b-a))


def mix(a, b, w):
    return {key:a[key]*(1-w)+b[key]*w for key in a}


READY = dict(tilt=22., arm=6., spread=12., jaw=6., stress=0., recoil=0.)
CHARGED = dict(tilt=23.5, arm=14., spread=9., jaw=12., stress=.6, recoil=0.)
FIRING = dict(tilt=24.3, arm=20., spread=7., jaw=18., stress=1., recoil=0.)
SETTLED = dict(tilt=14., arm=-26., spread=24., jaw=2., stress=0., recoil=0.)


def pose(kind, age):
    if kind == "vow":
        p=mix(SETTLED,READY,phase(age,8,52))
    elif kind == "charge":
        p=mix(READY,CHARGED,phase(age,8,104))
        breath=math.sin(age*.15)*phase(age,0,15)*(1-phase(age,92,110))
        p["tilt"]+=.28*breath
        p["arm"]+=.8*breath
    elif kind == "clash":
        p=mix(CHARGED,FIRING,phase(age,14,34))
        kick=phase(age,0,6)*(1-phase(age,7,18))
        tremble=math.sin(age*.30)*phase(age,18,30)*(1-phase(age,75,90))
        p["tilt"]-=3.1*kick
        p["arm"]-=11*kick
        p["jaw"]+=5*kick
        p["recoil"]=1.1*kick+.12*tremble
        p["arm"]+=.65*tremble
    elif kind == "breakthrough":
        p=dict(FIRING)
        push=phase(age,0,11)*(1-phase(age,22,40))
        p["tilt"]+=2.2*push
        p["arm"]+=7*push
        p["jaw"]+=4*push
        p["recoil"]=-.35*push
    else:
        p=mix(FIRING,SETTLED,phase(age,6,61))
    return {
        "group5":[p["tilt"],0,0],
        "head":[-p["tilt"]-8,0,0],
        "left_front_leg":[p["arm"],-8,p["spread"]],
        "right_front_leg":[p["arm"],8,-p["spread"]],
        "group3":[-28+2*p["stress"],0,0],
        "group4":[18-3*p["stress"],0,0],
        "group45":[p["jaw"],0,0],
    }, p["recoil"]


def author():
    source=ROOT/"neoforge-vfx"/ASSETS/"models/entity/rika_combat.bbmodel"
    model=json.loads(source.read_text(encoding="utf8"))
    model=copy.deepcopy(model)
    groups={g["name"]:g for g in model["groups"]}
    for i,element in enumerate(model["elements"]):
        if element["name"] in ("left_front_leg_cube","right_front_leg_cube"):
            # Same original cat foreleg and original UVs. One watertight skin
            # curves through the elbow; no floating replacement paw is introduced.
            model["elements"][i]=curved_arm(element.get("rika_original_cube",element))
    clips=[]
    for kind,length in LENGTHS.items():
        tracks={}
        for tick in range(length+1):
            values,_=pose(kind,tick)
            for name,value in values.items():
                add_key(tracks,groups[name],"rotation",tick/20,value)
        clip={"name":"animation.rika."+kind,"loop":"once","length":length/20,"animators":tracks}
        support=[]
        for tick in range(length+1):
            floor=min(p[:,1].min() for _,p,_ in faces(model,clip,tick/20))
            _,recoil=pose(kind,tick)
            # The contract's anchor is the projection beneath the firing point.
            # Move the whole supplied hierarchy back so the muzzle frames (0,2.25,0).
            support.append((tick/20,[0,-floor,10.5+recoil]))
        for time,position in support:
            add_key(tracks,groups["group2"],"position",time,position)
        clips.append(clip)
        print("Authored",kind,length+1,"grounded frames")
    model["animations"]=clips
    model["rika_finale_design"]={
        "scale":3.1,
        "source":[0,2.25,0],
        "support":"Original taper/tail base, not invented hind legs.",
        "arms":"Original cat forelegs, continuous 115-degree elbow skins with original atlas UVs.",
        "timing":"Vow70, charge110, clash90, breakthrough40, recovery65; endpoint-continuous.",
    }
    data=json.dumps(model,separators=(",",":"))
    for project in ("neoforge-vfx","forge-vfx"):
        (ROOT/project/ASSETS/"models/entity/rika_finale.bbmodel").write_text(data,encoding="utf8")


if __name__=="__main__":
    author()

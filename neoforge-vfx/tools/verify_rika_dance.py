"""Offline dance rig/clock/UV checks plus actual atlas-textured front and side views."""
from collections import Counter
import json
import math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
from import_rika_cat import ROOT, ASSETS, faces, sample
from rika_dance_author import pulse, cube_surface


def verify_arm_skin(model):
    """Catch inverted strips: a closed mesh can still fold through itself at an elbow."""
    for arm in model['elements']:
        if arm['name'] not in ('left_front_leg_cube','right_front_leg_cube'):
            continue
        vertices={key:np.array(value) for key,value in arm['vertices'].items()}
        for name,face in arm['faces'].items():
            p=np.array([vertices[key] for key in face['vertices']])
            first=np.cross(p[1]-p[0],p[2]-p[0])
            second=np.cross(p[2]-p[0],p[3]-p[0])
            dot=np.dot(first,second)/max(1e-12,np.linalg.norm(first)*np.linalg.norm(second))
            assert dot>.5, f"{arm['name']} {name}: skin folds into a pointed/reversed quad ({dot:.3f})"
        count=sum(name.startswith('north0_') for name in arm['faces'])
        rings=[]
        for i in range(count):
            north=[vertices[v] for v in arm['faces'][f'north0_{i}']['vertices']]
            south=[vertices[v] for v in arm['faces'][f'south{count-1-i}_0']['vertices']]
            rings.append(np.array([north[0],north[1],south[1],south[2]]))
        rings.append(np.array([north[3],north[2],south[0],south[3]]))
        for a,b in zip(rings,rings[1:]):
            travel=(b-a).mean(axis=0)
            assert np.min((b-a)@travel)>0, f"{arm['name']}: inner elbow skin runs backward through the preceding ring"
    print('PASS: both arm skins have consistently facing quads and no reversed longitudinal edges.')


def verify_spirit_curl(model, clip):
    """The dark spirit taper must turn upward and span a visible loop, not hang straight."""
    tail_ids = set()
    tip_id = None
    groups = {g['uuid']:g['name'] for g in model['groups']}
    def visit(node, in_tail=False):
        nonlocal tip_id
        if isinstance(node, dict):
            name = groups[node['uuid']]
            for child in node['children']:
                visit(child, in_tail or name == 'group46')
        elif in_tail:
            tail_ids.add(node)
            tip_id = node
    for node in model['outliner']:
        visit(node)
    for t in [0, clip['length']*.025, clip['length']*.075]:
        polys = faces(model, clip, t)
        tail = np.concatenate([p for identity,p,_ in polys if identity in tail_ids])
        tip = np.concatenate([p for identity,p,_ in polys if identity == tip_id])
        assert tip[:,1].mean()-tail[:,1].min() > 4, 'Dark lower-tail tip still hangs down instead of curling upward'
        assert np.ptp(tail[:,0]) > 6.5, 'Dark lower-tail curl has no visible front-facing width'
        # Consecutive skins must meet exactly after the production hierarchy
        # transforms; closed individual cubes alone would not catch a split coil.
        elements = {e['uuid']:e for e in model['elements']}
        chain = [identity for identity in elements if identity in tail_ids]
        chain.sort(key=lambda identity: -elements[identity]['rika_original_cube']['to'][1])
        for previous, following in zip(chain, chain[1:]):
            def cap(identity, prefix):
                element = elements[identity]
                return np.concatenate([p for i,p,f in polys if i == identity and
                    any(k.startswith(prefix) and v is f for k,v in element['faces'].items())])
            a,b = cap(previous,'down'),cap(following,'up')
            assert np.linalg.norm(a[:,None,:]-b[None,:,:],axis=2).min(axis=1).max()<1e-5, 'Spirit coil has a detached segment'


def verify(model):
    clip = model["animations"][0]
    verify_arm_skin(model)
    verify_spirit_curl(model, clip)
    assert abs(clip["length"]-88/30)<1e-7
    assert len(model["elements"])==51 and len(model["groups"])==55
    group = {g["name"]:g for g in model["groups"]}
    elements = {e['name']:e for e in model['elements']}
    tracks = clip["animators"]
    for arm in [e for e in model['elements'] if 'rika_original_cube' in e]:
        assert arm["type"]=="mesh"
        edges = Counter()
        for face in arm["faces"].values():
            ids = face["vertices"]
            for a,b in zip(ids,ids[1:]+ids[:1]):
                edges[tuple(sorted([a,b]))]+=1
            for u,v in face["uv"].values():
                assert 0<=u<=64 and 0<=v<=96
        assert set(edges.values())=={2}, "Curved limb/tail skin must be closed and continuous"
    min_world = np.full(3,np.inf)
    max_world = np.full(3,-np.inf)
    for cycle in np.linspace(0,1,217):
        t=float(cycle)*clip["length"]
        left=sample(tracks[group["left_front_leg"]["uuid"]]["keyframes"],"rotation",t)
        right=sample(tracks[group["right_front_leg"]["uuid"]]["keyframes"],"rotation",t)
        assert abs(left[0]-right[0])<1e-7 and abs(left[2]+right[2])<1e-7
        assert abs(left[2]-8*pulse(float(cycle)*4))<.035
        assert abs(left[0]+2.58*pulse(float(cycle)*8))<.012
        polygons = faces(model,clip,t)
        # Use the transformed face plane, not global Z: a leaned/yawed head
        # can look correct from the front while its fists are behind its ears.
        skull = elements['head_main']
        front = next(p for identity,p,f in polygons if identity == skull['uuid'] and f is skull['faces']['north'])
        forward = np.cross(front[1]-front[0],front[2]-front[0])
        forward /= np.linalg.norm(forward)
        up = front[0]-front[3]
        up /= np.linalg.norm(up)
        side = front[1]-front[0]
        half_width = np.linalg.norm(side)*.5
        side /= np.linalg.norm(side)
        skull_points = np.concatenate([p for identity,p,_ in polygons if identity == skull['uuid']])
        for name in ['left_front_leg_cube','right_front_leg_cube']:
            arm = elements[name]
            tips = [p for identity,p,f in polygons if identity == arm['uuid'] and
                    any(k.startswith('down') and v is f for k,v in arm['faces'].items())]
            fist = np.concatenate(tips).mean(axis=0)
            clearance = np.dot(fist-front.mean(axis=0),forward)
            assert clearance > 1., f'{name} fist is not in front of face: {clearance:.3f}px at {t:.3f}s'
            offset = fist-front.mean(axis=0)
            height = np.dot(offset,up)
            lateral = abs(np.dot(offset,side))
            assert -2.5 < height < .6, f'{name} fist must guard the cheek, not the forehead: height={height:.3f}px at {t:.3f}s'
            assert lateral > half_width, f'{name} fist overlaps the face instead of guarding its side at {t:.3f}s'
            assert fist[1] < skull_points[:,1].max()-.7, f'{name} fist rises over the head at {t:.3f}s'
        world = np.concatenate([p for _,p,_ in polygons])
        floor=world[:,1].min()
        assert abs(floor-.7*pulse(float(cycle)*8))<.025
        world=world*np.array([-2.4/16,2.4/16,2.4/16])+[1.75,0,1.10]
        # The reference's wide inward elbow passes behind Yuta's shoulder.
        # Test the occupied face space instead of forbidding that rear overlap.
        face_space=(np.abs(world[:,0])<.38)&(world[:,1]>1.15)&(world[:,1]<2.05)&(world[:,2]<.45)&(world[:,2]>-.65)
        assert not face_space.any(), 'Rika arm crosses the foreground dancer face'
        min_world=np.minimum(min_world,world.min(axis=0))
        max_world=np.maximum(max_world,world.max(axis=0))
    assert min_world[0]>-.45, "Inward elbow reaches beyond the close rear stagger"
    assert min_world[2]>-1.0, "Leaning companion must not advance across the dancer"
    print("PASS: 217 frames; symmetric boxing arms; 8 bounce / 4 elbow pulses; watertight UV skins; grounded hops.")
    print("World bounds:",min_world,max_world)
    return clip


def raster(polygons, textures, azimuth, width=600, height=500):
    image=np.empty((height,width,3),dtype=np.uint8);image[:]=[24,29,37]
    depth=np.full((height,width),np.inf)
    angle=math.radians(azimuth);elevation=math.radians(12)
    c,s=math.cos(angle),math.sin(angle)
    ce,se=math.cos(elevation),math.sin(elevation)
    def project(points):
        x=points[:,0]*c+points[:,2]*s
        z=-points[:,0]*s+points[:,2]*c
        y=points[:,1]*ce-z*se
        d=points[:,1]*se+z*ce
        return np.stack([width*.29+x*86,height-65-y*86,d],axis=1)
    for p,uv,material in polygons:
        q=project(p);texture=textures[material]
        normal=np.cross(p[1]-p[0],p[2]-p[0]);nlen=np.linalg.norm(normal)
        shade=.70+.30*abs(np.dot(normal/max(1e-10,nlen),np.array([.2,.6,-.77])))
        for indices in [(0,1,2),(0,2,3)]:
            a,b,cq=q[list(indices)];tuv=uv[list(indices)]
            xmin=max(0,int(math.floor(min(a[0],b[0],cq[0]))));xmax=min(width-1,int(math.ceil(max(a[0],b[0],cq[0]))))
            ymin=max(0,int(math.floor(min(a[1],b[1],cq[1]))));ymax=min(height-1,int(math.ceil(max(a[1],b[1],cq[1]))))
            if xmin>xmax or ymin>ymax:continue
            denom=(b[1]-cq[1])*(a[0]-cq[0])+(cq[0]-b[0])*(a[1]-cq[1])
            if abs(denom)<1e-9:continue
            yy,xx=np.mgrid[ymin:ymax+1,xmin:xmax+1];xx=xx+.5;yy=yy+.5
            w0=((b[1]-cq[1])*(xx-cq[0])+(cq[0]-b[0])*(yy-cq[1]))/denom
            w1=((cq[1]-a[1])*(xx-cq[0])+(a[0]-cq[0])*(yy-cq[1]))/denom
            w2=1-w0-w1
            d=w0*a[2]+w1*b[2]+w2*cq[2]
            mask=(w0>=-1e-8)&(w1>=-1e-8)&(w2>=-1e-8)&(d<depth[ymin:ymax+1,xmin:xmax+1])
            if not mask.any():continue
            tu=w0*tuv[0,0]+w1*tuv[1,0]+w2*tuv[2,0]
            tv=w0*tuv[0,1]+w1*tuv[1,1]+w2*tuv[2,1]
            color=texture[np.clip(tv.astype(int),0,texture.shape[0]-1),np.clip(tu.astype(int),0,texture.shape[1]-1)]
            mask &= color[:,:,3]>32
            image[ymin:ymax+1,xmin:xmax+1][mask]=(color[:,:,:3]*shade).astype(np.uint8)[mask]
            depth[ymin:ymax+1,xmin:xmax+1][mask]=d[mask]
    return Image.fromarray(image)


def preview(model,clip):
    elements={e["uuid"]:e for e in model["elements"]}
    asset=ROOT/"neoforge-vfx"/ASSETS
    textures=[np.array(Image.open(asset/("textures/entity/"+name+".png")).convert("RGBA"))
              for name in ["yuta_cat","yuta_details","rika_dance"]]
    production=ROOT/"neoforge-vfx/build/yuta-rika-dance.json"
    yuta=json.loads(production.read_text()) if production.exists() else None
    times=[0,clip["length"]*.025,clip["length"]*.05,clip["length"]*.125]
    board=Image.new("RGB",(2400,1500))
    for column,t in enumerate(times):
        polygons=[]
        for identity,p,face in faces(model,clip,t):
            if isinstance(face["uv"],dict):uv=np.array([face["uv"][v] for v in face["vertices"]])
            else:
                side=next(k for k,v in elements[identity]["faces"].items() if v is face)
                uv=np.array(cube_surface(elements[identity],side)[1])
            polygons.append((p*np.array([-2.4/16,2.4/16,2.4/16])+[1.75,0,1.10],uv,2))
        if yuta:
            frame=yuta[min(88,round(t*30))]
            for f in frame["faces"]:
                material=f["material"]
                if material==2:continue
                p=np.array(f["vertices"])
                world=p[:,:3]*[1.5,-1.5,1.5]+[0,1.501*1.5,0]
                uv=p[:,3:5]*[textures[material].shape[1],textures[material].shape[0]]
                polygons.append((world,uv,material))
        for row,azimuth in enumerate([0,-25,90]):
            panel=raster(polygons,textures,azimuth)
            ImageDraw.Draw(panel).text((18,16),f"{['FRONT','OBLIQUE','SIDE'][row]}  {t:.3f}s / {clip['length']:.3f}s",fill="white")
            board.paste(panel,(column*600,row*500))
    target=ROOT/"neoforge-vfx/build/rika-dance-textured-preview.png"
    board.save(target)
    print("Textured preview:",target)


if __name__=="__main__":
    path=ROOT/"neoforge-vfx"/ASSETS/"models/entity/rika_dance.bbmodel"
    assert path.read_bytes()==(ROOT/"forge-vfx"/ASSETS/"models/entity/rika_dance.bbmodel").read_bytes()
    model=json.loads(path.read_text(encoding="utf8"))
    preview(model,verify(model))

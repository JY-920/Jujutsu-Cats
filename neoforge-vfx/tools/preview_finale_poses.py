"""Rasterize the production Java mesh captures with the original actor atlases."""
import json
import math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
from import_rika_cat import ROOT, ASSETS


def project(p, angle):
    c,s=math.cos(math.radians(angle)),math.sin(math.radians(angle))
    ce,se=math.cos(math.radians(12)),math.sin(math.radians(12))
    x=p[:,0]*c+p[:,2]*s
    z=-p[:,0]*s+p[:,2]*c
    return np.stack([x,p[:,1]*ce-z*se,p[:,1]*se+z*ce],axis=1)


def raster(polys, textures, angle, bounds, width=400, height=480, source=False):
    image=np.empty((height,width,3),dtype=np.uint8);image[:]=[24,29,37]
    depth=np.full((height,width),np.inf)
    lower,upper=bounds
    scale=min((width-48)/(upper[0]-lower[0]),(height-76)/(upper[1]-lower[1]))
    center=(lower+upper)/2
    def screen(p):
        q=project(p,angle)
        q[:,0]=width/2+(q[:,0]-center[0])*scale
        q[:,1]=(height+30)/2-(q[:,1]-center[1])*scale
        return q
    for p,uv,material in polys:
        q=screen(p);texture=textures[material]
        normal=np.cross(p[1]-p[0],p[2]-p[0]);nlen=np.linalg.norm(normal)
        shade=.70+.30*abs(np.dot(normal/max(1e-10,nlen),np.array([.2,.6,-.77])))
        for inds in ((0,1,2),(0,2,3)):
            a,b,c=q[list(inds)];tuv=uv[list(inds)]
            x0=max(0,int(math.floor(min(a[0],b[0],c[0]))));x1=min(width-1,int(math.ceil(max(a[0],b[0],c[0]))))
            y0=max(0,int(math.floor(min(a[1],b[1],c[1]))));y1=min(height-1,int(math.ceil(max(a[1],b[1],c[1]))))
            if x0>x1 or y0>y1:continue
            denominator=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
            if abs(denominator)<1e-9:continue
            yy,xx=np.mgrid[y0:y1+1,x0:x1+1];xx=xx+.5;yy=yy+.5
            w0=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/denominator
            w1=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/denominator
            w2=1-w0-w1
            d=w0*a[2]+w1*b[2]+w2*c[2]
            mask=(w0>=-1e-8)&(w1>=-1e-8)&(w2>=-1e-8)&(d<depth[y0:y1+1,x0:x1+1])
            if not mask.any():continue
            tu=w0*tuv[0,0]+w1*tuv[1,0]+w2*tuv[2,0]
            tv=w0*tuv[0,1]+w1*tuv[1,1]+w2*tuv[2,1]
            color=texture[np.clip(tv.astype(int),0,texture.shape[0]-1),np.clip(tu.astype(int),0,texture.shape[1]-1)]
            mask &= color[:,:,3]>32
            image[y0:y1+1,x0:x1+1][mask]=(color[:,:,:3]*shade).astype(np.uint8)[mask]
            depth[y0:y1+1,x0:x1+1][mask]=d[mask]
    result=Image.fromarray(image);draw=ImageDraw.Draw(result)
    if source:
        x,y,_=screen(np.array([[0.,2.25,0.]]))[0]
        draw.ellipse((x-7,y-7,x+7,y+7),outline=(250,80,210),width=2)
        draw.line((x-11,y,x+11,y),fill=(250,80,210))
    return result


def board(frames, textures, target, source=False):
    width,height=400,480
    image=Image.new("RGB",(width*len(frames),height*2))
    for row,angle in enumerate((0,-35)):
        all_points=np.concatenate([project(p,angle) for _,polys in frames for p,_,_ in polys])
        lower,upper=all_points[:,:2].min(0),all_points[:,:2].max(0)
        for column,(label,polys) in enumerate(frames):
            panel=raster(polys,textures,angle,(lower,upper),width,height,source)
            ImageDraw.Draw(panel).text((12,12),label+(" / front" if row==0 else " / oblique"),fill="white")
            image.paste(panel,(column*width,row*height))
    image.save(target)
    print(target)


def main():
    asset=ROOT/"neoforge-vfx"/ASSETS
    out=ROOT/"build/duel112-pose-tests";out.mkdir(exist_ok=True,parents=True)
    actor=json.loads((ROOT/"build/yuta-geto-finale-poses.json").read_text(encoding="utf8"))
    textures=[np.array(Image.open(asset/("textures/entity/"+name+".png")).convert("RGBA"))
              for name in ("yuta_cat","yuta_details","geto_cat","geto_cat_details")]
    examples=[("Vow / ring",23,20),("Yuta command",24,40),("Yuta recoil",24,118),
              ("Geto charge",104,110),("Geto defeated",105,20),("Geto retreat",106,65)]
    frames=[]
    for label,action,age in examples:
        f=next(f for f in actor if f["action"]==action and f["age"]==age);polys=[]
        for face in f["faces"]:
            v=np.array(face["vertices"]);mat=face["material"]+(0 if action<100 else 2)
            polys.append((v[:,:3]*[1.5,-1.5,1.5]+[0,1.501*1.5,0],
                          v[:,3:]*[textures[mat].shape[1],textures[mat].shape[0]],mat))
        frames.append((f"{label} / {age}t",polys))
    board(frames,textures,out/"actors-finale-production.png")
    rika=json.loads((ROOT/"build/rika-finale.json").read_text(encoding="utf8"))
    textures=[np.array(Image.open(asset/"textures/entity/rika_dance.png").convert("RGBA"))]
    frames=[]
    for name,age in [("vow",35),("charge",55),("clash",12),("clash",45),("breakthrough",20),("recovery",32)]:
        f=next(f for f in rika if f["clip"]==name and f["age"]==age);polys=[]
        for face in f["faces"]:
            v=np.array(face)
            polys.append((v[:,:3]*[1,-1,1],v[:,3:]*[64,96],0))
        frames.append((f"Rika {name} / {age}t",polys))
    board(frames,textures,out/"rika-finale-production.png",source=True)


if __name__=="__main__":
    main()

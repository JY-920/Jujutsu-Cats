"""Authored low-poly katana surfaces, material swatches and editable Blockbench meshes.
The same vertices feed the cat, domain swords, previews and .bbmodel export.
"""
from pathlib import Path
import math,json,uuid,base64,io
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
COLORS={'steel_back':'394759','steel_flat':'748798','steel_face':'B8C7D1','edge':'F0F5F4',
 'edge_shadow':'CAD7DE','ridge':'DFE8EB','gold':'CDB544','gold_light':'F5E08B','gold_shadow':'807035',
 'grip_base':'A72034','wrap':'C52C3E','wrap_lit':'E44751','wrap_dark':'7D142C','seam':'481521'}
palette=Image.new('RGBA',(32,32),(0,0,0,255));uv={}
for i,(name,color) in enumerate(COLORS.items()):
 x=(i%8)*4;y=(i//8)*4;palette.paste('#'+color,(x,y,x+4,y+4));uv[name]=((x+2)/32,(y+2)/32)
parts={}
def face(name,color,points):
 points=list(dict.fromkeys(points))
 if len(points)<3:return
 assert len(points) in (3,4)
 if len(points)==3:points=points+[points[-1]]
 parts.setdefault(name,[]).append([[*p,*uv[color]] for p in points])
def cross(t):
 y=.132+.885*t;x=.072*t*t
 width=.033*(1-.20*t)
 if t>.88:width*=max(0,(1-t)/.12)
 depth=.0085*(1-.45*t)
 if t>.92:depth*=max(0,(1-t)/.08)
 return [(x-width,y,-depth*.45),(x-.003*(1-t),y,-depth),(x+width,y,-depth*.06),
         (x+width,y,depth*.06),(x-.003*(1-t),y,depth),(x-width,y,depth*.45)]
steps=[i/20 for i in range(20)]+[.97,1]
tones=['steel_flat','edge','edge_shadow','edge','steel_face','steel_back']
for lo,hi in zip(steps,steps[1:]):
 a,b=cross(lo),cross(hi)
 for j in range(6):face('curved_blade',tones[j],[a[j],b[j],b[(j+1)%6],a[(j+1)%6]])
# The bright longitudinal ridge is a physical narrow facet, not an emissive white billboard.
for lo,hi in zip(steps[:-1],steps[1:-1]):
 for sign in [-1,1]:
  points=[]
  for t,d in [(lo,-.0015),(hi,-.0015),(hi,.0015),(lo,.0015)]:
   points.append((.072*t*t-.003*(1-t)+d,.132+.885*t,sign*(.0085*(1-.45*t)+.00018)))
  face('polished_ridge','ridge',points)
def oval(name,rings,tones,n=16,cap=True):
 for i in range(n):
  a=i*2*math.pi/n;b=(i+1)*2*math.pi/n
  for k,(r,s) in enumerate(zip(rings,rings[1:])):
   y,rx,rz=r;yy,sx,sz=s
   face(name,tones[k%len(tones)],[(rx*math.cos(a),y,rz*math.sin(a)),(sx*math.cos(a),yy,sz*math.sin(a)),(sx*math.cos(b),yy,sz*math.sin(b)),(rx*math.cos(b),y,rz*math.sin(b))])
 if cap:
  for end in [0,-1]:
   y,rx,rz=rings[end]
   for i in range(n):
    a=i*2*math.pi/n;b=(i+1)*2*math.pi/n
    points=[(0,y,0),(rx*math.cos(a),y,rz*math.sin(a)),(rx*math.cos(b),y,rz*math.sin(b))]
    face(name,tones[0],points if end==0 else points[::-1])
oval('red_lacquer_handle',[(-.170,.028,.020),(-.157,.032,.024),(.085,.032,.024),(.095,.028,.020)],['grip_base'],12)
oval('gold_pommel',[(-.177,.026,.018),(-.173,.034,.025),(-.155,.034,.025),(-.151,.032,.023)],['gold_shadow','gold','gold_light'],12)
oval('gold_fuchi',[(.077,.031,.024),(.083,.037,.028),(.101,.037,.028),(.105,.033,.025)],['gold_shadow','gold','gold_light'],12)
oval('bevelled_guard',[(.100,.098,.057),(.104,.108,.065),(.113,.108,.065),(.117,.098,.057)],['gold_shadow','gold','gold_light'],24)
oval('habaki_collar',[(.117,.037,.014),(.124,.039,.016),(.149,.037,.014)],['gold_light','gold'],8)
# Raised red tsukamaki bands cross over a recessed near-black red handle.
for direction in [-1,1]:
 for k in range(90):
  points=[]
  for t,offset in [(k/90,-.008),( (k+1)/90,-.008),((k+1)/90,.008),(k/90,.008)]:
   angle=direction*t*math.pi*8+(.4 if direction==1 else math.pi-.4);y=-.151+t*.232+offset
   radius=.0011+(.00065 if direction==1 else 0)
   points.append(((.032+radius)*math.cos(angle),y,(.024+radius)*math.sin(angle)))
  face('crossed_red_wrap','wrap_lit' if direction==1 else 'wrap',points)
  # Slim dark hem makes the crossings legible without a high-resolution bitmap.
  hem=[(x*1.012,y+.0152,z*1.012) for x,y,z in points[:2]]+[(x*1.012,y+.0163,z*1.012) for x,y,z in points[:2][::-1]]
  face('wrap_seams','seam',hem)
def diamonds(detailed):
 for sign in [-1,1]:
  for i in range(6):
   y=-.132+i*.036
   def ring(size,lift):
    return [(x,y+dy,sign*(.0259*math.sqrt(max(.05,1-(x/.035)**2))+lift)) for x,dy in [(0,-.014*size),(.019*size,0),(0,.014*size),(-.019*size,0)]]
   outer,inner=ring(1,.0027),ring(.78,.0031)
   if detailed:
    for j in range(4):face('diamond_wrap_edges','seam',[outer[j],outer[(j+1)%4],inner[(j+1)%4],inner[j]])
   else:face('diamond_wrap_edges','seam',outer)
   face('diamond_wrap_inlays','wrap_dark',inner)
diamonds(True)
mesh=[f for faces in parts.values() for f in faces]
assert len(mesh)<1000 and all(math.isfinite(v) for f in mesh for p in f for v in p)
buf=io.BytesIO();palette.save(buf,format='PNG');png=buf.getvalue()
elements=[]
for name,faces in parts.items():
 vertices={};bbfaces={}
 for i,f in enumerate(faces):
  keys=[];tex={}
  # Triangular caps use three distinct vertices in the editable mesh.
  for j,p in enumerate(f):
   if j==3 and p==f[2]:continue
   key=f'v{i}_{j}';vertices[key]=[round(v*16,6) for v in p[:3]];tex[key]=[p[3]*32,p[4]*32];keys.append(key)
  bbfaces[str(i)]={'vertices':keys,'uv':tex,'texture':0}
 elements.append({'name':name,'type':'mesh','uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,'jujutsu-yuta-katana/'+name)),
  'origin':[0,0,0],'rotation':[0,0,0],'vertices':vertices,'faces':bbfaces,'visibility':True,'export':True,'color':0})
model={'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':'Yuta katana - red wrapped curved steel',
 'resolution':{'width':32,'height':32},'elements':elements,'outliner':[e['uuid'] for e in elements],
 'textures':[{'name':'yuta_katana.png','uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,'yuta-katana-palette')), 'id':'0','width':32,'height':32,
  'uv_width':32,'uv_height':32,'source':'data:image/png;base64,'+base64.b64encode(png).decode()}]}
# Complete lower-detail swords for the large field: never truncate a high-detail mesh.
parts={}
for i in range(8):
 a,b=cross(i/8),cross((i+1)/8)
 for j in range(6):face('blade',tones[j],[a[j],b[j],b[(j+1)%6],a[(j+1)%6]])
oval('handle',[(-.160,.032,.024),(.087,.032,.024)],['grip_base'],8)
oval('pommel',[(-.177,.034,.025),(-.151,.034,.025)],['gold'],8)
oval('fuchi',[(.077,.037,.028),(.104,.037,.028)],['gold'],8)
oval('guard',[(.101,.098,.057),(.108,.108,.065),(.117,.098,.057)],['gold','gold_light'],8)
oval('habaki',[(.117,.038,.015),(.149,.037,.014)],['gold_light'],6)
for direction in [-1,1]:
 for k in range(24):
  points=[]
  for t,o in [(k/24,-.008),((k+1)/24,-.008),((k+1)/24,.008),(k/24,.008)]:
   angle=direction*t*math.pi*8+(.4 if direction==1 else math.pi-.4)
   points.append((.0337*math.cos(angle),-.151+t*.232+o,.0257*math.sin(angle)))
  face('wrap','wrap_lit' if direction==1 else 'wrap',points)
diamonds(False)
field=[f for faces in parts.values() for f in faces]
assert len(field)<=256
for loader in ['neoforge','forge']:
 base=ROOT/(loader+'-vfx/src/main/resources/assets/jujutsuvfx')
 (base/'meshes/yuta_katana.json').write_text(json.dumps(mesh,separators=(',',':')),encoding='utf-8')
 (base/'meshes/yuta_katana_field.json').write_text(json.dumps(field,separators=(',',':')),encoding='utf-8')
 (base/'models/entity/yuta_katana.bbmodel').write_text(json.dumps(model,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
 (base/'textures/entity/yuta_katana.png').write_bytes(png)
out=ROOT/'exports/weapons-0.108.1';out.mkdir(parents=True,exist_ok=True)
(out/'乙骨刀.bbmodel').write_text(json.dumps(model,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
print('Yuta katana:',len(mesh),'detail quads;',len(field),'field quads; dual loader + editable Blockbench export')

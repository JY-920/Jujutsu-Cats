import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {randomUUID} from 'node:crypto';
import {deflateSync} from 'node:zlib';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const art=path.join(root,'art/malevolent-shrine');
const resource=path.join(root,'src/main/resources/assets/jujutsuvfx/models/domain');
fs.mkdirSync(art,{recursive:true});fs.mkdirSync(resource,{recursive:true});
const sections=new Map();let section;
const C={bone:0xf0e5c7,boneLight:0xfff4dd,boneShade:0xc7b28d,gum:0xb88070,
 wood:0xc44931,woodLight:0xe57b4b,woodDark:0x582d2b,roof:0x3c6975,tile:0x669ba6,
 edge:0xcd995b,horn:0x607c87,hornLight:0x829daa,void:0x08080a,fold:0x382528};
const add=(a,b)=>a.map((v,i)=>v+b[i]),sub=(a,b)=>a.map((v,i)=>v-b[i]),mul=(a,s)=>a.map(v=>v*s);
const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
const norm=a=>mul(a,1/(Math.hypot(...a)||1));
const lerp=(a,b,t)=>add(mul(a,1-t),mul(b,t));
const round=a=>a.map(v=>Math.round(v*1e5)/1e5);
function group(name){section=[];sections.set(name,section);}
function tri(a,b,c,color,mat=0){if(Math.hypot(...cross(sub(b,a),sub(c,a)))>1e-8)section.push({p:[round(a),round(b),round(c)],color,mat});}
function quad(a,b,c,d,col,mat=0){tri(a,b,c,col,mat);tri(a,c,d,col,mat);}
function box(x,y,z,w,h,d,col){const p=[[x,y,z],[x+w,y,z],[x+w,y+h,z],[x,y+h,z],[x,y,z+d],[x+w,y,z+d],[x+w,y+h,z+d],[x,y+h,z+d]];for(const f of [[0,3,2,1],[4,5,6,7],[0,4,7,3],[1,2,6,5],[3,7,6,2],[0,1,5,4]])quad(...f.map(i=>p[i]),col);}
function tube(points,radii,col,sides=8,rib=false){
 let previousU;
 const rings=points.map((p,i)=>{const dir=norm(sub(points[Math.min(i+1,points.length-1)],points[Math.max(0,i-1)]));const u=previousU?norm(sub(previousU,mul(dir,previousU.reduce((s,v,j)=>s+v*dir[j],0)))):norm(cross(dir,Math.abs(dir[1])<.95?[0,1,0]:[1,0,0]));previousU=u;const v=cross(dir,u);const r=Array.isArray(radii)?radii[i]:radii;return Array.from({length:sides},(_,j)=>add(p,add(mul(u,Math.cos(j*2*Math.PI/sides)*r),mul(v,Math.sin(j*2*Math.PI/sides)*r))));});
 for(let i=0;i<rings.length-1;i++)for(let j=0;j<sides;j++)quad(rings[i][j],rings[i][(j+1)%sides],rings[i+1][(j+1)%sides],rings[i+1][j],rib&&i%3===0?C.hornLight:col);
 for(let j=0;j<sides;j++){tri(points[0],rings[0][(j+1)%sides],rings[0][j],col);tri(points.at(-1),rings.at(-1)[j],rings.at(-1)[(j+1)%sides],col);}
}
function ellipsoid(p,s,col,n=10,m=7,mat=0){const q=(i,j)=>add(p,[Math.sin(i*Math.PI/m)*Math.cos(j*2*Math.PI/n)*s[0],Math.cos(i*Math.PI/m)*s[1],Math.sin(i*Math.PI/m)*Math.sin(j*2*Math.PI/n)*s[2]]);for(let i=0;i<m;i++)for(let j=0;j<n;j++)quad(q(i,j),q(i+1,j),q(i+1,j+1),q(i,j+1),col,mat);}
function bezier(points,t){let a=points;while(a.length>1)a=a.slice(0,-1).map((_,i)=>lerp(a[i],a[i+1],t));return a[0];}
function curve(points,r,col,steps=16,sides=8,taper=true,rib=false){const ps=Array.from({length:steps+1},(_,i)=>bezier(points,i/steps));const rs=ps.map((_,i)=>r*(taper?Math.pow(1-i/steps,.78):1)*(rib?(i%2?.94:1.025):1)+.002);tube(ps,rs,col,sides,rib);}
function segment(a,b,r,col){tube([a,b],[r,r],col,6);}
function transform(p,origin,scale,yaw=0,tilt=0){const y=p[1]*Math.cos(tilt)-p[2]*Math.sin(tilt),z=p[1]*Math.sin(tilt)+p[2]*Math.cos(tilt);return add(origin,mul([p[0]*Math.cos(yaw)+z*Math.sin(yaw),y,-p[0]*Math.sin(yaw)+z*Math.cos(yaw)],scale));}
function local(origin,scale,yaw,tilt,fn){const start=section.length;fn();for(let i=start;i<section.length;i++)section[i].p=section[i].p.map(p=>round(transform(p,origin,scale,yaw,tilt)));}

// Bovine skull: separate brow and cheek arches expose deep eye sockets;
// a tapered nasal bridge and split nasal cavity avoid a human/block skull silhouette.
function skull(origin,scale,yaw=0,tilt=0,horns=true){local(origin,scale,yaw,tilt,()=>{
 ellipsoid([0,.18,-.08],[.66,.48,.43],C.bone,10,6);
 ellipsoid([0,-.1,.35],[.30,.4,.59],C.boneLight,8,5);
 quad([-.25,.26,.57],[.25,.26,.57],[.13,-.40,.96],[-.13,-.40,.96],C.boneLight);
 for(const s of [-1,1]){
  ellipsoid([s*.46,.13,.33],[.245,.19,.115],C.void,8,5,2);
  curve([[s*.16,.32,.43],[s*.52,.48,.48],[s*.82,.26,.22],[s*.61,-.16,.37]],.095,C.boneLight,8,6,false);
  quad([s*.56,-.07,.42],[s*.65,-.2,.38],[s*.25,-.5,.84],[s*.22,-.31,.82],C.bone);
  curve([[s*.58,-.12,.33],[s*.48,-.42,.51],[s*.3,-.56,.82]],.075,C.bone,6,6,false);
  curve([[s*.16,.12,.66],[s*.19,-.28,.97],[s*.08,-.62,1.08]],.11,C.boneLight,6,6,true);
  ellipsoid([s*.12,-.4,.92],[.105,.2,.06],C.void,6,5,2);
  if(horns)curve([[s*.56,.42,-.12],[s*1.28,.67,-.3],[s*1.33,1.46,-.46],[s*.97,1.88,-.35]],.245,C.horn,14,7,true,true);
 }
 curve([[0,.5,.27],[.035,.21,.63],[-.02,-.09,.78]],.018,C.boneShade,5,5,false);
 for(let i=0;i<6;i++){const x=(i-2.5)*.063;ellipsoid([x,-.58,1.05],[.039,.082,.057],i%2?C.bone:C.boneLight,5,4);}
});}

// Projecting jaws meet at recessed lateral hinges. Upper and lower incisors
// lean in opposite depth directions, following the opening of a real mouth.
function mouth(origin,yaw,w=1.83,h=1.9,sideMaw=false){local(origin,1,yaw,0,()=>{
 const count=64;
 const projection=a=>.10+(Math.sin(a)>=0?(sideMaw?.65:.84):1.12)*Math.pow(Math.abs(Math.sin(a)),.72);
 const ring=(rx,ry,z,flare=0)=>Array.from({length:count},(_,i)=>{const a=i*2*Math.PI/count;return [Math.cos(a)*rx,Math.sin(a)*ry,z+flare*projection(a)+.035*Math.sin(a*3)];});
 const rings=[ring(w*.96,h*.96,.04,.94),ring(w*.81,h*.83,-.17,.24),ring(w*.56,h*.65,-.67),ring(w*.31,h*.40,-1.16)];
 for(let k=0;k<3;k++)for(let j=0;j<count;j++)quad(rings[k][j],rings[k][(j+1)%count],rings[k+1][(j+1)%count],rings[k+1][j],k===0?C.fold:C.void,2);
 for(let j=0;j<count;j++)tri([0,0,-1.18],rings[3][j],rings[3][(j+1)%count],C.void,2);
 for(let k=0;k<6;k++){const ps=ring(w*(.88-k*.115),h*(.89-k*.092),-.12-k*.16).map((p,j)=>[p[0]+.04*Math.sin(j*.4+k),p[1]+.04*Math.cos(j*.5+k),p[2]+.07]);tube([...ps,ps[0]],.023+(5-k)*.006,k%2?0x282526:0x3b3531,6);tube(ps.slice(9+k,22+k),.019,0x62594e,5);}
 const jawFront=ring(w,h,.02,1),jawBack=ring(w*1.04,h*1.025,-.19);
 const end=sideMaw?count/2:count;
 for(let j=0;j<end;j++){
  const k=(j+1)%count;
  quad(jawBack[j],jawBack[k],jawFront[k],jawFront[j],C.gum);
 }
 const gum=sideMaw?jawFront.slice(0,count/2+1):jawFront;
 tube(sideMaw?gum:[...gum,gum[0]],gum.map((_,i)=>.15+.09*Math.abs(Math.sin(i*2*Math.PI/count))).concat([.15]),C.gum,10);
 // Longitudinal gingival ridges make the projecting palate read as a solid jaw.
 for(const upper of (sideMaw?[true]:[true,false]))for(let j=0;j<15;j++){
  const a=(upper?.17:Math.PI+.17)+j*(Math.PI-.34)/14;
  const ca=Math.cos(a),sa=Math.sin(a);
  curve([[ca*w*1.04,sa*h*1.025,-.16],[ca*w*1.045,sa*h*1.05,projection(a)*.35],[ca*w,sa*h,projection(a)+.10]],.026,0xcf9b84,7,5,false);
 }
 for(const upper of (sideMaw?[true]:[true,false]))for(let i=0;i<11;i++){
  const a=(upper?.34:Math.PI+.34)+i*(Math.PI-.68)/10;
  const x=Math.cos(a)*w,y=Math.sin(a)*h;
  const len=(upper?.76:.60)*(1+.10*Math.sin(i*2.7));
  const rr=.12+.15*Math.abs(Math.sin(a));
  const inward=norm([-x*.16,-Math.sign(y),upper?.72:-.67]);
  const rootZ=projection(a)+.13;
  const ts=[0,.12,.35,.64,.86,1];const ps=ts.map(t=>[x+inward[0]*len*t,y+inward[1]*len*t,rootZ+inward[2]*len*t+.05*Math.sin(t*Math.PI)]);
  tube(ps,[rr*.85,rr,rr,rr*.94,rr*.70,.025],i%3===0?C.bone:C.boneLight,10);
 }
 // Larger canine teeth at both lateral hinges.
 for(const s of [-1,1])curve([[s*w*.94,h*.40,.55],[s*w*1.01,-h*.01,.85],[s*w*.78,-h*.40,.61]],.19,C.bone,11,8,true);
});}

function build(){
 group('01_不规则骨骸基座');
 ellipsoid([0,.25,0],[4.4,.4,3.75],C.woodDark,24,5);
 for(let i=0;i<27;i++){const a=i*2.39996,r=1.7+2.1*Math.sqrt((i+.5)/27);ellipsoid([Math.cos(a)*r,.3+(i%4)*.055,Math.sin(a)*r*.83],[.52,.23,.62],i%3?C.boneShade:C.bone,8,4);}
 group('02_四根木柱与枋梁');
 for(const x of [-2.34,2.34])for(const z of [-1.96,1.96]){
  box(x-.31,.47,z-.31,.62,.28,.62,C.woodDark);
  tube([[x,.7,z],[x,5.12,z]],[.285,.255],C.wood,8);
  for(let i=0;i<12;i++){const a=i*Math.PI/6;curve([[x+Math.cos(a)*.278,.86,z+Math.sin(a)*.278],[x+Math.cos(a+.045)*.29,2.7,z+Math.sin(a+.045)*.29],[x+Math.cos(a)*.26,4.89,z+Math.sin(a)*.26]],.014,i%3?C.woodLight:C.woodDark,4,4,false);}
  for(const y of [1.13,1.25,4.41,4.54]){tube([[x,y,z],[x,y+.1,z]],[.33,.33],C.woodDark,8);}
  box(x-.39,4.82,z-.39,.78,.25,.78,C.woodLight);
  for(const side of [-1,1])curve([[x+side*.08,4.48,z],[x+side*.67,4.51,z],[x+side*.74,5.03,z]],.09,C.wood,7,6,false);
 }
 for(const z of [-1.98,1.98]){box(-2.82,4.94,z-.18,5.64,.29,.36,C.wood);box(-2.83,5.27,z-.24,5.66,.13,.48,C.woodDark);}
 for(const x of [-2.34,2.34])box(x-.18,4.98,-2.43,.36,.26,4.86,C.wood);
 group('03_柱头骷髅雕刻');for(const x of [-2.34,2.34])for(const z of [-1.96,1.96])skull([x,4.75,z+.2*Math.sign(z)],.30,z>0?0:Math.PI,0,false);
 group('04_正面深腔巨口');mouth([0,2.73,2.0],0,1.87,1.93);
 group('05_背面巨口');mouth([0,2.73,-2.0],Math.PI,1.87,1.93);
 group('06_侧面悬垂牙颚');
 for(const s of [-1,1]){mouth([s*2.18,3.72,0],s*Math.PI/2,1.37,.94,true);}

 // A single broad hip roof; the front gable is part of it, not another storey.
 group('07_单层曲面大屋顶');
 const roof=(t,u,side)=>[u*(1.15+3.5*t),6.87-2.10*t+.87*t*t+Math.pow(Math.abs(u),5)*.77*Math.pow(t,3),side*3.4*t];
 for(const s of [-1,1]){
  for(let i=0;i<14;i++)for(let j=0;j<32;j++){const t=i/14,t2=(i+1)/14,u=-1+j/16,u2=u+1/16;quad(roof(t,u,s),roof(t2,u,s),roof(t2,u2,s),roof(t,u2,s),(i+j)%5===0?C.tile:C.roof);}
 }
 const hip=(t,u,s)=>[s*(1.15+3.5*t),6.87-2.1*t+.87*t*t+.77*Math.abs(u)**5*t*t*t,3.4*t*u];
 for(const s of [-1,1])for(let i=0;i<12;i++)for(let j=0;j<24;j++){const t=i/12,u=-1+j/12;quad(hip(t,u,s),hip((i+1)/12,u,s),hip((i+1)/12,u+1/12,s),hip(t,u+1/12,s),C.roof);}
 group('08_瓦垄瓦当与翘檐');
 for(const s of [-1,1])for(let j=0;j<=26;j++){const ps=Array.from({length:13},(_,i)=>add(hip(i/12,-1+j/13,s),[0,.045,0]));tube(ps,.032,C.tile,5);}
 for(const s of [-1,1]){
  for(let j=0;j<=32;j++){const u=-1+j/16;const ps=Array.from({length:13},(_,i)=>add(roof(i/12,u,s),[0,.043,0]));tube(ps,.032,C.tile,5);}
  const eave=Array.from({length:41},(_,i)=>roof(1,-1+i/20,s));
  for(const dy of [0,-.13,-.28])tube(eave.map(p=>add(p,[0,dy,0])),dy===-.13?.12:.07,dy===-.13?C.woodDark:C.edge,6);
  for(let j=0;j<49;j++){const p=roof(1,-1+j/24,s);tube([add(p,[0,-.07,-s*.045]),add(p,[0,-.07,s*.1])],[.055,.055],C.tile,8);}
  for(let i=1;i<=5;i++){const t=i/6;const ps=Array.from({length:25},(_,j)=>add(roof(t,-1+j/12,s),[0,.009,0]));tube(ps,.017,C.woodDark,4);}
 }
 for(const s of [-1,1]){const edge=Array.from({length:25},(_,i)=>[s*4.65,5.64+.77*Math.abs(-1+i/12)**5,3.4*(-1+i/12)]);tube(edge,.09,C.edge,6);}
 tube([[-1.3,6.93,0],[1.3,6.93,0]],.13,C.edge,8);
 group('09_檐下椽木斗拱');
 for(const s of [-1,1])for(let i=0;i<39;i++){
  const u=-1+i/19,outer=roof(1,u,s),inner=roof(.75,u,s);
  segment(add(inner,[0,-.28,0]),add(outer,[0,-.24,0]),.047,C.woodLight);
  const p=lerp(inner,outer,.70);segment(add(p,[0,-.34,0]),add(p,[0,-.08,0]),.045,C.woodLight);
 }
 for(const s of [-1,1])for(let j=0;j<27;j++){const z=-3.15+j*.243,y=5.64+.77*(Math.abs(z)/3.4)**5;segment([s*3.14,y-.47,z],[s*4.61,y-.14,z],.05,C.woodLight);}
 for(const z of [-2.5,2.5])for(let i=0;i<25;i++)box(-3.05+i*.25,5.08,z-.06,.08,.33,.12,C.woodLight);

 group('10_正背三角破风');
 for(const s of [-1,1]){
  const z=s*1.82;
  tri([-1.6,5.87,z],[1.6,5.87,z],[0,7.04,z],C.woodDark);
  for(const shift of [0,.13]){segment([-1.68,5.87+shift,z+s*.04],[0,7.08+shift,z+s*.04],.083,C.edge);segment([0,7.08+shift,z+s*.04],[1.68,5.87+shift,z+s*.04],.083,C.edge);}
  for(const x of [-1,1]){quad([x*1.6,5.93,z],[0,7.08,z],[0,6.85,s*.3],[x*1.15,6.35,s*.3],C.roof);}
  skull([0,6.13,z+s*.12],.33,s===1?0:Math.PI,0,false);
  for(const x of [-1,1])curve([[x*.32,6.06,z],[x*.84,6.1,z+s*.03],[x*.98,6.35,z]],.10,C.boneShade,8,6,true);
 }
 group('11_屋顶巨大弯角');
 for(const s of [-1,1])for(const f of [-1,1]){
  curve([[s*2.55,5.92,f*2.28],[s*3.5,6.6,f*2.33],[s*3.65,7.65,f*1.97],[s*3.19,8.38,f*1.72]],.34,C.horn,22,9,true,true);
  curve([[s*1.34,6.3,f*.45],[s*1.69,6.78,f*.44],[s*1.96,7.8,f*.35],[s*1.68,8.08,f*.30]],.25,C.horn,18,8,true,true);
 }
 group('12_屋檐骨饰与悬挂头骨');
 for(const s of [-1,1]){
  for(let i=0;i<13;i++){const p=roof(.97,-.94+i*.156,s);ellipsoid(add(p,[0,.07,0]),[.115,.1,.15],C.boneShade,7,4);}
  for(const x of [-1,1]){const p=roof(1,x,s);skull(add(p,[0,.07,s*.08]),.27,s>0?0:Math.PI,0,false);curve([add(p,[0,-.04,0]),add(p,[x*.1,-.5,s*.13]),add(p,[x*.11,-.62,s*.08])],.023,C.boneShade,7,5,false);skull(add(p,[x*.11,-.71,s*.08]),.21,s>0?0:Math.PI,0,false);}
 }
 group('13_基座前景牛骨');
 const arrangement=[[-2.8,.91,3.2,.90,-.38,.34],[-.92,.7,3.48,.72,.17,.46],[1.06,.96,3.19,.93,-.2,.31],[3.13,.69,2.8,.78,.52,.46],[-3.69,.67,1.52,.9,-.92,.35],[3.62,.9,.91,.91,1.02,.33],[-3.32,.78,-1.05,.93,-1.88,.42],[3.47,.72,-1.6,.87,2,.25],[-2.27,.71,-2.86,.88,-2.89,.45],[0,.96,-3.1,.99,Math.PI,.30],[2.22,.72,-2.89,.84,2.6,.41]];
 for(const [x,y,z,sc,yaw,tilt]of arrangement)skull([x,y,z],sc,yaw,tilt);
 group('14_散落小骨与鼻梁');
 for(let i=0;i<9;i++){const a=i*2.399,r=2.4+(i%3)*.4;skull([Math.cos(a)*r,.65+(i%2)*.28,Math.sin(a)*r],.44+(i%3)*.08,a,.65,true);}
 group('15_穿出骨堆的大弯角');
 for(let i=0;i<9;i++){const a=(i+.2)*Math.PI*2/9,px=Math.cos(a),pz=Math.sin(a),r=3.4+(i%2)*.38;curve([[px*r,.25,pz*r*.87],[px*(r+1.3),.75,pz*(r+1.1)*.87],[px*(r+.75),2.4+(i%3)*.25,pz*(r+.4)*.87],[px*(r-.1),3.36+(i%2)*.55,pz*(r-.3)*.87]],.29+(i%3)*.035,C.horn,22,8,true,true);}
 group('16_枝状骨角与缠绕筋骨');
 for(const x of [-1,1])for(const z of [-1,1]){
  const p=[x*2.66,1.26,z*2.3];curve([p,[x*3.25,1.95,z*2.47],[x*3.1,2.87,z*2.66],[x*3.53,3.36,z*2.58]],.10,C.woodDark,14,6,true);
  curve([[x*3.1,2.33,z*2.5],[x*3.78,2.46,z*2.83],[x*4.04,2.95,z*2.95]],.075,C.woodDark,10,6,true);
  curve([[x*3.25,2.83,z*2.63],[x*2.74,3.13,z*2.9],[x*2.77,3.65,z*2.86]],.069,C.woodDark,10,6,true);
  curve([[x*1.52,.97,z*2.4],[x*2.72,1.18,z*2.4],[x*2.63,2.03,z*1.81],[x*2.25,2.2,z*2.16]],.085,C.boneShade,15,6,true);
 }
}

function crc32(b){let c=0xffffffff;for(const v of b){c^=v;for(let k=0;k<8;k++)c=(c>>>1)^((c&1)?0xedb88320:0);}return(c^0xffffffff)>>>0;}
function png(colors){const w=256,h=Math.ceil(colors.length/32)*8;const raw=Buffer.alloc((w*4+1)*h);for(let y=0;y<h;y++)for(let x=0;x<w;x++){const col=colors[Math.floor(y/8)*32+Math.floor(x/8)]||0;const p=y*(w*4+1)+1+x*4;raw[p]=col>>16&255;raw[p+1]=col>>8&255;raw[p+2]=col&255;raw[p+3]=255;}const chunk=(type,data)=>{const b=Buffer.alloc(data.length+12);b.writeUInt32BE(data.length);b.write(type,4);data.copy(b,8);b.writeUInt32BE(crc32(b.subarray(4,-4)),b.length-4);return b;};const ih=Buffer.alloc(13);ih.writeUInt32BE(w);ih.writeUInt32BE(h,4);ih[8]=8;ih[9]=6;return {w,h,bytes:Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',ih),chunk('IDAT',deflateSync(raw)),chunk('IEND',Buffer.alloc(0))])};}
function exportModel(name,binaryPath){
 const triangles=[...sections.values()].flat();const colors=[...new Set(triangles.map(t=>t.color))];const palette=png(colors);const texUUID=randomUUID();
 const model={meta:{format_version:'5.0',model_format:'free',box_uv:false},name,model_identifier:'malevolent_shrine',visible_box:[0,0,0],resolution:{width:palette.w,height:palette.h},elements:[],outliner:[],textures:[{path:'shrine_palette.png',name:'shrine_palette.png',id:'0',uuid:texUUID,render_mode:'default',render_sides:'double',mode:'bitmap',saved:true,source:'data:image/png;base64,'+palette.bytes.toString('base64')}],animation_variable_placeholders:''};
 for(const [name,ts]of sections){const verts={},faces={},keys=new Map();let n=0;
  const key=p=>{const k=p.join(',');if(!keys.has(k)){const id='v'+n++;keys.set(k,id);verts[id]=p.map(v=>v*16);}return keys.get(k);};
  ts.forEach((t,i)=>{const ids=t.p.map(key),col=colors.indexOf(t.color),uv=[(col%32)*8+4,Math.floor(col/32)*8+4];faces['f'+i]={vertices:ids,uv:Object.fromEntries(ids.map(id=>[id,uv])),texture:0};});
  const id=randomUUID();model.elements.push({name,type:'mesh',uuid:id,origin:[0,0,0],rotation:[0,0,0],color:0,visibility:true,autouv:0,vertices:verts,faces});model.outliner.push({name,origin:[0,0,0],rotation:[0,0,0],uuid:randomUUID(),export:true,isOpen:false,children:[id]});
 }
 fs.writeFileSync(path.join(art,name+'.bbmodel'),JSON.stringify(model));fs.writeFileSync(path.join(art,'shrine_palette.png'),palette.bytes);
 const buffer=Buffer.alloc(12+triangles.length*3*20);buffer.writeUInt32BE(0x5348524e,0);buffer.writeUInt32BE(1,4);buffer.writeUInt32BE(triangles.length*3,8);let offset=12;const bounds={min:[Infinity,Infinity,Infinity],max:[-Infinity,-Infinity,-Infinity]};
 for(const t of triangles){const normal=norm(cross(sub(t.p[1],t.p[0]),sub(t.p[2],t.p[0])));const light=t.mat===2?1:.82+.18*Math.abs(normal[0]*.35+normal[1]*.82+normal[2]*.45);const col=(0xff000000|Math.round((t.color>>16&255)*light)<<16|Math.round((t.color>>8&255)*light)<<8|Math.round((t.color&255)*light))>>>0;
  for(const p of t.p){for(let j=0;j<3;j++){if(!Number.isFinite(p[j]))throw Error('Invalid position');bounds.min[j]=Math.min(bounds.min[j],p[j]);bounds.max[j]=Math.max(bounds.max[j],p[j]);buffer.writeFloatBE(p[j],offset+j*4);}buffer.writeUInt32BE(col,offset+12);buffer.writeFloatBE(t.mat,offset+16);offset+=20;}
 }
 if(binaryPath)fs.writeFileSync(binaryPath,buffer);
 const info={name,triangles:triangles.length,vertices:triangles.length*3,bytes:buffer.length,bounds,sections:Object.fromEntries([...sections].map(([k,v])=>[k,v.length])),palette:colors.length};fs.writeFileSync(path.join(art,name+'.json'),JSON.stringify(info,null,2));console.log(JSON.stringify(info));
}
if(process.argv.includes('--baseline')){const b=fs.readFileSync(path.join(art,'baseline/original.mesh'));group('旧版神龛');for(let o=12;o<b.length;o+=60){const ps=[];for(let i=0;i<3;i++)ps.push([b.readFloatBE(o+i*20),b.readFloatBE(o+i*20+4),b.readFloatBE(o+i*20+8)]);tri(...ps,b.readUInt32BE(o+12)&0xffffff,b.readFloatBE(o+16));}exportModel('baseline-shrine');}
else {build();exportModel('伏魔御厨子_漫画还原',path.join(resource,'malevolent_shrine.mesh'));}

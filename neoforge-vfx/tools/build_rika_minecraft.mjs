import fs from 'node:fs/promises';
import path from 'node:path';
import {randomUUID as uuid} from 'node:crypto';
import {deflateSync} from 'node:zlib';
const art=path.resolve('neoforge-vfx/art/rika-minecraft');
await fs.mkdir(art+'/previews',{recursive:true});
const W=128,H=128,rgba=Buffer.alloc(W*H*4);
const rgb=h=>[h>>16&255,h>>8&255,h&255];
function pixel(x,y,c){if(x<0||y<0||x>=W||y>=H)return;const k=(y*W+x)*4;rgba[k]=c[0];rgba[k+1]=c[1];rgba[k+2]=c[2];rgba[k+3]=255;}
function rect(x,y,w,h,c){for(let yy=y;yy<y+h;yy++)for(let xx=x;xx<x+w;xx++)pixel(xx,yy,rgb(c));}
function hash(x,y){let n=Math.imul(x+93,374761393)^Math.imul(y+31,668265263);n=Math.imul(n^(n>>>13),1274126177);return (n^(n>>>16))>>>0;}
const mats={bone:[0,0,32,0xc5cbd4],plate:[32,0,32,0xdde1e7],shade:[64,0,32,0x939bad],tooth:[96,0,16,0xeee9d6],claw:[112,0,16,0xb6b6c6],dark:[0,32,32,0x23232f],rib:[32,32,32,0xc2c8d1],mouth:[64,32,32,0x17131e],gum:[96,32,16,0x785264],tongue:[112,32,16,0x5d3d60],tendril:[0,64,32,0xcbd2da],transition:[32,64,32,0xd0d5dc],shadow:[64,64,32,0x56566c],darkTip:[96,64,32,0x353445]};
rect(0,0,128,128,0x242430);
for(const [name,[tx,ty,size,col]] of Object.entries(mats)){
 const base=rgb(col);
 for(let y=0;y<size;y++)for(let x=0;x<size;x++){
  const n=hash(Math.floor(x/2)+tx,Math.floor(y/3)+ty),noise=(n%9)-4;
  let shade=noise;
  if(['mouth','gum','tongue'].includes(name))shade=Math.floor(noise/2);
  if(name==='rib'||name==='bone')shade+=(x%13===1&&y%8<5)?-9:0;
  if(name==='plate')shade+=(x===0||x===size-1)?-14:(x%8===3&&y%10<7?5:0);
  pixel(tx+x,ty+y,base.map(v=>Math.max(0,Math.min(255,v+shade))));
 }
 if(name==='tendril')for(const [x,y] of [[3,5],[20,17],[12,27]]){
  rect(tx+x,ty+y,3,5,0x454454);rect(tx+x+1,ty+y-1,2,7,0x454454);
 }
 if(name==='transition')for(let x=0;x<size;x++){
  const edge=15+hash(Math.floor(x/3),9)%9;
  for(let y=edge;y<size;y++)pixel(tx+x,ty+y,rgb(y<edge+2?0x5e6173:0x252532));
 }
}
const crcTable=Array.from({length:256},(_,n)=>{for(let i=0;i<8;i++)n=n&1?0xedb88320^(n>>>1):n>>>1;return n>>>0;});
function chunk(tag,data){const t=Buffer.from(tag),body=Buffer.concat([t,data]);let c=0xffffffff;for(const b of body)c=crcTable[(c^b)&255]^(c>>>8);const out=Buffer.alloc(body.length+8);out.writeUInt32BE(data.length);body.copy(out,4);out.writeUInt32BE((c^0xffffffff)>>>0,out.length-4);return out;}
const ih=Buffer.alloc(13);ih.writeUInt32BE(W);ih.writeUInt32BE(H,4);ih[8]=8;ih[9]=6;
const raw=Buffer.alloc((W*4+1)*H);for(let y=0;y<H;y++)rgba.copy(raw,y*(W*4+1)+1,y*W*4,(y+1)*W*4);
const png=Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',ih),chunk('IDAT',deflateSync(raw)),chunk('IEND',Buffer.alloc(0))]);
await fs.writeFile(art+'/rika.png',png);
const elements=[],groups=[],outliner=[],bones=new Map(),animations=[];
function bone(name,parent,origin,rotation=[0,0,0]){
 const b={name,origin,rotation,uuid:uuid(),export:true,visibility:true,isOpen:false,children:[]};
 bones.set(name,b);groups.push(b);(parent?bones.get(parent).children:outliner).push(b);return b;
}
function cube(name,b,from,to,mat='bone',rot=[0,0,0],pivot=null){
 const [tx,ty,size]=mats[mat],d=to.map((v,i)=>v-from[i]),index=elements.length;
 const faceDims={north:[d[0],d[1]],south:[d[0],d[1]],east:[d[2],d[1]],west:[d[2],d[1]],up:[d[0],d[2]],down:[d[0],d[2]]};
 const faces={};for(const [face,[fw,fh]]of Object.entries(faceDims)){
  const width=Math.max(1,Math.round(fw)),height=Math.max(1,Math.round(fh));
  const u=tx+hash(index,face.length)%Math.max(1,size-width),v=ty+hash(index+7,face.length)%Math.max(1,size-height);
  faces[face]={uv:[u,v,Math.min(tx+size,u+width),Math.min(ty+size,v+height)],texture:0};
 }
 const c={name,type:'cube',uuid:uuid(),from,to,origin:pivot||from.map((v,i)=>(v+to[i])/2),rotation:rot,box_uv:false,autouv:0,inflate:0,shade:true,mirror_uv:false,color:0,visibility:true,export:true,faces};
 elements.push(c);bones.get(b).children.push(c.uuid);return c;
}
function box(name,b,x,y,z,w,h,d,mat='bone',rot=[0,0,0],pivot){return cube(name,b,[x,y,z],[x+w,y+h,z+d],mat,rot,pivot);}
function yz(name,b,x,y1,z1,y2,z2,w,d,mat='bone'){
 const len=Math.hypot(y2-y1,z2-z1),y=(y1+y2)/2,z=(z1+z2)/2;
 return box(name,b,x-w/2,y-len/2,z-d/2,w,len,d,mat,[Math.atan2(z2-z1,y2-y1)*180/Math.PI,0,0],[x,y,z]);
}
function xy(name,b,x1,y1,x2,y2,z,w,d,mat='bone'){
 const len=Math.hypot(x2-x1,y2-y1),x=(x1+x2)/2,y=(y1+y2)/2;
 return box(name,b,x-w/2,y-len/2,z-d/2,w,len,d,mat,[0,0,-Math.atan2(x2-x1,y2-y1)*180/Math.PI],[x,y,z]);
}
bone('root',null,[0,0,0]);bone('body','root',[0,30,2],[-6,0,0]);
box('waist_dark_transition','body',-5,27,-2,10,12,9,'transition');
box('ribcage_core','body',-8,35,-5,16,16,11,'shade');
box('upper_chest','body',-10,46,-6,20,9,12,'bone');
box('shoulder_back','body',-10,48,4,20,7,4,'rib');
box('sternum','body',-1.5,36,-6.3,3,14,2,'plate');
for(const side of [-1,1]){
 for(let i=0;i<4;i++){
  const width=8-i*.8,y=43-i*3;
  box('rib_'+side+'_'+i,'body',side<0?-width-1:1,y,-6.8,width,1.8,3.2,'rib',[0,0,side*10]);
 }
 box('clavicle_'+side,'body',side<0?-11:1,50,-6.8,10,3,3,'plate',[0,0,side*8]);
 box('scapula_'+side,'body',side<0?-9:1,41,5.1,8,10,3,'bone',[0,0,side*-12]);
}
bone('neck','body',[0,52,1]);box('neck','neck',-4,50,-7,8,9,9,'shade',[18,0,0]);
bone('head','neck',[0,57,-6],[6,0,0]);
box('cranium','head',-7,59,-15,14,11,11,'bone');
box('upper_gums','head',-7,61.5,-20,14,1.2,7,'gum');
box('mouth_back','head',-6,47,-12.5,12,15.5,2,'mouth');
for(const side of [-1,1]){
 box('temple_'+side,'head',side<0?-9:6,59,-17,3,9,10,'bone',[0,0,side*-12]);
 box('mouth_side_'+side,'head',side<0?-7.5:5.5,48.5,-16.7,2,13.5,5,'shade',[0,0,side*-6]);
}
// Broad segmented hood hides the eye in the standard movie manifestation.
for(let i=-2;i<=2;i++){
 const x=i*3.25,cy=64+Math.abs(i)*.35;
 box('forehead_plate_'+i,'head',x-1.8,cy,-20.5+Math.abs(i)*.6,3.6,7.5-Math.abs(i)*.6,6,'plate',[0,0,-i*5]);
 box('plate_crown_'+i,'head',x-1.7,70.7-Math.abs(i)*.4,-18.3+Math.abs(i)*.4,3.4,2,6,'plate');
}
box('hood_back','head',-6,68,-10.5,12,3.5,6.5,'bone');
for(let i=0;i<7;i++){
 const x=-6.25+i*2.08,len=i%2?4.3:3.3;
 box('upper_fang_'+i,'head',x-.58,61-len,-20.05,1.16,len,1.5,'tooth',[18,0,-x*1.8],[x,62,-19]);
 box('upper_fang_tip_'+i,'head',x-.33,60.1-len,-20.05,.66,1.2,1,'tooth',[18,0,-x*1.8],[x,62,-19]);
}
bone('jaw','head',[0,57,-10]);
box('lower_jaw','jaw',-5,45.5,-20.5,10,2.5,10,'bone');
box('lower_lip','jaw',-5.2,47.5,-20.7,10.4,1.2,4.2,'gum');
for(const s of [-1,1]){
 box('jaw_corner_'+s,'jaw',s<0?-7:5,47,-19,2,4.5,6,'bone',[0,0,s*-12]);
 yz('jaw_hinge_'+s,'jaw',s*6.3,49.5,-14,58,-10.5,2,2.5,'shade');
}
for(let i=0;i<6;i++){
 const x=-4.9+i*1.96,len=i%2?2.6:3.5;
 box('lower_fang_'+i,'jaw',x-.55,48.5,-20.5,1.1,len,1.25,'tooth',[-16,0,x*1.8],[x,48.5,-20]);
 box('lower_fang_tip_'+i,'jaw',x-.3,48.3+len,-20.5,.6,1,1,'tooth',[-16,0,x*1.8],[x,48.5,-20]);
}
bone('tongue','jaw',[0,48.5,-13]);box('tongue','tongue',-2,48.2,-18,4,1,5.5,'tongue',[-12,0,0]);
// Looping, spotted appendages form the recognizable crown behind the shell.
for(let i=-3;i<=3;i++){
 const name='tendril_'+(i+3),x=i*3.6,top=81-Math.abs(i)*1.1;
 bone(name,'head',[x,56,3],[0,0,-i*8]);
 yz(name+'_root',name,x,54,5,63,9,3.2,3.4,'transition');
 yz(name+'_rise',name,x,62,9,top,7,3.2,3.4,'tendril');
 yz(name+'_arc',name,x,top,7,top+1,0,3.2,3.4,'tendril');
 yz(name+'_tip',name,x,top+1,0,top-5,-3,2.6,2.8,'tendril');
}
// Arms are split at shoulder, elbow, wrist and fingers for usable animation.
for(const s of [-1,1]){
 const side=s<0?'right':'left',shoulder=s*11,elbow=s*18;
 bone(side+'_arm','body',[shoulder,50,1]);
 box(side+'_shoulder',side+'_arm',s<0?-16:9,46,-4,7,8,9,'bone',[0,0,s*12]);
 xy(side+'_upper_arm',side+'_arm',shoulder,47,elbow,36,0,5.5,6.5,'bone');
 box(side+'_elbow',side+'_arm',elbow-2.8,33.5,-3.2,5.6,5.6,6.4,'shade');
 bone(side+'_forearm',side+'_arm',[elbow,36,0],[s>0?58:15,0,s*5]);
 box(side+'_forearm',side+'_forearm',elbow-2.5,21.5,-2.6,5,15,5.2,'bone');
 box(side+'_forearm_ridge',side+'_forearm',elbow-1.1,23,-3.3,2.2,12,1.4,'plate');
 bone(side+'_hand',side+'_forearm',[elbow,22,0]);
 box(side+'_palm',side+'_hand',elbow-3.2,16,-2.8,6.4,6,5.6,'bone');
 bone(side+'_fingers',side+'_hand',[elbow,17,-.2]);
 for(let i=0;i<4;i++){
  const x=elbow-2.8+i*1.85,len=(i===0||i===3)?5.5:7;
  box(side+'_finger_'+i,side+'_fingers',x-.55,16-len,-2.7,1.3,len,2.2,'bone',[0,0,(i-1.5)*-5],[x,17,-1.5]);
  box(side+'_claw_'+i,side+'_fingers',x-.55,14.1-len,-3.7,1.2,2.4,2.2,'claw',[-24,0,0],[x,16-len,-1.6]);
 }
 box(side+'_thumb',side+'_hand',elbow+s*3-.85,16.4,-3.2,1.7,5.3,2.2,'bone',[0,0,s*-38],[elbow+s*3,21,-2]);
 box(side+'_thumb_claw',side+'_hand',elbow+s*5-.6,14.8,-3.7,1.2,2.2,2,'claw',[0,0,s*-38],[elbow+s*5,17,-2]);
}
bone('tail_base','root',[0,30,3]);
yz('tail_upper','tail_base',0,31,3,21,8,9,8,'dark');
bone('tail_middle','tail_base',[0,21,8]);
yz('tail_middle','tail_middle',0,22,8,12,8,7.5,7.5,'dark');
yz('tail_bend','tail_middle',0,14,8,7,2,7.5,7,'dark');
bone('tail_lower','tail_middle',[0,8,2]);
box('tail_sweep_1','tail_lower',-3.5,3,-3,12,6,8,'dark');
box('tail_sweep_2','tail_lower',6,3,-1.5,10,5.5,7,'darkTip',[0,-12,0]);
bone('tail_tip','tail_lower',[14,6,2]);
xy('tail_curl_1','tail_tip',13,5,20,9,2,4.8,5,'dark');
xy('tail_curl_2','tail_tip',20,8.5,21.5,14,3,3.5,3.5,'darkTip');
xy('tail_curl_3','tail_tip',21.5,14,18.5,17,4,2,2,'dark');
// Floating ragged streamers continue the black lower silhouette without adding legs.
bone('wisp_left','tail_middle',[-3,14,8]);
yz('wisp_left_1','wisp_left',-5,14,7,7,11,2.5,3,'darkTip');
yz('wisp_left_2','wisp_left',-5,7,11,4,18,2.5,2,'dark');
bone('wisp_right','tail_middle',[3,14,8]);
yz('wisp_right_1','wisp_right',5,15,8,9,13,2.5,3,'darkTip');
yz('wisp_right_2','wisp_right',5,9,13,7,19,2,2,'dark');

function clip(name,length,loop,tracks){
 const animators={};
 for(const [n,channels]of Object.entries(tracks)){
  const b=bones.get(n);if(!b)throw Error(n);
  const keyframes=[];for(const [channel,frames]of Object.entries(channels))for(const [time,x,y,z]of frames)keyframes.push({uuid:uuid(),channel,time,data_points:[{x:String(x),y:String(y),z:String(z)}],interpolation:'catmullrom',color:-1});
  animators[b.uuid]={name:n,type:'bone',rotation_global:false,keyframes};
 }
 animations.push({uuid:uuid(),name:'animation.rika.'+name,loop:loop?'loop':'once',override:false,length,snapping:24,selected:false,animators});
}
const idle={root:{position:[[0,0,0,0],[1.5,0,.75,0],[3,0,0,0]]},body:{rotation:[[0,-1,0,-1],[1.5,1,0,1],[3,-1,0,-1]]},head:{rotation:[[0,0,-2,0],[1.5,2,2,0],[3,0,-2,0]]},jaw:{rotation:[[0,0,0,0],[1.5,-4,0,0],[3,0,0,0]]},right_arm:{rotation:[[0,0,0,-2],[1.5,3,0,-5],[3,0,0,-2]]},left_arm:{rotation:[[0,3,0,5],[1.5,0,0,2],[3,3,0,5]]},tail_middle:{rotation:[[0,0,0,-2],[1.5,2,0,2],[3,0,0,-2]]},tail_tip:{rotation:[[0,0,-4,0],[1.5,0,4,0],[3,0,-4,0]]},wisp_left:{rotation:[[0,2,0,0],[1.5,-5,0,0],[3,2,0,0]]},wisp_right:{rotation:[[0,-4,0,0],[1.5,3,0,0],[3,-4,0,0]]}};
for(let i=0;i<7;i++)idle['tendril_'+i]={rotation:[[0,-2,0,0],[1.5,2,0,0],[3,-2,0,0]]};
clip('idle',3,true,idle);
const roar={body:{rotation:[[0,0,0,0],[.4,-8,0,0],[.8,5,0,0],[1.6,0,0,0]]},head:{rotation:[[0,0,0,0],[.4,13,0,0],[.8,3,0,0],[1.6,0,0,0]]},jaw:{rotation:[[0,0,0,0],[.35,-25,0,0],[1.05,-21,0,0],[1.6,0,0,0]]},tongue:{rotation:[[0,0,0,0],[.55,-12,0,0],[1.6,0,0,0]]},right_arm:{rotation:[[0,0,0,0],[.4,16,0,-23],[1.1,10,0,-18],[1.6,0,0,0]]},left_arm:{rotation:[[0,0,0,0],[.4,16,0,23],[1.1,10,0,18],[1.6,0,0,0]]}};
clip('roar',1.6,false,roar);
clip('swipe',1.1,false,{body:{rotation:[[0,0,0,0],[.22,0,-14,3],[.5,5,18,-4],[1.1,0,0,0]]},right_arm:{rotation:[[0,0,0,0],[.22,28,-15,-30],[.48,75,20,18],[.72,48,10,8],[1.1,0,0,0]]},right_forearm:{rotation:[[0,0,0,0],[.22,20,0,0],[.48,8,0,0],[1.1,0,0,0]]},right_fingers:{rotation:[[0,0,0,0],[.25,30,0,0],[.7,25,0,0],[1.1,0,0,0]]},head:{rotation:[[0,0,0,0],[.22,0,10,0],[.5,-3,-12,0],[1.1,0,0,0]]},jaw:{rotation:[[0,0,0,0],[.5,-12,0,0],[1.1,0,0,0]]}});

const model={meta:{format_version:'4.10',model_format:'bedrock',box_uv:false},name:'里香_MC风格',model_identifier:'rika_cursed_spirit',visible_box:[7,6,2.5],resolution:{width:W,height:H},elements,outliner,textures:[{name:'rika.png',id:'0',uuid:uuid(),width:W,height:H,uv_width:W,uv_height:H,source:'data:image/png;base64,'+png.toString('base64'),mode:'bitmap',internal:true,saved:true,render_mode:'default',render_sides:'auto'}],animations};
await fs.writeFile(art+'/里香_MC风格.bbmodel',JSON.stringify(model,null,2));
await fs.writeFile(art+'/model-stats.json',JSON.stringify({cubes:elements.length,bones:bones.size,texture:[W,H],animations:animations.map(a=>({name:a.name,length:a.length,loop:a.loop})),scale:'16 model units = 1 Minecraft block',front:'-Z'},null,2));
console.log(JSON.stringify({file:art+'/里香_MC风格.bbmodel',cubes:elements.length,bones:bones.size,animations:animations.length}));

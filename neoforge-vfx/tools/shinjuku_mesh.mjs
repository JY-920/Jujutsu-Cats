import fs from 'node:fs';
import {randomUUID} from 'node:crypto';
import {deflateSync} from 'node:zlib';
const add=(a,b)=>a.map((v,i)=>v+b[i]), sub=(a,b)=>a.map((v,i)=>v-b[i]), mul=(a,s)=>a.map(v=>v*s);
const dot=(a,b)=>a.reduce((s,v,i)=>s+v*b[i],0),cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
const norm=a=>mul(a,1/(Math.hypot(...a)||1));
const q=p=>p.map(v=>Math.round(v*1e5)/1e5);
function crc32(b){let c=0xffffffff;for(const v of b){c^=v;for(let k=0;k<8;k++)c=(c>>>1)^((c&1)?0xedb88320:0);}return(c^0xffffffff)>>>0;}
function palettePng(colors){const w=256,h=Math.max(8,Math.ceil(colors.length/32)*8),raw=Buffer.alloc((w*4+1)*h);for(let y=0;y<h;y++)for(let x=0;x<w;x++){const c=colors[Math.floor(y/8)*32+Math.floor(x/8)]||0,p=y*(w*4+1)+1+x*4;raw[p]=c>>16&255;raw[p+1]=c>>8&255;raw[p+2]=c&255;raw[p+3]=255;}const chunk=(t,d)=>{const b=Buffer.alloc(d.length+12);b.writeUInt32BE(d.length);b.write(t,4);d.copy(b,8);b.writeUInt32BE(crc32(b.subarray(4,-4)),b.length-4);return b;};const ih=Buffer.alloc(13);ih.writeUInt32BE(w);ih.writeUInt32BE(h,4);ih[8]=8;ih[9]=6;return {w,h,data:Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',ih),chunk('IDAT',deflateSync(raw)),chunk('IEND',Buffer.alloc(0))])};}

export class CharacterMesh {
 constructor(id,name){this.id=id;this.name=name;this.bones=[];this.animations={};}
 bone(name,parent,origin,rotation=[0,0,0]){if(this.bones.some(b=>b.name===name))throw Error('Duplicate bone '+name);this.current={name,parent,origin,rotation,uuid:randomUUID(),triangles:[]};this.bones.push(this.current);return this;}
 select(name){this.current=this.bones.find(b=>b.name===name);if(!this.current)throw Error('No bone '+name);return this;}
 tri(a,b,c,col){if(Math.hypot(...cross(sub(b,a),sub(c,a)))>1e-7)this.current.triangles.push({p:[q(a),q(b),q(c)],col});}
 quad(a,b,c,d,col){this.tri(a,b,c,col);this.tri(a,c,d,col);}
 box(x,y,z,w,h,d,col){const p=[[x,y,z],[x+w,y,z],[x+w,y+h,z],[x,y+h,z],[x,y,z+d],[x+w,y,z+d],[x+w,y+h,z+d],[x,y+h,z+d]];for(const f of [[0,3,2,1],[4,5,6,7],[0,4,7,3],[1,2,6,5],[3,7,6,2],[0,1,5,4]])this.quad(...f.map(i=>p[i]),col);}
 ellipsoid(p,s,col,n=12,m=8){const v=(i,j)=>add(p,[Math.sin(i*Math.PI/m)*Math.cos(j*2*Math.PI/n)*s[0],Math.cos(i*Math.PI/m)*s[1],Math.sin(i*Math.PI/m)*Math.sin(j*2*Math.PI/n)*s[2]]);for(let i=0;i<m;i++)for(let j=0;j<n;j++)this.quad(v(i,j),v(i,j+1),v(i+1,j+1),v(i+1,j),col);}
 tube(points,radii,col,sides=8){let previousU;const rings=points.map((p,i)=>{const dir=norm(sub(points[Math.min(i+1,points.length-1)],points[Math.max(0,i-1)]));const u=previousU?norm(sub(previousU,mul(dir,dot(previousU,dir)))):norm(cross(dir,Math.abs(dir[1])<.95?[0,1,0]:[1,0,0]));previousU=u;const v=cross(dir,u),r=Array.isArray(radii)?radii[i]:radii;return Array.from({length:sides},(_,j)=>add(p,add(mul(u,Math.cos(j*Math.PI*2/sides)*r),mul(v,Math.sin(j*Math.PI*2/sides)*r))));});for(let i=0;i<rings.length-1;i++)for(let j=0;j<sides;j++)this.quad(rings[i][j],rings[i+1][j],rings[i+1][(j+1)%sides],rings[i][(j+1)%sides],col);for(let j=0;j<sides;j++){this.tri(points[0],rings[0][j],rings[0][(j+1)%sides],col);this.tri(points.at(-1),rings.at(-1)[(j+1)%sides],rings.at(-1)[j],col);}}
 curve(points,r,col,steps=12,sides=8,taper=true){const ps=Array.from({length:steps+1},(_,i)=>{let a=points;const t=i/steps;while(a.length>1)a=a.slice(0,-1).map((_,k)=>add(mul(a[k],1-t),mul(a[k+1],t)));return a[0];});this.tube(ps,ps.map((_,i)=>r*(taper?Math.pow(1-i/steps,.8):1)+.001),col,sides);}
 clip(name,length,loop,tracks){this.animations[name]={length,loop,tracks};return this;}
 export(art,resources){
  const colors=[...new Set(this.bones.flatMap(b=>b.triangles.map(t=>t.col)))],png=palettePng(colors);
  const colorUV=c=>{const i=colors.indexOf(c);return [(i%32)*8+4,Math.floor(i/32)*8+4];};
  const runtime={format:1,texture:[png.w,png.h],bones:this.bones.map(b=>({name:b.name,parent:b.parent,origin:b.origin,rotation:b.rotation,triangles:b.triangles.map(t=>({p:t.p,uv:t.p.map(()=>{const uv=colorUV(t.col);return [uv[0]/png.w,uv[1]/png.h];})}))})),animations:this.animations};
  const bb={meta:{format_version:'5.0',model_format:'free',box_uv:false},name:this.name,model_identifier:this.id,resolution:{width:png.w,height:png.h},elements:[],outliner:[],animations:[],textures:[{id:'0',uuid:randomUUID(),name:this.id+'.png',path:this.id+'.png',render_mode:'default',render_sides:'double',mode:'bitmap',saved:true,source:'data:image/png;base64,'+png.data.toString('base64')}]};
  const outlines=new Map();
  for(const b of this.bones){const g={name:b.name,uuid:b.uuid,origin:b.origin,rotation:b.rotation,export:true,isOpen:false,children:[]};outlines.set(b.name,g);if(b.parent)outlines.get(b.parent).children.push(g);else bb.outliner.push(g);
   if(!b.triangles.length)continue;const vertices={},faces={},keys=new Map();let n=0;const key=p=>{const str=p.join(',');if(!keys.has(str)){keys.set(str,'v'+n);vertices['v'+n++]=p;}return keys.get(str);};
   b.triangles.forEach((t,i)=>{const ids=t.p.map(key);faces['f'+i]={vertices:ids,uv:Object.fromEntries(ids.map(id=>[id,colorUV(t.col)])),texture:0};});const uuid=randomUUID();bb.elements.push({name:b.name+'_mesh',type:'mesh',uuid,origin:[0,0,0],rotation:[0,0,0],visibility:true,vertices,faces});g.children.push(uuid);
  }
  for(const [name,c]of Object.entries(this.animations)){const clip={name:'animation.'+this.id+'.'+name,uuid:randomUUID(),length:c.length,loop:c.loop?'loop':'once',override:false,animators:{}};
   for(const [bone,tracks]of Object.entries(c.tracks)){const b=this.bones.find(b=>b.name===bone);if(!b)throw Error('Unknown animated bone '+bone);const keyframes=[];for(const [channel,keys]of Object.entries(tracks))for(const [time,x,y,z]of keys)keyframes.push({channel,uuid:randomUUID(),time,color:-1,interpolation:'linear',data_points:[{x:String(x),y:String(y),z:String(z)}]});clip.animators[b.uuid]={name:bone,type:'bone',keyframes};}bb.animations.push(clip);
  }
  fs.mkdirSync(art,{recursive:true});fs.mkdirSync(resources+'/models/entity/shinjuku',{recursive:true});fs.mkdirSync(resources+'/textures/entity/shinjuku',{recursive:true});
  fs.writeFileSync(art+'/'+this.id+'.bbmodel',JSON.stringify(bb));fs.writeFileSync(art+'/'+this.id+'.png',png.data);fs.writeFileSync(art+'/'+this.id+'.animations.json',JSON.stringify(this.animations,null,2));fs.writeFileSync(resources+'/models/entity/shinjuku/'+this.id+'.json',JSON.stringify(runtime));fs.writeFileSync(resources+'/textures/entity/shinjuku/'+this.id+'.png',png.data);
  const stats={id:this.id,name:this.name,bones:this.bones.length,triangles:this.bones.reduce((n,b)=>n+b.triangles.length,0),animations:Object.keys(this.animations),palette:colors.length,bounds:{min:[0,1,2].map(j=>Math.min(...this.bones.flatMap(b=>b.triangles.flatMap(t=>t.p.map(p=>p[j]))))),max:[0,1,2].map(j=>Math.max(...this.bones.flatMap(b=>b.triangles.flatMap(t=>t.p.map(p=>p[j])))))}};fs.writeFileSync(art+'/'+this.id+'.stats.json',JSON.stringify(stats,null,2));return stats;
 }
}

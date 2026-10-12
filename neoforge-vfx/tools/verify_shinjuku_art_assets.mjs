import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';

const project=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const art=path.join(project,'art/shinjuku-characters');
const assets=path.join(project,'src/main/resources/assets/jujutsuvfx');
const sha=data=>createHash('sha256').update(data).digest('hex');
const must=(condition,message)=>{if(!condition)throw Error(message);};
const same=(a,b,label)=>must(JSON.stringify(a)===JSON.stringify(b),label);
const models=[];

for(const id of ['gojo_shinjuku','sukuna_shinjuku','mahoraga_shinjuku']){
 const bbBytes=fs.readFileSync(path.join(art,id+'.bbmodel'));
 const runtimeBytes=fs.readFileSync(path.join(assets,'models/entity/shinjuku',id+'.json'));
 const bb=JSON.parse(bbBytes),runtime=JSON.parse(runtimeBytes);
 must(bb.meta.format_version==='5.0'&&bb.meta.model_format==='free',id+': unexpected Blockbench format');
 const bones=new Map(),meshes=new Map(bb.elements.map(e=>[e.uuid,e]));
 const visit=(nodes,parent)=>{for(const n of nodes){if(typeof n==='string')continue;must(!bones.has(n.name),id+': duplicate bone');bones.set(n.name,{...n,parent});visit(n.children,n.name);}};
 visit(bb.outliner,null);
 must(bones.size===runtime.bones.length,id+': bone counts differ');
 let triangles=0,keys=0,restRotatedBones=0;
 for(const b of runtime.bones){
  const g=bones.get(b.name);must(g,id+': missing BB bone '+b.name);
  same(g.parent,b.parent,id+': parent mismatch '+b.name);
  same(g.origin,b.origin,id+': absolute pivot mismatch '+b.name);
  same(g.rotation,b.rotation,id+': rest rotation mismatch '+b.name);
  if(g.rotation.some(v=>v!==0))restRotatedBones++;
  const meshIds=g.children.filter(n=>typeof n==='string');
  const faces=[];
  for(const meshId of meshIds){
   const mesh=meshes.get(meshId);must(mesh?.type==='mesh',id+': missing bound mesh');
   same(mesh.origin,[0,0,0],id+': mesh origin must preserve absolute authored coordinates');
   same(mesh.rotation,[0,0,0],id+': unexpected mesh-only rotation');
   for(const face of Object.values(mesh.faces)){
    must(face.vertices.length===3,id+': expected triangle');
    faces.push({p:face.vertices.map(v=>mesh.vertices[v]),uv:face.vertices.map(v=>[face.uv[v][0]/runtime.texture[0],face.uv[v][1]/runtime.texture[1]])});
   }
  }
  same(faces,b.triangles,id+': bound absolute vertices or UV differ '+b.name);
  triangles+=faces.length;
 }
 for(const [name,clip]of Object.entries(runtime.animations)){
  const bc=bb.animations.find(a=>a.name==='animation.'+id+'.'+name);
  must(bc,id+': missing BB animation '+name);must(bc.length===clip.length,id+': animation length differs');
  must(bc.loop===(clip.loop?'loop':'once'),id+': animation loop differs');
  for(const [bone,tracks]of Object.entries(clip.tracks)){
   const ba=bc.animators[bones.get(bone).uuid];must(ba,id+': missing animator '+bone);
   for(const [channel,frames]of Object.entries(tracks)){
    const actual=ba.keyframes.filter(k=>k.channel===channel).map(k=>{
     must(k.interpolation==='linear',id+': unsupported interpolation');
     const p=k.data_points[0];return[k.time,Number(p.x),Number(p.y),Number(p.z)];
    });
    same(actual,frames,id+': keyframe mismatch '+name+'/'+bone+'/'+channel);keys+=actual.length;
   }
  }
 }
 const texture=fs.readFileSync(path.join(assets,'textures/entity/shinjuku',id+'.png'));
 must(sha(texture)===sha(fs.readFileSync(path.join(art,id+'.png'))),id+': deliverable texture differs');
 must(sha(texture)===sha(Buffer.from(bb.textures[0].source.split(',')[1],'base64')),id+': embedded BB texture differs');
 models.push({id,bones:bones.size,triangles,restRotatedBones,keyframes:keys,absoluteBoundGeometryMatches:true,animationsMatch:true,texturesMatch:true,runtimeSha256:sha(runtimeBytes),blockbenchSha256:sha(bbBytes)});
}
const report={review:'Blockbench free 5.0 absolute mesh points and bone hierarchy match runtime assets; local 5.1.6 source confirms positive animation channels and default ZYX rotation order.',models};
fs.writeFileSync(path.join(project,'build/shinjuku-art-assets-review.json'),JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report,null,2));

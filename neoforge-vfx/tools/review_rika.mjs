import fs from 'node:fs/promises';
import path from 'node:path';
import {call} from './rika_mcp.mjs';
const art=path.resolve('neoforge-vfx/art/rika-minecraft').replaceAll('\\','/');
const mode=process.argv[2]||'load';
if(mode==='load'||mode==='reload'){
 await call('create_project',{name:'里香_MC风格',format:'bedrock'});
 console.log(JSON.stringify(await call('risky_eval',{code:`Codecs.project.parse(JSON.parse(require("fs").readFileSync(${JSON.stringify(art+'/里香_MC风格.bbmodel')},"utf8"))); Project.save_path=${JSON.stringify(art+'/里香_MC风格.bbmodel')}; Project.saved=true; "loaded"`})));
}
const views=[['front_3q',[-105,75,-145],[0,39,0]],['front',[0,45,-180],[0,39,0]],['side',[-170,65,0],[0,39,0]],['back',[85,72,150],[0,39,0]],['head_close',[-46,68,-77],[0,59,-8]]];
for(const [name,position,target]of views){
 await call('set_camera_angle',{position,target,projection:'perspective'});
 const r=await call('capture_screenshot');const im=r.content.find(x=>x.type==='image');if(!im)throw Error(name);
 await fs.writeFile(art+'/previews/'+name+'.png',Buffer.from(im.data,'base64'));
}
await call('set_camera_angle',{position:views[0][1],target:views[0][2],projection:'perspective'});
console.log(JSON.stringify(await call('get_project_info')));

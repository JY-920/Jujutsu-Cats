import {call} from './rika_mcp.mjs';
import fs from 'node:fs/promises';
import path from 'node:path';
const art=path.resolve('neoforge-vfx/art/rika-minecraft').replaceAll('\\','/');
console.log(JSON.stringify(await call('risky_eval',{code:'JSON.stringify({animations:Animation.all.map(a=>({name:a.name,length:a.length,bones:Object.keys(a.animators).length})),close:ModelProject.prototype.close.toString().slice(0,4000)})'})));
await call('set_camera_angle',{position:[-96,74,-133],target:[0,40,0],projection:'perspective'});
for(const [name,times]of [['idle',[0,1.5,3]],['roar',[0,.4,.8,1.6]],['swipe',[0,.22,.48,.72,1.1]]]){
 await call('risky_eval',{code:`Modes.options.animate.select(); Animation.all.find(a=>a.name==='animation.rika.${name}').select(); "selected"`});
 for(const t of times){
  await call('animation_timeline',{action:'set_time',time:t});
  await call('risky_eval',{code:'Animator.preview(); "posed"'});
  const r=await call('capture_screenshot');const im=r.content.find(c=>c.type==='image');
  await fs.writeFile(`${art}/previews/${name}_${t.toString().replace('.','_')}.png`,Buffer.from(im.data,'base64'));
 }
}
await call('animation_timeline',{action:'stop'});
await call('risky_eval',{code:'Modes.options.edit.select(); "stopped"'});

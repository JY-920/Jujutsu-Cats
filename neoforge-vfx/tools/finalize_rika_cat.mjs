import { call } from './rika_mcp.mjs';
import fs from 'node:fs/promises';
import path from 'node:path';
const art = path.resolve('neoforge-vfx/art/rika-cat').replaceAll('\\', '/');
console.log(JSON.stringify(await call('risky_eval', { code: `JSON.stringify({reopened:true,cubes:Cube.all.length,bones:Group.all.length,textures:Texture.all.length,animations:Animation.all.map(a=>a.name),animationRoundtripMatches:JSON.stringify(AnimationCodec.codecs.bedrock.compileFile(Animation.all))===JSON.stringify(JSON.parse(require('fs').readFileSync(${JSON.stringify(art + '/rika_cat.animation.json')},'utf8'))),project:Project.uuid})` })));
const drafts = ['44193dbb-0bbb-3fcb-280d-7504b903b3ab', '179c2a1c-1bbb-a08e-b4c5-ef41b4708b4c', '43b31402-0cb8-363d-2e2f-13328d9b2546', 'd779d504-017a-6316-79fb-1d4db5e625e6', 'c7015a64-dede-404f-1c7d-c1a8acf9c5dc', '5f163bf7-5f33-aec2-97ef-6f1bc0f66299'];
// Closing a project during the MCP evaluator's automatic Undo.finishEdit changes
// the active Undo instance. Defer owned draft-tab cleanup until that edit ends.
if (process.argv.includes('--close-drafts')) {
  await call('risky_eval', { code: `(()=>{const keep=Project;setTimeout(async()=>{for(const id of ${JSON.stringify(drafts)}){const p=ModelProject.all.find(p=>p.uuid===id);if(p&&p!==keep)await p.close(true);}keep.select();},100);return "draft cleanup queued"})()` });
  await new Promise(resolve => setTimeout(resolve, 3000));
}
await call('set_camera_angle', { position: [-27, 20, -32], target: [0, 8, 1], projection: 'perspective' });
await call('risky_eval', { code: 'Modes.options.animate.select(); Animation.all.forEach(a=>a.playing=false); Animation.all.find(a=>a.name==="animation.rika_cat.roar").select(); Animation.selected.playing=true; "roar"' });
await call('animation_timeline', { action: 'set_time', time: .35 });
await call('risky_eval', { code: `Animator.preview(); Preview.selected.screenshot({crop:true,width:900,height:650},data=>require('fs').writeFileSync(${JSON.stringify(art + '/previews/里香猫_咆哮.png')},Buffer.from(data.split(',')[1],'base64'))); "roar captured"` });
await call('animation_timeline', { action: 'stop' });
await call('risky_eval', { code: 'Modes.options.edit.select(); Project.saved=true; "ready"' });
console.log(JSON.stringify(await call('get_project_info')));

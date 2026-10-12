import { call } from './rika_mcp.mjs';
import path from 'node:path';
import fs from 'node:fs/promises';
const art = path.resolve('neoforge-vfx/art/rika-cat').replaceAll('\\', '/');
await call('animation_timeline', { action: 'stop' });
await call('risky_eval', { code: 'Modes.options.edit.select(); "edit"' });
await call('list_export_formats', { only_current_format: true });
for (const [codec_id, file] of [['project', '里香猫_MC风格.bbmodel'], ['bedrock', 'rika_cat.geo.json']]) {
  console.log(JSON.stringify(await call('export_model', { codec_id, path: art + '/' + file, max_content_length: 0 })));
}
console.log(JSON.stringify(await call('risky_eval', { code: `require("fs").writeFileSync(${JSON.stringify(art + '/rika_cat.animation.json')},JSON.stringify(AnimationCodec.codecs.bedrock.compileFile(Animation.all),null,2)); Project.saved=true; JSON.stringify({animations:Animation.all.map(a=>({name:a.name,length:a.length})),project:Project.uuid})` })));
const views = [['里香猫_预览', [-27, 20, -32], [0, 8, 1]], ['里香猫_侧面', [-46, 16, 3], [0, 8, 3]], ['里香猫_背面', [30, 22, 36], [0, 8, 3]]];
for (const [name, position, target] of views) {
  await call('set_camera_angle', { position, target, projection: 'perspective' });
  await call('risky_eval', { code: `Preview.selected.screenshot({crop:true,width:900,height:650}, data=>require("fs").writeFileSync(${JSON.stringify(art + '/previews/' + name + '.png')},Buffer.from(data.split(',')[1],'base64'))); "captured"` });
}
await call('set_camera_angle', { position: views[0][1], target: views[0][2], projection: 'perspective' });
await fs.cp(path.resolve('neoforge-vfx/art/rika-minecraft/references'), art + '/references', { recursive: true });
console.log('Native Blockbench model, geometry, animation JSON, previews and references exported.');

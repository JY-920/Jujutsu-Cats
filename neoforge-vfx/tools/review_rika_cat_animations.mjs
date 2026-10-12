import { call } from './rika_mcp.mjs';
import fs from 'node:fs/promises';
import path from 'node:path';
const art = path.resolve('neoforge-vfx/art/rika-cat').replaceAll('\\', '/');
await call('set_camera_angle', { position: [-32, 21, -39], target: [0, 8, 0], projection: 'perspective' });
for (const [name, times] of [['idle', [0, 1.5, 3]], ['walk', [0, .2, .4, .6, .8]], ['roar', [0, .35, .7, 1.15, 1.6]]]) {
  await call('risky_eval', { code: `Modes.options.animate.select(); Animation.all.forEach(a=>a.playing=false); Animation.all.find(a=>a.name==='animation.rika_cat.${name}').select(); Animation.selected.playing=true; "selected"` });
  for (const t of times) {
    await call('animation_timeline', { action: 'set_time', time: t });
    await call('risky_eval', { code: 'Animator.preview(); "posed"' });
    const r = await call('capture_screenshot'); const im = r.content.find(c => c.type === 'image');
    await fs.writeFile(`${art}/previews/${name}_${t.toString().replace('.', '_')}.png`, Buffer.from(im.data, 'base64'));
  }
}
await call('animation_timeline', { action: 'stop' });
await call('risky_eval', { code: 'Modes.options.edit.select(); "stopped"' });
console.log('Inspected 13 key poses across idle, walk, roar.');

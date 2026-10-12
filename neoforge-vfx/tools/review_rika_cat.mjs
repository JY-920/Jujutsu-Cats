import fs from 'node:fs/promises';
import path from 'node:path';
import { call } from './rika_mcp.mjs';
const art = path.resolve('neoforge-vfx/art/rika-cat').replaceAll('\\', '/');
if (process.argv[2] !== 'views') {
  await call('create_project', { name: '里香猫_MC风格', format: 'bedrock' });
  console.log(JSON.stringify(await call('risky_eval', { code: `Codecs.project.parse(JSON.parse(require("fs").readFileSync(${JSON.stringify(art + '/里香猫_MC风格.bbmodel')},"utf8"))); Project.save_path=${JSON.stringify(art + '/里香猫_MC风格.bbmodel')}; Project.saved=true; "loaded"` })));
}
const views = [['front_3q', [-36, 25, -43], [0, 8, 1]], ['front', [0, 13, -52], [0, 8, 0]], ['side', [-57, 17, 3], [0, 8, 3]], ['back', [35, 27, 46], [0, 8, 2]], ['head_close', [-13, 15, -34], [0, 10, -9]]];
for (const [name, position, target] of views) {
  await call('set_camera_angle', { position, target, projection: 'perspective' });
  const r = await call('capture_screenshot'); const im = r.content.find(x => x.type === 'image'); if (!im) throw Error(name);
  await fs.writeFile(art + '/previews/' + name + '.png', Buffer.from(im.data, 'base64'));
}
await call('set_camera_angle', { position: views[0][1], target: views[0][2], projection: 'perspective' });
console.log(JSON.stringify(await call('get_project_info')));

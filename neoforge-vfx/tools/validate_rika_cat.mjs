import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
const art = path.resolve('neoforge-vfx/art/rika-cat');
const model = JSON.parse(await fs.readFile(art + '/里香猫_MC风格.bbmodel', 'utf8'));
const geo = JSON.parse(await fs.readFile(art + '/rika_cat.geo.json', 'utf8'))['minecraft:geometry'][0];
const anim = JSON.parse(await fs.readFile(art + '/rika_cat.animation.json', 'utf8')).animations;
const externalPng = await fs.readFile(art + '/rika_cat.png');
assert(Buffer.from(model.textures[0].source.split(',')[1], 'base64').equals(externalPng), 'Embedded and standalone textures differ');
assert.equal(externalPng.readUInt32BE(16), 128); assert.equal(externalPng.readUInt32BE(20), 128);
assert.equal(model.elements.length, 82); assert.equal(model.groups.length, 19);
assert.equal(new Set(model.elements.map(c => c.uuid)).size, model.elements.length);
const modelBones = new Set(model.groups.map(g => g.uuid));
const exportBones = new Set(geo.bones.map(g => g.name));
assert.equal(exportBones.size, geo.bones.length);
assert.equal(geo.bones.reduce((n, b) => n + (b.cubes?.length || 0), 0), 82);
for (const b of geo.bones) if (b.parent) assert(exportBones.has(b.parent), b.parent);
for (const c of model.elements) {
  assert(c.from.every((n, i) => Number.isFinite(n) && c.to[i] > n), c.name + ' invalid dimensions');
  for (const f of Object.values(c.faces)) {
    assert(f.texture === 0, c.name + ' missing texture');
    assert(f.uv.every(n => Number.isFinite(n) && n >= 0 && n <= 128), c.name + ' UV bounds');
  }
}
const near = (a, b) => Math.abs(Number(a) - Number(b)) < 1e-8;
for (const a of model.animations) {
  assert(anim[a.name], a.name + ' not exported');
  assert(near(anim[a.name].animation_length, a.length));
  for (const [boneId, track] of Object.entries(a.animators)) {
    assert(modelBones.has(boneId)); assert(exportBones.has(track.name));
    for (const channel of ['rotation', 'position', 'scale']) {
      const keys = (track.keyframes || []).filter(k => k.channel === channel).sort((a, b) => a.time - b.time);
      if (!keys.length) continue;
      assert(near(keys[0].time, 0), a.name + ' first key');
      assert(near(keys.at(-1).time, a.length), a.name + ' last key ' + track.name);
      for (const k of keys) assert(k.data_points.every(d => ['x', 'y', 'z'].every(axis => Number.isFinite(Number(d[axis])))));
      if (a.loop === 'loop') for (const axis of ['x', 'y', 'z']) assert(near(keys[0].data_points[0][axis], keys.at(-1).data_points[0][axis]), a.name + ' loop discontinuity');
    }
  }
}
const report = { status: 'passed', cubes: 82, bones: 19, texture: '128x128, embedded matches external PNG', animations: Object.keys(anim), checks: ['native model and exported geometry counts', 'valid dimensions and UVs', 'valid texture and bone references', 'finite animation values', 'animation endpoint times', 'matching loop endpoints'] };
await fs.writeFile(art + '/validation.json', JSON.stringify(report, null, 2));
console.log(JSON.stringify(report, null, 2));

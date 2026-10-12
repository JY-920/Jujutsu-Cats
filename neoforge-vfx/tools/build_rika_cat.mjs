import fs from 'node:fs/promises';
import path from 'node:path';
import { randomUUID as uuid } from 'node:crypto';
import { deflateSync } from 'node:zlib';

// Rika's movie design, adapted onto the dimensions of the user's vanilla cat.
// All geometry is cuboids; the atlas is authored here at native pixel resolution.
const art = path.resolve('neoforge-vfx/art/rika-cat');
await fs.mkdir(art + '/previews', { recursive: true });
const basePath = 'C:/Users/16611/Downloads/正常猫模型/vanilla_yellow_cat.bbmodel';
const base = JSON.parse(await fs.readFile(basePath, 'utf8'));
const W = 128, H = 128, pixels = Buffer.alloc(W * H * 4);
const rgb = c => [c >> 16 & 255, c >> 8 & 255, c & 255];
const clamp = n => Math.max(0, Math.min(255, n));
function hash(x, y) { let v = Math.imul(x + 91, 374761393) ^ Math.imul(y + 33, 668265263); v = Math.imul(v ^ v >>> 13, 1274126177); return (v ^ v >>> 16) >>> 0; }
function px(x, y, c) { const k = (y * W + x) * 4; const a = typeof c === 'number' ? rgb(c) : c; pixels[k] = a[0]; pixels[k + 1] = a[1]; pixels[k + 2] = a[2]; pixels[k + 3] = 255; }
function rect(x, y, w, h, c) { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) px(x + i, y + j, c); }
rect(0, 0, W, H, 0x292638);
const mats = {
  skin: [0, 0, 16, 0xc8cedc], hood: [16, 0, 16, 0xe1e6ec], shade: [32, 0, 16, 0xa1acc5],
  tooth: [48, 0, 16, 0xefe8d4], dark: [64, 0, 16, 0x292638], gum: [80, 0, 16, 0x9b637f],
  tongue: [96, 0, 16, 0x89579a], ear: [112, 0, 16, 0x977591],
  tail: [32, 32, 16, 0x333046], crown: [48, 32, 16, 0xd5dce5], tendril: [64, 32, 16, 0xc6cfdf],
  paw: [80, 32, 16, 0x615e7a], belly: [96, 32, 16, 0xaab2c6], mouth: [112, 32, 16, 0x292235]
};
for (const [name, [u, v, size, c]] of Object.entries(mats)) {
  const col = rgb(c);
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    let delta = hash(Math.floor(x / 2) + u, Math.floor(y / 2) + v) % 7 - 3;
    if (name === 'hood' && x % 5 === 0) delta -= 7;
    if (name === 'tail' && x % 5 === 1 && y % 7 > 1) delta += 9;
    px(u + x, v + y, col.map(n => clamp(n + delta)));
  }
}
// Body side: front at u=0, a restrained rib pattern and a dark rear fade.
const fade = [0xcbd2df, 0xcbd2df, 0xc3cad9, 0xc3cad9, 0xbfc7d7, 0xbfc7d7, 0xb2bbd0, 0xb2bbd0, 0xa8b2c9, 0xa8b2c9, 0x939db7, 0x858aa7, 0x72728f, 0x5c5a77, 0x48445e, 0x3b364e];
for (let y = 0; y < 6; y++) for (let x = 0; x < 16; x++) {
  let c = rgb(fade[x]);
  const rib = x >= 2 && x <= 10 && x % 3 === 2 && y >= 1 && y <= 4;
  c = c.map(n => clamp(n + (rib ? -30 : y === 0 ? 8 : y === 5 ? -16 : 0)));
  px(x, 32 + y, c);
}
for (let y = 0; y < 16; y++) for (let x = 0; x < 4; x++) {
  let c = rgb(fade[y]);
  if ((x === 1 || x === 2) && y < 12) c = c.map(n => clamp(n + 10));
  px(x, 64 + y, c);
}
// Front and hind legs with pixel stepped dark socks.
for (let side = 0; side < 2; side++) for (let y = 0; y < 10; y++) for (let x = 0; x < 8; x++) {
  const line = 7 + (x % 3 === 0 ? 1 : 0);
  let c = y >= line ? 0x655d79 : side ? 0xa0a8bf : 0xcbd1dc;
  if (y === 9) c = 0x4e465f;
  px(16 + side * 8 + x, 64 + y, c);
}
// Black oval marks on pale loop segments; allocated UVs below align them.
  rect(64, 56, 3, 6, 0xcbd4e1); rect(65, 57, 1, 3, 0x4d4964);

const crcTable = Array.from({ length: 256 }, (_, n) => { for (let i = 0; i < 8; i++) n = n & 1 ? 0xedb88320 ^ n >>> 1 : n >>> 1; return n >>> 0; });
function chunk(tag, data) { const body = Buffer.concat([Buffer.from(tag), data]); let c = 0xffffffff; for (const b of body) c = crcTable[(c ^ b) & 255] ^ c >>> 8; const out = Buffer.alloc(body.length + 8); out.writeUInt32BE(data.length); body.copy(out, 4); out.writeUInt32BE((c ^ 0xffffffff) >>> 0, out.length - 4); return out; }
const ih = Buffer.alloc(13); ih.writeUInt32BE(W); ih.writeUInt32BE(H, 4); ih[8] = 8; ih[9] = 6;
const raw = Buffer.alloc((W * 4 + 1) * H); for (let y = 0; y < H; y++) pixels.copy(raw, y * (W * 4 + 1) + 1, y * W * 4, (y + 1) * W * 4);
const png = Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ih), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
await fs.writeFile(art + '/rika_cat.png', png);

const elements = [], outliner = [], bones = new Map(), animations = [];
function bone(name, parent, origin, rotation = [0, 0, 0]) {
  const b = { name, origin, rotation, uuid: uuid(), export: true, visibility: true, isOpen: false, children: [] };
  bones.set(name, b); (parent ? bones.get(parent).children : outliner).push(b); return b;
}
function cube(name, group, from, to, mat = 'skin', rotation = [0, 0, 0], pivot = null) {
  const [u, v, size] = mats[mat], d = to.map((n, i) => n - from[i]);
  const dims = { north: [d[0], d[1]], east: [d[2], d[1]], south: [d[0], d[1]], west: [d[2], d[1]], up: [d[0], d[2]], down: [d[0], d[2]] };
  const faces = {};
  for (const [side, [w, h]] of Object.entries(dims)) {
    const fw = Math.min(size, Math.max(1, Math.round(w))), fh = Math.min(size, Math.max(1, Math.round(h)));
    const x = u + hash(elements.length, side.length) % Math.max(1, size - fw), y = v + hash(elements.length + 4, side.length) % Math.max(1, size - fh);
    faces[side] = { uv: [x, y, x + fw, y + fh], texture: 0 };
  }
  const c = { name, type: 'cube', uuid: uuid(), from, to, origin: pivot || from.map((n, i) => (n + to[i]) / 2), rotation, box_uv: false, autouv: 0, inflate: 0, shade: true, mirror_uv: false, visibility: true, export: true, faces };
  elements.push(c); bones.get(group).children.push(c.uuid); return c;
}
const box = (name, b, x, y, z, w, h, d, mat, rot, pivot) => cube(name, b, [x, y, z], [x + w, y + h, z + d], mat, rot, pivot);
function yz(name, b, x, y1, z1, y2, z2, width, depth, mat = 'skin') {
  const len = Math.hypot(y2 - y1, z2 - z1), y = (y1 + y2) / 2, z = (z1 + z2) / 2;
  return box(name, b, x - width / 2, y - len / 2, z - depth / 2, width, len, depth, mat, [Math.atan2(z2 - z1, y2 - y1) * 180 / Math.PI, 0, 0], [x, y, z]);
}
function uv(c, face, coords) { c.faces[face].uv = coords; }
bone('root', null, [0, 0, 0]);
bone('body', 'root', [0, 7, 1]);
// The original body's -90° X bind rotation is baked into its bounds.
const body = cube('body_vanilla_proportions', 'body', [-2, 4, -7], [2, 10, 9]);
uv(body, 'east', [16, 32, 0, 38]); uv(body, 'west', [0, 32, 16, 38]);
uv(body, 'up', [0, 64, 4, 80]); uv(body, 'south', [64, 0, 68, 6]); uv(body, 'down', [96, 32, 100, 48]);
box('chest_bib', 'body', -1.7, 5.8, -7.18, 3.4, 3.5, .4, 'hood');
box('shoulder_ridge', 'body', -2.18, 9, -6.1, 4.36, 1.6, 3.3, 'skin');

for (const side of ['left', 'right']) {
  const s = side === 'left' ? 1 : -1;
  for (const kind of ['front', 'hind']) {
    const name = side + '_' + kind + '_leg';
    const original = base.elements.find(c => c.name === name + '_cube');
    const group = base.groups.find(g => g.name === name);
    bone(name, 'root', [group.origin[0], group.origin[1], (original.from[2] + original.to[2]) / 2]);
    const c = cube(name + '_cube', name, original.from.map((n, i) => i === 1 ? Math.max(0, n) : n), original.to);
    for (const face of ['north', 'east', 'south', 'west']) uv(c, face, [kind === 'front' ? 16 : 24, 64, kind === 'front' ? 18 : 26, kind === 'front' ? 74 : 74]);
    uv(c, 'down', [80, 32, 82, 34]);
    if (kind === 'front') {
      for (let j = 0; j < 2; j++) box(name + '_claw_' + j, name, s * 1.2 - .65 + j * .85, .12, -5.55, .5, .55, .85, 'tooth', [12, 0, 0]);
    }
  }
}

bone('head', 'root', [0, 9, -9]);
box('cranium', 'head', -2.6, 8.9, -11.8, 5.2, 3.2, 4.8, 'skin');
box('hood_rear', 'head', -2.3, 11.5, -10.1, 4.6, 1.1, 3.6, 'crown');
for (let i = -2; i <= 2; i++) {
  const x = i * 1.08;
  box('segmented_hood_' + (i + 2), 'head', x - .54, 9.65 + Math.abs(i) * .18, -13.05 + Math.abs(i) * .15, 1.04, 2.85 - Math.abs(i) * .28, 2.2, 'hood', [0, 0, -i * 4]);
  box('crown_step_' + (i + 2), 'head', x - .5, 12.05 - Math.abs(i) * .12, -12.2 + Math.abs(i) * .12, 1, .75, 2.3, 'crown');
}
box('mouth_shadow', 'head', -2.05, 6.65, -10.4, 4.1, 2.9, .65, 'mouth');
box('upper_gum', 'head', -2.2, 9.1, -13.55, 4.4, .45, 2.1, 'gum');
for (const s of [-1, 1]) {
  box('cheek_' + s, 'head', s < 0 ? -2.63 : 2.08, 7.8, -11.9, .55, 2.1, 1.45, 'shade', [-22, 0, -s * 7]);
  box('muzzle_pad_' + s, 'head', s < 0 ? -2.1 : .45, 9.15, -13.8, 1.65, .55, .8, 'skin');
  box('whisker_' + s, 'head', s < 0 ? -3.5 : 2.1, 9.25, -13.67, 1.4, .18, .18, 'shade', [0, 0, s * 9]);
}
box('cat_nose', 'head', -.35, 9.25, -14, .7, .35, .4, 'gum');
box('nose_tip', 'head', -.17, 9.08, -14, .34, .2, .4, 'gum');
for (let i = 0; i < 5; i++) {
  const x = -1.8 + i * .9, length = i === 0 || i === 4 ? 1.35 : .95;
  box('upper_fang_' + i, 'head', x - .2, 9.12 - length, -13.45, .4, length, .48, 'tooth', [-16, 0, x * 4], [x, 9.15, -13.2]);
  box('upper_fang_tip_' + i, 'head', x - .12, 8.82 - length, -13.4, .24, .4, .3, 'tooth', [-16, 0, x * 4], [x, 9.15, -13.2]);
}
bone('jaw', 'head', [0, 8.75, -9.6]);
box('lower_jaw', 'jaw', -2.2, 6.3, -13.7, 4.4, .7, 4.2, 'skin');
box('lower_gum', 'jaw', -2.05, 6.95, -13.7, 4.1, .3, 2.8, 'gum');
for (const s of [-1, 1]) yz('jaw_hinge_' + s, 'jaw', s * 2.2, 6.75, -12.7, 8.7, -9.6, .55, .7, 'shade');
for (let i = 0; i < 4; i++) {
  const x = -1.5 + i, length = i === 0 || i === 3 ? .85 : .65;
  box('lower_fang_' + i, 'jaw', x - .17, 7.17, -13.42, .34, length, .4, 'tooth', [19, 0, -x * 5], [x, 7.17, -13.2]);
}
bone('tongue', 'jaw', [0, 7.15, -11]);
box('purple_tongue', 'tongue', -.7, 7.17, -12.95, 1.4, .3, 2, 'tongue');

for (const s of [-1, 1]) {
  const n = s < 0 ? 'right_ear' : 'left_ear';
  bone(n, 'head', [s * 2.45, 11.5, -9.5], [0, 0, -s * 9]);
  box(n + '_base', n, s * 2.45 - .95, 11.6, -10.65, 1.9, 1.45, 1.55, 'skin');
  box(n + '_tip', n, s * 2.45 - .5, 13.05, -10.4, 1, .85, 1.1, 'hood');
  box(n + '_inner', n, s * 2.45 - .5, 12, -10.69, 1, .85, .12, 'ear');
}

// Five angular loops sit behind the ears and continue over the shoulders.
for (let i = -2; i <= 2; i++) {
  const n = 'tendril_' + (i + 2), x = i * 1.4, top = 15.4 - Math.abs(i) * .7;
  bone(n, 'head', [x, 9.5, -6.2], [0, 0, -i * 10]);
  yz(n + '_root', n, x, 9.3, -6, 11.2, -3.7, .85, .85, 'shade');
  const rise = yz(n + '_rise', n, x, 11, -3.7, top, -4.8, .85, .85, 'tendril');
  uv(rise, 'north', [64, 56, 67, 62]); uv(rise, 'south', [64, 56, 67, 62]);
  yz(n + '_bend', n, x, top, -4.8, top + .25, -7.1, .85, .85, 'crown');
  const tip = yz(n + '_tip', n, x, top + .25, -7.1, top - 1.1, -7.55, .68, .68, 'crown');
  uv(tip, 'north', [64, 56, 67, 60]);
}

bone('tail1', 'root', [0, 9, 8]);
yz('tail_base', 'tail1', 0, 9, 8, 6, 15, 1.05, 1.05, 'tail');
bone('tail2', 'tail1', [0, 6, 15]);
yz('tail_middle', 'tail2', 0, 6, 15, 8.4, 21, .95, .95, 'tail');
bone('tail_tip', 'tail2', [0, 8.4, 21]);
yz('tail_hook', 'tail_tip', 0, 8.4, 21, 11.2, 21.2, .8, .8, 'dark');
yz('tail_hook_tip', 'tail_tip', 0, 11.2, 21.2, 11.75, 19.9, .62, .62, 'tail');

function clip(name, length, loop, tracks) {
  const animators = {};
  for (const [n, channels] of Object.entries(tracks)) {
    const b = bones.get(n); if (!b) throw Error(n);
    const keyframes = [];
    for (const [channel, frames] of Object.entries(channels)) for (const [time, x, y, z] of frames) keyframes.push({ uuid: uuid(), channel, time, data_points: [{ x: String(x), y: String(y), z: String(z) }], interpolation: 'catmullrom', color: -1 });
    animators[b.uuid] = { name: n, type: 'bone', rotation_global: false, keyframes };
  }
  animations.push({ uuid: uuid(), name: 'animation.rika_cat.' + name, loop: loop ? 'loop' : 'once', override: false, length, snapping: 60, selected: false, animators });
}
const idle = {
  body: { scale: [[0, 1, 1, 1], [1.5, 1.02, 1.025, 1], [3, 1, 1, 1]] },
  head: { rotation: [[0, 0, -2, 0], [1.5, -2, 2, 1], [3, 0, -2, 0]] },
  jaw: { rotation: [[0, 0, 0, 0], [1.5, 3, 0, 0], [3, 0, 0, 0]] },
  tail1: { rotation: [[0, 0, -7, 0], [1.5, 0, 7, 0], [3, 0, -7, 0]] },
  tail2: { rotation: [[0, 1, 9, 0], [1.5, -1, -9, 0], [3, 1, 9, 0]] },
  tail_tip: { rotation: [[0, 0, -5, 0], [1.5, 0, 5, 0], [3, 0, -5, 0]] },
  left_ear: { rotation: [[0, 0, 0, 0], [.9, 0, 0, 0], [1.05, 5, 0, -8], [1.25, 0, 0, 0], [3, 0, 0, 0]] },
  right_ear: { rotation: [[0, 0, 0, 0], [1.7, 0, 0, 0], [1.85, 4, 0, 7], [2.05, 0, 0, 0], [3, 0, 0, 0]] }
};
for (let i = 0; i < 5; i++) idle['tendril_' + i] = { rotation: [[0, -2 + i * .3, 0, -1], [1.5, 2 - i * .3, 0, 1], [3, -2 + i * .3, 0, -1]] };
clip('idle', 3, true, idle);
const walk = {
  head: { rotation: [[0, -2, 0, -1.5], [.4, 0, 0, 1.5], [.8, -2, 0, -1.5]] },
  tail1: { rotation: [[0, 1, -5, 0], [.4, -1, 5, 0], [.8, 1, -5, 0]] },
  tail2: { rotation: [[0, 0, 6, 0], [.4, 0, -6, 0], [.8, 0, 6, 0]] }
};
for (const [n, sign] of [['left_front_leg', 1], ['right_hind_leg', 1], ['right_front_leg', -1], ['left_hind_leg', -1]]) {
  const height = n.includes('front') ? 9.9 : 6;
  const rot = [], pos = [];
  for (let j = 0; j <= 8; j++) {
    const t = j / 10, phase = j / 8 * Math.PI * 2;
    const angle = sign * 22 * Math.cos(phase), theta = angle * Math.PI / 180;
    const lift = .65 * Math.max(0, sign * Math.sin(phase));
    const y = -height * (1 - Math.cos(theta)) + Math.abs(Math.sin(theta)) + lift;
    rot.push([t, angle, 0, 0]); pos.push([t, 0, Math.round(y * 1000) / 1000, 0]);
  }
  walk[n] = { rotation: rot, position: pos };
}
for (let i = 0; i < 5; i++) walk['tendril_' + i] = { rotation: [[0, -3, 0, 0], [.4, 3, 0, 0], [.8, -3, 0, 0]] };
clip('walk', .8, true, walk);
const roar = {
  head: { rotation: [[0, 0, 0, 0], [.35, -10, 0, 0], [.7, -5, 0, 0], [1.15, -7, 0, 0], [1.6, 0, 0, 0]] },
  jaw: { rotation: [[0, 0, 0, 0], [.3, 28, 0, 0], [.65, 25, 0, 0], [1.05, 29, 0, 0], [1.6, 0, 0, 0]] },
  tongue: { rotation: [[0, 0, 0, 0], [.45, -8, 0, 0], [1, -3, 0, 0], [1.6, 0, 0, 0]] },
  left_ear: { rotation: [[0, 0, 0, 0], [.3, 12, 0, -12], [1.15, 10, 0, -10], [1.6, 0, 0, 0]] },
  right_ear: { rotation: [[0, 0, 0, 0], [.3, 12, 0, 12], [1.15, 10, 0, 10], [1.6, 0, 0, 0]] },
  tail1: { rotation: [[0, 0, 0, 0], [.4, 12, 0, 0], [1.6, 0, 0, 0]] }
};
for (let i = 0; i < 5; i++) roar['tendril_' + i] = { rotation: [[0, 0, 0, 0], [.3, -9, 0, (i - 2) * 4], [1.1, -7, 0, (i - 2) * 3], [1.6, 0, 0, 0]] };
clip('roar', 1.6, false, roar);

const model = {
  meta: { format_version: '4.10', model_format: 'bedrock', box_uv: false }, name: '里香猫_MC风格', model_identifier: 'rika_cat',
  visible_box: [3, 2, .7], resolution: { width: W, height: H }, elements, outliner,
  textures: [{ name: 'rika_cat.png', id: '0', uuid: uuid(), width: W, height: H, uv_width: W, uv_height: H, source: 'data:image/png;base64,' + png.toString('base64'), internal: true, saved: true, mode: 'bitmap', render_mode: 'default', render_sides: 'auto' }], animations
};
await fs.writeFile(art + '/里香猫_MC风格.bbmodel', JSON.stringify(model, null, 2));
await fs.writeFile(art + '/model-stats.json', JSON.stringify({ cubes: elements.length, bones: bones.size, texture: [W, H], animations: animations.map(a => ({ name: a.name, length: a.length, loop: a.loop })), base: basePath, scale: '16 model units = 1 Minecraft block', front: '-Z' }, null, 2));
console.log(JSON.stringify({ file: art + '/里香猫_MC风格.bbmodel', cubes: elements.length, bones: bones.size, animations: animations.length }));

import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {CharacterMesh} from './shinjuku_mesh.mjs';
import {buildMahoraga} from './build_mahoraga_shinjuku.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..'),art=root+'/art/shinjuku-characters',resources=root+'/src/main/resources/assets/jujutsuvfx';
const C={skin:0xecc0a7,skinDark:0xc58d76,skinLight:0xf5d5be,ink:0x211e26,black:0x242630,shirtLight:0x3b3c49,white:0xf6f0df,cloth:0xe4dac3,crease:0xb7aa94,hair:0xf5f8ff,hairShade:0xc2d4e2,darkHair:0x1d222c,darkHairLight:0x384050,eye:0x38c7f2,eyeLight:0xc7f7ff,sukunaEye:0xb82834};
function wedge(m,a,b,tip,depth,col){m.tri(a,b,tip,col);const off=p=>[p[0],p[1],p[2]-depth];m.tri(off(b),off(a),off(tip),col);m.quad(a,off(a),off(b),b,col);m.quad(b,off(b),off(tip),tip,col);m.quad(tip,off(tip),off(a),a,col);}
function faceBox(m,x,y,w,h,c,z=4.02){m.box(x,y,z,w,h,.025,c);}
function inkLine(m,pts,width=.065){m.curve(pts,width,C.ink,pts.length===2?1:5,4,false);}
function skinArm(m,x,side,sukuna){
 const name=side+'_arm',fore=side+'_forearm';m.bone(name,'body',[x,23,0]);m.box(x-2,18,-2,4,6,4,C.skin);
 if(!sukuna){m.box(x-2.10,20.5,-2.10,4.2,3.6,4.2,C.black);m.box(x-1.6,20.62,2.015,3.2,.17,.12,C.shirtLight);}
 else{
  // Shoulder rings and upper-arm markings continue around the block's sides.
  m.box(x-2.035,20.13,-2.035,4.07,.4,4.07,C.ink);
  const ring=Array.from({length:17},(_,i)=>[x+Math.cos(i*Math.PI/8)*.95,22.28+Math.sin(i*Math.PI/8)*.85,2.035]);m.tube(ring,.085,C.ink,4);
 }
 m.bone(fore,name,[x,18,0]);m.box(x-2,12,-2,4,6,4,C.skin);
 if(sukuna)for(const y of [13.7,15.0])m.box(x-2.035,y,-2.035,4.07,.44,4.07,C.ink);
 else{m.box(x-1.6,12.5,2.015,.16,3.8,.06,C.skinDark);}
 m.bone(side+'_hand',fore,[x,12.9,0]);m.box(x-1.93,11.75,-1.93,3.86,1.45,3.86,C.skinLight);
 for(let i=0;i<3;i++)faceBox(m,x-1.35+i*.83,11.92,.065,.72,C.skinDark,2.015);
 m.bone(side+'_fingers',side+'_hand',[x,12.1,1.25],[65,0,0]);
 m.box(x-.91,9.55,.91,.64,2.60,.68,C.skinLight);m.box(x-.01,9.37,.91,.64,2.78,.68,C.skinLight);
 m.select(side+'_hand');m.box(x+(side==='right'?1.6:-2.2),11.88,-.3,.6,1.2,1.3,C.skin);
}
function leg(m,x,side){m.bone(side+'_leg','root',[x,12,0]);m.box(x-2.08,6,-2.12,4.16,6.1,4.24,C.cloth);m.box(x-1.77,6.25,2.132,.25,5.8,.045,C.crease);m.box(x+.65,6.15,2.132,.13,4.7,.035,C.white);m.bone(side+'_shin',side+'_leg',[x,6,0]);m.box(x-2.03,.72,-2.08,4.06,5.3,4.16,C.cloth);m.box(x-1.82,1.1,2.095,.2,4.8,.04,C.crease);m.box(x-1.8,.5,-1.98,3.6,.9,3.96,C.black);m.bone(side+'_foot',side+'_shin',[x,1,0]);m.box(x-1.98,0,-2.04,3.96,.75,4.65,C.black);m.box(x-1.85,.63,-1.9,3.7,.22,4.3,C.shirtLight);}
function hair(m,sukuna){const col=sukuna?C.darkHair:C.hair,shade=sukuna?C.darkHairLight:C.hairShade;
 m.box(-4.10,29.6,-4.08,8.2,2.7,8.18,col);m.box(-4.1,27.6,-4.13,8.2,2.6,.5,shade);
 for(let i=0;i<9;i++){const x=-4+i*.91;const y=31.5+Math.sin(i*1.7)*.4;const drop=sukuna?29.65+(i%3)*.28:28.4+(i%3)*.44;wedge(m,[x,y,4.18],[x+.95,y+.15,4.18],[x+.21,drop,4.40],.34,i%3?col:shade);}
 for(let i=0;i<11;i++){const a=i*2.399;const px=Math.cos(a)*3.25,pz=Math.sin(a)*2.9;const ty=33.3+(i%3)*.46;wedge(m,[px-.9,31.7,pz],[px+1.0,31.8,pz],[px+(sukuna?.35:Math.cos(a)*1.05),ty,pz-(sukuna?1.8:.5)],1.25,i%3?col:shade);}
 for(const s of [-1,1])for(let i=0;i<3;i++){const z=-2.4+i*1.7;wedge(m,[s*3.75,30.6,z],[s*3.7,32.0,z],[s*(sukuna?4.55:5),31.4+i*.4,z-.7],1.05,col);}
}
function buildHuman(m,sukuna){
 m.bone('root',null,[0,0,0]);m.bone('body','root',[0,12,0]);m.box(-4,12,-2,8,12,4,sukuna?C.black:C.black);
 if(sukuna){
  // Fitted black high-neck sleeveless layer below the pale crossed gi.
  m.box(-1.75,23.35,-1.85,3.5,1.1,3.7,C.black);
  m.box(-4.02,12.5,-2.1,8.04,10.0,4.2,C.cloth);
  m.quad([-4.1,24.02,2.18],[-2.2,24.02,2.18],[2.9,13.8,2.18],[-1.0,13.8,2.18],C.white);
  m.quad([2.3,24.02,2.23],[4.12,24.02,2.23],[1.1,15.6,2.23],[-1.9,15.6,2.23],C.white);
  inkLine(m,[[-2.13,23.93,2.28],[2.3,14.5,2.28]],.055);
  m.box(-4.10,22.4,-2.16,1.85,1.8,4.32,C.cloth);m.box(2.25,22.4,-2.16,1.85,1.8,4.32,C.cloth);
  for(const x of [-3.2,-1.4,2.8])m.box(x,14.2,2.125,.16,3.8,.035,C.crease);
 }else{
  m.box(-1.6,23.6,-1.9,3.2,.6,3.8,C.skin);
  faceBox(m,-2.65,21.15,2.25,.17,C.shirtLight,2.02);faceBox(m,.4,21.15,2.25,.17,C.shirtLight,2.02);
  for(const x of [-2.8,2.5])faceBox(m,x,14.5,.17,5.6,C.shirtLight,2.02);
 }
 m.box(-4.16,12.15,-2.22,8.32,1.16,4.44,C.ink);m.box(-.65,12.0,2.24,1.3,1.45,.65,C.black);
 m.bone('sash','body',[0,12.8,2.2]);m.box(-.5,9.65,2.27,.68,3.1,.23,C.black);m.box(.25,10.05,2.26,.66,2.55,.20,C.shirtLight);
 m.bone('head','body',[0,24,0]);m.box(-4,24,-4,8,8,8,C.skin);m.box(-4.1,26.0,-.7,.25,2.25,1.3,C.skinDark);m.box(3.85,26.0,-.7,.25,2.25,1.3,C.skinDark);
 // Pixel-scale facial panels keep both human heads compatible with the Steve silhouette.
 for(const s of [-1,1]){const x=s<0?-2.92:1.1;faceBox(m,x,27.12,1.82,.76,0xf5eee5,4.02);faceBox(m,x+.52,27.18,.82,.65,sukuna?C.sukunaEye:C.eye,4.055);faceBox(m,x+.78,27.16,.25,.67,C.ink,4.09);faceBox(m,x+.56,27.60,.21,.19,C.eyeLight,4.125);faceBox(m,x-.12,28.02,2.0,.21,sukuna?C.ink:C.hairShade,4.03);}
 faceBox(m,-.20,26.30,.40,.36,C.skinDark);faceBox(m,-.72,25.23,1.44,.14,sukuna?C.ink:C.skinDark);faceBox(m,.38,25.37,.42,.12,C.skinDark);
 if(sukuna){
  for(const x of [-.35,.16])faceBox(m,x,29.25,.20,1.25,C.ink,4.08);
  for(const s of [-1,1]){
   inkLine(m,[[s*2.7,27.0,4.095],[s*3.40,26.68,4.095],[s*2.68,26.16,4.095]],.075);
   inkLine(m,[[s*2.0,26.20,4.095],[s*2.74,25.72,4.095],[s*2.74,24.77,4.095]],.075);
   faceBox(m,s*.42-.07,24.36,.14,.58,C.ink,4.09);
   faceBox(m,s<0?-3.13:2.35,26.93,.74,.12,C.ink,4.11);
  }
 }
 hair(m,sukuna);
 skinArm(m,-6,'right',sukuna);skinArm(m,6,'left',sukuna);leg(m,-2,'right');leg(m,2,'left');
 humanClips(m,sukuna);
}
function humanClips(m,sukuna){
 const idle={body:{position:[[0,0,0,0],[1.5,0,.14,0],[3,0,0,0]]},head:{rotation:[[0,0,-1,0],[1.5,-1,1,0],[3,0,-1,0]]},right_arm:{rotation:[[0,0,0,1],[1.5,1,0,2],[3,0,0,1]]},left_arm:{rotation:[[0,0,0,-1],[1.5,-1,0,-2],[3,0,0,-1]]},sash:{rotation:[[0,0,0,0],[1.5,-3,0,1],[3,0,0,0]]}};
 m.clip('idle',3,true,idle);
 const walk={};for(const side of ['left','right']){const s=side==='left'?1:-1;walk[side+'_leg']={rotation:[[0,28*s,0,0],[.25,0,0,0],[.5,-28*s,0,0],[.75,0,0,0],[1,28*s,0,0]]};walk[side+'_arm']={rotation:[[0,-23*s,0,0],[.5,23*s,0,0],[1,-23*s,0,0]]};walk[side+'_shin']={rotation:[[0,0,0,0],[.25,s>0?18:0,0,0],[.5,0,0,0],[.75,s<0?18:0,0,0],[1,0,0,0]]};walk[side+'_forearm']={rotation:[[0,-6,0,0],[.5,-12,0,0],[1,-6,0,0]]};}walk.root={position:[[0,0,0,0],[.25,0,.22,0],[.5,0,0,0],[.75,0,.22,0],[1,0,0,0]]};m.clip('walk',1,true,walk);
 m.clip('attack',1.2,false,{body:{rotation:[[0,0,0,0],[.22,0,-18,-3],[.43,6,19,3],[.72,0,8,0],[1.2,0,0,0]]},right_arm:{rotation:[[0,0,0,0],[.22,-40,-12,-13],[.43,-101,8,4],[.68,-75,0,-5],[1.2,0,0,0]]},right_forearm:{rotation:[[0,0,0,0],[.22,-75,0,0],[.43,-7,0,0],[.68,-24,0,0],[1.2,0,0,0]]},left_arm:{rotation:[[0,0,0,0],[.22,-32,5,12],[.68,-32,5,12],[1.2,0,0,0]]},left_forearm:{rotation:[[0,0,0,0],[.22,-55,0,0],[.68,-55,0,0],[1.2,0,0,0]]}});
 const hold=(pose)=>[[0,0,0,0],[.65,...pose],[1.85,...pose],[2.6,0,0,0]];
 const tracks=sukuna?{right_arm:{rotation:hold([-34.737,0,3.763])},left_arm:{rotation:hold([-34.737,0,-3.763])},right_forearm:{rotation:hold([-140.330,0,-91.934])},left_forearm:{rotation:hold([-140.330,0,91.934])},right_hand:{rotation:hold([31.810,38.336,-43.445])},left_hand:{rotation:hold([31.810,-38.336,43.445])},head:{rotation:hold([5,0,0])}}:{right_arm:{rotation:hold([-42,-9,-16])},right_forearm:{rotation:hold([-116,0,-11])},right_hand:{rotation:hold([12,0,-8])},left_arm:{rotation:hold([4,0,-4])},head:{rotation:hold([2,-7,0])}};
 tracks.right_fingers={rotation:hold([-65,0,0])};
 if(sukuna)tracks.left_fingers={rotation:hold([-65,0,0])};
 m.clip('signature',2.6,false,tracks);
}
for(const sukuna of [false,true]){const m=new CharacterMesh(sukuna?'sukuna_shinjuku':'gojo_shinjuku',sukuna?'新宿_伏黑宿傩':'新宿_五条悟');buildHuman(m,sukuna);console.log(JSON.stringify(m.export(art,resources)));}
const mahoraga=new CharacterMesh('mahoraga_shinjuku','新宿_魔虚罗');buildMahoraga(mahoraga);console.log(JSON.stringify(mahoraga.export(art,resources)));

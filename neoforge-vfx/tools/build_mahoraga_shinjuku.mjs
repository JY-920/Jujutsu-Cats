/**
 * Shinjuku Mahoraga. Rest-pose coordinates use Blockbench units, Y up, +Z front.
 * Primary shape references: anime turnaround and MAPPA episode 41 production art.
 * Each mesh primitive belongs to the selected articulated bone.
 */
export function buildMahoraga(m) {
  const C = {
    skin: 0xCDD3CD, light: 0xE7E8DB, bright: 0xF0EEDD,
    mid: 0xBDC6BE, shadow: 0x9DAAA3, crease: 0x7A8B84,
    wing: 0xE4E7DC, wingEdge: 0xB1C0B5,
    mouth: 0x29312D, gums: 0x939E90, tooth: 0xF6EDD1,
    cloth: 0xE7E2CC, clothLight: 0xF4EFD9, clothShade: 0xC6C8B6,
    pants: 0x364B47, pantsLight: 0x4B6056, pantsShade: 0x273B37,
    black: 0x29352F, ringLight: 0x54605A,
    wheel: 0xB6A65D, wheelLight: 0xD5C57D, wheelShade: 0x827840,
    blade: 0xDDE7E3, bladeLight: 0xF5FBED, bladeShade: 0xA9BDB8,
  };
  const PI = Math.PI;
  const vadd = (a, b) => a.map((x, i) => x + b[i]);
  const vsub = (a, b) => a.map((x, i) => x - b[i]);
  const vmul = (a, s) => a.map(x => x * s);
  const cross = (a, b) => [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
  const unit = a => vmul(a, 1 / Math.max(1e-9, Math.hypot(...a)));
  const lerp = (a, b, t) => a.map((v, i) => v+(b[i]-v)*t);

  function ell(c, r, color=C.skin, sides=16, rings=10) {
    const large=Math.max(...r)>2;
    m.ellipsoid(c, r, color, Math.min(sides,large?14:10), Math.min(rings,large?8:6));
  }
  function tube(points, radii, color=C.skin, sides=12) {
    m.tube(points, typeof radii === 'number' ? points.map(()=>radii) : radii, color, Math.min(sides,12));
  }
  function curve(points, r=.12, color=C.shadow, steps=14, sides=6, taper=false) {
    m.curve(points, r, color, Math.max(6,Math.ceil(steps*.55)), Math.min(sides,6), taper);
  }
  function ring(center, radius, thickness, color, plane='xz', count=40, tubeSides=8) {
    count=Math.max(16,Math.round(count*.65));
    const p=[];
    for(let i=0;i<=count;i++) {
      const a=i/count*2*PI;
      p.push(plane==='xy' ? [center[0]+radius*Math.cos(a),center[1]+radius*Math.sin(a),center[2]] :
        [center[0]+radius*Math.cos(a),center[1],center[2]+radius*Math.sin(a)]);
    }
    tube(p, thickness, color, Math.min(tubeSides,6));
  }
  function orientedRing(center, tangent, radius, thickness, color, count=24) {
    count=Math.max(12,Math.round(count*.58));
    const w=unit(tangent), u=unit(cross(w,Math.abs(w[1])<.9?[0,1,0]:[1,0,0])), v=cross(w,u);
    const p=[];
    for(let i=0;i<=count;i++) {
      const a=i/count*2*PI;
      p.push(vadd(center,vadd(vmul(u,radius*Math.cos(a)),vmul(v,radius*Math.sin(a)))));
    }
    tube(p,thickness,color,5);
  }
  function loft(rings, color, sides=20, phase=0) {
    const rows=rings.map(([x,y,z,rx,rz])=>Array.from({length:sides},(_,i)=>{
      const a=(i/sides)*2*PI+phase;
      return [x+rx*Math.cos(a),y,z+rz*Math.sin(a)];
    }));
    for(let j=0;j<rows.length-1;j++) for(let i=0;i<sides;i++) {
      const k=(i+1)%sides;
      m.quad(rows[j][i],rows[j+1][i],rows[j+1][k],rows[j][k],color);
    }
    const first=rings[0],last=rings.at(-1);
    for(let i=0;i<sides;i++) {
      const k=(i+1)%sides;
      m.tri([first[0],first[1],first[2]],rows[0][i],rows[0][k],color);
      m.tri([last[0],last[1],last[2]],rows.at(-1)[k],rows.at(-1)[i],color);
    }
  }
  function flatPolygon(xy,z,depth,color,backColor=C.mid) {
    let polygon=xy.map((p,i)=>({p,i}));
    let area=0;
    for(let i=0;i<xy.length;i++) area+=xy[i][0]*xy[(i+1)%xy.length][1]-xy[(i+1)%xy.length][0]*xy[i][1];
    if(area<0) polygon.reverse();
    const det=(a,b,c)=>(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);
    const inside=(p,a,b,c)=>det(a,b,p)>=-1e-8&&det(b,c,p)>=-1e-8&&det(c,a,p)>=-1e-8;
    let safety=0;
    while(polygon.length>2 && safety++<1000) {
      let did=false;
      for(let i=0;i<polygon.length;i++) {
        const a=polygon[(i+polygon.length-1)%polygon.length],b=polygon[i],c=polygon[(i+1)%polygon.length];
        if(det(a.p,b.p,c.p)<=1e-8) continue;
        if(polygon.some(q=>q!==a&&q!==b&&q!==c&&inside(q.p,a.p,b.p,c.p))) continue;
        const f=q=>[q.p[0],q.p[1],z+depth/2];
        const k=q=>[q.p[0],q.p[1],z-depth/2];
        m.tri(f(a),f(b),f(c),color);
        m.tri(k(c),k(b),k(a),backColor);
        polygon.splice(i,1); did=true; break;
      }
      if(!did) break;
    }
    for(let i=0;i<xy.length;i++) {
      const a=xy[i],b=xy[(i+1)%xy.length];
      m.quad([a[0],a[1],z-depth/2],[b[0],b[1],z-depth/2],[b[0],b[1],z+depth/2],[a[0],a[1],z+depth/2],backColor);
    }
  }
  function tooth(base, tip, width, depth) {
    const w=unit(vsub(tip,base));
    let u=unit(cross(w,[0,0,1]));
    if(Math.hypot(...u)<.1) u=[1,0,0];
    const v=unit(cross(w,u));
    const corners=(c,sc)=>[
      vadd(c,vadd(vmul(u,-width*.5*sc),vmul(v,-depth*.5*sc))),
      vadd(c,vadd(vmul(u,width*.5*sc),vmul(v,-depth*.5*sc))),
      vadd(c,vadd(vmul(u,width*.5*sc),vmul(v,depth*.5*sc))),
      vadd(c,vadd(vmul(u,-width*.5*sc),vmul(v,depth*.5*sc))),
    ];
    const a=corners(base,1), b=corners(tip,.77);
    for(let i=0;i<4;i++) m.quad(a[i],b[i],b[(i+1)%4],a[(i+1)%4],C.tooth);
    m.quad(b[0],b[1],b[2],b[3],C.bright);
  }
  function surfaceLine(points, color=C.crease, r=.048) { curve(points,r,color,10,5,false); }

  m.bone('root',null,[0,0,0]);
  m.bone('pelvis','root',[0,23.8,0]);
  loft([[0,22.7,0,5.3,2.9],[0,24.5,0,5.7,3.0],[0,26.2,0,5.5,2.9]],C.pants,20);
  m.bone('torso','pelvis',[0,26,0]);
  loft([
    [0,25.3,0,5.0,2.7],[0,27.1,0,5.1,2.8],
    [0,29.4,-.1,5.9,3.0],[0,31.9,-.2,7.2,3.3],
    [0,34.4,-.15,8.45,3.6],[0,36.5,-.15,8.8,3.55],
    [0,38.2,-.3,7.9,2.95],[0,39.4,-.4,5.25,2.6],
    [0,40.3,-.45,2.4,2.05],
  ],C.skin,24);

  // Pectorals merge into the thorax; restrained depth avoids spherical lobes.
  for(const s of [-1,1]) {
    ell([s*4.2,35.9,2.75],[4.45,2.45,1.33],C.skin,18,10);
    ell([s*5.8,32.3,2.25],[1.5,2.3,1.0],C.mid,14,8);
    ell([s*4.65,29.6,2.43],[1.2,2.35,.72],C.skin,12,8);
    curve([[s*2.2,39.3,1.55],[s*4.0,38.2,2.85],[s*7.0,38.25,2.1]],.29,C.light,15,8,false);
    surfaceLine([[s*.25,34.0,3.47],[s*2.7,33.8,3.83],[s*5.6,34.1,3.5],[s*7.4,34.8,2.9]],C.crease,.063);
    surfaceLine([[s*6.9,33.4,2.65],[s*6.1,31.9,3.0],[s*5.2,31.15,3.18]],C.shadow,.063);
    surfaceLine([[s*5.8,30.5,2.99],[s*4.9,29.2,3.13],[s*4.1,28.2,3.05]],C.shadow,.055);
    surfaceLine([[s*4.85,27.55,2.9],[s*3.65,26.45,3.0],[s*1.35,25.6,3.0]],C.crease,.061);
    // Scapula and trapezius remain visible from the rear.
    ell([s*4.8,36.0,-2.87],[3.75,2.9,1.0],C.mid,16,10);
    curve([[s*1.2,39.8,-1.75],[s*3.6,38.3,-2.9],[s*7.45,37.5,-2.65]],.62,C.skin,14,10,false);
    surfaceLine([[s*1.05,37.8,-3.3],[s*3.15,36.5,-3.8],[s*5.9,35.1,-3.25]],C.crease,.056);
    surfaceLine([[s*3.9,34.5,-3.1],[s*4.3,32.0,-2.9],[s*3.8,29.0,-2.7]],C.shadow,.056);
  }
  for(let row=0;row<3;row++) for(const s of [-1,1]) {
    const y=32.45-row*2.18;
    ell([s*1.32,y,2.94],[1.16,1.11,.60],row===2?C.mid:C.skin,14,8);
    if(row<2) surfaceLine([[s*.27,y-1.0,3.15],[s*1.3,y-1.03,3.48],[s*2.32,y-.85,3.14]],C.shadow,.043);
  }
  surfaceLine([[0,38.2,3.45],[0,35.0,3.95],[0,32.8,3.15],[0,29.0,3.22]],C.shadow,.052);
  ell([0,27.03,2.96],[.18,.30,.08],C.crease,10,6);
  surfaceLine([[0,38.5,-3.0],[0,34,-3.52],[0,30.0,-3.0],[0,26.3,-2.82]],C.shadow,.055);

  // The black linked ornament follows the clavicles instead of covering the pecs.
  curve([[-7.4,38.7,2.57],[-4.9,38.15,3.33],[0,37.3,4.03],[4.9,38.15,3.33],[7.4,38.7,2.57]],.13,C.black,28,7,false);
  const charms=[[-7.3,38.65,2.7],[-4.5,37.95,3.57],[-2.2,37.5,3.9],[2.2,37.5,3.9],[4.5,37.95,3.57],[7.3,38.65,2.7]];
  for(const p of charms) {
    ring(p,.34,.095,C.black,'xy',18,6);
    tube([[p[0],p[1]-.31,p[2]],[p[0]-.04,p[1]-1.0,p[2]+.11]],[.12,.085],C.black,7);
    tube([[p[0]+.14,p[1]-.43,p[2]],[p[0]+.18,p[1]-1.03,p[2]+.06]],[.058,.032],C.black,5);
  }
  curve([[-1.05,37.5,4.05],[-1.1,37.0,4.16],[0,36.7,4.28],[1.1,37.0,4.16],[1.05,37.5,4.05]],.135,C.black,20,7,false);
  tube([[0,36.78,4.3],[0,35.8,4.38]],[.16,.08],C.black,8);

  // Neck, eyeless skull, four broad feather-shaped sensory wings and square teeth.
  m.bone('head','torso',[0,39.4,-.3]);
  loft([[0,39.15,-.4,2.1,1.9],[0,40.8,-.15,1.8,1.9],[0,43.0,0,2.2,2.15]],C.skin,20);
  ell([0,44.85,0],[2.90,4.45,2.65],C.skin,22,14);
  ell([0,47.45,-.35],[2.35,2.0,2.12],C.light,18,12);
  for(const s of [-1,1]) {
    curve([[s*1.52,43.6,1.5],[s*1.45,41.4,1.65],[s*2.25,39.3,1.83]],.25,C.light,15,8,false);
    curve([[s*2.5,44.65,1.7],[s*2.8,43.5,2.35],[s*2.15,41.6,2.65],[s*.75,40.8,2.0]],.45,C.mid,18,10,false);
    curve([[s*1.12,48.75,1.24],[s*.85,46.2,2.53],[s*.35,44.5,2.82]],.24,C.light,16,8,false);
    surfaceLine([[s*.48,48.7,1.6],[s*.6,46.8,2.42],[s*.28,44.85,2.75]],C.shadow,.05);
  }
  ell([0,41.3,1.2],[1.92,1.08,1.78],C.mid,18,10);
  ell([0,43.92,2.7],[1.75,.45,.53],C.mid,16,8);
  // A concave oral cavity leaves the mouth visibly open at oblique angles.
  const mouthCenter=[0,42.6,2.80], mouthRing=[];
  for(let i=0;i<40;i++) {
    const a=i/40*2*PI;
    mouthRing.push([2.24*Math.cos(a),42.60+1.23*Math.sin(a),3.56+.18*Math.cos(a)*Math.cos(a)]);
  }
  for(let i=0;i<40;i++) m.tri(mouthCenter,mouthRing[i],mouthRing[(i+1)%40],C.mouth);
  tube([...mouthRing,mouthRing[0]],.15,C.gums,8);
  for(let i=-3;i<=3;i++) {
    const x=i*.58, side=Math.abs(i)/3;
    tooth([x,43.63-.18*side,3.59+.10*side],[x*.97,42.88-.07*side,4.0-.05*side],.53-.03*side,.55);
    tooth([x,41.58+.31*side,3.74],[x*.98,42.28+.12*side,4.07-.05*side],.52-.06*side,.49);
  }
  for(const s of [-1,1]) {
    tooth([s*2.06,43.17,3.61],[s*1.95,42.65,3.94],.38,.48);
    tooth([s*2.04,42.08,3.66],[s*1.98,42.45,3.91],.37,.42);
    curve([[s*2.12,43.05,3.48],[s*2.75,43.95,2.44],[s*3.14,44.7,1.34]],.19,C.light,14,8,false);
  }

  const upperWing=[
    [1.45,45.4],[2.35,46.65],[3.45,47.35],[3.9,48.65],[4.9,49.0],
    [5.4,50.32],[6.65,50.5],[7.4,51.75],[8.68,51.95],[10.1,53.75],
    [9.83,51.1],[9.2,49.8],[8.2,48.85],[7.2,48.13],[6.13,47.74],
    [5.34,46.7],[4.13,46.12],[3.1,45.58],[2.2,45.07],
  ];
  const lowerWing=[
    [1.75,44.95],[3.0,45.8],[4.14,45.85],[5.5,46.9],[6.3,46.65],
    [7.9,47.55],[8.55,47.16],[10.25,47.87],[10.7,47.53],[12.3,48.24],
    [11.48,46.77],[10.42,46.25],[9.5,46.08],[8.25,45.25],[7.1,45.2],
    [5.95,44.62],[4.56,44.61],[3.2,44.2],[2.15,44.31],
  ];
  for(const s of [-1,1]) {
    flatPolygon(upperWing.map(([x,y])=>[s*x,y]),1.05,.43,C.wing,C.wingEdge);
    flatPolygon(lowerWing.map(([x,y])=>[s*x,y]),1.9,.44,C.wing,C.wingEdge);
    curve([[s*1.48,45.42,1.36],[s*4.0,46.65,1.45],[s*7.1,48.65,1.4],[s*10.02,53.6,1.04]],.18,C.light,23,8,true);
    curve([[s*1.7,44.85,2.25],[s*5.1,45.4,2.3],[s*8.1,46.0,2.2],[s*12.2,48.15,1.9]],.17,C.light,24,8,true);
    for(let k=0;k<5;k++) {
      const x=3.1+k*1.17, y=46.0+k*.75;
      surfaceLine([[s*x,y,1.34],[s*(x+.12),y+.9,1.33],[s*(x+.66),y+1.28,1.30]],C.wingEdge,.046);
      const lx=3.5+k*1.35,ly=44.8+k*.33;
      surfaceLine([[s*lx,ly,2.19],[s*(lx+.23),ly+.82,2.16],[s*(lx+.95),ly+1.16,2.14]],C.wingEdge,.045);
    }
  }

  // The long segmented appendage grows backwards from the skull, not from the torso.
  m.bone('head_tail_a','head',[0,45.85,-2.0]);
  const tailA=[[0,45.85,-1.7],[0,45.65,-4.0],[0,45.20,-6.7],[0,44.6,-9.45],[0,43.7,-12.1],[0,42.65,-14.25]];
  const tailAR=[2.05,1.97,1.80,1.63,1.50,1.34];
  tube(tailA,tailAR,C.skin,16);
  for(let i=1;i<tailA.length-1;i++) orientedRing(tailA[i],vsub(tailA[i+1],tailA[i-1]),tailAR[i]*1.01,.105,C.shadow,26);
  m.bone('head_tail_b','head_tail_a',[0,42.65,-14.25]);
  const tailB=[[0,42.65,-14.25],[0,41.5,-16.4],[0,40.9,-18.35],[0,41.35,-20.0],[0,42.7,-20.4],[0,43.65,-19.7]];
  const tailBR=[1.34,1.19,.94,.76,.51,.10];
  tube(tailB,tailBR,C.skin,14);
  for(let i=1;i<tailB.length-1;i++) orientedRing(tailB[i],vsub(tailB[i+1],tailB[i-1]),tailBR[i]*1.015,.10,C.shadow,24);

  // Eight-handled dharma wheel, including a complete horizontal rim and raised hub.
  m.bone('wheel','head',[0,56.1,-.3]);
  ring([0,56.1,-.3],6.85,.38,C.wheel,'xz',64,10);
  ring([0,56.26,-.3],6.8,.09,C.wheelLight,'xz',64,6);
  ring([0,55.94,-.3],6.8,.09,C.wheelShade,'xz',64,6);
  ell([0,56.1,-.3],[1.13,.49,1.13],C.wheelLight,20,10);
  ring([0,56.1,-.3],1.14,.11,C.wheelShade,'xz',32,6);
  for(let i=0;i<8;i++) {
    const a=i/8*2*PI, dx=Math.cos(a),dz=Math.sin(a);
    tube([[dx*.83,56.1,-.3+dz*.83],[dx*6.88,56.1,-.3+dz*6.88],[dx*8.16,56.1,-.3+dz*8.16]],[.16,.18,.20],C.wheel,8);
    const p=[dx*8.75,56.1,-.3+dz*8.75];
    ell(p,[.77,.69,.77],C.wheelLight,14,10);
    orientedRing(p,[dx,0,dz],.57,.075,C.wheel,22);
    ell([dx*6.85,56.35,-.3+dz*6.85],[.28,.12,.28],C.wheelLight,10,6);
  }

  // Muscular arms are segmented at shoulders, elbows and wrists for clean animation.
  for(const [side,s] of [['right',-1],['left',1]]) {
    m.bone(`${side}_upperarm`,'torso',[s*9.25,37.2,-.25]);
    ell([s*9.3,36.8,-.25],[3.20,3.6,2.93],C.skin,18,12);
    tube([[s*9.65,35.85,-.12],[s*11.05,33.4,0],[s*12.0,30.25,.10]],[2.52,2.45,1.67],C.skin,16);
    ell([s*11.1,33.15,1.0],[1.82,2.75,1.44],C.skin,16,10);
    ell([s*11.3,32.9,-1.28],[1.6,2.4,1.10],C.mid,14,10);
    curve([[s*7.65,38.25,1.58],[s*9.62,38.2,2.45],[s*11.45,36.6,1.87]],.085,C.shadow,14,6,false);
    surfaceLine([[s*10.4,35.25,2.2],[s*11.85,32.55,1.95],[s*12.14,30.9,1.31]],C.shadow,.063);
    m.bone(`${side}_forearm`,`${side}_upperarm`,[s*12.05,29.9,.10]);
    ell([s*12.0,29.78,.05],[1.86,1.60,1.75],C.mid,16,10);
    tube([[s*12.1,29.7,.05],[s*12.65,27.6,.22],[s*13.1,24.5,.30],[s*13.25,21.7,.45]],[1.7,2.05,1.55,1.06],C.skin,16);
    ell([s*12.63,26.9,.88],[1.40,2.62,1.12],C.light,14,10);
    curve([[s*12.0,29.6,-1.15],[s*12.45,26.0,-1.10],[s*13.2,22.05,-.36]],.21,C.mid,18,8,false);
    surfaceLine([[s*12.2,28.2,1.67],[s*12.82,25.2,1.7],[s*13.21,22.3,1.21]],C.shadow,.058);
    orientedRing([s*13.22,21.58,.47],[s*.1,-1,.05],1.25,.20,C.black,26);
    orientedRing([s*13.22,21.65,.47],[s*.1,-1,.05],1.26,.052,C.ringLight,26);
    m.bone(`${side}_hand`,`${side}_forearm`,[s*13.25,21.25,.46]);
    ell([s*13.28,20.15,.48],[1.20,1.61,.80],C.skin,14,10);
    for(let f=0;f<4;f++) {
      const fx=s*(12.46+f*.51), length=[1.55,1.93,2.0,1.7][f];
      tube([[fx,19.58,.53],[fx+s*.13,18.7,.75],[fx+s*.04,19.0-length,.91]],[.29,.25,.20],C.skin,10);
      ell([fx+s*.04,19.07-length,1.10],[.20,.29,.07],C.light,10,6);
    }
    tube([[s*12.16,20.5,.60],[s*11.69,19.48,1.0],[s*11.95,18.95,1.35]],[.40,.31,.24],C.skin,12);
    for(let i=0;i<3;i++) surfaceLine([[s*(12.55+i*.5),20.8,1.12],[s*(12.66+i*.5),19.7,1.19]],C.shadow,.04);
  }

  // Cloth wrapping and a wide bevelled Sword of Extermination along the right arm.
  m.select('right_forearm');
  for(let i=0;i<7;i++) {
    const t=i/6, y=28.65-t*6.4, x=-12.34-t*.77;
    orientedRing([x,y,.23+t*.20],[-.11,-1,.035],1.74-t*.62,.19,i%2?C.clothShade:C.cloth,24);
  }
  const bladeLeft=[[-14.12,29.1],[-15.17,28.5],[-15.62,25.1],[-15.79,19.0],[-15.1,13.6],[-14.58,17.4],[-14.21,22.1]];
  flatPolygon(bladeLeft,-.62,.41,C.blade,C.bladeShade);
  const centerRidge=[-15.18,22.55,.06];
  for(let i=0;i<bladeLeft.length;i++) {
    const a=bladeLeft[i],b=bladeLeft[(i+1)%bladeLeft.length];
    m.tri([a[0],a[1],-.38],[b[0],b[1],-.38],centerRidge,i<4?C.bladeLight:C.bladeShade);
  }
  curve([[-14.1,28.8,-.64],[-14.58,26.5,-.64],[-14.82,23.0,-.65]],.18,C.cloth,14,8,false);

  // Broad knee-length hakama-like trousers, exposed shins, toe articulation and rings.
  for(const [side,s] of [['right',-1],['left',1]]) {
    m.bone(`${side}_thigh`,'pelvis',[s*3.78,23.3,0]);
    loft([
      [s*3.6,24.15,0,3.40,2.9],[s*3.8,22.5,0,3.73,3.02],
      [s*3.93,19.5,.10,3.69,2.92],[s*4.08,16.0,.11,3.47,2.77],
      [s*4.10,12.25,.12,3.57,2.81],[s*4.10,11.50,.12,3.63,2.84],
    ],C.pants,20,PI/20);
    for(let f=0;f<4;f++) {
      const x=s*(1.28+f*1.55), z=[2.3,2.85,2.8,2.28][f];
      curve([[x,23.6,z-.15],[x+s*.35,19.6,z+.18],[x+s*.12,15.5,z+.15],[x+s*.25,11.62,z]],.13,f%2?C.pantsShade:C.pantsLight,19,7,false);
    }
    curve([[s*6.2,23.6,-2.3],[s*6.73,20,-2.32],[s*6.9,16,-2.06],[s*6.4,11.6,-2.3]],.12,C.pantsShade,18,7,false);
    curve([[s*2.6,23.8,-2.66],[s*2.2,19.5,-2.71],[s*2.55,15,-2.59],[s*2.5,11.56,-2.47]],.12,C.pantsLight,18,7,false);
    m.bone(`${side}_shin`,`${side}_thigh`,[s*4.1,11.8,.12]);
    ell([s*4.1,11.30,.6],[2.07,1.53,1.77],C.skin,16,10);
    tube([[s*4.1,11.6,.18],[s*4.16,9.7,-.05],[s*4.30,6.7,-.12],[s*4.38,3.0,.09]],[1.93,2.13,1.58,.94],C.skin,16);
    ell([s*4.18,8.9,-.62],[1.53,2.60,1.35],C.mid,14,10);
    curve([[s*4.1,11.6,1.58],[s*4.0,8.2,1.55],[s*4.37,3.2,.99]],.26,C.light,18,8,false);
    surfaceLine([[s*5.14,10.45,1.35],[s*4.93,7.9,1.2],[s*4.66,3.2,.88]],C.shadow,.066);
    surfaceLine([[s*3.2,10.6,1.35],[s*3.1,8.5,1.24],[s*3.95,3.6,.77]],C.shadow,.066);
    orientedRing([s*4.38,2.92,.1],[0,1,0],1.14,.19,C.black,28);
    orientedRing([s*4.38,2.99,.1],[0,1,0],1.15,.055,C.ringLight,28);
    m.bone(`${side}_foot`,`${side}_shin`,[s*4.38,2.3,.16]);
    ell([s*4.38,1.63,.55],[1.31,1.25,1.66],C.skin,16,10);
    ell([s*4.30,.95,1.85],[1.80,.84,2.0],C.skin,16,10);
    for(let t=0;t<5;t++) {
      const x=s*(2.92+t*.66), z=3.24-.21*t, r=t===0?.52:.38;
      ell([x,.76,z],[r,.46,.91-.10*t],C.skin,12,8);
      ell([x,.92,z+.61-.07*t],[r*.75,.12,.28],C.light,10,6);
      surfaceLine([[x,1.25,z-.52],[x,.98,z+.14]],C.shadow,.037);
    }
    for(let i=0;i<3;i++) curve([[s*(3.5+i*.54),2.54,.53],[s*(3.38+i*.67),1.4,2.05],[s*(3.27+i*.67),1.15,2.76]],.075,C.light,13,6,false);
  }

  m.select('pelvis');
  loft([[0,24.10,0,5.80,3.13],[0,24.55,0,5.85,3.19],[0,25.40,0,5.65,3.16],[0,25.72,0,5.53,3.10]],C.cloth,28);
  curve([[-5.3,25.2,1.2],[-3.8,24.6,3.03],[0,24.5,3.23],[3.8,25.25,3.05],[5.5,24.8,1.2]],.22,C.clothLight,25,8,false);
  curve([[-5.2,24.4,1.6],[-2.8,25.0,3.3],[.5,25.4,3.20],[3.9,24.5,3.0],[5.3,24.9,1.9]],.19,C.clothShade,25,8,false);
  ell([0,24.97,3.63],[1.3,.93,.70],C.clothLight,16,10);
  flatPolygon([[-.48,25.38],[-3.57,25.88],[-4.06,25.35],[-3.73,24.35],[-1.01,24.49]],3.83,.45,C.cloth,C.clothShade);
  flatPolygon([[.49,25.4],[3.39,26.17],[4.17,25.48],[3.48,24.60],[.98,24.57]],3.87,.45,C.clothLight,C.clothShade);
  curve([[-.2,25.4,4.26],[-1.2,24.8,4.17],[-3.63,24.94,4.13]],.10,C.clothShade,13,6,false);
  curve([[.18,25.42,4.29],[1.6,25.27,4.22],[3.7,25.4,4.21]],.11,C.clothShade,13,6,false);
  m.bone('front_cloth','pelvis',[0,24.9,3.4]);
  // Folded strips use an undulating surface rather than a solid cuboid apron.
  const clothRows=[
    {y:24.6,x:-.22,w:1.12,z:3.52},
    {y:22.0,x:-.45,w:1.24,z:3.64},
    {y:19.0,x:-.12,w:1.31,z:3.46},
    {y:16.1,x:-.4,w:1.33,z:3.58},
    {y:13.9,x:-.23,w:1.36,z:3.71},
  ];
  const cols=8,grid=clothRows.map((r,j)=>Array.from({length:cols+1},(_,i)=>{
    const q=i/cols;
    return [r.x+(q-.5)*r.w*2,r.y+(j===clothRows.length-1?.20*Math.sin(q*8):0),r.z+.18*Math.sin(q*3*PI)];
  }));
  for(let j=0;j<grid.length-1;j++) for(let i=0;i<cols;i++) {
    const color=[C.clothShade,C.cloth,C.clothLight,C.clothLight,C.cloth,C.clothShade,C.cloth,C.clothLight][i];
    m.quad(grid[j][i],grid[j+1][i],grid[j+1][i+1],grid[j][i+1],color);
    m.quad(vadd(grid[j][i+1],[0,0,-.12]),vadd(grid[j+1][i+1],[0,0,-.12]),vadd(grid[j+1][i],[0,0,-.12]),vadd(grid[j][i],[0,0,-.12]),C.clothShade);
  }
  flatPolygon([[.65,24.8],[1.65,24.8],[1.73,19.0],[1.37,15.0],[.84,15.08],[.91,20.1]],3.40,.14,C.cloth,C.clothShade);

  // Pose curves are additive to the rest mesh. The wheel loop layers over any action.
  m.clip('idle',3,true,{
    pelvis:{position:[[0,0,0,0],[1.5,0,.13,0],[3,0,0,0]]},
    torso:{rotation:[[0,-1,0,0],[1.5,1.1,0,0],[3,-1,0,0]],scale:[[0,1,1,1],[1.5,1.012,1.009,1.018],[3,1,1,1]]},
    head:{rotation:[[0,0,-2,0],[1.5,-1.4,2,0],[3,0,-2,0]]},
    right_upperarm:{rotation:[[0,-2,0,2],[1.5,1.5,0,.6],[3,-2,0,2]]},
    left_upperarm:{rotation:[[0,1,0,-2],[1.5,-1.6,0,-.7],[3,1,0,-2]]},
    right_forearm:{rotation:[[0,-4,0,0],[1.5,-6,0,0],[3,-4,0,0]]},
    left_forearm:{rotation:[[0,-6,0,0],[1.5,-4,0,0],[3,-6,0,0]]},
    head_tail_a:{rotation:[[0,0,-1.5,0],[1.5,0,1.5,0],[3,0,-1.5,0]]},
    head_tail_b:{rotation:[[0,0,-2,0],[1.5,0,2,0],[3,0,-2,0]]},
    front_cloth:{rotation:[[0,-1,0,0],[1.5,1,0,0],[3,-1,0,0]]},
  });
  m.clip('walk',1,true,{
    pelvis:{position:[[0,0,0,0],[.25,0,.28,0],[.5,0,0,0],[.75,0,.28,0],[1,0,0,0]],rotation:[[0,0,-3,0],[.5,0,3,0],[1,0,-3,0]]},
    torso:{rotation:[[0,2,5,0],[.5,2,-5,0],[1,2,5,0]]},
    head:{rotation:[[0,-2,-3,0],[.5,-2,3,0],[1,-2,-3,0]]},
    right_thigh:{rotation:[[0,25,0,0],[.5,-25,0,0],[1,25,0,0]]},
    left_thigh:{rotation:[[0,-25,0,0],[.5,25,0,0],[1,-25,0,0]]},
    right_shin:{rotation:[[0,-4,0,0],[.25,-24,0,0],[.5,-3,0,0],[.75,-7,0,0],[1,-4,0,0]]},
    left_shin:{rotation:[[0,-3,0,0],[.25,-7,0,0],[.5,-4,0,0],[.75,-24,0,0],[1,-3,0,0]]},
    right_foot:{rotation:[[0,-8,0,0],[.25,9,0,0],[.5,5,0,0],[.75,-4,0,0],[1,-8,0,0]]},
    left_foot:{rotation:[[0,5,0,0],[.25,-4,0,0],[.5,-8,0,0],[.75,9,0,0],[1,5,0,0]]},
    right_upperarm:{rotation:[[0,-19,0,3],[.5,19,0,1],[1,-19,0,3]]},
    left_upperarm:{rotation:[[0,19,0,-1],[.5,-19,0,-3],[1,19,0,-1]]},
    right_forearm:{rotation:[[0,-8,0,0],[.5,-18,0,0],[1,-8,0,0]]},
    left_forearm:{rotation:[[0,-18,0,0],[.5,-8,0,0],[1,-18,0,0]]},
    head_tail_a:{rotation:[[0,2,-3,0],[.5,-2,3,0],[1,2,-3,0]]},
    front_cloth:{rotation:[[0,7,0,2],[.5,-6,0,-2],[1,7,0,2]]},
  });
  m.clip('attack',1.2,false,{
    pelvis:{position:[[0,0,0,0],[.28,0,-.9,-.3],[.48,0,-.35,1.15],[.8,0,-.4,.6],[1.2,0,0,0]],rotation:[[0,0,0,0],[.3,0,-19,0],[.52,0,21,0],[1.2,0,0,0]]},
    torso:{rotation:[[0,0,0,0],[.28,-7,-27,-5],[.49,13,32,10],[.7,7,16,3],[1.2,0,0,0]]},
    head:{rotation:[[0,0,0,0],[.3,1,17,0],[.52,-6,-15,0],[1.2,0,0,0]]},
    right_upperarm:{rotation:[[0,0,0,0],[.28,-116,-26,-32],[.43,-93,13,-16],[.56,39,29,19],[.8,12,10,6],[1.2,0,0,0]]},
    right_forearm:{rotation:[[0,0,0,0],[.28,-47,0,0],[.43,-17,0,0],[.56,-4,0,0],[1.2,0,0,0]]},
    right_hand:{rotation:[[0,0,0,0],[.28,-13,0,0],[.52,16,0,0],[1.2,0,0,0]]},
    left_upperarm:{rotation:[[0,0,0,0],[.28,-27,8,24],[.5,-45,-12,33],[.85,-12,0,10],[1.2,0,0,0]]},
    left_forearm:{rotation:[[0,0,0,0],[.28,-55,0,0],[.5,-72,0,0],[1.2,0,0,0]]},
    right_thigh:{rotation:[[0,0,0,0],[.28,-10,0,-5],[.52,15,0,-6],[1.2,0,0,0]]},
    left_thigh:{rotation:[[0,0,0,0],[.28,12,0,5],[.52,-14,0,5],[1.2,0,0,0]]},
    right_shin:{rotation:[[0,0,0,0],[.3,-15,0,0],[.55,-7,0,0],[1.2,0,0,0]]},
    left_shin:{rotation:[[0,0,0,0],[.3,-9,0,0],[.55,-21,0,0],[1.2,0,0,0]]},
    head_tail_a:{rotation:[[0,0,0,0],[.32,-6,-15,0],[.62,6,17,0],[1.2,0,0,0]]},
    head_tail_b:{rotation:[[0,0,0,0],[.42,-4,-12,0],[.75,5,14,0],[1.2,0,0,0]]},
    front_cloth:{rotation:[[0,0,0,0],[.3,-14,0,-6],[.57,21,0,9],[.9,-5,0,-3],[1.2,0,0,0]]},
  });
  m.clip('signature',2.6,false,{
    pelvis:{position:[[0,0,0,0],[.5,0,-1.8,0],[1.3,0,-1.9,0],[1.9,0,.28,0],[2.6,0,0,0]]},
    torso:{rotation:[[0,0,0,0],[.5,17,0,0],[1.25,20,0,0],[1.9,-8,0,0],[2.6,0,0,0]]},
    head:{rotation:[[0,0,0,0],[.5,6,0,0],[1.25,-4,0,0],[1.9,-14,0,0],[2.6,0,0,0]]},
    right_upperarm:{rotation:[[0,0,0,0],[.5,-30,0,-18],[1.3,-32,-12,-25],[1.9,-11,0,-38],[2.6,0,0,0]]},
    left_upperarm:{rotation:[[0,0,0,0],[.5,-30,0,18],[1.3,-32,12,25],[1.9,-11,0,38],[2.6,0,0,0]]},
    right_forearm:{rotation:[[0,0,0,0],[.5,-36,0,0],[1.3,-43,0,0],[1.9,-9,0,0],[2.6,0,0,0]]},
    left_forearm:{rotation:[[0,0,0,0],[.5,-36,0,0],[1.3,-43,0,0],[1.9,-9,0,0],[2.6,0,0,0]]},
    right_thigh:{rotation:[[0,0,0,0],[.5,18,0,-8],[1.3,18,0,-8],[1.9,0,0,-3],[2.6,0,0,0]]},
    left_thigh:{rotation:[[0,0,0,0],[.5,18,0,8],[1.3,18,0,8],[1.9,0,0,3],[2.6,0,0,0]]},
    right_shin:{rotation:[[0,0,0,0],[.5,-31,0,0],[1.3,-31,0,0],[1.9,0,0,0],[2.6,0,0,0]]},
    left_shin:{rotation:[[0,0,0,0],[.5,-31,0,0],[1.3,-31,0,0],[1.9,0,0,0],[2.6,0,0,0]]},
    head_tail_a:{rotation:[[0,0,0,0],[.6,-8,0,0],[1.4,-12,0,0],[2.0,9,0,0],[2.6,0,0,0]]},
    head_tail_b:{rotation:[[0,0,0,0],[.7,-13,0,0],[1.5,-17,0,0],[2.1,12,0,0],[2.6,0,0,0]]},
    front_cloth:{rotation:[[0,0,0,0],[.5,-16,0,0],[1.3,-18,0,0],[1.9,12,0,0],[2.6,0,0,0]]},
  });
  m.clip('wheel_spin',4,true,{wheel:{rotation:[[0,0,0,0],[1,0,90,0],[2,0,180,0],[3,0,270,0],[4,0,360,0]]}});
  m.select('root');
  return {
    name:'八握剑异戒神将魔虚罗 — 新宿之战',
    features:['四片羽翼状眼部','无普通人眼','上下排方牙与内凹口腔','分节后脑附肢','右臂退魔剑','宽膝深灰绿裤','米白腰结与垂布','水平八辐八球法轮'],
    animationNames:['idle','walk','attack','signature','wheel_spin'],
    nominalBodyHeight:49.5,
    nominalTotalHeight:57,
  };
}

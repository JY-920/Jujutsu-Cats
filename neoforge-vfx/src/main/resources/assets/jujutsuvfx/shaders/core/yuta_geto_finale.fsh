#version 150
in vec4 tint;
in vec2 uv;
in vec3 surfacePosition;
flat in int materialKind;
flat in float materialTime;
uniform vec4 ColorModulator;
out vec4 fragColor;
const float TAU = 6.28318530718;
float hash(vec2 p) {
    vec3 a = fract(vec3(p.xyx) * .1031);
    a += dot(a, a.yzx + 33.33);
    return fract((a.x + a.y) * a.z);
}
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1,0)), f.x),
               mix(hash(i + vec2(0,1)), hash(i + vec2(1,1)), f.x), f.y);
}
// The duel uses bounded local volume integration, matching Blue/Purple's turbulent
// density + sparse stellar filaments. Maximum's original surface modes below remain intact.
float hash3(vec3 p) {
    p = fract(p * .1031); p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}
float noise3(vec3 p) {
    vec3 i=floor(p),f=fract(p); f=f*f*(3.0-2.0*f);
    return mix(mix(mix(hash3(i),hash3(i+vec3(1,0,0)),f.x),
                   mix(hash3(i+vec3(0,1,0)),hash3(i+vec3(1,1,0)),f.x),f.y),
               mix(mix(hash3(i+vec3(0,0,1)),hash3(i+vec3(1,0,1)),f.x),
                   mix(hash3(i+vec3(0,1,1)),hash3(i+vec3(1,1,1)),f.x),f.y),f.z);
}
float sq(float a){return a*a;}
vec4 cloudField(vec3 q,float t,int kind){
    float r=length(q);
    if(r>1.48)return vec4(0);
    vec3 curl=vec3(sin(q.y*3.2-t*1.7),sin(q.z*2.9+t*1.4),sin(q.x*3.4-t*1.2));
    vec3 flow=q+curl*.13;
    float broad=noise3(flow*3.6+vec3(t*.39,-t*.68,t*.27));
    float detail=noise3(flow*12.5+vec3(-t*.78,t*.46,t*.59));
    float torn=smoothstep(.29,.76,broad*.63+detail*.37);
    float edge=r+(broad-.5)*.39+(detail-.5)*.11;
    float mantle=1.0-smoothstep(.71,1.17,edge);
    float angle=atan(q.y,q.x+.00001);
    if(kind==9){
        float winding=.5+.5*sin(angle*5.0+r*15.0-q.z*7.0+t*2.3+broad*6.0);
        float ropes=pow(winding,3.0);
        float cavity=exp(-dot(q.xy,q.xy)*18.0)*smoothstep(-.28,.62,q.z);
        float density=mantle*(.75+ropes*1.25+detail*.55)*(1.0-cavity*.55);
        float light=clamp(.32+q.y*.28-q.z*.12+ropes*.35,0.0,1.0);
        vec3 color=mix(vec3(.020,.011,.025),vec3(.49,.37,.44),light*(.48+detail*.52));
        color+=vec3(.15,.10,.13)*ropes*torn;
        color*=1.0-cavity*.90;
        float ember=pow(winding,20.0)*smoothstep(.59,.77,detail)*smoothstep(.55,.95,r);
        color+=vec3(.80,.055,.22)*ember;
        return vec4(color,density*2.15);
    }
    if(kind==16){
        float fold=pow(.5+.5*sin(q.y*8.2-q.x*5.7+q.z*4.9+broad*10.0-t*3.5),4.0);
        float wisps=mantle*(.08+fold*.62)*(.12+torn*.88);
        float skin=exp(-sq((edge-.85)/.115))*torn*(.06+fold*.25);
        float density=wisps+skin;
        vec3 warm=mix(vec3(.72,.008,.36),vec3(2.1,.30,1.20),fold*(.22+detail*.78));
        vec3 ash=mix(vec3(.10,.065,.11),vec3(.72,.54,.65),fold*(.4+detail*.6));
        vec3 color=mix(warm,ash,smoothstep(-.12,.52,q.x+(broad-.5)*.34)*.76);
        // Broad torn wings carry colored pressure, without expanding the white contact core.
        return vec4(color,density*1.12);
    }
    // Broken tilted layers retain a stellar center; no uniform white sphere is present.
    vec3 disk=q;disk.xy=mat2(.88,-.475,.475,.88)*disk.xy;
    float plane=exp(-sq((disk.z+disk.y*.37+(broad-.5)*.18)/.17));
    float crossPlane=exp(-sq((disk.z*.65-disk.y*.57+disk.x*.27+(detail-.5)*.07)/.095));
    float concentration=exp(-dot(q*vec3(3.1,3.5,3.3),q*vec3(3.1,3.5,3.3)));
    float spiral=pow(.5+.5*sin(angle*2.0-r*13.0+broad*5.2-t*1.7),5.0);
    float threads=pow(.5+.5*sin(disk.x*35.0-disk.y*29.0+detail*9.0+t*4.7),7.0);
    float knots=smoothstep(.60,.79,detail)*smoothstep(.39,.63,broad);
    float stars=plane*exp(-dot(disk.xy,disk.xy)*4.6)*(spiral*.52+threads*.43)*(.08+torn*.92);
    stars+=concentration*(crossPlane*.90+plane*(.3+threads*1.5)+knots*1.6);
    stars+=plane*knots*exp(-r*r*6.0)*.85;
    float skin=exp(-sq((edge-.91)/.075))*(.12+torn*.88);
    float nebula=mantle*(.10+.18*broad+spiral*plane*.10)+skin*.15;
    float rupture=kind==15?1.25:1.0;
    vec3 magenta=mix(vec3(.24,.003,.14),vec3(1.05,.018,.65),clamp(spiral*.45+detail*.5,0.0,1.0));
    vec3 stellar=mix(vec3(2.2,.44,1.48),vec3(4.2,3.6,4.05),clamp(concentration*.9+detail*.45,0.0,1.0));
    float density=nebula+stars*1.45*rupture;
    return vec4((magenta*nebula+stellar*stars*1.45*rupture)/max(density,.0001),density);
}
vec4 streamField(vec3 q,float t,int kind,vec3 advected){
    float x=q.x,rad=length(q.yz);
    // Advection is sampled once per ray; depth modulation stays continuous in the volume.
    // This retains coherent longitudinal cavities instead of averaging 18 unrelated noise columns.
    float broad=advected.x*.80+(.5+.5*sin(q.z*5.3+x*.7-t*1.9))*.20;
    float detail=advected.y*.76+(.5+.5*sin(q.z*11.1+x*1.3-t*4.1))*.24;
    float bend=sin(x*1.8-t*5.1)*.035+sin(x*4.3+t*3.7)*.018;
    float edge=length(q.yz-vec2(bend,sin(x*1.7-t*3.2)*.035))+(broad-.5)*.23;
    float mantle=1.0-smoothstep(.47,.98,edge);
    if(kind==12){
        float angle=atan(q.z,q.y+.00001);
        float twist=pow(.5+.5*sin(angle*5.0+x*2.7-t*7.5+broad*4.0),4.0);
        float density=mantle*(.75+twist*.8+detail*.35);
        vec3 color=mix(vec3(.027,.014,.026),vec3(.40,.28,.35),twist*(.38+detail*.62));
        color+=vec3(.38,.019,.12)*pow(twist,5.0)*smoothstep(.68,.84,detail);
        return vec4(color,density*2.1);
    }
    float fold=pow(.5+.5*sin(q.y*15.0-q.z*11.0+x*1.7-t*13.0+broad*8.0),6.0);
    float torn=smoothstep(.30,.73,detail*.7+broad*.3);
    float channel=advected.z;
    float cavities=.20+.80*smoothstep(.28,.72,channel);
    float core=exp(-sq(edge/.33))*(.25+detail*.37+fold*.62)*cavities;
    float hotA=exp(-sq((q.y-.13*sin(x*1.4-t*4.1))/.074)-sq((q.z-.08*cos(x*1.7-t*2.3))/.14));
    float hotB=exp(-sq((q.y+.18+.06*sin(x*2.1+t*3.3))/.049)-sq((q.z+.10)/.11));
    core+=(hotA*.62+hotB*.45)*(.42+.58*channel);
    float fibers=fold*torn*exp(-edge*edge*5.5);
    float skirt=mantle*(.075+broad*.13+fold*.14)*(.3+torn*.7);
    float density=core*1.08+fibers*.52+skirt;
    vec3 gas=vec3(1.65,.015,.85),heat=mix(vec3(3.4,1.05,2.5),vec3(4.25,3.70,4.15),clamp(core+detail*.3,0.0,1.0));
    return vec4((heat*(core*1.08+fibers*.52)+gas*skirt)/max(density,.0001),density);
}
void duelMaterial(int kind,float v){
    float time=materialTime;
    vec2 local=vec2(uv.x,v);
    float alpha=tint.a*ColorModulator.a;
    if(kind==13){
        float cloud=noise(vec2(local.x*27.0-time*.9,3.1+time*.18));
        float detail=noise(vec2(local.x*113.0+time*.7,local.y*4.0-time*.4));
        float ridge=exp(-sq((local.y-.5+(cloud-.5)*.14)/.22));
        float gaps=.018+.982*smoothstep(.37,.72,cloud);
        float grain=.20+.80*detail;
        alpha*=ridge*gaps*grain;
        if(alpha<.003)discard;
        fragColor=vec4(tint.rgb*ColorModulator.rgb*(.65+detail*.6),alpha);return;
    }
    if(kind==14){
        float across=abs(local.y*2.0-1.0);
        float core=exp(-across*across*17.0),glow=exp(-across*across*4.0)*.20;
        float flow=.56+.44*noise(vec2(local.x*17.0-time*6.7,4.0));
        alpha*=(core+glow)*flow;
        if(alpha<.003)discard;
        fragColor=vec4(tint.rgb*ColorModulator.rgb*(.85+core*.4),alpha);return;
    }
    bool stream=kind==11||kind==12;
    vec2 p=local*2.0-1.0;
    if(!stream&&dot(p,p)>1.0)discard;
    if(stream&&abs(p.y)>.995)discard;
    vec3 advected=vec3(0);
    if(stream){
        float along=local.x*12.0;
        advected=vec3(noise3(vec3(along*1.1-time*3.1,p.y*4.44,time*.13)),
            noise3(vec3(along*3.6-time*8.2,p.y*12.6,time*.29)),
            noise3(vec3(along*2.4-time*5.1,p.y*7.56,time*.17)));
    }
    float jitter=(hash(floor(gl_FragCoord.xy))-.5)*.65;
    vec4 sum=vec4(0);
    // Fixed authored budgets: thin pressure wings need 8 depth samples, streams 18,
    // and dense source/contact clouds 24. No screen-sized pass or distance fallback.
    int samples=kind==16?8:stream?18:24;
    float bound=stream?1.15:1.4,stepSize=2.0*bound/float(samples);
    for(int i=0;i<24;i++){
        if(i>=samples)break;
        float z=(float(i)+.5+jitter)*stepSize-bound;
        vec4 field;
        if(stream)field=streamField(vec3(local.x*12.0,p.y*1.2,z),time,kind,advected);
        else field=cloudField(vec3(p*1.5,z),time,kind);
        float a=1.0-exp(-field.a*stepSize*1.242857);
        sum.rgb+=(1.0-sum.a)*field.rgb*a;
        sum.a+=(1.0-sum.a)*a;
        if(sum.a>.995)break;
    }
    float envelope=stream?smoothstep(0.0,.025,local.x)*(1.0-smoothstep(.98,1.0,local.x)):1.0-smoothstep(.83,1.0,length(p));
    alpha*=sum.a*envelope;
    if(alpha<.003)discard;
    vec3 color=sum.rgb/max(sum.a,.0001)*ColorModulator.rgb;
    // Never permit an invalid local sample to affect the world framebuffer.
    if(any(isnan(color))||any(isinf(color)))discard;
    fragColor=vec4(color,clamp(alpha,0.0,1.0));
}
void main() {
    int kind = materialKind;
    float v = clamp(uv.y - float(kind) * 2.0, 0.0, 1.0);
    if(kind>=9){duelMaterial(kind,v);return;}
    float alpha = tint.a * ColorModulator.a;
    vec3 base = tint.rgb * ColorModulator.rgb;
    vec2 p = vec2(uv.x * 4.0, sin(v * TAU) * 1.2);
    float n = noise(p) * .62 + noise(p * 2.3 + 11.0) * .26 + noise(p * 5.1 + 19.0) * .12;
    if (kind <= 1) {
        // The fading solid pass uses ordered coverage, retaining its own depth.
        // It never changes the world color mask or clears the existing world.
        float threshold = hash(floor(gl_FragCoord.xy));
        if (alpha < .999 && threshold > alpha) discard;
        vec3 normal = normalize(cross(dFdx(surfacePosition), dFdy(surfacePosition)));
        if (!gl_FrontFacing) normal = -normal;
        float key = .35 + .35 * max(0.0, dot(normal, normalize(vec3(-.3,.85,.45))));
        // Longitudinal UVs also describe each rope's round section, smoothing its low-poly silhouette.
        key += .30 * (.5 + .5 * cos(v * TAU - .9));
        float rib = pow(.5 + .5 * sin(uv.x * 39.0 + n * 7.0 + v * TAU), 8.0);
        float crease = smoothstep(.14,.44,n);
        vec3 color = base * (.38 + key * .65) * (.7 + .3 * crease);
        color += vec3(.12,.105,.09) * rib * key * .37;
        if (kind == 1) color = base * (.60 + n * .28);
        fragColor = vec4(color, 1.0);
        return;
    }
    float edge = smoothstep(0.0,.16,v) * (1.0 - smoothstep(.84,1.0,v));
    vec3 color = base;
    if (kind == 2) {
        // Fine coherent filaments, not independently flickering noise particles.
        float filament = pow(.5 + .5 * sin(uv.x * 19.0 + n * 4.0), 5.0);
        alpha *= edge * (.54 + n * .28 + filament * .18);
        color *= .75 + filament * .25;
    } else if (kind == 3) {
        float flow = pow(.5 + .5 * sin(uv.x * 8.5 + sin(v * TAU) * 3.0 + n * 2.2), 4.0);
        alpha *= .46 + n * .28 + flow * .26;
        color = mix(base * (.64 + n * .25), min(vec3(1.0),base * 1.17), flow * .68);
    } else if (kind == 4) {
        alpha *= edge * (.64 + .36 * noise(vec2(uv.x * 3.0, 2.0)));
        color = mix(base, vec3(1.0,.96,.99), .25);
    } else if (kind == 5) {
        vec2 q = vec2(fract(uv.x),v) * 2.0 - 1.0;
        float cross = exp(-abs(q.x * q.y) * 26.0);
        alpha *= pow(max(0.0,1.0 - length(q) * .72),1.25) * (.28 + .72 * cross);
        color = mix(base,vec3(1.0,.97,1.0),.44);
    } else if (kind == 6) {
        float fine = .5 + .5 * sin(uv.x * 12.0 + sin(v * TAU) * 2.2);
        color = mix(base * .91,vec3(1.0,.98,1.0),fine * .7);
        alpha *= .87 + n * .13;
    } else if (kind == 7) {
        alpha *= edge * smoothstep(0.0,.18,fract(uv.x)) * (1.0 - smoothstep(.82,1.0,fract(uv.x)));
        color *= .82 + n * .18;
    } else {
        vec2 q = vec2(uv.x,v) * 2.0 - 1.0;
        float distance = length(q);
        alpha *= exp(-distance * distance * 6.0) * (1.0 - smoothstep(.7,1.0,distance));
        color = mix(base,vec3(1.0,.70,.92),exp(-distance * distance * 24.0) * .5);
    }
    if (alpha < .006) discard;
    fragColor = vec4(min(color,vec3(1.0)),clamp(alpha,0.0,1.0));
}

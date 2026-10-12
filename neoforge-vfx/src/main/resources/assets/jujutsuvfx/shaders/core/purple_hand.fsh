#version 150

// GLSL pow is undefined for negative bases, including pow(x, 2.0).
float vfxSquare(float x) { return x * x; }
vec2 vfxSquare(vec2 x) { return x * x; }
vec3 vfxSquare(vec3 x) { return x * x; }
vec4 vfxSquare(vec4 x) { return x * x; }
in vec2 uv;
flat in vec4 material;
uniform float Time, Charge, Release, Visibility, Seed;
out vec4 fragColor;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p) {
    vec2 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);
    return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+1.0),f.x),f.y);
}
float beam(vec2 p, float angle, float reach, float thickness) {
    vec2 direction=vec2(cos(angle),sin(angle));
    float along=abs(dot(p,direction)), across=abs(dot(p,vec2(-direction.y,direction.x)));
    return exp(-across*across/(thickness*thickness)) * exp(-along/reach) * (1.0-smoothstep(reach,reach*2.6,along));
}
void vfxMaterialMain() {
    vec2 p=uv;
    float r=length(p), angle=atan(p.y,p.x);
    if(r>1.0) discard;
    float grow=smoothstep(0.0,1.0,Charge);
    float released=step(0.0,Release), releaseAge=max(Release,0.0);
    float flash=released*exp(-releaseAge*32.0);
    float n=noise(p*5.7+vec2(Time*.37,-Time*.24)+Seed*.1);
    float clouds=noise(p*3.1+vec2(-Time*.14,Time*.21)+Seed*.07);
    vec2 cloudShape=p*vec2(.92,1.18)+vec2((n-.5)*.14,(clouds-.5)*.13);
    float mist=exp(-dot(cloudShape,cloudShape)*4.7)*(.30+.70*n)*(1.0-exp(-r*r*150.0));
    mist*=.26+.28*grow;
    float core=exp(-vfxSquare(r/(.039+.012*grow+.025*flash)));
    float halo=exp(-r*r*95.0)*(.35+.30*grow);
    float flares=beam(p,0.0,.25+.07*grow,.0026)
                +beam(p,1.5708,.26+.08*grow,.0025)
                +beam(p,.69,.115+.06*grow,.0032)*.80
                +beam(p,2.37,.135+.05*grow,.0028)*.70;
    flares*=.65+.65*grow;
    // Broken, clustered stellar circles: random tangential cells avoid a clock face of regular spikes.
    float rings=0.0, specks=0.0;
    for(int i=0;i<2;i++) {
        float life=fract(Time*.46+float(i)*.5);
        float radius=.18+.72*(1.0-life);
        vec2 direction=p/max(r,.001);
        float cloudBand=noise(direction*3.7+vec2(Time*.16,float(i)*11.7)+Seed*.013);
        float fineBand=noise(direction*19.0+vec2(-Time*.24,float(i)*19.1));
        float wave=(cloudBand-.5)*.010;
        float ridge=exp(-vfxSquare((r-radius+wave)/.0040));
        float grain=.045+.58*smoothstep(.32,.70,cloudBand)*(.20+.80*fineBand);
        float envelope=smoothstep(0.0,.12,life)*(1.0-smoothstep(.84,1.0,life));
        rings+=ridge*grain*envelope*(.28+.40*grow);
        float cells=83.0+float(i)*26.0;
        float arc=(angle/6.2831853+.5)*cells+Time*(i==0?.22:-.16);
        float sector=mod(floor(arc),cells), within=fract(arc);
        float pick=hash(vec2(sector+Seed*.071,float(i)*39.7));
        float point=.17+.66*hash(vec2(sector+float(i)*7.1,Seed+.31));
        float clump=smoothstep(.36,.66,cloudBand);
        float present=step(.35,pick)*clump;
        float radial=r-radius-(hash(vec2(sector+Seed,float(i)+3.1))-.5)*.016;
        float tangential=(within-point)*6.2831853*max(radius,.12)/cells;
        float dustSize=.0017+.0038*hash(vec2(sector*.67+Seed,float(i)+8.9));
        float dust=exp(-(tangential*tangential+radial*radial)/(dustSize*dustSize));
        // Only a few bright knots receive short glints, with unequal radial and tangential extents.
        float glint=step(.94,pick)*exp(-tangential*tangential/.000010)*exp(-abs(radial)/.010)*.40;
        specks+=(dust+glint)*present*envelope*(.48+.60*grow);
    }
    rings*=1.0-released; specks*=1.0-released;
    float glow=core*1.4+halo*.7+flares*.76+rings*.75+specks*.8;
    float alpha=clamp(mist+glow,0.0,1.0)*Visibility*(1.0-smoothstep(.92,1.0,r));
    if(alpha<.002) discard;
    vec3 cloudColor=mix(vec3(.065,.025,.24),vec3(.25,.035,.43),clouds);
    vec3 energy=cloudColor*mist+vec3(3.7,3.35,4.0)*core
                +vec3(.86,.11,1.6)*halo+vec3(1.85,1.34,2.12)*flares
                +vec3(1.30,.35,1.75)*rings+vec3(2.1,1.34,1.65)*specks;
    fragColor=vec4(energy/max(.04,mist+glow),alpha);
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

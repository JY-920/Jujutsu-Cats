#version 150

// GLSL pow is undefined for negative bases, including pow(x, 2.0).
float vfxSquare(float x) { return x * x; }
vec2 vfxSquare(vec2 x) { return x * x; }
vec3 vfxSquare(vec3 x) { return x * x; }
vec4 vfxSquare(vec4 x) { return x * x; }
uniform float Time, Age, Seed, Power, Kind, Pass;
in vec2 uv;
in vec4 tint;
out vec4 fragColor;

float hash3(vec3 p){p=fract(p*.1031);p+=dot(p,p.yzx+33.33);return fract((p.x+p.y)*p.z);}
float hash2(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float grain(vec2 p){
    vec2 c=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    return mix(mix(hash2(c),hash2(c+vec2(1,0)),f.x),mix(hash2(c+vec2(0,1)),hash2(c+1.0),f.x),f.y);
}
float blueNoise(vec3 p){
    vec3 c=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    return mix(mix(mix(hash3(c),hash3(c+vec3(1,0,0)),f.x),
                   mix(hash3(c+vec3(0,1,0)),hash3(c+vec3(1,1,0)),f.x),f.y),
               mix(mix(hash3(c+vec3(0,0,1)),hash3(c+vec3(1,0,1)),f.x),
                   mix(hash3(c+vec3(0,1,1)),hash3(c+vec3(1,1,1)),f.x),f.y),f.z);
}
void over(inout vec4 base,vec4 top){base.rgb=base.rgb*(1.0-top.a)+top.rgb;base.a=base.a*(1.0-top.a)+top.a;}

// Same uneven white heart, cyan mantle and torn deep-blue folds as limitless_volume's Blue.
// A small knuckle card integrates ten slices; there is no per-cat screen copy or framebuffer.
vec4 blueField(vec3 q){
    float r=length(q);if(r>2.45)return vec4(0);
    float t=floor((Time+Seed*.071)*24.0)/24.0;
    vec3 curl=vec3(sin(q.y*2.4+t*3.7),sin(q.z*2.1-t*4.3),sin(q.x*2.7+t*2.9));
    vec3 flow=q+curl*.18;
    float broad=blueNoise(flow*2.2+vec3(t*.71,-t*1.62,t*.43));
    float detail=blueNoise(flow*5.7+vec3(-t*1.3,t*.63,t*1.1));
    float surface=r+(broad-.5)*.50+(detail-.5)*.17;
    float heart=1.0-smoothstep(.66,.85,surface);
    float mantle=(1.0-smoothstep(.91,1.47,surface))*(1.0-heart);
    float fold=.5+.5*sin(q.y*5.2-q.x*3.8+q.z*2.9-r*8.3+broad*9.0-t*7.4);
    float torn=smoothstep(.39,.70,broad*.64+detail*.36);
    float shreds=smoothstep(.97,1.23,surface)*(1.0-smoothstep(1.58,2.30,surface))*torn*(.24+pow(fold,3.0)*1.5);
    float tip=pow(fold,7.0)*smoothstep(.45,.75,detail)*exp(-vfxSquare((surface-1.90)/.31));
    vec3 mantleColor=Kind>.5?vec3(2.65,.018,.085):vec3(.018,1.72,2.65);
    vec3 darkColor=Kind>.5?vec3(1.55,.007,.046):vec3(.012,.035,1.55);
    vec3 color=mix(mantleColor,darkColor,smoothstep(.92,1.91,surface));
    color=mix(color,(Kind>.5?vec3(2.95,.14,.28):vec3(.14,2.30,2.95)),shreds*.36+tip*.28);
    color=mix(color,vec3(4.8,5.1,5.5),smoothstep(.08,.86,heart));
    return vec4(color,heart*3.2+mantle*.25+shreds*.43+tip*.32);
}
vec4 blue(vec2 q){
    vec4 sum=vec4(0);
    for(int i=0;i<10;i++){
        vec4 f=blueField(vec3(q,-2.205+float(i)*.49));
        float a=1.0-exp(-f.a*.49);
        sum.rgb+=(1.0-sum.a)*f.rgb*a;sum.a+=(1.0-sum.a)*a;
        if(sum.a>.99)break;
    }
    // The paw remains visible through the wrapping; its white nucleus sits at the knuckles.
    return sum*.68;
}

// Red shares Blue's complete hand/contact design, with only its palette changed.
vec4 impact(vec2 p){
    float r=length(p),angle=atan(p.y,p.x+.00001);
    float fade=1.0-smoothstep(.36,.79,Age);
    float collapse=1.0-smoothstep(0.0,.24,Age)*.80;
    vec4 sum=blue(p/max(collapse,.1));
    sum*=1.0-smoothstep(.22,.46,Age);
    float implosion=exp(-vfxSquare((Age-.20)/.055));
    sum.rgb+=(Kind>.5?vec3(4.0,2.6,2.9):vec3(2.6,3.7,4.0))*exp(-r*r*21.0)*implosion;
    float ring=.30+max(0.0,Age-.18)*3.6;
    float wave=exp(-vfxSquare((r-ring+.055*sin(angle*5.0-Time*6.0))/.038))*smoothstep(.17,.25,Age)*fade;
    sum.rgb+=(Kind>.5?vec3(2.0,.09,.22):vec3(.09,1.30,2.0))*wave*.72;
    sum.a=max(sum.a,wave*.18);
    return sum*fade;
}
void vfxMaterialMain(){
    if(Pass>2.5){
        float r=length(uv),theta=atan(uv.y,uv.x+.00001);
        float radius=.16+.57*(1.0-exp(-Age*15.0));
        float edge=exp(-vfxSquare((r-radius+.012*sin(theta*9.0+Seed))/.025));
        float heart=exp(-r*r*40.0)*exp(-Age*19.0);
        float halo=exp(-r*r*5.0)*exp(-Age*9.0)*.20;
        float border=1.0-smoothstep(.86,1.0,r);
        fragColor=vec4(tint.rgb*(edge*1.8+halo)+vec3(3.3)*heart,edge*.20+heart*.48+halo*.08)*tint.a*border;
        return;
    }
    if(Pass>1.5){
        float x=abs(uv.x),body=1.0-smoothstep(.12,1.0,x);
        float frost=.83+.17*grain(gl_FragCoord.xy*.7+vec2(Time,-Time));
        float a=body*tint.a;
        fragColor=vec4(tint.rgb*a*frost*(1.1+Power*.5),a*.62);
        return;
    }
    vec4 result=Pass>.5?impact(uv*2.5):blue(uv*2.5);
    float edge=1.0-smoothstep(.89,1.0,length(uv));
    result*=edge;
    result.rgb*=.88+.20*Power;
    fragColor=result*tint;
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

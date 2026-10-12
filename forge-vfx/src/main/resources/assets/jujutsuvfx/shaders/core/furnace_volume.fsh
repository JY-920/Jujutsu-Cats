#version 150

// GLSL pow is undefined for negative bases, including pow(x, 2.0).
float vfxSquare(float x) { return x * x; }
vec2 vfxSquare(vec2 x) { return x * x; }
vec3 vfxSquare(vec3 x) { return x * x; }
vec4 vfxSquare(vec4 x) { return x * x; }
uniform sampler2D SceneDepth;
uniform mat4 InverseViewProjection;
uniform vec3 CenterRelative, Axis, Right, Up;
uniform float Time, Phase, PhaseTime, Radius;
in vec2 texCoord;
in vec4 tint;
out vec4 fragColor;

float hash(vec3 p){p=fract(p*.1031);p+=dot(p,p.yzx+33.33);return fract((p.x+p.y)*p.z);}
float noise(vec3 p){
    vec3 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),
               mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);
}
float fbm(vec3 p){return noise(p)*.57+noise(p*2.03+7.1)*.29+noise(p*4.13-5.7)*.14;}
float segment(vec3 p,vec3 a,vec3 b){vec3 v=b-a;return length(p-a-v*clamp(dot(p-a,v)/dot(v,v),0.0,1.0));}
vec3 fireColor(float heat){
    vec3 c=mix(vec3(.65,.009,.001),vec3(1.65,.24,.006),smoothstep(.04,.52,heat));
    c=mix(c,vec3(2.10,1.30,.32),smoothstep(.46,.86,heat));
    return mix(c,vec3(2.6,2.30,1.75),smoothstep(.82,1.0,heat));
}
vec4 arrowFire(vec3 world){
    vec3 delta=world-CenterRelative;
    vec3 q=vec3(dot(delta,Right),dot(delta,Up),dot(delta,Axis));
    float n=fbm(q*3.6-vec3(0,Time*3.8,Time*5.0));
    float d;
    if(Phase<.5){
        // A curved upright bow, two taut string segments, a long shaft and split arrowhead.
        float bow=max(length(vec2(q.x,length(q.yz-vec2(0,-.8))-1.75))-.065,-q.z-.18);
        float string=min(segment(q,vec3(0,1.64,-.18),vec3(0,0,-1.05)),
                         segment(q,vec3(0,-1.64,-.18),vec3(0,0,-1.05)))-.030;
        float shaft=segment(q,vec3(0,0,-1.05),vec3(0,0,2.8))-.055;
        float head=min(segment(q,vec3(0,0,2.8),vec3(0,.34,2.15)),
                       segment(q,vec3(0,0,2.8),vec3(0,-.34,2.15)))-.045;
        d=min(min(bow,string),min(shaft,head));
        float forming=smoothstep(.12,1.12,PhaseTime);
        d=mix(length(q-vec3(0,0,-.25))-.22,d,forming);
    }else{
        // The point leads a narrowing, turbulent tail. No stretching rectangle or stationary beam.
        float tailLength=min(8.5,3.85+PhaseTime*72.0);
        float along=clamp(-q.z/tailLength,0.0,1.0), taper=1.0-along;
        vec2 wake=vec2(sin(q.z*2.7+Time*23.0),cos(q.z*3.3+Time*19.0))*.13*along;
        vec3 bent=q-vec3(wake,0);
        float shaft=segment(bent,vec3(0,0,-tailLength),vec3(0,0,-.35))-(.035+.19*sqrt(taper));
        float head=max(max(q.z,-q.z-.95),length(q.xy)-clamp(-q.z,0.0,.95)*.42);
        d=min(shaft,head);
    }
    float fringe=.09+.18*n;
    float density=exp(-vfxSquare(max(d,0.0)/fringe));
    if(Phase<.5)density*=smoothstep(0.0,.24,PhaseTime);
    float heat=clamp((.27-d)*3.25+n*.14,0.0,1.0);
    return vec4(fireColor(heat),density);
}
vec4 furnaceFire(vec3 world){
    float elapsed=PhaseTime;
    float growth=.04+.96*(1.0-exp(-elapsed*6.0));
    vec3 q=(world-CenterRelative)/Radius;
    vec3 flow=q*4.2-vec3(0,elapsed*1.65,0);
    flow.xz+=vec2(sin(q.y*4.0+elapsed),cos(q.y*3.7-elapsed))*.28;
    float n=fbm(flow), detail=noise(flow*3.1+elapsed*.8);
    float cloud=length(vec3(q.x,q.y*.96-.45*growth-elapsed*.035,q.z));
    float edge=.76*growth+(n-.48)*.32*growth;
    float billow=1.0-smoothstep(edge-.14*growth,edge+.08,cloud);
    // The connected billowing fireball supplies the whole silhouette; no separate top chimney.
    float density=billow*(.55+.45*detail);
    density*=smoothstep(-.11,-.025,q.y)*(1.0-smoothstep(2.9,4.2,elapsed));
    float core=exp(-cloud*cloud/max(.16*growth*growth,.002));
    float cooling=1.0-smoothstep(1.35,3.9,elapsed);
    float heat=clamp((.25+.43*n+.54*core)*cooling,0.0,1.0);
    vec3 color=mix(vec3(.075,.029,.018),fireColor(heat),smoothstep(.07,.40,heat));
    return vec4(color,density);
}
void vfxMaterialMain(){
    vec4 farPoint=InverseViewProjection*vec4(texCoord*2.0-1.0,1,1);
    vec3 ray=normalize(farPoint.xyz*(farPoint.w<0.0?-1.0:1.0));
    vec3 bound=Phase<.5?CenterRelative+Axis*.7:Phase<1.5?CenterRelative-Axis*4.0:CenterRelative+vec3(0,Radius*.8,0);
    float boundRadius=Phase<.5?3.8:Phase<1.5?6.2:Radius*1.95;
    float along=dot(ray,bound), discriminant=along*along+boundRadius*boundRadius-dot(bound,bound);
    fragColor=vec4(0);
    if(discriminant<=0.0)return;
    float span=sqrt(discriminant), first=max(.035,along-span), last=along+span;
    float depth=texture(SceneDepth,texCoord).r;
    if(depth<.999999){
        vec4 hit=InverseViewProjection*vec4(texCoord*2.0-1.0,depth*2.0-1.0,1);
        last=min(last,dot(hit.xyz/hit.w,ray));
    }
    if(last<=first)return;
    int steps=Phase<1.5?64:48;
    float stepSize=(last-first)/float(steps);
    float jitter=.40+.20*hash(vec3(gl_FragCoord.xy,0));
    vec4 sum=vec4(0);
    for(int i=0;i<64;i++){
        if(i>=steps||sum.a>.995)break;
        vec3 samplePoint=ray*(first+(float(i)+jitter)*stepSize);
        vec4 sampleFire=Phase<1.5?arrowFire(samplePoint):furnaceFire(samplePoint);
        float opacity=1.0-exp(-sampleFire.a*stepSize*(Phase<1.5?11.0:9.0/Radius));
        sum.rgb+=(1.0-sum.a)*sampleFire.rgb*opacity;
        sum.a+=(1.0-sum.a)*opacity;
    }
    fragColor=sum*tint;
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { fragColor = vec4(0.0); }
}

#version 150

// GLSL pow is undefined for negative bases, including pow(x, 2.0).
float vfxSquare(float x) { return x * x; }
vec2 vfxSquare(vec2 x) { return x * x; }
vec3 vfxSquare(vec3 x) { return x * x; }
vec4 vfxSquare(vec4 x) { return x * x; }
uniform sampler2D SceneDepth;
uniform mat4 InverseViewProjection;
uniform vec3 CenterRelative, FusionRight, FusionUp;
uniform float Age, Radius, Seed, Visibility;
in vec2 texCoord;
in vec4 tint;
out vec4 fragColor;

float hash(vec3 p){return fract(sin(dot(p,vec3(127.1,311.7,74.7)))*43758.5453);}
float noise(vec3 p){
    vec3 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),
               mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);
}
mat2 turn(float angle){float s=sin(angle),c=cos(angle);return mat2(c,-s,s,c);}
vec4 field(vec3 local){
    float unit=max(Radius/32.0,.10);
    if(Age<1.0){
        float gather=smoothstep(0.0,.82,Age),collapse=smoothstep(.68,1.0,Age);
        float orbit=(1.0-gather)*3.8*unit,orb=unit*(1.22+.38*gather)*(1.0-collapse*.55);
        // Start at the exact endpoints of the incoming morph; the orbit then continues
        // smoothly around the collision's shared basis for every observer.
        vec3 q=vec3(dot(local,FusionRight),dot(local,FusionUp),dot(local,cross(FusionUp,FusionRight)));
        q.xy=turn(Age*2.2)*q.xy;
        float blue=length(q-vec3(orbit,0,0))/orb,red=length(q+vec3(orbit,0,0))/orb;
        float cloud=noise(q/max(unit,.1)*2.9+vec3(Seed*.021,Age*3.0,-Age*2.1));
        float a=exp(-blue*blue*1.55),b=exp(-red*red*1.55);
        float narrow=exp(-dot(q.yz,q.yz)/(orb*orb*.11));
        float link=narrow*exp(-q.x*q.x/max(orbit*orbit+orb*orb,.01))*.55*gather;
        float strands=pow(.5+.5*sin(atan(q.z,q.y+.00001)*4.0+q.x*4.0/unit-Age*13.0),5.0);
        float sheath=exp(-vfxSquare((length(q)/(2.1*unit)-1.0)/.25))*strands*.15;
        float density=(a+b+link+sheath)*(1.0+cloud*.8);
        vec3 color=(vec3(.025,.62,2.8)*a+vec3(2.5,.018,.15)*b)/max(a+b,.001);
        float white=pow(max(a,b),5.0)*(.5+.5*cloud);
        color=mix(color,vec3(3.6,2.8,4.0),white*.80);
        color=mix(color,vec3(.065,.024,.24),collapse*.93);
        density*=1.0+collapse*1.2;
        return vec4(color,density);
    }
    float elapsed=Age-1.0,front=Radius*clamp(elapsed,.05,1.0);
    vec3 q=local/max(front,.10);float r=length(q);
    vec3 drift=q*5.7+vec3(Seed*.071,-elapsed*.70,elapsed*.43);
    float cloud=noise(drift)*.67+noise(drift*2.1+3.7)*.33;
    float ripples=.5+.5*sin(r*36.0-elapsed*9.0+cloud*9.0);
    float curl=vfxSquare(clamp((cloud-.31)*2.1,0.0,1.0));
    float outer=1.0-smoothstep(.70,1.05,r),inner=smoothstep(.08,.42,r);
    // Thin, torn gas sheets allow terrain and the new crater to remain visible after the pulse.
    float density=(.075+.23*curl)*outer*inner*(.45+.55*ripples);
    float fade=1.0-smoothstep(1.25,4.50,elapsed);
    density*=fade;
    float shell=exp(-vfxSquare((r-.87-cloud*.08)/.075));
    density+=shell*(.22+.38*curl)*exp(-elapsed*.75);
    float hot=exp(-elapsed*7.5)*exp(-r*r*1.8);
    density+=hot*.58;
    vec3 violet=mix(vec3(.075,.025,.34),vec3(.65,.09,1.65),curl);
    vec3 color=mix(violet,vec3(1.20,.36,2.0),shell*.5);
    color=mix(color,vec3(3.3,2.80,3.8),hot);
    float stars=pow(noise(q*61.0+Seed*.09),29.0)*55.0;
    color+=vec3(.90,.50,1.20)*stars*outer*fade;
    return vec4(color,density);
}
void vfxMaterialMain(){
    fragColor=vec4(0);
    if(Age>=5.5)return;
    float unit=max(Radius/32.0,.10);
    float scale=Age<1.0?unit*2.1:max(.10,Radius*clamp(Age-1.0,.05,1.0));
    float bound=Age<1.0?unit*6.5:scale*1.09;
    vec4 farPoint=InverseViewProjection*vec4(texCoord*2.0-1.0,1,1);
    vec3 ray=normalize(farPoint.xyz*(farPoint.w<0.0?-1.0:1.0));
    float along=dot(ray,CenterRelative),disc=along*along+bound*bound-dot(CenterRelative,CenterRelative);
    if(disc<=0.0)return;
    float root=sqrt(disc),first=max(.025,along-root),last=along+root;
    float depth=texture(SceneDepth,texCoord).r;
    if(depth<.999999){
        vec4 scene=InverseViewProjection*vec4(texCoord*2.0-1.0,depth*2.0-1.0,1);
        last=min(last,dot(scene.xyz/scene.w,ray));
    }
    if(last<=first)return;
    float stepSize=(last-first)/32.0,jitter=.35+.30*hash(vec3(gl_FragCoord.xy,0));
    vec4 sum=vec4(0);
    for(int i=0;i<32;i++){
        vec4 f=field(ray*(first+(float(i)+jitter)*stepSize)-CenterRelative);
        float a=1.0-exp(-f.a*stepSize*3.2/max(scale,.10));
        sum.rgb+=(1.0-sum.a)*f.rgb*a;sum.a+=(1.0-sum.a)*a;
        if(sum.a>.985)break;
    }
    fragColor=sum*Visibility*tint;
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { fragColor = vec4(0.0); }
}

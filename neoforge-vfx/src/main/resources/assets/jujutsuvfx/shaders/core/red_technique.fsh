#version 150

// GLSL pow is undefined for negative bases, including pow(x, 2.0).
float vfxSquare(float x) { return x * x; }
vec2 vfxSquare(vec2 x) { return x * x; }
vec3 vfxSquare(vec3 x) { return x * x; }
vec4 vfxSquare(vec4 x) { return x * x; }
uniform sampler2D SceneColor, SceneDepth;
uniform mat4 InverseViewProjection;
uniform vec3 CenterRelative, Axis;
uniform vec4 CoreScreen;
uniform vec2 ScreenSize;
uniform float Time, PhaseTime, FlightTime, Phase, Charge, CoreRadius, Radius, Visibility;
uniform float BeamMode, BeamLength, BeamRadius, UseDepth, Distortion, Seed, SceneReady;
in vec2 texCoord;
in vec4 tint;
out vec4 fragColor;

float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float noise(vec2 p){vec2 c=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(c),hash(c+vec2(1,0)),f.x),mix(hash(c+vec2(0,1)),hash(c+1.0),f.x),f.y);}
float square(float x){return x*x;}
vec2 sphere(vec3 ray,vec3 center,float radius){float b=dot(ray,center),d=b*b-dot(center,center)+radius*radius;return d<=0.0?vec2(1,-1):vec2(max(.025,b-sqrt(d)),b+sqrt(d));}

// Intersect a finite cone/cylinder exactly. The taper is also used by server carving/hits.
vec2 cone(vec3 ray,float z0,float z1,float base,float slope){
    if(z1<=z0)return vec2(1,-1);
    vec3 o=-CenterRelative;
    float oz=dot(o,Axis),dz=dot(ray,Axis),lo=.025,hi=1000000.0;
    if(abs(dz)<.00001){if(oz<z0||oz>z1)return vec2(1,-1);}
    else {float a=(z0-oz)/dz,b=(z1-oz)/dz;lo=max(lo,min(a,b));hi=min(hi,max(a,b));}
    vec3 p=o-Axis*oz,d=ray-Axis*dz;
    float r=base+slope*oz,dr=slope*dz;
    float a=dot(d,d)-dr*dr,b=dot(p,d)-r*dr,c=dot(p,p)-r*r;
    if(abs(a)<.000001){
        if(abs(b)<.000001){if(c>0.0)return vec2(1,-1);}
        else if(b>0.0)hi=min(hi,-c/(2.0*b));else lo=max(lo,-c/(2.0*b));
    }else{
        float disc=b*b-a*c;
        if(disc<0.0){if(a>0.0)return vec2(1,-1);}
        else{
            float s=sqrt(disc),u=(-b-s)/a,v=(-b+s)/a;
            float nearRoot=min(u,v),farRoot=max(u,v);
            if(a>0.0){lo=max(lo,nearRoot);hi=min(hi,farRoot);}
            else if(lo<nearRoot)hi=min(hi,nearRoot);else lo=max(lo,farRoot);
        }
    }
    return vec2(lo,hi);
}
vec2 beam(vec3 ray,float radius){
    vec2 a=cone(ray,0.0,min(7.04,BeamLength),radius*.12,radius/8.0);
    vec2 b=cone(ray,7.04,BeamLength,radius,0.0);
    if(a.y<a.x)return b;if(b.y<b.x)return a;
    return vec2(min(a.x,b.x),max(a.y,b.y));
}

// Stable red-white material: pressure is communicated by inward motion, without white flashes.
// Closest distance from the visible camera ray to one bounded energy strand.
// The field is culled by its bounding sphere before these 24 short strands are evaluated.
float filament(vec3 ray,vec3 a,vec3 b,float width,float depth){
    vec3 d=b-a;float dd=dot(d,d),rd=dot(ray,d),ra=dot(ray,a),da=dot(d,a);
    float u=clamp((rd*ra-da)/max(.00001,dd-rd*rd),0.0,1.0);
    vec3 point=a+d*u;float t=dot(point,ray);
    if(t<=.025||t>=depth)return 0.0;
    float dist=length(point-ray*t),aa=max(width,t/ScreenSize.y*.8);
    float line=exp(-square(dist/aa))*min(1.0,width/aa);
    return line*smoothstep(0.0,.10,u)*(1.0-smoothstep(.72,1.0,u));
}
vec3 chargeRays(vec3 ray,float depth){
    float extent=2.0+Charge*6.0;
    if(sphere(ray,CenterRelative,extent).y<.025)return vec3(0);
    float collapse=smoothstep(.92,1.0,Charge),energy=0.0;
    for(int i=0;i<24;i++){
        float id=float(i),h=hash(vec2(id,Seed));
        float y=1.0-2.0*(id+.5)/24.0,a=id*2.399963+Seed*.03+Time*.08;
        vec3 direction=vec3(sqrt(1.0-y*y)*cos(a),y,sqrt(1.0-y*y)*sin(a));
        float travel=1.0-fract(Time*(.82+Charge*.65)+h);
        float r=mix(.30,extent,travel*travel)*(1.0-collapse*.62);
        float length=mix(.22,2.8,Charge)*(.6+h)*(.35+travel);
        energy+=filament(ray,CenterRelative+direction*max(.24,r-length),
                CenterRelative+direction*r,.009+Charge*.008,depth);
        // Fainter unbroken rails connect the moving streaks to the compressed core.
        energy+=filament(ray,CenterRelative+direction*.28,CenterRelative+direction*(extent*(.5+h*.5)),.0045,depth)*.15*Charge;
    }
    return vec3(2.4,.018,.105)*energy*smoothstep(.04,.35,Time);
}
vec3 handRays(vec2 point){
    float radius=length(point),angle=atan(point.y,point.x),sum=0.0;
    float extent=mix(2.3,5.8,Charge)*(1.0-smoothstep(.92,1.0,Charge)*.40);
    for(int i=0;i<18;i++){
        float id=float(i),h=hash(vec2(id,Seed));
        float a=id*2.399963+h*.26+Time*.06;
        float delta=atan(sin(angle-a),cos(angle-a));
        float travel=1.0-fract(Time*(.9+Charge*.55)+h);
        float outer=1.15+travel*travel*extent,inner=max(1.03,outer-.7-Charge*1.7);
        float width=max(.016,fwidth(angle)*1.1);
        sum+=exp(-square(delta/width))*smoothstep(inner,inner+.20,radius)*(1.0-smoothstep(outer-.2,outer,radius));
    }
    return vec3(1.8,.015,.065)*sum*smoothstep(.02,.26,Time)*.75;
}
float pressureRing(vec3 ray,float z,float radius,float width,float depth){
    float den=dot(ray,Axis);if(abs(den)<.007)return 0.0;
    float t=(dot(CenterRelative,Axis)+z)/den;
    if(t<=.025||t>=depth)return 0.0;
    vec3 p=ray*t-CenterRelative-Axis*z;
    float r=length(p),aa=max(width,t/ScreenSize.y*.7);
    vec3 right=normalize(cross(Axis,abs(Axis.y)>.96?vec3(0,0,1):vec3(0,1,0))),up=cross(right,Axis);
    float a=atan(dot(p,up),dot(p,right));
    float grain=.65+.35*noise(vec2(a*28.0-z,Time*3.0));
    return exp(-square((r-radius)/aa))*min(1.0,width/aa)*grain;
}
vec3 nucleus(vec2 p,float face,float flying){
    vec3 normal=vec3(p,face);
    vec2 flow=p*3.1+vec2(face*.78,-face*.51);
    float cloud=noise(flow+vec2(Time*.27,-Time*.19)+Seed*.013);
    float fold=noise(flow*2.17+vec2(-Time*.16,Time*.23));
    float grainScale=104.0;
    vec2 grainUv=(p+normal.z*vec2(.17,-.23))*grainScale;
    float grainFilter=1.0/(1.0+length(fwidth(grainUv))*1.7);
    float frost=(noise(grainUv)*.72+noise(grainUv*1.83+17.1)*.28-.5)*grainFilter;
    float hot=exp(-dot(p-vec2(-.11,.13),p-vec2(-.11,.13))*2.45);
    float white=smoothstep(.49,.78,hot*.46+cloud*.41+fold*.22);
    float facing=clamp(.35+.50*face-.15*p.x+.09*p.y,0.0,1.0);
    vec3 red=mix(vec3(.42,.002,.017),vec3(1.24,.012,.060),facing);
    vec3 pearl=vec3(2.6,1.8,1.95);
    vec3 color=mix(red,pearl,white*.94);
    color*=.91+frost*.43;
    color+=vec3(.20,.006,.020)*pow(1.0-face,4.0);
    return color*(1.0+flying*.09);
}

void vfxMaterialMain(){
    vec4 farPoint=InverseViewProjection*vec4(texCoord*2.0-1.0,1,1);
    vec3 ray=normalize(farPoint.xyz*(farPoint.w<0.0?-1.0:1.0));
    float depth=UseDepth>.5&&SceneReady>.5?texture(SceneDepth,texCoord).r:1.0,worldDepth=1000000.0;
    if(UseDepth>.5&&depth<.999999){vec4 p=InverseViewProjection*vec4(texCoord*2.0-1.0,depth*2.0-1.0,1);worldDepth=dot(p.xyz/p.w,ray);}
    vec2 size=ScreenSize,aspect=vec2(size.x/size.y,1.0),border=.5/size;
    vec3 light=vec3(0);float alpha=0.0;vec2 warp=vec2(0);
    bool cannon=BeamMode>.5&&Phase>.5,burst=Phase>1.5;
    bool hand=UseDepth<.5;
    if(hand&&Phase<.5){
        // The hand nucleus is projected directly, just like the existing fist/Purple
        // materials. GUI matrices, ray inversion and scene depth cannot hide it.
        vec2 p=(texCoord-CoreScreen.xy)*aspect/max(CoreScreen.z*CoreRadius,.0001);
        float r=length(p),aa=max(.006,1.3/(ScreenSize.y*max(CoreScreen.z*CoreRadius,.0001)));
        float halo=exp(-vfxSquare(max(0.0,r-1.0)*6.0))*(1.0-smoothstep(1.15,1.38,r));
        light+=vec3(1.3,.008,.055)*halo*.32;
        light+=handRays(p);
        float body=1.0-smoothstep(1.0-aa,1.0+aa,r);
        if(body>.001){
            float face=sqrt(max(0.0,1.0-r*r));
            vec3 color=nucleus(p,face,0.0);
            light=light*(1.0-body)+color*body;alpha=alpha*(1.0-body)+body;
        }
    }else if(cannon){
        float age=max(FlightTime,0.0),alive=burst?exp(-PhaseTime*6.0):smoothstep(0.0,.035,age);
        // The saturated shaft and narrow white filament remain visible from beside and behind.
        // Its wider translucent envelope still marks the real damage corridor.
        float radius=BeamRadius;
        vec2 shell=beam(ray,radius*.16),core=beam(ray,radius*.043),veil=beam(ray,radius);
        shell.y=min(shell.y,worldDepth);core.y=min(core.y,worldDepth);veil.y=min(veil.y,worldDepth);
        if(veil.y>veil.x){
            float z=dot(ray*veil.x-CenterRelative,Axis);
            float lane=.5+.5*sin(z*.8-Time*24.0);
            light+=vec3(.30,.001,.009)*(1.0-exp(-(veil.y-veil.x)/radius))*.08*alive;
            warp+=vec2(sin(z+Time*7.0),cos(z-Time*6.0))*.0007*alive*lane;
        }
        if(shell.y>shell.x){
            float density=1.0-exp(-(shell.y-shell.x)/max(radius*.11,.02));
            light+=vec3(2.1,.018,.12)*density*.85*alive;
            alpha=max(alpha,density*.23*alive);
        }
        if(core.y>core.x){
            float fill=1.0-exp(-(core.y-core.x)/max(radius*.035,.01));
            light+=vec3(3.4,2.45,2.65)*fill*alive;
            alpha=max(alpha,fill*.58*alive);
        }
        // Staggered pressure fronts travel away from the hand, not hoops orbiting the caster.
        for(int i=0;i<4;i++){
            float n=float(i),delay=.045+n*.075,rage=age-delay;
            if(rage<=0.0)continue;
            float z=2.5+n*(5.0+BeamLength*.09)+min(4.0,rage*3.0);
            if(z>BeamLength+.5)continue;
            float scale=(1.0-exp(-rage*13.0));
            float radiusRing=BeamRadius*(1.32-n*.20)*scale;
            float fade=alive*exp(-max(0.0,rage-.35)*1.7);
            float line=pressureRing(ray,z,radiusRing,.035+radiusRing*.017,worldDepth);
            float glow=pressureRing(ray,z,radiusRing,.16+radiusRing*.08,worldDepth);
            light+=(vec3(2.7,.35,.63)*line+vec3(.75,.003,.025)*glow*.40)*fade;
            warp+=vec2(sin(texCoord.y*23.0),cos(texCoord.x*21.0))*glow*.0016*fade;
        }
        float muzzleRadius=.25+min(.9,age*6.0);
        vec2 muzzle=sphere(ray,CenterRelative,muzzleRadius);
        if(muzzle.y>muzzle.x&&muzzle.x<worldDepth)
            light+=vec3(2.7,.20,.35)*(1.0-exp(-(muzzle.y-muzzle.x)*4.0))*exp(-age*14.0);
    }else if(!burst){
        if(Phase<.5)light+=chargeRays(ray,worldDepth);
        else {
            // Short press: a compact repulsive dart with a tapered tail, not a scaled cannon.
            float trail=filament(ray,CenterRelative-Axis*(CoreRadius*5.8),CenterRelative,
                    CoreRadius*.18,worldDepth);
            light+=vec3(2.4,.11,.23)*trail;
        }
        float r=max(CoreRadius,.025);
        vec2 hit=sphere(ray,CenterRelative,r),halo=sphere(ray,CenterRelative,r*1.32);
        hit.y=min(hit.y,worldDepth);halo.y=min(halo.y,worldDepth);
        if(halo.y>halo.x){
            float h=1.0-exp(-(halo.y-halo.x)/r);
            light+=vec3(1.45,.007,.055)*h*.30;
        }
        if(hit.y>hit.x){
            vec3 n=normalize(ray*hit.x-CenterRelative);
            float face=max(0.0,-dot(n,ray));
            vec2 p=(texCoord-CoreScreen.xy)*aspect/max(CoreScreen.z*r,.0001);
            vec3 color=nucleus(p,face,Phase>.5?1.0:0.0);
            float body=1.0-exp(-(hit.y-hit.x)*27.0/r);
            light=light*(1.0-body)+color*body;alpha=max(alpha,body);
        }

    }
    if(burst){
        vec3 impact=CenterRelative+(cannon?Axis*BeamLength:vec3(0));
        float radius=Radius*(1.0-exp(-PhaseTime*6.5));
        vec2 hit=sphere(ray,impact,max(radius,.025));
        if(hit.y>hit.x&&hit.x<worldDepth){
            vec3 n=normalize(ray*hit.x-impact);
            float edge=pow(max(0.0,1.0-abs(dot(n,ray))),5.5)*exp(-PhaseTime*4.2);
            light+=vec3(3.2,.43,.69)*edge;
            warp+=vec2(n.x,n.y)*edge*.023;
        }
    }
    float strength=Visibility;
    vec2 sampleUV=clamp(texCoord+warp*Distortion*strength,border,1.0-border);
    if(SceneReady<.5){fragColor=vec4(light*strength,clamp(alpha*strength,0.0,1.0))*tint;return;}
    if(UseDepth>.5&&abs(texture(SceneDepth,sampleUV).r-depth)>.0015)sampleUV=mix(texCoord,sampleUV,.04);
    vec3 background=texture(SceneColor,sampleUV).rgb;
    vec3 color=background*(1.0-clamp(alpha*strength,0.0,1.0))+light*strength;
    fragColor=vec4(color,1.0)*tint;
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

#version 150

// GLSL pow is undefined for negative bases, including pow(x, 2.0).
float vfxSquare(float x) { return x * x; }
vec2 vfxSquare(vec2 x) { return x * x; }
vec3 vfxSquare(vec3 x) { return x * x; }
vec4 vfxSquare(vec4 x) { return x * x; }
uniform sampler2D SceneDepth;
uniform mat4 InverseViewProjection;
uniform vec3 CenterRelative, Axis, Right, Up;
uniform float Time, PhaseTime, Kind, Phase, Radius, CoreRadius, Visibility, Expansion;
uniform vec2 PurpleMotion;
uniform vec2 PurpleFusion;
uniform float PurpleOutput;
in vec2 texCoord;
in vec4 tint;
out vec4 fragColor;

float hash(vec3 p){p=fract(p*.1031);p+=dot(p,p.yzx+33.33);return fract((p.x+p.y)*p.z);}
mat2 rotate(float a){float s=sin(a),c=cos(a);return mat2(c,-s,s,c);}
float blueNoise(vec3 p){
    vec3 cell=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    return mix(mix(mix(hash(cell),hash(cell+vec3(1,0,0)),f.x),
                   mix(hash(cell+vec3(0,1,0)),hash(cell+vec3(1,1,0)),f.x),f.y),
               mix(mix(hash(cell+vec3(0,0,1)),hash(cell+vec3(1,0,1)),f.x),
                   mix(hash(cell+vec3(0,1,1)),hash(cell+vec3(1,1,1)),f.x),f.y),f.z);
}
vec4 blueField(vec3 q){
    float r=length(q);
    if(r>2.45)return vec4(0);
    // Only the material is held at 24 drawings/second. The ball's position remains interpolated.
    float t=floor(Time*24.0)/24.0;
    vec3 curl=vec3(sin(q.y*2.4+t*3.7),sin(q.z*2.1-t*4.3),sin(q.x*2.7+t*2.9));
    vec3 flow=q+curl*.18;
    float broad=blueNoise(flow*2.2+vec3(t*.71,-t*1.62,t*.43));
    float detail=blueNoise(flow*5.7+vec3(-t*1.3,t*.63,t*1.1));
    float surface=r+(broad-.5)*.50+(detail-.5)*.17;
    float heart=1.0-smoothstep(.66,.85,surface);
    float mantle=(1.0-smoothstep(.91,1.47,surface))*(1.0-heart);
    // Uneven folds tear into disconnected blue/cyan sheets, without complete orbital rings.
    float fold=.5+.5*sin(q.y*5.2-q.x*3.8+q.z*2.9-r*8.3+broad*9.0-t*7.4);
    float torn=smoothstep(.39,.70,broad*.64+detail*.36);
    float shreds=smoothstep(.97,1.23,surface)*(1.0-smoothstep(1.58,2.30,surface))
            *torn*(.24+pow(fold,3.0)*1.5);
    float tip=pow(fold,7.0)*smoothstep(.45,.75,detail)
            *exp(-vfxSquare((surface-1.90)/.31));
    float blueRim=smoothstep(.92,1.91,surface);
    vec3 cyan=vec3(.018,1.72,2.65),deep=vec3(.012,.035,1.55);
    vec3 color=mix(cyan,deep,blueRim);
    color=mix(color,vec3(.14,2.30,2.95),shreds*.36+tip*.28);
    color=mix(color,vec3(4.8,5.1,5.5),smoothstep(.08,.86,heart));
    // Thin translucent exterior leaves the luminous white center visible through the moving folds.
    float density=heart*5.4+mantle*.25+shreds*.43+tip*.32+exp(-r*r*1.15)*.010;
    return vec4(color,density);
}
vec4 purpleField(vec3 q){
    float r=length(q);
    if(r>1.52)return vec4(0);
    float t=Time;
    vec3 disk=q;disk.yz=rotate(.58)*disk.yz;disk.xz=rotate(-.32)*disk.xz;disk.xy=rotate(PurpleMotion.x)*disk.xy;
    float cloud=blueNoise(disk*3.7+vec3(t*.23,-t*.41,t*.17));
    float detail=blueNoise(disk*13.2+vec3(-t*.37,t*.61,t*.29));
    float edge=r+(cloud-.5)*.12;
    float radial=length(disk.xy),angle=atan(disk.y,disk.x+.000001);
    float spiral=.5+.5*sin(angle*2.0-radial*12.0+cloud*4.6-t*.95);
    float arms=pow(spiral,5.0);
    float warpedZ=disk.z+.07*sin(disk.x*6.0+t*.8)+(cloud-.5)*.11;
    float planeDistance=warpedZ/(.12+.045*exp(-radial*radial*5.0));
    float crossDistance=(disk.z*.64+disk.y*.52-disk.x*.12+(cloud-.5)*.065)/.12;
    float plane=exp(-planeDistance*planeDistance),crossPlane=exp(-crossDistance*crossDistance);
    vec3 ellipsoid=disk*vec3(2.7,3.6,3.0);
    float concentration=exp(-dot(ellipsoid,ellipsoid));
    float torn=smoothstep(.32,.69,detail*.67+cloud*.33);
    float threads=pow(.5+.5*sin(disk.x*39.0-disk.y*27.0+detail*8.0+t*2.2),4.0);
    float knots=smoothstep(.59,.76,detail)*smoothstep(.38,.56,cloud);
    // A broken stellar cloud and thin, tilted arms supply the white center. There is no opaque white ball.
    float galaxy=plane*exp(-radial*radial*2.2)*(arms*.80+threads*.25)*(.08+torn*.92);
    galaxy+=concentration*(plane*(.40+torn*torn*1.7+threads*.70)+crossPlane*(.12+detail*.35));
    galaxy+=plane*exp(-radial*radial*2.1)*knots*1.35;
    galaxy*=1.0-smoothstep(.82,1.11,edge);
    float mantle=1.0-smoothstep(.92,1.07,edge);
    float skinDistance=(edge-.97)/.040;
    float skin=exp(-skinDistance*skinDistance)*(.18+.82*cloud);
    // Absorbing violet gas gives the galaxy a body, so scenery does not read through it like a portal.
    float nebula=mantle*(.23+arms*plane*.15+cloud*.22)+skin*.18+exp(-r*r*1.8)*.012;
    float stars=galaxy*.38;
    vec3 violet=mix(vec3(.28,.006,1.18),vec3(1.3,.055,2.6),clamp(arms*.60+cloud*.45,0.0,1.0));
    float outerGas=smoothstep(.58,1.025,edge);
    vec3 blueViolet=vec3(.184314,.133333,.741176);
    violet=mix(violet,blueViolet*(.48+.67*cloud),outerGas*.94);
    vec3 stellar=mix(vec3(2.2,.72,3.3),vec3(5.4,5.1,5.9),clamp(exp(-radial*radial*3.0)*.85+detail*.30,0.0,1.0));
    stellar+=vec3(4.0,4.0,4.2)*knots;
    // The amplified form concentrates distinct bright stellar filaments, retaining a dark
    // blue-violet shell rather than washing the whole enlarged volume into white.
    stellar*=1.0+max(PurpleOutput-1.0,0.0)*(.30+knots*.60);
    float density=nebula+stars;
    vec3 color=(violet*nebula+stellar*stars)/max(density,.0001);
    if(Phase>1.5)density*=exp(-PhaseTime*5.5);
    return vec4(color,density);
}
vec4 fusionField(vec3 local){
    float progress=PurpleFusion.y;
    float merge=smoothstep(.12,.83,progress);
    float separation=Radius*1.04*(1.0-merge);
    float orbRadius=Radius*(.29+.35*smoothstep(0.0,.76,progress));
    vec3 orbit=local;
    if(PurpleOutput>1.5)orbit.xy=rotate(-9.42477796*smoothstep(0.0,.86,progress))*orbit.xy;
    // Red on the caster's left, Blue on the right. Their shared seam becomes the galaxy,
    // while the two absorbing shells keep their colours until the silhouette closes.
    vec3 red=(orbit+vec3(separation,0,0))/orbRadius;
    vec3 blue=(orbit-vec3(separation,0,0))/orbRadius;
    float r=min(length(red),length(blue));
    vec4 lobes=vec4(0);
    if(r<1.22&&progress<.95){
        vec3 q=orbit/max(Radius,.025);
        q.xy=rotate(PurpleMotion.x)*q.xy;
        float cloud=blueNoise(q*5.7+vec3(Time*.31,-Time*.42,Time*.23));
        float detail=blueNoise(q*14.0+vec3(-Time*.61,Time*.35,Time*.47));
        float surface=r+(cloud-.5)*.12;
        float mantle=1.0-smoothstep(.93,1.08,surface);
        float radial=length(q.xy),angle=atan(q.y,q.x+.00001);
        float spiral=pow(.5+.5*sin(angle*2.0-radial*13.0+cloud*3.6-Time*1.2),3.0);
        float dust=smoothstep(.32,.76,cloud*.6+detail*.4);
        vec3 hue=mix(vec3(1.85,.006,.045),vec3(.012,.075,1.95),smoothstep(-.19,.19,orbit.x/max(orbRadius,.025)));
        float fusionOverlap=smoothstep(.25,.70,progress)*exp(-orbit.x*orbit.x/(orbRadius*orbRadius*1.8));
        hue=mix(hue,vec3(1.55,.025,2.65),fusionOverlap);
        vec3 color=hue*(.10+dust*.45+spiral*dust*.70);
        color*=1.0-smoothstep(.64,1.08,surface)*.66;
        color+=mix(vec3(1.6,.22,.65),vec3(.38,.70,2.5),step(0.0,orbit.x))
                *pow(detail,7.0)*spiral*.85;
        float density=mantle*(.65+cloud*.55)*(1.0-smoothstep(.68,.95,progress));
        lobes=vec4(color,density);
    }
    vec4 central=purpleField(local/max(CoreRadius,.025));
    central.a*=smoothstep(.34,.62,progress);
    float density=lobes.a+central.a;
    return vec4((lobes.rgb*lobes.a+central.rgb*central.a)/max(density,.0001),density);
}
vec4 field(vec3 world){
    bool blue=Kind<.5,purple=Kind>1.5,burst=!blue&&Phase>1.5;
    float growth=1.0-exp(-PhaseTime*9.0);
    float scale=purple?CoreRadius:burst?max(CoreRadius,Radius*.31*growth):CoreRadius;
    if(blue)scale*=.28+.72*Expansion;
    vec3 delta=(world-CenterRelative)/max(scale,.025);
    if(blue)return blueField(delta);
    vec3 q=vec3(dot(delta,Right),dot(delta,Up),dot(delta,Axis));
    if(purple&&PurpleFusion.x>.5)return fusionField(q*scale);
    if(purple){
        vec4 body=purpleField(q);
        if(PurpleOutput>1.5&&Phase>.5&&Phase<1.5&&q.z<0.0){
            float behind=-q.z,rho=length(q.xy),angle=atan(q.y,q.x+.00001);
            float narrow=max(.08,1.03-behind*.22);
            float envelope=exp(-pow(rho/narrow,4.0))*smoothstep(.20,.90,behind)*(1.0-smoothstep(2.7,4.1,behind));
            float stream=pow(.5+.5*sin(angle*3.0+behind*7.5-Time*22.0+sin(angle*5.0-behind*3.0)*.65),7.0);
            float folds=blueNoise(q*vec3(7.0,7.0,2.0)+vec3(0,0,Time*6.0));
            float wake=envelope*(.12+stream*.48)*(.30+folds*.70);
            vec3 hue=mix(vec3(.21,.085,.95),vec3(3.6,2.45,5.0),stream);
            body=vec4((body.rgb*body.a+hue*wake)/max(body.a+wake,.0001),body.a+wake);
        }
        return body;
    }
    float r=length(q), t=Time*3.6;
    float ripple=sin(r*23.0-Time*21.0+sin(q.y*9.0)*.5);
    float veins=pow(.5+.5*sin(atan(q.y,q.x)*9.0+q.z*8.0+t*5.0),10.0);
    float heart=1.0-smoothstep(.56,.98,r);
    float skin=exp(-vfxSquare((r-1.02)/.12))*(.3+.7*veins);
    float halo=exp(-r*r*.65)*.070;
    // The pressure air is drawn as center-outward geometry, without toroidal bands around Red.
    float density=heart*3.5+skin*.8+halo;
    float heat=clamp(heart*.93+skin*.20,0.0,1.0);
    if(Phase>.5&&Phase<1.5){
        // A continuous tapered wake follows the moving leading core in world space.
        float along=clamp(-q.z/7.0,0.0,1.0);
        vec2 bend=vec2(sin(q.z*2.8+Time*29.0),cos(q.z*3.1-Time*25.0))*.07*along;
        float width=.30*(1.0-along)+.025;
        float wake=exp(-dot(q.xy-bend,q.xy-bend)/(width*width))
                *smoothstep(-.15,.45,-q.z)*(1.0-smoothstep(5.5,7.0,-q.z));
        density+=wake*2.5;heat=max(heat,wake*.80);
    }
    if(burst){
        density*=exp(-PhaseTime*7.0)*(.72+.28*ripple);
        heat*=exp(-PhaseTime*.6);
    }
    vec3 hot=mix(vec3(1.6,.006,.065),vec3(2.2,.045,.24),clamp(skin,0.0,1.0));
    vec3 color=mix(hot,vec3(3.0,1.85,1.98),heat);
    return vec4(color,density);
}
void vfxMaterialMain(){
    fragColor=vec4(0);
    bool blue=Kind<.5,purple=Kind>1.5,flight=!blue&&Phase>.5&&Phase<1.5,burst=!blue&&Phase>1.5;
    if(burst&&PhaseTime>(purple?1.4:.9))return;
    float scale=purple?CoreRadius:burst?max(CoreRadius,Radius*.31*(1.0-exp(-PhaseTime*9.0))):CoreRadius;
    vec3 bound=CenterRelative-(flight&&!purple?Axis*CoreRadius*2.5:vec3(0));
    float boundRadius=scale*(blue?2.5:purple?1.55:flight?5.0:3.3);
    if(purple&&PurpleOutput>1.5&&flight){bound-=Axis*scale*1.25;boundRadius=scale*3.05;}
    if(purple&&PurpleFusion.x>.5){
        float separation=Radius*1.04*(1.0-smoothstep(.12,.83,PurpleFusion.y));
        float orbRadius=Radius*(.29+.35*smoothstep(0.0,.76,PurpleFusion.y));
        boundRadius=max(boundRadius,separation+orbRadius*1.23);
        scale=max(scale,Radius*.48);
    }
    vec4 farPoint=InverseViewProjection*vec4(texCoord*2.0-1.0,1,1);
    vec3 ray=normalize(farPoint.xyz*(farPoint.w<0.0?-1.0:1.0));
    float along=dot(ray,bound),disc=along*along+boundRadius*boundRadius-dot(bound,bound);
    if(disc<=0.0)return;
    float root=sqrt(disc),first=max(.025,along-root),last=along+root;
    float depth=texture(SceneDepth,texCoord).r;
    if(depth<.999999){
        vec4 p=InverseViewProjection*vec4(texCoord*2.0-1.0,depth*2.0-1.0,1);
        last=min(last,dot(p.xyz/p.w,ray));
    }
    if(last<=first)return;
    int samples=purple&&PurpleOutput>1.5?52:blue||purple?40:56;
    float stepSize=(last-first)/float(samples),offset=.35+.3*hash(vec3(gl_FragCoord.xy,0));
    vec4 sum=vec4(0);
    for(int i=0;i<56;i++){
        if(i>=samples||sum.a>.995)break;
        vec4 f=field(ray*(first+(float(i)+offset)*stepSize));
        float alpha=1.0-exp(-f.a*stepSize*3.5/max(scale,.025));
        sum.rgb+=(1.0-sum.a)*f.rgb*alpha;sum.a+=(1.0-sum.a)*alpha;
    }
    fragColor=sum*Visibility*tint;
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { fragColor = vec4(0.0); }
}

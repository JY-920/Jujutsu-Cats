#version 150

// GLSL pow is undefined for negative bases, including pow(x, 2.0).
float vfxSquare(float x) { return x * x; }
vec2 vfxSquare(vec2 x) { return x * x; }
vec3 vfxSquare(vec3 x) { return x * x; }
vec4 vfxSquare(vec4 x) { return x * x; }
uniform sampler2D RawSceneColor,RawSceneDepth;
uniform float RawSceneReady;
uniform sampler2D SceneColor;
uniform sampler2D SceneDepth;
uniform mat4 InverseViewProjection;
uniform mat4 SkyInverseViewProjection;
uniform mat4 ViewProjection;
uniform vec3 CenterRelative;
uniform vec3 Axis;
uniform float Radius;
uniform float Time;
uniform float Visibility;
uniform float Enclosed;
uniform float UseScene;
uniform float ScreenEffects;
uniform float FlashEnabled;
in vec2 texCoord;
in vec4 tint;
out vec4 fragColor;
const float TAU = 6.28318530718;
const float HORIZON_RADIUS = .48;
const vec3 STREAM_RED = vec3(209.0,45.0,87.0)/255.0;
const vec3 STREAM_VIOLET = vec3(81.0,25.0,200.0)/255.0;
const vec3 STREAM_PINK = vec3(233.0,153.0,247.0)/255.0;

float hash(vec3 p) {
    p = fract(p * .1031); p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}
float noise(vec3 p) {
    vec3 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i+vec3(1,0,0)), f.x),
                   mix(hash(i+vec3(0,1,0)), hash(i+vec3(1,1,0)), f.x), f.y),
               mix(mix(hash(i+vec3(0,0,1)), hash(i+vec3(1,0,1)), f.x),
                   mix(hash(i+vec3(0,1,1)), hash(i+vec3(1,1,1)), f.x), f.y), f.z);
}
float nebula(vec3 p) {
    float n = 0.0, weight = .5;
    for (int i = 0; i < 4; i++) { n += noise(p) * weight; p = p * 2.04 + vec3(5.7,9.2,3.8); weight *= .5; }
    return n;
}
vec3 streamColor(float seed) {
    return seed<.333333?STREAM_RED:seed<.666667?STREAM_VIOLET:STREAM_PINK;
}
vec3 stars(vec3 dir) {
    // Sample a continuous 3D field on the direction sphere. There are no cube faces or longitude seam.
    vec3 result = vec3(0);
    vec3 tangent = normalize(cross(dir,vec3(.37,.89,.24)) + vec3(.000001));
    vec3 bitangent = cross(dir,tangent);
    for (int i = 0; i < 4; i++) {
        float scale = 34.0*pow(1.87,float(i));
        vec3 p = dir*scale+vec3(float(i)*19.7,7.3,4.1), cell = floor(p);
        float h = hash(cell);
        vec3 offset = vec3(hash(cell+3.4),hash(cell+17.2),hash(cell+41.7))*.28-.14;
        vec3 d = fract(p)-.5-offset;
        // Each splat is zero before its cell boundary, including at all cell intersections.
        float support = 1.0-smoothstep(.23,.33,max(max(abs(d.x),abs(d.y)),abs(d.z)));
        float footprint = max(length(dFdx(p)),length(dFdy(p)));
        float intrinsic = .036+h*.030;
        float variance = intrinsic*intrinsic + footprint*footprint*.12;
        float core = exp(-dot(d,d)/variance)*intrinsic*intrinsic/variance;
        float glow = exp(-length(d)*21.0)*.18;
        float selected = smoothstep(.69,.94,h);
        float bright = smoothstep(.975,.999,h);
        float du=dot(d,tangent), dv=dot(d,bitangent), dn=dot(d,dir);
        float spikeWidth=max(.007,footprint*.20);
        float spikes=(exp(-abs(du)/spikeWidth-abs(dv)/.15)+exp(-abs(dv)/spikeWidth-abs(du)/.15))
                *exp(-dn*dn/.006)*bright*.38;
        vec3 tint=mix(vec3(.46,.69,1.0),vec3(1.0,.83,.62),smoothstep(.80,.98,hash(cell+25.0)));
        float twinkle=.94+.06*sin(Time*(.5+h*.3)+h*71.0);
        result += tint*((core+glow)*(1.45+bright*3.0)+spikes)*selected*support*twinkle;
    }
    return result;
}
vec3 cosmos(vec3 dir) {
    vec3 right = normalize(cross(vec3(0,1,0), Axis));
    vec3 up = cross(Axis, right);
    float facing = dot(dir, Axis);
    vec2 uv = vec2(dot(dir,right), dot(dir,up)) / max(facing, .12);
    float r = length(uv);
    float forwardMask = smoothstep(.25, .6, facing);
    // Direction-only lensing keeps the black hole, galaxy and stars at optical infinity while walking.
    float deflection=.066/(r*r+.095)+.20*exp(-vfxSquare((r-(HORIZON_RADIUS+.075))/.10));
    vec3 bent = normalize(dir+(right*uv.x+up*uv.y)*deflection*forwardMask);
    float gas = nebula(bent*5.4);
    float dust = nebula(bent*17.0+gas*.7);
    vec3 galacticNormal=normalize(right*.53+up*.74+Axis*.15);
    float latitude=dot(bent,galacticNormal);
    float wide=exp(-vfxSquare(latitude/.28));
    float ridge=exp(-vfxSquare((latitude+(gas-.5)*.09)/(.070+gas*.085)));
    float darkLane=exp(-vfxSquare((latitude-(dust-.5)*.070)/.026))*smoothstep(.31,.64,dust);
    vec3 coreDirection=normalize(Axis*.8+right*.52+up*.21);
    float bulge=exp(-vfxSquare(length(bent-coreDirection)/.28));
    vec3 color=vec3(.001,.002,.008);
    color+=mix(vec3(.009,.017,.050),vec3(.048,.018,.074),dust)*wide*gas*2.0;
    color+=vec3(.14,.25,.40)*ridge*gas*gas*2.3;
    color+=vec3(.25,.24,.28)*bulge*ridge*(.45+dust)*.65;
    color+=vec3(.022,.065,.14)*vfxSquare(smoothstep(.36,.75,gas));
    // Broken pearl-white and pink ribbons follow the same continuous spherical galaxy as its dust lanes.
    float pearl=exp(-vfxSquare((latitude+.018+(gas-.5)*.11)/(.016+dust*.014)));
    float pink=exp(-vfxSquare((latitude-.055+(dust-.5)*.10)/(.033+gas*.026)));
    float knots=smoothstep(.30,.66,dust)*(.35+gas*.85);
    float filigree=noise(bent*43.0+vec3(2.1,5.9,8.4));
    color+=vec3(.84,.86,.96)*pearl*knots*(.32+filigree*.45);
    color+=STREAM_PINK*pink*knots*(.20+filigree*.24);
    color+=vec3(.21,.075,.18)*wide*vfxSquare(knots)*.30;
    color*=1.0-darkLane*.77;
    color+=stars(bent)*(1.0+ridge*1.35)*(1.0-darkLane*.30);

    // Numerous narrow information filaments travel towards the central void.
    float angle = atan(uv.y, uv.x + .000001) / TAU + .5;
    float sector = floor(angle * 156.0);
    float lane = abs(fract(angle * 156.0) - .5);
    float seed = hash(vec3(sector, 7.0, 13.0));
    float along = fract(r*.58 + Time*(.7 + seed*.7) + seed*13.0);
    float stream = exp(-lane*lane*180.0) * smoothstep(.73,.98,along)
            * smoothstep(.40,.9,r) * (1.0-smoothstep(1.8,4.5,r)) * step(.40,seed);
    color += streamColor(hash(vec3(sector,23.0,4.0))) * stream * forwardMask * .62;

    // The broad spiral, outer light ring and enlarged circular horizon share the same directional center.
    // Every angular noise coordinate is periodic, so the vortex has no atan wrap seam.
    float aa=max(fwidth(r),.0011);
    if(forwardMask>.001&&r<2.6){
        float theta=atan(uv.y,uv.x+.000001);
        float winding=theta+2.35*log(max(r,.16))-Time*.12;
        vec2 orbit=vec2(cos(winding),sin(winding));
        float flow=nebula(vec3(orbit*(2.6+r*1.4),r*7.0-Time*.10));
        float fine=noise(vec3(orbit*13.0,r*49.0+flow*4.0-Time*.25));
        float wisps=pow(smoothstep(.25,.74,flow*.78+fine*.22),.78);
        float threadPhase=r*94.0-theta*6.0+flow*12.0-Time*.48;
        float threadFootprint=max(length(dFdx(uv)),length(dFdy(uv)))*(94.0+6.0/max(r,.16))
                +fwidth(flow)*12.0;
        float threads=pow(.5+.5*sin(threadPhase),6.0)
                *(1.0-smoothstep(.9,3.0,threadFootprint));
        float annulus=smoothstep(HORIZON_RADIUS,HORIZON_RADIUS+.11,r)*(1.0-smoothstep(.88,1.30,r));
        float arms=.56+.44*vfxSquare(.5+.5*sin(theta*3.0+r*11.0+flow*3.0));
        float heat=smoothstep(.43,.69,flow+fine*.16);
        vec3 cloud=mix(vec3(.18,.24,.40),vec3(.88,.94,1.06),heat);
        vec3 vapor=cloud*annulus*arms*(.085+wisps*1.05+threads*wisps*.27);
        // A feathered white-blue plume peels away from the spiral towards the right.
        float plumeY=uv.y-(.22+.15*sin(uv.x*2.2)+uv.x*.065);
        float plumeWidth=.12+max(uv.x,0.0)*.085;
        float plume=smoothstep(.40,.86,uv.x)*(1.0-smoothstep(1.6,2.45,uv.x))
                *exp(-vfxSquare(plumeY/plumeWidth));
        float plumeNoise=noise(vec3(uv.x*4.0,plumeY*18.0+flow*2.0,Time*.11));
        vapor+=mix(vec3(.10,.16,.29),vec3(.61,.70,.83),plumeNoise)
                *plume*(.20+vfxSquare(plumeNoise)*1.25);
        color*=1.0-annulus*wisps*forwardMask*.38;
        color+=vapor*forwardMask;

        vec2 ringUV=uv*vec2(.99,1.025);
        float ringRadius=length(ringUV);
        float ringAA=max(fwidth(ringRadius),.0011);
        float distanceToRing=abs(ringRadius-.79);
        float lightRing=exp(-distanceToRing/max(.0030,ringAA));
        float halo=exp(-distanceToRing/.026),corona=exp(-distanceToRing/.102);
        float upper=smoothstep(-.25,.64,ringUV.y);
        vec3 pearlGold=mix(vec3(.78,.87,1.0),vec3(1.0,.88,.61),upper);
        float detail=.82+.18*noise(vec3(ringUV*25.0,Time*.09));
        vec3 rim=mix(vec3(1.0),pearlGold,.38)*lightRing*1.85;
        rim+=pearlGold*(halo*.47+corona*.11);
        // Restrained gold/pink inner fringe and cyan outer fringe preserve the nearly white rim.
        rim+=vec3(.50,.24,.17)*exp(-abs(ringRadius-.779)/.012)*.23;
        rim+=vec3(.18,.42,.43)*exp(-abs(ringRadius-.811)/.014)*.32;
        rim+=vec3(.16,.24,.40)*exp(-abs(ringRadius-.852)/.037)*.23;
        color+=rim*forwardMask*detail;
        float innerMist=exp(-max(r-(HORIZON_RADIUS+.012),0.0)/.051)
                *smoothstep(HORIZON_RADIUS-.003,HORIZON_RADIUS+.032,r);
        color+=vec3(.25,.32,.43)*innerMist*forwardMask*(.35+wisps);
    }
    // Apply the horizon last: galaxy, clouds and the light ring cannot paint over its black center.
    float shadow=(1.0-smoothstep(HORIZON_RADIUS-aa,HORIZON_RADIUS+aa,r))*forwardMask;
    color=mix(color,vec3(.00006,.00008,.00015),shadow);
    return color;
}
vec3 reconstruct(vec2 uv, float depth) {
    vec4 p = InverseViewProjection * vec4(uv*2.0-1.0,depth*2.0-1.0,1.0);
    return p.xyz / (abs(p.w) < 1e-7 ? (p.w < 0.0 ? -1e-7 : 1e-7) : p.w);
}
float whiteInvasion(vec3 direction) {
    float progress=smoothstep(.025,.90,Time);
    vec3 right=normalize(cross(vec3(0,1,0),Axis)),up=cross(Axis,right);
    vec3 origin=normalize(Axis*.18-right*.88-up*.34);
    // The advancing edge crawls across the direction sphere rather than fading the entire frame to white.
    float tendrils=(noise(direction*12.0+vec3(4.2,1.7,8.9))-.5)*.25
            +(noise(direction*31.0)-.5)*.115+(noise(direction*73.0)-.5)*.042;
    float field=dot(direction,origin)+tendrils-mix(1.31,-1.31,progress);
    return smoothstep(-.045,.035,field);
}
vec3 openingVeil(vec3 direction,vec3 under) {
    if(FlashEnabled<.5)return under;
    float progress=smoothstep(.94,3.10,Time);
    if(progress<=0.0)return vec3(.985,.991,1.0);
    if(progress>=1.0)return under;
    // An irregular front breaks the white shell open in the cast direction, then sweeps behind the viewer.
    float corrugation=(noise(direction*19.0)-.5)*.26+(noise(direction*47.0)-.5)*.075;
    float field=dot(direction,Axis)+corrugation-mix(1.25,-1.25,progress);
    float reveal=smoothstep(-.018,.028,field);
    vec3 cell=fract(direction*16.0+vec3(3.7,8.2,1.9));
    float cellEdge=min(min(min(cell.x,1.0-cell.x),min(cell.y,1.0-cell.y)),min(cell.z,1.0-cell.z));
    float cracks=(1.0-smoothstep(.0,.035,cellEdge))*exp(-abs(field)*6.0)*progress;
    vec3 sheet=vec3(.985,.991,1.0)-vec3(.08,.045,.016)*cracks;
    vec3 color=mix(sheet,under,reveal);
    float rim=exp(-abs(field)/.022)*(1.0-progress);
    return color+mix(STREAM_VIOLET,STREAM_PINK,.72)*rim*.55;
}
uniform float InteriorView,BreakProgress,BreakStyle,OuterRadius;
uniform vec3 OuterCenter;
vec3 strikeNormal(float id){
    return normalize(vec3(hash(vec3(id,33,7))-.5,abs(hash(vec3(id,9,21))-.5)+.07,hash(vec3(id,15,11))-.5));
}
float strikeBorn(float id){return .035+hash(vec3(id,17,1))*.48;}
// Blades travel through the exterior annulus before touching the barrier. No interior hit planes.
vec4 externalAssault(vec3 ray,float limit,out float at){
    vec3 light=vec3(0);float alpha=0.0;at=1e7;
    float gap=OuterRadius-length(OuterCenter-CenterRelative)-Radius;
    if(BreakStyle>.5||BreakProgress<=0.0||gap<.1)return vec4(0);
    for(int i=0;i<22;i++){
        float id=float(i),age=BreakProgress-strikeBorn(id),flight=age/.085;
        if(flight<0.0||flight>1.0)continue;
        vec3 n=strikeNormal(id),side=normalize(cross(n,vec3(.13,1,.07)));
        vec3 center=CenterRelative+n*(Radius+.12+gap*.88*(1.0-flight));
        center+=side*(1.0-flight)*gap*.28;
        float reach=Radius*mix(.09,.19,hash(vec3(id,8,2)))*smoothstep(0.0,.15,flight);
        vec3 a=center-side*reach,b=center+side*reach,v=b-a;
        float rv=dot(ray,v),va=dot(v,a),ra=dot(ray,a);
        float den=max(dot(v,v)-rv*rv,.00001);
        float s=clamp((rv*ra-va)/den,0.0,1.0);
        vec3 nearest=a+v*s;float t=dot(nearest,ray);
        if(t<=0.0||t>limit)continue;
        float distance=length(ray*t-nearest),width=max(.07,Radius*.003);
        float core=1.0-smoothstep(width*.35,width,distance);
        float glow=exp(-distance/(width*2.5))*.55;
        float fade=smoothstep(0.0,.12,flight)*(1.0-smoothstep(.86,1.0,flight));
        float opacity=(core+glow)*fade;
        light+=(vec3(2.0,1.85,1.95)*core+vec3(1.5,.06,.13)*glow)*fade;
        alpha=max(alpha,opacity);if(opacity>.005)at=min(at,t);
    }
    return vec4(light,clamp(alpha,0.0,1.0));
}
float shellCuts(vec3 normal){
    if(BreakProgress<=0.0)return 0.0;
    float strokes=0.0;
    for(int i=0;i<22;i++){
        if(BreakStyle>.5&&i>2)break;
        float id=float(i);
        vec3 plane=normalize(vec3(hash(vec3(id,3,7))-.5,hash(vec3(id,9,2))-.5,hash(vec3(id,5,11))-.5));
        if(BreakStyle>.5)plane=normalize(vec3(.72,.63,.24)+id*.025);
        float field=dot(normal,plane),d=abs(field),aa=max(fwidth(field)*1.2,.0012);
        vec3 contact=strikeNormal(id),side=normalize(cross(contact,vec3(.13,1,.07)));
        if(BreakStyle<.5){plane=normalize(cross(contact,side));field=dot(normal,plane);d=abs(field);aa=max(fwidth(field)*1.2,.0012);}
        float born=BreakStyle>.5?.15:strikeBorn(id)+.072;
        float age=BreakProgress-born;
        vec3 tangent=normalize(cross(plane,vec3(.13,1,.07)));
        float extent=mix(-1.2,1.2,clamp(age*18.0,0.0,1.0));
        float visible=step(0.0,age)*step(dot(normal,tangent),extent);
        if(BreakStyle<.5){
            float span=mix(.20,.46,hash(vec3(id,8,2)));
            visible=step(0.0,age)*step(dot(normal,side),mix(-span,span,clamp(age*22.0,0.0,1.0)))
                    *step(-span,dot(normal,side))*smoothstep(.68,.88,dot(normal,contact));
        }
        strokes=max(strokes,(1.0-smoothstep(aa,aa*3.0,d))*visible);
        strokes=max(strokes,exp(-d/max(aa*6.0,.009))*visible*.3);
    }
    return strokes;
}
bool shellMissing(vec3 normal){
    float gone=smoothstep(.54,1.0,BreakProgress);
    return gone>0.0&&hash(floor(normal*18.0))<gone;
}
void vfxMaterialMain() {
    vec4 skyPoint=SkyInverseViewProjection*vec4(texCoord*2.0-1.0,1.0,1.0);
    vec3 skyRay=normalize(skyPoint.xyz*(skyPoint.w<0.0?-1.0:1.0));
    vec4 domainFar=InverseViewProjection*vec4(texCoord*2.0-1.0,1.0,1.0);
    vec3 domainRay=normalize(domainFar.xyz*(domainFar.w<0.0?-1.0:1.0));
    float domainAlong=dot(domainRay,CenterRelative);
    float domainDisc=domainAlong*domainAlong+Radius*Radius-dot(CenterRelative,CenterRelative);
    bool shellHit=domainDisc>0.0&&domainAlong+sqrt(max(domainDisc,0.0))>.001;
    if(InteriorView<.5){
        float entry=shellHit?max(.001,domainAlong-sqrt(max(domainDisc,0.0))):1e7;
        float limit=entry;
        if(UseScene>.5){float z=texture(SceneDepth,texCoord).r;if(z<.999999)limit=min(limit,length(reconstruct(texCoord,z)));}
        float strikeAt;vec4 assault=externalAssault(domainRay,limit,strikeAt);
        vec3 shellNormal=shellHit?normalize(domainRay*entry-CenterRelative):vec3(0,1,0);
        if(!shellHit||shellMissing(shellNormal)){
            if(assault.a<.005)discard;
            vec4 clip=ViewProjection*vec4(domainRay*strikeAt,1.0);
            gl_FragDepth=clamp(clip.z/clip.w*.5+.5,0.0,1.0);
            fragColor=vec4(assault.rgb/max(assault.a,.005),assault.a*Visibility)*tint;return;
        }
        if(UseScene>.5){
            float sceneDepth=texture(SceneDepth,texCoord).r;
            if(sceneDepth<.999999&&length(reconstruct(texCoord,sceneDepth))<entry-.025)discard;
        }
        vec3 normal=normalize(domainRay*entry-CenterRelative);
        if(shellMissing(normal))discard;
        float rim=pow(max(0.0,1.0-abs(dot(normal,domainRay))),3.5);
        float grain=noise(normal*18.0+Time*.025);
        vec3 shell=vec3(.008,.006,.018)+grain*vec3(.006,.004,.012);
        shell+=rim*vec3(.20,.18,.36);
        shell+=shellCuts(normal)*vec3(1.65,1.48,1.92);
        shell=shell*(1.0-assault.a)+assault.rgb;
        vec4 clip=ViewProjection*vec4(domainRay*entry,1.0);
        gl_FragDepth=clamp(clip.z/clip.w*.5+.5,0.0,1.0);
        fragColor=vec4(shell,Visibility)*tint;return;
    }
    if(!shellHit)discard;
    if(Time<.94&&FlashEnabled>.5){
        float coverage=whiteInvasion(skyRay);
        vec3 white=vec3(.985,.991,1.0);
        // The uncovered image may already be the other domain. Always retain that captured layer.
        vec3 color=UseScene>.5?mix(texture(SceneColor,texCoord).rgb,white,coverage):white;
        float alpha=Visibility*(UseScene>.5?1.0:coverage);
        gl_FragDepth=0.0;
        fragColor=vec4(color,alpha)*tint;
        return;
    }
    vec4 farPoint=InverseViewProjection*vec4(texCoord*2.0-1.0,1.0,1.0);
    vec3 ray=normalize(farPoint.xyz*(farPoint.w<0.0?-1.0:1.0));
    float along=dot(ray,CenterRelative);
    float disc=along*along+Radius*Radius-dot(CenterRelative,CenterRelative);
    float root=sqrt(max(disc,0.0)),nearT=along-root,farT=along+root;
    bool sphereHit=disc>0.0&&farT>.001;
    // Shared interior views use the far wall and retain an infinite directional background.
    float surfaceT=sphereHit?farT:1e6;
    bool floorHit=false;
    if(sphereHit&&ray.y<-.0001){
        float floorT=(CenterRelative.y+.045)/ray.y;
        vec3 floorPoint=ray*floorT-CenterRelative;
        if(floorT>max(nearT,0.0)&&floorT<surfaceT&&length(floorPoint)<Radius){
            surfaceT=floorT;floorHit=true;
        }
    }
    gl_FragDepth=1.0;
    if((sphereHit&&Enclosed>.5)||floorHit){
        vec4 clip=ViewProjection*vec4(ray*surfaceT,1.0);
        gl_FragDepth=clamp(clip.z/clip.w*.5+.5,0.0,1.0);
    }
    float depth=1.0;
    vec3 scene=vec3(0),position=ray*1e6;
    if(UseScene>.5){
        depth=texture(SceneDepth,texCoord).r;scene=texture(SceneColor,texCoord).rgb;
        if(depth<.999999)position=reconstruct(texCoord,depth);
    }
    if(RawSceneReady>.5&&UseScene>.5){
        float rawDepth=texture(RawSceneDepth,texCoord).r;
        vec3 rawPosition=rawDepth<.999999?reconstruct(texCoord,rawDepth):ray*1e6;
        if(rawDepth<.999999&&distance(rawPosition,CenterRelative)<Radius){
            if(abs(rawDepth-depth)>.00002)scene=texture(RawSceneColor,texCoord).rgb;
            depth=rawDepth;position=rawPosition;
        }else if(depth<.999999&&distance(position,CenterRelative)<Radius){
            // Fluid and block-entity geometry arriving after the opaque pass is hidden as well.
            depth=1.0;position=ray*1e6;
        }
    }
    // The directional atmosphere is only reached from this field or its active clash partner.
    vec3 color=cosmos(skyRay);
    bool foreground=depth<.999999&&(!sphereHit||length(position)<surfaceT-.035||(Enclosed<.5&&!floorHit));
    if(UseScene>.5&&foreground){
        vec3 local=position-CenterRelative;
        if(length(local)<Radius){
            float fog=1.0-exp(-length(position)/max(Radius*.65,5.0));
            float luma=dot(scene,vec3(.2126,.7152,.0722));
            vec3 cool=mix(scene,vec3(.16,.38,.68)*luma,.6);
            float pulse=exp(-vfxSquare((Time-1.79)/.54))*ScreenEffects;
            vec2 shift=vec2(sin(texCoord.y*11.0+Time*3.0),cos(texCoord.x*10.0-Time*2.0))*.006*pulse;
            vec2 warpedUV=clamp(texCoord+shift,vec2(.001),vec2(.999));
            if(abs(texture(SceneDepth,warpedUV).r-depth)<.00035)
                cool=mix(cool,texture(SceneColor,warpedUV).rgb*.75,pulse*.4);
            color=mix(cool,vec3(.003,.012,.042)+color*.035,fog*.68);
        }else color=scene;
    }
    color=openingVeil(skyRay,color);
    if(BreakProgress>0.0){
        vec3 wall=normalize(ray*farT-CenterRelative);
        if(shellMissing(wall))discard;
        color+=shellCuts(wall)*vec3(1.4,1.3,1.7);
    }
    if(FlashEnabled<.5)color=mix(UseScene>.5?scene:vec3(0),color,smoothstep(0.0,1.4,Time));
    fragColor=vec4(max(color,vec3(0)),Visibility)*tint;
}


// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

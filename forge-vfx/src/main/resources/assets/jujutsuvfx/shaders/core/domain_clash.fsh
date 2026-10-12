#version 150
uniform sampler2D ShrineColor, ShrineDepth, VoidColor, VoidDepth;
uniform mat4 InverseViewProjection;
uniform vec3 ShrineCenter,VoidCenter,PlanePoint,PlaneNormal;
uniform vec2 Resolution;
uniform float ShrineRadius,VoidRadius,BlendWidth,Time,ClashStrength,ScreenEffects;
uniform float NestedExterior;
in vec2 texCoord;
in vec4 tint;
out vec4 fragColor;

float hash(float n){return fract(sin(n*127.1+17.17)*43758.5453);}
vec2 sphere(vec3 ray,vec3 center,float radius){
    float along=dot(ray,center),disc=along*along+radius*radius-dot(center,center);
    if(disc<0.0)return vec2(1e8,-1e8);
    float root=sqrt(disc);return vec2(along-root,along+root);
}
vec3 position(float depth){
    vec4 p=InverseViewProjection*vec4(texCoord*2.0-1.0,depth*2.0-1.0,1.0);
    return p.xyz/(abs(p.w)<1e-7?(p.w<0.0?-1e-7:1e-7):p.w);
}
void vfxMaterialMain(){
    if(NestedExterior>.5){
        fragColor=texture(VoidColor,texCoord)*tint;
        gl_FragDepth=texture(VoidDepth,texCoord).r;return;
    }
    vec4 far=InverseViewProjection*vec4(texCoord*2.0-1.0,1.0,1.0);
    vec3 ray=normalize(far.xyz*(far.w<0.0?-1.0:1.0));
    float depthA=texture(ShrineDepth,texCoord).r,depthB=texture(VoidDepth,texCoord).r;
    float depth=min(depthA,depthB),distance=depth<.999999?length(position(depth)):1e7;
    vec2 a=sphere(ray,ShrineCenter,ShrineRadius),b=sphere(ray,VoidCenter,VoidRadius);
    bool inA=a.y>0.0&&distance>=max(a.x,0.0)-.025;
    bool inB=b.y>0.0&&distance>=max(b.x,0.0)-.025;
    float start=max(max(a.x,b.x),0.0),end=min(a.y,b.y);
    bool overlap=inA&&inB&&end>start&&distance>=start;
    bool viewerInside=length(ShrineCenter)<ShrineRadius||length(VoidCenter)<VoidRadius;
    // Outside the union, choose the nearest opaque shell/geometry instead of extending either sky.
    if(!viewerInside){
        bool voidFront=depthB<=depthA+.000001;
        fragColor=vec4(voidFront?texture(VoidColor,texCoord).rgb:texture(ShrineColor,texCoord).rgb,1.0)*tint;
        gl_FragDepth=voidFront?depthB:depthA;return;
    }
    // A shared interior preserves both skies across the seam, limited to the actual field volumes.
    float atmosphereDistance=max(24.0,max(length(ShrineCenter)+ShrineRadius,length(VoidCenter)+VoidRadius));
    // Sampling the overlap midpoint pinned almost every sky ray to the seam when viewing from one side.
    // Probe the far atmosphere instead, so looking towards the other domain actually reveals its sky.
    float probe=min(distance,atmosphereDistance);
    vec3 p=ray*probe;
    float signedDistance=dot(p-PlanePoint,PlaneNormal);
    vec3 local=p-ShrineCenter;
    float tendrils=sin(local.y*1.9+sin(local.z*.8-Time*1.2))
            *sin(local.x*.65-local.z*.4+Time*1.6)*BlendWidth*.28*ClashStrength;
    float field=signedDistance+tendrils;
    vec2 gradient=vec2(dFdx(field),dFdy(field));
    float weight=smoothstep(-BlendWidth,BlendWidth,field);
    if(!overlap){
        if(inB&&!inA)weight=1.0;
        else if(inA&&!inB)weight=0.0;
        else if(inA&&inB)weight=max(b.x,0.0)<max(a.x,0.0)?1.0:0.0;
    }

    float resolutionEdge=0.0,cuts=0.0;
    if(abs(Resolution.x)>.5){
        float progress=Resolution.y;
        float ripple=sin(local.x*.37+sin(local.y*.28)+progress*4.0)*.055;
        float front=progress*2.6-1.3+Resolution.x*field/max(max(ShrineRadius,VoidRadius),1.0)*.65+ripple;
        float swallowed=smoothstep(-.16,.16,front);
        swallowed=max(swallowed,smoothstep(.78,1.0,progress));
        resolutionEdge=exp(-abs(front)*28.0)*sin(progress*3.14159265);
        if(Resolution.x<0.0){
            vec2 pixel=texCoord*vec2(textureSize(ShrineColor,0));
            float scale=max(float(textureSize(ShrineColor,0).x),float(textureSize(ShrineColor,0).y));
            vec2 screen=(pixel-vec2(textureSize(ShrineColor,0))*.5)/scale;
            for(int i=0;i<24;i++){
                float n=float(i),angle=hash(n+3.0)*6.2831853;
                vec2 along=vec2(cos(angle),sin(angle)),normal=vec2(-along.y,along.x);
                float offset=(hash(n+23.0)-.5)*1.05;
                float d=abs(dot(screen,normal)-offset);
                float born=hash(n+77.0)*.58,age=progress-born;
                float extent=mix(-1.0,1.0,clamp(age*25.0,0.0,1.0));
                float life=step(0.0,age)*(1.0-smoothstep(.11,.23,age));
                float stroke=(1.0-smoothstep(.0006,.0026,d))*step(dot(screen,along),extent)*life;
                cuts=max(cuts,stroke);
                // Unequal pieces yield to the red field along the newly cut fissures.
                float fragments=step(hash(floor(dot(screen,along)*9.0)+n*19.0),progress);
                swallowed=max(swallowed,exp(-d*16.0)*fragments*smoothstep(.01,.18,age));
            }
            weight=mix(weight,0.0,swallowed);
        }else weight=mix(weight,1.0,swallowed);
    }

    vec2 uvA=texCoord,uvB=texCoord;
    float seam=overlap?exp(-abs(field)/max(BlendWidth*.58,.1))*ClashStrength:0.0;
    vec2 size=vec2(textureSize(ShrineColor,0)),margin=.5/size;
    vec2 normal=normalize(gradient+vec2(.000001));
    vec2 shift=normal*sin(field*6.0-Time*10.0)*seam*ScreenEffects*5.5/size;
    uvA=clamp(texCoord+shift,margin,1.0-margin);uvB=clamp(texCoord-shift,margin,1.0-margin);
    if(abs(texture(ShrineDepth,uvA).r-depthA)>.001)uvA=texCoord;
    if(abs(texture(VoidDepth,uvB).r-depthB)>.001)uvB=texCoord;
    vec3 color=mix(texture(ShrineColor,uvA).rgb,texture(VoidColor,uvB).rgb,weight);
    float veins=pow(.5+.5*sin(local.y*8.0+sin(local.z*2.8+Time*3.0)*2.0),10.0);
    vec3 edgeColor=mix(vec3(.85,.095,.065),vec3(.07,.48,.95),weight);
    color+=edgeColor*seam*.14+vec3(.42,.53,.63)*seam*veins*.14;
    if(abs(Resolution.x)>.5){
        color+=resolutionEdge*(Resolution.x>0.0?vec3(.24,.40,.75):vec3(.65,.012,.03))*.42;
        color=mix(color,vec3(1.6,.58,.65),cuts*.90);
    }
    // Preserve the selected side's geometry depth for the contact particles drawn next.
    gl_FragDepth=weight<.5?depthA:depthB;
    fragColor=vec4(color,1.0)*tint;
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

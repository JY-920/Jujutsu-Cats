#version 150
uniform mat4 InverseViewProjection;
uniform float Time;
uniform float Strength;
uniform mat4 ViewProjection;
uniform vec3 CenterRelative;
uniform float BoundaryRadius;
uniform float Enclosed;
uniform float SoftHorizon;
uniform float InteriorView;
in vec2 texCoord;
in vec4 tint;
out vec4 fragColor;
float hash(vec3 p) {
    p=fract(p*0.1031); p+=dot(p,p.yzx+33.33); return fract((p.x+p.y)*p.z);
}
float noise(vec3 p) {
    vec3 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);
    return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),
                   mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),
               mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),
                   mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);
}
float clouds(vec3 p) {
    float sum=0.0, amplitude=0.5;
    for(int i=0;i<5;i++) { sum+=noise(p)*amplitude; p=p*2.03+vec3(11.7,5.3,8.1); amplitude*=0.5; }
    return sum;
}
void vfxMaterialMain() {
    vec4 p=InverseViewProjection*vec4(texCoord*2.0-1.0,1.0,1.0);
    vec3 ray=normalize(p.xyz*(p.w<0.0?-1.0:1.0));
    gl_FragDepth=1.0;
    float horizonBlend=0.0;
    float alongRay=dot(ray,CenterRelative);
    float rayDisc=alongRay*alongRay+BoundaryRadius*BoundaryRadius-dot(CenterRelative,CenterRelative);
    if(Enclosed>.5&&(rayDisc<=0.0||alongRay+sqrt(max(rayDisc,0.0))<=.001))discard;
    if(Enclosed>0.5){
        // The outer observer sees a bounded surface; only a shared interior uses the far wall.
        float along=dot(ray,CenterRelative);
        float discriminant=along*along+BoundaryRadius*BoundaryRadius-dot(CenterRelative,CenterRelative);
        float exitDistance=InteriorView>.5?along+sqrt(max(discriminant,0.0)):max(.001,along-sqrt(max(discriminant,0.0)));
        if(discriminant>0.0 && exitDistance>.001){
            horizonBlend=(1.0-smoothstep(0.0,BoundaryRadius*.24,
                    abs(ray.y*exitDistance-CenterRelative.y-.065)))*SoftHorizon;
            vec4 boundaryClip=ViewProjection*vec4(ray*exitDistance,1.0);
            gl_FragDepth=clamp(boundaryClip.z/boundaryClip.w*.5+.5,0.0,1.0);
        }
    }
    float zenith=smoothstep(-0.12,0.9,ray.y);
    vec3 drift=vec3(Time*0.009,Time*0.003,-Time*0.006);
    float strata=clouds(ray*4.0+drift);
    float deep=clouds(ray*9.0-drift*0.5);
    // Lift the crimson midtones, while preserving dark gaps between luminous cloud banks.
    float banks=smoothstep(.26,.72,strata);
    vec3 sky=mix(vec3(.30,.008,.035),vec3(.105,.003,.022),zenith);
    sky*=.82+.42*deep;
    sky+=vec3(.47,.013,.050)*pow(banks,1.10);
    sky+=vec3(.17,.033,.042)*smoothstep(.55,.77,strata)*(.45+.55*deep);
    sky+=vec3(.09,.004,.010)*exp(-abs(ray.y)*4.0);
    // Keep this waterline color identical to the mirror's outer mist.
    sky=mix(sky,vec3(.23,.008,.035),horizonBlend);
    fragColor=vec4(sky,Strength)*tint;
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

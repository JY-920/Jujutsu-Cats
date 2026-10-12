#version 150
uniform sampler2D SceneDepth;
uniform mat4 InverseViewProjection, ViewProjection;
uniform vec2 SceneSize;
uniform float LightReachScale;
uniform vec4 LightPosition0, LightEnd0, LightColor0;
uniform vec4 LightPosition1, LightEnd1, LightColor1;
uniform vec4 LightPosition2, LightEnd2, LightColor2;
uniform vec4 LightPosition3, LightEnd3, LightColor3;
uniform vec4 LightPosition4, LightEnd4, LightColor4;
uniform vec4 LightPosition5, LightEnd5, LightColor5;
uniform vec4 LightPosition6, LightEnd6, LightColor6;
uniform vec4 LightPosition7, LightEnd7, LightColor7;
uniform vec4 LightPosition8, LightEnd8, LightColor8;
uniform vec4 LightPosition9, LightEnd9, LightColor9;
uniform vec4 LightPosition10, LightEnd10, LightColor10;
uniform vec4 LightPosition11, LightEnd11, LightColor11;
in vec2 texCoord;
in vec4 tint;
out vec4 fragColor;

vec3 reconstruct(vec2 uv,float depth){
    vec4 p=InverseViewProjection*vec4(uv*2.0-1.0,depth*2.0-1.0,1.0);
    return p.xyz/p.w;
}
vec3 neighbor(vec2 uv){
    uv=clamp(uv,.5/SceneSize,1.0-.5/SceneSize);
    return reconstruct(uv,texture(SceneDepth,uv).r);
}
vec3 surfaceNormal(vec3 p){
    vec2 stepUV=1.0/SceneSize;
    vec3 left=p-neighbor(texCoord-vec2(stepUV.x,0)),right=neighbor(texCoord+vec2(stepUV.x,0))-p;
    vec3 down=p-neighbor(texCoord-vec2(0,stepUV.y)),up=neighbor(texCoord+vec2(0,stepUV.y))-p;
    // Prefer the shorter one-sided derivative at a silhouette; never use a sky
    // sample to stretch an entity's normal across the background.
    vec3 dx=dot(left,left)<dot(right,right)?left:right;
    vec3 dy=dot(down,down)<dot(up,up)?down:up;
    vec3 n=cross(dx,dy);
    if(dot(n,n)<.00000001)return -normalize(p);
    n=normalize(n);return dot(n,p)>0.0?-n:n;
}
float visibilityToLight(vec3 p,vec3 normal,vec3 light){
    float visibility=1.0,path=length(light-p),bias=.075+length(p)*.002;
    if(path<.2)return visibility;
    // Four bounded screen-depth probes provide contact occlusion for visible
    // blockers only. Offscreen/hidden geometry is not available to this pass.
    for(int i=0;i<4;i++){
        float t=.16+float(i)*.21;
        vec3 samplePoint=mix(p+normal*.06,light,t);
        vec4 clip=ViewProjection*vec4(samplePoint,1.0);
        if(clip.w<=.01)continue;
        vec2 uv=clip.xy/clip.w*.5+.5;
        if(any(lessThan(uv,vec2(.001)))||any(greaterThan(uv,vec2(.999))))continue;
        float depth=texture(SceneDepth,uv).r;
        if(depth>=.999999)continue;
        float gap=length(samplePoint)-length(reconstruct(uv,depth));
        float thickness=.45+path*.075;
        float blocked=smoothstep(bias,bias+.12,gap)*(1.0-smoothstep(thickness,thickness*1.8,gap));
        visibility*=1.0-blocked*.78;
    }
    return .12+.88*visibility;
}
void vfxMaterialMain(){
    float depth=texture(SceneDepth,texCoord).r;
    if(depth>=.999999){fragColor=vec4(0,0,0,-1);return;}
    vec3 p=reconstruct(texCoord,depth),normal=surfaceNormal(p),irradiance=vec3(0);
    vec4 positions[12]=vec4[12](LightPosition0,LightPosition1,LightPosition2,LightPosition3,LightPosition4,LightPosition5,LightPosition6,LightPosition7,LightPosition8,LightPosition9,LightPosition10,LightPosition11);
    vec4 ends[12]=vec4[12](LightEnd0,LightEnd1,LightEnd2,LightEnd3,LightEnd4,LightEnd5,LightEnd6,LightEnd7,LightEnd8,LightEnd9,LightEnd10,LightEnd11);
    vec4 colors[12]=vec4[12](LightColor0,LightColor1,LightColor2,LightColor3,LightColor4,LightColor5,LightColor6,LightColor7,LightColor8,LightColor9,LightColor10,LightColor11);
    for(int i=0;i<12;i++){
        vec4 origin=positions[i],end=ends[i],color=colors[i];
        if(color.a<.002||origin.w<.1)continue;
        if(end.w>1.5){
            float distanceToCenter=length(p-origin.xyz)/origin.w;
            if(distanceToCenter>=1.0)continue;
            float falloff=1.0-smoothstep(.70,1.0,distanceToCenter);
            float facing=.40+.60*max(dot(normal,end.xyz),0.0);
            irradiance+=color.rgb*color.a*falloff*facing;
            continue;
        }
        vec3 source=origin.xyz;
        if(end.w>.5){
            vec3 segment=end.xyz-origin.xyz;
            float t=clamp(dot(p-origin.xyz,segment)/max(dot(segment,segment),.0001),0.0,1.0);
            source+=segment*t;
        }
        vec3 toLight=source-p;
        float d2=dot(toLight,toLight),range2=origin.w*origin.w;
        if(d2>=range2)continue;
        vec3 direction=d2>.0001?toLight*inversesqrt(d2):normal;
        // Scale attenuation along with the cutoff, so higher reach illuminates distant surfaces visibly.
        float edge=1.0-d2/range2,falloff=edge*edge/(1.0+.10*d2/max(1.0,LightReachScale*LightReachScale));
        float facing=.12+.88*max(dot(normal,direction),0.0);
        float contribution=color.a*falloff*facing;
        if(contribution<.002)continue;
        float occlusion=visibilityToLight(p,normal,source);
        irradiance+=color.rgb*contribution*occlusion;
    }
    fragColor=vec4(min(irradiance,vec3(8)),length(p))*tint;
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { fragColor = vec4(0.0); }
}

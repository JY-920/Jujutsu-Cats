#version 150
uniform sampler2D Reflection;
uniform sampler2D ReflectionDepth;
uniform sampler2D SceneColor;
uniform sampler2D SceneDepth;
uniform mat4 RiftView;
uniform mat4 RiftProjection;
uniform mat4 InverseViewProjection;
uniform vec3 Offset;
uniform float UseScene;
uniform float Time;
uniform float Radius;
uniform float Expansion;
uniform float Visibility;
in vec3 waterPosition;
in vec3 cameraRelative;
in vec4 projected;
out vec4 fragColor;

// The same layered value-noise family as the crimson sky, recolored for the mirror below it.
float hash(vec3 p){
    p=fract(p*.1031);p+=dot(p,p.yzx+33.33);return fract((p.x+p.y)*p.z);
}
float noise(vec3 p){
    vec3 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),
                   mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),
               mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),
                   mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);
}
float clouds(vec3 p){
    float sum=0.0,amplitude=.5;
    for(int i=0;i<5;i++){
        sum+=noise(p)*amplitude;p=p*2.03+vec3(11.7,5.3,8.1);amplitude*=.5;
    }
    return sum;
}
vec3 deepMirrorEnvironment(vec3 point,vec3 direction){
    // Reflect a virtual cloud vault anchored to the domain. Sampling the reflected ray
    // gives the cloud layers parallax instead of painting an ordinary repeating floor texture.
    float shellRadius=max(Radius*1.35,6.0);
    float along=dot(point,direction);
    float travel=-along+sqrt(max(along*along+shellRadius*shellRadius-dot(point,point),0.0));
    vec3 q=(point+direction*travel)/shellRadius;
    vec3 drift=vec3(Time*.014,Time*.005,-Time*.010);
    float warp=noise(q*2.4+drift*.6);
    float strata=clouds(q*4.0+vec3(warp*.75,-warp*.4,warp*.3)+drift);
    float deep=clouds(q*8.5-drift*.7+vec3(strata*.45));
    float banks=smoothstep(.26,.74,strata);
    float depth=smoothstep(.28,.72,deep);
    // More visible turquoise layers and bright teal rims, without washing out the mirror images.
    vec3 color=mix(vec3(.002,.036,.050),vec3(.018,.390,.430),pow(banks,1.08));
    color*=.86+.18*depth;
    color+=vec3(.035,.230,.235)*smoothstep(.48,.76,strata)*(.50+.50*depth);
    float horizon=pow(1.0-clamp(abs(direction.y),0.0,1.0),3.0);
    color+=vec3(.005,.070,.085)*horizon*(.35+.65*banks);
    return color;
}

vec3 reconstruct(vec2 uv,float depth){
    vec4 p=InverseViewProjection*vec4(uv*2.0-1.0,depth*2.0-1.0,1.0);
    return p.xyz/p.w;
}
bool sceneSample(vec3 rayPoint,out vec2 uv,out vec3 scenePoint,out float difference){
    vec4 clip=RiftProjection*RiftView*vec4(rayPoint,1.0);
    if(clip.w<=0.0)return false;
    uv=clip.xy/clip.w*.5+.5;
    if(any(lessThan(uv,vec2(.002)))||any(greaterThan(uv,vec2(.998))))return false;
    float depth=texture(SceneDepth,uv).r;
    if(depth>=.999999)return false;
    scenePoint=reconstruct(uv,depth);
    difference=(RiftView*vec4(scenePoint-rayPoint,0.0)).z;
    return true;
}
// Trace the real visible scene: moving creatures, their textures, blocks and existing lighting.
vec4 worldReflection(vec3 start,vec3 direction,out float hitDistance){
    hitDistance=100000.0;
    if(UseScene<.5||direction.y<=.003)return vec4(0.0);
    float previousDistance=.035;
    float previousDifference=-1.0;
    float maxTravel=min(120.0,Radius*3.0);
    for(int i=1;i<=40;i++){
        float distance=.035+pow(float(i)/40.0,1.7)*maxTravel;
        vec2 uv;vec3 scenePoint;float difference;
        if(!sceneSample(start+direction*distance,uv,scenePoint,difference)){
            previousDistance=distance;previousDifference=-1.0;continue;
        }
        if(difference>0.0&&previousDifference<=0.0){
            float lo=previousDistance,hi=distance;
            for(int j=0;j<5;j++){
                float mid=(lo+hi)*.5;
                vec2 middleUV;vec3 middlePoint;float middleDifference;
                if(sceneSample(start+direction*mid,middleUV,middlePoint,middleDifference)&&middleDifference>0.0)hi=mid;
                else lo=mid;
            }
            vec3 hit=start+direction*hi;
            if(sceneSample(hit,uv,scenePoint,difference)){
                vec3 local=scenePoint-Offset;
                float error=length(scenePoint-hit);
                if(local.y>.012&&length(local)<Radius&&error<.20+hi*.018){
                    float screenEdge=min(min(uv.x,uv.y),min(1.0-uv.x,1.0-uv.y));
                    float confidence=smoothstep(.008,.075,screenEdge)*(1.0-smoothstep(maxTravel*.80,maxTravel,hi));
                    hitDistance=length(scenePoint-start);
                    return vec4(texture(SceneColor,uv).rgb,confidence);
                }
            }
        }
        previousDifference=difference;previousDistance=distance;
    }
    return vec4(0.0);
}
void vfxMaterialMain(){
    vec2 p=waterPosition.xz;
    float r=length(p);
    float coverage=1.0-smoothstep(Radius*Expansion-.22,Radius*Expansion+.02,r);
    if(coverage*Visibility<.003)discard;
    // Long, low-amplitude swells only perturb the mirror; no striped color or checker caustics.
    float w1=dot(p,vec2(.82,.57))*.38-Time*.35;
    float w2=dot(p,vec2(-.46,.89))*.61+Time*.27;
    vec2 slope=vec2(.82,.57)*cos(w1)*.006+vec2(-.46,.89)*cos(w2)*.0035;
    vec3 normal=normalize(vec3(-slope.x,1.0,-slope.y));
    vec3 view=normalize(-cameraRelative);
    float fresnel=pow(1.0-clamp(abs(dot(normal,view)),0.0,1.0),5.0);
    vec3 reflectedRay=reflect(-view,normal);
    vec2 uv=projected.xy/projected.w*.5+.5;
    vec2 ripple=slope*.011;
    vec2 sampleUV=clamp(uv+ripple,vec2(.001),vec2(.999));
    vec4 reflected=texture(Reflection,sampleUV);
    float worldDistance;
    vec4 world=worldReflection(cameraRelative+vec3(0,.025,0),reflectedRay,worldDistance);
    float mirrorDepth=texture(ReflectionDepth,sampleUV).r;
    vec3 meshPoint=reconstruct(sampleUV,mirrorDepth);
    meshPoint.y=2.0*Offset.y-meshPoint.y;
    float meshDistance=mirrorDepth<.999999?length(meshPoint-cameraRelative):100000.0;
    if(world.a>0.0&&(reflected.a<.01||worldDistance<meshDistance)){
        reflected.rgb=mix(reflected.rgb,world.rgb,world.a);
        reflected.a=max(reflected.a,world.a);
    }
    vec3 color=deepMirrorEnvironment(waterPosition,reflectedRay);
    color+=vec3(.004,.035,.040)*fresnel;
    // Actual creatures, terrain, shrine and blades remain the dominant mirror image.
    float reflectedWeight=clamp(reflected.a,0.0,1.0)*(.84+.14*fresnel);
    color=mix(color,reflected.rgb*vec3(.84,.965,.99),reflectedWeight);
    // The outer third becomes the same crimson mist used at the dome's waterline.
    float boundary=max(Radius*Expansion,.001);
    float mist=smoothstep(boundary*.62,boundary*.997,r);
    float mistCloud=noise(vec3(p*.11,Time*.012));
    mist=clamp(mist+(mistCloud-.5)*.30*mist*(1.0-mist),0.0,1.0);
    color=mix(color,vec3(.23,.008,.035),mist);
    fragColor=vec4(color,coverage*Visibility);
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

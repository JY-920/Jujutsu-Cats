#version 150

// GLSL pow is undefined for negative bases, including pow(x, 2.0).
float vfxSquare(float x) { return x * x; }
vec2 vfxSquare(vec2 x) { return x * x; }
vec3 vfxSquare(vec3 x) { return x * x; }
vec4 vfxSquare(vec4 x) { return x * x; }
uniform float FlashEnabled;
uniform sampler2D SceneColor,SceneDepth,EnergyLayer;
uniform mat4 InverseViewProjection,ViewProjection;
uniform vec3 CenterRelative;
uniform float Age,Radius,Seed,Visibility,Distortion;
in vec2 texCoord;
in vec4 tint;
out vec4 fragColor;

vec4 upsample(float depth){
    vec2 size=vec2(textureSize(EnergyLayer,0)),p=texCoord*size-.5,base=floor(p),fraction=fract(p);
    vec4 sum=vec4(0);float weights=0.0;
    for(int y=0;y<2;y++)for(int x=0;x<2;x++){
        vec2 uv=(base+vec2(x,y)+.5)/size;
        float weight=(x==0?1.0-fraction.x:fraction.x)*(y==0?1.0-fraction.y:fraction.y);
        weight*=exp(-abs(texture(SceneDepth,uv).r-depth)/(.00012+(1.0-depth)*.005));
        sum+=texture(EnergyLayer,uv)*weight;weights+=weight;
    }
    return weights>.0001?sum/weights:vec4(0);
}
vec2 sphere(vec3 ray,float radius){
    float along=dot(ray,CenterRelative),disc=along*along+radius*radius-dot(CenterRelative,CenterRelative);
    if(disc<=0.0)return vec2(-1);
    float root=sqrt(disc);return vec2(along-root,along+root);
}
void vfxMaterialMain(){
    float depth=texture(SceneDepth,texCoord).r;
    vec4 energy=upsample(depth);
    vec2 size=vec2(textureSize(SceneColor,0)),border=.5/size;
    vec4 farPoint=InverseViewProjection*vec4(texCoord*2.0-1.0,1,1);
    vec3 ray=normalize(farPoint.xyz*(farPoint.w<0.0?-1.0:1.0));
    float worldDepth=100000.0;
    vec3 surface=ray*worldDepth;
    if(depth<.999999){
        vec4 p=InverseViewProjection*vec4(texCoord*2.0-1.0,depth*2.0-1.0,1);
        surface=p.xyz/p.w;worldDepth=dot(surface,ray);
    }
    float elapsed=max(Age-1.0,0.0),front=Radius*clamp(elapsed,.035,1.0);
    vec2 pressure=vec2(0);vec3 radiance=vec3(0);
    if(Age>=1.0&&elapsed<2.25){
        vec2 contact=sphere(ray,front);
        float hit=contact.x>.025?contact.x:contact.y;
        if(hit>.025&&hit<worldDepth){
            vec3 n=normalize(ray*hit-CenterRelative);
            float rim=pow(max(0.0,1.0-abs(dot(n,ray))),3.0);
            float folds=.5+.5*sin(dot(n,vec3(28,17,-21))+Seed*.12+elapsed*13.0);
            float shock=(.10+.85*rim)*exp(-elapsed*1.9)*Visibility;
            // World-space shell normals refract outward in every direction, including from inside the blast.
            vec4 projected=ViewProjection*vec4(ray*hit,1);
            vec4 displaced=ViewProjection*vec4(ray*hit+n*.3,1);
            vec2 direction=vec2(0);
            if(projected.w>.001&&displaced.w>.001)direction=displaced.xy/displaced.w-projected.xy/projected.w;
            pressure=direction/max(length(direction),.001)*shock*(.010+.006*folds);
            radiance+=mix(vec3(.18,.025,.74),vec3(.92,.64,1.0),rim)*shock*.65;
            float flash=exp(-elapsed*20.0)*Visibility*FlashEnabled;
            radiance+=vec3(.88,.71,1.0)*flash*.50;
        }
        if(depth<.999999){
            vec3 local=surface-CenterRelative;
            float crossing=exp(-vfxSquare((length(local)-front)/max(.18,Radius*.019)));
            float fade=exp(-elapsed*1.5)*Visibility;
            radiance+=vec3(.61,.24,1.0)*crossing*fade*.62;
        }
    }
    float contraction=Age<1.0?smoothstep(.55,1.0,Age):0.0;
    vec2 turbulence=vec2(sin(texCoord.y*113.0+Age*17.0+Seed),cos(texCoord.x*127.0-Age*19.0))
            *energy.a*(.0018+contraction*.0022);
    vec2 warped=clamp(texCoord+(pressure+turbulence)*Distortion,border,1.0-border);
    if(abs(texture(SceneDepth,warped).r-depth)>.0015)warped=mix(texCoord,warped,.06);
    vec3 background=texture(SceneColor,warped).rgb;
    vec3 result=background*(1.0-energy.a)+energy.rgb+radiance;
    fragColor=vec4(result,1.0)*tint;
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

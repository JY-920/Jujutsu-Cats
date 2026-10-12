#version 150

// GLSL pow is undefined for negative bases, including pow(x, 2.0).
float vfxSquare(float x) { return x * x; }
vec2 vfxSquare(vec2 x) { return x * x; }
vec3 vfxSquare(vec3 x) { return x * x; }
vec4 vfxSquare(vec4 x) { return x * x; }
uniform float FlashEnabled;
uniform sampler2D SceneColor, SceneDepth, FireLayer;
uniform mat4 InverseViewProjection;
uniform vec3 CenterRelative;
uniform vec4 BurstScreen;
uniform float Time, Phase, PhaseTime, Radius, Distortion;
in vec2 texCoord;
in vec4 tint;
out vec4 fragColor;

vec4 upsample(float depth){
    vec2 size=vec2(textureSize(FireLayer,0));
    vec2 p=texCoord*size-.5, base=floor(p), fraction=fract(p);
    vec4 sum=vec4(0);float weights=0.0;
    for(int y=0;y<2;y++)for(int x=0;x<2;x++){
        vec2 uv=(base+vec2(x,y)+.5)/size;
        float weight=(x==0?1.0-fraction.x:fraction.x)*(y==0?1.0-fraction.y:fraction.y);
        float difference=abs(texture(SceneDepth,uv).r-depth);
        weight*=exp(-difference/(.00012+(1.0-depth)*.005));
        sum+=texture(FireLayer,uv)*weight;weights+=weight;
    }
    return weights>.0001?sum/weights:vec4(0);
}
void vfxMaterialMain(){
    float depth=texture(SceneDepth,texCoord).r;
    vec4 fire=upsample(depth);
    vec2 size=vec2(textureSize(SceneColor,0)),aspect=vec2(size.x/size.y,1),border=.5/size;
    vec2 delta=(texCoord-BurstScreen.xy)*aspect;
    float screenDistance=length(delta),shock=0.0,flash=0.0,ground=0.0;
    vec2 pressure=vec2(0);
    if(Phase>1.5){
        if(BurstScreen.z>0.0){
            float radius=BurstScreen.z*(.04+1.25*(1.0-exp(-PhaseTime*3.8)));
            float edge=(screenDistance-radius)/max(BurstScreen.z*.06,.005);
            float visibility=1.0-smoothstep(.0003,.003,max(BurstScreen.w-depth,0.0));
            shock=exp(-edge*edge)*exp(-PhaseTime*2.7)*visibility;
            pressure=delta/max(screenDistance,.001)/aspect*shock*.012;
            flash=exp(-screenDistance*screenDistance/max(BurstScreen.z*BurstScreen.z,.002))
                    *exp(-PhaseTime*18.0)*visibility;
        }
        if(depth<.999999){
            vec4 p=InverseViewProjection*vec4(texCoord*2.0-1.0,depth*2.0-1.0,1);
            vec3 local=p.xyz/p.w-CenterRelative;
            float front=Radius*(.04+1.14*(1.0-exp(-PhaseTime*4.5)));
            ground=exp(-vfxSquare((length(local.xz)-front)/max(.10,Radius*.022)))
                    *exp(-abs(local.y)/max(.20,Radius*.10))*exp(-PhaseTime*2.1);
        }
    }
    vec2 turbulence=vec2(sin(texCoord.y*173.0-Time*23.0)+sin(texCoord.x*71.0+Time*15.0),
                         cos(texCoord.x*151.0-Time*18.0))*.0021*fire.a;
    vec2 warped=clamp(texCoord+(turbulence+pressure)*Distortion,border,1.0-border);
    if(abs(texture(SceneDepth,warped).r-depth)>.0015)warped=mix(texCoord,warped,.08);
    vec3 background=texture(SceneColor,warped).rgb;
    vec3 color=background*(1.0-fire.a)+fire.rgb;
    color+=vec3(1.0,.30,.018)*ground*.65;
    color+=vec3(.40,.20,.07)*(flash*.60*FlashEnabled+shock*.07)*Distortion;
    fragColor=vec4(color,1.0)*tint;
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

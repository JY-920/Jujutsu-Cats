#version 150
uniform sampler2D RawSceneColor,RawSceneDepth;
uniform float RawSceneReady;
uniform sampler2D SceneColor,SceneDepth;
uniform vec4 FocusBounds;
uniform float Age,Seed,Effects,Quiet,Visibility;
in vec2 texCoord;
in vec4 tint;
out vec4 fragColor;
float hash(float n){return fract(sin(n*127.1+Seed*17.17)*43758.5453);}
void vfxMaterialMain(){
    vec2 size=vec2(textureSize(SceneColor,0));
    vec3 scene=texture(SceneColor,texCoord).rgb;
    vec2 q=(texCoord-FocusBounds.xy)*size;
    float spread=max(FocusBounds.z*size.x,FocusBounds.w*size.y),lines=0.0;
    // The opening accents are a short composition, not persistent fixed scars.
    for(int i=0;i<19;i++){
        float id=float(i),angle=hash(id+1.0)*6.2831853;
        vec2 axis=vec2(cos(angle),sin(angle)),normal=vec2(-axis.y,axis.x);
        float offset=(hash(id+11.0)-.5)*spread*1.65;
        float d=abs(dot(q,normal)-offset);
        float along=dot(q,axis),extent=max(size.x,size.y)*.82;
        float progress=clamp((Age-id*.018)*25.0,0.0,1.0);
        float tip=mix(-extent,extent,progress);
        float stroke=(1.0-smoothstep(.40,1.2,d))*step(along,tip)*step(-extent,along);
        lines=max(lines,stroke*(.75+.25*hash(id+33.0)));
    }
    float depth=texture(SceneDepth,texCoord).r;
    float contour=clamp(length(vec2(dFdx(depth),dFdy(depth)))*800.0,0.0,1.0);
    vec2 body=(texCoord-FocusBounds.xy)/max(FocusBounds.zw,vec2(.001));
    contour*=1.0-smoothstep(.85,1.25,length(body));
    float ink=smoothstep(0.0,.10,Age)*(1.0-smoothstep(.68,.87,Age))*(1.0-Quiet);
    float flash=smoothstep(.67,.77,Age)*(1.0-smoothstep(.78,.98,Age))*Effects;
    float luminance=dot(scene,vec3(.30,.55,.15));
    float exposed=smoothstep(.032,.26,max(scene.r*.82,luminance*.88));
    // Scarlet sky and reflected ground retain dark shrine/terrain detail and the real black victim.
    vec3 red=mix(vec3(.005,.001,.006),vec3(1.28,.012,.035),exposed);
    red+=vec3(.14,.002,.008)*smoothstep(.3,.9,luminance);
    float invade=smoothstep(Quiet>.5?0.0:.76,Quiet>.5?.28:1.06,Age)*Visibility;
    vec3 color=mix(scene,red,invade);
    color=mix(color,vec3(max(lines,contour*.33)),ink*Visibility);
    if(RawSceneReady>.5){
        vec3 raw=texture(RawSceneColor,texCoord).rgb;
        float rawDepth=texture(RawSceneDepth,texCoord).r;
        bool inkPixel=max(raw.r,max(raw.g,raw.b))<.012&&rawDepth<.999999;
        float bounds=1.0-smoothstep(.98,1.03,max(abs(body.x),abs(body.y)));
        if(inkPixel&&abs(rawDepth-depth)<.00003)
            color=mix(color,vec3(.001,.001,.003),bounds*Visibility*(1.0-ink));
    }
    color=mix(color,vec3(1.0),flash*Visibility);
    fragColor=vec4(color,1.0)*tint;
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

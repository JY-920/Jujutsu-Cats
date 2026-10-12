#version 150

// GLSL pow is undefined for negative bases, including pow(x, 2.0).
float vfxSquare(float x) { return x * x; }
vec2 vfxSquare(vec2 x) { return x * x; }
vec3 vfxSquare(vec3 x) { return x * x; }
vec4 vfxSquare(vec4 x) { return x * x; }
uniform sampler2D SceneColor;
uniform sampler2D SceneDepth;
uniform vec4 Viewport;
uniform float UseScene;
uniform float Distortion;
uniform vec3 DomainCenter;
uniform float DomainRadius;
uniform float FocusMono;
in vec2 uv;
in vec3 cameraRelative;
in vec4 animation;
out vec4 fragColor;
float profile(float y){return pow(max(0.0,1.0-abs(y)),.72)*(.86+.085*sin(y*19.0)+.045*sin(y*43.0+1.0));}
void vfxMaterialMain(){
    if(DomainRadius>0.0&&length(cameraRelative-DomainCenter)>DomainRadius)discard;
    // Same one-way blade front as the core and its water reflection.
    float behind=animation.g*1.10-(1.0-uv.y)*.5;
    float sweep=smoothstep(0.0,.10,behind);
    float edge=abs(uv.x-.045*sin(uv.y*11.0)-.022*sin(uv.y*27.0))-profile(uv.y)*sqrt(sweep);
    vec2 edgeSlope=vec2(dFdx(edge),dFdy(edge));
    if(sweep<.003||abs(uv.y)>1.03||animation.r<.003)discard;
    if(edge<0.0)discard;
    float tips=1.0-smoothstep(.96,1.03,abs(uv.y));
    float nearGlow=exp(-edge*3.4)*tips;
    float softGlow=exp(-edge*1.25)*tips;
    float lead=exp(-vfxSquare((behind-.025)/.035))*(1.0-smoothstep(.94,1.0,animation.g));
    float opacity=animation.r*sweep;
    float halo=max(nearGlow,softGlow*.55)*(1.0+lead*(.65+animation.b*.2))*opacity;
    if(halo<.004)discard;
    float style=min(floor(animation.a*4.0),3.0);
    vec3 nearColor=(style<.5||style>2.5)?vec3(1.65):vec3(1.90,.008,.034);
    vec3 softColor=style>2.5?vec3(.68):vec3(.84,.003,.022);
    if(FocusMono>.5){
        float monochrome=min(floor(animation.a*3.0),2.0);
        if(monochrome<.5)discard; // A pure black cut has no red/white luminous halo.
        nearColor=vec3(1.80);softColor=vec3(.56);
    }
    vec3 glow=(nearColor*nearGlow+softColor*softGlow)*opacity;
    glow+=nearColor*lead*softGlow*opacity;
    vec2 screen=(gl_FragCoord.xy-Viewport.xy)/Viewport.zw;
    float alpha=clamp(halo*.82,0.0,.92);
    if(UseScene>.5){
        vec2 outward=normalize(edgeSlope+vec2(.000001));
        float wave=sin(edge*9.0+uv.y*18.0);
        vec2 shift=outward*(5.0+wave*2.5+lead*5.0)*softGlow*opacity*Distortion/Viewport.zw;
        vec2 warped=clamp(screen+shift,vec2(.001),vec2(.999));
        if(texture(SceneDepth,warped).r<gl_FragCoord.z-.00002)warped=screen;
        vec3 background=texture(SceneColor,warped).rgb;
        vec2 fringe=outward*softGlow*opacity*Distortion*.8/Viewport.zw;
        vec2 redUV=clamp(warped+fringe,vec2(.001),vec2(.999));
        vec2 blueUV=clamp(warped-fringe,vec2(.001),vec2(.999));
        if(FocusMono<.5){
            if(texture(SceneDepth,redUV).r>=gl_FragCoord.z-.00002)background.r=texture(SceneColor,redUV).r;
            if(texture(SceneDepth,blueUV).r>=gl_FragCoord.z-.00002)background.b=texture(SceneColor,blueUV).b;
        }
        fragColor=vec4(background+glow*.75,alpha);
    }else fragColor=vec4(glow,alpha);
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

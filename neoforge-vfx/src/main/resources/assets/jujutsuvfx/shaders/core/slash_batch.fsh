#version 150

// GLSL pow is undefined for negative bases, including pow(x, 2.0).
float vfxSquare(float x) { return x * x; }
vec2 vfxSquare(vec2 x) { return x * x; }
vec3 vfxSquare(vec3 x) { return x * x; }
vec4 vfxSquare(vec4 x) { return x * x; }
uniform vec3 DomainCenter;
uniform float DomainRadius;
uniform float FocusMono;
in vec2 uv;
in vec3 cameraRelative;
in vec4 animation;
out vec4 fragColor;
float profile(float y){return pow(max(0.0,1.0-abs(y)),.72)*(.86+.085*sin(y*19.0)+.045*sin(y*43.0+1.0));}
void vfxMaterialMain(){
    // Travel from +Y to -Y. The short trailing transition has finished everywhere
    // at progress 1, including the endpoint, before the persistent mesh takes over.
    // Keep this sweep in sync with rift_edge and water_reflection.
    float behind=animation.g*1.10-(1.0-uv.y)*.5;
    float sweep=smoothstep(0.0,.10,behind);
    float edge=abs(uv.x-.045*sin(uv.y*11.0)-.022*sin(uv.y*27.0))-profile(uv.y)*sqrt(sweep);
    float aa=max(fwidth(edge),.002);
    // Derive the local plane frame from screen derivatives for view-dependent depth inside the cut.
    vec3 px=dFdx(cameraRelative),py=dFdy(cameraRelative);
    vec2 tx=dFdx(uv),ty=dFdy(uv);
    float det=tx.x*ty.y-tx.y*ty.x;
    float invDet=abs(det)>.00000001?1.0/det:0.0;
    vec3 tangent=(px*ty.y-py*tx.y)*invDet,bitangent=(py*tx.x-px*ty.x)*invDet;
    if((DomainRadius>0.0&&length(cameraRelative-DomainCenter)>DomainRadius)
            ||sweep<.003||animation.r<.003||abs(uv.y)>=1.0||edge>0.0)discard;
    vec3 t=tangent/max(length(tangent),.0001),b=bitangent/max(length(bitangent),.0001);
    vec3 normal=cross(t,b),ray=normalize(cameraRelative);
    vec2 drift=vec2(dot(ray,t),dot(ray,b))/max(abs(dot(ray,normal)),.25);
    drift/=vec2(max(length(tangent),.12),max(length(bitangent),.5));
    drift=clamp(drift,vec2(-2.0),vec2(2.0));
    float style=min(floor(animation.a*4.0),3.0);
    vec3 rimColor=(style<.5||style>2.5)?vec3(1.50):vec3(1.75,.008,.032);
    vec3 core=style<.5?vec3(.46,.001,.014):style<1.5?vec3(.001,.0004,.001)
            :style<2.5?vec3(1.10,.001,.018):vec3(1.18);
    vec3 veins=style<.5?vec3(.52,.003,.017):style<1.5?vec3(.028,.0002,.001)
            :style<2.5?vec3(.18,.0,.003):vec3(.025);
    // Focused scarlet settlement uses only black, white, or black with a white edge.
    // Keep the original four colored materials unchanged for ordinary domain cuts.
    if(FocusMono>.5){
        float monochrome=min(floor(animation.a*3.0),2.0);
        core=monochrome>.5&&monochrome<1.5?vec3(1.45):vec3(.001);
        rimColor=monochrome<.5?vec3(.001):vec3(1.80);
        veins=vec3(0.0);
    }
    for(int i=0;i<4;i++){
        float depth=.08+float(i)*.16;
        vec2 q=uv+drift*depth;
        float vein=pow(.5+.5*sin(q.x*9.0+q.y*13.0+animation.a*53.0+depth*11.0
                +sin(q.y*21.0+depth)*1.6),15.0);
        core+=veins*vein*exp(-depth*1.8);
    }
    float rim=1.0-smoothstep(.045,max(FocusMono>.5?.25:.19,aa*2.2),-edge);
    core+=rimColor*.22*exp(edge*18.0);
    vec3 color=mix(core,rimColor,rim);
    float tip=exp(-vfxSquare((behind-.025)/.035))*(1.0-smoothstep(.94,1.0,animation.g));
    // Only the moving blade tip flashes; the already-cut length remains still.
    color+=rimColor*1.2*tip*(1.0+animation.b*.2);
    fragColor=vec4(color,animation.r*sweep);
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

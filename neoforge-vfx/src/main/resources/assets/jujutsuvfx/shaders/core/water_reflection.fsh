#version 150

// GLSL pow is undefined for negative bases, including pow(x, 2.0).
float vfxSquare(float x) { return x * x; }
vec2 vfxSquare(vec2 x) { return x * x; }
vec3 vfxSquare(vec3 x) { return x * x; }
vec4 vfxSquare(vec4 x) { return x * x; }
uniform float Reveal;
uniform float Visibility;
uniform float Radius;
uniform float Mode;
in vec3 waterPosition;
in vec3 modelPosition;
in vec2 uv;
in vec4 tint;
out vec4 fragColor;
float hash(vec3 p){p=fract(p*.1031);p+=dot(p,p.yzx+33.33);return fract((p.x+p.y)*p.z);}
void vfxMaterialMain(){
    if(waterPosition.y<.001||length(waterPosition)>Radius)discard;
    vec3 color=tint.rgb;
    float opacity=1.0;
    if(Mode<.5){
        float grain=hash(floor(modelPosition*19.0));
        if(modelPosition.y>Reveal*8.7||grain>Visibility)discard;
        if(uv.x<.5)color*=.87+grain*.17;
    }else{
        // Cut-mode vertex colors carry opacity and progress, matching the world blade.
        float behind=tint.g*1.10-(1.0-uv.y)*.5;
        float sweep=smoothstep(0.0,.10,behind);
        opacity=tint.r*sweep;
        if(opacity<.003)discard;
        float profile=pow(max(0.0,1.0-abs(uv.y)),.72)*(.86+.085*sin(uv.y*19.0)+.045*sin(uv.y*43.0+1.0));
        float spine=.045*sin(uv.y*11.0)+.022*sin(uv.y*27.0);
        float edge=abs(uv.x-spine)-profile*sqrt(sweep);
        if(edge>0.0||abs(uv.y)>=1.0)discard;
        float rim=1.0-smoothstep(.04,.19,-edge);
        float style=tint.b>.5?min(floor(tint.a*4.0),3.0):-1.0;
        vec3 rimColor=(style<.5||style>2.5)?vec3(1.50):vec3(1.75,.008,.032);
        vec3 base=style<-.5?vec3(.035,.001,.012):style<.5?vec3(.46,.001,.014)
                :style<1.5?vec3(.001,.0004,.001):style<2.5?vec3(1.10,.001,.018):vec3(1.18);
        color=mix(base,rimColor,rim);
        float tip=exp(-vfxSquare((behind-.025)/.035))*(1.0-smoothstep(.94,1.0,tint.g));
        color+=rimColor*1.2*tip;
    }
    fragColor=vec4(color,opacity);
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

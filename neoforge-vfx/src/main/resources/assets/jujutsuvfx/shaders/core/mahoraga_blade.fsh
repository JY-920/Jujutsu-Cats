#version 150
in vec2 uv;
in vec4 tint;
uniform float Time;
out vec4 fragColor;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
void vfxMaterialMain(){
    float edge=0.72+sin(uv.x*19.0+Time*2.0)*0.035;
    float d=abs(uv.y-edge);
    float core=exp(-d*d*650.0);
    float glow=exp(-d*d*26.0);
    float fibers=pow(max(0.0,sin(uv.y*76.0+uv.x*7.0)),8.0)*0.15;
    float grain=0.87+0.13*hash(floor(uv*vec2(260.0,46.0)));
    float rim=smoothstep(0.0,0.13,uv.y)*(1.0-smoothstep(0.86,1.0,uv.y));
    float alpha=tint.a*rim*grain*min(1.0,core+glow*0.43+fibers*glow);
    if(alpha<0.003)discard;
    vec3 color=mix(tint.rgb,vec3(1.55,1.57,1.65),core);
    fragColor=vec4(color,alpha);
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

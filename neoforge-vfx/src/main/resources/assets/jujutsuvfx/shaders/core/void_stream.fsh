#version 150
in vec2 uv;
in vec4 tint;
out vec4 fragColor;
void vfxMaterialMain() {
    float taper=pow(max(0.0,1.0-uv.y*uv.y),.42);
    float head=.56+.44*smoothstep(-.95,.72,uv.y);
    float edge=1.0-smoothstep(.65,1.0,abs(uv.x));
    // Separate the colored bloom, shoulder and hot filament without bleaching the whole beam white.
    float pixelWidth=max(fwidth(uv.x)*.42,.0001);
    float width=sqrt(.0064+pixelWidth*pixelWidth);
    float core=exp(-uv.x*uv.x/(width*width))*.08/width;
    float shoulder=exp(-uv.x*uv.x*32.0);
    float halo=exp(-uv.x*uv.x*4.5)*edge;
    float alpha=min(1.0,core*.92+shoulder*.42+halo*.28)*taper*head*tint.a;
    if(alpha<.002)discard;
    vec3 light=mix(tint.rgb,vec3(1.0),core*.12);
    fragColor=vec4(light*(1.0+shoulder*.22),alpha);
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

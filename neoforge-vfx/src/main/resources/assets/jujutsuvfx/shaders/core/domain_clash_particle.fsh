#version 150
in vec2 uv;
in vec4 tint;
out vec4 fragColor;
void vfxMaterialMain(){
    float taper=pow(max(0.0,1.0-uv.y*uv.y),.6);
    float core=exp(-abs(uv.x)*10.0)*taper;
    float glow=exp(-uv.x*uv.x*3.5)*taper;
    float alpha=(core*.8+glow*.30)*tint.a;
    if(alpha<.003)discard;
    fragColor=vec4(tint.rgb*(.7+core*.8)+vec3(.75)*core,alpha);
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

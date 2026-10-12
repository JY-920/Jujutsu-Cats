#version 150

// GLSL pow is undefined for negative bases, including pow(x, 2.0).
float vfxSquare(float x) { return x * x; }
vec2 vfxSquare(vec2 x) { return x * x; }
vec3 vfxSquare(vec3 x) { return x * x; }
vec4 vfxSquare(vec4 x) { return x * x; }
in vec2 uv;
in vec4 tint;
out vec4 fragColor;
void vfxMaterialMain(){
    float distanceToEdge=abs(uv.x-.5)*2.0;
    float core=1.0-smoothstep(.07,.22,distanceToEdge);
    float glow=vfxSquare(max(0.0,1.0-distanceToEdge));
    fragColor=vec4(mix(tint.rgb,vec3(1.0),core),tint.a*max(core,glow*.7));
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

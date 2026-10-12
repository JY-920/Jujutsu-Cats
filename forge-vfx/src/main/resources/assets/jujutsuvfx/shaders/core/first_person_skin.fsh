#version 150
uniform sampler2D SkinTexture;
in vec2 texCoord;
in vec4 skinColor;
out vec4 fragColor;
void vfxMaterialMain(){
    vec4 color=texture(SkinTexture,texCoord)*skinColor;
    if(color.a<.1)discard;
    fragColor=color;
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

#version 150
uniform sampler2D SkinTexture;
uniform vec3 OutlineData;
in vec2 texCoord;
in float skinAlpha;
out vec4 fragColor;
void vfxMaterialMain() {
    if (texture(SkinTexture, texCoord).a * skinAlpha < 0.12) discard;
    fragColor = vec4(1.0, OutlineData);
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

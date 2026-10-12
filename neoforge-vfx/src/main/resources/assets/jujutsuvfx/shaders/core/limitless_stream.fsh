#version 150
in vec2 uv;
in vec4 tint;
in vec3 relativePosition;
uniform vec3 CenterRelative;
uniform float CoreRadius;
out vec4 fragColor;
void vfxMaterialMain() {
    // The bright volume owns no depth attachment. Hide rear filaments behind its opaque heart.
    float distanceToFragment = length(relativePosition);
    vec3 ray = relativePosition / max(distanceToFragment, .0001);
    float along = dot(CenterRelative, ray);
    float centerSquared = dot(CenterRelative, CenterRelative);
    float discriminant = along * along - centerSquared + CoreRadius * CoreRadius;
    if (discriminant > 0.0) {
        float halfChord = sqrt(discriminant);
        float nearCore = along - halfChord, farCore = along + halfChord;
        if (nearCore > 0.0 && nearCore < distanceToFragment - .025) discard;
        if (centerSquared < CoreRadius * CoreRadius && farCore > 0.0 && farCore < distanceToFragment - .025) discard;
    }
    float taper = smoothstep(0.0, .15, uv.y) * (1.0 - smoothstep(.89, 1.0, uv.y));
    float edge = 1.0 - smoothstep(.66, 1.0, abs(uv.x));
    float aa = max(fwidth(uv.x) * .40, .0001);
    float width = sqrt(.0121 + aa * aa);
    float core = exp(-uv.x * uv.x / (width * width)) * .11 / width;
    float shoulder = exp(-uv.x * uv.x * 23.0);
    float halo = exp(-uv.x * uv.x * 4.7) * edge;
    float alpha = min(1.0, core * .91 + shoulder * .45 + halo * .32) * taper * tint.a;
    if (alpha < .002) discard;
    float head = smoothstep(.10, .92, uv.y);
    vec3 emission = mix(tint.rgb, vec3(.80, .97, 1.0), min(1.0, core * (.18 + head * .56)));
    fragColor = vec4(emission * (1.0 + shoulder * .26 + head * .13), alpha);
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

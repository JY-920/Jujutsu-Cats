#version 150

// GLSL pow is undefined for negative bases, including pow(x, 2.0).
float vfxSquare(float x) { return x * x; }
vec2 vfxSquare(vec2 x) { return x * x; }
vec3 vfxSquare(vec3 x) { return x * x; }
vec4 vfxSquare(vec4 x) { return x * x; }
uniform float Time, Power, Seed, Charged, FlameAspect;
uniform vec2 Bend, CoreHalf, FlameUp, CanvasHalf;
uniform vec4 FlamePose, FlameFlicker;
in vec2 uv;
flat in vec4 material;
out vec4 fragColor;

float hash(vec2 p) {
    // Small multipliers retain noise variation even after the upward flow has traveled a long way.
    vec3 q = fract(p.xyx * .1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x),
               mix(hash(i + vec2(0, 1)), hash(i + vec2(1)), f.x), f.y);
}
float roundedCore(vec2 p) {
    float radius = min(.085, min(CoreHalf.x, CoreHalf.y) * .40);
    vec2 q = abs(p) - CoreHalf + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}
vec2 flameSpace(vec2 p) {
    vec2 q = p * CanvasHalf;
    return vec2(q.x * FlameUp.y - q.y * FlameUp.x, dot(q, FlameUp) / FlameAspect);
}

void vfxMaterialMain() {
    if (material.r > .75) {
        // These UVs cross a connected, jagged ribbon. The space between chains is never filled.
        float across = abs(uv.x);
        float aa = min(.10, max(fwidth(across), .008));
        float core = 1.0 - smoothstep(.29 - aa, .36 + aa, across);
        float rim = exp(-vfxSquare((across - .46) / .14));
        float glow = pow(max(0.0, 1.0 - across), 1.5);
        float surge = pow(.5 + .5 * sin(uv.y * 23.0 - Time * 43.0 + Seed), 8.0);
        vec3 red = vec3(1.35 + surge * .65, .008 + surge * .025, .027 + surge * .065);
        vec3 color = mix(red * (.50 + rim * .62), vec3(.0015, .0002, .0005), core);
        float alpha = max(core * .98, rim * .97 + glow * .21) * material.a;
        alpha *= 1.0 - smoothstep(.90, 1.0, across);
        if (alpha < .003) discard;
        fragColor = vec4(color, alpha);
        return;
    }

    // The coating stays in arm coordinates; both palettes share a separately upright flame field.
    float skin = roundedCore(uv);
    vec2 rising = flameSpace(uv);
    vec2 wind = clamp(flameSpace(Bend), vec2(-.28, -.12), vec2(.28, .18));
    vec2 p = rising * 1.18;
    p -= wind * smoothstep(-.005, .32, skin) * .62;
    p.y -= .11;
    // CPU supplies a held 24 Hz time and independent shape anchors, shared by blue and red flames.
    float time = Time * 8.5 + Seed * .071;
    vec2 flow = vec2(p.x * 4.8 + sin(p.y * 5.0 + FlamePose.x * 3.7 + FlameFlicker.x * .65) * .72,
                     p.y * 5.5 - time * 3.8 + FlamePose.y * 1.4);
    float coarse = noise(flow), curl = noise(flow * 1.8 + vec2(FlamePose.z * 3.1, -time * .85 + FlamePose.w));
    vec2 shape = p - vec2(FlamePose.y * .043 + FlameFlicker.x * .012, -.025 + FlamePose.w * .028);
    shape.x *= 1.08 + FlamePose.x * .07;
    float angle = atan(shape.y, shape.x), radial = length(shape);
    float lobes = (.13 + FlamePose.w * .025) * sin(angle * 3.0 + FlamePose.x * 3.1 + FlameFlicker.y * .60)
                + (.075 + FlameFlicker.x * .015) * sin(angle * 7.0 + FlamePose.y * 4.4 + FlameFlicker.z * .70)
                + .038 * sin(angle * 13.0 + FlamePose.z * 5.2 + FlameFlicker.w * .90);
    lobes += FlamePose.z * .050 * shape.x;
    float upward = smoothstep(-.1, .6, p.y);
    float tongues = upward * ((.14 + FlamePose.y * .025)
            * sin(angle * 9.0 + FlamePose.w * 4.0 + FlameFlicker.y * .75) + .11 * coarse);
    float radius = clamp(.64 + lobes + tongues + (coarse - .5) * .29, .35, .90);
    float flame = radial - radius;
    // Limit the lower flame edge at the bottom of the fist, measured along flame-up, not the fingers.
    float bottom = dot(abs(FlameUp), CoreHalf * CanvasHalf) / FlameAspect;
    float base = -bottom - .010 + (curl - .5) * .016;
    flame = max(flame, (base - rising.y) * 1.18);
    // Only the fitted coating has a wrist-side cutoff; upward tongues remain free to rise past it.
    float cuff = -CoreHalf.y - .010 + (curl - .5) * .016
            + .008 * sin(uv.x * 17.0 + FlamePose.y * 4.7 + FlameFlicker.w * .50);
    float coating = max((skin - .025) * 1.18, (cuff - uv.y) * 1.18);
    float d = min(flame, coating);
    float aa = max(fwidth(d), .002);
    float body = 1.0 - smoothstep(-aa, aa, d);
    float inkWidth = .074 + .12 * curl
            + .036 * sin(angle * 5.0 + FlamePose.z * 4.3 + FlameFlicker.x * .55);
    float ink = (1.0 - smoothstep(inkWidth * .58, inkWidth, -d)) * body;
    float vein = pow(.5 + .5 * sin(angle * 11.0 + curl * 12.0 + radial * 24.0
            + FlamePose.w * 5.2 + FlameFlicker.z * .75), 5.0);
    ink = max(ink, vein * (1.0 - smoothstep(.12, .27, -d)) * .83 * body);
    float brightEdge = exp(-vfxSquare((d + .005) / max(.012, aa * 1.2))) * (.33 + .67 * coarse);
    float glow = exp(-vfxSquare(max(d, 0.0) / .047)) * (1.0 - body) * .17;
    float red = clamp(Charged, 0.0, 1.0);
    vec3 lowColor = mix(vec3(.012, .42, .55), vec3(.58, .010, .025), red);
    vec3 highColor = mix(vec3(.024, .84, .99), vec3(1.03, .025, .065), red);
    vec3 rippleColor = mix(vec3(.01, .10, .13), vec3(.15, .006, .016), red);
    vec3 inkColor = mix(vec3(.0015, .007, .009), vec3(.006, .0003, .001), red);
    vec3 edgeColor = mix(vec3(.025, 1.13, 1.30), vec3(1.40, .025, .070), red);
    vec3 glowColor = mix(vec3(.02, .69, .86), vec3(1.05, .012, .055), red);
    vec3 energy = mix(lowColor, highColor, coarse);
    energy += rippleColor * pow(.5 + .5 * sin(p.y * 12.0 + curl * 7.0 - time * 2.3
            + FlamePose.x * 3.1 + FlameFlicker.y * .70), 4.0);
    vec3 color = mix(energy, inkColor, ink);
    color = mix(color, edgeColor, brightEdge * .94);
    color = mix(color, glowColor, glow * 2.5);
    float alpha = max(body * mix(.32 + .13 * coarse, .97, ink), brightEdge * .80 + glow);
    alpha *= clamp(.91 + Power * .09, 0.0, 1.04) * material.a;
    alpha *= 1.0 - smoothstep(.94, 1.0, max(abs(uv.x), abs(uv.y)));
    if (alpha < .003) discard;
    fragColor = vec4(color, alpha);
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

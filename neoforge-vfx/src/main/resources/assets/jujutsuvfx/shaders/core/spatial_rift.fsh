#version 150

// GLSL pow is undefined for negative bases, including pow(x, 2.0).
float vfxSquare(float x) { return x * x; }
vec2 vfxSquare(vec2 x) { return x * x; }
vec3 vfxSquare(vec3 x) { return x * x; }
vec4 vfxSquare(vec4 x) { return x * x; }

uniform sampler2D SceneColor;
uniform sampler2D SceneDepth;
uniform vec2 Viewport;
uniform vec3 EyeLocal;
uniform vec2 HalfSize;
uniform float FaceZ;
uniform float Time;
uniform float Seed;
uniform float Opening;
uniform float Surface;
uniform float UseScene;
uniform float Strike;
uniform float Impact;
uniform float CutProgress;
uniform vec3 DomainCenter;
uniform float DomainRadius;
in vec2 localUv;
in vec4 tint;
in vec3 cameraRelative;
out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}
float noise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1,0,0)), f.x),
                   mix(hash(i + vec3(0,1,0)), hash(i + vec3(1,1,0)), f.x), f.y),
               mix(mix(hash(i + vec3(0,0,1)), hash(i + vec3(1,0,1)), f.x),
                   mix(hash(i + vec3(0,1,1)), hash(i + vec3(1,1,1)), f.x), f.y), f.z);
}
float profile(float y) {
    return pow(max(0.0, 1.0 - abs(y)), 0.72)
        * (0.86 + 0.085 * sin(y * 19.0) + 0.045 * sin(y * 43.0 + 1.0));
}
float spine(float y) { return 0.045 * sin(y * 11.0) + 0.022 * sin(y * 27.0); }

vec3 interior(vec2 uv) {
    vec3 entry = vec3(uv * HalfSize, FaceZ);
    vec3 ray = normalize(entry - EyeLocal);
    float faceSign = FaceZ >= 0.0 ? 1.0 : -1.0;
    float travelZ = max(abs(ray.z), 0.16);
    vec3 color = vec3(0.006, 0.002, 0.012);
    // Fixed depth planes: the camera ray intersects them at different positions,
    // producing true view-dependent parallax without any camera-facing billboards.
    for (int i = 0; i < 18; i++) {
        if (Strike > 0.5 && i >= 6) break;
        float depth = 0.12 + float(i) * 0.24;
        vec3 p = entry + ray * depth / travelZ;
        vec2 q = p.xy / vec2(max(HalfSize.x, 0.05), max(HalfSize.y, 0.1));
        q.x += sin(q.y * 4.0 + depth * 1.1 + Time * 0.18) * 0.19;
        float warp = noise(vec3(q * vec2(2.6, 4.0), depth + Seed));
        float veins = pow(max(0.0, 1.0 - abs(sin(q.x * 7.0 + q.y * 2.0
                        + depth * 2.4 + warp * 3.0 - Time * 0.6))), 16.0);
        float fade = exp(-depth * 0.58);
        color += vec3(0.14, 0.008, 0.029) * veins * fade;
        vec2 stars = q * vec2(9.0, 30.0) + vec2(depth * 3.0, faceSign * depth);
        vec2 cell = floor(stars);
        float star = hash(vec3(cell, float(i) + Seed));
        float dotStar = pow(max(0.0, 1.0 - length(fract(stars) - 0.5) * 8.0), 3.0);
        color += vec3(0.55, 0.25, 0.35) * dotStar * step(0.976, star) * fade;
    }
    float falloff = pow(clamp(abs(uv.x - spine(uv.y)) / max(profile(uv.y), 0.001), 0.0, 1.0), 4.0);
    return color + vec3(0.17, 0.008, 0.022) * falloff;
}

void vfxMaterialMain() {
    if(DomainRadius>0.0&&length(cameraRelative-DomainCenter)>DomainRadius)discard;
    vec2 uv = localUv;
    // Sweep from the upper tip to the lower tip once; the completed scar then remains stationary.
    if (Strike > 0.5 && uv.y < 1.0 - 2.0 * CutProgress) discard;
    if (Surface > 0.5 && Surface < 1.5) {
        float rim = pow(abs(uv.x * 2.0 - 1.0), 7.0);
        float streak = pow(0.5 + 0.5 * sin(uv.y * 150.0 - Time * 8.0), 14.0);
        fragColor = vec4(mix(vec3(0.065, 0.003, 0.011), vec3(0.95, 0.37, 0.40), rim)
                       + vec3(0.22, 0.016, 0.03) * streak, 1.0) * tint;
        return;
    }
    float width = profile(uv.y);
    float edge = abs(uv.x - spine(uv.y)) - width;
    float aa = max(fwidth(edge), 0.002);

    if (Surface > 1.5) {
        if (edge < -aa || abs(uv.y) > 1.06) discard;
        float halo = exp(-max(edge, 0.0) * 12.0) * (1.0 - smoothstep(0.97, 1.065, abs(uv.y)));
        if (halo < 0.008) discard;
        vec3 glow = mix(vec3(0.80, 0.018, 0.065), vec3(1.0, 0.28, 0.31), Impact) * halo;
        vec2 screenUv = gl_FragCoord.xy / Viewport;
        if (UseScene > 0.5) {
            vec2 outward = normalize(vec2(dFdx(edge), dFdy(edge)) + vec2(0.000001));
            float ripple = sin(edge * 30.0 - Time * 4.5 + uv.y * 7.0);
            vec2 offset = outward * (4.0 + ripple * 3.0) * halo * Opening
                        * mix(1.0, 0.35, Strike) / Viewport;
            vec2 warpedUv = clamp(screenUv + offset, 0.001, 0.999);
            // Do not drag foreground silhouettes into a rift behind them.
            if (texture(SceneDepth, warpedUv).r < gl_FragCoord.z - 0.00002) warpedUv = screenUv;
            vec3 scene = texture(SceneColor, warpedUv).rgb;
            fragColor = vec4(scene + glow * 0.65, clamp(halo * 0.85, 0.0, 0.85));
        } else {
            fragColor = vec4(glow, halo * 0.65);
        }
        return;
    }

    if (edge > 0.0 || abs(uv.y) >= 1.0) discard;
    float rim = 1.0 - smoothstep(0.045, max(0.19, aa * 2.2), -edge);
    float innerRim = exp(edge * 22.0);
    float spark = pow(0.5 + 0.5 * sin(uv.y * 93.0 - Time * 14.0 + Seed), 22.0);
    vec3 color = interior(uv);
    color += vec3(0.48, 0.008, 0.036) * innerRim;
    color = mix(color, vec3(1.35, 1.30, 1.32), rim);
    color += vec3(0.65, 0.28, 0.31) * Impact * (0.25 + 0.75 * innerRim);
    color += vec3(0.20, 0.12, 0.14) * spark * innerRim;
    float tip=exp(-vfxSquare((uv.y-(1.0-2.0*CutProgress))/.07))*(1.0-step(.999,CutProgress))*Strike;
    color+=vec3(1.8,1.75,1.8)*tip;
    fragColor = vec4(color, Opening) * tint;
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

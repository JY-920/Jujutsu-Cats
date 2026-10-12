#version 150
uniform float Time;
uniform float Hand;
uniform vec2 CoreHalf;
uniform vec2 Bend;
in vec2 uv;
flat in vec4 material;
out vec4 fragColor;

float hash(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}
float inkNoise(vec2 p) {
    vec2 a = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(a), hash(a + vec2(1,0)), f.x),
               mix(hash(a + vec2(0,1)), hash(a + vec2(1,1)), f.x), f.y);
}
float roundBox(vec2 p, vec2 halfSize, float radius) {
    vec2 q = abs(p) - halfSize + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}
float joinShape(float a, float b, float softness) {
    float h = max(softness - abs(a - b), 0.0) / softness;
    return min(a, b) - h * h * softness * 0.25;
}
float bolt(vec2 p, float seed, float width) {
    float node = (p.y + 0.90) * 5.6;
    float row = floor(node);
    float a = (hash(vec2(row, seed)) - 0.5) * 0.78;
    float b = (hash(vec2(row + 1.0, seed)) - 0.5) * 0.78;
    float slope = (b - a) * 5.6;
    float line = mix(a, b, fract(node));
    return max(abs(p.x - line) / sqrt(1.0 + slope * slope) - width, abs(p.y) - 0.88);
}

void vfxMaterialMain() {
    // Shifted UVs mark red ink flames without changing the existing lightning/material encoding.
    bool redFlame = uv.y > 2.0;
    vec2 p = uv - vec2(0, redFlame ? 4.0 : 0.0);
    float crop = 1.0 - smoothstep(0.93, 1.0, max(abs(p.x), abs(p.y)));
    if (crop < 0.003) discard;
    float encodedStyle = floor(material.r * 255.0 + 0.5);
    float shape = floor(encodedStyle / 64.0);
    float seed = mod(encodedStyle, 64.0) * 1.3287, erosion = material.g, power = material.b * 1.3;
    bool hand = Hand > 0.5;
    bool charged = shape > 2.5;
    float skin = hand ? roundBox(p, CoreHalf, min(0.18, CoreHalf.y * 0.65)) : 1.0;
    vec2 q = p;
    if (hand) {
        float freeEdge = smoothstep(-0.035, 0.18, skin);
        q -= Bend * freeEdge * (0.30 + 0.70 * smoothstep(-0.55, 0.65, p.y));
        q.x += sin(p.y * 8.0 - Time * 24.0 + seed) * freeEdge * 0.035;
    } else if (!charged) {
        float tip = smoothstep(-0.65, 0.80, p.y);
        q.x -= sin(p.y * 5.8 - Time * 18.0 + seed) * tip * (shape < 0.5 ? 0.15 : 0.055);
    }
    // Exactly two 2D value-noise evaluations, with no texture reads or ray-sampling loop.
    float flow = inkNoise(q * vec2(5.6, 5.2) + vec2(seed, -Time * 13.0));
    float fine = inkNoise(q * vec2(12.3, 10.7) + vec2(-Time * 4.0, -Time * 22.0 + seed));
    float d;
    if (charged) {
        float electricTime = seed + floor(Time * 23.0) * 0.731;
        if (hand) {
            float angle = (atan(q.y / max(CoreHalf.y, 0.15), q.x / max(CoreHalf.x, 0.15)) + 3.14159) * 2.54648;
            float sector = floor(angle);
            float jagged = mix(hash(vec2(sector, electricTime)), hash(vec2(mod(sector + 1.0, 16.0), electricTime)), fract(angle));
            d = roundBox(q, CoreHalf + vec2(0.035), 0.035) - (jagged - 0.25) * 0.16;
            float left = bolt(vec2((q.x + CoreHalf.x + 0.055) * 2.3, q.y), electricTime + 11.0, 0.035);
            float right = bolt(vec2((q.x - CoreHalf.x - 0.055) * 2.3, q.y), electricTime + 29.0, 0.035);
            d = min(d, min(left, right) * 0.65);
            d = min(d, skin - 0.01);
        } else {
            d = bolt(q, electricTime, 0.060 + 0.012 * power);
            vec2 fork = vec2((q.x - 0.23 - q.y * 0.35) * 1.7, q.y - 0.05);
            float branch = max(bolt(fork, electricTime + 17.0, 0.030) * 0.7, -0.18 - q.y);
            d = min(d, branch);
            d = max(d, (fine - 0.55) * 0.25 - (1.0 - erosion) * 0.30);
        }
    } else if (hand) {
        float angle = atan(q.y, q.x);
        float whipping = sin(angle * 5.0 + Time * 19.0 + seed) * 0.055
                       + sin(angle * 9.0 - Time * 29.0 - seed) * 0.031;
        float lick = pow(0.5 + 0.5 * sin(angle * 6.0 + Time * 13.0 + flow * 4.0), 3.0)
                   * 0.13 * smoothstep(-0.35, 0.40, q.y);
        d = roundBox(q, CoreHalf + vec2(0.055), 0.20)
          - whipping - lick - (flow - 0.5) * 0.20 - (fine - 0.5) * 0.055;
        // The thin blue coating stays over the actual arm while only its outer flames drag behind.
        d = min(d, skin - 0.014);
    } else if (shape < 0.5) {
        float t = clamp((q.y + 0.55) / 1.40, 0.0, 1.0);
        float flank = sin(t * 11.0 - Time * 17.0 + seed + sign(q.x) * 2.1);
        float width = (0.46 + 0.11 * flank + 0.10 * sin(t * 3.14159)) * pow(1.0 - t, 0.68) + 0.006;
        float stem = max(abs(q.x) - width, max(-0.55 - q.y, q.y - 0.85));
        float base = (length((q - vec2(0.035 * sin(seed), -0.43)) / vec2(0.45, 0.36)) - 1.0) * 0.36;
        d = min(stem, base) + (flow - 0.5) * 0.23 + (fine - 0.5) * 0.065;
    } else if (shape < 1.5) {
        vec2 center = vec2(sin(seed) * 0.045, cos(seed * 0.7) * 0.04);
        float angle = atan(q.y - center.y, q.x - center.x);
        float ripple = sin(angle * 5.0 - Time * 15.0 + seed) * 0.029;
        d = (length((q - center) / vec2(0.63, 0.64)) - 1.0) * 0.63
          + (flow - 0.5) * 0.15 + (fine - 0.5) * 0.06 - ripple;
    } else {
        vec2 lower = q - vec2(-0.025 + sin(seed) * 0.035, -0.32);
        vec2 upper = q - vec2(0.060 + sin(Time * 12.0 + seed) * 0.040, 0.36);
        float bottom = (length(lower / vec2(0.50, 0.43)) - 1.0) * 0.43;
        float top = (length(upper / vec2(0.33, 0.29)) - 1.0) * 0.29;
        float neck = roundBox(q - vec2(0.02, 0.015), vec2(0.15, 0.20), 0.10);
        d = joinShape(joinShape(bottom, neck, 0.07), top, 0.06)
          + (flow - 0.5) * 0.13 + (fine - 0.5) * 0.055;
    }
    if (!hand && !charged) {
        d = max(d, (fine - 0.49) * 0.45 - (1.0 - erosion) * 0.40);
        d += erosion * 0.055;
    }
    float edgeWidth = 0.075 + fine * 0.045;
    float ink = smoothstep(-edgeWidth - 0.045, -edgeWidth * 0.38, d)
              * (1.0 - smoothstep(-0.007, 0.023, d));
    float inside = 1.0 - smoothstep(-0.075, 0.018, d);
    float rim = 1.0 - smoothstep(0.006, 0.039, abs(d - 0.013));
    float glow = (1.0 - smoothstep(0.012, 0.09, d)) * smoothstep(-0.005, 0.026, d);
    if (charged) {
        vec3 red = vec3(1.32, 0.007, 0.035);
        vec3 color = vec3(0.0015, 0.0003, 0.001) + red * (rim * 1.20 + glow * 0.21);
        float alpha = max(inside * (hand ? 0.75 : 0.96), rim * 0.94 + glow * 0.13);
        alpha *= crop * material.a;
        if (alpha < 0.005) discard;
        fragColor = vec4(color, clamp(alpha, 0.0, 0.97));
        return;
    }
    if (hand) ink *= mix(1.0, 0.18, 1.0 - smoothstep(-0.025, 0.015, skin));
    vec3 cyan = mix(vec3(0.008, 0.37, 0.52), vec3(0.025, 0.83, 1.01), flow);
    cyan += vec3(0.008, 0.065, 0.07) * power;
    if (redFlame) cyan = mix(vec3(.45,.002,.019),vec3(1.08,.018,.058),flow) + vec3(.10,.002,.009)*power;
    vec3 color = mix(cyan, redFlame ? vec3(.003,.0004,.001) : vec3(0.001, 0.005, 0.008), ink * 0.99);
    color += (redFlame ? vec3(1.42,.014,.073) : vec3(0.025, 1.02, 1.20)) * (rim * 1.20 + glow * 0.13);
    float alpha = max(inside * (hand ? 0.26 + flow * 0.10 : 0.45 + flow * 0.18), ink * 0.94);
    alpha = max(alpha, rim * 0.79 + glow * 0.085);
    alpha *= crop * material.a;
    if (alpha < 0.005) discard;
    fragColor = vec4(color, clamp(alpha, 0.0, 0.96));
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

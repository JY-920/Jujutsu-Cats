#version 150
// Full-screen composite for the Rika / Geto finale: depth-aware upsampling, HDR glow, refraction,
// gravitational lensing around the curse vortex, pink scene lighting, grading and flash.
uniform sampler2D SceneColor, SceneDepth, EnergyLayer;
uniform mat4 InverseViewProjection, ViewProjection;
uniform vec3 RikaPos, GetoPos, ContactPos, BurstPos;
uniform float Time, RikaCharge, GetoCharge, RikaBeam, GetoBeam, Contact, Pressure, Burst, Aftermath, Visibility, Seed;
uniform float Distortion, FlashEnabled, Flash, EnergyScale, Grade, GetoScale, ContactTone;
in vec2 texCoord;
in vec4 tint;
out vec4 fragColor;

const vec3 LIGHT = vec3(1.0, .34, .78);
float sq(float x) { return x * x; }
float hash2(vec2 p) { vec3 a = fract(vec3(p.xyx) * .1031); a += dot(a, a.yzx + 33.33); return fract((a.x + a.y) * a.z); }
float noise2(vec2 p) {
    vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash2(i), hash2(i + vec2(1,0)), f.x), mix(hash2(i + vec2(0,1)), hash2(i + vec2(1,1)), f.x), f.y);
}

/** Hue-preserving filmic curve: saturated pink stays pink; only the hottest cores bleach to white. */
vec3 tonemap(vec3 x) {
    x = max(x, 0.0);
    float peak = max(x.r, max(x.g, x.b));
    vec3 hue = x * ((1.0 - exp(-peak * 1.15)) / max(peak, 1e-4));
    float bleach = smoothstep(1.6, 7.0, peak);
    return mix(hue, vec3(1.0, .96, .99) * (1.0 - exp(-peak * .9)), bleach * .85);
}
vec4 energyAt(vec2 uv) { return texture(EnergyLayer, uv); }
vec4 upsample(float depth) {
    vec2 size = vec2(textureSize(EnergyLayer, 0)), p = texCoord * size - .5, base = floor(p), fraction = fract(p);
    vec4 sum = vec4(0); float weights = 0.0;
    for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
        vec2 uv = (base + vec2(x, y) + .5) / size;
        float weight = (x == 0 ? 1.0 - fraction.x : fraction.x) * (y == 0 ? 1.0 - fraction.y : fraction.y);
        weight *= exp(-abs(texture(SceneDepth, uv).r - depth) / (.00012 + (1.0 - depth) * .005)) + .001;
        sum += energyAt(uv) * weight; weights += weight;
    }
    // Four diagonal taps suppress the half-resolution march dither without smearing silhouettes.
    for (int i = 0; i < 4; i++) {
        vec2 uv = texCoord + vec2(i < 2 ? -1.0 : 1.0, (i == 0 || i == 2) ? -1.0 : 1.0) * 1.1 / size;
        float weight = .16 * exp(-abs(texture(SceneDepth, uv).r - depth) / (.00012 + (1.0 - depth) * .005));
        sum += energyAt(uv) * weight; weights += weight;
    }
    return weights > .0001 ? sum / weights : vec4(0);
}
/** Cheap two-ring bloom of the HDR energy layer; light bleeds over occluders like a lens would. */
vec3 bloom() {
    vec2 texel = 1.0 / vec2(textureSize(EnergyLayer, 0));
    vec3 sum = vec3(0);
    for (int i = 0; i < 12; i++) {
        float a = float(i) * 2.399963 + Seed;
        float radius = i < 6 ? 5.0 : 13.0;
        vec4 e = energyAt(texCoord + vec2(cos(a), sin(a)) * texel * radius);
        sum += max(e.rgb - .35, 0.0) * (i < 6 ? .11 : .06);
    }
    return sum;
}
float rayPoint(vec3 ray, vec3 c, out float along) { along = dot(c, ray); return length(c - ray * max(along, 0.0)); }
float raySegment(vec3 ray, vec3 a, vec3 b, out float along) {
    vec3 d = b - a; float bb = dot(ray, d), cc = dot(d, d), dd = dot(ray, -a), ee = dot(d, -a);
    float denominator = max(cc - bb * bb, 1e-5);
    float s = clamp((ee - bb * dd) / denominator, 0.0, 1.0);
    vec3 q = a + d * s; along = max(dot(q, ray), 0.0);
    return length(q - ray * along);
}
vec2 screen(vec3 p) { vec4 c = ViewProjection * vec4(p, 1); return c.w > .001 ? c.xy / c.w * .5 + .5 : vec2(-9.0); }
vec2 shellPush(vec3 ray, vec3 center, float radius, float worldDepth, float strength) {
    float along = dot(ray, center), disc = along * along + radius * radius - dot(center, center);
    if (disc <= 0.0 || strength <= 0.0) return vec2(0);
    float root = sqrt(disc), hit = along - root > .05 ? along - root : along + root;
    if (hit <= .05 || hit > worldDepth) return vec2(0);
    vec3 normal = normalize(ray * hit - center);
    vec2 a = screen(ray * hit), b = screen(ray * hit + normal * .3);
    if (a.x < -8.0 || b.x < -8.0) return vec2(0);
    vec2 direction = b - a;
    float rim = pow(max(0.0, 1.0 - abs(dot(normal, ray))), 2.0);
    return direction / max(length(direction), .0001) * strength * (.25 + .75 * rim);
}

void vfxMaterialMain() {
    float depth = texture(SceneDepth, texCoord).r;
    vec2 size = vec2(textureSize(SceneColor, 0)), border = .5 / size;
    vec4 farPoint = InverseViewProjection * vec4(texCoord * 2.0 - 1.0, 1, 1);
    vec3 ray = normalize(farPoint.xyz * (farPoint.w < 0.0 ? -1.0 : 1.0));
    float worldDepth = 100000.0; vec3 surface = ray * worldDepth;
    if (depth < .999999) {
        vec4 p = InverseViewProjection * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1);
        surface = p.xyz / p.w; worldDepth = dot(surface, ray);
    }
    float aspect = size.x / size.y;
    vec3 rikaFront = mix(RikaPos, ContactPos, RikaBeam);
    float contactRadius = (.95 + 1.05 * Pressure) * Contact * GetoScale, splash = (3.6 + 5.0 * Pressure) * Contact * GetoScale;
    float getoRadius = (.75 + 2.35 * GetoCharge) * GetoScale;
    float burstRadius = Burst <= 0.0 ? 0.0 : 1.2 + 7.8 * (1.0 - sq(sq(1.0 - min(Burst, 1.0))));

    // ---- refraction ---------------------------------------------------------------------------
    vec2 push = vec2(0);
    for (int k = 0; k < 3; k++) {
        float phase = fract(Time * 1.55 + float(k) / 3.0);
        push += shellPush(ray, ContactPos, contactRadius * .55 + phase * splash * 1.1, worldDepth, Contact * (1.0 - phase) * .014);
    }
    push += shellPush(ray, BurstPos, burstRadius, worldDepth, Burst > 0.0 ? (1.0 - min(Burst, 1.0)) * .032 : 0.0);
    float along;
    if (RikaBeam > .001) {
        float d = raySegment(ray, RikaPos, rikaFront, along);
        float haze = RikaBeam * exp(-sq(d / 1.6)) * step(along, worldDepth);
        push += (vec2(noise2(texCoord * 60.0 + vec2(Time * 4.0, 0)), noise2(texCoord * 60.0 + vec2(7.0, -Time * 5.0))) - .5) * haze * .010;
    }
    // Light bends around the compressed curses: the background is pulled toward the vortex.
    vec2 lens = vec2(0);
    float getoDistance = rayPoint(ray, GetoPos, along);
    if (GetoCharge > .001 && along > 0.0 && along < worldDepth + getoRadius) {
        vec2 centre = screen(GetoPos);
        if (centre.x > -8.0) {
            vec2 offset = (texCoord - centre) * vec2(aspect, 1.0);
            float pull = GetoCharge * .55 * exp(-sq(getoDistance / (getoRadius * 2.1)));
            lens = -(offset / max(length(offset), .0001)) * pull * min(length(offset), .08) / vec2(aspect, 1.0);
        }
    }
    float vis = clamp(Visibility, 0.0, 1.0);
    vec2 shift = (push + lens) * Distortion * vis;
    vec2 warped = clamp(texCoord + shift, border, 1.0 - border);
    if (abs(texture(SceneDepth, warped).r - depth) > .0015 && length(lens) < .0001) warped = mix(texCoord, warped, .08);
    float chroma = clamp(length(shift) * 9.0, 0.0, 1.0) * Distortion;
    vec2 split = shift * .45 * chroma;
    vec3 background = vec3(texture(SceneColor, clamp(warped + split, border, 1.0 - border)).r,
            texture(SceneColor, warped).g, texture(SceneColor, clamp(warped - split, border, 1.0 - border)).b);

    // ---- scene lighting -------------------------------------------------------------------------
    if (depth < .999999) {
        vec3 normal = normalize(cross(dFdx(surface), dFdy(surface)));
        if (dot(normal, ray) > 0.0) normal = -normal;
        float h;
        vec3 toContact = ContactPos - surface; float dc = length(toContact);
        float intensity = Contact * (5.5 + 4.0 * Pressure) / (1.0 + dc * dc * .045) * (.35 + .65 * max(0.0, dot(normal, toContact / max(dc, .001))));
        vec3 toRika = RikaPos - surface; float dr = length(toRika);
        intensity += RikaCharge * 2.2 / (1.0 + dr * dr * .09) * (.35 + .65 * max(0.0, dot(normal, toRika / max(dr, .001))));
        if (RikaBeam > .001) {
            vec3 pa = surface - RikaPos, ba = rikaFront - RikaPos;
            h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-5), 0.0, 1.0);
            float db = length(pa - ba * h);
            intensity += RikaBeam * 1.6 / (1.0 + db * db * .12);
        }
        if (Burst > 0.0) {
            vec3 toBurst = BurstPos - surface; float db = length(toBurst);
            intensity += (1.0 - min(Burst, 1.0)) * 7.0 / (1.0 + db * db * .02) * (.4 + .6 * max(0.0, dot(normal, toBurst / max(db, .001))));
            // The expanding shell scorches a bright ring across the terrain.
            float crossing = exp(-sq((db - burstRadius) / max(.25, burstRadius * .035)));
            background += vec3(1.0, .42, .85) * crossing * (1.0 - min(Burst, 1.0)) * 1.4;
        }
        if (Contact > .001) {
            float crossing = 0.0;
            for (int k = 0; k < 3; k++) {
                float phase = fract(Time * 1.55 + float(k) / 3.0);
                crossing += exp(-sq((dc - (contactRadius * .55 + phase * splash * 1.1)) / .3)) * (1.0 - phase);
            }
            background += mix(vec3(1.0, .40, .85), vec3(.40, .08, .55), ContactTone) * crossing * Contact * .35;
        }
        intensity *= vis;
        vec3 tone = mix(LIGHT, vec3(.45, .10, .65), ContactTone);
        background += background * tone * intensity * .38 * (1.0 - .8 * ContactTone) + tone * intensity * mix(.012, .03, ContactTone);
        // The curse vortex drinks the light around it.
        float absorb = GetoCharge * .65 * exp(-sq(length(surface - GetoPos) / (getoRadius * 2.4)));
        background *= 1.0 - absorb * vis;
    }

    // ---- cinematic grade ------------------------------------------------------------------------
    float luma = dot(background, vec3(.2126, .7152, .0722));
    vec2 centred = texCoord - .5;
    float vignette = smoothstep(.25, .85, length(centred * vec2(aspect, 1.0)) / aspect * 1.4);
    background = mix(background, vec3(luma) * vec3(.92, .86, 1.0), Grade * .45);
    background *= 1.0 - Grade * (.22 + .40 * vignette);

    // ---- energy ---------------------------------------------------------------------------------
    vec4 energy = upsample(depth);
    vec3 light = energy.rgb * EnergyScale + bloom() * EnergyScale;
    float contactAlong, rikaAlong, beamAlong, burstAlong;
    float dc = rayPoint(ray, ContactPos, contactAlong);
    float glow = Contact * (.45 + .55 * Pressure) / (1.0 + sq(dc / max(.35, contactRadius * .30)));
    glow *= contactAlong < worldDepth ? 1.0 : .30;
    float dr = rayPoint(ray, RikaPos, rikaAlong);
    glow += RikaCharge * .35 / (1.0 + sq(dr / .6)) * (rikaAlong < worldDepth ? 1.0 : .3);
    if (RikaBeam > .001) {
        float db = raySegment(ray, RikaPos, rikaFront, beamAlong);
        glow += RikaBeam * .30 / (1.0 + sq(db / .55)) * (beamAlong < worldDepth ? 1.0 : .3);
    }
    if (Burst > 0.0) {
        float db = rayPoint(ray, BurstPos, burstAlong);
        glow += (1.0 - min(Burst, 1.0)) * .9 / (1.0 + sq(db / max(.8, burstRadius * .25))) * (burstAlong < worldDepth + burstRadius ? 1.0 : .3);
    }
    light += mix(vec3(1.0, .30, .74), vec3(.55, .08, .62), ContactTone) * glow * vis * (1.0 - .5 * ContactTone);
    vec3 mapped = tonemap(light);
    vec3 result = background * (1.0 - energy.a) + mapped;
    float flash = clamp(Flash * FlashEnabled, 0.0, 1.0);
    result = mix(result, vec3(1.0, .93, .98), flash * .62) + vec3(.35, .10, .26) * flash;
    fragColor = vec4(result, 1.0) * tint;
}

void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) discard;
}

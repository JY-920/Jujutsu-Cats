#version 150
// Rika / Geto finale: one camera-relative emission-absorption volume (Blue/Purple pipeline).
// Rika's side is emissive plasma; Geto's side is absorbing curse smoke that is lit by the pink light.
uniform sampler2D SceneDepth;
uniform mat4 InverseViewProjection;
uniform vec3 RikaPos, GetoPos, ContactPos, BurstPos;
uniform float Time, RikaCharge, GetoCharge, RikaBeam, GetoBeam, Contact, Pressure, Burst, Aftermath, Visibility, Seed, EnergyScale;
// GetoScale shrinks Geto's vortex for the short ranged blast; ContactTone 1 turns the impact into a curse burst.
uniform float GetoScale, ContactTone;
uniform float BeamPathCount, BeamPathLength;
uniform vec3 BeamNode0;
uniform vec3 BeamNode1;
uniform vec3 BeamNode2;
uniform vec3 BeamNode3;
uniform vec3 BeamNode4;
uniform vec3 BeamNode5;
uniform vec3 BeamNode6;
uniform vec3 BeamNode7;
uniform vec3 BeamNode8;
in vec2 texCoord;
in vec4 tint;
out vec4 fragColor;

const vec3 MAGENTA = vec3(1.55, .055, .95);
const vec3 PINK = vec3(2.9, .42, 1.95);
const vec3 HOT = vec3(6.4, 4.5, 5.9);
const vec3 LIGHT = vec3(1.0, .34, .78);

float hash3(vec3 p) {
    p = fract(p * .1031); p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}
float noise(vec3 p) {
    vec3 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash3(i), hash3(i + vec3(1,0,0)), f.x), mix(hash3(i + vec3(0,1,0)), hash3(i + vec3(1,1,0)), f.x), f.y),
               mix(mix(hash3(i + vec3(0,0,1)), hash3(i + vec3(1,0,1)), f.x), mix(hash3(i + vec3(0,1,1)), hash3(i + vec3(1,1,1)), f.x), f.y), f.z);
}
float fbm(vec3 p) {
    float sum = 0.0, amp = .5;
    for (int i = 0; i < 3; i++) { sum += amp * noise(p); p = p * 2.07 + vec3(1.7, 9.2, 4.1); amp *= .5; }
    return sum / .875;
}
float ridge(vec3 p) { return 1.0 - abs(noise(p) * 2.0 - 1.0); }
float sq(float x) { return x * x; }
vec3 perpendicular(vec3 axis) { return normalize(cross(axis, abs(axis.y) < .95 ? vec3(0,1,0) : vec3(1,0,0))); }

float segmentDistance(vec3 p, vec3 a, vec3 b, out float h) {
    vec3 pa = p - a, ba = b - a;
    h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-5), 0.0, 1.0);
    return length(pa - ba * h);
}

vec3 beamNode(int i) {
    if (i == 0) return BeamNode0;
    if (i == 1) return BeamNode1;
    if (i == 2) return BeamNode2;
    if (i == 3) return BeamNode3;
    if (i == 4) return BeamNode4;
    if (i == 5) return BeamNode5;
    if (i == 6) return BeamNode6;
    if (i == 7) return BeamNode7;
    return BeamNode8;
}

// Eight bounded spans are shared with the server's collision and damage path.
float beamPathDistance(vec3 p, out float along, out vec3 axis, out vec3 radial) {
    float best = 1e10, walked = 0.0;
    along = 0.0; axis = vec3(0,0,1); radial = p - BeamNode0;
    for (int i = 0; i < 8; i++) {
        if (float(i + 1) >= BeamPathCount) break;
        vec3 a = beamNode(i), d = beamNode(i + 1) - a;
        float span = length(d); if (span < 1e-5) continue;
        vec3 dir = d / span; float t = dot(p - a, dir);
        float at = clamp(t, 0.0, span); vec3 off = p - a - dir * at;
        float dist = dot(off, off);
        if (dist < best) {
            best = dist; axis = dir; along = walked + at; radial = off;
            // Keep the original beam's soft axial envelopes at the two ends.
            if ((i == 0 && t < 0.0) || (float(i + 2) >= BeamPathCount && t > span)) {
                along = walked + t; radial = p - a - dir * t;
            }
        }
        walked += span;
    }
    return sqrt(max(best, 0.0));
}

float curveDistance, curveAlong; vec3 curveAxis, curveRadial;

float rikaRadius() { return .45 + 1.45 * RikaCharge; }
float getoRadius() { return (.75 + 2.35 * GetoCharge) * GetoScale; }
float contactRadius() { return (.95 + 1.05 * Pressure) * Contact * GetoScale; }
float splashRadius() { return (3.6 + 5.0 * Pressure) * Contact * GetoScale; }
float burstRadius() { return Burst <= 0.0 ? 0.0 : 1.2 + 7.8 * (1.0 - sq(sq(1.0 - min(Burst, 1.0)))); }
vec3 rikaFront() { return mix(RikaPos, ContactPos, RikaBeam); }
vec3 getoFront() { return mix(GetoPos, ContactPos, GetoBeam); }

/** Pink light from every emitter, used to light the curse smoke and the contact debris. */
vec3 pinkLight(vec3 p) {
    float h;
    float intensity = Contact * (7.0 + 5.0 * Pressure) / (1.0 + dot(p - ContactPos, p - ContactPos) * .30);
    intensity += RikaCharge * 2.2 / (1.0 + dot(p - RikaPos, p - RikaPos) * .55);
    if (RikaBeam > .001) intensity += RikaBeam * 1.6 / (1.0 + sq(BeamPathCount > 1.0 ? curveDistance : segmentDistance(p, RikaPos, rikaFront(), h)) * 1.4);
    if (Burst > 0.0) intensity += (1.0 - Burst) * 9.0 / (1.0 + dot(p - BurstPos, p - BurstPos) * .08);
    return mix(LIGHT, vec3(.50, .12, .72), ContactTone) * intensity * (1.0 - .55 * ContactTone);
}

/** Conservative distance to every active volume; the march skips empty space with it. */
float bound(vec3 p) {
    float h, d = 1e5;
    if (RikaCharge > .001) d = min(d, length(p - RikaPos) - rikaRadius() * 1.6);
    if (GetoCharge > .001) d = min(d, length(p - GetoPos) - getoRadius() * 1.7);
    if (RikaBeam > .001) d = min(d, (BeamPathCount > 1.0 ? curveDistance : segmentDistance(p, RikaPos, rikaFront(), h)) - 2.8);
    if (GetoBeam > .001) d = min(d, (BeamPathCount > 1.0 ? curveDistance : segmentDistance(p, GetoPos, getoFront(), h)) - 2.8);
    if (Contact > .001) {
        vec3 q = p - ContactPos; vec3 axis = normalize(GetoPos - RikaPos);
        float axial = abs(dot(q, axis)), radial = length(q - axis * dot(q, axis));
        d = min(d, length(q) - contactRadius() * 2.6);
        d = min(d, max(radial - splashRadius() * 1.05, axial - 1.6));
    }
    if (Burst > 0.0) d = min(d, length(p - BurstPos) - burstRadius() * 1.3);
    if (Aftermath > .001) d = min(d, length(p - BurstPos) - 8.0);
    return d;
}

void rikaOrb(vec3 p, inout vec3 E, inout float S) {
    if (RikaCharge <= .001) return;
    vec3 q = p - RikaPos; float r = length(q), R = rikaRadius();
    if (r > R * 1.6) return;
    vec3 dir = q / max(r, 1e-4);
    float n = fbm(q * 2.4 / R + vec3(Seed, -Time * 1.8, Time * .9));
    float flow = fbm(dir * 3.0 + vec3(Time * 1.7, Seed, -Time * 1.1) + r / R * 1.5);
    // Crisp white-hot heart, a turbulent magenta mantle and arcs crawling over its surface.
    float heart = smoothstep(.30, .22, r / R + (n - .5) * .06);
    float mantle = smoothstep(.72, .30, r / R + (n - .5) * .45) * smoothstep(.35, .75, flow) * (1.0 - heart);
    float skin = exp(-sq((r / R - .31 - (n - .5) * .10) / .045));
    float arcs = pow(ridge(dir * 4.5 + vec3(Time * 2.6, -Time * 1.9, Seed)), 34.0) * exp(-sq((r / R - .45) / .2));
    float spikes = pow(noise(dir * 6.0 + vec3(Time * .7, 0, Seed)), 6.0) * 2.4;
    float rays = spikes * exp(-r / max(R * (.25 + .55 * spikes), .05)) * smoothstep(R * .25, R * .45, r);
    E += HOT * heart * 1.6 + MAGENTA * mantle * 1.2 + vec3(3.6, 1.2, 3.0) * skin * 1.1 + vec3(4.2, 2.2, 4.0) * arcs * 2.2 + PINK * rays * .35;
    S += heart * 6.0 + mantle * .9 + skin * .6 + rays * .03;
}

void rikaBeam(vec3 p, inout vec3 E, inout float S) {
    if (RikaBeam <= .001) return;
    vec3 ab = ContactPos - RikaPos; float len = length(ab);
    if (BeamPathCount > 1.0) len = BeamPathLength;
    if (len < .05) return;
    vec3 axis = normalize(ab), q = p - RikaPos; float t = dot(q, axis), front = len * RikaBeam;
    vec3 rad = q - axis * t;
    if (BeamPathCount > 1.0) { axis = curveAxis; t = curveAlong; rad = curveRadial; front = len; }
    if (t < -1.0 || t > front + 1.5) return;
    float rho = length(rad);
    float W = (.68 + .30 * Pressure) * (1.0 + .7 * exp(-sq((len - t) / 2.0)) * Contact) * (.7 + .3 * smoothstep(0.0, 3.0, t));
    if (rho > W * 2.0) return;
    vec3 u = perpendicular(axis), v = cross(axis, u);
    float angle = atan(dot(rad, v), dot(rad, u));
    float twist = angle + t * .38 - Time * 3.2;
    vec2 ring = vec2(cos(twist), sin(twist)) * rho / W;
    float n = fbm(vec3(ring * 2.6, t * .6 - Time * 11.0));
    float envelope = smoothstep(-.4, .5, t) * (1.0 - smoothstep(front - .5, front + .8, t));
    // Torn sheath: thresholded turbulence leaves dark gaps between bright flow ribbons.
    float sheath = smoothstep(.48, .78, n + .42 - rho / W * .55) * smoothstep(1.25, .6, rho / W);
    float core = exp(-sq(rho / (W * .16))) * (.75 + .5 * noise(vec3(t * 3.1 - Time * 46.0, Seed, 0)));
    float strands = 0.0;
    for (int k = 0; k < 3; k++) {
        float phase = float(k) * 2.094 + t * (1.4 + .3 * float(k)) - Time * (16.0 + 3.0 * float(k));
        vec2 centre = vec2(cos(phase), sin(phase)) * W * (.55 + .12 * float(k));
        vec2 local = vec2(dot(rad, u), dot(rad, v));
        strands += exp(-sq(length(local - centre) / (W * .07)));
    }
    float sparks = pow(noise(vec3(ring * 5.0, t * 1.4 - Time * 22.0)), 28.0) * 16.0 * smoothstep(1.5, .5, rho / W);
    // Energy pulses race toward the contact as bright travelling collars.
    float pulse = pow(.5 + .5 * sin(t * 1.15 - Time * 27.0), 14.0);
    float collar = pulse * exp(-sq((rho / W - .75 - (n - .5) * .3) / .16));
    E += envelope * (MAGENTA * sheath * (1.6 + 2.2 * pulse) + HOT * core * (2.6 + 1.5 * pulse) + vec3(3.4, 1.3, 3.1) * strands * 1.5
        + vec3(3.6, 1.6, 3.3) * collar * 1.6 + vec3(3.0, 2.2, 3.0) * sparks);
    S += envelope * (sheath * .55 + core * 1.6 + strands * .2 + collar * .3);
}

vec3 ash(float ropes, float n) { return mix(vec3(.012, .009, .015), vec3(.42, .36, .43), pow(clamp(ropes * .6 + n * .4, 0.0, 1.0), 2.2)); }

void getoOrb(vec3 p, vec3 lit, inout vec3 E, inout float S) {
    if (GetoCharge <= .001) return;
    vec3 q = p - GetoPos; float r = length(q), R = getoRadius();
    if (r > R * 1.7) return;
    vec3 axis = normalize(ContactPos - GetoPos), u = perpendicular(axis), v = cross(axis, u);
    float h = dot(q, axis); vec2 d = vec2(dot(q, u), dot(q, v)); float rho = length(d), angle = atan(d.y, d.x);
    float n = fbm(vec3(d * 2.6 / R, h / R * 2.5) + vec3(Time * .35, Seed, -Time * .5));
    // Maximum: a whirlpool funnel opening backwards, its spiral lanes winding into a black eye.
    float lane = sin(angle * 4.0 + log(rho / R + .08) * 7.0 - Time * 5.5 + n * 2.6);
    float bands = smoothstep(-.1, .8, lane);
    float lines = exp(-sq((lane - .96) / .07));
    float funnel = -.55 * (rho - .18 * R);
    float sheet = exp(-sq((h - funnel) / (R * (.16 + .10 * n) + rho * .08)));
    float extent = smoothstep(1.0, .80, rho / R + (n - .5) * .35);
    float ball = smoothstep(.42, .22, length(vec3(d, h * 1.15)) / R + (n - .5) * .25);
    float density = (sheet * extent * (.20 + bands) + ball * (.6 + .6 * bands)) * smoothstep(.15, .55, n + .2);
    float eye = exp(-sq(r / (R * .17)));
    float glints = pow(ridge(vec3(d * 3.0 / R, h / R) + vec3(Time * .9, -Time * 1.2, Seed)), 48.0) * density;
    vec3 albedo = ash(bands, n);
    E += density * albedo * (.18 + lit * .55) + vec3(.70, .64, .76) * lines * sheet * extent * (1.1 + lit * .4)
       + vec3(2.4, .07, .2) * glints * .5 + vec3(.32, .06, .62) * eye * .25;
    S += density * 4.2 + eye * 10.0 + lines * sheet * extent * .5;
}

void getoBeam(vec3 p, vec3 lit, inout vec3 E, inout float S) {
    if (GetoBeam <= .001) return;
    vec3 ab = ContactPos - GetoPos; float len = length(ab);
    if (BeamPathCount > 1.0) len = BeamPathLength;
    if (len < .05) return;
    vec3 axis = normalize(ab), q = p - GetoPos; float t = dot(q, axis), front = len * GetoBeam;
    vec3 rad = q - axis * t;
    if (BeamPathCount > 1.0) { axis = curveAxis; t = curveAlong; rad = curveRadial; front = len; }
    if (t < -1.0 || t > front + 1.2) return;
    float rho = length(rad);
    float W = (.95 + .35 * Pressure) * (1.0 + .45 * exp(-sq((len - t) / 2.0)) * Contact) * GetoScale;
    if (rho > W * 1.8) return;
    vec3 u = perpendicular(axis), v = cross(axis, u);
    float angle = atan(dot(rad, v), dot(rad, u));
    float twist = angle - t * 1.25 + Time * 7.5;
    float ropes = smoothstep(.2, .85, .5 + .5 * sin(twist * 5.0));
    vec2 ring = vec2(cos(twist), sin(twist)) * rho / W;
    float n = fbm(vec3(ring * 2.3, t * .6 + Time * 3.4));
    float envelope = smoothstep(-.5, .5, t) * (1.0 - smoothstep(front - .4, front + .7, t));
    float shape = smoothstep(1.0, .40, rho / W + (n - .5) * .7) * envelope;
    float density = shape * (.20 + ropes * 1.2) * smoothstep(.2, .65, n + .1);
    float hollow = exp(-sq(rho / (W * .22))) * shape;
    float slit = pow(ridge(vec3(ring * 4.0, t * 1.1 + Time * 6.5)), 40.0) * shape * ropes;
    float lines = exp(-sq((sin(twist * 5.0) - .93) / .08)) * shape;
    // Violet light leaks between the ropes; dark collars race along; a crimson spearhead leads.
    float leak = exp(-sq(rho / (W * .55))) * (1.0 - ropes) * shape;
    float collar = pow(.5 + .5 * sin(t * 1.3 - Time * 24.0), 12.0) * exp(-sq((rho / W - .85) / .14)) * envelope;
    float head = exp(-sq((t - front) / (W * 1.1))) * exp(-sq(rho / (W * .9))) * step(front, len - .3);
    E += density * ash(ropes, n) * (.14 + lit * .55) + vec3(.80, .72, .88) * lines * (1.25 + lit * .35)
       + vec3(2.6, .08, .25) * slit * .6 + vec3(.55, .10, 1.05) * (hollow * .55 + leak * .9)
       + vec3(.45, .30, .55) * collar * 1.2 + vec3(2.2, .25, .55) * head * 1.6;
    S += density * 3.2 + lines * .4 + collar * 1.4 + head * .8;
}

void contact(vec3 p, vec3 lit, inout vec3 E, inout float S) {
    if (Contact <= .001) return;
    vec3 q = p - ContactPos; float r = length(q), Rc = contactRadius(), Rd = splashRadius();
    if (r > max(Rc * 2.6, Rd * 1.1)) return;
    vec3 dir = q / max(r, 1e-4);
    vec3 axis = normalize(GetoPos - RikaPos), u = perpendicular(axis), v = cross(axis, u);
    float h = dot(q, axis); vec3 rad = q - axis * h; float rho = length(rad);
    float angle = atan(dot(rad, v), dot(rad, u));
    float n = fbm(q * 2.2 / max(Rc, .2) + vec3(Time * 2.6, -Time * 2.0, Time * 1.6 + Seed));
    // Pink on Rika's side, curse matter on Geto's side, with a torn interface between them.
    float side = mix(smoothstep(-.3, .3, h / Rc + (n - .5) * 1.6 + (Pressure - .5) * .3), 1.0, ContactTone);
    float ball = smoothstep(.62, .92, n + .55 - r / Rc * .75) * smoothstep(1.5, .7, r / Rc);
    float core = exp(-sq(r / (Rc * .16 * (1.0 + .2 * sin(Time * 41.0)))));
    // Spiky star burst: direction-only noise defines how far each ray reaches.
    float spike = pow(noise(dir * 8.0 + vec3(Seed, Time * 3.0, 0)), 5.0) * 2.2 + pow(noise(dir * 15.0 - Time * 5.0), 9.0) * 1.4;
    float rays = spike * exp(-r / max(Rc * (.35 + 1.4 * spike), .05)) * smoothstep(Rc * .1, Rc * .35, r);
    // A thin sheet of plasma sprays out perpendicular to the beams.
    float disk = exp(-sq(h / (.10 + .07 * rho))) * smoothstep(Rd, Rd * .25, rho) * smoothstep(Rc * .2, Rc * .8, rho);
    float streak = smoothstep(.55, .95, noise(vec3(angle * 7.0 + Seed, rho * .8 - Time * 16.0, Time)));
    float splash = disk * streak;
    float shells = 0.0;
    for (int k = 0; k < 3; k++) {
        float phase = fract(Time * 1.55 + float(k) / 3.0);
        float radius = Rc * .55 + phase * Rd * 1.1;
        shells += exp(-sq((r - radius) / (.05 + .12 * phase))) * (1.0 - phase) * smoothstep(.45, .75, n) * smoothstep(.85, .2, abs(h) / max(radius, .1));
    }
    float stars = pow(noise(q * 7.0 + Time * 3.5), 32.0) * 20.0 * smoothstep(Rc * 2.4, Rc * .5, r);
    float arcs = pow(ridge(q * 2.4 / max(Rc, .2) + vec3(Time * 6.0, -Time * 4.5, Seed)), 30.0) * smoothstep(Rc * 2.0, Rc * .3, r);
    vec3 plasma = MAGENTA * 1.1 + PINK * .35 * n;
    // Curse tone: a crimson-cored burst of ash instead of Rika's pink plasma.
    vec3 hot = mix(HOT * 4.0, vec3(2.6, .22, .40), ContactTone), spray = mix(vec3(3.2, 1.3, 2.9) * 1.1, vec3(.30, .22, .30) * (.4 + lit), ContactTone);
    vec3 shellTint = mix(vec3(2.2, .6, 1.8) * .9, vec3(.9, .10, .22), ContactTone), sparkTint = mix(vec3(3.2, 2.5, 3.2), vec3(2.4, .25, .35), ContactTone);
    E += ball * (1.0 - side) * plasma + core * hot + rays * mix(PINK * .9 * (1.0 - side * .6), vec3(.35, .28, .36) * (.5 + lit), ContactTone)
       + splash * spray + shells * shellTint
       + sparkTint * stars + mix(vec3(4.0, 2.0, 3.8), vec3(2.8, .2, .4), ContactTone) * arcs * 1.6;
    float dark = ball * side * 1.4;
    E += dark * ash(n, n) * (.12 + lit * .5);
    S += ball * (1.0 - side) * .7 + dark * 3.4 + core * 3.0 + rays * mix(.04, 1.2, ContactTone) + splash * ContactTone * 1.8;
}

void burst(vec3 p, inout vec3 E, inout float S) {
    if (Burst <= 0.0) return;
    vec3 q = p - BurstPos; float r = length(q), R = burstRadius(), b = min(Burst, 1.0);
    if (r > R * 1.3) return;
    vec3 dir = q / max(r, 1e-4);
    float n = fbm(q * 2.8 / R + vec3(0, -Time * .9, Seed));
    float fade = 1.0 - smoothstep(.35, 1.0, b);
    // A torn shell of plasma with dark gaps, not a solid ball.
    float shellBand = exp(-sq((r / R - .80 - (n - .5) * .25) / (.09 + .10 * b)));
    float torn = smoothstep(.52, .85, n + .05);
    float shell = shellBand * torn * fade;
    float inner = smoothstep(.95, .3, r / R) * smoothstep(.70, .95, n) * fade * .2;
    float hot = exp(-b * 9.0) * exp(-sq(r / (R * .30)));
    float spike = pow(noise(dir * 8.0 + Seed), 6.0) * 3.0;
    float rays = spike * smoothstep(1.2, .25, r / R) * smoothstep(.05, .35, r / R) * fade * 1.6;
    float stars = pow(noise(q * 5.0 - Time * 2.0), 32.0) * 16.0 * smoothstep(1.2, .3, r / R) * fade;
    E += shell * mix(MAGENTA * .8, PINK * 1.0, n) + inner * MAGENTA * .6 + rays * PINK * .7 + hot * HOT * 4.0 + vec3(3.2, 2.2, 3.0) * stars;
    S += shell * .9 + inner * .35 + hot * 1.5;
}

void aftermath(vec3 p, inout vec3 E, inout float S) {
    if (Aftermath <= .001) return;
    vec3 q = p - BurstPos; float r = length(q);
    if (r > 8.0) return;
    float n = fbm(q * .55 + vec3(0, Time * .25, Seed));
    float haze = smoothstep(8.0, 2.0, r) * smoothstep(.55, .85, n) * Aftermath;
    float embers = pow(noise(q * 2.2 + vec3(0, Time * 1.3, 0)), 34.0) * 9.0 * Aftermath * smoothstep(8.0, 3.0, r);
    E += MAGENTA * haze * .35 + vec3(3.0, 1.2, 2.6) * embers;
    S += haze * .10;
}

void vfxMaterialMain() {
    fragColor = vec4(0);
    if (Visibility <= .001) return;
    vec4 farPoint = InverseViewProjection * vec4(texCoord * 2.0 - 1.0, 1, 1);
    vec3 ray = normalize(farPoint.xyz * (farPoint.w < 0.0 ? -1.0 : 1.0));
    float limit = 160.0;
    float depth = texture(SceneDepth, texCoord).r;
    if (depth < .999999) {
        vec4 scene = InverseViewProjection * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1);
        limit = min(limit, dot(scene.xyz / scene.w, ray));
    }
    float jitter = hash3(vec3(gl_FragCoord.xy, Time * 7.0));
    float t = .05 + jitter * .085, T = 1.0;
    vec3 color = vec3(0);
    for (int i = 0; i < 112; i++) {
        if (t > limit || T < .015) break;
        vec3 p = ray * t;
        if (BeamPathCount > 1.0) curveDistance = beamPathDistance(p, curveAlong, curveAxis, curveRadial);
        float d = bound(p);
        if (d > .45) { t += d * .9; continue; }
        float dt = .085 + .007 * t;
        vec3 E = vec3(0); float S = 0.0;
        vec3 lit = (GetoCharge > .001 || GetoBeam > .001 || Contact > .001) ? pinkLight(p) : vec3(0);
        rikaOrb(p, E, S); rikaBeam(p, E, S);
        getoOrb(p, lit, E, S); getoBeam(p, lit, E, S);
        contact(p, lit, E, S); burst(p, E, S); aftermath(p, E, S);
        if (BeamPathCount > 1.0 && length(BeamNode0) < 2.3) {
            float clearance = smoothstep(2.4, 4.2, length(p)); E *= clearance; S *= clearance;
        }
        float span = min(dt, limit - t);
        float a = exp(-S * span);
        color += T * E * (S > 1e-3 ? (1.0 - a) / S : span);
        T *= a;
        t += dt;
    }
    fragColor = vec4(color * Visibility / EnergyScale, (1.0 - T) * Visibility) * tint;
}

void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) fragColor = vec4(0.0);
}

#version 150
in vec3 Position;
in vec4 Color;
in vec2 UV0;
uniform mat4 ViewProjection;
out vec4 tint;
out vec2 uv;
out vec3 surfacePosition;
flat out int materialKind;
flat out float materialTime;
void main() {
    gl_Position = ViewProjection * vec4(Position, 1.0);
    tint = Color;
    materialKind = int(floor((UV0.y + .0001) * .5));
    float ageCell = floor(UV0.x * .5);
    materialTime = ageCell * .00625;
    // Decode before interpolation: large phase-age coordinates cannot quantize fine filaments.
    uv = materialKind >= 9 ? vec2(UV0.x - ageCell * 2.0, UV0.y) : UV0;
    surfacePosition = Position;
}

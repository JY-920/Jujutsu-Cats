#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ViewProjection;
out vec2 uv;
out vec4 tint;
out vec3 relativePosition;
void main() {
    uv = UV0;
    tint = Color;
    relativePosition = Position;
    gl_Position = ViewProjection * vec4(Position, 1.0);
}

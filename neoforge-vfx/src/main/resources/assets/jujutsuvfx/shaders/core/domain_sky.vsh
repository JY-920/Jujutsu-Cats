#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
out vec2 texCoord;
out vec4 tint;
void main() {
    // Rasterize at the far plane: normal depth testing preserves terrain and entities.
    gl_Position = vec4(Position.xy, 1.0, 1.0);
    texCoord = UV0;
    tint = Color;
}

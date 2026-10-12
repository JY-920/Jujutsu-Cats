#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
out vec2 texCoord;
out vec4 tint;
void main() {
    // Hand presentation is already projected. Never use the GUI matrix or sky depth.
    gl_Position = vec4(Position, 1.0);
    texCoord = UV0;
    tint = Color;
}

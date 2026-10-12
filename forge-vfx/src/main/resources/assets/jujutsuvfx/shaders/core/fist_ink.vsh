#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
out vec2 uv;
flat out vec4 material;
void main() {
    // CPU-projected 2D cards also retain the correct projection in the deferred Iris hand pass.
    gl_Position = vec4(Position, 1.0);
    uv = UV0;
    material = Color;
}

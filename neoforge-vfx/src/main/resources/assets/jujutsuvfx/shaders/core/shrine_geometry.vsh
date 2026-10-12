#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 RiftView;
uniform mat4 RiftProjection;
uniform mat4 ModelToCamera;
out vec3 modelPosition;
out vec4 vertexTint;
out float material;
void main() {
    gl_Position = RiftProjection * RiftView * ModelToCamera * vec4(Position, 1.0);
    modelPosition = Position;
    vertexTint = Color;
    material = UV0.x;
}

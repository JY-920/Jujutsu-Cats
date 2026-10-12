#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 RiftView;
uniform mat4 RiftProjection;
out vec2 localUv;
out vec4 tint;
out vec3 cameraRelative;

void main() {
    gl_Position = RiftProjection * RiftView * vec4(Position, 1.0);
    localUv = UV0;
    tint = Color;
    cameraRelative = Position;
}

#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 RiftView;
uniform mat4 RiftProjection;
out vec2 uv;
out vec3 cameraRelative;
out vec4 animation;
void main(){
    gl_Position=RiftProjection*RiftView*vec4(Position,1.0);
    uv=UV0;cameraRelative=Position;animation=Color;
}

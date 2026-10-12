#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 RiftView;
uniform mat4 RiftProjection;
uniform vec3 Offset;
out vec3 waterPosition;
out vec3 cameraRelative;
out vec4 projected;
void main(){
    waterPosition=Position;
    cameraRelative=Position+Offset;
    projected=RiftProjection*RiftView*vec4(cameraRelative,1.0);
    gl_Position=projected;
}

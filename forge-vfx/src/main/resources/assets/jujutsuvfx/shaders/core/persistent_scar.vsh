#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 RiftView;
uniform mat4 RiftProjection;
uniform vec3 Offset;
uniform float Fade;
out vec2 uv;
out vec3 cameraRelative;
out vec4 animation;
void main(){
    cameraRelative=Position+Offset;
    gl_Position=RiftProjection*RiftView*vec4(cameraRelative,1.0);
    uv=UV0;
    // Share the slash fragment shader at its settled state, including the material seed.
    animation=vec4(Fade,1.0,0.0,Color.a);
}

#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 RiftView;
uniform mat4 RiftProjection;
uniform mat4 LocalToWater;
uniform vec3 Offset;
out vec3 waterPosition;
out vec3 modelPosition;
out vec2 uv;
out vec4 tint;
void main(){
    waterPosition=(LocalToWater*vec4(Position,1.0)).xyz;
    vec3 mirrored=waterPosition;mirrored.y=-mirrored.y;
    gl_Position=RiftProjection*RiftView*vec4(mirrored+Offset,1.0);
    modelPosition=Position;uv=UV0;tint=Color;
}

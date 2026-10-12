#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
out vec2 uv;
out vec4 tint;
void main(){
    gl_Position=vec4(Position,1.0);
    uv=UV0;
    tint=Color;
}

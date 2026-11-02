#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ViewProjection;
uniform vec3 Origin;
out vec3 localPosition;
out vec3 eyePosition;
out vec2 texCoord;
out vec4 vertexColor;
void main(){
 localPosition=Position;
 eyePosition=Position+Origin;
 texCoord=UV0;
 vertexColor=Color;
 gl_Position=ViewProjection*vec4(eyePosition,1.0);
}

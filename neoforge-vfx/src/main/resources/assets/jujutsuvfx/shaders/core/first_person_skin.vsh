#version 150
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;
uniform sampler2D LightmapTexture;
uniform mat4 HandView;
uniform mat4 HandProjection;
out vec2 texCoord;
out vec4 skinColor;
void main(){
    gl_Position=HandProjection*HandView*vec4(Position,1.0);
    texCoord=UV0;
    vec3 n=normalize(mat3(HandView)*Normal);
    float shade=.76+.24*max(0.0,dot(n,normalize(vec3(-.25,.65,.72))));
    skinColor=Color*texelFetch(LightmapTexture,clamp(UV2/16,ivec2(0),ivec2(15)),0);
    skinColor.rgb*=shade;
}

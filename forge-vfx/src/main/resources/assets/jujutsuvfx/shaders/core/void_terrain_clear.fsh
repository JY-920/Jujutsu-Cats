#version 150
uniform sampler2D SceneDepth;
uniform mat4 InverseViewProjection;
uniform vec3 CenterRelative;
uniform float Radius;
in vec2 texCoord;
in vec4 tint;
out vec4 fragColor;
void vfxMaterialMain(){
    float depth=texture(SceneDepth,texCoord).r;if(depth>=.999999)discard;
    vec4 p=InverseViewProjection*vec4(texCoord*2.0-1.0,depth*2.0-1.0,1);
    vec3 point=p.xyz/p.w;
    if(distance(point,CenterRelative)>=Radius)discard;
    // Later entity geometry can occupy this pixel; the final cosmic pass supplies its background.
    gl_FragDepth=1.0;fragColor=vec4(.002,.006,.025,1)*tint;
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

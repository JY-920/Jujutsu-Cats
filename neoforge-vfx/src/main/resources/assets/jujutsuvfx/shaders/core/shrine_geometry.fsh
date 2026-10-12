#version 150
uniform float Time;
uniform float Visibility;
uniform float Reveal;
uniform float Mode;
uniform float ModelHeight;
in vec3 modelPosition;
in vec4 vertexTint;
in float material;
out vec4 fragColor;
float hash(vec3 p) {
    p=fract(p*0.1031);p+=dot(p,p.yzx+33.33);return fract((p.x+p.y)*p.z);
}
void vfxMaterialMain() {
    if(Mode>0.5) {
        fragColor=vec4(vertexTint.rgb,vertexTint.a*Visibility);
        return;
    }
    float cutoff=Reveal*ModelHeight;
    float grain=hash(floor(modelPosition*19.0));
    if(modelPosition.y>cutoff || grain>Visibility) discard;
    vec3 color=vertexTint.rgb;
    if(material<0.5) {
        float wood=0.92+0.08*sin(modelPosition.y*31.0+sin(modelPosition.x*51.0+modelPosition.z*41.0));
        color*=wood*(0.87+grain*0.17);
        color+=vec3(0.06,0.003,0.008)*(0.5+0.5*sin(Time*2.1+modelPosition.y*2.0));
    } else if(material<1.5) {
        color*=0.8+0.2*sin(Time*8.0+modelPosition.x*5.0+modelPosition.z*7.0);
        color+=vec3(0.20,0.03,0.01);
    }
    float revealEdge=exp(-abs(cutoff-modelPosition.y)*9.0)*(1.0-step(0.998,Reveal));
    color+=vec3(1.0,0.11,0.04)*revealEdge;
    fragColor=vec4(color,1.0);
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

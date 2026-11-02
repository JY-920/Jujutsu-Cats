#version 150
in vec2 uv;
in vec4 tint;
uniform sampler2D SceneColor;
uniform sampler2D SceneDepth;
uniform vec4 Viewport;
uniform float Time;
uniform float Strength;
out vec4 fragColor;
void main(){
 vec2 p=uv*2.-1.;float r=length(p);
 float envelope=exp(-pow((r-.70)/.095,2.))*tint.a;
 if(envelope<.004||Strength<.001)discard;
 vec2 gradient=vec2(dFdx(r),dFdy(r));gradient/=max(length(gradient),.00001);
 float ripple=sin((r-.70)*55.-Time*18.+tint.r*19.);
 vec2 size=max(Viewport.zw,vec2(1.)),pixel=1./size;
 vec2 base=(gl_FragCoord.xy-Viewport.xy)/size;
 vec2 offset=gradient*ripple*9.*envelope*Strength*pixel;
 vec2 sampleUv=clamp(base+offset,pixel*.5,vec2(1.)-pixel*.5);
 if(texture(SceneDepth,sampleUv).r+.00002<gl_FragCoord.z)sampleUv=base;
 vec3 scene=texture(SceneColor,sampleUv).rgb;
 if(any(isnan(scene))||any(isinf(scene)))discard;
 fragColor=vec4(scene,clamp(envelope*.65*Strength,0.,.8));
}

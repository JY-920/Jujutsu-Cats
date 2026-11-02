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
 if(Strength<=0.001)discard;
 float x=uv.x,y=uv.y*2.0-1.0;
 float tip=smoothstep(0.0,.055,x)*(1.0-smoothstep(.945,1.0,x));
 float taper=pow(max(0.0,sin(x*3.14159265)),.5);
 float edge=1.0-smoothstep(.58,1.0,abs(y));
 float envelope=exp(-y*y*5.0)*tip*taper*edge*tint.a;
 if(envelope<.003)discard;
 // Screen-space gradient rotates the air pressure wave with the real slash plane.
 vec2 gradient=vec2(dFdx(uv.y),dFdy(uv.y));
 vec2 normal=gradient/max(length(gradient),.00001),along=vec2(normal.y,-normal.x);
 float phase=Time*24.0+tint.r*137.0;
 float pressure=sin(y*12.0-x*8.0+phase)*8.0+sign(y)*4.5;
 vec2 offset=(normal*pressure+along*sin(x*31.0-phase)*2.2)*envelope*Strength;
 vec2 size=max(Viewport.zw,vec2(1)),pixel=1.0/size;
 vec2 base=(gl_FragCoord.xy-Viewport.xy)/size;
 vec2 sampleUv=clamp(base+offset*pixel,pixel*.5,vec2(1)-pixel*.5);
 // Do not pull a nearer opaque silhouette into the refracted background.
 if(texture(SceneDepth,sampleUv).r+0.00002<gl_FragCoord.z)sampleUv=base;
 vec3 scene=texture(SceneColor,sampleUv).rgb;
 if(any(isnan(scene))||any(isinf(scene)))discard;
 fragColor=vec4(scene,clamp(envelope*.85*Strength,0.0,.85));
}

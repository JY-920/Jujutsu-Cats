#version 150
in vec4 tint;
in vec2 uv;
uniform vec4 ColorModulator;
out vec4 fragColor;
float hash(vec3 p){p=fract(p*0.1031);p+=dot(p,p.yzx+33.33);return fract((p.x+p.y)*p.z);}
float noise(vec3 p){vec3 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);}
void main(){
 float v=uv.y>=1.5?uv.y-2.0:uv.y;
 vec3 p=vec3(uv.x*2.5,sin(v*6.2831853),cos(v*6.2831853));
 float n=noise(p)*.57+noise(p*2.1+7.0)*.28+noise(p*4.2+17.0)*.15;
 float fiber=pow(.5+.5*sin(uv.x*28.0+n*11.0),12.0);
 float edge=uv.y>=1.5?1.0:smoothstep(0.0,.20,v)*(1.0-smoothstep(.80,1.0,v));
 float a=tint.a*ColorModulator.a*edge*(.48+.52*smoothstep(.18,.8,n));
 if(a<.008)discard;
 vec3 color=tint.rgb*ColorModulator.rgb*(.38+1.00*n)+tint.rgb*fiber*.22;
 fragColor=vec4(min(color,vec3(1.0)),a);
}

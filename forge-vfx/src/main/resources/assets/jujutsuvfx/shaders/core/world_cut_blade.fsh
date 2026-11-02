#version 150
in vec2 uv;
in vec4 tint;
uniform float Time;
out vec4 fragColor;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x),f.y);}
void main(){
 float x=uv.x,y=uv.y*2.0-1.0,seed=tint.r*173.0;
 float ends=smoothstep(0.0,0.045,x)*(1.0-smoothstep(0.955,1.0,x));
 float taper=pow(max(0.0,sin(x*3.14159265)),0.65);
 float flow=Time*20.0+seed;
 float grain=noise(vec2(x*38.0-flow, y*9.0+seed));
 float core,glow,filaments,alpha;
 if(tint.g<0.5){
  // A long convex razor, with narrow moving subsidiary cuts breaking off its rim.
  float bend=0.09*taper+0.012*sin(x*34.0-flow)*taper;
  float d=abs(y-bend),w=(0.040+0.022*grain)*taper;
  float aa=max(fwidth(y)*0.75,0.0015);
  core=1.0-smoothstep(w,w+aa,d);
  glow=exp(-d*d/max(0.002,taper*taper*0.045));
  float fork=abs(d-taper*(0.12+0.13*noise(vec2(x*13.0-flow*.6,seed))));
  filaments=exp(-fork*fork/0.00032)*pow(grain,2.0)*taper;
  float scratch=exp(-abs(y-bend-taper*.40)*(32.0+grain*28.0))*pow(noise(vec2(x*62.0-flow*1.3,seed+8.0)),5.0);
  alpha=ends*(core+glow*.30+filaments*.46+scratch*.45)*tint.a;
 }else{
  // The second sheet lies behind the blade in the cut plane: directional shredding,
  // not another ring or a stack of opaque rectangular ribbons.
  float back=uv.y,rag=noise(vec2(x*31.0-flow*.7,back*5.0+seed));
  float reach=max(.001,taper*(.20+.70*rag));
  float mask=1.0-smoothstep(reach*.6,reach,back);
  float strands=pow(noise(vec2(x*85.0-flow*.2,back*1.5+seed)),5.0);
  core=exp(-back*back/0.0012)*taper;
  glow=exp(-back*5.5)*mask*taper;
  filaments=strands*mask*(1.0-back);
  alpha=ends*(core*.45+glow*.12+filaments*.42)*tint.a;
 }
 if(alpha<0.002||any(isnan(vec3(alpha,core,glow)))||any(isinf(vec3(alpha,core,glow))))discard;
 vec3 rgb=mix(vec3(.64,.82,1.0),vec3(1.65,1.7,1.75),clamp(core+filaments*.5,0.0,1.0));
 fragColor=vec4(rgb,clamp(alpha,0.0,1.0));
}

#version 150
in vec2 uv;
in vec4 tint;
uniform float Time;
out vec4 fragColor;
float segment(vec2 p,vec2 a,vec2 b){vec2 d=b-a;return length(p-a-d*clamp(dot(p-a,d)/max(dot(d,d),.0001),0.,1.));}
float glow(float d,float width){return exp(-d*d/(width*width));}
void main(){
 vec2 p=uv*2.-1.;float r=length(p),angle=atan(p.y,p.x),age=tint.b;
 float mode=floor(tint.g*3.+.5),seed=tint.r*31.,line=0.,mist=0.;
 if(mode<.5){
  // Mirrored coiling snakes around the mouth, with two curved fang strokes.
  vec2 q=vec2(abs(p.x),p.y);
  float snake=abs(q.x-(.33+.14*sin(q.y*7.+age*2.)));
  float body=glow(snake,.025)*(1.-smoothstep(.4,.64,abs(q.y)));
  float fang=min(segment(q,vec2(.16,.12),vec2(.07,-.23)),segment(q,vec2(.07,-.23),vec2(.22,-.10)));
  float crest=abs(length(p*vec2(1.,1.24))-.77);
  line=body+glow(fang,.024)+glow(crest,.012)*(.3+.7*pow(abs(sin(angle*3.)),12.));
  mist=glow(snake,.09)*(1.-smoothstep(.4,.7,abs(q.y)))+glow(crest,.045)*.5;
 }else if(mode<1.5){
  float corrugation=.012*sin(angle*13.+seed+Time*9.)+.009*sin(angle*23.-Time*14.);
  float rim=abs(r-.70-corrugation);
  line=glow(rim,.012)+glow(abs(r-.83-corrugation*.5),.005)*.65;
  float comb=pow(max(0.,sin(angle*27.+seed)),18.);
  line+=glow(abs(r-.63),.065)*comb*.32;
  mist=glow(rim,.065)+glow(abs(r-.53),.11)*.16;
 }else if(mode<2.5){
  float turn=angle+age*3.;float chain=smoothstep(.18,.45,abs(sin(turn*5.)));
  float orbit=abs(r-(.71+.035*sin(turn*3.)));
  line=glow(orbit,.013)*chain+glow(abs(r-.88),.006)*smoothstep(.55,.85,abs(sin(turn*7.)));
  vec2 q=vec2(abs(p.x),p.y);float fang=min(segment(q,vec2(.23,.21),vec2(.07,-.28)),segment(q,vec2(.07,-.28),vec2(.30,-.12)));
  line+=glow(fang,.015)*.75;mist=glow(orbit,.07)*.65+glow(fang,.06)*.2;
 }else{
  float taper=pow(max(0.,1.-p.x*p.x),.6);
  float filament=abs(p.y-.15*sin(p.x*12.-Time*24.+seed)*taper);
  line=glow(filament,.022)*taper*.5;mist=glow(filament,.12)*taper*.28;
 }
 float bounds=(1.-smoothstep(.94,1.,max(abs(p.x),abs(p.y))));
 float alpha=clamp((line+mist*.32)*bounds*tint.a,0.,.92);
 if(alpha<.003)discard;
 vec3 violet=vec3(.36,.10,.94),white=vec3(.91,.84,1.);
 vec3 color=mix(violet,white,clamp(line/(line+mist+.001),0.,1.));
 fragColor=vec4(color*(.75+min(line,1.)*.55),alpha);
}

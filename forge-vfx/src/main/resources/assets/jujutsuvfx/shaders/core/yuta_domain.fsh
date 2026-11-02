#version 150
uniform sampler2D Sampler0;
uniform float Time;
uniform float Material;
uniform float Seed;
uniform float Opacity;
uniform float Reveal;
uniform float Clash;
in vec3 localPosition;
in vec3 eyePosition;
in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;
const float PI=3.14159265359;
float hash(vec3 p){p=fract(p*.1031);p+=dot(p,p.yzx+33.33);return fract((p.x+p.y)*p.z);}
float noise(vec3 p){vec3 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);}
float fbm(vec3 p){float f=0.0,a=.52;for(int i=0;i<5;i++){f+=a*noise(p);p=p*2.04+vec3(5.2,7.1,2.7);a*=.48;}return f;}
float ring(float r,float target,float width){return 1.0-smoothstep(width,width*2.0,abs(r-target));}
void main(){
 vec3 p=localPosition;vec3 color=vertexColor.rgb;float alpha=vertexColor.a*Opacity;
 if(Material<.5){
  vec3 ray=normalize(p);float low=pow(1.0-smoothstep(-.08,.78,ray.y),1.7);
  float drift=fbm(vec3(ray.x*.9,ray.y*13.0+Time*.016,ray.z*.9+Seed*5.0));
  float ribbons=pow(max(0.0,sin(ray.y*67.0+drift*3.8+sin(ray.x*3.0+ray.z*2.0)*.4)),9.0);
  float cloud=fbm(vec3(ray.x*1.8,ray.y*9.0+Time*.012,ray.z*1.8));
  color=mix(vec3(.012,.004,.023),vec3(.37,.095,.42),low);
  color+=vec3(.29,.12,.34)*ribbons*low*.48;
  color+=vec3(.13,.035,.17)*cloud*low;
  alpha*=.985*smoothstep(0.0,.15,Reveal)*mix(1.0,.36,Clash);
  float seam=pow(max(0.0,sin(ray.x*14.0+ray.z*9.0+cloud*17.0-Time*2.0)),28.0);
  color+=vec3(.58,.19,.73)*seam*Clash*.6;
 }else if(Material<1.5){
  float radius=length(p.xz),edge=1.0-smoothstep(37.0,39.4,radius);
  float mist=fbm(vec3(p.x*.13+Time*.018,Seed*10.0,p.z*.13));
  float veins=1.0-smoothstep(.012,.035,abs(noise(p*.8)-.5));
  color=mix(vec3(.32,.16,.41),vec3(.73,.49,.8),mist);
  color-=vec3(.022,.012,.032)*veins;
  color+=vec3(.15,.08,.17)*pow(mist,2.0);
  alpha*=edge*smoothstep(radius/40.0-.08,radius/40.0+.1,Reveal)*.97*mix(1.0,.4,Clash);
 }else if(Material<3.5||(Material>6.5&&Material<7.5)){
  float front=Material>6.5?10.0:Reveal*44.0-length(p.xz);if(front<0.0||Opacity*vertexColor.a<hash(floor(p*120.0)))discard;
  vec3 n=normalize(cross(dFdx(p),dFdy(p)));if(!gl_FrontFacing)n=-n;
  vec3 light=normalize(vec3(-.35,.85,.3)),view=normalize(-eyePosition);
  float diffuse=.56+.44*abs(dot(n,light)),spec=pow(abs(dot(n,normalize(light+view))),32.0);
  if(Material>2.5){
   vec4 texel=texture(Sampler0,texCoord);if(texel.a<.1)discard;
   float metal=smoothstep(.18,.7,max(max(texel.r,texel.g),texel.b));
   color=texel.rgb*(.72+diffuse*.28)+vec3(1.0)*spec*metal*.12;
  }else{
   float ceremonial=clamp(step(vertexColor.b,vertexColor.g)+step(vertexColor.g*1.7,vertexColor.r),0.0,1.0);
   float grit=noise(p*11.0)*.09+noise(p*1.4)*.055;
   color*=diffuse*(.92+grit);
   float crackA=1.0-smoothstep(.003,.012,abs(fract(p.y*.28+p.x*.31+p.z*.19+noise(p*1.4)*.17)-.5));
   float crackB=1.0-smoothstep(.004,.016,abs(noise(p*.72+vec3(noise(p*2.7)*.27))-.5));
   float faults=max(crackA*step(.44,noise(p*.66)),crackB*.72)*(1.0-ceremonial);
   color=mix(color,vec3(.115,.055,.145),faults*.86);
   color=mix(color,color*(.91+.09*sin(texCoord.x*PI)),ceremonial);
   color+=vec3(.13,.045,.17)*pow(max(0.0,1.0-abs(dot(n,view))),3.0);
   color+=vec3(.18,.045,.24)*Clash*(.5+.5*sin(Time*4.0+p.y*.6));
  }
  float fog=smoothstep(21.0,51.0,length(eyePosition))*.38+exp(-max(p.y,0.0)*.55)*.28;
  if(Material<2.5&&(vertexColor.r>vertexColor.g*1.7||vertexColor.g>vertexColor.b))fog*=.28;
  color=mix(color,vec3(.65,.36,.72),clamp(fog,0.0,Material>2.5?.12:.58));
  color+=vec3(.6,.22,.8)*exp(-max(front,0.0)*3.0)*.35;alpha=1.0;
 }else if(Material<4.5){
  vec2 uv=texCoord;float x=abs(uv.x-.5)*2.0;
  float core=exp(-x*x*28.0),sheath=pow(max(0.0,1.0-x),2.0);
  float flow=.8+.2*sin(uv.y*65.0-Time*7.0+sin(uv.y*19.0+Time));
  float cap=smoothstep(0.0,.025,uv.y)*(1.0-smoothstep(.94,1.0,uv.y));
  color=mix(vertexColor.rgb,vec3(1.0,.99,.92),core*.9);
  alpha*=clamp((core*.76+sheath*.3)*flow*cap,0.0,.88);
 }else if(Material<5.5){
  vec2 uv=texCoord*2.0-1.0;float r=length(uv),angle=atan(uv.y,uv.x);
  float circles=ring(r,.86,.008)+ring(r,.72,.005)*.7+ring(r,.34,.006)*.6;
  float teeth=ring(r,.8,.027)*pow(abs(cos(angle*24.0)),16.0);
  float star=ring(r*(.83+.17*abs(cos(angle*4.0))),.55,.008);
  float glyphs=ring(r,.65,.04)*step(.45,sin(angle*32.0))*step(.1,sin(r*130.0+angle*8.0));
  float haze=exp(-r*r*5.0)*.08;
  alpha*=clamp(circles+teeth*.8+star+glyphs*.6+haze,0.0,1.0)*(1.0-smoothstep(.9,1.0,r));
  color=mix(vertexColor.rgb,vec3(1.0,.98,.82),clamp(circles*.5+star*.4,0.0,.8));
 }else if(Material<6.5){
  vec2 uv=texCoord;float edge=pow(max(0.0,1.0-abs(uv.x*2.0-1.0)),1.6);
  float runes=step(.64,fract(uv.y*20.0-Time*.4))*step(.2,abs(sin(uv.y*110.0+uv.x*8.0)));
  alpha*=edge*(.25+runes*.75)*smoothstep(0.0,.03,uv.y)*(1.0-smoothstep(.95,1.0,uv.y));
  color=mix(vertexColor.rgb,vec3(1.0,.98,.88),runes*.55);
 }else if(Material<8.5){
  vec2 uv=texCoord*2.0-1.0;
  float soft=exp(-uv.x*uv.x*2.8-uv.y*uv.y*4.0);
  float cloud=fbm(vec3(localPosition.x*.31+Time*.026,localPosition.y*.6,localPosition.z*.31-Time*.019));
  alpha*=soft*(.48+cloud*.52)*(1.0-smoothstep(.75,1.0,abs(uv.x)))*(1.0-smoothstep(.75,1.0,abs(uv.y)));
  color=vertexColor.rgb*(.86+cloud*.14);
 }else{
  vec2 uv=texCoord*2.0-1.0;float r2=dot(uv,uv),core=exp(-r2*48.0),halo=exp(-r2*5.2);
  alpha*=core+halo*.58;
  color=mix(vertexColor.rgb,vec3(1.0,.9,1.0),core*.95);
 }
 if(alpha<.002)discard;
 fragColor=vec4(color,clamp(alpha,0.0,1.0));
}

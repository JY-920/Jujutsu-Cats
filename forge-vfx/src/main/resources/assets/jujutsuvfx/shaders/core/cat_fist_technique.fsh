#version 150

// GLSL pow is undefined for negative bases, including pow(x, 2.0).
float vfxSquare(float x) { return x * x; }
vec2 vfxSquare(vec2 x) { return x * x; }
vec3 vfxSquare(vec3 x) { return x * x; }
vec4 vfxSquare(vec4 x) { return x * x; }
uniform float Time, Age, Seed, Power, Kind, Pass, FlashEnabled;
in vec2 uv;
in vec4 tint;
out vec4 fragColor;

float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float noise(vec2 p){
    vec2 cell=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    return mix(mix(hash(cell),hash(cell+vec2(1,0)),f.x),
               mix(hash(cell+vec2(0,1)),hash(cell+1.0),f.x),f.y);
}
float band(float d,float width){return exp(-d*d/max(width*width,0.000002));}
float sphere(float r,float radius){return 1.0-smoothstep(radius-.025,radius+.025,r);}

vec4 bluePalm(vec2 p){
    float r=length(p),angle=atan(p.y,p.x+.00001);
    float swirl=angle*3.0+log(r+.075)*7.0+Time*5.8+Seed*.7;
    float flow=band(sin(swirl),.11)*(0.25+.75*pow(.5+.5*cos(r*20.0+Time*14.0),5.0));
    flow*=smoothstep(.18,.32,r)*(1.0-smoothstep(.62,.94,r));
    float core=sphere(r,.145),mantle=sphere(r,.335)*(1.0-core);
    float lens=band(r-.365-.018*sin(angle*3.0-Time*3.0),.017)*(.3+.7*pow(.5+.5*cos(angle+Time*2.2),3.0));
    float highlight=band(length((p-vec2(-.09,.095))*vec2(.9,1.5)),.065);
    float halo=exp(-r*r*7.5)*.19;
    vec3 depth=mix(vec3(.02,.52,.92),vec3(.015,.035,.42),smoothstep(.15,.34,r));
    vec3 color=depth*mantle*.62+vec3(.025,.38,1.0)*halo;
    color+=vec3(.10,1.45,2.25)*(flow*.80+lens*.66);
    color+=vec3(1.35,1.90,2.2)*core*.61+vec3(2.3)*highlight*.52;
    return vec4(color,core*.34+mantle*.26+flow*.22+lens*.16+halo*.10);
}
vec4 redPalm(vec2 p){
    float r=length(p),angle=atan(p.y,p.x+.00001);
    float matte=.82+.18*hash(floor(p*195.0)+Seed);
    float pulse=.68+FlashEnabled*.32*pow(.5+.5*sin(Time*14.0+Seed),5.0);
    float core=sphere(r,.245),rim=band(r-.267,.024);
    float pale=band(length((p-vec2(-.085,.06))*vec2(1.0,.68)),.085)*core;
    float pressure=fract(Time*1.65+Seed*.19);
    float wave=band(r-(.33+pressure*.48),.012+.012*pressure)*(1.0-pressure);
    wave*=smoothstep(-.65,.25,cos(angle-.7+Seed*.2));
    float halo=exp(-r*r*10.5)*.25;
    vec3 color=vec3(.94,.012,.068)*core*matte*.75+vec3(1.2,.016,.105)*(rim*.65+halo);
    color+=vec3(1.65,1.3,1.35)*pale*pulse*.75+vec3(1.8,.44,.52)*wave*.46;
    return vec4(color,core*.46+rim*.22+halo*.10+wave*.16);
}
vec4 blueContact(vec2 p){
    float r=length(p),angle=atan(p.y,p.x+.00001);
    float collapse=1.0-smoothstep(0.0,.25,Age);
    float radius=.10+.64*collapse;
    float fade=1.0-smoothstep(.25,.59,Age);
    float inner=exp(-r*r/(.008+.006*collapse));
    float convergence=band(r-radius,.018+.03*collapse);
    convergence*=.40+.60*vfxSquare(.5+.5*cos(angle*3.0+Seed+Age*10.0));
    float spirals=band(sin(angle*3.0+log(r+.07)*6.0+Age*18.0+Seed),.12);
    spirals*=smoothstep(.09,.22,r)*(1.0-smoothstep(radius*.70,radius+.13,r));
    float compress=band(Age-.19,.045),flash=compress*(.20+.80*FlashEnabled);
    float fringe=band(r-(.14+max(0.0,Age-.2)*1.45),.016)*smoothstep(.18,.24,Age);
    vec3 color=vec3(.025,.34,1.25)*(convergence*.32+exp(-r*r*9.0)*.12);
    color+=vec3(.07,1.15,1.8)*(spirals*.68+convergence*.63+fringe*.25);
    color+=vec3(1.9,2.2,2.5)*inner*(.45+flash*1.6);
    return vec4(color,(convergence*.24+spirals*.18+inner*.42+fringe*.10))*fade;
}
vec4 redContact(vec2 p){
    float r=length(p),angle=atan(p.y,p.x+.00001);
    float launch=1.0-exp(-Age*18.0),fade=1.0-smoothstep(.13,.56,Age);
    float grain=.85+.15*hash(floor(p*210.0)+Seed);
    float core=exp(-r*r*170.0)*exp(-Age*15.0);
    float radius=.14+.63*launch;
    float pressure=band(r-radius,.022)*smoothstep(-.6,.25,cos(angle*2.0-Seed));
    float wake=band(r-radius*.74,.035)*(.25+.75*pow(.5+.5*cos(angle*3.0+Seed),3.0));
    float spread=pow(.5+.5*cos(angle*7.0+noise(p*3.0+Seed)*2.4),12.0);
    spread*=smoothstep(.12,.27,r)*(1.0-smoothstep(radius*.80,radius+.10,r));
    vec3 color=vec3(1.4,.006,.065)*(pressure*.80+wake*.24+spread*.35)*grain;
    color+=vec3(2.0,1.15,1.25)*(pressure*.35+spread*.16);
    color+=vec3(2.5,2.0,2.1)*core*(.4+.6*FlashEnabled);
    return vec4(color,(pressure*.21+wake*.11+spread*.15+core*.42))*fade;
}
vec4 front(vec2 p){
    float r=length(p),angle=atan(p.y,p.x+.00001);
    float edge=band(r-.72-.018*sin(angle*3.0+Seed),.013+fwidth(r));
    float inner=band(r-.655,.018)*.35;
    float windows=.20+.80*smoothstep(-.3,.6,cos(angle*(Kind>.5?2.0:3.0)+Seed+Age*4.0));
    float fade=smoothstep(0.0,.045,Age)*(1.0-smoothstep(Kind>.5?.10:.21,Kind>.5?.43:.46,Age));
    vec3 base=Kind>.5?vec3(1.8,.08,.20):vec3(.06,.90,1.75);
    vec3 color=base*(edge*.75+inner*.45)+vec3(1.55,1.7,1.85)*edge*.36;
    return vec4(color,(edge*.25+inner*.12))*windows*fade;
}
void vfxMaterialMain(){
    vec4 result;
    if(Pass>1.5&&Pass<2.5){
        float edge=exp(-uv.x*uv.x*3.8),white=exp(-uv.x*uv.x*72.0);
        float ends=smoothstep(0.0,.07,uv.y)*(1.0-smoothstep(.87,1.0,uv.y));
        float silk=.90+.10*sin(uv.y*31.0-Time*9.0);
        vec3 color=tint.rgb*edge*1.25+vec3(1.7)*white*.72;
        fragColor=vec4(color*silk,edge*.28)*tint.a*ends;
        return;
    }
    if(Pass>2.5)result=front(uv);
    else if(Pass>.5)result=Kind>.5?redContact(uv):blueContact(uv);
    else result=Kind>.5?redPalm(uv):bluePalm(uv);
    float border=1.0-smoothstep(.88,1.0,length(uv));
    result.rgb*=.88+.18*Power;
    fragColor=vec4(result.rgb*tint.rgb,result.a)*tint.a*border;
    if(max(max(fragColor.r,fragColor.g),max(fragColor.b,fragColor.a))<.003)discard;
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

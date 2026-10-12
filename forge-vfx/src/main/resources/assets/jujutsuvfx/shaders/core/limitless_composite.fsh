#version 150

// GLSL pow is undefined for negative bases, including pow(x, 2.0).
float vfxSquare(float x) { return x * x; }
vec2 vfxSquare(vec2 x) { return x * x; }
vec3 vfxSquare(vec3 x) { return x * x; }
vec4 vfxSquare(vec4 x) { return x * x; }
uniform sampler2D SceneColor, SceneDepth, EnergyLayer;
uniform mat4 InverseViewProjection;
uniform vec3 CenterRelative, Axis, Right, Up;
uniform vec4 CoreScreen;
uniform float Time, PhaseTime, Kind, Phase, Radius, CoreRadius, Visibility, Expansion, Distortion;
uniform float Seed, Flash;
uniform vec2 PurpleMotion;
uniform vec2 PurpleFusion;
uniform float PurpleOutput;
in vec2 texCoord;
in vec4 tint;
out vec4 fragColor;

float starHash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
vec4 purpleAir(vec2 p){
    float r=length(p);
    if(r<.84||r>3.25)return vec4(0);
    vec2 displacement=vec2(0);float shadow=0.0,gleam=0.0;
    float drive=(.35+.65*PurpleMotion.y)*(1.0+.45*clamp(PurpleOutput-1.0,0.0,1.0)),free=max(r-.96,0.0);
    vec2 radial=p/max(r,.001),tangent=vec2(-radial.y,radial.x);
    for(int i=0;i<6;i++){
        float seed=starHash(vec2(float(i)*17.31+Seed,29.7));
        // Open hooked sheets meet the shell almost tangentially. Outer material travels
        // down the curved sheet toward the core, with clockwise angular motion in screen space.
        float hook=(.73+seed*.23)*(1.0-exp(-free*1.8))+.13*free;
        float angle=(float(i)+.32*(seed-.5))*1.04719755+Seed*.37+PurpleMotion.x+hook
                +.055*sin(Time*.91+seed*19.0+free*2.0);
        vec2 outward=vec2(cos(angle),sin(angle)),side=vec2(-outward.y,outward.x);
        float reach=1.80+starHash(vec2(seed,41.7))*1.25;
        reach=mix(1.65,reach,.50+.50*Expansion);
        if(dot(p,outward)<0.0||r>reach)continue;
        float phase=seed*17.0,travel=r*(6.8+seed)+Time*(8.5+seed*2.0)+phase;
        float along=clamp(free/max(reach-.96,.01),0.0,1.0);
        float width=(.21+seed*.15)*(1.0-.78*along)*(.91+.09*sin(travel));
        float x=dot(p,side)/width;
        float sheet=exp(-x*x*1.30)*smoothstep(.86,1.035,r)*(1.0-smoothstep(reach-.38,reach,r));
        sheet*=.82+.18*sin(Time*1.3+phase);
        float folds=sin(x*3.1+sin(travel)*.65);
        // Outward sampling contracts the scenery; the positive tangent sample produces
        // clockwise apparent motion. Fold ridges themselves advect from the tip inward.
        displacement+=side*sheet*folds*(.21+seed*.15)
                +(radial*.19+tangent*.22)*sheet*drive*(.68+.32*sin(travel));
        float dark=x-.43,light=x+.48;
        shadow=max(shadow,sheet*(.12+.65*exp(-dark*dark*45.0)));
        gleam=max(gleam,sheet*exp(-light*light*70.0)*(.70+.30*sin(travel)));
    }
    float size=length(displacement);
    displacement*=min(1.0,.46/max(size,.0001));
    return vec4(displacement,shadow,gleam);
}
mat2 galaxyRotate(float a){float s=sin(a),c=cos(a);return mat2(c,-s,s,c);}
vec3 galaxyFrame(vec3 p){
    p=vec3(dot(p,Right),dot(p,Up),dot(p,Axis));
    p.yz=galaxyRotate(.58)*p.yz;p.xz=galaxyRotate(-.32)*p.xz;p.xy=galaxyRotate(PurpleMotion.x)*p.xy;
    return p;
}
vec4 purpleRim(vec3 ray,float worldDepth){
    float along=dot(ray,CenterRelative),radius=max(CoreRadius,.025);
    float closestSquared=max(dot(CenterRelative,CenterRelative)-along*along,0.0);
    float disc=radius*radius*1.065*1.065-closestSquared;
    if(disc<=0.0||along<=0.0)return vec4(0);
    float hit=along-sqrt(disc);
    if(hit<=.025||hit>=worldDepth)return vec4(0);
    vec3 n=galaxyFrame(normalize(ray*hit-CenterRelative));
    float cloud=.5+.5*sin(dot(n,vec3(17,9,11))+Time*.8+sin(dot(n,vec3(9,-11,7))-Time*.6));
    float uneven=(cloud-.5)*.025+.006*sin(dot(n,vec3(31,-17,7))-Time*.6);
    float r=sqrt(closestSquared)/radius+uneven;
    float inner=smoothstep(.900,.986,r),outside=1.0-smoothstep(.986,1.065,r);
    float alive=Phase>1.5?exp(-PhaseTime*5.5):1.0;
    float alpha=inner*outside*(.52+.14*cloud)*Visibility*alive;
    // #2f22bd anchors both the absorbing shell and the volume's outer gas.
    vec3 blueViolet=vec3(.184314,.133333,.741176);
    vec3 dark=blueViolet*(.48+.57*cloud);
    float edge=(r-.903)/.028;
    vec3 thinLight=blueViolet*1.30*exp(-edge*edge)*(.12+.14*cloud)*Visibility*alive;
    return vec4(dark*alpha+thinLight,alpha);
}
vec3 purpleGalaxy(vec3 ray,float worldDepth){
    float along=dot(ray,CenterRelative),radius=CoreRadius*1.10;
    if(along*along+radius*radius-dot(CenterRelative,CenterRelative)<=0.0)return vec3(0);
    vec3 origin=galaxyFrame(-CenterRelative)/max(CoreRadius,.025),direction=galaxyFrame(ray),light=vec3(0);
    // Two intersecting, world-anchored dust sheets add fine stars without more volume-march steps.
    for(int layer=0;layer<2;layer++){
        vec3 o=origin,d=direction;
        if(layer==1){
            vec3 x=normalize(vec3(.64,0,.12)),z=normalize(vec3(-.12,.52,.64)),y=cross(z,x);
            o=vec3(dot(origin,x),dot(origin,y),dot(origin,z));
            d=vec3(dot(direction,x),dot(direction,y),dot(direction,z));
        }
        float hit=-o.z*(d.z<0.0?-1.0:1.0)/max(abs(d.z),.04);
        vec2 local=o.xy+d.xy*hit;
        vec2 grid=local*(layer==0?114.0:137.0),cell=floor(grid),within=fract(grid)-.5;
        float aa=max(length(fwidth(grid))*.25,.045);
        float radial=length(local),limit=layer==0?.97:.61;
        if(hit*CoreRadius<=.025||hit*CoreRadius>=worldDepth||radial>limit||abs(d.z)<.055)continue;
        float angle=atan(local.y,local.x+.000001);
        float arms=pow(.5+.5*sin(angle*2.0-radial*12.0-Time*.95),5.0);
        float concentration=exp(-radial*radial*6.0)*.48+arms*exp(-radial*radial*3.0)*.27;
        float seed=starHash(cell+float(layer)*31.17);
        if(seed<1.0-concentration)continue;
        vec2 offset=(vec2(starHash(cell+27.3),starHash(cell+63.1))-.5)*.78;
        vec2 point=within-offset;
        float width=.038+.065*starHash(cell+14.1),filtered=sqrt(width*width+aa*aa);
        float star=exp(-dot(point,point)/(filtered*filtered))*width*width/(filtered*filtered);
        if(seed>.989){
            vec2 spread=sqrt(vec2(.016*.016,.20*.20)+aa*aa);
            vec2 vertical=point/spread,horizontal=point/spread.yx;
            star+=(exp(-dot(vertical,vertical))+exp(-dot(horizontal,horizontal)))*.0032/(spread.x*spread.y)*.32;
        }
        float edge=1.0-smoothstep(limit*.80,limit,radial);
        float twinkle=.82+.18*sin(Time*(2.3+seed*1.1)+seed*61.0);
        float facing=smoothstep(.055,.24,abs(d.z));
        light+=mix(vec3(3.2,1.15,4.6),vec3(7.0,6.6,7.6),exp(-radial*radial*4.0))
                *star*edge*twinkle*facing*(layer==0?1.0:.60);
    }
    return light*Visibility*(Phase>1.5?exp(-PhaseTime*5.5):1.0);
}
vec3 purpleStars(vec3 ray,float worldDepth){
    float radius=CoreRadius*.97;
    float along=dot(ray,CenterRelative),disc=along*along+radius*radius-dot(CenterRelative,CenterRelative);
    if(disc<=0.0)return vec3(0);
    float hit=along-sqrt(disc);
    if(hit<=.025||hit>=worldDepth)return vec3(0);
    vec3 normal=normalize(ray*hit-CenterRelative),n=galaxyFrame(normal);
    vec2 uv=vec2(atan(n.z,n.x)/6.2831853,asin(clamp(n.y,-1.0,1.0))/3.14159265+.5);
    vec2 grid=uv*vec2(80,40),cell=floor(grid),local=fract(grid)-.5;
    cell.x=mod(cell.x,80.0);
    vec2 dx=dFdx(uv),dy=dFdy(uv);
    dx.x-=round(dx.x);dy.x-=round(dy.x);
    float aa=max(length((abs(dx)+abs(dy))*vec2(80,40))*.30,.055);
    float seed=starHash(cell);
    if(seed<.82)return vec3(0);
    vec2 offset=(vec2(starHash(cell+31.7),starHash(cell+73.1))-.5)*.46;
    float width=.065+.075*starHash(cell+12.7);
    float filtered=sqrt(width*width+aa*aa);
    float star=exp(-dot(local-offset,local-offset)/(filtered*filtered))*width*width/(filtered*filtered);
    float twinkle=.67+.33*sin(Time*3.7+seed*63.0);
    float facing=smoothstep(.05,.40,-dot(normal,ray));
    float alive=Phase>1.5?exp(-PhaseTime*5.5):1.0;
    return mix(vec3(1.6,.42,2.5),vec3(3.8,3.2,4.0),starHash(cell+8.1))*star*twinkle*facing*alive*Visibility;
}

vec4 upsample(float depth){
    vec2 size=vec2(textureSize(EnergyLayer,0));
    vec2 p=texCoord*size-.5,base=floor(p),f=fract(p);
    vec4 sum=vec4(0);float weights=0.0;
    for(int y=0;y<2;y++)for(int x=0;x<2;x++){
        vec2 uv=(base+vec2(x,y)+.5)/size;
        float w=(x==0?1.0-f.x:f.x)*(y==0?1.0-f.y:f.y);
        w*=exp(-abs(texture(SceneDepth,uv).r-depth)/(.00012+(1.0-depth)*.005));
        sum+=texture(EnergyLayer,uv)*w;weights+=w;
    }
    return weights>.0001?sum/weights:vec4(0);
}
void vfxMaterialMain(){
    bool blue=Kind<.5,purple=Kind>1.5,burst=!blue&&Phase>1.5;
    float depth=texture(SceneDepth,texCoord).r;
    vec2 size=vec2(textureSize(SceneColor,0)),aspect=vec2(size.x/size.y,1),border=.5/size;
    vec4 farPoint=InverseViewProjection*vec4(texCoord*2.0-1.0,1,1);
    vec3 ray=normalize(farPoint.xyz*(farPoint.w<0.0?-1.0:1.0));
    float worldDepth=1000000.0;
    vec3 world=vec3(0);
    if(depth<.999999){
        vec4 p=InverseViewProjection*vec4(texCoord*2.0-1.0,depth*2.0-1.0,1);
        world=p.xyz/p.w;worldDepth=dot(world,ray);
    }
    vec4 energy=upsample(depth);
    float fused=PurpleFusion.x>.5?smoothstep(.34,.80,PurpleFusion.y):1.0;
    float lensRadius=max(CoreRadius,Radius*.55*PurpleFusion.x);
    vec2 delta=(texCoord-CoreScreen.xy)*aspect;
    float distance2D=length(delta),lens=0.0,glow=0.0,shock=0.0,ground=0.0;
    vec2 warp=vec2(0);vec4 air=vec4(0);
    if(CoreScreen.z>0.0){
        float r=distance2D/max(CoreScreen.z,.0001);
        float along=dot(ray,CenterRelative),closest=length(ray*along-CenterRelative);
        float front=along-sqrt(max(lensRadius*lensRadius*4.0-closest*closest,0.0));
        float visible=smoothstep(0.0,max(.05,lensRadius*.22),worldDepth-front);
        float alive=burst?exp(-PhaseTime*9.0):1.0;
        lens=exp(-vfxSquare((r-1.2)/(purple?1.20:blue?1.35:.72)))*visible*Visibility*alive;
        // Sampling farther from the projected center draws the scene inward for Blue.
        float strength=(purple?.035+.075*PurpleMotion.y*(.30+.70*Expansion):blue?.16:-.105)*lens*(blue?Expansion:1.0);
        float swirl=purple?.075*(.35+.65*PurpleMotion.y)*(.40+.60*Expansion)*lens*smoothstep(.72,1.15,r)
                :.021*(blue?1.0:-1.0)*lens*sin(Time*6.0+r*3.0);
        warp=(delta*strength+vec2(-delta.y,delta.x)*swirl)/aspect;
        if(purple){
            float airVisible=smoothstep(0.0,max(.05,lensRadius*.20),worldDepth-max(.025,along-lensRadius*.45));
            air=purpleAir(delta/max(CoreScreen.z,.0001))*airVisible*Visibility*alive*(.25+.75*Expansion);
            if(PurpleOutput>1.001){
                // Wider, heavier air creases with narrow pearly edges. The broad violet
                // pressure mantle stays transparent, leaving the galaxy and silhouette readable.
                float boost=clamp(PurpleOutput-1.0,0.0,1.0);
                air.xy*=1.0+.23*boost;air.z*=1.0+.22*boost;air.w*=1.0+.35*boost;
            }
            warp+=air.xy*CoreScreen.z/aspect;
        }
        glow=exp(-r*r*.42)*visible*Visibility*alive;
    }
    if(burst&&!purple){
        float progress=1.0-exp(-PhaseTime*4.8);
        float along=dot(ray,CenterRelative),centerLength=dot(CenterRelative,CenterRelative);
        for(int i=0;i<2;i++){
            float radius=Radius*max(.005,progress-float(i)*.16);
            float disc=along*along+radius*radius-centerLength;
            if(disc>0.0){
                float span=sqrt(disc),hit=along-span;
                if(hit<.025)hit=along+span;
                if(hit>.025&&hit<worldDepth){
                    vec3 normal=(ray*hit-CenterRelative)/radius;
                    float grazing=pow(max(0.0,1.0-abs(dot(normal,ray))),5.0);
                    float ripple=.78+.22*sin(normal.y*35.0+atan(normal.z,normal.x)*13.0-Time*17.0);
                    shock+=(grazing+.022)*ripple*exp(-PhaseTime*3.5)*(i==0?1.0:.5);
                }
            }
        }
        if(depth<.999999){
            vec3 local=world-CenterRelative;
            ground=exp(-vfxSquare((length(local)-Radius*progress)/max(.10,Radius*.024)))
                    *exp(-PhaseTime*3.0);
        }
        if(CoreScreen.z>0.0)warp-=delta/max(distance2D,.001)/aspect*shock*.027;
    }
    vec2 warped=clamp(texCoord+warp*Distortion,border,1.0-border);
    float sampledDepth=texture(SceneDepth,warped).r;
    if(purple){
        if(sampledDepth<.999999){
            vec4 sampleWorld=InverseViewProjection*vec4(warped*2.0-1.0,sampledDepth*2.0-1.0,1);
            // Refract terrain behind the pressure field, while retaining foreground occluders.
            if(length(sampleWorld.xyz/sampleWorld.w)<length(CenterRelative)-lensRadius*1.25){
                warped=mix(texCoord,warped,.04);air*=.10;
            }
        }
    }else if(abs(sampledDepth-depth)>.0015)warped=mix(texCoord,warped,.06);
    vec3 background=texture(SceneColor,warped).rgb;
    if(purple){
        // Almost all air colour is sampled scenery, with only restrained cold highlights on the creases.
        background*=1.0-air.z*.24*Distortion;
        background+=vec3(.70,.80,.96)*air.w*.085*Distortion;
    }
    vec3 color=background*(1.0-energy.a)+energy.rgb;
    // The halo is emitted in this pass, so bloom from an optional shader pack is not required.
    color+=(purple?vec3(.16,.085,.64):blue?vec3(.018,.38,.86):vec3(.64,.008,.055))*glow*.52;
    if(purple){
        color+=(purpleStars(ray,worldDepth)+purpleGalaxy(ray,worldDepth))*fused;
        vec4 rim=purpleRim(ray,worldDepth)*fused;
        color=color*(1.0-rim.a)+rim.rgb;
        if(PurpleOutput>1.001&&CoreScreen.z>0.0){
            vec2 p=delta/max(CoreScreen.z,.0001);
            float r=length(p),a=atan(p.y,p.x+.00001);
            float compression=pow(.5+.5*sin(a*3.0-r*9.0-Time*7.0+sin(a*7.0)*.40),14.0);
            float edges=exp(-vfxSquare((r-1.13)/.19))*compression;
            color+=vec3(.58,.32,1.30)*edges*glow*fused*.45*clamp(PurpleOutput-1.0,0.0,1.0);
        }
        float flash=CoreScreen.z>0.0?Flash*Visibility*Distortion:0.0;
        color=mix(color,vec3(1.20,1.17,1.25),clamp(flash,0.0,.92));
    }
    color+=vec3(1.7,.13,.32)*(shock*.68+ground*.32)*Visibility;
    fragColor=vec4(color,1.0)*tint;
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

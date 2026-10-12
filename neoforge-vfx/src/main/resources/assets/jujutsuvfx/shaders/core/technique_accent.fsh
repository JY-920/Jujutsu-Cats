#version 150
in vec2 uv;
in vec4 tint;
in vec3 relativePosition;
uniform vec3 CenterRelative;
uniform float CoreRadius,Time;
out vec4 fragColor;
void vfxMaterialMain(){
    float distance=length(relativePosition);
    vec3 ray=relativePosition/max(distance,.0001);
    float along=dot(CenterRelative,ray);
    float disc=along*along-dot(CenterRelative,CenterRelative)+CoreRadius*CoreRadius;
    if(CoreRadius>0.0&&disc>0.0){float front=along-sqrt(disc);if(front>.025&&front<distance-.025)discard;}
    bool lightning=uv.y>1.5;
    float p=uv.y-(lightning?2.0:0.0);
    if(lightning){
        float x=abs(uv.x),tip=1.0-smoothstep(.89,1.0,p);
        float halo=exp(-x*x*3.8)*(1.0-smoothstep(.82,1.0,x));
        float border=exp(-x*x*17.0),white=exp(-x*x*135.0);
        vec3 emitted=vec3(.24,.008,1.45)*halo*.70+vec3(.45,.026,2.4)*border*.85+vec3(3.9)*white;
        if(halo*tip*tint.a<.002)discard;
        fragColor=vec4(emitted*tip*tint.a,0.0);
        return;
    }
    float taper=(1.0-smoothstep(.87,1.0,p))*(lightning?1.0:mix(.15,1.0,smoothstep(.0,.30,p)));
    float drift=dot(relativePosition-CenterRelative,vec3(.73,1.17,-.91));
    // Both waves travel from the root toward the tip, with a wandering bright vein inside the air sheet.
    float grain=.50+.28*sin(p*32.0-Time*39.0+drift)+.22*sin(p*59.0-Time*63.0+uv.x*4.0);
    float ragged=lightning?1.0:.64+grain*.33;
    float vein=lightning?0.0:sin(p*17.0-Time*19.0+drift)*.21;
    float x=abs(uv.x)/ragged;
    float fringe=exp(-x*x*(lightning?4.1:2.8))*(1.0-smoothstep(.80,1.0,x));
    float crossLine=(uv.x-vein)/ragged;
    float line=exp(-crossLine*crossLine*(lightning?72.0:24.0));
    float alpha=(fringe*(lightning?.56:.38)+line*(lightning?.94:.66))*taper*tint.a;
    if(alpha<.002)discard;
    vec3 white=vec3(2.30,2.14,2.55);
    vec3 color=mix(tint.rgb*(lightning?1.5:.16),white,clamp(line*(lightning?1.25:.92),0.0,1.0));
    // Premultiplied air adds silver/red highlights and dark translucent folds; electricity only emits light.
    alpha=clamp(alpha,0.0,1.0);
    fragColor=vec4(color*alpha,lightning?0.0:alpha*.65);
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

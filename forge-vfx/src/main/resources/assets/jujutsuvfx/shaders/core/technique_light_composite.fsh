#version 150
uniform sampler2D SceneColor,SceneDepth,LightLayer;
uniform mat4 InverseViewProjection;
uniform vec2 LightSize;
in vec2 texCoord;
in vec4 tint;
out vec4 fragColor;

float sceneDistance(vec2 uv,float depth){
    vec4 p=InverseViewProjection*vec4(uv*2.0-1.0,depth*2.0-1.0,1.0);
    return length(p.xyz/p.w);
}
void vfxMaterialMain(){
    vec3 base=texture(SceneColor,texCoord).rgb;
    float depth=texture(SceneDepth,texCoord).r;
    if(depth>=.999999){fragColor=vec4(base,1)*tint;return;}
    float distance=sceneDistance(texCoord,depth);
    vec2 pixel=texCoord*LightSize-.5,cell=floor(pixel),f=fract(pixel);
    vec3 light=vec3(0);float total=0.0;
    for(int y=0;y<2;y++)for(int x=0;x<2;x++){
        vec2 offset=vec2(float(x),float(y));
        vec2 uv=clamp((cell+offset+.5)/LightSize,.5/LightSize,1.0-.5/LightSize);
        vec4 sampleLight=texture(LightLayer,uv);
        if(sampleLight.a<0.0)continue;
        vec2 weights=mix(1.0-f,f,offset);
        float depthWeight=exp(-abs(sampleLight.a-distance)/max(.12,distance*.004));
        float weight=max(.025,weights.x*weights.y)*depthWeight;
        light+=sampleLight.rgb*weight;total+=weight;
    }
    if(total<.0001){fragColor=vec4(base,1)*tint;return;}
    light/=total;
    // A small diffuse floor reveals dark surfaces; scene texture remains the
    // material estimate. Highlight compression keeps the colored fill readable.
    float peak=max(light.r,max(light.g,light.b));
    vec3 material=.10+.70*sqrt(max(base,vec3(0)));
    vec3 lit=(base+light*material)/(1.0+peak*.32);
    fragColor=vec4(max(lit,vec3(0)),1)*tint;
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

#version 150

// GLSL pow is undefined for negative bases, including pow(x, 2.0).
float vfxSquare(float x) { return x * x; }
vec2 vfxSquare(vec2 x) { return x * x; }
vec3 vfxSquare(vec3 x) { return x * x; }
vec4 vfxSquare(vec4 x) { return x * x; }
uniform sampler2D SceneColor;
uniform sampler2D SceneDepth;
uniform float Strength;
uniform vec4 Impact0, Impact1, Impact2, Impact3;
uniform vec4 Axis0, Axis1, Axis2, Axis3;
in vec2 texCoord;
in vec4 tint;
out vec4 fragColor;

vec3 impulse(vec4 impact, vec4 blade, vec2 aspect) {
    if (impact.w < .001) return vec3(0);
    vec2 delta=(texCoord-impact.xy)*aspect;
    float distance=length(delta);
    vec2 normal=delta/max(distance,.001);
    vec2 axis=normalize(blade.xy*aspect+vec2(.000001));
    vec2 side=vec2(-axis.y,axis.x);
    float across=dot(delta,side), along=dot(delta,axis);
    float front=distance-(.018+impact.z*.64);
    float ring=exp(-vfxSquare(front/(.014+impact.z*.018)));
    float tear=exp(-abs(across)/.045-along*along/ .16)*(1.0-impact.z);
    vec2 wave=normal*ring*sin(front*110.0+1.2)*.009;
    wave+=side*sign(across+.00001)*tear*.006*cos(impact.z*9.0);
    return vec3(wave/aspect,ring*.035+tear*.018)*impact.w;
}
void vfxMaterialMain() {
    vec2 size=vec2(textureSize(SceneColor,0));
    vec2 aspect=vec2(size.x/size.y,1.0);
    vec3 pulse=impulse(Impact0,Axis0,aspect)+impulse(Impact1,Axis1,aspect)
            +impulse(Impact2,Axis2,aspect)+impulse(Impact3,Axis3,aspect);
    vec2 offset=clamp(pulse.xy*Strength,vec2(-.013),vec2(.013));
    vec2 border=.5/size;
    vec2 warped=clamp(texCoord+offset,border,1.0-border);
    float depth=texture(SceneDepth,texCoord).r;
    if(abs(texture(SceneDepth,warped).r-depth)>.0015) warped=mix(texCoord,warped,.12);
    vec3 color=texture(SceneColor,warped).rgb;
    vec2 fringe=normalize(offset+vec2(.0000001))*min(length(offset)*.14,.0012);
    vec2 redUV=clamp(warped+fringe,border,1.0-border), blueUV=clamp(warped-fringe,border,1.0-border);
    if(abs(texture(SceneDepth,redUV).r-depth)<.0015)color.r=texture(SceneColor,redUV).r;
    if(abs(texture(SceneDepth,blueUV).r-depth)<.0015)color.b=texture(SceneColor,blueUV).b;
    color+=vec3(.12,.035,.05)*pulse.z*Strength;
    fragColor=vec4(color,1.0)*tint;
}

// Never allow an invalid material sample to cover the game image.
void main() {
    vfxMaterialMain();
    if (any(isnan(fragColor)) || any(isinf(fragColor))) { discard; }
}

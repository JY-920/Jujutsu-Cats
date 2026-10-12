"""Compile both GLES dialects on the installed desktop driver, without a game window.

This catches real compiler/linker errors. It is not a mobile performance benchmark.
"""
from pathlib import Path
import re
import subprocess

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/"build/shader-check"
HEADERS=Path("E:/MCStudioDownload/game/MinecraftPE_Netease/3.8.0.313229/data/shaders/glsl")
SHADERS=ROOT/"pack/resource_pack/shaders/glsl"

def expand(path,depth=0):
    if depth>20:
        raise ValueError("cyclic include")
    source=path.read_text("utf-8")
    return re.sub(r'^\s*#include\s+"([^"]+)"',lambda m:expand(HEADERS/m[1],depth+1),source,flags=re.M)

JAVA=r'''
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import java.nio.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
public class NeteaseShaderProbe {
 static int[] pixel(int x,int y){
  ByteBuffer b=ByteBuffer.allocateDirect(4);GL11.glReadPixels(x,y,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);
  return new int[]{b.get(0)&255,b.get(1)&255,b.get(2)&255};
 }
 static void near(int[] a,int[] b,String label){
  for(int i=0;i<3;i++)if(Math.abs(a[i]-b[i])>2)throw new AssertionError(label+java.util.Arrays.toString(a));
 }
 static void draw(int p,String name,float depth,boolean active,Path image)throws Exception{
  GL20.glUseProgram(p);GL11.glViewport(0,0,64,64);
  int vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);
  int buffer=GL15.glGenBuffers();GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,buffer);
  GL15.glBufferData(GL15.GL_ARRAY_BUFFER,new float[]{-1,-1,0,0,0,1,-1,0,1,0,-1,1,0,0,1,1,1,0,1,1},GL15.GL_STATIC_DRAW);
  int pos=GL20.glGetAttribLocation(p,"POSITION"),uv=GL20.glGetAttribLocation(p,"TEXCOORD_0");
  GL20.glEnableVertexAttribArray(pos);GL20.glVertexAttribPointer(pos,3,GL11.GL_FLOAT,false,20,0);
  GL20.glEnableVertexAttribArray(uv);GL20.glVertexAttribPointer(uv,2,GL11.GL_FLOAT,false,20,12);
  int[] textures=new int[2];
  for(int i=0;i<2;i++){
   textures[i]=GL11.glGenTextures();GL13.glActiveTexture(GL13.GL_TEXTURE0+(i==0?0:2));GL11.glBindTexture(GL11.GL_TEXTURE_2D,textures[i]);
   ByteBuffer data=ByteBuffer.allocateDirect(4);data.put((byte)(i==0?77:Math.round(depth*255))).put((byte)102).put((byte)128).put((byte)255).flip();
   GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,1,1,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);
   GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
   GL20.glUniform1i(GL20.glGetUniformLocation(p,"TEXTURE_"+(i==0?0:2)),i==0?0:2);
  }
  float[] projection={1.4f,0,0,0,0,1.4f,0,0,0,0,-1.0002f,-1,0,0,-.20002f,0};
  GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(p,"PROJ"),false,projection);
  if(name.equals("purple")){
   GL20.glUniform4f(GL20.glGetUniformLocation(p,"EXTRA_VECTOR1"),0,0,8,2);
   GL20.glUniform4f(GL20.glGetUniformLocation(p,"EXTRA_VECTOR2"),3,3,.4f,active?1:0);
  }else if(name.equals("void")||name.equals("shrine")){
   GL20.glUniform4f(GL20.glGetUniformLocation(p,"EXTRA_VECTOR1"),0,0,3,active?1:0);
   GL20.glUniform4f(GL20.glGetUniformLocation(p,"EXTRA_VECTOR2"),0,0,1,0);
  }else{
   GL20.glUniform4f(GL20.glGetUniformLocation(p,"EXTRA_VECTOR1"),0,0,8,2);
   GL20.glUniform4f(GL20.glGetUniformLocation(p,"EXTRA_VECTOR2"),3,name.equals("technique")?1:0,.25f,active?1:0);
  }
  GL20.glUniform4f(GL20.glGetUniformLocation(p,"EXTRA_VECTOR3"),1,.7f,name.equals("purple")?0:2,1);
  GL20.glUniform1i(GL20.glGetUniformLocation(p,"TEXTURE_1"),0);
  if(name.equals("slash")){
   GL20.glUniform4f(GL20.glGetUniformLocation(p,"EXTRA_VECTOR2"),.7f,.7f,0,.12f);
   GL20.glUniform4f(GL20.glGetUniformLocation(p,"EXTRA_VECTOR4"),17,active?1:0,.4f,0);
  }
  GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP,0,4);GL11.glFinish();
  if(!active || name.equals("purple")&&depth<.99f)near(pixel(32,32),new int[]{77,102,128},"Passthrough / occlusion failed ");
  if(active&&name.equals("purple")&&depth>.99f&&pixel(32,32)[0]<150)throw new AssertionError("Missing purple core");
  if(image!=null){
   ByteBuffer data=ByteBuffer.allocateDirect(64*64*4);GL11.glReadPixels(0,0,64,64,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);
   BufferedImage im=new BufferedImage(64,64,BufferedImage.TYPE_INT_RGB);
   for(int y=0;y<64;y++)for(int x=0;x<64;x++){int n=(y*64+x)*4;im.setRGB(x,63-y,((data.get(n)&255)<<16)|((data.get(n+1)&255)<<8)|(data.get(n+2)&255));}
   ImageIO.write(im,"png",image.toFile());
  }
  int error=GL11.glGetError();if(error!=GL11.GL_NO_ERROR)throw new AssertionError("GL error "+error);
  for(int texture:textures)GL11.glDeleteTextures(texture);GL15.glDeleteBuffers(buffer);GL30.glDeleteVertexArrays(vao);
 }
 static int compile(int type,String source){
  int s=GL20.glCreateShader(type);GL20.glShaderSource(s,source);GL20.glCompileShader(s);
  if(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)==0)throw new AssertionError(GL20.glGetShaderInfoLog(s));return s;
 }
 public static void main(String[] args)throws Exception{
  if(!GLFW.glfwInit())throw new AssertionError("GLFW failed");
  GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
  GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,4);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,3);
  long window=GLFW.glfwCreateWindow(64,64,"NetEase shader verification",0,0);
  if(window==0)throw new AssertionError("No driver context");GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
  System.out.println(GL11.glGetString(GL11.GL_RENDERER));
  for(String version:new String[]{"100","300"})for(String name:new String[]{"purple","void","technique","shrine","slash","aura"}){
   Path root=Path.of(args[0]);int vertex=compile(GL20.GL_VERTEX_SHADER,Files.readString(root.resolve("fullscreen_"+version+".vert")));
   int fragment=compile(GL20.GL_FRAGMENT_SHADER,Files.readString(root.resolve(name+"_"+version+".frag")));
   int p=GL20.glCreateProgram();GL20.glAttachShader(p,vertex);GL20.glAttachShader(p,fragment);GL20.glLinkProgram(p);
   if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==0)throw new AssertionError(GL20.glGetProgramInfoLog(p));
   System.out.println("PASS GLES "+version+" "+name+" compile/link");
   draw(p,name,1,false,null);draw(p,name,1,true,root.resolve(name+"_"+version+".png"));
   if(name.equals("purple"))draw(p,name,.96f,true,null);
   System.out.println("PASS GLES "+version+" "+name+" raster / disabled passthrough"+(name.equals("purple")?" / foreground depth occlusion":""));
   GL20.glDeleteProgram(p);GL20.glDeleteShader(vertex);GL20.glDeleteShader(fragment);
  }
  GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();
 }
}
'''

if __name__=="__main__":
    OUT.mkdir(parents=True,exist_ok=True)
    for version in ("100","300"):
        prefix="#version "+version+(" es" if version=="300" else "")+"\n#define POS4 vec4\n#define MAT4 mat4\n#define MCPE_NETEASE\n"
        (OUT/("fullscreen_"+version+".vert")).write_text(prefix+expand(SHADERS/"jujutsuvfx_fullscreen.vertex"),encoding="utf-8")
        for name in ("purple","void","technique","shrine","slash","aura"):
            (OUT/(name+"_"+version+".frag")).write_text(prefix+expand(SHADERS/("jujutsuvfx_"+name+".fragment")),encoding="utf-8")
    strict=ROOT.parent/'build/shader-tools1022/glslang-16.6.0/bin/glslang.exe'
    for path in sorted(OUT.glob('*.frag'))+sorted(OUT.glob('*.vert')):
        result=subprocess.run([str(strict),str(path)],capture_output=True,text=True)
        if result.returncode:raise RuntimeError(str(path)+'\n'+result.stdout+result.stderr)
    print('PASS strict Khronos GLES 100 / 300 validation')
    (OUT/"NeteaseShaderProbe.java").write_text(JAVA,encoding="utf-8")
    cache=Path.home()/".gradle/caches/modules-2/files-2.1/org.lwjgl"
    jars=[]
    for lib in ("lwjgl","lwjgl-glfw","lwjgl-opengl"):
        for suffix in (".jar","-natives-windows.jar"):
            jars.append(str(next((cache/lib/"3.3.3").glob("*/"+lib+"-3.3.3"+suffix))))
    cp=";".join(jars)
    java=Path("C:/Program Files/Zulu/zulu-21/bin")
    subprocess.run([str(java/"javac.exe"),"-encoding","UTF-8","-proc:none","-cp",cp,"-d",str(OUT),str(OUT/"NeteaseShaderProbe.java")],check=True)
    result=subprocess.run([str(java/"java.exe"),"-cp",str(OUT)+";"+cp,"NeteaseShaderProbe",str(OUT)],capture_output=True,text=True,encoding="utf-8",errors="replace")
    (OUT/"results.log").write_text(result.stdout+result.stderr,encoding="utf-8")
    print(result.stdout+result.stderr)
    if result.returncode:
        raise SystemExit(result.returncode)

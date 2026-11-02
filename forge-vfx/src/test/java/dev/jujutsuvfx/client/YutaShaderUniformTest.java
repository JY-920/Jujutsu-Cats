package dev.jujutsuvfx.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.shaders.Uniform;
import org.objectweb.asm.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Checks the Minecraft Java setter overload, which a raw OpenGL shader probe cannot cover. */
public final class YutaShaderUniformTest {
 public static void main(String[] args) throws Exception {
  Map<String,String> types=new HashMap<>();
  try(var in=YutaShaderUniformTest.class.getResourceAsStream("/assets/jujutsuvfx/shaders/core/yuta_domain.json")){
   for(var entry:JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("uniforms")){
    var u=entry.getAsJsonObject();types.put(u.get("name").getAsString(),u.get("type").getAsString());
   }
  }
  List<String> errors=new ArrayList<>();int[] writes={0},textures={0};
  try(var in=YutaShaderUniformTest.class.getResourceAsStream("/dev/jujutsuvfx/client/YutaDomainRenderer.class")){
   new ClassReader(Objects.requireNonNull(in)).accept(new ClassVisitor(Opcodes.ASM9){
    @Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] exceptions){
     return new MethodVisitor(Opcodes.ASM9){
      String literal,uniform;
      @Override public void visitLdcInsn(Object value){if(value instanceof String s)literal=s;}
      @Override public void visitMethodInsn(int opcode,String owner,String method,String descriptor,boolean itf){
       if(method.equals("setShaderTexture"))textures[0]++;
       if(method.equals("safeGetUniform")){uniform=literal;return;}
       if(uniform!=null&&method.equals("set")){
        writes[0]++;
        if("float".equals(types.get(uniform))&&descriptor.startsWith("(I"))errors.add(name+": float "+uniform+" calls integer setter "+descriptor);
        uniform=null;
       }
      }
     };
    }
   },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
  }
  if(textures[0]<2)throw new AssertionError("Sword Sampler0 must bind and restore the RenderSystem texture slot");
  if(writes[0]<8)throw new AssertionError("Uniform call sites not inspected: "+writes[0]);
  if(!errors.isEmpty())throw new AssertionError(String.join("; ",errors));
  // Exercise the same Minecraft buffer path used by the two corrected scalar writes.
  try(Uniform reveal=new Uniform("Reveal",4,1,null);Uniform opacity=new Uniform("Opacity",4,1,null)){
   reveal.set(1f);opacity.set(1f);
  }
  System.out.println("Yuta shader: "+writes[0]+" Java uniform setter contracts and Minecraft float buffers passed");
 }
}

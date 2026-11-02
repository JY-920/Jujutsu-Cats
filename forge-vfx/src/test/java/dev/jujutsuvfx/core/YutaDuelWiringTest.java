package dev.jujutsuvfx.core;
import org.objectweb.asm.*;
/** Prevent the removed Yuta pair script from overriding own-health milestones. */
public final class YutaDuelWiringTest {
 public static void main(String[] args)throws Exception{
  for(String type:new String[]{"entity/YutaCat","entity/SorcererCat","server/CatSorcery"}){
   try(var in=YutaDuelWiringTest.class.getResourceAsStream("/dev/jujutsuvfx/"+type+".class")){
    new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9){
     @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
      return new MethodVisitor(Opcodes.ASM9){
       @Override public void visitMethodInsn(int op,String owner,String method,String desc,boolean itf){
        if(type.equals("entity/YutaCat")&&n.equals("beginDomain")&&method.equals("consume"))throw new AssertionError("Preparing a domain must not consume it before it opens");
        if(owner.equals("dev/jujutsuvfx/server/YutaDuel"))throw new AssertionError(type+" still bypasses own-health rules through YutaDuel."+method);
       }
      };
     }
    },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
   }
  }
  var c=Class.forName("dev.jujutsuvfx.core.CatDomainRules");
  var recovery=c.getMethod("fullyRecovered",float.class,float.class);
  check(!(boolean)recovery.invoke(null,780f,800f),"partial healing must not rearm domains");
  check((boolean)recovery.invoke(null,784f,800f),"full-recovery rule shared by all cats");
  var due=c.getMethod("milestone",float.class,float.class,int.class);
  var use=c.getMethod("consume",int.class,int.class);
  var route=c.getMethod("scriptOpponent",int.class,int.class,boolean.class);
  check((int)due.invoke(null,401f,800f,0)==0,"not before half health");
  check((int)due.invoke(null,400f,800f,0)==1,"half health independent of opponent health");
  int used=(int)use.invoke(null,0,1);
  check((int)due.invoke(null,250f,800f,used)==0,"no repeated half-health opening");
  check((int)due.invoke(null,80f,800f,used)==2,"critical opening available");
  check((int)due.invoke(null,0f,800f,0)==0,"dead cannot open");
  check((int)due.invoke(null,300f,800f,(int)use.invoke(null,0,2))==0,"critical consumes skipped half threshold");
  check((boolean)route.invoke(null,1,2,false),"Gojo/Sukuna script preserved");
  check((boolean)route.invoke(null,2,1,false),"Sukuna/Gojo script preserved");
  check(!(boolean)route.invoke(null,2,4,false),"Yuta excluded from Sukuna script");
  check(!(boolean)route.invoke(null,1,4,false),"outside target uses own-health domains");
  check(!(boolean)route.invoke(null,1,3,false),"unrelated Mahoraga excluded");
  check((boolean)route.invoke(null,1,3,true),"scripted two-on-one guest retained");
  System.out.println("Own-health domain thresholds and exact script opponent routing passed");
 }
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}

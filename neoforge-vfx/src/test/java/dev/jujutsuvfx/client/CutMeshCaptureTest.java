package dev.jujutsuvfx.client;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.jujutsuvfx.core.PlaneCut;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
public final class CutMeshCaptureTest {
 public static void main(String[] args){
  var capture=new CutMeshCapture();
  // A null material token avoids initializing Minecraft render types without a running client.
  var poses=new PoseStack();poses.translate(2,3,4);
  var out=capture.record(null,true);
  float[][] xy={{-1,-1},{1,-1},{1,1},{-1,1}};
  for(var p:xy)out.addVertex(poses.last().pose(),p[0],p[1],0).setColor(40,80,120,255).setUv((p[0]+1)*.5f,(p[1]+1)*.5f).setOverlay(0x12345678).setLight(0x00110022).setNormal(poses.last(),0,0,1);
  capture.finish();
  if(capture.faces.size()!=1)throw new AssertionError("Quad lost or duplicated");
  var face=capture.faces.get(0);if(face.material()!=null)throw new AssertionError("Texture material lost");
  var v=face.points().get(0);
  if(v.x()!=1||v.y()!=2||v.z()!=4||v.r()!=40||v.u()!=0||v.overlay()!=0x12345678||v.light()!=0x00110022||v.nz()!=1)throw new AssertionError("Posed geometry attributes lost: "+v);
  if(PlaneCut.clip(face.points(),new PlaneCut.Plane(1,0,0,2),true).size()!=4)throw new AssertionError("Captured quad cannot be cut");
  capture.finish();if(capture.faces.size()!=1)throw new AssertionError("Final vertex captured twice");
  System.out.println("Cut mesh capture: posed entity quad, material token, UV, colors, light and overlay preserved");
 }
}

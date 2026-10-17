package dev.jujutsuvfx.client;
import com.mojang.blaze3d.vertex.*;
import dev.jujutsuvfx.core.PlaneCut;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import java.util.*;
/** Records NEW_ENTITY quads after the renderer has posed them, including texture/material batches. */
final class CutMeshCapture implements MultiBufferSource {
 record Face(RenderType material,List<PlaneCut.Vertex> points){}
 final List<Face> faces=new ArrayList<>();
 private final Map<RenderType,Recorder> streams=new LinkedHashMap<>();
 private int count;
 @Override public VertexConsumer getBuffer(RenderType type){return record(type,type.format()==DefaultVertexFormat.NEW_ENTITY&&type.mode()==VertexFormat.Mode.QUADS);}
 VertexConsumer record(RenderType type,boolean keep){return streams.computeIfAbsent(type,t->new Recorder(t,keep));}
 void finish(){for(var r:streams.values())r.flush();}
 private final class Recorder implements VertexConsumer {
  final RenderType type;final boolean keep;final List<PlaneCut.Vertex> quad=new ArrayList<>();
  float x,y,z,u,v,nx,ny,nz;int r=255,g=255,b=255,a=255,light,overlay=OverlayTexture.NO_OVERLAY;boolean pending;
  Recorder(RenderType type,boolean keep){this.type=type;this.keep=keep;}
  void flush(){if(!pending)return;pending=false;if(!keep)return;
   if(++count>100000)throw new IllegalStateException("Space cut model exceeds capture budget");
   quad.add(new PlaneCut.Vertex(x,y,z,u,v,nx,ny,nz,r,g,b,a,light,overlay));
   if(quad.size()==4){faces.add(new Face(type,List.copyOf(quad)));quad.clear();}
  }
  public VertexConsumer addVertex(float x,float y,float z){flush();this.x=x;this.y=y;this.z=z;r=g=b=a=255;u=v=nx=ny=0;nz=1;light=0;overlay=OverlayTexture.NO_OVERLAY;pending=true;return this;}
  public VertexConsumer setColor(int r,int g,int b,int a){this.r=r;this.g=g;this.b=b;this.a=a;return this;}
  public VertexConsumer setUv(float u,float v){this.u=u;this.v=v;return this;}
  public VertexConsumer setUv1(int u,int v){overlay=(v<<16)|(u&65535);return this;}
  public VertexConsumer setUv2(int u,int v){light=(v<<16)|(u&65535);return this;}
  public VertexConsumer setNormal(float x,float y,float z){nx=x;ny=y;nz=z;return this;}
 }
}

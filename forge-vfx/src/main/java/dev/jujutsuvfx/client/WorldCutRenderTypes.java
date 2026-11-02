package dev.jujutsuvfx.client;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.RenderType;
/** Deliberately no WEATHER_TARGET: vanilla lightning is cleared/composited by the weather pass. */
final class WorldCutRenderTypes extends RenderType {
 private WorldCutRenderTypes(String name,VertexFormat format,VertexFormat.Mode mode,int size,boolean crumbling,boolean sort,Runnable setup,Runnable clear){super(name,format,mode,size,crumbling,sort,setup,clear);}
 private static final java.util.Map<RenderType,RenderType> BODIES=new java.util.IdentityHashMap<>();
 static void clearBodies(){BODIES.clear();}
 static RenderType body(RenderType source){
  return BODIES.computeIfAbsent(source,material->new WorldCutRenderTypes("world_cut_body",material.format(),material.mode(),65536,false,true,
   ()->{try(SceneSnapshot.Target target=new SceneSnapshot.Target()){material.setupRenderState();}},
   ()->{try(SceneSnapshot.Target target=new SceneSnapshot.Target()){material.clearRenderState();}}));
 }
 static final RenderType GLOW=create("world_cut_glow",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,4096,false,true,
  CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setTransparencyState(ADDITIVE_TRANSPARENCY)
   .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
}

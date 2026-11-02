package dev.jujutsuvfx.client;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.jujutsuvfx.entity.GetoCat;
import dev.jujutsuvfx.core.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class GetoCatRenderer extends MobRenderer<GetoCat,GetoCatModel> {
 private PlayfulCloudPhysics.Frame root;
 private PlayfulCloudPhysics.Vec worldOrigin;
 private static PlayfulCloudPhysics.Vec vector(org.joml.Vector3f v){return new PlayfulCloudPhysics.Vec(v.x,v.y,v.z);}
 private static PlayfulCloudPhysics.Frame basis(org.joml.Matrix4f m){return new PlayfulCloudPhysics.Frame(vector(m.transformPosition(new Vector3f())),vector(m.transformDirection(new Vector3f(1,0,0))),vector(m.transformDirection(new Vector3f(0,1,0))),vector(m.transformDirection(new Vector3f(0,0,1))));}

 private static final ResourceLocation SKIN=ResourceLocation.fromNamespaceAndPath("jujutsuvfx","textures/entity/geto_cat.png"),DETAILS=ResourceLocation.fromNamespaceAndPath("jujutsuvfx","textures/entity/geto_cat_details.png");
 public GetoCatRenderer(EntityRendererProvider.Context c){super(c,new GetoCatModel(c.getResourceManager()),.65f);addLayer(new Extra(this));addLayer(new CatDanceLayer<>(this,c.getResourceManager(),SKIN,DETAILS));}
 public ResourceLocation getTextureLocation(GetoCat c){return SKIN;}
 protected void scale(GetoCat c,PoseStack p,float partial){p.scale(1.5f,1.5f,1.5f);}
 @Override public boolean shouldRender(GetoCat c,Frustum f,double x,double y,double z){return super.shouldRender(c,f,x,y,z)||c.action()==GetoCat.UZUMAKI&&f.isVisible(c.getBoundingBox().inflate(GetoVortexMotion.RANGE+6));}
 @Override public void render(GetoCat c,float yaw,float partial,PoseStack p,MultiBufferSource buffers,int light){var prior=root;var oldOrigin=worldOrigin;root=basis(p.last().pose());
 worldOrigin=new PlayfulCloudPhysics.Vec(net.minecraft.util.Mth.lerp((double)partial,c.xOld,c.getX()),net.minecraft.util.Mth.lerp((double)partial,c.yOld,c.getY()),net.minecraft.util.Mth.lerp((double)partial,c.zOld,c.getZ()));
 try{super.render(c,yaw,partial,p,buffers,light);}finally{root=prior;worldOrigin=oldOrigin;}}
 private static final class Extra extends RenderLayer<GetoCat,GetoCatModel>{
  private final GetoCatRenderer owner;
  private final java.util.Map<GetoCat,PlayfulCloudPhysics> physics=new java.util.WeakHashMap<>();
  Extra(GetoCatRenderer r){super(r);owner=r;}
  public void render(PoseStack p,MultiBufferSource buffers,int light,GetoCat c,float swing,float amount,float partial,float age,float yaw,float pitch){
   if(c.isInvisible())return;
   getParentModel().renderMaterial(p,buffers.getBuffer(RenderType.entityCutoutNoCull(DETAILS)),light,LivingEntityRenderer.getOverlayCoords(c,0),-1,1);
   if(c.danceStyle()!=0||(c.action()==GetoCat.UZUMAKI||c.action()==GetoCat.BLAST))return;
   p.pushPose();try{
    var rig=getParentModel().rig;rig.combatRoot(p);
    // The first rod's midpoint remains between the jaws; only the other rods are simulated.
    rig.bone("head").translateAndRotate(p);p.translate(0,.085,-.23);p.mulPose(Axis.ZP.rotationDegrees(90));
    p.scale(.62f,.62f,.62f);p.translate(0,PlayfulCloudMotion.GRIP-PlayfulCloudMotion.ROD_LENGTH*.5,0);
    var frame=PlayfulCloudPhysics.Frame.relative(owner.root,basis(p.last().pose()),owner.worldOrigin);
    var chain=physics.computeIfAbsent(c,key->new PlayfulCloudPhysics());
    float drive=GetoCloudMotion.drive(c.action(),c.actionAge(partial));
    chain.ground(c.getY()+.04);chain.update((c.tickCount+(double)partial)/20,frame,drive);
    PlayfulCloudRenderer.physical(p,buffers.getBuffer(PlayfulCloudRenderer.SOLID),chain,frame,light,LivingEntityRenderer.getOverlayCoords(c,0));
    PlayfulCloudRenderer.physicalTrail(p,buffers.getBuffer(PlayfulCloudRenderer.TRAIL),chain,frame,drive*.42f);
   }finally{p.popPose();}
  }
 }
}

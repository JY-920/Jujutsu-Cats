package dev.jujutsuvfx.client;
import com.mojang.blaze3d.platform.NativeImage;
import dev.jujutsuvfx.core.SpiritRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
/** Cached hue replacement preserves source texture detail and cutout alpha, including eye masks. */
final class SpiritTextures {
 private record Key(ResourceLocation source,int palette){}
 private static final Map<Key,ResourceLocation> CACHE=new HashMap<>();
 private static final ResourceLocation PHANTOM=id("minecraft","textures/entity/phantom_eyes.png"),SPIDER=id("minecraft","textures/entity/spider_eyes.png");
 private static ResourceLocation id(String namespace,String path){return ResourceLocation.fromNamespaceAndPath(namespace,path);}
 static void clear(){ChimeraRig.clear();var manager=Minecraft.getInstance().getTextureManager();for(var e:CACHE.entrySet())if(!e.getKey().source.equals(e.getValue()))manager.release(e.getValue());CACHE.clear();}
 static ResourceLocation colored(ResourceLocation source,int palette){
  Key key=new Key(source,palette);var found=CACHE.get(key);if(found!=null)return found;
  if(CACHE.size()>=96)return source;
  try(var in=Minecraft.getInstance().getResourceManager().open(source)){
   NativeImage image=NativeImage.read(in);
   try{
    for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)image.setPixelRGBA(x,y,SpiritRules.colorizeAbgr(image.getPixelRGBA(x,y),palette));
    var out=id("jujutsuvfx","spirit_filter/"+palette+"/"+source.getNamespace()+"/"+source.getPath());
    Minecraft.getInstance().getTextureManager().register(out,new DynamicTexture(image));CACHE.put(key,out);return out;
   }catch(RuntimeException failure){image.close();throw failure;}
  }catch(Exception failure){com.mojang.logging.LogUtils.getLogger().warn("Cannot recolor spirit texture {}",source,failure);CACHE.put(key,source);return source;}
 }
 static RenderType material(RenderType original,ResourceLocation body,int palette){
  for(var eye:new ResourceLocation[]{PHANTOM,SPIDER})if(original==RenderType.eyes(eye))return RenderType.eyes(colored(eye,palette));
  if(original==RenderType.entityCutoutNoCull(body)||original==RenderType.entityCutoutNoCull(body,false))return RenderType.entityCutoutNoCull(colored(body,palette));
  if(original==RenderType.entityCutout(body))return RenderType.entityCutout(colored(body,palette));
  if(original==RenderType.entityTranslucent(body))return RenderType.entityTranslucent(colored(body,palette));
  return original;
 }
}

package dev.jujutsuvfx.item;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;

/** The held stack carries the server's start clock through vanilla equipment synchronization. */
public final class DanceItem extends Item {
 private static final String START="JujutsuDanceStart",OWNER="JujutsuDanceOwner",DIM="JujutsuDanceDimension";
 private final int style;
 public DanceItem(){this(1);}
 public DanceItem(int style){super(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON));this.style=style;}
 public static int style(Player p){if(dev.jujutsuvfx.server.LivingDance.active(p))return dev.jujutsuvfx.server.LivingDance.style(p);return held(p).getItem() instanceof DanceItem item?item.style:0;}
 @Override public net.minecraft.world.InteractionResult interactLivingEntity(ItemStack stack,Player player,LivingEntity target,net.minecraft.world.InteractionHand hand){
  if(!target.isAlive())return net.minecraft.world.InteractionResult.FAIL;
  dev.jujutsuvfx.server.LivingDance.toggle(target,style);
  return net.minecraft.world.InteractionResult.sidedSuccess(player.level().isClientSide);
 }
 public static ItemStack held(Player p){return p.getMainHandItem().getItem() instanceof DanceItem?p.getMainHandItem():p.getOffhandItem().getItem() instanceof DanceItem?p.getOffhandItem():ItemStack.EMPTY;}
 public static boolean active(LivingEntity e){if(dev.jujutsuvfx.server.LivingDance.active(e)&&e instanceof Player)return true;return e instanceof Player p&&p.isAlive()&&!p.isSpectator()&&!p.isSleeping()&&!p.isPassenger()&&!p.isFallFlying()&&!p.isSwimming()&&!p.isUsingItem()&&!held(p).isEmpty();}
 public static long started(Player p){if(dev.jujutsuvfx.server.LivingDance.active(p))return dev.jujutsuvfx.server.LivingDance.state(p).getLong("Start");var tag=tag(held(p));return tag.hasUUID(OWNER)&&tag.getUUID(OWNER).equals(p.getUUID())?tag.getLong(START):p.level().getGameTime();}
 @Override public void inventoryTick(ItemStack stack,Level level,Entity entity,int slot,boolean selected){
  if(level.isClientSide||!(entity instanceof Player p))return;
  var tag=tag(stack);boolean holding=active(p)&&held(p)==stack;
  String dim=level.dimension().location().toString();
  if(holding){
   if(!tag.contains(START)||!tag.hasUUID(OWNER)||!tag.getUUID(OWNER).equals(p.getUUID())||!dim.equals(tag.getString(DIM))){
    tag.putLong(START,level.getGameTime());tag.putUUID(OWNER,p.getUUID());tag.putString(DIM,dim);write(stack,tag);
   }
  }else if(tag.contains(START)){tag.remove(START);tag.remove(OWNER);tag.remove(DIM);write(stack,tag);}
 }
 private static CompoundTag tag(ItemStack stack){var data=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);return data==null?new CompoundTag():data.copyTag();}
 private static void write(ItemStack stack,CompoundTag tag){stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));}
}

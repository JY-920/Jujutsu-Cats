package dev.jujutsuvfx.server;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.SustainedBeam;
import dev.jujutsuvfx.core.BeamPath;
import dev.jujutsuvfx.core.TechniqueDamage;
import dev.jujutsuvfx.item.VfxItems;
import dev.jujutsuvfx.network.SkillGesture;
import dev.jujutsuvfx.network.UzumakiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Server-owned held beams. The scripted duel remains a separate presentation. */
@EventBusSubscriber(modid=JujutsuVfx.ID)
public final class PlayerUzumaki {
 private static final AtomicInteger SERIAL=new AtomicInteger(7_000_000);
 private static final Map<MinecraftServer,Map<UUID,Cast>> CASTS=new WeakHashMap<>();
 private static final class Cast {
  final ServerPlayer owner;final ServerLevel level;final InteractionHand hand;final ItemStack bound;final int session,kind;
  final long born;boolean fired;long clashStart=-1;int clashId;
  Vec3 origin,direction,otherOrigin=Vec3.ZERO,contact=Vec3.ZERO;double length=SustainedBeam.RANGE;
  final BeamPath.Steering steering=new BeamPath.Steering();
  List<SustainedBeam.P> path=List.of();
  List<SustainedBeam.P> otherPath=List.of();
  Cast(ServerPlayer p,InteractionHand hand,int kind){
   owner=p;level=p.serverLevel();this.hand=hand;this.kind=kind;bound=p.getItemInHand(hand).copy();
   born=p.getServer().getTickCount();session=SERIAL.incrementAndGet();origin=origin(p);direction=p.getLookAngle().normalize();
  }
  int age(){return (int)(owner.getServer().getTickCount()-born);}
  int gesture(){return kind==SustainedBeam.LOVE?SkillGesture.LOVE_BEAM:SkillGesture.UZUMAKI;}
  int clashAge(){return clashStart<0?-1:(int)(owner.getServer().getTickCount()-clashStart);}
  UzumakiState state(int age){return new UzumakiState(owner.getId(),owner.getUUID(),session,age,fired,origin,direction,length,kind,clashId,clashAge(),otherOrigin,contact,path.stream().map(PlayerUzumaki::vec).toList(),otherPath.stream().map(PlayerUzumaki::vec).toList());}
  void send(int age){var packet=state(age);for(ServerPlayer p:level.players())if(age<0||CinematicAudio.visualRange(p,origin,160))packet.send(p);}
 }
 private PlayerUzumaki(){}
 public static Vec3 origin(LivingEntity player){return player.getEyePosition().add(player.getLookAngle().scale(1.6)).add(0,-.15,0);}
 public static void start(ServerPlayer player,InteractionHand hand){
  int kind=player.getItemInHand(hand).is(VfxItems.LOVE_BEAM.get())?SustainedBeam.LOVE:SustainedBeam.GETO;
  if(!player.getItemInHand(hand).is(kind==SustainedBeam.LOVE?VfxItems.LOVE_BEAM.get():VfxItems.MAXIMUM_UZUMAKI.get())||DomainFreeze.hold(player))return;
  var casts=CASTS.computeIfAbsent(player.getServer(),key->new HashMap<>());if(casts.containsKey(player.getUUID()))return;
  Cast c=new Cast(player,hand,kind);casts.put(player.getUUID(),c);aim(c);c.send(0);
  TechniqueGestures.start(player,c.session,c.gesture(),hand,true);
  c.level.playSound(null,player.blockPosition(),SoundEvents.END_PORTAL_FRAME_FILL,SoundSource.PLAYERS,.9f,kind==SustainedBeam.LOVE?1.1f:.65f);
 }
 private static Cast find(ServerPlayer player){var casts=CASTS.get(player.getServer());return casts==null?null:casts.get(player.getUUID());}
 public static void cancel(ServerPlayer player){
  Cast c=find(player);if(c==null||c.clashId!=0)return;
  stop(c);CASTS.get(player.getServer()).remove(player.getUUID());
 }
 private static void stop(Cast c){
  c.send(-1);TechniqueGestures.releaseCurrent(c.owner,c.gesture());
  c.owner.getCooldowns().addCooldown(c.bound.getItem(),c.fired?100:10);
 }
 private static boolean valid(Cast c){return c.owner.isAlive()&&!c.owner.isRemoved()&&!c.owner.isSpectator()&&c.owner.level()==c.level
   &&c.owner.getServer().getPlayerList().getPlayer(c.owner.getUUID())==c.owner;}
 private static void aim(Cast c){
  c.origin=origin(c.owner);c.direction=c.owner.getLookAngle().normalize();
  var muzzle=c.level.clip(new ClipContext(c.owner.getEyePosition(),c.origin,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,c.owner));
  if(muzzle.getType()!=HitResult.Type.MISS){c.origin=muzzle.getLocation();c.path=List.of(p(c.origin));c.length=0;return;}
  c.path=BeamPath.clip(c.steering.update(p(c.origin),p(c.direction),c.fired),(a,b)->{
   var hit=c.level.clip(new ClipContext(vec(a),vec(b),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,c.owner));
   return hit.getType()==HitResult.Type.MISS?null:p(hit.getLocation());
  });
  c.length=BeamPath.length(c.path);
  if(c.path.size()>1)c.direction=vec(c.path.get(1).sub(c.path.get(0))).normalize();
 }
 @SubscribeEvent public static void tick(ServerTickEvent event){
  if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
  var casts=CASTS.get(event.getServer());if(casts==null)return;
  for(var it=casts.values().iterator();it.hasNext();){
   Cast c=it.next();int age=c.age();boolean held=c.owner.isUsingItem()&&c.owner.getUsedItemHand()==c.hand
    &&ItemStack.isSameItemSameTags(c.bound,c.owner.getUseItem());
   if(!valid(c)||c.clashId==0&&(!held||DomainFreeze.hold(c.owner))||c.clashAge()>=SustainedBeam.CLASH_END){stop(c);it.remove();continue;}
   if(c.clashId!=0){
    if(c.clashAge()==SustainedBeam.CLASH_TICKS&&c.kind==SustainedBeam.LOVE)
     c.level.playSound(null,c.contact.x,c.contact.y,c.contact.z,SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,2.8f,.65f);
    if(age%3==0)c.send(age);continue;
   }
   aim(c);
   if(!c.fired&&SustainedBeam.firing(age,held)){
    c.fired=true;c.send(age);c.level.playSound(null,c.owner.blockPosition(),SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,1.5f,.85f);
   }
   if(age%2==0)c.send(age);
  }
  // Find contacts before applying damage: nothing behind a new clash is hit.
  List<Cast> beams=casts.values().stream().filter(c->c.fired&&c.clashId==0).sorted(Comparator.comparingInt(c->c.session)).toList();
  record Pair(Cast a,Cast b,SustainedBeam.Contact contact){}
  List<Pair> pairs=new ArrayList<>();
  for(int i=0;i<beams.size();i++)for(int j=i+1;j<beams.size();j++){
   Cast a=beams.get(i),b=beams.get(j);if(a.level!=b.level||a.kind==b.kind)continue;
   var hit=BeamPath.contact(live(a),live(b),(pa,pb)->clearBetween(a,vec(pa),vec(pb)));
   if(hit!=null)pairs.add(new Pair(a,b,hit));
  }
  pairs.sort(Comparator.comparingDouble(p->p.contact.alongA()+p.contact.alongB()));
  for(Pair pair:pairs){
   Cast a=pair.a,b=pair.b;if(a.clashId!=0||b.clashId!=0)continue;
   Vec3 center=vec(pair.contact.point());int id=SERIAL.incrementAndGet();
   var pathA=BeamPath.prefix(a.path,pair.contact.alongA());var pathB=BeamPath.prefix(b.path,pair.contact.alongB());
   for(Cast c:List.of(a,b)){
    c.clashId=id;c.clashStart=event.getServer().getTickCount();c.contact=center;c.otherOrigin=c==a?b.origin:a.origin;
    c.path=c==a?pathA:pathB;c.otherPath=c==a?pathB:pathA;c.length=BeamPath.length(c.path);c.send(c.age());
    TechniqueGestures.releaseCurrent(c.owner,c.gesture());c.owner.stopUsingItem();
    c.owner.getCooldowns().addCooldown(c.bound.getItem(),100);
   }
   a.level.playSound(null,center.x,center.y,center.z,SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,1.7f,1.2f);
  }
  for(Cast c:beams)if(c.clashId==0&&c.age()%SustainedBeam.HIT_INTERVAL==0)hit(c);
  if(casts.isEmpty())CASTS.remove(event.getServer());
 }
 private static List<SustainedBeam.P> live(Cast c){return BeamPath.prefix(c.path,SustainedBeam.reach(c.age(),c.length));}
 private static boolean clearBetween(Cast c,Vec3 from,Vec3 to){return c.level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,c.owner)).getType()==HitResult.Type.MISS;}
 private static SustainedBeam.P p(Vec3 v){return new SustainedBeam.P(v.x,v.y,v.z);}
 private static Vec3 vec(SustainedBeam.P p){return new Vec3(p.x(),p.y(),p.z());}
 private static void hit(Cast c){
  double reach=SustainedBeam.reach(c.age(),c.length);if(reach<=0)return;
  var path=live(c);if(path.size()<2)return;
  AABB box=new AABB(c.origin,c.origin);for(var point:path)box=box.minmax(new AABB(vec(point),vec(point)));box=box.inflate(SustainedBeam.RADIUS+2);
  String type=c.kind==SustainedBeam.LOVE?"love_beam":"maximum_uzumaki";
  DamageSource source=new DamageSource(c.level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(
   ResourceKey.create(Registries.DAMAGE_TYPE,new ResourceLocation(JujutsuVfx.ID,type))),c.owner);
  for(LivingEntity target:c.level.getEntitiesOfClass(LivingEntity.class,box,t->TechniquePlayerRules.canAffect(c.owner,t))){
   Vec3 center=target.getBoundingBox().getCenter();var nearest=BeamPath.closest(path,p(center));
   if(nearest.along()<.1||nearest.distance()>SustainedBeam.RADIUS+target.getBbWidth()*.5)continue;
   if(!clearBetween(c,vec(nearest.point()),center))continue;
   if(TechniqueDamageRules.hurt(target,source,30,c.kind==SustainedBeam.LOVE?TechniqueDamage.LOVE_BEAM:TechniqueDamage.UZUMAKI)&&!DomainFreeze.hold(target)){
    target.setDeltaMovement(target.getDeltaMovement().add(vec(nearest.tangent()).scale(.18)));target.hurtMarked=true;
   }
  }
 }
 @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event){
  if(event.getEntity() instanceof ServerPlayer observer&&event.getTarget() instanceof ServerPlayer player){
   Cast c=find(player);if(c!=null&&c.level==observer.level())c.state(c.age()).send(observer);
  }
 }
 @SubscribeEvent public static void stopped(ServerStoppedEvent event){CASTS.remove(event.getServer());}
}

package dev.jujutsuvfx.mixin;
import dev.jujutsuvfx.entity.LivingDanceState;
import dev.jujutsuvfx.server.LivingDance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntity.class)
abstract class LivingDanceStateMixin implements LivingDanceState {
 @Unique private static final EntityDataAccessor<CompoundTag> JUJUTSU_DANCE=SynchedEntityData.defineId(LivingEntity.class,EntityDataSerializers.COMPOUND_TAG);
 @Inject(method="defineSynchedData",at=@At("TAIL"))
 private void jujutsu$define(CallbackInfo ci){((LivingEntity)(Object)this).getEntityData().define(JUJUTSU_DANCE,new CompoundTag());}
 public CompoundTag jujutsu$dance(){return ((LivingEntity)(Object)this).getEntityData().get(JUJUTSU_DANCE);}
 public void jujutsu$dance(CompoundTag state){((LivingEntity)(Object)this).getEntityData().set(JUJUTSU_DANCE,state);}
 @Inject(method="travel",at=@At("HEAD"),cancellable=true)
 private void jujutsu$stay(Vec3 motion,CallbackInfo ci){LivingEntity e=(LivingEntity)(Object)this;if(LivingDance.active(e)){LivingDance.hold(e);ci.cancel();}}
 @Inject(method="tick",at=@At("TAIL"))
 private void jujutsu$hold(CallbackInfo ci){LivingDance.hold((LivingEntity)(Object)this);}
}

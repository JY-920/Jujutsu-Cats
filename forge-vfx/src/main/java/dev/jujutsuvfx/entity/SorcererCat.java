package dev.jujutsuvfx.entity;

import dev.jujutsuvfx.item.VfxItems;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.core.CatCombatMoves;
import dev.jujutsuvfx.server.SorcererCatCombat;
import dev.jujutsuvfx.server.CatDuel;
import dev.jujutsuvfx.server.CatSorcery;
import dev.jujutsuvfx.server.CatAdaptation;
import dev.jujutsuvfx.network.FistImpact;
import dev.jujutsuvfx.network.FistStrike;
import dev.jujutsuvfx.platform.PacketDistributor;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import dev.jujutsuvfx.server.DomainFreeze;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Objects;

/** An independent sorcerer with cat-shaped locomotion, not vanilla Cat AI or social behavior. */
public abstract class SorcererCat extends TamableAnimal {
    public static final int BLINDFOLD_TICKS = 30, TRANSFORMATION_TICKS = 60;
    public static final int NORMAL = 0, LIFT_BLINDFOLD = 1, NO_BLINDFOLD = 2;
    public static final int STILL = 0, WANDER = 1, AGGRESSIVE = 2;
    public static final int LEFT = 1, RIGHT = 2, BOTH = 3;
    public static final int PLAIN = 0, BLUE = 1, RED = 2, CURSED = 3, BLACK_FLASH = 4;
    public static final double BASE_HEALTH = 800;
    public static final int RIVAL_RANGE=128;
    private static final EntityDataAccessor<Integer> APPEARANCE = dataInt();
    private static final EntityDataAccessor<Float> BLINDFOLD = SynchedEntityData.defineId(SorcererCat.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> TRANSFORMATION = dataInt();
    private static final EntityDataAccessor<Boolean> BROKEN_ARM = SynchedEntityData.defineId(SorcererCat.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> BLUE_HANDS = dataInt(), RED_HANDS = dataInt(), BEHAVIOR = dataInt();
    private static final EntityDataAccessor<Integer> CURSED_HANDS = dataInt(), BLACK_FLASH_HANDS = dataInt();
    private static final EntityDataAccessor<Integer> BATTLE_HANDS = dataInt(), READY_HAND = dataInt();
    private static final EntityDataAccessor<Boolean> AUTOMATIC = SynchedEntityData.defineId(SorcererCat.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<CompoundTag> STILL_POSE = SynchedEntityData.defineId(SorcererCat.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Long> ACTION = SynchedEntityData.defineId(SorcererCat.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> WHEEL = SynchedEntityData.defineId(SorcererCat.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> WHEEL_TICKS = dataInt();
    private static final EntityDataAccessor<Integer> WHEEL_TURN_SEQUENCE = dataInt();
    private static final EntityDataAccessor<CompoundTag> ADAPTATION = SynchedEntityData.defineId(SorcererCat.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Integer> CAMERA_TARGET = dataInt();
    private static final EntityDataAccessor<Boolean> CINEMATIC_SLOW = SynchedEntityData.defineId(SorcererCat.class, EntityDataSerializers.BOOLEAN);
    private final dev.jujutsuvfx.core.ActorTime cinematicTime = new dev.jujutsuvfx.core.ActorTime();
    private float animationTime,previousAnimationTime;
    public float cinematicRate(){return entityData.get(CINEMATIC_SLOW)?.25f:1f;}
    public boolean cinematicStep(long tick,boolean slow){
        entityData.set(CINEMATIC_SLOW,slow);return cinematicTime.step(tick,slow);
    }
    public float animationTime(float partial){return Mth.lerp(partial,previousAnimationTime,animationTime);}
    private boolean forcedDeath, resumeLastStand;
    private static final EntityDataAccessor<Boolean> FINALE_WARD = SynchedEntityData.defineId(SorcererCat.class, EntityDataSerializers.BOOLEAN);
    public boolean finaleWard(){return entityData.get(FINALE_WARD);}
    public void finaleWard(boolean active){if(!level().isClientSide){entityData.set(FINALE_WARD,active);
        if(active)DomainFreeze.releaseFinaleActor(this);}}
    public void clearCatHostility(){
        if(getTarget() instanceof SorcererCat)setTarget(null);
        if(knownAttacker() instanceof SorcererCat){provokedEntity=null;provokedUntil=0;}
        if(getLastHurtByMob() instanceof SorcererCat)setLastHurtByMob(null);
    }
    @Override public void kill(){
        forcedDeath=true;resumeLastStand=false;
        try{dev.jujutsuvfx.server.GojoLastStand.cancel(this);CatDuel.cancelFor(this);CatSorcery.cancelAll(this);
            finaleWard(false);super.kill();}
        finally{forcedDeath=false;}
    }
    @Override public void die(DamageSource source){
        if(!level().isClientSide&&!forcedDeath&&!source.is(DamageTypes.GENERIC_KILL)
                &&!CatDuel.finaleDamage(this,source)&&dev.jujutsuvfx.server.GojoLastStand.begin(this,source))return;
        dev.jujutsuvfx.server.GojoLastStand.cancel(this);finaleWard(false);super.die(source);
    }
    private static final EntityDataAccessor<Boolean> BROKEN_LEFT_ARM = SynchedEntityData.defineId(SorcererCat.class, EntityDataSerializers.BOOLEAN);
    private java.util.UUID summoner;
    private int wheelVisualSequence=-1, wheelVisualDuration, adaptationVisualRevision=-1;
    private long wheelVisualStart, adaptationVisualStart=-1000;
    private float wheelVisualAngle;
    private final CatAdaptation adaptation;
    private static final ResourceKey<DamageType> COMBO_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(JujutsuVfx.ID, "sorcerer_combo"));
    private float previousBlindfold, visualAge, previousAge, transitionAge, transitionTicks = 3, previousTransitionTicks = 3;
    private long visualAction = -1, previousAction = -1;
    private int previousTransformation = TRANSFORMATION_TICKS;
    private final SorcererCatCombat combat;
    private boolean noAiBeforeTransformation;
    private java.util.UUID provokedEntity;
    private long provokedUntil;
    private ServerBossEvent playerBoss;
    private boolean lowHealthDomainUsed;
    private boolean playerDomainUsed, firstDomainUncovered;
    private int domainRevealRemaining;
    private final java.util.LinkedHashMap<java.util.UUID,Integer> duelHistory=new java.util.LinkedHashMap<>();
    private final java.util.LinkedHashMap<java.util.UUID,Float> duelProgress=new java.util.LinkedHashMap<>();
    public float duelProgress(java.util.UUID opponent) { return duelProgress.getOrDefault(opponent,0f); }
    public void rememberDuelProgress(java.util.UUID opponent,float progress) {
        float value=dev.jujutsuvfx.core.DuelProgress.bounded(progress);
        if(value<=duelProgress(opponent))return;
        duelProgress.put(opponent,value);duelHistory.putIfAbsent(opponent,0);trimDuelHistory();setPersistenceRequired();
    }
    private void trimDuelHistory(){
        while(duelHistory.size()>32){var oldest=duelHistory.keySet().iterator().next();duelHistory.remove(oldest);duelProgress.remove(oldest);}
    }
    private void saveDuelHistory(CompoundTag tag){
        net.minecraft.nbt.ListTag history=new net.minecraft.nbt.ListTag();
        duelHistory.forEach((id,flags)->{CompoundTag entry=new CompoundTag();entry.putUUID("Opponent",id);
            entry.putInt("Scenes",flags);entry.putFloat("Progress",duelProgress(id));history.add(entry);});
        tag.put("SorcererDuels",history);
    }
    private void loadDuelHistory(CompoundTag tag){
        duelHistory.clear();duelProgress.clear();var history=tag.getList("SorcererDuels",10);
        for(int i=0;i<Math.min(32,history.size());i++){CompoundTag entry=history.getCompound(i);
            if(entry.hasUUID("Opponent")){var id=entry.getUUID("Opponent");duelHistory.put(id,entry.getInt("Scenes")&0xffff);
                duelProgress.put(id,dev.jujutsuvfx.core.DuelProgress.bounded(entry.getFloat("Progress")));}}
    }
    public boolean duelScenePlayed(java.util.UUID opponent,int flag) { return (duelHistory.getOrDefault(opponent,0)&flag)!=0; }
    public void rememberDuelScene(java.util.UUID opponent,int flag) {
        duelHistory.put(opponent,duelHistory.getOrDefault(opponent,0)|(flag&0xffff));
        trimDuelHistory();
        setPersistenceRequired();
    }
    public boolean duelPlayed(java.util.UUID opponent,boolean finish) { return (duelHistory.getOrDefault(opponent,0)&(finish?2:1))!=0; }
    public void rememberDuel(java.util.UUID opponent,boolean finish) {
        duelHistory.put(opponent,duelHistory.getOrDefault(opponent,0)|(finish?2:1));
        trimDuelHistory();
    }

    private static EntityDataAccessor<Integer> dataInt() {
        return SynchedEntityData.defineId(SorcererCat.class, EntityDataSerializers.INT);
    }

    public SorcererCat(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        xpReward = 50;
        combat = new SorcererCatCombat(this);
        adaptation = new CatAdaptation(this);
        for(var pathType:new net.minecraft.world.level.pathfinder.BlockPathTypes[]{
                net.minecraft.world.level.pathfinder.BlockPathTypes.WATER,net.minecraft.world.level.pathfinder.BlockPathTypes.LAVA,
                net.minecraft.world.level.pathfinder.BlockPathTypes.WATER_BORDER,net.minecraft.world.level.pathfinder.BlockPathTypes.DAMAGE_FIRE,
                net.minecraft.world.level.pathfinder.BlockPathTypes.DANGER_FIRE,net.minecraft.world.level.pathfinder.BlockPathTypes.DAMAGE_OTHER,
                net.minecraft.world.level.pathfinder.BlockPathTypes.DANGER_OTHER,net.minecraft.world.level.pathfinder.BlockPathTypes.POWDER_SNOW})
            setPathfindingMalus(pathType,-1);
    }

    /** Replicates the actual AI target for observers; it grants no client control over targeting. */
    @Override public void setTarget(LivingEntity target) {
        if(target!=null&&narrativeAlly(target))target=null;
        super.setTarget(target);
        if(!level().isClientSide) {
            LivingEntity actual=getTarget();
            entityData.set(CAMERA_TARGET,actual==null?-1:actual.getId());
        }
    }
    public LivingEntity cameraTarget() {
        if(!level().isClientSide)return getTarget();
        var target=level().getEntity(entityData.get(CAMERA_TARGET));
        return target instanceof LivingEntity living&&living.isAlive()?living:null;
    }

    public abstract boolean isSukuna();
    public boolean isMahoraga() { return false; }
    public final boolean isGojo() { return !isSukuna() && !isMahoraga(); }
    public boolean hasWheel() { return isMahoraga() || entityData.get(WHEEL); }
    public boolean attachWheel() {
        if (level().isClientSide || !isSukuna() || hasWheel()) return false;
        entityData.set(WHEEL, true); return true;
    }
    public boolean detachWheel() {
        if(level().isClientSide||!isSukuna()||!entityData.get(WHEEL))return false;
        entityData.set(WHEEL,false);entityData.set(WHEEL_TICKS,0);setPersistenceRequired();return true;
    }
    public java.util.UUID summoner() { return summoner; }
    public void setSummoner(java.util.UUID owner) {
        if(level().isClientSide||!isMahoraga())return;
        summoner=owner;
        if(getTarget()!=null&&narrativeAlly(getTarget()))setTarget(null);
        setPersistenceRequired();
    }
    /** Summoned Mahoraga and its summoner share hostility exclusions in both directions. */
    public boolean narrativeAlly(net.minecraft.world.entity.Entity other) {
        return other!=null&&(summoner!=null&&summoner.equals(other.getUUID())
                ||other instanceof SorcererCat cat&&cat.summoner!=null&&cat.summoner.equals(getUUID()));
    }
    @Override public boolean isAlliedTo(net.minecraft.world.entity.Entity other) {
        return narrativeAlly(other)||super.isAlliedTo(other);
    }
    public CatAdaptation adaptation() { return adaptation; }
    public CompoundTag adaptationView() { return entityData.get(ADAPTATION).copy(); }
    public void publishAdaptation(CompoundTag state) { if (!level().isClientSide) entityData.set(ADAPTATION,state); }
    public float adaptationFlash(long visualTick, float partial) {
        if(!isMahoraga())return 0;
        int revision=entityData.get(ADAPTATION).getInt("Revision");
        if(revision!=adaptationVisualRevision){
            if(adaptationVisualRevision>=0)adaptationVisualStart=visualTick;
            adaptationVisualRevision=revision;
        }
        return Mth.clamp(1-(visualTick-adaptationVisualStart+partial)/16f,0,1);
    }
    public float wheelProgress(float partial) { return wheelProgress(partial,tickCount); }
    public float wheelProgress(float partial, long visualTick) {
        if(!isMahoraga())return 0;
        int next=entityData.get(WHEEL_TURN_SEQUENCE);
        float elapsed=Mth.clamp(visualTick-wheelVisualStart+partial,0,wheelVisualDuration);
        if(next!=wheelVisualSequence){
            wheelVisualAngle=(wheelVisualAngle+elapsed*18)%360;
            wheelVisualStart=visualTick;
            wheelVisualDuration=dev.jujutsuvfx.platform.Numbers.clamp(entityData.get(WHEEL_TICKS),0,20);
            wheelVisualSequence=next;elapsed=Mth.clamp(partial,0,wheelVisualDuration);
        }
        return ((wheelVisualAngle+elapsed*18)%360+360)%360/360f;
    }
    public boolean wheelTurning() { return isMahoraga()&&entityData.get(WHEEL_TICKS)>0; }
    public void tickWheelAnimation() {
        if(!level().isClientSide&&wheelTurning())entityData.set(WHEEL_TICKS,entityData.get(WHEEL_TICKS)-1);
    }
    public void turnWheel() {
        if(!level().isClientSide&&isMahoraga()){
            entityData.set(WHEEL_TICKS,20);
            entityData.set(WHEEL_TURN_SEQUENCE,entityData.get(WHEEL_TURN_SEQUENCE)+1);
            CatAdaptation.track(this);
        }
    }
    protected int defaultCursedHands() { return 0; }
    protected int defaultBlackFlashHands() { return 0; }
    protected float punchDamageMultiplier(int kind) { return kind == BLACK_FLASH ? 4 : kind == RED ? 2 : kind == BLUE ? 1.5f : 1; }
    protected abstract void onPunchHit(ServerLevel level, LivingEntity target, Vec3 direction, int kind);

    protected void cursedHit(ServerLevel level, LivingEntity target, Vec3 direction, int kind) {
        if (kind != CURSED && kind != BLACK_FLASH) return;
        if (kind == BLACK_FLASH) launchTarget(level, target, direction, .6, .24);
        Vec3 point = target.getBoundingBox().getCenter().subtract(direction.scale(target.getBbWidth() * .4));
        var packet = new FistStrike(new FistImpact(getId(), target.getId(), point, direction,
                kind == BLACK_FLASH ? 1 : .8f), kind == BLACK_FLASH);
        for (var observer : level.players()) if (dev.jujutsuvfx.server.CinematicAudio.visualRange(observer,point,160)
                && dev.jujutsuvfx.platform.PacketDistributor.hasChannel(observer.connection,FistStrike.TYPE)) PacketDistributor.sendToPlayer(observer, packet);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return net.minecraft.world.entity.Mob.createMobAttributes().add(Attributes.MAX_HEALTH, BASE_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, .32).add(Attributes.FOLLOW_RANGE, RIVAL_RANGE)
                .add(Attributes.ATTACK_DAMAGE, 12).add(Attributes.KNOCKBACK_RESISTANCE, .45);
    }

    @Override public double getAttributeValue(Holder<Attribute> attribute) {
        return getAttributeValue(attribute.value());
    }
    @Override public double getAttributeValue(Attribute attribute) {
        if(attribute.equals(Attributes.FOLLOW_RANGE))return Math.max(RIVAL_RANGE,super.getAttributeValue(attribute));
        if (!attribute.equals(Attributes.MAX_HEALTH)) return super.getAttributeValue(attribute);
        // Vanilla's shared health attribute clamps at 1024. Evaluate this entity's own
        // modifiers with the same operations, without raising the limit for all other mobs.
        var instance = getAttribute(attribute);
        if (instance == null) return BASE_HEALTH;
        double additive = 0, multipliedBase = 0, multipliedTotal = 1;
        for (AttributeModifier modifier : instance.getModifiers()) {
            switch (modifier.getOperation()) {
                case ADDITION -> additive += modifier.getAmount();
                case MULTIPLY_BASE -> multipliedBase += modifier.getAmount();
                case MULTIPLY_TOTAL -> multipliedTotal *= 1 + modifier.getAmount();
            }
        }
        double value = (instance.getBaseValue() + additive) * (1 + multipliedBase) * multipliedTotal;
        return Double.isFinite(value) ? dev.jujutsuvfx.platform.Numbers.clamp(value, 1, 1_000_000) : BASE_HEALTH;
    }

    @Override protected float getStandingEyeHeight(net.minecraft.world.entity.Pose pose,net.minecraft.world.entity.EntityDimensions dimensions) {
        return dimensions.height*.768f;
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(APPEARANCE, NORMAL);
        entityData.define(BLINDFOLD, 0f);
        entityData.define(TRANSFORMATION, TRANSFORMATION_TICKS);
        entityData.define(BROKEN_ARM, false);
        entityData.define(BLUE_HANDS, 0); entityData.define(RED_HANDS, 0);
        entityData.define(CURSED_HANDS, defaultCursedHands()); entityData.define(BLACK_FLASH_HANDS, defaultBlackFlashHands());
        entityData.define(BATTLE_HANDS, -1); entityData.define(READY_HAND, -1); entityData.define(AUTOMATIC, true);
        entityData.define(BEHAVIOR, WANDER);
        entityData.define(STILL_POSE, new CompoundTag());
        entityData.define(ACTION, -1L);
        entityData.define(WHEEL, false); entityData.define(WHEEL_TICKS, 0);
        entityData.define(WHEEL_TURN_SEQUENCE,0);entityData.define(ADAPTATION,new CompoundTag());
        entityData.define(CAMERA_TARGET,-1);
        entityData.define(CINEMATIC_SLOW,false);
        entityData.define(BROKEN_LEFT_ARM,false);entityData.define(FINALE_WARD,false);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        goalSelector.addGoal(2, new CombatGoal());
        goalSelector.addGoal(5, new FollowOwnerGoal(this, 1, 7, 3, false));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, .8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(0,new HurtByTargetGoal(this){
            @Override public boolean canUse(){
                LivingEntity attacker=getLastHurtByMob();
                return trackableOpponent(attacker)&&super.canUse();
            }
            @Override public boolean canContinueToUse(){
                // HurtByTargetGoal otherwise expires behind a wall even though retaliation is still valid.
                return trackableOpponent(SorcererCat.this.getTarget());
            }
        });
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, SorcererCat.class, 5, false, false,
                target -> isRival(target) && trackableOpponent(target)){
            @Override public boolean canContinueToUse(){
                return trackableOpponent(SorcererCat.this.getTarget())&&super.canContinueToUse();
            }
            @Override protected net.minecraft.world.phys.AABB getTargetSearchArea(double range){
                return SorcererCat.this.getBoundingBox().inflate(range);
            }
        });
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, false, false,
                this::trackableOpponent){
            @Override protected net.minecraft.world.phys.AABB getTargetSearchArea(double range){
                return SorcererCat.this.getBoundingBox().inflate(range);
            }
            @Override public boolean canContinueToUse(){
                return trackableOpponent(SorcererCat.this.getTarget())&&super.canContinueToUse();
            }
        });
    }

    private boolean trackableOpponent(LivingEntity target){
        // Awareness is independent of visibility; canFight still enforces neutrality, alliances and mode.
        return canFight(target)&&distanceToSqr(target)<=RIVAL_RANGE*RIVAL_RANGE;
    }
    public boolean isRival(LivingEntity target) {
        return dev.jujutsuvfx.config.VfxConfig.cinematic() && !narrativeAlly(target) && target instanceof SorcererCat cat && (isMahoraga() != cat.isMahoraga() || !isMahoraga() && isSukuna() != cat.isSukuna());
    }
    public boolean canFight(LivingEntity target) {
        if (behavior() == STILL || isTransforming() || super.isNoAi() || !eligibleOpponent(target)) return false;
        if(target instanceof Player)return provokedBy(target);
        if (target instanceof SorcererCat cat) return (isRival(cat) || provokedBy(cat)) && !cat.isTransforming();
        return provokedBy(target)||dev.jujutsuvfx.config.VfxConfig.cinematic()&&behavior() == AGGRESSIVE;
    }
    private boolean eligibleOpponent(LivingEntity target){
        return target!=null&&target!=this&&target.level()==level()&&target.isAlive()&&!target.isRemoved()
                &&!target.isSpectator()&&!(target instanceof ArmorStand)&&!isAlliedTo(target)
                &&(!(target instanceof Player player)||!player.isCreative()&&!player.getAbilities().invulnerable)
                &&!(target instanceof TamableAnimal pet&&getOwnerUUID()!=null&&Objects.equals(getOwnerUUID(),pet.getOwnerUUID()));
    }
    private boolean provokedBy(LivingEntity target){
        return provokedEntity!=null&&provokedEntity.equals(target.getUUID())&&getServer()!=null
                &&getServer().getTickCount()<provokedUntil;
    }
    private LivingEntity knownAttacker(){
        return provokedEntity!=null&&level() instanceof ServerLevel level
                &&level.getEntity(provokedEntity) instanceof LivingEntity living?living:null;
    }
    public LivingEntity retaliationAttacker(){
        LivingEntity attacker=knownAttacker();
        return attacker!=null&&provokedBy(attacker)&&canFight(attacker)&&distanceToSqr(attacker)<=RIVAL_RANGE*RIVAL_RANGE?attacker:null;
    }
    /** Retain the player-only accessor for existing integrations. Combat uses retaliationAttacker(). */
    public Player retaliationTarget(){
        return retaliationAttacker() instanceof Player player?player:null;
    }

    private void refreshRetaliation(){
        LivingEntity current=getTarget();
        if(current!=null&&!trackableOpponent(current))setTarget(null);
        if(provokedEntity==null||getServer()==null)return;
        // Retain the known opponent across retreat, altitude and loss of line of sight.
        // The 30-second memory expires only after leaving the 128-block encounter.
        LivingEntity known=knownAttacker();
        if(known!=null&&!eligibleOpponent(known)){provokedEntity=null;provokedUntil=0;}
        else if(retaliationAttacker()!=null)provokedUntil=getServer().getTickCount()+600;
        else if(getServer().getTickCount()>=provokedUntil){provokedEntity=null;provokedUntil=0;}
        LivingEntity target=getTarget();
        if(target!=null&&(!canFight(target)||distanceToSqr(target)>RIVAL_RANGE*RIVAL_RANGE))setTarget(null);
    }
    public boolean needsPlayerDomain(){return !dev.jujutsuvfx.config.VfxConfig.cinematic()&&!dev.jujutsuvfx.server.GojoLastStand.active(this)&&!isMahoraga()&&getHealth()>0&&(!playerDomainUsed&&getHealth()<=getMaxHealth()*.5f||!lowHealthDomainUsed&&getHealth()<=getMaxHealth()*.1f);}
    public void markPlayerDomainUsed(){if(!playerDomainUsed)playerDomainUsed=true;else if(getHealth()<=getMaxHealth()*.1f)lowHealthDomainUsed=true;}

    private void updatePlayerBoss(){
        ServerPlayer target=getTarget() instanceof ServerPlayer player&&isAlive()&&!isNoAi()&&!isInSittingPose()
                &&player.level()==level()&&canFight(player)&&distanceToSqr(player)<=RIVAL_RANGE*RIVAL_RANGE
                &&getServer().getPlayerList().getPlayer(player.getUUID())==player?player:null;
        if(target==null){if(playerBoss!=null)playerBoss.removeAllPlayers();return;}
        if(playerBoss==null)playerBoss=new ServerBossEvent(getDisplayName(),isMahoraga()?BossEvent.BossBarColor.YELLOW:isSukuna()?BossEvent.BossBarColor.RED:BossEvent.BossBarColor.BLUE,
                BossEvent.BossBarOverlay.PROGRESS);
        for(ServerPlayer previous:java.util.List.copyOf(playerBoss.getPlayers()))if(previous!=target)playerBoss.removePlayer(previous);
        playerBoss.setName(getDisplayName());playerBoss.setProgress(Mth.clamp(getHealth()/getMaxHealth(),0,1));
        playerBoss.addPlayer(target);
    }
    @Override public void setHealth(float health){
        super.setHealth(health);
        // Only full recovery rearms both health thresholds for a new encounter.
        if(getHealth()>=getMaxHealth()*.98f){playerDomainUsed=false;lowHealthDomainUsed=false;}
        if(playerBoss!=null){
            playerBoss.setProgress(Mth.clamp(getHealth()/getMaxHealth(),0,1));
            if(getHealth()<=0)playerBoss.removeAllPlayers();
        }
    }
    @Override public void remove(net.minecraft.world.entity.Entity.RemovalReason reason){
        cancelLandingSmash();
        if(reason!=net.minecraft.world.entity.Entity.RemovalReason.KILLED&&dev.jujutsuvfx.server.GojoLastStand.active(this))resumeLastStand=true;
        if(!level().isClientSide)dev.jujutsuvfx.server.GojoLastStand.cancel(this);
        if(playerBoss!=null)playerBoss.removeAllPlayers();
        super.remove(reason);
    }
    @Override public void onAddedToWorld(){
        super.onAddedToWorld();if(!level().isClientSide&&adaptation!=null)adaptation.resume();
        if(!level().isClientSide&&resumeLastStand){resumeLastStand=false;kill();} // Never reload an immortal interrupted final cast.
    }
    @Override public void onRemovedFromWorld(){
        if(playerBoss!=null)playerBoss.removeAllPlayers();
        super.onRemovedFromWorld();
    }

    @Override public void tick() {
        if(!level().isClientSide&&isMahoraga()&&getPersistentData().getBoolean("JujutsuShadowGuest")&&!CatDuel.manages(this)){
            discard();return;
        }
        if(!level().isClientSide)refreshRetaliation();
        previousBlindfold = entityData.get(BLINDFOLD);
        previousTransformation = entityData.get(TRANSFORMATION);
        previousAge = visualAge; previousTransitionTicks = transitionTicks;
        if (level().isClientSide) {
            float rate=cinematicRate();previousAnimationTime=animationTime;animationTime+=rate;
            transitionTicks = Math.min(3, transitionTicks + rate);
            if (punching()) visualAge = Math.min(CatCombatMoves.duration(combatMove()),
                    Math.min(CatCombatMoves.frame(entityData.get(ACTION)) + 3, visualAge + rate));
        }
        if (behavior() == STILL) holdStill();
        if (isTransforming()) {
            setTarget(null); getNavigation().stop();
            setDeltaMovement(0, getDeltaMovement().y, 0);
        }
        if (!level().isClientSide && hoverLease > 0) {
            if (hoverLease == 1 || !isAlive() || isNoAi() || isInSittingPose()) releaseCombatHover();
            else { hoverLease--; fallDistance = 0; }
        }
        super.tick();
        if (behavior() == STILL) holdStill();
        if (!level().isClientSide) {
            if (isTransforming()) {
                int next = entityData.get(TRANSFORMATION) + 1;
                entityData.set(TRANSFORMATION, next);
                if (next == TRANSFORMATION_TICKS) finishTransformation();
            }
            float target = appearance() == LIFT_BLINDFOLD ? 1f : 0f;
            if(domainRevealRemaining==0)entityData.set(BLINDFOLD, Mth.approach(entityData.get(BLINDFOLD), target, 1f / BLINDFOLD_TICKS));
            tickLandingSmash();
            combat.tick();
            updatePlayerBoss();
        }
    }

    /** One actor-owned, saved reveal. Debug appearance changes remain available afterwards. */
    public boolean beginFirstDomainReveal() {
        if(level().isClientSide||!isGojo()||firstDomainUncovered)return false;
        firstDomainUncovered=true;
        if(appearance()==NO_BLINDFOLD)return false;
        setAppearance(LIFT_BLINDFOLD);domainRevealRemaining=BLINDFOLD_TICKS+8;return true;
    }
    public void advanceFirstDomainReveal() {
        if(level().isClientSide||domainRevealRemaining<=0)return;
        domainRevealRemaining--;
        float progress=Math.min(1,(BLINDFOLD_TICKS+8-domainRevealRemaining)/(float)BLINDFOLD_TICKS);
        entityData.set(BLINDFOLD,progress);
        if(domainRevealRemaining==0){entityData.set(APPEARANCE,NO_BLINDFOLD);}
    }
    public void finishFirstDomainReveal() {
        if(!level().isClientSide&&domainRevealRemaining>0){domainRevealRemaining=0;entityData.set(APPEARANCE,NO_BLINDFOLD);}
    }
    public int appearance() { return entityData.get(APPEARANCE); }
    public boolean brokenArm() { return entityData.get(BROKEN_ARM); }
    public boolean brokenLeftArm() { return entityData.get(BROKEN_LEFT_ARM); }
    public boolean handUnavailable(int hand) { return hand==LEFT?brokenLeftArm():hand==RIGHT?brokenArm():brokenLeftArm()&&brokenArm(); }
    public void setBrokenLeftArm(boolean broken) {
        if(level().isClientSide||!isGojo())return;
        entityData.set(BROKEN_LEFT_ARM,broken);cancelPunch();setPersistenceRequired();
        if(broken)combat.forceReverseRecovery();
    }
    public boolean forceReverseRecovery() { return combat.forceReverseRecovery(); }
    public void regrowLeftArm() {
        if(!level().isClientSide&&isGojo()&&brokenLeftArm()){
            entityData.set(BROKEN_LEFT_ARM,false);setPersistenceRequired();
        }
    }
    public int blueHands() { return entityData.get(BLUE_HANDS); }
    public int redHands() { return entityData.get(RED_HANDS); }
    public int behavior() { return entityData.get(BEHAVIOR); }
    public int cursedHands() { return entityData.get(CURSED_HANDS); }
    public int blackFlashHands() { return entityData.get(BLACK_FLASH_HANDS); }
    public boolean automaticEnergy() { return entityData.get(AUTOMATIC); }
    public int configuredHands(int kind) {
        return switch (kind) { case BLUE -> blueHands(); case RED -> redHands(); case CURSED -> cursedHands(); case BLACK_FLASH -> blackFlashHands(); default -> 0; };
    }
    public boolean hasEnergy(int kind, int hand) {
        if (isMahoraga()) return false;
        if (handUnavailable(hand)) return false;
        int battle = entityData.get(BATTLE_HANDS);
        return automaticEnergy() && battle >= 0 ? ((battle >>> (hand == LEFT ? 0 : 3)) & 7) == kind
                : (configuredHands(kind) & hand) != 0;
    }
    public void prepareEnergy(int hand, int kind) {
        if (level().isClientSide || isMahoraga() || handUnavailable(hand)) return;
        int shift = hand == LEFT ? 0 : 3, previous = Math.max(0, entityData.get(BATTLE_HANDS));
        entityData.set(BATTLE_HANDS, previous & ~(7 << shift) | kind << shift);
        entityData.set(READY_HAND, kind << 2 | hand);
    }
    public int readyHand() { return entityData.get(READY_HAND) < 0 ? 0 : entityData.get(READY_HAND) & 3; }
    public int readyKind() { return entityData.get(READY_HAND) < 0 ? PLAIN : entityData.get(READY_HAND) >>> 2; }
    public void clearCombatEnergy() { entityData.set(BATTLE_HANDS, -1); entityData.set(READY_HAND, -1); }
    public void setAutomaticEnergy(boolean automatic) {
        if (!level().isClientSide) { entityData.set(AUTOMATIC, automatic); cancelPunch(); setPersistenceRequired(); }
    }
    public SorcererCatCombat combat() { return combat; }

    public void setAppearance(int state) {
        if (level().isClientSide || state < NORMAL || state > NO_BLINDFOLD || isTransforming() || !isGojo() && state != NORMAL) return;
        if (state == LIFT_BLINDFOLD) { entityData.set(BLINDFOLD, 0f); previousBlindfold = 0; }
        entityData.set(APPEARANCE, state);
        setPersistenceRequired();
    }
    public void setBrokenArm(boolean broken) {
        if (!level().isClientSide && !isMahoraga()) { entityData.set(BROKEN_ARM, broken); cancelPunch(); setPersistenceRequired(); }
    }
    public void setHands(int kind, int mask) {
        if (level().isClientSide || mask < 0 || mask > BOTH || kind < BLUE || kind > BLACK_FLASH
                || isMahoraga() || isSukuna() && (kind == BLUE || kind == RED)) return;
        EntityDataAccessor<Integer> channel = switch (kind) {
            case BLUE -> BLUE_HANDS; case RED -> RED_HANDS; case CURSED -> CURSED_HANDS; default -> BLACK_FLASH_HANDS;
        };
        entityData.set(channel, mask);
        if (!automaticEnergy()) cancelPunch();
        setPersistenceRequired();
    }
    public void setBehavior(int mode) {
        if (level().isClientSide || mode < STILL || mode > AGGRESSIVE) return;
        dev.jujutsuvfx.server.CatSorcery.cancelAll(this);
        cancelPunch(); getNavigation().stop(); setTarget(null); setDeltaMovement(Vec3.ZERO);
        if (mode == STILL) {
            stopRiding();
            CompoundTag pose = new CompoundTag();
            pose.putDouble("X", getX()); pose.putDouble("Y", getY()); pose.putDouble("Z", getZ());
            pose.putFloat("Yaw", getYRot()); pose.putFloat("Pitch", getXRot());
            pose.putFloat("Head", yHeadRot); pose.putFloat("Body", yBodyRot);
            entityData.set(STILL_POSE, pose);
        } else { setNoAi(false); setOrderedToSit(false); setInSittingPose(false); }
        entityData.set(BEHAVIOR, mode);
        setPersistenceRequired();
    }
    private void holdStill() {
        CompoundTag pose = entityData.get(STILL_POSE);
        if (!pose.contains("X")) return;
        double x = pose.getDouble("X"), y = pose.getDouble("Y"), z = pose.getDouble("Z");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
        setPos(x, y, z); setYRot(pose.getFloat("Yaw")); setXRot(pose.getFloat("Pitch"));
        setOldPosAndRot(); yHeadRot = yHeadRotO = pose.getFloat("Head"); yBodyRot = yBodyRotO = pose.getFloat("Body");
        setDeltaMovement(Vec3.ZERO); fallDistance = 0;
    }
    @Override public boolean isNoAi() { return super.isNoAi() || behavior() == STILL || isTransforming(); }
    @Override public boolean isPushable() { return behavior() != STILL && !CatDuel.pinned(this) && super.isPushable(); }
    @Override public void move(net.minecraft.world.entity.MoverType type,Vec3 step){
        super.move(type,dev.jujutsuvfx.server.CatSpacing.limit(this,step));
    }
    @Override public void travel(Vec3 input) { if (behavior() == STILL) holdStill(); else if(CatDuel.pinned(this))setDeltaMovement(Vec3.ZERO); else super.travel(input); }
    @Override public void knockback(double strength, double x, double z) { if (behavior() != STILL && !receivingCombo && !combat.guarding()) super.knockback(strength, x, z); }
    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return null; }
    @Override protected SoundEvent getDeathSound() { return null; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean isFood(ItemStack stack) { return false; }
    @Override public boolean canMate(Animal other) { return false; }
    @Override public SorcererCat getBreedOffspring(ServerLevel level, AgeableMob other) { return null; }

    public boolean punching() { return combatMove() != CatCombatMoves.IDLE; }
    public int combatMove() { return CatCombatMoves.move(entityData.get(ACTION)); }
    public int punchHand() { return CatCombatMoves.hand(entityData.get(ACTION)); }
    public int punchKind() { return CatCombatMoves.kind(entityData.get(ACTION)); }
    public long previousCombatAction() { return previousAction; }
    public float previousCombatAge() { return transitionAge; }
    public float combatBlend(float partial) { return Mth.clamp(Mth.lerp(partial, previousTransitionTicks, transitionTicks) / 3, 0, 1); }
    public float punchAge(float partial) { return Mth.lerp(Mth.clamp(partial, 0, 1), previousAge, visualAge); }
    public void publishCombat(long action) { if (!level().isClientSide) entityData.set(ACTION, action); }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == ACTION && level().isClientSide) {
            long next = entityData.get(ACTION);
            if (CatCombatMoves.sequence(next) != CatCombatMoves.sequence(visualAction)) {
                previousAction = visualAction; transitionAge = visualAge;
                transitionTicks = previousTransitionTicks = 0;
                visualAge = previousAge = CatCombatMoves.frame(next);
            } else if (CatCombatMoves.frame(next) > visualAge) {
                previousAge = visualAge; visualAge = CatCombatMoves.frame(next);
            }
            visualAction = next;
        }
    }
    // Only an accepted downward combat hit can arm this short, unsaved landing effect.
    private static final class LandingSmash {
        final DamageSource source;final Level world;final Vec3 start;final long until;
        Vec3 previous;
        LandingSmash(DamageSource source,Level world,Vec3 start,long until){
            this.source=source;this.world=world;this.start=this.previous=start;this.until=until;
        }
    }
    private LandingSmash landingSmash;
    public boolean hasPendingLandingSmash(){return landingSmash!=null;}
    public void cancelLandingSmash(){landingSmash=null;}
    private static boolean aerialImpactTarget(LivingEntity target){
        return target instanceof SorcererCat cat&&!cat.onGround()&&!cat.isInWaterOrBubble()&&!cat.isInLava()
                &&cat.isAlive()&&!cat.isRemoved()&&!cat.isNoAi()&&!cat.isInSittingPose()
                &&cat.behavior()!=STILL&&!DomainFreeze.hold(cat)&&!CatDuel.pinned(cat);
    }
    /** Called after the accepted kick has released the victim's flight lease and committed its downward impulse. */
    public void armLandingSmash(SorcererCat attacker,int kind){
        if(!(level() instanceof ServerLevel level)||attacker==null||attacker==this||attacker.level()!=level
                ||!attacker.isAlive()||!aerialImpactTarget(this)||isPassenger()||isNoGravity())return;
        DamageSource source=new dev.jujutsuvfx.server.CatMeleeDamage(attacker.damageSources().mobAttack(attacker).typeHolder(),attacker,kind);
        if(combat.blocks(source)||isInvulnerableTo(source))return;
        landingSmash=new LandingSmash(source,level,position(),level.getServer().getTickCount()+80);
    }
    /** Natural falls are harmless. A downward combat kick already pays its full damage at contact. */
    @Override public boolean causeFallDamage(float distance,float multiplier,DamageSource source){fallDistance=0;return false;}
    private void tickLandingSmash(){
        LandingSmash smash=landingSmash;if(smash==null||!(level() instanceof ServerLevel level))return;
        var attacker=smash.source.getEntity();Vec3 feet=position();
        if(level!=smash.world||level.getServer().getTickCount()>smash.until||!isAlive()||isRemoved()||isNoAi()
                ||isPassenger()||isNoGravity()||isInWaterOrBubble()||isInLava()||CatDuel.pinned(this)
                ||attacker==null||!attacker.isAlive()||attacker.isRemoved()||attacker.level()!=level
                ||!Double.isFinite(feet.x)||!Double.isFinite(feet.y)||!Double.isFinite(feet.z)
                ||feet.distanceToSqr(smash.previous)>64||feet.y>smash.previous.y+.35){landingSmash=null;return;}
        smash.previous=feet;
        if(!onGround())return;
        landingSmash=null;fallDistance=0; // one contact plume per accepted downward combat kick
        double drop=smash.start.y-feet.y;if(drop<.45||DomainFreeze.hold(this))return;
        // The kick already paid its complete 1.9x damage through hurt() at contact.
        // Landing only adds dust, so it never double-hits or circumvents that attack's
        // defense, adaptation, invulnerability window or cinematic health floor.
        var hit=level.clip(new net.minecraft.world.level.ClipContext(feet.add(0,.35,0),feet.add(0,-.65,0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,this));
        Vec3 impact=hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK?hit.getLocation():feet;
        double spread=Math.min(1.5,.48+drop*.045);int count=(int)Math.min(30,12+drop*1.4);
        level.sendParticles(ParticleTypes.CLOUD,impact.x,impact.y+.08,impact.z,count,spread,.10,spread,.045);
        level.sendParticles(ParticleTypes.SMOKE,impact.x,impact.y+.12,impact.z,8,spread*.65,.16,spread*.65,.018);
    }
    // All combat teleports share an actor-owned clock; resetting an attack must not reset it.
    private long combatBlinkReadyAt;
    public boolean canCombatBlink(){
        return !level().isClientSide&&isGojo()&&isAlive()&&!isNoAi()&&!isTransforming()
                &&getServer()!=null&&getServer().getTickCount()>=combatBlinkReadyAt;
    }
    public boolean tryCombatBlink(){
        if(!canCombatBlink())return false;
        combatBlinkReadyAt=(long)getServer().getTickCount()+160;return true;
    }
    // Combat flight is a short renewed lease, never a permanent saved NoGravity flag.
    private int hoverLease;
    private boolean gravityBeforeHover, receivingCombo;
    public boolean combatHovering() { return hoverLease > 0; }
    public void renewCombatHover() {
        if (level().isClientSide || !isAlive() || isNoAi() || isInSittingPose()) return;
        cancelLandingSmash();
        if (hoverLease == 0) gravityBeforeHover = isNoGravity();
        hoverLease = 12; setNoGravity(true); fallDistance = 0;
    }
    public void releaseCombatHover() {
        if (hoverLease > 0) { hoverLease = 0; setNoGravity(gravityBeforeHover); }
    }
    public void provokePlayer(net.minecraft.world.entity.Entity attacker){
        if(attacker instanceof Player)provokeAttacker(attacker);
    }
    public void provokeAttacker(net.minecraft.world.entity.Entity attacker){
        if(level().isClientSide||!isAlive()||isTransforming())return;
        if(attacker instanceof net.minecraft.world.entity.projectile.Projectile shot)attacker=shot.getOwner();
        if(!(attacker instanceof LivingEntity living)||!eligibleOpponent(living))return;
        // Rival cats already use their own hostility and duel choreography. A pet's
        // attack belongs to the pet itself, not to its otherwise neutral owner.
        if(living instanceof SorcererCat other && isRival(other))return;
        provokedEntity=living.getUUID();provokedUntil=getServer().getTickCount()+600;
        setLastHurtByMob(living);if(canFight(living))setTarget(living);
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        if(source.is(DamageTypes.GENERIC_KILL)){
            if(!level().isClientSide){dev.jujutsuvfx.server.GojoLastStand.cancel(this);CatDuel.cancelFor(this);CatSorcery.cancelAll(this);finaleWard(false);}
            return super.hurt(source,amount);
        }
        if(source.is(DamageTypes.FALL))return false;
        if (level().isClientSide) return super.hurt(source, amount);
        if(narrativeAlly(source.getEntity())||CatDuel.recoveryProtected(this,source))return false;
        boolean finale=CatDuel.finaleDamage(this,source);
        if(finaleWard()&&!finale)return false;
        if(amount>0){
            combat.attacked();
            provokeAttacker(source.getEntity()!=null?source.getEntity():source.getDirectEntity());
        }
        if (!finale && combat.infinityBlocks(source)) { combat.blockCue(source); return false; }
        if(amount>0&&!finale)combat.reactToPlayerHit(source);
        if (!finale && combat.infinityBlocks(source)) { combat.blockCue(source); return false; }
        boolean combo = source.is(COMBO_DAMAGE);
        if (!finale && combo && combat.barraging()) { combat.blockCue(source); return false; }
        boolean guard = !finale && combat.blocks(source);
        if (guard) { amount *= isSukuna() ? .30f : .70f; combat.blockCue(source); }
        if(!finale)amount *= adaptation.damageScale(source);
        amount=CatDuel.limitDamage(this,source,amount);
        if(amount<=0)return false;
        boolean before = receivingCombo; receivingCombo = combo;
        try {
            float healthBefore = getHealth(), absorptionBefore = getAbsorptionAmount();
            boolean accepted = super.hurt(source, amount);
            if (accepted && (getHealth() < healthBefore || getAbsorptionAmount() < absorptionBefore)) {
                turnWheel(); adaptation.damaged(source);
            }
            return accepted;
        }
        finally { receivingCombo = before; }
    }
    private void cancelPunch() { if (combat != null) combat.reset(); }
    public boolean performCombatHit(ServerLevel level, LivingEntity target, int kind, int move) {
        float multiplier = move == CatCombatMoves.RAPID ? .62f : move == CatCombatMoves.DIVE_KICK ? 1.9f : move == CatCombatMoves.KICK ? 1.4f : move == CatCombatMoves.DOUBLE_PALM ? 1.55f : move == CatCombatMoves.UPPERCUT ? 1.3f : move == CatCombatMoves.ELBOW ? 1.2f : move == CatCombatMoves.SWEEP ? 1.15f : move == CatCombatMoves.HOOK ? 1.15f : 1;
        if(CatCombatMoves.blade(move))multiplier=move==CatCombatMoves.BLADE_CHAIN?.60f:
                move==CatCombatMoves.BLADE_CHOP?1.35f:move==CatCombatMoves.BLADE_RISE?1.12f:1.05f;
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * punchDamageMultiplier(kind) * multiplier;
        // Rapid jabs bypass only the vanilla hit cooldown through their own damage type.
        // Never reset another attacker's invulnerability timer or bypass protection/immunity.
        Holder<DamageType> type=(move==CatCombatMoves.RAPID||move==CatCombatMoves.BLADE_CHAIN)?level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(COMBO_DAMAGE):damageSources().mobAttack(this).typeHolder();
        DamageSource source=new dev.jujutsuvfx.server.CatMeleeDamage(type,this,kind);
        Vec3 previousMotion = target.getDeltaMovement();
        boolean blocked=target instanceof SorcererCat other&&other.combat().blocks(source);
        if (!target.hurt(source, damage)) return false;
        Vec3 direction = target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter()).normalize();
        if (direction.lengthSqr() < 1e-8) direction = getLookAngle();
        playSound(SoundEvents.PLAYER_ATTACK_STRONG, .75f, kind == BLUE ? .7f : kind == RED || kind == BLACK_FLASH ? 1.3f : 1);
        if(CatCombatMoves.blade(move)) {
            // Blade contacts have their own silver-gold cue, never a cursed fist or shrine cut.
            if(move==CatCombatMoves.BLADE_CHAIN){
                target.setDeltaMovement(previousMotion);target.hasImpulse=true;target.hurtMarked=true;
                level.getChunkSource().broadcastAndSend(target,new ClientboundSetEntityMotionPacket(target));
            }else if(!blocked){
                launchTarget(level,target,direction,move==CatCombatMoves.BLADE_CHOP?.7:.32,
                        move==CatCombatMoves.BLADE_RISE?.30:.08);
            }
        } else if (move == CatCombatMoves.RAPID) {
            // Contact visuals remain, but attraction, hit knockback and Red/Black Flash launch
            // must not carry either fighter out of the continuous exchange.
            target.setDeltaMovement(previousMotion); target.hasImpulse = true; target.hurtMarked = true;
            level.getChunkSource().broadcastAndSend(target, new ClientboundSetEntityMotionPacket(target));
            comboImpact(level, target, direction, kind);
        } else onPunchHit(level, target, direction, kind);
        if (!blocked && (move == CatCombatMoves.UPPERCUT || move == CatCombatMoves.DOUBLE_PALM || move == CatCombatMoves.SWEEP)) {
            if (target instanceof SorcererCat other) other.combat().stagger(move == CatCombatMoves.DOUBLE_PALM ? 7 : 4);
            Vec3 push = move == CatCombatMoves.SWEEP ? new Vec3(direction.z, 0, -direction.x).scale(punchHand() == LEFT ? 1 : -1).add(direction.scale(.35)).normalize() : direction;
            launchTarget(level, target, push, move == CatCombatMoves.DOUBLE_PALM ? 1.65 : move == CatCombatMoves.SWEEP ? 1.1 : .3,
                    move == CatCombatMoves.UPPERCUT ? .85 : .18);
        }
        if (move == CatCombatMoves.KICK) {
            if(!blocked&&target instanceof SorcererCat other)other.combat().stagger(9);
            launchTarget(level, target, direction, 2.0, .72);
        }
        return true;
    }
    private void comboImpact(ServerLevel level, LivingEntity target, Vec3 direction, int kind) {
        if (isMahoraga() || kind == PLAIN) {
            Vec3 hit = target.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.CRIT, hit.x, hit.y, hit.z, 3, .15, .18, .15, .03); return;
        }
        Vec3 point = target.getBoundingBox().getCenter().subtract(direction.scale(target.getBbWidth() * .4));
        FistImpact impact = new FistImpact(getId(), target.getId(), point, direction, .65f);
        if (kind == BLUE || kind == RED) {
            var packet = new dev.jujutsuvfx.network.TechniqueFistImpact(impact, kind == RED);
            for (var player : level.players()) if (dev.jujutsuvfx.server.CinematicAudio.visualRange(player,point,160) && dev.jujutsuvfx.platform.PacketDistributor.hasChannel(player.connection,packet.type()))
                PacketDistributor.sendToPlayer(player, packet);
        } else {
            var packet = new FistStrike(impact, kind == BLACK_FLASH);
            for (var player : level.players()) if (dev.jujutsuvfx.server.CinematicAudio.visualRange(player,point,160) && dev.jujutsuvfx.platform.PacketDistributor.hasChannel(player.connection,packet.type()))
                PacketDistributor.sendToPlayer(player, packet);
        }
    }
    protected void launchTarget(ServerLevel level, LivingEntity target, Vec3 direction, double horizontalSpeed, double lift) {
        if (!target.isAlive() || DomainFreeze.hold(target) || CatDuel.pinned(target)
                || target instanceof SorcererCat cat && cat.behavior() == STILL) return;
        double resistance = 1 - dev.jujutsuvfx.platform.Numbers.clamp(target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0, 1);
        if (target instanceof SorcererCat guarding && guarding.combat().guarding()) resistance *= .18;
        Vec3 horizontal = new Vec3(direction.x, 0, direction.z).normalize();
        target.setDeltaMovement(target.getDeltaMovement().add(horizontal.scale(horizontalSpeed * resistance)).add(0, lift * resistance, 0));
        target.hasImpulse = true; target.hurtMarked = true;
        level.getChunkSource().broadcastAndSend(target, new ClientboundSetEntityMotionPacket(target));
    }
    private final class CombatGoal extends Goal {
        CombatGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() {
            return behavior() != STILL && !isTransforming() && !isNoAi()
                    && (punching() || getTarget() != null && canFight(getTarget()));
        }
        @Override public boolean canContinueToUse() {
            return canUse() && (punching() || distanceToSqr(getTarget()) <= RIVAL_RANGE * RIVAL_RANGE);
        }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() { /* The server tick advances one authoritative action after movement. */ }
        @Override public void stop() { getNavigation().stop(); cancelPunch(); }
    }

    public void beginTransformation() {
        if (level().isClientSide || isTransforming()) return;
        dev.jujutsuvfx.server.CatSorcery.cancelAll(this);
        cancelPunch();
        noAiBeforeTransformation = super.isNoAi();
        entityData.set(TRANSFORMATION, 0); previousTransformation = 0;
        setTarget(null); getNavigation().stop(); setDeltaMovement(Vec3.ZERO);
    }
    public boolean isTransforming() { return entityData.get(TRANSFORMATION) < TRANSFORMATION_TICKS; }
    public float transformationProgress(float partial) {
        int current = entityData.get(TRANSFORMATION);
        if (Math.abs(current - previousTransformation) > 2) return current / (float) TRANSFORMATION_TICKS;
        return Mth.lerp(Mth.clamp(partial, 0, 1), previousTransformation, current) / TRANSFORMATION_TICKS;
    }
    private void finishTransformation() {
        setNoAi(noAiBeforeTransformation); setDeltaMovement(Vec3.ZERO);
        if (level() instanceof ServerLevel level) {
            double y = getY() + getBbHeight() * .45;
            level.sendParticles(ParticleTypes.POOF, getX(), y, getZ(), 28, .48, .35, .48, .07);
            level.sendParticles(ParticleTypes.END_ROD, getX(), y, getZ(), 12, .28, .32, .28, .04);
            level.playSound(null, getX(), y, getZ(), SoundEvents.GENERIC_EXPLODE, getSoundSource(), .55f, 1.35f);
            level.playSound(null, getX(), y, getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, getSoundSource(), .8f, 1.3f);
        }
    }
    @Override public boolean isInvulnerableTo(DamageSource source) {
        return isTransforming() && !source.is(DamageTypes.FELL_OUT_OF_WORLD) && !source.is(DamageTypes.GENERIC_KILL)
                || super.isInvulnerableTo(source);
    }
    public float blindfoldProgress(float partial) {
        float current = entityData.get(BLINDFOLD);
        if (Math.abs(current - previousBlindfold) > 2f / BLINDFOLD_TICKS) return current;
        return Mth.lerp(Mth.clamp(partial, 0, 1), previousBlindfold, current);
    }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (isTransforming()) return InteractionResult.sidedSuccess(level().isClientSide);
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(VfxItems.GOJO_CAT_DEBUG_STICK.get())) return stack.interactLivingEntity(player, this, hand);
        return InteractionResult.PASS;
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("GojoLastStandPending",resumeLastStand||dev.jujutsuvfx.server.GojoLastStand.active(this));
        if (hoverLease > 0) tag.putBoolean("NoGravity", gravityBeforeHover);
        String prefix = isMahoraga() ? "Mahoraga" : isSukuna() ? "Sukuna" : "Gojo";
        tag.putInt(prefix + "Schema", 4); tag.putInt(prefix + "Appearance", appearance());
        tag.putBoolean(prefix + "BrokenRightArm", brokenArm());
        tag.putBoolean(prefix + "BrokenLeftArm", brokenLeftArm());
        if(summoner!=null)tag.putUUID("SorcererSummoner",summoner);
        tag.putInt(prefix + "BlueHands", blueHands()); tag.putInt(prefix + "RedHands", redHands());
        tag.putInt(prefix + "CursedHands", cursedHands()); tag.putInt(prefix + "BlackFlashHands", blackFlashHands());
        tag.putBoolean(prefix + "AutomaticEnergy", automaticEnergy());
        tag.putInt(prefix + "Behavior", behavior()); tag.put(prefix + "StillPose", entityData.get(STILL_POSE).copy());
        tag.putFloat(prefix + "BlindfoldProgress", entityData.get(BLINDFOLD));
        tag.putInt(prefix + "TransformationStep", entityData.get(TRANSFORMATION));
        tag.putBoolean(prefix + "NoAiBeforeTransformation", noAiBeforeTransformation);
        saveDuelHistory(tag);
        tag.putBoolean("PlayerHalfHealthDomainUsed",playerDomainUsed);tag.putBoolean("LowHealthDomainUsed",lowHealthDomainUsed);
        tag.putBoolean("GojoFirstDomainUncovered",firstDomainUncovered);
        tag.putBoolean("GojoDomainRevealPending",domainRevealRemaining>0);
        tag.putBoolean("AdaptationWheel", hasWheel());
        adaptation.save(tag);
        if(provokedEntity!=null&&getServer()!=null&&provokedUntil>getServer().getTickCount()){
            tag.putUUID("ProvokedEntity",provokedEntity);tag.putInt("ProvokedTicks",(int)Math.min(600,provokedUntil-getServer().getTickCount()));
        }
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        cancelLandingSmash();
        super.readAdditionalSaveData(tag);resumeLastStand=tag.getBoolean("GojoLastStandPending");
        summoner=isMahoraga()&&tag.hasUUID("SorcererSummoner")?tag.getUUID("SorcererSummoner"):null;
        playerDomainUsed=tag.getBoolean("PlayerHalfHealthDomainUsed");lowHealthDomainUsed=tag.getBoolean("LowHealthDomainUsed");
        firstDomainUncovered=isGojo()&&tag.getBoolean("GojoFirstDomainUncovered");domainRevealRemaining=0;
        entityData.set(WHEEL, isSukuna() && tag.getBoolean("AdaptationWheel")); entityData.set(WHEEL_TICKS, 0);
        provokedEntity=null;provokedUntil=0;
        String provocationKey=tag.hasUUID("ProvokedEntity")?"ProvokedEntity":"ProvokedPlayer";
        if(tag.hasUUID(provocationKey)&&getServer()!=null){provokedEntity=tag.getUUID(provocationKey);
            provokedUntil=getServer().getTickCount()+dev.jujutsuvfx.platform.Numbers.clamp(tag.getInt("ProvokedTicks"),0,600);}
        loadDuelHistory(tag);
        String prefix = isMahoraga() ? "Mahoraga" : isSukuna() ? "Sukuna" : "Gojo";
        if (!isMahoraga() && tag.getInt(prefix + "Schema") < 4) {
            // Preserve health percentage when migrating both existing 1500-HP fighters.
            // Unmodified attributes can be absent from NBT; use the previous schema's base.
            float oldMax = !isSukuna() && tag.getInt(prefix + "Schema") < 2 ? 10 : 1500;
            var attributes = tag.getList("Attributes", 10);
            for (int i = 0; i < attributes.size(); i++)
                if (attributes.getCompound(i).getString("Name").equals("minecraft:generic.max_health")) { oldMax = getMaxHealth(); break; }
            float oldHealth = tag.contains("Health") ? tag.getFloat("Health") : getHealth();
            float ratio = oldMax > 0 ? oldHealth / oldMax : 1;
            Objects.requireNonNull(getAttribute(Attributes.MAX_HEALTH)).setBaseValue(BASE_HEALTH);
            setHealth((Float.isFinite(ratio) ? dev.jujutsuvfx.platform.Numbers.clamp(ratio, 0, 1) : 1) * getMaxHealth());
        }
        int appearance = !isGojo() ? NORMAL : tag.contains(prefix + "Appearance") ? Mth.clamp(tag.getInt(prefix + "Appearance"), NORMAL, NO_BLINDFOLD)
                : tag.getBoolean(prefix + "Uncovered") ? LIFT_BLINDFOLD : NORMAL;
        float saved = tag.contains(prefix + "BlindfoldProgress") ? tag.getFloat(prefix + "BlindfoldProgress") : appearance == LIFT_BLINDFOLD ? 1 : 0;
        saved = Float.isFinite(saved) ? Mth.clamp(saved, 0, 1) : 0;
        if(isGojo()&&tag.getBoolean("GojoDomainRevealPending")){appearance=NO_BLINDFOLD;saved=0;}
        entityData.set(APPEARANCE, appearance); entityData.set(BLINDFOLD, saved); previousBlindfold = saved;
        entityData.set(BROKEN_ARM, !isMahoraga() && tag.getBoolean(prefix + "BrokenRightArm"));
        entityData.set(BROKEN_LEFT_ARM,isGojo()&&tag.getBoolean(prefix + "BrokenLeftArm"));
        entityData.set(BLUE_HANDS, !isGojo() ? 0 : Mth.clamp(tag.getInt(prefix + "BlueHands"), 0, BOTH));
        entityData.set(RED_HANDS, !isGojo() ? 0 : Mth.clamp(tag.getInt(prefix + "RedHands"), 0, BOTH));
        String cursed = prefix + (isSukuna() && tag.getInt(prefix + "Schema") < 3 ? "BlueHands" : "CursedHands");
        String flash = prefix + (isSukuna() && tag.getInt(prefix + "Schema") < 3 ? "RedHands" : "BlackFlashHands");
        entityData.set(CURSED_HANDS, !isMahoraga() && tag.contains(cursed) ? Mth.clamp(tag.getInt(cursed), 0, BOTH) : defaultCursedHands());
        entityData.set(BLACK_FLASH_HANDS, !isMahoraga() && tag.contains(flash) ? Mth.clamp(tag.getInt(flash), 0, BOTH) : defaultBlackFlashHands());
        entityData.set(AUTOMATIC, !tag.contains(prefix + "AutomaticEnergy") || tag.getBoolean(prefix + "AutomaticEnergy"));
        entityData.set(BEHAVIOR, tag.contains(prefix + "Behavior") ? Mth.clamp(tag.getInt(prefix + "Behavior"), STILL, AGGRESSIVE) : WANDER);
        entityData.set(STILL_POSE, tag.getCompound(prefix + "StillPose").copy());
        if (behavior() == STILL && !entityData.get(STILL_POSE).contains("X")) setBehavior(STILL);
        int step = tag.contains(prefix + "TransformationStep") ? Mth.clamp(tag.getInt(prefix + "TransformationStep"), 0, TRANSFORMATION_TICKS) : TRANSFORMATION_TICKS;
        entityData.set(TRANSFORMATION, step); previousTransformation = step;
        noAiBeforeTransformation = tag.getBoolean(prefix + "NoAiBeforeTransformation");
        if (tag.getInt(prefix + "Schema") < 2 && isTransforming()) setNoAi(noAiBeforeTransformation);
        adaptation.load(tag);
        cancelPunch(); setPersistenceRequired();
    }
}

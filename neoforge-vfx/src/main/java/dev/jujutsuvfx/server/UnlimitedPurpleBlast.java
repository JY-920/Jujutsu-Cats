package dev.jujutsuvfx.server;

import dev.jujutsuvfx.core.TechniqueDamage;
import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.entity.SorcererCat;
import dev.jujutsuvfx.core.UnlimitedPurpleTiming;
import dev.jujutsuvfx.network.UnlimitedPurple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Expanding entity wave and incremental no-drop terrain work, with one budget per server tick. */
@EventBusSubscriber(modid = JujutsuVfx.ID)
public final class UnlimitedPurpleBlast {
    public static final float RADIUS = 32;
    public static final int CHARGE_TICKS = UnlimitedPurpleTiming.CHARGE_TICKS, WAVE_TICKS = UnlimitedPurpleTiming.WAVE_TICKS;
    private static final int VISUAL_TICKS = UnlimitedPurpleTiming.VISIBLE_TICKS, MAX_TERRAIN_SERVICE_TICKS = 160, MAX_PENDING = 64;
    private static final int SCAN_BUDGET = 32768, DESTROY_BUDGET = 4096;
    private static final double OBSERVER_RANGE_SQUARED = 160 * 160;
    private static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(JujutsuVfx.ID, "unlimited_purple"));
    private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();

    private static final class State {
        final Map<UUID, Blast> active = new LinkedHashMap<>();
        boolean stopped, ticking;
        int rotation;
    }

    private static final class Blast {
        final UUID id = UUID.randomUUID();
        final LivingEntity owner;
        final int terrainRadius;
        final ServerLevel level;
        final Vec3 center;
        final UnlimitedPurple.Source blue, red;
        final long born;
        long clockTick;double visualAge;final boolean finale,slowFusion;
        final ShellCursor terrain;
        final java.util.ArrayDeque<Candidate> pending=new java.util.ArrayDeque<>();
        final Set<UUID> struck = new HashSet<>();
        boolean canceled, waveDone, terrainDone;
        double terrainServiceTicks;

        Blast(LivingEntity owner, Vec3 center, UnlimitedPurple.Source blue, UnlimitedPurple.Source red, int terrainRadius) {
            this.owner = owner; this.level = (ServerLevel)owner.level(); this.center = center; this.terrainRadius=terrainRadius;
            this.blue = blue; this.red = red;
            born = level.getServer().getTickCount();clockTick=born;
            slowFusion=owner instanceof SorcererCat cat&&CatDuel.isFinale(cat);
            finale=slowFusion||owner instanceof SorcererCat cat&&GojoLastStand.active(cat);
            terrain = new ShellCursor(BlockPos.containing(center),terrainRadius);
        }
    }

    private static final class Budget {
        int scanned = SCAN_BUDGET, destroyed = DESTROY_BUDGET;
        long nanos=8_000_000L;
    }

    private record Candidate(BlockPos pos, BlockState state) {}

    private UnlimitedPurpleBlast() {}

    /** Check admission before consuming the colliding casts; a full queue leaves both spells intact. */
    public static boolean canTrigger(ServerPlayer owner, Vec3 center) {
        return canTriggerActor(owner,center);
    }
    private static boolean canTriggerActor(LivingEntity owner,Vec3 center) {
        if (owner == null || !(owner.level() instanceof ServerLevel level) || center == null || !finite(center)) return false;
        MinecraftServer server = owner.getServer();
        if (server == null || !server.isSameThread() || !owner.isAlive() || owner.isRemoved() || owner.isSpectator()
                || owner instanceof ServerPlayer player && server.getPlayerList().getPlayer(owner.getUUID()) != player) return false;
        BlockPos origin = BlockPos.containing(center);
        if (!level.getWorldBorder().isWithinBounds(origin) || !level.isInWorldBounds(origin)
                || level.getChunkSource().getChunkNow(origin.getX() >> 4, origin.getZ() >> 4) == null) return false;
        State state = STATES.get(server);
        return state == null || !state.stopped && state.active.size() < MAX_PENDING;
    }

    /** Admission and first snapshot precede the same-tick consumption of both source slots. */
    public static boolean trigger(ServerPlayer owner, Vec3 center, UnlimitedPurple.Source blue, UnlimitedPurple.Source red) {
        if (!canTrigger(owner, center)) return false;
        State state = STATES.computeIfAbsent(owner.getServer(), key -> new State());
        Blast blast = new Blast(owner, center, blue, red,(int)RADIUS);
        state.active.put(blast.id, blast);
        broadcast(blast, 0, false);
        return true;
    }

    /** NPC choreography uses the same full sphere and shared terrain budget as a player blast. */
    static UUID triggerCat(SorcererCat owner,Vec3 center,UnlimitedPurple.Source blue,UnlimitedPurple.Source red) {
        if(owner.isNoAi()||owner.isInSittingPose()||!canTriggerActor(owner,center))return null;
        State state=STATES.computeIfAbsent(owner.getServer(),key->new State());
        Blast blast=new Blast(owner,center,blue,red,(int)RADIUS);state.active.put(blast.id,blast);broadcast(blast,0,false);return blast.id;
    }
    static int age(SorcererCat owner,UUID id){
        State state=STATES.get(owner.getServer());Blast blast=state==null?null:state.active.get(id);
        return blast==null?-1:(int)blast.visualAge;
    }
    static boolean active(SorcererCat owner,UUID id) {
        State state=STATES.get(owner.getServer());Blast blast=state==null?null:state.active.get(id);
        return blast!=null&&current(state,blast);
    }
    static void cancelCat(SorcererCat owner,UUID id) {
        State state=STATES.get(owner.getServer());Blast blast=state==null?null:state.active.get(id);
        if(blast!=null&&blast.owner==owner)cancel(state,blast);
    }
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        State state = STATES.get(event.getServer());
        if (state == null || state.stopped || state.ticking) return;
        state.ticking = true;
        try {
            List<Blast> work = new ArrayList<>(state.active.values());
            Budget budget = new Budget();
            int start = work.isEmpty() ? 0 : Math.floorMod(state.rotation++, work.size());
            for (int index = 0; index < work.size(); index++) {
                Blast blast = work.get((start + index) % work.size());
                if (state.active.get(blast.id) != blast) continue;
                long clock=blast.level.getServer().getTickCount();
                long elapsed=Math.max(0,clock-blast.clockTick);blast.clockTick=clock;
                blast.visualAge+=elapsed*(blast.slowFusion&&blast.visualAge<CHARGE_TICKS?.25:1);
                int age=(int)blast.visualAge;
                if (!current(state, blast) || blast.terrainServiceTicks >= MAX_TERRAIN_SERVICE_TICKS) {
                    cancel(state, blast); continue;
                }
                if (age < VISUAL_TICKS && clock % (blast.slowFusion?4:20) == 0) broadcast(blast, age, false);
                if (age < CHARGE_TICKS) continue;
                float reached = RADIUS * Math.clamp((age - CHARGE_TICKS) / (float) WAVE_TICKS, 0, 1);
                if (!blast.waveDone) {
                    damageWave(state, blast, reached);
                    if (age >= CHARGE_TICKS + WAVE_TICKS) {
                        blast.waveDone = true;
                        blast.struck.clear();
                    }
                }
                if (!current(state, blast)) { cancel(state, blast); continue; }
                int remaining = work.size() - index;
                if (!blast.terrainDone && budget.scanned > 0 && budget.destroyed > 0 && budget.nanos > 0) {
                    int scansBefore = budget.scanned, destroysBefore = budget.destroyed;
                    carve(state, blast, reached, budget,
                            Math.max(1,budget.scanned/remaining),Math.max(1,budget.destroyed/remaining),
                            Math.max(1,budget.nanos/remaining));
                    // Queue wait and other blasts' work cost nothing. Even a completely solid
                    // 32-block sphere needs under 120 equivalent full-budget ticks, so sharing
                    // the budget cannot exhaust a job while leaving a permanent partial crater.
                    blast.terrainServiceTicks += (scansBefore - budget.scanned) / (double) SCAN_BUDGET
                            + (destroysBefore - budget.destroyed) / (double) DESTROY_BUDGET;
                }
                if (!current(state, blast)) { cancel(state, blast); continue; }
                if (blast.waveDone && blast.terrainDone && (!(blast.owner instanceof SorcererCat)||age>=VISUAL_TICKS)) {
                    // Clients finish the afterglow on their original timeline; no cancellation flash.
                    state.active.remove(blast.id, blast);
                    blast.struck.clear();
                }
            }
        } finally {
            state.ticking = false;
        }
    }

    private static void damageWave(State state, Blast blast, float reached) {
        if (!current(state, blast)) return;
        DamageSource source = new DamageSource(blast.level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DAMAGE), blast.owner, blast.owner);
        AABB bounds = new AABB(blast.center, blast.center).inflate(reached);
        for (LivingEntity target : blast.level.getEntitiesOfClass(LivingEntity.class, bounds, UnlimitedPurpleBlast::eligible)) {
            if (!current(state, blast)) return;
            if (target.level() != blast.level || !eligible(target) || blast.struck.contains(target.getUUID())
                    || !TechniquePlayerRules.canAffect(blast.owner,target) || blast.finale&&target==blast.owner) continue;
            AABB box = target.getBoundingBox();
            double distance = nearest(blast.center, box).distanceTo(blast.center);
            if (distance > reached || distance > RADIUS) continue;
            // Record before invoking external hurt hooks, so re-entry cannot strike this entity twice.
            blast.struck.add(target.getUUID());
            float strength = (float) Math.clamp(1 - distance / RADIUS, 0, 1);
            boolean hit = blast.finale&&CatDuel.finishingHit(blast.owner,target,source);
            if(!hit)hit=TechniqueDamageRules.hurt(target,source,
                    dev.jujutsuvfx.config.VfxConfig.cinematic()?999:target instanceof Player?38:240,TechniqueDamage.PURPLE_UNLIMITED);
            if (!current(state, blast)) return;
            if (target.level() != blast.level || !eligible(target)) continue;
            boolean frozen = DomainFreeze.hold(target);
            if (!current(state, blast)) return;
            if (!hit || frozen || CatDuel.pinned(target) || target instanceof Player&&!dev.jujutsuvfx.config.VfxConfig.cinematic()) continue;
            double response = Math.clamp(1 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0, 1);
            if (response <= 0) continue;
            Vec3 away = target.getBoundingBox().getCenter().subtract(blast.center);
            away = away.lengthSqr() < .001 ? new Vec3(0, 1, 0) : away.normalize();
            Vec3 impulse = away.scale(1.8 + 2.6 * strength).add(0, .35 + .55 * strength, 0).scale(response);
            Vec3 velocity = target.getDeltaMovement().scale(.25).add(impulse);
            if (velocity.lengthSqr() > 4.8 * 4.8) velocity = velocity.normalize().scale(4.8);
            target.setDeltaMovement(velocity);
            target.hasImpulse = true;
        }
    }

    private static void carve(State state, Blast blast, float reached, Budget budget,int scanQuota,int destroyQuota,long timeQuota) {
        if(!current(state,blast))return;
        if(!terrainAllowed(blast)||!TechniqueTerrainRules.canGrief(blast.owner)){
            blast.pending.clear();blast.terrainDone=true;return;
        }
        long began=System.nanoTime(),deadline=began+timeQuota;
        int scanStart=budget.scanned,destroyStart=budget.destroyed;
        try {
            while(current(state,blast)&&System.nanoTime()<deadline&&destroyStart-budget.destroyed<destroyQuota){
                if(!blast.pending.isEmpty()){
                    try(SkillBlockDrops.Scope ignored=SkillBlockDrops.quietTerrain(blast.level);
                        SkillHangingBreaks attachments=SkillHangingBreaks.capture(blast.level,blast.pending.stream().map(Candidate::pos).toList())){
                        while(!blast.pending.isEmpty()&&budget.destroyed>0&&destroyStart-budget.destroyed<destroyQuota
                                &&System.nanoTime()<deadline&&current(state,blast)){
                            Candidate next=blast.pending.removeFirst();
                            if(!terrainAllowed(blast))return;
                            if(!loadedNeighborhood(blast.level,next.pos)||!mayBreak(blast,next.pos)
                                    ||blast.level.getBlockState(next.pos)!=next.state)continue;
                            boolean permitted;
                            if(blast.owner instanceof ServerPlayer player){
                                BlockEvent.BreakEvent event=new BlockEvent.BreakEvent(blast.level,next.pos,next.state,player);
                                NeoForge.EVENT_BUS.post(event);permitted=!event.isCanceled();
                            }else permitted=EventHooks.onEntityDestroyBlock(blast.owner,next.pos,next.state);
                            if(!current(state,blast))return;
                            if(!permitted||!terrainAllowed(blast)||!loadedNeighborhood(blast.level,next.pos)
                                    ||blast.level.getBlockState(next.pos)!=next.state)continue;
                            budget.destroyed--;
                            blast.level.destroyBlock(next.pos,false,blast.owner);
                        }
                    }
                    if(!blast.pending.isEmpty())break;
                }
                if(blast.terrain.done()||budget.scanned<=0||scanStart-budget.scanned>=scanQuota)break;
                boolean waiting=false;
                while(!blast.terrain.done()&&blast.pending.size()<32&&budget.scanned>0&&scanStart-budget.scanned<scanQuota
                        &&System.nanoTime()<deadline&&current(state,blast)){
                    BlockPos pos=blast.terrain.peek();double distanceSquared=cellDistanceSquared(blast.center,pos);
                    if(distanceSquared<=blast.terrainRadius*blast.terrainRadius&&distanceSquared>reached*reached){waiting=true;break;}
                    blast.terrain.advance();budget.scanned--;
                    if(distanceSquared>blast.terrainRadius*blast.terrainRadius||!blast.level.isInWorldBounds(pos)
                            ||!blast.level.getWorldBorder().isWithinBounds(pos)||!loadedNeighborhood(blast.level,pos))continue;
                    BlockState block=blast.level.getBlockState(pos);
                    if(block.isAir()||block.getBlock() instanceof LiquidBlock||block.getDestroySpeed(blast.level,pos)<0
                            ||!block.canEntityDestroy(blast.level,pos,blast.owner)||!mayBreak(blast,pos))continue;
                    blast.pending.addLast(new Candidate(pos,block));
                }
                if(waiting&&blast.pending.isEmpty())break;
            }
            if(blast.terrain.done()&&blast.pending.isEmpty())blast.terrainDone=true;
        }finally{budget.nanos-=Math.max(0,System.nanoTime()-began);}
    }
    /** Exact point-to-block distance without allocating an AABB and three vectors per scanned cell. */
    private static double cellDistanceSquared(Vec3 point,BlockPos pos){
        double x=Math.max(0,Math.max(pos.getX()-point.x,point.x-pos.getX()-1));
        double y=Math.max(0,Math.max(pos.getY()-point.y,point.y-pos.getY()-1));
        double z=Math.max(0,Math.max(pos.getZ()-point.z,point.z-pos.getZ()-1));
        return x*x+y*y+z*z;
    }

    private static boolean terrainAllowed(Blast blast) {
        return (!(blast.owner instanceof ServerPlayer player)||player.mayBuild()) && dev.jujutsuvfx.config.VfxConfig.unlimitedDestruction();
    }

    private static boolean mayBreak(Blast blast, BlockPos pos) {
        return !(blast.owner instanceof ServerPlayer player)||blast.level.mayInteract(player,pos)&&player.mayInteract(blast.level,pos)
                && !player.blockActionRestricted(blast.level,pos,player.gameMode.getGameModeForPlayer());
    }

    private static boolean loadedNeighborhood(ServerLevel level, BlockPos pos) {
        // Include neighbors used by normal block updates, using only non-loading lookups.
        for (int x = (pos.getX() - 1) >> 4; x <= (pos.getX() + 1) >> 4; x++)
            for (int z = (pos.getZ() - 1) >> 4; z <= (pos.getZ() + 1) >> 4; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }

    private static boolean eligible(LivingEntity target) {
        return target.isAlive() && !target.isRemoved() && !target.isSpectator()
                && !target.isPassenger() && !target.isVehicle();
    }

    private static Vec3 nearest(Vec3 point, AABB box) {
        return new Vec3(Math.clamp(point.x, box.minX, box.maxX), Math.clamp(point.y, box.minY, box.maxY),
                Math.clamp(point.z, box.minZ, box.maxZ));
    }

    private static boolean finite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    private static boolean current(State state, Blast blast) {
        MinecraftServer server = blast.level.getServer();
        return !state.stopped && !blast.canceled && state.active.get(blast.id) == blast
                && blast.owner.isAlive() && !blast.owner.isRemoved() && !blast.owner.isSpectator()
                && blast.owner.level() == blast.level
                && (blast.owner instanceof ServerPlayer?server.getPlayerList().getPlayer(blast.owner.getUUID())==blast.owner
                    :blast.level.getEntity(blast.owner.getUUID())==blast.owner&&!(blast.owner instanceof SorcererCat cat&&cat.isNoAi()));
    }

    private static void broadcast(Blast blast, int age, boolean cancellation) {
        UnlimitedPurple packet = new UnlimitedPurple(blast.id, blast.center, RADIUS, age, blast.blue, blast.red, blast.slowFusion);
        for (ServerPlayer observer : List.copyOf(blast.level.players()))
            if (cancellation || observer.position().distanceToSqr(blast.center) <= OBSERVER_RANGE_SQUARED) packet.send(observer);
    }

    private static void cancel(State state, Blast blast) {
        if (blast.canceled) return;
        blast.canceled = true;blast.pending.clear();
        state.active.remove(blast.id, blast);
        blast.struck.clear();
        broadcast(blast, -1, true);
    }

    private static void cancelOwner(ServerPlayer owner) {
        State state = STATES.get(owner.getServer());
        if (state == null) return;
        for (Blast blast : List.copyOf(state.active.values())) if (blast.owner == owner) cancel(state, blast);
    }

    @SubscribeEvent public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer owner) cancelOwner(owner);
    }

    @SubscribeEvent public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer owner) cancelOwner(owner);
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        State state = STATES.get(event.getServer());
        if (state == null) return;
        state.stopped = true;
        for (Blast blast : List.copyOf(state.active.values())) cancel(state, blast);
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        State state = STATES.remove(event.getServer());
        if (state == null) return;
        state.stopped = true;
        for (Blast blast : state.active.values()) { blast.canceled = true;blast.pending.clear(); blast.struck.clear(); }
        state.active.clear();
    }

    /** Visits each cell of the 65-cube once, inside out, without precomputing a giant position list. */
    private static final class ShellCursor {
        private final BlockPos origin;
        private final int radius;
        private int shell, face, index;

        ShellCursor(BlockPos origin,int radius) { this.origin = origin;this.radius=radius; }
        boolean done() { return shell > radius; }

        BlockPos peek() {
            if (shell == 0) return origin;
            int side = shell * 2 + 1, inner = side - 2;
            if (face < 2) return origin.offset(index % side - shell, face == 0 ? -shell : shell, index / side - shell);
            if (face < 4) return origin.offset(face == 2 ? -shell : shell, index / side - shell + 1, index % side - shell);
            return origin.offset(index % inner - shell + 1, index / inner - shell + 1, face == 4 ? -shell : shell);
        }

        void advance() {
            if (shell == 0) { shell = 1; return; }
            int side = shell * 2 + 1;
            int cells = face < 2 ? side * side : face < 4 ? side * (side - 2) : (side - 2) * (side - 2);
            if (++index < cells) return;
            index = 0;
            if (++face == 6) { face = 0; shell++; }
        }
    }
}

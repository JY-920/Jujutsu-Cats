package dev.jujutsuvfx.server;

import dev.jujutsuvfx.core.CatSpell;
import dev.jujutsuvfx.entity.SorcererCat;
import dev.jujutsuvfx.network.CatCombatCue;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import static dev.jujutsuvfx.core.CatCombatMoves.*;

/** Finite, collision-checked footwork and air retreats; never requests unloaded terrain. */
final class CatTactics {
    private CatTactics() {}
    static float preferred(int kind, boolean large) {
        return CatSpell.domain(kind)?18:kind==CatSpell.FURNACE?29:kind==CatSpell.PURPLE?(large?36:23)
                :kind==CatSpell.RED?(large?28:17):kind==CatSpell.BLUE?18:kind==CatSpell.CLEAVE?17:12;
    }
    static float minimum(int kind, boolean large) {
        return kind==CatSpell.FURNACE?19:kind==CatSpell.PURPLE?(large?25:14)
                :kind==CatSpell.RED&&large?17:kind==CatSpell.CLEAVE?6:3;
    }
    static Maneuver forCast(SorcererCat cat, LivingEntity target, int kind, boolean large, boolean counter) {
        double distance=cat.distanceTo(target), desired=preferred(kind,large);
        if(counter&&distance>=9)return null;
        boolean heavy=kind==CatSpell.FURNACE||kind==CatSpell.PURPLE||large;
        boolean air=!cat.onGround()||heavy&&cat.getRandom().nextFloat()<.72f;
        if(distance>=desired-2&&(!air||cat.combatHovering())&&cat.getSensing().hasLineOfSight(target))return null;
        Vec3 away=horizontal(cat.position().subtract(target.position()));
        if(away.lengthSqr()<.01)away=horizontal(cat.getLookAngle()).scale(-1);
        return plan(cat,target,away,Math.clamp(desired-distance,3,counter?8:32),desired,air,counter);
    }
    static Maneuver reaction(SorcererCat cat, LivingEntity caster, Vec3 incoming, boolean ignoredPressure) {
        Vec3 away=horizontal(cat.position().subtract(caster.position()));
        Vec3 tangent=horizontal(new Vec3(-incoming.z,0,incoming.x));
        if(cat.getRandom().nextBoolean())tangent=tangent.scale(-1);
        return plan(cat,caster,tangent.add(away.scale(.8)).normalize(),9+cat.getRandom().nextFloat()*5,
                Math.min(44,cat.distanceTo(caster)+7),!cat.onGround()||cat.getRandom().nextBoolean(),false);
    }
    /** Recovery only uses a short supported ground route, with no air/blink fallback. */
    static Maneuver forRecovery(SorcererCat cat, LivingEntity target) {
        if(!(cat.level() instanceof ServerLevel level)||!cat.onGround()||cat.combatHovering())return null;
        Vec3 start=cat.position(),away=horizontal(start.subtract(target.position()));
        if(away.lengthSqr()<.01)away=horizontal(cat.getLookAngle()).scale(-1);
        double distance=cat.distanceTo(target),travel=Math.clamp(10-distance,3.5,6.5);
        Vec3 best=null;double score=-Double.MAX_VALUE;
        for(int i=0;i<7;i++){
            float angle=i==0?0:((i+1)/2)*.32f*(i%2==0?-1:1);
            Vec3 point=supported(cat,level,start.add(away.yRot(angle).scale(travel)));
            if(point==null||!route(cat,level,start,point)||!sight(cat,level,point,target))continue;
            double separation=point.distanceTo(target.position());
            if(separation<6||separation<distance+2)continue;
            int openness=CatArena.quality(cat,level,point);if(openness<16)continue;
            double value=openness*.25-Math.abs(separation-10)-Math.abs(angle);
            if(value>score){score=value;best=point;}
        }
        return best==null?null:new Maneuver(cat,target,best,STEP_BACK,30,false);
    }
    /** A descending, collision-checked route into a grounded repair stance, never a teleport. */
    static Maneuver forRecoveryLanding(SorcererCat cat,LivingEntity target){
        if(!(cat.level() instanceof ServerLevel level))return null;
        Vec3 start=cat.position(),away=horizontal(start.subtract(target.position()));
        if(away.lengthSqr()<.01)away=new Vec3(0,0,1);
        Vec3 best=null;double score=-Double.MAX_VALUE;
        for(int i=0;i<18;i++){
            float angle=i==0?0:(i%2==0?-1:1)*((i+1)/2)*.45f;
            double travel=i==0?0:4+(i%3)*3;
            Vec3 floor=CatArena.surface(level,start.add(away.yRot(angle).scale(travel)));
            if(floor==null||floor.y>start.y+1||start.y-floor.y>32||!CatArena.walkable(cat,level,floor))continue;
            Vec3 end=floor.add(0,.12,0);
            if(!airRoute(cat,level,start,end))continue;
            double separation=floor.distanceTo(target.position());
            double value=CatArena.quality(cat,level,floor)*.20-Math.abs(separation-10)*.6-start.distanceTo(end)*.10;
            if(separation<5.5)value-=15;
            if(value>score){best=end;score=value;}
        }
        return best==null?null:new Maneuver(cat,target,best,AIR_RETREAT,64,true);
    }
    private static Maneuver plan(SorcererCat cat, LivingEntity target, Vec3 direction, double travel,
                                 double desired, boolean preferAir, boolean counter) {
        if(!(cat.level() instanceof ServerLevel level))return null;
        boolean blink=cat.canCombatBlink()&&travel>6&&cat.getRandom().nextFloat()<.30f;
        Vec3 start=cat.position(),best=null;boolean bestAir=false;double score=-Double.MAX_VALUE;
        for(int i=0;i<12;i++) {
            float angle=(i%6==0?0:((i%6+1)/2)*.32f*(i%2==0?-1:1));
            Vec3 candidate=start.add(direction.yRot(angle).scale(travel*(i<6?1:.78)));
            for(int layer=0;layer<2;layer++) {
                boolean air=layer==1;
                if (air && cat.isMahoraga()) continue;
                Vec3 point=air?candidate.add(0,Math.max(4.5,target.getY()+6-start.y),0):supported(cat,level,candidate);
                if(point==null||air&&!empty(cat,level,point)||!sight(cat,level,point,target))continue;
                double separation=point.distanceTo(target.position());
                if(separation>52||separation<cat.distanceTo(target)-.5)continue;
                if(!blink&&!(air?airRoute(cat,level,start,point):route(cat,level,start,point))&&!CatTerrain.route(cat,start,point))continue;
                Vec3 floor=air?CatArena.surface(level,point):point;
                int openness=floor==null?-100:CatArena.quality(cat,level,floor);
                if(openness<16)continue;
                double value=openness*.3-Math.abs(separation-desired)*1.5+(air==preferAir?5:0)-Math.abs(angle)*2;
                if(value>score){score=value;best=point;bestAir=air;}
            }
        }
        return best==null?null:new Maneuver(cat,target,best,blink?SPACE_BLINK:bestAir?AIR_RETREAT:SPACE_DASH,counter?28:60,bestAir);
    }
    static boolean ready(SorcererCat cat, LivingEntity target, int kind, boolean large) {
        if(!(cat.level() instanceof ServerLevel level)||!cat.canFight(target))return false;
        double distance=cat.distanceTo(target);
        if(CatSpell.domain(kind))return distance<54;
        Vec3 floor=cat.onGround()?cat.position():CatArena.surface(level,cat.position());
        if(floor==null||!CatArena.landing(cat,level,floor))return false;
        double reach=kind==CatSpell.DISMANTLE?27:kind==CatSpell.RED&&!large?30:52;
        return (cat.onGround()||cat.combatHovering())&&distance>=minimum(kind,large)&&distance<=reach&&sight(cat,level,cat.position(),target);
    }
    static boolean sight(SorcererCat cat, ServerLevel level, Vec3 point, LivingEntity target) {
        Vec3 eye=point.add(0,cat.getEyeHeight(),0),end=target.getBoundingBox().getCenter();
        if(!dev.jujutsuvfx.core.LimitlessTechnique.loadedSegment(level,eye,end))return false;
        return level.clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,cat)).getType()==HitResult.Type.MISS;
    }
    static boolean empty(SorcererCat cat, ServerLevel level, Vec3 point) {
        AABB box=cat.getBoundingBox().move(point.subtract(cat.position())).inflate(.05,0,.05);
        return loaded(level,box)&&level.noCollision(cat,box)&&!level.containsAnyLiquid(box);
    }
    private static boolean loaded(ServerLevel level, AABB box) {
        if(box.minY<level.getMinBuildHeight()||box.maxY>=level.getMaxBuildHeight()||!level.getWorldBorder().isWithinBounds(box))return false;
        for(int x=Mth.floor(box.minX)>>4;x<=Mth.floor(box.maxX)>>4;x++)for(int z=Mth.floor(box.minZ)>>4;z<=Mth.floor(box.maxZ)>>4;z++)
            if(level.getChunkSource().getChunkNow(x,z)==null)return false;
        return true;
    }
    static Vec3 supported(SorcererCat cat, ServerLevel level, Vec3 desired) {
        for(int dy:new int[]{0,1,-1,2,-2}) {
            Vec3 p=new Vec3(desired.x,Math.floor(desired.y)+dy,desired.z);AABB box=cat.getBoundingBox().move(p.subtract(cat.position()));
            if(!empty(cat,level,p)||!loaded(level,box.move(0,-.2,0))||level.noCollision(cat,box.deflate(.1,0,.1).move(0,-.16,0)))continue;
            var floor=level.getBlockState(BlockPos.containing(p.add(0,-.08,0)));
            if(CatArena.hazardous(level,p)||floor.is(Blocks.MAGMA_BLOCK)||floor.is(Blocks.CAMPFIRE)||floor.is(Blocks.SOUL_CAMPFIRE)||floor.is(Blocks.CACTUS))continue;
            return p;
        }
        return null;
    }
    private static boolean route(SorcererCat cat, ServerLevel level, Vec3 from, Vec3 to) {
        int steps=Math.max(1,(int)Math.ceil(from.distanceTo(to)/.7));
        for(int step=1;step<=steps;step++){Vec3 point=from.lerp(to,step/(double)steps),floor=supported(cat,level,point);if(floor==null||Math.abs(floor.y-point.y)>.65)return false;}
        return true;
    }
    static boolean airRoute(SorcererCat cat, ServerLevel level, Vec3 from, Vec3 to) {
        int steps=Math.max(1,(int)Math.ceil(from.distanceTo(to)/.5));if(steps>112)return false;
        for(int i=1;i<=steps;i++)if(!empty(cat,level,from.lerp(to,i/(double)steps)))return false;
        return true;
    }
    static boolean escapeRoute(SorcererCat cat,ServerLevel level,Vec3 from,Vec3 to){
        int steps=Math.max(1,(int)Math.ceil(from.distanceTo(to)/.4));
        for(int i=1;i<=steps;i++){
            AABB box=cat.getBoundingBox().move(from.lerp(to,i/(double)steps).subtract(cat.position()));
            if(!loaded(level,box)||!level.noCollision(cat,box))return false;
        }
        return true;
    }
    static final class Maneuver {
        final SorcererCat cat;final LivingEntity target;final ServerLevel level;final Vec3 from,to;
        final int style,maxTicks,hand;final boolean air;
        int ticks,settled,blocked;boolean arrived,failed;Vec3 trail,previous;
        boolean escape, pursuit;
        Maneuver(SorcererCat cat,LivingEntity target,Vec3 to,int style,int maxTicks,boolean air){
            this.cat=cat;this.target=target;this.to=to;this.style=style;this.maxTicks=maxTicks;this.air=air;
            level=(ServerLevel)cat.level();from=trail=previous=cat.position();hand=cat.brokenArm()?SorcererCat.LEFT:SorcererCat.RIGHT;
        }
        Maneuver escaping(){escape=true;return this;}
        Maneuver pursuing(){pursuit=true;return this;}
        boolean tick(){
            ticks++;face(cat,target);cat.getNavigation().stop();if(air)cat.renewCombatHover();
            if(style==SPACE_BLINK){
                if(ticks==4){
                    Vec3 destination=pursuit?CatPursuit.blinkDestination(cat,level,target):to;
                    Vec3 safe=destination==null?null:air?(empty(cat,level,destination)?destination:null):supported(cat,level,destination);
                    if(safe!=null&&(escape||pursuit||sight(cat,level,safe,target))&&cat.tryCombatBlink()){
                        cat.teleportTo(safe.x,safe.y,safe.z);cat.fallDistance=0;arrived=true;
                        cue(CatCombatCue.BLINK,from,safe);cat.playSound(SoundEvents.ENDERMAN_TELEPORT,.38f,1.3f);
                    }else failed=true;
                }
                stop();return ticks>=9;
            }
            Vec3 delta=to.subtract(cat.position());if(!air)delta=new Vec3(delta.x,0,delta.z);double left=delta.length();
            if(left<.55)arrived=true;
            // Actual progress, not just horizontalCollision: spacing constraints can also stop a route.
            if(ticks>4&&!arrived&&(cat.horizontalCollision||cat.position().distanceToSqr(previous)<.0025))blocked++;
            else blocked=0;
            previous=cat.position();
            if(blocked>3||ticks>=maxTicks-4)failed=!arrived;
            if(ticks<=2||arrived||failed){stop();if(ticks>2)settled++;return settled>=3||ticks>=maxTicks;}
            Vec3 velocity=delta.normalize().scale(Math.min(left,cat.isSukuna()?1.65:1.45)),next=cat.position().add(velocity);
            CatTerrain.clearStep(cat,cat.position(),next);
            Vec3 ground=air?null:supported(cat,level,next);
            if(!(air?(style==AIR_ESCAPE?escapeRoute(cat,level,cat.position(),next):airRoute(cat,level,cat.position(),next)):ground!=null)){failed=true;stop();return false;}
            double vertical=cat.getDeltaMovement().y;
            if(!air&&ground.y>cat.getY()+.35&&cat.onGround())vertical=Math.max(vertical,.48);
            motion(air?velocity:new Vec3(velocity.x,vertical,velocity.z));
            if(ticks%5==0){cue(CatCombatCue.DASH,trail,cat.position());trail=cat.position();}
            return false;
        }
        void stop(){cat.setDeltaMovement(cat.getDeltaMovement().multiply(0,air?0:1,0));}
        private void motion(Vec3 velocity){cat.setDeltaMovement(velocity);cat.hasImpulse=true;cat.hurtMarked=true;
            if(ticks%2==0)level.getChunkSource().broadcastAndSend(cat,new ClientboundSetEntityMotionPacket(cat));}
        private void cue(int type,Vec3 start,Vec3 end){new CatCombatCue(cat.getId(),target.getId(),type,start.add(0,.6,0),end.add(0,.6,0),
                cat.isMahoraga()?SorcererCat.PLAIN:cat.isSukuna()?SorcererCat.CURSED:SorcererCat.BLUE,SorcererCat.PLAIN,cat.getRandom().nextInt()).broadcast(level);}
    }
    static void face(SorcererCat cat,LivingEntity target){
        Vec3 direction=target.getBoundingBox().getCenter().subtract(cat.getEyePosition()).normalize();
        cat.setYRot(Mth.approachDegrees(cat.getYRot(),(float)(Math.atan2(-direction.x,direction.z)*180/Math.PI),26));
        cat.setXRot(Mth.approachDegrees(cat.getXRot(),(float)(-Math.asin(Math.clamp(direction.y,-1,1))*180/Math.PI),16));
        cat.yHeadRot=cat.getYRot();cat.yBodyRot=cat.getYRot();
    }
    static Vec3 horizontal(Vec3 p){return new Vec3(p.x,0,p.z).normalize();}
}

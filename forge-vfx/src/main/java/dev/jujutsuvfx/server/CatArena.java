package dev.jujutsuvfx.server;

import dev.jujutsuvfx.entity.SorcererCat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import static dev.jujutsuvfx.core.CatCombatMoves.*;

/** Small loaded-area search favouring broad, dry floors, with an upward escape from pits. */
final class CatArena {
    private CatArena(){}
    static boolean hazardous(ServerLevel level,Vec3 p){
        BlockPos base=BlockPos.containing(p);
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=-1;y<=2;y++){
            BlockPos q=base.offset(x,y,z);if(!level.hasChunkAt(q))return true;
            var b=level.getBlockState(q);
            if(!b.getFluidState().isEmpty()||b.is(Blocks.FIRE)||b.is(Blocks.SOUL_FIRE)||b.is(Blocks.MAGMA_BLOCK)
                    ||b.is(Blocks.CAMPFIRE)&&b.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                    ||b.is(Blocks.SOUL_CAMPFIRE)&&b.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                    ||b.is(Blocks.CACTUS)||b.is(Blocks.SWEET_BERRY_BUSH)||b.is(Blocks.POWDER_SNOW))return true;
        }
        return false;
    }
    static Vec3 surface(ServerLevel level,Vec3 p){
        BlockPos q=BlockPos.containing(p);if(!level.hasChunkAt(q))return null;
        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,q.getX(),q.getZ());
        if(y<level.getMinBuildHeight()||y>=level.getMaxBuildHeight()-3)return null;
        return new Vec3(p.x,y,p.z);
    }
    static int quality(SorcererCat cat,ServerLevel level,Vec3 p){
        if(!CatTactics.empty(cat,level,p)||hazardous(level,p))return -100;
        Vec3 surface=surface(level,p);if(surface==null||surface.y>p.y+2)return -60;
        int score=0;
        for(int i=0;i<12;i++){
            double a=i*Math.PI/6;double radius=i%2==0?4:7;
            Vec3 q=p.add(Math.cos(a)*radius,0,Math.sin(a)*radius),floor=surface(level,q);
            if(floor==null||Math.abs(floor.y-p.y)>1.25||!CatTactics.empty(cat,level,floor)||hazardous(level,floor))continue;
            if(CatTactics.airRoute(cat,level,p.add(0,.15,0),floor.add(0,.15,0)))score+=2;
        }
        if(level.canSeeSky(BlockPos.containing(p).above()))score+=4;
        int higherRim=0;
        for(int i=0;i<8;i++){
            double a=i*Math.PI/4;
            Vec3 rim=surface(level,p.add(Math.cos(a)*16,0,Math.sin(a)*16));
            if(rim!=null&&rim.y>p.y+3)higherRim++;
        }
        if(higherRim>=4)score-=24;
        return score;
    }
    static boolean landing(SorcererCat cat,ServerLevel level,Vec3 point){return quality(cat,level,point)>=16;}
    static boolean walkable(SorcererCat cat,ServerLevel level,Vec3 p){
        if(!CatTactics.empty(cat,level,p)||hazardous(level,p))return false;
        var feet=cat.getBoundingBox().move(p.subtract(cat.position())).deflate(.10,0,.10).move(0,-.20,0);
        // Open, level ground is a destination preference. A supported slope or ledge is still
        // a valid route there; requiring a flat 4-block neighborhood rejected virtually every hill.
        return !level.noCollision(cat,feet);
    }
    private static boolean followPath(SorcererCat cat,ServerLevel level,Vec3 point,double speed){
        var path=cat.getNavigation().createPath(point.x,point.y,point.z,0);
        if(path==null||path.isDone())return false;
        Vec3 end=path.getEntityPosAtNode(cat,path.getNodeCount()-1);
        if(!path.canReach()&&end.distanceToSqr(cat.position())<.8*.8)return false;
        int next=path.getNextNodeIndex();
        for(int i=next;i<Math.min(path.getNodeCount(),next+12);i++)
            if(!walkable(cat,level,path.getEntityPosAtNode(cat,i)))return false;
        return cat.getNavigation().moveTo(path,speed);
    }
    static void navigate(SorcererCat cat,LivingEntity target,double speed){
        if(!(cat.level() instanceof ServerLevel level))return;
        // Start beside the opponent at their actual elevation, including inside roofed rooms.
        // A path ending inside their collision box used to invalidate every otherwise usable route.
        Vec3 fromTarget=cat.position().subtract(target.position()).multiply(1,0,1).normalize();
        if(fromTarget.lengthSqr()<.01)fromTarget=new Vec3(0,0,1);
        for(int i=0;i<8;i++){
            float angle=i==0?0:(float)(((i+1)/2)*Math.PI/4*(i%2==0?-1:1));
            Vec3 destination=CatTactics.supported(cat,level,target.position().add(fromTarget.yRot(angle).scale(CatPursuit.spacing(cat,target))));
            if(destination!=null&&followPath(cat,level,destination,speed))return;
        }
        Vec3 floor=surface(level,target.position());
        if(floor!=null&&Math.abs(floor.y-target.getY())<=1.3&&landing(cat,level,floor)
                &&followPath(cat,level,target.position(),speed))return;
        // Keep a reachable rim/flank when the opponent stands in rubble, water or a pit.
        // Fixed candidate budget; never load chunks to plan a combat route.
        Vec3 toward=cat.position().subtract(target.position()).multiply(1,0,1).normalize();
        if(toward.lengthSqr()<.01)toward=new Vec3(0,0,1);
        for(int i=0;i<6;i++){
            float angle=(i==0?0:((i+1)/2)*.58f*(i%2==0?-1:1));
            Vec3 flank=surface(level,target.position().add(toward.yRot(angle).scale(4.5+i*.55)));
            if(flank==null||Math.abs(flank.y-cat.getY())>12||!walkable(cat,level,flank))continue;
            if(followPath(cat,level,flank,speed))return;
        }
        // Narrow safe routes remain usable even if no wide arena exists nearby.
        if(walkable(cat,level,target.position())&&followPath(cat,level,target.position(),speed))return;
        if(CatPursuit.breach(cat,level,target))return;
        // A failed re-plan must not discard a still useful detour already being followed.
        if(cat.getNavigation().isDone())cat.getNavigation().stop();
    }
    static CatTactics.Maneuver escape(SorcererCat cat,LivingEntity target){
        if(!(cat.level() instanceof ServerLevel level)||target==null)return null;
        Vec3 floor=surface(level,cat.position());
        int currentQuality=quality(cat,level,cat.position());
        // A roof or a narrow but dry floor is not a hazard requiring an upward escape.
        if(currentQuality>=20||walkable(cat,level,cat.position()))return null;
        Vec3 best=null;double bestScore=-Double.MAX_VALUE,rimHeight=floor==null?cat.getY():floor.y+2;
        for(int i=0;i<32;i++){
            double a=i*Math.PI*.61803398875*2,dist=8+(i/8)*6;
            Vec3 p=surface(level,cat.position().add(Math.cos(a)*dist,0,Math.sin(a)*dist));
            if(p==null)continue;
            rimHeight=Math.max(rimHeight,p.y+2);
            if(Math.abs(p.y-cat.getY())>24)continue;
            int quality=quality(cat,level,p);if(quality<18)continue;
            double score=quality-Math.abs(p.y-cat.getY())*.8-Math.abs(p.distanceTo(target.position())-14)*.18-p.distanceTo(cat.position())*.14;
            if(score>bestScore){bestScore=score;best=p;}
        }
        if(best!=null){
            if(cat.canCombatBlink())return new CatTactics.Maneuver(cat,target,best,SPACE_BLINK,12,false).escaping();
            Vec3 flight=best.add(0,1.1,0);
            if(CatTactics.airRoute(cat,level,cat.position(),flight)||CatTerrain.route(cat,cat.position(),flight))return new CatTactics.Maneuver(cat,target,flight,AIR_RETREAT,60,true);
        }
        // No dry landing in range: stay just above the rim rather than climbing forever.
        if(best==null&&cat.getY()>=rimHeight&&cat.combatHovering())return null;
        // Rise above the local rim before the next leg. Collision still applies in liquids.
        double rim=best==null?rimHeight:Math.max(cat.getY()+3,Math.max(best.y,floor==null?cat.getY():floor.y)+2);
        Vec3 above=new Vec3(cat.getX(),Math.min(cat.getY()+25,rim),cat.getZ());
        if(above.distanceToSqr(cat.position())>.75*.75&&(CatTactics.escapeRoute(cat,level,cat.position(),above)||CatTerrain.route(cat,cat.position(),above)))return new CatTactics.Maneuver(cat,target,above,AIR_ESCAPE,40,true);
        return null;
    }
}

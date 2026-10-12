package dev.jujutsuvfx.server;

import com.mojang.logging.LogUtils;
import dev.jujutsuvfx.compat.CleaveCompatibility;
import dev.jujutsuvfx.core.FurnaceFlight;
import dev.jujutsuvfx.core.SukunaSlashCharge;
import dev.jujutsuvfx.core.SukunaSlashStyle;
import dev.jujutsuvfx.entity.SorcererCat;
import dev.jujutsuvfx.network.SukunaCut;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Tap is one cut; charged release is eight short-interval cuts, not damage during the wind-up. */
final class CatCleave {
    private final SorcererCat cat;
    private final ServerLevel level;
    private final Vec3 anchor,direction;
    private final boolean large;
    private final java.util.UUID targetId;
    private int remaining,next;
    private boolean physicsFailed;
    CatCleave(SorcererCat cat,LivingEntity target,Vec3 direction,boolean large){
        this.cat=cat;targetId=target.getUUID();level=(ServerLevel)cat.level();this.large=large;this.direction=CleaveCompatibility.worldDirection(cat,direction);
        anchor=CleaveCompatibility.worldPosition(level,target.getBoundingBox().getCenter());remaining=large?SukunaSlashCharge.BURST_CUTS:1;
    }
    boolean finished(){return remaining<=0;}
    void tick(){
        int now=cat.getServer().getTickCount();if(remaining<=0||now<next||!cat.isAlive())return;
        int seed=cat.getRandom().nextInt();boolean cross=SukunaSlashStyle.isCross(seed);
        if(!SukunaSlashSkills.reservePhysics(cat.getServer(),cross?2:1))return;
        Vec3 right=FurnaceFlight.right(direction),up=right.cross(direction).normalize();
        Vec3 stroke=SukunaSlashStyle.rotateStroke(seed,up,direction),normal=direction.cross(stroke).normalize();
        Vec3 second=cross?direction.cross(SukunaSlashStyle.secondStroke(stroke,direction)).normalize():null;
        double radius=large?5.5:4.5,scale=SukunaSlashStyle.crossScale(seed);
        Vec3 center=anchor;
        LivingEntity live=level.getEntity(targetId) instanceof LivingEntity entity?entity:null;
        boolean tracking=live!=null&&cat.canFight(live)&&cat.distanceToSqr(live)<40*40
                &&live.getBoundingBox().getCenter().subtract(cat.getEyePosition()).normalize().dot(direction)>.34;
        if(tracking)center=CleaveCompatibility.worldPosition(level,live.getBoundingBox().getCenter());
        if(large){
            double width=tracking?Math.min(.7,live.getBbWidth()*.5):3.0,height=tracking?Math.min(.8,live.getBbHeight()*.5):2.6;
            center=center.add(right.scale((cat.getRandom().nextDouble()-.5)*width)).add(up.scale((cat.getRandom().nextDouble()-.5)*height));
        }
        boolean terrain=false;
        if(TechniqueTerrainRules.enabled()) {
            if(TechniqueTerrainRules.cleavePhysics()&&!physicsFailed)try{terrain=Physics.cut(cat,center,normal,direction,radius,second,scale);}
            catch(RuntimeException|LinkageError error){physicsFailed=true;LogUtils.getLogger().warn("NPC Cleave physics pass stopped; later cuts use ordinary terrain",error);}
            else terrain=CleaveTerrain.cut(cat,center,normal,radius,second,scale);
        }
        boolean hit=SukunaCleaveDamage.strike(cat,center,normal,direction,radius,second,scale);
        visual(level,center,direction,stroke,(float)(radius*2.25),terrain||hit,seed,cross);
        remaining--;next=now+SukunaSlashCharge.BURST_INTERVAL;
    }
    static void visual(ServerLevel level,Vec3 center,Vec3 direction,Vec3 stroke,float length,boolean impact,int seed,boolean cross){
        SukunaCut packet=new SukunaCut(center,direction,stroke,length,1,impact,seed,cross);
        for(var player:level.players())if(player.distanceToSqr(center)<160*160&&player.connection.hasChannel(SukunaCut.TYPE))PacketDistributor.sendToPlayer(player,packet);
    }
    private static final class Physics {
        static boolean cut(SorcererCat cat,Vec3 center,Vec3 normal,Vec3 direction,double radius,Vec3 second,double scale){
            return (second==null?dev.jujutsuvfx.compat.SableStructureCutter.cut(cat,center,normal,direction,radius)
                    :dev.jujutsuvfx.compat.SableStructureCutter.cutCross(cat,center,normal,direction,radius,second,scale)).success();
        }
    }
}

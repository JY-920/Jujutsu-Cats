package dev.jujutsuvfx.compat;

import dev.jujutsuvfx.server.TechniqueTerrainRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.UUID;

/** Loader-neutral signatures: absent physics never resolves a Sable class. */
public final class StructureThrowBridge {
    public record Body(UUID id,Vec3 center,double radius) {}
    private StructureThrowBridge() {}
    public static Body acquire(LivingEntity owner,Vec3 around){return TechniqueTerrainRules.cleavePhysics()?Physics.acquire(owner,around):null;}
    public static Body assemble(LivingEntity owner,List<BlockPos> blocks){return TechniqueTerrainRules.cleavePhysics()?Physics.assemble(owner,blocks):null;}
    public static Body inspect(LivingEntity owner,UUID id){return TechniqueTerrainRules.cleavePhysics()?Physics.inspect(owner,id):null;}
    public static boolean velocity(LivingEntity owner,UUID id,Vec3 velocity,Vec3 spin){return TechniqueTerrainRules.cleavePhysics()&&Physics.velocity(owner,id,velocity,spin);}
    public static List<UUID> cut(LivingEntity owner,UUID id,Vec3 normal,Vec3 forward){return TechniqueTerrainRules.cleavePhysics()?Physics.cut(owner,id,normal,forward):List.of();}
    private static final class Physics {
        static Body wrap(SableStructureCutter.Target t){return t==null?null:new Body(t.id(),t.center(),t.radius());}
        static Body acquire(LivingEntity owner,Vec3 point){return wrap(SableStructureCutter.projectileCandidate(owner,point));}
        static Body assemble(LivingEntity owner,List<BlockPos> blocks){
            var result=SableStructureCutter.assembleProjectile(owner,blocks);
            if(!result.success()||result.bodies().isEmpty())return null;
            UUID id=result.bodies().get(0);Body ready=inspect(owner,id);if(ready!=null)return ready;
            // Assembly commits before the first physics mass update. Keep its identity while
            // that body becomes ready; never mistake a successful transfer for visual fallback.
            Vec3 center=Vec3.ZERO;for(BlockPos p:blocks)center=center.add(p.getCenter());center=center.scale(1.0/blocks.size());
            double radius=1;for(BlockPos p:blocks)radius=Math.max(radius,p.getCenter().distanceTo(center)+.87);
            return new Body(id,center,radius);
        }
        static Body inspect(LivingEntity owner,UUID id){return wrap(SableStructureCutter.inspect(owner,id));}
        static boolean velocity(LivingEntity owner,UUID id,Vec3 v,Vec3 spin){return SableStructureCutter.projectileVelocity(owner,id,v,spin);}
        static List<UUID> cut(LivingEntity owner,UUID id,Vec3 normal,Vec3 forward){return SableStructureCutter.cutProjectile(owner,id,normal,forward).bodies();}
    }
}

package dev.jujutsuvfx.server;

import dev.jujutsuvfx.compat.CleaveCompatibility;
import dev.jujutsuvfx.entity.SorcererCat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import java.util.*;

/** Small, shared per-level terrain budget. Route probes never mutate the world. */
final class CatTerrain {
    private CatTerrain(){}
    private static long lastWarning;
    private static void failed(Throwable failure){
        long now=System.nanoTime();if(lastWarning==0||now-lastWarning>30_000_000_000L){lastWarning=now;
            com.mojang.logging.LogUtils.getLogger().warn("Cat obstacle clearing skipped a failing terrain callback",failure);}
    }
    private static final Map<ServerLevel,Budget> BUDGETS=new WeakHashMap<>();
    private record Cell(BlockPos pos,BlockState state){}
    private static final class Budget {long tick;int used;final Map<UUID,Integer> actors=new HashMap<>();}
    static boolean mover(SorcererCat cat){return (cat.isSukuna()||cat.isMahoraga())&&TechniqueTerrainRules.enabled();}
    private static boolean loaded(ServerLevel level,BlockPos pos){
        if(!level.isInWorldBounds(pos)||!level.getWorldBorder().isWithinBounds(pos)||!CleaveCompatibility.worldBlock(level,pos))return false;
        for(int x=(pos.getX()-1)>>4;x<=(pos.getX()+1)>>4;x++)for(int z=(pos.getZ()-1)>>4;z<=(pos.getZ()+1)>>4;z++)
            if(level.getChunkSource().getChunkNow(x,z)==null)return false;
        return true;
    }
    private static boolean breakable(SorcererCat cat,ServerLevel level,BlockPos pos,BlockState state){
        return loaded(level,pos)&&state.getFluidState().isEmpty()&&state.getDestroySpeed(level,pos)>=0
                &&state.canEntityDestroy(level,pos,cat);
    }
    /** Permit an obstructed route only when every intersecting solid can be cleared later. */
    static boolean route(SorcererCat cat,Vec3 from,Vec3 to){
        try{return probe(cat,from,to);}catch(RuntimeException|LinkageError failure){failed(failure);return false;}
    }
    private static boolean probe(SorcererCat cat,Vec3 from,Vec3 to){
        if(!mover(cat)||!(cat.level() instanceof ServerLevel level)||!TechniqueTerrainRules.canGrief(cat))return false;
        int steps=Math.max(1,(int)Math.ceil(from.distanceTo(to)/.65));if(steps>86)return false;
        for(int i=1;i<=steps;i++){
            AABB box=bodyBox(cat,from.lerp(to,i/(double)steps));
            for(BlockPos p:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ))){
                if(!loaded(level,p))return false;
                BlockState s=level.getBlockState(p);
                if(!s.getFluidState().isEmpty())return false;
                if(!s.getCollisionShape(level,p).isEmpty()&&!breakable(cat,level,p,s))return false;
            }
        }
        return true;
    }
    private static AABB bodyBox(SorcererCat cat,Vec3 point){
        AABB b=cat.getBoundingBox().move(point.subtract(cat.position())).inflate(.12,0,.12);
        return new AABB(b.minX,b.minY+.20,b.minZ,b.maxX,b.maxY+.15,b.maxZ);
    }
    static void clearStep(SorcererCat cat,Vec3 from,Vec3 to){
        if(!mover(cat)||from.distanceToSqr(to)>2.5*2.5||from.distanceToSqr(to)<.0001)return;
        AABB box=bodyBox(cat,from).minmax(bodyBox(cat,to));
        if(box.getXsize()*box.getYsize()*box.getZsize()>192)return;
        List<BlockPos> cells=new ArrayList<>();
        for(BlockPos p:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ)))
            if(cells.size()<256)cells.add(p.immutable());
        clear(cat,cells,24);
    }
    /** Leave selected originals alone, including any excluded immovable cells; clear their outer seam. */
    static void clearRing(SorcererCat cat,List<BlockPos> selected){
        if(!TechniqueTerrainRules.cleavePhysics()||selected.isEmpty()||selected.size()>256)return;
        Set<BlockPos> keep=new HashSet<>(selected),seam=new LinkedHashSet<>();
        for(BlockPos p:selected)for(Direction side:Direction.values()){BlockPos q=p.relative(side);if(!keep.contains(q)&&dev.jujutsuvfx.core.StructureGrabSafety.allowed(
                cat.getX(),cat.getY(),cat.getZ(),q.getX()+.5,q.getY()+.5,q.getZ()+.5))seam.add(q);}
        clear(cat,seam,32);
    }
    private static void clear(SorcererCat cat,Collection<BlockPos> cells,int limit){
        try{clearBatch(cat,cells,limit);}catch(RuntimeException|LinkageError failure){failed(failure);}
    }
    private static void clearBatch(SorcererCat cat,Collection<BlockPos> cells,int limit){
        if(!(cat.level() instanceof ServerLevel level)||!TechniqueTerrainRules.canGrief(cat))return;
        Budget b=BUDGETS.computeIfAbsent(level,k->new Budget());
        if(b.tick!=level.getGameTime()){b.tick=level.getGameTime();b.used=0;b.actors.clear();}
        UUID id=cat.getUUID();int remaining=Math.min(limit,Math.min(128-b.used,32-b.actors.getOrDefault(id,0)));
        if(remaining<=0)return;
        List<Cell> valid=new ArrayList<>();
        for(BlockPos pos:cells){
            if(!loaded(level,pos))continue;BlockState state=level.getBlockState(pos);
            if(state.isAir()||!breakable(cat,level,pos,state))continue;
            if(!ForgeEventFactory.onEntityDestroyBlock(cat,pos,state)||!TechniqueTerrainRules.enabled())continue;
            if(level.getBlockState(pos)!=state)continue;
            valid.add(new Cell(pos,state));if(valid.size()>=remaining)break;
        }
        if(valid.isEmpty())return;
        List<Cell> effects=new ArrayList<>();
        try(SkillBlockDrops.Scope ignored=SkillBlockDrops.quietTerrain(level);
            SkillHangingBreaks attachments=SkillHangingBreaks.capture(level,valid.stream().map(Cell::pos).toList())){
            for(Cell cell:valid){
                BlockPos pos=cell.pos;
                if(!TechniqueTerrainRules.enabled()||!cat.isAlive()||cat.level()!=level)break;
                if(!loaded(level,pos))continue;
                BlockState state=level.getBlockState(pos);if(state!=cell.state||state.isAir()||!breakable(cat,level,pos,state))continue;
                // Charge the budget before callbacks, including failed removals.
                b.used++;b.actors.merge(id,1,Integer::sum);
                CleaveCompatibility.beforeWorldBreak(level,pos);
                if(level.getBlockState(pos)==state&&level.destroyBlock(pos,false,cat)&&effects.size()<2)effects.add(cell);
            }
        }
        for(Cell cell:effects)level.levelEvent(2001,cell.pos,Block.getId(cell.state));
    }
}

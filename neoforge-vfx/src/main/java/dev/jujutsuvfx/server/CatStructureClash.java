package dev.jujutsuvfx.server;

import com.mojang.logging.LogUtils;
import dev.jujutsuvfx.compat.CleaveCompatibility;
import dev.jujutsuvfx.compat.StructureThrowBridge;
import dev.jujutsuvfx.core.CatCombatMoves;
import dev.jujutsuvfx.core.CatSpell;
import dev.jujutsuvfx.core.FurnaceFlight;
import dev.jujutsuvfx.core.SukunaSlashStyle;
import dev.jujutsuvfx.entity.SorcererCat;
import dev.jujutsuvfx.network.CatSpellState;
import dev.jujutsuvfx.network.SukunaCut;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** One finite building-throw beat. CatDuel owns the actors; this session owns only its projectile/VFX. */
public final class CatStructureClash {
    public static final int DURATION=144,THROW_TICK=80;
    private static final int SCAN_PER_TICK=1152,MAX_BLOCKS=144,MAX_GHOSTS=48;
    private final SorcererCat gojo,sukuna;
    private final ServerLevel level;
    private final Vec3 forward,right,scanOrigin;
    private final int session,poseSequence;
    private final boolean safeSpacing;
    private final List<Ghost> ghosts=new ArrayList<>();
    private final Set<UUID> fragments=new LinkedHashSet<>();
    private StructureThrowBridge.Body body;
    private Vec3 point,source,hover,throwStart,impact,cutFocus;
    private float filmingRadius=4;
    private BlockPos seed;
    private List<BlockPos> liftedCells=List.of();
    private double bestScore=-Double.MAX_VALUE,boundRadius=3;
    private long lastTick=Long.MIN_VALUE;
    private int age,scanIndex,firstCut=-1,cuts;
    private boolean prepared,closed,physical,failed,blueEnded;

    private CatStructureClash(SorcererCat gojo,SorcererCat sukuna){
        this.gojo=gojo;this.sukuna=sukuna;level=(ServerLevel)gojo.level();
        Vec3 facing=sukuna.position().subtract(gojo.position()).multiply(1,0,1).normalize();
        forward=facing.lengthSqr()<.9?new Vec3(0,0,1):facing;
        right=FurnaceFlight.right(forward);
        scanOrigin=gojo.position();point=gojo.position().add(right.scale(5)).add(0,3,0);source=point;
        double separation=gojo.position().subtract(sukuna.position()).multiply(1,0,1).lengthSqr();
        safeSpacing=separation>=12*12&&separation<=40*40;
        session=PlayerSpellSync.nextId(gojo.getServer());poseSequence=session&0x3fffffff;
    }
    public static CatStructureClash begin(SorcererCat gojo,SorcererCat sukuna){
        if(!(gojo.level() instanceof ServerLevel)||gojo.level()!=sukuna.level())throw new IllegalArgumentException("Duel actors must share a server level");
        return new CatStructureClash(gojo,sukuna);
    }
    public int age(){return age;}
    public int duration(){return DURATION;}
    public boolean finished(){return closed||age>=DURATION;}
    public boolean physical(){return physical;}
    public Vec3 focus(){return point;}
    /** Keep filming the intact body, then its impact site rather than a changing fragment ID. */
    public Vec3 cameraFocus(){return cutFocus==null?point:cutFocus;}
    public float cameraRadius(){return filmingRadius;}
    public float radius(){
        double radius=boundRadius+1;
        for(Ghost g:ghosts)if(!g.entity.isRemoved())radius=Math.max(radius,g.entity.position().distanceTo(point)+1.8);
        return (float)radius;
    }
    /** Called by CatDuel's per-actor control path, so ordinary AI cannot replace this held pose. */
    public void pose(SorcererCat cat){
        if(cat!=gojo&&cat!=sukuna||closed)return;
        if(cat==gojo&&age<96){
            int frame=age<24?age/3:age<THROW_TICK?8+(age-24)/14:12+age-THROW_TICK;
            cat.publishCombat(CatCombatMoves.pack(poseSequence,CatCombatMoves.BLUE_CAST,frame,SorcererCat.RIGHT,1));
        }else if(cat==sukuna&&age>=84&&age<132){
            int frame=firstCut<0?Math.min(27,age-84):28+age-firstCut;
            cat.publishCombat(CatCombatMoves.pack(poseSequence+1,CatCombatMoves.CLEAVE_CAST,Math.min(55,frame),SorcererCat.RIGHT,1));
        }else cat.publishCombat(-1);
    }
    public void tick(){
        if(finished()||lastTick==level.getGameTime())return;lastTick=level.getGameTime();
        if(!gojo.isAlive()||!sukuna.isAlive()||gojo.level()!=level||sukuna.level()!=level){cleanup();return;}
        try {
            // Scan elevated terrain before deciding whether an existing body is preferable.
            if(age<24&&body==null)scan();
            if(age>=24&&!prepared)prepare();
            if(prepared){
                if(physical&&age<48&&!liftedCells.isEmpty())CatTerrain.clearRing(gojo,liftedCells);
                updateBody();
                if(age<THROW_TICK)lift();
                else if(firstCut<0)flight();
                if(firstCut>=0&&cuts<4&&age>=firstCut+cuts*dev.jujutsuvfx.core.SukunaSlashCharge.BURST_INTERVAL)cut();
                updateGhosts();
                if(!blueEnded&&age%3==0)blue(false);
            }
        }catch(RuntimeException|LinkageError error){
            if(!failed)LogUtils.getLogger().warn("Building throw stopped its physics operations; the finite scene can still finish",error);
            failed=true;body=null;
            // Never delete real bodies or restore a launched object into the source building.
            if(!prepared||hover==null||impact==null){prepared=true;source=point;hover=point.add(0,3,0);impact=sukuna.position().add(0,5,0);}
            if(!physical&&ghosts.isEmpty())ghosts(List.of());
        }
        pose(gojo);pose(sukuna);age++;
        if(age>=DURATION)cleanup();
    }
    private void scan(){
        // 29*29*32 cells over the preparation, never forcing a chunk load or scanning the entire arena.
        int end=Math.min(29*29*32,scanIndex+SCAN_PER_TICK);
        for(;scanIndex<end;scanIndex++){
            int i=scanIndex,x=i%29-14,z=(i/29)%29-14,y=30-i/(29*29);
            BlockPos pos=BlockPos.containing(scanOrigin).offset(x,y,z);
            if(!grabAllowed(pos)||!loaded(pos))continue;
            BlockState state=level.getBlockState(pos);if(!simple(pos,state))continue;
            String name=BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            boolean built=name.contains("brick")||name.contains("plank")||name.contains("concrete")||name.contains("glass")||name.contains("quartz")||name.contains("terracotta");
            double score=(built?18:0)+Math.max(0,y)*4.0-Math.sqrt(x*x+z*z)*.55;
            if(score>bestScore){bestScore=score;seed=pos.immutable();}
        }
    }
    private boolean grabAllowed(BlockPos pos){
        return dev.jujutsuvfx.core.StructureGrabSafety.allowed(scanOrigin.x,scanOrigin.y,scanOrigin.z,
                pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)
                &&dev.jujutsuvfx.core.StructureGrabSafety.allowed(gojo.getX(),gojo.getY(),gojo.getZ(),
                pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
    }
    private boolean bodyAllowed(StructureThrowBridge.Body candidate){
        if(candidate==null)return false;
        Vec3 p=candidate.center();return dev.jujutsuvfx.core.StructureGrabSafety.bodyAllowed(
                gojo.getX(),gojo.getY(),gojo.getZ(),p.x,p.y,p.z,candidate.radius());
    }
    private boolean loaded(BlockPos pos){
        if(!level.isInWorldBounds(pos)||!level.getWorldBorder().isWithinBounds(pos)||!CleaveCompatibility.worldBlock(level,pos))return false;
        for(int x=(pos.getX()-1)>>4;x<=(pos.getX()+1)>>4;x++)for(int z=(pos.getZ()-1)>>4;z<=(pos.getZ()+1)>>4;z++)
            if(level.getChunkSource().getChunkNow(x,z)==null)return false;
        return true;
    }
    private boolean simple(BlockPos pos,BlockState state){return !state.isAir()&&state.getFluidState().isEmpty()&&!state.hasBlockEntity()
            &&state.getDestroySpeed(level,pos)>=0&&!state.getCollisionShape(level,pos).isEmpty();}
    private List<BlockPos> collect(){
        if(seed==null)return List.of();
        ArrayDeque<BlockPos> queue=new ArrayDeque<>();Set<BlockPos> seen=new HashSet<>();List<BlockPos> blocks=new ArrayList<>();queue.add(seed);seen.add(seed);
        int inspected=0;
        while(!queue.isEmpty()&&blocks.size()<MAX_BLOCKS&&inspected++<768){
            BlockPos pos=queue.removeFirst();if(!grabAllowed(pos)||!loaded(pos)||!simple(pos,level.getBlockState(pos)))continue;
            blocks.add(pos);
            for(Direction d:Direction.values()){BlockPos next=pos.relative(d);
                if(Math.abs(next.getX()-seed.getX())<=3&&Math.abs(next.getY()-seed.getY())<=2&&Math.abs(next.getZ()-seed.getZ())<=3&&seen.add(next))queue.addLast(next);
            }
        }return blocks;
    }
    private void prepare(){
        if(body==null&&safeSpacing&&TechniqueTerrainRules.cleavePhysics()){
            var existing=StructureThrowBridge.acquire(gojo,scanOrigin.add(0,10,0));
            if(bodyAllowed(existing)&&(seed==null||existing.center().y>=seed.getY()-2))body=existing;
        }
        if(body!=null){body=StructureThrowBridge.inspect(gojo,body.id());if(!bodyAllowed(body))body=null;}
        prepared=true;List<BlockPos> selected=body==null?collect():List.of();
        if(body==null&&!failed&&safeSpacing&&TechniqueTerrainRules.cleavePhysics()&&selected.size()>=8)body=StructureThrowBridge.assemble(gojo,selected);
        physical=body!=null;
        if(physical&&!selected.isEmpty())liftedCells=List.copyOf(selected);
        if(physical){point=source=body.center();boundRadius=body.radius();fragments.add(body.id());}
        else {
            if(!selected.isEmpty()){Vec3 center=Vec3.ZERO;for(BlockPos p:selected)center=center.add(p.getCenter());point=source=center.scale(1.0/selected.size());}
            ghosts(selected);
        }
        Vec3 above=gojo.position().add(right.scale(4.5)).add(forward.scale(2));
        hover=new Vec3(above.x,Math.max(gojo.getY()+boundRadius+3.2,source.y+3.2),above.z);
        impact=sukuna.position().subtract(forward.scale(5)).add(0,boundRadius+2.5,0);
        // Shattering may spread the physics envelope very far; that must not drive camera zoom.
        filmingRadius=Mth.clamp(radius(),2,24);
    }
    private void updateBody(){
        if(failed)return;
        if(!TechniqueTerrainRules.cleavePhysics()){body=null;return;}
        if(body!=null){
            var current=StructureThrowBridge.inspect(gojo,body.id());
            if(current!=null){body=current;point=current.center();boundRadius=current.radius();}
            else if(age>34)body=null;
        }
        if(firstCut>=0){double r=1;for(UUID id:fragments){var f=StructureThrowBridge.inspect(gojo,id);if(f!=null)r=Math.max(r,f.center().distanceTo(point)+f.radius());}boundRadius=r;}
    }
    private void lift(){
        float t=smooth((age-24)/34f);Vec3 desired=source.lerp(hover,t);
        if(body!=null&&!failed){Vec3 velocity=desired.subtract(point).scale(4.5).add(0,.5,0);StructureThrowBridge.velocity(gojo,body.id(),velocity,new Vec3(0,.28,0));}
        else if(!physical)point=desired;
    }
    private void flight(){
        if(throwStart==null)throwStart=point;
        if(body!=null&&!failed){
            Vec3 to=impact.subtract(point);double remaining=Math.max(.35,(110-age)/20.0);
            StructureThrowBridge.velocity(gojo,body.id(),to.scale(1/remaining).add(0,.25,0),new Vec3(.18,.9,.12));
        }else if(!physical)point=throwStart.lerp(impact,Mth.clamp((age-THROW_TICK)/30f,0,1));
        // Even a short throw must have a visible flight segment before the counter-cut.
        if(age>=92&&(point.distanceTo(sukuna.position().add(0,boundRadius+2.5,0))<boundRadius+7||age>=110)){
            firstCut=age;cutFocus=point;blue(true);
            if(body!=null&&!failed)StructureThrowBridge.velocity(gojo,body.id(),Vec3.ZERO,new Vec3(0,.4,0));
        }
    }
    private void cut(){
        Vec3 towardsGojo=forward.scale(-1),up=new Vec3(0,1,0);
        int seed=gojo.getRandom().nextInt();Vec3 stroke=SukunaSlashStyle.rotateStroke(seed,up,towardsGojo),normal=towardsGojo.cross(stroke).normalize();
        Vec3 cutPoint=point;double cutRadius=Math.max(3,boundRadius);boolean severed=!physical;
        if(physical&&!failed&&TechniqueTerrainRules.cleavePhysics()){
            StructureThrowBridge.Body biggest=null;
            for(UUID id:fragments){var f=StructureThrowBridge.inspect(sukuna,id);if(f!=null&&(biggest==null||f.radius()>biggest.radius()))biggest=f;}
            if(biggest!=null){
                if(!SukunaSlashSkills.reservePhysics(level.getServer(),1))return;
                cutPoint=biggest.center();cutRadius=biggest.radius();
                List<UUID> next=StructureThrowBridge.cut(sukuna,biggest.id(),normal,towardsGojo);
                if(!next.isEmpty()){fragments.remove(biggest.id());fragments.addAll(next);severed=true;}
            }
        }
        boolean cross=!physical&&SukunaSlashStyle.isCross(seed);
        CatCleave.visual(level,cutPoint,towardsGojo,stroke,(float)Math.min(112,cutRadius*2.25),severed,seed,cross);
        if(!physical){
            Vec3 cutNormal=normal;
            for(Ghost g:ghosts){
                Vec3 offset=g.local.yRot(-(age-24)*1.1f*Mth.DEG_TO_RAD).add(g.offset);
                double side=offset.dot(cutNormal)>=0?1:-1;
                g.velocity=g.velocity.add(cutNormal.scale(side*.15)).add(towardsGojo.scale(.035));
                if(cross){Vec3 second=towardsGojo.cross(cutNormal).normalize();g.velocity=g.velocity.add(second.scale((offset.dot(second)>=0?1:-1)*.10));}
            }
        }
        cuts++;
    }
    private void blue(boolean end){
        if(blueEnded)return;
        if(end)blueEnded=true;
        new CatSpellState(session,gojo.getId(),gojo.getUUID(),CatSpell.BLUE,false,end?CatSpell.END:CatSpell.FLIGHT,
                age,Math.max(0,age-24),end?0:DURATION-age,1.15f,point,forward,gojo.getEyePosition(),0).broadcast(level);
    }
    private void ghosts(List<BlockPos> selected){
        int count=Math.min(MAX_GHOSTS,selected.isEmpty()?27:selected.size());
        for(int i=0;i<count;i++){
            BlockPos original=selected.isEmpty()?null:selected.get(i*selected.size()/count);
            BlockState state=original==null?Blocks.STONE_BRICKS.defaultBlockState():level.getBlockState(original);
            Vec3 offset=original==null?new Vec3(i%3-1,(i/3)%3-1,i/9-1):Vec3.atLowerCornerOf(original).subtract(source);
            GhostBlock entity=new GhostBlock(level);CompoundTag tag=new CompoundTag();tag.put("block_state",NbtUtils.writeBlockState(state));
            tag.putInt("interpolation_duration",2);tag.putInt("teleport_duration",2);tag.putFloat("view_range",2);
            entity.load(tag);entity.setNoGravity(true);entity.setInvulnerable(true);entity.setPos(source.add(offset));
            if(level.addFreshEntity(entity))ghosts.add(new Ghost(entity,offset));
        }
        boundRadius=3.8;
    }
    private void updateGhosts(){
        float yaw=(age-24)*1.1f;double shatter=firstCut<0?0:Math.max(0,age-firstCut);
        for(Ghost g:ghosts){
            if(firstCut>=0){g.velocity=g.velocity.add(0,-.018,0);g.offset=g.offset.add(g.velocity);}
            Vec3 drift=g.offset;
            Vec3 p=point.add(g.local.yRot(-yaw*Mth.DEG_TO_RAD)).add(drift);
            g.entity.setPos(p);g.entity.setYRot(yaw+(float)(shatter*g.velocity.x*18));
        }
    }
    public void cleanup(){
        if(closed)return;closed=true;blue(true);
        for(Ghost g:ghosts)g.entity.discard();ghosts.clear();
        // Real projectiles/fragments are ordinary physical structures and keep their final velocity.
    }
    private static final class Ghost {
        final GhostBlock entity;final Vec3 local;Vec3 velocity=Vec3.ZERO,offset=Vec3.ZERO;
        Ghost(GhostBlock entity,Vec3 local){this.entity=entity;this.local=local;}
    }
    private static final class GhostBlock extends Display.BlockDisplay {
        GhostBlock(ServerLevel level){super(EntityType.BLOCK_DISPLAY,level);}
        @Override public boolean shouldBeSaved(){return false;}
    }
    private static float smooth(float v){float t=Mth.clamp(v,0,1);return t*t*(3-2*t);}
}

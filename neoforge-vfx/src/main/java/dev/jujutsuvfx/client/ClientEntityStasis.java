package dev.jujutsuvfx.client;

import dev.jujutsuvfx.core.FrozenPose;
import dev.jujutsuvfx.network.EntityStasis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Stop client simulation and render interpolation too; server-only position locks still animate. */
public final class ClientEntityStasis {
    private static ClientLevel owner;
    private static final Map<UUID,Entry> FROZEN=new HashMap<>();
    private static final class Entry {
        EntityStasis packet;Entity entity;FrozenPose pose;
        Entry(EntityStasis packet){this.packet=packet;}
    }
    private static void ensureLevel(){
        ClientLevel current=Minecraft.getInstance().level;
        changeLevel(current);
    }
    public static void receive(EntityStasis packet){
        ensureLevel();if(owner==null)return;
        if(!packet.frozen()){
            Entry entry=FROZEN.remove(packet.uuid());
            if(entry!=null&&entry.pose!=null&&entry.entity!=null)entry.pose.release(entry.entity);
            return;
        }
        Entry entry=FROZEN.computeIfAbsent(packet.uuid(),id->new Entry(packet));entry.packet=packet;
        Entity entity=owner.getEntity(packet.entityId());
        if(entity!=null&&entity.getUUID().equals(packet.uuid()))hold(entity);
    }
    public static boolean hold(Entity entity){
        ensureLevel();if(entity.level()!=owner||entity.isRemoved()||!entity.isAlive())return false;
        Entry entry=FROZEN.get(entity.getUUID());
        if(entry==null)return false;
        if(entry.entity!=entity){
            EntityStasis packet=entry.packet;
            entity.setPos(packet.position());entity.setYRot(packet.yaw());entity.setXRot(packet.pitch());
            entity.setDeltaMovement(packet.velocity());entity.fallDistance=packet.fallDistance();
            entry.entity=entity;entry.pose=new FrozenPose(entity);
        }
        entry.pose.hold(entity);return true;
    }
    public static boolean localLocked(){
        ensureLevel();var player=Minecraft.getInstance().player;
        return player!=null&&player.isAlive()&&FROZEN.containsKey(player.getUUID());
    }
    public static float renderPartial(Entity entity,float partial){
        if(hold(entity))return 0;
        Entity vehicle=entity.getVehicle();
        return vehicle!=null&&hold(vehicle)?0:partial;
    }
    public static void changeLevel(ClientLevel current){if(owner!=current){FROZEN.clear();owner=current;}}
}

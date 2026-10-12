package dev.jujutsuvfx.client;

import dev.jujutsuvfx.network.DuelScene;
import dev.jujutsuvfx.entity.SorcererCat;
import net.minecraft.client.Minecraft;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stable server-cued fractures attached to the exact cat-owned field, including its visual outro. */
final class DomainBarrierEffects {
    private record Fracture(int actor,long born,int duration,int style){}
    private static final Map<Integer,Fracture> FRACTURES=new LinkedHashMap<>();
    private DomainBarrierEffects(){}
    static void clear(){FRACTURES.clear();}
    static void begin(DuelScene scene){
        if(scene.phase()!=DuelScene.DOMAIN_BREAK&&scene.phase()!=DuelScene.RESCUE_CUT)return;
        if(FRACTURES.size()>=16)FRACTURES.remove(FRACTURES.keySet().iterator().next());
        FRACTURES.put(scene.gojo(),new Fracture(scene.gojo(),ClientEffectClock.ticks()-scene.age(),
                scene.phase()==DuelScene.DOMAIN_BREAK?44:28,scene.phase()==DuelScene.DOMAIN_BREAK?0:1));
    }
    private static Fracture find(UnlimitedVoid field){
        long now=ClientEffectClock.ticks();FRACTURES.values().removeIf(f->now-f.born>f.duration+22);
        var world=Minecraft.getInstance().level;if(world==null)return null;
        for(Fracture f:FRACTURES.values())if(world.getEntity(f.actor) instanceof SorcererCat cat
                &&CatSpells.cameraVoid(cat.getUUID())==field)return f;
        return null;
    }
    static MalevolentShrine attacker(UnlimitedVoid field){
        Fracture fracture=find(field);MalevolentShrine shrine=CatSpells.shrine();DomainClash clash=CatSpells.clash();
        return fracture!=null&&fracture.style==0&&shrine!=null&&clash!=null
                &&clash.shrineSession==shrine.session&&CatSpells.gojo()==field?shrine:null;
    }
    static boolean destroying(UnlimitedVoid field){return field!=null&&find(field)!=null;}
    static float progress(UnlimitedVoid field,float partial){
        Fracture f=find(field);return f==null?0:Math.max(.001f,Math.min(1,(ClientEffectClock.ticks()-f.born+partial)/f.duration));
    }
    static float style(UnlimitedVoid field){Fracture f=find(field);return f==null?0:f.style;}
}

package dev.jujutsuvfx.server;

import dev.jujutsuvfx.JujutsuVfx;
import dev.jujutsuvfx.entity.SorcererCat;
import dev.jujutsuvfx.item.FistCombo;
import dev.jujutsuvfx.item.VfxItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Nine remembered damage families. Only confirmed hits/active control contacts teach resistance. */
@EventBusSubscriber(modid=JujutsuVfx.ID)
public final class CatAdaptation {
    public enum Phenomenon {
        IMPACT("impact",80), SLASH("slash",60), PROJECTILE("projectile",80), FIRE("fire",70),
        EXPLOSION("explosion",90), MAGIC("magic",100), ENVIRONMENT("environment",90),
        CURSED("cursed",90), BLACK_FLASH("black_flash",120), BLUE("blue",100), RED("red",110),
        PURPLE("purple",160), VOID("void",100), INFINITY("infinity",120);
        public final String id;
        public final int stepTicks;
        Phenomenon(String id,int ticks){this.id=id;stepTicks=ticks;}
        static Phenomenon find(String id){for(var kind:values())if(kind.id.equals(id))return kind;return null;}
    }
    private static final Map<MinecraftServer,Map<UUID,SorcererCat>> ACTIVE=new WeakHashMap<>();
    public static final int MEMORY_LIMIT=9, FULL_ADAPTATION_HITS=9;
    private static final class Analysis {
        int hits;
        long lastContact=-1000;
        boolean lastWasControl;
        int learnedHits(){return Math.min(FULL_ADAPTATION_HITS,hits);}
        int stage(){return learnedHits()*4/FULL_ADAPTATION_HITS;}
        float resistance(){return learnedHits()*.10f;}
    }
    private final SorcererCat cat;
    // Oldest encounter first; replacing a record moves it to the newest end.
    private final LinkedHashMap<Phenomenon,Analysis> learned=new LinkedHashMap<>();
    private Phenomenon latest;
    private int revision;
    private boolean dirty;
    private long lastTick=Long.MIN_VALUE;
    public CatAdaptation(SorcererCat cat){this.cat=cat;}
    public static boolean enabled(Entity entity){return entity instanceof SorcererCat cat&&cat.isMahoraga()&&cat.isAlive()&&!cat.isRemoved();}
    public static void track(SorcererCat cat){
        if(cat.level() instanceof ServerLevel level&&enabled(cat))
            ACTIVE.computeIfAbsent(level.getServer(),key->new LinkedHashMap<>()).put(cat.getUUID(),cat);
    }
    public int stage(Phenomenon kind){Analysis value=learned.get(kind);return value==null?0:value.stage();}
    public static int stage(Entity entity,Phenomenon kind){
        return enabled(entity)?((SorcererCat)entity).adaptation().stage(kind):0;
    }
    public static float controlScale(Entity entity,Phenomenon kind){
        if(!enabled(entity))return 1;
        Analysis value=((SorcererCat)entity).adaptation().learned.get(kind);
        return value==null?1:1-value.learnedHits()/(float)FULL_ADAPTATION_HITS;
    }
    public static int controlDuration(Entity entity,Phenomenon kind,int ticks){
        return Math.max(0,Math.round(ticks*controlScale(entity,kind)));
    }
    public static void touch(Entity entity,Phenomenon kind){
        // Persistent fields count one exposure per second, never every collision check.
        if(enabled(entity))((SorcererCat)entity).adaptation().observe(kind,false,20);
    }
    public void damaged(DamageSource source){
        if(!enabled(cat))return;
        Phenomenon kind=phenomenon(source);
        if(kind!=null)observe(kind,true,0);
    }
    public float damageScale(DamageSource source){
        if(!enabled(cat))return 1;
        Phenomenon kind=phenomenon(source);Analysis value=learned.get(kind);
        // Called before recording this hit: the first hit is full damage, the tenth is 10%.
        if(value==null)return 1;
        int hits=value.hits;
        if(value.lastWasControl&&cat.getServer()!=null&&value.lastContact==cat.getServer().getTickCount())hits--;
        return 1-Math.min(FULL_ADAPTATION_HITS,Math.max(0,hits))*.10f;
    }
    public static void infinityContact(Entity attacker){
        if(enabled(attacker))((SorcererCat)attacker).adaptation().observe(Phenomenon.INFINITY,false,1);
    }
    public static boolean piercesInfinity(Entity attacker){return stage(attacker,Phenomenon.INFINITY)>=4;}
    private void observe(Phenomenon kind,boolean damage,int repeatInterval){
        if(!(cat.level() instanceof ServerLevel level)||!enabled(cat))return;
        long now=level.getServer().getTickCount();Analysis value=learned.get(kind);
        if(value!=null){
            if(!damage&&now-value.lastContact<repeatInterval)return;
            // A field may call touch just before hurt in this same tick. It is one encounter.
            if(damage&&value.lastWasControl&&value.lastContact==now){
                value.lastWasControl=false;return;
            }
        }else value=new Analysis();
        learned.remove(kind);
        while(learned.size()>=MEMORY_LIMIT)learned.remove(learned.keySet().iterator().next());
        learned.put(kind,value);
        int previous=value.learnedHits();value.hits=Math.min(9999,value.hits+1);
        value.lastContact=now;value.lastWasControl=!damage;
        latest=kind;dirty=true;
        if(value.learnedHits()>previous){
            revision++;cat.turnWheel();
            cat.playSound(SoundEvents.IRON_TRAPDOOR_CLOSE,.38f,.85f);
            if(value.learnedHits()==FULL_ADAPTATION_HITS)cat.playSound(SoundEvents.AMETHYST_BLOCK_CHIME,.65f,1.05f);
        }
        track(cat);
    }
    private boolean advance(long now){
        if(lastTick==now)return true;
        lastTick=now;cat.tickWheelAnimation();
        // There is no passive learning timer and adaptation never restores health.
        if(dirty)publish();
        return cat.wheelTurning();
    }
    private void publish(){
        CompoundTag display=new CompoundTag();ListTag rows=new ListTag();
        for(var pair:learned.entrySet()){
            Analysis value=pair.getValue();CompoundTag row=new CompoundTag();
            row.putString("Kind",pair.getKey().id);row.putInt("Hits",value.hits);
            row.putInt("Reduction",value.learnedHits()*10);row.putInt("Stage",value.stage());
            row.putInt("Percent",value.learnedHits()*100/FULL_ADAPTATION_HITS);rows.add(row);
        }
        display.put("Rows",rows);display.putInt("Revision",revision);display.putInt("Limit",MEMORY_LIMIT);
        if(latest!=null)display.putString("Latest",latest.id);
        cat.publishAdaptation(display);dirty=false;
    }
    public void save(CompoundTag tag){
        ListTag rows=new ListTag();
        for(var pair:learned.entrySet()){
            CompoundTag row=new CompoundTag();row.putString("Kind",pair.getKey().id);
            row.putInt("Hits",pair.getValue().hits);rows.add(row);
        }
        CompoundTag state=new CompoundTag();state.putInt("Schema",2);state.put("Knowledge",rows);
        if(latest!=null)state.putString("Latest",latest.id);tag.put("PhenomenonAdaptation",state);
    }
    public void load(CompoundTag tag){
        learned.clear();latest=null;revision=0;lastTick=Long.MIN_VALUE;
        if(!cat.isMahoraga()){publish();return;}
        CompoundTag state=tag.getCompound("PhenomenonAdaptation");ListTag rows=state.getList("Knowledge",10);
        Phenomenon savedLatest=Phenomenon.find(state.getString("Latest"));Analysis latestValue=null;
        for(int i=0;i<Math.min(rows.size(),Phenomenon.values().length);i++){
            CompoundTag row=rows.getCompound(i);Phenomenon kind=Phenomenon.find(row.getString("Kind"));if(kind==null)continue;
            Analysis value=new Analysis();
            // Schema 1 progress becomes the nearest equivalent resistance, without replaying animations.
            int hits=state.getInt("Schema")>=2?row.getInt("Hits"):
                    Math.max(1,Math.round(Math.max(0,row.getInt("Progress"))/(float)(kind.stepTicks*4)*FULL_ADAPTATION_HITS));
            value.hits=Math.max(0,Math.min(9999,hits));if(value.hits==0)continue;
            learned.remove(kind);learned.put(kind,value);if(kind==savedLatest)latestValue=value;
        }
        if(latestValue!=null){learned.remove(savedLatest);learned.put(savedLatest,latestValue);}
        while(learned.size()>MEMORY_LIMIT)learned.remove(learned.keySet().iterator().next());
        for(var kind:learned.keySet())latest=kind;
        publish();
    }
    public void resume(){if(enabled(cat)&&(dirty||cat.wheelTurning()))track(cat);}
    public static Phenomenon phenomenon(DamageSource source){
        if(source.is(DamageTypes.FELL_OUT_OF_WORLD)||source.is(DamageTypes.GENERIC_KILL)
                ||source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))return null;
        if(source instanceof CatMeleeDamage && source.getEntity() instanceof SorcererCat bladeCat
                &&bladeCat.isMahoraga()&&dev.jujutsuvfx.core.CatCombatMoves.blade(bladeCat.combatMove()))return Phenomenon.SLASH;
        if(source instanceof CatMeleeDamage strike)return switch(strike.energy()){
            case SorcererCat.BLUE->Phenomenon.BLUE;case SorcererCat.RED->Phenomenon.RED;
            case SorcererCat.CURSED->Phenomenon.CURSED;case SorcererCat.BLACK_FLASH->Phenomenon.BLACK_FLASH;
            default->Phenomenon.IMPACT;};
        String id=source.typeHolder().unwrapKey().map(k->k.location().toString()).orElse("");
        Phenomenon technique=switch(id){
            case "jujutsuvfx:sukuna_cleave","jujutsuvfx:shrine_cut"->Phenomenon.SLASH;
            case "jujutsuvfx:limitless_blue"->Phenomenon.BLUE;case "jujutsuvfx:limitless_red"->Phenomenon.RED;
            case "jujutsuvfx:hollow_purple"->Phenomenon.PURPLE;case "jujutsuvfx:furnace"->Phenomenon.FIRE;
            default->null;};
        if(technique!=null)return technique;
        if(source.is(DamageTypeTags.IS_FIRE))return Phenomenon.FIRE;
        if(source.is(DamageTypeTags.IS_EXPLOSION))return Phenomenon.EXPLOSION;
        if(GojoInfinity.projectile(source))return Phenomenon.PROJECTILE;
        if(source.is(DamageTypes.MAGIC)||source.is(DamageTypes.INDIRECT_MAGIC)||source.is(DamageTypes.WITHER))return Phenomenon.MAGIC;
        if(source.getEntity() instanceof LivingEntity attacker&&(source.is(DamageTypes.PLAYER_ATTACK)||source.is(DamageTypes.MOB_ATTACK))){
            if(attacker instanceof Player&&VfxItems.isHeld(attacker.getMainHandItem()))
                return FistCombo.ready(attacker.getMainHandItem())?Phenomenon.BLACK_FLASH:Phenomenon.CURSED;
            return attacker.getMainHandItem().is(ItemTags.SWORDS)||attacker.getMainHandItem().is(ItemTags.AXES)?Phenomenon.SLASH:Phenomenon.IMPACT;
        }
        return source.getEntity()==null?Phenomenon.ENVIRONMENT:Phenomenon.MAGIC;
    }
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void tick(ServerTickEvent.Post event){
        var cats=ACTIVE.get(event.getServer());if(cats==null)return;
        // Wheel callbacks can renew the active entry while this snapshot is advancing.
        for(var pair:List.copyOf(cats.entrySet())){
            SorcererCat cat=pair.getValue();
            if(!enabled(cat)||!(cat.level() instanceof ServerLevel level)||level.getServer()!=event.getServer()
                    ||level.getEntity(cat.getUUID())!=cat||!cat.adaptation().advance(event.getServer().getTickCount()))
                cats.remove(pair.getKey(),cat);
        }
        if(cats.isEmpty())ACTIVE.remove(event.getServer());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){ACTIVE.remove(event.getServer());}
}

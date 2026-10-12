package dev.jujutsuvfx.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.jujutsuvfx.entity.SorcererCat;
import dev.jujutsuvfx.network.CatCombatCue;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import org.joml.Vector2f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** World-oriented ink flame silhouettes. Bounded, sorted cards share the existing fist material. */
public final class CatCombatEffects {
    private record Cue(CatCombatCue data,long born){
        float age(float partial){return Math.max(0,(ClientEffectClock.ticks()-born+partial)*.05f);}
        Vec3 center(){return data.from().lerp(data.to(),.5);}
    }
    private record Flame(Vector3f center,Vector3f axis,float width,float length,int shape,float seed,float erosion,float alpha,boolean red){}
    private static final List<Cue> CUES=new ArrayList<>();
    private static final int CARD_BUDGET=112;
    private static long nextGuardSound;
    private CatCombatEffects(){}
    static void clear(){CUES.clear();nextGuardSound=0;InfinityBarrierRenderer.clear();MahoragaBladeRenderer.clear();MahoragaShadowRenderer.release();BlastWarningRenderer.clear();}
    static void tick(){MahoragaBladeRenderer.tick();CUES.removeIf(c->c.age(0)>.78f);}
    public static void receive(CatCombatCue packet){
        Minecraft mc=Minecraft.getInstance();ClientFistEffects.changeLevel(mc.level);
        if(mc.level==null||mc.player==null||packet.effect()<0||packet.effect()>CatCombatCue.DOMAIN_RESULT
                ||!finite(packet.from())||!finite(packet.to())||packet.from().distanceToSqr(packet.to())>56*56
                ||Math.min(EffectView.distanceToSqr(packet.from()),EffectView.distanceToSqr(packet.to()))>(packet.effect()==CatCombatCue.BLAST_WARNING||packet.effect()==CatCombatCue.DOMAIN_RESULT?192*192:112*112))return;
        if(packet.effect()==CatCombatCue.BLAST_WARNING){BlastWarningRenderer.receive(packet);return;}
        if(packet.effect()==CatCombatCue.DOMAIN_RESULT){ClientDomainResolution.receive(packet.actor(),packet.other(),packet.first()!=0);return;}
        if(packet.effect()==CatCombatCue.BLADE){MahoragaBladeRenderer.receive(packet);return;}
        if(packet.effect()==CatCombatCue.INFINITY){
            InfinityBarrierRenderer.hit(packet.actor());
            return;
        }
        if(packet.effect()==CatCombatCue.BLINK&&mc.level.getEntity(packet.actor()) instanceof SorcererCat cat&&!cat.isSukuna()
                &&Math.min(cat.distanceToSqr(packet.from()),cat.distanceToSqr(packet.to()))<12*12){
            Vec3 feet=packet.to().add(0,-.6,0);
            cat.lerpTo(feet.x,feet.y,feet.z,cat.getYRot(),cat.getXRot(),0,true);cat.setPos(feet);cat.setOldPosAndRot();cat.setDeltaMovement(Vec3.ZERO);
        }
        if((packet.effect()==CatCombatCue.GUARD||packet.effect()==CatCombatCue.BARRAGE)&&ClientEffectClock.ticks()>=nextGuardSound){
            nextGuardSound=ClientEffectClock.ticks()+2;
            Vec3 camera=mc.gameRenderer.getMainCamera().getPosition(),delta=packet.from().subtract(camera);
            double distance=delta.length();float strength=(float)dev.jujutsuvfx.platform.Numbers.clamp((112-distance)/96,.12,1);
            Vec3 audible=camera.add(delta.scale(distance<.01?0:Math.min(distance,12)/distance));
            mc.level.playLocalSound(audible.x,audible.y,audible.z,net.minecraft.sounds.SoundEvents.SHIELD_BLOCK,
                    net.minecraft.sounds.SoundSource.HOSTILE,1.35f*strength,.78f+Math.floorMod(packet.seed(),17)*.01f,false);
            mc.level.playLocalSound(audible.x,audible.y,audible.z,net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                    net.minecraft.sounds.SoundSource.HOSTILE,.65f*strength,.72f,false);
        }
        if(CUES.size()>=32)CUES.remove(0);CUES.add(new Cue(packet,ClientEffectClock.ticks()));
    }
    static void render(RenderLevelStageEvent event){
        MahoragaBladeRenderer.render(event);BlastWarningRenderer.render(event);MahoragaShadowRenderer.render(event);
        if(CUES.isEmpty()||!FistSpriteRenderer.ready())return;
        float partial=event.getPartialTick(),time=((ClientEffectClock.ticks()%24000)+partial)*.05f;
        Vec3 camera=event.getCamera().getPosition();
        var visible=new ArrayList<>(CUES);visible.sort(Comparator.comparingDouble(c->c.center().distanceToSqr(camera)));
        List<Flame> cards=new ArrayList<>(CARD_BUDGET);
        for(int index=0;index<Math.min(8,visible.size());index++){
            Cue cue=visible.get(index);float age=cue.age(partial);var p=cue.data;
            if(age>.78f||cue.center().distanceToSqr(camera)>80*80)continue;
            Vec3 axis=p.to().subtract(p.from()).normalize();if(axis.lengthSqr()<.01)axis=new Vec3(0,0,1);
            Vec3 right=axis.cross(Math.abs(axis.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize(),up=right.cross(axis).normalize();
            float seed=Math.floorMod(p.seed(),1021),burst=1-(float)Math.exp(-age*22);
            float fade=(1-smooth((age-.13f)/.44f))*smooth(age/.018f);
            int kind=p.first();
            if(p.effect()==CatCombatCue.BLINK){
                // Departure folds towards the body; arrival tears open. No circular portal frame.
                for(int end=0;end<2;end++)for(int i=0;i<6;i++){
                    float s=seed+i*19.7f+end*111,angle=random(s)*Mth.TWO_PI;
                    Vec3 radial=new Vec3(Math.cos(angle),.35+random(s+1)*1.2,Math.sin(angle)).normalize();
                    float motion=end==0?1-burst:burst;
                    Vec3 center=(end==0?p.from():p.to()).add(radial.scale((.2+random(s+3)*.85)*motion));
                    flame(cards,event,center,radial.scale(end==0?-1:1),(.22f+random(s+4)*.35f)*(.45f+fade*.55f),
                            .55f+random(s+5)*.62f,i%3,s,smooth((age-.09f)/.55f),fade,kind);
                }
            }else if(p.effect()==CatCombatCue.DASH){
                for(int i=0;i<7;i++){
                    float s=seed+i*13.17f,u=(i+random(s)) / 7;
                    Vec3 point=p.from().lerp(p.to(),u).add(0,random(s+2)*.45-.2,0);
                    flame(cards,event,point,axis.scale(-1).add(0,.15,0),(.14f+random(s+1)*.20f)*fade,
                            .5f+random(s+3)*.8f,i%3,s,smooth(age/.6f),fade*(.45f+.55f*u),kind);
                }
            }else{
                boolean collision=p.effect()==CatCombatCue.CLASH||p.effect()==CatCombatCue.BARRAGE||p.effect()==CatCombatCue.GUARD;
                boolean blue=p.effect()==CatCombatCue.IMPACT&&kind==SorcererCat.BLUE;
                boolean kick=p.effect()==CatCombatCue.KICK||p.effect()==CatCombatCue.DIVE;
                Vec3 origin=collision?cue.center():p.from();
                float size=p.effect()==CatCombatCue.DIVE?1.25f:p.effect()==CatCombatCue.CLASH?1.15f:p.effect()==CatCombatCue.GUARD?.65f:.95f;
                for(int i=0;i<(collision?10:8);i++){
                    float s=seed+i*23.71f,angle=random(s)*Mth.TWO_PI;
                    Vec3 radial=right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
                    Vec3 heading=collision?radial.add(axis.scale((i%2==0?1:-1)*.30)).normalize()
                            :axis.add(radial.scale(.20+random(s+2)*.85)).normalize();
                    float travel=blue?(1-burst)*1.2f:burst*(.5f+random(s+4)*1.4f);
                    Vec3 point=origin.add((blue?radial:heading).scale(travel*size));
                    if(kick)heading=heading.add(0,p.effect()==CatCombatCue.DIVE?-.8:.3,0).normalize();
                    float width=(i<3?.42f+random(s+3)*.24f:.13f+random(s+3)*.24f)*size*(.4f+.6f*burst);
                    flame(cards,event,point,blue?radial.scale(-1):heading,width,width*(1.2f+random(s+6)*1.3f),
                            i%3,s,smooth((age-.10f)/.48f),fade,collision&&i%2!=0?p.second():kind);
                }
                // Unequal rounded, pointed and double-lobed embers dissolve independently.
                for(int i=0;i<6;i++){
                    float s=seed+271+i*17.91f,t=age-(.035f+random(s)*.06f),life=.28f+random(s+2)*.36f;
                    if(t<=0||t>=life)continue;
                    float a=random(s+3)*Mth.TWO_PI;
                    Vec3 radial=right.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
                    Vec3 heading=radial.add(axis.scale(collision?(random(s+4)-.5)*.8:1.4)).normalize();
                    Vec3 point=origin.add(heading.scale((1-Math.exp(-t*4))*(1.3+random(s+5)*2.3)*size));
                    float width=(i==0?.36f:i<3?.18f:.055f)*( .7f+random(s+7)*.8f);
                    flame(cards,event,point,heading.add(0,t*.8,0),width,width*(.8f+random(s+8)*1.6f),i%3,s,
                            smooth(t/life),1-smooth((t/life-.25f)/.75f),collision&&i%2!=0?p.second():kind);
                }
            }
        }
        if(cards.isEmpty())return;
        cards.sort(Comparator.comparingDouble(c->c.center.z));
        try(DomainRenderer.State ignored=new DomainRenderer.State()){
            BufferBuilder buffer=FistSpriteRenderer.begin(time,false,.4f,.5f,0,0,false);
            float aspect=FistSpriteRenderer.aspect(event.getProjectionMatrix());
            for(Flame card:cards)emit(buffer,event,card,aspect);
            FistSpriteRenderer.finish(buffer);
        }
    }
    private static void flame(List<Flame> cards,RenderLevelStageEvent event,Vec3 point,Vec3 direction,float width,float length,
                              int shape,float seed,float erosion,float alpha,int kind){
        if(cards.size()>=CARD_BUDGET||alpha<.005f)return;
        Vec3 p=point.subtract(event.getCamera().getPosition());
        Vector3f center=event.getPoseStack().last().pose().transformPosition(new Vector3f((float)p.x,(float)p.y,(float)p.z));
        if(center.z>-.08f)return;
        Vector3f axis=event.getPoseStack().last().pose().transformDirection(new Vector3f((float)direction.x,(float)direction.y,(float)direction.z));
        cards.add(new Flame(center,axis,width,length,shape,seed,erosion,alpha,kind==SorcererCat.RED||kind==SorcererCat.BLACK_FLASH));
    }
    private static void emit(BufferBuilder buffer,RenderLevelStageEvent event,Flame f,float aspect){
        var projection=event.getProjectionMatrix();Vector3f p=FistSpriteRenderer.project(projection,f.center,aspect);
        if(p==null)return;
        Vector3f tip=FistSpriteRenderer.project(projection,new Vector3f(f.center).fma(.3f,f.axis),aspect);
        Vector2f up=tip==null?new Vector2f():new Vector2f(tip.x-p.x,tip.y-p.y);
        if(up.lengthSquared()<.000004f){float a=random(f.seed+99)*Mth.TWO_PI;up.set(Mth.sin(a),Mth.cos(a));}else up.normalize();
        float scale=Math.abs(projection.m11()/f.center.z),w=f.width*scale,h=f.length*scale;
        FistSpriteRenderer.quadColored(buffer,p.x/aspect,p.y,p.z,up.y*w/aspect,-up.x*w,up.x*h/aspect,up.y*h,
                f.shape,f.seed,f.erosion,1,f.alpha,f.red);
    }
    static void camera(ViewportEvent.ComputeCameraAngles event,float partial,float scale){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;float kick=0;
        for(Cue c:CUES)if(c.data.effect()==CatCombatCue.CLASH||c.data.effect()==CatCombatCue.DIVE){
            if(CatCinematicCamera.steadyGojoView()&&mc.getCameraEntity().getId()==c.data.other())continue;
            float near=1-Mth.clamp((float)mc.player.distanceToSqr(c.center())/144,0,1);kick+=near*(float)Math.exp(-c.age(partial)*19)*.35f;
        }
        event.setPitch(event.getPitch()-Math.min(.5f,kick)*scale);
    }
    private static float smooth(float x){x=Mth.clamp(x,0,1);return x*x*(3-2*x);}
    private static float random(float x){double n=Math.sin(x*127.1)*43758.5453;return(float)(n-Math.floor(n));}
    private static boolean finite(Vec3 p){return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
}

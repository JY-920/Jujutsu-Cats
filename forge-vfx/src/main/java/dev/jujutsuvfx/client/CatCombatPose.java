package dev.jujutsuvfx.client;

import dev.jujutsuvfx.core.CatCombatMoves;
import net.minecraft.util.Mth;
import java.util.Arrays;
import static dev.jujutsuvfx.core.CatCombatMoves.*;

/** Reusable pose scratch space: no per-frame entity model, mesh or animation allocation. */
final class CatCombatPose {
    static final int HEAD = 0, BODY = 1, LEFT_ARM = 2, RIGHT_ARM = 3, LEFT_LEG = 4, RIGHT_LEG = 5, TAIL = 6, TAIL_TIP = 7;
    private final float[] values = new float[61];
    private float hipFollow;
    float hipFollow(){return hipFollow;}
    float value(int bone, int field) { return values[bone * 7 + field]; }
    float elbow(int hand) { return values[hand == 1 ? 56 : 57]; }
    float rootPitch() { return values[58]; }
    float rootRoll() { return values[59]; }
    float rootYaw() { return values[60]; }
    void blendFrom(CatCombatPose previous, float amount) {
        float t = smooth(amount);
        hipFollow=Mth.lerp(t,previous.hipFollow,hipFollow);
        for (int i = 0; i < values.length; i++) values[i] = Mth.lerp(t, previous.values[i], values[i]);
    }
    void sample(int move, int hand, float time, int variant) {
        Arrays.fill(values, 0);hipFollow=0;
        if (move == IDLE) return;
        // The supplied blindfold clip animates the left paw below these neutral model bones.
        if(move==DOMAIN_REVEAL){rotate(HEAD,-.025f*smooth(time/5),0,0);return;}
        if(move==MAHORAGA_ARRIVAL){if(variant==SHADOW_RETREAT)departure(time);else shadowArrival(time);return;}
        int main = hand == 1 ? LEFT_ARM : RIGHT_ARM, support = hand == 1 ? RIGHT_ARM : LEFT_ARM;
        float sign = hand == 1 ? 1 : -1;
        if(move==REPOSITION){reposition(main,support,sign,time,variant);return;}
        if(move==GUARD&&(variant==REACTION_WATCH||variant==REACTION_BRACE)){
            reaction(main,support,sign,time,variant==REACTION_BRACE);return;
        }
        if (move == REVERSE_CURSED) {
            float weight=smooth(time/5),breath=Mth.sin(time*.15f)*.025f;
            // A grounded, open stance with low hands, distinct from the raised Infinity ward.
            arm(LEFT_ARM,-.12f,-.12f,-.40f,-.32f,weight,.10f*weight,-.12f*weight);
            arm(RIGHT_ARM,-.12f,.12f,.40f,-.32f,weight,.10f*weight,-.12f*weight);
            rotate(HEAD,.10f*weight,0,0);rotate(BODY,breath*weight,0,0);
            leg(LEFT_LEG,.18f,.13f,weight);leg(RIGHT_LEG,.18f,-.13f,weight);
            rotate(TAIL,(-.12f+breath)*weight,0,0);
            rotate(TAIL_TIP,.17f*weight,0,0);return;
        }
        if (move == INFINITY) {
            float weight=smooth(time/3);
            // Both paws open away from the torso; left/right roll has opposite sign.
            arm(LEFT_ARM,-.22f,-.16f,-1.08f,-.16f,weight,.30f*weight,-.25f*weight);
            arm(RIGHT_ARM,-.22f,.16f,1.08f,-.16f,weight,.30f*weight,-.25f*weight);
            rotate(HEAD,-.055f*weight,0,0);rotate(BODY,-.025f*weight,0,0);
            leg(LEFT_LEG,.22f,.19f,weight);leg(RIGHT_LEG,.22f,-.19f,weight);
            values[58]=-.035f*weight;return;
        }
        if (move == GUARD) {
            float weight=smooth(time/3),pulse=Mth.sin(time*.22f)*.006f;
            // Extend the upper arms in front of the muzzle. A shallow elbow bend keeps
            // the wrists outside the head instead of folding both forearms back into it.
            arm(main,-1.36f,-sign*.12f,sign*.12f,-.20f,weight,.90f*weight,-2.20f*weight);
            arm(support,-1.23f,sign*.06f,-sign*.18f,-.38f,weight,1.20f*weight,-1.80f*weight);
            rotate(HEAD,.14f*weight,0,0);rotate(BODY,.09f*weight,sign*.1f*weight,0);
            leg(LEFT_LEG,.42f,.23f,weight);leg(RIGHT_LEG,.30f,-.27f,weight);
            values[58]=(-.09f+pulse)*weight;translate(BODY,0,.65f*weight,.25f*weight);return;
        }
        if(blade(move)){bladePose(move,time);return;}
        if (move == RAPID) {
            float weight=smooth(time/3)*(1-smooth((time-32)/4));
            float phase=(time-4)%3;if(phase<0)phase+=3;
            float punch=(1-smooth(Math.abs(phase-.65f)/1.65f))*weight;
            arm(main,-.3f-1.27f*punch,sign*(.16f-.35f*punch),sign*.20f,-1.55f+1.48f*punch,weight,-1.2f*weight,-1.65f*punch);
            arm(support,.08f,sign*.10f,-sign*.22f,-1.9f,weight,-1.45f*weight,.22f*punch);
            rotate(BODY,0,sign*.23f*punch,-sign*.065f*weight);rotate(HEAD,-.1f*weight,0,0);
            values[58]=.08f*weight;values[59]=sign*.055f*punch;
            // Transfer weight through the hips in time with each contact, instead of rigid legs.
            float drive=punch/Math.max(weight,.001f),rebound=(1-drive)*weight;
            int planted=hand==1?LEFT_LEG:RIGHT_LEG,trailing=hand==1?RIGHT_LEG:LEFT_LEG;
            leg(planted,.18f+.28f*drive,sign*(.19f+.09f*drive),weight);
            leg(trailing,.36f-.18f*drive,-sign*(.22f-.06f*drive),weight);
            translate(planted,sign*.16f*punch,-.15f*punch,-.35f*punch);
            translate(trailing,-sign*.12f*punch,-.28f*rebound,.32f*punch);
            translate(BODY,sign*.16f*punch,.28f*punch,-.18f*punch);
            hipFollow=.72f*weight;return;
        }
        if (move == AIR_CHASE || move == DIVE_KICK) {
            float weight=smooth(time/3),kick=move==DIVE_KICK?smooth((time-2)/3)*(1-smooth((time-15)/9)):0;
            arm(main,.22f,sign*.32f,sign*.42f,-1.45f,weight,-1.1f*weight,0);
            arm(support,.48f,-sign*.25f,-sign*.37f,-1.50f,weight,-1.05f*weight,0);
            leg(hand==1?LEFT_LEG:RIGHT_LEG,Mth.lerp(kick,.95f,-1.65f),sign*.18f,weight);
            leg(hand==1?RIGHT_LEG:LEFT_LEG,1.20f,-sign*.3f,weight);
            values[58]=(move==AIR_CHASE?.45f:.85f*kick)*weight;values[59]=sign*.22f*weight;
            values[60]=sign*.24f*kick;rotate(HEAD,-.2f*weight,0,0);rotate(TAIL,.48f*weight,0,0);return;
        }
        if(CatCombatMoves.spell(move)){spell(move,main,support,sign,time,variant);return;}
        if (move == PREPARE) {
            float wind = .35f + .65f * smooth(time / 3);
            arm(main, .28f, sign * .25f, sign * .27f, -1.65f, wind, -1.4f, -.3f);
            arm(support, .13f, -sign * .2f, -sign * .16f, -1.38f, wind * .70f, -.9f, 0);
            rotate(BODY, 0, -sign * .16f * wind, -sign * .055f * wind);
            rotate(HEAD, -.09f * wind, 0, 0);
            return;
        }
        if (move == UPPERCUT || move == ELBOW || move == DOUBLE_PALM || move == SWEEP) {
            float contact=CatCombatMoves.contact(move), duration=CatCombatMoves.duration(move);
            float strike=smooth((time-contact+3)/3), recovery=smooth((time-contact-1)/(duration-contact-1));
            float hit=strike*(1-recovery), weight=smooth(time/2)*(1-recovery*.8f);
            if (move == UPPERCUT) {
                arm(main,.55f-hit*2.75f,-sign*.20f,sign*.30f,-1.4f+hit*.95f,weight,-.2f-hit*1.2f,-hit*.7f);
                arm(support,-.15f,sign*.20f,-sign*.32f,-1.45f,weight,-1,0);
                rotate(BODY,.15f-hit*.3f,sign*(-.3f+hit*.65f),-sign*hit*.12f);
                values[58]=-.16f*hit;translate(BODY,0,.85f*(1-strike)*weight,-.2f*hit);
            } else if (move == ELBOW) {
                arm(main,-.80f-hit*.65f,sign*(-.95f+hit*1.35f),sign*.50f,-1.85f,weight,-.65f,-hit*1.3f);
                arm(support,.16f,sign*.22f,-sign*.32f,-1.25f,weight,-.7f,0);
                rotate(BODY,0,sign*(-.35f+hit*.8f),-sign*.14f*hit); values[60]=sign*.20f*hit;
            } else if (move == DOUBLE_PALM) {
                arm(LEFT_ARM,.18f-1.72f*hit,-.26f+hit*.16f,.14f,-1.55f+hit*1.48f,weight,-1.0f,-1.8f*hit);
                arm(RIGHT_ARM,.18f-1.72f*hit,.26f-hit*.16f,-.14f,-1.55f+hit*1.48f,weight,-1.0f,-1.8f*hit);
                rotate(BODY,.14f*hit,0,0); values[58]=.16f*hit;translate(BODY,0,.20f*hit,-.65f*hit);
            } else {
                arm(main,-.65f,sign*.40f,sign*.55f,-.8f,weight,-.3f,0);
                arm(support,.45f,-sign*.45f,-sign*.42f,-1.10f,weight,.2f,0);
                leg(hand==1?LEFT_LEG:RIGHT_LEG,-1.35f*hit,sign*.9f*hit,weight);
                leg(hand==1?RIGHT_LEG:LEFT_LEG,.95f,sign*-.20f,weight);
                values[58]=.35f*weight;values[59]=-sign*.42f*hit;
                values[60]=sign*Mth.wrapDegrees(270*smooth((time-1)/10))*(Mth.PI/180)*(1-recovery);
                translate(BODY,0,1.3f*weight,0); rotate(TAIL,.35f*weight,0,sign*.4f*hit);
            }
            if (move != SWEEP) { leg(LEFT_LEG,.30f,.20f,weight);leg(RIGHT_LEG,.30f,-.20f,weight); }
            rotate(HEAD,-.1f*weight,sign*-.1f*hit,0);return;
        }
        if (move == JAB || move == HOOK) {
            float contact = CatCombatMoves.contact(move), duration = CatCombatMoves.duration(move);
            float strike = smooth((time - contact + 2) / 2), recovery = smooth((time - contact - 1) / (duration - contact - 1));
            float extend = strike * (1 - recovery), weight = 1 - recovery * .62f;
            float shoulder = Mth.lerp(extend, .25f, move == HOOK ? -1.18f : -1.52f);
            float yaw = sign * (move == HOOK ? -.65f + extend * 1.2f : .25f - extend * .45f);
            arm(main, shoulder, yaw, sign * (move == HOOK ? .44f : .21f), Mth.lerp(extend, -1.65f, -.10f),
                    weight, -1.45f * weight, -1.7f * extend);
            arm(support, .15f, -sign * .20f, -sign * .17f, -1.3f, .64f * weight, -.85f * weight, 0);
            rotate(BODY, 0, sign * (-.17f + extend * (move == HOOK ? .61f : .35f)) * weight, -sign * .085f * weight);
            translate(BODY, 0, .50f * extend, -.30f * extend);
            rotate(HEAD, -.1f * weight, sign * -.08f * extend, 0);
            values[59] = sign * extend * (move == HOOK ? .12f : .045f);
            return;
        }
        if (move == KICK) {
            float lift = smooth(time / 4), extend = smooth((time - 4) / 3), recovery = smooth((time - 10) / 8);
            float air = lift * (1 - recovery), strike = extend * (1 - recovery);
            arm(main, .12f, sign * .38f, sign * .46f, -1.55f, air, -1.1f * air, 0);
            arm(support, -.45f, -sign * .30f, -sign * .5f, -.8f, air, -1.0f * air, 0);
            leg(hand == 1 ? LEFT_LEG : RIGHT_LEG, Mth.lerp(extend, .70f, -1.85f), sign * -.22f, air);
            leg(hand == 1 ? RIGHT_LEG : LEFT_LEG, .92f, -sign * .42f, air);
            values[58] = -.62f * air; values[59] = sign * .28f * air;
            // One complete turn; wrap the endpoint to zero so the next pose cannot rewind it.
            values[60] = sign * Mth.wrapDegrees(360*smooth((time-1)/10)) * (Mth.PI/180);
            rotate(BODY, -.12f * air, sign * .13f * air, 0);
            rotate(HEAD, .35f * air, -sign * .13f * air, 0);
            rotate(TAIL, -.35f * air, 0, 0); rotate(TAIL_TIP, .42f * air, 0, 0);
            return;
        }
        if (move == DASH) {
            float weight = smooth(time / 2) * (1 - smooth((time - 7) / 3));
            arm(main, .70f, sign * .14f, sign * .25f, -.48f, weight, 0, .3f);
            arm(support, .42f, -sign * .2f, -sign * .2f, -.75f, weight, 0, .2f);
            leg(LEFT_LEG, Mth.cos(time * 1.6f) * 1.05f, .06f, weight);
            leg(RIGHT_LEG, -Mth.cos(time * 1.6f) * 1.05f, -.06f, weight);
            values[58] = .19f * weight; values[59] = sign * .09f * weight;
            translate(BODY, 0, .75f * weight, 0); rotate(HEAD, -.15f * weight, 0, 0);
            return;
        }
        if (move == BLINK) {
            float fold = smooth(time / 3) * (1 - smooth((time - 6) / 6));
            arm(main, -.08f, -sign * .55f, sign * .50f, -1.62f, fold, -1.4f * fold, 0);
            arm(support, .18f, sign * .45f, -sign * .34f, -1.70f, fold, -1.2f * fold, 0);
            values[59] = sign * .20f * fold; values[60] = -sign * .19f * fold;
            translate(BODY, 0, .8f * fold, 0);
            leg(LEFT_LEG, -.35f, .22f, fold); leg(RIGHT_LEG, -.35f, -.22f, fold);
            return;
        }
        if (move == CLASH) {
            float weight = 1 - smooth((time - 4) / 6), jolt = Mth.sin(time * 1.5f) * .025f * (1 - smooth(time / 5));
            arm(main, -.58f, -sign * .42f, sign * .25f, -.80f, weight, -1.0f * weight, -.55f * weight);
            arm(support, -.38f, sign * .43f, -sign * .31f, -1.1f, weight, -1.5f * weight, -.40f * weight);
            values[58] = (-.14f + jolt) * weight; values[59] = sign * .15f * weight;
            rotate(HEAD, -.13f * weight, 0, 0);
            leg(LEFT_LEG, .32f, .18f, weight); leg(RIGHT_LEG, .32f, -.18f, weight);
        }
    }
    /** Blade first, shoulders uncurl, then the full body rises out of the ink. */
    private void shadowArrival(float time){
        float rise=smooth(time/32),weight=1-smooth((time-44)/6),curl=(1-rise)*weight;
        rotate(BODY,-.30f*curl,0,0);rotate(HEAD,-.16f*curl,0,0);
        arm(RIGHT_ARM,-1.70f+.95f*rise,.30f,.36f,-.38f,weight,0,-.6f*weight);
        arm(LEFT_ARM,-.92f+.65f*rise,-.32f,-.57f,-.65f,weight,0,-.4f*weight);
        leg(LEFT_LEG,.54f,.18f,curl);leg(RIGHT_LEG,.48f,-.18f,curl);
        rotate(TAIL,.72f*curl,0,0);rotate(TAIL_TIP,.44f*curl,0,0);
    }
    /** A distinct retreat: lower the sword, fold one paw to the ground and sink feet first. */
    private void departure(float time){
        float fold=smooth(time/12),sink=smooth((time-8)/24);
        rotate(BODY,.22f*fold,0,.06f*fold);rotate(HEAD,.16f*fold,0,0);
        arm(RIGHT_ARM,.38f,.42f,.28f,-.24f,fold,.2f*fold,.3f*fold);
        arm(LEFT_ARM,-.48f,-.20f,-.64f,-.82f,fold,.5f*fold,-.7f*fold);
        leg(LEFT_LEG,.72f,.20f,fold);leg(RIGHT_LEG,.62f,-.20f,fold);
        rotate(TAIL,.35f*fold+.60f*sink,0,0);rotate(TAIL_TIP,.50f*sink,0,0);
    }
    private void bladePose(int move,float time){
        int stroke=move==BLADE_CHAIN?Math.min(2,Math.max(0,(int)((time-2)/6))):0;
        float contact=bladeContact(move,stroke),duration=CatCombatMoves.duration(move);
        float cut=smooth((time-contact+2)/2);
        float recover=smooth((time-(bladeContact(move,bladeStrokes(move)-1)+2))/(duration-bladeContact(move,bladeStrokes(move)-1)-2));
        float weight=smooth(time/2)*(1-.85f*recover);
        float shoulder,yaw,roll,twist,lean;
        if(move==BLADE_RISE){
            shoulder=Mth.lerp(cut,.35f,-2.60f);yaw=Mth.lerp(cut,-.55f,.45f);
            roll=Mth.lerp(cut,.60f,-.35f);twist=Mth.lerp(cut,-.38f,.48f);lean=Mth.lerp(cut,.15f,-.15f);
        }else if(move==BLADE_CHOP){
            shoulder=Mth.lerp(cut,-2.75f,-.35f);yaw=Mth.lerp(cut,-.32f,.20f);
            roll=Mth.lerp(cut,.40f,-.24f);twist=Mth.lerp(cut,-.22f,.40f);lean=Mth.lerp(cut,-.16f,.26f);
        }else{
            float sweep=move==BLADE_CHAIN&&stroke==1?1-cut:cut;
            shoulder=-1.20f+(stroke==2?.28f:0);yaw=Mth.lerp(sweep,-1.12f,1.02f);
            roll=Mth.lerp(sweep,.85f,-.55f);twist=Mth.lerp(sweep,-.52f,.60f);lean=.10f;
        }
        // The weapon arm is always the anatomical right foreleg; the left hand counterbalances.
        arm(RIGHT_ARM,shoulder,yaw,roll,-.32f+.20f*cut,weight,-.7f,-.7f*cut);
        arm(LEFT_ARM,-.42f,-twist*.55f,-.50f,-.9f,weight*.85f,-.5f,.35f*cut);
        rotate(BODY,lean*weight,twist*weight,-.09f*weight);rotate(HEAD,-.1f*weight,-twist*.28f*weight,0);
        leg(LEFT_LEG,.30f,.25f,weight);leg(RIGHT_LEG,.45f,-.32f,weight);
        rotate(TAIL,-.13f*weight,-twist*.55f*weight,0);rotate(TAIL_TIP,.18f*weight,-twist*.28f*weight,0);
        values[58]=lean*.5f*weight;values[60]=twist*.25f*weight;
        translate(BODY,0,.3f*weight,-.38f*cut*weight);
    }
    private void reaction(int main,int support,float sign,float age,boolean brace){
        float time=age*dev.jujutsuvfx.core.CinematicReaction.RATE;
        float weight=smooth(age/12),stride=Mth.sin(time*.20f),shift=Mth.sin(time*.095f);
        // An open readied stance and a slow, braced retreat. Paws stay in front of the muzzle.
        arm(main,brace?-1.32f:-.48f,-sign*.18f,sign*.27f,brace?-.22f:-.55f,weight,
                (brace?.85f:.25f)*weight,(brace?-2.1f:-.70f)*weight);
        arm(support,brace?-1.15f:-.23f,sign*.14f,-sign*.38f,brace?-.30f:-.65f,weight,
                (brace?1.05f:.15f)*weight,(brace?-1.85f:-.48f)*weight);
        leg(LEFT_LEG,.27f+stride*.24f,.19f,weight);leg(RIGHT_LEG,.27f-stride*.24f,-.22f,weight);
        rotate(BODY,(brace?.08f:.02f)*weight,sign*(.08f+shift*.045f)*weight,-stride*.025f*weight);
        rotate(HEAD,(brace?.10f:-.03f)*weight,-sign*shift*.06f*weight,0);
        rotate(TAIL,(.12f+stride*.025f)*weight,-sign*shift*.08f*weight,0);
        rotate(TAIL_TIP,-.10f*weight,sign*shift*.07f*weight,0);
        translate(BODY,0,(.22f+Math.abs(stride)*.07f)*weight,.12f*weight);
        hipFollow=.8f*weight;values[58]=-.035f*weight;
    }
    private void reposition(int main,int support,float sign,float time,int style) {
        float weight=smooth(time/3),stride=Mth.sin(Math.max(0,time-2)*1.25f);
        if(style==SPACE_BLINK) {
            float fold=weight*(1-smooth((time-6)/5));
            arm(main,-.05f,-sign*.50f,sign*.45f,-1.85f,fold,-1.6f*fold,0);
            arm(support,.10f,sign*.35f,-sign*.35f,-1.7f,fold,-1.3f*fold,0);
            leg(LEFT_LEG,-.38f,.25f,fold);leg(RIGHT_LEG,-.38f,-.25f,fold);
            values[58]=-.12f*fold;values[59]=sign*.2f*fold;values[60]=-sign*.22f*fold;
            return;
        }
        boolean hop=style==BACK_HOP||style==AIR_RETREAT||style==AIR_ESCAPE,press=style==RUSH_IN;
        float airborne=(style==AIR_RETREAT||style==AIR_ESCAPE)?smooth(time/3):hop?smooth((time-2)/3)*(1-smooth((time-15)/8)):0;
        arm(main,press?.55f:-.16f,sign*.25f,sign*.35f,press?-.55f:-1.55f,weight,-.9f*weight,0);
        arm(support,press?.28f:.12f,-sign*.24f,-sign*.30f,press?-.8f:-1.45f,weight,-1.0f*weight,0);
        leg(LEFT_LEG,airborne*.8f+stride*.64f*(1-airborne),.15f,weight);
        leg(RIGHT_LEG,airborne*.9f-stride*.64f*(1-airborne),-.15f,weight);
        values[58]=(press?.22f:hop?-.32f:-.12f)*weight;
        values[59]=sign*(style==SIDE_STEP?.22f:.08f)*weight;
        rotate(HEAD,press?-.12f:.1f,0,0);rotate(TAIL,airborne*.5f,0,sign*.1f*weight);
        translate(BODY,0,(.4f+airborne*.65f)*weight,0);
    }
    private void spell(int move,int main,int support,float sign,float time,int variant) {
        if(move==DOMAIN_CAST){domain(main,support,sign,time,variant);return;}
        boolean large=(variant&1)!=0;
        int kind=switch(move){case BLUE_CAST->dev.jujutsuvfx.core.CatSpell.BLUE;case RED_CAST->dev.jujutsuvfx.core.CatSpell.RED;
            case PURPLE_CAST->dev.jujutsuvfx.core.CatSpell.PURPLE;case FURNACE_CAST->dev.jujutsuvfx.core.CatSpell.FURNACE;
            case SLASH_CAST->dev.jujutsuvfx.core.CatSpell.DISMANTLE;case CLEAVE_CAST->dev.jujutsuvfx.core.CatSpell.CLEAVE;default->dev.jujutsuvfx.core.CatSpell.SHRINE;};
        float launch=dev.jujutsuvfx.core.CatSpell.launchTick(kind,large),end=dev.jujutsuvfx.core.CatSpell.poseTicks(kind,large);
        float weight=smooth(time/6)*(1-smooth((time-end+12)/12));
        float release=smooth((time-launch)/3),twist=0;
        if((variant&2)!=0&&(move==BLUE_CAST||move==RED_CAST)){
            float toss=smooth((time-launch+3)/5);
            arm(main,-1.75f-toss*.95f,-sign*.14f,sign*.20f,-.36f+toss*.28f,weight,.2f*weight,-.8f*weight);
            arm(support,-.15f,sign*.16f,-sign*.18f,-.55f,weight*.75f,.5f*weight,-.3f*weight);
            rotate(HEAD,-.48f*weight,0,0);rotate(BODY,-.10f*weight,sign*.08f*weight,0);
            leg(LEFT_LEG,.85f,.20f,weight);leg(RIGHT_LEG,1.02f,-.23f,weight);
            rotate(TAIL,.35f*weight,0,0);return;
        }
        if(move==BLUE_CAST){
            arm(main,-1.05f,-sign*.20f,sign*.27f,-.60f,weight,-1.1f*weight,-.7f*weight);
            arm(support,.13f,-sign*.23f,-sign*.25f,-1.35f,weight*.65f,-1.0f*weight,0);
            twist=-sign*.16f;
        }else if(move==RED_CAST){
            float push=large?smooth((time-52)/8):release;
            arm(main,Mth.lerp(push,.18f,-1.3f),sign*.12f,sign*.24f,Mth.lerp(push,-2.15f,-.24f),weight,-1.3f*weight,-push*.8f*weight);
            arm(support,large?-.54f:.12f,-sign*.28f,-sign*.32f,large?-1.05f:-1.35f,weight*(large?1:.55f),-1.4f*weight,0);
            twist=sign*.12f*(1-push);values[58]=-.09f*push*weight;
        }else if(move==PURPLE_CAST){
            float merge=large?smooth((time-14)/14):0;
            arm(main,-.88f-release*.43f,-sign*.12f,sign*.35f,-.60f+release*.40f,weight,-1.5f*weight,-release*.8f*weight);
            arm(support,-.16f,sign*.56f,-sign*.40f,-1.95f,weight*(.30f+merge*.70f),-1.6f*weight,0);
            twist=-sign*.20f;values[59]=sign*.045f*weight;
        }else if(move==FURNACE_CAST){
            float draw=smooth(time/26);
            arm(main,-1.40f,-sign*.23f,sign*.20f,-.12f,weight,-.8f*weight,-.5f*weight);
            arm(support,.08f+draw*.18f,sign*(.4f+draw*.35f),-sign*.30f,-1.8f+release*.75f,weight,-1.5f*weight,draw*.55f*weight);
            twist=-sign*.32f;rotate(HEAD,0,sign*.20f*weight,0);
        }else if(move==SLASH_CAST||move==CLEAVE_CAST){
            float cut=smooth((time-launch+3)/3);
            if(move==CLEAVE_CAST&&large&&time>launch)cut=.65f+.35f*Mth.sin((time-launch)*Mth.PI);
            arm(main,.18f-cut*1.48f,sign*(.65f-cut*1.1f),sign*(.58f-cut*.35f),-1.8f+cut*1.60f,weight,-1.3f*weight,-cut*.75f*weight);
            arm(support,.15f,-sign*.20f,-sign*.24f,-1.4f,weight*.7f,-1*weight,0);
            twist=sign*(-.30f+cut*.6f);
        }else if((variant&3)==2){
            // Gojo raises one hand near the face; Sukuna forms a two-paw central seal below.
            arm(main,.10f,-sign*.22f,sign*.24f,-2.30f,weight,-1.8f*weight,0);
            arm(support,.16f,sign*.25f,-sign*.27f,-1.10f,weight*.62f,-.9f*weight,0);
            rotate(HEAD,-.12f*weight,0,0);values[58]=-.06f*weight;
        }else{
            arm(main,.10f,-sign*.53f,sign*.45f,-2.0f,weight,-1.65f*weight,0);
            arm(support,.10f,sign*.53f,-sign*.45f,-2.0f,weight,-1.65f*weight,0);
            rotate(HEAD,-.17f*weight,0,0);values[58]=-.08f*weight;
        }
        rotate(BODY,0,twist*weight,-sign*.055f*weight);
        leg(LEFT_LEG,.15f,.12f,weight*.6f);leg(RIGHT_LEG,.15f,-.12f,weight*.6f);
        if((variant&4)!=0){
            leg(LEFT_LEG,.95f,.23f,weight);leg(RIGHT_LEG,1.17f,-.24f,weight);
            values[59]+=Mth.sin(time*.11f)*.018f*weight;rotate(TAIL,.38f*weight,0,0);
        }else rotate(TAIL,-.22f*weight,0,0);
    }
    /** Stand over the hind feet before bringing a seal in front of the chest. */
    private void domain(int main,int support,float sign,float time,int variant){
        float end=dev.jujutsuvfx.core.CatSpell.poseTicks(dev.jujutsuvfx.core.CatSpell.SHRINE,false);
        float settle=1-smooth((time-end+10)/10);
        float rise=smooth(time/10)*settle;
        float seal=smooth((time-5)/7)*(1-smooth((time-end+15)/9));
        float tilt=-(float)Math.toRadians(80)*rise;
        // The imported body points forward at90deg. Rotate it about the rear hip,
        // not the neck; the hind feet stay supported and the head/shoulders rise together.
        rotate(BODY,tilt,0,0);
        standAt(BODY,12,-10,tilt);
        standAt(HEAD,15,-9,tilt);rotate(HEAD,-.025f*rise,0,0);
        float shoulderY=standingY(14.1f,-5,tilt),shoulderZ=standingZ(14.1f,-5,tilt);
        if((variant&3)==2){
            // Gojo lifts the casting paw in front of the face, with the support paw forward at upper-chest height.
            arm(main,-1.08f,sign*.10f,sign*.10f,-1.80f,seal,shoulderY+.70f*seal,shoulderZ-2.6f*seal);
            arm(support,-.90f,-sign*.12f,-sign*.14f,-1.40f,seal,shoulderY+.70f*seal,shoulderZ-1.4f*seal);
        }else{
            // Spread the elbows and fold the forearms inward/upward into an arch.
            // Imported paws are thick cuboids: retain a centre gap instead of intersecting them.
            arm(main,-.95f,sign*.10f,-sign*.70f,-1.45f,seal,shoulderY+.70f*seal,shoulderZ-2.40f*seal);
            arm(support,-.95f,-sign*.10f,sign*.70f,-1.45f,seal,shoulderY+.70f*seal,shoulderZ-2.40f*seal);
            values[main*7+3]=sign*seal;values[support*7+3]=-sign*seal;
        }
        leg(LEFT_LEG,.04f,.07f,rise);leg(RIGHT_LEG,.04f,-.07f,rise);
        // A low rear curl keeps the connected tail above the floor as the torso stands up.
        rotate(TAIL,1.80f*rise,0,0);rotate(TAIL_TIP,1.50f*rise,0,0);
    }
    private void standAt(int bone,float y,float z,float tilt){translate(bone,0,standingY(y,z,tilt),standingZ(y,z,tilt));}
    private static float standingY(float y,float z,float tilt){return 18+(y-18)*Mth.cos(tilt)-(z-5)*Mth.sin(tilt)-y;}
    private static float standingZ(float y,float z,float tilt){return 5+(y-18)*Mth.sin(tilt)+(z-5)*Mth.cos(tilt)-z;}

    private void arm(int bone, float x, float y, float z, float elbow, float weight, float lift, float forward) {
        rotate(bone, x, y, z); translate(bone, 0, lift, forward);
        values[bone * 7 + 6] = weight;
        values[bone == LEFT_ARM ? 56 : 57] = elbow * weight;
    }
    private void leg(int bone, float x, float z, float weight) { rotate(bone, x, 0, z); values[bone * 7 + 6] = weight; }
    private void rotate(int bone, float x, float y, float z) { int i = bone * 7; values[i] = x; values[i + 1] = y; values[i + 2] = z; }
    private void translate(int bone, float x, float y, float z) { int i = bone * 7 + 3; values[i] = x; values[i + 1] = y; values[i + 2] = z; }
    private static float smooth(float x) { float t = Mth.clamp(x, 0, 1); return t * t * (3 - 2 * t); }
}

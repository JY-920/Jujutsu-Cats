package dev.jujutsuvfx.core;

/** Pure camera composition math: no entity mutation, rendering API, or wall-clock dependency. */
public final class CinematicFraming {
    public enum Shot { DUEL, MELEE, CHARGE, RELEASE, DOMAIN, CHASE, AIR, DIVE, DEFENSE, DETAIL, REVEAL, FINALE,
        OPENING_DEFENSE, ARM_CUT, RECOVER, SUMMON, DESCENT, FUSION, VOID_VICTORY, SHRINE_VICTORY, BLADE_CONTACT, GESTURE, STRUCTURE, BARRIER }
    public enum Mode { AUTO, SHOULDER, POV }
    public record V(double x,double y,double z) {
        public static final V UP=new V(0,1,0),FORWARD=new V(0,0,1);
        public V add(V v){return new V(x+v.x,y+v.y,z+v.z);}
        public V sub(V v){return new V(x-v.x,y-v.y,z-v.z);}
        public V scale(double n){return new V(x*n,y*n,z*n);}
        public double dot(V v){return x*v.x+y*v.y+z*v.z;}
        public V cross(V v){return new V(y*v.z-z*v.y,z*v.x-x*v.z,x*v.y-y*v.x);}
        public double length(){return Math.sqrt(dot(this));}
        public V unit(){double n=length();return n<1e-8?FORWARD:scale(1/n);}
        public V horizontal(){return new V(x,0,z).unit();}
        public V lerp(V b,double t){return add(b.sub(this).scale(t));}
        public V limited(double distance){double n=length();return n>distance?scale(distance/n):this;}
        public boolean finite(){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);}
    }
    public record Scene(V hero,V target,V forward,V velocity,V effect,double effectRadius,
                        double bodyRadius,double aspect,boolean paired,double seconds,double side){}
    public record Plan(V eye,V aim,double fov,double roll,boolean detached){}

    /** Minimum hold plus a brief confirmation keeps individual punches from cutting the camera. */
    public static final class ShotClock {
        private Shot current=Shot.DUEL,candidate=Shot.DUEL;
        private double since=-100,candidateSince=-100;
        public void reset(){current=candidate=Shot.DUEL;since=candidateSince=-100;}
        public Shot choose(Shot wanted,double now){
            if(wanted!=candidate){candidate=wanted;candidateSince=now;}
            if(wanted==current)return current;
            double hold=current==Shot.DOMAIN||current==Shot.REVEAL?1.8:current==Shot.FINALE?2:current==Shot.RELEASE?.9:.8;
            boolean urgent=(wanted==Shot.RELEASE||wanted==Shot.DOMAIN||wanted==Shot.DIVE||wanted==Shot.REVEAL||wanted==Shot.FINALE)&&now-since>.28;
            if(urgent||now-since>=hold&&now-candidateSince>=.14){current=wanted;since=now;}
            return current;
        }
    }
    /** Render-time spring: low-frequency scene packets never directly step the pan/zoom. */
    public static final class StructureTrack {
        private V focus,velocity=new V(0,0,0);
        private double radius=4;
        public void reset(){focus=null;velocity=new V(0,0,0);radius=4;}
        public V focus(){return focus;}
        public double radius(){return radius;}
        public void update(V target,double size,double dt){
            if(target==null||!target.finite()||!Double.isFinite(size))return;
            size=clamp(size,2,24);dt=Double.isFinite(dt)?clamp(dt,0,.10):0;
            if(focus==null){focus=target;radius=size;return;}
            if(dt==0)return;
            // Analytic critically damped spring; fast physics/cut updates cannot cause overshoot.
            double rate=18,decay=Math.exp(-rate*dt);
            V offset=focus.sub(target),j=velocity.add(offset.scale(rate));
            V next=target.add(offset.add(j.scale(dt)).scale(decay));
            velocity=velocity.sub(j.scale(rate*dt)).scale(decay).limited(64);
            focus=focus.add(next.sub(focus).limited(64*dt));
            radius+=(size-radius)*blend(6,dt);
        }
    }
    private CinematicFraming(){}
    public static double clamp(double value,double low,double high){return Math.max(low,Math.min(high,value));}
    public static double blend(double rate,double dt){return 1-Math.exp(-rate*clamp(dt,0,.10));}
    public static V turnToward(V from,V to,double amount){
        double start=Math.atan2(from.x,from.z),end=Math.atan2(to.x,to.z);
        double angle=start+Math.atan2(Math.sin(end-start),Math.cos(end-start))*amount;
        return new V(Math.sin(angle),0,Math.cos(angle));
    }
    public static V orbitBlend(V from,V to,V pivot,double amount){
        V a=from.sub(pivot),b=to.sub(pivot);
        double ar=Math.hypot(a.x,a.z),br=Math.hypot(b.x,b.z);
        if(ar<.1||br<.1)return from.lerp(to,amount);
        double start=Math.atan2(a.x,a.z),end=Math.atan2(b.x,b.z);
        double angle=start+Math.atan2(Math.sin(end-start),Math.cos(end-start))*amount;
        double radius=ar+(br-ar)*amount;
        return pivot.add(new V(Math.sin(angle)*radius,a.y+(b.y-a.y)*amount,Math.cos(angle)*radius));
    }
    public static Plan plan(Scene s,Shot shot,Mode mode){
        V forward=s.forward.horizontal(),right=forward.cross(V.UP).unit().scale(s.side<0?-1:1);
        V opponent=s.paired?s.target:s.hero.add(forward.scale(5));
        double gap=opponent.sub(s.hero).length(),body=clamp(s.bodyRadius,.7,2.3);
        if(mode==Mode.POV)return new Plan(s.hero.add(forward.scale(.10)),opponent,74,0,false);
        if(mode==Mode.SHOULDER){
            V eye=s.hero.sub(forward.scale(4.8+body*.6)).add(right.scale(2.0+body*.25)).add(V.UP.scale(.95));
            V aim=s.hero.lerp(opponent,clamp(5/Math.max(5,gap),.2,.75));
            return new Plan(eye,aim,68,0,true);
        }
        V aim=s.hero.lerp(opponent,.42),offset=right.scale(.95).sub(forward.scale(.35)).add(V.UP.scale(.37));
        double distance=7.8+Math.min(12,gap*.18),fov=61,roll=0;
        boolean fitTarget=s.paired,fitEffect=false;
        switch(shot){
            case BARRIER -> {
                V center=s.effect==null?s.hero:s.effect;
                double radius=clamp(s.effectRadius,3,50);
                V offsetBarrier=right.scale(.82).sub(forward.scale(.45)).add(V.UP.scale(.62)).unit();
                // Film the entire finite shell from the real world, independently of the actors inside it.
                Plan exterior=new Plan(center.add(offsetBarrier.scale(radius*2.3)),center,70,0,true);
                return includeSphere(exterior,center,radius*1.04,s.aspect);
            }
            case OPENING_DEFENSE -> {
                // See Sukuna's crossed arms and the approaching projectile in the same shot.
                V focus=s.effect==null?opponent:s.effect;
                aim=s.hero.lerp(s.hero.add(focus.sub(s.hero).limited(14)),.24);
                // This authored screen direction ignores the general side toggle: Sukuna is right,
                // and the incoming Purple travels in from the left on either world-facing axis.
                offset=forward.cross(V.UP).unit().scale(-.90).add(forward.scale(.35)).add(V.UP.scale(.17));
                distance=7.4;fov=57;fitTarget=false;fitEffect=focus.sub(s.hero).length()<30;
            }
            case VOID_VICTORY -> {
                // The black hole lives at optical infinity. Face its direction behind the victim.
                V axis=s.effect==null?forward:s.effect.sub(s.hero).unit();
                V lateral=axis.cross(V.UP).unit();
                V eye=s.hero.sub(axis.horizontal().scale(8.5)).add(lateral.scale(.65)).add(V.UP.scale(.30));
                return new Plan(eye,eye.add(axis.scale(10)),70,0,true);
            }
            case SHRINE_VICTORY -> {
                V landmark=s.effect==null?opponent:s.effect;
                V axis=landmark.sub(s.hero).unit();
                aim=s.hero.add(axis.scale(3.2)).add(V.UP.scale(.15));
                offset=axis.horizontal().scale(-1).add(axis.cross(V.UP).unit().scale(.08)).add(V.UP.scale(.05));
                distance=10;fov=67;fitTarget=false;fitEffect=true;
            }
            case BLADE_CONTACT -> {
                aim=s.hero.lerp(opponent,.43).add(V.UP.scale(-.17));
                offset=right.scale(.94).add(forward.scale(.38)).add(V.UP.scale(.12));
                distance=6.4;fov=59;fitTarget=s.paired;
            }
            case ARM_CUT -> {
                // A distant ranged cut still needs a readable view of Gojo's forearm.
                aim=s.hero.add(opponent.sub(s.hero).limited(6).scale(.20)).add(V.UP.scale(-.26));
                offset=right.scale(.64).add(forward.scale(.78)).add(V.UP.scale(.06));
                distance=4.8;fov=49;fitTarget=s.paired&&gap<=12;
            }
            case RECOVER -> {
                aim=s.hero.add(right.scale(.22)).add(V.UP.scale(-.30));
                offset=forward.add(right.scale(.50)).add(V.UP.scale(.06));
                distance=3.9;fov=43;fitTarget=false;
            }
            case GESTURE -> {
                // Domain hands are performed upright: leave space from planted feet to raised head/paws.
                aim=s.hero.add(V.UP.scale(.35));
                offset=forward.add(right.scale(.34)).add(V.UP.scale(.07));
                distance=5.1;fov=48;fitTarget=false;
            }
            case SUMMON -> {
                aim=s.hero.add(V.UP.scale(.4));offset=forward.scale(.5).add(right).add(V.UP.scale(.13));
                distance=6.2;fov=58;fitTarget=false;
            }
            case DESCENT -> {
                // Keep the face and anatomical right blade visible through descent, impact,
                // and the upright reveal; the ground opponent supplies the landing context.
                aim=s.hero.lerp(opponent,.18).add(V.UP.scale(.20));
                offset=forward.scale(.84).add(forward.cross(V.UP).unit().scale(.55)).add(V.UP.scale(.10));
                distance=9;fov=59;fitTarget=s.paired;
            }
            case FUSION -> {
                V focus=s.effect==null?s.hero:s.effect;
                aim=s.hero.lerp(focus,.72);offset=forward.add(right.scale(.06)).add(V.UP.scale(.13));
                distance=9;fov=49;fitTarget=false;fitEffect=true;
            }
            case REVEAL -> {
                aim=s.hero.add(V.UP.scale(-.22)).add(right.scale(.12));
                offset=forward.add(right.scale(.38)).add(V.UP.scale(.045));
                distance=3.2;fov=44;fitTarget=false;
            }
            case DETAIL -> {
                V focus=s.effect==null?s.hero:s.hero.add(s.effect.sub(s.hero).limited(9));
                aim=s.hero.lerp(focus,.45);offset=right.scale(.75).add(forward.scale(.78)).add(V.UP.scale(.10));
                distance=4.2;fov=46;fitTarget=false;fitEffect=true;
            }
            case FINALE -> {
                V focus=s.effect==null?s.hero:s.effect;
                aim=s.hero.lerp(focus,.72);offset=forward.add(right.scale(.06)).add(V.UP.scale(.16));
                distance=12;fov=76;fitTarget=false;fitEffect=true;
            }
            case MELEE -> {
                aim=s.hero.lerp(opponent,.35);offset=right.add(forward.scale(.12)).add(V.UP.scale(.16));
                distance=5.4;fov=56;
            }
            case DEFENSE -> {
                aim=s.hero.lerp(opponent,.30);offset=right.scale(.85).add(forward.scale(.32)).add(V.UP.scale(.20));
                distance=6.5;fov=60;
            }
            case CHARGE -> {
                V focus=s.effect==null?s.hero.add(forward.scale(3)):s.hero.add(s.effect.sub(s.hero).limited(18));
                aim=s.hero.lerp(focus,.35);offset=right.scale(.88).add(forward.scale(.66)).add(V.UP.scale(.13));
                distance=6.2+clamp(s.effectRadius,0,6)*.7;fov=53;fitTarget=false;fitEffect=true;
            }
            case RELEASE -> {
                V focus=s.hero.add(opponent.sub(s.hero).limited(42));aim=s.hero.lerp(focus,.34);
                offset=right.scale(.72).sub(forward.scale(.84)).add(V.UP.scale(.26));
                distance=9.5;fov=68;fitTarget=false;
            }
            case DOMAIN -> {
                aim=s.hero.lerp(opponent,.50);offset=right.scale(.82).sub(forward.scale(.5)).add(V.UP.scale(.54));
                distance=clamp(12+s.effectRadius*.52,15,34);fov=76;
            }
            case CHASE -> {
                aim=s.hero.add(forward.scale(1.6)).add(s.velocity.scale(.11).limited(2));
                offset=right.scale(.52).sub(forward).add(V.UP.scale(.30));
                distance=8.5;fov=71;fitTarget=gap<22;
                roll=clamp(-s.velocity.dot(right)*.08,-1.2,1.2);
            }
            case AIR -> {
                aim=s.hero.lerp(opponent,.45);offset=right.scale(.9).sub(forward.scale(.45)).add(V.UP.scale(.36));
                distance=10.5;fov=67;
            }
            case DIVE -> {
                aim=s.hero.lerp(opponent,.6);offset=right.scale(.58).sub(forward.scale(.26)).add(V.UP.scale(.95));
                distance=10;fov=72;
            }
            default -> {}
        }
        // Gentle drift stays on one side of the fight line, rather than orbiting through the fighters.
        if(shot==Shot.DUEL||shot==Shot.CHARGE)
            offset=offset.add(right.scale(Math.sin(s.seconds*.35)*.035)).add(V.UP.scale(Math.sin(s.seconds*.51)*.012));
        V direction=offset.unit();
        distance=fit(distance,aim,direction,s.hero,shot==Shot.REVEAL?body*.58:body,fov,s.aspect);
        if(shot==Shot.GESTURE)distance=fit(distance,aim,direction,s.hero.add(V.UP.scale(.35)),1.45,fov,s.aspect);
        if(shot==Shot.DESCENT)distance=fit(distance,aim,direction,s.hero.add(V.UP.scale(.10)),3.0,fov,s.aspect);
        if(fitTarget)distance=fit(distance,aim,direction,opponent,body,fov,s.aspect);
        if(fitEffect&&s.effect!=null)distance=fit(distance,aim,direction,s.effect,clamp(s.effectRadius,.7,shot==Shot.FINALE?80:shot==Shot.FUSION?18:8),fov,s.aspect);
        distance=clamp(distance,3,shot==Shot.FINALE?160:shot==Shot.SHRINE_VICTORY?28:64);
        return new Plan(aim.add(direction.scale(distance)),aim,fov,roll,true);
    }
    /** One continuous reverse angle: Shrine/actors first, then Gojo against the directional black hole. */
    public static Plan domainClash(Scene s,V skyAxis,double shrineDepth,double progress){
        V axis=skyAxis.unit(),flat=axis.horizontal();
        V side=flat.cross(V.UP).unit().scale(s.side<0?-1:1);
        V landmark=s.effect==null?s.target: s.effect;
        double body=clamp(s.bodyRadius,1.25,2.3),aspect=clamp(s.aspect,.6,3.6);
        V firstAim=s.hero.lerp(landmark,.24).add(V.UP.scale(.15));
        V back=flat.add(side.scale(.14)).add(V.UP.scale(.10)).unit();
        double firstDistance=fit(10,firstAim,back,s.hero,body,72,aspect);
        firstDistance=fit(firstDistance,firstAim,back,landmark,clamp(s.effectRadius,1,10),72,aspect);
        if(s.paired)firstDistance=fit(firstDistance,firstAim,back,s.target,body,72,aspect);
        V firstEye=firstAim.add(back.scale(firstDistance));
        // The Shrine has real geometry behind Sukuna; finish in front of its near face.
        double room=landmark.sub(s.hero).dot(flat.scale(-1))-Math.max(0,shrineDepth)-.9;
        double close=clamp(Math.min(7.5,room),3.1,7.5);
        V lastEye=s.hero.sub(flat.scale(close)).add(side.scale(.40)).add(V.UP.scale(-.20));
        V lastAim=lastEye.add(axis.scale(12));
        double t=clamp((progress-.16)/.48,0,1);t=t*t*(3-2*t);
        V from=firstEye.sub(s.hero),to=lastEye.sub(s.hero);
        double firstAngle=Math.atan2(from.dot(side),from.dot(flat));
        double lastAngle=Math.atan2(to.dot(side),to.dot(flat));
        if(lastAngle<firstAngle)lastAngle+=Math.PI*2;
        double angle=firstAngle+(lastAngle-firstAngle)*t;
        double radius=Math.hypot(from.x,from.z)+(Math.hypot(to.x,to.z)-Math.hypot(from.x,from.z))*t;
        V eye=s.hero.add(flat.scale(Math.cos(angle)*radius)).add(side.scale(Math.sin(angle)*radius))
                .add(V.UP.scale(from.y+(to.y-from.y)*t+Math.sin(Math.PI*t)*.8));
        // The luminous ring spans about 39 degrees; moving away cannot shrink a sky feature.
        double skyFov=Math.max(84,Math.toDegrees(2*Math.atan(Math.tan(Math.toRadians(42))/Math.min(1,aspect))));
        Plan plan=new Plan(eye,firstAim.lerp(lastAim,t),72+(skyFov-72)*t,0,true);
        return includeSphere(plan,s.hero,body,aspect);
    }
    /** Re-anchor after camera collision/damping so optical-infinity framing cannot be displaced. */
    public static V clashSkyAim(V eye,V aim,V skyAxis,double progress){
        double t=clamp((progress-.60)/.16,0,1);t=t*t*(3-2*t);
        return aim.lerp(eye.add(skyAxis.unit().scale(12)),t);
    }
    /** Track the lifted body, hand the composition to Sukuna during flight, then hold the cut. */
    public static Plan structure(Scene s,double progress){
        V delta=s.target.sub(s.hero);
        V forward=Math.hypot(delta.x,delta.z)<.001?s.forward.horizontal():delta.horizontal();
        V side=forward.cross(V.UP).unit().scale(s.side<0?-1:1);
        double handoff=clamp((progress-.38)/.28,0,1);handoff=handoff*handoff*(3-2*handoff);
        V partner=s.hero.lerp(s.target,handoff);
        V structure=s.effect==null?partner:s.effect;
        double radius=clamp(s.effectRadius,2,24)+1.4;
        V aim=partner.lerp(structure,.76);
        V back=side.add(forward.scale(.12)).add(V.UP.scale(.22)).unit();
        double fov=64,distance=fit(12,aim,back,structure,radius,fov,s.aspect);
        distance=fit(distance,aim,back,partner,2.2,fov,s.aspect);
        return new Plan(aim.add(back.scale(distance)),aim,fov,0,true);
    }
    private static double fit(double distance,V aim,V backwards,V point,double radius,double fov,double aspect){
        V forward=backwards.scale(-1),right=forward.cross(V.UP).unit(),up=right.cross(forward).unit();
        V relative=point.sub(aim);
        double tanV=Math.tan(Math.toRadians(fov*.5))*.82,tanH=tanV*clamp(aspect,.6,3.6);
        double depth=relative.dot(forward);
        // The tangent touches a sphere in front of its centre plane. r/tan alone clips big blasts.
        distance=Math.max(distance,(Math.abs(relative.dot(right))+radius*Math.sqrt(1+tanH*tanH))/tanH-depth);
        return Math.max(distance,(Math.abs(relative.dot(up))+radius*Math.sqrt(1+tanV*tanV))/tanV-depth);
    }
    /** The rescue insert belongs inside the real barrier, including after interpolation. */
    public static Plan insideDomain(Plan plan,V center,double radius){
        if(!Double.isFinite(radius)||radius<=2||!center.finite()||!plan.eye.finite())return plan;
        double safe=Math.max(.5,radius-Math.max(1.5,radius*.12));
        V eye=center.add(plan.eye.sub(center).limited(safe));
        if(eye.sub(plan.eye).length()<1e-7)return plan;
        double before=plan.eye.sub(plan.aim).length(),after=Math.max(.5,eye.sub(plan.aim).length());
        double wider=Math.toDegrees(2*Math.atan(Math.tan(Math.toRadians(plan.fov*.5))*Math.max(1,before/after)));
        return new Plan(eye,plan.aim,clamp(wider,plan.fov,95),plan.roll,plan.detached);
    }
    public static Plan includeSphere(Plan plan,V center,double radius,double aspect){
        V back=plan.eye.sub(plan.aim).unit();
        double distance=fit(plan.eye.sub(plan.aim).length(),plan.aim,back,center,Math.max(.1,radius),plan.fov,aspect);
        return new Plan(plan.aim.add(back.scale(distance)),plan.aim,plan.fov,plan.roll,plan.detached);
    }
    public static double yaw(V eye,V aim){V d=aim.sub(eye);return Math.toDegrees(Math.atan2(-d.x,d.z));}
    public static double pitch(V eye,V aim){V d=aim.sub(eye);return -Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));}
}

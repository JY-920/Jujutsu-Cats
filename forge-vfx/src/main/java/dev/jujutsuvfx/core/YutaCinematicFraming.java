package dev.jujutsuvfx.core;

import dev.jujutsuvfx.core.CinematicFraming.*;

/** Pure compositions for ordinary combat and the server-timed Yuta/Geto finale. */
public final class YutaCinematicFraming {
    private YutaCinematicFraming() {}

    /** Actor/companion positions are ground anchors; collision is the actual elevated effect point. */
    public record Finale(V yuta,V geto,V rika,V collision,V getoEmitter,V rikaEmitter,double aspect,double side){
        public Finale(V yuta,V geto,V rika,V collision,double aspect,double side){
            this(yuta,geto,rika,collision,geto.add(V.UP.scale(2.2)),rika.add(V.UP.scale(2.25)),aspect,side);
        }
        public V axis(){return geto.sub(yuta).horizontal();}
        public V lateral(){return axis().cross(V.UP).scale(side<0?-1:1);}
        public V yutaBody(){return yuta.add(V.UP.scale(1.2));}
        public V getoBody(){return geto.add(V.UP.scale(1.2));}
        public V rikaSource(){return rikaEmitter;}
        public V getoSource(){return getoEmitter;}
    }

    public record Shake(V offset,double yaw,double pitch,double roll){}

    /** Smooth phase-clock impacts, independent of frame count, packet arrival, and flash toggles. */
    public static Shake shake(int phase,double age,double distance,double scale){
        if(!Double.isFinite(age+distance+scale)||age<0||distance<0)return new Shake(new V(0,0,0),0,0,0);
        double strength=switch(phase){
            case YutaGetoBeat.CHARGE -> .20*ease((age-104)/6);
            case YutaGetoBeat.CLASH -> Math.exp(-age/7)
                    +.30*ease(age/3)*(1-.45*ease(age/90));
            case YutaGetoBeat.BREAKTHROUGH -> .86*Math.exp(-age/7)
                    +1.15*ease((age-20)/2)*Math.exp(-Math.max(0,age-22)/6);
            case YutaGetoBeat.AFTERMATH -> .06*(1-ease(age/12));
            default -> 0;
        };
        strength=Math.min(1.2,strength)*CinematicFraming.clamp(scale,0,1)*(1-ease((distance-16)/80));
        double t=age*.05;
        V offset=new V(Math.sin(t*43+.8)*.070,Math.sin(t*61+1.7)*.045,Math.sin(t*37+.3)*.035).scale(strength);
        return new Shake(offset,Math.sin(t*51+1.3)*.85*strength,
                (Math.sin(t*73+.5)+.28*Math.sin(t*109))*1.15*strength,Math.sin(t*63+2.1)*.40*strength);
    }

    public static Plan ring(Scene s) {
        V front=s.forward().horizontal(),right=front.cross(V.UP).scale(s.side());
        V hands=s.hero().add(front.scale(.35)).add(V.UP.scale(-.35));
        Plan p=new Plan(hands.add(front.scale(3.4)).add(right.scale(1.1)).add(V.UP.scale(.2)),
                hands,45,0,true);
        return CinematicFraming.includeSphere(p,s.hero().add(V.UP.scale(-.15)),1.1,s.aspect());
    }

    /** Hands/face and local emitter, independent of how far away the opponent stands. */
    public static Plan casting(Scene s,V emitter,double radius){
        V front=s.forward().horizontal(),right=front.cross(V.UP).scale(s.side());
        V focus=s.hero().lerp(emitter,.35).add(V.UP.scale(-.12));
        Plan p=new Plan(focus.add(front.scale(3.7)).add(right.scale(2.4)).add(V.UP.scale(.3)),focus,46,0,true);
        p=CinematicFraming.includeSphere(p,s.hero(),.9,s.aspect());
        return CinematicFraming.includeSphere(p,emitter,Math.max(.6,radius),s.aspect());
    }

    /** Both ends of the travelling discharge stay in frame, including its live victim. */
    public static Plan vortex(Scene s,V origin,V tip,double radius,boolean flight) {
        V axis=tip.sub(origin).length()>.01?tip.sub(origin).horizontal():s.forward().horizontal();
        V right=axis.cross(V.UP).scale(s.side());
        V focus=flight?origin.lerp(tip,.56):s.hero().lerp(origin,.5);
        V offset=flight?right.add(axis.scale(-.18)).add(V.UP.scale(.32))
                :right.scale(.85).add(axis.scale(.65)).add(V.UP.scale(.18));
        Plan p=new Plan(focus.add(offset.unit().scale(flight?12:7)),focus,flight?68:52,0,true);
        p=CinematicFraming.includeSphere(p,s.hero(),Math.max(1.2,s.bodyRadius()),s.aspect());
        p=CinematicFraming.includeSphere(p,origin,Math.max(.8,radius),s.aspect());
        if(flight){
            p=CinematicFraming.includeSphere(p,tip,2.2,s.aspect());
            if(s.paired())p=CinematicFraming.includeSphere(p,s.target(),Math.max(1.2,s.bodyRadius()),s.aspect());
        }
        return p;
    }

    /** One authored sequence: hands, curse mass, Rika, locked wide, defeat, victorious pair. */
    public static Plan finale(Finale s,int phase,double age){
        age=Double.isFinite(age)?Math.max(0,age):0;
        Plan wide=wide(s),p;
        switch(phase){
            case YutaGetoBeat.RING,YutaGetoBeat.VOW -> {
                Scene hands=new Scene(s.yutaBody().add(V.UP.scale(.35)),s.getoBody(),s.axis(),
                        new V(0,0,0),s.rikaSource(),3.6,1.4,s.aspect(),true,0,s.side());
                p=ring(hands);
                if(phase==YutaGetoBeat.VOW)p=mix(p,victory(s),ease((age-44)/26));
            }
            case YutaGetoBeat.CHARGE -> {
                Plan curse=charge(s,false),love=charge(s,true);
                p=age<=52?mix(curse,love,ease((age-36)/16))
                        :mix(love,wide,ease((age-82)/28));
            }
            case YutaGetoBeat.BREAKTHROUGH -> {
                V impact=s.rikaSource().lerp(s.getoSource(),
                        YutaGetoBeat.breakthroughFraction((float)age));
                V focus=s.getoBody().lerp(impact,.28);
                V offset=s.lateral().add(s.axis().scale(-.30)).add(V.UP.scale(.35)).unit();
                p=new Plan(focus.add(offset.scale(14)),focus,67,0,true);
            }
            case YutaGetoBeat.AFTERMATH -> {
                Plan impact=impactEnd(s);
                p=mix(impact,injured(s),ease((age-10)/28));
            }
            case YutaGetoBeat.RETREAT -> p=mix(injured(s),victory(s),ease((age-22)/36));
            default -> p=wide;
        }
        return fitFinale(p,s,phase,age);
    }

    private static Plan charge(Finale s,boolean rika){
        V source=rika?s.rikaSource():s.getoSource(),body=rika?s.yutaBody():s.getoBody();
        V focus=source.lerp(body,.20);
        V offset=s.lateral().add(s.axis().scale(rika?.52:-.52)).add(V.UP.scale(.17)).unit();
        Plan p=new Plan(focus.add(offset.scale(10)),focus,rika?53:51,0,true);
        p=CinematicFraming.includeSphere(p,source,rika?3.6:3.8,s.aspect());
        return CinematicFraming.includeSphere(p,body,1.4,s.aspect());
    }

    private static Plan wide(Finale s){
        // Fixed source anchors, fixed screen direction: no age-driven orbit through opaque beams.
        V focus=s.rikaSource().lerp(s.getoSource(),.52);
        V offset=s.lateral().add(s.axis().scale(.16)).add(V.UP.scale(.34)).unit();
        Plan p=new Plan(focus.add(offset.scale(15)),focus,65,0,true);
        p=includeCasters(p,s);
        // Reserve the full collision travel before it moves, keeping the lens still during CLASH.
        p=CinematicFraming.includeSphere(p,s.rikaSource().lerp(s.getoSource(),.40),4.5,s.aspect());
        return CinematicFraming.includeSphere(p,s.rikaSource().lerp(s.getoSource(),.72),4.5,s.aspect());
    }

    private static Plan injured(Finale s){
        V focus=s.getoBody().add(V.UP.scale(-.12));
        V offset=s.lateral().scale(.80).sub(s.axis().scale(.62)).add(V.UP.scale(.10)).unit();
        return CinematicFraming.includeSphere(new Plan(focus.add(offset.scale(5.6)),focus,47,0,true),
                s.getoBody(),1.4,s.aspect());
    }

    private static Plan victory(Finale s){
        V focus=s.yutaBody().lerp(s.rikaSource(),.46);
        V offset=s.lateral().scale(.80).add(s.axis().scale(.68)).add(V.UP.scale(.15)).unit();
        Plan p=new Plan(focus.add(offset.scale(9)),focus,53,0,true);
        p=CinematicFraming.includeSphere(p,s.yutaBody(),1.4,s.aspect());
        return CinematicFraming.includeSphere(p,s.rikaSource(),3.0,s.aspect());
    }

    private static Plan impactEnd(Finale s){
        V focus=s.getoSource(),offset=s.lateral().add(s.axis().scale(-.30)).add(V.UP.scale(.35)).unit();
        return CinematicFraming.includeSphere(new Plan(focus.add(offset.scale(16)),focus,67,0,true),
                focus,12,s.aspect());
    }

    /** Refit after render-time damping, before collision handling. */
    public static Plan fitFinale(Plan p,Finale s,int phase,double age){
        if(phase==YutaGetoBeat.SEPARATE||phase==YutaGetoBeat.CLASH){
            p=includeCasters(p,s);
            p=CinematicFraming.includeSphere(p,s.collision(),4.5,s.aspect());
        }else if(phase==YutaGetoBeat.CHARGE){
            if(age<=36)p=CinematicFraming.includeSphere(p,s.getoSource(),3.8,s.aspect());
            if(age>=52&&age<=82){
                p=CinematicFraming.includeSphere(p,s.rikaSource(),3.6,s.aspect());
                p=CinematicFraming.includeSphere(p,s.yutaBody(),1.4,s.aspect());
            }
            if(age>=109)p=includeCasters(p,s);
        }else if(phase==YutaGetoBeat.BREAKTHROUGH){
            p=CinematicFraming.includeSphere(p,s.getoBody(),1.4,s.aspect());
            V impact=s.rikaSource().lerp(s.getoSource(),YutaGetoBeat.breakthroughFraction((float)age));
            p=CinematicFraming.includeSphere(p,impact,4.5,s.aspect());
            if(age>=18)p=CinematicFraming.includeSphere(p,s.getoSource(),4.5+7.5*ease((age-18)/21),s.aspect());
        }else if(phase==YutaGetoBeat.AFTERMATH){
            p=CinematicFraming.includeSphere(p,s.getoBody(),1.4,s.aspect());
            if(age<28)p=CinematicFraming.includeSphere(p,s.getoSource(),12*(1-ease(age/28)),s.aspect());
        }else if(phase==YutaGetoBeat.RETREAT&&age>=58){
            p=CinematicFraming.includeSphere(p,s.yutaBody(),1.4,s.aspect());
            p=CinematicFraming.includeSphere(p,s.rikaSource(),3.0,s.aspect());
        }
        if(phase==YutaGetoBeat.CLASH||phase==YutaGetoBeat.BREAKTHROUGH)
            p=new Plan(clearBeamEye(s,p.eye()),p.aim(),p.fov(),0,true);
        return p;
    }

    private static Plan includeCasters(Plan p,Finale s){
        p=CinematicFraming.includeSphere(p,s.yutaBody(),1.4,s.aspect());
        p=CinematicFraming.includeSphere(p,s.getoBody(),1.4,s.aspect());
        p=CinematicFraming.includeSphere(p,s.rikaSource(),3.6,s.aspect());
        return CinematicFraming.includeSphere(p,s.getoSource(),3.8,s.aspect());
    }

    public static double beamClearance(Finale s,V eye){
        return Math.min(segmentDistance(eye,s.rikaSource(),s.collision()),
                segmentDistance(eye,s.getoSource(),s.collision()));
    }

    /** Keep the interpolated eye on the selected side and outside both braided beam envelopes. */
    public static V clearBeamEye(Finale s,V eye){
        V lateral=s.lateral();
        double outward=eye.sub(s.collision()).dot(lateral);
        double near=Math.min(s.rikaSource().sub(s.collision()).dot(lateral),
                s.getoSource().sub(s.collision()).dot(lateral));
        double far=Math.max(s.rikaSource().sub(s.collision()).dot(lateral),
                s.getoSource().sub(s.collision()).dot(lateral));
        double margin=Math.max(near,far)+2.65;
        if(outward<margin)eye=eye.add(lateral.scale(margin-outward));
        return eye;
    }

    private static double segmentDistance(V p,V a,V b){
        V d=b.sub(a);
        double t=CinematicFraming.clamp(p.sub(a).dot(d)/Math.max(1e-8,d.dot(d)),0,1);
        return p.sub(a.lerp(b,t)).length();
    }
    private static Plan mix(Plan a,Plan b,double t){
        return new Plan(a.eye().lerp(b.eye(),t),a.aim().lerp(b.aim(),t),a.fov()+(b.fov()-a.fov())*t,0,true);
    }
    private static double ease(double t){t=CinematicFraming.clamp(t,0,1);return t*t*(3-2*t);}
}

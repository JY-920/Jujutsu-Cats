package dev.jujutsuvfx.core;

import dev.jujutsuvfx.core.CinematicFraming.*;

/** Standalone mathematical regressions; does not start Minecraft or touch a world. */
public final class CinematicFramingTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static Scene scene(V offset,double gap,double aspect,boolean paired){
        V hero=new V(0,4,0).add(offset);
        return new Scene(hero,hero.add(new V(0,-2,gap)),V.FORWARD,new V(2,3,18),
                hero.add(new V(0,1,3)),3,1.5,aspect,paired,10,1);
    }
    private static void visible(Plan plan,V point,double radius,double aspect){
        V f=plan.aim().sub(plan.eye()).unit(),r=f.cross(V.UP).unit(),u=r.cross(f).unit(),d=point.sub(plan.eye());
        double depth=d.dot(f),tan=Math.tan(Math.toRadians(plan.fov()/2));
        check(depth>radius,"Subject behind lens");
        check(Math.abs(d.dot(r))+radius<=depth*tan*aspect+.001,"Subject outside horizontal frame");
        check(Math.abs(d.dot(u))+radius<=depth*tan+.001,"Subject outside vertical frame");
    }
    public static void main(String[] args){
        V origin=new V(0,0,0),offset=new V(12000,-800,-25000);
        for(Shot shot:Shot.values())for(Mode mode:Mode.values())for(double aspect:new double[]{.6,1,16.0/9,3.5})
            for(double gap:new double[]{0,3,12,40,128})for(boolean paired:new boolean[]{false,true}){
                Plan p=CinematicFraming.plan(scene(origin,gap,aspect,paired),shot,mode);
                Plan translated=CinematicFraming.plan(scene(offset,gap,aspect,paired),shot,mode);
                check(p.eye().finite()&&p.aim().finite(),"Non-finite camera");
                check(Double.isFinite(CinematicFraming.yaw(p.eye(),p.aim()))&&Double.isFinite(CinematicFraming.pitch(p.eye(),p.aim())),"Non-finite angle");
                check(p.fov()>40&&p.fov()<90,"Unusable field of view");
                check(translated.eye().sub(offset).sub(p.eye()).length()<1e-8,"World position changes composition");
                check(translated.aim().sub(offset).sub(p.aim()).length()<1e-8,"World position changes aim");
            }
        for(Shot shot:new Shot[]{Shot.DUEL,Shot.MELEE,Shot.DOMAIN,Shot.DEFENSE,Shot.AIR,Shot.DIVE}){
            Scene s=scene(origin,16,16.0/9,true);Plan p=CinematicFraming.plan(s,shot,Mode.AUTO);
            visible(p,s.hero(),s.bodyRadius(),s.aspect());visible(p,s.target(),s.bodyRadius(),s.aspect());
        }
        for(int fps:new int[]{24,30,60,144,240}){
            double position=0;
            for(int i=0;i<fps;i++)position+=(10-position)*CinematicFraming.blend(6,1.0/fps);
            check(Math.abs(position-10*(1-Math.exp(-6)))<1e-9,"Frame-rate-dependent damping at "+fps);
        }
        check(CinematicFraming.blend(5,0)==0,"Pause drifts");
        check(CinematicFraming.blend(5,-1)==0,"Rewound partial tick moves camera");
        check(CinematicFraming.blend(5,10)==CinematicFraming.blend(5,.1),"Hitch not clamped");
        ShotClock clock=new ShotClock();
        check(clock.choose(Shot.MELEE,0)==Shot.DUEL,"Unconfirmed punch cuts immediately");
        check(clock.choose(Shot.MELEE,.15)==Shot.MELEE,"Stable melee never selected");
        check(clock.choose(Shot.DUEL,.2)==Shot.MELEE,"Shot does not hold");
        check(clock.choose(Shot.RELEASE,.5)==Shot.RELEASE,"Release did not preempt charging");
        check(clock.choose(Shot.CHARGE,.6)==Shot.RELEASE,"Shot snaps back after launch");
        check(clock.choose(Shot.DOMAIN,.9)==Shot.DOMAIN,"Domain reveal was missed");
        check(clock.choose(Shot.MELEE,1.5)==Shot.DOMAIN,"Domain reveal too short");
        check(clock.choose(Shot.MELEE,2.8)==Shot.MELEE,"Domain shot never ends");
        clock.reset();check(clock.choose(Shot.DUEL,0)==Shot.DUEL,"Actor switch retains shot clock");
        System.out.println("PASS: "+checks+" cinematic framing, composition, timing and FPS checks.");
    }
}

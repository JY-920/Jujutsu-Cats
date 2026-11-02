package dev.jujutsuvfx.core;

import dev.jujutsuvfx.core.CinematicFraming.*;

/** Real compositions: translated fights, narrow screens and a fast travelling vortex. */
public final class YutaCinematicFramingTest {
    private static int checks;
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    private static void visible(Plan p,V point,double radius,double aspect){
        V f=p.aim().sub(p.eye()).unit(),r=f.cross(V.UP).unit(),u=r.cross(f).unit(),d=point.sub(p.eye());
        double depth=d.dot(f),tan=Math.tan(Math.toRadians(p.fov()/2));
        check(depth>radius,"Object behind camera");
        check(Math.abs(d.dot(r))+radius<depth*tan*aspect,"Object outside horizontal frame");
        check(Math.abs(d.dot(u))+radius<depth*tan,"Object outside vertical frame");
    }
    public static void main(String[] args){
        for(double aspect:new double[]{.6,1,16.0/9,3.5})for(double side:new double[]{-1,1})
            for(V shift:new V[]{new V(0,0,0),new V(16000,-80,-24000)})
                for(float age:new float[]{0,28,58,69,70,71,74,80,100,111}){
                    V hero=shift.add(new V(0,2,0)),target=hero.add(new V(0,5,42));
                    V origin=hero.add(new V(0,.5,1)),tip=origin.add(new V(0,0,GetoVortexMotion.reach(age,48)));
                    double radius=GetoVortexMotion.radius(age)+.4;
                    Scene s=new Scene(hero,target,V.FORWARD,new V(0,0,0),origin,radius,1.3,aspect,true,5,side);
                    Plan p=YutaCinematicFraming.vortex(s,origin,tip,radius,age>=GetoVortexMotion.FIRE);
                    check(p.eye().finite()&&p.aim().finite(),"Non-finite vortex shot");
                    visible(p,hero,1.3,aspect);visible(p,origin,radius,aspect);
                    if(age>=GetoVortexMotion.FIRE){visible(p,tip,2.2,aspect);visible(p,target,1.3,aspect);}
                    Plan ring=YutaCinematicFraming.ring(s);
                    visible(ring,hero.add(V.UP.scale(-.15)),1.1,aspect);
                    check(ring.fov()==45,"Ring insert loses closeup FOV");
                    Plan inside=CinematicFraming.insideDomain(p,shift,36);
                    check(inside.eye().sub(shift).length()<36,"Camera escapes Yuta domain");
                }
        casting();
        finale();
        elevatedEmitters();
        shake();
        System.out.println("Yuta cinematic framing: "+checks+" checks passed");
    }

    private static void casting(){
        for(double aspect:new double[]{.6,1,16.0/9,3.5})for(double side:new double[]{-1,1}){
            V hero=new V(0,2,0),emitter=new V(0,2.4,1.4);
            Scene s=new Scene(hero,new V(0,2,80),V.FORWARD,new V(0,0,0),emitter,1,1,aspect,true,0,side);
            Plan p=YutaCinematicFraming.casting(s,emitter,1.1);
            visible(p,hero,.9,aspect);visible(p,emitter,1.1,aspect);
            check(p.fov()==46,"Casting must keep a close-up lens");
            if(aspect>=1)check(p.eye().sub(hero).length()<9,"Distant opponent pulls casting close-up away");
        }
    }
    private static void elevatedEmitters(){
        for(double aspect:new double[]{.6,1,16.0/9,3.5})for(double side:new double[]{-1,1})
            for(V shift:new V[]{new V(0,0,0),new V(16000,-80,-24000)}){
                V y=shift,g=shift.add(new V(0,0,26)),r=shift.add(new V(-2,0,-3.3));
                V love=r.add(new V(0,3,0)),curse=g.add(new V(0,4.4,3));
                V hit=love.lerp(curse,.65);
                var s=new YutaCinematicFraming.Finale(y,g,r,hit,curse,love,aspect,side);
                for(int phase:new int[]{YutaGetoBeat.SEPARATE,YutaGetoBeat.CLASH}){
                    Plan p=YutaCinematicFraming.finale(s,phase,20);
                    visible(p,y.add(V.UP.scale(1.2)),1.4,aspect);visible(p,g.add(V.UP.scale(1.2)),1.4,aspect);
                    visible(p,love,3.6,aspect);visible(p,curse,3.8,aspect);visible(p,hit,4.5,aspect);
                    check(YutaCinematicFraming.beamClearance(s,p.eye())>2.3,"Raised source occludes final camera");
                }
                Plan getoCharge=YutaCinematicFraming.finale(s,YutaGetoBeat.CHARGE,20);
                visible(getoCharge,curse,3.8,aspect);visible(getoCharge,g.add(V.UP.scale(1.2)),1.4,aspect);
                Plan rikaCharge=YutaCinematicFraming.finale(s,YutaGetoBeat.CHARGE,65);
                visible(rikaCharge,love,3.6,aspect);visible(rikaCharge,y.add(V.UP.scale(1.2)),1.4,aspect);
            }
    }

    private static void shake(){
        double early=0,late=0;
        for(int phase=YutaGetoBeat.NONE;phase<=YutaGetoBeat.DONE;phase++)
            for(int frame=0;frame<=4000;frame++){
                double age=frame*.025;
                var s=YutaCinematicFraming.shake(phase,age,0,1);
                check(s.offset().finite()&&Double.isFinite(s.yaw()+s.pitch()+s.roll()),"Invalid shake reaches camera");
                check(s.offset().length()<.11&&Math.abs(s.pitch())<1.8&&Math.abs(s.yaw())<1.1&&Math.abs(s.roll())<.5,"Unbounded finale shake");
                if(phase<YutaGetoBeat.CHARGE||phase>YutaGetoBeat.AFTERMATH)
                    check(s.offset().length()==0&&s.yaw()==0&&s.pitch()==0&&s.roll()==0,"Shake leaks into ordinary combat");
                if(phase==YutaGetoBeat.BREAKTHROUGH){
                    if(age<28)early=Math.max(early,Math.abs(s.pitch()));
                    if(age>38)late=Math.max(late,Math.abs(s.pitch()));
                }
            }
        check(early>.7&&late<.25,"Breakthrough lacks a strong impact and decaying tail");
        var impact=YutaCinematicFraming.shake(YutaGetoBeat.CLASH,1,0,1);
        check(Math.abs(impact.pitch())>.3&&impact.offset().length()>.02,"Discharge shake is imperceptible");
        var disabled=YutaCinematicFraming.shake(YutaGetoBeat.CLASH,1,0,0);
        check(disabled.offset().length()==0&&disabled.pitch()==0,"Screen-effect setting ignored");
        var remote=YutaCinematicFraming.shake(YutaGetoBeat.CLASH,1,96,1);
        check(remote.offset().length()==0&&remote.pitch()==0,"Distant observer receives local impact shake");
        var invalid=YutaCinematicFraming.shake(YutaGetoBeat.CLASH,Double.NaN,0,1);
        check(invalid.offset().finite()&&invalid.pitch()==0,"Invalid phase time reaches camera");
    }

    /** Catch a too-tight lens, a shot reversal through either beam, or a drifting clash camera. */
    private static void finale(){
        for(double aspect:new double[]{.6,1,16.0/9,3.5})for(double side:new double[]{-1,1})
            for(V shift:new V[]{new V(0,0,0),new V(16000,-80,-24000)})
                for(double gap:new double[]{12,30,60}){
                    V y=shift,g=shift.add(new V(0,0,gap)),r=shift.add(new V(-2,0,-3));
                    V rs=r.add(V.UP.scale(2.25)),gs=g.add(V.UP.scale(2.2));
                    V hit=rs.lerp(gs,.5);
                    var s=new YutaCinematicFraming.Finale(y,g,r,hit,aspect,side);
                    Plan first=null;
                    for(double age:new double[]{0,1,45,89}){
                        Plan p=YutaCinematicFraming.finale(s,YutaGetoBeat.CLASH,age);
                        check(p.eye().finite()&&p.aim().finite(),"Non-finite clash shot");
                        visible(p,y.add(V.UP.scale(1.2)),1.4,aspect);
                        visible(p,g.add(V.UP.scale(1.2)),1.4,aspect);
                        visible(p,rs,3.6,aspect);visible(p,gs,3.8,aspect);visible(p,hit,4.5,aspect);
                        check(YutaCinematicFraming.beamClearance(s,p.eye())>2.3,"Clash eye inside beam wraps");
                        if(first==null)first=p;
                        else check(p.eye().sub(first.eye()).length()<1e-8,"Clash changes shot with age");
                    }
                    for(double age:new double[]{0,20,36,44,52,70,82,95,109}){
                        Plan p=YutaCinematicFraming.finale(s,YutaGetoBeat.CHARGE,age);
                        check(p.eye().finite()&&p.aim().finite(),"Non-finite charge insert");
                        check(p.eye().sub(hit).dot(new V(-side,0,0))>2,"Charge insert crosses fight line");
                        if(age<=36)visible(p,gs,3.8,aspect);
                        if(age>=52&&age<=82){visible(p,rs,3.6,aspect);visible(p,y.add(V.UP.scale(1.2)),1.4,aspect);}
                    }
                    for(double age:new double[]{0,10,20,39}){
                        Plan p=YutaCinematicFraming.finale(s,YutaGetoBeat.BREAKTHROUGH,age);
                        visible(p,g.add(V.UP.scale(1.2)),1.4,aspect);
                        check(YutaCinematicFraming.beamClearance(s,p.eye())>2.3,"Breakthrough eye inside beam wraps");
                        if(age==39)visible(p,gs,12,aspect);
                    }
                    Plan injured=YutaCinematicFraming.finale(s,YutaGetoBeat.AFTERMATH,10);
                    visible(injured,g.add(V.UP.scale(1.2)),1.4,aspect);
                    Plan victory=YutaCinematicFraming.finale(s,YutaGetoBeat.RETREAT,90);
                    visible(victory,y.add(V.UP.scale(1.2)),1.4,aspect);visible(victory,rs,3.0,aspect);
                    check(victory.aim().sub(y).length()<victory.aim().sub(g).length(),"Final shot loses victorious pair");
                    V rescued=YutaCinematicFraming.clearBeamEye(s,hit);
                    check(YutaCinematicFraming.beamClearance(s,rescued)>2.3,"Safety fallback stays inside beam");
                }
    }
}

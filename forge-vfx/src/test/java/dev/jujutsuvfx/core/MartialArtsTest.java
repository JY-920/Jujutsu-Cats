package dev.jujutsuvfx.core;

public final class MartialArtsTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void near(float a,float b,float error,String message){check(Float.isFinite(a)&&Math.abs(a-b)<error,message+": "+a+" / "+b);}
    public static void main(String[] args){
        MartialArts.Sequence a=new MartialArts.Sequence(),b=new MartialArts.Sequence();
        check(a.press(1,100,false),"press");a.release(1,101);
        check(a.move()==MartialArts.JAB&&a.next()==1,"first tap and chain");
        check(b.move()==MartialArts.IDLE&&b.next()==0,"players have separate combos");
        var instant=new MartialArts.Sequence();instant.press(1,10,false);instant.release(1,10);
        check(instant.move()==MartialArts.JAB,"sub-tick tap launches normally");
        instant.press(2,10,false);instant.release(2,10);instant.tick(17);
        check(instant.move()==MartialArts.CROSS&&instant.action()==2,"two sub-tick taps are separated by the combat timeline");
        check(a.press(2,102,false),"early input accepted during anticipation");a.release(2,103);
        check(a.queued()==MartialArts.CROSS&&a.action()==1,"one stored follow-up, current action identity retained");
        check(!a.press(3,104,false),"full buffer cannot accumulate an uncontrollable chain");
        check(a.move()==MartialArts.JAB,"buffer does not cut off the first hit");a.tick(107);
        check(a.move()==MartialArts.JAB,"follow-through must finish");a.tick(108);
        check(a.move()==MartialArts.CROSS&&a.next()==2,"buffer drains once");
        check(a.action()==2,"queued press becomes the next active identity");
        check(a.press(4,109,false),"long press can begin during previous strike");a.tick(118);
        check(a.move()==MartialArts.CROSS&&a.charging(),"short hold is not full charge");a.tick(119);
        check(a.move()==MartialArts.SPIN&&!a.charging(),"second completed tap derives spin");
        a.release(4,133);check(a.move()==MartialArts.SPIN,"mouse-up cannot revert committed attack");
        a.tick(160);a.tick(180);check(a.move()==MartialArts.IDLE,"one move per hold");
        check(a.press(5,200,true),"air hold");a.tick(210);check(a.move()==MartialArts.DIVE,"air selects dive");
        a.land(216);check(a.move()==MartialArts.LAND&&a.age(216)==0,"landing commits once");
        a.land(217);check(a.age(217)==1,"repeated ground ticks cannot replay slam");
        a.cancel();check(!a.down()&&a.move()==0,"cancel clears input and animation");
        check(!a.press(4,220,false),"old packet ignored");
        check(MartialArts.derived(0,false)==MartialArts.PALM&&MartialArts.derived(1,false)==MartialArts.UPPERCUT
                &&MartialArts.derived(3,false)==MartialArts.BARRAGE,"stage derivatives");
        int hits=0;for(int i=0;i<60;i++)if(MartialArts.contactAt(MartialArts.BARRAGE,i))hits++;
        check(hits==6,"barrage has six bounded damage windows");
        b.adopt(8,8,MartialArts.UPPERCUT,2,0,0,false,300,false);
        b.adopt(8,8,MartialArts.UPPERCUT,6,0,0,false,304,false);
        check(b.age(304)==6,"snapshot does not replay pose");
        b.adopt(8,8,MartialArts.UPPERCUT,1,0,0,false,309,false);
        check(b.age(309)==11,"a delayed same-action snapshot never rewinds the clip");
        b.adopt(7,7,MartialArts.JAB,0,1,0,false,305,false);check(b.move()==MartialArts.UPPERCUT,"late state ignored");
        MartialArts.Sequence c=new MartialArts.Sequence();c.press(1,0,false);c.release(1,1);
        c.press(2,2,false);c.release(2,3);
        c.adopt(2,1,MartialArts.JAB,1,1,0,false,4,true);
        check(c.action()==1&&c.age(4)==3&&c.queued()==2,"buffer acknowledgement does not rewind or consume the input");
        c.tick(8);c.adopt(2,1,MartialArts.JAB,6,1,0,false,9,true);
        check(c.move()==MartialArts.CROSS&&c.action()==2,"late previous attack cannot replace a predicted follow-up");
        var late=new MartialArts.Sequence();late.press(1,0,false);
        late.adopt(1,1,MartialArts.PALM,1,0,0,false,9,true);late.tick(10);late.release(1,11);late.tick(40);
        check(late.move()==MartialArts.IDLE&&late.queued()==0,"server-committed held move cannot queue itself again");
        for(int input=1;input<12;input++){
            var q=new MartialArts.Sequence();q.press(1,0,false);q.release(1,1);
            check(q.press(2,input,false),"input accepted throughout the action");q.release(2,input+1);
            q.tick(Math.max(8,input+1));check(q.move()==MartialArts.CROSS,"early and late taps both connect");
        }
        MartialArts.ImpactClock stop=new MartialArts.ImpactClock();stop.hit(1,3,3.5f,10,2);
        near(stop.sample(1,4.5f,11),3.5f,.0001f,"confirmed hit holds pose");
        stop.hit(1,3,5,11,2);near(stop.sample(1,5.5f,12),3.5f,.0001f,"duplicate target/packet cannot extend hit stop");
        float previous=3.5f;
        for(float t=12;t<22;t+=.05f){float v=stop.sample(1,t-6.5f,t);check(v>=previous-.0001f,"hit stop recovery never rewinds");previous=v;}
        near(stop.sample(2,0,12),0,.0001f,"next buffered action immediately gets its own clock");
        check(MartialArts.aura(0,0,true)==0&&MartialArts.aura(MartialArts.JAB,0,true)==0,"no idle/windup aura");
        check(MartialArts.aura(MartialArts.JAB,2.8f,true)>.9f&&MartialArts.aura(MartialArts.JAB,2.8f,false)==0,"only striking hand bursts before contact");
        check(MartialArts.aura(MartialArts.JAB,4,true)==0,"aura ends immediately after contact");
        for(int move:new int[]{1,2,3,4,5,8,10})for(float t=0;t<MartialArts.duration(move);t+=.1f){
            MartialPose p=MartialPose.sample(move,t);var rig=p.rig(move,true);
            var feet=move==4?new MartialPose.Part[]{rig.leftLeg()}:new MartialPose.Part[]{rig.rightLeg(),rig.leftLeg()};
            for(var foot:feet){
                var end=LimbCurve.bend(.75f,0,foot.bend()*(float)Math.PI/180,.375f);
                float[] point=MartialPose.rotate(0,end.y()*16,end.z()*16,foot.pitch(),foot.yaw(),foot.roll());
                near(foot.y()+point[1],24,.02f,"support foot stays on ground");
                near(foot.x()+point[0],Math.copySign(1.9f+.7f*p.activity(),foot.x()),.02f,"support foot does not drift sideways");
            }
        }
        for(int move=0;move<=MartialArts.LAND;move++)for(float t=0;t<MartialArts.duration(move)+1;t+=.05f){
            MartialPose p=MartialPose.sample(move,t);
            for(var limb:new MartialPose.Limb[]{p.rightArm(),p.leftArm(),p.rightLeg(),p.leftLeg()}) {
                check(Float.isFinite(limb.pitch()+limb.yaw()+limb.roll()+limb.bend()),"finite animation");
                check(Math.abs(limb.bend())<=130,"joint has a non-inverting radius");
            }
            check(Float.isFinite(p.turn()+p.lean()+p.roll()+p.crouch()),"finite torso");
        }
        for(float angle=-2.27f;angle<=2.27f;angle+=.1f)for(float center:new float[]{.25f,.375f}){
            for(float joint:new float[]{center-3/16f,center+3/16f}) {
                var l=LimbCurve.bend(joint-.00001f,.08f,angle,center);var r=LimbCurve.bend(joint+.00001f,.08f,angle,center);
                near(l.y(),r.y(),.0001f,"continuous elbow/knee y");near(l.z(),r.z(),.0001f,"continuous elbow/knee z");
                near(l.angle(),r.angle(),.0002f,"continuous normal");
            }
        }
        for(float y=-.15f;y<.9f;y+=.03f){var p=LimbCurve.bend(y,.1f,0,.25f);near(y,p.y(),.00001f,"straight skin");near(.1f,p.z(),.00001f,"straight skin z");}
        System.out.println("Martial arts: actor isolation, full-action input buffer, derivatives, non-rewinding hit stop, planted support feet, contact-only aura and seamless curves passed.");
    }
}

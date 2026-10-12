package dev.jujutsuvfx.core;

/** Anticipation, fast strike, follow-through, then recovery; degrees and vanilla model pixels. */
public record MartialPose(Limb rightArm,Limb leftArm,Limb rightLeg,Limb leftLeg,
                          float turn,float lean,float roll,float crouch,float activity) {
    public record Limb(float pitch,float yaw,float roll,float bend) {
        public Limb blend(Limb b,float t){return new Limb(m(pitch,b.pitch,t),m(yaw,b.yaw,t),m(roll,b.roll,t),m(bend,b.bend,t));}
    }
    private static Limb limb(float p,float y,float r,float b){return new Limb(p,y,r,b);}
    private static final Limb R=limb(-35,-10,10,-72),L=limb(-42,14,-12,-76),LEG=limb(0,0,0,0);
    public static final MartialPose GUARD=new MartialPose(R,L,LEG,LEG,0,2,0,.75f,0);
    private static float m(float a,float b,float t){return a+(b-a)*t;}
    public MartialPose blend(MartialPose p,float t) {
        float dy=p.turn-turn;dy=(dy%360+540)%360-180;
        return new MartialPose(rightArm.blend(p.rightArm,t),leftArm.blend(p.leftArm,t),rightLeg.blend(p.rightLeg,t),leftLeg.blend(p.leftLeg,t),
                turn+dy*t,m(lean,p.lean,t),m(roll,p.roll,t),m(crouch,p.crouch,t),m(activity,p.activity,t));
    }
    private static MartialPose pose(Limb r,Limb l,Limb rl,Limb ll,float yaw,float lean,float roll,float low){
        return new MartialPose(r,l,rl,ll,yaw,lean,roll,low,1);
    }
    public static MartialPose charge(int stage,boolean air,float ticks) {
        MartialPose p=air?pose(limb(-140,-12,18,-85),limb(-140,12,-18,-85),limb(-60,0,8,95),limb(-28,0,-8,72),0,8,0,1)
                :switch(stage) {
            case 1->pose(limb(8,-18,20,-110),L,LEG,LEG,-24,8,2,1.8f);
            case 2->pose(limb(-48,-35,24,-80),limb(-35,30,-24,-75),limb(-30,-12,12,65),LEG,-42,4,1,1.2f);
            case 3->pose(limb(-40,-6,12,-96),limb(-45,6,-12,-96),LEG,LEG,-10,7,0,1.4f);
            default->pose(limb(-15,-28,20,-102),limb(-56,20,-12,-65),LEG,LEG,-24,4,1,1.4f);
        };
        return GUARD.blend(p,MartialArts.smooth(ticks/6));
    }
    public static MartialPose sample(int move,float age) {
        if(move==MartialArts.IDLE||age<0||age>=MartialArts.duration(move))return GUARD;
        if(move==MartialArts.BARRAGE) {
            if(age>19)return sample(move,19).blend(GUARD,MartialArts.smooth((age-19)/9));
            int beat=Math.max(0,Math.min(5,(int)Math.floor((age-1.5f)/3)));
            float local=age-(3+beat*3);
            float punch=local<0?MartialArts.smooth((local+1.5f)/1.5f):1-MartialArts.smooth((local-.3f)/1.2f);
            boolean right=beat%2==0;Limb arm=limb(-92,0,right?2:-2,-4);
            return pose(right?R.blend(arm,punch):R,right?L:L.blend(arm,punch),LEG,LEG,
                    (right?-14:17)*punch,5,0,1.15f);
        }
        if(move==MartialArts.SPIN) {
            float lift=MartialArts.smooth((age-2)/3)*(1-MartialArts.smooth((age-11)/5));
            MartialPose p=GUARD.blend(pose(limb(-62,-25,36,-45),limb(-30,40,-28,-78),
                    limb(-93,-10,16,8),limb(10,0,-5,48),0,6,-5,.2f),lift);
            float yaw=age<7?-55+235*MartialArts.smooth((age-1)/6):180+180*MartialArts.smooth((age-7)/7);
            return new MartialPose(p.rightArm,p.leftArm,p.rightLeg,p.leftLeg,yaw,p.lean,p.roll,p.crouch,Math.max(p.activity,1-age/23));
        }
        MartialPose wind,hit,follow;
        switch(move) {
            case MartialArts.JAB->{
                wind=pose(limb(-29,-20,12,-90),L,LEG,LEG,8,1,0,1.05f);
                hit=pose(limb(-90,-4,2,-3),L,LEG,LEG,-17,6,-1,.8f);
                follow=pose(limb(-92,1,4,-14),L,LEG,LEG,-20,7,-1,.9f);
            }
            case MartialArts.CROSS->{
                wind=pose(R,limb(-27,32,-15,-100),LEG,LEG,-17,1,0,1.15f);
                hit=pose(R,limb(-94,0,-2,-3),LEG,LEG,28,7,1,.85f);
                follow=pose(R,limb(-92,-7,-5,-17),LEG,LEG,33,7,1,.95f);
            }
            case MartialArts.HOOK->{
                wind=pose(limb(-36,-45,24,-106),L,LEG,LEG,26,3,1,1.35f);
                hit=pose(limb(-88,52,17,-61),L,LEG,LEG,-31,6,-2,.95f);
                follow=pose(limb(-83,68,19,-74),L,LEG,LEG,-38,6,-2,1.05f);
            }
            case MartialArts.KICK->{
                wind=pose(R,L,limb(-70,0,5,112),LEG,-14,-3,1,1.1f);
                hit=pose(limb(-20,-24,24,-60),L,limb(-96,-5,6,4),LEG,15,-8,2,1);
                follow=pose(limb(-20,-24,24,-60),L,limb(-90,-3,7,22),LEG,19,-7,2,1);
            }
            case MartialArts.PALM->{
                wind=charge(0,false,10);
                hit=pose(limb(-94,-2,0,-3),L,LEG,LEG,26,10,-1,1);
                follow=pose(limb(-97,3,2,-10),L,LEG,LEG,31,11,-1,1.1f);
            }
            case MartialArts.UPPERCUT->{
                wind=charge(1,false,10);
                hit=pose(limb(-148,-5,10,-24),L,limb(-45,0,6,65),limb(12,0,-4,18),24,-6,-2,.25f);
                follow=pose(limb(-161,2,12,-34),L,limb(-52,0,8,78),limb(14,0,-4,25),29,-8,-2,.1f);
            }
            case MartialArts.DIVE->{return charge(0,true,10).blend(pose(limb(-165,-8,12,-35),limb(-165,8,-12,-35),
                    limb(-26,0,6,68),limb(-38,0,-6,86),0,48,0,1),MartialArts.smooth(age/4));}
            case MartialArts.LAND->{
                wind=pose(limb(-115,-8,12,-30),limb(-115,8,-12,-30),LEG,LEG,0,20,0,2.5f);
                hit=pose(limb(-48,-12,16,-10),limb(-48,12,-16,-10),LEG,LEG,0,25,0,3.5f);
                follow=pose(limb(-42,-16,18,-22),limb(-42,16,-18,-22),LEG,LEG,0,22,0,3.2f);
            }
            default->{return GUARD;}
        }
        float contact=MartialArts.contact(move),strike=Math.min(1.4f,contact);
        if(age<contact) {
            float t=Math.max(0,(age-contact+strike)/strike);
            return wind.blend(hit,t*t*t);
        }
        if(age<contact+1.4f)return hit.blend(follow,MartialArts.smooth((age-contact)/1.4f));
        return follow.blend(GUARD,MartialArts.smooth((age-contact-1.4f)/(MartialArts.duration(move)-contact-1.4f)));
    }
    public record Part(float x,float y,float z,float pitch,float yaw,float roll,float bend) {}
    public record Rig(Part body,Part head,Part rightArm,Part leftArm,Part rightLeg,Part leftLeg) {}
    private static final float RAD=(float)Math.PI/180;
    /** Pelvis pivot and fixed staggered support feet. No torso tilt is added to the planted legs. */
    public Rig rig(int move,boolean grounded) {
        float py=12+Math.max(.75f,crouch)*activity,pz=-lean*.018f*activity;
        float[] origin=rotate(0,-12,0,lean,turn,roll);
        Part body=new Part(origin[0],py+origin[1],pz+origin[2],lean,turn,roll,0);
        Part head=new Part(body.x,body.y,body.z,0,turn*(move==MartialArts.SPIN?1:.32f),roll*.25f,0);
        Part ra=arm(rightArm,-5,py,pz),la=arm(leftArm,5,py,pz);
        boolean support=grounded&&move!=MartialArts.UPPERCUT&&move!=MartialArts.SPIN&&move!=MartialArts.DIVE;
        return new Rig(body,head,ra,la,leg(rightLeg,-1.9f,py,pz,support&&move!=MartialArts.KICK),leg(leftLeg,1.9f,py,pz,support));
    }
    private Part arm(Limb l,float x,float py,float pz) {
        float[] p=rotate(x,-10,0,lean,turn,roll);
        return new Part(p[0],py+p[1],pz+p[2],l.pitch+lean,l.yaw+turn,l.roll+roll,l.bend);
    }
    private Part leg(Limb l,float x,float py,float pz,boolean planted) {
        float[] hip=rotate(x,0,0,lean,turn,roll);
        float hx=hip[0],hy=py+hip[1],hz=pz+hip[2];
        if(!planted)return new Part(hx,hy,hz,l.pitch,l.yaw+turn,l.roll,l.bend);
        float dx=Math.copySign(1.9f+.7f*activity,x)-hx,dy=24-hy,dz=(x<0?1.0f:-1.0f)*activity-hz;
        float length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz),lo=0,hi=2.2f;
        for(int i=0;i<15;i++) {
            float mid=(lo+hi)*.5f;var end=LimbCurve.bend(.75f,0,mid,.375f);
            if(Math.hypot(end.y(),end.z())*16>length)lo=mid;else hi=mid;
        }
        float bend=(lo+hi)*.5f;var end=LimbCurve.bend(.75f,0,bend,.375f);
        float pitch=(float)(Math.atan2(dz,Math.hypot(dx,dy))-Math.atan2(end.z(),end.y()))/RAD;
        float roll=(float)-Math.atan2(dx,dy)/RAD;
        return new Part(hx,hy,hz,pitch,0,roll,bend/RAD);
    }
    public static float[] rotate(float x,float y,float z,float pitch,float yaw,float roll) {
        float c=(float)Math.cos(pitch*RAD),s=(float)Math.sin(pitch*RAD),a=y*c-z*s;z=y*s+z*c;y=a;
        c=(float)Math.cos(yaw*RAD);s=(float)Math.sin(yaw*RAD);a=x*c+z*s;z=-x*s+z*c;x=a;
        c=(float)Math.cos(roll*RAD);s=(float)Math.sin(roll*RAD);return new float[]{x*c-y*s,x*s+y*c,z};
    }
}

package dev.jujutsuvfx.core;

/** Tick-based input and combat timeline, shared by server authority and local pose prediction. */
public final class MartialArts {
    public static final int IDLE=0,JAB=1,CROSS=2,HOOK=3,KICK=4,PALM=5,UPPERCUT=6,SPIN=7,BARRAGE=8,DIVE=9,LAND=10;
    public static final int HOLD_TICKS=10,CHAIN_TICKS=26;
    private MartialArts() {}
    public static int duration(int move) {
        return switch(move) {case JAB,CROSS->12;case HOOK->14;case KICK->18;case PALM,UPPERCUT->20;
            case SPIN->23;case BARRAGE->28;case DIVE->32;case LAND->15;default->0;};
    }
    public static int contact(int move) {
        return switch(move) {case JAB,CROSS->3;case HOOK->4;case KICK,PALM,UPPERCUT->5;case SPIN->7;
            case BARRAGE->3;case LAND->1;default->-1;};
    }
    /** Input can be stored at any time; it cannot skip the strike or its follow-through. */
    public static int chainAt(int move) {
        return switch(move) {case JAB,CROSS->7;case HOOK->9;case KICK->12;case PALM->14;
            case UPPERCUT->16;case SPIN->18;case BARRAGE->23;case LAND->11;default->duration(move);};
    }
    public static float hitPause(int move,boolean black) {
        return black?2.4f:move==BARRAGE?.65f:move>=PALM?2f:move==HOOK||move==KICK?1.6f:1.2f;
    }
    /** Active fist only, immediately before contact. Idle/charging and foot attacks emit no hand aura. */
    public static float aura(int move,float age,boolean right) {
        if(move==IDLE||move==KICK||move==SPIN||move==DIVE||age<0||age>=duration(move))return 0;
        float hit=contact(move);
        if(move==BARRAGE) {
            int beat=Math.max(0,Math.min(5,(int)Math.floor((age-1.35f)/3)));
            hit=3+beat*3;if(right!=(beat%2==0))return 0;
        } else if(move!=LAND&&right!=(move!=CROSS))return 0;
        return smooth((age-hit+1.6f)/.55f)*(1-smooth((age-hit-.05f)/.45f));
    }
    public static boolean contactAt(int move,int age) {
        return move==BARRAGE?age>=3&&age<=18&&(age-3)%3==0:age==contact(move);
    }
    public static int derived(int next,boolean airborne) {
        return airborne?DIVE:switch(next) {case 1->UPPERCUT;case 2->SPIN;case 3->BARRAGE;default->PALM;};
    }
    public static float damage(int move) {
        return switch(move) {case CROSS->1.08f;case HOOK->1.2f;case KICK->1.3f;case PALM->1.75f;
            case UPPERCUT->1.45f;case SPIN->1.55f;case BARRAGE->.42f;case LAND->1.7f;default->1;};
    }
    public static float smooth(float x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}

    public static final class Sequence {
        private int move,next,sequence,action,queued,queuedSerial,inputStep;
        private long start=-10000,lastChain=-10000,pressed=-1;
        private boolean down,air,latched;
        public int move(){return move;}
        public int next(){return next;}
        public int sequence(){return sequence;}
        public int action(){return action;}
        public int queued(){return queued;}
        public int age(long now){return (int)Math.max(0,Math.min(1000,now-start));}
        public int held(long now){return down&&!latched?(int)Math.max(0,now-pressed):0;}
        public boolean charging(){return down&&!latched;}
        public boolean down(){return down;}
        public boolean busy(long now){return move!=IDLE&&age(now)<duration(move);}
        public boolean press(int serial,long now,boolean airborne) {
            if(serial<=sequence||down||queued!=0)return false;
            if(now-lastChain>CHAIN_TICKS||now<start)next=0;
            sequence=serial;inputStep=next;down=true;latched=false;pressed=now;air=airborne;return true;
        }
        public void release(int serial,long now) {
            if(!down||serial!=sequence)return;
            if(!latched){queued=now-pressed>=HOLD_TICKS?derived(inputStep,air):inputStep+1;queuedSerial=serial;}
            down=false;pressed=-1;tick(now);
        }
        public void tick(long now) {
            if(!busy(now)&&move!=IDLE)move=IDLE;
            if(down&&!latched&&now-pressed>=HOLD_TICKS){queued=derived(inputStep,air);queuedSerial=sequence;latched=true;}
            if(queued!=0&&(!busy(now)||age(now)>=chainAt(move))){int m=queued;queued=0;action=queuedSerial;begin(m,now);}
        }
        private void begin(int action,long now) {
            move=action;start=now;lastChain=now+duration(action);
            next=action>=JAB&&action<=KICK?action%4:0;
        }
        public void land(long now){if(move==DIVE)begin(LAND,now);}
        public void cancel(){move=IDLE;queued=0;down=false;latched=false;pressed=-1;next=0;lastChain=-10000;}
        /** Repeated snapshots never restart an animation already running for the same input. */
        public void adopt(int serial,int active,int clip,int age,int nextStep,int heldTicks,boolean holding,long now,boolean local) {
            if(active<action||!local&&serial<sequence)return;
            // A buffered press has its own serial. Its acknowledgement cannot restart the active clip.
            if(local&&active==action){
                if(move==clip)start=Math.min(start,now-age);
                return;
            }
            boolean same=active==action&&move==clip;
            // A delayed snapshot may advance the current clip but must never rewind its introduction.
            start=same?Math.min(start,now-age):now-age;
            sequence=Math.max(sequence,serial);action=active;move=clip;next=nextStep;lastChain=now-age+duration(clip);
            if(local){if(queuedSerial<=active)queued=0;if(down&&sequence==active)latched=true;}
            if(!local){down=holding;latched=false;pressed=holding?now-heldTicks:-1;queued=0;}
        }
    }

    /** Cosmetic hit stop. Input, server damage, camera control and world ticks keep running. */
    public static final class ImpactClock {
        private int action=-1,contact=-1;
        private float until=-10000,frozen;
        public void hit(int serial,int hitAge,float age,float now,float duration) {
            if(serial<action||serial==action&&hitAge<=contact)return;
            action=serial;contact=hitAge;frozen=age;until=now+duration;
        }
        public float sample(int serial,float age,float now) {
            if(serial!=action)return age;
            return Math.max(frozen,Math.min(age,frozen+Math.max(0,now-until)*1.65f));
        }
    }
}
